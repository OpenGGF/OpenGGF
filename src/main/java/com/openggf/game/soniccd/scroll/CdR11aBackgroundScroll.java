package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssets;

import java.util.Objects;
import java.util.Set;

import static com.openggf.game.soniccd.level.CdR11aAsset.CLOUD_SECT_SIZES;
import static com.openggf.game.soniccd.level.CdR11aAsset.CLOUD_UPSIDE_DOWN_SECT_SIZES;
import static com.openggf.game.soniccd.level.CdR11aAsset.WATER_DEFORM_SECTS;

/**
 * Pure immutable R11A BG arithmetic owner, authored by OpenAI Codex GPT-6.1 Sol.
 * Native source pin: 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, shipped FixBugs=0.
 * "PP Scroll" means src/Level/Palmtree Panic/Scroll (Act 1 Present).asm;
 * "Flags" means src/Level/Scroll Flag Set.asm, both in docs/scddisasm.
 *
 * Owns InitLevelScroll:135-170 and the ordinary BG continuation:203-398,
 * ApplyBGHScroll:433-534, with Flags:102-121,132-224. The caller supplies completed
 * foreground/event results in native order. No actor, pool, visit, frame, camera
 * singleton, callback, clock, renderer, tile-streaming or PLC service is retained.
 * Call step once at the source cut; capture/restore and publication only replace/read
 * State. This component alone makes no live-route or native-plane/pixel claim.
 */
public final class CdR11aBackgroundScroll {
    /** Raw bytes at horizBlkCrossedBg through the three bytes before scrollFlags. */
    public static final int CROSSING_RAM_BYTES = 8;
    public static final int CLOUD_RAM_BYTES = 512;
    public static final int DEFORMATION_RAM_BYTES = 512;
    public static final int HSCROLL_RAM_BYTES = 1024;

    /**
     * Six complete camera longs, three RAW UNSIGNED flag words and the full RAM images.
     * crossingRam byte indices: BG1 horizontal 0, BG1 vertical 1, BG2 horizontal 2,
     * BG2 padding 3, BG3 horizontal 4, padding 5..7. Never normalize their contents.
     * CdAssetBytes owns immutable storage and exposes only copies/read-only views.
     * vscroll is the native long: foreground Y in the high word, BG1 Y in the low.
     * Widths: Level/_Definitions.inc:121,127,195,237-277;
     * cloud allocation: _Include/Main CPU Variables.inc:191.
     */
    public record State(int cameraBgX, int cameraBgY, int cameraBg2X, int cameraBg2Y,
                        int cameraBg3X, int cameraBg3Y, CdAssetBytes crossingRam,
                        int scrollFlagsBg, int scrollFlagsBg2, int scrollFlagsBg3,
                        CdAssetBytes cloudRam, CdAssetBytes deformationRam,
                        CdAssetBytes hscrollRam, int vscroll) {
        public State {
            exact(crossingRam, CROSSING_RAM_BYTES, Failure.STATE_RAM_SHAPE, "crossing RAM");
            exact(cloudRam, CLOUD_RAM_BYTES, Failure.STATE_RAM_SHAPE, "cloud RAM");
            exact(deformationRam, DEFORMATION_RAM_BYTES, Failure.STATE_RAM_SHAPE, "deformation RAM");
            exact(hscrollRam, HSCROLL_RAM_BYTES, Failure.STATE_RAM_SHAPE, "HScroll RAM");
            word(scrollFlagsBg); word(scrollFlagsBg2); word(scrollFlagsBg3);
        }
        public int cameraBgXWord() { return cameraBgX >>> 16; }
        public int cameraBgYWord() { return cameraBgY >>> 16; }
        public int cameraBg2XWord() { return cameraBg2X >>> 16; }
        public int cameraBg2YWord() { return cameraBg2Y >>> 16; }
        public int cameraBg3XWord() { return cameraBg3X >>> 16; }
        public int cameraBg3YWord() { return cameraBg3Y >>> 16; }
    }

    /**
     * InitLevelScroll's FULL incoming d0/d1 bits after SetupCamera, plus the player X word.
     * d0's upper half becomes the BG1 Y fraction through SWAP. No hidden zero-extension
     * or cold-state assumption is allowed. d1's upper bits have no persisted consumer.
     * Caller supplies every initial State byte, including uncleared cloud/buffer tails.
     */
    public record InitRegisters(int foregroundYRegister, int foregroundXRegister, int playerX) {
        public InitRegisters { word(playerX); }
    }

