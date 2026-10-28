package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State;

import java.util.Arrays;
import java.util.Objects;

/**
 * Stateless PPZ Act1 Past background coordinate/deformation arithmetic.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, shipped FixBugs=0:
 * Palmtree Panic/Scroll (Act 1 Past).asm:153-177,183-331;
 * shared Scroll Flag Set.asm:15-60,166-224.
 *
 * <p>The historical State14 is reused as immutable data, not as Present admission.
 * The caller owns the actual program/source, completed foreground/event order,
 * once-only invocation, native entry clears and publication. No clock, assets,
 * general registers, capture partition, callbacks, drawing, PLCs or live owner
 * are retained. Capture/restore must replace values rather than call this code.
 */
public final class CdR11bBackgroundScroll {
    private CdR11bBackgroundScroll() { }

    /** Actual completed camera WORDs, signed native8:8 deltas, and scrollLock's HIGH BYTE. */
    public record Input(int cameraXWord, int cameraYWord, int scrollXDiff, int scrollYDiff,
                        int scrollLockByte) {
        public Input {
            word(cameraXWord); word(cameraYWord);
            signedWord(scrollXDiff); signedWord(scrollYDiff); nativeByte(scrollLockByte);
        }
    }

    /**
     * Native InitLevelScroll at the completed SetupCamera cut. Its source-proved
     * integer operands are derived from that existing foreground value, ignoring
     * foreground memory fractions. Other arbitrary incoming register seeds are
     * outside this semantic contract; no hidden high-half default is supplied.
     */
    public static State init(State before, CdR11aForegroundScroll.State completedSetup) {
        Objects.requireNonNull(before, "background state");
        Objects.requireNonNull(completedSetup, "completed foreground setup");
        int bgY = ((short) completedSetup.cameraYWord()) << 13;
        int bgXWord = completedSetup.cameraX() >>> 1;
        int bg3XWord = bgXWord >>> 2;
        int bg2XWord = (bg3XWord >>> 1) * 3;
        byte[] deform = before.deformationRam().copy();
        Arrays.fill(deform, 0, 16, (byte) 0); // Four CLR.L stores; not separate cloudRam or the tail.
        return new State(storeWord(before.cameraBgX(), bgXWord), bgY,
                storeWord(before.cameraBg2X(), bg2XWord), storeWord(before.cameraBg2Y(), bgY >>> 16),
                storeWord(before.cameraBg3X(), bg3XWord), storeWord(before.cameraBg3Y(), bgY >>> 16),
                before.crossingRam(), before.scrollFlagsBg(), before.scrollFlagsBg2(), before.scrollFlagsBg3(),
                before.cloudRam(), CdAssetBytes.copyOf(deform), before.hscrollRam(), before.vscroll());
    }

