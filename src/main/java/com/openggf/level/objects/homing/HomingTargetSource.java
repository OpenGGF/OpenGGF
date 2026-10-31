package com.openggf.level.objects.homing;

/**
 * Optional semantic homing role for objects that touch categories cannot describe: breakable
 * walls, monitors, grab points, and boss parts with a hidden progress priority. Returning
 * {@code null} excludes the object for the current tick. Only the homing-attack mutator reads
 * this; native touch, solidity, breakage and defeat never consult it.
 */
public interface HomingTargetSource {
    /** {@code player} is the homing leader, for roles that depend on character abilities. */
    HomingTarget homingTarget(com.openggf.game.PlayableEntity player);
}
