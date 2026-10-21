#!/usr/bin/env python3
"""Cold-entry semantic controls, task cold-entry-semantic-control-20261021/base8c78.

Explicit inputs: S3K_ROM, GPGX_GIT, COLD_ENTRY_TRACECHASER, COLD_ENTRY_EXPECTED.
Original source/ROM/profile initialize the machine. Native703 JSON is comparison
only. Fault injections below are controls, never qualified execution inputs.
No native/JVM/capture launch, report output or changes to existing model callers.
"""
import ast
import copy
from dataclasses import replace
import inspect
import io
import os
from pathlib import Path
import sys
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import cold_entry as ce  # noqa: E402
import mini68k  # noqa: E402

ENV = ('S3K_ROM', 'GPGX_GIT', 'COLD_ENTRY_TRACECHASER', 'COLD_ENTRY_EXPECTED')


def renumber(report):
    report['record_count'] = len(report['records'])
    for i, row in enumerate(report['records']):
        row[1] = i
    return report


@unittest.skipUnless(all(os.environ.get(k) for k in ENV), 'explicit cold-entry ROM/source/oracle inputs required')
class ColdEntry(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rom = Path(os.environ['S3K_ROM']).read_bytes()
        cls.sources = ce.Sources(os.environ['GPGX_GIT'], os.environ['COLD_ENTRY_TRACECHASER'])
        cls.expected = ce.load_expected(os.environ['COLD_ENTRY_EXPECTED'])
        cls.golden, cls.boundary = ce.generate(cls.rom, cls.sources, ce.Profile(ce.PROFILE_NAME))

    def cpu(self):
        return ce.ColdCPU(self.rom, self.sources, ce.Profile(ce.PROFILE_NAME))

    def at(self, pc, phase='boundary'):
        cpu = self.cpu()
        while (cpu.pc, cpu.phase) != (pc, phase):
            cpu.advance_phase()
        return cpu

    def finish(self, cpu):
        while True:
            try:
                cpu.advance_phase()
            except ce.DeviceBoundary as boundary:
                return boundary

    def instruction(self, cpu):
        self.assertEqual(cpu.phase, 'boundary')
        for unused in range(3):
            cpu.advance_phase()

    def test_golden_all_words_and_source_derived_boundary(self):
        result = ce.compare(self.golden, self.boundary, self.expected)
        self.assertEqual((result['claimed_records'], result['retired_instructions'], result['logical_reads']), (38, 11, 13))
        self.assertFalse(result['production_readiness'])
        before = self.golden.snapshot()
        with self.assertRaises(ce.DeviceBoundary) as raised:
            self.golden.advance_phase()
        self.assertEqual((raised.exception.pc, raised.exception.address, raised.exception.opcode), (0x238, 0xC00004, 0x3014))
        self.assertEqual(before, self.golden.snapshot())  # no device fetch or value read.

    def test_reset_geometry_vectors_bss_flags_and_refresh(self):
        cpu = self.cpu()
        enter, reset, init = cpu.events
        phase = ((262 - 192 + 159) * 3420 + 1004) // 7 * 7
        self.assertEqual(enter[3], phase)
        self.assertEqual((enter[2], enter[4], enter[5]), (0, 0, 4))
        self.assertEqual(reset[3], phase + 40 * 7)
        self.assertEqual(reset[4], ((phase + 40 * 7) // (128 * 7) + 1) * (128 * 7))
        self.assertEqual(reset[2], int.from_bytes(self.rom[4:8], 'big'))
        self.assertEqual(reset[29], int.from_bytes(self.rom[:4], 'big'))
        self.assertEqual(reset[5], 0x2704)
        self.assertEqual(reset[7], phase // 15 * 15)
        self.assertEqual((reset[6], reset[8], reset[9], enter[35], reset[35]), (0, 0, 0, 0, 0))
        self.assertEqual((init[35], init[36], init[10:13]), (0xA0, 0, (0x101, 0, 1)))
        self.assertEqual((cpu.pref_addr, cpu.pref_data), (8, int.from_bytes(self.rom[8:10], 'big')))

    def test_actual_shared_cpu_step_executes_eight_existing_forms(self):
        called = []
        step = mini68k.CPU.step
        def actual(cpu, calls, terms):
            called.append(cpu.pc)
            return step(cpu, calls, terms)
        with mock.patch.object(mini68k.CPU, 'step', actual):
            cpu, boundary = ce.generate(self.rom, self.sources, ce.Profile(ce.PROFILE_NAME))
        self.assertEqual(called, [0x206, 0x20A, 0x210, 0x212, 0x218, 0x21A, 0x226, 0x22E])
        ce.compare(cpu, boundary, self.expected)

    def test_lea_signed_register_is_distinct_from_24bit_bus(self):
        cpu = self.cpu()
        self.instruction(cpu)
        self.assertEqual(cpu.a[7], 0xFFFFFE00)
        self.assertEqual(cpu.read(0x100000298, 'w'), 0x8000)
        self.assertEqual(cpu.events[-1][10], 0x298)
        with self.assertRaises(mini68k.Unsupported):
            cpu.read(0x299, 'w')

    def test_tst_long_has_two_word_handlers_one_logical_read(self):
        cpu = self.at(0x20A)
        calls = []
        read = cpu.io_word
        def word(address):
            calls.append(address)
            return read(address)
        with mock.patch.object(cpu, 'io_word', word):
            start = len(cpu.events)
            self.instruction(cpu)
        self.assertEqual(calls, [0xA10008, 0xA1000A])
        reads = [row for row in cpu.events[start:] if row[0] == 6]
        self.assertEqual(len(reads), 1)
        self.assertEqual(reads[0][2], 0x210)  # extensions consumed before logical access.
        self.assertEqual(reads[0][10:13], (0xA10008, 0, 4))
        self.assertEqual(cpu.z, 1)

    def test_io_duplicate_lanes_and_no_controller_data_default(self):
        cpu = self.cpu()
        cpu.io[4:6] = [0x12, 0x34]  # lower handler fault vector, not reset initialization.
        self.assertEqual(cpu.read(0xA10008, 'l'), 0x12123434)
        for address in (0xA10002, 0xA10003, 0xA11100, 0xFF0000, 0xC00004):
            with self.assertRaises(mini68k.Unsupported):
                cpu.read(address, 'w')
        with self.assertRaises(mini68k.Unsupported):
            cpu.write(0xC00004, 'w', 0)

    def test_movem_word_mask_order_sign_extension_and_final_extra_read(self):
        cpu = self.at(0x21E)
        self.assertEqual(cpu.rom_word(cpu.pc + 2), 0xE0)
        start = len(cpu.events)
        sr = cpu.sr
        self.instruction(cpu)
        reads = [r for r in cpu.events[start:] if r[0] == 6]
        self.assertEqual([r[10] for r in reads], [0x298, 0x29A, 0x29C, 0x29E])
        self.assertEqual([r[12] for r in reads], [2] * 4)
        self.assertEqual([r[2] for r in reads], [0x222] * 4)
        self.assertEqual(reads[0][19:22], (0, 0, 0))
        self.assertEqual(reads[1][19:22], (0xFFFF8000, 0, 0))
        self.assertEqual(reads[2][19:22], (0xFFFF8000, 0x3FFF, 0))
        self.assertEqual(reads[3][19:22], (0xFFFF8000, 0x3FFF, 0x100))
        self.assertEqual([r[27] for r in reads], [0x298, 0x298, 0x298, 0x29E])
        self.assertEqual(len({(r[3], r[4]) for r in reads}), 1)
        self.assertEqual(cpu.sr, sr)

    def test_movem_long_register_progress_and_extra_word(self):
        cpu = self.at(0x222)
        self.assertEqual(cpu.rom_word(cpu.pc + 2), 0x1F00)
        start = len(cpu.events)
        self.instruction(cpu)
        reads = [r for r in cpu.events[start:] if r[0] == 6]
        self.assertEqual([r[10] for r in reads], [0x29E, 0x2A2, 0x2A6, 0x2AA, 0x2AE, 0x2B2])
        self.assertEqual([r[12] for r in reads], [4] * 5 + [2])
        for index, row in enumerate(reads):
            self.assertEqual(row[22:27], tuple(cpu.a[:index]) + (0,) * (5 - index))
        self.assertEqual([r[27] for r in reads], [0x29E] * 5 + [0x2B2])
        self.assertEqual(reads[-1][11], int.from_bytes(self.rom[0x2B2:0x2B4], 'big'))

    def test_fetch_refresh_execute_and_once_only_clock_conversion(self):
        cpu = self.at(0x21E)
        raw, deadline = cpu.raw, cpu.refresh
        self.assertGreaterEqual(raw, deadline)
        cpu.advance_phase()
        self.assertEqual((cpu.events[-1][3], cpu.events[-1][4]), (raw, deadline))
        self.assertEqual(cpu.raw, raw)
        cpu.advance_phase()
        self.assertEqual((cpu.raw, cpu.refresh), (raw + 14, raw + 128 * 7))
        cpu.advance_phase()
        self.assertEqual(cpu.raw, raw + 14 + (self.sources.table[0x4C9D] + 3 * 4) * 7)

    def test_movem_long_skip_extends_old_deadline_without_ordinary_charge(self):
        cpu = self.at(0x222, 'execute')
        cpu.raw, cpu.refresh = 500, 600  # exercise source path the ordinary prefix does not take.
        cpu.advance_phase()
        self.assertEqual(cpu.raw, 500 + (5 * 8 + self.sources.table[0x4CDD]) * 7)
        self.assertEqual(cpu.refresh, 600 + 128 * 7)
        self.assertNotEqual(cpu.refresh, 500 + 5 * 8 * 7 + 128 * 7)

    def test_movem_skip_before_base_and_refresh_only_on_next_fetch(self):
        cpu = self.at(0x222, 'execute')
        cpu.raw, cpu.refresh = 500, 820  # dynamic ends780; base ends864, crossing only afterward.
        cpu.advance_phase()
        self.assertEqual((cpu.raw, cpu.refresh), (864, 820))
        cpu.advance_phase()
        self.assertEqual((cpu.raw, cpu.events[-1][3], cpu.events[-1][4]), (864, 864, 820))
        cpu.advance_phase()
        self.assertEqual((cpu.raw, cpu.refresh), (878, 864 + 896))

    def test_branch_outcomes_follow_ccr_and_andi_does_not_clear_x(self):
        cpu = self.at(0x210)
        cpu.z = 0  # negative source-state injection: branch really changes, no fitted route.
        self.instruction(cpu)
        self.assertEqual(cpu.pc, 0x218)
        cpu = self.at(0x22A)
        self.assertEqual(cpu.d[0], 0xA0)
        cpu.x = 1
        self.instruction(cpu)
        self.assertEqual((cpu.d[0], cpu.x, cpu.n, cpu.z, cpu.v, cpu.c), (0, 1, 0, 1, 0, 0))
        self.instruction(cpu)
        self.assertEqual(cpu.pc, 0x238)

    def test_unknown_profile_rom_opcode_and_mask_inputs_refuse(self):
        for change in ({'name': 'other'}, {'bios': 1}, {'pal': 1}, {'region': 0xC0},
                       {'ports': (2, 1)}, {'save_provider': True}, {'neutral': False}, {'lines': 313},
                       {'bios': False}, {'neutral': 1}, {'devices': (False, 0)}):
            with self.assertRaises(ce.Refused):
                ce.generate(self.rom, self.sources, replace(ce.Profile(ce.PROFILE_NAME), **change))
        for address in (0, 4, 0x206, 0x220, 0x224):
            rom = bytearray(self.rom); rom[address] ^= 1
            with self.assertRaisesRegex(ce.Refused, 'ROM identity'):
                ce.generate(bytes(rom), self.sources, ce.Profile(ce.PROFILE_NAME))
        cpu = self.cpu()
        rom = bytearray(cpu.rom); rom[0x206:0x208] = b'\x4e\x71'; cpu.rom = bytes(rom)
        before = cpu.snapshot()
        with self.assertRaises(mini68k.Unsupported):
            cpu.advance_phase()
        self.assertEqual(before, cpu.snapshot())

    def test_unknown_io_producer_refuses_before_fetch(self):
        cpu = self.at(0x226)
        cpu.a[1] = 0xA10002 + 0x10FF
        before = cpu.snapshot()
        with self.assertRaises(mini68k.Unsupported):
            cpu.advance_phase()
        self.assertEqual(before, cpu.snapshot())

    def test_source_and_cycle_blob_negatives(self):
        with mock.patch.object(ce, '_git', return_value=b'0' * 40 + b'\n'):
            with self.assertRaisesRegex(ce.Refused, 'source commit/tree'):
                ce.Sources(os.environ['GPGX_GIT'], os.environ['COLD_ENTRY_TRACECHASER'])
        with self.assertRaises(ce.gpgx_cycles.IdentityError):
            ce.gpgx_cycles.load_table(os.environ['GPGX_GIT'], blob='0' * 40)

    def test_every_claimed_event_word_mutation_disagrees(self):
        for index in range(38):
            for word in range(40):
                report = copy.deepcopy(self.expected)
                report['records'][index][word] ^= 1
                with self.assertRaises(ce.Refused, msg=(index, word)):
                    ce.compare(self.golden, self.boundary, report)

    def test_named_initial_fields_are_all_compared(self):
        for key in self.expected['native_initial']:
            report = copy.deepcopy(self.expected)
            report['native_initial'][key] ^= 1
            with self.assertRaises(ce.Refused, msg=key):
                ce.compare(self.golden, self.boundary, report)

    def test_omitted_duplicate_and_reordered_extra_movem_reads(self):
        for address in (0x29E, 0x2B2):
            index = next(i for i, row in enumerate(self.expected['records'])
                         if row[0] == 6 and row[10] == address and row[12] == 2)
            for action in ('omit', 'duplicate', 'reorder'):
                report = copy.deepcopy(self.expected)
                if action == 'omit':
                    report['records'].pop(index)
                elif action == 'duplicate':
                    report['records'].insert(index, list(report['records'][index]))
                else:
                    report['records'][index - 1], report['records'][index] = report['records'][index], report['records'][index - 1]
                with self.assertRaises(ce.Refused, msg=(address, action)):
                    ce.compare(self.golden, self.boundary, renumber(report))

    def test_model_missing_extra_read_is_detected_independently(self):
        # Fault the actual producer, not just a report ordinal. Both source-owned reads are required.
        read = ce.ColdCPU.read
        for omit in (0x29E, 0x2B2):
            def dropped(cpu, address, size):
                value = read(cpu, address, size)
                if address == omit and size == 'w':
                    cpu.events.pop()
                return value
            with mock.patch.object(ce.ColdCPU, 'read', dropped):
                cpu, boundary = ce.generate(self.rom, self.sources, ce.Profile(ce.PROFILE_NAME))
            with self.assertRaises(ce.Refused):
                ce.compare(cpu, boundary, self.expected)

    def test_reset_phase_refresh_ccr_and_io_model_faults_disagree(self):
        for attribute in ('raw', 'refresh', 'z', 'irq'):
            cpu = self.cpu(); setattr(cpu, attribute, getattr(cpu, attribute) ^ 1)
            with self.assertRaises((ce.Refused, mini68k.Unsupported), msg=attribute):
                ce.compare(cpu, self.finish(cpu), self.expected)
        cpu = self.cpu(); cpu.io[0] ^= 1
        with self.assertRaises((ce.Refused, mini68k.Unsupported)):
            ce.compare(cpu, self.finish(cpu), self.expected)

    def test_expected_identity_status_schema_and_profile_negatives(self):
        for key, value in [('schema', 'wrong'), ('observer', 'off'), ('execution_status', 'partial'),
                           ('rom_sha1', '0' * 40), ('managed_cores_sha256', '0' * 64),
                           ('harness_executable_sha256', '0' * 64), ('managed_source_lock_sha256', '0' * 64),
                           ('installed_core_sha256', '0' * 64), ('comparison_only', False), ('record_words', 39)]:
            report = copy.deepcopy(self.expected); report[key] = value
            with self.assertRaises(ce.Refused, msg=key):
                ce.compare(self.golden, self.boundary, report)
        report = copy.deepcopy(self.expected); report['profile']['input'] = 'unknown'
        with self.assertRaises(ce.Refused):
            ce.compare(self.golden, self.boundary, report)
        for key in self.expected:
            report = copy.deepcopy(self.expected); del report[key]
            with self.assertRaises(ce.Refused, msg=key):
                ce.compare(self.golden, self.boundary, report)
        report = copy.deepcopy(self.expected); report['unknown'] = 1
        with self.assertRaises(ce.Refused):
            ce.compare(self.golden, self.boundary, report)

    def test_all_native_build_fields_are_authenticated(self):
        for key in self.expected['native_build_identity']:
            report = copy.deepcopy(self.expected)
            report['native_build_identity'][key] = 'wrong'
            with self.assertRaises(ce.Refused, msg=key):
                ce.compare(self.golden, self.boundary, report)
        report = copy.deepcopy(self.expected); report['native_build_identity']['toolchain']['zstd_threads'] = True
        with self.assertRaises(ce.Refused):
            ce.compare(self.golden, self.boundary, report)

    def test_missing_duplicate_reordered_and_partial_records_refuse(self):
        for action in ('omit', 'duplicate', 'reorder', 'incomplete'):
            report = copy.deepcopy(self.expected)
            if action == 'omit':
                report['records'].pop(5)
            elif action == 'duplicate':
                report['records'].insert(5, list(report['records'][5]))
            elif action == 'reorder':
                report['records'][3:5] = report['records'][3:5][::-1]
            else:
                report['records'].pop()
            with self.assertRaises(ce.Refused):
                ce.compare(self.golden, self.boundary, renumber(report))

    def test_capacity_ranges_types_and_duplicate_json_fields_refuse(self):
        for value in (-1, 1 << 32, True, 1.0, '0'):
            report = copy.deepcopy(self.expected); report['records'][4][3] = value
            with self.assertRaises(ce.Refused):
                ce.compare(self.golden, self.boundary, report)
        report = copy.deepcopy(self.expected); report['records'] *= 4; renumber(report)
        with self.assertRaises(ce.Refused):
            ce.compare(self.golden, self.boundary, report)
        with self.assertRaises(ce.Refused):
            ce.strict_object([('schema', 1), ('schema', 1)])
        for raw in (b' ' * 65537, b'{"schema":1,"schema":1}'):
            with mock.patch.object(Path, 'open', return_value=io.BytesIO(raw)):
                with self.assertRaises(ce.Refused):
                    ce.load_expected('comparison-only-input')
        cpu = self.cpu(); cpu.events = [cpu.events[0]] * 128
        with self.assertRaisesRegex(ce.Refused, 'capacity'):
            cpu.advance_phase()

    def test_copy_restore_at_every_fetch_refresh_and_movem_boundary(self):
        cpu = self.cpu()
        checkpoints = []
        while True:
            checkpoints.append((cpu.pc, cpu.phase, cpu.snapshot()))
            try:
                cpu.advance_phase()
            except ce.DeviceBoundary:
                break
        expected = cpu.snapshot()
        self.assertTrue({(0x21E, 'refresh'), (0x21E, 'execute'), (0x222, 'execute'),
                         (0x226, 'boundary')}.issubset({c[:2] for c in checkpoints}))
        for pc, phase, snapshot in checkpoints:
            resumed = self.cpu(); resumed.restore(snapshot); self.finish(resumed)
            self.assertEqual(resumed.snapshot(), expected, (pc, phase))

    def test_restore_is_detached_and_source_profile_bound(self):
        cpu = self.at(0x222, 'execute'); snapshot = cpu.snapshot()
        snapshot.state['d'][0] ^= 1
        self.assertEqual(cpu.d[0], 0)
        for bad in (replace(snapshot, source='wrong'), replace(snapshot, rom_sha256='wrong'),
                    replace(snapshot, profile=ce.Profile('unknown'))):
            with self.assertRaises(ce.Refused):
                cpu.restore(bad)

    def test_expected_mutation_has_no_execution_dataflow(self):
        before = self.golden.snapshot()
        with mock.patch.object(ce, 'load_expected', side_effect=AssertionError('execution read expected data')):
            cpu, boundary = ce.generate(self.rom, self.sources, ce.Profile(ce.PROFILE_NAME))
        report = copy.deepcopy(self.expected); report['records'][35][5] ^= 4
        with self.assertRaises(ce.Refused):
            ce.compare(cpu, boundary, report)
        self.assertEqual(self.golden.snapshot(), before)
        self.assertEqual(cpu.snapshot(), before)
        tree = ast.parse(inspect.getsource(ce.generate))
        self.assertEqual([arg.arg for arg in tree.body[0].args.args], ['rom', 'sources', 'profile'])

    def test_device_values_are_explicitly_unclaimed(self):
        report = copy.deepcopy(self.expected)
        report['records'][39][11] ^= 1
        report['records'][40][14] ^= 1
        result = ce.compare(self.golden, self.boundary, report)
        self.assertIn('device value', result['unsupported'])
        self.assertEqual(result['claimed_records'], 38)

    def test_existing_research_defaults_and_unsupported_forms_are_unchanged(self):
        cpu = mini68k.CPU(bytes(16))
        self.assertEqual((cpu.a[7], cpu.z, cpu.pc), (0xFFFE00, 0, 0))
        cpu.rom = b'\x4c\x9d\x00\xe0' + bytes(12)
        with self.assertRaises(mini68k.Unsupported):
            cpu.step({}, {})


if __name__ == '__main__':
    unittest.main()
