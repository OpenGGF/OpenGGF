"""Creator kit contracts: reproducibility, identity and authored-source boundaries."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import build_creator_kit as kit
import build_project


class CreatorKitTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "target").mkdir()
        (self.root / "docs/modding").mkdir(parents=True)
        (self.root / "examples").mkdir()
        (self.root / "tools/modding").mkdir(parents=True)
        (self.root / "tools/modding/build_project.py").write_text("# launcher")
        (self.root / "pom.xml").write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><version>0.7.prerelease</version></project>')
        (self.root / "mod-api-release-policy.properties").write_text("currentApi=0.7.0\ncurrentStatus=candidate\n")
        self.artifacts()

    def artifacts(self, commit="abcdef123", dirty=False):
        identity = f"app.baseVersion=0.7.prerelease\napp.commit={commit}\napp.dirty={str(dirty).lower()}\n"
        for suffix in ("jar-with-dependencies", "openggf-mod-sdk", "openggf-mod-sdk-javadoc", "mod-testkit"):
            with zipfile.ZipFile(self.root / f"target/OpenGGF-0.7.prerelease-{suffix}.jar", "w") as jar:
                jar.writestr("version.properties" if suffix == "jar-with-dependencies" else "META-INF/openggf-build.properties", identity)

    def test_reproducible_archive_hash_inventory_and_no_generated_assets(self):
        project = self.root / "examples/demo"
        (project / "src/main/resources/META-INF").mkdir(parents=True)
        (project / "src/main/resources/META-INF/openggf-mod.yaml").write_text("id: demo")
        (project / "target").mkdir()
        (project / "target/player.gen").write_bytes(b"not exported")
        (project / "config.yaml").write_text("private paths")
        (project / "README.md").write_text("[handbook](../../docs/modding/index.md)")
        (self.root / "docs/modding/index.md").write_text("[example](../../examples/demo/README.md) [source](../../src/Example.java)")
        entries = kit.collect(self.root, "abcdef123456")
        manifest = json.loads(entries["creator-kit.json"])
        self.assertEqual("candidate", manifest["apiStatus"])
        self.assertFalse(manifest["sourceDirty"])
        for name, digest in manifest["sha256"].items():
            self.assertEqual(hashlib.sha256(entries[name]).hexdigest(), digest)
        self.assertFalse(any("player.gen" in name or "config.yaml" in name for name in entries))
        self.assertIn(b"maven-compiler-plugin", entries["examples/demo/pom.xml"])
        self.assertIn(b"../../handbook/index.md", entries["examples/demo/README.md"])
        self.assertIn(b"../examples/demo/README.md", entries["handbook/index.md"])
        self.assertIn(b"https://github.com/OpenGGF/OpenGGF/blob/abcdef123456/src/Example.java", entries["handbook/index.md"])
        first, second = self.root / "first.zip", self.root / "second.zip"
        kit.write_zip(entries, first)
        kit.write_zip(entries, second)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        with self.assertRaises(FileExistsError):
            kit.write_zip(entries, first)

    def test_rejects_mismatched_stale_and_unlabelled_dirty_artifacts(self):
        self.artifacts(commit="old")
        with self.assertRaisesRegex(ValueError, "commit"):
            kit.collect(self.root, "abcdef123456")
        self.artifacts(dirty=True)
        with self.assertRaisesRegex(ValueError, "dirty"):
            kit.collect(self.root, "abcdef123456")
        self.assertTrue(json.loads(kit.collect(self.root, "abcdef123456", True)["creator-kit.json"])["sourceDirty"])
        with zipfile.ZipFile(self.root / "target/OpenGGF-0.7.prerelease-mod-testkit.jar", "w") as jar:
            jar.writestr("META-INF/openggf-build.properties", "app.commit=different")
        with self.assertRaisesRegex(ValueError, "do not match"):
            kit.collect(self.root, "abcdef123456", True)

    def test_clean_engine_does_not_hide_edited_kit_sources(self):
        with self.assertRaisesRegex(ValueError, "dirty"):
            kit.collect(self.root, "abcdef123456", source_dirty=True)
        report = json.loads(kit.collect(self.root, "abcdef123456", True, source_dirty=True)["creator-kit.json"])
        self.assertTrue(report["sourceDirty"])

    def test_jar_payloads_are_reproducible_across_maven_entry_timestamps_and_order(self):
        archives = []
        for year, names in ((2020, ("b", "a")), (2026, ("a", "b"))):
            stream = io.BytesIO()
            with zipfile.ZipFile(stream, "w") as archive:
                for name in names:
                    archive.writestr(zipfile.ZipInfo(name, (year, 1, 1, 0, 0, 0)), name.encode())
            archives.append(kit.normalize_jar(stream.getvalue()))
        self.assertEqual(*archives)
        with zipfile.ZipFile(io.BytesIO(archives[0])) as archive:
            self.assertEqual(b"a", archive.read("a"))

    def test_portable_launch_isolates_config_and_passes_space_containing_rom_paths(self):
        project = self.root / "my project"
        (project / "src/main/resources/META-INF").mkdir(parents=True)
        (project / "src/main/resources/META-INF/openggf-mod.yaml").write_text("id: demo")
        (project / "config.yaml").write_text("untouched")
        rom = self.root / "own s2.gen"
        rom.write_bytes(b"own asset")
        engine, sdk = self.root / "engine.jar", self.root / "sdk.jar"
        for artifact, path in ((engine, "version.properties"), (sdk, "META-INF/openggf-build.properties")):
            with zipfile.ZipFile(artifact, "w") as archive:
                archive.writestr(path, "app.baseVersion=0.7.prerelease\napp.commit=abcdef123\napp.dirty=false")
        with patch.object(build_project.subprocess, "run") as run:
            build_project.build(project, engine, sdk, run=True, roms={"sonic2": rom})
        self.assertEqual("untouched", (project / "config.yaml").read_text())
        config = (project / "target/play/config.yaml").read_text()
        self.assertIn(json.dumps(str(rom)), config)
        self.assertEqual(project / "target/play", run.call_args.kwargs["cwd"])
        self.assertEqual(["run", str(project / "target/classes")], run.call_args.args[0][-2:])
        self.assertEqual(b"own asset", rom.read_bytes())

    def test_exported_hello_tests_use_opt_in_jupiter_profile_and_never_enter_production_inputs(self):
        project = self.root / "examples/hello-scene"
        main = project / "src/main/resources/META-INF/openggf-mod.yaml"
        main.parent.mkdir(parents=True)
        main.write_text("id: hello-scene")
        specimen = project / "src/test/java/hello/HelloSceneIntegrationTest.java"
        specimen.parent.mkdir(parents=True)
        specimen.write_text("// production-backed test specimen")
        entries = kit.export_examples(self.root)
        self.assertEqual(specimen.read_bytes(), entries["examples/hello-scene/src/test/java/hello/HelloSceneIntegrationTest.java"])
        pom = entries["examples/hello-scene/pom.xml"]
        self.assertIn(b"src/creator-tests-disabled", pom)
        self.assertIn(b"<skipTests>true</skipTests>", pom)
        self.assertIn(b"<skipTests>false</skipTests>", pom)
        self.assertIn(b"<classifier>mod-testkit</classifier><scope>test</scope>", pom)
        self.assertIn(b"<artifactId>maven-surefire-plugin</artifactId><version>3.2.5", pom)

    def test_portable_build_refuses_wrong_testkit_identity_before_compiling(self):
        engine = self.root / "target/OpenGGF-0.7.prerelease-jar-with-dependencies.jar"
        sdk = self.root / "target/OpenGGF-0.7.prerelease-openggf-mod-sdk.jar"
        testkit = self.root / "wrong-testkit.jar"
        with zipfile.ZipFile(testkit, "w") as archive:
            archive.writestr("META-INF/openggf-build.properties", "app.commit=other")
        with self.assertRaisesRegex(ValueError, "same candidate"):
            build_project.matching_artifacts(engine, sdk, testkit)


if __name__ == "__main__":
    unittest.main()
