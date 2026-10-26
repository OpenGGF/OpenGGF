package com.openggf.game.sonic1.objects.badniks;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.SubpixelMotion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bom_BurnFuseAndExplode (_incObj/5F Badnik - Walking Bomb.asm:171-220):
 * the first fragment replaces the fuse, preserves its position fractions,
 * and falls through to Bom_Shrapnel before the object walk advances.
 */
class TestSonic1BombFuseConversion {
    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void conversionMovesReusedSlotImmediatelyAndReplaysAfterRestore(boolean counterBased) throws Exception {
        Harness harness = newHarness(40, 143, false, counterBased);
        ObjectManager manager = harness.manager();
        // Bom_CheckStartFuse sets 143; its separate Bom_Main pass does not burn it.
        // These are the 143 subsequent Bom_Fuse passes, ending with timer zero.
        for (int frame = 1; frame <= 143; frame++) {
            manager.update(0, null, List.of(), frame);
        }
        assertEquals(104, harness.fuse().getY());
        assertEquals(0xF000, motion(harness.fuse()).ySub,
                "SpeedToPos must retain the 16.16 fraction accumulated by the fuse");

        manager.setRewindInPlaceRestoreEnabledForTest(false);
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        CompositeSnapshot snapshot = registry.capture();

        manager.update(0, null, List.of(), 144);
        List<Sonic1BombShrapnelInstance> fragments = fragments(manager);
        assertEquals(List.of(40, 41, 42, 43), fragments.stream()
                .map(AbstractObjectInstance::getSlotIndex).toList());
        Sonic1BombShrapnelInstance first = fragments.getFirst();
        assertEquals(158, first.getX(), "same-slot Bom_Shrapnel must execute on fuse expiry");
        assertEquals(93, first.getY(), "only the integer Y resets before SpeedToPos");
        assertEquals(0xF000, motion(first).ySub);
        assertEquals(-0x2E8, readInt(first, "yVelocity"));
        for (Sonic1BombShrapnelInstance child : fragments.subList(1, fragments.size())) {
            assertEquals(160, child.getX(), "fresh children run Bom_Main without moving");
            assertEquals(96, child.getY());
            assertEquals(0, motion(child).ySub, "fresh slots do not inherit the fuse fraction");
        }
        // The player slot already passed when the fuse converted. Production
        // snapshots touch state before the next player slot, before any fragment
        // executes again; the reused SST must be eligible at that boundary.
        manager.snapshotTouchResponseState();
        assertTrue(first.isOnScreenForTouch(),
                "the in-place fragment must be touchable before its next own-slot update");
        manager.update(0, null, List.of(), 145);
        assertEquals(91, first.getY(), "retained fraction carries on the next negative step");
        assertEquals(0x0800, motion(first).ySub);
        List<FragmentState> expected = state(manager);

        registry.restore(snapshot);
        assertEquals(1, manager.activeObjectsOfType(Sonic1BombFuseInstance.class).size());
        manager.update(0, null, List.of(), 144);
        manager.snapshotTouchResponseState();
        manager.update(0, null, List.of(), 145);
        assertEquals(expected, state(manager),
                "restore before conversion must reproduce allocation, fractions and forward motion");
    }

    @Test
    void fullTailDoesNotConsumeALowerHoleOrLoseTheInPlaceFragment() {
        Harness harness = newHarness(124, 0, false, true);
        ObjectManager manager = harness.manager();
        manager.reserveAllButNFreeSlots(0);
        manager.releaseDynamicSlot(33);

        manager.update(0, null, List.of(), 1);

        List<Sonic1BombShrapnelInstance> fragments = fragments(manager);
        assertEquals(1, fragments.size(),
                "FindNextFreeObj failure skips children but cannot prevent in-place conversion");
        assertEquals(124, fragments.getFirst().getSlotIndex());
        assertEquals(158, fragments.getFirst().getX());
        assertEquals(93, fragments.getFirst().getY());
        assertEquals(33, manager.allocateDynamicSlot(),
                "a lower hole is unavailable to FindNextFreeObj and must remain free");
    }

    @Test
    void freshChildrenWaitForTheirFirstAnimationAndRestoreAfterConversion() throws Exception {
        Harness harness = newHarness(40, 0, true, true);
        ObjectManager manager = harness.manager();
        manager.update(0, null, List.of(), 1);
        assertEquals(List.of(10, 0, 0, 0), mappingFrames(manager),
                "Bom_Main leaves fresh SST frames clear; only the reused fuse animates");

        manager.setRewindInPlaceRestoreEnabledForTest(false);
        RewindRegistry registry = new RewindRegistry();
        registry.register(manager.rewindSnapshottable());
        CompositeSnapshot converted = registry.capture();
        List<FragmentState> convertedState = state(manager);

        // Ani_Bomb .shrapnel is duration3, A,B,FF. Fresh children first call
        // AnimateSprite on pass2; the FixBugs=0 reused fuse calls it twice on
        // conversion, leaving its A timer at2 rather than the fresh timer3.
        List<List<Integer>> expected = List.of(
                List.of(10, 10, 10, 10), List.of(10, 10, 10, 10),
                List.of(11, 10, 10, 10), List.of(11, 10, 10, 10),
                List.of(11, 11, 11, 11));
        for (int i = 0; i < expected.size(); i++) {
            manager.update(0, null, List.of(), i + 2);
            assertEquals(expected.get(i), mappingFrames(manager));
        }
        List<FragmentState> forward = state(manager);

        registry.restore(converted);
        assertEquals(convertedState, state(manager),
                "post-conversion restore retains cleared frames, fractions and ceiling flips");
        assertEquals(List.of(10, 0, 0, 0), mappingFrames(manager));
        for (int i = 0; i < expected.size(); i++) {
            manager.update(0, null, List.of(), i + 2);
            assertEquals(expected.get(i), mappingFrames(manager));
        }
        assertEquals(forward, state(manager));
    }

