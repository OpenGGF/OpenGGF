package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.sprites.managers.SpriteManager;

import java.io.IOException;

/** Owns top-level dispatch and frame-boundary handling for seamless transitions. */
final class LevelSeamlessTransitionExecutor {
    private final LevelManager levelManager;
    private final LevelTransitionCoordinator transitions;

    LevelSeamlessTransitionExecutor(
            LevelManager levelManager,
            LevelTransitionCoordinator transitions) {
        this.levelManager = levelManager;
        this.transitions = transitions;
    }

    void execute(SeamlessLevelTransitionRequest request) {
        if (request == null) {
            return;
        }

        try {
            transitions.setSpecialStageReturnLevelReloadRequested(false);
            switch (request.type()) {
                case MUTATE_ONLY -> levelManager.applySeamlessMutation(
                        request.mutationKey());
                case RELOAD_SAME_LEVEL -> {
                    levelManager.executeActTransition(normalizeReloadSameRequest(
                            request,
                            levelManager.currentZone,
                            levelManager.currentAct));
                    advanceFrameCounterAcrossReload();
                }
                case RELOAD_TARGET_LEVEL -> {
                    levelManager.executeActTransition(request);
                    advanceFrameCounterAcrossReload();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply seamless transition", e);
        } finally {
            transitions.setLevelInactiveForTransition(false);
        }
    }

    static SeamlessLevelTransitionRequest normalizeReloadSameRequest(
            SeamlessLevelTransitionRequest request,
            int currentZone,
            int currentAct) {
        return SeamlessLevelTransitionRequest
                .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(currentZone, currentAct)
                .deactivateLevelNow(request.deactivateLevelNow())
                .preserveMusic(request.preserveMusic())
                .preserveLevelGamestate(request.preserveLevelGamestate())
                .preserveEndOfLevelState(request.preserveEndOfLevelFlag())
                .preserveEndOfLevelActive(request.preserveEndOfLevelActive())
                .showInLevelTitleCard(request.showInLevelTitleCard())
                .runtimeArtAdmissionPolicy(request.runtimeArtAdmissionPolicy())
                .resetLevelGamestateAtInLevelTitleCardDisplay(
                        request.resetLevelGamestateAtInLevelTitleCardDisplay())
                .inLevelTitleCardResetAdditionalDispatches(
                        request.inLevelTitleCardResetAdditionalDispatches())
                .inLevelTitleCardResetPhaseOneDispatchOverlap(
                        request.inLevelTitleCardResetPhaseOneDispatchOverlap())
                .lockPlayerControlForInLevelTitleCard(
                        request.lockPlayerControlForInLevelTitleCard())
                .inLevelTitleCardExitAdditionalDispatches(
                        request.inLevelTitleCardExitAdditionalDispatches())
                .inLevelTitleCardExitPhaseOneDispatchOverlap(
                        request.inLevelTitleCardExitPhaseOneDispatchOverlap())
                .forceAirOnStaleObjectSupportLoss(
                        request.forceAirOnStaleObjectSupportLoss())
                .preserveOffsetCameraPosition(
                        request.preserveOffsetCameraPosition())
                .postTransitionMinXIfPresent(request.postTransitionMinX())
                .postTransitionMaxXIfPresent(request.postTransitionMaxX())
                .postTransitionMinYIfPresent(request.postTransitionMinY())
                .postTransitionMaxYIfPresent(request.postTransitionMaxY())
                .postTransitionMaxYTargetIfPresent(
                        request.postTransitionMaxYTarget())
                .playerOffset(request.playerOffsetX(), request.playerOffsetY())
                .cameraOffset(request.cameraOffsetX(), request.cameraOffsetY())
                .mutationKey(request.mutationKey())
                .musicOverrideId(request.musicOverrideId())
                .resourceHandoff(request.resourceHandoffId())
                .build();
    }

    /**
     * ROM {@code Level_frame_counter} (incremented in VInt_0_Main every gameplay
     * frame) keeps ticking through the act-reload frame; the engine's
     * {@code GameLoop} and headless test runner both {@code return} after
     * applying a seamless reload transition, skipping
     * {@code SpriteManager.update()} (which is where
     * {@code SpriteManager.frameCounter} normally increments). Bump it
     * explicitly here so sidekick AI gates that read
     * {@code (Level_frame_counter & MASK)} — e.g. sonic3k.asm:26775 loc_13E9C
     * 64-frame jump-cadence check — fire on the same frames as the ROM after
     * AIZ act 1 → act 2 reload.
     *
     * <p>S3K sidekick CPU now resolves this gate through the stored
     * {@code LevelManager.frameCounter}, so keep that counter aligned with the
     * sprite-manager gameplay counter here.
     *
     * <p>Only applies to RELOAD transitions: MUTATE_ONLY runs in places
     * (e.g. AIZ1 fire-transition art overlay) that may execute mid-frame
     * without skipping the rest of the gameplay loop.
     */
    void advanceFrameCounterAcrossReload() {
        // The reload is requested by ScreenEvents, but the ROM returns to the
        // remainder of LevelLoop afterward: OscillateNumDo still runs before
        // the next VBlank (docs/skdisasm/sonic3k.asm:7884-7910,
        // 104722-104774). The engine applies the pending reload at the next
        // frame top and returns from RecordingFrameDriver/GameLoop, so preserve
        // that native post-ScreenEvents oscillator tick explicitly.
        levelManager.advanceGlobalOscillation();

        // The pending seamless reload is consumed at frame top, so this row
        // returns before ObjectManager.update() can perform its normal V-int
        // clock increment. V_int_run_count is global work RAM and still ticks
        // in the ROM on that transition-only VBlank.
        if (levelManager.objectManager != null) {
            // V-blank-only row: see the exactly-one-tick-per-serviced-V-blank invariant on ObjectManager.vblaCounter.
            levelManager.objectManager.advanceVblaCounter();
        }

        // ROM keeps Level_frame_counter ticking through AIZ's reload frame
        // (docs/skdisasm/sonic3k.asm:7884-7894); S3K Tails CPU reads it for
        // loc_13E9C's 64-frame auto-jump gate (docs/skdisasm/sonic3k.asm:26775-26782).
        levelManager.frameCounter++;
        levelManager.markSidekickRomVisibleReloadFrameCounterBridge();
        SpriteManager spriteManager = GameServices.spritesOrNull();
        if (spriteManager != null) {
            spriteManager.setFrameCounter(spriteManager.getFrameCounter() + 1);
        }
    }
}
