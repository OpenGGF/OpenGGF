package com.openggf.game.soniccd.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;

/**
 * Immutable file bytes and the file-backed portions of RunMMD's two address domains.
 * This view neither executes code nor supplies pre-existing Word RAM beyond EOF.
 */
public final class CdMmdImage {
    public enum Domain { ORIGINAL_WORD_RAM, RELOCATED_COPY }
    public record FileSlice(int offset, int length) {
        public FileSlice {
            if (offset < 0 || length < 0) {
                throw new IllegalArgumentException("Negative file slice");
            }
        }
    }
    public record NativeRange(Domain domain, int address, int length, FileSlice fileSlice) { }

    private final byte[] bytes;
    private final CdMmdHeader header;

    private CdMmdImage(byte[] bytes, CdMmdHeader header) {
        this.bytes = bytes;
        this.header = header;
    }

    public static CdMmdImage parse(byte[] file) throws IOException {
        if (file.length > CdMmdHeader.WORD_RAM_BYTES) {
            throw new IOException("MMD file exceeds the original 2M Word RAM image");
        }
        byte[] frozen = file.clone();
        CdMmdHeader header = CdMmdHeader.parse(frozen);
        int copyBytes = header.declaredCopyBytes();
        if (copyBytes > CdMmdHeader.WORD_RAM_BYTES - CdMmdHeader.HEADER_BYTES) {
            throw new IOException("MMD copy source exceeds 2M Word RAM");
        }
        if (copyBytes > CdMmdHeader.ADDRESS_SPACE_BYTES - CdMmdHeader.nativeAddress(header.origin())) {
            throw new IOException("MMD copy wraps the 24-bit native address space");
        }
        return new CdMmdImage(frozen, header);
    }

    public CdMmdHeader header() { return header; }
    public int fileBytes() { return bytes.length; }
    public int fileBackedCopyBytes() {
        return Math.min(header.declaredCopyBytes(), bytes.length - CdMmdHeader.HEADER_BYTES);
    }
    public int unresolvedCopyBytes() { return header.declaredCopyBytes() - fileBackedCopyBytes(); }

    public NativeRange originalWordRam() {
        return new NativeRange(Domain.ORIGINAL_WORD_RAM, CdMmdHeader.WORD_RAM_ADDRESS,
                bytes.length, new FileSlice(0, bytes.length));
    }

    /** The original Word RAM image survives relocation. Only the file-backed copy is mapped. */
    public Optional<NativeRange> relocatedCopy() {
        return header.declaredCopyBytes() == 0 ? Optional.empty()
                : Optional.of(new NativeRange(Domain.RELOCATED_COPY,
                CdMmdHeader.nativeAddress(header.origin()), fileBackedCopyBytes(),
                new FileSlice(CdMmdHeader.HEADER_BYTES, fileBackedCopyBytes())));
    }

    public ByteBuffer fileSlice(long offset, int length) {
        checkRange(offset, length, bytes.length);
        return ByteBuffer.wrap(bytes, (int) offset, length).slice().asReadOnlyBuffer();
    }

    public ByteBuffer nativeSlice(Domain domain, int address, int length) {
        if (address < 0 || address >= CdMmdHeader.ADDRESS_SPACE_BYTES) {
            throw new IllegalArgumentException("Native address must be 24-bit");
        }
        NativeRange range = switch (domain) {
            case ORIGINAL_WORD_RAM -> originalWordRam();
            case RELOCATED_COPY -> relocatedCopy().orElseThrow(
                    () -> new IllegalArgumentException("MMD has no relocated copy"));
        };
        long relative = (long) address - range.address();
        checkRange(relative, length, range.length());
        return fileSlice(range.fileSlice().offset() + relative, length);
    }

    public byte[] readFile(long offset, int length) {
        checkRange(offset, length, bytes.length);
        return Arrays.copyOfRange(bytes, (int) offset, (int) offset + length);
    }

    static void checkRange(long offset, long length, long size) {
        if (offset < 0 || length < 0 || offset > size || length > size - offset) {
            throw new IllegalArgumentException("Slice outside file-backed extent");
        }
    }
}
