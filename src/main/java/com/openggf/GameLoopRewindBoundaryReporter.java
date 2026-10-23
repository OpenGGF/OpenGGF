package com.openggf;

import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.GameMode;
import com.openggf.game.rewind.RewindBoundary;
import com.openggf.game.session.GameplayModeContext;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Connects the installed live history's boundary callback to the loop's gameplay visit. */
final class GameLoopRewindBoundaryReporter {
    private final LiveRewindManager rewind;
    private final BooleanSupplier liveRuntime;
    private GameplayModeContext installedContext;
    private Consumer<RewindBoundary> override;

    GameLoopRewindBoundaryReporter(LiveRewindManager rewind, BooleanSupplier liveRuntime) {
        this.rewind = rewind;
        this.liveRuntime = liveRuntime;
    }

    void clear() {
        installedContext = null;
    }

    void refresh(GameplayModeContext context) {
        if (context != installedContext || override != null) install(context);
    }

    void initialAssemblyCompleted(GameplayModeContext assembled, GameplayModeContext current, GameMode mode) {
        if (assembled == null || assembled != current || !liveRuntime.getAsBoolean()) return;
        // Pending fresh assembly blocks engagement and retires its old reporter.
        // Rebind at this native completion before the first gameplay tick.
        rewind.bindOwner(assembled, false, mode);
        install(assembled);
        assembled.markRewindBoundary(RewindBoundary.LEVEL_LOAD);
    }

    void install(GameplayModeContext context, Consumer<RewindBoundary> consumer) {
        override = consumer;
        install(context);
    }

    void install(GameplayModeContext context) {
        if (context == null) return;
        Consumer<RewindBoundary> reporter = override != null
                ? override : rewind.boundaryReporterFor(context)::markBoundary;
        context.setRewindBoundaryReporter(boundary -> {
            if (liveRuntime.getAsBoolean()) reporter.accept(boundary);
        });
        installedContext = context;
    }
}
