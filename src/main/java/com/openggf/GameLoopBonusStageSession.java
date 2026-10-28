package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.*;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.io.IOException;
import java.util.logging.Logger;

/** The loop-lifetime host visit; provider runtime and persistent respawn capture retain their owners. */
final class GameLoopBonusStageSession {
    interface Host {
        LevelManager level();
        SpriteManager sprites();
        Camera camera();
        WaterSystem water();
        FadeManager fade();
        GameStateManager gameState();
        GameModule module();
        GameMode currentMode();
        GameplayRunPolicy runPolicy();
        GameplayModeContext publishedGameplayContext();
        GameplayModeContext resolvedGameplayContext();
        String mainCharacterCode();
        AbstractPlayableSprite mainPlayable();
        TitleCardProvider titleCard();
        GameMode enterBonusTitleCardAfterLevelLoadBoundary();
        GameMode changeMode(GameMode mode);
        void titleDestination(PostTitleCardDestination destination);
        void lockTitleControls(boolean locked);
        void notifyModeChanged(GameMode oldMode);
    }

    static final int STATUS_FIRE_SHIELD_BIT = 4;
    static final int STATUS_LIGHTNING_SHIELD_BIT = 5;
    static final int STATUS_BUBBLE_SHIELD_BIT = 6;
    static final int SAVED_SHIELD_MASK = (1 << STATUS_FIRE_SHIELD_BIT)
            | (1 << STATUS_LIGHTNING_SHIELD_BIT) | (1 << STATUS_BUBBLE_SHIELD_BIT);
    private static final Logger LOGGER = Logger.getLogger(GameLoopBonusStageSession.class.getName());
    private final Host host;
    private final AudioManager audioManager;
    private final BonusStageTransitionCoordinator bonusStageTransitionCoordinator = new BonusStageTransitionCoordinator();
    private boolean bonusStageTransitionPending;
    private BonusStageProvider activeBonusStageProvider;
    // Activation high-water survives the reload while Last_star_post_hit is zeroed.
    private int pendingBonusReturnStarPostMark = -1;
    // A one-shot obligation owed to this provider, independently of active provider publication.
    private BonusStageProvider deferredBonusProvider;
    private ShieldType pendingBonusStageShieldRestore;

    GameLoopBonusStageSession(Host host, AudioManager audioManager) {
        this.host = host;
        this.audioManager = audioManager;
    }

    BonusStageProvider provider() { return activeBonusStageProvider; }
    boolean isTransitionPending() { return bonusStageTransitionPending; }
    void retireForSessionReset() {
        if (activeBonusStageProvider != null) activeBonusStageProvider.onExit();
        if (deferredBonusProvider != null && deferredBonusProvider != activeBonusStageProvider) {
            deferredBonusProvider.onExit();
        }
        bonusStageTransitionPending = false;
        activeBonusStageProvider = null;
        deferredBonusProvider = null;
        pendingBonusReturnStarPostMark = -1;
        pendingBonusStageShieldRestore = null;
    }

    void handleBonusStageDebugKey(BonusStageType type) {
        if (host.currentMode() == GameMode.LEVEL) {
            enterBonusStage(type);
        } else if (host.currentMode() == GameMode.BONUS_STAGE && activeBonusStageProvider != null) {
            activeBonusStageProvider.requestExit();
        }
    }

    /**
     * Enters a bonus stage from level mode.
     * Captures current state, fades to black, loads the bonus zone.
     */
    void enterBonusStage(BonusStageType type) {
        if (host.currentMode() != GameMode.LEVEL || !host.runPolicy().bonusStageEntry()) {
            return;
        }

        BonusStageProvider provider = host.module().getBonusStageProvider();
        if (!provider.hasBonusStages()) {
            LOGGER.fine("Current game module has no bonus stages; ignoring entry request");
            return;
        }

        if (type == null || type == BonusStageType.NONE) {
            LOGGER.fine("Bonus stage entry ignored: NONE type");
            return;
        }

        if (host.fade().isActive()) {
            LOGGER.fine("Bonus stage entry ignored: fade already in progress");
            return;
        }

        var sprite = host.sprites().getSprite(host.mainCharacterCode());
        AbstractPlayableSprite playable = sprite instanceof AbstractPlayableSprite candidate
                ? candidate
                : null;
        var capture = bonusStageTransitionCoordinator.captureEntry(
                host.level(),
                host.camera(),
                host.water(),
                playable,
                host.module().getLevelEventProvider(),
                encodeSavedShieldStatus(playable));
        BonusStageState savedState = capture.savedState();
        pendingBonusReturnStarPostMark = capture.pendingStarPostActivationMark();

        // Fade out music
        audioManager.fadeOutMusic();

        bonusStageTransitionPending = true;
        host.fade().startFadeToBlack(() -> {
            doEnterBonusStage(provider, type, savedState);
        });

        LOGGER.info("Starting fade-to-black for Bonus Stage " + type);
    }

