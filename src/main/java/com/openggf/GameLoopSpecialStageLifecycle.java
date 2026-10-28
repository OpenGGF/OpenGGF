package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.PlaybackDebugManager;
import com.openggf.game.*;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.save.SaveReason;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.GameplayRunPolicy;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.logging.Logger;

/** Loop-lifetime authority for special entry, retained identity, results and return completion. */
final class GameLoopSpecialStageLifecycle {
    private static final Logger LOGGER = Logger.getLogger(GameLoopSpecialStageLifecycle.class.getName());
    private final Host host;
    private final AudioManager audioManager;
    private SpecialStageProvider activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;
    private GameLoop.SpecialStageObservationPacing specialStageObservationPacing;
    private ResultsScreen resultsScreen;
    private int ssRingsCollected;
    private boolean ssEmeraldCollected;
    private int ssStageIndex;
    private EmeraldRewardKind activeSpecialStageRewardKind = EmeraldRewardKind.CHAOS_EMERALD;
    private int resultsFrameCounter = 0;
    private boolean returningFromSpecialStage = false;
    private boolean specialStageTransitionPending = false;
    private final SpecialStageEntryPresentationController specialStageEntryPresentation =
            new SpecialStageEntryPresentationController();
    private boolean resultsExitFadeCompleted;
    private boolean resultsExitToWhite = true;
    private boolean resultsExitReady;
    private int resultsExitPreLevelFadeFramesRemaining = -1;

    GameLoopSpecialStageLifecycle(Host host, AudioManager audioManager) {
        this.host = host;
        this.audioManager = audioManager;
    }

    interface Host {
        LevelManager level(); SpriteManager sprites(); Camera camera(); FadeManager fade();
        GameStateManager gameState(); GameModule module(); GameMode currentMode(); GameplayRunPolicy runPolicy();
        SonicConfigurationService config(); InputHandler input(); PlaybackDebugManager playback();
        GameplayModeContext boundGameplayContext(); GameplayModeContext resolvedGameplayContext();
        PlcLifecycleFrame plcFrame(); LiveRewindManager rewind(); GameLoopSpecialStageInput pacingInput();
        SpecialStageProvider activeProvider(); String mainCharacterCode(); TitleCardProvider titleCard();
        GameMode changeMode(GameMode mode); boolean debugKeyPressed(int key); boolean rewindable();
        Runnable debugCompletionCallback(); Runnable debugFailureCallback(); void deregisterSpecialStageAdapter();
        void doEnterSpecialStage(SpecialStageProvider provider, int stage, boolean black);
        void doEnterResultsScreen(); void lockTitleControls(boolean locked);
        void restoreBigRingReturn(AbstractPlayableSprite playable); void notifyModeChanged(GameMode oldMode);
        void requestSessionSave(SaveReason reason);
    }

    void setObservationPacing(GameLoop.SpecialStageObservationPacing pacing) { specialStageObservationPacing = pacing; }
    ResultsScreen resultsScreen() { return resultsScreen; }
    EmeraldRewardKind rewardKind() { return activeSpecialStageRewardKind; }
    boolean transitionPending() { return specialStageTransitionPending; }
    boolean returningFromSpecialStage() { return returningFromSpecialStage; }
    void clearEntryPresentation() { specialStageEntryPresentation.clear(); }
    void retireForSessionReset() {
        activeSpecialStageProvider.reset();
        activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;
        specialStageObservationPacing = null;
        resultsScreen = null;
        ssRingsCollected = 0;
        ssEmeraldCollected = false;
        ssStageIndex = 0;
        activeSpecialStageRewardKind = EmeraldRewardKind.CHAOS_EMERALD;
        resultsFrameCounter = 0;
        returningFromSpecialStage = false;
        specialStageTransitionPending = false;
        specialStageEntryPresentation.clear();
        resultsExitFadeCompleted = false;
        resultsExitToWhite = true;
        resultsExitReady = false;
        resultsExitPreLevelFadeFramesRemaining = -1;
    }
    boolean retainedProviderRewindable() { return activeSpecialStageProvider != null && activeSpecialStageProvider.supportsRewind(); }
    SpecialStageProvider provider() {
        if (host.currentMode() == GameMode.SPECIAL_STAGE || host.currentMode() == GameMode.SPECIAL_STAGE_RESULTS) {
            return activeSpecialStageProvider != null ? activeSpecialStageProvider : NoOpSpecialStageProvider.INSTANCE;
        }
        return getCurrentModuleSpecialStageProvider();
    }
    void completeTitleReturn() {
        host.changeMode(GameMode.LEVEL);
        returningFromSpecialStage = false;
        LOGGER.info("Exited Title Card, returned to level from special stage at checkpoint");
    }

    /**
     * SPECIAL_STAGE per-frame update: debug shortcuts, sprite-debug navigation,
     * the special-stage input/update tick, and the completion → results-screen
     * handoff. Falls through (no early return) to the shared post-update tail,
     * matching the original dispatcher.
     */
    void updateSpecialStageMode() {
        SpecialStageProvider ssProvider = host.activeProvider();
        updateCapturedFrame(ssProvider, host.config(),
                host::debugKeyPressed,
                host.debugCompletionCallback(), host.debugFailureCallback(),
                host.input(), host.boundGameplayContext(), host.plcFrame(),
                specialStageObservationPacing, this::updateSpecialStageInput,
                specialStageEntryPresentation, host.fade(),
                () -> revealSpecialStage(ssProvider), host::rewindable,
                host.rewind(), host.currentMode(), this::enterResultsScreen);
    }

