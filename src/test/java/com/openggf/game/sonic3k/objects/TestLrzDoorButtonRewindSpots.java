package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerManager;
import com.openggf.game.sonic3k.Sonic3kLevelTriggerStaticAdapter;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.physics.TrigLookupTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rewind spots for slice 3b's two stateful mechanics: {@code Obj_LRZDoor}'s one-way opening
 * (sonic3k.asm:88032-88054) and {@code Obj_LRZButtonHorizontal}'s per-frame trigger write
 * (:88236-88273).
 *
 * <p>Each spot is captured before, during and after the event, and every restore is followed by a
 * forward replay: the same number of updates after a restore has to land on exactly the state an
 * uninterrupted timeline reached. That is what catches a door whose {@code $2E} restores but whose
 * routine stage does not, and a button whose latch restores while the shared
 * {@code Level_trigger_array} does not.
 *
 * <p>The trigger array is static state, so {@link Sonic3kLevelTriggerStaticAdapter} is registered
 * alongside the object manager exactly as {@code Sonic3kLevelEventManager} does in production.
 */
class TestLrzDoorButtonRewindSpots {

    private static final int DOOR_X = 0x0490;
    private static final int DOOR_Y = 0x0500;
    private static final int TRIGGER_INDEX = 0x04;
    private static final ObjectSpawn DOOR_SPAWN = new ObjectSpawn(
            DOOR_X, DOOR_Y, Sonic3kObjectIds.LBZ_CUP_ELEVATOR_POLE, TRIGGER_INDEX, 0, false, 41);
    private static final ObjectSpawn BUTTON_SPAWN = new ObjectSpawn(
            0x0445, 0x04D4, Sonic3kObjectIds.LRZ_BUTTON_HORIZONTAL, TRIGGER_INDEX, 0, false, 42);

    /** The door's own clock: {@code $2E} counts to {@code $40} and stops. */
    private static final int OPEN_ANGLE = 0x40;

    @BeforeEach
    void initHeadless() {
        GraphicsManager.getInstance().initHeadless();
        Sonic3kLevelTriggerManager.reset();
        AbstractObjectInstance.updateCameraBounds(0, 0, 0x800, 0x800, 0);
    }

    @AfterEach
    void tearDown() {
        Sonic3kLevelTriggerManager.reset();
        AbstractObjectInstance.resetCameraBoundsForTests();
        GraphicsManager.getInstance().resetState();
    }

    /**
     * BEFORE the opening: the door is captured shut, the timeline is allowed to open it, and the
     * restore has to put it back to shut - including the routine stage, or the replayed frames
     * would start from the wrong branch.
     */
    @Test
    void doorRewindSpotBeforeTheTriggerIsWritten() {
        Harness harness = Harness.create();
        LrzDoorObjectInstance door = harness.door();

        door.update(0, null);
        assertEquals(0, door.openTimer(), "precondition: the door is shut");

        CompositeSnapshot before = harness.capture();

        Sonic3kLevelTriggerManager.setBit(TRIGGER_INDEX, 0);
        for (int frame = 0; frame < 10; frame++) {
            door.update(frame, null);
        }
        assertEquals(10, door.openTimer(), "the diverging timeline opened the door 10 frames");

        harness.restore(before);
        LrzDoorObjectInstance restored = harness.onlyDoor();
        assertNotSame(door, restored, "restore recreates the door");
        assertEquals(0, restored.openTimer(), "$2E restores to shut");
        assertFalse(restored.isOpening(), "the routine stage restores to the waiting branch");
        assertEquals(DOOR_Y, restored.getCentreY(), "y restores to the placement");
        assertFalse(Sonic3kLevelTriggerManager.testAny(TRIGGER_INDEX),
                "the shared trigger array restores with the door");

        // Forward replay: with the array restored to zero the door must stay shut, which is only
        // true if both the object and the static array came back.
        for (int frame = 0; frame < 10; frame++) {
            restored.update(frame, null);
        }
        assertEquals(0, restored.openTimer(), "a restored-shut door does not resume opening");
    }

