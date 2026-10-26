#!/usr/bin/env python3
"""Auditoria de OGT Control: capturas y comprobaciones UX usando datos simulados."""
from __future__ import annotations

import json
import os
from pathlib import Path
import re
import runpy
import tempfile
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[1]
FIX = runpy.run_path(str(ROOT / "qa/crm-control-scroll-browser.py"))
OUT = Path(os.getenv("OGT_QA_OUTPUT", str(Path(tempfile.gettempdir()) / "ogt-qa-results"))) / "control-ux-audit"
OUT.mkdir(parents=True, exist_ok=True)
VIEWS = ("resumen", "usuarios", "empresas", "denuncias", "publicaciones")
SIZES = ((1440, 900), (1024, 768), (900, 600), (390, 844))

AUDIT_JS = r"""
() => {
  const issues=[];
  const add=(severity,code,detail,selector='')=>issues.push({severity,code,detail,selector});
  const visible=el=>{
    if(el.closest('[hidden],[inert],[aria-hidden="true"]')) return false;
    const st=getComputedStyle(el),r=el.getBoundingClientRect();
    return st.display!=='none' && st.visibility!=='hidden' && r.width>0 && r.height>0;
  };
  const expected={
    resumen:'qs_feed.svg',usuarios:'qs_user.svg',empresas:'qs_form.svg',
    denuncias:'qs_notifications.svg',publicaciones:'qs_anecdote.svg'
  };
  const doc=document.documentElement,desktop=innerWidth>=801;
  if(doc.scrollWidth>innerWidth+3)
    add(desktop?'error':'warning','DOCUMENT_WIDTH','Contenido desborda horizontalmente la ventana.','html');
  if(desktop && doc.scrollHeight>innerHeight+3)
    add('error','DOCUMENT_HEIGHT','La pagina crece por fuera del viewport.','html');
  const nav=[...document.querySelectorAll('.crm-nav-btn')].filter(visible);
  const active=nav.filter(b=>b.getAttribute('aria-current')==='page');
  if(nav.length!==5) add('error','NAV_MISSING','OGT Control debe tener cinco secciones.','.crm-nav');
  if(active.length!==1) add('error','NAV_CURRENT','Debe existir una sola seccion activa.','.crm-nav');
  const seen=new Set();
  for(const button of nav){
    const key=button.dataset.section;
    if(seen.has(key)) add('warning','NAV_DUPLICATED',key,'.crm-nav');
    seen.add(key);
    const icon=button.querySelector('img'),file=expected[key];
    if(file && (!icon || !icon.src.endsWith('/'+file)))
      add('error','INCORRECT_NAV_ICON','Icono incorrecto de '+key+' (esperado '+file+').','.crm-nav-btn');
  }
  for(const image of [...document.querySelectorAll(
    '.crm-nav-btn img,.crm-actions img,.crm-bulk img,.crm-dialog-head img'
  )].filter(visible)){
    if(image.complete && image.naturalWidth===0)
      add('error','BROKEN_ICON','No carga '+image.getAttribute('src'),'.crm-icon');
  }
  for(const button of [...document.querySelectorAll('button')].filter(visible)){
    const name=(button.getAttribute('aria-label')||button.textContent||'').trim();
    if(!name) add('error','UNLABELLED_ACTION','Boton sin nombre accesible.',button.className);
    if(button.matches('.crm-icon-btn,.crm-user-bulk-icon')){
      const r=button.getBoundingClientRect();
      if(r.width<40||r.height<40)
        add('warning','SMALL_ACTION_TARGET',name+' tiene menos de 40x40 px.','.crm-icon-btn');
      if(!button.querySelector('img'))
        add('error','ICON_MISSING','Accion sin icono: '+name,'.crm-icon-btn');
      if(button.matches('.crm-user-bulk-icon') && button.textContent.trim())
        add('error','TEXT_BULK_ACTION','Accion masiva debe ser icono, no texto.','.crm-user-bulk-icon');
    }
  }
  const panel=document.querySelector('.crm-panel');
  if(panel && desktop){
    const r=panel.getBoundingClientRect();
    if(r.bottom>innerHeight+2 || r.right>innerWidth+2)
      add('error','CARD_OFFSCREEN','Tarjeta excede el viewport.','.crm-panel');
    if(parseFloat(getComputedStyle(panel).borderTopLeftRadius)<12)
      add('warning','CARD_CORNERS','Tarjeta sin bordes redondeados.','.crm-panel');
  }
  const region=document.querySelector('.crm-panel>.crm-table-wrap:not([hidden])');
  if(region && panel && visible(region) && desktop){
    const a=region.getBoundingClientRect(),b=panel.getBoundingClientRect();
    if(a.bottom>b.bottom+2 || a.right>b.right+2)
      add('error','TABLE_OUTSIDE_CARD','Tabla fuera de contenedor.','.crm-table-wrap');
    if(region.querySelectorAll('tbody tr').length>=15 &&
       getComputedStyle(region).overflowY==='visible')
      add('error','UNBOUNDED_TABLE','La tabla larga no tiene scroll interno.','.crm-table-wrap');
  }
  const modal=document.querySelector('dialog.crm-user-moderation-dialog[open]');
  if(modal){
    const r=modal.getBoundingClientRect(),radius=parseFloat(getComputedStyle(modal).borderTopLeftRadius);
    if(!modal.matches(':modal')||modal.getAttribute('aria-modal')!=='true')
      add('error','DIALOG_NOT_FOREGROUND','Confirmacion no esta en primer plano.','dialog');
    if(radius<20) add('error','DIALOG_SQUARE','Confirmacion sin bordes redondeados.','dialog');
    if(r.left< -2||r.top< -2||r.right>innerWidth+2||r.bottom>innerHeight+2)
      add('error','DIALOG_CLIPPED','Confirmacion recortada por la ventana.','dialog');
    if(![...modal.querySelectorAll('button')].some(b=>b.textContent.trim()==='Cancelar'))
      add('error','DIALOG_NO_CANCEL','Confirmacion sin Cancelar.','dialog');
  }
  const headings=[...document.querySelectorAll('.crm-page-head,.crm-panel>h3')]
    .filter(visible).map(e=>e.textContent).join(' ');
  if(/\b(undefined|null|NaN|TODO|Lorem ipsum)\b/i.test(headings))
    add('warning','SUSPICIOUS_COPY','Texto de relleno o tecnico visible.','.crm-page-head');
  return issues;
}
"""


