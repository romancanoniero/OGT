#!/usr/bin/env python3
"""Prepare private review settings, or diagnose prerequisites without changing files."""
import argparse
import errno
import fcntl
import ipaddress
import os
import re
import signal
import socket
import stat
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
    try:
        address = ipaddress.ip_address(client_ip)
    except ValueError:
        raise PreparationError('La IP del revisor en SSH_CONNECTION no es valida.') from None
    if not address.is_global or address.is_multicast:
        raise PreparationError('La IP SSH del revisor no es publica; no se infiere una red permitida.')
    nets = []
    for item in values.get('OGT_PRELAUNCH_CIDRS', '').split(','):
        if not item.strip():
            continue
        try:
            network = ipaddress.ip_network(item.strip(), strict=True)
        except ValueError:
            raise PreparationError('OGT_PRELAUNCH_CIDRS contiene un CIDR invalido o con bits de host.') from None
        if network.prefixlen == 0 or network.is_multicast or network.is_unspecified:
            raise PreparationError('La lista de redes contiene un rango no permitido.')
        nets.append(network)
    if not any(address.version == net.version and address in net for net in nets):
        nets.append(ipaddress.ip_network(f'{address}/{address.max_prefixlen}', strict=True))
    cidrs = ','.join(str(net) for net in dict.fromkeys(nets))
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


def safe_failure(stage, error):
    """Only fixed labels and numeric error codes; never raw exception arguments."""
    if isinstance(error, PreparationError):
        detail = str(error)  # Only fixed application messages use this exception.
    elif isinstance(error, socket.gaierror):
        names = {getattr(socket, name): name for name in dir(socket) if name.startswith('EAI_')}
        code = names.get(error.errno, 'EAI_DESCONOCIDO')
        detail = 'Fallo de resolucion DNS (' + code + ').'
    elif isinstance(error, UnicodeError):
        detail = 'El archivo no tiene codificacion UTF-8 valida.'
    elif isinstance(error, PermissionError):
        detail = 'Permiso denegado al usuario SSH (EACCES/EPERM).'
    elif isinstance(error, FileNotFoundError):
        detail = 'No existe el archivo o directorio requerido (ENOENT).'
    elif isinstance(error, BlockingIOError):
        detail = 'Recurso ocupado; si es BLOQUEO, hay otra operacion activa (EAGAIN).'
    elif isinstance(error, TimeoutError):
        detail = 'Tiempo limite de la comprobacion agotado.'
    elif isinstance(error, OSError):
        detail = 'Fallo del sistema (' + errno.errorcode.get(error.errno, 'ERRNO_DESCONOCIDO') + ').'
    else:
        detail = 'Dato no valido en esta etapa; no se muestran valores privados.'
    return 'ERROR [' + stage + ']: ' + detail


def ssh_peer():
    parts = os.environ.get('SSH_CONNECTION', '').split()
    if len(parts) != 4:
        raise PreparationError('Ejecutar a traves del acceso SSH habitual de la Mac.')
    try:
        client = ipaddress.ip_address(parts[0])
        server = ipaddress.ip_address(parts[2])
    except ValueError:
        raise PreparationError('SSH_CONNECTION contiene una direccion no valida.') from None
    return str(client), server


def resolve_domain():
    # POSIX-only helper already requires fcntl. Do not wait indefinitely for DNS.
    def timed_out(_signum, _frame):
        raise TimeoutError()
    previous = signal.signal(signal.SIGALRM, timed_out)
    signal.alarm(10)
    try:
        return {ipaddress.ip_address(row[4][0]) for row in socket.getaddrinfo(DOMAIN, 443)}
    finally:
        signal.alarm(0)
        signal.signal(signal.SIGALRM, previous)


def require_direct(peer, resolved):
    if peer[1] not in resolved:
        raise PreparationError('El dominio no resuelve directamente a esta VPS. Revisar proxy/DNS antes de restringir acceso.')


def installation(root):
    if root.is_symlink() or not stat.S_ISDIR(root.stat().st_mode):
        raise PreparationError('No se encontro la instalacion OGT existente esperada.')
    return root