    void updateSpecialStageResultsMode() {
        if (resultsScreen instanceof com.openggf.game.internal.ResultsResourcePreparation preparation
                && preparation.isPreparingResults()) {
            // The resource loop has VBlank and queue tails, but no results sprite yet.
            LevelFrameStep.executeHardwareTimedObjectScan(
                    LevelFrameContext.from(host.boundGameplayContext()), host.plcFrame(),
                    PlcLifecyclePhase.SPECIAL_STAGE_RESULTS, () -> { });
            preparation.finishResultsPreparationIteration();
            return;
        }
        // ROM SS_NormalExit is a full loop iteration: VintID_TitleCards, a
        // V-int that runs ProcessPLC_9Tiles (docs/s1disasm/sonic.asm:946),
        // ExecuteObjects, BuildSprites, then RunPLC at the tail
        // (docs/s1disasm/sonic.asm:3402-3413). Claiming the lifecycle frame
        // alone never reached the hardware timing boundaries, so the loop-tail
        // arm was never submitted as hardware work.
        LevelFrameStep.executeHardwareTimedObjectScan(
                LevelFrameContext.from(host.boundGameplayContext()), host.plcFrame(),
                PlcLifecyclePhase.SPECIAL_STAGE_RESULTS,
                this::runSpecialStageResultsIteration);
    }

    /** One ROM SS_NormalExit iteration (docs/s1disasm/sonic.asm:3402-3413). */
    void runSpecialStageResultsIteration() {
        if (resultsExitReady) {
            // ROM: the GM loop only falls through to the next mode's dispatch
            // on the frame after the whiteout's final WaitForVBla, so the
            // returning level's load (and its ClearPLC/AddPLC submissions)
            // belongs to the first frame past the results screen's last
            // sampled row, never to that row itself.
            if (resultsExitPreLevelFadeFramesRemaining < 0) {
                // Level begins before its counted fade: Sonic 2's loc_3EC4
                // PlaySound(F9) precedes ClearPLC/Pal_FadeToBlack.
                host.level().beginLevelEntry();
                resultsExitPreLevelFadeFramesRemaining = host.module()
                        .getLevelInitProfile().preLevelFadeOutFrames();
            }
            if (resultsExitPreLevelFadeFramesRemaining > 0) {
                // The next mode's own entry fades out the previous screen
                // before it queues the returning level's PLCs (S1: GM_Level's
                // ClearPLC + PaletteFadeOut, sonic.asm:2710-2716). Each fade
                // frame is a WaitForVBlank row whose RunPLC only ever sees the
                // cleared queue, so hold the exit body — and with it the
                // reload's PLC submissions — for the fade's duration.
                resultsExitPreLevelFadeFramesRemaining--;
                return;
            }
            resultsExitPreLevelFadeFramesRemaining = -1;
            resultsExitReady = false;
            doExitResultsScreen();
            return;
        }
        if (resultsExitFadeCompleted) {
            // The exit fade's completion fired during this iteration's fade
            // update; hold the (already white) screen for the rest of the
            // frame — the ROM CPU is still inside PaletteWhiteOut's final
            // wait — and run the exit body on the next iteration.
            resultsExitFadeCompleted = false;
            resultsExitReady = true;
        } else {
            // Update results screen
            resultsFrameCounter++;
            if (resultsScreen != null) {
                resultsScreen.update(resultsFrameCounter, null);
                if (resultsScreen.isComplete()) {
                    exitResultsScreen();
                }
            }
        }
    }

    /**
     * Enters results screen with a specific ring count (for debug).
     * Uses fade-to-white transition like the normal path.
     */
    void enterResultsScreenWithDebugRings(boolean emeraldCollected, int ringsCollected) {
        if (host.currentMode() != GameMode.SPECIAL_STAGE) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = host.fade();
        if (fadeManager.isActive()) {
            return;
        }

        // Store special stage results for the results screen
        ssRingsCollected = ringsCollected;
        ssEmeraldCollected = emeraldCollected;
        ssStageIndex = host.activeProvider().getCurrentStage();

        // Publish only for providers that retain legacy GameLoop ownership.
        if (emeraldCollected) {
            SpecialStageTransitionSupport.publishRewardIfLoopOwned(
                    host.activeProvider(), host.gameState(),
                    ssStageIndex, activeSpecialStageRewardKind);
        }

        // Start fade-to-white, then show results when complete
        GameLoopPlcLifecycle.startToWhite(host.resolvedGameplayContext(), fadeManager, host::doEnterResultsScreen);

        LOGGER.info("DEBUG: Starting fade-to-white to exit Special Stage");
    }

