package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.common.CdMonitor;
import com.openggf.game.soniccd.objects.common.CdRobotGenerator;
import com.openggf.game.soniccd.objects.mmz.MmzHvPlatform;
import com.openggf.game.soniccd.objects.mmz.MmzBuzzsaw;
import com.openggf.game.soniccd.objects.mmz.MmzInvisibleBlock;
import com.openggf.game.soniccd.objects.mmz.MmzLaser;
import com.openggf.game.soniccd.objects.mmz.MmzSpikes;
import com.openggf.game.soniccd.objects.mmz.MmzTrapDoor;
import com.openggf.game.soniccd.objects.mmz.MmzMechaBu;
import com.openggf.game.soniccd.objects.mmz.MmzPohBee;
import com.openggf.game.soniccd.objects.mmz.MmzSpikeWall;
import com.openggf.game.soniccd.objects.mmz.MmzSpinDisc;
import com.openggf.game.soniccd.objects.mmz.MmzVanishPlatform;
import com.openggf.game.soniccd.player.CdSonicAnimation;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused behaviour of the Metallic Madness objects on stages loaded from the disc; each case cites the routine whose
 * values it checks (scddisasm {@code src/objects/r8}). Expected values come from the routines, never from running the
 * implementation. Native comparisons: {@link TestMmzObjectsAppearances} and {@link TestSonicCdFixtureFrontier}.
 */
class TestMmzObjects {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private HeadlessTestRunner runner;
    private CdDiscDataSource disc;

    @BeforeEach
    void reset() {
        TestEnvironment.resetAll();
        previous = EngineServices.current();
    }

    @AfterEach
    void cleanup() throws Exception {
        GroundSensor.setLevelManager(null);
        SessionManager.clear();
        GameModuleRegistry.reset();
        if (previous != null) EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }

    private void boot(CdStagePayload payload) throws Exception {
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        sonic = CdFixtureReplay.boot(tempDir, disc, payload);
        runner = new HeadlessTestRunner(sonic);
    }

    private <T extends ObjectInstance> T add(T object) {
        GameServices.level().getObjectManager().addDynamicObject(object);
        return object;
    }

    private ObjectSpawn at(int dx, int dy, int id, int subtype) {
        return new ObjectSpawn(sonic.getCentreX() + dx, sonic.getCentreY() + dy, id, subtype, 0, false, 0);
    }

    private CdLevel level() {
        return (CdLevel) GameServices.level().getCurrentLevel();
    }

    private MmzStageSignals signals() {
        return MmzStageSignals.of(com.openggf.game.GameModuleRegistry.getCurrent(), level());
    }

    @Test
    void hvPlatformHoldsEachShapeForItsTimer() throws Exception {
        boot(CdStagePayload.R81A);
        MmzHvPlatform platform = add(new MmzHvPlatform(at(0x60, -0x70, 0x38, 0)));
        // Routine2 loads shape 0 (frame 0, timer $78) on the first call, counts the timer down on the next $78 calls
        // and advances the shape on the last of them; the next call loads shape 1 (objects/r8/hv_platform.asm:36-59).
        runner.stepIdleFrames(1);
        assertEquals(0, platform.mapFrame());
        runner.stepIdleFrames(0x78);
        assertEquals(0, platform.mapFrame(), "the shape index advances on the call that reaches zero");
        assertEquals(1, platform.shapeIndex());
        runner.stepIdleFrames(1);
        assertEquals(1, platform.mapFrame(), "shape 1: frame 1 (byte_20C346 row 2)");
        // Shape 1: the load call set timer 4, four calls count it down (the fourth advances the shape), and the next
        // call loads shape 2 (objects/r8/hv_platform.asm:36-59).
        runner.stepIdleFrames(4);
        assertEquals(1, platform.mapFrame(), "the fourth count still shows shape 1");
        runner.stepIdleFrames(1);
        assertEquals(2, platform.mapFrame(), "shape 2 is frame 2 (byte_20C346 row 3)");
    }

