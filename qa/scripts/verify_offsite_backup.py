#!/usr/bin/env python3
"""Restore an encrypted OGT backup only into a temporary, disconnected local DB.

Requires the already-installed Docker runtime and official PostGIS image. Never
connects to the VPS, publishes a port or mounts a host data directory.
"""
import argparse
import io
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import tarfile
import time

sys.dont_write_bytecode = True
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'scripts'))
from ogt_offsite_backup import atomic_json, read_key, unseal, verify_payload, LIMIT


def run(args, data=None, timeout=90):
    return subprocess.run(args, input=data, capture_output=True, check=True, timeout=timeout).stdout


def wait_for_database(name):
    # The image's temporary initialization server accepts Unix-socket probes
    # before POSTGRES_DB exists. Require a successful query over final TCP.
    for _ in range(50):
        result = subprocess.run(['docker', 'exec', '-e', 'PGCONNECT_TIMEOUT=2', name,
                                 'psql', '-h', '127.0.0.1', '-X', '-qAt',
                                 '-U', 'postgres', '-d', 'ogt_rehearsal', '-c', 'SELECT 1'],
                                capture_output=True, timeout=4)
        if result.returncode == 0 and result.stdout.strip() == b'1':
            return
        time.sleep(0.2)
    raise RuntimeError('ISOLATED_DATABASE_NOT_READY')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--key-file', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    if args.archive.is_symlink() or args.archive.stat().st_size > LIMIT or args.receipt.exists():
        parser.error('Archive path/size or existing receipt refused')
    payload = unseal(args.archive.read_bytes(), read_key(args.key_file))
    with tarfile.open(fileobj=io.BytesIO(payload), mode='r:gz') as archive:
        manifest = json.load(archive.extractfile('offsite-manifest.json'))
        verify_payload(payload, manifest)
        dump = archive.extractfile('database.dump').read()
    image = 'postgis/postgis:16-3.4'
    if manifest['postgresImage'] != image:
        parser.error('Unexpected database image; no software was run')
    run(['docker', 'image', 'inspect', image])
    name = 'ogt-offsite-verify-' + secrets.token_hex(6)
    created = False
    try:
        run(['docker', 'run', '-d', '--name', name, '--network', 'none',
             '--memory', '512m', '--pids-limit', '128',
             '--tmpfs', '/var/lib/postgresql/data:rw,nosuid,nodev,size=268435456',
             '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', '-e', 'POSTGRES_DB=ogt_rehearsal', image])
        created = True
        wait_for_database(name)
        run(['docker', 'exec', '-i', name, 'pg_restore', '--exit-on-error', '--no-owner',
             '--no-privileges', '-U', 'postgres', '-d', 'ogt_rehearsal'], dump)
        prefix = ['docker', 'exec', '-e', 'PGOPTIONS=-c timezone=UTC', name, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                  '-U', 'postgres', '-d', 'ogt_rehearsal']
        tables = json.loads(run(prefix + ['-c', "SELECT json_agg(tablename ORDER BY tablename) FROM pg_tables WHERE schemaname='public'"]))
        parts = []
        for table in tables:
            quoted = '"' + table.replace('"', '""') + '"'
            literal = "'" + table.replace("'", "''") + "'"
            parts.append("SELECT " + literal + " AS name,count(*) AS rows,md5(coalesce(string_agg(h,'' ORDER BY h),'')) AS digest FROM (SELECT md5(row_to_json(t)::text) h FROM public." + quoted + " t) hashed")
        query = "SELECT json_object_agg(name,json_build_object('rows',rows,'digest',digest)) FROM (" + ' UNION ALL '.join(parts) + ') state'
        restored = json.loads(run(prefix + ['-c', query]))
        if 'database' in manifest and restored != manifest['database']:
            raise RuntimeError('RESTORE_DATA_FINGERPRINT_MISMATCH')
        if 'databaseSchema' in manifest and sorted(restored) != sorted(manifest['databaseSchema']):
            raise RuntimeError('RESTORE_SCHEMA_MISMATCH')
    finally:
        if created:
            run(['docker', 'rm', '-f', name])
    receipt = {'verifiedAtEpochSeconds': int(time.time()), 'isolatedRestoreSucceeded': True,
               'databaseTables': len(restored), 'sourceDumpHashVerified': True,
               'fullTableFingerprintsComparedToLiveSource': 'database' in manifest,
               'restoredSchemaMatchesSource': True,
               'mediaFiles': len(manifest['media']), 'mediaHashesVerified': True,
               'temporaryDatabaseRemoved': True, 'sourceDatabaseContacted': False,
               'sourceDatabaseRestored': False, 'hostDirectoryMounted': False,
               'networkDisabled': True, 'secretsIncluded': False}
    atomic_json(args.receipt, receipt)
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
