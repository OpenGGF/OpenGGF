package com.openggf.tools.audio.completerun.s3k;

import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import com.openggf.tools.audio.completerun.CompleteRunAudioCaptureStore;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transactional projection of validated S3K native observations into canonical records. */
public final class S3kCompleteRunReferenceProjector {
    public record Projection(List<CompleteRunAudioTrace.Record> records) {
        public Projection { records = List.copyOf(records); }
    }

    Projection projectPrefixForTesting(Path raw, Path rom) throws IOException {
        S3kCompleteRunAssetCatalog catalog = S3kCompleteRunAssetCatalog.load(rom);
        Transaction transaction = new Transaction(catalog);
        S3kCompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, transaction);
        return transaction.result();
    }

    void projectPrefixForTesting(Path raw, Path rom, Path output) throws IOException {
        Projection projection = projectPrefixForTesting(raw, rom);
        CompleteRunAudioTrace.Metadata metadata = syntheticMetadata(projection.records());
        projectToStore(raw, rom, output, metadata, null, false);
    }

    void project(Path raw, Path rom, Path output, CompleteRunAudioTrace.Metadata metadata,
            CompleteRunAudioTrace.NativeCapabilitySummary capability) throws IOException {
        metadata.validateFixtureProfile(S3kCompleteRunAudioProfile.profile());
        projectToStore(raw, rom, output, metadata, capability, true);
    }

    private void projectToStore(Path raw, Path rom, Path output, CompleteRunAudioTrace.Metadata metadata,
            CompleteRunAudioTrace.NativeCapabilitySummary capability, boolean full) throws IOException {
        S3kCompleteRunAssetCatalog catalog = S3kCompleteRunAssetCatalog.load(rom);
        var writer = new CompleteRunAudioCaptureStore().writeNew(output, metadata);
        Transaction transaction = new Transaction(catalog, writer, capability);
        boolean complete = false;
        try {
            if (full) S3kCompleteRunReferenceRawAdapter.scan(raw, transaction);
            else S3kCompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, transaction);
            complete = true;
        } finally {
            if (!complete) writer.abort();
        }
    }

    private static CompleteRunAudioTrace.Metadata syntheticMetadata(
            List<CompleteRunAudioTrace.Record> records) {
        var baseline = (CompleteRunAudioTrace.Baseline) records.getFirst();
        int end = ((CompleteRunAudioTrace.Frame) records.get(records.size() - 2)).absoluteFrame() + 1;
        var fixture = S3kCompleteRunAudioProfile.profile().fixture();
        var interval = new CompleteRunAudioTrace.CompleteRunFixture(fixture.romSha1(), fixture.romCrc32(),
                fixture.bk2Sha256(), fixture.bk2RowCount(), fixture.runManifestSha256(),
                List.of(new CompleteRunAudioTrace.ManifestSegment("test-prefix", baseline.absoluteFrame(), end)),
                baseline.absoluteFrame(), end);
        return syntheticMetadata(interval, "test-only." + S3kCompleteRunAudioProfile.ID);
    }

    static CompleteRunAudioTrace.Metadata fullSyntheticMetadataForTesting() {
        return syntheticMetadata(S3kCompleteRunAudioProfile.profile().fixture(), S3kCompleteRunAudioProfile.ID);
    }

    private static CompleteRunAudioTrace.Metadata syntheticMetadata(
            CompleteRunAudioTrace.CompleteRunFixture interval, String profileId) {
        var pinned = S3kCompleteRunAudioProfile.profile();
        return new CompleteRunAudioTrace.Metadata(CompleteRunAudioTrace.SCHEMA,
                profileId, interval,
                CompleteRunAudioTrace.ProducerKind.OPENGGF,
                new CompleteRunAudioTrace.ProducerRuntimeIdentity("OpenGGF", "test-only", "OpenGGF",
                        "test-only", "SMPS", "test-only", Map.of(
                                CompleteRunAudioTrace.RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64))),
                new CompleteRunAudioTrace.CallbackObserverIdentity("openggf.projector.test.v1"),
                new CompleteRunAudioTrace.ObserverProof("test-only", "test-only",
                        List.of(new CompleteRunAudioTrace.CallbackProof("projection", 1))),
                new CompleteRunAudioTrace.ChunkPolicy(4096, "gzip", 0), pinned.hardwareRoles(),
                pinned.stateInventory(), pinned.comparisonLayerInventory());
    }

    private static final class Transaction implements S3kCompleteRunReferenceRawAdapter.Sink {
        private final S3kCompleteRunAssetCatalog catalog;
        private final List<CompleteRunAudioTrace.Record> staged = new ArrayList<>();
        private final CompleteRunAudioCaptureStore.Writer writer;
        private final CompleteRunAudioTrace.NativeCapabilitySummary capability;
        private boolean started;
        private boolean committed;
        private int ymPort0Latch;
        private int ymPort1Latch;
        private long nextEventCoordinate;
        private final Map<Integer, ObservedEvent> begins = new LinkedHashMap<>();
        private final Map<Integer, ObservedEvent> ends = new LinkedHashMap<>();
        private final Map<Integer, List<ObservedTransition>> transitions = new LinkedHashMap<>();

        private Transaction(S3kCompleteRunAssetCatalog catalog) { this(catalog, null, null); }

        private Transaction(S3kCompleteRunAssetCatalog catalog, CompleteRunAudioCaptureStore.Writer writer,
                CompleteRunAudioTrace.NativeCapabilitySummary capability) {
            this.catalog = catalog;
            this.writer = writer;
            this.capability = capability;
        }

        @Override public void begin() { started = true; }
        @Override public void header(S3kCompleteRunReferenceRawAdapter.Header value) { }

        @Override public void baseline(S3kCompleteRunReferenceRawAdapter.RawBoundary value) throws IOException {
            requireStarted();
            if (!value.activeServices().isEmpty() || !value.pendingDescendants().isEmpty()) {
                throw new IllegalArgumentException("S3K baseline frontier is not empty");
            }
            ymPort0Latch = value.ymPort0Latch();
            ymPort1Latch = value.ymPort1Latch();
            append(new CompleteRunAudioTrace.Baseline(value.row(), normalize(value.driverState()),
                    S3kCompleteRunAudioProfile.profile().baselineRoleOwners()));
        }

        @Override public void frame(S3kCompleteRunReferenceRawAdapter.RawFrame value) throws IOException {
            requireStarted();
            begins.clear();
            ends.clear();
            transitions.clear();
            for (var event : value.events()) {
                long coordinate = Math.addExact(nextEventCoordinate, event.ordinal());
                ObservedEvent observed = new ObservedEvent(value.row(), coordinate, event);
                if (event.kind() == 1) begins.put(event.serviceToken(), observed);
                else if (event.kind() == 2) ends.put(event.serviceToken(), observed);
                else if (event.kind() == 11) transitions.computeIfAbsent(event.serviceToken(),
                        ignored -> new ArrayList<>()).add(
                                new ObservedTransition(value.row(), coordinate, event));
            }
            nextEventCoordinate = Math.addExact(nextEventCoordinate, value.events().size());
            List<CompleteRunAudioTrace.ChipEvent> chips = chips(value.events());
            var state = normalize(value.driverState());
            var service = new CompleteRunAudioTrace.DriverService(0, "native-observation",
                    CompleteRunAudioTrace.ServiceCompletion.COMPLETED, List.of(), state, chips);
            append(new CompleteRunAudioTrace.Frame(value.row(), segment(value.row()), value.lag(),
                    List.of(), List.of(service), chips, null));
        }

        @Override public void cutoff(S3kCompleteRunReferenceRawAdapter.RawBoundary value) throws IOException {
            requireStarted();
            List<CompleteRunAudioTrace.FrontierService> active = value.activeServices().stream()
                    .map(this::frontierService).toList();
            List<CompleteRunAudioTrace.FrontierService> pending = value.pendingDescendants().stream()
                    .map(this::frontierService).toList();
            List<CompleteRunAudioTrace.FrontierOwnedChip> chips = java.util.stream.Stream
                    .concat(active.stream(), pending.stream())
                    .flatMap(service -> service.chipEvents().stream()
                            .map(event -> new CompleteRunAudioTrace.FrontierOwnedChip(service.token(), event)))
                    .sorted(Comparator.comparingLong(valueChip -> valueChip.event().coordinate())).toList();
            List<CompleteRunAudioTrace.FrontierOwnedSnapshot> snapshots = new ArrayList<>();
            for (var service : java.util.stream.Stream.concat(active.stream(), pending.stream()).toList()) {
                for (int index = 0; index < service.snapshots().size(); index++) {
                    snapshots.add(new CompleteRunAudioTrace.FrontierOwnedSnapshot(
                            service.token(), index, service.snapshots().get(index)));
                }
            }
            append(CompleteRunAudioTrace.CutoffFrontier.fromNative(active, pending, chips, snapshots,
                    value.ymPort0Latch(), value.ymPort1Latch(), value.nativeArmEpoch(), value.nativeArmed(),
                    normalize(value.driverState()), sha256(value.driverState())));
        }

        @Override public void commit() throws IOException {
            if (writer != null) {
                writer.finish(capability);
                writer.close();
            }
            committed = true;
        }

        @Override public void abort() throws IOException {
            if (writer != null) writer.abort();
            else staged.clear();
            committed = false;
        }

        private void append(CompleteRunAudioTrace.Record record) throws IOException {
            if (writer != null) writer.append(record);
            else staged.add(record);
        }

        private Projection result() {
            if (!committed) throw new IllegalStateException("S3K projection did not commit");
            return new Projection(staged);
        }

        private CompleteRunAudioTrace.NormalizedState normalize(byte[] raw) {
            var result = S3kCompleteRunStateNormalizer.normalizeReference(
                    S3kCompleteRunStateDecoder.decode(raw, catalog), catalog.assets());
            S3kCompleteRunAudioProfile.profile().validateState(result);
            return result;
        }

        private List<CompleteRunAudioTrace.ChipEvent> chips(
                List<S3kCompleteRunReferenceRawAdapter.RawEvent> events) {
            List<CompleteRunAudioTrace.ChipEvent> result = new ArrayList<>();
            for (var event : events) {
                if (event.kind() == 3) {
                    int port = event.subject() >>> 1;
                    if ((event.subject() & 1) == 0) {
                        if (port == 0) ymPort0Latch = event.value(); else ymPort1Latch = event.value();
                    } else {
                        int register = port == 0 ? ymPort0Latch : ymPort1Latch;
                        result.add(new CompleteRunAudioTrace.YmWrite(result.size(), port, register, event.value()));
                    }
                } else if (event.kind() == 4) {
                    result.add(new CompleteRunAudioTrace.PsgWrite(result.size(), event.value()));
                }
            }
            return List.copyOf(result);
        }

        private void requireStarted() {
            if (!started) throw new IllegalStateException("S3K projection transaction is not open");
        }

        private CompleteRunAudioTrace.FrontierService frontierService(
                S3kCompleteRunReferenceRawAdapter.RawService raw) {
            ObservedEvent begin = requireEvent(begins.get(raw.token()), "S3K cutoff service begin");
            ObservedEvent end = raw.complete() || raw.cancelled()
                    ? requireEvent(ends.get(raw.token()), "S3K cutoff service end") : null;
            requireBoundaryEvent(begin, raw, true);
            if (end != null) requireBoundaryEvent(end, raw, false);
            List<CompleteRunAudioTrace.FrontierChipEvent> chips = raw.chips().stream().map(chip ->
                    new CompleteRunAudioTrace.FrontierChipEvent(chip.coordinate(), chip.nativeOrdinal(),
                            cpu(chip.sourceCpu()), Math.toIntExact(chip.pc()), chip.eventKind(), chip.subject(),
                            chip.value(), chip.data(), chip.eventKind() == 3 ? chip.port() : null,
                            chip.eventKind() == 3 ? chip.register() : null)).toList();
            List<CompleteRunAudioTrace.FrontierSnapshot> snapshots = raw.snapshots().stream().map(snapshot ->
                    new CompleteRunAudioTrace.FrontierSnapshot(snapshot.rangeId(), cpu(snapshot.sourceCpu()),
                            Math.toIntExact(snapshot.pc()), unsigned(snapshot.bytes()))).toList();
            List<ObservedTransition> observedTransitions = transitions.getOrDefault(raw.token(), List.of());
            if (observedTransitions.size() != raw.ancestryTransitions().size()) {
                throw new IllegalArgumentException("S3K cutoff ancestry transition coordinates are incomplete");
            }
            List<CompleteRunAudioTrace.NativeAncestryTransition> ancestry = new ArrayList<>();
            for (int index = 0; index < raw.ancestryTransitions().size(); index++) {
                var transition = raw.ancestryTransitions().get(index);
                ObservedTransition observed = observedTransitions.get(index);
                var event = observed.event();
                if (event.ordinal() != transition.nativeOrdinal()
                        || observed.coordinate() != transition.coordinate()
                        || event.serviceToken() != raw.token()
                        || event.serviceKind() != raw.kind()
                        || event.parentToken() != transition.currentParentToken()
                        || event.depth() != transition.currentDepth()
                        || event.subject() != transition.hookToken()
                        || event.sourceCpu() != transition.sourceCpu()
                        || event.pc() != transition.pc()) {
                    throw new IllegalArgumentException(
                            "S3K cutoff ancestry transition differs from its observed event");
                }
                ancestry.add(new CompleteRunAudioTrace.NativeAncestryTransition(
                        transition.coordinate(), observed.frame(), transition.nativeOrdinal(),
                        transition.previousParentToken(), transition.previousDepth(),
                        transition.currentParentToken(), transition.currentDepth(), transition.hookToken(),
                        cpu(transition.sourceCpu()), Math.toIntExact(transition.pc())));
            }
            CompleteRunAudioTrace.FrontierServiceState state = raw.cancelled()
                    ? CompleteRunAudioTrace.FrontierServiceState.RESET_CANCELLED
                    : raw.complete() ? CompleteRunAudioTrace.FrontierServiceState.COMPLETED
                            : CompleteRunAudioTrace.FrontierServiceState.OPEN;
            return new CompleteRunAudioTrace.FrontierService(raw.token(), raw.parentToken(), raw.depth(),
                    kind(raw.kind()), state, begin.frame(), begin.event().ordinal(), Math.toIntExact(raw.beginPc()),
                    raw.beginHookToken(), cpu(raw.beginSourceCpu()), end == null ? null : end.frame(),
                    end == null ? null : end.event().ordinal(), end == null ? null : Math.toIntExact(raw.endPc()),
                    end == null ? null : raw.endHookToken(), snapshots, chips, raw.currentParentToken(),
                    raw.currentDepth(), ancestry);
        }

        private static ObservedEvent requireEvent(ObservedEvent value, String label) {
            if (value == null) throw new IllegalArgumentException(label + " was not observed in the raw rows");
            return value;
        }

        private static void requireBoundaryEvent(ObservedEvent observed,
                S3kCompleteRunReferenceRawAdapter.RawService service, boolean begin) {
            S3kCompleteRunReferenceRawAdapter.RawEvent event = observed.event();
            int parent = begin ? service.parentToken() : service.currentParentToken();
            int depth = begin ? service.depth() : service.currentDepth();
            long coordinate = begin ? service.beginCoordinate() : service.endCoordinate();
            long pc = begin ? service.beginPc() : service.endPc();
            int hook = begin ? service.beginHookToken() : service.endHookToken();
            int source = begin ? service.beginSourceCpu() : service.cancelled() ? 3 : service.beginSourceCpu();
            if (observed.coordinate() != coordinate || event.kind() != (begin ? 1 : 2)
                    || event.serviceToken() != service.token()
                    || event.parentToken() != parent || event.serviceKind() != service.kind()
                    || event.depth() != depth || event.pc() != pc || event.subject() != hook
                    || event.sourceCpu() != source) {
                throw new IllegalArgumentException("S3K cutoff service differs from its observed boundary event");
            }
        }

        private static List<Integer> unsigned(byte[] bytes) {
            List<Integer> result = new ArrayList<>(bytes.length);
            for (byte value : bytes) result.add(Byte.toUnsignedInt(value));
            return List.copyOf(result);
        }

        private static String cpu(int source) {
            return switch (source) { case 1 -> "Z80"; case 2 -> "M68K"; case 3 -> "RESET";
                default -> throw new IllegalArgumentException("unknown S3K native source CPU"); };
        }

        private static String kind(int id) {
            return switch (id) {
                case 1 -> "Reset"; case 2 -> "SoundDriverLoad"; case 3 -> "VInt";
                case 5 -> "BootstrapPsgInit"; case 6 -> "DriverInit"; case 7 -> "DpcmIteration";
                case 8 -> "SegaPcmIteration"; case 9 -> "DigitalAudioDispatch";
                case 10 -> "SegaPcmDispatch"; case 11 -> "UpdateEverything";
                case 12 -> "UpdateMusic";
                default -> throw new IllegalArgumentException("unknown S3K native service kind");
            };
        }

        private static String sha256(byte[] bytes) {
            try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
            catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
        }

        private static String segment(int row) {
            return S3kCompleteRunAudioProfile.profile().fixture().segments().stream()
                    .filter(value -> row >= value.firstFrame() && row < value.exclusiveEnd())
                    .map(CompleteRunAudioTrace.ManifestSegment::id).findFirst().orElse(null);
        }

        private record ObservedEvent(int frame, long coordinate,
                S3kCompleteRunReferenceRawAdapter.RawEvent event) { }
        private record ObservedTransition(int frame, long coordinate,
                S3kCompleteRunReferenceRawAdapter.RawEvent event) { }
    }
}
