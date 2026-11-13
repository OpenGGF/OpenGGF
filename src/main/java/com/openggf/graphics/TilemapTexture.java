package com.openggf.graphics;

import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;

/**
 * 2D texture storing tile descriptors for GPU tilemap rendering.
 */
@com.openggf.game.ModApi
public class TilemapTexture {
    private int textureId = 0;
    private int widthTiles = 0;
    private int heightTiles = 0;
    private ByteBuffer uploadBuffer;
    private int uploadBufferCapacity;
    // Uploads are queued GPU copies from this stream instead of client-memory reads
    // that make the frame thread wait for the driver queue (see PixelUnpackStream).
    private final PixelUnpackStream unpackStream = new PixelUnpackStream(1024 * 1024);
    private TilemapTextureUploadOps uploadOps = new UnpackBufferUploadOps();

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
        int requiredBytes = checkedTilemapByteCount(widthTiles, heightTiles);
        if (data == null || widthTiles <= 0 || heightTiles <= 0
                || requiredBytes < 0 || requiredBytes > data.length) {
            return;
        }
        if (textureId == 0 || this.widthTiles != widthTiles || this.heightTiles != heightTiles) {
            init(widthTiles, heightTiles);
        }
        ensureUploadCapacity(requiredBytes);
        uploadBuffer.clear();
        uploadBuffer.put(data, 0, requiredBytes);
        uploadBuffer.flip();
        uploadRegion(textureId, 0, 0, widthTiles, heightTiles, uploadBuffer);
    }

    /** Stages tightly packed RGBA8 texels and copies them into the texture on the GPU. */
    private void uploadRegion(int texture, int x, int y, int width, int height, ByteBuffer texels) {
        try {
            long offset = unpackStream.stage(texels);
            glBindTexture(GL_TEXTURE_2D, texture);
            glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, width, height,
                    GL_RGBA, GL_UNSIGNED_BYTE, offset);
        } finally {
            // Every other upload in the engine reads client memory.
            glBindTexture(GL_TEXTURE_2D, 0);
            PixelUnpackStream.unbind();
        }
    }

    private final class UnpackBufferUploadOps implements TilemapTextureUploadOps {
        @Override
        public void uploadColumns(int texture, int destinationColumn, int columnCount,
                int rows, ByteBuffer packedRows) {
            uploadRegion(texture, destinationColumn, 0, columnCount, rows, packedRows);
        }

        @Override
        public void uploadRows(int texture, int destinationRow, int columns,
                int rowCount, ByteBuffer contiguousRows) {
            uploadRegion(texture, 0, destinationRow, columns, rowCount, contiguousRows);
        }
    }

    /**
     * Uploads a contiguous run of logical source columns into a physical texture
     * column. Rows are packed into caller-independent staging because the source
     * array retains the full tilemap row stride.
     */
    public boolean uploadColumns(byte[] data, int sourceWidthTiles, int heightTiles,
            int sourceColumn, int destinationColumn, int columnCount) {
        int requiredSourceBytes = checkedTilemapByteCount(sourceWidthTiles, heightTiles);
        int uploadBytes = checkedTilemapByteCount(columnCount, heightTiles);
        if (data == null || sourceWidthTiles <= 0 || heightTiles <= 0 || columnCount <= 0
                || sourceColumn < 0 || columnCount > sourceWidthTiles
                || sourceColumn > sourceWidthTiles - columnCount
                || destinationColumn < 0 || destinationColumn > sourceWidthTiles - columnCount
                || requiredSourceBytes < 0 || requiredSourceBytes > data.length
                || uploadBytes < 0
                || !hasStorage(sourceWidthTiles, heightTiles)) {
            return false;
        }
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

    private void uploadSubImage(int destinationColumn, int columnCount,
            int heightTiles, ByteBuffer packedRows) {
        uploadOps.uploadColumns(textureId, destinationColumn, columnCount, heightTiles, packedRows);
    }

    /** Uploads contiguous full-width logical rows into physical texture rows. */
    public boolean uploadRows(byte[] data, int widthTiles, int sourceHeightTiles,
            int sourceRow, int destinationRow, int rowCount) {
        int requiredSourceBytes = checkedTilemapByteCount(widthTiles, sourceHeightTiles);
        int uploadBytes = checkedTilemapByteCount(widthTiles, rowCount);
        if (data == null || widthTiles <= 0 || sourceHeightTiles <= 0 || rowCount <= 0
                || sourceRow < 0 || rowCount > sourceHeightTiles
                || sourceRow > sourceHeightTiles - rowCount
                || destinationRow < 0 || destinationRow > sourceHeightTiles - rowCount
                || requiredSourceBytes < 0 || requiredSourceBytes > data.length
                || uploadBytes < 0
                || !hasStorage(widthTiles, sourceHeightTiles)) {
            return false;
        }
        int sourceOffset = sourceRow * widthTiles * 4;
        ensureUploadCapacity(uploadBytes);
        uploadBuffer.clear();
        uploadBuffer.put(data, sourceOffset, uploadBytes);
        uploadBuffer.flip();
        uploadRowsSubImage(destinationRow, widthTiles, rowCount, uploadBuffer);
        return true;
    }

    private void uploadRowsSubImage(int destinationRow, int widthTiles,
            int rowCount, ByteBuffer contiguousRows) {
        uploadOps.uploadRows(textureId, destinationRow, widthTiles, rowCount, contiguousRows);
    }

    void setUploadOps(TilemapTextureUploadOps uploadOps) {
        this.uploadOps = Objects.requireNonNull(uploadOps);
    }

    public boolean hasStorage(int widthTiles, int heightTiles) {
        return textureId != 0 && this.widthTiles == widthTiles && this.heightTiles == heightTiles;
    }

    private static int checkedTilemapByteCount(int widthTiles, int heightTiles) {
        if (widthTiles <= 0 || heightTiles <= 0) {
            return -1;
        }
        try {
            return Math.multiplyExact(Math.multiplyExact(widthTiles, heightTiles), 4);
        } catch (ArithmeticException overflow) {
            return -1;
        }
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
        unpackStream.delete();
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
