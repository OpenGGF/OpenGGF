package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSszAct2CameraController {
    private HeadlessTestFixture boot() {
        var fixture=HeadlessTestFixture.builder().withZoneAndAct(10,1).withFreshLevelStartLifecycle().build();
        fixture.sprite().setDebugMode(true);
        return fixture;
    }
    private SszZoneRuntimeState state() { return (SszZoneRuntimeState)GameServices.zoneRuntimeState(); }
    private SszAct2CameraController controller() {
        return GameServices.level().getObjectManager().activeObjectsOfType(SszAct2CameraController.class).getFirst();
    }
    @Test void screenInitAllocatesCameraAfterArrivalAndGatesFractionalDrift() {
        var fixture=boot(); fixture.stepIdleFrames(1);
        var manager=GameServices.level().getObjectManager();
        var arrival=manager.activeObjectsOfType(SszArrivalControllerObjectInstance.class).getFirst();
        var controller=controller();
        assertTrue(controller.getSlotIndex()>arrival.getSlotIndex());
        controller.update(0,null); assertEquals(0,state().cloudOffsetFixed());
        state().setSpecialVIntRoutine(4);
        controller.update(1,null); assertEquals(0x11B,state().cloudOffsetFixed());
        assertEquals(0,state().eventsBgWord(0));
        controller.update(2,null); controller.update(3,null);
        assertEquals(3*0x11B,state().cloudOffsetFixed());
        assertEquals(0xFFFF,state().eventsBgWord(0),"$30=1 preseed followed by native swing reset");
    }
    @Test void fractionalOffsetAndSwingReplayTogetherAcrossRestore() {
        var fixture=boot(); fixture.stepIdleFrames(1); state().setSpecialVIntRoutine(4);
        fixture.stepIdleFrames(120);
        var registry=TestEnvironment.activeGameplayMode().getRewindRegistry(); var saved=registry.capture();
        fixture.stepIdleFrames(80); int offset=state().cloudOffsetFixed(),swing=state().eventsBgWord(0);
        registry.restore(saved); fixture.stepIdleFrames(80);
        assertEquals(offset,state().cloudOffsetFixed()); assertEquals(swing,state().eventsBgWord(0));
        assertEquals(1,GameServices.level().getObjectManager().activeObjectsOfType(SszAct2CameraController.class).size());
    }
    @Test void negativeEndingSignalRequiresTwoChangedZeroCrossings() {
        var fixture=boot(); fixture.stepIdleFrames(1); state().setSpecialVIntRoutine(4); state().setEventsFg4(0xFF00);
        int zeroCrossings=0,previous=0;
        for(int pass=0;pass<2000&&state().specialVIntRoutine()!=0xC;pass++) {
            controller().update(pass,null);
            int next=(short)state().eventsBgWord(0);
            if(next==0&&previous!=0)zeroCrossings++;
            if(zeroCrossings<2)assertEquals(4,state().specialVIntRoutine());
            previous=next;
        }
        assertEquals(2,zeroCrossings); assertEquals(0xC,state().specialVIntRoutine()); assertEquals(0,state().eventsFg4());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void seededPresentationCameraReplaysBothEmeraldBranches(boolean allEmeralds) {
        var fixture = boot(); fixture.stepIdleFrames(1);
        if (allEmeralds) for (int i = 0; i < 7; i++) TestEnvironment.objectServices().gameState().markEmeraldCollected(i);
        state().setSpecialVIntRoutine(4); state().setEventsFg4(0xFF00);
        var owner = controller();
        for (int i = 0; i < 2000 && owner.routineForTest() == 0; i++) owner.update(i, null);
        assertEquals(4, owner.routineForTest());
        assertEquals(0x20, owner.cloudSpeedForTest(), "routine4 falls through on the second zero crossing");
        // Declare the camera start only; let the controller derive every later cloud/camera value.
        fixture.camera().setY((short) 0x400);
        for (int i = 0; i < 140; i++) owner.update(i, null);
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        int expectedY = 0, expectedCloud = 0, expectedSteps = 0;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) { registry.restore(saved); owner = controller(); }
            int steps = 0;
            while (!owner.isDestroyed() && owner.routineForTest() == 4 && steps < 5000) {
                int y = fixture.camera().getY();
                int cloud = state().cloudOffsetFixed();
                owner.update(steps++, null);
                int speed = owner.cloudSpeedForTest();
                assertTrue(speed <= 0x8000);
                assertEquals(cloud + (allEmeralds ? -speed : speed), state().cloudOffsetFixed());
                int delta = fixture.camera().getY() - y;
                assertTrue(allEmeralds ? delta == 0 || delta == -1 : delta == 0 || delta == 1);
            }
            assertTrue(steps < 5000);
            assertEquals(allEmeralds ? 0x2A0 : 0x600, fixture.camera().getY());
            assertEquals(0xFF, state().eventsFg4Low());
            assertEquals(allEmeralds, owner.isDestroyed());
            if (!allEmeralds) assertEquals(8, owner.routineForTest());
            if (replay == 0) {
                expectedY = fixture.camera().getY(); expectedCloud = state().cloudOffsetFixed(); expectedSteps = steps;
            } else {
                assertEquals(expectedY, fixture.camera().getY()); assertEquals(expectedCloud, state().cloudOffsetFixed());
                assertEquals(expectedSteps, steps);
            }
        }
    }

    @Test
    void incompleteEmeraldIslandCameraWaitsForRedrawAndReplaysItsFractionalSlowdown() {
        var fixture = boot(); fixture.stepIdleFrames(1);
        state().setSpecialVIntRoutine(4); state().setEventsFg4(0xFF00);
        var owner = controller();
        for (int i = 0; i < 2000 && owner.routineForTest() == 0; i++) owner.update(i, null);
        fixture.camera().setY((short) 0x400);
        for (int i = 0; i < 5000 && owner.routineForTest() == 4; i++) owner.update(i, null);
        assertEquals(8, owner.routineForTest());
        assertEquals(0x8000, owner.cloudSpeedForTest());
        state().setForegroundRoutine(0x14);
        fixture.camera().setY((short) 0x720); // declared second-redraw completion boundary
        for (int i = 0; i < 10; i++) owner.update(i, null);
        assertEquals(0x720, fixture.camera().getY(), "stage14 still owns the camera");
        state().setForegroundRoutine(0x18);
        owner.update(0, null);
        assertEquals(0x720, fixture.camera().getY(), "first half-pixel remains fractional");
        owner.update(1, null);
        assertEquals(0x721, fixture.camera().getY());
        for (int i = 0; i < 351; i++) owner.update(i, null);
        assertTrue(owner.cloudSpeedForTest() < 0x8000, "cross $7D0 before capturing slowdown");
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        var saved = registry.capture();
        int expectedSteps = -1;
        int expectedSpeed = 0;
        for (int replay = 0; replay < 2; replay++) {
            if (replay != 0) { registry.restore(saved); owner = controller(); }
            int steps = 0;
            while (!owner.isDestroyed() && steps < 400) owner.update(steps++, null);
            assertTrue(owner.isDestroyed(), "retire at the documented actual-ending boundary");
            assertEquals(0x804, fixture.camera().getY());
            assertTrue(owner.cloudSpeedForTest() < 0);
            if (replay == 0) { expectedSteps = steps; expectedSpeed = owner.cloudSpeedForTest(); }
            else { assertEquals(expectedSteps, steps); assertEquals(expectedSpeed, owner.cloudSpeedForTest()); }
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {320, 800})
    void negativeEndingSignalDrivesCameraIntoTheRetainedRedrawAndReplays(int width) {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? com.openggf.configuration.WidescreenAspect.NATIVE_4_3
                        : com.openggf.configuration.WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        try {
            var fixture = boot(); fixture.stepIdleFrames(300);
            for (int i = 0; i < 7; i++) TestEnvironment.objectServices().gameState().markEmeraldCollected(i);
            // Declared post-stop presentation entry: the excluded ending owner would
            // write the negative signal while the defeated arena holds camera control.
            state().setEndingRunning(true);
            state().setForegroundRoutine(8);
            state().setEventsFg4(0xFF00);
            state().setSpecialVIntRoutine(4);
            fixture.camera().setScrollLocked(true);
            boolean sawCameraRoutine4 = false;
            int passes = 0;
            while (state().foregroundRoutine() != 0xC && passes++ < 5000) {
                fixture.stepIdleFrames(1);
                var controllers = GameServices.level().getObjectManager()
                        .activeObjectsOfType(SszAct2CameraController.class);
                sawCameraRoutine4 |= !controllers.isEmpty() && controllers.getFirst().routineForTest() == 4;
            }
            assertTrue(sawCameraRoutine4);
            assertEquals(0xC, state().foregroundRoutine(), "negative signal must reach the positive redraw gate");
            assertEquals(0x2A0, fixture.camera().getY());
            assertEquals(15, state().endingPlane().remaining(), "stage8 does not redraw in its own pass");
            assertEquals(1, GameServices.level().getObjectManager().activeObjectsOfType(SszEndingIslandMask.class).size());
            fixture.stepIdleFrames(3);
            assertEquals(9, state().endingPlane().remaining());
            var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
            var saved = registry.capture();
            fixture.stepIdleFrames(5);
            assertEquals(0x10, state().foregroundRoutine());
            assertEquals(0, fixture.camera().getX());
            assertEquals(0, fixture.camera().getY());
            byte[] expected = state().captureBytes();
            registry.restore(saved); fixture.stepIdleFrames(5);
            assertArrayEquals(expected, state().captureBytes());
        } finally {
            config.clearSessionOverrides();
        }
    }

    /**
     * loc_58B7C (sonic3k.asm:117827-117839) has no branch after its addq.w #4: the pass that
     * finishes the bottom-up redraw falls into loc_58B9E and runs sub_5B18E's loc_5B1A2 init.
     * The native Knuckles complete run holds Events_routine_fg $10 and _unkFA86 2 in the same
     * frame (input 422608); a one-pass delay started every credit line one frame late.
     */
    @Test
    void finishedRedrawEntersTheCreditsOwnerInTheSamePass() {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        try {
            var fixture = boot(); fixture.stepIdleFrames(300);
            for (int i = 0; i < 7; i++) TestEnvironment.objectServices().gameState().markEmeraldCollected(i);
            // Declared ending entry: loc_5E70E's sub_5FE82 progress plus the flight's negative signal.
            com.openggf.game.sonic3k.ending.S3kEndingCoordinator.initializeProgress(TestEnvironment.objectServices());
            state().setEndingRunning(true);
            state().setForegroundRoutine(8);
            state().setEventsFg4(0xFF00);
            state().setSpecialVIntRoutine(4);
            fixture.camera().setScrollLocked(true);
            int passes = 0;
            while (state().foregroundRoutine() != 0x10 && passes++ < 5000) fixture.stepIdleFrames(1);
            assertEquals(0x10, state().foregroundRoutine());
            assertEquals(2, state().ending().credits().routine(),
                    "loc_58B9E runs sub_5B18E's init on the pass that finishes the redraw");
        } finally {
            config.clearSessionOverrides();
        }
    }

    /**
     * loc_58BBC (sonic3k.asm:117846-117875) tests _unkFAAE (credits part two), not completion:
     * on the pass where the locked-on Camera_Y_pos equals $1660 it clears _unkFAB9 (no new
     * clouds), lifts Camera_Y_pos_BG_copy by $100, loads Pal_Ending2 and starts BG routine 4.
     * The native Knuckles complete run holds Events_routine_bg 4 and _unkFAB9 0 at the credits'
     * end (input 432708); Camera_Y_pos ends at $1CA0, so a completion gate never fires.
     */
    @Test
    void partTwoLiftsTheIslandAndStopsCloudsAtCameraY1660() throws Exception {
        var config = com.openggf.configuration.SonicConfigurationService.getInstance();
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(com.openggf.configuration.SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        try {
            var fixture = boot(); fixture.stepIdleFrames(300);
            var services = TestEnvironment.objectServices();
            for (int i = 0; i < 7; i++) services.gameState().markEmeraldCollected(i);
            com.openggf.game.sonic3k.ending.S3kEndingCoordinator.initializeProgress(services);
            state().setEndingRunning(true);
            state().setForegroundRoutine(8);
            state().setEventsFg4(0xFF00);
            state().setSpecialVIntRoutine(4);
            fixture.camera().setScrollLocked(true);
            int passes = 0;
            while (state().foregroundRoutine() != 0x10 && passes++ < 5000) fixture.stepIdleFrames(1);
            assertEquals(0x10, state().foregroundRoutine());
            // Declared: loc_5EA30's cloud configuration, then sub_5B18E advanced to just
            // before $1660 (the per-pass ScreenEvent gate is what is under test).
            var credits = state().ending().credits();
            if (!credits.active()) fixture.stepIdleFrames(1); // independent of the loc_58B9E hand-off pass
            assertTrue(credits.active());
            state().ending().clouds().configure(0x3F, 0, 0x80);
            for (int i = 0; i < 20000 && (credits.scroll() >> 16) < 0x1650; i++) credits.step(services.romReader());
            assertEquals(4, credits.partIndex(), "Camera_Y_pos $1650 is in credits part two");
            int backgroundY = state().backgroundCameraY();
            passes = 0;
            while ((fixture.camera().getY() & 0xFFFF) < 0x1660 && passes++ < 100) {
                assertEquals(0, state().backgroundRoutine(), "nothing changes before $1660");
                fixture.stepIdleFrames(1);
            }
            assertEquals(0x1660, fixture.camera().getY() & 0xFFFF);
            assertEquals(4, state().backgroundRoutine(), "addq.w #4,(Events_routine_bg).w");
            assertEquals(backgroundY + 0x100, state().backgroundCameraY(), "Camera_Y_pos_BG_copy+$100");
            assertEquals(0, state().ending().clouds().mask(), "clr.b (_unkFAB9).w");
            assertEquals(0x80, state().ending().clouds().yVelocity(), "_unkFAC4 is not touched");
        } finally {
            config.clearSessionOverrides();
        }
    }

}
