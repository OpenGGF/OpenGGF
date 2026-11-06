"""Keep original dispatch subjects visible while behavioral scope remains open."""
import copy
import hashlib
import json
from pathlib import Path
import unittest

import test_report


class GeneratedSubjectBehavior(unittest.TestCase):
    def setUp(self):
        self.fixture = test_report.ReportBehavior()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        original = Path(__file__).with_name('reference_inventory.json')
        self.inventory = self.fixture.root / 'tools/parity/reference_inventory.json'
        self.inventory.write_bytes(original.read_bytes())
        self.fixture.catalogue['reference_inventory'] = {
            'path': 'tools/parity/reference_inventory.json',
            'sha256': hashlib.sha256(self.inventory.read_bytes()).hexdigest()}
        self.fixture.cat.write_text(json.dumps(self.fixture.catalogue))

    def test_all_original_table_entries_remain_unresolved_beside_partial_behavior(self):
        result = self.fixture.run_tool()
        placeholders = [row for row in result['obligations']
                        if row['definition'].get('kind') == 'inventory-placeholder']
        self.assertEqual(801, len(placeholders))
        self.assertEqual(802, sum(game['declared'] for game in result['games'].values()))
        self.assertTrue(all(row['status'] == 'unknown' for row in placeholders))
        self.assertEqual(801, result['reference_subjects']['dispatch_entries'])
        self.assertIn('not distinct objects', result['reference_subjects']['count_meaning'])

    def test_inventory_bytes_cannot_change_without_the_catalogue_pin(self):
        self.inventory.write_text('{}')
        result = self.fixture.invoke()
        self.assertEqual(2, result.returncode)
        self.assertIn('inventory digest', result.stderr)

    def test_repinning_malformed_inventory_does_not_hide_missing_original_entries(self):
        raw = json.loads(self.inventory.read_text())
        raw['subjects'].pop()
        self.inventory.write_text(json.dumps(raw))
        self.fixture.catalogue['reference_inventory']['sha256'] = hashlib.sha256(
            self.inventory.read_bytes()).hexdigest()
        self.fixture.cat.write_text(json.dumps(self.fixture.catalogue))
        result = self.fixture.invoke()
        self.assertEqual(2, result.returncode)
        self.assertIn('every original dispatch entry', result.stderr)

    def test_native_evidence_cannot_discharge_a_semantic_inventory_placeholder(self):
        self.fixture.report = self.fixture.run_tool()
        placeholder = next(row for row in self.fixture.report['obligations']
                           if row['definition'].get('kind') == 'inventory-placeholder')
        evidence = self.fixture.evidence(('exact',))
        row = evidence['observations'][0]
        row.update(obligation_id=placeholder['id'], case_id='uncatalogued-subject',
                   contract_digest=placeholder['contract_digest'],
                   rom_sha1=placeholder['definition']['reference']['rom_sha1'],
                   expected=1, compared=1, unit='unresolved native dispatch semantics')
        path = self.fixture.root / 'placeholder.json'
        path.write_text(json.dumps(evidence))
        result = self.fixture.invoke('--evidence', str(path))
        self.assertEqual(2, result.returncode)
        self.assertIn('placeholder', result.stderr)

    def test_behavior_added_for_one_facet_does_not_remove_the_subject_placeholder(self):
        added = copy.deepcopy(self.fixture.catalogue['obligations'][0])
        added['id'] = 's1.motobug.smoke'
        self.fixture.catalogue['obligations'].append(added)
        self.fixture.cat.write_text(json.dumps(self.fixture.catalogue))
        result = self.fixture.run_tool()
        self.assertEqual(801, result['reference_subjects']['dispatch_entries'])
        self.assertEqual(803, sum(game['declared'] for game in result['games'].values()))


if __name__ == '__main__':
    unittest.main()
