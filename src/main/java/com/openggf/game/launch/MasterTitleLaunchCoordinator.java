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
    private final java.util.function.Supplier<MasterTitleScreen> hub;
    private Runnable pendingLaunchCallback;
    private java.util.Map<com.openggf.configuration.SonicConfiguration, Object> pendingSessionOverrides = java.util.Map.of();
    private Runnable afterStepLaunchCallback;
    private Runnable returnToMasterTitleHandler;
    private Runnable launchFailureHandler;

    public MasterTitleLaunchCoordinator(SonicConfigurationService configService) {
        this(configService, new LaunchProfileStore(configService), new LaunchProfileApplier(configService));
    }

    /**
     * Applies Start profiles through the live hub's store, so a patch-backed main the hub offers
     * (Knuckles in Sonic 2) is not sanitised away; without a hub the default store applies.
     */
    public MasterTitleLaunchCoordinator(SonicConfigurationService configService,
                                        java.util.function.Supplier<MasterTitleScreen> hub) {
        this(configService, new LaunchProfileStore(configService), new LaunchProfileApplier(configService), hub);
    }

    public MasterTitleLaunchCoordinator(SonicConfigurationService configService,
                                        LaunchProfileStore profileStore,
                                        LaunchProfileApplier profileApplier) {
        this(configService, profileStore, profileApplier, () -> null);
    }

    private MasterTitleLaunchCoordinator(SonicConfigurationService configService,
                                         LaunchProfileStore profileStore,
                                         LaunchProfileApplier profileApplier,
                                         java.util.function.Supplier<MasterTitleScreen> hub) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore");
        this.profileApplier = Objects.requireNonNull(profileApplier, "profileApplier");
        this.hub = Objects.requireNonNull(hub, "hub");
    }

    public void prepareExit(String selectedGameId, boolean programmaticSelection) {
        configService.clearSessionOverrides();
        MasterTitleScreen.GameEntry entry = MasterTitleScreen.GameEntry.fromGameId(selectedGameId);
        if (!programmaticSelection) {
            MasterTitleScreen screen = hub.get();
            LaunchProfileStore hubStore = screen != null ? screen.launchProfileStore() : null;
            LaunchProfileStore store = hubStore != null ? hubStore : profileStore;
            profileApplier.apply(store.load(entry), entry, store);
        } else {
            pendingSessionOverrides.forEach(configService::setSessionOverride);
        }
        pendingSessionOverrides = java.util.Map.of();
        configService.resolveDisplayAspect();
    }

    public void prepareStandaloneExit() {
        resetOverrides();
    }

    /**
     * Hosted-media launches apply their own saved profile through the same team/donor boundary.
     * The no-argument overload retains the legacy stock-team preparation for external callers.
     */
    public void prepareHostedExit(String gameId) {
        prepareExit(gameId, false);
    }

    public void prepareHostedExit() {
        configService.clearSessionOverrides();
        configService.setSessionOverride(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        configService.setSessionOverride(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        pendingSessionOverrides = java.util.Map.of();
        configService.resolveDisplayAspect();
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
