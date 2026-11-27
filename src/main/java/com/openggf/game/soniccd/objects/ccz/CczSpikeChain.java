package com.openggf.game.soniccd.objects.ccz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.CameraRelativeDraw;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.TrigLookupTable;

import java.util.List;

/** ObjSpikeChain (Level/Collision Chaos/Objects/Spike Chain/Main.asm:36-65). */
@CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
public final class CczSpikeChain extends CczObject
        implements ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
    static final int ID = 0x2C;
    static final int TILE = 0x03BD;

    private int angle;
    private int[] ballSlots;

    public CczSpikeChain(ObjectSpawn spawn) {
        super(spawn, "Spike chain pivot");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && !initialize()) return;

        // ObjSpikeChain_Rotate (Level/Collision Chaos/Objects/Spike Chain/Main.asm:134-182).
        angle = (angle + (spawn.subtype() == 0 ? 1 : -1)) & 0xFF;
        int sin = TrigLookupTable.sinHex(angle);
        int cos = TrigLookupTable.cosHex(angle);
        for (int i = 0; i < ballSlots.length; i++) {
            if (slotOccupant(ballSlots[i]) instanceof Ball ball && ball.parentSlot == getSlotIndex()) {
                int radius = 16 * (i + 1);
                ball.targetX = x + ((cos * radius) >> 8);
                ball.targetY = y + ((sin * radius) >> 8);
            }
        }
    }

    private boolean initialize() {
        // ObjSpikeChain_Init (Level/Collision Chaos/Objects/Spike Chain/Main.asm:75-130).
        routine = 2;
        tileWord = TILE;
        priority = 1;
        width = yRadius = 8;
        // STAGE_TIME is fixed by the program, even while a warp changes the live time zone.
        ballSlots = new int[CdLevel.nativeTimeZone(cdLevel().payload().stage().period()) + 2];
        for (int i = 0; i < ballSlots.length; i++) {
            Ball ball = new Ball(new ObjectSpawn(x, y, ID, 0xFF, 0, false, 0));
            ball.parentSlot = getSlotIndex();
            if (!findNextObjSlot(ball)) {
                setDestroyed(true);
                return false;
            }
            ballSlots[i] = ball.getSlotIndex();
        }
        return true;
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }
    @Override protected boolean drawsBeforeDespawnCheck() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        drawSheet(CczObjectArt.SPIKE_CHAIN_PIVOT, 0);
    }

    /** ObjSpikeChain_Child (Level/Collision Chaos/Objects/Spike Chain/Main.asm:193-227). */
    @CameraRelativeDraw(CameraRelativeDraw.Use.ART_SELECTION)
    public static final class Ball extends CczObject
            implements TouchResponseProvider, ObjectVisualFacing.NonDirectional, SpawnRewindRecreatable {
        private int parentSlot = -1;
        private int targetX;
        private int targetY;

        public Ball(ObjectSpawn spawn) {
            super(spawn, "Spike chain ball");
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (routine == 0) {
                routine = 2;
                tileWord = TILE;
                priority = 1;
                width = yRadius = 8;
                colType = 0x8B;
            }
            x = targetX & 0xFFFF;
            y = targetY & 0xFFFF;
            if (!slotHoldsId(parentSlot, ID)) setDestroyed(true);
        }

        // The shared CD touch pass applies sub_20735C's warp-star and invincibility gates
        // (r3/player_object_collide.asm:212-227).
        @Override public int getCollisionFlags() { return colType; }
        @Override public int getCollisionProperty() { return 0; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawSheet(CczObjectArt.SPIKE_CHAIN_BALL, 0);
        }
    }
}