    /**
     * Completed FG integer camera words and SIGNED scrollXDiff/player oXVel words.
     * oXVel is not ground velocity. playerX is the native integer centre word;
     * playerControl and scrollLockByte are bytes (the latter is the high byte of
     * the native scrollLock word). There is no retained input or time source.
     */
    public record Input(int cameraX, int cameraY, int scrollXDiff, int playerXVelocity,
                        int playerX, int playerControl, int scrollLockByte) {
        public Input {
            word(cameraX); word(cameraY); signedWord(scrollXDiff); signedWord(playerXVelocity);
            word(playerX); nativeByte(playerControl); nativeByte(scrollLockByte);
        }
    }

    public enum Boundary { SCROLL_LOCKED, ARITHMETIC_COMPLETE }

    /** Observations, not replay instructions. State is the single owner of all persisted RAM. */
    public record Result(State state, Boundary boundary, int deformationWordsWritten,
                         int hscrollLongsWritten, int rampOffset, int bgDelta, int bg2Delta, int bg3Delta) {
        public Result { Objects.requireNonNull(state, "state"); Objects.requireNonNull(boundary, "boundary"); }
    }

    /** These owners remain outside the arithmetic contract, including on a successful step. */
    public enum UnownedBoundary {
        BG_TILE_STREAMING_WITH_UNBOUND_CAMERA_SECTIONS,
        SECTION_ART_AND_PLC_SERVICE,
        RENDER_AND_SPRITE_PUBLICATION
    }

    public static Set<UnownedBoundary> unownedBoundaries() {
        return Set.of(UnownedBoundary.BG_TILE_STREAMING_WITH_UNBOUND_CAMERA_SECTIONS,
                UnownedBoundary.SECTION_ART_AND_PLC_SERVICE, UnownedBoundary.RENDER_AND_SPRITE_PUBLICATION);
    }

    /** A malformed shape or an actual native read/write outside this owner's explicit RAM. */
    public enum Failure {
        WORD_SHAPE, SIGNED_WORD_SHAPE, BYTE_SHAPE, STATE_RAM_SHAPE, TABLE_SHAPE, VIEW_SELECTOR,
        DEFORMATION_CAPACITY, DEFORMATION_READ, HSCROLL_CAPACITY, WATER_TABLE_READ
    }

    public static final class InvalidScroll extends IllegalArgumentException {
        private final Failure failure;
        private final int offset;
        private InvalidScroll(Failure failure, int offset, String detail) {
            super(failure + (offset < 0 ? "" : " at byte " + offset) + ": " + detail);
            this.failure = failure;
            this.offset = offset;
        }
        public Failure failure() { return failure; }
        /** Byte offset for a memory boundary; -1 for a scalar/shape error. */
        public int offset() { return offset; }
    }

    // Only immutable table bytes are retained. No assets/media owner or source file is retained.
    private final CdAssetBytes cloudSizes;
    private final CdAssetBytes upsideDownCloudSizes;
    private final CdAssetBytes waterSections;

    private CdR11aBackgroundScroll(CdAssetBytes cloudSizes, CdAssetBytes upsideDownCloudSizes, CdAssetBytes waterSections) {
        this.cloudSizes = exact(cloudSizes, 10, Failure.TABLE_SHAPE, "CloudSectSizes");
        this.upsideDownCloudSizes = exact(upsideDownCloudSizes, 9, Failure.TABLE_SHAPE, "CloudUpsideDownSectSizes");
        this.waterSections = exact(waterSections, 12, Failure.TABLE_SHAPE, "WaterDeformSects");
        int words = 144; // PP Scroll:306-385: the fixed mountains/bushes/water blocks.
        for (int i = 0; i < 10; i++) words += cloudSizes.unsignedByte(i) + 1;
        for (int i = 0; i < 9; i++) words += upsideDownCloudSizes.unsignedByte(i) + 1;
        if (words > DEFORMATION_RAM_BYTES / 2) {
            throw invalid(Failure.DEFORMATION_CAPACITY, -1, "cloud sections exceed supplied deformation RAM");
        }
    }

    /** Runtime binding uses ONLY the authenticated loader's existing bounded views; no new offset. */
    public static CdR11aBackgroundScroll bind(CdR11aAssets assets) {
        Objects.requireNonNull(assets, "assets");
        return fromViews(assets.view(CLOUD_SECT_SIZES), assets.view(CLOUD_UPSIDE_DOWN_SECT_SIZES),
                assets.view(WATER_DEFORM_SECTS));
    }

