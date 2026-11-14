package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.mmz.MmzHvPlatform;
import com.openggf.game.soniccd.objects.mmz.MmzBuzzsaw;
import com.openggf.game.soniccd.objects.mmz.MmzInvisibleBlock;
import com.openggf.game.soniccd.objects.mmz.MmzLaser;
import com.openggf.game.soniccd.objects.mmz.MmzSpikes;
import com.openggf.game.soniccd.objects.mmz.MmzTrapDoor;
import com.openggf.game.soniccd.objects.mmz.MmzMechaBu;
import com.openggf.game.soniccd.objects.mmz.MmzSpikeWall;
import com.openggf.game.soniccd.objects.mmz.MmzSpinDisc;
import com.openggf.game.soniccd.objects.mmz.MmzVanishPlatform;
import com.openggf.game.soniccd.zones.mmz.MmzStageSignals;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
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

    @Test
    void invisibleBlockIsSolidUnderThePlayer() throws Exception {
        boot(CdStagePayload.R82A);
        MmzInvisibleBlock block = add(new MmzInvisibleBlock(at(0, 0x13 + 0x10 - 0x10, 0x09, 0)));
        runner.stepIdleFrames(4);
        assertFalse(block.isDestroyed());
        assertTrue(sonic.isOnObject(), "SolidObject (objects/r8/invisible_block.asm:27-32) seats Sonic on the $10 box");
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
}
