#!/usr/bin/env python3
"""Obtain iOS Firebase config from the REAL OGT Firebase app, not a mock.

Protected GitHub runner: service-account JSON is supplied through stdin from
the existing VPS, never written to disk. The downloaded Firebase client plist
is not a private key, but its identity must match the Xcode bundle exactly.
"""
import argparse
import base64
import json
import os
import plistlib
import sys
from urllib import error, request

PROJECT = "goodthings-55612"
BUNDLE = "com.onlygoodthings.app"
IOS_APP_ID = "1:521063054927:ios:4148b99938eb083430743a"

def validated_plist(raw: bytes) -> bytes:
    config = plistlib.loads(raw)
    expected = {"PROJECT_ID": PROJECT, "GOOGLE_APP_ID": IOS_APP_ID,
                "BUNDLE_ID": BUNDLE, "GCM_SENDER_ID": "521063054927"}
    for key, value in expected.items():
        if config.get(key) != value:
            raise ValueError("Firebase iOS plist %s does not match canonical OGT" % key)
    if not all(config.get(key) for key in ("API_KEY", "CLIENT_ID")):
        raise ValueError("Firebase iOS plist lacks required API_KEY or CLIENT_ID")
    return raw

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    if not args.output.endswith("/iosApp/iosApp/GoogleService-Info.plist"):
        print("REFUSED: wrong iOS app config destination")
        return 2
    try:
        from google.auth.transport.requests import Request
        from google.oauth2 import service_account
        info = json.load(sys.stdin)
        if info.get("type") != "service_account" or info.get("project_id") != PROJECT:
            raise ValueError("Unexpected service-account Firebase project")
        credentials = service_account.Credentials.from_service_account_info(
            info, scopes=["https://www.googleapis.com/auth/firebase.readonly"])
        del info
        credentials.refresh(Request())
        url = ("https://firebase.googleapis.com/v1beta1/projects/" + PROJECT +
               "/iosApps/" + IOS_APP_ID + "/config")
        req = request.Request(url, headers={"Authorization": "Bearer " + credentials.token})
        try:
            with request.urlopen(req, timeout=25) as response:
                payload = json.load(response)
        except error.HTTPError as exc:
            raise ValueError("Firebase Management API denied iOS config: HTTP %s" % exc.code) from None
        if payload.get("configFilename") != "GoogleService-Info.plist":
            raise ValueError("Unexpected Firebase iOS config filename")
        raw = base64.b64decode(payload["configFileContents"], validate=True)
        validated_plist(raw)
        with open(args.output, "wb") as out:
            out.write(raw)
        os.chmod(args.output, 0o600)
        print("Verified the real Firebase iOS app config, bundle and project.")
        return 0
    except Exception as exc:
        # Never echo credentials, tokens or Firebase API response bodies.
        print("Firebase iOS setup blocked:", str(exc) if isinstance(exc, ValueError) else type(exc).__name__)
        return 2

if __name__ == "__main__":
    sys.exit(main())
