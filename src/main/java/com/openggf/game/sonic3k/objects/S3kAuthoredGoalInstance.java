package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import java.util.List;

/**
 * Authored-level goal: layout identity owns the trigger; the native Obj_EndSign/results graph
 * owns presentation and completion. The game-owned key is independent of every numeric stock object ID.
 */
public final class S3kAuthoredGoalInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    public static final String OBJECT_KEY = "openggf:authored-goal";
    private boolean triggered;

    public S3kAuthoredGoalInstance(ObjectSpawn spawn) {
        super(spawn, "AuthoredGoal");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity entity) {
        if (triggered || !(entity instanceof AbstractPlayableSprite player)) return;
        int dx = (player.getCentreX() & 0xFFFF) - spawn.x();
        if (dx < 0 || dx >= 32 || Math.abs(player.getCentreY() - spawn.y()) > 64) return;
        S3kSignpostInstance sign = spawnFreeChild(() -> new S3kSignpostInstance(spawn.x(), 0, true));
        // Preserve the trigger if the native allocator is full; a later pass may retry.
        if (sign != null && !sign.isDestroyed() && sign.getSlotIndex() >= 0) triggered = true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (triggered || services().renderManager() == null) return;
        var renderer = services().renderManager().getRenderer(Sonic3kObjectArtKeys.END_SIGN);
        if (renderer != null && renderer.isReady())
            renderer.drawFrameIndex(0, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.fromS3kWord(0x280);
    }
}
