package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.ccz.CczBlock;
import com.openggf.game.soniccd.objects.ccz.CczHiddenBlock;
import com.openggf.game.soniccd.objects.ccz.CczSpikes;
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
 * Focused behaviour of the Collision Chaos objects the objects-3 lane added, on stages loaded from the disc; each case cites the routine whose
 * values it checks (scddisasm {@code src/Level/Collision Chaos/Objects}). Expected values come from the routines, never from running the
 * implementation. Native comparisons: {@link TestMmzObjectsAppearances} and {@link TestSonicCdFixtureFrontier}.
 */
class TestCczObjects3 {
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
    void blockLoadsItsFirstMotionRecordThenMovesOnTheNextFrame() throws Exception {
        boot(CdStagePayload.R31A);
        CczBlock block = add(new CczBlock(at(0x60, -0x80, 0x21, 0)));
        int x0 = block.getX();
        runner.stepIdleFrames(1);
        // ObjBlock_MotionData row 0: $40 frames, acceleration -8, velocity $200 (Block/Main.asm:196-197). The legacy build
        // returns after loading the record (:134-139).
        assertEquals(0x40, block.timer());
        assertEquals(x0, block.getX());
        runner.stepIdleFrames(1);
        // Move: $200 - 8 = $1F8 (8.8) added to X as 16.16: one pixel and 0xF800 of sub-pixel (:142-150).
        assertEquals(0x3F, block.timer());
        assertEquals(x0 + 1, block.getX());
        runner.stepIdleFrames(0x3F);
        assertEquals(1, block.direction(), "the record ends after $40 moves (:150-159)");
        assertEquals(0x40, block.timer(), "and the next record is loaded at once (:109-133)");
    }

    @Test
    void hiddenBlockSlidesDownWhileThePlayerIsLeftOfIt() throws Exception {
        boot(CdStagePayload.R31A);
        ObjectSpawn spawn = at(0x60, -0x80, 0x22, 0);
        CczHiddenBlock block = add(new CczHiddenBlock(spawn));
        runner.stepIdleFrames(4);
        // The player is left of its left edge: the offset grows 8 a frame to $20 (ObjHiddenBlock_CheckPlayer, Block/Main.asm:351-401).
        assertEquals(0x20, block.offset());
        assertEquals(spawn.y() + 0x20, block.getY());
    }

    @Test
    void spikesTakeTheirSizeAndTouchTypeFromTheOrientationRow() throws Exception {
        boot(CdStagePayload.R31A);
        // ObjSpikes_Properties (scddisasm/src/Level/Collision Chaos/Objects/Spikes/Main.asm:59-63): up $10/8/0, down $12/$C/$83.
        CczSpikes up = add(new CczSpikes(at(0x60, -0x80, 0x20, 0)));
        CczSpikes down = add(new CczSpikes(at(0xA0, -0x80, 0x20, 1)));
        runner.stepIdleFrames(1);
        assertEquals(0x10, up.solidWidth());
        assertEquals(8, up.solidYRadius());
        assertEquals(0, up.getCollisionFlags());
        assertEquals(0x12, down.solidWidth());
        assertEquals(0xC, down.solidYRadius());
        assertEquals(0x83, down.getCollisionFlags());
    }

    @Test
    void movingSpikesExtendInStepsOfEight() throws Exception {
        boot(CdStagePayload.R31A);
        ObjectSpawn spawn = at(0x60, -0x80, 0x20, 4);
        CczSpikes spikes = add(new CczSpikes(spawn));
        runner.stepIdleFrames(4);
        // Subtype bit 2 moves; subtype & 3 = 0 is down; the offset grows 8 a frame to $20 (Spikes/Main.asm:135-164, 200-228).
        assertEquals(0x20, spikes.moveOffset());
        assertEquals(spawn.y() + 0x20, spikes.getY());
    }
}