    /** Shape/selector decoder only. Constructing View objects does not authenticate media. */
    public static CdR11aBackgroundScroll fromViews(CdR11aAssets.View clouds, CdR11aAssets.View upsideDown,
                                                  CdR11aAssets.View water) {
        return fromTables(viewBytes(clouds, CLOUD_SECT_SIZES), viewBytes(upsideDown, CLOUD_UPSIDE_DOWN_SECT_SIZES),
                viewBytes(water, WATER_DEFORM_SECTS));
    }

    /** Explicit format-fixture seam. Callers supply all bytes; this is not a retail identity certificate. */
    public static CdR11aBackgroundScroll fromTables(CdAssetBytes clouds, CdAssetBytes upsideDown, CdAssetBytes water) {
        return new CdR11aBackgroundScroll(clouds, upsideDown, water);
    }

    private static CdAssetBytes viewBytes(CdR11aAssets.View view, CdR11aAsset expected) {
        Objects.requireNonNull(view, "view");
        if (view.asset() != expected) throw invalid(Failure.VIEW_SELECTOR, -1, expected.nativeLabel());
        if (Objects.requireNonNull(view.range(), "range").length() != expected.length()) {
            throw invalid(Failure.TABLE_SHAPE, -1, expected.nativeLabel() + " range length");
        }
        return exact(view.bytes(), expected.length(), Failure.TABLE_SHAPE, expected.nativeLabel());
    }

    /**
     * InitLevelScroll (PP Scroll:135-170). Only its actual stores are performed.
     * Level/Main.asm ClearCamera:210-216 can justify a caller's camera zeros after
     * that cold clear executes; it does not justify fabricating arbitrary buffers.
     */
    public static State init(State initial, InitRegisters registers) {
        Objects.requireNonNull(initial, "initial"); Objects.requireNonNull(registers, "registers");
        int y = backgroundY(registers.foregroundYRegister() & 0xFFFF, registers.playerX());
        int d0 = (registers.foregroundYRegister() & 0xFFFF0000) | y;
        int x = (registers.foregroundXRegister() & 0xFFFF) >>> 1;
        int eighthX = x >>> 2;
        int bg3X = uw(eighthX + eighthX + eighthX);
        int sixteenthX = eighthX >>> 1;
        int bg2X = uw(sixteenthX + sixteenthX + sixteenthX);
        byte[] clouds = initial.cloudRam().copy();
        for (int i = 0; i < 19; i++) putLong(clouds, i * 4, 0); // CLR.L / DBF #$12; NOT the remaining 436 bytes.
        return new State(storeWord(initial.cameraBgX(), x), swap(d0),
                storeWord(initial.cameraBg2X(), bg2X), storeWord(initial.cameraBg2Y(), y),
                storeWord(initial.cameraBg3X(), bg3X), storeWord(initial.cameraBg3Y(), y),
                initial.crossingRam(), initial.scrollFlagsBg(), initial.scrollFlagsBg2(), initial.scrollFlagsBg3(),
                CdAssetBytes.copyOf(clouds), initial.deformationRam(), initial.hscrollRam(), initial.vscroll());
    }

