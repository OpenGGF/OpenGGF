package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.TransitionSstOccupant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestObjectManagerFixedSstExecution {

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void restoredFixedSlotThreeExecutesBeforeSlotFourAndReplacementWaitsForNextPass() {
        List<String> executionOrder = new ArrayList<>();
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return holder[0];
            }
        };
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);

        ObjectManager manager = new ObjectManager(
                List.of(), new S3kLayoutRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;

        RecordingObject replacement = new RecordingObject("replacement-3", executionOrder);
        SelfDeletingObject restoredFixedSlot = new SelfDeletingObject(executionOrder);
        ReplacementPublisher slotFour = new ReplacementPublisher(replacement, executionOrder);
        manager.addDynamicObjectAtSlot(restoredFixedSlot, 3);
        manager.addDynamicObjectAtSlot(slotFour, 4);

        List<TransitionSstOccupant> carried = manager.snapshotAllLiveSstObjectsForTransition();
        assertEquals(List.of(3, 4), carried.stream().map(TransitionSstOccupant::originalSlot).toList());
        ObjectManager restoredManager = new ObjectManager(
                List.of(), new S3kLayoutRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = restoredManager;
        for (TransitionSstOccupant occupant : carried) {
            restoredManager.addDynamicObjectAtSlot(occupant.identity(), occupant.originalSlot());
        }
        manager = restoredManager;

        manager.update(0, null, List.of(), 1);

        assertEquals(List.of("restored-3", "slot-4"), executionOrder,
                "Process_Sprites must visit a restored fixed SST before the allocatable window; "
                        + "a replacement written into the already-visited slot waits for the next pass");
        assertFalse(manager.getActiveObjects().contains(restoredFixedSlot));
        assertTrue(manager.getActiveObjects().contains(replacement));
        assertEquals(3, replacement.getSlotIndex(),
                "fixed-slot destruction/replacement must retain the exact global SST identity");
        assertEquals(0, replacement.updateCount);

        manager.update(0, null, List.of(), 2);

        assertEquals(List.of("restored-3", "slot-4", "replacement-3", "slot-4"), executionOrder,
                "the replacement must execute at slot 3 before slot 4 on the following pass");
        assertSame(replacement, manager.getActiveObjects().stream()
                .filter(object -> object instanceof AbstractObjectInstance instance
                        && instance.getSlotIndex() == 3)
                .findFirst()
                .orElseThrow());
    }

    @Test
    void fixedSupportSlotsAfterDynamicWindowKeepGlobalOrderWithoutConsumingAllocationCapacity() {
        List<String> executionOrder = new ArrayList<>();
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);
        ObjectManager manager = new ObjectManager(
                List.of(), new S3kLayoutRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, new StubObjectServices());

        manager.addDynamicObjectAtSlot(new RecordingObject("fixed-109", executionOrder), 109);
        manager.addDynamicObjectAtSlot(new RecordingObject("dynamic-4", executionOrder), 4);
        manager.addDynamicObjectAtSlot(new RecordingObject("fixed-94", executionOrder), 94);
        manager.addDynamicObjectAtSlot(new RecordingObject("fixed-3", executionOrder), 3);

        assertEquals(1, manager.getActiveObjectSlotCount(),
                "fixed/process-only SSTs must not consume AllocateObject capacity");
        assertEquals(ObjectSlotLayout.SONIC_3K.dynamicSlotCount(), manager.getObjectSlotCapacity());

        manager.update(0, null, List.of(), 1);

        assertEquals(List.of("fixed-3", "dynamic-4", "fixed-94", "fixed-109"), executionOrder);
        assertEquals(1, manager.getActiveObjectSlotCount(),
                "executing support slots must leave dynamic allocator pressure unchanged");
    }

    private static final class S3kLayoutRegistry implements ObjectRegistry {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "FixedSstTest";
        }

        @Override
        public ObjectSlotLayout objectSlotLayout() {
            return ObjectSlotLayout.SONIC_3K;
        }
    }

    private static class RecordingObject extends AbstractObjectInstance {
        private final String marker;
        private final List<String> executionOrder;
        private int updateCount;

        private RecordingObject(String marker, List<String> executionOrder) {
            super(new ObjectSpawn(0, 0, 0x01, 0, 0, false, 0), marker);
            this.marker = marker;
            this.executionOrder = executionOrder;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            updateCount++;
            executionOrder.add(marker);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static final class SelfDeletingObject extends RecordingObject {
        private SelfDeletingObject(List<String> executionOrder) {
            super("restored-3", executionOrder);
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            super.update(frameCounter, player);
            setDestroyed(true);
        }
    }

    private static final class ReplacementPublisher extends RecordingObject {
        private final RecordingObject replacement;
        private boolean published;

        private ReplacementPublisher(RecordingObject replacement, List<String> executionOrder) {
            super("slot-4", executionOrder);
            this.replacement = replacement;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            super.update(frameCounter, player);
            if (!published) {
                published = true;
                services().objectManager().addDynamicObjectAtSlot(replacement, 3);
            }
        }
    }
}
