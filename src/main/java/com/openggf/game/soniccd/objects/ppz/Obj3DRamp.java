package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The 3D ramp marker, object $2A ({@code Obj3DRamp}, Palmtree Panic/Objects/3D Ramp/Main.asm:194-340).
 *
 * <p><b>Booster</b> (secondary subtype 0): a $40 by $40 pad that slides sideways with the player's progress
 * through the ramp chunk ({@code (position >> 2) + (position >> 3)} pixels, mirrored for a non-zero subtype)
 * and, when the player overlaps it and is not in the give-up animation ($2B), sets the player's vertical
 * speed to {@code +-$C00} (away from the direction it was heading) and starts a 60 frame timer. While the
 * timer runs it animates (animation 1 on the ramp, 2 off it) and touches regardless of the player being in
 * the air; with no timer it shows the frame {@code position / $30} and touches only a grounded player
 * (Main.asm:229-340). The "on the ramp" bit is {@link PpzRampPlayer#onRamp}.
 *
 * <p><b>Fall marker</b> (secondary subtype non-zero, {@code Obj3DFall}, scddisasm/src/Level/Palmtree Panic/Objects/3D Ramp/Main.asm:138-190, no sprite): while the
 * player is within +-$20 horizontally and $40 vertically, is moving left and is at or left of the marker's X,
 * it pins the player there (X, no speed, animation $37, jump flag, no stick, rolling status). Shipped
 * {@code FixBugs = 0}: the follow-up writes {@code oYRadius}/{@code oXRadius}/{@code oY} of the <em>marker</em>
 * ({@code (a0)}), not the player (Main.asm:183-185); it is kept as assembled.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class Obj3DRamp extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** The give-up animation id ($2B), tested by both variants (Main.asm:158,322). */
    private static final int GIVE_UP_ANIMATION = com.openggf.game.soniccd.player.CdAnimationIds.GIVE_UP;

    private int routine;
    /** oVar2A: the rest X (Main.asm:220). */
    private int baseX;
    /** oVar2E: the touch timer. */
    private int timer;
    private int mapFrame;
    private CdAnimator animator;
    private boolean mirrored;

    public Obj3DRamp(ObjectSpawn spawn) {
        super(spawn, "3D ramp");
    }

    /** The marker variant needs the layout's secondary subtype, which only the live level can answer. */
    private boolean fallMarker() {
        return subtype2() != 0;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return fallMarker() ? x : baseX; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (PpzStage.bossAct(services()) || player == null) return;
        if (fallMarker()) {
            fallMain(player);
            return;
        }
        if (routine == 0) init();
        main(player);
    }

    /** Obj3DRamp_Init (scddisasm/src/Level/Palmtree Panic/Objects/3D Ramp/Main.asm:212-225). */
    private void init() {
        routine = 2;
        priority = 1;
        tileWord = PpzObjectArt.RAMP_TILE;
        width = 0x20;
        yRadius = 0x20;
        baseX = x;
        mirrored = spawn.subtype() != 0;                // bset #0,oSprFlags/oFlags
        animator = PpzObjectArt.animator(artProvider(), e -> e.rampAnimations(), 0);
    }

    /** Obj3DRamp_Main (scddisasm/src/Level/Palmtree Panic/Objects/3D Ramp/Main.asm:229-340). */
    private void main(PlayableEntity player) {
        int d1;
        boolean onRamp = PpzRampPlayer.onRamp(player);
        boolean positioned = true;
        if (timer != 0) {
            if (animator != null) {
                animator.anim = onRamp ? 1 : 2;         // move.b #1,oAnim; addq.b #1 when off the ramp
                animator.step();
                mapFrame = animator.mapFrame();
            }
            d1 = slide(player, true);
        } else {
            mapFrame = 0;                               // .TimeRunSet
            d1 = 0;
            if (onRamp) d1 = slide(player, false);
        }
        x = (baseX + d1) & 0xFFFF;                      // .Move3D
        if (timer != 0) {
            timer--;                                    // subq.b #1,oVar2E(a0); bra.s .ChkTouch
        } else if (player instanceof AbstractPlayableSprite sprite && sprite.getAir()) {
            return;                                     // .SkipTimer: btst #1,oFlags(a6); bne.s .End
        }
        touch(player);
    }

    /** .GetChunkPos .. KeepFrame (Main.asm:250-287): the pad's slide for the player's position in the chunk. */
    private int slide(PlayableEntity player, boolean timerRunning) {
        int d0 = player.getCentreX() & 0xFF;
        if (mirrored) d0 = 0xFF - d0;                   // tst.b oSubtype(a0); beq.s .NoFlip
        d0 = Obj3DPlant.chunkPosition(d0);
        if (!timerRunning) mapFrame = d0 / 0x30;        // divu.w #$30,d0; move.b d0,oMapFrame(a0)
        int d1 = d0 >> 2;                               // lsr.w #2,d1
        d1 += d1 >> 1;                                  // lsr.w #1,d2; add.w d2,d1
        return mirrored ? -d1 : d1;
    }

    /** .ChkTouch (Main.asm:303-337). */
    private void touch(PlayableEntity player) {
        int d0 = (player.getCentreX() - x + width) & 0xFFFF;
        if ((short) d0 < 0 || d0 >= width * 2) return;
        d0 = (player.getCentreY() - y + yRadius) & 0xFFFF;
        if ((short) d0 < 0 || d0 >= yRadius * 2) return;
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (sprite.getAnimationId() == GIVE_UP_ANIMATION) return;
        if (timer == 0) timer = 60;
        sprite.setYSpeed((short) (sprite.getYSpeed() >= 0 ? 0xC00 : -0xC00));   // .TimerSet / .LaunchDown
    }

    /** Obj3DFall_Init and Obj3DFall_Main (scddisasm/src/Level/Palmtree Panic/Objects/3D Ramp/Main.asm:149-190). */
    private void fallMain(PlayableEntity player) {
        if (routine == 0) {
            routine = 2;                                // Obj3DFall_Init
            return;
        }
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (sprite.getAnimationId() == GIVE_UP_ANIMATION) return;
        if (((y - sprite.getCentreY() + 0x40) & 0xFFFF) >= 0x80) return;
        if (((x - sprite.getCentreX() + 0x20) & 0xFFFF) >= 0x40) return;
        if (sprite.getXSpeed() >= 0) return;            // tst.w d1; bpl.s .End
        if (x < (sprite.getCentreX() & 0xFFFF)) return; // cmp.w oX(a6),d0; bcs.s .End
        sprite.setCentreX((short) x);
        sprite.setXSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(0x37);
        sprite.setJumping(true);
        sprite.setStickToConvex(false);
        yRadius = 0xE;                                  // the marker's own fields, as assembled
        y = (y + 5) & 0xFFFF;
        sprite.setRollingFlagPreserveRadii(true);       // bset #2,oFlags(a6)
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || PpzStage.bossAct(services()) || fallMarker()) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_RAMP, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, mirrored, false);
    }

    public int mapFrame() { return mapFrame; }
    public int timer() { return timer; }
}
