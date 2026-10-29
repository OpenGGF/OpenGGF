package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native loc_90502 -> loc_90512 initialization, underflow, allocation and recreation edges. */
class TestLrzRockCrusherTimerLifecycle {
    @BeforeEach void headless() {
        GraphicsManager.getInstance().initHeadless();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x4000, 0x4000, 0);
    }
    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void expiryKeepsTheNativeBridgeAllocationPrefixWhenSlotsRunOut(int freeSlots) {
        var h = harness(0);
        var fillers = new ArrayList<LrzSinkingRockObjectInstance>();
        for (int i = 0; i < 256; i++) {
            var filler = h.objects.createDynamicObject(() -> new LrzSinkingRockObjectInstance(
                    new ObjectSpawn(0x2000, 0x2000, 0x17, 0, 0, false, 0)));
            if (filler == null) break;
            fillers.add(filler);
        }
        assertNull(h.objects.createDynamicObject(() -> new LrzSinkingRockObjectInstance(
                new ObjectSpawn(0x2000, 0x2000, 0x17, 0, 0, false, 0))), "capacity is actually exhausted");
        assertTrue(fillers.size() >= freeSlots);
        for (int i = 0; i < freeSlots; i++) h.objects.removeDynamicObject(fillers.get(i));
        run(h.timer, 1, 179);
        assertEquals(0, h.timer.countdown(), "zero does not fire; bpl retains it");
        assertTrue(bridges(h).isEmpty());
        h.timer.update(180, null);
        assertTrue(h.timer.isDestroyed());
        assertEquals(-1, (short) h.state.chunkEditRequest(), "world request does not depend on allocation success");
        var bridges = bridges(h);
        assertEquals(freeSlots, bridges.size());
        for (int i = 0; i < freeSlots; i++) {
            assertEquals(0xF00 + i * 0x80, bridges.get(i).getX());
            assertEquals(0x760, bridges.get(i).getY());
            assertTrue(bridges.get(i).isArmedForTest());
        }
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,1", "0,179", "2,0", "2,1", "2,179"})
    void recreationReplaysInitializationAndTheExactExpiryAllocationGraph(int subtype, int before) {
        var h = harness(subtype);
        run(h.timer, 1, before);
        var saved = h.rewind.capture();
        run(h.timer, before + 1, 180);
        assertTrue(h.timer.isDestroyed());
        assertEquals(subtype == 0 ? 2 : 1, bridges(h).size());
        var expected = h.rewind.capture();
        h.rewind.restore(saved);
        same(saved, h.rewind.capture());
        var restored = h.objects.getActiveObjects().stream()
                .filter(LrzRockCrusherTimerChildInstance.class::isInstance)
                .map(LrzRockCrusherTimerChildInstance.class::cast).filter(o -> !o.isDestroyed()).toList();
        assertEquals(1, restored.size());
        assertNotSame(h.timer, restored.getFirst(), "exercise recreation, including the captured armed flag");
        run(restored.getFirst(), before + 1, 180);
        same(expected, h.rewind.capture());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 5})
    void nonzeroSubtypeRestoresCameraAndAllocatesTheAvailablePrefix(int freeSlots) {
        var h = harness(2);
        var fillers = new ArrayList<LrzSinkingRockObjectInstance>();
        while (true) {
            var filler = h.objects.createDynamicObject(() -> new LrzSinkingRockObjectInstance(
                    new ObjectSpawn(0x2000, 0x2000, 0x17, 0, 0, false, 0)));
            if (filler == null) break;
            fillers.add(filler);
        }
        assertTrue(fillers.size() >= freeSlots);
        for (int i = 0; i < freeSlots; i++) h.objects.removeDynamicObject(fillers.get(i));
        run(h.timer, 1, 180);
        assertTrue(h.timer.isDestroyed());
        assertEquals(0xFF, h.state.chunkEditRequest() & 0xFFFF);
        assertEquals(-1, h.state.screenShake().flag(), "nonzero subtype retains shake");
        assertEquals(0x900, h.camera.getMaxYTarget() & 0xFFFF,
                "camera target restores even if allocation fails");
        var bridges = bridges(h);
        assertEquals(freeSlots == 0 ? 0 : 1, bridges.size());
        if (!bridges.isEmpty()) {
            assertEquals(0x540, bridges.getFirst().getX());
            assertEquals(0x860, bridges.getFirst().getY());
            assertTrue(bridges.getFirst().isArmedForTest());
        }
        var workers = h.objects.getActiveObjects().stream()
                .filter(S3kCameraGradualObjectInstance.class::isInstance)
                .map(S3kCameraGradualObjectInstance.class::cast)
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex))
                .toList();
        int[] kinds = LrzRockCrusherObjectInstance.changeLevSizeKinds();
        assertEquals(Math.max(0, freeSlots - 1), workers.size());
        for (int i = 0; i < workers.size(); i++) {
            assertEquals(kinds[i], workers.get(i).getSpawn().subtype(), "native allocation prefix");
        }
    }

    private enum AllocationOrder { LOWER_SLOT, HIGHER_SLOT }

    @ParameterizedTest
    @EnumSource(AllocationOrder.class)
    void managerDispatchesBySlotAndReplaysRetirementAndReuse(AllocationOrder order) {
        var h = harness(0);
        h.objects.removeDynamicObject(h.timer);
        if (order == AllocationOrder.HIGHER_SLOT) {
            for (int slot = 4; slot < 10; slot++) {
                assertNotNull(h.objects.createDynamicObjectAtSlot(Noise::new, slot));
            }
        }
        var spawner = h.objects.createDynamicObjectAtSlot(TimerSpawner::new, 10);
        assertNotNull(spawner);
        h.objects.update(0, null, List.of(), 1);
        var timer = onlyTimer(h);
        int timerSlot = timer.getSlotIndex();
        assertEquals(order == AllocationOrder.HIGHER_SLOT ? 11 : 4, timerSlot);
        if (order == AllocationOrder.HIGHER_SLOT) {
            assertEquals(178, timer.countdown(), "later slot initializes during the same pass");
        } else {
            assertEquals(0, timer.countdown(), "earlier slot waits for the next pass");
            h.objects.update(0, null, List.of(), 2);
            assertEquals(178, timer.countdown());
        }
        for (int frame = 0; frame < 178; frame++) h.objects.update(0, null, List.of(), 3 + frame);
        assertEquals(0, onlyTimer(h).countdown());
        var beforeExpiry = h.rewind.capture();
        h.objects.update(0, null, List.of(), 181);
        assertTrue(h.objects.activeObjectsOfType(LrzRockCrusherTimerChildInstance.class).isEmpty(),
                "manager removes the expired timer, rather than retaining a destroyed slot");
        assertNotNull(h.objects.createDynamicObjectAtSlot(Noise::new, timerSlot), "retired slot is reusable");
        var afterExpiry = h.rewind.capture();
        h.rewind.restore(beforeExpiry);
        same(beforeExpiry, h.rewind.capture());
        h.objects.update(0, null, List.of(), 181);
        assertTrue(h.objects.activeObjectsOfType(LrzRockCrusherTimerChildInstance.class).isEmpty());
        assertNotNull(h.objects.createDynamicObjectAtSlot(Noise::new, timerSlot));
        same(afterExpiry, h.rewind.capture());
    }

    private static LrzRockCrusherTimerChildInstance onlyTimer(Harness h) {
        var timers = h.objects.activeObjectsOfType(LrzRockCrusherTimerChildInstance.class);
        assertEquals(1, timers.size());
        return timers.getFirst();
    }

    private static final class TimerSpawner extends AbstractObjectInstance implements RewindRecreatable {
        private boolean spawned;
        private TimerSpawner() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "timer spawner"); }
        @Override public TimerSpawner recreateForRewind(RewindRecreateContext ctx) { return new TimerSpawner(); }
        @Override public boolean isPersistent() { return true; }
        @Override public void update(int clock, com.openggf.game.PlayableEntity player) {
            if (!spawned) {
                spawned = true;
                spawnFreeChild(() -> new LrzRockCrusherTimerChildInstance(0));
            }
        }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static final class Noise extends AbstractObjectInstance implements RewindRecreatable {
        private Noise() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "slot occupant"); }
        @Override public Noise recreateForRewind(RewindRecreateContext ctx) { return new Noise(); }
        @Override public boolean isPersistent() { return true; }
        @Override public void update(int clock, com.openggf.game.PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    private static void same(CompositeSnapshot expected, CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (var key : expected.entries().keySet()) {
            var differences = RewindSnapshotDiff.diffKey(key, expected.entries().get(key), actual.entries().get(key));
            assertTrue(differences.isEmpty(), differences.toString());
        }
    }
    private static void run(LrzRockCrusherTimerChildInstance timer, int first, int last) {
        for (int i = first; i <= last; i++) timer.update(i, null);
    }
    private static List<LrzCollapsingBridgeInstance> bridges(Harness h) {
        return h.objects.getActiveObjects().stream().filter(LrzCollapsingBridgeInstance.class::isInstance)
                .map(LrzCollapsingBridgeInstance.class::cast).filter(o -> !o.isDestroyed())
                .sorted(java.util.Comparator.comparingInt(AbstractObjectInstance::getSlotIndex)).toList();
    }
    private static Harness harness(int subtype) {
        var camera = new Camera() {
            @Override public short getWidth() { return 0x4000; }
            @Override public short getHeight() { return 0x4000; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
        var state = new LrzZoneRuntimeState(9, 0, PlayerCharacter.KNUCKLES);
        camera.setMaxYTarget((short) 0xB00);
        state.storeCameraBounds(0, 0x4000, 0, 0x900);
        var zones = new ZoneRuntimeRegistry(); zones.install(state);
        var services = new TestObjectServices().withCamera(camera).withGraphicsManager(GraphicsManager.getInstance()).withZoneRuntimeRegistry(zones);
        var objects = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        services.withDirectObjectManager(objects);
        objects.reset(0);
        objects.setRewindInPlaceRestoreEnabledForTest(false);
        var rewind = new RewindRegistry();
        rewind.register(objects.rewindSnapshottable()); rewind.register(zones); rewind.register(camera);
        var timer = objects.createDynamicObject(() -> new LrzRockCrusherTimerChildInstance(subtype));
        assertNotNull(timer);
        return new Harness(objects, state, rewind, timer, camera);
    }
    private record Harness(ObjectManager objects, LrzZoneRuntimeState state, RewindRegistry rewind,
                           LrzRockCrusherTimerChildInstance timer, Camera camera) { }
}
