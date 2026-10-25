package com.openggf.tools.audio.completerun.s1;

import com.openggf.tools.audio.completerun.CompleteRunAudioCaptureStore;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ComparisonLayer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects S1 raw.v1 bus writes into canonical frame chips, without interpreting
 * managed snapshots as canonical state. Origin: native-gap audio evidence task.
 */
public final class S1CompleteRunReferenceProjector {
    record Projection(List<CompleteRunAudioTrace.Record> records) {
        Projection { records = List.copyOf(records); }
    }

    Projection projectPrefixForTesting(Path raw) throws IOException {
        return projectRecords(raw, false);
    }

    private Projection projectRecords(Path raw, boolean full) throws IOException {
        Transaction transaction = new Transaction(null);
        if (full) S1CompleteRunReferenceRawAdapter.scan(raw, transaction);
        else S1CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, transaction);
        return new Projection(transaction.result());
    }

    /** Streaming production seam: the authenticated caller owns writer metadata and publication. */
    void project(Path raw, CompleteRunAudioCaptureStore.Writer writer) throws IOException {
        write(raw, writer, true);
    }

    private void write(Path raw, CompleteRunAudioCaptureStore.Writer writer, boolean full) throws IOException {
        try {
            Transaction transaction = new Transaction(writer);
            if (full) S1CompleteRunReferenceRawAdapter.scan(raw, transaction);
            else S1CompleteRunReferenceRawAdapter.scanPrefixForTesting(raw, transaction);
        } catch (IOException | RuntimeException | Error failure) {
            try { writer.abort(); }
            catch (IOException | RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** Metadata comes from the authenticated caller; this method supplies no runtime identity. */
    void project(Path raw, Path output, CompleteRunAudioTrace.Metadata metadata) throws IOException {
        project(raw, output, metadata, true);
    }

    void projectPrefixForTesting(Path raw, Path output, CompleteRunAudioTrace.Metadata metadata)
            throws IOException {
        project(raw, output, metadata, false);
    }

    private void project(Path raw, Path output, CompleteRunAudioTrace.Metadata metadata, boolean full)
            throws IOException {
        for (ComparisonLayer layer : ComparisonLayer.values()) {
            if (metadata.producerObservationInventory().isObserved(layer)
                    != (layer == ComparisonLayer.FRAME_CHIP_EVENTS)) {
                throw new IllegalArgumentException("S1 raw.v1 projection requires frame-chips-only inventory");
            }
        }
        if (metadata.observerRuntimeIdentity() instanceof CompleteRunAudioTrace.BufferedNativeObserverIdentity) {
            throw new IllegalArgumentException("S1 raw.v1 omits the terminal native frontier; "
                    + "buffered-native canonical publication is unavailable");
        }
        var writer = new CompleteRunAudioCaptureStore().writeNew(output, metadata);
        write(raw, writer, full);
    }

    private static final class Transaction implements S1CompleteRunReferenceRawAdapter.Sink {
        private final CompleteRunAudioCaptureStore.Writer writer;
        private final List<CompleteRunAudioTrace.Record> staged = new ArrayList<>();
        private int port0, port1;
        private long nextChip;
        private boolean committed;

        private Transaction(CompleteRunAudioCaptureStore.Writer writer) { this.writer = writer; }
        @Override public void begin() { }

        @Override public void baseline(S1CompleteRunReferenceRawAdapter.Baseline value) throws IOException {
            port0 = value.ymPort0Latch();
            port1 = value.ymPort1Latch();
            append(new CompleteRunAudioTrace.Baseline(value.row(), null, null,
                    new CompleteRunAudioTrace.BoundaryFrontier(null, null, null, null, null, null)));
        }

        @Override public void frame(S1CompleteRunReferenceRawAdapter.Frame value) throws IOException {
            List<CompleteRunAudioTrace.ChipEvent> chips = new ArrayList<>();
            for (var event : value.events()) {
                if (event.kind() == 8) {
                    port0 = port1 = 0;
                } else if (event.kind() == 3) {
                    int port = event.subject() >>> 1;
                    if ((event.subject() & 1) == 0) {
                        if (port == 0) port0 = event.value(); else port1 = event.value();
                    } else {
                        chips.add(new CompleteRunAudioTrace.YmWrite(nextChip++, port,
                                port == 0 ? port0 : port1, event.value()));
                    }
                } else if (event.kind() == 4) {
                    chips.add(new CompleteRunAudioTrace.PsgWrite(nextChip++, event.value()));
                }
            }
            String segment = S1CompleteRunAudioProfile.profile().fixture().segments().stream()
                    .filter(item -> item.firstFrame() <= value.row() && value.row() < item.exclusiveEnd())
                    .map(CompleteRunAudioTrace.ManifestSegment::id).findFirst().orElse(null);
            append(new CompleteRunAudioTrace.Frame(value.row(), segment, null, null, null,
                    null, null, chips, null));
        }

        @Override public void terminal(int exclusiveEnd) throws IOException {
            // raw.v1 has no serialized native cutoff frontier. Null means unobserved,
            // even when native async services are still open at the movie terminal.
            append(new CompleteRunAudioTrace.CutoffFrontier(null, null, null, null, null, null, null));
        }

        @Override public void commit() throws IOException {
            if (writer != null) {
                writer.finish(null);
                writer.close();
            }
            committed = true;
        }

        @Override public void abort() throws IOException {
            staged.clear();
            if (writer != null) writer.abort();
            committed = false;
        }

        private void append(CompleteRunAudioTrace.Record record) throws IOException {
            if (writer == null) staged.add(record); else writer.append(record);
        }

        private List<CompleteRunAudioTrace.Record> result() {
            if (!committed) throw new IllegalStateException("S1 projection did not commit");
            return staged;
        }
    }
}
