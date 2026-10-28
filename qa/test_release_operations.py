import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import subprocess
import shutil
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import ogt_operations as ops
import install_ogt_operations as install
import restore_ogt_application as recovery

class OperationsTests(unittest.TestCase):
    def healthy(self):return {name:{'ok':True} for name in ('containers','application','authorization','database','redis','release','tls','capacity','backups')}
    def test_failure_is_visible_immediately_and_incident_requires_three_failed_samples(self):
        checks=self.healthy();checks['database']={'ok':False}
        first=ops.assess(checks,{},100);self.assertEqual('warning',first['status']);self.assertEqual([],first['alerts'])
        second=ops.assess(checks,first,160);third=ops.assess(checks,second,220)
        self.assertEqual('critical',third['status']);self.assertEqual('DATABASE_FAILED',third['alerts'][0]['code'])
        self.assertEqual('healthy',ops.assess(self.healthy(),third,280)['status'])
    def test_authorization_is_immediately_critical_and_queue_and_capacity_signals_are_visible(self):
        checks=self.healthy();checks['authorization']={'ok':False,'securityViolation':True};checks['database'].update(erasureOverdue=1,paymentIncidents=2);checks['capacity'].update(diskUsedPercent=86,memoryAvailablePercent=9)
        result=ops.assess(checks,{},100);self.assertEqual('critical',result['status'])
        self.assertEqual({'AUTHORIZATION_FAILED','ERASUREOVERDUE','PAYMENTINCIDENTS','DISK_CAPACITY','MEMORY_CAPACITY'},{a['code'] for a in result['alerts']})
    def test_probe_failures_never_serialize_sensitive_errors(self):
        def broken():raise ValueError('PRIVATE_CREDENTIAL_DO_NOT_PRINT')
        self.assertEqual({'ok':False},ops.safe_probe(broken))
        self.assertNotIn('PRIVATE',json.dumps(ops.safe_probe(broken)))
    def test_atomic_snapshots_refuse_symlinks_and_keep_original_target_untouched(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);target=root/'protected';target.write_text('protected');link=root/'status.json';link.symlink_to(target)
            with self.assertRaises(ValueError):ops.atomic(link,'changed')
            self.assertEqual('protected',target.read_text())
            ops.atomic(root/'actual.json','valid');self.assertEqual('valid',(root/'actual.json').read_text())
            self.assertEqual(0o600,(root/'actual.json').stat().st_mode&0o777)
    def test_cron_installation_preserves_every_unrelated_job_and_is_idempotent(self):
        before='MAILTO=private@example.test\n12 * * * * private-command --credential-file /private/key\n# preserved comment\n'
        after=install.cron_with_block(before);self.assertTrue(after.startswith(before));self.assertEqual(after,install.cron_with_block(after))
        self.assertEqual(before,after.replace(install.BLOCK,''))
        for invalid in (install.BEGIN+'\n',install.END+'\n',install.BLOCK+install.BLOCK,install.END+'\n'+install.BEGIN+'\n'):
            with self.assertRaises(ValueError):install.cron_with_block(invalid)
    def test_executable_tools_do_not_add_bytecode_to_a_sealed_release(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            names=['install_ogt_operations.py','ogt_backup.py','ogt_operations.py','restore_ogt_application.py','release_guard.py']
            for name in names:shutil.copy2(ROOT/'scripts'/name,root/name)
            for name in names[:4]:
                subprocess.run([sys.executable,str(root/name),'--help'],capture_output=True,check=True)
            self.assertEqual(set(names),{p.name for p in root.iterdir()})
    def test_no_external_notification_destination_or_data_mutation_is_in_the_collector(self):
        sql=ops.SQL.upper();self.assertIn('BEGIN READ ONLY',sql);self.assertIn('STATEMENT_TIMEOUT',sql)
        for word in ('UPDATE ','DELETE ','INSERT ','TRUNCATE '):self.assertNotIn(word,sql)
        source=(ROOT/'scripts/ogt_operations.py').read_text();self.assertNotIn('hooks.slack',source);self.assertNotIn('sendmail',source)

class RecoveryTests(unittest.TestCase):
    def test_recovery_rejects_changed_schema_before_container_changes(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'current-release.json').write_text(json.dumps({'sourceSha':'a'*40,'files':{'database/migrations/35.sql':'current'}}))
            target={'files':{'database/migrations/35.sql':'different'}}
            with patch.object(recovery,'validate_context',return_value=(root,'ogt','ogt-backend')),patch.object(recovery,'package',return_value=target),patch.object(recovery,'check_runtime'),patch.object(recovery,'run') as run:
                with self.assertRaisesRegex(ValueError,'SCHEMA_COMPATIBILITY'):recovery.plan(root,'b'*40+'-20261028T000000Z','a'*40)
                run.assert_not_called()
    def test_runtime_drift_and_unknown_files_are_not_deleted(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)
            for name in recovery.MANAGED:
                if '.' in name:(root/name).write_text('original')
                else:(root/name).mkdir()
            files={p.relative_to(root).as_posix():recovery.digest(p) for p in root.rglob('*') if p.is_file()}
            recovery.check_runtime(root,{'files':files})
            extra=root/'web-app'/'user-custom.txt';extra.write_text('protected')
            with self.assertRaisesRegex(ValueError,'DRIFT'):recovery.check_runtime(root,{'files':files})
            self.assertEqual('protected',extra.read_text())
    def test_copy_failure_occurs_before_any_stop_or_image_mutation(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder);(root/'backups').mkdir();(root/'releases'/'target'/'backend-dist').mkdir(parents=True)
            info={'root':str(root),'targetReleaseId':'target','targetSha':'a'*40}
            with patch.object(recovery,'package',return_value={}),patch.object(recovery.shutil,'copytree',side_effect=OSError('disk full')),patch.object(recovery,'run') as run:
                with self.assertRaises(OSError):recovery.apply(info,'Ensayo de error antes de activar')
                run.assert_not_called()
    def test_recovery_cannot_change_data_services_ports_credentials_or_gate(self):
        configuration={'volumes':{},'networks':{},'services':{'postgres':{'image':'pg'},'redis':{'image':'redis'},'api':{'container_name':'ogt-api','ports':[{'host_ip':'127.0.0.1'}],'environment':{},'volumes':[]},'web':{'container_name':'ogt-web','ports':[{'host_ip':'127.0.0.1'}],'labels':{'traefik.http.routers.ogt-web.middlewares':'ogt-prelaunch@docker'}}}}
        recovery.safe_configuration(configuration,configuration,'ogt')
        changes=[('postgres','image','other'),('api','ports',[{'host_ip':'0.0.0.0'}]),('api','environment',{'SECRET':'other'}),('web','labels',{})]
        for service,key,value in changes:
            target=json.loads(json.dumps(configuration));target['services'][service][key]=value
            with self.assertRaises(ValueError):recovery.safe_configuration(configuration,target,'ogt')
    def test_lab_mode_cannot_target_the_real_installation(self):
        with self.assertRaisesRegex(ValueError,'LAB_ROOT'):recovery.validate_context(Path('/home/iankmp/ogt'),True)

if __name__=='__main__':unittest.main()
