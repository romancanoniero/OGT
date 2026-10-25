"""Read-only browser smoke against the DEPLOYED OGT origin.

This is not authenticated acceptance or a replacement for Firebase/TOTP/tenant
E2E. Intended to run from a network explicitly allowlisted for prelaunch.
Never log in, submit forms, mutate production data or bypass access controls.
"""
import json
import os
from pathlib import Path
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright

ORIGIN = os.environ.get("OGT_QA_ORIGIN", "https://onlygoodthings.lat").rstrip("/")
url = urlsplit(ORIGIN)
if url.scheme != "https" or url.hostname not in ("onlygoodthings.lat", "www.onlygoodthings.lat") or url.username or url.password or url.port:
    raise SystemExit("OGT_QA_ORIGIN must be the verified HTTPS OGT origin")

OUT = Path(os.environ.get("OGT_QA_SCREENSHOTS", "qa-artifacts/guest-smoke"))
OUT.mkdir(parents=True, exist_ok=True)
checks = [
    ("publica", "/", "public"),
    ("control", "/crm/control/", "control"),
    ("empresas", "/crm/empresas/", "empresa"),
]
results = []
with sync_playwright() as pw:
    browser = pw.chromium.launch(headless=True)
    try:
        for label, path, mode in checks:
            context = browser.new_context(locale="es-AR", viewport={"width": 1365, "height": 900})
            page = context.new_page()
            record = {"module": label, "url": ORIGIN + path, "result": "failed"}
            try:
                response = page.goto(ORIGIN + path, wait_until="domcontentloaded", timeout=30000)
                status = response.status if response else 0
                record["httpStatus"] = status
                if status in (401, 403, 429):
                    raise AssertionError(f"Acceso previo al lanzamiento bloqueado: HTTP {status}. Autorizar IP del revisor; no abrir acceso público para CI.")
                if status != 200:
                    raise AssertionError(f"HTTP {status}: la ruta publicada no responde 200")
                if mode == "public":
                    page.locator("#app-shell").wait_for(state="attached", timeout=15000)
                    if page.locator('script[src*="/bootstrap.js"]').count() == 0:
                        raise AssertionError("La portada no carga bootstrap.js")
                else:
                    actual = page.locator("html").get_attribute("data-crm")
                    if actual != mode:
                        raise AssertionError(f"El servidor devolvió {actual!r} en lugar del CRM {mode!r} (posible SPA fallback)")
                    page.locator("#crm-login input[type=email]").wait_for(state="attached", timeout=15000)
                    page.locator("#crm-login input[type=password]").wait_for(state="attached", timeout=15000)
                    if page.locator('script[src*="crm.js"]').count() == 0:
                        raise AssertionError("Falta la inicialización del CRM")
                    # These are entrypoint checks only; no protected sections accessed.
                record["result"] = "passed"
            except Exception as exc:
                record["error"] = str(exc)[:600]
            finally:
                page.screenshot(path=str(OUT / f"{label}.png"), full_page=True)
                results.append(record)
                context.close()
    finally:
        browser.close()
print(json.dumps({"kind": "guest-browser-smoke", "identity": "unauthenticated", "source": ORIGIN, "results": results}, ensure_ascii=False, indent=2))
if any(item["result"] != "passed" for item in results):
    raise SystemExit(1)
