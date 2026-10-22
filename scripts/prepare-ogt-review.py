#!/usr/bin/env python3
"""Prepare only the two review settings on the existing OGT VPS; no service restart."""
import argparse
import fcntl
import ipaddress
import os
import re
import socket
import tempfile
from pathlib import Path

DOMAIN = 'onlygoodthings.lat'
LAUNCH_KEYS = {'OGT_LAUNCH_MODE', 'OGT_PRELAUNCH_CIDRS'}


class PreparationError(ValueError):
    """A fixed, non-secret explanation safe to display."""



def parse_env(text):
    result = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith('#'):
            continue
        key, sep, value = stripped.partition('=')
        if not sep or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key.strip()):
            raise PreparationError('Formato .env no compatible; no se imprimen valores.')
        key, value = key.strip(), value.strip()
        if key in result:
            raise PreparationError('Hay claves duplicadas en .env; resolverlas antes de publicar.')
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        result[key] = value
    return result


def prepare_text(text, client_ip):
    values = parse_env(text)
    if values.get('OGT_LAUNCH_MODE', '') not in ('', 'prelaunch'):
        raise PreparationError('La instalacion no esta marcada para revision privada; no se cambia su politica.')
    # Do not silently turn a development install into production authentication.
    if values.get('OGT_ENV') != 'production':
        raise PreparationError('OGT_ENV debe ser production; no se modifican credenciales ni autenticacion.')
    if values.get('OGT_ALLOW_DEV_TOKENS', 'false') != 'false' or values.get('FIREBASE_AUTH_EMULATOR_HOST'):
        raise PreparationError('Autenticacion de laboratorio detectada; no se publica.')
    if not (values.get('OGT_FIREBASE_CREDENTIALS') or values.get('GOOGLE_APPLICATION_CREDENTIALS')):
        raise PreparationError('Faltan credenciales Firebase en la instalacion existente.')
    if not values.get('OGT_DB_PASSWORD') or values.get('OGT_DB_USER', 'ogt') != 'ogt':
        raise PreparationError('La configuracion de la base existente requiere revision.')
    if values.get('OGT_FIREBASE_PROJECT_ID', 'goodthings-55612') != 'goodthings-55612':
        raise PreparationError('El proyecto Firebase no corresponde a OGT.')
    address = ipaddress.ip_address(client_ip)
    if not address.is_global or address.is_multicast:
        raise PreparationError('La IP SSH del revisor no es publica; no se infiere una red permitida.')
    nets = []
    for item in values.get('OGT_PRELAUNCH_CIDRS', '').split(','):
        if not item.strip():
            continue
        network = ipaddress.ip_network(item.strip(), strict=True)
        if network.prefixlen == 0 or network.is_multicast or network.is_unspecified:
            raise PreparationError('La lista de redes contiene un rango no permitido.')
        nets.append(network)
    if not any(address.version == net.version and address in net for net in nets):
        nets.append(ipaddress.ip_network(f'{address}/{address.max_prefixlen}', strict=True))
    cidrs = ','.join(str(net) for net in dict.fromkeys(nets))
    # Already sufficient: preserve original file byte-for-byte.
    if values.get('OGT_LAUNCH_MODE') == 'prelaunch' and values.get('OGT_PRELAUNCH_CIDRS') == cidrs:
        return text, cidrs
    kept = []
    for line in text.splitlines(keepends=True):
        if line.strip().partition('=')[0].strip() not in LAUNCH_KEYS:
            kept.append(line)
    body = ''.join(kept)
    if body and not body.endswith('\n'):
        body += '\n'
    return body + f'OGT_LAUNCH_MODE=prelaunch\nOGT_PRELAUNCH_CIDRS={cidrs}\n', cidrs


def save_env(path, before, after):
    if before == after:
        return None
    path = Path(path)
    if path.is_symlink() or path.read_bytes() != before.encode('utf-8'):
        raise PreparationError('El archivo .env cambio durante la preparacion; no se reemplaza.')
    fd, backup_name = tempfile.mkstemp(prefix='.env.before-review-', dir=path.parent)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(before.encode('utf-8'))
        stream.flush()
        os.fsync(stream.fileno())
    fd, temp_name = tempfile.mkstemp(prefix='.env.review-', dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(after.encode('utf-8'))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temp_name, path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)
    return Path(backup_name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    try:
        parts = os.environ.get('SSH_CONNECTION', '').split()
        if len(parts) != 4:
            raise PreparationError('Ejecutar a traves del acceso SSH habitual de la Mac.')
        client, _, server, _ = parts
        server = ipaddress.ip_address(server)
        # Do not guess a proxy/CDN policy or add shared proxy ranges.
        resolved = {ipaddress.ip_address(row[4][0]) for row in socket.getaddrinfo(DOMAIN, 443)}
        if server not in resolved:
            raise PreparationError('El dominio no resuelve directamente a esta VPS. Revisar proxy/DNS antes de restringir acceso.')
        root = Path.home() / 'ogt'
        if root.is_symlink() or not root.is_dir():
            raise PreparationError('No se encontro la instalacion OGT existente esperada.')
        with (root / '.release.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            path = root / '.env'
            if path.is_symlink():
                raise PreparationError('No se modifica un .env enlazado simbolicamente.')
            before = path.read_bytes().decode('utf-8')
            after, cidrs = prepare_text(before, client)
            if args.apply:
                backup = save_env(path, before, after)
                print('Revision privada configurada.' if backup else 'Revision privada ya configurada.')
                if backup:
                    print('Copia privada del .env anterior: ' + str(backup))
            else:
                print('Comprobacion correcta. No se modifico .env.')
            print('Redes de revision: ' + cidrs)
            print('No se reiniciaron servicios ni se modificaron credenciales o datos de la aplicacion.')
    except PreparationError as error:
        parser.exit(1, f'Preparacion bloqueada: {error} No se reiniciaron servicios.\n')
    except (ValueError, OSError, UnicodeError):
        # Never include raw dotenv content, IP parsing input or secret values in logs.
        parser.exit(1, 'Preparacion bloqueada: revisar acceso SSH directo, DNS, autenticacion real y configuracion privada existente. No se reiniciaron servicios.\n')


if __name__ == '__main__':
    main()
