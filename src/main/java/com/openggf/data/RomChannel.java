package com.openggf.data;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

/**
 * Positioned read channels over a {@link Rom}'s in-memory bytes.
 *
 * <p>The compression readers ({@code NemesisReader}, {@code KosinskiReader},
 * {@code EnigmaReader}) consume a {@link java.nio.channels.ReadableByteChannel}.
 * Gameplay code used to hand them {@link Rom#getFileChannel()} after moving
 * its shared position, which required a lock around every decompression and
 * cannot work for catalogue-served ROMs that have no file. A channel from
 * {@link #at(Rom, long)} is private to its caller, starts at the requested
 * address, and reads from the ROM's immutable byte snapshot, so it needs no
 * lock and works for file-backed and in-memory ROMs alike.
 *
 * <p>Not part of the Mod API: mods read through {@link RomByteReader}.
 */
public final class RomChannel {

    private RomChannel() {
    }

    /**
     * Opens a read-only channel positioned at {@code offset} over the ROM's
     * bytes. {@link SeekableByteChannel#position()} reports the absolute ROM
     * address, so a caller can measure how many compressed bytes a reader
     * consumed.
     */
    public static SeekableByteChannel at(Rom rom, long offset) throws IOException {
        return at(RomByteReader.fromRom(Objects.requireNonNull(rom, "rom")), offset);
    }

    /** Opens a read-only channel positioned at {@code offset} over a reader. */
    public static SeekableByteChannel at(RomByteReader reader, long offset) throws IOException {
        Objects.requireNonNull(reader, "reader");
        if (offset < 0 || offset > reader.size()) {
            throw new IOException(String.format("ROM channel offset out of bounds: 0x%X size=0x%X",
                    offset, reader.size()));
        }
        return new ReaderChannel(reader, (int) offset);
    }

    private static final class ReaderChannel implements SeekableByteChannel {
        private final RomByteReader reader;
        private int position;
        private boolean open = true;

        ReaderChannel(RomByteReader reader, int position) {
            this.reader = reader;
            this.position = position;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException {
            ensureOpen();
            int remaining = reader.size() - position;
            if (remaining <= 0) {
                return -1;
            }
            int count = Math.min(dst.remaining(), remaining);
            if (count == 0) {
                return 0;
            }
            if (dst.hasArray()) {
                reader.copyTo(position, dst.array(), dst.arrayOffset() + dst.position(), count);
                dst.position(dst.position() + count);
            } else {
                dst.put(reader.slice(position, count));
            }
            position += count;
            return count;
        }

        @Override
        public int write(ByteBuffer src) {
            throw new NonWritableChannelException();
        }

        @Override
        public long position() throws IOException {
            ensureOpen();
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            ensureOpen();
            if (newPosition < 0 || newPosition > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Position out of range: " + newPosition);
            }
            position = (int) newPosition;
            return this;
        }

        @Override
        public long size() throws IOException {
            ensureOpen();
            return reader.size();
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new NonWritableChannelException();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        private void ensureOpen() throws IOException {
            if (!open) {
                throw new ClosedChannelException();
            }
        }
    }
}
