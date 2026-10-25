"""Run repository QA without production credentials and retain truthful results."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SUITES = ('qa/crm-browser.py', 'qa/crm-entrypoints-browser.py', 'qa/crm-layout-browser.py')


def check_syntax(root: Path) -> bool:
    """Compile all QA and deployment Python sources without importing or writing pyc."""
    paths = sorted(p for folder in ('qa', 'scripts') for p in (root / folder).rglob('*.py'))
    if not paths:
        print('ERROR: no Python sources found', file=sys.stderr)
        return False
    failures = []
    for path in paths:
        try:
            compile(path.read_bytes(), str(path.relative_to(root)), 'exec')
        except (SyntaxError, ValueError, OSError) as exc:
            failures.append(f'{path.relative_to(root)}: {exc}')
    for error in failures:
        print(error, file=sys.stderr)
    print(f'Python syntax: {len(paths)} files, {len(failures)} errors')
    return not failures


def run_suite(root: Path, suite: str, output: Path, timeout: float = 300) -> dict:
    """Execute once; never retry an assertion failure to manufacture a green result."""
    output.mkdir(parents=True, exist_ok=True)
    log_path = output / (Path(suite).stem + '.log')
    started = time.monotonic()
    status, code = 'failed', 1
    env = {**os.environ, 'PYTHONDONTWRITEBYTECODE': '1', 'PYTHONUNBUFFERED': '1',
           'OGT_QA_OUTPUT': str(output.resolve())}
    env.pop('PYTHONOPTIMIZE', None)
    with log_path.open('w', encoding='utf-8') as log:
        try:
            with subprocess.Popen(
                [sys.executable, '-B', str(root / suite)], cwd=root,
                env=env, stdout=log, stderr=subprocess.STDOUT,
                start_new_session=(os.name == 'posix'),
            ) as process:
                try:
                    code = process.wait(timeout=timeout)
                    status = 'passed' if code == 0 else 'failed'
                except subprocess.TimeoutExpired:
                    if os.name == 'posix':
                        try:
                            os.killpg(process.pid, signal.SIGKILL)
                        except ProcessLookupError:
                            pass
                    else:
                        process.kill()
                    process.wait()
                    status, code = 'timeout', 124
                    log.write(f'\nSuite exceeded {timeout} seconds.\n')
        except OSError as exc:
            log.write(f'Could not execute suite: {exc}\n')
    result = {'suite': suite, 'status': status, 'exitCode': code,
              'durationSeconds': round(time.monotonic() - started, 3), 'log': log_path.name}
    print(f"{suite}: {status} (exit {code})", flush=True)
    # Preserve the original locator/traceback diagnostics in the Actions console.
    print(log_path.read_text(encoding='utf-8', errors='replace')[-20000:], flush=True)
    return result


def write_report(results: list[dict], output: Path) -> int:
    failures = sum(result['status'] != 'passed' for result in results)
    report = {'repository': 'romancanoniero/OGT', 'sourceSha': os.getenv('GITHUB_SHA'),
              'scope': 'browser fixtures; not live Firebase, mobile upload or Contabo',
              'suiteCount': len(results), 'failedSuites': failures, 'results': results}
    (output / 'results.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    xml = ET.Element('testsuite', name='OGT browser QA', tests=str(len(results)), failures=str(failures))
    for result in results:
        case = ET.SubElement(xml, 'testcase', name=result['suite'], time=str(result['durationSeconds']))
        if result['status'] != 'passed':
            failure = ET.SubElement(case, 'failure', message=result['status'])
            log = (output / result['log']).read_text(encoding='utf-8', errors='replace')[-20000:]
            failure.text = re.sub(r'[\x00-\x08\x0b\x0c\x0e-\x1f]', '', log)
    ET.ElementTree(xml).write(output / 'junit.xml', encoding='utf-8', xml_declaration=True)
    summary = ['## OGT browser QA', '', '| Suite | Result | Seconds |', '| --- | --- | --- |']
    summary.extend(f"| `{r['suite']}` | {r['status']} | {r['durationSeconds']} |" for r in results)
    summary.extend(['', 'Identity and API are simulated. This does not confirm a deployment.', ''])
    markdown = '\n'.join(summary)
    (output / 'summary.md').write_text(markdown, encoding='utf-8')
    if os.getenv('GITHUB_STEP_SUMMARY'):
        with Path(os.environ['GITHUB_STEP_SUMMARY']).open('a', encoding='utf-8') as stream:
            stream.write(markdown)
    return 1 if failures or not results else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group(required=True)
    modes.add_argument('--syntax', action='store_true')
    modes.add_argument('--browser', action='store_true')
    parser.add_argument('--output', type=Path, default=Path(tempfile.gettempdir()) / 'ogt-qa-results')
    args = parser.parse_args()
    if not __debug__:
        parser.error('Run QA without -O: assertions must remain enabled')
    if not check_syntax(ROOT):
        return 1
    if args.syntax:
        return 0
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    # All suites run even if one fails; the final status keeps every failure.
    results = [run_suite(ROOT, suite, output) for suite in SUITES]
    return write_report(results, output)


if __name__ == '__main__':
    raise SystemExit(main())
