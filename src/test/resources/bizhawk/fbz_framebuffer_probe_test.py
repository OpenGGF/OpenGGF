"""FBZ framebuffer predicate guard; no Pillow, emulator, ROM or gameplay writes."""
import importlib.util
from pathlib import Path
import unittest

SOURCE = Path(__file__).resolve().parents[4] / "tools/bizhawk/capture_fbz_visual_references.py"
spec = importlib.util.spec_from_file_location("fbz_probe", SOURCE)
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)


class FramebufferProbeTest(unittest.TestCase):
    def test_blank_active_crop_rejects_bright_borders(self):
        self.assertIsNone(probe.framebuffer_content((348, 240),
            lambda x, y: 0 if 14 <= x < 334 and 8 <= y < 232 else 255))

    def test_palette_gradient_without_horizontal_content_rejects(self):
        self.assertIsNone(probe.framebuffer_content((320, 224), lambda x, y: y))

    def test_small_upper_overlay_cannot_prove_full_gameplay(self):
        self.assertIsNone(probe.framebuffer_content((320, 224),
            lambda x, y: int(1 <= x < 10 and 1 <= y < 20)))

    def test_separated_active_content_passes_both_native_shapes(self):
        for size, offset in [((320, 224), (0, 0)), ((348, 240), (14, 8))]:
            left, top = offset
            rows = probe.framebuffer_content(size,
                lambda x, y: int(left+5 <= x < left+10 and top+5 <= y < top+200))
            self.assertEqual(list(range(5, 200)), rows)

    def test_later_window_moves_start_checkpoint_and_all_cadence_monitors(self):
        plan = probe.capture_plan(250000, 128)
        self.assertIn('observation_limit_frames=128', plan)
        self.assertIn('checkpoints={{id="fbz1-start-outdoor",bk2_frame=250000}}', plan)
        self.assertEqual(5, plan.count(']={250000}'))

    def test_boundary_plan_uses_reviewed_coordinate_axis_and_rejects_other_recipes(self):
        self.assertIn("address=45072,forward=6913,reverse=6911", probe.boundary_plan("fbz1-boundary-4-horizontal"))
        self.assertIn("address=45076,forward=2497,reverse=2495", probe.boundary_plan("fbz1-boundary-1-outdoor"))
        self.assertIn("act=2,normal=4,region_address=61120", probe.boundary_plan("fbz2-boundary-outdoor"))
        with self.assertRaises(ValueError):
            probe.boundary_plan("fbz2-exit")

    def test_unknown_dimensions_reject(self):
        self.assertIsNone(probe.framebuffer_content((640, 448), lambda x, y: x+y))


if __name__ == "__main__":
    unittest.main()
