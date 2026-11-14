package com.openggf.game.soniccd.objects.shared;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdObject;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.physics.TrigLookupTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Wacky Workbench's spike chain ({@code SpikeChainObject}, objects/r6/spike_chain.asm:3-213): a parent
 * that turns by $100 (negative for a nonzero subtype) of its 16-bit angle a frame and sets four, five
 * (present) or six (future) hurt spike balls ({@link Ball}, subtype $FF, collision type $B9) on a line
 * through its centre, 16 pixels apart, by writing their 16.16 positions every frame. The ball count is
 * the stage's {@code STAGE_TIME} (:73-92). Collision Chaos's spike chain is another routine
 * (Level/Collision Chaos/Objects/Spike Chain/Main.asm) under the same name.
 */
public final class ObjSpikeChain extends CdObject implements ObjectVisualFacing.NonDirectional {
    private final List<Ball> balls = new ArrayList<>();
    private boolean initialised;
    private int routine;
    /** obj.var_2a (angle, 8.8) and obj.var_2c (its step) (:35-38, 103-106). */
    private int angle;
    private int step;

    public ObjSpikeChain(ObjectSpawn spawn) {
        super(spawn, "Spike chain");
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0 && !init()) return;
        turn();
    }

    /** SpikeChainObject_0_Routine0 (:27-93); it falls into Routine2 on the same frame. */
    private boolean init() {
        var level = cdLevel();
        if (level == null) return false;
        var payload = level.payload();
        var objects = services().objectManager();
        if (objects == null) return false;
        routine = 2;
        priority = 1;
        width = 8;
        yRadius = 8;
        tileWord = SharedObjectCatalogue.offset(payload, "spikeChainTile");
        step = getSpawn().subtype() == 0 ? 0x100 : -0x100;
        int count = 4 + (timeZone() != 0 ? 1 : 0) + (timeZone() == 2 ? 1 : 0);   // STAGE_TIME<>0, STAGE_TIME=2 (:73, 83)
        for (int i = 0; i < count; i++) {
            Ball ball = new Ball(this, x, y, getSpawn().objectId());
            balls.add(ball);
            objects.addDynamicObjectAfterCurrent(ball);                 // SpawnObjectAfter (:41, 49, 57, 65, 74, 84)
        }
        initialised = true;
        return true;
    }

    /** SpikeChainObject_0_Routine2 (:102-162): positions of the balls, 16 pixels apart along the angle. */
    private void turn() {
        angle = (angle + step) & 0xFFFF;
        int a = (angle >> 8) & 0xFF;
        // SineCosine: d0 = sin, d1 = cos, each swapped into the high word (:107-111).
        int sin = TrigLookupTable.sinHex(a) << 16;
        int cos = TrigLookupTable.cosHex(a) << 16;
        int d4 = sin >> 2, d5 = cos >> 2;
        int d2 = sin >> 3, d3 = cos >> 3;
        int d0 = sin >> 4, d1 = cos >> 4;
        int px = x << 16, py = y << 16;
        // A ball's y takes the sine offsets, its x the cosine offsets (:123-124).
        int[][] offsets = {{d0, d1}, {d2, d3}, {d0 + d2, d1 + d3}, {d4, d5}, {d0 + d4, d1 + d5}, {d2 + d4, d3 + d5}};
        for (int i = 0; i < balls.size(); i++) {
            balls.get(i).place(px + offsets[i][1], py + offsets[i][0]);
        }
    }

    @Override protected boolean despawnsByCheckObjDespawn() { return true; }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (initialised) drawFrame(SharedObjectArt.SPIKE_CHAIN, 0, hFlip(), vFlip());
    }

    public List<Ball> balls() { return balls; }

    /** SpikeChainChildObject (:172-207): follows the position its parent writes; gone when the parent is. */
    public static final class Ball extends CdObject implements TouchResponseProvider, ObjectVisualFacing.NonDirectional {
        /** move.b #$B9,obj.collide_type(a0): hurt, size $39 (:202). */
        private static final int COLLISION = 0xB9;

        private final ObjSpikeChain parent;
        /** obj.var_3e: the parent's slot ({@code move.w a0,obj.var_3e(a1)}, :43). */
        private final int parentSlot;
        private int fixedX;
        private int fixedY;
        private boolean initialised;

        Ball(ObjSpikeChain parent, int x, int y, int id) {
            super(new ObjectSpawn(x, y, id, 0xFF, 0, false, 0), "Spike chain ball");
            this.parent = parent;
            this.parentSlot = parent.getSlotIndex();
            this.fixedX = x << 16;
            this.fixedY = y << 16;
        }

        void place(int newX, int newY) {
            fixedX = newX;
            fixedY = newY;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (!slotHoldsId(parentSlot, 0x2D)) {
                setDestroyed(true);                                      // cmpi.b #$2D,obj.id(a1); jmp DeleteObject (:176-179)
                return;
            }
            if (!initialised) {                                          // SpikeChainObject_1_Routine0 (:196-202)
                if (artProvider() == null) return;
                priority = 1;
                width = 8;
                yRadius = 8;
                tileWord = parent.tileWord;
                initialised = true;
            }
            x = fixedX >>> 16;                                           // move.w obj.var_2a(a0),obj.x(a0) (:205-206)
            y = fixedY >>> 16;
        }

        @Override public int getCollisionFlags() { return initialised ? COLLISION : 0; }
        @Override public int getCollisionProperty() { return 0; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (initialised) drawFrame(SharedObjectArt.SPIKE_CHAIN_BALL, 0, false, false);
        }
    }
}
