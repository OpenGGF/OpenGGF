package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.util.Objects;

/** Capture-owned presentation configuration scoped around headless boot. */
final class TraceCapturePresentationSetup implements AutoCloseable {
    private final SonicConfigurationService configuration;
    private final Object previousAspect;
    private final Object previousTestMode;
    private boolean closed;

    private TraceCapturePresentationSetup(SonicConfigurationService configuration,
            Object previousAspect, Object previousTestMode) {
        this.configuration = configuration;
        this.previousAspect = previousAspect;
        this.previousTestMode = previousTestMode;
    }

    static TraceCapturePresentationSetup open(SonicConfigurationService configuration,
            TraceCaptureDimensions dimensions) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(dimensions, "dimensions");
        Object previousAspect = configuration.getConfigValue(SonicConfiguration.DISPLAY_ASPECT);
        Object previousTestMode = configuration.getConfigValue(SonicConfiguration.TEST_MODE_ENABLED);
        try {
            if (configuration.getBoolean(SonicConfiguration.TEST_MODE_ENABLED)) {
                configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, false);
            }
            configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,
                    dimensions.aspect().name());
            configuration.resolveDisplayAspect();
            int resolvedWidth = configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
            if (resolvedWidth != dimensions.logicalWidth()) {
                throw new IllegalStateException("capture presentation resolved width "
                        + resolvedWidth + " instead of " + dimensions.logicalWidth());
            }
            return new TraceCapturePresentationSetup(configuration, previousAspect, previousTestMode);
        } catch (Throwable failure) {
            try { restore(configuration, previousAspect, previousTestMode); }
            catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw rethrow(failure);
        }
    }

    /**
     * Keeps replay-owned camera construction at the native trace width while
     * the already-created GL presentation remains widescreen. The scope is
     * intentionally narrow: it is used only around headless boot, before the
     * gameplay camera is constructed.
     */
    AutoCloseable pinNativeTraceWidth() {
        Object previousAspect = configuration.getConfigValue(
                SonicConfiguration.DISPLAY_ASPECT);
        configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT,
                "NATIVE_4_3");
        configuration.resolveDisplayAspect();
        return new AutoCloseable() {
            private boolean closed;

            @Override
            public void close() {
                if (!closed) {
                    closed = true;
                    configuration.setConfigValue(
                            SonicConfiguration.DISPLAY_ASPECT, previousAspect);
                    configuration.resolveDisplayAspect();
                }
            }
        };
    }

    @Override
    public void close() {
        if (closed) return;
        restore(configuration, previousAspect, previousTestMode);
        closed = true;
    }

    private static void restore(SonicConfigurationService configuration,
            Object previousAspect, Object previousTestMode) {
        if (previousTestMode != null) {
            configuration.setConfigValue(SonicConfiguration.TEST_MODE_ENABLED, previousTestMode);
        }
        if (previousAspect != null) {
            configuration.setConfigValue(SonicConfiguration.DISPLAY_ASPECT, previousAspect);
        }
        configuration.resolveDisplayAspect();
    }

    private static RuntimeException rethrow(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
        return new IllegalStateException("capture presentation setup failed", failure);
    }
}
