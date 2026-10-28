import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('release_guard', ROOT / 'scripts/release_guard.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)
SHA = 'a' * 40


class ReleaseGuardTests(unittest.TestCase):
    def env(self):
        return dict(OGT_ENV='production', OGT_ALLOW_DEV_TOKENS='false',
                    OGT_FIREBASE_CREDENTIALS='/run/secrets/admin.json',
                    OGT_DB_PASSWORD='fixture-not-a-real-password',
                    OGT_LAUNCH_MODE='prelaunch', OGT_PRELAUNCH_CIDRS='203.0.113.7/32')

    def test_production_security_is_used_before_launch(self):
        self.assertTrue(guard.validate_env(self.env()))

    def test_rejects_missing_or_insecure_settings(self):
        for key, value in [('OGT_ENV', 'development'), ('OGT_ALLOW_DEV_TOKENS', 'true'),
                           ('FIREBASE_AUTH_EMULATOR_HOST', 'localhost:9099'),
                           ('OGT_FIREBASE_PROJECT_ID', 'another-project'),
                           ('OGT_DB_PASSWORD', ''), ('OGT_DB_USER', 'other'),
                           ('OGT_LAUNCH_MODE', 'public'), ('OGT_PRELAUNCH_CIDRS', ''),
                           ('OGT_PRELAUNCH_CIDRS', '0.0.0.0/0'), ('OGT_PRELAUNCH_CIDRS', '::/0'),
                           ('OGT_PRELAUNCH_CIDRS', 'invalid'), ('OGT_PRELAUNCH_CIDRS', '224.0.0.0/4'),
                           ('OGT_FIREBASE_CREDENTIALS', '')]:
            with self.subTest(key=key, value=value):
                values = self.env(); values[key] = value
                with self.assertRaises(ValueError):
                    guard.validate_env(values)

    def test_ipv6_review_network(self):
        values = self.env(); values['OGT_PRELAUNCH_CIDRS'] = '2001:db8::1/128'
        self.assertTrue(guard.validate_env(values))

    def test_payment_candidate_cannot_enable_real_money_or_inline_credentials(self):
        values=self.env();values.update(OGT_MP_MODE='sandbox',OGT_MP_COLLECTOR_ID='12345',
            OGT_MP_ACCESS_TOKEN_FILE='/run/secrets/mercadopago/access-token-test',
            OGT_MP_WEBHOOK_SECRET_FILE='/run/secrets/mercadopago/webhook-secret-test')
        self.assertTrue(guard.validate_env(values))
        for key,value in [('OGT_MP_MODE','live'),('OGT_MP_LIVE_PAYMENTS','true'),
                          ('OGT_MP_ACCESS_TOKEN_FILE','/tmp/token'),('OGT_MP_COLLECTOR_ID',''),
                          ('OGT_MP_PUBLIC_BASE','https://another.example')]:
            invalid=dict(values);invalid[key]=value
            with self.subTest(key=key),self.assertRaises(ValueError):guard.validate_env(invalid)

    def test_webhook_exception_does_not_open_the_rest_of_the_app(self):
        source=(ROOT/'deploy/vps/docker-compose.yml').read_text()
        self.assertIn('Path(`/api/v1/payments/mercadopago/webhook`) && Method(`POST`)',source)
        self.assertIn('ogt-mp.middlewares=ogt-mp-rate@docker,ogt-mp-body@docker',source)
        self.assertIn('ogt-mp-body.buffering.maxRequestBodyBytes=65536',source)
        self.assertIn('ogt-web.middlewares=ogt-prelaunch@docker',source)

    def test_sandbox_buyer_allowlist_is_optional_and_numeric(self):
        values=self.env();values.update(OGT_MP_MODE='sandbox',OGT_MP_COLLECTOR_ID='12345',
            OGT_MP_ACCESS_TOKEN_FILE='/run/secrets/mercadopago/access-token-test',
            OGT_MP_WEBHOOK_SECRET_FILE='/run/secrets/mercadopago/webhook-secret-test',
            OGT_MP_SANDBOX_BUYER_IDS='67890, 123')
        self.assertTrue(guard.validate_env(values))
        for invalid in ['67890,','unknown','1.2','-1']:
            with self.subTest(invalid=invalid),self.assertRaises(ValueError):
                guard.validate_env(dict(values,OGT_MP_SANDBOX_BUYER_IDS=invalid))

    def fixture(self, folder):
        root = Path(folder)
        for name in guard.REQUIRED:
            path = root / name; path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('fixture component, not a working application\n')
        return root

    def test_manifest_covers_all_components(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder)
            guard.make_manifest(root, SHA)
            self.assertEqual(guard.verify_manifest(root, SHA)['scope'], 'app-backend-crms')

    def test_tampering_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder); guard.make_manifest(root, SHA)
            (root / 'web-app/crm/crm.js').write_text('changed')
            with self.assertRaises(ValueError): guard.verify_manifest(root, SHA)

    def test_wrong_commit_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder); guard.make_manifest(root, SHA)
            with self.assertRaises(ValueError): guard.verify_manifest(root, 'b' * 40)

    def test_missing_backend_prevents_ui_only_release(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder); (root / 'backend-dist/bin/backend').unlink()
            with self.assertRaises(ValueError): guard.make_manifest(root, SHA)

    def test_unexpected_file_is_detected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder); guard.make_manifest(root, SHA)
            (root / 'extra.txt').write_text('extra')
            with self.assertRaises(ValueError): guard.verify_manifest(root, SHA)

    def test_private_files_are_rejected(self):
        for name in ['.env', 'key.pem', 'private.key', 'AuthKey.p8', 'signing.p12', 'app.jks', 'access-token-test', 'webhook-secret-test', 'firebase-admin.json']:
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                root = self.fixture(folder); (root / name).write_text('fixture')
                with self.assertRaises(ValueError): guard.make_manifest(root, SHA)

    def test_symlinks_are_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.fixture(folder); (root / 'link').symlink_to('/etc/passwd')
            with self.assertRaises(ValueError): guard.make_manifest(root, SHA)

    def test_dotenv_does_not_execute_shell(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / '.env'
            path.write_text('OGT_DB_PASSWORD="$(not-executed)"\n')
            self.assertEqual(guard.dotenv(path)['OGT_DB_PASSWORD'], '$(not-executed)')


class DeploymentContractTests(unittest.TestCase):
    def test_direct_ports_are_not_public(self):
        source = (ROOT / 'deploy/vps/docker-compose.yml').read_text()
        self.assertIn('127.0.0.1:19080:8080', source)
        self.assertIn('127.0.0.1:19081:80', source)
        self.assertIn('ogt-prelaunch@docker', source)
        self.assertNotIn('docker-entrypoint-initdb.d', source)

    def test_migrations_are_explicit_and_stop_on_error(self):
        source = (ROOT / 'scripts/activate-ogt-release.sh').read_text()
        self.assertIn('ON_ERROR_STOP=1 --single-transaction', source)
        self.assertIn('23_crm_foundations.sql', source)
        self.assertIn('24_post_media_frame.sql', source)
        self.assertIn('25_post_media_original.sql', source)
        self.assertIn('26_crm_publication_index.sql', source)
        self.assertIn('28_crm_user_moderation.sql', source)
        self.assertIn('29_stories.sql', source)
        self.assertIn('30_story_frame.sql', source)
        self.assertIn('31_account_erasure.sql', source)
        self.assertIn('32_user_blocks.sql', source)
        self.assertIn('33_csr_operations.sql', source)
        self.assertIn('34_company_registration.sql', source)
        self.assertLess(source.index('33_csr_operations.sql'), source.index('34_company_registration.sql'))
        self.assertLess(source.index('31_account_erasure.sql'), source.index('32_user_blocks.sql'))
        self.assertLess(source.index('32_user_blocks.sql'), source.index('33_csr_operations.sql'))
        self.assertLess(source.index('28_crm_user_moderation.sql'), source.index('29_stories.sql'))
        self.assertLess(source.index('29_stories.sql'), source.index('30_story_frame.sql'))
        self.assertLess(source.index('30_story_frame.sql'), source.index('31_account_erasure.sql'))
        self.assertLess(source.index('26_crm_publication_index.sql'), source.index('28_crm_user_moderation.sql'))
        self.assertLess(source.index('23_crm_foundations.sql'), source.index('24_post_media_frame.sql'))
        self.assertLess(source.index('24_post_media_frame.sql'), source.index('25_post_media_original.sql'))
        self.assertLess(source.index('25_post_media_original.sql'), source.index('26_crm_publication_index.sql'))
        self.assertNotIn('1[7-9]', source)
        self.assertNotIn('down -v', source)
        self.assertLess(source.index('pg_dump'), source.index('ON_ERROR_STOP=1'))
        self.assertLess(source.index('ON_ERROR_STOP=1'), source.index('docker compose up'))

    def test_crm_assets_have_no_spa_fallback(self):
        source = (ROOT / 'web/app/nginx.conf').read_text()
        crm = source.split('location ^~ /crm/ {', 1)[1].split('}', 1)[0]
        self.assertIn('try_files $uri $uri/ =404', crm)
        self.assertNotIn('/index.html', crm)

    def test_local_default_cannot_publish(self):
        source = (ROOT / 'scripts/deploy-ogt-vps.sh').read_text()
        self.assertIn('MODE="${1:---check}"', source)
        self.assertIn(':backend:test :shared:jvmTest :backend:installDist :shared:jsBrowserProductionWebpack', source)
        self.assertNotIn(':shared:syncWebSdk', source)
        self.assertNotIn('StrictHostKeyChecking=no', source)


if __name__ == '__main__':
    unittest.main()
