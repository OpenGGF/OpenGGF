package com.openggf.game.sonic3k.render;

import java.util.Arrays;
import java.util.Objects;

/**
 * Persistent physical SST bytes, independently of Java owners or
 * allocation. Cold R79; native044 sonic3k.constants.asm:9..17,113,307..330:
 * Object_RAM is110 entries of$4A at the physical word address$B000.
 *
 * <p>Delete_Current/Referenced_Sprite clears exactly$4A bytes
 * (sonic3k.asm:36148..36165). loc_60DE clears a range containing Object_RAM
 * (:7652..7656). Allocation (:37946..37975) reads code without clearing residue;
 * SetUp_ObjAttributes (:176987..177005) and other partial writes leave all
 * unwritten bytes intact. There is deliberately no occupancy/free callback.
 *
 * <p>Source labels describe the caller's declared native writer, not proof that
 * a gameplay branch ran. The current world adapter captures clear-owned DATA;
 * production integration covers only the actual fresh-Level clear. Ordinary
 * object/header writes, slot retirement, Draw_Sprite/camera/piece-tail producers
 * and render publication remain unintegrated. These bytes are not a certificate
 * of then-current native gameplay RAM. Raw
 * snapshot bytes MUST be interpreted with their knownness, never as
 * an implicit cleared header. Nothing here hydrates state from a fixture.
 */
final class S3kPhysicalSstHeaderBank implements S3kPhysicalDisplayQueue.Projection {
    private static final int BASE = 0xB000;
    private static final int SLOT_BYTES = 0x4A;
    private static final int SLOTS = 110;
    private static final int BYTES = SLOTS * SLOT_BYTES;

    private final byte[] bytes = new byte[BYTES];
    private final boolean[] known = new boolean[BYTES];
    private final Provenance[] provenance = new Provenance[BYTES];

    enum WriteKind {
        LEVEL_CLEAR, SPRITE_CLEAR, PARTIAL_BYTE, PARTIAL_WORD, PARTIAL_LONG, RENDER_PUBLICATION
    }

    /** Immutable per-byte origin; caller labels never authenticate a native call. */
    record Provenance(WriteKind kind, String nativeWriter) {
        Provenance {
            Objects.requireNonNull(kind);
            if (Objects.requireNonNull(nativeWriter).isBlank()) {
                throw new IllegalArgumentException("A declared native writer is required");
            }
        }
    }

    private static final Provenance LEVEL_CLEAR = new Provenance(
            WriteKind.LEVEL_CLEAR, "native044 loc_60DE clearRAM Object_RAM range");
    private static final Provenance SPRITE_CLEAR = new Provenance(
            WriteKind.SPRITE_CLEAR, "native044 Delete_Current/Referenced_Sprite full74-byte clear");
    private static final Provenance PUBLICATION = new Provenance(
            WriteKind.RENDER_PUBLICATION, "native044 Render_Sprites loc_1AD54/loc_1ADB2 flags byte");

    /** Explicit actual level clear of this bank's covered range, not other RAM. */
    void clearLevelObjectRam() {
        clear(0, BYTES, LEVEL_CLEAR);
    }

    /** Explicit native full-slot clear, never inferred from code0 or a free slot. */
    void clearSprite(int addressWord) {
        clear(index(addressWord, 0, SLOT_BYTES), SLOT_BYTES, SPRITE_CLEAR);
    }

    /**
     * All seven bytes required by the existing normal Header contract must be
     * known BEFORE the queue can perform its first flag clear. No owner lookup,
     * liveness test, geometry query, lazy fill or code0 special case is involved.
     */
    @Override
    public S3kPhysicalDisplayQueue.Header read(int addressWord) {
        int flags = readByte(addressWord, 4);
        int height = readByte(addressWord, 6);
        int width = readByte(addressWord, 7);
        int x = readWord(addressWord, 0x10);
        int y = readWord(addressWord, 0x14);
        return new S3kPhysicalDisplayQueue.Header(flags, x, y, width, height);
    }

    /** Exact then-current raw byte writeback; bit operations belong to the queue. */
    @Override
    public void writeRenderFlags(int addressWord, int renderFlags) {
        read(addressWord); // Fail qualification before mutation even for a direct writeback call.
        requireUnsigned(renderFlags, 0xFF);
        store(index(addressWord, 4, 1), 1, renderFlags, PUBLICATION);
    }

    boolean isKnown(int addressWord, int offset) {
        return known[index(addressWord, offset, 1)];
    }

    Provenance origin(int addressWord, int offset) {
        int i = index(addressWord, offset, 1);
        requireKnown(i, 1);
        return provenance[i];
    }

    int readByte(int addressWord, int offset) {
        return (int) readValue(addressWord, offset, 1);
    }

    int readWord(int addressWord, int offset) {
        return (int) readValue(addressWord, offset, 2);
    }

    long readLong(int addressWord, int offset) {
        return readValue(addressWord, offset, 4);
    }

    // Native fields are separate reads: a known publication header does not
    // imply known code, mappings, frame, priority, art or position fractions.
    long code(int addressWord) { return readLong(addressWord, 0); }
    int priority(int addressWord) { return readWord(addressWord, 8); }
    int artTile(int addressWord) { return readWord(addressWord, 0xA); }
    long mappings(int addressWord) { return readLong(addressWord, 0xC); }
    int xFraction(int addressWord) { return readWord(addressWord, 0x12); }
    int yFraction(int addressWord) { return readWord(addressWord, 0x16); }
    int mappingFrame(int addressWord) { return readByte(addressWord, 0x22); }

