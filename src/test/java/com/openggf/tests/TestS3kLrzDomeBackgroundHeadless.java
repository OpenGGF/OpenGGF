package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.events.LrzBackgroundStageMachine;
import com.openggf.game.sonic3k.events.Sonic3kLRZEvents;
import com.openggf.game.sonic3k.objects.LrzDomeLavaPlatformObjectInstance;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Lava Reef act 1 dome background on a real level load: {@code LRZ1_BackgroundInit}'s
 * Knuckles-only chunk (sonic3k.asm:115239-115242) and the {@code loc_56E40} lock reached from a
 * live Player 1 position.
 *
 * <p>{@code LRZ1_BackgroundInit} is called with {@code a3} on the <b>background</b> layout row
 * table -- its first act is to copy row 0's pointer over rows 7 to 31, which is what makes the
 * crystal wall repeat -- so {@code movea.w 4(a3),a1} is background row 1 and
 * {@code move.b #-$A,4(a1)} writes chunk {@code $F6} into its column 4. It happens only for
 * {@code Player_mode} 3.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzDomeBackgroundHeadless {

    private static final int BG_LAYER = 1;
    private static final int KNUCKLES_CHUNK_ROW = 1;
    private static final int KNUCKLES_CHUNK_COLUMN = 4;
    private static final int KNUCKLES_CHUNK_ID = 0xF6;

    /** Region 0 of {@code word_56F88}: X {@code $1AC0}-{@code $1B40}, Y {@code $840}-{@code $8C0). */
    private static final int INSIDE_REGION_X = 0x1B10;
    private static final int INSIDE_REGION_Y = 0x0880;

    @AfterEach
    void cleanup() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        SonicConfigurationService.getInstance()
                .setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void knucklesGetsTheBackgroundChunkAndSonicDoesNot() {
        character("sonic", "tails");
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        events().update(0, 1);
        int placed = block(map());
        assertNotEquals(KNUCKLES_CHUNK_ID, placed,
                "the placed layout must not already hold $F6, or this test proves nothing");

        character("knuckles", "");
        HeadlessTestFixture.builder().withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0).build();
        events().update(0, 1);
        assertEquals(KNUCKLES_CHUNK_ID, block(map()),
                "move.b #-$A,4(a1) on background row 1, column 4");
    }

    /**
     * Entering region 0 past {@code $1B00} locks the background, allocates {@code Obj_56EA0} and
     * steps {@code Events_routine_bg} to 4, and the whole composite survives a capture / diverge /
     * restore / replay round trip with the platform in it.
     */
    @Test
    void lockingInsideARegionRewinds() {
        character("sonic", "tails");
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_LRZ, 0)
                .startPosition((short) INSIDE_REGION_X, (short) INSIDE_REGION_Y)
                .startPositionIsCentre()
                .build();
        fixture.stepFrame(false, false, false, false, false);

        LrzZoneRuntimeState lrz = state();
        assertTrue(lrz.domeRegionLocked(), "st (Events_bg+$00).w at loc_56E40");
        assertEquals(LrzBackgroundStageMachine.BG_STAGE_LOCKED, lrz.backgroundRoutine());
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                        .anyMatch(o -> o instanceof LrzDomeLavaPlatformObjectInstance),
                "AllocateObject -> Obj_56EA0");

        rewindSpot(fixture, "locked dome region");
    }

    // ===== harness =====

    /** Capture, diverge one frame, restore, compare; then replay that frame and compare. */
    private static void rewindSpot(HeadlessTestFixture fixture, String label) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        CompositeSnapshot after = registry.capture();
        assertDiffers(before, after, label);

        registry.restore(before);
        assertSame(before, registry.capture(), label + " restore");
        fixture.stepFrame(false, false, false, false, false);
        assertSame(after, registry.capture(), label + " forward replay");
    }

    private static void assertDiffers(CompositeSnapshot a, CompositeSnapshot b, String label) {
        for (String key : a.entries().keySet()) {
            if (!RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty()) {
                return;
            }
        }
        throw new AssertionError(label + ": the diverging frame changed nothing at all");
    }

    private static void assertSame(CompositeSnapshot a, CompositeSnapshot b, String label) {
        assertEquals(a.entries().keySet(), b.entries().keySet(), label + " snapshot keys");
        for (String key : a.entries().keySet()) {
            assertTrue(RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)).isEmpty(),
                    () -> label + " " + key + ": "
                            + RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key)));
        }
    }

    private static void character(String main, String sidekick) {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, main);
        configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick);
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    private static int block(Map map) {
        return map.getValue(BG_LAYER, KNUCKLES_CHUNK_COLUMN, KNUCKLES_CHUNK_ROW) & 0xFF;
    }

    private static Map map() {
        LevelManager manager = GameServices.levelOrNull();
        Level level = manager != null ? manager.getCurrentLevel() : null;
        return level != null ? level.getMap() : null;
    }

    private static LrzZoneRuntimeState state() {
        return S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
    }

    private static Sonic3kLRZEvents events() {
        Sonic3kLevelEventManager manager =
                (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();
        return manager.getLrzEventsForTest();
    }
}
