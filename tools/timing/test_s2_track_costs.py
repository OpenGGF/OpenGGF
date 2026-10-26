#!/usr/bin/env python3
# Purpose: independent instruction vectors, format checks and persistent native-track replay.
# Inputs: synthetic instructions/bitstreams; optional SONIC2_ROM original World REV01 ROM.
# Origin: 2026-10-22 s2_chain_pacing task, implementation milestone 44c1d674004c.
import contextlib
import io
import os
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import s2_track_costs as track


def cpu(words, cls=track.TrackCPU):
    rom = bytearray(0x8000)
    for index, word in enumerate(words):
        rom[0x1000 + index * 2:0x1002 + index * 2] = word.to_bytes(2, "big")
    model = cls(bytes(rom))
    model.pc = 0x1000
    return model


class TrackInstructions(unittest.TestCase):
    def test_movem_word_save_order_restore_sign_extension_and_flags(self):
        model = cpu([0x48A3, 0x3F00, 0x4C9B, 0x00FC])
        original = [0x11110001, 0x22227FFF, 0x33338000, 0x4444FFFF, 0x5555AAAA, 0x66660000]
        model.d[2:8], model.a[3] = original, 0xFFDB4A
        model.x, model.n, model.z, model.v, model.c = 1, 1, 0, 1, 1
        model.step({}, {})
        self.assertEqual(model.a[3], 0xFFDB3E)
        self.assertEqual(bytes(model.ram[0xDB3E:0xDB4A]), bytes.fromhex("00017fff8000ffffaaaa0000"))
        model.d[2:8] = [0] * 6
        model.step({}, {})
        self.assertEqual(model.d[2:8], [1, 0x7FFF, 0xFFFF8000, 0xFFFFFFFF, 0xFFFFAAAA, 0])
        self.assertEqual(model.a[3], 0xFFDB4A)
        self.assertEqual((model.x, model.n, model.z, model.v, model.c), (1, 1, 0, 1, 1))

    def test_indexed_pc_jump_uses_extension_address_signed_word_and_displacement(self):
        model = cpu([0x4EFB, 0x00FC])  # -4(PC,D0.W)
        model.d[0] = 0xABCDFFF8  # upper register half is not an index
        model.x, model.n, model.z, model.v, model.c = 1, 0, 1, 0, 1
        model.step({}, {})
        self.assertEqual(model.pc, 0xFF6)
        self.assertEqual((model.x, model.n, model.z, model.v, model.c), (1, 0, 1, 0, 1))

    def test_memory_set_clear_preserve_flags_and_sign_extend_absolute_word(self):
        model = cpu([0x50F8, 0xDB0E, 0x51F8, 0xDB0E])
        model.x, model.n, model.z, model.v, model.c = 1, 1, 0, 1, 1
        model.step({}, {})
        self.assertEqual(model.ram[0xDB0E], 0xFF)
        model.step({}, {})
        self.assertEqual(model.ram[0xDB0E], 0)
        self.assertEqual((model.x, model.n, model.z, model.v, model.c), (1, 1, 0, 1, 1))

    def test_all_track_byte_rotate_forms_wrap_and_leave_extend(self):
        for register in (2, 4):
            for left, counts in ((False, (1, 2, 3, 4)), (True, (1, 2, 3))):
                for count in counts:
                    opcode = 0xE018 | count << 9 | int(left) << 8 | register
                    for byte in (0, 1, 0x81, 0xFF):
                        with self.subTest(register=register, left=left, count=count, byte=byte):
                            model = cpu([opcode])
                            model.d[register], model.x = 0xCAFEAB00 | byte, 1
                            expected = (((byte << count) | (byte >> (8 - count))) if left
                                        else ((byte >> count) | (byte << (8 - count)))) & 0xFF
                            carry = (byte >> (8 - count)) & 1 if left else (byte >> (count - 1)) & 1
                            model.step({}, {})
                            self.assertEqual(model.d[register], 0xCAFEAB00 | expected)
                            self.assertEqual((model.x, model.n, model.z, model.v, model.c),
                                             (1, expected >> 7, int(expected == 0), 0, carry))

    def test_old_adapter_still_refuses_added_forms_and_new_neighbours_fail(self):
        forms = ([0x48A3, 0x3F00], [0x4C9B, 0x00FC], [0x4EFB, 0],
                 [0x50F8, 0xDB0E], [0x51F8, 0xDB0F], [0xE31A], [0xE81C])
        for words in forms:
            with self.subTest(old=words), self.assertRaises(track.load.mini68k.Unsupported):
                cpu(words, track.load.S2CPU).step({}, {})
        for words in ([0x48A3, 0x3E00], [0x4C9B, 0x00FE], [0x4EFB, 0x0800],
                      [0xE31B], [0xE95C]):
            with self.subTest(new=words), self.assertRaises(track.load.mini68k.Unsupported):
                cpu(words).step({}, {})

    def test_motorola_costs_for_new_forms_are_exact(self):
        model = cpu([0x4C9B, 0x00FC, 0x48A3, 0x3F00, 0x4EFB, 0,
                     0x50F8, 0xDB0E, 0xE31A, 0xE81C])
        decoder = track.load.m68k_timing.Decoder(model.rom)
        expected = ((0x1000, 36), (0x1004, 32), (0x1008, 14),
                    (0x100C, 16), (0x1010, 8), (0x1012, 14))
        for pc, clocks in expected:
            with self.subTest(pc=pc):
                cost = decoder.decode(pc).cost
                self.assertTrue(cost.exact())
                self.assertEqual(cost.lo, clocks)


