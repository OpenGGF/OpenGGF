#!/usr/bin/env python3
"""Cold-entry semantic controls, task cold-entry-semantic-control-20261021/base8c78.

Explicit inputs: S3K_ROM, GPGX_GIT, COLD_ENTRY_TRACECHASER, COLD_ENTRY_EXPECTED.
Original source/ROM/profile initialize the machine. Native703 JSON is comparison
only. Fault injections below are controls, never qualified execution inputs.
No native/JVM/capture launch, report output or changes to existing model callers.
"""
import ast
import copy
from contextlib import redirect_stdout, redirect_stderr
from dataclasses import replace
import inspect
import io
import json
import os
from pathlib import Path
import sys
import tempfile
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

    @unittest.skipUnless(os.environ.get('BIZHAWK_GIT'), 'explicit BizHawk427 object store required')
    def test_opt_in_first_status_all_words_and_semantic_stop(self):
        self.assertTrue(callable(getattr(ce, 'generate_first_vdp', None)),
                        'independent first-status execution must be an explicit opt-in')
        sources = ce.FirstVDPSources(os.environ['GPGX_GIT'], os.environ['COLD_ENTRY_TRACECHASER'],
                                     os.environ['BIZHAWK_GIT'])
        cpu = ce.generate_first_vdp(self.rom, sources, ce.Profile(ce.PROFILE_NAME))
        result = ce.compare_first_vdp(cpu, self.expected)
        self.assertEqual((result['claimed_records'], result['retired_instructions'], result['logical_reads']),
                         (41, 12, 14))
        self.assertEqual(cpu.events, [tuple(row) for row in self.expected['records']])
        self.assertEqual((cpu.pc, cpu.phase), (0x23A, 'first_status_retired'))
        before = cpu.snapshot()
        with self.assertRaises(mini68k.Unsupported):
            cpu.advance_phase()
        self.assertEqual(cpu.snapshot(), before)  # no MOVEQ fetch or later device continuation.

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

    def test_cli_digest_identifies_same_bounded_bytes_after_path_replacement(self):
        original = Path(os.environ['COLD_ENTRY_EXPECTED']).read_bytes()
        self.assertLessEqual(len(original), 65536)
        real_open = Path.open
        for replacement in (None, b'{}', b' ' * 65537):
            with self.subTest(replacement_size=None if replacement is None else len(replacement)):
                # Only a task-owned temporary copy changes; the qualified703 input is read-only.
                with tempfile.TemporaryDirectory(prefix='.cold-entry-expected-r2-',
                                                 dir=Path(__file__).resolve().parent) as directory:
                    expected = Path(directory) / 'expected.json'
                    expected.write_bytes(original)
                    reads = []
                    class ExpectedStream:
                        def __init__(self, stream):
                            self.stream = stream

                        def __enter__(self):
                            return self

                        def __exit__(self, *error):
                            self.stream.close()

                        def read(self, limit=-1):
                            reads.append(limit)
                            raw = self.stream.read(limit)
                            if len(reads) == 1 and replacement is not None:
                                changed = expected.with_suffix('.replacement')
                                changed.write_bytes(replacement)
                                changed.replace(expected)  # genuine atomic pathname replacement after read.
                            return raw

                    def open_path(path, *args, **kwargs):
                        stream = real_open(path, *args, **kwargs)
                        return ExpectedStream(stream) if path == expected else stream

                    output = io.StringIO()
                    args = ['--rom', os.environ['S3K_ROM'], '--gpgx-git', os.environ['GPGX_GIT'],
                            '--tracechaser', os.environ['COLD_ENTRY_TRACECHASER'],
                            '--expected', str(expected), '--profile', ce.PROFILE_NAME]
                    with mock.patch.object(Path, 'open', open_path), redirect_stdout(output):
                        self.assertEqual(ce.main(args), 0)
                    result = json.loads(output.getvalue())
                    self.assertEqual(result['status'], 'PASS')
                    self.assertEqual(result['claimed_records'], 38)
                    self.assertEqual(result['native_expected_sha256'], ce.digest(original))
                    self.assertEqual(reads, [65537])
                    self.assertEqual(expected.read_bytes(), original if replacement is None else replacement)

    @unittest.skipUnless(os.environ.get('BIZHAWK_GIT'), 'explicit BizHawk427 object store required')
    def test_opt_in_cli_keeps_compared_digest_after_oversize_atomic_path_replacement(self):
        original = Path(os.environ['COLD_ENTRY_EXPECTED']).read_bytes()
        real_open = Path.open
        with tempfile.TemporaryDirectory(prefix='.cold-entry-first-vdp-',
                                         dir=Path(__file__).resolve().parent) as directory:
            expected = Path(directory) / 'expected.json'; expected.write_bytes(original)
            reads = []
            class ExpectedStream:
                def __init__(self, stream): self.stream = stream
                def __enter__(self): return self
                def __exit__(self, *error): self.stream.close()
                def read(self, limit=-1):
                    reads.append(limit)
                    raw = self.stream.read(limit)
                    changed = expected.with_suffix('.replacement'); changed.write_bytes(b' ' * 65537)
                    changed.replace(expected)
                    return raw
            def open_path(path, *args, **kwargs):
                stream = real_open(path, *args, **kwargs)
                return ExpectedStream(stream) if path == expected else stream
            args = ['--rom', os.environ['S3K_ROM'], '--gpgx-git', os.environ['GPGX_GIT'],
                    '--tracechaser', os.environ['COLD_ENTRY_TRACECHASER'], '--expected', str(expected),
                    '--profile', ce.PROFILE_NAME, '--first-vdp-status', '--bizhawk-git', os.environ['BIZHAWK_GIT']]
            output = io.StringIO()
            with mock.patch.object(Path, 'open', open_path), redirect_stdout(output):
                self.assertEqual(ce.main(args), 0)
            result = json.loads(output.getvalue())
            self.assertEqual((result['claimed_records'], result['native_expected_sha256'], reads),
                             (41, ce.digest(original), [65537]))
            self.assertEqual(expected.stat().st_size, 65537)

    def test_private_expected_handoff_keeps_raw_digest_and_public_loader_contract(self):
        raw = b' { "value" : 1 } \n'
        with mock.patch.object(Path, 'open', return_value=io.BytesIO(raw)):
            report, sha256 = ce._load_expected_with_digest('comparison-only-input')
        self.assertEqual(report, {'value': 1})
        self.assertEqual(sha256, ce.digest(raw))
        self.assertNotEqual(sha256, ce.digest(json.dumps(report).encode()))
        with mock.patch.object(Path, 'open', return_value=io.BytesIO(raw)):
            self.assertEqual(ce.load_expected('comparison-only-input'), report)

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


