package com.openggf;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Test access to the visit owner and private application entry points; no substitute operations. */
final class SuspensionTestAccess {
    private SuspensionTestAccess() { }

    static GameLoopSuspension visit(Engine engine) { return get(engine, "suspension"); }
    static GameLoopSuspension.Effects binding(Engine engine) { return get(engine, "suspensionEffects"); }
    static boolean prepareSuspend(Engine engine, com.openggf.game.GameMode mode) {
        return visit(engine).prepareSuspend(binding(engine), mode);
    }
    static void captureSuspendThumbnail(Engine engine) { visit(engine).afterScene(binding(engine).capture()); }
    static void suspendToMasterTitle(Engine engine) { visit(engine).suspendToMasterTitle(binding(engine)); }
    static void resumeSuspendedGame(Engine engine) { visit(engine).resumeSuspendedGame(binding(engine)); }
    static void discardSuspendedGame(Engine engine) { visit(engine).discardSuspendedGame(binding(engine)); }

    record TestEffects(GameLoopSuspension.CaptureEffects capture, GameLoopSuspension.HubEffects hub,
                       GameLoopSuspension.RetirementEffects retirement, GameLoopSuspension.Effects binding) { }

    /** Narrow external effects for loop-only controls; all visit choreography remains production code. */
    static TestEffects effects() {
        var capture = org.mockito.Mockito.mock(GameLoopSuspension.CaptureEffects.class);
        var hub = org.mockito.Mockito.mock(GameLoopSuspension.HubEffects.class);
        var retirement = org.mockito.Mockito.mock(GameLoopSuspension.RetirementEffects.class);
        var world = org.mockito.Mockito.mock(com.openggf.game.session.WorldSession.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(world.getGameModule().getGameId()).thenReturn(com.openggf.game.GameId.S2);
        org.mockito.Mockito.when(capture.publishedWorld()).thenReturn(world);
        var settings = org.mockito.Mockito.mock(com.openggf.game.launch.SuspendedSettings.class);
        org.mockito.Mockito.when(capture.suspendSettings()).thenReturn(settings);
        var screen = org.mockito.Mockito.mock(com.openggf.game.MasterTitleScreen.class);
        org.mockito.Mockito.when(hub.currentScreen()).thenReturn(screen);
        org.mockito.Mockito.when(hub.createScreen()).thenReturn(screen);
        return new TestEffects(capture, hub, retirement, new GameLoopSuspension.Effects(capture, hub, retirement));
    }

    static void set(Object target, String name, Object value) {
        try { field(target, name).set(target, value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @SuppressWarnings("unchecked")
    static <T> T get(Object target, String name) {
        try { return (T) field(target, name).get(target); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
            catch (NoSuchFieldException absent) { /* Mockito/subclass callers retain the real owner. */ }
        }
        throw new NoSuchFieldException(name);
    }

    static <T> T call(Object target, String name) { return call(target, name, new Class<?>[0]); }

    @SuppressWarnings("unchecked")
    static <T> T call(Object target, String name, Class<?>[] types, Object... arguments) {
        try {
            Method method = Engine.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return (T) method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failure.getCause() instanceof Error error) throw error;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
