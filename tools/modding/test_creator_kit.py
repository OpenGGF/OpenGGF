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
        self.campaign()
        self.artifacts()

    def campaign(self):
        project = self.root / kit.CAMPAIGN_SOURCE
        for relative, data in {"README.md": "[guide](../../../../../../docs/modding/guides/two-act-campaign.md)\n",
                               "tools/generate_assets.py": "# original reproducible fixture generator\n",
                               "src/main/java/example/tide/TideCircuitMod.java": "// original campaign fixture\n",
                               "src/main/resources/META-INF/openggf-mod.yaml": "id: sample-tide-circuit\nbaseGame: s2\n"}.items():
            path = project / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(data)
        for act in (1, 2):
            folder = project / f"src/main/resources/levels/tide/act{act}"
            folder.mkdir(parents=True)
            assets = {name.removesuffix(".bin"): name for name in sorted(kit.CAMPAIGN_ASSETS)}
            (folder / "level.json").write_text(json.dumps({"assets": assets}))
            for name in kit.CAMPAIGN_ASSETS:
                (folder / name).write_bytes((str(act) + name).encode())

    def artifacts(self, commit="abcdef123", dirty=False):
        identity = f"app.baseVersion=0.7.prerelease\napp.commit={commit}\napp.dirty={str(dirty).lower()}\n"
        for suffix in ("jar-with-dependencies", "openggf-mod-sdk", "openggf-mod-sdk-javadoc", "mod-testkit"):
            with zipfile.ZipFile(self.root / f"target/OpenGGF-0.7.prerelease-{suffix}.jar", "w") as jar:
                jar.writestr("version.properties" if suffix == "jar-with-dependencies" else "META-INF/openggf-build.properties", identity)
                if suffix == "mod-testkit":
                    for name in ("ModTestKit", "DeterministicInput", "CreatorTestLauncher"):
                        jar.writestr("com/openggf/mods/testing/" + name + ".class", b"class fixture")

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

    def test_rejects_incomplete_or_mixed_testkit_classifier_contents(self):
        artifact = self.root / "target/OpenGGF-0.7.prerelease-mod-testkit.jar"
        with zipfile.ZipFile(artifact) as archive:
            original = {name: archive.read(name) for name in archive.namelist()}
        required = "com/openggf/mods/testing/CreatorTestLauncher.class"
        with zipfile.ZipFile(artifact, "w") as archive:
            for name, data in original.items():
                if name != required:
                    archive.writestr(name, data)
        with self.assertRaisesRegex(ValueError, "incomplete.*CreatorTestLauncher"):
            kit.collect(self.root, "abcdef123456")
        for unrelated in ("com/openggf/Engine.class", "com/openggf/tools/modsdk/GgfModCli.class",
                          "org/junit/jupiter/api/Test.class", "com/openggf/mods/testing/source.java"):
            with self.subTest(unrelated=unrelated), zipfile.ZipFile(artifact, "w") as archive:
                for name, data in original.items():
                    archive.writestr(name, data)
                archive.writestr(unrelated, b"unrelated fixture")
            with self.assertRaisesRegex(ValueError, "unrelated inputs"):
                kit.collect(self.root, "abcdef123456")

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

    def test_exported_readme_leads_with_artifact_build_and_run_instructions(self):
        project = self.root / "examples/hello-scene"
        main = project / "src/main/resources/META-INF/openggf-mod.yaml"
        main.parent.mkdir(parents=True)
        main.write_text("id: hello-scene\nbaseGame: s3k\n")
        original = "# Original scene notes\n\nBuild from source with python3 play.py.\n"
        (project / "README.md").write_text(original)
        readme = kit.export_examples(self.root)["examples/hello-scene/README.md"].decode()
        self.assertTrue(readme.startswith("# Build hello-scene from the creator kit"))
        self.assertLess(readme.index("python3 ../../tools/build_project.py ."), readme.index("python3 play.py"))
        for instruction in ("21 JDK", "-Dopenggf.engine.jar=/absolute/kit/engine.jar",
                            "-Dopenggf.sdk.jar=/absolute/kit/sdk.jar", "target/hello-scene-mod.jar",
                            "--run --s3k /absolute/own-s3k.gen", "target/play"):
            self.assertIn(instruction, readme)
        self.assertTrue(readme.endswith(original))

    def test_campaign_export_contains_all_original_assets_generator_and_local_guide_links(self):
        guide = self.root / "docs/modding/guides/two-act-campaign.md"
        guide.parent.mkdir(parents=True)
        guide.write_text("[complete campaign](../../../src/test/resources/mods/sample-two-act-campaign-src/project/)\n")
        entries = kit.collect(self.root, "abcdef123456")
        prefix = "examples/tide-circuit/"
        assets = [name for name in entries if name.startswith(prefix) and name.endswith(".bin")]
        self.assertEqual(22, len(assets))
        for name in assets:
            source = self.root / kit.CAMPAIGN_SOURCE / name.removeprefix(prefix)
            self.assertEqual(source.read_bytes(), entries[name])
        self.assertIn(prefix + "tools/generate_assets.py", entries)
        pom = entries[prefix + "pom.xml"]
        self.assertIn(b"<artifactId>tide-circuit</artifactId>", pom)
        self.assertIn(b"maven-compiler-plugin</artifactId><version>3.14.0", pom)
        self.assertNotIn(b"published-openggf", pom)
        self.assertIn(b"--run --s2 /absolute/own-s2.gen", entries[prefix + "README.md"])
        self.assertIn(b"../../handbook/guides/two-act-campaign.md", entries[prefix + "README.md"])
        self.assertIn(b"../../examples/tide-circuit/README.md", entries["handbook/guides/two-act-campaign.md"])

    def test_campaign_export_fails_on_missing_generated_source_assets_or_generator(self):
        project = self.root / kit.CAMPAIGN_SOURCE
        asset = project / "src/main/resources/levels/tide/act2/patterns.bin"
        payload = asset.read_bytes()
        asset.unlink()
        with self.assertRaisesRegex(ValueError, "Missing maintained campaign asset.*act2/patterns.bin"):
            kit.export_examples(self.root)
        asset.write_bytes(payload)
        (project / "tools/generate_assets.py").unlink()
        with self.assertRaisesRegex(ValueError, "Missing maintained campaign source input.*generate_assets.py"):
            kit.export_examples(self.root)

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
