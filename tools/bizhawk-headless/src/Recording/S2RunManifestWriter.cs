using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// One finished run segment as it appears in run_manifest.json (Lua
    /// segments_done entry). The Lua-side per-segment row-count field is
    /// `rows`, emitted under the JSON key "trace_frame_count". Special-stage
    /// entries carry a special_stage_index and hardcode zone_id/act to 0.
    /// </summary>
    public sealed class S2RunManifestSegment
    {
        public const string LevelKind = "level";
        public const string SpecialStageKind = "special_stage";

        public S2RunManifestSegment(
            string dir,
            string kind,
            string traceProfile,
            int bk2FrameOffset,
            int traceFrameCount,
            int zoneId,
            int act,
            int? specialStageIndex)
        {
            if (dir == null)
            {
                throw new ArgumentNullException("dir");
            }
            if (kind == null)
            {
                throw new ArgumentNullException("kind");
            }
            if (traceProfile == null)
            {
                throw new ArgumentNullException("traceProfile");
            }
            Dir = dir;
            Kind = kind;
            TraceProfile = traceProfile;
            Bk2FrameOffset = bk2FrameOffset;
            TraceFrameCount = traceFrameCount;
            ZoneId = zoneId;
            Act = act;
            SpecialStageIndex = specialStageIndex;
        }

        public string Dir { get; private set; }
        public string Kind { get; private set; }
        public string TraceProfile { get; private set; }
        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
        public int ZoneId { get; private set; }
        public int Act { get; private set; }
        public int? SpecialStageIndex { get; private set; }
    }

    /// <summary>
    /// One recorded segment transition (Lua transitions_done entry). The
    /// optional RAM-sourced fields are emitted iff they were RECORDED for
    /// the transition kind, never keyed on the value: in Lua, 0 is truthy,
    /// so a sampled 0 (e.g. emeralds_before on the first detour or the
    /// post-reload rings_after) still renders. starpost_special records set
    /// the five *_before-side fields; stage_exit records set only
    /// rings_after / emeralds_after. The v9.13-s2 reload kinds (§11.2) set
    /// rings/emeralds before+after, and death_restart additionally
    /// saved_x/y_pos + last_star_post_hit; neither sets
    /// special_bonus_entry_flag.
    /// </summary>
    public sealed class S2RunManifestTransition
    {
        public const string StarpostSpecialKind = "starpost_special";
        public const string StageExitKind = "stage_exit";
        public const string DeathRestartKind = "death_restart";
        public const string LevelAdvanceKind = "level_advance";

        public S2RunManifestTransition(
            int fromSegment,
            int toSegment,
            string entryKind,
            int modeChangeBk2Frame)
        {
            if (entryKind == null)
            {
                throw new ArgumentNullException("entryKind");
            }
            FromSegment = fromSegment;
            ToSegment = toSegment;
            EntryKind = entryKind;
            ModeChangeBk2Frame = modeChangeBk2Frame;
        }

        public int FromSegment { get; private set; }
        public int ToSegment { get; private set; }
        public string EntryKind { get; private set; }
        public int ModeChangeBk2Frame { get; private set; }
        public int? SpecialBonusEntryFlag { get; set; }
        public int? SavedXPos { get; set; }
        public int? SavedYPos { get; set; }
        public int? LastStarPostHit { get; set; }
        public int? RingsBefore { get; set; }
        public int? RingsAfter { get; set; }
        public int? EmeraldsBefore { get; set; }
        public int? EmeraldsAfter { get; set; }
    }

    /// <summary>
    /// Byte-exact port of the S2 Lua run-mode run_manifest.json emitter
    /// (tools/bizhawk/s2_trace_recorder.lua v9.13-s2, write_run_manifest;
    /// spec s2-run-mode-behavior.md §6, §11). Written exactly once at
    /// run termination to the run root. rom_checksum is the inline literal
    /// "7B905383" (S2 World REV01 CRC32) and lua_script_version the
    /// "9.13-s2" constant — neither is computed at runtime. String fields
    /// that the Lua renders with %q (run_id, dir, kind, trace_profile,
    /// entry_kind) go through the %q-faithful quoting below; source_bk2 goes
    /// through the shared json_escape helper instead.
    /// </summary>
    public static class S2RunManifestWriter
    {
        public static string Format(
            string runId,
            string sourceBk2,
            IList<S2RunManifestSegment> segments,
            IList<S2RunManifestTransition> transitions)
        {
            if (runId == null)
            {
                throw new ArgumentNullException("runId");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (segments == null)
            {
                throw new ArgumentNullException("segments");
            }
            if (transitions == null)
            {
                throw new ArgumentNullException("transitions");
            }

            var json = new StringBuilder(1024);
            json.Append("{\n");
            json.Append("  \"run_schema\": 1,\n");
            json.Append("  \"game\": \"s2\",\n");
            json.Append("  \"run_id\": ").Append(LuaQ(runId)).Append(",\n");
            json.Append("  \"source_bk2\": \"")
                .Append(JsonEscape(sourceBk2)).Append("\",\n");
            json.Append("  \"rom_checksum\": \"7B905383\",\n");
            json.Append("  \"lua_script_version\": \"9.13-s2\",\n");
            json.Append("  \"segments\": [\n");
            for (var index = 0; index < segments.Count; index++)
            {
                S2RunManifestSegment segment = segments[index];
                json.Append("    {\"dir\": ").Append(LuaQ(segment.Dir));
                json.Append(", \"kind\": ").Append(LuaQ(segment.Kind));
                json.Append(", \"trace_profile\": ")
                    .Append(LuaQ(segment.TraceProfile));
                json.Append(", \"bk2_frame_offset\": ")
                    .Append(Dec(segment.Bk2FrameOffset));
                json.Append(", \"trace_frame_count\": ")
                    .Append(Dec(segment.TraceFrameCount));
                json.Append(", \"zone_id\": ").Append(Dec(segment.ZoneId));
                json.Append(", \"act\": ").Append(Dec(segment.Act));
                if (segment.Kind == S2RunManifestSegment.SpecialStageKind)
                {
                    json.Append(", \"special_stage_index\": ")
                        .Append(Dec(segment.SpecialStageIndex ?? 0));
                }
                json.Append('}');
                if (index < segments.Count - 1)
                {
                    json.Append(',');
                }
                json.Append('\n');
            }
            json.Append("  ],\n");
            json.Append("  \"transitions\": [\n");
            for (var index = 0; index < transitions.Count; index++)
            {
                S2RunManifestTransition tx = transitions[index];
                json.Append("    {\"from_segment\": ")
                    .Append(Dec(tx.FromSegment));
                json.Append(", \"to_segment\": ").Append(Dec(tx.ToSegment));
                json.Append(", \"entry_kind\": ").Append(LuaQ(tx.EntryKind));
                json.Append(", \"mode_change_bk2_frame\": ")
                    .Append(Dec(tx.ModeChangeBk2Frame));
                AppendOptional(json, "special_bonus_entry_flag",
                    tx.SpecialBonusEntryFlag);
                AppendOptional(json, "saved_x_pos", tx.SavedXPos);
                AppendOptional(json, "saved_y_pos", tx.SavedYPos);
                AppendOptional(json, "last_star_post_hit", tx.LastStarPostHit);
                AppendOptional(json, "rings_before", tx.RingsBefore);
                AppendOptional(json, "rings_after", tx.RingsAfter);
                AppendOptional(json, "emeralds_before", tx.EmeraldsBefore);
                AppendOptional(json, "emeralds_after", tx.EmeraldsAfter);
                json.Append('}');
                if (index < transitions.Count - 1)
                {
                    json.Append(',');
                }
                json.Append('\n');
            }
            json.Append("  ]\n}\n");
            return json.ToString();
        }

        private static void AppendOptional(
            StringBuilder json,
            string name,
            int? value)
        {
            if (value.HasValue)
            {
                json.Append(", \"").Append(name).Append("\": ")
                    .Append(Dec(value.Value));
            }
        }

        /// <summary>
        /// Lua string.format %q: double-quoted with backslash-escaped
        /// quote/backslash; an embedded newline renders as a backslash
        /// followed by a literal newline, CR as \r and NUL as \0. Run-mode
        /// strings are plain identifiers in practice, but the escaping is
        /// ported faithfully.
        /// </summary>
        private static string LuaQ(string value)
        {
            var quoted = new StringBuilder(value.Length + 2);
            quoted.Append('"');
            foreach (char item in value)
            {
                switch (item)
                {
                    case '"':
                        quoted.Append("\\\"");
                        break;
                    case '\\':
                        quoted.Append("\\\\");
                        break;
                    case '\n':
                        quoted.Append("\\\n");
                        break;
                    case '\r':
                        quoted.Append("\\r");
                        break;
                    case '\0':
                        quoted.Append("\\0");
                        break;
                    default:
                        quoted.Append(item);
                        break;
                }
            }
            quoted.Append('"');
            return quoted.ToString();
        }

        /// <summary>
        /// json_escape (oggf_trace_common.lua): backslash then quote.
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
