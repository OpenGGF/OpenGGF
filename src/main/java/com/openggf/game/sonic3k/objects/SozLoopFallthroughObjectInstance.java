package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.util.List;

/** SKL $3B, Obj_SOZLoopFallthrough ($4044A), shipped-ROM loop exit controller. */
public final class SozLoopFallthroughObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private final FbzParticipantStateTable participants = new FbzParticipantStateTable(1);

    public SozLoopFallthroughObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SOZLoopFallthrough");
    }

    @Override public void update(int vIntRunCount, PlayableEntity leader) {
        if (leader instanceof AbstractPlayableSprite player) updatePlayer(player);
        for (var participant : services().playerQuery().playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
            if (participant != leader && participant instanceof AbstractPlayableSprite player) updatePlayer(player);
        }
        coarseXCullViewport(spawn.x());
    }

    private void updatePlayer(AbstractPlayableSprite player) {
        int slot = participants.slot(player);
        if (participants.flag(slot, 0)) {
            // loc_40508: integrate old signed WORD velocities, then compare signed
            // WORD (new y - extent) with the controller's y. Equality releases.
            NativePositionOps.addXPos16_16(player, player.getXSpeed() << 8);
            int oldYSpeed = player.getYSpeed();
            player.setYSpeed((short) (oldYSpeed + 0x38));
            int nativeY = ((player.getCentreY() << 16) | player.getYSubpixelRaw()) + (oldYSpeed << 8);
            NativePositionOps.writeYPosPreserveSubpixel(player, nativeY >> 16);
            player.setSubpixelRaw(player.getXSubpixelRaw(), nativeY & 0xFFFF);
            if ((short) (player.getCentreY() - ((spawn.subtype() & 0x7F) << 4)) >= (short) spawn.y()) {
                participants.flag(slot, 0, false);
                player.setOnObject(false);
                ObjectControlState.none().applyTo(player);
            }
        } else if (((player.getCentreX() - spawn.x() + 0x10) & 0xFFFF) < 0x20
                && ((player.getCentreY() - spawn.y() + 0x10) & 0xFFFF) < 0x20
                && !player.isOnObject() && !player.isHurt() && !player.getDead()
                && !player.isObjectControlled() && player.getYSpeed() >= 0x800) {
            // sub_40474 returns after capture: no displacement or gravity yet.
            participants.flag(slot, 0, true);
            player.setOnObject(true);
            player.setRollingFlagPreserveRadii(true);
            player.applyCustomRadii(7, 14);
            player.setAnimationId(2);
            player.setRollingJump(false);
            player.setDoubleJumpFlag(0);
            player.setJumping(false);
            player.setAir(true);
            ObjectControlState.nativeBit7FullControl().applyTo(player); // object_control = $81
            player.setXSpeed((short) 0);
        }
        if (participants.flag(slot, 0) && player.isOnObject()) {
            var manager = services().objectManager();
            if (manager != null) manager.markObjectSupportThisFrame(player);
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible controller: Delete_Sprite_If_Not_In_Range does not draw.
    }
}
