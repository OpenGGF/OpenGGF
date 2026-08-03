using System;
using System.Globalization;
using System.Text;

namespace OpenGGF.BizHawk.Headless
{
    /// <summary>
    /// Native writer for the S3K complete-run recorder's THREE
    /// metadata.json shapes, derived from the frozen Lua layout
    /// (tools/bizhawk/s3k_complete_run_recorder.lua) and advanced to
    /// v6.42-s3k-completerun: <c>write_metadata</c> for the level and
    /// bonus shapes, <c>write_ss_metadata</c> L5103 for the special-stage
    /// shape; spec tools/bizhawk-headless/docs/s3k-run-publication.md §3).
    ///
    /// This is deliberately NOT an extension of
    /// <see cref="S3KTraceMetadataWriter"/> (the STANDARD recorder's
    /// writer). The two writers disagree in ways that are structural, not
    /// parameterisable: the complete-run shape adds source_bk2,
    /// pre_trace_osc_frames, an optional run_id, segment_index, the
    /// Player_mode-derived character triple and a 19-name
    /// aux_schema_extras list, moves notes/trace_profile, and stamps a
    /// different lua_script_version. Sharing one formatter would mean a
    /// flag per line.
    ///
    /// Three shapes, and the differences between them are load-bearing
    /// (spec §3.3):
    ///
    /// - LEVEL (trace_profile "complete_run"): the base shape.
    /// - BONUS (trace_profile "s3k_bonus_stage"): the base shape with the
    ///   single trace_profile line replaced by
    ///   trace_profile + bonus_stage_type + (6.32+) v_int_run_count.
    /// - SPECIAL STAGE (trace_profile "s3k_special_stage"): a DIFFERENT key
    ///   set and order, not a superset — no zone/zone_id/act, no
    ///   pre_trace_osc_frames, no start_x/start_y, no rng_seed, no
    ///   csv_version, no capture_mode, and no profile aux schema extras
    ///   (the optional physical queue capability is still advertised); no
    ///   bonus_stage_type, no v_int_run_count, no notes; plus
    ///   trace_schema 7 / hardware_timing_schema 2,
    ///   ss_csv_version and a hardcoded fresh_load false.
    ///
    /// The two special-stage absences that look like env or version deltas
    /// but are structural: <c>write_ss_metadata</c> never reads
    /// LIGHTWEIGHT_REGEN (so no recorder build can give it a capture_mode
    /// line) and never reads current_segment_is_bonus /
    /// start_v_int_run_count (so the 6.31 -> 6.32 v_int_run_count addition
    /// is invisible to it). The (C) fixture set proves both empirically:
    /// captured in ONE pass under one --run-id, its three bonus dirs carry
    /// capture_mode AND v_int_run_count while special_stage/ carries
    /// neither.
    ///
    /// Sampling times matter because the Lua rewrites metadata.json at arm,
    /// every 300 rows and at finalize, and only the finalize write survives
    /// (spec §2.3). trace_frame_count, recording_date and Player_mode are
    /// therefore FINALIZE-time values; everything else comes from the arm
    /// record. A native port that materialises the file exactly once, at
    /// finalize, is byte-equivalent.
    /// </summary>
    public static class S3KCompleteRunMetadataWriter
    {
        /// <summary>
        /// The Lua's hardcoded S3K_ROM_CHECKSUM: the BizHawk movie-header
        /// hash of the locked-on combined image, never computed from the
        /// ROM file. NOT the file SHA-1 used by RomIdentity.
        /// </summary>
        public const string RomChecksum = "C5B1C655C19F462ADE0AC4E17A844D10";

        /// <summary>
        /// Emitted iff LIGHTWEIGHT_REGEN, which commit 192d9c976 inverted
        /// to default-ON (LIGHTWEIGHT_REGEN = not DIAGNOSTIC_HOOKS_ENABLED).
        /// The native port refuses OGGF_TRACE_ENABLE_DIAGNOSTIC_HOOKS=1
        /// outright, so for it the line is unconditional on the level and
        /// bonus paths — and structurally impossible on the SS path.
        /// </summary>
        public const string CaptureMode =
            "physics_animation_aux_without_diagnostic_hooks";

