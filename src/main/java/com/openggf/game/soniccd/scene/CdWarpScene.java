package com.openggf.game.soniccd.scene;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Direct native WARP game algorithms over its own finite immutable scene value.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Time Warp Cutscene/Main.asm
 * Start:125-199, PaletteCycle:203-244, VInterrupt:249-287, RunObjects/DrawObject:306-557,
 * Random:1437-1454, and Objects/Sonic/Main.asm / Objects/Sparkle/Main.asm.
 * This is not a CPU, VDP, bus, interrupt scheduler, old-level clock or generic scene framework.
 *
 * <p>One step represents one admitted WARP pulse. Setup ends at the timer90 arm; the caller supplies
 * the distinct source counter explicitly. A zero seed describes a no-setup-pulse engine semantic
 * cut after the native clear, not a measured console interrupt phase. Delay pulses do not sample
 * controllers or publish pictures. Selected pulses publish palette/scroll/SAT, sample input, then
 * decrement the timer and increment this scene's WORD counter. The first external CRAM and tap are
 * unknown rather than invented; neither is selected by gameplay before it becomes known.
 *
 * <p>Only WARP's32 source-layout64-byte slots belong here. R11A actor, Frame, object, saved and
 * audio owners are never read or duplicated. Effects are completed typed metadata, not replay
 * commands. The actual module controls admission, exactly-once execution, adoption, sound ordering,
 * old-program suspension and rewind. A pure throw exposes no staged State/partial Result.
 */
public final class CdWarpScene {
    public enum Phase { DELAY, PREPARE, FADE_IN, MAIN, FADE_OUT, RETURNED }
    public enum SoundRequest {
        FM_D8;
        public int nativeId() { return 0xD8; }
        public int driverQueue() { return 2; }
    }
    public enum Boundary { SONIC_ROUTINE, SPARKLE_ROUTINE, TRAIL_SLOT, SPARKLE_BANK_FULL,
        ANIMATION_DIRECTORY, ANIMATION_FRAME, SPRITE_LEAF }

    public static final class UnsupportedScene extends IllegalStateException {
        private final Boundary boundary;
        private final int objectIndex;
        private final int address;
        private UnsupportedScene(Boundary boundary, int objectIndex, int address) {
            super(boundary + " object=" + objectIndex + " address=$" + Integer.toUnsignedString(address, 16));
            this.boundary = boundary; this.objectIndex = objectIndex; this.address = address;
        }
        public Boundary boundary() { return boundary; }
        public int objectIndex() { return objectIndex; }
        public int address() { return address; }
    }

