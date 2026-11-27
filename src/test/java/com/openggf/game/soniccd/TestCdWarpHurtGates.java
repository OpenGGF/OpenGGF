package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.objects.boss.ttz.TtzBossMissile;
import com.openggf.game.soniccd.objects.mmz.MmzScarabBody;
import com.openggf.game.soniccd.player.CdPlayerStage;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.game.soniccd.timetravel.CdTimeWarp;
import com.openggf.game.soniccd.zones.wwz.WwzPlayerProfile;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ReevaluatingSolidObject;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCdWarpHurtGates {
    @TempDir
    Path tempDir;
    private EngineContext previous;
    private AbstractPlayableSprite sonic;
    private CdDiscDataSource disc;
    private CdTimeTravel timeTravel;
    private ObjectManager objects;
    private int frame;

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
        EngineServices.configure(previous);
        TestEnvironment.resetAll();
        if (disc != null) disc.close();
    }

    private void boot(CdStagePayload payload) throws Exception {
        disc = CdDiscDataSource.open(CdTestMedia.usaCue());
        sonic = CdFixtureReplay.boot(tempDir, disc, payload);
        timeTravel = CdTimeTravel.of(SessionManager.requireCurrentGameModule());
        objects = GameServices.level().getObjectManager();
        sonic.setRingCount(5);
    }

    private void startWarpStars() {
        timeTravel.touchTimePost(true);
        sonic.setGSpeed((short) 0x600);
        CdTimeWarp.run(timeTravel, sonic, false, null);
        sonic.setGSpeed((short) 0);
        assertTrue(timeTravel.starsActive());
        assertFalse(timeTravel.warping(), "the stars protect Sonic before the destination is committed");
    }

    private void stepObjects() {
        objects.update(GameServices.camera().getX(), sonic, List.of(), ++frame, false, true, false);
        objects.refreshPostCameraRenderState();
    }

    private void placeSonic(int x, int y) {
        NativePositionOps.writeXPosResetSubpixel(sonic, x);
        NativePositionOps.writeYPosResetSubpixel(sonic, y);
        sonic.setAir(true);
        sonic.setYSpeed((short) 0);
    }

    private ObjectInstance add(int id, int subtype, int x, int y) {
        var registry = SessionManager.requireCurrentGameModule().createObjectRegistry();
        ObjectInstance object = registry.create(new ObjectSpawn(x, y, id, subtype, 0, false, 0));
        objects.addDynamicObject(object);
        return object;
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "R11A, 0x26, 0",
            "R31B, 0x20, 0",
            "R31A, 0x30, 1",
            "R41A, 0x22, 0",
            "R51C, 0x26, 0",
            "R81A, 0x36, 255",
            "R82A, 0x36, 255",
            "R83C, 0x36, 255"
    })
    void standingOnSpikesChecksWarpStars(CdStagePayload payload, int id, int subtype) throws Exception {
        boot(payload);
        ObjectInstance spikes = add(id, subtype, sonic.getCentreX() + 80, sonic.getCentreY() - 48);
        for (int i = 0; i < 5; i++) stepObjects(); // Initialize, draw, and let the TTZ spikes reach their extended position.
        int landingY = spikes.getY() - sonic.getYRadius() - ((ReevaluatingSolidObject) spikes).solidYRadius() + 1;
        startWarpStars();
        placeSonic(spikes.getX(), landingY);
        stepObjects();
        assertSafe();
        assertTrue(objects.isRidingObject(sonic, spikes), "Sonic actually lands on the spikes");

        timeTravel.stopTimeWarp();
        placeSonic(spikes.getX(), landingY);
        stepObjects();
        assertHurt();
    }

    @Test
    void sidewaysSpikesUseSharedTouchGate() throws Exception {
        boot(CdStagePayload.R51C);
        ObjectInstance spikes = add(0x26, 2, sonic.getCentreX() + 80, sonic.getCentreY() - 48);
        stepObjects();
        startWarpStars();
        placeSonic(spikes.getX(), spikes.getY());
        objects.runTouchResponsesForPlayer(sonic, ++frame);
        assertSafe();

        timeTravel.stopTimeWarp();
        objects.runTouchResponsesForPlayer(sonic, ++frame);
        assertHurt();
    }

    @Test
    void missileTouchStillCountsWhileWarpStarsPreventHurt() throws Exception {
        boot(CdStagePayload.R43C);
        GameServices.camera().setX((short) 0x9B0);
        GameServices.camera().setY((short) 0x4F0);
        placeSonic(0xAAF, 0x5AC);
        add(0x4C, 0, 0xAB0, 0x520);
        TtzBossMissile missile = null;
        for (int i = 0; i < 1400 && missile == null; i++) {
            stepObjects();
            missile = objects.getActiveObjects().stream().filter(TtzBossMissile.class::isInstance)
                    .map(TtzBossMissile.class::cast).filter(m -> m.getCollisionFlags() == 0xFE)
                    .findFirst().orElse(null);
        }
        assertNotNull(missile, "Eggman must fire a missile");
        int status = missile.getCollisionProperty();
        startWarpStars();
        placeSonic(missile.getX(), missile.getY());
        objects.runTouchResponsesForPlayer(sonic, ++frame);
        assertEquals(status + 1, missile.getCollisionProperty());
        assertSafe();

        timeTravel.stopTimeWarp();
        placeSonic(0xAAF, 0x5AC);
        objects.runTouchResponsesForPlayer(sonic, ++frame);
        placeSonic(missile.getX(), missile.getY());
        objects.runTouchResponsesForPlayer(sonic, ++frame);
        assertHurt();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void scarabEnemySideChecksWarpStars(boolean warping) throws Exception {
        boot(CdStagePayload.R81A);
        add(0x2F, 4, sonic.getCentreX() + 80, sonic.getCentreY());
        stepObjects();
        stepObjects();
        var beetle = objects.getActiveObjects().stream().filter(MmzScarabBody.class::isInstance)
                .map(MmzScarabBody.class::cast).findFirst().orElseThrow();
        if (warping) startWarpStars();
        placeSonic(beetle.getX() + 4, beetle.getY());
        objects.runTouchResponsesForPlayer(sonic, ++frame);
        if (warping) {
            assertSafe();
            assertTrue(beetle.isDestroyed() || beetle.getCollisionFlags() == 0,
                    "the enemy side takes a hit while Sonic is warping");
        } else {
            assertHurt();
        }
    }

    @Test
    void freezerCannotCaptureSonicWhileWarpStarsAreActive() throws Exception {
        boot(CdStagePayload.R61A);
        add(0x05, 0, sonic.getCentreX(), sonic.getCentreY() - 36);
        startWarpStars();
        assertFalse(runUntilFrozen(), "warp stars prevent capture by the freezer");
        assertSafe();

        timeTravel.stopTimeWarp();
        assertTrue(runUntilFrozen(), "the same freezer captures unprotected Sonic");
    }

    @Test
    void wwzBeamChecksWarpStars() throws Exception {
        boot(CdStagePayload.R61A);
        WwzPlayerProfile.setBeamMode(sonic, 1);
        placeSonic(0xC78, 0x1B8); // Act 1 Present, blue beam block $1AA.
        startWarpStars();
        CdPlayerStage.of(sonic).afterMovementMode(sonic);
        assertSafe();

        timeTravel.stopTimeWarp();
        CdPlayerStage.of(sonic).afterMovementMode(sonic);
        assertHurt();
    }

    @Test
    void wwzSparksCheckWarpStars() throws Exception {
        boot(CdStagePayload.R61A);
        placeSonic(0xF08, 0x168); // Act 1 Present, spark block $243.
        startWarpStars();
        CdPlayerStage.of(sonic).afterMovementMode(sonic);
        assertSafe();

        timeTravel.stopTimeWarp();
        CdPlayerStage.of(sonic).afterMovementMode(sonic);
        assertHurt();
    }

    @ParameterizedTest
    @CsvSource({"89, -1", "90, 0"})
    void wwzHangBarClearsWarp(int timer, int directionAfterGrab) throws Exception {
        boot(CdStagePayload.R61A);
        placeSonic(0x1308, 0x170); // The bar probe reaches block $159 at ($1308, $158).
        startWarpStars();
        while (timeTravel.warpTimer() < timer) timeTravel.vIntTick(false);

        CdPlayerStage.of(sonic).afterMovementMode(sonic);

        assertAll(
                () -> assertEquals(0x2C, sonic.getAnimationId(), "Sonic grabs the bar"),
                () -> assertFalse(timeTravel.starsActive(), "grabbing the bar ends star protection"),
                () -> assertEquals(0, timeTravel.warpTimer(), "grabbing the bar resets the timer"),
                () -> assertEquals(directionAfterGrab, timeTravel.warpDirection(), "bar grab direction at timer " + timer));
    }

    private boolean runUntilFrozen() {
        for (int i = 0; i < 700; i++) {
            stepObjects();
            if ((WwzPlayerProfile.playerControl(sonic) & 0x41) == 0x41) return true;
        }
        return false;
    }

    private void assertSafe() {
        assertAll(
                () -> assertFalse(sonic.isHurt() || sonic.getDead(), "warp stars prevent hurt"),
                () -> assertEquals(5, sonic.getRingCount(), "warp stars preserve rings"));
    }

    private void assertHurt() {
        assertTrue(sonic.isHurt());
    }
}
