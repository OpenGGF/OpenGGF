package com.openggf.game.sonic3k;

import com.openggf.game.sonic3k.objects.LrzPostDefeatCameraReleaseInstance;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.Sonic3kPaletteCycler;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.Palette;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestLrzPostBossPalette {
    @Test void thirteenRomRowsHoldTheirNativeDurationsAndReleaseTheSharedClock() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1).build();
        fixture.stepIdleFrames(2);
        var rom = GameServices.rom().getRom();
        var level = GameServices.level().getCurrentLevel();
        var state = S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var ownership = TestEnvironment.objectServices().paletteOwnershipRegistryOrNull();
        var cycler = new Sonic3kPaletteCycler(RomByteReader.fromRom(rom), level, 9, 1, ownership, null);
        var waiter = waiter();
        GameServices.camera().setX((short) 0);
        GameServices.gameState().setEndOfLevelFlag(true);
        // word_78EAA has thirteen rows, four-byte header, five words + delay per row.
        int data = rom.read32BitAddr(0x78EAE) + 4;
        // palscriptdata stores frames-1; decrement-before-negative gives exactly frames ticks.
        int[] starts = {0, 4, 8, 12, 16, 32, 36, 40, 44, 48, 52, 56, 60};
        int row = 0;
        for (int tick = 0; tick < 110; tick++) {
            ownership.beginFrame();
            waiter.update(tick, null);
            cycler.update();
            assertEquals(0x7FFF - tick, primaryTimer(cycler),
                    "native object write follows this frame's AnPal decrement");
            if (row + 1 < starts.length && tick == starts[row + 1]) row++;
            assertColors(level.getPalette(2), 1, rom.readBytes(data + row * 12, 10));
            assertFalse(waiter.isDestroyed(), "callback must not release camera early");
        }
        GameServices.camera().setX((short) 0x940);
        ownership.beginFrame();
        waiter.update(110, null);
        assertTrue(waiter.isDestroyed());
        cycler.update();
        assertEquals(0, primaryTimer(cycler), "release is stored after the old clock's tick");
        assertColors(level.getPalette(2), 1, rom.readBytes(data + 12 * 12, 10));
        ownership.beginFrame();
        cycler.update();
        assertColors(level.getPalette(2), 1, rom.readBytes(0x327E + 8, 8));
        assertEquals(-1, state.consumePrimaryPaletteTimerWrite(), "cycler consumes the release write");
    }

    @Test void rotationPauseAndFullRegistryRestoreRetainTheExactScriptCursor() throws Exception {
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1).build();
        GameServices.camera().setX((short) 0);
        GameServices.camera().setScrollLocked(true);
        // Let production publish scroll/power-up state before capturing this setup.
        fixture.stepIdleFrames(2);
        var waiter = waiter();
        GameServices.gameState().setEndOfLevelFlag(true);
        for (int n = 0; n < 17; n++) waiter.update(n, null);
        var registry = fixture.gameplayMode().getRewindRegistry();
        var saved = registry.capture();
        for (int n = 17; n < 67; n++) waiter.update(n, null);
        var expected = registry.capture();
        registry.restore(saved);
        assertSameState(saved, registry.capture());
        waiter = GameServices.level().getObjectManager()
                .activeObjectsOfType(LrzPostDefeatCameraReleaseInstance.class).stream()
                .filter(o -> o.gate() == LrzPostDefeatCameraReleaseInstance.Gate.WAITER).findFirst().orElseThrow();
        for (int n = 17; n < 67; n++) waiter.update(n, null);
        assertSameState(expected, registry.capture());
        var ownership = TestEnvironment.objectServices().paletteOwnershipRegistryOrNull();
        ownership.setPaletteRotationDisabled(true);
        var paused = registry.capture();
        for (int n = 0; n < 100; n++) waiter.update(n, null);
        assertSameState(paused, registry.capture());
        ownership.setPaletteRotationDisabled(false);
        waiter.update(100, null);
        assertFalse(RewindSnapshotDiff.diffKey("object-manager", paused.get("object-manager"),
                registry.capture().get("object-manager")).isEmpty(), "resuming advances the byte delay");
    }

    @Test void fadeOnlyPalettePassConsumesObjectWritesWithoutAdvancingClocks() throws Exception {
        HeadlessTestFixture.builder().withZoneAndAct(9, 1).build();
        var level = GameServices.level().getCurrentLevel();
        var state = S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow();
        var cycler = new Sonic3kPaletteCycler(RomByteReader.fromRom(GameServices.rom().getRom()), level, 9, 1);
        state.writePrimaryPaletteTimer(0x7FFF);
        cycler.update(false);
        assertEquals(0x7FFF, primaryTimer(cycler));
        assertEquals(-1, state.consumePrimaryPaletteTimerWrite());
        state.writePrimaryPaletteTimer(0);
        cycler.update(false);
        assertEquals(0, primaryTimer(cycler));
        cycler.update();
        assertEquals(15, primaryTimer(cycler), "the first resumed AnPal pass sees the release");
    }

    private static LrzPostDefeatCameraReleaseInstance waiter() {
        var waiter = new LrzPostDefeatCameraReleaseInstance(LrzPostDefeatCameraReleaseInstance.Gate.WAITER);
        waiter.setServices(TestEnvironment.objectServices());
        GameServices.level().getObjectManager().addDynamicObject(waiter);
        return waiter;
    }

    private static void assertSameState(com.openggf.game.rewind.CompositeSnapshot a,
                                        com.openggf.game.rewind.CompositeSnapshot b) {
        assertEquals(a.entries().keySet(), b.entries().keySet());
        for (String key : a.entries().keySet()) {
            var diff = RewindSnapshotDiff.diffKey(key, a.get(key), b.get(key));
            assertTrue(diff.isEmpty(), () -> key + ": " + diff);
        }
    }

    private static int primaryTimer(Sonic3kPaletteCycler cycler) throws Exception {
        var cycles = Sonic3kPaletteCycler.class.getDeclaredField("cycles");
        cycles.setAccessible(true);
        var cycle = ((java.util.List<?>) cycles.get(cycler)).getFirst();
        var timer = cycle.getClass().getDeclaredField("timerAB");
        timer.setAccessible(true);
        return timer.getInt(cycle);
    }

    private static void assertColors(Palette palette, int first, byte[] data) {
        for (int i = 0; i < data.length / 2; i++) {
            var expected = new Palette.Color();
            expected.fromSegaFormat(data, i * 2);
            assertEquals(rgb(expected), rgb(palette.getColor(first + i)), "color " + (first + i));
        }
    }
    private static int rgb(Palette.Color c) { return (c.r & 255) << 16 | (c.g & 255) << 8 | (c.b & 255); }
}
