package com.openggf.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for a trace recording directory, parsed from metadata.json.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceMetadata(
    @JsonProperty("game") String game,
    @JsonProperty("zone") String zone,
    @JsonProperty("zone_id") Integer zoneId,
    @JsonProperty("act") int act,
    @JsonProperty("bk2_frame_offset") int bk2FrameOffset,
    @JsonProperty("trace_frame_count") int traceFrameCount,
    @JsonProperty("start_x") String startXHex,
    @JsonProperty("start_y") String startYHex,
    @JsonProperty("recording_date") String recordingDate,
    @JsonProperty("lua_script_version") String luaScriptVersion,
    @JsonProperty("trace_schema") Integer traceSchema,
    @JsonProperty("csv_version") Integer csvVersion,
    @JsonProperty("trace_profile") String traceProfile,
    @JsonProperty("bizhawk_version") String bizhawkVersion,
    @JsonProperty("genesis_core") String genesisCore,
    @JsonProperty("aux_schema_extras") List<String> auxSchemaExtras,
    @JsonProperty("rom_zone_id") Integer romZoneId,
    @JsonProperty("route") String route,
    @JsonProperty("source_bk2") String sourceBk2,
    @JsonProperty("rom_checksum") String romChecksum,
    @JsonProperty("notes") String notes,
    @JsonProperty("characters") List<String> characters,
    @JsonProperty("main_character") String mainCharacter,
    @JsonProperty("sidekicks") List<String> sidekicks,
    @JsonProperty("pre_trace_osc_frames") Integer preTraceOscFrames,
    @JsonProperty("rng_seed") String rngSeedHex,
    @JsonProperty("trace_type") String traceType,
    @JsonProperty("input_source") String inputSource,
    @JsonProperty("credits_demo_index") Integer creditsDemoIndex,
    @JsonProperty("credits_demo_slug") String creditsDemoSlug
) {

    /**
     * Recorder version at which the engine title-card phase began executing
     * objects natively (matching ROM `TitleCard_Main`). Traces recorded at or
     * after this version have frame-0 state captured against the
     * universal-ADR-1 engine; the bootstrap comparator can assert against
     * them. Earlier traces captured under the freeze-during-card workaround
     * are not bootstrap-comparable.
     */
    private static final int BOOTSTRAP_MIN_MAJOR = 9;
    private static final int BOOTSTRAP_MIN_MINOR = 2;

    /**
     * Number of Level_MainLoop frames the ROM executed between OscillateNumInit
     * and the first trace frame. The engine must pre-advance OscillationManager
     * by this many updates to match the ROM's oscillation phase.
     * Returns 0 if not specified in the metadata.
     */
    public int preTraceOscillationFrames() {
        return preTraceOscFrames != null ? preTraceOscFrames : 0;
    }

    /**
     * Whether this trace's frame-0 state is comparable against the
     * post-universal-title-card engine (ADR-1, design spec 2026-05-15).
     * Derived from {@link #luaScriptVersion}: true when the recorder version
     * is at or above v9.2-s2. Older recordings captured frame-0 against the
     * freeze-during-card engine workaround and are skipped by the bootstrap
     * comparator.
     */
    public boolean nativePreludeMode() {
        if (luaScriptVersion == null) {
            return false;
        }
        int dot = luaScriptVersion.indexOf('.');
        if (dot <= 0) {
            return false;
        }
        int end = luaScriptVersion.indexOf('-', dot);
        if (end < 0) {
            end = luaScriptVersion.length();
        }
        try {
            int major = Integer.parseInt(luaScriptVersion.substring(0, dot));
            int minor = Integer.parseInt(luaScriptVersion.substring(dot + 1, end));
            return major > BOOTSTRAP_MIN_MAJOR
                    || (major == BOOTSTRAP_MIN_MAJOR && minor >= BOOTSTRAP_MIN_MINOR);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Initial ROM RNG seed captured when the trace begins, or {@code null} for
     * legacy traces that did not record it. S3K level-select recordings can
     * inherit non-zero RNG state before gameplay; replay must preserve it for
     * ROM Random_Number users such as CNZ balloons.
     */
    public Long initialRngSeed() {
        if (rngSeedHex == null || rngSeedHex.isBlank()) {
            return null;
        }
        String raw = rngSeedHex.trim();
        if (raw.startsWith("0x") || raw.startsWith("0X")) {
            raw = raw.substring(2);
        }
        return Long.parseUnsignedLong(raw, 16) & 0xFFFFFFFFL;
    }

    /** Parse start_x hex string to short. */
    public short startX() {
        return (short) Integer.parseInt(startXHex.replace("0x", ""), 16);
    }

    /** Parse start_y hex string to short. */
    public short startY() {
        return (short) Integer.parseInt(startYHex.replace("0x", ""), 16);
    }

    /** Returns the recorded sidekick list, or an empty list when absent. */
    public List<String> recordedSidekicks() {
        if (characters != null && characters.size() > 1) {
            return List.copyOf(characters.subList(1, characters.size()));
        }
        return sidekicks != null ? List.copyOf(sidekicks) : List.of();
    }

    /** Returns the recorded playable character list in order. */
    public List<String> recordedCharacters() {
        if (characters != null && !characters.isEmpty()) {
            return List.copyOf(characters);
        }
        List<String> result = new ArrayList<>();
        if (mainCharacter != null && !mainCharacter.isBlank()) {
            result.add(mainCharacter);
        }
        result.addAll(recordedSidekicks());
        return List.copyOf(result);
    }

    /** Returns the primary recorded playable character, if present. */
    public String recordedMainCharacter() {
        List<String> recorded = recordedCharacters();
        return recorded.isEmpty() ? null : recorded.getFirst();
    }

    /** Returns whether this trace metadata explicitly records a gameplay team. */
    public boolean hasRecordedTeam() {
        return !recordedCharacters().isEmpty();
    }

    /**
     * Whether the trace's aux_state.jsonl emits per-frame {@code cpu_state}
     * events (the v6+ recorder extension that snapshots the full Tails CPU
     * global block plus {@code Ctrl_2_logical} on every recorded frame).
     */
    public boolean hasPerFrameCpuState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("cpu_state_per_frame");
    }

    /**
     * Whether the trace's aux_state.jsonl emits per-frame
     * {@code oscillation_state} events (the v6.1+ S3K recorder extension that
     * snapshots the full {@code Oscillating_table} bytes plus
     * {@code Level_frame_counter} on every recorded frame). Used by trace
     * replay diagnostics to ROM-verify global oscillator phase.
     */
    public boolean hasPerFrameOscillationState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("oscillation_state_per_frame");
    }

    /**
     * Whether the trace's aux_state.jsonl emits per-frame {@code cage_state}
     * events (the v6.3+ S3K recorder extension that snapshots each CNZ wire
     * cage object's per-player state bytes plus its main status byte on every
     * recorded frame). Used by divergence diagnostics for the CNZ cage's
     * ride/release state machine.
     */
    public boolean hasPerFrameCageState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("cage_state_per_frame");
    }

    /**
     * Whether the trace's aux_state.jsonl emits per-frame {@code cage_execution}
     * events (the v6.3+ S3K recorder extension that captures each CNZ wire
     * cage routine branch the M68K CPU entered along with register state).
     * Used by divergence diagnostics to ROM-verify which cage branch the
     * CPU actually took on each frame.
     */
    public boolean hasPerFrameCageExecution() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("cage_execution_per_frame");
    }

    /**
     * Whether the trace's aux_state.jsonl emits per-frame
     * {@code velocity_write} events (the v6.4+ S3K recorder extension that
     * captures every M68K write to the sidekick's {@code x_vel}/{@code y_vel}
     * RAM addresses, with each hit's writing-instruction PC). Used to root-
     * cause the CNZ1 trace F3649 divergence (Tails {@code x_speed} jumps
     * from -$48 to -$0A00 in one frame in ROM but takes two in the engine).
     */
    public boolean hasPerFrameVelocityWrite() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("velocity_write_per_frame");
    }

    /**
     * Whether the trace's aux_state.jsonl emits per-frame
     * {@code position_write} events (the v6.8+ S3K recorder extension that
     * captures M68K writes to the sidekick's {@code x_pos}/{@code y_pos}
     * RAM words, with each hit's writing-instruction PC). Used to root-cause
     * the CNZ1 trace F4790 Tails {@code x_pos} write.
     */
    public boolean hasPerFramePositionWrite() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("position_write_per_frame");
    }

    /**
     * Whether the trace emits focused S3K AIZ battleship ship-loop execution
     * diagnostics around {@code AIZ2_DoShipLoop/sub_50318}.
     */
    public boolean hasPerFrameAizShipLoop() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("aiz_ship_loop_per_frame");
    }

    /**
     * Whether the trace emits hook-driven {@code sonic_record_pos} events
     * showing the Player_1 Stat_table source writes used by delayed Tails CPU
     * reads.
     */
    public boolean hasPerFrameSonicRecordPos() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("sonic_record_pos_per_frame");
    }

    /**
     * Whether the trace emits per-frame {@code tails_cpu_normal_step} events,
     * the focused S3K Tails CPU normal-follow diagnostic for CNZ/AIZ frontiers.
     */
    public boolean hasPerFrameTailsCpuNormalStep() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("tails_cpu_normal_step_per_frame");
    }

    /**
     * Whether the trace emits per-frame {@code sidekick_interact_object}
     * events, the focused S3K sidekick interact-object diagnostic for AIZ
     * ride/grab handoff frontiers.
     */
    public boolean hasPerFrameSidekickInteractObject() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("sidekick_interact_object_per_frame");
    }

    /**
     * Whether the trace emits per-frame CNZ cylinder state snapshots, focused on
     * the P1/P2 slot bytes consumed by {@code sub_324C0}.
     */
    public boolean hasPerFrameCnzCylinderState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("cnz_cylinder_state_per_frame");
    }

    /**
     * Whether the trace emits CNZ cylinder execution-hook hits around
     * {@code sub_324C0} and {@code MvSonicOnPtfm}.
     */
    public boolean hasPerFrameCnzCylinderExecution() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("cnz_cylinder_execution_per_frame");
    }

    /**
     * Whether the trace emits focused CNZ post-boss event RAM snapshots for
     * {@code Events_bg+$08/$0C}, {@code Events_routine_bg}, and
     * {@code Background_collision_flag}.
     */
    public boolean hasPerFrameCnzEventRam() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("cnz_event_ram_per_frame");
    }

    /**
     * Whether the trace emits per-frame slot-machine feature state snapshots.
     * This identifies traces whose title-card replay needs the native
     * slot-machine short init window before comparison begins.
     */
    public boolean hasPerFrameSlotMachineState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("cnz_slot_machine_state_per_frame");
    }

    /**
     * @deprecated Use {@link #hasPerFrameSlotMachineState()}. The current
     * recorder schema name is still CNZ-specific, but replay policy should
     * consume the generic feature capability.
     */
    @Deprecated
    public boolean hasPerFrameCnzSlotMachineState() {
        return hasPerFrameSlotMachineState();
    }

    /**
     * Whether the trace emits S3K fixed Breathing_bubbles /
     * Breathing_bubbles_P2 controller diagnostics and their visible dynamic
     * {@code Obj_AirCountdown} children.
     */
    public boolean hasPerFrameAirCountdownState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("air_countdown_state_per_frame");
    }

    /**
     * Whether the trace emits focused S3K {@code Random_Number} call-order
     * diagnostics with ROM seed/result and object register context.
     */
    public boolean hasPerFrameRngCall() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("rng_call_per_frame");
    }

    /**
     * Whether the trace emits per-frame {@code aiz_boundary_state} events, the
     * focused S3K AIZ sidekick tree/boundary diagnostic for F4679.
     */
    public boolean hasPerFrameAizBoundaryState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("aiz_boundary_state_per_frame");
    }

    /**
     * Whether the trace emits per-frame {@code aiz_transition_floor_solid}
     * events, the focused S3K AIZ transition floor diagnostic for F5415.
     */
    public boolean hasPerFrameAizTransitionFloorSolid() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("aiz_transition_floor_solid_per_frame");
    }

    /**
     * Whether the trace emits per-frame {@code aiz_handoff_terrain_state}
     * events, the focused S3K AIZ fire-handoff terrain diagnostic for F5435.
     */
    public boolean hasPerFrameAizHandoffTerrainState() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("aiz_handoff_terrain_state_per_frame");
    }

    /**
     * Whether the fixture explicitly records that trace frame 0 is a
     * sidekick-only seed row requiring one native sidekick setup tick before
     * comparison continues. This is replay phase metadata, not a diagnostic
     * aux-event stream.
     */
    public boolean hasSidekickSeedFramePrelude() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("sidekick_seed_frame_prelude");
    }

    /**
     * Whether trace frame 0 intentionally starts before the first comparable
     * LEVEL-mode row. Replay drives this native prefix from frame 0 instead of
     * jumping to the first level frame.
     */
    public boolean hasPreLevelIntroPrefix() {
        return auxSchemaExtras != null
                && auxSchemaExtras.contains("pre_level_intro_prefix");
    }

    /** Load metadata from a metadata.json file. */
    public static TraceMetadata load(Path metadataFile) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(metadataFile.toFile(), TraceMetadata.class);
    }
}


