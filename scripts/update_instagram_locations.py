#!/usr/bin/env python3
"""Apply reviewed place evidence to existing imported posts; plans always roll back."""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
from import_instagram_posts import identity, quote, reviewed_location, REMOTE


def make_sql(data, apply=False):
    handle=data['handle']
    if not re.fullmatch(r'[A-Za-z0-9_.]{1,30}',handle) or not 1 <= len(data['posts']) <= 100:
        raise ValueError('Select a bounded reviewed Instagram batch')
    uid=identity('ogt-import:instagram:'+handle)
    rows=[]
    for post in data['posts']:
        source=f'https://www.instagram.com/{handle}/p/{post["shortcode"]}/'
        if post['sourceUrl']!=source or post['kind'] not in ('ADOPTION','POST'):
            raise ValueError('Unexpected imported post')
        label,point=reviewed_location(post)
        listing=identity(source+'#adoption') if post['kind']=='ADOPTION' else None
        rows.append(f"({quote(identity(source))}::uuid,{quote(listing)}::uuid,{quote(source)},{quote(post['body'])},{quote(label)}, {point})")
    ids=','.join(quote(identity(p['sourceUrl']))+'::uuid' for p in data['posts'])
    social_expression="CASE WHEN t.id IN ("+ids+") THEN to_jsonb(t)-'location'-'gathering_place'-'updated_at' ELSE to_jsonb(t) END"
    listing_expression="CASE WHEN t.id IN (SELECT listing_id FROM desired) THEN to_jsonb(t)-'location'-'place_label'-'updated_at' ELSE to_jsonb(t) END"
    index_clause=" WHERE post_id NOT IN ("+ids+")"
    return f"""BEGIN ISOLATION LEVEL REPEATABLE READ;
SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='60s';
SELECT pg_advisory_xact_lock(hashtextextended('ogt-instagram-import',0));
CREATE TEMP TABLE desired(post_id uuid PRIMARY KEY,listing_id uuid,source_url text,body text,label text,point geometry(Point,4326)) ON COMMIT DROP;
INSERT INTO desired VALUES {','.join(rows)};
DO $check$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM users WHERE id={quote(uid)}::uuid AND firebase_uid={quote('ogt-import:instagram:'+handle)} AND publishing_blocked) THEN RAISE EXCEPTION 'Imported owner changed'; END IF;
 IF (SELECT count(*) FROM desired d JOIN social_posts p ON p.id=d.post_id WHERE p.author_user_id={quote(uid)}::uuid AND p.attribution_url=d.source_url AND p.body=d.body AND p.listing_id IS NOT DISTINCT FROM d.listing_id)<>(SELECT count(*) FROM desired) THEN RAISE EXCEPTION 'Imported content changed; review before updating'; END IF;
END $check$;
CREATE FUNCTION pg_temp.location_snapshot() RETURNS TABLE(table_name text,row_hash text) LANGUAGE plpgsql AS $snapshot$
DECLARE r record; expression text; clause text; h text;
BEGIN
 FOR r IN SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename LOOP
  expression='to_jsonb(t)';clause='';
  IF r.tablename='social_posts' THEN expression={quote(social_expression)}; END IF;
  IF r.tablename='animal_listings' THEN expression={quote(listing_expression)}; END IF;
  IF r.tablename='crm_publication_index' THEN clause={quote(index_clause)}; END IF;
  EXECUTE format('SELECT md5(COALESCE(jsonb_agg(v ORDER BY v::text)::text,''[]'')) FROM (SELECT %s AS v FROM public.%I t%s) s',expression,r.tablename,clause) INTO h;
  table_name=r.tablename;row_hash=h;RETURN NEXT;
 END LOOP;
END $snapshot$;
CREATE TEMP TABLE before_location AS SELECT * FROM pg_temp.location_snapshot();
UPDATE social_posts p SET location=d.point,gathering_place=d.label FROM desired d WHERE p.id=d.post_id AND (p.location IS DISTINCT FROM d.point OR p.gathering_place IS DISTINCT FROM d.label);
UPDATE animal_listings a SET location=d.point,place_label=COALESCE(d.label,'Ubicación pendiente de confirmar') FROM desired d WHERE a.id=d.listing_id AND (a.location IS DISTINCT FROM d.point OR a.place_label IS DISTINCT FROM COALESCE(d.label,'Ubicación pendiente de confirmar'));
DO $protect$ DECLARE changed text; BEGIN
 SELECT string_agg(table_name,', ') INTO changed FROM before_location b FULL JOIN pg_temp.location_snapshot() a USING(table_name) WHERE b.row_hash IS DISTINCT FROM a.row_hash;
 IF changed IS NOT NULL THEN RAISE EXCEPTION 'Protected data changed: %',changed; END IF;
END $protect$;
SELECT jsonb_build_object('mode',{quote('apply' if apply else 'plan')},'reviewedPosts',(SELECT count(*) FROM desired),'locatedPosts',(SELECT count(*) FROM desired WHERE point IS NOT NULL),'locatedAdoptions',(SELECT count(*) FROM desired WHERE listing_id IS NOT NULL AND point IS NOT NULL),'protectedDataVerified',true);
{'COMMIT;' if apply else 'ROLLBACK;'}"""


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest',type=Path);parser.add_argument('--apply',action='store_true');parser.add_argument('--receipt',required=True,type=Path)
    args=parser.parse_args();sql=make_sql(json.loads(args.manifest.read_text()),args.apply)
    args.receipt.parent.mkdir(parents=True,exist_ok=True);args.receipt.with_suffix('.sql').write_text(sql)
    command=['ssh','-o','BatchMode=yes','-o','StrictHostKeyChecking=yes','-o','ConnectTimeout=15','iankmp-vps','python3 -c '+shlex.quote(REMOTE)]
    result=subprocess.run(command,input=json.dumps({'apply':args.apply,'sql':sql,'files':[]}),capture_output=True,text=True,timeout=180)
    if result.returncode:raise RuntimeError(result.stderr[-3000:])
    receipt=json.loads(next(line for line in reversed(result.stdout.splitlines()) if line.startswith('{')))
    args.receipt.write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))


if __name__=='__main__':main()
