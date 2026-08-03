using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Byte-exact port of the S1 complete-run recorder's level-segment
    /// metadata.json layout (tools/bizhawk/s1_complete_run_recorder.lua
    /// write_metadata; spec tools/bizhawk-headless/docs/
    /// s1-complete-run-behavior.md section 7): 2-space indent, fixed key
    /// order, LF line endings, and a trailing newline after the closing
    /// brace. Differs from the standard recorder's
    /// <see cref="S1TraceMetadataWriter"/> in the 9-entry
    /// aux_schema_extras list and the trailing source_bk2 key.
    /// All start-captured values come from the segment's arm-frame RAM;
    /// zone/zone_id/act are the raw ROM values (SBZ3 is lz/1/4 and Final
    /// Zone is sbz/5/3 — fixture directory names were renamed by hand, not
    /// by the recorder). The recording date is the only nondeterministic
    /// field; current strict-v5 output emits no legacy version fields.
    /// </summary>
    public static class S1CompleteRunMetadataWriter
    {
        /// <summary>
        /// The unconditional aux_schema_extras line. The env-gated
        /// rng_call_per_frame entry (spec section 6.3) belongs to the
        /// unported diagnostic mode and never appears in fixtures.
        /// </summary>
        public const string AuxSchemaExtrasLine =
            "  \"aux_schema_extras\": [\"s1_obj64_state_per_frame\","
            + " \"object_near_obj_frame\", \"v_objstate_per_frame\","
            + " \"camera_boundary_per_frame\","
            + " \"object_near_routine2_objoff3c\","
            + " \"object_near_objoff_34_36_38\", \"v_oscillate_per_frame\","
            + " \"lag_state_per_frame\", \"object_near_objoff_32\"],\n";

        /// <summary>
        /// Formats the complete metadata.json contents, including the final
        /// newline. <paramref name="actRaw"/> is the raw byte at 0xFE11; the
        /// emitted "act" value is actRaw + 1. <paramref name="sourceBk2"/>
        /// is written via plain quoting, which equals the Lua's %q for any
        /// metacharacter-free file name; names that %q would escape are
        /// rejected rather than silently diverging.
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
            string sourceBk2,
            bool loadQueueState = false)
        {
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (sourceBk2.IndexOfAny(
                new[] { '"', '\\', '\n', '\r', '\0' }) >= 0)
            {
                throw new ArgumentException(
                    "source_bk2 contains characters the Lua %q quoting"
                    + " would escape; plain quoting would diverge: <"
                    + sourceBk2 + ">.",
                    "sourceBk2");
            }

            var json = new StringBuilder(1024);
            json.Append("{\n");
            json.Append("  \"game\": \"s1\",\n");
            json.Append("  \"zone\": \"")
                .Append(S1TraceMetadataWriter.ZoneName(zoneId))
                .Append("\",\n");
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
            TraceContract.AppendNativeEnvelope(json);
            if (loadQueueState)
            {
                json.Append(AuxSchemaExtrasLine.Substring(
                    0, AuxSchemaExtrasLine.Length - 3));
                json.Append(", \"load_queue_state_per_frame\", \"")
                    .Append(TraceContract.DynamicArtTransferStatePerFrame)
                    .Append("\"],\n");
            }
            else
            {
                json.Append(AuxSchemaExtrasLine);
            }
            json.Append("  \"rom_checksum\": \"\",\n");
            json.Append("  \"notes\": \"\",\n");
            json.Append("  \"source_bk2\": \"")
                .Append(sourceBk2).Append("\"\n");
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
