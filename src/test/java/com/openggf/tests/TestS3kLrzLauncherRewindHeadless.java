package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.LrzSpikeBallLauncherObjectInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Real $1590,$97C placement and its allocated ball, including reconstruction after deletion. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzLauncherRewindHeadless {
    @AfterEach
    void reset() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void launchedBallIsRelinkedAndReplaysAfterRestore() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short) 0x1550, (short) 0x940).startPositionIsCentre().build();
        fixture.sprite().setRingCount(355);
        for (int frame = 0; frame < 400; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (launcher().ball() != null && launcher().ball().isInFlight()) break;
        }
        assertNotNull(launcher().ball());
        assertTrue(launcher().ball().isInFlight(), "the ROM animation command must have launched the ball");
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        CompositeSnapshot after = registry.capture();
        var oldBall = launcher().ball();
        oldBall.setDestroyed(true); // Deliberately force the recreation path, not in-place restore.
        fixture.stepFrame(false, false, false, false, false);
        registry.restore(before);
        assertNotSame(oldBall, launcher().ball());
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().contains(launcher().ball()),
                "the parent reference must resolve to the managed recreated ball");
        assertSameState(before, registry.capture(), "restore");
        fixture.stepFrame(false, false, false, false, false);
        assertSameState(after, registry.capture(), "forward replay");
    }

    private static LrzSpikeBallLauncherObjectInstance launcher() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(LrzSpikeBallLauncherObjectInstance.class::isInstance)
                .map(LrzSpikeBallLauncherObjectInstance.class::cast)
                .filter(l -> l.getX() == 0x1590).findFirst().orElseThrow();
    }

    private static void assertSameState(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }
}
