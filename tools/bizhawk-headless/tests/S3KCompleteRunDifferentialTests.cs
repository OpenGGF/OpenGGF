using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// ROM-backed differential gate for the native S3K COMPLETE-RUN
    /// recorder (tools/bizhawk/s3k_complete_run_recorder.lua
    /// v6.33-s3k-completerun; spec
    /// tools/bizhawk-headless/docs/s3k-run-publication.md). It runs the
    /// real CLI end-to-end through run.sh over the canonical Knuckles
    /// multi-bonus movie and asserts that the published bonus segment is
    /// byte-identical to the committed identity-(C) fixture.
    ///
    /// Scope, deliberately: ONE fast case. The pass is truncated with
    /// --effective-movie-length 7001 — the modeled equivalent of the
    /// REFUSED OGGF_BK2_FRAME_COUNT — which is the exact BK2 frame at
    /// which the real capture armed its next level segment, so the
    /// gumball segment it publishes has the fixture's own
    /// bk2_frame_offset 5570 and trace_frame_count 1430 and must therefore
    /// match it byte for byte, not approximately. That makes this a true
    /// byte gate over the whole pipeline — segmentation, the 42-column
    /// row with ADDR_FRAMECOUNT 0xFE04, the complete-run aux cascade
    /// including game_paused_state, the bonus metadata shape with its
    /// arm-sampled v_int_run_count, the manifest, and LF publication — in
    /// about five seconds.
    ///
    /// What it does NOT cover: the seven identity-(A) *_completerun dirs
    /// are gated by <see cref="S3KCompleteRunSegmentsDifferentialTests"/>,
    /// which turned out to cost about six minutes rather than the hours
    /// estimated here and so runs in the default suite too. The remaining
    /// three identity-(C) dirs (bonus_slots, bonus_pachinko,
    /// special_stage) would each need their own long pass over THIS movie
    /// and remain uncovered.
    ///
    /// Only two things may differ from the fixture, both pinned as exact
    /// literals: the recording_date VALUE (nondeterministic), and the
    /// presence of run_id — which is not a delta at all here, because the
    /// fixture itself was captured under --run-id s3k-multibonus and this
    /// gate passes the same id. physics.csv and aux_state.jsonl are
    /// compared by sha256 with ZERO normalization.
    ///
    /// Skips (does not pass) when S3K_ROM_PATH, a BizHawk distribution or
    /// the fixtures are absent; fails (does not skip) on any mismatch.
    /// </summary>
    internal static class S3KCompleteRunDifferentialTests
    {
        private const int CaptureTimeoutMilliseconds = 600000;
        private const string RunId = "s3k-multibonus";
        private const string MovieFileName = "s3-knux-multibonus-ss.bk2";
        private const int MovieFrameCount = 114622;
        private const int EffectiveMovieLength = 7001;

        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");

        /// <summary>
        /// The stamp the fixture and this port both carry, pinned as an
        /// exact literal so a shared drift fails rather than cancels out.
        /// </summary>
        private const string LuaScriptVersionLine =
            "  \"lua_script_version\": \"6.33-s3k-completerun\",";

        // docs/s3k-run-publication.md §0.3, identity (C). The physics hash
        // was last moved by Lua 6.33-s3k-completerun (ADDR_VBLA_WORD 0xFE12
        // Life_count -> 0xFE0E V_int_run_count low word); the aux hash was
        // NOT, because no aux field reads that address.
        private const string GumballPhysicsSha256 =
            "8d6e3e3004e811a124c516ac224fe9e9dd5476cce1d6c3097b3b7c65c2"
            + "526dd6";
        private const string GumballAuxSha256 =
            "842fbad87a91effb9749bcd7b95f61d558d1cb9929e35cf5ca9ac32874"
            + "3460e7";

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KCompleteRunDifferential native capture matches the"
                + " canonical bonus_gumball segment",
                NativeCaptureMatchesCanonicalBonusSegment));
        }

        private static void NativeCaptureMatchesCanonicalBonusSegment()
        {
            string s3kRoot = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k");
            string fixtureDirectory =
                Path.Combine(s3kRoot, "bonus_gumball");
            string moviePath =
                Path.Combine(s3kRoot, "_movies", MovieFileName);
            Dependencies dependencies = Resolve(fixtureDirectory, moviePath);
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            string root = Path.Combine(
                Path.GetTempPath(),
                "openggf-s3k-completerun-differential-"
                + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                // The canonical bytes ship gzipped only; decompress them
                // read-only into the temp root so the pinned hashes are
                // asserted against exactly what the Lua recorder wrote.
                // Nothing under src/test/resources/traces/ is written.
                Directory.CreateDirectory(root);
                AssertEx.Equal(
                    GumballPhysicsSha256,
                    EndToEndTests.ComputeSha256(Gunzip(
                        Path.Combine(
                            fixtureDirectory, "physics.csv.gz"),
                        Path.Combine(root, "fixture-physics.csv"))));
                AssertEx.Equal(
                    GumballAuxSha256,
                    EndToEndTests.ComputeSha256(Gunzip(
                        Path.Combine(
                            fixtureDirectory, "aux_state.jsonl.gz"),
                        Path.Combine(root, "fixture-aux_state.jsonl"))));

                string stdout = RunCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output);

                string fullOutput = Path.GetFullPath(output);
                AssertEx.Equal(
                    "BizHawk: " + installation.ManagedVersion + "\n"
                    + "ROM SHA-1: " + RomIdentity.Sonic3kLockOnSha1 + "\n"
                    + "Movie frames: " + MovieFrameCount + "\n"
                    + "Effective movie length: " + EffectiveMovieLength
                    + "\n"
                    + "Run ID: " + RunId + "\n"
                    + "Segments: 2\n"
                    + "Transitions: 1\n"
                    + "Segment aiz: kind=level, BK2 frame offset=915,"
                    + " trace frames=4654\n"
                    + "Segment gumball: kind=bonus_stage, BK2 frame"
                    + " offset=5570, trace frames=1430\n"
                    + "Run manifest: "
                    + Path.Combine(fullOutput, "run_manifest.json") + "\n",
                    stdout);

                string gumball = Path.Combine(fullOutput, "gumball");
                AssertEx.Equal(
                    GumballPhysicsSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(gumball, "physics.csv")));
                AssertEx.Equal(
                    GumballAuxSha256,
                    EndToEndTests.ComputeSha256(
                        Path.Combine(gumball, "aux_state.jsonl")));
                AssertMetadataEqualExceptRecordingDate(
                    Path.Combine(fixtureDirectory, "metadata.json"),
                    Path.Combine(gumball, "metadata.json"));

                // The manifest's two segment records and its single
                // starpost_bonus transition are the committed (B)
                // manifest's own first records, including every RAM-sampled
                // entry field.
                string manifest = File.ReadAllText(
                    Path.Combine(fullOutput, "run_manifest.json"));
                AssertContains(
                    manifest,
                    "    {\"dir\": \"aiz\", \"kind\": \"level\","
                    + " \"trace_profile\": \"complete_run\","
                    + " \"bk2_frame_offset\": 915,"
                    + " \"trace_frame_count\": 4654, \"zone_id\": 0,"
                    + " \"act\": 1},\n");
                AssertContains(
                    manifest,
                    "    {\"dir\": \"gumball\", \"kind\": \"bonus_stage\","
                    + " \"trace_profile\": \"s3k_bonus_stage\","
                    + " \"bk2_frame_offset\": 5570,"
                    + " \"trace_frame_count\": 1430, \"zone_id\": 19,"
                    + " \"act\": 1,"
                    + " \"bonus_stage_type\": \"gumball\"}\n");
                AssertContains(
                    manifest,
                    "    {\"from_segment\": 0, \"to_segment\": 1,"
                    + " \"entry_kind\": \"starpost_bonus\","
                    + " \"mode_change_bk2_frame\": 5570,"
                    + " \"special_bonus_entry_flag\": 2,"
                    + " \"saved_x_pos\": 10104, \"saved_y_pos\": 604,"
                    + " \"last_star_post_hit\": 0, \"rings_before\": 59,"
                    + " \"emeralds_before\": 0}\n");
                if (manifest.IndexOf('\r') >= 0)
                {
                    throw new InvalidOperationException(
                        "run_manifest.json contains CR; the S3K"
                        + " complete-run recorder publishes LF in both"
                        + " modes.");
                }
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
        /// Line-for-line equality with exactly one permitted difference:
        /// the recording_date VALUE. Both sides must carry a
        /// well-formed recording_date line at the same index; every other
        /// line must be identical, and the line counts must match — no
        /// key-dropping normalization.
        ///
        /// lua_script_version is additionally pinned as an exact literal on
        /// BOTH sides, exactly as the three sibling S3K gates do. It is not
        /// an allowance — raw line equality already forces the two sides to
        /// agree — but it forces them to agree on the CURRENT stamp rather
        /// than on any shared value, so a fixture and a port that drifted
        /// together fail here instead of passing quietly.
        /// </summary>
        private static void AssertMetadataEqualExceptRecordingDate(
            string fixturePath, string producedPath)
        {
            string[] expected = ReadLines(fixturePath);
            string[] actual = ReadLines(producedPath);
            if (expected.Length != actual.Length)
            {
                throw new InvalidOperationException(
                    "metadata.json line count is " + actual.Length
                    + "; expected " + expected.Length + ".");
            }
            var versionLines = 0;
            for (var index = 0; index < expected.Length; index++)
            {
                if (expected[index] == LuaScriptVersionLine)
                {
                    versionLines++;
                }
                if (expected[index] == actual[index])
                {
                    continue;
                }
                if (RecordingDateLine.IsMatch(expected[index])
                    && RecordingDateLine.IsMatch(actual[index]))
                {
                    continue;
                }
                throw new InvalidOperationException(
                    "metadata.json line " + (index + 1) + " is <"
                    + actual[index] + ">; expected <" + expected[index]
                    + ">.");
            }
            AssertEx.Equal(1, versionLines);
        }

        private static string[] ReadLines(string path)
        {
            return File.ReadAllText(path)
                .Split(new[] { '\n' }, StringSplitOptions.None);
        }

        private static string RunCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments =
                    EndToEndTests.Quote(
                        Path.Combine(EndToEndTests.ToolDirectory, "run.sh"))
                    + " --mode trace"
                    + EndToEndTests.NoCompressArgument
                    + " --rom " + EndToEndTests.Quote(romPath)
                    + " --movie " + EndToEndTests.Quote(moviePath)
                    + " --output " + EndToEndTests.Quote(output)
                    + " --run-id " + RunId
                    + " --effective-movie-length " + EffectiveMovieLength,
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.EnvironmentVariables["BIZHAWK_HOME"] = bizHawkHome;
            start.EnvironmentVariables["DISPLAY"] = ":99";
            // The port refuses every complete-run recorder environment
            // variable that changes output and that it does not model
            // (Program.RejectUnmodeledS3kCompleteRunEnvironment). The
            // fixture was captured with all of them unset, so the gate must
            // run with all of them unset too rather than inheriting a stray
            // value from the developer's shell — which would turn a byte
            // gate into a refusal.
            foreach (string unmodeled in new[]
            {
                "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS",
                "OGGF_S3K_CNZ_EVENT_RAM_RANGE",
                "OGGF_S3K_AIZ_WALL_SENSOR_RANGE",
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START",
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END",
                "OGGF_S3K_CRL_RANGE",
                "OGGF_S3K_CNZ_CYLINDER_RANGE",
                "OGGF_TRACE_STOP_FRAME",
                "OGGF_BK2_FRAME_COUNT"
            })
            {
                start.EnvironmentVariables[unmodeled] = null;
            }

            // Drain both pipes CONCURRENTLY via the shared helper every
            // other differential gate uses. Reading stdout to EOF first and
            // stderr only afterwards deadlocks whenever the child fills the
            // ~64 KB stderr pipe buffer while stdout is still open — the
            // child blocks in write(2), stdout never reaches EOF, and the
            // blocking ReadToEnd() is never left, so the timeout and the
            // Kill() below it become dead code. That is a live risk here:
            // the capture runs BizHawk's native GPGX core under run.sh,
            // whose stderr NativeStandardOutputSilencer does not cover, and
            // a mono/native fault can emit a large trace.
            EndToEndTests.ProcessResult result =
                EndToEndTests.RunProcess(start, CaptureTimeoutMilliseconds);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException(
                    "S3K complete-run capture failed with exit code "
                    + result.ExitCode + ". stderr: "
                    + result.StandardError);
            }
            return result.StandardOutput;
        }

        private static Dependencies Resolve(
            string fixtureDirectory, string moviePath)
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

            if (!Directory.Exists(fixtureDirectory))
            {
                missing.Add(
                    "canonical fixture directory is absent: "
                    + fixtureDirectory);
            }
            if (!File.Exists(moviePath))
            {
                missing.Add("canonical movie is absent: " + moviePath);
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
            return new Dependencies(romPath, bizHawkHome);
        }

        private static string Gunzip(
            string sourcePath, string destinationPath)
        {
            using (FileStream source = File.OpenRead(sourcePath))
            using (var gzip = new GZipStream(
                source, CompressionMode.Decompress))
            using (FileStream destination = File.Create(destinationPath))
            {
                gzip.CopyTo(destination);
            }
            return destinationPath;
        }

        private static void AssertContains(
            string value, string expectedFragment)
        {
            if (value.IndexOf(expectedFragment, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Expected run_manifest.json to contain <"
                    + expectedFragment + ">.");
            }
        }

        private sealed class Dependencies
        {
            public Dependencies(string romPath, string bizHawkHome)
            {
                RomPath = romPath;
                BizHawkHome = bizHawkHome;
            }

            public string RomPath { get; private set; }
            public string BizHawkHome { get; private set; }
        }
    }
}
