"""Behavior checks for original-ROM dispatch-entry inventory generation."""
import copy
from dataclasses import replace
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest


TOOL = Path(__file__).with_name('native_inventory.py')
REFERENCE = Path(__file__).with_name('reference_inventory.json')


class NativeInventoryDecoding(unittest.TestCase):
    @staticmethod
    def specification():
        import native_inventory as inventory
        return replace(inventory.TABLES[0], offset=4, first_id=1, last_id=3)

    def decode(self, pointers, length=64):
        import native_inventory as inventory
        data = bytearray(length)
        struct.pack_into(f'>{len(pointers)}I', data, 4, *pointers)
        return inventory.decode_table(bytes(data), self.specification())

    def test_absolute_big_endian_pointers_preserve_bounds_and_shared_targets(self):
        rows = self.decode([32, 34, 32])
        self.assertEqual([1, 2, 3], [row['native_id'] for row in rows])
        self.assertEqual([4, 8, 12], [row['entry_address'] for row in rows])
        self.assertEqual([32, 34, 32], [row['target_address'] for row in rows])
        self.assertEqual([[3], [], [1]], [row['alias_ids'] for row in rows])
        self.assertTrue(all(row['status'] == 'uncatalogued-subject' for row in rows))
        self.assertEqual(3, len({row['id'] for row in rows}))

    def test_zero_odd_and_outside_rom_targets_are_rejected(self):
        for invalid in (0, 33, 64, 0xFFFFFFFF):
            with self.subTest(pointer=invalid):
                with self.assertRaisesRegex(ValueError, 'invalid code pointer'):
                    self.decode([32, invalid, 36])

    def test_truncated_table_is_not_a_smaller_inventory(self):
        import native_inventory as inventory
        with self.assertRaisesRegex(ValueError, 'truncated dispatch table'):
            inventory.decode_table(b'\x00' * 15, self.specification())

    def test_table_boundary_excludes_padding_and_other_routines(self):
        import native_inventory as inventory
        data = bytearray(64)
        struct.pack_into('>4I', data, 4, 32, 34, 36, 0xFFFFFFFF)
        self.assertEqual(3, len(inventory.decode_table(bytes(data), self.specification())))

    def test_invalid_id_span_and_odd_table_base_are_rejected(self):
        import native_inventory as inventory
        for spec in (replace(self.specification(), first_id=4),
                     replace(self.specification(), last_id=256),
                     replace(self.specification(), offset=3)):
            with self.subTest(specification=spec):
                with self.assertRaisesRegex(ValueError, 'invalid dispatch table bounds'):
                    inventory.decode_table(b'\x00' * 64, spec)

    def test_real_table_specifications_retain_original_id_domains(self):
        import native_inventory as inventory
        self.assertEqual([140, 220, 256, 185], [spec.count for spec in inventory.TABLES])
        self.assertEqual([1, 1, 0, 0], [spec.first_id for spec in inventory.TABLES])
        self.assertEqual([0xDC40, 0x1637C, 0x952A2, 0x95586],
                         [spec.offset + spec.count * 4 for spec in inventory.TABLES])
        for zone in range(256):
            tables = [spec.table for spec in inventory.TABLES if spec.game == 's3k'
                      and any(first <= zone <= last for first, last in spec.zone_ranges)]
            self.assertEqual(1, len(tables))
            self.assertEqual('Sprite_Listing3' if zone <= 6 or 14 <= zone <= 21 else 'Sprite_ListingK', tables[0])


class NativeInventoryArtifact(unittest.TestCase):
    def setUp(self):
        self.inventory = json.loads(REFERENCE.read_text())

    def test_reference_has_every_entry_and_known_alias_behaviors_remain_unresolved(self):
        import native_inventory as inventory
        rows = inventory.validate_inventory(self.inventory)
        self.assertEqual(801, len(rows))
        self.assertEqual([135, 211, 219, 171], [table['distinct_targets'] for table in self.inventory['tables']])
        self.assertEqual(801, len({row['id'] for row in rows}))
        s1_null = [row for row in rows if row['game'] == 's1' and 2 <= row['native_id'] <= 7]
        self.assertEqual({0xDC40}, {row['target_address'] for row in s1_null})
        self.assertTrue(all(len(row['alias_ids']) == 5 for row in s1_null))
        s2_null = [row for row in rows if row['game'] == 's2' and row['target_address'] == 0x1637C]
        self.assertEqual([0x4C, 0x4D, 0x4E, 0x4F, 0x62, 0xD0, 0xD1], [row['native_id'] for row in s2_null])
        self.assertTrue(all(row['status'] == 'uncatalogued-subject' for row in rows))

    def test_artifact_metadata_and_typed_rows_fail_closed(self):
        import native_inventory as inventory
        mutations = [
            lambda d: d['generator'].update(sha256='0' * 64),
            lambda d: d['reference_roms']['s1'].update(sha1='0' * 40),
            lambda d: d['tables'][0].update(offset=0),
            lambda d: d['tables'][0].update(first_id=True),
            lambda d: d['subjects'].pop(),
            lambda d: d['subjects'].__setitem__(1, copy.deepcopy(d['subjects'][0])),
            lambda d: d['subjects'][0].update(native_id=True),
            lambda d: d['subjects'][0].update(target_address=0),
            lambda d: d['subjects'][0].update(alias_ids=[255]),
            lambda d: d['subjects'][0].update(status='verified'),
            lambda d: d.update(approved=True),
        ]
        for index, mutate in enumerate(mutations):
            with self.subTest(mutation=index):
                altered = copy.deepcopy(self.inventory)
                mutate(altered)
                with self.assertRaises(ValueError):
                    inventory.validate_inventory(altered)

    def test_rendering_is_stable_json_with_one_line_per_subject(self):
        import native_inventory as inventory
        rendered = inventory.render_inventory(self.inventory)
        self.assertEqual(self.inventory, json.loads(rendered))
        self.assertEqual(rendered, inventory.render_inventory(copy.deepcopy(self.inventory)))
        self.assertEqual(801, sum(line.startswith('    {"kind":') for line in rendered.splitlines()))
        self.assertEqual(rendered, REFERENCE.read_text())


class NativeInventoryCli(unittest.TestCase):
    def test_wrong_rom_never_creates_an_inventory(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            rom = root / 'wrong.gen'
            rom.write_bytes(b'not the original ROM')
            output = root / 'inventory.json'
            result = subprocess.run([
                sys.executable, str(TOOL), '--s1-rom', str(rom), '--s2-rom', str(rom),
                '--s3k-rom', str(rom), '--output', str(output)], capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('wrong original ROM identity', result.stderr)
            self.assertFalse(output.exists())
            self.assertEqual('', result.stdout)

    def test_relative_rom_paths_are_rejected_before_output(self):
        import native_inventory as inventory
        with self.assertRaisesRegex(ValueError, 'absolute existing'):
            inventory.generate_inventory({'s1': Path('s1.gen'), 's2': Path('s2.gen'), 's3k': Path('s3k.gen')})

    def test_every_original_is_required(self):
        import native_inventory as inventory
        with self.assertRaisesRegex(ValueError, 'exactly'):
            inventory.generate_inventory({})


if __name__ == '__main__':
    unittest.main()
