package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * The saved bytes and reached, synchronous stores of ObjCheckpoint_SaveData.
 * Native source: N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/_Objects/Checkpoint/Main.asm:18-43. Declarations are in
 * src/_Include/Main CPU Variables.inc:77-96,144 and src/Level/_Definitions.inc.
 * The sole engine leaf dependency is CdAssetBytes from
 * J ea8dabcf67df7d02f7a2a39496ff7cea93b7e8bd.
 *
 * <p>This value helper supplies no cold RAM, live inputs, checkpoint body,
 * restart execution or publication authority. Its caller owns actual adoption
 * of each StoreCall at the reached native instruction. All checks UNRUN by author.
 */
public final class CdCheckpointSaveData {
    private static final int SAVED_BYTE_COUNT = 31;
    private static final int FIVE_MINUTES = 0x00050000;

    private CdCheckpointSaveData() {
    }

    /** Actual source addresses and instruction widths, in bytes. */
    public enum Source {
        SPAWN_MODE(0xFF1522, 1),
        PRIMARY_X(0xFFD008, 2),
        PRIMARY_Y(0xFFD00C, 2),
        WATER_ROUTINE(0xFFF64D, 1),
        BOTTOM_BOUND(0xFFF72E, 2),
        FG_X(0xFFF700, 2),
        FG_Y(0xFFF704, 2),
        BG_X(0xFFF708, 2),
        BG_Y(0xFFF70C, 2),
        BG2_X(0xFFF710, 2),
        BG2_Y(0xFFF714, 2),
        BG3_X(0xFFF718, 2),
        BG3_Y(0xFFF71C, 2),
        WATER_HEIGHT2(0xFFF648, 2),
        WATER_FULLSCREEN(0xFFF64E, 1),
        TIME_LONG(0xFF1514, 4),
        MINI_SONIC(0xFF195B, 1);

        private final int nativeAddress;
        private final int width;

        Source(int nativeAddress, int width) {
            this.nativeAddress = nativeAddress;
            this.width = width;
        }

        public int nativeAddress() {
            return nativeAddress;
        }

        public int width() {
            return width;
        }
    }

    /** The eighteen native stores; repeated writes are distinct instructions. */
    public enum Store {
        SAVED_SPAWN(Source.SPAWN_MODE, 0xFF1523, 1),
        SAVED_X(Source.PRIMARY_X, 0xFF1524, 2),
        SAVED_Y(Source.PRIMARY_Y, 0xFF1526, 2),
        WATER_ROUTINE_FIRST(Source.WATER_ROUTINE, 0xFF1543, 1),
        BOTTOM_BOUND(Source.BOTTOM_BOUND, 0xFF1530, 2),
        FG_X(Source.FG_X, 0xFF1532, 2),
        FG_Y(Source.FG_Y, 0xFF1534, 2),
        BG_X(Source.BG_X, 0xFF1536, 2),
        BG_Y(Source.BG_Y, 0xFF1538, 2),
        BG2_X(Source.BG2_X, 0xFF153A, 2),
        BG2_Y(Source.BG2_Y, 0xFF153C, 2),
        BG3_X(Source.BG3_X, 0xFF153E, 2),
        BG3_Y(Source.BG3_Y, 0xFF1540, 2),
        WATER_HEIGHT_WORD(Source.WATER_HEIGHT2, 0xFF1542, 2),
        WATER_ROUTINE_SECOND(Source.WATER_ROUTINE, 0xFF1543, 1),
        WATER_FULLSCREEN(Source.WATER_FULLSCREEN, 0xFF1544, 1),
        TIME_LONG(Source.TIME_LONG, 0xFF152A, 4),
        MINI_SONIC(Source.MINI_SONIC, 0xFF1591, 1);

        private final Source source;
        private final int nativeAddress;
        private final int width;

        Store(Source source, int nativeAddress, int width) {
            this.source = source;
            this.nativeAddress = nativeAddress;
            this.width = width;
        }

        public Source source() {
            return source;
        }

        public int nativeAddress() {
            return nativeAddress;
        }

        public int width() {
            return width;
        }
    }

