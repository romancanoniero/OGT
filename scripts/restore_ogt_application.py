#!/usr/bin/env python3
"""Reviewable application recovery. No SQL, migrations, environment or volume restore."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.request
import uuid
import sys
sys.dont_write_bytecode = True
from release_guard import validate_env,dotenv

ID=re.compile(r'([a-f0-9]{40})-[0-9]{8}T[0-9]{6}Z')
MANAGED=('backend-dist','web-app','Dockerfile','docker-compose.yml','web-nginx.conf')


def run(args,timeout=30):
    return subprocess.run(args,capture_output=True,text=True,check=True,timeout=timeout).stdout.strip()


def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as source:
        for chunk in iter(lambda:source.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()


def package(path,sha):
    manifest=json.loads((path/'release-manifest.json').read_text())
    if (manifest.get('repository'),manifest.get('scope'),manifest.get('sourceSha'),manifest.get('launchMode'),manifest.get('baseUrl'))!=(
        'romancanoniero/OGT','app-backend-crms',sha,'prelaunch','https://onlygoodthings.lat'):raise ValueError('INVALID_RELEASE_IDENTITY')
    declared=manifest.get('files')
    if not isinstance(declared,dict) or not declared:raise ValueError('EMPTY_RELEASE_MANIFEST')
    actual={}
    for file in path.rglob('*'):
        if file.is_symlink():raise ValueError('RELEASE_SYMLINK_REFUSED')
        if file.is_file() and file.name!='release-manifest.json':actual[file.relative_to(path).as_posix()]=digest(file)
    if declared!=actual:raise ValueError('RELEASE_CONTENTS_CHANGED')
    for name in ('backend-dist/bin/backend','web-app/release.json','web-app/crm/control/index.html','web-app/crm/empresas/index.html','docker-compose.yml','Dockerfile','web-nginx.conf'):
        if name not in declared:raise ValueError('INCOMPLETE_RELEASE')
    return manifest


def check_runtime(root,manifest):
    declared={name:sha for name,sha in manifest['files'].items() if name.split('/')[0] in MANAGED}
    actual={}
    for name in MANAGED:
        item=root/name
        if item.is_symlink():raise ValueError('RUNTIME_SYMLINK_REFUSED')
        files=item.rglob('*') if item.is_dir() else (item,)
        for file in files:
            if file.is_symlink():raise ValueError('RUNTIME_SYMLINK_REFUSED')
            if file.is_file():actual[file.relative_to(root).as_posix()]=digest(file)
    if actual!=declared:raise ValueError('RUNTIME_DRIFT_REQUIRES_REVIEW')


def compose(root,file):
    return json.loads(run(['docker','compose','--project-directory',str(root),'--env-file',str(root/'.env'),'-f',str(file),'config','--format','json']))


def validate_context(root,lab):
    if root.is_symlink():raise ValueError('ROOT_SYMLINK_REFUSED')
    if lab and not re.fullmatch(r'/(?:private/)?tmp/ogt-recovery-qa-[a-z0-9]+/ogt',str(root)):raise ValueError('LAB_ROOT_REFUSED')
    root=root.resolve(strict=True)
    if lab:
        if not re.fullmatch(r'/(?:private/)?tmp/ogt-recovery-qa-[a-z0-9]+/ogt',str(root)):raise ValueError('LAB_ROOT_REFUSED')
        settings=json.loads((root/'.rehearsal.json').read_text());prefix=settings.get('prefix','')
        if not re.fullmatch(r'ogt-recovery-qa-[a-z0-9]+',prefix):raise ValueError('LAB_PREFIX_REFUSED')
        values=dotenv(root/'.env')
        if values.get('OGT_ENV')!='development' or values.get('OGT_ALLOW_DEV_TOKENS')!='false' or values.get('OGT_FIREBASE_PROJECT_ID')!='demo-ogt-recovery-qa' or values.get('FIREBASE_AUTH_EMULATOR_HOST')!='127.0.0.1:9099':raise ValueError('LAB_AUTH_REFUSED')
        if any(key in values for key in ('OGT_FIREBASE_CREDENTIALS','GOOGLE_APPLICATION_CREDENTIALS','OGT_MP_ACCESS_TOKEN_FILE')):raise ValueError('LAB_CREDENTIALS_REFUSED')
        return root,prefix,prefix+'-backend'
    if root!=Path('/home/iankmp/ogt'):raise ValueError('WRONG_INSTALLATION')
    validate_env(dotenv(root/'.env'))
    return root,'ogt','ogt-backend'


def safe_configuration(current,target,prefix):
    for key in ('volumes','networks'):
        if current.get(key)!=target.get(key):raise ValueError('PERSISTENT_RESOURCES_CHANGED')
    for service in ('postgres','redis'):
        if current['services'][service]!=target['services'][service]:raise ValueError('DATA_SERVICES_CHANGED')
    for service in ('api','web'):
        a=current['services'][service];b=target['services'][service]
        if b.get('container_name')!=prefix+'-'+service:raise ValueError('CONTAINER_SCOPE_REFUSED')
        if a.get('environment')!=b.get('environment'):raise ValueError('ENVIRONMENT_CHANGE_REFUSED')
        if a.get('ports')!=b.get('ports') or not b.get('ports') or any(p.get('host_ip')!='127.0.0.1' for p in b['ports']):raise ValueError('PORT_EXPOSURE_REFUSED')
        if a.get('labels')!=b.get('labels'):raise ValueError('ACCESS_POLICY_CHANGE_REFUSED')
        mounts=lambda row:[v for v in row.get('volumes',[]) if v.get('target')!='/run/ogt-operations']
        if mounts(a)!=mounts(b):raise ValueError('MOUNT_CHANGE_REFUSED')
    if prefix=='ogt' and target['services']['web'].get('labels',{}).get('traefik.http.routers.ogt-web.middlewares')!='ogt-prelaunch@docker':raise ValueError('PRIVATE_ACCESS_REQUIRED')


def plan(root,target_id,expected,lab=False):
    root,prefix,image=validate_context(root,lab)
    match=ID.fullmatch(target_id)
    if not match or not re.fullmatch(r'[a-f0-9]{40}',expected):raise ValueError('FULL_RELEASE_IDS_REQUIRED')
    current=json.loads((root/'current-release.json').read_text())
    if current.get('sourceSha')!=expected:raise ValueError('CURRENT_VERSION_CHANGED')
    target_path=root/'releases'/target_id
    target=package(target_path,match[1]);check_runtime(root,current)
    # Additive schemas are retained, never reversed. Older binaries must carry
    # the exact same migration contracts before they can be restored.
    schema=lambda m:{k:v for k,v in m['files'].items() if k.startswith('database/migrations/')}
    if not schema(current) or schema(current)!=schema(target):raise ValueError('SCHEMA_COMPATIBILITY_REQUIRES_REVIEW')
    safe_configuration(compose(root,root/'docker-compose.yml'),compose(root,target_path/'docker-compose.yml'),prefix)
    run(['docker','image','inspect',image+':'+target_id])
    for service in ('postgres','redis'):
        if run(['docker','inspect','--format','{{.State.Running}}',prefix+'-'+service])!='true':raise ValueError('DATA_SERVICE_NOT_RUNNING')
    return {'root':str(root),'currentSha':expected,'targetSha':match[1],'targetReleaseId':target_id,'prefix':prefix,'image':image,
            'managedPaths':list(MANAGED),'databaseRestored':False,'environmentChanged':False,'mediaChanged':False,'lab':lab}


def response(port,path,body=None):
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self,*args):return None
    request=urllib.request.Request('http://127.0.0.1:'+port+path,data=body,headers={'Content-Type':'application/json'})
    try:
        with urllib.request.build_opener(NoRedirect).open(request,timeout=4) as reply:return reply.status,reply.read(65537)
    except urllib.error.HTTPError as error:return error.code,b''


def verify_running(root,info,target):
    port=run(['docker','port',info['prefix']+'-web','80/tcp'])
    if not re.fullmatch(r'127\.0\.0\.1:[0-9]{1,5}',port):raise ValueError('RESTORED_PORT_NOT_PRIVATE')
    port=port.rsplit(':',1)[1]
    for attempt in range(75):
        try:
            code,body=response(port,'/health')
            if code==200 and json.loads(body).get('data',{}).get('status')=='up':break
        except Exception:pass
        if attempt==74:raise ValueError('RESTORED_APPLICATION_NOT_HEALTHY')
        time.sleep(2)
    code,body=response(port,'/release.json')
    if code!=200 or json.loads(body).get('sourceSha')!=info['targetSha']:raise ValueError('RESTORED_VERSION_MISMATCH')
    for name in ('control','empresas'):
        code,body=response(port,'/crm/'+name+'/')
        if code!=200 or b'id="crm-root"' not in body:raise ValueError('RESTORED_CRM_UNAVAILABLE')
    for name in ('access','platform/summary','company/summary'):
        if response(port,'/api/v1/crm/'+name,b'{}')[0]!=401:raise ValueError('RESTORED_AUTHORIZATION_FAILED')
    check_runtime(root,target)


def apply(info,reason):
    if not 10<=len(reason.strip())<=500:raise ValueError('REVIEW_REASON_REQUIRED')
    root=Path(info['root']);target_path=root/'releases'/info['targetReleaseId']
    target=package(target_path,info['targetSha'])
    stamp=uuid.uuid4().hex;backup=root/'backups'/('application-recovery-'+stamp);backup.mkdir(mode=0o700)
    stage=root/('.application-recovery-'+stamp);stage.mkdir(mode=0o700)
    journal={**info,'reason':reason.strip(),'phase':'prepared','moved':[]}
    def save():
        path=backup/'recovery.json';path.write_text(json.dumps(journal,indent=2)+'\n');os.chmod(path,0o600)
    save()
    # All copying completes before stopping anything; a disk/copy failure leaves
    # the current application and its data services running unchanged.
    for name in MANAGED:
        source=target_path/name
        if source.is_dir():shutil.copytree(source,stage/name)
        else:shutil.copy2(source,stage/name)
    check_runtime(stage,target)
    old_image=run(['docker','inspect','--format','{{.Image}}',info['prefix']+'-api'])
    (backup/'previous-image.txt').write_text(old_image+'\n')
    run(['docker','tag',old_image,info['image']+':before-recovery-'+stamp])
    try:
        journal['phase']='switching';save()
        run(['docker','stop',info['prefix']+'-web',info['prefix']+'-api'],timeout=60)
        for name in MANAGED:
            os.rename(root/name,backup/name);os.rename(stage/name,root/name);journal['moved'].append(name);save()
        run(['docker','tag',info['image']+':'+info['targetReleaseId'],info['image']+':local'])
        run(['docker','compose','--project-directory',str(root),'--env-file',str(root/'.env'),'-f',str(root/'docker-compose.yml'),
             'up','-d','--no-deps','--force-recreate','api','web'],timeout=150)
        verify_running(root,info,target)
        shutil.copy2(root/'current-release.json',backup/'previous-release.json')
        shutil.copy2(target_path/'release-manifest.json',root/'current-release.json')
        journal['phase']='verified';save();stage.rmdir()
    except Exception:
        # Keep the tested recovery material and fail closed; never restore a DB
        # snapshot over newer user data in response to an application failure.
        subprocess.run(['docker','stop',info['prefix']+'-web',info['prefix']+'-api'],capture_output=True,timeout=60)
        journal['phase']='failed-services-stopped';save();raise
    return {'status':'verified','targetSha':info['targetSha'],'audit':str(backup/'recovery.json'),'databaseRestored':False}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--root',type=Path,default=Path('/home/iankmp/ogt'))
    parser.add_argument('--target-release',required=True);parser.add_argument('--expected-current-sha',required=True)
    parser.add_argument('--apply',action='store_true');parser.add_argument('--reason',default='');parser.add_argument('--lab',action='store_true')
    args=parser.parse_args()
    # Serializes with the existing release activator, including a read-only plan.
    import fcntl
    try:
        root,prefix,image=validate_context(args.root,args.lab)
        with (root/'.release.lock').open('a') as lock:
            fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            info=plan(root,args.target_release,args.expected_current_sha,args.lab)
            print(json.dumps(apply(info,args.reason) if args.apply else info,indent=2))
    except Exception as error:
        code=str(error) if isinstance(error,ValueError) and re.fullmatch(r'[A-Z_]+',str(error)) else 'RECOVERY_FAILED_REVIEW_PRIVATE_AUDIT'
        parser.exit(1,code+'\n')


if __name__=='__main__':main()
