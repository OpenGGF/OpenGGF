#!/usr/bin/env python3
# Purpose: independent flag/register vectors and failure controls for the S2 load-cost diagnostic.
# Inputs: synthetic opcodes; optional SONIC2_ROM pointing to the original World REV01 ROM.
# Origin: 2026-10-22 s2_chain_pacing task, implementation milestone 44c1d674004c.
import contextlib
import io
import os
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
import s2_load_costs as costs


def cpu(words, cls=costs.S2CPU):
    rom = bytearray(0x2000)
    for index, word in enumerate(words):
        rom[0x1000 + index * 2:0x1002 + index * 2] = word.to_bytes(2, "big")
    result = cls(bytes(rom))
    result.pc = 0x1000
    return result


def synthetic_ledger(model):
    # Unit tests exercise the runner independently of the mandatory CLI ROM identity gate.
    ledger = object.__new__(costs.Ledger)
    ledger.rom = model.rom
    ledger.decoder = costs.m68k_timing.Decoder(model.rom)
    ledger.decoded = {}
    return ledger


class S2OpcodeForms(unittest.TestCase):
    def test_status_save_restore_preserves_upper_register_and_system_mask(self):
        model = cpu([0x40C6, 0x44C6])
        model.d[6] = 0xABCD0000
        model.sr_mask = 7
        model.x, model.n, model.z, model.v, model.c = 1, 0, 1, 1, 0
        model.step({}, {})
        self.assertEqual(model.d[6], 0xABCD2716)
        model.x = model.n = model.z = model.v = model.c = 0
        model.step({}, {})
        self.assertEqual((model.x, model.n, model.z, model.v, model.c), (1, 0, 1, 1, 0))
        self.assertEqual((model.sr_mask, model.pc), (7, 0x1004))

    def test_kos_repeat_rotate_uses_extend_and_preserves_upper_word(self):
        model = cpu([0xE353])
        model.d[3], model.x = 0xCAFE8000, 1
        model.step({}, {})
        self.assertEqual((model.d[3], model.x, model.c, model.n, model.z, model.v),
                         (0xCAFE0001, 1, 1, 0, 0, 0))
        model = cpu([0xE353])
        model.step({}, {})
        self.assertEqual((model.d[3], model.x, model.c, model.z), (0, 0, 0, 1))

    def test_ring_rotates_wrap_without_changing_extend(self):
        model = cpu([0xE218])
        model.d[0], model.x = 0xABCDEF01, 1
        model.step({}, {})
        self.assertEqual((model.d[0], model.x, model.c, model.n, model.z, model.v),
                         (0xABCDEF80, 1, 1, 1, 0, 0))
        model = cpu([0xE958])
        model.d[0], model.x = 0xABCD8001, 1
        model.step({}, {})
        self.assertEqual((model.d[0], model.x, model.c), (0xABCD0018, 1, 0))

    def test_player_offset_uses_signed_absolute_word_address_and_word_overflow(self):
        model = cpu([0x0478, 0x0001, 0x8000])
        model.write(0xFFFF8000, "w", 0x8000)
        model.step({}, {})
        self.assertEqual(model.read(0xFFFF8000, "w"), 0x7FFF)
        self.assertEqual((model.v, model.n, model.z, model.c, model.x, model.pc),
                         (1, 0, 0, 0, 0, 0x1006))

    def test_background_register_save_has_native_stack_order_and_no_flag_changes(self):
        model = cpu([0x48E7, 0x0E00, 0x4CDF, 0x0070])
        model.a[7] = 0xFFFFF000
        model.d[4:7] = [0x11223344, 0x55667788, 0x99AABBCC]
        model.x, model.n, model.z, model.v, model.c = 1, 0, 1, 1, 0
        model.step({}, {})
        self.assertEqual(model.a[7], 0xFFFFEFF4)
        self.assertEqual([model.read(0xFFFFEFF4 + offset, "l") for offset in (0, 4, 8)],
                         [0x11223344, 0x55667788, 0x99AABBCC])
        model.d[4:7] = [0, 0, 0]
        model.step({}, {})
        self.assertEqual(model.d[4:7], [0x11223344, 0x55667788, 0x99AABBCC])
        self.assertEqual((model.a[7], model.x, model.n, model.z, model.v, model.c),
                         (0xFFFFF000, 1, 0, 1, 1, 0))

    def test_tile_flip_xor_and_memory_bit_test_have_distinct_flag_effects(self):
        model = cpu([0x0A83, 0x0800, 0x0800])
        model.d[3], model.x, model.c = 0xAAAA0000, 1, 1
        model.step({}, {})
        self.assertEqual((model.d[3], model.x, model.n, model.z, model.v, model.c),
                         (0xA2AA0800, 1, 1, 0, 0, 0))
        model = cpu([0x0810, 0x000B])  # memory bit 11 aliases bit 3
        model.a[0] = 0xFFFF9000
        model.write(model.a[0], "b", 8)
        model.x, model.n, model.v, model.c = 1, 1, 1, 1
        model.step({}, {})
        self.assertEqual((model.z, model.x, model.n, model.v, model.c), (0, 1, 1, 1, 1))

    def test_shared_interpreter_still_refuses_every_added_form(self):
        forms = ([0x40C6], [0x44C6], [0xE353], [0xE218], [0xE958],
                 [0x0478, 1, 0x8000], [0x48E7, 0x0E00], [0x4CDF, 0x0070],
                 [0x0A83, 0x0800, 0x0800], [0x0810, 3])
        for words in forms:
            with self.subTest(words=words), self.assertRaises(costs.mini68k.Unsupported):
                cpu(words, costs.palette_forms.CPU).step({}, {})

    def test_unimplemented_neighbours_remain_errors(self):
        forms = ([0x40C0], [0x44C0], [0xE352], [0xE21A], [0xE95A],
                 [0x04B8, 0, 1, 0x8000], [0x48E7, 0x0001],
                 [0x4CDF, 0x8000], [0x0A81, 0x0800, 0x0800], [0x0811, 3])
        for words in forms:
            with self.subTest(words=words), self.assertRaises(costs.mini68k.Unsupported):
                cpu(words).step({}, {})


