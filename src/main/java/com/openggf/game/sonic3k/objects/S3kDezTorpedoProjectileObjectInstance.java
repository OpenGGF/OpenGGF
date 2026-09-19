package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** Projectile routine {@code loc_4728A} created after its DEZ torpedo launcher. */
public final class S3kDezTorpedoProjectileObjectInstance extends AbstractObjectInstance
        implements TouchResponseProvider, SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int PRIORITY_WORD = 0x300;
    private int xFixed;

    public S3kDezTorpedoProjectileObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTorpedo");
        xFixed = (spawn.x() & 0xFFFF) << 16;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        int velocity = (spawn.renderFlags() & 1) != 0 ? 0x400 : -0x400;
        xFixed += velocity << 8;
        updateDynamicSpawn((xFixed >> 16) & 0xFFFF, getY());
        if (!isOnScreen()) {
            setDestroyed(true);
        }
    }

    @Override public int getCollisionFlags() { return isDestroyed() ? 0 : 0x9B; }
    @Override public int getCollisionProperty() { return 0; }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(PRIORITY_WORD); }
    @Override public int romObjectCodePointerHighWord() { return 4; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TORPEDO_LAUNCHER);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(9, getX(), getY(), (spawn.renderFlags() & 1) != 0, false);
        }
    }
}
