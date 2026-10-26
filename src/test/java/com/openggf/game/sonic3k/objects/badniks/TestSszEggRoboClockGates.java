package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The EggRobo routines read {@code (V_int_run_count+3).w}: the low byte of the big-endian
 * longword counter, i.e. {@code V_int_run_count & $FF}. The {@code +3} is an address, so none
 * of these gates is shifted by three ticks.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszEggRoboClockGates {

    /** loc_85B02 has no Draw_Sprite; loc_915F6 must still read the placeholder's retained bit7. */
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 31})
    void edgeReleaserKeepsPlaceholderVisibilityThroughTheUndrawnRestore(int pixelsOutside) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(
                0x240 + pixelsOutside, 0xC00, 0xA0, 4, 0, false, 0));
        robo.setServices(TestEnvironment.objectServices());
        TestEnvironment.objectServices().objectManager().addDynamicObject(robo);
        assertEquals(0x20, robo.getOnScreenHalfWidth());
        robo.update(1, null);
        robo.refreshPostCameraRenderState(); // Its $20 placeholder overlaps the right edge.
        robo.update(2, null); // loc_85B02 restores the continuation but submits no sprite.
        // A32px left camera move removes even the old placeholder overlap,
        // while the actor remains inside Sprite_CheckDeleteTouch's coarse-X window.
        AbstractObjectInstance.updateCameraBounds(0xE0, 0xB80, 0x220, 0xC60, 0x1000);
        robo.refreshPostCameraRenderState(); // No submission: must preserve the previous true bit.
        robo.update(16, null);
        assertEquals(3, robo.animalsLeftForTest(), "initial loc_915F6 sees the retained placeholder bit");
        assertEquals(4, robo.getOnScreenHalfWidth(), "ObjDat3_919B2 now owns the render extent");
        assertEquals(4, robo.getOnScreenHalfHeight());
        robo.refreshPostCameraRenderState(); // Actual active submission is now outside: bit7 clears.
        robo.update(32, null);
        assertEquals(3, robo.animalsLeftForTest(), "the next release reads that false render bit");
        robo.refreshPostCameraRenderState();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        robo.update(48, null);
        assertEquals(3, robo.animalsLeftForTest(), "camera return cannot replace the preceding render bit");
        robo.refreshPostCameraRenderState(); // Releaser extent is4, rather than its old placeholder32.
        robo.update(64, null);
        assertEquals(pixelsOutside < 4 ? 2 : 3, robo.animalsLeftForTest(),
                "Render_Sprites uses the current ObjDat extent and exclusive right edge");
    }

    /** Drive the original acceleration; no velocity, pairing word or private phase is supplied. */
    @Test
    void flyByKeepsItsPairBlockedWhileItsRenderBoxStillOverlapsTheScreen() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        var state = (SszZoneRuntimeState) services.zoneRuntimeState();
        assertFalse(state.eggRoboFlyByPassed(0));
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 0, 0, false, 0));
        robo.setServices(services);
        services.objectManager().addDynamicObject(robo);
        releasePlaceholder(robo);
        int tick = 3;
        while (robo.currentYVelocity() >= 0 && tick < 80) {
            robo.update(tick++, null);
            robo.refreshPostCameraRenderState();
        }
        assertTrue(robo.currentYVelocity() < 0, "loc_91548 reached its upward branch naturally");
        for (int pass = 0; pass < 3; pass++) {
            robo.update(tick++, null);
            assertFalse(robo.isDestroyed());
            assertFalse(state.eggRoboFlyByPassed(0));
            int cameraX = robo.getX() + 16; // Centre outside, but the $20 render box overlaps.
            int cameraY = robo.getY() - 0x60;
            AbstractObjectInstance.updateCameraBounds(cameraX, cameraY,
                    cameraX + 320, cameraY + 224, 0x1000);
            robo.refreshPostCameraRenderState();
        }
        robo.update(tick++, null);
        assertFalse(state.eggRoboFlyByPassed(0), "last frame's overlapping box still blocks the pair");
        int cameraX = robo.getX() + 33;
        int cameraY = robo.getY() - 0x60;
        AbstractObjectInstance.updateCameraBounds(cameraX, cameraY,
                cameraX + 320, cameraY + 224, 0x1000);
        robo.refreshPostCameraRenderState();
        assertFalse(state.eggRoboFlyByPassed(0), "rendering alone never publishes the pairing word");
        robo.update(tick, null);
        assertTrue(robo.isDestroyed());
        assertTrue(state.eggRoboFlyByPassed(0), "loc_91570 consumes the first non-overlapping render");
    }

    /** Component dispatch oracle: Obj_WaitOffscreen, loc_85B02, then Obj_EggRobo's continuation. */
    @ParameterizedTest
    @ValueSource(ints = {0, 2, 4})
    void everySubtypeWaitsForTheRetainedRenderThenReturnsBeforeInitializing(int subtype) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, subtype, 0, false, 0));
        robo.setServices(TestEnvironment.objectServices());
        TestEnvironment.objectServices().objectManager().addDynamicObject(robo);
        assertEquals(0, robo.getCollisionFlags(), "Map_Offscreen has no enemy attributes");
        robo.update(0, null);
        assertFalse(robo.isDestroyed(), "sub_91914 must not run in the placeholder pass");
        assertEquals(4, robo.animalsLeftForTest());
        assertEquals(0x7F, robo.scaleIndexForTest());
        robo.refreshPostCameraRenderState();
        // Move the camera after Render_Sprites: release consumes its retained bit, not a new query.
        AbstractObjectInstance.updateCameraBounds(0x500, 0xB80, 0x640, 0xC60, 0x1000);
        robo.update(16, null);
        assertFalse(robo.isDestroyed(), "loc_85B02 restores the continuation and returns");
        assertEquals(0, robo.getCollisionFlags());
        assertEquals(4, robo.animalsLeftForTest());
        assertEquals(0x7F, robo.scaleIndexForTest());
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        robo.update(32, null);
        if (subtype == 0) {
            assertEquals(0x7C, robo.scaleIndexForTest(), "loc_91874 falls through into loc_91526");
        } else if (subtype == 2) {
            assertTrue(robo.isDestroyed(), "only now may sub_91914 reject the unreleased pair");
        } else {
            assertEquals(3, robo.animalsLeftForTest(), "loc_918FC falls through into loc_915F6");
        }
    }

    /** Isolated fighter contract; the pairing bit is explicitly supplied, not natural-route evidence. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void fighterInitializationKeepsXAndCarriesTheLiteralHoverFractions(int flags) {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var services = TestEnvironment.objectServices();
        ((SszZoneRuntimeState) services.zoneRuntimeState()).markEggRoboFlyByPassed(0);
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 2, flags, false, 0));
        robo.setServices(services);
        services.objectManager().addDynamicObject(robo);
        releasePlaceholder(robo);
        // sub_918E2 starts at $100; loc_9159A subtracts8 before MoveSprite2's long addition.
        int[] velocities = {0xF8, 0xF0, 0xE8, 0xE0, 0xD8, 0xD0, 0xC8, 0xC0};
        int[] yOffsets = {0, 1, 2, 3, 4, 5, 6, 6};
        for (int i = 0; i < velocities.length; i++) {
            robo.update(0x100 + i, null);
            assertEquals(velocities[i], robo.currentYVelocity(), "native hover velocity at pass " + i);
            assertEquals(0x200, robo.getX(), "loc_918C4 never sets a horizontal velocity");
            assertEquals(0xC00 + yOffsets[i], robo.getY(), "MoveSprite2 retains fractional Y at pass " + i);
        }
    }

    /** loc_9164E/loc_9167E literal endpoints, including the retained half pixel at hover handoff. */
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void releaserLaunchDirectionAndFractionalRiseFallReachTheNativeHoverHandoff(int flags) {
        var robo = onScreenReleaser(flags);
        for (int tick = 0; tick <= 64; tick += 16) robo.update(tick, null);
        assertEquals("RISING", robo.stateForTest());
        assertEquals(0x14, robo.getOnScreenHalfWidth(), "conversion installs ObjDat3_919A6");
        assertEquals(0x18, robo.getOnScreenHalfHeight());
        assertEquals(0x200, robo.getX(), "final release returns before the first MoveSprite2");
        assertEquals(0xC00, robo.getY());
        int[] firstY = {0xBFF, 0xBFF, 0xBFF, 0xBFF, 0xBFF, 0xBFE, 0xBFE, 0xBFD};
        for (int pass = 1; pass <= 32; pass++) {
            robo.update(64 + pass, null);
            assertEquals(0x200 + (flags == 0 ? -3 : 3) * pass, robo.getX());
            if (pass <= firstY.length) assertEquals(firstY[pass - 1], robo.getY());
        }
        assertEquals("FALLING", robo.stateForTest());
        assertEquals(0xBDF, robo.getY(), "32 upward accelerations sum to -33 pixels");
        for (int pass = 1; pass <= 24; pass++) robo.update(96 + pass, null);
        assertEquals("HOVER", robo.stateForTest());
        assertEquals(0xBD4, robo.getY(), "falling ends at $BD4.8000");
        assertEquals(0x100, robo.currentYVelocity());
        robo.update(121, null);
        assertEquals(0xBD5, robo.getY(), "the half pixel survives sub_918E2");
        assertEquals(0xF8, robo.currentYVelocity());
    }

    /** {@code loc_915F6}: {@code andi.b #$F,d0 / bne.s loc_91648} before each release. */
    @Test
    void releaserFreesAnAnimalOnlyWhenTheVintLowNibbleIsZero() {
        EggRoboBadnikInstance releaser = onScreenReleaser();
        int animalsLeft = releaser.animalsLeftForTest();
        List<Integer> releaseTicks = new ArrayList<>();
        // Start mid-cycle with a non-zero high byte: only the low nibble of the low byte gates.
        for (int count = 0x1201; count <= 0x1250; count++) {
            releaser.update(count, null);
            if (releaser.animalsLeftForTest() != animalsLeft) {
                releaseTicks.add(count);
                animalsLeft = releaser.animalsLeftForTest();
            }
        }

        assertEquals(List.of(0x1210, 0x1220, 0x1230, 0x1240, 0x1250), releaseTicks,
                "each release waits for (V_int_run_count & $F) == 0");
        assertEquals("RISING", releaser.stateForTest(),
                "the release that takes $39 negative hands the object to loc_9164E");
    }

    /** {@code loc_9164E} and {@code loc_9167E} both open with {@code bsr.w sub_91988}. */
    @Test
    void launchedReleaserAlternatesFramesOneAndThreeOnVintBitZero() {
        EggRoboBadnikInstance robo = onScreenReleaser();
        int count = 0x1301;
        while (robo.stateForTest().equals("RELEASING")) {
            robo.update(count++, null);
        }
        int checked = 0;
        for (; robo.stateForTest().equals("RISING") || robo.stateForTest().equals("FALLING"); count++) {
            robo.update(count, null);
            assertEquals((count & 1) != 0 ? 3 : 1, robo.mappingFrame,
                    "sub_91988: frame 3 when bit 0 of (V_int_run_count+3) is set, else 1, at tick " + count);
            checked++;
        }
        assertTrue(checked > 8, "the rise and fall last more than a few frames");
    }

    /** {@code loc_9176C}: {@code moveq #0,d0 / btst #0,(V_int_run_count+3).w / beq / moveq #7,d0}. */
    @Test
    void shotChargeFlashShowsFrameSevenOnOddVintTicks() {
        HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        var shot = new EggRoboShotInstance(new ObjectSpawn(0x200, 0xC00, 0, 0, 0, false, 0), true);
        shot.setServices(TestEnvironment.objectServices());
        for (int count = 0x2201; count <= 0x2210; count++) {
            shot.update(count, null);
            assertEquals((count & 1) != 0 ? 7 : 0, shot.mappingFrameForTest(),
                    "charge flash frame at tick " + count);
        }
    }

    private static EggRoboBadnikInstance onScreenReleaser() {
        return onScreenReleaser(0);
    }

    private static EggRoboBadnikInstance onScreenReleaser(int flags) {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 0).build();
        fixture.stepIdleFrames(2);
        var services = TestEnvironment.objectServices();
        AbstractObjectInstance.updateCameraBounds(0x100, 0xB80, 0x240, 0xC60, 0x1000);
        // Object $A0, subtype 4: the low nibble selects the animal releaser (loc_918FC).
        var robo = new EggRoboBadnikInstance(new ObjectSpawn(0x200, 0xC00, 0xA0, 4, flags, false, 0));
        robo.setServices(services);
        services.objectManager().addDynamicObject(robo);
        releasePlaceholder(robo);
        return robo;
    }

    private static void releasePlaceholder(EggRoboBadnikInstance robo) {
        robo.update(1, null);
        robo.refreshPostCameraRenderState();
        robo.update(2, null);
    }
}
