package com.openggf.level.objects.boss;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateZeroScalarArgsRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Boss Explosion (Obj58).
 * Uses ArtNem_FieryExplosion with mappings from Obj58_MapUnc_2D50A.
 */
public class BossExplosionObjectInstance extends AbstractObjectInstance
        implements SpawnCoordinateZeroScalarArgsRewindRecreatable {
    private static final int FRAME_DELAY = 7;
    private static final int LAST_FRAME = 6;

    /**
     * S2 Obj58 {@code move.b #0,priority(a0)} (s2.asm:61320) and the S3K boss explosion
     * ObjDat word 0 draw front-most; Sonic 1 reuses Obj3F, whose
     * {@code move.b #1,obPriority(a0)} (_incObj/27, 3F Explosions.asm:78) is bucket 1.
     */
    public static final int S2_S3K_PRIORITY_BUCKET = RenderPriority.bucket(0);
    public static final int S1_PRIORITY_BUCKET = RenderPriority.bucket(1);

    private int sfxId;
    private int priorityBucket;
    private int mappingFrame;
    private int frameTimer;
    private boolean initialized;

    public BossExplosionObjectInstance(int x, int y, int sfxId) {
        this(x, y, 0, sfxId);
    }

    public BossExplosionObjectInstance(int x, int y, int objectId, int sfxId) {
        this(x, y, objectId, sfxId, S2_S3K_PRIORITY_BUCKET);
    }

    public BossExplosionObjectInstance(int x, int y, int objectId, int sfxId, int priorityBucket) {
        super(new ObjectSpawn(x, y, objectId, 0, 0, false, 0), "Boss Explosion");
        this.sfxId = sfxId;
        this.priorityBucket = RenderPriority.bucket(priorityBucket);
        this.mappingFrame = 0;
        this.frameTimer = FRAME_DELAY;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            services().playSfx(sfxId);
            initialized = true;
            return;
        }
        frameTimer--;
        if (frameTimer >= 0) {
            return;
        }
        frameTimer = FRAME_DELAY;
        mappingFrame++;
        if (mappingFrame > LAST_FRAME) {
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }
        var renderManager = services().renderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getBossExplosionRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return priorityBucket;
    }
}
