package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Local ROM boundaries from Obj_HCZMiniboss in the locked-on sonic3k.asm. */
class TestHczMinibossRomParity {
    @Test
    void verticalLockSurvivesWaitingForHorizontalLock() throws Exception {
        Camera camera = mock(Camera.class);
        when(camera.getY()).thenReturn((short) 0x638);
        when(camera.getX()).thenReturn((short) 0x3600);
        HczMinibossInstance boss = boss(camera);
        invoke(boss, "updateWaitTrigger");
        verify(camera).setMinY((short) 0x638);
        verify(camera).setMaxY((short) 0x638);
        clearInvocations(camera);
        invoke(boss, "updateWaitTrigger");
        verify(camera, never()).setMinY(anyShort());
        verify(camera).setMinX((short) 0x3600);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void rocketWindDownRunsBothSpeedTwoWaitsAndRestoresMidway(int index) throws Exception {
        HczMinibossInstance boss = boss(mock(Camera.class));
        Object rocket = ((Object[]) field(boss, "rockets"))[index];
        int home = new int[] {0x80, 0, 0xC0, 0x40}[index];
        set(rocket, "routine", 12);
        set(rocket, "speed", 4);
        set(rocket, "phaseX", (home - 4) & 0xFF);
        set(rocket, "phaseY", (home - 4) & 0xFF);
        set(rocket, "collisionArmed", true);
        tickRocket(boss, rocket);
        assertEquals(home, field(rocket, "phaseX"));
        assertFalse((boolean) field(rocket, "collisionArmed"));
        for (int i = 0; i < 32; i++) tickRocket(boss, rocket);
        assertEquals(2, field(rocket, "speed"), "loc_6A3C4 retains speed 2 for another $1F wait");
        assertEquals(31, field(rocket, "timer"));
        Object snapshot = invoke(rocket, "captureRewindStateValue");
        for (int i = 0; i < 32; i++) tickRocket(boss, rocket);
        assertEquals(1, field(rocket, "speed"));
        assertEquals(index >= 2 ? 4 : 6, field(rocket, "routine"));
        Object expected = invoke(rocket, "captureRewindStateValue");
        Method restore = rocket.getClass().getDeclaredMethod("restoreRewindStateValue", snapshot.getClass());
        restore.setAccessible(true);
        restore.invoke(rocket, snapshot);
        for (int i = 0; i < 32; i++) tickRocket(boss, rocket);
        assertEquals(expected, invoke(rocket, "captureRewindStateValue"));
        for (int i = 0; i < 64; i++) tickRocket(boss, rocket);
        assertEquals(2, field(rocket, "routine"));
    }

    @Test
    void rocketDepthChangesAtRomPriorityBoundary() throws Exception {
        HczMinibossInstance boss = boss(mock(Camera.class));
        Object rocket = ((Object[]) field(boss, "rockets"))[0];
        for (int phase = 0; phase < 256; phase++) {
            set(rocket, "phaseY", phase);
            Method refresh = boss.getClass().getDeclaredMethod("refreshRocketPosition", rocket.getClass());
            refresh.setAccessible(true);
            refresh.invoke(boss, rocket);
            assertEquals(phase < 0x80, field(rocket, "front"), "sub_6AB1A phase " + phase);
        }
    }

    @Test
    void lowerEngineTouchUsesSameVIntFlickerGateAsItsDrawing() throws Exception {
        HczMinibossInstance boss = boss(mock(Camera.class));
        Object state = field(boss, "state");
        set(state, "routine", 6);
        for (int vInt : new int[] {0, 1, 16, 17, 254, 255}) {
            set(boss, "lastVIntRunCount", vInt);
            assertEquals((vInt & 1) == 0 ? 2 : 1, boss.getMultiTouchRegions().length);
            assertEquals((vInt & 1) == 0, invoke(boss, "isBodyEngineVisible"));
        }
        set(boss, "lastVIntRunCount", 0);
        set(boss, "closedBody", true);
        assertEquals(1, boss.getMultiTouchRegions().length);
        set(boss, "closedBody", false);
        set(state, "invulnerable", true);
        assertEquals(1, boss.getMultiTouchRegions().length, "engine remains harmful while core flashes");
        set(state, "defeated", true);
        assertNull(boss.getMultiTouchRegions());
        assertEquals(false, invoke(boss, "isBodyEngineVisible"));
    }

    private static HczMinibossInstance boss(Camera camera) {
        TestObjectServices services = new TestObjectServices().withCamera(camera);
        HczMinibossInstance boss = ObjectConstructionContext.with(services, -1, () -> new HczMinibossInstance(
                new ObjectSpawn(0x3720, 0x068C, 0x99, 0, 0, false, 0)));
        boss.setServices(services);
        return boss;
    }

    private static void tickRocket(HczMinibossInstance boss, Object rocket) throws Exception {
        Method method = boss.getClass().getDeclaredMethod("updateRocketState", rocket.getClass());
        method.setAccessible(true);
        method.invoke(boss, rocket);
    }

    private static Object invoke(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Field findField(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object field(Object target, String name) throws Exception {
        return findField(target, name).get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        findField(target, name).set(target, value);
    }
}