    /**
     * Ordinary LevelScroll BG continuation after completed USA events -> ScrollCamX -> ScrollCamY.
     * Lock reproduces PP Scroll:177-180. Unlocked flag clears belong to :183-186;
     * all arithmetic/buffer branches through ApplyBGHScroll's return are modeled.
     * A failed out-of-owner memory access exposes no Result or partially mutated input.
     */
    public Result step(State state, Input input) {
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(input, "input");
        if (input.scrollLockByte() != 0) return new Result(state, Boundary.SCROLL_LOCKED, 0, 0, 0, 0, 0, 0);

        // PP Scroll .GotSpeed:203-238: sign extend each WORD before shifting its LONG.
        int ramp = (input.playerControl() & 2) != 0 && input.scrollXDiff() != 0 ? input.playerXVelocity() << 8 : 0;
        int bg3Delta = (input.scrollXDiff() << 5) + ramp;
        int base = input.scrollXDiff() << 4;
        int bg2Delta = base + base + base + ramp + ramp;
        int bgDelta = (input.scrollXDiff() << 7) + ramp;
        int bgX = state.cameraBgX() + bgDelta;
        int bg2X = state.cameraBg2X() + bg2Delta;
        int bg3X = state.cameraBg3X() + bg3Delta;
        byte[] crossings = state.crossingRam().copy();
        // BSET on a memory label uses a BYTE, i.e. the HIGH byte of each raw flag word.
        int flags3 = horizontalFlags(crossings, 4, state.cameraBg3X(), bg3X, 6);
        int flags2 = horizontalFlags(crossings, 2, state.cameraBg2X(), bg2X, 4);
        int flags = horizontalFlags(crossings, 0, state.cameraBgX(), bgX, 2);

        int y = backgroundY(input.cameraY(), input.playerX());
        // Flags SetVertiScrollFlagsBG2:102-121 sets only the integer WORD and checks signed SUB.W.
        if (((y & 0x10) ^ Byte.toUnsignedInt(crossings[1])) == 0) {
            crossings[1] ^= 0x10;
            flags |= (short) (y - state.cameraBgYWord()) < 0 ? 0x0100 : 0x0200;
        }
        int bgY = storeWord(state.cameraBgY(), y);
        int bg2Y = storeWord(state.cameraBg2Y(), y);
        int bg3Y = storeWord(state.cameraBg3Y(), y);
        // :257-261 OR/CLR.B the high bytes only. All three words were CLR.W at :184-186.
        flags |= (flags2 | flags3) & 0xFF00;
        flags2 &= 0xFF;
        flags3 &= 0xFF;
        // :198-199 first publishes old BG Y; :253 replaces it. Final VScroll owns both words.
        int vscroll = (input.cameraY() << 16) | y;

        byte[] clouds = state.cloudRam().copy();
        // PP Scroll:263-282: nineteen EXPLICIT LONG additions, every unlocked call, without a clock.
        addCloud(clouds, 0, 0x10000);
        addCloud(clouds, 4, 0xE000);
        addCloud(clouds, 8, 0xC000);
        addCloud(clouds, 12, 0xA000);
        addCloud(clouds, 16, 0x8000);
        addCloud(clouds, 20, 0x6000);
        addCloud(clouds, 24, 0x4800);
        addCloud(clouds, 28, 0x4000);
        addCloud(clouds, 32, 0x2800);
        addCloud(clouds, 36, 0x2000);
        addCloud(clouds, 40, 0x2000);
        addCloud(clouds, 44, 0x4000);
        addCloud(clouds, 48, 0x8000);
        addCloud(clouds, 52, 0xC000);
        addCloud(clouds, 56, 0x10000);
        addCloud(clouds, 60, 0xC000);
        addCloud(clouds, 64, 0x8000);
        addCloud(clouds, 68, 0x4000);
        addCloud(clouds, 72, 0x2000);

        byte[] deform = state.deformationRam().copy();
        int deformationWords = buildDeformation(clouds, deform, bgX >>> 16, bg2X >>> 16, bg3X >>> 16);
        byte[] hscroll = state.hscrollRam().copy();
        int hscrollLongs = applyHScroll(deform, hscroll, input.cameraX(), bgX >>> 16, y);
        var next = new State(bgX, bgY, bg2X, bg2Y, bg3X, bg3Y, CdAssetBytes.copyOf(crossings),
                flags, flags2, flags3, CdAssetBytes.copyOf(clouds), CdAssetBytes.copyOf(deform),
                CdAssetBytes.copyOf(hscroll), vscroll);
        return new Result(next, Boundary.ARITHMETIC_COMPLETE, deformationWords, hscrollLongs,
                ramp, bgDelta, bg2Delta, bg3Delta);
    }

    /** PP Scroll .ChgDir/.No3DRamp:135-150,240-250; borrow skips the logical halving. */
    private static int backgroundY(int foregroundY, int playerX) {
        if (playerX < 0x800) return foregroundY;
        int subtracted = uw(foregroundY - 0x1E0);
        if (foregroundY >= 0x1E0) subtracted >>>= 1;
        return uw(subtracted + 0x1E0);
    }

    /** Flags SetHorizScrollFlagsBG/BG2/BG3:132-224, including zero-delta crossing checks. */
    private static int horizontalFlags(byte[] crossings, int index, int oldPosition, int newPosition, int baseBit) {
        if ((((newPosition >>> 16) & 0x10) ^ Byte.toUnsignedInt(crossings[index])) != 0) return 0;
        crossings[index] ^= 0x10;
        int bit = newPosition - oldPosition < 0 ? baseBit : baseBit + 1; // Native SUB.L/BPL, with long wrap.
        return 1 << (bit + 8);
    }

