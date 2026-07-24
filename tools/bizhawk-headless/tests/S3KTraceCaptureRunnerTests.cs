using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Synthetic-movie tests for the S3K three-profile capture runner:
    /// the gameplay_unlock arm predicate (S3K's THREE-term check —
    /// Game_mode 0x0C, move_lock 0, Ctrl_1_locked 0), the aiz_end_to_end
    /// arm-frame-recorded / one-ahead-input asymmetry, the level-gated
    /// discard-and-reset and zone-leave semantics, and the post-advance
    /// stop ordering (a stop never records the row it fires on; the
    /// frame fed by the movie's final input row is never recorded).
    /// </summary>
    internal static class S3KTraceCaptureRunnerTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string BlankRow = "|..|........|........|";

        private const string EmptySidekickBlock =
            "0,0000,0000,0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00";

        public static void Register(List<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3K runner captures byte-exact gameplay_unlock output",
                CapturesByteExactGameplayUnlockOutput));
            tests.Add(new TestMain.TestCase(
                "S3K runner waits for move_lock AND Ctrl_1_locked release",
                WaitsForMoveLockAndCtrl1LockedRelease));
            tests.Add(new TestMain.TestCase(
                "S3K runner gameplay_unlock stops without recording the"
                + " mode-exit row",
                GameplayUnlockStopsWithoutRecordingModeExitRow));
            tests.Add(new TestMain.TestCase(
                "S3K runner aiz profile records the arm frame with"
                + " one-ahead input",
                AizRecordsArmFrameWithOneAheadInput));
            tests.Add(new TestMain.TestCase(
                "S3K runner aiz arm on the final input row finalizes with"
                + " zero rows",
                AizArmOnFinalRowFinalizesWithZeroRows));
            tests.Add(new TestMain.TestCase(
                "S3K runner level-gated arm on the final input row idles"
                + " once then finalizes empty",
                LevelGatedArmOnFinalRowIdlesOnceThenFinalizesEmpty));
            tests.Add(new TestMain.TestCase(
                "S3K runner level-gated discard ships only the last armed"
                + " segment",
                LevelGatedDiscardShipsLastArmedSegment));
            tests.Add(new TestMain.TestCase(
                "S3K runner level-gated discard beats zone-leave on the"
                + " same frame",
                LevelGatedDiscardBeatsZoneLeaveOnSameFrame));
            tests.Add(new TestMain.TestCase(
                "S3K runner throws clearly when the recorder never arms",
                ThrowsWhenRecorderNeverArms));
        }

        /// <summary>
        /// gameplay_unlock end-to-end: detection frame not recorded, CSV
        /// input from BK2 rows offset..offset+2, lag_counter as a REAL
        /// 0xF628 read (S3K delta — S2 pins 0), vblank_counter from the
        /// Life_count word at 0xFE12, start values captured on the arm
        /// frame only, and the movie-FINISHED stop never recording the
        /// final input row's frame.
        /// </summary>
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
                    var host = new FakeS1Host((h, frame) =>
                    {
                        h.SetU16(0xF628, (ushort)frame);   // lag counter
                        h.SetU16(0xFE12, 0x0300);          // lives word
                        if (frame == 3)
                        {
                            h.Ram[0xF600] = 0x0C;
                            h.Ram[0xFE10] = 0x03;          // CNZ
                            h.SetU32(0xF636, 0x12345678u);
                            h.SetU16(0xB010, 0x0104);
                            h.SetU16(0xB014, 0x0304);
                        }
                        if (frame == 4)
                        {
                            // Post-detection mutations must NOT leak into
                            // the start-captured metadata values.
                            h.SetU32(0xF636, 0xAABBCCDDu);
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "gameplay_unlock",
                            "2026-07-24",
                            physics,
                            aux,
                            metadata);

                    AssertEx.Equal(3, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    AssertEx.Equal(7, host.CompletedFrame);

                    string playerTail =
                        ",0000,0000,0000,00,0,0,0,0000,0000,00,00,00,00,00,"
                        + EmptySidekickBlock + "\n";
                    AssertEx.Equal(
                        S3KTraceCsvWriter.Header + "\n"
                        + "0000,0008,0000,0000,0000,0000,0300,0004,1,0104,0304"
                        + playerTail
                        + "0001,0012,0000,0000,0000,0000,0300,0005,1,0104,0304"
                        + playerTail
                        + "0002,0000,0000,0000,0000,0000,0300,0006,1,0104,0304"
                        + playerTail,
                        physics.ToString());

                    string auxText = aux.ToString();
                    AssertEx.Equal(
                        true,
                        auxText.StartsWith(
                            "{\"frame\":-1,\"vfc\":0,"
                            + "\"event\":\"cpu_state_snapshot\""));
                    AssertEx.Equal(
                        1, CountOf(auxText, "\"cpu_state_snapshot\""));
                    AssertEx.Equal(
                        3, CountOf(auxText, "\"event\":\"cpu_state\""));
                    AssertEx.Equal(
                        3,
                        CountOf(auxText, "\"event\":\"oscillation_state\""));
                    AssertEx.Equal(
                        true,
                        auxText.Contains(
                            "{\"frame\":0,\"event\":\"zone_act_state\","
                            + "\"actual_zone_id\":3,\"actual_act\":0,"
                            + "\"apparent_act\":0,\"game_mode\":12}"));
                    // gameplay_unlock has no checkpoint vocabulary at all
                    // (gameplay_start/gameplay_end are level-gated-only).
                    AssertEx.Equal(
                        0, CountOf(auxText, "\"event\":\"checkpoint\""));

                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"zone\": \"cnz\",\n"
                            + "  \"zone_id\": 3,\n"
                            + "  \"act\": 1,\n"
                            + "  \"bk2_frame_offset\": 3,\n"
                            + "  \"trace_frame_count\": 3,\n"
                            + "  \"start_x\": \"0x0104\",\n"
                            + "  \"start_y\": \"0x0304\",\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"rng_seed\": \"0x12345678\",\n"));
                    // gameplay_unlock in CNZ still gets EMPTY notes (the
                    // CNZ notes require the level-gated profile).
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"notes\": \"\"\n"));
                });
        }

        /// <summary>
        /// The S3K arm predicate has THREE terms: Game_mode 0x0C alone is
        /// not enough while either the P1 move_lock word (0xB032) or
        /// Ctrl_1_locked (0xF7CA) is non-zero. S2's two-term predicate
        /// would arm two frames early here.
        /// </summary>
        private static void WaitsForMoveLockAndCtrl1LockedRelease()
        {
            WithMovie(
                Rows(8),
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        if (frame == 2)
                        {
                            h.Ram[0xF600] = 0x0C;
                            h.SetU16(0xB032, 0x0010);   // move_lock held
                            h.Ram[0xF7CA] = 0x01;       // Ctrl_1_locked
                        }
                        if (frame == 4)
                        {
                            h.SetU16(0xB032, 0);        // lock released...
                        }
                        if (frame == 5)
                        {
                            h.Ram[0xF7CA] = 0;          // ...then control
                        }
                    });

                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "gameplay_unlock",
                            "2026-07-24",
                            new StringWriter(),
                            new StringWriter(),
                            new StringWriter());

                    AssertEx.Equal(5, result.Bk2FrameOffset);
                    AssertEx.Equal(2, result.TraceFrameCount);
                });
        }

        private static void GameplayUnlockStopsWithoutRecordingModeExitRow()
        {
            WithMovie(
                Rows(10),
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        if (frame == 2)
                        {
                            h.Ram[0xF600] = 0x0C;
                        }
                        if (frame == 6)
                        {
                            h.Ram[0xF600] = 0x28;   // level select
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "gameplay_unlock",
                            "2026-07-24",
                            physics,
                            aux,
                            metadata);

                    AssertEx.Equal(2, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    // The mode-exit row (trace frame 3) is never written.
                    AssertEx.Equal(
                        3,
                        CountOf(aux.ToString(),
                            "\"event\":\"cpu_state\""));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"trace_frame_count\": 3,\n"));
                    // gameplay_unlock never emits gameplay_end.
                    AssertEx.Equal(
                        0,
                        CountOf(aux.ToString(), "\"gameplay_end\""));
                });
        }

        /// <summary>
        /// aiz_end_to_end: arms the moment Game_mode enters the level
        /// family (0x4C here), records the ARM frame as trace row 0, and
        /// takes the CSV input column from BK2[offset + N] — one AHEAD of
        /// the row that produced row N's state (the v6.30 rule; replay
        /// compensates). Transitional 0x8C frames do not stop it; the
        /// run ends on the movie stops with the final input row's frame
        /// never recorded.
        /// </summary>
        private static void AizRecordsArmFrameWithOneAheadInput()
        {
            WithMovie(
                new[]
                {
                    BlankRow,
                    BlankRow,
                    "|..|...R....|........|",   // BK2[2] = row 0 input
                    "|..|..L.....|........|",   // BK2[3] = row 1 input
                    "|..|U.......|........|",   // BK2[4] = row 2 input
                    "|..|.D......|........|"    // BK2[5] = row 3 input
                },
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        if (frame == 2)
                        {
                            h.Ram[0xF600] = 0x4C;   // level-load handoff
                            h.SetU16(0xB010, 0x0120);
                            h.SetU16(0xB014, 0x00D4);
                        }
                        if (frame == 4)
                        {
                            h.Ram[0xF600] = 0x8C;   // still level family
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "aiz_end_to_end",
                            "2026-07-24",
                            physics,
                            aux,
                            metadata);

                    AssertEx.Equal(2, result.Bk2FrameOffset);
                    AssertEx.Equal(4, result.TraceFrameCount);
                    AssertEx.Equal(6, host.CompletedFrame);

                    // Input column = BK2[2..5]: R, L, U, D. Row 0 is the
                    // arm frame, whose state was fed by BK2[1] — the
                    // 0x0008 value pins the one-ahead convention.
                    string[] lines = physics.ToString().Split('\n');
                    AssertEx.Equal(
                        true, lines[1].StartsWith("0000,0008,"));
                    AssertEx.Equal(
                        true, lines[2].StartsWith("0001,0004,"));
                    AssertEx.Equal(
                        true, lines[3].StartsWith("0002,0001,"));
                    AssertEx.Equal(
                        true, lines[4].StartsWith("0003,0002,"));

                    string auxText = aux.ToString();
                    AssertEx.Equal(
                        true,
                        auxText.Contains(
                            "{\"frame\":0,\"event\":\"checkpoint\","
                            + "\"name\":\"intro_begin\","
                            + "\"actual_zone_id\":0,\"actual_act\":0,"
                            + "\"apparent_act\":0,\"game_mode\":76}"));
                    AssertEx.Equal(
                        0, CountOf(auxText, "\"gameplay_end\""));

                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"zone\": \"aiz\",\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"notes\": \"AIZ intro through HCZ handoff"
                            + " end-to-end fixture\"\n"));
                });
        }

        /// <summary>
        /// aiz arm on the frame that consumed the movie's final input
        /// row: the arm falls through into the movie-end checks in the
        /// SAME iteration (no idle advance) and the BK2-end guard fires
        /// before row 0 is written — header-only physics, empty aux,
        /// trace_frame_count 0.
        /// </summary>
        private static void AizArmOnFinalRowFinalizesWithZeroRows()
        {
            WithMovie(
                Rows(3),
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        if (frame == 3)
                        {
                            h.Ram[0xF600] = 0x4C;
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "aiz_end_to_end",
                            "2026-07-24",
                            physics,
                            aux,
                            metadata);

                    AssertEx.Equal(3, result.Bk2FrameOffset);
                    AssertEx.Equal(0, result.TraceFrameCount);
                    AssertEx.Equal(3, host.CompletedFrame);
                    AssertEx.Equal(
                        S3KTraceCsvWriter.Header + "\n",
                        physics.ToString());
                    AssertEx.Equal(string.Empty, aux.ToString());
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"trace_frame_count\": 0,\n"));
                });
        }

        /// <summary>
        /// level-gated arm on the final input row: the arm frame is
        /// dropped, so the loop must idle-advance ONCE past the movie's
        /// end (Lua post-movie on_frame_end parity) before the BK2-end
        /// guard finalizes with zero rows; the gameplay_end checkpoint
        /// reads the post-idle RAM at frame 0.
        /// </summary>
        private static void LevelGatedArmOnFinalRowIdlesOnceThenFinalizesEmpty()
        {
            WithMovie(
                Rows(3),
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        if (frame == 3)
                        {
                            h.Ram[0xF600] = 0x0C;
                            h.Ram[0xFE10] = 0x03;
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "level_gated_reset_aware",
                            "2026-07-24",
                            physics,
                            aux,
                            metadata);

                    AssertEx.Equal(3, result.Bk2FrameOffset);
                    AssertEx.Equal(0, result.TraceFrameCount);
                    AssertEx.Equal(4, host.CompletedFrame);
                    AssertEx.Equal(
                        S3KTraceCsvWriter.Header + "\n",
                        physics.ToString());
                    AssertEx.Equal(
                        "{\"frame\":0,\"event\":\"checkpoint\","
                        + "\"name\":\"gameplay_end\","
                        + "\"actual_zone_id\":3,\"actual_act\":0,"
                        + "\"apparent_act\":0,\"game_mode\":12}\n",
                        aux.ToString());
                });
        }

        /// <summary>
        /// The level-gated discard: a Title-mode frame while armed
        /// deletes the whole in-progress recording (buffers, aux engine
        /// state, checkpoint dedup, pre-trace snapshot latch) and
        /// re-arms; the shipped output is the LAST armed segment with
        /// its own arm-frame metadata, exactly one cpu_state_snapshot,
        /// a fresh gameplay_start, and the zone-leave gameplay_end
        /// carrying the post-leave zone at frame == row count.
        /// </summary>
        private static void LevelGatedDiscardShipsLastArmedSegment()
        {
            WithMovie(
                Rows(12),
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        if (frame == 2)
                        {
                            h.Ram[0xF600] = 0x0C;
                            h.Ram[0xFE10] = 0x03;
                            h.Ram[0xF711] = 0x01;   // level started
                            h.SetU32(0xF636, 0x11111111u);
                        }
                        if (frame == 5)
                        {
                            h.Ram[0xF600] = 0x04;   // pause+A to Title
                        }
                        if (frame == 7)
                        {
                            h.Ram[0xF600] = 0x0C;
                            h.SetU32(0xF636, 0x22222222u);
                            h.SetU16(0xB010, 0x0100);
                            h.SetU16(0xB014, 0x0200);
                        }
                        if (frame == 10)
                        {
                            h.Ram[0xFE10] = 0x05;   // zone-leave (ICZ)
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "level_gated_reset_aware",
                            "2026-07-24",
                            physics,
                            aux,
                            metadata);

                    AssertEx.Equal(7, result.Bk2FrameOffset);
                    AssertEx.Equal(2, result.TraceFrameCount);

                    string[] lines = physics.ToString().Split('\n');
                    AssertEx.Equal(S3KTraceCsvWriter.Header, lines[0]);
                    AssertEx.Equal(true, lines[1].StartsWith("0000,"));
                    AssertEx.Equal(true, lines[2].StartsWith("0001,"));
                    AssertEx.Equal(4, lines.Length);   // header, 2 rows, ""

                    string auxText = aux.ToString();
                    AssertEx.Equal(
                        1, CountOf(auxText, "\"cpu_state_snapshot\""));
                    AssertEx.Equal(
                        2, CountOf(auxText, "\"event\":\"cpu_state\""));
                    AssertEx.Equal(
                        true,
                        auxText.Contains(
                            "{\"frame\":0,\"event\":\"checkpoint\","
                            + "\"name\":\"gameplay_start\","
                            + "\"actual_zone_id\":3,\"actual_act\":0,"
                            + "\"apparent_act\":0,\"game_mode\":12}"));
                    AssertEx.Equal(
                        true,
                        auxText.EndsWith(
                            "{\"frame\":2,\"event\":\"checkpoint\","
                            + "\"name\":\"gameplay_end\","
                            + "\"actual_zone_id\":5,\"actual_act\":0,"
                            + "\"apparent_act\":0,\"game_mode\":12}\n"));

                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"bk2_frame_offset\": 7,\n"
                            + "  \"trace_frame_count\": 2,\n"
                            + "  \"start_x\": \"0x0100\",\n"
                            + "  \"start_y\": \"0x0200\",\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"rng_seed\": \"0x22222222\",\n"));
                });
        }

        /// <summary>
        /// Stop ordering: the discard check runs BEFORE the zone-leave
        /// check in the Lua's on_frame_end source order. A level-select
        /// frame whose zone byte has also changed must DISCARD (and
        /// later re-arm), not finalize a zone-leave.
        /// </summary>
        private static void LevelGatedDiscardBeatsZoneLeaveOnSameFrame()
        {
            WithMovie(
                Rows(10),
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        if (frame == 2)
                        {
                            h.Ram[0xF600] = 0x0C;
                            h.Ram[0xFE10] = 0x03;
                        }
                        if (frame == 5)
                        {
                            // Level select: mode AND zone change on the
                            // same frame.
                            h.Ram[0xF600] = 0x28;
                            h.Ram[0xFE10] = 0x22;
                        }
                        if (frame == 7)
                        {
                            h.Ram[0xF600] = 0x0C;
                            h.Ram[0xFE10] = 0x03;
                        }
                    });

                    var aux = new StringWriter();
                    S3KTraceCaptureResult result =
                        S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "level_gated_reset_aware",
                            "2026-07-24",
                            new StringWriter(),
                            aux,
                            new StringWriter());

                    // Finalized by movie FINISHED with the SECOND arm's
                    // offset; a wrong zone-leave-first ordering would
                    // have finalized at frame 5 with offset 2.
                    AssertEx.Equal(7, result.Bk2FrameOffset);
                    AssertEx.Equal(2, result.TraceFrameCount);
                    AssertEx.Equal(
                        true,
                        aux.ToString().Contains(
                            "\"name\":\"gameplay_end\","
                            + "\"actual_zone_id\":3,"));
                });
        }

        private static void ThrowsWhenRecorderNeverArms()
        {
            WithMovie(
                Rows(4),
                movie =>
                {
                    var host = new FakeS1Host((h, frame) =>
                    {
                        h.Ram[0xF600] = 0x04;   // Title forever
                    });

                    AssertEx.Throws<InvalidDataException>(
                        () => S3KTraceCaptureRunner.Capture(
                            movie,
                            host,
                            "gameplay_unlock",
                            "2026-07-24",
                            new StringWriter(),
                            new StringWriter(),
                            new StringWriter()),
                        "never armed");
                });
        }

        private static int CountOf(string haystack, string needle)
        {
            var count = 0;
            var index = 0;
            while (true)
            {
                index = haystack.IndexOf(
                    needle, index, StringComparison.Ordinal);
                if (index < 0)
                {
                    return count;
                }
                count++;
                index += needle.Length;
            }
        }

        private static string[] Rows(int count)
        {
            var rows = new string[count];
            for (int i = 0; i < count; i++)
            {
                rows[i] = BlankRow;
            }
            return rows;
        }

        private static void WithMovie(
            IEnumerable<string> rows,
            Action<Bk2Movie> body)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "openggf-s3k-trace-" + Guid.NewGuid().ToString("N"));
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
