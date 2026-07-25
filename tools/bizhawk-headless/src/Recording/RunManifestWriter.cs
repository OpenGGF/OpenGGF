using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// One finished run segment as it appears in run_manifest.json (Lua
    /// segments_done entry), shared by the S1 and S2 run recorders. The
    /// Lua-side per-segment row-count field is `rows`, emitted under the
    /// JSON key "trace_frame_count". Special-stage entries carry a
    /// special_stage_index and hardcode zone_id/act to 0.
    /// </summary>
    public sealed class RunManifestSegment
    {
        public const string LevelKind = "level";
        public const string SpecialStageKind = "special_stage";

        /// <summary>
        /// S3K-only third kind: gumball / pachinko / slots run under the
        /// ordinary level Game_mode family with zone ids 0x13-0x15, so they
        /// arm, record and finalize through the level path and differ only
        /// in kind / trace_profile / the bonus_stage_type extra
        /// (docs/s3k-complete-run-behavior.md §5.4). S1 and S2 never
        /// produce it.
        /// </summary>
        public const string BonusStageKind = "bonus_stage";

        public RunManifestSegment(
            string dir,
            string kind,
            string traceProfile,
            int bk2FrameOffset,
            int traceFrameCount,
            int zoneId,
            int act,
            int? specialStageIndex)
            : this(
                dir,
                kind,
                traceProfile,
                bk2FrameOffset,
                traceFrameCount,
                zoneId,
                act,
                specialStageIndex,
                null)
        {
        }

        /// <summary>
        /// S3K overload carrying the bonus_stage_type extra. The optional
        /// extras stay mutually exclusive by kind — a bonus_stage entry
        /// renders bonus_stage_type, a special_stage entry renders
        /// special_stage_index, a level entry renders neither — so the S1
        /// and S2 emitters, which never produce
        /// <see cref="BonusStageKind"/>, are byte-unaffected.
        /// </summary>
        public RunManifestSegment(
            string dir,
            string kind,
            string traceProfile,
            int bk2FrameOffset,
            int traceFrameCount,
            int zoneId,
            int act,
            int? specialStageIndex,
            string bonusStageType)
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
            BonusStageType = bonusStageType;
        }

        public string Dir { get; private set; }
        public string Kind { get; private set; }
        public string TraceProfile { get; private set; }
        public int Bk2FrameOffset { get; private set; }
        public int TraceFrameCount { get; private set; }
        public int ZoneId { get; private set; }
        public int Act { get; private set; }
        public int? SpecialStageIndex { get; private set; }

        /// <summary>
        /// gumball / pachinko / slots for a <see cref="BonusStageKind"/>
        /// entry; null everywhere else. Emitted in the same trailing slot
        /// as <see cref="SpecialStageIndex"/>.
        /// </summary>
        public string BonusStageType { get; private set; }
    }

    /// <summary>
    /// One recorded segment transition (Lua transitions_done entry), shared
    /// by the S1 and S2 run recorders. The optional RAM-sourced fields are
    /// emitted iff they were RECORDED for the transition kind, never keyed
    /// on the value: in Lua, 0 is truthy, so a sampled 0 (e.g.
    /// emeralds_before on the first detour) still renders. S2
    /// starpost_special records set the five *_before-side fields; S1
    /// giant_ring records set only rings_before / emeralds_before (S1 has no
    /// starpost machinery); stage_exit records set only rings_after /
    /// emeralds_after on both games. The S2 v9.13-s2 reload kinds
    /// (s2-run-mode-behavior.md §11.2) set rings/emeralds before+after, and
    /// death_restart additionally saved_x/y_pos + last_star_post_hit;
    /// neither sets special_bonus_entry_flag.
    /// </summary>
    public sealed class RunManifestTransition
    {
        public const string StarpostSpecialKind = "starpost_special";

        /// <summary>
        /// S3K-only: pushed at the level arm gate when the zone being armed
        /// is a bonus zone (docs/s3k-complete-run-behavior.md §7.2). Not
        /// S2's "starpost_special", which names the special-stage entry.
        /// </summary>
        public const string StarpostBonusKind = "starpost_bonus";
        public const string GiantRingKind = "giant_ring";
        public const string StageExitKind = "stage_exit";
        public const string DeathRestartKind = "death_restart";
        public const string LevelAdvanceKind = "level_advance";

        public RunManifestTransition(
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
    /// Shared byte-exact core of the Lua run-mode run_manifest.json
    /// emitters (s2_trace_recorder.lua write_run_manifest L998-1061;
    /// s1_complete_run_recorder.lua write_run_manifest L785-849). The two
    /// recorders emit the identical structural layout; the per-game deltas
    /// are literals and quoting only, injected by the thin
    /// <see cref="S1RunManifestWriter"/> / <see cref="S2RunManifestWriter"/>
    /// front-ends: the game name, the inline rom_checksum and
    /// lua_script_version literals, whether the run_id line may be absent
    /// (S1: emitted iff OGGF_TRACE_RUN_ID was set; S2: always present), and
    /// whether source_bk2 is rendered with Lua %q (S1) or the shared
    /// json_escape helper (S2). The optional transition fields keep one
    /// fixed order — the S2 superset — of which S1 records only the last
    /// four, so both games' emission orders are preserved verbatim.
    /// </summary>
    public static class RunManifestWriter
    {
        public static string Format(
            string game,
            string runId,
            string sourceBk2,
            bool sourceBk2LuaQuoted,
            string romChecksum,
            string luaScriptVersion,
            IList<RunManifestSegment> segments,
            IList<RunManifestTransition> transitions)
        {
            if (game == null)
            {
                throw new ArgumentNullException("game");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (romChecksum == null)
            {
                throw new ArgumentNullException("romChecksum");
            }
            if (luaScriptVersion == null)
            {
                throw new ArgumentNullException("luaScriptVersion");
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
            json.Append("  \"game\": \"").Append(game).Append("\",\n");
            if (runId != null)
            {
                json.Append("  \"run_id\": ").Append(LuaQ(runId))
                    .Append(",\n");
            }
            if (sourceBk2LuaQuoted)
            {
                json.Append("  \"source_bk2\": ").Append(LuaQ(sourceBk2))
                    .Append(",\n");
            }
            else
            {
                json.Append("  \"source_bk2\": \"")
                    .Append(JsonEscape(sourceBk2)).Append("\",\n");
            }
            json.Append("  \"rom_checksum\": \"").Append(romChecksum)
                .Append("\",\n");
            json.Append("  \"lua_script_version\": \"")
                .Append(luaScriptVersion).Append("\",\n");
            json.Append("  \"segments\": [\n");
            for (var index = 0; index < segments.Count; index++)
            {
                RunManifestSegment segment = segments[index];
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
                // Lua write_run_manifest's if/elseif on s.kind (S3K L1494,
                // S1/S2 special-stage-only): exactly one optional extra,
                // appended last, chosen by kind and never by which field
                // happens to be populated.
                if (segment.Kind == RunManifestSegment.BonusStageKind)
                {
                    json.Append(", \"bonus_stage_type\": ")
                        .Append(LuaQ(segment.BonusStageType ?? string.Empty));
                }
                else if (segment.Kind == RunManifestSegment.SpecialStageKind)
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
                RunManifestTransition tx = transitions[index];
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
        public static string LuaQ(string value)
        {
            if (value == null)
            {
                throw new ArgumentNullException("value");
            }
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
        /// json_escape (oggf_trace_common.lua): backslash then quote, no
        /// surrounding quotes.
        /// </summary>
        public static string JsonEscape(string value)
        {
            if (value == null)
            {
                throw new ArgumentNullException("value");
            }
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
