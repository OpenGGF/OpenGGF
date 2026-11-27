package com.openggf.game;

import com.openggf.configuration.ConfigurationPersistence;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;

import java.util.Objects;

/**
 * The setup guide's state for one engine run. The engine owns one instance for the whole process, so the
 * guide opens on its own at most once per run however often the hub is rebuilt (returning from a game,
 * reloading mods, changing language), and progress left in the hub survives until the guide finishes.
 * Hubs built without a session (tools and tests) keep the earlier behaviour and never open the guide.
 */
public final class SetupGuideSession {
    /** Where the guide was left: its step, the style chosen so far (null before one), and the focused row. */
    public record Progress(int step, SetupGuidePreset style, int focus) { }

    private final boolean existingInstall;
    private boolean autoOpened;
    private Progress progress;

    public SetupGuideSession(boolean existingInstall) { this.existingInstall = existingInstall; }

    /** A session for this run: an existing install had a configuration file before the engine started. */
    public static SetupGuideSession forConfiguration(SonicConfigurationService config) {
        return new SetupGuideSession(ConfigurationPersistence.loadedExistingFile(config));
    }

    /** Whether this run started from an earlier install's configuration (an upgrade), not a fresh one. */
    public boolean existingInstall() { return existingInstall; }

    /**
     * Claims this run's single automatic opening. True only the first time, while the guide has neither
     * been finished nor skipped, and never in test mode.
     */
    public boolean claimAutomaticOpen(SonicConfigurationService config) {
        Objects.requireNonNull(config, "config");
        if (autoOpened || SetupGuidePreset.settled(config) || config.getBoolean(SonicConfiguration.TEST_MODE_ENABLED))
            return false;
        autoOpened = true;
        return true;
    }

    /** Whether the guide has already opened on its own in this run. */
    public boolean automaticOpenUsed() { return autoOpened; }

    /** Progress kept when the guide is left for the hub without finishing; null when none. */
    public Progress progress() { return progress; }

    public void keep(Progress progress) { this.progress = progress; }

    public void clearProgress() { progress = null; }
}
