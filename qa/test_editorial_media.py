import copy
import hashlib
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import replace_editorial_media as media


class EditorialMediaTests(unittest.TestCase):
    def setUp(self):
        self.entries = json.loads((ROOT / 'store/editorial-media-rights.json').read_text())['entries']
        self.records = {e['postId']: e for e in json.loads((ROOT / 'store/editorial-media/provenance.json').read_text())['entries']}

    def test_current_illustrations_bind_delivery_master_and_reference_rights(self):
        illustrations = [e for e in self.entries if e.get('kind') == 'generated-illustration']
        self.assertEqual(18, len(illustrations))
        for e in illustrations:
            with self.subTest(subject=e['subject']):
                media.validate_illustration(e, self.records[e['postId']])
                self.assertEqual(e['originalSha256'], hashlib.sha256((ROOT / e['asset']).read_bytes()).hexdigest())

    def test_historical_reference_is_bound_to_exact_subject_source_and_bytes(self):
        entry = next(e for e in self.entries if e['subject'] == 'Carlos Saavedra Lamas')
        record = self.records[entry['postId']]
        for change in ('subject', 'source', 'bytes', 'territory', 'photo_in_app'):
            e, r = copy.deepcopy(entry), copy.deepcopy(record)
            ref = r['referenceImages'][0]
            if change == 'subject': e['postId'] = 'a2000000-0000-4000-8000-000000000099'
            elif change == 'source': ref['sourcePage'] += '?another-source'
            elif change == 'bytes': ref['sha256'] = '0' * 64
            elif change == 'territory': ref['licence'] = 'Public domain'
            else: ref['sourcePhotographIncludedInApp'] = True
            with self.subTest(change=change), self.assertRaises(AssertionError):
                media.validate_reference_rights(e, r, ref)

    def test_creative_commons_adaptations_keep_the_reference_licence(self):
        e = copy.deepcopy(next(e for e in self.entries if e['subject'] == 'Quino'))
        r = self.records[e['postId']]
        e['licence'] = 'Recurso generado para OGT por encargo del titular'
        with self.assertRaises(AssertionError):
            media.validate_reference_rights(e, r, r['referenceImages'][0])


if __name__ == '__main__':
    unittest.main()
