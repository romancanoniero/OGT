#!/usr/bin/env python3
"""OGT-only read-only probes. Atomic private snapshots; no credentials or messages."""
import argparse
import concurrent.futures
import datetime as dt
import fcntl
import json
import os
from pathlib import Path
import shutil
import socket
import stat
import uuid
import urllib.parse
import ssl
import subprocess
import time
import urllib.error
import urllib.request

VERSION = 1
CONTAINERS = ('ogt-api', 'ogt-web', 'ogt-postgres', 'ogt-redis')
SQL = """BEGIN READ ONLY; SET LOCAL statement_timeout='3000ms';
SELECT json_build_object(
 'paymentsOverdue',(SELECT count(*) FROM csr_payment_jobs WHERE NOT done AND next_attempt_at<now()-interval '5 minutes'),
 'paymentIncidents',(SELECT count(*) FROM csr_payment_incidents WHERE resolved_at IS NULL),
 'erasureOverdue',(SELECT count(*) FROM account_erasure_jobs WHERE completed_at IS NULL AND requested_at<now()-interval '1 hour'),
 'mediaOverdue',(SELECT count(*) FROM crm_erased_media WHERE processed_at IS NULL AND created_at<now()-interval '1 hour')+
                (SELECT count(*) FROM erased_story_media WHERE processed_at IS NULL AND created_at<now()-interval '1 hour'));
COMMIT;"""


def command(args, timeout=8):
    result = subprocess.run(args, capture_output=True, text=True, timeout=timeout, check=True)
    return result.stdout.strip()


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def http(path, body=None):
    # Fixed loopback origin; never follow redirects or acquire a user's session.
    request = urllib.request.Request('http://127.0.0.1:19081'+path, data=body,
                                     headers={'Content-Type': 'application/json'})
    try:
        with urllib.request.build_opener(NoRedirect).open(request, timeout=4) as response:
            return response.status, response.read(65537)
    except urllib.error.HTTPError as error:
        return error.code, b''


def probe_containers():
    rows = [json.loads(line) for line in command(['docker', 'inspect', '--format',
        '{{json .State}}', *CONTAINERS]).splitlines()]
    values = {}
    for name, state in zip(CONTAINERS, rows):
        values[name] = state.get('Running') is True and state.get('Health', {}).get('Status', 'healthy') == 'healthy'
    return {'ok': len(values)==len(CONTAINERS) and all(values.values()), 'services': values}


def probe_application():
    status, body = http('/health')
    data = json.loads(body).get('data', {}) if status==200 else {}
    ready = data.get('status')=='up' and data.get('service')=='ogt-backend'
    payments = data.get('payments')
    return {'ok': ready and payments in ('sandbox','disabled','live') and (payments=='disabled' or data.get('paymentsReady')=='true'),
            'paymentsMode': payments if payments in ('sandbox','disabled','live','unavailable') else 'unknown'}


def probe_authorization():
    statuses = [http('/api/v1/crm/'+path, b'{}')[0] for path in ('access','platform/summary','company/summary','platform/audit-summary')]
    return {'ok': statuses==[401,401,401,401], 'securityViolation':any(200<=s<300 for s in statuses)}


def probe_database():
    output = command(['docker','exec','ogt-postgres','psql','-X','-qAt','-v','ON_ERROR_STOP=1',
                      '-U','ogt','-d','onlygoodthings','-c',SQL])
    data = json.loads(output)
    if set(data)!={'paymentsOverdue','paymentIncidents','erasureOverdue','mediaOverdue'} or any(type(v)!=int or v<0 for v in data.values()):
        raise ValueError('Invalid aggregate response')
    return {'ok': True, **data}


def probe_redis():
    return {'ok': command(['docker','exec','ogt-redis','redis-cli','PING'], timeout=4)=='PONG'}


