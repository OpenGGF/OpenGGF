package com.openggf.game.resources;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlcProviderOwnedFadeLifecycle {

    @Test
    void endingProvidersReceiveTheLifecycleCapabilityAndWrapCallbacks()
            throws IOException {
        String s1 = source(
                "com/openggf/game/sonic1/credits/Sonic1EndingProvider.java");
        String s2 = source(
                "com/openggf/game/sonic2/credits/Sonic2EndingProvider.java");

        assertTrue(s1.contains("implements EndingProvider, NativeFadeLifecycleAware"));
        assertTrue(s1.contains("bindNativeFadeLifecycle("));
        assertTrue(s1.contains("beginNativeBlockingFade()"));
        assertTrue(s2.contains("implements EndingProvider, NativeFadeLifecycleAware"));
        assertTrue(s2.contains("bindNativeFadeLifecycle("));
        assertTrue(s2.contains(
                "beginNativeBlockingFade().wrapCompletion(completion)"));
    }

    @Test
    void creditsManagerUsesInjectedLifecycleWithoutSessionLookups()
            throws IOException {
        String credits = source(
                "com/openggf/game/sonic1/credits/Sonic1CreditsManager.java");
        assertTrue(credits.contains("NativeFadeLifecycle nativeFadeLifecycle"));
        assertTrue(credits.contains(
                "nativeFadeLifecycle.beginNativeBlockingFade().wrapCompletion(completion)"));
        assertTrue(!credits.contains("SessionManager"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(Path.of("src/main/java").resolve(relative));
    }
}
