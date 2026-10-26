"""Safety checks for source census and actual-invocation reconciliation."""
import json
import argparse
from pathlib import Path
import tempfile
import unittest
import test_reel as reel


class InventoryTests(unittest.TestCase):
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
                    'invocation_index':i,'junit_unique_id':None,'capture_status':'not-run'} for i in (1,2)]
            (root/'run.json').write_text(json.dumps({'selected_ids':[c['id'] for c in cases]}))
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
            base={'id':'C#m','fixture_sha256':'same','capture_status':'not-run'}
            inventory=root/'inventory.json';inventory.write_text(json.dumps({'source_commit':'base','cases':[base]}))
            records=[]
            for i in (1,2):
                directory=root/f'run-{i}';directory.mkdir()
                (directory/'run.json').write_text('{"roms":{}}')
                capture=base | {'case_dir':'cases/actual','capture_fixture_sha256':'same','junit_unique_id':'real-id'}
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
            broken=json.loads(Path(records[1]).read_text())
            broken['cases'][0]['capture_fixture_sha256']='changed'
            Path(records[1]).write_text(json.dumps(broken));args.out=str(root/'rejected')
            with self.assertRaisesRegex(ValueError,'Fixture changed'):
                reel.combine(args)


if __name__=='__main__':
    unittest.main()
