package com.openggf.game.internal;

/**
 * Game service marker: the ring spill is the lost-ring owner's own init, run on the owner's turn in the object loop.
 * The hurt routine only claims a free slot and writes the owner's object ID there; the owner's routine 0 then
 * allocates the other rings, clears the ring count and plays the sound, and falls into its movement routine (Sonic CD
 * {@code HurtPlayer} and {@code ObjLostRing_Init}, Level/Palmtree Panic/Player Object Collision.asm:229-238,
 * Level/_Objects/Ring/Main.asm:332-410). An owner placed in a slot the loop has already passed (an object later in the
 * table hurt the player in its own turn) therefore spills on the next frame. Without the marker the shared spill
 * allocates every ring at the hurt and only defers the ring-count clear.
 */
public interface LostRingSpillAtOwnerTurn {
}
