#!/usr/bin/env python3
"""Rotate only verified OGT packages. Media, manual backups and unknown files are protected."""
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
from ogt_operations import atomic

RELEASE = re.compile(r'([a-f0-9]{40})-(\d{8}T\d{6}Z)')
BACKUP_FILES = {'application.tgz', 'database.dump', 'database-toc.txt', 'previous-image.txt', 'published-release.json'}


def digest(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def age(name, now):
    match = RELEASE.fullmatch(name)
    return now - dt.datetime.strptime(match[2], '%Y%m%dT%H%M%SZ').replace(tzinfo=dt.timezone.utc).timestamp()


def sealed(folder):
    if folder.is_symlink() or not RELEASE.fullmatch(folder.name):
        return False
    try:
        manifest = json.loads((folder / 'release-manifest.json').read_text())
        if (manifest.get('repository'), manifest.get('scope'), manifest.get('sourceSha')) != (
                'romancanoniero/OGT', 'app-backend-crms', RELEASE.fullmatch(folder.name)[1]):
            return False
        actual = {}
        for file in folder.rglob('*'):
            if file.is_symlink():
                return False
            if file.is_file() and file != folder / 'release-manifest.json':
                actual[file.relative_to(folder).as_posix()] = digest(file)
        return bool(actual) and actual == manifest.get('files') and all(name in actual for name in (
            'backend-dist/bin/backend', 'web-app/release.json', 'Dockerfile', 'docker-compose.yml', 'web-nginx.conf'))
    except (OSError, ValueError, TypeError):
        return False


def fresh_backup(root, now):
    for folder in sorted((root / 'backups').glob('scheduled-*'), reverse=True):
        if folder.is_symlink():
            continue
        try:
            receipt = json.loads((folder / 'backup.json').read_text())
            archive = folder / 'database.dump'
            if archive.is_symlink() or not archive.is_file() or not 0 <= now - receipt['completedAtEpochSeconds'] <= 48 * 3600:
                continue
            if receipt.get('kind') == 'postgres-custom' and receipt.get('archiveBytes') == archive.stat().st_size and receipt.get('archiveSha256') == digest(archive):
                with archive.open('rb') as stream:
                    if stream.read(5) == b'PGDMP' and (folder / 'database-toc.txt').is_file():
                        return folder
        except (OSError, ValueError, KeyError, TypeError):
            continue
    raise ValueError('RECENT_VERIFIED_BACKUP_REQUIRED')


def docker_inventory():
    rows = subprocess.run(['docker','image','ls','--no-trunc','--format','{{json .}}','ogt-backend'],capture_output=True,text=True,check=True,timeout=30)
    images = [json.loads(line) for line in rows.stdout.splitlines() if line.strip()]
    containers = subprocess.run(['docker','container','ls','-aq'],capture_output=True,text=True,check=True,timeout=30).stdout.split()
    used = set()
    if containers:
        used = set(subprocess.run(['docker','inspect','--format','{{.Image}}',*containers],capture_output=True,text=True,check=True,timeout=30).stdout.split())
    return images, used


def image_removals(root, keep, removals, now):
    images, protected = docker_inventory()
    discarded_archives = {item['path'] for item in removals if item['kind'] in ('backup','application-backup')}
    for folder in (root/'backups').iterdir():
        if folder.is_symlink() or not folder.is_dir() or str(folder.relative_to(root)) in discarded_archives:
            continue
        if not RELEASE.fullmatch(folder.name) or folder.name in keep or (folder/'application.tgz').is_file():
            for name in ('previous-image.txt','image.txt'):
                path=folder/name
                if path.is_file() and not path.is_symlink():
                    value=path.read_text().strip()
                    if re.fullmatch('sha256:[a-f0-9]{64}',value):protected.add(value)
    found=[]
    for row in images:
        if row.get('Repository')!='ogt-backend' or row.get('ID') in protected:
            continue
        tag=row.get('Tag','');release=tag.removeprefix('before-')
        if RELEASE.fullmatch(release) and release not in keep and age(release,now)>=48*3600:
            found.append({'tag':'ogt-backend:'+tag,'imageId':row['ID']})
    return sorted(found,key=lambda item:item['tag'])


def plan(root, now=None, docker=False):
    now = time.time() if now is None else now
    for name in ('releases', 'backups', 'operations'):
        if (root / name).is_symlink():
            raise ValueError('SYMLINK_REFUSED')
    current = json.loads((root / 'current-release.json').read_text())['sourceSha']
    if not re.fullmatch('[a-f0-9]{40}', current):
        raise ValueError('INVALID_CURRENT_RELEASE')
    fresh = fresh_backup(root, now)
    candidates = sorted((p for p in (root / 'releases').iterdir() if RELEASE.fullmatch(p.name) and not p.is_symlink() and p.is_dir()), key=lambda p: p.name[-16:], reverse=True)
    # Always keep five newest packages, every package from the last 48h, and the active SHA.
    keep = {p.name for p in candidates[:5]} | {p.name for p in candidates if p.name.startswith(current + '-') or age(p.name, now) < 48 * 3600}
    pins = root / 'operations/retention-pins.json'
    if pins.exists():
        if pins.is_symlink():
            raise ValueError('SYMLINK_REFUSED')
        pinned = json.loads(pins.read_text())
        if not isinstance(pinned, list) or any(not isinstance(x, str) or not RELEASE.fullmatch(x) for x in pinned):
            raise ValueError('INVALID_RETENTION_PINS')
        keep.update(pinned)
    if not any(p.name.startswith(current + '-') and sealed(p) for p in candidates):
        raise ValueError('SEALED_CURRENT_RELEASE_REQUIRED')
    removals = []
    for folder in candidates:
        if folder.name not in keep and sealed(folder):
            removals.append({'kind': 'release', 'path': str(folder.relative_to(root)), 'files': {
                str(p.relative_to(folder)): digest(p) for p in folder.rglob('*') if p.is_file()}})
    for folder in (root / 'backups').iterdir():
        if folder.is_symlink() or not folder.is_dir() or not RELEASE.fullmatch(folder.name) or folder.name in keep or folder.name.startswith(current+'-') or age(folder.name, now) < 48 * 3600:
            continue
        files = list(folder.iterdir())
        if not {'database.dump', 'database-toc.txt', 'published-release.json'} <= {p.name for p in files} <= BACKUP_FILES:
            continue
        if any(p.is_symlink() or not p.is_file() or p.stat().st_size == 0 or now-p.stat().st_mtime < 48*3600 for p in files):
            continue
        try:
            published = json.loads((folder / 'published-release.json').read_text())
            with (folder / 'database.dump').open('rb') as stream:
                valid = stream.read(5) == b'PGDMP'
            if not valid or published.get('sourceSha') != RELEASE.fullmatch(folder.name)[1] or published.get('scope') != 'app-backend-crms':
                continue
        except (OSError, ValueError):
            continue
        if age(folder.name,now)>=30*86400 and all(now-p.stat().st_mtime>=30*86400 for p in files):
            removals.append({'kind': 'backup', 'path': str(folder.relative_to(root)), 'files': {p.name: digest(p) for p in files}})
        elif (folder/'application.tgz').is_file():
            # Keep the database snapshot for 30 days; redundant app archives follow release retention.
            removals.append({'kind': 'application-backup', 'path': str(folder.relative_to(root)), 'files': {'application.tgz': digest(folder/'application.tgz')}})
    return {'schemaVersion': 1, 'activeSha': current, 'freshBackup': fresh.name, 'keptReleases': sorted(keep),
            'remove': removals, 'candidateBytes': sum((root/item['path']/name).stat().st_size for item in removals for name in item['files']),
            'dockerIncluded':docker,'removeImages':image_removals(root,keep,removals,now) if docker else [],
            'mediaTouched': False}


def apply(root, reviewed):
    # Fail closed if anything changed after review. Save the entire removal inventory before unlinking.
    actual = plan(root,docker=reviewed.get('dockerIncluded',False))
    if actual != reviewed:
        raise ValueError('RETENTION_PLAN_CHANGED')
    archive = root / 'backups' / actual['freshBackup'] / 'database.dump'
    with archive.open('rb') as stream:
        subprocess.run(['docker', 'exec', '-i', 'ogt-postgres', 'pg_restore', '--list'], stdin=stream, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=30, check=True)
    subprocess.run(['curl', '-fsS', '--max-time', '10', 'http://127.0.0.1:19080/health'], stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, timeout=15, check=True)
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    atomic(root/'operations'/('retention-'+stamp+'.json'), json.dumps(actual, indent=2)+'\n')
    for item in actual['remove']:
        folder = root/item['path']
        # No recursive wildcard deletion: only the exact hashed files in the reviewed package.
        for name, expected in item['files'].items():
            path = folder/name
            if path.is_symlink() or digest(path) != expected:
                raise ValueError('RETENTION_FILE_CHANGED')
        for name in item['files']:
            (folder/name).unlink()
        if item['kind']!='application-backup':
            for directory in sorted((p for p in folder.rglob('*') if p.is_dir()), key=lambda p: len(p.parts), reverse=True):
                directory.rmdir()
            folder.rmdir()
    removed_tags=0
    for image in actual['removeImages']:
        # Exact OGT version tag only. No force, global prune, volumes, or other repositories.
        result=subprocess.run(['docker','image','rm','--no-prune',image['tag']],capture_output=True,text=True,check=False,timeout=30)
        if result.returncode==0:removed_tags+=1
    return {'removedPackages': len(actual['remove']), 'removedBytes': actual['candidateBytes'], 'removedImageTags':removed_tags,'mediaTouched': False}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    root = Path('/home/iankmp/ogt')
    if root.is_symlink():
        parser.error('Root symlink refused')
    with (root/'.release.lock').open('a') as release_lock, (root/'operations/backup.lock').open('a') as backup_lock:
        try:
            fcntl.flock(release_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            fcntl.flock(backup_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        reviewed = plan(root,docker=True)
        print(json.dumps(apply(root, reviewed) if args.apply else reviewed), flush=True)


if __name__ == '__main__':
    main()
