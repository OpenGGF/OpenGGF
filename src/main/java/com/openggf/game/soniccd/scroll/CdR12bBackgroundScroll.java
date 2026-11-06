package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR12bProfile;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State;

import java.util.Arrays;
import java.util.Objects;

/**
 * Pure PPZ Act2 Past background arithmetic, authored by OpenAI Codex GPT-6.1 Sol.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, shipped FixBugs=0:
 * Palmtree Panic/Scroll (Act 2 Past).asm:165-259,265-561 and shared
 * Scroll Flag Set.asm:15-60,166-224. ACT2_FUTURE_VARIANT=0; USA events run before FG.
 *
 * <p>Reuses the existing immutable State14; only the actual eight-byte ROM table
 * is retained. The caller supplies the completed foreground/event cut and invokes
 * once in native order. No World, source admission, clock, capture owner, camera
 * copies, draw, PLC, current art, callback, mutable state or publication is created.
 * Capture/restore only replaces values and must not invoke these operations.</p>
 */
public final class CdR12bBackgroundScroll {
    private final CdAssetBytes cloudLineCounts;

    private CdR12bBackgroundScroll(CdAssetBytes cloudLineCounts) {
        this.cloudLineCounts = Objects.requireNonNull(cloudLineCounts, "CloudLineCounts");
        if (cloudLineCounts.length() != 8) throw new IllegalArgumentException("Eight CloudLineCounts bytes required");
        int words = 48; // Eight mountain, eight waterfall,32 lower-background WORDs.
        for (int i = 0; i < 8; i++) words += cloudLineCounts.unsignedByte(i) + 1;
        if (32 + words * 2 > CdR11aBackgroundScroll.DEFORMATION_RAM_BYTES) {
            throw new IllegalArgumentException("CloudLineCounts exceed the supplied deformation RAM");
        }
    }

    /** Authenticated original retained-image ingress; never opens media or accepts a public Identity. */
    public static CdR12bBackgroundScroll bind(CdR12bTerrainAssets assets) {
        return fromTables(Objects.requireNonNull(assets, "actual R12B data").view(CdR12bProfile.Asset.CLOUD_LINE_COUNTS));
    }

    /** Constructed component-format seam. Immutable bytes alone confer no retail or invocation authority. */
    public static CdR12bBackgroundScroll fromTables(CdAssetBytes cloudLineCounts) {
        return new CdR12bBackgroundScroll(cloudLineCounts);
    }

    /** Completed camera WORDs, signed native8:8 differences and scrollLock WORD's HIGH byte. */
    public record Input(int cameraXWord, int cameraYWord, int scrollXDiff, int scrollYDiff, int scrollLockByte) {
        public Input {
            word(cameraXWord); word(cameraYWord);
            signedWord(scrollXDiff); signedWord(scrollYDiff); nativeByte(scrollLockByte);
        }
    }

    /**
     * Completed SetupCamera cut, Scroll:165-259. Only source-proved integer operands
     * are supplied, ignoring foreground memory fractions. BG1 Y is a full LONG;
     * the other five camera WORD stores preserve their existing fractions.
     */
    public static State init(State before, CdR11aForegroundScroll.State completedSetup) {
        Objects.requireNonNull(before, "background state");
        Objects.requireNonNull(completedSetup, "completed foreground setup");
        int yPart = ((short) completedSetup.cameraYWord()) << 12;
        int bgY = yPart + yPart + yPart;
        int bgXWord = completedSetup.cameraX() >>> 1;
        int bg3XWord = bgXWord >>> 2;
        int bg2XWord = (bg3XWord >>> 1) * 3;
        byte[] deform = before.deformationRam().copy();
        Arrays.fill(deform, 0, 32, (byte) 0); // Eight CLR.L stores; separate cloudRam and the tail survive.
        return new State(storeWord(before.cameraBgX(), bgXWord), bgY,
                storeWord(before.cameraBg2X(), bg2XWord), storeWord(before.cameraBg2Y(), bgY >>> 16),
                storeWord(before.cameraBg3X(), bg3XWord), storeWord(before.cameraBg3Y(), bgY >>> 16),
                before.crossingRam(), before.scrollFlagsBg(), before.scrollFlagsBg2(), before.scrollFlagsBg3(),
                before.cloudRam(), CdAssetBytes.copyOf(deform), before.hscrollRam(), before.vscroll());
    }

