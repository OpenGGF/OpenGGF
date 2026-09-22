package com.openggf.game.sonic3k.objects.bosses;

import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.level.objects.boss.AbstractBossChild;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.bosses.LrzMinibossInstance;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Positioned arena entry, preserving the actual boss/child graph through a whole-world restore. */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzBossRewindHeadless {
    @AfterEach
    void reset() {
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
    }

    @Test
    void unfoldedArmsKeepTheirStateAndIdentityAcrossRewind() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 0)
                .startPosition((short) 0x2C00, (short) 0x600).startPositionIsCentre().build();
        fixture.sprite().setRingCount(355);
        for (int i = 0; i < 400; i++) fixture.stepFrame(false, false, false, true, false);
        var boss = boss();
        assertEquals(24, boss.getChildComponents().size(), "both full arms must be alive");
        assertRoundTrip(fixture, LrzMinibossOrbiterChild.class);
    }

    @Test
    void hitFlashAndDefeatDebrisAreRecreatedFromTheirActualPhases() {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 0)
                .startPosition((short) 0x2C00, (short) 0x600).startPositionIsCentre().build();
        fixture.sprite().setRingCount(355);
        for (int i = 0; i < 400; i++) fixture.stepFrame(false, false, false, true, false);
        boolean checkedFlash = false;
        for (int i = 0; i < 1600 && !checkedFlash; i++) {
            for (var child : boss().getChildComponents()) {
                if (child instanceof LrzMinibossHandChild hand
                        && hand.isFiring() && hand.getCollisionFlags() != 0) {
                    hand.onPlayerAttack(null, null); // Exercise production touch, not route completion.
                }
            }
            fixture.stepFrame(false, false, false, false, false);
            if (hasChild(LrzMinibossHitSparkChild.class)) {
                assertRoundTrip(fixture, LrzMinibossHitSparkChild.class);
                checkedFlash = true;
            }
        }
        assertTrue(checkedFlash, "a firing hand must create its hit flash");
        for (int i = 0; i < 4608 && !boss().getState().defeated; i++) {
            if (boss().getCollisionFlags() == 6) boss().onPlayerAttack(null, null);
            fixture.sprite().setRingCount(355);
            fixture.stepFrame(false, false, false, false, false);
        }
        assertTrue(boss().getState().defeated, "six eligible drill hits");
        for (int i = 0; i < 1024 && !hasChild(LrzMinibossDebrisChild.class); i++) {
            fixture.stepFrame(false, false, false, false, false);
        }
        assertTrue(hasChild(LrzMinibossDebrisChild.class), "defeat must create debris");
        assertRoundTrip(fixture, LrzMinibossDebrisChild.class);
    }

    private static boolean hasChild(Class<?> type) {
        return GameServices.level().getObjectManager().getActiveObjects().stream().anyMatch(type::isInstance);
    }

    private static void assertRoundTrip(HeadlessTestFixture fixture, Class<?> childType) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        CompositeSnapshot before = registry.capture();
        fixture.stepFrame(false, false, false, false, false);
        CompositeSnapshot after = registry.capture();
        var oldChildren = GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(AbstractBossChild.class::isInstance).map(AbstractBossChild.class::cast).toList();
        assertTrue(oldChildren.stream().anyMatch(childType::isInstance));
        // Force reconstruction of the entire live child graph, including both mirrored arms.
        oldChildren.forEach(child -> child.setDestroyed(true));
        fixture.stepFrame(false, false, false, false, false);
        registry.restore(before);
        assertTrue(hasChild(childType));
        assertSameState(before, registry.capture(), "restore");
        fixture.stepFrame(false, false, false, false, false);
        assertSameState(after, registry.capture(), "forward replay");
    }

    private static LrzMinibossInstance boss() {
        return GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(LrzMinibossInstance.class::isInstance).map(LrzMinibossInstance.class::cast)
                .findFirst().orElseThrow();
    }

    private static void assertSameState(CompositeSnapshot expected, CompositeSnapshot actual, String label) {
        assertEquals(expected.entries().keySet(), actual.entries().keySet());
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> label + " " + key + ": " + diff);
        }
    }
}
