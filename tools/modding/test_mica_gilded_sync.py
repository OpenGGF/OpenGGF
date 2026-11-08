"""MicaGilded.java must carry the Gilded tables from gilded.yaml (see gilded_java.py)."""
import pathlib
import subprocess
import sys
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parent / "playable_art" / "gilded_java.py"


class MicaGildedSyncTest(unittest.TestCase):
    def test_java_tables_match_yaml(self):
        result = subprocess.run([sys.executable, "-I", str(SCRIPT), "--check"], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
