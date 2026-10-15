package com.openggf.level.objects;

import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestObjectTouchResponseRewindCapture {

    @BeforeEach
    void setUp() {
        TestEnvironment.configureGameModuleFixture(new Sonic2GameModule());
    }

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void captureOmitsEmptySidekickOverlapEntries() throws Exception {
        ObjectTouchResponseController controller = new ObjectTouchResponseController(null, null);
        Tails sidekick = new Tails("tails_p2", (short) 0, (short) 0);
        sidekick.setCpuControlled(true);

        sidekickOverlapMap(controller).put(sidekick, newEmptyOverlapPair());

        var snapshot = controller.captureRewindState();

        assertTrue(snapshot.sidekickEntries().isEmpty(),
                "empty per-sidekick overlap buffers should not allocate snapshot entries");
    }

    @Test
    void captureIsCanonicalAcrossIdentityHashOrder() throws Exception {
        ObjectManager manager = org.mockito.Mockito.mock(ObjectManager.class);
        org.mockito.Mockito.when(manager.objectCallbacks()).thenReturn(new ObjectCallbackRouter(null));
        ObjectTouchResponseController controller = new ObjectTouchResponseController(manager, null);
        var overlaps = sidekickOverlapMap(controller);
        // Seven-follower rosters made the identity-hash iteration order of the
        // per-sidekick map visible after restore re-inserted the same members.
        for (int index = 12; index >= 1; index--) {
            Tails sidekick = new Tails(String.format("tails_p%02d", index), (short) 0, (short) 0);
            sidekick.setCpuControlled(true);
            Object pair = newEmptyOverlapPair();
            @SuppressWarnings("unchecked")
            var overlapping = (java.util.Set<ObjectInstance>) pairField(pair, "overlapping");
            for (int slot : new int[] {40 + index, 20 + index, 60 + index}) overlapping.add(slotObject(slot));
            overlaps.put(sidekick, pair);
        }

        var snapshot = controller.captureRewindState();

        assertEquals(12, snapshot.sidekickEntries().size());
        for (int index = 0; index < 12; index++) {
            var entry = snapshot.sidekickEntries().get(index);
            assertEquals(String.format("tails_p%02d", index + 1), entry.sidekickCode());
            assertArrayEquals(new int[] {21 + index, 41 + index, 61 + index}, entry.overlappingSlotIndices());
        }
    }

    private static ObjectInstance slotObject(int slot) {
        var object = new AbstractObjectInstance(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "slot") {
            @Override public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) { }
            @Override public void appendRenderCommands(java.util.List<com.openggf.graphics.GLCommand> commands) { }
        };
        object.setSlotIndex(slot);
        return object;
    }

    private static Object pairField(Object pair, String name) throws Exception {
        Field field = pair.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(pair);
    }

    @SuppressWarnings("unchecked")
    private static Map<com.openggf.game.PlayableEntity, Object> sidekickOverlapMap(
            ObjectTouchResponseController controller) throws Exception {
        Field field = ObjectTouchResponseController.class.getDeclaredField("sidekickOverlaps");
        field.setAccessible(true);
        return (Map<com.openggf.game.PlayableEntity, Object>) field.get(controller);
    }

    private static Object newEmptyOverlapPair() throws Exception {
        Class<?> pairClass = Class.forName(
                "com.openggf.level.objects.ObjectTouchResponseController$OverlapBufferPair");
        Constructor<?> constructor = pairClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }
}
