package com.openggf.game.soniccd.objects.ssz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RewindRecreateContext;

import java.util.List;

/**
 * Stardust Speedway's non-holographic ground animal.
 * AnimalObject (objects/r7/animal.asm:3-11) selects the ground routines for nonzero low subtype bits.
 */
@CameraRelativeDraw(CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjSszGroundAnimal extends SszObject implements ObjectVisualFacing.Source, RewindRecreatable {
    private static final int HOP_Y_SPEED = -0x40000;

    private enum EndOfRoutine { NONE, RANGE_ONLY, DRAW_AND_RANGE }

    private int horizontalStep;
    private int hopVelocity;
    private EndOfRoutine endOfRoutine = EndOfRoutine.NONE;

    public ObjSszGroundAnimal(ObjectSpawn spawn) {
        super(spawn, "Animal");
    }

    @Override
    public ObjSszGroundAnimal recreateForRewind(RewindRecreateContext context) {
        return new ObjSszGroundAnimal(context.spawn());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        endOfRoutine = EndOfRoutine.NONE;
        if (!isReleased()) return;

        switch (routine) {
            case 0 -> {
                // AnimalObject_0_Routine0 (objects/r7/animal.asm:119-129).
                routine = 2;
                yRadius = 12;
                width = 8;
                priority = 4;
                spriteFlip = 0;
                tileWord = SszObjectArt.groundAnimalTile(artProvider());
                horizontalStep = 1;
                hopVelocity = HOP_Y_SPEED;
                initialised = true;
            }
            case 2, 4 -> {
                // AnimalObject_0_Routine2 (objects/r7/animal.asm:139-157).
                x = (x + horizontalStep) & 0xFFFF;
                int yPosition = ((y << 16) | ySub) + hopVelocity;
                y = yPosition >>> 16;
                ySub = yPosition & 0xFFFF;
                hopVelocity += 0x2000;
                mapFrame = hopVelocity < 0 ? 0 : 1;
                int floor = checkFloor(x, y, yRadius).distance();
                if (floor < 0) {
                    y = (y + floor) & 0xFFFF;
                    hopVelocity = HOP_Y_SPEED;
                    routine += 2;
                }
                endOfRoutine = EndOfRoutine.DRAW_AND_RANGE;
            }
            case 6 -> {
                // AnimalObject_0_Routine6 (objects/r7/animal.asm:161-165) turns without moving.
                routine = 2;
                horizontalStep = -horizontalStep;
                flipHorizontally();
                endOfRoutine = EndOfRoutine.DRAW_AND_RANGE;
            }
            default -> throw new IllegalStateException("Invalid SSZ ground animal routine " + routine);
        }
    }

    private boolean isReleased() {
        // CheckAnimalPrescence (common/time_check.asm:34-53).
        if (timeZone() >= 2) {
            if (flags().goodFuture) return true;
            ObjectLifetimeOps.deleteNoRespawn(this);
            return false;
        }
        var time = CdTimeTravel.of(services().gameModule());
        if (time != null && time.projectorDestroyed()) return true;
        endOfRoutine = EndOfRoutine.RANGE_ONLY;
        return false;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return endOfRoutine != EndOfRoutine.NONE; }
    // loc_20E928 (objects/r7/animal.asm:155-157) clears the on-screen bit in DrawObject before checking range.
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }
    @Override protected boolean calledDrawObjectThisFrame() { return endOfRoutine == EndOfRoutine.DRAW_AND_RANGE; }
    @Override protected String sheetKind() { return SszObjectArt.GROUND_ANIMAL; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (calledDrawObjectThisFrame() && renderFlagOnScreen() && !isDestroyed()) {
            super.appendRenderCommands(commands);
        }
    }

    @Override
    public ObjectVisualFacing.Basis nativeFacing() {
        return (spriteFlip & 1) != 0 ? ObjectVisualFacing.Basis.RIGHT : ObjectVisualFacing.Basis.LEFT;
    }
}
