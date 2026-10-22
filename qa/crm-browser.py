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

export function getMultiFactorResolver(){return {session:'mfa',hints:[{uid:'factor',factorId:window.testFactor||'totp',displayName:'Factor de prueba'}],resolveSignIn:async a=>{if(a.code!=='123456')throw {code:'auth/invalid-verification-code'};auth.currentUser={uid:'fixture'};listeners.forEach(f=>f());return {user:auth.currentUser};}};}
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
        state={'deny':False,'mismatch':False,'enroll':enroll,'requests':[]}
        def route(route):
            url=route.request.url
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
                if state['enroll']:
                    route.fulfill(status=403,content_type='application/json',body=json.dumps({'success':False,'errorCode':'STEP_UP_REQUIRED','message':'Segundo factor obligatorio'}));return
                if state['deny']:
                    route.fulfill(status=403,content_type='application/json',body=json.dumps({'success':False,'errorCode':'FORBIDDEN','message':'Acceso revocado'}));return
                import time
                if url.endswith('/access'):
                    data={'platformRole':'OWNER','companies':[{'companyId':A,'role':'ADMIN'},{'companyId':B,'role':'VIEWER'}],'stepUpValidUntilEpochSeconds':int(time.time())+900}
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
    context,page,state,errors=session();begin(page);finish(page)
    expect(page.locator('#crm-root')).to_contain_text('Usuarios activos: 17')
    assert page.locator('#password').input_value()==''
    page.get_by_role('button',name='Cerrar sesi\u00f3n',exact=True).click();expect(page.locator('#crm-root')).to_be_empty()
    assert not errors,errors;context.close();results.append('TOTP login and logout')
    context,page,state,errors=session();begin(page);finish(page,'111111')
    expect(page.locator('#crm-status')).to_contain_text('no es v\u00e1lido');expect(page.locator('#crm-root')).to_be_empty()
    finish(page);expect(page.locator('#crm-root')).to_contain_text('Usuarios activos: 17');assert not errors,errors;context.close();results.append('Invalid MFA code rejected, valid retry succeeds')
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
        page.get_by_role('button',name='Ingresar con Google').click()
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
    expect(page.locator('#crm-status')).to_contain_text('Ya existe una cuenta')
    expect(page.locator('#crm-root')).to_be_empty()
    assert not errors,errors
    context.close()
    results.append('Existing registration gets actionable Firebase error and no privilege')

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
