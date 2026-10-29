import hashlib
import importlib.util
import io
import json
import os
import shutil
import subprocess
import time
from pathlib import Path
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import ogt_offsite_backup as offsite
import install_ogt_offsite_backup as installer
import ogt_backup as server_backup


def media_tar(name='media/photo.jpg', data=b'image', link=False):
    buffer = io.BytesIO()
    with tarfile.open(fileobj=buffer, mode='w') as archive:
        item = tarfile.TarInfo(name)
        if link:
            item.type = tarfile.SYMTYPE; item.linkname = '/private/protected'
        else:
            item.size = len(data)
        archive.addfile(item, None if link else io.BytesIO(data))
        secret = tarfile.TarInfo(offsite.SIGNING_KEY)
        secret.size = 12
        archive.addfile(secret, io.BytesIO(b'PRIVATE-KEY!'))
    return buffer.getvalue()


class OffsiteTests(unittest.TestCase):
    def fixture(self, folder):
        key = folder / 'key'; key.write_bytes(os.urandom(32)); key.chmod(0o600)
        state = {'databaseSchema': ['users'],
                 'media': {'media/photo.jpg': hashlib.sha256(b'image').hexdigest()},
                 'postgresImage': 'postgis/postgis:16-3.4'}
        metadata = json.dumps({'mediaIncluded': False, 'sourceSha': 'a' * 40,
                               'archiveSha256': hashlib.sha256(b'PGDMPfixture').hexdigest()}).encode()
        replies = [json.dumps(state).encode(), b'{"backupId":"scheduled-20261029T120000Z"}',
                   b'PGDMPfixture', metadata, media_tar(), json.dumps(state).encode()]
        return {'keyFile': str(key)}, state, replies

    def test_real_encryption_roundtrip_and_due_guard_preserve_existing_manual_backup(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); target = root / 'data'; target.mkdir(mode=0o700)
            original = target / '20261029T110336Z'; original.mkdir(); (original / 'protected').write_text('keep')
            config, expected, replies = self.fixture(root)
            with patch.object(offsite, 'destination', return_value=target), patch.object(offsite, 'remote', side_effect=replies) as remote:
                receipt = offsite.backup(config)
                self.assertEqual(6, remote.call_count)
            self.assertTrue(receipt['mediaHashesVerified'])
            self.assertFalse(receipt['isolatedRestorePerformedThisRun'])
            self.assertFalse(receipt['pruningEnabled'])
            folder = next(target.glob('automatic-*'))
            encrypted = (folder / 'database-and-media.tar.gz.aesgcm').read_bytes()
            self.assertNotIn(b'PGDMPfixture', encrypted)
            payload = offsite.unseal(encrypted, offsite.read_key(config['keyFile']))
            with tarfile.open(fileobj=io.BytesIO(payload), mode='r:gz') as tar:
                self.assertNotIn(offsite.SIGNING_KEY, tar.getnames())
                manifest = json.load(tar.extractfile('offsite-manifest.json'))
            self.assertEqual(expected['databaseSchema'], manifest['databaseSchema'])
            offsite.verify_payload(payload, manifest)
            self.assertEqual(0o600, (folder / 'receipt.json').stat().st_mode & 0o777)
            with patch.object(offsite, 'destination', return_value=target), patch.object(offsite, 'remote') as remote:
                self.assertIsNone(offsite.backup(config, True)); remote.assert_not_called()
            self.assertEqual('keep', (original / 'protected').read_text())

    def test_ciphertext_tampering_and_wrong_key_fail_authentication(self):
        key = os.urandom(32); saved = offsite.seal(b'private payload', key)
        damaged = bytearray(saved); damaged[-1] ^= 1
        for blob, used_key in [(bytes(damaged), key), (saved, os.urandom(32))]:
            with self.assertRaises(Exception):
                offsite.unseal(blob, used_key)

    def test_missing_mount_never_creates_a_local_substitute(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / 'not-mounted' / 'data'
            with patch.object(Path, 'is_mount', return_value=False):
                with self.assertRaisesRegex(offsite.BackupError, 'NOT_MOUNTED'):
                    offsite.destination({'mount': str(target.parent), 'destination': str(target)})
            self.assertFalse(target.parent.exists())

    def test_destination_symlink_and_parent_traversal_preserve_unrelated_directory(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); mount = root / 'drive'; mount.mkdir()
            protected = root / 'protected'; protected.mkdir(); (protected / 'keep').write_text('keep')
            (mount / 'linked').symlink_to(protected)
            before = protected.stat().st_mode
            with patch.object(Path, 'is_mount', return_value=True):
                for destination in [mount / 'linked/data', mount / '../protected/data']:
                    with self.assertRaises(offsite.BackupError):
                        offsite.destination({'mount': str(mount), 'destination': str(destination)})
            self.assertEqual(before, protected.stat().st_mode)
            self.assertEqual(['keep'], [p.name for p in protected.iterdir()])

    def test_unsafe_media_members_are_rejected_before_any_extraction(self):
        for name, link in [('media/../../protected', False), ('/media/absolute', False), ('media/link', True), ('media/.unexpected-secret', False)]:
            with self.assertRaises(offsite.BackupError):
                offsite.pack(media_tar(name, link=link), b'PGDMPx', b'{}', {})

    def test_media_change_during_capture_prevents_completed_receipt(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); config, state, replies = self.fixture(root)
            state['media']['media/photo.jpg'] = 'changed-content'
            replies[-1] = json.dumps(state).encode()
            target = root / 'data'; target.mkdir()
            with patch.object(offsite, 'destination', return_value=target), patch.object(offsite, 'remote', side_effect=replies):
                with self.assertRaisesRegex(offsite.BackupError, 'SOURCE_CHANGED'):
                    offsite.backup(config)
            self.assertEqual([], list(target.iterdir()))

    def test_database_bytes_must_match_the_source_dump_before_a_receipt_can_complete(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); config, _, replies = self.fixture(root)
            replies[2] = b'PGDMPdifferent bytes'
            target = root / 'data'; target.mkdir()
            with patch.object(offsite, 'destination', return_value=target), patch.object(offsite, 'remote', side_effect=replies):
                with self.assertRaisesRegex(offsite.BackupError, 'SOURCE_DATABASE_HASH_MISMATCH'):
                    offsite.backup(config)
            self.assertEqual([], list(target.iterdir()))

    def test_remote_failure_reports_the_stage_without_echoing_sensitive_output(self):
        failure = subprocess.CalledProcessError(255, ['ssh'], stderr=b'PRIVATE_SERVER_DIAGNOSTIC')
        with patch.object(offsite.subprocess, 'run', side_effect=failure):
            with self.assertRaisesRegex(offsite.BackupError, '^SOURCE_HASH_FAILED_255$'):
                offsite.remote(['sha256sum', '/known/archive'])

    def test_key_permissions_and_symlink_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); key = root / 'key'; key.write_bytes(os.urandom(32)); key.chmod(0o644)
            with self.assertRaisesRegex(offsite.BackupError, 'KEY_PERMISSIONS'):
                offsite.read_key(key)
            key.chmod(0o600); link = root / 'link'; link.symlink_to(key)
            with self.assertRaises(OSError):
                offsite.read_key(link)

    def test_corrupt_archive_is_not_accepted_as_a_recent_verified_backup(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); target = root / 'data'; target.mkdir()
            config, _, replies = self.fixture(root)
            with patch.object(offsite, 'destination', return_value=target), patch.object(offsite, 'remote', side_effect=replies):
                offsite.backup(config)
            archive = next(target.glob('automatic-*/database-and-media.tar.gz.aesgcm'))
            value = bytearray(archive.read_bytes()); value[-1] ^= 1; archive.write_bytes(value)
            self.assertIsNone(offsite.latest_verified(target))

    def test_installer_refuses_an_unrelated_agent_without_changing_its_contents(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / 'agent.plist'
            source = installer.plistlib.dumps({'Label': 'another.project', 'ProgramArguments': ['protected-job']})
            path.write_bytes(source)
            with self.assertRaisesRegex(offsite.BackupError, 'UNRELATED_EXISTING_AGENT'):
                installer.existing_agent(path, Path(temp) / 'ogt.py')
            self.assertEqual(source, path.read_bytes())

    def test_launch_agent_runs_only_the_backup_runner_and_never_a_shell_or_retention_job(self):
        state = Path('/private/state')
        definition = installer.agent_definition('/python', '/ogt/runner.py', '/private/config.json', state)
        self.assertEqual(['/python', '-B', '/ogt/runner.py', '--config', '/private/config.json', '--if-due'], definition['ProgramArguments'])
        self.assertEqual(3600, definition['StartInterval'])
        self.assertNotIn('KeepAlive', definition)
        self.assertEqual('Background', definition['ProcessType'])

    def test_retention_removes_only_expired_owned_complete_copies_after_a_valid_recent_copy(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); target = root / 'data'; target.mkdir()
            config, _, replies = self.fixture(root)
            with patch.object(offsite, 'destination', return_value=target), patch.object(offsite, 'remote', side_effect=replies):
                offsite.backup(config)
            current = next(target.glob('automatic-*'))
            old = target / 'automatic-20260901T000000000000Z'; shutil.copytree(current, old)
            epoch = 1788220800
            receipt = json.loads((old / 'receipt.json').read_text()); receipt['completedAtEpochSeconds'] = epoch
            (old / 'receipt.json').write_text(json.dumps(receipt))
            for path in old.iterdir():os.utime(path, (epoch, epoch))
            manual = target / '20260901T000000Z'; shutil.copytree(old, manual)
            unknown = target / 'automatic-20260902T000000000000Z'; shutil.copytree(old, unknown); (unknown / 'keep').write_text('protected')
            protected = target / 'automatic-20260903T000000000000Z'; protected.symlink_to(manual, target_is_directory=True)
            self.assertEqual(1, offsite.prune_automatic(target, 30))
            self.assertFalse(old.exists()); self.assertTrue(current.is_dir())
            self.assertTrue(manual.is_dir()); self.assertTrue((unknown / 'keep').is_file()); self.assertTrue(protected.is_symlink())

    def test_retention_without_a_valid_recent_copy_never_deletes_anything(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp); protected = target / 'keep'; protected.write_text('keep')
            with self.assertRaisesRegex(offsite.BackupError, 'RECENT_BACKUP_REQUIRED'):
                offsite.prune_automatic(target, 30)
            self.assertEqual('keep', protected.read_text())

    def test_server_expiry_preserves_current_manual_unknown_and_symlinked_snapshots(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); base = root / 'backups'; base.mkdir()
            fresh = base / 'scheduled-20261029T120000Z'; fresh.mkdir()
            for name, value in [('database.dump', b'PGDMPvalid'), ('database-toc.txt', b'valid TOC')]:
                (fresh / name).write_bytes(value)
            old = base / 'scheduled-20260901T000000Z'; shutil.copytree(fresh, old)
            deployed = base / ('a'*40 + '-20260901T000000Z'); shutil.copytree(fresh, deployed)
            unknown = base / ('b'*40 + '-20260901T000000Z'); shutil.copytree(fresh, unknown); (unknown / 'keep').write_text('protected')
            manual = base / 'manual-20260901T000000Z'; shutil.copytree(fresh, manual)
            linked = base / 'scheduled-20260902T000000Z'; linked.symlink_to(manual, target_is_directory=True)
            for folder in (old, deployed, unknown, manual):
                for path in folder.iterdir():os.utime(path, (1788220800, 1788220800))
            self.assertEqual(2, server_backup.prune_automatic(root, fresh.name, 30))
            self.assertTrue((fresh / 'database.dump').is_file()); self.assertTrue(manual.is_dir())
            self.assertTrue(linked.is_symlink()); self.assertEqual('protected', (unknown / 'keep').read_text())

    def test_server_expiry_refuses_stale_or_invalid_fresh_copy(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp); folder = root / 'backups/scheduled-20260901T000000Z'; folder.mkdir(parents=True)
            (folder / 'database.dump').write_bytes(b'not a dump'); (folder / 'database-toc.txt').write_text('toc')
            with self.assertRaisesRegex(ValueError, 'FRESH_BACKUP_REQUIRED'):
                server_backup.prune_automatic(root, folder.name, 30)
            self.assertTrue((folder / 'database.dump').is_file())

    def test_restore_waits_for_the_actual_database_instead_of_the_temporary_init_server(self):
        path = Path(__file__).parent / 'scripts/verify_offsite_backup.py'
        spec = importlib.util.spec_from_file_location('offsite_restore_test', path)
        verifier = importlib.util.module_from_spec(spec); spec.loader.exec_module(verifier)
        responses = [subprocess.CompletedProcess([], 2, b'', b'database does not exist'),
                     subprocess.CompletedProcess([], 0, b'', b''),
                     subprocess.CompletedProcess([], 0, b'1\n', b'')]
        with patch.object(verifier.subprocess, 'run', side_effect=responses) as run, patch.object(verifier.time, 'sleep'):
            verifier.wait_for_database('only-this-test-container')
        self.assertEqual(3, run.call_count)
        for call in run.call_args_list:
            command = call.args[0]
            self.assertIn('127.0.0.1', command); self.assertIn('ogt_rehearsal', command)
            self.assertIn('only-this-test-container', command)


if __name__ == '__main__':
    unittest.main()
