#!/usr/bin/env python3
"""Standard-library regression tests for ``tools/agent-scratch``.

The suite never asks tempfile to choose its default directory: a forgotten
default would itself defeat this helper's purpose on hosts with a tmpfs /tmp.
"""

import argparse
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
        self.env = {"AGENT_SCRATCH_ROOT": str(self.root), "OGGF_SCRATCH_ROOT": None}

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
                self.helper._safe_root_path({"AGENT_SCRATCH_ROOT": value})
        with self.assertRaises(self.helper.ScratchError):
            self.helper._safe_root_path({"AGENT_SCRATCH_ROOT": "/generic", "OGGF_SCRATCH_ROOT": "/legacy"})
        self.assertEqual(pathlib.Path("/legacy"), self.helper._safe_root_path({"OGGF_SCRATCH_ROOT": "/legacy"}))
        root = self.helper.ensure_root(self.env)
        self.assertEqual(self.root, root)
        self.assertEqual(0o700, stat.S_IMODE(root.stat().st_mode))
        for child in ("claude", "codex/tmp", "codex/test-sessions", "tasks", "quarantine"):
            self.assertTrue((root / child).is_dir())

    def test_static_symlink_root_is_rejected(self):
        outside = pathlib.Path(self.temp.name) / "outside"
        outside.mkdir()
        link = pathlib.Path(self.temp.name) / "link"
        link.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(self.helper.ScratchError):
            self.helper.ensure_root({"AGENT_SCRATCH_ROOT": str(link)})

    def test_new_rejects_traversal_and_creates_private_unique_directories(self):
        for label in ("../escape", "a/b", ".", "bad\x00name"):
            status, _, error = self.run_helper(["new", label])
            self.assertEqual(2, status, error)
        status, output, error = self.run_helper(["new", "probe"])
        self.assertEqual(0, status, error)
        created = pathlib.Path(output.splitlines()[-1])
        self.assertTrue(created.is_dir())
        self.assertEqual(0o700, stat.S_IMODE(created.stat().st_mode))

    def test_reserve_test_session_returns_structured_private_allocation(self):
        root = self.root
        with environment(**self.env):
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.helper.cmd_reserve_test_session(argparse.Namespace(json=True))
        record = json.loads(output.getvalue())
        allocation = pathlib.Path(record["allocation_path"])
        self.assertEqual(1, record["schema_version"])
        self.assertEqual("MANAGED_CODEX_TEST_SESSIONS", record["storage_tier"])
        self.assertEqual(str(root.resolve()), record["managed_root"])
        self.assertEqual(str(allocation.resolve()), record["allocation_path"])
        self.assertEqual(root / "codex" / "test-sessions", allocation.parent)
        self.assertEqual(0o700, stat.S_IMODE(allocation.stat().st_mode))
        self.assertTrue(record["filesystem_device"])
        self.assertGreater(record["usable_bytes"], 0)
        self.assertIn(record["inode_count_status"], {"MEASURED", "UNAVAILABLE_DYNAMIC"})
        if record["inode_count_status"] == "MEASURED":
            self.assertGreaterEqual(record["usable_inodes"], 0)
        else:
            self.assertIsNone(record["usable_inodes"])
        self.assertRegex(record["retention_deadline"], r"^\d{4}-\d{2}-\d{2}T")
        self.assertTrue(record["helper_version"])

    def test_statvfs_reports_dynamic_inode_unavailability_separately_from_zero(self):
        unavailable = type("Statvfs", (), {
            "f_bavail": 4, "f_frsize": 1024, "f_blocks": 16,
            "f_files": 0, "f_favail": 0,
        })()
        measured_zero = type("Statvfs", (), {
            "f_bavail": 4, "f_frsize": 1024, "f_blocks": 16,
            "f_files": 32, "f_favail": 0,
        })()

        self.assertEqual({
            "usable_bytes": 4096,
            "total_bytes": 16384,
            "usable_inodes": None,
            "inode_count_status": "UNAVAILABLE_DYNAMIC",
        }, self.helper._statvfs_values(unavailable))
        self.assertEqual({
            "usable_bytes": 4096,
            "total_bytes": 16384,
            "usable_inodes": 0,
            "inode_count_status": "MEASURED",
        }, self.helper._statvfs_values(measured_zero))

    def test_reserve_test_session_rejects_a_recreated_allocation_name(self):
        root = self.helper.ensure_root(self.env)
        lane = root / "codex" / "test-sessions"

        def recreate_allocation(_allocation_fd):
            allocation, = [entry for entry in lane.iterdir()
                           if entry.name.startswith("session-")]
            os.rename(allocation, lane / "probed-allocation")
            allocation.mkdir(mode=self.helper.MODE)

        with environment(**self.env), \
             mock.patch.object(self.helper, "_probe_directory", side_effect=recreate_allocation), \
             self.assertRaises(self.helper.ScratchError):
            self.helper.cmd_reserve_test_session(argparse.Namespace(json=True))

    def test_reserve_test_session_rejects_a_symlinked_lane(self):
        root = self.helper.ensure_root(self.env)
        lane = root / "codex" / "test-sessions"
        outside = pathlib.Path(self.temp.name) / "outside"
        outside.mkdir()
        lane.rmdir()
        lane.symlink_to(outside, target_is_directory=True)

        with environment(**self.env), self.assertRaises(self.helper.ScratchError):
            self.helper.cmd_reserve_test_session(argparse.Namespace(json=True))

    def test_reservation_record_rejects_a_replaced_allocation_parent(self):
        root = self.helper.ensure_root(self.env)
        lane = root / "codex" / "test-sessions"
        allocation = lane / "session-replaced-parent"
        allocation.mkdir()
        parked = root / "codex" / "parked-test-sessions"
        os.rename(lane, parked)
        replacement = root / "codex" / "replacement-test-sessions"
        replacement.mkdir()
        os.rename(replacement, lane)

        with self.assertRaises(self.helper.ScratchError):
            self.helper._reservation_record(root, allocation)

    def _installed_configuration(self):
        """Install the real generated contract into an isolated home directory."""
        root = self.helper.ensure_root(self.env)
        home = pathlib.Path(self.temp.name) / "home"
        home.mkdir()
        with environment(**self.env), \
             mock.patch.object(self.helper.pathlib.Path, "home", return_value=home), \
             mock.patch.object(self.helper, "_legacy_migration_preflight", return_value="absent"), \
             mock.patch.object(self.helper, "_retire_legacy_units", return_value="absent"), \
             mock.patch.object(self.helper, "_run_systemd_install", return_value="timer active"):
            self.assertEqual(0, self.helper.cmd_install(argparse.Namespace()))
        return root, home

    def _verify_installed_configuration(self, root, home):
        with environment(**self.env), \
             mock.patch.object(self.helper.pathlib.Path, "home", return_value=home), \
             mock.patch.object(self.helper, "_legacy_migration_preflight", return_value="absent"), \
             mock.patch.object(self.helper, "_verify_unit_syntax", return_value="verified"), \
             mock.patch.object(self.helper, "_verify_timer", return_value="verified"), \
             mock.patch.object(self.helper, "_verify_legacy_timer", return_value="absent"), \
             mock.patch.object(self.helper, "_verify_claude_tmpdir", return_value="unverified"):
            return self.helper.cmd_verify(argparse.Namespace())

    def test_verify_rejects_missing_test_session_lane(self):
        root, home = self._installed_configuration()
        (root / "codex" / "test-sessions").rmdir()

        with self.assertRaisesRegex(self.helper.ScratchError, "test-session lane"):
            self._verify_installed_configuration(root, home)

    def test_verify_rejects_stale_installed_helper(self):
        root, home = self._installed_configuration()
        installed = self.helper._installed_helper_path(home)
        installed.write_bytes(installed.read_bytes() + b"\n# stale\n")

        with self.assertRaisesRegex(self.helper.ScratchError, "does not match"):
            self._verify_installed_configuration(root, home)

    def test_verify_reports_known_user_bus_diagnostics_after_static_checks(self):
        root, home = self._installed_configuration()
        for diagnostic in ("Failed to connect to bus", "Failed to connect to user scope bus"):
            unavailable = type("Run", (), {
                "returncode": 1, "stdout": "", "stderr": diagnostic,
            })()
            output = io.StringIO()
            with environment(**self.env), \
                 mock.patch.object(self.helper.pathlib.Path, "home", return_value=home), \
                 mock.patch.object(self.helper, "_legacy_migration_preflight", return_value="absent"), \
                 mock.patch.object(self.helper, "_verify_unit_syntax", return_value="verified"), \
                 mock.patch.object(self.helper, "_verify_legacy_timer", return_value="absent"), \
                 mock.patch.object(self.helper, "_verify_claude_tmpdir", return_value="unverified"), \
                 mock.patch.object(self.helper.subprocess, "run", return_value=unavailable), \
                 contextlib.redirect_stdout(output):
                self.assertEqual(0, self.helper.cmd_verify(argparse.Namespace()))
            self.assertIn("config=verified", output.getvalue())
            self.assertIn("systemd_timer=UNAVAILABLE_IN_SANDBOX", output.getvalue())

    def test_verify_bounds_the_optional_claude_probe(self):
        root, home = self._installed_configuration()
        timed_out = []

        def timeout_probe(command, **kwargs):
            timed_out.append(kwargs["timeout"])
            raise subprocess.TimeoutExpired(command, kwargs["timeout"])

        output = io.StringIO()
        started = time.monotonic()
        with environment(**self.env), \
             mock.patch.object(self.helper.pathlib.Path, "home", return_value=home), \
             mock.patch.object(self.helper, "_legacy_migration_preflight", return_value="absent"), \
             mock.patch.object(self.helper, "_verify_unit_syntax", return_value="verified"), \
             mock.patch.object(self.helper, "_verify_timer", return_value="verified"), \
             mock.patch.object(self.helper, "_verify_legacy_timer", return_value="absent"), \
             mock.patch.object(self.helper.shutil, "which", return_value="claude"), \
             mock.patch.object(self.helper.subprocess, "run", side_effect=timeout_probe), \
             contextlib.redirect_stdout(output):
            self.assertEqual(0, self.helper.cmd_verify(argparse.Namespace()))
        self.assertLess(time.monotonic() - started, 0.5)
        self.assertEqual([self.helper.CLAUDE_VERIFY_TIMEOUT_SECONDS], timed_out)
        self.assertLessEqual(self.helper.CLAUDE_VERIFY_TIMEOUT_SECONDS, 2)
        self.assertIn("claude=unverified", output.getvalue())

    def _session(self, name, state, *, pid=None, start=None, old=True):
        root = self.helper.ensure_root(self.env)
        session = root / "codex" / "test-sessions" / name
        session.mkdir()
        manifest = {"state": state}
        if pid is not None:
            manifest["pid"] = pid
            manifest["process_start_epoch_ms"] = start
        (session / "manifest.json").write_text(json.dumps(manifest) + "\n")
        if old:
            then = time.time() - (self.helper.TEST_SESSION_RETENTION_DAYS + 2) * 86400
            os.utime(session, (then, then))
        return root, session

    def test_prune_preserves_live_running_session(self):
        start = self.helper._process_start_epoch_ms(os.getpid())
        self.assertIsNotNone(start)
        _, session = self._session("live", "RUNNING", pid=os.getpid(), start=start)

        status, output, error = self.run_helper(["prune"])

        self.assertEqual(0, status, error)
        self.assertIn("protected-live test-sessions/live", output)
        self.assertTrue(session.exists())

    def test_prune_uses_live_owner_metadata_from_the_manifest_lease(self):
        start = self.helper._process_start_epoch_ms(os.getpid())
        self.assertIsNotNone(start)
        _, session = self._session("leased", "RUNNING", old=True)
        lease = pathlib.Path(self.temp.name) / "lease" / "lease.lock"
        lease.parent.mkdir()
        lease.write_text("lease\n")
        (lease.parent / "owner.json").write_text(json.dumps({
            "pid": os.getpid(), "process_start_epoch_ms": start
        }) + "\n")
        (session / "manifest.json").write_text(json.dumps({
            "state": "RUNNING", "lease_path": str(lease)
        }) + "\n")
        then = time.time() - (self.helper.TEST_SESSION_RETENTION_DAYS + 2) * 86400
        os.utime(session, (then, then))

        status, output, error = self.run_helper(["prune"])

        self.assertEqual(0, status, error)
        self.assertIn("protected-live test-sessions/leased", output)
        self.assertTrue(session.exists())

    def test_prune_moves_expired_stale_running_session_to_quarantine(self):
        root, session = self._session("stale", "RUNNING", pid=999999999,
                                      start=1)
        (session / "evidence").write_text("preserve me")
        then = time.time() - (self.helper.TEST_SESSION_RETENTION_DAYS + 2) * 86400
        os.utime(session, (then, then))

        status, output, error = self.run_helper(["prune"])

        self.assertEqual(0, status, error)
        self.assertIn("quarantined test-sessions/stale", output)
        self.assertFalse(session.exists())
        quarantined = list((root / "quarantine").iterdir())
        self.assertEqual(1, len(quarantined))
        self.assertEqual("preserve me", (quarantined[0] / "evidence").read_text())

    def test_prune_quarantine_starts_a_fresh_fourteen_day_retention_period(self):
        root, session = self._session("very-old", "RUNNING", pid=999999999,
                                      start=1)
        (session / "evidence").write_text("retain until quarantine expires")
        then = time.time() - 90 * 86400
        os.utime(session, (then, then))

        self.assertEqual(0, self.run_helper(["prune"])[0])
        quarantined, = (root / "quarantine").iterdir()
        self.assertTrue(quarantined.exists())

        status, output, error = self.run_helper(["prune"])

        self.assertEqual(0, status, error)
        self.assertNotIn(f"removed quarantine/{quarantined.name}", output)
        self.assertTrue(quarantined.exists())
        expired = time.time() - 15 * 86400
        os.utime(quarantined, (expired, expired))
        self.assertEqual(0, self.run_helper(["prune"])[0])
        self.assertFalse(quarantined.exists())

    def test_prune_removes_expired_terminal_session_unless_kept(self):
        root, removable = self._session("terminal", "PASSED")
        _, kept = self._session("kept", "PASSED")
        until = (dt.date.today() + dt.timedelta(days=1)).isoformat()
        self.assertEqual(0, self.run_helper(["keep", str(kept), "--until", until])[0])
        then = time.time() - (self.helper.TEST_SESSION_RETENTION_DAYS + 2) * 86400
        os.utime(kept, (then, then))

        status, output, error = self.run_helper(["prune"])

        self.assertEqual(0, status, error)
        self.assertIn("removed test-sessions/terminal", output)
        self.assertIn("protected-keep test-sessions/kept", output)
        self.assertFalse(removable.exists())
        self.assertTrue(kept.exists())
        self.assertTrue((root / "quarantine").is_dir())

    def test_symlink_swap_race_does_not_remove_outside_sentinel(self):
        root = self.helper.ensure_root(self.env)
        tasks = root / "tasks"
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
        tasks = root / "tasks"
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
        tasks = root / "tasks"
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
        child_env = {key: value for key, value in {**os.environ, **self.env}.items()
                     if value is not None}
        commands = [[sys.executable, str(HELPER), "new", "parallel"] for _ in range(4)]
        commands += [[sys.executable, str(HELPER), "prune", "--dry-run"] for _ in range(4)]
        children = [subprocess.Popen(command, env=child_env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                    for command in commands]
        completed = [child.communicate() for child in children]
        self.assertEqual([0] * 8, [child.returncode for child in children])
        self.assertTrue(all(not error for _, error in completed))

    def test_status_keep_and_prune_are_bounded_and_safe(self):
        root = self.helper.ensure_root(self.env)
        task = root / "tasks/old-task"
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
        task = root / "tasks/direct"
        task.mkdir()
        nested = task / "nested"
        nested.mkdir()
        until = (dt.date.today() + dt.timedelta(days=1)).isoformat()
        self.assertEqual(2, self.run_helper(["keep", str(root / "tasks"), "--until", until])[0])
        self.assertEqual(2, self.run_helper(["keep", str(nested), "--until", until])[0])
        self.assertEqual(0, self.run_helper(["keep", str(task), "--until", until])[0])

    def test_marker_rejects_non_regular_files_without_blocking(self):
        root = self.helper.ensure_root(self.env)
        task = root / "tasks/marker"
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
        self.assertEqual(str(root), values["AGENT_SCRATCH_ROOT"])
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
        self.assertIn('EnvironmentFile=' + str(home).replace(" ", "\\x20") + '/.config/agent-scratch/environment', service)
        self.assertNotIn('EnvironmentFile="', service)
        self.assertIn("\u00a0", self.helper.systemd_exec_quote("a\u00a0b"))

    def test_owned_legacy_units_are_disabled_and_removed_but_legacy_environment_is_inert(self):
        root = self.helper.ensure_root(self.env)
        home = pathlib.Path(self.temp.name) / "home"
        legacy_helper = home / "checkout" / "tools" / "agent-scratch"
        legacy_helper.parent.mkdir(parents=True)
        legacy_helper.write_text("#!/bin/sh\n")
        environment_text, service, timer = self.helper._legacy_unit_files(root, legacy_helper, home)
        environment_path, service_path, timer_path = self.helper._legacy_unit_paths(home)
        environment_path.parent.mkdir(parents=True)
        service_path.parent.mkdir(parents=True, exist_ok=True)
        environment_path.write_text(environment_text)
        service_path.write_text(service)
        timer_path.write_text(timer)
        calls = []

        def systemctl(command, **kwargs):
            calls.append(command)
            if command[-2:] == ["is-enabled", self.helper.LEGACY_TIMER_UNIT]:
                return type("Run", (), {"returncode": 1, "stdout": "disabled\n", "stderr": ""})()
            if command[-2:] == ["is-active", self.helper.LEGACY_TIMER_UNIT]:
                return type("Run", (), {"returncode": 3, "stdout": "inactive\n", "stderr": ""})()
            return type("Run", (), {"returncode": 0, "stdout": "", "stderr": ""})()

        with mock.patch.object(self.helper.subprocess, "run", side_effect=systemctl):
            self.assertEqual("retired", self.helper._retire_legacy_units(home, root))
        self.assertIn(["systemctl", "--user", "disable", "--now", self.helper.LEGACY_TIMER_UNIT], calls)
        self.assertFalse(service_path.exists())
        self.assertFalse(timer_path.exists())
        self.assertTrue(environment_path.exists())

    def test_legacy_timer_verification_rejects_an_active_or_enabled_timer(self):
        active = type("Run", (), {"returncode": 0, "stdout": "enabled\n", "stderr": ""})()
        with mock.patch.object(self.helper.subprocess, "run", return_value=active):
            with self.assertRaises(self.helper.ScratchError):
                self.helper._verify_legacy_timer()

    def test_unowned_legacy_units_fail_install_without_disabling_or_writing(self):
        root = self.helper.ensure_root(self.env)
        home = pathlib.Path(self.temp.name) / "home"
        home.mkdir()
        legacy_helper = home / "checkout" / "tools" / "agent-scratch"
        legacy_helper.parent.mkdir(parents=True)
        legacy_helper.write_text("#!/bin/sh\n")
        environment_text, service, timer = self.helper._legacy_unit_files(root, legacy_helper, home)
        environment_path, service_path, timer_path = self.helper._legacy_unit_paths(home)
        environment_path.parent.mkdir(parents=True)
        service_path.parent.mkdir(parents=True, exist_ok=True)
        environment_path.write_text(environment_text)
        altered_service = service + "# user-managed alteration\n"
        service_path.write_text(altered_service)
        timer_path.write_text(timer)
        calls = []

        def unexpected_systemd(command, **kwargs):
            calls.append(command)
            raise AssertionError(f"unexpected systemctl mutation: {command}")

        with environment(**self.env), mock.patch.object(self.helper.pathlib.Path, "home", return_value=home), \
             mock.patch.object(self.helper, "_legacy_timer_state", return_value="active"), \
             mock.patch.object(self.helper.subprocess, "run", side_effect=unexpected_systemd), \
             self.assertRaisesRegex(self.helper.ScratchError, "manual review"):
            self.helper.cmd_install(argparse.Namespace())
        self.assertEqual([], calls)
        self.assertEqual(altered_service, service_path.read_text())
        self.assertEqual(timer, timer_path.read_text())
        self.assertFalse((home / ".local" / "bin" / "agent-scratch").exists())
        self.assertFalse((home / ".config" / "agent-scratch" / "environment").exists())

    def test_systemd_analyzer_accepts_escaped_absolute_environment_file_path(self):
        if not shutil.which("systemd-analyze"):
            self.skipTest("systemd-analyze is unavailable")
        home = pathlib.Path(self.temp.name) / "home with space"
        unit_dir = pathlib.Path(self.temp.name) / "units"
        unit_dir.mkdir()
        with mock.patch.object(self.helper.pathlib.Path, "home", return_value=home):
            environment, service, timer = self.helper._unit_files(self.root)
        helper = self.helper._installed_helper_path(home)
        helper.parent.mkdir(parents=True)
        helper.write_text("#!/bin/sh\nexit 0\n")
        helper.chmod(0o755)
        environment_path = home / ".config" / "agent-scratch" / "environment"
        environment_path.parent.mkdir(parents=True)
        environment_path.write_text(environment)
        timer = timer.replace("agent-scratch-prune.service", "test.service")
        service_path = unit_dir / "test.service"
        timer_path = unit_dir / "test.timer"
        service_path.write_text(service)
        timer_path.write_text(timer)
        analyzer_env = {**os.environ, "HOME": str(self.temp.name), "XDG_CONFIG_HOME": str(pathlib.Path(self.temp.name) / "empty-config")}
        result = subprocess.run(["systemd-analyze", "--user", "verify", str(service_path), str(timer_path)], env=analyzer_env, text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertNotIn("EnvironmentFile= path is not absolute", result.stderr)

    def test_install_stable_helper_is_atomic_executable_and_used_by_service(self):
        home = pathlib.Path(self.temp.name) / "user home"
        source = pathlib.Path(self.temp.name) / "source-helper"
        source.write_text("#!/usr/bin/env python3\nprint('new helper')\n")
        source.chmod(0o755)
        target = self.helper._installed_helper_path(home)
        target.parent.mkdir(parents=True)
        target.write_text("old helper\n")
        target.chmod(0o755)
        installed = self.helper._install_stable_helper(source, home)
        self.assertEqual(target, installed)
        self.assertFalse(installed.is_symlink())
        self.assertEqual(source.read_bytes(), installed.read_bytes())
        self.assertEqual(0o755, stat.S_IMODE(installed.stat().st_mode))
        # A pre-existing shared user bin directory stays at its safe mode;
        # installation must not tighten permissions on unrelated tools.
        self.assertEqual(0o755, stat.S_IMODE(installed.parent.stat().st_mode))
        _, service, _ = self.helper._unit_files(self.root, installed)
        self.assertIn("ExecStart=" + self.helper.systemd_exec_quote(str(installed)) + " prune", service)
        self.assertNotIn(str(self.helper.repo_root()), service)
        self.assertEqual([], list(installed.parent.glob(".agent-scratch.*")))

    def test_install_stable_helper_retries_short_writes_and_preserves_existing_safe_bin_mode(self):
        home = pathlib.Path(self.temp.name) / "home"
        source = pathlib.Path(self.temp.name) / "source-helper"
        payload = b"#!/bin/sh\n" + b"x" * 65536
        source.write_bytes(payload)
        user_bin = home / ".local" / "bin"
        user_bin.mkdir(parents=True)
        user_bin.chmod(0o755)
        write = os.write

        def short_write(fd, data):
            return write(fd, data[:7]) if len(data) > 7 else write(fd, data)

        with mock.patch.object(self.helper.os, "write", side_effect=short_write):
            installed = self.helper._install_stable_helper(source, home)
        self.assertEqual(payload, installed.read_bytes())
        self.assertEqual(0o755, stat.S_IMODE(user_bin.stat().st_mode))

    def test_install_stable_helper_rejects_in_place_source_mutation_without_replacing_target(self):
        home = pathlib.Path(self.temp.name) / "home"
        source = pathlib.Path(self.temp.name) / "source-helper"
        source.write_bytes(b"a" * 65536)
        target = self.helper._installed_helper_path(home)
        target.parent.mkdir(parents=True)
        target.write_text("old helper\n")
        read = os.read
        changed = False

        def mutate_after_read(fd, size):
            nonlocal changed
            data = read(fd, size)
            if data and not changed:
                changed = True
                with source.open("r+b") as mutate:
                    mutate.seek(0)
                    mutate.write(b"b")
                    mutate.flush()
                    os.fsync(mutate.fileno())
            return data

        with mock.patch.object(self.helper.os, "read", side_effect=mutate_after_read):
            with self.assertRaises(self.helper.ScratchError):
                self.helper._install_stable_helper(source, home)
        self.assertEqual(b"old helper\n", target.read_bytes())

    def test_install_rejected_config_leaves_existing_stable_helper_unchanged(self):
        root = self.helper.ensure_root(self.env)
        home = pathlib.Path(self.temp.name) / "home"
        target = self.helper._installed_helper_path(home)
        target.parent.mkdir(parents=True)
        target.write_text("old helper\n")
        codex = home / ".codex" / "config.toml"
        codex.parent.mkdir(parents=True)
        codex.write_text("shell_environment_policy.set = { TMPDIR = \"/wrong\" }\n")
        with environment(**self.env), mock.patch.object(self.helper.pathlib.Path, "home", return_value=home):
            with self.assertRaises(self.helper.ScratchError):
                self.helper.cmd_install(argparse.Namespace())
        self.assertEqual(b"old helper\n", target.read_bytes())

    def test_environment_file_encoder_round_trips_significant_root_characters(self):
        raw = str(pathlib.Path(self.temp.name) / 'root # "quoted" \\ trailing ')
        self.assertEqual(pathlib.Path(raw), self.helper.ensure_root({"AGENT_SCRATCH_ROOT": raw}))
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
        with mock.patch.object(self.helper.subprocess, "run", return_value=type("Run", (), {"returncode": 0, "stderr": "warning: ignored directive", "stdout": ""})()):
            with self.assertRaises(self.helper.ScratchError):
                self.helper._verify_unit_syntax(service, timer)

    def test_unit_syntax_verification_isolated_from_unrelated_user_units(self):
        unit_dir = pathlib.Path(self.temp.name) / "units"
        unit_dir.mkdir()
        service = unit_dir / "service"
        timer = unit_dir / "timer"
        service.write_text("[Service]\nExecStart=/bin/true\n")
        timer.write_text("[Timer]\nOnCalendar=daily\n")
        captured = {}

        def analyzer(command, **kwargs):
            captured.update(kwargs)
            return type("Run", (), {"returncode": 0, "stderr": "", "stdout": ""})()

        with mock.patch.object(self.helper.subprocess, "run", side_effect=analyzer):
            self.assertEqual("verified", self.helper._verify_unit_syntax(service, timer))
        self.assertIn("env", captured)
        self.assertNotEqual(os.environ.get("HOME"), captured["env"]["HOME"])
        self.assertNotEqual(os.environ.get("XDG_CONFIG_HOME"), captured["env"]["XDG_CONFIG_HOME"])
        if os.environ.get("XDG_RUNTIME_DIR"):
            self.assertEqual(os.environ["XDG_RUNTIME_DIR"], captured["env"].get("XDG_RUNTIME_DIR"))

    def test_verify_timer_requires_enabled_state_and_next_trigger(self):
        enabled = type("Run", (), {"returncode": 0, "stdout": "enabled\n", "stderr": ""})()
        trigger = type("Run", (), {"returncode": 0, "stdout": "Fri 2026-08-14 14:00:00 BST\n", "stderr": ""})()
        with mock.patch.object(self.helper.subprocess, "run", side_effect=[enabled, trigger]):
            self.assertEqual("verified", self.helper._verify_timer())
        for diagnostic in ("Failed to connect to bus", "Failed to connect to user scope bus"):
            unavailable = type("Run", (), {"returncode": 1, "stdout": "", "stderr": diagnostic})()
            with mock.patch.object(self.helper.subprocess, "run", return_value=unavailable):
                self.assertEqual("unavailable_in_sandbox", self.helper._verify_timer())

    def test_legacy_timer_state_requires_explicit_inactive_or_known_sandbox_status(self):
        disabled = type("Run", (), {"returncode": 1, "stdout": "disabled\n", "stderr": ""})()
        inactive = type("Run", (), {"returncode": 3, "stdout": "inactive\n", "stderr": ""})()
        with mock.patch.object(self.helper.subprocess, "run", side_effect=[disabled, inactive]):
            self.assertEqual("inactive", self.helper._legacy_timer_state())
        for diagnostic in ("Failed to connect to bus", "Failed to connect to user scope bus"):
            unavailable = type("Run", (), {"returncode": 1, "stdout": "", "stderr": diagnostic})()
            with mock.patch.object(self.helper.subprocess, "run", side_effect=[unavailable, unavailable]):
                self.assertEqual("unavailable_in_sandbox", self.helper._legacy_timer_state())
        unexpected = type("Run", (), {"returncode": 1, "stdout": "", "stderr": "permission denied"})()
        with mock.patch.object(self.helper.subprocess, "run", side_effect=[unexpected, unexpected]), \
             self.assertRaisesRegex(self.helper.ScratchError, "cannot inspect legacy cleanup timer"):
            self.helper._legacy_timer_state()

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

    def test_claude_probe_uses_fresh_allowed_bash_and_exact_stdout(self):
        root = self.helper.ensure_root(self.env)
        run = type("Run", (), {"returncode": 0, "stdout": str(root / "claude") + "\n", "stderr": ""})()
        with mock.patch.object(self.helper.shutil, "which", return_value="claude"), \
             mock.patch.object(self.helper.subprocess, "run", return_value=run) as invoke:
            self.assertEqual("verified", self.helper._verify_claude_tmpdir(root))
        command = invoke.call_args.args[0]
        self.assertIn("--allowedTools", command)
        self.assertEqual("Bash(printf *)", command[command.index("--allowedTools") + 1])
        self.assertIn("--permission-mode", command)
        self.assertEqual("auto", command[command.index("--permission-mode") + 1])
        self.assertNotIn("bypassPermissions", command)
        self.assertIn("--no-session-persistence", command)
        prompt = command[-1]
        self.assertIn("printf '%s' \"${TMPDIR-UNSET}\"", prompt)
        self.assertIn("exactly the command's stdout", prompt)
        prose = type("Run", (), {"returncode": 0, "stdout": f"`{root / 'claude'}`\n", "stderr": ""})()
        with mock.patch.object(self.helper.shutil, "which", return_value="claude"), \
             mock.patch.object(self.helper.subprocess, "run", return_value=prose):
            with self.assertRaises(self.helper.ScratchError):
                self.helper._verify_claude_tmpdir(root)

    def test_path_does_not_touch_unrelated_sentinel(self):
        sentinel = pathlib.Path(self.temp.name) / "sentinel"
        sentinel.write_text("safe")
        status, output, error = self.run_helper(["path", "tasks"])
        self.assertEqual(0, status, error)
        self.assertTrue(output.strip().endswith("/tasks"))
        self.assertEqual("safe", sentinel.read_text())


if __name__ == "__main__":
    unittest.main(verbosity=2)
