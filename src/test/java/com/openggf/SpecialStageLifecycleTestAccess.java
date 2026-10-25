package com.openggf;

import java.lang.reflect.InvocationTargetException;

/** Test-only access to the special-stage lifecycle; no production compatibility mirrors. */
public final class SpecialStageLifecycleTestAccess {
    private SpecialStageLifecycleTestAccess() { }
    public static Object read(GameLoop loop, String name) { return readField(isState(name) ? owner(loop) : loop, name); }
    public static void write(GameLoop loop, String name, Object value) { writeField(isState(name) ? owner(loop) : loop, name, value); }
    public static Object invoke(GameLoop loop, String name, Class<?>[] signature, Object... args) throws Exception {
        return call(methodTarget(loop, name), name, signature, args);
    }
    private static final java.util.Set<String> STATE = java.util.Set.of(
            "activeSpecialStageProvider", "specialStageObservationPacing", "resultsScreen", "ssRingsCollected",
            "ssEmeraldCollected", "ssStageIndex", "activeSpecialStageRewardKind", "resultsFrameCounter",
            "returningFromSpecialStage", "specialStageTransitionPending", "specialStageEntryPresentation",
            "resultsExitFadeCompleted", "resultsExitToWhite", "resultsExitReady", "resultsExitPreLevelFadeFramesRemaining");
    private static final java.util.Set<String> METHODS = java.util.Set.of(
            "updateSpecialStageMode", "updateSpecialStageResultsMode", "runSpecialStageResultsIteration",
            "enterResultsScreenWithDebugRings", "enterSpecialStage", "defaultSpecialStageStartupPolicy",
            "enterResultsScreen", "exitResultsScreen", "doExitResultsScreen", "startResultsReturnFadeIn",
            "enterTitleCardFromResults", "updateSpecialStageInput");
    public static boolean isState(String name) { return STATE.contains(name); }
    public static boolean isOwnerMethod(String name) { return METHODS.contains(name); }
    public static Object methodTarget(GameLoop loop, String name) { return isOwnerMethod(name) ? owner(loop) : loop; }
    static GameLoopSpecialStageLifecycle owner(GameLoop loop) {
        var owner = (GameLoopSpecialStageLifecycle)readField(loop, "specialStageLifecycle");
        if (owner == null) {
            // Explicit fixture construction for constructor-bypassing Mockito drivers, like BONUS.
            try {
                var adapter = Class.forName("com.openggf.GameLoop$SpecialStageHost").getDeclaredConstructor(GameLoop.class);
                adapter.setAccessible(true);
                owner = new GameLoopSpecialStageLifecycle((GameLoopSpecialStageLifecycle.Host)adapter.newInstance(loop),
                        (com.openggf.audio.AudioManager)readField(loop, "audioManager"));
                writeField(loop, "specialStageLifecycle", owner);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }
        return owner;
    }
    static Object readField(Object target, String name) {
        try { var f = (target instanceof GameLoop ? GameLoop.class : target.getClass()).getDeclaredField(name); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    static void writeField(Object target, String name, Object value) {
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
