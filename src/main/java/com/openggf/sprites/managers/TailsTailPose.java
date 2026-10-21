package com.openggf.sprites.managers;

/** Shared ROM tail scripts for the live controller and pure read-only scene projection. */
public final class TailsTailPose {
    // S2 Obj05Ani_Swish/Spindash; separate-art AniTails_Tail01/07 uses its own mappings.
    static final int[] SWISH_S2 = { 0x09, 0x0A, 0x0B, 0x0C, 0x0D };
    static final int[] SWISH_SEPARATE = { 0x22, 0x23, 0x24, 0x25, 0x26 };
    static final int[] SPINDASH_S2 = { 0x81, 0x82, 0x83, 0x84 };
    static final int[] SPINDASH_SEPARATE = { 1, 2, 3, 4 };
    static final int SWISH_DELAY = 7, SPINDASH_DELAY = 2;

    // Obj05 animation indices
    static final int ANIM_BLANK = 0;
    /** TailsAni_Push: the parent-animation index ROM forces into d0 while pushing. */
    static final int PARENT_ANIM_PUSH = 4;
    static final int ANIM_SWISH = 1;
    static final int ANIM_FLICK = 2;
    static final int ANIM_DIRECTIONAL = 3;
    static final int ANIM_SPINDASH = 7;
    static final int ANIM_SKIDDING = 8;
    static final int ANIM_PUSHING = 9;
    static final int ANIM_HANGING = 10;
    static final int ANIM_FLY1 = 0xB;
    static final int ANIM_FLY2 = 0xC;

    /**
     * Obj05AniSelection: maps parent animation ID to Obj05 animation.
     * ROM (S2): byte_1D29E
     */
    static final int[] ANI_SELECTION_S2 = {
        0,  // 0x00 Walk -> Blank
        0,  // 0x01 Run -> Blank
        3,  // 0x02 Roll -> Directional
        3,  // 0x03 Roll2 -> Directional
        9,  // 0x04 Push -> Pushing
        1,  // 0x05 Wait -> Swish
        0,  // 0x06 Balance -> Blank
        2,  // 0x07 LookUp -> Flick
        1,  // 0x08 Duck -> Swish
        7,  // 0x09 Spindash -> Spindash
        0,  // 0x0A Dummy1 -> Blank
        0,  // 0x0B Dummy2 -> Blank
        0,  // 0x0C Balance2 -> Blank
        8,  // 0x0D Skid -> Skidding
        0,  // 0x0E Float -> Blank
        0,  // 0x0F Float2 -> Blank
        0,  // 0x10 Spring -> Blank
        0,  // 0x11 Hang -> Blank
        0,  // 0x12 Blink -> Blank
        0,  // 0x13 Blink2 -> Blank
        10, // 0x14 Hang2 -> Hanging
        0,  // 0x15 Bubble -> Blank
        0,  // 0x16 -> Blank
        0,  // 0x17 -> Blank
        0,  // 0x18 Death -> Blank
        0,  // 0x19 Hurt -> Blank
        0,  // 0x1A Hurt2 -> Blank
        0,  // 0x1B -> Blank
        0,  // 0x1C -> Blank
        0,  // 0x1D Balance3 -> Blank
        0,  // 0x1E Balance4 -> Blank
        0,  // 0x1F HaulAss -> Blank
        0,  // 0x20 Fly -> Blank
    };

    /**
     * Obj_Tails_Tail_AniSelection: maps parent animation ID to Obj05 animation.
     * ROM (S3K): sonic3k.asm:30116
     */
    static final int[] ANI_SELECTION_S3K = {
        0,     // 0x00 Walk -> Blank
        0,     // 0x01 Run -> Blank
        3,     // 0x02 Roll -> Directional
        3,     // 0x03 Roll2 -> Directional
        9,     // 0x04 Push -> Pushing
        1,     // 0x05 Wait -> Swish
        0,     // 0x06 Balance -> Blank
        2,     // 0x07 LookUp -> Flick
        1,     // 0x08 Duck -> Swish
        7,     // 0x09 Spindash -> Spindash
        0,     // 0x0A Dummy1 -> Blank
        0,     // 0x0B Dummy2 -> Blank
        0,     // 0x0C Dummy3 -> Blank
        8,     // 0x0D Stop/Skid -> Skidding
        0,     // 0x0E Float -> Blank
        0,     // 0x0F Float2 -> Blank
        0,     // 0x10 Spring -> Blank
        0,     // 0x11 Hang -> Blank
        0,     // 0x12 -> Blank
        0,     // 0x13 Victory -> Blank
        10,    // 0x14 Hang2 -> Hanging
        0,     // 0x15 Bubble -> Blank
        0,     // 0x16 Death -> Blank
        0,     // 0x17 Death2 -> Blank
        0,     // 0x18 Death3 -> Blank
        0,     // 0x19 Slide2 -> Blank
        0,     // 0x1A Hurt -> Blank
        0,     // 0x1B Slide -> Blank
        0,     // 0x1C Blank -> Blank
        0,     // 0x1D Dummy4 -> Blank
        0,     // 0x1E Dummy5 -> Blank
        0,     // 0x1F HaulAss -> Blank
        0xB,   // 0x20 Fly -> Fly1
        0xC,   // 0x21 Fly2 -> Fly2
        0xB,   // 0x22 Carry -> Fly1
        0xC,   // 0x23 Ascend -> Fly2
        0xB,   // 0x24 Tired -> Fly1
        0,     // 0x25 Swim -> Blank (tails included in parent mapping)
        0,     // 0x26 Swim ascend -> Blank (tails included in parent mapping)
        0,     // 0x27 Swim carry -> Blank (tails included in parent mapping)
        0,     // 0x28 Swim tired -> Blank (tails included in parent mapping)
    };

