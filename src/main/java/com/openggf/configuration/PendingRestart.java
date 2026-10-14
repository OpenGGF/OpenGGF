package com.openggf.configuration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Restart-only settings ({@link SettingTiming#RESTART}) that the player saved in Settings since
 * OpenGGF started, so menus can say a restart is needed until it happens. Only Settings saves
 * count: the engine itself writes some of these keys in memory (the launched game's ROM, test
 * mode leaving the trace picker), which a restart would not change. Saving a value back to what
 * OpenGGF started with clears it again.
 */
public final class PendingRestart {
    private static volatile PendingRestart session;

    private final Map<SonicConfiguration, Object> started = new EnumMap<>(SonicConfiguration.class);
    private final Set<SonicConfiguration> saved = EnumSet.noneOf(SonicConfiguration.class);

    private PendingRestart(SonicConfigurationService config) {
        for (SonicConfiguration key : ConfigCatalog.emitOrder()) {
            if (SettingTiming.of(key) == SettingTiming.RESTART) started.put(key, config.getConfigValue(key));
        }
    }

    /** Records the restart-only values OpenGGF is running with; called once at engine start. */
    public static void captureAtStart(SonicConfigurationService config) {
        session = new PendingRestart(Objects.requireNonNull(config, "config"));
    }

    /** Restart-only settings saved since start that differ from what OpenGGF is running with. */
    public static List<SonicConfiguration> current(SonicConfigurationService config) {
        PendingRestart tracker = session;
        return tracker == null ? List.of() : tracker.changed(config);
    }

    static void noteSaved(Set<SonicConfiguration> keys) {
        PendingRestart tracker = session;
        if (tracker == null) return;
        synchronized (tracker) {
            for (SonicConfiguration key : keys) {
                if (tracker.started.containsKey(key)) tracker.saved.add(key);
            }
        }
    }

    private synchronized List<SonicConfiguration> changed(SonicConfigurationService config) {
        List<SonicConfiguration> changed = new ArrayList<>();
        for (SonicConfiguration key : saved) {
            if (!Objects.equals(String.valueOf(started.get(key)), String.valueOf(config.getConfigValue(key)))) {
                changed.add(key);
            }
        }
        return changed;
    }

    /** Test seam: forget the running session. */
    static void clearForTest() {
        session = null;
    }
}
