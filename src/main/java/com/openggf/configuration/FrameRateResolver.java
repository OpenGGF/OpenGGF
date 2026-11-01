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
        return presentation(config, 0);
    }

    public static boolean automaticPresentation(SonicConfigurationService config) {
        return "AUTO".equalsIgnoreCase(config.getString(SonicConfiguration.FPS));
    }

    /** Auto uses the live presentation ceiling; display-free callers retain the region rate. */
    public static int presentation(SonicConfigurationService config, int presentationCeilingHz) {
        if (automaticPresentation(config)) {
            return presentationCeilingHz > 0 ? presentationCeilingHz : simulation(config);
        }
        return Math.clamp(config.getInt(SonicConfiguration.FPS), 1,
                SonicConfigurationService.MAX_PRESENTATION_FPS);
    }

    /** Live cadence preference; tick-indexed gameplay, recordings and traces never read it. */
    public static DisplayPacing pacing(SonicConfigurationService config) {
        return DisplayPacing.parse(config.getString(SonicConfiguration.DISPLAY_PACING));
    }
}
