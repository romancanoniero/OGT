import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import ogt_operations as ops

URL='https://hc-ping.com/10000000-0000-4000-8000-000000000001'

class HeartbeatTests(unittest.TestCase):
    def config(self, root, url=URL):
        path=Path(root)/'private.json';path.write_text(json.dumps({'url':url}));path.chmod(0o600);return path
    def test_without_configuration_no_network_or_existing_behavior_changes(self):
        with tempfile.TemporaryDirectory() as root:
            self.assertIsNone(ops.external_heartbeat({'status':'healthy'},Path(root)/'absent',lambda _:self.fail('Unexpected network')))
    def test_success_and_failure_use_only_the_reviewed_provider_and_safe_aggregate(self):
        with tempfile.TemporaryDirectory() as root:
            path=self.config(root);sent=[]
            def send(request):sent.append(request);return 200
            self.assertEqual({'state':'healthy','delivered':True},ops.external_heartbeat({'status':'healthy','privateUser':'NEVER_SEND'},path,send))
            self.assertEqual(URL,sent[-1].full_url)
            result=ops.external_heartbeat({'status':'critical','alerts':[{'code':'DATABASE_FAILED','private':'NEVER_SEND'},{'code':'PRIVATE_IDENTIFIER'}]},path,send)
            self.assertEqual({'state':'failed','delivered':True},result)
            self.assertEqual(URL+'/fail',sent[-1].full_url)
            self.assertEqual({'service':'OGT','status':'failed','alerts':['DATABASE_FAILED']},json.loads(sent[-1].data))
            self.assertNotIn('NEVER_SEND',str([request.data for request in sent]))
    def test_transient_probe_failure_preserves_existing_debounce(self):
        with tempfile.TemporaryDirectory() as root:
            result=ops.external_heartbeat({'status':'warning','alerts':[]},self.config(root),lambda _:self.fail('Premature alarm'))
            self.assertEqual({'state':'debouncing','delivered':False},result)
    def test_url_validation_rejects_redirects_ports_credentials_queries_and_other_hosts(self):
        for value in ['http://hc-ping.com/'+URL.rsplit('/',1)[1],URL+'?token=private',URL+'#private',URL+'/fail',URL.replace('hc-ping.com','hc-ping.com.evil.test'),URL.replace('hc-ping.com','user@hc-ping.com'),URL.replace('hc-ping.com','hc-ping.com:443'),'https://127.0.0.1/private','https://hc-ping.com/not-a-uuid']:
            with self.subTest(value=value),self.assertRaises(ValueError):ops.heartbeat_url(value)
    def test_transport_errors_and_rejected_responses_never_expose_the_capability(self):
        with tempfile.TemporaryDirectory() as root:
            path=self.config(root)
            def broken(_):raise RuntimeError(URL+' PRIVATE_RESPONSE')
            for send in [broken,lambda _:302,lambda _:503]:
                result=ops.external_heartbeat({'status':'healthy'},path,send)
                self.assertEqual({'state':'delivery_failed','delivered':False},result)
                self.assertNotIn(URL,json.dumps(result))
    def test_public_or_symlinked_secret_files_are_not_read_or_used(self):
        with tempfile.TemporaryDirectory() as root:
            path=self.config(root);link=Path(root)/'link';link.symlink_to(path)
            send=lambda _:self.fail('Unsafe secret file used')
            self.assertEqual('delivery_failed',ops.external_heartbeat({'status':'healthy'},link,send)['state'])
            path.chmod(0o644)
            self.assertEqual('delivery_failed',ops.external_heartbeat({'status':'healthy'},path,send)['state'])
    def test_recovery_ping_is_healthy_after_a_failed_state(self):
        with tempfile.TemporaryDirectory() as root:
            sent=[];path=self.config(root)
            send=lambda request:sent.append(request.full_url) or 200
            ops.external_heartbeat({'status':'critical','alerts':[{'code':'AUTHORIZATION_FAILED'}]},path,send)
            ops.external_heartbeat({'status':'healthy','alerts':[]},path,send)
            self.assertEqual([URL+'/fail',URL],sent)

if __name__=='__main__':unittest.main()
