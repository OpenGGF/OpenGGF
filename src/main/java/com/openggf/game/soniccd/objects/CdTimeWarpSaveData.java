package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The distinct saved value and source-ordered stores of SaveTimeWarpData.
 * Native N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/Level/_Objects/Sonic/Main.asm:532-567; native saved declarations are
 * src/_Include/Main CPU Variables.inc:81,97-118,125,146. Live player/scroll/water
 * widths are declared in src/Level/_Definitions.inc:33-72,216-262.
 *
 * <p>This helper neither supplies live inputs nor owns a Frame, timer, native
 * invocation or capture partition. Its42 bytes do not overlap the checkpoint
 * saved value. Actual callers own phase/source checks and synchronous adoption
 * at every reached store. A StoreCall is immutable metadata, never authority to
 * replay a native write. All Java checks remain UNRUN by the source author.
 */
public final class CdTimeWarpSaveData {
    private static final int SAVED_BYTE_COUNT = 42;
    private static final int FIVE_MINUTES = 0x00050000;

    private CdTimeWarpSaveData() {}

    /** Actual live source addresses and widths. Sources are read only where reached. */
    public enum Source {
        SPAWN_MODE(0xFF1522, 1),
        PRIMARY_X(0xFFD008, 2),
        PRIMARY_Y(0xFFD00C, 2),
        PRIMARY_GVEL(0xFFD014, 2),
        PRIMARY_X_VELOCITY(0xFFD010, 2),
        PRIMARY_Y_VELOCITY(0xFFD012, 2),
        PRIMARY_FLAGS(0xFFD022, 1),
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
        RINGS(0xFF1512, 2),
        LIVES_FLAGS(0xFF150D, 1),
        TIME_LONG(0xFF1514, 4),
        MINI_SONIC(0xFF195B, 1);

        private final int nativeAddress;
        private final int width;

        Source(int nativeAddress, int width) { this.nativeAddress = nativeAddress; this.width = width; }
        public int nativeAddress() { return nativeAddress; }
        public int width() { return width; }
    }

    /** All26 native stores in source order, including the two distinct BCLR writes. */
    public enum Store {
        SAVED_SPAWN(Source.SPAWN_MODE, 0xFF1546, 1),
        SAVED_X(Source.PRIMARY_X, 0xFF1548, 2),
        SAVED_Y(Source.PRIMARY_Y, 0xFF154A, 2),
        GROUND_VELOCITY(Source.PRIMARY_GVEL, 0xFF1564, 2),
        X_VELOCITY(Source.PRIMARY_X_VELOCITY, 0xFF1566, 2),
        Y_VELOCITY(Source.PRIMARY_Y_VELOCITY, 0xFF1568, 2),
        PLAYER_FLAGS(Source.PRIMARY_FLAGS, 0xFF154C, 1),
        PLAYER_FLAGS_CLEAR_STANDING(null, 0xFF154C, 1),
        PLAYER_FLAGS_CLEAR_WATER(null, 0xFF154C, 1),
        WATER_ROUTINE_FIRST(Source.WATER_ROUTINE, 0xFF1562, 1),
        BOTTOM_BOUND(Source.BOTTOM_BOUND, 0xFF154E, 2),
        FG_X(Source.FG_X, 0xFF1550, 2),
        FG_Y(Source.FG_Y, 0xFF1552, 2),
        BG_X(Source.BG_X, 0xFF1554, 2),
        BG_Y(Source.BG_Y, 0xFF1556, 2),
        BG2_X(Source.BG2_X, 0xFF1558, 2),
        BG2_Y(Source.BG2_Y, 0xFF155A, 2),
        BG3_X(Source.BG3_X, 0xFF155C, 2),
        BG3_Y(Source.BG3_Y, 0xFF155E, 2),
        WATER_HEIGHT_WORD(Source.WATER_HEIGHT2, 0xFF1560, 2),
        WATER_ROUTINE_SECOND(Source.WATER_ROUTINE, 0xFF1562, 1),
        WATER_FULLSCREEN(Source.WATER_FULLSCREEN, 0xFF1563, 1),
        RINGS(Source.RINGS, 0xFF1528, 2),
        LIVES_FLAGS(Source.LIVES_FLAGS, 0xFF1545, 1),
        TIME_LONG(Source.TIME_LONG, 0xFF1570, 4),
        MINI_SONIC(Source.MINI_SONIC, 0xFF1593, 1);

        private final Source source;
        private final int nativeAddress;
        private final int width;

        Store(Source source, int nativeAddress, int width) {
            this.source = source; this.nativeAddress = nativeAddress; this.width = width;
        }