    /** One complete closed value; source fields only, no Frame, renderer, callback or host clock. */
    public record State(Phase phase, int timerWord, int vIntCounterWord, int fadeStep,
                        int vscrollBgWord, int displayedVscrollBgWord, int trailIndex, long rngSeed,
                        int cycleTimerWord, int cycleIndexWord, boolean displayEnabled, int fadedOutByte,
                        OptionalInt heldByte, OptionalInt tapByte, CdAssetBytes objects,
                        CdAssetBytes palette, CdAssetBytes fadePalette, Optional<CdAssetBytes> displayedPalette,
                        CdAssetBytes cpuSprites, int spriteCount, CdAssetBytes displayedSprites, int displayedSpriteCount) {
        public State {
            Objects.requireNonNull(phase); word(timerWord); word(vIntCounterWord); word(vscrollBgWord);
            word(displayedVscrollBgWord); word(trailIndex); word(cycleTimerWord); word(cycleIndexWord); rawByte(fadedOutByte);
            if (rngSeed < 0 || rngSeed > 0xFFFF_FFFFL) throw new IllegalArgumentException("WARP RNG is an unsigned LONG");
            if (fadeStep < 0 || fadeStep > 24) throw new IllegalArgumentException("WARP fade cursor is0..24");
            Objects.requireNonNull(heldByte); Objects.requireNonNull(tapByte);
            if (heldByte.isPresent()) rawByte(heldByte.getAsInt()); if (tapByte.isPresent()) rawByte(tapByte.getAsInt());
            bytes(objects, 32 * 64); bytes(palette, 128); bytes(fadePalette, 128);
            Objects.requireNonNull(displayedPalette); displayedPalette.ifPresent(value -> bytes(value, 128));
            bytes(cpuSprites, 640); bytes(displayedSprites, 640);
            if (spriteCount < 0 || spriteCount > 80 || displayedSpriteCount < 0 || displayedSpriteCount > 80)
                throw new IllegalArgumentException("WARP SAT capacity is80 entries");
        }
        @Override public boolean equals(Object other) {
            return other instanceof State v && phase == v.phase && timerWord == v.timerWord
                    && vIntCounterWord == v.vIntCounterWord && fadeStep == v.fadeStep
                    && vscrollBgWord == v.vscrollBgWord && displayedVscrollBgWord == v.displayedVscrollBgWord
                    && trailIndex == v.trailIndex && rngSeed == v.rngSeed && cycleTimerWord == v.cycleTimerWord
                    && cycleIndexWord == v.cycleIndexWord && displayEnabled == v.displayEnabled && fadedOutByte == v.fadedOutByte
                    && heldByte.equals(v.heldByte) && tapByte.equals(v.tapByte) && same(objects, v.objects)
                    && same(palette, v.palette) && same(fadePalette, v.fadePalette)
                    && sameOptional(displayedPalette, v.displayedPalette) && same(cpuSprites, v.cpuSprites)
                    && spriteCount == v.spriteCount && same(displayedSprites, v.displayedSprites)
                    && displayedSpriteCount == v.displayedSpriteCount;
        }
        @Override public int hashCode() {
            return Objects.hash(phase, timerWord, vIntCounterWord, fadeStep, vscrollBgWord, displayedVscrollBgWord,
                    trailIndex, rngSeed, cycleTimerWord, cycleIndexWord, displayEnabled, fadedOutByte, heldByte, tapByte,
                    Arrays.hashCode(objects.copy()), Arrays.hashCode(palette.copy()), Arrays.hashCode(fadePalette.copy()),
                    displayedPalette.map(value -> Arrays.hashCode(value.copy())).orElse(0), Arrays.hashCode(cpuSprites.copy()),
                    spriteCount, Arrays.hashCode(displayedSprites.copy()), displayedSpriteCount);
        }
    }
    public record Result(State state, List<SoundRequest> sounds) {
        public Result { Objects.requireNonNull(state); sounds = List.copyOf(sounds); }
    }

    private final CdWarpAssets assets;
    public CdWarpScene(CdWarpAssets assets) { this.assets = Objects.requireNonNull(assets, "authenticated finite WARP assets"); }

    /** Native clear/file initializers at the timer90 arm; external CRAM and controller latch stay unknown. */
    public State initial(int initialVIntCounterWord) {
        word(initialVIntCounterWord);
        return new State(Phase.DELAY, 90, initialVIntCounterWord, 0, 0, 0, 0, 0, 0xFFFF, 0, false, 0,
                OptionalInt.empty(), OptionalInt.empty(), CdAssetBytes.copyOf(new byte[2048]), assets.palette(),
                CdAssetBytes.copyOf(new byte[128]), Optional.empty(), CdAssetBytes.copyOf(new byte[640]), 0,
                CdAssetBytes.copyOf(new byte[640]), 0);
    }

