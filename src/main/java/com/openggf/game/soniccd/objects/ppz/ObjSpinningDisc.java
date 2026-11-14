package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.art.CdObjectArtProvider;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * The spinning disc, object $09 ({@code ObjSpinningDisc}, Palmtree Panic/Objects/Spinning Disc/Main.asm:7-235): a
 * $20 wide, 8 high solid (tile from {@code SetObjectTileID} 6, priority 4) that only acts while drawn. A player
 * that lands on it ({@code SolidObject} returns top or bottom contact; the solid routine lets a player below pass
 * under it, role {@code PASS_UNDER}) is taken over ({@code oPlayerCtrl} bit 0): animation $2D, circling the disc's
 * X at the distance it landed ({@code oPlayerRotDist}) starting at angle 0 (right of the centre) or $80 (left),
 * +8 of angle a frame, X = disc X + dist * cos(angle) / 256, mapping frame from the angle's top nibble
 * ({@code ObjSpinningDisc_PlayerFrames}), the distance growing by one every $40 of angle and by the held
 * direction (away from the centre grows it, towards it shrinks it to 0). A tapped A, B or C releases the
 * player with a jump of $680 ($380 in water) along {@code oAngle - $40}, the roll-jump setup of a normal jump and
 * {@code FM_A0}. A player in routine 6 or later is let go of the circling but keeps the control bit.
 *
 * <p>Not modelled: {@code miniSonic} radii (no stage sets it here) and the disc's {@code timeStopTimer}
 * animation gate (no time stop is hosted); the water test reads the disc's {@code oFlags} bit 6, which nothing sets
 * in Palmtree Panic. Control uses the shared object-control seam (movement skipped, touch response running).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSpinningDisc extends CdObject implements ReevaluatingSolidObject,
        ObjectVisualFacing.NonDirectional {
    /** ObjSpinningDisc_PlayerFrames (scddisasm/src/Level/Palmtree Panic/Objects/Spinning Disc/Main.asm:111). */
    private static final int[] PLAYER_FRAMES = {0, 0, 0, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 5};
    /** {@code move.b #$2D,oAnim(a1)} (ObjSpinningDisc_Main, Main.asm:56). */
    private static final int DISC_ANIMATION = 0x2D;
    private static final int JUMP_SPEED = 0x680, WATER_JUMP_SPEED = 0x380;

    private int routine;
    private CdAnimator animator;
    private int mapFrame;
    /** The player's oPlayerRotAngle and oPlayerRotDist (Level/_Definitions.inc:88-89). */
    private int rotAngle;
    private int rotDist;
    private boolean held;

    public ObjSpinningDisc(ObjectSpawn spawn) {
        super(spawn, "Spinning disc");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity entity) {
        if (PpzStage.standDown(services(), this)) return;
        if (routine == 0) {
            init();
        }
        main();
        animate();
    }

    /** ObjSpinningDisc_Init (scddisasm/src/Level/Palmtree Panic/Objects/Spinning Disc/Main.asm:36-47). */
    private void init() {
        routine = 2;
        priority = 4;
        width = 0x10;
        yRadius = 8;
        CdObjectArtProvider provider = artProvider();
        if (provider != null) tileWord = provider.objectTileId(6, subtype2());   // moveq #6,d0; jsr SetObjectTileID(pc)
        animator = PpzObjectArt.animator(provider, e -> e.spinningDiscAnimations(), 0);
    }

    private void animate() {
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
    }

    /** ObjSpinningDisc_Main (scddisasm/src/Level/Palmtree Panic/Objects/Spinning Disc/Main.asm:49-79). */
    private void main() {
        if (!renderFlagOnScreen()) return;                       // tst.b oSprFlags(a0); bpl.w .End
        if (!(services().camera().getFocusedSprite() instanceof AbstractPlayableSprite player)) return;
        if (!solidObject(Entry.SOLID)) return;                   // bsr.s ObjSpinningDisc_SolidObj; beq.s .End
        if (!held) {                                             // bset #0,oPlayerCtrl(a1); bne.s .DidInit
            held = true;
            player.setObjectControlled(true);
            player.setObjectControlAllowsCpu(true);
            player.setAnimationId(DISC_ANIMATION);
            rotAngle = 0;
            int d0 = (player.getCentreX() - x) & 0xFFFF;
            if (d0 > 0x7FFF) {                                   // sub.w oX(a0),d0; bcc.s .PlayerRight
                d0 = -d0 & 0xFFFF;
                rotAngle = 0x80;
            }
            rotDist = d0 & 0xFF;
        }
        if (player.getDead()) return;                            // cmpi.b #6,oRoutine(a1); bcc.s .End
        moveSonic(player);
    }

    /** ObjSpinningDisc_Main .MoveSonic (scddisasm/src/Level/Palmtree Panic/Objects/Spinning Disc/Main.asm:81-109). */
    private void moveSonic(AbstractPlayableSprite player) {
        rotAngle = (rotAngle + 8) & 0xFF;
        int cos = TrigLookupTable.cosHex(rotAngle);
        int d0 = (rotDist * cos) >>> 8;                          // muls.w d1,d0; lsr.l #8,d0
        NativePositionOps.writeXPosPreserveSubpixel(player, (x + d0) & 0xFFFF);   // move.w oX(a0),oX(a1); add.w d0,oX(a1)
        // oAnimFrame(a1) is the animation script's frame index, not a mapping frame: the script ($2D) maps it, and the
        // frame the player drew this pass is unchanged until the next one (Main.asm:96).
        player.setAnimationFrameIndex(PLAYER_FRAMES[(rotAngle & 0xF0) >> 4]);
        if ((rotAngle & 0x3F) == 0) rotDist = (rotDist + 1) & 0xFF;               // andi.b #$3F,d1; bne.s .ChkInput
        checkDirections(player);
        checkJump(player);
    }

    /** ObjSpinningDisc_CheckDirs (scddisasm/src/Level/Palmtree Panic/Objects/Spinning Disc/Main.asm:114-155). */
    private void checkDirections(AbstractPlayableSprite player) {
        boolean left = player.isLeftPressed();
        boolean right = player.isRightPressed();
        boolean playerRight = (player.getCentreX() & 0xFFFF) >= (x & 0xFFFF);      // sub.w oX(a0),d0; bcc.s .ChkRight2
        boolean away = playerRight ? right : left;
        boolean toward = playerRight ? left : right;
        if (away) {
            rotDist = (rotDist + 1) & 0xFF;
        } else if (toward) {
            rotDist = Math.max(0, rotDist - 1);                  // subq.b #1; bcc.s .End; clr
        }
    }

    /** Clears the control this disc holds (the writes of .CheckJump's release, without the launch). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (!held) return;
        held = false;
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite player) {
            player.setObjectControlled(false);
        }
    }

    /** ObjSpinningDisc_CheckJump (scddisasm/src/Level/Palmtree Panic/Objects/Spinning Disc/Main.asm:157-226). */
    private void checkJump(AbstractPlayableSprite player) {
        if (!player.isJumpJustPressed()) return;                 // move.b playerCtrlTap.w,d0; andi.b #$70,d0
        held = false;
        player.setObjectControlled(false);                       // clr.b oPlayerCtrl(a1)
        int speed = JUMP_SPEED;                                  // btst #6,oFlags(a0) (water) not set in Palmtree Panic
        int angle = (player.getAngle() - 0x40) & 0xFF;
        int xVel = (TrigLookupTable.cosHex(angle) * speed) >> 8;
        int yVel = (TrigLookupTable.sinHex(angle) * speed) >> 8;
        player.setXSpeed((short) (player.getXSpeed() + xVel));
        player.setYSpeed((short) (player.getYSpeed() + yVel));
        player.setAir(true);                                     // bset #1,oFlags(a1)
        player.setPushing(false);                                // bclr #5,oFlags(a1)
        player.setJumping(true);                                 // move.b #1,oPlayerJump(a1)
        player.setStickToConvex(false);                          // clr.b oPlayerStick(a1)
        services().playSfx(PpzSounds.FM_A0);
        player.applyCustomRadii(9, 0x13);
        if (player.getRolling()) {
            player.setRollingJump(true);                         // .RollJump: bset #4,oFlags(a1)
        } else {
            player.applyCustomRadii(7, 0xE);
            NativePositionOps.addYPosPreserveSubpixel(player, 5);
            player.setRollingFlagPreserveRadii(true);            // bset #2,oFlags(a1)
            player.setAnimationId(2);                            // move.b #2,oAnim(a1)
        }
    }

    @Override public Role solidRole() { return Role.PASS_UNDER; }
    @Override public int solidWidth() { return width; }
    @Override public int solidYRadius() { return yRadius; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || tileWord <= 0) return;
        var provider = artProvider();
        if (provider == null) return;
        String key = PpzObjectArt.key(provider, PpzObjectArt.PPZ_DISC, tileWord, services().camera().getX());
        if (key != null) drawFrame(key, mapFrame, false, false);
    }

    public int mapFrame() { return mapFrame; }
    public int rotationAngle() { return rotAngle; }
    public int rotationDistance() { return rotDist; }
    public boolean holdsPlayer() { return held; }
}