@unittest.skipUnless(all(os.environ.get(k) for k in ENV + ('BIZHAWK_GIT',)),
                     'explicit cold-entry and BizHawk427 source inputs required')
class FirstVDPStatus(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rom = Path(os.environ['S3K_ROM']).read_bytes()
        cls.sources = ce.FirstVDPSources(os.environ['GPGX_GIT'], os.environ['COLD_ENTRY_TRACECHASER'],
                                         os.environ['BIZHAWK_GIT'])
        cls.profile = ce.Profile(ce.PROFILE_NAME)
        cls.frame = ce.FirstFrameProfile()
        cls.expected = ce.load_expected(os.environ['COLD_ENTRY_EXPECTED'])
        cls.golden = ce.generate_first_vdp(cls.rom, cls.sources, cls.profile)

    def cpu(self):
        return ce.FirstVDPCPU(self.rom, self.sources, self.profile)

    def at(self, phase='execute'):
        cpu = self.cpu()
        while (cpu.pc, cpu.phase) != (0x238, phase):
            cpu.advance_phase()
        return cpu

    def finish(self, cpu):
        while cpu.phase != 'first_status_retired':
            cpu.advance_phase()
        return cpu

    def device(self):
        return ce.FirstVDP.reset(self.sources, self.profile, self.frame)

    def test_reset_state_and_first_admission_are_source_derived(self):
        reset = self.device()
        self.assertEqual(self.frame.input_devices, (0, 255, 255, 255, 0, 255, 255, 255))
        self.assertEqual(reset.reg, [0] * 10 + [0xFF] + [0] * 21)
        self.assertEqual((reset.status, reset.pending, reset.hint_pending, reset.vint_pending,
                          reset.addr, reset.addr_latch, reset.code, reset.dma_length, reset.dma_end,
                          reset.fifo_idx, reset.interlaced, reset.im2, reset.odd_frame), (0,) * 13)
        self.assertEqual((reset.width, reset.height, reset.viewport_x, reset.viewport_y,
                          reset.v_counter, reset.h_counter, reset.hvc_latch),
                         (256, 192, 14, 24, 192, 255, 0x10000))
        self.assertEqual(self.sources.status_timings, (770, 280, 860))
        cpu = self.cpu()
        self.assertEqual(cpu.vdp.v_counter, 159)  # assertion on scheduling, never an initialization input.
        self.assertEqual((cpu.vdp.mcycles, cpu.vdp.line_end), (229 * 3420, 230 * 3420))
        self.assertEqual((cpu.vdp.h_counter, cpu.vdp.status, cpu.vdp.z80_irq), (0x5F, 0, False))
        self.assertEqual((cpu.vdp.hint_pending, cpu.vdp.vint_pending, cpu.irq), (0, 0, 0))
        self.assertEqual(cpu.vdp.fifo_cycles, [0] * 4)
        self.assertTrue(cpu.vdp.first_frame and cpu.vdp.admitted)

    def test_scheduler_uses_run_deadlines_including_equal_boundary(self):
        line = self.sources.line_mclk
        for raw, vc, start in ((0, 192, 0), (line - 1, 192, 0), (line, 193, line),
                               (69 * line, 261, 69 * line), (70 * line, 0, 70 * line),
                               (229 * line - 1, 158, 228 * line), (229 * line, 159, 229 * line),
                               (230 * line, 160, 230 * line)):
            dev = self.device()
            dev.admit_first_frame(raw, self.sources, self.profile, self.frame)
            self.assertEqual((dev.v_counter, dev.mcycles, dev.line_end), (vc, start, start + line), raw)
        with self.assertRaisesRegex(mini68k.Unsupported, 'frame-end'):
            self.device().admit_first_frame(262 * line, self.sources, self.profile, self.frame)
        cpu = self.cpu()
        cpu.raw = cpu.vdp.line_end
        with self.assertRaisesRegex(mini68k.Unsupported, 'scheduler handoff'):
            cpu.advance_phase()

    def test_real_mini68k_step_owns_status_move_and_ccr(self):
        calls = []
        step = mini68k.CPU.step
        def actual(cpu, *args):
            calls.append(cpu.pc)
            return step(cpu, *args)
        with mock.patch.object(mini68k.CPU, 'step', actual):
            cpu = ce.generate_first_vdp(self.rom, self.sources, self.profile)
        self.assertEqual(calls[-1], 0x238)
        self.assertEqual(len(calls), 9)  # existing eight forms plus actual MOVE.W.
        self.assertEqual((cpu.d[0], cpu.sr, cpu.ir, cpu.pc), (0x7208, 0x2700, 0x3014, 0x23A))
        ce.compare_first_vdp(cpu, self.expected)

    def test_handler_effects_precede_logical_observation_then_registers_and_charge(self):
        cpu = self.at()
        cpu.vdp.pending = 1
        cpu.vdp.status = 0x60  # synthetic collision/overflow: absent from native ABI, unit control only.
        cpu.d[0], cpu.x = 0x12340000, 1
        raw, deadline, old_sr = cpu.raw, cpu.refresh, cpu.sr
        observed = []
        emit = cpu.emit
        def observe(kind, *args, **kwargs):
            if kind == 6:
                observed.append((cpu.vdp.pending, cpu.vdp.status, cpu.d[0], cpu.sr,
                                 cpu.raw, cpu.vdp.last_sample, cpu.cursor, cpu.ir))
            return emit(kind, *args, **kwargs)
        with mock.patch.object(cpu, 'emit', observe):
            cpu.advance_phase()
        self.assertEqual(observed, [(0, 0, 0x12340000, old_sr, raw, raw + 8 * 7, 0x23A, 0x3014)])
        read, retire = cpu.events[-2:]
        self.assertEqual((read[3], read[5], read[14], read[11]), (raw, old_sr, 0x12340000, 0x7268))
        self.assertEqual((retire[3], retire[4], retire[5], retire[14]),
                         (raw + 8 * 7, deadline, 0x2710, 0x12347268))

    def test_open_bus_uses_advanced_rom_pc_not_ir_or_prefetch(self):
        cpu = self.at()
        self.assertEqual((cpu.cursor, cpu.ir, cpu.pref_data), (0x23A, 0x3014, 0x7000))
        cpu.pref_data = 0xFC00  # even a different prefetch latch must not supply the bus bits.
        rom = bytearray(cpu.rom); rom[0x23A:0x23C] = b'\xab\xcd'; cpu.rom = bytes(rom)
        cpu.advance_phase()
        self.assertEqual(cpu.events[-2][11], (0xABCD & 0xFC00) | 0x208)
        self.assertNotEqual(cpu.events[-2][11], (cpu.ir & 0xFC00) | 0x208)
        self.assertEqual(cpu.events[-2][10:13], (0xC00004, 0xAA08, 2))
        self.assertEqual(sum(row[0] == 6 for row in cpu.events), 14)  # direct open bus is not another read.

    def test_sample_clock_is_local_and_base_charged_once(self):
        cpu = self.at()
        raw = cpu.raw
        cpu.advance_phase()
        self.assertEqual((cpu.vdp.last_sample, cpu.events[-2][3], cpu.events[-1][3]),
                         (raw + 56, raw, raw + 56))
        cpu = self.at()
        cpu.raw = cpu.vdp.mcycles + cpu.vdp.hblank_start - self.sources.table[0x3014] * 7
        raw = cpu.raw
        cpu.advance_phase()
        self.assertLess(raw - cpu.vdp.mcycles, cpu.vdp.hblank_start)
        self.assertEqual(cpu.events[-2][11] & 4, 4)  # sampled after base cost, not raw observation time.
        self.assertEqual(cpu.events[-2][3], raw)
        self.assertEqual(cpu.raw, raw + 56)

    def test_fifo_empty_full_and_partial_predicates_and_index_order(self):
        dev = self.device(); dev.fifo_idx = 1
        dev.fifo_cycles = [156, 157, 0, 0]
        self.assertEqual(dev.control_read(100, 56) & 0x300, 0x200)  # last == sample wins.
        self.assertEqual(dev.control_read(99, 56) & 0x300, 0x100)  # oldest > sample.
        dev.fifo_cycles = [200, 100, 0, 0]
        self.assertEqual(dev.control_read(100, 56) & 0x300, 0)  # partial, neither bit.
        self.assertEqual(dev.status, 0)  # returned FIFO/display bits do not persist in status.

    def test_hblank_source_interval_is_closed_then_open(self):
        for sample, bit in ((279, 0), (280, 4), (859, 4), (860, 0)):
            dev = self.device(); dev.mcycles = 1000
            self.assertEqual(dev.control_read(1000 + sample - 56, 56) & 4, bit, sample)

    def test_vint_requires_height_threshold_and_unasserted_z80_irq(self):
        for vc, relative, irq, bit in ((159, 770, False, 0), (192, 769, False, 0),
                                       (192, 770, False, 0x80), (192, 771, True, 0)):
            dev = self.device(); dev.v_counter = vc; dev.z80_irq = irq
            self.assertEqual(dev.control_read(relative - 56, 56) & 0x80, bit, (vc, relative, irq))
            self.assertEqual((dev.vint_pending, dev.status), (0, 0))

    def test_dma_busy_completion_precedes_return_and_pending_clear(self):
        for length, end, bit in ((0, 156, 0), (0, 157, 2), (1, 156, 2)):
            dev = self.device(); dev.status = 0x62; dev.pending = 1
            dev.dma_length, dev.dma_end = length, end
            result = dev.control_read(100, 56)
            self.assertEqual(result & 2, bit)
            self.assertEqual(result & 0x60, 0x60)  # return captures flags before clear.
            self.assertEqual((dev.pending, dev.status), (0, bit))
        dev = self.device(); dev.reg[1] = 0x40
        self.assertEqual(dev.control_read(100, 56) & 8, 0)
        dev.reg[1] = 0
        self.assertEqual(dev.control_read(100, 56) & 8, 8)

    def test_render_false_is_native_non_skip_and_other_profiles_refuse(self):
        for change in ({'native_skip': 1}, {'native_skip': False}, {'managed_render': True},
                       {'overscan': 0}, {'memory_callbacks': True},
                       {'input_devices': list(self.frame.input_devices)},
                       {'input_devices': (1, -1, -1, -1, 0, -1, -1, -1)}):
            with self.assertRaises(ce.Refused, msg=change):
                ce.generate_first_vdp(self.rom, self.sources, self.profile, replace(self.frame, **change))
        with self.assertRaises(ce.Refused):
            ce.generate_first_vdp(self.rom, self.sources, self.profile, object())
        for attribute, value in (('interlaced', 1), ('dma_length', 1)):
            dev = self.device(); setattr(dev, attribute, value)
            with self.assertRaises(mini68k.Unsupported):
                dev.admit_first_frame(self.cpu().raw, self.sources, self.profile, self.frame)
        dev = self.device(); dev.reg[1] = 0x40
        with self.assertRaises(mini68k.Unsupported):
            dev.admit_first_frame(self.cpu().raw, self.sources, self.profile, self.frame)

    def test_unknown_device_source_rom_and_profile_fail_closed(self):
        cpu = self.at('boundary'); cpu.a[4] = 0xC00008
        before = cpu.snapshot()
        with self.assertRaises(mini68k.Unsupported):
            cpu.advance_phase()
        self.assertEqual(cpu.snapshot(), before)
        for address, size in ((0xC00004, 'b'), (0xC00000, 'w'), (0xC00008, 'w')):
            with self.assertRaises(mini68k.Unsupported):
                self.at().read(address, size)
        with self.assertRaises(mini68k.Unsupported):
            self.at().write(0xC00004, 'w', 0)
        with self.assertRaises(ce.Refused):
            ce.generate_first_vdp(self.rom, ce.Sources(os.environ['GPGX_GIT'],
                os.environ['COLD_ENTRY_TRACECHASER']), self.profile)
        real_git = ce._git
        def wrong_bizhawk(repo, *args):
            if str(repo) == os.environ['BIZHAWK_GIT'] and args[0] == 'rev-parse':
                return b'0' * 40 + b'\n'
            return real_git(repo, *args)
        with mock.patch.object(ce, '_git', wrong_bizhawk), self.assertRaises(ce.Refused):
            ce.FirstVDPSources(os.environ['GPGX_GIT'], os.environ['COLD_ENTRY_TRACECHASER'],
                               os.environ['BIZHAWK_GIT'])
        rom = bytearray(self.rom); rom[0x23A] ^= 1
        with self.assertRaises(ce.Refused):
            ce.generate_first_vdp(bytes(rom), self.sources, self.profile)
        with self.assertRaises(ce.Refused):
            ce.generate_first_vdp(self.rom, self.sources, replace(self.profile, bios=1))

    def test_every_new_record_word_mutation_and_record_order_disagrees(self):
        for index in range(38, 41):
            for word in range(40):
                report = copy.deepcopy(self.expected); report['records'][index][word] ^= 1
                with self.assertRaises(ce.Refused, msg=(index, word)):
                    ce.compare_first_vdp(self.golden, report)
        for action in ('omit', 'duplicate', 'reorder'):
            report = copy.deepcopy(self.expected)
            if action == 'omit': report['records'].pop(39)
            elif action == 'duplicate': report['records'].insert(39, list(report['records'][39]))
            else: report['records'][38:41] = report['records'][38:41][::-1]
            with self.assertRaises(ce.Refused, msg=action):
                ce.compare_first_vdp(self.golden, renumber(report))

    def test_complete_pure_continuations_before_and_after_status_read(self):
        cpu = self.cpu(); checkpoints = []
        while cpu.phase != 'first_status_retired':
            checkpoints.append(cpu.snapshot()); cpu.advance_phase()
        checkpoints.append(cpu.snapshot())
        self.assertTrue({(0x238, 'boundary'), (0x238, 'refresh'), (0x238, 'execute'),
                         (0x23A, 'first_status_retired')}.issubset(
                             {(c.state['pc'], c.state['phase']) for c in checkpoints}))
        for snapshot in checkpoints:
            resumed = self.cpu(); resumed.restore(snapshot); self.finish(resumed)
            self.assertEqual(resumed.snapshot(), cpu.snapshot())
        snapshot = self.at().snapshot(); snapshot.state['vdp'].fifo_cycles[0] = 123
        self.assertEqual(self.at().vdp.fifo_cycles[0], 0)  # nested device state is detached.
        resumed = self.cpu(); resumed.restore(snapshot); snapshot.state['vdp'].pending = 1
        self.assertEqual(resumed.vdp.pending, 0)
        for bad in (replace(snapshot, source='wrong'), replace(snapshot, rom_sha256='wrong')):
            with self.assertRaises(ce.Refused): resumed.restore(bad)
        snapshot.state['frame_profile'] = replace(self.frame, native_skip=1)
        with self.assertRaises(ce.Refused): resumed.restore(snapshot)

    def test_expected_only_dataflow_and_status_producer_fault(self):
        with mock.patch.object(ce, '_load_expected_with_digest', side_effect=AssertionError('expected input')):
            cpu = ce.generate_first_vdp(self.rom, self.sources, self.profile)
        before = cpu.snapshot()
        report = copy.deepcopy(self.expected); report['records'][39][11] ^= 1
        with self.assertRaises(ce.Refused): ce.compare_first_vdp(cpu, report)
        self.assertEqual(cpu.snapshot(), before)
        with mock.patch.object(ce.FirstVDP, 'control_read', return_value=0):
            wrong = ce.generate_first_vdp(self.rom, self.sources, self.profile)
        with self.assertRaises(ce.Refused): ce.compare_first_vdp(wrong, self.expected)
        with self.assertRaises(ce.Refused): ce.compare_first_vdp(self.at(), self.expected)

    def test_opt_in_cli_preserves_bounded_same_buffer_identity(self):
        original = Path(os.environ['COLD_ENTRY_EXPECTED']).read_bytes()
        args = ['--rom', os.environ['S3K_ROM'], '--gpgx-git', os.environ['GPGX_GIT'],
                '--tracechaser', os.environ['COLD_ENTRY_TRACECHASER'], '--expected', 'comparison-only',
                '--profile', ce.PROFILE_NAME, '--first-vdp-status', '--bizhawk-git', os.environ['BIZHAWK_GIT']]
        reads, completed = [], []
        generate = ce.generate_first_vdp
        def execute(*a, **kw):
            cpu = generate(*a, **kw); completed.append(cpu.phase); return cpu
        class ExpectedStream(io.BytesIO):
            def read(self, limit=-1):
                self_test.assertEqual(completed, ['first_status_retired'])
                reads.append(limit)
                return super().read(limit)
        self_test = self
        real_open = Path.open
        def open_path(path, *a, **kw):
            return ExpectedStream(original) if str(path) == 'comparison-only' else real_open(path, *a, **kw)
        output = io.StringIO()
        with mock.patch.object(ce, 'generate_first_vdp', execute), mock.patch.object(Path, 'open', open_path), redirect_stdout(output):
            self.assertEqual(ce.main(args), 0)
        result = json.loads(output.getvalue())
        self.assertEqual((result['claimed_records'], result['native_expected_sha256'], reads),
                         (41, ce.digest(original), [65537]))
        self.assertIn('core/vdp_render.c', result['source_manifest'])
        self.assertIn('BizHawk:waterbox/gpgx/cinterface/cinterface.c', result['source_manifest'])
        with mock.patch.object(ce, '_load_expected_with_digest', side_effect=AssertionError('unexpected report read')):
            with redirect_stderr(io.StringIO()):
                self.assertEqual(ce.main(args[:-2]), 1)  # opt-in missing BizHawk store.
                self.assertEqual(ce.main([a for a in args if a != '--first-vdp-status']), 1)


if __name__ == '__main__':
    unittest.main()
