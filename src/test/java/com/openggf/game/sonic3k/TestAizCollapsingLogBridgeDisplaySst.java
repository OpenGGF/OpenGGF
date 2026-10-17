package com.openggf.game.sonic3k;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.AizCollapsingLogBridgeObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obj_AIZCollapsingLogBridge's display SST (docs/skdisasm/sonic3k.asm, pinned 044fa46).
 *
 * <ul>
 *   <li>Init (:59245-59268 normal, :59290-59318 fire): AllocateObjectAfterCurrent gives the
 *       logs a display SST (loc_2AEB4) and stores its address in $3C. The parent never draws.</li>
 *   <li>loc_2AEB4 is Sprite_OnScreen_Test (:37302-37315): draw inside the coarse $280 window,
 *       otherwise delete, the same window as the parent's Delete_Sprite_If_Not_In_Range.</li>
 *   <li>sub_2AFFE (:59486-59521): the first falling log is written into the display SST; the
 *       other five come from AllocateObjectAfterCurrent.</li>
 * </ul>
 *
 * <p>Only types that predate the display SST are referenced, so the file is its own
 * old-source control: the display occupant is matched by simple class name.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestAizCollapsingLogBridgeDisplaySst {
    private static final int CAMERA_X = 0x4500;
    private static final int CAMERA_Y = 0x0200;
    private static final int BRIDGE_X = 0x4580;
    private static final int BRIDGE_Y = 0x0218;
    /** Fire drawbridge, subtype $85: $36 = 5 (first log delay), logs from x - $50 every $20. */
    private static final ObjectSpawn FIRE_BRIDGE =
            new ObjectSpawn(BRIDGE_X, BRIDGE_Y, Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE, 0x85, 0, false, 0);

    private HeadlessTestFixture fixture;
    private ObjectManager objects;
    private Camera camera;
    private int frame;

    @BeforeEach
    void setUp() {
        fixture = HeadlessTestFixture.builder().withZoneAndAct(0, 1).build();
        objects = GameServices.level().getObjectManager();
        camera = fixture.camera();
        camera.setX((short) CAMERA_X);
        camera.setY((short) CAMERA_Y);
        camera.setMinX((short) CAMERA_X);
        camera.setMaxX((short) CAMERA_X);
        stepObjects();
        stepObjects();
    }

    @AfterEach
    void tearDown() {
        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(false);
        TestEnvironment.resetAll();
    }

    @Test
    void firstPassAllocatesTheDisplaySstAfterTheBridge() {
        int bridgeSlot = slotAboveEveryOccupant();
        objects.addDynamicObjectAtSlot(new AizCollapsingLogBridgeObjectInstance(FIRE_BRIDGE), bridgeSlot);

        stepObjects();

        Map<Integer, String> slots = occupants();
        assertEquals("AizCollapsingLogBridgeObjectInstance", slots.get(bridgeSlot), "the parent keeps its SST");
        assertEquals("DisplayChild", slots.get(bridgeSlot + 1),
                "init allocates the display SST after the parent (AllocateObjectAfterCurrent)");
    }

    @Test
    void collapseWritesTheFirstLogIntoTheDisplaySst() throws Exception {
        int bridgeSlot = slotAboveEveryOccupant();
        objects.addDynamicObjectAtSlot(new AizCollapsingLogBridgeObjectInstance(FIRE_BRIDGE), bridgeSlot);
        stepObjects();
        assertEquals("DisplayChild", occupants().get(bridgeSlot + 1), "precondition: display SST allocated");

        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(true);
        stepObjects();

        AbstractObjectInstance first = occupantAt(bridgeSlot + 1);
        assertNotNull(first, "the display SST is not freed");
        assertEquals("CollapsingLogSegment", first.getClass().getSimpleName(),
                "movea.w $3C(a0),a3 / movea.l a3,a1: the first log takes the display SST");
        assertEquals(BRIDGE_X - 0x50, getInt(first, "fixedX"), "it is the leftmost fire log");
        assertEquals(5 - 1, getInt(first, "delayTimer"),
                "the display SST follows the parent, so the first log runs in the same pass");
        for (int i = 2; i <= 6; i++) {
            assertEquals("CollapsingLogSegment", occupants().get(bridgeSlot + i),
                    "logs 2-6 take the next free SSTs after the parent");
        }
    }

    @Test
    void displaySstLeavesWithItsParentOutsideTheCoarseWindow() {
        int bridgeSlot = slotAboveEveryOccupant();
        objects.addDynamicObjectAtSlot(new AizCollapsingLogBridgeObjectInstance(FIRE_BRIDGE), bridgeSlot);
        stepObjects();
        assertEquals("DisplayChild", occupants().get(bridgeSlot + 1), "precondition: display SST allocated");

        camera.setMinX((short) (CAMERA_X + 0x400));
        camera.setMaxX((short) (CAMERA_X + 0x400));
        camera.setX((short) (CAMERA_X + 0x400));
        stepObjects();

        Map<Integer, String> slots = occupants();
        assertTrue(!slots.containsKey(bridgeSlot) && !slots.containsKey(bridgeSlot + 1),
                "Sprite_OnScreen_Test and Delete_Sprite_If_Not_In_Range share the window: " + slots);
    }

    @Test
    void displaySstRestoresWithItsParentAndReplaysTheCollapse() throws Exception {
        GameplayModeContext gameplayMode = TestEnvironment.activeGameplayMode();
        assertNotNull(gameplayMode);
        RewindRegistry registry = gameplayMode.getRewindRegistry();
        int bridgeSlot = slotAboveEveryOccupant();
        objects.addDynamicObjectAtSlot(new AizCollapsingLogBridgeObjectInstance(FIRE_BRIDGE), bridgeSlot);
        stepObjects();
        Map<Integer, String> captured = occupants();
        CompositeSnapshot snapshot = registry.capture();
        int capturedFrame = frame;

        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(true);
        stepObjects();
        Map<Integer, String> reference = occupants();

        registry.restore(snapshot);
        frame = capturedFrame;
        assertEquals(captured, occupants(), "restore recreates the bridge and its display SST in place");
        AbstractObjectInstance display = occupantAt(bridgeSlot + 1);
        assertNotNull(display, "restored display SST");
        assertSame(occupantAt(bridgeSlot), field(display, "parent").get(display),
                "the restored display SST draws for the restored bridge");

        AizCollapsingLogBridgeObjectInstance.setDrawBridgeBurnActive(true);
        stepObjects();
        assertEquals(reference, occupants(), "forward replay collapses into the same SSTs");
    }

    private void stepObjects() {
        frame++;
        objects.update(camera.getX(), null, List.of(), frame, false);
    }

    /** Leaves free SSTs below the bridge, so lowest-free and after-current allocation differ. */
    private int slotAboveEveryOccupant() {
        int highest = occupants().keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        return highest + 3;
    }

    private AbstractObjectInstance occupantAt(int slot) {
        for (ObjectInstance object : objects.getActiveObjects()) {
            if (object instanceof AbstractObjectInstance instance && !object.isDestroyed()
                    && instance.getSlotIndex() == slot) {
                return instance;
            }
        }
        return null;
    }

    /** Slot -> simple class name for every live SST occupant. */
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
}
