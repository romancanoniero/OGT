import importlib.util
from pathlib import Path
import unittest
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import serialization
spec=importlib.util.spec_from_file_location('apple_config',Path(__file__).resolve().parents[1]/'scripts/ogt_apple_provider_config.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
class AppleProviderConfigTest(unittest.TestCase):
    def config(self):return {'name':m.RESOURCE,'enabled':True,'appleSignInConfig':{'bundleIds':[m.BUNDLE,'preserved.related.app']}}
    def pem(self):return ec.generate_private_key(ec.SECP256R1()).private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()).decode()
    def test_patch_is_limited_to_service_id_and_apple_code_flow(self):
        before=self.config();patch=m.prepare_patch(before,m.SERVICE,'QAKEY12345',self.pem())
        self.assertEqual(set(patch),{'clientId','appleSignInConfig'})
        self.assertEqual(set(patch['appleSignInConfig']),{'codeFlowConfig'})
        self.assertEqual(before['appleSignInConfig']['bundleIds'],[m.BUNDLE,'preserved.related.app'])
        self.assertNotIn('bundleIds',m.MASK);self.assertNotIn('enabled',m.MASK)
    def test_conflicting_existing_identity_or_key_is_never_replaced(self):
        for change in [{'clientId':'another.app'},{'name':'projects/another/defaultSupportedIdpConfigs/apple.com'},{'enabled':False},{'appleSignInConfig':{'bundleIds':[]}}, {'appleSignInConfig':{'bundleIds':[m.BUNDLE],'codeFlowConfig':{'teamId':'OTHERTEAM'}}}]:
            with self.assertRaises(ValueError):m.prepare_patch(self.config()|change,m.SERVICE,'QAKEY12345',self.pem())
    def test_project_number_is_the_same_canonical_project(self):
        d=self.config();d['name']='projects/521063054927/defaultSupportedIdpConfigs/apple.com'
        self.assertEqual(m.prepare_patch(d,m.SERVICE,'QAKEY12345',self.pem())['clientId'],m.SERVICE)
    def test_receipt_never_contains_the_private_key(self):
        d=self.config();d['appleSignInConfig']['codeFlowConfig']={'teamId':m.TEAM,'keyId':'QAKEY12345','privateKey':'never print'}
        self.assertNotIn('privateKey',str(m.configured_facts(d)))
if __name__=='__main__':unittest.main()
