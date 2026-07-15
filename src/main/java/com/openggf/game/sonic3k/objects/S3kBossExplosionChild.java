package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K boss explosion child (ROM: Obj_BossExplosion1/2).
 * The spawning controller plays sfx_Explode (0xB4); this child animates through
 * AniRaw_BossExplosion.
 *
 * ROM animation format: Animate_RawMultiDelay — (mapping frame, delay) pairs.
 * AniRaw_BossExplosion (sonic3k.asm:176871):
 *   dc.b 0,0, 0,1, 1,1, 2,2, 3,3, 4,4, 5,4, $F4
 * $F4 = end (calls Go_Delete_Sprite via $34 callback).
 */
public class S3kBossExplosionChild extends AbstractObjectInstance implements SpawnCoordinateRewindRecreatable {
    // The leading 0,0 pair is skipped by Animate_RawMultiDelay's initial +2.
    private static final int[] FRAMES = {0, 1, 2, 3, 4, 5};
    private static final int[] DELAYS = {1, 1, 2, 3, 4, 4};

    private int rawIndex;
    private int mappingFrame;
    private int animationFrameTimer;

    S3kBossExplosionChild() {
        this(0, 0);
    }

    public S3kBossExplosionChild(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "S3kBossExplosion");
        this.rawIndex = 0;
        this.mappingFrame = 0;
        this.animationFrameTimer = 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // SFX is played by the controller (sub_52850), not each child
        if (--animationFrameTimer >= 0) return;
        if (rawIndex >= FRAMES.length) {
            setDestroyed(true);
            return;
        }
        mappingFrame = FRAMES[rawIndex];
        animationFrameTimer = DELAYS[rawIndex];
        rawIndex++;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) return;
        ObjectRenderManager rm = services().renderManager();
        if (rm == null) return;
        // ROM: Obj_BossExplosion1/2 share Map_BossExplosion and AniRaw_BossExplosion.
        PatternSpriteRenderer renderer = rm.getBossExplosionRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(mappingFrame, spawn.x(), spawn.y(), false, false);
    }

    int mappingFrameForTest() { return mappingFrame; }

    @Override
    public boolean isHighPriority() {
        // ROM: Obj_BossExplosion uses make_art_tile(ArtTile_BossExplosion2,0,1) for AIZ
        // (and make_art_tile(ArtTile_BossExplosion,0,1) for other zones) — priority bit = 1.
        return true;
    }

    @Override
    public int getPriorityBucket() {
        // ROM: ObjDat_BossExplosion dc.w 0 → sprite_priority $0000 → bucket 0
        return 0;
    }
}
