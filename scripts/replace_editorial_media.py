#!/usr/bin/env python3
"""Replace reviewed editorial media only; defaults to a read-only data plan.

Existing user posts, authors, bodies, sources and discussions are never imported
or reseeded. New immutable URLs make clients discard the previous image cache.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import shlex
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'store/editorial-media-rights.json'
REFERENCE_LICENCES = {
    'CC BY 2.0': 'https://creativecommons.org/licenses/by/2.0',
    'CC BY 4.0': 'https://creativecommons.org/licenses/by/4.0',
    'CC BY-SA 2.0': 'https://creativecommons.org/licenses/by-sa/2.0',
    'CC BY-SA 3.0': 'https://creativecommons.org/licenses/by-sa/3.0',
}
HISTORICAL_IDENTITY_REFERENCES = {
    'a2000000-0000-4000-8000-000000000023': (
        'https://commons.wikimedia.org/wiki/File:Carlos_Saavedra_Lamas.jpg',
        '647505023e9708c9b5d1c0a798b6a8123b93b64ab47a2d00bc665d49c5ac0238',
    ),
    'a2000000-0000-4000-8000-000000000040': (
        'https://commons.wikimedia.org/wiki/File:Elpidio_Gonz%C3%A1lez.JPG',
        'a7de3836935076a6e73736a397612d95f556306ee072785f8fe03d0a74c03df7',
    ),
}


def validate_reference_rights(e, record, reference):
    assert reference['sourcePage'].startswith('https://commons.wikimedia.org/wiki/File:')
    assert reference['creator'] and e['attributionRequired'] is True
    if reference['licence'] == 'Public domain in Argentina (source declaration)':
        # Only these two reviewed historic identity references are admitted.
        # Their source declaration is territorial, not a worldwide licence.
        assert HISTORICAL_IDENTITY_REFERENCES.get(e['postId']) == (reference['sourcePage'], reference['sha256'])
        assert reference['licenceUrl'] == 'https://commons.wikimedia.org/wiki/Template:PD-AR-Photo'
        assert reference['use'] == 'identity-reference-new-illustration'
        assert reference['sourcePhotographIncludedInApp'] is False
        assert reference['rightsNote']
        assert e['licence'] == record['outputLicence'] == 'Recurso generado para OGT por encargo del titular'
        assert e['licenceUrl'] is None
    else:
        assert reference['licence'] in REFERENCE_LICENCES
        assert reference['licenceUrl'] == REFERENCE_LICENCES[reference['licence']]
        # Keep the reference's licence on the adaptation, including ShareAlike.
        assert e['licence'] == reference['licence'] and e['licenceUrl'] == reference['licenceUrl']
        assert record['outputLicence'] == reference['licence']


def validate_illustration(e, record):
    assert record['sha256'] == e['originalSha256']
    assert hashlib.sha256((ROOT / record['master']).read_bytes()).hexdigest() == record['originalSha256']
    references = record.get('referenceImages', [])
    assert record['inputImages'] in (0, 1) and len(references) == record['inputImages']
    for reference in references:
        path = ROOT / reference['asset']
        assert path.parent == ROOT / 'store/editorial-media/references' and not path.is_symlink()
        data = path.read_bytes()
        assert 0 < len(data) <= 10 * 1024 * 1024 and data[:3] == b'\xff\xd8\xff'
        assert hashlib.sha256(data).hexdigest() == reference['sha256']
        validate_reference_rights(e, record, reference)
    assert e['creditUrl'] == 'https://onlygoodthings.lat/terms#ilustracion-' + e['postId']

REMOTE = r'''
import base64,hashlib,json,os,subprocess,sys,tempfile
from pathlib import Path
j=json.load(sys.stdin)
root=Path.home()/'ogt/media/u'
assert root.is_dir() and not root.is_symlink()
entries=j['entries']
assert entries and len(entries)<=19
def quote(s):return "'"+s.replace("'","''")+"'"
checks=[];updates=[]
for e in entries:
 data=base64.b64decode(e['bytes'],validate=True)
 assert hashlib.sha256(data).hexdigest()==e['sha256']
 path=root/e['storedName']
 assert not path.is_symlink()
 if path.exists():assert hashlib.sha256(path.read_bytes()).hexdigest()==e['sha256']
 elif j['apply']:
  fd,tmp=tempfile.mkstemp(prefix='.editorial-',dir=root)
  try:
   with os.fdopen(fd,'wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
   os.chmod(tmp,0o644);os.link(tmp,path)
  finally:os.unlink(tmp)
 old=quote(e['expectedUrl']);new=quote(e['url']);pid=quote(e['postId'])+'::uuid';mid=quote(e['mediaId'])+'::uuid'
 checks.append("IF (SELECT count(*) FROM social_posts p JOIN post_media m ON m.post_id=p.id WHERE p.id="+pid+" AND m.id="+mid+" AND NOT p.is_story AND p.moderation_status='VISIBLE' AND m.kind='IMAGE' AND p.source_url="+quote(e['sourceUrl'])+" AND p.attribution_url IN ("+quote(e['previousCreditUrl'])+','+quote(e['creditUrl'])+") AND m.url IN ("+old+','+new+"))<>1 THEN RAISE EXCEPTION 'EDITORIAL_BASELINE_CHANGED'; END IF;")
 if e.get('mode')=='original-transport-only':
  transport=e['originalTransport'];original_old=quote(transport['expectedUrl']);original_new=quote(transport['url'])
  checks.append('IF (SELECT original_url FROM post_media WHERE id='+mid+') NOT IN ('+original_old+','+original_new+") OR (SELECT original_url FROM post_media WHERE id="+mid+") IS NULL THEN RAISE EXCEPTION 'EDITORIAL_ORIGINAL_BASELINE_CHANGED'; END IF;")
  updates.append('UPDATE post_media SET original_url='+original_new+' WHERE id='+mid+' AND post_id='+pid+' AND original_url='+original_old+';')
 elif e.get('mode')=='transport-only':
  updates.append('UPDATE post_media SET url='+new+',original_url=CASE WHEN original_url='+old+' THEN '+new+' ELSE original_url END WHERE id='+mid+' AND post_id='+pid+' AND url='+old+';')
 else:
  updates.append('UPDATE post_media SET url='+new+', original_url='+new+', alt_text='+quote(e['alt'])+", frame_x=NULL,frame_y=NULL,frame_zoom=NULL WHERE id="+mid+' AND post_id='+pid+' AND url='+old+';')
 if e.get('mode')!='original-transport-only':
  updates.append('UPDATE social_posts SET media_urls=array_replace(media_urls,'+old+','+new+'),updated_at=now() WHERE id='+pid+' AND '+old+'=ANY(media_urls);')
 updates.append('UPDATE social_posts SET attribution_url='+quote(e['creditUrl'])+' WHERE id='+pid+' AND attribution_url IS DISTINCT FROM '+quote(e['creditUrl'])+';')
# Exclude precisely the two expected changes; validate all other post/media
# values under the same repeatable-read snapshot, including discussion counts.
ids=','.join(quote(e['postId'])+'::uuid' for e in entries)
mids=','.join(quote(e['mediaId'])+'::uuid' for e in entries)
transport_ids=','.join(quote(e['mediaId'])+'::uuid' for e in entries if e.get('mode')=='transport-only') or 'NULL::uuid'
original_ids=','.join(quote(e['mediaId'])+'::uuid' for e in entries if e.get('mode')=='original-transport-only') or 'NULL::uuid'
protected="SELECT jsonb_build_object('posts',(SELECT jsonb_agg(to_jsonb(p)-'updated_at'-'media_urls'-'attribution_url' ORDER BY p.id) FROM social_posts p WHERE p.id IN ("+ids+")),'media',(SELECT jsonb_agg(CASE WHEN m.id IN ("+original_ids+") THEN to_jsonb(m)-'original_url' WHEN m.id IN ("+transport_ids+") THEN to_jsonb(m)-'url'-'original_url' ELSE to_jsonb(m)-'url'-'original_url'-'alt_text'-'frame_x'-'frame_y'-'frame_zoom' END ORDER BY m.id) FROM post_media m WHERE m.id IN ("+mids+")),'comments',(SELECT count(*) FROM post_comments),'allPosts',(SELECT count(*) FROM social_posts))"
sql="SET TRANSACTION ISOLATION LEVEL REPEATABLE READ; DO $ogt_replace$ DECLARE before_data jsonb;after_data jsonb; BEGIN\n"+'\n'.join(checks)+"\n"+protected.replace('SELECT jsonb_build_object','SELECT jsonb_build_object',1)+" INTO before_data;\n"
if j['apply']:
 sql+='\n'.join(updates)+'\n'
 # The established erasure worker checks every current reference before removing
 # a file. Shared images remain available; unreferenced old portraits stop serving.
 for name in j['cleanupNames']:
  sql+='INSERT INTO crm_erased_media(stored_name) VALUES('+quote(name)+") ON CONFLICT(stored_name) DO UPDATE SET processed_at=NULL,outcome=NULL;\n"
sql+=protected+" INTO after_data; IF before_data IS DISTINCT FROM after_data THEN RAISE EXCEPTION 'PROTECTED_EDITORIAL_DATA_CHANGED'; END IF; END $ogt_replace$;"
cmd=['docker','exec','-i','ogt-postgres','psql','-X','-qAt','-U','ogt','-d','onlygoodthings','-v','ON_ERROR_STOP=1','--single-transaction']
r=subprocess.run(cmd,input=sql,text=True,capture_output=True,timeout=40)
if r.returncode:raise RuntimeError('EDITORIAL_TRANSACTION_FAILED: '+r.stderr[-1500:])
print(json.dumps({'mode':'apply' if j['apply'] else 'plan','reviewedImages':len(entries),'protectedDataVerified':True,'newUrls':[e['url'] for e in entries],'originalFilesOverwritten':False,'postsDeleted':False,'legacyFilesQueuedForConditionalCleanup':len(j['cleanupNames']) if j['apply'] else 0}))
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--post-id', action='append', help='Limit the reviewed operation to these exact publication UUIDs')
    args = parser.parse_args()
    entries = []
    cleanup=set()
    evidence=json.loads((ROOT/'store/editorial-media/provenance.json').read_text())
    manifest_entries=json.loads(MANIFEST.read_text())['entries']
    selected=set(args.post_id or [])
    if selected-set(e['postId'] for e in manifest_entries):
        parser.error('Selected publication is not in the reviewed editorial inventory')
    for e in manifest_entries:
        if selected and e['postId'] not in selected:
            continue
        if not e.get('asset') or not e.get('previousImage'):
            continue
        for previous in [e['previousImage'],*e.get('replacementHistory',[])]:
            for name in (previous.get('originalFile'),str(previous.get('expectedMediaUrl','')).rsplit('/',1)[-1]):
                if not name:continue
                candidate=Path(name)
                assert candidate.name==name and candidate.suffix in ('.jpg','.png','.webp') and str(uuid.UUID(candidate.stem))==candidate.stem
                cleanup.add(name)
        if e.get('kind')=='generated-illustration':
            record=next(r for r in evidence['entries'] if r['postId']==e['postId'])
            validate_illustration(e,record)
        else:
            assert e['licence'].startswith('CC BY') and all(x not in e['licence'] for x in ('NC', 'ND'))
        for key in ('postId', 'mediaId'):
            assert str(uuid.UUID(e[key])) == e[key]
        path = ROOT / e['asset']
        assert path.parent == ROOT / 'store/editorial-media' and not path.is_symlink()
        assert str(uuid.UUID(path.stem)) == path.stem and path.suffix == '.jpg'
        data = path.read_bytes()
        assert 0 < len(data) <= 10 * 1024 * 1024 and data[:3] == b'\xff\xd8\xff'
        assert hashlib.sha256(data).hexdigest() == e['originalSha256']
        if e.get('replacementMode')=='original-transport-only':
            transport=e['originalTransport']
            assert transport['expectedUrl']=='http://217.216.82.209:19080/media/u/'+path.name
            assert transport['url']=='https://onlygoodthings.lat/media/u/'+path.name
            assert e['expectedMediaUrl'].startswith('https://onlygoodthings.lat/media/u/')
        else:
            assert e['expectedMediaUrl'] == 'https://onlygoodthings.lat/media/u/' + path.name
        entries.append({'postId': e['postId'], 'mediaId': e['mediaId'],
                        'expectedUrl': e['previousImage']['expectedMediaUrl'],
                        'url': e['expectedMediaUrl'], 'sourceUrl': e['expectedNewsSource'],
                        'creditUrl': e['creditUrl'], 'previousCreditUrl':e['previousImage'].get('creditUrl') or e['creditUrl'], 'storedName': path.name,
                        'sha256': e['originalSha256'], 'alt': e['replacementAlt'],'mode':e.get('replacementMode','replace'),
                        'originalTransport':e.get('originalTransport'),
                        'bytes': base64.b64encode(data).decode()})
    assert entries
    cleanup.difference_update(e['storedName'] for e in entries)
    cleanup.difference_update(e['url'].rsplit('/',1)[-1] for e in entries)
    result = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10',
                             'iankmp-vps', 'python3 -c ' + shlex.quote(REMOTE)],
                            input=json.dumps({'apply': args.apply, 'entries': entries,'cleanupNames':sorted(cleanup)}),
                            text=True, capture_output=True, timeout=70)
    if result.returncode:
        raise RuntimeError('Replacement did not complete: ' + result.stderr[-1800:])
    receipt = json.loads(result.stdout)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
