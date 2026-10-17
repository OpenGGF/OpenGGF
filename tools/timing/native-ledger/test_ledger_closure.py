# Origin: S3K Kos CPU-budget research, native stall ABI v2 first exact interval (native-stall-counters lane, Claude
#   Opus 5.5, 2026-10-17; original sha256 af521b1d...), corrected by Root (OpenAI Codex GPT-6.1) after review
#   findings N1/N4/N10 and the odd-cycle/label-coverage follow-up (2026-10-17).
# Promoted into OpenGGF tools/timing on 2026-10-17 at base 00f8ab78a; promotion changed only the default decoder
#   directory (../m68k-closure) and this header.
# Inputs: env GPGX_GIT (Genesis-Plus-GX repo holding 051d430d) is REQUIRED (setUpClass errors without it);
#   M68K_CLOSURE_TOOLS overrides ../m68k-closure. No ROM or capture needed.
# Diagnostic only: no runtime cost, gameplay state, readiness or timing authority; not registered with the engine.
"""Behavioral safety checks for native-v2 ledger evidence (OpenGGF S3K parity, 2026-10-17).

Uses a tiny hand-constructed NOP window and the pinned GPGX source cost table. No emulator runs or ROM fixture
is needed. Set GPGX_GIT to its existing object store and M68K_CLOSURE_TOOLS to the sibling decoder directory.
"""
import contextlib
import copy
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest

import ledger_closure as lc

TOOLS = Path(os.environ.get('M68K_CLOSURE_TOOLS', Path(__file__).resolve().parent.parent / 'm68k-closure'))
sys.path.insert(0, str(TOOLS))
import gpgx_cycles
import m68k_timing


