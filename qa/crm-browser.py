"""Browser regression checks with mock identity/API, never production credentials."""
import json
import os
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
export const getIdToken=async()=> 'fixture-token';
export const signInWithEmailAndPassword=async()=>{if(window.testEnroll){auth.currentUser={uid:'fixture',email:'fixture@example.test',emailVerified:true,providerData:[],enrolledFactors:[]};listeners.forEach(f=>f());return {user:auth.currentUser};}throw {code:'auth/multi-factor-auth-required'};};
export class GoogleAuthProvider{addScope(){}}
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
        state={'deny':False,'mismatch':False,'enroll':enroll,'requests':[],'avatar_version':1}
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
            if '/api/v1/crm/' in url:
                body=route.request.post_data_json or {}
                state['requests'].append((url,body))
                if state['enroll'] or state.get('stale'):
                    route.fulfill(status=403,content_type='application/json',body=json.dumps({'success':False,'errorCode':'STEP_UP_REQUIRED','message':'Segundo factor obligatorio'}));return
                if state['deny']:
                    route.fulfill(status=403,content_type='application/json',body=json.dumps({'success':False,'errorCode':'FORBIDDEN','message':'Acceso revocado'}));return
                import time
                if url.endswith('/access'):
                    data={'platformRole':'OWNER','companies':[{'companyId':A,'role':'ADMIN'},{'companyId':B,'role':'VIEWER'}],'stepUpValidUntilEpochSeconds':int(time.time())+900}
                elif url.endswith('/posts/search') and state.get('post_fixture'):
                    start=21 if body.get('cursor') else 1
                    posts=[{
                        'id':f'{n:08d}-1111-4111-8111-111111111111',
                        'status':'VISIBLE',
                        'authorName':'Ana Torres',
                        'body':f'Publicación de prueba {n}',
                        'animalTitle':'Luna' if n==1 else None,
                        'createdAt':'2026-10-24T10:00:00Z',
                        'topic':'adopción',
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
                elif url.endswith('/users/posts') and state.get('user_fixture'):
                    data={'posts':[{'id':'12345678-1111-4111-8111-111111111111','body':'Historia de adopción','topic':'Adopción','createdAt':'2026-10-24T10:00:00Z'}], 'nextCursor':None}
                elif url.endswith('/users/list') and state.get('user_fixture'):
                    index=21 if body.get('cursor') else 1
                    values=[{
                        'id':f'{n:08d}-1111-4111-8111-111111111111',
                        'displayName':f'Usuario de prueba {n:02d}',
                        'photoUrl':('/test-avatar/'+str(state['avatar_version'])) if n==1 else ('/test-avatar/broken' if n==2 else None),
                        'status':'ACTIVE' if n%2 else 'SUSPENDED',
                        'role':'USER' if n%3 else 'COMMUNITY_MODERATOR',
                        'createdAt':'2026-10-24T12:00:00Z',
                        'lastSeenAt':None,
                    } for n in range(index,26 if index==21 else 21)]
                    data={'users':values,'nextCursor':'users-fixture-cursor' if index==1 else None}
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
                    mime='text/javascript' if file.suffix in ('.js','.mjs') else 'text/html' if file.suffix=='.html' else 'application/json'
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
        assert not errors,errors
        context.close()
        results.append(mode+' full navigation: '+', '.join(sections))
    # Filtros, orden y paginación deben actualizar filas sin remontar el resto del CRM.
    context,page,state,errors=session('control')
    state['user_fixture']=True
    begin(page);finish(page)
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    page.wait_for_timeout(450)
    if page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]').count()==0:
        print('DEBUG USERS:',json.dumps({'requests':state['requests'][-6:],'errors':errors,'text':page.locator('#crm-root').inner_text()[-3000:]},ensure_ascii=True))
    expect(page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')).to_have_count(20)
    # Profile-image E2E: populated, missing and invalid photos, styling, selection,
    # updated URL after server refresh, and stable navigation/toolbar DOM.
    avatar_rows=page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')
    avatar=avatar_rows.first.locator('.crm-list-user-avatar')
    expect(avatar).to_have_attribute('src','/test-avatar/1')
    page.wait_for_function("""() => {
      const img=document.querySelector('.crm-list-user-avatar');
      return img && img.complete && img.naturalWidth > 0;
    }""")
    assert page.evaluate("""() => {
      const img=document.querySelector('.crm-list-user-avatar');
      const style=getComputedStyle(img);
      return style.borderRadius==='50%' && style.boxShadow!=='none' &&
        img.previousElementSibling?.type==='checkbox';
    }""")
    import re
    expect(avatar_rows.nth(1).locator('.crm-list-user-avatar')).to_have_attribute('src', re.compile(r'avatar_anonymous\.png$'))
    expect(avatar_rows.nth(2).locator('.crm-list-user-avatar')).to_have_attribute('src', re.compile(r'avatar_anonymous\.png$'))
    assert page.locator('.crm-query-popover').is_hidden()
    page.get_by_role('button',name='Cargar 20 más').click()
    expect(page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')).to_have_count(25)
    page.get_by_role('button',name='Filtros (0)').click()
    page.get_by_label('Activo',exact=True).check()
    expect(page.locator('.crm-query-chip')).to_contain_text(['Estado: Activo','Orden:'])
    expect(page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')).to_have_count(20)
    assert any(item[1].get('filters',{}).get('statuses')==['ACTIVE'] for item in state['requests'] if item[0].endswith('/users/list'))
    page.get_by_role('button',name='Orden (1)').click()
    page.get_by_role('button',name='+ Nombre').click()
    assert any(len(item[1].get('orders',[]))==2 for item in state['requests'] if item[0].endswith('/users/list'))
    page.locator('.crm-nav-btn[data-section="publicaciones"]').click()
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    expect(page.locator('.crm-query-chip')).to_contain_text(['Estado: Activo','Orden:'])
    page.get_by_role('button',name='Ver usuario').first.click()
    expect(page.locator('.crm-dialog')).to_contain_text('Usuario de prueba')
    expect(page.locator('.crm-profile-hero')).to_be_visible()
    page.get_by_role('tab',name='Publicaciones').click()
    expect(page.locator('.crm-profile-content')).to_contain_text('Historia de adopción')
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
    results.append('Users list: 20+5, filter/order, preserved state and safe read-only detail')

    context,page,state,errors=session('control')
    state['post_fixture']=True
    begin(page);finish(page)
    page.locator('.crm-nav-btn[data-section="publicaciones"]').click()
    table_rows=page.locator('.crm-table-wrap:not([hidden]) tbody tr[data-post]')
    expect(table_rows).to_have_count(20)
    expect(table_rows.first).to_contain_text('Luna')
    expect(table_rows.first).to_contain_text('Publicación de prueba 1')
    page.evaluate("""() => {
      window.__layoutSnapshot = {
        nav: document.querySelector('.crm-nav'),
        toolbar: document.querySelector('.crm-tools'),
        search: document.querySelector('.crm-field-search input'),
        table: document.querySelector('.crm-table-wrap:not([hidden]) table'),
        row: document.querySelector('tr[data-post]')
      };
    }""")
    page.get_by_role('button',name='Cargar 20 más').click()
    expect(table_rows).to_have_count(25)
    assert page.evaluate("""() =>
      window.__layoutSnapshot.nav === document.querySelector('.crm-nav') &&
      window.__layoutSnapshot.toolbar === document.querySelector('.crm-tools') &&
      window.__layoutSnapshot.table === document.querySelector('.crm-table-wrap:not([hidden]) table') &&
      window.__layoutSnapshot.row === document.querySelector('tr[data-post]')""")
    page.get_by_role('button',name='Autor ↕').click()
    expect(table_rows).to_have_count(20)
    assert page.evaluate("""() =>
      window.__layoutSnapshot.row === document.querySelector('tr[data-post]') &&
      window.__layoutSnapshot.search === document.querySelector('.crm-field-search input')""")
    page.get_by_role('button',name='Filtros (0)').click()
    page.get_by_label('Tema de la publicación').fill('adopción')
    page.get_by_role('button',name='Agregar',exact=True).first.click()
    expect(page.locator('.crm-query-chip')).to_contain_text(['Tema: adopción','Orden:'])
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
    expect(page.locator('#crm-status')).to_contain_text('venció')
    expect(page.locator('#crm-status')).to_contain_text('código puede ser correcto')
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
        page.get_by_role('button',name='Configurar aplicación autenticadora').click()
        expect(page.get_by_label('Código del autenticador')).to_be_visible()
        page.get_by_label('Código del autenticador').fill('123456')
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
        expect(eye).to_have_attribute('aria-label','Ocultar contraseña')
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
    expect(page.get_by_role('button',name='Cerrar sesión')).to_be_hidden()
    expect(page.locator('#crm-status')).not_to_contain_text('Renovar el token')
    expect(page.locator('#crm-status')).not_to_contain_text('Cerrá sesión')
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
    expect(eye).to_have_attribute('aria-label','Mostrar contraseña')
    eye.click()
    expect(page.locator('#public-secret')).to_have_attribute('type','text')
    expect(eye).to_have_attribute('aria-label','Ocultar contraseña')
    eye.click()
    expect(page.locator('#public-secret')).to_have_attribute('type','password')
    context.close()
    results.append('Public login and signup shared password eye control')
    browser.close()
print(json.dumps({'passed':len(results),'scenarios':results,'identityAndApi':'mocked; not real Firebase or Contabo'},ensure_ascii=True,indent=2))
