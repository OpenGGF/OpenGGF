package com.openggf.tests;

import com.openggf.game.session.SessionManager;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.ShieldType;
import com.openggf.game.rewind.snapshot.RingSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingFrame;
import com.openggf.level.rings.RingFramePiece;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRingManager {
    @BeforeEach
    public void setUp() {
        EngineServices.configure(EngineContext.fromLegacySingletonsForBootstrap());
        TestEnvironment.activeGameplayMode();
    }

    @AfterEach
    public void tearDown() {
        SessionManager.clear();
    }

    @Test
    public void testRingCollectionAndSparkleLifecycle() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);
        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);

        ringManager.update(0, player, 0);

        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
        assertEquals(0, ringManager.getSparkleStartFrame(spawn));
        assertTrue(ringManager.isRenderable(spawn, 1));

        assertFalse(ringManager.isRenderable(spawn, 2));
        assertEquals(-1, ringManager.getSparkleStartFrame(spawn));
    }

    @Test
    public void testCollectedRingsPersistOffscreen() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);
        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);

        ringManager.update(0, player, 0);

        assertTrue(ringManager.isCollected(spawn));

        ringManager.update(10000, player, 1);
        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());

        ringManager.update(0, player, 2);
        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
    }

    @Test
    public void testS1RingCollectionUsesTouchWindowInsteadOfSpriteBounds() {
        RingSpawn spawn = new RingSpawn(0x0098, 0x0248);
        RingManager ringManager = buildRingManagerWithSpinPiece(List.of(spawn),
                new RingFramePiece(-16, -16, 4, 4, 0, false, false, 0));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 0x0087, (short) 0x025B);
        player.setRolling(false);

        ringManager.update(0, player, 0);

        assertFalse(ringManager.isCollected(spawn),
                "Ring should not collect at the MZ1 frame-71 trace position");
        assertEquals(0, player.getRingCount());

        player.setCentreX((short) 0x008B);
        player.setCentreY((short) 0x024F);

        ringManager.update(0, player, 1);

        assertTrue(ringManager.isCollected(spawn),
                "Ring should collect once the ROM touch window overlaps");
        assertEquals(1, player.getRingCount());
    }

    @Test
    public void testS3kNormalStageRingUsesSixPixelTouchHalfSize() {
        RingSpawn spawn = new RingSpawn(116, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);
        player.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);

        ringManager.collectStageRings(player, 0);

        assertFalse(ringManager.isCollected(spawn),
                "S3K Test_Ring_Collisions normal path uses d1=6/d6=$C, not the attracted-ring 8px object radius");
        assertEquals(0, player.getRingCount());

        player.setCentreX((short) 102);

        ringManager.collectStageRings(player, 1);

        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
    }

    @Test
    public void testDuplicatePlacedRingsAtSameCoordinateCollectIndependently() {
        RingSpawn first = new RingSpawn(100, 100);
        RingSpawn second = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(first, second));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);

        ringManager.collectStageRings(player, 0);

        assertTrue(ringManager.isCollected(first));
        assertTrue(ringManager.isCollected(second));
        assertEquals(2, player.getRingCount());
    }

    @Test
    public void testS3kStageRingSweepSkipsDuringHighPostHitInvulnerability() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);
        player.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        player.setInvulnerableFrames(90);

        ringManager.collectStageRings(player, 0);

        assertFalse(ringManager.isCollected(spawn),
                "S3K Test_Ring_Collisions returns while invulnerability_timer >= 90");
        assertEquals(0, player.getRingCount());

        player.setInvulnerableFrames(89);
        ringManager.collectStageRings(player, 1);

        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
    }

    @Test
    public void testCpuSidekickObjectControlledRecoveryCannotCollectStageRings() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);

        TestPlayableSprite tails = new TestPlayableSprite((short) 100, (short) 100);
        tails.setCpuControlled(true);
        tails.setObjectControlled(true);
        tails.setControlLocked(true);

        ringManager.collectStageRings(tails, 0);

        assertFalse(ringManager.isCollected(spawn),
                "CPU Tails recovery flight keeps object_control set and must not collect stage rings");
        assertEquals(0, tails.getRingCount());
    }

    @Test
    public void testNativeBit7ObjectControlSuppressesStageRingCollection() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);
        ObjectControlState.nativeBit7FullControl().applyTo(player);

        ringManager.collectStageRings(player, 0);

        assertFalse(ringManager.isCollected(spawn),
                "ROM skips TouchResponse/Test_Ring_Collisions while object_control bit 7 suppresses touch");
        assertEquals(0, player.getRingCount());

        ObjectControlState.none().applyTo(player);
        ringManager.collectStageRings(player, 1);

        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
    }

    @Test
    public void testNativeLowBitObjectControlAllowsStageRingCollection() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);
        ObjectControlState.nativeBits0To6CpuAllowedMovementActive().applyTo(player);

        ringManager.collectStageRings(player, 0);

        assertTrue(ringManager.isCollected(spawn),
                "ROM object_control bits 0-6 do not suppress Test_Ring_Collisions");
        assertEquals(1, player.getRingCount());
    }

    @Test
    public void testLateRingManagerUpdateCanSkipStageRingCollection() {
        RingSpawn spawn = new RingSpawn(100, 100);
        RingManager ringManager = buildRingManager(List.of(spawn));
        ringManager.reset(0);

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);

        ringManager.update(0, player, 0, false);

        assertFalse(ringManager.isCollected(spawn),
                "LevelManager's late post-object update must not run a second placed-ring sweep");
        assertEquals(0, player.getRingCount());

        ringManager.update(0, player, 1, true);

        assertTrue(ringManager.isCollected(spawn));
        assertEquals(1, player.getRingCount());
    }

    // NOTE: legacy per-ring lost-ring lifecycle (collected-ring sparkle expiry and
    // on-expiry slot release) was retired with the legacy LostRingPool.updatePhysics
    // loop — per-ring physics/lifetime now runs in the object exec loop
    // (LostRingObjectInstance). Per-ring round-trip is covered by
    // com.openggf.level.rings.TestLostRingRewindCodec; the object-loop expiry/slot
    // release lands with the Stage-5 object physics relocation. updateLostRingPhysics
    // now only advances the shared decelerating spin (Ring_spill_anim_*).

    @Test
    public void testLostRingSpawnReservesDynamicSlots() throws Exception {
        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null);
        setField(levelManager, "objectManager", objectManager);

        RingManager ringManager = buildRingManagerWithLevelManager(List.of(), levelManager);
        setField(levelManager, "ringManager", ringManager);

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);

        ringManager.spawnLostRings(player, 3, 0);

        assertEquals(3, objectManager.getAllocatedSlotCount(),
                "Spilled lost rings should reserve real dynamic slots while active");
    }

    @Test
    public void testS3kLightningAttractedRingReservesSlotThroughSparkle() throws Exception {
        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null);
        setField(levelManager, "objectManager", objectManager);

        RingManager ringManager = buildRingManagerWithLevelManager(List.of(), levelManager);
        setField(levelManager, "ringManager", ringManager);
        RingSnapshot base = ringManager.capture();
        int reservedSlot = objectManager.allocateDynamicSlot();
        objectManager.releaseDynamicSlot(reservedSlot);
        ringManager.restore(new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                new RingSnapshot.AttractedRingEntry[] {
                        new RingSnapshot.AttractedRingEntry(
                                true, 0, 100, 100, 0, 0, 0, 0,
                                0, reservedSlot, false, -1)
                }));

        TestPlayableSprite player = new TestPlayableSprite((short) 100, (short) 100);
        player.usePhysicsFeatureSet(PhysicsFeatureSet.SONIC_3K);
        player.giveShield(ShieldType.LIGHTNING);

        ringManager.update(0, player, 0);

        assertEquals(1, player.getRingCount());
        assertEquals(1, objectManager.getAllocatedSlotCount(),
                "S3K Obj_Attracted_Ring keeps its SST slot for loc_1A920 sparkle after collection");

        ringManager.update(0, player, 1);
        assertEquals(1, objectManager.getAllocatedSlotCount(),
                "Attracted-ring sparkle should keep occupying the dynamic slot before Ani_RingSparkle finishes");

        ringManager.update(0, player, 2);
        assertEquals(0, objectManager.getAllocatedSlotCount(),
                "Attracted-ring sparkle should release its dynamic slot when the sparkle routine deletes");
    }

    @Test
    public void testAttractedRingRestoreRereservesObjectSlot() throws Exception {
        LevelManager levelManager = GameServices.level();
        ObjectManager objectManager = new ObjectManager(List.of(), new NoOpObjectRegistry(), 0, null, null);
        setField(levelManager, "objectManager", objectManager);

        RingManager ringManager = buildRingManagerWithLevelManager(List.of(), levelManager);
        setField(levelManager, "ringManager", ringManager);
        RingSnapshot base = ringManager.capture();

        int reservedSlot = objectManager.allocateDynamicSlot();
        objectManager.releaseDynamicSlot(reservedSlot);

        RingSnapshot snapshot = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                new RingSnapshot.AttractedRingEntry[] {
                        new RingSnapshot.AttractedRingEntry(
                                true, 0, 0x200, 0x180, 0, 0, 0, 0,
                                0, reservedSlot, false, -1)
                });

        ringManager.restore(snapshot);

        assertEquals(1, objectManager.getAllocatedSlotCount(),
                "Restoring an active Obj_Attracted_Ring snapshot must also restore its SST slot reservation");
        assertFalse(reservedSlot == objectManager.allocateDynamicSlot(),
                "The restored attracted-ring slot must not be reallocated to the next dynamic object");
    }

    @Test
    public void testS3kAttractedRingUsesTouchResponseBoundsForCollection() {
        RingManager ringManager = buildRingManager(List.of());
        RingSnapshot base = ringManager.capture();
        RingSnapshot snapshot = new RingSnapshot(
                base.collected(),
                base.sparkleTimers(),
                base.placementCursorIndex(),
                base.placementLastCameraX(),
                base.lostRingActiveCount(),
                base.spillAnimCounter(),
                base.spillAnimAccum(),
                base.spillAnimFrame(),
                base.lostRingFrameCounter(),
                base.lostRings(),
                new RingSnapshot.AttractedRingEntry[] {
                        new RingSnapshot.AttractedRingEntry(
                                true, 0, 0x0A4F, 0x0C5D, 0, 0, 0, 0,
                                0, -1, false, -1)
                });
        ringManager.restore(snapshot);

        TestPlayableSprite player = new TestPlayableSprite((short) 0x0A4A, (short) 0x0C4B);
        player.setRolling(true);

        ringManager.update(0, player, 2388);

        assertEquals(0, player.getRingCount(),
                "S3K collision_flags $47 uses TouchResponse bounds; this frame-2388 geometry is near but not overlapping");
        assertTrue(ringManager.capture().attractedRings()[0].active(),
                "The attracted ring should remain active until the ROM touch box overlaps");
    }

    private RingManager buildRingManager(List<RingSpawn> spawns) {
        return buildRingManagerWithSpinPiece(spawns, new RingFramePiece(0, 0, 1, 1, 0, false, false, 0));
    }

    private RingManager buildRingManagerWithLevelManager(List<RingSpawn> spawns, LevelManager levelManager) {
        return buildRingManagerWithLevelManagerAndSpinPiece(spawns, levelManager,
                new RingFramePiece(0, 0, 1, 1, 0, false, false, 0));
    }

    private RingManager buildRingManagerWithSpinPiece(List<RingSpawn> spawns, RingFramePiece piece) {
        return buildRingManagerWithLevelManagerAndSpinPiece(spawns, null, piece);
    }

    private RingManager buildRingManagerWithLevelManagerAndSpinPiece(List<RingSpawn> spawns,
                                                                     LevelManager levelManager,
                                                                     RingFramePiece piece) {
        Pattern pattern = new Pattern();
        pattern.setPixel(0, 0, (byte) 1);

        RingFrame frame = new RingFrame(List.of(piece));
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
                spawns,
                spriteSheet,
                levelManager,
                null,
                GameServices.audio());
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

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite(short x, short y) {
            super("TEST", x, y);
            setWidth(16);
            setHeight(32);
            setCentreX(x);
            setCentreY(y);
        }

        private void usePhysicsFeatureSet(PhysicsFeatureSet featureSet) {
            setPhysicsFeatureSet(featureSet);
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

        private int ringCount = 0;

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


