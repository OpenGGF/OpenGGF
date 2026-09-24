package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** {@code loc_591D6}: SSZ2's eight-piece screen-space ending-island SAT mask. */
public final class SszEndingIslandMaskObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private int screenY = 0xF0;

    public SszEndingIslandMaskObjectInstance(ObjectSpawn spawn) {
        super(spawn, "SSZ ending island mask");
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public int getPriorityBucket() { return 3; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        SszZoneRuntimeState state = (SszZoneRuntimeState) services().zoneRuntimeState();
        if (state.foregroundRoutine() == 0x18) {
            ObjectLifetimeOps.deleteNoRespawn(this);
            return;
        }
        if (state.backgroundRoutine() != 0) {
            int backgroundY = state.backgroundCameraY();
            screenY = backgroundY == 0x80 ? 0xF0 : 0x2C0 - backgroundY;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        var graphics = services().graphicsManager();
        if (isDestroyed() || !graphics.isSpriteSatCollectionActive()) return;
        graphics.requestSpriteMask();
        int cameraX = services().camera().getX();
        int cameraY = services().camera().getY();
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                graphics.submitSpriteSatControlEntry(
                        cameraX + 0xA2 + column * 0x40,
                        cameraY + screenY - 0x40 + row * 0x40,
                        4, 4, 0x7F0);
            }
        }
    }

    int screenYForTest() { return screenY; }
}