    @Test
    void partialTailUsesOnlyForwardSlotsAndFreshChildrenKeepTheirOwnState() throws Exception {
        Harness harness = newHarness(124, 0, true, true);
        ObjectManager manager = harness.manager();
        assertTrue(manager.reserveDynamicSlot(125));
        assertTrue(manager.reserveDynamicSlot(127));

        manager.update(0, null, List.of(), 1);

        List<Sonic1BombShrapnelInstance> fragments = fragments(manager);
        assertEquals(List.of(124, 126), fragments.stream()
                .map(AbstractObjectInstance::getSlotIndex).toList());
        Sonic1BombShrapnelInstance first = fragments.getFirst();
        Sonic1BombShrapnelInstance child = fragments.getLast();
        assertTrue(readBoolean(first, "renderXFlip"));
        assertTrue(readBoolean(first, "renderYFlip"), "the in-place fragment retains fuse status");
        assertFalse(readBoolean(child, "renderXFlip"));
        assertFalse(readBoolean(child, "renderYFlip"), "fresh SSTs start with cleared status");
        assertEquals(-0x100, readInt(child, "xVelocity"));
        assertEquals(-0x200, readInt(child, "yVelocity"));
        assertEquals(33, manager.allocateDynamicSlot());
    }

    private static Harness newHarness(int fuseSlot, int timer, boolean ceilingBomb,
                                      boolean counterBased) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic1ObjectRegistry(),
                0, null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        if (counterBased) {
            manager.enableCounterBasedRespawn();
        }
        manager.reset(0);
        Sonic1BombBadnikInstance bomb = manager.createDynamicObjectAtSlot(
                () -> new Sonic1BombBadnikInstance(
                        new ObjectSpawn(160, 96, 0x5F, 0, 0, false, 0)), 32);
        Sonic1BombFuseInstance fuse = manager.createDynamicObjectAtSlot(
                () -> new Sonic1BombFuseInstance(160, 96, false, ceilingBomb,
                        timer, ceilingBomb ? -0x10 : 0x10, bomb), fuseSlot);
        return new Harness(manager, fuse);
    }

    private static List<Sonic1BombShrapnelInstance> fragments(ObjectManager manager) {
        return manager.activeObjectsOfType(Sonic1BombShrapnelInstance.class).stream()
                .filter(fragment -> !fragment.isDestroyed())
                .sorted(Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }

    private static List<FragmentState> state(ObjectManager manager) {
        return fragments(manager).stream().map(fragment -> {
            try {
                SubpixelMotion.State motion = motion(fragment);
                return new FragmentState(fragment.getSlotIndex(), fragment.getX(), fragment.getY(),
                        motion.xSub, motion.ySub, readInt(fragment, "xVelocity"),
                        readInt(fragment, "yVelocity"), readInt(fragment, "animTickCounter"),
                        readBoolean(fragment, "renderXFlip"), readBoolean(fragment, "renderYFlip"));
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }).toList();
    }

    private static List<Integer> mappingFrames(ObjectManager manager) throws Exception {
        var mapping = Sonic1BombShrapnelInstance.class.getDeclaredMethod("getMappingFrame");
        mapping.setAccessible(true);
        return fragments(manager).stream().map(fragment -> {
            try {
                return (Integer) mapping.invoke(fragment);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }).toList();
    }

    private static SubpixelMotion.State motion(Object object) throws ReflectiveOperationException {
        return (SubpixelMotion.State) field(object, "motionState").get(object);
    }

    private static int readInt(Object object, String name) throws ReflectiveOperationException {
        return field(object, name).getInt(object);
    }

    private static boolean readBoolean(Object object, String name) throws ReflectiveOperationException {
        return field(object, name).getBoolean(object);
    }

    private static Field field(Object object, String name) throws NoSuchFieldException {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private record Harness(ObjectManager manager, Sonic1BombFuseInstance fuse) { }
    private record FragmentState(int slot, int x, int y, int xFraction, int yFraction,
                                 int xVelocity, int yVelocity, int animationTick,
                                 boolean xFlip, boolean yFlip) { }
}
