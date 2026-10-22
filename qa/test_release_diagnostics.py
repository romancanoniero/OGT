"""Safe local diagnostic fixtures; never connect to the VPS or use real secrets."""
import contextlib
import errno
import fcntl
import importlib.util
import io
import ipaddress
import os
from pathlib import Path
import socket
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('review_diagnostics', ROOT / 'scripts/prepare-ogt-review.py')
review = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(review)
SECRET = 'fixture-private-value-must-not-appear'
ENV = ('OGT_ENV=production\nOGT_ALLOW_DEV_TOKENS=false\n'
       'OGT_FIREBASE_CREDENTIALS=/private/' + SECRET + '\n'
       'OGT_DB_PASSWORD=' + SECRET + '\n')
PEER = ('8.8.8.8', ipaddress.ip_address('1.1.1.1'))


class DiagnosticTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.env = self.root / '.env'
        self.env.write_text(ENV)
        self.peer = patch.object(review, 'ssh_peer', return_value=PEER)
        self.resolve = patch.object(review, 'resolve_domain', return_value={PEER[1]})
        self.peer.start(); self.resolve.start()
        self.addCleanup(self.peer.stop); self.addCleanup(self.resolve.stop)

    def run_diagnostic(self):
        stream = io.StringIO()
        with contextlib.redirect_stdout(stream), patch.object(review, 'save_env', side_effect=AssertionError('must not write')):
            status = review.diagnose(self.root)
        output = stream.getvalue()
        self.assertNotIn(SECRET, output)
        return status, output

    def test_healthy_readonly_diagnosis_never_creates_lock_or_backup(self):
        before = {p.name: p.read_bytes() for p in self.root.iterdir()}
        code, out = self.run_diagnostic()
        self.assertEqual(code, 0)
        self.assertIn('OK [CONFIGURACION]', out)
        self.assertEqual(before, {p.name: p.read_bytes() for p in self.root.iterdir()})

    def test_existing_lock_is_not_changed(self):
        lock = self.root / '.release.lock'; lock.write_bytes(b'unchanged')
        code, _ = self.run_diagnostic()
        self.assertEqual(code, 0)
        self.assertEqual(lock.read_bytes(), b'unchanged')

    def test_active_exclusive_lock_is_reported_without_removal(self):
        path = self.root / '.release.lock'
        with path.open('w') as locked:
            fcntl.flock(locked, fcntl.LOCK_EX | fcntl.LOCK_NB)
            code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [BLOQUEO]', out)
        self.assertIn('OK [CONFIGURACION]', out)
        self.assertTrue(path.is_file())

    def test_permission_denial_is_specific_and_does_not_print_exception_path(self):
        with patch.object(review, 'read_env', side_effect=PermissionError(errno.EACCES, SECRET, SECRET)):
            code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [LECTURA_ENV]: Permiso denegado', out)

    def test_missing_env_is_distinguished(self):
        self.env.unlink()
        code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [LECTURA_ENV]', out)
        self.assertIn('ENOENT', out)

    def test_dns_failure_does_not_hide_other_checks(self):
        with patch.object(review, 'resolve_domain', side_effect=socket.gaierror(socket.EAI_AGAIN, SECRET)):
            code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [DNS]', out)
        self.assertIn('EAI_AGAIN', out)
        self.assertIn('OK [LECTURA_ENV]', out)
        self.assertIn('OK [CONFIGURACION]', out)

    def test_multiple_failures_in_one_run(self):
        self.env.unlink()
        with patch.object(review, 'resolve_domain', side_effect=socket.gaierror(socket.EAI_NONAME, SECRET)):
            code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [DNS]', out)
        self.assertIn('ERROR [LECTURA_ENV]', out)
        self.assertIn('2 bloqueo(s)', out)

    def test_dns_mismatch_does_not_change_policy(self):
        with patch.object(review, 'resolve_domain', return_value={ipaddress.ip_address('9.9.9.9')}):
            code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [DESTINO_DNS]', out)
        self.assertEqual(self.env.read_text(), ENV)

    def test_bad_cidr_never_prints_value(self):
        self.env.write_text(ENV + 'OGT_PRELAUNCH_CIDRS=' + SECRET + '\n')
        code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [CONFIGURACION]', out)
        self.assertIn('CIDR invalido', out)

    def test_invalid_utf8_does_not_print_bytes(self):
        self.env.write_bytes(SECRET.encode() + b'\xff')
        code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('UTF-8', out)

    def test_symlink_env_is_not_read(self):
        other = self.root / 'private'; other.write_text(ENV)
        self.env.unlink(); self.env.symlink_to(other)
        code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [LECTURA_ENV]', out)
        self.assertEqual(other.read_text(), ENV)

    def test_lock_symlink_is_rejected(self):
        (self.root / '.release.lock').symlink_to(self.env)
        code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [BLOQUEO]', out)
        self.assertEqual(self.env.read_text(), ENV)

    def test_directory_write_permission_checked_without_creating_test_file(self):
        with patch.object(review.os, 'access', return_value=False):
            code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [PERMISO_RESPALDO]', out)
        self.assertEqual([p.name for p in self.root.iterdir()], ['.env'])

    def test_timeout_is_specific(self):
        with patch.object(review, 'resolve_domain', side_effect=TimeoutError(SECRET)):
            code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('ERROR [DNS]: Tiempo limite', out)

    def test_missing_firebase_configuration_still_blocks(self):
        self.env.write_text('OGT_ENV=production\nOGT_DB_PASSWORD=' + SECRET + '\n')
        code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('Faltan credenciales Firebase', out)

    def test_unrestricted_network_still_blocks(self):
        self.env.write_text(ENV + 'OGT_PRELAUNCH_CIDRS=0.0.0.0/0\n')
        code, out = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertIn('rango no permitido', out)

    def test_public_mode_not_changed(self):
        body = ENV + 'OGT_LAUNCH_MODE=public\n'
        self.env.write_text(body)
        code, _ = self.run_diagnostic()
        self.assertEqual(code, 1)
        self.assertEqual(self.env.read_text(), body)


