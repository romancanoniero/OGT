"""Desktop Control: table rows stay inside rounded cards and scroll there."""
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
HTML = '''<!doctype html><html lang="es" data-crm="control"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="/crm/crm.css"><link rel="stylesheet" href="/crm/crm-control-scroll.css">
</head><body data-crm-view="app">
<div id="crm-auth" class="crm-auth"><main class="crm-auth-card">
<p class="crm-status">Acceso de prueba autorizado</p><button class="crm-btn">Cerrar sesi\u00f3n</button>
</main></div><section id="crm-root"></section>
<script type="module" src="/crm/crm-control-scroll.js"></script>
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
  const image=region.querySelector('.crm-list-user-avatar');
  const face=image?.getBoundingClientRect();
  const selector=image?.previousElementSibling?.getBoundingClientRect();
  const avatar=face ? {width:face.width,height:face.height,x:face.left,selectRight:selector.right,
    dy:(face.top+face.height/2)-(selector.top+selector.height/2)} : null;
  return {avatar,pageWidth:doc.scrollWidth,pageHeight:doc.scrollHeight,w:innerWidth,h:innerHeight,
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
    if value['avatar']:
        avatar=value['avatar']
        assert abs(avatar['width']-38)<1 and abs(avatar['height']-38)<1, (label,avatar)
        assert avatar['x']>=avatar['selectRight'] and abs(avatar['dy'])<=8, (label,avatar)
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
    # Only Control loads this stylesheet, and it is intentionally absent from Empresas.
    control=(WEB / 'crm/control/index.html').read_text()
    empresas=(WEB / 'crm/empresas/index.html').read_text()
    assert '../crm-control-scroll.css?v=control-bounded-1' in control
    assert '../crm-control-scroll.js?v=control-bounded-1' in control
    assert 'crm-control-scroll' not in empresas
    results=[]
    with sync_playwright() as pw:
        executable=os.getenv('CRM_CHROMIUM_PATH')
        browser=pw.chromium.launch(headless=True,
            **({'executable_path':executable} if executable else {}))
        for width,height in [(1440,900),(1024,768),(900,600)]:
            context=browser.new_context(viewport={'width':width,'height':height})
            context.route('**/*', route_local)
            page=context.new_page()
            errors=[]
            page.on('pageerror',lambda error: errors.append(str(error)))
            try:
                page.goto(BASE + '/crm/control/fixture')
                page.wait_for_function('window.fixtureReady === true')
                page.evaluate('document.fonts.ready')
                for section in ['resumen','usuarios','empresas','denuncias','publicaciones']:
                    page.locator('.crm-nav-btn[data-section="'+section+'"]').click()
                    region=page.locator('.crm-panel > .crm-table-wrap:not([hidden])')
                    expected=20 if section in ('usuarios','publicaciones') else 60
                    expect(region.locator('tbody tr')).to_have_count(expected)
                    before=page.locator('.crm-panel').bounding_box()
                    check_bounds(page,(section,width,height))
                    assert_internal_scroll(page,region)
                    assert page.locator('.crm-panel').bounding_box()==before
                    results.append(f'{section}: {width}x{height}')
                # Pagination: more rows must only increase scrollHeight, not card height.
                page.locator('.crm-nav-btn[data-section="usuarios"]').click()
                region=page.locator('.crm-panel > .crm-table-wrap:not([hidden])')
                region.evaluate('(el)=>{el.scrollTop=130}')
                scroll_before=region.evaluate('(el)=>el.scrollTop')
                card_before=page.locator('.crm-panel').bounding_box()
                for count in (40,60):
                    page.get_by_role('button',name='Cargar 20 más').click()
                    expect(region.locator('tbody tr')).to_have_count(count)
                    check_bounds(page,(count,width,height))
                    assert page.locator('.crm-panel').bounding_box()==card_before
                assert region.evaluate('(el)=>el.scrollTop') >= scroll_before-1
                # Selection action remains on-screen and never grows the document.
                region.locator('tbody tr').first.locator('input[type="checkbox"]').check()
                expect(page.get_by_role('button',name='Revisar seleccionados')).to_be_visible()
                check_bounds(page,('selection',width,height))
                # Resizing without refresh must keep the table in its rounded card.
                page.set_viewport_size({'width':max(801,width-90),'height':max(540,height-75)})
                check_bounds(page,('resized',width,height))
                page.set_viewport_size({'width':width,'height':height})
                check_bounds(page,('restored',width,height))
                # Failure preserves old rows inside the bounded region.
                page.evaluate('window.fixture.fail=true')
                page.locator('.crm-field-search input').fill('offline')
                expect(page.locator('.crm-list-error')).to_be_visible()
                check_bounds(page,('error',width,height))
                page.evaluate('window.fixture.fail=false')
                page.locator('.crm-field-search input').fill('vacio')
                expect(region.locator('tbody tr')).to_have_count(0)
                expect(page.locator('.crm-empty')).to_be_visible()
                assert page.evaluate('''()=>
                    document.documentElement.scrollHeight <= innerHeight+2 &&
                    document.documentElement.scrollWidth <= innerWidth+2 &&
                    document.querySelector('.crm-panel').getBoundingClientRect().bottom<=innerHeight+2''')
                assert not errors,errors
                results.append(f'pagination/selection/resize/errors/empty: {width}x{height}')
            except BaseException:
                page.screenshot(path=str(OUTPUT/f'control-scroll-failure-{width}x{height}.png'),full_page=True)
                raise
            finally:
                context.close()
        browser.close()
    print(json.dumps({'passed':len(results),'cases':results,
        'scope':'Control web desktop only; fixture rows, not user data'},ensure_ascii=False))


if __name__=='__main__':
    main()
