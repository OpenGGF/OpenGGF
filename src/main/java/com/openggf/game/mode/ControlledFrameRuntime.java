package com.openggf.game.mode;

import com.openggf.*;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.game.GameServices;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.EngineTiming;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.EngineServices;

/** Single controlled-mode path used by interactive, direct, headless and rewind stepping. */
public final class ControlledFrameRuntime {
    private ControlledFrameRuntime() { }

    public static GameplayFrameController controller(GameplayModeContext context) {
        return context == null || !context.isGameplayRuntimeReady() ? null
                : context.getWorldSession().getGameModule().gameplayFrameController();
    }

    public static LevelFrameResult step(GameplayModeContext context, InputHandler input,
                                        LogicalInputSnapshot snapshot) {
        return step(context, input, snapshot, false);
    }
    public static LevelFrameResult step(GameplayModeContext context, InputHandler input,
                                        LogicalInputSnapshot snapshot, boolean audioFrameOwned) {
        return step(context, input, snapshot, audioFrameOwned, () -> { });
    }
    public static boolean prepareSetup(GameplayModeContext context) {
        return LevelFrameStep.admit(LevelFrameContext.from(context), context.getLevelManager(), false).result()
                == LevelFrameResult.SETUP_ONLY;
    }
    public static LevelFrameResult step(GameplayModeContext context, InputHandler input,
                                        LogicalInputSnapshot snapshot, boolean audioFrameOwned, Runnable beforeGameplay) {
        var controller = java.util.Objects.requireNonNull(controller(context));
        if (prepareSetup(context)) return LevelFrameResult.SETUP_ONLY;
        if (!audioFrameOwned) {
            var audio = GameServices.audio();
            audio.beginGameplayAudioFrame(audio.commandTimeline().currentFrame() + 1);
        }
        var course = new CourseControl(context, input);
        try {
            var disposition = java.util.Objects.requireNonNull(controller.beforeFrame(course, snapshot), "gameplay frame disposition");
            boolean advance = disposition != GameplayFrameDisposition.HOLD;
            serviceTitleCardOverlay(context);
            boolean transition = serviceLevelTransition(context);
            if (transition) advance = false;
            if (advance) {
                beforeGameplay.run();
                EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
                context.plcFrameLifecycle().runLogicalIteration(frame -> { }, context.getFadeManager()::update,
                        frame -> {
                            var frameContext = LevelFrameContext.from(context);
                            LevelFrameStep.updateTimers(frameContext);
                            boolean overrideOwned = input.hasLogicalOverride();
                            var previousOverride = input.logical();
                            input.setLogicalOverride(disposition == GameplayFrameDisposition.ADVANCE_NATIVE_INPUT
                                    ? snapshot : LogicalInputSnapshot.neutral());
                            try {
                                context.getSpriteManager().publishHeldInputForLevelEvents(input);
                                return LevelFrameStep.execute(frameContext, frame, PlcLifecyclePhase.ORDINARY_LEVEL,
                                        context.getLevelManager(), context.getCamera(),
                                        () -> context.getSpriteManager().update(input), LevelFrameStep.DIRECT_WRAPPER);
                            } finally {
                                input.setLogicalOverride(previousOverride);
                                if (!overrideOwned) input.clearLogicalOverride();
                            }
                        });
            }
            if (!advance && !transition) serviceHeldLevelEntry(context);
            controller.afterTick(course, advance);
            return advance ? LevelFrameResult.GAMEPLAY_FRAME : LevelFrameResult.HELD;
        } finally { course.closeFrame(); }
    }

    /** Native destination requests remain presentation work while a creator holds the source world. */
    private static boolean serviceLevelTransition(GameplayModeContext context) {
        var level = context.getLevelManager();
        var fade = context.getFadeManager();
        if (!fade.isActive() && level.consumeZoneActRequest()) {
            int zone = level.getRequestedZone(), act = level.getRequestedAct();
            int music = level.getRequestedMusicId();
            level.setLevelInactiveForTransition(true);
            var blocking = context.plcFrameLifecycle().beginNativeBlockingFade();
            fade.startFadeToBlack(blocking.wrapCompletion(() -> {
                try {
                    if (music >= 0) level.setSuppressNextMusicChange(true);
                    if (context.getWorldSession().getGameModule().getTitleCardProvider()
                            instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication)
                        level.loadZoneAndActAtFreshTitleCardBoundary(zone, act);
                    else level.loadZoneAndActWithTitleCard(zone, act);
                    if (music >= 0) GameServices.audio().playMusic(music);
                    var title = context.getWorldSession().getGameModule().getTitleCardProvider();
                    if (level.hasPendingFreshLevelTransitionBoundary()
                            && title instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication boundary
                            && boundary.installsImmediateFreshLevelPalette()) fade.clearOverlayForImmediatePaletteLoad();
                    else fade.startFadeFromBlack(null);
                }
                catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load controlled destination", failure); }
            }));
        }
        if (!level.isLevelInactiveForTransition()) return false;
        context.plcFrameLifecycle().runLogicalIteration(frame -> {
            if (frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE))
                LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(context), frame);
        }, fade::update, frame -> null);
        return true;
    }

    /** A creator HOLD freezes world physics, but the released native title still owns its exit tail. */
    private static void serviceTitleCardOverlay(GameplayModeContext context) {
        var provider = context.getWorldSession().getGameModule().getTitleCardProvider();
        java.util.function.Consumer<Boolean> controlLock = locked -> {
            for (var sprite : context.getSpriteManager().getAllSprites()) {
                if (sprite instanceof com.openggf.sprites.playable.AbstractPlayableSprite playable) {
                    playable.setControlLocked(locked);
                }
            }
        };
        com.openggf.trace.replay.TraceSuppressedRowClosure.executeUnownedTitleCardWork(
                false, provider,
                () -> com.openggf.game.InLevelTitleCardCoordinator.startIfRequested(
                        context.getLevelManager(), provider,
                        context.getGameStateManager().isEndOfLevelActive(), controlLock),
                controlLock);
    }

    /**
     * A held row is still one presented vertical interrupt. Level-entry work the profile counts
     * in interrupts (Sonic 2's Level_PlayBgm countdown, s2.asm:4767-4911) keeps elapsing in that
     * time, so the zone music starts on the ROM's schedule instead of on a later advanced row.
     * Only a pending entry-music publication is serviced; held rows advance no gameplay.
     */
    private static void serviceHeldLevelEntry(GameplayModeContext context) {
        var profile = context.getWorldSession().getGameModule().getLevelInitProfile();
        if (profile.isLevelMusicPublicationPending()) profile.serviceLevelLoadVBlank();
    }

    public static boolean retainRolling(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        var context = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
        var controller = controller(context);
        return controller != null && controller.retainRolling()
                && context.getCamera().getFocusedSprite() == sprite && !sprite.isHurt() && !sprite.getDead();
    }

    public static void prepareRoster(GameplayModeContext context,
                                    com.openggf.game.rewind.snapshot.SpriteManagerSnapshot snapshot) {
        CourseControl.prepareRoster(context, snapshot);
    }
}
