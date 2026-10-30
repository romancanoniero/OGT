#!/usr/bin/env python3
"""Plan or reconcile prior account erasures in an OFFLINE OGT recovery DB only.

The source database, live routing, Firebase and media files are never restored
or modified. An authenticated journal must cover the source's final write time.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import stat
import subprocess
import sys

sys.dont_write_bytecode=True
from ogt_offsite_backup import open_erasure_journal, read_key, atomic_json, SSH


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--encrypted-journal',type=Path,required=True)
    p.add_argument('--key-file',type=Path,required=True)
    p.add_argument('--database',required=True)
    p.add_argument('--snapshot-epoch',type=int,required=True)
    p.add_argument('--required-through-epoch',type=int,required=True)
    p.add_argument('--receipt',type=Path,required=True)
    p.add_argument('--apply',action='store_true')
    a=p.parse_args()
    if not re.fullmatch('ogt_recovery_[a-f0-9]{32}',a.database):p.error('Only a labelled offline recovery database is supported')
    if a.snapshot_epoch<=0 or a.required_through_epoch<a.snapshot_epoch:p.error('Invalid recovery interval')
    path=a.encrypted_journal
    if path.is_symlink():p.error('Journal symlink refused')
    info=path.stat()
    if not stat.S_ISREG(info.st_mode) or info.st_uid!=os.getuid() or info.st_mode&0o077 or info.st_size>2*1024*1024:p.error('Journal ownership, permissions or size invalid')
    journal=open_erasure_journal(path.read_bytes(),read_key(a.key_file))
    if journal['capturedAtEpochSeconds']<a.required_through_epoch:p.error('Journal predates the final possible source write; recovery stays blocked')
    payload=json.dumps(journal,sort_keys=True,separators=(',',':')).encode();sha=hashlib.sha256(payload).hexdigest()
    cmd=['docker','exec','-i','ogt-api','java','-cp','/app/lib/*',
         'com.onlygoodthings.backend.auth.RecoveryPrivacy','--database',a.database,
         '--journal-sha256',sha,'--snapshot-epoch',str(a.snapshot_epoch),
         '--required-through-epoch',str(a.required_through_epoch)]
    if a.apply:cmd.append('--apply')
    r=subprocess.run(SSH+[shlex.join(cmd)],input=payload,capture_output=True,timeout=180)
    if r.returncode:raise SystemExit('Recovery privacy check failed; candidate remains unapproved. No private command output is printed.')
    lines=[line for line in r.stdout.decode().splitlines() if line.startswith('{')]
    if len(lines)!=1:raise SystemExit('Unexpected recovery receipt; no candidate approved')
    receipt=json.loads(lines[0]);assert receipt['journalSha256']==sha and receipt['mode']==('apply' if a.apply else 'plan')
    assert receipt['sourceDatabaseModified'] is False and receipt['firebaseProviderTouched'] is False and receipt['approvedForPublicServing'] is False
    a.receipt.parent.mkdir(parents=True,exist_ok=True);atomic_json(a.receipt,receipt)
    print(json.dumps(receipt,indent=2))


if __name__=='__main__':main()
