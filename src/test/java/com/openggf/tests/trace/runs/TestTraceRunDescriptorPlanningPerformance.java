package com.openggf.tests.trace.runs;

import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.catalog.TraceCatalog;
import com.openggf.trace.catalog.TraceEntry;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunSegmentDescriptor;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in retained-heap evidence for compact run-segment planning.
 *
 * <p>The real 67-segment run exceeds the default one-GiB Surefire heap when
 * planned eagerly. Run this class through {@code -Ptrace-replay}, whose
 * project-owned three-GiB fork setting is also used by the complete-run
 * replay suite.</p>
 */
@EnabledIfSystemProperty(
        named = "openggf.trace.segmentDescriptorBenchmark", matches = "true")
class TestTraceRunDescriptorPlanningPerformance {
    private static final String RUN_ID =
            "s3k-knuckles-complete-superemeralds";
    private static final int EXPECTED_SEGMENTS = 67;

    @Test
    void compactDescriptorsRetainLessHeapWithoutOwningEagerPayloads()
            throws Exception {
        Path projectRoot = Path.of(System.getProperty("project.basedir", "."))
                .toAbsolutePath().normalize();
        TraceEntry entry = TraceCatalog.scan(
                        projectRoot.resolve("src/test/resources/traces"))
                .stream()
                .filter(candidate -> candidate.isRun()
                        && RUN_ID.equals(candidate.runManifest().runId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Trace catalog did not discover run " + RUN_ID));
        TraceRunManifest manifest = entry.runManifest();

        assertEquals(EXPECTED_SEGMENTS, manifest.segments().size(),
                "benchmark must keep using the measured 67-segment fixture");

        EagerMeasurement eager = measureEager(manifest, entry.runDir());
        DescriptorMeasurement descriptor =
                measureDescriptors(manifest, entry.runDir());

        assertEquals(eager.segmentCount(), descriptor.segmentCount());
        assertEquals(eager.rowCount(), descriptor.rowCount());
        assertEquals(descriptor.rowCount(), descriptor.rawFrameCount());
        assertDescriptorShapeHasNoEagerPayloadOwner();
        assertTrue(descriptor.retainedBytes() < eager.retainedBytes(),
                () -> "descriptor retained heap " + descriptor.retainedBytes()
                        + " must be below eager retained heap "
                        + eager.retainedBytes());

        long reductionBytes = eager.retainedBytes()
                - descriptor.retainedBytes();
        long reductionPercent = eager.retainedBytes() == 0
                ? 0
                : Math.round(reductionBytes * 100.0 / eager.retainedBytes());
        System.out.printf(
                "TRACE_SEGMENT_DESCRIPTOR_BENCH segments=%d "
                        + "eager_retained_bytes=%d descriptor_retained_bytes=%d "
                        + "reduction_bytes=%d reduction_percent=%d "
                        + "descriptor_raw_frames=%d%n",
                descriptor.segmentCount(), eager.retainedBytes(),
                descriptor.retainedBytes(), reductionBytes,
                reductionPercent, descriptor.rawFrameCount());
    }

    private static EagerMeasurement measureEager(
            TraceRunManifest manifest, Path runDir) throws IOException {
        long baselineBytes = forcedGcHeapBytes();
        List<TraceRunReplayWalker.SegmentPlan> plans =
                TraceRunReplayWalker.plan(manifest, runDir);
        long retainedBytes = forcedGcHeapBytes() - baselineBytes;
        int rowCount = plans.stream().mapToInt(plan -> {
            TraceRunSpecialStageRows specialRows = plan.specialStageRows();
            return specialRows != null
                    ? specialRows.rowCount() : plan.trace().frameCount();
        }).sum();
        EagerMeasurement result = new EagerMeasurement(
                plans.size(), rowCount, retainedBytes);
        Reference.reachabilityFence(plans);
        return result;
    }

    private static DescriptorMeasurement measureDescriptors(
            TraceRunManifest manifest, Path runDir) throws IOException {
        long baselineBytes = forcedGcHeapBytes();
        List<TraceRunSegmentDescriptor> descriptors =
                TraceRunReplayWalker.planDescriptors(manifest, runDir);
        long retainedBytes = forcedGcHeapBytes() - baselineBytes;
        int rowCount = descriptors.stream()
                .mapToInt(TraceRunSegmentDescriptor::rowCount)
                .sum();
        int rawFrameCount = descriptors.stream()
                .mapToInt(descriptor -> descriptor.rawFrames().size())
                .sum();
        DescriptorMeasurement result = new DescriptorMeasurement(
                descriptors.size(), rowCount, rawFrameCount, retainedBytes);
        Reference.reachabilityFence(descriptors);
        return result;
    }

    private static void assertDescriptorShapeHasNoEagerPayloadOwner() {
        List<Class<?>> forbiddenOwners = List.of(
                TraceData.class, TraceRunSpecialStageRows.class);
        assertTrue(Arrays.stream(
                        TraceRunSegmentDescriptor.class.getRecordComponents())
                .noneMatch(component -> forbiddenOwners.stream()
                        .anyMatch(owner -> owner.isAssignableFrom(
                                component.getType()))));
        assertTrue(Arrays.stream(TraceRunSegmentDescriptor.class.getDeclaredFields())
                .noneMatch(field -> forbiddenOwners.stream()
                        .anyMatch(owner -> owner.isAssignableFrom(
                                field.getType()))));
        assertEquals(1, Arrays.stream(
                        TraceRunSegmentDescriptor.class.getRecordComponents())
                .filter(component -> component.getType() == TraceFrame.class)
                .count(), "only the compact opening-row summary may retain a frame");
        assertTrue(Arrays.stream(
                        TraceRunSegmentDescriptor.class.getRecordComponents())
                .filter(component -> component.getType() != TraceFrame.class)
                .noneMatch(component -> component.getGenericType().getTypeName()
                        .contains(TraceFrame.class.getName())),
                "descriptor collections must not retain raw TraceFrame payloads");
    }

    private static long forcedGcHeapBytes() {
        System.gc();
        System.gc();
        return ManagementFactory.getMemoryMXBean()
                .getHeapMemoryUsage().getUsed();
    }

    private record EagerMeasurement(
            int segmentCount, int rowCount, long retainedBytes) {
    }

    private record DescriptorMeasurement(
            int segmentCount,
            int rowCount,
            int rawFrameCount,
            long retainedBytes) {
    }
}
