package com.openggf.game.internal;

/**
 * Game service marker: when the counter-based spawner cannot find a free slot for a saved-flags entry, it clears
 * the entry's loaded bit again before it stops ({@code bclr #7,2(a2,d3.w)} in {@code SpawnObjects_Backward}
 * {@code .SpawnFailed} and {@code SpawnObjects_Forward}, Sonic CD Level/Object Spawner.asm:144-151, 198-201), so the
 * entry spawns when a cursor next walks over it. Sonic 1's {@code ObjPosLoad} leaves the bit its {@code bset} set
 * ({@code .failed_to_spawn}, docs/s1disasm/_inc/ObjPosLoad.asm:148-157), and the entry stays unloadable until the
 * bit is cleared some other way. Without this marker the shared spawner
 * keeps Sonic 1's rule.
 */
public interface FailedSpawnUnmarksEntry {
}
