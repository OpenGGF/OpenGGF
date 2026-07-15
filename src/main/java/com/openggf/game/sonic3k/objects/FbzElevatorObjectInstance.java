package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Locked-on S3KL {@code Obj_FBZElevator} ($E2), ROM $3CA1A-$3CB0C.
 *
 * <p>The placed SST is an invisible periodic allocator. Each successful
 * {@code AllocateObjectAfterCurrent} is a separate, independently rewound car
 * SST; cars deliberately keep no parent reference and can outlive the placed
 * controller.
 */
public final class FbzElevatorObjectInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable {
    private static final int SPAWN_RELOAD = 0x5F;

    /** ROM word {@code $30(a0)}; zeroed placement RAM makes the first predecrement spawn immediately. */
    private int spawnTimer;

    public FbzElevatorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "FBZElevator");
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        spawnTimer = (short) (spawnTimer - 1);
        if (spawnTimer < 0) {
            // loc_3CA20 resets before allocation, so slot exhaustion does not
            // retry on the next frame.
            spawnTimer = SPAWN_RELOAD;
            int yVelocity = (spawn.renderFlags() & 1) != 0 ? 1 : -1;
            // The ROM does not copy parent status into the car render_flags.
            // Direction is runtime state, while the dynamic spawn stays unflipped.
            spawnAfterCurrentSibling(() -> new Car(spawn.x(), spawn.y(),
                    (spawn.subtype() & 0xFF) << 3, yVelocity));
        }
    }

    public int spawnTimer() {
        return spawnTimer & 0xFFFF;
    }

    /** ROM x_pos/y_pos are object centres; ObjectInstance getX/getY remain compatibility aliases. */
    public int getCentreX() { return spawn.x(); }
    public int getCentreY() { return spawn.y(); }

    @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) {
        return coarseOutOfRange(getCentreX(), cameraX);
    }

    @Override public void appendRenderCommands(List<GLCommand> commands) { }

    static boolean coarseOutOfRange(int objectX, int cameraX) {
        int coarseBack = (cameraX - 0x80) & 0xFF80;
        return (((objectX & 0xFF80) - coarseBack) & 0xFFFF) > 0x280;
    }

    /** Dynamically allocated {@code loc_3CA92} car SST. */
    public static final class Car extends AbstractObjectInstance
            implements SlopedSolidProvider, SpawnRewindRecreatable {
        private static final byte[] SLOPE = {
                0x10,0x10,0x10,0x10,0x10,0x10,0x10,0x11,0x12,0x12,
                0x13,0x13,0x13,0x14,0x14,0x14,0x15,0x15,0x15,0x16,
                0x16,0x16,0x17,0x17,0x17,0x18,0x18,0x18,0x19,0x19,
                0x19,0x1A,0x1A,0x1A,0x1B,0x1B,0x1B,0x1C,0x1C,0x1C,
                0x1D,0x1D,0x1D,0x1E,0x1E,0x1E,0x1F,0x1F,0x1F,0x20,
                0x20,0x20,0x21,0x21,0x21,0x21,0x21,0x21,0x21,0x21
        };

        private int centreX;
        private int centreY;
        private int travelTimer;
        private int yVelocity;

        public Car(ObjectSpawn spawn) {
            super(spawn, "FBZElevatorCar");
            centreX = spawn.x();
            centreY = spawn.y();
            travelTimer = (spawn.subtype() & 0xFF) << 3;
            // Exact-class rewind constructor: live cars are built by the
            // explicit-state overload, and captured generic state restores
            // this field. Direction never comes from child render_flags.
            yVelocity = -1;
        }

        Car(int x, int y, int timer, int yVelocity) {
            this(new ObjectSpawn(x, y, Sonic3kObjectIds.FBZ_ELEVATOR,
                    0, 0, false, 0));
            this.travelTimer = timer;
            this.yVelocity = yVelocity;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            travelTimer = (short) (travelTimer - 1);
            if (travelTimer < 0) centreX = 0x7F00;
            // ROM add.w y_vel,y_pos is an integer pixel-word write, not MoveSprite 8.8 motion.
            centreY = (short) (centreY + yVelocity) & 0xFFFF;
            updateDynamicSpawn(centreX, centreY);
            // sub_1DD0E collision is resolved by the shared SlopedSolidProvider
            // phase after this post-move position is published. Its player query
            // naturally extends the native P1-then-P2 routine to extra sidekicks.
        }

        public int getCentreX() { return centreX & 0xFFFF; }
        public int getCentreY() { return centreY & 0xFFFF; }
        public int travelTimer() { return travelTimer; }
        public int yVelocity() { return yVelocity; }

        @Override public boolean requiresSameFrameUpdate() { return true; }
        @Override public boolean checksOutOfRangeAfterRoutine() { return true; }
        @Override public boolean usesCustomOutOfRangeCheck() { return true; }
        @Override public boolean isCustomOutOfRange(int cameraX) {
            return coarseOutOfRange(getCentreX(), cameraX);
        }
        @Override public int getOnScreenHalfWidth() { return 0x30; }
        @Override public int getOnScreenHalfHeight() { return 0x20; }
        @Override public int getPriorityBucket() { return 1; }
        @Override public SolidObjectParams getSolidParams() {
            return new SolidObjectParams(0x3B, 0x10, 0x10);
        }
        @Override public byte[] getSlopeData() { return SLOPE.clone(); }
        @Override public boolean isSlopeFlipped() { return false; }
        @Override public boolean addsSlopeCatchRangeToVerticalOverlap() { return true; }
        @Override public boolean usesInclusiveRightEdge() { return true; }
        @Override public boolean bypassesOffscreenSolidGate() { return true; }
        @Override public boolean clearsStandingBitOnContinuedRideExit(PlayableEntity player) { return true; }
        @Override public boolean usesInstanceSolidStateLatchKey() { return true; }
        @Override public boolean skipsCpuSidekickWhenRenderFlagOffScreen() { return false; }
        @Override public boolean dropOnFloor() { return yVelocity >= 0; }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(Sonic3kObjectArtKeys.FBZ_ELEVATOR);
            if (renderer != null && renderer.isReady()) {
                renderer.drawFrameIndex(0, getCentreX(), getCentreY(), false, false);
            }
        }
    }
}
