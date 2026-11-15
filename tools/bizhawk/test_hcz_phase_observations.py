"""Negative controls for literal native-phase evidence; no core, ROM or JVM launch."""
from copy import deepcopy
import json
from collections import Counter
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import compare_hcz_phase_observations as probe


def regions(**words):
    data = {key: bytearray(size) for key, (_, size) in probe.REGIONS.items()}
    for address, value in words.items():
        address = int(address, 16)
        for key, (base, size) in probe.REGIONS.items():
            if base <= address < base + size:
                data[key][address - base:address - base + 2] = value.to_bytes(2, "big")
                break
    return {key: value.hex().upper() for key, value in data.items()}


def fixture():
    old = regions(EE78=10, EE3E=10, EEC2=4, FE10=0x0101)
    new = regions(EE78=11, EE3E=11, EEC2=8, FE10=0x0101)
    frames = [{"sequence": i, "completed_frame": i + 9, "bk2_input_index_0based": i + 8,
               "trace_row_decimal": i - 1,
               "is_lagged": False, "core_lag_count": 0, "individual_button_mask": 64,
               "trace_logical_mask": 16, "raw_regions_hex": raw}
              for i, raw in enumerate((old, new), 1)]
    cold = deepcopy(frames)
    for row in cold:
        row["input_mask"] = row.pop("individual_button_mask")
    names = ["process_sprites_entry", "find_floor_bg_diff_read", "find_wall_bg_y_diff_read",
             "hcz2_screen_entry", "hcz2_background_entry", "hcz2_wall_move_entry",
             "shake_setup_entry", "bg_diff_published"]
    sites = {"sites": [{"name": name, "pc": 0x100 + i} for i, name in enumerate(names)]}
    events = [{"sequence": i + 1, "site": name, "registered_trigger_pc": 0x100 + i,
               "observed_cpu_pc": 0x100 + i, "scheduled_completed_target": 11,
               "host_completed_frame_raw": 10, "advance_before_completed_frame": 10,
               "inflight_input_index_0based": 10, "registers": {"A0": 0xFFB000},
               "raw_regions_hex": old}
              for i, name in enumerate(names)]
    return frames, events, sites, cold


