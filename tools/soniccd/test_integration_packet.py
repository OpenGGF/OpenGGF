#!/usr/bin/env python3
"""Synthetic source/report safety tests; never launch Maven or read game media.

Run: python3 -B -m unittest discover -s tools/soniccd -p test_integration_packet.py
Origin: 2026-10-26 private Sonic CD packet integration aid.
"""
import base64
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

class CustodyCliTests(PacketFixture):
    def custody(self, *args):
        return self.cli("custody", "--worktree", self.root, *args)

    def test_empty_module_inventory_is_read_only_and_does_not_inspect_root_sources(self):
        before = self.destination.read_bytes()
        result = self.custody()
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertTrue(report["ready"])
        self.assertEqual(report["head"], self.head)
        self.assertEqual(report["worktree"], str(self.root))
        self.assertEqual(report["modules"], [])
        self.assertEqual(report["links"], [])
        self.assertEqual(self.destination.read_bytes(), before)
        self.assertEqual(self.git("status", "--porcelain=v1"), "?? src/\n")

    def test_explicit_resource_link_matches_without_reading_or_modifying_target(self):
        target = self.base / "original media"
        target.mkdir()
        asset = target / "not-an-input.bin"
        asset.write_bytes(b"original supplied bytes")
        link = self.root / "resources"
        link.symlink_to(target, target_is_directory=True)
        result = self.custody("--link", "resources=" + str(target))
        self.assertEqual(result.returncode, 0, result.stderr)
        report = json.loads(result.stdout)
        self.assertTrue(report["ready"])
        self.assertEqual(len(report["links"]), 1)
        self.assertTrue(link.is_symlink())
        self.assertEqual(asset.read_bytes(), b"original supplied bytes")

    def test_wrong_resource_target_is_negative_evidence_without_repair(self):
        target = self.base / "actual"
        expected = self.base / "expected"
        target.mkdir()
        expected.mkdir()
        link = self.root / "resources"
        link.symlink_to(target, target_is_directory=True)
        result = self.custody("--link", "resources=" + str(expected))
        self.assertEqual(result.returncode, 1, result.stderr)
        self.assertFalse(json.loads(result.stdout)["ready"])
        self.assertEqual(link.resolve(), target)

    def test_resource_link_syntax_and_mutating_options_are_rejected(self):
        for value in ("resources", "resources=", "=missing"):
            with self.subTest(value=value):
                result = self.custody("--link", value)
                self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
                self.assertNotIn("Traceback", result.stderr)
                self.assertEqual(result.stdout, "")
        for option in ("--apply", "--execute", "--maven-terminal"):
            with self.subTest(option=option):
                self.assertEqual(self.custody(option).returncode, 2)


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

    def test_simple_testcase_owner_is_attributed_to_suite_but_foreign_qualified_owner_is_rejected(self):
        path = self.report()
        tree = ET.parse(path)
        tree.getroot().find("testcase").set("classname", "TestOne")
        tree.write(path, encoding="utf-8", xml_declaration=True)
        result = self.summary()
        self.assertEqual(result.returncode, 0, result.stderr)
        case = json.loads(result.stdout)["classes"][0]["cases"][0]
        self.assertEqual(case["identity"], "example.TestOne#case(0)")
        self.assertEqual(case["reported_classname"], "TestOne")
        tree.getroot().find("testcase").set("classname", "foreign.TestOne")
        tree.write(path, encoding="utf-8", xml_declaration=True)
        result = self.summary()
        self.assertEqual(result.returncode, 2)
        self.assertIn("owner mismatch", result.stderr)

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


