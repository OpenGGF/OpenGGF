package com.openggf.level.objects;

/**
 * Game-specific dynamic object slot layout for the shared {@link ObjectManager}.
 *
 * <p>Only the allocatable dynamic slot window is modeled here. Fixed player/UI/support
 * slots that live outside the manager remain owned by their respective systems.
 */
public record ObjectSlotLayout(
        int firstDynamicSlot,
        int dynamicSlotCount,
        int processSlotCount,
        boolean twoAxisCursorPlacement,
        boolean preallocatesLostRingOwnerSlot) {
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
    // S3K HurtCharacter allocates the first Obj37 owner slot before Obj37_Init
    // fills the spill with AllocateObjectAfterCurrent from that owner.
    //
    // S3K Process_Sprites still walks the full 110-slot Object_RAM table
    // (docs/skdisasm/sonic3k.constants.asm:303-323;
    // docs/skdisasm/sonic3k.asm:35965-35980), so frame-cadence code that
    // reads d7 must use processSlotCount rather than the dynamic allocator end.
    public static final ObjectSlotLayout SONIC_3K = new ObjectSlotLayout(4, 89, 110, true, true);

    public ObjectSlotLayout(int firstDynamicSlot, int dynamicSlotCount) {
        this(firstDynamicSlot, dynamicSlotCount, firstDynamicSlot + dynamicSlotCount, false, false);
    }

    public ObjectSlotLayout(int firstDynamicSlot, int dynamicSlotCount, boolean twoAxisCursorPlacement) {
        this(firstDynamicSlot, dynamicSlotCount, firstDynamicSlot + dynamicSlotCount,
                twoAxisCursorPlacement, false);
    }

    public ObjectSlotLayout(int firstDynamicSlot, int dynamicSlotCount,
                            boolean twoAxisCursorPlacement, boolean preallocatesLostRingOwnerSlot) {
        this(firstDynamicSlot, dynamicSlotCount, firstDynamicSlot + dynamicSlotCount,
                twoAxisCursorPlacement, preallocatesLostRingOwnerSlot);
    }

    public ObjectSlotLayout {
        if (firstDynamicSlot < 0) {
            throw new IllegalArgumentException("firstDynamicSlot must be >= 0");
        }
        if (dynamicSlotCount < 0) {
            throw new IllegalArgumentException("dynamicSlotCount must be >= 0");
        }
        if (processSlotCount < firstDynamicSlot + dynamicSlotCount) {
            throw new IllegalArgumentException("processSlotCount must cover the dynamic slot window");
        }
    }

    public int lastDynamicSlotExclusive() {
        return firstDynamicSlot + dynamicSlotCount;
    }

    public int lastProcessSlotExclusive() {
        return processSlotCount;
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
