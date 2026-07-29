package com.openggf.game.resources;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlcObjectOwnedFadeLifecycle {

    @Test
    void bothResultsObjectsCloseTheOutgoingMarkerBeforeTransitionCallbacks()
            throws IOException {
        assertMarkedResultsObject(
                "com/openggf/game/sonic1/objects/Sonic1ResultsScreenObjectInstance.java");
        assertMarkedResultsObject(
                "com/openggf/game/sonic2/objects/ResultsScreenObjectInstance.java");
    }

    private static void assertMarkedResultsObject(String relative) throws IOException {
        String source = Files.readString(Path.of("src/main/java").resolve(relative));
        assertTrue(source.contains(
                "nativeFadeLifecycle().beginNativeBlockingFade()"));
        assertTrue(source.contains("marker.wrapCompletion(() ->"));
        assertTrue(source.contains(
                "var reveal = services().nativeFadeLifecycle().beginNativeBlockingFade()"));
        assertTrue(source.contains("reveal.wrapCompletion(() -> { })"));
    }
}
