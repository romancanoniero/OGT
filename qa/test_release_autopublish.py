"""Contract tests for the real workflow gate; never access production."""
import ast
import itertools
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE = (ROOT / '.github/workflows/ogt-release.yml').read_text(encoding='utf-8')
PUBLISH = SOURCE.split('\n  publish:\n', 1)[1]
MATCH = re.search(r'^    if: >-\n((?:      .+\n)+)', PUBLISH, re.MULTILINE)
if MATCH is None:
    raise ValueError('Missing explicit publish authorization gate')
EXPRESSION = ' '.join(line.strip() for line in MATCH.group(1).splitlines())


def allowed(event, ref, repo, confirm, success):
    expression = EXPRESSION.replace('&&', ' and ').replace('||', ' or ')
    values = {'github.event_name': event, 'github.ref': ref,
              'github.repository': repo, 'inputs.confirm': confirm,
              'success()': success}
    for key, value in values.items():
        expression = expression.replace(key, repr(value))

    def evaluate(node):
        if isinstance(node, ast.Constant):
            return node.value
        if isinstance(node, ast.BoolOp):
            items = [evaluate(value) for value in node.values]
            if isinstance(node.op, ast.And):
                return all(items)
            if isinstance(node.op, ast.Or):
                return any(items)
        if isinstance(node, ast.Compare) and len(node.ops) == 1 and isinstance(node.ops[0], ast.Eq):
            return evaluate(node.left) == evaluate(node.comparators[0])
        raise ValueError('Unsupported authorization expression: ' + ast.dump(node))

    return evaluate(ast.parse(expression, mode='eval').body)


class AutomaticPublicationTests(unittest.TestCase):
    def test_public_sdk_uses_the_run_scoped_read_token_in_both_builds(self):
        checkouts = SOURCE.split('repository: romancanoniero/db-kmp-sdk\n')[1:]
        self.assertEqual(len(checkouts), 2)
        for checkout in checkouts:
            settings = checkout.split('      - ', 1)[0]
            self.assertIn('token: ${{ github.token }}', settings)
            self.assertIn('persist-credentials: false', settings)
            self.assertIn('0a881b7a15c9a7d99b77da8547aabe2dd493a8b0', settings)
        self.assertNotIn('OGT_SDK_READ_TOKEN', SOURCE)
        self.assertNotIn('OGT_SDK_CHECKOUT_TOKEN', SOURCE)

    def test_activation_checks_the_account_routes_required_after_mfa(self):
        activation = (ROOT / 'scripts/activate-ogt-release.sh').read_text(encoding='utf-8')
        self.assertIn('for path in me/consent me/consent/accept me/settings; do', activation)
        self.assertIn('http://127.0.0.1:19081/api/v1/$path', activation)
        self.assertIn('test "$code" = 401', activation)

    def test_event_branch_repository_and_success_matrix(self):
        combinations = itertools.product(
            ['push', 'workflow_dispatch', 'pull_request', 'pull_request_target', 'schedule'],
            ['refs/heads/main', 'refs/heads/release/example', 'refs/pull/58/merge', 'refs/tags/v1'],
            ['romancanoniero/OGT', 'other/OGT'],
            ['', 'SOLO_COMPROBAR', 'PUBLICAR_PRIMERA_VERSION'], [False, True])
        for event, ref, repo, confirm, success in combinations:
            expected = (success and ref == 'refs/heads/main' and repo == 'romancanoniero/OGT'
                        and (event == 'push' or (event == 'workflow_dispatch'
                             and confirm == 'PUBLICAR_PRIMERA_VERSION')))
            with self.subTest(event=event, ref=ref, repo=repo, confirm=confirm, success=success):
                self.assertEqual(allowed(event, ref, repo, confirm, success), expected)

    def test_validation_environment_and_serialization_remain(self):
        self.assertIn('needs: [checks, browser, backend, database]', PUBLISH)
        self.assertIn('  backend:\n', SOURCE)
        self.assertIn(':backend:test', SOURCE)
        self.assertIn('  database:\n', SOURCE)
        self.assertIn('qa/crm-user-moderation-db-smoke.sql', SOURCE)
        self.assertIn('environment:\n      name: ogt-launch', PUBLISH)
        self.assertIn("cancel-in-progress: ${{ github.event_name == 'pull_request' }}", SOURCE)
        self.assertIn("group: ogt-release-${{ github.ref }}", SOURCE)
        self.assertIn("branches: [main, 'release/**']", SOURCE)
        self.assertNotIn('head_commit.message', EXPRESSION)
        self.assertNotIn('continue-on-error', SOURCE)
        self.assertIn('StrictHostKeyChecking yes', PUBLISH)
        self.assertIn('bash scripts/deploy-ogt-vps.sh --deploy', PUBLISH)
        self.assertIn('ref: ${{ github.sha }}', PUBLISH)

    def test_confirmation_follows_successful_remote_verification(self):
        self.assertIn("data.get('sourceSha') == os.environ['EXPECTED_SHA']", PUBLISH)
        self.assertIn("data.get('repository') == 'romancanoniero/OGT'", PUBLISH)
        self.assertIn("data.get('scope') == 'app-backend-crms'", PUBLISH)
        self.assertLess(PUBLISH.index('/health'), PUBLISH.index("echo 'verified=true'"))
        self.assertLess(PUBLISH.index("echo 'verified=true'"), PUBLISH.index('Record verified Contabo deployment'))
        self.assertEqual(PUBLISH.count("if: success() && steps.verify.outputs.verified == 'true'"), 2)
        self.assertIn('if-no-files-found: error', PUBLISH)
        self.assertIn('retention-days: 30', PUBLISH)
        self.assertIn('name: Remove SSH credentials\n        if: always()', PUBLISH)


if __name__ == '__main__':
    unittest.main()
