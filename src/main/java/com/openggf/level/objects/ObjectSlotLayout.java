package com.openggf.level.objects;

/**
 * Game-specific dynamic object slot layout for the shared {@link ObjectManager}.
 *
 * <p>Only the allocatable dynamic slot window is modeled here. Fixed player/UI/support
 * slots that live outside the manager remain owned by their respective systems.
 */
public record ObjectSlotLayout(int firstDynamicSlot, int dynamicSlotCount, boolean twoAxisCursorPlacement) {
    public static final ObjectSlotLayout SONIC_1 = new ObjectSlotLayout(32, 96);
    public static final ObjectSlotLayout SONIC_2 = new ObjectSlotLayout(16, 112);
    // S3K Object_RAM has Player_1, Player_2, and Reserved_object_3 before
    // Dynamic_object_RAM, but AllocateObject pre-increments from
    // Dynamic_object_RAM before testing a slot (docs/skdisasm/sonic3k.asm:37906-37918),
    // so normal dynamic allocation starts at global SST slot 4.
    //
    // S3K Load_Sprites also has separate X-cursor and Y-camera allocation passes:
    // the X pass advances Object_load_addr_front (docs/skdisasm/sonic3k.asm:37640-37658),
    // then the Y pass allocates previously X-passed entries that enter the vertical band
    // (docs/skdisasm/sonic3k.asm:37723-37762). This can allocate newer X-pass entries
    // before older deferred-Y entries.
    public static final ObjectSlotLayout SONIC_3K = new ObjectSlotLayout(4, 89, true);

    public ObjectSlotLayout(int firstDynamicSlot, int dynamicSlotCount) {
        this(firstDynamicSlot, dynamicSlotCount, false);
    }

    public ObjectSlotLayout {
        if (firstDynamicSlot < 0) {
            throw new IllegalArgumentException("firstDynamicSlot must be >= 0");
        }
        if (dynamicSlotCount < 0) {
            throw new IllegalArgumentException("dynamicSlotCount must be >= 0");
        }
    }

    public int lastDynamicSlotExclusive() {
        return firstDynamicSlot + dynamicSlotCount;
    }

    public boolean isDynamicSlot(int slotIndex) {
        return slotIndex >= firstDynamicSlot && slotIndex < lastDynamicSlotExclusive();
    }

    public int toExecIndex(int slotIndex) {
        return slotIndex - firstDynamicSlot;
    }

    public int toSlotIndex(int execIndex) {
        return firstDynamicSlot + execIndex;
    }
}
