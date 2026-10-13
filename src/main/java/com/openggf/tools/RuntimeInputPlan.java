package com.openggf.tools;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.trace.TraceData;
import com.openggf.trace.replay.TraceReplayRowPolicy;
import com.openggf.trace.timing.HardwareCompletionEdge;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Input-only normal-Engine workload, originating in the 2026-10-12 runtime
 * performance task. Compile from a v5 fixture in a separate process; measured
 * processes load only controller inputs, structural loop admission and the
 * dedicated prepared-job readiness stream. Physics and auxiliary comparison
 * rows never enter the measured process.
 */
public record RuntimeInputPlan(int schema, String game, int zone, int act, int traceStart,
        List<TraceReplayRowPolicy> rows, List<Bk2FrameInput> inputs,
        boolean recordedTiming, List<HardwareCompletionEdge> readiness) {
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public RuntimeInputPlan {
        if (schema != 1 || !List.of("s1", "s2", "s3k").contains(game)
                || zone < 0 || act < 0 || traceStart < 0 || rows.isEmpty()) {
            throw new IllegalArgumentException("Invalid runtime input plan");
        }
        rows = List.copyOf(rows);
        inputs = List.copyOf(inputs);
        readiness = List.copyOf(readiness);
        if (inputs.size() != rows.size() + 1) {
            throw new IllegalArgumentException("Plan must retain one preceding controller input");
        }
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            if (row.traceIndex() != traceStart + i
                    || row.validationBk2Index() != inputs.get(i + 1).frameIndex()
                    || row.appliedInputOffset() < -1 || row.appliedInputOffset() > 0) {
                throw new IllegalArgumentException("Misaligned runtime input row " + i);
            }
        }
    }

    public static RuntimeInputPlan read(Path path) throws java.io.IOException {
        return JSON.readValue(path.toFile(), RuntimeInputPlan.class);
    }

    public void write(Path path) throws java.io.IOException {
        JSON.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
    }

    public static RuntimeInputPlan compile(Path directory, int zone, int act,
            int start, int count) throws java.io.IOException {
        var trace = TraceData.load(directory);
        int end = Math.min(trace.frameCount(), Math.addExact(start, count));
        if (start < 0 || count < 1 || start >= end) {
            throw new IllegalArgumentException("Empty or invalid route window");
        }
        String source = trace.metadata().sourceBk2();
        Path moviePath = source == null || source.isBlank() ? null
                : directory.getParent().resolve("_movies").resolve(source);
        if (moviePath == null || !Files.isRegularFile(moviePath)) {
            try (var paths = Files.list(directory)) {
                var candidates = paths.filter(p -> p.toString().endsWith(".bk2")).toList();
                if (candidates.size() != 1) {
                    throw new IllegalArgumentException("Expected one movie for " + directory);
                }
                moviePath = candidates.getFirst();
            }
        }
        var movie = new Bk2MovieLoader().load(moviePath);
        int first = trace.metadata().bk2FrameOffset() + start;
        if (first < 1 || first + end - start > movie.getFrameCount()) {
            throw new IllegalArgumentException("Controller movie does not cover route and predecessor");
        }
        var rows = new java.util.ArrayList<TraceReplayRowPolicy>();
        for (int i = start; i < end; i++) {
            rows.add(TraceReplayRowPolicy.resolve(trace, i,
                    trace.metadata().bk2FrameOffset() + i));
        }
        var inputs = movie.getFrames().subList(first - 1, first + end - start).stream()
                .map(f -> new Bk2FrameInput(f.frameIndex(), f.p1InputMask(), f.p1ActionMask(),
                        f.p1StartPressed(), f.p2InputMask(), f.p2ActionMask(), f.p2StartPressed(),
                        f.debugModeTogglePressed(), f.debugShiftDown(), f.debugControlDown(),
                        f.debugAltDown(), f.debugSuperDown(), ""))
                .toList();
        return new RuntimeInputPlan(1, trace.metadata().game(), zone, act, start,
                rows, inputs, trace.hardwareTimingSchedule().hasRecordedInput(),
                trace.hardwareTimingSchedule().edges());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new IllegalArgumentException(
                    "Usage: RuntimeInputPlan <trace-dir> <zone> <act> <start> <count> <output-json>");
        }
        var plan = compile(Path.of(args[0]), Integer.parseInt(args[1]),
                Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
        plan.write(Path.of(args[5]));
        System.out.println("Compiled " + plan.rows().size() + " input rows for " + plan.game());
    }
}
