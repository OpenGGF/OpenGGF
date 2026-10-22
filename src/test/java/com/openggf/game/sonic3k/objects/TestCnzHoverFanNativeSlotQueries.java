package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestCnzHoverFanNativeSlotQueries {
    @Test
    void executionAliasObservesPendingDeletionAndSlotReplacement() {
        var services = new TestObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var earlier = fan(0x17D0, 0x0A08, 0x80, 0, 193);
        var later = fan(0x1800, 0x0AA0, 0x83, 1, 198);
        manager.addDynamicObjectAtSlot(earlier, 41);
        manager.addDynamicObjectAtSlot(later, 40);
        assertEquals(40, earlier.getExecutionSlotIndex());
        assertEquals(41, later.getExecutionSlotIndex());

        later.setDestroyed(true);
        assertEquals(40, earlier.getExecutionSlotIndex(),
                "the original typed population query includes an SST awaiting removal");
        manager.removeDynamicObject(later);
        var noise = new Noise();
        manager.addDynamicObjectAtSlot(noise, 40);
        assertEquals(41, earlier.getExecutionSlotIndex(),
                "a replacement of another type cannot borrow the fan execution slot");
    }

    @Test
    void repeatedExecutionQueriesDoNotRescanUnrelatedSlots() {
        var services = new TestObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var earlier = fan(0x17D0, 0x0A08, 0x80, 0, 193);
        var later = fan(0x1800, 0x0AA0, 0x83, 1, 198);
        var noise = new Noise();
        manager.addDynamicObjectAtSlot(earlier, 41);
        manager.addDynamicObjectAtSlot(later, 40);
        manager.addDynamicObjectAtSlot(noise, 60);
        assertEquals(40, earlier.getExecutionSlotIndex());
        noise.slotReads = 0;
        for (int query = 0; query < 12; query++) {
            assertEquals(40, earlier.getExecutionSlotIndex());
            assertEquals(41, later.getExecutionSlotIndex());
        }
        assertEquals(0, noise.slotReads,
                "execution setup should dereference the two adjacent slots, not sort the population");
    }

    @Test
    void adjacentSlotContinuesPastIneligibleFanSharingTheOwnedSlot() {
        var services = new TestObjectServices().withIsolatedObjectManager();
        var manager = services.objectManager();
        var earlier = fan(0x17D0, 0x0A08, 0x80, 0, 193);
        var ineligible = fan(0x1800, 0x0AA0, 0, 0, 197);
        var eligible = fan(0x1800, 0x0AA0, 0x83, 1, 198);
        manager.addDynamicObjectAtSlot(earlier, 41);
        manager.addDynamicObjectAtSlot(ineligible, 40);
        manager.addDynamicObjectAtSlot(eligible, 42);
        eligible.setSlotIndex(40);
        assertEquals(41, earlier.resolveActiveVariantExecutionSlot(List.of(ineligible)),
                "looking only at the first occupant would incorrectly retain the owned slot");
        assertEquals(40, earlier.resolveActiveVariantExecutionSlot(
                manager.activeObjectsOfType(CnzHoverFanInstance.class)), "positive control for old selection");
        assertEquals(40, earlier.getExecutionSlotIndex(),
                "an ineligible first occupant must not hide a later eligible fan in the same SST");
    }

    private static CnzHoverFanInstance fan(int x, int y, int subtype, int flags, int layoutIndex) {
        return new CnzHoverFanInstance(new ObjectSpawn(x, y, 0x46, subtype, flags, false, 0, layoutIndex));
    }

    private static final class Noise extends AbstractObjectInstance {
        private int slotReads;
        private Noise() { super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "slot-query-noise"); }
        @Override public int getSlotIndex() { slotReads++; return super.getSlotIndex(); }
        @Override public void update(int vIntRunCount, PlayableEntity player) { }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }
}
