using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the Lua trace recorder's metadata.json layout
    /// (tools/bizhawk/s1_trace_recorder.lua): 2-space indent, fixed key
    /// order, LF line endings, and a trailing newline after the closing
    /// brace. The recording date is injected as a string (production passes
    /// DateTime.Now formatted yyyy-MM-dd; tests pass a fixed value) — it is
    /// the only nondeterministic field.
    /// </summary>
    public static class S1TraceMetadataWriter
    {
        /// <summary>
        /// Maps u8 v_zone (0xFE10) to the recorder's zone name; unknown ids
        /// render as unknown_%02x with LOWERCASE hex.
        /// </summary>
        public static string ZoneName(int zoneId)
        {
            switch (zoneId)
            {
                case 0: return "ghz";
                case 1: return "lz";
                case 2: return "mz";
                case 3: return "slz";
                case 4: return "syz";
                case 5: return "sbz";
                case 6: return "endz";
                case 7: return "ss";
                default:
                    return "unknown_"
                        + zoneId.ToString("x2", CultureInfo.InvariantCulture);
            }
        }

        /// <summary>
        /// Formats the complete metadata.json contents, including the final
        /// newline. All start-captured values come from the detection frame's
        /// RAM. <paramref name="actRaw"/> is the raw byte at 0xFE11; the
        /// emitted "act" value is actRaw + 1.
        /// </summary>
        public static string Format(
            int zoneId,
            int actRaw,
            int bk2FrameOffset,
            int traceFrameCount,
            int startX,
            int startY,
            uint rngSeed,
            string recordingDate,
            bool loadQueueState = false)
        {
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }

            var json = new StringBuilder(512);
            json.Append("{\n");
            json.Append("  \"game\": \"s1\",\n");
            json.Append("  \"zone\": \"")
                .Append(ZoneName(zoneId)).Append("\",\n");
            json.Append("  \"zone_id\": ").Append(Dec(zoneId)).Append(",\n");
            json.Append("  \"act\": ").Append(Dec(actRaw + 1)).Append(",\n");
            json.Append("  \"bk2_frame_offset\": ")
                .Append(Dec(bk2FrameOffset)).Append(",\n");
            json.Append("  \"trace_frame_count\": ")
                .Append(Dec(traceFrameCount)).Append(",\n");
            json.Append("  \"start_x\": \"0x")
                .Append(Hex4(startX)).Append("\",\n");
            json.Append("  \"start_y\": \"0x")
                .Append(Hex4(startY)).Append("\",\n");
            json.Append("  \"characters\": [\"sonic\"],\n");
            json.Append("  \"main_character\": \"sonic\",\n");
            json.Append("  \"sidekicks\": [],\n");
            json.Append("  \"rng_seed\": \"0x")
                .Append(Hex8(rngSeed)).Append("\",\n");
            json.Append("  \"recording_date\": \"")
                .Append(recordingDate).Append("\",\n");
            json.Append("  \"lua_script_version\": \"3.5\",\n");
            json.Append("  \"trace_schema\": 4,\n");
            json.Append("  \"csv_version\": 7,\n");
            json.Append("  \"aux_schema_extras\": [\"s1_obj64_state_per_frame\"");
            if (loadQueueState)
            {
                json.Append(", \"load_queue_state_per_frame\"");
            }
            json.Append("],\n");
            json.Append("  \"rom_checksum\": \"\",\n");
            json.Append("  \"notes\": \"\"\n");
            json.Append("}\n");
            return json.ToString();
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private static string Hex4(int value)
        {
            return value.ToString("X4", CultureInfo.InvariantCulture);
        }

        private static string Hex8(uint value)
        {
            return value.ToString("X8", CultureInfo.InvariantCulture);
        }
    }
}
