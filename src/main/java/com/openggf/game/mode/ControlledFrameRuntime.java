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
        boolean advance = controller.beforeTick(course, snapshot);
        if (advance) {
            beforeGameplay.run();
            EngineTiming.vIntRunCounter(EngineServices.current()).serviceRepresentedVBlank();
            context.plcFrameLifecycle().runLogicalIteration(frame -> { }, context.getFadeManager()::update,
                    frame -> {
                        var frameContext = LevelFrameContext.from(context);
                        LevelFrameStep.updateTimers(frameContext);
                        boolean overrideOwned = input.hasLogicalOverride();
                        input.setLogicalOverride(LogicalInputSnapshot.neutral());
                        try {
                            return LevelFrameStep.execute(frameContext, frame, PlcLifecyclePhase.ORDINARY_LEVEL,
                                    context.getLevelManager(), context.getCamera(),
                                    () -> context.getSpriteManager().update(input), LevelFrameStep.DIRECT_WRAPPER);
                        } finally {
                            input.setLogicalOverride(snapshot);
                            if (!overrideOwned) input.clearLogicalOverride();
                        }
                    });
        }
        if (!advance) serviceHeldLevelEntry(context);
        controller.afterTick(course, advance);
        return advance ? LevelFrameResult.GAMEPLAY_FRAME : LevelFrameResult.HELD;
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