    void enterSpecialStage(SpecialStageEntryRequest request) {
        if (host.currentMode() != GameMode.LEVEL) {
            return;
        }

        if (!host.runPolicy().specialStageEntry()) {
            return;
        }

        FadeManager fadeManager = host.fade();
        boolean screenAlreadyFaded = false;
        boolean fadeFromBlack = false;
        boolean transitionSfxAlreadyPlayed = false;

        if (fadeManager.isActive()) {
            FadeManager.FadeState fadeState = fadeManager.getState();
            if (fadeState == FadeManager.FadeState.HOLD_WHITE) {
                // Screen is held white (S1 big ring -> results -> fade to white path).
                // Take over and enter special stage directly with fade-from-white.
                screenAlreadyFaded = true;
                fadeFromBlack = false;
                transitionSfxAlreadyPlayed = true;
            } else if (fadeState == FadeManager.FadeState.HOLD_BLACK) {
                // Screen is held black. Enter special stage with fade-from-black.
                screenAlreadyFaded = true;
                fadeFromBlack = true;
            } else {
                // Different fade in progress, can't start
                return;
            }
        }

        SpecialStageProvider ssProvider = getCurrentModuleSpecialStageProvider();
        if (!ssProvider.hasSpecialStages()) {
            LOGGER.fine("Current game module has no special stages; ignoring entry request");
            return;
        }

        // Clear power-ups before entering special stage
        String mainCode = host.mainCharacterCode();
        var sprite = host.sprites().getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite playable) {
            playable.clearPowerUps();
        }

        if (shouldPlaySpecialStageEntrySfx(transitionSfxAlreadyPlayed)) {
            playSpecialStageTransitionSfx(ssProvider);
        }

        if (ssProvider.fadesMusicOnEntry()) {
            // S2 SpecialStage queues MusID_FadeOut between its entry SFX and
            // Pal_FadeToWhite (s2.asm:6542-6546). The provider owns whether
            // this shared entry path submits that command.
            audioManager.fadeOutMusic();
        }

        // Determine which stage to enter
        final int stageIndex = SpecialStageTransitionSupport.resolveStageIndex(
                request, ssProvider, host.gameState());
        activeSpecialStageRewardKind = request.rewardKind();

