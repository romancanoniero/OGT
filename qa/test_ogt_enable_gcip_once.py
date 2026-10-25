"""Dry-run validation for the one-time, restricted OGT Identity Platform upgrade."""
import unittest
from unittest.mock import patch
from io import BytesIO
from scripts.ogt_enable_gcip_once import mfa_facts, permitted_state, factor_ids, access_req


def config(subtype="FIREBASE_AUTH", general="DISABLED",
           totp="ENABLED", intervals=1):
    return {"subtype": subtype,
            "mfa": {"state": general, "providerConfigs": [
                {"state": totp, "totpProviderConfig": {"adjacentIntervals": intervals}}
            ]}}


class UpgradeGuardTest(unittest.TestCase):
    def test_service_account_does_not_force_quota_project(self):
        class Response:
            def __enter__(self):
                return self
            def __exit__(self, *_):
                return False
            def read(self):
                return b'{}'
        class Credentials:
            token = 'private-test-token'
        seen = []
        def capture(req, timeout):
            seen.append(req)
            return Response()
        with patch('scripts.ogt_enable_gcip_once.request.urlopen', side_effect=capture):
            access_req('/v2/projects/goodthings-55612/identityPlatform:initializeAuth',
                       Credentials(), method='POST', empty_post=True)
        self.assertEqual(len(seen), 1)
        self.assertEqual(seen[0].method, 'POST')
        self.assertNotIn('X-goog-user-project', seen[0].headers)
        self.assertEqual(seen[0].data, b'')

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
