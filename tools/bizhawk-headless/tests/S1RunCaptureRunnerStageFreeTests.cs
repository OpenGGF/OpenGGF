using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Synthetic-movie tests for the stage-free path of the shipping S1
    /// complete-run engine, <see cref="S1RunCaptureRunner"/> — the runner
    /// Program.cs routes BOTH S1 complete-run entry points through
    /// (--trace-profile complete_run and --run-id). A movie that never
    /// reads game_mode $10 must exercise exactly the legacy complete-run
    /// segmentation semantics: arm/finalize/re-arm across mode exits,
    /// per-segment offsets, counts and dir tokens, cross-segment tracker
    /// carry-over (spec section 5), the death-does-not-split rule, the
    /// never-re-arm ending/credits tail, movie-end and S1_STOP_AT_FRAME
    /// truncation, and byte-literal expectations for the complete-run aux
    /// additions (extended object_near plus the four per-frame diagnostic
    /// events). Every capture here passes a null run id, so the shared
    /// helper additionally asserts the stage-free layout invariant: zero
    /// transitions, a null run manifest, and only kind "level" segments.
    /// The detour-route scenarios live in S1RunCaptureRunnerTests.
    /// </summary>
    internal static class S1RunCaptureRunnerStageFreeTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string BlankRow = "|..|........|........|";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free finalizes and re-arms across"
                + " a mode exit",
                FinalizesAndRearmsAcrossModeExit));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free carries trackers across"
                + " segment boundaries",
                CarriesTrackersAcrossSegmentBoundaries));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free keeps one segment through an"
                + " in-mode death",
                KeepsOneSegmentThroughInModeDeath));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free suffixes repeated zone-act"
                + " dir tokens",
                SuffixesRepeatedZoneActDirTokens));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free never re-arms after the"
                + " ending modes",
                NeverRearmsAfterEndingModes));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free drops the movie's last input"
                + " row",
                DropsMovieLastInputRow));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free honors the stop-at-frame"
                + " hard stop",
                HonorsStopAtFrameHardStop));
            tests.Add(new TestMain.TestCase(
                "S1RunCaptureRunner stage-free emits complete-run aux"
                + " events byte-exactly",
                EmitsCompleteRunAuxEventsByteExactly));
        }

        /// <summary>
        /// Schedule (completed frame F): boot 1-4; arm at F=5 (GHZ act raw
        /// 0); rows F=6-14 (9 rows); mode exit 0x8C at F=15 finalizes;
        /// title-card gap F=15-17; re-arm at F=18 (act raw 1); rows
        /// F=19-24 (6 rows); mode exit at F=25; idle to the 30-row movie
        /// end, which contributes nothing.
        /// </summary>
        private static void FinalizesAndRearmsAcrossModeExit()
        {
            WithMovie(Rows(30), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
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
                    if (frame == 15)
                    {
                        h.Ram[0xF600] = 0x8C;           // got-through card
                    }
                    if (frame == 16)
                    {
                        h.Ram[0xFE11] = 0x01;           // next act raw 1
                        h.SetU16(0xD008, 0x0060);
                        h.SetU16(0xD00C, 0x0290);
                        h.SetU32(0xF636, 0x00000000u);
                    }
                    if (frame == 18)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 25)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                });

                IList<RunSegmentOutput> segments;
                S1RunCaptureResult result =
                    Capture(movie, host, 0, out segments);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal(2, segments.Count);
                AssertEx.Equal("ghz1", result.Segments[0].Dir);
                AssertEx.Equal("ghz2", result.Segments[1].Dir);

                RunSegmentOutput seg1 = segments[0];
                AssertEx.Equal("ghz1", seg1.DirToken);
                AssertEx.Equal(0, seg1.ManifestEntry.ZoneId);
                AssertEx.Equal(1, seg1.ManifestEntry.Act);
                AssertEx.Equal(5, seg1.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(9, seg1.ManifestEntry.TraceFrameCount);
                string[] seg1Lines = seg1.PhysicsCsv.Split('\n');
                AssertEx.Equal(11, seg1Lines.Length);   // header+9+empty
                AssertEx.Equal(S1TraceCsvWriter.Header, seg1Lines[0]);
                AssertEx.Equal(true, seg1Lines[1].StartsWith("0000,"));
                AssertEx.Equal(true, seg1Lines[9].StartsWith("0008,"));
                AssertEx.Equal(string.Empty, seg1Lines[10]);
                AssertContains(seg1.MetadataJson, "  \"zone\": \"ghz\",\n");
                AssertContains(seg1.MetadataJson, "  \"act\": 1,\n");
                AssertContains(seg1.MetadataJson,
                    "  \"bk2_frame_offset\": 5,\n");
                AssertContains(seg1.MetadataJson,
                    "  \"trace_frame_count\": 9,\n");
                AssertContains(seg1.MetadataJson,
                    "  \"start_x\": \"0x0050\",\n");
                AssertContains(seg1.MetadataJson,
                    "  \"start_y\": \"0x03B0\",\n");
                AssertContains(seg1.MetadataJson,
                    "  \"rng_seed\": \"0xAABBCCDD\",\n");
                AssertContains(seg1.MetadataJson,
                    "  \"source_bk2\": \"synthetic.bk2\"\n");

                RunSegmentOutput seg2 = segments[1];
                AssertEx.Equal("ghz2", seg2.DirToken);
                AssertEx.Equal(18, seg2.ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(6, seg2.ManifestEntry.TraceFrameCount);
                AssertContains(seg2.MetadataJson, "  \"act\": 2,\n");
                AssertContains(seg2.MetadataJson,
                    "  \"rng_seed\": \"0x00000000\",\n");
            });
        }

        /// <summary>
        /// Spec section 5: prev_routine, known_objects and prev_opl_screen
        /// are never reset between segments. Segment 2's frame 0 emits NO
        /// routine_change (routine still 0x02 from segment 1), emits
        /// object_removed for a slot cleared during the gap, stays silent
        /// for a slot whose id is unchanged, and emits a cursor_state with
        /// dir "L" because the new opl_screen is below segment 1's last.
        /// </summary>
        private static void CarriesTrackersAcrossSegmentBoundaries()
        {
            WithMovie(Rows(24), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xD024] = 0x02;
                        h.SetU16(0xF76E, 0x0100);       // opl_screen
                        h.Ram[SlotAddr(40)] = 0x25;     // cleared in the gap
                        h.Ram[SlotAddr(41)] = 0x26;     // persists silently
                    }
                    if (frame == 10)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                    if (frame == 11)
                    {
                        h.Ram[SlotAddr(40)] = 0x00;
                        h.SetU16(0xF76E, 0x0000);
                    }
                    if (frame == 14)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 20)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                });

                IList<RunSegmentOutput> segments;
                Capture(movie, host, 0, out segments);

                AssertEx.Equal(2, segments.Count);
                string seg1Aux = segments[0].AuxStateJsonl;
                string seg2Aux = segments[1].AuxStateJsonl;

                // Only the FIRST segment sees the 0x00 -> 0x02 routine
                // change and the initial appearances.
                AssertContains(seg1Aux,
                    "\"event\":\"routine_change\",\"from\":\"0x00\","
                    + "\"to\":\"0x02\"");
                AssertContains(seg1Aux,
                    "\"event\":\"object_appeared\",\"slot\":40");
                AssertContains(seg1Aux,
                    "\"event\":\"object_appeared\",\"slot\":41");
                AssertContains(seg1Aux, "\"dir\":\"R\"");

                AssertEx.Equal(false, seg2Aux.Contains("routine_change"));
                AssertContains(seg2Aux,
                    "\"event\":\"object_removed\",\"slot\":40,"
                    + "\"object_type\":\"0x25\"");
                AssertEx.Equal(false, seg2Aux.Contains("object_appeared"));
                AssertContains(seg2Aux,
                    "\"event\":\"cursor_state\",\"opl_screen\":\"0x0000\"");
                AssertContains(seg2Aux, "\"dir\":\"L\"");
            });
        }

        /// <summary>
        /// A death keeps v_gamemode at 0x0C (the restart loops inside
        /// GM_Level), so recording continues uninterrupted: one segment
        /// spanning the death, with the routine change visible in aux.
        /// </summary>
        private static void KeepsOneSegmentThroughInModeDeath()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.Ram[0xD024] = 0x02;
                    }
                    if (frame == 8)
                    {
                        h.Ram[0xD024] = 0x06;           // Sonic_Death
                        h.SetU16(0xD03E, 0x0100);       // relock controls
                    }
                    if (frame == 12)
                    {
                        h.Ram[0xD024] = 0x02;
                        h.SetU16(0xD03E, 0x0000);
                    }
                });

                IList<RunSegmentOutput> segments;
                S1RunCaptureResult result =
                    Capture(movie, host, 0, out segments);

                AssertEx.Equal(1, result.Segments.Count);
                // Rows F=4..19: the guard at F=20 drops only the last row.
                AssertEx.Equal(
                    16, segments[0].ManifestEntry.TraceFrameCount);
                AssertContains(segments[0].AuxStateJsonl,
                    "\"event\":\"routine_change\",\"from\":\"0x02\","
                    + "\"to\":\"0x06\"");
            });
        }

        /// <summary>
        /// Re-entering the same zone/act arms a suffixed dir token (ghz1
        /// then ghz1_2) — the naming that keeps special-stage/mode
        /// round-trip re-entries from overwriting earlier segments.
        /// </summary>
        private static void SuffixesRepeatedZoneActDirTokens()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 8)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                    if (frame == 11)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 16)
                    {
                        h.Ram[0xF600] = 0x8C;
                    }
                });

                IList<RunSegmentOutput> segments;
                S1RunCaptureResult result =
                    Capture(movie, host, 0, out segments);

                AssertEx.Equal(2, result.Segments.Count);
                AssertEx.Equal("ghz1", result.Segments[0].Dir);
                AssertEx.Equal("ghz1_2", result.Segments[1].Dir);
            });
        }

        /// <summary>
        /// After the final segment's mode exit the pass idles through
        /// GM_Ending/GM_Credits (never 0x0C again): no ending or credits
        /// segment is ever created and the movie end contributes nothing —
        /// the committed credits_* fixtures come from a different pipeline.
        /// </summary>
        private static void NeverRearmsAfterEndingModes()
        {
            WithMovie(Rows(30), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                    if (frame == 10)
                    {
                        h.Ram[0xF600] = 0x18;           // GM_Ending
                    }
                    if (frame == 20)
                    {
                        h.Ram[0xF600] = 0x1C;           // GM_Credits
                    }
                });

                IList<RunSegmentOutput> segments;
                S1RunCaptureResult result =
                    Capture(movie, host, 0, out segments);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(3, segments[0].ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(6, segments[0].ManifestEntry.TraceFrameCount);
            });
        }

        /// <summary>
        /// A movie ending mid-segment: the frame that consumed the movie's
        /// LAST input row is never recorded, so the segment's final row is
        /// the one fed by the second-to-last input row (rows = frame count
        /// - offset - 1).
        /// </summary>
        private static void DropsMovieLastInputRow()
        {
            WithMovie(Rows(12), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                });

                IList<RunSegmentOutput> segments;
                S1RunCaptureResult result =
                    Capture(movie, host, 0, out segments);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(3, segments[0].ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(8, segments[0].ManifestEntry.TraceFrameCount);
            });
        }

        /// <summary>
        /// S1_STOP_AT_FRAME mid-segment finalizes a truncated but
        /// well-formed segment: metadata reflects the rows written so far,
        /// and the stage-free manifest gate stays closed even on the hard
        /// stop.
        /// </summary>
        private static void HonorsStopAtFrameHardStop()
        {
            WithMovie(Rows(20), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                    }
                });

                IList<RunSegmentOutput> segments;
                S1RunCaptureResult result =
                    Capture(movie, host, 10, out segments);

                AssertEx.Equal(1, result.Segments.Count);
                AssertEx.Equal(3, segments[0].ManifestEntry.Bk2FrameOffset);
                AssertEx.Equal(6, segments[0].ManifestEntry.TraceFrameCount);
                AssertContains(segments[0].MetadataJson,
                    "  \"trace_frame_count\": 6,\n");
            });
        }

        /// <summary>
        /// Byte-literal expectations for the complete-run aux additions on
        /// a fully controlled frame 0: the extended object_near template
        /// (key order objoff_3c before objoff_32), then v_objstate,
        /// camera_boundary, v_oscillate and lag_state in the Lua's exact
        /// order between the object scan and cursor_state, with the
        /// emulator-cumulative lag count carried through unchanged.
        /// </summary>
        private static void EmitsCompleteRunAuxEventsByteExactly()
        {
            WithMovie(Rows(12), movie =>
            {
                var host = new FakeS1Host((h, frame) =>
                {
                    if (frame == 3)
                    {
                        h.Ram[0xF600] = 0x0C;
                        h.SetU16(0xD008, 0x0100);       // player x
                        h.SetU16(0xD00C, 0x0300);       // player y
                        h.Ram[0xD024] = 0x02;           // routine
                        int obj = SlotAddr(33);
                        h.Ram[obj] = 0x3B;
                        h.SetU16(obj + 0x08, 0x0120);
                        h.SetU16(obj + 0x0C, 0x0310);
                        h.Ram[obj + 0x24] = 0x02;       // obj routine
                        h.Ram[0xFC00] = 0x03;           // objstate fwd ctr
                        h.Ram[0xFC01] = 0x01;           // objstate bwd ctr
                        h.SetU16(0xF726, 0x0300);       // limitbtm1
                        h.SetU16(0xF72E, 0x0300);       // limitbtm2
                        h.SetU16(0xF73E, 0x0060);       // lookshift
                        h.Ram[0xFE5F] = 0x7C;           // oscillate low byte
                        h.LagCount = 347;
                    }
                    if (frame == 8)
                    {
                        h.IsLagged = true;
                        h.LagCount = 348;
                    }
                    if (frame == 9)
                    {
                        h.IsLagged = false;
                    }
                });

                IList<RunSegmentOutput> segments;
                Capture(movie, host, 0, out segments);

                AssertEx.Equal(1, segments.Count);
                string[] auxLines = segments[0].AuxStateJsonl.Split('\n');

                // Frame 0 was recorded at completed frame 4 (vfc 4).
                AssertEx.Equal(
                    "{\"frame\":0,\"vfc\":4,\"event\":\"routine_change\","
                    + "\"from\":\"0x00\",\"to\":\"0x02\","
                    + "\"sonic_x\":\"0x0100\",\"sonic_y\":\"0x0300\","
                    + "\"x_vel\":0,\"y_vel\":0,\"inertia\":0,"
                    + "\"status\":\"0x00\",\"stand_on_obj\":0}",
                    auxLines[0]);
                AssertEx.Equal(
                    "{\"frame\":0,\"vfc\":4,\"event\":\"state_snapshot\","
                    + "\"control_locked\":false,\"anim_id\":0,"
                    + "\"status_byte\":\"0x00\",\"routine\":\"0x02\","
                    + "\"y_radius\":0,\"x_radius\":0,\"on_object\":false,"
                    + "\"pushing\":false,\"underwater\":false,"
                    + "\"roll_jumping\":false}",
                    auxLines[1]);
                AssertEx.Equal(
                    "{\"frame\":0,\"vfc\":4,\"event\":\"object_appeared\","
                    + "\"slot\":33,\"object_type\":\"0x3B\","
                    + "\"x\":\"0x0120\",\"y\":\"0x0310\"}",
                    auxLines[2]);
                AssertEx.Equal(
                    "{\"frame\":0,\"vfc\":4,\"event\":\"object_near\","
                    + "\"slot\":33,\"type\":\"0x3B\",\"x\":\"0x0120\","
                    + "\"y\":\"0x0310\",\"routine\":\"0x02\","
                    + "\"status\":\"0x00\",\"obj_frame\":\"0x00\","
                    + "\"routine2\":\"0x00\","
                    + "\"objoff_3c\":\"0x00000000\","
                    + "\"objoff_32\":\"0x0000\",\"objoff_34\":\"0x0000\","
                    + "\"objoff_36\":\"0x0000\",\"objoff_38\":\"0x0000\"}",
                    auxLines[3]);
                AssertEx.Equal(
                    "{\"frame\":0,\"vfc\":4,\"event\":\"slot_dump\","
                    + "\"slots\":[[33,\"0x3B\"]]}",
                    auxLines[4]);
                AssertEx.Equal(
                    "{\"frame\":0,\"event\":\"v_objstate\",\"bytes\":\"0301"
                    + new string('0', 380) + "\"}",
                    auxLines[5]);
                AssertEx.Equal(
                    "{\"frame\":0,\"event\":\"camera_boundary\","
                    + "\"limitbtm1\":\"0x0300\",\"limitbtm2\":\"0x0300\","
                    + "\"lookshift\":\"0x0060\",\"bgscrollvert\":\"0x00\"}",
                    auxLines[6]);
                AssertEx.Equal(
                    "{\"frame\":0,\"event\":\"v_oscillate\",\"bytes\":\"007C"
                    + new string('0', 128) + "\"}",
                    auxLines[7]);
                AssertEx.Equal(
                    "{\"frame\":0,\"event\":\"lag_state\",\"lagged\":false,"
                    + "\"lagcount\":347}",
                    auxLines[8]);
                AssertEx.Equal(
                    "{\"frame\":0,\"vfc\":4,\"event\":\"cursor_state\","
                    + "\"opl_screen\":\"0x0000\","
                    + "\"fwd_ptr\":\"0x00000000\","
                    + "\"bwd_ptr\":\"0x00000000\",\"fwd_ctr\":3,"
                    + "\"bwd_ctr\":1,\"dir\":\"R\"}",
                    auxLines[9]);

                // The lag frame (trace frame 4, completed frame 8) carries
                // the cumulative count; the next frame drops the flag but
                // keeps the count.
                AssertContains(segments[0].AuxStateJsonl,
                    "{\"frame\":4,\"event\":\"lag_state\",\"lagged\":true,"
                    + "\"lagcount\":348}");
                AssertContains(segments[0].AuxStateJsonl,
                    "{\"frame\":5,\"event\":\"lag_state\",\"lagged\":false,"
                    + "\"lagcount\":348}");

                // Every recorded frame carries the four per-frame events
                // exactly once (8 recorded rows).
                AssertEx.Equal(8, CountOccurrences(
                    segments[0].AuxStateJsonl, "\"event\":\"v_objstate\""));
                AssertEx.Equal(8, CountOccurrences(
                    segments[0].AuxStateJsonl, "\"event\":\"lag_state\""));
            });
        }

        /// <summary>
        /// Runs the shipping engine with a null run id and asserts the
        /// stage-free layout invariant on every pass: a movie that never
        /// reads game_mode $10 records zero transitions, the manifest gate
        /// stays closed (null run_manifest.json — the legacy complete-run
        /// layout), and every finished segment is kind "level".
        /// </summary>
        private static S1RunCaptureResult Capture(
            Bk2Movie movie,
            FakeS1Host host,
            int stopAtFrame,
            out IList<RunSegmentOutput> segments)
        {
            var collector = new RunSegmentCollector();
            S1RunCaptureResult result =
                S1RunCaptureRunner.CaptureScratchLegacy(
                movie,
                host,
                null,
                "synthetic.bk2",
                "2026-07-24",
                S1CompleteRunMetadataWriter.LuaScriptVersion,
                stopAtFrame,
                collector);
            segments = collector.Segments;
            AssertEx.Equal(0, result.Transitions.Count);
            AssertEx.Equal(true, result.RunManifestJson == null);
            foreach (RunSegmentOutput segment in collector.Segments)
            {
                AssertEx.Equal(
                    RunManifestSegment.LevelKind,
                    segment.ManifestEntry.Kind);
            }
            return result;
        }

        private static int SlotAddr(int slot)
        {
            return 0xD000 + (slot * 0x40);
        }

        private static int CountOccurrences(string text, string fragment)
        {
            var count = 0;
            var index = 0;
            while ((index = text.IndexOf(
                fragment, index, StringComparison.Ordinal)) >= 0)
            {
                count++;
                index += fragment.Length;
            }
            return count;
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
                "openggf-s1-completerun-" + Guid.NewGuid().ToString("N"));
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
