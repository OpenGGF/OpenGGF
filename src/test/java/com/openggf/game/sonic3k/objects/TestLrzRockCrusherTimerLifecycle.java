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
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        return new Harness(objects, state, rewind, timer);
    }
    private record Harness(ObjectManager objects, LrzZoneRuntimeState state, RewindRegistry rewind,
                           LrzRockCrusherTimerChildInstance timer) { }
}
