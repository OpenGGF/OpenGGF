package com.openggf.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.trace.DivergenceReport.ReplayGate;
import com.openggf.trace.DivergenceReport.CarriedEntryDiagnostics;
import com.openggf.trace.TraceFiles;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceVerificationScope;
import com.openggf.trace.VerificationGroup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Trace Triage Assistant (Option 5 of {@code docs/agent-workflow/support-options.md}).
 *
 * <p>Reads existing trace-replay report artifacts that the engine already
 * produces and prints a short FIRST-DIVERGENCE brief. It performs no ROM run
 * and never mutates engine state. A saved report does not establish that a
 * current test executed or that its process completed successfully.
 *
 * <p>Start with the exact {@code triage:} command printed by a replay failure,
 * or by a replay run with {@code -Dtrace.print.summary=true}. Its explicit
 * {@code --report} and {@code --fixture} paths identify the actual allocation
 * and compressed fixture auxiliaries; the context table is a report sibling.
 * An optional {@code --aux <path>} overrides auxiliary evidence.
 *
 * <p>Usage (substitute the emitted paths):
 * <pre>
 *   python3 tools/testing/maven_queue.py -Dmse=off exec:java -Dexec.mainClass=com.openggf.tools.TraceTriageTool '-Dexec.args=--report "target/trace-reports/actual/report.json" --fixture "src/test/resources/traces/actual"'
 * </pre>
 *
 * <p>Legacy {@code s2 mtz1} lookup remains supported under
 * {@code openggf.trace.reports} (default {@code target/trace-reports}); prefer
 * the emitted explicit route when reports have unique nested allocations.
 *
 * <p>The report-parsing, first-divergence, and subsystem-classification logic
 * lives in pure static methods that operate on a parsed JSON string, so they
 * are unit-testable from a synthetic report fixture with no ROM and no OpenGL.
 */
public final class TraceTriageTool {

    /** Default directory that the engine writes trace reports into. */
    public static final String DEFAULT_REPORT_DIR = "target/trace-reports";

    private TraceTriageTool() {
    }

    // ------------------------------------------------------------------
    // Parsed model
    // ------------------------------------------------------------------

    /**
     * A published error/warning group. Its verification group may be unknown
     * in legacy evidence; only ALL or absent-gate diagnosis accepts that case.
     */
    public record Divergence(
            String field,
            String severity,
            int startFrame,
            int endFrame,
            int frameSpan,
            String expectedAtStart,
            String actualAtStart,
            boolean cascading,
            VerificationGroup verificationGroup) {
    }

    /** A bootstrap (frame-0) divergence lifted from the report's {@code bootstrap} array. */
    public record Bootstrap(
            String field,
            String severity,
            String expected,
            String actual,
            String context) {
    }

    /** The gate's diagnostic view; the parsed report keeps its global evidence. */
    private record DiagnosticSelection(
            TraceVerificationScope scope, Bootstrap bootstrapError, Divergence divergence) {
        boolean hasEvidence() {
            return bootstrapError != null || divergence != null;
        }

        boolean hasHardError() {
            return bootstrapError != null || divergence != null && "ERROR".equals(divergence.severity());
        }
    }

    /** Immutable view of the published global evidence, before diagnostic scope selection. */
    public record TraceReport(
            int errorCount,
            int warningCount,
            int totalFrames,
            String summary,
            List<Bootstrap> bootstrap,
            List<Divergence> errors,
            List<Divergence> warnings,
            ReplayGate replayGate,
            CarriedEntryDiagnostics carriedEntryDiagnostics,
            List<String> missingAdvertisedAuxSchemas) {

        /**
         * The global earliest per-frame hard error, otherwise the earliest
         * warning. Raw evidence readers do not inherit the CLI's selected scope.
         */
        public Divergence firstDivergence() {
            return firstDivergence(TraceVerificationScope.ALL);
        }

        private Divergence firstDivergence(TraceVerificationScope scope) {
            Divergence best = earliest(errors, scope);
            if (best != null) {
                return best;
            }
            return earliest(warnings, scope);
        }

        private static Divergence earliest(List<Divergence> groups, TraceVerificationScope scope) {
            Divergence best = null;
            for (Divergence d : groups) {
                if (scope.includes(d.verificationGroup())
                        && (best == null || d.startFrame() < best.startFrame())) {
                    best = d;
                }
            }
            return best;
        }

        /** The global first bootstrap ERROR, if present (frame-0 prelude failure). */
        public Bootstrap firstBootstrapError() {
            for (Bootstrap b : bootstrap) {
                if ("ERROR".equalsIgnoreCase(b.severity())) {
                    return b;
                }
            }
            return null;
        }

        private DiagnosticSelection diagnosticSelection() {
            TraceVerificationScope scope = replayGate == null
                    ? TraceVerificationScope.ALL : replayGate.verification();
            // Bootstrap discrepancies describe the physics/prelude boundary.
            Bootstrap boot = scope.includes(VerificationGroup.PHYSICS) ? firstBootstrapError() : null;
            return new DiagnosticSelection(scope, boot, firstDivergence(scope));
        }
    }

