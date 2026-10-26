#!/usr/bin/env python3
"""Synthetic source/report safety tests; never launch Maven or read game media.

Run: python3 -B -m unittest discover -s tools/soniccd -p test_integration_packet.py
Origin: 2026-10-26 private Sonic CD packet integration aid.
"""
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from contextlib import contextmanager, redirect_stderr, redirect_stdout
from datetime import datetime, timezone
from unittest import mock
import xml.etree.ElementTree as ET

import integration_packet as packet

SCRIPT = Path(__file__).with_name("integration_packet.py")


class PacketFixture(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.root = self.base / "worktree"
        self.root.mkdir()
        self.git("init", "-q")
        self.git("-c", "user.name=Packet Test", "-c", "user.email=test@example.invalid",
                 "commit", "--allow-empty", "-qm", "synthetic base")
        self.head = self.git("rev-parse", "HEAD").strip()
        (self.root / "src").mkdir()
        self.source = self.base / "odd-worker-mirror.java"
        self.source.write_bytes(b"package example;\n")
        self.source.chmod(0o444)
        self.destination = self.root / "src/Canonical.java"
        self.destination.write_bytes(b"old source\n")
        self.entry = dict(source=str(self.source), sha256=self.sha(self.source.read_bytes()),
                          bytes=self.source.stat().st_size, mode="0444", utf8=True, lf=True,
                          destination="src/Canonical.java",
                          expected={"sha256": self.sha(self.destination.read_bytes())})
        self.manifest = self.base / "packet.json"
        self.write_manifest()

    @staticmethod
    def sha(data):
        return hashlib.sha256(data).hexdigest()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True,
                                       stderr=subprocess.DEVNULL)

    def write_manifest(self, entries=None, **extra):
        self.manifest.write_text(json.dumps(dict(version=1, worktree_head=self.head,
                                                files=entries or [self.entry], **extra)))

    def cli(self, *args):
        return subprocess.run([sys.executable, "-B", str(SCRIPT), *map(str, args)],
                              capture_output=True, text=True)

    def packet(self, *args):
        return self.cli("packet", "--worktree", self.root, "--manifest", self.manifest, *args)

