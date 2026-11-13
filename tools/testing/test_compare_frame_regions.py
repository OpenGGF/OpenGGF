"""Creator-coloured corruption must fail even when no stock palette survives."""
import tempfile
import unittest
from pathlib import Path
from PIL import Image
from compare_frame_regions import compare_frame, scan


class CompareFrameRegionsTest(unittest.TestCase):
    def test_creator_body_outside_owned_cockpit_fails(self):
        stock = Image.new("RGB", (12, 12), "black")
        before = stock.copy()
        before.putpixel((4, 4), (0, 146, 109))  # approved main body
        after = before.copy()
        after.putpixel((8, 8), (109, 109, 146))  # misplaced creator tile
        result = compare_frame(stock, before, after, [[1, 1, 3, 3]])
        self.assertEqual(1, result["new_non_stock_pixels_outside"])
        self.assertEqual((8, 8, 9, 9), result["unexpected_bounds"])

    def test_only_owned_pilot_changes_pass_and_boundaries_are_exclusive(self):
        stock = Image.new("RGB", (8, 8), "blue")
        before = stock.copy()
        after = before.copy()
        after.putpixel((3, 3), (109, 109, 146))
        self.assertEqual(0, compare_frame(stock, before, after, [[2, 2, 4, 4]])["unexpected_pixels"])
        self.assertEqual(1, compare_frame(stock, before, after, [[2, 2, 3, 4]])["unexpected_pixels"])

    def test_erasing_an_approved_body_also_fails(self):
        stock = Image.new("RGB", (8, 8), "black")
        before = stock.copy()
        before.putpixel((7, 7), (0, 146, 109))
        result = compare_frame(stock, before, stock, [])
        self.assertEqual(1, result["unexpected_pixels"])
        self.assertEqual(0, result["new_non_stock_pixels_outside"])

    def test_missing_frame_or_region_cannot_silently_reduce_coverage(self):
        with tempfile.TemporaryDirectory() as name:
            directory = Path(name)
            Image.new("RGB", (8, 8)).save(directory / "000001.png")
            with self.assertRaisesRegex(ValueError, "Missing region"):
                scan(directory, directory, directory, {}, 1, 1)
            with self.assertRaises(FileNotFoundError):
                scan(directory, directory, directory, {"1": [], "2": []}, 1, 2)


if __name__ == "__main__":
    unittest.main()
