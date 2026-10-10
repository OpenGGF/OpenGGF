"""Regression controls for Windows archive integrity and launcher inputs.

Runs without a Windows compiler; actual binary/audit/registration qualification
is performed by build_windows.py on the Windows builder.
"""
import json
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile

from build_windows import archive_bundle, assemble, digest, extract_jar, pe_x64, run
from qualify_wine import verify_bundle


def fake_pe():
    data = bytearray(128)
    data[:2] = b"MZ"
    struct.pack_into("<I", data, 60, 64)
    data[64:68] = b"PE\0\0"
    struct.pack_into("<H", data, 68, 0x8664)
    return data


class PackagingTests(unittest.TestCase):
    def test_complete_bundle_and_tampered_mod(self):
        with tempfile.TemporaryDirectory(prefix="windows zip spaces ") as temp:
            root = Path(temp)
            image = root / "native/OpenGGF.exe"
            image.parent.mkdir()
            image.write_bytes(fake_pe())
            (image.parent / "support.dll").write_bytes(fake_pe())
            engine = root / "engine.jar"
            with zipfile.ZipFile(engine, "w") as jar:
                for index in range(5):
                    jar.writestr(f"windows/x64/org/lwjgl/{index}/lib{index}.dll", fake_pe())
            inputs = root / "inputs"
            (inputs / "mods").mkdir(parents=True)
            mod = inputs / "mods/hello-scene.jar"
            with zipfile.ZipFile(mod, "w") as jar:
                jar.writestr("META-INF/openggf-mod.yaml", "id: hello-scene\n")
                jar.writestr("content/sample.bin", b"unchanged")
            (inputs / "build-info.json").write_text(json.dumps({"sourceCommit": "a" * 40,
                "mods": [{"slug": "hello-scene", "id": "hello-scene", "jarSha256": digest(mod)}]}))
            contract = root / "contract"
            contract.mkdir()
            (contract / "members.tsv").write_text("C\tcom.openggf.Engine\n")
            graal = root / "graal"
            (graal / "legal").mkdir(parents=True)
            (graal / "legal/notice").write_text("notice")
            for name in ("LICENSE.txt", "THIRD_PARTY_LICENSE.txt"):
                (graal / name).write_text("retained")
            bundle = root / "bundle"
            assemble(bundle, image, engine, inputs, contract, graal, {}, "b" * 40)
            self.assertEqual("b" * 40, json.loads((bundle / "build-info.json").read_text())["nativeBuildSourceCommit"])
            self.assertIn("/tree/" + "b" * 40, (bundle / "README.txt").read_text())
            launcher = (bundle / "_launch.bat").read_text()
            self.assertIn('pushd "%~dp0"', launcher)
            self.assertIn('OpenGGF.exe %*', launcher)
            self.assertIn('-Dggfmod.dev.modDir=%~dp0quick-mods\\hello-scene" %*',
                          (bundle / "Launch hello-scene.bat").read_text())
            self.assertEqual(b"unchanged", (bundle / "quick-mods/hello-scene/content/sample.bin").read_bytes())
            config = (bundle / "config.yaml").read_text()
            for rom in ("s1.gen", "s2.gen", "s3k.gen"): self.assertIn('"' + rom + '"', config)
            output = root / "friends.zip"
            archive_bundle(bundle, output)
            self.assertEqual("b" * 40, verify_bundle(bundle)["nativeBuildSourceCommit"])
            untouched = (bundle / "SHA256SUMS.txt").read_bytes()
            (bundle / "SHA256SUMS.txt").write_text("")
            with self.assertRaisesRegex(ValueError, "coverage"): verify_bundle(bundle)
            (bundle / "SHA256SUMS.txt").write_bytes(untouched)
            with zipfile.ZipFile(output) as archive:
                self.assertIsNone(archive.testzip())
                self.assertIn("bundle/Launch hello-scene.bat", archive.namelist())
                self.assertNotIn("bundle/engine.jar", archive.namelist())
                self.assertIn("bundle/LICENSES/GraalVM/legal/notice", archive.namelist())
            self.assertIn(digest(output), output.with_suffix(".zip.sha256").read_text())
            with self.assertRaises(FileExistsError): archive_bundle(bundle, output)
            (bundle / "s1.gen").write_bytes(b"private")
            with self.assertRaisesRegex(ValueError, "ROM"): archive_bundle(bundle, root / "roms.zip")
            mod.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError, "hash drift"):
                assemble(root / "tampered", image, engine, inputs, contract, graal, {})

    def test_invalid_pe_and_traversal(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            binary = root / "wrong.exe"
            data = fake_pe()
            struct.pack_into("<H", data, 68, 0x14c)
            binary.write_bytes(data)
            with self.assertRaisesRegex(ValueError, "AMD64"): pe_x64(binary)
            binary.write_bytes(b"text")
            with self.assertRaisesRegex(ValueError, "PE header"): pe_x64(binary)
            jar = root / "unsafe.jar"
            with zipfile.ZipFile(jar, "w") as archive: archive.writestr("../escaped", b"bad")
            with self.assertRaisesRegex(ValueError, "Unsafe"): extract_jar(jar, root / "extract")
            self.assertFalse((root / "escaped").exists())

    def test_abort_is_not_a_missing_member_pass(self):
        with tempfile.TemporaryDirectory() as temp:
            for code in (0, 2, 134):
                with self.assertRaisesRegex(AssertionError, "ordinary exit 1"):
                    run([sys.executable, "-c", f"print('MISSING native member: F'); raise SystemExit({code})"], temp,
                        "MISSING native member: F")
            run([sys.executable, "-c", "print('MISSING native member: F'); raise SystemExit(1)"], temp,
                "MISSING native member: F")


if __name__ == "__main__": unittest.main()
