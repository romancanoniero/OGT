"""Minimal, secret-free checks for OGT Apple Firebase config identity."""
import plistlib
import unittest

from scripts.ogt_ios_firebase_config import validated_plist, PROJECT, IOS_APP_ID, BUNDLE

class FirebaseIosConfigTest(unittest.TestCase):
    def example(self):
        return {
            "PROJECT_ID": PROJECT,
            "GOOGLE_APP_ID": IOS_APP_ID,
            "BUNDLE_ID": BUNDLE,
            "GCM_SENDER_ID": "521063054927",
            "API_KEY": "dummy-for-test-only",
            "CLIENT_ID": "dummy-for-test-only.apps.googleusercontent.com",
        }

    def test_valid_ogt_app_identity(self):
        raw = plistlib.dumps(self.example())
        self.assertEqual(validated_plist(raw), raw)

    def test_android_app_id_cannot_be_used_for_apple(self):
        data = self.example()
        data["GOOGLE_APP_ID"] = "1:521063054927:android:invalid"
        with self.assertRaisesRegex(ValueError, "GOOGLE_APP_ID"):
            validated_plist(plistlib.dumps(data))

    def test_wrong_bundle_or_project_rejected(self):
        for key, incorrect in [("BUNDLE_ID", "com.another.app"), ("PROJECT_ID", "wrong-project")]:
            with self.subTest(key=key):
                data = self.example()
                data[key] = incorrect
                with self.assertRaisesRegex(ValueError, key):
                    validated_plist(plistlib.dumps(data))

    def test_missing_oauth_and_api_key_rejected(self):
        for key in ("CLIENT_ID", "API_KEY"):
            data = self.example()
            data.pop(key)
            with self.assertRaisesRegex(ValueError, "lacks required"):
                validated_plist(plistlib.dumps(data))

if __name__ == "__main__":
    unittest.main()
