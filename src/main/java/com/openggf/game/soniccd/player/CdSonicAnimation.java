package com.openggf.game.soniccd.player;

import com.openggf.game.AnimationId;
import com.openggf.game.CanonicalAnimation;

/**
 * Sonic CD's Ani_Sonic entries $00-$1E (scddisasm
 * {@code src/Level/_Objects/Sonic/Data/Animations.asm:2-32}), named after CD's labels.
 * The list is Sonic 1's in Sonic 1's order, so each entry keeps Sonic 1's canonical
 * correspondence; CD's own later entries are {@link CdAnimationIds}' constants.
 */
public enum CdSonicAnimation implements AnimationId {
    WALK(0x00),
    RUN(0x01),
    ROLL(0x02),
    ROLL_FAST(0x03),
    PUSH(0x04),
    IDLE(0x05),
    BALANCE(0x06),
    LOOK_UP(0x07),
    DUCK(0x08),
    WALK0(0x09),
    WALK1(0x0A),
    WALK2(0x0B),
    WALK3(0x0C),
    SKID(0x0D),
    UNK_0E(0x0E),
    FLOAT(0x0F),
    SPRING(0x10),
    HANG(0x11),
    UNK_12(0x12),
    UNK_13(0x13),
    UNK_14(0x14),
    BUBBLE(0x15),
    DEATH_BW(0x16),
    DROWN(0x17),
    DEATH(0x18),
    UNK_19(0x19),
    HURT(0x1A),
    SLIDE(0x1B),
    BLANK(0x1C),
    UNK_1D(0x1D),
    UNK_1E(0x1E);

    private final int id;

    CdSonicAnimation(int id) {
        this.id = id;
    }

    @Override
    public int id() {
        return id;
    }

    /** The canonical animation this entry stands for: Sonic 1's for the same slot. */
    public CanonicalAnimation toCanonical() {
        return switch (this) {
            case WALK -> CanonicalAnimation.WALK;
            case RUN -> CanonicalAnimation.RUN;
            case ROLL -> CanonicalAnimation.ROLL;
            case ROLL_FAST -> CanonicalAnimation.ROLL2;
            case PUSH -> CanonicalAnimation.PUSH;
            case IDLE -> CanonicalAnimation.WAIT;
            case BALANCE -> CanonicalAnimation.BALANCE;
            case LOOK_UP -> CanonicalAnimation.LOOK_UP;
            case DUCK -> CanonicalAnimation.DUCK;
            case WALK0 -> CanonicalAnimation.WARP1;
            case WALK1 -> CanonicalAnimation.WARP2;
            case WALK2 -> CanonicalAnimation.WARP3;
            case WALK3 -> CanonicalAnimation.WARP4;
            case SKID -> CanonicalAnimation.STOP;
            case UNK_0E -> CanonicalAnimation.FLOAT;
            case FLOAT -> CanonicalAnimation.FLOAT2;
            case SPRING -> CanonicalAnimation.SPRING;
            case HANG -> CanonicalAnimation.HANG;
            case UNK_12 -> CanonicalAnimation.LEAP1;
            case UNK_13 -> CanonicalAnimation.LEAP2;
            case UNK_14 -> CanonicalAnimation.SURF;
            case BUBBLE -> CanonicalAnimation.GET_AIR;
            case DEATH_BW -> CanonicalAnimation.BURNT;
            case DROWN -> CanonicalAnimation.DROWN;
            case DEATH -> CanonicalAnimation.DEATH;
            case UNK_19 -> CanonicalAnimation.SHRINK;
            case HURT -> CanonicalAnimation.HURT;
            case SLIDE -> CanonicalAnimation.WATER_SLIDE;
            case BLANK -> CanonicalAnimation.NULL_ANIM;
            case UNK_1D -> CanonicalAnimation.FLOAT3;
            case UNK_1E -> CanonicalAnimation.FLOAT4;
        };
    }

    /** The entry ID for {@code canonical}, or -1 when no entry in $00-$1E stands for it. */
    public static int fromCanonical(CanonicalAnimation canonical) {
        return AnimationId.fromCanonical(values(), CdSonicAnimation::toCanonical, canonical);
    }
}
