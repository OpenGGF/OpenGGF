package com.openggf;

import com.openggf.game.resources.DynamicArtDiagnosticsProvider;
import com.openggf.game.resources.DynamicArtLifecycleService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the production ordering seam used by visual named-run DPLC audit.
 */
class TestGameLoopTraceRunPostIteration {

    @Test
    void loopDelegatesTraceIterationBracketingToTraceSessionOwner() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/openggf/GameLoop.java"));
        int step = source.indexOf("private void stepInternal()");
        int delegated = source.indexOf(
                "TraceSessionLauncher.runProductionIterationIfActive(", step);

        assertTrue(step >= 0 && delegated > step,
                "GameLoop must delegate trace-specific iteration bracketing "
                        + "to TraceSessionLauncher");
        assertFalse(source.substring(step,
                        source.indexOf("private void stepInternalBody()", step))
                        .contains("traceSession.afterProductionIteration()"),
                "GameLoop must not own trace-specific exception choreography");
    }

    @Test
    void diagnosticsServiceExposesNoRegistrationOrCapableReference() {
        assertTrue(Arrays.stream(
                        DynamicArtDiagnosticsProvider.class
                                .getDeclaredMethods())
                .allMatch(method -> method.getParameterCount() == 0));
        assertTrue(Arrays.stream(
                        DynamicArtLifecycleService.class.getDeclaredFields())
                .noneMatch(field -> field.getType().getName()
                        .startsWith("java.util.function")));
        assertFalse(Arrays.stream(
                        DynamicArtLifecycleService.class.getDeclaredFields())
                .anyMatch(field -> field.getType().getName()
                        .startsWith("com.openggf.trace")));
        assertEquals(2, DynamicArtDiagnosticsProvider.class
                .getDeclaredMethods().length);
    }
}
