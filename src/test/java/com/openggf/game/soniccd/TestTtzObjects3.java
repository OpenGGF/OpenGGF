package com.openggf.game.soniccd;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.session.EngineContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.soniccd.level.CdLevel;
import com.openggf.game.soniccd.objects.shared.SharedSwitchFlags;
import com.openggf.game.soniccd.objects.ttz.TtzDoor;
import com.openggf.game.soniccd.objects.ttz.TtzSwitch;
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
 * Focused behaviour of the Tidal Tempest objects the objects-3 lane added, on stages loaded from the disc; each case cites the routine whose
 * values it checks (scddisasm {@code src/objects/r4}). Expected values come from the routines, never from running the
 * implementation. Native comparisons: {@link TestMmzObjectsAppearances} and {@link TestSonicCdFixtureFrontier}.
 */
class TestTtzObjects3 {
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


    private SharedSwitchFlags flags() {
        return GameModuleRegistry.getCurrent().getGameService(SharedSwitchFlags.class);
    }

    @Test
    void switchHoldsItsFlagWhileStoodOnAndSinksFourPixels() throws Exception {
        boot(CdStagePayload.R41A);
        ObjectSpawn spawn = at(0, 0x1F, 0x27, 5);
        TtzSwitch button = add(new TtzSwitch(spawn));
        int pressedAt = -1;
        for (int frame = 1; frame <= 10 && pressedAt < 0; frame++) {
            runner.stepIdleFrames(1);
            if (button.pressed()) pressedAt = frame;
        }
        assertTrue(pressedAt > 0, "SolidObject seats the player (objects/r4/switch.asm:39-45)");
        assertEquals(0xC0 | 0x20, flags().get(5) & 0xE0, "bits 7 and 6 set, bit 5 toggled (:56-69)");
        assertEquals(spawn.y() + 4, button.getY(), "addq.w #4,obj.y on the press frame (:78-79)");
    }

    @Test
    void doorTakesItsSizeFromTheKindRowAndOpensWhileTheSwitchIsHeld() throws Exception {
        boot(CdStagePayload.R41A);
        // Kind 0 row of byte_20CD8A: width $40, height 8, travel $80 (objects/r4/door.asm:53); slides left.
        ObjectSpawn spawn = at(0x80, -0x80, 0x28, 0x03);
        TtzDoor door = add(new TtzDoor(spawn));
        runner.stepIdleFrames(2);
        assertEquals(0x40, door.solidWidth());
        assertEquals(8, door.solidYRadius());
        assertEquals(0, door.travel(), "the switch is not held: the door stays shut (:61-66)");
        flags().set(3, 0x80);
        runner.stepIdleFrames(2);
        assertEquals(16, door.travel(), "8 a frame while the switch is held (sub_20CF24, :262-267)");
        assertEquals(spawn.x() - 16, door.getX(), "kind 0 slides left (DoorObject_1_Routine0, :210-217)");
    }
}
