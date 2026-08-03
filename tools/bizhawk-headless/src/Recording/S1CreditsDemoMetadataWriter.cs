using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>Metadata for one ROM-owned S1 ending demo segment.</summary>
    public static class S1CreditsDemoMetadataWriter
    {
        public static string Format(
            S1CreditsDemoDefinition demo,
            int startFrame,
            int traceFrameCount,
            int startX,
            int startY,
            int zoneId,
            int actRaw,
            uint rngSeed,
            string recordingDate)
        {
            if (demo == null) throw new ArgumentNullException("demo");
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }
            var json = new StringBuilder(640);
            json.Append("{\n");
            json.Append("  \"game\": \"s1\",\n");
            json.Append("  \"zone\": \"").Append(
                S1TraceMetadataWriter.ZoneName(zoneId)).Append("\",\n");
            json.Append("  \"zone_id\": ").Append(zoneId).Append(",\n");
            json.Append("  \"act\": ").Append(actRaw + 1).Append(",\n");
            json.Append("  \"trace_type\": \"credits_demo\",\n");
            json.Append("  \"input_source\": \"rom_ending_demo\",\n");
            json.Append("  \"credits_demo_index\": ").Append(demo.Index).Append(",\n");
            json.Append("  \"credits_demo_slug\": \"").Append(demo.Slug).Append("\",\n");
            json.Append("  \"emu_frame_start\": ").Append(startFrame).Append(",\n");
            json.Append("  \"bk2_frame_offset\": 0,\n");
            json.Append("  \"trace_frame_count\": ").Append(traceFrameCount).Append(",\n");
            json.Append("  \"start_x\": \"0x").Append(startX.ToString("X4", CultureInfo.InvariantCulture)).Append("\",\n");
            json.Append("  \"start_y\": \"0x").Append(startY.ToString("X4", CultureInfo.InvariantCulture)).Append("\",\n");
            json.Append("  \"characters\": [\"sonic\"],\n");
            json.Append("  \"main_character\": \"sonic\",\n");
            json.Append("  \"sidekicks\": [],\n");
            json.Append("  \"rng_seed\": \"0x").Append(rngSeed.ToString("X8", CultureInfo.InvariantCulture)).Append("\",\n");
            json.Append("  \"recording_date\": \"").Append(recordingDate).Append("\",\n");
            TraceContract.AppendNativeEnvelope(json);
            json.Append("  \"aux_schema_extras\": [\"s1_obj64_state_per_frame\", \"")
                .Append(TraceContract.DynamicArtTransferStatePerFrame)
                .Append("\"],\n");
            json.Append("  \"rom_checksum\": \"\",\n");
            json.Append("  \"notes\": \"\"\n}\n");
            return json.ToString();
        }
    }
}
