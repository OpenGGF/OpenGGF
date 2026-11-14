package com.openggf.game.soniccd.objects.ppz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.game.soniccd.objects.common.CdSolidRules;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Palmtree Panic's tunnel path, object $07 ({@code ObjTunnelPath}, Palmtree Panic/Objects/Tunnel Path/Main.asm:119-399): an
 * invisible marker that captures a player within 16 pixels on both axes and carries him along a path of points in the stage
 * file (the subtype picks the path, {@code ObjTunnelPath_Paths}). The player is controlled by the object ({@code oPlayerCtrl}
 * $81, plus $40, invisible, with a non-zero {@code oSubtype2}), rolls (animation 2) and is moved point to point at a speed
 * given by the subtype ({@code ObjTunnelPath_GVels}, at least $1000), the velocity components set so that each leg takes the
 * number of frames kept in the high byte of {@code oTunnelSteps} ({@code subq.b} on a word: the leg length times 16, divided by
 * 256). At the last point the player is released and the object starts over. It is not drawn.
 * Not modelled: the time-warp saved-flags update at the top of the routine ({@code btst #7,timeZone}, :120-149).
 */
public final class PpzTunnelPath extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** move.w #FM_91,d0; jsr PlayFMSound (Tunnel Path/Main.asm:240-241); FM_91 is the second FM sound ID of the driver. */
    private static final int FM_91 = 0x91;
    /** cmpi.b #$2B,oAnim(a6) (:153). */
    private static final int ANIM_GIVE_UP = 0x2B;

    private int routine;
    private int steps;              // oTunnelSteps (word)
    private int index;              // oTunnelIndex high byte
    private int end;                // oTunnelEnd
    private int data;               // oTunnelData: stage-file offset of the first point
    private int targetX;            // oTunnelX
    private int targetY;            // oTunnelY
    private boolean holding;

    public PpzTunnelPath(ObjectSpawn spawn) {
        super(spawn, "Tunnel path");
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return routine < 4; }   // cmpi.b #4,oRoutine; bcc (:163-165)

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        if (sprite.getAnimationId() == ANIM_GIVE_UP || sprite.getDead()) return;   // :153-156
        switch (routine) {
            case 0 -> init();
            case 2 -> main(sprite);
            case 4 -> gotPlayer(sprite);
            case 6 -> movePlayer(sprite);
            default -> { }
        }
    }

    /** ObjTunnelPath_Init (:180-196): priority 1, width $10; the path from the stage file. */
    private void init() {
        priority = 1;
        width = 0x10;
        routine = 2;
        var provider = artProvider();
        if (provider == null) return;
        var entry = PpzObjectCatalogue.entry(provider.payload());
        var image = provider.image();
        int paths = entry.tunnelPathPaths();
        int d0 = ((getSpawn().subtype() & 0xFF) * 2) & 0xFE;
        int path = paths + (short) image.u16(paths + d0);                       // adda.w (a2,d0.w),a2
        index = image.u8(path);                                                 // oTunnelIndex: the high byte of the first word
        end = image.u8(path + 1);                                               // oTunnelEnd: its low byte
        data = path + 2;
        targetX = image.u16(data);
        targetY = image.u16(data + 2);
    }

    /** ObjTunnelPath_Main (:200-244). */
    private void main(AbstractPlayableSprite sprite) {
        if (((sprite.getCentreX() - x + 16) & 0xFFFF) >= 32) return;
        if (((sprite.getCentreY() - y + 16) & 0xFFFF) >= 32) return;
        if (sprite.isObjectControlled()) return;                                // tst.b oPlayerCtrl(a6)
        if (sprite.isHurt()) {                                                  // cmpi.b #4,oRoutine(a6): subq.b #2
            sprite.setHurt(false);
            sprite.setInvulnerableFrames(CdSolidRules.INSTANCE.hurtLandingInvulnerability());   // move.w #$78,oPlayerHurt
        }
        routine = 4;
        sprite.setObjectControlled(true);                                       // oPlayerCtrl = %10000001
        holding = true;
        if (subtype2() != 0) sprite.setHidden(true);                            // ori.b #$40
        sprite.setAnimationId(2);
        setPlayerGroundSpeed(sprite);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setPushing(false);                                               // bclr #5,oFlags(a6)
        sprite.setAir(true);                                                    // bset #1,oFlags(a6)
        sprite.setJumping(false);                                               // clr.b oPlayerJump(a6)
        NativePositionOps.writeXPosPreserveSubpixel(sprite, x);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, y);
        services().playSfx(FM_91);
    }

    /** ObjTunnelPath_GotPlayer (:248-253). */
    private void gotPlayer(AbstractPlayableSprite sprite) {
        setPlayerSpeed(sprite);
        routine += 2;
        services().playSfx(FM_91);
    }

    /** ObjTunnelPath_MovePlayer (:257-299). */
    private void movePlayer(AbstractPlayableSprite sprite) {
        int high = ((steps >> 8) - 1) & 0xFF;                                   // subq.b #1,oTunnelSteps(a0)
        steps = (high << 8) | (steps & 0xFF);
        if ((byte) high >= 0) {                                                 // bpl.s .Move
            NativePositionOps.addXPos16_16(sprite, (short) sprite.getXSpeed() << 8);
            NativePositionOps.addYPos16_16(sprite, (short) sprite.getYSpeed() << 8);
            return;
        }
        NativePositionOps.writeXPosPreserveSubpixel(sprite, targetX);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, targetY);
        int next = (index + 4) & 0xFF;
        if (next >= end) {                                                      // cmp.b oTunnelEnd,d1; bcs.s .NotEnd
            NativePositionOps.writeYPosPreserveSubpixel(sprite, sprite.getCentreY() & 0x7FF);   // .Release
            sprite.setObjectControlled(false);
            sprite.setHidden(false);
            holding = false;
            routine = 0;
            return;
        }
        index = next;
        var image = artProvider().image();
        targetX = image.u16(data + next);
        targetY = image.u16(data + next + 2);
        setPlayerSpeed(sprite);
    }

    /** ObjTunnelPath_SetPlayerGVel (:375-388): $1000, or a larger table value for a subtype below $80. */
    private void setPlayerGroundSpeed(AbstractPlayableSprite sprite) {
        int speed = 0x1000;
        int subtype = getSpawn().subtype() & 0xFF;
        if (subtype < 0x80) {                                                   // bmi.s .End
            var provider = artProvider();
            int table = PpzObjectCatalogue.entry(provider.payload()).tunnelPathSpeeds();
            int value = (short) provider.image().u16(table + (subtype & 0xF) * 2);
            if (value > (short) speed) speed = value;                           // cmp.w; ble.s .End
        }
        sprite.setGSpeed((short) speed);
    }

    /**
     * ObjTunnelPath_SetPlayerSpeed (:303-371): the velocity components and the step count towards the next point. The
     * longer axis moves at the ground speed; the other at the ratio of the two distances (divs.w on 16-bit differences
     * shifted up a word).
     */
    private void setPlayerSpeed(AbstractPlayableSprite sprite) {
        int gSpeed = sprite.getGSpeed();
        int d2 = gSpeed;
        int d3 = gSpeed;
        int dx = (short) (targetX - sprite.getCentreX());
        int d0 = dx & 0xFFFF;
        if (dx < 0) {                                                           // bge.s .PlayerLeft
            d0 = -dx & 0xFFFF;
            d2 = -d2;
        }
        int dy = (short) (targetY - sprite.getCentreY());
        int d1 = dy & 0xFFFF;
        if (dy < 0) {
            d1 = -dy & 0xFFFF;
            d3 = -d3;
        }
        if (d1 < d0) {                                                          // cmp.w d0,d1; bcs.s .XGreater
            int q0 = divs(dx << 16, d2);                                        // d0 = diff.x:0 / d2
            int q1 = dy == 0 ? 0 : divs(dy << 16, q0);                          // d1 = diff.y:0 / d0
            sprite.setYSpeed((short) q1);
            sprite.setXSpeed((short) d2);
            steps = (short) q0 < 0 ? (-(short) q0) & 0xFFFF : q0 & 0xFFFF;      // tst.w d0; bpl.s; neg.w d0
            return;
        }
        int q1 = divs(dy << 16, d3);                                            // d1 = diff.y:0 / d3
        int q0 = dx == 0 ? 0 : divs(dx << 16, q1);                              // d0 = diff.x:0 / d1
        sprite.setXSpeed((short) q0);
        sprite.setYSpeed((short) d3);
        steps = (short) q1 < 0 ? (-(short) q1) & 0xFFFF : q1 & 0xFFFF;
    }

    /** {@code divs.w}: the 32-bit dividend by the signed low word of the divisor; the quotient's low word (0 on overflow, as the register keeps the low word 0). */
    private static int divs(int dividend, int divisor) {
        int d = (short) divisor;
        if (d == 0) return 0;
        long q = (long) dividend / d;
        if (q < Short.MIN_VALUE || q > Short.MAX_VALUE) return 0;
        return (int) q & 0xFFFF;
    }

    /** Clears the control this path holds (the writes of the release, without moving the player). */
    @Override
    public void releasePlayersForVisualDirection() {
        if (!holding) return;
        holding = false;
        if (services().camera().getFocusedSprite() instanceof AbstractPlayableSprite player) {
            player.setObjectControlled(false);
            player.setHidden(false);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Never drawn: the sprite flags are set but the routine has no DrawObject (ObjTunnelPath, :119-168).
    }

    public int routine() { return routine; }
    public int steps() { return steps; }
}
