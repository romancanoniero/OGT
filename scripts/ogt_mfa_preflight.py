#!/usr/bin/env python3
"""Read-only Firebase MFA audit for OGT. Never prints credentials or user IDs.

Run on a protected CI runner. Service account JSON is piped from the existing
Contabo secret to stdin. No token, code, TOTP secret or password is persisted.
"""
import argparse
import json
import sys
from urllib import error, request

PROJECT = "goodthings-55612"
HOST = "https://identitytoolkit.googleapis.com"


def summarize(config, account=None):
    mfa = config.get("mfa") or {}
    state = mfa.get("state", "STATE_UNSPECIFIED")
    providers = mfa.get("providerConfigs") or []
    totp = next((p for p in providers if "totpProviderConfig" in p), {})
    totp_state = totp.get("state", "NOT_CONFIGURED")
    adjacent = (totp.get("totpProviderConfig") or {}).get("adjacentIntervals")
    result = {
        "project": PROJECT,
        "generalMfa": state,
        "totpProvider": totp_state,
        "adjacentIntervals": adjacent,
    }
    if account is not None:
        enrolled = account.get("mfaInfo") or []
        result["accountFound"] = True
        result["emailVerified"] = account.get("emailVerified") is True
        result["factorsCount"] = len(enrolled)
        # Factor display names, enrollment IDs, enrollment dates and user details
        # are intentionally not sent to the public GitHub Actions log.
        result["totpFactorsCount"] = sum(
            1 for factor in enrolled if "totpInfo" in factor
        )
    if state != "ENABLED":
        result["status"] = "BLOCKED_GENERAL_MFA_NOT_ENABLED"
        result["nextAction"] = (
            "Identity Platform product alignment/upgrade requires an owner "
            "review of billing; never auto-upgrade, disable MFA or reset users."
        )
    elif totp_state != "ENABLED":
        result["status"] = "BLOCKED_TOTP_PROVIDER_NOT_ENABLED"
        result["nextAction"] = "Review provider configuration; no automatic writes."
    elif account is not None and result["totpFactorsCount"] == 0:
        result["status"] = "BLOCKED_ACCOUNT_HAS_NO_TOTP"
        result["nextAction"] = "Verify account and run a secure MFA enrollment."
    elif account is not None and not result["emailVerified"]:
        result["status"] = "BLOCKED_UNVERIFIED_EMAIL"
        result["nextAction"] = "Verify email before MFA access."
    else:
        result["status"] = "CONFIG_OK_NOT_REAL_TOTP_VERIFIED"
        result["nextAction"] = (
            "Project and enrollment prerequisites pass; a real user sign-in "
            "is still needed. Never collect a live TOTP in CI."
        )
    return result


def read_response(path, credential, payload=None):
    headers = {"Authorization": "Bearer " + credential.token}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    method = "POST" if data is not None else "GET"
    req = request.Request(HOST + path, headers=headers, data=data, method=method)
    try:
        with request.urlopen(req, timeout=20) as response:
            return json.load(response)
    except error.HTTPError as exc:
        # Do not echo GCP response bodies: they might contain user metadata.
        raise RuntimeError("Identity Toolkit HTTP " + str(exc.code)) from None


def main():
    parser = argparse.ArgumentParser(description="OGT read-only MFA preflight")
    parser.add_argument("--email", default="", help="Target account; not printed")
    parser.add_argument("--project", default=PROJECT)
    args = parser.parse_args()
    if args.project != PROJECT:
        print("REFUSED: unexpected OGT Firebase project")
        return 3
    try:
        # Defer import so pure read-only decision logic has no CI dependencies.
        from google.oauth2 import service_account
        from google.auth.transport.requests import Request
        credentials_json = json.load(sys.stdin)
        if credentials_json.get("project_id") != PROJECT:
            print("REFUSED: Firebase credentials target a different project")
            return 3
        credentials = service_account.Credentials.from_service_account_info(
            credentials_json, scopes=["https://www.googleapis.com/auth/identitytoolkit"]
        )
        del credentials_json
        credentials.refresh(Request())
        config = read_response("/admin/v2/projects/" + PROJECT + "/config", credentials)
        account = None
        if args.email:
            data = read_response(
                "/v1/projects/" + PROJECT + "/accounts:lookup",
                credentials, {"email": [args.email]}
            )
            users = data.get("users") or []
            if len(users) != 1:
                print(json.dumps({"project": PROJECT, "status": "ACCOUNT_NOT_FOUND_OR_AMBIGUOUS"}))
                return 2
            account = users[0]
        result = summarize(config, account)
        print(json.dumps(result, ensure_ascii=False, sort_keys=True))
        return 0 if result["status"] == "CONFIG_OK_NOT_REAL_TOTP_VERIFIED" else 2
    except (ValueError, KeyError, TypeError, RuntimeError) as exc:
        # No service-account or API error payloads should reach Actions logs.
        print("MFA_PREFLIGHT_ERROR:", type(exc).__name__, str(exc)[:90])
        return 3
    except Exception as exc:
        print("MFA_PREFLIGHT_ERROR:", type(exc).__name__)
        return 3


if __name__ == "__main__":
    sys.exit(main())
