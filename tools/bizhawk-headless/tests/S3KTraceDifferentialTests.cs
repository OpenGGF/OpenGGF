using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gate proving the native S3K standard trace capture
    /// reproduces the Lua recorder (tools/bizhawk/s3k_trace_recorder.lua)
    /// byte-for-byte against the canonical AIZ end-to-end fixture
    /// src/test/resources/traces/s3k/aiz1_to_hcz_fullrun/. The case runs
    /// the trace-mode CLI end-to-end through run.sh (game auto-detected
    /// from the S3K locked-on ROM) with --trace-profile aiz_end_to_end and
    /// asserts:
    ///
    /// - the exact stdout contract, including BK2 frame offset 511 and
    ///   20798 trace frames (the AIZ movie's 21309 input rows exactly:
    ///   511 + 20798, so the run ends on the BK2-end guard);
    /// - physics.csv and aux_state.jsonl sha256 hashes with ZERO
    ///   normalization — the fixtures ship gzipped only, so the canonical
    ///   bytes are decompressed read-only into the temp root and hashed
    ///   there before the produced files are hashed. The fixtures under
    ///   src/test/resources/traces/ are never written to.
    /// - metadata.json line-for-line equality except the two deltas
    ///   pinned by docs/s3k-trace-recorder-behavior.md §6.2: the
    ///   recording_date value (nondeterministic) and the fixture's
    ///   lua_script_version "6.28-s3k" being produced as "6.30-s3k" (the
    ///   v6.29/v6.30 Lua commits changed nothing else in AIZ output, and
    ///   the AIZ physics.csv.gz was regenerated under the v6.30 input
    ///   rule). Both deltas are asserted as exact literals — never a loose
    ///   regex over the version, and no other line may differ.
    ///
    /// Skips (does not pass) when S3K_ROM_PATH, a BizHawk distribution, or
    /// the fixture directory is absent; fails (does not skip) on any
    /// mismatch.
    /// </summary>
    internal static class S3KTraceDifferentialTests
    {
        private const int CaptureTimeoutMilliseconds = 600000;
        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");

        // §6.2 pinned delta: the fixtures were stamped by Lua v6.28; the
        // native port stamps the v6.30 string the fixture bytes already
        // correspond to. Asserted as exact literals on both sides.
        private const string FixtureLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.28-s3k\",";
        private const string ProducedLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.30-s3k\",";

        private static readonly S3KDifferentialCase AizEndToEndCase =
            new S3KDifferentialCase(
                "aiz1_to_hcz_fullrun",
                "s3-aiz1-2-sonictails.bk2",
                "aiz_end_to_end",
                511,
                20798,
                21309,
                "f7b27ab88246de341e90b96e908331f1b5c7d338b825a46c619751401"
                + "76be456",
                "1fd26d37b3d07b5a95dd355bd56d9ba39b5cf63e68e93233320efd60"
                + "7646979e");

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KTraceDifferential native capture matches canonical AIZ"
                + " end-to-end trace",
                () => NativeCaptureMatchesCanonicalTrace(AizEndToEndCase)));
        }

        /// <summary>
        /// One canonical fixture comparison: the fixture directory name
        /// under src/test/resources/traces/s3k/, the movie file name inside
        /// it, the --trace-profile argument (also the value the CLI must
        /// echo), the canonical BK2 frame offset / trace frame count /
        /// movie frame count, and the canonical sha256 hashes of the Lua
        /// recorder's physics.csv and aux_state.jsonl bytes.
        /// </summary>
        private sealed class S3KDifferentialCase
        {
            public S3KDifferentialCase(
                string fixtureDirectoryName,
                string movieFileName,
                string traceProfile,
                int bk2FrameOffset,
                int traceFrameCount,
                int movieFrameCount,
                string physicsSha256,
                string auxStateSha256)
            {
                FixtureDirectoryName = fixtureDirectoryName;
                MovieFileName = movieFileName;
                TraceProfile = traceProfile;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                MovieFrameCount = movieFrameCount;
                PhysicsSha256 = physicsSha256;
                AuxStateSha256 = auxStateSha256;
            }

            public string FixtureDirectoryName { get; private set; }
            public string MovieFileName { get; private set; }
            public string TraceProfile { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public int MovieFrameCount { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        private static void NativeCaptureMatchesCanonicalTrace(
            S3KDifferentialCase differentialCase)
        {
            string traceDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k",
                differentialCase.FixtureDirectoryName);
            S3KDifferentialDependencies dependencies =
                ResolveS3KDifferentialDependencies(traceDirectory);
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);
            string moviePath = Path.Combine(
                traceDirectory,
                differentialCase.MovieFileName);

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s3k-trace-differential-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                // The canonical fixture bytes ship gzipped only; they are
                // decompressed read-only into the temp root so the pinned
                // hashes are asserted against the exact bytes the Lua
                // recorder wrote. The fixtures are never modified.
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
                AssertPinnedDeltaMetadataEquality(
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
        /// Returns a readable path holding the canonical bytes of the named
        /// fixture file: the plain file when it exists, otherwise its .gz
        /// sibling decompressed to the given scratch path.
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
        /// Resolves the S3K locked-on ROM (S3K_ROM_PATH), the BizHawk
        /// installation (BIZHAWK_HOME, falling back to the repository's
        /// docs/BizHawk-2.11-linux-x64), and the canonical fixture
        /// directory with the S1/S2 differential gates' semantics: absent
        /// inputs skip, present-but-invalid inputs fail.
        /// </summary>
        private static S3KDifferentialDependencies
            ResolveS3KDifferentialDependencies(string traceDirectory)
        {
            string suppliedRomPath =
                Environment.GetEnvironmentVariable("S3K_ROM_PATH");
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
                missing.Add("S3K_ROM_PATH is not set");
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

            if (!Directory.Exists(traceDirectory))
            {
                missing.Add(
                    "canonical fixture directory is absent: "
                    + traceDirectory);
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
                    "Supplied S3K_ROM_PATH does not exist: " + romPath + ".");
            }
            RomIdentity.ValidateSonic3kLockOn(File.ReadAllBytes(romPath));
            BizHawkInstallation.Validate(bizHawkHome);
            return new S3KDifferentialDependencies(romPath, bizHawkHome);
        }

        private static string RunTraceCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output,
            S3KDifferentialCase differentialCase)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments =
                    EndToEndTests.Quote(
                        Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                    + " --mode trace"
                    + " --rom " + EndToEndTests.Quote(romPath)
                    + " --movie " + EndToEndTests.Quote(moviePath)
                    + " --output " + EndToEndTests.Quote(output)
                    + " --trace-profile " + differentialCase.TraceProfile,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.EnvironmentVariables["BIZHAWK_HOME"] = bizHawkHome;
            start.EnvironmentVariables["DISPLAY"] = ":99";
            // The native port refuses the Lua's diagnostic-hook arming
            // variables (Program.RejectS3kDiagnosticHookEnvironment); every
            // gated fixture was captured with them unset, so the gate must
            // run with them unset too rather than inheriting a stray value.
            start.EnvironmentVariables[
                "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS"] = string.Empty;
            start.EnvironmentVariables["OGGF_S3K_CNZ_EVENT_RAM_RANGE"] =
                string.Empty;
            start.EnvironmentVariables["OGGF_S3K_RNG_CALL_RANGE"] =
                string.Empty;
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
            S3KDifferentialCase differentialCase)
        {
            return
                "BizHawk: " + installation.ManagedVersion + "\n"
                + "ROM SHA-1: " + RomIdentity.Sonic3kLockOnSha1 + "\n"
                + "Movie frames: "
                + differentialCase.MovieFrameCount + "\n"
                + "Trace profile: "
                + differentialCase.TraceProfile + "\n"
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
        /// fixture's except for the two deltas pinned by
        /// docs/s3k-trace-recorder-behavior.md §6.2 for this fixture: the
        /// recording_date value (which must still carry the exact key
        /// formatting and an ISO date value) and the lua_script_version
        /// line, whose fixture and produced forms are both asserted as
        /// exact literals. Each delta must appear exactly once; every other
        /// line must match exactly, and the line counts must agree (so a
        /// stray inserted or dropped line — e.g. MGZ's leftover
        /// pre_trace_osc_frames — cannot slip through).
        /// </summary>
        private static void AssertPinnedDeltaMetadataEquality(
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

        private sealed class S3KDifferentialDependencies
        {
            public S3KDifferentialDependencies(
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
