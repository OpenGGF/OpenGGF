using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Synthetic-movie tests for the S2 run-mode stage-detour state machine:
    /// level -> ss -> level sequencing, per-segment naming/offsets/rows,
    /// transition records with RAM fields read at the specified frames, the
    /// movie-done guard (including mid-detour movie end and the effective
    /// movie length override), the run-end finalize routing, and the
    /// v9.13-s2 Block 1.5 title-card reload survival (death_restart vs
    /// level_advance kind selection, boundary/re-arm field sourcing,
    /// pending-transition discard, terminal-mode finalize).
    /// </summary>
    internal static class S2RunCaptureRunnerTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string BlankRow = "|..|........|........|";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner records a level-ss-level round trip",
                RecordsLevelSsLevelRoundTrip));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner numbers repeat detours and level arms",
                NumbersRepeatDetoursAndLevelArms));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner finalizes ss segment when movie ends mid-detour",
                FinalizesSsSegmentWhenMovieEndsMidDetour));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner never arms ss without a started level segment",
                NeverArmsSsWithoutStartedLevelSegment));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner ends the run on a non-level non-ss mode",
                EndsRunOnNonLevelNonSsMode));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner honors the effective movie length override",
                HonorsEffectiveMovieLengthOverride));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner survives a death restart reload and re-arms",
                SurvivesDeathRestartReloadAndRearms));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner classifies a changed zone-act reload as level advance",
                ClassifiesChangedZoneActReloadAsLevelAdvance));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner discards a pending reload when the run ends first",
                DiscardsPendingReloadWhenRunEndsBeforeRearm));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner ends the run on the continue screen while armed",
                EndsRunOnContinueScreenWhileArmed));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner emits the special-stage aux event stream",
                EmitsSpecialStageAuxEventStream));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner publishes mandatory audit manifest and"
                + " empty gap ledger",
                PublishesMandatoryAuditManifest));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner attributes callbacks on the terminal"
                + " mode advance to the run gap",
                AttributesTerminalBoundaryCallbacksToGap));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner publishes a trailing reload gap without"
                + " a later arm",
                PublishesTrailingReloadGap));
            tests.Add(new TestMain.TestCase(
                "S2RunCaptureRunner publication rejects scratch legacy"
                + " audit omission",
                PublicationRejectsScratchLegacyAuditOmission));
        }

        private static void PublicationRejectsScratchLegacyAuditOmission()
        {
            WithMovie(Rows(1), movie =>
            {
                AssertEx.Throws<ArgumentNullException>(
                    () => S2RunCaptureRunner.Capture(
                        movie,
                        new FakeRunHost(null),
                        "audit-run",
                        "synthetic.bk2",
                        "2026-07-30",
                        0,
                        new RunSegmentCollector(),
                        null),
                    "requires native load audit");
            });
        }

        private static void AttributesTerminalBoundaryCallbacksToGap()
        {
            byte[] rom = S2DynamicArtObserverTests.CreateRom();
            S2DynamicArtObserverTests.DefineLevelDplc(
                rom, 4, new[] { 0x0000 });
            WithMovie(Rows(6), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 2)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 4)
                    {
                        SubmitAndCompleteS2(h, 4);
                        h.Ram[0xF600] = 0x20;
                    }
                });
                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "boundary-gap", "synthetic.bk2",
                    "2026-07-30", 0, rom);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(
                    0,
                    Count(result.Segments[0].AuxStateJsonl,
                        "\"phase\":\"submitted\""));
                AssertEx.Equal(2, result.DynamicArtGapTransitions.Count);
                string submitted =
                    result.DynamicArtGapTransitions[0].Format();
                AssertContains(submitted, "\"phase\":\"submitted\"");
                AssertContains(
                    submitted,
                    "\"submission_origin\":\"run_gap\"");
                AssertContains(
                    submitted, "\"movie_logical_frame\":3");
                AssertContains(
                    result.DynamicArtGapTransitions[1].Format(),
                    "\"phase\":\"completed\"");
            });
        }

        private static void PublishesTrailingReloadGap()
        {
            byte[] rom = S2DynamicArtObserverTests.CreateRom();
            S2DynamicArtObserverTests.DefineLevelDplc(
                rom, 4, new[] { 0x0000 });
            WithMovie(Rows(7), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 2)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 4)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                    if (frame == 5)
                    {
                        SubmitAndCompleteS2(h, 4);
                    }
                });
                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "trailing-gap", "synthetic.bk2",
                    "2026-07-30", 0, rom);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(2, result.DynamicArtGapTransitions.Count);
                AssertContains(
                    result.RunManifestJson,
                    "\"dynamic_art_gap_transitions\": [");
            });
        }

        private static void SubmitAndCompleteS2(
            FakeRunHost host, int mappingFrame)
        {
            host.SetCpuRegister("M68K A0", S2Ram.PlayerBase);
            host.Ram[S2Ram.PlayerBase + S2Ram.OffMappingFrame] =
                (byte)mappingFrame;
            host.Ram[0xF766] = 0;
            host.FireExecuteCallback(0x1B848);

            host.SetCpuRegister("M68K D1", 0x50000);
            host.SetCpuRegister("M68K D2", 0xF000);
            host.SetCpuRegister("M68K D3", 0x10);
            host.SetU32(0xDCFC, 0xFFF000);
            host.FireExecuteCallback(0x144E);
            host.SetU32(0xDCFC, 0xFFF00E);
            host.FireExecuteCallback(0x14AA);
            host.FireExecuteCallback(0x1B89A);
            host.FireExecuteCallback(0x14AC);
        }

        private static void PublishesMandatoryAuditManifest()
        {
            WithMovie(Rows(8), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame >= 2)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                });
                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie,
                    host,
                    "audit-run",
                    "synthetic.bk2",
                    "2026-07-30",
                    0,
                    S2DynamicArtObserverTests.CreateRom());

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(
                    result.Segments[0].ManifestEntry.TraceFrameCount,
                    Count(result.Segments[0].AuxStateJsonl,
                        "\"event\":\"load_queue_state\""));
                AssertEx.Equal(
                    result.Segments[0].ManifestEntry.TraceFrameCount,
                    Count(result.Segments[0].AuxStateJsonl,
                        "\"event\":\"dynamic_art_transfer_state\""));
                AssertContains(
                    result.Segments[0].MetadataJson,
                    "\"dynamic_art_transfer_state_per_frame\"");
                AssertContains(result.RunManifestJson, "\"trace_schema\": 5");
                AssertContains(
                    result.RunManifestJson,
                    "\"dynamic_art_gap_transitions\": [");
                AssertEx.Equal(0, result.DynamicArtGapTransitions.Count);
                AssertContains(
                    S2SpecialStageMetadataWriter.Format(
                        0, 1, 1, "synthetic.bk2", "2026-07-30",
                        "audit-run", 1, true),
                    "\"dynamic_art_transfer_state_per_frame\"");
            });
        }

        /// <summary>
        /// Schedule (completed frame F): boot 1-4; arm at F=5 (EHZ act 1
        /// raw 0); level rows F=6-15 (10 rows); ss entry at F=16 with the
        /// starpost RAM fields set; ss rows F=17-21 (5 rows, one lag frame,
        /// one Start+jump input row); exit + same-frame re-arm at F=22 with
        /// the post-reload ring zeroing; level rows F=23-27; movie of 28
        /// rows fires the movie-done guard at F=28 (rows = 28 - 22 - 1).
        /// </summary>
        private static void RecordsLevelSsLevelRoundTrip()
        {
            string[] rows = Rows(28);
            rows[17] = "|..|....A..S|........|";     // ss row 1 input.
            WithMovie(rows, movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 5)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xFE10] = 0x00;           // EHZ
                        h.Ram[0xFE11] = 0x00;           // act raw 0
                        h.SetU32(0xF636, 0x11223344u);
                    }
                    if (frame == 16)
                    {
                        h.Ram[0xF600] = 0x10;
                        h.Ram[0xF7CD] = 1;              // bigring flag
                        h.SetU16(0xFE32, 100);          // saved x
                        h.SetU16(0xFE34, 50);           // saved y
                        h.Ram[0xFE30] = 1;              // last star post
                        h.SetU16(0xFE20, 7);            // rings before
                        h.Ram[0xFFB1] = 0;              // emeralds before
                        h.Ram[0xFE16] = 2;              // ss index
                        h.Ram[0xB000] = 0x09;           // Sonic ss slot
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
                        h.Ram[0xB000] = 0x00;
                        h.SetU16(0xFE20, 0);            // post-reload zeroing
                        h.Ram[0xFFB1] = 1;              // emeralds after
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "test-run", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(3, result.Segments.Count);
                AssertEx.Equal(2, result.Transitions.Count);

                RunSegmentOutput seg1 = result.Segments[0];
                AssertEx.Equal("seg1_ehz1", seg1.DirToken);
                AssertEx.Equal("level", seg1.ManifestEntry.Kind);
                AssertEx.Equal(
                    "gameplay_unlock", seg1.ManifestEntry.TraceProfile);
                AssertEx.Equal(5, seg1.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(10, seg1.ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, seg1.ManifestEntry.ZoneId);
                AssertEx.Equal(1, seg1.ManifestEntry.Act);
                string[] seg1Lines = seg1.PhysicsCsv.Split('\n');
                AssertEx.Equal(12, seg1Lines.Length);   // header+10+empty
                AssertEx.Equal(S2TraceCsvWriter.Header, seg1Lines[0]);
                AssertEx.Equal(true, seg1Lines[1].StartsWith("0000,"));
                AssertEx.Equal(true, seg1Lines[10].StartsWith("0009,"));
                AssertContains(seg1.MetadataJson,
                    "  \"gameplay_segment\": 0,\n");
                AssertContains(seg1.MetadataJson,
                    "  \"bk2_frame_offset\": 5,\n");
                AssertContains(seg1.MetadataJson,
                    "  \"trace_frame_count\": 10,\n");
                AssertContains(seg1.MetadataJson,
                    "  \"source_bk2\": \"synthetic.bk2\",\n"
                    + "  \"run_id\": \"test-run\",\n"
                    + "  \"segment_index\": 0,\n"
                    + "  \"rom_checksum\": \"\",\n");
                AssertContains(seg1.MetadataJson,
                    "  \"rng_seed\": \"0x11223344\",\n");

                RunSegmentOutput ss = result.Segments[1];
                AssertEx.Equal("ss", ss.DirToken);
                AssertEx.Equal("special_stage", ss.ManifestEntry.Kind);
                AssertEx.Equal(
                    "s2_special_stage", ss.ManifestEntry.TraceProfile);
                AssertEx.Equal(16, ss.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(5, ss.ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, ss.ManifestEntry.ZoneId);
                AssertEx.Equal(0, ss.ManifestEntry.Act);
                AssertEx.Equal(2, ss.ManifestEntry.SpecialStageIndex ?? -1);
                // v9.13-s2 §11.3: the ss aux stream carries the frame -1
                // pre-trace snapshot (all-zero here: nothing populated the
                // SS parameter RAM) and the first-row control_state from
                // the null seed; no further events fire because no tracked
                // state changes across the 5 rows.
                AssertEx.Equal(
                    "{\"frame\":-1,\"type\":\"state_snapshot\","
                    + "\"ring_requirement\":\"0x0000\","
                    + "\"current_level_layout\":\"0x00000000\","
                    + "\"initial_speed_factor\":\"0x0000\","
                    + "\"perfect_rings_left\":\"0x0000\"}\n"
                    + "{\"frame\":0,\"type\":\"control_state\","
                    + "\"started\":0}\n",
                    ss.AuxStateJsonl);
                string[] ssLines = ss.PhysicsCsv.Split('\n');
                AssertEx.Equal(7, ssLines.Length);      // header+5+empty
                AssertEx.Equal(S2SpecialStageCsvWriter.Header, ssLines[0]);
                // Row 0 (F=17): blank input; Sonic present with zeroed
                // fields (only the id byte was set).
                AssertEx.Equal(
                    "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,"
                    + "1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,"
                    + "0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0",
                    ssLines[1]);
                // Row 1 (F=18) consumed input row 17: A|Start -> 0x90.
                AssertEx.Equal(true, ssLines[2].StartsWith("1,90,0,0,"));
                // Row 2 (F=19) was flagged as a lag frame.
                AssertEx.Equal(true, ssLines[3].StartsWith("2,0,0,1,"));
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

                RunSegmentOutput seg2 = result.Segments[2];
                AssertEx.Equal("seg2_ehz1", seg2.DirToken);
                AssertEx.Equal(22, seg2.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(5, seg2.ManifestEntry.TraceFrameCount);
                AssertContains(seg2.MetadataJson,
                    "  \"segment_index\": 2,\n");
                // The return segment re-emits gameplay_start: the tracker
                // reset after the detour cleared the checkpoint dedup.
                AssertContains(seg2.AuxStateJsonl, "\"gameplay_start\"");

                RunManifestTransition starpost = result.Transitions[0];
                AssertEx.Equal(0, starpost.FromSegment);
                AssertEx.Equal(1, starpost.ToSegment);
                AssertEx.Equal("starpost_special", starpost.EntryKind);
                AssertEx.Equal(16, starpost.ModeChangeBk2Frame);
                AssertEx.Equal(1, starpost.SpecialBonusEntryFlag ?? -1);
                AssertEx.Equal(100, starpost.SavedXPos ?? -1);
                AssertEx.Equal(50, starpost.SavedYPos ?? -1);
                AssertEx.Equal(1, starpost.LastStarPostHit ?? -1);
                AssertEx.Equal(7, starpost.RingsBefore ?? -1);
                AssertEx.Equal(0, starpost.EmeraldsBefore ?? -1);
                AssertEx.Equal(false, starpost.RingsAfter.HasValue);
                AssertEx.Equal(false, starpost.EmeraldsAfter.HasValue);

                RunManifestTransition exit = result.Transitions[1];
                AssertEx.Equal(1, exit.FromSegment);
                AssertEx.Equal(2, exit.ToSegment);
                AssertEx.Equal("stage_exit", exit.EntryKind);
                AssertEx.Equal(22, exit.ModeChangeBk2Frame);
                AssertEx.Equal(0, exit.RingsAfter ?? -1);
                AssertEx.Equal(1, exit.EmeraldsAfter ?? -1);
                AssertEx.Equal(false, exit.SavedXPos.HasValue);
                AssertEx.Equal(false, exit.RingsBefore.HasValue);

                AssertContains(result.RunManifestJson,
                    "  \"run_id\": \"test-run\",\n");
                AssertContains(result.RunManifestJson,
                    "{\"dir\": \"ss\", \"kind\": \"special_stage\","
                    + " \"trace_profile\": \"s2_special_stage\","
                    + " \"bk2_frame_offset\": 16, \"trace_frame_count\": 5,"
                    + " \"zone_id\": 0, \"act\": 0,"
                    + " \"special_stage_index\": 2},");
                AssertContains(result.RunManifestJson,
                    "{\"from_segment\": 1, \"to_segment\": 2,"
                    + " \"entry_kind\": \"stage_exit\","
                    + " \"mode_change_bk2_frame\": 22,"
                    + " \"rings_after\": 0, \"emeralds_after\": 1}");
            });
        }

        /// <summary>
        /// Two detours: the ss dir tokens are "ss" then "ss_2" and the
        /// level tokens number by level arms only (the return level after
        /// the second detour is seg3_, not seg5_), with segment_index
        /// counting all finished segments.
        /// </summary>
        private static void NumbersRepeatDetoursAndLevelArms()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
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

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "roundtrip", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(5, result.Segments.Count);
                AssertEx.Equal("seg1_ehz1", result.Segments[0].DirToken);
                AssertEx.Equal("ss", result.Segments[1].DirToken);
                AssertEx.Equal("seg2_ehz1", result.Segments[2].DirToken);
                AssertEx.Equal("ss_2", result.Segments[3].DirToken);
                AssertEx.Equal("seg3_ehz1", result.Segments[4].DirToken);
                AssertEx.Equal(
                    0,
                    result.Segments[1].ManifestEntry.SpecialStageIndex ?? -1);
                AssertEx.Equal(
                    1,
                    result.Segments[3].ManifestEntry.SpecialStageIndex ?? -1);
                AssertContains(result.Segments[3].MetadataJson,
                    "  \"segment_index\": 3\n");
                AssertContains(result.Segments[4].MetadataJson,
                    "  \"segment_index\": 4,\n");

                AssertEx.Equal(4, result.Transitions.Count);
                for (var index = 0; index < 4; index++)
                {
                    AssertEx.Equal(
                        index, result.Transitions[index].FromSegment);
                    AssertEx.Equal(
                        index + 1, result.Transitions[index].ToSegment);
                }
                AssertEx.Equal(
                    "starpost_special", result.Transitions[0].EntryKind);
                AssertEx.Equal(
                    "stage_exit", result.Transitions[1].EntryKind);
                AssertEx.Equal(
                    "starpost_special", result.Transitions[2].EntryKind);
                AssertEx.Equal(
                    "stage_exit", result.Transitions[3].EntryKind);

                // v9.13-s2 §11.3: the per-detour aux state resets at each
                // ss arm, so ss_2 re-emits its own frame -1 snapshot and
                // first-row control_state.
                foreach (int ssIndex in new[] { 1, 3 })
                {
                    AssertContains(
                        result.Segments[ssIndex].AuxStateJsonl,
                        "{\"frame\":-1,\"type\":\"state_snapshot\",");
                    AssertContains(
                        result.Segments[ssIndex].AuxStateJsonl,
                        "{\"frame\":0,\"type\":\"control_state\","
                        + "\"started\":0}");
                }
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
                var host = new FakeRunHost((h, frame) =>
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

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "midway", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal("level", result.Segments[0].ManifestEntry.Kind);
                AssertEx.Equal(3, result.Segments[0].ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(4, result.Segments[0].ManifestEntry.TraceFrameCount);
                AssertEx.Equal(
                    "special_stage", result.Segments[1].ManifestEntry.Kind);
                AssertEx.Equal(8, result.Segments[1].ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(3, result.Segments[1].ManifestEntry.TraceFrameCount);
                AssertContains(result.Segments[1].MetadataJson,
                    "  \"trace_frame_count\": 3,\n");
                AssertEx.Equal(1, result.Transitions.Count);
                AssertContains(result.RunManifestJson, "\"special_stage\"");
            });
        }

        /// <summary>
        /// game_mode $10 with no armed level segment (a movie starting in
        /// or resetting into the special stage) must not create an ss
        /// segment or a from_segment=-1 transition; the later level arm
        /// records normally.
        /// </summary>
        private static void NeverArmsSsWithoutStartedLevelSegment()
        {
            WithMovie(Rows(12), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
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

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "coldstart", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal("seg1_ehz1", result.Segments[0].DirToken);
                AssertEx.Equal(6, result.Segments[0].ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(0, result.Transitions.Count);
            });
        }

        /// <summary>
        /// Leaving level gameplay to a mode other than $10 (results, game
        /// over, ...) is a real stop: the armed level segment finalizes and
        /// the manifest is written.
        /// </summary>
        private static void EndsRunOnNonLevelNonSsMode()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 9)
                    {
                        h.Ram[0xF600] = 0x00;   // Sega screen / reset
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "stopped", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(3, result.Segments[0].ManifestEntry.Bk2FrameOffset);
                // Rows F=4..8; the F=9 mode change records no partial row.
                AssertEx.Equal(5, result.Segments[0].ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertContains(result.RunManifestJson,
                    "  \"run_id\": \"stopped\",\n");
            });
        }

        /// <summary>
        /// The 4b guard uses the injected capture-session movie length when
        /// it is shorter than the BK2's own row count (spec §2 caveat: the
        /// canonical fixture's seg3 tail is not reproducible from the
        /// file-derived length). Same schedule as the round trip's seg1,
        /// with the guard pulled in from F=28 to F=12.
        /// </summary>
        private static void HonorsEffectiveMovieLengthOverride()
        {
            WithMovie(Rows(28), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 5)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "short", "synthetic.bk2",
                    "2026-07-24", 12);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(5, result.Segments[0].ManifestEntry.Bk2FrameOffset);
                // rows = effective length - offset - 1 = 12 - 5 - 1.
                AssertEx.Equal(6, result.Segments[0].ManifestEntry.TraceFrameCount);
            });
        }

        /// <summary>
        /// v9.13-s2 Block 1.5 (§11.2), death_restart branch: arm at F=3
        /// (EHZ act 1); level rows F=4-7; Game_Mode $8C at F=8 with
        /// Current_ZoneAndAct unchanged finalizes seg1 and captures the
        /// pending transition's boundary fields (rings/emeralds_before,
        /// saved_x/y_pos, last_star_post_hit — all read on the $8C frame:
        /// saved_x is overwritten before the re-arm to prove the sourcing
        /// moment); $8C holds through F=11; the $0C frame at F=12 re-arms
        /// seg2_ehz1 and completes the transition with the re-arm frame's
        /// rings/emeralds_after (the ROM zeroed rings on the reload).
        /// </summary>
        private static void SurvivesDeathRestartReloadAndRearms()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xFE10] = 0x00;           // EHZ
                        h.Ram[0xFE11] = 0x00;           // act raw 0
                    }
                    if (frame == 8)
                    {
                        h.Ram[0xF600] = 0x8C;
                        h.SetU16(0xFE20, 7);            // rings before
                        h.Ram[0xFFB1] = 3;              // emeralds before
                        h.SetU16(0xFE32, 100);          // saved x
                        h.SetU16(0xFE34, 50);           // saved y
                        h.Ram[0xFE30] = 2;              // last star post
                    }
                    if (frame == 12)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.SetU16(0xFE20, 0);            // post-reload zeroing
                        h.Ram[0xFFB1] = 4;              // emeralds after
                        h.SetU16(0xFE32, 999);          // must NOT be read
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "death-run", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(2, result.Segments.Count);
                RunSegmentOutput seg1 = result.Segments[0];
                AssertEx.Equal("seg1_ehz1", seg1.DirToken);
                AssertEx.Equal("level", seg1.ManifestEntry.Kind);
                AssertEx.Equal(3, seg1.ManifestEntry.Bk2FrameOffset);
                // Rows F=4..7; the F=8 boundary records no partial row.
                AssertEx.Equal(4, seg1.ManifestEntry.TraceFrameCount);
                RunSegmentOutput seg2 = result.Segments[1];
                AssertEx.Equal("seg2_ehz1", seg2.DirToken);
                AssertEx.Equal("level", seg2.ManifestEntry.Kind);
                AssertEx.Equal(12, seg2.ManifestEntry.Bk2FrameOffset);
                // rows = movie length - offset - 1 = 20 - 12 - 1.
                AssertEx.Equal(7, seg2.ManifestEntry.TraceFrameCount);
                AssertContains(seg2.MetadataJson,
                    "  \"segment_index\": 1,\n");
                // The re-arm rebuilt the aux engine: gameplay_start again.
                AssertContains(seg2.AuxStateJsonl, "\"gameplay_start\"");

                AssertEx.Equal(1, result.Transitions.Count);
                RunManifestTransition reload = result.Transitions[0];
                AssertEx.Equal(0, reload.FromSegment);
                AssertEx.Equal(1, reload.ToSegment);
                AssertEx.Equal("death_restart", reload.EntryKind);
                AssertEx.Equal(8, reload.ModeChangeBk2Frame);
                AssertEx.Equal(100, reload.SavedXPos ?? -1);
                AssertEx.Equal(50, reload.SavedYPos ?? -1);
                AssertEx.Equal(2, reload.LastStarPostHit ?? -1);
                AssertEx.Equal(7, reload.RingsBefore ?? -1);
                AssertEx.Equal(3, reload.EmeraldsBefore ?? -1);
                AssertEx.Equal(0, reload.RingsAfter ?? -1);
                AssertEx.Equal(4, reload.EmeraldsAfter ?? -1);
                AssertEx.Equal(
                    false, reload.SpecialBonusEntryFlag.HasValue);

                // Manifest renders the death_restart optional fields in the
                // fixed §6 order.
                AssertContains(result.RunManifestJson,
                    "{\"from_segment\": 0, \"to_segment\": 1,"
                    + " \"entry_kind\": \"death_restart\","
                    + " \"mode_change_bk2_frame\": 8,"
                    + " \"saved_x_pos\": 100, \"saved_y_pos\": 50,"
                    + " \"last_star_post_hit\": 2,"
                    + " \"rings_before\": 7, \"rings_after\": 0,"
                    + " \"emeralds_before\": 3, \"emeralds_after\": 4}");
            });
        }

        /// <summary>
        /// v9.13-s2 Block 1.5 (§11.2), level_advance branch: arm at F=3
        /// (EHZ act 1); Obj3A writes the destination act into
        /// Current_ZoneAndAct on a $0C tail frame (F=6) BEFORE the reload —
        /// classification still compares the $8C boundary value against the
        /// segment-START zone/act, so the tail write must not matter; the
        /// $8C boundary at F=8 differs from the start (act 2) ->
        /// level_advance, which omits saved_x/y_pos and last_star_post_hit
        /// even though they hold values; the re-arm at F=11 produces
        /// seg2_ehz2 from the boundary's new act.
        /// </summary>
        private static void ClassifiesChangedZoneActReloadAsLevelAdvance()
        {
            WithMovie(Rows(18), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xFE10] = 0x00;           // EHZ
                        h.Ram[0xFE11] = 0x00;           // act raw 0
                        h.SetU16(0xFE32, 100);          // stale saved x
                        h.SetU16(0xFE34, 50);           // stale saved y
                        h.Ram[0xFE30] = 1;              // stale star post
                    }
                    if (frame == 6)
                    {
                        h.Ram[0xFE11] = 0x01;           // Obj3A tail write
                    }
                    if (frame == 8)
                    {
                        h.Ram[0xF600] = 0x8C;
                        h.SetU16(0xFE20, 12);           // rings before
                        h.Ram[0xFFB1] = 1;              // emeralds before
                    }
                    if (frame == 11)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "advance-run", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal("seg1_ehz1", result.Segments[0].DirToken);
                AssertEx.Equal("seg2_ehz2", result.Segments[1].DirToken);
                AssertEx.Equal(
                    11, result.Segments[1].ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(2, result.Segments[1].ManifestEntry.Act);

                AssertEx.Equal(1, result.Transitions.Count);
                RunManifestTransition reload = result.Transitions[0];
                AssertEx.Equal(0, reload.FromSegment);
                AssertEx.Equal(1, reload.ToSegment);
                AssertEx.Equal("level_advance", reload.EntryKind);
                AssertEx.Equal(8, reload.ModeChangeBk2Frame);
                AssertEx.Equal(false, reload.SavedXPos.HasValue);
                AssertEx.Equal(false, reload.SavedYPos.HasValue);
                AssertEx.Equal(false, reload.LastStarPostHit.HasValue);
                AssertEx.Equal(
                    false, reload.SpecialBonusEntryFlag.HasValue);
                AssertEx.Equal(12, reload.RingsBefore ?? -1);
                AssertEx.Equal(1, reload.EmeraldsBefore ?? -1);
                AssertEx.Equal(12, reload.RingsAfter ?? -1);
                AssertEx.Equal(1, reload.EmeraldsAfter ?? -1);

                AssertContains(result.RunManifestJson,
                    "{\"from_segment\": 0, \"to_segment\": 1,"
                    + " \"entry_kind\": \"level_advance\","
                    + " \"mode_change_bk2_frame\": 8,"
                    + " \"rings_before\": 12, \"rings_after\": 12,"
                    + " \"emeralds_before\": 1, \"emeralds_after\": 1}");
            });
        }

        /// <summary>
        /// A run terminating mid-reload (movie exhausted while Game_Mode is
        /// still $8C, before the completing re-arm) discards the pending
        /// transition — the manifest carries the finalized segment and NO
        /// transition record whose to_segment would point past it.
        /// </summary>
        private static void DiscardsPendingReloadWhenRunEndsBeforeRearm()
        {
            WithMovie(Rows(10), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 7)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "midreload", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal("seg1_ehz1", result.Segments[0].DirToken);
                // Rows F=4..6; the F=7 boundary records no partial row.
                AssertEx.Equal(
                    3, result.Segments[0].ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertContains(result.RunManifestJson,
                    "  \"transitions\": [\n  ],\n"
                    + "  \"dynamic_art_gap_transitions\": [\n  ]\n}\n");
            });
        }

        /// <summary>
        /// With Block 1.5 intercepting $8C, the armed non-level branch
        /// fires only for genuinely terminal modes: the continue screen
        /// ($14, a direct write from $0C — no title-card bit) while armed
        /// ends the run with the segment finalized and the manifest
        /// written.
        /// </summary>
        private static void EndsRunOnContinueScreenWhileArmed()
        {
            WithMovie(Rows(16), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 9)
                    {
                        h.Ram[0xF600] = 0x14;   // ContinueScreen
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "continue", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(1, result.Segments.Count);
                // Rows F=4..8; the F=9 mode change records no partial row.
                AssertEx.Equal(
                    5, result.Segments[0].ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, result.Transitions.Count);
                AssertContains(result.RunManifestJson,
                    "  \"run_id\": \"continue\",\n");
            });
        }

        /// <summary>
        /// v9.13-s2 §11.3 SS aux surface, full-event pass: arm at F=3; ss
        /// entry at F=8 with populated SS parameter RAM (pre-trace snapshot
        /// sampled on the entry frame); row 0 at F=9 (control_state from
        /// the null seed, started 0); row 1 at F=10 flips
        /// SpecialStage_Started and a message-state byte (control_state
        /// then message_state, standalone order); rows 2-3 at F=11-12 are
        /// lag frames so last_nonlag holds at 1; F=12 also raises
        /// SS_Check_Rings_flag and spawns ObjID_SSResults in slot 2 —
        /// checkpoint (frame 3), stage_finished (frame=last non-lag 1,
        /// observed_frame 3), then results_started (slot 2). Exit at F=13
        /// re-arms the return level segment.
        /// </summary>
        private static void EmitsSpecialStageAuxEventStream()
        {
            WithMovie(Rows(18), movie =>
            {
                var host = new FakeRunHost((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 8)
                    {
                        h.Ram[0xF600] = 0x10;
                        h.SetU16(0xDB8C, 0x0032);       // ring requirement
                        h.SetU32(0xDB8E, 0x12345678u);  // level layout
                        h.SetU16(0xDB16, 0x0400);       // speed factor
                        h.SetU16(0xDB9A, 0x0032);       // perfect rings
                    }
                    if (frame == 10)
                    {
                        h.Ram[0xDB23] = 1;              // SS started
                        h.Ram[0xDBA7] = 0x0A;           // trigger rings
                    }
                    if (frame == 11)
                    {
                        h.IsLagged = true;
                    }
                    if (frame == 12)
                    {
                        h.Ram[0xDB86] = 0x01;           // check rings flag
                        h.Ram[0xB000 + 2 * 0x40] = 0x6F; // SS results obj
                    }
                    if (frame == 13)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.IsLagged = false;
                    }
                });

                CollectedRunCapture result = CollectedRunCapture.CaptureS2(
                    movie, host, "ss-aux", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(3, result.Segments.Count);
                RunSegmentOutput ss = result.Segments[1];
                AssertEx.Equal("ss", ss.DirToken);
                AssertEx.Equal(4, ss.ManifestEntry.TraceFrameCount);
                AssertEx.Equal(
                    "{\"frame\":-1,\"type\":\"state_snapshot\","
                    + "\"ring_requirement\":\"0x0032\","
                    + "\"current_level_layout\":\"0x12345678\","
                    + "\"initial_speed_factor\":\"0x0400\","
                    + "\"perfect_rings_left\":\"0x0032\"}\n"
                    + "{\"frame\":0,\"type\":\"control_state\","
                    + "\"started\":0}\n"
                    + "{\"frame\":1,\"type\":\"control_state\","
                    + "\"started\":1}\n"
                    + "{\"frame\":1,\"type\":\"message_state\","
                    + "\"hide_rings_to_go\":\"0x00\","
                    + "\"trigger_rings_to_go\":\"0x0a\","
                    + "\"no_rings_togo_lifetime\":\"0x0000\"}\n"
                    + "{\"frame\":3,\"type\":\"checkpoint\","
                    + "\"check_rings_flag\":\"0x01\"}\n"
                    + "{\"frame\":1,\"observed_frame\":3,"
                    + "\"type\":\"stage_finished\","
                    + "\"check_rings_flag\":\"0x01\"}\n"
                    + "{\"frame\":3,\"type\":\"results_started\","
                    + "\"slot\":2}\n",
                    ss.AuxStateJsonl);
            });
        }

        private static void AssertContains(
            string value,
            string expectedFragment)
        {
            if (value.IndexOf(
                expectedFragment,
                StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected text to contain <" + expectedFragment + ">.");
            }
        }

        private static int Count(string value, string needle)
        {
            int count = 0;
            int index = 0;
            while ((index = value.IndexOf(
                needle, index, StringComparison.Ordinal)) >= 0)
            {
                count++;
                index += needle.Length;
            }
            return count;
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
                "openggf-s2-run-" + Guid.NewGuid().ToString("N"));
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

        /// <summary>
        /// Fake host whose Advance() stamps the completed frame into vfc
        /// (0xFE04) and Sonic's position words, then runs the per-advance
        /// script with the host itself so scripts can drive RAM and the
        /// lag flag by completed-frame number.
        /// </summary>
        internal sealed class FakeRunHost : IGpgxHost, ICpuRegisterReader
        {
            private readonly Action<FakeRunHost, int> onAdvance;
            private readonly Dictionary<uint, Action> executeCallbacks =
                new Dictionary<uint, Action>();
            private readonly Dictionary<string, uint> registers =
                new Dictionary<string, uint>(StringComparer.Ordinal);

            public FakeRunHost(Action<FakeRunHost, int> onAdvance)
            {
                this.onAdvance = onAdvance;
                Ram = new byte[0x10000];
            }

            public byte[] Ram { get; private set; }
            public int CompletedFrame { get; private set; }
            public bool IsLagged { get; set; }
            public int LagCount { get; set; }

            public void ClearButtons()
            {
            }

            public void SetButton(string name, bool pressed)
            {
            }

            public IDisposable RegisterExecuteCallback(
                uint address, Action callback)
            {
                executeCallbacks[address] = callback;
                return NoOpCallbackRegistration.Instance;
            }

            public void FireExecuteCallback(uint address)
            {
                Action callback;
                if (!executeCallbacks.TryGetValue(address, out callback))
                {
                    throw new InvalidOperationException(
                        "No execute callback is registered at 0x"
                        + address.ToString("X") + ".");
                }
                callback();
            }

            public void Advance()
            {
                CompletedFrame++;
                SetU16(0xFE04, (ushort)CompletedFrame);
                SetU16(0xB008, (ushort)(0x0100 + CompletedFrame));
                SetU16(0xB00C, (ushort)(0x0300 + CompletedFrame));
                if (onAdvance != null)
                {
                    onAdvance(this, CompletedFrame);
                }
            }

            public byte ReadMainRamByte(int offset)
            {
                return Ram[offset];
            }

            public uint ReadCpuRegister(string name)
            {
                uint value;
                return registers.TryGetValue(name, out value) ? value : 0;
            }

            public void SetCpuRegister(string name, uint value)
            {
                registers[name] = value;
            }

            public void Dispose()
            {
            }

            public void SetU16(int offset, ushort value)
            {
                Ram[offset] = (byte)(value >> 8);
                Ram[offset + 1] = (byte)value;
            }

            public void SetU32(int offset, uint value)
            {
                Ram[offset] = (byte)(value >> 24);
                Ram[offset + 1] = (byte)(value >> 16);
                Ram[offset + 2] = (byte)(value >> 8);
                Ram[offset + 3] = (byte)value;
            }
        }
    }
}
