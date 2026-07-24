using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    /// <summary>
    /// Literal-byte tests for the S2 metadata writer against the shapes of
    /// the three canonical level fixtures (ehz1_fullrun / arz / arz2). The
    /// expected strings are the fixture bytes with lua_script_version
    /// switched to the native port's "9.12-s2" and the injected recording
    /// date — exactly the two normalizations the differential gate permits.
    /// </summary>
    internal static class S2TraceMetadataWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S2TraceMetadataWriter matches ehz1_fullrun fixture bytes",
                MatchesEhz1FullrunFixtureBytes));
            tests.Add(new TestMain.TestCase(
                "S2TraceMetadataWriter matches arz fixture bytes",
                MatchesArzFixtureBytes));
            tests.Add(new TestMain.TestCase(
                "S2TraceMetadataWriter matches arz2 fixture bytes",
                MatchesArz2FixtureBytes));
            tests.Add(new TestMain.TestCase(
                "S2TraceMetadataWriter renders sonic-alone character lists",
                RendersSonicAloneCharacterLists));
            tests.Add(new TestMain.TestCase(
                "S2TraceMetadataWriter applies MTZ alternate act shift",
                AppliesMtzAlternateActShift));
            tests.Add(new TestMain.TestCase(
                "S2TraceMetadataWriter falls back to unknown zone naming",
                FallsBackToUnknownZoneNaming));
            tests.Add(new TestMain.TestCase(
                "S2TraceMetadataWriter json-escapes profile and source",
                JsonEscapesProfileAndSource));
        }

        private static void MatchesEhz1FullrunFixtureBytes()
        {
            AssertEx.Equal(
                "{\n"
                + "  \"game\": \"s2\",\n"
                + "  \"zone\": \"ehz\",\n"
                + "  \"zone_id\": 0,\n"
                + "  \"rom_zone_id\": 0,\n"
                + "  \"act\": 1,\n"
                + "  \"gameplay_segment\": 0,\n"
                + "  \"bk2_frame_offset\": 899,\n"
                + "  \"trace_frame_count\": 5852,\n"
                + "  \"start_x\": \"0x0060\",\n"
                + "  \"start_y\": \"0x0290\",\n"
                + "  \"characters\": [\"sonic\", \"tails\"],\n"
                + "  \"main_character\": \"sonic\",\n"
                + "  \"sidekicks\": [\"tails\"],\n"
                + "  \"rng_seed\": \"0x00000000\",\n"
                + "  \"recording_date\": \"2026-07-13\",\n"
                + "  \"lua_script_version\": \"9.12-s2\",\n"
                + "  \"trace_schema\": 9,\n"
                + "  \"csv_version\": 7,\n"
                + "  \"aux_schema_extras\": "
                + "[\"cnz_slot_machine_state_per_frame\", "
                + "\"cpu_state_per_frame\"],\n"
                + "  \"trace_profile\": \"gameplay_unlock\",\n"
                + "  \"bizhawk_version\": \"2.11\",\n"
                + "  \"genesis_core\": \"Genplus-gx\",\n"
                + "  \"route\": \"ehz\",\n"
                + "  \"source_bk2\": \"s2-ehz1.bk2\",\n"
                + "  \"rom_checksum\": \"\",\n"
                + "  \"notes\": \"\"\n"
                + "}\n",
                S2TraceMetadataWriter.Format(
                    0x00,
                    0,
                    0,
                    899,
                    5852,
                    0x0060,
                    0x0290,
                    true,
                    0u,
                    "gameplay_unlock",
                    "s2-ehz1.bk2",
                    "2026-07-13"));
        }

        private static void MatchesArzFixtureBytes()
        {
            AssertEx.Equal(
                "{\n"
                + "  \"game\": \"s2\",\n"
                + "  \"zone\": \"arz\",\n"
                + "  \"zone_id\": 2,\n"
                + "  \"rom_zone_id\": 15,\n"
                + "  \"act\": 1,\n"
                + "  \"gameplay_segment\": 0,\n"
                + "  \"bk2_frame_offset\": 2752,\n"
                + "  \"trace_frame_count\": 5073,\n"
                + "  \"start_x\": \"0x0060\",\n"
                + "  \"start_y\": \"0x037E\",\n"
                + "  \"characters\": [\"sonic\", \"tails\"],\n"
                + "  \"main_character\": \"sonic\",\n"
                + "  \"sidekicks\": [\"tails\"],\n"
                + "  \"rng_seed\": \"0x00000000\",\n"
                + "  \"recording_date\": \"2026-07-13\",\n"
                + "  \"lua_script_version\": \"9.12-s2\",\n"
                + "  \"trace_schema\": 9,\n"
                + "  \"csv_version\": 7,\n"
                + "  \"aux_schema_extras\": "
                + "[\"cnz_slot_machine_state_per_frame\", "
                + "\"cpu_state_per_frame\"],\n"
                + "  \"trace_profile\": \"level_gated_reset_aware\",\n"
                + "  \"bizhawk_version\": \"2.11\",\n"
                + "  \"genesis_core\": \"Genplus-gx\",\n"
                + "  \"route\": \"arz\",\n"
                + "  \"source_bk2\": \"s2-lvl-select-ARZ.bk2\",\n"
                + "  \"rom_checksum\": \"\",\n"
                + "  \"notes\": \"\"\n"
                + "}\n",
                S2TraceMetadataWriter.Format(
                    0x0F,
                    0,
                    0,
                    2752,
                    5073,
                    0x0060,
                    0x037E,
                    true,
                    0u,
                    "level_gated_reset_aware",
                    "s2-lvl-select-ARZ.bk2",
                    "2026-07-13"));
        }

        private static void MatchesArz2FixtureBytes()
        {
            string metadata = S2TraceMetadataWriter.Format(
                0x0F,
                1,
                1,
                7998,
                7809,
                0x0060,
                0x037E,
                true,
                0u,
                "level_gated_reset_aware",
                "s2-lvl-select-ARZ.bk2",
                "2026-07-13");
            AssertEx.Equal(
                true, metadata.Contains("  \"act\": 2,\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"gameplay_segment\": 1,\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"bk2_frame_offset\": 7998,\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"trace_frame_count\": 7809,\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"zone\": \"arz\",\n"));
        }

        private static void RendersSonicAloneCharacterLists()
        {
            string metadata = S2TraceMetadataWriter.Format(
                0x00, 0, 0, 10, 20, 0, 0, false, 0u,
                "gameplay_unlock", "movie.bk2", "2026-07-13");
            AssertEx.Equal(
                true, metadata.Contains("  \"characters\": [\"sonic\"],\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"sidekicks\": [],\n"));
        }

        private static void AppliesMtzAlternateActShift()
        {
            // rom_zone_id 0x05 (MTZ alternate) renders apparent act +2, so
            // raw act 0 emits "act": 3; zone/route stay "mtz", zone_id 7.
            string metadata = S2TraceMetadataWriter.Format(
                0x05, 0, 0, 10, 20, 0, 0, true, 0u,
                "gameplay_unlock", "movie.bk2", "2026-07-13");
            AssertEx.Equal(true, metadata.Contains("  \"act\": 3,\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"zone\": \"mtz\",\n"));
            AssertEx.Equal(true, metadata.Contains("  \"zone_id\": 7,\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"rom_zone_id\": 5,\n"));
        }

        private static void FallsBackToUnknownZoneNaming()
        {
            // Unmapped zone ids: name unknown_%02x (LOWERCASE hex), engine
            // zone id passes through the raw value.
            string metadata = S2TraceMetadataWriter.Format(
                0x1A, 0, 0, 10, 20, 0, 0, true, 0u,
                "gameplay_unlock", "movie.bk2", "2026-07-13");
            AssertEx.Equal(
                true, metadata.Contains("  \"zone\": \"unknown_1a\",\n"));
            AssertEx.Equal(true, metadata.Contains("  \"zone_id\": 26,\n"));
            AssertEx.Equal(
                true, metadata.Contains("  \"route\": \"unknown_1a\",\n"));
        }

        private static void JsonEscapesProfileAndSource()
        {
            string metadata = S2TraceMetadataWriter.Format(
                0x00, 0, 0, 10, 20, 0, 0, true, 0u,
                "profile\"with\\quirks",
                "dir\\movie\".bk2",
                "2026-07-13");
            AssertEx.Equal(
                true,
                metadata.Contains(
                    "  \"trace_profile\": \"profile\\\"with\\\\quirks\",\n"));
            AssertEx.Equal(
                true,
                metadata.Contains(
                    "  \"source_bk2\": \"dir\\\\movie\\\".bk2\",\n"));
        }
    }
}