class NativePhaseEvidenceTests(unittest.TestCase):
    def compare(self, mutate=None):
        data = fixture()
        if mutate:
            mutate(*data)
        return probe.analyze(*data, first_frame=10, last_frame=11)

    def test_clean_prefix_cannot_stand_in_for_the_declared_window(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            probe.analyze(*fixture(), first_frame=10, last_frame=12)

    def test_previous_publication_and_individual_buttons_are_independent_of_jump_encoding(self):
        result = self.compare()
        self.assertEqual("values_match_timing_partial", result["status"])
        self.assertEqual(26, result["counts"]["field_comparisons"])
        self.assertEqual(2, result["counts"]["decisive_collision_events"])
        self.assertEqual(11, result["end_transition"]["end_target"])

    def test_constant_Y_and_irrelevant_changing_fields_do_not_claim_read_discrimination(self):
        result = self.compare()
        self.assertEqual(0, result["counts"]["changed_fields"]["camera_y_diff"])
        reads = result["per_site_read_discrimination"]
        self.assertEqual(1, reads["find_floor_bg_diff_read"]["events_with_any_read_field_changed"])
        self.assertEqual(0, reads["find_wall_bg_y_diff_read"]["events_with_any_read_field_changed"])
        self.assertEqual({"address": "EE40", "changing_events": 0, "unchanging_events": 1},
                         reads["find_wall_bg_y_diff_read"]["read_fields"]["camera_y_diff"])
        self.assertFalse(result["all_read_field_timing_discriminated"])

    def test_control_with_both_read_differences_changing_is_distinguished(self):
        def change(frames, _, __, cold):
            raw = regions(EE78=11, EE3E=11, EE40=1, EEC2=8, FE10=0x0101)
            frames[1]["raw_regions_hex"] = cold[1]["raw_regions_hex"] = raw
        result = self.compare(change)
        self.assertEqual("values_match_timing_discriminated", result["status"])
        self.assertTrue(result["all_read_field_timing_discriminated"])

    def test_current_publication_substitution_is_reported_even_with_misleading_named_values(self):
        def change(frames, events, *_):
            events[1]["raw_regions_hex"] = frames[1]["raw_regions_hex"]
            events[1]["named_unsigned"] = {"camera_x": 10}
        result = self.compare(change)
        self.assertEqual("differences", result["status"])
        self.assertTrue(any(x["kind"] == "previous_publication" and x["field"] == "camera_x"
                            for x in result["differences"]))

    def test_cold_raw_change_is_retained_without_excluding_unused_words(self):
        def change(frames, *_):
            frames[1]["raw_regions_hex"] = dict(frames[1]["raw_regions_hex"])
            frames[1]["raw_regions_hex"]["wall_slot11_B32E_004A"] = "FF" + "00" * 73
        result = self.compare(change)
        self.assertTrue(any(x["kind"] == "cold_raw_bytes" for x in result["differences"]))

    def test_lost_input_or_lag_identity_is_not_normalized(self):
        for key, value in (("individual_button_mask", 16), ("core_lag_count", 1)):
            with self.subTest(key=key):
                result = self.compare(lambda frames, *_: frames[1].update({key: value}))
                self.assertEqual("differences", result["status"])

    def test_clock_or_frame_discontinuity_rejects_instead_of_fitting_an_offset(self):
        mutations = [lambda frames, *_: frames[1].update(completed_frame=12),
                     lambda _, events, *__: events[1].update(host_completed_frame_raw=9)]
        for mutate in mutations:
            with self.subTest(mutate=mutate), self.assertRaises(ValueError):
                self.compare(mutate)

    def test_no_collision_or_unchanged_only_observations_cannot_qualify(self):
        def no_collision(_, events, *__):
            for event in events[1:3]:
                event.update(site="process_sprites_entry", registered_trigger_pc=0x100,
                             observed_cpu_pc=0x100)
        with self.assertRaisesRegex(ValueError, "No sampled"):
            self.compare(no_collision)
        def unchanged(frames, _, __, cold):
            frames[1]["raw_regions_hex"] = frames[0]["raw_regions_hex"]
            cold[1]["raw_regions_hex"] = frames[0]["raw_regions_hex"]
        result = self.compare(unchanged)
        self.assertTrue(any(x["kind"] == "unchanged_only_control" for x in result["differences"]))

    def test_order_and_observed_PC_are_checked_separately(self):
        cases = [(lambda _, events, *__: events[1].update(observed_cpu_pc=0x123), "callback_PC"),
                 (lambda _, events, *__: events[1].update(registered_trigger_pc=0x123), "callback_PC"),
                 (lambda _, events, *__: events[0].update(site="hcz2_screen_entry",
                                                        registered_trigger_pc=0x103,
                                                        observed_cpu_pc=0x103), "collision_order"),
                 (lambda _, events, *__: events[3].update(site="process_sprites_entry",
                                                        registered_trigger_pc=0x100,
                                                        observed_cpu_pc=0x100), "collision_order")]
        for change, kind in cases:
            with self.subTest(kind=kind):
                self.assertTrue(any(row["kind"] == kind for row in self.compare(change)["differences"]))

    def test_actor_and_missing_previous_sample_are_not_silently_dropped(self):
        result = self.compare(lambda _, events, *__: events[1]["registers"].update(A0=0xFFB094))
        self.assertTrue(any(row["kind"] == "unsampled_actor" for row in result["differences"]))
        def first_target(_, events, *__):
            for event in events:
                event.update(scheduled_completed_target=10, host_completed_frame_raw=9,
                             advance_before_completed_frame=9, inflight_input_index_0based=9)
        result = self.compare(first_target)
        self.assertEqual(0, result["counts"]["field_comparisons"])
        self.assertEqual(2, sum(row["kind"] == "missing_previous_sample"
                               for row in result["differences"]))

    def test_event_selection_clocks_and_raw_schema_rejections(self):
        cases = [lambda _, events, *__: events[1].update(site="unknown"),
                 lambda _, events, *__: events[1].update(scheduled_completed_target=10),
                 lambda _, events, *__: events[1].update(scheduled_completed_target=12),
                 lambda _, events, *__: events[1].update(advance_before_completed_frame=9),
                 lambda _, events, *__: events[1].update(inflight_input_index_0based=9),
                 lambda _, __, sites, ___: sites["sites"].append(sites["sites"][0]),
                 lambda _, events, *__: events[1]["raw_regions_hex"].pop("F600_0080"),
                 lambda _, events, *__: events[1]["raw_regions_hex"].update(F600_0080="GG" * 128)]
        for change in cases:
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.compare(change)

    def test_complete_identity_and_resume_description(self):
        for key, value in (("is_lagged", True), ("trace_row_decimal", 17)):
            with self.subTest(key=key):
                result = self.compare(lambda frames, *_: frames[1].update({key: value}))
                self.assertTrue(any(x["kind"] == "cold_identity" and x["field"] == key
                                    for x in result["differences"]))
        with self.assertRaises(ValueError):
            self.compare(lambda frames, *_: frames[1].update(bk2_input_index_0based=8))
        self.assertEqual(11, self.compare()["end_transition"]["first_setup_target_at_or_after_end"])

    def test_cli_executes_and_refuses_to_overwrite_its_complete_evidence(self):
        frames, events, sites, cold = fixture()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name, rows in (("frames", frames), ("events", events), ("cold", cold)):
                (root / name).write_text("".join(json.dumps(row) + "\n" for row in rows))
            (root / "sites").write_text(json.dumps(sites))
            self.write_summaries(root, events)
            command = [sys.executable, "-B", str(Path(probe.__file__)),
                       "--frames", str(root / "frames"), "--events", str(root / "events"),
                       "--cold-frames", str(root / "cold"), "--sites", str(root / "sites"),
                       "--first-frame", "10", "--last-frame", "11",
                       "--summary", str(root / "summary"),
                       "--cold-summary", str(root / "cold-summary"),
                       "--output", str(root / "output")]
            result = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            saved = (root / "output").read_bytes()
            self.assertEqual(6, len(json.loads(saved)["input_sha256"]))
            self.assertEqual(2, subprocess.run(command, capture_output=True).returncode)
            self.assertEqual(saved, (root / "output").read_bytes())
            # Older executed phase summaries need their explicit original movie provenance.
            summary = json.loads((root / "summary").read_text())
            summary.pop("movie_sha256")
            (root / "summary").write_text(json.dumps(summary))
            legacy = command[:-1] + [str(root / "legacy-output")]
            self.assertEqual(2, subprocess.run(legacy, capture_output=True).returncode)
            (root / "provenance").write_text(json.dumps({"movie": {"sha256": probe.PROFILE_MOVIE_SHA256}}))
            with_provenance = legacy + ["--phase-provenance", str(root / "provenance")]
            self.assertEqual(0, subprocess.run(with_provenance, capture_output=True).returncode)
            self.assertEqual(7, len(json.loads((root / "legacy-output").read_bytes())["input_sha256"]))

    @staticmethod
    def write_summaries(root, events):
        common = {"execution_status": "completed", "first_requested_completed_frame": 10,
                  "last_requested_completed_frame": 11, "samples": 2,
                  "last_completed_frame": 11, "inputs_applied": 11, "RAM_writes": 0,
                  "state_loads": 0, "video_enabled": False,
                  "movie_sha256": probe.PROFILE_MOVIE_SHA256}
        observed = common | {"registration_before_completed_frame": 9,
                             "registered_callbacks_disposed": True,
                             "opcode_checks_before_registration": True,
                             "sites_sha256": probe.digest(root / "sites"),
                             "completed-frames.jsonl_sha256": probe.digest(root / "frames"),
                             "execute-events.jsonl_sha256": probe.digest(root / "events"),
                             "execute_event_count": len(events),
                             "hit_counts": dict(Counter(e["site"] for e in events))}
        cold = common | {"execute_callbacks": 0, "initial_completed_frame": 0,
                         "expected_samples": 2, "observations_sha256": probe.digest(root / "cold")}
        (root / "summary").write_text(json.dumps(observed))
        (root / "cold-summary").write_text(json.dumps(cold))

    def test_terminal_evidence_rejects_truncated_events_and_wrong_cold_or_movie(self):
        frames, events, sites, cold = fixture()
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            for name, rows in (("frames", frames), ("events", events), ("cold", cold)):
                (root / name).write_text("".join(json.dumps(row) + "\n" for row in rows))
            (root / "sites").write_text(json.dumps(sites))
            self.write_summaries(root, events)
            command = [sys.executable, "-B", str(Path(probe.__file__)),
                       "--frames", str(root / "frames"), "--events", str(root / "events"),
                       "--cold-frames", str(root / "cold"), "--sites", str(root / "sites"),
                       "--first-frame", "10", "--last-frame", "11",
                       "--summary", str(root / "summary"),
                       "--cold-summary", str(root / "cold-summary"),
                       "--output", str(root / "output")]
            cases = [("summary", "execution_status", "incomplete"),
                     ("summary", "registered_callbacks_disposed", False),
                     ("summary", "registration_before_completed_frame", 8),
                     ("summary", "execute_event_count", 7),
                     ("summary", "sites_sha256", "bad"),
                     ("cold-summary", "execute_callbacks", 1),
                     ("cold-summary", "RAM_writes", 1),
                     ("cold-summary", "state_loads", 1),
                     ("cold-summary", "movie_sha256", "bad")]
            for name, key, value in cases:
                with self.subTest(key=key):
                    self.write_summaries(root, events)
                    file = root / name
                    summary = json.loads(file.read_text())
                    summary[key] = value
                    file.write_text(json.dumps(summary))
                    result = subprocess.run(command, capture_output=True, text=True)
                    self.assertEqual(2, result.returncode, result.stdout)
                    self.assertEqual("", result.stdout)
                    self.assertFalse((root / "output").exists())
            # Even a recomputed content hash cannot hide the terminal count mismatch.
            self.write_summaries(root, events)
            (root / "events").write_text("".join(json.dumps(e) + "\n" for e in events[:-1]))
            summary = json.loads((root / "summary").read_text())
            summary["execute-events.jsonl_sha256"] = probe.digest(root / "events")
            (root / "summary").write_text(json.dumps(summary))
            self.assertEqual(2, subprocess.run(command, capture_output=True).returncode)
            self.assertFalse((root / "output").exists())


if __name__ == "__main__":
    unittest.main()
