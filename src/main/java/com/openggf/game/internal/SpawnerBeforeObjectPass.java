package com.openggf.game.internal;

/**
 * Game service marker: the object spawner runs at the start of the level frame, before the
 * object pass, with the camera the previous frame left (Sonic CD {@code SpawnObjects;
 * RunObjects}), rather than after the camera update (Sonic 1 ObjPosLoad).
 */
public interface SpawnerBeforeObjectPass {
}
