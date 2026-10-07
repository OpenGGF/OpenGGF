package com.openggf.mods.code;

import com.openggf.control.PhysicalGamepad;
import com.openggf.control.PhysicalInput;
import com.openggf.control.PhysicalInputEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Regression checks against the packaged creator's physical binding reducer. */
class TestSitarHeroControls {
    @TempDir static Path work;
    private static ExampleModHarness harness;
    private static Class<?> settingsType;
    private static Class<?> controlsType;
    private static Class<?> bindingType;

    @BeforeAll
    static void compileExample() throws Exception {
        harness = ExampleModHarness.build(Path.of("examples/sitar-hero"), work.resolve("mod"));
        settingsType = harness.loader().loadClass("sitarhero.controls.ControlSettings");
        controlsType = harness.loader().loadClass("sitarhero.controls.MappedControls");
        bindingType = harness.loader().loadClass("sitarhero.controls.PhysicalBinding");
    }

    @AfterAll
    static void closeExample() throws Exception { if (harness != null) harness.close(); }

    @Test
    void padKickTapIsIndependentOfHeldKeyboardKickAndCanRetrigger() throws Exception {
        Object controls = controls(true);
        List<?> key = accept(controls, input(List.of(32), pad(), key(1, 32, 1)));
        assertEquals(16, number(key.getFirst(), "directPressed"));
        List<?> pad = accept(controls, input(List.of(32), pad(0), button(2, 0, 1)));
        assertEquals(16, number(pad.getFirst(), "directPressed"), "held Space must not suppress pad A kick");
        List<?> released = accept(controls, input(List.of(32), pad(), button(3, 0, 0)));
        assertEquals(0, number(released.getFirst(), "directPressed"));
        assertEquals(16, number(released.getFirst(), "frets"), "the keyboard alternative stays held");
        List<?> again = accept(controls, input(List.of(32), pad(0), button(4, 0, 1)));
        assertEquals(16, number(again.getFirst(), "directPressed"));
    }

    @Test
    void padStrumPowerAndPauseReactWhileKeyboardAlternativesStayHeld() throws Exception {
        Object controls = controls(false);
        accept(controls, input(List.of(265, 340, 256), pad(), key(1, 265, 1), key(2, 340, 1), key(3, 256, 1)));
        List<?> frames = accept(controls, input(List.of(265, 340, 256), pad(11, 6, 7),
                button(4, 11, 1), button(5, 6, 1), button(6, 7, 1)));
        assertTrue(flag(frames.get(0), "strum"));
        assertTrue(flag(frames.get(1), "power"));
        assertTrue(flag(frames.get(2), "pause"));
        assertFalse(flag(frames.getLast(), "strum"));
        assertFalse(flag(frames.getLast(), "power"));
        assertFalse(flag(frames.getLast(), "pause"));
    }

    @Test
    void melodicFretHeldStateStillMergesAlternativesAndShortTapsRemainOrdered() throws Exception {
        Object controls = controls(false);
        accept(controls, input(List.of(65), pad(), key(1, 65, 1)));
        float[] axes = {0, 0, 0, 0, 1, -1};
        PhysicalGamepad shoulder = new PhysicalGamepad(0, "pad", new boolean[15], axes);
        accept(controls, input(List.of(65), shoulder, axis(2, 4, 1)));
        List<?> keyReleased = accept(controls, input(List.of(), shoulder, key(3, 65, 0)));
        assertEquals(1, number(keyReleased.getFirst(), "frets"), "held analog fret remains after key release");
        List<?> tap = accept(controls, input(List.of(), shoulder, key(4, 265, 1), key(5, 265, 0)));
        assertTrue(flag(tap.get(0), "strum"));
        assertFalse(flag(tap.get(1), "strum"));
        assertEquals(4, number(tap.get(0), "timestamp"));
        assertEquals(5, number(tap.get(1), "timestamp"));
    }

