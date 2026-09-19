package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** SKL {@code $5E}, {@code Obj_DEZHoverMachine} (sonic3k.asm:95740-95809). */
public final class S3kDezHoverMachineObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private int mappingFrame;
    private boolean childSpawned;

    public S3kDezHoverMachineObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZHoverMachine");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!childSpawned) {
            childSpawned = true;
            // AllocateObjectAfterCurrent: the oscillator executes later in the same object pass.
            spawnChild(() -> new S3kDezHoverMachineFieldObjectInstance(new ObjectSpawn(
                    (getX() + 0x20) & 0xFFFF, getY(), 0, 0, spawn.renderFlags(), false, 0)));
        }
        mappingFrame = (mappingFrame + 1) & 1;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_HOVER_MACHINE);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                    (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
        }
    }

    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(0x280); }
    @Override public int getOnScreenHalfWidth() { return 0x10; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    int mappingFrameForTest() { return mappingFrame; }
}
