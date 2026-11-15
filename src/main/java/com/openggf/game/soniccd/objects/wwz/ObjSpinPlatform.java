package com.openggf.game.soniccd.objects.wwz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Spinning platform, object $28 ({@code ObjSpinPlatform}, Level/Wacky Workbench/Objects/Platform/Main
 * (Spinning).asm:229-545): a top-solid $10-wide platform that swings 64 pixels on a sine of the level
 * frame counter (subtype 0 and 1 along X, 2 and 3 along Y, odd types mirrored). A player who lands on it is
 * captured ({@code oPlayerCtrl} bit 0) and circled round the platform's X: the angle advances four a
 * frame, the radius is the landing distance, grows one every 64 angle steps, and the d-pad changes it. A jump
 * press releases the player with 1664/256 of the tangent velocity added to the speeds.
 *
 * <p>The player's rotation angle and radius ({@code oPlayerRotAngle}, {@code oPlayerRotDist}) are kept
 * here, with the platform that captured the player. {@code miniSonic} is not hosted (the player has no mini
 * form yet), so the jump uses the standard radii.
 */
public final class ObjSpinPlatform extends WwzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    private static final int INIT = 0, MAIN = 2;
    /** {@code move.b #$2D,oAnim(a1)} (:66). */
    private static final int SPIN_ANIMATION = 0x2D;
    /** {@code move.w #$680,d2} (:204). */
    private static final int JUMP_FORCE = 0x680;
    /** ObjSpinPlatform_MoveSonic .PlayerFrames (:143-158). */
    private static final int[] PLAYER_FRAMES = {0, 0, 0, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 5, 5, 5};

    private int spinX;
    private int spinY;
    private int xVel;
    private int rotAngle;
    private int rotDist;
    /** {@code oPlayerCtrl} bit 0 set by this platform and not yet cleared. */
    private boolean holdsPlayer;
    private CdAnimator animator;

    public ObjSpinPlatform(ObjectSpawn spawn) {
        super(spawn, "SpinPlatform");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == INIT) {
            init();
        }
        main(sprite(player));
    }

    /** ObjSpinPlatform_Init (:38-47); it falls through into ObjSpinPlatform_Main. */
    private void init() {
        priority = 1;
        tileWord = WwzObjectArt.SPIN_PLATFORM_TILE;
        spinX = x;
        spinY = y;
        yRadius = 12;
        width = 16;
        routine = MAIN;
    }

    /** ObjSpinPlatform_Main (:51-83). */
    private void main(AbstractPlayableSprite player) {
        move();
        animate();
        if (player == null || !solidObject(Entry.TOP)) return;      // bsr ObjSpinPlatform_Solid / beq.s .End
        player.setDirection(Direction.LEFT);                         // bset #0,oFlags(a1); oSprFlags bit 0
        if (!player.isObjectControlled()) {                          // bset #0,oPlayerCtrl(a1) / bne.s
            player.setObjectControlled(true);
            player.setObjectControlAllowsCpu(true);                  // bit 0 only: oPlayerCtrl's sign stays clear
            holdsPlayer = true;
            player.setAnimationId(SPIN_ANIMATION);                   // move.b #$2D,oAnim(a1)
            rotAngle = 0;
            int playerX = player.getCentreX() & 0xFFFF;
            int distance = playerX - (x & 0xFFFF);                   // sub.w oX(a0),d0 / bcc.s .PlayerRight
            if (distance < 0) {
                distance = -distance;                                // neg.w d0
                rotAngle = 0x80;                                     // move.b #$80,oPlayerRotAngle(a1)
            }
            rotDist = distance & 0xFF;                               // move.b d0,oPlayerRotDist(a1)
        }
        if (player.getDead()) return;                                // cmpi.b #6,oRoutine(a1) / bcc.s .End
        moveSonic(player);
    }

    /** ObjSpinPlatform_Move (:257-329): the four movement types by subtype. */
    private void move() {
        switch (getSpawn().subtype() & 3) {
            case 0 -> {                                              // MoveX
                int before = xPosition();
                x = (spinX + offset()) & 0xFFFF;
                xVel = ((xPosition() - before) >>> 8) & 0xFFFF;
            }
            case 1 -> {                                              // MoveX2
                int before = xPosition();
                x = (spinX - offset()) & 0xFFFF;
                xVel = ((xPosition() - before) >>> 8) & 0xFFFF;
            }
            case 2 -> y = (spinY - offset()) & 0xFFFF;               // MoveY
            default -> y = (spinY + offset()) & 0xFFFF;              // MoveY2
        }
    }

    /** ObjSpinPlatform_GetOffset (:320-329): {@code CalcSine(levelFrames & $FF) * 4 >> 4}. */
    private int offset() {
        var levels = services().levelManager();
        // The level loop increments levelFrames before RunObjects (Level/Main.asm:400-402), so the routine reads the
        // count of the frame it runs in; the engine's counter has not yet counted the frame being executed.
        // LoadedLevelFrameCounter makes the engine's counter levelFrames itself here; no local correction.
        int frame = (levels == null ? 0 : levels.getFrameCounter()) & 0xFF;
        int sine = TrigLookupTable.sinHex(frame);
        return (short) (sine * 4) >> 4;
    }

    private void animate() {
        if (animator == null) animator = WwzObjectArt.animator(artProvider(), "spinPlatformAnimations", 0);
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
        spriteFlip = (getSpawn().renderFlags() ^ animator.flipBits()) & 3;
    }

    /** ObjSpinPlatform_MoveSonic (:107-137). */
    private void moveSonic(AbstractPlayableSprite player) {
        rotAngle = (rotAngle + 4) & 0xFF;
        int cos = TrigLookupTable.cosHex(rotAngle);
        int offset = (rotDist * cos) >> 8;                          // muls.w d1,d0 / asr.l #8,d0
        NativePositionOps.writeXPosPreserveSubpixel(player, (x + offset) & 0xFFFF);
        // oAnimFrame(a1) = .PlayerFrames[angle >> 4]: the player's animation script is not run while captured.
        player.setAnimationFrameIndex(PLAYER_FRAMES[(rotAngle & 0xF0) >> 4]);
        if ((rotAngle & 0x3F) == 0) rotDist = (rotDist + 1) & 0xFF;  // addq.b #1,oPlayerRotDist(a1)
        checkDirections(player);
        checkJump(player);
    }

    /** ObjSpinPlatform_CheckDirs (:163-193). */
    private void checkDirections(AbstractPlayableSprite player) {
        boolean left = player.isLeftPressed();
        boolean right = player.isRightPressed();
        boolean playerRight = player.getCentreX() >= x;
        boolean grow = playerRight ? right : left;
        boolean shrink = playerRight ? left : right;
        if (grow) {
            rotDist = (rotDist + 1) & 0xFF;
        } else if (shrink) {
            rotDist = rotDist == 0 ? 0 : rotDist - 1;                // subq.b #1 / bcc.s .End / clr.b
        }
    }

    /** ObjSpinPlatform_CheckJump (:198-252). */
    private void checkJump(AbstractPlayableSprite player) {
        if (!player.isJumpJustPressed()) return;                      // playerCtrlTap & $70
        player.setObjectControlled(false);                            // clr.b oPlayerCtrl(a1)
        holdsPlayer = false;
        WwzPlayerProfile.clearControl(player);
        int angle = (player.getAngle() - 0x40) & 0xFF;
        int sine = TrigLookupTable.sinHex(angle);
        int cosine = TrigLookupTable.cosHex(angle);
        player.setXSpeed((short) (player.getXSpeed() + ((cosine * JUMP_FORCE) >> 8)));
        player.setYSpeed((short) (player.getYSpeed() + ((sine * JUMP_FORCE) >> 8)));
        player.setAir(true);                                          // bset #1,oFlags(a1)
        player.setPushing(false);                                     // bclr #5,oFlags(a1)
        player.setJumping(true);                                      // move.b #1,oPlayerJump(a1)
        player.setStickToConvex(false);                               // clr.b oPlayerStick(a1)
        if (player.getRolling()) {
            player.setRollingJump(true);                              // .RollJump: bset #4,oFlags(a1)
            return;
        }
        short centreX = player.getCentreX();
        short centreY = player.getCentreY();
        player.setRolling(true);                                      // $E/7 radii, bset #2, anim 2
        NativePositionOps.writeXPosPreserveSubpixel(player, centreX);
        NativePositionOps.writeYPosPreserveSubpixel(player, centreY + 5);   // addq.w #5,oY(a1)
        player.setAnimationId(2);
    }

    /** Clears the control this object still holds (the player-only half of its own release). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (!holdsPlayer) return;
        holdsPlayer = false;
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite sprite) sprite.setObjectControlled(false);
    }

    @Override public int solidXVelocity() { return (short) xVel; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override protected int despawnX() { return spinX; }

    @Override
    protected String sheetKey() {
        return WwzObjectArt.key(WwzObjectArt.SPIN_PLATFORM);
    }
}
