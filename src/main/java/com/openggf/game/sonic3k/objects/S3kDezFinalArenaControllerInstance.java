package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.runtime.S3kDezZoneRuntimeState;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreateObjectLinks;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * {@code Obj_5A7C8} plus the fixed {@code Obj_5A8E6} wall and the two arena-block
 * emitters. The falling blocks remain separate dynamic objects, matching their
 * independent solid/motion/rewind lifetime in the ROM.
 */
public final class S3kDezFinalArenaControllerInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SpawnRewindRecreatable {
    private int x = 0x130;
    private int alignedCameraX = 0x80;
    private int emissionX = 0x2D0;
    private int emissionTimer;
    private int emissionCount;
    private boolean wallSpawned;

    public S3kDezFinalArenaControllerInstance(ObjectSpawn spawn) {
        super(spawn, "DEZ3ArenaFloor");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        S3kDezZoneRuntimeState state = state();
        if (!wallSpawned && state.act3BackgroundWord(0x00) == 0x6C0) {
            spawnFreeChild(() -> new ArenaWall(this));
            wallSpawned = true;
        }
        updateCameraLock(state);
        updateFloorWindow(state);
        updateShakeEmitter(state);
        emitRequestedBlock(state);
    }

    private void updateCameraLock(S3kDezZoneRuntimeState state) {
        int cameraX = services().camera().getX() & 0xFFFF;
        services().camera().setMinX(services().camera().getX());
        if (cameraX >= 0x520 && state.act3BackgroundWord(0x00) == 0x6C0)
            state.setAct3BackgroundWord(0x00, 0x2C0);
    }

    private void updateFloorWindow(S3kDezZoneRuntimeState state) {
        int aligned = (services().camera().getXCopy() & 0xFFFF) & 0xFFE0;
        if (aligned != alignedCameraX) {
            int delta = aligned > alignedCameraX ? -0x20 : 0x20;
            if (alignedCameraX >= state.act3BackgroundWord(0x16)) x += delta;
            alignedCameraX = aligned;
        }
    }

    private void updateShakeEmitter(S3kDezZoneRuntimeState state) {
        if (state.backgroundRoutine() < 4 || !services().gameState().isScreenShakeActive()) return;
        if (emissionCount == 0) {
            emissionCount = 0x13;
            emissionX = 0x2D0;
            state.setAct3BackgroundWord(0x16, 0x2C0);
        }
        if (emissionTimer-- > 0) return;
        emissionTimer = 0x0F;
        state.setAct3BackgroundWord(0x08, emissionX);
        emissionX += 0x20;
        if (--emissionCount == 0) services().gameState().setScreenShakeActive(false);
    }

    private void emitRequestedBlock(S3kDezZoneRuntimeState state) {
        int request = state.act3BackgroundWord(0x08);
        if (request == 0) return;
        state.setAct3BackgroundWord(0x08, 0);
        if (request < state.act3BackgroundWord(0x16)) return;
        FallingBlock block = spawnFreeChild(() -> new FallingBlock(this, request));
        if (block != null && request >= alignedCameraX - 0x20) state.setAct3BackgroundWord(0x06, request);
        state.setAct3BackgroundWord(0x16, state.act3BackgroundWord(0x16) + 0x20);
        if (request >= alignedCameraX) x += 0x20;
    }

    private S3kDezZoneRuntimeState state() {
        if (services().zoneRuntimeState() instanceof S3kDezZoneRuntimeState state) return state;
        throw new IllegalStateException("DEZ3 arena requires its runtime state");
    }

    @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0xB0, 0x10, 0x10); }
    @Override public int getX() { return x; }
    @Override public int getY() { return 0xF0; }
    @Override public boolean isPersistent() { return true; }
    @Override public int getPriorityBucket() { return 4; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    static final class ArenaWall extends AbstractObjectInstance
            implements SolidObjectProvider, RewindRecreatable {
        ArenaWall(S3kDezFinalArenaControllerInstance owner) {
            super(new ObjectSpawn(0x40, 0xF0, Sonic3kObjectIds.DEZ_END_BOSS,
                    1, 0, false, -1), "DEZ3ArenaWall");
        }

        @Override public ArenaWall recreateForRewind(RewindRecreateContext context) {
            return RewindRecreateObjectLinks.nearestObject(context,
                    S3kDezFinalArenaControllerInstance.class, true, 0x800)
                    .map(ArenaWall::new).orElse(null);
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            S3kDezZoneRuntimeState state = services().zoneRuntimeState() instanceof S3kDezZoneRuntimeState dez
                    ? dez : null;
            if (state == null || state.act3BackgroundWord(0x00) != 0x6C0) setDestroyed(true);
        }
        @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x40, 0x10, 0x10); }
        @Override public int getX() { return 0x40; }
        @Override public int getY() { return 0xF0; }
        @Override public boolean isPersistent() { return true; }
        @Override public void appendRenderCommands(List<GLCommand> commands) { }
    }

    static final class FallingBlock extends AbstractObjectInstance
            implements SolidObjectProvider, RewindRecreatable {
        private int x;
        private int y = 0xF0;
        private int yFixed = y << 16;
        private int yVelocity;

        FallingBlock(S3kDezFinalArenaControllerInstance owner, int x) {
            super(new ObjectSpawn(x, 0xF0, Sonic3kObjectIds.DEZ_END_BOSS,
                    2, 0, false, -1), "DEZ3FallingBlock");
            this.x = x;
        }

        @Override public FallingBlock recreateForRewind(RewindRecreateContext context) {
            return RewindRecreateObjectLinks.nearestObject(context,
                    S3kDezFinalArenaControllerInstance.class, true, 0x1000)
                    .map(parent -> new FallingBlock(parent, context.spawn().x())).orElse(null);
        }
        @Override public void update(int vIntRunCount, PlayableEntity player) {
            yFixed += yVelocity << 8;
            yVelocity = (short) (yVelocity + 0x1A);
            y = yFixed >> 16;
            if (y > (services().camera().getY() & 0xFFFF) + 0x300) setDestroyed(true);
        }
        @Override public SolidObjectParams getSolidParams() { return SolidObjectParams.of(0x10, 0x10, 0x10); }
        @Override public int getX() { return x; }
        @Override public int getY() { return y; }
        @Override public int getPriorityBucket() { return 4; }
        @Override public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.DEZ3_BLOCKS);
            if (renderer != null && renderer.isReady()) {
                int frame = (x & 0x60) >>> 5;
                renderer.drawFrameIndex(frame, x, y, false, false, 2);
            }
        }
    }
}
