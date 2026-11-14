package com.openggf.graphics;

import java.nio.ByteBuffer;

/**
 * Engine-internal seam for incremental tilemap uploads. The production
 * implementation is owned by each {@link TilemapTexture} and stages through its
 * pixel-unpack stream; tests substitute a recording implementation.
 */
public interface TilemapTextureUploadOps {
    void uploadColumns(int textureId, int destinationColumn, int columnCount,
            int heightTiles, ByteBuffer packedRows);

    void uploadRows(int textureId, int destinationRow, int widthTiles,
            int rowCount, ByteBuffer contiguousRows);
}
