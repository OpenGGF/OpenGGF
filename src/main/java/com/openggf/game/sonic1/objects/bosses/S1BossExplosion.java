package com.openggf.game.sonic1.objects.bosses;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic1.audio.Sonic1Sfx;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

/** Native Obj3F: fiery mappings, five eight-pass frames, no display during its init pass. */
public final class S1BossExplosion extends ExplosionObjectInstance {
    private boolean initialized;
    private boolean displayThisPass;

    S1BossExplosion(int x, int y, ObjectRenderManager renderer) {
        super(Sonic1ObjectIds.EXPLOSION, x, y, renderer, Sonic1Sfx.BOSS_EXPLOSION.id);
    }

    public S1BossExplosion(ObjectSpawn spawn, ObjectServices services) {
        super(spawn, services);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        displayThisPass = initialized;
        initialized = true;
        super.update(vIntRunCount, player);
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed(); // Expl_Main / ExItem_Animate never call out_of_range.
    }

    @Override
    public int getOnScreenHalfWidth() { return 12; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !displayThisPass || services().renderManager() == null) return;
        var renderer = services().renderManager().getBossExplosionRenderer();
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(currentMappingFrame(), getX(), getY(), false, false);
        }
    }
}
