package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SszCraneShip;
import com.openggf.game.sonic3k.objects.bosses.SszMechaSonicObjectInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Cold Knuckles arrival through the placed crane's player-control handoff; no state injection. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszCraneRouteHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest @ValueSource(ints = {320, 800})
    void placedCraneGrabsCarriesAndReleasesKnucklesIntoTheMechaFight(int width) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT,
                (width == 320 ? WidescreenAspect.NATIVE_4_3 : WidescreenAspect.SUPER_32_9).name());
        config.resolveDisplayAspect();
        config.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, width);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(10, 1)
                .withFreshLevelStartLifecycle().build();
        var state = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        boolean sawShip = false;
        boolean sawGrab = false;
        boolean sawPan = false;
        boolean released = false;
        com.openggf.game.rewind.CompositeSnapshot carrying = null;
        var registry = TestEnvironment.activeGameplayMode().getRewindRegistry();
        int capturedFrame = -1;
        int releasedFrame = -1;
        for (int frame = 0; frame < 1600; frame++) {
            fixture.stepIdleFrames(1);
            sawShip |= !GameServices.level().getObjectManager().activeObjectsOfType(SszCraneShip.class).isEmpty();
            if (state.cutsceneFlag(2)) sawGrab = true;
            // loc_7B484 reuses/clears bit 4 on Mecha's landing. It is a
            // consumed signal, not a permanent "pan complete" status bit.
            sawPan |= state.cutsceneFlag(4);
            if (sawGrab && carrying == null) {
                var objects = GameServices.level().getObjectManager();
                assertEquals(1, objects.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.SszCraneClaw.class).size());
                assertEquals(2, objects.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.SszCraneClawPart.class).size());
                assertFalse(objects.activeObjectsOfType(
                        com.openggf.game.sonic3k.objects.SszCraneShipDecoration.class).isEmpty());
                carrying = registry.capture();
                fixture.stepIdleFrames(45);
                var expected = registry.capture();
                // Force recreation of the parent and children, not merely scalar restoration
                // into surviving instances. The whole registry owns queue/palette state too.
                objects.getActiveObjects().stream().filter(o -> o instanceof SszCraneShip
                        || o instanceof com.openggf.game.sonic3k.objects.SszCraneClaw
                        || o instanceof com.openggf.game.sonic3k.objects.SszCraneClawPart
                        || o instanceof com.openggf.game.sonic3k.objects.SszCraneShipDecoration)
                        .forEach(o -> ((com.openggf.level.objects.AbstractObjectInstance) o).setDestroyed(true));
                fixture.stepIdleFrames(1);
                registry.restore(carrying);
                same(carrying, registry.capture());
                fixture.stepIdleFrames(45);
                same(expected, registry.capture());
                registry.restore(carrying);
                capturedFrame = frame;
            }
            if (sawGrab && state.cutsceneFlag(5) && sawPan
                    && !fixture.sprite().isObjectControlled()) {
                released = true;
                releasedFrame = frame;
                break;
            }
            assertFalse(fixture.sprite().getDead(), "arrival/crane sequence must not kill Knuckles");
        }
        assertTrue(sawShip, "the native $B2 placement must instantiate the ship");
        assertTrue(sawGrab, "claw must reach Knuckles and set bit 2");
        assertTrue(released, () -> "release/pan incomplete: flags=" + state.cutsceneFlags()
                + " camera=" + fixture.camera().getX() + "," + fixture.camera().getY()
                + " player=" + fixture.sprite().getCentreX() + "," + fixture.sprite().getCentreY()
                + " controlled=" + fixture.sprite().isObjectControlled());
        assertTrue(sawPan, "camera pan must finish independently of the player-release helper");
        assertEquals(0x100 - (width - 320) / 2, fixture.camera().getX());
        assertEquals(0x100, fixture.camera().getMinX());
        assertEquals(0x100, fixture.camera().getMaxX());
        assertEquals(0x220, fixture.sprite().getCentreX());
        assertEquals(0x4AC, fixture.sprite().getCentreY());
        assertEquals(1, GameServices.level().getObjectManager()
                .activeObjectsOfType(SszMechaSonicObjectInstance.class).size());
        byte[] releasedState = state.captureBytes();
        var releasedSnapshot = registry.capture();
        assertNotNull(carrying);
        registry.restore(carrying);
        same(carrying, registry.capture());
        fixture.stepIdleFrames(releasedFrame - capturedFrame);
        same(releasedSnapshot, registry.capture());
        var replayed = TestEnvironment.objectServices().spriteManager().getMainPlayable();
        assertFalse(replayed.isObjectControlled());
        assertFalse(replayed.getDead());
        assertEquals(0x220, replayed.getCentreX());
        assertEquals(0x4AC, replayed.getCentreY());
        assertEquals(0x100 - (width - 320) / 2, fixture.camera().getX());
        assertEquals(0x100, fixture.camera().getMinX());
        assertEquals(0x100, fixture.camera().getMaxX());
        assertArrayEquals(releasedState, ((SszZoneRuntimeState) GameServices.zoneRuntimeState()).captureBytes());
        assertEquals(1, GameServices.level().getObjectManager()
                .activeObjectsOfType(SszMechaSonicObjectInstance.class).size());
    }
    private static void same(com.openggf.game.rewind.CompositeSnapshot expected,
                             com.openggf.game.rewind.CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var differences = com.openggf.game.rewind.RewindSnapshotDiff.diffKey(
                    key, expected.get(key), actual.get(key));
            assertTrue(differences.isEmpty(), () -> key + ": " + differences);
        }
    }
}
