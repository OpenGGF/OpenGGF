package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.identity.ObjectRefId;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TestObjectRewindExactAdoption {
    @Test
    void s3kAdoptionAndRollbackUseTheCapturedGlobalSstSlotAtomically() throws Exception {
        ObjectManager manager = s3kObjectManager();
        reserveOnly(manager, 9, 13);
        int reservedCount = manager.getActiveObjectSlotCount();
        var adoption = manager.exactRewindAdoptionSurface();
        ObjectRefId sentinelId = ObjectRefId.dynamic(9, 1, 90);
        ObjectRefId targetId = ObjectRefId.dynamic(13, 1, 130);

        ProbeObject sentinel = adoption.adopt(sentinelId, ProbeObject::new);
        ProbeObject target = adoption.adopt(targetId, ProbeObject::new);

        assertNotNull(sentinel);
        assertNotNull(target);
        ObjectInstance[] execOrder = execOrder(manager);
        assertSame(sentinel, execOrder[9], "S3K execOrder uses the global SST slot");
        assertSame(target, execOrder[13], "the adopted object must occupy its captured global slot");
        assertSame(sentinel, execOrder[13 - ObjectSlotLayout.SONIC_3K.firstDynamicSlot()],
                "adopting slot 13 must not overwrite the existing slot-9 occupant");
        assertEquals(reservedCount, manager.getActiveObjectSlotCount(),
                "adoption must reuse the snapshot-owned reservations");
        assertSame(target, manager.captureIdentityContext().requireIdentityTable().resolve(targetId));

        AtomicBoolean occupiedFactoryCalled = new AtomicBoolean();
        assertNull(adoption.adopt(ObjectRefId.dynamic(9, 2, 91), () -> {
            occupiedFactoryCalled.set(true);
            return new ProbeObject();
        }));
        assertFalse(occupiedFactoryCalled.get(), "an occupied captured slot must fail before construction");
        assertSame(sentinel, execOrder[9]);
        assertSame(target, execOrder[13]);
        assertEquals(reservedCount, manager.getActiveObjectSlotCount());

        adoption.rollback(target);

        assertNull(execOrder[13], "rollback must clear the exact global slot it adopted");
        assertSame(sentinel, execOrder[9], "rollback must not disturb a different slot occupant");
        assertEquals(-1, target.getSlotIndex());
        assertNull(manager.captureIdentityContext().requireIdentityTable().resolve(targetId));
        assertSame(sentinel, manager.captureIdentityContext().requireIdentityTable().resolve(sentinelId));
        assertEquals(reservedCount, manager.getActiveObjectSlotCount(),
                "rollback must preserve the pre-existing snapshot reservation bits");
        assertEquals(List.of(sentinel), manager.activeObjectsOfType(ProbeObject.class));
    }

    private static void reserveOnly(ObjectManager manager, int... retainedSlots) throws Exception {
        Field field = ObjectManager.class.getDeclaredField("slotAllocator");
        field.setAccessible(true);
        SlotAllocator allocator = (SlotAllocator) field.get(manager);
        for (int slot : retainedSlots) {
            assertTrue(allocator.reserve(slot));
        }
    }

    private static ObjectInstance[] execOrder(ObjectManager manager) throws Exception {
        Field field = ObjectManager.class.getDeclaredField("execOrder");
        field.setAccessible(true);
        return (ObjectInstance[]) field.get(manager);
    }

    private static ObjectManager s3kObjectManager() {
        ObjectRegistry registry = new ObjectRegistry() {
            @Override public ObjectInstance create(ObjectSpawn spawn) { return null; }
            @Override public void reportCoverage(List<ObjectSpawn> spawns) { }
            @Override public String getPrimaryName(int objectId) { return "test"; }
            @Override public ObjectSlotLayout objectSlotLayout() { return ObjectSlotLayout.SONIC_3K; }
        };
        ObjectManager[] holder = new ObjectManager[1];
        StubObjectServices services = new StubObjectServices() {
            @Override public ObjectManager objectManager() { return holder[0]; }
        };
        holder[0] = new ObjectManager(List.of(), registry, 0, null, null,
                GraphicsManager.getInstance(), null, services);
        return holder[0];
    }

    private static final class ProbeObject extends AbstractObjectInstance {
        private ProbeObject() {
            super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "ExactAdoptionProbe");
        }

        @Override public void update(int frameCounter, PlayableEntity player) { }
        @Override public int getX() { return 0; }
        @Override public int getY() { return 0; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
