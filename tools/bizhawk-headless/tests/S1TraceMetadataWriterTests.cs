using System.Collections.Generic;

namespace OpenGGF.BizHawk.Headless.Tests
{
    internal static class S1TraceMetadataWriterTests
    {
        public static void Register(ICollection<TestMain.TestCase> tests)
        {
            tests.Add(new TestMain.TestCase(
                "S1TraceMetadataWriter formats canonical GHZ1 shape byte-exactly",
                FormatsCanonicalGhz1ShapeByteExactly));
            tests.Add(new TestMain.TestCase(
                "S1TraceMetadataWriter maps zone names with unknown fallback",
                MapsZoneNamesWithUnknownFallback));
            tests.Add(new TestMain.TestCase(
                "S1TraceMetadataWriter renders act plus one and hex widths",
                RendersActPlusOneAndHexWidths));
        }

        private static void FormatsCanonicalGhz1ShapeByteExactly()
        {
            string expected =
                "{\n"
                + "  \"game\": \"s1\",\n"
                + "  \"zone\": \"ghz\",\n"
                + "  \"zone_id\": 0,\n"
                + "  \"act\": 1,\n"
                + "  \"bk2_frame_offset\": 840,\n"
                + "  \"trace_frame_count\": 3905,\n"
                + "  \"start_x\": \"0x0050\",\n"
                + "  \"start_y\": \"0x03B0\",\n"
                + "  \"characters\": [\"sonic\"],\n"
                + "  \"main_character\": \"sonic\",\n"
                + "  \"sidekicks\": [],\n"
                + "  \"rng_seed\": \"0x00000000\",\n"
                + "  \"recording_date\": \"2026-07-13\",\n"
                + "  \"recorder\": \"native-bizhawk-headless\",\n"
                + "  \"recorder_version\": \"3.0\",\n"
                + "  \"trace_schema\": 5,\n"
                + "  \"aux_schema_extras\": [\"s1_obj64_state_per_frame\"],\n"
                + "  \"rom_checksum\": \"\",\n"
                + "  \"notes\": \"\"\n"
                + "}\n";

            string metadata = S1TraceMetadataWriter.Format(
                0,
                0,
                840,
                3905,
                0x0050,
                0x03B0,
                0x00000000u,
                "2026-07-13");
            AssertEx.Equal(expected, metadata);
            AssertEx.Equal(false, metadata.Contains("lua_script_version"));
            AssertEx.Equal(false, metadata.Contains("csv_version"));
            AssertEx.Equal(false, metadata.Contains("ss_csv_version"));
            AssertEx.Equal(false, metadata.Contains("hardware_timing_schema"));
            AssertEx.Equal(false, metadata.Contains("run_schema"));
        }

        private static void MapsZoneNamesWithUnknownFallback()
        {
            AssertEx.Equal("ghz", S1TraceMetadataWriter.ZoneName(0));
            AssertEx.Equal("lz", S1TraceMetadataWriter.ZoneName(1));
            AssertEx.Equal("mz", S1TraceMetadataWriter.ZoneName(2));
            AssertEx.Equal("slz", S1TraceMetadataWriter.ZoneName(3));
            AssertEx.Equal("syz", S1TraceMetadataWriter.ZoneName(4));
            AssertEx.Equal("sbz", S1TraceMetadataWriter.ZoneName(5));
            AssertEx.Equal("endz", S1TraceMetadataWriter.ZoneName(6));
            AssertEx.Equal("ss", S1TraceMetadataWriter.ZoneName(7));
            AssertEx.Equal("unknown_08", S1TraceMetadataWriter.ZoneName(8));
            AssertEx.Equal("unknown_2a", S1TraceMetadataWriter.ZoneName(0x2A));
            AssertEx.Equal("unknown_ff", S1TraceMetadataWriter.ZoneName(0xFF));
        }

        private static void RendersActPlusOneAndHexWidths()
        {
            string json = S1TraceMetadataWriter.Format(
                9,
                2,
                5,
                7,
                0x000F,
                0x1234,
                0xDEADBEEFu,
                "2020-01-02");

            AssertEx.Equal(
                true, json.Contains("  \"zone\": \"unknown_09\",\n"));
            AssertEx.Equal(true, json.Contains("  \"zone_id\": 9,\n"));
            AssertEx.Equal(true, json.Contains("  \"act\": 3,\n"));
            AssertEx.Equal(
                true, json.Contains("  \"bk2_frame_offset\": 5,\n"));
            AssertEx.Equal(
                true, json.Contains("  \"trace_frame_count\": 7,\n"));
            AssertEx.Equal(
                true, json.Contains("  \"start_x\": \"0x000F\",\n"));
            AssertEx.Equal(
                true, json.Contains("  \"start_y\": \"0x1234\",\n"));
            AssertEx.Equal(
                true, json.Contains("  \"rng_seed\": \"0xDEADBEEF\",\n"));
            AssertEx.Equal(
                true,
                json.Contains("  \"recording_date\": \"2020-01-02\",\n"));
            AssertEx.Equal(true, json.EndsWith("}\n"));
            AssertEx.Equal(false, json.Contains("\r"));
        }
    }
}
