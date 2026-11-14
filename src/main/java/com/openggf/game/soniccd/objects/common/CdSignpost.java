package com.openggf.game.soniccd.objects.common;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Signpost, object $17 ({@code ObjSignpost}, Level/_Objects/Level End/Main.asm:708-907): width $18,
 * Y radius $20, priority 4, tile $43C (high priority except in Quartz Quadrant). When the player
 * passes it within 128 pixels vertically it locks the left boundary, stops the HUD timer, clears
 * invincibility and spins for 120 frames (Ani_Signpost), shows the player side (frame 3) and, 60
 * frames later, forces Right ({@code ctrlLocked} bit 0, {@code playerCtrlHold} $808; none in Stardust
 * Speedway act 3), computes the time bonus ({@code TimeBonuses[min((m*60+s)/15, 20)]}) and ring bonus
 * (rings * 100) and spawns the results object $3A with FindObjSlot and a 16-frame delay.
 *
 * <p>Sounds by ID: {@code FM_SIGNPOST} when passed, and at StartResults {@code SCMD_FADEPCM} in the past then
 * {@code SCMD_RESULTMUS}. Not hosted: the speed-shoes flag clear (the player's timer owner has no flag-only write).
 */
public final class CdSignpost extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** ObjSignpost_Main: move.b #120,oLvlEndTimer(a0) (Level End/Main.asm:758-775). */
    private static final int SPIN_FRAMES = 120;
    /** ObjSignpost_Spin: move.b #60,oLvlEndTimer(a0) (Level End/Main.asm:792-800). */
    private static final int RESULTS_DELAY = 60;
    /** {@code FM_SIGNPOST} ($9D, FM/_Sound IDs.inc:23): {@code move.w #FM_SIGNPOST,d0} (Level End/Main.asm:782-783). */
    private static final int FM_SIGNPOST = 0x9D;

    private int routine;
    private int timer;
    private int mapFrame;
    private CdAnimator animator;
    private int timeBonus;
    private int ringBonus;

    public CdSignpost(ObjectSpawn spawn) {
        super(spawn, "Signpost");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        switch (routine) {
            case 0 -> {
                // ObjSignpost_Init (Level End/Main.asm:739-753).
                routine = 2;
                width = 0x18;
                yRadius = 0x20;
                priority = 4;
                tileWord = zone() == 3 ? CommonObjectArt.SIGNPOST_TILE : CommonObjectArt.SIGNPOST_TILE | 0x8000;
                main(player);
            }
            case 2 -> main(player);
            case 4 -> spin();
            case 6 -> startResultsWhenDue();
            default -> { }                                   // ResultsActive: rts (Level End/Main.asm:905-907)
        }
    }

    /** ObjSignpost_Main (Level End/Main.asm:758-786). */
    private void main(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        int dy = ((sprite.getCentreY() - y) + 128) & 0xFFFF;
        if ((dy & 0x8000) != 0 || dy >= 256) return;
        if ((x & 0xFFFF) >= (sprite.getCentreX() & 0xFFFF)) return;
        var camera = services().camera();
        if (camera != null) camera.setMinX(camera.getX());
        var state = services().levelGamestate();
        if (state != null) state.pauseTimer();              // clr.b updateHUDTime
        timer = SPIN_FRAMES;
        mapFrame = 0;
        routine = 4;
        sprite.setInvincibleFrames(0);                       // clr.b invincible
        services().playSfx(FM_SIGNPOST);
    }

    /** ObjSignpost_Spin (Level End/Main.asm:792-803). */
    private void spin() {
        if (animator == null) animator = animator(e -> e.offset("signpostAnimations"), 0);
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
        if (--timer != 0) return;
        routine = 6;
        mapFrame = 3;
        timer = RESULTS_DELAY;
    }

    /** StartResults (Level End/Main.asm:816-866) through the shared tail ({@link #startResults()}). */
    private void startResultsWhenDue() {
        if (--timer != 0) return;
        ResultsBonuses bonuses = startResults();
        timer = 180;
        routine = 8;
        timeBonus = bonuses.time();
        ringBonus = bonuses.ring();
    }

    int routine() { return routine; }
    int mapFrame() { return mapFrame; }
    int timeBonus() { return timeBonus; }
    int ringBonus() { return ringBonus; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine != 0) drawFrame(CommonObjectArt.SIGNPOST, mapFrame, hFlip(), vFlip());
    }

    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }
}
