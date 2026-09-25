package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** SKL $4D, Obj_DEZTorpedoLauncher / loc_471D6..loc_47284 (sonic3k.asm:93011-93072). */
public final class S3kDezTorpedoLauncherObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private boolean initialized;
    private boolean renderedOnScreen;
    private int countdown;
    private int mappingFrame;
    private int recoilTimer;

    public S3kDezTorpedoLauncherObjectInstance(ObjectSpawn spawn) {
        super(spawn, "DEZTorpedoLauncher");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialized) {
            initialized = true;
            countdown = interval();
        }
        if (mappingFrame != 0) {
            recoil();
            return;
        }
        // loc_471D6 consumes the carried Render_Sprites bit, not camera
        // visibility freshly recomputed before this object's dispatch.
        if (!renderedOnScreen) return;
        countdown = (short) (countdown - 1);
        if (countdown >= 0) return;
        countdown = interval();
        var projectile = spawnAfterCurrentSibling(() -> new S3kDezTorpedoObjectInstance(
                new ObjectSpawn(getX(), getY(), spawn.objectId(), spawn.subtype(),
                        spawn.renderFlags(), false, spawn.rawYWord())));
        if (projectile != null && !projectile.isDestroyed()) services().playSfx(Sonic3kSfx.CHAIN_TENSION.id);
        // Allocation failure still reaches loc_47252 and closes the muzzle.
        mappingFrame = 8;
        recoilTimer = 0x1F;
        recoil(); // the firing dispatch falls through into the byte countdown
    }

    private int interval() { return (spawn.subtype() & 0xFF) << 2; }

    private void recoil() {
        recoilTimer = (byte) (recoilTimer - 1);
        if (recoilTimer < 0) {
            recoilTimer = 7;
            mappingFrame--;
        }
    }

    @Override public void refreshPostCameraRenderState() {
        if (initialized) renderedOnScreen = isWithinRenderSpriteBounds(8, 0x10);
    }
    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return isCoarseXOutOfRange(getX(), cameraX, coarseXCullRange());
    }
    @Override public int getOnScreenHalfWidth() { return 8; }
    @Override public int getOnScreenHalfHeight() { return 0x10; }
    @Override public int getPriorityBucket() { return 5; } // priority=$280, art bit 15 clear
    @Override public void appendRenderCommands(List<GLCommand> commands) {
        var renderer = getRenderer(Sonic3kObjectArtKeys.DEZ_TORPEDO_LAUNCHER);
        if (renderer != null && renderer.isReady()) renderer.drawFrameIndex(mappingFrame, getX(), getY(),
                (spawn.renderFlags() & 1) != 0, (spawn.renderFlags() & 2) != 0);
    }

    public int countdownForTest() { return countdown; }
    public int mappingFrameForTest() { return mappingFrame; }
}