class DraftCliTests(PacketFixture):
    def setUp(self):
        super().setUp()
        self.spec = self.base / "draft-spec.json"
        self.mapping = {key: self.entry[key] for key in ("source", "sha256", "bytes", "destination")}
        self.write_spec()

    def cli(self, *args):
        # In-process CLI parsing makes injected observation races deterministic.
        # The unchanged packet/JUnit classes retain subprocess CLI coverage.
        out, err = io.StringIO(), io.StringIO()
        argv = list(map(str, args))
        with redirect_stdout(out), redirect_stderr(err):
            try:
                code = packet.main(argv)
            except SystemExit as stop:
                code = stop.code
        return subprocess.CompletedProcess(argv, code, out.getvalue(), err.getvalue())

    def write_spec(self, entries=None, **changes):
        value = dict(version=1, files=[self.mapping] if entries is None else entries)
        value.update(changes)
        self.spec.write_text(json.dumps(value), encoding="utf-8")

    def draft(self, *args):
        return self.cli("draft", "--worktree", self.root, "--spec", self.spec, *args)

    def rejected_draft(self, text=None):
        result = self.draft()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "")
        if text:
            self.assertIn(text, result.stderr)
        return result

    def test_exact_manifest_pins_dirty_bytes_and_absence_round_trip_without_writes(self):
        self.destination.write_bytes(b"reviewed dirty destination\n")
        self.write_spec([self.mapping, dict(self.mapping, destination="src/New.java")])
        observed = (self.source, self.destination, self.spec, self.manifest)
        before = [(p.read_bytes(), packet.signature(p.stat())) for p in observed]
        paths = sorted(str(p) for p in self.base.rglob("*"))
        with mock.patch.object(packet, "worktree_build_slot", side_effect=AssertionError("no lease")), \
             mock.patch.object(packet, "apply_packet", side_effect=AssertionError("no apply")), \
             mock.patch.object(packet, "replace_one", side_effect=AssertionError("no replacement")):
            result = self.draft()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stderr, "")
        value = json.loads(result.stdout)
        self.assertEqual(set(value), {"version", "worktree_head", "files"})
        self.assertEqual(value["version"], 1)
        self.assertEqual(value["worktree_head"], self.head)
        expected_entry = dict(self.entry, expected={"sha256": self.sha(self.destination.read_bytes())})
        self.assertEqual(value["files"], [expected_entry,
            dict(expected_entry, destination="src/New.java", expected={"absent": True})])
        self.assertEqual(sorted(str(p) for p in self.base.rglob("*")), paths)
        self.assertEqual([(p.read_bytes(), packet.signature(p.stat())) for p in observed], before)
        # Only this test's caller saves stdout; draft has no output path or write step.
        self.manifest.write_text(result.stdout, encoding="utf-8")
        imported = self.packet()
        self.assertEqual(imported.returncode, 0, imported.stderr)
        self.assertFalse(json.loads(imported.stdout)["applied"])
        self.assertFalse((self.root / "src/New.java").exists())

    def test_only_fixed_read_only_git_calls_and_actual_head_are_used(self):
        real = packet.subprocess.check_output
        with mock.patch.object(packet.subprocess, "check_output", wraps=real) as git:
            result = self.draft()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["worktree_head"], self.head)
        self.assertGreaterEqual(len(git.call_args_list), 4)
        for call in git.call_args_list:
            self.assertIn(call.args[0], (["git", "rev-parse", "--show-toplevel"],
                                        ["git", "rev-parse", "HEAD"]))
            self.assertEqual(call.kwargs["cwd"], self.root)

    def test_spec_requires_explicit_pins_destinations_and_exact_keys(self):
        for changes in ({"worktree_head": self.head}, {"unknown": True}, {"version": True},
                        {"version": 2}, {"files": []}, {"files": {}}, {"files": [None]}):
            with self.subTest(changes=changes):
                self.write_spec(**changes)
                self.rejected_draft()
        for field in self.mapping:
            with self.subTest(missing=field):
                self.write_spec([{k: v for k, v in self.mapping.items() if k != field}])
                self.rejected_draft("exactly keys")
        for extra in ({"expected": {"absent": True}}, {"mode": "0444"}, {"approval": True}):
            with self.subTest(extra=extra):
                self.write_spec([dict(self.mapping, **extra)])
                self.rejected_draft("exactly keys")

    def test_malformed_and_duplicate_json_keys_rejected(self):
        for raw in ('{', '[]', '{"version":1,"version":1,"files":[]}',
                    json.dumps(dict(version=1, files=[self.mapping])).replace(
                        '"sha256":', '"source":"duplicate", "sha256":')):
            with self.subTest(raw=raw):
                self.spec.write_text(raw)
                self.rejected_draft()

    def test_non_utf8_and_excessively_nested_spec_fail_without_traceback(self):
        for data in (b"\xff", ("[" * 2000 + "0" + "]" * 2000).encode("utf-8")):
            with self.subTest(prefix=data[:10]):
                self.spec.write_bytes(data)
                result = self.rejected_draft()
                self.assertNotIn("Traceback", result.stderr)
        # JSON decoders on other supported Python versions may exhaust recursion.
        with mock.patch.object(packet.json, "loads", side_effect=RecursionError("synthetic decoder limit")):
            self.rejected_draft("nesting")

    def test_spec_must_be_external_canonical_regular_and_bounded(self):
        internal = self.root / "spec.json"
        internal.write_bytes(self.spec.read_bytes())
        link = self.base / "linked-spec.json"
        link.symlink_to(self.spec)
        for spec in (internal, link, self.base, self.base / "missing.json"):
            with self.subTest(spec=spec):
                result = self.cli("draft", "--worktree", self.root, "--spec", spec)
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, "")
        with self.spec.open("wb") as stream:
            stream.truncate(packet.MAX_FILE + 1)
        self.rejected_draft("limit")

    def test_spec_destination_hardlink_alias_rejected(self):
        self.destination.unlink()
        os.link(self.spec, self.destination)
        self.rejected_draft("alias")

    def test_source_hash_and_length_are_verified_not_invented(self):
        for change in ({"sha256": "0"*64}, {"bytes": 0}, {"sha256": "BAD"},
                       {"sha256": 42}, {"bytes": True}, {"bytes": -1},
                       {"bytes": packet.MAX_FILE + 1}, {"bytes": 1.5}):
            with self.subTest(change=change):
                self.write_spec([dict(self.mapping, **change)])
                self.rejected_draft()

    def test_nonfrozen_and_nontext_sources_rejected(self):
        self.source.chmod(0o644)
        self.rejected_draft("not frozen")
        for data in (b"CR\r\n", b"no final LF", b"NUL\0\n", b"\xff\n"):
            with self.subTest(data=data):
                self.source.write_bytes(data)
                self.source.chmod(0o444)
                self.write_spec([dict(self.mapping, sha256=self.sha(data), bytes=len(data))])
                self.rejected_draft()
                self.source.chmod(0o644)

    def test_empty_frozen_source_is_allowed_with_explicit_empty_pin(self):
        self.source.chmod(0o644)
        self.source.write_bytes(b"")
        self.source.chmod(0o444)
        self.write_spec([dict(self.mapping, sha256=self.sha(b""), bytes=0)])
        result = self.draft()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["files"][0]["bytes"], 0)

    def test_unsafe_destinations_and_duplicates_rejected(self):
        for value in ("../escape", str(self.destination), "src/../Canonical.java", "src//Canonical.java",
                      "src/./Canonical.java", "src\\Canonical.java", ".git/config", "src/.GIT/config",
                      "C:/escape", "", None):
            with self.subTest(value=value):
                self.write_spec([dict(self.mapping, destination=value)])
                self.rejected_draft()
        self.write_spec([self.mapping, self.mapping])
        self.rejected_draft("Duplicate/conflicting")

    def test_missing_destination_parent_is_not_created(self):
        self.write_spec([dict(self.mapping, destination="missing/New.java")])
        self.rejected_draft("directories separately")
        self.assertFalse((self.root / "missing").exists())

    def test_sources_must_be_external_and_canonical_without_links(self):
        link = self.base / "linked-source"
        link.symlink_to(self.source)
        loop = self.base / "loop"
        loop.symlink_to(loop)
        directory_link = self.base / "directory-link"
        directory_link.symlink_to(self.base, target_is_directory=True)
        for value in ("relative.java", str(self.destination), str(link), str(loop),
                      str(directory_link / self.source.name), str(self.base / ".." / self.base.name / self.source.name)):
            with self.subTest(value=value):
                self.write_spec([dict(self.mapping, source=value)])
                self.rejected_draft()

    def test_destination_file_and_parent_symlinks_rejected(self):
        link = self.root / "src/Alias.java"
        link.symlink_to(self.destination)
        parent = self.root / "linked"
        parent.symlink_to(self.base, target_is_directory=True)
        for value in ("src/Alias.java", "linked/New.java"):
            with self.subTest(value=value):
                self.write_spec([dict(self.mapping, destination=value)])
                self.rejected_draft("symlinks")

    def test_source_destination_and_cross_entry_hardlink_aliases_rejected(self):
        second = self.root / "src/Other.java"
        os.link(self.source, second)
        self.write_spec([dict(self.mapping, destination="src/Other.java")])
        self.rejected_draft("hardlink alias")
        self.write_spec([self.mapping, dict(self.mapping, destination="src/Other.java")])
        self.rejected_draft("hardlink alias")
        second.unlink()
        os.link(self.destination, second)
        self.write_spec([self.mapping, dict(self.mapping, destination="src/Other.java")])
        self.rejected_draft("hardlink alias")

    def test_per_file_and_total_source_budgets_rejected(self):
        self.source.chmod(0o644)
        with self.source.open("wb") as stream:
            stream.truncate(packet.MAX_FILE + 1)
        self.source.chmod(0o444)
        self.rejected_draft("limit")
        self.source.chmod(0o644)
        self.source.write_bytes(b"package example;\n")
        self.source.chmod(0o444)
        self.write_spec([self.mapping, dict(self.mapping, destination="src/New.java")])
        with mock.patch.object(packet, "MAX_PACKET", self.mapping["bytes"] * 2 - 1):
            self.rejected_draft("64 MiB")

    def test_oversized_destination_rejected(self):
        with self.destination.open("wb") as stream:
            stream.truncate(packet.MAX_FILE + 1)
        self.rejected_draft("limit")

    def mutate_after_snapshot(self, path, mutation):
        real = packet.snapshot
        changed = False

        def observing(candidate, **kwargs):
            nonlocal changed
            saved = real(candidate, **kwargs)
            if candidate == path and not changed:
                changed = True
                mutation()
            return saved
        return mock.patch.object(packet, "snapshot", observing)

    def test_observed_source_byte_change_rejected_without_mutating_it_again(self):
        def mutate():
            self.source.chmod(0o644)
            self.source.write_bytes(b"other actor changed source\n")
            self.source.chmod(0o444)
        with self.mutate_after_snapshot(self.source, mutate):
            self.rejected_draft("change")
        self.assertEqual(self.source.read_bytes(), b"other actor changed source\n")

    def test_observed_source_mode_change_rejected(self):
        with self.mutate_after_snapshot(self.source, lambda: self.source.chmod(0o644)):
            self.rejected_draft("change")
        self.assertEqual(self.source.stat().st_mode & 0o7777, 0o644)

    def test_observed_existing_and_absent_destination_changes_rejected(self):
        with self.mutate_after_snapshot(self.destination, lambda: self.destination.write_bytes(b"edited\n")):
            self.rejected_draft("Baseline changed")
        self.destination.unlink()
        with self.mutate_after_snapshot(self.destination, lambda: self.destination.write_bytes(b"appeared\n")):
            self.rejected_draft("Baseline changed")
        self.assertEqual(self.destination.read_bytes(), b"appeared\n")

    def test_observed_spec_change_rejected(self):
        with self.mutate_after_snapshot(self.spec, lambda: self.spec.write_text(self.spec.read_text() + "\n")):
            self.rejected_draft("change")

    def test_observed_final_head_change_rejected(self):
        with mock.patch.object(packet, "worktree", side_effect=[(self.root, self.head),
                (self.root, self.head), (self.root, "0"*40)]):
            self.rejected_draft("HEAD")

    def test_parent_replacement_even_for_absent_destination_rejected(self):
        self.destination.unlink()
        original = self.root / "src-old"
        real = packet.validate_packet

        def changed(*args):
            result = real(*args)
            (self.root / "src").rename(original)
            (self.root / "src").mkdir()
            return result
        with mock.patch.object(packet, "validate_packet", changed):
            self.rejected_draft("directory changed")

    def test_destination_metadata_change_after_validation_rejected(self):
        real = packet.validate_packet

        def changed(*args):
            result = real(*args)
            self.destination.chmod(0o600)
            return result
        with mock.patch.object(packet, "validate_packet", changed):
            self.rejected_draft("change")

    def test_exact_worktree_root_required(self):
        result = self.cli("draft", "--worktree", self.root / "src", "--spec", self.spec)
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        self.assertIn("exact worktree root", result.stderr)

    def test_no_output_apply_or_scheduler_options(self):
        for option in ("--apply", "--maven-terminal", "--output"):
            with self.subTest(option=option):
                result = self.draft(option)
                self.assertEqual(result.returncode, 2)
                self.assertEqual(result.stdout, "")
        result = self.cli("draft", "--help")
        self.assertEqual(result.returncode, 0)
        self.assertIn("--spec", result.stdout)


