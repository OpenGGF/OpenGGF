package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.OscillationManager;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Short production-frame witnesses, independent of whole boss routes. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kActTransitionFrameOwnership {
    @ParameterizedTest
    @ValueSource(ints = {3, 6, 7}) // CNZ/LBZ own an oscillator dispatch; MHZ queues a frame-top reload.
    void nextFrameAfterBoundaryReloadReplaysFromTheCapturedWorld(int zone) throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(zone, 0).build();
        var level = GameServices.level();
        level.applySeamlessTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(zone, 1).build());
        var registry = fixture.gameplayMode().getRewindRegistry();
        var before = registry.capture();
        int[] oscillatorBefore = OscillationManager.valuesForTest();

        fixture.stepIdleFrames(1);
        int[] expectedOscillator = OscillationManager.valuesForTest();
        var expected = registry.capture();
        assertFalse(java.util.Arrays.equals(oscillatorBefore, expectedOscillator),
                "the next real gameplay row must advance the ordinary loop tail");
        assertFalse(level.consumeActTransitionRewindBoundaryDuringFrame(),
                "the boundary row must not publish a second boundary into this gameplay row");

        for (int cycle = 0; cycle < 2; cycle++) {
            registry.restore(before);
            fixture.stepIdleFrames(1);
            assertArrayEquals(expectedOscillator, OscillationManager.valuesForTest());
            var replayed = registry.capture();
            for (var key : java.util.List.of("object-manager", "sprites", "level")) {
                assertNotNull(expected.get(key), "missing captured owner " + key);
                assertNotNull(replayed.get(key), "missing replayed owner " + key);
                var differences = RewindSnapshotDiff.diffKey(key, expected.get(key), replayed.get(key));
                assertTrue(differences.isEmpty(), key + ": " + differences);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 6}) // AIZ/HCZ ordinary tail; CNZ/LBZ transition-owned tail.
    void directInFrameReloadPublishesItsOwnMarkersExactlyOnce(int zone) throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(zone, 0).build();
        var level = GameServices.level();
        level.executeActTransition(SeamlessLevelTransitionRequest.builder(
                        SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                .targetZoneAct(zone, 1).build());

        assertTrue(level.consumeActTransitionExecutedDuringFrame());
        assertTrue(level.consumeActTransitionRewindBoundaryDuringFrame());
        assertEquals(zone == 3 || zone == 6,
                level.consumeActTransitionOscillationAdvancedDuringFrame());
        assertFalse(level.consumeActTransitionExecutedDuringFrame());
        assertFalse(level.consumeActTransitionRewindBoundaryDuringFrame());
        assertFalse(level.consumeActTransitionOscillationAdvancedDuringFrame());
    }
}
