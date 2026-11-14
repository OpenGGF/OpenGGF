package com.openggf.game.internal;

/**
 * Game service marker: the counter-based spawner creates an object for a layout entry without a saved-flags bit
 * every time its cursor walks over the entry, even while an earlier object from that entry is still live (Sonic CD
 * {@code SpawnObject}, Object Spawner.asm:267-282: only a saved-flags entry is skipped, through its loaded bit).
 * Without it the shared placement keeps at most one live object per layout entry.
 */
public interface UntrackedEntriesSpawnWhileLive {
}
