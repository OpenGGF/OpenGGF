package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One storage owner for whole-pool slots 1..31, retaining all 64 bytes of every slot.
 * Primary slot 0 belongs to the player frame; dynamic slots 32..127 belong to CdObjectPool.
 * No allocation, object execution, media admission or general RAM lookup occurs here.
 *
 * <p>Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Level/_Definitions.inc:oSize (25), objects/resObjects..dynObjects (119-171);
 * _Include/Main CPU.inc:WORKRAM (38). Normalized 24-bit object RAM is $FFD000..$FFEFFF.
 * LevelStart/.ClearObjects (Level/Main.asm:186-192) writes 2048 zero longwords there.
 * This bank owns only $FFD040..$FFD7FF; it never supplies a second primary/dynamic owner.
 *
 * <p>A runtime must retain one bank and capture/restore it once in its reserved partition.
 * Images and snapshots are immutable values associated with an opaque owner token, not extra
 * live stores. All mutation is for that single runtime owner; compound publication uses the
 * full-bank value guard below, with no revision or native-execution authority implied.
 *
 * <p>The optional raw-backed facade owns no live bytes or Image array. Its actual external owner
 * remains responsible for initialization, private operation/lifetime admission and capture/restore.
 * Facade snapshots are derived immutable values and must not be registered as a second capture partition.
 */
public final class CdReservedObjectBank {
    public static final int FIRST_WHOLE_INDEX = 1;
    public static final int END_WHOLE_INDEX = 32;
    public static final int RESERVED_COUNT = 31;
    public static final int BYTE_SIZE = 0x40;

    /** Finite physical identity, independent of an object ID, placement row or saved ID. */
    public record Identity(int wholeIndex) {
        public Identity { reservedIndex(wholeIndex); }
        public int nativeAddress24() { return 0xFFD000 + wholeIndex * BYTE_SIZE; }
    }

    /**
     * Immutable image of one reserved slot. CdObjectState field enums supply offsets only;
     * their dynamic identities and object-specific meanings do not become reserved/player rules.
     * Raw offsets are bounded storage access, including unnamed player-2 aliases and odd offsets;
     * accepting an offset does not admit a CPU operation or an object's behavior.
     */
    public static final class Image {
        private final Object owner;
        private final Identity identity;
        private final byte[] bytes;

        private Image(Object owner, Identity identity, byte[] ownedBytes) {
            this.owner = owner;
            this.identity = identity;
            this.bytes = ownedBytes;
        }

        public Identity identity() { return identity; }
        public byte[] copyBytes() { return bytes.clone(); }

        /** Replace exactly this image's 64 storage values, copying the caller's buffer. No live write. */
        public Image withBytes(byte[] values) {
            Objects.requireNonNull(values, "reserved image bytes");
            if (values.length != BYTE_SIZE) {
                throw new IllegalArgumentException("CD reserved image must contain exactly 64 bytes");
            }
            return new Image(owner, identity, values.clone());
        }

        public int unsignedByte(CdObjectState.ByteField field) {
            return byteAt(Objects.requireNonNull(field, "byte field").byteOffset());
        }

        public byte signedByte(CdObjectState.ByteField field) { return (byte) unsignedByte(field); }

        public int word(CdObjectState.WordField field) {
            return wordAt(Objects.requireNonNull(field, "word field").byteOffset());
        }

        public short signedWord(CdObjectState.WordField field) { return (short) word(field); }

        public int longBits(CdObjectState.LongField field) {
            return longBitsAt(Objects.requireNonNull(field, "long field").byteOffset());
        }

        /** Unsigned byte at an offset in this image only. */
        public int byteAt(int offset) { return Byte.toUnsignedInt(bytes[checkOffset(offset, 1)]); }

        /** Unsigned big-endian word; both bytes must belong to this image. */
        public int wordAt(int offset) { return readWord(checkOffset(offset, 2)); }

        /** Big-endian 32-bit pattern; all four bytes must belong to this image. */
        public int longBitsAt(int offset) {
            int at = checkOffset(offset, 4);
            return (readWord(at) << 16) | readWord(at + 2);
        }

        public Image moveByte(CdObjectState.ByteField field, int value) {
            return moveByteAt(Objects.requireNonNull(field, "byte field").byteOffset(), value);
        }

        public Image moveWord(CdObjectState.WordField field, int value) {
            return moveWordAt(Objects.requireNonNull(field, "word field").byteOffset(), value);
        }

        public Image moveLong(CdObjectState.LongField field, int bits) {
            return moveLongAt(Objects.requireNonNull(field, "long field").byteOffset(), bits);
        }

        /** MOVE.B storage width: truncate the value, retain all other bytes, and publish nothing. */
        public Image moveByteAt(int offset, int value) {
            int at = checkOffset(offset, 1);
            byte[] next = bytes.clone();
            next[at] = (byte) value;
            return new Image(owner, identity, next);
        }

