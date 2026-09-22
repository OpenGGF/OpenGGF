package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;

import java.util.List;

/** SKL $50, Obj_DEZConveyorBelt / sub_47854 (sonic3k.asm:93515-93559). */
public final class S3kDezConveyorBeltObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    public S3kDezConveyorBeltObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZConveyorBelt");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var query = services().playerQuery();
        PlayableEntity main = query.mainPlayerOrNull();
        if (main == null) main = player;
        carry(main);
        PlayableEntity second = query.nativeP2OrNull();
        if (second != main) carry(second);
    }

    private void carry(PlayableEntity player) {
        if (player == null) return;
        // Init falls through. Subtype bit 7 is ignored, and zero width admits
        // nobody. Both bounds are unsigned word comparisons, upper-exclusive.
        int halfWidth = (spawn.subtype() & 0x7F) << 3;
        int dx = (player.getCentreX() - getX() + halfWidth) & 0xFFFF;
        if (dx >= halfWidth * 2) return;
        int dy = (player.getCentreY() - getY() + 0x30) & 0xFFFF;
        if (dy >= 0x60 || player.getAir()) return;
        int shift = (spawn.renderFlags() & 1) == 0 ? 2 : -2;
        // sub.w #$30,d1 / bcs: above the centre keeps the direction; at or
        // below the centre reverses it. No reverse-gravity flag is read here.
        if (dy >= 0x30) shift = -shift;
        // add.w d0,x_pos(a1) retains the fractional word and all velocity.
        NativePositionOps.writeXPosPreserveSubpixel(player, player.getCentreX() + shift);
    }

    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        // Delete_Sprite_If_Not_In_Range uses the fixed $280 coarse-X limit.
        return isCoarseXOutOfRange(getX(), cameraX, 0x280);
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        // The belt is level art. This controller has no sprite mapping.
    }
}
