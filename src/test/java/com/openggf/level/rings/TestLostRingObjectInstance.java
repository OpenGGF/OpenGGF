package com.openggf.level.rings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestLostRingObjectInstance {

    @Test
    void spillAnimationDeceleratesLikeRom() {
        // ROM ChangeRingFrame: accum += counter each frame; frame = (accum >> 9) & 3;
        // counter decrements; counter starts at 0xFF.
        SpillAnimationState anim = new SpillAnimationState();
        anim.reset();                 // counter=0xFF, accum=0, frame=0
        assertEquals(0xFF, anim.counter());
        anim.tick();                  // accum = 0xFF; frame = (0xFF>>9)&3 = 0; counter=0xFE
        assertEquals(0, anim.frame());
        assertEquals(0xFE, anim.counter());
        // advance enough to roll bits 10:9
        for (int i = 0; i < 3; i++) anim.tick();
        // accum after 4 ticks = 0xFF+0xFE+0xFD+0xFC = 0x03FA; (0x03FA>>9)&3 = 1
        assertEquals(1, anim.frame());
    }

    @Test
    void floorCheckCadenceReadsFeatureSetMaskS1EveryFourFramesS2EveryEight() {
        // ROM: per-game floor-check cadence (relocated from RingManager.LostRingPool.updatePhysics,
        // RingManager.java:1242-1248). S1 probes every 4 frames (andi.b #3), S2/S3K every 8 (andi.b #7).
        // The object must consult PhysicsFeatureSet.ringFloorCheckMask(), not a hardcoded constant.
        ProbeRecordingRing s1Ring = new ProbeRecordingRing(0x100, 0x100, 0, 0x0400,
                /*mask*/com.openggf.game.PhysicsFeatureSet.RING_FLOOR_CHECK_MASK_S1,
                /*reverseGravity*/false);
        // phaseOffset 0: probe fires when (vbla & mask) == 0.
        s1Ring.setVblaForTest(4);   // 4 & 3 == 0 → S1 probes; would NOT probe under S2 mask (4 & 7 == 4)
        s1Ring.stepPhysicsForTest(0x18, true);
        assertEquals(1, s1Ring.floorProbeCount, "S1 (#3 mask) must probe the floor on frame 4");

        ProbeRecordingRing s2Ring = new ProbeRecordingRing(0x100, 0x100, 0, 0x0400,
                /*mask*/com.openggf.game.PhysicsFeatureSet.RING_FLOOR_CHECK_MASK_S2,
                /*reverseGravity*/false);
        s2Ring.setVblaForTest(4);   // 4 & 7 == 4 → S2 does NOT probe on frame 4
        s2Ring.stepPhysicsForTest(0x18, true);
        assertEquals(0, s2Ring.floorProbeCount, "S2 (#7 mask) must NOT probe the floor on frame 4");
        s2Ring.setVblaForTest(8);   // 8 & 7 == 0 → S2 probes
        s2Ring.stepPhysicsForTest(0x18, true);
        assertEquals(1, s2Ring.floorProbeCount, "S2 (#7 mask) must probe the floor on frame 8");
    }

    @Test
    void s3kReverseGravityProbesCeilingNotFloor() {
        // ROM: S3K Reverse_gravity_flag routes Obj37 to Obj_Bouncing_Ring_Reverse_Gravity,
        // which probes the CEILING (RingCheckFloorDist_ReverseGravity, upward) and only when
        // yVel <= 0 (rising). Relocated from RingManager.java:1271-1294.
        ProbeRecordingRing ring = new ProbeRecordingRing(0x100, 0x100, 0, /*yVel rising*/-0x0400,
                /*mask*/com.openggf.game.PhysicsFeatureSet.RING_FLOOR_CHECK_MASK_S2,
                /*reverseGravity*/true);
        ring.setVblaForTest(0);     // 0 & 7 == 0 → probe fires this frame
        ring.stepPhysicsForTest(0x18, true);
        assertEquals(1, ring.ceilingProbeCount, "S3K reverse gravity must probe the ceiling");
        assertEquals(0, ring.floorProbeCount, "S3K reverse gravity must NOT probe the floor");
    }

    /**
     * Captures which per-game probe path the object takes given an injected floor-check mask and
     * reverse-gravity flag — the feature-set-driven cadence decision is the unit under test, so the
     * actual level collision distance is stubbed to 0 (no terrain).
     */
    private static final class ProbeRecordingRing extends LostRingObjectInstance {
        private final int mask;
        private final boolean reverseGravity;
        private int vbla;
        int floorProbeCount;
        int ceilingProbeCount;

        private ProbeRecordingRing(int xPixel, int yPixel, int xVel, int yVel,
                                   int mask, boolean reverseGravity) {
            super(new ObjectSpawn(xPixel & 0xFFFF, yPixel & 0xFFFF, 0x37, 0, 0, false, 0));
            initFixedPointForTest(xPixel, yPixel, xVel, yVel, 0, 0xFF);
            this.mask = mask;
            this.reverseGravity = reverseGravity;
        }

        void setVblaForTest(int vbla) {
            this.vbla = vbla;
        }

        @Override
        protected int resolveFloorCheckMask() {
            return mask;
        }

        @Override
        protected boolean isReverseGravityActive() {
            return reverseGravity;
        }

        @Override
        protected int resolveVblaCounter() {
            return vbla;
        }

        @Override
        protected int ringCheckFloorDist(int x, int y) {
            floorProbeCount++;
            return 0;
        }

        @Override
        protected int ringCheckCeilingDist(int x, int y) {
            ceilingProbeCount++;
            return 0;
        }
    }

    @Test
    void ringBouncePhysicsMatchesLegacyPool() {
        // Fixed-point contract (identical to LostRing.reset, RingManager LostRing.java:24):
        //   xSubpixel = x << 8 (pixel coordinate stored in the high byte; low byte = sub-pixel).
        // forTest(x, y, ...) constructs with xSubpixel = x << 8, ySubpixel = y << 8.
        LostRingObjectInstance ring = LostRingObjectInstance.forTest(
                /*xPixel*/0x100, /*yPixel*/0x100, /*xVel*/0x0200, /*yVel*/-0x0400, /*phase*/0, /*lifetime*/0xFF);
        assertEquals(0x100 << 8, ring.getXSubpixelForTest());      // 0x10000 at start
        ring.stepPhysicsForTest(/*gravity*/0x18, /*floorCheck*/false);
        // ROM step (LostRingPool.updatePhysics, RingManager.java:1245-1247):
        //   xSubpixel += xVel;  ySubpixel += yVel;  yVel += gravity.
        assertEquals((0x100 << 8) + 0x0200, ring.getXSubpixelForTest()); // 0x10200
        assertEquals((0x100 << 8) + (-0x0400), ring.getYSubpixelForTest()); // 0x0FC00
        assertEquals(-0x0400 + 0x18, ring.getYVelForTest());
    }

    @BeforeEach
    void setUpEngine() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDownEngine() {
        GraphicsManager.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void spawnRegistersRingObjectsInSlotOrder() throws Exception {
        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null);
        setField(levelManager, "objectManager", objectManager);

        RingManager ringManager = buildRingManagerWithLevelManager(levelManager);
        setField(levelManager, "ringManager", ringManager);

        SpawnTestPlayableSprite player = new SpawnTestPlayableSprite((short) 0x100, (short) 0x100);

        ringManager.spawnLostRings(player, 4, 0);

        List<LostRingObjectInstance> rings =
                objectManager.activeObjectsOfType(LostRingObjectInstance.class);
        assertEquals(4, rings.size(), "spawnLostRings should register 4 LostRingObjectInstances");
        for (int i = 1; i < rings.size(); i++) {
            assertTrue(rings.get(i).getSlotIndex() > rings.get(i - 1).getSlotIndex(),
                    "lost-ring objects must occupy ascending slots");
        }
        assertEquals(SpillAnimationState.INITIAL_COUNTER, ringManager.getSpillAnimationState().counter(),
                "spawn should reset the shared spill-spin counter to 0xFF");
    }

    @Test
    void spawnStopsOnAllocationFailureAndCapsAt32() throws Exception {
        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null);
        setField(levelManager, "objectManager", objectManager);

        RingManager ringManager = buildRingManagerWithLevelManager(levelManager);
        setField(levelManager, "ringManager", ringManager);

        SpawnTestPlayableSprite player = new SpawnTestPlayableSprite((short) 0x100, (short) 0x100);

        // Pre-fill the dynamic slot pool so only 3 slots remain free.
        objectManager.reserveAllButNFreeSlots(3);
        ringManager.spawnLostRings(player, 10, 0);

        List<LostRingObjectInstance> rings =
                objectManager.activeObjectsOfType(LostRingObjectInstance.class);
        assertEquals(3, rings.size(),
                "spawn must stop on the first -1 allocation, leaving exactly the 3 allocatable rings");
        for (int i = 1; i < rings.size(); i++) {
            assertTrue(rings.get(i).getSlotIndex() > rings.get(i - 1).getSlotIndex(),
                    "successfully-allocated rings must occupy ascending slots");
        }
    }

    @Test
    void neverSpawnsMoreThanRomCapOf32() throws Exception {
        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null);
        setField(levelManager, "objectManager", objectManager);

        RingManager ringManager = buildRingManagerWithLevelManager(levelManager);
        setField(levelManager, "ringManager", ringManager);

        SpawnTestPlayableSprite player = new SpawnTestPlayableSprite((short) 0x100, (short) 0x100);

        // Leave 64 free slots; request 50 → capped at the ROM 0x20 (32) limit.
        objectManager.reserveAllButNFreeSlots(64);
        ringManager.spawnLostRings(player, 50, 0);

        assertEquals(0x20, objectManager.activeObjectsOfType(LostRingObjectInstance.class).size(),
                "spilled rings must never exceed the ROM cap of 0x20 (32)");
    }

    private RingManager buildRingManagerWithLevelManager(LevelManager levelManager) {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);

        RingFrame frame = new RingFrame(List.of(new RingFramePiece(0, 0, 1, 1, 0, false, false, 0)));
        List<RingFrame> frames = new ArrayList<>();
        frames.add(frame);
        frames.add(frame);
        frames.add(frame);

        Pattern[] patterns = new Pattern[16];
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = pattern;
        }

        RingSpriteSheet spriteSheet = new RingSpriteSheet(patterns, frames, 1, 1, 1, 2);
        RingManager ringManager = new RingManager(
                List.of(), spriteSheet, levelManager, null, GameServices.audio());
        ringManager.ensurePatternsCached(GraphicsManager.getInstance(), 0);
        return ringManager;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class NoOpObjectRegistry implements ObjectRegistry {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return null;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "noop";
        }
    }

    private static final class SpawnTestPlayableSprite extends AbstractPlayableSprite {
        private int ringCount;

        private SpawnTestPlayableSprite(short x, short y) {
            super("TEST", x, y);
            setWidth(16);
            setHeight(32);
            setCentreX(x);
            setCentreY(y);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 0;
            runHeight = 0;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void addRings(int delta) {
            ringCount += delta;
        }

        @Override
        public int getRingCount() {
            return ringCount;
        }

        @Override
        public void setRingCount(int ringCount) {
            this.ringCount = ringCount;
        }

        @Override
        public void draw() {
        }
    }
}
