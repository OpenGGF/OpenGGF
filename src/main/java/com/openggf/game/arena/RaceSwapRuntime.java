package com.openggf.game.arena;

import com.openggf.LevelFrameContext;
import com.openggf.game.GameModule;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;

/** Semantic bridge used by the canonical live/replay frame owner. */
public final class RaceSwapRuntime {
    private RaceSwapRuntime() { }
    public static RaceSwapApplier applier(GameModule module) {
        return module == null ? null : module.getGameService(RaceSwapApplier.class);
    }
    private static GameplayModeContext mode(LevelFrameContext frame) {
        var context=SessionManager.getCurrentGameplayMode();
        return context!=null && context.getLevelManager()==frame.levelManager() ? context : null;
    }
    public static boolean active(LevelFrameContext frame) {
        var context=mode(frame);
        return context!=null && context.getRunPolicy().swapEnabled() && applier(frame.gameModule())!=null;
    }
    public static void afterVBlank(LevelFrameContext frame) {
        var context=mode(frame);
        if (context==null) throw new IllegalStateException("Race frame has no matching gameplay owner");
        applier(frame.gameModule()).afterVBlank(context);
    }
    public static boolean freezesObjects(LevelFrameContext frame) {
        return active(frame) && applier(frame.gameModule()).freezesObjects();
    }
}
