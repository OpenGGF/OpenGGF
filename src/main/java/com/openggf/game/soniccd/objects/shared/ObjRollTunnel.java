package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Roll tunnel ({@code ObjRollTunnel}, Collision Chaos/Objects/Roll Tunnel/Main.asm:7-210;
 * {@code RollTunnelObject}, objects/roll_tunnel.asm:3-179), also Palmtree Panic's and Wacky
 * Workbench act 1 present's spin tunnel tag ({@code ObjSpinTunnel}, Spin Tunnel/Main (Tag).asm:7-170),
 * which is the same body. An invisible marker: while the player is within $28 pixels on both
 * axes it forces a roll and, by subtype, a minimum speed:
 * <ul>
 *   <li>0: horizontal, |X velocity| at least $A00 ($D00 in Stardust Speedway, {@code cmpi.b #5,zone});</li>
 *   <li>1: vertical, |Y velocity| at least $D00, airborne;</li>
 *   <li>2, 3: X velocity +-$D00 from the held direction, for upward (2) or downward (3) travel;</li>
 *   <li>4: Up while moving right sends the player up at -$A00.</li>
 * </ul>
 * USA build: the X radius write is {@code oXRadius} (Roll Tunnel/Main.asm:107-123, REGION=USA).
 * It draws only with the debug cheat on ({@code tst.b debugCheat}, Roll Tunnel/Main.asm:12-14),
 * which is not hosted, so it never draws.
 */
public final class ObjRollTunnel extends CdObject implements ObjectVisualFacing.NonDirectional {
    /** .GetMaxSpeed: move.w #$A00,d1; cmpi.b #5,zone; move.w #$D00,d1 (Roll Tunnel/Main.asm:52-56). */
    private static final int MIN_X_SPEED = 0xA00, MIN_X_SPEED_ZONE_5 = 0xD00, ZONE_5 = 5;
    /** .GetVerticalSpeed / .SetControlledSpeed: $D00 (Roll Tunnel/Main.asm:88-91, 146-147). */
    private static final int TUNNEL_SPEED = 0xD00;
    /** .Reverse: move.w #-$A00,d0 (Roll Tunnel/Main.asm:169). */
    private static final int REVERSE_SPEED = -0xA00;
    /** ObjRollTunnel_Main: cmpi.b #$2B,oAnim(a1) (Roll Tunnel/Main.asm:39), the give-up animation. */
    private static final int ANIM_GIVE_UP = 0x2B;
    /** ObjRollTunnel_CheckPlayer: addi.w #$28; cmpi.w #$50 on both axes (Roll Tunnel/Main.asm:193-205). */
    private static final int RANGE = 0x28;
    /** .Activate: move.b #$E,oYRadius; move.b #7,oXRadius; addq.w #5,oY; move.b #2,oAnim (Roll Tunnel/Main.asm:103-125). */
    private static final int ROLL_DROP = 5;

    private int routine;

    public ObjRollTunnel(ObjectSpawn spawn) {
        super(spawn, "Roll tunnel");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) {
            // ObjRollTunnel_Init falls through into ObjRollTunnel_Main (Roll Tunnel/Main.asm:27-37).
            routine = 2;
        }
        if (!(player instanceof AbstractPlayableSprite sprite)) return;
        // cmpi.b #$2B,oAnim(a1); beq; cmpi.b #6,oRoutine(a1); bcc (Roll Tunnel/Main.asm:39-42): routine 6 is death.
        if (sprite.getAnimationId() == ANIM_GIVE_UP || sprite.getDead()) return;
        if (!inRange(sprite)) return;
        int subtype = getSpawn().subtype() & 0xFF;
        if (subtype == 0) {
            horizontal(sprite);
        } else if (subtype < 2) {
            setVertical(sprite, atLeast(sprite.getYSpeed(), TUNNEL_SPEED));
        } else {
            controlled(sprite, subtype);
        }
        activate(sprite);
    }

    /** ObjRollTunnel_CheckPlayer (Roll Tunnel/Main.asm:174-210): debug mode off, |dx| and |dy| within $28. */
    private boolean inRange(AbstractPlayableSprite sprite) {
        if (sprite.isDebugMode()) return false;
        int dx = (sprite.getCentreX() - x + RANGE) & 0xFFFF;
        int dy = (sprite.getCentreY() - y + RANGE) & 0xFFFF;
        return dx < 2 * RANGE && dy < 2 * RANGE;
    }

    /** Subtype 0 (Roll Tunnel/Main.asm:48-77). */
    private void horizontal(AbstractPlayableSprite sprite) {
        int min = zone() == ZONE_5 ? MIN_X_SPEED_ZONE_5 : MIN_X_SPEED;
        short speed = atLeast(sprite.getXSpeed(), min);
        sprite.setXSpeed(speed);
        sprite.setGSpeed(speed);
        // On the ceiling ((oAngle + $20) & $C0 == $80) the ground speed is negated.
        if (((sprite.getAngle() + 0x20) & 0xC0) == 0x80) sprite.setGSpeed((short) -speed);
    }

    /** .Controlled (Roll Tunnel/Main.asm:132-170): p1CtrlHold bits 0 up, 2 left, 3 right. */
    private void controlled(AbstractPlayableSprite sprite, int subtype) {
        if (subtype == 4) {
            // .Reverse: tst.w oXVel; bmi .Activate; btst #0,d1; beq .Activate.
            if (sprite.getXSpeed() < 0 || !sprite.isUpPressed()) return;
            setVertical(sprite, (short) REVERSE_SPEED);
            return;
        }
        if (subtype == 2) {
            if (sprite.getYSpeed() >= 0) return;   // tst.w oYVel; bpl.s .Activate
        } else if (sprite.getYSpeed() < 0) {
            return;                               // .CheckDown: tst.w oYVel; bmi.s .Activate
        }
        int speed = TUNNEL_SPEED;
        if (!sprite.isRightPressed()) {
            if (!sprite.isLeftPressed()) return;
            speed = -speed;
        }
        if (subtype != 2) sprite.setAir(true);    // .SetControlledXSpeed: bset #1,oFlags(a1) unless subtype 2
        sprite.setXSpeed((short) speed);
        sprite.setGSpeed((short) speed);
    }

    /** .SetVerticalGroundSpeed: Y velocity and ground speed, airborne (Roll Tunnel/Main.asm:98-101). */
    private static void setVertical(AbstractPlayableSprite sprite, short speed) {
        sprite.setYSpeed(speed);
        sprite.setGSpeed(speed);
        sprite.setAir(true);
    }

    /** |v| raised to at least {@code min}, keeping v's sign (bpl/neg; cmp.w; bcc; tst.w; bpl; neg). */
    private static short atLeast(short v, int min) {
        int magnitude = Math.abs((int) v);
        if (Integer.compareUnsigned(magnitude & 0xFFFF, min) < 0) magnitude = min;
        return (short) (v < 0 ? -magnitude : magnitude);
    }

    /** .Activate: bset #2,oFlags(a1); bne .End; then the roll radii, Y and animation (Roll Tunnel/Main.asm:103-125). */
    private static void activate(AbstractPlayableSprite sprite) {
        if (sprite.getRolling()) return;
        short centreX = sprite.getCentreX();
        short centreY = sprite.getCentreY();
        sprite.setRolling(true);                  // $E/7 radii, animation 2 (roll)
        NativePositionOps.writeXPosPreserveSubpixel(sprite, centreX);
        NativePositionOps.writeYPosPreserveSubpixel(sprite, centreY + ROLL_DROP);
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Drawn only under the debug cheat (Roll Tunnel/Main.asm:12-14), which is not hosted.
    }
}
