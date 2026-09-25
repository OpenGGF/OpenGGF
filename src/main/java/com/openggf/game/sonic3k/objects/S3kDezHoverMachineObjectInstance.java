package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** SKL $5E, Obj_DEZHoverMachine / loc_494DA (sonic3k.asm:95699-95733). */
public final class S3kDezHoverMachineObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private boolean initialized;
    private int mappingFrame;

    public S3kDezHoverMachineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZHoverMachine");
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            // AllocateObjectAfterCurrent creates an independent sibling, not a
            // child whose lifetime depends on this stationary housing.
            spawnAfterCurrentSibling(() -> new S3kDezHoverRotorObjectInstance(
                    new ObjectSpawn(getX(), getY(), spawn.objectId(), 0,
                            spawn.renderFlags(), false, spawn.rawYWord())));
        }
        // Init and allocation failure both fall through into this animation.
        mappingFrame = (mappingFrame + 1) & 1;
    }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
    }
    @Override public int getOnScreenHalfWidth() { return 16; }
    @Override public int getOnScreenHalfHeight() { return 16; }
    @Override public int getPriorityBucket() { return 5; } // priority=$280, art bit 15 clear
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_HOVER_MACHINE);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
    public int mappingFrameForTest() { return mappingFrame; }
}
