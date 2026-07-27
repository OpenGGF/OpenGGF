using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S1 complete-run recorder's ss metadata.json
    /// layout (tools/bizhawk/s1_complete_run_recorder.lua, write_ss_metadata
    /// L583-605; spec s1-run-mode-behavior.md §4): a distinct shape from the
    /// level metadata — trace_profile is unconditionally "s1_special_stage",
    /// characters/main_character/sidekicks are the hardcoded solo-Sonic
    /// values, fresh_load is always false, and segment_index (the last key)
    /// equals the number of segments finished before this one. The Lua
    /// rewrites the file at arm, every 300 rows, and at finalize with the
    /// same values apart from trace_frame_count; only the final bytes
    /// matter, so the native port formats once at finalize. source_bk2 is
    /// rendered with Lua %q (not json_escape); the run_id line is emitted
    /// only when non-null and is written RAW (plain concatenation, no
    /// escaping). lua_script_version is a capture-session input for the same
    /// reason as in <see cref="S1RunManifestWriter"/>: the canonical
    /// fixtures stamp "3.15" while the current Lua stamps "3.18", with no
    /// other output-affecting delta (spec §10).
    /// </summary>
    public static class S1SpecialStageMetadataWriter
    {
        public static string Format(
            int specialStageIndex,
            int bk2FrameOffset,
            int traceFrameCount,
            string sourceBk2,
            string luaScriptVersion,
            string recordingDate,
            string runId,
            int segmentIndex)
        {
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (luaScriptVersion == null)
            {
                throw new ArgumentNullException("luaScriptVersion");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }

            var json = new StringBuilder(512);
            json.Append("{\n");
            json.Append("  \"game\": \"s1\",\n");
            json.Append("  \"trace_profile\": \"s1_special_stage\",\n");
            json.Append("  \"special_stage_index\": ")
                .Append(Dec(specialStageIndex)).Append(",\n");
            json.Append("  \"ss_csv_version\": 1,\n");
            json.Append("  \"characters\": [\"sonic\"],\n");
            json.Append("  \"main_character\": \"sonic\",\n");
            json.Append("  \"sidekicks\": [],\n");
            json.Append("  \"bk2_frame_offset\": ")
                .Append(Dec(bk2FrameOffset)).Append(",\n");
            json.Append("  \"trace_frame_count\": ")
                .Append(Dec(traceFrameCount)).Append(",\n");
            json.Append("  \"source_bk2\": ")
                .Append(RunManifestWriter.LuaQ(sourceBk2)).Append(",\n");
            json.Append("  \"lua_script_version\": \"")
                .Append(luaScriptVersion).Append("\",\n");
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

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }
    }
}
