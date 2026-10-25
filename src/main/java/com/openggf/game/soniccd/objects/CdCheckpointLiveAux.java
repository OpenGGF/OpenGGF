package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.Arrays;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Eight live checkpoint-input bytes and their byte knowledge; pure finite native writers only.
 * The caller admits each actual source cut. Calling a writer here does not prove an entry,
 * service, load, save or restore phase was reached. There is no implicit complete-zero seed.
 *
 * <p>Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * time/timeMinutes/timeSeconds/timeFrames (src/_Include/Main CPU Variables.inc:66-69);
 * waterHeight2/waterRoutine/waterFullscreen (src/Level/_Definitions.inc:216-220).
 * Canonical byte order is time[0..3], height2[4..5], routine[6], fullscreen[7].
 * The time long retains all 32 bits, including its arbitrary leading byte.</p>
 *
 * <p>Saved-water aliasing belongs solely to the saved-byte owner. This value neither duplicates
 * saved payload nor owns pause, player, camera, timer-service or runtime state.</p>
 */
public final class CdCheckpointLiveAux {
    private CdCheckpointLiveAux() {}

    public enum Field {
        TIME_LONG(0xFF1514, 4),
        WATER_HEIGHT2(0xFFF648, 2),
        WATER_ROUTINE(0xFFF64D, 1),
        WATER_FULLSCREEN(0xFFF64E, 1);

        private final int nativeAddress;
        private final int width;

        private Field(int nativeAddress, int width) {
            this.nativeAddress = nativeAddress;
            this.width = width;
        }

        public int nativeAddress() { return nativeAddress; }
        public int width() { return width; }
    }

    public enum TimeByte {
        MINUTES(0xFF1515),
        SECONDS(0xFF1516),
        FRAMES(0xFF1517);

        private final int nativeAddress;

        private TimeByte(int nativeAddress) { this.nativeAddress = nativeAddress; }

        public int nativeAddress() { return nativeAddress; }
    }

    /** Immutable value. Unknown backing bytes are privately canonical zero, never readable values. */
    public static final class State {
        private final byte[] bytes;
        private final int knownMask;

        private State(byte[] bytes, int knownMask) {
            this.bytes = bytes.clone();
            this.knownMask = knownMask;
            for (int i = 0; i < 8; i++) {
                if ((knownMask & (1 << i)) == 0) { this.bytes[i] = 0; }
            }
        }

        public int knownMask() { return knownMask; }

        /** Accepts only one of this owner's eight canonical 24-bit native addresses. */
        public OptionalInt knownByte(int nativeAddress) {
            int index = indexAt(nativeAddress);
            return (knownMask & (1 << index)) == 0
                    ? OptionalInt.empty() : OptionalInt.of(Byte.toUnsignedInt(bytes[index]));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) { return true; }
            return other instanceof State state && knownMask == state.knownMask
                    && Arrays.equals(bytes, state.bytes);
        }

