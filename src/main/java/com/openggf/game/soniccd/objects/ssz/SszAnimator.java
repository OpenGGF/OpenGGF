package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.soniccd.level.CdStageImage;

/**
 * {@code AnimateObject} (Level/Object Animate.asm:14-94) over a script table, as
 * {@code CdAnimator} models it, with the object's script index ({@code oAnimFrame}, which the Stardust
 * Speedway launchers and path swap read and write: {@code obj.anim_index}, objects/r7/launch.asm:85,
 * path_swap.asm:121) and timer exposed. The commands are $FF loop, $FE loop back, $FD new animation, $FC
 * routine+2, $FB loop and clear routine 2 and $FA routine 2 +2; {@link #step()} returns 2 for $FC, -1 for $FB
 * and -2 for $FA.
 */
final class SszAnimator {
    private final CdStageImage image;
    private final int table;
    int anim;
    private int prevAnim;
    private int animFrame;
    private int animTime;
    private int mapFrame;
    private int flipBits;
    private boolean showed;

    SszAnimator(CdStageImage image, int table, int anim) {
        this.image = image;
        this.table = table;
        this.anim = anim;
    }

    /** One AnimateObject call. */
    int step() {
        showed = false;
        if (anim != prevAnim) {                              // cmp.b oPrevAnim; beq: a new animation restarts
            prevAnim = anim;
            animFrame = 0;
            animTime = 0;
        }
        animTime = (animTime - 1) & 0xFF;
        if ((animTime & 0x80) == 0) return 0;                // subq.b #1,oAnimTime; bpl.s .End
        int script = table + (short) image.u16(table + anim * 2);
        animTime = image.u8(script);
        int value = image.u8(script + 1 + animFrame);
        if (value < 0x80) return show(value);
        switch (value) {
            case 0xFF -> {
                animFrame = 0;
                return show(image.u8(script + 1));
            }
            case 0xFE -> {
                int back = image.u8(script + 2 + animFrame);
                animFrame = (animFrame - back) & 0xFF;
                return show(image.u8(script + 1 + animFrame));
            }
            case 0xFD -> {
                anim = image.u8(script + 2 + animFrame);
                return 0;
            }
            case 0xFC -> {
                return 2;
            }
            case 0xFB -> {
                animFrame = 0;
                return -1;
            }
            default -> {
                return value == 0xFA ? -2 : 0;
            }
        }
    }

    private int show(int value) {
        showed = true;
        mapFrame = value & 0x1F;
        flipBits = (value >> 5) & 3;
        animFrame = (animFrame + 1) & 0xFF;
        return 0;
    }

    int mapFrame() { return mapFrame; }

    /** AnimateObjectApplyFrame ran in the last {@link #step()}: it alone rewrites {@code oSprFlags} bits 0-1 (Object Animate.asm:25-37). */
    boolean showedFrame() { return showed; }

    /** Script X/Y flip bits, XORed with the object's status flip bits by AnimateObject. */
    int flipBits() { return flipBits; }

    /** {@code oAnimFrame}: the index of the next script entry. */
    int animFrame() { return animFrame; }

    /** {@code move.b #n,oAnimFrame(a0)}. */
    void setAnimFrame(int value) { animFrame = value & 0xFF; }

    /** {@code move.w #$FF,oAnim(a0)}: anim 0 and a previous animation of $FF, so the next call restarts. */
    void restartFromZero() {
        anim = 0;
        prevAnim = -1;
    }
}
