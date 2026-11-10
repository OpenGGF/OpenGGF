"""Temporal identity, native limb phases and reproducible generated-part assets."""
import json
from pathlib import Path
import unittest
from playable_art.locked_stage import rebuild, rotate_material_light, derive_curl, read_indexed
from playable_art.locked_stage_review import measure

ROOT = Path(__file__).resolve().parents[2]


class LockedStageTests(unittest.TestCase):
    def bank(self, character):
        folder = ROOT / f"examples/mica-and-talon/art/{character}/consistent-stage"
        return folder, json.loads((folder / "native-pose-generation.json").read_text())

    def test_all_shipped_frames_reassemble_and_obey_native_colour_budgets(self):
        for character in ["mica", "talon"]:
            folder, _ = self.bank(character)
            self.assertEqual(72, rebuild(folder, check=True))

    def test_upright_identity_is_one_drawing_with_native_offsets(self):
        for character in ["mica", "talon"]:
            _, bank = self.bank(character)
            for frame in bank["frames"]:
                if frame["layer"] != "upright":
                    continue
                canonical = bank["canonical"][frame["game"]]
                for kind in ["head", "torso"]:
                    placements = [p for p in frame["placements"]
                                  if f"-{kind}-angle" in p["part"]]
                    self.assertEqual(1, len(placements))
                    placement = placements[0]
                    self.assertEqual(f'parts/{frame["game"]}-{kind}-angle{frame["head_angle"]}.png',
                                     placement["part"])
                    if frame["game"] == "s3k":
                        self.assertEqual(0, frame["head_angle"])
                        self.assertEqual(canonical[f"{kind}_dimensions"], placement["size"])
                        self.assertEqual([a + b for a, b in zip(canonical[f"{kind}_position"],
                                                               frame["head_offset"])],
                                         placement["position"])

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
                    self.assertLessEqual(row["shoe_contact_error_max"], 3.25)
                    self.assertLessEqual(row["limb_edge_error_max"], 3)

    def test_angled_identities_and_curls_derive_from_single_canonical_sources(self):
        for character in ["mica", "talon"]:
            folder, bank = self.bank(character)
            for game, canonical in bank["canonical"].items():
                angles = {f["head_angle"] for f in bank["frames"]
                          if f["game"] == game and f["layer"] == "upright"}
                for kind in ["head", "torso"]:
                    source = read_indexed(folder / canonical[kind])
                    for angle in angles:
                        expected = rotate_material_light(source, angle, canonical["material_ramp"])
                        actual = read_indexed(folder / f"parts/{game}-{kind}-angle{angle}.png")
                        self.assertEqual(expected.size, actual.size)
                        self.assertEqual(expected.tobytes(), actual.tobytes())
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


if __name__ == "__main__":
    unittest.main()
