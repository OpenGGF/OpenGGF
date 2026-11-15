"""Reject incomplete or misattributed diagnostic events; no retail inputs."""
from pathlib import Path
import tempfile
import unittest

from native_owner_analyze import check_events, RECORD
from native_owner_schema import FIELDS


def stream(extra=None):
    rows = []
    epochs = dict(scheduler_epoch=0, cd_epoch=0, main_epoch=0, pcm_epoch=0, reset_epoch=0, gfx_operation=0)
    def emit(kind, **values):
        if kind in {4: 1, 6: 1, 8: 1, 11: 1}:
            epochs[{4: 'scheduler_epoch', 6: 'cd_epoch', 8: 'main_epoch', 11: 'pcm_epoch'}[kind]] += 1
        row = dict.fromkeys(FIELDS, 0)
        row.update(epochs, call=1, kind=kind, sequence_lo=len(rows) + 1)
        row.update(values)
        rows.append(row)
    emit(1)
    emit(3, scd_cycles=3184)
    emit(4, scd_cycles=0)
    if extra:
        extra(emit)
    emit(5, s68k_cycles=3250, gfx_cycles=3000, cdc_cycle0=3200, cdc_cycle1=3300)
    emit(6, aux0=3184, s68k_cycles=66, gfx_cycles=(-184) & 0xffffffff, cdc_cycle0=16, cdc_cycle1=116)
    emit(7, m68k_cycles=3430)
    emit(8, aux0=3420, m68k_cycles=10)
    emit(9)
    emit(10, pcm_cycles=3072)
    emit(11, pcm_cycles=0)
    emit(2)
    return rows


def write(path, rows):
    path.write_bytes(b''.join(RECORD.pack(*(r[n] & 0xffffffff for n in FIELDS)) for r in rows))


class LedgerControls(unittest.TestCase):
    def check(self, rows):
        with tempfile.TemporaryDirectory() as name:
            p = Path(name) / 'events.bin'
            write(p, rows)
            return check_events(p, 1)

    def test_signed_residual_and_unsigned_wrap_are_preserved(self):
        result = self.check(stream())
        self.assertEqual(result['events'], 11)
        self.assertEqual(result['epochs']['cd_epoch'], 1)

    def test_write_identity_and_long_width_survive(self):
        def access(emit):
            emit(14, cpu=2, width=4, address=0xff8064)
            emit(15, cpu=2, width=4, address=0xff8064)
        result = self.check(stream(access))
        self.assertEqual(result['cpu_width_phase_counts']['2/4/write_before_handler'], 1)

    def test_rejects_lost_cpu_identity(self):
        with self.assertRaisesRegex(ValueError, 'CPU/width'):
            self.check(stream(lambda emit: emit(14, cpu=0, width=2)))

    def test_rejects_unknown_width(self):
        with self.assertRaisesRegex(ValueError, 'CPU/width'):
            self.check(stream(lambda emit: emit(14, cpu=1, width=3)))

    def test_rejects_protection_write_as_ownership_commit(self):
        with self.assertRaisesRegex(ValueError, 'nonownership bits'):
            self.check(stream(lambda emit: emit(16, width=1, address=0xa12002, aux0=1, aux1=0x0101, word_mode=0x0201)))

    def test_accepts_native_mirrored_ownership_handler_legs(self):
        for handler, width, address in [(1, 1, 0xa12043), (1, 2, 0xa12082),
                (1, 2, 0xa120c3), (2, 1, 0xff8202), (2, 1, 0x1ffe03),
                (2, 2, 0x0ffe02), (2, 2, 0xff8003)]:
            with self.subTest(handler=handler, width=width, address=hex(address)):
                result = self.check(stream(lambda emit: emit(16, width=width,
                    address=address, value=2, aux0=handler, aux1=1, word_mode=2, dmna=1)))
                self.assertEqual(result['kinds']['wordram_handler_commit'], 1)

    def test_rejects_unmapped_reserved_or_invalid_width_ownership_labels(self):
        for handler, width, address in [(1, 1, 0xa12042), (1, 2, 0xa12102),
                (1, 2, 0xb12002), (2, 1, 0xfe8203), (2, 1, 0xff4003),
                (2, 2, 0xff8204), (1, 4, 0xa12002), (2, 4, 0xff8002),
                (0, 1, 0xa12003), (3, 1, 0xff8003)]:
            with self.subTest(handler=handler, width=width, address=hex(address)):
                with self.assertRaisesRegex(ValueError, 'owning register handler'):
                    self.check(stream(lambda emit: emit(16, width=width, address=address,
                        aux0=handler, aux1=1, word_mode=2, dmna=1)))

    def test_rejects_incomplete_cpu_write(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete native write'):
            self.check(stream(lambda emit: emit(14, cpu=2, width=2, address=0xff8066)))

    def test_rejects_clock_epoch_guess(self):
        rows = stream()
        rows[1]['cd_epoch'] = 1
        with self.assertRaisesRegex(ValueError, 'epoch'):
            self.check(rows)

    def test_rejects_rebase_amount_mismatch(self):
        rows = stream()
        next(r for r in rows if r['kind'] == 6)['aux0'] += 1
        with self.assertRaisesRegex(ValueError, 'CD rebase arithmetic'):
            self.check(rows)

    def test_rejects_missing_final_call_boundary(self):
        with self.assertRaisesRegex(ValueError, 'Incomplete native call'):
            self.check(stream()[:-1])

    def test_rejects_reordered_events(self):
        rows = stream()
        rows[1], rows[2] = rows[2], rows[1]
        with self.assertRaisesRegex(ValueError, 'sequence'):
            self.check(rows)

    def test_rejects_truncated_abi(self):
        with tempfile.TemporaryDirectory() as name:
            p = Path(name) / 'events.bin'
            p.write_bytes(b'\0' * 255)
            with self.assertRaisesRegex(ValueError, 'Truncated'):
                check_events(p, 1)

    def test_rejects_unlaunched_graphics_completion(self):
        with self.assertRaisesRegex(ValueError, 'no observed launch'):
            self.check(stream(lambda emit: emit(19)))


if __name__ == '__main__':
    unittest.main()
