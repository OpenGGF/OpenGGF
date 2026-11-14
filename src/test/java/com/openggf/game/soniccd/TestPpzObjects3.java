package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.ppz.PpzPlatform;
import com.openggf.game.soniccd.objects.ppz.PpzTunnelPath;
import com.openggf.sprites.NativePositionOps;
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
 * Focused behaviour of the Palmtree Panic objects the objects-3 lane added, on stages loaded from the disc; each case cites the routine whose
 * values it checks (scddisasm {@code src/Level/Palmtree Panic/Objects}). Expected values come from the routines, never from running the
 * implementation. Native comparisons: {@link TestMmzObjectsAppearances} and {@link TestSonicCdFixtureFrontier}.
 */
class TestPpzObjects3 {
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


    @Test
    void platformWidthAndFrameComeFromTheLowSubtypeBits() throws Exception {
        boot(CdStagePayload.R11A);
        // The widths table is dc.b $10, $20, $30, 0 (Platform/Main.asm:85), indexed by subtype & 3, which is also the frame
        // (ObjPlatform_Init, Platform/Main.asm:30-54).
        PpzPlatform small = add(new PpzPlatform(at(0x60, -0x80, 0x21, 0x40)));
        PpzPlatform medium = add(new PpzPlatform(at(0xA0, -0x80, 0x21, 0x41)));
        PpzPlatform large = add(new PpzPlatform(at(0xE0, -0x80, 0x21, 0x42)));
        runner.stepIdleFrames(1);
        assertEquals(2, small.routine(), "ObjPlatform_Init: addq.b #2,oRoutine (:80)");
        assertEquals(0, small.mapFrame());
        assertEquals(0x10, small.solidWidth());
        assertEquals(1, medium.mapFrame());
        assertEquals(0x20, medium.solidWidth());
        assertEquals(2, large.mapFrame());
        assertEquals(0x30, large.solidWidth());
    }

    @Test
    void platformOfFormFiveFallsThirtyOneFramesAfterItIsStoodOn() throws Exception {
        boot(CdStagePayload.R11A);
        PpzPlatform platform = add(new PpzPlatform(at(0, 0x19, 0x21, 0x50)));
        int stoodOnAt = -1;
        for (int frame = 1; frame <= 14 && stoodOnAt < 0; frame++) {
            runner.stepIdleFrames(1);
            if (platform.sinkOffset() > 0) stoodOnAt = frame;
        }
        assertTrue(stoodOnAt > 0, "TopSolidObject seats Sonic (:164-170)");
        int startY = platform.getY();
        runner.stepIdleFrames(28);
        assertTrue(platform.getY() <= startY + 4, "the 30 frame timer is still running (:241-248): y " + platform.getY());
        runner.stepIdleFrames(30);
        assertTrue(platform.getY() > startY + 20, "oYVel grows $40 a frame once it drops (:252-263): y " + platform.getY() + " from " + startY);
    }

    @Test
    void tunnelPathCapturesThePlayerAndSetsTheSecondLegFromTheStagePath() throws Exception {
        boot(CdStagePayload.R11A);
        // Path 0 starts at ($1440, $F0) and its second point is ($1478, $108) (ObjTunnelPath_Path0, Tunnel Path/Main.asm:403-405).
        com.openggf.game.GameServices.camera().setX((short) 0x13A0);   // the path is placed far from the stage start
        com.openggf.game.GameServices.camera().setY((short) 0x70);
        NativePositionOps.writeXPosPreserveSubpixel(sonic, 0x1440);
        NativePositionOps.writeYPosPreserveSubpixel(sonic, 0xF0);
        PpzTunnelPath path = add(new PpzTunnelPath(new ObjectSpawn(0x1440, 0xF0, 0x07, 0, 0, false, 0)));
        runner.stepIdleFrames(2);
        assertTrue(sonic.isObjectControlled(), "ObjTunnelPath_Main sets oPlayerCtrl (:213-223)");
        assertEquals(0x1000, sonic.getGSpeed(), "ObjTunnelPath_SetPlayerGVel: $1000 (:376)");
        // GotPlayer sets the speed for the zero-length first leg; MovePlayer then arrives at point 0 and starts the leg to point 1:
        // dx $38 > dy $18, so X runs at the ground speed, the leg takes (dx << 16) / $1000 = $380 (high byte 3 frames) and Y is
        // (dy << 16) / $380 (:349-371).
        runner.stepIdleFrames(2);
        assertEquals(0x1000, sonic.getXSpeed());
        assertEquals((0x18 << 16) / 0x380, sonic.getYSpeed());
        assertEquals(0x380, path.steps());
    }
}
