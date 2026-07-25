using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Differential gate for the native S3K complete-run recorder in RUN
    /// MODE (<c>--run-id</c>), over the canonical Knuckles multi-bonus
    /// movie
    /// (src/test/resources/traces/s3k/_movies/s3-knux-multibonus-ss.bk2,
    /// 114,622 input rows). Two untruncated passes, one per capture
    /// identity, both driven through the real CLI:
    ///
    /// 1. <c>--run-id s3k-multibonus</c> reproduces all four committed
    ///    identity-(C) directories — <c>bonus_gumball</c>,
    ///    <c>bonus_slots</c>, <c>bonus_pachinko</c>, <c>special_stage</c> —
    ///    with physics.csv and aux_state.jsonl byte-identical (length AND
    ///    sha256, ZERO normalization) and metadata.json equal line for line
    ///    apart from the recording_date VALUE. Three of those four were
    ///    previously verified only by hand
    ///    (docs/s3k-run-publication.md §10.5); this closes them.
    ///
    /// 2. <c>--run-id s3-knux-multibonus-ss</c> reproduces the STRUCTURE of
    ///    the committed identity-(B) run tree —
    ///    src/test/resources/traces/s3k/runs/s3-knux-multibonus-ss/ — all
    ///    25 segment directories and run_manifest.json, with every
    ///    permitted delta pinned as an exact literal (§3 below).
    ///
    /// ## Why (B) is gated structurally and not byte-exactly
    ///
    /// (B) is a 2026-07-19 Windows EmuHawk capture by Lua
    /// 6.31-s3k-completerun, three recorder builds behind HEAD. It is NOT
    /// byte-reproducible by the current recorder, for three independent
    /// reasons, each of which was re-verified against the fixture bytes
    /// before this gate was written rather than taken from the spec:
    ///
    /// - **CRLF.** The Lua writes only "\n"; io.open(…, "w") is text mode,
    ///   so a Windows host expands every newline. All 25 (B) segments and
    ///   the (B) manifest are CRLF. Identity (A) and identity (C) — both
    ///   Linux/Mono captures — are LF, and this port publishes LF in both
    ///   modes. Emitting CRLF here would corrupt the identity-(C) gate in
    ///   case 1 of this very file. ExpandRunNewlines must never reach an
    ///   S3K path.
    /// - **ADDR_FRAMECOUNT 0xFE08 → 0xFE04** (Lua commit 6564667eb, no
    ///   version bump). 0xFE08 is Debug_placement_mode, dead-zero in
    ///   gameplay; 0xFE04 is the live Level_frame_counter. The complete-run
    ///   Lua at HEAD reads 0xFE04 (s3k_complete_run_recorder.lua:547, whose
    ///   own comment records the change), so the port must too. Measured
    ///   consequence, and it is the whole consequence: across all 25 (B)
    ///   segments every physics.csv column 5 cell is the constant "0000"
    ///   and every aux vfc / level_frame_counter is the constant 0.
    /// - **Diagnostic hooks were armed.** (B) predates the
    ///   LIGHTWEIGHT_REGEN inversion (Lua commit 192d9c976), so its capture
    ///   ran the hook-registration block, and four of its 25 segments carry
    ///   hook-driven aux families that a hooks-off capture cannot emit:
    ///   hcz_2 95 lines, hcz_6 48, mgz 69, mgz_3 69.
    ///
    /// None of the three is a defect in this port, and none can be
    /// "fixed": reverting any of them would break the identity-(A) gate
    /// (S3KCompleteRunSegmentsDifferentialTests) and case 1 below, which
    /// are byte-exact against captures made by the CURRENT Lua. So (B) is
    /// gated at recorder level instead, and hard.
    ///
    /// ## What the (B) case actually proves
    ///
    /// It is not a weakened byte comparison — every byte is still
    /// accounted for, and the only free value is pinned to a constant:
    ///
    /// - **run_manifest.json**: the fixture, after CRLF→LF and the
    ///   substitution of its single 6.31 lua_script_version line, must
    ///   equal the produced manifest EXACTLY. That is 8,740 bytes covering
    ///   all 25 segment records (dir, kind, trace_profile,
    ///   bk2_frame_offset, trace_frame_count, zone_id, act,
    ///   bonus_stage_type / special_stage_index) and all 22 transition
    ///   records with their sampled RAM (special_bonus_entry_flag,
    ///   saved_x_pos, saved_y_pos, last_star_post_hit, rings_before,
    ///   rings_after, emeralds_before, emeralds_after) — reproduced by a
    ///   real capture, not fed to the formatter.
    ///   S3KCompleteRunPublicationTests already gates the formatter given
    ///   the right data; this gates the recorder discovering that data
    ///   from the movie.
    /// - **physics.csv**: same row count, and the set of differing COLUMN
    ///   indices must be exactly {5} for level and bonus segments and
    ///   EMPTY for the three special-stage segments, whose 20-column
    ///   schema has no counter. Every one of the other 41 columns of every
    ///   row must match byte for byte.
    /// - **aux_state.jsonl**: the fixture's hook-driven line count must
    ///   equal the pinned per-segment literal and the capture's must be 0;
    ///   after dropping exactly those lines, the two streams must be equal
    ///   line for line, in order, with the counter fields masked — and the
    ///   fixture's masked-away values are separately asserted to be the
    ///   constant 0 that the dead 0xFE08 read produces. A live value on the
    ///   fixture side fails rather than being absorbed.
    /// - **metadata.json**: the set of differing KEYS must equal the
    ///   pinned per-kind literal, key order must be identical, and every
    ///   other key's line must match byte for byte.
    ///
    /// The counter column itself is not left ungated by any of this: it is
    /// byte-gated on the four identity-(C) directories in case 1 — cut from
    /// this same movie — and on the seven identity-(A) directories in
    /// S3KCompleteRunSegmentsDifferentialTests.
    ///
    /// Cost, measured: 1m08s wall and 234 MB peak RSS per pass, 370 MB of
    /// output each. The passes run sequentially and each output tree is
    /// deleted before the next, so peak scratch is one pass. That scratch
    /// lives under the tool directory's .scratch/ rather than
    /// Path.GetTempPath() because /tmp is frequently a RAM-backed tmpfs.
    ///
    /// Skips (does not pass) when S3K_ROM_PATH, a BizHawk distribution, the
    /// movie or the fixtures are absent; fails (does not skip) on any
    /// mismatch.
    /// </summary>
    internal static class S3KRunModeDifferentialTests
    {
        private const int CaptureTimeoutMilliseconds = 3600000;
        private const string MovieFileName = "s3-knux-multibonus-ss.bk2";
        private const int MovieFrameCount = 114622;

        /// <summary>The identity-(C) run id, from its fixtures' metadata.</summary>
        private const string SetCRunId = "s3k-multibonus";

        /// <summary>The identity-(B) run id, from its manifest.</summary>
        private const string SetBRunId = "s3-knux-multibonus-ss";

        private const string RecordingDateLinePrefix =
            "  \"recording_date\": \"";
        private static readonly Regex RecordingDateLine = new Regex(
            "^  \"recording_date\": \"[0-9]{4}-[0-9]{2}-[0-9]{2}\",$");

        private const string CurrentVersionLine =
            "  \"lua_script_version\": \"6.32-s3k-completerun\",";

        /// <summary>
        /// The stamp (B) was captured under. Commit 9e3ccdb41 hand-edited
        /// only the eight bonus segments' metadata to 6.32 and never
        /// rewrote the manifest or the level/ss segments, which is why this
        /// literal appears in the manifest and in 17 of the 25 segments but
        /// not in the other 8 — an in-run version drift that is a fixture
        /// provenance artifact, not recorder behavior. write_run_manifest
        /// and write_metadata read the same LUA_SCRIPT_VERSION global and
        /// can never disagree in a real capture.
        /// </summary>
        private const string LegacyVersionLine =
            "  \"lua_script_version\": \"6.31-s3k-completerun\",";

        private const string CaptureModeKey = "capture_mode";
        private const string VersionKey = "lua_script_version";
        private const string PreTraceOscKey = "pre_trace_osc_frames";
        private const string RecordingDateKey = "recording_date";
        private const string RunIdKey = "run_id";

        /// <summary>
        /// Aux event families that only a hook-driven (memory-execute /
        /// memory-write callback) capture can emit. The port does not wire
        /// LibGPGX's callback surface at all and refuses
        /// OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS, so a produced stream must
        /// contain none of these; (B) contains four families' worth
        /// because it predates the LIGHTWEIGHT_REGEN inversion.
        /// </summary>
        private static readonly string[] HookDrivenEvents =
        {
            "position_write",
            "velocity_write",
            "solid_object_cont_entry",
            "cage_execution",
            "cnz_cylinder_execution",
            "sonic_record_pos",
            "rng_call",
            "tails_cpu_normal_step",
            "aiz_boundary_state",
            "aiz_transition_floor_solid",
            "collision_response_list_per_frame",
            "aiz_ship_loop",
            "cnz_event_ram"
        };

        // ------------------------------------------------------------------
        // Identity (C): four byte-exact directories.
        // ------------------------------------------------------------------

        /// <summary>
        /// docs/s3k-run-publication.md §0.3, independently recomputed from
        /// the committed .gz files. bonus_gumball is also covered by the
        /// five-second truncated gate in S3KCompleteRunDifferentialTests;
        /// it is repeated here so this pass stands alone. special_stage's
        /// aux is the empty file — the SS path opens aux_state.jsonl and
        /// never writes to it, and the port must publish the empty file
        /// rather than omit it.
        /// </summary>
        private static readonly SetCCase[] SetCCases =
        {
            new SetCCase(
                "gumball", "bonus_gumball",
                232294,
                "7faeb1e2b1804ac75b98daf97810e2c6be9818b8baee8417b56faff0"
                + "36cbe917",
                8408680,
                "842fbad87a91effb9749bcd7b95f61d558d1cb9929e35cf5ca9ac328"
                + "743460e7"),
            new SetCCase(
                "slots", "bonus_slots",
                195034,
                "4e037ce8ee31835333c04f72fbd953f4b14aab85bacbaea1f643ed9a"
                + "7c810dcb",
                2360965,
                "afe43538f38435bb28ef09defe765ff8fabeae6b4e9d1253485bc4f7"
                + "9c682249"),
            new SetCCase(
                "pachinko", "bonus_pachinko",
                494896,
                "61a25ceed47298965838c3c4bb3847dbab3702065a16e1581acfed11"
                + "07d3fc67",
                10733394,
                "129c19c636f1df05783f72d19af3f8aa1936bc44c63501d3825f1e8d"
                + "35c6acc3"),
            new SetCCase(
                "ss", "special_stage",
                272711,
                "b6afb3f5f9708f974bf71d5fcfb973aced8e60d81e51f64e01a88012"
                + "996fa5a1",
                0,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b"
                + "7852b855")
        };

        // ------------------------------------------------------------------
        // Identity (B): the 25-segment run tree.
        // ------------------------------------------------------------------

        private const string Level = "level";
        private const string Bonus = "bonus_stage";
        private const string Ss = "special_stage";

        /// <summary>
        /// The 25 published segments in recorder emission order, with the
        /// bk2_frame_offset / trace_frame_count pairs transcribed from the
        /// committed manifest, and the per-segment count of hook-driven aux
        /// lines the legacy capture contains. The dir tokens pin the
        /// repeat-visit suffix rule: one segment_dir_counts table keyed by
        /// BASE token, shared by the level, bonus and ss paths, 1-based, no
        /// zero padding, never reset — so aiz_5 is the fifth AIZ segment
        /// even though four other zones' segments were interleaved.
        /// </summary>
        private static readonly SetBCase[] SetBCases =
        {
            new SetBCase("aiz", Level, 915, 4654, 0),
            new SetBCase("gumball", Bonus, 5570, 1430, 0),
            new SetBCase("aiz_2", Level, 7001, 2140, 0),
            new SetBCase("slots", Bonus, 9142, 1200, 0),
            new SetBCase("aiz_3", Level, 10343, 7568, 0),
            new SetBCase("slots_2", Bonus, 17912, 1278, 0),
            new SetBCase("aiz_4", Level, 19191, 3210, 0),
            new SetBCase("gumball_2", Bonus, 22402, 1648, 0),
            new SetBCase("aiz_5", Level, 24051, 3631, 0),
            new SetBCase("hcz", Level, 27683, 3176, 0),
            new SetBCase("slots_3", Bonus, 30860, 5379, 0),
            new SetBCase("hcz_2", Level, 36240, 11933, 95),
            new SetBCase("ss", Ss, 48174, 4630, 0),
            new SetBCase("hcz_3", Level, 54274, 3949, 0),
            new SetBCase("slots_4", Bonus, 58224, 1603, 0),
            new SetBCase("hcz_4", Level, 59828, 2097, 0),
            new SetBCase("ss_2", Ss, 61926, 7194, 0),
            new SetBCase("hcz_5", Level, 70590, 3435, 0),
            new SetBCase("slots_5", Bonus, 74026, 1791, 0),
            new SetBCase("hcz_6", Level, 75818, 8422, 48),
            new SetBCase("mgz", Level, 84241, 8721, 69),
            new SetBCase("pachinko", Bonus, 92963, 3051, 0),
            new SetBCase("mgz_2", Level, 96015, 2076, 0),
            new SetBCase("ss_3", Ss, 98092, 6537, 0),
            new SetBCase("mgz_3", Level, 106104, 8517, 69)
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KRunModeDifferential native run-mode capture matches the"
                + " four canonical identity-(C) segments",
                NativeRunCaptureMatchesSetC));
            tests.Add(new TestMain.TestCase(
                "S3KRunModeDifferential native run-mode capture reproduces"
                + " the s3-knux-multibonus-ss run structure",
                NativeRunCaptureReproducesSetB));
        }

        // ==================================================================
        // Case 1 — identity (C), byte-exact
        // ==================================================================

        private static void NativeRunCaptureMatchesSetC()
        {
            string tracesRoot = TracesRoot();
            string moviePath =
                Path.Combine(tracesRoot, "_movies", MovieFileName);
            var fixtureDirectories = new List<string>();
            foreach (SetCCase segment in SetCCases)
            {
                fixtureDirectories.Add(
                    Path.Combine(tracesRoot, segment.FixtureDirectoryName));
            }
            Dependencies dependencies =
                Resolve(moviePath, fixtureDirectories);
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            WithCapture(
                dependencies, moviePath, SetCRunId, installation,
                delegate(string output)
                {
                    // Self-check the canonical bytes first, streamed from
                    // the .gz so nothing is materialised, so that a hash
                    // mismatch says whether the fixture or the capture
                    // moved.
                    foreach (SetCCase segment in SetCCases)
                    {
                        string fixtureDirectory = Path.Combine(
                            tracesRoot, segment.FixtureDirectoryName);
                        AssertFixtureBytes(
                            segment.FixtureDirectoryName + "/physics.csv"
                            + " (fixture)",
                            Path.Combine(fixtureDirectory, "physics.csv"),
                            segment.PhysicsLength,
                            segment.PhysicsSha256);
                        AssertFixtureBytes(
                            segment.FixtureDirectoryName
                            + "/aux_state.jsonl (fixture)",
                            Path.Combine(
                                fixtureDirectory, "aux_state.jsonl"),
                            segment.AuxStateLength,
                            segment.AuxStateSha256);
                    }

                    foreach (SetCCase segment in SetCCases)
                    {
                        string produced =
                            Path.Combine(output, segment.DirToken);
                        AssertProducedBytes(
                            segment.DirToken + "/physics.csv",
                            Path.Combine(produced, "physics.csv"),
                            segment.PhysicsLength,
                            segment.PhysicsSha256);
                        AssertProducedBytes(
                            segment.DirToken + "/aux_state.jsonl",
                            Path.Combine(produced, "aux_state.jsonl"),
                            segment.AuxStateLength,
                            segment.AuxStateSha256);
                        AssertMetadataEqualExceptRecordingDate(
                            segment.DirToken,
                            SetCRunId,
                            Path.Combine(
                                tracesRoot,
                                segment.FixtureDirectoryName,
                                "metadata.json"),
                            Path.Combine(produced, "metadata.json"));
                    }

                    AssertOutputLayout(output);
                });
        }

        /// <summary>
        /// Byte-identical to the fixture apart from the recording_date
        /// VALUE. Both sides must be LF-only and newline-terminated, have
        /// the same line count, carry exactly one recording_date line at
        /// the same index (well-formed on the produced side), carry exactly
        /// one lua_script_version line equal to the pinned 6.32 literal,
        /// and carry the run_id line for the id this pass was given — which
        /// is not a delta at all, because the identity-(C) fixtures were
        /// themselves captured under --run-id s3k-multibonus.
        /// </summary>
        private static void AssertMetadataEqualExceptRecordingDate(
            string context,
            string runId,
            string fixturePath,
            string producedPath)
        {
            string fixtureText = ReadAllTextExact(fixturePath);
            string producedText = ReadAllTextExact(producedPath);
            AssertPublishedShape(context + " (fixture)", fixtureText);
            AssertPublishedShape(context, producedText);

            string[] fixtureLines = fixtureText.Split('\n');
            string[] producedLines = producedText.Split('\n');
            if (fixtureLines.Length != producedLines.Length)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context
                    + "/metadata.json: line count is "
                    + producedLines.Length + "; expected "
                    + fixtureLines.Length + ".");
            }

            var recordingDateLines = 0;
            var versionLines = 0;
            var runIdLines = 0;
            string expectedRunIdLine =
                "  \"run_id\": \"" + runId + "\",";
            for (var index = 0; index < fixtureLines.Length; index++)
            {
                if (fixtureLines[index].StartsWith(
                    RecordingDateLinePrefix, StringComparison.Ordinal))
                {
                    recordingDateLines++;
                    if (!RecordingDateLine.IsMatch(producedLines[index]))
                    {
                        throw new InvalidOperationException(
                            "First divergence at " + context
                            + "/metadata.json line " + (index + 1)
                            + ": recording_date is malformed: <"
                            + producedLines[index] + ">.");
                    }
                    continue;
                }
                if (fixtureLines[index] == CurrentVersionLine)
                {
                    versionLines++;
                }
                if (fixtureLines[index] == expectedRunIdLine)
                {
                    runIdLines++;
                }
                if (fixtureLines[index] != producedLines[index])
                {
                    throw new InvalidOperationException(
                        "First divergence at " + context
                        + "/metadata.json line " + (index + 1) + ": was <"
                        + producedLines[index] + ">; expected <"
                        + fixtureLines[index] + ">.");
                }
            }
            AssertEx.Equal(1, recordingDateLines);
            AssertEx.Equal(1, versionLines);
            AssertEx.Equal(1, runIdLines);
        }

        // ==================================================================
        // Case 2 — identity (B), recorder-level with every delta pinned
        // ==================================================================

        private static void NativeRunCaptureReproducesSetB()
        {
            string tracesRoot = TracesRoot();
            string moviePath =
                Path.Combine(tracesRoot, "_movies", MovieFileName);
            string runRoot = Path.Combine(
                tracesRoot, "runs", SetBRunId);
            var fixtureDirectories = new List<string> { runRoot };
            foreach (SetBCase segment in SetBCases)
            {
                fixtureDirectories.Add(
                    Path.Combine(runRoot, segment.DirToken));
            }
            Dependencies dependencies =
                Resolve(moviePath, fixtureDirectories);
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(dependencies.BizHawkHome);

            WithCapture(
                dependencies, moviePath, SetBRunId, installation,
                delegate(string output)
                {
                    AssertRunManifestReproduced(runRoot, output);
                    foreach (SetBCase segment in SetBCases)
                    {
                        string fixtureDirectory =
                            Path.Combine(runRoot, segment.DirToken);
                        string produced =
                            Path.Combine(output, segment.DirToken);
                        AssertSetBPhysics(
                            segment, fixtureDirectory, produced);
                        AssertSetBAux(
                            segment, fixtureDirectory, produced);
                        AssertSetBMetadata(
                            segment, fixtureDirectory, produced);
                    }
                    AssertOutputLayout(output);
                });
        }

        /// <summary>
        /// The produced manifest must equal the committed (B) manifest
        /// after exactly two pinned substitutions and no others: CRLF→LF
        /// (a Windows text-mode artifact of the legacy host) and the single
        /// 6.31 lua_script_version line. Both are asserted to be present on
        /// the fixture side before being applied, so a regenerated fixture
        /// that no longer needs them fails here instead of passing
        /// vacuously.
        /// </summary>
        private static void AssertRunManifestReproduced(
            string runRoot, string output)
        {
            string fixturePath =
                Path.Combine(runRoot, "run_manifest.json");
            string producedPath =
                Path.Combine(output, "run_manifest.json");
            if (!File.Exists(producedPath))
            {
                throw new InvalidOperationException(
                    "First divergence at run_manifest.json: the run-mode"
                    + " pass published none. The Lua's gate (L1459) is a"
                    + " disjunction — a run id alone forces emission.");
            }
            string fixtureText = ReadAllTextExact(fixturePath);
            if (fixtureText.IndexOf("\r\n", StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "The (B) run manifest is expected to be CRLF (a"
                    + " Windows-EmuHawk capture artifact). If it is now"
                    + " LF, the fixture was regenerated and"
                    + " docs/s3k-run-publication.md section 7.4 must be"
                    + " revisited.");
            }
            string normalized = fixtureText.Replace("\r\n", "\n");
            AssertLineCount(
                "run_manifest.json (fixture)",
                normalized, LegacyVersionLine, 1);
            AssertLineCount(
                "run_manifest.json (fixture)",
                normalized, CurrentVersionLine, 0);
            string expected = normalized.Replace(
                LegacyVersionLine + "\n", CurrentVersionLine + "\n");

            string producedText = ReadAllTextExact(producedPath);
            AssertPublishedShape("run_manifest.json", producedText);
            AssertTextEqual("run_manifest.json", expected, producedText);
        }

        /// <summary>
        /// Same header, same row count, and the set of differing COLUMN
        /// indices exactly {5} for level and bonus segments (the 42-column
        /// v7 schema's gameplay_frame_counter) and EMPTY for special-stage
        /// segments, whose 20-column schema carries no counter. The
        /// fixture's column 5 must additionally be the constant "0000" on
        /// every row — the observable signature of the dead 0xFE08
        /// Debug_placement_mode read — so a fixture carrying live values
        /// fails rather than being absorbed.
        /// </summary>
        private static void AssertSetBPhysics(
            SetBCase segment, string fixtureDirectory, string produced)
        {
            string context = segment.DirToken + "/physics.csv";
            string[] fixtureLines = SplitLines(
                ReadFixtureText(
                    Path.Combine(fixtureDirectory, "physics.csv")));
            string producedText = ReadAllTextExact(
                Path.Combine(produced, "physics.csv"));
            AssertPublishedShape(context, producedText);
            string[] producedLines = SplitLines(producedText);

            if (fixtureLines.Length != producedLines.Length)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": line count is "
                    + producedLines.Length + "; expected "
                    + fixtureLines.Length + ".");
            }
            AssertEx.Equal(
                segment.TraceFrameCount, producedLines.Length - 1);
            AssertTextEqual(
                context + " header", fixtureLines[0], producedLines[0]);

            bool isSpecialStage = segment.Kind == Ss;
            var differingColumns = new List<int>();
            for (var row = 1; row < fixtureLines.Length; row++)
            {
                if (fixtureLines[row] == producedLines[row])
                {
                    continue;
                }
                string[] fixtureCells = fixtureLines[row].Split(',');
                string[] producedCells = producedLines[row].Split(',');
                if (fixtureCells.Length != producedCells.Length)
                {
                    throw new InvalidOperationException(
                        "First divergence at " + context + " row "
                        + (row - 1) + ": column count is "
                        + producedCells.Length + "; expected "
                        + fixtureCells.Length + ".");
                }
                for (var column = 0;
                    column < fixtureCells.Length;
                    column++)
                {
                    if (fixtureCells[column] == producedCells[column])
                    {
                        continue;
                    }
                    if (isSpecialStage
                        || column != S3KLevelFrameCounterColumn)
                    {
                        throw new InvalidOperationException(
                            "First divergence at " + context + " row "
                            + (row - 1) + " column " + column + ": was <"
                            + producedCells[column] + ">; expected <"
                            + fixtureCells[column] + ">. Only column "
                            + S3KLevelFrameCounterColumn
                            + " (gameplay_frame_counter) may differ, and"
                            + " only on a level or bonus segment.");
                    }
                    if (fixtureCells[column] != DeadFrameCounterCell)
                    {
                        throw new InvalidOperationException(
                            "First divergence at " + context + " row "
                            + (row - 1) + " column " + column
                            + ": the legacy fixture cell is <"
                            + fixtureCells[column] + ">, not the constant <"
                            + DeadFrameCounterCell + "> a dead 0xFE08 read"
                            + " produces. The fixture was regenerated;"
                            + " re-derive the permitted delta.");
                    }
                    if (!differingColumns.Contains(column))
                    {
                        differingColumns.Add(column);
                    }
                }
            }
            differingColumns.Sort();
            AssertTextEqual(
                context + " differing column indices",
                isSpecialStage
                    ? string.Empty
                    : S3KLevelFrameCounterColumn.ToString(),
                string.Join(
                    ",",
                    differingColumns.ConvertAll(
                        delegate(int value)
                        {
                            return value.ToString();
                        }).ToArray()));
        }

        /// <summary>
        /// After dropping the fixture's hook-driven lines — whose count
        /// must equal the pinned per-segment literal, and of which the
        /// capture must have none — the two streams must be equal line for
        /// line and in order, with only the vfc and level_frame_counter
        /// VALUES masked. The fixture's masked values are separately
        /// required to be the constant 0.
        ///
        /// Both sides are streamed a line at a time: the largest (B)
        /// segment's aux is 32 MB compressed and neither side is
        /// materialised.
        /// </summary>
        private static void AssertSetBAux(
            SetBCase segment, string fixtureDirectory, string produced)
        {
            string context = segment.DirToken + "/aux_state.jsonl";
            var fixtureHookLines = 0;
            var producedHookLines = 0;
            var compared = 0;
            using (TextReader fixtureReader = OpenFixtureReader(
                Path.Combine(fixtureDirectory, "aux_state.jsonl")))
            using (TextReader producedReader = new StreamReader(
                Path.Combine(produced, "aux_state.jsonl"),
                new UTF8Encoding(false)))
            {
                while (true)
                {
                    string fixtureLine = NextNonHookLine(
                        fixtureReader, ref fixtureHookLines);
                    string producedLine = NextNonHookLine(
                        producedReader, ref producedHookLines);
                    if (fixtureLine == null && producedLine == null)
                    {
                        break;
                    }
                    if (fixtureLine == null || producedLine == null)
                    {
                        throw new InvalidOperationException(
                            "First divergence at " + context + " line "
                            + (compared + 1) + ": "
                            + (producedLine == null
                                ? "the capture ended early."
                                : "the capture has extra lines, starting <"
                                    + Truncate(producedLine) + ">."));
                    }
                    compared++;
                    string maskedFixture = MaskFrameCounters(
                        context, compared, fixtureLine, true);
                    string maskedProduced = MaskFrameCounters(
                        context, compared, producedLine, false);
                    if (maskedFixture != maskedProduced)
                    {
                        throw new InvalidOperationException(
                            "First divergence at " + context + " line "
                            + compared + ": was <"
                            + Truncate(producedLine) + ">; expected <"
                            + Truncate(fixtureLine)
                            + "> (vfc / level_frame_counter values"
                            + " excluded).");
                    }
                }
            }
            AssertEx.Equal(segment.HookDrivenAuxLines, fixtureHookLines);
            AssertEx.Equal(0, producedHookLines);
        }

        /// <summary>
        /// The set of differing metadata KEYS must equal the pinned literal
        /// for this segment kind, key ORDER must be identical, and every
        /// other key's line must match byte for byte:
        ///
        /// - level: capture_mode added, lua_script_version 6.31→6.32,
        ///   pre_trace_osc_frames 0→1, recording_date.
        /// - bonus: capture_mode added, pre_trace_osc_frames 0→1,
        ///   recording_date. lua_script_version already reads 6.32 because
        ///   commit 9e3ccdb41 hand-edited exactly these eight files, and
        ///   v_int_run_count — the line that commit ADDED — must therefore
        ///   MATCH, which is what makes this the tightest available check
        ///   on the arm-time 0xFE0C sample.
        /// - special_stage: lua_script_version 6.31→6.32 and
        ///   recording_date, and nothing else — the SS shape has no
        ///   capture_mode branch and no pre_trace_osc_frames key at all,
        ///   which is why its delta is strictly smaller.
        /// </summary>
        private static void AssertSetBMetadata(
            SetBCase segment, string fixtureDirectory, string produced)
        {
            string context = segment.DirToken + "/metadata.json";
            string fixtureText = ReadFixtureText(
                Path.Combine(fixtureDirectory, "metadata.json"));
            string producedText = ReadAllTextExact(
                Path.Combine(produced, "metadata.json"));
            AssertPublishedShape(context, producedText);

            List<string> fixtureKeys;
            IDictionary<string, string> fixtureByKey =
                ParseMetadataLines(fixtureText, out fixtureKeys);
            List<string> producedKeys;
            IDictionary<string, string> producedByKey =
                ParseMetadataLines(producedText, out producedKeys);

            // recording_date is excluded from the diff and checked
            // separately for well-formedness: it is sampled at finalize, so
            // whether it differs from the 2026-07-19 fixture depends on the
            // day the gate runs. Requiring it to differ would make the gate
            // pass or fail by calendar.
            var differing = new List<string>();
            foreach (string key in producedKeys)
            {
                if (key == RecordingDateKey)
                {
                    continue;
                }
                if (!fixtureByKey.ContainsKey(key))
                {
                    differing.Add(key);
                    continue;
                }
                if (fixtureByKey[key] != producedByKey[key])
                {
                    differing.Add(key);
                }
            }
            foreach (string key in fixtureKeys)
            {
                if (key != RecordingDateKey
                    && !producedByKey.ContainsKey(key))
                {
                    differing.Add(key);
                }
            }
            differing.Sort(StringComparer.Ordinal);
            AssertTextEqual(
                context + " differing keys",
                string.Join(",", segment.PermittedMetadataDeltaKeys),
                string.Join(",", differing.ToArray()));

            // Key order must be identical over the shared keys: a
            // set-equality check alone would let a reordering through.
            var sharedFixtureOrder = new List<string>();
            foreach (string key in fixtureKeys)
            {
                if (producedByKey.ContainsKey(key))
                {
                    sharedFixtureOrder.Add(key);
                }
            }
            var sharedProducedOrder = new List<string>();
            foreach (string key in producedKeys)
            {
                if (fixtureByKey.ContainsKey(key))
                {
                    sharedProducedOrder.Add(key);
                }
            }
            AssertTextEqual(
                context + " key order",
                string.Join(",", sharedFixtureOrder.ToArray()),
                string.Join(",", sharedProducedOrder.ToArray()));

            // The values behind each permitted delta, pinned.
            AssertTextEqual(
                context + " lua_script_version (fixture)",
                segment.Kind == Bonus
                    ? CurrentVersionLine
                    : LegacyVersionLine,
                fixtureByKey[VersionKey]);
            AssertTextEqual(
                context + " lua_script_version",
                CurrentVersionLine,
                producedByKey[VersionKey]);
            AssertEx.Equal(false, fixtureByKey.ContainsKey(CaptureModeKey));
            if (segment.Kind == Ss)
            {
                AssertEx.Equal(
                    false, producedByKey.ContainsKey(CaptureModeKey));
                AssertEx.Equal(
                    false, producedByKey.ContainsKey(PreTraceOscKey));
            }
            else
            {
                AssertTextEqual(
                    context + " capture_mode",
                    "  \"capture_mode\":"
                    + " \"physics_animation_aux_without_diagnostic_hooks\",",
                    producedByKey[CaptureModeKey]);
                AssertTextEqual(
                    context + " pre_trace_osc_frames (fixture)",
                    "  \"pre_trace_osc_frames\": 0,",
                    fixtureByKey[PreTraceOscKey]);
                AssertTextEqual(
                    context + " pre_trace_osc_frames",
                    "  \"pre_trace_osc_frames\": 1,",
                    producedByKey[PreTraceOscKey]);
            }
            AssertTextEqual(
                context + " run_id",
                "  \"run_id\": \"" + SetBRunId + "\",",
                producedByKey[RunIdKey]);
            if (!RecordingDateLine.IsMatch(
                producedByKey[RecordingDateKey]))
            {
                throw new InvalidOperationException(
                    "First divergence at " + context
                    + ": recording_date is malformed: <"
                    + producedByKey[RecordingDateKey] + ">.");
            }
        }

        // ==================================================================
        // Shared capture driver and helpers
        // ==================================================================

        /// <summary>
        /// The 0-based index of gameplay_frame_counter in the 42-column
        /// CSV v7 schema (frame, input, camera_x, camera_y, rings,
        /// gameplay_frame_counter, …).
        /// </summary>
        private const int S3KLevelFrameCounterColumn = 5;

        /// <summary>
        /// What a dead 0xFE08 (Debug_placement_mode) read renders as in
        /// column 5 — the constant every (B) row carries.
        /// </summary>
        private const string DeadFrameCounterCell = "0000";

        private static void WithCapture(
            Dependencies dependencies,
            string moviePath,
            string runId,
            BizHawkInstallation installation,
            Action<string> body)
        {
            string root = Path.Combine(
                EndToEndTests.ToolDirectory,
                ".scratch",
                "s3k-runmode-" + Guid.NewGuid().ToString("N"));
            string output = Path.Combine(root, "capture");
            try
            {
                Directory.CreateDirectory(root);
                string stdout = RunCapture(
                    dependencies.RomPath,
                    dependencies.BizHawkHome,
                    moviePath,
                    output,
                    runId);
                // The whole 25-segment summary, so a segmentation
                // regression anywhere in the movie is reported as a
                // summary diff before any byte comparison runs.
                AssertTextEqual(
                    "stdout",
                    ExpectedStdout(installation, runId, output),
                    stdout);
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

        /// <summary>
        /// Exactly the 25 segment directories with three files each, plus
        /// run_manifest.json and nothing else. Directories the recorder
        /// pre-creates for zones the movie never visits are not published,
        /// because the publisher stages files rather than directories.
        /// </summary>
        private static void AssertOutputLayout(string output)
        {
            var expectedDirectories = new List<string>();
            foreach (SetBCase segment in SetBCases)
            {
                expectedDirectories.Add(segment.DirToken);
            }
            expectedDirectories.Sort(StringComparer.Ordinal);

            var actualDirectories = new List<string>();
            foreach (string path in Directory.GetDirectories(output))
            {
                actualDirectories.Add(Path.GetFileName(path));
            }
            actualDirectories.Sort(StringComparer.Ordinal);
            AssertTextEqual(
                "published directories",
                string.Join("\n", expectedDirectories.ToArray()),
                string.Join("\n", actualDirectories.ToArray()));

            var actualRootFiles = new List<string>();
            foreach (string path in Directory.GetFiles(output))
            {
                actualRootFiles.Add(Path.GetFileName(path));
            }
            actualRootFiles.Sort(StringComparer.Ordinal);
            AssertTextEqual(
                "published root files",
                "run_manifest.json",
                string.Join("\n", actualRootFiles.ToArray()));

            foreach (string dirToken in actualDirectories)
            {
                string directory = Path.Combine(output, dirToken);
                var actualFiles = new List<string>();
                foreach (string path in Directory.GetFiles(directory))
                {
                    actualFiles.Add(Path.GetFileName(path));
                }
                actualFiles.Sort(StringComparer.Ordinal);
                AssertTextEqual(
                    dirToken + " files",
                    "aux_state.jsonl\nmetadata.json\nphysics.csv",
                    string.Join("\n", actualFiles.ToArray()));
                AssertEx.Equal(
                    0, Directory.GetDirectories(directory).Length);
            }
        }

        private static string ExpectedStdout(
            BizHawkInstallation installation,
            string runId,
            string output)
        {
            var expected = new StringBuilder();
            expected.Append("BizHawk: ")
                .Append(installation.ManagedVersion).Append('\n');
            expected.Append("ROM SHA-1: ")
                .Append(RomIdentity.Sonic3kLockOnSha1).Append('\n');
            expected.Append("Movie frames: ")
                .Append(MovieFrameCount).Append('\n');
            // No "Effective movie length" line: both passes are
            // untruncated. A "Run ID" line rather than a "Trace profile"
            // one, because --run-id selects the recorder here.
            expected.Append("Run ID: ").Append(runId).Append('\n');
            expected.Append("Segments: ")
                .Append(SetBCases.Length).Append('\n');
            expected.Append("Transitions: 22\n");
            foreach (SetBCase segment in SetBCases)
            {
                expected.Append("Segment ").Append(segment.DirToken)
                    .Append(": kind=").Append(segment.Kind)
                    .Append(", BK2 frame offset=")
                    .Append(segment.Bk2FrameOffset)
                    .Append(", trace frames=")
                    .Append(segment.TraceFrameCount)
                    .Append('\n');
            }
            // The manifest line is present in BOTH passes: the Lua's gate
            // (L1459) is a disjunction, so a run id alone forces emission
            // even before the movie's first detour is seen. Its absence is
            // what makes identity (A) identity (A).
            expected.Append("Run manifest: ")
                .Append(Path.Combine(output, "run_manifest.json"))
                .Append('\n');
            return expected.ToString();
        }

        private static string RunCapture(
            string romPath,
            string bizHawkHome,
            string moviePath,
            string output,
            string runId)
        {
            var start = new ProcessStartInfo
            {
                FileName = "/bin/bash",
                Arguments =
                    EndToEndTests.Quote(Path.Combine(
                        EndToEndTests.ToolDirectory, "run.sh"))
                    + " --mode trace"
                    + " --rom " + EndToEndTests.Quote(romPath)
                    + " --movie " + EndToEndTests.Quote(moviePath)
                    + " --output " + EndToEndTests.Quote(output)
                    + " --run-id " + EndToEndTests.Quote(runId),
                UseShellExecute = false,
                RedirectStandardOutput = true,
                RedirectStandardError = true
            };
            start.EnvironmentVariables["BIZHAWK_HOME"] = bizHawkHome;
            start.EnvironmentVariables["DISPLAY"] = ":99";
            // The port refuses every output-affecting complete-run recorder
            // environment variable it does not model
            // (Program.RejectUnmodeledS3kCompleteRunEnvironment). The
            // fixtures were captured with all eight unset, so the gate
            // clears them rather than inheriting a stray value from the
            // developer's shell — which would turn a byte gate into a
            // refusal.
            foreach (string unmodeled in new[]
            {
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

            EndToEndTests.ProcessResult result =
                EndToEndTests.RunProcess(start, CaptureTimeoutMilliseconds);
            if (result.ExitCode != 0)
            {
                throw new InvalidOperationException(
                    "S3K run-mode capture exited " + result.ExitCode
                    + ". stderr: " + result.StandardError);
            }
            AssertEx.Equal(string.Empty, result.StandardError);
            return result.StandardOutput;
        }

        /// <summary>
        /// Reads the next line whose event is NOT hook-driven, counting
        /// the hook-driven lines it skipped. Returns null at end of
        /// stream.
        /// </summary>
        private static string NextNonHookLine(
            TextReader reader, ref int hookLines)
        {
            while (true)
            {
                string line = reader.ReadLine();
                if (line == null)
                {
                    return null;
                }
                if (line.Length == 0)
                {
                    continue;
                }
                if (IsHookDriven(line))
                {
                    hookLines++;
                    continue;
                }
                return line;
            }
        }

        private static bool IsHookDriven(string line)
        {
            const string marker = "\"event\":\"";
            int start = line.IndexOf(marker, StringComparison.Ordinal);
            if (start < 0)
            {
                return false;
            }
            start += marker.Length;
            int end = line.IndexOf('"', start);
            if (end < 0)
            {
                return false;
            }
            string name = line.Substring(start, end - start);
            foreach (string candidate in HookDrivenEvents)
            {
                if (candidate == name)
                {
                    return true;
                }
            }
            return false;
        }

        /// <summary>
        /// Replaces the VALUE of every "vfc" and "level_frame_counter"
        /// field with a placeholder. On the fixture side the replaced value
        /// must be exactly "0" — the dead 0xFE08 read — so this is a pinned
        /// substitution of a known constant, not a tolerance.
        /// </summary>
        private static string MaskFrameCounters(
            string context, int lineNumber, string line, bool isFixture)
        {
            string masked = MaskField(
                context, lineNumber, line, "\"vfc\":", isFixture);
            return MaskField(
                context, lineNumber, masked,
                "\"level_frame_counter\":", isFixture);
        }

        private static string MaskField(
            string context,
            int lineNumber,
            string line,
            string key,
            bool isFixture)
        {
            var builder = new StringBuilder(line.Length);
            var index = 0;
            while (true)
            {
                int found = line.IndexOf(
                    key, index, StringComparison.Ordinal);
                if (found < 0)
                {
                    builder.Append(line, index, line.Length - index);
                    return builder.ToString();
                }
                int valueStart = found + key.Length;
                int valueEnd = valueStart;
                if (valueEnd < line.Length && line[valueEnd] == '-')
                {
                    valueEnd++;
                }
                while (valueEnd < line.Length
                    && line[valueEnd] >= '0'
                    && line[valueEnd] <= '9')
                {
                    valueEnd++;
                }
                if (isFixture)
                {
                    string value = line.Substring(
                        valueStart, valueEnd - valueStart);
                    if (value != "0")
                    {
                        throw new InvalidOperationException(
                            "First divergence at " + context + " line "
                            + lineNumber + ": the legacy fixture's " + key
                            + " is <" + value + ">, not the constant 0 a"
                            + " dead 0xFE08 read produces. The fixture was"
                            + " regenerated; re-derive the permitted"
                            + " delta.");
                    }
                }
                builder.Append(line, index, valueStart - index);
                builder.Append('X');
                index = valueEnd;
            }
        }

        /// <summary>
        /// Splits a metadata.json body into its "  \"key\": value," lines,
        /// preserving order. The opening brace, closing brace and trailing
        /// empty line are dropped.
        /// </summary>
        private static IDictionary<string, string> ParseMetadataLines(
            string text, out List<string> order)
        {
            order = new List<string>();
            var byKey = new Dictionary<string, string>();
            foreach (string line in text.Split('\n'))
            {
                const string prefix = "  \"";
                if (!line.StartsWith(prefix, StringComparison.Ordinal))
                {
                    continue;
                }
                int end = line.IndexOf('"', prefix.Length);
                if (end < 0)
                {
                    continue;
                }
                string key = line.Substring(
                    prefix.Length, end - prefix.Length);
                order.Add(key);
                byKey[key] = line;
            }
            return byKey;
        }

        private static void AssertLineCount(
            string context, string text, string line, int expected)
        {
            var count = 0;
            foreach (string candidate in text.Split('\n'))
            {
                if (candidate == line)
                {
                    count++;
                }
            }
            if (count != expected)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": found " + count
                    + " occurrences of <" + line + ">; expected "
                    + expected + ".");
            }
        }

        private static void AssertTextEqual(
            string context, string expected, string actual)
        {
            if (expected == actual)
            {
                return;
            }
            string[] expectedLines = expected.Split('\n');
            string[] actualLines = actual.Split('\n');
            int shared = Math.Min(
                expectedLines.Length, actualLines.Length);
            for (var index = 0; index < shared; index++)
            {
                if (expectedLines[index] != actualLines[index])
                {
                    throw new InvalidOperationException(
                        "First divergence at " + context + " line "
                        + (index + 1) + ": was <"
                        + Truncate(actualLines[index]) + ">; expected <"
                        + Truncate(expectedLines[index]) + ">.");
                }
            }
            throw new InvalidOperationException(
                "First divergence at " + context + ": line count is "
                + actualLines.Length + "; expected "
                + expectedLines.Length + ".");
        }

        private static string Truncate(string value)
        {
            return value.Length <= 240
                ? value
                : value.Substring(0, 240) + "…";
        }

        /// <summary>
        /// Every file this recorder publishes is LF-only and
        /// newline-terminated, in BOTH plain and run mode: identity (C) is
        /// a run-mode Linux capture with LF output, so the S1/S2 "run mode
        /// implies CRLF" rule (ExpandRunNewlines) must never reach an S3K
        /// path.
        /// </summary>
        private static void AssertPublishedShape(
            string context, string text)
        {
            if (text.IndexOf('\r') >= 0)
            {
                throw new InvalidOperationException(
                    context + " contains CR; the S3K complete-run recorder"
                    + " publishes LF in both plain and run mode.");
            }
            if (text.Length != 0
                && !text.EndsWith("\n", StringComparison.Ordinal))
            {
                throw new InvalidOperationException(
                    context + " is not newline-terminated.");
            }
        }

        /// <summary>
        /// Reads a published or committed text file exactly: no newline
        /// translation, no BOM insertion.
        /// </summary>
        private static string ReadAllTextExact(string path)
        {
            using (var reader = new StreamReader(
                path, new UTF8Encoding(false)))
            {
                return reader.ReadToEnd();
            }
        }

        /// <summary>
        /// Reads a committed fixture that may be stored gzipped, with the
        /// legacy CRLF collapsed to LF. Every (B) file is CRLF; the
        /// collapse is asserted to be necessary by the callers that can
        /// (the manifest) and is uniform for the rest.
        /// </summary>
        private static string ReadFixtureText(string plainPath)
        {
            using (TextReader reader = OpenFixtureReader(plainPath))
            {
                return reader.ReadToEnd().Replace("\r\n", "\n");
            }
        }

        private static TextReader OpenFixtureReader(string plainPath)
        {
            if (File.Exists(plainPath))
            {
                return new StreamReader(
                    plainPath, new UTF8Encoding(false));
            }
            return new StreamReader(
                new GZipStream(
                    File.OpenRead(plainPath + ".gz"),
                    CompressionMode.Decompress),
                new UTF8Encoding(false));
        }

        private static string[] SplitLines(string text)
        {
            string trimmed = text.EndsWith("\n", StringComparison.Ordinal)
                ? text.Substring(0, text.Length - 1)
                : text;
            return trimmed.Split('\n');
        }

        private static void AssertFixtureBytes(
            string context,
            string plainPath,
            long expectedLength,
            string expectedSha256)
        {
            if (File.Exists(plainPath))
            {
                AssertProducedBytes(
                    context, plainPath, expectedLength, expectedSha256);
                return;
            }
            long length;
            string actual = HashGzip(plainPath + ".gz", out length);
            AssertBytes(
                context, expectedLength, expectedSha256, length, actual);
        }

        private static void AssertProducedBytes(
            string context,
            string path,
            long expectedLength,
            string expectedSha256)
        {
            var info = new FileInfo(path);
            if (!info.Exists)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": file is absent.");
            }
            AssertBytes(
                context,
                expectedLength,
                expectedSha256,
                info.Length,
                EndToEndTests.ComputeSha256(path));
        }

        /// <summary>
        /// Reports the byte length first: a length mismatch localises a
        /// truncated or over-long stream far better than a hash mismatch,
        /// which only says "different".
        /// </summary>
        private static void AssertBytes(
            string context,
            long expectedLength,
            string expectedSha256,
            long actualLength,
            string actualSha256)
        {
            if (expectedLength != actualLength)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context + ": length is "
                    + actualLength + " bytes; expected " + expectedLength
                    + ".");
            }
            if (expectedSha256 != actualSha256)
            {
                throw new InvalidOperationException(
                    "First divergence at " + context
                    + ": length matches but sha256 is <" + actualSha256
                    + ">; expected <" + expectedSha256 + ">.");
            }
        }

        private static string HashGzip(string gzipPath, out long length)
        {
            var total = 0L;
            var buffer = new byte[1 << 16];
            using (FileStream source = File.OpenRead(gzipPath))
            using (var gzip = new GZipStream(
                source, CompressionMode.Decompress))
            using (SHA256 sha256 = SHA256.Create())
            {
                while (true)
                {
                    int read = gzip.Read(buffer, 0, buffer.Length);
                    if (read <= 0)
                    {
                        break;
                    }
                    sha256.TransformBlock(buffer, 0, read, null, 0);
                    total += read;
                }
                sha256.TransformFinalBlock(buffer, 0, 0);
                length = total;
                return BitConverter.ToString(sha256.Hash)
                    .Replace("-", string.Empty)
                    .ToLowerInvariant();
            }
        }

        private static string TracesRoot()
        {
            return Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k");
        }

        private static Dependencies Resolve(
            string moviePath, IList<string> fixtureDirectories)
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

            if (!File.Exists(moviePath))
            {
                missing.Add("canonical movie is absent: " + moviePath);
            }
            foreach (string directory in fixtureDirectories)
            {
                if (!Directory.Exists(directory))
                {
                    missing.Add(
                        "canonical fixture directory is absent: "
                        + directory);
                }
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
                    "Supplied S3K_ROM_PATH does not exist: " + romPath
                    + ".");
            }
            RomIdentity.ValidateSonic3kLockOn(File.ReadAllBytes(romPath));
            BizHawkInstallation.Validate(bizHawkHome);
            return new Dependencies(romPath, bizHawkHome);
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

        private sealed class SetCCase
        {
            public SetCCase(
                string dirToken,
                string fixtureDirectoryName,
                long physicsLength,
                string physicsSha256,
                long auxStateLength,
                string auxStateSha256)
            {
                DirToken = dirToken;
                FixtureDirectoryName = fixtureDirectoryName;
                PhysicsLength = physicsLength;
                PhysicsSha256 = physicsSha256;
                AuxStateLength = auxStateLength;
                AuxStateSha256 = auxStateSha256;
            }

            public string DirToken { get; private set; }
            public string FixtureDirectoryName { get; private set; }
            public long PhysicsLength { get; private set; }
            public string PhysicsSha256 { get; private set; }
            public long AuxStateLength { get; private set; }
            public string AuxStateSha256 { get; private set; }
        }

        private sealed class SetBCase
        {
            public SetBCase(
                string dirToken,
                string kind,
                int bk2FrameOffset,
                int traceFrameCount,
                int hookDrivenAuxLines)
            {
                DirToken = dirToken;
                Kind = kind;
                Bk2FrameOffset = bk2FrameOffset;
                TraceFrameCount = traceFrameCount;
                HookDrivenAuxLines = hookDrivenAuxLines;
            }

            public string DirToken { get; private set; }
            public string Kind { get; private set; }
            public int Bk2FrameOffset { get; private set; }
            public int TraceFrameCount { get; private set; }
            public int HookDrivenAuxLines { get; private set; }

            /// <summary>
            /// The metadata keys permitted to differ, sorted ordinally, as
            /// the exact literal this segment's kind requires.
            /// recording_date is excluded — it is date-dependent and is
            /// checked separately for well-formedness.
            /// </summary>
            public string[] PermittedMetadataDeltaKeys
            {
                get
                {
                    if (Kind == Ss)
                    {
                        return new[] { VersionKey };
                    }
                    if (Kind == Bonus)
                    {
                        return new[] { CaptureModeKey, PreTraceOscKey };
                    }
                    return new[]
                    {
                        CaptureModeKey, VersionKey, PreTraceOscKey
                    };
                }
            }
        }
    }
}