        // The provider owns the entry white-out; preserve the level's last
        // frame until its reveal boundary rather than delaying the mode change.
        if (screenAlreadyFaded) {
            fadeManager.cancel();
        }
        host.doEnterSpecialStage(ssProvider, stageIndex, fadeFromBlack);
        LOGGER.info("Entered Special Stage " + (stageIndex + 1)
                + " revealing from " + (fadeFromBlack ? "black" : "white"));
    }

    /**
     * Ordinary startup leaves entry-load presentation with the provider. A
     * BK2 session supplies recorded row admission instead, so it selects
     * TRACE_ACCURATE and the provider must not add another load hold. This
     * ownership choice does not supply any gameplay state.
     */
    SpecialStageStartupPolicy defaultSpecialStageStartupPolicy() {
        return LevelIterationAdmissionController.specialStageStartupPolicy(
                host.playback().isSessionPlaying());
    }

    void doEnterSpecialStage(SpecialStageProvider ssProvider, int stageIndex,
                                     boolean fadeFromBlack,
                                     SpecialStageStartupPolicy startupPolicy,
                                     EmeraldRewardKind rewardKind) {
        // Clear the transition freeze flag (now we're in special stage mode)
        specialStageTransitionPending = false;
        activeSpecialStageRewardKind = rewardKind;

        try {
            ssProvider.reset();
            if (activeSpecialStageRewardKind == EmeraldRewardKind.CHAOS_EMERALD) {
                // Preserve the established S1/S2 and ordinary S3K startup seam.
                ssProvider.initializeStage(stageIndex, startupPolicy);
            } else {
                ssProvider.initializeStage(stageIndex, startupPolicy, activeSpecialStageRewardKind);
            }
            activeSpecialStageProvider = ssProvider;
            GameplayModeContext context = host.resolvedGameplayContext();
            if (context != null) {
                context.registerSpecialStageAdapter(ssProvider);
            }

            // The level camera survives the ROM's entry fade-to-white, which
            // still shows the level's last frame; revealSpecialStage origins it.
            specialStageEntryPresentation.begin(ssProvider, fadeFromBlack, host.fade(),
                    () -> revealSpecialStage(ssProvider),
                    host.boundGameplayContext().plcFrameLifecycle());

            GameMode oldMode = host.changeMode(GameMode.SPECIAL_STAGE);

            // Notify listener of mode change
            host.notifyModeChanged(oldMode);

            LOGGER.info("Entered Special Stage " + (stageIndex + 1) + " (H32 mode: 256x224)");
        } catch (IOException e) {
            specialStageEntryPresentation.clear();
            host.deregisterSpecialStageAdapter();
            activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;
            throw new RuntimeException("Failed to initialize Special Stage " + (stageIndex + 1), e);
        } catch (RuntimeException e) {
            specialStageEntryPresentation.clear();
            host.deregisterSpecialStageAdapter();
            activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;
            throw e;
        }
    }

    /**
     * Enters the results screen after special stage completion/failure.
     * Performs fade-to-white transition before showing results.
     *
     * @param emeraldCollected true if an emerald was collected
     */
    void enterResultsScreen(boolean emeraldCollected) {
        if (host.currentMode() != GameMode.SPECIAL_STAGE) {
            return;
        }

        // Check if the SS manager pre-started a fade (S1: concurrent fade during exit spin)
        FadeManager fadeManager = host.fade();
        boolean fadeAlreadyWhite = (fadeManager.getState() == FadeManager.FadeState.HOLD_WHITE);

        // The pre-started S1 fade carries no completion, so the screen is simply held
        // white and this call owns the transition. The fade-to-white started below also
        // parks in HOLD_WHITE for one frame before FadeManager runs its completion, and
        // a finished stage re-raises this transition every frame -- so that window must
        // keep waiting. Taking it over would enter the results screen twice and open a
        // second native blocking fade while the pending completion still owns the first.
        if (fadeManager.isActive() && (!fadeAlreadyWhite || fadeManager.hasPendingCompletion())) {
            return; // Different fade in progress, or our own completion still pending
        }

        SpecialStageProvider ssProvider = host.activeProvider();

        // Store special stage results for the results screen
        ssRingsCollected = ssProvider.getRingsCollected();
        ssEmeraldCollected = emeraldCollected;
        ssStageIndex = ssProvider.getCurrentStage();

        // Mark emerald as collected now (so it shows in results screen)
        if (emeraldCollected) {
            SpecialStageTransitionSupport.publishRewardIfLoopOwned(
                    ssProvider, host.gameState(), ssStageIndex,
                    activeSpecialStageRewardKind);
        }

        if (fadeAlreadyWhite || (ssProvider instanceof com.openggf.game.internal.SpecialStageResultsEntry entry
                && entry.hasCompletedResultsEntryFade())) {
            // Fade pre-started by SS manager (S1) - screen is already white.
            // Go directly to results; doEnterResultsScreen() calls startFadeFromWhite().
            host.doEnterResultsScreen();
        } else {
            // Normal path (S2): start fade-to-white, then callback to results.
            // This boundary is raised from updateSpecialStageMode's tail, after
            // specialStageEntryPresentation.update has already spent this
            // iteration's FadeManager tick, so the ROM's first
            // Pal_FadeToWhite WaitForVint is already the NEXT iteration's --
            // deferring again would give the routine 23 V-ints instead of the
            // 22 its dbf loop runs (docs/s2disasm/s2.asm:3571-3582) and push
            // the results loop's first VintID_Level (s2.asm:6797-6803, 781,
            // 1770) one row late.
            //
            // Note that V-int does NOT retire the stage's last player DPLC
            // pair, as an earlier version of this comment claimed. The results
            // setup block zeroes the queue head first -- unguarded, so the
            // shipped ROM runs it -- at s2.asm:6759-6760, and ProcessDMAQueue
            // stops on a zero first word (s2.asm:1772-1790). The ROM DISCARDS
            // that pair; it never transfers. Vint_Fade (s2.asm:1068-1070) does
            // not call ProcessDMAQueue either, so nothing drains between the
            // mode change and the clear.
            GameLoopPlcLifecycle.startToWhiteAfterFrameFadeTick(
                    host.resolvedGameplayContext(), fadeManager, () -> {
                        host.doEnterResultsScreen();
                    });
        }

        LOGGER.info("Starting fade-to-white to exit Special Stage");
    }

    /**
     * The special-stage identity a recorded run segment is cut on: the value
     * {@code Current_Special_Stage} held when the ROM entered
     * {@code GameModeID_SpecialStage}, held constant for the whole recorded
     * segment.
     *
     * <p>The ROM's byte is not constant across that segment. A won stage
     * increments it inside the stage itself, in the same block that raises
     * {@code SS_Check_Rings_flag} ({@code addi_.b #1,(Current_Special_Stage).w},
     * docs/s2disasm/s2.asm:72467-72477), and it is zeroed only on a later entry
     * once it reaches 7 (s2.asm:6538-6540). The run recorder samples it once, at
     * the first {@code GameModeID_SpecialStage} frame
     * ({@code S2RunCaptureRunner.StartSsSegment}), so a segment's recorded
     * {@code special_stage_index} is that pre-increment number for the whole
     * segment, results tail included.
     *
     * <p>The engine splits that one ROM mode into {@code SPECIAL_STAGE} plus
     * {@code SPECIAL_STAGE_RESULTS}, and entering results deinitialises the
     * stage manager ({@code SpecialStageProvider#resetForResults}), dropping its
     * scratch stage index back to 0. Reading the live provider during the
     * results tail therefore reports 0 for every stage -- which only happened to
     * match the recorded identity for stage 0. The results phase belongs to the
     * stage captured at the exit boundary, so report that instead.
     */
    Integer recordedSpecialStageIdentity(GameMode observedMode) {
        if (observedMode == GameMode.SPECIAL_STAGE_RESULTS) {
            return ssStageIndex;
        }
        if (observedMode != GameMode.SPECIAL_STAGE) {
            return null;
        }
        SpecialStageProvider provider = host.activeProvider();
        return provider != null ? provider.getCurrentStage() : null;
    }

    /**
     * Actually enters the results screen after fade-to-white completes.
     */
    void doEnterResultsScreen() {
        // Reset special stage provider
        SpecialStageProvider ssProvider = host.activeProvider();
        ssProvider.resetForResults();

        // Transition to results mode
        GameMode oldMode = host.changeMode(GameMode.SPECIAL_STAGE_RESULTS);
        resultsFrameCounter = 0;

        // Create results screen with current emerald count via provider
        int totalEmeralds = host.gameState().getEmeraldCount();
        // Native results creation stays bound to this executing provider and world.
        resultsScreen = com.openggf.game.sonic3k.specialstage.S3kSpecialStageWorldAccess.createResults(
                ssProvider, host.boundGameplayContext(), ssRingsCollected, ssEmeraldCollected, ssStageIndex, totalEmeralds);

        if (resultsScreen == null) {
            resultsScreen = NoOpResultsScreen.INSTANCE;
        }

        playSpecialStageResultsMusic(ssProvider);

        // Notify listener of mode change
        host.notifyModeChanged(oldMode);

        // Neither game fades back in here. After the stage's whiteout both ROMs
        // rebuild the screen and then write the results palette straight to the
        // active palette in one go -- S2 `moveq #PalID_Result,d0 / bsr.w
        // PalLoad_Now` (docs/s2disasm/s2.asm:6762-6763) and S1
        // `moveq #palid_SSResult,d0 / bsr.w PalLoad` (docs/s1disasm/sonic.asm:3382-3383)
        // -- so the results screen appears at full intensity on the very first
        // V-blank of its tally loop (S2 `VintID_Level` at s2.asm:6797-6800, S1
        // `SS_NormalExit` at sonic.asm:3403-3406). A fade-from-white here would
        // instead hold PALETTE_FADE for its whole duration and push the first
        // results-owned V-blank that many rows late. (That V-blank's
        // ProcessDMAQueue retires nothing: s2.asm:6759-6760 zeroes the queue
        // head first, unguarded, so the ROM discards the stage's last player
        // DPLC pair rather than transferring it.)
        host.fade().clearOverlayForImmediatePaletteLoad();

        LOGGER.info("Entered Special Stage Results Screen (rings=" + ssRingsCollected +
                ", emerald=" + ssEmeraldCollected + ")");
    }

    /**
     * Exits the results screen and shows the title card before returning to the
     * level.
     * Performs fade-to-black transition before showing title card.
     */
    void exitResultsScreen() {
        if (host.currentMode() != GameMode.SPECIAL_STAGE_RESULTS) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = host.fade();
        if (fadeManager.isActive()) {
            return;
        }

        // Fade out, then show title card when complete. The completion only latches the exit:
        // the fade update that completes it runs at the start of the results screen's last
        // faded frame, and the exit body (the returning level's load) must not run until the
        // iteration after that frame's V-int sample.
        resultsExitToWhite = host.activeProvider().resultsExitFadesToWhite();
        if (resultsExitToWhite) {
            playSpecialStageTransitionSfx(host.activeProvider());
            GameLoopPlcLifecycle.startToWhite(host.resolvedGameplayContext(), fadeManager,
                    () -> resultsExitFadeCompleted = true);
        } else {
            GameLoopPlcLifecycle.startToBlack(host.resolvedGameplayContext(), fadeManager,
                    () -> resultsExitFadeCompleted = true);
        }
    }

    /**
     * Actually exits the results screen after fade-to-black completes.
     */
    void doExitResultsScreen() {
        // Clean up results screen
        resultsScreen = null;
        host.deregisterSpecialStageAdapter();
        activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;

        // S3K's special-stage clear sets bit 7 in Last_star_post_hit before
        // the return reload, enabling Load_Starpost_Settings' Saved2 branch.
        host.level().setLastStarPostHit();

        if (host.level().getCurrentLevel() == null) {
            // No level was loaded (special stage launched from level select).
            // Load the starting level; loadCurrentLevel() will request its own title card.
            GameMode oldMode = host.changeMode(GameMode.LEVEL);
            try {
                host.level().loadZoneAndAct(0, 0);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load starting level after special stage", e);
            }

            host.notifyModeChanged(oldMode);

            startResultsReturnFadeIn();

            LOGGER.info("Exited Results Screen, loaded starting level (no previous level)");
            return;
        }

        // ROM HPZ results rebuild the sanctuary as the results-screen backdrop only.
        // The pedestal that launched the stage (loc_90926) sets
        // Special_bonus_entry_flag = 1, so Load_Starpost_Settings takes loc_2D2C2 and
        // restores Saved2_* — the player resumes in the zone the Big Ring was collected
        // in, not in the sanctuary. The sanctuary is re-entered only through another
        // Big Ring.
        //
        // Otherwise (and for every other game) the ROM runs the full Level: function,
        // which clears all object RAM (bridges/stateful objects reset to initial state)
        // and reloads level data:
        // - S1: zone/act may have been advanced before SS entry
        // - S2: same zone/act, objects reset, rings cleared (Obj79_LoadData clr.w Ring_count)
        // - S3K: same zone/act, objects reset, rings restored from Saved2_ring_count
        boolean sanctuaryReturn;
        // The star-post activation mark is the engine's model of the ROM's
        // "last checkpoint reached" byte, and that byte is NOT part of what the
        // return's level reload clears.
        //
        // S3K: Load_Starpost_Settings' giant-ring/bonus branch loc_2D2C2
        // (docs/skdisasm/sonic3k.asm:61833-61859) restores the whole Saved2_*
        // block but deliberately writes no Last_star_post_hit, so the value the
        // special-stage exit left there survives -- the exit's
        // "ori.b #$80,(Last_star_post_hit).w" (:12157, :12712) over the subtype
        // sub_2D164 stored when the post was touched (:61744), with LevelSizeLoad's
        // "andi.b #$7F,(Last_star_post_hit).w" (:7913) stripping the marker bit
        // before LevelLoop. sub_2D028 then reads it as already-hit for every post
        // whose subtype is at or below it ("cmp.b d2,d1 / bhs.w loc_2D0EA",
        // :61646-61650), which is what stops the post the player entered from
        // re-arming its 20-ring bonus stars (:61678-61681) on the return.
        //
        // S1 is the same shape: v_lastlamp is cleared only by the end-of-act card
        // (docs/s1disasm/_incObj/3A Got Through Card.asm:198), a death
        // (_incObj/01 Sonic.asm:1116) and a new game (sonic.asm:1968) -- never by a
        // level reload -- and Lamp_Blue gates on the identical "last >= subtype"
        // comparison (_incObj/79 Lamppost.asm:57-62).
        //
        // The bonus-stage return already carries this across its own reload
        // (BonusStageTransitionCoordinator.restoreReturnState); the
        // special-stage return is the second implementation of the same contract
        // and was missing it.
        int returnStarPostActivationMark = -1;
        RespawnState checkpointBeforeReturnReload = host.level().getCheckpointState();
        if (checkpointBeforeReturnReload != null) {
            returnStarPostActivationMark =
                    checkpointBeforeReturnReload.getStarPostActivationMark();
        }
        // The reload's title card is presented below by this method, so the
        // load must request it (keeping the queued initial PLCs live for the
        // card's locked loop) rather than model an omitted presentation.
        host.level().setResultsReturnCardOwnedByCaller(true);
        try {
            sanctuaryReturn = SpecialStageTransitionSupport.loadSpecialStageReturnLevel(
                    host.level(), activeSpecialStageRewardKind, ssStageIndex);
        } finally {
            host.level().setResultsReturnCardOwnedByCaller(false);
        }
        if (returnStarPostActivationMark >= 0) {
            RespawnState checkpointAfterReturnReload = host.level().getCheckpointState();
            if (checkpointAfterReturnReload != null) {
                checkpointAfterReturnReload.restoreStarPostActivationMark(
                        returnStarPostActivationMark);
            }
        }

        // Consume any pending title card request to prevent double title card
        // (we're manually entering the title card below)
        host.level().consumeTitleCardRequest();

        if (sanctuaryReturn) {
            // ROM GameMode_SpecialStageResults installs the HPZ hub directly;
            // there is no intervening HPZ title card. The controller consumes
            // the typed return context and runs success choreography or exposes
            // the failed pedestal immediately.
            returningFromSpecialStage = false;
            GameMode oldMode = host.changeMode(GameMode.LEVEL);
            host.notifyModeChanged(oldMode);
            startResultsReturnFadeIn();
            host.requestSessionSave(SaveReason.SPECIAL_STAGE_SAVE);
            LOGGER.info("Exited Results Screen directly into the HPZ sanctuary hub"
                    + " (stage=" + ssStageIndex + ", success=" + ssEmeraldCollected + ")");
            return;
        }

        // Set flag so exitTitleCard knows to restore checkpoint state
        returningFromSpecialStage = true;

        // Enter title card mode for the APPARENT zone/act. Obj_TitleCardInit
        // never reads Current_zone_and_act: the act-number art is chosen by
        // "tst.b (Apparent_act).w / bne" -- Num2 when non-zero, Num1 when zero
        // (sonic3k.asm:62171-62181) -- and the zone art by
        // "move.b (Apparent_zone_and_act).w,d0" (sonic3k.asm:62195). The two
        // acts diverge across an S3K seamless act transition, because
        // Current_act advances inside the act-1 background-event dispatch while
        // Apparent_act is only raised later, by the end-sign results object's
        // "move.b #1,(Apparent_act).w" at loc_2DD06 (sonic3k.asm:62754). A
        // giant-ring special stage entered in that window returns to a card the
        // ROM still draws with the act-1 digit. The bonus-stage return already
        // reads Saved_apparent_zone_and_act for the same reason.
        int zoneIndex = host.level().getCurrentZone();
        int actIndex = host.level().getApparentAct();
        enterTitleCardFromResults(zoneIndex, actIndex);

        // Reveal the title card from the exit fade's colour. Without this, the overlay
        // persists indefinitely because completeFade() sees no new fade was started.
        startResultsReturnFadeIn();
        host.requestSessionSave(SaveReason.SPECIAL_STAGE_SAVE);

        LOGGER.info("Exited Results Screen, entering Title Card for zone " + zoneIndex + " act " + actIndex);
    }

    void startResultsReturnFadeIn() {
        if (host.titleCard() instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication boundary
                && boundary.hasImmediateFreshLevelPalette()) {
            host.fade().clearOverlayForImmediatePaletteLoad();
            return;
        }
        if (resultsExitToWhite) {
            GameLoopPlcLifecycle.startFromWhite(host.resolvedGameplayContext(), host.fade(), null);
        } else {
            GameLoopPlcLifecycle.startFromBlack(host.resolvedGameplayContext(), host.fade(), null);
        }
    }

    /**
     * Enters the title card from the results screen context.
     * Similar to enterTitleCard but allows entry from SPECIAL_STAGE_RESULTS mode.
     * Restores the player to their checkpoint position before showing title card.
     */
    void enterTitleCardFromResults(int zoneIndex, int actIndex) {
        GameMode oldMode = host.changeMode(GameMode.TITLE_CARD);

        // Restore player to checkpoint state BEFORE title card starts
        // This prevents the player from falling/dying during the title card animation
        String mainCode = host.mainCharacterCode();
        var sprite = host.sprites().getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite playable) {
            RespawnState checkpointState = host.level().getCheckpointState();

            if (host.level().hasBigRingReturn()
                    && (host.level().isSanctuaryOriginRestorePending(zoneIndex, actIndex)
                        || host.level().sanctuaryReentryStage().isEmpty())) {
                // S3K big ring path: restore all Saved2_* state
                host.restoreBigRingReturn(playable);
            } else if (checkpointState != null && checkpointState.isActive()) {
                // S2 checkpoint star path: restore to checkpoint (ROM: Saved_* variables)
                checkpointState.restoreToPlayer(playable, host.camera());
            } else {
                // No checkpoint - camera will follow player at level start position
                host.camera().updatePosition(true);
            }

            // Freeze all movement during title card
            playable.setXSpeed((short) 0);
            playable.setYSpeed((short) 0);
            playable.setGSpeed((short) 0);
            playable.setAir(false);
            // Clear death/hurt state to prevent dying during title card
            playable.setDead(false);
            playable.setHurt(false);
            playable.setDeathCountdown(0);
            playable.setInvulnerableFrames(0);
            playable.setRolling(false);
            // Restore visibility and controls (cleared by S3K big ring entry sequence)
            playable.setHidden(false);
            playable.setObjectControlled(false);
            // Unfreeze camera (frozen by S3K big ring entry sequence)
            host.camera().setFrozen(false);

            // ROM SpawnLevelMainSprites_SpawnPlayers, both arms
            // (docs/skdisasm/sonic3k.asm:8399 sidekick, :8420 Tails-as-Player_1).
            SpecialStageReturnSpawn.applyMainCharacterSpawnOffset(playable, host.config());
            SpecialStageReturnSpawn.respawnSidekicks(
                    playable, host.sprites().getSidekicks(), host.level());
        }
        InLevelTitleCardCoordinator.prepareResultsTransition(
                sprite, host::lockTitleControls, host::module, host.sprites(), host.level());

        // Initialize the title card manager
        if (host.titleCard() != null) {
            host.titleCard().initializeFreshLevelTransition(zoneIndex, actIndex);
        }

        // Start zone music immediately when title card begins (not at the end)
        host.level().playCurrentLevelMusic();

        // Notify listener of mode change
        host.notifyModeChanged(oldMode);
    }

    private SpecialStageProvider getCurrentModuleSpecialStageProvider() {
        var module = host.module();
        if (module == null) {
            return NoOpSpecialStageProvider.INSTANCE;
        }
        return module.getSpecialStageProvider();
    }

    static boolean shouldPlaySpecialStageEntrySfx(boolean transitionSfxAlreadyPlayed) {
        return !transitionSfxAlreadyPlayed;
    }

    private void playSpecialStageTransitionSfx(SpecialStageProvider ssProvider) {
        int sfxId = ssProvider.getTransitionSfxId();
        if (sfxId >= 0) {
            audioManager.playSfx(sfxId);
        }
    }

    private void revealSpecialStage(SpecialStageProvider ssProvider) {
        host.camera().setX((short) 0);
        host.camera().setY((short) 0);
        // Special-stage entry clears gameplay power-ups. Clear both audio
        // speed mechanisms at the same boundary, immediately before the new
        // song is constructed, so it cannot inherit the outgoing level's
        // speed-shoes tempo. S3K's stage-local acceleration starts from here.
        audioManager.setSpeedShoes(false);
        audioManager.setSpeedMultiplier(1);
        int musicId = ssProvider.getStageMusicId();
        if (!audioManager.playMusic(ssProvider.getStageMusic()) && musicId >= 0) {
            audioManager.playMusic(musicId);
        }
    }

    private void playSpecialStageResultsMusic(SpecialStageProvider ssProvider) {
        int musicId = ssProvider.getResultsMusicId();
        if (!audioManager.playMusic(ssProvider.getResultsMusic()) && musicId >= 0) {
            audioManager.playMusic(musicId);
        }
    }

    void updateSpecialStageInput() {
        SpecialStageProvider ssProvider = host.activeProvider();
        if (GameLoopSpecialStageInput.handleDebugInput(ssProvider, host.config(),
                host::debugKeyPressed)) {
            return;
        }

        host.pacingInput().apply(ssProvider, host.input(), host.resolvedGameplayContext().getWorldSession());
    }

    private static void updateCapturedFrame(SpecialStageProvider provider,
                       SonicConfigurationService config,
                       IntPredicate debugKeyPressed,
                       Runnable completeWithEmerald,
                       Runnable failStage,
                       InputHandler input,
                       GameplayModeContext gameplayMode,
                       PlcLifecycleFrame plcFrame,
                       GameLoop.SpecialStageObservationPacing pacing,
                       Runnable updateInput,
                       SpecialStageEntryPresentationController presentation,
                       FadeManager fadeManager,
                       Runnable playStageMusic,
                       BooleanSupplier rewindable,
                       LiveRewindManager rewind,
                       GameMode currentMode,
                       Consumer<Boolean> enterResults) {
        SpecialStageDebugCapabilities capabilities =
                SpecialStageDebugCapabilities.orNone(provider.debugCapabilities());
        if (capabilities.stageSelection()
                && isUnmodifiedDebugKeyPressed(debugKeyPressed, org.lwjgl.glfw.GLFW.GLFW_KEY_X)) {
            provider.debugNextStage();
        }
        if (capabilities.layoutSelection()
                && isUnmodifiedDebugKeyPressed(debugKeyPressed, org.lwjgl.glfw.GLFW.GLFW_KEY_Z)) {
            provider.debugToggleLayoutSet();
        }
        if (isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY))) {
            completeWithEmerald.run();
        }
        if (isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_FAIL_KEY))) {
            failStage.run();
        }
        if (capabilities.spriteViewer() && isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_SPRITE_DEBUG_KEY))) {
            provider.toggleSpriteDebugMode();
        }
        if (capabilities.planeVisibility() && isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_PLANE_DEBUG_KEY))) {
            provider.cyclePlaneDebugMode();
        }
        updateDebugNavigation(provider, capabilities, config, debugKeyPressed);

        TraceSessionLauncher session = TraceSessionLauncher.active();
        if (session != null) session.applySpecialStageTraceInputIfActive(input);
        boolean skipTick = session != null && session.shouldSkipCurrentSpecialStageTick();
        if (!skipTick) {
            GameLoop.SpecialStageObservationPacing installed = pacing;
            if (installed == null && session != null) {
                // Production replay owns the same recorded RunObjects pass stream
                // that tests install explicitly through their package bridge.
                installed = session.currentSpecialStagePassPacing().orElse(null);
            }
            if (installed != null) {
                provider.setLagCompensation(0);
            }
            GameLoop.SpecialStageObservationPacing resolvedPacing = installed;
            LevelFrameStep.executeHardwareTimedObjectScan(
                    LevelFrameContext.from(gameplayMode), plcFrame,
                    PlcLifecyclePhase.orElse(provider.specialStagePlcLifecyclePhase(),
                            PlcLifecyclePhase.SPECIAL_STAGE),
                    () -> com.openggf.game.internal.NativeSpecialStageFrame.step(provider,
                            gameplayMode.getWorldSession(), () -> updateProvider(provider, resolvedPacing, updateInput)));
        } else if (session.skippedSpecialStagePlcPhase().isPresent()) {
            LevelFrameStep.serviceVBlankOnly(LevelFrameContext.from(gameplayMode), plcFrame,
                    session.skippedSpecialStagePlcPhase().orElseThrow());
        }
        presentation.update(provider, fadeManager, playStageMusic, gameplayMode.plcFrameLifecycle());
        if (session != null) session.advanceSpecialStageTraceCursorIfActive(input);
        if (rewindable.getAsBoolean() && session == null) {
            rewind.recordExternalFrame(gameplayMode, currentMode, provider, false, input, false);
        }
        if (provider.isFinished() && (session == null || !session.isSpecialStageSession())) {
            if (TraceSessionLauncher.shouldDeferRunModeBoundaryCommit()) {
                return;
            }
            enterResults.accept(provider.isEmeraldCollected());
        }
    }

    private static void updateDebugNavigation(SpecialStageProvider provider,
                                              SpecialStageDebugCapabilities capabilities,
                                              SonicConfigurationService config,
                                              IntPredicate pressed) {
        if (!capabilities.spriteViewer() || !provider.isSpriteDebugMode()) return;
        SpecialStageDebugProvider debug = provider.getDebugProvider();
        if (debug == null) return;
        if (isUnmodifiedDebugKeyPressed(pressed, config.getInt(SonicConfiguration.RIGHT))) debug.nextPage();
        if (isUnmodifiedDebugKeyPressed(pressed, config.getInt(SonicConfiguration.LEFT))) debug.previousPage();
        if (isUnmodifiedDebugKeyPressed(pressed, config.getInt(SonicConfiguration.DOWN))) debug.nextSet();
        if (isUnmodifiedDebugKeyPressed(pressed, config.getInt(SonicConfiguration.UP))) debug.previousSet();
    }

    /** Keeps the modifier contract explicit after the special-stage loop was extracted. */
    private static boolean isUnmodifiedDebugKeyPressed(IntPredicate predicate, int keyCode) {
        return predicate.test(keyCode);
    }

    private static void updateProvider(SpecialStageProvider provider,
                                       GameLoop.SpecialStageObservationPacing pacing,
                                       Runnable updateInput) {
        if (pacing == null) {
            updateInput.run();
            provider.update();
            return;
        }
        for (int pass = 0; pass < pacing.passCount(); pass++) {
            pacing.runPass(pass, provider);
            pacing.afterPass(pass);
        }
    }
}
