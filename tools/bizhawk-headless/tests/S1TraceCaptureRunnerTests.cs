using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Text;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1TraceCaptureRunnerTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string BlankRow = "|..|........|........|";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1TraceCaptureRunner detects offset and captures byte-exact files",
                DetectsOffsetAndCapturesByteExactFiles));
            tests.Add(new TestMain.TestCase(
                "S1TraceCaptureRunner finishes on game-mode exit without partial row",
                FinishesOnGameModeExitWithoutPartialRow));
            tests.Add(new TestMain.TestCase(
                "S1TraceCaptureRunner finishes on movie exhaustion",
                FinishesOnMovieExhaustion));
            tests.Add(new TestMain.TestCase(
                "S1TraceCaptureRunner waits for control lock release before starting",
                WaitsForControlLockReleaseBeforeStarting));
            tests.Add(new TestMain.TestCase(
                "S1TraceCaptureRunner applies buttons per row in smoke-runner order",
                AppliesButtonsPerRowInSmokeRunnerOrder));
            tests.Add(new TestMain.TestCase(
                "S1TraceCaptureRunner fails clearly when detection never fires",
                FailsClearlyWhenDetectionNeverFires));
        }

        private static void DetectsOffsetAndCapturesByteExactFiles()
        {
            WithMovie(
                new[]
                {
                    BlankRow,
                    BlankRow,
                    BlankRow,
                    "|..|...R....|........|",
                    "|..|.D...B..|........|",
                    BlankRow
                },
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 3)
                        {
                            ram.SetByte(0xF600, 0x0C);
                            ram.SetU32(0xF636, 0x12345678u);
                            ram.SetByte(0xFE11, 0x01);
                        }
                        if (completedFrame == 4)
                        {
                            // Post-detection mutations must NOT leak into the
                            // start-captured metadata values.
                            ram.SetU32(0xF636, 0xAABBCCDDu);
                            ram.SetByte(0xFE11, 0x05);
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S1TraceCaptureResult result = S1TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "2026-07-13",
                        physics,
                        aux,
                        metadata);

                    AssertEx.Equal(3, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    AssertEx.Equal(6, host.AdvanceCount);

                    string commonTail =
                        ",0000,0000,00,0,0,0,0000,0000,00,00,00,00,00,"
                        + S1TraceCsvWriter.SidekickBlock + "\n";
                    AssertEx.Equal(
                        S1TraceCsvWriter.Header + "\n"
                        + "0000,0008,0000,0000,0000,0004,0000,0000,1,0104,0304"
                        + ",0000" + commonTail
                        + "0001,0012,0000,0000,0000,0005,0000,0000,1,0105,0305"
                        + ",0000" + commonTail
                        + "0002,0000,0000,0000,0000,0006,0000,0000,1,0106,0306"
                        + ",0000" + commonTail,
                        physics.ToString());

                    AssertEx.Equal(
                        "{\"frame\":0,\"vfc\":4,\"event\":\"state_snapshot\","
                        + "\"control_locked\":false,\"anim_id\":0,"
                        + "\"status_byte\":\"0x00\",\"routine\":\"0x00\","
                        + "\"y_radius\":0,\"x_radius\":0,\"on_object\":false,"
                        + "\"pushing\":false,\"underwater\":false,"
                        + "\"roll_jumping\":false}\n"
                        + "{\"frame\":0,\"vfc\":4,\"event\":\"cursor_state\","
                        + "\"opl_screen\":\"0x0000\","
                        + "\"fwd_ptr\":\"0x00000000\","
                        + "\"bwd_ptr\":\"0x00000000\","
                        + "\"fwd_ctr\":0,\"bwd_ctr\":0,\"dir\":\"R\"}\n",
                        aux.ToString());

                    AssertEx.Equal(
                        "{\n"
                        + "  \"game\": \"s1\",\n"
                        + "  \"zone\": \"ghz\",\n"
                        + "  \"zone_id\": 0,\n"
                        + "  \"act\": 2,\n"
                        + "  \"bk2_frame_offset\": 3,\n"
                        + "  \"trace_frame_count\": 3,\n"
                        + "  \"start_x\": \"0x0103\",\n"
                        + "  \"start_y\": \"0x0303\",\n"
                        + "  \"characters\": [\"sonic\"],\n"
                        + "  \"main_character\": \"sonic\",\n"
                        + "  \"sidekicks\": [],\n"
                        + "  \"rng_seed\": \"0x12345678\",\n"
                        + "  \"recording_date\": \"2026-07-13\",\n"
                        + "  \"lua_script_version\": \"3.5\",\n"
                        + "  \"trace_schema\": 4,\n"
                        + "  \"csv_version\": 7,\n"
                        + "  \"aux_schema_extras\": "
                        + "[\"s1_obj64_state_per_frame\"],\n"
                        + "  \"rom_checksum\": \"\",\n"
                        + "  \"notes\": \"\"\n"
                        + "}\n",
                        metadata.ToString());
                });
        }

        private static void FinishesOnGameModeExitWithoutPartialRow()
        {
            WithMovie(
                Rows(10),
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
                            ram.SetByte(0xF600, 0x00);
                        }
                    });

                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    S1TraceCaptureResult result = S1TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "2026-07-13",
                        physics,
                        aux,
                        metadata);

                    AssertEx.Equal(1, result.Bk2FrameOffset);
                    AssertEx.Equal(2, result.TraceFrameCount);
                    AssertEx.Equal(4, host.AdvanceCount);

                    string[] physicsLines = physics.ToString().Split('\n');
                    AssertEx.Equal(4, physicsLines.Length);
                    AssertEx.Equal(S1TraceCsvWriter.Header, physicsLines[0]);
                    AssertEx.Equal(true, physicsLines[1].StartsWith("0000,"));
                    AssertEx.Equal(true, physicsLines[2].StartsWith("0001,"));
                    AssertEx.Equal(string.Empty, physicsLines[3]);
                    AssertEx.Equal(
                        false, aux.ToString().Contains("\"frame\":2,"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"bk2_frame_offset\": 1,\n"));
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"trace_frame_count\": 2,\n"));
                });
        }

        private static void FinishesOnMovieExhaustion()
        {
            WithMovie(
                Rows(5),
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 2)
                        {
                            ram.SetByte(0xF600, 0x0C);
                        }
                    });

                    var physics = new StringWriter();
                    var metadata = new StringWriter();
                    S1TraceCaptureResult result = S1TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "2026-07-13",
                        physics,
                        new StringWriter(),
                        metadata);

                    AssertEx.Equal(2, result.Bk2FrameOffset);
                    AssertEx.Equal(3, result.TraceFrameCount);
                    AssertEx.Equal(5, host.AdvanceCount);
                    AssertEx.Equal(
                        5,
                        physics.ToString().Split('\n').Length);
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"trace_frame_count\": 3,\n"));
                });
        }

        private static void WaitsForControlLockReleaseBeforeStarting()
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
                            ram.SetU16(0xD03E, 0);
                        }
                    });
                    host.Ram.SetU16(0xD03E, 2);

                    var metadata = new StringWriter();
                    S1TraceCaptureResult result = S1TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "2026-07-13",
                        new StringWriter(),
                        new StringWriter(),
                        metadata);

                    AssertEx.Equal(4, result.Bk2FrameOffset);
                    AssertEx.Equal(4, result.TraceFrameCount);
                    AssertEx.Equal(
                        true,
                        metadata.ToString().Contains(
                            "  \"bk2_frame_offset\": 4,\n"));
                });
        }

        private static void AppliesButtonsPerRowInSmokeRunnerOrder()
        {
            WithMovie(
                new[]
                {
                    "|P.|UDLRABCS|........|",
                    "|.R|.D...B..|........|"
                },
                movie =>
                {
                    var host = new ScriptedHost((ram, completedFrame) =>
                    {
                        if (completedFrame == 1)
                        {
                            ram.SetByte(0xF600, 0x0C);
                        }
                    });

                    S1TraceCaptureRunner.Capture(
                        movie,
                        host,
                        "2026-07-13",
                        new StringWriter(),
                        new StringWriter(),
                        new StringWriter());

                    string[] expected =
                    {
                        "ClearButtons",
                        "SetButton(\"Power\", true)",
                        "SetButton(\"P1 Up\", true)",
                        "SetButton(\"P1 Down\", true)",
                        "SetButton(\"P1 Left\", true)",
                        "SetButton(\"P1 Right\", true)",
                        "SetButton(\"P1 A\", true)",
                        "SetButton(\"P1 B\", true)",
                        "SetButton(\"P1 C\", true)",
                        "SetButton(\"P1 Start\", true)",
                        "Advance",
                        "ClearButtons",
                        "SetButton(\"Reset\", true)",
                        "SetButton(\"P1 Down\", true)",
                        "SetButton(\"P1 B\", true)",
                        "Advance"
                    };
                    AssertEx.Equal(
                        string.Join("\n", expected),
                        string.Join("\n", host.Calls));
                });
        }

        private static void FailsClearlyWhenDetectionNeverFires()
        {
            WithMovie(
                Rows(3),
                movie =>
                {
                    var host = new ScriptedHost(null);
                    var physics = new StringWriter();
                    var aux = new StringWriter();
                    var metadata = new StringWriter();
                    AssertEx.Throws<InvalidDataException>(
                        () => S1TraceCaptureRunner.Capture(
                            movie,
                            host,
                            "2026-07-13",
                            physics,
                            aux,
                            metadata),
                        "Start detection never fired");
                    AssertEx.Equal(3, host.AdvanceCount);
                    AssertEx.Equal(string.Empty, physics.ToString());
                    AssertEx.Equal(string.Empty, aux.ToString());
                    AssertEx.Equal(string.Empty, metadata.ToString());
                });
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
                "openggf-trace-" + Guid.NewGuid().ToString("N"));
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
        /// frame into vfc (0xFE04) and the player position words (0xD008 /
        /// 0xD00C become 0x0100 + frame / 0x0300 + frame), then runs the
        /// per-advance script. Button/advance calls are logged; RAM reads
        /// are not.
        /// </summary>
        private sealed class ScriptedHost : IGpgxHost
        {
            private readonly Action<RamAccess, int> onAdvance;

            public ScriptedHost(Action<RamAccess, int> onAdvance)
            {
                this.onAdvance = onAdvance;
                Ram = new RamAccess();
                Calls = new List<string>();
            }

            public RamAccess Ram { get; private set; }
            public int CompletedFrame { get; private set; }
            public int AdvanceCount { get; private set; }
            public List<string> Calls { get; private set; }

            public void ClearButtons()
            {
                Calls.Add("ClearButtons");
            }

            public void SetButton(string name, bool pressed)
            {
                Calls.Add(
                    "SetButton(\"" + name + "\", "
                    + pressed.ToString().ToLowerInvariant() + ")");
            }

            public void Advance()
            {
                Calls.Add("Advance");
                AdvanceCount++;
                CompletedFrame++;
                Ram.SetU16(0xFE04, (ushort)CompletedFrame);
                Ram.SetU16(0xD008, (ushort)(0x0100 + CompletedFrame));
                Ram.SetU16(0xD00C, (ushort)(0x0300 + CompletedFrame));
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

        private sealed class RamAccess
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
