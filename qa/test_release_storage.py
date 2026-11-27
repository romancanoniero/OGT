import hashlib
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
from unittest.mock import patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import ogt_retention as retention


class StorageRetentionTests(unittest.TestCase):
    def fixture(self, root):
        for name in ('operations','releases','backups','media'):(root/name).mkdir()
        current='f'*40
        (root/'current-release.json').write_text(json.dumps({'sourceSha':current}))
        (root/'media/protected.mp4').write_bytes(b'live media')
        self.package(root,current+'-20261127T000000Z')
        fresh=root/'backups/scheduled-20261127T000000Z';fresh.mkdir()
        data=b'PGDMPverified backup';(fresh/'database.dump').write_bytes(data);(fresh/'database-toc.txt').write_text('toc')
        (fresh/'backup.json').write_text(json.dumps({'kind':'postgres-custom','completedAtEpochSeconds':int(time.time()),'archiveBytes':len(data),'archiveSha256':hashlib.sha256(data).hexdigest()}))
        for i in range(1,9):self.package(root,str(i)*40+f'-202610{i+10:02}T000000Z')
        return fresh

    def package(self, root, name):
        folder=root/'releases'/name;folder.mkdir()
        files={}
        for name in ('backend-dist/bin/backend','web-app/release.json','Dockerfile','docker-compose.yml','web-nginx.conf'):
            path=folder/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('protected package payload')
            files[name]=retention.digest(path)
        (folder/'release-manifest.json').write_text(json.dumps({'repository':'romancanoniero/OGT','scope':'app-backend-crms','sourceSha':folder.name[:40],'files':files}))
        return folder

    def test_keeps_active_five_recent_manual_unknown_pinned_and_all_media(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.fixture(root)
            pin='1'*40+'-20261011T000000Z'
            (root/'operations/retention-pins.json').write_text(json.dumps([pin]))
            unknown=root/'releases/manual';unknown.mkdir();(unknown/'keep').write_text('untouched')
            broken=root/'releases'/('2'*40+'-20261012T000000Z');(broken/'unknown').write_text('keep')
            result=retention.plan(root)
            self.assertFalse(result['mediaTouched']);self.assertEqual(2,len(result['remove']))
            self.assertIn(pin,result['keptReleases']);self.assertTrue((root/'media/protected.mp4').is_file())
            with patch.object(retention.subprocess,'run'):
                applied=retention.apply(root,result)
            self.assertEqual(2,applied['removedPackages']);self.assertTrue((unknown/'keep').is_file());self.assertTrue((broken/'unknown').is_file())
            self.assertEqual(b'live media',(root/'media/protected.mp4').read_bytes())
            self.assertTrue(list((root/'operations').glob('retention-*.json')))

    def test_fresh_backup_hash_and_current_package_are_required_before_any_deletion(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);fresh=self.fixture(root)
            (fresh/'database.dump').write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'RECENT_VERIFIED_BACKUP_REQUIRED'):retention.plan(root)
            self.assertEqual(9,len(list((root/'releases').iterdir())))

    def test_only_old_exact_ogt_image_tags_are_selected_and_container_rollback_and_manual_images_are_kept(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.fixture(root)
            protected='sha256:'+'b'*64;running='sha256:'+'c'*64
            manual=root/'backups/manual-keep';manual.mkdir();(manual/'image.txt').write_text(protected)
            rows=[{'Repository':'ogt-backend','ID':'sha256:'+'a'*64,'Tag':'a'*40+'-20260901T000000Z'},
                  {'Repository':'ogt-backend','ID':protected,'Tag':'before-'+'b'*40+'-20260901T000000Z'},
                  {'Repository':'ogt-backend','ID':running,'Tag':'c'*40+'-20260901T000000Z'},
                  {'Repository':'ogt-backend','ID':'sha256:'+'d'*64,'Tag':'manual'},
                  {'Repository':'another-project','ID':'sha256:'+'e'*64,'Tag':'e'*40+'-20260901T000000Z'}]
            with patch.object(retention,'docker_inventory',return_value=(rows,{running})):
                result=retention.plan(root,docker=True)
                self.assertEqual(['ogt-backend:'+'a'*40+'-20260901T000000Z'],[x['tag'] for x in result['removeImages']])
                with patch.object(retention.subprocess,'run') as run:
                    run.return_value.returncode=0;retention.apply(root,result)
                commands=[call.args[0] for call in run.call_args_list]
                self.assertIn(['docker','image','rm','--no-prune',result['removeImages'][0]['tag']],commands)
                self.assertFalse(any('--force' in cmd or 'prune' in cmd for cmd in commands))

    def test_changed_plan_or_symlink_never_deletes_a_protected_target(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.fixture(root);reviewed=retention.plan(root)
            candidate=root/reviewed['remove'][0]['path'];(candidate/'unknown').write_text('new protected file')
            with self.assertRaisesRegex(ValueError,'RETENTION_PLAN_CHANGED'):retention.apply(root,reviewed)
            self.assertTrue((candidate/'unknown').is_file())
            (root/'releases'/('a'*40+'-20261001T000000Z')).symlink_to(root/'media',target_is_directory=True)
            self.assertFalse(any(x['path'].endswith('a'*40+'-20261001T000000Z') for x in retention.plan(root)['remove']))

    def test_deployment_backup_rotation_leaves_manual_unknown_and_rollback_files(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.fixture(root)
            old=root/'backups'/('a'*40+'-20260901T000000Z');old.mkdir()
            (old/'database.dump').write_bytes(b'PGDMPold');(old/'database-toc.txt').write_text('toc')
            (old/'published-release.json').write_text(json.dumps({'sourceSha':'a'*40,'scope':'app-backend-crms'}))
            for file in old.iterdir():os.utime(file,(1788220800,1788220800))
            self.assertTrue(any(x['kind']=='backup' for x in retention.plan(root)['remove']))
            (old/'manual-recovery-notes').write_text('keep')
            self.assertFalse(any(x['kind']=='backup' for x in retention.plan(root)['remove']))

    def test_redundant_application_archive_expires_before_its_database_snapshot(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);self.fixture(root)
            old=root/'backups'/('a'*40+'-20261110T000000Z');old.mkdir()
            (old/'database.dump').write_bytes(b'PGDMPold');(old/'database-toc.txt').write_text('toc')
            (old/'application.tgz').write_bytes(b'old application archive')
            (old/'published-release.json').write_text(json.dumps({'sourceSha':'a'*40,'scope':'app-backend-crms'}))
            epoch=time.time()-5*86400
            for file in old.iterdir():os.utime(file,(epoch,epoch))
            reviewed=retention.plan(root)
            item=next(x for x in reviewed['remove'] if x['path'].endswith(old.name))
            self.assertEqual('application-backup',item['kind']);self.assertEqual({'application.tgz'},set(item['files']))
            with patch.object(retention.subprocess,'run'):retention.apply(root,reviewed)
            self.assertFalse((old/'application.tgz').exists());self.assertTrue((old/'database.dump').is_file())