    /**
     * Obj05Ani_Blank / AniTails_Tail00: {@code dc.b $20, 0, $FF} - a REAL script
     * that writes mapping_frame 0 with duration $20, not a "do nothing" state
     * (docs/s2disasm/s2.asm:41813, docs/skdisasm/General/Sprites/Tails/Anim - Tails
     * Tail.asm:15). Obj05_Main still runs LoadTailsTailsDynPLC for it
     * (docs/s2disasm/s2.asm:41756-41763), which writes
     * TailsTails_LastLoadedDPLC = 0 (docs/s2disasm/s2.asm:41642) before bailing on
     * the empty DPLC frame 0 (docs/s2disasm/s2.asm:41647-41648;
     * docs/s2disasm/mappings/spriteDPLC/Tails.asm:142-143). Skipping it left the
     * dedupe latch frozen at the previous directional frame.
     */
    static final int[] BLANK_FRAMES = { 0 };
    static final int BLANK_DELAY = 0x20;

    // --- S2 frame data (mapping frame indices from MapUnc_Tails) ---
    static final int[] SWISH_FRAMES_S2 = SWISH_S2;
    static final int[] FLICK_FRAMES_S2 = { 0x09, 0x0A, 0x0B, 0x0C, 0x0D };
    static final int[] DIRECTIONAL_FRAMES_S2 = { 0x49, 0x4A, 0x4B, 0x4C };
    static final int[] SPINDASH_FRAMES_S2 = SPINDASH_S2;
    static final int[] SKID_PUSH_FRAMES_S2 = { 0x87, 0x88, 0x89, 0x8A };

    // --- S3K frame data (mapping frame indices from Map_Tails_Tail) ---
    // Verified against Anim - Tails Tail.asm (AniTails_Tail01..0C)
    static final int[] SWISH_FRAMES_S3K = SWISH_SEPARATE;
    static final int[] FLICK_FRAMES_S3K = { 0x22, 0x23, 0x24, 0x25, 0x26 };
    static final int[] DIRECTIONAL_FRAMES_S3K = { 5, 6, 7, 8 };  // AniTails_Tail03
    static final int[] SPINDASH_FRAMES_S3K = SPINDASH_SEPARATE; // AniTails_Tail07
    static final int[] SKID_FRAMES_S3K = { 0x1A, 0x1B, 0x1C, 0x1D };  // AniTails_Tail08
    static final int[] PUSH_FRAMES_S3K = { 0x1E, 0x1F, 0x20, 0x21 };  // AniTails_Tail09
    static final int[] HANG_FRAMES_S3K = { 0x29, 0x2A, 0x2B, 0x2C };  // AniTails_Tail0A
    static final int[] FLY_FRAMES_S3K = { 0x27, 0x28 };                // AniTails_Tail0B/0C

    // Animation delays (frame duration)

    static final int FLICK_DELAY = 3;
    static final int DIRECTIONAL_DELAY = 3;

    static final int SKID_DELAY = 2;
    static final int PUSH_DELAY = 9;
    static final int HANG_DELAY = 9;
    static final int FLY1_DELAY = 1;
    static final int FLY2_DELAY = 0;


    private TailsTailPose() { }

    /** Detached mapping choice; a null pose means the body already contains its tails. */
    public record Pose(int mappingFrame, boolean flipX, boolean flipY) { }

    /** Directional bank/flips are held between the ROM's four-tick mapping reads. */
    public static long sampleTick(boolean separateArt, int parentAnimation, long tick) {
        if (tick < 0) throw new IllegalArgumentException("Negative tail pose time");
        return animation(separateArt, parentAnimation) == ANIM_DIRECTIONAL
                ? tick - tick % (DIRECTIONAL_DELAY + 1) : tick;
    }

