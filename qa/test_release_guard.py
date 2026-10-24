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
        for name in ['.env', 'key.pem', 'private.key']:
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
