#!/usr/bin/env python3
"""Portable packaging regressions; run: python3 -m unittest discover -s racing/server -p 'test_*.py'.

Synthetic compiled outputs exercise relocation, identity preservation and the image
context boundary without Maven, ROMs or a Docker daemon. Real service/container
qualification remains separate. Origin: 2026-10-23 racing Docker deployment.
"""
import contextlib
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


HERE = Path(__file__).resolve().parent
build = load_module("racing_build", HERE / "build.py")
images = load_module("racing_images", HERE.parent / "deploy" / "build-images.py")


class PortableDistributionTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.checkout = self.root / "original checkout"
        self.engine = self.checkout / "target" / "classes"
        self.server = self.checkout / "target" / "racing-server" / "classes"
        self.dist = self.checkout / "target" / "racing-server" / "dist"
        self.engine.mkdir(parents=True)
        self.server.mkdir(parents=True)
        self.version = b"version=0.7.prerelease\ngit.commit=123456789\ngit.dirty=true\n"
        (self.engine / "version.properties").write_bytes(self.version)
        (self.engine / "Example.class").write_bytes(b"compiled engine")
        (self.engine / "META-INF" / "openggf").mkdir(parents=True)
        (self.engine / "META-INF" / "openggf" / "LICENSE").write_text("licence text")
        (self.server / "ExampleServer.class").write_bytes(b"compiled server")
        (self.server / "net").mkdir()
        (self.server / "net" / "track-validation-profiles.json").write_text('{"profile":true}')
        self.dependencies = []
        for group in ("first", "second"):
            dep = self.checkout / "maven cache" / group / "same-name-1.0.jar"
            dep.parent.mkdir(parents=True)
            with zipfile.ZipFile(dep, "w") as jar:
                jar.writestr(group + ".class", group)
            self.dependencies.append(dep)
        # These inputs sit beside the sources/build, as on an operator's checkout.
        (self.checkout / "local-rom.gen").write_bytes(b"ROM must stay out")
        (self.checkout / "config.yaml").write_text("private local configuration")
        (self.checkout / ".env").write_text("TOKEN=private")
        (self.checkout / "server.key").write_text("private TLS key")

    def package(self):
        with contextlib.redirect_stdout(io.StringIO()), patch.object(build, "distribution_platform", return_value="linux/amd64"):
            build.write_distribution(self.engine, self.server, self.dependencies, self.dist)
        return json.loads((self.dist / "distribution.json").read_text())

    def test_preserves_original_build_identity_resources_and_dependency_closure(self):
        manifest = self.package()
        with zipfile.ZipFile(self.dist / "lib" / "openggf-engine.jar") as jar:
            self.assertEqual(self.version, jar.read("version.properties"))
            self.assertEqual(b"licence text", jar.read("META-INF/openggf/LICENSE"))
        with zipfile.ZipFile(self.dist / "lib" / "racing-server.jar") as jar:
            self.assertEqual(b'{"profile":true}', jar.read("net/track-validation-profiles.json"))
        packaged_deps = sorted((self.dist / "lib" / "deps").glob("*.jar"))
        self.assertEqual([p.read_bytes() for p in self.dependencies], [p.read_bytes() for p in packaged_deps])
        self.assertEqual(hashlib.sha256(self.version).hexdigest(), manifest["versionPropertiesSha256"])
        self.assertEqual("linux/amd64", manifest["buildPlatform"])
        self.assertNotIn(str(self.checkout), (self.dist / "distribution.json").read_text())

    def test_launcher_remains_usable_after_checkout_and_maven_cache_are_removed(self):
        self.package()
        moved = self.root / "moved installation"
        shutil.move(self.dist, moved)
        shutil.rmtree(self.checkout)
        java_home = self.root / "runtime JRE"
        (java_home / "bin").mkdir(parents=True)
        java = java_home / "bin" / "java"
        java.write_text(f"#!{sys.executable}\nimport json, os, sys\n"
                        "print(json.dumps({'args':sys.argv[1:], 'cwd':os.getcwd(), 'options':os.environ['JAVA_TOOL_OPTIONS']}))\n")
        java.chmod(0o755)
        env = dict(os.environ, JAVA_HOME=str(java_home), JAVA_TOOL_OPTIONS="-Xmx64m")
        arguments = [str(moved / "bin" / "racing-server"), "verifier", "--rom", "a ROM.gen", "--data", "data"]
        result = subprocess.run(arguments, cwd=self.root, env=env, text=True, capture_output=True, check=True)
        actual = json.loads(result.stdout)
        self.assertEqual(["--enable-native-access=ALL-UNNAMED", "-cp",
                          f"{moved}/lib/racing-server.jar:{moved}/lib/openggf-engine.jar:{moved}/lib/deps/*",
                          build.TOOLS["verifier"], "--rom", "a ROM.gen", "--data", "data"], actual["args"])
        self.assertEqual(str(self.root), actual["cwd"])
        self.assertEqual("-Xmx64m", actual["options"])

    def test_missing_identity_fails_instead_of_inventing_one(self):
        (self.engine / "version.properties").unlink()
        with self.assertRaisesRegex(ValueError, "Missing compiled build identity"):
            self.package()

    def test_compiled_tree_cannot_pull_files_through_symlinks(self):
        (self.engine / "private.key").symlink_to(self.checkout / "server.key")
        with self.assertRaisesRegex(ValueError, "must not contain symlinks"):
            self.package()

    def test_image_context_only_contains_manifest_artifacts_and_recipe(self):
        manifest = self.package()
        # Even an extra file placed into the distribution is not a runtime input.
        (self.dist / "operator-secret").write_text("must not enter build context")
        context = self.root / "docker context"
        images.prepare_context(self.dist, context)
        actual = {p.relative_to(context).as_posix() for p in context.rglob("*") if p.is_file()}
        self.assertEqual({"Dockerfile", ".dockerignore", "dist/distribution.json"}
                         | {"dist/" + p for p in manifest["files"]}, actual)
        self.assertEqual(0o755, (context / "dist" / "bin" / "racing-server").stat().st_mode & 0o777)

    def test_modified_artifact_fails_before_existing_context_is_replaced(self):
        self.package()
        context = self.root / "context"
        context.mkdir()
        (context / "previous-build").write_text("keep until inputs validate")
        (self.dist / "bin" / "racing-server").write_text("modified")
        with self.assertRaisesRegex(ValueError, "changed since packaging"):
            images.prepare_context(self.dist, context)
        self.assertTrue((context / "previous-build").exists())

    def test_context_modes_allow_non_root_runtime_with_restrictive_host_umask(self):
        self.package()
        context = self.root / "private context"
        previous_umask = os.umask(0o077)
        try:
            images.prepare_context(self.dist, context)
        finally:
            os.umask(previous_umask)
        for path in (context / "dist", *(context / "dist").rglob("*")):
            with self.subTest(path=path):
                expected = 0o755 if path.is_dir() or path.name == "racing-server" else 0o644
                self.assertEqual(expected, path.stat().st_mode & 0o777)

    def test_other_native_platform_is_rejected(self):
        manifest = self.package()
        manifest["buildPlatform"] = "darwin/arm64"
        (self.dist / "distribution.json").write_text(json.dumps(manifest))
        with self.assertRaisesRegex(ValueError, "requires a linux/amd64 distribution"):
            images.prepare_context(self.dist, self.root / "context")

    def test_manifest_cannot_include_local_config_or_escape_distribution(self):
        self.package()
        for path in ("../server.key", "/tmp/private.key", "config.yaml", "lib/deps/../../private.jar"):
            with self.subTest(path=path):
                self.assertFalse(images.allowed_artifact(path))

    def test_portable_launcher_reports_missing_or_unknown_service(self):
        self.package()
        for args in ([], ["unknown"]):
            with self.subTest(args=args):
                result = subprocess.run([str(self.dist / "bin" / "racing-server"), *args],
                                        text=True, capture_output=True)
                self.assertEqual(2, result.returncode)
                self.assertIn("usage:", result.stderr)


if __name__ == "__main__":
    unittest.main()
