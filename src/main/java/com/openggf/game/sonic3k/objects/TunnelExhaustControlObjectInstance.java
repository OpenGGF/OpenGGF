package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.SubpixelMotion;

import java.util.List;

/**
 * LBZ tunnel exhaust controller.
 *
 * <p>ROM reference: {@code Obj_TunnelExhaustControl}
 * ({@code sonic3k.asm:57461-57572}). The control object emits a small exhaust
 * sprite every four frames for 60 frames, then moves itself off-screen.
 */
final class TunnelExhaustControlObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int EMIT_PERIOD = 4;
    private static final int LIFETIME = 60;
    private static final int OFFSCREEN_X = 0x7FF0;
    private static final int PRIORITY_BUCKET = 3; // ROM priority $180.
    private static final int ON_SCREEN_HALF_SIZE = 0x10;

    private int subtype;
    private int xVel;
    private int yVel;
    private int emitTimer;
    private int lifetime = LIFETIME;

    TunnelExhaustControlObjectInstance(ObjectSpawn spawn, int subtype, int xVel, int yVel) {
        super(spawn, "TunnelExhaustControl");
        this.subtype = subtype & 0xFF;
        this.xVel = xVel;
        this.yVel = yVel;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (subtype != 0) {
            maybeEmitTimedExhaust(frameCounter);
            deleteIfNotInRange();
            return;
        }

        emitTimer--;
        if (emitTimer < 0) {
            emitTimer = EMIT_PERIOD - 1;
            spawnChild(() -> new TunnelExhaustParticleInstance(buildSpawnAt(getX(), getY()), xVel, yVel, false));
        }
        lifetime--;
        if (lifetime < 0) {
            updateDynamicSpawn(OFFSCREEN_X, getY());
        }
        deleteIfNotInRange();
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return ON_SCREEN_HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return ON_SCREEN_HALF_SIZE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Control object is not drawn by the ROM; it only emits exhaust children.
    }

    private void maybeEmitTimedExhaust(int frameCounter) {
        if (((frameCounter + 1) & 3) == 0) {
            spawnChild(() -> new TunnelExhaustParticleInstance(buildSpawnAt(getX(), getY()), 0, 0x400, true));
        }
    }

    private void deleteIfNotInRange() {
        if (!isOnScreen(0x80)) {
            setDestroyedByOffscreen();
        }
    }

    int subtypeForTesting() {
        return subtype;
    }
}

final class TunnelExhaustParticleInstance extends AbstractObjectInstance implements SpawnRewindRecreatable {
    private static final int PRIORITY_BUCKET = 7; // ROM priority $380.
    private static final int ON_SCREEN_HALF_SIZE = 0x10;
    private static final int TIMED_LIFETIME = 0x0C;
    private static final int FALL_GRAVITY = 0x38;

    private final SubpixelMotion.State motion;
    private final boolean timed;
    private int timer;
    private int mappingFrame = 1;
    private int renderFlags = 0x84;

    TunnelExhaustParticleInstance(ObjectSpawn spawn, int xVel, int yVel, boolean timed) {
        super(spawn, "TunnelExhaustParticle");
        this.motion = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, xVel, yVel);
        this.timed = timed;
        this.timer = timed ? TIMED_LIFETIME - 1 : 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if ((frameCounter & 1) == 0) {
            renderFlags ^= 1;
        }
        if (timed) {
            timer--;
            if (timer < 0) {
                updateDynamicSpawn(0x7FF0, getY());
                return;
            }
        }
        SubpixelMotion.moveSprite(motion, FALL_GRAVITY);
        updateDynamicSpawn(motion.x, motion.y);
        if (!isOnScreen(0x80)) {
            setDestroyedByOffscreen();
        }
    }

    @Override
    public int getPriorityBucket() {
        return PRIORITY_BUCKET;
    }

    @Override
    public int getOnScreenHalfWidth() {
        return ON_SCREEN_HALF_SIZE;
    }

    @Override
    public int getOnScreenHalfHeight() {
        return ON_SCREEN_HALF_SIZE;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Exhaust particle art is owned by Map_TunnelExhaust; the pipe plug task
        // only needs the control/timing handoff, so do not fake this with plug art.
    }
}
