package com.openggf.game.internal;

import com.openggf.level.ChunkDesc;

/** Internal level-owned collision address decoding; ordinary map levels retain their existing lookup. */
public interface EncodedCollisionLayout {
    boolean hasEncodedCollisionLayout();
    ChunkDesc collisionChunkDescAt(byte layer, int x, int y);
}
