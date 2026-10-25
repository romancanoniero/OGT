"""Dry-run validation for the one-time, restricted OGT Identity Platform upgrade."""
import unittest
from scripts.ogt_enable_gcip_once import mfa_facts, permitted_state, factor_ids


def config(subtype="FIREBASE_AUTH", general="DISABLED",
           totp="ENABLED", intervals=1):
    return {"subtype": subtype,
            "mfa": {"state": general, "providerConfigs": [
                {"state": totp, "totpProviderConfig": {"adjacentIntervals": intervals}}
            ]}}


class UpgradeGuardTest(unittest.TestCase):
    def test_observed_ogt_configuration_is_eligible(self):
        self.assertEqual(permitted_state(config()), {
            "subtype": "FIREBASE_AUTH", "general": "DISABLED",
            "totp": "ENABLED", "intervals": 1})

    def test_already_upgraded_project_is_idempotent(self):
        facts = permitted_state(config(subtype="IDENTITY_PLATFORM", general="ENABLED"))
        self.assertEqual(facts["general"], "ENABLED")

    def test_mfa_mandatory_cannot_be_changed_silently(self):
        with self.assertRaises(ValueError):
            permitted_state(config(general="MANDATORY"))

    def test_prevent_tolerance_and_provider_changes(self):
        with self.assertRaises(ValueError):
            permitted_state(config(intervals=5))
        with self.assertRaises(ValueError):
            permitted_state(config(totp="DISABLED"))

    def test_unknown_product_never_changes(self):
        with self.assertRaises(ValueError):
            permitted_state(config(subtype="SUBTYPE_UNSPECIFIED"))

    def test_enrollment_comparison_never_exposes_ids(self):
        before = {"mfaInfo": [{"mfaEnrollmentId": "secret-1", "totpInfo": {}}]}
        after = {"mfaInfo": [{"mfaEnrollmentId": "secret-1", "totpInfo": {}}]}
        self.assertEqual(factor_ids(before), factor_ids(after))
        self.assertNotEqual(factor_ids(before), factor_ids({"mfaInfo": []}))


if __name__ == "__main__":
    unittest.main()
