package com.openggf.configuration;

public final class FrameRateResolver {
    private FrameRateResolver() {
    }

    public static int effective(SonicConfigurationService config) {
        return simulation(config);
    }

    /** Region-owned outer service cadence, including audio and paused host service. */
    public static int simulation(SonicConfigurationService config) {
        return "PAL".equalsIgnoreCase(config.getString(SonicConfiguration.REGION)) ? 50 : 60;
    }

    /** Window presentation limit; neither ROM admission nor audio consumes this rate. */
    public static int presentation(SonicConfigurationService config) {
        return Math.clamp(config.getInt(SonicConfiguration.FPS), 1,
                SonicConfigurationService.MAX_PRESENTATION_FPS);
    }
}
