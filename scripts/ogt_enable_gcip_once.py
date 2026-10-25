#!/usr/bin/env python3
"""One-time, owner-approved OGT Identity Platform upgrade and MFA activation.

Only this fixed Firebase project. Does not edit billing, providers, users, enrolled
factors, time windows or auth methods. Google credentials are streamed through stdin.
"""
import argparse
import json
import sys
import time
from urllib import request, error

PROJECT = "goodthings-55612"
API = "https://identitytoolkit.googleapis.com"
EXPECTED_INTERVALS = 1
EXPECTED_TOTP = "ENABLED"


def mfa_facts(config):
    settings = config.get("mfa") or {}
    provider = next((p for p in settings.get("providerConfigs", [])
                     if isinstance(p, dict) and "totpProviderConfig" in p), {})
    totp = provider.get("totpProviderConfig") or {}
    return {
        "subtype": config.get("subtype", "UNSPECIFIED"),
        "general": settings.get("state", "UNSPECIFIED"),
        "totp": provider.get("state", "NOT_CONFIGURED"),
        "intervals": totp.get("adjacentIntervals"),
    }


def permitted_state(config):
    facts = mfa_facts(config)
    if facts["subtype"] not in ("FIREBASE_AUTH", "IDENTITY_PLATFORM"):
        raise ValueError("Unknown Firebase Authentication product subtype")
    if facts["totp"] != EXPECTED_TOTP:
        raise ValueError("TOTP provider is not enabled; refusing product change")
    if facts["intervals"] != EXPECTED_INTERVALS:
        raise ValueError("TOTP time windows changed; refusing product change")
    if facts["general"] not in ("ENABLED", "DISABLED"):
        raise ValueError("Unexpected MFA policy; refusing product change")
    return facts


def factor_ids(user):
    # Only for private before/after comparison; never print IDs, account or labels.
    return sorted((entry.get("mfaEnrollmentId"), "totpInfo" in entry)
                  for entry in user.get("mfaInfo", []))


def access_req(path, credentials, *, method="GET", payload=None, empty_post=False):
    body = None
    if payload is not None:
        body = json.dumps(payload).encode("utf-8")
    elif empty_post:
        body = b""
    headers = {
        "Authorization": "Bearer " + credentials.token,
        "X-Goog-User-Project": PROJECT,
    }
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = request.Request(API + path, data=body, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=35) as response:
            raw = response.read()
            return json.loads(raw.decode("utf-8")) if raw else {}
    except error.HTTPError as exc:
        try:
            details = json.loads(exc.read().decode("utf-8")).get("error", {})
            status = details.get("status", "UNKNOWN")
            msg = str(details.get("message", "")).replace("\n", " ")[:170]
        except (ValueError, UnicodeError):
            status, msg = "UNKNOWN", ""
        raise RuntimeError("Identity Toolkit HTTP %s / %s: %s" % (exc.code, status, msg)) from None


def get_config(creds):
    return access_req("/admin/v2/projects/" + PROJECT + "/config", creds)


def get_account(creds, email):
    found = access_req("/v1/projects/" + PROJECT + "/accounts:lookup",
                       creds, method="POST", payload={"email": [email]}).get("users") or []
    if len(found) != 1 or not found[0].get("emailVerified"):
        raise ValueError("Admin identity missing, ambiguous or unverified")
    if not any("totpInfo" in entry for entry in found[0].get("mfaInfo", [])):
        raise ValueError("Admin TOTP factor is not enrolled")
    return found[0]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True)
    parser.add_argument("--email", required=True)
    args = parser.parse_args()
    args.email = args.email.strip().strip("\"'")
    if args.project != PROJECT or not args.email:
        print("ABORT: unexpected project or missing protected account reference")
        return 3
    try:
        from google.oauth2 import service_account
        from google.auth.transport.requests import Request

        info = json.load(sys.stdin)
        if info.get("type") != "service_account" or info.get("project_id") != PROJECT:
            print("ABORT: credentials do not belong to OGT")
            return 3
        creds = service_account.Credentials.from_service_account_info(
            info, scopes=["https://www.googleapis.com/auth/cloud-platform"])
        del info
        creds.refresh(Request())

        before_config = get_config(creds)
        before = permitted_state(before_config)
        prior_user = get_account(creds, args.email)
        prior_factors = factor_ids(prior_user)
        print("PREFLIGHT: project=goodthings-55612 subtype=%s general=%s totp=%s adjacentIntervals=%s factors=%s" %
              (before["subtype"], before["general"], before["totp"],
               before["intervals"], len(prior_factors)))

        if before["subtype"] == "FIREBASE_AUTH":
            print("ACTION: initializing Identity Platform on existing Blaze project")
            # Official API: POST /v2/projects/{id}/identityPlatform:initializeAuth.
            # Empty body; requires firebaseauth.configs.create. Never edit billing.
            access_req("/v2/projects/" + PROJECT + "/identityPlatform:initializeAuth",
                       creds, method="POST", empty_post=True)
        else:
            print("ACTION: Identity Platform was already enabled; skipping initialization")

        current = before
        for attempt in range(7):
            current = permitted_state(get_config(creds))
            if current["subtype"] == "IDENTITY_PLATFORM":
                break
            if attempt < 6:
                time.sleep(5)
        if current["subtype"] != "IDENTITY_PLATFORM":
            raise RuntimeError("Product activation not verified; MFA policy untouched")

        if current["general"] != "ENABLED":
            print("ACTION: enabling optional MFA (not MANDATORY), preserving provider and interval settings")
            access_req("/admin/v2/projects/" + PROJECT +
                       "/config?updateMask=mfa.state", creds, method="PATCH",
                       payload={"mfa": {"state": "ENABLED"}})

        verified = permitted_state(get_config(creds))
        if verified["subtype"] != "IDENTITY_PLATFORM" or verified["general"] != "ENABLED":
            raise RuntimeError("MFA configuration verification failed")
        after_user = get_account(creds, args.email)
        if factor_ids(after_user) != prior_factors:
            raise RuntimeError("MFA enrollment changed unexpectedly: immediate manual review required")
        print("SUCCESS: subtype=IDENTITY_PLATFORM general=ENABLED totp=ENABLED adjacentIntervals=1 "
              "existing_enrollments_preserved=true")
        print("NOTICE: Real Microsoft Authenticator sign-in must be checked by the account holder.")
        return 0
    except Exception as exc:
        # No tokens, JSON keys, OTPs, enrollment IDs, user identifiers in logs.
        if isinstance(exc, (ValueError, RuntimeError)):
            print("STOP:", str(exc)[:250])
        else:
            print("STOP:", type(exc).__name__)
        return 2


if __name__ == "__main__":
    sys.exit(main())
