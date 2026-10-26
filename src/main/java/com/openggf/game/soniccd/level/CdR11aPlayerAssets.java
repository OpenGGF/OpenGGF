package com.openggf.game.soniccd.level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Bounded R11A player byte prerequisites at scddisasm 1ecc5f3a. No animation,
 * physics, CalcAngle/CalcSine arithmetic, DPLC residency or hardware parity is implemented.
 * Production bytes come only from CdR11aAssetLoader's authenticated supplied MMD.
 */
public final class CdR11aPlayerAssets {
    public static final int ANIMATION_COUNT = 60;
    private static final int ANIMATION_BYTES = 578;

    /** Includes the native header, commands, operands and actual trailing padding/duplicate FF bytes. */
    public record AnimationScript(int relativeOffset, CdAssetBytes bytes) {
        /** Nonnegative delay, or native FF/FE/FD/FC handler selector; never a generic S1 profile. */
        public byte signedDelayOrHandler() { return bytes.signedByte(0); }
    }

    private final CdSonicVisualAssets sonic;
    private final CdAssetBytes animationTable;
    private final List<AnimationScript> animations;
    private final CdAssetBytes miniAnimations;
    private final CdAssetBytes rampChunks;
    private final CdAssetBytes specialChunks;
    private final CdAssetBytes sineTable;
    private final CdAssetBytes arcTanTable;

    private CdR11aPlayerAssets(byte[] animations, byte[] miniAnimations, byte[] rampChunks,
                                byte[] specialChunks, byte[] sineTable, byte[] arcTanTable,
                                CdSonicVisualAssets sonic) {
        this.sonic = Objects.requireNonNull(sonic, "sonic");
        this.animationTable = exact(animations, ANIMATION_BYTES, "Ani_Sonic");
        this.animations = decodeAnimations(animationTable, sonic.mappingFrames().size());
        this.miniAnimations = exact(miniAnimations, ANIMATION_COUNT, "live MiniAnims");
        for (int id = 0; id < ANIMATION_COUNT; id++) {
            if (this.miniAnimations.unsignedByte(id) >= ANIMATION_COUNT) {
                throw new IllegalArgumentException("Mini animation ID outside Ani_Sonic");
            }
        }

        // ObjSonic_Handle3DRamp.ChunkList (Sonic/Main.asm:797-831): five
        // positive raw layout-byte identities followed by the actual $FF sentinel.
        this.rampChunks = exact(rampChunks, 6, "3D ramp chunks");
        for (int i = 0; i < 5; i++) {
            if (this.rampChunks.unsignedByte(i) == 0 || this.rampChunks.signedByte(i) < 0) {
                throw new IllegalArgumentException("Early/invalid R11A ramp chunk sentinel");
            }
        }
        if (this.rampChunks.unsignedByte(5) != 0xFF) {
            throw new IllegalArgumentException("Missing reviewed R11A ramp chunk sentinel");
        }
        // LevelSizeLoad copies one long; Sonic_SpecialChunks compares all four
        // raw bytes independently (Scroll:113-129; Sonic/Main.asm:2522-2544).
        this.specialChunks = exact(specialChunks, 4, "SpecChunks");

        // Functions (Misc).asm:69-101: 256 sine words plus the 64-word
        // cosine extension. Retain the real values, including native asymmetries.
        this.sineTable = exact(sineTable, 640, "SineTable");
        for (int i = 0; i < 320; i++) {
            int value = sineWord(i);
            if (value < -0x100 || value > 0x100
                    || (i >= 256 && value != sineWord(i - 256))) {
                throw new IllegalArgumentException("Unverified sine word/cosine extension");
            }
        }
        // CalcAngle (Functions (Misc).asm:119-219) can form quotient 256
        // for equal nonzero magnitudes. Do not clamp to 255 or retain the EVEN byte.
        this.arcTanTable = exact(arcTanTable, 257, "ArcTanTable reachable entries");
        int previous = 0;
        for (int i = 0; i <= 256; i++) {
            int value = this.arcTanTable.unsignedByte(i);
            if (value < previous || value > 0x20) {
                throw new IllegalArgumentException("Unverified arctangent table range/order");
            }
            previous = value;
        }
        if (this.arcTanTable.unsignedByte(0) != 0 || this.arcTanTable.unsignedByte(256) != 0x20) {
            throw new IllegalArgumentException("Unverified arctangent endpoints");
        }
    }

    /**
     * Component format decoder only; constructed bytes do not authenticate a release.
     * The supplied, already decoded visual owner is retained by identity, without a second decode/copy.
     */
    public static CdR11aPlayerAssets decode(byte[] animations, byte[] miniAnimations, byte[] rampChunks,
                                            byte[] specialChunks, byte[] sineTable, byte[] arcTanTable,
                                            CdSonicVisualAssets sonic) {
        return new CdR11aPlayerAssets(animations, miniAnimations, rampChunks, specialChunks, sineTable, arcTanTable, sonic);
    }