def mock_source() -> str:
    """Ejecutar componentes CSS/JS reales, pero jamas leer datos del servidor."""
    src = FIX["FIXTURE"]
    edits = (
        ("window.fixture = { size: 60, fail: false, reads: 0 };",
         "window.fixture = { size: 60, fail: false, reads: 0, moderations: [] };"),
        ("const specs=sections.map(([id,label])=>({id,label,icon:'qs_eye.svg',",
         "const icons={resumen:'qs_feed.svg',usuarios:'qs_user.svg',empresas:'qs_form.svg',"
         "denuncias:'qs_notifications.svg',publicaciones:'qs_anecdote.svg'};\n"
         "const specs=sections.map(([id,label])=>({id,label,icon:icons[id],"),
        ("...(users ? {bulkReview:{icon:'qs_eye.svg',label:'Revisar seleccionados'}} : {}),",
         "...(users ? {bulkReview:{icon:'qs_eye.svg',label:'Revisar seleccionados'},"
         "userActions:{block:true,delete:true},async bulkModerate(action,users){"
         "window.fixture.moderations.push({action,ids:users.map(u=>u.id)});}} : {}),"),
        ("actions:[{icon:'qs_eye.svg',",
         "actions:[...(users?[{icon:'qs_publish_block.svg',"
         "label:'Bloquear publicaciones de '+user.displayName,name:'userBlock'},"
         "{icon:'qs_trash.png',label:'Eliminar usuario '+user.displayName,"
         "name:'userDelete',danger:true}]:[]),{icon:'qs_eye.svg',"),
    )
    for before,after in edits:
        if before not in src:
            raise RuntimeError("Fixture changed: "+before[:60])
        src=src.replace(before,after,1)
    return src


def local_route(route,source: str) -> None:
    if urlsplit(route.request.url).path=="/fixture.js":
        route.fulfill(status=200,content_type="text/javascript",body=source)
    else:
        FIX["route_local"](route)


def capture(page,screen: str,state: str,size: str,issues: list,shots: list) -> None:
    issues.extend({**issue,"screen":screen,"state":state,"viewport":size}
                  for issue in page.evaluate(AUDIT_JS))
    filename=size+"--"+state+".png"
    page.screenshot(path=str(OUT/filename),full_page=False,animations="disabled",caret="hide")
    shots.append({"screen":screen,"state":state,"viewport":size,"file":filename})


def inspect_states(page,size: str,issues: list,shots: list) -> None:
    """Estados relevantes: seleccion, dialogos, filtros, ficha. Nunca confirmar."""
    if size not in ("1440x900","390x844"):
        return
    page.locator('.crm-nav-btn[data-section="usuarios"]').click()
    first=page.locator('.crm-table-wrap:not([hidden]) tbody tr').first
    first.locator('input[type="checkbox"]').check()
    capture(page,"usuarios","usuarios-seleccion",size,issues,shots)
    for label,state,close_with_escape in (
        ("Bloquear publicaciones","usuarios-bloqueo",False),
        ("Eliminar usuarios","usuarios-eliminacion",True),
    ):
        trigger=page.get_by_role("button",name=label,exact=True)
        expect(trigger).to_be_visible()
        trigger.click()
        dialog=page.locator(".crm-user-moderation-dialog")
        expect(dialog).to_be_visible()
        capture(page,"usuarios",state,size,issues,shots)
        if close_with_escape:
            page.keyboard.press("Escape")
        else:
            dialog.get_by_role("button",name="Cancelar").click()
        expect(dialog).to_be_hidden()
    assert page.evaluate("() => window.fixture.moderations.length")==0, (
        "A simulated destructive action happened before confirmation"
    )
    first.get_by_role("button",name="Ver usuario").click()
    profile=page.locator(".crm-profile-dialog")
    expect(profile).to_be_visible()
    capture(page,"usuarios","usuarios-ficha",size,issues,shots)
    profile.get_by_role("button",name="Cerrar").click()
    expect(profile).to_be_hidden()

    for label,state in (("Filtros","usuarios-filtros"),("Orden","usuarios-orden")):
        button=page.get_by_role("button",name=re.compile("^"+label)).first
        expect(button).to_be_visible()
        button.click()
        capture(page,"usuarios",state,size,issues,shots)
        button.click()