    // ------------------------------------------------------------------
    // Pure parsing
    // ------------------------------------------------------------------

    /**
     * Parses a trace report JSON string into a {@link TraceReport}. Pure: no
     * file IO, no ROM, no engine state. Uses the project's existing Jackson
     * dependency rather than introducing a new JSON library.
     *
     * @param json the raw report JSON (schema from {@code DivergenceReport.toJson()})
     * @return the parsed model
     * @throws IOException if the JSON is malformed
     */
    public static TraceReport parseReport(String json) throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject() || root.has("error")) {
            throw new IOException("Expected a DivergenceReport object, not a producer error or another report schema");
        }

        int errorCount = requiredCount(root, "error_count");
        int warningCount = requiredCount(root, "warning_count");
        int totalFrames = requiredCount(root, "total_frames");
        String summary = requiredText(root, "summary");

        List<Bootstrap> bootstrap = new ArrayList<>();
        for (JsonNode n : requiredArray(root, "bootstrap")) {
            bootstrap.add(new Bootstrap(
                    requiredText(n, "field"),
                    requiredSeverity(n),
                    requiredText(n, "expected"),
                    requiredText(n, "actual"),
                    requiredText(n, "context")));
        }

        List<Divergence> errors = parseGroups(requiredArray(root, "errors"), "ERROR");
        List<Divergence> warnings = parseGroups(requiredArray(root, "warnings"), "WARNING");
        boolean hasErrors = !errors.isEmpty()
                || bootstrap.stream().anyMatch(entry -> "ERROR".equals(entry.severity()));
        boolean hasWarnings = !warnings.isEmpty()
                || bootstrap.stream().anyMatch(entry -> "WARNING".equals(entry.severity()));
        if ((errorCount > 0) != hasErrors || (warningCount > 0) != hasWarnings) {
            throw new IOException("Divergence counts disagree with the published error/warning groups");
        }

        // Optional reference diagnostics are best effort; primary fields and
        // a present replay gate retain their strict wire validation.
        List<String> missingAuxSchemas = new ArrayList<>();
        JsonNode missingAuxNode = root.get("missing_advertised_aux_schemas");
        if (missingAuxNode != null && missingAuxNode.isArray()) {
            for (JsonNode schema : missingAuxNode) {
                if (schema.isTextual() && !schema.textValue().isBlank()) {
                    missingAuxSchemas.add(schema.textValue());
                }
            }
        }

        ReplayGate gate = ReplayGate.readOptionalFrom(root);
        if (gate != null && gate.verification() != TraceVerificationScope.ALL) {
            requireClassifiedGroups(errors);
            requireClassifiedGroups(warnings);
        }
        return new TraceReport(errorCount, warningCount, totalFrames, summary,
                List.copyOf(bootstrap), errors, warnings, gate, CarriedEntryDiagnostics.readOptionalFrom(root),
                List.copyOf(missingAuxSchemas));
    }

    private static List<Divergence> parseGroups(JsonNode array, String severity) throws IOException {
        List<Divergence> out = new ArrayList<>();
        for (JsonNode n : array) {
            if (!severity.equals(requiredSeverity(n))) {
                throw new IOException("Divergence severity must match its " + severity + " group");
            }
            int startFrame = requiredCount(n, "start_frame");
            int endFrame = requiredCount(n, "end_frame");
            int frameSpan = requiredCount(n, "frame_span");
            if (endFrame < startFrame || frameSpan == 0) {
                throw new IOException("Divergence must have an ordered, nonempty frame span");
            }
            out.add(new Divergence(
                    requiredText(n, "field"), severity, startFrame, endFrame, frameSpan,
                    requiredText(n, "expected_at_start"),
                    requiredText(n, "actual_at_start"),
                    requiredBoolean(n, "cascading"), reportedVerificationGroup(n)));
        }
        return List.copyOf(out);
    }

    private static VerificationGroup reportedVerificationGroup(JsonNode group) {
        JsonNode value = group.get("verification_group");
        if (value == null || !value.isTextual()) {
            return null;
        }
        return switch (value.textValue()) {
            case "physics" -> VerificationGroup.PHYSICS;
            case "animation" -> VerificationGroup.ANIMATION;
            default -> null;
        };
    }

    private static void requireClassifiedGroups(List<Divergence> groups) throws IOException {
        for (Divergence group : groups) {
            if (group.verificationGroup() == null) {
                // ALL/absent-gate reports historically accepted unclassified groups.
                // A narrow diagnosis cannot safely call that evidence out of scope.
                throw new IOException("Narrow replay verification requires canonical verification_group "
                        + "'physics' or 'animation' for field '" + group.field() + "'");
            }
        }
    }

    private static JsonNode requiredArray(JsonNode parent, String field) throws IOException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw new IOException("DivergenceReport requires array '" + field + "'");
        }
        return value;
    }

    private static int requiredCount(JsonNode parent, String field) throws IOException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IOException("DivergenceReport requires nonnegative integer '" + field + "'");
        }
        return value.intValue();
    }

    private static String requiredText(JsonNode parent, String field) throws IOException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw new IOException("DivergenceReport requires string '" + field + "'");
        }
        return value.textValue();
    }

    private static boolean requiredBoolean(JsonNode parent, String field) throws IOException {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IOException("DivergenceReport requires boolean '" + field + "'");
        }
        return value.booleanValue();
    }

    private static String requiredSeverity(JsonNode parent) throws IOException {
        String severity = requiredText(parent, "severity");
        if (!"ERROR".equals(severity) && !"WARNING".equals(severity)) {
            throw new IOException("Unsupported divergence severity: " + severity);
        }
        return severity;
    }

    // ------------------------------------------------------------------
    // Pure classification
    // ------------------------------------------------------------------

    /** Likely owning subsystem for a divergent field. */
    public enum Subsystem {
        PLAYER_PHYSICS("player physics"),
        PLAYABLE_ANIMATION("playable animation"),
        OBJECT_SOLID("object solid"),
        TOUCH_RESPONSE("touch response"),
        EVENT("event"),
        SIDEKICK("sidekick"),
        PALETTE("palette"),
        LAYOUT_MUTATION("layout mutation"),
        ART_PLC("art/PLC"),
        TEST_BOOTSTRAP("test bootstrap"),
        UNKNOWN("unknown");

        private final String label;

        Subsystem(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Classifies the likely owning subsystem from a field name and an optional
     * bootstrap hint. Pure string logic, no ROM/engine. Field names follow the
     * recorder schema (see {@code TraceBinder.compareFrame()} and
     * {@code BootstrapDivergence}).
     *
     * @param field         the divergent field label (e.g. {@code "tails_x"}, {@code "angle"})
     * @param isBootstrap   whether the field came from the bootstrap array
     * @return the best-guess subsystem
     */
    public static Subsystem classifySubsystem(String field, boolean isBootstrap) {
        if (field == null) {
            return Subsystem.UNKNOWN;
        }
        String f = field.toLowerCase(Locale.ROOT);

        // Frame-0 prelude / ring-buffer / cpu-view setup is a test-bootstrap concern.
        if (isBootstrap) {
            if (f.startsWith("player_history") || f.contains("history.pos")
                    || f.contains("rng") || f.contains("seed")
                    || f.contains("oscill") || f.contains("frame_counter")
                    || f.contains("game_mode")) {
                return Subsystem.TEST_BOOTSTRAP;
            }
        }

        // Animation fields are independently gated and must be classified
        // before the sidekick prefix catches Tails animation mismatches.
        if (f.endsWith("animation_id") || f.endsWith("mapping_frame")
                || f.endsWith("animation_present")) {
            return Subsystem.PLAYABLE_ANIMATION;
        }

        // Sidekick (Tails / second player) fields.
        if (f.startsWith("tails") || f.contains("sidekick") || f.contains("cpu")) {
            return Subsystem.SIDEKICK;
        }

        // Object slot / routine / subtype fields.
        if (f.startsWith("object_slot") || f.contains("object_slot")
                || f.contains("routine") || f.contains("subtype")) {
            // A slot whose presence/absence diverges is most often a solid /
            // spawn-windowing problem; touch-specific fields handled below.
            if (f.contains("touch") || f.contains("hurt") || f.contains("bounce")) {
                return Subsystem.TOUCH_RESPONSE;
            }
            return Subsystem.OBJECT_SOLID;
        }

        // Touch-response (enemy bounce / hurt / collide).
        if (f.contains("touch") || f.contains("hurt") || f.contains("bounce")
                || f.contains("invuln") || f.contains("rings")
                || f.contains("lives")) {
            // A life count that diverges is a death the engine did or did not
            // take, which sits in the same causal family as ring loss.
            return Subsystem.TOUCH_RESPONSE;
        }

        // Palette.
        if (f.contains("palette") || f.contains("pal_") || f.contains("cram")
                || f.contains("color") || f.contains("colour")) {
            return Subsystem.PALETTE;
        }

        // Layout / tilemap mutation.
        if (f.contains("layout") || f.contains("tilemap") || f.contains("chunk")
                || f.contains("block") || f.contains("map")) {
            return Subsystem.LAYOUT_MUTATION;
        }

        // Art / PLC / pattern fields.
        if (f.contains("plc") || f.contains("art") || f.contains("pattern")
                || f.contains("dplc") || f.contains("vram")) {
            return Subsystem.ART_PLC;
        }

        // Event / camera / zone-act / boss-arena flow.
        if (f.startsWith("camera") || f.contains("camera")
                || f.contains("zone") || f.contains("act")
                || f.contains("boundary") || f.contains("event")
                || f.contains("boss")) {
            return Subsystem.EVENT;
        }

        // Player physics — position / speed / angle / air / rolling / ground mode.
        if (f.equals("x") || f.equals("y")
                || f.equals("x_speed") || f.equals("y_speed") || f.equals("g_speed")
                || f.equals("angle") || f.equals("air") || f.equals("rolling")
                || f.equals("ground_mode")
                || f.endsWith("_speed") || f.endsWith("_angle")) {
            return Subsystem.PLAYER_PHYSICS;
        }

        return Subsystem.UNKNOWN;
    }

    /**
     * Suggests disassembly search terms for a divergent field. Pure string
     * logic. Returned terms are intended for {@code RomOffsetFinder} / disasm
     * label search, not exact addresses.
     */
    public static List<String> suggestDisasmSearchTerms(String field, Subsystem subsystem) {
        Set<String> terms = new LinkedHashSet<>();
        switch (subsystem) {
            case PLAYER_PHYSICS -> {
                terms.add("Sonic_Move");
                terms.add("Obj01");
                terms.add("Player_Subroutines");
                terms.add("ChkRoll");
            }
            case PLAYABLE_ANIMATION -> {
                terms.add("Animate_Sonic");
                terms.add("AnimateSprite");
                terms.add("mapping_frame");
                terms.add("anim_frame");
            }
            case SIDEKICK -> {
                terms.add("Obj02");
                terms.add("Tails_");
                terms.add("Obj_Tails");
                terms.add("CtrlData_");
            }
            case OBJECT_SOLID -> {
                terms.add("SolidObject");
                terms.add("PlatformObject");
                terms.add("MvSonicOnPtfm");
            }
            case TOUCH_RESPONSE -> {
                terms.add("Touch_Response");
                terms.add("Touch_Loop");
                terms.add("HurtSonic");
            }
            case EVENT -> {
                terms.add("Dynamic_Resize");
                terms.add("LevEvents_");
                terms.add("Deform");
            }
            case PALETTE -> {
                terms.add("PalCycle");
                terms.add("Pal_");
                terms.add("AnPal");
            }
            case LAYOUT_MUTATION -> {
                terms.add("AniArt");
                terms.add("Dynamic_");
                terms.add("LoadZoneTiles");
            }
            case ART_PLC -> {
                terms.add("PLC_");
                terms.add("ArtNem_");
                terms.add("ArtKos_");
                terms.add("DPLC_");
            }
            case TEST_BOOTSTRAP -> {
                terms.add("Level_Init");
                terms.add("RandomNumber");
                terms.add("Oscillating_Data");
            }
            default -> {
                // fall through to field-name token below
            }
        }
        if (field != null && !field.isBlank()) {
            terms.add(field);
        }
        return new ArrayList<>(terms);
    }

    /**
     * Suggests focused tests / commands for a divergent field. Pure string
     * logic.
     */
    public static List<String> suggestFocusedTests(Subsystem subsystem) {
        List<String> tests = new ArrayList<>();
        switch (subsystem) {
            case PLAYER_PHYSICS -> {
                tests.add("TestPhysicsProfile");
                tests.add("TestCollisionModel");
                tests.add("TestSpindashGating");
            }
            case PLAYABLE_ANIMATION -> {
                tests.add("TestTraceBinder");
                tests.add("PlayableSpriteAnimation tests");
            }
            case SIDEKICK -> tests.add("TestPhysicsProfile (sidekick profile)");
            case OBJECT_SOLID -> {
                tests.add("TestNoDirectMapMutationsInGameplay");
                tests.add("TestObjectServicesMigrationGuard");
            }
            case TOUCH_RESPONSE -> tests.add("Object touch-response unit test for the involved badnik");
            case EVENT -> tests.add("Zone event-manager test (e.g. *EventsTest)");
            case PALETTE -> tests.add("Sonic3kPaletteCycler / palette-ownership test");
            case LAYOUT_MUTATION -> tests.add("TestNoDirectMapMutationsInGameplay");
            case ART_PLC -> {
                tests.add("TestSonic3kPlcArtRegistry");
                tests.add("TestPatternSpriteRendererCorruptionGuard");
            }
            case TEST_BOOTSTRAP -> tests.add("TestTraceReplayInvariantGuard");
            default -> tests.add("(no specific test guess; run the failing *TraceReplay)");
        }
        tests.add("TestTraceReplayInvariantGuard (comparison-only guard)");
        return tests;
    }

    /**
     * Decides whether {@code docs/status/trace-frontier-log.md} likely needs an
     * update. The frontier log must be updated when a trace frontier moves,
     * passes, regresses, or is used to select the next target. If the report
     * shows errors within the published gate's scope, the selected frontier is
     * failing. Global totals remain available independently of this diagnosis.
     */
    public static boolean frontierLogLikelyNeedsUpdate(TraceReport report) {
        return report.diagnosticSelection().hasHardError();
    }

    // ------------------------------------------------------------------
    // Pure brief rendering
    // ------------------------------------------------------------------

    /**
     * Renders the first-divergence brief from a parsed report plus optional
     * context-text and aux-event excerpts. Pure: takes already-loaded strings,
     * does no IO.
     *
     * @param game            game id (e.g. {@code "s2"}); may be empty/unknown
     * @param zone            zone id (e.g. {@code "mtz1"}); may be empty/unknown
     * @param report          parsed report model
     * @param contextExcerpt  nearby lines from {@code _context.txt}, or empty
     * @param auxExcerpt      nearby lines from {@code aux_state.jsonl}, or empty
     * @return the multi-line brief
     */
    public static String renderBrief(String game,
                                     String zone,
                                     TraceReport report,
                                     String contextExcerpt,
                                     String auxExcerpt) {
        DiagnosticSelection selected = report.diagnosticSelection();
        StringBuilder sb = new StringBuilder();
        sb.append("=== Trace Triage Brief ===\n");
        sb.append("Game/Zone: ").append(blankToDash(game)).append(" / ")
                .append(blankToDash(zone)).append("\n");
        sb.append("Totals: ").append(report.errorCount()).append(" errors, ")
                .append(report.warningCount()).append(" warnings, ")
                .append(report.totalFrames()).append(" frames compared\n");
        ReplayGate gate = report.replayGate();
        if (gate == null) {
            sb.append("Replay completion: UNKNOWN (no replay_gate published).\n");
        } else {
            sb.append("Replay completion: ")
                    .append(gate.comparisonGateReached() ? "COMPLETE" : "INCOMPLETE")
                    .append(" (").append(gate.plannedTraceRows()).append(" planned trace rows; ")
                    .append(gate.verification().name().toLowerCase(Locale.ROOT)).append(" verification).\n");
        }
        if (selected.scope() != TraceVerificationScope.ALL) {
            sb.append("Diagnosis scope: ").append(selected.scope().name().toLowerCase(Locale.ROOT))
                    .append("; totals and report summary cover all verification groups.\n");
        }
        if (report.totalFrames() == 0) {
            sb.append("Comparison coverage: absent (0 frames compared); gameplay comparison coverage is not established.\n");
        }
        if (!report.missingAdvertisedAuxSchemas().isEmpty()) {
            sb.append("Reference/coverage limits: missing advertised aux schemas: ")
                    .append(String.join(", ", report.missingAdvertisedAuxSchemas())).append(".\n");
            sb.append("Aux reference evidence is limited; replay gate and mismatch totals remain as published.\n");
        }
        CarriedEntryDiagnostics entry = report.carriedEntryDiagnostics();
        if (entry == null) {
            sb.append("Carried entry diagnostics: not reported (unknown).\n");
        } else {
            sb.append("Carried entry diagnostic evaluation:\n");
            sb.append("Provenance: ").append(entry.provenance() == null ? "unknown" : entry.provenance()).append("\n");
            appendEntryNotes(sb, "Disagreements", entry.disagreements());
            appendEntryNotes(sb, "Unresolved", entry.unresolved());
            sb.append("This evaluation does not establish imported gameplay state, complete provenance or run-chain parity.\n");
        }
        sb.append("Saved report only; check this invocation's Surefire result and process completion.\n");
        sb.append("Report summary: ").append(blankToDash(report.summary())).append("\n");
        sb.append("\n");

        Bootstrap bootErr = selected.bootstrapError();
        if (bootErr != null) {
            Subsystem bootSub = classifySubsystem(bootErr.field(), true);
            sb.append("BOOTSTRAP (frame 0) ERROR present:\n");
            sb.append("  field   : ").append(bootErr.field()).append("\n");
            sb.append("  ROM     : ").append(bootErr.expected()).append("\n");
            sb.append("  engine  : ").append(bootErr.actual()).append("\n");
            sb.append("  context : ").append(bootErr.context()).append("\n");
            sb.append("  likely owner: ").append(bootSub.label()).append("\n");
            sb.append("  NOTE: a frame-0 prelude failure usually means the engine did not reach\n");
            sb.append("        the correct natural start state. Fix the simulated prelude --\n");
            sb.append("        do NOT hydrate engine state from the trace to paper over it.\n");
            sb.append("\n");
        }

        Divergence first = selected.divergence();
        if (first == null) {
            sb.append("PER-FRAME DIVERGENCE: none ");
            if (selected.scope() != TraceVerificationScope.ALL) {
                sb.append("for ").append(selected.scope().name().toLowerCase(Locale.ROOT))
                        .append(" verification ");
            }
            sb.append("in ").append(report.totalFrames()).append(" compared frames.\n");
            sb.append("\n");
        } else {
            Subsystem sub = classifySubsystem(first.field(), false);
            sb.append("FIRST DIVERGENCE");
            if (selected.scope() != TraceVerificationScope.ALL) {
                sb.append(" (").append(selected.scope().name().toLowerCase(Locale.ROOT))
                        .append(" verification)");
            }
            sb.append('\n');
            sb.append("  frame    : ").append(first.startFrame())
                    .append(" (span ").append(first.frameSpan())
                    .append(", ends ").append(first.endFrame()).append(")\n");
            sb.append("  field    : ").append(first.field())
                    .append("  [").append(first.severity()).append("]\n");
            sb.append("  ROM      : ").append(first.expectedAtStart()).append("\n");
            sb.append("  engine   : ").append(first.actualAtStart()).append("\n");
            sb.append("  cascading: ").append(first.cascading()).append("\n");
            sb.append("  likely owning subsystem: ").append(sub.label()).append("\n");
            sb.append("\n");

            sb.append("Suggested disassembly search terms (RomOffsetFinder / disasm label search):\n");
            for (String t : suggestDisasmSearchTerms(first.field(), sub)) {
                sb.append("  - ").append(t).append("\n");
            }
            sb.append("\n");

            sb.append("Suggested focused tests:\n");
            for (String t : suggestFocusedTests(sub)) {
                sb.append("  - ").append(t).append("\n");
            }
            sb.append("\n");
        }

        if (contextExcerpt != null && !contextExcerpt.isBlank()) {
            sb.append("Nearby context (").append("_context.txt").append("):\n");
            sb.append(indent(contextExcerpt));
            sb.append("\n");
        }
        if (auxExcerpt != null && !auxExcerpt.isBlank()) {
            sb.append("Nearby aux events (focus row first; object ids/routines/subtypes/positions):\n");
            sb.append(indent(auxExcerpt));
            sb.append("\n");
        }

        sb.append(comparisonOnlyReminder());
        sb.append("\n");
        sb.append("Frontier log update likely needed: ")
                .append(frontierLogLikelyNeedsUpdate(report) ? "YES" : "no")
                .append("  (file: docs/status/trace-frontier-log.md)\n");

        return sb.toString();
    }

    private static void appendEntryNotes(StringBuilder brief, String label, List<String> notes) {
        brief.append(label).append(":");
        if (notes.isEmpty()) {
            brief.append(" none reported\n");
        } else {
            brief.append("\n");
            for (String note : notes) {
                brief.append("  - ").append(note).append("\n");
            }
        }
    }

    private static String comparisonOnlyReminder() {
        return "REMINDER: Trace data is COMPARISON-ONLY. It is read-only diagnostic input.\n"
                + "Never hydrate, sync, or snap engine state from the trace in committed code.\n"
                + "The engine must reproduce ROM behaviour natively from controller input.\n"
                + "(Guard: TestTraceReplayInvariantGuard.)\n";
    }

    private static String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    private static String indent(String block) {
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            sb.append("  | ").append(line).append("\n");
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Context / aux excerpt extraction (pure)
    // ------------------------------------------------------------------

    /**
     * Extracts lines near a frame from a {@code _context.txt} body. Returns at
     * most {@code maxLines} lines whose leading frame number falls within
     * {@code radius} of {@code frame}, plus any ROM:/ENG: diagnostic lines that
     * immediately follow them. Pure: operates on the already-loaded text.
     */
    public static String extractContextNearFrame(String contextText, int frame, int radius, int maxLines) {
        if (contextText == null || contextText.isBlank()) {
            return "";
        }
        String[] lines = contextText.split("\n", -1);
        List<String> out = new ArrayList<>();
        boolean keepingDiag = false;
        for (String line : lines) {
            if (out.size() >= maxLines) {
                break;
            }
            int lf = leadingFrame(line);
            if (lf >= 0) {
                keepingDiag = Math.abs(lf - frame) <= radius;
                if (keepingDiag) {
                    out.add(line.stripTrailing());
                }
            } else if (keepingDiag) {
                String trimmed = line.strip();
                if (trimmed.startsWith("ROM:") || trimmed.startsWith("ENG:")) {
                    out.add("    " + trimmed);
                }
            }
        }
        return String.join("\n", out);
    }

    private static int leadingFrame(String line) {
        if (line == null || line.isEmpty() || !Character.isDigit(line.charAt(0))) {
            return -1;
        }
        int i = 0;
        while (i < line.length() && Character.isDigit(line.charAt(i))) {
            i++;
        }
        try {
            return Integer.parseInt(line.substring(0, i));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Extracts a bounded aux window, reserving space for the actual focus row. */
    public static String extractAuxNearFrame(String auxJsonl, int frame, int radius, int maxLines) {
        if (auxJsonl == null || auxJsonl.isBlank()) {
            return "";
        }
        try (var reader = new BufferedReader(new StringReader(auxJsonl))) {
            return extractAuxNearFrame(reader, frame, radius, maxLines);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Malformed auxiliary evidence", failure);
        }
    }

    static String readAuxNearFrame(Path path, int frame, int radius, int maxLines) throws IOException {
        try (BufferedReader reader = TraceFiles.openReader(path)) {
            return extractAuxNearFrame(reader, frame, radius, maxLines);
        }
    }

    private static String extractAuxNearFrame(BufferedReader reader, int frame, int radius, int maxLines)
            throws IOException {
        if (maxLines <= 0) {
            return "";
        }
        ObjectMapper mapper = new ObjectMapper();
        List<String> focus = new ArrayList<>();
        List<String> nearby = new ArrayList<>();
        String line;
        // Some diagnostic fixtures append observations out of order. Scan once
        // with bounded storage, rather than stopping at the first later frame.
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode event = mapper.readTree(line);
            JsonNode recordedFrame = event.get("frame");
            if (recordedFrame == null || !recordedFrame.isIntegralNumber()
                    || !recordedFrame.canConvertToInt()) {
                continue;
            }
            int auxFrame = recordedFrame.intValue();
            if (auxFrame == frame && focus.size() < maxLines) {
                focus.add(line.strip());
                if (focus.size() == maxLines) {
                    break;
                }
            } else if (Math.abs((long) auxFrame - frame) <= radius && nearby.size() < maxLines) {
                nearby.add(line.strip());
            }
        }
        int remaining = maxLines - focus.size();
        focus.addAll(nearby.subList(0, Math.min(remaining, nearby.size())));
        return String.join("\n", focus);
    }

    // ------------------------------------------------------------------
    // CLI entry point (IO lives here; logic above is pure)
    // ------------------------------------------------------------------

    static String configuredReportDirectory() {
        return System.getProperty("openggf.trace.reports", DEFAULT_REPORT_DIR);
    }

    static Path defaultReportPath(String reportDir, String game, String zone) {
        return Path.of(reportDir, game + "_" + zone + "_report.json");
    }

    static Path defaultContextPath(String reportDir, String game, String zone) {
        return Path.of(reportDir, game + "_" + zone + "_context.txt");
    }

    static Path contextPathForReport(Path reportPath) {
        String filename = reportPath.getFileName().toString();
        String stem = filename.endsWith(".json")
                ? filename.substring(0, filename.length() - ".json".length())
                : filename;
        if (stem.endsWith("_report")) {
            stem = stem.substring(0, stem.length() - "_report".length());
        }
        return reportPath.resolveSibling(stem + "_context.txt");
    }

    static Path resolveDefaultReportPath(String reportDir, String game, String zone)
            throws IOException {
        Path legacy = defaultReportPath(reportDir, game, zone);
        Path reportRoot = Path.of(reportDir);
        if (!Files.isDirectory(reportRoot)) {
            return legacy;
        }
        String prefix = game + "_" + zone + "-";
        List<Path> candidates;
        try (var paths = Files.walk(reportRoot)) {
            candidates = paths.filter(Files::isRegularFile)
                    .filter(path -> !path.getParent().equals(reportRoot))
                    .filter(path -> !path.equals(legacy))
                    .filter(path -> path.getFileName().toString().startsWith(prefix)
                            || path.getFileName().toString().startsWith(
                                    game + "_" + zone + "_"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !path.getFileName().toString().endsWith(".owner.json"))
                    .sorted()
                    .toList();
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        if (candidates.size() > 1) {
            throw new IOException("multiple nested reports match " + game + " " + zone
                    + "; rerun with --report <path>");
        }
        return legacy;
    }

    static Path resolveReportPath(String explicitPath, String reportDir,
            String game, String zone) {
        return explicitPath != null
                ? Path.of(explicitPath)
                : defaultReportPath(reportDir, game, zone);
    }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(String[] args, PrintStream output, PrintStream errors) {
        String game = null;
        String zone = null;
        String reportPath = null;
        String contextPath = null;
        String auxPath = null;
        String fixturePath = null;
        String reportDir = configuredReportDirectory();

        List<String> positionals = new ArrayList<>();
        try {
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--report" -> reportPath = optionValue(args, ++i);
                    case "--context" -> contextPath = optionValue(args, ++i);
                    case "--aux" -> auxPath = optionValue(args, ++i);
                    case "--fixture" -> fixturePath = optionValue(args, ++i);
                    case "--dir" -> reportDir = optionValue(args, ++i);
                    default -> {
                        if (args[i].startsWith("-")) {
                            throw new IllegalArgumentException("Unknown flag: " + args[i]);
                        }
                        positionals.add(args[i]);
                    }
                }
            }
            if (positionals.size() > 2) {
                throw new IllegalArgumentException("Expected at most <game> <zone>");
            }
        } catch (IllegalArgumentException failure) {
            errors.println(failure.getMessage());
            printUsage(errors);
            return 2;
        }

        if (positionals.size() >= 1) {
            game = positionals.get(0).toLowerCase(Locale.ROOT);
        }
        if (positionals.size() >= 2) {
            zone = positionals.get(1).toLowerCase(Locale.ROOT);
        }

        try {
            Path fixture = fixturePath == null ? null : Path.of(fixturePath);
            if (fixture != null) {
                TraceMetadata metadata = TraceMetadata.load(fixture.resolve("metadata.json"));
                if (game == null) {
                    game = metadata.game();
                }
                if (zone == null) {
                    zone = metadata.zone() + metadata.act();
                }
            }
            if (reportPath == null) {
                if (game == null || zone == null) {
                    printUsage(errors);
                    return 2;
                }
                reportPath = resolveDefaultReportPath(reportDir, game, zone).toString();
            }
            Path reportFile = Path.of(reportPath);
            if (!Files.isRegularFile(reportFile)) {
                throw new IOException("Report not found: " + reportFile.toAbsolutePath()
                        + "; run the concrete *TraceReplay test first");
            }
            String json = Files.readString(reportFile, StandardCharsets.UTF_8);
            TraceReport report = parseReport(json);

            DiagnosticSelection selected = report.diagnosticSelection();
            Divergence first = selected.divergence();
            boolean bootstrapError = selected.bootstrapError() != null;
            int focusFrame = bootstrapError || first == null
                    ? 0 : first.startFrame();
            // Aux snapshots use -1 for the pre-trace state; context begins at 0.
            int auxFocusFrame = bootstrapError ? -1 : focusFrame;

            String contextExcerpt = "";
            Path contextFile = contextPath == null
                    ? contextPathForReport(reportFile) : Path.of(contextPath);
            if ((selected.scope() == TraceVerificationScope.ALL || selected.hasEvidence())
                    && (contextPath != null || Files.exists(contextFile))) {
                String contextText = Files.readString(contextFile, StandardCharsets.UTF_8);
                contextExcerpt = extractContextNearFrame(contextText, focusFrame, 2, 12);
            }

            String auxExcerpt = "";
            Path auxFile = auxPath != null ? Path.of(auxPath)
                    : fixture == null ? null : TraceFiles.resolve(fixture, "aux_state.jsonl");
            if (auxFile != null && selected.hasEvidence()) {
                auxExcerpt = readAuxNearFrame(auxFile, auxFocusFrame, 3, 12);
            }

            output.println("Report: " + reportFile);
            if (fixture != null) {
                output.println("Fixture: " + fixture);
            }
            output.print(renderBrief(game, zone, report, contextExcerpt, auxExcerpt));
            return 0;
        } catch (IOException | IllegalArgumentException failure) {
            errors.println("Failed to read diagnostic evidence: " + failure.getMessage());
            return 1;
        }
    }

    private static String optionValue(String[] arguments, int index) {
        if (index >= arguments.length || arguments[index].startsWith("--")) {
            throw new IllegalArgumentException("Missing value for " + arguments[index - 1]);
        }
        return arguments[index];
    }

    private static void printUsage(PrintStream output) {
        output.println("Usage: TraceTriageTool <game> <zone> [--dir <report-root>]");
        output.println("   or: TraceTriageTool --report <path> [--fixture <directory>]");
        output.println("Optional overrides: --context <path> --aux <path>; aux may be gzip-compressed.");
    }
}
