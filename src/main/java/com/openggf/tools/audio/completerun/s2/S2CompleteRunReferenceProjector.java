package com.openggf.tools.audio.completerun.s2;

import com.openggf.tools.audio.completerun.CompleteRunAudioCaptureStore;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Transactional projection of validated S2 native observations into canonical records. */
public final class S2CompleteRunReferenceProjector {
    public record Projection(List<CompleteRunAudioTrace.Record> records) {
        public Projection { records = List.copyOf(records); }
    }

    Projection projectPrefixForTesting(Path raw, Path rom) throws IOException {
        S2CompleteRunAssetCatalog catalog = S2CompleteRunAssetCatalog.load(rom);
        Transaction transaction = new Transaction(catalog);
        S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, transaction);
        return transaction.result();
    }

    void projectPrefixForTesting(Path raw, Path rom, Path output) throws IOException {
        Projection projection = projectPrefixForTesting(raw, rom);
        CompleteRunAudioTrace.Metadata metadata = syntheticMetadata(projection.records());
        projectToStore(raw, rom, output, metadata, null, false);
    }

    void project(Path raw, Path rom, Path output, CompleteRunAudioTrace.Metadata metadata,
            CompleteRunAudioTrace.NativeCapabilitySummary capability) throws IOException {
        metadata.validateFixtureProfile(S2CompleteRunAudioProfile.profile());
        projectToStore(raw, rom, output, metadata, capability, true);
    }

    private void projectToStore(Path raw, Path rom, Path output, CompleteRunAudioTrace.Metadata metadata,
            CompleteRunAudioTrace.NativeCapabilitySummary capability, boolean full) throws IOException {
        S2CompleteRunAssetCatalog catalog = S2CompleteRunAssetCatalog.load(rom);
        var writer = new CompleteRunAudioCaptureStore().writeNew(output, metadata);
        Transaction transaction = new Transaction(catalog, writer, capability);
        boolean complete = false;
        try {
            if (full) S2CompleteRunReferenceRawAdapter.scan(raw, transaction);
            else S2CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, transaction);
            complete = true;
        } finally {
            if (!complete) writer.abort();
        }
    }

    private static CompleteRunAudioTrace.Metadata syntheticMetadata(
            List<CompleteRunAudioTrace.Record> records) {
        var baseline = (CompleteRunAudioTrace.Baseline) records.getFirst();
        int end = ((CompleteRunAudioTrace.Frame) records.get(records.size() - 2)).absoluteFrame() + 1;
        var fixture = S2CompleteRunAudioProfile.profile().fixture();
        var interval = new CompleteRunAudioTrace.CompleteRunFixture(fixture.romSha1(), fixture.romCrc32(),
                fixture.bk2Sha256(), fixture.bk2RowCount(), fixture.runManifestSha256(),
                List.of(new CompleteRunAudioTrace.ManifestSegment("test-prefix", baseline.absoluteFrame(), end)),
                baseline.absoluteFrame(), end);
        return syntheticMetadata(interval, "test-only." + S2CompleteRunAudioProfile.ID);
    }

    static CompleteRunAudioTrace.Metadata fullSyntheticMetadataForTesting() {
        return syntheticMetadata(S2CompleteRunAudioProfile.profile().fixture(), S2CompleteRunAudioProfile.ID);
    }

    private static CompleteRunAudioTrace.Metadata syntheticMetadata(
            CompleteRunAudioTrace.CompleteRunFixture interval, String profileId) {
        var pinned = S2CompleteRunAudioProfile.profile();
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

    private static final class Transaction implements S2CompleteRunReferenceRawAdapter.Sink {
        private final S2CompleteRunAssetCatalog catalog;
        private final List<CompleteRunAudioTrace.Record> staged = new ArrayList<>();
        private final CompleteRunAudioCaptureStore.Writer writer;
        private final CompleteRunAudioTrace.NativeCapabilitySummary capability;
        private boolean started;
        private boolean committed;
        private int ymPort0Latch;
        private int ymPort1Latch;

        private Transaction(S2CompleteRunAssetCatalog catalog) { this(catalog, null, null); }

        private Transaction(S2CompleteRunAssetCatalog catalog, CompleteRunAudioCaptureStore.Writer writer,
                CompleteRunAudioTrace.NativeCapabilitySummary capability) {
            this.catalog = catalog;
            this.writer = writer;
            this.capability = capability;
        }

        @Override public void begin() { started = true; }
        @Override public void header(S2CompleteRunReferenceRawAdapter.Header value) { }

        @Override public void baseline(S2CompleteRunReferenceRawAdapter.RawBoundary value) throws IOException {
            requireStarted();
            if (!value.pendingDescendants().isEmpty()) unsupportedFrontier();
            ymPort0Latch = value.ymPort0Latch();
            ymPort1Latch = value.ymPort1Latch();
            append(new CompleteRunAudioTrace.Baseline(value.row(), normalize(value.driverState()),
                    S2CompleteRunAudioProfile.profile().baselineRoleOwners()));
        }

        @Override public void frame(S2CompleteRunReferenceRawAdapter.RawFrame value) throws IOException {
            requireStarted();
            List<CompleteRunAudioTrace.ChipEvent> chips = chips(value.events());
            var state = normalize(value.driverState());
            var service = new CompleteRunAudioTrace.DriverService(0, "native-observation",
                    CompleteRunAudioTrace.ServiceCompletion.COMPLETED, List.of(), state, chips);
            append(new CompleteRunAudioTrace.Frame(value.row(), segment(value.row()), value.lag(),
                    List.of(), List.of(service), chips, null));
        }

        @Override public void cutoff(S2CompleteRunReferenceRawAdapter.RawBoundary value) throws IOException {
            requireStarted();
            if (!value.activeServices().isEmpty() || !value.pendingDescendants().isEmpty()) unsupportedFrontier();
            append(new CompleteRunAudioTrace.CutoffFrontier(List.of(), List.of(), List.of(), null,
                    value.ymPort0Latch(), value.ymPort1Latch(), normalize(value.driverState())));
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
            if (!committed) throw new IllegalStateException("S2 projection did not commit");
            return new Projection(staged);
        }

        private CompleteRunAudioTrace.NormalizedState normalize(byte[] raw) {
            var result = S2CompleteRunStateNormalizer.normalizeReference(
                    S2CompleteRunStateDecoder.decode(raw, catalog), catalog.assets());
            S2CompleteRunAudioProfile.profile().validateState(result);
            return result;
        }

        private List<CompleteRunAudioTrace.ChipEvent> chips(
                List<S2CompleteRunReferenceRawAdapter.RawEvent> events) {
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
                } else if (event.kind() < 1 || event.kind() > 10) {
                    throw new IllegalArgumentException("S2 raw semantic event is unsupported");
                }
            }
            return List.copyOf(result);
        }

        private void requireStarted() {
            if (!started) throw new IllegalStateException("S2 projection transaction is not open");
        }

        private static void unsupportedFrontier() {
            throw new IllegalArgumentException("S2 native service frontier has no approved canonical mapping");
        }

        private static String segment(int row) {
            return S2CompleteRunAudioProfile.profile().fixture().segments().stream()
                    .filter(value -> row >= value.firstFrame() && row < value.exclusiveEnd())
                    .map(CompleteRunAudioTrace.ManifestSegment::id).findFirst().orElse(null);
        }
    }
}
