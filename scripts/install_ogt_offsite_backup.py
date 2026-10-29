#!/usr/bin/env python3
"""Install only the OGT offsite user LaunchAgent; leave other jobs untouched."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import subprocess
import sys

sys.dont_write_bytecode = True
from ogt_offsite_backup import BackupError, atomic_json, destination, private_directory, read_key

LABEL = 'com.onlygoodthings.offsite-backup'


def agent_definition(python, runner, config, state):
    return {'Label': LABEL, 'ProgramArguments': [str(python), '-B', str(runner), '--config', str(config), '--if-due'],
            'RunAtLoad': True, 'StartInterval': 3600, 'StartOnMount': True,
            'ProcessType': 'Background', 'ThrottleInterval': 300,
            'EnvironmentVariables': {'PATH': '/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin'},
            'StandardOutPath': str(state / 'events.log'), 'StandardErrorPath': str(state / 'errors.log')}


def existing_agent(path, runner):
    if path.is_symlink():
        raise BackupError('AGENT_SYMLINK')
    if not path.exists():
        return None
    value = plistlib.loads(path.read_bytes())
    args = value.get('ProgramArguments', [])
    if value.get('Label') != LABEL or len(args) < 3 or args[2] != str(runner):
        raise BackupError('UNRELATED_EXISTING_AGENT')
    return value


def inventory(folder, exclude):
    return {path.name: hashlib.sha256(path.read_bytes()).hexdigest()
            for path in folder.iterdir() if path.is_file() and path != exclude}


def write_private(path, content):
    if path.is_symlink():
        raise BackupError('INSTALL_TARGET_SYMLINK')
    temp = path.with_name(path.name + '.installing')
    fd = os.open(temp, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(fd, 'wb') as handle:
            handle.write(content); handle.flush(); os.fsync(handle.fileno())
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mount', type=Path, required=True)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--key-file', type=Path, required=True)
    parser.add_argument('--retention-days', type=int, choices=(30, 90), required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    if sys.platform != 'darwin':
        parser.error('This installer requires macOS')
    home = Path.home()
    runtime = home / '.local/share/ogt/offsite-backup'
    state = home / '.local/state/ogt/offsite-backup'
    agents = home / 'Library/LaunchAgents'
    runner = runtime / 'ogt_offsite_backup.py'; config_file = runtime / 'config.json'
    agent_file = agents / (LABEL + '.plist')
    config = {'mount': str(args.mount), 'destination': str(args.destination), 'keyFile': str(args.key_file),
              'retentionDays': args.retention_days}
    # Validate the approved destination and existing key before creating a job.
    destination(config); read_key(args.key_file)
    definition = agent_definition(sys.executable, runner, config_file, state)
    previous = existing_agent(agent_file, runner)
    source = Path(__file__).with_name('ogt_offsite_backup.py').read_bytes()
    plan = {'label': LABEL, 'runtime': str(runtime), 'destination': str(args.destination),
            'dailyCopy': True, 'retryIntervalSeconds': 3600, 'pruningEnabled': True,
            'retentionDays': args.retention_days,
            'runtimeSha256': hashlib.sha256(source).hexdigest(), 'existingOgtAgent': previous is not None}
    if not args.apply:
        print(json.dumps(plan, indent=2)); return
    baseline = inventory(agents, agent_file)
    private_directory(runtime); private_directory(state)
    # Do not silently replace a different saved destination or future policy.
    if config_file.exists():
        if config_file.is_symlink() or json.loads(config_file.read_text()) != config:
            raise BackupError('EXISTING_CONFIGURATION_DIFFERS')
    changed = (previous != definition or not runner.is_file() or runner.read_bytes() != source)
    write_private(runner, source)
    atomic_json(config_file, config)
    for name in ('events.log', 'errors.log'):
        fd = os.open(state / name, os.O_WRONLY | os.O_CREAT | os.O_NOFOLLOW, 0o600)
        os.close(fd)
    write_private(agent_file, plistlib.dumps(definition))
    domain = 'gui/' + str(os.getuid()); service = domain + '/' + LABEL
    loaded = subprocess.run(['/bin/launchctl', 'print', service], capture_output=True).returncode == 0
    if loaded and changed:
        subprocess.run(['/bin/launchctl', 'bootout', service], capture_output=True, check=True)
        loaded = False
    if not loaded:
        subprocess.run(['/bin/launchctl', 'bootstrap', domain, str(agent_file)], capture_output=True, check=True)
    subprocess.run(['/bin/launchctl', 'print', service], capture_output=True, check=True)
    if inventory(agents, agent_file) != baseline:
        raise BackupError('UNRELATED_AGENT_CHANGED_CONCURRENTLY')
    receipt = {**plan, 'installed': True, 'registeredWithLaunchd': True,
               'unrelatedAgentFilesUnchanged': True, 'keyCopied': False}
    atomic_json(state / 'installation.json', receipt)
    print(json.dumps(receipt, indent=2))


if __name__ == '__main__':
    main()
