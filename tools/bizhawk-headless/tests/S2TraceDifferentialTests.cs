using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gates proving the native S2 trace capture reproduces
    /// the Lua recorder byte-for-byte against the canonical fixtures:
    /// the plain gameplay_unlock profile against
    /// src/test/resources/traces/s2/ehz1_fullrun/, and
    /// level_gated_reset_aware segment selection against
    /// src/test/resources/traces/s2/arz/ (segment 0) and
    /// src/test/resources/traces/s2/arz2/ (segment 1) — both ARZ fixtures
    /// were recorded from the same s2-lvl-select-ARZ.bk2 level-select
    /// movie, so the two cases prove segment skipping as well as capture.
    /// Each case runs the trace-mode CLI end-to-end through run.sh (game
    /// auto-detected from the S2 ROM) and asserts the canonical
    /// physics.csv and aux_state.jsonl sha256 hashes (gzipped fixtures are
    /// decompressed to a temp file first; the fixtures themselves are
    /// never touched), the exact detected BK2 frame offset, and
    /// metadata.json equality normalized only on the recording_date value
    /// and the fixture's lua_script_version "9.11-s2" being produced as
    /// "9.12-s2" (the v9.12 Lua header declares plain-mode output
    /// byte-identical to 9.11-s2 except that string). Skips (does not
    /// pass) when S2_ROM_PATH or a BizHawk distribution is absent; fails
    /// (does not skip) on any hash mismatch.
    /// </summary>
    internal static class S2TraceDifferentialTests
    {
        private const int CaptureTimeoutMilliseconds = 600000;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");
        private const string FixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.11-s2\",";
        private const string ProducedLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.12-s2\",";

        private static readonly S2DifferentialCase Ehz1Case =
            new S2DifferentialCase(
                "ehz1_fullrun",
                "s2-ehz1.bk2",
                null,
                null,
                "gameplay_unlock",
                0,
                899,
                5852,
                6778,
                "efeb90112d36f897317f688881140c042792a2b640cf8313470216db"
                + "91f57a83",
                "5522e70caa8134570eb5acdcfc3c188655d929b2e777101ae7078516"
                + "8e122dc2");

        // Both ARZ segment cases replay the same level-select movie
        // (checked in under traces/s2/arz/); only --gameplay-segment
        // differs, so segment 1 also proves the skip-counted-segment path.
        private static readonly S2DifferentialCase ArzSegment0Case =
            new S2DifferentialCase(
                "arz",
                "s2-lvl-select-ARZ.bk2",
                "level_gated_reset_aware",
                0,
                "level_gated_reset_aware",
                0,
                2752,
                5073,
                15853,
                "72c0a49ca19e26248889aee82e68b3cd7a2f503965c1ae80eb1be16e"
                + "a01578ec",
                "390dc8862377ffb8c77c72d75938acbe1a06bf72cf94392b2ffdd2dd"
                + "6929d772");

        private static readonly S2DifferentialCase ArzSegment1Case =
            new S2DifferentialCase(
                "arz2",
                Path.Combine("..", "arz", "s2-lvl-select-ARZ.bk2"),
                "level_gated_reset_aware",
                1,
                "level_gated_reset_aware",
                1,
                7998,
                7809,
                15853,
                "83056cfcb9b059165fdd8710d7d510c9db249700a57d287610ce02d5"
                + "2ac35451",
                "bae3b1654a7356dbbc6729e56767c0e0718e842163ecc236f1c60c51"
                + "21b9c1e8");

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native capture matches canonical EHZ1"
                + " trace",
                () => NativeCaptureMatchesCanonicalTrace(Ehz1Case)));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native segment 0 capture matches"
                + " canonical ARZ trace",
                () => NativeCaptureMatchesCanonicalTrace(ArzSegment0Case)));
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native segment 1 capture matches"
                + " canonical ARZ2 trace",
                () => NativeCaptureMatchesCanonicalTrace(ArzSegment1Case)));
        }

        /// <summary>
        /// One canonical fixture comparison: the fixture directory name
        /// under src/test/resources/traces/s2/, the movie path relative to
        /// that directory, the optional --trace-profile /
        /// --gameplay-segment CLI arguments (null = argument omitted), the
        /// profile and segment values the CLI must report, the canonical
        /// BK2 frame offset / trace frame count / movie frame count, and
        /// the canonical sha256 hashes of the Lua recorder's physics.csv
        /// and aux_state.jsonl bytes.
        /// </summary>
        private sealed class S2DifferentialCase
        {
            public S2DifferentialCase(
                string fixtureDirectoryName,
                string movieRelativePath,
                string traceProfileArgument,
                int? gameplaySegmentArgument,
                string expectedTraceProfile,
                int expectedGameplaySegment,
                int bk2FrameOffset,
                int traceFrameCount,
                int movieFrameCount,
                string physicsSha256,
                string auxStateSha256)
            {
                FixtureDirectoryName = fixtureDirectoryName;
                MovieRelativePath = movieRelativePath;
                TraceProfileArgument = traceProfileArgument;
                GameplaySegmentArgument = gameplaySegmentArgument;
                ExpectedTraceProfile = expectedTraceProfile;
                ExpectedGameplaySegment = expectedGameplaySegment;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                MovieFrameCount = movieFrameCount;
                PhysicsSha256 = physicsSha256;
                AuxStateSha256 = auxStateSha256;
            }

            public string FixtureDirectoryName { get; private set; }
            public string MovieRelativePath { get; private set; }
            public string TraceProfileArgument { get; private set; }
            public int? GameplaySegmentArgument { get; private set; }
            public string ExpectedTraceProfile { get; private set; }
            public int ExpectedGameplaySegment { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public int MovieFrameCount { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        private static void NativeCaptureMatchesCanonicalTrace(
            S2DifferentialCase differentialCase)
        {
            S2DifferentialDependencies dependencies =
                ResolveS2DifferentialDependencies();
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            string traceDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s2",
                differentialCase.FixtureDirectoryName);
            string moviePath = Path.GetFullPath(Path.Combine(
                traceDirectory,
                differentialCase.MovieRelativePath));

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s2-trace-differential-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                // Canonical fixture bytes may ship plain or gzipped;
                // gzipped ones are decompressed read-only into the temp
                // root so the canonical hashes are asserted against the
                // exact bytes the Lua recorder wrote. The fixtures
                // themselves are never modified.
                Directory.CreateDirectory(root);
                AssertEx.Equal(
                    differentialCase.PhysicsSha256,
                    EndToEndTests.ComputeSha256(MaterializeFixture(
                        traceDirectory,
                        "physics.csv",
                        Path.Combine(root, "fixture-physics.csv"))));
                AssertEx.Equal(
                    differentialCase.AuxStateSha256,
                    EndToEndTests.ComputeSha256(MaterializeFixture(
                        traceDirectory,
                        "aux_state.jsonl",
                        Path.Combine(root, "fixture-aux_state.jsonl"))));

                string stdout = RunTraceCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output,
                    differentialCase);

                AssertEx.Equal(
                    ExpectedStdout(installation, output, differentialCase),
                    stdout);
                AssertEx.Equal(
                    differentialCase.PhysicsSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(output, "physics.csv")));
                AssertEx.Equal(
                    differentialCase.AuxStateSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(output, "aux_state.jsonl")));
                AssertNormalizedMetadataEquality(
                    Path.Combine(traceDirectory, "metadata.json"),
                    Path.Combine(output, "metadata.json"));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        /// <summary>
        /// Returns a readable path holding the canonical bytes of the
        /// named fixture file: the plain file when it exists, otherwise
        /// its .gz sibling decompressed to the given scratch path.
        /// </summary>
        private static string MaterializeFixture(
            string traceDirectory,
            string fileName,
            string scratchPath)
        {
            string plainPath = Path.Combine(traceDirectory, fileName);
            if (File.Exists(plainPath))
            {
                return plainPath;
            }
            Gunzip(plainPath + ".gz", scratchPath);
            return scratchPath;
        }

        /// <summary>
        /// Resolves the S2 ROM (S2_ROM_PATH) and BizHawk installation
        /// (BIZHAWK_HOME, falling back to the repository's
        /// docs/BizHawk-2.11-linux-x64) with the S1 differential gate's
        /// semantics: absent inputs skip, present-but-invalid inputs fail.
        /// </summary>
        private static S2DifferentialDependencies
            ResolveS2DifferentialDependencies()
        {
            string suppliedRomPath =
                Environment.GetEnvironmentVariable("S2_ROM_PATH");
            string suppliedBizHawkHome =
                Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            string fallbackBizHawkHome = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "docs",
                "BizHawk-2.11-linux-x64");

            var missing = new List<string>();
            string romPath = null;
            if (string.IsNullOrEmpty(suppliedRomPath))
            {
                missing.Add("S2_ROM_PATH is not set");
            }
            else
            {
                romPath = Path.GetFullPath(suppliedRomPath);
            }

            string bizHawkHome = null;
            if (!string.IsNullOrEmpty(suppliedBizHawkHome))
            {
                bizHawkHome = Path.GetFullPath(suppliedBizHawkHome);
            }
            else if (Directory.Exists(fallbackBizHawkHome))
            {
                bizHawkHome = Path.GetFullPath(fallbackBizHawkHome);
            }
            else
            {
                missing.Add("BizHawk distribution is not installed");
            }

            if (missing.Count != 0)
            {
                throw new TestMain.SkipTestException(
                    string.Join("; ", missing.ToArray()));
            }

            // Present inputs are validated, not skipped over.
            if (!File.Exists(romPath))
            {
                throw new InvalidOperationException(
                    "Supplied S2_ROM_PATH does not exist: " + romPath + ".");
            }
            RomIdentity.ValidateSonic2Rev01(File.ReadAllBytes(romPath));
            BizHawkInstallation.Validate(bizHawkHome);
            return new S2DifferentialDependencies(romPath, bizHawkHome);
        }

        private static string RunTraceCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output,
            S2DifferentialCase differentialCase)
        {
            string arguments =
                EndToEndTests.Quote(
                    Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                + " --mode trace"
                + " --rom " + EndToEndTests.Quote(romPath)
                + " --movie " + EndToEndTests.Quote(moviePath)
                + " --output " + EndToEndTests.Quote(output);
            if (differentialCase.TraceProfileArgument != null)
            {
                arguments += " --trace-profile "
                    + differentialCase.TraceProfileArgument;
            }
            if (differentialCase.GameplaySegmentArgument.HasValue)
            {
                arguments += " --gameplay-segment "
                    + differentialCase.GameplaySegmentArgument.Value;
            }
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments = arguments,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.EnvironmentVariables["BIZHAWK_HOME"] = bizHawkHome;
            start.EnvironmentVariables["DISPLAY"] = ":99";
            EndToEndTests.ProcessResult result = EndToEndTests.RunProcess(
                start,
                CaptureTimeoutMilliseconds);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException(
                    "Trace capture exited " + result.ExitCode + ". stderr: "
                    + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        private static string ExpectedStdout(
            BizHawkInstallation installation,
            string output,
            S2DifferentialCase differentialCase)
        {
            return
                "BizHawk: " + installation.ManagedVersion + "\n"
                + "ROM SHA-1: " + RomIdentity.Sonic2Rev01Sha1 + "\n"
                + "Movie frames: "
                + differentialCase.MovieFrameCount + "\n"
                + "Trace profile: "
                + differentialCase.ExpectedTraceProfile + "\n"
                + "Gameplay segment: "
                + differentialCase.ExpectedGameplaySegment + "\n"
                + "BK2 frame offset: "
                + differentialCase.Bk2FrameOffset + "\n"
                + "Trace frames: "
                + differentialCase.TraceFrameCount + "\n"
                + "Physics CSV: "
                + Path.Combine(output, "physics.csv") + "\n"
                + "Aux state JSONL: "
                + Path.Combine(output, "aux_state.jsonl") + "\n"
                + "Metadata JSON: "
                + Path.Combine(output, "metadata.json") + "\n";
        }

        /// <summary>
        /// Asserts the produced metadata.json is byte-identical to the
        /// fixture's except for the two permitted normalizations: the
        /// recording_date value (which must still carry the exact key
        /// formatting and an ISO date value), and the fixture's
        /// lua_script_version "9.11-s2" line, which the native port must
        /// produce as exactly "9.12-s2".
        /// </summary>
        private static void AssertNormalizedMetadataEquality(
            string fixturePath,
            string producedPath)
        {
            string fixtureText = File.ReadAllText(fixturePath);
            string producedText = File.ReadAllText(producedPath);
            AssertEx.Equal(false, fixtureText.IndexOf('\r') >= 0);
            AssertEx.Equal(false, producedText.IndexOf('\r') >= 0);
            AssertEx.Equal(true, fixtureText.EndsWith("\n"));
            AssertEx.Equal(true, producedText.EndsWith("\n"));

            string[] fixtureLines = fixtureText.Split('\n');
            string[] producedLines = producedText.Split('\n');
            AssertEx.Equal(fixtureLines.Length, producedLines.Length);
            var recordingDateLines = 0;
            var luaScriptVersionLines = 0;
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (fixtureLines[index].StartsWith(
                    RecordingDateLinePrefix,
                    StringComparison.Ordinal))
                {
                    recordingDateLines++;
                    if (!RecordingDateLine.IsMatch(producedLines[index]))
                    {
                        throw new InvalidOperationException(
                            "Produced recording_date line is malformed: <"
                            + producedLines[index] + ">.");
                    }
                }
                else if (fixtureLines[index] == FixtureLuaScriptVersionLine)
                {
                    luaScriptVersionLines++;
                    AssertEx.Equal(
                        ProducedLuaScriptVersionLine,
                        producedLines[index]);
                }
                else
                {
                    AssertEx.Equal(
                        fixtureLines[index],
                        producedLines[index]);
                }
            }
            AssertEx.Equal(1, recordingDateLines);
            AssertEx.Equal(1, luaScriptVersionLines);
        }

        private static void Gunzip(string sourcePath, string destinationPath)
        {
            using (FileStream source = File.OpenRead(sourcePath))
            using (var gzip = new GZipStream(
                source,
                CompressionMode.Decompress))
            using (FileStream destination = File.Create(destinationPath))
            {
                gzip.CopyTo(destination);
            }
        }

        private sealed class S2DifferentialDependencies
        {
            public S2DifferentialDependencies(
                string romPath,
                string bizHawkHome)
            {
                RomPath = romPath;
                BizHawkHome = bizHawkHome;
            }

            public string RomPath { get; private set; }
            public string BizHawkHome { get; private set; }
        }
    }
}
