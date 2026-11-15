package com.openggf.game.soniccd.objects.mmz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.ReevaluatingSolidObject;

import java.util.List;

/**
 * HV platform, object $38 in the R81x index ({@code HVPlatformObject}, objects/r8/hv_platform.asm:3-89): a solid
 * platform that cycles through twelve shapes (frames 0-6 and back), each with its own timer, width and
 * height. In frame 0 (the flat shape) it reports a downward speed of $100 to riders; it never moves itself.
 * The routine is SolidObject, DrawObject, then CheckObjectDespawn2 against the initial X.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class MmzHvPlatform extends MmzObject implements ReevaluatingSolidObject, ObjectVisualFacing.NonDirectional {
    /**
     * byte_20C346 (objects/r8/hv_platform.asm:71-84): frame, frames to hold (+1 for the load frame), oWidth, oYRadius.
     */
    private static final int[][] SHAPES = {
            {0, 0x78, 0x08, 0x38}, {1, 0x04, 0x08, 0x28}, {2, 0x04, 0x08, 0x18}, {3, 0x20, 0x08, 0x08},
            {4, 0x04, 0x18, 0x08}, {5, 0x04, 0x28, 0x08}, {6, 0x78, 0x38, 0x08}, {5, 0x04, 0x28, 0x08},
            {4, 0x04, 0x18, 0x08}, {3, 0x20, 0x08, 0x08}, {2, 0x04, 0x18, 0x08}, {1, 0x04, 0x28, 0x08},
    };

    private boolean initialised;
    private int timer;          // obj.var_3a
    private int shape;          // obj.var_3c
    private int mapFrame;
    private int yVelocity;
    private final int initialX;  // obj.var_36

    public MmzHvPlatform(ObjectSpawn spawn) {
        super(spawn, "HVPlatform");
        this.initialX = spawn.x();
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (!initialised) {
            // HVPlatformObject_0_Routine0 (objects/r8/hv_platform.asm:27-35): priority 3, tile $41B, var_36 = X,
            // then it falls into Routine2 in the same call.
            initialised = true;
            priority = 3;
            tileWord = MmzObjectArt.HV_PLATFORM_TILE;
        }
        routine2();
        solidObject(Entry.SOLID);                    // sub_20C2C4 (:21-25)
    }

    /** HVPlatformObject_0_Routine2 (objects/r8/hv_platform.asm:36-70). */
    private void routine2() {
        if (timer == 0) {
            int[] row = SHAPES[shape];
            mapFrame = row[0];
            timer = row[1];
            width = row[2];
            yRadius = row[3];
        } else if (--timer == 0) {
            // loc_20C31E (:52-59): next shape, wrapping at $C.
            if (++shape >= SHAPES.length) shape = 0;
        }
        // loc_20C334 (:60-66): clr.w oYVel; frame 0 sets $100.
        yVelocity = mapFrame == 0 ? 0x100 : 0;
    }

    @Override public int solidYVelocity() { return yVelocity; }

    /** State for tests and tooling. */
    public int mapFrame() { return mapFrame; }
    public int shapeIndex() { return shape; }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected int despawnX() { return initialX; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(MmzObjectArt.HV_PLATFORM, mapFrame, hFlip(), vFlip());
    }
}
