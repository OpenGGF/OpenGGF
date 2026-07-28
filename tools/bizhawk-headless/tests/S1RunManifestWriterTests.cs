using System.Collections.Generic;
using System.IO;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Literal-byte tests for the S1 run_manifest.json: reconstructing the
    /// canonical s1-ghz-maze-roundtrip manifest from its recorded values
    /// must reproduce the recorder-regenerated 3.18 fixture exactly,
    /// including its level endpoint. The fixture is stored using the run
    /// bundle's CRLF convention, so its line endings are normalized to the
    /// Lua's written LF before comparison — the only transformation. Also
    /// covers the S1-only optional run_id line.
    /// </summary>
    internal static class S1RunManifestWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1RunManifestWriter reproduces the canonical fixture bytes",
                ReproducesCanonicalFixtureBytes));
            tests.Add(new TestMain.TestCase(
                "S1RunManifestWriter omits the run_id line when no run id"
                + " was set",
                OmitsRunIdLineWhenNoRunIdWasSet));
            tests.Add(new TestMain.TestCase(
                "S1RunManifestWriter emits the level movie endpoint",
                EmitsLevelMovieEndpoint));
            tests.Add(new TestMain.TestCase(
                "S1RunManifestWriter emits the title-screen movie endpoint",
                EmitsTitleScreenMovieEndpoint));
            tests.Add(new TestMain.TestCase(
                "S1RunManifestWriter omits an unspecified movie endpoint",
                OmitsUnspecifiedMovieEndpoint));
        }

        private static void ReproducesCanonicalFixtureBytes()
        {
            var segments = new List<RunManifestSegment>
            {
                new RunManifestSegment(
                    "ghz1", "level", "complete_run",
                    774, 4182, 0, 1, null),
                new RunManifestSegment(
                    "ss", "special_stage", "s1_special_stage",
                    4957, 3091, 0, 0, 0),
                new RunManifestSegment(
                    "ghz2", "level", "complete_run",
                    8049, 812, 0, 2, null)
            };
            var giantRing = new RunManifestTransition(
                0, 1, "giant_ring", 4957);
            giantRing.RingsBefore = 85;
            giantRing.EmeraldsBefore = 0;   // Lua-truthy zero still renders.
            var stageExit = new RunManifestTransition(
                1, 2, "stage_exit", 8049);
            stageExit.RingsAfter = 67;      // S1 carries rings through SS.
            stageExit.EmeraldsAfter = 1;

            string produced = S1RunManifestWriter.Format(
                "s1-ghz-maze-roundtrip",
                "s1-ghz-maze-roundtrip.bk2",
                "3.18",
                "level",
                segments,
                new List<RunManifestTransition> { giantRing, stageExit });

            // Ground truth: the committed fixture manifest. The capture ran
            // on Windows EmuHawk whose text-mode io expanded the Lua's "\n"
            // to CRLF; the native writer emits the written LF, so CRLF
            // normalization is the single permitted difference here.
            string fixture = File.ReadAllText(Path.Combine(
                EndToEndTests.RepositoryRoot,
                "src", "test", "resources", "traces", "s1", "runs",
                "s1-ghz-maze-roundtrip", "run_manifest.json"));
            AssertEx.Equal(fixture.Replace("\r\n", "\n"), produced);
        }

        private static void OmitsRunIdLineWhenNoRunIdWasSet()
        {
            // A detour without OGGF_TRACE_RUN_ID still emits the manifest
            // (any transition forces it), just without the run_id line.
            var exit = new RunManifestTransition(1, 2, "stage_exit", 500);
            exit.RingsAfter = 0;
            exit.EmeraldsAfter = 0;
            string produced = S1RunManifestWriter.Format(
                null,
                "movie.bk2",
                "3.18",
                null,
                new List<RunManifestSegment>
                {
                    new RunManifestSegment(
                        "ghz1", "level", "complete_run", 10, 20, 0, 1, null)
                },
                new List<RunManifestTransition> { exit });
            AssertEx.Equal(false, produced.Contains("run_id"));
            AssertEx.Equal(
                true,
                produced.Contains(
                    "  \"game\": \"s1\",\n"
                    + "  \"source_bk2\": \"movie.bk2\",\n"
                    + "  \"rom_checksum\": \"AFE05EEE\",\n"
                    + "  \"lua_script_version\": \"3.18\",\n"));
            // Zero-valued recorded fields still render (Lua truthiness).
            AssertEx.Equal(
                true,
                produced.Contains(
                    " \"rings_after\": 0, \"emeralds_after\": 0}"));
        }

        private static void EmitsLevelMovieEndpoint()
        {
            string produced = S1RunManifestWriter.Format(
                "run",
                "movie.bk2",
                "3.18",
                "level",
                OneLevelSegment(),
                new List<RunManifestTransition>());
            AssertEx.Equal(
                true,
                produced.Contains(
                    "  \"lua_script_version\": \"3.18\",\n"
                    + "  \"expected_movie_end_mode\": \"level\",\n"
                    + "  \"segments\": [\n"));
        }

        private static void EmitsTitleScreenMovieEndpoint()
        {
            string produced = S1RunManifestWriter.Format(
                "run",
                "movie.bk2",
                "3.18",
                "title_screen",
                OneLevelSegment(),
                new List<RunManifestTransition>());
            AssertEx.Equal(
                true,
                produced.Contains(
                    "  \"expected_movie_end_mode\": \"title_screen\",\n"));
        }

        private static void OmitsUnspecifiedMovieEndpoint()
        {
            string produced = S1RunManifestWriter.Format(
                "run",
                "movie.bk2",
                "3.18",
                null,
                OneLevelSegment(),
                new List<RunManifestTransition>());
            AssertEx.Equal(
                false,
                produced.Contains("expected_movie_end_mode"));
        }

        private static IList<RunManifestSegment> OneLevelSegment()
        {
            return new List<RunManifestSegment>
            {
                new RunManifestSegment(
                    "ghz1", "level", "complete_run",
                    10, 20, 0, 1, null)
            };
        }
    }
}
