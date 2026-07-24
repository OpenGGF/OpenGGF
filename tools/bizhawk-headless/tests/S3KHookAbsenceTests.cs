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
