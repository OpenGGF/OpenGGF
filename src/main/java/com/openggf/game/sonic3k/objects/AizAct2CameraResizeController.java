package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/**
 * One of the camera-boundary workers created by {@code Change_Act2Sizes}.
 *
 * <p>The ROM replaces the AIZ miniboss with {@code Obj_EndSignControl}, then
 * allocates independent {@code Obj_IncLevEndXGradual} and
 * {@code Obj_IncLevEndYGradual} objects before deleting the former miniboss
 * slot (sonic3k.asm:180415-180419,180575-180609,178154-178169,178210-178225).
 * Keeping these workers separate is significant: ordinary level objects may
 * reuse the released boss slot while the boundary changes continue.
 */
final class AizAct2CameraResizeController extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    static final int MAX_X = 0;
    static final int MAX_Y = 1;

    // Non-final so generic rewind capture can restore the constructor variant.
    private int boundary;
    private int accumulator;
    private boolean firstUpdate = true;

    AizAct2CameraResizeController(int boundary) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "AIZAct2CameraResize");
        this.boundary = boundary;
        // Preserve the already-verified engine/ROM camera phase when moving
        // this work out of AizMinibossInstance. The newly allocated ROM
        // workers are eligible later in the same ExecuteObjects pass.
        this.accumulator = boundary == MAX_X ? 0x4000 : 0x8000;
    }

    AizAct2CameraResizeController(ObjectSpawn spawn) {
        this(MAX_X);
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (boundary == MAX_X) {
            updateMaxX();
        } else {
            updateMaxY(playerEntity);
        }
        firstUpdate = false;
    }

    private void updateMaxX() {
        var camera = services().camera();
        var level = services().currentLevel();
        int storedMax = level != null ? level.getMaxX() : (camera.getMaxX() & 0xFFFF);

        accumulator += 0x4000;
        int delta = accumulator >>> 16;
        int currentMax = camera.getMaxX() & 0xFFFF;
        if (currentMax > storedMax) {
            // A later camera owner has already widened farther than the stale
            // gradual worker's target; ROM deletes the worker at its bound.
            setDestroyed(true);
            return;
        }
        int nextMax = currentMax + delta;
        if (nextMax >= storedMax) {
            camera.setMaxX((short) storedMax);
            setDestroyed(true);
        } else {
            camera.setMaxX((short) nextMax);
        }
    }

    private void updateMaxY(PlayableEntity playerEntity) {
        var camera = services().camera();
        var level = services().currentLevel();
        int storedMax = level != null ? level.getMaxY() : (camera.getMaxYTarget() & 0xFFFF);

        accumulator += 0x8000;
        int delta = accumulator >>> 16;
        if (firstUpdate && playerEntity != null && !playerEntity.getAir()) {
            // DeformBgLayer moves the camera before Do_ResizeEvents. Preserve
            // the creation-frame carry that the old in-parent implementation
            // already verified against the trace.
            delta += 2;
        }
        int nextMax = (camera.getMaxY() & 0xFFFF) + delta;
        if (nextMax > storedMax) {
            camera.setMaxY((short) storedMax);
            setDestroyed(true);
        } else {
            camera.setMaxY((short) nextMax);
            camera.setMaxYTarget((short) storedMax);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible ROM control object.
    }
}
