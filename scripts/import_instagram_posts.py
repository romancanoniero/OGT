#!/usr/bin/env python3
"""Import a reviewed Instagram manifest. Plans roll back; --apply adds only new rows/files."""
import argparse
import base64
import hashlib
import json
import math
from pathlib import Path
import re
import shlex
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = 'https://onlygoodthings.lat/media/u/'


def quote(value):
    if value is None:
        return 'NULL'
    return "'" + str(value).replace("'", "''") + "'"


def identity(value):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, value))


def reviewed_location(entry):
    location = entry.get('location')
    if location is None:
        return None, 'NULL'
    label = location.get('label', '').strip()
    lat, lng = location.get('latitude'), location.get('longitude')
    if not label or len(label) > 200 or (lat is None) != (lng is None):
        raise ValueError('A reviewed place needs a label and a coordinate pair')
    if lat is None:
        return label, 'NULL'
    if any(type(v) not in (float,int) or not math.isfinite(v) for v in (lat,lng)) or not -90 <= lat <= 90 or not -180 <= lng <= 180:
        raise ValueError('Invalid reviewed place coordinates')
    if not location.get('evidence') or not location.get('sourceUrl','').startswith('https://www.instagram.com/'):
        raise ValueError('Coordinates require reviewed location evidence')
    return label, f'ST_SetSRID(ST_MakePoint({lng},{lat}),4326)'


SPECIES = {'DOG': ('perros','Perros'), 'CAT': ('gatos','Gatos'), 'BIRD': ('aves','Aves'),
           'RABBIT': ('conejos','Conejos'), 'HAMSTER': ('hamsters','Hámsters'), 'FISH': ('peces','Peces'),
           'TURTLE': ('tortugas','Tortugas'), 'OTHER': ('animales','Animales')}


def reviewed_animal(entry):
    animal = entry.get('animal', {})
    alert = entry.get('kind') in ('LOST','FOUND')
    species, size = animal.get('species', None if alert else 'CAT'), animal.get('size', None if alert else 'SMALL')
    if species not in SPECIES or size not in (None,'SMALL','MEDIUM','LARGE'):
        raise ValueError('Invalid reviewed animal species or size')
    care = animal.get('careReview')
    if care is not None:
        if any(care.get(k) not in ('ALL','SOME','NONE','UNKNOWN') for k in ('vaccination','sterilization')):
            raise ValueError('Health status must be explicitly reviewed, including unknowns')
        if care.get('adoptionMode','UNSPECIFIED') not in ('UNSPECIFIED','SEPARATE','TOGETHER'):
            raise ValueError('Invalid reviewed adoption mode')
        count=care.get('availableCount')
        if count is not None and (type(count) is not int or not 1 <= count <= 100):
            raise ValueError('Invalid reviewed available animal count')
    return species,size,care


def reviewed_alert(entry):
    animal = entry.get('animal', {})
    place, point = reviewed_location(entry)
    if not place or point == 'NULL' or not animal.get('marks','').strip() or not animal.get('lastSeenPlace','').strip():
        raise ValueError('A pet alert needs reviewed signs, place and coordinates')
    if entry['kind'] == 'LOST' and not animal.get('petName','').strip():
        raise ValueError('A lost pet needs its reviewed name')
    if len(animal['marks']) > 160 or len(animal['lastSeenPlace']) > 120 or len(entry['body']) > 400:
        raise ValueError('A pet alert must fit the existing manual form')


def adoption_details_sql(entry):
    species,size,care = reviewed_animal(entry)
    if care is None:
        return 'NULL', []
    animal=entry['animal']
    ids=['species-'+species.lower()]
    if size:ids.append('size-'+size.lower())
    sex={'Hembra':'female','Macho':'male'}.get(animal.get('sex'))
    if sex:ids.append('sex-'+sex)
    ids += ['vaccination-'+care['vaccination'].lower(), 'sterilization-'+care['sterilization'].lower()]
    array='ARRAY['+','.join(quote(i) for i in ids)+']::text[]'
    tags=f"(SELECT COALESCE(jsonb_agg(jsonb_build_object('id',id,'label',label,'key',tag_key,'group',group_name,'legacyValue',legacy_value,'active',active,'sortOrder',sort_order,'version',version) ORDER BY group_name,sort_order,id),'[]'::jsonb) FROM adoption_tags WHERE id=ANY({array}) AND active)"
    value=f"jsonb_build_object('version',1,'presentation',{quote(entry['body'])},'historyAndCare',{quote(animal.get('homeNeeds',''))},'tags',{tags},'adoptionMode',{quote(care.get('adoptionMode','UNSPECIFIED'))},'availableCount',{care.get('availableCount') or 'NULL'})"
    return value,ids


