"""TalonSolar.java must carry the Solar tables from solar.yaml (see solar_java.py)."""
import pathlib
import subprocess
import sys
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parent / "playable_art" / "solar_java.py"


class TalonSolarSyncTest(unittest.TestCase):
    def test_java_tables_match_yaml(self):
        result = subprocess.run([sys.executable, "-I", str(SCRIPT), "--check"], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
