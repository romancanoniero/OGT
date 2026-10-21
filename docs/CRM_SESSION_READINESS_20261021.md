# OGT CRM session delivery - 21 October 2026

Repository: `romancanoniero/OGT`. Source branch: `feature/crm-foundations-20261021`.

## What this revision implements

- Email sign-in with an enrolled Firebase SMS or TOTP second factor inside the CRM.
- Dedicated Firebase app `ogt-crm`, browser-session persistence, explicit sign-out.
- Backend step-up expiry, immediate clearing of protected data on denial/logout,
  cancellation of obsolete requests, and response-to-tenant matching.
- Structured errors for absent backend, rejected permissions, invalid JSON and timeouts.
- No laboratory tokens, arbitrary bearer-token destinations or privilege bypass.
- Existing three CRM backend endpoints and visual patterns remain unchanged.

## Executed and pending verification

27 Node unit tests passed locally. JavaScript syntax passed locally.
The browser environment rejected navigation with ERR_BLOCKED_BY_ADMINISTRATOR;
therefore local browser checks did not execute. `qa/crm-browser.py` provides five
regression scenarios with mocked identity and API; the new CI workflow executes
these checks on the source branch. Check the actual Actions result, not this file,
before stating that browser checks passed. None of these mocks validates live MFA,
real Firebase project configuration or production tenant authorization.

## Verified deployment blocker

Automated staging run `39680394040` passed request validation and preflight.
Its publish job `119501184009` received no values for:

- Secrets: OGT_STAGING_HOST, OGT_STAGING_USER, OGT_STAGING_SSH_KEY,
  OGT_STAGING_KNOWN_HOSTS.
- Variable: OGT_STAGING_BASE_URL.

No SSH connection or file publication occurred in that run. Configure these in
`ogt-crm-staging` through an authorized administrator. Never paste private keys
into the repository, issue comments or application logs. Use a staging-only SSH
identity and a previously verified host key; do not reuse a broad administrator
key or disable StrictHostKeyChecking.

The existing OGT deployment script names `onlygoodthings.lat` and the SSH alias
`iankmp-vps`; this is not proof that an isolated CRM staging URL is configured.
No public CRM URL has been verified.

## Not complete

This is a session and regression-testing increment, not completion of both CRMs.
Operational campaign/user/moderation screens, full backend build/integration,
required migrations, live MFA, authorized account provisioning and a verified
Contabo deployment remain separate gates. The staging workflow transfers only
static UI files. Do not label it a complete backend deployment or merge the
feature branch to production solely because client tests pass.

Firebase references:
https://firebase.google.com/docs/auth/web/multi-factor
https://firebase.google.com/docs/auth/web/totp-mfa