class DiagnosticUnitTests(unittest.TestCase):
    def test_raw_os_errors_never_expose_secrets(self):
        for error in [PermissionError(errno.EACCES, SECRET, SECRET),
                      FileNotFoundError(errno.ENOENT, SECRET, SECRET),
                      OSError(errno.ENOSPC, SECRET, SECRET),
                      ValueError(SECRET),
                      UnicodeDecodeError('utf8', SECRET.encode(), 0, 1, SECRET)]:
            with self.subTest(error=type(error).__name__):
                self.assertNotIn(SECRET, review.safe_failure('LECTURA_ENV', error))
        self.assertIn('ENOSPC', review.safe_failure('GUARDADO_ENV', OSError(errno.ENOSPC, SECRET)))

    def test_bad_ssh_address_redacted(self):
        with patch.dict(os.environ, {'SSH_CONNECTION': SECRET + ' 22 1.1.1.1 22'}):
            with self.assertRaises(review.PreparationError) as caught:
                review.ssh_peer()
        self.assertNotIn(SECRET, str(caught.exception))

    def test_apply_dns_failure_identifies_stage_and_never_saves(self):
        stream = io.StringIO()
        with patch('sys.argv', ['review', '--apply']), \
             patch.object(review, 'ssh_peer', return_value=PEER), \
             patch.object(review, 'resolve_domain', side_effect=socket.gaierror(socket.EAI_AGAIN, SECRET)), \
             patch.object(review, 'save_env') as save, contextlib.redirect_stderr(stream):
            with self.assertRaises(SystemExit) as caught:
                review.main()
        self.assertEqual(caught.exception.code, 1)
        self.assertIn('ERROR [DNS]', stream.getvalue())
        self.assertNotIn(SECRET, stream.getvalue())
        save.assert_not_called()

    def test_diagnose_and_apply_are_mutually_exclusive(self):
        with patch('sys.argv', ['review', '--diagnose', '--apply']), contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as caught:
                review.main()
        self.assertEqual(caught.exception.code, 2)

    def test_existing_preparation_remains_idempotent(self):
        once, _ = review.prepare_text(ENV, '8.8.8.8')
        twice, _ = review.prepare_text(once, '8.8.8.8')
        self.assertEqual(once, twice)
        self.assertTrue(once.startswith(ENV))

    def test_resolver_cancels_alarm_on_failure(self):
        with patch.object(review.socket, 'getaddrinfo', side_effect=socket.gaierror(socket.EAI_NONAME, SECRET)), \
             patch.object(review.signal, 'signal', return_value='old_handler') as set_handler, \
             patch.object(review.signal, 'alarm') as alarm:
            with self.assertRaises(socket.gaierror):
                review.resolve_domain()
        self.assertEqual(alarm.call_args_list[-1].args, (0,))
        self.assertEqual(set_handler.call_args_list[-1].args, (review.signal.SIGALRM, 'old_handler'))


if __name__ == '__main__':
    unittest.main()