class PacketCliTests(PacketFixture):
    def test_explicit_odd_mirror_mapping_is_read_only_by_default(self):
        result = self.packet()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertEqual(report["files"][0]["destination"], str(self.destination))
        self.assertEqual(report["files"][0]["current"]["sha256"], self.entry["expected"]["sha256"])
        self.assertEqual(self.destination.read_bytes(), b"old source\n")
        self.assertEqual(self.source.stat().st_mode & 0o7777, 0o444)

    def rejected(self, text=None, *args):
        result = self.packet(*args)
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        if text:
            self.assertIn(text, result.stderr)
        self.assertEqual(self.destination.read_bytes(), b"old source\n")
        return result

    def test_destination_traversal_absolute_metadata_and_lexical_aliases_rejected(self):
        for value in ("../escape.java", str(self.base / "escape.java"), "src/../escape.java",
                      "src//Canonical.java", "src/./Canonical.java", "src\\Canonical.java",
                      ".git/config", "src/.git/config", "C:/escape.java"):
            with self.subTest(value=value):
                self.entry["destination"] = value
                self.write_manifest()
                self.rejected()

    def test_source_must_be_external_absolute_and_not_destination(self):
        for value in ("relative.java", str(self.destination)):
            with self.subTest(value=value):
                self.entry["source"] = value
                self.write_manifest()
                self.rejected()

    def test_source_file_and_parent_symlinks_rejected(self):
        alias = self.base / "source-alias.java"
        alias.symlink_to(self.source)
        link = self.base / "directory-link"
        external = self.base / "external"
        external.mkdir()
        link.symlink_to(external, target_is_directory=True)
        (external / "source.java").write_bytes(self.source.read_bytes())
        for value in (alias, link / "source.java"):
            with self.subTest(value=value):
                self.entry["source"] = str(value)
                self.write_manifest()
                self.rejected("symlinks")

    def test_symlink_loop_is_rejected_without_traceback(self):
        loop = self.base / "loop"
        loop.symlink_to(loop)
        self.entry["source"] = str(loop)
        self.write_manifest()
        result = self.rejected()
        self.assertNotIn("Traceback", result.stderr)

    def test_destination_file_symlink_even_within_tree_rejected(self):
        alias = self.root / "src/Alias.java"
        alias.symlink_to(self.destination)
        self.entry["destination"] = "src/Alias.java"
        self.write_manifest()
        self.rejected("symlinks")

    def test_destination_parent_symlink_escape_rejected(self):
        (self.root / "linked").symlink_to(self.base, target_is_directory=True)
        self.entry["destination"] = "linked/escape.java"
        self.entry["expected"] = {"absent": True}
        self.write_manifest()
        self.rejected("symlinks")
        self.assertFalse((self.base / "escape.java").exists())

    def test_source_destination_hardlink_alias_rejected(self):
        alias = self.base / "hardlink.java"
        os.link(self.destination, alias)
        alias.chmod(0o444)
        self.entry.update(source=str(alias), sha256=self.sha(alias.read_bytes()), bytes=alias.stat().st_size)
        self.write_manifest()
        self.rejected("hardlink alias")

    def test_cross_entry_hardlink_alias_rejected(self):
        target = self.root / "src/Other.java"
        os.link(self.source, target)
        second = dict(self.entry, destination="src/Other.java", expected={"sha256": self.entry["sha256"]})
        self.write_manifest([self.entry, second])
        self.rejected("hardlink alias")

    def test_destination_hardlinks_to_each_other_rejected(self):
        os.link(self.destination, self.root / "src/Other.java")
        self.write_manifest([self.entry, dict(self.entry, destination="src/Other.java")])
        self.rejected("hardlink alias")

    def test_duplicate_destination_and_unknown_manifest_data_rejected(self):
        self.write_manifest([self.entry, self.entry])
        self.rejected("Duplicate/conflicting")
        self.write_manifest(extra="unreviewed")
        self.rejected("exactly keys")
        self.write_manifest()
        self.manifest.write_text(self.manifest.read_text().replace('"version": 1', '"version": 1, "version": 1'))
        self.rejected("Duplicate JSON key")

    def test_malformed_hash_length_mode_flags_and_unknown_entry_data_rejected(self):
        for change in ({"sha256": "x"*64}, {"sha256": 42}, {"bytes": -1}, {"bytes": True},
                       {"mode": "0644"}, {"utf8": 1}, {"lf": False}, {"unknown": "value"},
                       {"expected": {}}, {"expected": {"absent": 1}}, {"expected": {"sha256": "bad"}}):
            with self.subTest(change=change):
                self.write_manifest([dict(self.entry, **change)])
                self.rejected()

    def test_changed_source_digest_length_and_nonfrozen_source_rejected(self):
        for change in ({"sha256": "0"*64}, {"bytes": 1}):
            with self.subTest(change=change):
                self.write_manifest([dict(self.entry, **change)])
                self.rejected("Source current=")
        self.source.chmod(0o644)
        self.write_manifest()
        self.rejected("not frozen")

    def test_crlf_missing_final_lf_nul_and_non_utf8_rejected(self):
        for data in (b"bad\r\n", b"no final LF", b"binary\0\n", b"\xff\n"):
            with self.subTest(data=data):
                self.source.chmod(0o644)
                self.source.write_bytes(data)
                self.source.chmod(0o444)
                self.entry.update(sha256=self.sha(data), bytes=len(data))
                self.write_manifest()
                self.rejected()

    def test_baseline_absence_or_changed_pin_rejected_with_current_and_expected(self):
        for expected in ({"sha256": "0"*64}, {"absent": True}):
            with self.subTest(expected=expected):
                self.write_manifest([dict(self.entry, expected=expected)])
                result = self.rejected("Baseline changed")
                self.assertIn("current=", result.stderr)
                self.assertIn("expected=", result.stderr)

    def test_missing_destination_requires_explicit_absent(self):
        self.entry["destination"] = "src/New.java"
        self.write_manifest()
        self.rejected("Baseline changed")
        self.entry["expected"] = {"absent": True}
        self.write_manifest()
        self.assertEqual(self.packet().returncode, 0)
        self.assertFalse((self.root / "src/New.java").exists())

    def test_missing_parent_is_not_created_implicitly(self):
        self.entry.update(destination="unreviewed/New.java", expected={"absent": True})
        self.write_manifest()
        self.rejected("directories separately", "--apply", "--maven-terminal")
        self.assertFalse((self.root / "unreviewed").exists())

    def test_wrong_worktree_head_or_owner_subdirectory_rejected(self):
        document = json.loads(self.manifest.read_text())
        document["worktree_head"] = "0"*40
        self.manifest.write_text(json.dumps(document))
        self.rejected("HEAD current=")
        result = self.cli("packet", "--worktree", self.root / "src", "--manifest", self.manifest)
        self.assertEqual(result.returncode, 2)
        self.assertIn("exact worktree root", result.stderr)

    def test_entire_packet_preflight_prevents_any_write(self):
        self.write_manifest([self.entry, dict(self.entry, destination="src/New.java", expected={"sha256": "0"*64})])
        self.rejected("no files written", "--apply", "--maven-terminal")
        self.assertFalse((self.root / "src/New.java").exists())

    def test_apply_requires_explicit_terminal_declaration(self):
        self.rejected("--maven-terminal", "--apply")

    def test_apply_uses_build_lock_creates_644_with_fresh_mtime_and_preserves_originals(self):
        os.utime(self.source, (1, 1))
        source_before = self.source.stat()
        before = time.time_ns()
        with redirect_stdout(io.StringIO()), mock.patch.object(packet, "worktree_build_slot", wraps=packet.worktree_build_slot) as lock:
            result = packet.apply_packet(self.root, packet.read_manifest(self.manifest), maven_terminal=True)
        lock.assert_called_once_with(self.root)
        self.assertTrue(result["applied"])
        self.assertEqual(self.destination.read_bytes(), self.source.read_bytes())
        self.assertEqual(self.destination.stat().st_mode & 0o7777, 0o644)
        self.assertGreaterEqual(self.destination.stat().st_mtime_ns, before)
        self.assertEqual(self.source.stat().st_mode & 0o7777, 0o444)
        self.assertEqual(self.source.stat().st_mtime_ns, source_before.st_mtime_ns)
        self.assertEqual(self.git("status", "--short").strip(), "?? src/")
        self.assertEqual(self.git("rev-parse", "HEAD").strip(), self.head)

    def test_apply_to_absent_destination_and_stdout_is_json(self):
        self.entry.update(destination="src/New.java", expected={"absent": True})
        self.write_manifest()
        result = self.packet("--apply", "--maven-terminal")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertTrue(json.loads(result.stdout)["applied"])
        self.assertEqual((self.root / "src/New.java").read_bytes(), self.source.read_bytes())

    def test_revalidates_after_waiting_for_build_lock(self):
        @contextmanager
        def changed_during_wait(root):
            self.destination.write_bytes(b"concurrent data\n")
            yield
        with mock.patch.object(packet, "worktree_build_slot", changed_during_wait):
            with self.assertRaisesRegex(packet.PacketError, "no files written"):
                packet.apply_packet(self.root, packet.read_manifest(self.manifest), maven_terminal=True)
        self.assertEqual(self.destination.read_bytes(), b"concurrent data\n")

    def test_partial_io_failure_reports_completed_and_cleans_only_owned_temps(self):
        second = self.root / "src/Second.java"
        second.write_bytes(b"old source\n")
        unrelated = self.root / "src/.integration-packet-user-file"
        unrelated.write_bytes(b"preserve\n")
        self.write_manifest([self.entry, dict(self.entry, destination="src/Second.java")])
        real = os.replace

        def failing_replace(source, destination, **kwargs):
            if destination == "Second.java":
                raise OSError("synthetic disk failure")
            return real(source, destination, **kwargs)

        with redirect_stdout(io.StringIO()), mock.patch.object(packet.os, "replace", failing_replace):
            with self.assertRaises(packet.PacketError) as caught:
                packet.apply_packet(self.root, packet.read_manifest(self.manifest), maven_terminal=True)
        self.assertTrue(caught.exception.report["partial"])
        self.assertEqual(caught.exception.report["completed"], [str(self.destination)])
        self.assertEqual(caught.exception.report["remaining"], [str(second)])
        self.assertEqual(self.destination.read_bytes(), self.source.read_bytes())
        self.assertEqual(second.read_bytes(), b"old source\n")
        self.assertEqual(list((self.root / "src").glob(".integration-packet-*")), [unrelated])

    def test_cleanup_failure_reports_owned_path_and_original_io_error(self):
        with redirect_stdout(io.StringIO()), \
             mock.patch.object(packet.os, "replace", side_effect=OSError("synthetic rename failure")), \
             mock.patch.object(packet.os, "unlink", side_effect=OSError("synthetic cleanup failure")):
            with self.assertRaises(packet.PacketError) as caught:
                packet.apply_packet(self.root, packet.read_manifest(self.manifest), maven_terminal=True)
        message = str(caught.exception)
        self.assertIn("Owned temporary cleanup failed", message)
        self.assertIn(str(self.root / "src/.integration-packet-"), message)
        self.assertIn("synthetic rename failure", message)
        self.assertFalse(caught.exception.report["partial"])
        self.assertEqual(self.destination.read_bytes(), b"old source\n")
        leftovers = list((self.root / "src").glob(".integration-packet-*"))
        self.assertEqual(len(leftovers), 1)
        leftovers[0].unlink()

    def test_cli_reports_exit_three_for_partial_import(self):
        second = self.root / "src/Second.java"
        second.write_bytes(b"old source\n")
        self.write_manifest([self.entry, dict(self.entry, destination="src/Second.java")])
        real = os.replace

        def failing(source, destination, **kwargs):
            if destination == "Second.java":
                raise OSError("synthetic partial import")
            return real(source, destination, **kwargs)

        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err), mock.patch.object(packet.os, "replace", failing):
            code = packet.main(["packet", "--worktree", str(self.root), "--manifest", str(self.manifest),
                                "--apply", "--maven-terminal"])
        self.assertEqual(code, 3)
        self.assertEqual(out.getvalue(), "")
        self.assertIn('"partial": true', err.getvalue())

    def test_destination_parent_swap_before_write_cannot_escape(self):
        foreign = self.base / "foreign"
        foreign.mkdir()
        outside = foreign / "Canonical.java"
        outside.write_bytes(b"foreign data\n")
        original = self.root / "src-original"
        real = packet.replace_one

        def swapped(root, source, current):
            (root / "src").rename(original)
            (root / "src").symlink_to(foreign, target_is_directory=True)
            real(root, source, current)

        with redirect_stdout(io.StringIO()), mock.patch.object(packet, "replace_one", swapped):
            with self.assertRaises(packet.PacketError):
                packet.apply_packet(self.root, packet.read_manifest(self.manifest), maven_terminal=True)
        self.assertEqual(outside.read_bytes(), b"foreign data\n")
        self.assertEqual((original / "Canonical.java").read_bytes(), b"old source\n")

    def test_partial_concurrent_change_is_not_rolled_back(self):
        second = self.root / "src/Second.java"
        second.write_bytes(b"old source\n")
        self.write_manifest([self.entry, dict(self.entry, destination="src/Second.java")])
        real = packet.replace_one

        def concurrent(root, source, current):
            if current.path == second:
                self.destination.write_bytes(b"another editor owns this\n")
                second.write_bytes(b"concurrent data\n")
            real(root, source, current)

        with redirect_stdout(io.StringIO()), mock.patch.object(packet, "replace_one", concurrent):
            with self.assertRaises(packet.PacketError) as caught:
                packet.apply_packet(self.root, packet.read_manifest(self.manifest), maven_terminal=True)
        self.assertTrue(caught.exception.report["partial"])
        self.assertEqual(self.destination.read_bytes(), b"another editor owns this\n")
        self.assertEqual(second.read_bytes(), b"concurrent data\n")
        self.assertEqual(list((self.root / "src").glob(".integration-packet-*")), [])


