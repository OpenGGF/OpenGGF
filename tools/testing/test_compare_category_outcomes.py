"""Controls for bounded category-result comparisons (synthetic evidence only)."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('compare_category_outcomes.py')
spec = importlib.util.spec_from_file_location('category_comparison', SCRIPT)
comparison = importlib.util.module_from_spec(spec)
spec.loader.exec_module(comparison)


class CategoryOutcomeComparisonTest(unittest.TestCase):
    def setUp(self):
        self.reference = {
            'source': 'a' * 40, 'run': 'baseline-run',
            'failures': [{'identity': 'pkg.Route#replay', 'kind': 'failure',
                          'type': 'org.opentest4j.AssertionFailedError',
                          'message': 'org.opentest4j.AssertionFailedError: frame 57: x differs'}],
            'skips': [{'identity': 'pkg.Probe#optIn', 'message': 'Property is absent'}],
        }
        self.lanes = [
            {'lane': 'ordinary', 'exit_code': 1, 'reports': 3, 'tests': 5,
             'failures': 1, 'errors': 0, 'skipped': 1,
             'failed_cases_omitted': 0, 'skipped_cases_omitted': 0,
             'failed_cases': [{'class': 'pkg.Route', 'test': 'replay', 'kind': 'failure',
                               'type': 'org.opentest4j.AssertionFailedError',
                               'message': 'frame 57: x differs', 'detail': ''}],
             'skipped_cases': [{'class': 'pkg.Probe', 'test': 'optIn', 'reason': 'Property is absent'}]},
            {'lane': 'guards', 'exit_code': 0, 'reports': 2, 'tests': 4,
             'failures': 0, 'errors': 0, 'skipped': 0,
             'failed_cases_omitted': 0, 'skipped_cases_omitted': 0,
             'failed_cases': [], 'skipped_cases': []},
        ]

    def compare(self, **kwargs):
        return comparison.compare(self.lanes, self.reference, **kwargs)

    def test_unchanged_complete_negative_cases_match(self):
        self.assertTrue(self.compare()['negative_cases_unchanged'])

    def test_same_totals_with_new_failure_identity_require_review(self):
        self.lanes[0]['failed_cases'][0]['test'] = 'different'
        result = self.compare()
        self.assertFalse(result['negative_cases_unchanged'])
        self.assertEqual(['pkg.Route#different'], result['new_failures'])
        self.assertEqual(['pkg.Route#replay'], result['missing_expected_failures'])

    def test_changed_kind_type_and_assertion_require_review(self):
        for field, value in (('type', 'IllegalStateException'), ('message', 'frame 56: y differs'),
                             ('kind', 'error')):
            with self.subTest(field=field):
                lanes = copy.deepcopy(self.lanes)
                lanes[0]['failed_cases'][0][field] = value
                if field == 'kind':
                    lanes[0].update(failures=0, errors=1)
                result = comparison.compare(lanes, self.reference)
                self.assertEqual(['pkg.Route#replay'], result['changed_failures'])

    def test_absent_failure_is_reviewable_not_an_automatic_improvement(self):
        self.lanes[0].update(exit_code=0, failures=0, failed_cases=[])
        result = self.compare()
        self.assertFalse(result['negative_cases_unchanged'])
        self.assertEqual(['pkg.Route#replay'], result['missing_expected_failures'])

    def test_changed_skip_identity_or_cause_requires_review(self):
        for field, value in (('test', 'missingRom'), ('reason', 'ROM is missing')):
            with self.subTest(field=field):
                lanes = copy.deepcopy(self.lanes)
                lanes[0]['skipped_cases'][0][field] = value
                self.assertFalse(comparison.compare(lanes, self.reference)['negative_cases_unchanged'])

    def test_guard_failure_and_skip_require_review(self):
        guard = self.lanes[1]
        guard.update(exit_code=1, failures=1, failed_cases=[{
            'class': 'pkg.Guard', 'test': 'budget', 'kind': 'failure', 'type': 'AssertionError',
            'message': 'budget exceeded', 'detail': ''}])
        self.assertEqual(['pkg.Guard#budget'], self.compare()['guard_failures'])
        guard.update(exit_code=0, failures=0, failed_cases=[], skipped=1,
                     skipped_cases=[{'class': 'pkg.Guard', 'test': 'budget', 'reason': 'disabled'}])
        self.assertEqual(['pkg.Guard#budget'], self.compare()['guard_skips'])

    def test_incomplete_empty_missing_and_duplicate_lanes_are_rejected(self):
        variants = ([], self.lanes[:1], [self.lanes[0], self.lanes[0]],
                    [{**self.lanes[0], 'status': 'incomplete'}, self.lanes[1]],
                    [{**self.lanes[0], 'exit_code': 130}, self.lanes[1]],
                    [{**self.lanes[0], 'reports': 0}, self.lanes[1]],
                    [{**self.lanes[0], 'tests': 1}, self.lanes[1]])
        for lanes in variants:
            with self.subTest(lanes=lanes), self.assertRaises(comparison.EvidenceError):
                comparison.compare(lanes, self.reference)

    def test_omitted_mismatched_or_duplicate_negative_cases_are_rejected(self):
        for mutate in (
            lambda x: x[0].update(failed_cases_omitted=1),
            lambda x: x[0].update(skipped_cases_omitted=1),
            lambda x: x[0].update(failures=2),
            lambda x: x[0].update(exit_code=0),
            lambda x: x[0].update(failures=2, failed_cases=x[0]['failed_cases'] * 2),
            lambda x: x[0].update(skipped=2, skipped_cases=x[0]['skipped_cases'] * 2),
        ):
            lanes = copy.deepcopy(self.lanes)
            mutate(lanes)
            with self.subTest(lanes=lanes), self.assertRaises(comparison.EvidenceError):
                comparison.compare(lanes, self.reference)

    def test_complete_long_assertion_is_recovered_only_from_matching_detail(self):
        assertion = 'expected snapshot ' + 'x' * 2900
        case = self.lanes[0]['failed_cases'][0]
        case.update(message=assertion[:2048], detail=comparison.PREFIX + assertion + '\n at Route.java:10')
        self.reference['failures'][0]['message'] = assertion
        result = self.compare()
        self.assertTrue(result['negative_cases_unchanged'])
        self.assertEqual(assertion, result['complete_assertions']['pkg.Route#replay'])
        case['detail'] = comparison.PREFIX + 'other assertion'
        with self.assertRaises(comparison.EvidenceError):
            self.compare()

    def test_capped_or_multiline_assertion_and_skip_are_rejected(self):
        for mutate in (
            lambda x: x[0]['failed_cases'][0].update(message='x' * 2048, detail='x' * 8192),
            lambda x: x[0]['failed_cases'][0].update(message='first\nsecond'),
            lambda x: x[0]['skipped_cases'][0].update(reason='x' * 4096),
            lambda x: x[0]['skipped_cases'][0].update(reason='first\nsecond'),
        ):
            lanes = copy.deepcopy(self.lanes)
            mutate(lanes)
            with self.subTest(lanes=lanes), self.assertRaises(comparison.EvidenceError):
                comparison.compare(lanes, self.reference)

    def test_known_ssz_blob_normalization_is_explicit_and_identity_bounded(self):
        case = self.lanes[0]['failed_cases'][0]
        case.update(message='snapshot RewindObjectStateBlob@abc123')
        self.reference['failures'][0]['message'] = 'snapshot RewindObjectStateBlob@def456'
        self.assertFalse(self.compare(normalize_known_ssz_blobs=True)['negative_cases_unchanged'])
        case['class'], case['test'] = comparison.SSZ.split('#', 1)
        self.reference['failures'][0]['identity'] = comparison.SSZ
        self.assertFalse(self.compare()['negative_cases_unchanged'])
        self.assertTrue(self.compare(normalize_known_ssz_blobs=True)['negative_cases_unchanged'])
        self.reference['failures'][0]['message'] = 'snapshot RewindObjectStateBlob@HASH'
        self.assertTrue(self.compare(normalize_known_ssz_blobs=True)['negative_cases_unchanged'])
        case['message'] += ' actual count 2'
        self.assertFalse(self.compare(normalize_known_ssz_blobs=True)['negative_cases_unchanged'])

    def test_reference_provenance_and_unique_identities_are_required(self):
        for mutate in (lambda x: x.pop('source'),
                       lambda x: x['failures'].append(x['failures'][0]),
                       lambda x: x['skips'].append(x['skips'][0])):
            reference = copy.deepcopy(self.reference)
            mutate(reference)
            with self.subTest(reference=reference), self.assertRaises(comparison.EvidenceError):
                comparison.compare(self.lanes, reference)

    def test_duplicate_json_keys_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'data.json'
            path.write_text('{"source":"a","source":"b"}')
            with self.assertRaises(comparison.EvidenceError):
                comparison.read_json(path)

    def test_cli_preserves_inputs_and_returns_comparison_status(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            baseline, results, output = (root / name for name in ('baseline.json', 'results.json', 'comparison.json'))
            baseline.write_text(json.dumps(self.reference))
            results.write_text(json.dumps(self.lanes))
            original = results.read_bytes()
            command = [sys.executable, str(SCRIPT), '--baseline', str(baseline),
                       '--results', str(results), '--output', str(output)]
            completed = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertTrue(json.loads(output.read_text())['comparison']['negative_cases_unchanged'])
            self.assertEqual(original, results.read_bytes())
            self.lanes[0]['failed_cases'][0]['message'] = 'changed'
            results.write_text(json.dumps(self.lanes))
            self.assertEqual(1, subprocess.run(command, capture_output=True).returncode)
            self.lanes[0]['status'] = 'incomplete'
            results.write_text(json.dumps(self.lanes))
            self.assertEqual(2, subprocess.run(command, capture_output=True).returncode)
            self.assertEqual('invalid', json.loads(output.read_text())['status'])

    def test_cli_rejects_output_aliasing_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'baseline.json'
            original = json.dumps(self.reference)
            path.write_text(original)
            completed = subprocess.run([sys.executable, str(SCRIPT), '--baseline', str(path),
                                        '--results', str(path), '--output', str(path)], capture_output=True)
            self.assertEqual(2, completed.returncode)
            self.assertEqual(original, path.read_text())


if __name__ == '__main__':
    unittest.main()
