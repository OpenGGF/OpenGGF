package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import java.util.List;

/** Native position/render words shared by the final DEZ encounter. */
abstract class DezFinalBossSprite extends DezEndBossSprite {
    /** Root SST $1C: reset by mouth closure and consumed by sub_81046. */
    protected int fireClock;

    DezFinalBossSprite(ObjectSpawn spawn, String name) { super(spawn, name); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) return;
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_FINAL_BOSS_MISC);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndexForcedPriority(frame, getX(), getY(), flipX, flipY, 1, highPriority);
    }
}
