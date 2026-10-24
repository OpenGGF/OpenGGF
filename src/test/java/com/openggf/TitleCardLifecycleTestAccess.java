package com.openggf;

import com.openggf.game.TitleCardProvider;

/** Typed access to the retained title owner, with no production compatibility mirrors. */
public final class TitleCardLifecycleTestAccess {
    private TitleCardLifecycleTestAccess() { }
    static GameLoopTitleCardLifecycle owner(GameLoop loop) {
        var owner = (GameLoopTitleCardLifecycle) SpecialStageLifecycleTestAccess.readField(loop, "titleCardLifecycle");
        if (owner == null) {
            // CALLS_REAL_METHODS fixtures bypass constructors; install the genuine live adapter.
            try {
                var constructor = Class.forName("com.openggf.GameLoop$TitleCardHost").getDeclaredConstructor(GameLoop.class);
                constructor.setAccessible(true);
                owner = new GameLoopTitleCardLifecycle((GameLoopTitleCardLifecycle.Host) constructor.newInstance(loop));
                SpecialStageLifecycleTestAccess.writeField(loop, "titleCardLifecycle", owner);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        return owner;
    }
    public static void provider(GameLoop loop, TitleCardProvider value) {
        SpecialStageLifecycleTestAccess.writeField(owner(loop), "titleCardProvider", value);
    }
    static TitleCardProvider cached(GameLoop loop) {
        return owner(loop).cachedProvider();
    }
    static void destination(GameLoop loop, PostTitleCardDestination value) {
        owner(loop).destination(value);
    }
    static PostTitleCardDestination destination(GameLoop loop) {
        return owner(loop).destination();
    }
    public static void bonusDestination(GameLoop loop, boolean bonus) {
        destination(loop, bonus ? PostTitleCardDestination.BONUS_STAGE : PostTitleCardDestination.LEVEL);
    }
    static LevelFrameResult result(GameLoop loop) {
        return owner(loop).releaseResult();
    }
    public static void release(GameLoop loop) throws Exception {
        owner(loop).exitTitleCard();
    }
    static void prelude(GameLoop loop) throws Exception {
        owner(loop).runInputNeutralTitleCardPlayerPrelude();
    }
    public static boolean update(GameLoop loop) throws Exception {
        return update(loop, false);
    }
    public static boolean update(GameLoop loop, boolean frameStep) throws Exception {
        return (boolean) SpecialStageLifecycleTestAccess.call(loop, "updateTitleCardMode", new Class<?>[]{boolean.class}, frameStep);
    }
}