    /**
     * DURING the opening: captured mid-travel, diverged to the end, restored, then replayed the
     * same number of frames the uninterrupted run took.
     */
    @Test
    void doorRewindSpotMidOpeningReplaysToTheSameState() {
        Harness harness = Harness.create();
        LrzDoorObjectInstance door = harness.door();
        Sonic3kLevelTriggerManager.setBit(TRIGGER_INDEX, 0);

        for (int frame = 0; frame < 20; frame++) {
            door.update(frame, null);
        }
        assertEquals(20, door.openTimer(), "precondition: mid-travel");
        assertTrue(door.isOpening());

        CompositeSnapshot midway = harness.capture();

        for (int frame = 20; frame < OPEN_ANGLE; frame++) {
            door.update(frame, null);
        }
        assertTrue(door.isFullyOpen(), "the diverging timeline finished the door");
        int uninterruptedY = door.getCentreY();

        harness.restore(midway);
        LrzDoorObjectInstance restored = harness.onlyDoor();
        assertEquals(20, restored.openTimer(), "$2E restores mid-travel");
        assertTrue(restored.isOpening(), "the opening routine stage restores");
        assertEquals(DOOR_Y - (TrigLookupTable.sinHex(20) >> 2), restored.getCentreY(),
                "y restores to the sine step for $2E = 20");

        for (int frame = 20; frame < OPEN_ANGLE; frame++) {
            restored.update(frame, null);
        }
        assertTrue(restored.isFullyOpen(), "the replayed timeline finishes the door");
        assertEquals(uninterruptedY, restored.getCentreY(),
                "forward replay lands on the uninterrupted y");
        assertEquals(DOOR_Y - 0x40, restored.getCentreY(), "which is the ROM's 64-pixel rise");
    }

    /**
     * AFTER the opening: the one-way latch is the interesting part. A restored open door must stay
     * open even though the trigger array is restored with it, because {@code loc_429BC} never
     * re-reads the array.
     */
    @Test
    void doorRewindSpotAfterOpeningKeepsTheOneWayLatch() {
        Harness harness = Harness.create();
        LrzDoorObjectInstance door = harness.door();
        Sonic3kLevelTriggerManager.setBit(TRIGGER_INDEX, 0);
        for (int frame = 0; frame < OPEN_ANGLE; frame++) {
            door.update(frame, null);
        }
        assertTrue(door.isFullyOpen(), "precondition: fully open");
        Sonic3kLevelTriggerManager.clearAll(TRIGGER_INDEX);

        CompositeSnapshot after = harness.capture();

        for (int frame = 0; frame < 30; frame++) {
            door.update(frame, null);
        }

        harness.restore(after);
        LrzDoorObjectInstance restored = harness.onlyDoor();
        assertTrue(restored.isFullyOpen(), "the finished routine stage restores");
        assertEquals(OPEN_ANGLE, restored.openTimer(), "$2E restores at $40");
        assertEquals(DOOR_Y - 0x40, restored.getCentreY(), "y restores fully open");

        for (int frame = 0; frame < 30; frame++) {
            restored.update(frame, null);
        }
        assertEquals(DOOR_Y - 0x40, restored.getCentreY(),
                "a restored open door neither closes nor moves again");
        assertEquals(OPEN_ANGLE, restored.openTimer());
    }

    /**
     * The button's latch is one frame deep and lives in two places at once: the object's own
     * side-contact flag and the shared array byte. Capturing while pressed and restoring must bring
     * both back, or the next update writes the wrong thing.
     */
    @Test
    void buttonRewindSpotRestoresThePressAndTheSharedArrayTogether() {
        Harness harness = Harness.create();
        LrzButtonHorizontalObjectInstance button = harness.button();

        button.onSolidContact(null, sideContact(), 0);
        button.update(0, null);
        assertTrue(Sonic3kLevelTriggerManager.testBit(TRIGGER_INDEX, 0), "precondition: pressed");
        assertEquals(1, button.mappingFrame());

        CompositeSnapshot pressed = harness.capture();

        // Diverge: let go. The ROM clears the bit the first frame nobody is touching it.
        button.update(1, null);
        assertFalse(Sonic3kLevelTriggerManager.testBit(TRIGGER_INDEX, 0),
                "the diverging timeline released the button");
        assertEquals(0, button.mappingFrame());

        harness.restore(pressed);
        LrzButtonHorizontalObjectInstance restored = harness.onlyButton();
        assertNotSame(button, restored, "restore recreates the button");
        assertEquals(TRIGGER_INDEX, restored.triggerIndex(), "the subtype decode rebuilds");
        assertEquals(0, restored.triggerBit());
        assertFalse(restored.isLatching());
        assertTrue(Sonic3kLevelTriggerManager.testBit(TRIGGER_INDEX, 0),
                "the shared array byte restores with the button");

        // Forward replay with nobody touching it releases again, exactly as the diverged run did.
        restored.update(1, null);
        assertFalse(Sonic3kLevelTriggerManager.testBit(TRIGGER_INDEX, 0),
                "forward replay reproduces the release");
        assertEquals(0, restored.mappingFrame());
    }

