"""Geometry and interaction regression for the real shared CRM, using only local fixtures."""
import json
import mimetypes
import os
from pathlib import Path
import tempfile
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
WEB = ROOT / 'web/app'
BASE = 'https://ogt-layout.test'
OUTPUT = Path(os.getenv('OGT_QA_OUTPUT', str(Path(tempfile.gettempdir()) / 'ogt-layout-qa')))
OUTPUT.mkdir(parents=True, exist_ok=True)
HTML = '''<!doctype html><html lang="es"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="/crm/crm.css"><link rel="stylesheet" href="/crm/crm-layout.css">
</head><body data-crm-view="app">
<div id="crm-auth" class="crm-auth"><main class="crm-auth-card">
<p class="crm-status">Acceso de prueba autorizado</p><button class="crm-btn">Cerrar sesi\u00f3n</button>
</main></div><section id="crm-root"></section>
<script type="module" src="/crm/crm-layout.js"></script>
<script type="module" src="/fixture.js"></script></body></html>'''
FIXTURE = r'''
import { mountWorkspace } from '/crm/crm-ui.js';
const company = location.pathname.includes('empresas');
window.fixture = { size: 60, fail: false, reads: 0 };
const records = Array.from({length: 60}, (_, i) => ({
  id: String(i + 1), displayName: 'Usuario de prueba ' + String(i + 1).padStart(2, '0'),
  photoUrl: null, status: 'ACTIVE', role: 'USER', createdAt: '2026-10-25T12:00:00Z'
}));
const posts = records.map(u => ({id:u.id, topic:'Historia de prueba',
  body:'Contenido de la publicaci\u00f3n. '.repeat(12), createdAt:u.createdAt}));
const profile = user => ({...user, displayName:window.fixture.longName ? user.displayName.repeat(20) : user.displayName, communityPoints: 5, visiblePostCount:60,
  receivedReportCount:40, canReviewReports:true,
  reportedContent:records.slice(0,40).map(u => ({id:u.id,reason:'Revisi\u00f3n de contenido',status:'OPEN',createdAt:u.createdAt}))});
function listing(users) {
  return {
    advanced:true, filterType: users ? 'users' : 'posts', readOnly: users, userProfile:users,
    mark:true, noun: users ? 'usuarios' : 'publicaciones',
    ...(users ? {bulkReview:{icon:'qs_eye.svg',label:'Revisar seleccionados'}} : {}),
    columns:['Nombre','Estado','Rol','Alta','Actividad','Acci\u00f3n'],
    sortable:{Nombre:users ? 'name' : 'author', Alta:'createdAt'},
    maxOrders:4, orderLabels:{createdAt:'Alta',name:'Nombre',author:'Autor'},
    defaultFilters:users ? {statuses:[],roles:[],from:'',to:''} : {topics:[],authors:[],photo:'any',from:'',to:''},
    placeholder:users ? 'Buscar usuario' : 'Buscar publicaci\u00f3n',
    emptyTitle:'Sin registros', emptyDetail:'No hay resultados.',
    async load(query, cursor) {
      window.fixture.reads++;
      if(window.fixture.fail) throw new Error('Fixture offline');
      if(query === 'vacio') return {items:[],nextCursor:null};
      const start=Number(cursor || 0), end=Math.min(start+20,window.fixture.size);
      return {items:records.slice(start,end),nextCursor:end<window.fixture.size ? String(end) : null};
    },
    async loadDetail(user) {
      if(users) return profile(user);
      const comments=records.map(u=>({id:u.id, authorName:u.displayName,body:'Comentario de prueba '.repeat(12)}));
      return {impactCount:3,comments,anecdotes:comments,reports:comments.map(u=>({reason:u.body,status:'OPEN'}))};
    },
    async loadUserPosts() { return {posts,nextCursor:null}; },
    async searchUsers() { return {users:records}; },
    async reassign() { throw new Error('No mutation allowed in layout fixture'); },
    present(user) { return {
      key:user.id,mark:true,
      cells:[{text:user.displayName},{badge:'Activo',tone:'calm'}, {text:'Usuario'},
        {text:'25/10/2026, 12:00'}, {text:'Sin actividad registrada'}],
      actions:[{icon:'qs_eye.svg',label:users?'Ver usuario':'Ver publicaci\u00f3n',name:'view'},
        ...(!users?[{icon:'qs_eye.svg',label:'Cambiar autor\u00eda',name:'reassign'}]:[])],
      detail:{title:user.displayName,meta:'Perfil de prueba',body:'Datos simulados sin acceso productivo.'}
    };}
  };
}
const sections = company
  ? [['resumen','Resumen'],['campanas','Campa\u00f1as'],['cupones','Cupones'],['impacto','Impacto']]
  : [['resumen','Resumen'],['usuarios','Usuarios'],['empresas','Empresas'],['denuncias','Denuncias'],['publicaciones','Publicaciones']];
const specs=sections.map(([id,label])=>({id,label,icon:'qs_eye.svg',...(['usuarios','publicaciones'].includes(id)?{view:'listing'}:{})}));
window.desk=mountWorkspace(document.getElementById('crm-root'),{
  title:company?'OGT Empresas':'OGT Control',brand:'OGT',lede:'Prueba de geometr\u00eda',
  sections:specs,rows:records.map(u=>({key:u.id,label:'Indicador '+u.id,value:1})),
  listings:{usuarios:listing(true),publicaciones:listing(false)}
});
window.fixtureReady=true;
'''
BOUNDS = '''() => {
  const panel=document.querySelector('.crm-panel');
  const region=panel.querySelector('.crm-table-wrap:not([hidden])');
  const p=panel.getBoundingClientRect(), r=region.getBoundingClientRect();
  const doc=document.documentElement;
  return {pageWidth:doc.scrollWidth,pageHeight:doc.scrollHeight,w:innerWidth,h:innerHeight,
    panel:{x:p.left,y:p.top,right:p.right,bottom:p.bottom},
    region:{x:r.left,y:r.top,right:r.right,bottom:r.bottom,height:r.height},
    client:region.clientHeight,scroll:region.scrollHeight,
    overflow:getComputedStyle(region).overflowY,radius:getComputedStyle(panel).borderTopLeftRadius};
}'''


