package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A PLACED record (layoutIndex >= 0) whose owner the manager rejects (here: a dynamic,
 * unmanaged instance carrying a placed record's spawn) must fail the frost-puff reservation
 * closed (-1, no allocator mutation), never fall back to a raw reservation. A non-placed
 * (layoutIndex < 0) fixture keeps the legacy raw chain.
 */
class TestIczEndBossPlacedRejectionFailsClosed {
    private ObjectManager manager;

    @BeforeEach void setup() {
        GraphicsManager.getInstance().initHeadless();
        var player = new TestablePlayableSprite("sonic", (short) 0x4400, (short) 0x0420);
        Camera camera = new Camera() {
            @Override public short getX() { return 0x4390; }
            @Override public short getY() { return 0x05F8; }
            @Override public short getWidth() { return 320; }
            @Override public short getHeight() { return 224; }
        };
        ObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return manager; }
            @Override public Camera camera() { return camera; }
            @Override public GameStateManager gameState() { return new GameStateManager(); }
            @Override public ObjectPlayerQuery playerQuery() { return new ObjectPlayerQuery(() -> player, List::of); }
        };
        manager = new ObjectManager(List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        AbstractObjectInstance.updateCameraBounds(0x4300, 0x0500, 0x4700, 0x0700, 0);
    }

    @AfterEach void cleanup() {
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    private static Set<Integer> occupied(ObjectManager m) {
        return new TreeSet<>(m.occupiedDynamicSlotIdsWithReservations().keySet());
    }

    /** Runs the owner's real puff creation for selector 0 (seven puffs) and returns their slots. */
    private static List<Integer> createPuffs(IczEndBossInstance boss) throws Exception {
        Class<?> anchor = Class.forName(IczEndBossInstance.class.getName() + "$EffectAnchor");
        Method m = IczEndBossInstance.class.getDeclaredMethod("createFrostPuffsForSelector", int.class, anchor);
        m.setAccessible(true);
        m.invoke(boss, 0, Enum.valueOf((Class) anchor, "PARENT"));
        Field f = IczEndBossInstance.class.getDeclaredField("effectChildren");
        f.setAccessible(true);
        List<?> children = (List<?>) f.get(boss);
        return children.stream().map(c -> {
            try {
                Field slot = c.getClass().getDeclaredField("nativeSlot");
                slot.setAccessible(true);
                return (Integer) slot.get(c);
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        }).toList();
    }

    @Test void placedRecordWithAnUnmanagedOwnerFailsClosedWithoutAllocatorMutation() throws Exception {
        // Dynamic (unmanaged) instance carrying a placed record's spawn: layoutIndex 340 >= 0.
        var boss = manager.createDynamicObject(() -> new IczEndBossInstance(
                new ObjectSpawn(0x4490, 0x05B8, Sonic3kObjectIds.ICZ_END_BOSS, 0, 0, false, 0x05B8, 340, null, null)));
        assertTrue(boss.getSlotIndex() >= 0);
        Set<Integer> before = occupied(manager);
        List<Integer> slots = createPuffs(boss);
        assertEquals(7, slots.size(), "puffs are still created: the allocation failed, not the effect");
        assertTrue(slots.stream().allMatch(s -> s == -1), "native allocation failure for every puff: " + slots);
        assertEquals(before, occupied(manager), "no raw reservation, no allocator mutation");
    }

    @Test void nonPlacedFixtureKeepsTheLegacyRawChain() throws Exception {
        var boss = manager.createDynamicObject(() -> new IczEndBossInstance(
                new ObjectSpawn(0x4490, 0x05B8, Sonic3kObjectIds.ICZ_END_BOSS, 0, 0, false, 0x05B8)));
        Set<Integer> before = occupied(manager);
        List<Integer> slots = createPuffs(boss);
        assertEquals(7, slots.size());
        assertTrue(slots.stream().allMatch(s -> s >= 0), "legacy raw chain allocates real slots: " + slots);
        Set<Integer> after = occupied(manager);
        assertTrue(after.containsAll(slots) && after.size() > before.size(), "raw reservations hold the slots");
    }
}
