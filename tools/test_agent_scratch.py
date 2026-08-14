#!/usr/bin/env python3
"""Standard-library regression tests for ``tools/agent-scratch``.

The suite never asks tempfile to choose its default directory: a forgotten
default would itself defeat this helper's purpose on hosts with a tmpfs /tmp.
"""

import contextlib
import datetime as dt
import importlib.machinery
import importlib.util
import io
import json
import os
import pathlib
import shutil
import stat
import subprocess
import sys
import tempfile
import threading
import time
import tomllib
import unittest
from unittest import mock


HELPER = pathlib.Path(__file__).with_name("agent-scratch")
CACHE = pathlib.Path.home() / ".cache" / "oggf-agent-scratch-tests"


def load_helper():
    loader = importlib.machinery.SourceFileLoader("agent_scratch", str(HELPER))
    spec = importlib.util.spec_from_loader(loader.name, loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


@contextlib.contextmanager
def environment(**values):
    original = {key: os.environ.get(key) for key in values}
    os.environ.update({key: value for key, value in values.items() if value is not None})
    for key, value in values.items():
        if value is None:
            os.environ.pop(key, None)
    try:
        yield
    finally:
        for key, value in original.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value


class AgentScratchTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        CACHE.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.chmod(CACHE, 0o700)
        cls.helper = load_helper()
        filesystem = cls.helper.filesystem_type(CACHE)
        if filesystem in {"tmpfs", "ramfs"}:
            raise unittest.SkipTest(f"test cache is unexpectedly {filesystem}")

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(dir=CACHE)
        self.root = pathlib.Path(self.temp.name) / "managed"
        self.env = {"OGGF_SCRATCH_ROOT": str(self.root)}

    def tearDown(self):
        self.temp.cleanup()

    def run_helper(self, argv):
        output, errors = io.StringIO(), io.StringIO()
        with environment(**self.env), contextlib.redirect_stdout(output), contextlib.redirect_stderr(errors):
            status = self.helper.main(argv)
        return status, output.getvalue(), errors.getvalue()

    def test_helper_exports_main_dispatcher(self):
        self.assertTrue(callable(self.helper.main))

    def test_mount_parser_selects_longest_accepted_mount_and_rejects_memory_filesystems(self):
        fixture = """25 20 0:21 / / rw - ext4 /dev/sda rw\n30 25 0:33 / /persistent rw - btrfs /dev/sdb rw\n31 25 0:44 / /tmp rw - tmpfs tmpfs rw\n32 25 0:45 / /ram rw - ramfs ramfs rw\n"""
        self.assertEqual("btrfs", self.helper.filesystem_type(pathlib.Path("/persistent/x"), fixture))
        self.assertEqual("tmpfs", self.helper.filesystem_type(pathlib.Path("/tmp/x"), fixture))
        self.assertEqual("ramfs", self.helper.filesystem_type(pathlib.Path("/ram/x"), fixture))

    def test_root_rejections_and_layout(self):
        for value in ("relative", "/tmp/nope", "/ok\nno"):
            with self.assertRaises(self.helper.ScratchError):
                self.helper._safe_root_path({"OGGF_SCRATCH_ROOT": value})
        root = self.helper.ensure_root(self.env)
        self.assertEqual(self.root, root)
        self.assertEqual(0o700, stat.S_IMODE(root.stat().st_mode))
        for child in ("claude", "codex/tmp", "openggf/tasks", "quarantine"):
            self.assertTrue((root / child).is_dir())

    def test_static_symlink_root_is_rejected(self):
        outside = pathlib.Path(self.temp.name) / "outside"
        outside.mkdir()
        link = pathlib.Path(self.temp.name) / "link"
        link.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(self.helper.ScratchError):
            self.helper.ensure_root({"OGGF_SCRATCH_ROOT": str(link)})

    def test_new_rejects_traversal_and_creates_private_unique_directories(self):
        for label in ("../escape", "a/b", ".", "bad\x00name"):
            status, _, error = self.run_helper(["new", label])
            self.assertEqual(2, status, error)
        status, output, error = self.run_helper(["new", "probe"])
        self.assertEqual(0, status, error)
        created = pathlib.Path(output.splitlines()[-1])
        self.assertTrue(created.is_dir())
        self.assertEqual(0o700, stat.S_IMODE(created.stat().st_mode))

    def test_symlink_swap_race_does_not_remove_outside_sentinel(self):
        root = self.helper.ensure_root(self.env)
        tasks = root / "openggf/tasks"
        old = tasks / "old"
        old.mkdir()
        (old / "inside").write_text("managed")
        old_time = time.time() - 9 * 86400
        os.utime(old, (old_time, old_time))
        outside = pathlib.Path(self.temp.name) / "outside"
        outside.mkdir()
        sentinel = outside / "sentinel"
        sentinel.write_text("do not touch")
        # Toggle an entry between a real directory and a symlink while prune is
        # traversing it.  Either lock observation is acceptable; following the
        # attacker-owned target is not.
        parked = tasks / "parked"
        stop = threading.Event()
        def swapper():
            while not stop.is_set():
                try:
                    if old.is_symlink():
                        old.unlink()
                        os.rename(parked, old)
                    elif old.exists():
                        os.rename(old, parked)
                        old.symlink_to(outside, target_is_directory=True)
                except FileNotFoundError:
                    pass
        thread = threading.Thread(target=swapper)
        thread.start()
        status, _, error = self.run_helper(["prune"])
        stop.set()
        thread.join()
        self.assertEqual(0, status, error)
        self.assertEqual("do not touch", sentinel.read_text())

    def test_same_parent_inode_replacement_is_not_staged_for_removal(self):
        root = self.helper.ensure_root(self.env)
        tasks = root / "openggf/tasks"
        original = tasks / "candidate"
        original.mkdir()
        expected = original.stat()
        replacement = tasks / "replacement"
        replacement.mkdir()
        replacement_identity = replacement.stat()
        os.replace(replacement, original)
        tasks_fd = os.open(tasks, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            self.assertIsNone(self.helper._stage_directory(tasks_fd, "candidate", expected))
        finally:
            os.close(tasks_fd)
        self.assertTrue(original.is_dir())
        self.assertEqual((replacement_identity.st_dev, replacement_identity.st_ino), (original.stat().st_dev, original.stat().st_ino))

    def test_final_directory_replacement_is_not_removed(self):
        root = self.helper.ensure_root(self.env)
        tasks = root / "openggf/tasks"
        candidate = tasks / "candidate"
        candidate.mkdir()
        expected = candidate.stat()
        replacement = tasks / "replacement"
        replacement.mkdir()
        replacement_identity = replacement.stat()
        os.replace(replacement, candidate)
        tasks_fd = os.open(tasks, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            self.assertFalse(self.helper._finalize_directory(tasks_fd, "candidate", expected))
        finally:
            os.close(tasks_fd)
        self.assertTrue(candidate.is_dir())
        self.assertEqual((replacement_identity.st_dev, replacement_identity.st_ino), (candidate.stat().st_dev, candidate.stat().st_ino))

    def test_concurrent_new_and_prune_share_lock(self):
        self.helper.ensure_root(self.env)
        child_env = {**os.environ, **self.env}
        commands = [[sys.executable, str(HELPER), "new", "parallel"] for _ in range(4)]
        commands += [[sys.executable, str(HELPER), "prune", "--dry-run"] for _ in range(4)]
        children = [subprocess.Popen(command, env=child_env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                    for command in commands]
        completed = [child.communicate() for child in children]
        self.assertEqual([0] * 8, [child.returncode for child in children])
        self.assertTrue(all(not error for _, error in completed))

    def test_status_keep_and_prune_are_bounded_and_safe(self):
        root = self.helper.ensure_root(self.env)
        task = root / "openggf/tasks/old-task"
        task.mkdir()
        (task / "payload").write_bytes(b"x" * 13)
        old = time.time() - 9 * 86400
        os.utime(task, (old, old))
        status, output, error = self.run_helper(["status"])
        self.assertEqual(0, status, error)
        self.assertIn("tasks_bytes=13", output)
        self.assertEqual(0, self.run_helper(["keep", str(task), "--until", (dt.date.today() + dt.timedelta(days=2)).isoformat()])[0])
        self.assertIn("protected-keep", self.run_helper(["prune", "--dry-run"])[1])
        self.assertTrue(task.exists())
        self.assertEqual(2, self.run_helper(["keep", str(task), "--until", (dt.date.today() + dt.timedelta(days=31)).isoformat()])[0])
        (task / self.helper.KEEP_FILE).unlink()
        os.utime(task, (old, old))
        self.assertIn("would-remove", self.run_helper(["prune", "--dry-run"])[1])
        self.assertEqual(0, self.run_helper(["prune"])[0])
        self.assertFalse(task.exists())

    def test_keep_accepts_only_direct_prune_candidates(self):
        root = self.helper.ensure_root(self.env)
        task = root / "openggf/tasks/direct"
        task.mkdir()
        nested = task / "nested"
        nested.mkdir()
        until = (dt.date.today() + dt.timedelta(days=1)).isoformat()
        self.assertEqual(2, self.run_helper(["keep", str(root / "openggf/tasks"), "--until", until])[0])
        self.assertEqual(2, self.run_helper(["keep", str(nested), "--until", until])[0])
        self.assertEqual(0, self.run_helper(["keep", str(task), "--until", until])[0])

    def test_marker_rejects_non_regular_files_without_blocking(self):
        root = self.helper.ensure_root(self.env)
        task = root / "openggf/tasks/marker"
        task.mkdir()
        marker = task / self.helper.KEEP_FILE
        os.mkfifo(marker)
        task_fd = os.open(task, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
        try:
            with self.assertRaises(self.helper.ScratchError):
                self.helper._marker_expiry(task_fd)
        finally:
            os.close(task_fd)

    def test_toml_editor_preserves_comments_unrelated_keys_and_is_idempotent(self):
        root = self.helper.ensure_root(self.env)
        config = pathlib.Path(self.temp.name) / "config.toml"
        config.write_text(f"# retained\n[shell_environment_policy.set]\nOTHER = 'yes' # keep\nTMPDIR = {str(root / 'codex' / 'tmp')!r} # retained managed comment\n\n[sandbox_workspace_write]\nwritable_roots = [\"/existing\"]\ncustom = 9\n")
        self.helper._update_codex(config, root)
        first = config.read_text()
        self.helper._update_codex(config, root)
        self.assertEqual(first, config.read_text())
        self.assertIn("# retained", first)
        self.assertIn("# retained managed comment", first)
        parsed = tomllib.loads(first)
        self.assertEqual("yes", parsed["shell_environment_policy"]["set"]["OTHER"])
        self.assertEqual(["/existing", str(root / "codex")], parsed["sandbox_workspace_write"]["writable_roots"])

    def test_claude_editor_sets_shell_temp_vars_idempotently_and_rejects_conflicts(self):
        root = self.helper.ensure_root(self.env)
        settings = pathlib.Path(self.temp.name) / "settings.json"
        settings.write_text('{"env": {"OTHER": "kept"}}\n')
        self.helper._update_claude(settings, root)
        first = settings.read_text()
        self.helper._update_claude(settings, root)
        self.assertEqual(first, settings.read_text())
        values = json.loads(first)["env"]
        self.assertEqual("kept", values["OTHER"])
        self.assertEqual(str(root), values["OGGF_SCRATCH_ROOT"])
        for key in ("CLAUDE_CODE_TMPDIR", "TMPDIR", "TMP", "TEMP"):
            self.assertEqual(str(root / "claude"), values[key])
        conflict = pathlib.Path(self.temp.name) / "conflict-settings.json"
        original = '{"env": {"TMP": "/wrong"}}\n'
        conflict.write_text(original)
        with self.assertRaises(self.helper.ScratchError):
            self.helper._update_claude(conflict, root)
        self.assertEqual(original, conflict.read_text())

    def test_toml_editor_preserves_hash_inside_quoted_managed_path(self):
        root = pathlib.Path(self.temp.name) / "managed#root"
        config = pathlib.Path(self.temp.name) / "hash.toml"
        tmpdir = root / "codex" / "tmp"
        config.write_text(f"[shell_environment_policy.set]\nTMPDIR = {str(tmpdir)!r} # retained comment\n")
        self.helper._update_codex(config, root)
        first = config.read_text()
        self.helper._update_codex(config, root)
        self.assertEqual(first, config.read_text())
        self.assertIn(str(tmpdir), first)
        self.assertIn("# retained comment", first)

    def test_toml_editor_preserves_managed_table_header_comments(self):
        root = self.helper.ensure_root(self.env)
        config = pathlib.Path(self.temp.name) / "header-comments.toml"
        config.write_text("[shell_environment_policy.set] # shell comment\nOTHER = \"yes\"\n\n[sandbox_workspace_write] # sandbox comment\ncustom = 1\n")
        self.helper._update_codex(config, root)
        first = config.read_text()
        self.helper._update_codex(config, root)
        self.assertEqual(first, config.read_text())
        self.assertIn("[shell_environment_policy.set] # shell comment", first)
        self.assertIn("[sandbox_workspace_write] # sandbox comment", first)

    def test_toml_editor_appends_to_multiline_writable_roots(self):
        root = self.helper.ensure_root(self.env)
        config = pathlib.Path(self.temp.name) / "multiline.toml"
        config.write_text("# head comment\n[sandbox_workspace_write]\nwritable_roots = [\n  \"/one\",\n  \"/two\",\n]\nother = \"preserved\"\n")
        self.helper._update_codex(config, root)
        updated = config.read_text()
        parsed = tomllib.loads(updated)
        self.assertEqual(["/one", "/two", str(root / "codex")], parsed["sandbox_workspace_write"]["writable_roots"])
        self.assertIn("# head comment", updated)
        self.assertIn('other = "preserved"', updated)

    def test_toml_editor_rejects_unsupported_and_conflicting_forms_without_writing(self):
        root = self.helper.ensure_root(self.env)
        for source in ("shell_environment_policy.set = { TMPDIR = \"/wrong\" }\n", "sandbox_workspace_write = { writable_roots = [] }\n", "[shell_environment_policy.set]\nTMPDIR = \"/wrong\"\n"):
            config = pathlib.Path(self.temp.name) / f"{abs(hash(source))}.toml"
            config.write_text(source)
            with self.assertRaises(self.helper.ScratchError):
                self.helper._update_codex(config, root)
            self.assertEqual(source, config.read_text())

    def test_systemd_execstart_quote_handles_space_quotes_backslash_and_controls(self):
        quoted = self.helper.systemd_exec_quote('/checkout with space/a"b\\c\n')
        self.assertEqual('"/checkout\\x20with\\x20space/a\\"b\\\\c\\n"', quoted)

    def test_systemd_quotes_environment_path_and_preserves_unicode_whitespace(self):
        home = pathlib.Path(self.temp.name) / "home with space"
        with mock.patch.object(self.helper.pathlib.Path, "home", return_value=home):
            _, service, _ = self.helper._unit_files(self.root)
        self.assertIn('EnvironmentFile="' + str(home).replace(" ", "\\x20") + '/.config/oggf-agent-scratch/environment"', service)
        self.assertIn("\u00a0", self.helper.systemd_exec_quote("a\u00a0b"))

    def test_environment_file_encoder_round_trips_significant_root_characters(self):
        raw = str(pathlib.Path(self.temp.name) / 'root # "quoted" \\ trailing ')
        self.assertEqual(pathlib.Path(raw), self.helper.ensure_root({"OGGF_SCRATCH_ROOT": raw}))
        encoded = self.helper.systemd_environment_value_encode(raw)
        self.assertEqual(raw, self.helper.systemd_environment_value_decode(encoded))
        text = self.helper._environment_file_text(pathlib.Path(raw))
        self.assertEqual(raw, self.helper._environment_file_root(text))

    def test_process_inspection_failure_is_unknown_and_protects_prune(self):
        with mock.patch.object(self.helper.subprocess, "run", side_effect=FileNotFoundError):
            self.assertEqual("unknown", self.helper._active("claude"))
            self.assertEqual("unknown", self.helper._active("codex"))
        root = self.helper.ensure_root(self.env)
        old = root / "claude/old"
        old.mkdir()
        then = time.time() - 9 * 86400
        os.utime(old, (then, then))
        with mock.patch.object(self.helper, "_active", return_value="unknown"):
            status, output, error = self.run_helper(["prune"])
        self.assertEqual(0, status, error)
        self.assertIn("protected-unknown", output)
        self.assertTrue(old.exists())

    def test_missing_systemd_tools_leaves_units_and_reports_activation_command(self):
        service = pathlib.Path(self.temp.name) / "service"
        timer = pathlib.Path(self.temp.name) / "timer"
        service.write_text("[Service]\nExecStart=/bin/true\n")
        timer.write_text("[Timer]\nOnCalendar=daily\n")
        with mock.patch.object(self.helper.subprocess, "run", side_effect=FileNotFoundError):
            result = self.helper._run_systemd_install(service, timer)
        self.assertIn("systemctl --user daemon-reload", result)
        self.assertTrue(service.exists())

    def test_verify_checks_unit_syntax_and_marks_missing_analyzer_unverified(self):
        root = self.helper.ensure_root(self.env)
        service = pathlib.Path(self.temp.name) / "service"
        timer = pathlib.Path(self.temp.name) / "timer"
        service.write_text("[Service]\nExecStart=/bin/true\n")
        timer.write_text("[Timer]\nOnCalendar=daily\n")
        with mock.patch.object(self.helper.subprocess, "run", side_effect=FileNotFoundError):
            self.assertEqual("unverified", self.helper._verify_unit_syntax(service, timer))
        with mock.patch.object(self.helper.subprocess, "run", return_value=type("Run", (), {"returncode": 1, "stderr": "bad unit", "stdout": ""})()):
            with self.assertRaises(self.helper.ScratchError):
                self.helper._verify_unit_syntax(service, timer)

    def test_verify_timer_requires_enabled_state_and_next_trigger(self):
        enabled = type("Run", (), {"returncode": 0, "stdout": "enabled\n", "stderr": ""})()
        trigger = type("Run", (), {"returncode": 0, "stdout": "Fri 2026-08-14 14:00:00 BST\n", "stderr": ""})()
        with mock.patch.object(self.helper.subprocess, "run", side_effect=[enabled, trigger]):
            self.assertEqual("verified", self.helper._verify_timer())
        unavailable = type("Run", (), {"returncode": 1, "stdout": "", "stderr": "Failed to connect to bus"})()
        with mock.patch.object(self.helper.subprocess, "run", return_value=unavailable):
            self.assertEqual("unverified", self.helper._verify_timer())

    def test_claude_timeout_and_launch_failure_are_unverified(self):
        root = self.helper.ensure_root(self.env)
        with mock.patch.object(self.helper.shutil, "which", return_value="claude"), \
             mock.patch.object(self.helper.subprocess, "run", side_effect=subprocess.TimeoutExpired("claude", 45)):
            self.assertEqual("unverified", self.helper._verify_claude_tmpdir(root))
        with mock.patch.object(self.helper.shutil, "which", return_value="claude"), \
             mock.patch.object(self.helper.subprocess, "run", side_effect=OSError):
            self.assertEqual("unverified", self.helper._verify_claude_tmpdir(root))

    def test_claude_success_requires_absolute_confined_tmpdir(self):
        root = self.helper.ensure_root(self.env)
        run = type("Run", (), {"returncode": 0, "stdout": "relative/path\n", "stderr": ""})()
        with mock.patch.object(self.helper.shutil, "which", return_value="claude"), \
             mock.patch.object(self.helper.subprocess, "run", return_value=run):
            with self.assertRaises(self.helper.ScratchError):
                self.helper._verify_claude_tmpdir(root)

    def test_path_does_not_touch_unrelated_sentinel(self):
        sentinel = pathlib.Path(self.temp.name) / "sentinel"
        sentinel.write_text("safe")
        status, output, error = self.run_helper(["path", "tasks"])
        self.assertEqual(0, status, error)
        self.assertTrue(output.strip().endswith("/openggf/tasks"))
        self.assertEqual("safe", sentinel.read_text())


if __name__ == "__main__":
    unittest.main(verbosity=2)
