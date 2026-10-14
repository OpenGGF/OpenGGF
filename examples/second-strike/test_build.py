"""Bounded launcher/publisher safety checks; fake commands never start the engine or Maven."""
import contextlib
import importlib.util
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("second_strike_build", Path(__file__).with_name("build.py"))
build = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build)


class BuildSafety(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.project = self.root / "examples/second-strike"
        (self.project / "src/main/resources").mkdir(parents=True)
        (self.root / "target").mkdir()
        (self.root / "target/second-strike-classpath.txt").write_text("fake-dependency.jar")
        self.output = self.root / "target/second-strike"
        self.output.mkdir()
        self.jar = self.output / "second-strike.jar"
        self.jar.write_bytes(b"previous valid package")
        self.commands = []
        for name, value in [("ROOT", self.root), ("PROJECT", self.project), ("OUTPUT", self.output)]:
            change = patch.object(build, name, value)
            change.start(); self.addCleanup(change.stop)
        change = patch.object(build, "worktree_build_slot", lambda _: contextlib.nullcontext())
        change.start(); self.addCleanup(change.stop)

    def fake_command(self, command, **kwargs):
        self.commands.append(command)
        if "com.openggf.tools.modsdk.GgfModCli" in command:
            self.assertEqual(b"previous valid package", self.jar.read_bytes(), "publisher sees prior valid output")
            self.assertEqual(["--warnings", "error"], command[-2:])
            raise subprocess.CalledProcessError(1, command)

    def test_strict_publication_failure_preserves_last_valid_jar(self):
        with patch.object(build.sys, "argv", ["build.py"]), patch.object(build.subprocess, "run", self.fake_command):
            with self.assertRaises(subprocess.CalledProcessError):
                build.main()
        self.assertEqual(b"previous valid package", self.jar.read_bytes())
        self.assertEqual(3, len(self.commands))

    def test_existing_profile_rejects_explicit_character_or_team_before_build(self):
        profile = self.root / "player"
        profile.mkdir(); config = profile / "config.yaml"; config.write_text("user: settings\n")
        saves = profile / "saves"; saves.mkdir(); save = saves / "native.bin"; save.write_bytes(b"user save")
        rom = self.root / "owned-input.gen"; rom.write_bytes(b"fake path check only")
        for flags in [["--character", "tails"], ["--solo"]]:
            args = ["build.py", "--run", "--rom", str(rom), "--profile", str(profile), *flags]
            with patch.object(build.sys, "argv", args), patch.object(build.subprocess, "run") as command:
                with self.assertRaises(SystemExit) as error, contextlib.redirect_stderr(__import__("io").StringIO()):
                    build.main()
                self.assertEqual(2, error.exception.code); command.assert_not_called()
            self.assertEqual("user: settings\n", config.read_text()); self.assertEqual(b"user save", save.read_bytes())

    def test_existing_profile_without_overrides_is_preserved_and_used(self):
        profile = self.root / "player"; profile.mkdir()
        config = profile / "config.yaml"; config.write_text("user: settings\n")
        rom = self.root / "owned-input.gen"; rom.write_bytes(b"fake path check only")
        def succeed(command, **kwargs):
            self.commands.append((command, kwargs))
            if "com.openggf.tools.modsdk.GgfModCli" in command:
                Path(command[command.index("--out") + 1]).write_bytes(b"validated replacement")
        args = ["build.py", "--run", "--profile", str(profile)]
        with patch.object(build.sys, "argv", args), patch.object(build.subprocess, "run", succeed):
            build.main()
        self.assertEqual("user: settings\n", config.read_text())
        command, options = self.commands[-1]
        self.assertEqual(profile, options["cwd"])
        self.assertIn("-Dopenggf.saveRoot=" + str(profile / "saves"), command)

    def test_repeat_build_uses_fresh_candidate_then_replaces_last_good_output(self):
        def publish(command, **kwargs):
            if "com.openggf.tools.modsdk.GgfModCli" in command:
                candidate = Path(command[command.index("--out") + 1])
                self.assertFalse(candidate.exists(), "native publisher rejects existing outputs")
                self.assertEqual(b"previous valid package", self.jar.read_bytes())
                candidate.write_bytes(b"validated replacement")
        with patch.object(build.sys, "argv", ["build.py"]), patch.object(build.subprocess, "run", publish):
            build.main()
        self.assertEqual(b"validated replacement", self.jar.read_bytes())

    @unittest.skipUnless(__import__("os").name == "posix", "OS lease inheritance is POSIX-only")
    def test_competing_writer_cannot_enter_compile_to_author_handoff(self):
        import maven_queue
        real_run = subprocess.run
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        marker = self.root / "writer-entered"
        child = None
        def command(argv, **kwargs):
            nonlocal child
            if argv[0] == "git": return real_run(argv, **kwargs)
            if "compile" in argv:
                script = ("import sys; from pathlib import Path; sys.path.insert(0, " + repr(str(Path(maven_queue.__file__).parent))
                          + "); from maven_queue import worktree_build_slot; "
                          + "\nwith worktree_build_slot(Path(" + repr(str(self.root)) + ")): Path(" + repr(str(marker)) + ").write_text('entered')")
                child = subprocess.Popen([build.sys.executable, "-c", script], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                time.sleep(.4)
            self.assertFalse(marker.exists(), "competing writer must stay blocked through Maven, javac and publication")
            if "com.openggf.tools.modsdk.GgfModCli" in argv:
                Path(argv[argv.index("--out") + 1]).write_bytes(b"validated replacement")
        try:
            with patch.object(build, "worktree_build_slot", maven_queue.worktree_build_slot), \
                    patch.object(build.sys, "argv", ["build.py"]), patch.object(build.subprocess, "run", command):
                build.main()
        finally:
            if child is not None: child.wait(timeout=5)
        self.assertTrue(marker.exists(), "the writer enters after the coherent builder releases its lease")


if __name__ == "__main__":
    unittest.main()