        /// <summary>
        /// fixture_notes for TRACE_PROFILE == "complete_run". Written for
        /// EVERY complete-run segment including bonus segments and
        /// including Knuckles-route captures, where its wording is
        /// inaccurate. Reproduce it verbatim; do not "fix" it.
        /// </summary>
        public const string Notes =
            "Per-zone segment from the S3K complete-run (AIZ->Doomsday)"
            + " Sonic+Tails movie. Covers act1 -> seamless act1->act2 -> the"
            + " act2->next-zone exit handoff (trailing 0x8C frames)."
            + " Game_paused aux flag is comparison-only.";

        private const string BizHawkVersion = "2.11";
        private const string GenesisCore = "Genplus-gx";

        /// <summary>
        /// aux_schema_extras for TRACE_PROFILE == "complete_run": the
        /// 12-name base list plus the 7 complete-run additions, in Lua
        /// append order. Advertised UNCONDITIONALLY — hook-driven names
        /// appear here even with the hooks off and no such event in the
        /// stream — and byte-identical in every (A)/(B)/(C) fixture. The
        /// standard recorder's env-armed cnz_event_ram_per_frame /
        /// rng_call_per_frame appends live in write_metadata's `else` arm
        /// (L1418/L1422), which the hard-pinned "complete_run" profile makes
        /// unreachable, so they can never join this list.
        /// </summary>
        private static readonly string[] AuxSchemaExtras =
        {
            "cpu_state_per_frame",
            "oscillation_state_per_frame",
            "object_state_per_frame",
            "interact_state_per_frame",
            "velocity_write_per_frame",
            "position_write_per_frame",
            "tails_cpu_normal_step_per_frame",
            "sidekick_interact_object_per_frame",
            "control_lock_state_per_frame",
            "sonic_record_pos_per_frame",
            "air_countdown_state_per_frame",
            "game_paused_state_per_frame",
            "cage_state_per_frame",
            "cage_execution_per_frame",
            "cnz_cylinder_state_per_frame",
            "cnz_cylinder_execution_per_frame",
            "solid_object_cont_entry_per_frame",
            "collision_response_list_per_frame",
            "collision_response_list_end_of_frame"
        };

