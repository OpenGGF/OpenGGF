package com.openggf.game.internal;

/**
 * Game service: the object ID a spilled (lost) ring carries in its slot, where it is not Sonic 1's {@code $37}
 * ({@link com.openggf.level.rings.LostRingObjectInstance#DEFAULT_OBJECT_ID}). Sonic CD's ring loss writes
 * {@code $11} ({@code move.b #$11,oID(a1)}, Level/_Objects/Ring/Main.asm:353; HurtPlayer, Level/Palmtree
 * Panic/Player Object Collision.asm:236). A child that tests a slot's ID byte sees this value.
 */
@FunctionalInterface
public interface LostRingObjectId {
    int lostRingObjectId();
}
