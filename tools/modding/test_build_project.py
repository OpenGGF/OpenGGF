"""Portable launcher behavior: local settings, diagnostics and legacy interfaces."""
import contextlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import build_project as launcher


class BuildProjectTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="creator project ")
        self.addCleanup(self.temp.cleanup)
        self.project = Path(self.temp.name) / "my mod"
        resources = self.project / "src/main/resources"
        (resources / "META-INF").mkdir(parents=True)
        (resources / "META-INF/openggf-mod.yaml").write_text("formatVersion: 1\nid: sample\n")
        self.engine = self.jar("engine.jar", "version.properties")
        self.sdk = self.jar("sdk.jar", "META-INF/openggf-build.properties")
        self.testkit = self.jar("testkit.jar", "META-INF/openggf-build.properties")
        self.config = self.project / launcher.LOCAL_CONFIG

    def jar(self, name, metadata, commit="abc123"):
        path = Path(self.temp.name) / name
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr(metadata, f"app.commit={commit}\napp.baseVersion=0.7\n")
        return path

    def settings(self, **values):
        self.config.write_text(json.dumps(dict(schemaVersion=1, engine="../engine.jar", sdk="../sdk.jar", **values)))

    def invoke(self, *flags):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            result = launcher.main([str(self.project), *map(str, flags)])
        return result, output.getvalue()

    def probe(self, command, **kwargs):
        return subprocess.CompletedProcess(command, 0, Path(command[0]).stem + " 25.0.1\n", "")

    def doctor(self, *flags):
        with patch.object(launcher.subprocess, "run", side_effect=self.probe):
            result, output = self.invoke("--doctor", "--format", "json", *flags)
        return result, json.loads(output)

    def test_config_relative_paths_cli_overrides_and_spaces(self):
        self.settings(s2="../own rom.gen", testkit="../testkit.jar")
        data = json.loads(self.config.read_text())
        data["engine"] = "missing-overridden-engine.jar"
        self.config.write_text(json.dumps(data))
        rom = Path(self.temp.name) / "override rom.gen"
        rom.touch()
        with patch.object(launcher, "doctor", return_value=[]), patch.object(launcher, "build") as build:
            result, _ = self.invoke("--run", "--s2", rom, "--engine", self.engine)
        self.assertEqual(0, result)
        args, kwargs = build.call_args
        self.assertEqual((self.project, self.engine, self.sdk), args)
        self.assertEqual(rom, kwargs["roms"]["sonic2"])
        self.assertEqual(self.testkit, kwargs["testkit"])
        self.assertTrue(kwargs["run"])

    def test_external_config_paths_use_config_directory(self):
        self.settings()
        external = Path(self.temp.name) / "settings.json"
        external.write_text(json.dumps(dict(schemaVersion=1, engine="engine.jar", sdk="sdk.jar")))
        result, output = self.invoke("--config", external, "--verify-only")
        self.assertEqual(0, result)
        self.assertEqual("abc123", json.loads(output)["app.commit"])

    def test_legacy_explicit_paths_verify_without_jdk_or_project_build(self):
        with patch.object(launcher.subprocess, "run", side_effect=AssertionError("must not probe")):
            result, output = self.invoke("--verify-only", "--engine", self.engine, "--sdk", self.sdk)
        self.assertEqual(0, result)
        self.assertEqual("abc123", json.loads(output)["app.commit"])
        self.assertFalse((self.project / "target").exists())

    def test_legacy_build_flags(self):
        with patch.object(launcher, "doctor", return_value=[]), patch.object(launcher, "build") as build:
            self.invoke("--engine", self.engine, "--sdk", self.sdk, "--testkit", self.testkit, "--test")
        self.assertTrue(build.call_args.kwargs["test"])

    def test_default_config_absent_is_optional_but_explicit_missing_fails(self):
        result, report = self.doctor("--engine", self.engine, "--sdk", self.sdk)
        self.assertEqual(0, result)
        result, report = self.doctor("--config", self.project / "missing.json")
        self.assertEqual(1, result)
        self.assertEqual("config.invalid", report["findings"][0]["code"])

    def test_strict_invalid_configs_fail_as_json(self):
        for content in ('{', '[]', '{"schemaVersion":true}', '{"schemaVersion":2}',
                        '{"schemaVersion":1,"unknown":1}', '{"schemaVersion":1,"sdk":null}',
                        '{"schemaVersion":1,"sdk":""}', '{"schemaVersion":1,"sdk":3}',
                        '{"schemaVersion":1,"sdk":"a","sdk":"b"}'):
            with self.subTest(content=content):
                self.config.write_text(content)
                result, report = self.doctor()
                self.assertEqual(1, result)
                self.assertEqual("fail", report["result"])
                self.assertEqual(1, report["formatVersion"])
                self.assertEqual({"code", "severity", "message", "path", "guidance"}, set(report["findings"][0]))

    def test_mismatching_and_malformed_artifacts(self):
        self.settings()
        wrong = self.jar("wrong.jar", "META-INF/openggf-build.properties", "different")
        for artifact in (wrong, self.config):
            if artifact == self.config:
                self.config.write_text("{}")
            # CLI-only configuration to inspect an invalid ZIP independently.
            result, report = self.doctor("--config", self.write_external_config(), "--sdk", artifact)
            self.assertEqual(1, result)
            self.assertIn("artifact.identity", [f["code"] for f in report["findings"]])

    def write_external_config(self):
        path = Path(self.temp.name) / "valid.json"
        path.write_text(json.dumps(dict(schemaVersion=1, engine=str(self.engine), sdk=str(self.sdk))))
        return path

    def test_optional_testkit_rom_maven_not_required_for_build_doctor(self):
        self.settings()
        with patch.object(launcher.shutil, "which", return_value=None):
            result, report = self.doctor()
        self.assertEqual(0, result)
        self.assertEqual([], report["findings"])

    def test_cached_console_test_setup_does_not_need_maven(self):
        self.settings(testkit="../testkit.jar")
        tests = self.project / "src/test/java/ModTest.java"
        tests.parent.mkdir(parents=True)
        tests.write_text("class ModTest {}")
        console = self.project / "target/test-libs/junit-platform-console-standalone-1.10.3.jar"
        console.parent.mkdir(parents=True)
        console.touch()
        with patch.object(launcher.shutil, "which", return_value=None):
            result, report = self.doctor("--test")
        self.assertEqual(0, result)
        self.assertEqual([], report["findings"])

    def test_legacy_default_artifacts_still_resolve_beside_kit_tools(self):
        with patch.object(launcher, "doctor", return_value=[]), patch.object(launcher, "build") as build:
            result, _ = self.invoke()
        self.assertEqual(0, result)
        kit = Path(launcher.__file__).resolve().parent.parent
        self.assertEqual((self.project, kit / "engine.jar", kit / "sdk.jar"), build.call_args.args)

    def test_test_prerequisites_and_supplied_rom_are_actionable(self):
        self.settings(s1="missing.gen")
        with patch.object(launcher.shutil, "which", return_value=None):
            result, report = self.doctor("--test")
        self.assertEqual(1, result)
        self.assertEqual({"testkit.required", "test.maven", "test.sources", "rom.missing"},
                         {f["code"] for f in report["findings"]})
        self.assertTrue(all(f["guidance"] for f in report["findings"]))

    def test_doctor_is_read_only_and_only_probes_jdk(self):
        self.settings()
        before = {p: p.read_bytes() for p in Path(self.temp.name).rglob("*") if p.is_file()}
        with patch.object(launcher.subprocess, "run", side_effect=self.probe) as run:
            result, output = self.invoke("--doctor", "--format=json", "--run")
        self.assertEqual(0, result)
        json.loads(output)
        self.assertEqual([["--version"]] * 3, [c.args[0][1:] for c in run.call_args_list])
        after = {p: p.read_bytes() for p in Path(self.temp.name).rglob("*") if p.is_file()}
        self.assertEqual(before, after)
        self.assertFalse((self.project / "target").exists())

    def test_missing_or_wrong_jdk_fails_without_traceback(self):
        self.settings()
        for outcome in (FileNotFoundError("java unavailable"), subprocess.CompletedProcess([], 0, "openjdk 21", "")):
            with patch.object(launcher.subprocess, "run", side_effect=outcome if isinstance(outcome, Exception) else None,
                              return_value=outcome):
                result, output = self.invoke("--doctor", "--format", "json")
            self.assertEqual(1, result)
            self.assertEqual(["jdk.tool"] * 3, [f["code"] for f in json.loads(output)["findings"]])

    def test_argument_failure_json_equals_form(self):
        result, output = self.invoke("--doctor", "--format=json", "--unknown")
        self.assertEqual(1, result)
        self.assertEqual("fail", json.loads(output)["result"])

    def test_starter_conversions_use_authored_inputs_and_fresh_output(self):
        source = self.project / "src/main/mod"
        source.mkdir(parents=True)
        (source / "sample.png").write_bytes(b"original PNG")
        (source / "sample-sheet.yaml").write_text("original sheet")
        (source / "level-source").mkdir()
        classes = self.project / "target/classes"
        (classes / "art").mkdir(parents=True)
        (classes / "art/sample.ggfs").touch()
        (classes / "levels/sample").mkdir(parents=True)
        with patch.object(launcher.subprocess, "run") as run:
            launcher.convert_starter_resources(self.project, classes, ["sdk-cli"])
        self.assertFalse((classes / "art/sample.ggfs").exists())
        self.assertFalse((classes / "levels/sample").exists())
        self.assertEqual(["sdk-cli", "convert", "art", "--image", str(source / "sample.png"),
                          "--sheet", str(source / "sample-sheet.yaml"), "--out", str(classes / "art/sample.ggfs")],
                         run.call_args_list[0].args[0])
        self.assertEqual(["sdk-cli", "convert", "level", "--from-export", str(source / "level-source"),
                          "--out", str(classes / "levels/sample")], run.call_args_list[1].args[0])
        self.assertEqual(b"original PNG", (source / "sample.png").read_bytes())

    def test_local_settings_never_copied_to_packaged_resources(self):
        resources = self.project / "src/main/resources"
        (resources / launcher.LOCAL_CONFIG).write_text('{"s1":"private"}')
        target = self.project / "target/classes"
        launcher.copy_resources(self.project, target)
        self.assertFalse((target / launcher.LOCAL_CONFIG).exists())
        self.assertTrue((target / "META-INF/openggf-mod.yaml").is_file())


if __name__ == "__main__":
    unittest.main()