class JunitCliTests(PacketFixture):
    def setUp(self):
        super().setUp()
        self.reports = self.root / "target/surefire-reports"
        self.reports.mkdir(parents=True)
        self.started = time.time() - 2
        self.since = datetime.fromtimestamp(self.started, timezone.utc).isoformat()

    def report(self, classname="example.TestOne", outcomes=("passed",), properties=None):
        props = {"java.version": "25.0.1", "user.dir": str(self.root),
                 "soniccd.media.root": "/external/private-media", "openggf.headless.gl": "surfaceless-egl",
                 "API_TOKEN": "DO-NOT-PRINT-SECRET"}
        if properties:
            props.update(properties)
        suite = ET.Element("testsuite", name=classname, tests=str(len(outcomes)),
                           failures=str(outcomes.count("failure")), errors=str(outcomes.count("error")),
                           skipped=str(outcomes.count("skipped")))
        group = ET.SubElement(suite, "properties")
        for key, value in props.items():
            if value is not None:
                ET.SubElement(group, "property", name=key, value=value)
        for index, status in enumerate(outcomes):
            case = ET.SubElement(suite, "testcase", classname=classname, name=f"case({index})")
            if status != "passed":
                ET.SubElement(case, status, message=f"exact {status} message", type="example.Cause")
        path = self.reports / f"TEST-{classname}.xml"
        ET.ElementTree(suite).write(path, encoding="utf-8", xml_declaration=True)
        return path

    def summary(self, *args):
        return self.cli("junit", "--worktree", self.root, "--class", "example.TestOne", "--since", self.since, *args)

    def test_fresh_report_includes_actual_identities_and_only_requested_properties(self):
        self.report()
        nested = self.reports / "foreign-target"
        nested.mkdir()
        (nested / "TEST-Foreign.xml").write_text("not XML; must not read recursively")
        result = self.summary("--property", "openggf.headless.gl=surfaceless-egl",
                              "--property", "soniccd.media.root=/external/private-media")
        self.assertEqual(result.returncode, 0, result.stderr)
        data = json.loads(result.stdout)
        self.assertEqual(data["tests"], 1)
        observed = data["classes"][0]
        self.assertEqual(observed["cases"][0]["identity"], "example.TestOne#case(0)")
        self.assertEqual(observed["properties"]["java.version"], "25.0.1")
        self.assertEqual(observed["properties"]["user.dir"], str(self.root))
        self.assertNotIn("DO-NOT-PRINT-SECRET", result.stdout + result.stderr)
        self.assertNotIn("API_TOKEN", result.stdout + result.stderr)

    def test_failures_errors_skips_are_reported_by_exact_identity_and_not_clean(self):
        self.report(outcomes=("passed", "failure", "error", "skipped"))
        result = self.summary()
        self.assertEqual(result.returncode, 1, result.stderr)
        data = json.loads(result.stdout)
        self.assertEqual([data[k] for k in ("tests", "failures", "errors", "skipped")], [4, 1, 1, 1])
        self.assertEqual(data["classes"][0]["cases"][2], dict(identity="example.TestOne#case(2)",
            status="error", message="exact error message", type="example.Cause"))
        self.assertFalse(data["clean"])

    def test_stale_xml_rejected(self):
        path = self.report()
        os.utime(path, (self.started-10, self.started-10))
        result = self.summary()
        self.assertEqual(result.returncode, 2)
        self.assertIn("Stale XML", result.stderr)

    def test_stale_unrelated_reports_are_ignored_without_parsing_or_deleting_them(self):
        self.report()
        old = self.reports / "TEST-example.Historical.xml"
        old.write_text("historical unrelated content must not be parsed")
        os.utime(old, (self.started-10, self.started-10))
        result = self.summary()
        self.assertEqual(result.returncode, 0, result.stderr)
        data = json.loads(result.stdout)
        self.assertEqual(data["tests"], 1)
        self.assertEqual(data["ignored_stale_reports"], 1)
        self.assertEqual([c["name"] for c in data["classes"]], ["example.TestOne"])
        self.assertEqual(old.read_text(), "historical unrelated content must not be parsed")

    def test_missing_or_unexpected_exact_class_set_rejected(self):
        self.assertEqual(self.summary().returncode, 2)
        self.report()
        self.report("example.OtherTest")
        result = self.summary()
        self.assertEqual(result.returncode, 2)
        self.assertIn("unexpected", result.stderr)
        self.assertEqual(self.summary("--class", "example.OtherTest").returncode, 0)

    def test_missing_jvm_property_wrong_cwd_missing_or_wrong_requested_property_not_clean(self):
        for values, extra in (({"java.version": None}, ()), ({"user.dir": str(self.base)}, ()),
                              ({"openggf.headless.gl": None}, ("--property", "openggf.headless.gl=surfaceless-egl")),
                              ({}, ("--property", "soniccd.media.root=/different"))):
            with self.subTest(values=values, extra=extra):
                self.report(properties=values)
                result = self.summary(*extra)
                self.assertEqual(result.returncode, 1)
                self.assertTrue(json.loads(result.stdout)["issues"])

    def test_secret_property_and_patterns_duplicate_classes_rejected(self):
        self.report()
        for extra in (("--property", "API_TOKEN=should-not-echo"), ("--class", "example.*"),
                      ("--class", "example.TestOne")):
            with self.subTest(extra=extra):
                result = self.summary(*extra)
                self.assertEqual(result.returncode, 2)
                self.assertNotIn("should-not-echo", result.stdout + result.stderr)

    def test_report_symlink_or_parent_escape_rejected(self):
        path = self.report()
        external = self.base / "old.xml"
        path.rename(external)
        path.symlink_to(external)
        self.assertEqual(self.summary().returncode, 2)
        path.unlink()
        self.reports.rmdir()
        self.reports.symlink_to(self.base, target_is_directory=True)
        result = self.summary()
        self.assertEqual(result.returncode, 2)
        self.assertIn("symlinks", result.stderr)

    def test_wrong_suite_or_testcase_owner_and_inconsistent_counts_rejected(self):
        for mutation in ("suite", "case", "counts", "duplicate", "empty"):
            with self.subTest(mutation=mutation):
                path = self.report()
                tree = ET.parse(path)
                suite = tree.getroot()
                if mutation == "suite":
                    suite.set("name", "example.Other")
                elif mutation == "case":
                    suite.find("testcase").set("classname", "example.Other")
                elif mutation == "counts":
                    suite.set("tests", "9")
                elif mutation == "duplicate":
                    suite.append(suite.find("testcase"))
                else:
                    suite.remove(suite.find("testcase"))
                    suite.set("tests", "0")
                tree.write(path)
                self.assertEqual(self.summary().returncode, 2)

    def test_unrecognized_suite_results_and_xml_entity_declarations_rejected(self):
        path = self.report()
        tree = ET.parse(path)
        ET.SubElement(tree.getroot(), "error", message="suite failed outside case")
        tree.write(path)
        self.assertEqual(self.summary().returncode, 2)
        path.write_text('<!DOCTYPE testsuite [<!ENTITY test "unsafe">]><testsuite/>')
        self.assertEqual(self.summary().returncode, 2)

    def test_cutoff_requires_explicit_timezone_and_past_time(self):
        self.report()
        for value in ("not-time", "2026-01-01T00:00:00", "9999-01-01T00:00:00Z"):
            with self.subTest(value=value):
                self.assertEqual(self.summary("--since", value).returncode, 2)


if __name__ == "__main__":
    unittest.main()
