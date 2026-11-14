package com.openggf.game.internal;

/**
 * Game service marker: the object spawner walks the level's object layout in its stored order
 * and stops at the first entry beyond the window, so entries load (and take free slots) in
 * that order even where the layout is not sorted by X (Sonic CD {@code SpawnObjects}). Without
 * it the shared placement sorts the layout by chunk.
 */
public interface StoredOrderObjectLayout {
}
