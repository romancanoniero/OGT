"""Deterministic, credential-free tests for OGT MFA prerequisite checks."""
import unittest
from scripts.ogt_mfa_preflight import summarize


class OgtMfaAuditTest(unittest.TestCase):
    def test_disabled_general_mfa_blocks_even_if_totp_provider_enabled(self):
        config = {"mfa": {"state": "DISABLED", "providerConfigs": [
            {"state": "ENABLED", "totpProviderConfig": {"adjacentIntervals": 1}}
        ]}}
        data = summarize(config, {
            "emailVerified": True,
            "mfaInfo": [{"totpInfo": {}, "displayName": "OGT Control",
                         "mfaEnrollmentId": "private-id"}]
        })
        self.assertEqual(data["status"], "BLOCKED_GENERAL_MFA_NOT_ENABLED")
        self.assertEqual(data["totpFactorsCount"], 1)
        self.assertEqual(data["adjacentIntervals"], 1)
        self.assertNotIn("private-id", str(data))
        self.assertNotIn("OGT Control", str(data))

    def test_enrolled_totp_with_enabled_project_is_only_preflight(self):
        data = summarize({"mfa": {"state": "ENABLED", "providerConfigs": [
            {"state": "ENABLED", "totpProviderConfig": {"adjacentIntervals": 5}}
        ]}}, {"emailVerified": True, "mfaInfo": [{"totpInfo": {}}]})
        self.assertEqual(data["status"], "CONFIG_OK_NOT_REAL_TOTP_VERIFIED")

    def test_missing_account_factor_blocks(self):
        data = summarize({"mfa": {"state": "ENABLED", "providerConfigs": [
            {"state": "ENABLED", "totpProviderConfig": {"adjacentIntervals": 1}}
        ]}}, {"emailVerified": True, "mfaInfo": []})
        self.assertEqual(data["status"], "BLOCKED_ACCOUNT_HAS_NO_TOTP")

    def test_provider_disabled_is_blocker(self):
        data = summarize({"mfa": {"state": "ENABLED", "providerConfigs": []}})
        self.assertEqual(data["status"], "BLOCKED_TOTP_PROVIDER_NOT_ENABLED")

    def test_unverified_email_is_blocker(self):
        data = summarize({"mfa": {"state": "ENABLED", "providerConfigs": [
            {"state": "ENABLED", "totpProviderConfig": {"adjacentIntervals": 1}}
        ]}}, {"emailVerified": False, "mfaInfo": [{"totpInfo": {}}]})
        self.assertEqual(data["status"], "BLOCKED_UNVERIFIED_EMAIL")


if __name__ == "__main__":
    unittest.main()
