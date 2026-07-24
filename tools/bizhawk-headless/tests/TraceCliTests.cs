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
            tests.Add(new TestMain.TestCase(
                "TraceCli validates the S2 trace arguments",
                ValidatesS2TraceArguments));
            tests.Add(new TestMain.TestCase(
                "TraceCli run mode refuses only an existing run manifest",
                RunModeRefusesOnlyExistingRunManifest));
            tests.Add(new TestMain.TestCase(
                "TraceCli rejects S2 arguments with the Sonic 1 ROM",
                RejectsS2ArgumentsWithSonic1Rom));
            tests.Add(new TestMain.TestCase(
                "TraceCli S2 trace run publishes with labeled stdout",
                S2TraceRunPublishesWithLabeledStdout));
            tests.Add(new TestMain.TestCase(
                "TraceCli S2 run mode publishes segments and manifest",
                S2RunModePublishesSegmentsAndManifest));
            tests.Add(new TestMain.TestCase(
                "TraceCli S2 run mode failure leaves no partial outputs",
                S2RunModeFailureLeavesNoPartialOutputs));
        }

        private static void ValidatesS2TraceArguments()
        {
            WithUnusedOutput(
                output =>
                {
                    // --run-id is mutually exclusive with the plain-mode
                    // selection arguments (Lua env semantics: the run
                    // capture procedure sets neither env var).
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--run-id", "run",
                            "--trace-profile", "gameplay_unlock")),
                        "--run-id cannot be combined with --trace-profile");
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--run-id", "run",
                            "--gameplay-segment", "1")),
                        "--run-id cannot be combined with"
                        + " --gameplay-segment");

                    AssertEx.Throws<ArgumentOutOfRangeException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--gameplay-segment", "-1")),
                        "--gameplay-segment must be at least 0");
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--gameplay-segment", "one")),
                        "--gameplay-segment must be an integer");

                    // The S2 selection arguments are trace-mode only.
                    foreach (string[] pair in new[]
                    {
                        new[] { "--trace-profile", "gameplay_unlock" },
                        new[] { "--gameplay-segment", "1" },
                        new[] { "--run-id", "run" }
                    })
                    {
                        AssertEx.Throws<ArgumentException>(
                            () => CommandLineOptions.Parse(Append(
                                SmokeArguments(output),
                                pair[0], pair[1])),
                            pair[0] + " is only supported in trace mode");
                    }

                    // Valid combinations parse.
                    CommandLineOptions plain =
                        CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--trace-profile", "level_gated_reset_aware",
                            "--gameplay-segment", "1"));
                    AssertEx.Equal(
                        "level_gated_reset_aware", plain.TraceProfile);
                    AssertEx.Equal(1, plain.GameplaySegment ?? -1);
                    AssertEx.Equal(null, plain.RunId);

                    CommandLineOptions run =
                        CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--run-id", "my-run"));
                    AssertEx.Equal("my-run", run.RunId);
                    AssertEx.Equal(null, run.TraceProfile);
                    AssertEx.Equal(false, run.GameplaySegment.HasValue);
                });
        }

        private static void RunModeRefusesOnlyExistingRunManifest()
        {
            WithUnusedOutput(
                output =>
                {
                    Directory.CreateDirectory(output);
                    string manifestPath = Path.Combine(
                        output,
                        "run_manifest.json");
                    File.WriteAllText(
                        manifestPath,
                        "{}\n",
                        new UTF8Encoding(false));

                    AssertEx.Throws<IOException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--run-id", "run")),
                        "already exists and will not be replaced: "
                        + manifestPath);

                    // A leftover manifest does not block plain trace mode,
                    // and leftover plain outputs do not block run mode.
                    CommandLineOptions plain =
                        CommandLineOptions.Parse(TraceArguments(output));
                    AssertEx.Equal(CaptureMode.Trace, plain.Mode);
                });
            WithUnusedOutput(
                output =>
                {
                    Directory.CreateDirectory(output);
                    File.WriteAllText(
                        Path.Combine(output, "physics.csv"),
                        "existing plain trace output\n",
                        new UTF8Encoding(false));

                    CommandLineOptions run =
                        CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--run-id", "run"));
                    AssertEx.Equal("run", run.RunId);
                });
        }

        private static void RejectsS2ArgumentsWithSonic1Rom()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                4,
                moviePath => WithUnusedOutput(
                    output =>
                    {
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
                                "--output", output,
                                "--trace-profile", "gameplay_unlock"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) =>
                                new ScriptedTraceHost(-1));

                        AssertEx.Equal(1, exitCode);
                        AssertEx.Equal(string.Empty, stdout.ToString());
                        AssertContains(
                            stderr.ToString(),
                            "only supported with the Sonic 2 ROM");
                        AssertEx.Equal(
                            false,
                            Directory.Exists(Path.GetFullPath(output))
                            && Directory.GetFileSystemEntries(
                                Path.GetFullPath(output)).Length > 0);
                    }));
        }

        private static void S2TraceRunPublishesWithLabeledStdout()
        {
            S2TraceCliDependencies dependencies = ResolveS2Dependencies();
            // 7 rows with detection at frame 3 yields 3 trace rows (the
            // movie's final input row is never consumed).
            WithSyntheticMovie(
                7,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        var host = new S2RunCaptureRunnerTests.FakeRunHost(
                            (h, frame) =>
                            {
                                if (frame == 3)
                                {
                                    h.Ram[0xF600] = 0x0C;
                                }
                            });
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

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        AssertEx.Equal(
                            "BizHawk: "
                            + dependencies.ManagedVersion
                            + "\n"
                            + "ROM SHA-1: "
                            + RomIdentity.Sonic2Rev01Sha1
                            + "\n"
                            + "Movie frames: 7\n"
                            + "Trace profile: gameplay_unlock\n"
                            + "Gameplay segment: 0\n"
                            + "BK2 frame offset: 3\n"
                            + "Trace frames: 3\n"
                            + "Physics CSV: "
                            + Path.Combine(fullOutput, "physics.csv") + "\n"
                            + "Aux state JSONL: "
                            + Path.Combine(fullOutput, "aux_state.jsonl")
                            + "\n"
                            + "Metadata JSON: "
                            + Path.Combine(fullOutput, "metadata.json")
                            + "\n",
                            stdout.ToString());

                        string physics = File.ReadAllText(
                            Path.Combine(fullOutput, "physics.csv"));
                        AssertEx.Equal(
                            true,
                            physics.StartsWith(
                                S2TraceCsvWriter.Header + "\n0000,"));
                        string metadata = File.ReadAllText(
                            Path.Combine(fullOutput, "metadata.json"));
                        AssertContains(
                            metadata,
                            "  \"trace_profile\": \"gameplay_unlock\",\n");
                        AssertContains(
                            metadata,
                            "  \"source_bk2\": \"synthetic.bk2\",\n"
                            + "  \"rom_checksum\": \"\",\n");
                    }));
        }

        private static void S2RunModePublishesSegmentsAndManifest()
        {
            S2TraceCliDependencies dependencies = ResolveS2Dependencies();
            WithSyntheticMovie(
                12,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        var host = new S2RunCaptureRunnerTests.FakeRunHost(
                            RoundTripSchedule);
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
                                "--output", output,
                                "--run-id", "cli-run"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) => host);

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        AssertEx.Equal(
                            "BizHawk: "
                            + dependencies.ManagedVersion
                            + "\n"
                            + "ROM SHA-1: "
                            + RomIdentity.Sonic2Rev01Sha1
                            + "\n"
                            + "Movie frames: 12\n"
                            + "Run ID: cli-run\n"
                            + "Segments: 3\n"
                            + "Transitions: 2\n"
                            + "Segment seg1_ehz1: kind=level,"
                            + " BK2 frame offset=3, trace frames=2\n"
                            + "Segment ss: kind=special_stage,"
                            + " BK2 frame offset=6, trace frames=2\n"
                            + "Segment seg2_ehz1: kind=level,"
                            + " BK2 frame offset=9, trace frames=2\n"
                            + "Run manifest: "
                            + Path.Combine(fullOutput, "run_manifest.json")
                            + "\n",
                            stdout.ToString());

                        string[] files = Directory.GetFiles(
                                fullOutput,
                                "*",
                                SearchOption.AllDirectories)
                            .Select(path =>
                                path.Substring(fullOutput.Length + 1))
                            .OrderBy(
                                name => name,
                                StringComparer.Ordinal)
                            .ToArray();
                        AssertEx.Equal(
                            "run_manifest.json,"
                            + "seg1_ehz1/aux_state.jsonl,"
                            + "seg1_ehz1/metadata.json,"
                            + "seg1_ehz1/physics.csv,"
                            + "seg2_ehz1/aux_state.jsonl,"
                            + "seg2_ehz1/metadata.json,"
                            + "seg2_ehz1/physics.csv,"
                            + "ss/aux_state.jsonl,"
                            + "ss/metadata.json,"
                            + "ss/physics.csv",
                            string.Join(",", files));

                        // The ss aux file exists and is byte-empty (§4).
                        AssertEx.Equal(
                            0L,
                            new FileInfo(Path.Combine(
                                fullOutput,
                                "ss",
                                "aux_state.jsonl")).Length);
                        // Run-mode files carry the canonical capture's
                        // Windows text-mode CRLF line endings
                        // (docs/s2-run-mode-behavior.md §9).
                        AssertContains(
                            File.ReadAllText(Path.Combine(
                                fullOutput,
                                "run_manifest.json")),
                            "  \"run_id\": \"cli-run\",\r\n");
                        AssertContains(
                            File.ReadAllText(Path.Combine(
                                fullOutput,
                                "seg2_ehz1",
                                "metadata.json")),
                            "  \"segment_index\": 2,\r\n");
                    }));
        }

        private static void S2RunModeFailureLeavesNoPartialOutputs()
        {
            S2TraceCliDependencies dependencies = ResolveS2Dependencies();
            WithSyntheticMovie(
                12,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        // A competing final inside a segment directory makes
                        // the multi-file publication fail after some links
                        // succeeded; every published final must be revoked.
                        Directory.CreateDirectory(
                            Path.Combine(output, "ss"));
                        string competingPath = Path.Combine(
                            output,
                            "ss",
                            "physics.csv");
                        File.WriteAllText(
                            competingPath,
                            "competing ss capture\n",
                            new UTF8Encoding(false));

                        var host = new S2RunCaptureRunnerTests.FakeRunHost(
                            RoundTripSchedule);
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
                                "--output", output,
                                "--run-id", "cli-run"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) => host);

                        AssertEx.Equal(1, exitCode);
                        AssertContains(
                            stderr.ToString(),
                            "already exists and will not be replaced");

                        string fullOutput = Path.GetFullPath(output);
                        string[] files = Directory.GetFiles(
                            fullOutput,
                            "*",
                            SearchOption.AllDirectories);
                        AssertEx.Equal(1, files.Length);
                        AssertEx.Equal(
                            Path.GetFullPath(competingPath),
                            Path.GetFullPath(files[0]));
                        AssertEx.Equal(
                            "competing ss capture\n",
                            File.ReadAllText(files[0]));
                    }));
        }

        /// <summary>
        /// Minimal round-trip schedule for a 12-row movie: arm at F=3, ss
        /// entry at F=6, exit + same-frame re-arm at F=9, movie-done guard
        /// at F=12. Yields segments seg1_ehz1/ss/seg2_ehz1 with offsets
        /// 3/6/9 and 2 rows each.
        /// </summary>
        private static void RoundTripSchedule(
            S2RunCaptureRunnerTests.FakeRunHost host,
            int frame)
        {
            if (frame == 3)
            {
                host.Ram[0xF600] = 0x0C;
            }
            if (frame == 6)
            {
                host.Ram[0xF600] = 0x10;
            }
            if (frame == 9)
            {
                host.Ram[0xF600] = 0x0C;
            }
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

        private static S2TraceCliDependencies ResolveS2Dependencies()
        {
            string romPath =
                Environment.GetEnvironmentVariable("S2_ROM_PATH");
            string bizHawkHome =
                Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            var missing = new List<string>();
            if (string.IsNullOrEmpty(romPath))
            {
                missing.Add("S2_ROM_PATH is not set");
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
            RomIdentity.ValidateSonic2Rev01(File.ReadAllBytes(romPath));
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(bizHawkHome);
            return new S2TraceCliDependencies(
                romPath,
                installation.ManagedVersion.ToString());
        }

        private sealed class S2TraceCliDependencies
        {
            public S2TraceCliDependencies(
                string romPath,
                string managedVersion)
            {
                RomPath = romPath;
                ManagedVersion = managedVersion;
            }

            public string RomPath { get; private set; }
            public string ManagedVersion { get; private set; }
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

            public bool IsLagged
            {
                get { return false; }
            }

            public int LagCount
            {
                get { return 0; }
            }

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
