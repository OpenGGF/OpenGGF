package com.openggf;

import com.openggf.game.EndingProvider;
import com.openggf.game.GameModule;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.internal.FreshLevelTitleBoundaryPublication;
import com.openggf.game.internal.LevelEntryPresentation;
import com.openggf.game.internal.LevelPaletteFadeOwner;
import com.openggf.game.internal.WorldTitleCardUpdate;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Bridges GameLoop-owned modes and fades to the session PLC lifecycle. */
final class GameLoopPlcLifecycle {
    private GameLoopPlcLifecycle() {
    }

    /** Palette-fade rows publish VBlank work before the fade and iteration body. */
    static void runLogicalIteration(PlcFrameLifecycleCoordinator lifecycle,
                                    GameplayModeContext context, Runnable updateFade,
                                    Function<PlcLifecycleFrame, Void> body) {
        lifecycle.runLogicalIteration(
                frame -> {
                    if (frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE)) {
                        LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(context), frame);
                    }
                }, updateFade, body);
    }

    static void runPhase(PlcLifecycleFrame frame, PlcLifecyclePhase phase, Runnable body) {
        frame.claim(phase);
        body.run();
        prepare(frame, phase);
    }

    static PlcLifecyclePhase endingPhase(EndingProvider provider) {
        if (provider == null) {
            return PlcLifecyclePhase.ENDING;
        }
        return provider.plcLifecyclePhaseOverride().orElseGet(() ->
                switch (provider.getCurrentPhase()) {
                    case CREDITS_TEXT -> PlcLifecyclePhase.CREDITS_TEXT;
                    case CREDITS_DEMO -> PlcLifecyclePhase.CREDITS_DEMO;
                    case POST_CREDITS, FINISHED -> PlcLifecyclePhase.POST_CREDITS;
                    case CUTSCENE -> PlcLifecyclePhase.ENDING;
                });
    }

    static void runNativeScreen(PlcLifecycleFrame frame, com.openggf.game.GameMode mode, Runnable body) {
        if (mode == com.openggf.game.GameMode.COMPETITION) {
            com.openggf.game.competition.NativeCompetitionIterationAccess.runAdmitted(frame, body);
        } else {
            runPhase(frame, com.openggf.game.nativemenu.NativeMenuController.phase(mode), body);
        }
    }

    static void prepare(PlcLifecycleFrame frame, PlcLifecyclePhase phase) {
        if (frame.isOwnedBy(phase)) {
            frame.prepareAfterLoop(phase);
        }
    }

    /**
     * Level submitted Nemesis before the first loc_62CC wait. Initialization
     * dispatches Obj_TitleCardInit once; finish its hardware scan/tail without
     * dispatching the next card routine (loc_62CC, sonic3k.asm:7769-7780).
     * Synchronous callbacks preserve virtual entry/provider seams and live
     * level rebinding; this bridge retains neither loop nor presentation state.
     */
    static void enterPendingTitleCard(Supplier<LevelManager> level, GameplayModeContext context,
                                      PlcLifecycleFrame frame, BiConsumer<Integer, Integer> enter,
                                      Supplier<TitleCardProvider> provider) {
        enter.accept(level.get().getTitleCardZone(), level.get().getTitleCardAct());
        TitleCardProvider titleCard = provider.get();
        if (titleCard != null && titleCard.shouldAdvanceVblankClockDuringLockedPhase()) {
            LevelFrameStep.executeHardwareTimedObjectScan(LevelFrameContext.from(context),
                    frame, PlcLifecyclePhase.LEVEL_TITLE_CARD, () -> { });
            level.get().advanceTitleCardVblankOnly();
        }
    }

    /** Returns whether the provider's hardware scan owns this iteration's preparation. */
    static boolean updateTitleCardProvider(LevelFrameContext frameContext, PlcLifecycleFrame frame,
                                           TitleCardProvider titleCard, GameplayModeContext context) {
        boolean hardwareTimedProviderScan = titleCard != null
                && titleCard.shouldAdvanceVblankClockDuringLockedPhase();
        if (hardwareTimedProviderScan) {
            LevelFrameStep.executeHardwareTimedObjectScan(frameContext, frame,
                    PlcLifecyclePhase.LEVEL_TITLE_CARD,
                    () -> WorldTitleCardUpdate.dispatch(titleCard, context.getWorldSession()));
            // The caller already attempted to claim this token. Prepare only its
            // surviving title phase, after the scan and all hardware boundaries.
            prepare(frame, PlcLifecyclePhase.LEVEL_TITLE_CARD);
        } else if (titleCard != null) {
            WorldTitleCardUpdate.dispatch(titleCard, context.getWorldSession());
        }
        return hardwareTimedProviderScan;
    }

    /** Samples the current module at the caller's pre-load or post-load boundary. */
    static boolean usesWhiteLevelEntry(GameModule module, int zone, int act) {
        var presentation = module.getGameService(LevelEntryPresentation.class);
        return presentation != null && presentation.usesWhiteTransition(zone, act);
    }

    /** Keeps the entry decision sampled before the caller's log and music fade. */
    static void startToLevelEntry(GameplayModeContext context, FadeManager fade,
                                  boolean white, Runnable completion) {
        if (white) {
            startToWhite(context, fade, completion);
        } else {
            startToBlack(context, fade, completion);
        }
    }

    static void startToBlack(GameplayModeContext context, FadeManager fade, Runnable completion) {
        fade.startFadeToBlack(wrap(context, completion));
    }

    static void startFromBlack(GameplayModeContext context, FadeManager fade, Runnable completion) {
        fade.startFadeFromBlack(wrap(context, completion));
    }

    /**
     * Reveals the screen after a data-select launch. When the launched level fades
     * its own palette in, the host adds no whole-screen fade over it: S3K's save
     * screen has already faded to black, {@code Level} loads line 0 with
     * {@code LoadPalette_Immediate} (docs/skdisasm/sonic3k.asm:7698) and
     * {@code loc_64DC}'s {@code Pal_FromBlack} reveals lines 1-3 inside LevelLoop
     * (7874-7878, 5018-5028).
     *
     * @param launched the launched level, or {@code null} when the launch failed
     *        and the data select itself is returning
     */
    static void revealDataSelectLaunch(FadeManager fade, LevelManager launched, int terminalNoOpFrames) {
        if (launched != null && launched.getAnimatedPatternManager() instanceof LevelPaletteFadeOwner owner
                && owner.presentsLevelFadeFromBlack()) {
            fade.clearOverlayForImmediatePaletteLoad();
            return;
        }
        fade.startFadeFromBlack(null, terminalNoOpFrames);
    }

    /**
     * Reveals a reload that re-enters the game's own level routine (a death
     * restart, a bonus-stage return). S3K's {@code Level} has already faded to
     * black, loads line 0 with {@code LoadPalette_Immediate} (sonic3k.asm:7698)
     * before its title loop and fades lines 1-3 in at loc_64DC; no whole-screen
     * reveal runs over the card's slide-in.
     */
    static void revealLevelRoutineEntry(
            GameplayModeContext context, FadeManager fade, TitleCardProvider titleCard) {
        if (titleCard instanceof FreshLevelTitleBoundaryPublication boundary
                && boundary.installsImmediateFreshLevelPalette()) {
            fade.clearOverlayForImmediatePaletteLoad();
            return;
        }
        startFromBlack(context, fade, null);
    }

    static void startToWhite(GameplayModeContext context, FadeManager fade, Runnable completion) {
        fade.startFadeToWhite(wrap(context, completion));
        // Pal_FadeToWhite's first act is a V-int wait, before any colour update
        // (docs/s2disasm/s2.asm:3571-3582). The frame that decided to fade has
        // already spent its V-int on the loop iteration that raised the
        // decision -- for the special stage exit that is the RunObjects pass
        // which set SS_Check_Rings_flag (s2.asm:6714-6725) -- so the first fade
        // step belongs to the next V-int, not to this one.
        fade.deferFirstStepToNextVint();
    }

    /**
     * Starts the same {@code Pal_FadeToWhite} window for a caller whose
     * iteration has already run its {@link FadeManager#update()} tick.
     *
     * <p>The deferral in {@link #startToWhite} models the fact that the
     * deciding iteration's V-int belongs to the loop the fade is leaving, not
     * to {@code Pal_FadeToWhite}'s first {@code WaitForVint}
     * (docs/s2disasm/s2.asm:3571-3582). It does that by swallowing the fade
     * tick of the iteration the fade is started in -- which is only the
     * deciding iteration's tick when the caller runs <em>before</em> that tick.
     * A caller that runs after it has already forgone the deciding iteration's
     * colour step by construction, so deferring again would swallow the first
     * real one too and stretch a 22-V-int routine
     * ({@code move.w #$15,d4} + {@code dbf}, s2.asm:3573-3581) over 23 rows.
     */
    static void startToWhiteAfterFrameFadeTick(
            GameplayModeContext context, FadeManager fade, Runnable completion) {
        fade.startFadeToWhite(wrap(context, completion));
    }

    static void startFromWhite(GameplayModeContext context, FadeManager fade, Runnable completion) {
        fade.startFadeFromWhite(wrap(context, completion));
    }

    private static Runnable wrap(GameplayModeContext context, Runnable completion) {
        Runnable callback = completion != null ? completion : () -> { };
        if (context == null || !context.isGameplayRuntimeReady()) {
            return callback;
        }
        return context.plcFrameLifecycle().beginNativeBlockingFade().wrapCompletion(callback);
    }
}