        /// <summary>
        /// The level / bonus metadata.json, including the trailing newline.
        /// <paramref name="arm"/> supplies every arm-sampled field (with
        /// <see cref="S3KSegmentArm.GameplayFrameCounter"/> already
        /// overwritten by the segment's first recorded frame, which is what
        /// pre_trace_osc_frames must carry);
        /// <paramref name="traceFrameCount"/>,
        /// <paramref name="recordingDate"/> and
        /// <paramref name="playerMode"/> are the finalize-time samples.
        /// <paramref name="runId"/> is null when --run-id was not supplied,
        /// in which case the key is ABSENT (not null).
        ///
        /// Whether the bonus block replaces the plain trace_profile line is
        /// decided by <see cref="S3KSegmentArm.BonusStageType"/> being
        /// non-null — i.e. by the Lua's current_segment_is_bonus, which
        /// start_new_segment sets from BONUS_TOKENS[zone_id]. It is NOT
        /// decided by the arm's TraceProfile string, so the two can never
        /// drift apart.
        /// </summary>
        public static string Format(
            S3KSegmentArm arm,
            int traceFrameCount,
            string sourceBk2,
            string recordingDate,
            string runId,
            int playerMode,
            bool loadQueueState = false)
        {
            if (arm == null)
            {
                throw new ArgumentNullException("arm");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }

            var json = new StringBuilder(2048);
            json.Append("{\n");
            json.Append("  \"game\": \"s3k\",\n");
            json.Append("  \"zone\": \"").Append(arm.ZoneToken)
                .Append("\",\n");
            json.Append("  \"zone_id\": ").Append(Dec(arm.ZoneId))
                .Append(",\n");
            json.Append("  \"act\": ").Append(Dec(arm.ActRaw + 1))
                .Append(",\n");
            json.Append("  \"bk2_frame_offset\": ")
                .Append(Dec(arm.Bk2FrameOffset)).Append(",\n");
            json.Append("  \"source_bk2\": ")
                .Append(RunManifestWriter.LuaQ(sourceBk2)).Append(",\n");
            json.Append("  \"trace_frame_count\": ")
                .Append(Dec(traceFrameCount)).Append(",\n");
            json.Append("  \"pre_trace_osc_frames\": ")
                .Append(Dec(arm.GameplayFrameCounter)).Append(",\n");
            json.Append("  \"start_x\": \"0x").Append(Hex4(arm.StartX))
                .Append("\",\n");
            json.Append("  \"start_y\": \"0x").Append(Hex4(arm.StartY))
                .Append("\",\n");
            json.Append(CharacterMetadataJson(playerMode));
            json.Append("  \"rng_seed\": \"0x").Append(Hex8(arm.RngSeed))
                .Append("\",\n");
            json.Append("  \"recording_date\": \"").Append(recordingDate)
                .Append("\",\n");
            TraceContract.AppendNativeEnvelope(json);
            json.Append("  \"capture_mode\": \"").Append(CaptureMode)
                .Append("\",\n");
            json.Append("  \"aux_schema_extras\": [");
            for (var index = 0; index < AuxSchemaExtras.Length; index++)
            {
                if (index > 0)
                {
                    json.Append(", ");
                }
                json.Append(JsonQuote(AuxSchemaExtras[index]));
            }
            if (loadQueueState)
            {
                json.Append(", \"load_queue_state_per_frame\"");
            }
            json.Append("],\n");
            if (arm.BonusStageType != null)
            {
                json.Append(
                    "  \"trace_profile\": \"s3k_bonus_stage\",\n");
                json.Append("  \"bonus_stage_type\": \"")
                    .Append(arm.BonusStageType).Append("\",\n");
                // 6.32+ only, bonus only, and gated on the sample existing
                // rather than on its value: a genuine 0 still renders.
                if (arm.VIntRunCount.HasValue)
                {
                    json.Append("  \"v_int_run_count\": ")
                        .Append(Dec(arm.VIntRunCount.Value)).Append(",\n");
                }
            }
            else
            {
                json.Append("  \"trace_profile\": \"")
                    .Append(S3KCompleteRunSegmenter.LevelTraceProfile)
                    .Append("\",\n");
            }
            if (runId != null)
            {
                json.Append("  \"run_id\": \"").Append(runId)
                    .Append("\",\n");
            }
            json.Append("  \"segment_index\": ").Append(Dec(arm.SegmentIndex))
                .Append(",\n");
            json.Append("  \"bizhawk_version\": \"").Append(BizHawkVersion)
                .Append("\",\n");
            json.Append("  \"genesis_core\": \"").Append(GenesisCore)
                .Append("\",\n");
            json.Append("  \"rom_checksum\": \"").Append(RomChecksum)
                .Append("\",\n");
            json.Append("  \"notes\": ").Append(JsonQuote(Notes))
                .Append('\n');
            json.Append("}\n");
            return json.ToString();
        }

