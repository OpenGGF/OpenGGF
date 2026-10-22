package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GraphicsManager;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Native Obj3E allocation and dispatch, independently of a particular capsule movie. */
class TestEggPrisonNativeAllocation {
    private static final int BODY_SLOT = 40;
    private static final ObjectSpawn SPAWN = new ObjectSpawn(0x180, 0x180, 0x3E, 0, 0, false, 0);

    @BeforeEach
    void initGraphics() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void resetGraphics() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void lowerHolesRemainAvailableWhenEveryLaterSlotIsFull() {
        Harness h = harness(Set.of(16, 20, 35));
        h.step(0);
        assertEquals(List.of(16, 20, 35), componentSlots(h.manager));
        assertFalse(h.manager.hasFreeDynamicSlot(), "Three successful native allocations exhaust the remaining holes");
    }

    @Test
    void mixedHolesAllocateInTableOrderAcrossTheParent() {
        Harness h = harness(Set.of(16, 20, 41));
        h.step(0);
        assertEquals(16, componentSlot(h.manager, "EggPrisonButtonObjectInstance"));
        assertEquals(20, componentSlot(h.manager, "EggPrisonLockSlotChild"));
        assertEquals(41, componentSlot(h.manager, "EggPrisonBrokenSlotChild"));
    }

    @Test
    void occupiedLowerBandUsesLaterSlotsAsTheControl() {
        Harness h = harness(Set.of(41, 42, 43));
        h.step(0);
        assertEquals(List.of(41, 42, 43), componentSlots(h.manager));
        assertFalse(h.manager.hasFreeDynamicSlot());
    }

    @Test
    void fullRamProducesNoLiveOrSlotlessComponents() {
        Harness h = harness(Set.of());
        h.step(0);
        assertEquals(List.of(), componentSlots(h.manager));
        assertEquals(List.of(h.prison), h.manager.getActiveObjects().stream()
                .filter(o -> !o.isDestroyed()).toList());
        assertEquals(-1, h.manager.allocateDynamicSlot());
        h.step(1);
        assertEquals(List.of(), componentSlots(h.manager), "Failed component allocations must not retry next pass");
    }

    @Test
    void lowerLockFirstMovesOnThePassAfterTheBodyArmsIt() throws Exception {
        assertLockArming(Set.of(16, 20, 35), 0);
    }

    @Test
    void laterLockMovesOnTheSamePassTheBodyArmsIt() throws Exception {
        assertLockArming(Set.of(41, 42, 43), 8);
    }

    @Test
    void lowerBrokenPieceKeepsItsFullTimerOnTheArmingPass() throws Exception {
        assertBrokenArming(Set.of(16, 20, 35), 0xB4);
    }

    @Test
    void laterBrokenPieceCountsDownOnTheArmingPass() throws Exception {
        assertBrokenArming(Set.of(41, 42, 43), 0xB3);
    }

    @Test
    void lowerComponentGraphRecreatesAndReplaysTheArmingBoundaryTwice() throws Exception {
        assertGraphReplay(Set.of(16, 20, 35));
    }

    @Test
    void laterComponentGraphRecreatesAndReplaysTheArmingBoundaryTwice() throws Exception {
        assertGraphReplay(Set.of(41, 42, 43));
    }

    private static void assertGraphReplay(Set<Integer> holes) throws Exception {
        Harness h = harness(holes, true);
        h.manager.setRewindInPlaceRestoreEnabledForTest(false);
        h.step(0);
        h.prison.onButtonTriggered();
        for (int frame = 1; frame <= 30; frame++) h.step(frame);
        assertEquals(0, intField(h.prison, "breakDelayTimer"), "Snapshot is immediately before timer underflow");
        assertEquals(0, intField(h.prison, "brokenAnimDuration"));
        RewindRegistry registry = new RewindRegistry();
        registry.register(h.manager.rewindSnapshottable());
        var before = registry.capture();
        h.step(31);
        h.step(32);
        int expectedTimer = intField(h.prison, "brokenAnimDuration");
        int expectedLockX = intField(h.prison, "lockX");
        var expectedSlots = componentSlots(h.manager);

        for (int cycle = 0; cycle < 2; cycle++) {
            // Removing every actual object proves reconstruction and relinking,
            // including children whose lower SST slots precede the parent.
            for (ObjectInstance object : List.copyOf(h.manager.getActiveObjects())) {
                h.manager.removeDynamicObject(object);
            }
            registry.restore(before);
            EggPrisonObjectInstance restored = h.manager.getActiveObjects().stream()
                    .filter(EggPrisonObjectInstance.class::isInstance)
                    .map(EggPrisonObjectInstance.class::cast).findFirst().orElseThrow();
            assertNotSame(h.prison, restored, "Restore must recreate the parent");
            assertEquals(BODY_SLOT, restored.getSlotIndex());
            assertEquals(0, intField(restored, "brokenAnimDuration"));
            h.step(31);
            h.step(32);
            assertEquals(expectedTimer, intField(restored, "brokenAnimDuration"), "Broken timer replay cycle " + cycle);
            assertEquals(expectedLockX, intField(restored, "lockX"), "Lock movement replay cycle " + cycle);
            assertEquals(expectedSlots, componentSlots(h.manager), "No lost or duplicate components on replay");
        }
    }

