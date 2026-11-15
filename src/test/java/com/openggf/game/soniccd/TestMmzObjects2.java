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
import com.openggf.game.soniccd.objects.mmz.MmzSpikeCrusher;
import com.openggf.game.soniccd.objects.mmz.MmzSpikeWall;
import com.openggf.game.soniccd.objects.mmz.MmzTwinWalker;
import com.openggf.game.soniccd.objects.mmz.MmzBigbom;
import com.openggf.game.soniccd.objects.mmz.MmzScarab;
import com.openggf.game.soniccd.objects.mmz.MmzScarabBody;
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
 * Second-pass focused behaviour (Bigbom, Scarab, twin walker, spike crusher) on stages loaded from the disc; each case cites the routine whose
 * values it checks (scddisasm {@code src/objects/r8}). Expected values come from the routines, never from running the
 * implementation. Native comparisons: {@link TestMmzObjectsAppearances} and {@link TestSonicCdFixtureFrontier}.
 */
class TestMmzObjects2 {
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

    private List<ObjectInstance> live(int objectId) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o -> !o.isDestroyed() && o.getSpawn().objectId() == objectId).toList();
    }

    @Test
    void spikeCrusherBuildsItsThreePartsAndTwoTips() throws Exception {
        boot(CdStagePayload.R83C);
        ObjectSpawn spawn = at(0x20, -0x80, 0x33, 0);
        MmzSpikeCrusher centre = add(new MmzSpikeCrusher(spawn));
        runner.stepIdleFrames(1);
        // Routine0 (objects/r8/trap_3.asm:68-131): style byte_20DB02[0] = 0 picks row 0 of byte_20DB3A/46/52: the centre part is
        // frame 1 at X + $CC (-$34), the left extension frame 3 at X, the right frame 2 at X + $34.
        assertEquals(1, centre.mapFrame());
        assertEquals(spawn.x() - 0x34, centre.getX());
        List<MmzSpikeCrusher> parts = live(0x33).stream().filter(o -> o instanceof MmzSpikeCrusher)
                .map(o -> (MmzSpikeCrusher) o).toList();
        assertEquals(3, parts.size());
        assertTrue(parts.stream().anyMatch(p -> p.mapFrame() == 3 && p.getX() == spawn.x()), "left extension");
        assertTrue(parts.stream().anyMatch(p -> p.mapFrame() == 2 && p.getX() == spawn.x() + 0x34), "right extension");
        // loc_20DA8A (:115-126): every part whose frame is not 3 adds a hurting tip.
        long tips = live(0x33).stream().filter(o -> !(o instanceof MmzSpikeCrusher)).count();
        assertEquals(2, tips);
    }

    @Test
    void spikeCrusherSinksHalfAPixelPerFrameOnceThePlayerIsBelowIt() throws Exception {
        boot(CdStagePayload.R83C);
        MmzSpikeCrusher centre = add(new MmzSpikeCrusher(at(0, -0x30, 0x33, 0)));
        runner.stepIdleFrames(1);                       // Routine0
        int y = centre.getY();
        runner.stepIdleFrames(1);                       // Routine2: the player is $30 below, inside the +-$30 window (:234-250)
        assertEquals(4, centre.routine());
        runner.stepIdleFrames(1);                       // Routine4: addi.l #$8000,oY while the probe is above the floor (:287-310)
        runner.stepIdleFrames(19);
        assertEquals(y + 10, centre.getY(), "twenty calls of half a pixel");
    }

    @Test
    void twinWalkerMakesSevenLinksAndTheRightPlatformSixtyFourPixelsAway() throws Exception {
        boot(CdStagePayload.R82A);
        ObjectSpawn spawn = at(0x30, -0x80, 0x26, 0);
        MmzTwinWalker walker = add(new MmzTwinWalker(spawn));
        runner.stepIdleFrames(1);
        // Routine0 (objects/r8/twin_walker.asm:22-58): eight SpawnObject calls, each $8 further right; the eighth is the right platform.
        assertEquals(9, live(0x26).size());
        for (int i = 0; i < 7; i++) {
            assertEquals(spawn.x() + 8 * (i + 1), walker.link(i).getX(), "link " + i);
        }
        assertEquals(spawn.x() + 0x40, walker.rightPlatform().getX());
        assertEquals(2, walker.phase());
        assertEquals(0x0C, walker.steps());
        assertEquals(0x8000, walker.angle());
    }

    @Test
    void twinWalkerSwingsTheRopeAroundThePivotOnceAPlayerStandsOnAPlatform() throws Exception {
        boot(CdStagePayload.R82A);
        // The left platform's top ($29 above its centre) level with Sonic's feet ($13 below his centre).
        ObjectSpawn spawn = at(0, 0x13 + 0x29, 0x26, 0);
        MmzTwinWalker walker = add(new MmzTwinWalker(spawn));
        MmzTwinWalker right = null;
        for (int frame = 0; frame < 40 && (right == null || right.platformRoutine() != 4); frame++) {
            runner.stepIdleFrames(1);
            right = walker.rightPlatform();
        }
        assertNotNull(right);
        assertEquals(4, right.platformRoutine(), "TwinWalkerObject_1_Routine2 then Routine4 on the pivot (objects/r8/twin_walker.asm:146-190)");
        assertEquals(0x0B, walker.steps(), "TwinWalkerObject_2_Routine0 spent one step (:206-219)");
        // First swing call: angle $8000 + $200, so the byte is $82. SineCosine($82) = (-$C, -$FF): each link is
        // (-$FF << 16) >> 5 = -$7F800 further along X (just under 8 pixels left) and -$6000 along Y.
        assertEquals(0x8200, walker.angle());
        assertEquals(right.getX() - 8, walker.link(0).getX());
        assertEquals(right.getY(), walker.link(0).getY(), "-$6000 on top of the pivot's half-pixel Y");
        assertEquals(right.getX() - 0x40, walker.getX(), "eight links of the rope reach the swinging platform");
    }

    @Test
    void bigbomBodyFallsThenPatrolsAndLightsItsFuseNearAPlayer() throws Exception {
        boot(CdStagePayload.R82A);
        sonic.setInvincibleFrames(4000);                 // loc_20696C: the hurt types skip an invincible player
        MmzBigbom controller = add(new MmzBigbom(at(0x08, -0x40, 0x23, 0)));
        runner.stepIdleFrames(1);
        // BigbomObject_1_Routine0 (objects/r8/bigbom.asm:48-65): the controller makes the body (subtype 2) and waits at Routine2.
        assertEquals(2, controller.routine());
        List<ObjectInstance> both = live(0x23);
        assertEquals(2, both.size());
        MmzBigbom body = (MmzBigbom) both.stream().filter(o -> o != controller).findFirst().orElseThrow();
        // Routine2 (:241-263): one pixel down per call until the floor, then (oSubtype2 0) Routine $C with collision $A6.
        // RoutineC (:338-344) starts the patrol and falls into RoutineE (:345-380): its first call finds oVar30 negative,
        // shows walk frame 0 and, with Sonic inside sub_20D8B8's window (dY in [-$A0, $50), dX in [-$60, $60)), lights the
        // fuse at once (loc_20D8DC falls into Routine10, :410-417): the body goes to Routine $12, the controller to Routine4.
        int frames = 0;
        while (body.routine() < 0x12 && frames++ < 400) runner.stepIdleFrames(1);
        assertEquals(0x12, body.routine());
        assertEquals(4, controller.routine());
        assertEquals(0xA6, body.getCollisionFlags(), "move.b #$A6,oColType (:255-259) with stage_layer 0");
        // Routine6 (:75-102): $1E calls later the body advances and the fuse exists (controller, body, fuse).
        runner.stepIdleFrames(0x1E);
        assertEquals(0x16, body.routine(), "Routine14 falls into Routine16 (:434-440)");
        assertEquals(3, live(0x23).size());
    }

    @Test
    void scarabSpawnerMakesTheBeetleItsShellAndTheMonitorOnItsBack() throws Exception {
        boot(CdStagePayload.R81B);
        ObjectSpawn spawn = at(0x50, 0, 0x2F, 0x80);
        MmzScarab spawner = add(new MmzScarab(spawn));
        runner.stepIdleFrames(2);
        // ScarabObject_6_Routine0/2 (objects/r8/scarab.asm:21-43): the player is within $140, so loc_20DD08 (:60-75) makes the beetle
        // (subtype 0), and loc_20DD3E (:76-97) the monitor (oSubtype2 0 is a kind 0-7) $18 left of it and 4 above; the
        // beetle's own Routine0 (:225-258) makes its shell.
        assertEquals(4, spawner.routine(), "loc_20DD08: addq.b #2,oRoutine");
        List<ObjectInstance> parts = live(0x2F);
        assertEquals(4, parts.size(), "spawner, beetle, shell and monitor");
        assertTrue(parts.stream().anyMatch(o -> o instanceof MmzScarabBody), "the beetle");
        assertTrue(parts.stream().anyMatch(o -> o.getX() == spawn.x() - 0x18), "the monitor at oX - $18");
    }

    @Test
    void scarabBeetleWalksSixtyOneCallsRestsAndWalksAgain() throws Exception {
        boot(CdStagePayload.R81B);
        MmzScarab spawner = add(new MmzScarab(at(0x50, 0, 0x2F, 0x80)));
        runner.stepIdleFrames(1);
        MmzScarabBody beetle = (MmzScarabBody) live(0x2F).stream().filter(o -> o instanceof MmzScarabBody)
                .findFirst().orElseThrow();
        // Routine0 (:225-271) sets oVar34 to $3C and falls into Routine2, whose first call walks and then counts it down
        // (ScarabObject_2_Routine0 -> loc_20E076, :356-392).
        assertEquals(0x3B, beetle.pauseTimer());
        runner.stepIdleFrames(60);
        assertEquals(-1, beetle.pauseTimer(), "the 61st call is the last that walks");
        runner.stepIdleFrames(29);
        assertEquals(60, beetle.pauseTimer(), "oVar34 reaches -30 on the 90th call and reloads 60 (:376-383)");
    }
}