    /**
     * Native saved reads, including LoadCheckpointData's word at $FF1542.
     * The declaration of savedWaterHeight is a byte: this word's low byte
     * is the same savedWaterRoutine byte, never an independent shadow.
     */
    public enum SavedField {
        SPAWN_MODE(0xFF1523, 1),
        PRIMARY_X(0xFF1524, 2),
        PRIMARY_Y(0xFF1526, 2),
        WATER_ROUTINE(0xFF1543, 1),
        BOTTOM_BOUND(0xFF1530, 2),
        FG_X(0xFF1532, 2),
        FG_Y(0xFF1534, 2),
        BG_X(0xFF1536, 2),
        BG_Y(0xFF1538, 2),
        BG2_X(0xFF153A, 2),
        BG2_Y(0xFF153C, 2),
        BG3_X(0xFF153E, 2),
        BG3_Y(0xFF1540, 2),
        WATER_HEIGHT_WORD(0xFF1542, 2),
        WATER_FULLSCREEN(0xFF1544, 1),
        TIME_LONG(0xFF152A, 4),
        MINI_SONIC(0xFF1591, 1);

        private final int nativeAddress;
        private final int width;

        SavedField(int nativeAddress, int width) {
            this.nativeAddress = nativeAddress;
            this.width = width;
        }

        public int nativeAddress() {
            return nativeAddress;
        }

        public int width() {
            return width;
        }
    }

    /** First unknown actual byte encountered by an addressed native saved read. */
    public static final class UnknownSavedByte extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final int nativeAddress;

        private UnknownSavedByte(int nativeAddress) {
            super(String.format("$%06X: unknown checkpoint saved byte", nativeAddress));
            this.nativeAddress = nativeAddress;
        }

