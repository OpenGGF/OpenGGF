using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua run-mode ss metadata.json layout
    /// (tools/bizhawk/s2_trace_recorder.lua v9.13-s2, write_ss_metadata;
    /// spec s2-run-mode-behavior.md §4): a distinct shape from the
    /// level metadata — trace_profile is unconditionally "s2_special_stage",
    /// characters/sidekicks are hardcoded sonic+tails (not derived from slot
    /// presence), fresh_load is always false, and segment_index (the last
    /// key) equals the number of segments finished before this one. The Lua
    /// rewrites the file at arm, every 300 rows, and at finalize with the
    /// same values apart from trace_frame_count; only the final bytes
    /// matter, so the native port formats once at finalize. run_id is
    /// emitted only when non-null and, like the level writer's run block, is
    /// written RAW (no json_escape); source_bk2 goes through json_escape.
    /// </summary>
    public static class S2SpecialStageMetadataWriter
    {
        public static string FormatStandalone(
            int bk2FrameOffset,
            int traceFrameCount,
            string sourceBk2,
            string recordingDate,
            bool dynamicArtAudit = false)
        {
            var json = new StringBuilder(512);
            json.Append("{\n");
            json.Append("  \"game\": \"s2\",\n");
            json.Append("  \"trace_profile\": \"s2_special_stage\",\n");
            json.Append("  \"special_stage_index\": 0,\n");
            json.Append("  \"ss_csv_version\": 1,\n");
            if (dynamicArtAudit)
            {
                json.Append("  \"aux_schema_extras\": "
                    + "[\"dynamic_art_transfer_state_per_frame_v1\"],\n");
            }
            json.Append("  \"characters\": [\"sonic\", \"tails\"],\n");
            json.Append("  \"main_character\": \"sonic\",\n");
            json.Append("  \"sidekicks\": [\"tails\"],\n");
            json.Append("  \"bk2_frame_offset\": ").Append(Dec(bk2FrameOffset))
                .Append(",\n");
            json.Append("  \"trace_frame_count\": ").Append(Dec(traceFrameCount))
                .Append(",\n");
            json.Append("  \"source_bk2\": \"").Append(JsonEscape(sourceBk2))
                .Append("\",\n");
            json.Append("  \"lua_script_version\": \"1.4-s2ss-native\",\n");
            json.Append("  \"recording_date\": \"").Append(recordingDate)
                .Append("\",\n");
            json.Append("  \"bizhawk_version\": \"2.11\",\n");
            json.Append("  \"genesis_core\": \"Genplus-gx\"\n");
            json.Append("}\n");
            return json.ToString();
        }

        public static string Format(
            int specialStageIndex,
            int bk2FrameOffset,
            int traceFrameCount,
            string sourceBk2,
            string recordingDate,
            string runId,
            int segmentIndex,
            bool dynamicArtAudit = false)
        {
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }

            var json = new StringBuilder(512);
            json.Append("{\n");
            json.Append("  \"game\": \"s2\",\n");
            json.Append("  \"trace_profile\": \"s2_special_stage\",\n");
            json.Append("  \"special_stage_index\": ")
                .Append(Dec(specialStageIndex)).Append(",\n");
            json.Append("  \"ss_csv_version\": 1,\n");
            if (dynamicArtAudit)
            {
                json.Append("  \"aux_schema_extras\": "
                    + "[\"dynamic_art_transfer_state_per_frame_v1\"],\n");
            }
            json.Append("  \"characters\": [\"sonic\", \"tails\"],\n");
            json.Append("  \"main_character\": \"sonic\",\n");
            json.Append("  \"sidekicks\": [\"tails\"],\n");
            json.Append("  \"bk2_frame_offset\": ")
                .Append(Dec(bk2FrameOffset)).Append(",\n");
            json.Append("  \"trace_frame_count\": ")
                .Append(Dec(traceFrameCount)).Append(",\n");
            json.Append("  \"source_bk2\": \"")
                .Append(JsonEscape(sourceBk2)).Append("\",\n");
            json.Append("  \"lua_script_version\": \"9.13-s2\",\n");
            json.Append("  \"recording_date\": \"")
                .Append(recordingDate).Append("\",\n");
            if (runId != null)
            {
                json.Append("  \"run_id\": \"").Append(runId).Append("\",\n");
            }
            json.Append("  \"fresh_load\": false,\n");
            json.Append("  \"segment_index\": ")
                .Append(Dec(segmentIndex)).Append('\n');
            json.Append("}\n");
            return json.ToString();
        }

        /// <summary>
        /// json_escape (oggf_trace_common.lua): backslash then quote, no
        /// surrounding quotes.
        /// </summary>
        private static string JsonEscape(string value)
        {
            return value
                .Replace("\\", "\\\\")
                .Replace("\"", "\\\"");
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }
    }
}
