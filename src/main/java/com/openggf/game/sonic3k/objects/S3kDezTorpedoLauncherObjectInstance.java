package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/** SKL {@code $4D}, {@code Obj_DEZTorpedoLauncher} (sonic3k.asm:93052-93126). */
public final class S3kDezTorpedoLauncherObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, RomObjectCodePointerProvider {
    private static final int PRIORITY_WORD = 0x280;
    // Mutable so the generic rewind codec captures the ROM $30(a0) reload word explicitly.
    private int reload;
    private int timer;
    private int mappingFrame;
    private int closeTimer;
    private boolean closing;

    public S3kDezTorpedoLauncherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTorpedoLauncher");
        reload = (spawn.subtype() & 0xFF) << 2;
        timer = reload;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (closing) {
            if (--closeTimer < 0) {
                closeTimer = 7;
                if (--mappingFrame == 0) {
                    closing = false;
                }
            }
            return;
        }
        // loc_471D6 reads the retained on-screen bit before decrementing the word timer.
        if (!isOnScreen() || --timer >= 0) {
            return;
        }
        timer = reload;
        spawnChild(() -> new S3kDezTorpedoProjectileObjectInstance(new ObjectSpawn(
                getX(), getY(), 0, 0, spawn.renderFlags(), false, 0)));
        if (tryServices() != null) {
            services().playSfx(Sonic3kSfx.CHAIN_TENSION.id);
        }
        mappingFrame = 8;
        closeTimer = 0x1F;
        closing = true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TORPEDO_LAUNCHER);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                    (spawn.renderFlags() & 1) != 0, false);
        }
    }

    @Override public int getPriorityBucket() { return RenderPriority.fromS3kWord(PRIORITY_WORD); }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int romObjectCodePointerHighWord() { return 4; }

    int timerForTest() { return timer; }
    int mappingFrameForTest() { return mappingFrame; }
    boolean closingForTest() { return closing; }
}