        /// <summary>
        /// The special-stage metadata.json (write_ss_metadata), including
        /// the trailing newline. A different key set AND order from
        /// <see cref="Format"/>; see the class doc for the four absences
        /// that are structural rather than configurable.
        /// </summary>
        public static string FormatSpecialStage(
            S3KSegmentArm arm,
            int traceFrameCount,
            string sourceBk2,
            string recordingDate,
            string runId,
            int playerMode,
            bool loadQueueState = false)
        {
            if (arm == null)
            {
                throw new ArgumentNullException("arm");
            }
            if (sourceBk2 == null)
            {
                throw new ArgumentNullException("sourceBk2");
            }
            if (recordingDate == null)
            {
                throw new ArgumentNullException("recordingDate");
            }

            var json = new StringBuilder(768);
            json.Append("{\n");
            json.Append("  \"game\": \"s3k\",\n");
            json.Append("  \"trace_profile\": \"s3k_special_stage\",\n");
            json.Append("  \"special_stage_index\": ")
                .Append(Dec(arm.SpecialStageIndex ?? 0)).Append(",\n");
            json.Append(CharacterMetadataJson(playerMode));
            json.Append("  \"bk2_frame_offset\": ")
                .Append(Dec(arm.Bk2FrameOffset)).Append(",\n");
            json.Append("  \"trace_frame_count\": ")
                .Append(Dec(traceFrameCount)).Append(",\n");
            json.Append("  \"source_bk2\": ")
                .Append(RunManifestWriter.LuaQ(sourceBk2)).Append(",\n");
            TraceContract.AppendNativeEnvelope(json);
            json.Append("  \"recording_date\": \"").Append(recordingDate)
                .Append("\",\n");
            json.Append("  \"bizhawk_version\": \"").Append(BizHawkVersion)
                .Append("\",\n");
            json.Append("  \"genesis_core\": \"").Append(GenesisCore)
                .Append("\",\n");
            json.Append("  \"rom_checksum\": \"").Append(RomChecksum)
                .Append("\",\n");
            if (runId != null)
            {
                json.Append("  \"run_id\": \"").Append(runId)
                    .Append("\",\n");
            }
            if (loadQueueState)
            {
                json.Append(
                    "  \"aux_schema_extras\":"
                    + " [\"load_queue_state_per_frame\"],\n");
            }
            // Hardcoded: giant-ring entries are always mid-level. A future
            // fresh-boot SS capture path would have to set this true.
            json.Append("  \"fresh_load\": false,\n");
            json.Append("  \"segment_index\": ").Append(Dec(arm.SegmentIndex))
                .Append('\n');
            json.Append("}\n");
            return json.ToString();
        }

        /// <summary>
        /// character_metadata_json (Lua L1257): the three-line
        /// characters/main_character/sidekicks block derived from
        /// Player_mode, read as a WORD at 0xFF08
        /// (sonic3k.constants.asm:892 — 0 Sonic+Tails, 1 Sonic alone,
        /// 2 Tails alone, 3 Knuckles). Any other value falls through to the
        /// Sonic+Tails default, exactly like the Lua's if/elseif chain.
        /// Shared by all three metadata shapes, which is why the SS shape
        /// carries characters even though it carries no zone.
        /// </summary>
        public static string CharacterMetadataJson(int playerMode)
        {
            if (playerMode == 1)
            {
                return "  \"characters\": [\"sonic\"],\n"
                    + "  \"main_character\": \"sonic\",\n"
                    + "  \"sidekicks\": [],\n";
            }
            if (playerMode == 2)
            {
                return "  \"characters\": [\"tails\"],\n"
                    + "  \"main_character\": \"tails\",\n"
                    + "  \"sidekicks\": [],\n";
            }
            if (playerMode == 3)
            {
                return "  \"characters\": [\"knuckles\"],\n"
                    + "  \"main_character\": \"knuckles\",\n"
                    + "  \"sidekicks\": [],\n";
            }
            return "  \"characters\": [\"sonic\", \"tails\"],\n"
                + "  \"main_character\": \"sonic\",\n"
                + "  \"sidekicks\": [\"tails\"],\n";
        }

        /// <summary>
        /// json_quote (oggf_trace_common.lua L140): quote-wrapping with
        /// backslash-then-quote escaping. Deliberately NOT json_escape,
        /// which returns an unwrapped body.
        /// </summary>
        private static string JsonQuote(string value)
        {
            return "\""
                + value.Replace("\\", "\\\\").Replace("\"", "\\\"")
                + "\"";
        }

        private static string Dec(int value)
        {
            return value.ToString(CultureInfo.InvariantCulture);
        }

        private static string Dec(uint value)
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
