"""Safety and constructed integer checks for the optional, explicitly nonofficial PCM harness.

Run with OPENGGF_PCM_ORACLE_SOURCE pointing to the external pinned source directory.
All source/binary scratch is outside the checkout. Origin: bounded PCM device task, 39dd03c50.
"""

import os
from pathlib import Path
import subprocess
import tempfile
import unittest

import pcm_oracle_check as oracle


class TestPcmOracleFingerprint(unittest.TestCase):
    def test_wrong_source_is_rejected_before_a_compiler_or_build_is_needed(self):
        with tempfile.TemporaryDirectory(prefix="pcm-fingerprint-", dir="/tmp") as directory:
            source = Path(directory)
            (source / "pcm.c").write_bytes(b"not the pinned C source\n")
            with self.assertRaisesRegex(ValueError, "pcm.c: SHA-256 mismatch"):
                with oracle.built_oracle(source, "missing-compiler-must-not-run"):
                    self.fail("unverified input reached compilation")


@unittest.skipUnless(os.environ.get("OPENGGF_PCM_ORACLE_SOURCE"), "explicit pinned source directory required")
class TestPcmIntegerOracle(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.context = oracle.built_oracle(Path(os.environ["OPENGGF_PCM_ORACLE_SOURCE"]))
        cls.program = cls.context.__enter__()

    @classmethod
    def tearDownClass(cls):
        cls.context.__exit__(None, None, None)

    def test_hand_constructed_sample_and_state_expectations(self):
        self.assertEqual(20, len(oracle.check_constructed_cases(self.program)))

    def test_protocol_rejects_unbounded_work_data_addresses_and_unsaved_restore(self):
        for command in ["N 0", "N 4097", "N 4294967295", "W 8192 0", "W 0 256", "W 0 -1",
                        "P 8 0", "P 0 4294967296", "R 8192", "L", "W 7 128 extra", "X"]:
            with self.subTest(command=command):
                with self.assertRaises(subprocess.CalledProcessError):
                    oracle.run_commands(self.program, [command])

    def test_negative_shift_and_half_register_updates_use_the_actual_c_types(self):
        result = oracle.run_commands(self.program, oracle.ram(0x100, 1)
                                    + oracle.channel(envelope=1, frequency=0x0801)
                                    + ["W 8 254", "N 1", "W 3 1", "W 2 127", "N 1"])
        self.assertEqual([-1, -1, -1, -1, True], result[0]["samples"][0])
        self.assertEqual(0x017f, result[1]["channels"][0][3])
        self.assertEqual(0x80980, result[1]["channels"][0][0])


if __name__ == "__main__":
    unittest.main()
