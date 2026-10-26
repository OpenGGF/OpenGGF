package com.openggf.tools.audio.completerun;

import com.openggf.tools.audio.completerun.CompleteRunAudioProducer.Request;
import com.openggf.tools.audio.completerun.CompleteRunAudioTrace.ProducerKind;
import com.openggf.tools.audio.completerun.s2.S2CompleteRunAudioProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class TestCompleteRunAudioProducerMetadata {
    @TempDir Path temporary;

    @Test
    void engineStartupRejectsAReferenceTypedRequestBeforeInputs() {
        var poison = temporary.resolve("absent");
        var request = new Request(ProducerKind.REFERENCE, S2CompleteRunAudioProfile.ID,
                poison.resolve("rom"), poison.resolve("movie"), poison.resolve("manifest"),
                null, temporary.resolve("out"));
        assertThrows(IllegalArgumentException.class, () ->
                CompleteRunAudioOpenGgfProducerSupport.capture(request, S2CompleteRunAudioProfile.ID));
    }

    @Test
    void referencePublicationStopsBeforeOpeningAnUnreviewedInstallation() {
        var poison = temporary.resolve("absent");
        var request = new Request(ProducerKind.REFERENCE, S2CompleteRunAudioProfile.ID,
                poison.resolve("rom"), poison.resolve("movie"), poison.resolve("manifest"),
                poison.resolve("home"), temporary.resolve("out"));
        Exception failure = assertThrows(Exception.class,
                () -> CompleteRunAudioProducerMetadata.reference(request, S2CompleteRunAudioProfile.ID));
        assertTrue(failure.getMessage().contains("reviewed duplicate capture"), failure::getMessage);
        assertFalse(Files.exists(request.output()));
    }
}
