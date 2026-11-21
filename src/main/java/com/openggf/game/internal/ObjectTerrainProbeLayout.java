package com.openggf.game.internal;

import com.openggf.level.ChunkDesc;

/** Optional object-owned collision block lookup with per-read sprite effects. */
public interface ObjectTerrainProbeLayout {
    ChunkDesc readObjectProbeBlock(int x, int y, SpriteState sprite);

    interface SpriteState {
        boolean terrainProbeLoopPath();

        void setTerrainProbeLoopPath(boolean onLoopPath);

        void setTerrainProbePriority(boolean high);
    }
}
