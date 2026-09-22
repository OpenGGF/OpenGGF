package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;

import java.util.List;

/** Independent AllocateObjectAfterCurrent result, loc_4728A (sonic3k.asm:93073-93084). */
public final class S3kDezTorpedoObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, TouchResponseProvider {
    private int currentX;
    private boolean renderedOnScreen;
    private boolean published;

    public S3kDezTorpedoObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTorpedo");
        currentX = spawn.x();
        // The launcher copies its already-visible render_flags byte. The child
        // executes later in the same slot walk, before its own first render.
        renderedOnScreen = true; // this entry is reachable only from a visible launcher
    }

    @Override public void update(int vIntRunCount, PlayableEntity player) {
        published = false;
        if (!renderedOnScreen) {
            setDestroyed(true);
            return;
        }
        // MoveSprite2 with x_vel=±$400 and y_vel=0; no fractional accumulation.
        currentX = (currentX + ((spawn.renderFlags() & 1) != 0 ? 4 : -4)) & 0xFFFF;
        updateDynamicSpawn(currentX, getY());
        published = true;
    }
    @Override public void refreshPostCameraRenderState() {
        if (published && !isDestroyed()) renderedOnScreen = isWithinRenderSpriteBounds(8, 8);
    }
    @Override public int getX() { return currentX; }
    // loc_4728A has no coarse range tail. Only its carried render flag retires it.
    @Override public boolean isPersistent() { return true; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 8; }
    @Override public int getPriorityBucket() { return 6; } // priority=$300, art bit 15 clear
    @Override public int getCollisionFlags() { return 0x9B; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public boolean requiresRenderFlagForTouch() { return false; }
    @Override public boolean publishesTouchResponseListEntryThisFrame() { return published && !isDestroyed(); }
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TORPEDO);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(9, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }
}
