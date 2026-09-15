package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.*;
import java.util.List;

/** SKL $49, Obj_SOZSolidSprites ($41F44): two static full-solid terrain sprites. */
public final class SozSolidSpritesObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    public SozSolidSpritesObjectInstance(ObjectSpawn spawn) { super(spawn, "SOZSolidSprites"); }

    @Override public void update(int vIntRunCount, PlayableEntity player) { checkpointAll(); }
    @Override public SolidExecutionMode solidExecutionMode() { return SolidExecutionMode.MANUAL_CHECKPOINT; }
    @Override public SolidObjectParams getSolidParams() {
        return spawn.subtype() == 0 ? new SolidObjectParams(0x1B, 0x18, 0x19)
                : new SolidObjectParams(0x2B, 8, 9);
    }
    @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
    @Override public int romObjectCodePointerHighWord() { return 4; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public int getOnScreenHalfWidth() { return spawn.subtype() == 0 ? 0x10 : 0x20; }
    @Override public int getOnScreenHalfHeight() { return spawn.subtype() == 0 ? 0x18 : 8; }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(spawn.x(), cameraX, coarseXCullRange());
    }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.SOZ_SOLID_SPRITES);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(spawn.subtype() == 0 ? 0 : 1,
                spawn.x(), spawn.y(), (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
}
