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
                "TraceCli trace run compresses payloads by default and"
                + " honors --no-compress",
                TraceRunPublishesCompressedPayloads));
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
            tests.Add(new TestMain.TestCase(
                "TraceCli rejects S2-only arguments with the Sonic 1 ROM",
                RejectsS2OnlyArgumentsWithSonic1Rom));
            tests.Add(new TestMain.TestCase(
                "TraceCli S1 complete run publishes level segments with LF",
                S1CompleteRunPublishesLevelSegmentsWithLf));
            tests.Add(new TestMain.TestCase(
                "TraceCli S1 run mode publishes segments and manifest with"
                + " CRLF",
                S1RunModePublishesSegmentsAndManifestWithCrlf));
            tests.Add(new TestMain.TestCase(
                "TraceCli S1 complete run emits the manifest for a detour"
                + " without a run id",
                S1CompleteRunEmitsManifestForDetourWithoutRunId));
            tests.Add(new TestMain.TestCase(
                "TraceCli S1 complete run refuses an existing run manifest",
                S1CompleteRunRefusesExistingRunManifest));
            tests.Add(new TestMain.TestCase(
                "TraceCli S1 run mode failure leaves no partial outputs",
                S1RunModeFailureLeavesNoPartialOutputs));
            tests.Add(new TestMain.TestCase(
                "TraceCli S3K trace publishes with labeled stdout",
                S3kTracePublishesWithLabeledStdout));
            tests.Add(new TestMain.TestCase(
                "TraceCli rejects the gameplay-segment argument with the"
                + " S3K ROM",
                RejectsSegmentArgumentWithS3kRom));
            // The four environment-variable tests below set OGGF_* in
            // this process and restore them afterwards. A capture child
            // started anywhere in that window inherits the block, and
            // OGGF_TRACE_STOP_FRAME or OGGF_BK2_FRAME_COUNT would then
            // truncate a concurrent gate's capture — either a refusal or,
            // worse, a shorter trace. They run alone.
            tests.Add(new TestMain.TestCase(
                "TraceCli S3K trace refuses every unmodeled output"
                + " affecting environment variable",
                S3kTraceRefusesUnmodeledEnvironment,
                game: "s3k",
                serial: true));
            tests.Add(new TestMain.TestCase(
                "TraceCli S3K trace does not refuse the deferred"
                + " families' hook-gated window overrides",
                S3kTraceAcceptsHookGatedWindowEnvironment,
                game: "s3k",
                serial: true));
            tests.Add(new TestMain.TestCase(
                "TraceCli S3K complete-run mode publishes segment"
                + " directories and a manifest with labeled stdout",
                S3kCompleteRunPublishesRunLayout));
            tests.Add(new TestMain.TestCase(
                "TraceCli S3K complete_run profile publishes segments with"
                + " no manifest and no run_id",
                S3kCompleteRunProfilePublishesWithoutManifest));
            tests.Add(new TestMain.TestCase(
                "TraceCli S3K complete-run refuses every unmodeled output"
                + " affecting environment variable",
                S3kCompleteRunRefusesUnmodeledEnvironment,
                game: "s3k",
                serial: true));
            tests.Add(new TestMain.TestCase(
                "TraceCli S3K complete-run does not refuse the variables"
                + " that cannot change its output",
                S3kCompleteRunAcceptsNonOutputEnvironment,
                game: "s3k",
                serial: true));
        }

        /// <summary>
        /// S3K standard trace (auto-detected from the locked-on ROM's
        /// SHA-1): the shared four-file publication pipeline with the
        /// S3K runner and the S2-style stdout contract minus the
        /// segment line (S3K has no --gameplay-segment).
        /// </summary>
        private static void S3kTracePublishesWithLabeledStdout()
        {
            S3kTraceCliDependencies dependencies = ResolveS3kDependencies();
            // 7 rows with detection at frame 3 yields 3 trace rows (the
            // frame fed by the movie's final input row is never
            // recorded).
            WithSyntheticMovie(
                7,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        var host = new FakeS1Host(
                            (h, frame) =>
                            {
                                if (frame == 3)
                                {
                                    h.Ram[0xF600] = 0x0C;
                                    h.Ram[0xFE10] = 0x03;
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
                            + RomIdentity.Sonic3kLockOnSha1
                            + "\n"
                            + "Movie frames: 7\n"
                            + "Trace profile: gameplay_unlock\n"
                            + "BK2 frame offset: 3\n"
                            + "Trace frames: 3\n"
                            + "Physics CSV: "
                            + Path.Combine(fullOutput, "physics.csv") + "\n"
                            + "Aux state JSONL: "
                            + Path.Combine(fullOutput, "aux_state.jsonl")
                            + "\n"
                            + "Hardware timing JSONL: "
                            + Path.Combine(
                                fullOutput, "hardware_timing.jsonl")
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
                                S3KTraceCsvWriter.Header + "\n0000,"));
                        string metadata = File.ReadAllText(
                            Path.Combine(fullOutput, "metadata.json"));
                        string hardwareTimingPath = Path.Combine(
                            fullOutput, "hardware_timing.jsonl");
                        AssertContains(
                            stdout.ToString(),
                            "Hardware timing JSONL: "
                            + hardwareTimingPath + "\n");
                        AssertEx.Equal(
                            true, File.Exists(hardwareTimingPath));
                        AssertEx.Equal(
                            "aux_state.jsonl,hardware_timing.jsonl,"
                            + "metadata.json,physics.csv",
                            PublishedNames(fullOutput));
                        AssertContains(
                            metadata,
                            "  \"game\": \"s3k\",\n"
                            + "  \"zone\": \"cnz\",\n");
                        AssertContains(
                            metadata,
                            "  \"lua_script_version\": \"6.34-s3k\",\n"
                            + "  \"trace_schema\": 7,\n"
                            + "  \"hardware_timing_schema\": 1,\n");
                        AssertContains(
                            metadata,
                            "  \"trace_profile\": \"gameplay_unlock\",\n");
                        AssertContains(
                            metadata,
                            "  \"capture_mode\": \"physics_animation_aux"
                            + "_without_diagnostic_hooks\",\n");
                    }));
        }

        /// <summary>
        /// --gameplay-segment stays S2-only. --run-id is NO LONGER refused
        /// with the S3K ROM: it now selects the migrated complete-run
        /// recorder (see
        /// <see cref="S3kCompleteRunPublishesRunLayout"/>).
        /// </summary>
        private static void RejectsSegmentArgumentWithS3kRom()
        {
            S3kTraceCliDependencies dependencies = ResolveS3kDependencies();
            var rejections = new[]
            {
                new[]
                {
                    "--gameplay-segment", "1",
                    "only supported with the Sonic 2 ROM"
                }
            };
            foreach (string[] rejection in rejections)
            {
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
                                    rejection[0], rejection[1]
                                },
                                stdout,
                                stderr,
                                (romPath, syncSettings) =>
                                    new ScriptedTraceHost(-1));

                            AssertEx.Equal(1, exitCode);
                            AssertEx.Equal(
                                string.Empty, stdout.ToString());
                            AssertContains(
                                stderr.ToString(), rejection[2]);
                            AssertEx.Equal(
                                false,
                                Directory.Exists(Path.GetFullPath(output))
                                && Directory.GetFileSystemEntries(
                                    Path.GetFullPath(output)).Length > 0);
                        }));
            }
        }

        /// <summary>
        /// The Lua S3K recorder reads its whole diagnostic surface from
        /// the environment rather than from CLI flags, so "the native CLI
        /// exposes no such flag" does not stop a variable exported by an
        /// earlier Lua investigation from changing the capture. The port
        /// models none of them, so each must be a loud refusal that
        /// publishes nothing. Covered here:
        ///
        /// - the hook switch and the two variables that ARM a deferred
        ///   hook-driven aux family;
        /// - the frame-window overrides for the five poll-driven families
        ///   the port DOES implement with the Lua defaults pinned as
        ///   constants (aiz_fire_transition, terrain_wall_sensor,
        ///   collision_response_list_end_of_frame, cnz_cylinder_state,
        ///   aiz_handoff_terrain_state) — these change aux_state.jsonl
        ///   with the hook switch off, which is exactly how every gated
        ///   fixture was captured;
        /// - the two early-stop variables, which truncate physics.csv and
        ///   aux_state.jsonl.
        ///
        /// Refusal keys on non-emptiness, so a malformed value the Lua
        /// would have warned about and ignored is refused too rather than
        /// silently producing a canonical-looking file.
        /// </summary>
        private static void S3kTraceRefusesUnmodeledEnvironment()
        {
            var refusals = new[]
            {
                new[]
                {
                    "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS", "1",
                    "diagnostic"
                },
                new[]
                {
                    "OGGF_S3K_CNZ_EVENT_RAM_RANGE", "15620-15735",
                    "OGGF_S3K_CNZ_EVENT_RAM_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_RNG_CALL_RANGE", "600-700",
                    "OGGF_S3K_RNG_CALL_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_FIRE_RANGE", "100-200",
                    "OGGF_S3K_AIZ_FIRE_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_WALL_SENSOR_RANGE", "7000-7100",
                    "OGGF_S3K_AIZ_WALL_SENSOR_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_CRL_RANGE", "600-700",
                    "OGGF_S3K_CRL_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_CNZ_CYLINDER_RANGE", "4400-4600",
                    "OGGF_S3K_CNZ_CYLINDER_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START", "5000",
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END", "5500",
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END"
                },
                new[]
                {
                    "OGGF_TRACE_STOP_FRAME", "120",
                    "OGGF_TRACE_STOP_FRAME"
                },
                new[]
                {
                    "OGGF_BK2_FRAME_COUNT", "3",
                    "OGGF_BK2_FRAME_COUNT"
                },
                // A value the Lua would warn about and ignore is still an
                // operator intent to change the capture: refuse it too.
                new[]
                {
                    "OGGF_S3K_CRL_RANGE", "not-a-range",
                    "OGGF_S3K_CRL_RANGE"
                }
            };
            foreach (string[] refusal in refusals)
            {
                string stderrText = RunS3kTraceWithEnvironment(
                    refusal[0],
                    refusal[1],
                    1);
                AssertContains(stderrText, refusal[2]);
                AssertContains(stderrText, "Lua recorder");
            }
        }

        /// <summary>
        /// The refusal must stay scoped to variables that actually change
        /// output. The window overrides belonging to families the port
        /// defers entirely (position_write, solid_object_cont_entry,
        /// aiz_boundary_state, aiz_transition_floor_solid, ...) only ever
        /// widen a window whose flush is gated on a hook-populated
        /// `state.seen`/hit list, so with the hook switch off — itself a
        /// refusal — they change no byte of the Lua's own output either.
        /// Refusing them would be a false refusal, so this pins that the
        /// CLI does not name them.
        /// </summary>
        private static void S3kTraceAcceptsHookGatedWindowEnvironment()
        {
            var accepted = new[]
            {
                new[] { "OGGF_S3K_POSITION_WRITE_RANGE", "4788-4792" },
                new[] { "OGGF_S3K_VELOCITY_WRITE_RANGE", "3640-3660" },
                new[] { "OGGF_S3K_SOLID_CONT_RANGE", "7600-7625" },
                new[] { "OGGF_S3K_AIZ_SHIP_LOOP_RANGE", "16320-16335" },
                new[] { "OGGF_S3K_AIZ_BOUNDARY_RANGE", "4660-4679" },
                new[]
                {
                    "OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START", "5408"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_END", "5438"
                }
            };
            foreach (string[] entry in accepted)
            {
                // The scripted host never arms, so the capture still
                // fails (exit 1) — but it must fail on the recording, not
                // on the variable.
                string stderrText = RunS3kTraceWithEnvironment(
                    entry[0],
                    entry[1],
                    1);
                if (stderrText.Contains(entry[0]))
                {
                    throw new Exception(
                        "S3K trace refused " + entry[0] + ", which only"
                        + " retunes a hook-gated family the port defers"
                        + " and therefore cannot change output with the"
                        + " diagnostic hooks off. stderr: " + stderrText);
                }
            }
        }

        /// <summary>
        /// S3K complete-run mode selected by --run-id
        /// (s3k_complete_run_recorder.lua's OGGF_TRACE_RUN_ID): one movie
        /// pass, per-segment directories named by zone token, and
        /// run_manifest.json at the output root. The scripted host enters
        /// Game_mode 0x0C at frame 3 with Current_zone 0, so the recorder
        /// arms at frame 3 — the arm frame belongs to no segment — and
        /// records rows 4..11 before the 12-row movie's input-end guard
        /// stops it at frame 12.
        /// </summary>
        private static void S3kCompleteRunPublishesRunLayout()
        {
            S3kTraceCliDependencies dependencies = ResolveS3kDependencies();
            WithSyntheticMovie(
                12,
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
                                "--run-id", "s3k-cli-run"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) =>
                                new ScriptedTraceHost(3));

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        AssertEx.Equal(
                            "BizHawk: "
                            + dependencies.ManagedVersion + "\n"
                            + "ROM SHA-1: "
                            + RomIdentity.Sonic3kLockOnSha1 + "\n"
                            + "Movie frames: 12\n"
                            + "Run ID: s3k-cli-run\n"
                            + "Segments: 1\n"
                            + "Transitions: 0\n"
                            + "Segment aiz: kind=level, BK2 frame offset=3,"
                            + " trace frames=8\n"
                            + "Run manifest: "
                            + Path.Combine(fullOutput, "run_manifest.json")
                            + "\n",
                            stdout.ToString());

                        string segment = Path.Combine(fullOutput, "aiz");
                        string physics = File.ReadAllText(
                            Path.Combine(segment, "physics.csv"));
                        AssertEx.Equal(
                            true,
                            physics.StartsWith(
                                S3KTraceCsvWriter.Header + "\n0000,"));
                        // Publishing must never CRLF-expand an S3K path.
                        AssertEx.Equal(-1, physics.IndexOf('\r'));
                        AssertEx.Equal(
                            true,
                            File.Exists(Path.Combine(
                                segment, "aux_state.jsonl")));

                        string metadata = File.ReadAllText(
                            Path.Combine(segment, "metadata.json"));
                        AssertContains(
                            metadata,
                            "  \"game\": \"s3k\",\n"
                            + "  \"zone\": \"aiz\",\n");
                        AssertContains(
                            metadata,
                            "  \"lua_script_version\": \""
                            + S3KCompleteRunMetadataWriter.LuaScriptVersion
                            + "\",\n");
                        AssertContains(
                            metadata,
                            "  \"trace_profile\": \"complete_run\",\n"
                            + "  \"run_id\": \"s3k-cli-run\",\n"
                            + "  \"segment_index\": 0,\n");
                        AssertContains(
                            metadata,
                            "  \"capture_mode\": \"physics_animation_aux"
                            + "_without_diagnostic_hooks\",\n");
                        AssertContains(
                            metadata, "  \"trace_frame_count\": 8,\n");

                        string manifest = File.ReadAllText(
                            Path.Combine(fullOutput, "run_manifest.json"));
                        AssertContains(
                            manifest,
                            "  \"run_id\": \"s3k-cli-run\",\n");
                        AssertContains(
                            manifest,
                            "    {\"dir\": \"aiz\", \"kind\": \"level\","
                            + " \"trace_profile\": \"complete_run\","
                            + " \"bk2_frame_offset\": 3,"
                            + " \"trace_frame_count\": 8, \"zone_id\": 0,"
                            + " \"act\": 1}\n");
                        AssertContains(
                            manifest, "  \"transitions\": [\n  ]\n}\n");
                    }));
        }

        /// <summary>
        /// The identity-(A) invocation: the same recorder with no run id,
        /// selected by --trace-profile complete_run exactly as in the S1
        /// complete-run CLI. A detour-free pass therefore publishes the
        /// per-zone directories and NO manifest, and its metadata carries
        /// no run_id key.
        /// </summary>
        private static void S3kCompleteRunProfilePublishesWithoutManifest()
        {
            S3kTraceCliDependencies dependencies = ResolveS3kDependencies();
            WithSyntheticMovie(
                12,
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
                                "--trace-profile", "complete_run"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) =>
                                new ScriptedTraceHost(3));

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        AssertEx.Equal(
                            "BizHawk: "
                            + dependencies.ManagedVersion + "\n"
                            + "ROM SHA-1: "
                            + RomIdentity.Sonic3kLockOnSha1 + "\n"
                            + "Movie frames: 12\n"
                            + "Trace profile: complete_run\n"
                            + "Segments: 1\n"
                            + "Transitions: 0\n"
                            + "Segment aiz: kind=level, BK2 frame offset=3,"
                            + " trace frames=8\n",
                            stdout.ToString());
                        AssertEx.Equal(
                            false,
                            File.Exists(Path.Combine(
                                fullOutput, "run_manifest.json")));

                        string metadata = File.ReadAllText(Path.Combine(
                            fullOutput, "aiz", "metadata.json"));
                        AssertEx.Equal(
                            -1,
                            metadata.IndexOf(
                                "run_id", StringComparison.Ordinal));
                    }));
        }

        /// <summary>
        /// The complete-run recorder's own environment surface
        /// (docs/s3k-run-publication.md §8.1). It is NOT the standard
        /// recorder's table: the complete-run script hard-pins
        /// TRACE_PROFILE, which makes OGGF_S3K_AIZ_FIRE_RANGE and
        /// OGGF_S3K_RNG_CALL_RANGE unable to affect output — see
        /// <see cref="S3kCompleteRunAcceptsNonOutputEnvironment"/>, which
        /// pins their non-refusal so this guard cannot degrade into a
        /// blanket OGGF_* ban.
        /// </summary>
        private static void S3kCompleteRunRefusesUnmodeledEnvironment()
        {
            var refusals = new[]
            {
                new[]
                {
                    "OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS", "1", "diagnostic"
                },
                new[]
                {
                    "OGGF_S3K_CNZ_EVENT_RAM_RANGE", "15620-15735",
                    "OGGF_S3K_CNZ_EVENT_RAM_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_WALL_SENSOR_RANGE", "7000-7100",
                    "OGGF_S3K_AIZ_WALL_SENSOR_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START", "5000",
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_START"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END", "5500",
                    "OGGF_S3K_AIZ_HANDOFF_TERRAIN_FRAME_END"
                },
                new[]
                {
                    "OGGF_S3K_CRL_RANGE", "600-700", "OGGF_S3K_CRL_RANGE"
                },
                new[]
                {
                    "OGGF_S3K_CNZ_CYLINDER_RANGE", "4400-4600",
                    "OGGF_S3K_CNZ_CYLINDER_RANGE"
                },
                new[]
                {
                    "OGGF_TRACE_STOP_FRAME", "120", "OGGF_TRACE_STOP_FRAME"
                },
                new[]
                {
                    "OGGF_BK2_FRAME_COUNT", "3", "OGGF_BK2_FRAME_COUNT"
                },
                // A value the Lua would warn about and ignore is still an
                // operator intent to change the capture: refuse it too.
                new[]
                {
                    "OGGF_S3K_CRL_RANGE", "not-a-range",
                    "OGGF_S3K_CRL_RANGE"
                }
            };
            foreach (string[] refusal in refusals)
            {
                string stderrText = RunS3kCompleteRunWithEnvironment(
                    refusal[0], refusal[1], 1);
                AssertContains(stderrText, refusal[2]);
                AssertContains(stderrText, "Lua recorder");
            }
        }

        /// <summary>
        /// Pins the deliberate NON-refusals of the complete-run path, so
        /// the guard above can never widen into "refuse anything named
        /// OGGF_*". Three classes:
        ///
        /// - OGGF_S3K_AIZ_FIRE_RANGE and OGGF_S3K_RNG_CALL_RANGE, which
        ///   the STANDARD recorder's table DOES refuse. Under the
        ///   complete-run script's hard-pinned TRACE_PROFILE their
        ///   emitters are unreachable (V628_AIZ_FIRE.write returns on
        ///   !is_aiz_end_to_end_profile(); rng_call needs the hook
        ///   registration that only runs with the already-refused hook
        ///   switch), so refusing them would be a false refusal.
        /// - The seven window overrides for families the port defers
        ///   entirely, whose flushes early-return on hook-populated hit
        ///   lists that stay empty with the hooks off.
        /// - OGGF_TRACE_QUIET, which only replaces the Lua's `print` with
        ///   a no-op and changes no published byte, and
        ///   OGGF_TRACE_LIGHTWEIGHT, which HEAD no longer reads at all
        ///   (removed by 192d9c976) and which must not be refused merely
        ///   because it once existed.
        /// </summary>
        private static void S3kCompleteRunAcceptsNonOutputEnvironment()
        {
            var accepted = new[]
            {
                new[] { "OGGF_S3K_AIZ_FIRE_RANGE", "100-200" },
                new[] { "OGGF_S3K_RNG_CALL_RANGE", "600-700" },
                new[] { "OGGF_S3K_POSITION_WRITE_RANGE", "4788-4792" },
                new[] { "OGGF_S3K_VELOCITY_WRITE_RANGE", "3640-3660" },
                new[] { "OGGF_S3K_SOLID_CONT_RANGE", "7600-7625" },
                new[] { "OGGF_S3K_AIZ_SHIP_LOOP_RANGE", "16320-16335" },
                new[] { "OGGF_S3K_AIZ_BOUNDARY_RANGE", "4660-4679" },
                new[] { "OGGF_S3K_AIZ_BOUNDARY_FRAME_START", "4660" },
                new[] { "OGGF_S3K_AIZ_BOUNDARY_FRAME_END", "4679" },
                new[]
                {
                    "OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_START", "5408"
                },
                new[]
                {
                    "OGGF_S3K_AIZ_TRANSITION_FLOOR_FRAME_END", "5438"
                },
                new[] { "OGGF_TRACE_QUIET", "1" },
                new[] { "OGGF_TRACE_LIGHTWEIGHT", "1" }
            };
            foreach (string[] entry in accepted)
            {
                // The scripted host never arms, so the pass publishes an
                // empty manifest and exits 0 — the point is only that the
                // variable itself was not the failure.
                string stderrText = RunS3kCompleteRunWithEnvironment(
                    entry[0], entry[1], 0);
                if (stderrText.IndexOf(
                    entry[0], StringComparison.Ordinal) >= 0)
                {
                    throw new InvalidOperationException(
                        "S3K complete-run refused " + entry[0]
                        + ", which cannot change its published output."
                        + " stderr: " + stderrText);
                }
            }
        }

        /// <summary>
        /// Runs the S3K complete-run CLI once with a single environment
        /// variable set (and restored afterwards), asserting the exit code.
        /// A refusal (exit 1) must additionally leave stdout empty and
        /// publish nothing. Returns stderr.
        /// </summary>
        private static string RunS3kCompleteRunWithEnvironment(
            string variable,
            string value,
            int expectedExitCode)
        {
            S3kTraceCliDependencies dependencies = ResolveS3kDependencies();
            string captured = null;
            WithSyntheticMovie(
                4,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        Environment.SetEnvironmentVariable(
                            variable, value);
                        try
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
                                    "--run-id", "s3k-env-run"
                                },
                                stdout,
                                stderr,
                                (romPath, syncSettings) =>
                                    new ScriptedTraceHost(-1));

                            AssertEx.Equal(expectedExitCode, exitCode);
                            if (expectedExitCode != 0)
                            {
                                AssertEx.Equal(
                                    string.Empty, stdout.ToString());
                                AssertEx.Equal(
                                    false,
                                    Directory.Exists(
                                        Path.GetFullPath(output))
                                    && Directory.GetFileSystemEntries(
                                        Path.GetFullPath(output))
                                        .Length > 0);
                            }
                            captured = stderr.ToString();
                        }
                        finally
                        {
                            Environment.SetEnvironmentVariable(
                                variable, null);
                        }
                    }));
            return captured;
        }

        /// <summary>
        /// Runs the S3K trace CLI once with a single environment variable
        /// set (and restored afterwards), asserting the exit code, an
        /// empty stdout, and that nothing was published. Returns stderr.
        /// </summary>
        private static string RunS3kTraceWithEnvironment(
            string variable,
            string value,
            int expectedExitCode)
        {
            S3kTraceCliDependencies dependencies = ResolveS3kDependencies();
            string captured = null;
            WithSyntheticMovie(
                4,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        Environment.SetEnvironmentVariable(
                            variable, value);
                        try
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
                                    "--output", output
                                },
                                stdout,
                                stderr,
                                (romPath, syncSettings) =>
                                    new ScriptedTraceHost(-1));

                            AssertEx.Equal(expectedExitCode, exitCode);
                            AssertEx.Equal(
                                string.Empty, stdout.ToString());
                            AssertEx.Equal(
                                false,
                                Directory.Exists(
                                    Path.GetFullPath(output))
                                && Directory.GetFileSystemEntries(
                                    Path.GetFullPath(output))
                                    .Length > 0);
                            captured = stderr.ToString();
                        }
                        finally
                        {
                            Environment.SetEnvironmentVariable(
                                variable, null);
                        }
                    }));
            return captured;
        }

        private static S3kTraceCliDependencies ResolveS3kDependencies()
        {
            string romPath =
                Environment.GetEnvironmentVariable("S3K_ROM_PATH");
            string bizHawkHome =
                Environment.GetEnvironmentVariable("BIZHAWK_HOME");
            var missing = new List<string>();
            if (string.IsNullOrEmpty(romPath))
            {
                missing.Add("S3K_ROM_PATH is not set");
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
            RomIdentity.ValidateSonic3kLockOn(File.ReadAllBytes(romPath));
            BizHawkInstallation installation =
                BizHawkInstallation.Validate(bizHawkHome);
            return new S3kTraceCliDependencies(
                romPath,
                installation.ManagedVersion.ToString());
        }

        private sealed class S3kTraceCliDependencies
        {
            public S3kTraceCliDependencies(
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
        /// The Sonic 1 ROM accepts --run-id and --trace-profile
        /// complete_run (the complete-run recorder) but keeps rejecting
        /// the S2-only selection arguments: --gameplay-segment and any
        /// other profile string.
        /// </summary>
        private static void RejectsS2OnlyArgumentsWithSonic1Rom()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            foreach (string[] pair in new[]
            {
                new[] { "--gameplay-segment", "1" },
                new[] { "--trace-profile", "level_gated_reset_aware" }
            })
            {
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
                                    pair[0], pair[1]
                                },
                                stdout,
                                stderr,
                                (romPath, syncSettings) =>
                                    new ScriptedTraceHost(-1));

                            AssertEx.Equal(1, exitCode);
                            AssertEx.Equal(
                                string.Empty, stdout.ToString());
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
        }

        /// <summary>
        /// Stage-free complete-run schedule for a 14-row movie: arm at F=3
        /// (GHZ act raw 0), rows F=4-6, mode exit 0x8C at F=7, re-arm at
        /// F=9 (act raw 1), rows F=10-13, movie-done guard at F=14. Two
        /// level segments ghz1 (offset 3, 3 rows) and ghz2 (offset 9,
        /// 4 rows), no detour, no manifest.
        /// </summary>
        private static void CompleteRunSchedule(
            FakeS1Host host,
            int frame)
        {
            if (frame == 3)
            {
                host.Ram[0xF600] = 0x0C;
                host.Ram[0xFE10] = 0x00;
                host.Ram[0xFE11] = 0x00;
                host.SetU16(0xD008, 0x0100);
                host.SetU16(0xD00C, 0x0300);
            }
            if (frame == 7)
            {
                host.Ram[0xF600] = 0x8C;
            }
            if (frame == 9)
            {
                host.Ram[0xF600] = 0x0C;
                host.Ram[0xFE11] = 0x01;
            }
        }

        private static void S1CompleteRunPublishesLevelSegmentsWithLf()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                14,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        var host =
                            new FakeS1Host(
                                CompleteRunSchedule);
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
                                "--trace-profile", "complete_run"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) => host);

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        AssertEx.Equal(
                            "BizHawk: "
                            + dependencies.ManagedVersion
                            + "\n"
                            + "ROM SHA-1: "
                            + RomIdentity.Sonic1Rev01Sha1
                            + "\n"
                            + "Movie frames: 14\n"
                            + "Trace profile: complete_run\n"
                            + "Segments: 2\n"
                            + "Transitions: 0\n"
                            + "Segment ghz1: kind=level,"
                            + " BK2 frame offset=3, trace frames=3\n"
                            + "Segment ghz2: kind=level,"
                            + " BK2 frame offset=9, trace frames=4\n",
                            stdout.ToString());

                        string fullOutput = Path.GetFullPath(output);
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
                            "ghz1/aux_state.jsonl,"
                            + "ghz1/metadata.json,"
                            + "ghz1/physics.csv,"
                            + "ghz2/aux_state.jsonl,"
                            + "ghz2/metadata.json,"
                            + "ghz2/physics.csv",
                            string.Join(",", files));

                        // The complete-run fixture set is LF-only (spec
                        // s1-complete-run-behavior.md section 8): no CRLF
                        // expansion anywhere in this layout.
                        foreach (string file in files)
                        {
                            byte[] bytes = File.ReadAllBytes(
                                Path.Combine(fullOutput, file));
                            AssertEx.Equal(
                                false,
                                bytes.Contains((byte)'\r'));
                        }

                        string metadata = File.ReadAllText(Path.Combine(
                            fullOutput, "ghz2", "metadata.json"));
                        AssertContains(
                            metadata,
                            "  \"zone\": \"ghz\",\n  \"zone_id\": 0,\n"
                            + "  \"act\": 2,\n"
                            + "  \"bk2_frame_offset\": 9,\n"
                            + "  \"trace_frame_count\": 4,\n");
                        AssertContains(
                            metadata,
                            "  \"lua_script_version\": \""
                            + S1CompleteRunMetadataWriter.LuaScriptVersion
                            + "\",\n");
                        AssertContains(
                            metadata,
                            "  \"source_bk2\": \"synthetic.bk2\"\n}\n");
                        string physics = File.ReadAllText(Path.Combine(
                            fullOutput, "ghz1", "physics.csv"));
                        AssertEx.Equal(
                            true,
                            physics.StartsWith(
                                S1TraceCsvWriter.Header + "\n0000,"));
                    }));
        }

        /// <summary>
        /// Detour round-trip schedule for a 12-row movie: level arm at F=3,
        /// rows F=4-5; giant-ring entry at F=6 (rings 7, emeralds 0,
        /// v_lastspecial 1), ss rows F=7-8; exit + same-frame re-arm at F=9
        /// (act raw 1, carried rings 5, emerald collected), rows F=10-11;
        /// movie-done guard at F=12. Segments ghz1/ss/ghz2 with offsets
        /// 3/6/9 and 2 rows each, transitions giant_ring + stage_exit.
        /// </summary>
        private static void S1RoundTripSchedule(
            FakeS1Host host,
            int frame)
        {
            if (frame == 3)
            {
                host.Ram[0xF600] = 0x0C;
                host.Ram[0xFE10] = 0x00;
                host.Ram[0xFE11] = 0x00;
            }
            if (frame == 6)
            {
                host.Ram[0xF600] = 0x10;
                host.SetU16(0xFE20, 7);
                host.Ram[0xFE57] = 0;
                host.Ram[0xFE16] = 1;
            }
            if (frame == 9)
            {
                host.Ram[0xF600] = 0x0C;
                host.Ram[0xFE11] = 0x01;
                host.SetU16(0xFE20, 5);
                host.Ram[0xFE57] = 1;
            }
        }

        private static void S1RunModePublishesSegmentsAndManifestWithCrlf()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                12,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        var host =
                            new FakeS1Host(
                                S1RoundTripSchedule);
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
                                "--run-id", "cli-s1-run"
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
                            + RomIdentity.Sonic1Rev01Sha1
                            + "\n"
                            + "Movie frames: 12\n"
                            + "Run ID: cli-s1-run\n"
                            + "Segments: 3\n"
                            + "Transitions: 2\n"
                            + "Segment ghz1: kind=level,"
                            + " BK2 frame offset=3, trace frames=2\n"
                            + "Segment ss: kind=special_stage,"
                            + " BK2 frame offset=6, trace frames=2\n"
                            + "Segment ghz2: kind=level,"
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
                            "ghz1/aux_state.jsonl,"
                            + "ghz1/metadata.json,"
                            + "ghz1/physics.csv,"
                            + "ghz2/aux_state.jsonl,"
                            + "ghz2/metadata.json,"
                            + "ghz2/physics.csv,"
                            + "run_manifest.json,"
                            + "ss/aux_state.jsonl,"
                            + "ss/metadata.json,"
                            + "ss/physics.csv",
                            string.Join(",", files));

                        // The ss aux file exists and is byte-empty; CRLF
                        // expansion of empty content stays empty.
                        AssertEx.Equal(
                            0L,
                            new FileInfo(Path.Combine(
                                fullOutput,
                                "ss",
                                "aux_state.jsonl")).Length);
                        // Run-mode files carry the canonical capture's
                        // Windows text-mode CRLF line endings
                        // (docs/s1-run-mode-behavior.md section 9).
                        string manifest = File.ReadAllText(Path.Combine(
                            fullOutput,
                            "run_manifest.json"));
                        AssertContains(
                            manifest,
                            "  \"run_id\": \"cli-s1-run\",\r\n");
                        AssertContains(
                            manifest,
                            "  \"rom_checksum\": \"AFE05EEE\",\r\n");
                        AssertContains(
                            manifest,
                            "\"entry_kind\": \"giant_ring\","
                            + " \"mode_change_bk2_frame\": 6,"
                            + " \"rings_before\": 7,"
                            + " \"emeralds_before\": 0}");
                        // S1 level metadata is byte-identical in and out
                        // of run context: no run_id / segment_index lines
                        // (docs/s1-run-mode-behavior.md section 7).
                        string levelMetadata = File.ReadAllText(
                            Path.Combine(
                                fullOutput,
                                "ghz2",
                                "metadata.json"));
                        AssertContains(
                            levelMetadata,
                            "  \"bk2_frame_offset\": 9,\r\n");
                        AssertEx.Equal(
                            false,
                            levelMetadata.Contains("run_id"));
                        string ssMetadata = File.ReadAllText(Path.Combine(
                            fullOutput,
                            "ss",
                            "metadata.json"));
                        AssertContains(
                            ssMetadata,
                            "  \"special_stage_index\": 1,\r\n");
                        AssertContains(
                            ssMetadata,
                            "  \"run_id\": \"cli-s1-run\",\r\n");
                        AssertContains(
                            ssMetadata,
                            "  \"segment_index\": 1\r\n}\r\n");
                    }));
        }

        /// <summary>
        /// The S1 detour machine is always on (docs/s1-run-mode-behavior.md
        /// section 1): a complete_run capture whose movie enters a special
        /// stage produces the ss segment and run_manifest.json even
        /// without --run-id — only the run_id lines are absent — and the
        /// complete_run invocation keeps its LF-only encoding.
        /// </summary>
        private static void S1CompleteRunEmitsManifestForDetourWithoutRunId()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                12,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        var host =
                            new FakeS1Host(
                                S1RoundTripSchedule);
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
                                "--trace-profile", "complete_run"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) => host);

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        AssertContains(
                            stdout.ToString(),
                            "Trace profile: complete_run\n"
                            + "Segments: 3\n"
                            + "Transitions: 2\n");
                        AssertContains(
                            stdout.ToString(),
                            "Run manifest: "
                            + Path.Combine(fullOutput, "run_manifest.json")
                            + "\n");

                        byte[] manifestBytes = File.ReadAllBytes(
                            Path.Combine(fullOutput, "run_manifest.json"));
                        AssertEx.Equal(
                            false,
                            manifestBytes.Contains((byte)'\r'));
                        string manifest = Encoding.UTF8.GetString(
                            manifestBytes);
                        AssertEx.Equal(
                            false,
                            manifest.Contains("run_id"));
                        AssertContains(
                            manifest,
                            "  \"rom_checksum\": \"AFE05EEE\",\n");
                        string ssMetadata = File.ReadAllText(Path.Combine(
                            fullOutput,
                            "ss",
                            "metadata.json"));
                        AssertEx.Equal(
                            false,
                            ssMetadata.Contains("run_id"));
                    }));
        }

        private static void S1CompleteRunRefusesExistingRunManifest()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                14,
                moviePath => WithUnusedOutput(
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
                                "--trace-profile", "complete_run"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) =>
                                new FakeS1Host(CompleteRunSchedule));

                        AssertEx.Equal(1, exitCode);
                        AssertEx.Equal(string.Empty, stdout.ToString());
                        AssertContains(
                            stderr.ToString(),
                            "already exists and will not be replaced: "
                            + Path.Combine(
                                Path.GetFullPath(output),
                                "run_manifest.json"));

                        string[] files = Directory.GetFiles(
                            Path.GetFullPath(output),
                            "*",
                            SearchOption.AllDirectories);
                        AssertEx.Equal(1, files.Length);
                        AssertEx.Equal(
                            "{}\n",
                            File.ReadAllText(files[0]));
                    }));
        }

        private static void S1RunModeFailureLeavesNoPartialOutputs()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                12,
                moviePath => WithUnusedOutput(
                    output =>
                    {
                        // A competing final inside the first segment
                        // directory makes the multi-file publication fail;
                        // no staged file may survive as a final.
                        Directory.CreateDirectory(
                            Path.Combine(output, "ghz1"));
                        string competingPath = Path.Combine(
                            output,
                            "ghz1",
                            "physics.csv");
                        File.WriteAllText(
                            competingPath,
                            "competing ghz1 capture\n",
                            new UTF8Encoding(false));

                        var host =
                            new FakeS1Host(
                                S1RoundTripSchedule);
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
                                "--run-id", "cli-s1-run"
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
                            "competing ghz1 capture\n",
                            File.ReadAllText(files[0]));
                    }));
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

                    // --effective-movie-length models the capture session's
                    // movie-length signal (run-mode movie-done guard only).
                    AssertEx.Throws<ArgumentException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--effective-movie-length", "22612")),
                        "--effective-movie-length requires --run-id");
                    AssertEx.Throws<ArgumentOutOfRangeException>(
                        () => CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--run-id", "run",
                            "--effective-movie-length", "0")),
                        "--effective-movie-length must be at least 1");

                    // The S2 selection arguments are trace-mode only.
                    foreach (string[] pair in new[]
                    {
                        new[] { "--trace-profile", "gameplay_unlock" },
                        new[] { "--gameplay-segment", "1" },
                        new[] { "--run-id", "run" },
                        new[] { "--effective-movie-length", "22612" }
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
                    AssertEx.Equal(0, run.EffectiveMovieLength);

                    CommandLineOptions sessionRun =
                        CommandLineOptions.Parse(Append(
                            TraceArguments(output),
                            "--run-id", "my-run",
                            "--effective-movie-length", "22612"));
                    AssertEx.Equal(22612, sessionRun.EffectiveMovieLength);
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

                        // v9.13-s2 §11.3: the ss aux file carries the
                        // frame -1 pre-trace snapshot (all-zero SS
                        // parameter RAM here) and the first-row
                        // control_state, CRLF-expanded like every other
                        // run-mode file.
                        AssertEx.Equal(
                            "{\"frame\":-1,\"type\":\"state_snapshot\","
                            + "\"ring_requirement\":\"0x0000\","
                            + "\"current_level_layout\":\"0x00000000\","
                            + "\"initial_speed_factor\":\"0x0000\","
                            + "\"perfect_rings_left\":\"0x0000\"}\r\n"
                            + "{\"frame\":0,\"type\":\"control_state\","
                            + "\"started\":0}\r\n",
                            File.ReadAllText(Path.Combine(
                                fullOutput,
                                "ss",
                                "aux_state.jsonl")));
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

        /// <summary>
        /// Compression end to end. First: a default capture with the
        /// threshold pinned to 0 publishes both payloads as verified gzips
        /// (metadata.json is never a payload and stays plain), the
        /// uncompressed names are gone, and the report printed after
        /// publication names what landed compressed. Then --no-compress over
        /// the same capture publishes the plain names and reports nothing —
        /// the opt-out every ROM-backed gate relies on. A synthetic
        /// three-row capture is far below the 1 MiB default, which is why
        /// the threshold has to be pinned for the first half.
        /// </summary>
        private static void TraceRunPublishesCompressedPayloads()
        {
            TraceCliDependencies dependencies = ResolveDependencies();
            WithSyntheticMovie(
                7,
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
                                "--compress-threshold", "0"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) =>
                                new ScriptedTraceHost(3));

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        AssertEx.Equal(
                            "aux_state.jsonl.gz,metadata.json,"
                            + "physics.csv.gz",
                            PublishedNames(fullOutput));

                        string physics = DecompressText(Path.Combine(
                            fullOutput, "physics.csv.gz"));
                        AssertEx.Equal(
                            true,
                            physics.StartsWith(
                                S1TraceCsvWriter.Header + "\n0000,"));
                        AssertContains(
                            DecompressText(Path.Combine(
                                fullOutput, "aux_state.jsonl.gz")),
                            "\"event\":\"state_snapshot\"");
                        AssertContains(
                            stdout.ToString(),
                            "Compressed "
                            + Path.Combine(fullOutput, "physics.csv")
                            + " -> "
                            + Path.Combine(fullOutput, "physics.csv.gz")
                            + " (");
                        AssertContains(
                            stdout.ToString(),
                            "Compressed "
                            + Path.Combine(fullOutput, "aux_state.jsonl")
                            + " -> ");
                    }));

            // --no-compress publishes the plain names and reports nothing:
            // the gates capture into a temp directory, compare raw bytes and
            // never commit them.
            WithSyntheticMovie(
                7,
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
                                "--no-compress"
                            },
                            stdout,
                            stderr,
                            (romPath, syncSettings) =>
                                new ScriptedTraceHost(3));

                        AssertEx.Equal(string.Empty, stderr.ToString());
                        AssertEx.Equal(0, exitCode);

                        string fullOutput = Path.GetFullPath(output);
                        AssertEx.Equal(
                            "aux_state.jsonl,metadata.json,physics.csv",
                            PublishedNames(fullOutput));
                        AssertEx.Equal(
                            false,
                            stdout.ToString().Contains("Compressed "));
                    }));
        }

        private static string PublishedNames(string directory)
        {
            return string.Join(
                ",",
                Directory.GetFileSystemEntries(directory)
                    .Select(Path.GetFileName)
                    .OrderBy(name => name, StringComparer.Ordinal)
                    .ToArray());
        }

        private static string DecompressText(string path)
        {
            using (FileStream stream = File.OpenRead(path))
            using (var gzip = new GZipStream(
                stream,
                CompressionMode.Decompress))
            using (var reader = new StreamReader(
                gzip,
                new UTF8Encoding(false)))
            {
                return reader.ReadToEnd();
            }
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
