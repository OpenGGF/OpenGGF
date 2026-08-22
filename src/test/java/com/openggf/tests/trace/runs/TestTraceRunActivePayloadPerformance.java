package com.openggf.tests.trace.runs;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.BonusStageType;
import com.openggf.game.GameMode;
import com.openggf.trace.SpecialStageRunObjectsPassBinder;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceHudModel;
import com.openggf.trace.TraceRunManifest;
import com.openggf.trace.live.LiveTraceComparator;
import com.openggf.trace.live.MismatchEntry;
import com.openggf.trace.replay.runs.ActiveSegmentPayload;
import com.openggf.trace.replay.runs.TraceRunReplayWalker;
import com.openggf.trace.replay.runs.TraceRunSegmentDescriptor;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRowDriver;
import com.openggf.trace.replay.runs.TraceRunSpecialStageRows;
import com.openggf.trace.replay.runs.TraceStructuralRowComparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in fixed-cap evidence for bounded active trace-run payload ownership. */
@EnabledIfSystemProperty(
        named = "openggf.trace.activePayloadBenchmark", matches = "true")
class TestTraceRunActivePayloadPerformance {
    private static final String S3K_RUN_ID =
            "s3k-knuckles-complete-superemeralds";
    private static final long FIXED_WARMED_EAGER_BASELINE_BYTES =
            1_087_200_800L;
    private static final long MAX_DESCRIPTOR_RETAINED_BYTES = 16_777_216L;
    private static final long MAX_INSTALLED_BYTES = 268_435_456L;
    private static final double MIN_REDUCTION_PERCENT = 75.0;

    @Test
    void allSegmentsStayWithinFixedDescriptorAndInstalledCaps()
            throws Exception {
        Path projectRoot = Path.of(System.getProperty("project.basedir", "."))
                .toAbsolutePath().normalize();
        List<RunSource> sources = List.of(
                source(projectRoot, "s3k", S3K_RUN_ID),
                source(projectRoot, "s1", "s1-ghz-maze-roundtrip"),
                source(projectRoot, "s2", "s2-ehz-halfpipe-roundtrip"));
        assertEquals(67, sources.getFirst().manifest().segments().size(),
                "benchmark must keep sampling the reviewed 67-segment run");

        warmAndReleasePlanners(sources);
        DescriptorMeasurement descriptor = measureDescriptors(sources);
        forcedGcHeapBytes();
        int fdBefore = linuxOpenFileDescriptorCount();
        InstalledMeasurement installed = measureInstalled(sources);
        forcedGcHeapBytes();
        int fdAfter = linuxOpenFileDescriptorCount();

        double reductionPercent = 100.0 * (1.0
                - installed.maxBytes()
                        / (double) FIXED_WARMED_EAGER_BASELINE_BYTES);
        System.out.printf(
                "TRACE_ACTIVE_PAYLOAD_BENCH eager_retained_bytes=%d "
                        + "descriptor_retained_bytes=%d max_installed_bytes=%d "
                        + "reduction_percent=%.2f max_segment=%s%n",
                FIXED_WARMED_EAGER_BASELINE_BYTES, descriptor.retainedBytes(),
                installed.maxBytes(), reductionPercent, installed.maxSegment());

        assertEquals(67, descriptor.s3kSegmentCount());
        assertTrue(descriptor.retainedBytes() <= MAX_DESCRIPTOR_RETAINED_BYTES,
                () -> "descriptor retained heap " + descriptor.retainedBytes()
                        + " exceeds " + MAX_DESCRIPTOR_RETAINED_BYTES);
        assertTrue(installed.maxBytes() <= MAX_INSTALLED_BYTES,
                () -> "installed retained heap " + installed.maxBytes()
                        + " exceeds " + MAX_INSTALLED_BYTES + " at "
                        + installed.maxSegment());
        assertTrue(reductionPercent >= MIN_REDUCTION_PERCENT,
                () -> "installed reduction " + reductionPercent
                        + "% is below " + MIN_REDUCTION_PERCENT + "%");
        assertTrue(installed.sampledS1Special(),
                "the installed samples must include a real S1 special driver");
        assertTrue(installed.sampledS2Special(),
                "the installed samples must include a real S2 special driver");
        assertTrue(installed.sampledPassBinder(),
                "the S2 sample must retain a real recorded pass binder");
        if (fdBefore >= 0 && fdAfter >= 0) {
            assertTrue(fdAfter <= fdBefore + 8,
                    () -> "Linux fd smoke check grew from " + fdBefore
                            + " to " + fdAfter);
        }
    }

