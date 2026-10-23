package com.openggf.tools.audio.completerun.s1;

import static com.openggf.tools.audio.completerun.s1.S1RawContractVectors.*;
import static org.junit.jupiter.api.Assertions.*;

import com.openggf.tools.audio.completerun.CompleteRunAudioCaptureStore;
import com.openggf.tools.audio.completerun.CompleteRunAudioObservationInventories;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS1CompleteRunReferenceProjector {
    @TempDir Path temporary;

    @Test void preservesBusOrderAddressLatchesAndGlobalChipOrdinalsAcrossRows() throws Exception {
        var records = new S1CompleteRunReferenceProducer().capturePipelineForTesting(raw(prefix())).records();
        assertInstanceOf(Baseline.class, records.getFirst());
        var first = (Frame) records.get(1);
        var second = (Frame) records.get(2);
        assertEquals(List.of(new YmWrite(0, 0, 42, 127), new YmWrite(1, 1, 180, 192)), first.chipEvents());
        assertEquals(List.of(new PsgWrite(2, 159), new YmWrite(3, 1, 180, 64)), second.chipEvents());
        assertEquals("ghz1", first.segment());
        assertNull(first.postRowState());
        assertNull(first.services());
        assertNull(first.requests());
        assertNull(first.decisions());
        assertNull(first.lag());
        var cutoff = (CutoffFrontier) records.getLast();
        assertNull(cutoff.activeStack());
        assertNull(cutoff.terminalState());
        assertNull(cutoff.nativeDiagnostics());
    }

    @Test void resetsBothYmAddressLatchesAtOrderedNativeReset() throws Exception {
        String reset = event(861, 0, 2, 0, 8, 1, 0, 3, 0, 0, 0, 0, 0, 1, "0")
                + event(861, 1, 2, 0, 3, 1, 0, 3, 0, 1, 0, 0, 64, 0, "0")
                + event(861, 2, 2, 0, 3, 1, 0, 3, 0, 3, 0, 0, 32, 0, "0")
                + snapshot(861, 3, 2, 0, 1, 0, 3, 0, 2)
                + event(861, 6, 2, 0, 9, 1, 0, 3, 0, 0, 0, 0, 0, 1, "0");
        var records = new S1CompleteRunReferenceProjector().projectPrefixForTesting(raw(metadata() + baseline()
                + frame(860, "") + frame(861, reset) + terminal(862))).records();
        assertEquals(List.of(new YmWrite(0, 0, 0, 64), new YmWrite(1, 1, 0, 32)),
                ((Frame) records.get(2)).chipEvents());
    }

    @Test void publishesDeterministicAtomicTestStoreThroughActualRawConversion() throws Exception {
        Path input = raw(prefix());
        Path first = temporary.resolve("first");
        Path second = temporary.resolve("second");
        var projector = new S1CompleteRunReferenceProjector();
        projector.projectPrefixForTesting(input, first, testMetadata());
        projector.projectPrefixForTesting(input, second, testMetadata());
        assertArrayEquals(Files.readAllBytes(first.resolve("manifest.json")),
                Files.readAllBytes(second.resolve("manifest.json")));
        assertArrayEquals(Files.readAllBytes(first.resolve("chunks/000000.jsonl.gz")),
                Files.readAllBytes(second.resolve("chunks/000000.jsonl.gz")));
        List<CompleteRunAudioTrace.Record> records = new ArrayList<>();
        try (var reader = new CompleteRunAudioCaptureStore().read(first)) {
            while (reader.hasNext()) records.add(reader.next());
        }
        assertInstanceOf(Terminal.class, records.getLast());
        assertEquals(4, ((Terminal) records.getLast()).ymCount() + ((Terminal) records.getLast()).psgCount());
    }

    @Test void lateFailureDeletesEveryPrivateOutputAndPreservesExistingDestination() throws Exception {
        Path output = temporary.resolve("rejected");
        assertThrows(IllegalArgumentException.class, () -> new S1CompleteRunReferenceProjector()
                .projectPrefixForTesting(raw(prefix().replace("\"overflows\":0", "\"overflows\":1")),
                        output, testMetadata()));
        assertFalse(Files.exists(output));
        try (var entries = Files.list(temporary)) {
            assertTrue(entries.noneMatch(path -> path.getFileName().toString().startsWith(".audio-staging-")));
        }
        Path existing = Files.writeString(temporary.resolve("existing"), "keep");
        assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> new S1CompleteRunReferenceProjector()
                .projectPrefixForTesting(raw(prefix()), existing, testMetadata()));
        assertEquals("keep", Files.readString(existing));
    }

    @Test void rejectsMissingObservationAndFixtureIntervalMismatchWithoutPublishing() throws Exception {
        var value = testMetadata();
        var unobserved = new Metadata(value.schema(), value.profileId(), value.fixture(), value.producerKind(),
                value.producerRuntimeIdentity(), value.observerRuntimeIdentity(), value.observerProof(),
                value.chunkPolicy(), value.hardwareRoles(), value.stateInventory(), value.comparisonLayerInventory(),
                ProducerObservationInventory.allUnobserved("test unavailable"));
        Path output = temporary.resolve("no-output");
        assertThrows(IllegalArgumentException.class, () -> new S1CompleteRunReferenceProjector()
                .projectPrefixForTesting(raw(prefix()), output, unobserved));
        assertFalse(Files.exists(output));
        assertThrows(IllegalArgumentException.class, () -> new S1CompleteRunReferenceProjector()
                .projectPrefixForTesting(raw(metadata() + baseline() + frame(860, "") + terminal(861)),
                        output, value));
        assertFalse(Files.exists(output));
    }

    private Path raw(String text) throws Exception {
        return Files.writeString(Files.createTempFile(temporary, "s1-", ".jsonl"), text);
    }

    private static Metadata testMetadata() {
        var pinned = S1CompleteRunAudioProfile.profile();
        var original = pinned.fixture();
        var fixture = new CompleteRunFixture(original.romSha1(), original.romCrc32(), original.bk2Sha256(),
                original.bk2RowCount(), original.runManifestSha256(), List.of(new ManifestSegment("ghz1", 860, 862)),
                860, 862);
        // Deliberately test-only. These hashes/identities never enter a fixed profile or runtime pin.
        return new Metadata(CompleteRunAudioTrace.SCHEMA, "test-only.s1-raw-chip-projection", fixture, ProducerKind.OPENGGF,
                new ProducerRuntimeIdentity("OpenGGF", "test-only", "OpenGGF", "test-only", "SMPS", "test-only",
                        Map.of(RuntimeArtifact.OPENGGF_PRODUCER, "4".repeat(64))),
                new CallbackObserverIdentity("test-only.s1-raw-chip-projection"),
                new ObserverProof("test-only", "test-only", List.of(new CallbackProof("projection", 1))),
                new ChunkPolicy(4096, "gzip", 0), pinned.hardwareRoles(), pinned.stateInventory(),
                pinned.comparisonLayerInventory(), CompleteRunAudioObservationInventories.frameChipsOnly(
                        "raw.v1 lacks canonical state and terminal frontier"));
    }
}
