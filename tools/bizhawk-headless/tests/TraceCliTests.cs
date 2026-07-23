using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Text;
using BizHawk.Headless.Gpgx;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class TraceCliTests
    {
        private const string LogKey =
            "LogKey:#Power|Reset|"
            + "#P1 Up|P1 Down|P1 Left|P1 Right|P1 A|P1 B|P1 C|P1 Start|"
            + "#P2 Up|P2 Down|P2 Left|P2 Right|P2 A|P2 B|P2 C|P2 Start|";

        private const string BlankRow = "|..|........|........|";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "TraceCli mode defaults to smoke and accepts explicit values",
                ModeDefaultsToSmokeAndAcceptsExplicitValues));
            tests.Add(new TestMain.TestCase(
                "TraceCli rejects unknown mode values",
                RejectsUnknownModeValues));
            tests.Add(new TestMain.TestCase(
                "TraceCli trace mode rejects smoke-only arguments",
                TraceModeRejectsSmokeOnlyArguments));
            tests.Add(new TestMain.TestCase(
                "TraceCli trace mode refuses each existing final output",
                TraceModeRefusesEachExistingFinalOutput));
            tests.Add(new TestMain.TestCase(
                "TraceCli modes only refuse their own existing outputs",
                ModesOnlyRefuseTheirOwnExistingOutputs));
            tests.Add(new TestMain.TestCase(
                "TraceCli trace run publishes three files with labeled stdout",
                TraceRunPublishesThreeFilesWithLabeledStdout));
            tests.Add(new TestMain.TestCase(
                "TraceCli trace run failure leaves no partial outputs",
                TraceRunFailureLeavesNoPartialOutputs));
        }

        private static void ModeDefaultsToSmokeAndAcceptsExplicitValues()
        {
            WithUnusedOutput(
                output =>
                {
                    CommandLineOptions defaults =
                        CommandLineOptions.Parse(SmokeArguments(output));
                    AssertEx.Equal(CaptureMode.Smoke, defaults.Mode);

                    CommandLineOptions explicitSmoke =
                        CommandLineOptions.Parse(Append(
                            SmokeArguments(output),
                            "--mode", "smoke",
                            "--bk2-frame-offset", "840",
                            "--max-frames", "5"));
                    AssertEx.Equal(CaptureMode.Smoke, explicitSmoke.Mode);
                    AssertEx.Equal(840, explicitSmoke.Bk2FrameOffset);
                    AssertEx.Equal(5, explicitSmoke.MaxFrames);

                    CommandLineOptions trace =
                        CommandLineOptions.Parse(TraceArguments(output));
                    AssertEx.Equal(CaptureMode.Trace, trace.Mode);
                    AssertEx.Equal(
                        Path.GetFullPath("game.gen"),
                        trace.RomPath);
                    AssertEx.Equal(
                        Path.GetFullPath("movie.bk2"),
                        trace.MoviePath);
                    AssertEx.Equal(
                        Path.GetFullPath(output),
                        trace.OutputDirectory);
                });
        }

        private static void RejectsUnknownModeValues()
        {
            WithUnusedOutput(
                output =>
                {
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            SmokeArguments(output),
                            "--mode", "record")),
                        "--mode");
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            SmokeArguments(output),
                            "--mode", "")),
                        "--mode");
                });
        }

        private static void TraceModeRejectsSmokeOnlyArguments()
        {
            WithUnusedOutput(
                output =>
                {
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--bk2-frame-offset", "840")),
                        "--bk2-frame-offset is not supported in trace mode");
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--max-frames", "1000")),
                        "--max-frames is not supported in trace mode");
                });
        }

        private static void TraceModeRefusesEachExistingFinalOutput()
        {
            foreach (string existingName in new[]
            {
                "physics.csv",
                "aux_state.jsonl",
                "metadata.json"
            })
            {
                WithUnusedOutput(
                    output =>
                    {
                        Directory.CreateDirectory(output);
                        string existingPath = Path.Combine(
                            output,
                            existingName);
                        byte[] original = { 0xDE, 0xAD, 0xBE, 0xEF };
                        File.WriteAllBytes(existingPath, original);

                        AssertEx.Throws<IOException>(
                            () => CommandLineOptions.Parse(
                                TraceArguments(output)),
                            "already exists and will not be replaced: "
                            + existingPath);
                        AssertEx.Equal(
                            "DE-AD-BE-EF",
                            BitConverter.ToString(
                                File.ReadAllBytes(existingPath)));
                    });
            }
        }

        private static void ModesOnlyRefuseTheirOwnExistingOutputs()
        {
            WithUnusedOutput(
                output =>
                {
                    Directory.CreateDirectory(output);
                    File.WriteAllText(
                        Path.Combine(output, "smoke.csv"),
                        "existing smoke output\n",
                        new UTF8Encoding(false));

                    // A leftover smoke capture must not block trace mode.
                    CommandLineOptions trace =
                        CommandLineOptions.Parse(TraceArguments(output));
                    AssertEx.Equal(CaptureMode.Trace, trace.Mode);
                });
            WithUnusedOutput(
                output =>
                {
                    Directory.CreateDirectory(output);
                    File.WriteAllText(
                        Path.Combine(output, "physics.csv"),
                        "existing trace output\n",
                        new UTF8Encoding(false));

                    // A leftover trace capture must not block smoke mode.
                    CommandLineOptions smoke =
                        CommandLineOptions.Parse(SmokeArguments(output));
                    AssertEx.Equal(CaptureMode.Smoke, smoke.Mode);
                });
        }

        private static void TraceRunPublishesThreeFilesWithLabeledStdout()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            // 7 rows with offset 3 yields 3 trace rows: the movie's final
            // input row is never consumed (Lua FINISHED parity).
            WithSyntheticMovie(
                7,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        var host = new ScriptedTraceHost(3);
                        var stdout = new StringWriter(
                            CultureInfo.InvariantCulture);
                        var stderr = new StringWriter(
                            CultureInfo.InvariantCulture);
                        string dateBefore = Today();

                        int exitCode = Program.Run(
                            new[]
                            {
                                "--mode", "trace",
                                "--rom", dependencies.RomPath,
                                "--movie", moviePath,
                                "--output", output
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) => host);

                        string dateAfter = Today();
                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        string physicsPath = Path.Combine(
                            fullOutput,
                            "physics.csv");
                        string auxStatePath = Path.Combine(
                            fullOutput,
                            "aux_state.jsonl");
                        string metadataPath = Path.Combine(
                            fullOutput,
                            "metadata.json");
                        AssertEx.Equal(
                            "BizHawk: "
                            + dependencies.ManagedVersion
                            + "\n"
                            + "ROM SHA-1: "
                            + RomIdentity.Sonic1Rev01Sha1
                            + "\n"
                            + "Movie frames: 7\n"
                            + "BK2 frame offset: 3\n"
                            + "Trace frames: 3\n"
                            + "Physics CSV: " + physicsPath + "\n"
                            + "Aux state JSONL: " + auxStatePath + "\n"
                            + "Metadata JSON: " + metadataPath + "\n",
                            stdout.ToString());

                        string[] entries = Directory
                            .GetFileSystemEntries(fullOutput)
                            .Select(Path.GetFileName)
                            .OrderBy(name => name, StringComparer.Ordinal)
                            .ToArray();
                        AssertEx.Equal(
                            "aux_state.jsonl,metadata.json,physics.csv",
                            string.Join(",", entries));

                        string[] physicsLines =
                            File.ReadAllText(physicsPath).Split('\n');
                        AssertEx.Equal(5, physicsLines.Length);
                        AssertEx.Equal(
                            S1TraceCsvWriter.Header,
                            physicsLines[0]);
                        AssertEx.Equal(
                            true,
                            physicsLines[1].StartsWith("0000,"));
                        AssertEx.Equal(
                            true,
                            physicsLines[3].StartsWith("0002,"));
                        AssertEx.Equal(string.Empty, physicsLines[4]);

                        string auxState = File.ReadAllText(auxStatePath);
                        AssertEx.Equal(
                            true,
                            auxState.StartsWith(
                                "{\"frame\":0,\"vfc\":4,"
                                + "\"event\":\"state_snapshot\","));
                        AssertEx.Equal(true, auxState.EndsWith("\n"));

                        string metadata = File.ReadAllText(metadataPath);
                        AssertContains(
                            metadata,
                            "  \"bk2_frame_offset\": 3,\n");
                        AssertContains(
                            metadata,
                            "  \"trace_frame_count\": 3,\n");
                        AssertContains(
                            metadata,
                            "  \"start_x\": \"0x0103\",\n");
                        bool dateMatches =
                            metadata.Contains(RecordingDateLine(dateBefore))
                            || metadata.Contains(
                                RecordingDateLine(dateAfter));
                        AssertEx.Equal(true, dateMatches);
                    }));
        }

        private static void TraceRunFailureLeavesNoPartialOutputs()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                4,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        // Game mode never reaches 0x0C, so start detection
                        // never fires and capture fails after staging began.
                        var host = new ScriptedTraceHost(-1);
                        var stdout = new StringWriter(
                            CultureInfo.InvariantCulture);
                        var stderr = new StringWriter(
                            CultureInfo.InvariantCulture);

                        int exitCode = Program.Run(
                            new[]
                            {
                                "--mode", "trace",
                                "--rom", dependencies.RomPath,
                                "--movie", moviePath,
                                "--output", output
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) => host);

                        AssertEx.Equal(1, exitCode);
                        AssertEx.Equal(string.Empty, stdout.ToString());
                        AssertContains(
                            stderr.ToString(),
                            "Start detection never fired");
                        AssertEx.Equal(
                            true,
                            Directory.Exists(Path.GetFullPath(output)));
                        AssertEx.Equal(
                            0,
                            Directory.GetFileSystemEntries(
                                Path.GetFullPath(output)).Length);
                    }));
        }

        private static string RecordingDateLine(string date)
        {
            return "  \"recording_date\": \"" + date + "\",\n";
        }

        private static string Today()
        {
            return DateTime.Now.ToString(
                "yyyy-MM-dd",
                CultureInfo.InvariantCulture);
        }

        private static string[] SmokeArguments(string output)
        {
            return new[]
            {
                "--rom", "game.gen",
                "--movie", "movie.bk2",
                "--output", output
            };
        }

        private static string[] TraceArguments(string output)
        {
            return Append(SmokeArguments(output), "--mode", "trace");
        }

        private static string[] Append(
            string[] source,
            params string[] values)
        {
            return source.Concat(values).ToArray();
        }

        private static void WithUnusedOutput(Action<string> body)
        {
            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-trace-cli-" + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "output");
            try
            {
                body(output);
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void WithSyntheticMovie(
            int rowCount,
            Action<string> body)
        {
            string directory = Path.Combine(
                Path.GetTempPath(),
                "openggf-trace-cli-movie-" + Guid.NewGuid().ToString("N"));
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
                        + string.Join(
                            "\r\n",
                            Enumerable.Repeat(BlankRow, rowCount))
                        + "\r\n[/Input]\r\n");
                }
                body(path);
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

        private static TraceCliDependencies ResolveDependencies()
        {
            string romPath =
                Environment.GetEnvironmentVariable("S1_ROM_PATH");
            string bizHawkHome =
                Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            var missing = new List<string>();
            if (string.IsNullOrEmpty(romPath))
            {
                missing.Add("S1_ROM_PATH is not set");
            }
            if (string.IsNullOrEmpty(bizHawkHome))
            {
                missing.Add("BIZHAWK_HOME is not set");
            }
            if (missing.Count != 0)
            {
                throw new TestMain.SkipTestException(
                    string.Join("; ", missing.ToArray()));
            }

            // Present inputs are validated, not skipped over.
            romPath = Path.GetFullPath(romPath);
            RomIdentity.ValidateSonic1Rev01(File.ReadAllBytes(romPath));
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(bizHawkHome);
            return new TraceCliDependencies(
                romPath,
                installation.ManagedVersion.ToString());
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

        private sealed class TraceCliDependencies
        {
            public TraceCliDependencies(
                string romPath,
                string managedVersion)
            {
                RomPath = romPath;
                ManagedVersion = managedVersion;
            }

            public string RomPath { get; private set; }
            public string ManagedVersion { get; private set; }
        }

        /// <summary>
        /// Fake host whose Advance() stamps the completed frame into vfc
        /// (0xFE04) and the player position words (0xD008 / 0xD00C become
        /// 0x0100 + frame / 0x0300 + frame). Game mode 0xF600 becomes 0x0C
        /// once <c>startFrame</c> frames have completed (never for -1).
        /// </summary>
        private sealed class ScriptedTraceHost : IGpgxHost
        {
            private readonly int startFrame;
            private readonly byte[] ram = new byte[0x10000];

            public ScriptedTraceHost(int startFrame)
            {
                this.startFrame = startFrame;
            }

            public int CompletedFrame { get; private set; }

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
                SetU16(0xD008, (ushort)(0x0100 + CompletedFrame));
                SetU16(0xD00C, (ushort)(0x0300 + CompletedFrame));
                if (CompletedFrame == startFrame)
                {
                    ram[0xF600] = 0x0C;
                }
            }

            public byte ReadMainRamByte(int offset)
            {
                return ram[offset];
            }

            public void Dispose()
            {
            }

            private void SetU16(int offset, ushort value)
            {
                ram[offset] = (byte)(value >> 8);
                ram[offset + 1] = (byte)value;
            }
        }
    }
}
