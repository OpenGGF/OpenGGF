package com.openggf;

import com.openggf.game.EndingProvider;
import com.openggf.game.resources.PlcFrameLifecycleCoordinator.PlcLifecycleFrame;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.graphics.FadeManager;

/** Bridges GameLoop-owned modes and fades to the session PLC lifecycle. */
final class GameLoopPlcLifecycle {
    private GameLoopPlcLifecycle() {
    }

    static void runPhase(PlcLifecycleFrame frame, PlcLifecyclePhase phase, Runnable body) {
        frame.claim(phase);
        body.run();
        prepare(frame, phase);
    }

    static void prepare(PlcLifecycleFrame frame, PlcLifecyclePhase phase) {
        if (frame.isOwnedBy(phase)) {
            frame.prepareAfterLoop(phase);
        }
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

    static void startToBlack(GameplayModeContext context, FadeManager fade, Runnable completion) {
        fade.startFadeToBlack(wrap(context, completion));
    }

    static void startFromBlack(GameplayModeContext context, FadeManager fade, Runnable completion) {
        fade.startFadeFromBlack(wrap(context, completion));
    }

    static void startToWhite(GameplayModeContext context, FadeManager fade, Runnable completion) {
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
