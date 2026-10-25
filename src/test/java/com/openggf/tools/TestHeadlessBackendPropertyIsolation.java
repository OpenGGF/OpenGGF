package com.openggf.tools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/** Replays the real property polluter without connecting to any display. */
class TestHeadlessBackendPropertyIsolation {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"surfaceless-egl", "x11"})
    void globallyRegisteredTeardownRestoresTheBackendAfterTheHistoricalPolluter(String incoming) {
        String previous = System.getProperty(SurfacelessEglContext.PROPERTY);
        try {
            restore(incoming);
            var listener = new SummaryGeneratingListener();
            var request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectMethod(TestSurfacelessEglContext.class,
                            "selectionIsOptInAndRejectsUnknownBackends"))
                    .build();
            LauncherFactory.create().execute(request, listener);

            var summary = listener.getSummary();
            assertEquals(1, summary.getTestsSucceededCount(),
                    () -> "the historical polluter must execute: " + summary.getFailures());
            assertEquals(0, summary.getTestsSkippedCount());
            assertEquals(0, summary.getTotalFailureCount(),
                    () -> "JUnit lifecycle failed: " + summary.getFailures());
            assertEquals(incoming, System.getProperty(SurfacelessEglContext.PROPERTY),
                    "HeadlessStateTeardownExtension must restore the caller's graphics backend "
                            + "after each top-level class; clearing it makes later boots use GLFW");
        } finally {
            restore(previous);
        }
    }

    private static void restore(String value) {
        if (value == null) {
            System.clearProperty(SurfacelessEglContext.PROPERTY);
        } else {
            System.setProperty(SurfacelessEglContext.PROPERTY, value);
        }
    }
}
