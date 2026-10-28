import plistlib
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ANDROID='{http://schemas.android.com/apk/res/android}'

class MobileReleaseConfigurationTests(unittest.TestCase):
    def test_ios_release_uses_tls_and_debug_preserves_local_lab_support(self):
        release=plistlib.loads((ROOT/'iosApp/iosApp/Info.plist').read_bytes())
        debug=plistlib.loads((ROOT/'iosApp/iosApp/Info-Debug.plist').read_bytes())
        self.assertNotIn('NSAppTransportSecurity',release)
        self.assertTrue(debug['NSAppTransportSecurity']['NSAllowsArbitraryLoads'])
        baseline=dict(debug);baseline.pop('NSAppTransportSecurity')
        self.assertEqual(release,baseline)
        project=(ROOT/'iosApp/iosApp.xcodeproj/project.pbxproj').read_text()
        debug_block=project.split('A900000000000003 /* Debug */ = {',1)[1].split('name = Debug;',1)[0]
        release_block=project.split('A900000000000004 /* Release */ = {',1)[1].split('name = Release;',1)[0]
        self.assertIn('INFOPLIST_FILE = iosApp/Info-Debug.plist;',debug_block)
        self.assertIn('INFOPLIST_FILE = iosApp/Info.plist;',release_block)

    def test_android_release_rejects_cleartext_without_removing_debug_http(self):
        release=ET.parse(ROOT/'composeApp/src/androidMain/AndroidManifest.xml').getroot().find('application')
        debug=ET.parse(ROOT/'composeApp/src/debug/AndroidManifest.xml').getroot().find('application')
        self.assertEqual(release.attrib[ANDROID+'usesCleartextTraffic'],'false')
        self.assertEqual(debug.attrib[ANDROID+'usesCleartextTraffic'],'true')
        session=(ROOT/'shared/src/commonMain/kotlin/com/onlygoodthings/shared/data/SessionStore.kt').read_text()
        self.assertIn('https://onlygoodthings.lat',session)
        self.assertIn('wss://onlygoodthings.lat/db',session)
        self.assertNotIn('ws://217.216.',session)

    def test_privacy_manifest_is_in_resources_and_covers_observed_apis(self):
        manifest=plistlib.loads((ROOT/'iosApp/iosApp/PrivacyInfo.xcprivacy').read_bytes())
        reasons={entry['NSPrivacyAccessedAPIType']:entry['NSPrivacyAccessedAPITypeReasons'] for entry in manifest['NSPrivacyAccessedAPITypes']}
        self.assertEqual(reasons,{
            'NSPrivacyAccessedAPICategoryUserDefaults':['CA92.1'],
            'NSPrivacyAccessedAPICategorySystemBootTime':['35F9.1'],
            'NSPrivacyAccessedAPICategoryFileTimestamp':['C617.1'],
        })
        self.assertFalse(manifest['NSPrivacyTracking'])
        self.assertIn('A100000000000007 /* PrivacyInfo.xcprivacy in Resources */,',(ROOT/'iosApp/iosApp.xcodeproj/project.pbxproj').read_text())

if __name__=='__main__':unittest.main()
