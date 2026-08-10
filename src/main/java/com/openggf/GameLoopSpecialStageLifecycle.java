package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameMode;
import com.openggf.game.SpecialStageDebugProvider;
import com.openggf.game.SpecialStageEntryPresentationController;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.FadeManager;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/** Owns the SPECIAL_STAGE frame body so {@link GameLoop} remains a mode dispatcher. */
final class GameLoopSpecialStageLifecycle {
    private GameLoopSpecialStageLifecycle() {
    }

    static void update(SpecialStageProvider provider,
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
        if (isUnmodifiedDebugKeyPressed(debugKeyPressed, org.lwjgl.glfw.GLFW.GLFW_KEY_X)) provider.debugNextStage();
        if (isUnmodifiedDebugKeyPressed(debugKeyPressed, org.lwjgl.glfw.GLFW.GLFW_KEY_Z)) provider.debugToggleLayoutSet();
        if (isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY))) {
            completeWithEmerald.run();
        }
        if (isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_FAIL_KEY))) {
            failStage.run();
        }
        if (isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_SPRITE_DEBUG_KEY))) {
            provider.toggleSpriteDebugMode();
        }
        if (isUnmodifiedDebugKeyPressed(
                debugKeyPressed, config.getInt(SonicConfiguration.SPECIAL_STAGE_PLANE_DEBUG_KEY))) {
            provider.cyclePlaneDebugMode();
        }
        updateDebugNavigation(provider, config, debugKeyPressed);

        TraceSessionLauncher session = TraceSessionLauncher.active();
        if (session != null) session.applySpecialStageTraceInputIfActive(input);
        boolean skipTick = session != null && session.shouldSkipCurrentSpecialStageTick();
        if (!skipTick) {
            LevelFrameStep.executeHardwareTimedObjectScan(
                    LevelFrameContext.from(gameplayMode), plcFrame,
                    PlcLifecyclePhase.SPECIAL_STAGE,
                    () -> updateProvider(provider, pacing, updateInput));
        } else if (session.skippedSpecialStagePlcPhase().isPresent()) {
            LevelFrameStep.serviceVBlankOnly(LevelFrameContext.from(gameplayMode), plcFrame,
                    session.skippedSpecialStagePlcPhase().orElseThrow());
        }
        presentation.update(provider, fadeManager, playStageMusic, gameplayMode.plcFrameLifecycle());
        if (session != null) session.advanceSpecialStageTraceCursorIfActive(input);
        if (rewindable.getAsBoolean() && session == null) {
            rewind.recordExternalFrame(currentMode, false, input);
        }
        if (provider.isFinished() && (session == null || !session.isSpecialStageSession())) {
            enterResults.accept(provider.isEmeraldCollected());
        }
    }

    private static void updateDebugNavigation(SpecialStageProvider provider,
                                              SonicConfigurationService config,
                                              IntPredicate pressed) {
        if (!provider.isSpriteDebugMode()) return;
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
            pacing.applyPassInput(pass, provider);
            provider.update();
            pacing.afterPass(pass);
        }
    }
}
