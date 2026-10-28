#!/usr/bin/env python3
"""Configure only OGT's Apple provider after reviewing the Apple key registration.

Private keys stay outside the repository. No key, token, or provider response body
is printed. Native bundle IDs, other providers, MFA and users remain unchanged.
"""
import argparse
import json
from pathlib import Path
import re
import sys
from urllib import request,error,parse

PROJECT='goodthings-55612'
TEAM='WER3D825VB'
SERVICE='com.onlygoodthings.app.web'
BUNDLE='com.onlygoodthings.app'
RESOURCE=f'projects/{PROJECT}/defaultSupportedIdpConfigs/apple.com'
API='https://identitytoolkit.googleapis.com/admin/v2/'+RESOURCE
MASK='clientId,appleSignInConfig.codeFlowConfig'


def prepare_patch(current,service_id,key_id,pem):
    if current.get('name') not in (RESOURCE,'projects/521063054927/defaultSupportedIdpConfigs/apple.com') or not current.get('enabled'):
        raise ValueError('Apple provider identity or enabled state changed; review before applying')
    if service_id!=SERVICE or not re.fullmatch('[A-Z0-9]{10}',key_id):
        raise ValueError('Unexpected Services ID or key ID')
    if current.get('clientId') not in (None,'',SERVICE):
        raise ValueError('Existing Services ID differs; no replacement performed')
    apple=current.get('appleSignInConfig',{})
    if BUNDLE not in apple.get('bundleIds',[]):
        raise ValueError('Canonical native bundle ID is not present')
    code=apple.get('codeFlowConfig',{})
    if code.get('teamId') not in (None,'',TEAM):
        raise ValueError('Existing Apple team differs')
    if code.get('keyId') not in (None,'',key_id):
        raise ValueError('Existing key differs; explicit rotation review required')
    from cryptography.hazmat.primitives.serialization import load_pem_private_key
    from cryptography.hazmat.primitives.asymmetric.ec import EllipticCurvePrivateKey,SECP256R1
    key=load_pem_private_key(pem.encode(),password=None)
    if not isinstance(key,EllipticCurvePrivateKey) or not isinstance(key.curve,SECP256R1):
        raise ValueError('Expected an Apple P256 signing key')
    return {'clientId':SERVICE,'appleSignInConfig':{'codeFlowConfig':{'teamId':TEAM,'keyId':key_id,'privateKey':pem}}}


def configured_facts(config):
    apple=config.get('appleSignInConfig',{});code=apple.get('codeFlowConfig',{})
    return {'enabled':config.get('enabled',False),'serviceId':config.get('clientId'),
            'teamId':code.get('teamId'),'keyId':code.get('keyId'),
            'nativeBundlePreserved':BUNDLE in apple.get('bundleIds',[])}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--credentials',required=True)
    parser.add_argument('--key',required=True);parser.add_argument('--key-id',required=True)
    parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    try:
        key_path=Path(args.key).resolve();repo=Path(__file__).resolve().parents[1]
        if repo==key_path or repo in key_path.parents:raise ValueError('Private key must remain outside Git repository')
        if key_path.name!=f'AuthKey_{args.key_id}.p8':raise ValueError('Key filename must match reviewed key ID')
        if key_path.stat().st_mode & 0o077:raise ValueError('Private key permissions must be 0600')
        from google.oauth2 import service_account
        from google.auth.transport.requests import Request
        creds=service_account.Credentials.from_service_account_file(args.credentials,scopes=['https://www.googleapis.com/auth/cloud-platform'])
        if creds.project_id!=PROJECT:raise ValueError('Credentials belong to a different project')
        creds.refresh(Request())
        def call(method='GET',body=None):
            url=API+('?'+parse.urlencode({'updateMask':MASK}) if method=='PATCH' else '')
            req=request.Request(url,data=json.dumps(body).encode() if body else None,headers={'Authorization':'Bearer '+creds.token,'Content-Type':'application/json'},method=method)
            try:
                with request.urlopen(req,timeout=25) as result:return json.load(result)
            except error.HTTPError as exc:raise ValueError(f'Provider configuration HTTP {exc.code}') from None
        before=call();patch=prepare_patch(before,SERVICE,args.key_id,key_path.read_text())
        if not args.apply:print(json.dumps({'mode':'review','before':configured_facts(before),'plannedServiceId':SERVICE,'plannedKeyId':args.key_id}));return 0
        call('PATCH',patch);after=call();facts=configured_facts(after)
        if facts!={'enabled':True,'serviceId':SERVICE,'teamId':TEAM,'keyId':args.key_id,'nativeBundlePreserved':True}:raise ValueError('Post-configuration verification failed')
        print(json.dumps({'mode':'applied','verified':facts}));return 0
    except Exception as exc:
        print('Apple provider configuration blocked: '+(str(exc) if isinstance(exc,ValueError) else type(exc).__name__),file=sys.stderr);return 2
if __name__=='__main__':sys.exit(main())
