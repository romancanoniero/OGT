#!/usr/bin/env python3
"""Validate the first integrated OGT release without printing credentials."""
import argparse
import hashlib
import ipaddress
import json
import re
from pathlib import Path

REPOSITORY = "romancanoniero/OGT"
BASE_URL = "https://onlygoodthings.lat"
REQUIRED = (
    "backend-dist/bin/backend", "docker-compose.yml", "Dockerfile",
    "web-nginx.conf", "web-app/index.html", "web-app/ogt-sdk.js",
    "web-app/google-logo.svg",
    "web-app/crm/control/index.html", "web-app/crm/empresas/index.html",
    "web-app/crm/crm.js", "web-app/crm/crm-core.js",
    "web-app/crm/crm-auth.js", "web-app/crm/crm-runtime.js",
    "web-app/app.js", "web-app/auth.js",
    "database/migrations/23_crm_foundations.sql",
    "database/migrations/24_post_media_frame.sql",
    "database/migrations/25_post_media_original.sql",
    "database/migrations/26_crm_publication_index.sql",
    "database/migrations/28_crm_user_moderation.sql",
    "database/migrations/29_stories.sql", "database/migrations/30_story_frame.sql",
    "database/migrations/31_account_erasure.sql", "database/migrations/32_user_blocks.sql", "database/migrations/33_csr_operations.sql", "database/migrations/34_company_registration.sql",
    "web-app/apple-continue-es.png", "web-app/coupon-wallet.js", "web-app/crm/crm-business.js", "web-app/crm/crm-registration.js",
    "web-app/user-safety.js",
    "web-app/ephemeral-stories.js", "web-app/story-frame.js", "web-app/story-playback.js",
    "web-app/crm/crm-stories.js",
)


def dotenv(path):
    values = {}
    for raw in Path(path).read_text().splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, sep, value = line.partition("=")
        if not sep or not re.fullmatch(r"[A-Z][A-Z0-9_]*", key.strip()):
            raise ValueError("Invalid .env entry; no values were printed")
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        values[key.strip()] = value
    return values


def validate_env(values):
    if values.get("OGT_ENV") != "production":
        raise ValueError("OGT_ENV must be production, even before public launch")
    if values.get("OGT_ALLOW_DEV_TOKENS", "false") != "false" or values.get("FIREBASE_AUTH_EMULATOR_HOST"):
        raise ValueError("Laboratory authentication is forbidden")
    if values.get("OGT_FIREBASE_PROJECT_ID", "goodthings-55612") != "goodthings-55612":
        raise ValueError("Unexpected Firebase project")
    if not (values.get("OGT_FIREBASE_CREDENTIALS") or values.get("GOOGLE_APPLICATION_CREDENTIALS")):
        raise ValueError("Firebase Admin credentials are not configured")
    if not values.get("OGT_DB_PASSWORD"):
        raise ValueError("OGT_DB_PASSWORD is missing")
    if values.get("OGT_DB_USER", "ogt") != "ogt":
        raise ValueError("This release targets the existing ogt database owner")
    if values.get("OGT_LAUNCH_MODE") != "prelaunch":
        raise ValueError("OGT_LAUNCH_MODE must be prelaunch; public launch is a separate approval")
    payment_mode=values.get("OGT_MP_MODE","disabled")
    if payment_mode not in ("disabled","sandbox") or values.get("OGT_MP_LIVE_PAYMENTS","false")!="false":
        raise ValueError("This candidate permits only disabled or sandbox payments")
    if payment_mode=="sandbox":
        if not re.fullmatch(r"[0-9]{1,30}",values.get("OGT_MP_COLLECTOR_ID","")):
            raise ValueError("Missing test Mercado Pago receiver")
        buyers=values.get("OGT_MP_SANDBOX_BUYER_IDS","")
        if buyers.strip() and not all(re.fullmatch(r"[0-9]{1,30}",item.strip()) for item in buyers.split(",")):
            raise ValueError("Invalid sandbox test-buyer identifiers; no values were printed")
        for key in ("OGT_MP_ACCESS_TOKEN_FILE","OGT_MP_WEBHOOK_SECRET_FILE"):
            path=values.get(key,"")
            if not path.startswith("/run/secrets/mercadopago/") or ".." in path or path.endswith("/"):
                raise ValueError("Payment credentials must use private mounted files")
        if values.get("OGT_MP_PUBLIC_BASE") not in (None,"",BASE_URL):
            raise ValueError("Unexpected payment callback origin")
    cidrs = values.get("OGT_PRELAUNCH_CIDRS", "").split(",")
    if not cidrs or not all(item.strip() for item in cidrs):
        raise ValueError("OGT_PRELAUNCH_CIDRS must identify the reviewer's allowed networks")
    for item in cidrs:
        network = ipaddress.ip_network(item.strip(), strict=True)
        if network.prefixlen == 0 or network.is_multicast or network.is_unspecified:
            raise ValueError("An unrestricted or invalid review network is forbidden")
    return True


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inventory(root):
    root = Path(root)
    files = {}
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError("Release packages must not contain symlinks")
        if path.is_file() and path != root / "release-manifest.json":
            rel = path.relative_to(root).as_posix()
            if path.name.startswith(".env") or path.suffix in {".pem", ".key", ".p8", ".p12", ".jks", ".keystore", ".mobileprovision"} or path.name in {"access-token-test","webhook-secret-test","firebase-admin.json","service-account.json"}:
                raise ValueError("A credential-like file was found in the release")
            files[rel] = sha256(path)
    for item in REQUIRED:
        if item not in files or (root / item).stat().st_size == 0:
            raise ValueError(f"Missing release component: {item}")
    return files


def make_manifest(root, source_sha):
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("A full commit SHA is required")
    root = Path(root)
    (root / "web-app/release.json").write_text(json.dumps({
        "repository": REPOSITORY, "sourceSha": source_sha,
        "scope": "app-backend-crms", "launchMode": "prelaunch",
    }) + "\n")
    data = {"repository": REPOSITORY, "sourceSha": source_sha,
            "baseUrl": BASE_URL, "scope": "app-backend-crms",
            "launchMode": "prelaunch", "files": inventory(root)}
    (root / "release-manifest.json").write_text(json.dumps(data, indent=2) + "\n")
    return data


def verify_manifest(root, expected_sha):
    root = Path(root)
    data = json.loads((root / "release-manifest.json").read_text())
    if (data.get("repository"), data.get("sourceSha"), data.get("scope"), data.get("launchMode"), data.get("baseUrl")) != (
        REPOSITORY, expected_sha, "app-backend-crms", "prelaunch", BASE_URL
    ):
        raise ValueError("Wrong repository, version, scope or launch mode")
    if data.get("files") != inventory(root):
        raise ValueError("Release contents do not match the tested package")
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=["env", "manifest", "verify"])
    parser.add_argument("path")
    parser.add_argument("sha", nargs="?")
    args = parser.parse_args()
    try:
        if args.operation == "env":
            validate_env(dotenv(args.path))
        elif args.operation == "manifest":
            make_manifest(args.path, args.sha or "")
        else:
            verify_manifest(args.path, args.sha or "")
    except (ValueError, OSError, TypeError) as error:
        parser.exit(1, f"Release blocked: {error}\n")
    print("OGT release validation passed")


if __name__ == "__main__":
    main()
