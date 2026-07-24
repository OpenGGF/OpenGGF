using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Synthetic-movie tests for the S2 dual-profile capture runner: plain
    /// gameplay_unlock detection, level_gated_reset_aware segment counting /
    /// EHZ-bootstrap skip and discard semantics, movie-end folding, and the
    /// finalize-time metadata derivation.
    /// </summary>
    internal static class S2TraceCaptureRunnerTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string BlankRow = "|..|........|........|";

        private const string EmptyCharacterBlock =
            "0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner captures byte-exact gameplay_unlock output",
                CapturesByteExactGameplayUnlockOutput));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner waits for S2 move_lock release",
                WaitsForS2MoveLockRelease));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner does not discard EHZ under gameplay_unlock",
                DoesNotDiscardEhzUnderGameplayUnlock));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner discards EHZ bootstrap when reset-aware",
                DiscardsEhzBootstrapWhenResetAware));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner skips EHZ segment without counting when reset-aware",
                SkipsEhzSegmentWithoutCountingWhenResetAware));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner counts EHZ segment under gameplay_unlock",
                CountsEhzSegmentUnderGameplayUnlock));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner fails clearly when target segment never reached",
                FailsClearlyWhenTargetSegmentNeverReached));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner finishes on movie exhaustion with CNZ gate armed",
                FinishesOnMovieExhaustionWithCnzGateArmed));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner discards EHZ bootstrap leaving level mode on final movie frame",
                DiscardsEhzBootstrapLeavingLevelModeOnFinalMovieFrame));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner sees sidekick appearing on final unrecorded frame",
                SeesSidekickAppearingOnFinalUnrecordedFrame));
            tests.Add(new TestMain.TestCase(
                "S2TraceCaptureRunner does not arm on the movie's final frame",
                DoesNotArmOnMoviesFinalFrame));
        }

        private static void CapturesByteExactGameplayUnlockOutput()
        {
            WithMovie(
                new[]
                {
                    BlankRow,
                    BlankRow,
                    BlankRow,
                    "|..|...R....|........|",
                    "|..|.D...B..|........|",
                    BlankRow,
                    // Final input row: consumed and emulated, but its
                    // frame is never recorded (Lua FINISHED parity).
                    BlankRow
                },
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 3)
                        {
                            ram.SetByte(0xF600, 0x0C);
                            ram.SetByte(0xFE10, 0x0F);      // ARZ
                            ram.SetU32(0xF636, 0x12345678u);
                        }
                        if (completedFrame == 4)
                        {
                            // Post-detection mutations must NOT leak into
                            // the start-captured metadata values.
                            ram.SetU32(0xF636, 0xAABBCCDDu);
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "gameplay_unlock",
                        0,
                        "synthetic.bk2",
                        "2026-07-13",
                        physics,
                        aux,
                        metadata);

                    AssertEx.Equal(3, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    AssertEx.Equal(0, result.GameplaySegment);
                    AssertEx.Equal(7, host.AdvanceCount);

                    string playerTail =
                        ",0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00,"
                        + EmptyCharacterBlock + "\n";
                    AssertEx.Equal(
                        S2TraceCsvWriter.Header + "\n"
                        + "0000,0008,0000,0000,0000,0004,0000,0000,1,0104,0304"
                        + playerTail
                        + "0001,0012,0000,0000,0000,0005,0000,0000,1,0105,0305"
                        + playerTail
                        + "0002,0000,0000,0000,0000,0006,0000,0000,1,0106,0306"
                        + playerTail,
                        physics.ToString());

                    string zeros = ZeroList(64);
                    AssertEx.Equal(
                        "{\"frame\":-1,\"vfc\":3,"
                        + "\"event\":\"player_history_snapshot\","
                        + "\"history_pos\":0,"
                        + "\"x_history\":[" + zeros + "],"
                        + "\"y_history\":[" + zeros + "],"
                        + "\"input_history\":[" + zeros + "],"
                        + "\"status_history\":[" + zeros + "]}\n"
                        + "{\"frame\":-1,\"vfc\":3,"
                        + "\"event\":\"cpu_state_snapshot\","
                        + "\"character\":\"tails\",\"control_counter\":0,"
                        + "\"respawn_counter\":0,\"cpu_routine\":0,"
                        + "\"target_x\":\"0x0000\",\"target_y\":\"0x0000\","
                        + "\"interact_id\":\"0x00\",\"jumping\":0}\n"
                        + ZoneActLine(0)
                        + "{\"frame\":0,\"event\":\"checkpoint\","
                        + "\"name\":\"gameplay_start\",\"actual_zone_id\":15,"
                        + "\"engine_zone_id\":2,\"actual_act\":0,"
                        + "\"apparent_act\":0,\"game_mode\":12}\n"
                        // Frame 0: zone_act_state deduped against the
                        // arm-time emission; snapshot gate skipped (both
                        // character ids are 0); cursor_state always fires
                        // on the first recorded frame.
                        + CpuStateLine(0, 4)
                        + "{\"frame\":0,\"vfc\":4,\"event\":\"cursor_state\","
                        + "\"opl_screen\":\"0x0000\","
                        + "\"fwd_ptr\":\"0x00000000\","
                        + "\"bwd_ptr\":\"0x00000000\","
                        + "\"fwd_ctr\":0,\"bwd_ctr\":0,\"dir\":\"R\"}\n"
                        + ZoneActLine(1)
                        + CpuStateLine(1, 5)
                        + ZoneActLine(2)
                        + CpuStateLine(2, 6),
                        aux.ToString());

                    AssertEx.Equal(
                        "{\n"
                        + "  \"game\": \"s2\",\n"
                        + "  \"zone\": \"arz\",\n"
                        + "  \"zone_id\": 2,\n"
                        + "  \"rom_zone_id\": 15,\n"
                        + "  \"act\": 1,\n"
                        + "  \"gameplay_segment\": 0,\n"
                        + "  \"bk2_frame_offset\": 3,\n"
                        + "  \"trace_frame_count\": 3,\n"
                        + "  \"start_x\": \"0x0103\",\n"
                        + "  \"start_y\": \"0x0303\",\n"
                        + "  \"characters\": [\"sonic\"],\n"
                        + "  \"main_character\": \"sonic\",\n"
                        + "  \"sidekicks\": [],\n"
                        + "  \"rng_seed\": \"0x12345678\",\n"
                        + "  \"recording_date\": \"2026-07-13\",\n"
                        + "  \"lua_script_version\": \"9.13-s2\",\n"
                        + "  \"trace_schema\": 9,\n"
                        + "  \"csv_version\": 7,\n"
                        + "  \"aux_schema_extras\": "
                        + "[\"cnz_slot_machine_state_per_frame\", "
                        + "\"cpu_state_per_frame\"],\n"
                        + "  \"trace_profile\": \"gameplay_unlock\",\n"
                        + "  \"bizhawk_version\": \"2.11\",\n"
                        + "  \"genesis_core\": \"Genplus-gx\",\n"
                        + "  \"route\": \"arz\",\n"
                        + "  \"source_bk2\": \"synthetic.bk2\",\n"
                        + "  \"rom_checksum\": \"\",\n"
                        + "  \"notes\": \"\"\n"
                        + "}\n",
                        metadata.ToString());
                });
        }

        private static void WaitsForS2MoveLockRelease()
        {
            WithMovie(
                Rows(8),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);
                        }
                        if (completedFrame == 4)
                        {
                            ram.SetU16(0xB02E, 0);
                        }
                    });
                    // S2 control lock is the move_lock word at 0xB02E (not
                    // S1's +0x3E).
                    host.Ram.SetU16(0xB02E, 2);

                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "gameplay_unlock",
                        0,
                        "synthetic.bk2",
                        "2026-07-13",
                        new StringWriter(),
                        new StringWriter(),
                        metadata);

                    AssertEx.Equal(4, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"bk2_frame_offset\": 4,\n"));
                });
        }

        private static void DoesNotDiscardEhzUnderGameplayUnlock()
        {
            WithMovie(
                Rows(10),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);  // Zone byte 0 = EHZ.
                        }
                        if (completedFrame == 4)
                        {
                            ram.SetByte(0xF600, 0x00);
                        }
                    });

                    var physics = new StringWriter();
                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "gameplay_unlock",
                        0,
                        "synthetic.bk2",
                        "2026-07-13",
                        physics,
                        new StringWriter(),
                        metadata);

                    // Leaving level mode finalizes an EHZ recording under
                    // gameplay_unlock — no discard, no partial row for the
                    // exit frame.
                    AssertEx.Equal(1, result.Bk2FrameOffset);
                    AssertEx.Equal(2, result.TraceFrameCount);
                    AssertEx.Equal(4, host.AdvanceCount);
                    string[] physicsLines = physics.ToString().Split('\n');
                    AssertEx.Equal(4, physicsLines.Length);
                    AssertEx.Equal(true, physicsLines[1].StartsWith("0000,"));
                    AssertEx.Equal(true, physicsLines[2].StartsWith("0001,"));
                    AssertEx.Equal(string.Empty, physicsLines[3]);
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains("  \"zone\": \"ehz\",\n"));
                });
        }

        private static void DiscardsEhzBootstrapWhenResetAware()
        {
            WithMovie(
                Rows(12),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);  // Zone 0 = EHZ.
                        }
                        if (completedFrame == 2)
                        {
                            // Sidekick present only during the DISCARDED
                            // bootstrap recording: recorded_sidekick_present
                            // must stay sticky across the discard.
                            ram.SetByte(0xB040, 0x02);
                        }
                        if (completedFrame == 4)
                        {
                            ram.SetByte(0xF600, 0x00);
                            ram.SetByte(0xB040, 0x00);
                        }
                        if (completedFrame == 6)
                        {
                            ram.SetByte(0xF600, 0x0C);
                            ram.SetByte(0xFE10, 0x0F);  // ARZ
                        }
                        if (completedFrame == 9)
                        {
                            ram.SetByte(0xF600, 0x00);
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "level_gated_reset_aware",
                        0,
                        "synthetic.bk2",
                        "2026-07-13",
                        physics,
                        aux,
                        metadata);

                    // Only the post-discard ARZ segment survives.
                    AssertEx.Equal(6, result.Bk2FrameOffset);
                    AssertEx.Equal(2, result.TraceFrameCount);
                    AssertEx.Equal(0, result.GameplaySegment);

                    // The discarded segment's bytes are gone: one header,
                    // rows starting at trace frame 0 with the second arm's
                    // positions, one arm block (vfc 6, the re-arm frame).
                    string[] physicsLines = physics.ToString().Split('\n');
                    AssertEx.Equal(4, physicsLines.Length);
                    AssertEx.Equal(S2TraceCsvWriter.Header, physicsLines[0]);
                    AssertEx.Equal(
                        true, physicsLines[1].StartsWith("0000,0000,"));
                    AssertEx.Equal(
                        true, physicsLines[1].Contains(",1,0107,0307,"));
                    AssertEx.Equal(
                        1,
                        CountOf(aux.ToString(), "player_history_snapshot"));
                    AssertEx.Equal(
                        true,
                        aux.ToString().StartsWith("{\"frame\":-1,\"vfc\":6,"));
                    AssertEx.Equal(
                        false, aux.ToString().Contains("\"vfc\":1,"));

                    // gameplay_segment stays 0 (the discard never counts),
                    // the metadata is the ARZ segment's, and the sticky
                    // sidekick presence survives the discard.
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains("  \"zone\": \"arz\",\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"gameplay_segment\": 0,\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"characters\": [\"sonic\", \"tails\"],\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"sidekicks\": [\"tails\"],\n"));
                });
        }

        private static void SkipsEhzSegmentWithoutCountingWhenResetAware()
        {
            WithMovie(
                Rows(16),
                movie =>
                {
                    var host = new ScriptedHost(SegmentScript);

                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "level_gated_reset_aware",
                        1,
                        "synthetic.bk2",
                        "2026-07-13",
                        new StringWriter(),
                        new StringWriter(),
                        metadata);

                    // EHZ bootstrap skipped WITHOUT counting; MCZ skipped
                    // and counted (index 0 -> 1); ARZ records as segment 1.
                    AssertEx.Equal(10, result.Bk2FrameOffset);
                    AssertEx.Equal(2, result.TraceFrameCount);
                    AssertEx.Equal(1, result.GameplaySegment);
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains("  \"zone\": \"arz\",\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"gameplay_segment\": 1,\n"));
                });
        }

        private static void CountsEhzSegmentUnderGameplayUnlock()
        {
            WithMovie(
                Rows(16),
                movie =>
                {
                    var host = new ScriptedHost(SegmentScript);

                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "gameplay_unlock",
                        1,
                        "synthetic.bk2",
                        "2026-07-13",
                        new StringWriter(),
                        new StringWriter(),
                        metadata);

                    // Without reset-awareness the EHZ skip IS counted
                    // (index 0 -> 1), so the MCZ segment records as
                    // segment 1.
                    AssertEx.Equal(6, result.Bk2FrameOffset);
                    AssertEx.Equal(1, result.TraceFrameCount);
                    AssertEx.Equal(1, result.GameplaySegment);
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains("  \"zone\": \"mcz\",\n"));
                });
        }

        /// <summary>
        /// Three gameplay segments: EHZ (frames 2-3), MCZ (frames 6-7), ARZ
        /// (frames 10-12), separated by non-level frames.
        /// </summary>
        private static void SegmentScript(RamAccess ram, int completedFrame)
        {
            if (completedFrame == 2)
            {
                ram.SetByte(0xF600, 0x0C);  // Zone 0 = EHZ.
            }
            if (completedFrame == 4)
            {
                ram.SetByte(0xF600, 0x00);
            }
            if (completedFrame == 6)
            {
                ram.SetByte(0xF600, 0x0C);
                ram.SetByte(0xFE10, 0x0B);  // MCZ
            }
            if (completedFrame == 8)
            {
                ram.SetByte(0xF600, 0x00);
            }
            if (completedFrame == 10)
            {
                ram.SetByte(0xF600, 0x0C);
                ram.SetByte(0xFE10, 0x0F);  // ARZ
            }
            if (completedFrame == 13)
            {
                ram.SetByte(0xF600, 0x00);
            }
        }

        private static void FailsClearlyWhenTargetSegmentNeverReached()
        {
            WithMovie(
                Rows(6),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);
                            ram.SetByte(0xFE10, 0x0F);
                        }
                        if (completedFrame == 3)
                        {
                            ram.SetByte(0xF600, 0x00);
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    AssertEx.Throws<InvalidDataException>(
                        () => S2TraceCaptureRunner.Capture(
                            movie,
                            host,
                            "level_gated_reset_aware",
                            2,
                            "synthetic.bk2",
                            "2026-07-13",
                            physics,
                            aux,
                            metadata),
                        "Target gameplay segment 2 never became recordable");
                    AssertEx.Equal(6, host.AdvanceCount);
                    AssertEx.Equal(string.Empty, physics.ToString());
                    AssertEx.Equal(string.Empty, aux.ToString());
                    AssertEx.Equal(string.Empty, metadata.ToString());
                });
        }

        private static void FinishesOnMovieExhaustionWithCnzGateArmed()
        {
            WithMovie(
                Rows(5),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);
                            ram.SetByte(0xFE10, 0x0C);  // CNZ
                        }
                    });

                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "gameplay_unlock",
                        0,
                        "synthetic.bk2",
                        "2026-07-13",
                        new StringWriter(),
                        aux,
                        metadata);

                    // Lua FINISHED parity: with offset 1 and 5 input rows,
                    // the final input row IS consumed and its frame
                    // emulated, but never recorded; trace rows 0-2 are
                    // recorded.
                    AssertEx.Equal(1, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    AssertEx.Equal(5, host.AdvanceCount);

                    // Arming in CNZ (start_rom_zone_id 0x0C) turns on the
                    // per-frame slot machine diagnostic.
                    AssertEx.Equal(
                        3,
                        CountOf(aux.ToString(), "cnz_slot_machine_state"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains("  \"zone\": \"cnz\",\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"trace_frame_count\": 3,\n"));
                });
        }

        /// <summary>
        /// Lua stop-condition ordering (§3.5): the frame fed by the movie's
        /// FINAL input row is emulated, and the mode-exit check runs on its
        /// state BEFORE the FINISHED stop. If that frame takes game_mode off
        /// 0x0C during an armed reset-aware EHZ recording, the recording is
        /// DISCARDED (the Lua deletes the files and the run ends "never
        /// became recordable") — it must NOT be published.
        /// </summary>
        private static void DiscardsEhzBootstrapLeavingLevelModeOnFinalMovieFrame()
        {
            WithMovie(
                Rows(6),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);  // Zone 0 = EHZ.
                        }
                        if (completedFrame == 6)
                        {
                            // Level mode is left exactly on the frame fed
                            // by the movie's final input row.
                            ram.SetByte(0xF600, 0x00);
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    AssertEx.Throws<InvalidDataException>(
                        () => S2TraceCaptureRunner.Capture(
                            movie,
                            host,
                            "level_gated_reset_aware",
                            0,
                            "synthetic.bk2",
                            "2026-07-13",
                            physics,
                            aux,
                            metadata),
                        "Target gameplay segment 0 never became recordable");
                    AssertEx.Equal(6, host.AdvanceCount);
                    AssertEx.Equal(string.Empty, physics.ToString());
                    AssertEx.Equal(string.Empty, aux.ToString());
                    AssertEx.Equal(string.Empty, metadata.ToString());
                });
        }

        /// <summary>
        /// The finalize-time live sidekick read happens AFTER the frame fed
        /// by the movie's final input row was emulated (Lua write_metadata
        /// runs post-advance), so a sidekick id byte first becoming nonzero
        /// on that final unrecorded frame is still reflected in metadata.
        /// </summary>
        private static void SeesSidekickAppearingOnFinalUnrecordedFrame()
        {
            WithMovie(
                Rows(5),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);
                            ram.SetByte(0xFE10, 0x0F);  // ARZ
                        }
                        if (completedFrame == 5)
                        {
                            // Sidekick appears only on the final,
                            // unrecorded frame.
                            ram.SetByte(0xB040, 0x02);
                        }
                    });

                    var metadata = new StringWriter();
                    S2TraceCaptureResult result = S2TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "gameplay_unlock",
                        0,
                        "synthetic.bk2",
                        "2026-07-13",
                        new StringWriter(),
                        new StringWriter(),
                        metadata);

                    AssertEx.Equal(1, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    AssertEx.Equal(5, host.AdvanceCount);
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"characters\": [\"sonic\", \"tails\"],\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"sidekicks\": [\"tails\"],\n"));
                });
        }

        /// <summary>
        /// The Lua's pre-arm FINISHED check runs BEFORE the arm predicate,
        /// so a movie whose final input row's frame is the first armable
        /// frame ends "never became recordable" — it must not arm and then
        /// publish an empty trace.
        /// </summary>
        private static void DoesNotArmOnMoviesFinalFrame()
        {
            WithMovie(
                Rows(4),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 4)
                        {
                            ram.SetByte(0xF600, 0x0C);
                            ram.SetByte(0xFE10, 0x0F);  // ARZ
                        }
                    });

                    var physics = new StringWriter();
                    var metadata = new StringWriter();
                    AssertEx.Throws<InvalidDataException>(
                        () => S2TraceCaptureRunner.Capture(
                            movie,
                            host,
                            "gameplay_unlock",
                            0,
                            "synthetic.bk2",
                            "2026-07-13",
                            physics,
                            new StringWriter(),
                            metadata),
                        "Target gameplay segment 0 never became recordable");
                    AssertEx.Equal(4, host.AdvanceCount);
                    AssertEx.Equal(string.Empty, physics.ToString());
                    AssertEx.Equal(string.Empty, metadata.ToString());
                });
        }

        private static string ZoneActLine(int frame)
        {
            return "{\"frame\":" + frame
                + ",\"event\":\"zone_act_state\",\"actual_zone_id\":15,"
                + "\"engine_zone_id\":2,\"actual_act\":0,\"apparent_act\":0,"
                + "\"game_mode\":12}\n";
        }

        private static string CpuStateLine(int frame, int vfc)
        {
            return "{\"frame\":" + frame + ",\"vfc\":" + vfc
                + ",\"event\":\"cpu_state\",\"character\":\"tails\","
                + "\"interact\":\"0x0000\",\"idle_timer\":0,"
                + "\"flight_timer\":0,\"cpu_routine\":0,"
                + "\"target_x\":\"0x0000\",\"target_y\":\"0x0000\","
                + "\"auto_fly_timer\":0,\"auto_jump_flag\":0,"
                + "\"ctrl2_held\":\"0x00\",\"ctrl2_pressed\":\"0x00\","
                + "\"ctrl2_raw_held\":\"0x00\",\"ctrl1_logical\":\"0x0000\","
                + "\"pos_table_index\":\"0x00\",\"delayed_index\":\"0xBC\","
                + "\"delayed_x\":\"0x0000\",\"delayed_y\":\"0x0000\","
                + "\"delayed_input\":\"0x0000\",\"delayed_status\":\"0x00\","
                + "\"tails_status\":\"0x00\",\"tails_interact\":\"0x00\","
                + "\"tails_inertia\":\"0x0000\"}\n";
        }

        private static string ZeroList(int count)
        {
            var list = new StringBuilder();
            for (int index = 0; index < count; index++)
            {
                if (index > 0)
                {
                    list.Append(',');
                }
                list.Append('0');
            }
            return list.ToString();
        }

        private static int CountOf(string haystack, string needle)
        {
            int count = 0;
            int index = 0;
            while ((index = haystack.IndexOf(
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
                "openggf-s2-trace-" + Guid.NewGuid().ToString("N"));
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
        /// Fake host whose Advance() mutates RAM: it stamps the completed
        /// frame into vfc (0xFE04) and Sonic's S2 position words (0xB008 /
        /// 0xB00C become 0x0100 + frame / 0x0300 + frame), then runs the
        /// per-advance script.
        /// </summary>
        private sealed class ScriptedHost : IGpgxHost
        {
            private readonly Action<RamAccess, int> onAdvance;

            public ScriptedHost(Action<RamAccess, int> onAdvance)
            {
                this.onAdvance = onAdvance;
                Ram = new RamAccess();
            }

            public RamAccess Ram { get; private set; }
            public int CompletedFrame { get; private set; }

            public bool IsLagged
            {
                get { return false; }
            }

            public int LagCount
            {
                get { return 0; }
            }
            public int AdvanceCount { get; private set; }

            public void ClearButtons()
            {
            }

            public void SetButton(string name, bool pressed)
            {
            }

            public void Advance()
            {
                AdvanceCount++;
                CompletedFrame++;
                Ram.SetU16(0xFE04, (ushort)CompletedFrame);
                Ram.SetU16(0xB008, (ushort)(0x0100 + CompletedFrame));
                Ram.SetU16(0xB00C, (ushort)(0x0300 + CompletedFrame));
                if (onAdvance != null)
                {
                    onAdvance(Ram, CompletedFrame);
                }
            }

            public byte ReadMainRamByte(int offset)
            {
                return Ram.GetByte(offset);
            }

            public void Dispose()
            {
            }
        }

        internal sealed class RamAccess
        {
            private readonly byte[] ram = new byte[0x10000];

            public byte GetByte(int offset)
            {
                return ram[offset];
            }

            public void SetByte(int offset, byte value)
            {
                ram[offset] = value;
            }

            public void SetU16(int offset, ushort value)
            {
                ram[offset] = (byte)(value >> 8);
                ram[offset + 1] = (byte)value;
            }

            public void SetU32(int offset, uint value)
            {
                ram[offset] = (byte)(value >> 24);
                ram[offset + 1] = (byte)(value >> 16);
                ram[offset + 2] = (byte)(value >> 8);
                ram[offset + 3] = (byte)value;
            }
        }
    }
}
