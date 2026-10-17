package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSstSlotAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Native shot INIT/cleared raw state; component manager replay, not an AIZ cold-route certificate. */
class TestAizMinibossShotNativeInit {
    private static final int SCRIPT = 0x6914C;
    private static final byte[] RAW = {1, 0x0C, 0x0D, (byte) 0xFC};
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest @EnumSource(AizMinibossBarrelShotChild.Mode.class)
    void nativeSharedInitDrawsAtItsBirthAndLeavesTheWaitCounterUnconsumed(AizMinibossBarrelShotChild.Mode mode) throws Exception {
        var h = harness(320);
        var shot = addShot(h, mode);
        var services = spy(h.services);
        shot.setServices(services);
        int x = shot.getX(), y = shot.getY();
        shot.update(0, h.main);
        assertEquals(x, shot.getX());
        assertEquals(y, shot.getY(), "AIZMiniboss_BarrelShot_Init ends at Draw_Sprite, before MoveSprite2");
        assertEquals(0x60, field(shot, "timer"), "Init writes the literal counter without calling Obj_Wait");
        assertEquals(5, shot.getPriorityBucket(), "ObjDat priority $280");
        clearInvocations(h.renderer);
        shot.appendRenderCommands(List.of());
        verify(h.renderer).drawFrameIndex(0x0C, x, y, false, false, 0);
        shot.update(1, h.main);
        assertEquals(y - 4, shot.getY(), "the following MoveWait owns native y_vel=-$400");
        assertEquals(0x5F, field(shot, "timer"));
        verify(services, times(1)).playSfx(Sonic3kSfx.PROJECTILE.id);
    }

    @Test void clearedRawCursorStartsAtDThenRestartsTheRomScript() {
        var h = harness(320);
        var shot = addShot(h, AizMinibossBarrelShotChild.Mode.SIMPLE);
        int[] frames = {0x0C, 0x0D, 0x0D, 0x0C, 0x0C, 0x0D, 0x0D, 0x0C};
        for (int entry = 0; entry < frames.length; entry++) {
            h.step(entry);
            clearInvocations(h.renderer);
            shot.appendRenderCommands(List.of());
            verify(h.renderer).drawFrameIndex(frames[entry], shot.getX(), shot.getY(), false, false, 0);
        }
    }

    @Test void nativeMoveWaitCounterChangesRoutineOnlyOnSignedUnderflow() throws Exception {
        var h = harness(320);
        var shot = addShot(h, AizMinibossBarrelShotChild.Mode.SIMPLE);
        int birthY = shot.getY();
        h.step(0);
        for (int entry = 1; entry <= 0x60; entry++) h.step(entry);
        assertEquals(birthY - 0x60 * 4, shot.getY());
        assertEquals(0, field(shot, "timer"));
        assertEquals("PRELAUNCH", field(shot, "state").toString());
        h.step(0x61);
        assertEquals(birthY - 0x61 * 4, shot.getY());
        assertEquals(8, field(shot, "timer"), "native callback publishes the pause counter on this same movement dispatch");
        assertEquals("PRE_DROP_PRIME", field(shot, "state").toString());
    }

