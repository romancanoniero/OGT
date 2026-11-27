import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import hashlib

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('instagram_import',ROOT/'scripts/import_instagram_posts.py')
importer=importlib.util.module_from_spec(spec)
spec.loader.exec_module(importer)


class InstagramImportTests(unittest.TestCase):
    def fixture(self, root):
        media=Path(root)/'media';media.mkdir()
        payload=b'\xff\xd8\xffsynthetic-local-fixture'
        (media/'photo.jpg').write_bytes(payload)
        source='https://www.instagram.com/qa.cats/'
        return dict(handle='qa.cats',sourceUrl=source,displayName='Gatos QA (importado)',bio='Perfil sintético para QA',posts=[dict(shortcode='QA_POST_1',sourceUrl=source+'p/QA_POST_1/',kind='ADOPTION',category='Adopción',body='Publicación importada para QA.',createdAt='2026-11-27T12:00:00Z',animal=dict(title='Gato QA',petName='QA'),media=[dict(kind='IMAGE',file='media/photo.jpg',sha256=hashlib.sha256(payload).hexdigest())])])

    def prepare(self, root, data):
        path=Path(root)/'manifest.json';path.write_text(json.dumps(data))
        return importer.prepare(path)

    def test_import_is_deterministic_and_plan_rolls_back_without_news_reclassification(self):
        with tempfile.TemporaryDirectory() as root:
            fixture=self.fixture(root)
            first=self.prepare(root,fixture);second=self.prepare(root,fixture)
            self.assertEqual(first['userId'],second['userId'])
            self.assertEqual(first['posts'][0]['id'],second['posts'][0]['id'])
            sql=importer.make_sql(first)
            self.assertTrue(sql.endswith('ROLLBACK;'))
            self.assertIn('ON CONFLICT(id) DO NOTHING',sql)
            self.assertIn('Protected existing data changed',sql)
            self.assertIn('source_url IS NULL',sql)
            self.assertIn('attribution_url',sql)
            self.assertNotIn('UPDATE users',sql)
            self.assertNotIn('DELETE FROM social_posts',sql)

    def test_rejects_wrong_sources_missing_disclosure_duplicate_posts_and_truncated_carousels(self):
        with tempfile.TemporaryDirectory() as root:
            original=self.fixture(root)
            variants=[]
            changed=copy.deepcopy(original);changed['posts'][0]['sourceUrl']='https://example.test/post';variants.append(changed)
            changed=copy.deepcopy(original);changed['displayName']='Unmarked profile';variants.append(changed)
            changed=copy.deepcopy(original);changed['posts']*=2;variants.append(changed)
            changed=copy.deepcopy(original);changed['posts'][0]['media']*=11;variants.append(changed)
            changed=copy.deepcopy(original);changed['posts'][0]['media'][0]['durationMs']='0); DELETE FROM users;';variants.append(changed)
            for variant in variants:
                with self.subTest(variant=variants.index(variant)), self.assertRaises(ValueError):self.prepare(root,variant)

    def test_location_requires_evidence_and_never_defaults_to_the_profile_neighborhood(self):
        with tempfile.TemporaryDirectory() as root:
            data=self.fixture(root)
            sql=importer.make_sql(self.prepare(root,data))
            self.assertNotIn('ST_MakePoint(-58.4495,-34.6204)',sql)
            self.assertIn('Ubicación pendiente de confirmar',sql)
            location=dict(label='Córdoba (zona aproximada)',latitude=-31.42,longitude=-64.18,sourceUrl=data['posts'][0]['sourceUrl'],evidence='Ubicación declarada en la publicación')
            data['posts'][0]['location']=location
            sql=importer.make_sql(self.prepare(root,data))
            self.assertIn('ST_MakePoint(-64.18,-31.42)',sql)
            self.assertIn('location,gathering_place',sql)
            for invalid in [dict(location,latitude=float('nan')),dict(location,longitude=None),dict(location,evidence='')]:
                data['posts'][0]['location']=invalid
                with self.assertRaises(ValueError):self.prepare(root,data)

    def test_changed_or_outside_media_is_rejected_before_a_server_write(self):
        with tempfile.TemporaryDirectory() as root:
            original=self.fixture(root)
            (Path(root)/'media/photo.jpg').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'hash changed'):self.prepare(root,original)
            original['posts'][0]['media'][0]['file']='../outside.jpg'
            with self.assertRaises(ValueError):self.prepare(root,original)


if __name__=='__main__':unittest.main()