class FailureContracts(unittest.TestCase):
    def test_dbf_expiry_charges_fourteen_clocks(self):
        model = cpu([0x7000, 0x51C8, 0xFFFE, 0x4E75])
        result = synthetic_ledger(model).run(model, 0x1000, ((0x1000, 0x1008),))
        self.assertEqual((result.cycles, result.instructions), (34, 3))

    def test_program_counter_escape_and_nontermination_fail_closed(self):
        model = cpu([0x6004])
        with self.assertRaisesRegex(costs.CostError, "left priced ranges"):
            synthetic_ledger(model).run(model, 0x1000, ((0x1000, 0x1002),))
        model = cpu([0x60FE])
        with self.assertRaisesRegex(costs.CostError, "instruction limit"):
            synthetic_ledger(model).run(model, 0x1000, ((0x1000, 0x1002),), limit=5)

    def test_invalid_rom_region_zone_act_and_camera_are_explicit_errors(self):
        with self.assertRaisesRegex(costs.CostError, "original Sonic 2"):
            costs.Ledger(b"not a ROM")
        for args in ((7, 0, 0, 0, "ntsc"), (0, 2, 0, 0, "ntsc"),
                     (0, 0, -1, 0, "ntsc"), (0, 0, 0, 65536, "ntsc"),
                     (0, 0, 0, 0, "unknown")):
            with self.subTest(args=args), self.assertRaises(costs.CostError):
                costs.validate_inputs(*args)
        with self.assertRaisesRegex(costs.CostError, "region"):
            costs.frame_equivalents(1, "unknown")

    def test_invalid_kos_source_and_destination_are_explicit_errors(self):
        model = cpu([0x4E75])
        ledger = synthetic_ledger(model)
        for source, destination in ((len(model.rom), 0xFF9000), (0, 0x1000)):
            with self.subTest(source=source), self.assertRaises(costs.CostError):
                ledger.kos(model, source, destination)

    def test_cli_rejects_relative_or_missing_paths_without_success_output(self):
        for path in ("relative.gen", "/missing-s2-load-cost-test/rom.gen"):
            stdout, stderr = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                status = costs.main(["--rom", path])
            self.assertEqual(status, 2)
            self.assertEqual(stdout.getvalue(), "")
            self.assertTrue(stderr.getvalue().startswith("ERROR:"))