    @Test void pauseAndLaunchKeepTheNativeRawCursorAndDelay() throws Exception {
        var h = harness(320);
        var shot = addShot(h, AizMinibossBarrelShotChild.Mode.SIMPLE);
        for (int entry = 0; entry <= 0x61; entry++) h.step(entry);
        for (int entry = 0x62; entry <= 0x69; entry++) h.step(entry);
        assertEquals("PRE_DROP_PRIME", field(shot, "state").toString());
        h.step(0x6A);
        assertEquals("TOP_DROP_SIMPLE", field(shot, "state").toString());
        h.step(0x6B);
        clearInvocations(h.renderer);
        shot.appendRenderCommands(List.of());
        verify(h.renderer).drawFrameIndex(0x0D, shot.getX(), shot.getY(), false, true, 0);
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void preInitAndMovingRawStateRestoreAndReplayForTwo45EntryCycles(int width) {
        var h = harness(width);
        // Exercise full recreation of the existing fight graph around common shot Init.
        // The independently red cutscene-parent factory is the next bounded owner slice.
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        addShot(h, AizMinibossBarrelShotChild.Mode.SIMPLE);
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        h.step(0);
        var initialized = rewind.capture();
        for (int entry = 1; entry <= 45; entry++) h.step(entry);
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before); same(before, rewind.capture());
            h.step(0); same(initialized, rewind.capture());
            for (int entry = 1; entry <= 45; entry++) h.step(entry);
            same(after, rewind.capture());
            rewind.restore(initialized); same(initialized, rewind.capture());
            for (int entry = 1; entry <= 45; entry++) h.step(entry);
            same(after, rewind.capture());
            rewind.restore(before); same(before, rewind.capture());
        }
    }

    private static Harness harness(int width) {
        var h = new Harness(width);
        var rom = mock(RomByteReader.class);
        when(rom.slice(SCRIPT, RAW.length)).thenReturn(RAW.clone());
        h.services.withRomReader(rom);
        return h;
    }
    private static AizMinibossBarrelShotChild addShot(Harness h,
            AizMinibossBarrelShotChild.Mode mode) {
        return h.manager.createDynamicObjectAtSlot(() -> new AizMinibossBarrelShotChild(
                h.boss(), h.barrel(7), h.barrel(7).getX(), h.barrel(7).getY() + 4, mode), 9);
    }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }

    public static final class FrozenFightBoss extends AizMinibossInstance {
        public FrozenFightBoss(com.openggf.level.objects.ObjectSpawn spawn) {
            super(spawn);
            getState().x = spawn.x(); getState().y = spawn.y();
            getState().xFixed = spawn.x() << 16; getState().yFixed = spawn.y() << 16;
        }
        @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {
            getState().lastUpdatedVIntRunCount = vIntRunCount;
        }
        @Override public FrozenFightBoss recreateForRewind(com.openggf.level.objects.RewindRecreateContext ctx) {
            return new FrozenFightBoss(ctx.spawn());
        }
        @Override public boolean isPersistent() { return true; }
    }
    static final class Harness {
        final com.openggf.camera.Camera camera = mock(com.openggf.camera.Camera.class);
        final com.openggf.tests.TestablePlayableSprite main = new com.openggf.tests.TestablePlayableSprite(
                "sonic", (short) 0x3000, (short) 0x350);
        final com.openggf.level.render.PatternSpriteRenderer renderer = mock(com.openggf.level.render.PatternSpriteRenderer.class);
        final com.openggf.level.objects.TestObjectServices services;
        final com.openggf.level.objects.ObjectManager manager;
        Harness(int width) {
            main.setCentreX((short) 0x3000); main.setCentreY((short) 0x350);
            when(camera.getX()).thenReturn((short) 0x2F00);
            when(camera.getXCopy()).thenReturn((short) 0x2F00);
            when(camera.getY()).thenReturn((short) 0x300);
            when(camera.getYCopy()).thenReturn((short) 0x300);
            when(camera.getWidth()).thenReturn((short) width);
            when(camera.getHeight()).thenReturn((short) 224);
            when(camera.getFocusedSprite()).thenReturn(main);
            var render = mock(com.openggf.level.objects.ObjectRenderManager.class);
            when(render.getRenderer(com.openggf.game.sonic3k.Sonic3kObjectArtKeys.AIZ_MINIBOSS)).thenReturn(renderer);
            when(renderer.isReady()).thenReturn(true);
            services = new com.openggf.level.objects.TestObjectServices() {
                @Override public com.openggf.level.objects.ObjectRenderManager renderManager() { return render; }
            }.withCamera(camera).withGameModule(new com.openggf.game.sonic3k.Sonic3kGameModule())
                    .withGraphicsManager(GraphicsManager.getInstance());
            manager = new com.openggf.level.objects.ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                    GraphicsManager.getInstance(), camera, services);
            services.withDirectObjectManager(manager);
            manager.reset(0x2F00);
            manager.createDynamicObjectAtSlot(() -> new FrozenFightBoss(
                    new com.openggf.level.objects.ObjectSpawn(0x3020, 0x34C, 0x91, 0, 0, false, 0)), 6);
            for (int slot = 7; slot <= 8; slot++) {
                int index = slot - 7;
                var barrel = manager.createDynamicObjectAtSlot(() -> new AizMinibossFlameBarrelChild(boss(), index, false), slot);
            }
        }
        FrozenFightBoss boss() { return (FrozenFightBoss) NativeSstSlotAccess.currentOwner(manager, 6); }
        AizMinibossFlameBarrelChild barrel(int slot) {
            return (AizMinibossFlameBarrelChild) NativeSstSlotAccess.currentOwner(manager, slot);
        }
        void step(int frame) { manager.update(0x2F00, main, List.of(), frame); manager.refreshPostCameraRenderState(); }
    }
}