def read_env(path):
    # Do not follow links or block on special files. No content is printed.
    fd = os.open(str(path), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise PreparationError('El .env debe ser un archivo regular, no un enlace o dispositivo.')
        content = stream.read(1024 * 1024 + 1)
        if len(content) > 1024 * 1024:
            raise PreparationError('El .env excede el limite de lectura de 1 MiB.')
        return content.decode('utf-8')


def check_directory_write(root):
    # Permission check only: do not create a temporary file in diagnostic mode.
    if not os.access(root, os.W_OK | os.X_OK):
        raise PermissionError(errno.EACCES, 'fixed')


def check_lock_readonly(root):
    path = root / '.release.lock'
    if path.is_symlink():
        raise PreparationError('El archivo de bloqueo es un enlace; no se utiliza.')
    try:
        fd = os.open(str(path), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    except FileNotFoundError:
        check_directory_write(root)
        return
    with os.fdopen(fd, 'rb') as lock:
        if not stat.S_ISREG(os.fstat(lock.fileno()).st_mode):
            raise PreparationError('El bloqueo debe ser un archivo regular.')
        if not os.access(path, os.W_OK):
            raise PermissionError(errno.EACCES, 'fixed')
        # A shared probe on a read-only fd detects an active exclusive deploy lock.
        # It neither creates a file nor changes contents and is released immediately.
        fcntl.flock(lock, fcntl.LOCK_SH | fcntl.LOCK_NB)
        fcntl.flock(lock, fcntl.LOCK_UN)


def diagnose(root=None):
    """Report independent checks in one run. No file writes, apply, build or restart."""
    root = Path(root) if root is not None else Path.home() / 'ogt'
    failed = []
    skipped = object()
    def check(stage, action):
        try:
            result = action()
        except (PreparationError, ValueError, OSError) as error:
            failed.append(stage)
            print(safe_failure(stage, error))
            return skipped
        print('OK [' + stage + ']')
        return result
    print('OGT: diagnostico sin modificar archivos, credenciales ni servicios.')
    peer = check('CONEXION_SSH', ssh_peer)
    resolved = check('DNS', resolve_domain)
    if peer is not skipped and resolved is not skipped:
        check('DESTINO_DNS', lambda: require_direct(peer, resolved))
    else:
        print('OMITIDO [DESTINO_DNS]: depende de CONEXION_SSH y DNS.')
    folder = check('CARPETA_OGT', lambda: installation(root))
    if folder is not skipped:
        check('PERMISO_RESPALDO', lambda: check_directory_write(root))
        check('BLOQUEO', lambda: check_lock_readonly(root))
        contents = check('LECTURA_ENV', lambda: read_env(root / '.env'))
        if contents is not skipped and peer is not skipped:
            check('CONFIGURACION', lambda: prepare_text(contents, peer[0]))
        else:
            print('OMITIDO [CONFIGURACION]: depende de LECTURA_ENV y CONEXION_SSH.')
    print('RESULTADO: ' + (str(len(failed)) + ' bloqueo(s).' if failed else 'comprobaciones de lectura correctas; no es un despliegue.'))
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--apply', action='store_true')
    mode.add_argument('--diagnose', action='store_true')
    args = parser.parse_args()
    if args.diagnose:
        raise SystemExit(diagnose())
    stage = 'CONEXION_SSH'
    try:
        peer = ssh_peer()
        stage = 'DNS'
        resolved = resolve_domain()
        stage = 'DESTINO_DNS'
        require_direct(peer, resolved)
        stage = 'CARPETA_OGT'
        root = installation(Path.home() / 'ogt')
        stage = 'BLOQUEO'
        with (root / '.release.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            path = root / '.env'
            stage = 'LECTURA_ENV'
            before = read_env(path)
            stage = 'CONFIGURACION'
            after, cidrs = prepare_text(before, peer[0])
            if args.apply:
                stage = 'GUARDADO_ENV'
                backup = save_env(path, before, after)
                print('Revision privada configurada.' if backup else 'Revision privada ya configurada.')
                if backup:
                    print('Copia privada del .env anterior: ' + str(backup))
            else:
                print('Comprobacion correcta. No se modifico .env.')
            print('Redes de revision: ' + cidrs)
            print('No se reiniciaron servicios ni se modificaron credenciales o datos de la aplicacion.')
    except (PreparationError, ValueError, OSError) as error:
        parser.exit(1, safe_failure(stage, error) + '\nNo se reiniciaron servicios.\n')


if __name__ == '__main__':
    main()
