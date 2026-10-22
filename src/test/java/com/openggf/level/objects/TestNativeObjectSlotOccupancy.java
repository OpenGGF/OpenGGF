package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestNativeObjectSlotOccupancy {
    @Test
    void rawDereferenceKeepsPendingDeletionUntilRemovalThenFollowsReplacement() {
        var services = new StubObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var pending = new Occupant(0x200);
        manager.addDynamicObjectAtSlot(pending, 40);
        assertSame(pending, NativeObjectQueries.atSlot(manager, 40, Occupant.class));
        pending.setDestroyed(true);
        assertSame(pending, NativeObjectQueries.atSlot(manager, 40, Occupant.class));
        assertNull(NativeObjectQueries.liveAtSlot(manager, 40, Occupant.class));
        manager.removeDynamicObject(pending);
        assertNull(NativeObjectQueries.atSlot(manager, 40, Occupant.class));
        var replacement = new Occupant(0x300);
        manager.addDynamicObjectAtSlot(replacement, 40);
        assertSame(replacement, NativeObjectQueries.atSlot(manager, 40, Occupant.class));
        assertEquals(0x300, NativeObjectQueries.atSlot(manager, 40, Occupant.class).getX());
    }

    private static final class Occupant extends AbstractObjectInstance {
        private Occupant(int x) { super(new ObjectSpawn(x, 0, 0, 0, 0, false, 0), "raw-slot-occupant"); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
