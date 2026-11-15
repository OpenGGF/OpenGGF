package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/**
 * Circling spike balls, object $38 in R41C, R41D, R42C, R42D and R43 ({@code CircleSpikeBallObject},
 * objects/r4/circle_spike_ball.asm:3-137): a layout entry makes four spiked balls (tile $370, collision $A9,
 * $20 wide) that orbit the entry's position. The entry itself is the first ball (priority 1); it spawns three
 * more (priorities 4, 3 and 2 in spawn order) whose phases lead by {@code d, 2d, 3d} with {@code d = -$800}
 * (or {@code +$800} when the subtype is negative). The phase advances by {@code oVar30} ($100, negated for a
 * negative subtype) a frame; the position is the centre plus {@code (cos, sin) >> (subtype & $7F)}. The
 * entry deletes itself by CheckObjectDespawn; the other balls follow it and delete when it is gone.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjCircleSpikeBall extends TtzObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
    private final ObjCircleSpikeBall parent;
    /** The slot of {@code parent} the native child stores and tests each turn ({@code cmpi.b #$38,obj.id(a1)}). */
    private int parentSlot = -1;
    private int routine;
    private int centreX;
    private int centreY;
    private int phase;
    private int step;
    private int shift;
    private boolean initialised;

    public ObjCircleSpikeBall(ObjectSpawn spawn) {
        super(spawn, "CircleSpikeBall");
        this.parent = null;
    }

    private ObjCircleSpikeBall(ObjCircleSpikeBall parent, int phaseOffset, int priority) {
        super(childSpawn(parent.getX(), parent.getY(), 0x38, parent.spawn.subtype(), 0), "CircleSpikeBall");
        this.parent = parent;
        this.parentSlot = parent == null ? -1 : parent.getSlotIndex();
        this.phase = phaseOffset;
        this.priority = priority;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return parent == null; }
    @Override public int getCollisionFlags() { return initialised ? 0xA9 : 0; }
    @Override public int getCollisionProperty() { return 0; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (parent != null && !slotHoldsId(parentSlot, 0x38)) { setDestroyed(true); return; }   // cmpi.b #$38,obj.id(a1); bne
        if (routine == 0) {
            makeBalls();
            return;
        }
        phase = (phase + step) & 0xFFFF;                         // add.w d0,obj.var_2e(a0)
        int angle = (phase >> 8) & 0xFF;                         // asr.w #8,d0
        int cos = TrigLookupTable.cosHex(angle) >> shift;
        int sin = TrigLookupTable.sinHex(angle) >> shift;
        x = (centreX + cos) & 0xFFFF;
        y = (centreY + sin) & 0xFFFF;
    }

    /** CircleSpikeBallObject_0_Routine0 (objects/r4/circle_spike_ball.asm:27-115). */
    private void makeBalls() {
        boolean reverse = spawn.subtype() >= 0x80;
        int d0 = reverse ? 0x800 : -0x800;
        ObjCircleSpikeBall first = new ObjCircleSpikeBall(this, 3 * d0, 4);
        if (!spawnChild(first)) { setDestroyed(true); return; }
        ObjCircleSpikeBall second = new ObjCircleSpikeBall(this, 2 * d0, 3);
        if (!spawnChild(second)) { setDestroyed(true); return; }
        ObjCircleSpikeBall third = new ObjCircleSpikeBall(this, d0, 2);
        if (!spawnChild(third)) { setDestroyed(true); return; }
        int stepValue = reverse ? -0x100 : 0x100;
        int shiftValue = (reverse ? (-spawn.subtype()) & 0xFF : spawn.subtype()) & 0x7F;
        priority = 1;
        for (ObjCircleSpikeBall ball : List.of(this, third, second, first)) {
            ball.routine = 2;
            ball.initialised = true;
            ball.width = 0x10;
            ball.yRadius = 0x10;
            ball.tileWord = TtzObjectArt.CIRCLE_SPIKE_TILE;
            ball.centreX = x;
            ball.centreY = y;
            ball.step = stepValue;
            ball.shift = shiftValue;
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!initialised) return;
        drawSheet(TtzObjectArt.CIRCLE_SPIKE, 0, false, false);
    }
}
