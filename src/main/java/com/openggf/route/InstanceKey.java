package com.openggf.route;

/**
 * A live object's timeline identity: stable across rewind recreation, new for a fresh respawn,
 * and meaningless after a level or timeline reset (hence the epoch). Never a Java pointer.
 */
public record InstanceKey(long epoch, int slot, int generation, int spawnId, int dynamicId, String kind) { }
