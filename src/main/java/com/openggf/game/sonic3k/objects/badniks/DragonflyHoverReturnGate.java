package com.openggf.game.sonic3k.objects.badniks;

/**
 * Mirrors ROM {@code $38} bit 2 on a Dragonfly (Obj $8E, {@code sonic3k.asm:193742})
 * or one of its linked-body tail segments: set while the object is in its
 * hover-wait return routine, observed by the next object in the tail chain
 * via its {@code parent3} anchor ({@code loc_8DDCA}/{@code loc_8DDF8} on the
 * main object; {@code loc_8DE8A}/{@code loc_8DEF4} on each segment).
 *
 * <p>The value is live: the ROM allocates the chain in ascending SST order
 * ({@code CreateChild4_LinkListRepeated} calling {@code AllocateObjectAfterCurrent}
 * with the Dragonfly in {@code a0}, {@code sonic3k.asm:177038-177061,37917-37930}),
 * so each link runs after its anchor within the same object-execution pass and
 * sees the bit as it stands this frame.
 */
interface DragonflyHoverReturnGate {
    boolean isHoverReturnGateOpen();
}