    /** PP Scroll CloudsScroll through ScrollBtmWater:284-385. One word describes eight pixels. */
    private int buildDeformation(byte[] clouds, byte[] output, int bgX, int bg2X, int bg3X) {
        int cursor = 0;
        for (int i = 0; i < 10; i++) {
            int cloudWord = readLong(clouds, i * 4) >>> 16; // MOVE.L/SWAP, then ADD.W/NEG.W.
            cursor = writeWords(output, cursor, -(cloudWord + bg3X), cloudSizes.unsignedByte(9 - i) + 1);
        }
        cursor = writeWords(output, cursor, -bg2X, 20);
        cursor = writeWords(output, cursor, -bgX, 4);
        cursor = writeWords(output, cursor, -bgX, 56);
        cursor = writeWords(output, cursor, -bgX, 4);
        cursor = writeWords(output, cursor, -bg2X, 20);
        for (int i = 0; i < 9; i++) {
            int cloudWord = readLong(clouds, (10 + i) * 4) >>> 16;
            cursor = writeWords(output, cursor, -(cloudWord + bg3X), upsideDownCloudSizes.unsignedByte(8 - i) + 1);
        }
        cursor = writeWords(output, cursor, -bg2X, 20);
        cursor = writeWords(output, cursor, -bgX, 4);
        cursor = writeWords(output, cursor, -bgX, 16);
        return cursor / 2;
    }

    private enum HPhase { ENTRY, UNMODIFIED, LOOP, WATER }

    /**
     * PP Scroll ApplyBGHScroll:433-534. Keep native labels/word counters and SWAPs:
     * the initial 29 DBF counter is NOT a generic 240-line clamp. A partial first
     * block and water's block accounting can leave or overwrite nominal tail lines.
     * FixBugs=0 is retained; no corrected/fitted scanline algorithm is substituted.
     */
    private int applyHScroll(byte[] deform, byte[] output, int foregroundX, int bgX, int bgY) {
        int foregroundHigh = uw(-foregroundX) << 16; // :284-286, preserved by subsequent MOVE.W instructions.
        int d1 = 29;
        int d2 = bgY;
        int d4 = bgY;
        int section = 0;
        int source = (bgY & 0x7F8) >>> 2; // :390-398: byte offset, NOT a word index.
        int destination = 0;
        HPhase phase = HPhase.ENTRY;
        while (true) {
            switch (phase) {
                case ENTRY -> phase = d4 >= waterWord(section) ? HPhase.WATER : HPhase.UNMODIFIED;
                case UNMODIFIED -> {
                    // .ScrollUnmodified:437-444, including the direct SectOffscreen jump here.
                    int skipped = d2 & 7;
                    d4 = uw(d4 - skipped + 8);
                    int value = deformationWord(deform, source);
                    source += 2;
                    for (int i = skipped; i < 8; i++) destination = writeLine(output, destination, foregroundHigh | value);
                    d1 = uw(d1 - 1);
                    phase = HPhase.LOOP;
                }
                case LOOP -> {
                    // .ScrollLoop:446-459 checks TST.W/BMI before reading the next section.
                    if ((short) d1 < 0) return destination / 4;
                    if (d4 >= waterWord(section)) {
                        phase = HPhase.WATER;
                    } else {
                        d4 = uw(d4 + 8);
                        int value = deformationWord(deform, source);
                        source += 2;
                        for (int i = 0; i < 8; i++) destination = writeLine(output, destination, foregroundHigh | value);
                        d1 = uw(d1 - 1);
                    }
                }
                case WATER -> {
                    // .FoundWaterSection:464-470 uses unsigned subtraction borrow, then equality.
                    int depth = uw(d4 - waterWord(section));
                    int size = waterWord(section + 2);
                    int remaining = uw(size - depth);
                    if (size < depth) {
                        section += 4;
                        d2 = d4;
                        phase = HPhase.UNMODIFIED; // .SectOffscreen:531-534, not ENTRY/LOOP.
                        continue;
                    }
                    if (remaining == 0) {
                        section += 4;
                        phase = HPhase.LOOP;
                        continue;
                    }
                    int rate = waterRate(foregroundX, bgX);
                    int speed = bgX; // MOVEQ #0 / MOVE.W: integer in LOW half, fraction in HIGH half.
                    int offscreen = uw(depth - 1);
                    if ((short) offscreen >= 0) {
                        do {
                            speed = swap(swap(speed) + rate); // .GetStartWaterSpeed:487-492.
                            offscreen = uw(offscreen - 1);
                        } while (offscreen != 0xFFFF);
                    }
                    int blocks = remaining >>> 3;
                    boolean borrow = d1 < blocks;
                    d1 = uw(d1 - blocks);
                    if (borrow) {
                        int shrink = uw(uw(-d1) << 3);
                        remaining = uw(remaining - shrink);
                        if (remaining == 0) {
                            section += 4;
                            phase = HPhase.LOOP;
                            continue;
                        }
                    }
                    // .StartWaterDeform/.DoWaterDeform:506-525. No rounding/clamping of the line count.
                    for (int line = 0; line < remaining; line++) {
                        destination = writeLine(output, destination, foregroundHigh | uw(-speed));
                        speed = swap(swap(speed) + rate);
                        d4 = uw(d4 + 1);
                        if ((d4 & 7) == 0) source += 2;
                    }
                    section += 4;
                    phase = HPhase.LOOP;
                }
            }
        }
    }

