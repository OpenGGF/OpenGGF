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
    /// "9.13-s2" (the v9.12/v9.13 Lua headers declare plain-mode output
    /// byte-identical to 9.11-s2 except that string). A fourth gate runs
    /// one --run-id capture against
    /// src/test/resources/traces/s2/runs/s2-ehz-halfpipe-roundtrip/ —
    /// injecting the canonical capture session's movie-length signal via
    /// --effective-movie-length (see RunEffectiveMovieLength) — and
    /// asserts all five segment directories plus run_manifest.json (see
    /// the run-mode constants below). Skips (does not pass) when
    /// S2_ROM_PATH or a BizHawk distribution is absent; fails (does not
    /// skip) on any hash mismatch.
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
        // Run fixtures were captured by the v9.12 Lua; the v9.13 header
        // declares those shapes byte-identical apart from this string.
        private const string RunFixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.12-s2\",";
        private const string ProducedLuaScriptVersionLine =
            "  \"lua_script_version\": \"9.13-s2\",";

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

        // Run-mode gate: one native --run-id capture of the canonical EHZ
        // halfpipe round-trip movie (level -> ss -> level -> ss -> level)
        // must reproduce all five segment directories and the run manifest.
        // The fixture set is stamped lua_script_version 9.12-s2 and carries
        // the canonical capture's Windows text-mode CRLF line endings
        // (docs/s2-run-mode-behavior.md §9), so physics/aux bytes are
        // asserted without any normalization; each segment metadata.json
        // and run_manifest.json are normalized on the recording_date value
        // (metadata only) and the 9.12-s2 -> 9.13-s2 version line.
        private const string RunFixtureDirectoryName =
            "s2-ehz-halfpipe-roundtrip";
        private const string RunMovieFileName =
            "s2-ehz-halfpipe-roundtrip.bk2";
        private const int RunMovieFrameCount = 22819;

        // The canonical capture session's movie-length signal
        // (s2-run-mode-behavior.md §2 capture-time caveat): EmuHawk's
        // chromeless movie.length() under-reported the committed BK2's
        // 22819 rows by 207 frames, so the Lua's 4b movie-done guard ended
        // the run at emu frame 22612 while seg3 was armed — before the
        // movie's tail act-transition reload ($8C at the same frame) could
        // be seen. Reproducing the fixture therefore requires injecting the
        // session value; a v9.13 runner fed the file-derived 22819 would
        // instead survive the reload and record a sixth segment (seg4_ehz2)
        // plus a level_advance transition.
        private const int RunEffectiveMovieLength = 22612;
        private const string RunManifestSha256 =
            "aabe4597821eb8223266728f44730a5a15321bad167ebc56d8569f09d5"
            + "cb0cf1";

        private static readonly S2RunSegmentCase[] RunSegmentCases =
        {
            new S2RunSegmentCase(
                "seg1_ehz1",
                "level",
                825,
                2969,
                "19f1712ccd56f95724c50256efefb49e3b65531bea864cada32a3178"
                + "d4da7320",
                "bfb475c238449f8844aec22612b25f6ac5c131db25aec98244bf6de4"
                + "5f2c3d55"),
            new S2RunSegmentCase(
                "ss",
                "special_stage",
                3795,
                5733,
                "9c2ed10bf732f76398b20e1763ddfbb5ed3df0b66394e68a78f8ec53"
                + "00129d1b",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b"
                + "7852b855"),
            new S2RunSegmentCase(
                "seg2_ehz1",
                "level",
                9701,
                2903,
                "6e373f9cb786391813f8d50dff5bfbd57575cf525c5f272e2aca510a"
                + "70725c45",
                "307aa3380304204e02576b85b6c6886fda9b772f1b1d39a2ee90d8cf"
                + "f734d05b"),
            new S2RunSegmentCase(
                "ss_2",
                "special_stage",
                12605,
                6381,
                "13c6ea30eae9361bfb9e7c03b2cfb50bb3193d2a7a5809df780d8cd3"
                + "e5bd84ab",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b"
                + "7852b855"),
            new S2RunSegmentCase(
                "seg3_ehz1",
                "level",
                19159,
                3452,
                "7632445f5ef5cdc1c429db3b375f95c4c34198c2abd2a86f81a49b69"
                + "3a50aea6",
                "6538660383c358770246b1a628ba89bd83969a61bca7059077a855e0"
                + "f5cd5259")
        };

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
            tests.Add(new TestMain.TestCase(
                "S2TraceDifferential native run mode capture matches"
                + " canonical halfpipe round trip",
                NativeRunModeCaptureMatchesCanonicalRun));
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

        /// <summary>
        /// One run-mode segment expectation: the segment directory token
        /// under the run output root, the manifest kind the CLI reports
        /// for it on stdout, the canonical BK2 frame offset and trace
        /// frame count, and the canonical sha256 hashes of the segment's
        /// physics.csv and aux_state.jsonl bytes (CRLF line endings
        /// included; the special-stage aux hash is the empty file's).
        /// </summary>
        private sealed class S2RunSegmentCase
        {
            public S2RunSegmentCase(
                string dirToken,
                string kind,
                int bk2FrameOffset,
                int traceFrameCount,
                string physicsSha256,
                string auxStateSha256)
            {
                DirToken = dirToken;
                Kind = kind;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                PhysicsSha256 = physicsSha256;
                AuxStateSha256 = auxStateSha256;
            }

            public string DirToken { get; private set; }
            public string Kind { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        private static void NativeRunModeCaptureMatchesCanonicalRun()
        {
            S2DifferentialDependencies dependencies =
                ResolveS2DifferentialDependencies();
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            string runDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s2",
                "runs",
                RunFixtureDirectoryName);
            string moviePath = Path.GetFullPath(Path.Combine(
                runDirectory,
                RunMovieFileName));

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s2-run-differential-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                // Self-check the canonical fixture bytes first so a hash
                // mismatch reports whether the fixture or the capture
                // diverged. Gzipped fixtures are decompressed read-only
                // into the temp root; the fixtures are never modified.
                Directory.CreateDirectory(root);
                foreach (S2RunSegmentCase segment in RunSegmentCases)
                {
                    string scratch = Path.Combine(
                        root,
                        "fixture-" + segment.DirToken);
                    Directory.CreateDirectory(scratch);
                    AssertSha256(
                        segment.DirToken + "/physics.csv (fixture)",
                        segment.PhysicsSha256,
                        EndToEndTests.ComputeSha256(MaterializeFixture(
                            Path.Combine(runDirectory, segment.DirToken),
                            "physics.csv",
                            Path.Combine(scratch, "physics.csv"))));
                    AssertSha256(
                        segment.DirToken + "/aux_state.jsonl (fixture)",
                        segment.AuxStateSha256,
                        EndToEndTests.ComputeSha256(MaterializeFixture(
                            Path.Combine(runDirectory, segment.DirToken),
                            "aux_state.jsonl",
                            Path.Combine(scratch, "aux_state.jsonl"))));
                }
                AssertSha256(
                    "run_manifest.json (fixture)",
                    RunManifestSha256,
                    EndToEndTests.ComputeSha256(Path.Combine(
                        runDirectory,
                        "run_manifest.json")));

                string stdout = RunRunModeCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output);

                AssertEx.Equal(
                    ExpectedRunStdout(installation, output),
                    stdout);
                foreach (S2RunSegmentCase segment in RunSegmentCases)
                {
                    AssertSha256(
                        segment.DirToken + "/physics.csv",
                        segment.PhysicsSha256,
                        EndToEndTests.ComputeSha256(Path.Combine(
                            output,
                            segment.DirToken,
                            "physics.csv")));
                    AssertSha256(
                        segment.DirToken + "/aux_state.jsonl",
                        segment.AuxStateSha256,
                        EndToEndTests.ComputeSha256(Path.Combine(
                            output,
                            segment.DirToken,
                            "aux_state.jsonl")));
                    AssertNormalizedRunMetadataEquality(
                        Path.Combine(
                            runDirectory,
                            segment.DirToken,
                            "metadata.json"),
                        Path.Combine(
                            output,
                            segment.DirToken,
                            "metadata.json"));
                }
                AssertNormalizedRunManifestEquality(
                    Path.Combine(runDirectory, "run_manifest.json"),
                    Path.Combine(output, "run_manifest.json"));
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
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

        private static string RunRunModeCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output)
        {
            string arguments =
                EndToEndTests.Quote(
                    Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                + " --mode trace"
                + " --rom " + EndToEndTests.Quote(romPath)
                + " --movie " + EndToEndTests.Quote(moviePath)
                + " --output " + EndToEndTests.Quote(output)
                + " --run-id " + RunFixtureDirectoryName
                + " --effective-movie-length " + RunEffectiveMovieLength;
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
                    "Run capture exited " + result.ExitCode + ". stderr: "
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

        private static string ExpectedRunStdout(
            BizHawkInstallation installation,
            string output)
        {
            string expected =
                "BizHawk: " + installation.ManagedVersion + "\n"
                + "ROM SHA-1: " + RomIdentity.Sonic2Rev01Sha1 + "\n"
                + "Movie frames: " + RunMovieFrameCount + "\n"
                + "Effective movie length: " + RunEffectiveMovieLength + "\n"
                + "Run ID: " + RunFixtureDirectoryName + "\n"
                + "Segments: " + RunSegmentCases.Length + "\n"
                + "Transitions: " + (RunSegmentCases.Length - 1) + "\n";
            foreach (S2RunSegmentCase segment in RunSegmentCases)
            {
                expected += "Segment " + segment.DirToken
                    + ": kind=" + segment.Kind
                    + ", BK2 frame offset=" + segment.Bk2FrameOffset
                    + ", trace frames=" + segment.TraceFrameCount + "\n";
            }
            expected += "Run manifest: "
                + Path.Combine(output, "run_manifest.json") + "\n";
            return expected;
        }

        /// <summary>
        /// First-divergence hash assertion: names the diverging file so a
        /// run-gate failure reports which of the eleven canonical byte
        /// sets broke first.
        /// </summary>
        private static void AssertSha256(
            string context,
            string expected,
            string actual)
        {
            if (expected != actual)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": expected sha256 <"
                    + expected + "> but was <" + actual + ">.");
            }
        }

        /// <summary>
        /// Asserts the produced metadata.json is byte-identical to the
        /// fixture's except for the two permitted normalizations: the
        /// recording_date value (which must still carry the exact key
        /// formatting and an ISO date value), and the fixture's
        /// lua_script_version "9.11-s2" line, which the native port must
        /// produce as exactly "9.13-s2".
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

        /// <summary>
        /// Run-mode variant of the metadata comparison: run fixture
        /// metadata.json files carry the canonical capture's Windows
        /// text-mode CRLF line endings and are stamped lua_script_version
        /// 9.12-s2, so the produced file must be byte-identical except the
        /// recording_date value (which must still carry the exact key
        /// formatting and an ISO date value) and the version line, which
        /// the native port must produce as exactly "9.13-s2".
        /// </summary>
        private static void AssertNormalizedRunMetadataEquality(
            string fixturePath,
            string producedPath)
        {
            CompareRunLines(fixturePath, producedPath, true);
        }

        /// <summary>
        /// run_manifest.json comparison: the fixture manifest carries CRLF
        /// line endings and the 9.12-s2 stamp; the produced manifest must
        /// be byte-identical except the version line ("9.13-s2"). There is
        /// no recording_date in the manifest.
        /// </summary>
        private static void AssertNormalizedRunManifestEquality(
            string fixturePath,
            string producedPath)
        {
            CompareRunLines(fixturePath, producedPath, false);
        }

        private static void CompareRunLines(
            string fixturePath,
            string producedPath,
            bool expectRecordingDate)
        {
            string fixtureText = File.ReadAllText(fixturePath);
            string producedText = File.ReadAllText(producedPath);
            // CRLF-only: no lone LF or CR may remain once CRLF pairs are
            // removed, and both files end with a CRLF-terminated line.
            AssertEx.Equal(
                false,
                fixtureText.Replace("\r\n", "").IndexOfAny(
                    new[] { '\r', '\n' }) >= 0);
            AssertEx.Equal(
                false,
                producedText.Replace("\r\n", "").IndexOfAny(
                    new[] { '\r', '\n' }) >= 0);
            AssertEx.Equal(true, fixtureText.EndsWith("\r\n"));
            AssertEx.Equal(true, producedText.EndsWith("\r\n"));

            string[] fixtureLines = fixtureText.Split(
                new[] { "\r\n" },
                StringSplitOptions.None);
            string[] producedLines = producedText.Split(
                new[] { "\r\n" },
                StringSplitOptions.None);
            AssertEx.Equal(fixtureLines.Length, producedLines.Length);
            var recordingDateLines = 0;
            var luaScriptVersionLines = 0;
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (expectRecordingDate
                    && fixtureLines[index].StartsWith(
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
                else if (fixtureLines[index]
                    == RunFixtureLuaScriptVersionLine)
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
            AssertEx.Equal(expectRecordingDate ? 1 : 0, recordingDateLines);
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
