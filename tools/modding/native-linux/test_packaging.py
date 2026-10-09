"""Linux bundle regression controls; real native rendering is a separate check."""
import json
from pathlib import Path
import struct
import subprocess
import tempfile
import tarfile
import unittest
import zipfile
from build_linux import assemble, archive_bundle, digest, elf_x64
from prepare_toolchain import rootfs_filter

def fake_elf():
    data=bytearray(64);data[:6]=b"\x7fELF\x02\x01";struct.pack_into("<H",data,18,62);return data

class PackagingTests(unittest.TestCase):
    def test_linux_bundle_modes_paths_integrity_and_tamper(self):
        with tempfile.TemporaryDirectory(prefix="linux zip spaces ") as temp:
            root=Path(temp);image=root / "native/OpenGGF";image.parent.mkdir();image.write_bytes(fake_elf())
            (image.parent / "support.so").write_bytes(fake_elf())
            engine=root / "engine.jar"
            with zipfile.ZipFile(engine,"w") as jar:
                for i in range(5):jar.writestr(f"linux/x64/org/lwjgl/lib{i}.so",fake_elf())
            inputs=root / "inputs";(inputs / "mods").mkdir(parents=True)
            mod=inputs / "mods/hello-scene.jar"
            with zipfile.ZipFile(mod,"w") as jar:jar.writestr("META-INF/openggf-mod.yaml","id: hello-scene\n")
            (inputs / "build-info.json").write_text(json.dumps({"sourceCommit":"a"*40,"mods":[
                {"id":"hello-scene","slug":"hello-scene","jarSha256":digest(mod)}]}))
            contract=root / "contract";contract.mkdir();(contract / "members.tsv").write_text("C\tcom.openggf.Engine\n")
            graal=root / "graal";(graal / "legal").mkdir(parents=True)
            for name in ("LICENSE.txt","THIRD_PARTY_LICENSE.txt"):(graal / name).write_text("retained")
            bundle=root / "bundle with spaces";assemble(bundle,image,engine,inputs,contract,graal,{})
            shortcut=bundle / "Launch hello-scene.sh"
            subprocess.run(["sh","-n",shortcut],check=True)
            self.assertEqual(0o755,shortcut.stat().st_mode & 0o777)
            for name in ("s1.gen","s2.gen","s3k.gen"):self.assertIn(name,(bundle / "config.yaml").read_text())
            # Exercise the exact launcher from a different cwd and a path containing spaces.
            (bundle / "OpenGGF").write_text('#!/bin/sh\nprintf "%s\\n" "$@" > actual-args\n')
            subprocess.run([shortcut,"--audit"],cwd=root,check=True)
            self.assertEqual([f"-Dggfmod.dev.modDir={bundle}/quick-mods/hello-scene","--audit"],
                (bundle / "actual-args").read_text().splitlines())
            output=root / "friends.zip";archive_bundle(bundle,output)
            with zipfile.ZipFile(output) as archive:
                self.assertIsNone(archive.testzip())
                self.assertEqual(0o755,(archive.getinfo("bundle with spaces/Launch hello-scene.sh").external_attr>>16)&0o777)
            self.assertIn(digest(output),output.with_suffix(".zip.sha256").read_text())
            (bundle / "s1.gen").write_bytes(b"private")
            with self.assertRaisesRegex(ValueError,"ROM"):archive_bundle(bundle,root / "wrong.zip")
            mod.write_bytes(b"tampered")
            with self.assertRaisesRegex(ValueError,"hash drift"):assemble(root / "tamper",image,engine,inputs,contract,graal,{})

    def test_wrong_architecture_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp) / "binary";data=fake_elf();struct.pack_into("<H",data,18,183);path.write_bytes(data)
            with self.assertRaisesRegex(ValueError,"Linux x64 ELF"):elf_x64(path)

    def test_rootfs_links_remain_inside_extraction(self):
        with tempfile.TemporaryDirectory() as temp:
            link=tarfile.TarInfo("etc/alternatives/awk");link.type=tarfile.SYMTYPE;link.linkname="/usr/bin/mawk"
            self.assertEqual("../../usr/bin/mawk",rootfs_filter(link,temp).linkname)
            link.linkname="../../../../escaped"
            with self.assertRaises(tarfile.LinkOutsideDestinationError):rootfs_filter(link,temp)

if __name__=="__main__":unittest.main()