def check_bounds(page, label):
    value = page.evaluate(BOUNDS)
    assert value['pageHeight'] <= value['h'] + 2, (label, value)
    assert value['pageWidth'] <= value['w'] + 2, (label, value)
    panel, region = value['panel'], value['region']
    assert 0 <= panel['x'] and panel['right'] <= value['w'] + 1, (label, value)
    assert panel['bottom'] <= value['h'] + 1, (label, value)
    assert region['y'] >= panel['y'] and region['bottom'] <= panel['bottom'] + 1, (label, value)
    assert region['height'] >= 40 and value['scroll'] > value['client'], (label, value)
    assert value['overflow'] == 'auto' and float(value['radius'].replace('px', '')) > 0, (label, value)
    return value


def assert_internal_scroll(page, region):
    expect(region).to_have_attribute('tabindex', '0')
    region.evaluate('(el) => { el.scrollTop=0; }')
    region.focus()
    region.press('PageDown')
    page.wait_for_function('(el) => el.scrollTop > 0', arg=region.element_handle())
    assert page.evaluate('window.scrollY') == 0
    region.evaluate('(el) => { el.scrollTop=el.scrollHeight; }')
    assert region.evaluate('(el) => el.scrollTop > 0')
    # Sticky table headings remain inside the visible scrollport.
    if region.locator('th').count():
        top=region.bounding_box()['y']
        assert abs(region.locator('th').first.bounding_box()['y'] - top) <= 2


def route_local(route):
    path=urlsplit(route.request.url).path
    if path.endswith('/fixture'):
        route.fulfill(status=200,content_type='text/html',body=HTML)
    elif path == '/fixture.js':
        route.fulfill(status=200,content_type='text/javascript',body=FIXTURE)
    else:
        file=(WEB / path.lstrip('/')).resolve()
        if file.is_relative_to(WEB.resolve()) and file.is_file():
            content_type=mimetypes.guess_type(file.name)[0] or 'application/octet-stream'
            route.fulfill(status=200,content_type=content_type,body=file.read_bytes())
        else:
            route.fulfill(status=404,body='Not found')


