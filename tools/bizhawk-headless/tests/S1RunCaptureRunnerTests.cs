using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Synthetic-movie tests for the S1 detour-aware run state machine:
    /// level -> ss -> level sequencing with per-segment naming, offsets and
    /// rows; transition records with RAM fields read at the specified
    /// frames (giant_ring on the $10 entry frame, stage_exit on the
    /// return-level arm frame); the always-on detour machine's manifest
    /// gating (any transition OR a run id); the S1-specific re-arm after a
    /// non-$10 mode exit; cross-segment aux tracker carry-over straight
    /// through a detour; and the run-end finalize routing for movie end,
    /// mid-detour movie end, and S1_STOP_AT_FRAME.
    /// </summary>
    internal static class S1RunCaptureRunnerTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string BlankRow = "|..|........|........|";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner records a level-ss-level round trip",
                RecordsLevelSsLevelRoundTrip));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner suffixes repeat detours and level"
                + " re-entries",
                SuffixesRepeatDetoursAndLevelReEntries));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner finalizes ss segment when movie ends"
                + " mid-detour",
                FinalizesSsSegmentWhenMovieEndsMidDetour));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner never arms ss without a started level"
                + " segment",
                NeverArmsSsWithoutStartedLevelSegment));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner re-arms after a non-ss mode exit",
                RearmsAfterNonSsModeExit));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner forces the manifest for a detour-free"
                + " run with a run id",
                ForcesManifestForDetourFreeRunWithRunId));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner honors stop-at-frame inside a detour",
                HonorsStopAtFrameInsideDetour));
        }

        /// <summary>
        /// Schedule (completed frame F): boot 1-4; arm at F=5 (GHZ act raw
        /// 0); level rows F=6-15 (10 rows); ss entry at F=16 with the
        /// giant_ring RAM fields and the SS state set; ss rows F=17-21
        /// (5 rows, one lag frame, one Right+A input row); exit + same-frame
        /// re-arm at F=22 (act raw 1 -> ghz2) with the carried ring count;
        /// level rows F=23-27; the 28-row movie fires the movie-done guard
        /// at F=28.
        /// </summary>
        private static void RecordsLevelSsLevelRoundTrip()
        {
            string[] rows = Rows(28);
            rows[17] = "|..|...RA...|........|";     // ss row 1 input.
            WithMovie(rows, movie =>
            {
                var host = new FakeS1Host(
                    (h, frame) =>
                {
                    if (frame == 5)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xFE10] = 0x00;           // GHZ
                        h.Ram[0xFE11] = 0x00;           // act raw 0
                        h.SetU16(0xD008, 0x0050);
                        h.SetU16(0xD00C, 0x03B0);
                        h.SetU32(0xF636, 0xAABBCCDDu);
                        h.Ram[0xD024] = 0x02;           // routine
                    }
                    if (frame == 16)
                    {
                        h.Ram[0xF600] = 0x10;
                        h.SetU16(0xFE20, 10);           // rings before -> "a"
                        h.Ram[0xFE57] = 0;              // emeralds before
                        h.Ram[0xFE16] = 2;              // v_lastspecial
                        h.SetU32(0xD008, 0x25AB0300u);  // ss x_pos
                        h.SetU32(0xD00C, 0x044D8300u);  // ss y_pos
                        h.SetU16(0xD010, 0xFFDE);       // vel_x
                        h.SetU16(0xD012, 0x0048);       // vel_y
                        h.SetU16(0xD014, 0x013C);       // inertia
                        h.Ram[0xD022] = 0x07;           // status
                        h.SetU16(0xF780, 0x4000);       // ss_angle
                        h.SetU16(0xF782, 0x1800);       // ss_rotate
                        h.SetU16(0xF7A0, 0x000C);       // bg_anim
                    }
                    if (frame == 18)
                    {
                        h.Ram[0xD024] = 0x04;   // routine changes mid-SS
                    }
                    if (frame == 19)
                    {
                        h.IsLagged = true;
                    }
                    if (frame == 20)
                    {
                        h.IsLagged = false;
                    }
                    if (frame == 22)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xFE11] = 0x01;           // act raw 1
                        h.SetU16(0xFE20, 9);            // carried rings
                        h.Ram[0xFE57] = 1;              // emerald collected
                        h.SetU16(0xD008, 0x0060);
                        h.SetU16(0xD00C, 0x0290);
                    }
                });

                List<S1RunSegmentOutput> outputs;
                S1RunCaptureResult result = Capture(
                    movie, host, "test-run", 0, out outputs);

                AssertEx.Equal(3, result.Segments.Count);
                AssertEx.Equal(3, outputs.Count);
                AssertEx.Equal(2, result.Transitions.Count);

                S1RunSegmentOutput seg1 = outputs[0];
                AssertEx.Equal("ghz1", seg1.DirToken);
                AssertEx.Equal("level", seg1.ManifestEntry.Kind);
                AssertEx.Equal(
                    "complete_run", seg1.ManifestEntry.TraceProfile);
                AssertEx.Equal(5, seg1.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(10, seg1.ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, seg1.ManifestEntry.ZoneId);
                AssertEx.Equal(1, seg1.ManifestEntry.Act);
                string[] seg1Lines = seg1.PhysicsCsv.Split('\n');
                AssertEx.Equal(12, seg1Lines.Length);   // header+10+empty
                AssertEx.Equal(S1TraceCsvWriter.Header, seg1Lines[0]);
                AssertContains(seg1.MetadataJson, "  \"zone\": \"ghz\",\n");
                AssertContains(seg1.MetadataJson,
                    "  \"rng_seed\": \"0xAABBCCDD\",\n");
                AssertContains(seg1.MetadataJson,
                    "  \"source_bk2\": \"synthetic.bk2\"\n");
                AssertContains(seg1.MetadataJson,
                    "  \"lua_script_version\": \"3.17\",\n");
                // S1 level metadata is byte-identical in and out of run
                // context: no run_id / segment_index lines (spec §7).
                AssertEx.Equal(false, seg1.MetadataJson.Contains("run_id"));
                AssertEx.Equal(
                    false, seg1.MetadataJson.Contains("segment_index"));

                S1RunSegmentOutput ss = outputs[1];
                AssertEx.Equal("ss", ss.DirToken);
                AssertEx.Equal("special_stage", ss.ManifestEntry.Kind);
                AssertEx.Equal(
                    "s1_special_stage", ss.ManifestEntry.TraceProfile);
                AssertEx.Equal(16, ss.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(5, ss.ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, ss.ManifestEntry.ZoneId);
                AssertEx.Equal(0, ss.ManifestEntry.Act);
                AssertEx.Equal(2, ss.ManifestEntry.SpecialStageIndex ?? -1);
                AssertEx.Equal(string.Empty, ss.AuxStateJsonl);
                string[] ssLines = ss.PhysicsCsv.Split('\n');
                AssertEx.Equal(7, ssLines.Length);      // header+5+empty
                AssertEx.Equal(S1SpecialStageCsvWriter.Header, ssLines[0]);
                // Row 0 (F=17): blank input, full lowercase-hex state.
                AssertEx.Equal(
                    "0,0,0,25ab0300,44d8300,ffde,48,13c,7,4000,1800,c,a,0",
                    ssLines[1]);
                // Row 1 (F=18) consumed input row 17: Right|A -> 0x18.
                AssertEx.Equal(true, ssLines[2].StartsWith("1,18,0,"));
                // Row 2 (F=19) was flagged as a lag frame.
                AssertEx.Equal(true, ssLines[3].StartsWith("2,0,1,"));
                AssertContains(ss.MetadataJson,
                    "  \"trace_profile\": \"s1_special_stage\",\n");
                AssertContains(ss.MetadataJson,
                    "  \"special_stage_index\": 2,\n");
                AssertContains(ss.MetadataJson,
                    "  \"bk2_frame_offset\": 16,\n");
                AssertContains(ss.MetadataJson,
                    "  \"trace_frame_count\": 5,\n");
                AssertContains(ss.MetadataJson,
                    "  \"run_id\": \"test-run\",\n"
                    + "  \"fresh_load\": false,\n"
                    + "  \"segment_index\": 1\n");

                S1RunSegmentOutput seg2 = outputs[2];
                AssertEx.Equal("ghz2", seg2.DirToken);
                AssertEx.Equal(22, seg2.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(5, seg2.ManifestEntry.TraceFrameCount);
                AssertEx.Equal(2, seg2.ManifestEntry.Act);
                // Cross-segment tracker carry-over (spec §8): the routine
                // moved 0x02 -> 0x04 while the aux engine was dormant
                // inside the detour, so the return level's frame 0 diffs
                // against the pre-detour value — exactly the fixture ghz2
                // opening event.
                AssertContains(seg2.AuxStateJsonl,
                    "\"event\":\"routine_change\",\"from\":\"0x02\","
                    + "\"to\":\"0x04\"");

                RunManifestTransition giantRing = result.Transitions[0];
                AssertEx.Equal(0, giantRing.FromSegment);
                AssertEx.Equal(1, giantRing.ToSegment);
                AssertEx.Equal("giant_ring", giantRing.EntryKind);
                AssertEx.Equal(16, giantRing.ModeChangeBk2Frame);
                AssertEx.Equal(10, giantRing.RingsBefore ?? -1);
                AssertEx.Equal(0, giantRing.EmeraldsBefore ?? -1);
                AssertEx.Equal(false, giantRing.RingsAfter.HasValue);
                AssertEx.Equal(false, giantRing.EmeraldsAfter.HasValue);
                AssertEx.Equal(false, giantRing.SavedXPos.HasValue);
                AssertEx.Equal(
                    false, giantRing.SpecialBonusEntryFlag.HasValue);

                RunManifestTransition exit = result.Transitions[1];
                AssertEx.Equal(1, exit.FromSegment);
                AssertEx.Equal(2, exit.ToSegment);
                AssertEx.Equal("stage_exit", exit.EntryKind);
                AssertEx.Equal(22, exit.ModeChangeBk2Frame);
                // S1 carries the ring count through the SS round trip.
                AssertEx.Equal(9, exit.RingsAfter ?? -1);
                AssertEx.Equal(1, exit.EmeraldsAfter ?? -1);
                AssertEx.Equal(false, exit.RingsBefore.HasValue);
                AssertEx.Equal(false, exit.EmeraldsBefore.HasValue);

                AssertContains(result.RunManifestJson,
                    "  \"run_id\": \"test-run\",\n");
                AssertContains(result.RunManifestJson,
                    "  \"rom_checksum\": \"AFE05EEE\",\n");
                AssertContains(result.RunManifestJson,
                    "{\"dir\": \"ss\", \"kind\": \"special_stage\","
                    + " \"trace_profile\": \"s1_special_stage\","
                    + " \"bk2_frame_offset\": 16, \"trace_frame_count\": 5,"
                    + " \"zone_id\": 0, \"act\": 0,"
                    + " \"special_stage_index\": 2},");
                AssertContains(result.RunManifestJson,
                    "{\"from_segment\": 0, \"to_segment\": 1,"
                    + " \"entry_kind\": \"giant_ring\","
                    + " \"mode_change_bk2_frame\": 16,"
                    + " \"rings_before\": 10, \"emeralds_before\": 0},");
                AssertContains(result.RunManifestJson,
                    "{\"from_segment\": 1, \"to_segment\": 2,"
                    + " \"entry_kind\": \"stage_exit\","
                    + " \"mode_change_bk2_frame\": 22,"
                    + " \"rings_after\": 9, \"emeralds_after\": 1}");
            });
        }

        /// <summary>
        /// Two detours re-entering the SAME zone/act: level and ss tokens
        /// share one dir-count table, so the sequence is ghz1, ss, ghz1_2,
        /// ss_2, ghz1_3, with ss segment_index counting all finished
        /// segments. No run id — the transitions alone force the manifest,
        /// which then carries no run_id line (and neither does the ss
        /// metadata).
        /// </summary>
        private static void SuffixesRepeatDetoursAndLevelReEntries()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeS1Host(
                    (h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 6)
                    {
                        h.Ram[0xF600] = 0x10;
                        h.Ram[0xFE16] = 0;
                    }
                    if (frame == 9)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 12)
                    {
                        h.Ram[0xF600] = 0x10;
                        h.Ram[0xFE16] = 1;
                    }
                    if (frame == 15)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                });

                List<S1RunSegmentOutput> outputs;
                S1RunCaptureResult result = Capture(
                    movie, host, null, 0, out outputs);

                AssertEx.Equal(5, result.Segments.Count);
                AssertEx.Equal("ghz1", result.Segments[0].Dir);
                AssertEx.Equal("ss", result.Segments[1].Dir);
                AssertEx.Equal("ghz1_2", result.Segments[2].Dir);
                AssertEx.Equal("ss_2", result.Segments[3].Dir);
                AssertEx.Equal("ghz1_3", result.Segments[4].Dir);
                AssertEx.Equal(
                    0, result.Segments[1].SpecialStageIndex ?? -1);
                AssertEx.Equal(
                    1, result.Segments[3].SpecialStageIndex ?? -1);
                AssertContains(outputs[1].MetadataJson,
                    "  \"segment_index\": 1\n");
                AssertContains(outputs[3].MetadataJson,
                    "  \"segment_index\": 3\n");
                AssertEx.Equal(
                    false, outputs[1].MetadataJson.Contains("run_id"));

                AssertEx.Equal(4, result.Transitions.Count);
                for (var index = 0; index < 4; index++)
                {
                    AssertEx.Equal(
                        index, result.Transitions[index].FromSegment);
                    AssertEx.Equal(
                        index + 1, result.Transitions[index].ToSegment);
                }
                AssertEx.Equal(
                    "giant_ring", result.Transitions[0].EntryKind);
                AssertEx.Equal(
                    "stage_exit", result.Transitions[1].EntryKind);
                AssertEx.Equal(
                    "giant_ring", result.Transitions[2].EntryKind);
                AssertEx.Equal(
                    "stage_exit", result.Transitions[3].EntryKind);

                // Transitions occurred, so the manifest is emitted even
                // without OGGF_TRACE_RUN_ID — minus the run_id line.
                AssertEx.Equal(true, result.RunManifestJson != null);
                AssertEx.Equal(
                    false, result.RunManifestJson.Contains("run_id"));
            });
        }

        /// <summary>
        /// A movie ending while the detour is active must finalize the SS
        /// segment (never a bogus level entry) and still write the
        /// manifest: arm F=3, entry F=8, ss rows F=9-11, guard at F=12.
        /// </summary>
        private static void FinalizesSsSegmentWhenMovieEndsMidDetour()
        {
            WithMovie(Rows(12), movie =>
            {
                var host = new FakeS1Host(
                    (h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 8)
                    {
                        h.Ram[0xF600] = 0x10;
                        h.Ram[0xFE16] = 4;
                    }
                });

                List<S1RunSegmentOutput> outputs;
                S1RunCaptureResult result = Capture(
                    movie, host, null, 0, out outputs);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal("level", result.Segments[0].Kind);
                AssertEx.Equal(3, result.Segments[0].Bk2FrameOffset);
                AssertEx.Equal(4, result.Segments[0].TraceFrameCount);
                AssertEx.Equal(
                    "special_stage", result.Segments[1].Kind);
                AssertEx.Equal(8, result.Segments[1].Bk2FrameOffset);
                AssertEx.Equal(3, result.Segments[1].TraceFrameCount);
                AssertContains(outputs[1].MetadataJson,
                    "  \"trace_frame_count\": 3,\n");
                AssertEx.Equal(1, result.Transitions.Count);
                AssertEx.Equal(true, result.RunManifestJson != null);
                AssertContains(result.RunManifestJson, "\"special_stage\"");
            });
        }

        /// <summary>
        /// game_mode $10 with no armed level segment (a movie starting
        /// inside the special stage) must not create an ss segment or a
        /// from_segment=-1 transition; the later level arm records
        /// normally, and with no transitions and no run id the manifest
        /// stays suppressed.
        /// </summary>
        private static void NeverArmsSsWithoutStartedLevelSegment()
        {
            WithMovie(Rows(12), movie =>
            {
                var host = new FakeS1Host(
                    (h, frame) =>
                {
                    if (frame == 2)
                    {
                        h.Ram[0xF600] = 0x10;
                    }
                    if (frame == 6)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                });

                List<S1RunSegmentOutput> outputs;
                S1RunCaptureResult result = Capture(
                    movie, host, null, 0, out outputs);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal("ghz1", result.Segments[0].Dir);
                AssertEx.Equal(6, result.Segments[0].Bk2FrameOffset);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertEx.Equal(true, result.RunManifestJson == null);
            });
        }

        /// <summary>
        /// S1 delta versus the S2 runner: leaving level gameplay to a
        /// non-$10 mode (got-through card, death reload) finalizes the
        /// segment and RE-ARMS — the pass keeps recording the whole
        /// playthrough. Arm F=3, rows F=4-8, exit 0x8C at F=9, re-arm
        /// F=12, rows F=13-19, movie-done guard at F=20. Stage-free with
        /// no run id: the manifest gate suppresses run_manifest.json so
        /// the output layout matches the legacy recorder exactly.
        /// </summary>
        private static void RearmsAfterNonSsModeExit()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeS1Host(
                    (h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 9)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                    if (frame == 12)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xFE11] = 0x01;
                    }
                });

                List<S1RunSegmentOutput> outputs;
                S1RunCaptureResult result = Capture(
                    movie, host, null, 0, out outputs);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal("ghz1", result.Segments[0].Dir);
                AssertEx.Equal(3, result.Segments[0].Bk2FrameOffset);
                AssertEx.Equal(5, result.Segments[0].TraceFrameCount);
                AssertEx.Equal("ghz2", result.Segments[1].Dir);
                AssertEx.Equal(12, result.Segments[1].Bk2FrameOffset);
                AssertEx.Equal(7, result.Segments[1].TraceFrameCount);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertEx.Equal(true, result.RunManifestJson == null);
            });
        }

        /// <summary>
        /// OGGF_TRACE_RUN_ID forces manifest emission even for a
        /// detour-free pass (spec §1): same schedule as the re-arm test
        /// but with a run id, yielding a manifest with two level segments
        /// and an empty transitions array.
        /// </summary>
        private static void ForcesManifestForDetourFreeRunWithRunId()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeS1Host(
                    (h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 9)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                    if (frame == 12)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xFE11] = 0x01;
                    }
                });

                List<S1RunSegmentOutput> outputs;
                S1RunCaptureResult result = Capture(
                    movie, host, "forced", 0, out outputs);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertEx.Equal(true, result.RunManifestJson != null);
                AssertContains(result.RunManifestJson,
                    "  \"run_id\": \"forced\",\n");
                AssertEx.Equal(
                    true,
                    result.RunManifestJson.EndsWith(
                        "  \"transitions\": [\n  ]\n}\n"));
            });
        }

        /// <summary>
        /// The S1_STOP_AT_FRAME hard stop routes through the same run-end
        /// funnel: stopping at F=12 inside the detour finalizes the ss
        /// segment (arm F=3, entry F=8, ss rows F=9-11) with the movie
        /// still holding unconsumed rows.
        /// </summary>
        private static void HonorsStopAtFrameInsideDetour()
        {
            WithMovie(Rows(30), movie =>
            {
                var host = new FakeS1Host(
                    (h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 8)
                    {
                        h.Ram[0xF600] = 0x10;
                        h.Ram[0xFE16] = 5;
                    }
                });

                List<S1RunSegmentOutput> outputs;
                S1RunCaptureResult result = Capture(
                    movie, host, null, 12, out outputs);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal(
                    "special_stage", result.Segments[1].Kind);
                AssertEx.Equal(3, result.Segments[1].TraceFrameCount);
                AssertEx.Equal(
                    5, result.Segments[1].SpecialStageIndex ?? -1);
                AssertEx.Equal(true, result.RunManifestJson != null);
            });
        }

        private static S1RunCaptureResult Capture(
            Bk2Movie movie,
            IGpgxHost host,
            string runId,
            int stopAtFrame,
            out List<S1RunSegmentOutput> outputs)
        {
            var collected = new List<S1RunSegmentOutput>();
            S1RunCaptureResult result = S1RunCaptureRunner.Capture(
                movie,
                host,
                runId,
                "synthetic.bk2",
                "2026-07-24",
                S1CompleteRunMetadataWriter.LuaScriptVersion,
                stopAtFrame,
                collected.Add);
            outputs = collected;
            return result;
        }

        private static void AssertContains(
            string value,
            string expectedFragment)
        {
            if (value.IndexOf(expectedFragment, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected text to contain <" + expectedFragment + ">.");
            }
        }

        private static string[] Rows(int count)
        {
            var rows = new string[count];
            for (int index = 0; index < count; index++)
            {
                rows[index] = BlankRow;
            }
            return rows;
        }

        private static void WithMovie(
            IEnumerable<string> rows,
            Action<Bk2Movie> body)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "openggf-s1-run-" + Guid.NewGuid().ToString("N"));
            string path = Path.Combine(directory, "synthetic.bk2");
            Directory.CreateDirectory(directory);
            try
            {
                using (var stream = File.Create(path))
                using (var archive = new ZipArchive(
                    stream,
                    ZipArchiveMode.Create,
                    false))
                {
                    WriteEntry(
                        archive,
                        "Header.txt",
                        Fixture("ghz1-header.txt"));
                    WriteEntry(
                        archive,
                        "SyncSettings.json",
                        Fixture("ghz1-sync-settings.json"));
                    WriteEntry(
                        archive,
                        "Input Log.txt",
                        "[Input]\r\n"
                        + LogKey + "\r\n"
                        + string.Join("\r\n", rows)
                        + "\r\n[/Input]\r\n");
                }
                body(Bk2Reader.Read(path));
            }
            finally
            {
                Directory.Delete(directory, true);
            }
        }

        private static void WriteEntry(
            ZipArchive archive,
            string name,
            string content)
        {
            ZipArchiveEntry entry =
                archive.CreateEntry(name, CompressionLevel.NoCompression);
            using (Stream stream = entry.Open())
            using (var writer = new StreamWriter(
                stream,
                new UTF8Encoding(false)))
            {
                writer.Write(content);
            }
        }

        private static string Fixture(string name)
        {
            return File.ReadAllText(Path.Combine(
                AppDomain.CurrentDomain.BaseDirectory,
                "fixtures",
                name));
        }
    }
}
