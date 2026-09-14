import importlib.util
from pathlib import Path
import unittest

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

if __name__=='__main__':unittest.main()
