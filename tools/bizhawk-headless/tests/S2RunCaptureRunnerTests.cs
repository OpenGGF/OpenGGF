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
    /// movie length override), and the run-end finalize routing.
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

                S2RunCaptureResult result = S2RunCaptureRunner.Capture(
                    movie, host, "test-run", "synthetic.bk2",
                    "2026-07-24", 0);

                AssertEx.Equal(3, result.Segments.Count);
                AssertEx.Equal(2, result.Transitions.Count);

                S2RunSegmentOutput seg1 = result.Segments[0];
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

                S2RunSegmentOutput ss = result.Segments[1];
                AssertEx.Equal("ss", ss.DirToken);
                AssertEx.Equal("special_stage", ss.ManifestEntry.Kind);
                AssertEx.Equal(
                    "s2_special_stage", ss.ManifestEntry.TraceProfile);
                AssertEx.Equal(16, ss.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(5, ss.ManifestEntry.TraceFrameCount);
                AssertEx.Equal(0, ss.ManifestEntry.ZoneId);
                AssertEx.Equal(0, ss.ManifestEntry.Act);
                AssertEx.Equal(2, ss.ManifestEntry.SpecialStageIndex ?? -1);
                AssertEx.Equal(string.Empty, ss.AuxStateJsonl);
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

                S2RunSegmentOutput seg2 = result.Segments[2];
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

                S2RunCaptureResult result = S2RunCaptureRunner.Capture(
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

                S2RunCaptureResult result = S2RunCaptureRunner.Capture(
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

                S2RunCaptureResult result = S2RunCaptureRunner.Capture(
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

                S2RunCaptureResult result = S2RunCaptureRunner.Capture(
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

                S2RunCaptureResult result = S2RunCaptureRunner.Capture(
                    movie, host, "short", "synthetic.bk2",
                    "2026-07-24", 12);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(5, result.Segments[0].ManifestEntry.Bk2FrameOffset);
                // rows = effective length - offset - 1 = 12 - 5 - 1.
                AssertEx.Equal(6, result.Segments[0].ManifestEntry.TraceFrameCount);
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
        internal sealed class FakeRunHost : IGpgxHost
        {
            private readonly Action<FakeRunHost, int> onAdvance;

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