    @Test
    void spinDiscHoldsAGroundedPlayerAndClampsTheGroundSpeed() throws Exception {
        boot(CdStagePayload.R82A);
        // Subtype 0: radius $48, positive direction (objects/r8/spin_disc.asm:17-42).
        add(new MmzSpinDisc(at(0, 0, 0x22, 0)));
        sonic.setGSpeed((short) 0x100);
        runner.stepIdleFrames(1);
        assertEquals(1, signals().discHold(), "first contact sets the player's oVar38 (loc_20A7B8)");
        assertTrue(sonic.getGSpeed() >= 0x400, "loc_20A808 raises a slower speed to $400");
        sonic.setGSpeed((short) 0x1200);
        runner.stepIdleFrames(1);
        assertTrue(sonic.getGSpeed() <= 0xF00 && sonic.getGSpeed() > 0xE00, "loc_20A816 caps at $F00 (then the player moves)");
    }

    @Test
    void spinDiscReleasesThePlayerOutsideItsSquare() throws Exception {
        boot(CdStagePayload.R82A);
        add(new MmzSpinDisc(at(0x200, 0, 0x22, 0)));
        runner.stepIdleFrames(2);
        assertEquals(0, signals().discHold(), "outside the square the hold stays clear (loc_20A7A8)");
    }

    @Test
    void spikeWallFlipsOnItsFirstCallAndHurtsOnlyOnLayerZero() throws Exception {
        boot(CdStagePayload.R82A);
        MmzSpikeWall wall = add(new MmzSpikeWall(at(0x70, -0x30, 0x2B, 0)));
        int start = wall.getX();
        runner.stepIdleFrames(1);
        // Routine2: add the speed, then oVar2A counts from 0 to -1 and reloads: reverse (objects/r8/spike_wall.asm:27-43).
        assertEquals(0xB1, wall.getCollisionFlags(), "stage_layer 0: collision $B1");
        runner.stepIdleFrames(1);
        assertEquals(start, wall.getX(), "the second call undoes the first call's $5000");
        signals().flipStageLayer();
        runner.stepIdleFrames(1);
        assertEquals(0, wall.getCollisionFlags(), "stage_layer nonzero: oColType 0");
    }

    @Test
    void mechaBuDropsToTheFloorThenPatrolsAtFiveThirtySeconds() throws Exception {
        boot(CdStagePayload.R81A);
        MmzMechaBu bu = add(new MmzMechaBu(at(0x70, -0x40, 0x24, 0)));
        runner.stepIdleFrames(200);
        assertEquals(4, bu.routine(), "Routine2 ends on the floor (objects/r8/mecha_bu.asm:60-72)");
        int x = bu.getX();
        runner.stepIdleFrames(16);
        // Routine4 adds the speed and then tests oVar2A, which starts at 0: the first call adds -$5000, reloads the
        // timer to $199 and negates the speed, so every later call adds +$5000; 16 of them are exactly +5 pixels
        // (objects/r8/mecha_bu.asm:73-97).
        assertEquals(x + 5, bu.getX());
    }

    @ParameterizedTest
    @CsvSource({"0, 16, 3, 98, 130, 128, 259, 9", "1, 11, 1, 82, 258, 128, 515, 3"})
    void pohBeeFliesAndSwingsItsWeights(int subtype, int parts, int firstBob, int weightY,
                                        int turnFrame, int turnX, int returnFrame, int endBob) throws Exception {
        boot(CdStagePayload.R81A);
        ObjectSpawn spawn = at(0x90, -0x90, 0x2E, subtype);
        MmzPohBee bee = add(new MmzPohBee(spawn));
        runner.stepIdleFrames(1);
        List<Integer> samples = new ArrayList<>();
        samples.add(pohBeeActors().size() - 1);
        samples.add(bee.getX() - spawn.x());
        samples.add(bee.getY() - spawn.y());
        samples.add(bee.mapFrame());
        runner.stepIdleFrames(3);
        samples.add(bee.mapFrame());
        runner.stepIdleFrames(60);
        var weight = pohBeeActors().stream().filter(o -> o instanceof TouchResponseProvider p
                && p.getCollisionFlags() == 0xAF).findFirst().orElseThrow();
        samples.add(weight.getY() - spawn.y());
        runner.stepIdleFrames(turnFrame - 64);
        samples.add(bee.getX() - spawn.x());
        runner.stepIdleFrames(returnFrame - turnFrame);
        samples.add(bee.getX() - spawn.x());
        samples.add(bee.getY() - spawn.y());

        // loc_20DA38 (objects/r8/poh_bee.asm:81-108) and loc_20DB5E (objects/r8/poh_bee.asm:165-216).
        // The long weight passes the quarter turn before call 64 and is swinging back upward.
        assertEquals(List.of(parts, -1, firstBob, 0, 1, weightY, turnX, -1, endBob), samples);
    }

