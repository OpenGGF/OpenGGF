package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnRewindRecreatable;

import java.util.List;

/** Independent ROM {@code Change_Act2Sizes} fixed-point boundary worker. */
final class FbzAct2CameraResizeWorker extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    static final int MAX_X = 0;
    static final int MIN_Y = 1;
    static final int MAX_Y = 2;

    private int boundary;
    private int target;
    private int accumulator;

    FbzAct2CameraResizeWorker(int boundary) {
        this(boundary, defaultTarget(boundary));
    }

    FbzAct2CameraResizeWorker(int boundary, int target) {
        super(new ObjectSpawn(0, 0, 0, boundary, 0, false, 0), "FBZAct2CameraResize");
        this.boundary = boundary;
        this.target = target & 0xFFFF;
        setRomWorldPositioned(false);
    }

    FbzAct2CameraResizeWorker(ObjectSpawn spawn) {
        this(spawn.subtype());
    }

    @Override public int getX() { return 0; }
    @Override public int getY() { return 0; }
    @Override public boolean isPersistent() { return true; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var camera = services().camera();
        // The stored ROM destination is this worker's captured target. Max-X
        // and min-Y have no second native 2px easing owner: keep the engine's
        // generic easing targets at the word this worker actually published.
        // Max-Y retains Camera_target_max_Y_pos and its separate native easing.
        switch (boundary) {
            case MAX_X -> {
                accumulator += 0x4000;
                int next = ((camera.getMaxX() & 0xFFFF) + (short) (accumulator >>> 16)) & 0xFFFF;
                if (Integer.compareUnsigned(next, target) >= 0) {
                    camera.setMaxX((short) target);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                } else camera.setMaxX((short) next);
            }
            case MIN_Y -> {
                accumulator += 0x4000;
                int next = ((camera.getMinY() & 0xFFFF) - (short) (accumulator >>> 16)) & 0xFFFF;
                if ((short) next <= (short) target) {
                    camera.setMinY((short) target);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                } else camera.setMinY((short) next);
            }
            case MAX_Y -> {
                accumulator += 0x8000;
                int next = ((camera.getMaxY() & 0xFFFF) + (short) (accumulator >>> 16)) & 0xFFFF;
                if ((short) next > (short) target) {
                    camera.setMaxYCurrent((short) target);
                    ObjectLifetimeOps.deleteNoRespawn(this);
                } else {
                    camera.setMaxYCurrent((short) next);
                }
            }
            default -> throw new IllegalStateException("Unknown FBZ boundary worker " + boundary);
        }
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    private static int defaultTarget(int boundary) {
        return switch (boundary) {
            case MAX_X -> 0x6000;
            case MIN_Y -> 0;
            case MAX_Y -> 0x0B00;
            default -> throw new IllegalArgumentException("Unknown FBZ boundary worker " + boundary);
        };
    }
}