    /**
     * Samples the same script tables as the live controller without advancing it. The caller
     * supplies facing and velocity (world pixels per 60 Hz tick) at {@link #sampleTick}.
     * Authored animation changes deliberately begin a fresh performance, unlike native state
     * transitions which can preserve a shared tail script's phase.
     */
    public static Pose sample(boolean separateArt, int parentAnimation, long tick,
                              boolean facingLeft, double velocityX, double velocityY) {
        if (tick < 0 || !Double.isFinite(velocityX) || !Double.isFinite(velocityY))
            throw new IllegalArgumentException("Invalid tail pose time or velocity");
        int anim = animation(separateArt, parentAnimation);
        if (anim == ANIM_BLANK) return null;
        int[] frames = frames(separateArt, anim);
        if (frames == null) return null;
        if (anim == ANIM_FLICK && tick >= (long) frames.length * (FLICK_DELAY + 1)) {
            tick -= (long) frames.length * (FLICK_DELAY + 1);
            anim = ANIM_SWISH;
            frames = frames(separateArt, anim);
        }
        int mapping = frames[(int) ((tick / (delay(anim) + 1)) % frames.length)];
        if (anim != ANIM_DIRECTIONAL) return new Pose(mapping, facingLeft, false);
        // Preserve native 8.8 precision for ordinary movement. Extremely fast authored
        // tracks are proportionally scaled into signed native velocity range, never wrapped.
        double largest = Math.max(Math.abs(velocityX), Math.abs(velocityY));
        double scale = largest > 127 ? 32767 / largest : 256;
        int angle = adjustedAngle((short) Math.round(velocityX * scale),
                (short) Math.round(velocityY * scale), facingLeft);
        int flags = (angle & 0x80) == 0 ? 0 : 3;
        if (facingLeft) flags ^= 1;
        return new Pose(mapping + ((angle >> 3) & 0x0C), (flags & 1) != 0, (flags & 2) != 0);
    }

    static int adjustedAngle(short velocityX, short velocityY, boolean facingLeft) {
        // TAnim_GetTailFrame/GetArcTan includes zero velocity, whose native angle is $40.
        int angle = com.openggf.physics.TrigLookupTable.calcAngle(velocityX, velocityY);
        angle = facingLeft ? (angle + 0x80) & 0xFF : (~angle) & 0xFF;
        return (angle + 0x10) & 0xFF;
    }

    static int animation(boolean isS3k, int parentAnimId) {
        int[] table = isS3k ? ANI_SELECTION_S3K : ANI_SELECTION_S2;
        if (parentAnimId >= 0 && parentAnimId < table.length) {
            return table[parentAnimId];
        }
        return ANIM_BLANK;
    }

    static int[] frames(boolean isS3k, int anim) {
        if (isS3k) {
            return switch (anim) {
                case ANIM_SWISH -> SWISH_FRAMES_S3K;
                case ANIM_FLICK -> FLICK_FRAMES_S3K;
                case ANIM_DIRECTIONAL -> DIRECTIONAL_FRAMES_S3K;
                case ANIM_SPINDASH -> SPINDASH_FRAMES_S3K;
                case ANIM_SKIDDING -> SKID_FRAMES_S3K;
                case ANIM_PUSHING -> PUSH_FRAMES_S3K;
                case ANIM_HANGING -> HANG_FRAMES_S3K;
                case ANIM_FLY1, ANIM_FLY2 -> FLY_FRAMES_S3K;
                case ANIM_BLANK -> BLANK_FRAMES;
                default -> null;
            };
        }
        return switch (anim) {
            case ANIM_SWISH -> SWISH_FRAMES_S2;
            case ANIM_FLICK -> FLICK_FRAMES_S2;
            case ANIM_DIRECTIONAL -> DIRECTIONAL_FRAMES_S2;
            case ANIM_SPINDASH, ANIM_HANGING -> SPINDASH_FRAMES_S2;
            case ANIM_SKIDDING, ANIM_PUSHING -> SKID_PUSH_FRAMES_S2;
            case ANIM_BLANK -> BLANK_FRAMES;
            default -> null;
        };
    }

    static int delay(int anim) {
        return switch (anim) {
            case ANIM_BLANK -> BLANK_DELAY;
            case ANIM_SWISH -> SWISH_DELAY;
            case ANIM_FLICK -> FLICK_DELAY;
            case ANIM_DIRECTIONAL -> DIRECTIONAL_DELAY;
            case ANIM_SPINDASH -> SPINDASH_DELAY;
            case ANIM_SKIDDING -> SKID_DELAY;
            case ANIM_PUSHING -> PUSH_DELAY;
            case ANIM_HANGING -> HANG_DELAY;
            case ANIM_FLY1 -> FLY1_DELAY;
            case ANIM_FLY2 -> FLY2_DELAY;
            default -> 0;
        };
    }



    /** S2 Obj05AniSelection maps Wait/Duck to Swish and Spindash to Spindash.
     * No parent, animation clock or DPLC owner is changed by this value lookup. */
    public static int frame(boolean separateArt, boolean spindash, long tick) {
        if (tick < 0) throw new IllegalArgumentException("Negative tail pose time");
        int[] frames = spindash
                ? (separateArt ? SPINDASH_SEPARATE : SPINDASH_S2)
                : (separateArt ? SWISH_SEPARATE : SWISH_S2);
        int delay = spindash ? SPINDASH_DELAY : SWISH_DELAY;
        return frames[(int) ((tick / (delay + 1)) % frames.length)];
    }
}
