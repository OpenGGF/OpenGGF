#!/usr/bin/env python3
"""Read-only observer controls. Every run directory here is synthetic, never raw retained diagnostics."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

HERE=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('category_observation',HERE/'category_observation.py')
observer=importlib.util.module_from_spec(spec);sys.modules[spec.name]=observer;spec.loader.exec_module(observer)


class ObservationControls(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory(prefix='category-observer-synthetic-');self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);self.run=self.root/'run';self.run.mkdir();self.policy=self.root/'policy.json'
        self.plan=dict(schema=1,head='e472e7808967937376c24ecc071e40e07684fbd0',base=None,mode='focused',full=False,
            categories=['common'],reasons=[],tests=['com/openggf/TestSynthetic.java'],inventory_count=1,guards=True,
            workers=2,suite='smoke',scope='bounded smoke suite and guards; full-suite is public-release-only; trace/native profiles are separate',
            working_tree_fingerprint='f'*64,max_minutes=40,ordinary_batch_classes=50)
        self.preparation=dict(status='passed',exit_code=0,elapsed_seconds=0.5,working_tree_fingerprint='f'*64)
        self.status=dict(status='passed')
        self.results=[self.lane('ordinary',2),self.lane('guards',1)]
        self.rules=dict(version=1,inputs={'gl':'synthetic declared input'},rules=[])
        self.save()

    @staticmethod
    def lane(name,workers):
        return dict(lane=name,workers=workers,batches_planned=1,batches_completed=1,exit_code=0,elapsed_seconds=1.0,
                    status='passed',reports=1,tests=1,failures=0,errors=0,skipped=0,skipped_cases=[],failed_cases=[],
                    skipped_cases_omitted=0,failed_cases_omitted=0)

    @staticmethod
    def skip(name):return {'class':'synthetic.TestSkip','test':name,'reason':'synthetic retained reason'}

    @staticmethod
    def failure(name,kind='failure'):return {
        'class':'synthetic.TestFailure','test':name,'kind':kind,'type':'synthetic.Assertion','message':'assertion text',
        'detail':'synthetic retained failure detail'}

    def save(self):
        for name,value in [('plan',self.plan),('preparation',self.preparation),('status',self.status),('results',self.results)]:
            (self.run/(name+'.json')).write_text(json.dumps(value)+'\n')
        self.policy.write_text(json.dumps(self.rules)+'\n')

    def observe(self,caps=''):
        return observer.observe(str(self.run),str(self.policy),caps)

    def cli(self,*extra):
        out=io.StringIO()
        with contextlib.redirect_stdout(out):code=observer.main(['--run-dir',str(self.run),'--policy',str(self.policy),*extra])
        return code,json.loads(out.getvalue())

    def assert_bad(self):
        code,result=self.cli();self.assertEqual(code,2);self.assertEqual(result['status'],'rejected')

    def test_huge_integer_durations_reject_as_json_before_float_conversion(self):
        fields = [(self.plan, 'max_minutes'), (self.preparation, 'elapsed_seconds'),
                  (self.preparation, 'build_wait_seconds'), (self.results[0], 'elapsed_seconds')]
        absent = object()
        for target, key in fields:
            with self.subTest(field=key, record=('lane' if target is self.results[0] else 'plan_or_preparation')):
                old = target.get(key, absent)
                try:
                    target[key] = 10 ** 400
                    self.save()
                    completed = subprocess.run([sys.executable, '-B', str(HERE/'category_observation.py'),
                        '--run-dir', str(self.run), '--policy', str(self.policy)],
                        capture_output=True, text=True, timeout=30)
                    self.assertEqual(completed.returncode, 2, completed.stderr)
                    self.assertEqual(json.loads(completed.stdout)['status'], 'rejected')
                    self.assertNotIn('Traceback', completed.stderr)
                finally:
                    if old is absent: target.pop(key, None)
                    else: target[key] = old
        self.save()

    def test_declared_pass_is_still_incomplete_without_after_state_and_never_certifies(self):
        result=self.observe();self.assertEqual(result['status'],'incomplete')
        self.assertEqual(result['declared']['status'],'passed');self.assertEqual(result['declared']['head'],self.plan['head'])
        self.assertEqual(result['declared']['working_tree_fingerprint'],'f'*64)
        self.assertFalse(result['source_after_state_observed']);self.assertFalse(result['certification'])
        self.assertIn('source_after_state_unobserved',result['incomplete_reasons'])
        self.assertEqual(result['counts'],dict(reports=2,tests=2,failures=0,errors=0,skipped=0))
        self.assertEqual(self.cli()[0],3)

    def test_synthetic_forward_timeout_preserves_34302_counts_65_of_91_and_skip_classes(self):
        # Forward control copies supplied aggregate magnitudes, not original raw diagnostics or a source-bound run.
        self.plan.update(tests=['synthetic/Test%04d.java'%n for n in range(4550)],inventory_count=4550)
        total=self.lane('ordinary',2);total.update(batches_planned=91,batches_completed=65,status='incomplete',
            reports=3316,tests=34302,failures=8,errors=4,skipped=72,elapsed_seconds=2359.81,
            reason='Stopped Maven process tree: invocation timeout exceeded; validation is incomplete, not passed. Inspect the retained tail before retrying.')
        total['failed_cases']=[self.failure('f%d'%n,'failure' if n<8 else 'error') for n in range(12)]
        total['skipped_cases']=[self.skip('allowed%d'%n) for n in range(8)]+[self.skip('unknown%d'%n) for n in range(62)]+[self.skip('gl%d'%n) for n in range(2)]
        self.rules['rules']=[dict(match='synthetic.TestSkip#allowed%d'%n,category='opt-in-diagnostic',evidence='synthetic:1',allowed_when_absent=None) for n in range(8)]+[
            dict(match='synthetic.TestSkip#gl%d'%n,category='gl-context',evidence='synthetic:2',allowed_when_absent='gl') for n in range(2)]
        self.results=[total];self.status['status']='incomplete';self.save()
        result=self.observe('gl=true');self.assertEqual(result['counts'],dict(reports=3316,tests=34302,failures=8,errors=4,skipped=72))
        progress=result['lanes'][0]['progress'];self.assertEqual(progress['completed'],65);self.assertEqual(progress['planned'],91)
        self.assertEqual(progress['next_uncompleted_batch'],66);self.assertEqual(progress['uncompleted_batches'],26)
        self.assertEqual(result['guards']['status'],'not-started')
        self.assertEqual(result['skip_classification']['counts'],dict(allowed=8,unclassified=62,required_skipped=2,undeclared_input=0))
        self.assertEqual(result['status'],'incomplete');self.assertIn('ordinary_lane_incomplete',result['incomplete_reasons'])
        self.assertEqual(result['failures']['retained'],12);self.assertFalse(result['certification'])
        self.assertIn('declared_failures_present',result['issues']);self.assertIn('retained_skips_required_skipped',result['issues'])

    def test_results_is_a_list_and_normalized_supplied_object_is_not_a_raw_schema(self):
        self.results={'lane':'ordinary','tests':34302,'failures_detail':[]};self.save();self.assert_bad()

    def test_missing_each_named_document_stays_explicit_without_invented_counts(self):
        for name in ['plan','preparation','status','results']:
            with self.subTest(name=name):
                self.save();(self.run/(name+'.json')).unlink();result=self.observe()
                self.assertIn(name+'.json',result['missing_inputs']);self.assertEqual(result['status'],'incomplete')
                if name=='results':self.assertIsNone(result['counts'])

    def test_preparation_failure_and_no_lane_results_do_not_mean_zero_tests_passed(self):
        self.preparation.update(status='failed',exit_code=1,reason='synthetic preparation failure')
        self.results=[];self.status['status']='incomplete';self.save();result=self.observe()
        self.assertIsNone(result['counts']);self.assertEqual(result['preparation']['status'],'failed')
        self.assertIn('ordinary_lane_not_started',result['incomplete_reasons'])

    def test_guards_disabled_or_absent_remain_unobserved_even_if_ordinary_claims_pass(self):
        for requested in [False,True]:
            with self.subTest(requested=requested):
                self.plan['guards']=requested;self.results=self.results[:1];self.save();result=self.observe()
                self.assertFalse(result['guards']['observed']);self.assertEqual(result['guards']['requested'],requested)
                self.assertIn('guards_not_run',result['incomplete_reasons'])

    def test_exact_nested_dynamic_identity_and_reason_projection_are_not_normalized(self):
        identity='synthetic.Test$Nested#case[2](value)';skip={'class':'synthetic.Test$Nested','test':'case[2](value)',
            'reason':'org.opentest4j.TestAbortedException: required synthetic input',
            'reason_projection':'validated-junit-abort-cause','reason_source_chars':300,'reason_stack_frames':3}
        self.results[0].update(tests=2,skipped=1,skipped_cases=[skip])
        self.rules['rules']=[dict(match=identity,category='optional-fixture',evidence='synthetic:3',allowed_when_absent='gl')];self.save()
        result=self.observe('gl=false');self.assertEqual(result['skip_classification']['allowed'][0]['identity'],identity)
        self.assertEqual(result['skip_classification']['counts']['allowed'],1)
        self.assertEqual(self.observe()['skip_classification']['counts']['undeclared_input'],1)
        self.assertEqual(self.observe('gl=true')['skip_classification']['counts']['required_skipped'],1)

    def test_capabilities_use_existing_parser_and_only_declared_policy_inputs(self):
        self.assertEqual(self.observe(' gl=TRUE, ')['capabilities'],{'gl':True})
        for text in ['unknown=false','gl=maybe','gl=true,gl=false','=true','gl']:
            with self.subTest(text=text):self.assertEqual(self.cli('--capabilities',text)[0],2)

    def test_explicit_null_documents_and_wrong_policy_envelopes_reject(self):
        for name in ['status','preparation','results','plan']:
            with self.subTest(name=name):
                self.save();(self.run/(name+'.json')).write_text('null\n');self.assert_bad()
        self.save();self.rules['version']=True;self.save();self.assert_bad()
        self.rules['version']=1;self.rules['inputs']=[];self.save();self.assert_bad()

    def test_signed_process_exit_codes_are_claims_not_negative_counts(self):
        self.preparation.update(status='failed',exit_code=-9);self.results=[];self.status['status']='incomplete';self.save()
        self.assertEqual(self.observe()['preparation']['exit_code'],-9)
        self.preparation.update(status='passed',exit_code=0)
        self.results=[self.lane('ordinary',2)];self.results[0].update(status='incomplete',exit_code=-9,failed_batch=1,reason='synthetic launcher crash')
        self.save();self.assertEqual(self.observe()['lanes'][0]['exit_code'],-9)

    def test_retained_skip_reason_projections_and_truncations_are_explicit(self):
        skip=self.skip('long');skip['reason']='a'*4096
        self.results[0].update(tests=2,skipped=1,skipped_cases=[skip]);self.save();result=self.observe()
        observed=result['skip_classification']['reason_observations'][0]
        self.assertEqual(observed['reason'],'a'*512);self.assertEqual(observed['reason_output_omitted_chars'],3584)
        self.assertTrue(observed['reason_at_runner_cap']);self.assertIn('runner_skip_reason_at_cap',result['incomplete_reasons'])

    def test_policy_validation_keeps_exact_existing_duplicate_and_gl_rules(self):
        rule=dict(match='synthetic.TestSkip#t',category='gl-context',evidence='synthetic:4',allowed_when_absent='gl')
        for rules in [[rule,rule],[{**rule,'allowed_when_absent':None}],[{**rule,'category':'unknown'}],[{**rule,'match':'synthetic.*#t'}]]:
            with self.subTest(rules=rules):self.rules['rules']=rules;self.save();self.assert_bad()

    def test_duplicate_keys_nonfinite_and_bool_counts_are_rejected(self):
        for text in ['{"status":"passed","status":"incomplete"}','{"status":NaN}','{"status":Infinity}']:
            self.save();(self.run/'status.json').write_text(text);self.assert_bad()
        for key in ['tests','reports','batches_completed','skipped_cases_omitted','exit_code']:
            self.results=[self.lane('ordinary',2),self.lane('guards',1)];self.results[0][key]=False;self.save();self.assert_bad()

    def test_unknown_keys_status_shapes_and_lane_order_are_not_guessed(self):
        for document,key,value in [('status','after_state',{}),('status','status','timeout'),('preparation','status','queued'),('plan','schema',2),('plan','unknown',1)]:
            self.setUpCaseReset();getattr(self,document)[key]=value;self.save();self.assert_bad()
        self.setUpCaseReset();self.results.reverse();self.save();self.assert_bad()

    def setUpCaseReset(self):
        self.status={'status':'passed'};self.preparation=dict(status='passed',exit_code=0,elapsed_seconds=.5,working_tree_fingerprint='f'*64)
        self.plan.pop('unknown',None);self.plan['schema']=1;self.results=[self.lane('ordinary',2),self.lane('guards',1)]

    def test_omissions_must_match_counts_and_the_original_thousand_case_cap(self):
        for changes in [dict(skipped=1),dict(skipped=1,skipped_cases_omitted=1),dict(failures=1),dict(failed_cases_omitted=1)]:
            self.results[0]=self.lane('ordinary',2);self.results[0].update(changes);self.save();self.assert_bad()

    def test_input_omissions_and_observer_detail_omissions_remain_separate(self):
        lane=self.results[0];lane.update(tests=2000,skipped=1001,skipped_cases=[self.skip('u%d'%n) for n in range(1000)],skipped_cases_omitted=1)
        self.save();result=self.observe();classification=result['skip_classification']
        self.assertEqual(classification['retained'],1000);self.assertEqual(classification['input_omitted'],1)
        self.assertEqual(classification['counts']['unclassified'],1000)
        self.assertEqual(len(classification['unclassified']),100);self.assertEqual(classification['output_omitted']['unclassified'],900)
        self.assertIn('skipped_case_evidence_omitted',result['incomplete_reasons'])

    def test_failure_details_are_bounded_with_literal_messages_and_explicit_omissions(self):
        rows=[self.failure('f%d'%n) for n in range(60)];rows[0]['detail']='x'*8192;rows[0]['message']='m'*2048
        self.results[0].update(status='failed',exit_code=1,tests=100,failures=60,failed_cases=rows);self.status['status']='failed';self.save()
        result=self.observe();f=result['failures'];self.assertEqual(f['retained'],60);self.assertEqual(f['output_omitted'],10)
        self.assertEqual(len(f['cases']),50);self.assertEqual(f['cases'][0]['detail'],'x'*1024)
        self.assertEqual(f['cases'][0]['detail_omitted_chars'],7168);self.assertEqual(f['cases'][0]['message_omitted_chars'],1536)
        self.assertEqual(result['counts']['failures'],60)

    def test_duplicate_case_identities_and_impossible_totals_are_rejected(self):
        for changes in [dict(tests=3,skipped=2,skipped_cases=[self.skip('x'),self.skip('x')]),
                        dict(tests=2,failures=2,errors=1,failed_cases=[self.failure('a'),self.failure('b'),self.failure('c','error')])]:
            self.results[0]=self.lane('ordinary',2);self.results[0].update(changes);self.save();self.assert_bad()

    def test_skip_then_failure_in_another_lane_cannot_silently_reuse_an_identity(self):
        self.results[0].update(tests=2,skipped=1,skipped_cases=[self.skip('same')])
        failure=self.failure('same');failure['class']='synthetic.TestSkip'
        self.results[1].update(status='failed',exit_code=1,failures=1,failed_cases=[failure]);self.status['status']='failed'
        self.save();self.assert_bad()

    def test_batch_count_worker_and_disabled_guard_contradictions_reject(self):
        for change in [dict(batches_planned=2),dict(workers=1),dict(batches_completed=2)]:
            self.results[0]=self.lane('ordinary',2);self.results[0].update(change);self.save();self.assert_bad()
        self.results[0]=self.lane('ordinary',2);self.plan['guards']=False;self.save();self.assert_bad()

    def test_declared_source_fingerprint_disagreement_is_visible_incomplete(self):
        self.preparation['working_tree_fingerprint']='e'*64;self.save();result=self.observe()
        self.assertIn('declared_source_fingerprint_mismatch',result['incomplete_reasons'])

    def test_only_four_named_documents_are_read_and_unrelated_paths_are_ignored(self):
        (self.run/'unrelated.json').write_text('not JSON');(self.run/'unrelated-link').symlink_to('/not-selected')
        result=self.observe();self.assertEqual(set(result['inputs']),{'status.json','preparation.json','results.json','plan.json','policy'})
        self.assertFalse((self.run/'output.json').exists())

    def test_symlinks_hardlinks_noncanonical_and_nonregular_inputs_reject(self):
        saved=(self.run/'status.json').read_bytes();(self.run/'status.json').unlink();(self.root/'real.json').write_bytes(saved)
        (self.run/'status.json').symlink_to(self.root/'real.json');self.assert_bad()
        (self.run/'status.json').unlink();os.link(self.root/'real.json',self.run/'status.json');self.assert_bad()
        (self.run/'status.json').unlink();(self.run/'status.json').mkdir();self.assert_bad()
        self.assertEqual(self.cli('--run-dir',str(self.run)+'/../run')[0],2)
        alias=self.root/'alias';alias.symlink_to(self.run,target_is_directory=True)
        self.assertEqual(self.cli('--run-dir',str(alias))[0],2)

    def test_unrelated_sibling_directory_activity_does_not_change_selected_path_identity(self):
        original=observer.classifier
        def activity(saved,policy):
            (self.root/'unrelated-new-child').mkdir()
            return original(saved,policy)
        with mock.patch.object(observer,'classifier',side_effect=activity):
            self.assertEqual(self.observe()['counts']['tests'],2)

    def test_selected_input_mutation_and_missing_file_appearance_fail_closed(self):
        original=observer.recheck
        def changed(snapshot):
            if snapshot.path.name=='results.json':snapshot.path.write_text('[]\n')
            return original(snapshot)
        with mock.patch.object(observer,'recheck',side_effect=changed):self.assertEqual(self.cli()[0],3)
        self.save();(self.run/'status.json').unlink()
        def appeared(snapshot):
            if snapshot.path.name=='status.json':snapshot.path.write_text('{"status":"passed"}\n')
            return original(snapshot)
        with mock.patch.object(observer,'recheck',side_effect=appeared):self.assertEqual(self.cli()[0],3)

    def test_read_depth_node_and_selection_budgets_are_not_relaxed(self):
        self.save();(self.run/'results.json').write_bytes(b' '* (16*1024*1024+1));self.assertEqual(self.cli()[0],3)
        self.save();(self.run/'status.json').write_text('['*34+'0'+']'*34);self.assert_bad()
        self.plan['tests']=['synthetic/T%d.java'%n for n in range(10001)];self.plan['inventory_count']=10001;self.save();self.assertEqual(self.cli()[0],3)

    def test_aggregate_budget_is_enforced_before_reading_the_next_snapshot(self):
        original=observer.snapshot;spent=[]
        def counted(*args,**kwargs):
            saved=original(*args,**kwargs);spent.append(len(saved.data or b''))
            self.assertLessEqual(sum(spent),4096)
            return saved
        with mock.patch.object(observer,'MAX_TOTAL_BYTES',4096),mock.patch.object(observer,'snapshot',side_effect=counted):
            self.assertEqual(self.cli()[0],3)

    def test_dependency_pin_mismatch_is_rejected_before_any_untrusted_execution(self):
        with mock.patch.object(observer,'DEPENDENCY_SHA256','0'*64):self.assertEqual(self.cli()[0],2)

    def test_json_cli_exit_contract_and_no_files_written(self):
        before={p:p.read_bytes() for p in self.run.iterdir() if p.is_file()}
        env=dict(os.environ,PYTHONDONTWRITEBYTECODE='1')
        done=subprocess.run([sys.executable,'-B',str(HERE/'category_observation.py'),'--run-dir',str(self.run),'--policy',str(self.policy)],env=env,capture_output=True,timeout=10)
        self.assertEqual(done.returncode,3);self.assertEqual(json.loads(done.stdout)['status'],'incomplete');self.assertEqual(done.stderr,b'')
        self.assertEqual(before,{p:p.read_bytes() for p in self.run.iterdir() if p.is_file()})


if __name__=='__main__':unittest.main()