    /** Validate shape and closed-phase consistency only; never prefetch unvisited objects or mapping bytes. */
    public void validate(State state) {
        Objects.requireNonNull(state, "complete WARP state");
        boolean blank = state.phase == Phase.DELAY || state.phase == Phase.PREPARE;
        if (blank) {
            if (state.displayEnabled || state.displayedPalette.isPresent() || state.heldByte.isPresent() || state.tapByte.isPresent()
                    || state.fadeStep != 0 || state.fadedOutByte != 0
                    || (state.phase == Phase.DELAY ? state.timerWord == 0 || state.timerWord > 90 : state.timerWord != 0))
                throw new IllegalArgumentException("Invalid WARP timer-only setup cut");
        } else {
            if (!state.displayEnabled || state.displayedPalette.isEmpty() || state.heldByte.isEmpty())
                throw new IllegalArgumentException("Selected WARP cuts require their actual selected products and held input");
            if (state.tapByte.isEmpty() && !(state.phase == Phase.FADE_IN && state.fadeStep == 0))
                throw new IllegalArgumentException("Only the first selected WARP tap may be unknown");
            boolean fadingOut = state.phase == Phase.FADE_OUT || state.phase == Phase.RETURNED;
            if (state.fadedOutByte != (fadingOut ? 1 : 0)) throw new IllegalArgumentException("Inconsistent WARP fadedOut field");
            if (state.phase == Phase.RETURNED ? state.fadeStep != 24
                    : state.phase == Phase.FADE_IN || state.phase == Phase.FADE_OUT ? state.fadeStep >= 24 : state.fadeStep != 0)
                throw new IllegalArgumentException("Inconsistent WARP native fade cursor");
        }
    }

    public Result step(State state, int rawHeldByte) {
        validate(state); rawByte(rawHeldByte);
        if (state.phase == Phase.RETURNED) throw new IllegalStateException("WARP already returned; caller owns the IPX tail");
        var c = new Cursor(state); List<SoundRequest> sounds = List.of();
        switch (state.phase) {
            case DELAY -> { c.pulse(false, false, rawHeldByte); if (c.timer == 0) c.phase = Phase.PREPARE; }
            case PREPARE -> {
                c.timer = 240; c.enabled = true; c.fadePalette = c.palette; c.palette = white();
                sounds = List.of(SoundRequest.FM_D8); c.pulse(true, true, rawHeldByte); c.phase = Phase.FADE_IN; c.fadeStep = 0;
            }
            case FADE_IN -> {
                int component = c.fadeStep / 8, intensity = 14 - (c.fadeStep % 8) * 2;
                c.palette = CdWarpPalette.fadeFromWhite(c.palette, c.fadePalette, component, intensity);
                c.pulse(true, true, rawHeldByte); c.fadeStep++;
                if (c.fadeStep == 24) { c.phase = Phase.MAIN; c.fadeStep = 0; c.faded = 0; }
            }
            case MAIN -> {
                var cycled = CdWarpPalette.cycle(c.palette, c.cycleTimer, c.cycleIndex, assets.cycleColours());
                c.palette = cycled.palette(); c.cycleTimer = cycled.timerWord(); c.cycleIndex = cycled.indexWord();
                c.runObjects(); c.pulse(true, cycled.updated(), rawHeldByte);
                if ((c.tap.orElseThrow() & 0x80) != 0 || c.timer == 0) { c.phase = Phase.FADE_OUT; c.fadeStep = 0; c.faded = 1; }
            }
            case FADE_OUT -> {
                c.palette = CdWarpPalette.fadeToWhite(c.palette, c.fadeStep / 8);
                c.pulse(true, true, rawHeldByte); c.fadeStep++;
                if (c.fadeStep == 24) c.phase = Phase.RETURNED;
            }
            case RETURNED -> throw new AssertionError("Already rejected");
        }
        var next = c.finish(); validate(next); return new Result(next, sounds);
    }

