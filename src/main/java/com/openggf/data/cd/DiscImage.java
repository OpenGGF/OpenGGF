package com.openggf.data.cd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Read-only, positional access to a user-supplied CD image, whatever its container.
 * Every implementation exposes the same CUE-equivalent geometry ({@link DiscTrack}): stored and synthetic
 * gaps stay distinct, frame zero of a track is its INDEX 01, and audio frames are returned in Red Book
 * (little-endian) byte order, exactly as a BINARY CUE file stores them. Content identity is a separate concern.
 */
public interface DiscImage extends AutoCloseable {
    /** Bound on one read, shared by every container. */
    int MAX_READ_BYTES = CueDiscImage.MAX_READ_BYTES;

    /** Opens a CUE sheet or a CHD by content: a file starting with the CHD tag is a CHD, anything else a CUE. */
    static DiscImage open(Path path) throws IOException {
        Objects.requireNonNull(path);
        byte[] tag;
        try (InputStream input = Files.newInputStream(path)) {
            tag = input.readNBytes(ChdDiscImage.TAG.length);
        }
        return Arrays.equals(tag, ChdDiscImage.TAG) ? ChdDiscImage.open(path) : CueDiscImage.open(path);
    }

    List<DiscTrack> tracks();

    DiscTrack track(int number);

    /** Canonical descriptor selected at open: the CUE sheet, or the CHD file itself. */
    Path descriptorPath() throws IOException;

    /**
     * Exact bounded layout bytes parsed at open, detached from the owner: the CUE text, or a CHD's
     * track metadata entries in file order.
     */
    byte[] readDescriptorBytes() throws IOException;

    /** Rejects a redundant descriptor argument that no longer identifies this owner's canonical descriptor. */
    void requireDescriptor(Path descriptor) throws IOException;

    /** Whole stored files in declaration order, each read through this disc's retained handle. */
    List<? extends SourceFile> sourceFiles() throws IOException;

    /** Reads INDEX01-relative MODE1 user data, stripping raw sync/header/ECC bytes. */
    byte[] readMode1Bytes(int number, long byteOffset, int length) throws IOException;

    /** Returns CDDA bytes in Red Book little-endian order. Negative frames address the pregap. */
    byte[] readAudioFrames(int number, long firstFrame, int frameCount) throws IOException;

    /** Returns stored frames. Synthetic AUDIO pregaps are silence; raw data gaps are not invented. */
    byte[] readRawFrames(int number, long firstFrame, int frameCount) throws IOException;

    /** Fails when the owner is closed or the track's backing file changed since open. */
    void checkTrack(int number) throws IOException;

    @Override
    void close() throws IOException;

    /** A whole stored file bound to the disc's lifecycle. */
    interface SourceFile {
        Path path();

        long bytes();

        byte[] read(long offset, int length) throws IOException;
    }
}
