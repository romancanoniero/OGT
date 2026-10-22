#!/usr/bin/env python3
"""Clone one local SDK commit without touching its working tree or private files."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ALLOWED_REMOTES = {
    'https://github.com/romancanoniero/db-kmp-sdk',
    'https://github.com/romancanoniero/db-kmp-sdk.git',
    'git@github.com:romancanoniero/db-kmp-sdk.git',
}


class SnapshotError(ValueError):
    pass


def git(*args):
    env = dict(os.environ, GIT_OPTIONAL_LOCKS='0', GIT_TERMINAL_PROMPT='0')
    # Never inherit a different repository from a caller's Git plumbing command.
    for key in ('GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE', 'GIT_OBJECT_DIRECTORY',
                'GIT_ALTERNATE_OBJECT_DIRECTORIES', 'GIT_COMMON_DIR'):
        env.pop(key, None)
    result = subprocess.run(
        ['git', '-c', 'core.hooksPath=/dev/null', *map(str, args)],
        env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if result.returncode:
        # Do not print arbitrary repository contents, configuration or credentials.
        raise SnapshotError('Git no pudo preparar la copia del SDK. El original no se modifica.')
    return result.stdout.decode('utf-8').strip()


def create_snapshot(source, destination):
    source = Path(source).expanduser().resolve(strict=True)
    destination = Path(os.path.abspath(Path(destination).expanduser()))
    parent = destination.parent.resolve(strict=True)
    destination = parent / destination.name
    if destination.exists() or destination.is_symlink():
        raise SnapshotError('El destino ya existe; no se sobrescribe.')
    if parent == source or source in parent.parents:
        raise SnapshotError('La copia debe estar fuera del SDK original.')
    if Path(git('-C', source, 'rev-parse', '--show-toplevel')).resolve() != source:
        raise SnapshotError('Indica la raiz del repositorio SDK, no una subcarpeta.')
    origin = git('-C', source, 'remote', 'get-url', 'origin')
    if origin not in ALLOWED_REMOTES:
        raise SnapshotError('El origen no corresponde a romancanoniero/db-kmp-sdk.')
    revision = git('-C', source, 'rev-parse', '--verify', 'HEAD^{commit}')
    entries = git('-C', source, 'ls-tree', '-r', revision)
    if any(line.startswith('160000 ') for line in entries.splitlines()):
        raise SnapshotError('El SDK usa submodulos; se requiere preparar esa dependencia explicitamente.')
    # Transport cloning creates independent objects; it does not copy the working
    # directory, index, ignored credentials, untracked files or local Git config.
    with tempfile.TemporaryDirectory(prefix='.ogt-sdk-copy-', dir=parent) as scratch:
        checkout = Path(scratch) / 'sdk'
        git('clone', '--quiet', '--no-local', '--no-hardlinks', '--no-checkout',
            '--', source, checkout)
        git('-C', checkout, 'checkout', '--quiet', '--detach', revision)
        git('-C', checkout, 'remote', 'set-url', 'origin', origin)
        if git('-C', checkout, 'rev-parse', 'HEAD') != revision:
            raise SnapshotError('La copia no coincide con el commit solicitado.')
        if git('-C', checkout, 'status', '--porcelain', '--untracked-files=normal'):
            raise SnapshotError('La copia no esta limpia. No se usara para publicar.')
        settings = checkout / 'settings.gradle.kts'
        if not settings.is_file() or settings.is_symlink():
            raise SnapshotError('El commit del SDK no contiene settings.gradle.kts valido.')
        if (checkout / '.git/objects/info/alternates').exists():
            raise SnapshotError('La copia no es independiente del SDK original.')
        # The caller supplies a new directory in its private temporary folder.
        if destination.exists() or destination.is_symlink():
            raise SnapshotError('El destino aparecio durante la copia; no se sobrescribe.')
        shutil.move(str(checkout), str(destination))
    return revision


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source')
    parser.add_argument('destination')
    args = parser.parse_args()
    try:
        print(create_snapshot(args.source, args.destination))
    except SnapshotError as error:
        parser.exit(1, f'SDK: {error}\n')
    except (OSError, UnicodeError):
        parser.exit(1, 'SDK: no se pudo crear la copia local. No se publicaron archivos ni se altero el original.\n')


if __name__ == '__main__':
    main()