    private final class Cursor {
        private Phase phase;
        private int timer, counter, fadeStep, scroll, displayedScroll, trail, cycleTimer, cycleIndex, faded, count, displayedCount;
        private long rng;
        private boolean enabled;
        private OptionalInt held, tap;
        private Optional<CdAssetBytes> displayedPalette;
        private CdAssetBytes palette, fadePalette, displayedSat;
        private final byte[] objects, sat;
        private Cursor(State s) {
            phase = s.phase; timer = s.timerWord; counter = s.vIntCounterWord; fadeStep = s.fadeStep;
            scroll = s.vscrollBgWord; displayedScroll = s.displayedVscrollBgWord; trail = s.trailIndex; rng = s.rngSeed;
            cycleTimer = s.cycleTimerWord; cycleIndex = s.cycleIndexWord; enabled = s.displayEnabled; faded = s.fadedOutByte;
            held = s.heldByte; tap = s.tapByte; objects = s.objects.copy(); palette = s.palette; fadePalette = s.fadePalette;
            displayedPalette = s.displayedPalette; sat = s.cpuSprites.copy(); count = s.spriteCount;
            displayedSat = s.displayedSprites; displayedCount = s.displayedSpriteCount;
        }
        private void pulse(boolean selected, boolean updatePalette, int rawHeld) {
            if (selected) {
                if (updatePalette) displayedPalette = Optional.of(palette);
                displayedScroll = scroll; scroll = (scroll - 4) & 0xFFFF;
                displayedSat = CdAssetBytes.copyOf(sat); displayedCount = count;
                tap = held.isPresent() ? OptionalInt.of((held.getAsInt() ^ rawHeld) & rawHeld) : OptionalInt.empty();
                held = OptionalInt.of(rawHeld);
            }
            if (timer != 0) timer = (timer - 1) & 0xFFFF;
            counter = (counter + 1) & 0xFFFF;
        }
        private void runObjects() {
            if ((counter & 3) == 0) {
                int selected = trail; trail = (trail + 1) & 3;
                if (selected >= 32) throw stop(Boundary.TRAIL_SLOT, selected, selected * 64);
                putWord(objects, selected * 64, 1); // ID rewrite never clears an occupied/trailing object.
                int free;
                for (free = 4; free < 32 && getWord(objects, free * 64) != 0; free++) { }
                if (free == 32) throw stop(Boundary.SPARKLE_BANK_FULL, free, free * 64);
                putWord(objects, free * 64, 2);
            }
            count = 0;
            for (int slot = 4; slot < 7; slot++) sparkle(slot);
            sonic(0, false);
            for (int slot = 1; slot < 4; slot++) sonic(slot, true);
            // FixBugs=0: shipped source reuses front base for25 back visits. Last3 back slots are not run.
            for (int slot = 4; slot < 29; slot++) sparkle(slot);
            // A full80-entry native table terminates in adjacent unused RAM, not by changing link80.
            // That adjacent zero has no consumer in this finite scene; retain the full640-byte SAT.
            if (count < 80) Arrays.fill(sat, count * 8, count * 8 + 4, (byte) 0);
        }
        private void sonic(int slot, boolean isTrail) {
            int at = slot * 64;
            if (getWord(objects, at) == 0) return;
            int routine = u8(objects[at + 3]);
            switch (routine) {
                case 0 -> {
                    putWord(objects, at + 4, 0x8000); putLong(objects, at + 6, CdWarpAssets.SONIC_MAPPING_ADDRESS);
                    putWord(objects, at + 16, 288); putWord(objects, at + 20, 416); setAnimation(slot, 0);
                    objects[at + 3] = 1; enterSonic(at);
                }
                case 1 -> enterSonic(at);
                case 2 -> {
                    objects[at + 14]--; // oTimer's BYTE is the HIGH byte of its WORD; low byte is untouched.
                    if (objects[at + 14] == 0) objects[at + 3]++;
                }
                case 3 -> {
                    int y = (getWord(objects, at + 20) - 8) & 0xFFFF; putWord(objects, at + 20, y);
                    if (y == 96) objects[at + 3]++;
                }
                case 4 -> { }
                default -> throw stop(Boundary.SONIC_ROUTINE, slot, routine);
            }
            if (isTrail) System.arraycopy(objects, 10, objects, at + 10, 4);
            draw(slot);
        }
        private void enterSonic(int at) {
            int y = (getWord(objects, at + 20) - 8) & 0xFFFF; putWord(objects, at + 20, y);
            if (y == 248) { objects[at + 3]++; objects[at + 14] = 120; }
        }
        private void sparkle(int slot) {
            int at = slot * 64;
            if (getWord(objects, at) == 0) return;
            int routine = u8(objects[at + 3]);
            if (routine == 0) {
                putWord(objects, at + 4, 0); putLong(objects, at + 6, CdWarpAssets.SPARKLE_MAPPING_ADDRESS);
                setAnimation(slot, 0); objects[at + 3]++;
                int random = random(); putLong(objects, at + 28, random & 0x3FFFF);
                putWord(objects, at + 16, getWord(objects, 16) - 16 + (random & 31));
                putWord(objects, at + 20, getWord(objects, 20) - 24); putWord(objects, at + 14, 45);
            } else if (routine != 1) throw stop(Boundary.SPARKLE_ROUTINE, slot, routine);
            putLong(objects, at + 16, getLong(objects, at + 16) + getLong(objects, at + 24));
            putLong(objects, at + 20, getLong(objects, at + 20) + getLong(objects, at + 28));
            int remaining = (getWord(objects, at + 14) - 1) & 0xFFFF; putWord(objects, at + 14, remaining);
            if (remaining == 0) objects[at + 2] |= 1;
            if ((objects[at + 2] & 1) != 0) Arrays.fill(objects, at, at + 64, (byte) 0);
            else draw(slot);
        }
        private int random() {
            int seed = (int) rng; if (seed == 0) seed = 0x2A6D365A;
            int product = seed * 41;
            int sum = ((product & 0xFFFF) + (product >>> 16)) & 0xFFFF;
            int result = (seed & 0xFFFF0000) | sum;
            rng = Integer.toUnsignedLong((sum << 16) | (product & 0xFFFF));
            return result;
        }
        private void setAnimation(int slot, int animation) {
            int at = slot * 64; objects[at + 10] = (byte) animation; objects[at + 11] = 0;
            int directory = mappingLong(getLong(objects, at + 6) + animation * 4, slot, Boundary.ANIMATION_DIRECTORY);
            mappingByte(directory, slot, Boundary.ANIMATION_DIRECTORY); // Native explicitly reads/skips frame count.
            int speed = mappingByte(directory + 1, slot, Boundary.ANIMATION_DIRECTORY);
            objects[at + 12] = (byte) speed; objects[at + 13] = (byte) speed;
        }
        private void draw(int slot) {
            int at = slot * 64, animation = u8(objects[at + 10]);
            int directory = mappingLong(getLong(objects, at + 6) + animation * 4, slot, Boundary.ANIMATION_DIRECTORY);
            int frames = mappingByte(directory, slot, Boundary.ANIMATION_DIRECTORY);
            int speed = mappingByte(directory + 1, slot, Boundary.ANIMATION_DIRECTORY);
            if ((objects[at + 2] & 2) == 0) {
                objects[at + 12]--;
                if (objects[at + 12] < 0) {
                    objects[at + 12] = (byte) speed; objects[at + 11]++;
                    if (u8(objects[at + 11]) >= frames) objects[at + 11] = 0;
                }
            }
            if ((objects[at + 2] & 4) != 0) return;
            int x = getWord(objects, at + 16), y = getWord(objects, at + 20), tile = getWord(objects, at + 4);
            int leaf = mappingLong(directory + 2 + u8(objects[at + 11]) * 4, slot, Boundary.ANIMATION_FRAME);
            int piecesMinusOne = mappingByte(leaf, slot, Boundary.SPRITE_LEAF);
            if (piecesMinusOne >= 128) return;
            int cursor = leaf + 1;
            for (int piece = 0; piece <= piecesMinusOne && count < 80; piece++, cursor += 5) {
                int rawY = mappingByte(cursor, slot, Boundary.SPRITE_LEAF);
                int size = mappingByte(cursor + 1, slot, Boundary.SPRITE_LEAF);
                int rawTile = (mappingByte(cursor + 2, slot, Boundary.SPRITE_LEAF) << 8)
                        | mappingByte(cursor + 3, slot, Boundary.SPRITE_LEAF);
                int rawX = mappingByte(cursor + 4, slot, Boundary.SPRITE_LEAF);
                int out = count * 8; putWord(sat, out, y + (byte) rawY); sat[out + 2] = (byte) size;
                count++; sat[out + 3] = (byte) count; putWord(sat, out + 4, tile + rawTile);
                int finalX = (x + (byte) rawX) & 0x1FF; putWord(sat, out + 6, finalX == 0 ? 1 : finalX);
            }
        }
        private State finish() {
            return new State(phase, timer, counter, fadeStep, scroll, displayedScroll, trail, rng, cycleTimer, cycleIndex,
                    enabled, faded, held, tap, CdAssetBytes.copyOf(objects), palette, fadePalette, displayedPalette,
                    CdAssetBytes.copyOf(sat), count, displayedSat, displayedCount);
        }
    }