class TrackBusAndState(unittest.TestCase):
    def test_port_width_order_values_and_prefix_are_observed_without_stalls(self):
        model = cpu([])
        model.observing, model.cpu_prefix = True, 20
        model.write(0xC00004, "l", 0x40000002)  # VRAM $8000
        model.pc, model.cpu_prefix = 0x1006, 48
        model.write(0xC00000, "w", 0x1234)
        model.pc, model.cpu_prefix = 0x1008, 56
        model.write(0xC00000, "l", 0x56789ABC)
        self.assertEqual([(write.pc, write.cpu_prefix, write.width, write.value, write.destination)
                          for write in model.writes],
                         [(0x1000, 20, 4, 0x40000002, "vdp_control"),
                          (0x1006, 48, 2, 0x1234, "vdp_data"),
                          (0x1008, 56, 4, 0x56789ABC, "vdp_data")])
        self.assertEqual(model.vram[0x8000:0x8006], bytes.fromhex("123456789abc"))
        self.assertEqual(model.vdp_address, 0x8006)
        self.assertEqual(model.data_writes, [])  # longword-only inherited observer is bypassed

    def test_snapshot_is_deep_and_restores_ram_registers_flags_and_private_vram(self):
        model = cpu([])
        model.write(0xFFDB00, "l", 0xCA908)
        model.write(0xC00004, "l", 0x40000003)
        model.write(0xC00000, "w", 0x1234)
        model.d[2], model.a[3], model.x, model.sr_mask = 0xFFFF8000, 0xFFDB3E, 1, 7
        state = model.capture()
        model.ram[0xDB00], model.d[2], model.a[3], model.x = 9, 0, 0, 0
        model.vram[0xC000], model.vdp_address = 0xFF, None
        model.restore(state)
        self.assertEqual(model.capture(), state)
        self.assertEqual(model.read(0xFFDB00, "l"), 0xCA908)
        self.assertEqual(model.vram[0xC000:0xC002], bytes.fromhex("1234"))
        self.assertFalse(model.observing)

    def test_unmodelled_device_commands_and_bad_inputs_fail_explicitly(self):
        model = cpu([])
        for address, size, value in ((0xC00000, "w", 1), (0xC00004, "w", 0x8F02),
                                     (0xC00004, "l", 0), (0xC00000, "b", 1)):
            with self.subTest(address=address, size=size, value=value), self.assertRaises(track.load.mini68k.Unsupported):
                model.write(address, size, value)
        for mapping in (-1, 56):
            with self.assertRaisesRegex(track.CostError, "mapping ID"):
                track.mapping_bounds(bytes(0x10000), mapping)
        for args in (["--rom", "relative.gen"], ["--rom", "/does/not/exist.gen"]):
            with contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(track.main(args), 2)
        with self.assertRaisesRegex(track.CostError, "original Sonic 2"):
            track.prepare(b"not a ROM", 0)


