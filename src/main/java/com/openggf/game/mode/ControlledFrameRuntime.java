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
    /** Locked normal title drawing remains native even though the controlled host stays in LEVEL. */
    public static boolean isNativeTitlePresentationActive(GameplayModeContext context) {
        return context != null && context.isGameplayRuntimeReady()
                && context.controlledPresentation().activeFor(context);
    }
    public static boolean prepareSetup(GameplayModeContext context) {
        if (context.controlledPresentation().hasPendingWork(context)) return false;
        return LevelFrameStep.admit(LevelFrameContext.from(context), context.getLevelManager(), false).result()
                == LevelFrameResult.SETUP_ONLY;
    }
    public static LevelFrameResult step(GameplayModeContext context, InputHandler input,
                                        LogicalInputSnapshot snapshot, boolean audioFrameOwned, Runnable beforeGameplay) {
        var course = new CourseControl(context, input);
        try {
            var controller = java.util.Objects.requireNonNull(controller(context));
            if (prepareSetup(context)) return LevelFrameResult.SETUP_ONLY;
            if (!audioFrameOwned) {
                var audio = GameServices.audio();
                audio.beginGameplayAudioFrame(audio.commandTimeline().currentFrame() + 1);
            }
            if (input != null && controller.capturesKeyboardText())
                com.openggf.control.InputHandlerInternalAccess.captureKeyboardText(input, controller);
            else if (input != null) com.openggf.control.InputHandlerInternalAccess.releaseKeyboardText(input, controller);
            var disposition = java.util.Objects.requireNonNull(controller.beforeFrame(course, snapshot), "gameplay frame disposition");
            boolean advance = disposition != GameplayFrameDisposition.HOLD;
            var presentation = context.controlledPresentation();
            if (!presentation.hasPendingWork(context)) serviceTitleCardOverlay(context);
            boolean transition = serviceLevelTransition(context);
            boolean presenting = !transition && presentation.service(context);
            if (transition || presenting) advance = false;
            if (advance) {
                LogicalInputSnapshot nativeInput = disposition == GameplayFrameDisposition.ADVANCE_NATIVE_INPUT
                        ? snapshot : java.util.Objects.requireNonNull(controller.nativeInput(course), "native input");
                beforeGameplay.run();
                EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
                context.plcFrameLifecycle().runLogicalIteration(frame -> { }, context.getFadeManager()::update,
                        frame -> {
                            var frameContext = LevelFrameContext.from(context);
                            LevelFrameStep.updateTimers(frameContext);
                            return com.openggf.control.InputHandlerInternalAccess.withNativeInput(input, nativeInput, () -> {
                                context.getSpriteManager().publishHeldInputForLevelEvents(input);
                                return LevelFrameStep.execute(frameContext, frame, PlcLifecyclePhase.ORDINARY_LEVEL,
                                        context.getLevelManager(), context.getCamera(),
                                        () -> context.getSpriteManager().update(input), LevelFrameStep.DIRECT_WRAPPER);
                            });
                        });
            }
            if (!advance && !transition && !presenting) serviceHeldLevelEntry(context);
            controller.afterTick(course, advance);
            return advance ? LevelFrameResult.GAMEPLAY_FRAME : LevelFrameResult.HELD;
        } catch (RuntimeException | Error failure) {
            try { context.directedPlayerLease().abort(); }
            catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
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
                false, false, provider,
                () -> com.openggf.game.InLevelTitleCardCoordinator.startIfRequested(
                        context.getLevelManager(), provider,
                        context.getGameStateManager().isEndOfLevelActive(), controlLock),
                controlLock, context.getWorldSession());
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

    /**
     * Session-lifetime ownership of a normal destination card, independent of its released
     * overlay tail. Registered with rewind alongside the provider and native assembly state.
     * No gameplay owner is retained across a load; each iteration resolves the current visit.
     */
    public static final class PresentationOwner implements
            com.openggf.game.rewind.RewindSnapshottable<PresentationOwner.Snapshot> {
        private boolean active;
        private long loadGeneration = -1;

        private boolean activeFor(GameplayModeContext context) {
            return active && (com.openggf.level.NativeLevelLoadAccess.isPending(context.getLevelManager())
                    || loadGeneration == context.getLevelManager().getCompletedProductionLoadGeneration());
        }

        public boolean hasPendingWork(GameplayModeContext context) {
            var level = context.getLevelManager();
            return activeFor(context) || level.isTitleCardRequested()
                    || level.hasPendingFreshLevelTransitionBoundary()
                    || com.openggf.level.NativeLevelLoadAccess.isPending(level);
        }

        /** A presentation row is held even when it completes assembly; input belongs to the next row. */
        private boolean service(GameplayModeContext context) {
            if (!hasPendingWork(context)) return false;
            EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
            context.plcFrameLifecycle().runLogicalIteration(frame -> {
                if (frame.isOwnedBy(PlcLifecyclePhase.PALETTE_FADE))
                    LevelFrameStep.dispatchGameVBlank(LevelFrameContext.from(context), frame);
            }, context.getFadeManager()::update, frame -> {
                var level = context.getLevelManager();
                if (com.openggf.level.NativeLevelLoadAccess.resumeScreenInitialization(level)
                        || com.openggf.level.NativeLevelLoadAccess.resumeTitleless(level, context, frame)) return null;
                var title = context.getWorldSession().getGameModule().getTitleCardProvider();
                if (!activeFor(context)) {
                    active = false;
                    if (level.consumeTitleCardRequest()) {
                        active = true;
                        loadGeneration = level.getCompletedProductionLoadGeneration();
                        boolean fresh = level.hasPendingFreshLevelTransitionBoundary();
                        var player = context.getCamera().getFocusedSprite();
                        if (!fresh && player != null) {
                            player.setXSpeed((short) 0); player.setYSpeed((short) 0); player.setGSpeed((short) 0);
                            player.setAir(false); player.setDead(false); player.setHurt(false);
                            player.setDeathCountdown(0); player.setInvulnerableFrames(0); player.setRolling(false);
                        }
                        lockControls(context, true);
                        if (title != null) {
                            if (fresh) title.initializeFreshLevelTransition(level.getTitleCardZone(), level.getTitleCardAct());
                            else title.initialize(level.getTitleCardZone(), level.getTitleCardAct());
                        }
                        if (fresh && title instanceof com.openggf.game.internal.FreshLevelTitleBoundaryPublication boundary
                                && boundary.installsImmediateFreshLevelPalette())
                            context.getFadeManager().clearOverlayForImmediatePaletteLoad();
                        if (!fresh && !context.getCamera().getFrozen()) context.getCamera().updatePosition(true);
                        if (title != null && title.shouldAdvanceVblankClockDuringLockedPhase()) {
                            LevelFrameStep.executeHardwareTimedObjectScan(LevelFrameContext.from(context), frame,
                                    PlcLifecyclePhase.LEVEL_TITLE_CARD, () -> { });
                            level.advanceTitleCardVblankOnly();
                        }
                        return null; // initialization is this row's sole title dispatch
                    }
                    // Explicit omission may already have completed the card. Native load and
                    // assembly are separate obligations, never inferred from an overlay flag.
                    if (!com.openggf.level.NativeLevelLoadAccess.isPending(level)) completeAssembly(context);
                    return null;
                }
                NativeTitleCardIteration.run(title, frame, context, level,
                        context.getSpriteManager(), context.getCamera(), () -> { }, () -> { },
                        phase -> { if (frame.isOwnedBy(phase)) frame.prepareAfterLoop(phase); },
                        LevelFrameStep.DIRECT_WRAPPER, () -> { },
                        () -> context.getSpriteManager().updateWithoutInput(),
                        () -> context.getSpriteManager().updateWithoutInput(), prelude -> {
                            level.completeInitialTitleCardPresentation();
                            lockControls(context, false);
                            if (!level.hasPendingFreshLevelTransitionBoundary()) {
                                var events = context.getWorldSession().getGameModule().getLevelEventProvider();
                                if (events != null) events.applyZonePlayerStateAfterTitleCard();
                            }
                            active = false;
                            completeAssembly(context);
                        });
                // A resumable load can complete during the native title scan.
                loadGeneration = level.getCompletedProductionLoadGeneration();
                return null;
            });
            return true;
        }

        private static void completeAssembly(GameplayModeContext context) {
            var level = context.getLevelManager();
            level.completeFreshLevelTransitionBoundary();
            level.consumePendingInitialProcessSpritesPass();
            com.openggf.level.LevelRewindBoundaryAccess.initialAssemblyCompleted(level);
        }

        private static void lockControls(GameplayModeContext context, boolean locked) {
            for (var sprite : context.getSpriteManager().getAllSprites())
                if (sprite instanceof com.openggf.sprites.playable.AbstractPlayableSprite playable)
                    playable.setControlLocked(locked);
        }

        public record Snapshot(boolean active, long loadGeneration) { }
        @Override public String key() { return "controlled-native-presentation"; }
        @Override public Snapshot capture() { return new Snapshot(active, loadGeneration); }
        @Override public void restore(Snapshot state) { active = state.active(); loadGeneration = state.loadGeneration(); }
        @Override public void resetForMissingSnapshot() { active = false; loadGeneration = -1; }
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