class PreserveCliTests(unittest.TestCase):
    """Whole-file byte observations with synthetic, externally pinned frozen files."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="packet-preserve-test-")
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name).resolve()
        self.baseline = self.base / "baseline.java"
        self.final = self.base / "FINAL.java"
        self.recipe = self.base / "recipe.json"
        self.configure(b"count=2;\nold\nend\n", b"count=3;\nnew\nend\nappend\n",
                       [(6, b"2", b"3"), (9, b"old", b"new"), (17, b"", b"append\n")])

    @staticmethod
    def frozen(path, data):
        if path.exists():
            path.chmod(0o644)
        path.write_bytes(data)
        path.chmod(0o444)

    @staticmethod
    def pin(path, data):
        return dict(path=str(path), sha256=packet.digest(data), bytes=len(data))

    @staticmethod
    def edit(offset, before, after):
        return dict(offset=offset, before=base64.b64encode(before).decode("ascii"),
                    after=base64.b64encode(after).decode("ascii"))

    def configure(self, baseline, final, edits):
        self.frozen(self.baseline, baseline)
        self.frozen(self.final, final)
        self.value = dict(version=1, baseline=self.pin(self.baseline, baseline),
                          final=self.pin(self.final, final),
                          edits=[self.edit(*edit) for edit in edits])
        self.write_recipe()

    def write_recipe(self):
        self.frozen(self.recipe, json.dumps(self.value).encode("utf-8"))

    def cli(self, *extra):
        argv = ["preserve", "--recipe", str(self.recipe), *map(str, extra)]
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            try:
                code = packet.main(argv)
            except SystemExit as stop:
                code = stop.code
        return subprocess.CompletedProcess(argv, code, out.getvalue(), err.getvalue())

    def reject(self, text=None):
        result = self.cli()
        self.assertEqual(result.returncode, 2, result.stdout + result.stderr)
        self.assertEqual(result.stdout, "")
        self.assertLessEqual(len(result.stderr), 4096)
        self.assertNotIn("Traceback", result.stderr)
        if text:
            self.assertIn(text.lower(), result.stderr.lower())
        return result

    def test_count_and_append_migration_cli_is_read_only_across_invocations(self):
        # Real 0444 files and CLI subprocesses, not a mock of the verifier.
        saved = {p: (p.read_bytes(), packet.signature(p.stat()))
                 for p in (self.recipe, self.baseline, self.final)}
        for _ in range(2):
            result = subprocess.run([sys.executable, "-B", str(SCRIPT), "preserve",
                                     "--recipe", str(self.recipe)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(result.stderr, "")
            report = json.loads(result.stdout)
            self.assertTrue(report["forward_equal"])
            self.assertTrue(report["inverse_equal"])
            self.assertEqual(report["edits"], 3)
            for name in ("baseline", "final"):
                self.assertEqual(report[name], {k: self.value[name][k] for k in ("sha256", "bytes")})
            self.assertIn("non-atomic", report["observation"].lower())
            self.assertLess(len(result.stdout), 1500)
            self.assertNotIn("append", result.stdout)
        self.assertEqual({p.name for p in self.base.iterdir()}, {p.name for p in saved})
        for path, (data, info) in saved.items():
            self.assertEqual(path.read_bytes(), data)
            self.assertEqual(packet.signature(path.stat()), info)

    def test_insert_replace_delete_with_lf_and_utf8_byte_offsets(self):
        baseline = "α\nold\nremove\nend\n".encode()
        final = "α\ninsert\noβ\nend\n".encode()
        self.configure(baseline, final, [(3, b"", b"insert\n"), (4, b"ld", "β".encode()),
                                       (7, b"remove\n", b"")])
        self.assertEqual(self.cli().returncode, 0)

    def test_twenty_five_declared_migrations_do_not_shift_baseline_offsets(self):
        baseline = b"x\n" * 25
        edits = [(index * 2, b"x", str(index).encode()) for index in range(25)]
        final = b"".join(str(index).encode() + b"\n" for index in range(25))
        self.configure(baseline, final, edits)
        self.assertEqual(json.loads(self.cli().stdout)["edits"], 25)

    def test_inverse_reconstructs_all_original_bytes_with_cumulative_shifts(self):
        edits = [(6, b"2", b"three"), (9, b"old\n", b""), (17, b"", b"append\n")]
        baseline, final = b"count=2;\nold\nend\n", b"count=three;\nend\nappend\n"
        self.configure(baseline, final, edits)
        self.assertEqual(packet.reconstruct_preservation(final, edits, inverse=True), baseline)
        # A preserved gap must also survive the inverse; it cannot only undo edit fragments.
        self.assertNotEqual(packet.reconstruct_preservation(final.replace(b"end", b"END", 1), edits,
                                                           inverse=True), baseline)
        real = packet.reconstruct_preservation

        def corrupt_inverse(data, edits, *, inverse=False):
            result = real(data, edits, inverse=inverse)
            return result + b"!" if inverse else result
        with mock.patch.object(packet, "reconstruct_preservation", side_effect=corrupt_inverse):
            self.reject("inverse")

    def test_empty_edit_list_requires_identical_independent_files_and_empty_files_work(self):
        for data in (b"", b"unchanged\n"):
            self.configure(data, data, [])
            self.assertEqual(self.cli().returncode, 0)
        self.configure(b"old\n", b"new\n", [])
        self.reject("forward")

    def test_whole_file_insertion_deletion_and_adjacent_edits(self):
        for before, after, edits in ((b"", b"new\n", [(0, b"", b"new\n")]),
                                     (b"old\n", b"", [(0, b"old\n", b"")]),
                                     (b"abc\n", b"ABc\n", [(0, b"a", b"A"), (1, b"b", b"B")])):
            with self.subTest(before=before):
                self.configure(before, after, edits)
                self.assertEqual(self.cli().returncode, 0)

    def test_wrong_original_even_with_revised_pin_is_rejected(self):
        self.frozen(self.baseline, b"count=9;\nold\nend\n")
        self.value["baseline"] = self.pin(self.baseline, self.baseline.read_bytes())
        self.write_recipe()
        self.reject("before")

    def test_undeclared_extra_byte_or_replacement_even_with_correct_final_pin(self):
        for data in (b"count=3;\nnew\nend!\nappend\n", b"count=3;\nnew\nEND\nappend\n"):
            self.frozen(self.final, data)
            self.value["final"] = self.pin(self.final, data)
            self.write_recipe()
            result = self.reject("forward")
            self.assertIn("offset", result.stderr)
            self.assertNotIn("count=", result.stderr)

    def test_duplicate_decreasing_overlapping_and_out_of_range_offsets(self):
        for edits in ([(6, b"2", b"3"), (6, b"", b"x")],
                      [(9, b"old", b"new"), (6, b"2", b"3")],
                      [(6, b"2;", b"3;"), (7, b";", b";")],
                      [(18, b"", b"x")], [(16, b"\nx", b"\n")]):
            self.value["edits"] = [self.edit(*edit) for edit in edits]
            self.write_recipe()
            self.reject()

    def test_offset_types_overflow_and_empty_empty_edit(self):
        for offset in (True, -1, 1.0, "6", 10 ** 100):
            self.value["edits"] = [self.edit(offset, b"2", b"3")]
            self.write_recipe()
            self.reject("offset")
        self.value["edits"] = [self.edit(0, b"", b"")]
        self.write_recipe()
        self.reject("empty")

    def test_base64_must_be_canonical_standard_strict_text(self):
        for encoded in (None, 7, "Mg", "Mg===", "Mh==", "Mg==\n", "_w==", "é", "!!!!"):
            for name in ("before", "after"):
                self.value["edits"] = [self.edit(6, b"2", b"3")]
                self.value["edits"][0][name] = encoded
                self.write_recipe()
                self.reject("base64")

    def test_unknown_missing_and_wrong_container_recipe_keys(self):
        original = json.loads(self.recipe.read_text())
        for where in ((), ("baseline",), ("final",), ("edits", 0)):
            for change in ("extra", "missing"):
                self.value = json.loads(json.dumps(original))
                selected = self.value
                for key in where:
                    selected = selected[key]
                if change == "extra":
                    selected["unknown"] = "never execute"
                else:
                    selected.pop(next(iter(selected)))
                self.write_recipe()
                self.reject("keys")
        for key, value in (("version", True), ("version", 2), ("edits", {}),
                           ("baseline", []), ("final", None), ("edits", [None])):
            self.value = dict(original, **{key: value})
            self.write_recipe()
            self.reject()

    def test_duplicate_keys_malformed_non_utf8_nonfinite_and_nested_json(self):
        for data in (b'{"version":1,"version":1}', b'{"a":{"x":1,"x":2}}', b'{', b'\xff',
                     b'{"version":NaN}', b'[' * 1500 + b']' * 1500):
            self.frozen(self.recipe, data)
            self.reject()

    def test_pins_are_external_required_exact_lowercase_and_bounded(self):
        original = json.loads(self.recipe.read_text())
        for name in ("baseline", "final"):
            for key, value in (("sha256", "0" * 64), ("sha256", "A" * 64), ("sha256", None),
                               ("bytes", 1), ("bytes", True), ("bytes", -1),
                               ("bytes", packet.MAX_FILE + 1)):
                self.value = json.loads(json.dumps(original))
                self.value[name][key] = value
                self.write_recipe()
                self.reject()

    def test_frozen_text_contract_applies_to_both_files(self):
        for name in ("baseline", "final"):
            self.configure(b"count=2;\nold\nend\n", b"count=3;\nnew\nend\nappend\n",
                           [(6, b"2", b"3"), (9, b"old", b"new"), (17, b"", b"append\n")])
            path = getattr(self, name)
            for data in (b"bad\r\n", b"bad\0\n", b"\xff\n", b"missing LF"):
                self.frozen(path, data)
                self.value[name] = self.pin(path, data)
                self.write_recipe()
                self.reject()
            path.chmod(0o644)
            self.reject("0444")
            path.chmod(0o444)

    def test_paths_must_be_explicit_canonical_external_regular_and_distinct(self):
        original = str(self.baseline)
        for path in (None, "relative.java", str(self.base / "missing"), str(self.base),
                     str(self.base) + "/./baseline.java", str(self.base) + "//baseline.java",
                     str(self.base / ".." / self.base.name / "baseline.java")):
            self.value["baseline"]["path"] = path
            self.write_recipe()
            self.reject()
        self.value["baseline"]["path"] = original
        self.write_recipe()
        with mock.patch.object(packet, "ROOT", self.base):
            self.reject("external")
        self.value["final"]["path"] = original
        self.write_recipe()
        self.reject("alias")

    def test_file_parent_symlink_loop_hardlink_and_recipe_aliases(self):
        link = self.base / "link.java"
        link.symlink_to(self.baseline)
        loop = self.base / "loop"
        loop.symlink_to(loop)
        parent = self.base / "linked-parent"
        parent.symlink_to(self.base, target_is_directory=True)
        hard = self.base / "hard.java"
        os.link(self.baseline, hard)
        for path in (link, loop, parent / self.baseline.name, hard, self.recipe):
            self.value["final"]["path"] = str(path)
            self.write_recipe()
            self.reject()

    def test_recipe_path_is_also_canonical_regular_external_and_bounded(self):
        saved = self.recipe
        alias = self.base / "alias.json"
        alias.symlink_to(saved)
        for path in (alias, self.base, self.base / "missing", Path("relative.json")):
            self.recipe = path
            self.reject()
        self.recipe = saved
        with mock.patch.object(packet, "MAX_RECIPE", self.recipe.stat().st_size - 1):
            self.reject("limit")

    def test_actual_oversized_regular_files_are_rejected_before_reading(self):
        for path, limit in ((self.baseline, packet.MAX_FILE), (self.recipe, packet.MAX_RECIPE)):
            saved = path.read_bytes()
            path.chmod(0o644)
            with path.open("wb") as stream:
                stream.truncate(limit + 1)
            path.chmod(0o444)
            self.reject("limit")
            self.frozen(path, saved)

    def test_each_source_must_be_external_even_when_recipe_is_external(self):
        checkout = self.base / "checkout"
        checkout.mkdir()
        inside = checkout / "source.java"
        self.frozen(inside, self.baseline.read_bytes())
        for name in ("baseline", "final"):
            original = self.value[name]["path"]
            self.value[name]["path"] = str(inside)
            self.write_recipe()
            with mock.patch.object(packet, "ROOT", checkout):
                self.reject("external")
            self.value[name]["path"] = original

    def test_input_edit_count_decoded_and_reconstructed_size_budgets(self):
        with mock.patch.object(packet, "MAX_FILE", 16):
            self.reject("limit")
        with mock.patch.object(packet, "MAX_EDITS", 2):
            self.reject("limit")
        with mock.patch.object(packet, "MAX_EDIT_BYTES", 9):
            self.reject("limit")
        self.configure(b"a" * 12000 + b"\n", b"a" * 12000 + b"\n", [(0, b"", b"b" * 2000)])
        with mock.patch.object(packet, "MAX_FILE", 13000):
            self.reject("reconstructed")

    def mutate_after_snapshot(self, path, mutation):
        real = packet.snapshot
        changed = False

        def observing(candidate, **kwargs):
            nonlocal changed
            saved = real(candidate, **kwargs)
            if candidate == path and not changed:
                changed = True
                mutation()
            return saved
        return mock.patch.object(packet, "snapshot", side_effect=observing)

    def test_observed_input_content_mode_identity_and_recipe_changes_reject(self):
        for path in (self.baseline, self.final, self.recipe):
            saved = path.read_bytes()
            for change in (lambda: self.frozen(path, saved + b"\n"),
                           lambda: path.chmod(0o644),
                           lambda: (path.unlink(), self.frozen(path, saved))):
                self.frozen(path, saved)
                with self.mutate_after_snapshot(path, change):
                    self.reject("change")
                # The verifier never repairs another writer's change.
                self.assertEqual(path.read_bytes(), saved + b"\n" if path.stat().st_size != len(saved) else saved)
            self.frozen(path, saved)

    def test_observed_parent_replacement_rejects_even_when_file_inode_is_retained(self):
        parent = self.base / "exports"
        parent.mkdir()
        nested = parent / "baseline.java"
        self.baseline.rename(nested)
        self.baseline = nested
        self.value["baseline"]["path"] = str(nested)
        self.write_recipe()
        replacement = self.base / "replacement"
        replacement.mkdir()
        os.link(nested, replacement / nested.name)

        def replace_parent():
            parent.rename(self.base / "old-exports")
            replacement.rename(parent)
        with self.mutate_after_snapshot(nested, replace_parent):
            self.reject("directory changed")

    def test_preserve_never_calls_git_apply_lock_or_executes_recipe_data(self):
        with mock.patch.object(packet, "worktree", side_effect=AssertionError("no Git")), \
             mock.patch.object(packet.subprocess, "check_output", side_effect=AssertionError("no command")), \
             mock.patch.object(packet, "worktree_build_slot", side_effect=AssertionError("no lock")), \
             mock.patch.object(packet, "apply_packet", side_effect=AssertionError("no apply")), \
             mock.patch.object(packet.os, "replace", side_effect=AssertionError("no write")):
            self.assertEqual(self.cli().returncode, 0)

    def test_errors_are_bounded_and_do_not_dump_source_or_long_untrusted_keys(self):
        self.frozen(self.recipe, ('{"' + 'sensitive' * 10000 + '":0,"' +
                                  'sensitive' * 10000 + '":1}').encode())
        self.reject()
        self.frozen(self.recipe, b'{"baseline": "secret source text"}')
        self.assertNotIn("secret source", self.reject().stderr)

    def test_no_apply_output_worktree_or_scheduler_options(self):
        for option in ("--apply", "--output", "--worktree", "--maven-terminal"):
            self.assertEqual(self.cli(option).returncode, 2)


if __name__ == "__main__":
    unittest.main()
