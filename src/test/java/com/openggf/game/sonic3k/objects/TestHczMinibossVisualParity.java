package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TestHczMinibossVisualParity {
    @Test
    void compositorUsesIndependentRomPrioritiesAndReverseSlotOrder() throws Exception {
        DrawHarness harness = new DrawHarness();
        HczMinibossInstance boss = harness.boss;
        set(field(boss, "state"), "routine", 6);
        Object[] rockets = (Object[]) field(boss, "rockets");
        int[] phases = {0x00, 0x10, 0x50, 0xB0};
        for (int i = 0; i < 4; i++) {
            set(rockets[i], "phaseY", phases[i]);
            invokeRocket(boss, "refreshRocketPosition", rockets[i]);
            set(rockets[i], "x", 100 + i);
            set(rockets[i], "exhaustActive", i < 3);
        }
        boss.appendRenderCommands(new ArrayList<GLCommand>());
        // Draw_Sprite/Render_Sprites: lower bucket and earlier slot wins.
        // Child tables allocate water, rockets, lower engine, then rocket exhausts.
        assertEquals(List.of(0x0E, 0x15, 0x1A, 0x16, 0, 0x0F, 6, 2, 1, 0x11),
                harness.draws.stream().map(Draw::frame).toList());
        assertEquals(List.of(5, 5, 5, 5, 5, 4, 4, 4, 4, 3),
                harness.draws.stream().map(Draw::bucket).toList());
        assertEquals(List.of(102, 101, 100), harness.draws.subList(6, 9).stream().map(Draw::x).toList());
        assertEquals(5, harness.bucket, "SAT bucket must be restored for the next object");
    }

    @Test
    void exhaustWaitsForItsOwnFullSpeedAndUsesEvenVIntGate() throws Exception {
        DrawHarness harness = new DrawHarness();
        HczMinibossInstance boss = harness.boss;
        set(field(boss, "state"), "routine", 6);
        Object[] rockets = (Object[]) field(boss, "rockets");
        invoke(boss, "beginRocketWindUp");
        for (int i = 0; i < 128; i++) invokeRocket(boss, "updateRocketState", rockets[0]);
        assertEquals(true, field(rockets[0], "collisionArmed"));
        assertEquals(false, invokeRocket(boss, "isRocketExhaustVisible", rockets[0]),
                "loc_6A384 arms collision; loc_6A3A0 exposes exhaust 32 updates later");
        for (int i = 0; i < 32; i++) invokeRocket(boss, "updateRocketState", rockets[0]);
        assertEquals(true, invokeRocket(boss, "isRocketExhaustVisible", rockets[0]));
        assertEquals(false, invokeRocket(boss, "isRocketExhaustVisible", rockets[1]));
        set(boss, "lastVIntRunCount", 1);
        assertEquals(false, invokeRocket(boss, "isRocketExhaustVisible", rockets[0]));
        set(boss, "lastVIntRunCount", 2);
        set(field(boss, "state"), "defeated", true);
        assertEquals(false, invokeRocket(boss, "isRocketExhaustVisible", rockets[0]));
    }

    @Test
    void waterCooldownRunsDeceleratingScriptAndFreezesUntilDefeatHandoff() throws Exception {
        DrawHarness harness = new DrawHarness();
        HczMinibossInstance boss = harness.boss;
        set(field(boss, "state"), "routine", 22);
        set(boss, "waterEffectRoutine", 10);
        set(boss, "waterEffectAnimFrame", 0);
        set(boss, "waterEffectAnimTimer", 0);
        invoke(boss, "updateWaterEffect");
        assertEquals(0x17, field(boss, "waterEffectFrame"));
        assertEquals(2, field(boss, "waterEffectAnimTimer"));
        for (int i = 1; i < 96; i++) invoke(boss, "updateWaterEffect");
        assertEquals(10, field(boss, "waterEffectRoutine"));
        invoke(boss, "updateWaterEffect");
        assertEquals(4, field(boss, "waterEffectRoutine"));
        assertEquals(0x16, field(boss, "waterEffectFrame"));

        set(field(boss, "state"), "routine", 26);
        set(field(boss, "state"), "defeated", true);
        set(boss, "waterEffectRoutine", 12);
        set(boss, "waterEffectFrame", 0x18);
        invoke(boss, "updateWaterEffect");
        assertEquals(0x18, field(boss, "waterEffectFrame"));
        assertEquals(true, invoke(boss, "isWaterEffectVisible"));
        set(boss, "defeatHandoffStarted", true);
        assertEquals(false, invoke(boss, "isWaterEffectVisible"));
        assertEquals(false, invoke(boss, "isFightVisible"));
    }

    @Test
    void defeatedRocketsUseIndexedFlippedVelocitiesAndFlickerWithoutFurtherOrbit() throws Exception {
        DrawHarness harness = new DrawHarness();
        HczMinibossInstance boss = harness.boss;
        Object state = field(boss, "state");
        set(state, "x", 160);
        set(state, "y", 112);
        set(state, "defeated", true);
        Object[] rockets = (Object[]) field(boss, "rockets");
        int[] expectedXVelocity = {-0x400, 0x400, -0x300, 0x400};
        for (int i = 0; i < 4; i++) {
            Object rocket = rockets[i];
            set(rocket, "routine", 10);
            set(rocket, "speed", 4);
            set(rocket, "exhaustActive", true);
            invokeRocket(boss, "updateRocketState", rocket);
            assertEquals(expectedXVelocity[i], field(rocket, "debrisXVel"));
            assertEquals(-0x300, field(rocket, "debrisYVel"));
            int x = (int) field(rocket, "x"), y = (int) field(rocket, "y");
            int phase = (int) field(rocket, "phaseY");
            invokeRocket(boss, "updateRocketState", rocket);
            assertEquals(x + expectedXVelocity[i] / 256, field(rocket, "x"));
            assertEquals(y - 3, field(rocket, "y"));
            assertEquals(-0x2C8, field(rocket, "debrisYVel"));
            assertEquals(phase, field(rocket, "phaseY"));
            assertEquals(false, field(rocket, "debrisVisible"));
            invokeRocket(boss, "updateRocketState", rocket);
            assertEquals(true, field(rocket, "debrisVisible"));
            for (int frame = 0; frame < 128; frame++) invokeRocket(boss, "updateRocketState", rocket);
            assertEquals(true, field(rocket, "debrisDestroyed"));
        }
    }

    private record Draw(int bucket, int frame, int x) {}

    private static final class DrawHarness {
        final List<Draw> draws = new ArrayList<>();
        int bucket = 5;
        final HczMinibossInstance boss;

        DrawHarness() {
            GraphicsManager graphics = mock(GraphicsManager.class);
            PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);
            doAnswer(call -> { bucket = call.getArgument(0); return null; })
                    .when(graphics).setCurrentSpriteSatBucket(anyInt());
            doAnswer(call -> { draws.add(new Draw(bucket, call.getArgument(0), call.getArgument(1))); return null; })
                    .when(renderer).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean());
            doAnswer(call -> { draws.add(new Draw(bucket, call.getArgument(0), call.getArgument(1))); return null; })
                    .when(renderer).drawFrameIndex(anyInt(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt());
            TestObjectServices services = new TestObjectServices().withCamera(mock(Camera.class)).withGraphicsManager(graphics);
            boss = ObjectConstructionContext.with(services, -1, () -> new HczMinibossInstance(
                    new ObjectSpawn(0x3720, 0x068C, 0x99, 0, 0, false, 0)) {
                @Override
                protected PatternSpriteRenderer getRenderer(String key) { return renderer; }
            });
            boss.setServices(services);
        }
    }

    static Object invoke(Object target, String name) throws Exception {
        Method method = HczMinibossInstance.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    static Object invokeRocket(Object boss, String name, Object rocket) throws Exception {
        Method method = HczMinibossInstance.class.getDeclaredMethod(name, rocket.getClass());
        method.setAccessible(true);
        return method.invoke(boss, rocket);
    }

    static Field findField(Object target, String name) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    static Object field(Object target, String name) throws Exception { return findField(target, name).get(target); }
    static void set(Object target, String name, Object value) throws Exception { findField(target, name).set(target, value); }
}