    /**
     * Actually enters the bonus stage after the fade-to-black completes.
     * Loads the bonus zone, then shows the "BONUS STAGE" title card.
     * Preparation runs under the title card; the provider assembly callback is
     * deferred to {@link #applyDeferredBonusStageSetup()} at title release.
     */
    void doEnterBonusStage(BonusStageProvider provider, BonusStageType type,
                           BonusStageState savedState) {
        bonusStageTransitionPending = false;
        pendingBonusStageShieldRestore = null;

        // Register provider on the active gameplay mode so objects can access it via GameServices.bonusStage().
        activeBonusStageProvider = provider;
        var gameplayMode = host.publishedGameplayContext();
        if (gameplayMode != null) {
            gameplayMode.setActiveBonusStageProvider(provider);
        }

        provider.onEnter(type, savedState);

        if (gameplayMode != null) {
            gameplayMode.registerBonusStageAdapter(provider);
        }

        // Load the bonus zone through the normal level loading path
        int zoneId = provider.getZoneId(type);
        int zone = (zoneId >> 8) & 0xFF;
        int act = zoneId & 0xFF;

        try {
            // Suppress auto-music: bonus music plays later in applyDeferredBonusStageSetup
            host.level().setSuppressNextMusicChange(true);
            host.level().loadZoneAndAct(zone, act);
            // Consume the default title card request — we'll show the bonus card instead
            host.level().consumeTitleCardRequest();
        } catch (IOException e) {
            com.openggf.level.DeferredLevelLoadException.rethrowCallbackAbort(e);
            LOGGER.severe("Failed to load bonus stage zone: " + e.getMessage());
            provider.onExit();
            activeBonusStageProvider = null;
            if (gameplayMode != null) {
                gameplayMode.setActiveBonusStageProvider(null);
                gameplayMode.deregisterBonusStageAdapter();
            }
            host.changeMode(GameMode.LEVEL);
            return;
        }
        GameMode oldMode = host.enterBonusTitleCardAfterLevelLoadBoundary();

        prepareBonusStageForTitleCard(type, savedState);

        // Defer bonus-stage-specific setup to exitTitleCard() when the title card completes
        deferredBonusProvider = provider;

        // Initialize the bonus title card
        TitleCardProvider tcp = host.titleCard();
        if (tcp != null) {
            tcp.initializeBonus();
        }

        // Play bonus stage music at title card start (ROM: Restore_LevelMusic during title card)
        int musicId = provider.getMusicId(type);
        if (musicId >= 0) {
            audioManager.playMusic(musicId);
        }

        // Enter TITLE_CARD mode — exitTitleCard will transition to BONUS_STAGE
        host.lockTitleControls(true);

        // Cancel the FadeManager overlay — the stage fades in through its own
        // Level/loc_64DC Animate_Palette fade. The FadeManager was in HOLD_BLACK from
        // the fade-to-black; clearing it lets the title card's own black rect and
        // text render without being covered by the fade overlay.
        host.fade().cancel();

        host.notifyModeChanged(oldMode);

        LOGGER.info("Entered bonus title card for " + type + " (zone 0x"
                + Integer.toHexString(zoneId) + ")");
    }

    /**
     * Forces ordinary bonus player sprites to VDP high priority, unless the
     * coordinator owns player priority through its presentation policy.
     * This only restores the tile-priority bit; it does not rewrite the player's
     * display bucket, which remains governed by the normal priority model.
     */
    void forcePlayerHighPriorityInBonusStage() {
        com.openggf.game.internal.BonusStageFrameLifecycle.restorePlayerPriority(activeBonusStageProvider, host.sprites());
    }