        @Override
        public int hashCode() { return 31 * Arrays.hashCode(bytes) + knownMask; }
    }

    /** The first missing byte of the addressed field, rather than an inferred zero or partial value. */
    public static final class UnknownLiveByte extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        private final int nativeAddress;

        private UnknownLiveByte(int nativeAddress) {
            super("Unknown live byte at native address 0x" + Integer.toHexString(nativeAddress));
            this.nativeAddress = nativeAddress;
        }

        public int nativeAddress() { return nativeAddress; }
    }

    public static State unknown() { return new State(new byte[8], 0); }

    /** Explicit development ingress, not evidence that a native producer or clear has executed. */
    public static State declaredDevIngress(CdAssetBytes raw8, int knownMask) {
        Objects.requireNonNull(raw8, "raw8");
        requireUnsigned(knownMask, 0xFF, "knownMask");
        if (raw8.length() != 8) { throw new IllegalArgumentException("raw8 must contain exactly eight bytes"); }
        return new State(raw8.copy(), knownMask);
    }

    /** Big-endian raw bits; a long returns its exact int bit pattern, without decimal normalization. */
    public static int read(State state, Field field) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(field, "field");
        int index = indexAt(field.nativeAddress);
        int value = 0;
        for (int i = 0; i < field.width; i++) {
            if ((state.knownMask & (1 << (index + i))) == 0) {
                throw new UnknownLiveByte(field.nativeAddress + i);
            }
            value = (value << 8) | Byte.toUnsignedInt(state.bytes[index + i]);
        }
        return value;
    }

    /**
     * Projection of LevelStart.NormalLoad.ClearMiscVars (src/Level/Main.asm:202-208).
     * The native half-open span is $FFF628..$FFF680, 22 long stores / $58 bytes.
     * Only this owner's four water bytes are written here; all time bits/knowledge survive.
     */
    public static State clearMiscAtLevelStart(State state) { return replace(state, 4, 4, 0); }

    /** Main.SkipTitleCard spawnMode==0 store only (src/Level/Main.asm:285-290), $FF1514..$FF1518. */
    public static State clearTimeAtSpawnZero(State state) { return replace(state, 0, 4, 0); }

    /**
     * One raw MOVE.L result: LoadTimeWarpData (:16), LoadCheckpointData (:54) in
     * src/Level/Load Saved Data.asm, or .SetTimeOver (HUD and Points/Main.asm:346).
     */
    public static State writeTimeLong(State state, int rawBits) { return replace(state, 0, 4, rawBits); }

    /**
     * One supplied native byte result, without arithmetic or carry: LoadCheckpointData
     * (src/Level/Load Saved Data.asm:55-56), .ChkTime (HUD and Points/Main.asm:266-279).
     */
    public static State writeTimeByte(State state, TimeByte field, int byteBits) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(field, "field");
        requireUnsigned(byteBits, 0xFF, "byteBits");
        return replace(state, indexAt(field.nativeAddress), 1, byteBits);
    }

    /** LoadTimeWarpData (:17), LoadCheckpointData (:57, :76), src/Level/Load Saved Data.asm. */
    public static State writeWaterRoutine(State state, int byteBits) {
        Objects.requireNonNull(state, "state");
        requireUnsigned(byteBits, 0xFF, "byteBits");
        return replace(state, 6, 1, byteBits);
    }

    /** Reached zone2 LoadCheckpointData store only (src/Level/Load Saved Data.asm:73-75). */
    public static State writeWaterHeight2(State state, int wordBits) {
        Objects.requireNonNull(state, "state");
        requireUnsigned(wordBits, 0xFFFF, "wordBits");
        return replace(state, 4, 2, wordBits);
    }

    /** Reached zone2 LoadCheckpointData store only (src/Level/Load Saved Data.asm:73-77). */
    public static State writeWaterFullscreen(State state, int byteBits) {
        Objects.requireNonNull(state, "state");
        requireUnsigned(byteBits, 0xFF, "byteBits");
        return replace(state, 7, 1, byteBits);
    }

    private static State replace(State state, int index, int width, int rawBits) {
        Objects.requireNonNull(state, "state");
        byte[] bytes = state.bytes.clone();
        for (int i = 0; i < width; i++) {
            bytes[index + i] = (byte) (rawBits >>> ((width - 1 - i) * 8));
        }
        return new State(bytes, state.knownMask | (((1 << width) - 1) << index));
    }

    private static void requireUnsigned(int value, int mask, String name) {
        if ((value & ~mask) != 0) { throw new IllegalArgumentException(name + " exceeds native width"); }
    }

    private static int indexAt(int nativeAddress) {
        return switch (nativeAddress) {
            case 0xFF1514 -> 0;
            case 0xFF1515 -> 1;
            case 0xFF1516 -> 2;
            case 0xFF1517 -> 3;
            case 0xFFF648 -> 4;
            case 0xFFF649 -> 5;
            case 0xFFF64D -> 6;
            case 0xFFF64E -> 7;
            default -> throw new IllegalArgumentException("Not an owned canonical 24-bit native address");
        };
    }
}
