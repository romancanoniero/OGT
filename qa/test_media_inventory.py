import sys
import tempfile
import os
from pathlib import Path
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import ogt_operations as ops

class MediaInventoryTests(unittest.TestCase):
    def test_counts_actual_files_without_following_symlinks_or_reading_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);media=root/'media';(media/'u').mkdir(parents=True);(media/'stories-private').mkdir()
            (media/'u/photo.jpg').write_bytes(b'a'*101);(media/'u/video.mp4').write_bytes(b'b'*203)
            (media/'stories-private/story.bin').write_bytes(b'c'*307);(media/'stories-private/story-poster.bin').write_bytes(b'd'*41)
            (media/'stories-private/.signing-key').write_bytes(b'SECRET')
            (media/'u/.upload-temp').write_bytes(b'pending')
            other=root/'other-project';other.mkdir();(other/'large.mp4').write_bytes(b'x'*1000)
            (media/'outside').symlink_to(other,target_is_directory=True);(media/'u/link.jpg').symlink_to(other/'large.mp4')
            actual=ops.probe_media(root)
            self.assertTrue(actual['complete']);self.assertEqual(4,actual['files']);self.assertEqual(652,actual['mediaBytes'])
            self.assertEqual(101,actual['uploadImageBytes']);self.assertEqual(203,actual['uploadVideoBytes']);self.assertEqual(307,actual['storyBytes']);self.assertEqual(41,actual['posterBytes'])
            self.assertGreater(actual['diskTotalBytes'],0);self.assertGreaterEqual(actual['allocatedBytes'],0)
            self.assertNotIn('SECRET',str(actual));self.assertNotIn(str(other),str(actual))
            os.link(media/'u/photo.jpg',media/'u/hardlink.jpg')
            linked=ops.probe_media(root);self.assertEqual(actual['allocatedBytes'],linked['allocatedBytes']);self.assertEqual(5,linked['files'])
    def test_incomplete_inventory_never_reports_a_partial_total_as_the_full_storage(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'media').mkdir();(root/'media/a').write_bytes(b'one')
            result=ops.probe_media(root,max_files=0)
            self.assertEqual({'ok':False,'complete':False},result)
            (root/'media/a').unlink();self.assertEqual(0,ops.probe_media(root)['mediaBytes'])

if __name__=='__main__':unittest.main()
