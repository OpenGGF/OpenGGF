package com.openggf.game.launch;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.MasterTitleScreen;

import java.util.Objects;
import java.util.logging.Logger;

public class MasterTitleLaunchCoordinator {
    private static final Logger LOGGER = Logger.getLogger(MasterTitleLaunchCoordinator.class.getName());

    private final SonicConfigurationService configService;
    private final LaunchProfileStore profileStore;
    private final LaunchProfileApplier profileApplier;
    private Runnable pendingLaunchCallback;
    private java.util.Map<com.openggf.configuration.SonicConfiguration, Object> pendingSessionOverrides = java.util.Map.of();
    private Runnable afterStepLaunchCallback;
    private Runnable returnToMasterTitleHandler;
    private Runnable launchFailureHandler;

    public MasterTitleLaunchCoordinator(SonicConfigurationService configService) {
        this(configService, new LaunchProfileStore(configService), new LaunchProfileApplier(configService));
    }

    public MasterTitleLaunchCoordinator(SonicConfigurationService configService,
                                        LaunchProfileStore profileStore,
                                        LaunchProfileApplier profileApplier) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore");
        this.profileApplier = Objects.requireNonNull(profileApplier, "profileApplier");
    }

    public void prepareExit(String selectedGameId, boolean programmaticSelection) {
        configService.clearSessionOverrides();
        MasterTitleScreen.GameEntry entry = MasterTitleScreen.GameEntry.fromGameId(selectedGameId);
        if (!programmaticSelection) {
            profileApplier.apply(profileStore.load(entry), entry, profileStore);
        } else {
            pendingSessionOverrides.forEach(configService::setSessionOverride);
        }
        pendingSessionOverrides = java.util.Map.of();
        configService.resolveDisplayAspect();
    }

    public void prepareStandaloneExit() {
        resetOverrides();
    }

    public void returnToMasterTitle() {
        resetOverrides();
        if (returnToMasterTitleHandler != null) {
            returnToMasterTitleHandler.run();
        }
    }

    public void restoreAfterFailedExit(String selectedGameId, Runnable fadeFromBlack) {
        resetOverrides();
        if (launchFailureHandler != null) {
            launchFailureHandler.run();
        }
        clearCallbacks();
        fadeFromBlack.run();
        LOGGER.info("Master title screen exit failed; restored master title for game: " + selectedGameId);
    }

    public void setPendingLaunchCallback(Runnable pendingLaunchCallback) {
        this.pendingLaunchCallback = pendingLaunchCallback;
    }

    /** Session-only settings a programmatic launch applies after the exit clears overrides. */
    public void setPendingSessionOverrides(java.util.Map<com.openggf.configuration.SonicConfiguration, Object> overrides) {
        pendingSessionOverrides = java.util.Map.copyOf(overrides);
    }

    public void clearPendingLaunchCallback() {
        pendingSessionOverrides = java.util.Map.of();
        pendingLaunchCallback = null;
    }

    public void stagePendingLaunchCallback() {
        afterStepLaunchCallback = pendingLaunchCallback;
        pendingLaunchCallback = null;
    }

    public void runAfterStepLaunchCallbackIfPresent() {
        Runnable callback = afterStepLaunchCallback;
        afterStepLaunchCallback = null;
        if (callback != null) {
            callback.run();
        }
    }

    public void setReturnToMasterTitleHandler(Runnable returnToMasterTitleHandler) {
        this.returnToMasterTitleHandler = returnToMasterTitleHandler;
    }

    public void setLaunchFailureHandler(Runnable launchFailureHandler) {
        this.launchFailureHandler = launchFailureHandler;
    }

    private void resetOverrides() {
        configService.clearSessionOverrides();
        configService.resolveDisplayAspect();
    }

    private void clearCallbacks() {
        pendingSessionOverrides = java.util.Map.of();
        pendingLaunchCallback = null;
        afterStepLaunchCallback = null;
    }
}