    private DescriptorMeasurement measureDescriptors(List<RunSource> sources)
            throws IOException {
        long baseline = forcedGcHeapBytes();
        DescriptorSet descriptors = planDescriptorSet(sources);
        long retained = forcedGcHeapBytes() - baseline;
        DescriptorMeasurement result = new DescriptorMeasurement(
                descriptors.s3k().size(), retained);
        descriptors.fence();
        Reference.reachabilityFence(sources);
        Reference.reachabilityFence(this);
        return result;
    }

    private InstalledMeasurement measureInstalled(List<RunSource> sources)
            throws Exception {
        long baseline = forcedGcHeapBytes();
        DescriptorSet descriptors = planDescriptorSet(sources);
        List<SegmentTarget> targets = new ArrayList<>(69);
        for (int index = 0; index < descriptors.s3k().size(); index++) {
            targets.add(new SegmentTarget("s3k", index,
                    descriptors.s3k().get(index)));
        }
        targets.add(firstSpecial("s1", descriptors.s1()));
        targets.add(firstSpecial("s2", descriptors.s2()));

        long maxBytes = Long.MIN_VALUE;
        String maxSegment = "none";
        boolean sampledS1 = false;
        boolean sampledS2 = false;
        boolean sampledBinder = false;
        for (SegmentTarget target : targets) {
            InstalledSample sample = measureInstalledSegment(
                    target, descriptors, baseline);
            if (sample.retainedBytes() > maxBytes) {
                maxBytes = sample.retainedBytes();
                maxSegment = target.label();
            }
            sampledS1 |= target.game().equals("s1");
            sampledS2 |= target.game().equals("s2");
            sampledBinder |= sample.passBinderInstalled();
            forcedGcHeapBytes();
        }
        descriptors.fence();
        Reference.reachabilityFence(targets);
        Reference.reachabilityFence(sources);
        Reference.reachabilityFence(this);
        return new InstalledMeasurement(
                maxBytes, maxSegment, sampledS1, sampledS2, sampledBinder);
    }

    private InstalledSample measureInstalledSegment(
            SegmentTarget target,
            DescriptorSet descriptors,
            long baseline) throws Exception {
        ActiveSegmentPayload lease = TraceRunReplayWalker.openActiveSegment(
                target.descriptor(), target.index());
        InstalledRoots roots = null;
        try {
            TraceData trace = lease.trace();
            TraceRunSpecialStageRows rows = lease.specialStageRows();
            roots = rows == null
                    ? ordinaryRoots(lease, trace, target, descriptors)
                    : specialRoots(lease, trace, rows, target, descriptors);
            long retained = forcedGcHeapBytes() - baseline;
            roots.fenceAfterSample();
            Reference.reachabilityFence(trace);
            Reference.reachabilityFence(rows);
            Reference.reachabilityFence(lease);
            return new InstalledSample(retained, roots.passBinder != null);
        } finally {
            if (roots != null) {
                roots.detach();
            }
            lease.close();
        }
    }