def prepare(path):
    data = json.loads(Path(path).read_text())
    handle = data['handle']
    if not re.fullmatch(r'[A-Za-z0-9_.]{1,30}', handle):
        raise ValueError('Invalid Instagram handle')
    source = f'https://www.instagram.com/{handle}/'
    if data['sourceUrl'] != source or '(importado)' not in data['displayName']:
        raise ValueError('Profile must disclose its original source and imported status')
    if not 1 <= len(data['posts']) <= 100:
        raise ValueError('Select a bounded, reviewed post batch')
    if data.get('topicSpecies','CAT') not in SPECIES:
        raise ValueError('Invalid reviewed profile topic species')
    existing = data.get('existingAuthor')
    if existing is not None:
        for key in ('userId','ownerUserId'):
            if str(uuid.UUID(existing[key])) != existing[key]:
                raise ValueError('Invalid existing test author identity')
        if not re.fullmatch(r'prueba_[a-z0-9_]+', existing.get('nickname','')):
            raise ValueError('Only a disclosed existing test contact can author this batch')
    user_id = existing['userId'] if existing else identity('ogt-import:instagram:' + handle)
    files = {}
    def asset(item):
        filename = item['file']
        p = (Path(path).resolve().parent / filename).resolve()
        media_root = (Path(path).resolve().parent / 'media').resolve()
        if not p.is_relative_to(media_root) or p.is_symlink() or not p.is_file():
            raise ValueError('Media must be a reviewed local file in media/')
        content = p.read_bytes()
        if not 0 < len(content) <= 100 * 1024 * 1024:
            raise ValueError('Invalid media size')
        sha = hashlib.sha256(content).hexdigest()
        if sha != item['sha256']:
            raise ValueError('Reviewed media hash changed')
        extension = p.suffix.lower()
        if extension not in ('.jpg', '.mp4'):
            raise ValueError('Unsupported prepared media')
        name = identity('ogt-instagram-media:' + sha) + extension
        files[name] = {'name': name, 'sha256': sha, 'bytes': base64.b64encode(content).decode()}
        return PUBLIC + name
    photo = asset(data['avatar']) if data.get('avatar') else None
    posts = []
    seen = set()
    for entry in data['posts']:
        code = entry['shortcode']
        if not re.fullmatch(r'[A-Za-z0-9_-]{5,40}', code) or code in seen:
            raise ValueError('Invalid or repeated Instagram post')
        seen.add(code)
        if entry['sourceUrl'] != source + 'p/' + code + '/':
            raise ValueError('Post source must belong to the reviewed account')
        if entry['kind'] not in ('ADOPTION', 'POST', 'LOST', 'FOUND') or not 1 <= len(entry['media']) <= 20:
            raise ValueError('Classify the post and preserve all 1..20 media items')
        if entry.get('sourceMediaCount',len(entry['media'])) != len(entry['media']):
            raise ValueError('The reviewed source carousel must be complete')
        if not entry['body'].strip() or 'importad' not in entry['body'].lower():
            raise ValueError('Post must disclose the import')
        reviewed_location(entry)
        if entry['kind'] in ('ADOPTION','LOST','FOUND'):
            reviewed_animal(entry)
        if entry['kind'] in ('LOST','FOUND'):
            reviewed_alert(entry)
        post = dict(entry, id=identity(entry['sourceUrl']))
        post['listingId'] = identity(entry['sourceUrl'] + '#' + entry['kind'].lower()) if entry['kind'] != 'POST' else None
        prepared = []
        for order, item in enumerate(entry['media']):
            if item['kind'] not in ('IMAGE', 'VIDEO'):
                raise ValueError('Invalid media kind')
            duration = item.get('durationMs')
            if duration is not None and (type(duration) is not int or not 0 < duration <= 2147483647):
                raise ValueError('Invalid media duration')
            url = asset(item)
            poster = asset(item['poster']) if item.get('poster') else None
            if item['kind'] == 'VIDEO' and (not poster or not item.get('durationMs')):
                raise ValueError('A video requires its reviewed poster and duration')
            prepared.append(dict(item, id=identity(post['id'] + f'#media:{order}'), url=url, posterUrl=poster, order=order))
        post['media'] = prepared
        posts.append(post)
    return dict(data, userId=user_id, photoUrl=photo, posts=posts, files=list(files.values()))


