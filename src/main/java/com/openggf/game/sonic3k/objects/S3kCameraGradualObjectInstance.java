package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.runtime.S3kCameraStoredBounds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;

import java.util.List;

/**
 * ROM {@code Obj_IncLevEndXGradual}, {@code Obj_DecLevStartXGradual},
 * {@code Obj_DecLevStartYGradual} and {@code Obj_IncLevEndYGradual} (sonic3k.asm:178159-178233)
 * as allocated by the Hidden Palace Knuckles cutscene and the Doomsday flight controller and end
 * boss. Each pass adds {@code $4000} ({@code $8000} for the Y end) to a 16.16 accumulator and moves
 * the boundary by its integer part until it reaches the matching {@code Camera_stored_*} word held
 * in the current zone's {@link S3kCameraStoredBounds} runtime state, then snaps and deletes itself.
 *
 * <p>The ROM has no boundary easing for the X limits or the Y start, so those writes set the
 * engine's current and target words together. {@code Camera_max_Y_pos} keeps the target the
 * cutscene wrote to {@code Camera_target_max_Y_pos}.
 */
public final class S3kCameraGradualObjectInstance extends AbstractObjectInstance
        implements RewindRecreatable {
    static final int INC_END_X = 0;
    static final int DEC_START_X = 1;
    static final int DEC_START_Y = 2;
    static final int INC_END_Y = 3;

    private int kind;
    private int accumulator;
    private boolean actTwoLevelSizes;

    public S3kCameraGradualObjectInstance(int kind) {
        super(new ObjectSpawn(0, 0, 0, kind, 0, false, 0), "S3kCameraGradual");
        this.kind = kind;
    }

    /**
     * Change_Act2Sizes copies the loaded act's LevelSizes into Camera_stored_*.
     * For this shared handoff the loaded level supplies those targets, including
     * zones without a cutscene-owned S3kCameraStoredBounds runtime. Cutscene
     * workers retain their mutable runtime source; only the handoff opts in.
     * The source selector and accumulator are both captured by generic rewind.
     */
    static S3kCameraGradualObjectInstance forActTwoLevelSizes(int kind) {
        var worker = new S3kCameraGradualObjectInstance(kind);
        worker.actTwoLevelSizes = true;
        return worker;
    }

    /**
     * Probe constructor for {@link com.openggf.level.objects.ObjectRewindDynamicCodecs}'s
     * {@code genericRecreate}: it must build an instance of a {@link RewindRecreatable} class
     * before it can call {@link #recreateForRewind}, and it only knows a fixed set of
     * signatures. Without one of them the whole child is silently dropped on restore, which is
     * how the Lava Reef crusher lost its four {@code Child7_ChangeLevSize} children. The kind
     * rides in the spawn's subtype, exactly as {@link #recreateForRewind} reads it back.
     */
    private S3kCameraGradualObjectInstance(ObjectSpawn spawn) {
        this(spawn.subtype());
    }

    @Override
    public S3kCameraGradualObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new S3kCameraGradualObjectInstance(ctx.spawn().subtype());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        var registry = services().zoneRuntimeRegistry();
        S3kCameraStoredBounds storedBounds = registry != null
                && registry.current() instanceof S3kCameraStoredBounds bounds ? bounds : null;
        var level = actTwoLevelSizes ? services().currentLevel() : null;
        if (actTwoLevelSizes ? level == null : storedBounds == null) {
            return;
        }
        var camera = services().camera();
        accumulator += kind == INC_END_Y ? 0x8000 : 0x4000;
        int step = (accumulator >>> 16) & 0xFFFF;
        switch (kind) {
            case INC_END_X -> {
                int stored = (actTwoLevelSizes ? level.getMaxX() : storedBounds.cameraStoredMaxX());
                int next = ((camera.getMaxX() & 0xFFFF) + step) & 0xFFFF;
                camera.claimCustomMaxXBoundaryEasing();
                // cmp.w (Camera_stored_max_X_pos).w,d0 / bhs.s
                if (next >= stored) {
                    camera.setMaxX((short) stored);
                    ObjectLifetimeOps.expireDynamic(this);
                } else {
                    camera.setMaxX((short) next);
                }
            }
            case DEC_START_X -> {
                short stored = (short) (actTwoLevelSizes ? level.getMinX() : storedBounds.cameraStoredMinX());
                short next = (short) (camera.getMinX() - step);
                // cmp.w (Camera_stored_min_X_pos).w,d0 / ble.s
                if (next <= stored) {
                    camera.setMinX(stored);
                    ObjectLifetimeOps.expireDynamic(this);
                } else {
                    camera.setMinX(next);
                }
            }
            case DEC_START_Y -> {
                short stored = (short) (actTwoLevelSizes ? level.getMinY() : storedBounds.cameraStoredMinY());
                short next = (short) (camera.getMinY() - step);
                if (next <= stored) {
                    camera.setMinY(stored);
                    ObjectLifetimeOps.expireDynamic(this);
                } else {
                    camera.setMinY(next);
                }
            }
            default -> {
                short stored = (short) (actTwoLevelSizes ? level.getMaxY() : storedBounds.cameraStoredMaxY());
                short next = (short) (camera.getMaxY() + step);
                // cmp.w (Camera_stored_max_Y_pos).w,d0 / bgt.s
                if (next > stored) {
                    camera.setMaxYCurrent(stored);
                    ObjectLifetimeOps.expireDynamic(this);
                } else {
                    camera.setMaxYCurrent(next);
                }
            }
        }
    }

    @Override public boolean isPersistent() { return true; }
    @Override public void appendRenderCommands(List<GLCommand> commands) { }
}
