package com.openggf.game.sonic3k.objects;

import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestAizTransitionFloorObjectInstance {

    @org.junit.jupiter.api.AfterEach
    void resetCamera() {
        com.openggf.level.objects.AbstractObjectInstance.resetCameraBoundsForTests();
        com.openggf.graphics.GraphicsManager.getInstance().resetState();
    }

    @Test
    void nativeStatusBitSevenSuppressesObjectEdgeBalanceEvenAfterPlayerRebase() {
        var floor = new AizTransitionFloorObjectInstance();
        assertTrue(floor.suppressesObjectEdgeBalance(),
                "Obj_AIZTransitionFloor bset#7,status; Sonic_Move bmi bypasses the edge branch");
        assertEquals(0, floor.getBalanceWidthPixels(), "the cleared native width byte is never written");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {320,352,400,528,800})
    void actTwoKeepsTheSentinelSolidTailUntilNextPhysicalClearAndReplaysTwice(int width) {
        com.openggf.graphics.GraphicsManager.getInstance().initHeadless();
        var h = new TestAizMinibossFlareNativeGraph.Harness(width);
        var level = mock(com.openggf.level.LevelManager.class);
        when(level.getCurrentAct()).thenReturn(1);
        h.services.withLevelManager(level);
        when(h.camera.getX()).thenReturn((short) 0x10);
        when(h.camera.getXCopy()).thenReturn((short) 0x10);
        for (var object : java.util.List.copyOf(h.manager.getActiveObjects())) h.manager.removeDynamicObject(object);
        var floor = h.manager.createDynamicObjectAtSlot(AizTransitionFloorObjectInstance::new, 17);
        var spawn = floor.getSpawn();
        var rewind = h.manager.rewindSnapshottable();
        var before = rewind.capture();
        h.manager.update(0x10, h.main, java.util.List.of(), 1);
        var marked = rewind.capture();
        assertSame(floor, com.openggf.level.objects.NativeSstSlotAccess.currentOwner(h.manager, 17));
        assertFalse(floor.isDestroyed(), "Act2 swaps to Delete_Current_Sprite; its solid tail still executes");
        assertEquals(0x7FFF, floor.getX());
        assertEquals(0x3A0, floor.getY());
        assertEquals(1, floor.romObjectCodePointerHighWord(), "retained code is Delete_Current_Sprite");
        assertFalse(floor.isSkipSolidContactThisFrame());
        assertSame(spawn, floor.getSpawn(), "native X word changes, immutable birth placement stays captured");
        h.manager.update(0x10, h.main, java.util.List.of(), 2);
        assertNull(com.openggf.level.objects.NativeSstSlotAccess.currentOwner(h.manager, 17));
        var cleared = rewind.capture();
        for (int cycle = 0; cycle < 2; cycle++) {
            rewind.restore(before); same(before, rewind.capture());
            h.manager.update(0x10, h.main, java.util.List.of(), 1); same(marked, rewind.capture());
            rewind.restore(marked); same(marked, rewind.capture());
            h.manager.update(0x10, h.main, java.util.List.of(), 2); same(cleared, rewind.capture());
            rewind.restore(cleared); same(cleared, rewind.capture());
        }
    }

    private static void same(Object expected, Object actual) {
        var diffs = com.openggf.game.rewind.RewindSnapshotDiff.diffKey("object-manager", expected, actual);
        assertTrue(diffs.isEmpty(), diffs.toString());
    }

    @Test
    void highFixedPointPhaseAcceptsAfterTwentyZeroDistancePasses() {
        AizTransitionFloorObjectInstance floor = new AizTransitionFloorObjectInstance();
        TestablePlayableSprite player = playerWithYSubpixel(0xF700);

        rejectZeroDistancePasses(floor, player, 20);

        assertFalse(floor.rejectsZeroDistanceTopSolidLanding(player));
    }

    @Test
    void lowFixedPointPhaseAcceptsAfterTwentyOneZeroDistancePasses() {
        AizTransitionFloorObjectInstance floor = new AizTransitionFloorObjectInstance();
        TestablePlayableSprite player = playerWithYSubpixel(0x0100);

        rejectZeroDistancePasses(floor, player, 20);
        assertTrue(floor.rejectsZeroDistanceTopSolidLanding(player));

        floor.onRejectedZeroDistanceTopSolidLanding(player);
        assertFalse(floor.rejectsZeroDistanceTopSolidLanding(player));
    }

    private static TestablePlayableSprite playerWithYSubpixel(int ySubpixel) {
        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setSubpixelRaw(0, ySubpixel);
        return player;
    }

    private static void rejectZeroDistancePasses(AizTransitionFloorObjectInstance floor,
                                                 TestablePlayableSprite player,
                                                 int count) {
        for (int i = 0; i < count; i++) {
            assertTrue(floor.rejectsZeroDistanceTopSolidLanding(player));
            floor.onRejectedZeroDistanceTopSolidLanding(player);
        }
    }
}