def make_sql(data, apply=False):
    uid = quote(data['userId'])
    existing = data.get('existingAuthor')
    # Existing authors remain inside the protected snapshot, including metadata and identity.
    all_ids = [] if existing else [data['userId']]
    for p in data['posts']:
        all_ids += [p['id'], *([p['listingId']] if p['listingId'] else []), *[m['id'] for m in p['media']]]
    ids = ','.join(quote(x) for x in all_ids)
    metadata = {'importedProfile': {'platform': 'instagram', 'handle': data['handle'], 'sourceUrl': data['sourceUrl'], 'bio': data['bio'], 'managed': False},
                'settings': {'barrio': data.get('profilePlace',''), 'language': 'es', 'publicProfileVisible': True}}
    sql = ["BEGIN ISOLATION LEVEL REPEATABLE READ; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='45s';",
           "SELECT pg_advisory_xact_lock(hashtextextended('ogt-instagram-import',0));",
           (ROOT/'database/migrations/42_imported_profiles.sql').read_text(),
           (ROOT/'database/migrations/44_adoption_location.sql').read_text(),
           "CREATE TEMP TABLE import_protected(table_name text PRIMARY KEY, before_hash text) ON COMMIT DROP;",
           f"""CREATE FUNCTION pg_temp.import_snapshot() RETURNS TABLE(table_name text, row_hash text) LANGUAGE plpgsql AS $snapshot$
DECLARE r record; clause text; h text;
BEGIN
 FOR r IN SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename LOOP
  clause=CASE WHEN r.tablename IN ('users','social_posts','animal_listings','post_media') THEN {quote(' WHERE id NOT IN ('+ids+')')}
              WHEN r.tablename IN ('post_people','crm_publication_index') THEN {quote(' WHERE post_id NOT IN ('+ids+')')} ELSE '' END;
  EXECUTE format('SELECT md5(COALESCE(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text,''[]'')) FROM public.%I t%s',r.tablename,clause) INTO h;
  table_name=r.tablename;row_hash=h;RETURN NEXT;
 END LOOP;
END $snapshot$;
INSERT INTO import_protected SELECT * FROM pg_temp.import_snapshot();""",
           f"INSERT INTO users(id,firebase_uid,display_name,photo_url,nickname,invite_code,publishing_blocked,metadata) VALUES({uid}::uuid,{quote('ogt-import:instagram:'+data['handle'])},{quote(data['displayName'])},{quote(data['photoUrl'])},{quote(data['handle'])},{quote('IG-'+data['userId'])},true,{quote(json.dumps(metadata))}::jsonb) ON CONFLICT(id) DO NOTHING;",
           f"DO $check$ BEGIN IF NOT EXISTS(SELECT 1 FROM users WHERE id={uid}::uuid AND firebase_uid={quote('ogt-import:instagram:'+data['handle'])} AND publishing_blocked AND role='USER' AND status='ACTIVE' AND display_name={quote(data['displayName'])} AND metadata->'importedProfile'->>'sourceUrl'={quote(data['sourceUrl'])}) THEN RAISE EXCEPTION 'Imported profile collision or changed owner'; END IF; END $check$;"]
    if existing:
        sql = sql[:-2] + [f"DO $check$ BEGIN IF NOT EXISTS(SELECT 1 FROM users WHERE id={uid}::uuid AND firebase_uid={quote('ogt-test:share:'+data['userId'])} AND publishing_blocked AND role='USER' AND status='ACTIVE' AND nickname={quote(existing['nickname'])} AND metadata->'testContact'->>'scope'='SHARE_TEST' AND metadata->'testContact'->>'ownerUserId'={quote(existing['ownerUserId'])}) THEN RAISE EXCEPTION 'Existing test author collision or changed owner'; END IF; END $check$;"]
    for p in data['posts']:
        place, point = reviewed_location(p)
        pid = quote(p['id']) + '::uuid'
        media_urls = 'ARRAY[' + ','.join(quote(m['url']) for m in p['media']) + ']::text[]'
        if p['listingId']:
            a = p['animal']
            values = [quote(a.get(k,'')) for k in ('title','petName','ageLabel','sex','temperament','homeNeeds')]
            title,name,age,sex,temperament,home = values
            species,size,care = reviewed_animal(p)
            details,tag_ids = adoption_details_sql(p) if p['kind']=='ADOPTION' else ('NULL',[])
            if tag_ids:
                array='ARRAY['+','.join(quote(i) for i in tag_ids)+']::text[]'
                sql.append(f"DO $tags$ BEGIN IF (SELECT count(*) FROM adoption_tags WHERE id=ANY({array}) AND active)<>{len(tag_ids)} THEN RAISE EXCEPTION 'Reviewed adoption tag missing or inactive'; END IF; END $tags$;")
            sterilized = care['sterilization']=='ALL' if care else bool(a.get('sterilized'))
            vaccinated = care['vaccination']=='ALL' if care else False
            alert=p['kind'] in ('LOST','FOUND')
            urgency='HIGH' if p['kind']=='LOST' else 'MEDIUM' if alert else 'LOW'
            sql.append(f"INSERT INTO animal_listings(id,reporter_user_id,kind,species,size,urgency,title,description,photo_urls,location,alert_radius_m,pet_name,age_label,sex,temperament,sterilized,vaccinated,home_needs,place_label,adoption_details,marks,last_seen_place,created_at) VALUES({quote(p['listingId'])}::uuid,{uid}::uuid,{quote(p['kind'])},{quote(species)},{quote(size)},{quote(urgency)},{title},{quote(p['body'])},{media_urls},{point},{2000 if alert else 0},{name},{age},{sex},{temperament},{'TRUE' if sterilized else 'FALSE'},{'TRUE' if vaccinated else 'FALSE'},{home},{quote(place or "Ubicación pendiente de confirmar")},{details},{quote(a.get('marks',''))},{quote(a.get('lastSeenPlace',''))},{quote(p['createdAt'])}::timestamptz) ON CONFLICT(id) DO NOTHING;")
            sql.append(f"DO $listing$ BEGIN IF NOT EXISTS(SELECT 1 FROM animal_listings WHERE id={quote(p['listingId'])}::uuid AND reporter_user_id={uid}::uuid AND kind={quote(p['kind'])} AND species={quote(species)} AND size IS NOT DISTINCT FROM {quote(size)}::animal_size AND title={title} AND description={quote(p['body'])} AND photo_urls={media_urls} AND marks={quote(a.get('marks',''))} AND last_seen_place={quote(a.get('lastSeenPlace',''))} AND location IS NOT DISTINCT FROM {point}) THEN RAISE EXCEPTION 'Imported listing collision or edited content'; END IF; END $listing$;")
        topic_species = reviewed_animal(p)[0] if p['listingId'] else data.get('topicSpecies','CAT')
        topic_key,topic_label = SPECIES[topic_species]
        tags = json.dumps([{'key':topic_key,'label':topic_label}, {'key':'adopcion' if p['kind']=='ADOPTION' else 'transitoanimal' if p['category']=='Tránsito animal' else 'comunidad','label':p['category']}])
        sql += [f"INSERT INTO social_posts(id,author_kind,author_user_id,body,media_urls,location,gathering_place,topic,listing_id,attribution_url,publication_kind,hashtags,created_at) VALUES({pid},'USER',{uid}::uuid,{quote(p['body'])},{media_urls},{point},{quote(place)},{quote(p['category'])},{quote(p['listingId'])}::uuid,{quote(p['sourceUrl'])},{'NULL' if p['listingId'] else quote('POST')},{quote(tags)}::jsonb,{quote(p['createdAt'])}::timestamptz) ON CONFLICT(id) DO NOTHING;",
                f"DO $check$ BEGIN IF NOT EXISTS(SELECT 1 FROM social_posts WHERE id={pid} AND author_user_id={uid}::uuid AND body={quote(p['body'])} AND attribution_url={quote(p['sourceUrl'])} AND media_urls={media_urls} AND listing_id IS NOT DISTINCT FROM {quote(p['listingId'])}::uuid AND source_url IS NULL) THEN RAISE EXCEPTION 'Imported post collision or edited content'; END IF; END $check$;",
                f"INSERT INTO post_people(post_id,user_id,role) VALUES({pid},{uid}::uuid,'AUTHOR') ON CONFLICT DO NOTHING;"]
        for m in p['media']:
            sql += [f"INSERT INTO post_media(id,post_id,kind,url,original_url,poster_url,sort_order,duration_ms,alt_text,source_url) VALUES({quote(m['id'])}::uuid,{pid},{quote(m['kind'])}::media_kind,{quote(m['url'])},{quote(m['url'])},{quote(m['posterUrl'])},{m['order']},{m.get('durationMs') or 'NULL'},{quote(m.get('alt',p['category']))},{quote(p['sourceUrl'])}) ON CONFLICT(id) DO NOTHING;",
                    f"DO $check$ BEGIN IF NOT EXISTS(SELECT 1 FROM post_media WHERE id={quote(m['id'])}::uuid AND post_id={pid} AND kind={quote(m['kind'])}::media_kind AND url={quote(m['url'])} AND sort_order={m['order']} AND poster_url IS NOT DISTINCT FROM {quote(m['posterUrl'])}) THEN RAISE EXCEPTION 'Imported media collision'; END IF; END $check$;"]
    sql += ["DO $protect$ DECLARE changed text; BEGIN SELECT string_agg(table_name,', ' ORDER BY table_name) INTO changed FROM import_protected b FULL JOIN pg_temp.import_snapshot() a USING(table_name) WHERE b.before_hash IS DISTINCT FROM a.row_hash; IF changed IS NOT NULL THEN RAISE EXCEPTION 'Protected existing data changed: %',changed; END IF; END $protect$;",
            f"SELECT jsonb_build_object('authorId',{uid},'posts',(SELECT count(*) FROM social_posts WHERE author_user_id={uid}::uuid),'adoptions',(SELECT count(*) FROM animal_listings WHERE reporter_user_id={uid}::uuid AND kind='ADOPTION'),'petAlerts',(SELECT count(*) FROM animal_listings WHERE reporter_user_id={uid}::uuid AND kind IN ('LOST','FOUND')),'protectedDataVerified',true,'mode',{quote('apply' if apply else 'plan')});",
            'COMMIT;' if apply else 'ROLLBACK;']
    return '\n'.join(sql)


