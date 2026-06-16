package com.openggf.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DivergenceReport {

    private final List<FrameComparison> allComparisons;
    private final List<DivergenceGroup> errors;
    private final List<DivergenceGroup> warnings;
    private final List<BootstrapDivergence> bootstrapDivergences;
    private final TraceData traceData;

    public DivergenceReport(List<FrameComparison> comparisons) {
        this(comparisons, null, List.of());
    }

    public DivergenceReport(List<FrameComparison> comparisons, TraceData traceData) {
        this(comparisons, traceData, List.of());
    }

    /**
     * Variant that includes bootstrap (frame-0) divergences detected by
     * {@link TraceBinder#compareBootstrapFrame0(TraceData, EngineSnapshot)}.
     * Bootstrap divergences render ahead of the per-frame block in both
     * JSON and text outputs.
     */
    public DivergenceReport(List<FrameComparison> comparisons,
                            TraceData traceData,
                            List<BootstrapDivergence> bootstrapDivergences) {
        this.allComparisons = List.copyOf(comparisons);
        this.traceData = traceData;
        List<BootstrapDivergence> boot = bootstrapDivergences == null
                ? new ArrayList<>()
                : new ArrayList<>(bootstrapDivergences);
        boot.sort(Comparator.comparingInt(
                (BootstrapDivergence d) -> d.severity() == BootstrapDivergence.Severity.ERROR
                        ? 0 : 1));
        this.bootstrapDivergences = List.copyOf(boot);
        List<DivergenceGroup> allGroups = buildGroups(comparisons);
        this.errors = allGroups.stream()
            .filter(g -> g.severity() == Severity.ERROR)
            .toList();
        this.warnings = allGroups.stream()
            .filter(g -> g.severity() == Severity.WARNING)
            .toList();
    }

    public List<DivergenceGroup> errors() { return errors; }
    public List<DivergenceGroup> warnings() { return warnings; }
    public boolean hasErrors() { return totalErrorCount() > 0; }
    public boolean hasWarnings() { return totalWarningCount() > 0; }

    /** Bootstrap (frame-0) divergences, sorted ERROR-first then WARNING. */
    public List<BootstrapDivergence> bootstrapDivergences() {
        return bootstrapDivergences;
    }

    public boolean hasBootstrapDivergences() {
        return !bootstrapDivergences.isEmpty();
    }

    public String toSummary() {
        return buildSummary(true);
    }

    /**
     * Compact one-line summary for assertion messages and sweep logs.
     * Full ROM/engine diagnostics are still written to JSON and context files
     * by callers; keeping this short avoids duplicating large context windows
     * into Surefire XML and console buffers during full trace sweeps.
     */
    public String toCompactSummary() {
        return buildSummary(false);
    }

    /**
     * Short assertion-only summary for failing trace replay tests. The
     * JSON/context report files carry checkpoint, zone, and diagnostics detail;
     * this string stays small so full sweeps do not flood Surefire XML and
     * console output with repeated context.
     */
    public String toAssertionSummary() {
        int errorCount = totalErrorCount();
        int warningCount = totalWarningCount();

        if (errorCount == 0 && warningCount == 0) {
            return "All frames match trace. No divergences.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(errorCount > 0
                ? "Trace replay diverged. "
                : "Trace replay produced warnings. ");
        sb.append(String.format("Totals: %d error%s, %d warning%s.",
            errorCount, errorCount == 1 ? "" : "s",
            warningCount, warningCount == 1 ? "" : "s"));

        BootstrapDivergence firstBootstrapError =
                firstBootstrapDivergence(BootstrapDivergence.Severity.ERROR);
        if (firstBootstrapError != null) {
            appendBootstrapSummary(sb, "error", firstBootstrapError, false);
            return sb.toString();
        }
        if (!errors.isEmpty()) {
            appendGroupSummary(sb, "error", errors.get(0));
            return sb.toString();
        }

        BootstrapDivergence firstBootstrapWarning =
                firstBootstrapDivergence(BootstrapDivergence.Severity.WARNING);
        if (firstBootstrapWarning != null) {
            appendBootstrapSummary(sb, "warning", firstBootstrapWarning, false);
            return sb.toString();
        }
        if (!warnings.isEmpty()) {
            appendGroupSummary(sb, "warning", warnings.get(0));
        }
        return sb.toString();
    }

    private String buildSummary(boolean includeInlineDiagnostics) {
        int errorCount = totalErrorCount();
        int warningCount = totalWarningCount();

        if (errorCount == 0 && warningCount == 0) {
            return "All frames match trace. No divergences.";
        }

        StringBuilder sb = new StringBuilder();
        int bootstrapErrorCount = bootstrapErrorCount();
        int bootstrapWarningCount = bootstrapWarningCount();
        if (bootstrapErrorCount > 0 || bootstrapWarningCount > 0) {
            sb.append(String.format("%d bootstrap error%s, %d bootstrap warning%s. ",
                bootstrapErrorCount, bootstrapErrorCount == 1 ? "" : "s",
                bootstrapWarningCount, bootstrapWarningCount == 1 ? "" : "s"));
        }
        sb.append(String.format("%d error%s, %d warning%s.",
            errorCount, errorCount == 1 ? "" : "s",
            warningCount, warningCount == 1 ? "" : "s"));

        BootstrapDivergence firstBootstrapError = firstBootstrapDivergence(BootstrapDivergence.Severity.ERROR);
        if (firstBootstrapError != null) {
            appendBootstrapSummary(sb, "error", firstBootstrapError, includeInlineDiagnostics);
        } else if (!errors.isEmpty()) {
            DivergenceGroup first = errors.get(0);
            sb.append(String.format(" First error: frame %d -- %s mismatch (expected=%s, actual=%s)",
                first.startFrame(), first.field(), first.expectedAtStart(), first.actualAtStart()));
            if (includeInlineDiagnostics) {
                appendFirstErrorDiagnostics(sb, first);
            }
        } else {
            BootstrapDivergence firstBootstrapWarning =
                    firstBootstrapDivergence(BootstrapDivergence.Severity.WARNING);
            if (firstBootstrapWarning != null) {
                appendBootstrapSummary(sb, "warning", firstBootstrapWarning, includeInlineDiagnostics);
            }
        }

        appendTraceContextSummary(sb, summaryReferenceFrame());
        return sb.toString();
    }

    private void appendBootstrapSummary(StringBuilder sb, String label,
                                        BootstrapDivergence divergence,
                                        boolean includeInlineDiagnostics) {
        sb.append(String.format(" First bootstrap %s: frame 0 -- %s mismatch (expected=%s, actual=%s)",
            label, divergence.field(), divergence.expected(), divergence.actual()));
        if (includeInlineDiagnostics
                && divergence.context() != null
                && !divergence.context().isBlank()) {
            sb.append(" ").append(divergence.context());
        }
    }

    private void appendGroupSummary(StringBuilder sb, String label, DivergenceGroup group) {
        sb.append(String.format(" First %s: frame %d -- %s mismatch (expected=%s, actual=%s)",
            label, group.startFrame(), group.field(), group.expectedAtStart(), group.actualAtStart()));
    }

    /**
     * Surface sub-pixel + counter context for the first failing frame inline
     * on the summary line so frontier-advancement iter loops can read it
     * without opening the report JSON. Only emitted for position/speed-shape
     * fields where the diagnostic context is informative.
     */
    private void appendFirstErrorDiagnostics(StringBuilder sb, DivergenceGroup first) {
        String field = first.field();
        if (field == null) {
            return;
        }
        boolean positionShape = field.equals("x") || field.equals("y")
                || field.endsWith("_x") || field.endsWith("_y")
                || field.endsWith("_x_speed") || field.endsWith("_y_speed")
                || field.endsWith("_g_speed")
                || field.equals("x_speed") || field.equals("y_speed")
                || field.equals("g_speed");
        if (!positionShape) {
            return;
        }
        FrameComparison fc = findComparison(first.startFrame());
        if (fc == null) {
            return;
        }
        String rom = fc.romDiagnostics();
        String engine = fc.engineDiagnostics();
        if ((rom == null || rom.isEmpty()) && (engine == null || engine.isEmpty())) {
            return;
        }
        sb.append(" rom={").append(rom == null ? "" : rom).append('}');
        sb.append(" engine={").append(engine == null ? "" : engine).append('}');
    }

    private FrameComparison findComparison(int frame) {
        for (FrameComparison fc : allComparisons) {
            if (fc.frame() == frame) {
                return fc;
            }
        }
        return null;
    }

    public String toJson() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            ObjectNode root = mapper.createObjectNode();

            root.put("error_count", totalErrorCount());
            root.put("warning_count", totalWarningCount());
            root.put("bootstrap_error_count", bootstrapErrorCount());
            root.put("bootstrap_warning_count", bootstrapWarningCount());
            root.put("total_frames", allComparisons.size());
            root.put("summary", toCompactSummary());

            int referenceFrame = summaryReferenceFrame();
            TraceEvent.Checkpoint checkpoint = latestCheckpointAtOrBefore(referenceFrame);
            if (checkpoint != null) {
                root.set("latest_checkpoint", checkpointToJson(mapper, checkpoint));
            }

            TraceEvent.ZoneActState zoneActState = latestZoneActStateAtOrBefore(referenceFrame);
            if (zoneActState != null) {
                root.set("latest_zone_act_state", zoneActStateToJson(mapper, zoneActState));
            }

            List<String> missingAuxSchemas = missingAdvertisedAuxSchemas();
            if (!missingAuxSchemas.isEmpty()) {
                ArrayNode missingNode = root.putArray("missing_advertised_aux_schemas");
                for (String schema : missingAuxSchemas) {
                    missingNode.add(schema);
                }
            }

            // Bootstrap (frame-0) divergences render ahead of the per-frame
            // groups so consumers see prelude failures first.
            ArrayNode bootstrapNode = root.putArray("bootstrap");
            for (BootstrapDivergence divergence : bootstrapDivergences) {
                bootstrapNode.add(bootstrapDivergenceToJson(mapper, divergence));
            }

            ArrayNode errorsNode = root.putArray("errors");
            for (DivergenceGroup g : errors) {
                errorsNode.add(groupToJson(mapper, g));
            }

            ArrayNode warningsNode = root.putArray("warnings");
            for (DivergenceGroup g : warnings) {
                warningsNode.add(groupToJson(mapper, g));
            }

            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"Failed to serialise report: " + e.getMessage() + "\"}";
        }
    }

    public String getContextWindow(int centreFrame, int radius) {
        int centreIndex = comparisonIndexForFrame(centreFrame);
        int start = Math.max(0, centreIndex - radius);
        int end = Math.min(allComparisons.size() - 1, centreIndex + radius);

        StringBuilder sb = new StringBuilder();
        if (shouldRenderBootstrapSection()) {
            appendBootstrapSection(sb);
        }
        appendTraceContextWindow(sb, centreFrame);
        sb.append("=== Per-frame ===\n");
        sb.append(String.format("%-6s", "Frame"));

        Set<String> fieldNames = new LinkedHashSet<>();
        boolean allFields = shouldRenderAllContextFields();
        for (int i = start; i <= end; i++) {
            if (i < allComparisons.size()) {
                FrameComparison fc = allComparisons.get(i);
                if (allFields) {
                    fieldNames.addAll(fc.fields().keySet());
                } else {
                    fc.fields().entrySet().stream()
                            .filter(entry -> entry.getValue().isDivergent())
                            .map(Map.Entry::getKey)
                            .forEach(fieldNames::add);
                }
            }
        }
        if (fieldNames.isEmpty()) {
            for (int i = start; i <= end; i++) {
                if (i < allComparisons.size()) {
                    fieldNames.addAll(allComparisons.get(i).fields().keySet());
                }
            }
        }

        for (String field : fieldNames) {
            sb.append(String.format(" | %-8s | %-8s", "Exp " + field, "Act " + field));
        }
        sb.append("\n");

        for (int i = start; i <= end; i++) {
            if (i >= allComparisons.size()) {
                break;
            }
            FrameComparison fc = allComparisons.get(i);
            sb.append(String.format("%-6d", fc.frame()));
            for (String field : fieldNames) {
                FieldComparison comp = fc.fields().get(field);
                if (comp != null) {
                    String marker = comp.severity() == Severity.ERROR ? "*" : " ";
                    sb.append(String.format(" | %-8s |%s%-7s",
                        comp.expected(), marker, comp.actual()));
                } else {
                    sb.append(String.format(" | %-8s | %-8s", "?", "?"));
                }
            }
            if (shouldRenderFrameDiagnostics(fc, centreFrame)) {
                String romDiag = fc.romDiagnostics();
                String engDiag = fc.engineDiagnostics();
                if (!romDiag.isEmpty() || !engDiag.isEmpty()) {
                    sb.append("\n       ROM: ").append(formatContextDiagnostics(romDiag));
                    sb.append("\n       ENG: ").append(formatContextDiagnostics(engDiag));
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private boolean shouldRenderFrameDiagnostics(FrameComparison comparison, int centreFrame) {
        if (!comparison.hasDivergence()) {
            return false;
        }
        String mode = System.getProperty("trace.context.diagnostics", "frontier")
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "all", "full", "verbose" -> true;
            case "none", "off", "false" -> false;
            default -> comparison.frame() == centreFrame;
        };
    }

    private boolean shouldRenderAllContextFields() {
        String mode = System.getProperty("trace.context.fields", "divergent")
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "all", "full", "compared" -> true;
            default -> false;
        };
    }

    private String formatContextDiagnostics(String diagnostics) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return "-";
        }
        int maxChars = contextDiagnosticMaxChars();
        if (maxChars < 0 || diagnostics.length() <= maxChars) {
            return diagnostics;
        }
        int visibleChars = Math.max(0, maxChars);
        return diagnostics.substring(0, visibleChars)
                + "... [truncated "
                + (diagnostics.length() - visibleChars)
                + " chars; set -Dtrace.context.diagnosticChars=full]";
    }

    private int contextDiagnosticMaxChars() {
        String value = System.getProperty("trace.context.diagnosticChars", "900")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (value.equals("full") || value.equals("all") || value.equals("unlimited")) {
            return -1;
        }
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 900;
        }
    }

    private int comparisonIndexForFrame(int frame) {
        if (allComparisons.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < allComparisons.size(); i++) {
            if (allComparisons.get(i).frame() == frame) {
                return i;
            }
        }
        int insertion = 0;
        while (insertion < allComparisons.size() && allComparisons.get(insertion).frame() < frame) {
            insertion++;
        }
        if (insertion == 0) {
            return 0;
        }
        if (insertion >= allComparisons.size()) {
            return allComparisons.size() - 1;
        }
        int beforeFrame = allComparisons.get(insertion - 1).frame();
        int afterFrame = allComparisons.get(insertion).frame();
        return Math.abs(frame - beforeFrame) <= Math.abs(afterFrame - frame)
                ? insertion - 1
                : insertion;
    }

    private void appendTraceContextSummary(StringBuilder sb, int frame) {
        TraceEvent.Checkpoint checkpoint = latestCheckpointAtOrBefore(frame);
        if (checkpoint != null) {
            sb.append(" Latest checkpoint: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(checkpoint)));
        }

        TraceEvent.ZoneActState zoneActState = latestZoneActStateAtOrBefore(frame);
        if (zoneActState != null) {
            sb.append(" Latest zone/act state: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(zoneActState)));
        }
    }

    private void appendTraceContextWindow(StringBuilder sb, int frame) {
        TraceEvent.Checkpoint checkpoint = latestCheckpointAtOrBefore(frame);
        if (checkpoint != null) {
            sb.append("Latest checkpoint: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(checkpoint)))
                .append("\n");
        }

        TraceEvent.ZoneActState zoneActState = latestZoneActStateAtOrBefore(frame);
        if (zoneActState != null) {
            sb.append("Latest zone_act_state: ")
                .append(TraceEventFormatter.summariseFrameEvents(List.of(zoneActState)))
                .append("\n");
        }

        List<String> missingAuxSchemas = missingAdvertisedAuxSchemas();
        if (!missingAuxSchemas.isEmpty()) {
            sb.append("Missing advertised aux schemas: ")
                .append(String.join(", ", missingAuxSchemas))
                .append("\n");
        }

        appendFocusedTraceDiagnostics(sb, frame);
    }

    private int summaryReferenceFrame() {
        if (hasBootstrapDivergences()) {
            return 0;
        }
        if (!errors.isEmpty()) {
            return errors.get(0).startFrame();
        }
        if (!warnings.isEmpty()) {
            return warnings.get(0).startFrame();
        }
        if (!allComparisons.isEmpty()) {
            return allComparisons.get(allComparisons.size() - 1).frame();
        }
        return -1;
    }

    private int totalErrorCount() {
        return errors.size() + bootstrapErrorCount();
    }

    private int totalWarningCount() {
        return warnings.size() + bootstrapWarningCount();
    }

    private int bootstrapErrorCount() {
        return (int) bootstrapDivergences.stream()
                .filter(d -> d.severity() == BootstrapDivergence.Severity.ERROR)
                .count();
    }

    private int bootstrapWarningCount() {
        return (int) bootstrapDivergences.stream()
                .filter(d -> d.severity() == BootstrapDivergence.Severity.WARNING)
                .count();
    }

    public boolean hasBootstrapErrors() {
        return bootstrapErrorCount() > 0;
    }

    public boolean hasBootstrapWarnings() {
        return bootstrapWarningCount() > 0;
    }

    private BootstrapDivergence firstBootstrapDivergence(BootstrapDivergence.Severity severity) {
        for (BootstrapDivergence divergence : bootstrapDivergences) {
            if (divergence.severity() == severity) {
                return divergence;
            }
        }
        return null;
    }

    private TraceEvent.Checkpoint latestCheckpointAtOrBefore(int frame) {
        if (traceData == null || frame < 0) {
            return null;
        }
        return traceData.latestCheckpointAtOrBefore(frame);
    }

    private TraceEvent.ZoneActState latestZoneActStateAtOrBefore(int frame) {
        if (traceData == null || frame < 0) {
            return null;
        }
        return traceData.latestZoneActStateAtOrBefore(frame);
    }

    private List<String> missingAdvertisedAuxSchemas() {
        return traceData == null
                ? List.of()
                : traceData.missingAdvertisedAuxSchemas();
    }

    private void appendFocusedTraceDiagnostics(StringBuilder sb, int frame) {
        if (traceData == null || frame < 0) {
            return;
        }
        List<TraceEvent> diagnostics = new ArrayList<>();
        diagnostics.addAll(traceData.stateSnapshotsForFrame(frame));
        diagnostics.addAll(traceData.cageStatesForFrame(frame));
        TraceEvent.CageExecution cageExecution = traceData.cageExecutionForFrame(frame);
        if (cageExecution != null) {
            diagnostics.add(cageExecution);
        }
        TraceEvent.VelocityWrite velocityWrite = traceData.velocityWriteForFrame(frame, "tails");
        if (velocityWrite != null) {
            diagnostics.add(velocityWrite);
        }
        TraceEvent.PositionWrite positionWrite = traceData.positionWriteForFrame(frame, "tails");
        if (positionWrite != null) {
            diagnostics.add(positionWrite);
        }
        TraceEvent.PositionWrite sonicPositionWrite = traceData.positionWriteForFrame(frame, "sonic");
        if (sonicPositionWrite != null) {
            diagnostics.add(sonicPositionWrite);
        }
        TraceEvent.AizShipLoop aizShipLoop = traceData.aizShipLoopForFrame(frame);
        if (aizShipLoop != null) {
            diagnostics.add(aizShipLoop);
        }
        TraceEvent.SonicRecordPos sonicRecordPos = traceData.sonicRecordPosForFrame(frame);
        if (sonicRecordPos != null) {
            diagnostics.add(sonicRecordPos);
        }
        TraceEvent.CpuState cpuState = traceData.cpuStateForFrame(frame, "tails");
        if (cpuState != null) {
            diagnostics.add(cpuState);
        }
        TraceEvent.TailsCpuNormalStep cpuNormalStep =
                traceData.tailsCpuNormalStepForFrame(frame, "tails");
        if (cpuNormalStep != null) {
            diagnostics.add(cpuNormalStep);
            TraceEvent.SonicRecordPos sourceRecord =
                    latestSonicRecordPosForStatIndex(frame,
                            (cpuNormalStep.posTableIndex() - 0x44) & 0xFF);
            if (sourceRecord != null && sourceRecord != sonicRecordPos) {
                diagnostics.add(sourceRecord);
            }
        }
        TraceEvent.SidekickInteractObjectState interactObject =
                traceData.sidekickInteractObjectStateForFrame(frame, "tails");
        if (interactObject != null) {
            diagnostics.add(interactObject);
        }
        diagnostics.addAll(traceData.cnzCylinderStatesForFrame(frame));
        TraceEvent.CnzCylinderExecution cnzCylinderExecution =
                traceData.cnzCylinderExecutionForFrame(frame);
        if (cnzCylinderExecution != null) {
            diagnostics.add(cnzCylinderExecution);
        }
        TraceEvent.CnzEventRamState cnzEventRam =
                traceData.cnzEventRamStateForFrame(frame);
        if (cnzEventRam != null) {
            diagnostics.add(cnzEventRam);
        }
        diagnostics.addAll(traceData.airCountdownStatesForFrame(frame));
        TraceEvent.RngCall rngCall = traceData.rngCallForFrame(frame);
        if (rngCall != null) {
            diagnostics.add(rngCall);
        }
        TraceEvent.AizBoundaryState aizBoundary =
                traceData.aizBoundaryStateForFrame(frame, "tails");
        if (aizBoundary != null) {
            diagnostics.add(aizBoundary);
        }
        TraceEvent.AizTransitionFloorSolidState aizFloor =
                traceData.aizTransitionFloorSolidStateForFrame(frame);
        if (aizFloor != null) {
            diagnostics.add(aizFloor);
        }
        TraceEvent.AizHandoffTerrainState aizHandoffTerrain =
                traceData.aizHandoffTerrainStateForFrame(frame);
        if (aizHandoffTerrain != null) {
            diagnostics.add(aizHandoffTerrain);
        }
        if (!diagnostics.isEmpty()) {
            sb.append("Trace diagnostics @")
                .append(frame)
                .append(": ")
                .append(TraceEventFormatter.summariseFrameEvents(diagnostics))
                .append("\n");
        }
    }

    private TraceEvent.SonicRecordPos latestSonicRecordPosForStatIndex(int frame, int statIndex) {
        if (traceData == null || frame < 0) {
            return null;
        }
        int start = Math.max(0, frame - 80);
        for (int f = frame; f >= start; f--) {
            TraceEvent.SonicRecordPos record = traceData.sonicRecordPosForFrame(f);
            if (record == null) {
                continue;
            }
            for (TraceEvent.SonicRecordPos.Hit hit : record.hits()) {
                if ((hit.posTableIndex() & 0xFF) == statIndex) {
                    return record;
                }
            }
        }
        return null;
    }

    private boolean shouldRenderBootstrapSection() {
        if (!bootstrapDivergences.isEmpty()) {
            return true;
        }
        String mode = System.getProperty("trace.context.bootstrap", "divergent")
                .trim()
                .toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "all", "always", "full", "true" -> true;
            default -> false;
        };
    }

    private static List<DivergenceGroup> buildGroups(List<FrameComparison> comparisons) {
        List<DivergenceGroup> groups = new ArrayList<>();
        Map<String, DivergenceGroupBuilder> openGroups = new LinkedHashMap<>();

        for (FrameComparison fc : comparisons) {
            Set<String> activeFields = new HashSet<>();

            for (Map.Entry<String, FieldComparison> entry : fc.fields().entrySet()) {
                String field = entry.getKey();
                FieldComparison comp = entry.getValue();

                if (comp.isDivergent()) {
                    activeFields.add(field);
                    DivergenceGroupBuilder builder = openGroups.get(field);
                    if (builder != null && builder.severity == comp.severity()
                            && builder.endFrame == fc.frame() - 1) {
                        builder.endFrame = fc.frame();
                    } else {
                        if (builder != null) {
                            groups.add(builder.build());
                        }
                        openGroups.put(field, new DivergenceGroupBuilder(
                            field, comp.severity(), fc.frame(),
                            comp.expected(), comp.actual()));
                    }
                }
            }

            Iterator<Map.Entry<String, DivergenceGroupBuilder>> iter =
                openGroups.entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<String, DivergenceGroupBuilder> entry = iter.next();
                if (!activeFields.contains(entry.getKey())) {
                    groups.add(entry.getValue().build());
                    iter.remove();
                }
            }
        }

        for (DivergenceGroupBuilder builder : openGroups.values()) {
            groups.add(builder.build());
        }

        groups.sort(Comparator
            .comparingInt(DivergenceGroup::startFrame)
            .thenComparingInt(g -> fieldSummaryPriority(g.field())));
        markCascading(groups);
        return groups;
    }

    private static int fieldSummaryPriority(String field) {
        if (field == null) {
            return 50;
        }
        if (field.equals("x") || field.equals("y")
                || field.endsWith("_x") || field.endsWith("_y")
                || field.equals("x_sub") || field.equals("y_sub")
                || field.endsWith("_x_sub") || field.endsWith("_y_sub")
                || field.equals("x_speed") || field.equals("y_speed")
                || field.equals("g_speed")
                || field.endsWith("_x_speed") || field.endsWith("_y_speed")
                || field.endsWith("_g_speed")) {
            return 0;
        }
        if (field.equals("routine") || field.endsWith("_routine")) {
            return 10;
        }
        if (field.equals("status_byte") || field.endsWith("_status_byte")) {
            return 40;
        }
        return 20;
    }

    private static void markCascading(List<DivergenceGroup> groups) {
        int earliestErrorFrame = Integer.MAX_VALUE;
        String earliestErrorField = null;
        for (DivergenceGroup g : groups) {
            if (g.severity() == Severity.ERROR && g.startFrame() < earliestErrorFrame) {
                earliestErrorFrame = g.startFrame();
                earliestErrorField = g.field();
            }
        }

        if (earliestErrorField == null) {
            return;
        }

        for (int i = 0; i < groups.size(); i++) {
            DivergenceGroup g = groups.get(i);
            boolean cascading = g.severity() == Severity.ERROR
                && g.startFrame() > earliestErrorFrame
                && !g.field().equals(earliestErrorField);
            if (cascading != g.cascading()) {
                groups.set(i, new DivergenceGroup(g.field(), g.severity(),
                    g.startFrame(), g.endFrame(),
                    g.expectedAtStart(), g.actualAtStart(), cascading));
            }
        }
    }

    private ObjectNode groupToJson(ObjectMapper mapper, DivergenceGroup g) {
        ObjectNode node = mapper.createObjectNode();
        node.put("field", g.field());
        node.put("severity", g.severity().name());
        node.put("start_frame", g.startFrame());
        node.put("end_frame", g.endFrame());
        node.put("frame_span", g.frameSpan());
        node.put("expected_at_start", g.expectedAtStart());
        node.put("actual_at_start", g.actualAtStart());
        node.put("cascading", g.cascading());
        return node;
    }

    private ObjectNode checkpointToJson(ObjectMapper mapper, TraceEvent.Checkpoint checkpoint) {
        ObjectNode node = mapper.createObjectNode();
        node.put("frame", checkpoint.frame());
        node.put("name", checkpoint.name());
        putNullableInt(node, "actual_zone_id", checkpoint.actualZoneId());
        putNullableInt(node, "actual_act", checkpoint.actualAct());
        putNullableInt(node, "apparent_act", checkpoint.apparentAct());
        putNullableInt(node, "game_mode", checkpoint.gameMode());
        if (checkpoint.notes() == null) {
            node.putNull("notes");
        } else {
            node.put("notes", checkpoint.notes());
        }
        return node;
    }

    private ObjectNode zoneActStateToJson(ObjectMapper mapper, TraceEvent.ZoneActState zoneActState) {
        ObjectNode node = mapper.createObjectNode();
        node.put("frame", zoneActState.frame());
        putNullableInt(node, "actual_zone_id", zoneActState.actualZoneId());
        putNullableInt(node, "actual_act", zoneActState.actualAct());
        putNullableInt(node, "apparent_act", zoneActState.apparentAct());
        putNullableInt(node, "game_mode", zoneActState.gameMode());
        return node;
    }

    private void putNullableInt(ObjectNode node, String field, Integer value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    /**
     * Appends the "=== Bootstrap (frame 0) ===" section to the text context
     * window. Always emits the header so the per-frame header that follows
     * is unambiguous; the body is empty for legacy traces.
     */
    private void appendBootstrapSection(StringBuilder sb) {
        sb.append("=== Bootstrap (frame 0) ===\n");
        if (bootstrapDivergences.isEmpty()) {
            sb.append("(no bootstrap divergences)\n");
            return;
        }
        for (BootstrapDivergence divergence : bootstrapDivergences) {
            sb.append(String.format("[%s] %s  expected=%s  actual=%s%n",
                    divergence.severity().name(),
                    divergence.field(),
                    divergence.expected(),
                    divergence.actual()));
            if (divergence.context() != null && !divergence.context().isBlank()) {
                sb.append("    ").append(divergence.context()).append("\n");
            }
        }
    }

    /** Builds the JSON node for a single {@link BootstrapDivergence}. */
    private ObjectNode bootstrapDivergenceToJson(ObjectMapper mapper,
                                                 BootstrapDivergence divergence) {
        ObjectNode node = mapper.createObjectNode();
        node.put("field", divergence.field());
        node.put("severity", divergence.severity().name());
        node.put("expected", divergence.expected());
        node.put("actual", divergence.actual());
        node.put("context", divergence.context() == null ? "" : divergence.context());
        return node;
    }

    private static class DivergenceGroupBuilder {
        final String field;
        final Severity severity;
        final int startFrame;
        final String expectedAtStart;
        final String actualAtStart;
        int endFrame;

        DivergenceGroupBuilder(String field, Severity severity, int frame,
                String expected, String actual) {
            this.field = field;
            this.severity = severity;
            this.startFrame = frame;
            this.endFrame = frame;
            this.expectedAtStart = expected;
            this.actualAtStart = actual;
        }

        DivergenceGroup build() {
            return new DivergenceGroup(field, severity, startFrame, endFrame,
                expectedAtStart, actualAtStart, false);
        }
    }
}
