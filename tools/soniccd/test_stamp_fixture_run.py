"""ROM-free fixture acceptance and tamper controls for the CD-05 task."""
import copy
import gzip
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

from stamp_fixture_run import compare_runs, sha, validate_fixture, validate_run


class StampFixtureChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.ingress = bytearray(0x40000)
        self.ingress[0x1d000:0x20000] = bytes([0xa5]) * 12288
        self.egress = bytearray(self.ingress)
        for column in range(32):
            start = 0x1d000 + column * 384
            self.egress[start + 96:start + 384] = bytes(288)
        devices = {"regs.58": 0x8007, "regs.02": 2, "regs.64": 72,
                   "gfx_hw.tracePtr": 0x1c8c0, "gfx_hw.bufferStart": 0x3a0c0,
                   "gfx_hw.dotMask": 0x7fffff, "gfx_hw.stampShift": 16,
                   "gfx_hw.mapShift": 7, "gfx_hw.mapPtr": 0x20000,
                   "gfx_hw.bufferOffset": 761, "regs.5c": 11, "regs.60": 0,
                   "regs.5e": 0x7400, "regs.62": 256, "regs.66": 0x7200, "regs.5a": 0x8000}
        end = dict(devices, **{"regs.58": 7, "regs.02": 1, "regs.64": 0,
                              "gfx_hw.tracePtr": 0x1cb00, "gfx_hw.bufferStart": 0x3a300})
        self.fixture = {"ordinal": 1, "callback_address": 0xa12003, "callback_flags": 0x1000,
                        "callback_value": 1, "ingress_frame": 1450, "egress_frame_call": 1461, "observation_kind": "partial-device-continuation",
                        "image_base": 0x1d000, "image_bytes": 12288, "wordram_bytes": 0x40000,
                        "wordram_encoding": "68000-address-order-big-endian",
                        "ingress_file": "in.bin.gz", "egress_file": "out.bin.gz",
                        "ingress_devices": devices, "egress_devices": end,
                        "request": {"completed_lines": 24, "remaining_lines": 72,
                                    "trace_base": 0x1c800, "map_offset": 0x20000, "width": 256,
                                    "stamp_size_register": 7, "image_start_register": 0x7400,
                                    "wordram_mode_register": 2,
                                    "trace_words": [0] * 384, "cursor": 0x1c8c0, "reconstructed_total_lines": 96}}
        self.payload("ingress", self.ingress)
        self.payload("egress", self.egress)

    def payload(self, side, data):
        (self.root / self.fixture[f"{side}_file"]).write_bytes(gzip.compress(data, mtime=0))
        self.fixture[f"{side}_sha256"] = sha(data)

    def test_partial_native_shape_preserves_completed_cells(self):
        result = validate_fixture(self.root, self.fixture)
        self.assertEqual(result["touched_nibbles"], 72 * 256)
        self.assertEqual(result["image_bytes_compared"], 12288)
        self.assertEqual(result["wordram_bytes_compared"], 262144)

    def test_altered_compressed_payload_rejected(self):
        changed = bytearray(self.ingress)
        changed[123] ^= 1
        (self.root / "in.bin.gz").write_bytes(gzip.compress(changed, mtime=0))
        with self.assertRaisesRegex(ValueError, "hash"):
            validate_fixture(self.root, self.fixture)

    def test_rehashed_unrelated_owner_write_rejected(self):
        self.egress[123] ^= 1
        self.payload("egress", self.egress)
        with self.assertRaisesRegex(ValueError, "outside the image"):
            validate_fixture(self.root, self.fixture)

    def test_rehashed_wrong_render_pixel_rejected(self):
        self.egress[0x1d080] ^= 1
        self.payload("egress", self.egress)
        with self.assertRaisesRegex(ValueError, "Exact native continuation mismatch"):
            validate_fixture(self.root, self.fixture)

    def test_completed_prefix_cannot_be_silently_rerendered(self):
        self.egress[0x1d000] = 0
        self.payload("egress", self.egress)
        with self.assertRaisesRegex(ValueError, "Exact native continuation mismatch"):
            validate_fixture(self.root, self.fixture)

    def test_trace_words_must_match_ingress(self):
        self.fixture["request"]["trace_words"][0] = 1
        with self.assertRaisesRegex(ValueError, "Trace words"):
            validate_fixture(self.root, self.fixture)

    def test_partial_progress_cannot_claim_submission(self):
        self.fixture["observation_kind"] = "full-request"
        with self.assertRaisesRegex(ValueError, "partial ingress"):
            validate_fixture(self.root, self.fixture)

    def test_wrong_cursor_owner_and_unobserved_mode_rejected(self):
        for key, value in (("gfx_hw.tracePtr", 0x1c800), ("regs.02", 1), ("regs.58", 0x8005)):
            with self.subTest(key=key):
                changed = copy.deepcopy(self.fixture)
                changed["ingress_devices"][key] = value
                with self.assertRaises(ValueError):
                    validate_fixture(self.root, changed)

    def test_other_callback_or_non_owner_cannot_claim_egress(self):
        for key, value in (("callback_address", 0xa12004), ("callback_flags", 0x2000),
                           ("callback_value", 2), ("egress_frame_call", 1450)):
            with self.subTest(key=key):
                changed = copy.deepcopy(self.fixture)
                changed[key] = value
                with self.assertRaisesRegex(ValueError, "ownership read"):
                    validate_fixture(self.root, changed)

    def test_incomplete_egress_rejected(self):
        self.fixture["egress_devices"]["regs.64"] = 1
        with self.assertRaisesRegex(ValueError, "Egress"):
            validate_fixture(self.root, self.fixture)

    def test_only_2m_sub_owner_is_supported_at_ingress(self):
        for ownership in (0, 1, 3, 4, 5, 6, 7):
            with self.subTest(ownership=ownership):
                changed = copy.deepcopy(self.fixture)
                changed["ingress_devices"]["regs.02"] = ownership
                changed["request"]["wordram_mode_register"] = ownership
                with self.assertRaises(ValueError):
                    validate_fixture(self.root, changed)

    def test_request_raw_mode_must_match_ingress_including_unrelated_bits(self):
        for bit in (1, 4, 8, 0x40, 0x100):
            with self.subTest(bit=bit):
                changed = copy.deepcopy(self.fixture)
                changed["request"]["wordram_mode_register"] ^= bit
                with self.assertRaises(ValueError):
                    validate_fixture(self.root, changed)

    def test_egress_cannot_change_stamp_mode_or_wordram_mode_or_priority(self):
        for key, value in (("regs.58", 5), ("regs.58", 0x107),
                           ("regs.02", 5), ("regs.02", 9), ("regs.02", 17), ("regs.02", 25)):
            with self.subTest(key=key, value=value):
                changed = copy.deepcopy(self.fixture)
                changed["egress_devices"][key] = value
                with self.assertRaises(ValueError):
                    validate_fixture(self.root, changed)

    def test_request_and_device_stamp_modes_must_agree(self):
        for key, value in (("regs.58", 0x8107), ("regs.02", 10)):
            with self.subTest(key=key):
                changed = copy.deepcopy(self.fixture)
                changed["ingress_devices"][key] = value
                changed["request"]["wordram_mode_register"] = changed["ingress_devices"]["regs.02"]
                with self.assertRaises(ValueError):
                    validate_fixture(self.root, changed)

    def test_ownership_return_and_unrelated_mode_register_bits_are_permitted(self):
        self.fixture["ingress_devices"]["regs.02"] = 0x2a02
        self.fixture["request"]["wordram_mode_register"] = 0x2a02
        self.fixture["egress_devices"]["regs.02"] = 0x5bc1
        self.assertEqual(validate_fixture(self.root, self.fixture)["wordram_bytes_compared"], 262144)

    @unittest.skipUnless(os.environ.get("SONICCD_BIZHAWK_HOME"), "Set SONICCD_BIZHAWK_HOME for pinned C# guard checks")
    def test_compiled_producer_rejects_mutated_descriptors_and_allows_return(self):
        from device_observer_run import RUNTIME_PINS, verify_pins
        from oracle_boot import ASSEMBLIES, COMPILER_SHA256, digest

        compiler = Path("/usr/lib/mono/msbuild/Current/bin/Roslyn/csc.exe")
        self.assertEqual(digest(compiler), COMPILER_SHA256)
        dll = Path(os.environ["SONICCD_BIZHAWK_HOME"]).resolve(strict=True) / "dll"
        verify_pins(dll, RUNTIME_PINS)
        cases = [{"label": "normal return", "expected": "ready", "fixture": self.fixture}]
        for owner in (0, 1, 3, 4, 5, 6, 7):
            changed = copy.deepcopy(self.fixture)
            changed["ingress_devices"]["regs.02"] = owner
            changed["request"]["wordram_mode_register"] = owner
            cases.append({"label": f"ingress owner {owner}", "expected": "reject", "fixture": changed})
        for side, key, value in (("request", "wordram_mode_register", 6),
                                  ("request", "wordram_mode_register", 0x102),
                                  ("request", "stamp_size_register", 5),
                                  ("request", "cursor", 0x1c800),
                                  ("ingress_devices", "regs.58", 0x8107),
                                  ("ingress_devices", "regs.02", 10),
                                  ("egress_devices", "regs.58", 5),
                                  ("egress_devices", "regs.58", 0x107),
                                  ("egress_devices", "regs.02", 5),
                                  ("egress_devices", "regs.02", 9),
                                  ("egress_devices", "regs.02", 17),
                                  ("egress_devices", "regs.02", 25),
                                  ("egress_devices", "gfx_hw.dotMask", 0x7ffff),
                                  ("egress_devices", "gfx_hw.stampShift", 15),
                                  ("egress_devices", "gfx_hw.mapShift", 3),
                                  ("egress_devices", "regs.64", 1)):
            changed = copy.deepcopy(self.fixture)
            changed[side][key] = value
            cases.append({"label": f"{side} {key} {value}", "expected": "reject", "fixture": changed})
        changed = copy.deepcopy(self.fixture)
        changed["ingress_devices"]["regs.02"] = changed["request"]["wordram_mode_register"] = 10
        cases.append({"label": "matching raw mode with unsupported priority", "expected": "reject", "fixture": changed})
        changed = copy.deepcopy(self.fixture)
        changed["egress_devices"] = dict(changed["ingress_devices"])
        cases.append({"label": "still busy and sub owned", "expected": "pending", "fixture": changed})
        changed = copy.deepcopy(self.fixture)
        changed["ingress_devices"]["regs.02"] = changed["request"]["wordram_mode_register"] = 0x2a02
        changed["egress_devices"]["regs.02"] = 0x5bc1
        cases.append({"label": "unrelated register bits", "expected": "ready", "fixture": changed})
        harness = self.root / "StampGuardChecks.cs"
        harness.write_text('''using System;
using System.IO;
using System.Collections.Generic;
using Newtonsoft.Json.Linq;
internal static class StampGuardChecks {
    public static int Main(string[] args) {
        int count = 0;
        foreach (JObject test in JArray.Parse(File.ReadAllText(args[0]))) {
            var f = (JObject)test["fixture"];
            string result;
            try {
                var ingress = f["ingress_devices"].ToObject<SortedDictionary<string, long>>();
                StampFixtureProbe.ValidateIngress((JObject)f["request"], ingress);
                var pending = new StampFixturePending { request = (JObject)f["request"], ingress = ingress };
                result = StampFixtureProbe.EgressReady(pending,
                    f["egress_devices"].ToObject<SortedDictionary<string, long>>()) ? "ready" : "pending";
            } catch (InvalidOperationException) { result = "reject"; }
            if (result != (string)test["expected"]) {
                Console.Error.WriteLine(test["label"] + ": got " + result); return 1;
            }
            count++;
        }
        Console.WriteLine("Passed " + count + " compiled producer guard cases"); return 0;
    }
}
''')
        fixtures = self.root / "guard-cases.json"
        fixtures.write_text(json.dumps(cases))
        executable = self.root / "guard-checks.exe"
        source = Path(__file__).resolve().parent
        command = ["mono", str(compiler), "/nologo", "/langversion:9", "/deterministic+",
                   "/main:StampGuardChecks", f"/out:{executable}",
                   "/reference:/usr/lib/mono/4.8-api/Facades/netstandard.dll"]
        subprocess.run(command + [f"/reference:{dll / name}" for name in ASSEMBLIES]
                       + [str(source / name) for name in ("stamp_fixture_probe.cs", "device_observer_probe.cs", "oracle_probe.cs")]
                       + [str(harness)], check=True, timeout=30, capture_output=True, text=True)
        env = os.environ.copy()
        for name in ("DISPLAY", "WAYLAND_DISPLAY"):
            env.pop(name, None)
        env.update(MONO_PATH=str(dll), LD_LIBRARY_PATH=str(dll), TMPDIR=str(self.root))
        result = subprocess.run(["mono", str(executable), str(fixtures)], check=True, timeout=30,
                                env=env, capture_output=True, text=True)
        self.assertIn(f"Passed {len(cases)} compiled producer guard cases", result.stdout)

    def test_undeclared_extent_and_payload_escape_rejected(self):
        for key, value in (("image_bytes", 100), ("wordram_encoding", "native"), ("ingress_file", "../in.bin.gz")):
            with self.subTest(key=key):
                changed = copy.deepcopy(self.fixture)
                changed[key] = value
                with self.assertRaises(ValueError):
                    validate_fixture(self.root, changed)

    def test_av_and_restore_controls_reject_changes(self):
        for label in ("left", "right"):
            root = self.root / label
            root.mkdir()
            (root / "av.jsonl").write_text('{"frame":1}\n')
            (root / "continuation.jsonl").write_text('same\n')
            (root / "fixtures.json").write_text('[]')
            (root / "summary.json").write_text(json.dumps({"frames": 1, "fixture_count": 0,
                "restore_120_frames_equal": True, "observation_savestate_unchanged": True,
                "wordram_accesses_before_egress": 0, "checkpoint_sha256": "same"}))
        left, right = self.root / "left", self.root / "right"
        compare_runs(left, right, fixtures=False)
        (right / "av.jsonl").write_text('different\n')
        with self.assertRaisesRegex(ValueError, "av.jsonl"):
            compare_runs(left, right, fixtures=False)
        summary = json.loads((left / "summary.json").read_text())
        for key in ("restore_120_frames_equal", "observation_savestate_unchanged"):
            changed = dict(summary, **{key: False})
            (left / "summary.json").write_text(json.dumps(changed))
            with self.assertRaisesRegex(ValueError, "restore or observer"):
                validate_run(left, 1, 0)


if __name__ == "__main__":
    unittest.main()
