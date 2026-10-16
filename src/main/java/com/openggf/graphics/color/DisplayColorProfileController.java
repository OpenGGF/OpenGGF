package com.openggf.graphics.color;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.graphics.GraphicsManager;

import java.util.Objects;
import java.util.function.Consumer;

public final class DisplayColorProfileController {
    public static final int NOTIFICATION_FRAMES = 120;

    private final int toggleKey;
    private final Consumer<DisplayColorProfile> persistProfile;
    private final Consumer<DisplayColorProfile> applyProfile;
    private final Runnable refreshPalettes;
    private DisplayColorProfile currentProfile;
    private SonicConfigurationService savedConfig;
    private GraphicsManager graphics;
    // Last raw saved strings consumed by synchronizeSavedSettings(); an unchanged
    // configuration value is skipped without parsing, keeping the per-frame path
    // allocation-free.
    private String syncedProfileValue;
    private String syncedExpansionValue;
    private String notificationText;
    private int notificationFramesRemaining;

    public DisplayColorProfileController(DisplayColorProfile initialProfile,
                                         int toggleKey,
                                         Consumer<DisplayColorProfile> persistProfile,
                                         Runnable refreshPalettes) {
        this(initialProfile, toggleKey, persistProfile, profile -> {
        }, refreshPalettes);
    }

    public DisplayColorProfileController(DisplayColorProfile initialProfile,
                                         int toggleKey,
                                         Consumer<DisplayColorProfile> persistProfile,
                                         Consumer<DisplayColorProfile> applyProfile,
                                         Runnable refreshPalettes) {
        this.currentProfile = initialProfile == null ? DisplayColorProfile.RAW_RGB : initialProfile;
        this.toggleKey = toggleKey;
        this.persistProfile = Objects.requireNonNull(persistProfile, "persistProfile");
        this.applyProfile = Objects.requireNonNull(applyProfile, "applyProfile");
        this.refreshPalettes = Objects.requireNonNull(refreshPalettes, "refreshPalettes");
    }

    public static DisplayColorProfileController fromConfig(SonicConfigurationService configService,
                                                           GraphicsManager graphicsManager,
                                                           Runnable refreshPalettes) {
        Objects.requireNonNull(configService, "configService");
        Objects.requireNonNull(graphicsManager, "graphicsManager");
        Objects.requireNonNull(refreshPalettes, "refreshPalettes");

        DisplayColorProfile profile = applySavedSettings(configService, graphicsManager);

        DisplayColorProfileController controller = new DisplayColorProfileController(
                profile,
                configService.getInt(SonicConfiguration.DISPLAY_COLOR_PROFILE_TOGGLE_KEY),
                nextProfile -> {
                    configService.setConfigValue(SonicConfiguration.DISPLAY_COLOR_PROFILE, nextProfile.name());
                    configService.saveConfig();
                },
                graphicsManager::setDisplayColorProfile,
                refreshPalettes);
        controller.savedConfig = configService;
        controller.graphics = graphicsManager;
        controller.syncedProfileValue = configService.getString(SonicConfiguration.DISPLAY_COLOR_PROFILE);
        controller.syncedExpansionValue = configService.getString(SonicConfiguration.DISPLAY_COLOR_EXPANSION);
        return controller;
    }

    /**
     * Applies the saved display profile and expansion to a renderer. The windowed
     * engine and every headless/capture boot share this owner, so a saved Limited
     * setting reaches captured frames exactly as it reaches the window.
     */
    public static DisplayColorProfile applySavedSettings(SonicConfigurationService configService,
                                                         GraphicsManager graphicsManager) {
        Objects.requireNonNull(configService, "configService");
        Objects.requireNonNull(graphicsManager, "graphicsManager");
        DisplayColorProfile profile = DisplayColorProfile.parse(
                configService.getString(SonicConfiguration.DISPLAY_COLOR_PROFILE));
        graphicsManager.setDisplayColorProfile(profile);
        graphicsManager.setDisplayColorExpansion(DisplayColorExpansion.parse(
                configService.getString(SonicConfiguration.DISPLAY_COLOR_EXPANSION)));
        return profile;
    }

    /** Apply saved settings independently of shortcut ownership, including while the settings menu is open. */
    public void synchronizeSavedSettings() {
        if (savedConfig == null) return;
        String profileValue = savedConfig.getString(SonicConfiguration.DISPLAY_COLOR_PROFILE);
        if (!profileValue.equals(syncedProfileValue)) {
            syncedProfileValue = profileValue;
            DisplayColorProfile profile = DisplayColorProfile.parse(profileValue);
            if (profile != currentProfile) {
                currentProfile = profile;
                applyProfile.accept(profile);
                refreshPalettes.run();
            }
        }
        String expansionValue = savedConfig.getString(SonicConfiguration.DISPLAY_COLOR_EXPANSION);
        if (!expansionValue.equals(syncedExpansionValue)) {
            syncedExpansionValue = expansionValue;
            graphics.setDisplayColorExpansion(DisplayColorExpansion.parse(expansionValue));
        }
    }

    public void update(InputHandler inputHandler) {
        if (inputHandler != null && toggleKey >= 0 && inputHandler.isKeyPressed(toggleKey)) {
            currentProfile = currentProfile.next();
            persistProfile.accept(currentProfile);
            applyProfile.accept(currentProfile);
            refreshPalettes.run();
            notificationText = "Color: " + currentProfile.label();
            notificationFramesRemaining = NOTIFICATION_FRAMES;
            return;
        }

        if (notificationFramesRemaining > 0) {
            notificationFramesRemaining--;
            if (notificationFramesRemaining == 0) {
                notificationText = null;
            }
        }
    }

    public DisplayColorProfile currentProfile() {
        return currentProfile;
    }

    public String notificationText() {
        return notificationFramesRemaining > 0 ? notificationText : null;
    }
}