        /** Empty only for the two local saved-byte read/modify/write operations. */
        public Optional<Source> source() { return Optional.ofNullable(source); }
        public int nativeAddress() { return nativeAddress; }
        public int width() { return width; }
    }

    /** Saved fields, not a description of which fields a later loader consumes. */
    public enum SavedField {
        SPAWN_MODE(0xFF1546, 1),
        PRIMARY_X(0xFF1548, 2),
        PRIMARY_Y(0xFF154A, 2),
        PRIMARY_GVEL(0xFF1564, 2),
        PRIMARY_X_VELOCITY(0xFF1566, 2),
        PRIMARY_Y_VELOCITY(0xFF1568, 2),
        PRIMARY_FLAGS(0xFF154C, 1),
        WATER_ROUTINE(0xFF1562, 1),
        BOTTOM_BOUND(0xFF154E, 2),
        FG_X(0xFF1550, 2),
        FG_Y(0xFF1552, 2),
        BG_X(0xFF1554, 2),
        BG_Y(0xFF1556, 2),
        BG2_X(0xFF1558, 2),
        BG2_Y(0xFF155A, 2),
        BG3_X(0xFF155C, 2),
        BG3_Y(0xFF155E, 2),
        WATER_HEIGHT2(0xFF1560, 2),
        WATER_FULLSCREEN(0xFF1563, 1),
        RINGS(0xFF1528, 2),
        LIVES_FLAGS(0xFF1545, 1),
        TIME_LONG(0xFF1570, 4),
        MINI_SONIC(0xFF1593, 1);

        private final int nativeAddress;
        private final int width;

        SavedField(int nativeAddress, int width) { this.nativeAddress = nativeAddress; this.width = width; }
        public int nativeAddress() { return nativeAddress; }
        public int width() { return width; }
    }

    /** First unknown addressed byte reached by a saved-field read. */
    public static final class UnknownSavedByte extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final int nativeAddress;

