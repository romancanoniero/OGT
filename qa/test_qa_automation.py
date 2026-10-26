"""Self-tests for the QA runner: no browser, network or credentials required."""
from pathlib import Path
import json
import tempfile
import unittest
from unittest.mock import patch
import run_automated_tests as qa


class QaAutomationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        (self.root / 'qa').mkdir()
        self.output = self.root / 'results'

    def tearDown(self):
        self.temp.cleanup()

    def script(self, name, content):
        path = self.root / 'qa' / name
        path.write_text(content, encoding='utf-8')
        return 'qa/' + name

    def test_syntax_detects_malformed_python_without_execution(self):
        marker = self.root / 'must-not-exist'
        self.script('good.py', f"open({str(marker)!r}, 'w').close()\n")
        self.assertTrue(qa.check_syntax(self.root))
        self.assertFalse(marker.exists())
        self.script('broken.py', '))\n')
        self.assertFalse(qa.check_syntax(self.root))

    def test_success_is_recorded_and_does_not_create_pyc(self):
        suite = self.script('pass.py', "print('fixture passed')\n")
        result = qa.run_suite(self.root, suite, self.output)
        self.assertEqual(result['status'], 'passed')
        self.assertFalse(list(self.root.rglob('*.pyc')))
        with patch.dict('os.environ', {'GITHUB_STEP_SUMMARY': str(self.root / 'summary.md')}):
            self.assertEqual(qa.write_report([result], self.output), 0)
        self.assertTrue((self.output / 'junit.xml').exists())

    def test_failure_does_not_skip_second_suite_or_turn_green(self):
        first = self.script('fail.py', "raise AssertionError('fixture failure')\n")
        second = self.script('next.py', "print('second suite executed')\n")
        results = [qa.run_suite(self.root, s, self.output) for s in (first, second)]
        with patch.dict('os.environ', {'GITHUB_STEP_SUMMARY': ''}):
            self.assertEqual(qa.write_report(results, self.output), 1)
        report = json.loads((self.output / 'results.json').read_text())
        self.assertEqual(report['failedSuites'], 1)
        self.assertEqual(report['suiteCount'], 2)
        self.assertIn('second suite executed', (self.output / 'next.log').read_text())

    def test_control_ux_audit_in_every_browser_run_and_weekly(self):
        self.assertEqual(qa.UX_SUITE, 'qa/crm-control-ux-audit.py')
        self.assertIn(qa.UX_SUITE, qa.SUITES)
        root = Path(__file__).resolve().parents[1]
        workflow = (root / '.github/workflows/ogt-control-ux-audit.yml').read_text()
        audit = (root / qa.UX_SUITE).read_text()
        standard = (root / 'docs/DATA_VIEWS_STANDARD.md').read_text()
        self.assertIn("cron: '0 11 * * 1'", workflow)
        self.assertIn('python qa/run_automated_tests.py --ux', workflow)
        self.assertIn('actions/upload-artifact@v6', workflow)
        self.assertNotIn('secrets.OGT_VPS_SSH_KEY', workflow)
        self.assertIn('crm-control-scroll-browser.py', audit)
        self.assertIn('findings.json', audit)
        self.assertIn('page.screenshot(', audit)
        self.assertIn('## 17. Auditoría automática', standard)

    def test_timeout_is_failure(self):
        suite = self.script('slow.py', 'import time\ntime.sleep(5)\n')
        result = qa.run_suite(self.root, suite, self.output, timeout=0.1)
        self.assertEqual(result['status'], 'timeout')
        self.assertEqual(result['exitCode'], 124)


if __name__ == '__main__':
    unittest.main()
