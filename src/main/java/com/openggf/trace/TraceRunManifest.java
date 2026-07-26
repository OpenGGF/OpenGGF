package com.openggf.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parsed {@code run_manifest.json} for a multi-segment trace run
 * (spec: docs/superpowers/specs/2026-07-18-multi-stage-trace-runs-design.md).
 * A run bundles ordered per-mode segment trace directories recorded from one
 * shared BK2 movie, plus the transition boundary records between them.
 * Comparison-only: this class is read by replay/validation code and never
 * feeds engine state.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TraceRunManifest(
    @JsonProperty("run_schema") int runSchema,
    @JsonProperty("game") String game,
    @JsonProperty("run_id") String runId,
    @JsonProperty("source_bk2") String sourceBk2,
    @JsonProperty("rom_checksum") String romChecksum,
    @JsonProperty("lua_script_version") String luaScriptVersion,
    @JsonProperty("segments") List<Segment> segments,
    @JsonProperty("transitions") List<Transition> transitions,
    @JsonProperty("expected_movie_end_mode") ExpectedMovieEndMode expectedMovieEndMode
) {

    public static final int SUPPORTED_RUN_SCHEMA = 1;
    public static final Set<String> SEGMENT_KINDS = Set.of("level", "special_stage", "bonus_stage");
    public static final Set<String> ENTRY_KINDS =
        Set.of("giant_ring", "starpost_special", "starpost_bonus", "stage_exit",
            "death_restart", "level_advance");

    public TraceRunManifest {
        if (expectedMovieEndMode == null) {
            expectedMovieEndMode = ExpectedMovieEndMode.UNSPECIFIED;
        }
    }

    /** Source-compatible constructor for manifests created before terminal mode was recorded. */
    public TraceRunManifest(
            int runSchema, String game, String runId, String sourceBk2,
            String romChecksum, String luaScriptVersion, List<Segment> segments,
            List<Transition> transitions) {
        this(runSchema, game, runId, sourceBk2, romChecksum, luaScriptVersion,
                segments, transitions, ExpectedMovieEndMode.UNSPECIFIED);
    }

    /**
     * Terminal mode sampled by the recorder at movie completion. Its wire form
     * is deliberately lowercase and optional: missing data disables terminal
     * tail playback rather than inferring a lifecycle from the game.
     */
    public enum ExpectedMovieEndMode {
        UNSPECIFIED,
        LEVEL,
        TITLE_SCREEN;

        @JsonCreator
        public static ExpectedMovieEndMode fromJson(String wireValue) {
            return switch (wireValue) {
                case "level" -> LEVEL;
                case "title_screen" -> TITLE_SCREEN;
                default -> throw new IllegalArgumentException(
                        "Unknown expected_movie_end_mode '" + wireValue + "'");
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(
        @JsonProperty("dir") String dir,
        @JsonProperty("kind") String kind,
        @JsonProperty("trace_profile") String traceProfile,
        @JsonProperty("bk2_frame_offset") int bk2FrameOffset,
        @JsonProperty("trace_frame_count") int traceFrameCount,
        @JsonProperty("zone_id") Integer zoneId,
        @JsonProperty("act") Integer act,
        @JsonProperty("special_stage_index") Integer specialStageIndex,
        @JsonProperty("bonus_stage_type") String bonusStageType
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Transition(
        @JsonProperty("from_segment") int fromSegment,
        @JsonProperty("to_segment") int toSegment,
        @JsonProperty("entry_kind") String entryKind,
        @JsonProperty("mode_change_bk2_frame") int modeChangeBk2Frame,
        @JsonProperty("special_bonus_entry_flag") Integer specialBonusEntryFlag,
        @JsonProperty("saved_x_pos") Integer savedXPos,
        @JsonProperty("saved_y_pos") Integer savedYPos,
        @JsonProperty("last_star_post_hit") Integer lastStarPostHit,
        @JsonProperty("rings_before") Integer ringsBefore,
        @JsonProperty("rings_after") Integer ringsAfter,
        @JsonProperty("emeralds_before") Integer emeraldsBefore,
        @JsonProperty("emeralds_after") Integer emeraldsAfter
    ) {}

    public static TraceRunManifest load(Path manifestPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.readString(manifestPath));
        JsonNode expectedEndMode = root.get("expected_movie_end_mode");
        if (expectedEndMode != null && !expectedEndMode.isTextual()) {
            throw new IOException("expected_movie_end_mode must be a string when present");
        }
        return mapper.treeToValue(root, TraceRunManifest.class);
    }

    /**
     * Structural validation against the run directory. Throws
     * {@link IllegalStateException} naming the first violation.
     */
    public void validate(Path runDir) {
        if (runSchema != SUPPORTED_RUN_SCHEMA) {
            throw new IllegalStateException("Unsupported run_schema " + runSchema);
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalStateException("Manifest has no segments");
        }
        int previousOffset = -1;
        Set<String> segmentDirs = new HashSet<>();
        for (int i = 0; i < segments.size(); i++) {
            Segment seg = segments.get(i);
            if (!SEGMENT_KINDS.contains(seg.kind())) {
                throw new IllegalStateException(
                    "Segment " + i + " has unknown kind '" + seg.kind() + "'");
            }
            if (seg.bk2FrameOffset() <= previousOffset) {
                throw new IllegalStateException(
                    "Segment " + i + " bk2_frame_offset " + seg.bk2FrameOffset()
                        + " is not strictly increasing");
            }
            previousOffset = seg.bk2FrameOffset();
            if ("bonus_stage".equals(seg.kind()) && seg.bonusStageType() == null) {
                throw new IllegalStateException(
                    "Segment " + i + " is bonus_stage but has no bonus_stage_type");
            }
            if ("special_stage".equals(seg.kind()) && seg.specialStageIndex() == null) {
                throw new IllegalStateException(
                    "Segment " + i + " is special_stage but has no special_stage_index");
            }
            if (!segmentDirs.add(seg.dir())) {
                throw new IllegalStateException(
                    "Segment " + i + " has duplicate segment directory '" + seg.dir() + "'");
            }
            Path segDir = runDir.resolve(seg.dir());
            if (!Files.isDirectory(segDir) || !Files.exists(segDir.resolve("metadata.json"))) {
                throw new IllegalStateException(
                    "Segment " + i + " directory missing or lacks metadata.json: " + seg.dir());
            }
        }
        if (transitions != null) {
            for (int i = 0; i < transitions.size(); i++) {
                Transition t = transitions.get(i);
                if (!ENTRY_KINDS.contains(t.entryKind())) {
                    throw new IllegalStateException(
                        "Transition " + i + " has unknown entry_kind '" + t.entryKind() + "'");
                }
                if (t.fromSegment() < 0 || t.fromSegment() >= segments.size()) {
                    throw new IllegalStateException(
                        "Transition " + i + " from_segment out of range: " + t.fromSegment());
                }
                if (t.toSegment() != t.fromSegment() + 1 || t.toSegment() >= segments.size()) {
                    throw new IllegalStateException(
                        "Transition " + i + " to_segment invalid: " + t.toSegment());
                }
            }
        }
    }
}
