#!/usr/bin/env python3
"""Exercise candidate Traefik routes in an owned, isolated Docker network."""
import importlib.util
import json
import subprocess
import tempfile
import time
import uuid
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('public_documents',ROOT/'qa/check-public-documents.py')
docs=importlib.util.module_from_spec(spec)
spec.loader.exec_module(docs)

def main():
    source=(ROOT/'deploy/vps/docker-compose.yml').read_text()
    baseline=subprocess.check_output(['git','show','a91ed4472993dbfbd8f3504ff6e841490c65ebb2:deploy/vps/docker-compose.yml'],cwd=ROOT,text=True)
    config=docs.configuration(source)
    assert config['http']['routers']==docs.configuration(baseline)['http']['routers'],'Original routers changed'
    labels=dict(line.strip()[2:].split('=',1) for line in source.splitlines() if line.strip().startswith('- traefik.'))
    for name in ['ogt-store-review','ogt-store-review-db']:
        prefix='traefik.http.routers.'+name+'.'
        config['http']['routers'][name]={'rule':labels[prefix+'rule'],'entryPoints':['web'],'service':'ogt-web','priority':int(labels[prefix+'priority'])}
        if prefix+'middlewares' in labels:
            config['http']['routers'][name]['middlewares']=[value.replace('@docker','') for value in labels[prefix+'middlewares'].split(',')]
    config['http']['middlewares']['ogt-review-auth']={'forwardAuth':{'address':'http://web/internal/store-review/authorize','authRequestHeaders':['Authorization'],'trustForwardHeader':False}}
    config['http']['middlewares']['ogt-review-strip']={'stripPrefix':{'prefixes':['/store-review']}}
    config['http']['middlewares']['ogt-review-db-rate']={'rateLimit':{'average':int(labels['traefik.http.middlewares.ogt-review-db-rate.ratelimit.average']),'burst':int(labels['traefik.http.middlewares.ogt-review-db-rate.ratelimit.burst'])}}
    config['http']['middlewares']['ogt-review-db-connections']={'inFlightReq':{'amount':int(labels['traefik.http.middlewares.ogt-review-db-connections.inflightreq.amount'])}}
    token=uuid.uuid4().hex
    network,web,edge=['ogt-review-qa-'+token+suffix for suffix in ['-net','-web','-edge']]
    owned=[]
    try:
        with tempfile.TemporaryDirectory(prefix='ogt-review-routing-') as directory:
            folder=Path(directory)
            production=(ROOT/'web/app/nginx.conf').read_text()
            auth='''  location = /internal/store-review/authorize {
    proxy_pass http://ogt-api:8080;
    proxy_set_header Authorization $http_authorization;
  }'''
            assert auth in production
            fixture='''  location = /internal/store-review/authorize {
    if ($http_authorization != "Bearer qa-reviewer") { return 401; }
    return 204;
  }'''
            (folder/'nginx.conf').write_text(production.replace(auth,fixture,1)+'\nserver { listen 8080; return 200 "qa-upstream"; }\n')
            (folder/'routes.json').write_text(json.dumps(config))
            docs.run(['docker','network','create','--label',docs.LABEL+'='+token,network]);owned.append(('network',network))
            docs.run(['docker','run','-d','--name',web,'--network',network,'--network-alias','web','--network-alias','ogt-api','--label',docs.LABEL+'='+token,'--read-only','--memory','64m','--pids-limit','64','--tmpfs','/var/cache/nginx','--tmpfs','/var/run','--tmpfs','/tmp','-v',str(ROOT/'web/app')+':/usr/share/nginx/html:ro','-v',str(folder/'nginx.conf')+':/etc/nginx/conf.d/default.conf:ro','nginx:1.28-alpine']);owned.append(('container',web))
            docs.run(['docker','run','-d','--name',edge,'--network',network,'--label',docs.LABEL+'='+token,'--read-only','--memory','128m','--pids-limit','128','--cap-drop','ALL','-p','127.0.0.1::8080','-v',str(folder/'routes.json')+':/etc/traefik/dynamic.yml:ro','traefik:v3.6.6','--entrypoints.web.address=:8080','--providers.file.filename=/etc/traefik/dynamic.yml']);owned.append(('container',edge))
            inspected=json.loads(docs.run(['docker','inspect',edge]))[0]
            port=int(inspected['NetworkSettings']['Ports']['8080/tcp'][0]['HostPort'])
            for attempt in range(40):
                try:
                    if docs.request(port,'/privacy')[0]==200:break
                except OSError:pass
                time.sleep(.25)
            checked=[]
            for path in ['/store-review/api/v1/me/settings','/store-review/media/u/qa.jpg']:
                for headers,expected in [({},401),({'Authorization':'Bearer someone-else'},401),({'X-Forwarded-User':'qa-reviewer','X-Forwarded-For':'192.0.2.1'},401),({'Authorization':'Bearer qa-reviewer'},200)]:
                    status,body,_=docs.request(port,path,headers=headers)
                    assert status==expected,(path,status,expected)
                    if expected==200:assert body==b'qa-upstream'
                    checked.append({'path':path,'status':status})
            for path in ['/','/crm/control/','/crm/empresas/','/api/v1/auth/session','/internal/store-review/authorize','/store-review/crm/control/','/store-review/api/v1/crm/access','/store-review/api/v1/payments/mercadopago/webhook']:
                status=docs.request(port,path,headers={'Authorization':'Bearer qa-reviewer'})[0]
                assert status==403,(path,status)
                checked.append({'path':path,'status':status})
            for path in ['/privacy','/terms','/borrar-cuenta']:
                assert docs.request(port,path)[0]==200
            status,body,_=docs.request(port,'/borrar-cuenta')
            assert b'mailto:romancanoniero@gmail.com' in body
            assert docs.request(port,'/store-review/api/v1/me/settings',host='other.example',headers={'Authorization':'Bearer qa-reviewer'})[0]==404
            result={'checks':checked,'passed':len(checked)+5,'scope':'Isolated proxy/auth fixture; Firebase identity/expiry/WebSocket checks are separate','originalRoutersUnchanged':True}
            out=ROOT/'build/store-readiness/routing.json';out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2)+'\n')
            print(json.dumps(result))
    finally:
        for kind,name in reversed(owned):docs.remove_owned(kind,name,token)

if __name__=='__main__':main()