def main():
    # Every entry point must load the same layout, not a Users-only override.
    for mode in ('control','empresas'):
        html=(WEB / 'crm' / mode / 'index.html').read_text()
        assert '../crm-layout.css?v=bounded-lists-1' in html
        assert '../crm-layout.js?v=bounded-lists-1' in html
    results=[]
    with sync_playwright() as pw:
        executable=os.getenv('CRM_CHROMIUM_PATH')
        browser=pw.chromium.launch(headless=True,**({'executable_path':executable} if executable else {}))
        for width,height in [(1440,900),(1024,768),(800,600),(390,844),(320,568),(844,390),(720,450)]:
            for mode,sections in [('control',['resumen','usuarios','empresas','denuncias','publicaciones']),
                                  ('empresas',['resumen','campanas','cupones','impacto'])]:
                context=browser.new_context(viewport={'width':width,'height':height},has_touch=width<801)
                context.route('**/*',route_local)
                page=context.new_page()
                errors=[]
                page.on('pageerror',lambda error:errors.append(str(error)))
                try:
                    page.goto(BASE + '/crm/' + mode + '/fixture')
                    page.wait_for_function('window.fixtureReady === true')
                    page.evaluate('document.fonts.ready')
                    for section in sections:
                        page.locator('.crm-nav-btn[data-section="'+section+'"]').click()
                        region=page.locator('.crm-panel > .crm-table-wrap:not([hidden])')
                        expect(region.locator('tbody tr')).to_have_count(20 if section in ('usuarios','publicaciones') else 60)
                        check_bounds(page,(mode,section,width,height))
                        before=page.locator('.crm-panel').bounding_box()
                        assert_internal_scroll(page,region)
                        assert page.locator('.crm-panel').bounding_box()==before
                        results.append(f'{mode}/{section}@{width}x{height}')
                    if mode=='control':
                        exercise_lists(page,width,height)
                    assert not errors,errors
                except BaseException:
                    page.screenshot(path=str(OUTPUT/f'layout-failure-{mode}-{width}x{height}.png'),full_page=True)
                    raise
                finally:
                    context.close()
        browser.close()
    print(json.dumps({'passedViews':len(results),'views':results,
                      'scope':'real CRM components with local fixture data; not production accounts'},indent=2))


