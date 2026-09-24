package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/** Invisible SKL {@code $50}, {@code Obj_DEZConveyorBelt} (sonic3k.asm:93556-93603). */
public final class S3kDezConveyorBeltObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int halfWidth;
    private int step;

    public S3kDezConveyorBeltObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZConveyorBelt");
        halfWidth = (spawn.subtype() & 0x7F) << 3;
        step = (spawn.renderFlags() & 1) == 0 ? 2 : -2;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (tryServices() == null || services().playerQuery() == null) {
            return;
        }
        for (PlayableEntity candidate : services().playerQuery()
                .playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            apply(candidate);
        }
        if (!isInRangeAt(getX())) {
            setDestroyedByOffscreen();
        }
    }

    void apply(PlayableEntity player) {
        if (!(player instanceof AbstractPlayableSprite sprite) || player.getAir()) {
            return;
        }
        int dx = (player.getCentreX() - getX() + halfWidth) & 0xFFFF;
        if (dx >= halfWidth * 2) {
            return;
        }
        int relativeY = player.getCentreY() - getY();
        int dy = (relativeY + 0x30) & 0xFFFF;
        if (dy >= 0x60) {
            return;
        }
        int signedStep = relativeY < 0 ? step : -step;
        NativePositionOps.writeXPosPreserveSubpixel(sprite, player.getCentreX() + signedStep);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Obj_DEZConveyorBelt has no mappings or Draw_Sprite call.
    }

    @Override public int romObjectCodePointerHighWord() { return 4; }
    int halfWidthForTest() { return halfWidth; }
    int stepForTest() { return step; }
}
