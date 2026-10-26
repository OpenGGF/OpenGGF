package com.openggf.game.sonic3k.objects;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.*;
import com.openggf.level.objects.boss.AbstractBossInstance;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Live native parent3 graph recreation; no recycled-SST pressure or cold-route certification. */
class TestAizMinibossShotNativeGraph {
    @BeforeEach void init() { GraphicsManager.getInstance().initHeadless(); }
    @AfterEach void reset() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest @EnumSource(AizMinibossBarrelShotChild.Mode.class)
    void phaseOneOnlyNeedsTheCapturedSpawnShell(AizMinibossBarrelShotChild.Mode mode) {
        var h = harness(320);
        var shot = addShot(h, 9, h.boss(), h.barrel(7), mode);
        var spawn = new ObjectSpawn(0x3071, 0x2A7, 0x90, 4, 3, false, 0x42A7);
        var recreated = shot.recreateForRewind(new RewindRecreateContext(spawn, null, h.services));
        assertNotNull(recreated, "phase one must not require or select a live boss/barrel");
        assertEquals(spawn, recreated.getSpawn(), "retain every captured spawn field before phase-two links");
    }

    @ParameterizedTest @ValueSource(ints = {320, 352, 400, 528, 800})
    void movingCutsceneShotRecreatesAndReplaysTwo45EntryCycles(int width) throws Exception {
        var h = harness(width);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        addShot(h, 9, h.boss(), h.barrel(7), AizMinibossBarrelShotChild.Mode.SIMPLE);
        for (int frame = 0; frame <= 6; frame++) { h.moveBoss(frame); h.step(frame); }
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        ObjectSpawn movingSpawn = shot(h, 9).getSpawn();
        for (int frame = 7; frame <= 51; frame++) { h.moveBoss(frame); h.step(frame); }
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before); same(before, rewind.capture());
            assertNotNull(shot(h, 9), "real cutscene-owned shot must survive full graph recreation");
            assertEquals(movingSpawn, shot(h, 9).getSpawn());
            assertSame(h.boss(), field(shot(h, 9), "parent"));
            assertSame(h.barrel(7), field(shot(h, 9), "barrel"));
            for (int frame = 7; frame <= 51; frame++) { h.moveBoss(frame); h.step(frame); }
            same(after, rewind.capture());
            rewind.restore(after); same(after, rewind.capture());
            rewind.restore(before); same(before, rewind.capture());
        }
    }

    @Test void twoParentsAndThreeBarrelsKeepExactIdentityAfterShotMovesNearTheOtherGraph() throws Exception {
        var h = harness(320);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        var fight = h.manager.createDynamicObjectAtSlot(() -> new TestAizMinibossShotNativeInit.FrozenFightBoss(
                new ObjectSpawn(0x3100, 0x34C, 0x91, 0, 0, false, 0)), 11);
        var fightBarrel = h.manager.createDynamicObjectAtSlot(() -> new AizMinibossFlameBarrelChild(fight, 2, false), 12);
        var cutsceneShot = addShot(h, 9, h.boss(), h.barrel(8), AizMinibossBarrelShotChild.Mode.ADVANCED_NON_COLLIDING);
        addShot(h, 10, fight, fightBarrel, AizMinibossBarrelShotChild.Mode.ADVANCED_COLLIDING);
        for (int frame = 0; frame <= 6; frame++) h.step(frame);
        // Native SetShotPosition can detach shot XY from its parent3 barrel's location.
        // This declared checkpoint makes nearest-parent selection observably wrong.
        setField(cutsceneShot, "currentX", fightBarrel.getX());
        setField(cutsceneShot, "xFixed", fightBarrel.getX() << 16);
        var rewind = h.manager.rewindSnapshottable();
        var captured = rewind.capture();
        ObjectSpawn spawn = cutsceneShot.getSpawn();
        rewind.restore(captured); same(captured, rewind.capture());
        assertEquals(spawn, shot(h, 9).getSpawn());
        assertSame(h.boss(), field(shot(h, 9), "parent"));
        assertSame(h.barrel(8), field(shot(h, 9), "barrel"));
        assertSame(NativeSstSlotAccess.currentOwner(h.manager, 11), field(shot(h, 10), "parent"));
        assertSame(NativeSstSlotAccess.currentOwner(h.manager, 12), field(shot(h, 10), "barrel"));
        for (int frame = 7; frame <= 51; frame++) h.step(frame);
        var after = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(captured); same(captured, rewind.capture());
            for (int frame = 7; frame <= 51; frame++) h.step(frame);
            same(after, rewind.capture());
            rewind.restore(after); same(after, rewind.capture());
        }
    }

    private static TestAizMinibossFlareNativeGraph.Harness harness(int width) {
        var h = new TestAizMinibossFlareNativeGraph.Harness(width);
        var rom = mock(RomByteReader.class);
        when(rom.slice(0x6914C, 4)).thenReturn(new byte[]{1, 0x0C, 0x0D, (byte)0xFC});
        h.services.withRomReader(rom);
        return h;
    }
    private static AizMinibossBarrelShotChild addShot(TestAizMinibossFlareNativeGraph.Harness h, int slot,
            AbstractBossInstance boss, AizMinibossFlameBarrelChild barrel, AizMinibossBarrelShotChild.Mode mode) {
        return h.manager.createDynamicObjectAtSlot(() -> new AizMinibossBarrelShotChild(
                boss, barrel, barrel.getX(), barrel.getY() + 4, mode), slot);
    }
    private static AizMinibossBarrelShotChild shot(TestAizMinibossFlareNativeGraph.Harness h, int slot) {
        var owner = NativeSstSlotAccess.currentOwner(h.manager, slot);
        return owner instanceof AizMinibossBarrelShotChild shot ? shot : null;
    }
    private static Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private static void setField(Object object, String name, Object value) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value);
    }
    private static void same(Object expected, Object actual) {
        var diff = RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diff.isEmpty(), () -> diff.toString());
    }
}