class TrackRunner(unittest.TestCase):
    @staticmethod
    def synthetic(words):
        rom = bytearray(0x8000)
        for index, word in enumerate(words):
            rom[track.ENTRY + index * 2:track.ENTRY + index * 2 + 2] = word.to_bytes(2, "big")
        model = track.TrackCPU(bytes(rom))
        # Only test the runner here; production construction verifies identity.
        ledger = object.__new__(track.TrackLedger)
        ledger.rom = model.rom
        ledger.decoder = track.load.m68k_timing.Decoder(model.rom)
        ledger.decoded = {}
        return model, ledger

    def test_dbf_expiration_charges_fourteen_clocks(self):
        model, ledger = self.synthetic([0x7000, 0x51C8, 0xFFFE, 0x4E75])
        result = ledger.run_slice(model, 4)
        self.assertEqual((result.cpu_cycles, result.instructions, result.writes), (34, 3, ()))

    def test_unsupported_path_escape_and_nontermination_fail_closed(self):
        for words, limit, message in (([0xFFFF], 100, "unsupported track"),
                                      ([0x4EF9, 0, 0x7000], 100, "left priced ranges"),
                                      ([0x60FE], 10, "instruction limit 10")):
            with self.subTest(words=words):
                model, ledger = self.synthetic(words)
                with self.assertRaisesRegex(track.CostError, message):
                    ledger.run_slice(model, 4, limit)
                self.assertFalse(model.observing)
        model, ledger = self.synthetic([0x4E75])
        with self.assertRaisesRegex(track.CostError, "unsigned byte"):
            ledger.run_slice(model, -1)


def pack_bits(bits):
    bits += "0" * ((-len(bits)) % 8)
    return bytes(int(bits[start:start + 8], 2) for start in range(0, len(bits), 8))


def reference_rom():
    """28 rows: one UNC tile, then127 RLE tiles, then an explicit EOL."""
    streams = (pack_bits("100" * 28), pack_bits("0000000" * 28),
               pack_bits(("0000000" + "11111111") * 28))
    frame = b"".join(len(data).to_bytes(4, "big") + data for data in streams)
    rom = bytearray(track.MAPPING_END)
    source = 0xCA904
    rom[track.MAPPING_TABLE:track.MAPPING_TABLE + 8] = source.to_bytes(4, "big") + (source + len(frame)).to_bytes(4, "big")
    rom[source:source + len(frame)] = frame
    rom[track.UNC_LUT:track.UNC_LUT + 2] = b"\0\2"
    rom[track.RLE_LUT:track.RLE_LUT + 4] = bytes.fromhex("0001007e")
    return bytes(rom)


class IndependentFormat(unittest.TestCase):
    def test_msb_streams_runs_eol_and_raw_128_word_mirroring(self):
        rom = reference_rom()
        normal = track.decode_reference(rom, 0)
        flipped = track.decode_reference(rom, 0, True)
        self.assertEqual(normal, tuple(([0x6002] + [0xE001] * 127) * 28))
        self.assertEqual(flipped, tuple(([0xE001] * 127 + [0x6802]) * 28))
        # A 32-column Java display-strip permutation is deliberately not this oracle.
        self.assertNotEqual(flipped[31], 0x6802)
        self.assertEqual(flipped[127], 0x6802)

    def test_truncation_bad_row_lengths_and_stream_exhaustion_fail(self):
        rom = bytearray(reference_rom())
        rom[track.RLE_LUT + 3] = 0x7D  # row would have127, not128 words
        with self.assertRaisesRegex(track.CostError, "reference row has 127"):
            track.decode_reference(bytes(rom), 0)
        rom = bytearray(reference_rom())
        rom[0xCA906:0xCA908] = b"\xff\xff"
        with self.assertRaisesRegex(track.CostError, "segment length"):
            track.decode_reference(bytes(rom), 0)
        with self.assertRaisesRegex(track.CostError, "exhausted"):
            track.Bits(b"\x80").take(9)