    private static void assertLockArming(Set<Integer> holes, int firstMove) throws Exception {
        Harness h = harness(holes);
        h.step(0);
        int initialX = intField(h.prison, "lockX");
        h.prison.onButtonTriggered();
        h.step(1);
        assertEquals(initialX + firstMove, intField(h.prison, "lockX"),
                "loc_3F38E executes at the lock's own SST slot after loc_3F2B4 arms it");
        h.step(2);
        assertEquals(initialX + firstMove + 8, intField(h.prison, "lockX"));
    }

    private static void assertBrokenArming(Set<Integer> holes, int firstTimer) throws Exception {
        Harness h = harness(holes);
        h.step(0);
        h.prison.onButtonTriggered();
        h.step(1);
        // loc_3F2B4 seeds $1D. loc_3F2FC returns for 29..0 and arms the
        // broken piece on the thirtieth subsequent body dispatch.
        for (int frame = 2; frame <= 30; frame++) h.step(frame);
        assertEquals(0, intField(h.prison, "brokenAnimDuration"), "Still before native timer underflow");
        h.step(31);
        assertEquals(firstTimer, intField(h.prison, "brokenAnimDuration"),
                "loc_3F3F4 must decrement only when the broken piece's SST slot dispatches");
        h.step(32);
        assertEquals(firstTimer - 1, intField(h.prison, "brokenAnimDuration"));
    }

    private static Harness harness(Set<Integer> holes) {
        return harness(holes, false);
    }

    private static Harness harness(Set<Integer> holes, boolean persistentOccupants) {
        ObjectManager[] holder = new ObjectManager[1];
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x100);
        when(camera.getY()).thenReturn((short) 0x100);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
            @Override public Camera camera() { return camera; }
        };
        ObjectManager manager = new ObjectManager(List.of(), new Sonic2ObjectRegistry(), 0,
                null, null, GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;
        EggPrisonObjectInstance prison = new EggPrisonObjectInstance(SPAWN, "EggPrison");
        manager.addDynamicObjectAtSlot(prison, BODY_SLOT);
        // S2 AllocateObject scans exactly the 112 dynamic SST entries 16..127.
        for (int slot = 16; slot < 128; slot++) {
            if (slot != BODY_SLOT && !holes.contains(slot)) {
                if (persistentOccupants) {
                    // A bare external reservation has no snapshot owner. Real
                    // occupants keep the pressure alive through graph restore.
                    manager.addDynamicObjectAtSlot(new Occupant(new ObjectSpawn(
                            0x100, 0x100, 0, 0, 0, false, 0)), slot);
                } else {
                    manager.reserveDynamicSlot(slot);
                }
            }
        }
        return new Harness(manager, prison,
                new TestablePlayableSprite("sonic", (short) 0, (short) 0));
    }

    private static List<Integer> componentSlots(ObjectManager manager) {
        return manager.getActiveObjects().stream().filter(o -> !o.isDestroyed())
                .filter(o -> o.getSpawn().objectId() == 0x3E && o.getClass() != EggPrisonObjectInstance.class)
                .map(o -> ((AbstractObjectInstance) o).getSlotIndex()).sorted().toList();
    }

    private static int componentSlot(ObjectManager manager, String className) {
        return manager.getActiveObjects().stream().filter(o -> !o.isDestroyed())
                .filter(o -> o.getClass().getSimpleName().equals(className))
                .mapToInt(o -> ((AbstractObjectInstance) o).getSlotIndex()).findFirst().orElseThrow();
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private record Harness(ObjectManager manager, EggPrisonObjectInstance prison,
                           TestablePlayableSprite player) {
        void step(int frame) { manager.update(0x100, player, List.of(), frame); }
    }

    private static final class Occupant extends AbstractObjectInstance implements RewindRecreatable {
        private Occupant(ObjectSpawn spawn) { super(spawn, "Native slot occupant"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
        @Override public Occupant recreateForRewind(RewindRecreateContext ctx) { return new Occupant(ctx.spawn()); }
    }
}