    private InstalledRoots ordinaryRoots(
            ActiveSegmentPayload lease,
            TraceData trace,
            SegmentTarget target,
            DescriptorSet descriptors) {
        LiveTraceComparator comparator = new LiveTraceComparator(
                trace, ToleranceConfig.DEFAULT, 0, () -> null);
        TraceStructuralRowComparator structural =
                new TraceStructuralRowComparator(
                        trace, ToleranceConfig.DEFAULT, 0,
                        List::of, () -> null);
        TraceRunReplayWalker.DynamicArtSegmentComparison dynamicArt =
                new TraceRunReplayWalker.DynamicArtSegmentComparison(
                        trace, trace.frameCount());
        TraceRunReplayWalker.BoundaryProbe observer = boundaryProbe();
        observer.setDelegate(comparator);
        RetainingSlotProbe slotProbe = new RetainingSlotProbe(trace);
        return new InstalledRoots(
                this, descriptors, lease, trace, null,
                observer, comparator, comparator, trace::frameCount,
                new FixtureConsumer(trace, null), comparator, structural,
                null, null, dynamicArt, slotProbe);
    }

    private InstalledRoots specialRoots(
            ActiveSegmentPayload lease,
            TraceData trace,
            TraceRunSpecialStageRows rows,
            SegmentTarget target,
            DescriptorSet descriptors) {
        TraceRunSpecialStageRowDriver driver =
                new TraceRunSpecialStageRowDriver(rows, trace);
        SpecialStageRunObjectsPassBinder binder =
                rows.newRunObjectsPassBinder().orElse(null);
        if (binder != null) {
            assertTrue(binder.hasRemaining(),
                    "the representative S2 pass binder must own recorded passes");
        }
        TraceRunReplayWalker.DynamicArtSegmentComparison dynamicArt =
                new TraceRunReplayWalker.DynamicArtSegmentComparison(
                        trace, rows.rowCount(), rows.normalizedDynamicArtRows());
        SpecialObserver delegate = new SpecialObserver(driver, binder);
        TraceRunReplayWalker.BoundaryProbe observer = boundaryProbe();
        observer.setDelegate(delegate);
        RetainingSlotProbe slotProbe = new RetainingSlotProbe(trace);
        return new InstalledRoots(
                this, descriptors, lease, trace, rows,
                observer, delegate, new SpecialHud(driver), trace::frameCount,
                new FixtureConsumer(trace, rows), null, null,
                driver, binder, dynamicArt, slotProbe);
    }

    private static DescriptorSet planDescriptorSet(List<RunSource> sources)
            throws IOException {
        return new DescriptorSet(
                TraceRunReplayWalker.planDescriptors(
                        sources.get(0).manifest(), sources.get(0).directory()),
                TraceRunReplayWalker.planDescriptors(
                        sources.get(1).manifest(), sources.get(1).directory()),
                TraceRunReplayWalker.planDescriptors(
                        sources.get(2).manifest(), sources.get(2).directory()));
    }

    private static void warmAndReleasePlanners(List<RunSource> sources)
            throws IOException {
        for (RunSource source : sources) {
            List<TraceRunReplayWalker.SegmentPlan> eager =
                    TraceRunReplayWalker.plan(
                            source.manifest(), source.directory());
            Reference.reachabilityFence(eager);
            eager = null;
            forcedGcHeapBytes();
            List<TraceRunSegmentDescriptor> descriptors =
                    TraceRunReplayWalker.planDescriptors(
                            source.manifest(), source.directory());
            Reference.reachabilityFence(descriptors);
            descriptors = null;
            forcedGcHeapBytes();
        }
    }

    private static RunSource source(
            Path projectRoot, String game, String runId) throws IOException {
        Path directory = projectRoot.resolve(
                "src/test/resources/traces/" + game + "/runs/" + runId);
        return new RunSource(directory, TraceRunManifest.load(
                directory.resolve("run_manifest.json")));
    }

    private static SegmentTarget firstSpecial(
            String game, List<TraceRunSegmentDescriptor> descriptors) {
        for (int index = 0; index < descriptors.size(); index++) {
            if ("special_stage".equals(
                    descriptors.get(index).segment().kind())) {
                return new SegmentTarget(game, index, descriptors.get(index));
            }
        }
        throw new IllegalStateException("no representative special segment for " + game);
    }

