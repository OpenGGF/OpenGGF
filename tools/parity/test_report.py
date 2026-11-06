"""Behavior checks for the parity catalogue and current-observation join."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

TOOL = Path(__file__).with_name('report.py')


class ReportBehavior(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git('init', '-q')
        self.git('config', 'user.email', 'parity-test@example.invalid')
        self.git('config', 'user.name', 'Parity fixture')
        (self.root / 'src/main').mkdir(parents=True)
        (self.root / 'src/main/Owner.java').write_text('class Owner {}\n')
        (self.root / 'pom.xml').write_text('<project/>\n')
        adapter = self.root / 'tools/parity/fixture_adapter.py'
        adapter.parent.mkdir(parents=True)
        adapter.write_text('fixture producer code identity\n')
        self.adapter_hash = hashlib.sha256(adapter.read_bytes()).hexdigest()
        self.registry = {'schema': 1, 'producers': [{'id': 'controlled-native-producer',
            'adapter_path': 'tools/parity/fixture_adapter.py', 'adapter_sha256': self.adapter_hash,
            'evidence_kinds': ['native-state']}]}
        (adapter.parent / 'producers.json').write_text(json.dumps(self.registry))
        self.git('add', '.')
        self.git('commit', '-qm', 'Fixture')
        self.catalogue = {
            'schema': 1, 'version': 'test-1',
            'scopes': [{'game': game, 'domain': domain, 'closed': False,
                        'reason': 'Partial reference inventory.'}
                       for game in ('s1', 's2', 's3k') for domain in (
                           'gameplay', 'visuals', 'objects', 'badniks', 'bosses',
                           'camera-events', 'terrain', 'special-stages', 'progression', 'audio')],
            'obligations': [
                {'id': 's1.motobug.patrol', 'game': 's1', 'domain': 'badniks',
                 'subject': 'S1 object 0x40 Motobug', 'context': {'act': 'GHZ1', 'width': 320},
                 'contract': 'Patrol and reverse with the original timer and velocity.',
                 'reference': {'rom_sha1': '69e102855d4389c3fd1a8f3dc7d193f8eee5fe5b',
                               'citation': 'Moto_Move in the S1 disassembly.'},
                 'required_cases': ['left', 'right'],
                 'case_contracts': {case: {'expected': 10, 'unit': 'native frame observations', 'evidence_kinds': ['native-state']}
                                    for case in ('left', 'right')}}]}
        self.cat = self.root / 'catalogue.json'
        self.cat.write_text(json.dumps(self.catalogue))
        self.report = self.run_tool()

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args], stderr=subprocess.STDOUT)

    def invoke(self, *args):
        return subprocess.run([sys.executable, str(TOOL), *args, '--root', str(self.root),
                               '--catalogue', str(self.cat)], capture_output=True, text=True)

    def run_tool(self, *args):
        result = self.invoke(*args)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout)

    def evidence(self, statuses=('exact', 'exact'), **overrides):
        row = self.report['obligations'][0]
        evidence = {'schema': 1, 'source': self.report['source'],
                    'catalogue_digest': self.report['catalogue_digest'],
                    'producer': {'id': 'controlled-native-producer', 'version': '1',
                                 'comparator_sha256': self.adapter_hash}, 'synthetic': False, 'observations': []}
        for case, status in zip(('left', 'right'), statuses):
            observation = {'obligation_id': row['id'], 'case_id': case,
                           'contract_digest': row['contract_digest'],
                           'rom_sha1': self.catalogue['obligations'][0]['reference']['rom_sha1'],
                           'status': status, 'compared': 10, 'expected': 10, 'unit': 'native frame observations',
                           'artifact_sha256': 'a' * 64, 'evidence_kind': 'native-state', 'causal_status': 'isolated', 'metrics': {'mismatching_frames': 0}}
            observation.update(overrides)
            evidence['observations'].append(observation)
        return evidence

    def with_evidence(self, evidence, name='evidence.json'):
        path = self.root / 'target' / name
        path.parent.mkdir(exist_ok=True)
        path.write_text(json.dumps(evidence))
        return self.run_tool('--evidence', str(path))

    def test_no_evidence_keeps_open_empty_scopes_and_unknown_obligation(self):
        self.assertEqual(30, len(self.report['scopes']))
        self.assertEqual('unknown', self.report['obligations'][0]['status'])
        self.assertEqual({'s1': None, 's2': None, 's3k': None}, self.report['whole_game_parity'])
        audio = next(s for s in self.report['scopes'] if s['game'] == 's1' and s['domain'] == 'audio')
        self.assertEqual({'numerator': 0, 'denominator': 0, 'ratio': None}, audio['verified_coverage'])

    def test_all_required_cases_exact_verifies_only_declared_partial_scope(self):
        result = self.with_evidence(self.evidence())
        self.assertEqual('verified', result['obligations'][0]['status'])
        self.assertEqual(1, result['games']['s1']['verified'])
        self.assertIsNone(result['whole_game_parity']['s1'])
        self.assertEqual({'numerator': 1, 'denominator': 1, 'ratio': 1.0},
                         result['games']['s1']['verified_coverage'])

    def test_missing_required_case_cannot_verify(self):
        result = self.with_evidence(self.evidence(('exact',)))
        self.assertEqual('unknown', result['obligations'][0]['status'])
        self.assertEqual('missing', result['obligations'][0]['cases'][1]['status'])

    def test_skipped_incomplete_and_reference_limitations_remain_unresolved(self):
        for status in ('skipped', 'incomplete', 'reference-limited'):
            with self.subTest(status=status):
                result = self.with_evidence(self.evidence(('exact', status)))
                self.assertEqual('unknown', result['obligations'][0]['status'])
                self.assertEqual(status, result['obligations'][0]['cases'][1]['status'])
                self.assertEqual(0, result['games']['s1']['measurement_coverage']['numerator'])

    def test_current_mismatch_is_visible_even_when_another_case_is_missing(self):
        result = self.with_evidence(self.evidence(('mismatch',)))
        self.assertEqual('mismatch', result['obligations'][0]['status'])
        self.assertEqual(1, result['games']['s1']['mismatched'])
        self.assertEqual(0.0, result['games']['s1']['measured_agreement']['ratio'])

    def test_changed_actual_source_marks_old_evidence_stale(self):
        evidence = self.evidence()
        (self.root / 'src/main/Owner.java').write_text('class Owner { int changed; }\n')
        result = self.with_evidence(evidence)
        self.assertNotEqual(self.report['source']['fingerprint'], result['source']['fingerprint'])
        self.assertTrue(result['source']['dirty'])
        self.assertEqual('unknown', result['obligations'][0]['status'])
        self.assertTrue(all(case['status'] == 'stale' for case in result['obligations'][0]['cases']))

    def test_untracked_executable_input_and_test_tool_changes_change_fingerprint(self):
        for relative in ('src/test/New.java', 'tools/testing/new_test.py', '.mvn/jvm.config',
                         'tools/parity/producer.py', 'examples/game/Pilot.java'):
            with self.subTest(path=relative):
                path = self.root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('untracked executable input')
                current = self.run_tool()
                self.assertNotEqual(self.report['source']['fingerprint'], current['source']['fingerprint'])
                path.unlink()
        self.assertEqual(self.report['source']['fingerprint'], self.run_tool()['source']['fingerprint'])

    def test_target_outputs_do_not_change_source_identity(self):
        (self.root / 'target').mkdir()
        (self.root / 'target/random.json').write_text('ignored diagnostic')
        self.assertEqual(self.report['source']['fingerprint'], self.run_tool()['source']['fingerprint'])

    def test_invalid_evidence_fails_closed(self):
        mutations = [
            lambda e: e['observations'][0].update(rom_sha1='0' * 40),
            lambda e: e['observations'][0].update(contract_digest='0' * 64),
            lambda e: e.update(catalogue_digest='0' * 64),
            lambda e: e['observations'][0].update(case_id='unbound'),
            lambda e: e['observations'][0].update(obligation_id='unbound'),
            lambda e: e['observations'][0].update(compared=9),
            lambda e: e['observations'][0].update(expected=0),
            lambda e: e['observations'][0].update(unit='stereo samples'),
            lambda e: e['observations'][0].update(compared=True),
            lambda e: e['observations'][0].update(metrics={'delta': float('nan')}),
            lambda e: e['observations'].append(copy.deepcopy(e['observations'][0]))]
        for index, mutate in enumerate(mutations):
            with self.subTest(mutation=index):
                evidence = self.evidence()
                mutate(evidence)
                path = self.root / 'bad.json'
                path.write_text(json.dumps(evidence))
                result = self.invoke('--evidence', str(path))
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertEqual('', result.stdout)

    def test_producer_cannot_lower_extent_to_a_matching_prefix(self):
        evidence = self.evidence()
        for observation in evidence['observations']:
            observation.update(expected=1, compared=1)
        path = self.root / 'prefix.json'
        path.write_text(json.dumps(evidence))
        result = self.invoke('--evidence', str(path))
        self.assertEqual(2, result.returncode)

    def test_unregistered_producer_cannot_discharge_parity(self):
        evidence = self.evidence()
        evidence['producer']['id'] = 'unknown-adapter'
        result = self.with_evidence(evidence)
        self.assertEqual('unknown', result['obligations'][0]['status'])
        self.assertEqual('none', result['qualification'])
        self.assertTrue(all(case['status'] == 'unregistered-producer'
                            for case in result['obligations'][0]['cases']))

    def test_empty_registry_never_qualifies_a_handwritten_current_claim(self):
        (self.root / 'tools/parity/producers.json').write_text('{"schema":1,"producers":[]}')
        self.report = self.run_tool()
        result = self.with_evidence(self.evidence(('mismatch', 'exact')))
        self.assertEqual('none', result['qualification'])
        self.assertEqual(0, result['games']['s1']['verified'])
        self.assertEqual(0, result['games']['s1']['mismatched'])
        self.assertEqual('unknown', result['obligations'][0]['status'])

    def test_synthetic_claims_never_discharge_parity(self):
        evidence = self.evidence()
        evidence['synthetic'] = True
        result = self.with_evidence(evidence)
        self.assertEqual('unknown', result['obligations'][0]['status'])
        self.assertEqual('none', result['qualification'])
        self.assertTrue(all(case['status'] == 'synthetic' for case in result['obligations'][0]['cases']))

    def test_wrong_comparator_or_evidence_kind_lacks_authority(self):
        for change in ('comparator', 'engine-consistency', 'implementation-metadata', 'native-rgb'):
            with self.subTest(change=change):
                evidence = self.evidence()
                if change == 'comparator':
                    evidence['producer']['comparator_sha256'] = '0' * 64
                else:
                    for observation in evidence['observations']:
                        observation['evidence_kind'] = change
                result = self.with_evidence(evidence)
                self.assertEqual('unknown', result['obligations'][0]['status'])
                self.assertTrue(all(case['status'] == 'insufficient-authority'
                                    for case in result['obligations'][0]['cases']))

    def test_producer_kind_permission_does_not_override_case_authority(self):
        self.registry['producers'][0]['evidence_kinds'].append('native-rgb')
        (self.root / 'tools/parity/producers.json').write_text(json.dumps(self.registry))
        self.report = self.run_tool()
        evidence = self.evidence(evidence_kind='native-rgb')
        result = self.with_evidence(evidence)
        self.assertEqual('unknown', result['obligations'][0]['status'])
        self.assertEqual('insufficient-authority', result['obligations'][0]['cases'][0]['status'])

    def test_post_divergence_claims_are_not_independent_disagreements(self):
        result = self.with_evidence(self.evidence(('mismatch', 'mismatch'), causal_status='post-divergence'))
        self.assertEqual('unknown', result['obligations'][0]['status'])
        self.assertEqual(0, result['games']['s1']['mismatched'])
        self.assertTrue(all(case['status'] == 'post-divergence' for case in result['obligations'][0]['cases']))

    def test_first_divergence_is_decisive_only_as_a_mismatch(self):
        result = self.with_evidence(self.evidence(('mismatch',), causal_status='first-divergence'))
        self.assertEqual('mismatch', result['obligations'][0]['status'])
        evidence = self.evidence(causal_status='first-divergence')
        path = self.root / 'wrong-cause.json'
        path.write_text(json.dumps(evidence))
        self.assertEqual(2, self.invoke('--evidence', str(path)).returncode)

    def test_registry_bytes_and_explicit_synthetic_flag_are_required(self):
        evidence = self.evidence()
        del evidence['synthetic']
        path = self.root / 'no-synthetic.json'
        path.write_text(json.dumps(evidence))
        self.assertEqual(2, self.invoke('--evidence', str(path)).returncode)
        (self.root / 'tools/parity/fixture_adapter.py').write_text('changed adapter code')
        result = self.invoke()
        self.assertEqual(2, result.returncode)
        self.assertIn('adapter bytes', result.stderr)

    def test_curated_v1_inventory_cannot_be_closed_by_a_boolean(self):
        self.catalogue['scopes'][0]['closed'] = True
        self.cat.write_text(json.dumps(self.catalogue))
        result = self.invoke()
        self.assertEqual(2, result.returncode)
        self.assertIn('cannot close', result.stderr)

    def test_removed_confirmed_mismatch_is_explicit_in_comparison(self):
        before = self.root / 'before.json'
        before.write_text(json.dumps(self.with_evidence(self.evidence(('mismatch',)))))
        self.catalogue['obligations'] = []
        self.cat.write_text(json.dumps(self.catalogue))
        after = self.root / 'after.json'
        after.write_text(json.dumps(self.run_tool()))
        delta = self.run_tool('compare', '--before', str(before), '--after', str(after))
        self.assertEqual(['s1.motobug.patrol'], delta['removed_mismatches'])
        self.assertEqual([], delta['transitions'])

    def test_unchanged_contract_status_transition_compares_without_denominator_arithmetic(self):
        before = self.root / 'before.json'
        before.write_text(json.dumps(self.report))
        after = self.root / 'after.json'
        after.write_text(json.dumps(self.with_evidence(self.evidence())))
        delta = self.run_tool('compare', '--before', str(before), '--after', str(after))
        self.assertEqual([{'id': 's1.motobug.patrol', 'before': 'unknown', 'after': 'verified'}],
                         delta['transitions'])
        self.assertEqual([], delta['changed_contracts'])

    def test_duplicate_observation_across_files_is_rejected(self):
        path = self.root / 'evidence.json'
        path.write_text(json.dumps(self.evidence()))
        result = self.invoke('--evidence', str(path), '--evidence', str(path))
        self.assertEqual(2, result.returncode)
        self.assertIn('duplicate', result.stderr.lower())

    def test_duplicate_json_keys_are_rejected(self):
        path = self.root / 'bad.json'
        path.write_text('{"schema":1,"schema":1}')
        result = self.invoke('--evidence', str(path))
        self.assertEqual(2, result.returncode)
        self.assertIn('duplicate', result.stderr.lower())

    def test_compare_separates_contract_and_denominator_changes_from_status_changes(self):
        before = self.root / 'before.json'
        before.write_text(json.dumps(self.report))
        self.catalogue['obligations'][0]['contract'] = 'A new contract with a different requirement.'
        added = copy.deepcopy(self.catalogue['obligations'][0])
        added['id'] = 's1.motobug.contact'
        self.catalogue['obligations'].append(added)
        self.cat.write_text(json.dumps(self.catalogue))
        after = self.root / 'after.json'
        after.write_text(json.dumps(self.run_tool()))
        delta = self.run_tool('compare', '--before', str(before), '--after', str(after))
        self.assertEqual(['s1.motobug.contact'], delta['added'])
        self.assertEqual(['s1.motobug.patrol'], delta['changed_contracts'])
        self.assertEqual([], delta['transitions'])
        self.assertTrue(delta['catalogue_changed'])

    def test_compare_rejects_duplicate_rows_and_inconsistent_contract_digest(self):
        for corrupt in ('duplicate', 'digest'):
            with self.subTest(corrupt=corrupt):
                bad = copy.deepcopy(self.report)
                if corrupt == 'duplicate':
                    bad['obligations'].append(copy.deepcopy(bad['obligations'][0]))
                else:
                    bad['obligations'][0]['contract_digest'] = 'b' * 64
                path = self.root / 'bad-report.json'
                path.write_text(json.dumps(bad))
                result = self.invoke('compare', '--before', str(path), '--after', str(path))
                self.assertEqual(2, result.returncode)

    def test_markdown_and_inventory_are_real_cli_outputs(self):
        result = self.invoke('--format', 'markdown')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Whole-game parity is unavailable', result.stdout)
        self.assertIn('s1.motobug.patrol', result.stdout)
        self.assertIn('0/1 (0.0%)', result.stdout)
        self.assertIn('0/0 (unavailable)', result.stdout)
        inventory = self.run_tool('inventory')
        self.assertEqual(1, len(inventory['obligations']))
        self.assertEqual(30, len(inventory['scopes']))


if __name__ == '__main__':
    unittest.main()
