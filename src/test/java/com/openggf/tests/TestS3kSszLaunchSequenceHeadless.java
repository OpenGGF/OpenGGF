package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.SszLaunchControllerObjectInstance;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Production ownership for {@code Obj_57E96}'s SSZ1 spiral-ramp exit. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSszLaunchSequenceHeadless {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @Test
    void resultsFlagRunsTheNativeJumpAndRequestsDeathEgg() {
        HeadlessTestFixture fixture = boot();
        GameServices.gameState().setEndOfLevelFlag(true);
        fixture.stepIdleFrames(2);
        SszLaunchControllerObjectInstance launch = active();
        assertNotNull(launch, "SSZ1_ScreenEvent allocates Obj_57E96");

        for (int frame = 0; frame < 0x600 && !launch.jumpingForTest(); frame++) {
            fixture.stepIdleFrames(1);
        }
        assertTrue(launch.jumpingForTest(), "sub_5806E reaches the scripted ramp jump");
        assertEquals(0x910, launch.rampCounterForTest(), "the jump fires at counter $910");
        assertEquals(0x400, fixture.sprite().getXSpeed(), "native jump x_vel");
        assertEquals((short) -0x680, fixture.sprite().getYSpeed(), "native jump y_vel");
        assertEquals(0x800, fixture.sprite().getGSpeed(), "native jump ground_vel");

        CompositeSnapshot before = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot after = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.gameplayMode().getRewindRegistry().restore(before);
        sameSnapshot(before, fixture.gameplayMode().getRewindRegistry().capture(), "launch restore");
        fixture.runner().primeInputState(
                new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(after, fixture.gameplayMode().getRewindRegistry().capture(), "launch replay");

        for (int frame = 0; frame < 0x200
                && GameServices.level().getCurrentZone() == Sonic3kZoneIds.ZONE_SSZ; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(Sonic3kZoneIds.ZONE_DEZ, GameServices.level().getCurrentZone(),
                "loc_581D2 requests StartNewLevel $B00");
        assertEquals(0, GameServices.level().getCurrentAct());
    }

    @Test
    void columnHandshakeQueuesAndPublishesTheDeathEggImage() {
        HeadlessTestFixture fixture = boot();
        GameServices.gameState().setEndOfLevelFlag(true);
        fixture.stepIdleFrames(2);
        SszZoneRuntimeState state = (SszZoneRuntimeState) GameServices.zoneRuntimeState();
        assertEquals(4, state.foregroundRoutine());

        for (int frame = 0; frame < 40; frame++) {
            GameServices.camera().setY((short) 0x5C0);
            GameServices.camera().setYCopy((short) 0x5C0);
            fixture.stepIdleFrames(1);
        }
        CompositeSnapshot crumbleBefore = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.stepIdleFrames(1);
        CompositeSnapshot crumbleAfter = fixture.gameplayMode().getRewindRegistry().capture();
        fixture.gameplayMode().getRewindRegistry().restore(crumbleBefore);
        sameSnapshot(crumbleBefore, fixture.gameplayMode().getRewindRegistry().capture(), "crumble restore");
        fixture.runner().primeInputState(new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, ""));
        fixture.stepIdleFrames(1);
        sameSnapshot(crumbleAfter, fixture.gameplayMode().getRewindRegistry().capture(), "crumble replay");

        for (int frame = 0; frame < 0x300 && !state.launchResourcesPublished(); frame++) {
            // The production handoff enters from the final arena at Camera_Y $5C0. This
            // isolated load still has the arrival controller, so retain the owning arena Y.
            GameServices.camera().setY((short) 0x5C0);
            GameServices.camera().setYCopy((short) 0x5C0);
            fixture.stepIdleFrames(1);
        }

        assertTrue(active().columnsFinishedForTest(), "all ten sub_5750C columns clamp");
        assertEquals(-0x40, active().columnOffsetForTest(0));
        assertEquals(0xFF, state.eventsFg4Low(), "the last clamp writes Events_fg_4+1");
        assertEquals(8, state.foregroundRoutine());
        assertTrue(state.launchResourcesQueued());
        assertTrue(state.launchResourcesPublished());
        assertEquals(-1, state.launchBlocksJobOrdinal());
        assertEquals(-1, state.launchChunksJobOrdinal());
        assertEquals(-1, state.launchCustomArtJobOrdinal());
        assertEquals(-1, state.launchRampArtJobOrdinal());
        assertNotNull(GameServices.level().getCurrentLevel().getPattern(0x073));
        assertNotNull(GameServices.level().getCurrentLevel().getPattern(0x348));
        var map = GameServices.level().getCurrentLevel().getMap();
        int patchX = map.getWidth() - 3;
        assertArrayEquals(new int[]{4, 5, 6}, new int[]{
                map.getValue(0, patchX, 0) & 0xFF,
                map.getValue(0, patchX + 1, 0) & 0xFF,
                map.getValue(0, patchX + 2, 0) & 0xFF});
        assertArrayEquals(new int[]{7, 8, 9}, new int[]{
                map.getValue(0, patchX, 2) & 0xFF,
                map.getValue(0, patchX + 1, 2) & 0xFF,
                map.getValue(0, patchX + 2, 2) & 0xFF});

        for (int frame = 0; frame < 0x400 && (GameServices.camera().getY() & 0xFFFF) != 0x110; frame++) {
            fixture.stepIdleFrames(1);
        }
        assertEquals(0x110, GameServices.camera().getY() & 0xFFFF,
                "loc_57F94 follows the spiral down to the Death Egg framing");
        int riseBefore = state.launchBackgroundRise();
        fixture.stepIdleFrames(2);
        assertEquals(riseBefore + 2 * 0x6000, state.launchBackgroundRise());
        assertEquals(GameServices.camera().getXCopy(), state.backgroundCameraX());
    }

    private static HeadlessTestFixture boot() {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        return HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_SSZ, 0)
                .withFreshLevelStartLifecycle()
                .startPosition((short) 0x1A40, (short) 0x660)
                .startPositionIsCentre()
                .build();
    }

    private static SszLaunchControllerObjectInstance active() {
        var manager = GameServices.level().getObjectManager();
        if (manager == null) return null;
        return manager.getActiveObjects().stream()
                .filter(SszLaunchControllerObjectInstance.class::isInstance)
                .map(SszLaunchControllerObjectInstance.class::cast)
                .filter(object -> !object.isDestroyed()).findFirst().orElse(null);
    }

    private static void sameSnapshot(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label);
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }
}
