#!/usr/bin/env python3
"""Encrypted, verified OGT data/media pull. No restore, pruning or secret export.

Intended for a user LaunchAgent on the Mac. A disconnected destination is a
failure, never an instruction to create a replacement directory on the Mac.
The existing one-time OGTDATA1 backup format and key remain readable.
"""
import argparse
import datetime as dt
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import secrets
import shlex
import shutil
import stat
import subprocess
import sys
import tarfile
import time

sys.dont_write_bytecode = True
MAGIC = b'OGTDATA1\n'
AAD = b'OGT external data backup v1'
KIND = 'ogt-automatic-offsite-v1'
LIMIT = 512 * 1024 * 1024
SSH = ['/usr/bin/ssh', '-o', 'BatchMode=yes', '-o', 'StrictHostKeyChecking=yes',
       '-o', 'ConnectTimeout=15', '-o', 'ServerAliveInterval=15',
       '-o', 'ServerAliveCountMax=2', 'iankmp-vps']
REMOTE_ROOT = '/home/iankmp/ogt'
SIGNING_KEY = 'media/stories-private/.signing-key'

# pg_dump supplies its own consistent transaction while users keep writing.
# Only schema names and media fingerprints are compared around capture: normal
# views, location updates and other account writes must not block a valid dump.
SOURCE_STATE = r'''
import hashlib,io,json,subprocess,tarfile
from pathlib import PurePosixPath
sql=['docker','exec','-e','PGOPTIONS=-c timezone=UTC','ogt-postgres','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d','onlygoodthings']
def query(q):return json.loads(subprocess.check_output(sql+['-c',q],timeout=120))
tables=query("SELECT json_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname='public'")
raw=subprocess.check_output(['docker','cp','ogt-api:/app/media','-'],timeout=180)
if len(raw)>536870912:raise ValueError('MEDIA_SIZE_LIMIT')
files={}
with tarfile.open(fileobj=io.BytesIO(raw),mode='r:') as source:
 for item in source:
  p=PurePosixPath(item.name)
  if p.is_absolute() or '..' in p.parts or not p.parts or p.parts[0]!='media' or not(item.isdir() or item.isfile()):raise ValueError('MEDIA_PATH')
  if item.name=='media/stories-private/.signing-key':continue
  if any(part.startswith('.') for part in p.parts):raise ValueError('UNKNOWN_PRIVATE_MEDIA')
  if item.isfile():files[item.name]=hashlib.sha256(source.extractfile(item).read()).hexdigest()
image=subprocess.check_output(['docker','inspect','ogt-postgres','--format','{{.Config.Image}}'],text=True).strip()
print(json.dumps({'databaseSchema':tables,'media':files,'postgresImage':image}))
'''


class BackupError(Exception):
    """Only fixed, non-sensitive error codes are exposed to scheduled logs."""


def private_directory(path):
    path = Path(path)
    if path.is_symlink():
        raise BackupError('DIRECTORY_SYMLINK')
    path.mkdir(mode=0o700, parents=True, exist_ok=True)
    if path.stat().st_uid != os.getuid() or path.stat().st_mode & 0o077:
        raise BackupError('DIRECTORY_PERMISSIONS')
    return path


def atomic_json(path, value):
    if path.is_symlink():
        raise BackupError('STATE_SYMLINK')
    temp = path.with_name(path.name + '.tmp-' + secrets.token_hex(8))
    try:
        fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as handle:
            json.dump(value, handle, ensure_ascii=False, indent=2)
            handle.write('\n'); handle.flush(); os.fsync(handle.fileno())
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


