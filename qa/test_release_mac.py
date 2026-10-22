"""Local helper tests only: these do not connect to Contabo or compile Kotlin."""
import importlib.util
import os
import re
import subprocess
import stat
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('ogt_review', ROOT / 'scripts/prepare-ogt-review.py')
review = importlib.util.module_from_spec(spec)
spec.loader.exec_module(review)

BASE = ('# preserve this exact comment\nOGT_ENV=production\n'
        'OGT_ALLOW_DEV_TOKENS=false\nOGT_DB_PASSWORD="private test value"\n'
        'OGT_FIREBASE_CREDENTIALS=/run/secrets/firebase.json\n')


class ReviewConfigurationTests(unittest.TestCase):
    def test_adds_private_review_without_touching_credentials(self):
        result, networks = review.prepare_text(BASE, '8.8.8.8')
        self.assertTrue(result.startswith(BASE))
        self.assertEqual(networks, '8.8.8.8/32')
        self.assertIn('OGT_LAUNCH_MODE=prelaunch\n', result)

    def test_ipv6_uses_single_address(self):
        _, networks = review.prepare_text(BASE, '2606:4700:4700::1111')
        self.assertEqual(networks, '2606:4700:4700::1111/128')

    def test_idempotent(self):
        once, _ = review.prepare_text(BASE, '8.8.8.8')
        twice, _ = review.prepare_text(once, '8.8.8.8')
        self.assertEqual(once, twice)

    def test_existing_allowed_network_retained(self):
        old = BASE + 'OGT_LAUNCH_MODE=prelaunch\nOGT_PRELAUNCH_CIDRS=8.8.8.0/24\n'
        result, networks = review.prepare_text(old, '8.8.8.8')
        self.assertEqual(result, old)
        self.assertEqual(networks, '8.8.8.0/24')

    def test_other_reviewers_are_not_removed(self):
        old = BASE + 'OGT_LAUNCH_MODE=prelaunch\nOGT_PRELAUNCH_CIDRS=1.1.1.1/32\n'
        _, networks = review.prepare_text(old, '8.8.8.8')
        self.assertEqual(networks, '1.1.1.1/32,8.8.8.8/32')

    def test_public_mode_not_overwritten(self):
        with self.assertRaises(ValueError):
            review.prepare_text(BASE + 'OGT_LAUNCH_MODE=public\n', '8.8.8.8')

    def test_unrestricted_network_rejected(self):
        with self.assertRaises(ValueError):
            review.prepare_text(BASE + 'OGT_PRELAUNCH_CIDRS=0.0.0.0/0\n', '8.8.8.8')

    def test_nonpublic_peer_rejected(self):
        for address in ['127.0.0.1', '10.0.0.2', '::1', '224.0.0.1']:
            with self.subTest(address=address), self.assertRaises(ValueError):
                review.prepare_text(BASE, address)

    def test_lab_auth_not_silently_changed(self):
        for text in [BASE.replace('production', 'development'),
                     BASE.replace('TOKENS=false', 'TOKENS=true'),
                     BASE + 'FIREBASE_AUTH_EMULATOR_HOST=localhost:9099\n']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                review.prepare_text(text, '8.8.8.8')

    def test_firebase_and_database_required(self):
        for key in ['OGT_DB_PASSWORD', 'OGT_FIREBASE_CREDENTIALS']:
            body = ''.join(line for line in BASE.splitlines(keepends=True) if not line.startswith(key))
            with self.subTest(key=key), self.assertRaises(ValueError):
                review.prepare_text(body, '8.8.8.8')

    def test_other_project_rejected(self):
        with self.assertRaises(ValueError):
            review.prepare_text(BASE + 'OGT_FIREBASE_PROJECT_ID=other\n', '8.8.8.8')

    def test_duplicate_env_keys_rejected(self):
        with self.assertRaises(ValueError):
            review.prepare_text(BASE + 'OGT_ENV=production\n', '8.8.8.8')

    def test_comments_and_crlf_preserved(self):
        base = BASE.replace('\n', '\r\n')
        result, _ = review.prepare_text(base, '8.8.8.8')
        self.assertTrue(result.startswith(base))

    def test_no_final_newline_handled(self):
        result, _ = review.prepare_text(BASE.rstrip('\n'), '8.8.8.8')
        self.assertIn('firebase.json\nOGT_LAUNCH_MODE', result)

    def test_private_backup_and_atomic_save(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env'
            path.write_text(BASE)
            after, _ = review.prepare_text(BASE, '8.8.8.8')
            backup = review.save_env(path, BASE, after)
            self.assertEqual(backup.read_text(), BASE)
            self.assertEqual(path.read_text(), after)
            self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(backup.stat().st_mode), 0o600)

    def test_concurrent_change_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env'
            path.write_text(BASE + '# changed\n')
            with self.assertRaises(ValueError):
                review.save_env(path, BASE, BASE + '# new\n')
            self.assertTrue(path.read_text().endswith('# changed\n'))

    def test_symlink_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / 'original'
            target.write_text(BASE)
            path = Path(directory) / '.env'
            path.symlink_to(target)
            with self.assertRaises(ValueError):
                review.save_env(path, BASE, BASE + '# new\n')
            self.assertEqual(target.read_text(), BASE)

    def test_no_write_when_unchanged(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / '.env'
            path.write_text(BASE)
            self.assertIsNone(review.save_env(path, BASE, BASE))
            self.assertEqual(len(list(Path(directory).iterdir())), 1)


class DeploymentSourceTests(unittest.TestCase):
    def setUp(self):
        self.deploy = (ROOT / 'scripts/deploy-ogt-vps.sh').read_text()
        self.mac = (ROOT / 'scripts/publish-ogt-from-mac.sh').read_text()

    def test_build_does_not_run_copy_into_tracked_web_sources(self):
        self.assertNotIn(':shared:syncWebSdk', self.deploy)
        self.assertIn(':shared:jsBrowserProductionWebpack', self.deploy)
        self.assertIn('cp "$artifact" "$WORK/release/web-app/$name"', self.deploy)
        self.assertIn('git diff --exit-code --quiet', self.deploy)

    def test_server_validation_precedes_gradle(self):
        self.assertLess(self.deploy.index('python3 - env'), self.deploy.index('./gradlew'))

    def test_no_static_only_publish_in_wrapper(self):
        self.assertIn('bash scripts/deploy-ogt-vps.sh --deploy', self.mac)
        self.assertNotIn('rsync -', self.mac)
        self.assertIn("app-backend-crms", self.mac)

    def test_known_hosts_and_alias_preserved(self):
        self.assertIn('StrictHostKeyChecking=yes', self.mac)
        self.assertIn('iankmp-vps', self.mac)
        self.assertNotIn('StrictHostKeyChecking=no', self.mac + self.deploy)

    def test_wrapper_cannot_run_accidentally(self):
        self.assertIn('[[ "${1:-}" == --deploy ]]', self.mac)
        self.assertIn('uname -s', self.mac)

    def test_wrapper_rejects_jump_host_inference(self):
        self.assertIn('proxyjump', self.mac)
        self.assertIn('proxycommand', self.mac)

    def test_remote_ssh_peer_probe_executes_without_losing_quotes(self):
        # Exercise the exact shell passed to the remote SSH session. A prior
        # Python one-liner lost its quotes and blocked the real deployment.
        match = re.search(
            r"ACTUAL_SERVER=.*?'(set -- \$SSH_CONNECTION;[^']+)'",
            self.mac,
        )
        self.assertIsNotNone(match, 'Remote SSH peer command is missing')
        script = match.group(1)
        for connection, expected in [
            ('198.51.100.5 54321 217.216.82.209 22', '217.216.82.209'),
            ('198.51.100.5 54321', ''),
        ]:
            with self.subTest(connection=connection):
                env = {**os.environ, 'SSH_CONNECTION': connection}
                result = subprocess.run(
                    ['sh', '-c', script], env=env, capture_output=True,
                    text=True, check=True,
                )
                self.assertEqual(result.stdout.strip(), expected)


if __name__ == '__main__':
    unittest.main()
