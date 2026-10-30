#!/usr/bin/env python3
"""Complete missing names on the reviewed editorial posts; read-only by default."""
import argparse
import json
from pathlib import Path
import shlex
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]


def reviewed_entries(manifest):
    entries = manifest['entries']
    if len(entries) != 19:
        raise ValueError('EDITORIAL_INVENTORY_CHANGED')
    seen = set()
    result = []
    for entry in entries:
        post_id = entry['postId']
        if str(uuid.UUID(post_id)) != post_id or post_id in seen:
            raise ValueError('INVALID_EDITORIAL_ID')
        seen.add(post_id)
        name = entry['subject']
        if not name or name != name.strip() or len(name) > 120 or any(ord(c) < 32 for c in name):
            raise ValueError('INVALID_HONOREE_NAME')
        if not entry['expectedNewsSource'].startswith('https://'):
            raise ValueError('INVALID_EDITORIAL_SOURCE')
        result.append({'postId': post_id, 'name': name, 'sourceUrl': entry['expectedNewsSource'],
                       'imageUrl': entry['expectedMediaUrl']})
    return result


def quote(value):
    return "'" + value.replace("'", "''") + "'"


def transaction(entries, apply=False):
    ids = ','.join(quote(e['postId']) + '::uuid' for e in entries)
    values = ','.join('(' + ','.join(quote(e[k]) for k in ('postId', 'name', 'sourceUrl', 'imageUrl')) + ')' for e in entries)
    protected = """SELECT jsonb_build_object(
      'posts',(SELECT jsonb_agg(CASE WHEN p.id IN (%s) THEN to_jsonb(p)-'honoree_name'-'updated_at' ELSE to_jsonb(p) END ORDER BY p.id) FROM social_posts p),
      'media',(SELECT jsonb_agg(to_jsonb(m) ORDER BY m.id) FROM post_media m),
      'comments',(SELECT jsonb_agg(to_jsonb(c) ORDER BY c.id) FROM post_comments c))""" % ids
    sql = """SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET LOCAL statement_timeout='25s';
CREATE TEMP TABLE reviewed_honorees(post_id text PRIMARY KEY,name text,source_url text,image_url text) ON COMMIT DROP;
INSERT INTO reviewed_honorees VALUES %s;
CREATE TEMP TABLE honoree_result(changed integer) ON COMMIT DROP;
DO $ogt_honorees$ DECLARE before_data jsonb;after_data jsonb;changed integer:=0; BEGIN
PERFORM 1 FROM social_posts WHERE id IN (%s) ORDER BY id FOR UPDATE;
IF (SELECT count(*) FROM social_posts p JOIN reviewed_honorees e ON p.id=e.post_id::uuid
    WHERE NOT p.is_story AND p.moderation_status='VISIBLE' AND p.source_url=e.source_url
      AND e.image_url=ANY(p.media_urls))<>%d THEN RAISE EXCEPTION 'EDITORIAL_BASELINE_CHANGED'; END IF;
%s INTO before_data;
""" % (values, ids, len(entries), protected)
    if apply:
        sql += """UPDATE social_posts p SET honoree_name=e.name,updated_at=now()
FROM reviewed_honorees e WHERE p.id=e.post_id::uuid AND (p.honoree_name IS NULL OR btrim(p.honoree_name)='');
GET DIAGNOSTICS changed=ROW_COUNT;
"""
    sql += protected + """ INTO after_data;
IF before_data IS DISTINCT FROM after_data THEN RAISE EXCEPTION 'PROTECTED_DATA_CHANGED'; END IF;
INSERT INTO honoree_result VALUES(changed);
END $ogt_honorees$;
SELECT json_build_object('reviewedPosts',%d,'changedNames',(SELECT changed FROM honoree_result),
 'missingNames',(SELECT count(*) FROM social_posts WHERE id IN (%s) AND (honoree_name IS NULL OR btrim(honoree_name)='')),
 'protectedDataVerified',true,'mode',%s);
""" % (len(entries), ids, quote('apply' if apply else 'plan'))
    return sql


REMOTE = r'''
import json,subprocess,sys
j=json.load(sys.stdin)
r=subprocess.run(['docker','exec','-i','ogt-postgres','psql','-X','-qAt','-U','ogt','-d','onlygoodthings','-v','ON_ERROR_STOP=1','--single-transaction'],input=j['sql'],text=True,capture_output=True,timeout=40)
if r.returncode:raise RuntimeError('HONOREE_TRANSACTION_FAILED: '+r.stderr[-1200:])
print(r.stdout.strip())
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    entries = reviewed_entries(json.loads((ROOT / 'store/editorial-media-rights.json').read_text()))
    result = subprocess.run(['ssh', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=10', 'iankmp-vps',
                             'python3 -c ' + shlex.quote(REMOTE)],
                            input=json.dumps({'sql': transaction(entries, args.apply)}),
                            text=True, capture_output=True, timeout=55)
    if result.returncode:
        raise RuntimeError(result.stderr[-1500:])
    receipt = json.loads(result.stdout)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n')
    args.receipt.chmod(0o600)
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
