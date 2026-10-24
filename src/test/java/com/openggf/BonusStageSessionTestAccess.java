package com.openggf;

import com.openggf.game.*;
import java.lang.reflect.InvocationTargetException;

/** Typed test access to the retained visit, including constructor-bypassing driver fixtures. */
public final class BonusStageSessionTestAccess {
    private BonusStageSessionTestAccess() {}
    private static GameLoopBonusStageSession owner(GameLoop loop) {
        var owner = (GameLoopBonusStageSession) read(loop, "bonusSession");
        if (owner == null) {
            // CALLS_REAL_METHODS bypasses GameLoop's constructor. Install its actual live adapter.
            try {
                var constructor = Class.forName("com.openggf.GameLoop$BonusHost").getDeclaredConstructor(GameLoop.class);
                constructor.setAccessible(true);
                owner = new GameLoopBonusStageSession((GameLoopBonusStageSession.Host) constructor.newInstance(loop),
                        (com.openggf.audio.AudioManager) read(loop, "audioManager"));
                write(loop, "bonusSession", owner);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        return owner;
    }
    public static BonusStageProvider provider(GameLoop loop) { return (BonusStageProvider) read(owner(loop), "activeBonusStageProvider"); }
    public static void provider(GameLoop loop, BonusStageProvider provider) { write(owner(loop), "activeBonusStageProvider", provider); }
    public static boolean pending(GameLoop loop) { return (boolean) read(owner(loop), "bonusStageTransitionPending"); }
    public static void pending(GameLoop loop, boolean pending) { write(owner(loop), "bonusStageTransitionPending", pending); }
    public static void deferred(GameLoop loop, BonusStageProvider provider) { write(owner(loop), "deferredBonusProvider", provider); }
    public static BonusStageProvider deferred(GameLoop loop) { return (BonusStageProvider) read(owner(loop), "deferredBonusProvider"); }
    public static void enter(GameLoop loop, BonusStageType type) throws Exception { call(owner(loop), "enterBonusStage", new Class<?>[]{BonusStageType.class}, type); }
    public static void entered(GameLoop loop, BonusStageProvider provider, BonusStageType type, BonusStageState state) throws Exception {
        call(owner(loop), "doEnterBonusStage", new Class<?>[]{BonusStageProvider.class, BonusStageType.class, BonusStageState.class}, provider, type, state);
    }
    public static void exit(GameLoop loop) throws Exception { call(owner(loop), "exitBonusStage", new Class<?>[0]); }
    public static void exited(GameLoop loop, BonusStageProvider provider, BonusStageState state) throws Exception {
        call(owner(loop), "doExitBonusStage", new Class<?>[]{BonusStageProvider.class, BonusStageState.class}, provider, state);
    }
    public static void completeDeferred(GameLoop loop) throws Exception { call(owner(loop), "applyDeferredBonusStageSetup", new Class<?>[0]); }
    public static void priority(GameLoop loop) throws Exception { call(owner(loop), "forcePlayerHighPriorityInBonusStage", new Class<?>[0]); }
    static Object read(Object target, String name) {
        try { var f = (target instanceof GameLoop ? GameLoop.class : target.getClass()).getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static void write(Object target, String name, Object value) {
        try { var f = (target instanceof GameLoop ? GameLoop.class : target.getClass()).getDeclaredField(name); f.setAccessible(true); f.set(target, value); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static Object call(Object target, String name, Class<?>[] signature, Object... args) throws Exception {
        var m = (target instanceof GameLoop ? GameLoop.class : target.getClass()).getDeclaredMethod(name, signature); m.setAccessible(true);
        try { return m.invoke(target, args); } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) throw cause;
            if (e.getCause() instanceof Error cause) throw cause;
            throw e;
        }
    }
}
