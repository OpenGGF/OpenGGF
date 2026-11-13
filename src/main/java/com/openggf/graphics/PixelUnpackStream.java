package com.openggf.graphics;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.opengl.GL21.GL_PIXEL_UNPACK_BUFFER;

/**
 * Append-only pixel-unpack buffer that lets texture uploads return without
 * waiting for the GL driver to drain its queue.
 *
 * <p>{@code glTexSubImage2D} from client memory must finish reading the caller's
 * pointer before it returns. Under Mesa (glthread plus the threaded radeonsi
 * driver) that makes the frame thread wait until the driver threads have
 * executed everything queued before the upload, and on an integrated GPU shared
 * with the desktop that wait reached 102 ms for a 2.5 KB atlas update (S3K AIZ
 * frame 1502) and 701 ms for 6.6 KB (HCZ 26161). Thread wait channels showed the
 * frame thread in {@code futex_wait} while the driver thread ran, and putting a
 * {@code glFinish} first moved the time into the finish and left even a 1 MiB
 * upload at 0.1 ms: the cost was the wait, not the bytes or a format conversion.
 * Sourcing the upload from a buffer object lets glthread queue it and the driver
 * copy on the GPU, ordered after the earlier draws, so the picture is unchanged.
 *
 * <p>Each {@link #stage} appends at a fresh offset, so it never overwrites a
 * range a queued copy may still read; when the buffer is full it is orphaned
 * with {@code glBufferData(null)} and the driver supplies new storage. This is
 * the GL 2.1 streaming idiom: the engine's GL 4.1 floor (macOS) rules out
 * persistent mapping, and {@code glMapBufferRange} would make glthread
 * synchronise again, whereas {@code glBufferSubData} is queued.
 *
 * <p>{@link #stage} leaves the buffer bound to {@code GL_PIXEL_UNPACK_BUFFER};
 * the caller issues its uploads with the returned offset and then calls
 * {@link #unbind()}, because every other upload in the engine reads client
 * memory and assumes no unpack buffer is bound. Construction touches no GL.
 */
final class PixelUnpackStream {
    /** Keeps each staged region on a boundary every driver's copy path accepts. */
    static final int OFFSET_ALIGNMENT = 256;

    private final int minimumCapacity;
    private int buffer;
    private long capacity;
    private long cursor;

    PixelUnpackStream(int minimumCapacity) {
        if (minimumCapacity <= 0) {
            throw new IllegalArgumentException("minimumCapacity must be positive: " + minimumCapacity);
        }
        this.minimumCapacity = minimumCapacity;
    }

    /**
     * Copies {@code data}'s remaining bytes into the buffer, binds it to
     * {@code GL_PIXEL_UNPACK_BUFFER} and returns the byte offset that upload
     * calls pass in place of a client pointer.
     */
    long stage(ByteBuffer data) {
        int length = data.remaining();
        if (buffer == 0) {
            buffer = glGenBuffers();
        }
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, buffer);
        long offset = reserve(length);
        if (offset < 0) {
            capacity = Math.max(capacity, Math.max(minimumCapacity, alignUp(length)));
            glBufferData(GL_PIXEL_UNPACK_BUFFER, capacity, GL_STREAM_DRAW);
            cursor = 0;
            offset = reserve(length);
        }
        glBufferSubData(GL_PIXEL_UNPACK_BUFFER, offset, data);
        return offset;
    }

    /** Next offset for {@code length} bytes in the current storage, or -1 when it must be orphaned. */
    long reserve(int length) {
        if (length > capacity - cursor) {
            return -1;
        }
        long offset = cursor;
        cursor = Math.min(capacity, alignUp(cursor + length));
        return offset;
    }

    static void unbind() {
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
    }

    /** Deletes the buffer. The stream can be reused afterwards with a new buffer. */
    void delete() {
        if (buffer != 0) {
            glDeleteBuffers(buffer);
        }
        buffer = 0;
        capacity = 0;
        cursor = 0;
    }

    long capacity() {
        return capacity;
    }

    private static long alignUp(long value) {
        return (value + OFFSET_ALIGNMENT - 1) & -(long) OFFSET_ALIGNMENT;
    }
}
