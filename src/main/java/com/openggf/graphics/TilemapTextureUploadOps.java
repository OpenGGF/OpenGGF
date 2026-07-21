package com.openggf.graphics;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.GL_RGBA;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glTexSubImage2D;

/** Engine-internal seam for incremental tilemap uploads. */
public interface TilemapTextureUploadOps {
    void uploadColumns(int textureId, int destinationColumn, int columnCount,
            int heightTiles, ByteBuffer packedRows);

    void uploadRows(int textureId, int destinationRow, int widthTiles,
            int rowCount, ByteBuffer contiguousRows);

    static TilemapTextureUploadOps openGl() {
        return OpenGlHolder.INSTANCE;
    }

    final class OpenGlHolder {
        private static final TilemapTextureUploadOps INSTANCE = new TilemapTextureUploadOps() {
            @Override
            public void uploadColumns(int textureId, int destinationColumn, int columnCount,
                    int heightTiles, ByteBuffer packedRows) {
                glBindTexture(GL_TEXTURE_2D, textureId);
                glTexSubImage2D(GL_TEXTURE_2D, 0, destinationColumn, 0, columnCount, heightTiles,
                        GL_RGBA, GL_UNSIGNED_BYTE, packedRows);
                glBindTexture(GL_TEXTURE_2D, 0);
            }

            @Override
            public void uploadRows(int textureId, int destinationRow, int widthTiles,
                    int rowCount, ByteBuffer contiguousRows) {
                glBindTexture(GL_TEXTURE_2D, textureId);
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, destinationRow, widthTiles, rowCount,
                        GL_RGBA, GL_UNSIGNED_BYTE, contiguousRows);
                glBindTexture(GL_TEXTURE_2D, 0);
            }
        };

        private OpenGlHolder() {
        }
    }
}