class LedgerChecks(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.git = os.environ['GPGX_GIT']
        cls.table = gpgx_cycles.load_table(cls.git)

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.run = self.root / 'run'
        self.run.mkdir()
        self.rom = self.root / 'nop.bin'
        self.rom.write_bytes(bytes.fromhex('4e71 6706'))
        kinds = {k: 0 for k in (*lc.KINDS, 'dma_rewind')}
        first = dict(sequence=0, complete=False, frame=0, label=1, open_accept_ordinal=-1,
                     close_accept_ordinal=0, entries=1, executed=1, open_stamp_mclk=0,
                     close_stamp_mclk=28, straddle_pc=-1, straddle_context=0, straddle_mclk=0,
                     carry_pc=-1, carry_mclk=0, refresh_events=0, accept_vint=0, accept_hint=0,
                     accept_other=0, irq_delay_inline=0, synthetic_rte=0, abandoned_frames=0,
                     kind_mclk=kinds)
        second = copy.deepcopy(first)
        second.update(sequence=1, complete=True, frame=1, label=2, open_accept_ordinal=0,
                      close_accept_ordinal=1, open_stamp_mclk=28, close_stamp_mclk=406, accept_vint=1)
        second['kind_mclk']['accept_vint'] = 350  # table index 0: 50 cycles * 7
        self.intervals = [first, second]
        self.accepts = [dict(ordinal=i, level=6, inside_instruction=0, table_index=0, cycles=50,
                             return_pc=0, stamp_mclk=stamp, frame=i, label=i+1,
                             closes_bank_sequence=i) for i, stamp in enumerate((28, 406))]
        self.frames = [dict(frame=i, completed_frame=i+1, label=i+1, vint_accepts=1,
                            stamp_mclk=stamp+400, context=0) for i, stamp in enumerate((28, 406))]
        self.slots = 'sequence,pc,context,count,mclk\n0,0,0,1,28\n1,0,0,1,28\n'
        self.meta = dict(schema='openggf.m68k-ledger-window.v1', ingress_kind='declared_setup',
                         rom_sha1=hashlib.sha1(self.rom.read_bytes()).hexdigest(),
                         movie_sha256='a'*64, core_sha256='b'*64, frames=2, ledger=True,
                         intervals_complete=1, acceptances=2,
                         ingress_state_sha256='c'*64, ingress_provenance=dict(ingress_kind='declared_setup',
                         rom_sha1=hashlib.sha1(self.rom.read_bytes()).hexdigest(), movie_sha256='a'*64,
                         core_sha256='b'*64, state_sha256='c'*64, region='ntsc'))
        self.write_run()

    def write_jsonl(self, name, rows):
        (self.run/name).write_text(''.join(json.dumps(x)+'\n' for x in rows))

    def write_run(self):
        self.write_jsonl('intervals.jsonl', self.intervals)
        self.write_jsonl('accepts.jsonl', self.accepts)
        self.write_jsonl('frames.jsonl', self.frames)
        (self.run/'interval-slots.csv').write_text(self.slots)
        (self.run/'audio-window.s16').write_bytes(b'')
        (self.run/'state-end.bin').write_bytes(b'fake state not interpreted by the offline checker')
        self.rehash()

    def rehash(self):
        self.meta['files_sha256'] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                                     for p in self.run.iterdir() if p.name != 'metadata.json'}
        (self.run/'metadata.json').write_text(json.dumps(self.meta))

    def check(self, *extra):
        out = io.StringIO()
        with contextlib.redirect_stdout(out):
            code = lc.main([str(self.run), '--rom', str(self.rom), '--gpgx-git', self.git,
                            '--tools', str(TOOLS), *extra])
        return code, out.getvalue()

    def test_complete_source_cost_window_passes(self):
        self.assertEqual(0, self.check()[0])

    def test_branch_mix_is_not_reported_exact(self):
        rom = self.rom.read_bytes()
        word = lambda pc: (rom[pc] << 8) | rom[pc+1]
        # BEQ.s: one 10-cycle taken + one 8-cycle fall, with no flow observation.
        result, _ = lc.admissible(m68k_timing.Decoder(rom), self.table, word, 2, 2, 126)
        self.assertEqual('mix-admissible', result)

    def test_truncated_interval_file_cannot_pass_a_subset(self):
        self.intervals = self.intervals[1:]  # remaining complete interval still closes numerically
        self.write_jsonl('intervals.jsonl', self.intervals)
        self.assertNotEqual(0, self.check()[0])

    def test_rehashed_missing_partial_interval_is_still_refused(self):
        self.intervals = self.intervals[1:]
        self.write_run()
        self.assertNotEqual(0, self.check()[0])

    def test_rehashed_acceptance_stamp_mismatch_is_refused(self):
        self.accepts[1]['stamp_mclk'] += 7
        self.write_run()
        self.assertNotEqual(0, self.check()[0])

    def test_rehashed_wrong_vint_bank_link_is_refused(self):
        self.accepts[1]['closes_bank_sequence'] = 0
        self.write_run()
        self.assertNotEqual(0, self.check()[0])

    def test_rehashed_acceptance_cost_mismatch_is_refused(self):
        self.accepts[0]['cycles'] = 51
        self.write_run()
        self.assertNotEqual(0, self.check()[0])

    def test_unlisted_required_file_is_refused(self):
        self.meta['files_sha256'].pop('accepts.jsonl')
        (self.run/'metadata.json').write_text(json.dumps(self.meta))
        self.assertNotEqual(0, self.check()[0])

    def test_rehashed_incorrect_slot_count_is_refused(self):
        self.intervals[1]['entries'] = 2
        self.write_run()
        self.assertNotEqual(0, self.check()[0])

    def test_rehashed_duplicate_slots_are_refused(self):
        self.slots += '1,0,0,1,28\n'
        self.intervals[1].update(entries=2, executed=2, close_stamp_mclk=434)
        self.accepts[1]['stamp_mclk'] = 434
        self.write_run()
        self.assertNotEqual(0, self.check()[0])

    def test_invalid_slot_context_is_refused(self):
        self.slots = self.slots.replace('1,0,0,1,28', '1,0,9,1,28')
        self.write_run()
        self.assertNotEqual(0, self.check()[0])

    def test_rehashed_false_complete_count_is_refused(self):
        self.meta['intervals_complete'] = 2
        self.rehash()
        self.assertNotEqual(0, self.check()[0])

    def test_unproved_ingress_label_is_refused(self):
        self.meta['ingress_kind'] = self.meta['ingress_provenance']['ingress_kind'] = 'authentic'
        self.rehash()
        self.assertNotEqual(0, self.check()[0])

    def test_rom_identity_mismatch_is_refused(self):
        self.rom.write_bytes(bytes.fromhex('4e71 6606'))
        self.assertNotEqual(0, self.check()[0])

    def test_named_fixed_instruction_count_control_fails_cost_check(self):
        code, output = self.check('--perturb', 'count:1:0x0:0')
        self.assertEqual(1, code, output)
        self.assertIn('outside', output)

    def test_conserved_dma_misclassification_fails_source_cost_not_identity(self):
        self.intervals[1]['kind_mclk']['dma'] = 7
        self.intervals[1]['close_stamp_mclk'] += 7
        self.accepts[1]['stamp_mclk'] += 7
        self.write_run()
        code, output = self.check('--perturb', 'conserve_dma:1:0x0:0')
        self.assertEqual(1, code, output)
        row = json.loads(output.splitlines()[0])
        self.assertEqual(0, row[2])  # Conservation still holds; the independent cost check must fail.
        self.assertIn('outside', output)

    def test_conditional_count_can_remain_ambiguous_without_claiming_exact(self):
        # Four taken BEQs = 40 cycles; five falls also = 40. A count error cannot be inferred from this aggregate.
        self.slots = self.slots.replace('1,0,0,1,28', '1,2,0,4,280')
        self.intervals[1].update(executed=4, close_stamp_mclk=658)
        self.accepts[1]['stamp_mclk'] = 658
        self.write_run()
        code, output = self.check('--perturb', 'count:1:0x2:0')
        self.assertEqual(0, code, output)
        self.assertIn('mix-admissible', output)
        self.assertNotIn('"exact"', output)

    def test_bounded_scc_rejects_an_impossible_odd_cycle(self):
        rom = bytes.fromhex('5bc0')  # smi D0: 4 or 6 cycles, never 5
        word = lambda pc: (rom[pc] << 8) | rom[pc+1]
        kind, _ = lc.admissible(m68k_timing.Decoder(rom), self.table, word, 0, 1, 35)
        self.assertEqual('fail', kind)

    def test_mix_bounded_dbcc_rejects_an_impossible_odd_cycle(self):
        rom = bytes.fromhex('5bc8 fffc')  # dbmi D0: 10, 12 or 14 cycles, never 13
        word = lambda pc: (rom[pc] << 8) | rom[pc+1]
        kind, _ = lc.admissible(m68k_timing.Decoder(rom), self.table, word, 0, 1, 91)
        self.assertEqual('fail', kind)

    def test_bounded_even_cycle_ambiguity_remains_explicit(self):
        rom = bytes.fromhex('5bc0')
        word = lambda pc: (rom[pc] << 8) | rom[pc+1]
        # Moving two cycles can imitate a real operand/condition change; an aggregate cannot decide it.
        self.assertEqual('bounded', lc.admissible(m68k_timing.Decoder(rom), self.table, word, 0, 1, 42)[0])

    def test_missing_requested_labels_are_incomplete(self):
        code, output = self.check('--labels', '1-3')
        self.assertEqual(2, code, output)
        self.assertIn('INCOMPLETE', output)

    def test_one_covered_label_passes(self):
        self.assertEqual(0, self.check('--labels', '2-2')[0])

    def test_control_outside_selected_intervals_is_usage_error(self):
        self.assertEqual(3, self.check('--perturb', 'count:0')[0])

    def test_invalid_label_syntax_returns_usage_error(self):
        with contextlib.redirect_stderr(io.StringIO()):
            try:
                result = self.check('--labels', '2--3')[0]
            except ValueError:
                self.fail('label parser raised instead of returning the documented usage status')
        self.assertEqual(3, result)

    def test_unknown_pc_reports_incomplete(self):
        self.slots = self.slots.replace('1,0,0,1,28', '1,256,0,1,28')
        self.write_run()
        self.assertEqual(2, self.check()[0])

    def test_corrupt_structure_returns_the_evidence_error_status(self):
        (self.run/'metadata.json').write_text('[]')
        self.assertEqual(3, self.check()[0])

    def test_kind_and_refresh_controls_are_timing_failures(self):
        for control in ('kind:1', 'refresh:1'):
            with self.subTest(control=control):
                self.assertEqual(1, self.check('--perturb', control)[0])

    def add_carry_interval(self):
        self.intervals[1].update(close_stamp_mclk=392, straddle_pc=0, straddle_mclk=14)
        self.slots = self.slots.replace('1,0,0,1,28', '1,0,0,1,14') + '2,0,0,1,28\n'
        self.accepts[1].update(stamp_mclk=392, inside_instruction=1)
        third = copy.deepcopy(self.intervals[1])
        third.update(sequence=2, frame=2, label=3, open_accept_ordinal=1, close_accept_ordinal=2,
                     open_stamp_mclk=392, close_stamp_mclk=784, straddle_pc=-1, straddle_mclk=0,
                     carry_pc=0, carry_mclk=14)
        self.intervals.append(third)
        self.accepts.append(dict(self.accepts[0], ordinal=2, stamp_mclk=784, frame=2, label=3,
                                 closes_bank_sequence=2))
        self.frames.append(dict(self.frames[1], frame=2, completed_frame=3, label=3, stamp_mclk=1184))
        self.meta.update(frames=3, intervals_complete=2, acceptances=3)
        self.write_run()

    def test_straddle_closes_with_the_next_interval_carry(self):
        self.add_carry_interval()
        self.assertEqual(0, self.check()[0])

    def test_bad_carry_time_is_refused(self):
        self.add_carry_interval()
        self.intervals[2]['carry_mclk'] += 7
        self.write_run()
        self.assertEqual(1, self.check()[0])

    def test_nonzero_residual_is_fail_even_if_last_carry_is_missing(self):
        self.intervals[1].update(close_stamp_mclk=392, straddle_pc=0, straddle_mclk=14)
        self.intervals[1]['kind_mclk']['z80_bus'] = 7
        self.slots = self.slots.replace('1,0,0,1,28', '1,0,0,1,14')
        self.accepts[1].update(stamp_mclk=392, inside_instruction=1)
        self.write_run()
        self.assertEqual(1, self.check()[0])

    def test_deferred_carry_is_incomplete_when_conservation_holds(self):
        self.intervals[1].update(close_stamp_mclk=392, straddle_pc=0, straddle_mclk=14)
        self.slots = self.slots.replace('1,0,0,1,28', '1,0,0,1,14')
        self.accepts[1].update(stamp_mclk=392, inside_instruction=1)
        self.write_run()
        self.assertEqual(2, self.check()[0])

    def test_hint_and_other_acceptance_costs_are_joined_to_the_interval(self):
        for level, kind in [(4, 'hint'), (2, 'other')]:
            with self.subTest(level=level):
                self.intervals[1].update(close_accept_ordinal=2, close_stamp_mclk=756)
                self.intervals[1]['accept_hint'] = int(level == 4)
                self.intervals[1]['accept_other'] = int(level == 2)
                self.intervals[1]['kind_mclk'].update(accept_hint=350 if level == 4 else 0,
                                                      accept_other=350 if level == 2 else 0)
                self.accepts = [self.accepts[0], dict(self.accepts[0], ordinal=1, level=level, frame=1,
                                label=2, stamp_mclk=56, closes_bank_sequence=-1),
                                dict(self.accepts[0], ordinal=2, frame=1, label=2,
                                     stamp_mclk=756, closes_bank_sequence=1)]
                self.meta['acceptances'] = 3
                self.write_run()
                self.assertEqual(0, self.check()[0])

    def test_cited_s3k_ram_cost_is_not_assumed_for_another_rom(self):
        rom = self.rom.read_bytes()
        word = lambda pc: (rom[pc] << 8) | rom[pc+1]
        self.assertEqual('unsupported', lc.admissible(m68k_timing.Decoder(rom), self.table, word,
                                                     0xFFFFF0, 1, 84)[0])


if __name__ == '__main__':
    unittest.main()
