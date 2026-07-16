package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Locked-on {@code Obj_FBZExitHall} ($8A), placed longword-offset subtypes $00/$04. */
public final class FbzExitHallInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private final boolean hallRecord;
    private boolean destroyedByOffscreen;

    public FbzExitHallInstance(ObjectSpawn spawn) {
        super(spawn, "FBZExitHall");
        if (spawn.subtype() != 0 && spawn.subtype() != 4) {
            throw new IllegalArgumentException("FBZ exit hall subtype: " + spawn.subtype());
        }
        hallRecord = spawn.subtype() == 4;
    }

    @Override public void update(int frameCounter, PlayableEntity player) {
        if (tryServices() != null && services().camera() != null
                && isCoarseXOutOfRange(spawn.x(), services().camera().getX(), 0x280)) {
            destroyedByOffscreen = true;
            ObjectLifetimeOps.destroyRespawnableOffscreen(this);
        }
    }

    @Override public int getX() { return spawn.x(); }
    @Override public int getY() { return spawn.y(); }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return hallRecord ? 0x18 : 0x20; }
    @Override public int getPriorityBucket() { return hallRecord ? 5 : 0; }
    int mappingFrame() { return hallRecord ? 1 : 0; }

    @Override public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(renderArtKey());
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame(), getX(), getY(), false, false);
    }
    private String renderArtKey() {
        return hallRecord
                ? Sonic3kObjectArtKeys.FBZ_EXIT_HALL
                : Sonic3kObjectArtKeys.FBZ_EXIT_HALL_DOOR_SCENERY;
    }
    String renderArtKeyForTest() { return renderArtKey(); }
    boolean wasDestroyedByOffscreen() { return destroyedByOffscreen; }
}