REMOTE = r'''
import base64,hashlib,json,os,subprocess,sys,tempfile,datetime
from pathlib import Path
j=json.load(sys.stdin);root=Path.home()/'ogt/media/u'
assert root.is_dir() and not root.is_symlink()
backup=None
if j['apply']:
 backup=Path.home()/'ogt/backups'/('instagram-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
 backup.mkdir(mode=0o700)
 with (backup/'before.dump').open('wb') as f:subprocess.run(['docker','exec','ogt-postgres','pg_dump','-Fc','-U','ogt','-d','onlygoodthings'],stdout=f,check=True,timeout=60)
 subprocess.run(['docker','exec','-i','ogt-postgres','pg_restore','--list'],input=(backup/'before.dump').read_bytes(),stdout=subprocess.DEVNULL,check=True,timeout=30)
 for item in j['files']:
  data=base64.b64decode(item['bytes'],validate=True);assert hashlib.sha256(data).hexdigest()==item['sha256']
  name=item['name'];assert Path(name).name==name
  path=root/name;assert not path.is_symlink()
  if path.exists():assert hashlib.sha256(path.read_bytes()).hexdigest()==item['sha256']
  else:
   fd,tmp=tempfile.mkstemp(prefix='.instagram-',dir=root)
   try:
    with os.fdopen(fd,'wb') as f:f.write(data);f.flush();os.fsync(f.fileno())
    os.chmod(tmp,0o644);os.link(tmp,path)
   finally:os.unlink(tmp)
r=subprocess.run(['docker','exec','-i','ogt-postgres','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d','onlygoodthings'],input=j['sql'],text=True,capture_output=True,timeout=90)
if r.returncode:raise RuntimeError(r.stderr[-2500:])
result=json.loads(next(line for line in reversed(r.stdout.splitlines()) if line.startswith('{')))
result['backup']=str(backup) if backup else None;result['files']=len(j['files'])
if backup:(backup/'receipt.json').write_text(json.dumps(result,indent=2))
print(json.dumps(result))
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest',type=Path)
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--local-qa',action='store_true')
    parser.add_argument('--qa-database',default='onlygoodthings')
    parser.add_argument('--receipt',type=Path,required=True)
    args=parser.parse_args()
    data=prepare(args.manifest)
    sql=make_sql(data,args.apply)
    args.receipt.parent.mkdir(parents=True,exist_ok=True)
    args.receipt.with_suffix('.sql').write_text(sql)
    if args.local_qa:
        if args.apply:parser.error('Local QA only supports a rolled-back plan')
        if args.qa_database!='onlygoodthings' and not re.fullmatch(r'ogt_instagram_qa_[a-f0-9]{8}',args.qa_database):parser.error('Use an isolated Instagram QA database')
        run=subprocess.run(['docker','exec','-i','ogt-qa-postgres-1','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d',args.qa_database],input=sql,text=True,capture_output=True,timeout=90)
    else:
        command=['ssh','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=15','iankmp-vps','python3 -c '+shlex.quote(REMOTE)]
        run=subprocess.run(command,input=json.dumps({'apply':args.apply,'sql':sql,'files':data['files']}),text=True,capture_output=True,timeout=180)
    if run.returncode:raise RuntimeError(run.stderr[-3000:])
    result=json.loads(next(line for line in reversed(run.stdout.splitlines()) if line.startswith('{')))
    args.receipt.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result))


if __name__=='__main__':main()
