package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.ccz.CczIntroState;
import com.openggf.game.soniccd.objects.common.CdBigRing;
import com.openggf.game.soniccd.objects.common.CdMonitor;
import com.openggf.game.soniccd.objects.common.CdTimeIcon;
import com.openggf.game.soniccd.program.CdSceneCoordinator;
import com.openggf.game.soniccd.timetravel.CdTimeTravel;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestRunner;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class TestCdTimeAttackObjects {
    @TempDir Path config;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void placedTimePosts(boolean timeAttack) throws Exception {
        run(CdStagePayload.R11A, 1, timeAttack, sonic -> {
            var post = level().getObjects().stream()
                    .filter(spawn -> spawn.objectId() == 0x19 && spawn.subtype() == 8)
                    .findFirst().orElseThrow();
            frameAt(sonic, post);
            assertEquals(!timeAttack, live(post), "placed past post at " + post);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void hudTimeIcon(boolean timeAttack) throws Exception {
        run(CdStagePayload.R11A, 1, timeAttack, sonic -> {
            new HeadlessTestRunner(sonic).stepIdleFrames(2);
            assertEquals(timeAttack ? -1 : 0x19, GameServices.level().getObjectManager().objectIdInSlot(CdTimeIcon.SLOT),
                    "reserved HUD time icon slot");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void collisionChaosIntroSpikes(boolean timeAttack) throws Exception {
        assertIntroPlacement(0x30, timeAttack);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void collisionChaosAmy(boolean timeAttack) throws Exception {
        assertIntroPlacement(0x32, timeAttack);
    }

    private void assertIntroPlacement(int id, boolean timeAttack) throws Exception {
        run(CdStagePayload.R31A, 4, timeAttack, sonic -> {
            var placement = level().getObjects().stream().filter(spawn -> spawn.objectId() == id)
                    .findFirst().orElseThrow();
            frameAt(sonic, placement);
            assertEquals(!timeAttack, live(placement), "intro placement " + placement);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void collisionChaosCaptureFlag(boolean timeAttack) throws Exception {
        run(CdStagePayload.R31A, 4, timeAttack, sonic -> {
            var amy = level().getObjects().stream().filter(spawn -> spawn.objectId() == 0x32)
                    .findFirst().orElseThrow();
            frameAt(sonic, amy);
            assertEquals(timeAttack, CczIntroState.of(level()).amyCaptured(), "Amy's exit sets amy_captured");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void extraLifeMonitor(boolean timeAttack) throws Exception {
        run(CdStagePayload.R11A, 1, timeAttack, sonic -> {
            var spawn = new ObjectSpawn(sonic.getCentreX() + 64, sonic.getCentreY(), 0x19, 0, 0, false, 0);
            var monitor = new CdMonitor(spawn);
            GameServices.level().getObjectManager().addDynamicObject(monitor);
            new HeadlessTestRunner(sonic).stepIdleFrames(2);
            assertEquals(!timeAttack, monitor.getCollisionFlags() != 0, "1-Up monitor can be touched");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void bigRingWithEnoughRings(boolean timeAttack) throws Exception {
        run(CdStagePayload.R11A, 1, timeAttack, sonic -> {
            var spawn = new ObjectSpawn(sonic.getCentreX() + 64, sonic.getCentreY(), 0x14, 0, 0, false, 0);
            GameServices.level().getObjectManager().addDynamicObject(new CdBigRing(spawn));
            GameServices.level().getLevelGamestate().setRings(50);
            new HeadlessTestRunner(sonic).stepIdleFrames(2);
            assertEquals(!timeAttack, live(spawn), "big ring after the 50-ring check");
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void palmtreePanicAmy(boolean timeAttack) throws Exception {
        run(CdStagePayload.R11A, 1, timeAttack, sonic -> {
            var amy = level().getObjects().stream().filter(spawn -> spawn.objectId() == 0x2F)
                    .findFirst().orElseThrow();
            var color = level().getPalette(1).getColor(0);
            int original = PaletteWriteSupport.segaWordFromColor(color);
            int sentinel = original ^ 0xEEE;
            color.fromSegaFormat(new byte[] {(byte) (sentinel >> 8), (byte) sentinel}, 0);
            frameAt(sonic, amy);
            var slot = GameServices.level().getObjectManager().getActiveObjects().stream()
                    .filter(object -> !object.isDestroyed() && amy.equals(object.getSpawn()))
                    .findFirst().orElseThrow();
            var commands = new ArrayList<GLCommand>();
            slot.appendRenderCommands(commands);
            assertAll(
                    () -> assertEquals(!timeAttack, !commands.isEmpty(), "Amy's slot draws"),
                    () -> assertEquals(timeAttack ? original : sentinel,
                            PaletteWriteSupport.segaWordFromColor(level().getPalette(1).getColor(0)),
                            "Amy restores line 1"));
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stonesForceGoodFutureOnlyInCampaign(boolean timeAttack) throws Exception {
        run(CdStagePayload.R11A, 1, timeAttack, sonic -> {
            var travel = CdTimeTravel.of(SessionManager.requireCurrentGameModule());
            travel.setTimeStones(CdTimeTravel.ALL_TIME_STONES);
            assertAll(
                    () -> {
                        travel.actCompleted(CdZone.PALMTREE_PANIC, 1);
                        assertEquals(!timeAttack, travel.goodFuture(), "act results");
                    },
                    () -> {
                        travel.gameOver();
                        travel.levelStart(CdStagePayload.R11A);
                        assertEquals(!timeAttack, travel.goodFuture(), "stage load");
                    });
        });
    }

    private static void frameAt(AbstractPlayableSprite sonic, ObjectSpawn placement) {
        NativePositionOps.writeXPosPreserveSubpixel(sonic, Math.max(0, placement.x() - 100));
        NativePositionOps.writeYPosPreserveSubpixel(sonic, placement.y());
        var camera = GameServices.camera();
        camera.updatePosition(true);
        GameServices.level().getObjectManager().reset(camera.getX());
        new HeadlessTestRunner(sonic).stepIdleFrames(2);
    }

    private static boolean live(ObjectSpawn spawn) {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(object -> !object.isDestroyed() && spawn.equals(object.getSpawn()));
    }

    private static CdLevel level() {
        return (CdLevel) GameServices.level().getCurrentLevel();
    }

    private void run(CdStagePayload payload, int selection, boolean timeAttack,
                     Consumer<AbstractPlayableSprite> check) throws Exception {
        TestEnvironment.resetAll();
        var previous = EngineServices.current();
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var sonic = CdFixtureReplay.boot(config, disc, CdStagePayload.R11A);
            if (timeAttack) {
                var scenes = SessionManager.requireCurrentGameModule().getGameService(CdSceneCoordinator.class);
                scenes.timeAttackSelected(selection);
                GameServices.level().loadZoneAndAct(scenes.titleStartZone(), scenes.titleStartAct());
            } else {
                GameServices.level().loadZoneAndAct(SonicCdZoneRegistry.zoneIndex(payload),
                        SonicCdZoneRegistry.actIndex(payload));
            }
            assertEquals(payload, level().payload());
            check.accept(sonic);
        } finally {
            GroundSensor.setLevelManager(null);
            SessionManager.clear();
            GameModuleRegistry.reset();
            EngineServices.configure(previous);
            TestEnvironment.resetAll();
        }
    }
}
