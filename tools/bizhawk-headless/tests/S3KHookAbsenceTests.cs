using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Unit-proof of the Stage C no-op decision for S3K exec/memwrite hook
    /// support (docs/s3k-profiles-and-hooks.md §2.4, docs/s3k-aux-events.md
    /// §5): every hook-driven aux event family of the S3K standard Lua
    /// recorder is provably absent from all three gated fixtures because
    /// they were captured with OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS unset
    /// (lightweight mode). The native port therefore defers the GpgxHost
    /// M68K exec/mem callback surface instead of implementing it.
    ///
    /// These gates pin that decision to the fixture bytes:
    /// - zero lines whose "event" value is any of the 13 deferred
    ///   hook/env-armed families, per fixture;
    /// - non-vacuous anchors (per-frame poll family counts equal the
    ///   fixture row count; exactly one cpu_state_snapshot) so an empty or
    ///   mis-located stream cannot pass silently;
    /// - the hybrid aiz_handoff_terrain_state family emits exactly its 9
    ///   in-window AIZ skeleton events with the hook-fed fields pinned to
    ///   the lightweight defaults (sonic_floor_seen:false,
    ///   solid_vertical_seen:false);
    /// - each metadata.json carries the lightweight capture_mode line.
    ///
    /// If a fixture is ever regenerated with diagnostic hooks enabled,
    /// these tests fail — the signal that native exec-hook capture must
    /// then be implemented rather than deferred. Fixtures are checked in,
    /// so a missing file is a hard failure, not a skip.
    ///
    /// COMPLETE-RUN EXTENSION (Stage B, spec
    /// docs/s3k-completerun-profiles.md §6). The complete-run recorder's
    /// canonical fixtures split into three sets and the deferral decision
    /// is NOT uniform across them, so the split is asserted here rather
    /// than left implicit:
    ///
    /// - (A) the seven <c>*_completerun</c> dirs and (C) the four
    ///   <c>bonus_*</c> / <c>special_stage</c> dirs were captured with
    ///   hooks OFF on 2026-07-23. They carry the lightweight capture_mode
    ///   line, contain zero hook-driven events, and are the byte-exact
    ///   differential target. The absence gate above is extended to all
    ///   eleven of them, with per-fixture non-vacuous anchors including
    ///   the complete-run-only game_paused_state family.
    /// - (B) <c>runs/s3-knux-multibonus-ss/</c> USED to be the one
    ///   committed set captured with hooks ON: four of its 25 segments —
    ///   hcz_2, hcz_6, mgz, mgz_3 — carried position_write /
    ///   velocity_write / solid_object_cont_entry events, and this file
    ///   pinned them in the OPPOSITE direction so the absence gate could
    ///   never silently widen to cover a fixture that would require native
    ///   exec callbacks. Commit 63eccd290 re-captured
    ///   that run on Linux at Lua 6.32 with the hooks off, so no committed
    ///   S3K fixture needs exec callbacks any more and the counter-gate
    ///   had no subject left. Those exact four segments are therefore
    ///   carried on the ABSENCE side instead — they are the ones that
    ///   would regress first if a future regeneration ever re-armed the
    ///   hooks, so gating them is what keeps the deferral honest.
    /// </summary>
    internal static class S3KHookAbsenceTests
    {
        private const string LightweightCaptureModeLine =
            "\"capture_mode\": "
            + "\"physics_animation_aux_without_diagnostic_hooks\"";

        /// <summary>
        /// The hook-driven families (all requiring
        /// OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1) plus the env-armed
        /// polled cnz_event_ram family — the exact deferred set of
        /// docs/s3k-aux-events.md §3.22/§5. Note
        /// collision_response_list_per_frame is deferred while its poll
        /// sibling collision_response_list_end_of_frame is expected.
        /// </summary>
        private static readonly string[] DeferredFamilies =
        {
            "tails_cpu_normal_step",
            "aiz_boundary_state",
            "aiz_transition_floor_solid",
            "cage_execution",
            "velocity_write",
            "position_write",
            "aiz_ship_loop",
            "sonic_record_pos",
            "rng_call",
            "cnz_cylinder_execution",
            "solid_object_cont_entry",
            "collision_response_list_per_frame",
            "cnz_event_ram"
        };

        private sealed class GatedFixture
        {
            public string CaseName;
            public string FixtureDirectoryName;
            public int TraceFrameCount;
            public int AizHandoffSkeletonCount;
        }

        private static readonly GatedFixture Aiz = new GatedFixture
        {
            CaseName = "AIZ fullrun",
            FixtureDirectoryName = "aiz1_to_hcz_fullrun",
            TraceFrameCount = 20798,
            AizHandoffSkeletonCount = 9
        };

        private static readonly GatedFixture Cnz = new GatedFixture
        {
            CaseName = "CNZ",
            FixtureDirectoryName = "cnz",
            TraceFrameCount = 42253,
            AizHandoffSkeletonCount = 0
        };

        private static readonly GatedFixture Mgz = new GatedFixture
        {
            CaseName = "MGZ",
            FixtureDirectoryName = "mgz",
            TraceFrameCount = 35912,
            AizHandoffSkeletonCount = 0
        };

        /// <summary>
        /// A complete-run (A)/(C) fixture: hooks off, byte-exact target.
        /// <see cref="QueueOnlyAux"/> distinguishes the s3k_special_stage
        /// segment, which emits only physical queue-state audit events and
        /// whose separate metadata writer emits neither capture_mode nor
        /// v_int_run_count.
        /// </summary>
        private sealed class CompleteRunFixture
        {
            public string CaseName;
            public string FixtureDirectoryName;
            public int TraceFrameCount;
            public int AizHandoffSkeletonCount;
            public bool QueueOnlyAux;
            public string PhysicsHeader;
        }

        private static CompleteRunFixture Level(
            string caseName,
            string directoryName,
            int rows,
            int aizHandoff)
        {
            return new CompleteRunFixture
            {
                CaseName = caseName,
                FixtureDirectoryName = directoryName,
                TraceFrameCount = rows,
                AizHandoffSkeletonCount = aizHandoff,
                QueueOnlyAux = false,
                PhysicsHeader = S3KTraceCsvWriter.Header
            };
        }

        /// <summary>A segment dir inside the identity-(B) run tree.</summary>
        private static string RunSegment(string dirToken)
        {
            return Path.Combine(
                "runs", "s3-knux-multibonus-ss", dirToken);
        }

        /// <summary>
        /// (A) the seven complete-run level segments, (C) the three
        /// standalone bonus segments and the standalone special stage, and
        /// the four (B) run segments that were hook-bearing before the run
        /// was re-captured. Row counts and the AIZ skeleton count are the
        /// fixtures' own measured values.
        /// </summary>
        private static readonly CompleteRunFixture[] CompleteRunFixtures =
        {
            Level("aiz_completerun", "aiz_completerun", 26228, 9),
            Level("hcz_completerun", "hcz_completerun", 31482, 0),
            Level("mgz_completerun", "mgz_completerun", 39398, 0),
            Level("cnz_completerun", "cnz_completerun", 40064, 0),
            Level("icz_completerun", "icz_completerun", 25393, 0),
            Level("lbz_completerun", "lbz_completerun", 46244, 0),
            Level("mhz_completerun", "mhz_completerun", 28156, 0),
            Level("bonus_gumball", "bonus_gumball", 1430, 0),
            Level("bonus_slots", "bonus_slots", 1200, 0),
            Level("bonus_pachinko", "bonus_pachinko", 3051, 0),
            // The four (B) run segments that carried hook-driven families
            // before 63eccd290 re-captured the run with the hooks off.
            // Gating these four is what the deleted counter-gate's
            // guarantee becomes: if a regeneration re-arms the hooks,
            // these fail first and by name.
            Level("run hcz_2", RunSegment("hcz_2"), 11933, 0),
            Level("run hcz_6", RunSegment("hcz_6"), 8422, 0),
            Level("run mgz", RunSegment("mgz"), 8721, 0),
            Level("run mgz_3", RunSegment("mgz_3"), 8517, 0),
            new CompleteRunFixture
            {
                CaseName = "special_stage",
                FixtureDirectoryName = "special_stage",
                TraceFrameCount = 4630,
                AizHandoffSkeletonCount = 0,
                QueueOnlyAux = true,
                PhysicsHeader = S3KSpecialStageCsvWriter.Header
            }
        };

        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S3KHookAbsence AIZ fixture aux stream has no hook-driven"
                + " events",
                () => FixtureHasNoHookDrivenEvents(Aiz)));
            tests.Add(new TestMain.TestCase(
                "S3KHookAbsence CNZ fixture aux stream has no hook-driven"
                + " events",
                () => FixtureHasNoHookDrivenEvents(Cnz)));
            tests.Add(new TestMain.TestCase(
                "S3KHookAbsence MGZ fixture aux stream has no hook-driven"
                + " events",
                () => FixtureHasNoHookDrivenEvents(Mgz)));

            foreach (CompleteRunFixture fixture in CompleteRunFixtures)
            {
                CompleteRunFixture captured = fixture;
                tests.Add(new TestMain.TestCase(
                    "S3KHookAbsence complete-run fixture "
                    + captured.CaseName
                    + " aux stream has no hook-driven events",
                    () => CompleteRunFixtureHasNoHookDrivenEvents(captured)));
            }
        }

        private static void FixtureHasNoHookDrivenEvents(
            GatedFixture fixture)
        {
            string fixtureDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k",
                fixture.FixtureDirectoryName);
            if (!Directory.Exists(fixtureDirectory))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture directory missing: "
                    + fixtureDirectory);
            }

            AssertLightweightCaptureMode(
                Path.Combine(fixtureDirectory, "metadata.json"));

            var deferred = new HashSet<string>(
                DeferredFamilies, StringComparer.Ordinal);
            var counts = new Dictionary<string, long>(StringComparer.Ordinal);
            long totalLines = 0;
            long aizHandoffSkeletonLines = 0;
            string auxGzipPath = Path.Combine(
                fixtureDirectory, "aux_state.jsonl.gz");
            if (!File.Exists(auxGzipPath))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture aux stream missing: "
                    + auxGzipPath);
            }

            using (FileStream compressed = File.OpenRead(auxGzipPath))
            using (var gzip = new GZipStream(
                compressed, CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    totalLines++;
                    string eventName = ExtractEventName(line, totalLines);
                    if (deferred.Contains(eventName))
                    {
                        throw new InvalidOperationException(
                            fixture.CaseName
                            + " fixture contains deferred hook-driven"
                            + " event \"" + eventName + "\" at aux line "
                            + totalLines
                            + " — the fixture was regenerated with"
                            + " diagnostic hooks enabled; native"
                            + " exec-hook capture can no longer be"
                            + " deferred.");
                    }

                    long count;
                    counts.TryGetValue(eventName, out count);
                    counts[eventName] = count + 1;

                    if (eventName == "aiz_handoff_terrain_state"
                        && line.IndexOf(
                            "\"sonic_floor_seen\":false",
                            StringComparison.Ordinal) >= 0
                        && line.IndexOf(
                            "\"solid_vertical_seen\":false",
                            StringComparison.Ordinal) >= 0)
                    {
                        aizHandoffSkeletonLines++;
                    }
                }
            }

            // Non-vacuous anchors: the unconditional per-frame poll
            // families must match the fixture row count exactly, and the
            // pre-trace snapshot appears exactly once. A truncated,
            // empty, or mis-located stream cannot satisfy these.
            AssertEx.Equal(
                (long)fixture.TraceFrameCount, CountOf(counts, "cpu_state"));
            AssertEx.Equal(
                (long)fixture.TraceFrameCount,
                CountOf(counts, "oscillation_state"));
            AssertEx.Equal(1L, CountOf(counts, "cpu_state_snapshot"));

            // The hybrid family stays a lightweight skeleton: every
            // occurrence carries hook fields pinned false, and the AIZ
            // fixture has exactly its 9 in-window frames (0 elsewhere).
            AssertEx.Equal(
                (long)fixture.AizHandoffSkeletonCount,
                CountOf(counts, "aiz_handoff_terrain_state"));
            AssertEx.Equal(
                (long)fixture.AizHandoffSkeletonCount,
                aizHandoffSkeletonLines);
        }

        /// <summary>
        /// The (A)/(C) gate. Same absence proof as the standard-recorder
        /// fixtures, plus the complete-run-only anchors: the
        /// game_paused_state family must appear exactly once per row, and
        /// the physics.csv header must be the profile's own header (42
        /// columns for complete_run / s3k_bonus_stage, 20 for
        /// s3k_special_stage) so a profile mix-up cannot pass.
        /// </summary>
        private static void CompleteRunFixtureHasNoHookDrivenEvents(
            CompleteRunFixture fixture)
        {
            string fixtureDirectory = FixtureDirectory(
                fixture.FixtureDirectoryName);

            AssertEx.Equal(
                fixture.PhysicsHeader,
                ReadFirstGzipLine(
                    Path.Combine(fixtureDirectory, "physics.csv.gz")));

            if (fixture.QueueOnlyAux)
            {
                // Special-stage rows now carry the physical direct/module
                // queue audit, but still never flush hook-driven families.
                var queueCounts =
                    new Dictionary<string, long>(StringComparer.Ordinal);
                CountAuxLines(
                    fixtureDirectory, queueCounts, fixture.CaseName);
                AssertEx.Equal(
                    2L * fixture.TraceFrameCount,
                    CountOf(queueCounts, "load_queue_state"));
                return;
            }

            AssertLightweightCaptureMode(
                Path.Combine(fixtureDirectory, "metadata.json"));

            var counts = new Dictionary<string, long>(StringComparer.Ordinal);
            long aizHandoffSkeletonLines = 0;
            CountAuxLines(fixtureDirectory, counts, fixture.CaseName);

            AssertEx.Equal(
                (long)fixture.TraceFrameCount, CountOf(counts, "cpu_state"));
            AssertEx.Equal(
                (long)fixture.TraceFrameCount,
                CountOf(counts, "oscillation_state"));
            // The one aux family the complete-run recorder adds over the
            // standard recorder: unconditional, exactly 1 per recorded row.
            AssertEx.Equal(
                (long)fixture.TraceFrameCount,
                CountOf(counts, "game_paused_state"));
            AssertEx.Equal(1L, CountOf(counts, "cpu_state_snapshot"));

            aizHandoffSkeletonLines =
                CountOf(counts, "aiz_handoff_terrain_state_skeleton");
            AssertEx.Equal(
                (long)fixture.AizHandoffSkeletonCount,
                CountOf(counts, "aiz_handoff_terrain_state"));
            AssertEx.Equal(
                (long)fixture.AizHandoffSkeletonCount,
                aizHandoffSkeletonLines);
        }

        private static string FixtureDirectory(string relativeName)
        {
            string fixtureDirectory = Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src",
                "test",
                "resources",
                "traces",
                "s3k",
                relativeName);
            if (!Directory.Exists(fixtureDirectory))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture directory missing: "
                    + fixtureDirectory);
            }
            return fixtureDirectory;
        }

        /// <summary>
        /// Streams a fixture's gunzipped aux_state.jsonl, tallying event
        /// names into <paramref name="counts"/> (when non-null) and
        /// throwing on any deferred hook family when
        /// <paramref name="absenceCaseName"/> is non-null — pass null to
        /// tally without rejecting, which is what the empty special-stage
        /// stream's total-line check needs. Lines are CR-stripped
        /// defensively; no committed S3K fixture is CRLF since 63eccd290
        /// re-captured the last one that was. The synthetic
        /// "aiz_handoff_terrain_state_skeleton" key counts in-window
        /// events whose hook-fed fields are at their lightweight defaults.
        /// Returns the total line count.
        /// </summary>
        private static long CountAuxLines(
            string fixtureDirectory,
            Dictionary<string, long> counts,
            string absenceCaseName)
        {
            var deferred = new HashSet<string>(
                DeferredFamilies, StringComparer.Ordinal);
            string auxGzipPath = Path.Combine(
                fixtureDirectory, "aux_state.jsonl.gz");
            if (!File.Exists(auxGzipPath))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture aux stream missing: "
                    + auxGzipPath);
            }

            long totalLines = 0;
            using (FileStream compressed = File.OpenRead(auxGzipPath))
            using (var gzip = new GZipStream(
                compressed, CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip))
            {
                string line;
                while ((line = reader.ReadLine()) != null)
                {
                    line = line.TrimEnd('\r');
                    totalLines++;
                    string eventName = ExtractEventName(line, totalLines);
                    if (absenceCaseName != null
                        && counts != null
                        && deferred.Contains(eventName))
                    {
                        throw new InvalidOperationException(
                            absenceCaseName
                            + " fixture contains deferred hook-driven"
                            + " event \"" + eventName + "\" at aux line "
                            + totalLines
                            + " — the fixture was regenerated with"
                            + " diagnostic hooks enabled; native"
                            + " exec-hook capture can no longer be"
                            + " deferred.");
                    }

                    if (counts == null)
                    {
                        continue;
                    }

                    long count;
                    counts.TryGetValue(eventName, out count);
                    counts[eventName] = count + 1;

                    if (eventName == "aiz_handoff_terrain_state"
                        && line.IndexOf(
                            "\"sonic_floor_seen\":false",
                            StringComparison.Ordinal) >= 0
                        && line.IndexOf(
                            "\"solid_vertical_seen\":false",
                            StringComparison.Ordinal) >= 0)
                    {
                        const string key =
                            "aiz_handoff_terrain_state_skeleton";
                        long skeleton;
                        counts.TryGetValue(key, out skeleton);
                        counts[key] = skeleton + 1;
                    }
                }
            }
            return totalLines;
        }

        private static string ReadFirstGzipLine(string gzipPath)
        {
            if (!File.Exists(gzipPath))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture payload missing: " + gzipPath);
            }
            using (FileStream compressed = File.OpenRead(gzipPath))
            using (var gzip = new GZipStream(
                compressed, CompressionMode.Decompress))
            using (var reader = new StreamReader(gzip))
            {
                string line = reader.ReadLine();
                return line == null ? null : line.TrimEnd('\r');
            }
        }

        private static void AssertLightweightCaptureMode(
            string metadataPath)
        {
            if (!File.Exists(metadataPath))
            {
                throw new InvalidOperationException(
                    "Checked-in S3K fixture metadata missing: "
                    + metadataPath);
            }

            string metadata = File.ReadAllText(metadataPath);
            if (metadata.IndexOf(
                LightweightCaptureModeLine, StringComparison.Ordinal) < 0)
            {
                throw new InvalidOperationException(
                    "Fixture metadata lacks the lightweight capture_mode"
                    + " line (" + LightweightCaptureModeLine + "): "
                    + metadataPath
                    + " — cannot prove diagnostic hooks were off at"
                    + " capture.");
            }
        }

        private static string ExtractEventName(string line, long lineNumber)
        {
            const string marker = "\"event\":\"";
            int start = line.IndexOf(marker, StringComparison.Ordinal);
            if (start < 0)
            {
                throw new InvalidOperationException(
                    "Aux line " + lineNumber
                    + " has no \"event\" field: " + line);
            }

            start += marker.Length;
            int end = line.IndexOf('"', start);
            if (end < 0)
            {
                throw new InvalidOperationException(
                    "Aux line " + lineNumber
                    + " has an unterminated \"event\" value: " + line);
            }

            return line.Substring(start, end - start);
        }

        private static long CountOf(
            Dictionary<string, long> counts, string eventName)
        {
            long count;
            counts.TryGetValue(eventName, out count);
            return count;
        }
    }
}