    /**
     * One actual LevelScroll BG continuation after USA events and foreground X/Y.
     * A nonzero high lock byte returns before flag clears or any arithmetic. The
     * caller must retain source-order state if an earlier owning operation stops.
     */
    public State step(State before, Input actual) {
        Objects.requireNonNull(before, "background state"); Objects.requireNonNull(actual, "actual foreground input");
        if (actual.scrollLockByte() != 0) return before;

        int dx = actual.scrollXDiff();
        int bg3X = before.cameraBg3X() + (dx << 5);
        int xPart = dx << 4;
        int bg2X = before.cameraBg2X() + xPart + xPart + xPart;
        int bgX = before.cameraBgX() + (dx << 7);
        int yPart = actual.scrollYDiff() << 4;
        int bgY = before.cameraBgY() + yPart + yPart + yPart;
        byte[] crossed = before.crossingRam().copy();
        // Full entry CLR.Ws precede BG3, BG2, BG1 X/Y. New BSETs target HIGH bytes.
        int flags3 = crossingFlag(crossed, 4, before.cameraBg3X(), bg3X, 6);
        int flags2 = crossingFlag(crossed, 2, before.cameraBg2X(), bg2X, 4);
        int flags = crossingFlag(crossed, 0, before.cameraBgX(), bgX, 2);
        flags |= crossingFlag(crossed, 1, before.cameraBgY(), bgY, 0);
        int bgYWord = bgY >>> 16;
        flags |= (flags2 | flags3) & 0xFF00;
        flags2 &= 0xFF; flags3 &= 0xFF;

        byte[] deform = before.deformationRam().copy();
        // Scroll:367-376: accumulators reside in deformation RAM, not scrlSectSpeeds/cloudRam.
        addLong(deform, 0, 0x10000); addLong(deform, 4, 0xE000);
        addLong(deform, 8, 0xC000); addLong(deform, 12, 0xA000);
        addLong(deform, 16, 0x8000); addLong(deform, 20, 0x6000);
        addLong(deform, 24, 0x5000); addLong(deform, 28, 0x4000);
        int cursor = 32;
        int bg3Word = bg3X >>> 16;
        for (int group = 0; group < 8; group++) {
            // Accumulator pointer advances, while DBF d6 indexes counts7..0.
            cursor = words(deform, cursor, cloudLineCounts.unsignedByte(7 - group) + 1,
                    -(readWord(deform, group * 4) + bg3Word));
        }
        cursor = words(deform, cursor, 8, -bg3Word);
        cursor = words(deform, cursor, 8, -(bg2X >>> 16));
        words(deform, cursor, 32, -(bg2X >>> 16));

        // FillHScroll:547-561:30 blocks, partial first,240-(Y&7) LONGs. No224-line clamp.
        byte[] hscroll = before.hscrollRam().copy();
        int high = ((-actual.cameraXWord()) & 0xFFFF) << 16;
        int source = 32 + ((bgYWord & 0x1F8) >>> 2);
        int destination = 0;
        for (int block = 0; block < 30; block++) {
            int value = high | readWord(deform, source); source += 2;
            int count = block == 0 ? 8 - (bgYWord & 7) : 8;
            for (int line = 0; line < count; line++) {
                putLong(hscroll, destination, value); destination += 4;
            }
        }
        return new State(bgX, bgY, bg2X, storeWord(before.cameraBg2Y(), bgYWord),
                bg3X, storeWord(before.cameraBg3Y(), bgYWord), CdAssetBytes.copyOf(crossed),
                flags, flags2, flags3, before.cloudRam(), CdAssetBytes.copyOf(deform),
                CdAssetBytes.copyOf(hscroll), (actual.cameraYWord() << 16) | bgYWord);
    }

    /** Shared native Scroll Flag Set arithmetic: whole BYTE XOR and signed wrapping SUB.L, including zero. */
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
