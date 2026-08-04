"""Guard the v5 publication transaction and protected predecessor archive."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "docs/architecture/validation/trace/2026-08-04-trace-v5-publication-manifest.md"

EXPECTED_DELETIONS = {
    "src/test/resources/traces/s1/ghz1_fullrun/aux_state_retro.jsonl",
    "src/test/resources/traces/s1/ghz1_fullrun/aux_state_retro.jsonl.gz",
    "src/test/resources/traces/s1/ghz1_fullrun/metadata_retro.json",
    "src/test/resources/traces/s1/ghz1_fullrun/physics_retro.csv",
    "src/test/resources/traces/s1/mz1_fullrun/aux_state_retro.jsonl",
    "src/test/resources/traces/s1/mz1_fullrun/aux_state_retro.jsonl.gz",
    "src/test/resources/traces/s1/mz1_fullrun/metadata_retro.json",
    "src/test/resources/traces/s1/mz1_fullrun/physics_retro.csv",
    "src/test/resources/traces/s3k/ending_completerun/aux_state.jsonl.gz",
    "src/test/resources/traces/s3k/ending_completerun/hardware_timing.jsonl",
    "src/test/resources/traces/s3k/ending_completerun/physics.csv.gz",
}


class TraceV5PublicationManifestTests(unittest.TestCase):
    def test_exact_deletion_set_and_protected_archive(self) -> None:
        text = MANIFEST.read_text(encoding="utf-8")
        section = text.split("## True deletions", 1)[1].split("```", 2)[1]
        listed = {
            line for line in section.splitlines()
            if line.strip() and line.strip() != "text"
        }
        self.assertEqual(EXPECTED_DELETIONS | {
            line for line in listed if line.startswith("src/test/resources/traces/synthetic/")
        }, listed)
        self.assertTrue(EXPECTED_DELETIONS <= listed)
        self.assertFalse(any("2026-08-04-s1-credits-predecessor" in path for path in listed))

        archive = ROOT / "docs/architecture/validation/trace/2026-08-04-s1-credits-predecessor"
        fixture_dirs = sorted(path for path in archive.iterdir() if path.is_dir())
        self.assertEqual(8, len(fixture_dirs))
        self.assertTrue(all(
            {path.name for path in directory.iterdir()} == {"aux_state.jsonl", "metadata.json", "physics.csv"}
            for directory in fixture_dirs
        ))
        self.assertRegex(text, re.escape("ending_completerun/metadata.json") + r".*hpz22_completerun/metadata.json")


if __name__ == "__main__":
    unittest.main()
