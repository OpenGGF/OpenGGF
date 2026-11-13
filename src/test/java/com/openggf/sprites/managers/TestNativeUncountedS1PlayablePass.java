package com.openggf.sprites.managers;

import com.openggf.physics.Direction;
import com.openggf.game.GameServices;
import com.openggf.game.GroundMode;
import com.openggf.level.objects.ObjectManagerInternalAccess;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_1)
class TestNativeUncountedS1PlayablePass {
    @AfterEach void cleanup() { TestEnvironment.resetAll(); }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 60})
    void endingInitialPassRunsForcedMovementWithoutServicingAnyClock(int priorFrozenObjectPasses) throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, 7, 0);
        try {
            var sprites = GameServices.sprites();
            var level = GameServices.level();
            var objects = level.getObjectManager();
            var sonic = sprites.getMainPlayable();
            assertEquals(0x620, sonic.getCentreX() & 0xffff);
            sonic.setAir(false); sonic.setAngle((byte) 0); sonic.setGroundMode(GroundMode.GROUND);
            sonic.setOnObject(false); sonic.setPushing(false);
            sonic.setDirection(Direction.LEFT); sonic.setControlLocked(true);
            sonic.setForcedInputMask(AbstractPlayableSprite.INPUT_LEFT);
            sonic.setGSpeed((short) -0x800); sonic.forceAnimationRestart();
            level.setFrameCounter(73); sprites.setFrameCounter(73 + priorFrozenObjectPasses); objects.initVblaCounter(0x1234);
            int ordinal = objects.getFrameCounter();
            var dispatch = ObjectManagerInternalAccess.captureInitialDispatch(objects);
            try (var scope = dispatch.begin(GameServices.camera().getX(), sonic, sprites.getSidekicks())) {
                sprites.runNativeUncountedPlayablePass();
                dispatch.requireCurrent();
            }
            // End_LoadSonic's first ordinary Sonic pass caps the forced run
            // to native top speed and moves before the first VBlank_Ending.
            assertEquals(0x61a, sonic.getCentreX() & 0xffff);
            assertEquals(0x1234, objects.getVblaCounter());
            assertEquals(73, level.getFrameCounter());
            assertEquals(73 + priorFrozenObjectPasses, sprites.getFrameCounter());
            assertEquals(ordinal + 1, objects.getFrameCounter());
            assertEquals(AbstractPlayableSprite.INPUT_LEFT,
                    sonic.getLogicalInputState() & AbstractPlayableSprite.INPUT_LEFT);
            assertFalse(objects.hasActiveInitialProcessSpritesDispatch());
        } finally { shared.dispose(); }
    }

    @Test void liveEndingCallerSelectsTheRealContinuationWithoutRecordedTiming() throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
        try {
            var level = GameServices.level();
            assertFalse(com.openggf.level.NativeLevelLoadAccess.hasPhaseAdmission(level));
            try (var caller = com.openggf.level.NativeLevelLoadAccess.beginModeSwitchLoad(level, 7, 0)) {
                level.loadZoneAndAct(7, 0);
            }
            assertTrue(com.openggf.level.NativeLevelLoadAccess.isTitlelessPending(level),
                    "immediate readiness still retains the actual counted entry wait");
            assertNull(com.openggf.level.NativeLevelLoadAccess.pendingPhase(level));
            assertEquals(0x61a, GameServices.sprites().getMainPlayable().getCentreX() & 0xffff,
                    "End_LoadSonic performed its real uncounted forced-movement pass");
            try (var secondCaller = com.openggf.level.NativeLevelLoadAccess.beginModeSwitchLoad(level, 7, 0)) {
                // The previous caller scope closed before returning its pending continuation.
            }
        } finally { shared.dispose(); }
    }

    @Test void unownedPassCannotRunPlayablePhysics() throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, 7, 0);
        try {
            var sprites = GameServices.sprites();
            short x = sprites.getMainPlayable().getCentreX();
            assertThrows(IllegalStateException.class, sprites::runNativeUncountedPlayablePass);
            assertEquals(x, sprites.getMainPlayable().getCentreX());
        } finally { shared.dispose(); }
    }
}