        private UnknownSavedByte(int nativeAddress) {
            super(String.format("$%06X: unknown time-warp saved byte", nativeAddress));
            this.nativeAddress = nativeAddress;
        }
        public int nativeAddress() { return nativeAddress; }
    }

    /**
     * Immutable value and per-byte knowledge, with no native padding or live owner.
     * Packed order is FF1528..29, FF1545..46, FF1548..4C, FF154E..69,
     * FF1570..73, FF1593. Unknown backing bytes are canonical private zero.
     */
    public static final class State {
        private final byte[] bytes;
        private final byte[] knownMask;

        private State(byte[] bytes, byte[] knownMask) {
            if (bytes.length != SAVED_BYTE_COUNT || knownMask.length != SAVED_BYTE_COUNT)
                throw new IllegalArgumentException("Warp saved bytes and mask require exactly42 bytes");
            this.bytes = bytes.clone(); this.knownMask = knownMask.clone();
            for (int i = 0; i < SAVED_BYTE_COUNT; i++) {
                if (this.knownMask[i] != 0 && this.knownMask[i] != 1)
                    throw new IllegalArgumentException("Warp saved knowledge requires binary bytes");
                if (this.knownMask[i] == 0) this.bytes[i] = 0;
            }
        }

        public static State unknown() { return new State(new byte[SAVED_BYTE_COUNT], new byte[SAVED_BYTE_COUNT]); }
        public static State supplied(CdAssetBytes bytes, CdAssetBytes mask) {
            Objects.requireNonNull(bytes, "saved bytes"); Objects.requireNonNull(mask, "saved knowledge");
            return new State(bytes.copy(), mask.copy());
        }
        public CdAssetBytes knownMask() { return CdAssetBytes.copyOf(knownMask); }
        public OptionalInt knownByte(int nativeAddress) {
            int index = packedIndex(nativeAddress);
            return knownMask[index] == 0 ? OptionalInt.empty() : OptionalInt.of(Byte.toUnsignedInt(bytes[index]));
        }

        /** Unsigned BYTE/WORD values, or all raw bits of a big-endian LONG. */
        public int read(SavedField field) {
            Objects.requireNonNull(field, "saved field"); int value = 0;
            for (int part = 0; part < field.width(); part++) {
                int address = field.nativeAddress() + part; int index = packedIndex(address);
                if (knownMask[index] == 0) throw new UnknownSavedByte(address);
                value = (value << 8) | Byte.toUnsignedInt(bytes[index]);
            }
            return value;
        }

        private State stage(Store store, int rawValue) {
            byte[] afterBytes = bytes.clone(), afterMask = knownMask.clone();
            for (int part = 0; part < store.width(); part++) {
                int index = packedIndex(store.nativeAddress() + part);
                afterBytes[index] = (byte) (rawValue >>> (8 * (store.width() - part - 1)));
                afterMask[index] = 1;
            }
            return new State(afterBytes, afterMask);
        }

        @Override public boolean equals(Object other) {
            return this == other || other instanceof State state
                    && Arrays.equals(bytes, state.bytes) && Arrays.equals(knownMask, state.knownMask);
        }
        @Override public int hashCode() { return 31 * Arrays.hashCode(bytes) + Arrays.hashCode(knownMask); }
    }

    /**
     * The caller owns actual phase/read/store authority and reentry exclusion.
     * Reads must not change the saved value. A returned commit must have adopted
     * finalState synchronously. Pure value equality cannot prove exactly-once
     * publication, reject same-value replay, or authenticate a live producer.
     */
    public interface Port {
        State currentState();
        int read(Source source);
        void commit(StoreCall call);
    }

    /** Describes one reached native store; publicly constructed values carry no authority. */
    public record StoreCall(Store store, int rawValue, State expectedState, State finalState) {
        public StoreCall {
            Objects.requireNonNull(store, "store"); Objects.requireNonNull(expectedState, "expected saved value");
            Objects.requireNonNull(finalState, "final saved value"); requireRawWidth(rawValue, store.width());
        }
    }

    /** Completed metadata only; neither a resume cursor nor deferred commands. */
    public record Result(State state, List<Store> completedStores) {
        public Result { Objects.requireNonNull(state, "saved value"); completedStores = List.copyOf(completedStores); }
    }

    /**
     * SaveTimeWarpData (532-567), one reached read/store at a time. A throw returns
     * no Result; the port retains whichever earlier stores it actually adopted.
     * The original callback exception escapes unchanged. Live values are never
     * changed by this helper, and no unknown later source is read in advance.
     */
    public static Result save(State before, Port port) {
        Objects.requireNonNull(before, "initial saved value"); Objects.requireNonNull(port, "actual publication port");
        requirePublished(before, port); State current = before;
        List<Store> completed = new ArrayList<>(26);
        for (Store store : Store.values()) {
            int rawValue;
            if (store == Store.PLAYER_FLAGS_CLEAR_STANDING) {
                rawValue = current.read(SavedField.PRIMARY_FLAGS) & ~0x08;
            } else if (store == Store.PLAYER_FLAGS_CLEAR_WATER) {
                rawValue = current.read(SavedField.PRIMARY_FLAGS) & ~0x40;
            } else {
                Source source = store.source().orElseThrow();
                rawValue = port.read(source); requireRawWidth(rawValue, source.width());
                if (store == Store.TIME_LONG && Integer.compareUnsigned(rawValue, FIVE_MINUTES) >= 0)
                    rawValue = FIVE_MINUTES;
            }
            // A read port must not hide a changed saved value beneath a later full-value commit.
            requirePublished(current, port);
            State after = current.stage(store, rawValue);
            port.commit(new StoreCall(store, rawValue, current, after));
            requirePublished(after, port);
            completed.add(store); current = after;
        }
        return new Result(current, completed);
    }

    private static void requirePublished(State expected, Port port) {
        if (!expected.equals(port.currentState()))
            throw new IllegalStateException("Actual warp saved value differs from the reached publication");
    }
    private static void requireRawWidth(int value, int width) {
        if ((width == 1 && (value & ~0xFF) != 0) || (width == 2 && (value & ~0xFFFF) != 0))
            throw new IllegalArgumentException("Source/store value exceeds its unsigned native width");
    }
    private static int packedIndex(int nativeAddress) {
        if (nativeAddress >= 0xFF1528 && nativeAddress < 0xFF152A) return nativeAddress - 0xFF1528;
        if (nativeAddress >= 0xFF1545 && nativeAddress < 0xFF1547) return 2 + nativeAddress - 0xFF1545;
        if (nativeAddress >= 0xFF1548 && nativeAddress < 0xFF154D) return 4 + nativeAddress - 0xFF1548;
        if (nativeAddress >= 0xFF154E && nativeAddress < 0xFF156A) return 9 + nativeAddress - 0xFF154E;
        if (nativeAddress >= 0xFF1570 && nativeAddress < 0xFF1574) return 37 + nativeAddress - 0xFF1570;
        if (nativeAddress == 0xFF1593) return 41;
        throw new IllegalArgumentException("Address is not a canonical owned time-warp saved byte");
    }
}