        /** MOVE.W storage width: big-endian, retaining position fractions and all other bytes. */
        public Image moveWordAt(int offset, int value) {
            int at = checkOffset(offset, 2);
            byte[] next = bytes.clone();
            putWord(next, at, value);
            return new Image(owner, identity, next);
        }

        /** MOVE.L storage width, sharing the same bytes as all overlapping byte/word views. */
        public Image moveLongAt(int offset, int bits) {
            int at = checkOffset(offset, 4);
            byte[] next = bytes.clone();
            putWord(next, at, bits >>> 16);
            putWord(next, at + 2, bits);
            return new Image(owner, identity, next);
        }

        private static int checkOffset(int offset, int width) {
            return Objects.checkFromIndexSize(offset, width, BYTE_SIZE);
        }

        private int readWord(int at) {
            return (Byte.toUnsignedInt(bytes[at]) << 8) | Byte.toUnsignedInt(bytes[at + 1]);
        }

        private static void putWord(byte[] destination, int at, int value) {
            destination[at] = (byte) (value >>> 8);
            destination[at + 1] = (byte) value;
        }

        /** Same bank token, same physical slot and all 64 equal values; reference identity is irrelevant. */
        @Override public boolean equals(Object other) {
            return other instanceof Image image && owner == image.owner
                    && identity.equals(image.identity) && Arrays.equals(bytes, image.bytes);
        }

        @Override public int hashCode() {
            return 31 * (31 * System.identityHashCode(owner) + identity.hashCode()) + Arrays.hashCode(bytes);
        }
    }

    /** Immutable ordered cut of all 31 images, holding a token rather than a reference to the live bank. */
    public static final class Snapshot {
        private final Object owner;
        private final List<Image> slots;

        private Snapshot(Object owner, List<Image> slots) {
            this.owner = owner;
            this.slots = List.copyOf(slots);
        }

        public List<Image> slots() { return slots; }
    }

    /**
     * Borrow the actual immutable31*64-byte owner value and delegate a complete prebuilt value CAS.
     * The backing owns native invocation/source/phase/exact-once admission. A boolean/equal value
     * never supplies those receipts. Reads must return exactly1984 bytes; no default or live seed exists.
     * A false CAS publishes none of the requested value; a thrown failure may retain the backing's
     * genuinely reached prefix. This facade never retries, rolls back or wraps that original failure.
     */
    public interface RawBacking {
        CdAssetBytes read();
        boolean compareAndSet(CdAssetBytes expected, CdAssetBytes next);
    }

    private static final int RAW_BYTE_SIZE = RESERVED_COUNT * BYTE_SIZE;
    private final Object owner = new Object();
    private final Image[] slots;
    private final RawBacking backing;

    private CdReservedObjectBank() {
        slots = new Image[RESERVED_COUNT];
        backing = null;
        for (int whole = FIRST_WHOLE_INDEX; whole < END_WHOLE_INDEX; whole++) {
            slots[reservedIndex(whole)] = new Image(owner, new Identity(whole), new byte[BYTE_SIZE]);
        }
    }

    /**
     * Explicit immediate ClearObjects cut only (Level/Main.asm:186-192), not unknown/live RAM seeding.
     * The future runtime caller must admit that native initialization boundary and apply subsequent
     * LevelStart writes, including HUD/title initialization (:261-274), in their owning order.
     * This package-private factory itself authenticates no execution or media.
     */
    static CdReservedObjectBank clearedAfterLevelStart() { return new CdReservedObjectBank(); }

    private CdReservedObjectBank(RawBacking backing) {
        this.backing = Objects.requireNonNull(backing, "actual reserved raw backing");
        slots = null;
    }

    /** No factory-time read, clear, import, image mirror or additional capture owner. */
    public static CdReservedObjectBank backedBy(RawBacking backing) { return new CdReservedObjectBank(backing); }

    public Image state(int wholeIndex) {
        int index = reservedIndex(wholeIndex);
        if (backing != null) return rawImage(readRaw(), index);
        return slots[index];
    }

    /** Publish one same-bank immutable image to its unchanged physical identity. */
    public void write(Image image) {
        validateImage(image);
        if (backing != null) {
            var expected = readRaw();
            byte[] bytes = expected.copy();
            System.arraycopy(image.bytes, 0, bytes, reservedIndex(image.identity.wholeIndex()) * BYTE_SIZE, BYTE_SIZE);
            var next = CdAssetBytes.copyOf(bytes);
            if (!compareRaw(expected, next)) throw new IllegalStateException("Actual reserved raw owner rejected write CAS");
            return;
        }
        slots[reservedIndex(image.identity.wholeIndex())] = image;
    }

    public Snapshot capture() {
        if (backing != null) {
            var raw = readRaw();
            var images = new ArrayList<Image>(RESERVED_COUNT);
            for (int index = 0; index < RESERVED_COUNT; index++) images.add(rawImage(raw, index));
            return new Snapshot(owner, images);
        }
        var images = new ArrayList<Image>(RESERVED_COUNT);
        for (Image image : slots) images.add(image);
        return new Snapshot(owner, images);
    }