def read_key(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as handle:
        info = os.fstat(handle.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_uid != os.getuid() or info.st_mode & 0o077:
            raise BackupError('KEY_PERMISSIONS')
        key = handle.read(33)
    if len(key) != 32:
        raise BackupError('KEY_LENGTH')
    return key


def destination(config):
    mount = Path(config['mount'])
    target = Path(config['destination'])
    if not mount.is_absolute() or not target.is_absolute() or not mount.is_mount():
        raise BackupError('DESTINATION_NOT_MOUNTED')
    if '..' in target.parts or mount.is_symlink() or not target.is_relative_to(mount) or target == mount:
        raise BackupError('DESTINATION_SCOPE')
    for folder in [target, *target.parents]:
        if folder == mount:
            break
        if folder.is_symlink():
            raise BackupError('DESTINATION_SYMLINK')
    # Do not alter permissions of the user's existing volume or backup parent.
    private_directory(target)
    return target


def remote(args, data=None, timeout=240):
    stage = ('SOURCE_METADATA' if args[:2] == ['python3', '-'] else
             'SOURCE_DATABASE_BACKUP' if args[0] == 'python3' else
             'DOWNLOAD_MEDIA' if args[0] == 'docker' else
             'SOURCE_HASH' if args[0] == 'sha256sum' else 'DOWNLOAD_DATABASE_FILE')
    try:
        result = subprocess.run(SSH + [shlex.join(args)], input=data, capture_output=True,
                                timeout=timeout, check=True)
    except subprocess.CalledProcessError as error:
        raise BackupError(stage + '_FAILED_' + str(error.returncode)) from None
    except subprocess.TimeoutExpired:
        raise BackupError(stage + '_TIMEOUT') from None
    if len(result.stdout) > LIMIT:
        raise BackupError('CAPTURE_SIZE_LIMIT')
    return result.stdout


def safe_member(item):
    p = PurePosixPath(item.name)
    if p.is_absolute() or '..' in p.parts or not p.parts or not(item.isdir() or item.isfile()):
        raise BackupError('ARCHIVE_PATH')
    if item.size < 0 or item.size > LIMIT:
        raise BackupError('MEMBER_SIZE_LIMIT')
    return p


def pack(media, dump, metadata, manifest):
    out = io.BytesIO()
    total = 0
    with tarfile.open(fileobj=out, mode='w:gz') as target:
        with tarfile.open(fileobj=io.BytesIO(media), mode='r:') as source:
            for item in source:
                p = safe_member(item)
                if p.parts[0] != 'media':
                    raise BackupError('MEDIA_PREFIX')
                if item.name == SIGNING_KEY:
                    continue
                if any(part.startswith('.') for part in p.parts):
                    raise BackupError('UNKNOWN_PRIVATE_MEDIA')
                total += item.size
                if total > LIMIT:
                    raise BackupError('ARCHIVE_SIZE_LIMIT')
                # Normalize metadata; never restore Docker ownership or file modes.
                saved = tarfile.TarInfo(item.name)
                saved.type = tarfile.DIRTYPE if item.isdir() else tarfile.REGTYPE
                saved.mode = 0o700 if item.isdir() else 0o600
                saved.size = item.size
                target.addfile(saved, source.extractfile(item) if item.isfile() else None)
        for name, data in [('database.dump', dump), ('database-backup.json', metadata),
                           ('offsite-manifest.json', json.dumps(manifest, sort_keys=True).encode())]:
            saved = tarfile.TarInfo(name); saved.size = len(data); saved.mode = 0o600
            target.addfile(saved, io.BytesIO(data))
    return out.getvalue()


def verify_payload(payload, expected):
    files = {}; seen = set(); total = 0; manifest = None; dump_digest = None
    with tarfile.open(fileobj=io.BytesIO(payload), mode='r:gz') as archive:
        for member in archive:
            safe_member(member)
            if member.name in seen:
                raise BackupError('DUPLICATE_MEMBER')
            seen.add(member.name); total += member.size
            if total > LIMIT:
                raise BackupError('ARCHIVE_SIZE_LIMIT')
            if not member.isfile():
                continue
            value = archive.extractfile(member).read()
            if member.name.startswith('media/'):
                if member.name == SIGNING_KEY:
                    raise BackupError('SECRET_IN_ARCHIVE')
                files[member.name] = hashlib.sha256(value).hexdigest()
            elif member.name == 'database.dump':
                if not value.startswith(b'PGDMP'):
                    raise BackupError('INVALID_DATABASE_DUMP')
                dump_digest = hashlib.sha256(value).hexdigest()
            elif member.name == 'offsite-manifest.json':
                manifest = json.loads(value)
            elif member.name != 'database-backup.json':
                raise BackupError('UNKNOWN_ARCHIVE_FILE')
    if manifest != expected or files != expected['media'] or dump_digest != expected['databaseDumpSha256']:
        raise BackupError('ARCHIVE_VERIFICATION')


def seal(payload, key):
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    nonce = secrets.token_bytes(12)
    return MAGIC + nonce + AESGCM(key).encrypt(nonce, payload, AAD)


def unseal(encrypted, key):
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    if not encrypted.startswith(MAGIC) or len(encrypted) < len(MAGIC) + 28:
        raise BackupError('ARCHIVE_HEADER')
    n = len(MAGIC)
    return AESGCM(key).decrypt(encrypted[n:n+12], encrypted[n+12:], AAD)


def latest_verified(target):
    receipts = []
    for path in target.glob('automatic-*/receipt.json'):
        if path.is_symlink() or path.parent.is_symlink():
            continue
        try:
            data = json.loads(path.read_text())
            archive = path.parent / 'database-and-media.tar.gz.aesgcm'
            if (data.get('kind') == KIND and data.get('encryptionRoundtripVerified') is True
                    and type(data.get('completedAtEpochSeconds')) == int
                    and archive.is_file() and not archive.is_symlink()
                    and archive.stat().st_size == data['encryptedBytes']
                    and hashlib.sha256(archive.read_bytes()).hexdigest() == data['encryptedSha256']):
                receipts.append(data)
        except (OSError, ValueError, KeyError):
            continue
    return max(receipts, key=lambda row: row['completedAtEpochSeconds']) if receipts else None


def prune_automatic(target, days):
    """Expire only this runner's complete files, after a recent valid copy exists."""
    if days is None:
        return 0
    if days not in (30, 90):
        raise BackupError('RETENTION_POLICY')
    now = time.time(); latest = latest_verified(target)
    if not latest or not 0 <= now - latest['completedAtEpochSeconds'] < 48 * 3600:
        raise BackupError('RECENT_BACKUP_REQUIRED_FOR_RETENTION')
    count = 0
    for folder in target.glob('automatic-*'):
        if folder.is_symlink() or not folder.is_dir() or not re.fullmatch(r'automatic-\d{8}T\d{12}Z', folder.name):
            continue
        if {p.name for p in folder.iterdir()} != {'receipt.json', 'database-and-media.tar.gz.aesgcm'}:
            continue
        receipt = folder / 'receipt.json'; archive = folder / 'database-and-media.tar.gz.aesgcm'
        if receipt.is_symlink() or archive.is_symlink() or not receipt.is_file() or not archive.is_file():
            continue
        try:
            data = json.loads(receipt.read_text())
        except (OSError, ValueError):
            continue
        cutoff = now - days * 86400
        folder_time = dt.datetime.strptime(folder.name, 'automatic-%Y%m%dT%H%M%S%fZ').replace(tzinfo=dt.timezone.utc).timestamp()
        if (data.get('kind') != KIND or data.get('encryptionRoundtripVerified') is not True
                or type(data.get('completedAtEpochSeconds')) != int
                or max(data['completedAtEpochSeconds'], folder_time, archive.stat().st_mtime, receipt.stat().st_mtime) >= cutoff
                or data.get('encryptedBytes') != archive.stat().st_size
                or data.get('encryptedSha256') != hashlib.sha256(archive.read_bytes()).hexdigest()):
            continue
        archive.unlink(); receipt.unlink(); folder.rmdir(); count += 1
    return count


def backup(config, if_due=False):
    target = destination(config)
    days = config.get('retentionDays')
    if days not in (None, 30, 90):
        raise BackupError('RETENTION_POLICY')
    previous = latest_verified(target)
    if if_due and previous and 0 <= time.time() - previous['completedAtEpochSeconds'] < 24 * 3600:
        prune_automatic(target, days)
        return None
    key = read_key(config['keyFile'])
    before = json.loads(remote(['python3', '-'], SOURCE_STATE.encode()))
    response = json.loads(remote(['python3', '-B', REMOTE_ROOT + '/operations-runtime/ogt_backup.py']))
    backup_id = response['backupId']
    if not re.fullmatch(r'scheduled-\d{8}T\d{6}Z', backup_id):
        raise BackupError('INVALID_SOURCE_BACKUP')
    source = REMOTE_ROOT + '/backups/' + backup_id + '/'
    dump = remote(['cat', source + 'database.dump'])
    metadata = remote(['cat', source + 'backup.json'])
    if not dump.startswith(b'PGDMP') or json.loads(metadata).get('mediaIncluded') is not False:
        raise BackupError('INVALID_SOURCE_BACKUP')
    source_hash = json.loads(metadata).get('archiveSha256')
    if source_hash is None:
        # Compatibility with the previous server runtime during a staged upgrade.
        source_hash = remote(['sha256sum', source + 'database.dump']).decode().split()[0]
    if not re.fullmatch(r'[0-9a-f]{64}', source_hash) or hashlib.sha256(dump).hexdigest() != source_hash:
        raise BackupError('SOURCE_DATABASE_HASH_MISMATCH')
    media = remote(['docker', 'cp', 'ogt-api:/app/media', '-'])
    after = json.loads(remote(['python3', '-'], SOURCE_STATE.encode()))
    if before != after:
        raise BackupError('SOURCE_CHANGED_RETRY_NEXT_RUN')
    manifest = {**before, 'kind': KIND, 'sourceBackupId': backup_id,
                'databaseDumpSha256': hashlib.sha256(dump).hexdigest()}
    payload = pack(media, dump, metadata, manifest)
    verify_payload(payload, manifest)
    encrypted = seal(payload, key)
    if shutil.disk_usage(target).free < len(encrypted) + 100 * 1024 * 1024:
        raise BackupError('INSUFFICIENT_DESTINATION_SPACE')
    stamp = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    folder = target / ('automatic-' + stamp); folder.mkdir(mode=0o700)
    archive = folder / 'database-and-media.tar.gz.aesgcm'
    fd = os.open(archive, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as handle:
        handle.write(encrypted); handle.flush(); os.fsync(handle.fileno())
    recovered = unseal(archive.read_bytes(), key)
    verify_payload(recovered, manifest)
    receipt = {'kind': KIND, 'completedAtEpochSeconds': int(time.time()),
               'sourceBackupId': backup_id, 'sourceSha': json.loads(metadata)['sourceSha'],
               'databaseTables': len(before['databaseSchema']), 'mediaFiles': len(before['media']),
               'encryptedBytes': len(encrypted), 'encryptedSha256': hashlib.sha256(encrypted).hexdigest(),
               'authenticatedEncryption': 'AES-256-GCM', 'encryptionRoundtripVerified': True,
               'sourceFingerprintsStable': True, 'mediaHashesVerified': True,
               'databaseDumpHashVerified': True, 'databaseDumpHashMatchesSource': True,
               'transactionallyConsistentPgDump': True, 'concurrentAccountWritesAllowed': True,
               'postgresTocVerifiedOnSource': True,
               'isolatedRestorePerformedThisRun': False, 'sourceDataModified': False,
               'storyUrlSigningKeyExcluded': True, 'secretsIncluded': False,
               'retentionDays': days, 'pruningEnabled': days is not None}
    atomic_json(folder / 'receipt.json', receipt)
    prune_automatic(target, days)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--if-due', action='store_true')
    args = parser.parse_args()
    state = private_directory(Path.home() / '.local/state/ogt/offsite-backup')
    lock_fd = os.open(state / 'runner.lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    with os.fdopen(lock_fd, 'r+') as lock:
        os.fchmod(lock.fileno(), 0o600)
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        previous = json.loads((state / 'status.json').read_text()) if (state / 'status.json').is_file() else {}
        try:
            if args.config.is_symlink() or args.config.stat().st_uid != os.getuid() or args.config.stat().st_mode & 0o077:
                raise BackupError('CONFIG_PERMISSIONS')
            # The first background invocation performs a real transfer even if a
            # manual rehearsal just ran, so launchd's SSH/volume access is tested.
            scheduled_verified = previous.get('scheduledTransferVerified') is True
            result = backup(json.loads(args.config.read_text()), args.if_due and scheduled_verified)
            status = {'status': 'verified' if result else 'not_due',
                      'lastAttemptEpochSeconds': int(time.time()),
                      'scheduledTransferVerified': scheduled_verified or (args.if_due and result is not None),
                      'lastSuccessEpochSeconds': result['completedAtEpochSeconds'] if result else previous.get('lastSuccessEpochSeconds')}
            atomic_json(state / 'status.json', status)
            if result:
                print(json.dumps({'event': 'ogt_offsite_backup', **status}), flush=True)
        except Exception as error:
            code = str(error) if isinstance(error, BackupError) else type(error).__name__.upper()
            status = {'status': 'failed', 'errorCode': code,
                      'lastAttemptEpochSeconds': int(time.time()),
                      'scheduledTransferVerified': previous.get('scheduledTransferVerified') is True,
                      'lastSuccessEpochSeconds': previous.get('lastSuccessEpochSeconds')}
            atomic_json(state / 'status.json', status)
            print(json.dumps({'event': 'ogt_offsite_backup', **status}), flush=True)
            raise SystemExit(2)


if __name__ == '__main__':
    main()