        public int nativeAddress() {
            return nativeAddress;
        }
    }

    /**
     * Plain immutable saved value, retained by value for capture/restore.
     * Packed order: $FF1523..27, $FF152A..2D, $FF1530..44, $FF1591.
     * Unknown backing bytes are canonical private zero, not certified RAM.
     */
    public static final class State {
        private final byte[] bytes;
        private final byte[] knownMask;

        private State(byte[] bytes, byte[] knownMask) {
            if (bytes.length != SAVED_BYTE_COUNT || knownMask.length != SAVED_BYTE_COUNT) {
                throw new IllegalArgumentException("Saved bytes and mask must each contain exactly 31 bytes");
            }
            this.bytes = bytes.clone();
            this.knownMask = knownMask.clone();
            for (int i = 0; i < SAVED_BYTE_COUNT; i++) {
                if (this.knownMask[i] != 0 && this.knownMask[i] != 1) {
                    throw new IllegalArgumentException("Saved knowledge mask must contain only 0 or 1");
                }
                if (this.knownMask[i] == 0) {
                    this.bytes[i] = 0;
                }
            }
        }

        /** Explicit lack of knowledge, including before any successful save. */
        public static State unknown() {
            return new State(new byte[SAVED_BYTE_COUNT], new byte[SAVED_BYTE_COUNT]);
        }

        /** Explicit bytes and binary knowledge supplied by the actual owning caller. */
        public static State supplied(CdAssetBytes bytes, CdAssetBytes mask) {
            Objects.requireNonNull(bytes, "bytes");
            Objects.requireNonNull(mask, "mask");
            return new State(bytes.copy(), mask.copy());
        }

        public CdAssetBytes knownMask() {
            return CdAssetBytes.copyOf(knownMask);
        }

        /** Address must already be canonical 24-bit and inside one of the four spans. */
        public OptionalInt knownByte(int nativeAddress) {
            int index = packedIndex(nativeAddress);
            return knownMask[index] == 0 ? OptionalInt.empty()
                    : OptionalInt.of(Byte.toUnsignedInt(bytes[index]));
        }

        /** Raw big-endian bits; a long retains all int bits, including its sign bit. */
        public int read(SavedField field) {
            Objects.requireNonNull(field, "field");
            int value = 0;
            for (int part = 0; part < field.width(); part++) {
                int address = field.nativeAddress() + part;
                int index = packedIndex(address);
                if (knownMask[index] == 0) {
                    throw new UnknownSavedByte(address);
                }
                value = (value << 8) | Byte.toUnsignedInt(bytes[index]);
            }
            return value;
        }

        private State stage(Store store, int rawValue) {
            byte[] afterBytes = bytes.clone();
            byte[] afterMask = knownMask.clone();
            for (int part = 0; part < store.width(); part++) {
                int index = packedIndex(store.nativeAddress() + part);
                int shift = 8 * (store.width() - 1 - part);
                afterBytes[index] = (byte) (rawValue >>> shift);
                afterMask[index] = 1;
            }
            return new State(afterBytes, afterMask);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof State state
                    && Arrays.equals(bytes, state.bytes)
                    && Arrays.equals(knownMask, state.knownMask);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(bytes) + Arrays.hashCode(knownMask);
        }
    }

    /**
     * Actual source/publication seam. Implementations own phase/source/store
     * validation and actual state adoption. A returned commit must have published
     * finalState synchronously; retaining a StoreCall grants no future authority.
     */
    public interface Port {
        State currentState();

        int read(Source source);

        void commit(StoreCall call);
    }

    /** Plain values describing one reached store, not an execution or adoption token. */
    public record StoreCall(Store store, int rawValue, State expectedState, State finalState) {
        public StoreCall {
            Objects.requireNonNull(store, "store");
            Objects.requireNonNull(expectedState, "expectedState");
            Objects.requireNonNull(finalState, "finalState");
            requireRawWidth(rawValue, store.width());
        }
    }

    /** Completed metadata only; no resumable instruction cursor or replay commands. */
    public record Result(State state, List<Store> completedStores) {
        public Result {
            Objects.requireNonNull(state, "state");
            completedStores = List.copyOf(completedStores);
        }
    }

    /**
     * Execute only this native save, reading each input at its native position.
     * Exceptions escape unchanged; earlier actual publications remain the caller's
     * state. Failure returns no Result and carries no retry/resume authority.
     */
    public static Result save(State before, Port port) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(port, "port");
        requirePublished(before, port);
        State current = before;
        List<Store> completed = new ArrayList<>(18);
        for (Store store : Store.values()) {
            int rawValue = port.read(store.source());
            requireRawWidth(rawValue, store.source().width());
            if (store == Store.TIME_LONG && Integer.compareUnsigned(rawValue, FIVE_MINUTES) >= 0) {
                // CMP/BCS branches below the unsigned clamp threshold; live time is only read, never normalized or changed.
                rawValue = FIVE_MINUTES;
            }
            // No destination read: even an unknown byte becomes known at this actual store.
            State after = current.stage(store, rawValue);
            port.commit(new StoreCall(store, rawValue, current, after));
            requirePublished(after, port);
            completed.add(store);
            current = after;
        }
        return new Result(current, completed);
    }

    private static void requirePublished(State expected, Port port) {
        if (!expected.equals(port.currentState())) {
            throw new IllegalStateException("Actual checkpoint saved state does not match the reached publication");
        }
    }

    private static void requireRawWidth(int value, int width) {
        if ((width == 1 && (value & ~0xFF) != 0)
                || (width == 2 && (value & ~0xFFFF) != 0)) {
            throw new IllegalArgumentException("Source/store value exceeds its unsigned native width");
        }
    }

    private static int packedIndex(int nativeAddress) {
        if (nativeAddress >= 0xFF1523 && nativeAddress <= 0xFF1527) {
            return nativeAddress - 0xFF1523;
        }
        if (nativeAddress >= 0xFF152A && nativeAddress <= 0xFF152D) {
            return 5 + nativeAddress - 0xFF152A;
        }
        if (nativeAddress >= 0xFF1530 && nativeAddress <= 0xFF1544) {
            return 9 + nativeAddress - 0xFF1530;
        }
        if (nativeAddress == 0xFF1591) {
            return 30;
        }
        throw new IllegalArgumentException("Address is not a canonical owned checkpoint saved byte");
    }
}