def report(issues: list,shots: list) -> int:
    error_count=sum(x["severity"]=="error" for x in issues)
    warning_count=sum(x["severity"]=="warning" for x in issues)
    data={"repository":"romancanoniero/OGT","sourceSha":os.getenv("GITHUB_SHA"),
          "scope":"OGT Control web, API e identidad simuladas, nunca datos reales",
          "views":VIEWS,"sizes":[str(a)+"x"+str(b) for a,b in SIZES],
          "errors":error_count,"warnings":warning_count,
          "screenshots":shots,"findings":issues,
          "subjective":"La pertinencia y estetica requieren revisar las capturas por separado."}
    (OUT/"findings.json").write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    lines=["# Auditoria de OGT Control","","**"+str(error_count)+
           " errores · "+str(warning_count)+" observaciones · "+str(len(shots))+
           " capturas**","","| Tipo | Seccion | Resolucion | Regla | Observacion |",
           "| --- | --- | --- | --- | --- |"]
    for x in issues:
        fields=[x["severity"],x.get("screen",""),x.get("viewport",""),
                x["code"],x["detail"].replace("|","/")[:140]]
        lines.append("| "+" | ".join(fields)+" |")
    lines.extend(["","## Capturas adjuntas","",
                  "| Estado | Resolucion | Archivo |",
                  "| --- | --- | --- |"])
    for x in shots:
        lines.append("| "+x["state"]+" | "+x["viewport"]+" | "+x["file"]+" |")
    lines.extend(["","Los hallazgos automaticos son objetivos y limitados.",
                  "Para detectar elementos desagradables o irrelevantes que no cumplen",
                  "una regla objetiva, revisar las PNG con criterio de diseño.",
                  "No prueba Firebase MFA ni datos privados de Contabo.",""])
    (OUT/"report.md").write_text("\n".join(lines),encoding="utf-8")
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a",encoding="utf-8") as out:
            out.write("\n"+"\n".join(lines[:6])+"\n")
            out.write("\nRevisar el informe y las capturas en el artefacto de QA.\n")
    print(json.dumps({"errors":error_count,"warnings":warning_count,
        "screenshots":len(shots),"report":str(OUT/"report.md"),
        "codes":sorted({x["code"] for x in issues})},ensure_ascii=False),flush=True)
    return 1 if error_count or not shots else 0


def main() -> int:
    source=mock_source()
    issues,shots=[],[]
    with sync_playwright() as pw:
        binary=os.environ.get("CRM_CHROMIUM_PATH")
        browser=pw.chromium.launch(headless=True,
            **({"executable_path":binary} if binary else {}))
        try:
            for width,height in SIZES:
                size=str(width)+"x"+str(height)
                context=browser.new_context(viewport={"width":width,"height":height},
                                            locale="es-AR",reduced_motion="reduce")
                context.route("**/*",lambda route:local_route(route,source))
                page=context.new_page()
                page.set_default_timeout(7000)
                errors=[]
                page.on("pageerror",lambda error:errors.append(str(error)))
                try:
                    page.goto(FIX["BASE"]+"/crm/control/fixture")
                    page.wait_for_function("window.fixtureReady === true")
                    page.evaluate("async () => { await document.fonts.ready; return true; }")
                    for section in VIEWS:
                        page.locator('.crm-nav-btn[data-section="'+section+'"]').click()
                        expect(page.locator('.crm-nav-btn[aria-current="page"]')).to_have_attribute(
                            "data-section",section)
                        capture(page,section,section,size,issues,shots)
                    inspect_states(page,size,issues,shots)
                except Exception as exc:
                    issues.append({"severity":"error","code":"BROWSER_AUDIT_FAILED",
                        "detail":type(exc).__name__+": "+str(exc)[:340],
                        "screen":"recorrido","state":"recorrido","viewport":size})
                    page.screenshot(path=str(OUT/(size+"--error.png")),full_page=False)
                finally:
                    issues.extend({"severity":"error","code":"PAGE_JS_ERROR",
                        "detail":str(error)[:320],"screen":"recorrido",
                        "state":"ejecucion","viewport":size} for error in errors)
                    context.close()
        finally:
            browser.close()
    return report(issues,shots)


if __name__=="__main__":
    raise SystemExit(main())
