package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * 2D texture storing tile descriptors for GPU tilemap rendering.
 */
public class TilemapTexture {
    private int textureId = 0;
    private int widthTiles = 0;
    private int heightTiles = 0;
    private ByteBuffer uploadBuffer;
    private int uploadBufferCapacity;

    public void init(int widthTiles, int heightTiles) {
        if (widthTiles <= 0 || heightTiles <= 0) {
            return;
        }
        if (textureId == 0) {
            textureId = glGenTextures();
        }
        this.widthTiles = widthTiles;
        this.heightTiles = heightTiles;

        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, widthTiles, heightTiles, 0,
                GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public void upload(byte[] data, int widthTiles, int heightTiles) {
        long requiredBytes = (long) widthTiles * heightTiles * 4L;
        if (data == null || widthTiles <= 0 || heightTiles <= 0
                || requiredBytes > data.length || requiredBytes > Integer.MAX_VALUE) {
            return;
        }
        if (textureId == 0 || this.widthTiles != widthTiles || this.heightTiles != heightTiles) {
            init(widthTiles, heightTiles);
        }
        ensureUploadCapacity(data.length);
        uploadBuffer.clear();
        uploadBuffer.put(data);
        uploadBuffer.flip();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, widthTiles, heightTiles,
                GL_RGBA, GL_UNSIGNED_BYTE, uploadBuffer);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /**
     * Uploads a contiguous run of logical source columns into a physical texture
     * column. Rows are packed into caller-independent staging because the source
     * array retains the full tilemap row stride.
     */
    public boolean uploadColumns(byte[] data, int sourceWidthTiles, int heightTiles,
            int sourceColumn, int destinationColumn, int columnCount) {
        long requiredSourceBytes = (long) sourceWidthTiles * heightTiles * 4L;
        if (data == null || sourceWidthTiles <= 0 || heightTiles <= 0 || columnCount <= 0
                || sourceColumn < 0 || columnCount > sourceWidthTiles
                || sourceColumn > sourceWidthTiles - columnCount
                || destinationColumn < 0 || destinationColumn > sourceWidthTiles - columnCount
                || requiredSourceBytes > data.length || requiredSourceBytes > Integer.MAX_VALUE
                || !hasStorage(sourceWidthTiles, heightTiles)) {
            return false;
        }
        int uploadBytes = columnCount * heightTiles * 4;
        ensureUploadCapacity(uploadBytes);
        uploadBuffer.clear();
        int sourceRowBytes = sourceWidthTiles * 4;
        int copiedRowBytes = columnCount * 4;
        int sourceOffset = sourceColumn * 4;
        for (int row = 0; row < heightTiles; row++) {
            uploadBuffer.put(data, row * sourceRowBytes + sourceOffset, copiedRowBytes);
        }
        uploadBuffer.flip();
        uploadSubImage(destinationColumn, columnCount, heightTiles, uploadBuffer);
        return true;
    }

    protected void uploadSubImage(int destinationColumn, int columnCount,
            int heightTiles, ByteBuffer packedRows) {
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, destinationColumn, 0, columnCount, heightTiles,
                GL_RGBA, GL_UNSIGNED_BYTE, packedRows);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    /** Uploads contiguous full-width logical rows into physical texture rows. */
    public boolean uploadRows(byte[] data, int widthTiles, int sourceHeightTiles,
            int sourceRow, int destinationRow, int rowCount) {
        long requiredSourceBytes = (long) widthTiles * sourceHeightTiles * 4L;
        long uploadBytes = (long) widthTiles * rowCount * 4L;
        if (data == null || widthTiles <= 0 || sourceHeightTiles <= 0 || rowCount <= 0
                || sourceRow < 0 || rowCount > sourceHeightTiles
                || sourceRow > sourceHeightTiles - rowCount
                || destinationRow < 0 || destinationRow > sourceHeightTiles - rowCount
                || requiredSourceBytes > data.length || requiredSourceBytes > Integer.MAX_VALUE
                || uploadBytes > Integer.MAX_VALUE
                || !hasStorage(widthTiles, sourceHeightTiles)) {
            return false;
        }
        int uploadLength = (int) uploadBytes;
        int sourceOffset = sourceRow * widthTiles * 4;
        ensureUploadCapacity(uploadLength);
        uploadBuffer.clear();
        uploadBuffer.put(data, sourceOffset, uploadLength);
        uploadBuffer.flip();
        uploadRowsSubImage(destinationRow, widthTiles, rowCount, uploadBuffer);
        return true;
    }

    protected void uploadRowsSubImage(int destinationRow, int widthTiles,
            int rowCount, ByteBuffer contiguousRows) {
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, destinationRow, widthTiles, rowCount,
                GL_RGBA, GL_UNSIGNED_BYTE, contiguousRows);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public boolean hasStorage(int widthTiles, int heightTiles) {
        return textureId != 0 && this.widthTiles == widthTiles && this.heightTiles == heightTiles;
    }

    private void ensureUploadCapacity(int capacity) {
        if (uploadBuffer != null && uploadBufferCapacity >= capacity) {
            return;
        }
        if (uploadBuffer != null) {
            MemoryUtil.memFree(uploadBuffer);
        }
        uploadBuffer = MemoryUtil.memAlloc(capacity);
        uploadBufferCapacity = capacity;
    }

    public int getTextureId() {
        return textureId;
    }

    public int getWidthTiles() {
        return widthTiles;
    }

    public int getHeightTiles() {
        return heightTiles;
    }

    public void cleanup() {
        if (textureId != 0) {
            glDeleteTextures(textureId);
        }
        textureId = 0;
        widthTiles = 0;
        heightTiles = 0;
        if (uploadBuffer != null) {
            MemoryUtil.memFree(uploadBuffer);
            uploadBuffer = null;
            uploadBufferCapacity = 0;
        }
    }
}