    /** A door and its button together: the spot a route rewind actually lands on. */
    @Test
    void buttonAndDoorRewindTogetherMidOpening() {
        Harness harness = Harness.create();
        LrzButtonHorizontalObjectInstance button = harness.button();
        LrzDoorObjectInstance door = harness.door();

        // Press, then hold for a few frames so the door starts.
        for (int frame = 0; frame < 5; frame++) {
            button.onSolidContact(null, sideContact(), frame);
            button.update(frame, null);
            door.update(frame, null);
        }
        assertEquals(5, door.openTimer(), "the door followed the held button");

        CompositeSnapshot midway = harness.capture();

        for (int frame = 5; frame < 25; frame++) {
            door.update(frame, null);
        }
        assertEquals(25, door.openTimer());

        harness.restore(midway);
        LrzDoorObjectInstance restoredDoor = harness.onlyDoor();
        LrzButtonHorizontalObjectInstance restoredButton = harness.onlyButton();
        assertEquals(5, restoredDoor.openTimer(), "the door restores mid-travel");
        assertTrue(Sonic3kLevelTriggerManager.testBit(TRIGGER_INDEX, 0),
                "the held press restores with it");

        for (int frame = 5; frame < 25; frame++) {
            restoredButton.onSolidContact(null, sideContact(), frame);
            restoredButton.update(frame, null);
            restoredDoor.update(frame, null);
        }
        assertEquals(25, restoredDoor.openTimer(), "forward replay reaches the same frame count");
    }

    // ----- harness ------------------------------------------------------------------------------

    private static SolidContact sideContact() {
        return new SolidContact(false, true, false, false, true);
    }

    private record Harness(ObjectManager objectManager, RewindRegistry registry) {

        static Harness create() {
            ObjectManager[] holder = new ObjectManager[1];
            Camera camera = mockCamera();
            ObjectServices services = new StubObjectServices() {
                @Override public ObjectManager objectManager() { return holder[0]; }
                @Override public Camera camera() { return camera; }
                @Override public GraphicsManager graphicsManager() { return GraphicsManager.getInstance(); }
            };
            ObjectManager objectManager = new ObjectManager(
                    List.of(),
                    new Sonic3kObjectRegistry(),
                    0,
                    null,
                    null,
                    GraphicsManager.getInstance(),
                    camera,
                    services);
            holder[0] = objectManager;
            objectManager.reset(0);
            objectManager.setRewindInPlaceRestoreEnabledForTest(false);

            RewindRegistry registry = new RewindRegistry();
            registry.register(objectManager.rewindSnapshottable());
            // Sonic3kLevelEventManager#extraRewindAdapters registers the same adapter in production.
            registry.register(new Sonic3kLevelTriggerStaticAdapter());
            return new Harness(objectManager, registry);
        }

        LrzDoorObjectInstance door() {
            return objectManager.createDynamicObject(() -> new LrzDoorObjectInstance(DOOR_SPAWN));
        }

        LrzButtonHorizontalObjectInstance button() {
            return objectManager.createDynamicObject(
                    () -> new LrzButtonHorizontalObjectInstance(BUTTON_SPAWN));
        }

        CompositeSnapshot capture() {
            return registry.capture();
        }

        void restore(CompositeSnapshot snapshot) {
            registry.restore(snapshot);
        }

        LrzDoorObjectInstance onlyDoor() {
            return only(LrzDoorObjectInstance.class);
        }

        LrzButtonHorizontalObjectInstance onlyButton() {
            return only(LrzButtonHorizontalObjectInstance.class);
        }

        private <T extends ObjectInstance> T only(Class<T> type) {
            List<T> live = objectManager.getActiveObjects().stream()
                    .filter(object -> object.getClass() == type && !object.isDestroyed())
                    .map(type::cast)
                    .toList();
            assertEquals(1, live.size(), "exactly one live " + type.getSimpleName() + " after restore");
            return live.get(0);
        }
    }

    private static Camera mockCamera() {
        return new Camera() {
            @Override public short getX() { return 0; }
            @Override public short getY() { return 0; }
            @Override public short getWidth() { return 0x800; }
            @Override public short getHeight() { return 0x800; }
            @Override public boolean isVerticalWrapEnabled() { return false; }
        };
    }
}
