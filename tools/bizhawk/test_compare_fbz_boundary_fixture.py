import importlib.util
from pathlib import Path
import unittest
import tempfile
import json

spec=importlib.util.spec_from_file_location('boundary',Path(__file__).with_name('compare_fbz_boundary_fixture.py'))
boundary=importlib.util.module_from_spec(spec)
spec.loader.exec_module(boundary)

class DescriptorContract(unittest.TestCase):
    def test_known_native_attributes_preserve_priority_palette_and_flips(self):
        # LevelTilemapManager packed RGBA: patternlo, patternhi/palette/flips/priority, 0, valid.
        examples=[((0,0,0,255),0),((0x9A,0x90,0,255),0xC09A),
                  ((0xFF,0xFF,0,255),0xFFFF),((0x10,0x28,0,255),0x2810)]
        for encoded,expected in examples:
            self.assertEqual([expected]*2048,boundary.descriptor_words(bytes(encoded)*2048))

    def test_rejects_wrong_plane_shape_and_unpublished_descriptors(self):
        with self.assertRaises(ValueError):boundary.descriptor_words(b'')
        with self.assertRaises(ValueError):boundary.descriptor_words(bytes(8192))

class SpritePublicationContract(unittest.TestCase):
    def fixture(self, root, frames, displayed, prepared):
        lines = []
        for i, (frame, shown, ready) in enumerate(zip(frames, displayed, prepared)):
            name = f'phase-{i:02d}'
            lines.append(json.dumps(dict(kind='sample', phase='phase', index=i, native_frame=frame)))
            (root / (name + '-prepared-sat.bin')).write_bytes(bytes([ready]) * 0x280)
            vram = bytearray(0x10000)
            vram[0xF800:0xFA80] = bytes([shown]) * 0x280
            (root / (name + '.vram')).write_bytes(vram)
        (root / 'fixture.jsonl').write_text('\n'.join(lines))

    def test_previous_preparation_matches_publication_without_hiding_current_difference(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, [10, 11, 12], [0, 1, 2], [1, 2, 3])
            result = boundary.sprite_publication(root)
            self.assertEqual(2, result['consecutive_pairs'])
            self.assertEqual(0, result['previous_cpu_mismatch_pairs'])
            self.assertEqual(3, result['current_cpu_mismatch_samples'])

    def test_duplicates_and_gaps_are_not_consecutive_publications(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, [10, 10, 12, 13], [0, 0, 0, 9], [1, 1, 2, 3])
            result = boundary.sprite_publication(root)
            self.assertEqual(1, result['consecutive_pairs'])
            self.assertEqual(1, result['duplicate_samples'])
            self.assertEqual(1, result['sample_gaps'])
            self.assertEqual(1, result['previous_cpu_mismatch_pairs'])

    def test_rejects_missing_or_short_tables_and_backwards_frames(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.fixture(root, [10, 9], [0, 1], [1, 2])
            with self.assertRaises(ValueError): boundary.sprite_publication(root)
            self.fixture(root, [10, 11], [0, 1], [1, 2])
            (root / 'phase-01-prepared-sat.bin').write_bytes(b'')
            with self.assertRaises(ValueError): boundary.sprite_publication(root)
            (root / 'phase-01-prepared-sat.bin').unlink()
            with self.assertRaises(FileNotFoundError): boundary.sprite_publication(root)

if __name__=='__main__':unittest.main()
