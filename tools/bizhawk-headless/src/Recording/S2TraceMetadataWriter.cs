using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S2 Lua trace recorder's strict-v5 metadata.json layout
    /// (tools/bizhawk/s2_trace_recorder.lua;
    /// spec tools/bizhawk-headless/docs/s2-trace-recorder-behavior.md §8):
    /// 2-space indent, fixed key order, LF line endings, and a trailing
    /// newline after the closing brace. The Lua rewrites the file at arm,
    /// every 300 recorded frames, and at finalize; only the final bytes
    /// matter, so the native port formats once at finish. The recording
    /// date is injected (production passes DateTime.Now as yyyy-MM-dd;
    /// tests pass a fixed value) — it is the only nondeterministic field.
    /// Removed script-version fields are never emitted by the strict-v5
    /// writer.
    /// </summary>
    public static class S2TraceMetadataWriter
    {
        /// <summary>
        /// Formats the complete metadata.json contents, including the final
        /// newline. All start-captured values come from the detection
        /// frame's RAM. <paramref name="actRaw"/> is the raw byte at 0xFE11;
        /// the emitted "act" is apparent_act_for(romZoneId, actRaw) + 1
        /// (the MTZ alternate zone id 0x05 shifts acts by +2).
        /// <paramref name="sidekickPresent"/> is the recorder's sidekick
        /// rule: sticky recorded_sidekick_present OR a non-zero id byte at
        /// 0xB040 at write time. <paramref name="traceProfile"/> and
        /// <paramref name="sourceBk2"/> are json-escaped; the zone/route
        /// names are written raw.
        /// </summary>
        public static string Format(
            int romZoneId,
            int actRaw,
            int gameplaySegment,
            int bk2FrameOffset,
            int traceFrameCount,
            int startX,
            int startY,
            bool sidekickPresent,
            uint rngSeed,
            string traceProfile,
            string sourceBk2,
            string recordingDate,
            bool loadQueueState = false,
            bool nativePreludeBootstrap = false)
        {
            return Format(
                romZoneId,
                actRaw,
                gameplaySegment,
                bk2FrameOffset,
                traceFrameCount,
                startX,
                startY,
                sidekickPresent,
                rngSeed,
                traceProfile,
                sourceBk2,
                recordingDate,
                null,
                0,
                loadQueueState,
                nativePreludeBootstrap);
        }

        /// <summary>
        /// Run-mode overload (s2-run-mode-behavior.md §7): when
        /// <paramref name="runId"/> is non-null, a two-line block
        /// <c>"run_id"</c> / <c>"segment_index"</c> is inserted between
        /// <c>source_bk2</c> and <c>rom_checksum</c>. The Lua writes the
        /// run_id value RAW (no json_escape — L578), and segment_index is
        /// the number of segments finished before this one (#segments_done
        /// at write time). A null run id reproduces plain-mode bytes.
        /// </summary>
        public static string Format(
            int romZoneId,
            int actRaw,
            int gameplaySegment,
            int bk2FrameOffset,
            int traceFrameCount,
            int startX,
            int startY,
            bool sidekickPresent,
            uint rngSeed,
            string traceProfile,
            string sourceBk2,
            string recordingDate,
            string runId,
            int segmentIndex,
            bool loadQueueState = false,
            bool nativePreludeBootstrap = false)
        {
            if (traceProfile == null)
            {
                throw new ArgumentNullException("traceProfile");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }

            string zoneName = S2Zones.ZoneName(romZoneId);
            int act = S2Zones.ApparentAct(romZoneId, actRaw) + 1;

            var json = new StringBuilder(768);
            json.Append("{\n");
            json.Append("  \"game\": \"s2\",\n");
            json.Append("  \"zone\": \"").Append(zoneName).Append("\",\n");
            json.Append("  \"zone_id\": ")
                .Append(Dec(S2Zones.EngineZoneId(romZoneId))).Append(",\n");
            json.Append("  \"rom_zone_id\": ")
                .Append(Dec(romZoneId)).Append(",\n");
            json.Append("  \"act\": ").Append(Dec(act)).Append(",\n");
            json.Append("  \"gameplay_segment\": ")
                .Append(Dec(gameplaySegment)).Append(",\n");
            json.Append("  \"bk2_frame_offset\": ")
                .Append(Dec(bk2FrameOffset)).Append(",\n");
            json.Append("  \"trace_frame_count\": ")
                .Append(Dec(traceFrameCount)).Append(",\n");
            json.Append("  \"start_x\": \"0x")
                .Append(Hex4(startX)).Append("\",\n");
            json.Append("  \"start_y\": \"0x")
                .Append(Hex4(startY)).Append("\",\n");
            json.Append(sidekickPresent
                ? "  \"characters\": [\"sonic\", \"tails\"],\n"
                : "  \"characters\": [\"sonic\"],\n");
            json.Append("  \"main_character\": \"sonic\",\n");
            json.Append(sidekickPresent
                ? "  \"sidekicks\": [\"tails\"],\n"
                : "  \"sidekicks\": [],\n");
            json.Append("  \"rng_seed\": \"0x")
                .Append(Hex8(rngSeed)).Append("\",\n");
            json.Append("  \"recording_date\": \"")
                .Append(recordingDate).Append("\",\n");
            TraceContract.AppendNativeEnvelope(json);
            json.Append("  \"aux_schema_extras\": "
                + "[\"cnz_slot_machine_state_per_frame\", "
                + "\"cpu_state_per_frame\"");
            if (loadQueueState)
            {
                json.Append(", \"load_queue_state_per_frame\"");
                json.Append(
                    ", \"").Append(
                        TraceContract.DynamicArtTransferStatePerFrame)
                    .Append("\"");
            }
            if (nativePreludeBootstrap)
            {
                json.Append(", \"").Append(
                    TraceContract.NativePreludeBootstrap).Append("\"");
            }
            json.Append("],\n");
            json.Append("  \"trace_profile\": \"")
                .Append(JsonEscape(traceProfile)).Append("\",\n");
            json.Append("  \"bizhawk_version\": \"2.11\",\n");
            json.Append("  \"genesis_core\": \"Genplus-gx\",\n");
            json.Append("  \"route\": \"").Append(zoneName).Append("\",\n");
            json.Append("  \"source_bk2\": \"")
                .Append(JsonEscape(sourceBk2)).Append("\",\n");
            if (runId != null)
            {
                json.Append("  \"run_id\": \"").Append(runId).Append("\",\n");
                json.Append("  \"segment_index\": ")
                    .Append(Dec(segmentIndex)).Append(",\n");
            }
            json.Append("  \"rom_checksum\": \"\",\n");
            json.Append("  \"notes\": \"\"\n");
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
