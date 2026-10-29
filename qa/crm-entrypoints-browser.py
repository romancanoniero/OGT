"""Public-entrypoint regressions. All identity, assets and HTTP are local fixtures.
These checks cannot certify Contabo, Firebase, or a real backend deployment.
"""
import os
from pathlib import Path
import unittest
from urllib.parse import urlsplit
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
INDEX = (ROOT / 'web/app/index.html').read_text(encoding='utf-8')
BOOT = (ROOT / 'web/app/bootstrap.js').read_text(encoding='utf-8')
SVG = '<svg xmlns="http://www.w3.org/2000/svg"><symbol id="qa-icon" viewBox="0 0 1 1"><path d="M0 0h1v1"/></symbol></svg>'
ERROR_HTML = '<html><body><h1>404 Not Found</h1><hr>nginx/1.28.3</body></html>'


class EntryPoints(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pw = sync_playwright().start()
        executable = os.environ.get('OGT_CHROMIUM_PATH')
        cls.browser = cls.pw.chromium.launch(
            headless=True, **({'executable_path': executable} if executable else {}))

    @classmethod
    def tearDownClass(cls):
        cls.browser.close()
        cls.pw.stop()

    def run_case(self, *, path='/', icons=200, icon_type='image/svg+xml',
                 icon_body=SVG, fail_script=None):
        context = self.browser.new_context()
        self.addCleanup(context.close)
        page = context.new_page()
        requests = []
        errors = []
        page.on('pageerror', lambda error: errors.append(str(error)))

        def route_request(route):
            u = urlsplit(route.request.url)
            requests.append(u.path)
            if u.hostname != 'localhost':
                # Stub external Firebase and fonts; there is no outbound access.
                mime = 'text/css' if u.hostname == 'fonts.googleapis.com' else 'application/javascript'
                route.fulfill(status=200, content_type=mime, body='')
            elif u.path in ['/', '/crm/control/', '/crm/empresas/', '/deep/path/']:
                # Simulate the stale SPA fallback from the reported screenshot.
                route.fulfill(status=200, content_type='text/html', body=INDEX)
            elif u.path == '/bootstrap.js':
                route.fulfill(status=200, content_type='application/javascript', body=BOOT)
            elif u.path == '/icons/qs.svg':
                if icons == 'network-error':
                    route.abort('failed')
                else:
                    route.fulfill(status=icons, content_type=icon_type, body=icon_body)
            elif u.path == fail_script:
                route.fulfill(status=404, content_type='text/html', body=ERROR_HTML)
            elif u.path == '/ogt-sdk.js':
                route.fulfill(status=200, content_type='application/javascript', body='window.qaSdkLoaded = true;')
            elif u.path == '/app.js':
                route.fulfill(status=200, content_type='application/javascript', body='window.qaAppLoaded = window.qaSdkLoaded === true;')
            elif u.path == '/content-attribution.js':
                route.fulfill(status=200, content_type='application/javascript', body=(ROOT/'web/app/content-attribution.js').read_text())
            elif u.path == '/auth.js':
                route.fulfill(status=200, content_type='application/javascript', body='')
            elif u.path == '/styles.css':
                route.fulfill(status=200, content_type='text/css', body='[hidden]{display:none!important}body{font-family:system-ui}')
            else:
                route.fulfill(status=404, content_type='text/plain', body='not found')

        page.route('**/*', route_request)
        page.goto('http://localhost:8765' + path, wait_until='networkidle')
        page.wait_for_function('window.qaAppLoaded === true || !!document.getElementById("ogt-load-error")')
        return page, requests, errors

    def test_valid_svg_and_startup_order(self):
        page, _, errors = self.run_case()
        page.wait_for_selector('#icons svg', state='attached')
        self.assertTrue(page.evaluate('window.qaAppLoaded'))
        self.assertEqual(errors, [])

    def test_html_404_never_becomes_page_content(self):
        page, _, errors = self.run_case(icons=404, icon_type='text/html', icon_body=ERROR_HTML)
        self.assertNotIn('404 Not Found', page.locator('body').inner_text())
        self.assertEqual(page.locator('#icons').inner_html(), '')
        self.assertTrue(page.evaluate('window.qaAppLoaded'))
        self.assertEqual(errors, [])

    def test_html_with_200_is_rejected(self):
        page, _, _ = self.run_case(icon_type='text/html', icon_body=ERROR_HTML)
        self.assertEqual(page.locator('#icons').inner_html(), '')
        self.assertTrue(page.evaluate('window.qaAppLoaded'))

    def test_svg_mime_with_html_body_is_rejected(self):
        page, _, _ = self.run_case(icon_body=ERROR_HTML)
        self.assertEqual(page.locator('#icons').inner_html(), '')
        self.assertNotIn('404 Not Found', page.locator('body').inner_text())

    def test_malformed_svg_is_rejected(self):
        page, _, _ = self.run_case(icon_body='<svg><broken')
        self.assertEqual(page.locator('#icons').inner_html(), '')
        self.assertTrue(page.evaluate('window.qaAppLoaded'))

    def test_network_failure_in_optional_icons_is_not_fatal(self):
        page, _, _ = self.run_case(icons='network-error')
        self.assertTrue(page.evaluate('window.qaAppLoaded'))

    def test_missing_sdk_does_not_start_the_application(self):
        page, requests, _ = self.run_case(fail_script='/ogt-sdk.js')
        self.assertNotIn('/app.js', requests)
        self.assertTrue(page.locator('#app-shell').is_hidden())
        self.assertIn('No se pudieron cargar', page.locator('[role=alert]').inner_text())

    def test_missing_app_is_explained(self):
        page, _, _ = self.run_case(fail_script='/app.js')
        self.assertTrue(page.locator('#app-shell').is_hidden())
        self.assertIn('No se pudieron cargar', page.locator('[role=alert]').inner_text())

    def test_stale_crm_fallback_does_not_start_public_app(self):
        for path in ['/crm/control/', '/crm/empresas/']:
            with self.subTest(path=path):
                page, requests, errors = self.run_case(path=path)
                self.assertTrue(page.locator('#app-shell').is_hidden())
                self.assertIn('web publica en lugar del CRM', page.locator('[role=alert]').inner_text())
                self.assertNotIn('/ogt-sdk.js', requests)
                self.assertNotIn('/icons/qs.svg', requests)
                self.assertFalse(any(p.startswith(path + 'styles.css') for p in requests))
                self.assertEqual(errors, [])

    def test_deep_public_fallback_uses_root_assets(self):
        page, requests, errors = self.run_case(path='/deep/path/')
        self.assertIn('/styles.css', requests)
        self.assertIn('/ogt-sdk.js', requests)
        self.assertIn('/app.js', requests)
        self.assertFalse(any(p.startswith('/deep/path/styles.css') for p in requests))
        self.assertEqual(errors, [])


if __name__ == '__main__':
    unittest.main(verbosity=2)
