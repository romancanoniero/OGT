import hashlib
import io
import json
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import ogt_offsite_backup as offsite

class ErasureRecoveryJournalTests(unittest.TestCase):
    def test_a_failed_secondary_journal_does_not_suppress_the_existing_daily_backup(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);config=root/'config.json';config.write_text('{}');config.chmod(0o600)
            with patch.object(Path,'home',return_value=root),patch.object(sys,'argv',['offsite','--config',str(config)]),patch.object(sys,'stdout',io.StringIO()),patch.object(offsite,'refresh_erasure_journal',side_effect=offsite.BackupError('ERASURE_JOURNAL_UNAVAILABLE')),patch.object(offsite,'backup',return_value={'completedAtEpochSeconds':1234}) as backup:
                offsite.main()
            backup.assert_called_once()
            status=json.loads((root/'.local/state/ogt/offsite-backup/status.json').read_text())
            self.assertEqual('warning',status['status']);self.assertEqual(1234,status['lastSuccessEpochSeconds'])
    def test_hourly_journal_is_encrypted_and_a_restored_source_cannot_forget_prior_erasures(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);key=root/'key';key.write_bytes(b'k'*32);key.chmod(0o600);config={'keyFile':str(key)}
            now=int(time.time());first={'kind':offsite.ERASURE_KIND,'capturedAtEpochSeconds':now,'hashes':['a'*64]}
            with patch.object(offsite,'destination',return_value=root),patch.object(offsite,'remote',return_value=json.dumps(first).encode()):
                receipt=offsite.refresh_erasure_journal(config)
            encrypted=(root/'erasure-journal.aesgcm').read_bytes()
            self.assertNotIn(b'a'*64,encrypted);self.assertEqual(1,receipt['hashCount'])
            second={**first,'hashes':['b'*64]}
            with patch.object(offsite,'destination',return_value=root),patch.object(offsite,'remote',return_value=json.dumps(second).encode()):
                offsite.refresh_erasure_journal(config)
            merged=offsite.open_erasure_journal((root/'erasure-journal.aesgcm').read_bytes(),b'k'*32)
            self.assertEqual(['a'*64,'b'*64],merged['hashes'])
            self.assertEqual(0o600,(root/'erasure-journal.aesgcm').stat().st_mode&0o777)
            self.assertEqual({'key','erasure-journal.aesgcm'},{p.name for p in root.iterdir()})
    def test_bad_order_duplicates_identifiers_and_clock_cannot_replace_the_good_journal(self):
        for hashes in [['not-a-hash'],['a'*64,'a'*64],['b'*64,'a'*64]]:
            with self.assertRaises(offsite.BackupError):offsite.validate_erasure_journal({'kind':offsite.ERASURE_KIND,'capturedAtEpochSeconds':1,'hashes':hashes})
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);key=root/'key';key.write_bytes(b'k'*32);key.chmod(0o600)
            invalid={'kind':offsite.ERASURE_KIND,'capturedAtEpochSeconds':1,'hashes':[]}
            with patch.object(offsite,'destination',return_value=root),patch.object(offsite,'remote',return_value=json.dumps(invalid).encode()):
                with self.assertRaisesRegex(offsite.BackupError,'SOURCE_CLOCK'):offsite.refresh_erasure_journal({'keyFile':str(key)})
            self.assertFalse((root/'erasure-journal.aesgcm').exists())
    def test_ciphertext_tampering_is_rejected_without_exposing_contents(self):
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        payload=json.dumps({'kind':offsite.ERASURE_KIND,'capturedAtEpochSeconds':1,'hashes':['a'*64]}).encode()
        nonce=b'n'*12;encrypted=offsite.ERASURE_MAGIC+nonce+AESGCM(b'k'*32).encrypt(nonce,payload,offsite.ERASURE_AAD)
        self.assertEqual(1,len(offsite.open_erasure_journal(encrypted,b'k'*32)['hashes']))
        changed=encrypted[:-1]+bytes([encrypted[-1]^1])
        with self.assertRaisesRegex(offsite.BackupError,'AUTHENTICATION'):offsite.open_erasure_journal(changed,b'k'*32)

if __name__=='__main__':unittest.main()
