package com.openggf.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parsed {@code run_manifest.json} for a multi-segment trace run
 * (spec: docs/architecture/designs/2026-07-18-multi-stage-trace-runs-design.md).
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
    @JsonProperty("dynamic_art_gap_transitions")
        List<DynamicArtTransfer.GapTransition> dynamicArtGapTransitions,
    @JsonProperty("expected_movie_end_mode") ExpectedMovieEndMode expectedMovieEndMode
) {

    public static final int SUPPORTED_RUN_SCHEMA = 2;
    public static final Set<String> SEGMENT_KINDS = Set.of("level", "special_stage", "bonus_stage");
    public static final Set<String> ENTRY_KINDS =
        Set.of("giant_ring", "starpost_special", "starpost_bonus", "stage_exit",
            "death_restart", "level_advance");

    public TraceRunManifest {
        dynamicArtGapTransitions = dynamicArtGapTransitions == null
                ? List.of()
                : List.copyOf(dynamicArtGapTransitions);
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
                segments, transitions, List.of(),
                ExpectedMovieEndMode.UNSPECIFIED);
    }

    /** Source-compatible constructor for pre-schema-2 callers with terminal mode. */
    public TraceRunManifest(
            int runSchema, String game, String runId, String sourceBk2,
            String romChecksum, String luaScriptVersion, List<Segment> segments,
            List<Transition> transitions,
            ExpectedMovieEndMode expectedMovieEndMode) {
        this(runSchema, game, runId, sourceBk2, romChecksum, luaScriptVersion,
                segments, transitions, List.of(), expectedMovieEndMode);
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
        @JsonProperty("bonus_stage_type") String bonusStageType,
        @JsonProperty("dynamic_art_initial_ledger_descriptors")
            List<DynamicArtTransfer.Descriptor> dynamicArtInitialLedgerDescriptors,
        @JsonProperty("dynamic_art_initial_ledger_fingerprint")
            String dynamicArtInitialLedgerFingerprint
    ) {
        public Segment {
            dynamicArtInitialLedgerDescriptors =
                    dynamicArtInitialLedgerDescriptors == null
                            ? List.of()
                            : List.copyOf(dynamicArtInitialLedgerDescriptors);
            if (dynamicArtInitialLedgerFingerprint == null
                    && dynamicArtInitialLedgerDescriptors.isEmpty()) {
                dynamicArtInitialLedgerFingerprint =
                        DynamicArtTransfer.ledgerHash(List.of());
            }
        }

        public Segment(
                String dir, String kind, String traceProfile,
                int bk2FrameOffset, int traceFrameCount,
                Integer zoneId, Integer act, Integer specialStageIndex,
                String bonusStageType) {
            this(dir, kind, traceProfile, bk2FrameOffset, traceFrameCount,
                    zoneId, act, specialStageIndex, bonusStageType,
                    List.of(), null);
        }
    }

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
        JsonNode schemaNode = root.get("run_schema");
        if (schemaNode == null || !schemaNode.isIntegralNumber()
                || !schemaNode.canConvertToInt()) {
            throw new IOException("run_schema must be an integer");
        }
        int schema = schemaNode.asInt();
        JsonNode gapNode = root.get("dynamic_art_gap_transitions");
        if (schema == 2 && (gapNode == null || !gapNode.isArray())) {
            throw new IOException(
                    "run_schema 2 requires dynamic_art_gap_transitions array");
        }
        if (schema == 1 && gapNode != null) {
            throw new IOException(
                    "run_schema 1 must omit dynamic_art_gap_transitions");
        }
        List<DynamicArtTransfer.GapTransition> gaps = new ArrayList<>();
        if (gapNode != null) {
            try {
                for (JsonNode transition : gapNode) {
                    gaps.add(DynamicArtTransfer.parseGapTransition(transition));
                }
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid dynamic_art_gap_transitions", e);
            }
        }
        com.fasterxml.jackson.databind.node.ObjectNode base =
                ((com.fasterxml.jackson.databind.node.ObjectNode) root.deepCopy());
        base.remove("dynamic_art_gap_transitions");
        TraceRunManifest parsed = mapper.treeToValue(base, TraceRunManifest.class);
        return new TraceRunManifest(parsed.runSchema(), parsed.game(),
                parsed.runId(), parsed.sourceBk2(), parsed.romChecksum(),
                parsed.luaScriptVersion(), parsed.segments(), parsed.transitions(),
                gaps, parsed.expectedMovieEndMode());
    }

    /**
     * Structural validation against the run directory. Throws
     * {@link IllegalStateException} naming the first violation.
     */
    public void validate(Path runDir) {
        if (runSchema < 1 || runSchema > SUPPORTED_RUN_SCHEMA) {
            throw new IllegalStateException("Unsupported run_schema " + runSchema);
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalStateException("Manifest has no segments");
        }
        int previousOffset = -1;
        int previousEnd = -1;
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
            if (seg.traceFrameCount() < 0) {
                throw new IllegalStateException(
                        "Segment " + i + " has negative trace_frame_count");
            }
            if (seg.bk2FrameOffset() < previousEnd) {
                throw new IllegalStateException(
                        "Segment " + i + " overlaps the preceding segment range");
            }
            previousOffset = seg.bk2FrameOffset();
            previousEnd = Math.addExact(
                    seg.bk2FrameOffset(), seg.traceFrameCount());
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
            Set<Integer> fromSegments = new HashSet<>();
            int previousFrom = -1;
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
                if (t.fromSegment() <= previousFrom
                        || !fromSegments.add(t.fromSegment())) {
                    throw new IllegalStateException(
                            "Transition " + i
                                    + " does not preserve unique segment adjacency");
                }
                previousFrom = t.fromSegment();
            }
        }
    }

    public List<DynamicArtTransfer.Descriptor> validateDynamicArtGaps(
            List<DynamicArtTransfer.Descriptor> openingLedger,
            boolean requireEmptyPostGap) {
        return validateDynamicArtGaps(
                openingLedger, requireEmptyPostGap,
                new DynamicArtTransfer.LifecycleIdentity());
    }

    private List<DynamicArtTransfer.Descriptor> validateDynamicArtGaps(
            List<DynamicArtTransfer.Descriptor> openingLedger,
            boolean requireEmptyPostGap,
            DynamicArtTransfer.LifecycleIdentity identity) {
        if (runSchema == 1) {
            if (!dynamicArtGapTransitions.isEmpty()) {
                throw new IllegalStateException(
                        "legacy run schema cannot carry dynamic-art gaps");
            }
            return List.copyOf(openingLedger);
        }
        List<DynamicArtTransfer.Descriptor> terminal;
        try {
            terminal = DynamicArtTransfer.validateGaps(
                    dynamicArtGapTransitions, openingLedger, game,
                    identity);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid dynamic-art gap lifecycle", e);
        }
        if (requireEmptyPostGap && !terminal.isEmpty()) {
            throw new IllegalStateException(
                    "dynamic-art post-gap ledger must be empty");
        }
        return terminal;
    }

    /**
     * Validates segment/gap adjacency with one run-wide edge-ordinal identity
     * set. Each segment has already been validated from an empty arm by its
     * loader; its terminal ledger may flow only through the following movie
     * gap and must be empty before the next segment begins.
     */
    public void validateDynamicArtRun(List<TraceData> traces) {
        if (runSchema == 1) {
            return;
        }
        if (traces.size() != segments.size()) {
            throw new IllegalStateException(
                    "dynamic-art run trace count does not match segments");
        }
        for (TraceData trace : traces) {
            if (!trace.metadata().hasPerFrameDynamicArtTransferState()) {
                throw new IllegalStateException(
                        "run_schema 2 segment omits dynamic-art capability");
            }
        }

        DynamicArtTransfer.LifecycleIdentity identity =
                new DynamicArtTransfer.LifecycleIdentity();
        int gapIndex = 0;
        List<DynamicArtTransfer.Descriptor> opening = List.of();
        int firstOffset = segments.getFirst().bk2FrameOffset();
        List<DynamicArtTransfer.GapTransition> beforeFirst = new ArrayList<>();
        while (gapIndex < dynamicArtGapTransitions.size()
                && dynamicArtGapTransitions.get(gapIndex).dynamicArtGapEdge()
                        .movieLogicalFrame() < firstOffset) {
            beforeFirst.add(dynamicArtGapTransitions.get(gapIndex++));
        }
        opening = validateGapSlice(beforeFirst, opening, false, identity);

        for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
            Segment segment = segments.get(segmentIndex);
            List<DynamicArtTransfer.Descriptor> declared =
                    segment.dynamicArtInitialLedgerDescriptors();
            if ("s1".equals(game) && !declared.isEmpty()) {
                throw new IllegalStateException(
                        "S1 run segments cannot declare a submitted initial ledger");
            }
            if (!DynamicArtTransfer.ledgerHash(declared).equals(
                    segment.dynamicArtInitialLedgerFingerprint())) {
                throw new IllegalStateException(
                        "dynamic-art initial ledger fingerprint mismatch");
            }
            if (!descriptorsMatch(opening, declared)) {
                throw new IllegalStateException(
                        "dynamic-art post-gap ledger does not match next segment initial ledger");
            }
            try {
                opening = traces.get(segmentIndex)
                        .validateDynamicArtLifecycle(identity, declared);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "Invalid dynamic-art segment lifecycle", e);
            }
            int segmentEnd = Math.addExact(
                    segment.bk2FrameOffset(), segment.traceFrameCount());
            int nextOffset = segmentIndex + 1 < segments.size()
                    ? segments.get(segmentIndex + 1).bk2FrameOffset()
                    : Integer.MAX_VALUE;
            List<DynamicArtTransfer.GapTransition> slice = new ArrayList<>();
            while (gapIndex < dynamicArtGapTransitions.size()
                    && dynamicArtGapTransitions.get(gapIndex)
                            .dynamicArtGapEdge().movieLogicalFrame() < nextOffset) {
                DynamicArtTransfer.GapTransition transition =
                        dynamicArtGapTransitions.get(gapIndex++);
                if (transition.dynamicArtGapEdge().movieLogicalFrame()
                        < segmentEnd) {
                    throw new IllegalStateException(
                            "dynamic-art gap edge is not adjacent to its segment gap");
                }
                slice.add(transition);
            }
            boolean nextSegmentArms = segmentIndex + 1 < segments.size();
            opening = validateGapSlice(slice, opening, false, identity);
        }
        if (gapIndex != dynamicArtGapTransitions.size()) {
            throw new IllegalStateException(
                    "dynamic-art gap transition lies beyond the run segment order");
        }
    }

    private static boolean descriptorsMatch(
            List<DynamicArtTransfer.Descriptor> expected,
            List<DynamicArtTransfer.Descriptor> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }
        for (int index = 0; index < expected.size(); index++) {
            if (!expected.get(index).fingerprint()
                    .equals(actual.get(index).fingerprint())) {
                return false;
            }
        }
        return true;
    }

    private List<DynamicArtTransfer.Descriptor> validateGapSlice(
            List<DynamicArtTransfer.GapTransition> transitions,
            List<DynamicArtTransfer.Descriptor> opening,
            boolean requireEmpty,
            DynamicArtTransfer.LifecycleIdentity identity) {
        List<DynamicArtTransfer.Descriptor> terminal;
        try {
            terminal = DynamicArtTransfer.validateGaps(
                    transitions, opening, game, identity);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid dynamic-art run lifecycle", e);
        }
        if (requireEmpty && !terminal.isEmpty()) {
            throw new IllegalStateException(
                    "dynamic-art post-gap ledger must be empty before segment arm");
        }
        return terminal;
    }
}
