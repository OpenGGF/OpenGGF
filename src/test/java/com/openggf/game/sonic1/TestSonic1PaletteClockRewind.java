package com.openggf.game.sonic1;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.rewind.snapshot.PatternAnimatorSnapshot;
import com.openggf.level.Level;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@RequiresRom(SonicGame.SONIC_1)
class TestSonic1PaletteClockRewind {
    /** PalCycle_GHZ (_inc/PaletteCycle.asm:47-57) resets 5.
     * PalCycle_SLZ (_inc/PaletteCycle.asm:174-185) resets 7 and increments before use. */
    @ParameterizedTest @CsvSource({"0,5", "4,7"})
    void ordinaryPaletteTicksKeepTheirNativePeriod(int zone, int reset) throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, zone, 0);
        try {
            var palette = new Sonic1PaletteCycler(shared.level(), shared.level().getZoneIndex());
            palette.update();
            assertCycle(palette, 1, reset);
            for (int row = 0; row < reset; row++) palette.update();
            assertCycle(palette, 1, 0);
            palette.update();
            assertCycle(palette, 2, reset);
        } finally { shared.dispose(); }
    }

    @ParameterizedTest @CsvSource({"0", "4"})
    void normalLevelOwnerRestoresPaletteCountersAndColorsTogether(int zone) throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, zone, 0);
        try {
            var fixture = HeadlessTestFixture.builder().withSharedLevel(shared).build();
            var registry = fixture.gameplayMode().getRewindRegistry();
            var before = registry.capture();
            assertNotNull(((PatternAnimatorSnapshot) before.get("pattern-animator")).extra());
            fixture.stepIdleFrames(9);
            var after = registry.capture();
            assertFalse(RewindSnapshotDiff.diffKey("palette-colors", before.get("palette-colors"), after.get("palette-colors")).isEmpty());
            registry.restore(before);
            fixture.stepIdleFrames(9);
            assertSameState(after, registry.capture());
        } finally { shared.dispose(); }
    }

    @Test void registryAcceptsLegacyPatternOnlyExtraWithoutInventingPalettePhase() throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, 4, 0);
        try {
            var fixture = HeadlessTestFixture.builder().withSharedLevel(shared).build();
            fixture.stepIdleFrames(2);
            var registry = fixture.gameplayMode().getRewindRegistry();
            var legacy = captureLegacyPatternOnly(fixture);
            fixture.stepIdleFrames(3);
            var palette = (Sonic1PaletteCycler) GameServices.level().getAnimatedPaletteManager();
            byte[] live = palette.captureCyclerState();
            registry.restore(legacy);
            assertArrayEquals(live, palette.captureCyclerState(), "legacy snapshots carry no authoritative palette phase");
            palette.restoreCyclerState(new byte[]{1, 2, 3});
            assertArrayEquals(live, palette.captureCyclerState(), "incomplete private payload cannot partially restore cycles");
        } finally { shared.dispose(); }
    }

    @Test void registryRejectsUnscopedLegacyPayloadBeforeChangingPalettePhase() throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, 4, 0);
        try {
            var fixture = HeadlessTestFixture.builder().withSharedLevel(shared).build();
            fixture.stepIdleFrames(2);
            var legacy = captureLegacyPatternOnly(fixture);
            var unscoped = new CompositeSnapshot(new LinkedHashMap<>(legacy.entries()));
            fixture.stepIdleFrames(3);
            assertScopeRejectionPreservesPaletteAndWorld(fixture, unscoped);
        } finally { shared.dispose(); }
    }

    @Test void registryRejectsLegacyCaptureFromRetiredWorldBeforeChangingPalettePhase() throws Exception {
        CompositeSnapshot retired;
        Object retiredWorld;
        var previous = SharedLevel.load(SonicGame.SONIC_1, 4, 0);
        try {
            var fixture = HeadlessTestFixture.builder().withSharedLevel(previous).build();
            fixture.stepIdleFrames(2);
            retired = captureLegacyPatternOnly(fixture);
            retiredWorld = fixture.gameplayMode().getWorldSession();
        } finally { previous.dispose(); }

        var shared = SharedLevel.load(SonicGame.SONIC_1, 4, 0);
        try {
            var fixture = HeadlessTestFixture.builder().withSharedLevel(shared).build();
            assertNotSame(retiredWorld, fixture.gameplayMode().getWorldSession());
            fixture.stepIdleFrames(5);
            assertScopeRejectionPreservesPaletteAndWorld(fixture, retired);
        } finally { shared.dispose(); }
    }

    @Test void replacingTheLevelBindsOnlyItsOwnPaletteClock() throws Exception {
        var shared = SharedLevel.load(SonicGame.SONIC_1, 0, 0);
        try {
            var game = new Sonic1(TestEnvironment.currentRom());
            var oldPatterns = (Sonic1PatternAnimator) game.loadAnimatedPatternManager(shared.level(), 0);
            var oldPalette = (Sonic1PaletteCycler) game.loadAnimatedPaletteManager(shared.level(), 0);
            oldPalette.update();
            byte[] oldState = oldPatterns.capture().extra();
            var newLevel = mock(Level.class); // SLZ has no pattern handlers or pattern priming.
            var newPatterns = (Sonic1PatternAnimator) game.loadAnimatedPatternManager(newLevel, com.openggf.game.sonic1.constants.Sonic1Constants.ZONE_SLZ);
            assertNull(newPatterns.capture().extra(), "a new level cannot borrow the old palette owner");
            var newPalette = (Sonic1PaletteCycler) game.loadAnimatedPaletteManager(newLevel, com.openggf.game.sonic1.constants.Sonic1Constants.ZONE_SLZ);
            assertNotNull(newPatterns.capture().extra());
            assertArrayEquals(oldState, oldPatterns.capture().extra());
            assertCycle(newPalette, 0, 0);
        } finally { shared.dispose(); }
    }

    private static CompositeSnapshot captureLegacyPatternOnly(HeadlessTestFixture fixture) {
        var level = fixture.gameplayMode().getLevelManager();
        var patterns = (Sonic1PatternAnimator) level.getAnimatedPatternManager();
        var palette = (Sonic1PaletteCycler) level.getAnimatedPaletteManager();
        assertNotNull(palette);
        assertNotNull(patterns.capture().extra(), "the live fixture must begin with its palette owner attached");
        // Emit the genuine legacy payload through the registered owner while retaining this world's scope.
        // Reattach before any gameplay step or restore; no snapshot layout/admission is constructed here.
        patterns.attachPaletteCycler(null);
        try {
            var legacy = fixture.gameplayMode().getRewindRegistry().capture();
            assertNull(((PatternAnimatorSnapshot) legacy.get("pattern-animator")).extra());
            return legacy;
        } finally {
            patterns.attachPaletteCycler(palette);
        }
    }

    private static void assertScopeRejectionPreservesPaletteAndWorld(
            HeadlessTestFixture fixture, CompositeSnapshot rejected) {
        var registry = fixture.gameplayMode().getRewindRegistry();
        var palette = (Sonic1PaletteCycler) fixture.gameplayMode().getLevelManager().getAnimatedPaletteManager();
        var before = registry.capture();
        byte[] live = palette.captureCyclerState();
        var fullFailure = assertThrows(IllegalArgumentException.class, () -> registry.restore(rejected));
        assertEquals("Rewind snapshot belongs to a different world scope", fullFailure.getMessage());
        assertArrayEquals(live, palette.captureCyclerState());
        assertSameState(before, registry.capture());
        var courseFailure = assertThrows(IllegalArgumentException.class, () -> registry.restoreCourse(rejected));
        assertEquals("Rewind snapshot belongs to a different world scope", courseFailure.getMessage());
        assertArrayEquals(live, palette.captureCyclerState());
        assertSameState(before, registry.capture());
    }

    private static void assertCycle(Sonic1PaletteCycler palette, int frame, int timer) {
        var bytes = ByteBuffer.wrap(palette.captureCyclerState());
        assertEquals(0x53315031, bytes.getInt()); assertEquals(1, bytes.getInt());
        assertEquals(8, bytes.getInt()); assertEquals(frame, bytes.getInt()); assertEquals(timer, bytes.getInt());
        assertFalse(bytes.hasRemaining());
    }

    private static void assertSameState(CompositeSnapshot expected, CompositeSnapshot actual) {
        for (String key : expected.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, expected.get(key), actual.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }
}
