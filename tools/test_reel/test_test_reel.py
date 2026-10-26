"""Safety checks for source census and actual-invocation reconciliation."""
import json
import argparse
from pathlib import Path
import tempfile
import unittest
import test_reel as reel


class InventoryTests(unittest.TestCase):
    def test_specific_review_does_not_reclassify_other_methods_or_controls(self):
        scope = {'adapters':{'C':'actual object adapter'},
                 'non_drawable':{'C#logic':'No drawable model', 'C#mixed[2]':'Invisible control'}}
        self.assertEqual((None,'No drawable model'),reel.presentation_disposition(scope,'C','logic',None))
        self.assertEqual(('actual object adapter','Adapter available'),reel.presentation_disposition(scope,'C','mixed',1))
        self.assertEqual((None,'Invisible control'),reel.presentation_disposition(scope,'C','mixed',2))

    def test_complete_reel_separates_real_frames_from_one_frame_logic_cards(self):
        common = {'junit_unique_id':'actual-id', 'outcome':'passed',
                  'capture_fixture_sha256':'a'*64, 'matches_inventory_source':True}
        video = common | {'id':'C#video','capture_status':'captured','frames':7}
        logic = common | {'id':'C#logic','capture_status':'no-adapter','frames':0,
                          'drawable_disposition':'Mock renderer contract; no framebuffer'}
        selected = reel.reel_cases({'cases':[video,logic]}, True, True)
        self.assertEqual([('engine-frames',7),('non-drawable-card',1)],
                         [(kind,frames) for _,kind,frames in selected])
        self.assertEqual(0,logic['frames'])

    def test_full_reel_cannot_turn_missing_execution_or_unreviewed_case_into_a_card(self):
        case = {'id':'C#missing','capture_status':'not-executed','frames':0}
        with self.assertRaisesRegex(ValueError,'Incomplete reel'):
            reel.reel_cases({'cases':[case]}, True, True)
        case.update(capture_status='no-adapter',junit_unique_id='actual-id',outcome='passed',
                    capture_fixture_sha256='a'*64,matches_inventory_source=True,
                    drawable_disposition='Not yet reviewed for drawable state')
        with self.assertRaisesRegex(ValueError,'reviewed non-drawable'):
            reel.reel_cases({'cases':[case]}, True, True)
        case['drawable_disposition']='Adapter available'
        with self.assertRaisesRegex(ValueError,'reviewed non-drawable'):
            reel.reel_cases({'cases':[case]}, True, True)

    def test_complete_reel_requires_passed_matching_executed_sources(self):
        case = {'id':'C#video','capture_status':'captured','frames':2,
                'junit_unique_id':'actual-id','outcome':'failed',
                'capture_fixture_sha256':'a'*64,'matches_inventory_source':True}
        with self.assertRaisesRegex(ValueError,'passed actual invocation'):
            reel.reel_cases({'cases':[case]}, False, True)
        case['outcome']='passed';case['matches_inventory_source']=False
        with self.assertRaisesRegex(ValueError,'source identity'):
            reel.reel_cases({'cases':[case]}, False, True)

    def test_expands_controls_in_both_annotation_orders_and_respects_literal_braces(self):
        source = '''class Cases {
            @ParameterizedTest @CsvSource({"false, 12", "true, 128"})
            void pad(boolean left, int speed) { String braces="}"; /* } */ }
            @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2})
            @ParameterizedTest void delay(int n) { }
            @Test void simple() { if(true) { } }
        }'''
        methods = reel.methods(source)
        self.assertEqual([['false','12'],['true','128']],methods['pad']['arguments'])
        self.assertEqual([['0'],['1'],['2']],methods['delay']['arguments'])
        self.assertEqual([[]],methods['simple']['arguments'])

    def test_hex_sources_map_to_the_values_junit_actually_passes(self):
        source = '\n'.join(['@ParameterizedTest @CsvSource({"0x10,-0xC00,true"})',
                           'void words(int x, int v, boolean enabled) {}'])
        method = reel.methods(source)['words']
        self.assertEqual([['16','-3072','true']],method['arguments'])
        self.assertEqual([['0x10','-0xC00','true']],method['source_arguments'])

    def test_unknown_parameter_source_stops_census(self):
        with self.assertRaisesRegex(ValueError,'provider needs an adapter'):
            reel.methods('@ParameterizedTest @MethodSource("data") void hidden(int n) {}')

    def test_duplicate_parameter_values_use_real_junit_invocation_index(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            cases=[{'id':f'C#m[{i}]','class':'C','method':'m','arguments':['true'],
                    'invocation_index':i,'source_file':'C.java','junit_unique_id':None,'capture_status':'not-run'} for i in (1,2)]
            (root/'run.json').write_text(json.dumps({'selected_ids':[c['id'] for c in cases],
                                                   'source_file_hashes':{'C.java':'a'*64}}))
            for i in (1,2):
                directory=root/'cases'/str(i);directory.mkdir(parents=True)
                (directory/'case.json').write_text(json.dumps({
                    'class':'C','method':'m','arguments':['true'],
                    'junit_unique_id':f'[engine:junit-jupiter]/[test-template-invocation:#{i}]',
                    'capture_status':'no-adapter','capture_reason':'No drawable adapter','frames':0}))
            reel.reconcile({'cases':cases},root)
            self.assertNotEqual(cases[0]['junit_unique_id'],cases[1]['junit_unique_id'])
            self.assertEqual('cases/2',cases[1]['case_dir'])

    def test_missing_execution_is_retained_honestly(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            (root/'run.json').write_text('{"selected_ids":["C#m"]}')
            case={'id':'C#m','class':'C','method':'m','arguments':[],'invocation_index':None}
            reel.reconcile({'cases':[case]},root)
            self.assertEqual('not-executed',case['capture_status'])
            self.assertEqual('unknown',case['outcome'])

    def test_combination_refuses_duplicate_and_changed_fixtures(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            base={'id':'C#m','class':'C','method':'m','arguments':[],'invocation_index':None,
                  'source_file':'C.java','fixture_sha256':'a'*64,'capture_status':'not-run',
                  'adapter':None,'drawable_disposition':'Reviewed: mock draw contract only'}
            inventory=root/'inventory.json';inventory.write_text(json.dumps({'source_commit':'base','cases':[base]}))
            records=[]
            for i in (1,2):
                directory=root/f'run-{i}';directory.mkdir()
                (directory/'run.json').write_text(json.dumps({'roms':{},'source_file_hashes':{'C.java':'a'*64}}))
                capture=base | {'case_dir':'cases/actual','capture_fixture_sha256':'a'*64,'junit_unique_id':'real-id',
                                'adapter':'stale claim','drawable_disposition':'Not yet reviewed for drawable state'}
                record=directory/'manifest.json';record.write_text(json.dumps({'source_commit':'base','cases':[capture]}))
                records.append(str(record))
            args=argparse.Namespace(inventory=str(inventory),out=str(root/'out'),manifest=records,
                                    allow_legacy_source=False,prefer_last=False)
            with self.assertRaisesRegex(ValueError,'Duplicate recording'):
                reel.combine(args)
            args.out=str(root/'chosen');args.prefer_last=True
            reel.combine(args)
            chosen=json.loads((root/'chosen/manifest.json').read_text())['cases'][0]
            self.assertIn('run-2',chosen['case_dir'])
            self.assertIsNone(chosen['adapter'])
            self.assertEqual('Reviewed: mock draw contract only',chosen['drawable_disposition'])
            # A deliberately superseded revision is not a selected source. Only
            # the final chosen recording may satisfy the target inventory hash.
            (root/'run-1/run.json').write_text(json.dumps({'source_file_hashes':{'C.java':'b'*64}}))
            args.out=str(root/'superseded');reel.combine(args)
            combined=json.loads((root/'superseded/manifest.json').read_text())
            self.assertEqual('a'*64,combined['cases'][0]['capture_fixture_sha256'])
            self.assertEqual([{'id':'C#m','manifest':records[0]}],combined['superseded_recordings'])
            broken=json.loads(Path(records[1]).read_text())
            # A stale/forged manifest claim must not override the actual recorded source snapshot.
            broken['cases'][0]['capture_fixture_sha256']='a'*64
            Path(records[1]).write_text(json.dumps(broken))
            (root/'run-2/run.json').write_text(json.dumps({'source_file_hashes':{'C.java':'b'*64}}))
            args.out=str(root/'rejected')
            with self.assertRaisesRegex(ValueError,'Fixture changed'):
                reel.combine(args)

    def test_dirty_recorded_fixture_cannot_masquerade_as_committed_inventory(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);run=root/'run';directory=run/'cases'/'actual';directory.mkdir(parents=True)
            hash_a,hash_b='a'*64,'b'*64
            case={'id':'C#m[1]','class':'C','method':'m','arguments':['true'],'invocation_index':1,
                  'source_file':'src/test/java/C.java','fixture_sha256':hash_a,'capture_status':'not-run'}
            (run/'run.json').write_text(json.dumps({'selected_ids':[case['id']],'source_commit':'base',
                'source_file_hashes':{case['source_file']:hash_b}}))
            (directory/'case.json').write_text(json.dumps({'class':'C','method':'m','arguments':['true'],
                'junit_unique_id':'[engine:junit-jupiter]/[test-template-invocation:#1]',
                'capture_status':'no-adapter','capture_reason':'No drawable state','frames':0}))
            recording={'source_commit':'base','cases':[dict(case)]}
            reel.reconcile(recording,run)
            self.assertEqual(hash_b,recording['cases'][0]['capture_fixture_sha256'])
            self.assertEqual(hash_a,recording['cases'][0]['fixture_sha256'])
            manifest=run/'manifest.json';manifest.write_text(json.dumps(recording))
            inventory=root/'inventory.json';inventory.write_text(json.dumps({'source_commit':'base','cases':[case]}))
            args=argparse.Namespace(inventory=str(inventory),out=str(root/'reject-a'),manifest=[str(manifest)],
                                   prefer_last=False,allow_legacy_source=False)
            with self.assertRaisesRegex(ValueError,'Fixture changed'):
                reel.combine(args)
            case['fixture_sha256']=hash_b
            inventory.write_text(json.dumps({'source_commit':'base','cases':[case]}))
            args.out=str(root/'accept-b');reel.combine(args)
            combined=json.loads((root/'accept-b/manifest.json').read_text())['cases'][0]
            self.assertEqual(hash_b,combined['fixture_sha256'])
            self.assertEqual(hash_b,combined['capture_fixture_sha256'])

    def test_dirty_provider_arguments_and_ordinals_are_not_relabelled(self):
        case={'id':'C#m[1]','source_file':'C.java','method':'m','arguments':['true'],'invocation_index':1}
        source='@ParameterizedTest @ValueSource(booleans={false}) void m(boolean value) {}'
        with self.assertRaisesRegex(ValueError,'Arguments/ordinals changed'):
            reel.validate_selected_cases([case],{'C.java':source})
        source=source.replace('false','true')
        reel.validate_selected_cases([case],{'C.java':source})
        with self.assertRaisesRegex(ValueError,'ordinal mismatch'):
            reel.validate_invocation(case | {'junit_unique_id':'[test-template-invocation:#2]'})

    def test_missing_hash_is_only_an_explicit_legacy_limit_never_an_inventory_hash(self):
        case={'id':'C#m','source_file':'C.java','fixture_sha256':'a'*64}
        with self.assertRaisesRegex(ValueError,'hash unavailable'):
            reel.recorded_fixture_hash({},case)
        self.assertIsNone(reel.recorded_fixture_hash({},case,allow_legacy_source=True))
        # A new run with an incomplete hash map is corrupt, not a legacy exception.
        with self.assertRaisesRegex(ValueError,'hash unavailable'):
            reel.recorded_fixture_hash({'source_file_hashes':{}},case,allow_legacy_source=True)
        with self.assertRaisesRegex(ValueError,'hash unavailable'):
            reel.recorded_fixture_hash({'source_fingerprint':'new-format'},case,allow_legacy_source=True)
        with self.assertRaisesRegex(ValueError,'changed while queued/running'):
            reel.recorded_fixture_hash({'source_file_hashes':{'C.java':'a'*64},
                                        'source_files_changed':['C.java']},case)

    def test_matching_file_hash_does_not_authorize_changed_invocation_arguments(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);run=root/'run';run.mkdir()
            case={'id':'C#m[1]','class':'C','method':'m','arguments':['true'],'invocation_index':1,
                  'source_file':'C.java','fixture_sha256':'b'*64,'capture_status':'not-run'}
            inventory=root/'inventory.json';inventory.write_text(json.dumps({'cases':[case]}))
            (run/'run.json').write_text(json.dumps({'source_file_hashes':{'C.java':'b'*64}}))
            for field,value in [('arguments',['false']),('invocation_index',2)]:
                captured=case | {'case_dir':'cases/actual','junit_unique_id':'[test-template-invocation:#1]',field:value}
                manifest=run/'manifest.json';manifest.write_text(json.dumps({'cases':[captured]}))
                args=argparse.Namespace(inventory=str(inventory),out=str(root/field),manifest=[str(manifest)],
                                       prefer_last=False,allow_legacy_source=False)
                with self.assertRaisesRegex(ValueError,'identity mismatch'):
                    reel.combine(args)

    def test_reconciliation_export_preserves_raw_evidence_and_keeps_hashes_separate(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);run=root/'run';directory=run/'cases'/'actual';directory.mkdir(parents=True)
            case={'id':'C#m','class':'C','method':'m','arguments':[],'invocation_index':None,
                  'source_file':'C.java','fixture_sha256':'a'*64,'capture_status':'not-run'}
            inventory=root/'inventory.json';inventory.write_text(json.dumps({'source_commit':'base','cases':[case]}))
            (run/'run.json').write_text(json.dumps({'selected_ids':['C#m'],'source_commit':'base',
                'source_file_hashes':{'C.java':'b'*64}}))
            (directory/'case.json').write_text(json.dumps({'class':'C','method':'m','arguments':[],
                'junit_unique_id':'actual-id','capture_status':'no-adapter','capture_reason':'Logic','frames':0}))
            before={p:p.read_bytes() for p in (inventory,run/'run.json',directory/'case.json')}
            reel.reconcile_command(argparse.Namespace(inventory=str(inventory),run_dir=str(run),
                                                      out=str(root/'derived'),allow_legacy_source=False))
            derived=json.loads((root/'derived/manifest.json').read_text())['cases'][0]
            self.assertEqual('a'*64,derived['inventory_fixture_sha256'])
            self.assertEqual('b'*64,derived['capture_fixture_sha256'])
            self.assertFalse(derived['matches_inventory_source'])
            self.assertEqual(directory,(root/'derived'/derived['case_dir']).resolve())
            for path,content in before.items():
                self.assertEqual(content,path.read_bytes())

    def test_legacy_combination_retains_unknown_actual_hash(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp);run=root/'run';run.mkdir()
            case={'id':'C#m','class':'C','method':'m','arguments':[],'invocation_index':None,
                  'source_file':'C.java','fixture_sha256':'a'*64,'capture_status':'not-run'}
            inventory=root/'inventory.json';inventory.write_text(json.dumps({'source_commit':'base','cases':[case]}))
            (run/'run.json').write_text('{"source_commit":"base"}')
            capture=case | {'case_dir':'cases/actual','capture_fixture_sha256':'a'*64,'junit_unique_id':'actual-id'}
            manifest=run/'manifest.json';manifest.write_text(json.dumps({'source_commit':'base','cases':[capture]}))
            args=argparse.Namespace(inventory=str(inventory),out=str(root/'denied'),manifest=[str(manifest)],
                                   prefer_last=False,allow_legacy_source=False)
            with self.assertRaisesRegex(ValueError,'hash unavailable'):
                reel.combine(args)
            args.allow_legacy_source=True;args.out=str(root/'legacy');reel.combine(args)
            actual=json.loads((root/'legacy/manifest.json').read_text())['cases'][0]
            self.assertIsNone(actual['capture_fixture_sha256'])
            self.assertIsNone(actual['matches_inventory_source'])
            self.assertIn('unavailable',actual['provenance_limit'])


if __name__=='__main__':
    unittest.main()
