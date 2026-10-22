"""Exercise real local Git repositories; no SDK secrets or server access required."""
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('sdk_snapshot', ROOT / 'scripts/sdk_committed_snapshot.py')
snapshot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(snapshot)


class SnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='ogt sdk test ')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root / 'source sdk'
        self.destination = self.root / 'release sdk'
        self.source.mkdir()
        self.git('init', '-q')
        self.git('config', 'user.name', 'OGT test')
        self.git('config', 'user.email', 'qa@example.test')
        self.git('config', 'commit.gpgsign', 'false')
        self.git('remote', 'add', 'origin', 'https://github.com/romancanoniero/db-kmp-sdk.git')
        (self.source / 'settings.gradle.kts').write_text('// committed settings\n')
        (self.source / 'code.kt').write_text('// original committed source\n')
        (self.source / '.gitignore').write_text('local.properties\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'fixture')
        self.revision = self.git('rev-parse', 'HEAD')

    def git(self, *args, cwd=None):
        env = dict(os.environ, GIT_OPTIONAL_LOCKS='0')
        return subprocess.check_output(['git', '-c', 'core.hooksPath=/dev/null', '-C', str(cwd or self.source), *args], env=env, stderr=subprocess.PIPE).decode().strip()

    def fingerprint(self):
        return {str(p.relative_to(self.source)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in self.source.rglob('*') if p.is_file()}

    def make(self):
        return snapshot.create_snapshot(self.source, self.destination)

    def test_dirty_tracked_staged_untracked_and_ignored_preserved_byte_for_byte(self):
        (self.source / 'code.kt').write_text('// staged version\n')
        self.git('add', 'code.kt')
        (self.source / 'code.kt').write_text('// working version\n')
        (self.source / 'new.kt').write_text('// untracked local work\n')
        (self.source / 'local.properties').write_text('fixture-local-settings=not-a-real-secret\n')
        self.git('config', 'ogt.private-setting', 'not-copied')
        before = self.fingerprint()
        self.assertEqual(self.make(), self.revision)
        self.assertEqual(before, self.fingerprint())
        self.assertEqual((self.destination / 'code.kt').read_text(), '// original committed source\n')
        self.assertFalse((self.destination / 'new.kt').exists())
        self.assertFalse((self.destination / 'local.properties').exists())
        self.assertNotIn('ogt.private-setting', self.git('config', '--local', '--list', cwd=self.destination))
        self.assertEqual(self.git('status', '--porcelain', cwd=self.destination), '')

    def test_keeps_local_commit_even_when_not_pushed(self):
        (self.source / 'code.kt').write_text('// newer local committed source\n')
        self.git('add', 'code.kt')
        self.git('commit', '-qm', 'local only')
        current = self.git('rev-parse', 'HEAD')
        self.assertEqual(self.make(), current)
        self.assertEqual(self.git('rev-parse', 'HEAD', cwd=self.destination), current)

    def test_detached_source_head(self):
        self.git('checkout', '--quiet', '--detach', self.revision)
        self.assertEqual(self.make(), self.revision)

    def test_existing_destination_is_never_overwritten(self):
        self.destination.mkdir()
        marker = self.destination / 'keep'
        marker.write_text('untouched')
        with self.assertRaises(snapshot.SnapshotError): self.make()
        self.assertEqual(marker.read_text(), 'untouched')

    def test_destination_symlink_rejected(self):
        self.destination.symlink_to(self.root / 'does-not-exist')
        with self.assertRaises(snapshot.SnapshotError): self.make()
        self.assertTrue(self.destination.is_symlink())

    def test_wrong_repository_rejected(self):
        self.git('remote', 'set-url', 'origin', 'https://github.com/example/not-the-sdk.git')
        with self.assertRaises(snapshot.SnapshotError): self.make()
        self.assertFalse(self.destination.exists())

    def test_inside_source_destination_rejected(self):
        before = self.fingerprint()
        with self.assertRaises(snapshot.SnapshotError):
            snapshot.create_snapshot(self.source, self.source / 'copy')
        self.assertEqual(before, self.fingerprint())

    def test_subdirectory_source_rejected(self):
        sub = self.source / 'nested'
        sub.mkdir()
        with self.assertRaises(snapshot.SnapshotError):
            snapshot.create_snapshot(sub, self.destination)

    def test_missing_committed_settings_rejected_and_temp_removed(self):
        self.git('rm', '-q', 'settings.gradle.kts')
        self.git('commit', '-qm', 'missing settings')
        (self.source / 'settings.gradle.kts').write_text('// only uncommitted\n')
        with self.assertRaises(snapshot.SnapshotError): self.make()
        self.assertFalse(self.destination.exists())
        self.assertEqual(list(self.root.glob('.ogt-sdk-copy-*')), [])

    def test_submodules_do_not_silently_disappear(self):
        self.git('update-index', '--add', '--cacheinfo', '160000', self.revision, 'submodule')
        self.git('commit', '-qm', 'fixture gitlink')
        with self.assertRaises(snapshot.SnapshotError): self.make()
        self.assertFalse(self.destination.exists())

    def test_independent_objects_and_preserved_origin(self):
        self.make()
        self.assertFalse((self.destination / '.git/objects/info/alternates').exists())
        self.assertEqual(self.git('remote', 'get-url', 'origin', cwd=self.destination),
                         'https://github.com/romancanoniero/db-kmp-sdk.git')
        source_inodes = {p.stat().st_ino for p in (self.source / '.git/objects').rglob('*') if p.is_file()}
        destination_inodes = {p.stat().st_ino for p in (self.destination / '.git/objects').rglob('*') if p.is_file()}
        self.assertFalse(source_inodes & destination_inodes)


class WrapperTests(unittest.TestCase):
    def test_snapshot_requires_explicit_option_and_is_cleaned_on_exit(self):
        source = (ROOT / 'scripts/publish-ogt-from-mac.sh').read_text()
        self.assertIn('if [[ "${2:-}" == --sdk-committed ]]; then', source)
        self.assertIn('export OGT_DB_KMP_SDK_PATH="$SDK"', source)
        self.assertIn('trap cleanup EXIT', source)
        self.assertIn('git -C "$SDK" status --porcelain', source)
        self.assertLess(source.index('scripts/sdk_committed_snapshot.py'), source.index('python3 - --apply'))
        for command in ('git stash', 'git reset', 'git clean', 'git add', 'git commit'):
            self.assertNotIn(command, source)


if __name__ == '__main__':
    unittest.main()