@unittest.skipUnless(os.environ.get("SONIC2_ROM"), "set SONIC2_ROM to run original-ROM integration")
class OriginalRomCosts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.rom = Path(os.environ["SONIC2_ROM"]).read_bytes()

    def test_ehz_component_census_reproduces_the_retained_instruction_probe(self):
        result = costs.analyze(self.rom)
        components = {item["name"]: item for item in result["components"]}
        self.assertEqual(result["selected_cpu_cycles"], 4_734_984)
        self.assertEqual(components["ring_setup"]["expanded_rings"], 226)
        self.assertEqual(components["ring_setup"]["cpu_cycles"], 2_044_704)
        self.assertEqual(components["initial_background"]["block_calls"], 512)
        self.assertEqual(components["initial_background"]["vdp_data_bytes"], 4096)
        self.assertEqual(components["initial_background"]["vdp_control_writes"], 64)
        self.assertEqual(components["initial_background"]["interrupt_masked_rows"], 16)
        self.assertEqual([components[name]["output_bytes"] for name in (
            "block_maps", "chunk_maps", "level_layout", "primary_collision", "secondary_collision")],
            [4000, 32768, 4096, 768, 768])
        self.assertFalse(result["runtime_authority"])
        self.assertTrue(result["unresolved"])

    def test_other_act_and_region_change_semantic_work_or_units_without_frame_rounding(self):
        result = costs.analyze(self.rom, act=1, region="pal")
        components = {item["name"]: item for item in result["components"]}
        self.assertEqual(components["level_layout"]["source"], "0x45C84")
        self.assertEqual(components["level_layout"]["cpu_cycles"], 158_876)
        self.assertEqual(components["ring_setup"]["expanded_rings"], 225)
        self.assertEqual(components["ring_setup"]["cpu_cycles"], 1_866_926)
        self.assertAlmostEqual(result["selected_cpu_frame_equivalents"],
                               result["selected_cpu_cycles"] * 7 / (3420 * 313))
        self.assertNotEqual(result["selected_cpu_frame_equivalents"],
                            int(result["selected_cpu_frame_equivalents"]))

    def test_cpz_and_arz_use_their_own_streams_and_keep_water_work_unresolved(self):
        for zone, act, kos_clocks, ring_clocks, rings in (
                (13, 0, 2_414_870, 1_806_212, 195),
                (15, 1, 2_638_932, 2_124_140, 217)):
            with self.subTest(zone=zone, act=act):
                result = costs.analyze(self.rom, zone=zone, act=act)
                components = {item["name"]: item for item in result["components"]}
                self.assertEqual(sum(item["cpu_cycles"] for item in result["components"][:5]),
                                 kos_clocks)
                self.assertEqual(components["ring_setup"]["cpu_cycles"], ring_clocks)
                self.assertEqual(components["ring_setup"]["expanded_rings"], rings)
                self.assertNotIn("water_effects_no_water", components)
                self.assertIn("WaterEffects needs explicit water-owner initialization RAM",
                              result["unresolved"])
                self.assertEqual(components["block_maps"]["scratch_spill_bytes"],
                                 0x100 if zone == 15 else 0)


if __name__ == "__main__":
    unittest.main()