    /** Pure complete preflight, allowing a composite runtime to validate every partition before restoring. */
    public void validateRestore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "reserved snapshot");
        if (snapshot.owner != owner) {
            throw new IllegalArgumentException("Foreign CD reserved bank capture");
        }
        if (snapshot.slots.size() != RESERVED_COUNT) {
            throw new IllegalArgumentException("CD reserved capture must contain all 31 images");
        }
        for (int i = 0; i < RESERVED_COUNT; i++) {
            Image image = snapshot.slots.get(i);
            validateImage(image);
            if (image.identity.wholeIndex() != i + FIRST_WHOLE_INDEX) {
                throw new IllegalArgumentException("CD reserved capture must retain complete physical order");
            }
        }
    }

    public void restore(Snapshot snapshot) {
        validateRestore(snapshot);
        if (backing != null) {
            var expected = readRaw();
            var next = snapshotBytes(snapshot);
            if (!compareRaw(expected, next)) throw new IllegalStateException("Actual reserved raw owner rejected restore CAS");
            return;
        }
        for (int i = 0; i < RESERVED_COUNT; i++) slots[i] = snapshot.slots.get(i);
    }

    /**
     * Validate the expected full cut and every supplied image before comparing or writing.
     * Images may be supplied in any order, at most once per physical slot. Invalid inputs throw;
     * a valid expected cut with any changed live slot returns false without a write, including
     * when the replacement list is empty or only an unselected slot changed. Equal reconstructed
     * or restored values match: this is full-bank same-owner/value CAS, with no ABA/version guard.
     */
    public boolean compareAndSet(Snapshot expected, List<Image> images) {
        validateRestore(expected);
        List<Image> replacements = List.copyOf(Objects.requireNonNull(images, "reserved replacement images"));
        boolean[] seen = new boolean[RESERVED_COUNT];
        for (Image image : replacements) {
            validateImage(image);
            int index = reservedIndex(image.identity.wholeIndex());
            if (seen[index]) throw new IllegalArgumentException("Duplicate CD reserved physical index");
            seen[index] = true;
        }
        if (backing != null) {
            var actual = readRaw();
            if (!Arrays.equals(actual.copy(), snapshotBytes(expected).copy())) return false;
            byte[] bytes = actual.copy();
            for (Image image : replacements) {
                System.arraycopy(image.bytes, 0, bytes, reservedIndex(image.identity.wholeIndex()) * BYTE_SIZE, BYTE_SIZE);
            }
            return compareRaw(actual, CdAssetBytes.copyOf(bytes));
        }
        for (int i = 0; i < RESERVED_COUNT; i++) {
            if (!slots[i].equals(expected.slots.get(i))) return false;
        }
        for (Image image : replacements) slots[reservedIndex(image.identity.wholeIndex())] = image;
        return true;
    }

    private CdAssetBytes readRaw() {
        var raw = Objects.requireNonNull(backing.read(), "actual reserved raw value");
        if (raw.length() != RAW_BYTE_SIZE) throw new IllegalArgumentException("Actual reserved raw value must contain exactly1984 bytes");
        return raw;
    }

    private Image rawImage(CdAssetBytes raw, int index) {
        return new Image(owner, new Identity(index + FIRST_WHOLE_INDEX), raw.slice(index * BYTE_SIZE, BYTE_SIZE).copy());
    }

    /** Caller has already validated every same-owner image before constructing the complete value. */
    private static CdAssetBytes snapshotBytes(Snapshot snapshot) {
        byte[] bytes = new byte[RAW_BYTE_SIZE];
        for (int index = 0; index < RESERVED_COUNT; index++) {
            System.arraycopy(snapshot.slots.get(index).bytes, 0, bytes, index * BYTE_SIZE, BYTE_SIZE);
        }
        return CdAssetBytes.copyOf(bytes);
    }

    /** Successful-return value verification only; same-value/no-op native receipts remain with the backing. */
    private boolean compareRaw(CdAssetBytes expected, CdAssetBytes next) {
        if (!backing.compareAndSet(expected, next)) return false;
        if (!Arrays.equals(readRaw().copy(), next.copy()))
            throw new IllegalStateException("Actual reserved raw owner did not adopt the complete requested value");
        return true;
    }

    private void validateImage(Image image) {
        Objects.requireNonNull(image, "reserved image");
        if (image.owner != owner) throw new IllegalArgumentException("Foreign CD reserved image owner");
        reservedIndex(image.identity.wholeIndex());
        if (image.bytes.length != BYTE_SIZE) {
            throw new IllegalArgumentException("CD reserved image must contain exactly 64 bytes");
        }
    }

    private static int reservedIndex(int wholeIndex) {
        return Objects.checkIndex(wholeIndex - FIRST_WHOLE_INDEX, RESERVED_COUNT);
    }
}
