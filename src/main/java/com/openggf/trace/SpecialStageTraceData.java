package com.openggf.trace;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Reads and holds the contents of a Sonic 2 special-stage trace directory:
 * {@code metadata.json}, {@code physics.csv(.gz)}, and optional
 * {@code aux_state.jsonl(.gz)}.
 *
 * <p>Mirrors {@link TraceData#load(Path)}'s load flow but parses rows with
 * {@link SpecialStageTraceFrame#parseCsvRow(String)} instead of
 * {@link TraceFrame}, and requires {@code metadata.trace_profile} to be
 * {@code "s2_special_stage"}.
 */
public final class SpecialStageTraceData {

    private static final String REQUIRED_TRACE_PROFILE = "s2_special_stage";

    private final TraceMetadata metadata;
    private final List<SpecialStageTraceFrame> frames;
    private final Map<Integer, List<TraceEvent>> eventsByFrame;

    private SpecialStageTraceData(TraceMetadata metadata, List<SpecialStageTraceFrame> frames,
            Map<Integer, List<TraceEvent>> eventsByFrame) {
        this.metadata = metadata;
        this.frames = frames;
        this.eventsByFrame = eventsByFrame;
    }

    public static SpecialStageTraceData load(Path traceDirectory) throws IOException {
        Path metadataPath = traceDirectory.resolve("metadata.json");
        TraceMetadata metadata = TraceMetadata.load(metadataPath);

        String traceProfile = metadata.traceProfile();
        if (!REQUIRED_TRACE_PROFILE.equals(traceProfile)) {
            throw new IllegalArgumentException(
                "Expected trace_profile '" + REQUIRED_TRACE_PROFILE + "', got '"
                    + traceProfile + "' in " + metadataPath);
        }

        Path physicsPath = TraceData.resolveTraceFile(traceDirectory, "physics.csv");
        if (physicsPath == null) {
            throw new NoSuchFileException(traceDirectory.resolve("physics.csv").toString());
        }
        Path auxPath = TraceData.resolveTraceFile(traceDirectory, "aux_state.jsonl");

        List<SpecialStageTraceFrame> frames = loadPhysicsCsv(physicsPath);
        Map<Integer, List<TraceEvent>> events = auxPath != null
            ? TraceData.loadAuxEvents(auxPath)
            : Collections.emptyMap();

        return new SpecialStageTraceData(metadata, frames, events);
    }

    public TraceMetadata metadata() {
        return metadata;
    }

    public int frameCount() {
        return frames.size();
    }

    public SpecialStageTraceFrame getFrame(int i) {
        if (i < 0 || i >= frames.size()) {
            throw new IndexOutOfBoundsException(
                "Frame " + i + " out of range [0, " + frames.size() + ")");
        }
        return frames.get(i);
    }

    /** Reuses {@link TraceEvent} + aux jsonl parsing shared with {@link TraceData}. */
    public List<TraceEvent> getEventsForFrame(int i) {
        return eventsByFrame.getOrDefault(i, Collections.emptyList());
    }

    /** Returns the first frame carrying a {@code stage_finished} aux event, if any. */
    public OptionalInt stageFinishedFrame() {
        return eventsByFrame.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(SpecialStageTraceData::isStageFinished))
            .mapToInt(Map.Entry::getKey)
            .min();
    }

    private static boolean isStageFinished(TraceEvent event) {
        return event instanceof TraceEvent.StateSnapshot snapshot
            && "stage_finished".equals(snapshot.fields().get("type"));
    }

    private static List<SpecialStageTraceFrame> loadPhysicsCsv(Path csvPath) throws IOException {
        List<SpecialStageTraceFrame> frames = new ArrayList<>();
        try (BufferedReader reader = TraceData.openTraceReader(csvPath)) {
            String line = reader.readLine(); // skip header
            if (line == null) return frames;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    frames.add(SpecialStageTraceFrame.parseCsvRow(trimmed));
                }
            }
        }
        return frames;
    }
}