    /** PP Scroll:472-480. DIVS overflow sets V and leaves the destination dividend unchanged. */
    private static int waterRate(int foregroundX, int bgX) {
        int dividend = ((short) (foregroundX - bgX)) << 8;
        int quotient = dividend / 224; // Java integer division, like successful DIVS, truncates toward zero.
        int afterDivs = quotient < Short.MIN_VALUE || quotient > Short.MAX_VALUE ? dividend : quotient;
        return ((short) afterDivs) << 8; // EXT.L consumes the low WORD, then ASL.L #8.
    }

    private int waterWord(int offset) {
        if (offset < 0 || offset > waterSections.length() - 2) {
            throw invalid(Failure.WATER_TABLE_READ, offset, "native water section read leaves supplied view");
        }
        return waterSections.unsignedWord(offset);
    }
    private static int deformationWord(byte[] ram, int offset) {
        if (offset < 0 || offset > ram.length - 2) {
            throw invalid(Failure.DEFORMATION_READ, offset, "native read reaches adjacent, unowned RAM");
        }
        return readWord(ram, offset);
    }
    private static int writeLine(byte[] ram, int offset, int value) {
        if (offset < 0 || offset > ram.length - 4) {
            throw invalid(Failure.HSCROLL_CAPACITY, offset, "native write leaves HScroll RAM");
        }
        putLong(ram, offset, value);
        return offset + 4;
    }
    private static int writeWords(byte[] ram, int offset, int value, int count) {
        if (offset < 0 || count > (ram.length - offset) / 2) {
            throw invalid(Failure.DEFORMATION_CAPACITY, offset, "native section write leaves deformation RAM");
        }
        for (int i = 0; i < count; i++, offset += 2) putWord(ram, offset, value);
        return offset;
    }
    private static void addCloud(byte[] ram, int offset, int increment) { putLong(ram, offset, readLong(ram, offset) + increment); }
    private static int readWord(byte[] ram, int offset) { return (Byte.toUnsignedInt(ram[offset]) << 8) | Byte.toUnsignedInt(ram[offset + 1]); }
    private static int readLong(byte[] ram, int offset) { return (readWord(ram, offset) << 16) | readWord(ram, offset + 2); }
    private static void putWord(byte[] ram, int offset, int value) { ram[offset] = (byte) (value >>> 8); ram[offset + 1] = (byte) value; }
    private static void putLong(byte[] ram, int offset, int value) { putWord(ram, offset, value >>> 16); putWord(ram, offset + 2, value); }
    private static int storeWord(int original, int value) { return (value << 16) | (original & 0xFFFF); }
    private static int swap(int value) { return (value << 16) | (value >>> 16); }
    private static int uw(int value) { return value & 0xFFFF; }
    private static CdAssetBytes exact(CdAssetBytes bytes, int size, Failure failure, String name) {
        Objects.requireNonNull(bytes, name);
        if (bytes.length() != size) throw invalid(failure, -1, name + " must contain " + size + " bytes");
        return bytes;
    }
    private static void word(int value) {
        if (value < 0 || value > 0xFFFF) throw invalid(Failure.WORD_SHAPE, -1, "unsigned native word required");
    }
    private static void signedWord(int value) {
        if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) throw invalid(Failure.SIGNED_WORD_SHAPE, -1, "signed native word required");
    }
    private static void nativeByte(int value) {
        if (value < 0 || value > 0xFF) throw invalid(Failure.BYTE_SHAPE, -1, "unsigned native byte required");
    }
    private static InvalidScroll invalid(Failure failure, int offset, String detail) { return new InvalidScroll(failure, offset, detail); }
}
