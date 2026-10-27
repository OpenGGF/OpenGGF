#!/usr/bin/env python3
"""Structured-handoff safety tests; synthetic files and Git only, no Maven/media.

Origin: 2026-10-27 Sonic CD integration workflow task.
"""
import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest import mock

import integration_packet as packet

if importlib.util.find_spec("integration_handoff") is not None:
    import integration_handoff as handoff
else:
    handoff = None


class HandoffTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name).resolve()
        self.root = self.base / "tree"
        self.root.mkdir()
        self.git("init", "-q")
        self.git("-c", "user.name=Handoff Test", "-c", "user.email=test@example.invalid",
                 "commit", "--allow-empty", "-qm", "synthetic baseline")
        self.head = self.git("rev-parse", "HEAD").strip()
        (self.root / "src").mkdir()
        self.target = self.root / "src/Canonical.java"
        self.target.write_bytes(b"old\n")
        self.source = self.base / "mirror.java"
        self.source.write_bytes(b"package example;\n")
        self.source.chmod(0o444)
        self.dep = self.base / "dependency.java"
        self.dep.write_bytes(b"dependency\n")
        self.dep.chmod(0o444)
        self.path = self.base / "handoff.json"
        self.value = dict(
            version=1, id="disc-frame-r1",
            packet=dict(version=1, worktree_head=self.head, files=[dict(
                source=str(self.source), sha256=self.sha(self.source), bytes=self.source.stat().st_size,
                mode="0444", utf8=True, lf=True, destination="src/Canonical.java",
                expected={"sha256": self.sha(self.target)})]),
            dependencies=[dict(name="body", path=str(self.dep), sha256=self.sha(self.dep),
                               bytes=self.dep.stat().st_size, mode="0444")],
            api_changes=["Append one typed publication."], known_defects=[],
            tests=dict(classes=["example.TestOwner"], properties={},
                       expected_tests={"example.TestOwner": 7}, java_major=25))
        self.write()

    @staticmethod
    def sha(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def git(self, *args):
        return subprocess.check_output(["git", *args], cwd=self.root, text=True,
                                       stderr=subprocess.DEVNULL)

    def write(self):
        self.path.write_text(json.dumps(self.value) + "\n")

    def summary(self, **kwargs):
        self.assertIsNotNone(handoff, "Missing structured handoff implementation")
        return handoff.handoff_summary(self.root, self.path, **kwargs)

    def rejected(self):
        self.write()
        with self.assertRaises(packet.PacketError):
            self.summary()

    def test_ready_packet_preserves_explicit_mapping_and_descriptive_metadata(self):
        result = self.summary()
        self.assertTrue(result["ready"])
        self.assertEqual(result["stage"], "baseline")
        self.assertEqual(result["packet"], self.value["packet"])
        self.assertEqual(result["tests"], self.value["tests"])
        self.assertEqual(result["id"], "disc-frame-r1")
        self.assertEqual(result["api_changes"], self.value["api_changes"])
        self.assertEqual(self.target.read_bytes(), b"old\n")

    def test_installed_stage_requires_final_bytes_and_keeps_original_packet(self):
        original = copy.deepcopy(self.value)
        self.assertFalse(self.summary(installed=True)["ready"])
        self.target.write_bytes(self.source.read_bytes())
        result = self.summary(installed=True)
        self.assertTrue(result["ready"])
        self.assertEqual(result["stage"], "installed")
        self.assertEqual(result["packet"], original["packet"])
        self.assertEqual(json.loads(self.path.read_text()), original)
        self.assertFalse(self.summary()["ready"])

    def test_absent_dependency_is_blocked_not_ready(self):
        self.dep.unlink()
        result = self.summary()
        self.assertFalse(result["ready"])
        self.assertTrue(result["reasons"])
        self.assertEqual(result["dependencies"][0]["observed"], {"absent": True})

    def test_dependency_with_uncreated_parent_is_blocked(self):
        self.value["dependencies"][0]["path"] = str(self.base / "pending/author/Body.java")
        self.write()
        result = self.summary()
        self.assertFalse(result["ready"])
        self.assertEqual(result["dependencies"][0]["status"], "missing")

    def test_non_utf8_json_string_value_is_invalid(self):
        self.value["api_changes"] = ["\ud800"]
        self.rejected()

    def test_changed_dependency_hash_length_or_mode_is_blocked(self):
        for key, value in (("sha256", "0" * 64), ("bytes", 1), ("mode", "0644")):
            with self.subTest(key=key):
                original = copy.deepcopy(self.value)
                self.value["dependencies"][0][key] = value
                self.write()
                self.assertFalse(self.summary()["ready"])
                self.value = original

    def test_changed_destination_source_or_head_is_blocked(self):
        self.target.write_bytes(b"changed\n")
        self.assertFalse(self.summary()["ready"])
        self.target.write_bytes(b"old\n")
        self.source.chmod(0o644)
        self.source.write_bytes(b"changed source\n")
        self.source.chmod(0o444)
        self.assertFalse(self.summary()["ready"])
        self.value["packet"]["worktree_head"] = "1" * 40
        self.write()
        self.assertFalse(self.summary()["ready"])

    def test_dependency_same_exact_source_and_matching_pins_is_valid(self):
        source = self.value["packet"]["files"][0]
        self.value["dependencies"] = [dict(name="source", path=source["source"],
            sha256=source["sha256"], bytes=source["bytes"], mode=source["mode"])]
        self.write()
        self.assertTrue(self.summary()["ready"])
        self.value["dependencies"][0]["sha256"] = "0" * 64
        self.rejected()

    def test_hardlink_dependency_alias_is_invalid(self):
        alias = self.base / "alias.java"
        os.link(self.dep, alias)
        entry = copy.deepcopy(self.value["dependencies"][0])
        entry.update(name="alias", path=str(alias))
        self.value["dependencies"].append(entry)
        self.rejected()

    def test_dependency_cannot_alias_spec_or_destination(self):
        for path in (self.path, self.target):
            with self.subTest(path=path):
                self.value["dependencies"][0]["path"] = str(path)
                self.rejected()

    def test_source_and_destination_hardlink_alias_is_invalid(self):
        self.target.unlink()
        os.link(self.source, self.target)
        self.value["packet"]["files"][0]["expected"] = {"sha256": self.sha(self.target)}
        self.rejected()

    def test_symlink_inputs_and_external_spec_requirement(self):
        linked = self.base / "link.java"
        linked.symlink_to(self.dep)
        self.value["dependencies"][0]["path"] = str(linked)
        self.rejected()
        self.value["dependencies"][0]["path"] = str(self.dep)
        self.write()
        inside = self.root / "handoff.json"
        inside.write_bytes(self.path.read_bytes())
        self.assertIsNotNone(handoff)
        with self.assertRaises(packet.PacketError):
            handoff.handoff_summary(self.root, inside)
        linked_spec = self.base / "linked.json"
        linked_spec.symlink_to(self.path)
        with self.assertRaises(packet.PacketError):
            handoff.handoff_summary(self.root, linked_spec)

    def test_unknown_and_duplicate_json_keys_rejected(self):
        self.value["unknown"] = True
        self.rejected()
        self.path.write_text('{"version":1,"version":1}\n')
        with self.assertRaises(packet.PacketError):
            self.summary()

    def test_malformed_json_and_text_contract_rejected(self):
        for data in (b"{\n", b"{}\r\n", b"{}", b"\xff\n", b"{}\x00\n"):
            with self.subTest(data=data):
                self.path.write_bytes(data)
                with self.assertRaises(packet.PacketError):
                    self.summary()

    def test_identifier_name_duplicates_and_limits(self):
        for value in (True, "../escape", "", "a" * 129):
            with self.subTest(value=value):
                self.value["id"] = value
                self.rejected()
        self.value["id"] = "good"
        self.value["dependencies"].append(copy.deepcopy(self.value["dependencies"][0]))
        self.rejected()

    def test_bool_versions_and_byte_counts_rejected(self):
        for owner, key in ((self.value, "version"), (self.value["packet"], "version"),
                           (self.value["dependencies"][0], "bytes"),
                           (self.value["packet"]["files"][0], "bytes")):
            original = owner[key]
            owner[key] = True
            self.rejected()
            owner[key] = original

    def test_packet_schema_destinations_and_duplicate_mapping_invalid(self):
        file = self.value["packet"]["files"][0]
        for destination in ("../bad.java", "/absolute.java", "src//x.java", ".git/config"):
            with self.subTest(destination=destination):
                file["destination"] = destination
                self.rejected()
        file["destination"] = "src/Canonical.java"
        self.value["packet"]["files"].append(copy.deepcopy(file))
        self.rejected()

    def test_invalid_class_selectors_and_duplicates_rejected(self):
        for classes in ([], ["Simple"], ["example.*"], ["example.Test#method"],
                        ["example.TestOwner", "example.TestOwner"], ["example.Test"] * 51):
            with self.subTest(classes=classes):
                self.value["tests"]["classes"] = classes
                self.rejected()

    def test_count_inventory_requires_exact_classes_and_positive_int(self):
        for counts in ({}, {"example.TestOwner": True}, {"example.TestOwner": 0},
                       {"example.TestOwner": 1000001}, {"other.Test": 7}):
            with self.subTest(counts=counts):
                self.value["tests"]["expected_tests"] = counts
                self.rejected()

    def test_properties_are_allowlisted_and_plain_strings(self):
        for props in ({"secret": "x"}, {"java.version": "25"}, {"user.dir": str(self.root)},
                      {"soniccd.media.root": True}, {"soniccd.media.root": "a\nb"},
                      {"soniccd.media.root": "a" * 4097}):
            with self.subTest(props=props):
                self.value["tests"]["properties"] = props
                self.rejected()
        self.value["tests"]["properties"] = {"soniccd.media.root": "/existing/media"}
        self.write()
        self.assertTrue(self.summary()["ready"])

    def test_java_version_and_test_unknown_fields_rejected(self):
        for value in (True, "25", 24, 26):
            self.value["tests"]["java_major"] = value
            self.rejected()
        self.value["tests"]["java_major"] = 25
        self.value["tests"]["command"] = "arbitrary command"
        self.rejected()

    def test_description_and_dependency_budgets_rejected(self):
        for key in ("api_changes", "known_defects"):
            for value in (["a"] * 129, ["x" * 2049], ["bad\x00value"], "not an array"):
                self.value[key] = value
                self.rejected()
            self.value[key] = []
        self.value["dependencies"] *= 129
        self.rejected()

    def test_per_file_and_spec_size_budgets_rejected(self):
        self.value["dependencies"][0]["bytes"] = packet.MAX_FILE + 1
        self.rejected()
        self.value["dependencies"][0]["bytes"] = self.dep.stat().st_size
        self.write()
        self.assertIsNotNone(handoff)
        with mock.patch.object(handoff, "MAX_SPEC", 8):
            with self.assertRaises(packet.PacketError):
                self.summary()

    def test_changed_input_during_recheck_rejected(self):
        self.assertIsNotNone(handoff)
        original = packet.unchanged

        def change(saved, **kwargs):
            if saved.path == self.dep:
                self.dep.chmod(0o644)
                self.dep.write_bytes(b"late mutation\n")
            return original(saved, **kwargs)

        with mock.patch.object(packet, "unchanged", side_effect=change):
            with self.assertRaises(packet.PacketError):
                self.summary()

    def test_missing_dependency_appearing_during_recheck_rejected(self):
        self.dep.unlink()
        self.assertIsNotNone(handoff)
        original = packet.unchanged

        def appear(saved, **kwargs):
            if saved.path == self.dep:
                self.dep.write_bytes(b"dependency\n")
                self.dep.chmod(0o444)
            return original(saved, **kwargs)

        with mock.patch.object(packet, "unchanged", side_effect=appear):
            with self.assertRaises(packet.PacketError):
                self.summary()

    def test_source_replaced_by_hardlink_alias_to_dependency_is_invalid(self):
        self.source.unlink()
        os.link(self.dep, self.source)
        self.rejected()

    def test_missing_packet_source_is_blocked(self):
        self.source.unlink()
        self.assertFalse(self.summary()["ready"])

    def test_empty_dependencies_and_declared_defect_do_not_grant_authenticity(self):
        self.value["dependencies"] = []
        self.value["known_defects"] = ["Declared fixture oracle needs review."]
        self.write()
        result = self.summary()
        self.assertTrue(result["ready"])
        self.assertEqual(result["known_defects"], self.value["known_defects"])
        self.assertIn("non-atomic", result["observation"].lower())


if __name__ == "__main__":
    unittest.main()