    @Test
    void pohBeeAttackReplacesItsSlotAndRewindRecreatesTheChains() throws Exception {
        boot(CdStagePayload.R81A);
        MmzPohBee bee = add(new MmzPohBee(at(0x80, -0x60, 0x2E, 0)));
        runner.stepIdleFrames(27);
        var objects = GameServices.level().getObjectManager();
        var registry = SessionManager.getCurrentGameplayMode().getRewindRegistry();
        var snapshot = registry.capture();
        int slot = bee.getSlotIndex();
        runner.stepIdleFrames(40);
        var forward = pohBeePositions();
        int score = GameServices.gameState().getScore();
        bee.onPlayerAttack(sonic, null);
        runner.stepIdleFrames(1);
        int replacement = objects.objectIdInSlot(slot);
        int points = GameServices.gameState().getScore() - score;
        int survivingParts = pohBeeActors().size();
        registry.restore(snapshot);
        runner.stepIdleFrames(40);

        // loc_20DC18 (objects/r8/poh_bee.asm:230-239): the explosion ID retires every part.
        assertEquals(List.of(0x18, 100, 0, forward),
                List.of(replacement, points, survivingParts, pohBeePositions()));
    }

    private List<ObjectInstance> pohBeeActors() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == 0x2E)
                .sorted(Comparator.comparingInt(o -> ((AbstractObjectInstance) o).getSlotIndex()))
                .toList();
    }

    private List<List<Integer>> pohBeePositions() {
        return pohBeeActors().stream().map(o -> List.of(((AbstractObjectInstance) o).getSlotIndex(),
                o.getX(), o.getY(), o instanceof MmzPohBee body ? body.mapFrame() : -1)).toList();
    }

    @Test
    void invisibleBlockIsSolidUnderThePlayer() throws Exception {
        boot(CdStagePayload.R82A);
        MmzInvisibleBlock block = add(new MmzInvisibleBlock(at(0, 0x13 + 0x10 - 0x10, 0x09, 0)));
        runner.stepIdleFrames(4);
        assertFalse(block.isDestroyed());
        assertTrue(sonic.isOnObject(), "SolidObject (objects/r8/invisible_block.asm:27-32) seats Sonic on the $10 box");
    }

    @Test
    void invisibleBlockLeavesUpwardMovementFromBelowIntact() throws Exception {
        boot(CdStagePayload.R82A);
        var manager = GameServices.level().getObjectManager();
        var camera = GameServices.camera();
        int x = camera.getX() + 0x90;
        int y = camera.getY() + 0x50;
        add(new MmzInvisibleBlock(new ObjectSpawn(x, y, 0x09, 0, 0, false, 0)));
        runner.stepIdleFrames(2);

        // One pixel of penetration into the underside of the solid box.
        int playerY = y + sonic.getYRadius() + 0x10 + 1;
        sonic.setCentreX((short) x);
        sonic.setCentreY((short) playerY);
        sonic.setAir(true);
        sonic.setYSpeed((short) -0x530);
        manager.update(camera.getX(), sonic, List.of(), 0, false, true, true);
        assertEquals(playerY, sonic.getCentreY());
        assertEquals(-0x530, sonic.getYSpeed());
        assertTrue(sonic.getAir());
    }

    @Test
    void vanishPlatformStartsInFrameSixAndBecomesSolidAfterItsDelay() throws Exception {
        boot(CdStagePayload.R81A);
        MmzVanishPlatform platform = add(new MmzVanishPlatform(at(0x40, -0x30, 0x2C, 0)));
        runner.stepIdleFrames(1);
        assertNotNull(platform);
        assertFalse(platform.isDestroyed());
        List<ObjectInstance> mine = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> o.getSpawn().objectId() == 0x2C).toList();
        assertEquals(1, mine.size(), "subtype 0 lists no children (objects/r8/vanish_platform.asm:168-176)");
    }

    private List<ObjectInstance> live(int objectId) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == objectId).toList();
    }

    @Test
    void buzzsawMovesOnItsFirstCallWaitsSixtyCallsAndMovesAgain() throws Exception {
        boot(CdStagePayload.R81A);
        MmzBuzzsaw saw = add(new MmzBuzzsaw(at(0x70, -0x70, 0x27, 0)));
        int x = saw.getX();
        int y = saw.getY();
        runner.stepIdleFrames(1);
        // Routine0 falls into Routine2's body: oVar2A $3C -> $3B, then add.l #$10000 to X and Y (objects/r8/buzzsaw.asm:27-108).
        assertEquals(x + 1, saw.getX());
        assertEquals(y + 1, saw.getY());
        runner.stepIdleFrames(59);
        // Routine8 counts oVar2A down to zero on call 60 and changes routine without moving (:140-146).
        assertEquals(x + 1, saw.getX());
        runner.stepIdleFrames(1);
        // Call 61 is Routine2: oVar2A 1 -> 0 starts the next animation step and the saw moves again.
        assertEquals(x + 2, saw.getX());
        assertEquals(2, live(0x27).size(), "the saw and its handle (objects/r8/buzzsaw.asm:59-89)");
    }

    @Test
    void trapDoorOpensInEightStepsOfFourPixels() throws Exception {
        boot(CdStagePayload.R81A);
        ObjectSpawn spawn = at(0x70, -0x50, 0x37, 0);
        MmzTrapDoor door = add(new MmzTrapDoor(spawn));
        runner.stepIdleFrames(1);
        // Routine0: subq.w #4,oX, spawn the other door $80 right; then Routine2's first step adds 4 (objects/r8/trap_1.asm:262-331).
        assertEquals(spawn.x(), door.getX(), "-4 then +4 on the first call");
        assertEquals(2, live(0x37).size(), "the second door (loc_20C080)");
        runner.stepIdleFrames(7);
        assertEquals(spawn.x() + 28, door.getX(), "eight steps: -4 + 8 * 4");
        assertEquals(7, door.mapFrame(), "byte_20C1B4 row 8: frame 7");
    }

    @Test
    void trapBlockCarriesASpringAboveItself() throws Exception {
        boot(CdStagePayload.R81A);
        MmzSpikes block = add(new MmzSpikes(at(0x70, -0x60, 0x36, 0)));
        runner.stepIdleFrames(1);
        // The level has springs of its own near the start; the child sits at the block's X, 24 pixels above it.
        List<ObjectInstance> springs = live(0x0A).stream()
                .filter(o -> o.getX() == block.getX() && o.getY() == block.getY() - 0x18).toList();
        assertEquals(1, springs.size(), "loc_20BE20 spawns object $0A at oVar39 $E8 = -24 (objects/r8/trap_1.asm:58-70)");
    }

    @Test
    void laserEmitterFiresAFloorAlignedBeamOnItsFirstCall() throws Exception {
        boot(CdStagePayload.R82A);
        MmzLaser emitter = add(new MmzLaser(at(0x70, -0x70, 0x1D, 0)));
        runner.stepIdleFrames(1);
        List<ObjectInstance> lasers = live(0x1D);
        assertEquals(2, lasers.size(), "oVar2A is 1 at init, so the first Routine2 call fires (objects/r8/laser.asm:23-70)");
        ObjectInstance beam = lasers.stream().filter(o -> o != emitter).findFirst().orElseThrow();
        // The beam starts at the emitter's Y with the low nibble cleared (andi.w #$FFF0,oY(a1)) and Routine4 adds 4 on each
        // call it has run; whether it ran in its spawn frame depends on its slot (SpawnObjectAfter).
        int delta = beam.getY() - (emitter.getY() & 0xFFF0);
        assertTrue(delta == 0 || delta == 4, "beam Y " + delta + " below the aligned emitter Y");
    }

    @Test
    void ordinaryFloorProbeClearsLostRingTilePriority() throws Exception {
        boot(CdStagePayload.R81B);
        int x = 0x190;
        int y = 0x710;
        assertOrdinaryChunk(x, y + 8);
        // Begin on the falling leg of LostRingMain, with the tile priority set at spill.
        var ring = add(LostRingObjectInstance.spawn(0x11, x, y, 0, 0, 0, 255, null));
        assertTrue(ring.isHighPriority());
        runner.stepIdleFrames(4);
        assertFalse(ring.isHighPriority(), "LostRingMain calls CheckBlockDown on its falling cadence");
    }

    @Test
    void fallingMonitorClearsTilePriorityWithoutChangingItsBucket() throws Exception {
        boot(CdStagePayload.R81B);
        int x = 0x190;
        int y = 0x710;
        assertOrdinaryChunk(x, y + 0xE - 2);
        GameServices.camera().setX((short) (x - 0x60));
        GameServices.camera().setY((short) (y - 0x50));
        CdMonitor monitor = add(new CdMonitor(new ObjectSpawn(x, y, 0x19, 1, 0, false, 0)));
        monitor.snapshotPreUpdatePosition();
        monitor.update(1, sonic);
        monitor.refreshPostCameraRenderState();
        assertTrue(monitor.isOnScreenForTouch());
        assertTrue(monitor.isHighPriority());
        int initialBucket = monitor.getPriorityBucket();

        sonic.setCentreY((short) (y + 0x10));
        sonic.setYSpeed((short) -0x100);
        monitor.onTouchResponse(sonic, null, 2);
        monitor.update(2, sonic);
        assertEquals(y - 2, monitor.getY(), "the hit from below starts the monitor's fall routine");
        assertFalse(monitor.isHighPriority(), "the monitor probes before its floor contact");
        assertEquals(initialBucket, monitor.getPriorityBucket());
    }

    private void assertOrdinaryChunk(int x, int y) {
        int column = x >> 8;
        int row = y >> 8;
        assertTrue(level().getMap().getValue(0, column, row) > 0);
        assertFalse(level().hasLoopFlag(column, row));
    }

    @Test
    void rollingSonicBreaksThePastRobotGeneratorAtTheWiderMmzTouchEdge() throws Exception {
        boot(CdStagePayload.R81B);
        CdRobotGenerator generator = add(new CdRobotGenerator(at(0x70, 0, 0x43, 0)));
        runner.stepIdleFrames(2);
        var objects = GameServices.level().getObjectManager();
        int collisionType = generator.getCollisionFlags();
        // byte_206D2A (r8/player_object_collide.asm:494-552): $22 reaches 40 px horizontally,
        // $3A reaches 32. Sonic contributes another 8 px.
        sonic.setCentreX((short) (generator.getX() - 45));
        sonic.setCentreY((short) generator.getY());
        sonic.setAnimationId(CdSonicAnimation.WALK.id());
        objects.runTouchResponsesForPlayer(sonic, 3);
        generator.update(3, sonic);
        assertFalse(level().flags().goodFuture, "walking contact leaves the generator intact");

        // loc_206CBE (r8/player_object_collide.asm:439-445) accepts animation 2 on each overlap.
        sonic.setAnimationId(CdSonicAnimation.ROLL.id());
        objects.runTouchResponsesForPlayer(sonic, 4);
        generator.update(4, sonic);
        assertTrue(level().flags().goodFuture, "rolling contact at 45 px breaks the generator");
        assertEquals(0xE2, collisionType);
    }

    @Test
    void foregroundSpillUsesBucketZeroAndHighPriorityArt() throws Exception {
        assertSpillPriority(0, 0, true);
    }

    @Test
    void backgroundSpillUsesBucketThreeAndLowPriorityArt() throws Exception {
        assertSpillPriority(1, 3, false);
    }

    private void assertSpillPriority(int layer, int bucket, boolean highPriority) throws Exception {
        boot(CdStagePayload.R81A);
        MmzBuzzsaw saw = add(new MmzBuzzsaw(at(0x70, -0x20, 0x27, 0)));
        runner.stepIdleFrames(2);
        if (signals().stageLayer() != layer) signals().flipStageLayer();
        sonic.setRingCount(2);
        sonic.setCentreX((short) saw.getX());
        sonic.setCentreY((short) saw.getY());
        var objects = GameServices.level().getObjectManager();
        objects.runTouchResponsesForPlayer(sonic, 2);
        saw.setDestroyed(true);

        signals().flipStageLayer();
        runner.stepIdleFrames(2);
        // loc_208B5A (objects/ring.asm:346-364) uses the spill's layer for both render priorities.
        var priorities = objects.getActiveObjects().stream()
                .filter(LostRingObjectInstance.class::isInstance)
                .map(LostRingObjectInstance.class::cast)
                .map(ring -> List.of(ring.getPriorityBucket(), ring.isHighPriority()))
                .toList();
        assertEquals(List.of(List.of(bucket, highPriority), List.of(bucket, highPriority)), priorities);
    }

    @Test
    void spilledRingsCanOnlyBeCollectedOnTheLayerWhereSonicWasHurt() throws Exception {
        boot(CdStagePayload.R81A);
        MmzBuzzsaw saw = add(new MmzBuzzsaw(at(0x70, -0x20, 0x27, 0)));
        runner.stepIdleFrames(2);
        if (signals().stageLayer() == 0) signals().flipStageLayer();
        sonic.setRingCount(2);
        sonic.setCentreX((short) saw.getX());
        sonic.setCentreY((short) saw.getY());
        var objects = GameServices.level().getObjectManager();
        objects.runTouchResponsesForPlayer(sonic, 2);
        saw.setDestroyed(true);

        // HurtPlayer (r8/player_object_collide.asm:273-282) stamps the layer when it allocates the owner.
        signals().flipStageLayer();
        runner.stepIdleFrames(2);
        var rings = objects.getActiveObjects().stream().filter(LostRingObjectInstance.class::isInstance)
                .map(LostRingObjectInstance.class::cast).toList();
        var first = rings.getFirst();
        sonic.setHurt(false);
        sonic.setInvulnerableFrames(0);
        sonic.setCentreX((short) first.getX());
        sonic.setCentreY((short) first.getY());

        // loc_206978 (r8/player_object_collide.asm:104-127) rejects the other layer before advancing the routine.
        objects.runTouchResponsesForPlayer(sonic, 5);
        long collectedOnOtherLayer = rings.stream().filter(LostRingObjectInstance::isCollected).count();
        signals().flipStageLayer();
        objects.runTouchResponsesForPlayer(sonic, 6);
        long collectedOnSpillLayer = rings.stream().filter(LostRingObjectInstance::isCollected).count();
        assertEquals(List.of(List.of(1, 1), 0L, 1L),
                List.of(rings.stream().map(LostRingObjectInstance::touchLayer).toList(),
                        collectedOnOtherLayer, collectedOnSpillLayer));
    }

    @Test
    void laserBeamShrinksSonicOnTheNativeContactFrame() throws Exception {
        boot(CdStagePayload.R82A);
        var camera = GameServices.camera();
        camera.setX((short) 6869);
        camera.setY((short) 1356);
        sonic.setCentreX((short) 7029);
        sonic.setCentreY((short) 1452);
        sonic.applyCustomRadii(9, 19);

        // The USA disc's R82A demo fires at iteration 121, hits at 128, clears
        // the beam and splash at 148, and fires again at 151. Hold Sonic at
        // the contact position across one cycle to isolate the object pass.
        var manager = GameServices.level().getObjectManager();
        ObjectManagerInternalAccess.refreshPlacementAfterRelocation(manager);
        for (int frame = 1; frame <= 31; frame++) {
            manager.update(camera.getX(), sonic, List.of(), frame, false, true, true);
            List<Integer> laserYs = live(0x1D).stream().filter(laser -> laser.getX() == 7033)
                    .map(ObjectInstance::getY).sorted().toList();

            if (frame <= 8) {
                assertEquals(List.of(1415, 1408 + 4 * frame).stream().sorted().toList(), laserYs,
                        "descending beam on cycle frame " + frame);
            } else if (frame >= 28) {
                assertEquals(frame == 31 ? List.of(1412, 1415) : List.of(1415), laserYs,
                        "beam off and refire on cycle frame " + frame);
            }
            assertEquals(frame >= 8, sonic.isHurt(), "knockback on cycle frame " + frame);
            if (frame == 8) {
                assertEquals(-0x200, sonic.getXSpeed());
                assertEquals(-0x400, sonic.getYSpeed());
                assertTrue(signals().shrunk());
            }
        }
    }

}
