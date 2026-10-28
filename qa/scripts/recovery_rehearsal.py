#!/usr/bin/env python3
"""Rehearse recovery on uniquely labelled QA resources with synthetic data only."""
import argparse
import base64
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import sys
import time
import urllib.request
import uuid

sys.dont_write_bytecode = True
PRODUCTION=Path('/home/iankmp/ogt')


def command(args,**kwargs):
    return subprocess.run(args,capture_output=True,text=True,check=True,timeout=180,**kwargs).stdout.strip()


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--candidate',required=True);parser.add_argument('--baseline',required=True);parser.add_argument('--schema-directory',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);parser.add_argument('--source-root',type=Path,default=PRODUCTION);parser.add_argument('--local',action='store_true');args=parser.parse_args()
    global_root=args.source_root.resolve(strict=True)
    if args.local and sys.platform!='darwin':raise ValueError('LOCAL_MAC_REHEARSAL_ONLY')
    if not args.local and global_root!=PRODUCTION:raise ValueError('WRONG_SOURCE_INSTALLATION')
    sys.path.insert(0,str(global_root/'releases'/args.candidate/'scripts'))
    import restore_ogt_application as restore
    import release_guard
    candidate=global_root/'releases'/args.candidate;baseline=global_root/'releases'/args.baseline
    for folder in (candidate,baseline):restore.package(folder,restore.ID.fullmatch(folder.name)[1])
    original=(global_root/'current-release.json').read_bytes()
    nonce=uuid.uuid4().hex[:12];prefix='ogt-recovery-qa-'+nonce
    home=Path('/tmp').resolve()/prefix;home.mkdir(mode=0o700);root=home/'ogt';root.mkdir(mode=0o700);(root/'releases').mkdir();(root/'backups').mkdir();(root/'media').mkdir();(root/'operations').mkdir()
    (home/'firebase.json').write_text(json.dumps({'emulators':{'auth':{'host':'0.0.0.0','port':9098},'ui':{'enabled':False},'singleProjectMode':True}}))
    (home/'auth-proxy.conf').write_text('events {}\nhttp { server { listen 127.0.0.1:9099; location / { proxy_pass http://auth:9098; } } }\n')
    (root/'.rehearsal.json').write_text(json.dumps({'prefix':prefix}));(root/'.env').write_text('OGT_ENV=development\nOGT_ALLOW_DEV_TOKENS=false\nOGT_FIREBASE_PROJECT_ID=demo-ogt-recovery-qa\nFIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099\n');os.chmod(root/'.env',0o600)
    labels={'traefik.enable':'false','ogt.recovery-rehearsal':nonce}
    image=prefix+'-backend'
    config={'name':prefix,'services':{
      'postgres':{'image':'postgis/postgis:16-3.4','platform':'linux/amd64','container_name':prefix+'-postgres','environment':{'POSTGRES_DB':'onlygoodthings','POSTGRES_USER':'ogt','POSTGRES_PASSWORD':'ogt_qa_recovery_only'},'volumes':['pg:/var/lib/postgresql/data'],'networks':['qa'],'labels':labels,'mem_limit':'256m','cpus':0.25},
      'auth':{'image':'node:22-alpine','container_name':prefix+'-auth','command':['node','/tools/node_modules/firebase-tools/lib/bin/firebase.js','emulators:start','--only','auth','--project','demo-ogt-recovery-qa','--config','/qa/firebase.json'],'environment':{'CI':'true','FIREBASE_CLI_DISABLE_UPDATE_CHECK':'true'},'volumes':[str(Path(__file__).resolve().parents[1]/'node_modules')+':/tools/node_modules:ro',str(home/'firebase.json')+':/qa/firebase.json:ro'],'networks':['qa'],'labels':labels,'mem_limit':'256m','cpus':0.5},
      'auth-proxy':{'image':'nginx:1.28-alpine','container_name':prefix+'-auth-proxy','network_mode':'service:api','volumes':[str(home/'auth-proxy.conf')+':/etc/nginx/nginx.conf:ro'],'labels':labels,'mem_limit':'32m','cpus':0.25},
      'redis':{'image':'redis:7-alpine','container_name':prefix+'-redis','tmpfs':['/data'],'networks':['qa'],'labels':labels,'mem_limit':'64m','cpus':0.25},
      'api':{'image':image+':local','container_name':prefix+'-api','env_file':['.env'],'environment':{'PORT':'8080','OGT_JDBC_URL':'jdbc:postgresql://postgres:5432/onlygoodthings','OGT_DB_USER':'ogt','OGT_DB_PASSWORD':'ogt_qa_recovery_only','OGT_REDIS_URL':'redis://redis:6379','OGT_MEDIA_DIR':'/app/media','OGT_PUBLIC_BASE':'http://127.0.0.1','OGT_MP_MODE':'disabled'},'volumes':[str(root/'media')+':/app/media',str(root/'operations')+':/run/ogt-operations:ro'],'ports':['127.0.0.1::8080'],'networks':{'qa':{'aliases':['ogt-api']}},'labels':labels,'mem_limit':'512m','cpus':0.5},
      'web':{'image':'nginx:1.28-alpine','depends_on':['api'],'container_name':prefix+'-web','volumes':[str(root/'web-app')+':/usr/share/nginx/html:ro',str(root/'web-nginx.conf')+':/etc/nginx/conf.d/default.conf:ro'],'ports':['127.0.0.1::80'],'networks':['qa','front'],'labels':labels,'mem_limit':'64m','cpus':0.25}},
      'networks':{'qa':{'internal':True,'name':prefix},'front':{'name':prefix+'-front'}},'volumes':{'pg':{'name':prefix+'-pg'}}}
    compose=['docker','compose','--project-directory',str(root),'--env-file',str(root/'.env'),'-f',str(root/'docker-compose.yml')]
    # Refuse collisions; cleanup below verifies the unique label before removing anything.
    if command(['docker','ps','-aq','--filter','label=ogt.recovery-rehearsal='+nonce]):raise ValueError('QA_RESOURCE_COLLISION')
    report={'candidateSha':args.candidate[:40],'baselineSha':args.baseline[:40],'isolated':True,'realCredentialsUsed':False,'publicNetwork':False,'authEmulated':True,'backendNetworkInternal':True,'frontendLoopbackOnly':True,'phases':[]}
    created=False
    try:
        for source in (candidate,baseline):
            target=root/'releases'/source.name;shutil.copytree(source,target)
            (target/'docker-compose.yml').write_text(json.dumps(config,indent=2)+'\n')
            nginx=(target/'web-nginx.conf').read_text().replace('  location /api/ {','  location /qa-firebase/ { proxy_pass http://auth:9098/; }\n  location /api/ {');(target/'web-nginx.conf').write_text(nginx)
            lab_manifest=json.loads((target/'release-manifest.json').read_text())
            lab_manifest['files']['docker-compose.yml']=restore.digest(target/'docker-compose.yml')
            lab_manifest['files']['web-nginx.conf']=restore.digest(target/'web-nginx.conf')
            lab_manifest['rehearsalOnly']=True
            (target/'release-manifest.json').write_text(json.dumps(lab_manifest,indent=2)+'\n')
            command(['docker','tag','ogt-backend:'+source.name,image+':'+source.name])
        for name in restore.MANAGED:
            source=root/'releases'/args.candidate/name
            if source.is_dir():shutil.copytree(source,root/name)
            else:shutil.copy2(source,root/name)
        shutil.copy2(root/'releases'/args.candidate/'release-manifest.json',root/'current-release.json')
        command(['docker','tag',image+':'+args.candidate,image+':local'])
        created=True;command(compose+['up','-d','postgres','redis','auth'])
        for attempt in range(60):
            ready=subprocess.run(['docker','exec',prefix+'-postgres','pg_isready','-h','127.0.0.1','-U','ogt','-d','onlygoodthings'],capture_output=True).returncode==0
            if ready:break
            time.sleep(1)
        if not ready:raise ValueError('QA_DATABASE_NOT_READY')
        def sql(text):return command(['docker','exec','-i',prefix+'-postgres','psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','ogt','-d','onlygoodthings'],input=text)
        for migration in sorted(args.schema_directory.glob('*.sql')):
            if migration.name!='14_elpidio_homenaje.sql':
                report['phase']='schema:'+migration.name;sql(migration.read_text())
        owner=str(uuid.uuid4());company=str(uuid.uuid4())
        sql(f"INSERT INTO users(id,firebase_uid,email,display_name,invite_code) VALUES('{owner}','recovery-qa-owner','recovery-qa@example.test','Titular QA','{owner}'); INSERT INTO companies(id,legal_name,verification_status,campaign_balance_cents) VALUES('{company}','Empresa QA aislada','VERIFIED',50000); INSERT INTO csr_ledger(company_id,kind,available_delta_cents,source_key,note) VALUES('{company}','OPENING',50000,'qa-opening','Synthetic QA'); INSERT INTO crm_company_members(company_id,user_id,member_role,status,accepted_at) VALUES('{company}','{owner}','OWNER','ACTIVE',now()); INSERT INTO company_admins(company_id,user_id,is_primary) VALUES('{company}','{owner}',true); INSERT INTO crm_platform_staff(user_id,staff_role,enabled,approved_by,approved_at) VALUES('{owner}','OWNER',true,'{owner}',now());")
        def snapshot():
            (root/'operations'/'status.json').write_text(json.dumps({'schemaVersion':1,'checkedAtEpochSeconds':int(time.time()),'status':'healthy','checks':{'database':{'ok':True,'paymentIncidents':0}},'alerts':[]}))
        report['phase']='candidate-start';snapshot();command(compose+['up','-d','--no-deps','api']);command(compose+['up','-d','--no-deps','auth-proxy']);command(compose+['up','-d','--no-deps','web'])
        def info(target):return {'prefix':prefix,'targetSha':target[:40]}
        restore.verify_running(root,info(args.candidate),json.loads((root/'current-release.json').read_text()))
        port=command(['docker','port',prefix+'-web','80/tcp']).rsplit(':',1)[1]
        signup=urllib.request.Request('http://127.0.0.1:'+port+'/qa-firebase/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key',data=json.dumps({'email':'recovery-qa@example.test','password':'Recovery-QA-only-2468','returnSecureToken':True}).encode(),headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(signup,timeout=30) as response:identity=json.load(response)
        uid=identity['localId'];assert re.fullmatch(r'[A-Za-z0-9_-]{1,128}',uid)
        sql("UPDATE users SET firebase_uid='"+uid+"' WHERE id='"+owner+"'")
        parts=identity['idToken'].split('.');claims=json.loads(base64.urlsafe_b64decode(parts[1]+'='*((-len(parts[1]))%4)));assert claims['aud']=='demo-ogt-recovery-qa'
        claims['email_verified']=True;claims['firebase']['sign_in_second_factor']='totp';parts[1]=base64.urlsafe_b64encode(json.dumps(claims).encode()).decode().rstrip('=');token='.'.join(parts)
        def api(path,data={}):
            port=command(['docker','port',prefix+'-web','80/tcp']).rsplit(':',1)[1]
            req=urllib.request.Request('http://127.0.0.1:'+port+'/api/v1/'+path,data=json.dumps(data).encode(),headers={'Content-Type':'application/json','Authorization':'Bearer '+token})
            with urllib.request.urlopen(req,timeout=30) as response:body=json.load(response)
            if body.get('success') is not True:raise ValueError('QA_API_FAILED')
            return body['data']
        campaign=api('csr/campaigns/save',{'companyId':company,'requestId':str(uuid.uuid4()),'title':'Campaña del ensayo','description':'Datos sintéticos que deben sobrevivir a la recuperación.','socialGoal':0,'budgetCents':10000})['id']
        reserve={'companyId':company,'requestId':str(uuid.uuid4()),'campaignId':campaign,'amountCents':3000}
        api('csr/finance/reserve',reserve)
        def assert_state(available,reserved):
            state=api('csr/finance/summary',{'companyId':company})
            assert state['availableCents']==available and state['reservedCents']==reserved and state['reconciled']
            assert api('csr/team/list',{'companyId':company})['role']=='OWNER'
            assert api('csr/campaigns/manage',{'companyId':company})[0]['id']==campaign
            return {key:state[key] for key in ('availableCents','reservedCents','spentCents','debtCents','reconciled')}
        assert api('crm/platform/operations')['status']=='healthy'
        report['phases'].append({'version':'candidate','state':assert_state(47000,3000)})
        report['phase']='restore-previous';command(['docker','rm','-f',prefix+'-auth-proxy']);target_info=restore.plan(root,args.baseline,args.candidate[:40],lab=True);restore.apply(target_info,'Ensayo aislado de vuelta a la versión anterior');command(compose+['up','-d','--no-deps','auth-proxy'])
        api('csr/finance/reserve',reserve)  # Lost-response retry survives the version switch.
        report['phases'].append({'version':'previous','state':assert_state(47000,3000)})
        api('csr/finance/reserve',{**reserve,'requestId':str(uuid.uuid4()),'amountCents':1000})
        report['phase']='restore-candidate';command(['docker','rm','-f',prefix+'-auth-proxy']);target_info=restore.plan(root,args.candidate,args.baseline[:40],lab=True);restore.apply(target_info,'Ensayo aislado de recuperación de la versión candidata');command(compose+['up','-d','--no-deps','auth-proxy'])
        snapshot();assert api('crm/platform/operations')['status']=='healthy'
        report['phases'].append({'version':'candidate-restored','state':assert_state(46000,4000)})
        assert (global_root/'current-release.json').read_bytes()==original
        report.update(status='passed',phase='completed',productionVersionUnchanged=True,databaseRestored=False,newWritesDuringRollbackPreserved=True,idempotentRetryPreserved=True)
    except Exception as error:
        report['status']='failed';report['errorType']=type(error).__name__
        diagnostic=getattr(error,'stderr','') or str(error)
        for service in ('postgres','api','web','auth','auth-proxy'):
            details=subprocess.run(['docker','logs','--tail','40',prefix+'-'+service],capture_output=True,text=True)
            diagnostic+='\n'+service+'\n'+details.stdout+details.stderr
        details_path=args.output.with_suffix('.diagnostics.log');details_path.write_text(diagnostic);os.chmod(details_path,0o600)
        raise
    finally:
        # No prune/down/volume glob: remove only resources created by this nonce.
        if created:
            for service in ('web','auth-proxy','api','auth','redis','postgres'):
                name=prefix+'-'+service
                result=subprocess.run(['docker','inspect','--format','{{index .Config.Labels "ogt.recovery-rehearsal"}}',name],capture_output=True,text=True)
                if result.returncode==0 and result.stdout.strip()==nonce:command(['docker','rm','-f','-v',name])
            subprocess.run(['docker','network','rm',prefix,prefix+'-front'],capture_output=True)
            subprocess.run(['docker','volume','rm',prefix+'-pg'],capture_output=True)
        for tag in command(['docker','image','ls',image,'--format','{{.Repository}}:{{.Tag}}']).splitlines():
            if tag.startswith(image+':'):subprocess.run(['docker','image','rm','--no-prune',tag],capture_output=True)
        if report.get('status')=='passed':shutil.rmtree(home)
        report['qaContainersRemoved']=not bool(command(['docker','ps','-aq','--filter','label=ogt.recovery-rehearsal='+nonce]))
        args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n');os.chmod(args.output,0o600)
    print(json.dumps(report,indent=2))


if __name__=='__main__':main()
