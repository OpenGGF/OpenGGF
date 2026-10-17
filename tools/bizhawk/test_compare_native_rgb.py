"""Fail-closed controls for the 2026-10-17 maintained native RGB comparator."""
import contextlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
from PIL import Image

spec = importlib.util.spec_from_file_location('compare_native_rgb', Path(__file__).with_name('compare_native_rgb.py'))
rgb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rgb)


class NativeRgbComparison(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.native = self.root / 'native'
        self.engine = self.root / 'engine'
        self.native.mkdir()
        self.engine.mkdir()
        self.spots = self.root / 'spots.txt'
        self.spots.write_text('1\n')
        Image.new('RGB', (2, 2), (238, 0, 0)).save(self.native / 'input-00001.ppm')
        Image.new('RGB', (2, 2), (238, 0, 0)).save(self.engine / 'outer-00002.png')

    def compare(self, **kwargs):
        args = dict(native=self.native, engine=self.engine, spots=[1], mapping='native-poll-plus-one',
                    expected_count=1, size=(2, 2))
        args.update(kwargs)
        return rgb.compare(**args)

    def cli(self, *extra):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return rgb.main(['--native-dir', str(self.native), '--engine-dir', str(self.engine),
                             '--spots', str(self.spots), '--mapping', 'native-poll-plus-one',
                             '--expected-count', '1', '--size', '2x2', '--report', str(self.root / 'report.json'), *extra])

    def test_exact_raw_match_and_mapping(self):
        result = self.compare()
        self.assertEqual((1, 0, 0), (result['compared_pairs'], result['mismatching_pairs'], result['mismatching_rgb_pixels']))
        self.assertEqual(2, result['pairs'][0]['engine_outer'])
        self.assertEqual(0, self.cli())

    def test_missing_engine_png_is_error_not_a_skipped_pair(self):
        (self.engine / 'outer-00002.png').unlink()
        self.assertEqual(2, self.cli())
        self.assertFalse((self.root / 'report.json').exists())

    def test_missing_native_and_bad_png_fail(self):
        (self.native / 'input-00001.ppm').unlink()
        self.assertEqual(2, self.cli())
        Image.new('RGB', (2, 2)).save(self.native / 'input-00001.ppm')
        (self.engine / 'outer-00002.png').write_bytes(b'not png')
        self.assertEqual(2, self.cli())

    def test_size_mismatch_even_when_bytes_have_same_length(self):
        Image.new('RGB', (4, 1), (238, 0, 0)).save(self.engine / 'outer-00002.png')
        self.assertEqual(2, self.cli())

    def test_pixel_difference_preserves_low_bits_and_fails_cli(self):
        image = Image.new('RGB', (2, 2), (238, 0, 0))
        image.putpixel((1, 0), (237, 0, 0))
        image.save(self.engine / 'outer-00002.png')
        result = self.compare()
        self.assertEqual(1, result['mismatching_rgb_pixels'])
        self.assertEqual(dict(x=1, y=0, native=[238, 0, 0], engine=[237, 0, 0]), result['pairs'][0]['first_difference'])
        self.assertEqual(1, self.cli())

    def test_invalid_duplicate_empty_and_bad_count_spots_fail(self):
        for text in ('', '1\n1\n', '-1\n', '01\n', '1 2\n', '60000\n', '\n'):
            with self.subTest(text=text):
                self.spots.write_text(text)
                self.assertEqual(2, self.cli())
        with self.assertRaises(ValueError):
            self.compare(expected_count=2)
        with self.assertRaises(ValueError):
            self.compare(spots=[1, 1], expected_count=2)

    def test_mapping_is_explicit_and_never_searches(self):
        with self.assertRaises(FileNotFoundError):
            self.compare(mapping='identity')
        for mapping in ('', 'auto', '+2'):
            with self.assertRaises(ValueError):
                self.compare(mapping=mapping)
        with self.assertRaises(ValueError):
            self.compare(spots=[59999])

    def test_crops_are_explicit_bounded_and_equal_after_crop(self):
        Image.new('RGB', (4, 3), (238, 0, 0)).save(self.native / 'input-00001.ppm')
        with self.assertRaises(ValueError):
            self.compare()
        self.assertEqual(0, self.compare(native_crop=(1, 1, 2, 2))['mismatching_rgb_pixels'])
        for crop in ((3, 2, 2, 2), (-1, 0, 2, 2), (0, 0, 0, 2), (0, 0, 1, 2)):
            with self.assertRaises(ValueError):
                self.compare(native_crop=crop)
        for text in ('auto', '0,0,0,2', '-1,0,2,2', '0,0,2'):
            with self.assertRaises(ValueError):
                rgb.crop_rectangle(text)

    def test_declared_four_digit_names_never_fall_back_or_search(self):
        # A separately authored four-digit original reference; no renamed reference files.
        Image.new('RGB', (2, 2), (238, 0, 0)).save(self.native / 'input-0001.ppm')
        (self.native / 'input-00001.ppm').unlink()
        self.assertEqual(0, self.compare(native_digits=4)['mismatching_rgb_pixels'])
        self.assertEqual(0, self.cli('--native-digits', '4'))
        with self.assertRaises(FileNotFoundError):
            self.compare()  # Five remains the R1 default; no filename discovery.
        for digits in (0, 3, 6, '4', True):
            with self.assertRaises(ValueError):
                self.compare(native_digits=digits)

    def test_report_refuses_overwrite(self):
        report = self.root / 'report.json'
        report.write_text('retained')
        self.assertEqual(2, self.cli())
        self.assertEqual('retained', report.read_text())


if __name__ == '__main__':
    unittest.main()
