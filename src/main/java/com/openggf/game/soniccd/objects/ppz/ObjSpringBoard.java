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
 * The springboard, object $28 ({@code ObjSpringBoard}, Palmtree Panic/Objects/Springboard/Main.asm:107-376).
 * It is not solid: each frame {@code ObjSpringBoard_ChkPlayer} (scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:13-91) tests the player's box against one
 * of eight hit boxes. A touching player (not dead, not rising, no debug mode) is put on top of the board
 * (Y = the board's Y minus the player's Y radius, sub-pixel from the board) and the board moves to its fling
 * routine; there each frame that the player still touches, the player's Y speed becomes
 * {@code -min(ySpeed + $100, $A00)}, the player is thrown into the air as a rolling jump and the 64 frame timer
 * restarts; the board returns to idle when the timer runs out.
 *
 * <p>Routine 2 faces the standard way (hit boxes 5 and 6), routine 4 is the flipped board (a non-zero subtype or a
 * flipped placement: hit boxes 3 and 4, animations 4 and 2). The index also lists {@code NormalMain2} and
 * {@code FlipMain2} (routines 6 and 8); nothing sets those routines, so they cannot run (ObjSpringBoard .Index, scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:117-124;
 * ObjSpringBoard_FlipMain2 187-210, ObjSpringBoard_NormalMain2 295-318). The init has no draw, so the board shows from the second frame; the horizontal flip bits are
 * cleared (ObjSpringBoard_Init, Main.asm:140-149), the vertical one is kept.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSpringBoard extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** ObjSpringBoard_ChkPlayer's .Hitboxes: right, left, bottom, top offsets from the board (ObjSpringBoard_ChkPlayer .Hitboxes, scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:93-101). */
    private static final int[][] HITBOXES = {
            {16, -16, 16, -16}, {16, -16, 4, -4}, {9, -9, 56, 16}, {0, -24, 4, -4},
            {0, -24, 12, 0}, {24, 0, 4, -4}, {24, 0, 12, 0}, {32, -32, 32, -32}};
    private static final int NORMAL_IDLE = 5, NORMAL_FLING = 6, FLIP_IDLE = 3, FLIP_FLING = 4;
    /** {@code move.b #$40,oSprBrdFling(a0)} (ObjSpringBoard_FlipFling, Main.asm:233 and siblings). */
    private static final int FLING_FRAMES = 0x40;
    /** {@code cmpi.w #$A00,d0; bmi.s .SetYVel; move.w #$A00,d0} (ObjSpringBoard_FlipFling, Main.asm:226-229). */
    private static final int FLING_SPEED_CAP = 0xA00;

    private enum Routine { INIT, MAIN, FLIP_MAIN, FLING, FLIP_FLING }

    private Routine routine = Routine.INIT;
    private boolean flipped;
    private int timer;
    private CdAnimator animator;
    private int mapFrame;

    public ObjSpringBoard(ObjectSpawn spawn) {
        super(spawn, "Springboard");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == Routine.INIT) {
            init();
            return;                                             // ObjSpringBoard_Init ends in rts
        }
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        switch (routine) {
            case MAIN -> {
                if (check(sprite, NORMAL_IDLE)) {               // ObjSpringBoard_NormalMain (scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:273-293)
                    standOn(sprite);
                    routine = Routine.FLING;
                    anim(3);
                }
            }
            case FLIP_MAIN -> {
                if (check(sprite, FLIP_IDLE)) {                 // ObjSpringBoard_FlipMain (scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:165-185)
                    standOn(sprite);
                    routine = Routine.FLIP_FLING;
                    anim(4);
                }
            }
            case FLING -> {
                anim(1);                                        // ObjSpringBoard_NormalFling (scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:320-376)
                fling(sprite, NORMAL_FLING, Routine.MAIN, 3);
            }
            case FLIP_FLING -> {
                anim(2);                                        // ObjSpringBoard_FlipFling (scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:212-271)
                fling(sprite, FLIP_FLING, Routine.FLIP_MAIN, 4);
            }
            default -> { }
        }
        animate();                                              // ObjSpringBoard_Draw: AnimateObject, DrawObject
    }

    /** ObjSpringBoard_Init (scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:128-156). */
    private void init() {
        priority = 3;
        width = 0x10;
        yRadius = 4;
        CdObjectArtProvider provider = artProvider();
        if (provider != null) tileWord = provider.objectTileId(7, subtype2());   // moveq #7,d0; jsr SetObjectTileID
        flipped = spawn.subtype() != 0 || hFlip();               // tst.b oSubtype(a0); bne.s .Flip; btst #0,oSprFlags
        routine = flipped ? Routine.FLIP_MAIN : Routine.MAIN;
        animator = PpzObjectArt.animator(provider, e -> e.springBoardAnimations(), flipped ? 4 : 3);
    }

    private void anim(int id) {
        if (animator != null) animator.anim = id;
    }

    private void animate() {
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    /** Align the player on top of the board: {@code oY(a0) - oYRadius(a1)} as a long, so the sub-pixel is the board's. */
    private void standOn(AbstractPlayableSprite player) {
        player.setCentreY((short) (y - player.getYRadius()));
    }

    /**
     * The fling routines (ObjSpringBoard_FlipFling, Main.asm:212-271; NormalFling 320-376): another touch re-bounces the player; the timer then counts down and
     * the board goes back to its idle routine and animation.
     */
    private void fling(AbstractPlayableSprite player, int hitbox, Routine idle, int idleAnim) {
        if (check(player, hitbox)) {
            int speed = (player.getYSpeed() + 0x100) & 0xFFFF;  // move.w oYVel(a1),d0; addi.w #$100,d0
            if ((short) speed >= FLING_SPEED_CAP) speed = FLING_SPEED_CAP;
            player.setYSpeed((short) -speed);
            timer = FLING_FRAMES;
            boolean wasAir = player.getAir();
            player.setAir(true);                                // bset #1,oFlags(a1)
            if (wasAir) player.setJumping(false);               // bne: clr.b oPlayerJump(a1)
            player.setPushing(false);                           // bclr #5,oFlags(a1)
            player.setStickToConvex(false);                     // clr.b oPlayerStick(a1)
            player.applyCustomRadii(9, 0x13);                   // move.b #$13,oYRadius; move.b #9,oXRadius
            if (player.getRolling()) {
                player.setRollingJump(true);                    // bset #4,oFlags(a1)
            } else {
                player.applyCustomRadii(7, 0xE);
                com.openggf.sprites.NativePositionOps.addYPosPreserveSubpixel(player, 5);
                player.setRollingFlagPreserveRadii(true);       // bset #2,oFlags(a1)
                player.setAnimationId(2);                       // move.b #2,oAnim(a1)
            }
        }
        timer = (timer - 1) & 0xFF;                             // .Animate: subq.b #1,d0
        if (timer == 0) {
            routine = idle;
            anim(idleAnim);
            timer = FLING_FRAMES;
        }
    }

    /** ObjSpringBoard_ChkPlayer (scddisasm/src/Level/Palmtree Panic/Objects/Springboard/Main.asm:13-91): returns whether the player touches hit box {@code box}. */
    private boolean check(AbstractPlayableSprite player, int box) {
        if (player.isDebugMode() || player.getDead() || player.getYSpeed() < 0) return false;
        int[] hb = HITBOXES[box & 7];
        int playerX = player.getCentreX() & 0xFFFF;
        int playerRadius = player.getXRadius();
        if ((short) ((playerX - playerRadius) - (x + hb[0])) >= 0) return false;       // cmp.w d4,d5; bpl.s
        if ((short) ((x - -hb[1]) - (playerX + playerRadius)) >= 0) return false;      // neg.w d2; sub.w d2,d4
        int playerY = player.getCentreY() & 0xFFFF;
        playerRadius = player.getYRadius();
        if ((short) ((playerY - playerRadius) - (y + hb[2])) >= 0) return false;
        return (short) ((y - -hb[3]) - (playerY + playerRadius)) < 0;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == Routine.INIT || tileWord <= 0) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_SPRINGBOARD, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, vFlip());
    }

    public int mapFrame() { return mapFrame; }
    public boolean isFlipped() { return flipped; }
    public int timer() { return timer; }
}
