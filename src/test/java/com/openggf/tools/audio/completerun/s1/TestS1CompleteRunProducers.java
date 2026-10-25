package com.openggf.tools.audio.completerun.s1;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.tools.audio.completerun.CompleteRunAudioProducer;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestS1CompleteRunProducers {
    @TempDir Path temporary;

    @Test void bothMissingProducerClassesNowExistAndRefuseUnavailableBindingBeforeInputs() throws Exception {
        Path existing = Files.writeString(temporary.resolve("existing"), "keep");
        var nativeFailure = assertThrows(IllegalStateException.class,
                () -> new S1CompleteRunReferenceProducer().capture(request(ProducerKind.REFERENCE,
                        S1CompleteRunAudioProfile.ID, existing)));
        assertTrue(nativeFailure.getMessage().contains("unavailable"));
        assertTrue(nativeFailure.getMessage().contains("duplicate emerald-run evidence"));
        Exception engineFailure = assertThrows(Exception.class,
                () -> new S1CompleteRunOpenGgfProducer().capture(request(ProducerKind.OPENGGF,
                        S1CompleteRunAudioProfile.ID, existing)));
        assertTrue(engineFailure.getMessage().contains("not installed"));
        assertEquals("keep", Files.readString(existing));
        for (CompleteRunAudioProducer producer : List.of(new S1CompleteRunReferenceProducer(),
                new S1CompleteRunOpenGgfProducer())) {
            Path output = temporary.resolve("uncreated-" + producer.getClass().getSimpleName());
            ProducerKind kind = producer instanceof S1CompleteRunReferenceProducer
                    ? ProducerKind.REFERENCE : ProducerKind.OPENGGF;
            assertThrows(Exception.class, () -> producer.capture(request(kind, S1CompleteRunAudioProfile.ID, output)));
            assertFalse(Files.exists(output));
        }
    }

    @Test void fixedEntryPointsRejectWrongKindAndProfileWithoutInputs() {
        Path output = temporary.resolve("absent");
        assertThrows(IllegalArgumentException.class, () -> new S1CompleteRunReferenceProducer()
                .capture(request(ProducerKind.OPENGGF, S1CompleteRunAudioProfile.ID, output)));
        assertThrows(IllegalArgumentException.class, () -> new S1CompleteRunReferenceProducer()
                .capture(request(ProducerKind.REFERENCE, "other", output)));
        assertThrows(IllegalArgumentException.class, () -> new S1CompleteRunOpenGgfProducer()
                .capture(request(ProducerKind.REFERENCE, S1CompleteRunAudioProfile.ID, output)));
        assertThrows(IllegalArgumentException.class, () -> new S1CompleteRunOpenGgfProducer()
                .capture(request(ProducerKind.OPENGGF, "other", output)));
        assertFalse(Files.exists(output));
    }

    @Test void noRuntimePinsOrUnavailableLayerClaimsAreInventedByInstallation() {
        var profile = S1CompleteRunAudioProfile.profile();
        assertTrue(profile.producerRuntimeIdentities().isEmpty());
        assertTrue(profile.observerRuntimeIdentities().isEmpty());
        assertTrue(profile.observerProofs().isEmpty());
        assertTrue(profile.producerBindings().values().stream()
                .allMatch(CompleteRunAudioTrace.UnavailableProducerBinding.class::isInstance));
        for (var inventory : profile.producerObservationInventories().values()) {
            for (var layer : CompleteRunAudioTrace.ComparisonLayer.values()) assertFalse(inventory.isObserved(layer));
        }
    }

    private CompleteRunAudioProducer.Request request(ProducerKind kind, String id, Path output) {
        Path missing = temporary.resolve("missing");
        return new CompleteRunAudioProducer.Request(kind, id, missing.resolve("rom.gen"),
                missing.resolve("emerald.bk2"), missing.resolve("manifest.json"),
                missing.resolve("tracechaser"), output);
    }
}
