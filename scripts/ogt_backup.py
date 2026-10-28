#!/usr/bin/env python3
"""Daily custom-format PostgreSQL backup. Never restores, prunes or copies secrets."""
import argparse
import datetime as dt
import fcntl
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
from ogt_operations import atomic,command


def backup(root, if_due=False):
    complete=[p for p in (root/'backups').glob('scheduled-*/backup.json') if p.is_file()]
    if if_due and complete and time.time()-max(p.stat().st_mtime for p in complete)<24*3600:return None
    size=int(command(['docker','exec','ogt-postgres','psql','-X','-qAt','-U','ogt','-d','onlygoodthings',
                      '-c',"SELECT pg_database_size('onlygoodthings')"]))
    if shutil.disk_usage(root).free<max(2*1024**3,size*2):raise ValueError('INSUFFICIENT_BACKUP_SPACE')
    stamp=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    folder=root/'backups'/('scheduled-'+stamp);folder.mkdir(mode=0o700)
    archive=folder/'database.dump'
    with archive.open('xb') as handle:
        os.chmod(archive,0o600)
        subprocess.run(['docker','exec','ogt-postgres','pg_dump','-U','ogt','-d','onlygoodthings','-Fc'],
                       stdout=handle,stderr=subprocess.PIPE,timeout=120,check=True)
        handle.flush();os.fsync(handle.fileno())
    if not archive.stat().st_size:raise ValueError('EMPTY_ARCHIVE')
    with archive.open('rb') as handle:
        result=subprocess.run(['docker','exec','-i','ogt-postgres','pg_restore','--list'],stdin=handle,
                              stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30,check=True)
    atomic(folder/'database-toc.txt',result.stdout.decode())
    release=json.loads((root/'current-release.json').read_text())
    atomic(folder/'backup.json',json.dumps({'schemaVersion':1,'kind':'postgres-custom','sourceSha':release['sourceSha'],
        'completedAtEpochSeconds':int(time.time()),'archiveBytes':archive.stat().st_size,'secretsIncluded':False,'mediaIncluded':False})+'\n')
    return folder.name


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--if-due',action='store_true');args=parser.parse_args()
    root=Path('/home/iankmp/ogt');state=root/'operations'
    if root.is_symlink() or state.is_symlink() or (root/'backups').is_symlink():parser.error('Backup symlink refused')
    state.mkdir(mode=0o700,exist_ok=True)
    with (state/'backup.lock').open('a') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:return
        try:
            name=backup(root,args.if_due)
            if name:print(json.dumps({'event':'ogt_database_backup','status':'verified','backupId':name}),flush=True)
        except Exception:
            print(json.dumps({'event':'ogt_database_backup','status':'failed'}),flush=True);raise SystemExit(2)


if __name__=='__main__':main()