    private static List<AnimationScript> decodeAnimations(CdAssetBytes table, int mappingCount) {
        int[] offsets = new int[ANIMATION_COUNT];
        int first = ANIMATION_BYTES;
        for (int id = 0; id < ANIMATION_COUNT; id++) {
            // ObjSonic_Animate (Sonic/Main.asm:2659-2662) uses signed ADDA.W.
            int offset = (short) table.unsignedWord(id * 2);
            if (offset < ANIMATION_COUNT * 2 || offset >= table.length() || (offset & 1) != 0) {
                throw new IllegalArgumentException("Animation signed relative pointer outside reviewed bodies");
            }
            offsets[id] = offset;
            first = Math.min(first, offset);
        }
        if (first != ANIMATION_COUNT * 2) {
            throw new IllegalArgumentException("Missing first reviewed animation body");
        }
        List<AnimationScript> scripts = new ArrayList<>();
        for (int offset : offsets) {
            int end = table.length();
            // Bound by the next distinct body, preserving the native $3A/$36
            // and $3B/$37 aliases without assuming monotonically increasing IDs.
            for (int other : offsets) {
                if (other > offset) { end = Math.min(end, other); }
            }
            var body = table.slice(offset, end - offset);
            validateScript(body, mappingCount);
            scripts.add(new AnimationScript(offset, body));
        }
        return List.copyOf(scripts);
    }

    private static void validateScript(CdAssetBytes body, int mappingCount) {
        if (body.length() < 3) { throw new IllegalArgumentException("Truncated animation body"); }
        int header = body.unsignedByte(0);
        if (header >= 0x80 && header < 0xFC) {
            throw new UnsupportedOperationException("Unreviewed CD animation handler byte");
        }
        // RunAnimScript (Sonic/Main.asm:2675-2715) treats $80..$FC as
        // frame IDs, not generic negative-byte commands. Only $FF/$FE/$FD
        // terminate these reviewed scripts. FC headers select FrozenAnim
        // (2888-2893); retaining that script does not execute its external frame selection.
        for (int cursor = 1; cursor < body.length(); cursor++) {
            int value = body.unsignedByte(cursor);
            if (value < 0xFD) {
                if (value >= mappingCount) { throw new IllegalArgumentException("Animation frame outside Sonic mappings"); }
                continue;
            }
            if (cursor == 1) { throw new IllegalArgumentException("Animation has no frame before command"); }
            int end = cursor + 1;
            if (value != 0xFF) {
                if (end == body.length()) { throw new IllegalArgumentException("Truncated animation command operand"); }
                int operand = body.unsignedByte(end++);
                if (value == 0xFE && (operand == 0 || operand > cursor - 1)) {
                    throw new IllegalArgumentException("Animation loop-back outside its frame sequence");
                }
                if (value == 0xFD && operand >= ANIMATION_COUNT) {
                    throw new IllegalArgumentException("Animation change outside Ani_Sonic");
                }
            }
            for (int padding = end; padding < body.length(); padding++) {
                int tail = body.unsignedByte(padding);
                if (tail != 0 && tail != 0xFF) {
                    throw new IllegalArgumentException("Unreviewed bytes beyond animation terminal command");
                }
            }
            return;
        }
        throw new IllegalArgumentException("Animation body has no bounded terminal command");
    }

    public CdSonicVisualAssets sonic() { return sonic; }
    public CdAssetBytes animationTable() { return animationTable; }
    public List<AnimationScript> animations() { return animations; }
    public AnimationScript animation(int nativeId) { return animations.get(nativeId); }
    public CdAssetBytes miniAnimations() { return miniAnimations; }
    public int miniAnimationId(int nativeId) { return miniAnimations.unsignedByte(nativeId); }
    /** Five raw layout-byte IDs and the actual signed-negative sentinel, with no layout lookup. */
    public CdAssetBytes rampChunks() { return rampChunks; }
    /** Native order: loop, special loop, special roll tunnel, regular roll tunnel. */
    public CdAssetBytes specialChunks() { return specialChunks; }
    public CdAssetBytes sineTable() { return sineTable; }
    public CdAssetBytes arcTanTable() { return arcTanTable; }
    /** Signed native table word; index 0..319 includes the cosine extension. No angle arithmetic. */
    public int sineWord(int tableIndex) {
        Objects.checkIndex(tableIndex, 320);
        return (short) sineTable.unsignedWord(tableIndex * 2);
    }
    /** Native quotient 0..256; values outside the admitted table fail without clamping. */
    public int arcTanByte(int nativeQuotient) { return arcTanTable.unsignedByte(nativeQuotient); }

    private static CdAssetBytes exact(byte[] bytes, int length, String name) {
        if (bytes.length != length) { throw new IllegalArgumentException(name + " extent"); }
        return CdAssetBytes.copyOf(bytes);
    }
}