def probe_release(root):
    manifest = json.loads((root/'current-release.json').read_text())
    sha = manifest.get('sourceSha','')
    status, payload = http('/release.json')
    actual = json.loads(payload) if status==200 else {}
    return {'ok': len(sha)==40 and all(x in '0123456789abcdef' for x in sha) and
            manifest.get('repository')=='romancanoniero/OGT' and
            actual.get('sourceSha')==sha and actual.get('scope')=='app-backend-crms',
            'sourceSha': sha if len(sha)==40 else ''}


def probe_tls():
    with socket.create_connection(('onlygoodthings.lat',443), timeout=5) as connection:
        with ssl.create_default_context().wrap_socket(connection, server_hostname='onlygoodthings.lat') as tls:
            expires = ssl.cert_time_to_seconds(tls.getpeercert()['notAfter'])
    days = int((expires-time.time())//86400)
    return {'ok': days>=14, 'daysRemaining': max(0, days)}


def probe_capacity(root):
    usage = shutil.disk_usage(root)
    percent = round(100*(usage.total-usage.free)/usage.total,1)
    mem = {}
    for line in Path('/proc/meminfo').read_text().splitlines():
        key, _, value = line.partition(':')
        if key in ('MemTotal','MemAvailable'): mem[key]=int(value.split()[0])
    available = round(100*mem['MemAvailable']/mem['MemTotal'],1)
    return {'ok': percent<90 and usage.free>=2*1024**3 and available>=5,
            'diskUsedPercent': percent,'diskFreeGiB': round(usage.free/1024**3,2),'memoryAvailablePercent': available}



def probe_media(root, max_files=250000, seconds=5):
    # Scan only OGT's actual host media mount; never Docker overlay or other apps.
    media=root/'media'
    if media.is_symlink() or not media.is_dir():raise ValueError('Media directory unavailable')
    deadline=time.monotonic()+seconds
    counts={key:0 for key in ('files','mediaBytes','allocatedBytes','uploadImageFiles','uploadImageBytes','uploadVideoFiles','uploadVideoBytes','storyFiles','storyBytes','posterFiles','posterBytes','otherFiles','otherBytes')}
    allocated=set()
    stack=[media]
    while stack:
        if time.monotonic()>deadline: return {'ok':False,'complete':False}
        with os.scandir(stack.pop()) as entries:
            for entry in entries:
                if time.monotonic()>deadline or counts['files']>=max_files:return {'ok':False,'complete':False}
                if entry.name.startswith('.') or entry.is_symlink():continue
                if entry.is_dir(follow_symlinks=False):stack.append(Path(entry.path));continue
                if not entry.is_file(follow_symlinks=False):continue
                try: info=entry.stat(follow_symlinks=False)
                except FileNotFoundError: continue  # Concurrent expiry/deletion; retry on next sample.
                size=info.st_size
                inode=(info.st_dev,info.st_ino)
                if inode not in allocated:
                    counts['allocatedBytes']+=info.st_blocks*512
                    allocated.add(inode)
                counts['files']+=1;counts['mediaBytes']+=size
                relative=Path(entry.path).relative_to(media)
                ext=Path(entry.name).suffix.lower()
                if relative.parts[0]=='u' and ext in ('.jpg','.jpeg','.png','.webp'):category='uploadImage'
                elif relative.parts[0]=='u' and ext in ('.mp4','.webm'):category='uploadVideo'
                elif relative.parts[0]=='stories-private' and entry.name.endswith('-poster.bin'):category='poster'
                elif relative.parts[0]=='stories-private' and ext=='.bin':category='story'
                else:category='other'
                counts[category+'Files']+=1;counts[category+'Bytes']+=size
    usage=shutil.disk_usage(media)
    return {'ok':usage.free>=2*1024**3 and usage.free/usage.total>.1,'complete':True,**counts,
            'diskTotalBytes':usage.total,'diskFreeBytes':usage.free,
            'diskUsedPercent':round(100*(usage.total-usage.free)/usage.total,1)}


def probe_backups(root):
    # .dump and pg_restore --list must both have completed for a deploy snapshot.
    candidates = [p for p in (root/'backups').glob('*/database.dump')
                  if p.is_file() and p.stat().st_size>0 and (p.parent/'database-toc.txt').is_file() and (p.parent/'database-toc.txt').stat().st_size>0
                  and (not p.parent.name.startswith('scheduled-') or (p.parent/'backup.json').is_file())]
    age = int((time.time()-max(p.stat().st_mtime for p in candidates))/3600) if candidates else -1
    return {'ok': age in range(0,49),'ageHours': age}


def safe_probe(probe):
    try:
        result=probe()
        if type(result.get('ok'))!=bool: raise ValueError('Invalid probe')
        return result
    except Exception:
        # Never serialize command stdout/stderr, environment, database rows or URLs.
        return {'ok': False}


def assess(checks, previous, now):
    old = previous.get('failures', {})
    failures = {name: 0 if value.get('ok') else int(old.get(name,0))+1 for name,value in checks.items()}
    alerts = []
    for name, count in failures.items():
        if count and (count>=3 or (name=='authorization' and checks[name].get('securityViolation') is True)):
            alerts.append({'code': name.upper()+'_FAILED','severity':'critical'})
    data=checks.get('database',{})
    for key in ('paymentsOverdue','paymentIncidents','erasureOverdue','mediaOverdue'):
        if data.get(key,0)>0: alerts.append({'code':key.upper(),'severity':'warning'})
    capacity=checks.get('capacity',{})
    if capacity.get('diskUsedPercent',0)>=85:
        alerts.append({'code':'DISK_CAPACITY','severity':'warning'})
    if capacity.get('memoryAvailablePercent',100)<10:
        alerts.append({'code':'MEMORY_CAPACITY','severity':'warning'})
    # A single failed probe is already visible as degraded while debounce avoids
    # repeated incident events for startup/network transients.
    status = 'critical' if any(a['severity']=='critical' for a in alerts) else 'warning' if alerts or any(failures.values()) else 'healthy'
    return {'schemaVersion':VERSION,'checkedAtEpochSeconds':int(now),'status':status,
            'checks':checks,'failures':failures,'alerts':alerts}


def atomic(path, payload):
    path=Path(path)
    if path.is_symlink(): raise ValueError('Snapshot symlink refused')
    temporary=path.with_name(path.name+'.next')
    flags=os.O_WRONLY|os.O_CREAT|os.O_TRUNC|os.O_NOFOLLOW
    fd=os.open(temporary,flags,0o600)
    with os.fdopen(fd,'w') as handle:
        handle.write(payload);handle.flush();os.fsync(handle.fileno())
    os.replace(temporary,path)


def metrics(snapshot):
    lines=['# OGT aggregate operational probes; no user identifiers.']
    for name,value in sorted(snapshot['checks'].items()):
        lines.append('ogt_probe_ok{probe="'+name+'"} '+('1' if value['ok'] else '0'))
    lines.append('ogt_checked_at_seconds '+str(snapshot['checkedAtEpochSeconds']))
    lines.append('ogt_active_alerts '+str(len(snapshot['alerts'])))
    return '\n'.join(lines)+'\n'


def collect(root):
    probes={'containers':probe_containers,'application':probe_application,'authorization':probe_authorization,
            'database':probe_database,'redis':probe_redis,'release':lambda:probe_release(root),
            'tls':probe_tls,'capacity':lambda:probe_capacity(root),'backups':lambda:probe_backups(root),'media':lambda:probe_media(root)}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        work={name:pool.submit(safe_probe,probe) for name,probe in probes.items()}
        return {name:future.result() for name,future in work.items()}


HEARTBEAT_CODES = frozenset(
    [name.upper() + '_FAILED' for name in ('containers','application','authorization','database','redis','release','tls','capacity','backups','media')] +
    ['PAYMENTSOVERDUE','PAYMENTINCIDENTS','ERASUREOVERDUE','MEDIAOVERDUE','DISK_CAPACITY','MEMORY_CAPACITY']
)


def heartbeat_url(raw):
    # This private URL grants only permission to report this check's state.
    # Never follow redirects or accept arbitrary hosts, query strings or paths.
    if not isinstance(raw, str): raise ValueError('Invalid heartbeat destination')
    parsed = urllib.parse.urlsplit(raw)
    if parsed.scheme != 'https' or parsed.netloc != 'hc-ping.com' or parsed.query or parsed.fragment:
        raise ValueError('Invalid heartbeat destination')
    identifier = parsed.path.removeprefix('/')
    if str(uuid.UUID(identifier)) != identifier: raise ValueError('Invalid heartbeat destination')
    return 'https://hc-ping.com/' + identifier


def external_heartbeat(snapshot, config_path, send=None):
    config_path = Path(config_path)
    if not config_path.exists() and not config_path.is_symlink(): return None
    try:
        fd = os.open(config_path, os.O_RDONLY | os.O_NOFOLLOW)
        with os.fdopen(fd) as handle:
            info = os.fstat(handle.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid() or info.st_mode & 0o077 or info.st_size > 4096:
                raise ValueError('Unsafe heartbeat configuration')
            url = heartbeat_url(json.load(handle)['url'])
        failed = bool(snapshot.get('alerts'))
        if snapshot.get('status') != 'healthy' and not failed:
            # Preserve the collector's existing three-sample debounce. A host
            # outage still times out at the external provider without a ping.
            return {'state':'debouncing','delivered':False}
        body = json.dumps({'service':'OGT','status':'failed' if failed else 'healthy',
                           'alerts':sorted({a['code'] for a in snapshot.get('alerts', []) if a.get('code') in HEARTBEAT_CODES})}).encode()
        request = urllib.request.Request(url + ('/fail' if failed else ''), data=body,
                                         headers={'Content-Type':'application/json'}, method='POST')
        if send is None:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=5) as response:
                if response.status != 200: raise ValueError('Heartbeat rejected')
        else:
            if send(request) != 200: raise ValueError('Heartbeat rejected')
        return {'state':'failed' if failed else 'healthy','delivered':True}
    except Exception:
        # Neither the capability URL nor response bodies/exceptions may be logged.
        return {'state':'delivery_failed','delivered':False}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=Path.home()/'ogt');args=parser.parse_args()
    root=args.root.resolve(strict=True)
    if root!=Path('/home/iankmp/ogt') or root.is_symlink():parser.error('Only the existing OGT installation is supported')
    state=root/'operations'
    if state.is_symlink():parser.error('Operational state symlink refused')
    state.mkdir(mode=0o700,exist_ok=True)
    with (state/'collector.lock').open('a') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:return
        try:previous=json.loads((state/'status.json').read_text())
        except (OSError,ValueError):previous={}
        snapshot=assess(collect(root),previous,time.time())
        atomic(state/'status.json',json.dumps(snapshot,indent=2)+'\n')
        atomic(state/'metrics.prom',metrics(snapshot))
        heartbeat = external_heartbeat(snapshot, Path.home()/'ogt-secrets/operations-heartbeat.json')
        if heartbeat is not None:
            try: prior_heartbeat = json.loads((state/'heartbeat.json').read_text())
            except (OSError, ValueError): prior_heartbeat = {}
            atomic(state/'heartbeat.json',json.dumps({**heartbeat,'checkedAtEpochSeconds':int(time.time())})+'\n')
            if heartbeat.get('state') != prior_heartbeat.get('state'):
                print(json.dumps({'event':'ogt_external_heartbeat',**heartbeat}),flush=True)
        if snapshot['alerts']!=previous.get('alerts') or snapshot['status']!=previous.get('status'):
            print(json.dumps({'event':'ogt_operational_state','status':snapshot['status'],'alerts':snapshot['alerts']}),flush=True)
        if snapshot['status']=='critical':raise SystemExit(2)


if __name__=='__main__':main()
