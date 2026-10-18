package com.openggf.tests;

import com.openggf.LevelFrameResult;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** CNZ restore must rebuild scroll output without another native deform publication. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzParallaxRegisteredRewind {
    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
    }

    @Test
    void freshAndDispatchedWorldsRestoreAndReplayEveryRegisteredKeyTwice() throws Exception {
        TestEnvironment.configureGameModuleFixture(SonicGame.SONIC_3K);
        var configuration = GameServices.configuration();
        configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
        configuration.setSessionOverride(SonicConfiguration.SCREEN_HEIGHT_PIXELS, 224);
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 1)
                .startPosition((short) 0x48C0, (short) 0x0B40).startPositionIsCentre().build();
        var level = GameServices.level();
        var events = assertInstanceOf(Sonic3kLevelEventManager.class,
                GameServices.module().getLevelEventProvider()).getCnzEvents();
        assertNotNull(events);
        // Public handler initialization is not an ordinary CNZ1_Deform dispatch.
        GameServices.parallax().initZone(Sonic3kZoneIds.ZONE_CNZ, 1,
                fixture.camera().getX(), fixture.camera().getY());
        level.getObjectManager().setRewindInPlaceRestoreEnabledForTest(false);
        var registry = fixture.gameplayMode().getRewindRegistry();
        assertEquals(0, events.getDeformPhaseBgX());
        assertEquals(0, events.getPublishedBgCameraX());
        var fresh = registry.capture();
        assertWorld(fresh, registry.capture(), "fresh repeated capture");

        step(fixture);
        int dispatchedPhase = events.getDeformPhaseBgX();
        int dispatchedCopy = events.getPublishedBgCameraX();
        assertNotEquals(0, dispatchedPhase, "ordinary native deform published its phase");
        assertNotEquals(0, dispatchedCopy, "ordinary native deform published its BG copy");
        var dispatched = registry.capture();
        var dispatchedScroll = GameServices.parallax().getHScroll().clone();
        step(fixture);
        var forward = registry.capture();
        var forwardScroll = GameServices.parallax().getHScroll().clone();

        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(fresh);
            assertWorld(fresh, registry.capture(), "fresh restore " + cycle);
            assertEquals(0, events.getDeformPhaseBgX(), "restore cannot publish the first deform phase");
            assertEquals(0, events.getPublishedBgCameraX(), "restore cannot publish the first BG copy");
            step(fixture);
            assertWorld(dispatched, registry.capture(), "first dispatch replay " + cycle);
            assertArrayEquals(dispatchedScroll, GameServices.parallax().getHScroll());
            step(fixture);
            assertWorld(forward, registry.capture(), "second dispatch replay " + cycle);
            assertArrayEquals(forwardScroll, GameServices.parallax().getHScroll());

            registry.restore(dispatched);
            assertWorld(dispatched, registry.capture(), "dispatched restore " + cycle);
            assertEquals(dispatchedPhase, events.getDeformPhaseBgX());
            assertEquals(dispatchedCopy, events.getPublishedBgCameraX());
            assertArrayEquals(dispatchedScroll, GameServices.parallax().getHScroll(),
                    "derived scroll is rebuilt without republishing captured event inputs");
            step(fixture);
            assertWorld(forward, registry.capture(), "dispatched forward replay " + cycle);
            assertArrayEquals(forwardScroll, GameServices.parallax().getHScroll());
        }
    }

    private static void step(HeadlessTestFixture fixture) {
        int before = GameServices.level().getFrameCounter();
        assertEquals(LevelFrameResult.GAMEPLAY_FRAME,
                fixture.runner().stepFrame(false, false, false, false, false));
        assertEquals((before + 1) & 0xFFFF, GameServices.level().getFrameCounter() & 0xFFFF);
        assertFalse(fixture.sprite().getDead());
    }

    private static void assertWorld(CompositeSnapshot expected, CompositeSnapshot actual, String context) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet(), context + " all registered keys");
        for (String key : expected.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.keyEquals(key, expected.get(key), actual.get(key)),
                    () -> context + " " + RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key)));
        }
    }
}
