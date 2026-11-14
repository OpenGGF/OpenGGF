"""Research inventory checks; optional pinned disassemblies, no runtime/ROM assets."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
PATH = ROOT / 'examples/mica-and-talon/production/mica/powered/inventory.py'
spec = importlib.util.spec_from_file_location('powered_inventory', PATH)
inventory = importlib.util.module_from_spec(spec)
spec.loader.exec_module(inventory)


@unittest.skipUnless((ROOT / 'docs/s2disasm/s2.asm').is_file()
                     and (ROOT / 'docs/skdisasm/General/Sprites/Sonic/Anim - Sonic.asm').is_file(),
                     'optional pinned S2/S3K disassembly references unavailable')
class PoweredInventoryTest(unittest.TestCase):
    def setUp(self):
        self.rows = {(r['game'], r['animation']): r for r in inventory.inventory()}

    def test_merged_tail_is_normal_not_an_out_of_bounds_powered_table_read(self):
        self.assertEqual(len(self.rows), 70)
        self.assertEqual(self.rows['S2', 0x20]['label'], 'SonAni_Lying')
        self.assertEqual(self.rows['S2', 0x21]['label'], 'SonAni_LieDown')
        self.assertEqual(self.rows['S3K', 0x23]['label'], 'AniSonic23')
        self.assertTrue(self.rows['S3K', 0x23]['script_shared'])

    def test_s2_implicit_slope_and_alternate_walk_are_included(self):
        walk = self.rows['S2', 0]
        self.assertEqual(walk['frames'], [0x77, 0x78, 0x79, 0x7A, 0x7B, 0x7C, 0x75, 0x76])
        self.assertEqual(walk['reachable_frames'], list(range(0x75, 0xB5)))
        self.assertEqual(self.rows['S2', 1]['reachable_frames'], list(range(0xB5, 0xBD)))

    def test_s3k_run_has_duplicate_mapping_indices_outside_script_literals(self):
        run = self.rows['S3K', 1]
        self.assertEqual(run['frames'], [0x21, 0x22])
        self.assertEqual(run['reachable_frames'], [0x21, 0x22, 0x25, 0x26, 0x29, 0x2A, 0x2D, 0x2E])

    def test_transform_repeats_and_switch_are_preserved_even_when_script_shared(self):
        transform = self.rows['S2', 0x1F]
        self.assertTrue(transform['script_shared'])
        self.assertEqual(transform['frames'], [0x6D, 0x6D, 0x6E, 0x6E, 0x6F,
                                               0x70, 0x71, 0x70, 0x71, 0x70, 0x71, 0x70, 0x71])
        self.assertEqual((transform['delay'], transform['terminator'], transform['parameter']), (2, 0xFD, 0))


if __name__ == '__main__':
    unittest.main()
