#!/usr/bin/env python3
"""ROM-free instruction/counter controls for the incomplete S1 startup audit.

All instruction vectors below are handwritten test programs, not captured ROM
content. No ROM, emulator, trace, JVM, generated report or native job is needed.
Origin: audio-evidence startup diagnosis, 2026-10-22.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / 's1_startup_instruction_audit.py'
SPEC = importlib.util.spec_from_file_location('s1_startup_instruction_audit', SCRIPT)
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)


def put_words(program, pc, *words):
    program[pc:pc + len(words) * 2] = struct.pack('>' + 'H' * len(words), *words)


def install_checksum(program):
    # Independent unpacked word sum; does not call the production checksum helper.
    end = struct.unpack_from('>I', program, 0x1A4)[0]
    words = struct.unpack_from('>' + 'H' * ((end + 1 - 0x200) // 2), program, 0x200)
    struct.pack_into('>H', program, 0x18E, sum(words) & 0xFFFF)


def handcrafted_program():
    program = bytearray(0x80000)
    put_words(program, 0x216, 0x4BFA, 0x007C, 0x4C9D, 0x00E0)
    put_words(program, 0x236, 0x7000, 0x2C40)
    put_words(program, 0x262, 0x2D00, 0x51CE, 0xFFFC)
    put_words(program, 0x294, 0x8000, 0x3FFF, 0x0100)
    put_words(program, 0x31C, 0x207C, 0, 0x0200, 0x227C, 0, 0x01A4,
              0x2011, 0x7200, 0xD258, 0xB088, 0x64FA, 0x227C, 0, 0x018E,
              0xB251, 0x6600, 0x007C)
    put_words(program, 0x33E, 0x4DF8, 0xFE00, 0x7E00, 0x3C3C, 0x007F,
              0x2CC7, 0x51CE, 0xFFFC)
    put_words(program, 0x364, 0x4DF9, 0x00FF, 0, 0x7E00, 0x3C3C, 0x3F7F,
              0x2CC7, 0x51CE, 0xFFFC)
    put_words(program, 0x71FAC, 0x13FC, 0x0088, 0x00A0, 0x1FFF,
              0x33FC, 0, 0x00A1, 0x1100, 0x323C, 0x0011, 0x303C, 0xFFFF,
              0x4E71, 0x51C8, 0xFFFC, 0x51C9, 0xFFF4, 0x584F, 0x4E75)
    struct.pack_into('>I', program, 0x1A4, len(program) - 1)
    install_checksum(program)
    return program


def interpret_dbf(initial, body_cycles=4):
    """Small-loop interpreter: execute body, decrement only the low word, branch."""
    register = 0xA5A50000 | initial
    bodies = taken = cycles = 0
    while True:
        bodies += 1
        cycles += body_cycles
        low = ((register & 0xFFFF) - 1) & 0xFFFF
        register = (register & 0xFFFF0000) | low
        if low == 0xFFFF:
            cycles += 14
            break
        taken += 1
        cycles += 10
    return {'iterations': bodies, 'taken': taken, 'fall_through': 1,
            'terminal_low_word': register & 0xFFFF}, register, cycles


def interpret_nested(outer_initial, inner_initial):
    outer = outer_initial
    bodies = inner_taken = inner_fall = outer_taken = outer_bodies = 0
    while True:
        outer_bodies += 1
        inner = inner_initial
        while True:
            bodies += 1
            inner = (inner - 1) & 0xFFFF
            if inner == 0xFFFF:
                inner_fall += 1
                break
            inner_taken += 1
        outer = (outer - 1) & 0xFFFF
        if outer == 0xFFFF:
            break
        outer_taken += 1
    return bodies, inner_taken, inner_fall, outer_taken, outer_bodies


class S1StartupInstructionAuditContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.program = handcrafted_program()

    def test_unsigned_dbf_counts_match_a_separate_register_interpreter(self):
        for initial in [0, 1, 2, 7, 0x7F, 0xFF, 0x7FFF, 0xFFFF]:
            with self.subTest(initial=initial):
                outcomes, register, _ = interpret_dbf(initial)
                self.assertEqual(outcomes, AUDIT.dbf_outcomes(initial))
                self.assertEqual(0xA5A5FFFF, register)
        self.assertEqual(65536, AUDIT.dbf_outcomes(0xFFFF)['iterations'])

    def test_dbf_instruction_cost_and_branch_target_match_interpreted_small_loops(self):
        program = bytes.fromhex('4e7151c8fffc')  # NOP; DBF d0,NOP.
        nop = AUDIT._instruction(program, 0, 0x4E71, 'nop', '')
        branch = AUDIT._instruction(program, 2, 0x51C8, 'dbf', 'w', 4,
                                    kind='dbcc', target=0)
        for initial in [0, 1, 4, 0xFFFF]:
            with self.subTest(initial=initial):
                outcomes, _, cycles = interpret_dbf(initial)
                terms = [AUDIT._term(nop, 'nop', outcomes['iterations']),
                         AUDIT._term(branch, 'dbf d0,0', outcomes['iterations'],
                                     taken=outcomes['taken'], fall=1)]
                self.assertEqual(cycles, sum(term['instruction_only_cycles'] for term in terms))

    def test_nested_counter_counts_match_an_independent_small_loop_interpreter(self):
        for outer, inner in [(0, 0), (0, 7), (1, 0), (3, 5), (0, 0xFFFF), (0xFFFF, 0)]:
            with self.subTest(outer=outer, inner=inner):
                bodies, taken, expired, outer_taken, outer_bodies = interpret_nested(outer, inner)
                actual = AUDIT.nested_dbf_outcomes(outer, inner)
                self.assertEqual(bodies, actual['body_executions'])
                self.assertEqual(taken, actual['inner_taken'])
                self.assertEqual(expired, actual['inner_fall_through'])
                self.assertEqual(outer_taken, actual['outer']['taken'])
                self.assertEqual(outer_bodies, actual['outer']['iterations'])
        self.assertEqual(1 << 32, AUDIT.nested_dbf_outcomes(0xFFFF, 0xFFFF)['body_executions'])

    def test_signed_or_out_of_range_counter_inputs_are_rejected(self):
        for value in [-1, -32768, 0x10000, 1 << 32, True, 1.0, '65535']:
            with self.subTest(value=value), self.assertRaises(AUDIT.AuditError):
                AUDIT.dbf_outcomes(value)

    def test_checksum_is_big_endian_word_addition_with_u16_wrap_and_inclusive_end(self):
        vector = bytes.fromhex('ffff00010002ffff')
        self.assertEqual({'iterations': 4, 'checksum_u16': 1, 'terminal_a0': 8},
                         AUDIT.checksum_words(vector, 0, 7))
        self.assertEqual({'iterations': 2, 'checksum_u16': 3, 'terminal_a0': 6},
                         AUDIT.checksum_words(vector, 2, 5))

    def test_checksum_span_rejects_unsigned_alignment_range_and_bounds_errors(self):
        vector = bytes(8)
        for start, end in [(-1, 7), (0, -1), (True, 7), (0, True), (0.0, 7),
                           (0, 1 << 32), (1 << 32, 7), (1, 7), (0, 6), (6, 5), (0, 9)]:
            with self.subTest(start=start, end=end), self.assertRaises(AUDIT.AuditError):
                AUDIT.checksum_words(vector, start, end)

    def test_checksum_body_has_unsigned_bhs_and_actual_header_result(self):
        component = AUDIT.checksum_component(self.program)
        facts = component['facts']
        self.assertEqual(0x200, facts['start'])
        self.assertEqual(0x1A4, facts['inclusive_end_pointer'])
        self.assertEqual(0x7FFFF, facts['inclusive_end'])
        self.assertEqual(261888, facts['iterations'])
        self.assertEqual(facts['stored_checksum_u16'], facts['checksum_u16'])
        self.assertEqual('not_taken', facts['checksum_branch'])
        self.assertEqual(6285310, facts['loop_instruction_only_cycles'])
        self.assertEqual(6285382, component['instruction_only_cycles'])
        branch = component['instructions'][6]
        self.assertEqual((261887, 1, 10, 8),
                         (branch['taken'], branch['fall_through'],
                          branch['taken_cycles'], branch['fall_through_cycles']))

    def test_checksum_mismatch_is_an_error_not_a_taken_path_estimate(self):
        altered = self.program.copy()
        altered[0x18E] ^= 1
        with self.assertRaisesRegex(AUDIT.AuditError, 'checksum'):
            AUDIT.checksum_component(altered)

    def test_checksum_reads_header_end_and_rejects_a_torn_or_missing_last_word(self):
        for end in [0x7FFFD, 0x7FFFE, 0x80001, 0xFFFFFFFF, 0x1FF]:
            with self.subTest(end=end):
                altered = self.program.copy()
                struct.pack_into('>I', altered, 0x1A4, end)
                with self.assertRaises(AUDIT.AuditError):
                    AUDIT.checksum_component(altered)

    def test_entry_full_ram_loop_is_located_and_uses_table_seed_and_32bit_predecrement(self):
        component = AUDIT.entry_ram_component(self.program)
        facts = component['facts']
        self.assertEqual(0x294, facts['setup_values_pc'])
        self.assertEqual(0x3FFF, facts['initial_d6_u16'])
        self.assertEqual(16384, facts['iterations'])
        self.assertEqual(65536, facts['bytes_cleared'])
        self.assertEqual(0xFFFF0000, facts['terminal_a6_u32'])
        self.assertEqual((0xFF0000, 0xFFFFFF),
                         (facts['ram_start_24bit'], facts['ram_inclusive_end_24bit']))
        self.assertEqual(360452, facts['loop_instruction_only_cycles'])
        self.assertIn('noncontiguous', component['coverage'])

    def test_crossreset_and_main_ram_ranges_are_disjoint_and_cover_exact_full_ram(self):
        components = AUDIT.instruction_components(self.program)
        crossreset, main = components[2:4]
        self.assertEqual((128, 512, 2840),
                         (crossreset['facts']['iterations'], crossreset['facts']['bytes_cleared'],
                          crossreset['instruction_only_cycles']))
        self.assertEqual((16256, 65024, 357660),
                         (main['facts']['iterations'], main['facts']['bytes_cleared'],
                          main['instruction_only_cycles']))
        self.assertEqual(crossreset['facts']['ram_start_24bit'],
                         main['facts']['ram_inclusive_end_24bit'] + 1)
        self.assertEqual(65536, crossreset['facts']['bytes_cleared'] + main['facts']['bytes_cleared'])
        self.assertEqual(0xFFFFFF, crossreset['facts']['ram_inclusive_end_24bit'])

    def test_chant_busy_loop_has_independent_motorola_counts_and_no_frame_conversion(self):
        component = AUDIT.chant_component(self.program)
        self.assertEqual(18, component['facts']['outer']['iterations'])
        self.assertEqual(65536, component['facts']['inner_per_outer']['iterations'])
        self.assertEqual(1179648, component['facts']['body_executions'])
        self.assertEqual(16515544, component['instruction_only_cycles'])
        self.assertEqual(16515480, component['facts']['busy_loop_instruction_only_cycles'])
        inner = component['instructions'][5]
        self.assertEqual((1179630, 18, 10, 14),
                         (inner['taken'], inner['fall_through'],
                          inner['taken_cycles'], inner['fall_through_cycles']))
        self.assertEqual('addq.w #4,sp', component['instructions'][-2]['instruction'])
        self.assertIn('bus waits', component['coverage'])
        self.assertFalse(any('frame' in key for key in component))

    def test_changed_opcodes_registers_targets_and_operands_fail_closed(self):
        for address in [0x216, 0x218, 0x21C, 0x236, 0x238, 0x262, 0x266, 0x296,
                        0x31E, 0x326, 0x32C, 0x32E, 0x330, 0x33C,
                        0x340, 0x346, 0x348, 0x34C, 0x368, 0x36E, 0x374,
                        0x71FAE, 0x71FB2, 0x71FB6, 0x71FBE, 0x71FC2,
                        0x71FC4, 0x71FC8, 0x71FCC, 0x71FCE, 0x71FD0]:
            with self.subTest(address=hex(address)):
                altered = self.program.copy()
                altered[address + 1] ^= 1
                install_checksum(altered)
                with self.assertRaises(AUDIT.AuditError):
                    AUDIT.instruction_components(altered)

    def test_missing_or_duplicate_entry_ram_loop_is_rejected(self):
        for pc in [0x262, 0x280]:
            with self.subTest(pc=pc):
                altered = self.program.copy()
                if pc == 0x262:
                    put_words(altered, pc, 0x4E71)
                else:
                    put_words(altered, pc, 0x2D00, 0x51CE, 0xFFFC)
                with self.assertRaises(AUDIT.AuditError):
                    AUDIT.entry_ram_component(altered)

    def test_short_instruction_and_uncertain_or_unsupported_timing_are_rejected(self):
        for address in [-2, 1, 4, 0x100000000]:
            with self.subTest(address=address), self.assertRaises(AUDIT.AuditError):
                AUDIT._word(b'\x4e\x71', address)
        with self.assertRaises(AUDIT.AuditError):
            AUDIT._instruction(bytes.fromhex('303c'), 0, 0x303C, 'move', 'w', 4, src='#', dst='Dn')
        with self.assertRaisesRegex(AUDIT.AuditError, 'unsupported'):
            AUDIT._instruction(bytes.fromhex('a000'), 0, 0xA000, 'unknown', '')
        timing = AUDIT._load_timing()
        with self.assertRaisesRegex(AUDIT.AuditError, 'not exact'):
            AUDIT._exact(timing.Cost(12, 14), 0)

    def test_bad_branch_outcome_cardinality_cannot_produce_a_cycle_total(self):
        branch = AUDIT._instruction(bytes.fromhex('51c8fffe'), 0, 0x51C8,
                                    'dbf', 'w', 4, kind='dbcc', target=0)
        with self.assertRaises(AUDIT.AuditError):
            AUDIT._term(branch, 'dbf', 3, taken=1, fall=1)
        with self.assertRaises(AUDIT.AuditError):
            AUDIT._term(branch, 'dbf', 1, taken=-1, fall=2)

    def test_identity_gate_precedes_instruction_analysis_and_admits_no_fixture_pin(self):
        self.assertNotEqual(AUDIT.ROM_SHA1, hashlib.sha1(self.program).hexdigest())
        with mock.patch.object(AUDIT, 'instruction_components', side_effect=AssertionError('must not analyse')):
            for vector in [b'', bytes(self.program), bytes(self.program[:-1])]:
                with self.subTest(size=len(vector)), self.assertRaisesRegex(AUDIT.AuditError, 'identity'):
                    AUDIT.audit_rom(vector)

    def test_error_cli_is_read_only_and_returns_error_three(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'handcrafted-not-a-rom.bin'
            path.write_bytes(self.program)
            before = path.read_bytes()
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                status = AUDIT.main(['--rom', str(path)])
            report = json.loads(output.getvalue())
            self.assertEqual(3, status)
            self.assertEqual('ERROR', report['status'])
            self.assertFalse(report['complete'])
            self.assertIn('identity', report['error'])
            self.assertEqual(before, path.read_bytes())
            self.assertEqual([path], list(Path(temporary).iterdir()))

    def test_valid_cli_wiring_remains_incomplete_and_never_succeeds(self):
        # CLI wiring only: the real identity gate is covered separately above.
        # No synthetic bytes are admitted by audit_rom or any production pin.
        report = {'schema': AUDIT.SCHEMA, 'status': 'INCOMPLETE', 'complete': False,
                  'missing_terms': AUDIT.MISSING_TERMS,
                  'whole_startup_total': None, 'whole_frame_conversion': None}
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'input.bin'
            path.write_bytes(b'test-only CLI transport')
            output = io.StringIO()
            with mock.patch.object(AUDIT, 'audit_rom', return_value=report) as audit, \
                    contextlib.redirect_stdout(output):
                status = AUDIT.main(['--rom', str(path)])
            audit.assert_called_once_with(b'test-only CLI transport')
            self.assertEqual(2, status)
            self.assertEqual(report, json.loads(output.getvalue()))


if __name__ == '__main__':
    unittest.main()
