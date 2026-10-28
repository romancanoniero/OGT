"""Browser regression checks with mock identity/API, never production credentials."""
import json
import os
import re
from urllib.parse import urlsplit
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
ROOT = Path(__file__).resolve().parents[1]
APP = """const apps=[];export const getApps=()=>apps;export function initializeApp(c,n){const a={name:n};apps.push(a);return a;}"""
AUTH = """
const auth={currentUser:null},listeners=[];
export const browserSessionPersistence='session';
export const getAuth=()=>auth;
export const setPersistence=async()=>{};
export function onAuthStateChanged(a,cb){listeners.push(cb);queueMicrotask(cb);return()=>{};}
export const getIdToken=async()=> 'fixture-token-'+(window.testTokenVersion||1);
export const signInWithEmailAndPassword=async()=>{if(window.testEnroll){auth.currentUser={uid:'fixture',email:'fixture@example.test',emailVerified:true,providerData:[],enrolledFactors:[]};listeners.forEach(f=>f());return {user:auth.currentUser};}throw {code:'auth/multi-factor-auth-required'};};
export class GoogleAuthProvider{addScope(){}setCustomParameters(parameters){if(parameters.prompt!=='select_account')throw Error('Google must offer account selection');}}
export const signInWithPopup=async()=>{throw {code:'auth/multi-factor-auth-required'};};
export const createUserWithEmailAndPassword=async(a,e,p)=>{if(window.testRegistrationError)throw {code:window.testRegistrationError};auth.currentUser={uid:'fixture',email:e,emailVerified:false,providerData:[],enrolledFactors:[]};listeners.forEach(f=>f());return {user:auth.currentUser};};
export const multiFactor=user=>({enrolledFactors:user.enrolledFactors||[],getSession:async()=> 'new-mfa-session',enroll:async()=>{user.enrolledFactors=[{factorId:'totp'}];}});
export const sendEmailVerification=async()=>{};
export const reload=async()=>{};

export function getMultiFactorResolver(){return {session:'mfa',hints:[{uid:'factor',factorId:window.testFactor||'totp',displayName:'Factor de prueba'}],resolveSignIn:async a=>{if(window.testChallengeExpired){window.testChallengeExpired=false;throw {code:'auth/totp-challenge-timeout'};}if(a.code!=='123456')throw {code:'auth/invalid-verification-code'};auth.currentUser=window.testStale?{uid:'fixture',email:'fixture@example.test',emailVerified:true,providerData:[],enrolledFactors:[{factorId:'totp'}]}:{uid:'fixture'};listeners.forEach(f=>f());return {user:auth.currentUser};}};}
export const TotpMultiFactorGenerator={assertionForSignIn:(uid,code)=>({code}),generateSecret:async()=>({secretKey:'TEST-DO-NOT-USE'}),assertionForEnrollment:(secret,code)=>({secret,code})};
export class PhoneAuthProvider{async verifyPhoneNumber(){return 'verification';}static credential(id,code){return {code};}}
export const PhoneMultiFactorGenerator={assertion:x=>x};
export class RecaptchaVerifier{clear(){}}
export async function signOut(){auth.currentUser=null;listeners.forEach(f=>f());}
"""
A='11111111-1111-4111-8111-111111111111'
B='22222222-2222-4222-8222-222222222222'
# Serve fixtures directly from memory; no network or localhost listener.
base='https://ogt-crm.test'
with sync_playwright() as pw:
    executable=os.environ.get('CRM_CHROMIUM_PATH')
    browser=pw.chromium.launch(headless=True,**({'executable_path':executable} if executable else {}))
    def session(mode='control',factor='totp',enroll=False):
        context=browser.new_context()
        context.add_init_script("window.testFactor="+json.dumps(factor)+";window.testEnroll="+json.dumps(enroll)+"; const original=window.fetch.bind(window); window.fetch=async (url,opts)=>{if(window.testDelay&&String(url).endsWith('/summary'))await new Promise(r=>setTimeout(r,400));return original(url,opts);};")
        state={'deny':False,'mismatch':False,'enroll':enroll,'requests':[],'avatar_version':1,'blocked_ids':{'00000003-1111-4111-8111-111111111111'},'deleted_ids':set()}
        def route(route):
            url=route.request.url
            if urlsplit(url).path.startswith('/test-avatar/'):
                version=urlsplit(url).path.rsplit('/',1)[-1]
                if version == 'broken':
                    route.fulfill(status=404,body='Image not found');return
                route.fulfill(status=200,content_type='image/svg+xml',body='<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40"><circle cx="20" cy="20" r="20" fill="teal"/></svg>');return
            if 'gstatic.com' in url:
                content='' if '-compat.js' in url else APP if 'firebase-app.js' in url else AUTH
                route.fulfill(status=200,content_type='text/javascript',headers={'Access-Control-Allow-Origin':'*'},body=content);return
            if urlsplit(url).path == '/google-logo.svg':
                logo = (ROOT / 'web/app/google-logo.svg').read_text()
                route.fulfill(status=200,content_type='image/svg+xml',body=logo)
                return
            if urlsplit(url).path == '/story-frame.js':
                route.fulfill(status=200,content_type='text/javascript',body=(ROOT/'web/app/story-frame.js').read_text())
                return
            if '/api/v1/crm/' in url:
                body=route.request.post_data_json or {}
                state['requests'].append((url,body))
                if state.get('expected_token') and route.request.headers.get('authorization') != 'Bearer '+state['expected_token']:
                    route.fulfill(status=401,content_type='text/plain',body='Unauthorized');return
                if state['enroll'] or state.get('stale'):
                    route.fulfill(status=403,content_type='application/json',body=json.dumps({'success':False,'errorCode':'STEP_UP_REQUIRED','message':'Segundo factor obligatorio'}));return
                if state['deny']:
                    route.fulfill(status=403,content_type='application/json',body=json.dumps({'success':False,'errorCode':'FORBIDDEN','message':'Acceso revocado'}));return
                import time
                if url.endswith('/access'):
                    data={'platformRole':state.get('platform_role','OWNER'),'companies':[{'companyId':A,'role':'ADMIN'},{'companyId':B,'role':'VIEWER'}],'stepUpValidUntilEpochSeconds':int(time.time())+900}
                elif url.endswith('/posts/search') and state.get('post_fixture'):
                    start=21 if body.get('cursor') else 1
                    posts=[{
                        'id':f'{n:08d}-1111-4111-8111-111111111111',
                        'status':'VISIBLE',
                        'authorName':'Ana Torres',
                        'body':f'Publicaci\u00f3n de prueba {n}',
                        'animalTitle':'Luna' if n==1 else None,
                        'createdAt':'2026-10-24T10:00:00Z',
                        'topic':'adopci\u00f3n',
                        'imageUrl':None,
                    } for n in range(start,26 if start==21 else 21)]
                    data={'posts':posts,'nextCursor':'mock-cursor' if start==1 else None}
                elif url.endswith('/users/detail') and state.get('user_fixture'):
                    data={
                        'id':body['userId'],'displayName':'Usuario de prueba 01',
                        'photoUrl':'/test-avatar/'+str(state['avatar_version']),'status':'ACTIVE','role':'USER',
                        'createdAt':'2026-10-24T12:00:00Z','lastSeenAt':None,
                        'communityPoints':7,'visiblePostCount':2,'receivedReportCount':0,
                        'reportedContent':[],'canReviewReports':True,'conversationsRestricted':True,
                    }
                elif url.endswith('/users/moderate') and state.get('user_fixture'):
                    action=body.get('action')
                    users=body.get('userIds',[])
                    if action=='BLOCK':
                        state['blocked_ids'].update(users)
                    elif action=='UNBLOCK':
                        state['blocked_ids'].difference_update(users)
                    elif action=='DELETE':
                        state['deleted_ids'].update(users)
                    data={'action':action,'ids':users}
                elif url.endswith('/users/posts') and state.get('user_fixture'):
                    data={'posts':[{'id':'12345678-1111-4111-8111-111111111111','body':'Historia de adopci\u00f3n','topic':'Adopci\u00f3n','createdAt':'2026-10-24T10:00:00Z'}], 'nextCursor':None}
                elif url.endswith('/users/list') and state.get('user_fixture'):
                    index=21 if body.get('cursor') else 1
                    values=[{
                        'id':f'{n:08d}-1111-4111-8111-111111111111',
                        'displayName':f'Usuario de prueba {n:02d}',
                        'photoUrl':('/test-avatar/'+str(state['avatar_version'])) if n==1 else ('/test-avatar/broken' if n==2 else None),
                        'status':'ACTIVE' if n%2 else 'SUSPENDED',
                        'canPublish':n%2!=0 and f'{n:08d}-1111-4111-8111-111111111111' not in state['blocked_ids'],
                        'role':'USER' if n%3 else 'COMMUNITY_MODERATOR',
                        'createdAt':'2026-10-24T12:00:00Z',
                        'lastSeenAt':None,
                    } for n in range(index,26 if index==21 else 21) if f'{n:08d}-1111-4111-8111-111111111111' not in state['deleted_ids']]
                    data={'users':values,'nextCursor':'users-fixture-cursor' if index==1 else None}
                elif url.endswith('/platform/operations'):
                    data={'schemaVersion':1,'checkedAtEpochSeconds':int(time.time()),'status':'healthy','stale':False,'checks':{},'alerts':[]}
                elif url.endswith('/company/summary'):
                    cid=body['companyId']
                    data={'companyId': B if state['mismatch'] else cid,'tradeName':'Empresa Uno' if cid==A else 'Empresa Dos','verificationStatus':'VERIFIED','campaigns':2,'activeCampaigns':1,'issuedCoupons':3,'balanceCents':0,'impactScore':10}
                else:data={'users':17,'companies':2,'pendingCompanies':1,'openReports':0,'visiblePosts':12}
                route.fulfill(status=200,content_type='application/json',body=json.dumps({'success':True,'data':data}));return
            if url.startswith(base + '/'):
                relative=urlsplit(url).path.lstrip('/')
                if not relative or relative.endswith('/'): relative+='index.html'
                file=(ROOT/'web/app/crm'/relative).resolve()
                if file.is_relative_to((ROOT/'web/app/crm').resolve()) and file.is_file():
                    mime={'.js':'text/javascript','.mjs':'text/javascript','.html':'text/html','.css':'text/css','.png':'image/png','.svg':'image/svg+xml','.json':'application/json'}.get(file.suffix,'application/octet-stream')
                    route.fulfill(status=200,content_type=mime,body=file.read_bytes());return
                route.fulfill(status=404,body='Not found');return
            route.abort()
        context.route('**/*',route)
        page=context.new_page();errors=[];page.on('pageerror',lambda error: errors.append(str(error)))
        page.goto(base+'/'+mode+'/');expect(page.locator('#crm-login')).to_be_visible()
        return context,page,state,errors
    def begin(page):
        page.locator('#email').fill('test@example.test');page.locator('#password').fill('fixture-password')
        page.get_by_role('button',name='Ingresar',exact=True).click();expect(page.locator('#crm-mfa')).to_be_visible()
        page.get_by_role('button',name='Usar este factor',exact=True).click();expect(page.get_by_role('button',name='Verificar e ingresar',exact=True)).to_be_enabled()
    def finish(page,code='123456'):
        page.locator('#crm-code').fill(code);page.get_by_role('button',name='Verificar e ingresar',exact=True).click()
    results=[]
    context,page,state,errors=session('control')
    state['post_fixture']=True
    begin(page);finish(page)
    expect(page.locator('#crm-root .crm-workspace')).to_be_visible()
    # Model Firebase's automatic ID-token rotation while the MFA window stays
    # valid. Navigation must ask Firebase for its current credential, not reuse
    # the string captured when the workspace first opened.
    page.evaluate('window.testTokenVersion=2')
    state['expected_token']='fixture-token-2'
    page.locator('.crm-nav-btn[data-section="publicaciones"]').click()
    expect(page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')).to_have_count(20)
    expect(page.locator('#crm-root .crm-workspace')).to_be_visible()
    assert not errors,errors
    context.close()
    results.append('Open Control workspace uses refreshed Firebase token without discarding navigation or extending MFA')
    # Navigate every currently implemented CRM workspace under an authorized
    # simulated session. The separate tenant and MFA checks below remain in force.
    for mode, sections in [
        ('control', ['resumen', 'usuarios', 'empresas', 'denuncias', 'publicaciones']),
        ('empresas', ['resumen', 'campanas', 'cupones', 'impacto']),
    ]:
        context,page,state,errors=session(mode);begin(page);finish(page)
        expect(page.locator('#crm-root .crm-workspace')).to_be_visible()
        for section in sections:
            button=page.locator('.crm-nav-btn[data-section="'+section+'"]')
            expect(button).to_be_visible()
            button.click()
            expect(button).to_have_attribute('aria-current','page')
            assert page.locator('.crm-nav-btn[aria-current="page"]').count()==1
        if mode == 'control':
            user_icon=page.locator('.crm-nav-btn[data-section="usuarios"] img')
            expect(user_icon).to_have_attribute('src','../icons/qs_user.svg')
            page.wait_for_function("""() => {
              const img=document.querySelector('.crm-nav-btn[data-section="usuarios"] img');
              return img && img.complete && img.naturalWidth > 0;
            }""")
        assert not errors,errors
        context.close()
        results.append(mode+' full navigation: '+', '.join(sections))
    for role in ['OWNER','OPERATIONS','SUPPORT','ANALYST']:
        context,page,state,errors=session('control');state['platform_role']=role
        begin(page);finish(page)
        expect(page.locator('#crm-root .crm-workspace')).to_be_visible()
        section=page.locator('.crm-nav-btn[data-section="operacion"]')
        if role in ['OWNER','OPERATIONS']:
            expect(section).to_be_visible();section.click()
            expect(page.get_by_role('heading',name='Servicio saludable',exact=True)).to_be_visible()
        else:expect(section).to_have_count(0)
        assert not errors,errors
        context.close();results.append('Operational status role boundary: '+role)
    # Filters, order and pagination update rows without rebuilding the CRM.
    context,page,state,errors=session('control')
    state['user_fixture']=True
    begin(page);finish(page)
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    expect(page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')).to_have_count(20)
    # Profile-image E2E: populated, missing and invalid photos, styling,
    # updated URL after server refresh, and stable navigation/toolbar DOM.
    avatar_rows=page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')
    avatar=avatar_rows.first.locator('.crm-list-user-avatar')
    expect(avatar).to_have_attribute('src','/test-avatar/1')
    page.wait_for_function("""() => {
      const img=document.querySelector('.crm-list-user-avatar');
      return img && img.complete && img.naturalWidth > 0;
    }""")
    expect(avatar).to_have_css('border-radius', '50%')
    expect(avatar).not_to_have_css('box-shadow', 'none')
    assert avatar.evaluate("img => img.previousElementSibling?.type === 'checkbox'"), 'Avatar must follow the row selector'
    expect(avatar_rows.nth(1).locator('.crm-list-user-avatar')).to_have_attribute('src', re.compile(r'avatar_anonymous\.png$'))
    expect(avatar_rows.nth(2).locator('.crm-list-user-avatar')).to_have_attribute('src', re.compile(r'avatar_anonymous\.png$'))
    assert page.locator('.crm-query-popover').is_hidden()
    page.get_by_role('button',name='Cargar 20 m\u00e1s').click()
    expect(page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')).to_have_count(25)
    page.get_by_role('button',name='Filtros (0)').click()
    page.get_by_label('Activo',exact=True).check()
    expect(page.locator('.crm-query-chip')).to_contain_text(['Estado: Activo','Orden:'])
    expect(page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')).to_have_count(20)
    assert any(item[1].get('filters',{}).get('statuses')==['ACTIVE'] for item in state['requests'] if item[0].endswith('/users/list'))
    # Change the server fixture BEFORE requesting fresh rows. Do not weaken the
    # version-2 assertion to version 1: list and detail must both observe a change.
    page.evaluate("""() => {
      window.__userAvatarNodes={nav:document.querySelector('.crm-nav'),
        toolbar:document.querySelector('.crm-tools')};
    }""")
    state['avatar_version']=2
    page.get_by_role('button',name='Orden (1)').click()
    page.get_by_role('button',name='+ Nombre').click()
    expect(avatar_rows.first.locator('.crm-list-user-avatar')).to_have_attribute('src','/test-avatar/2')
    assert any(len(item[1].get('orders',[]))==2 for item in state['requests'] if item[0].endswith('/users/list'))
    assert page.evaluate("""() => {
      const s=window.__userAvatarNodes;
      return s.nav===document.querySelector('.crm-nav') &&
        s.toolbar===document.querySelector('.crm-tools');
    }"""), 'Avatar updates must not rebuild navigation or toolbar'
    page.locator('.crm-nav-btn[data-section="publicaciones"]').click()
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    expect(page.locator('.crm-query-chip')).to_contain_text(['Estado: Activo','Orden:'])
    expect(avatar_rows.first.locator('.crm-list-user-avatar')).to_have_attribute('src','/test-avatar/2')
    page.get_by_role('button',name='Ver usuario').first.click()
    expect(page.locator('.crm-dialog')).to_contain_text('Usuario de prueba')
    expect(page.locator('.crm-profile-hero')).to_be_visible()
    expect(page.locator('.crm-profile-avatar')).to_have_attribute('src','/test-avatar/2')
    page.get_by_role('tab',name='Publicaciones').click()
    expect(page.locator('.crm-profile-content')).to_contain_text('Historia de adopci\u00f3n')
    page.get_by_role('tab',name='Chats denunciados').click()
    expect(page.locator('.crm-profile-content')).to_contain_text('El CRM no permite explorar mensajes privados')
    page.get_by_role('tab',name='Resumen').click()
    assert page.locator('.crm-dialog').get_by_role('button',name='Confirmar quita').count()==0
    assert page.locator('.crm-dialog').get_by_role('button',name='Quitar').count()==0
    page.locator('.crm-dialog').get_by_role('button',name='Cerrar').click()
    # Detail must use the same latest avatar after the listing updates.
    page.get_by_role('button',name='Ver usuario').first.click()
    expect(page.locator('.crm-profile-avatar')).to_have_attribute('src','/test-avatar/2')
    page.locator('.crm-dialog').get_by_role('button',name='Cerrar').click()
    assert not errors,errors
    context.close()
    results.append('Users list: 20+5, filter/order, preserved state and safe read-only detail; avatar v1 -> v2')

    # The new user moderation toolbar only appears after selecting rows.
    context,page,state,errors=session('control')
    state['user_fixture']=True
    begin(page);finish(page)
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    rows=page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')
    assert page.locator('.crm-bulk').get_by_role('button',name='Bloquear publicaciones',exact=True).count()==0
    rows.first.locator('input[type="checkbox"]').check()
    expect(page.locator('.crm-bulk').get_by_role('button',name='Bloquear publicaciones',exact=True)).to_be_visible()
    expect(page.locator('.crm-bulk').get_by_role('button',name='Eliminar usuarios',exact=True)).to_be_visible()
    page.locator('.crm-bulk').get_by_role('button',name='Bloquear publicaciones',exact=True).click()
    dialog=page.locator('.crm-user-moderation-dialog')
    expect(dialog).to_contain_text('podrán seguir ingresando y leyendo')
    assert dialog.get_by_role('button',name='Confirmar bloquear publicaciones').is_disabled()
    dialog.locator('textarea').fill('Moderación por incumplimiento comprobado')
    dialog.get_by_role('button',name='Confirmar bloquear publicaciones').click()
    try:
        expect(dialog).to_be_hidden(timeout=3000)
    except AssertionError:
        print('MODERATION_DIAGNOSTIC', {
            'alert':dialog.locator('[role="alert"]').all_text_contents(),
            'requests':state['requests'][-4:],
            'pageErrors':errors,
        }, flush=True)
        raise
    assert any(x[1].get('action')=='BLOCK' and len(x[1].get('userIds',[]))==1
               for x in state['requests'] if x[0].endswith('/users/moderate'))
    expect(rows.first).to_contain_text('Solo lectura')
    rows.nth(2).locator('input[type="checkbox"]').check()
    page.locator('.crm-bulk').get_by_role('button',name='Permitir publicar',exact=True).click()
    dialog=page.locator('.crm-user-moderation-dialog')
    dialog.locator('textarea').fill('Revisión finalizada, puede participar nuevamente')
    dialog.get_by_role('button',name='Confirmar permitir publicaciones').click()
    expect(dialog).to_be_hidden()
    assert any(x[1].get('action')=='UNBLOCK' and len(x[1].get('userIds',[]))==1
               for x in state['requests'] if x[0].endswith('/users/moderate'))
    expect(rows.nth(2)).not_to_contain_text('Solo lectura')
    rows.first.locator('input[type="checkbox"]').check()
    rows.nth(1).locator('input[type="checkbox"]').check()
    page.locator('.crm-bulk').get_by_role('button',name='Eliminar usuarios',exact=True).click()
    dialog=page.locator('.crm-user-moderation-dialog')
    expect(dialog).to_contain_text('irreversible')
    dialog.locator('textarea').fill('Eliminación ordenada tras revisión administrativa')
    assert dialog.get_by_role('button',name='Eliminar definitivamente').is_disabled()
    dialog.get_by_label('Escribí ELIMINAR para confirmar').fill('ELIMINAR')
    dialog.get_by_role('button',name='Eliminar definitivamente').click()
    expect(dialog).to_be_hidden()
    assert any(x[1].get('action')=='DELETE' and x[1].get('confirm')=='ELIMINAR'
               and len(x[1].get('userIds',[]))==2
               for x in state['requests'] if x[0].endswith('/users/moderate'))
    expect(rows).to_have_count(18)
    assert not errors,errors
    context.close()
    results.append('Users bulk moderation: selection actions, reason, typed deletion and API payload')

    # Per-row actions do not inherit the bulk selection. Every dangerous click
    # first opens a foreground rounded native modal; Cancel/Escape never mutate.
    context,page,state,errors=session('control')
    state['user_fixture']=True
    begin(page);finish(page)
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    rows=page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')
    expect(rows).to_have_count(20)
    first=rows.first
    second=rows.nth(1)
    expect(first.get_by_role('button',name='Bloquear publicaciones de Usuario de prueba 01')).to_be_visible()
    expect(first.get_by_role('button',name='Eliminar usuario Usuario de prueba 01')).to_be_visible()
    expect(rows.nth(2).get_by_role('button',name='Permitir publicaciones de Usuario de prueba 03')).to_be_visible()
    assert first.get_by_role('button',name='Bloquear publicaciones de Usuario de prueba 01').evaluate(
        '(el) => el.textContent.trim() === "" && el.querySelector("img") !== null')
    second.locator('input[type="checkbox"]').check()
    first.get_by_role('button',name='Bloquear publicaciones de Usuario de prueba 01').click()
    dialog=page.locator('.crm-user-moderation-dialog')
    expect(dialog).to_be_visible()
    assert dialog.evaluate("(el) => el.matches(':modal') && el.getAttribute('aria-modal') === 'true'")
    assert dialog.evaluate("(el) => parseFloat(getComputedStyle(el).borderTopLeftRadius)") >= 20
    assert dialog.evaluate("(el) => getComputedStyle(el, '::backdrop').backgroundColor.includes('0.5')")
    expect(dialog).to_contain_text('Usuario de prueba 01')
    assert not any(url.endswith('/users/moderate') for url,_ in state['requests'])
    dialog.get_by_role('button',name='Cancelar').click()
    expect(dialog).to_be_hidden()
    assert not any(url.endswith('/users/moderate') for url,_ in state['requests'])
    # Canceling must not alter the previously selected second row.
    assert second.locator('input[type="checkbox"]').is_checked()
    first.get_by_role('button',name='Bloquear publicaciones de Usuario de prueba 01').click()
    dialog=page.locator('.crm-user-moderation-dialog')
    dialog.locator('textarea').fill('Incumplimiento comprobado de la política de publicación')
    dialog.get_by_role('button',name='Confirmar bloquear publicaciones').click()
    expect(dialog).to_be_hidden()
    one='00000001-1111-4111-8111-111111111111'
    two='00000002-1111-4111-8111-111111111111'
    mods=[body for url,body in state['requests'] if url.endswith('/users/moderate')]
    assert mods[-1]['action']=='BLOCK' and mods[-1]['userIds']==[one],mods
    expect(rows.first).to_contain_text('Solo lectura')
    expect(rows.first.get_by_role('button',name='Permitir publicaciones de Usuario de prueba 01')).to_be_visible()
    assert second.locator('input[type="checkbox"]').is_checked()
    rows.first.get_by_role('button',name='Eliminar usuario Usuario de prueba 01').click()
    dialog=page.locator('.crm-user-moderation-dialog')
    assert dialog.evaluate("(el) => el.matches(':modal')")
    expect(dialog).to_contain_text('irreversible')
    dialog.locator('textarea').fill('Baja administrativa tras investigación documentada')
    dialog.get_by_label('Escribí ELIMINAR para confirmar').fill('ELIMINAR')
    page.keyboard.press('Escape')
    expect(dialog).to_be_hidden()
    assert len([1 for url,_ in state['requests'] if url.endswith('/users/moderate')]) == 1
    rows.first.get_by_role('button',name='Eliminar usuario Usuario de prueba 01').click()
    dialog=page.locator('.crm-user-moderation-dialog')
    dialog.locator('textarea').fill('Baja administrativa tras investigación documentada')
    dialog.get_by_label('Escribí ELIMINAR para confirmar').fill('ELIMINAR')
    dialog.get_by_role('button',name='Eliminar definitivamente').click()
    expect(dialog).to_be_hidden()
    mods=[body for url,body in state['requests'] if url.endswith('/users/moderate')]
    assert mods[-1]['action']=='DELETE' and mods[-1]['userIds']==[one] and mods[-1]['confirm']=='ELIMINAR',mods
    expect(rows).to_have_count(19)
    assert rows.first.get_attribute('data-post')==two
    assert rows.first.locator('input[type="checkbox"]').is_checked()
    assert not errors,errors
    context.close()
    results.append('Users per-row icon actions: isolated target, 24px modal, top layer, Cancel/Escape, block/delete confirmation')


    for role,may_block in [('TRUST_SAFETY',True),('SUPPORT',False)]:
        context,page,state,errors=session('control')
        state['platform_role']=role;state['user_fixture']=True
        begin(page);finish(page)
        page.locator('.crm-nav-btn[data-section="usuarios"]').click()
        page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]').first.locator('input[type="checkbox"]').check()
        assert page.locator('.crm-bulk').get_by_role('button',name='Bloquear publicaciones',exact=True).count()==(1 if may_block else 0)
        assert page.locator('.crm-bulk').get_by_role('button',name='Eliminar usuarios',exact=True).count()==0
        first=page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]').first
        assert first.get_by_role('button',name='Eliminar usuario Usuario de prueba 01').count()==0
        assert first.get_by_role('button',name='Bloquear publicaciones de Usuario de prueba 01').count()==(1 if may_block else 0)
        assert not errors,errors
        context.close()
    results.append('Users toolbar respects Trust & Safety and support permissions')

    context,page,state,errors=session('control')
    state['post_fixture']=True
    begin(page);finish(page)
    page.locator('.crm-nav-btn[data-section="publicaciones"]').click()
    table_rows=page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')
    expect(table_rows).to_have_count(20)
    expect(table_rows.first).to_contain_text('Luna')
    expect(table_rows.first).to_contain_text('Publicaci\u00f3n de prueba 1')
    page.evaluate("""() => {
      window.__layoutSnapshot = {
        nav: document.querySelector('.crm-nav'),
        toolbar: document.querySelector('.crm-tools'),
        search: document.querySelector('.crm-field-search input'),
        table: document.querySelector('.crm-table-wrap:not([hidden]) table'),
        row: document.querySelector('tr[data-post]')
      };
    }""")
    page.get_by_role('button',name='Cargar 20 m\u00e1s').click()
    expect(table_rows).to_have_count(25)
    assert page.evaluate("""() =>
      window.__layoutSnapshot.nav === document.querySelector('.crm-nav') &&
      window.__layoutSnapshot.toolbar === document.querySelector('.crm-tools') &&
      window.__layoutSnapshot.table === document.querySelector('.crm-table-wrap:not([hidden]) table') &&
      window.__layoutSnapshot.row === document.querySelector('tr[data-post]')""")
    page.get_by_role('button',name='Autor \u2195').click()
    expect(table_rows).to_have_count(20)
    assert page.evaluate("""() =>
      window.__layoutSnapshot.row === document.querySelector('tr[data-post]') &&
      window.__layoutSnapshot.search === document.querySelector('.crm-field-search input')""")
    page.get_by_role('button',name='Filtros (0)').click()
    page.get_by_label('Tema de la publicaci\u00f3n').fill('adopci\u00f3n')
    page.get_by_role('button',name='Agregar',exact=True).first.click()
    expect(page.locator('.crm-query-chip')).to_contain_text(['Tema: adopci\u00f3n','Orden:'])
    expect(table_rows).to_have_count(20)
    assert page.evaluate("""() =>
      window.__layoutSnapshot.nav === document.querySelector('.crm-nav') &&
      window.__layoutSnapshot.toolbar === document.querySelector('.crm-tools') &&
      window.__layoutSnapshot.row === document.querySelector('tr[data-post]') &&
      !document.querySelector('.crm-query-popover').hidden""")
    assert not errors,errors
    context.close()
    results.append('Publications refresh only table rows across load more, sort and filters')

    context,page,state,errors=session();begin(page);finish(page)
    expect(page.locator('#crm-root')).to_contain_text('Usuarios activos: 17')
    assert page.locator('#password').input_value()==''
    page.get_by_role('button',name='Cerrar sesi\u00f3n',exact=True).click();expect(page.locator('#crm-root')).to_be_empty()
    assert not errors,errors;context.close();results.append('TOTP login and logout')
    context,page,state,errors=session();begin(page);finish(page,'111111')
    expect(page.locator('#crm-status')).to_contain_text('no es v\u00e1lido');expect(page.locator('#crm-root')).to_be_empty()
    finish(page);expect(page.locator('#crm-root')).to_contain_text('Usuarios activos: 17');assert not errors,errors;context.close();results.append('Invalid MFA code rejected, valid retry succeeds')
    context,page,state,errors=session('empresas')
    begin(page)
    page.evaluate("window.testChallengeExpired=true")
    finish(page)
    # A challenge timeout is not a rejected TOTP code: Firebase requires
    # the first sign-in factor again, and the old resolver must be discarded.
    expect(page.locator('#crm-mfa')).to_be_hidden()
    expect(page.locator('#crm-login')).to_be_visible()
    expect(page.locator('#crm-status')).to_contain_text('venci\u00f3')
    expect(page.locator('#crm-status')).to_contain_text('c\u00f3digo puede ser correcto')
    expect(page.locator('#crm-root')).to_be_empty()
    # Same identity can authenticate with a newly issued challenge.
    begin(page);finish(page)
    expect(page.locator('#crm-root')).to_contain_text('Empresa Uno')
    assert not errors,errors;context.close()
    results.append('Expired TOTP challenge resets first factor; fresh sign-in works')
    context,page,state,errors=session('empresas','phone');begin(page);finish(page)
    expect(page.locator('#crm-root')).to_contain_text('Empresa Uno')
    page.get_by_label('Seleccionar empresa').select_option(B);expect(page.locator('#crm-root')).to_contain_text('Empresa Dos')
    assert state['requests'][-1][1]['companyId']==B
    state['deny']=True;page.locator('#crm-refresh').click();expect(page.locator('#crm-root')).to_be_empty();expect(page.locator('#crm-login')).to_be_visible()
    assert not errors,errors;context.close();results.append('SMS, tenant switch and revoked access')
    context,page,state,errors=session();begin(page);finish(page);expect(page.locator('#crm-root')).to_contain_text('Usuarios activos: 17')
    page.evaluate('window.testDelay=true');page.locator('#crm-refresh').click()
    page.get_by_role('button',name='Cerrar sesi\u00f3n',exact=True).click();page.wait_for_timeout(600);expect(page.locator('#crm-root')).to_be_empty()
    assert not errors,errors;context.close();results.append('Late network response cannot restore data after logout')
    context,page,state,errors=session('empresas');state['mismatch']=True;begin(page);finish(page)
    expect(page.locator('#crm-status')).to_contain_text('otra empresa');expect(page.locator('#crm-root')).to_be_empty()
    assert not errors,errors;context.close();results.append('Mismatched tenant response never displayed')
    for mode in ['control','empresas']:
        context,page,state,errors=session(mode,enroll=True)
        page.locator('#email').fill('fixture@example.test')
        page.locator('#password').fill('fixture-password')
        page.get_by_role('button',name='Ingresar',exact=True).click()
        expect(page.locator('#crm-onboarding')).to_be_visible()
        expect(page.locator('#crm-root')).to_be_empty()
        assert state['requests'] and all(path.endswith('/access') for path,_ in state['requests']),state['requests']
        page.get_by_role('button',name='Configurar aplicaci\u00f3n autenticadora').click()
        expect(page.get_by_label('C\u00f3digo del autenticador')).to_be_visible()
        page.get_by_label('C\u00f3digo del autenticador').fill('123456')
        page.get_by_role('button',name='Confirmar segundo factor').click()
        expect(page.locator('#crm-login')).to_be_visible()
        expect(page.locator('#crm-onboarding')).to_be_hidden()
        expect(page.locator('#crm-root')).to_be_empty()
        assert not errors,errors
        context.close()
        results.append(mode+' cannot access before MFA; enrollment requires a new login')
    for mode in ['control','empresas']:
        context,page,state,errors=session(mode)
        page.get_by_role('button',name='Continuar con Google').click()
        expect(page.locator('#crm-mfa')).to_be_visible()
        page.get_by_role('button',name='Usar este factor').click()
        page.locator('#crm-code').fill('123456')
        page.get_by_role('button',name='Verificar e ingresar').click()
        expect(page.locator('#crm-root')).not_to_be_empty()
        assert not errors,errors
        context.close()
        results.append(mode+' Google sign-in also requires Firebase second factor')
    for mode in ['control','empresas']:
        context,page,state,errors=session(mode)
        google=page.locator('#crm-google-login')
        expect(google).to_be_visible()
        expect(google.locator('img.crm-google-mark')).to_be_visible()
        assert google.locator('img').get_attribute('src')=='/google-logo.svg'
        password=page.locator('#password')
        expect(password).to_have_attribute('type','password')
        eye=page.locator('#crm-login .crm-password-eye')
        expect(eye).to_be_visible()
        eye.click()
        expect(password).to_have_attribute('type','text')
        expect(eye).to_have_attribute('aria-label','Ocultar contrase\u00f1a')
        eye.click()
        expect(password).to_have_attribute('type','password')
        assert not errors,errors
        context.close()
        results.append(mode+' Google logo and accessible eye toggle')
    context,page,state,errors=session('control')
    page.evaluate("window.testRegistrationError='auth/email-already-in-use'")
    page.get_by_role('button',name='Crear cuenta de Control').click()
    page.locator('#email').fill('fixture@example.test')
    page.locator('#password').fill('valid-example-password')
    page.get_by_role('button',name='Registrarme').click()
    expect(page.locator('#crm-status')).to_contain_text('ya tiene una cuenta')
    expect(page.locator('#crm-root')).to_be_empty()
    expect(page.get_by_role('button',name='Ingresar',exact=True)).to_be_visible()
    expect(page.get_by_role('button',name='Crear cuenta de Control')).to_be_visible()
    expect(page.locator('#crm-refresh')).to_be_hidden()
    assert page.locator('#email').input_value()=='fixture@example.test'
    assert page.locator('#password').input_value()==''
    page.get_by_role('button',name='Continuar con Google').click()
    expect(page.locator('#crm-mfa')).to_be_visible()
    page.get_by_role('button',name='Usar este factor').click()
    page.locator('#crm-code').fill('123456')
    page.get_by_role('button',name='Verificar e ingresar').click()
    expect(page.locator('#crm-root')).to_contain_text('Usuarios activos: 17')
    assert not errors,errors
    context.close()
    results.append('Existing account signup switches to login; Google MFA authenticates securely')
    context,page,state,errors=session('control')
    state['stale']=True
    page.evaluate('window.testStale=true')
    begin(page);finish(page)
    expect(page.locator('#crm-login')).to_be_visible()
    expect(page.locator('#email')).to_be_visible()
    expect(page.locator('#password')).to_be_visible()
    expect(page.locator('#crm-login .crm-password-eye')).to_be_visible()
    expect(page.get_by_role('button',name='Ingresar',exact=True)).to_be_visible()
    expect(page.get_by_role('button',name='Continuar con Google')).to_be_visible()
    expect(page.locator('#crm-onboarding')).to_be_hidden()
    expect(page.get_by_role('button',name='Cerrar sesi\u00f3n')).to_be_hidden()
    expect(page.locator('#crm-status')).not_to_contain_text('Renovar el token')
    expect(page.locator('#crm-status')).not_to_contain_text('Cerr\u00e1 sesi\u00f3n')
    expect(page.locator('#crm-root')).to_be_empty()
    assert not errors,errors
    context.close()
    results.append('Expired step-up clears the session and shows the sign-in form')

    context=browser.new_context()
    page=context.new_page()
    page.set_content('<div id="auth-root"><label>Clave<input id="public-secret" type="password" autocomplete="current-password"></label></div>')
    source=(ROOT/'web/app/app.js').read_text()
    start=source.index('function enablePasswordVisibility(')
    stop=source.index('function paintAuth(',start)
    snippet=source[start:stop]
    page.evaluate('() => { '+snippet+' enablePasswordVisibility(document.getElementById("auth-root")); }')
    eye=page.locator('#auth-root .auth-password-toggle')
    expect(eye).to_have_attribute('aria-label','Mostrar contrase\u00f1a')
    eye.click()
    expect(page.locator('#public-secret')).to_have_attribute('type','text')
    expect(eye).to_have_attribute('aria-label','Ocultar contrase\u00f1a')
    eye.click()
    expect(page.locator('#public-secret')).to_have_attribute('type','password')
    context.close()
    results.append('Public login and signup shared password eye control')
    browser.close()
print(json.dumps({'passed':len(results),'scenarios':results,'identityAndApi':'mocked; not real Firebase or Contabo'},ensure_ascii=True,indent=2))
