#!/usr/bin/env python3
"""Daily PostgreSQL backup; optional expiry of generated snapshots, never a restore."""
import argparse
import datetime as dt
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import sys
sys.dont_write_bytecode = True
from ogt_operations import atomic,command


def prune_automatic(root, fresh, days):
    """Only OGT-generated completed DB snapshots; preserve every unknown file."""
    if days not in (30,90):raise ValueError('INVALID_RETENTION')
    base=root/'backups';current=base/fresh
    if base.is_symlink() or not re.fullmatch(r'(?:scheduled|[a-f0-9]{40})-\d{8}T\d{6}Z',fresh) or current.is_symlink() or any(p.is_symlink() or not p.is_file() or p.stat().st_size==0 for p in (current/'database.dump',current/'database-toc.txt')):
        raise ValueError('FRESH_BACKUP_REQUIRED')
    with (current/'database.dump').open('rb') as handle:
        if handle.read(5)!=b'PGDMP':raise ValueError('FRESH_BACKUP_REQUIRED')
    if time.time()-(current/'database.dump').stat().st_mtime>48*3600:raise ValueError('FRESH_BACKUP_REQUIRED')
    cutoff=time.time()-days*86400;removed=0
    for folder in base.iterdir():
        match=re.fullmatch(r'(?:scheduled|[a-f0-9]{40})-(\d{8}T\d{6}Z)',folder.name)
        if folder==current or folder.is_symlink() or not folder.is_dir() or not match:continue
        created=dt.datetime.strptime(match[1],'%Y%m%dT%H%M%SZ').replace(tzinfo=dt.timezone.utc).timestamp()
        files=list(folder.iterdir());names={p.name for p in files}
        if not {'database.dump','database-toc.txt'}<=names or not names<={'database.dump','database-toc.txt','backup.json'}:continue
        if any(p.is_symlink() or not p.is_file() or p.stat().st_size==0 or p.stat().st_mtime>=cutoff for p in files) or created>=cutoff:continue
        with (folder/'database.dump').open('rb') as handle:
            if handle.read(5)!=b'PGDMP':continue
        # No recursive deletion: a concurrent/unknown addition makes rmdir fail.
        for path in files:path.unlink()
        folder.rmdir();removed+=1
    return removed


def backup(root, if_due=False, retention_days=None):
    complete=[p for p in (root/'backups').glob('scheduled-*/backup.json') if p.is_file()]
    if if_due and complete and time.time()-max(p.stat().st_mtime for p in complete)<24*3600:
        if retention_days is not None:prune_automatic(root,max(complete,key=lambda p:p.stat().st_mtime).parent.name,retention_days)
        return None
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
    with archive.open('rb') as handle:archive_hash=hashlib.file_digest(handle,'sha256').hexdigest()
    atomic(folder/'backup.json',json.dumps({'schemaVersion':1,'kind':'postgres-custom','sourceSha':release['sourceSha'],
        'completedAtEpochSeconds':int(time.time()),'archiveBytes':archive.stat().st_size,'archiveSha256':archive_hash,'secretsIncluded':False,'mediaIncluded':False})+'\n')
    if retention_days is not None:prune_automatic(root,folder.name,retention_days)
    return folder.name


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--if-due',action='store_true');parser.add_argument('--retention-days',type=int,choices=(30,90));args=parser.parse_args()
    root=Path('/home/iankmp/ogt');state=root/'operations'
    if root.is_symlink() or state.is_symlink() or (root/'backups').is_symlink():parser.error('Backup symlink refused')
    state.mkdir(mode=0o700,exist_ok=True)
    with (state/'backup.lock').open('a') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:return
        try:
            name=backup(root,args.if_due,args.retention_days)
            if name:print(json.dumps({'event':'ogt_database_backup','status':'verified','backupId':name}),flush=True)
        except Exception:
            print(json.dumps({'event':'ogt_database_backup','status':'failed'}),flush=True);raise SystemExit(2)


if __name__=='__main__':main()
