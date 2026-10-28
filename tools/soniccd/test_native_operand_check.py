"""Synthetic binary recipes; never opens original media or runs native code."""
import copy
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import native_operand_check as check


def sha(data):
    return hashlib.sha256(data).hexdigest()


class OperandTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.data = bytes.fromhex('43f9002095be43fa0010000800f8fff00001') + bytes(range(16))
        self.binary = self.root / 'stage.bin'
        self.binary.write_bytes(self.data)
        self.recipe = self.root / 'recipe.json'
        self.spec = {
            'version': 1,
            'binary': {'path': 'stage.bin', 'bytes': len(self.data), 'sha256': sha(self.data)},
            'spans': [{'id': 'table', 'offset': 18, 'bytes': 16, 'sha256': sha(self.data[18:])}],
            'literals': [{'id': 'absolute-lea', 'offset': 0, 'hex': '43f9002095be'}],
            'operands': [
                {'id': 'absolute', 'kind': 'absolute-long', 'offset': 2, 'width': 4,
                 'encoded': '002095be', 'expected_target': 0x2095be},
                {'id': 'pc', 'kind': 'pc-relative-word', 'offset': 8, 'width': 2,
                 'encoded': '0010', 'pc_base': 0x200008, 'expected_target': 0x200018},
                {'id': 'table-entry', 'kind': 'table-relative-word', 'offset': 14, 'width': 2,
                 'encoded': 'fff0', 'table_base': 0x200100, 'expected_target': 0x2000f0}
            ]
        }

    def write(self, spec=None):
        self.recipe.write_text(json.dumps(self.spec if spec is None else spec) + '\n')
        return self.recipe

    def run_check(self):
        return check.verify(self.write(), self.root)

    def invalid(self, mutation, code=2):
        mutation(self.spec)
        with self.assertRaises(check.CheckError) as caught:
            self.run_check()
        self.assertEqual(code, caught.exception.code)
        return str(caught.exception)

    def test_complete_recipe_derives_targets_without_disclosing_bytes(self):
        result = self.run_check()
        self.assertEqual('verified', result['status'])
        self.assertEqual([0x2095be, 0x200018, 0x2000f0], [r['actual_target'] for r in result['operands']])
        output = json.dumps(result)
        self.assertNotIn('43f9002095be', output)
        self.assertNotIn('encoded', output)
        self.assertEqual(sha(self.data), result['binary']['sha256'])
        self.assertFalse(result['runtime_or_source_authentication'])

    def test_pc_base_off_by_two_is_a_mismatch_not_silently_adjusted(self):
        self.spec['operands'][1]['pc_base'] += 2
        message = self.invalid(lambda s: None, 1)
        self.assertIn('pc', message)

    def test_negative_pc_displacement_is_signed(self):
        self.spec['operands'][1].update(offset=14, encoded='fff0', pc_base=0x200008, expected_target=0x1ffff8)
        self.spec['operands'].pop()
        self.assertEqual(-16, self.run_check()['operands'][1]['displacement'])

    def test_positive_table_displacement(self):
        self.spec['operands'][2].update(offset=10, encoded='0008', expected_target=0x200108)
        self.assertEqual(8, self.run_check()['operands'][2]['displacement'])

    def test_absolute_long_does_not_take_pc_or_table_base(self):
        self.invalid(lambda s: s['operands'][0].update(pc_base=0x200000))

    def test_wrong_region_binary_identity_rejected_before_operands(self):
        self.binary.write_bytes(bytes(reversed(self.data)))
        self.assertIn('identity', self.invalid(lambda s: None, 1))

    def test_truncated_file_rejected(self):
        self.binary.write_bytes(self.data[:-1])
        self.invalid(lambda s: None, 1)

    def test_extra_trailing_binary_byte_rejected(self):
        self.binary.write_bytes(self.data + b'\0')
        self.invalid(lambda s: None, 1)

    def test_span_hash_mismatch(self):
        self.invalid(lambda s: s['spans'][0].update(sha256='0' * 64), 1)

    def test_literal_mismatch_does_not_echo_bytes(self):
        message = self.invalid(lambda s: s['literals'][0].update(hex='43fa002095be'), 1)
        self.assertNotIn('43fa', message)
        self.assertNotIn('43f9', message)

    def test_operand_encoding_mismatch(self):
        self.invalid(lambda s: s['operands'][0].update(encoded='002095bf'), 1)

    def test_overlapping_declared_spans_rejected(self):
        self.invalid(lambda s: s['spans'].append({'id':'other', 'offset':20,'bytes':2,'sha256':sha(self.data[20:22])}))

    def test_cross_category_overlap_is_explicit_and_permitted(self):
        result = self.run_check()
        self.assertGreater(result['cross_category_overlaps'], 0)

    def test_duplicate_ids_rejected(self):
        self.invalid(lambda s: s['operands'][0].update(id='table'))

    def test_duplicate_json_keys_rejected(self):
        self.recipe.write_text('{"version":1,"version":1}\n')
        with self.assertRaises(check.CheckError) as caught:
            check.verify(self.recipe, self.root)
        self.assertEqual(2, caught.exception.code)

    def test_unknown_top_level_key_rejected(self):
        self.invalid(lambda s: s.update(execute='anything'))

    def test_unknown_operand_kind_is_incomplete(self):
        self.invalid(lambda s: s['operands'][0].update(kind='cpu-evaluate'), 3)

    def test_bool_is_not_an_offset(self):
        self.invalid(lambda s: s['spans'][0].update(offset=True))

    def test_wrong_fixed_operand_width_rejected(self):
        self.invalid(lambda s: s['operands'][0].update(width=2))

    def test_signed_target_underflow_rejected(self):
        self.spec['operands'][2].update(table_base=0, expected_target=0)
        self.invalid(lambda s: None)

    def test_target_overflow_rejected(self):
        self.spec['operands'][1].update(pc_base=0xffffffff)
        self.invalid(lambda s: None)

    def test_operand_outside_binary_rejected(self):
        self.invalid(lambda s: s['operands'][0].update(offset=len(self.data)-3))

    def test_span_outside_binary_rejected(self):
        self.invalid(lambda s: s['spans'][0].update(bytes=17))

    def test_noncanonical_hex_rejected(self):
        for value in ['0X1234', '43 F9', 'ABCDEF', '0', '']:
            with self.subTest(value=value):
                spec = copy.deepcopy(self.spec)
                spec['literals'][0]['hex'] = value
                with self.assertRaises(check.CheckError):
                    check.verify(self.write(spec), self.root)

    def test_no_observations_rejected(self):
        self.invalid(lambda s: (s.update(spans=[], literals=[], operands=[])))

    def test_absolute_binary_path_rejected(self):
        self.invalid(lambda s: s['binary'].update(path=str(self.binary)))

    def test_traversal_and_lexical_aliases_rejected(self):
        for value in ['../stage.bin', './stage.bin', 'a/../stage.bin', 'a//stage.bin', 'a\\stage.bin']:
            with self.subTest(value=value):
                spec=copy.deepcopy(self.spec); spec['binary']['path']=value
                with self.assertRaises(check.CheckError): check.verify(self.write(spec), self.root)

    def test_symlink_binary_rejected(self):
        link=self.root/'link.bin'; link.symlink_to(self.binary)
        self.invalid(lambda s: s['binary'].update(path='link.bin'))

    def test_symlink_parent_rejected(self):
        real=self.root/'real'; real.mkdir(); (real/'stage.bin').write_bytes(self.data)
        (self.root/'alias').symlink_to(real, target_is_directory=True)
        self.invalid(lambda s: s['binary'].update(path='alias/stage.bin'))

    def test_recipe_binary_hardlink_alias_rejected(self):
        self.recipe.unlink(missing_ok=True); os.link(self.binary,self.recipe)
        with self.assertRaises(check.CheckError): check.verify(self.recipe,self.root)

    def test_fifo_rejected_without_blocking(self):
        fifo=self.root/'fifo'; os.mkfifo(fifo)
        self.invalid(lambda s: s['binary'].update(path='fifo'))

    def test_changed_input_during_final_recheck_rejected(self):
        real=check._recheck
        def changed(snapshot):
            if snapshot.path == self.binary:
                self.binary.write_bytes(self.data + b'\0')
            return real(snapshot)
        with patch.object(check,'_recheck',side_effect=changed):
            with self.assertRaises(check.CheckError): self.run_check()

    def test_limits_checked_before_binary_allocation(self):
        self.invalid(lambda s: s['binary'].update(bytes=check.MAX_BINARY+1))

    def test_nonfinite_json_rejected(self):
        self.write(); text=self.recipe.read_text().replace('"version": 1','"version": NaN'); self.recipe.write_text(text)
        with self.assertRaises(check.CheckError): check.verify(self.recipe,self.root)

    def test_cli_stdout_is_json_only_and_does_not_modify_inputs(self):
        self.write(); before={p:p.read_bytes() for p in (self.recipe,self.binary)}
        p=subprocess.run([sys.executable,'-B',check.__file__,'--root',str(self.root),'--recipe',str(self.recipe)],capture_output=True,text=True)
        self.assertEqual(0,p.returncode,p.stderr)
        self.assertEqual('verified',json.loads(p.stdout)['status']); self.assertEqual('',p.stderr)
        self.assertEqual(before,{p:p.read_bytes() for p in before})

    def test_cli_failure_emits_only_bounded_error_no_success_stdout(self):
        self.spec['literals'][0]['hex']='43fa002095be'; self.write()
        p=subprocess.run([sys.executable,'-B',check.__file__,'--root',str(self.root),'--recipe',str(self.recipe)],capture_output=True,text=True)
        self.assertEqual(1,p.returncode); self.assertEqual('',p.stdout)
        self.assertEqual('mismatch',json.loads(p.stderr)['status']); self.assertNotIn('43fa',p.stderr)

    def test_signed_word_extremes_are_not_unsigned_or_byte_displacements(self):
        for raw, displacement in [('8000', -32768), ('7fff', 32767), ('ffff', -1)]:
            with self.subTest(raw=raw):
                content = self.data[:14] + bytes.fromhex(raw) + self.data[16:]
                self.binary.write_bytes(content)
                self.spec['binary']['sha256'] = sha(content)
                self.spec['operands'][2].update(encoded=raw, expected_target=0x200100 + displacement)
                self.assertEqual(displacement, self.run_check()['operands'][2]['displacement'])

    def test_raw_long_retains_high_unsigned_bits(self):
        self.binary.write_bytes(self.data[:2] + bytes.fromhex('ffffffff') + self.data[6:])
        self.spec['binary']['sha256'] = sha(self.binary.read_bytes())
        self.spec['literals'] = []
        self.spec['operands'][0].update(encoded='ffffffff', expected_target=0xffffffff)
        self.assertEqual(0xffffffff, self.run_check()['operands'][0]['actual_target'])

    def test_overlapping_operands_reject_even_with_distinct_ids(self):
        self.invalid(lambda s: s['operands'].append(dict(s['operands'][0], id='alias')))

    def test_unsupported_version_is_incomplete(self):
        self.invalid(lambda s: s.update(version=2), 3)

    def test_parent_symlink_selected_root_rejects(self):
        (self.root/'alias-root').symlink_to(self.root, target_is_directory=True)
        with self.assertRaises(check.CheckError): check.verify(self.write(),self.root/'alias-root')

    def test_recipe_change_is_rechecked_too(self):
        self.write()
        original=check._recheck
        calls=0
        def changed(snapshot):
            nonlocal calls
            if snapshot.path == self.recipe:
                calls += 1
                if calls == 2: self.recipe.write_bytes(snapshot.data + b' ')
            return original(snapshot)
        with patch.object(check,'_recheck',side_effect=changed):
            with self.assertRaises(check.CheckError): check.verify(self.recipe,self.root)

    def test_cli_unsupported_kind_is_incomplete_without_raw_recipe(self):
        self.spec['operands'][0]['kind']='instruction-execution'; self.write()
        p=subprocess.run([sys.executable,'-B',check.__file__,'--root',str(self.root),'--recipe',str(self.recipe)],capture_output=True,text=True)
        self.assertEqual(3,p.returncode); self.assertEqual('',p.stdout)
        self.assertEqual('incomplete',json.loads(p.stderr)['status'])
        self.assertNotIn('43f9002095be',p.stderr)


if __name__ == '__main__':
    unittest.main()
