"""Temporal identity, native limb phases and reproducible generated-part assets."""
import json
import hashlib
import math
from pathlib import Path
import unittest
try:
    import PIL  # noqa: F401  # Pillow is the art pipeline's only third-party dependency
except ModuleNotFoundError:
    raise unittest.SkipTest('Pillow is not installed; the playable-art pipeline tests need it')
from playable_art.locked_stage import rebuild, derive_curl, read_indexed
from playable_art.locked_stage_review import measure
from playable_art.stage_body_motion import render_body, posed_point, rotate_point, principal_axis

ROOT = Path(__file__).resolve().parents[2]


class LockedStageTests(unittest.TestCase):
    def bank(self, character):
        folder = ROOT / f"examples/mica-and-talon/production/{character}/consistent-stage"
        return folder, json.loads((folder / "native-pose-generation.json").read_text())

    def test_all_shipped_frames_reassemble_and_obey_native_colour_budgets(self):
        for character in ["mica", "talon"]:
            folder, _ = self.bank(character)
            self.assertEqual(72, rebuild(folder, check=True))

    def test_upright_identity_is_one_canonical_drawing_with_measured_motion(self):
        for character in ["mica", "talon"]:
            folder, bank = self.bank(character)
            for frame in bank["frames"]:
                if frame["layer"] != "upright":
                    continue
                canonical = bank["canonical"][frame["game"]]
                self.assertEqual(1, sum(p.get("kind") == "locked_body" for p in frame["placements"]))
                motion = frame["body_motion"]
                for kind in ["head", "torso"]:
                    source = next(p for p in motion["source"] if p["role"] == kind)
                    self.assertEqual(canonical[kind], source["part"])
                    self.assertEqual(canonical[f"{kind}_dimensions"], source["size"])
                    self.assertEqual(canonical[f"{kind}_position"], source["position"])
                    self.assertEqual(canonical["locked_source_sha256"][kind],
                                     hashlib.sha256((folder / source["part"]).read_bytes()).hexdigest())
                self.assertEqual([1., 1.], motion["scale"])
                self.assertEqual(0., motion["shear"])

    def test_mirrored_phase_keeps_identity_but_alternates_native_limbs(self):
        for character in ["mica", "talon"]:
            _, bank = self.bank(character)
            for selector in [3, 4, 5, 6, 7, 8]:
                orientation = "h" if selector >= 6 else "n"
                frame = next(f for f in bank["frames"] if f["game"] == "s3k"
                             and f["id"] == selector and f["orientation"] == orientation)
                gloves = [p for p in frame["limb_landmarks"] if p["kind"] == "glove"]
                self.assertEqual(1, len(gloves))
                box = gloves[0]["native_bbox"]
                self.assertEqual(selector < 6, box[0] + box[2] > 0)
                self.assertEqual(1 if selector in [5, 8] else 2,
                                 sum(p["kind"] == "shoe" for p in frame["limb_landmarks"]))

    def test_curls_have_no_upright_identity_or_separate_limbs(self):
        for character in ["mica", "talon"]:
            _, bank = self.bank(character)
            for frame in bank["frames"]:
                if frame["layer"] == "curl":
                    self.assertEqual(1, len(frame["placements"]))
                    self.assertIn("-curl-phase", frame["placements"][0]["part"])
                    self.assertEqual([], frame["limb_landmarks"])
                    self.assertEqual("n", frame["orientation"])

    def test_independent_pixel_landmarks_and_identity_stay_bounded(self):
        for character in ["mica", "talon"]:
            folder, _ = self.bank(character)
            for row in measure(folder):
                if row["layer"] != "upright":
                    continue
                with self.subTest(character=character, game=row["game"],
                                  selector=row["id"], orientation=row["orientation"]):
                    self.assertEqual(1.0, row["head_ear_iou"])
                    self.assertEqual(0, row["head_rgb_differences"])
                    self.assertEqual(1.0, row["torso_iou"])
                    self.assertEqual(0, row["torso_rgb_differences"])
                    self.assertLessEqual(row["glove_centroid_error_max"], 1.01)
                    self.assertLessEqual(row["shoe_contact_error_max"], 3)
                    self.assertLessEqual(row["limb_edge_error_max"], 3)
                    self.assertLessEqual(abs(row["lean_error"]), 2)
                    self.assertLessEqual(abs(row["visible_lean_error"]), 2)
                    self.assertTrue(row["matching_sign"])
                    self.assertTrue(row["visible_matching_sign"])
                    self.assertLessEqual(row["joint_gap_max"], 1.5)
                    self.assertLess(row["specular_dx"], 0)
                    self.assertLess(row["specular_dy"], 0)

    def test_angled_identities_and_curls_derive_from_single_canonical_sources(self):
        for character in ["mica", "talon"]:
            folder, bank = self.bank(character)
            for game, canonical in bank["canonical"].items():
                source = read_indexed(folder / canonical["curl"])
                glint = read_indexed(folder / canonical["curl_glint"])
                phases = []
                for derivation in canonical["curl_derivations"].values():
                    expected = derive_curl(source, bank["palette"],
                                           bank["allowed_palette_indices"][game],
                                           derivation["transform"], glint)
                    actual = read_indexed(folder / derivation["file"])
                    self.assertEqual(expected.tobytes(), actual.tobytes())
                    phases.append(actual.tobytes())
                self.assertEqual(len(phases), len(set(phases)), "Native ball glint phases must remain distinct")

    def test_mirrored_stride_actually_tilts_and_hips_own_the_transform(self):
        for character in ["mica", "talon"]:
            folder, bank = self.bank(character)
            for selector, orientation in [(3, "n"), (5, "n"), (6, "h"), (8, "h")]:
                frame = next(f for f in bank["frames"] if f["game"] == "s3k"
                             and f["id"] == selector and f["orientation"] == orientation)
                motion = frame["body_motion"]
                self.assertGreater(abs(motion["rotation"]), 2)
                point = posed_point(motion["pivot"], motion)
                rotated = rotate_point(motion["pivot"], motion["heading"])
                for j in range(2):
                    self.assertAlmostEqual(point[j], rotated[j] + motion["shift"][j])
                image, labels, _ = render_body(folder, bank, motion)
                self.assertEqual([bool(p) for p in image.get_flattened_data()],
                                 [bool(p) for p in labels.get_flattened_data()])

    def test_principal_axis_sign_matches_known_screen_directions(self):
        for degrees in [-135, -90, -15, 0, 15, 90, 135, 180]:
            radians = math.radians(degrees)
            samples = [(t * math.sin(radians), -t * math.cos(radians)) for t in range(20)]
            self.assertAlmostEqual(degrees, principal_axis(samples, degrees)["angle"], places=7)


if __name__ == "__main__":
    unittest.main()
