# OGT: publish the integrated candidate from the already authorized Mac

The user's SSH diagnostic confirmed that `~/ogt/web-app/crm/control/index.html`,
`crm/empresas/index.html`, and `crm/crm.js` were absent on the VPS. A 200 response
with `text/html` alone is not evidence of a working CRM page.

Use a fresh checkout of `romancanoniero/OGT`, preserving all existing local work:

    bash scripts/publish-ogt-from-mac.sh --deploy

The helper requires the existing `iankmp-vps` SSH alias, trusted host key,
Node, Python 3, Java, rsync, curl, and the existing clean private SDK checkout at
`~/AndroidStudioProjects/db-kmp-sdk`. An alternate SDK path may be supplied with
`OGT_DB_KMP_SDK_PATH`. It does not download the private SDK, request credentials,
create users, change roles, install software or create a staging installation.

## What executing --deploy authorizes

1. Validate the source and local prerequisites.
2. On the same VPS, back up the existing `.env` privately and set only
   `OGT_LAUNCH_MODE=prelaunch` and `OGT_PRELAUNCH_CIDRS`. The current public address
   observed by the trusted SSH server is added to any existing reviewer networks.
   Credentials and all other settings are preserved. No service restarts here.
3. Execute the integrated deploy script: compile and test backend/shared, create
   a complete release package, back up the installation, apply the explicit CRM
   migration, install the package and restart OGT web/API.
4. Verify both real CRM pages and the exact release over HTTPS from the Mac.
   A successful static HTML response from the public shell is not accepted.

No source checkout is reset and no application database is erased. The two review
settings are not automatically reverted if a later build fails; the backup path
is printed without showing any credentials. For activation failures and recovery,
read `FIRST_RELEASE.md` before retrying. This helper does not claim to implement
an automatic database rollback.

## Direct routing is required for automatic reviewer discovery

SSH proxy/jump hosts are rejected. The VPS checks that `onlygoodthings.lat`
resolves directly to the server endpoint of the SSH connection. A CDN, proxy,
NAT mismatch, private peer address or unresolved hostname stops automatic setup;
do not disable host checking or allow shared proxy ranges to force it through.
Browsing via a different VPN, IPv6 path, private relay or mobile network may
require explicitly authorizing that reviewer network separately.

## Corrected build behavior

The old deploy invoked `:shared:syncWebSdk`, which copies generated output over
tracked `web/app/ogt-sdk.js`, and then rejected tracked changes. The corrected
script runs `:shared:jsBrowserProductionWebpack` and copies the compiled bundle
only into the temporary release package. Source integrity checks remain enabled.
Server configuration is checked before the expensive build.

## Verification boundaries

Helper tests and shell syntax checks do not compile Kotlin, connect to Contabo,
validate live Firebase MFA, or complete the CRM business modules. The helper
prints verified URLs only after its HTTPS and version checks pass. The current
CRM remains the implemented access/session/summary candidate, not a claim of
finished campaign, user-management and moderation functionality.
