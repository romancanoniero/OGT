#!/usr/bin/env python3
"""Restore one new OGT backup into a uniquely labelled, unserved verification DB."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import uuid


def sql(database,query):
    return subprocess.run(['docker','exec','ogt-postgres','psql','-X','-qAt','-v','ON_ERROR_STOP=1',
        '-U','ogt','-d',database,'-c',query],capture_output=True,text=True,check=True,timeout=30).stdout.strip()

COUNTS="""SELECT json_build_object('users',(SELECT count(*) FROM users),'posts',(SELECT count(*) FROM social_posts),
 'companies',(SELECT count(*) FROM companies),'members',(SELECT count(*) FROM crm_company_members),
 'companyAccess',(SELECT count(*) FROM crm_company_access),'ledger',(SELECT count(*) FROM csr_ledger),
 'balance',(SELECT coalesce(sum(campaign_balance_cents),0) FROM companies),
 'tables',(SELECT count(*) FROM information_schema.tables WHERE table_schema='public'));"""


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--archive',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);args=parser.parse_args()
    if args.archive.is_symlink():parser.error('Archive symlink refused')
    archive=args.archive.resolve(strict=True)
    if not re.fullmatch(r'/home/iankmp/ogt/backups/scheduled-[0-9]{8}T[0-9]{6}Z/database.dump',str(archive)) or not (archive.parent/'backup.json').is_file():parser.error('Only a completed scheduled OGT backup is supported')
    nonce=uuid.uuid4().hex;name='ogt_backup_verify_qa_'+nonce
    before=json.loads(sql('onlygoodthings',COUNTS));created=False
    receipt={'backupId':archive.parent.name,'sourceDatabaseRestored':False,'copyServedByApplication':False}
    try:
        sql('postgres','CREATE DATABASE "'+name+'"');created=True
        sql('postgres','COMMENT ON DATABASE "'+name+'" IS \'ogt-backup-verify:'+nonce+'\'')
        with archive.open('rb') as source:
            subprocess.run(['docker','exec','-i','ogt-postgres','pg_restore','-U','ogt','--no-owner','--exit-on-error','--single-transaction','--dbname',name],stdin=source,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=120)
        restored=json.loads(sql(name,COUNTS));after=json.loads(sql('onlygoodthings',COUNTS))
        stable=before==after
        if stable and restored!=before:raise ValueError('RESTORED_COUNTS_MISMATCH')
        receipt.update(status='verified',sourceCountsStable=stable,countsMatch=(restored==before) if stable else None,restoredCounts=restored)
    finally:
        if created:
            marker=sql('postgres',"SELECT coalesce(shobj_description(oid,'pg_database'),'') FROM pg_database WHERE datname='"+name+"'")
            if marker!='ogt-backup-verify:'+nonce:raise ValueError('VERIFICATION_DATABASE_MARKER_CHANGED')
            sql('postgres','DROP DATABASE "'+name+'"');receipt['temporaryDatabaseRemoved']=True
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(receipt,indent=2)+'\n');os.chmod(args.output,0o600)
    print(json.dumps(receipt,indent=2))


if __name__=='__main__':main()
