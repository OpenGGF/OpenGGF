package com.openggf;

import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.rewind.LiveRewindManager;
import com.openggf.game.rewind.RewindBoundary;
import java.util.function.Consumer;

/** Test access to the gameplay visit that actually owns a production loop's history. */
public final class GameLoopRewindTestAccess {
    private GameLoopRewindTestAccess() { }

    public static GameplayModeContext owner(GameLoop loop) {
        return loop.resolveGameplayModeContext();
    }

    public static void observeBoundaries(GameLoop loop, LiveRewindManager live,
                                         Consumer<RewindBoundary> observer) {
        loop.installLiveRewindBoundaryReporter(boundary -> {
            live.markBoundary(boundary);
            // Observe the same completed publication as the production floor.
            owner(loop).plcFrameLifecycle().afterConsumedIteration(() -> observer.accept(boundary));
        });
    }
}
