package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Spin disc, object $22 in all three Metallic Madness indexes ({@code SpinDiscObject}, objects/r8/spin_disc.asm:3-139): an
 * invisible zone (its init writes no map, tile or draw call; {@code SpinDiscSprites} is never loaded) that grabs a
 * grounded player inside its square, holds them with the {@code obj.var_38} flag
 * ({@link MmzStageSignals#setDiscHold}) and clamps their ground speed to between $400 and $F00 in the direction of
 * the subtype's high nibble. The routine ends in CheckObjectDespawn.
 */
public final class MmzSpinDisc extends MmzObject implements ObjectVisualFacing.NonDirectional {
    private final int centreX;                   // oVar32
    private final int centreY;                   // oVar30
    private final int radius;                    // oVar38: the square's half side
    private final int direction;                 // oVar36
    private boolean holding;                     // oVar3A

    public MmzSpinDisc(ObjectSpawn spawn) {
        super(spawn, "SpinDisc");
        // SpinDiscObject_0_Routine0 (objects/r8/spin_disc.asm:17-42): priority 4, oWidth 8; var_38 $48 (or $30 for a
        // subtype whose low nibble is set); var_36 = (byte)(subtype & $F0) << 3.
        this.priority = 4;
        this.width = 8;
        this.centreX = spawn.x();
        this.centreY = spawn.y();
        this.radius = (spawn.subtype() & 0xF) != 0 ? 0x30 : 0x48;
        this.direction = (short) (((byte) (spawn.subtype() & 0xF0)) << 3);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (player instanceof AbstractPlayableSprite sprite) grab(sprite);   // sub_20A770 (:49-71)
    }

    /** sub_20A770 (objects/r8/spin_disc.asm:49-134). */
    private void grab(AbstractPlayableSprite player) {
        int side = radius * 2;
        int dx = ((player.getCentreX() - centreX) + radius) & 0xFFFF;
        int dy = ((player.getCentreY() - centreY) + radius) & 0xFFFF;
        boolean inside = dx < side && dy < side;                   // cmp.w d3,d0; bcc (unsigned)
        MmzStageSignals signals = signals();
        if (!inside) {
            // loc_20A7A8 (:72-77): leaving clears the player's hold and the object's flag.
            if (holding) {
                if (signals != null) signals.setDiscHold(0);
                player.setStickToConvex(false);                    // clr.b obj.var_38(a1)
                holding = false;
            }
            return;
        }
        if (player.getAir()) {                                     // btst #1,oFlags(a1); bne (:49-71)
            holding = false;
            return;
        }
        if (!holding) {
            // loc_20A7B8 (:83-95): first contact.
            holding = true;
            if (!player.getRolling()) player.setAnimationId(0);    // clr.b oAnim(a1) unless rolling
            player.setPushing(false);                              // bclr #5,oFlags(a1)
            // move.b #1,oPrevAnim(a1) (:93): the player's next animation pass restarts its script
            // (index 0, timer 0) unless the animation is 1.
            if (player.getAnimationManager() != null) player.getAnimationManager().publishPreviousAnimationId(1);
            if (signals != null) signals.setDiscHold(1);           // move.b #1,oVar38(a1)
            player.setStickToConvex(true);                         // the player's stick flag (oVar38)
        }
        // loc_20A7E2 (:96-129): clamp the ground speed to $400..$F00 in the disc's direction.
        int gSpeed = player.getGSpeed();
        if (direction < 0) {
            if (gSpeed > -0x400) player.setGSpeed((short) -0x400);
            else if (gSpeed < -0xF00) player.setGSpeed((short) -0xF00);
        } else {
            if (gSpeed < 0x400) player.setGSpeed((short) 0x400);
            else if (gSpeed > 0xF00) player.setGSpeed((short) 0xF00);
        }
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
    }
}