    void prepareBonusStageForTitleCard(BonusStageType type, BonusStageState savedState) {
        if (savedState != null && host.level().getLevelGamestate() != null) {
            host.level().getLevelGamestate().pauseTimer();
            host.level().getLevelGamestate().setRings(savedState.savedRingCount());
        }
        host.level().setBonusStageHudLayout(true);
        if (savedState != null) {
            applyBonusStageEntryShieldRestore(
                    host.mainPlayable(), savedState.savedStatusSecondary());
        }
        restorePlayableStateForBonusTitleCard();
        forcePlayerHighPriorityInBonusStage();
        refreshPlayableSpriteArtCaches();
        ensureBonusStageBootstrapObjectPresent(type);
    }

    void ensureBonusStageBootstrapObjectPresent(BonusStageType type) {
        BonusStageBootstrapInstaller.ensurePresent(
                activeBonusStageProvider, type, host.level().getObjectManager());
    }

    void restorePlayableStateForBonusTitleCard() {
        for (var sprite : host.sprites().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable) {
                playable.setHidden(false);
                playable.setObjectControlled(false);
            }
        }
    }

    void refreshPlayableSpriteArtCaches() {
        for (var sprite : host.sprites().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable
                    && playable.getSpriteRenderer() != null) {
                playable.getSpriteRenderer().invalidateDplcCache();
            }
        }
    }

    /**
     * Exits the current bonus stage. Fades to black, restores previous zone.
     */
    void exitBonusStage() {
        if (host.currentMode() != GameMode.BONUS_STAGE || activeBonusStageProvider == null) {
            return;
        }

        BonusStageProvider provider = activeBonusStageProvider;
        BonusStageState savedState = provider.getSavedState();

        audioManager.fadeOutMusic();

        bonusStageTransitionPending = true;
        if (shouldStartBonusStageExitFade(provider)) {
            // Restart_level_flag re-enters Level:, whose Pal_FadeToBlack holds the
            // frozen stage for 22 V-ints ($15 + dbf) and services the Nemesis queue
            // on each (sonic3k.asm:7928, 7536-7556, 5074-5083): the death-restart fade.
            GameLoopPlcLifecycle.startToBlack(host.resolvedGameplayContext(), host.fade(),
                    () -> doExitBonusStage(provider, savedState));
            LOGGER.info("Starting fade-to-black to exit Bonus Stage");
        } else {
            doExitBonusStage(provider, savedState);
            LOGGER.info("Exiting Bonus Stage using provider-completed fade-to-black");
        }
    }

    static boolean shouldStartBonusStageExitFade(BonusStageProvider provider) {
        return provider == null || !provider.hasCompletedExitFadeToBlack();
    }

    /**
     * Actually exits the bonus stage after the fade-to-black completes.
     */
    void doExitBonusStage(BonusStageProvider provider, BonusStageState savedState) {
        bonusStageTransitionPending = false;

        // ROM: on bonus-stage exit the live HUD Ring_count is copied straight into
        // Saved_ring_count (loc_61076: move.w (Ring_count).w,(Saved_ring_count).w,
        // sonic3k.asm:127814; the pachinko/slots exits do the same at 96683/99001),
        // and the returning level reload then restores Ring_count from
        // Saved_ring_count. So the count carried back is the interior's LIVE ring
        // total at exit, NOT the entry snapshot plus a bookkeeping reward. This
        // matters where a machine's per-item ring award differs between the HUD
        // Ring_count and the Saved_ring_count it also bumps: the gumball ring ball
        // adds +10 to the HUD but +20 to Saved_ring_count (loc_6114E, :127899), and
        // that transient +20 is discarded here by the Ring_count->Saved_ring_count
        // copy. Capture the live HUD ring total now, before onExit()/loadZoneAndAct
        // reset it, and restore it on return below (replacing the former
        // savedRingCount + rewards.rings() reconstruction, which double-counted the
        // gumball ball as +20 and returned 79 rings where the ROM returns 69).
        int interiorExitRingCount =
                bonusStageTransitionCoordinator.captureInteriorExitRingCount(host.level(), savedState);

        // Capture rewards BEFORE onExit() in case it resets counters.
        BonusStageProvider.BonusStageRewards rewards = provider.getRewards();

        provider.onExit();
        activeBonusStageProvider = null;
        host.level().setBonusStageHudLayout(false);

        var gameplayMode = host.publishedGameplayContext();
        if (gameplayMode != null) {
            gameplayMode.setActiveBonusStageProvider(null);
            gameplayMode.deregisterBonusStageAdapter();
        }

        if (savedState == null) {
            LOGGER.warning("No saved state for bonus stage exit — returning to zone 0,0");
            try {
                host.level().loadZoneAndAct(0, 0);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load fallback level", e);
            }
            host.changeMode(GameMode.LEVEL);
            host.fade().startFadeFromBlack(null);
            return;
        }

        // Restore previous zone
        int zone = (savedState.savedZoneAndAct() >> 8) & 0xFF;
        int act = savedState.savedZoneAndAct() & 0xFF;

        try {
            // Arm Level_FromSavedGame and suppress auto-music before preparing the
            // captured respawn table; each write keeps its live manager lookup.
            bonusStageTransitionCoordinator.prepareReturnLoad(host::level, savedState);
            // Publish the return cause immediately before the synchronous load.
            TraceSessionLauncher.markNextRunInteriorReturnLoad();
            host.level().loadZoneAndAct(zone, act);
            // Consume the auto-generated title card request — we initialize it ourselves below
            host.level().consumeTitleCardRequest();
        } catch (IOException e) {
            throw new RuntimeException("Failed to reload level after bonus stage", e);
        } finally {
            host.level().clearBonusStageReturn();
        }

        var sprite = host.sprites().getSprite(host.mainCharacterCode());
        AbstractPlayableSprite playable = sprite instanceof AbstractPlayableSprite candidate
                ? candidate
                : null;
        ShieldType shieldToRestore = playable != null
                ? resolveShieldToRestore(rewards, savedState.savedStatusSecondary())
                : null;
        if (playable != null) {
            pendingBonusStageShieldRestore = shieldToRestore;
        }
        bonusStageTransitionCoordinator.restoreReturnState(
                host.level(),
                host.camera(),
                host.water(),
                playable,
                host.module().getLevelEventProvider(),
                savedState,
                pendingBonusReturnStarPostMark,
                interiorExitRingCount,
                shieldToRestore,
                rewards,
                host.gameState()::addLife);
        pendingBonusReturnStarPostMark = -1;

        // Level/loc_62B6 spawns Obj_TitleCard on this reload for every zone but $1701
        // or Act3_flag (sonic3k.asm:7762-7767); the recorded gumball exit shows it.
        int apparentZone = (savedState.savedApparentZoneAndAct() >> 8) & 0xFF;
        int apparentAct = savedState.savedApparentZoneAndAct() & 0xFF;
        TitleCardProvider tcp = host.titleCard();
        if (tcp != null) {
            tcp.initialize(apparentZone, apparentAct);
        }

        // Enter TITLE_CARD mode — exitTitleCard will transition to LEVEL
        host.titleDestination(PostTitleCardDestination.LEVEL);
        GameMode oldMode = host.changeMode(GameMode.TITLE_CARD);
        host.lockTitleControls(true);

        // Play zone music (ROM: Restore_LevelMusic during title card wait)
        host.level().playCurrentLevelMusic();

        // The reload re-enters Level: the zone card appears over black at full colour.
        GameLoopPlcLifecycle.revealLevelRoutineEntry(host.resolvedGameplayContext(), host.fade(), tcp);

        host.notifyModeChanged(oldMode);
        LOGGER.info("Exiting bonus stage, entering zone title card for zone " + zone + " act " + act);
    }

    static int encodeSavedShieldStatus(AbstractPlayableSprite playable) {
        if (playable == null || !playable.hasShield()) {
            return 0;
        }
        ShieldType shieldType = playable.getShieldType();
        if (shieldType == null) {
            return 0;
        }
        return switch (shieldType) {
            case FIRE -> 1 << STATUS_FIRE_SHIELD_BIT;
            case LIGHTNING -> 1 << STATUS_LIGHTNING_SHIELD_BIT;
            case BUBBLE -> 1 << STATUS_BUBBLE_SHIELD_BIT;
            default -> 0;
        };
    }

    static ShieldType resolveShieldToRestore(BonusStageProvider.BonusStageRewards rewards, int savedStatusSecondary) {
        if (rewards.fireShield()) {
            return ShieldType.FIRE;
        }
        if (rewards.bubbleShield()) {
            return ShieldType.BUBBLE;
        }
        if (rewards.lightningShield()) {
            return ShieldType.LIGHTNING;
        }
        if (rewards.shield()) {
            return ShieldType.BASIC;
        }

        return savedShieldType(savedStatusSecondary);
    }

    /**
     * ROM {@code SpawnLevelMainSprites_SpawnPowerup} {@code loc_6A02}
     * (docs/skdisasm/sonic3k.asm:8326-8355) tests the saved elemental bits in
     * fire -> lightning -> bubble order and re-gives that shield.
     */
    static ShieldType savedShieldType(int savedStatusSecondary) {
        int savedShieldBits = savedStatusSecondary & SAVED_SHIELD_MASK;
        if ((savedShieldBits & (1 << STATUS_FIRE_SHIELD_BIT)) != 0) {
            return ShieldType.FIRE;
        }
        if ((savedShieldBits & (1 << STATUS_LIGHTNING_SHIELD_BIT)) != 0) {
            return ShieldType.LIGHTNING;
        }
        if ((savedShieldBits & (1 << STATUS_BUBBLE_SHIELD_BIT)) != 0) {
            return ShieldType.BUBBLE;
        }
        return null;
    }

    /**
     * ROM {@code SpawnLevelMainSprites_SpawnPowerup}
     * (docs/skdisasm/sonic3k.asm:8296-8322) runs on every level spawn, and the
     * bonus zones are explicitly routed into its restore arm --
     * {@code cmpi.b #$13,(Current_zone).w / beq loc_69E0} and the same for
     * {@code #$14} (:8302-8305). It re-gives Player 1 the shield saved in
     * {@code Saved_status_secondary}, so the ROM's player keeps its elemental
     * shield for the DURATION of the bonus stage, not only after returning to
     * the level.
     *
     * <p>The engine captured the value at entry
     * ({@link #encodeSavedShieldStatus}) but consumed it only on the way out
     * ({@link #resolveShieldToRestore}), leaving the bonus-stage player
     * shieldless. In Pachinko that removes {@code Test_Ring_Collisions}'
     * {@code Status_LtngShield} arm (:18486-18489), which allocates
     * {@code Obj_Attracted_Ring} and pulls a nearby ring into the player.
     */
    static void applyBonusStageEntryShieldRestore(
            AbstractPlayableSprite playable, int savedStatusSecondary) {
        if (playable == null) {
            return;
        }
        ShieldType shieldType = savedShieldType(savedStatusSecondary);
        if (shieldType != null) {
            playable.giveShield(shieldType);
        }
    }

    void applyPendingBonusStageShieldRestore(AbstractPlayableSprite playable) {
        if (pendingBonusStageShieldRestore == null || playable == null) {
            return;
        }
        ShieldType shieldType = pendingBonusStageShieldRestore;
        pendingBonusStageShieldRestore = null;
        playable.giveShield(shieldType);
    }

    void setPendingShield(ShieldType shieldType) {
        pendingBonusStageShieldRestore = shieldType;
    }

    boolean hasPendingShield() {
        return pendingBonusStageShieldRestore != null;
    }

    /**
     * Applies bonus stage setup that was deferred until the title card completed.
     * Mirrors the setup previously done inline in doEnterBonusStage.
     */
    void applyDeferredBonusStageSetup() {
        BonusStageProvider provider = deferredBonusProvider;

        // Clear deferred state
        deferredBonusProvider = null;

        if (provider == null) {
            LOGGER.warning("No deferred bonus stage provider — skipping setup");
            return;
        }

        provider.onDeferredSetupComplete();

        // Music already started in doEnterBonusStage (at title card init).
        // Background fade is handled by the title card's own per-channel rect fade
        // during DISPLAY (synchronized with the 22-frame hold, matching ROM).
    }

}