def exercise_lists(page,width,height):
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    region=page.locator('.crm-panel > .crm-table-wrap:not([hidden])')
    rows=region.locator('tbody tr')
    expect(rows).to_have_count(20)
    page.evaluate('''() => {window.layoutNodes={nav:document.querySelector('.crm-nav'),
      tools:document.querySelector('.crm-tools'),panel:document.querySelector('.crm-panel'),
      first:document.querySelector('tr[data-post]')};}''')
    region.evaluate('(el)=>{el.scrollTop=150}')
    previous_scroll=region.evaluate('(el)=>el.scrollTop')
    page.get_by_role('button',name='Cargar 20 m\u00e1s').click()
    expect(rows).to_have_count(40)
    assert region.evaluate('(el)=>el.scrollTop') >= previous_scroll-1
    page.get_by_role('button',name='Cargar 20 m\u00e1s').click()
    expect(rows).to_have_count(60)
    assert page.evaluate('''()=> window.layoutNodes.nav===document.querySelector('.crm-nav') &&
      window.layoutNodes.tools===document.querySelector('.crm-tools') &&
      window.layoutNodes.panel===document.querySelector('.crm-panel') &&
      window.layoutNodes.first===document.querySelector('tr[data-post]')''')
    check_bounds(page,'60 rows')
    # Selection review is a bounded list too, not a growing full-screen dialog.
    region.get_by_label('Marcar p\u00e1gina').check()
    page.get_by_role('button',name='Revisar seleccionados').click()
    selected=page.locator('.crm-profile-dialog > .crm-profile-posts')
    expect(selected.locator('button')).to_have_count(60)
    assert_internal_scroll(page,selected)
    dialog=page.locator('.crm-profile-dialog')
    assert dialog.evaluate('(el)=>el.scrollHeight<=el.clientHeight+2')
    dialog.get_by_role('button',name='Cerrar',exact=True).click()
    rows.first.get_by_role('button',name='Ver usuario').click()
    page.get_by_role('tab',name='Publicaciones',exact=True).click()
    content=page.locator('.crm-profile-content')
    expect(content.locator('.crm-profile-post')).to_have_count(60)
    assert_internal_scroll(page,content)
    dialog=page.locator('.crm-profile-dialog')
    assert dialog.evaluate('(el)=>el.scrollHeight<=el.clientHeight+2')
    assert content.bounding_box()['height'] >= 40
    page.get_by_role('tab',name='Denuncias',exact=True).click()
    expect(content.locator('.crm-profile-post')).to_have_count(40)
    assert_internal_scroll(page,content)
    dialog.get_by_role('button',name='Cerrar',exact=True).click()
    page.evaluate('window.fixture.longName=true')
    rows.first.get_by_role('button',name='Ver usuario').click()
    expect(page.locator('.crm-profile-content')).to_contain_text('Informaci')
    assert page.locator('.crm-profile-dialog').evaluate('(el)=>el.scrollHeight<=el.clientHeight+2')
    assert page.locator('.crm-profile-content').bounding_box()['height'] >= 40
    page.locator('.crm-profile-dialog').get_by_role('button',name='Cerrar',exact=True).click()
    page.get_by_role('button',name='Filtros (0)').click()
    popover=page.locator('.crm-query-popover')
    expect(popover).to_be_visible()
    box=popover.bounding_box()
    assert box['y']>=0 and box['y']+box['height'] <= page.viewport_size['height']+1,box
    page.keyboard.press('Escape')
    expect(popover).to_be_hidden()
    # Horizontal overflow stays inside the rounded card on narrow displays.
    region.evaluate('(el)=>{el.scrollLeft=el.scrollWidth}')
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth+2')
    region.evaluate('(el)=>{el.scrollLeft=0;el.scrollTop=0}')
    if width==390:
        page.screenshot(path=str(OUTPUT/'lists-mobile.png'))
    if width==1440:
        page.screenshot(path=str(OUTPUT/'lists-desktop.png'))
    # Error and empty results must not tear down or enlarge the shell.
    search=page.locator('.crm-field-search input')
    page.evaluate('window.fixture.fail=true')
    search.fill('offline')
    expect(page.locator('.crm-list-error')).to_be_visible()
    check_bounds(page,'failed refresh retains rows')
    page.evaluate('window.fixture.fail=false')
    search.fill('vacio')
    expect(rows).to_have_count(0)
    expect(page.locator('.crm-empty')).to_be_visible()
    assert page.evaluate('document.documentElement.scrollHeight<=innerHeight+2')
    # Publication conversations and author picker use the same internal-list policy.
    page.locator('.crm-nav-btn[data-section="publicaciones"]').click()
    expect(rows).to_have_count(20)
    rows.first.get_by_role('button',name='Ver publicaci\u00f3n').click()
    comments=page.get_by_role('region',name='Comentarios',exact=True)
    expect(comments.locator('.crm-subnode')).to_have_count(60)
    assert_internal_scroll(page,comments)
    reports=page.get_by_role('region',name='Denuncias',exact=True)
    expect(reports.locator('p')).to_have_count(60)
    page.locator('.crm-dialog').get_by_role('button',name='Cerrar',exact=True).click()
    rows.first.get_by_role('button',name='Cambiar autor\u00eda').click()
    sheet=page.locator('.crm-sheet')
    sheet.get_by_label('Buscar usuario').fill('Usuario')
    matches=page.get_by_role('region',name='Resultados de usuarios')
    expect(matches.locator('.crm-pick')).to_have_count(60)
    assert_internal_scroll(page,matches)
    matches.locator('.crm-pick').last.click()
    expect(sheet.get_by_role('button',name='Confirmar autor\u00eda')).to_be_enabled()
    sheet.get_by_role('button',name='Cerrar',exact=True).click()
    # Resizing an already mounted shell must not require reload or lose navigation.
    original=page.viewport_size
    page.set_viewport_size({'width':390,'height':600})
    page.wait_for_function('Math.abs(document.body.getBoundingClientRect().height-innerHeight)<=1')
    check_bounds(page,'live resize')
    page.set_viewport_size(original)
    page.wait_for_function('Math.abs(document.body.getBoundingClientRect().height-innerHeight)<=1')
    check_bounds(page,'restore viewport')


if __name__=='__main__':
    main()