    private static TraceRunReplayWalker.BoundaryProbe boundaryProbe() {
        return new TraceRunReplayWalker.BoundaryProbe(
                new TraceRunReplayWalker.EngineHooks() {
                    @Override
                    public int currentBk2Frame() {
                        return 0;
                    }

                    @Override
                    public BonusStageType peekBonusRequest() {
                        return null;
                    }

                    @Override
                    public boolean isSpecialStageRequested() {
                        return false;
                    }

                    @Override
                    public GameMode currentMode() {
                        return GameMode.LEVEL;
                    }
                });
    }

    private static int linuxOpenFileDescriptorCount() throws IOException {
        Path descriptors = Path.of("/proc/self/fd");
        if (!Files.isDirectory(descriptors)) {
            return -1;
        }
        try (var entries = Files.list(descriptors)) {
            return Math.toIntExact(entries.count());
        }
    }

    private static long forcedGcHeapBytes() {
        System.gc();
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private record RunSource(Path directory, TraceRunManifest manifest) {
    }

    private record SegmentTarget(
            String game, int index, TraceRunSegmentDescriptor descriptor) {
        private String label() {
            return game + "-" + index + "-" + descriptor.segment().dir();
        }
    }

    private record DescriptorMeasurement(int s3kSegmentCount, long retainedBytes) {
    }

    private record InstalledMeasurement(
            long maxBytes,
            String maxSegment,
            boolean sampledS1Special,
            boolean sampledS2Special,
            boolean sampledPassBinder) {
    }

    private record InstalledSample(
            long retainedBytes, boolean passBinderInstalled) {
    }

    private record FixtureConsumer(
            TraceData trace, TraceRunSpecialStageRows rows) {
    }

    private record DescriptorSet(
            List<TraceRunSegmentDescriptor> s3k,
            List<TraceRunSegmentDescriptor> s1,
            List<TraceRunSegmentDescriptor> s2) {
        private DescriptorSet {
            s3k = List.copyOf(s3k);
            s1 = List.copyOf(s1);
            s2 = List.copyOf(s2);
        }

        private void fence() {
            Reference.reachabilityFence(s3k);
            Reference.reachabilityFence(s1);
            Reference.reachabilityFence(s2);
            Reference.reachabilityFence(this);
        }
    }

    private static final class InstalledRoots {
        private Object session;
        private DescriptorSet descriptors;
        private ActiveSegmentPayload activeLease;
        private TraceData trace;
        private TraceRunSpecialStageRows rows;
        private PlaybackDebugManager.PlaybackFrameObserver playbackObserver;
        private PlaybackDebugManager.PlaybackFrameObserver boundaryDelegate;
        private TraceHudModel hudSupplier;
        private Supplier<Integer> cameraSupplier;
        private FixtureConsumer fixture;
        private LiveTraceComparator comparator;
        private TraceStructuralRowComparator structuralComparator;
        private TraceRunSpecialStageRowDriver specialDriver;
        private SpecialStageRunObjectsPassBinder passBinder;
        private TraceRunReplayWalker.DynamicArtSegmentComparison dynamicArt;
        private RetainingSlotProbe slotProbe;

        private InstalledRoots(
                Object session,
                DescriptorSet descriptors,
                ActiveSegmentPayload activeLease,
                TraceData trace,
                TraceRunSpecialStageRows rows,
                PlaybackDebugManager.PlaybackFrameObserver playbackObserver,
                PlaybackDebugManager.PlaybackFrameObserver boundaryDelegate,
                TraceHudModel hudSupplier,
                Supplier<Integer> cameraSupplier,
                FixtureConsumer fixture,
                LiveTraceComparator comparator,
                TraceStructuralRowComparator structuralComparator,
                TraceRunSpecialStageRowDriver specialDriver,
                SpecialStageRunObjectsPassBinder passBinder,
                TraceRunReplayWalker.DynamicArtSegmentComparison dynamicArt,
                RetainingSlotProbe slotProbe) {
            this.session = session;
            this.descriptors = descriptors;
            this.activeLease = activeLease;
            this.trace = trace;
            this.rows = rows;
            this.playbackObserver = playbackObserver;
            this.boundaryDelegate = boundaryDelegate;
            this.hudSupplier = hudSupplier;
            this.cameraSupplier = cameraSupplier;
            this.fixture = fixture;
            this.comparator = comparator;
            this.structuralComparator = structuralComparator;
            this.specialDriver = specialDriver;
            this.passBinder = passBinder;
            this.dynamicArt = dynamicArt;
            this.slotProbe = slotProbe;
        }

        private void fenceAfterSample() {
            Reference.reachabilityFence(session);
            Reference.reachabilityFence(descriptors);
            Reference.reachabilityFence(activeLease);
            Reference.reachabilityFence(trace);
            Reference.reachabilityFence(rows);
            Reference.reachabilityFence(playbackObserver);
            Reference.reachabilityFence(boundaryDelegate);
            Reference.reachabilityFence(hudSupplier);
            Reference.reachabilityFence(cameraSupplier);
            Reference.reachabilityFence(fixture);
            Reference.reachabilityFence(comparator);
            Reference.reachabilityFence(structuralComparator);
            Reference.reachabilityFence(specialDriver);
            Reference.reachabilityFence(passBinder);
            Reference.reachabilityFence(dynamicArt);
            Reference.reachabilityFence(slotProbe);
            Reference.reachabilityFence(this);
        }

        private void detach() {
            if (playbackObserver instanceof TraceRunReplayWalker.BoundaryProbe probe) {
                probe.setDelegate(null);
            }
            if (boundaryDelegate instanceof SpecialObserver specialObserver) {
                specialObserver.detach();
            }
            if (slotProbe != null) {
                slotProbe.close();
            }
            session = null;
            descriptors = null;
            activeLease = null;
            trace = null;
            rows = null;
            playbackObserver = null;
            boundaryDelegate = null;
            hudSupplier = null;
            cameraSupplier = null;
            fixture = null;
            comparator = null;
            structuralComparator = null;
            specialDriver = null;
            passBinder = null;
            dynamicArt = null;
            slotProbe = null;
        }
    }

    private static final class RetainingSlotProbe {
        private TraceData trace;

        private RetainingSlotProbe(TraceData trace) {
            this.trace = trace;
        }

        private void close() {
            trace = null;
        }
    }

    private static final class SpecialObserver
            implements PlaybackDebugManager.PlaybackFrameObserver {
        private TraceRunSpecialStageRowDriver driver;
        private SpecialStageRunObjectsPassBinder binder;

        private SpecialObserver(
                TraceRunSpecialStageRowDriver driver,
                SpecialStageRunObjectsPassBinder binder) {
            this.driver = driver;
            this.binder = binder;
        }

        @Override
        public boolean shouldSkipGameplayTick(Bk2FrameInput frame) {
            return false;
        }

        @Override
        public void afterFrameAdvanced(Bk2FrameInput frame, boolean skipped) {
        }

        private void detach() {
            driver = null;
            binder = null;
        }
    }

    private static final class SpecialHud implements TraceHudModel {
        private final TraceRunSpecialStageRowDriver driver;

        private SpecialHud(TraceRunSpecialStageRowDriver driver) {
            this.driver = driver;
        }

        @Override public int errorCount() { return 0; }
        @Override public int warningCount() { return 0; }
        @Override public int laggedFrames() { return 0; }
        @Override public int recentActionMask() { return 0; }
        @Override public int recentInputMask() { return 0; }
        @Override public boolean recentStartPressed() { return false; }
        @Override public List<MismatchEntry> recentMismatches() { return List.of(); }
        @Override public boolean hasRecordingDesync() { return false; }
        @Override public boolean isComplete() { return driver.isComplete(); }
    }
}