@unittest.skipUnless(os.environ.get("SONIC2_ROM"), "set SONIC2_ROM to original World REV01 ROM")
class OriginalRomTrack(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ["SONIC2_ROM"])
        if not path.is_absolute():
            raise AssertionError("SONIC2_ROM must be absolute")
        cls.rom = path.read_bytes()
        track.load.verify_rom(cls.rom)

    def test_all56_frames_four_persistent_slices_both_orientations_and_pnts_with_replay(self):
        ledger = track.TrackLedger(self.rom)
        cases = slices = snapshots = 0
        for mapping in range(56):
            for flipped in (False, True):
                reference = track.decode_reference(self.rom, mapping, flipped)
                for alternate in (False, True):
                    with self.subTest(mapping=mapping, flipped=flipped, alternate=alternate):
                        model = track.prepare(self.rom, mapping, flipped, alternate)
                        parts, states = [], []
                        source, end = track.mapping_bounds(self.rom, mapping)
                        for index in range(4):
                            part = ledger.run_slice(model, index)
                            parts.append(part)
                            states.append(model.capture())
                            self.assertEqual(part.post_state.mapping_frame, mapping)
                            self.assertEqual(bool(part.post_state.orientation), flipped)
                            self.assertEqual(part.post_state.alternate_pnt, int(alternate))
                            self.assertGreater(part.cpu_cycles, 0)
                            self.assertGreater(part.instructions, 0)
                            self.assertEqual(sum(write.width for write in part.writes
                                                 if write.destination == ("pnt_buffer" if flipped else "vdp_data")), 0x700)
                            self.assertEqual([write.destination for write in part.writes[:1]], ["vdp_control"])
                            # ROM writes each slice into a distinct $700-byte
                            # quarter, with alternate PNT selecting $C000/$8000.
                            command = part.writes[0].value
                            destination = ((command >> 16) & 0x3FFF) | ((command & 3) << 14)
                            self.assertEqual(destination, (0xC000 if alternate else 0x8000) + index * 0x700)
                            self.assertTrue(all(write.cpu_prefix < part.cpu_cycles for write in part.writes))
                            self.assertEqual([write.cpu_prefix for write in part.writes],
                                             sorted(write.cpu_prefix for write in part.writes))
                            if index < 3:
                                self.assertTrue(all(source < pointer <= end for pointer in part.post_state.cursors))
                            else:
                                self.assertEqual(part.post_state.saved_words, (0,) * 6)
                            slices += 1
                        self.assertEqual(tuple(word for part in parts for word in part.pnt_words), reference)
                        if flipped:
                            self.assertFalse(any(model.vram))  # VInt DMA is outside Draw
                        else:
                            base = 0xC000 if alternate else 0x8000
                            raw = model.vram[base:base + 0x1C00]
                            self.assertEqual(tuple(int.from_bytes(raw[index:index + 2], "big")
                                                   for index in range(0, len(raw), 2)), reference)
                        # Replay every remaining suffix, not just snapshot equality.
                        for boundary in range(3):
                            model.restore(states[boundary])
                            for index in range(boundary + 1, 4):
                                self.assertEqual(ledger.run_slice(model, index), parts[index])
                                self.assertEqual(model.capture(), states[index])
                            snapshots += 1
                        # All no-draw indices share only the native orientation path.
                        for index in (4, 5, 9, 59):
                            part = ledger.run_slice(model, index)
                            self.assertEqual((part.writes, part.pnt_words), ((), ()))
                        cases += 1
        self.assertEqual((cases, slices, snapshots), (224, 896, 672))


if __name__ == "__main__":
    unittest.main()
