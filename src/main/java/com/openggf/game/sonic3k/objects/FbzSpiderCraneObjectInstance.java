package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;

/** Locked-on {@code Obj_FBZSpiderCrane} ($E5), $3D0A2-$3D2F6. */
public final class FbzSpiderCraneObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private enum State { WAIT, DESCEND, CAPTURE, RETRACT, TRAVEL, INERT }

    private State state = State.WAIT;
    private int extension;
    private int x;
    private int y;
    private int xFixed;
    private int horizontalVelocityFixed;
    private boolean returning;
    private boolean companionAllocationAttempted;
    private FbzSpiderCraneCompanionObjectInstance companion;

    public FbzSpiderCraneObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZSpiderCrane");
        x = spawn.x();
        y = spawn.y();
        xFixed = x << 16;
    }

    @Override
    public void update(int frameCounter, PlayableEntity ignored) {
        PlayableEntity main = services().playerQuery().mainPlayerOrNull();
        switch (state) {
            case WAIT -> {
                if (main != null && !main.isDebugMode()
                        && unsignedWindow(main.getCentreX() - x, 0x10, 0x20)) state = State.DESCEND;
            }
            case DESCEND -> descendAndTryCapture(main);
            case CAPTURE -> captureAndAllocate(main);
            case RETRACT -> retract(main);
            case TRAVEL -> travel(main);
            case INERT -> { }
        }
        updateDynamicSpawn(x, y);
    }

    private void descendAndTryCapture(PlayableEntity main) {
        if (extension < 0x40) extension++;
        y = spawn.y() + extension;
        if (main == null || main.getAir()) return;
        int relativeX = main.getCentreX() - x;
        int relativeY = main.getCentreY() - y - 5;
        if (unsignedWindow(relativeX, 0x10, 0x20) && unsignedWindow(relativeY, 0, 0x14)) {
            if (main instanceof AbstractPlayableSprite sprite) {
                int nativeX = sprite.getCentreX();
                int nativeY = sprite.getCentreY();
                sprite.setAnimationId(0xE);
                ObjectControlState.nativeBit7FullControl().applyTo(sprite);
                sprite.setSpindash(false);
                sprite.setXSpeed((short) 0);
                sprite.setYSpeed((short) 0);
                sprite.setDirection(com.openggf.physics.Direction.RIGHT);
                sprite.setRolling(false);
                sprite.setAir(false);
                sprite.setOnObject(false);
                sprite.setPushing(false);
                sprite.restoreDefaultRadii();
                NativePositionOps.writeXPosPreserveSubpixel(sprite, nativeX);
                NativePositionOps.writeYPosPreserveSubpixel(sprite, nativeY);
            }
            state = State.CAPTURE;
        }
    }

    private void captureAndAllocate(PlayableEntity main) {
        pinMain(main);
        if (!companionAllocationAttempted) {
            companionAllocationAttempted = true;
            companion = spawnAfterCurrentSibling(() -> new FbzSpiderCraneCompanionObjectInstance(
                    buildSpawnAt(x, y), this));
            if (companion.isDestroyed()) companion = null;
        }
        state = State.RETRACT;
    }

    private void retract(PlayableEntity main) {
        if (extension > 0) extension--;
        y = spawn.y() + extension;
        alignCompanionAndPlayer(main);
        if (extension == 0) state = State.TRAVEL;
    }

    private void travel(PlayableEntity main) {
        if (!returning) {
            horizontalVelocityFixed += 0x1000;
            if (x - spawn.x() >= horizontalTravel()) returning = true;
        } else {
            horizontalVelocityFixed -= 0x1000;
            if (horizontalVelocityFixed == 0) {
                releaseMain(main);
                if (companion != null) companion.releaseToInertFrame();
                state = State.INERT;
                return;
            }
        }
        xFixed += horizontalVelocityFixed;
        x = xFixed >> 16;
        alignCompanionAndPlayer(main);
    }

    private void alignCompanionAndPlayer(PlayableEntity main) {
        if (companion != null) companion.follow(x, y);
        pinMain(main);
    }

    private void pinMain(PlayableEntity main) {
        if (main instanceof AbstractPlayableSprite sprite) {
            NativePositionOps.writeXPosPreserveSubpixel(sprite, x);
            NativePositionOps.writeYPosPreserveSubpixel(sprite, y + 0x10);
        }
    }

    private static void releaseMain(PlayableEntity main) {
        if (main instanceof AbstractPlayableSprite sprite) {
            sprite.setAnimationId(0);
            ObjectControlState.none().applyTo(sprite);
            sprite.setAir(true);
        }
    }

    private static boolean unsignedWindow(int value, int add, int size) {
        return Integer.compareUnsigned((value + add) & 0xFFFF, size) < 0;
    }

    public int horizontalTravel() { return (spawn.subtype() & 0xFF) << 2; }
    FbzSpiderCraneCompanionObjectInstance companionMember() { return companion; }
    String stateName() { return state.name(); }

    @Override
    protected void afterRewindRestoreSettled() {
        if (companion != null || services().objectManager() == null) return;
        for (ObjectInstance candidate : services().objectManager().getActiveObjects()) {
            if (candidate instanceof FbzSpiderCraneCompanionObjectInstance visual
                    && visual.ownerSlot() == getSlotIndex()) {
                companion = visual;
                return;
            }
        }
    }
    @Override public int getX() { return x; }
    @Override public int getY() { return y; }
    @Override public int getPriorityBucket() { return 1; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_SPIDER_CRANE);
        if (renderer != null && renderer.isReady()) {
            boolean carryingFrame = state == State.RETRACT || state == State.TRAVEL;
            renderer.drawFrameIndex(carryingFrame ? 9 : 0xA, x, y, false, false);
            if (state == State.DESCEND || state == State.CAPTURE || state == State.RETRACT) {
                renderer.drawFrameIndex((extension + 7) >>> 3, x, y, false, false);
            }
        }
    }
}
