package com.openggf.configuration;

import java.util.function.LongSupplier;

/** UI-thread display preview. Never changes persisted preferences or gameplay overrides. */
public final class DisplaySettingsTrial {
    private static final long DURATION = 15_000_000_000L;
    private final SonicConfigurationService config;
    private final LongSupplier clock;
    private String candidate;
    private long deadline;

    DisplaySettingsTrial(SonicConfigurationService config) { this(config, System::nanoTime); }
    DisplaySettingsTrial(SonicConfigurationService config, LongSupplier clock) {
        this.config = config;
        this.clock = clock;
    }

    /** Internal host access; keeps preview controls off the creator configuration API. */
    public static DisplaySettingsTrial forConfig(SonicConfigurationService config) {
        return config.displaySettingsTrial();
    }

    public void begin(String mode) {
        if (!ConfigCatalog.meta(SonicConfiguration.DISPLAY_WINDOW_MODE).allowedValues().contains(mode))
            throw new IllegalArgumentException("Choose a listed window mode");
        candidate = mode;
        deadline = clock.getAsLong() + DURATION;
    }

    /** Called by the engine even when focus loss suspends menu updates. */
    public String windowMode() {
        return active() ? candidate : config.getString(SonicConfiguration.DISPLAY_WINDOW_MODE);
    }

    public boolean active() {
        if (candidate != null && clock.getAsLong() - deadline >= 0) cancel();
        return candidate != null;
    }

    public int secondsRemaining() {
        return active() ? (int) Math.max(1, (deadline - clock.getAsLong() + 999_999_999L) / 1_000_000_000L) : 0;
    }

    /** Share of the preview still to run, 1 at the start and 0 once it reverts; drives the countdown meter. */
    public float remainingFraction() {
        return active() ? Math.clamp((deadline - clock.getAsLong()) / (float) DURATION, 0f, 1f) : 0f;
    }

    public void cancel() { candidate = null; }
}
