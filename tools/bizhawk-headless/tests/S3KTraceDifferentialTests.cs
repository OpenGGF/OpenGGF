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
    /// byte-for-byte against the canonical fixtures under
    /// src/test/resources/traces/s3k/. Each case runs the trace-mode CLI
    /// end-to-end through run.sh (game auto-detected from the S3K
    /// locked-on ROM) with the fixture's own --trace-profile and asserts:
    ///
    /// - the exact stdout contract, including the canonical BK2 frame
    ///   offset and trace frame count;
    /// - physics.csv and aux_state.jsonl sha256 hashes with ZERO
    ///   normalization — the fixtures ship gzipped only, so the canonical
    ///   bytes are decompressed read-only into the temp root and hashed
    ///   there before the produced files are hashed. The fixtures under
    ///   src/test/resources/traces/ are never written to.
    /// - metadata.json line-for-line equality apart from the
    ///   nondeterministic recording_date, only when both sides use the
    ///   current 6.38-s3k / trace-schema-7 / hardware-schema-2 contract;
    /// - hardware_timing.jsonl has the exact schema-2 event shape and
    ///   contains independently increasing kos_decompression_queue and
    ///   kos_module_queue ledgers at their permitted boundaries.
    ///
    /// The committed 6.37-s3k / trace-schema-7 / hardware-schema-1
    /// fixtures remain readable historical inputs, but they are explicit
    /// load-only compatibility. This differential gate must not normalize
    /// their module-only timing into schema-2 direct-authority success.
    ///
    /// The cases deliberately cover both terminating profiles:
    ///
    /// - aiz1_to_hcz_fullrun / aiz_end_to_end records its arm frame as row
    ///   0 and ends on the BK2-end guard (511 + 20798 == the movie's 21309
    ///   input rows exactly);
    /// - cnz / level_gated_reset_aware drops its arm frame, survives the
    ///   pause+A soft reset out of AIZ through the discard-and-re-arm path
    ///   (offset 3171 belongs to the LAST armed segment), and finalizes on
    ///   the zone-leave check rather than either movie-end stop (3171 +
    ///   42253 == 45424, short of the movie's 45597 input rows) — so it
    ///   also pins that the zone-leave row is never recorded and that the
    ///   finalization aux checkpoint lands at frame == the row count;
    /// - mgz / level_gated_reset_aware is the second level-gated fixture
    ///   and pins the profile's zone-independence: it likewise finalizes
    ///   on the MGZ->CNZ zone-leave check (2602 + 35912 == 38514, short of
    ///   the movie's 38818 input rows), it advertises the cnz_cylinder_*
    ///   aux families in aux_schema_extras despite never starting in CNZ
    ///   (the advertisement is profile-based, not zone-based), and its aux
    ///   stream legitimately carries NO gameplay_start and no
    ///   act-transition checkpoint at all — only gameplay_end — because
    ///   the level-gated profile's gameplay_start emission is zone-3
    ///   literal. Byte-equal aux_state.jsonl is what pins that quirk as
    ///   reproduced rather than generalized away.
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

        /// <summary>
        /// Exact current and committed compatibility literals. Any other
        /// value, or any mixed combination, fails.
        /// </summary>
        private const string CommittedLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.37-s3k\",";
        private const string CurrentLuaScriptVersionLine =
            "  \"lua_script_version\": \"6.38-s3k\",";
        private const string CurrentTraceSchemaLine =
            "  \"trace_schema\": 7,";
        private const string CommittedHardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 1,";
        private const string HardwareTimingSchemaLine =
            "  \"hardware_timing_schema\": 2,";
        private static readonly Regex HardwareTimingEventLine = new Regex(
            "^\\{\"event\":\"hardware_work_completed\","
            + "\"raw_frame\":([0-9]+),"
            + "\"boundary\":\"([a-z_]+)\","
            + "\"kind\":\"(kos_decompression_queue|kos_module_queue)\","
            + "\"ordinal\":([0-9]+),"
            + "\"submission_fingerprint\":\"sha256:"
            + "([0-9a-f]{64})\"\\}$");

        private static readonly S3KDifferentialCase AizEndToEndCase =
            new S3KDifferentialCase(
                "aiz1_to_hcz_fullrun",
                "s3-aiz1-2-sonictails.bk2",
                "aiz_end_to_end",
                511,
                20798,
                21309,
                "3c219725d85d64762b514f973263edced337a37cd16fb8bf50f2b0ac"
                + "3b5a2a39",
                "9d90d669de5b9fc0c00666ad2023a164d1d110d441b9bcc8403280d1"
                + "a5d74b47");

        private static readonly S3KDifferentialCase CnzLevelGatedCase =
            new S3KDifferentialCase(
                "cnz",
                "s3k-cnz-sonic-tails.bk2",
                "level_gated_reset_aware",
                3171,
                42253,
                45597,
                "195de5a64bd879f6d920ffe9a487931beb4f6366516587d23268b105"
                + "9a7b46e2",
                "17ddb988b74e8718d6e3d73a7aaefff56d077e6e5d015c7ab875a4674"
                + "a94052e");

        private static readonly S3KDifferentialCase MgzLevelGatedCase =
            new S3KDifferentialCase(
                "mgz",
                "s3k-mgz-sonic-tails.bk2",
                "level_gated_reset_aware",
                2602,
                35912,
                38818,
                "16bff6712e4228494b8aeac587006edeee9f6befc62aa7b9078a465d"
                + "b4e2d611",
                "4ce8ee02e8e6dc1664659a494578427da0c6111e5a4c0fb88b71026b2"
                + "b2c2035");

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KTraceDifferential requires schema two and rejects"
                + " schema one as load-only compatibility",
                MetadataCompatibilityShapesAreExact));
            tests.Add(new TestMain.TestCase(
                "S3KTraceDifferential requires direct and module timing"
                + " ledgers",
                CurrentHardwareTimingRequiresBothLedgers));
            tests.Add(new TestMain.TestCase(
                "S3KTraceDifferential orders same-frame VINT before PRE"
                + " before POST",
                CurrentHardwareTimingUsesCanonicalSameFrameOrder));
            tests.Add(new TestMain.TestCase(
                "S3KTraceDifferential native capture matches canonical AIZ"
                + " timing stream",
                () => NativeCaptureMatchesCanonicalTrace(AizEndToEndCase),
                game: "s3k",
                movie: "s3-aiz1-2-sonictails",
                kind: TestKind.Gate,
                estimatedSeconds: 19.0));
            tests.Add(new TestMain.TestCase(
                "S3KTraceDifferential native capture matches canonical CNZ"
                + " level-gated trace",
                () => NativeCaptureMatchesCanonicalTrace(CnzLevelGatedCase),
                game: "s3k",
                movie: "s3k-cnz-sonic-tails",
                kind: TestKind.Gate,
                estimatedSeconds: 37.0));
            tests.Add(new TestMain.TestCase(
                "S3KTraceDifferential native capture matches canonical MGZ"
                + " level-gated trace",
                () => NativeCaptureMatchesCanonicalTrace(MgzLevelGatedCase),
                game: "s3k",
                movie: "s3k-mgz-sonic-tails",
                kind: TestKind.Gate,
                estimatedSeconds: 28.0));
        }

        /// <summary>
        /// One canonical fixture comparison: the fixture directory name
        /// under src/test/resources/traces/s3k/, the movie file name inside
        /// it, the --trace-profile argument (also the value the CLI must
        /// echo), the canonical BK2 frame offset / trace frame count /
        /// movie frame count, and the canonical sha256 hashes of the Lua
        /// recorder's physics.csv and aux_state.jsonl bytes. There is no
        /// per-fixture metadata allowance field any more: every fixture is
        /// held to the same recording_date-only delta.
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

            string root = TestScratch.CreateRootPath(
                "openggf-s3k-trace-differential");
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
                string hardwareTimingPath =
                    Path.Combine(output, "hardware_timing.jsonl");
                AssertEx.Equal(true, File.Exists(hardwareTimingPath));
                AssertEx.Equal(
                    false,
                    File.Exists(hardwareTimingPath
                        + TracePayloadCompressor.GzipExtension));
                AssertCurrentHardwareTimingLedgers(hardwareTimingPath);
                AssertRecordingDateOnlyMetadataEquality(
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
                    + EndToEndTests.NoCompressArgument
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
            // The native port refuses every Lua environment variable that
            // changes recorder output and that it does not model
            // (Program.RejectUnmodeledS3kEnvironment). Every gated fixture
            // was captured with all of them unset, so the gate must run
            // with all of them unset too rather than inheriting a stray
            // value from the developer's shell — which would otherwise
            // turn a byte-parity gate into a refusal, or (before the
            // refusal existed) into a silently non-canonical capture.
            foreach (string unmodeled in new[]
            {
                "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS",
                "OGGF_S3K_CNZ_EVENT_RAM_RANGE",
                "OGGF_S3K_RNG_CALL_RANGE",
                "OGGF_S3K_AIZ_FIRE_RANGE",
                "OGGF_S3K_AIZ_WALL_SENSOR_RANGE",
                "OGGF_S3K_CRL_RANGE",
                "OGGF_S3K_CNZ_CYLINDER_RANGE",
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START",
                "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END",
                "OGGF_TRACE_STOP_FRAME",
                "OGGF_BK2_FRAME_COUNT"
            })
            {
                start.EnvironmentVariables[unmodeled] = string.Empty;
            }
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
                + "Hardware timing JSONL: "
                + Path.Combine(output, "hardware_timing.jsonl") + "\n"
                + "Metadata JSON: "
                + Path.Combine(output, "metadata.json") + "\n";
        }

        /// <summary>
        /// Asserts the produced metadata.json is byte-identical to the
        /// fixture's except for the ONE delta
        /// docs/s3k-trace-recorder-behavior.md §6.1 still permits: the
        /// recording_date value, which is nondeterministic and is only
        /// required to keep the exact key formatting and an ISO date value.
        /// It must occur exactly once.
        ///
        /// Everything else is exact-line equality, including the line
        /// count. Only current schema-2 metadata can establish differential
        /// success; committed schema-1 metadata is load-only compatibility.
        /// </summary>
        private static void AssertRecordingDateOnlyMetadataEquality(
            string fixturePath,
            string producedPath)
        {
            string fixtureText = File.ReadAllText(fixturePath);
            string producedText = File.ReadAllText(producedPath);
            AssertEx.Equal(false, fixtureText.IndexOf('\r') >= 0);
            AssertEx.Equal(false, producedText.IndexOf('\r') >= 0);
            AssertEx.Equal(true, fixtureText.EndsWith("\n"));
            AssertEx.Equal(true, producedText.EndsWith("\n"));

            MetadataNormalization normalization =
                NormalizeCurrentMetadataForFixture(
                    fixtureText, producedText);
            producedText = normalization.Text;
            string fixtureVersionLine = normalization.VersionLine;

            string[] fixtureLines = fixtureText.Split('\n');
            string[] producedLines = producedText.Split('\n');
            AssertEx.Equal(fixtureLines.Length, producedLines.Length);

            var recordingDateLines = 0;
            var versionLines = 0;
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                string fixtureLine = fixtureLines[index];
                string producedLine = producedLines[index];
                if (fixtureLine.StartsWith(
                    RecordingDateLinePrefix,
                    StringComparison.Ordinal))
                {
                    recordingDateLines++;
                    if (!RecordingDateLine.IsMatch(producedLine))
                    {
                        throw new InvalidOperationException(
                            "Produced recording_date line is malformed: <"
                            + producedLine + ">.");
                    }
                    continue;
                }

                AssertEx.Equal(fixtureLine, producedLine);
                if (fixtureLine == fixtureVersionLine)
                {
                    versionLines++;
                }
            }
            AssertEx.Equal(1, recordingDateLines);
            AssertEx.Equal(1, versionLines);
        }

        private static MetadataNormalization NormalizeCurrentMetadataForFixture(
            string fixtureText,
            string producedText)
        {
            RequireMetadataShape(
                producedText,
                CurrentLuaScriptVersionLine,
                CurrentTraceSchemaLine,
                HardwareTimingSchemaLine,
                "produced");

            if (HasMetadataShape(
                fixtureText,
                CurrentLuaScriptVersionLine,
                CurrentTraceSchemaLine,
                HardwareTimingSchemaLine))
            {
                return new MetadataNormalization(
                    producedText, CurrentLuaScriptVersionLine);
            }
            if (HasMetadataShape(
                fixtureText,
                CommittedLuaScriptVersionLine,
                CurrentTraceSchemaLine,
                CommittedHardwareTimingSchemaLine))
            {
                throw new InvalidOperationException(
                    "Fixture metadata is committed schema-one load-only"
                    + " compatibility and cannot establish schema-two"
                    + " direct-authority differential success.");
            }

            throw new InvalidOperationException(
                "Fixture metadata has an unknown or mixed S3K trace"
                + " version/schema shape.");
        }

        private static bool HasMetadataShape(
            string text,
            string versionLine,
            string traceSchemaLine,
            string hardwareTimingSchemaLine)
        {
            return CountOccurrences(text, "\"lua_script_version\":") == 1
                && CountOccurrences(text, versionLine) == 1
                && CountOccurrences(text, "\"trace_schema\":") == 1
                && CountOccurrences(text, traceSchemaLine) == 1
                && CountOccurrences(text, "\"hardware_timing_schema\":")
                    == 1
                && CountOccurrences(text, hardwareTimingSchemaLine) == 1;
        }

        private static void RequireMetadataShape(
            string text,
            string versionLine,
            string traceSchemaLine,
            string hardwareTimingSchemaLine,
            string owner)
        {
            if (!HasMetadataShape(
                text,
                versionLine,
                traceSchemaLine,
                hardwareTimingSchemaLine))
            {
                throw new InvalidOperationException(
                    owner + " metadata does not have the exact current"
                    + " S3K trace version/schema shape.");
            }
        }

        private static void MetadataCompatibilityShapesAreExact()
        {
            const string currentVersion =
                "  \"lua_script_version\": \"6.38-s3k\",";
            const string committedVersion =
                "  \"lua_script_version\": \"6.37-s3k\",";
            const string currentHardwareSchema =
                "  \"hardware_timing_schema\": 2,";
            const string committedHardwareSchema =
                "  \"hardware_timing_schema\": 1,";
            string current = currentVersion + "\n"
                + CurrentTraceSchemaLine + "\n"
                + currentHardwareSchema + "\n";
            string committed = committedVersion + "\n"
                + CurrentTraceSchemaLine + "\n"
                + committedHardwareSchema + "\n";

            AssertEx.Equal(
                current,
                NormalizeCurrentMetadataForFixture(
                    current, current).Text);
            AssertEx.Throws<InvalidOperationException>(
                () => NormalizeCurrentMetadataForFixture(
                    committed, current),
                "load-only compatibility");
            AssertEx.Throws<InvalidOperationException>(
                () => NormalizeCurrentMetadataForFixture(
                    "  \"lua_script_version\": \"9.99-s3k\",\n"
                        + CurrentTraceSchemaLine + "\n"
                        + HardwareTimingSchemaLine + "\n",
                    current),
                "unknown or mixed");
            AssertEx.Throws<InvalidOperationException>(
                () => NormalizeCurrentMetadataForFixture(
                    current.Replace(
                        CurrentLuaScriptVersionLine,
                        CurrentLuaScriptVersionLine + "\n"
                            + CommittedLuaScriptVersionLine),
                    current),
                "unknown or mixed");
        }

        private static void AssertCurrentHardwareTimingLedgers(string path)
        {
            string text = File.ReadAllText(path);
            if (text.IndexOf('\r') >= 0 || !text.EndsWith("\n"))
            {
                throw new InvalidOperationException(
                    "Current hardware timing must be LF-terminated JSONL.");
            }

            string[] lines = text.Split('\n');
            bool directSeen = false;
            bool moduleSeen = false;
            long lastDirectOrdinal = -1;
            long lastModuleOrdinal = -1;
            long lastRawFrame = -1;
            int lastBoundaryRank = -1;
            for (var index = 0; index < lines.Length - 1; index++)
            {
                Match match = HardwareTimingEventLine.Match(lines[index]);
                if (!match.Success)
                {
                    throw new InvalidOperationException(
                        "Current hardware timing line " + (index + 1)
                        + " does not have the exact schema-two event"
                        + " shape.");
                }

                long rawFrame = Convert.ToInt64(match.Groups[1].Value);
                string boundary = match.Groups[2].Value;
                string kind = match.Groups[3].Value;
                long ordinal = Convert.ToInt64(match.Groups[4].Value);
                int boundaryRank;
                long previousOrdinal;
                if (kind == "kos_decompression_queue")
                {
                    if (boundary != "pre_main_loop")
                    {
                        throw new InvalidOperationException(
                            "kos_decompression_queue must retire at"
                            + " pre_main_loop.");
                    }
                    directSeen = true;
                    boundaryRank = 1;
                    previousOrdinal = lastDirectOrdinal;
                    lastDirectOrdinal = ordinal;
                }
                else
                {
                    if (boundary != "vint_service"
                        && boundary != "post_objects")
                    {
                        throw new InvalidOperationException(
                            "kos_module_queue must retire at vint_service"
                            + " or post_objects.");
                    }
                    moduleSeen = true;
                    boundaryRank =
                        boundary == "vint_service" ? 0 : 2;
                    previousOrdinal = lastModuleOrdinal;
                    lastModuleOrdinal = ordinal;
                }

                if (previousOrdinal >= ordinal)
                {
                    throw new InvalidOperationException(
                        kind + " ordinals must increase independently.");
                }
                if (rawFrame < lastRawFrame
                    || (rawFrame == lastRawFrame
                        && boundaryRank < lastBoundaryRank))
                {
                    throw new InvalidOperationException(
                        "Current hardware timing events are not in raw-frame"
                        + " and boundary order.");
                }
                lastBoundaryRank = boundaryRank;
                lastRawFrame = rawFrame;
            }

            if (!directSeen)
            {
                throw new InvalidOperationException(
                    "Current hardware timing has no"
                    + " kos_decompression_queue ledger.");
            }
            if (!moduleSeen)
            {
                throw new InvalidOperationException(
                    "Current hardware timing has no kos_module_queue"
                    + " ledger.");
            }
        }

        private static void CurrentHardwareTimingRequiresBothLedgers()
        {
            string root = TestScratch.CreateRootPath(
                "openggf-s3k-trace-ledger-contract");
            string path = Path.Combine(root, "hardware_timing.jsonl");
            const string direct =
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":13,"
                + "\"boundary\":\"pre_main_loop\","
                + "\"kind\":\"kos_decompression_queue\",\"ordinal\":0,"
                + "\"submission_fingerprint\":\"sha256:"
                + "0123456789abcdef0123456789abcdef"
                + "0123456789abcdef0123456789abcdef\"}\n";
            const string module =
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":13,"
                + "\"boundary\":\"post_objects\","
                + "\"kind\":\"kos_module_queue\",\"ordinal\":0,"
                + "\"submission_fingerprint\":\"sha256:"
                + "abcdef0123456789abcdef0123456789"
                + "abcdef0123456789abcdef0123456789\"}\n";
            try
            {
                Directory.CreateDirectory(root);
                File.WriteAllText(path, direct + module);
                AssertCurrentHardwareTimingLedgers(path);

                File.WriteAllText(path, module);
                AssertEx.Throws<InvalidOperationException>(
                    () => AssertCurrentHardwareTimingLedgers(path),
                    "kos_decompression_queue");

                File.WriteAllText(path, direct);
                AssertEx.Throws<InvalidOperationException>(
                    () => AssertCurrentHardwareTimingLedgers(path),
                    "kos_module_queue");
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private static void CurrentHardwareTimingUsesCanonicalSameFrameOrder()
        {
            string root = TestScratch.CreateRootPath(
                "openggf-s3k-trace-ledger-order");
            string path = Path.Combine(root, "hardware_timing.jsonl");
            const string vint =
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":13,"
                + "\"boundary\":\"vint_service\","
                + "\"kind\":\"kos_module_queue\",\"ordinal\":0,"
                + "\"submission_fingerprint\":\"sha256:"
                + "abcdef0123456789abcdef0123456789"
                + "abcdef0123456789abcdef0123456789\"}\n";
            const string pre =
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":13,"
                + "\"boundary\":\"pre_main_loop\","
                + "\"kind\":\"kos_decompression_queue\",\"ordinal\":0,"
                + "\"submission_fingerprint\":\"sha256:"
                + "0123456789abcdef0123456789abcdef"
                + "0123456789abcdef0123456789abcdef\"}\n";
            const string post =
                "{\"event\":\"hardware_work_completed\",\"raw_frame\":13,"
                + "\"boundary\":\"post_objects\","
                + "\"kind\":\"kos_module_queue\",\"ordinal\":0,"
                + "\"submission_fingerprint\":\"sha256:"
                + "fedcba9876543210fedcba9876543210"
                + "fedcba9876543210fedcba9876543210\"}\n";
            try
            {
                Directory.CreateDirectory(root);

                File.WriteAllText(path, vint + pre);
                AssertCurrentHardwareTimingLedgers(path);

                File.WriteAllText(path, pre + vint);
                AssertEx.Throws<InvalidOperationException>(
                    () => AssertCurrentHardwareTimingLedgers(path),
                    "raw-frame and boundary order");

                File.WriteAllText(path, pre + post);
                AssertCurrentHardwareTimingLedgers(path);
            }
            finally
            {
                if (Directory.Exists(root))
                {
                    Directory.Delete(root, true);
                }
            }
        }

        private sealed class MetadataNormalization
        {
            internal MetadataNormalization(string text, string versionLine)
            {
                Text = text;
                VersionLine = versionLine;
            }

            internal string Text { get; private set; }
            internal string VersionLine { get; private set; }
        }

        private static int CountOccurrences(string value, string needle)
        {
            int count = 0;
            int start = 0;
            while ((start = value.IndexOf(
                needle, start, StringComparison.Ordinal)) >= 0)
            {
                count++;
                start += needle.Length;
            }
            return count;
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
