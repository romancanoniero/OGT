#!/usr/bin/env python3
"""Exercise the offline recovery CLI against synthetic, disposable local data."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]


def run(args, data=None, timeout=60, **kwargs):
    return subprocess.run(args, input=data, capture_output=True, check=True,
                          timeout=timeout, **kwargs).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    if sys.platform != 'darwin' or args.receipt.exists():
        parser.error('Use a fresh receipt on the local Mac only')
    nonce = secrets.token_hex(16)
    name = 'ogt-privacy-qa-' + nonce
    database = 'ogt_recovery_' + nonce
    password = secrets.token_hex(20)
    label = 'ogt.recovery-privacy-qa'
    image = 'postgis/postgis:16-3.4'
    java = Path.home() / 'Library/Java/JavaVirtualMachines/jbr-21.0.5/Contents/Home/bin/java'
    libs = ROOT / 'backend/build/install/backend/lib'
    assert java.is_file() and libs.is_dir()
    run(['docker', 'image', 'inspect', image])
    created = False
    report = {'syntheticDataOnly': True, 'firebaseContacted': False,
              'vpsContacted': False, 'sourceDatabaseRestored': False}
    try:
        run(['docker', 'run', '-d', '--name', name, '--label', label + '=' + nonce,
             '--memory', '512m', '--pids-limit', '128',
             '--tmpfs', '/var/lib/postgresql/data:rw,nosuid,nodev,size=268435456',
             '-p', '127.0.0.1::5432', '-e', 'POSTGRES_DB=onlygoodthings',
             '-e', 'POSTGRES_USER=ogt', '-e', 'POSTGRES_PASSWORD=' + password, image])
        created = True
        def sql(query, db='onlygoodthings'):
            return run(['docker', 'exec', '-i', name, 'psql', '-h', '127.0.0.1',
                        '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'ogt', '-d', db], query.encode()).decode().strip()
        for _ in range(60):
            try:
                if sql('SELECT 1') == '1':
                    break
            except subprocess.CalledProcessError:
                pass
            time.sleep(.3)
        else:
            raise RuntimeError('QA_DATABASE_NOT_READY')
        for migration in sorted((ROOT / 'database/migrations').glob('*.sql')):
            # Run the bundled schema and its prerequisite fixtures in this new
            # container. No source dump, remote media or real credentials are read.
            if migration.name != '14_elpidio_homenaje.sql':
                try:
                    sql(migration.read_text())
                except subprocess.CalledProcessError as error:
                    raise RuntimeError(migration.name + ': ' + error.stderr.decode()[-1200:]) from None
        survivor = '88888888-8888-4888-8888-888888888881'
        erased = '88888888-8888-4888-8888-888888888882'
        post = '88888888-8888-4888-8888-888888888891'
        deleted_post = '88888888-8888-4888-8888-888888888892'
        media = '88888888-8888-4888-8888-888888888895.jpg'
        sql(f"""INSERT INTO users(id,firebase_uid,email,display_name,invite_code,photo_url) VALUES
         ('{survivor}','privacy-qa-survivor','survivor@example.test','Survivor','QA-SURVIVOR',NULL),
         ('{erased}','privacy-qa-erased','erased@example.test','Erased','QA-ERASED','/media/u/{media}');
         INSERT INTO social_posts(id,author_kind,author_user_id,body,impact_count,comment_count) VALUES
         ('{post}','USER','{survivor}','Surviving discussion',1,1),
         ('{deleted_post}','USER','{erased}','Erased author post',0,0);
         INSERT INTO post_comments(post_id,author_user_id,body) VALUES('{post}','{erased}','Erased reply');
         INSERT INTO post_impacts(post_id,user_id) VALUES('{post}','{erased}');""")
        fingerprint = """SELECT md5(jsonb_build_object(
          'users',(SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM users t),
          'posts',(SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM social_posts t),
          'comments',(SELECT jsonb_agg(to_jsonb(t) ORDER BY id) FROM post_comments t),
          'tombstones',(SELECT jsonb_agg(to_jsonb(t) ORDER BY uid_hash) FROM crm_erased_accounts t))::text)"""
        baseline = sql(fingerprint)
        sql('CREATE DATABASE "' + database + '" TEMPLATE onlygoodthings;')
        marker = 'ogt-recovery-privacy:' + nonce
        sql('COMMENT ON DATABASE "' + database + '" IS \'' + marker + '\';')
        port = run(['docker', 'port', name, '5432/tcp']).decode().strip().rsplit(':', 1)[1]
        env = {**os.environ, 'OGT_JDBC_URL': 'jdbc:postgresql://127.0.0.1:' + port + '/onlygoodthings',
               'OGT_DB_USER': 'ogt', 'OGT_DB_PASSWORD': password}
        now = int(time.time())
        uid_hash = hashlib.sha256(b'privacy-qa-erased').hexdigest()
        journal = {'kind': 'ogt-erasure-journal-v1', 'capturedAtEpochSeconds': now, 'hashes': [uid_hash]}
        payload = json.dumps(journal, sort_keys=True).encode()
        sha = hashlib.sha256(payload).hexdigest()
        def cli(target=database, apply=False, through=now, expected=sha):
            command = [str(java), '-cp', str(libs / '*'),
                       'com.onlygoodthings.backend.auth.RecoveryPrivacy', '--database', target,
                       '--journal-sha256', expected, '--snapshot-epoch', str(now - 60),
                       '--required-through-epoch', str(through)]
            if apply:
                command.append('--apply')
            return subprocess.run(command, input=payload, capture_output=True, env=env, timeout=45)
        def receipt(result):
            assert result.returncode == 0, 'CLI_REJECTED_VALID_FIXTURE'
            lines = [line for line in result.stdout.decode().splitlines() if line.startswith('{')]
            assert len(lines) == 1
            return json.loads(lines[0])
        for result in (cli(target='onlygoodthings', apply=True), cli(apply=True, through=now + 1),
                       cli(apply=True, expected='0' * 64)):
            assert result.returncode == 2
        assert sql(fingerprint, database) == baseline
        planned = receipt(cli())
        assert planned['matchingAccounts'] == 1 and not planned['privacyReconciled']
        assert sql(fingerprint, database) == baseline
        sql(f"INSERT INTO crm_platform_staff(user_id,staff_role,enabled,approved_by,approved_at) VALUES('{erased}','OWNER',true,'{survivor}',now())", database)
        assert cli(apply=True).returncode == 2
        assert sql(f"SELECT count(*) FROM users WHERE id IN ('{survivor}','{erased}')", database) == '2'
        sql(f"DELETE FROM crm_platform_staff WHERE user_id='{erased}'", database)
        applied = receipt(cli(apply=True))
        assert applied['privacyReconciled'] and applied['matchingAccounts'] == 1
        assert applied['approvedForPublicServing'] is False
        assert sql(f"SELECT count(*) FROM users WHERE id='{survivor}'", database) == '1'
        assert sql(f"SELECT count(*) FROM users WHERE id='{erased}'", database) == '0'
        assert sql(f"SELECT impact_count::text||','||comment_count::text FROM social_posts WHERE id='{post}'", database) == '0,0'
        assert sql(f"SELECT count(*) FROM social_posts WHERE id='{deleted_post}'", database) == '0'
        assert sql(f"SELECT count(*) FROM crm_erased_accounts WHERE uid_hash='{uid_hash}'", database) == '1'
        assert sql(f"SELECT count(*) FROM crm_erased_media WHERE stored_name='{media}'", database) == '1'
        assert receipt(cli(apply=True))['matchingAccounts'] == 0
        assert sql(fingerprint) == baseline
        report.update(status='passed', sourceDataUnchanged=True, staleJournalRejected=True,
                      invalidFingerprintRejected=True, liveDatabaseRejected=True,
                      ownershipConflictRejected=True, dryRunPreservedData=True,
                      erasedAccountAndContentRemoved=True, survivingDiscussionRecounted=True,
                      mediaCleanupQueued=True, retryIdempotent=True)
    finally:
        if created:
            actual = run(['docker', 'inspect', '--format', '{{index .Config.Labels "' + label + '"}}', name]).decode().strip()
            assert actual == nonce
            run(['docker', 'rm', '-f', '-v', name])
        report['temporaryContainerRemoved'] = True
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(report, indent=2) + '\n')
    args.receipt.chmod(0o600)
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
