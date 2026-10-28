#!/usr/bin/env python3
"""Install only the named OGT cron block; preserve every pre-existing job."""
import argparse
import datetime as dt
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
from ogt_operations import atomic

BEGIN='# BEGIN OGT OPERATIONS v1'
END='# END OGT OPERATIONS v1'
BLOCK='''# BEGIN OGT OPERATIONS v1
* * * * * /usr/bin/python3 /home/iankmp/ogt/operations-runtime/ogt_operations.py >> /home/iankmp/ogt/operations/events.log 2>&1
17 * * * * /usr/bin/python3 /home/iankmp/ogt/operations-runtime/ogt_backup.py --if-due >> /home/iankmp/ogt/operations/events.log 2>&1
# END OGT OPERATIONS v1
'''


def cron_with_block(original):
    if original.count(BEGIN)!=original.count(END) or original.count(BEGIN)>1:
        raise ValueError('Ambiguous OGT cron block')
    if BEGIN in original:
        start=original.index(BEGIN);end=original.index(END)+len(END)
        if end<start:raise ValueError('Invalid OGT cron block order')
        if original[end:end+1]=='\n':end+=1
        return original[:start]+BLOCK+original[end:]
    return original+('' if not original or original.endswith('\n') else '\n')+BLOCK


def read_cron():
    result=subprocess.run(['crontab','-l'],capture_output=True,text=True,check=False,env={**os.environ,'LC_ALL':'C'})
    if result.returncode not in (0,1):raise ValueError('Cannot read existing cron')
    if result.returncode==1 and (result.stdout or not re.fullmatch(r'no crontab for [A-Za-z0-9_-]+\n?',result.stderr)):raise ValueError('Cannot safely read existing cron')
    return result.stdout if result.returncode==0 else ''


def main():
    parser=argparse.ArgumentParser();parser.add_argument('release',type=Path);args=parser.parse_args()
    root=Path('/home/iankmp/ogt');release=args.release.resolve(strict=True)
    if release.parent!=root/'releases':parser.error('Only a sealed existing OGT release is supported')
    for folder in (root/'operations',root/'operations-runtime'):
        if folder.is_symlink():parser.error('Operational directory symlink refused')
        folder.mkdir(mode=0o700,exist_ok=True)
    # Validate the complete immutable package before copying executable code.
    from release_guard import verify_manifest
    manifest=json.loads((release/'release-manifest.json').read_text());verify_manifest(release,manifest['sourceSha'])
    for name in ('ogt_operations.py','ogt_backup.py'):
        atomic(root/'operations-runtime'/name,(release/'scripts'/name).read_text())
    original=read_cron();updated=cron_with_block(original)
    if updated!=original:
        stamp=dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
        atomic(root/'operations'/('crontab-before-'+stamp),original)
        if read_cron()!=original:raise ValueError('Cron changed concurrently; no jobs replaced')
        subprocess.run(['crontab','-'],input=updated,text=True,capture_output=True,check=True)
        if read_cron()!=updated:raise ValueError('Cron verification failed')
    print('OGT operational checks installed; unrelated jobs preserved')


if __name__=='__main__':main()