    /**
     * One actual unlocked LevelScroll BG continuation, after the caller's events
     * and foreground X/Y operations. Native lock returns before whole-word flag
     * clears. Values confer no source/invocation authority or partial-write owner.
     */
    public static State step(State before, Input actual) {
        Objects.requireNonNull(before, "background state"); Objects.requireNonNull(actual, "actual foreground input");
        if (actual.scrollLockByte() != 0) return before;

        // Past206-245: signed WORD -> fixed-point delta, wrapping LONG sums.
        int dx = actual.scrollXDiff();
        int bg3X = before.cameraBg3X() + (dx << 5);
        int base = dx << 4;
        int bg2X = before.cameraBg2X() + base + base + base;
        int bgX = before.cameraBgX() + (dx << 7);
        int bgY = before.cameraBgY() + (actual.scrollYDiff() << 5);
        byte[] crossed = before.crossingRam().copy();
        // All three flag WORDs were CLR.W; new memory BSETs target their HIGH BYTE.
        int flags3 = crossingFlag(crossed, 4, before.cameraBg3X(), bg3X, 6);
        int flags2 = crossingFlag(crossed, 2, before.cameraBg2X(), bg2X, 4);
        int flags = crossingFlag(crossed, 0, before.cameraBgX(), bgX, 2);
        flags |= crossingFlag(crossed, 1, before.cameraBgY(), bgY, 0);
        int bgYWord = bgY >>> 16;
        flags |= (flags2 | flags3) & 0xFF00;
        flags2 &= 0xFF; flags3 &= 0xFF; // Native CLR.B after the full entry clears.

        byte[] deform = before.deformationRam().copy();
        // Past247-251: these four accumulators live INSIDE deformation RAM.
        addLong(deform, 0, 0x10000); addLong(deform, 4, 0xC000);
        addLong(deform, 8, 0x8000); addLong(deform, 12, 0x4000);
        int cursor = 16;
        int bg3Word = bg3X >>> 16;
        cursor = words(deform, cursor, 2, -(readWord(deform, 0) + bg3Word));
        cursor = words(deform, cursor, 2, -(readWord(deform, 4) + bg3Word));
        cursor = words(deform, cursor, 2, -(readWord(deform, 8) + bg3Word));
        cursor = words(deform, cursor, 2, -(readWord(deform, 12) + bg3Word));
        cursor = words(deform, cursor, 10, -bg3Word);
        words(deform, cursor, 24, -(bg2X >>> 16)); // Ends atbyte100; actual tail remains supplied.

        // Past309-331: thirty8-line blocks, partial first; NOT a224-line clamp.
        byte[] hscroll = before.hscrollRam().copy();
        int high = ((-actual.cameraXWord()) & 0xFFFF) << 16;
        int source = 16 + ((bgYWord & 0x1F8) >>> 2);
        int destination = 0;
        for (int block = 0; block < 30; block++) {
            int value = high | readWord(deform, source); source += 2;
            int lineCount = block == 0 ? 8 - (bgYWord & 7) : 8;
            for (int line = 0; line < lineCount; line++) {
                putLong(hscroll, destination, value); destination += 4;
            }
        }
        int vscroll = (actual.cameraYWord() << 16) | bgYWord;
        return new State(bgX, bgY, bg2X, storeWord(before.cameraBg2Y(), bgYWord),
                bg3X, storeWord(before.cameraBg3Y(), bgYWord), CdAssetBytes.copyOf(crossed),
                flags, flags2, flags3, before.cloudRam(), CdAssetBytes.copyOf(deform),
                CdAssetBytes.copyOf(hscroll), vscroll);
    }

    /** Whole BYTE XOR and signed wrapped SUB.L, including zero deltas. Flags15-60,166-224. */
    private static int crossingFlag(byte[] bytes, int index, int oldPosition, int newPosition, int baseBit) {
        if ((((newPosition >>> 16) & 0x10) ^ Byte.toUnsignedInt(bytes[index])) != 0) return 0;
        bytes[index] ^= 0x10;
        int bit = newPosition - oldPosition < 0 ? baseBit : baseBit + 1;
        return 1 << (bit + 8);
    }
    private static int storeWord(int previous, int word) { return (word << 16) | (previous & 0xFFFF); }
    private static int words(byte[] output, int offset, int count, int value) {
        for (int i = 0; i < count; i++) { putWord(output, offset, value); offset += 2; }
        return offset;
    }
    private static void addLong(byte[] bytes, int offset, int value) { putLong(bytes, offset, readLong(bytes, offset) + value); }
    private static int readWord(byte[] bytes, int offset) { return Byte.toUnsignedInt(bytes[offset]) << 8 | Byte.toUnsignedInt(bytes[offset + 1]); }
    private static int readLong(byte[] bytes, int offset) { return readWord(bytes, offset) << 16 | readWord(bytes, offset + 2); }
    private static void putWord(byte[] bytes, int offset, int value) { bytes[offset] = (byte) (value >>> 8); bytes[offset + 1] = (byte) value; }
    private static void putLong(byte[] bytes, int offset, int value) { putWord(bytes, offset, value >>> 16); putWord(bytes, offset + 2, value); }
    private static void word(int value) { if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException("Unsigned camera WORD required"); }
    private static void signedWord(int value) { if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) throw new IllegalArgumentException("Signed native8:8 WORD required"); }
    private static void nativeByte(int value) { if (value < 0 || value > 0xFF) throw new IllegalArgumentException("Unsigned scroll-lock BYTE required"); }
}
