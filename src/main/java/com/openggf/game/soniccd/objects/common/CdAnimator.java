package com.openggf.game.soniccd.objects.common;

import com.openggf.game.soniccd.level.CdStageImage;

/**
 * {@code AnimateObject} (Level/Object Animate.asm:14-94) over a script table in the stage
 * file: the animation, previous animation, script index and timer bytes of one object, and
 * the commands $FF loop, $FE loop back, $FD new animation, $FC routine+2, $FB loop and clear
 * routine 2, $FA routine 2 +2. It is Sonic 1's AnimateSprite.
 */
public final class CdAnimator {
    private final CdStageImage image;
    private final int table;
    public int anim;
    private int prevAnim = -1;
    private int animFrame;
    private int animTime;
    private int mapFrame;
    private int flipBits;

    public CdAnimator(CdStageImage image, int table, int anim) {
        this.image = image;
        this.table = table;
        this.anim = anim;
    }

    /** One AnimateObject call; returns the routine increment the script requested (0 or 2). */
    public int step() {
        if (anim != prevAnim) {
            prevAnim = anim;
            animFrame = 0;
            animTime = 0;
        }
        animTime = (animTime - 1) & 0xFF;
        if ((animTime & 0x80) == 0) return 0;              // subq.b #1; bpl.s .End
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
                return -1;                                    // the caller clears routine 2
            }
            default -> {
                return value == 0xFA ? -2 : 0;               // the caller adds 2 to routine 2
            }
        }
    }

    private int show(int value) {
        mapFrame = value & 0x1F;
        flipBits = (value >> 5) & 3;                          // rol.b #3; andi.b #3
        animFrame = (animFrame + 1) & 0xFF;
        return 0;
    }

    public int mapFrame() { return mapFrame; }

    /**
     * The script's own bytes ({@code oPrevAnim}, {@code oAnimFrame}, {@code oAnimTime}, {@code oMapFrame}, the flip bits),
     * for an owner that keeps them as rewind-captured scalars: {@code {prevAnim, animFrame, animTime, mapFrame, flipBits}}.
     */
    public int[] scriptState() { return new int[] {prevAnim, animFrame, animTime, mapFrame, flipBits}; }

    /** Puts back {@link #scriptState()}. */
    public void restoreScriptState(int[] state) {
        prevAnim = state[0];
        animFrame = state[1];
        animTime = state[2];
        mapFrame = state[3];
        flipBits = state[4];
    }

    /** Script X/Y flip bits, XORed with the object's status flip bits by AnimateObject. */
    public int flipBits() { return flipBits; }

    /** {@code move.b #1,oPrevAnim(a0)}: forces the next call to restart the script. */
    public void restart() { prevAnim = -1; }

    public void setMapFrame(int frame) { mapFrame = frame; }
}