    @Test
    void identicalAlternativeBindingsProduceOneStrikeAndResetHeldStateProducesNone() throws Exception {
        Object settings = settingsType.getConstructor().newInstance();
        Object binding = bindingType.getConstructor(char.class, int.class, int.class, int.class)
                .newInstance('K', -1, 265, 1);
        settingsType.getMethod("bind", boolean.class, boolean.class, int.class, bindingType)
                .invoke(settings, false, true, 5, binding);
        Object controls = controlsType.getConstructor(settingsType).newInstance(settings);
        controlsType.getMethod("reset", PhysicalInput.class, boolean.class).invoke(controls, input(List.of(), pad()), false);
        List<?> frames = accept(controls, input(List.of(265), pad(), key(1, 265, 1)));
        long strikes = 0;
        for (Object frame : frames) if (flag(frame, "strum")) strikes++;
        assertEquals(1, strikes);
        controlsType.getMethod("reset", PhysicalInput.class, boolean.class)
                .invoke(controls, input(List.of(265), pad()), false);
        assertFalse(flag(accept(controls, input(List.of(265), pad())).getFirst(), "strum"));
    }

    @Test
    void malformedPersistedBindingsRetainDefaultsWithoutFaultingScene() throws Exception {
        Object settings = settingsType.getConstructor().newInstance();
        assertDoesNotThrow(() -> settingsType.getMethod("read", String.class).invoke(settings,
                "k0.0=,0,1,1\np0.5=,0,11,1\nk0.1=KK,-1,65,1\nk0.2=K,-1,86,1\n"));
        Object first = settingsType.getMethod("binding", boolean.class, boolean.class, int.class)
                .invoke(settings, false, false, 0);
        assertEquals(65, number(first, "code"));
        Object strum = settingsType.getMethod("binding", boolean.class, boolean.class, int.class)
                .invoke(settings, false, true, 5);
        assertEquals(11, number(strum, "code"));
        Object valid = settingsType.getMethod("binding", boolean.class, boolean.class, int.class)
                .invoke(settings, false, false, 2);
        assertEquals(86, number(valid, "code"));
        InvocationTargetException failure = assertThrows(InvocationTargetException.class,
                () -> bindingType.getMethod("decode", String.class).invoke(null, ",0,1,1"));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    }

    private static Object controls(boolean drums) throws Exception {
        Object settings = settingsType.getConstructor().newInstance();
        Object controls = controlsType.getConstructor(settingsType).newInstance(settings);
        controlsType.getMethod("reset", PhysicalInput.class, boolean.class).invoke(controls, input(List.of(), pad()), drums);
        return controls;
    }

    private static List<?> accept(Object controls, PhysicalInput input) throws Exception {
        return (List<?>) controlsType.getMethod("accept", PhysicalInput.class).invoke(controls, input);
    }

    private static PhysicalInput input(List<Integer> keys, PhysicalGamepad pad, PhysicalInputEvent... events) {
        return new PhysicalInput(10, keys, List.of(pad), List.of(events), 0);
    }

    private static PhysicalGamepad pad(int... buttons) {
        boolean[] state = new boolean[15]; for (int button : buttons) state[button] = true;
        return new PhysicalGamepad(0, "pad", state, new float[]{0, 0, 0, 0, -1, -1});
    }

    private static PhysicalInputEvent key(long time, int code, float value) {
        return new PhysicalInputEvent(time, time, PhysicalInputEvent.Kind.KEY, -1, code, value);
    }

    private static PhysicalInputEvent button(long time, int code, float value) {
        return new PhysicalInputEvent(time, time, PhysicalInputEvent.Kind.BUTTON, 0, code, value);
    }

    private static PhysicalInputEvent axis(long time, int code, float value) {
        return new PhysicalInputEvent(time, time, PhysicalInputEvent.Kind.AXIS, 0, code, value);
    }

    private static long number(Object value, String accessor) throws Exception {
        return ((Number) value.getClass().getMethod(accessor).invoke(value)).longValue();
    }

    private static boolean flag(Object value, String accessor) throws Exception {
        return (boolean) value.getClass().getMethod(accessor).invoke(value);
    }
}
