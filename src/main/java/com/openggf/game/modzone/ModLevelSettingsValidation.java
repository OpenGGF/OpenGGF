package com.openggf.game.modzone;

import com.openggf.game.GameModule;
import com.openggf.game.session.GameplayTeamBootstrap;

import java.util.Objects;

/** Internal authoring validation; portable settings cannot depend on process-wide donor configuration. */
public final class ModLevelSettingsValidation {
    private ModLevelSettingsValidation() { }

    public static void validate(GameModule module, ModLevelSettings settings) {
        validateCapabilities(module, settings);
        if (settings.team() != null)
            GameplayTeamBootstrap.requireExactTeam(module.getPlayableCharacterRegistry(), settings.team());
    }

    /** Registration may check exact keys later, after its own character contributions are resolved. */
    public static void validateCapabilities(GameModule module, ModLevelSettings settings) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(settings, "settings");
        // Unused environment settings retain the host's existing validation path.
        if (settings.background() != null || settings.waterHeight() != null)
            module.getModLevelSettingsProvider().validate(settings);
        if (settings.team() != null && !settings.team().sidekicks().isEmpty() && !module.supportsSidekick())
            throw new IllegalArgumentException("This host does not support required sidekicks in authored level settings");
    }
}
