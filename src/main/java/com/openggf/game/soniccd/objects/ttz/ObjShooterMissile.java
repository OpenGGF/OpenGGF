package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * The shooter's missile ({@code loc_20F32E}, objects/r4/shooter.asm:64-78; the shooter ID with subtype $FF):
 * tile $318, $20 wide, 6 tall. It moves by its 16.16 velocity every frame and, while the player is inside a
 * box around it ({@code sub_20F35E}, :85-98: Y within [-$C, +$C) and X within [-$14, +$14) of the missile
 * after the carry games below), sets the player's ground speed to the velocity over 256. It ends in
 * CheckObjectDespawn. The animation is {@code MissileAnims}.
 *
 * <p>The box test reproduces the routine's flag use exactly: the first compare's borrow is tested, the
 * second runs only on its carry.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
final class ObjShooterMissile extends TtzObject implements ObjectVisualFacing.NonDirectional {
    private final int velocity;
    private final boolean facingRight;
    private int xFixed;
    private boolean initialised;
    private com.openggf.game.soniccd.objects.common.CdAnimator animator;
    private int mapFrame;

    ObjShooterMissile(int x, int y, int velocity, boolean facingRight, int priority) {
        super(childSpawn(x, y, 0x42, 0xFF, 0), "ShooterMissile");
        this.velocity = velocity;
        this.facingRight = facingRight;
        this.priority = priority;
        this.xFixed = x << 16;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised) {
            initialised = true;
            width = 0x20;
            yRadius = 6;
            tileWord = TtzObjectArt.MISSILE_TILE;
            animator = TtzObjectArt.animator(artProvider(), "missileAnimations", 0);
        }
        if (player != null && !insideBox(player)) {
            // bcc.s loc_20F340: no carry, the player is outside
        } else if (player != null) {
            player.setGSpeed((short) (velocity >> 8));          // asr.l #8,d0; move.w d0,obj.ground_speed(a1)
        }
        xFixed += velocity;
        x = xFixed >>> 16;
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    /** sub_20F35E (objects/r4/shooter.asm:82-93): carry set means inside. */
    private boolean insideBox(PlayableEntity player) {
        // subi.w #-$C ; subi.w #$18 ; bcc.s: carry clear returns "outside"
        int d0 = (player.getCentreY() - y) & 0xFFFF;
        d0 = (d0 + 0xC) & 0xFFFF;
        if (d0 >= 0x18) return false;
        int e0 = (player.getCentreX() - x) & 0xFFFF;
        e0 = (e0 + 0x14) & 0xFFFF;
        return e0 < 0x28;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(TtzObjectArt.MISSILE, mapFrame, facingRight, false);
    }
}
