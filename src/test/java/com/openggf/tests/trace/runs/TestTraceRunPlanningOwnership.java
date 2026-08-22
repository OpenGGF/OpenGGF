package com.openggf.tests.trace.runs;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.replay.runs.ActiveSegmentPayload;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRows;
import com.openggf.tests.trace.TraceV5RunFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestTraceRunPlanningOwnership {

    @Test
    void descriptorLaunchRetainsNoPayloadThroughItsTransitiveObjectGraph(
            @TempDir Path root) throws Exception {
        Path runDir = TraceV5RunFixture.writeS3kBonusRun(root.resolve("s3k/runs"));
        TraceV5RunFixture.writeMovie(runDir.resolve("synthetic.bk2"));
        TraceEntry entry = TraceCatalog.scan(root).stream()
                .filter(TraceEntry::isRun)
                .findFirst()
                .orElseThrow();

        TraceCatalog.PreparedDescriptorRunLaunch prepared =
                TraceCatalog.prepareDescriptorRunLaunch(entry);

        assertDoesNotThrow(() -> assertNoPayloadReachable(prepared));
    }

    @Test
    void payloadReachabilityProbeRejectsNestedPayloadOwners(
            @TempDir Path root) throws Exception {
        Path s3kRun = TraceV5RunFixture.writeS3kBonusRun(root.resolve("s3k/runs"));
        TraceRunManifest s3kManifest = TraceRunManifest.load(
                s3kRun.resolve("run_manifest.json"));
        TraceData trace;
        try (ActiveSegmentPayload active = TraceRunReplayWalker.openActiveSegment(
                TraceRunReplayWalker.planDescriptors(s3kManifest, s3kRun).getFirst(), 0)) {
            trace = active.trace();
        }
        Path s2Run = TraceV5RunFixture.writeS2SpecialStageRun(root.resolve("s2/runs"));
        TraceRunManifest s2Manifest = TraceRunManifest.load(
                s2Run.resolve("run_manifest.json"));
        TraceRunSpecialStageRows specialRows;
        try (ActiveSegmentPayload active = TraceRunReplayWalker.openActiveSegment(
                TraceRunReplayWalker.planDescriptors(s2Manifest, s2Run).get(1), 1)) {
            specialRows = active.specialStageRows();
        }
        Reader reader = new BufferedReader(new StringReader("trace"));
        InputStream stream = new ByteArrayInputStream(new byte[] {1});
        Path mappedPath = root.resolve("payload.bin");
        Files.write(mappedPath, new byte[] {1});
        try (FileChannel channel = FileChannel.open(mappedPath, StandardOpenOption.READ)) {
            MappedByteBuffer mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, 1);
            List<Object> payloads = List.of(trace,
                    new TraceEvent.ObjectRemoved(0, 1, "badnik"), specialRows,
                    reader, stream, mapped, new AtomicReference<>(trace),
                    new WeakReference<>(trace), Stream.of(trace));

            for (Object payload : payloads) {
                assertThrows(AssertionError.class,
                        () -> assertNoPayloadReachable(new PayloadWrapper(
                                Optional.of(Map.of("payloads", List.of(payload))))));
            }
        }
    }

    private static void assertNoPayloadReachable(Object root) {
        visit(root, "root", new IdentityHashMap<>());
    }

    private static void visit(Object value, String path,
            IdentityHashMap<Object, Boolean> visited) {
        if (value == null || isLeaf(value) || visited.put(value, Boolean.TRUE) != null) {
            return;
        }
        if (isPayloadOwner(value)) {
            throw new AssertionError("payload owner reachable at " + path + ": "
                    + value.getClass().getName());
        }
        if (value instanceof Optional<?> optional) {
            optional.ifPresent(nested -> visit(nested, path + ".optional", visited));
            return;
        }
        if (value instanceof AtomicReference<?> reference) {
            visit(reference.get(), path + ".atomicReference", visited);
            return;
        }
        if (value instanceof Reference<?> reference) {
            visit(reference.get(), path + ".reference", visited);
            return;
        }
        if (value instanceof Stream<?> stream) {
            try (stream) {
                stream.forEach(nested -> visit(nested, path + ".stream", visited));
            } catch (RuntimeException e) {
                throw new AssertionError("cannot inspect stream at " + path, e);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object nested : iterable) {
                visit(nested, path + "[" + index++ + "]", visited);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                visit(entry.getKey(), path + ".key", visited);
                visit(entry.getValue(), path + ".value", visited);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int index = 0; index < length; index++) {
                visit(java.lang.reflect.Array.get(value, index), path + "[" + index + "]",
                        visited);
            }
            return;
        }
        if (value.getClass().getPackageName().startsWith("java.")) {
            throw new AssertionError("opaque platform object reachable at " + path + ": "
                    + value.getClass().getName());
        }
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                try {
                    if (!field.trySetAccessible()) {
                        throw new AssertionError("cannot inspect " + path + "."
                                + field.getName());
                    }
                    visit(field.get(value), path + "." + field.getName(), visited);
                } catch (IllegalAccessException e) {
                    throw new AssertionError("cannot inspect " + path + "."
                            + field.getName(), e);
                }
            }
        }
    }

    private static boolean isPayloadOwner(Object value) {
        return value instanceof TraceData
                || value instanceof TraceRunSpecialStageRows
                || value instanceof TraceEvent
                || value instanceof Reader
                || value instanceof InputStream
                || value instanceof MappedByteBuffer
                || value instanceof ActiveSegmentPayload;
    }

    private static boolean isLeaf(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum<?> || value instanceof Class<?>
                || value instanceof Path || value instanceof BitSet;
    }

    private record PayloadWrapper(Object nested) {
    }
}