    /** Raw written bits are supplied unsigned; a future CPU adapter must narrow its register explicitly. */
    void writeByte(int addressWord, int offset, int value, String nativeWriter) {
        writeValue(addressWord, offset, 1, value, 0xFF, WriteKind.PARTIAL_BYTE, nativeWriter);
    }

    void writeWord(int addressWord, int offset, int value, String nativeWriter) {
        writeValue(addressWord, offset, 2, value, 0xFFFF, WriteKind.PARTIAL_WORD, nativeWriter);
    }

    void writeLong(int addressWord, int offset, long value, String nativeWriter) {
        writeValue(addressWord, offset, 4, value, 0xFFFFFFFFL, WriteKind.PARTIAL_LONG, nativeWriter);
    }

    /**
     * A real unqualified position writer invalidates only its affected words.
     * Keep the last bytes/origins as historical residue, never readable current
     * gameplay. This is not a clear or an intake of capture/trace values.
     */
    void invalidatePositionField(int addressWord, int offset, int width) {
        if ((offset != 0x10 && offset != 0x12 && offset != 0x14 && offset != 0x16)
                || (width != 2 && width != 4) || (width == 4 && offset != 0x10 && offset != 0x14)) {
            throw new IllegalArgumentException("Expected one native position word/long");
        }
        int first = index(addressWord, offset, width);
        Arrays.fill(known, first, first + width, false);
    }

    /** Unknown actual writer: preserve residue/origin, revoke only the affected field. */
    void invalidateField(int addressWord, int offset, int width) {
        if (width != 1 && width != 2 && width != 4)
            throw new IllegalArgumentException("Expected an actual native field width");
        int first = index(addressWord, offset, width);
        Arrays.fill(known, first, first + width, false);
    }

    Snapshot capture() {
        return new Snapshot(bytes, known, provenance);
    }

    void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        System.arraycopy(snapshot.bytes, 0, bytes, 0, BYTES);
        System.arraycopy(snapshot.known, 0, known, 0, BYTES);
        System.arraycopy(snapshot.provenance, 0, provenance, 0, BYTES);
    }

    /** Ephemeral failed-construction rollback only; never a free/allocation/reset hook. */
    void restoreSlot(int addressWord, Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        int start = index(addressWord, 0, SLOT_BYTES);
        System.arraycopy(snapshot.bytes, start, bytes, start, SLOT_BYTES);
        System.arraycopy(snapshot.known, start, known, start, SLOT_BYTES);
        System.arraycopy(snapshot.provenance, start, provenance, start, SLOT_BYTES);
    }

    /** Defensive internal value only; no registry, public snapshot shape or world-closure claim. */
    static final class Snapshot {
        private final byte[] bytes;
        private final boolean[] known;
        private final Provenance[] provenance;

        private Snapshot(byte[] bytes, boolean[] known, Provenance[] provenance) {
            this.bytes = bytes.clone();
            this.known = known.clone();
            this.provenance = provenance.clone(); // Provenance records contain only immutable values.
        }

        byte[] image() { return bytes.clone(); }
        boolean[] knownBytes() { return known.clone(); }
        Provenance[] provenance() { return provenance.clone(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            return other instanceof Snapshot snapshot
                    && Arrays.equals(bytes, snapshot.bytes)
                    && Arrays.equals(known, snapshot.known)
                    && Arrays.equals(provenance, snapshot.provenance);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(bytes);
            result = 31 * result + Arrays.hashCode(known);
            return 31 * result + Arrays.hashCode(provenance);
        }
    }

    private long readValue(int addressWord, int offset, int width) {
        int i = index(addressWord, offset, width);
        requireKnown(i, width);
        long value = 0;
        for (int n = 0; n < width; n++) {
            value = (value << 8) | (bytes[i + n] & 0xFF);
        }
        return value;
    }

    private void writeValue(int addressWord, int offset, int width, long value, long maximum,
                            WriteKind kind, String nativeWriter) {
        int i = index(addressWord, offset, width);
        requireUnsigned(value, maximum);
        Provenance source = new Provenance(kind, nativeWriter);
        store(i, width, value, source); // All qualifications completed before the first write.
    }

    private void store(int index, int width, long value, Provenance source) {
        for (int n = 0; n < width; n++) {
            bytes[index + n] = (byte) (value >>> ((width - 1 - n) * 8));
            known[index + n] = true;
            provenance[index + n] = source;
        }
    }

    private void clear(int first, int count, Provenance source) {
        Arrays.fill(bytes, first, first + count, (byte) 0);
        Arrays.fill(known, first, first + count, true);
        Arrays.fill(provenance, first, first + count, source);
    }

    private void requireKnown(int first, int width) {
        for (int i = first; i < first + width; i++) {
            if (!known[i]) {
                throw new IllegalStateException("Unknown physical SST byte: "
                        + Integer.toHexString(BASE + i));
            }
        }
    }

    /**
     * MOVEA.W uses an unsigned stored word/sign-extended CPU address. This
     * interface accepts only the unsigned SST base word, not a signed/full
     * address. Word/long accesses require EVEN addresses on68000, not4-alignment,
     * and may not cross the selected$4A-byte slot boundary.
     */
    private static int index(int addressWord, int offset, int width) {
        if (addressWord < BASE || addressWord >= BASE + BYTES
                || (addressWord - BASE) % SLOT_BYTES != 0) {
            throw new IllegalArgumentException("Expected a native110-slot physical SST word");
        }
        if (offset < 0 || offset > SLOT_BYTES - width || (width > 1 && (offset & 1) != 0)) {
            throw new IllegalArgumentException("Expected an in-slot byte or even word/long access");
        }
        return addressWord - BASE + offset;
    }

    private static void requireUnsigned(long value, long maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Expected the unsigned bits of the actual typed write");
        }
    }
}
