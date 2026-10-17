package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizBattleshipInstance;
import com.openggf.game.sonic3k.objects.AizBgTreeSpawnerInstance;
import com.openggf.game.sonic3k.objects.AizBossSmallInstance;
import com.openggf.game.timing.HardwareServiceBoundary;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.HardwareBoundaryPump;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AIZ2 battleship SST ownership through the live AIZ2 events and ObjectManager
 * (aiz_5 slot layout, first divergence rows 2377 and 3950).
 *
 * <ul>
 *   <li>Obj_AIZBattleship init: two AllocateObjectAfterCurrent propellers
 *       (docs/skdisasm/sonic3k.asm:105303-105318).</li>
 *   <li>Obj_AIZBattleshipMain exit: the ship's own SST becomes Obj_AIZ2BossSmall,
 *       Events_fg_4 is set, and Obj_AIZ2MakeTree is allocated after it
 *       (:105327-105339).</li>
 *   <li>AIZ2SE_ShipDraw/EndRefresh: Events_routine_fg holds $C for three
 *       ScreenEvents passes and only then writes the $46C0 loop boundary
 *       (:104978-105017); propellers delete on their first pass at $C
 *       (:105395-105399).</li>
 * </ul>
 *
 * <p>Only types that predate the propeller object are referenced, so the same
 * file is the old-source control for these owners.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAiz2BattleshipSlotOwnership {
    private static final String PROPELLER = "AizBattleshipPropellerInstance";
    private static final int CAMERA_X = 0x4300;
    private static final int CAMERA_Y = 0x0200;
    /** One more $8800 Main step takes _unkEE98 below the $3CDC exit test. */
    private static final int SHIP_X_AT_EXIT_EDGE = 0x3CDC << 16;

    private HeadlessTestFixture fixture;
    private ObjectManager objects;
    private Sonic3kAIZEvents events;
    private Camera camera;
    private int frame;

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        objects = GameServices.level().getObjectManager();
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        events = manager.getAizEvents();
        assertNotNull(events, "AIZ2 events must be live");
        camera = fixture.camera();
        camera.setX((short) CAMERA_X);
        camera.setY((short) CAMERA_Y);
        camera.setMinX((short) CAMERA_X);
        camera.setMaxX((short) CAMERA_X);
        // State after AIZ2SE_ShipRefresh allocated the ship: resize stages done,
        // ship-loop active, Events_routine_fg at ShipDraw.
        events.setAiz2ResizeRoutine(0x10);
        events.setBattleshipSpawned(true);
        events.setBattleshipAutoScrollActiveRaw(true);
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void shipInitAllocatesTwoPropellerSstsAfterItselfThatRunOnThatPass() {
        AizBattleshipInstance ship = addShipAndWarmUp();
        int shipSlot = ship.getSlotIndex();
        TreeSet<Integer> before = new TreeSet<>(occupants().keySet());
        int first = firstFreeAfter(before, shipSlot);
        int second = firstFreeAfter(withSlot(before, first), shipSlot);

        stepObjects();

        Map<Integer, String> after = occupants();
        assertEquals(PROPELLER, after.get(first),
                "the first AllocateObjectAfterCurrent takes the next free SST after the ship");
        assertEquals(PROPELLER, after.get(second),
                "the second AllocateObjectAfterCurrent takes the following free SST");
        assertEquals(2, after.values().stream().filter(PROPELLER::equals).count());
        int shipX = ship.getSecondaryCameraX();
        assertEquals(CAMERA_X + 0x3FBC - shipX, objectAt(first).getX(),
                "the front propeller ran after the ship in the same pass");
        assertEquals(CAMERA_X + 0x3DCC - shipX, objectAt(second).getX(),
                "the back propeller ran after the ship in the same pass");
    }

    @Test
    void leavingShipHandsItsOwnSstToTheSmallCraftAndAllocatesMakeTreeAfterIt() throws Exception {
        AizBattleshipInstance ship = addShipAndWarmUp();
        stepObjects();
        int shipSlot = ship.getSlotIndex();
        setInt(ship, "shipXFixed", SHIP_X_AT_EXIT_EDGE);
        int expectedTreeSlot = firstFreeAfter(new TreeSet<>(occupants().keySet()), shipSlot);

        stepObjects();

        assertTrue(ship.isDestroyed(), "the ship's code pointer is rewritten");
        ObjectInstance craft = objectAt(shipSlot);
        assertTrue(craft instanceof AizBossSmallInstance,
                "move.l #Obj_AIZ2BossSmall,(a0) keeps the ship's SST; found " + describe(craft));
        assertEquals(0, getInt(craft, "frameCounter"),
                "the rewritten SST was already passed, so the craft's init waits for the next pass");
        assertEquals(1, liveCount(AizBossSmallInstance.class));
        ObjectInstance tree = single(AizBgTreeSpawnerInstance.class);
        assertEquals(expectedTreeSlot, ((AbstractObjectInstance) tree).getSlotIndex(),
                "Obj_AIZ2MakeTree is allocated after the ship's SST");

        stepObjects();
        assertEquals(1, getInt(craft, "frameCounter"), "the craft's first pass is the next frame");
    }

    @Test
    void shipExitHoldsEventsRoutineAtEndRefreshForThreeScreenEventPasses() throws Exception {
        AizBattleshipInstance ship = addShipAndWarmUp();
        stepObjects();
        setInt(ship, "shipXFixed", SHIP_X_AT_EXIT_EDGE);

        stepObjects();
        assertTrue(events.isEventsFg4Raw(),
                "Obj_AIZBattleshipMain sets Events_fg_4 as the ship leaves");
        assertEquals(0x4440, events.getBattleshipWrapX(),
                "the ship's exit alone does not move the loop boundary");

        stepEvents();
        assertFalse(events.isEventsFg4Raw(), "AIZ2SE_ShipDraw loc_50008 clears Events_fg_4");
        assertEquals(0x4440, events.getBattleshipWrapX(), "EndRefresh pass 1 of 3");
        stepObjects();
        stepEvents();
        assertEquals(0x4440, events.getBattleshipWrapX(), "EndRefresh pass 2 of 3");
        stepObjects();
        stepEvents();
        assertEquals(0x46C0, events.getBattleshipWrapX(),
                "the third pass completes the redraw and writes Events_bg+$02 = $46C0");
    }

    @Test
    void propellersSurviveTheExitPassAndDeleteOnTheirFirstPassAtEndRefresh() throws Exception {
        AizBattleshipInstance ship = addShipAndWarmUp();
        stepObjects();
        stepEvents();
        setInt(ship, "shipXFixed", SHIP_X_AT_EXIT_EDGE);

        stepObjects();
        assertEquals(2, propellerCount(),
                "Events_routine_fg is still ShipDraw while the exit pass runs the propellers");
        stepEvents();
        assertEquals(2, propellerCount(), "ScreenEvents does not delete objects");

        stepObjects();
        assertEquals(0, propellerCount(), "Events_routine_fg == $C deletes both propellers");
    }

    @Test
    void exitFrameStateRestoresAndReplaysLikeTheUninterruptedRun() throws Exception {
        GameplayModeContext gameplayMode = TestEnvironment.activeGameplayMode();
        assertNotNull(gameplayMode);
        RewindRegistry registry = gameplayMode.getRewindRegistry();

        AizBattleshipInstance ship = addShipAndWarmUp();
        stepObjects();
        stepEvents();
        setInt(ship, "shipXFixed", SHIP_X_AT_EXIT_EDGE);
        stepObjects();
        stepEvents();
        // Captured mid-$C: ship gone, craft in its SST, propellers and MakeTree live.
        Map<Integer, String> capturedOccupants = occupants();
        assertTrue(capturedOccupants.containsValue(PROPELLER));
        CompositeSnapshot snapshot = registry.capture();
        int capturedFrame = frame;

        List<Map<Integer, String>> reference = new java.util.ArrayList<>();
        List<Integer> referenceWrap = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            stepObjects();
            stepEvents();
            reference.add(occupants());
            referenceWrap.add(events.getBattleshipWrapX());
        }

        registry.restore(snapshot);
        frame = capturedFrame;
        assertEquals(capturedOccupants, occupants(),
                "restore recreates every SST occupant in its captured slot");
        assertTrue(events.isBattleshipAutoScrollActiveRaw(),
                "the craft in the ship's SST is a live loop driver; restore must not release the loop");

        for (int i = 0; i < 3; i++) {
            stepObjects();
            stepEvents();
            assertEquals(reference.get(i), occupants(), "replay pass " + i + " occupants");
            assertEquals(referenceWrap.get(i), events.getBattleshipWrapX(), "replay pass " + i + " wrap");
        }
    }

    private AizBattleshipInstance addShipAndWarmUp() {
        int baseSecondaryY = (CAMERA_Y + 0x08F0) & 0x0FF0;
        ObjectSpawn spawn = new ObjectSpawn(CAMERA_X, baseSecondaryY, 0, 0, 0, false, 0);
        AizBattleshipInstance ship = new AizBattleshipInstance(spawn, baseSecondaryY);
        // Placement around the fixed camera settles before the ship is allocated.
        stepObjects();
        stepObjects();
        objects.addDynamicObject(ship);
        // AIZ2SE_ShipRefresh allocates during ScreenEvents: the creation pass is skipped.
        stepObjects();
        assertTrue(objects.getActiveObjects().contains(ship));
        return ship;
    }

    private void stepObjects() {
        frame++;
        objects.update(camera.getX(), null, List.of(), frame, false);
    }

    private void stepEvents() {
        HardwareBoundaryPump.service(HardwareServiceBoundary.VINT_SERVICE);
        HardwareBoundaryPump.service(HardwareServiceBoundary.PRE_MAIN_LOOP);
        events.update(1, frame);
        HardwareBoundaryPump.service(HardwareServiceBoundary.POST_OBJECTS);
    }

    private Map<Integer, String> occupants() {
        Map<Integer, String> slots = new TreeMap<>();
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (object instanceof AbstractObjectInstance instance && !object.isDestroyed()
                    && instance.getSlotIndex() >= 0) {
                slots.put(instance.getSlotIndex(), object.getClass().getSimpleName());
            }
        }
        return slots;
    }

    private ObjectInstance objectAt(int slot) {
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (object instanceof AbstractObjectInstance instance && !object.isDestroyed()
                    && instance.getSlotIndex() == slot) {
                return object;
            }
        }
        return null;
    }

    private long propellerCount() {
        return occupants().values().stream().filter(PROPELLER::equals).count();
    }

    private long liveCount(Class<?> type) {
        return objects.getActiveObjects().stream()
                .filter(o -> type.isInstance(o) && !o.isDestroyed()).count();
    }

    private ObjectInstance single(Class<?> type) {
        List<ObjectInstance> live = objects.getActiveObjects().stream()
                .filter(o -> type.isInstance(o) && !o.isDestroyed())
                .map(o -> (ObjectInstance) o)
                .toList();
        assertEquals(1, live.size(), "expected one live " + type.getSimpleName());
        return live.getFirst();
    }

    private static int firstFreeAfter(TreeSet<Integer> occupied, int slot) {
        int candidate = slot + 1;
        while (occupied.contains(candidate)) {
            candidate++;
        }
        return candidate;
    }

    private static TreeSet<Integer> withSlot(TreeSet<Integer> occupied, int slot) {
        TreeSet<Integer> copy = new TreeSet<>(occupied);
        copy.add(slot);
        return copy;
    }

    private static String describe(ObjectInstance object) {
        return object == null ? "empty" : object.getClass().getSimpleName();
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static int getInt(Object target, String name) throws Exception {
        return field(target, name).getInt(target);
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        field(target, name).setInt(target, value);
    }
}
