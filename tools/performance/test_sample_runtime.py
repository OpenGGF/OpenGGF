"""Measurement controls: requested storage and duplicate FDs must not inflate VRAM."""
import tempfile
import unittest
from pathlib import Path
from sample_runtime import drm_clients, memory_bytes


class RuntimeSamplingControls(unittest.TestCase):
    def test_resident_storage_deduplicates_only_within_device(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            discrete = "drm-client-id: 7\ndrm-pdev: 0000:03:00.0\ndrm-total-vram: 64 MiB\ndrm-resident-vram: 4 MiB\ndrm-shared-vram: 0\n"
            (root / "1").write_text(discrete)
            (root / "2").write_text(discrete)
            (root / "3").write_text("drm-client-id: 7\ndrm-pdev: 0000:19:00.0\ndrm-resident-vram: 3 KiB\n")
            clients = drm_clients(root)
            self.assertEqual(2, len(clients))
            self.assertEqual(4 * 1024 * 1024 + 3 * 1024,
                             sum(c["resident_vram"] for c in clients))

    def test_unknown_units_fail_instead_of_becoming_plausible_bytes(self):
        self.assertEqual(1024, memory_bytes("1 kB"))
        self.assertEqual(1024 * 1024, memory_bytes("1 MiB"))
        with self.assertRaises(ValueError):
            memory_bytes("1 unknown")


if __name__ == "__main__":
    unittest.main()
