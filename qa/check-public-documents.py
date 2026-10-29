#!/usr/bin/env python3
"""Exercise real Traefik rules against isolated static pages, never production."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = ('/privacy', '/privacy.html', '/terms', '/terms.html', '/terminos',
          '/uso', '/borrar-cuenta', '/borrar-cuenta.html', '/eliminar-cuenta',
          '/.well-known/apple-app-site-association', '/apple-app-site-association',
          '/.well-known/assetlinks.json')
PRIVATE = ('/', '/index.html', '/auth.js', '/crm/control/', '/crm/empresas/',
           '/api/v1/auth/session', '/api/v1/account/erase', '/api/v1/social/feed',
           '/api/v1/stories/list', '/media/example.jpg', '/db', '/ws', '/health',
           '/release.json', '/join/example', '/h/example', '/privacy/',
           '/privacy/api/v1/auth/session', '/privacy%2Fcrm%2Fcontrol%2F',
           '/.well-known/unknown.json')
LABEL = 'org.onlygoodthings.qa.public-documents'


def run(args):
    return subprocess.run(args, capture_output=True, text=True, check=True,
                          timeout=90).stdout.strip()


def configuration(source):
    labels = dict(line.strip()[2:].split('=', 1) for line in source.splitlines()
                  if line.strip().startswith('- traefik.'))
    routers = {}
    for name in ('ogt-web', 'ogt-mp', 'ogt-legal'):
        prefix = 'traefik.http.routers.' + name + '.'
        if prefix + 'rule' not in labels:
            continue
        router = {'rule': labels[prefix + 'rule'], 'entryPoints': ['web'],
                  'service': 'ogt-web', 'priority': int(labels[prefix + 'priority'])}
        if prefix + 'middlewares' in labels:
            router['middlewares'] = [v.replace('@docker', '') for v in labels[prefix + 'middlewares'].split(',')]
        routers[name] = router
    return {'http': {'routers': routers,
        'services': {'ogt-web': {'loadBalancer': {'servers': [{'url': 'http://web:80'}]}}},
        'middlewares': {'ogt-prelaunch': {'ipAllowList': {'sourceRange': ['192.0.2.1/32']}},
            'ogt-mp-rate': {'rateLimit': {'average': int(labels['traefik.http.middlewares.ogt-mp-rate.ratelimit.average']),
                                        'burst': int(labels['traefik.http.middlewares.ogt-mp-rate.ratelimit.burst'])}},
            'ogt-mp-body': {'buffering': {'maxRequestBodyBytes': int(labels['traefik.http.middlewares.ogt-mp-body.buffering.maxRequestBodyBytes']),
                                        'memRequestBodyBytes': int(labels['traefik.http.middlewares.ogt-mp-body.buffering.memRequestBodyBytes'])}}}}}


def request(port, path, method='GET', host='onlygoodthings.lat', headers=None):
    req = urllib.request.Request(f'http://127.0.0.1:{port}' + path,
        method=method, headers={'Host': host, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=4) as response:
            return response.status, response.read(), response.headers.get('Content-Type', '')
    except urllib.error.HTTPError as error:
        return error.code, error.read(), error.headers.get('Content-Type', '')


def check(port, public):
    count = 0
    protected = {}
    for host in ('onlygoodthings.lat', 'www.onlygoodthings.lat'):
        for path in PUBLIC:
            for method in ('GET', 'HEAD'):
                status, body, mime = request(port, path, method, host)
                assert status == (200 if public else 403), (host, method, path, status)
                if public:
                    assert ('json' if 'association' in path or 'assetlinks' in path else 'text/html') in mime
                    assert bool(body) == (method == 'GET')
                count += 1
            for method in ('POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'):
                assert request(port, path, method, host)[0] == 403, (method, path)
                count += 1
        for path in PRIVATE:
            status = request(port, path, host=host)[0]
            # Traefik rejects encoded slashes as malformed before route matching.
            assert status in ((400, 403) if '%2F' in path else (403,)), (path, status)
            protected[host + path] = status
            count += 1
    assert request(port, '/', headers={'X-Forwarded-For': '192.0.2.1', 'X-Real-IP': '192.0.2.1'})[0] == 403
    assert request(port, '/privacy', host='other.example')[0] == 404
    assert request(port, '/privacy?next=/crm/control/')[0] == (200 if public else 403)
    # This existing exception must still reach the upstream, without real payments.
    assert request(port, '/api/v1/payments/mercadopago/webhook', 'POST')[1] == b'qa-upstream'
    assert request(port, '/api/v1/payments/mercadopago/webhook', 'GET')[0] == 403
    return count + 5, protected


def remove_owned(kind, name, token):
    inspected = json.loads(run(['docker', kind, 'inspect', name]))[0]
    labels = inspected.get('Config', {}).get('Labels', {}) if kind == 'container' else inspected.get('Labels', {})
    if labels.get(LABEL) != token:
        raise RuntimeError('QA resource ownership mismatch')
    run(['docker', kind, 'rm', *(['-f'] if kind == 'container' else []), name])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-ref', required=True)
    args = parser.parse_args()
    baseline = run(['git', '-C', str(ROOT), 'show', args.baseline_ref + ':deploy/vps/docker-compose.yml'])
    candidate = (ROOT / 'deploy/vps/docker-compose.yml').read_text()
    token = uuid.uuid4().hex
    network, web, edge = ('ogt-docs-qa-' + token + suffix for suffix in ('-net', '-web', '-edge'))
    owned = []
    try:
        with tempfile.TemporaryDirectory(prefix='ogt-docs-qa-') as folder:
            folder = Path(folder)
            (folder / 'nginx.conf').write_text((ROOT / 'web/app/nginx.conf').read_text() +
                '\nserver { listen 8080; return 200 "qa-upstream"; }\n')
            # A dedicated bridge is required for Docker Desktop's loopback port.
            # No production network, Docker socket, credentials or data mounts.
            run(['docker', 'network', 'create', '--label', LABEL + '=' + token, network])
            owned.append(('network', network))
            run(['docker', 'run', '-d', '--name', web, '--network', network,
                '--network-alias', 'web', '--network-alias', 'ogt-api', '--label', LABEL + '=' + token,
                '--read-only', '--memory', '64m', '--pids-limit', '64',
                '--tmpfs', '/var/cache/nginx', '--tmpfs', '/var/run', '--tmpfs', '/tmp',
                '-v', str(ROOT / 'web/app') + ':/usr/share/nginx/html:ro',
                '-v', str(folder / 'nginx.conf') + ':/etc/nginx/conf.d/default.conf:ro', 'nginx:1.28-alpine'])
            owned.append(('container', web))
            results = {}
            baseline_protected = None
            for phase, source in (('baseline', baseline), ('candidate', candidate)):
                path = folder / (phase + '.yml')
                path.write_text(json.dumps(configuration(source)))
                run(['docker', 'run', '-d', '--name', edge, '--network', network,
                    '--label', LABEL + '=' + token, '--read-only', '--memory', '128m',
                    '--pids-limit', '128', '--cap-drop', 'ALL', '-p', '127.0.0.1::8080',
                    '-v', str(path) + ':/etc/traefik/dynamic.yml:ro', 'traefik:v3.6.6',
                    '--entrypoints.web.address=:8080', '--providers.file.filename=/etc/traefik/dynamic.yml'])
                owned.append(('container', edge))
                for _ in range(40):
                    inspected = json.loads(run(['docker', 'inspect', edge]))[0]
                    bindings = inspected['NetworkSettings']['Ports'].get('8080/tcp')
                    if bindings:
                        port = bindings[0]['HostPort']
                        break
                    time.sleep(.25)
                else:
                    raise RuntimeError('Isolated loopback binding was not ready')
                for _ in range(40):
                    try:
                        if request(port, '/health')[0] == 403:
                            break
                    except (OSError, urllib.error.URLError):
                        pass
                    time.sleep(.25)
                else:
                    raise RuntimeError('Isolated routing was not ready')
                count, protected = check(port, phase == 'candidate')
                if phase == 'baseline':
                    baseline_protected = protected
                else:
                    assert protected == baseline_protected, 'Protected route behavior changed'
                results[phase] = {'requestsPassed': count}
                remove_owned('container', edge, token)
                owned.remove(('container', edge))
            results['protectedRoutesUnchanged'] = True
            print(json.dumps(results))
    finally:
        for kind, name in reversed(owned):
            remove_owned(kind, name, token)


if __name__ == '__main__':
    main()