    private int mappingByte(int address, int slot, Boundary boundary) {
        var sonic = assets.sonicMappings(); var sparkle = assets.sparkleMappings();
        if (address >= CdWarpAssets.SONIC_MAPPING_ADDRESS && (long) address < (long) CdWarpAssets.SONIC_MAPPING_ADDRESS + sonic.length())
            return sonic.unsignedByte(address - CdWarpAssets.SONIC_MAPPING_ADDRESS);
        if (address >= CdWarpAssets.SPARKLE_MAPPING_ADDRESS && (long) address < (long) CdWarpAssets.SPARKLE_MAPPING_ADDRESS + sparkle.length())
            return sparkle.unsignedByte(address - CdWarpAssets.SPARKLE_MAPPING_ADDRESS);
        throw stop(boundary, slot, address);
    }
    private int mappingLong(int address, int slot, Boundary boundary) {
        if ((address & 1) != 0) throw stop(boundary, slot, address);
        return (mappingByte(address, slot, boundary) << 24) | (mappingByte(address + 1, slot, boundary) << 16)
                | (mappingByte(address + 2, slot, boundary) << 8) | mappingByte(address + 3, slot, boundary);
    }
    private static UnsupportedScene stop(Boundary boundary, int slot, int address) { return new UnsupportedScene(boundary, slot, address); }
    private static int u8(byte value) { return value & 255; }
    private static int getWord(byte[] bytes, int at) { return (u8(bytes[at]) << 8) | u8(bytes[at + 1]); }
    private static int getLong(byte[] bytes, int at) { return (getWord(bytes, at) << 16) | getWord(bytes, at + 2); }
    private static void putWord(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
    private static void putLong(byte[] bytes, int at, int value) { putWord(bytes, at, value >>> 16); putWord(bytes, at + 2, value); }
    private static void word(int value) { if (value < 0 || value > 0xFFFF) throw new IllegalArgumentException("WARP field requires an unsigned WORD"); }
    private static void rawByte(int value) { if (value < 0 || value > 255) throw new IllegalArgumentException("WARP input/flag requires an unsigned BYTE"); }
    private static void bytes(CdAssetBytes value, int expected) {
        Objects.requireNonNull(value); if (value.length() != expected) throw new IllegalArgumentException("WARP complete byte extent must be" + expected);
    }
    private static boolean same(CdAssetBytes a, CdAssetBytes b) { return Arrays.equals(a.copy(), b.copy()); }
    private static boolean sameOptional(Optional<CdAssetBytes> a, Optional<CdAssetBytes> b) {
        return a.isEmpty() ? b.isEmpty() : b.isPresent() && same(a.get(), b.get());
    }
    private static CdAssetBytes white() {
        byte[] result = new byte[128]; for (int at = 0; at < 128; at += 2) { result[at] = 14; result[at + 1] = (byte) 238; }
        return CdAssetBytes.copyOf(result);
    }
}
