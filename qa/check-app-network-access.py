#!/usr/bin/env python3
"""Compare private and opt-in app routing using isolated proxies and an auth fixture."""
import argparse
import importlib.util
import json
import re
import subprocess
import tempfile
import time
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('documents', ROOT / 'qa/check-public-documents.py')
docs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(docs)
SWITCH = '${OGT_PUBLIC_APP_HOST:-disabled.invalid}'


def labels(source):
    return dict(line.strip()[2:].split('=', 1) for line in source.splitlines()
                if line.strip().startswith('- traefik.'))


def configuration(source, enabled):
    values = labels(source)
    config = docs.configuration(source)
    names = {key.split('.')[3] for key in values if key.startswith('traefik.http.routers.')}
    for name in names:
        prefix = 'traefik.http.routers.' + name + '.'
        router = {'rule': values[prefix + 'rule'].replace(SWITCH, 'onlygoodthings.lat' if enabled else 'disabled.invalid'),
                  'entryPoints': ['web'], 'service': 'ogt-web', 'priority': int(values[prefix + 'priority'])}
        if prefix + 'middlewares' in values:
            router['middlewares'] = [value.replace('@docker', '') for value in values[prefix + 'middlewares'].split(',')]
        config['http']['routers'][name] = router
    middlewares = config['http']['middlewares']
    middlewares['ogt-review-auth'] = {'forwardAuth': {'address': 'http://web/internal/store-review/authorize', 'authRequestHeaders': ['Authorization'], 'trustForwardHeader': False}}
    middlewares['ogt-review-strip'] = {'stripPrefix': {'prefixes': ['/store-review']}}
    for name in ('ogt-app-api-rate', 'ogt-app-db-rate', 'ogt-review-db-rate'):
        prefix = 'traefik.http.middlewares.' + name + '.ratelimit.'
        middlewares[name] = {'rateLimit': {'average': int(values[prefix + 'average']), 'burst': int(values[prefix + 'burst'])}}
    middlewares['ogt-review-db-connections'] = {'inFlightReq': {'amount': int(values['traefik.http.middlewares.ogt-review-db-connections.inflightreq.amount'])}}
    return config


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-ref', default='9474f6d')
    args = parser.parse_args()
    source = (ROOT / 'deploy/vps/docker-compose.yml').read_text()
    previous = subprocess.check_output(['git', 'show', args.baseline_ref + ':deploy/vps/docker-compose.yml'], cwd=ROOT, text=True)
    current_labels = labels(source)
    # Existing IP, legal, webhook and expiring review routes must stay identical.
    for key, value in labels(previous).items():
        assert current_labels.get(key) == value, key
    api_rule = current_labels['traefik.http.routers.ogt-app-api.rule']
    namespaces = set(re.search(r'\^/api/v1/\(([^)]+)\)/', api_rule).group(1).split('|'))
    assert not namespaces.intersection({'crm', 'payments', 'internal'})
    implemented = set()
    for path in (ROOT / 'backend/src/main/kotlin').rglob('*.kt'):
        implemented.update(re.findall(r'"/api/v1/([a-z-]+)/', path.read_text()))
    assert implemented - {'crm', 'payments'} <= namespaces, 'An existing app API namespace would be blocked'

    token = uuid.uuid4().hex
    network, web = ['ogt-app-access-qa-' + token + suffix for suffix in ('-net', '-web')]
    owned = []
    checks = []
    try:
        with tempfile.TemporaryDirectory(prefix='ogt-app-access-qa-') as directory:
            folder = Path(directory)
            production = (ROOT / 'web/app/nginx.conf').read_text()
            auth = '''  location = /internal/store-review/authorize {
    proxy_pass http://ogt-api:8080;
    proxy_set_header Authorization $http_authorization;
  }'''
            assert auth in production
            fixture = '''  location = /internal/store-review/authorize {
    if ($http_authorization != "Bearer qa-authorized-fixture") { return 401; }
    return 204;
  }'''
            # This is a routing/auth fixture, never a Firebase token or production API.
            upstream = '''
server {
  listen 8080;
  default_type application/json;
  location = /api/v1/auth/session { return 401 '{"errorCode":"INVALID_TOKEN"}'; }
  location = /api/v1/payments/mercadopago/webhook { return 200 'qa-upstream'; }
  location /api/v1/ {
    if ($http_authorization != "Bearer qa-authorized-fixture") { return 401; }
    return 200 'qa-authenticated-api';
  }
  location = /db { return 401 'qa-socket-auth-required'; }
  location = /media/qa-public.jpg { return 200 'qa-public-media'; }
  location /media/ { return 403 'qa-private-media'; }
  location / { return 200 'qa-upstream'; }
}
'''
            (folder / 'nginx.conf').write_text(production.replace(auth, fixture, 1) + upstream)
            docs.run(['docker', 'network', 'create', '--label', docs.LABEL + '=' + token, network])
            owned.append(('network', network))
            docs.run(['docker', 'run', '-d', '--name', web, '--network', network, '--network-alias', 'web', '--network-alias', 'ogt-api', '--label', docs.LABEL + '=' + token, '--read-only', '--memory', '64m', '--pids-limit', '64', '--tmpfs', '/var/cache/nginx', '--tmpfs', '/var/run', '--tmpfs', '/tmp', '-v', str(ROOT / 'web/app') + ':/usr/share/nginx/html:ro', '-v', str(folder / 'nginx.conf') + ':/etc/nginx/conf.d/default.conf:ro', 'nginx:1.28-alpine'])
            owned.append(('container', web))
            for enabled in (False, True):
                edge = 'ogt-app-access-qa-' + token + ('-public' if enabled else '-private')
                routes = folder / (edge + '.json')
                routes.write_text(json.dumps(configuration(source, enabled)))
                docs.run(['docker', 'run', '-d', '--name', edge, '--network', network, '--label', docs.LABEL + '=' + token, '--read-only', '--memory', '128m', '--pids-limit', '128', '--cap-drop', 'ALL', '-p', '127.0.0.1::8080', '-v', str(routes) + ':/etc/traefik/dynamic.yml:ro', 'traefik:v3.6.6', '--entrypoints.web.address=:8080', '--providers.file.filename=/etc/traefik/dynamic.yml'])
                owned.append(('container', edge))
                inspected = json.loads(docs.run(['docker', 'inspect', edge]))[0]
                port = int(inspected['NetworkSettings']['Ports']['8080/tcp'][0]['HostPort'])
                for _ in range(40):
                    try:
                        if docs.request(port, '/privacy')[0] == 200:
                            break
                    except OSError:
                        pass
                    time.sleep(.25)
                else:
                    raise RuntimeError('Isolated app routing did not start')

                def check(path, expected, method='GET', headers=None, host='onlygoodthings.lat'):
                    status, body, _ = docs.request(port, path, method, host, headers)
                    assert status in expected if isinstance(expected, tuple) else status == expected, (enabled, path, method, status, expected)
                    checks.append({'enabled': enabled, 'path': path, 'method': method, 'status': status})
                    return body

                for namespace in sorted(namespaces - {'auth'}):
                    path = '/api/v1/' + namespace + '/qa'
                    check(path, 401 if enabled else 403, 'POST')
                    body = check(path, 200 if enabled else 403, 'POST', {'Authorization': 'Bearer qa-authorized-fixture'})
                    if enabled:
                        assert body == b'qa-authenticated-api'
                check('/api/v1/auth/session', 401 if enabled else 403, 'POST')
                for path in ('/', '/crm/control/', '/crm/empresas/', '/api/v1/crm/access', '/api/v1/%63rm/access', '/api/v1/auth/../crm/access', '/api/v1/auth/%2e%2e/crm/access', '/internal/store-review/authorize', '/health', '/release.json', '/ws', '/api/v1/unknown/qa'):
                    check(path, (400, 403), headers={'Authorization': 'Bearer qa-authorized-fixture', 'X-Forwarded-For': '192.0.2.1', 'X-Forwarded-User': 'owner'})
                check('/api/v1/crm/access', 403, 'POST')
                check('/api/v1/crm%2Faccess', (400, 403), 'POST')
                for method in ('GET', 'HEAD'):
                    check('/media/qa-public.jpg', 200 if enabled else 403, method)
                assert check('/media/private.jpg', 403) != b'qa-authenticated-api'
                check('/media/qa-public.jpg', 403, 'POST')
                check('/db', 401 if enabled else 403)
                check('/db', 403, 'POST')
                check('/api/v1/payments/mercadopago/webhook', 403, 'GET')
                assert check('/api/v1/payments/mercadopago/webhook', 200, 'POST') == b'qa-upstream'
                check('/store-review/api/v1/me/settings', 401)
                check('/store-review/api/v1/me/settings', 200, headers={'Authorization': 'Bearer qa-authorized-fixture'})
                for host in ('other.example', 'disabled.invalid', 'www.onlygoodthings.lat'):
                    check('/api/v1/auth/session', 403 if host.startswith('www.') else 404, 'POST', host=host)
                for path in ('/privacy', '/terms', '/borrar-cuenta', '/presentacion'):
                    check(path, 200)
            result = {'passed': len(checks), 'checks': checks, 'originalRoutesUnchanged': True, 'productionModified': False, 'scope': 'Isolated Traefik/nginx routing and auth fixture; real Firebase, MFA and account permissions remain backend responsibilities.'}
            out = ROOT / 'build/app-network-access/routing.json'
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(json.dumps(result, indent=2) + '\n')
            print(json.dumps({key: value for key, value in result.items() if key != 'checks'}))
    finally:
        for kind, name in reversed(owned):
            docs.remove_owned(kind, name, token)


if __name__ == '__main__':
    main()
