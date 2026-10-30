import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import complete_editorial_honorees as honorees


class EditorialHonoreesTests(unittest.TestCase):
    def inventory(self):
        return json.loads((ROOT / 'store/editorial-media-rights.json').read_text())

    def test_reviewed_names_use_exact_publication_identity_and_source(self):
        entries = honorees.reviewed_entries(self.inventory())
        self.assertEqual(19, len({e['postId'] for e in entries}))
        self.assertIn('René Favaloro', {e['name'] for e in entries})
        self.assertIn('Lionel Messi', {e['name'] for e in entries})

    def test_partial_duplicate_or_invalid_inventory_is_rejected_before_ssh(self):
        for change in ('partial', 'duplicate', 'blank', 'control', 'source'):
            data = self.inventory()
            if change == 'partial': data['entries'].pop()
            elif change == 'duplicate': data['entries'][1]['postId'] = data['entries'][0]['postId']
            elif change == 'blank': data['entries'][0]['subject'] = ' '
            elif change == 'control': data['entries'][0]['subject'] = 'Name\nInjected'
            else: data['entries'][0]['expectedNewsSource'] = 'javascript:invalid'
            with self.subTest(change=change), self.assertRaises(ValueError):
                honorees.reviewed_entries(data)

    def test_plan_cannot_write_existing_data_and_apply_only_fills_missing_names(self):
        entries = honorees.reviewed_entries(self.inventory())
        plan = honorees.transaction(entries)
        self.assertNotIn('UPDATE social_posts', plan)
        apply = honorees.transaction(entries, True)
        self.assertIn("p.honoree_name IS NULL OR btrim(p.honoree_name)=''", apply)
        self.assertIn('EDITORIAL_BASELINE_CHANGED', apply)
        self.assertIn('PROTECTED_DATA_CHANGED', apply)
        for forbidden in ('DELETE ', 'TRUNCATE ', 'UPDATE post_media', 'UPDATE post_comments'):
            self.assertNotIn(forbidden, apply)
        self.assertEqual("'O''Brien'", honorees.quote("O'Brien"))


if __name__ == '__main__':
    unittest.main()
