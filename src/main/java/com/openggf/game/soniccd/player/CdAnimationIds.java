package com.openggf.game.soniccd.player;

import com.openggf.game.CanonicalAnimation;

/**
 * Sonic CD's Ani_Sonic IDs. Entries $00-$1C are Sonic 1's list in Sonic 1's order
 * (scddisasm src/Level/_Objects/Sonic/Data/Animations.asm:2-30), so canonical
 * animations resolve through {@link CdSonicAnimation}, CD's copy of that list; CD
 * appends its own entries after them.
 */
public final class CdAnimationIds {
    /** SonAni_GiveUp (Animations.asm:45); ObjSonic_ChkBoredom writes it. */
    public static final int GIVE_UP = 0x2B;
    /** SonAni_Peelout (Animations.asm:51); the walk/run special handler selects it at $A00. */
    public static final int PEELOUT = 0x31;
    /** SonAni_Balance2 (Animations.asm:52), ObjSonic_MoveGround's .BalanceAniBackwards. */
    public static final int BALANCE_BACKWARDS = 0x32;
    /** SonAni_Run3D (Animations.asm:55), the 3D-ramp walk/run script. */
    public static final int RUN_3D = 0x35;
    /** SonAni_Roll3D (Animations.asm:56), the 3D-ramp climbing roll script. */
    public static final int ROLL_3D = 0x36;

    private CdAnimationIds() { }

    public static int resolve(CanonicalAnimation canonical) {
        int id = CdSonicAnimation.fromCanonical(canonical);
        if (id >= 0) return id;
        return switch (canonical) {
            // .CheckSpindash writes Duck then ObjSonic_StartRoll writes Roll (Main.asm:1104-1123).
            case SPINDASH -> CdSonicAnimation.ROLL.id();
            case SKID -> CdSonicAnimation.SKID.id();
            // Balance states 3/4 face away from the edge: .BalanceAniBackwards (Main.asm:929-931).
            case BALANCE2 -> CdSonicAnimation.BALANCE.id();
            case BALANCE3, BALANCE4 -> BALANCE_BACKWARDS;
            case BLINK, GET_UP, VICTORY, BLANK, SUPER_TRANSFORM, GLIDE_LAND -> CdSonicAnimation.IDLE.id();
            case BUBBLE -> CdSonicAnimation.BUBBLE.id();
            case HURT2, HURT_FALL -> CdSonicAnimation.HURT.id();
            case SLIDE -> CdSonicAnimation.ROLL.id();
            default -> -1;
        };
    }
}
