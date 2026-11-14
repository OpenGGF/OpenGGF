package com.openggf.game.soniccd.objects.shared;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Metallic Madness's tunnel path, object $07 ({@code TunnelPathObject}, objects/r8/tunnel_path.asm:80-377): Quartz Quadrant's body (the
 * $20 by $20 capture box, absolute four-byte points, {@link TunnelPathBase}) at a fixed ground speed of $1000
 * ({@code sub_208478}, :355-366: the subtype table's largest entry is $1000, so a larger one never replaces it). The object with subtype
 * $80 is the free-path form driven by the pad ({@code loc_208154}, :3-75), hosted as {@link #runsAlternativeRoutine}. The capture also clears the player's
 * tile priority bit ({@code andi.b #$7F,obj.sprite_tile(a6)}, :199), which has no typed input and is not modelled.
 */
public final class ObjMmzTunnelPath extends TunnelPathBase {
    public ObjMmzTunnelPath(ObjectSpawn spawn) {
        super(spawn);
    }

    @Override protected String pathsColumn() { return "mmzTunnelPaths"; }

    /** {@code move.w #$1000,obj.ground_speed(a6)} (objects/r8/tunnel_path.asm:356). */
    @Override protected int captureGroundSpeed() { return 0x1000; }

    @Override protected int boxX() { return 0x10; }

    @Override protected int boxY() { return 0x10; }

    @Override protected int recordSize() { return 4; }

    /** {@code cmpi.b #$80,obj.subtype(a0); beq.w loc_208154} (:119-121). */
    private boolean freeForm() { return (getSpawn().subtype() & 0xFF) == 0x80; }

    /** {@code p1_joy_hold} direction bits of the previous frame, for {@code player_joy_tap}. */
    private int previousHold;
    private int tap;

    @Override
    public void update(int vIntRunCount, com.openggf.game.PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) {
            int hold = (sprite.isUpPressed() ? 1 : 0) | (sprite.isDownPressed() ? 2 : 0)
                    | (sprite.isLeftPressed() ? 4 : 0) | (sprite.isRightPressed() ? 8 : 0);
            tap = hold & ~previousHold;                                    // the frame's newly pressed directions
            previousHold = hold;
        }
        super.update(vIntRunCount, player);
    }

    /**
     * The free-path form (loc_208154, :3-75): Routine0 (:19-27) takes the object's own position as the target; Routine2 is the
     * shared capture; Routine4 waits for a direction tap and moves the target $10 pixels that way (up, down, left, right,
     * :29-58) before starting the $1000 ride ({@code loc_2081E0}, :68-72), which the shared Routine6 carries and, as the path
     * is one record ({@code obj.var_3a} = 4), ends. It always ends in CheckObjectDespawn.
     */
    @Override
    protected boolean runsAlternativeRoutine(AbstractPlayableSprite player) {
        if (!freeForm()) return false;
        switch (routine) {
            case 0 -> {
                priority = 1;
                pathIndex = 0;                                              // move.w #4,obj.var_3a(a0): index byte 0, limit byte 4
                pathLimit = 4;
                targetX = x & 0xFFFF;
                targetY = y & 0xFFFF;
                routine = 2;
            }
            case 2 -> waiting(player);
            case 4 -> {
                if ((tap & 1) != 0) targetY = (targetY - 0x10) & 0xFFFF;
                else if ((tap & 2) != 0) targetY = (targetY + 0x10) & 0xFFFF;
                else if ((tap & 4) != 0) targetX = (targetX - 0x10) & 0xFFFF;
                else if ((tap & 8) != 0) targetX = (targetX + 0x10) & 0xFFFF;
                else break;
                routine += 2;                                               // loc_2081E0
                player.setGSpeed((short) 0x1000);
                computeVelocity(player);
            }
            default -> carrying(player);
        }
        return true;
    }

    @Override
    protected int playerControl(AbstractPlayableSprite player) {
        return player.isObjectControlled() ? 1 : 0;
    }

    @Override
    protected void releaseControl(AbstractPlayableSprite player) {
        // clr.b obj.var_2c(a6): bit 0 is the whole byte here.
    }
}
