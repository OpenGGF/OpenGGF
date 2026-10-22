package com.openggf.game.sonic2;

import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Retail PalCycle dispatch aliases, checked against ROM words and native periods.
 * Addresses here come from PalCycle_MTZ (s2.asm:2763-2811),
 * PalCycle_CPZ (s2.asm:2951-2984), and PalCycle_WFZ (s2.asm:2998-3040),
 * independently of the production palette address constants.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestSonic2PaletteCycleAliases {
    private static final int PAL_CYCLE_TABLE = 0x19F4;
    private RomByteReader rom;

    @BeforeEach
    void setUp() throws IOException {
        GraphicsManager.getInstance().initHeadless();
        rom = RomByteReader.fromRom(TestEnvironment.currentRom());
    }

    @ParameterizedTest
    @EnumSource(Alias.class)
    void retailDispatchTableSelectsTheAliasedRoutine(Alias alias) {
        // PalCycle (s2.asm:2703-2720) uses signed offsets relative to the table.
        assertEquals(alias.routine, nativeRoutine(alias.romZone));
        assertEquals(alias.routine, nativeRoutine(alias.originalRomZone));
    }

    @ParameterizedTest
    @EnumSource(Alias.class)
    void everyAliasedStreamMatchesNativeColorsAndCadenceThroughItsWrap(Alias alias) throws IOException {
        TestCycler test = newTestCycler(alias);
        // Two full periods of the longest stream also wrap every shorter stream.
        for (int tick = 0; tick < alias.fullCycleTicks() * 2; tick++) {
            test.tick();
            assertNativeFrame(test, alias.channels, tick);
        }
    }

    @ParameterizedTest
    @EnumSource(Alias.class)
    void productionLevelLoadInstallsAliasedCycles(Alias alias) {
        HeadlessTestFixture.builder().withZoneAndAct(alias.logicalZone, alias.act).build();
        Level level = GameServices.level().getCurrentLevel();
        assertEquals(alias.romZone, level.getZoneIndex(), "Production load must retain the retail zone ID");
        Sonic2LevelAnimationManager manager = assertInstanceOf(Sonic2LevelAnimationManager.class,
                GameServices.level().getAnimatedPaletteManager());

        GameServices.paletteOwnershipRegistry().beginFrame();
        manager.update();

        assertNativeFrame(level, GameServices.paletteOwnershipRegistry(), alias.channels, 0);
    }

    @ParameterizedTest
    @EnumSource(Alias.class)
    void capturedTimersAndCountersReplayACompleteCycleForEveryStream(Alias alias) throws IOException {
        TestCycler test = newTestCycler(alias);
        int snapshotTick = 37; // Between native writes for at least one stream in each alias.
        for (int tick = 0; tick < snapshotTick; tick++) {
            test.tick();
        }
        byte[] saved = test.cycler.captureCyclerState();
        assertTrue(saved.length > 4, "Alias cycle state must be present");
        Palette[] savedColors = copyPalettes(test.level);
        List<byte[]> expectedStates = new ArrayList<>();
        List<byte[]> expectedColors = new ArrayList<>();
        for (int tick = snapshotTick; tick < snapshotTick + alias.fullCycleTicks(); tick++) {
            test.tick();
            assertNativeFrame(test, alias.channels, tick);
            expectedStates.add(test.cycler.captureCyclerState());
            expectedColors.add(rgbBytes(test.level));
        }

        test.cycler.restoreCyclerState(saved);
        restorePalettes(test.level, savedColors);
        assertArrayEquals(saved, test.cycler.captureCyclerState());
        for (int i = 0; i < expectedStates.size(); i++) {
            test.tick();
            assertNativeFrame(test, alias.channels, snapshotTick + i);
            assertArrayEquals(expectedStates.get(i), test.cycler.captureCyclerState(), "Cycle state at replay tick " + i);
            assertArrayEquals(expectedColors.get(i), rgbBytes(test.level), "Palette colors at replay tick " + i);
        }
    }

    @Test
    void skyChaseConveyorToggleSelectsTheBeltTableAndSixPassPeriod() throws IOException {
        GameServices.gameState().setWfzFireToggle(true);
        TestCycler test = newTestCycler(Alias.SCZ);
        Channel[] beltChannels = Alias.SCZ.channels.clone();
        beltChannels[0] = new Channel("s2.wfz.fireBeltCycle", 0x20C2, 2, 7, 4, 4, 8, 6);
        for (int tick = 0; tick < Alias.SCZ.fullCycleTicks() * 2; tick++) {
            test.tick();
            assertNativeFrame(test, beltChannels, tick);
        }
    }

    @ParameterizedTest
    @EnumSource(Alias.class)
    void primaryTimerWriteHoldsOnlyItsNativeStreamsAndSurvivesRestore(Alias alias) throws IOException {
        TestCycler test = newTestCycler(alias);
        test.tick();
        Palette[] initialColors = copyPalettes(test.level);
        test.cycler.setPrimaryTimer(0x7FFF);
        byte[] held = test.cycler.captureCyclerState();
        for (int tick = 1; tick <= 24; tick++) {
            test.tick();
            assertHeldFrame(test, alias, tick);
        }
        byte[] afterHold = test.cycler.captureCyclerState();
        byte[] colorsAfterHold = rgbBytes(test.level);
        test.cycler.restoreCyclerState(held);
        restorePalettes(test.level, initialColors);
        for (int tick = 1; tick <= 24; tick++) {
            test.tick();
            assertHeldFrame(test, alias, tick);
        }
        assertArrayEquals(afterHold, test.cycler.captureCyclerState());
        assertArrayEquals(colorsAfterHold, rgbBytes(test.level));

        // A native timer value of 2 skips two passes, then writes on underflow.
        test.cycler.setPrimaryTimer(2);
        for (int tick = 25; tick <= 26; tick++) {
            test.tick();
            assertHeldFrame(test, alias, tick);
        }
        test.tick();
        int count = alias == Alias.DEZ ? alias.channels.length : 1;
        for (int i = 0; i < count; i++) {
            Channel channel = alias.channels[i];
            assertChannelColors(test.level, channel, 1, "Timer resumed " + alias);
            assertChannelOwner(test.registry, channel, channel.owner);
        }
    }

    private void assertHeldFrame(TestCycler test, Alias alias, int tick) {
        for (int i = 0; i < alias.channels.length; i++) {
            Channel channel = alias.channels[i];
            if (alias == Alias.DEZ || i == 0) {
                assertChannelColors(test.level, channel, 0, "Held primary timer " + alias + " tick " + tick);
                assertChannelOwner(test.registry, channel, "none");
            } else {
                assertChannelColors(test.level, channel, tick / channel.period, "Independent timer " + alias);
                assertChannelOwner(test.registry, channel, tick % channel.period == 0 ? channel.owner : "none");
            }
        }
    }

    private int nativeRoutine(int zone) {
        return PAL_CYCLE_TABLE + (short) rom.readU16BE(PAL_CYCLE_TABLE + zone * 2);
    }

    private TestCycler newTestCycler(Alias alias) throws IOException {
        Level level = mock(Level.class);
        when(level.getPaletteCount()).thenReturn(4);
        for (int line = 0; line < 4; line++) {
            when(level.getPalette(line)).thenReturn(new Palette());
        }
        return new TestCycler(new Sonic2PaletteCycler(TestEnvironment.currentRom(), level, alias.romZone),
                level, GameServices.paletteOwnershipRegistry());
    }

    private void assertNativeFrame(TestCycler test, Channel[] channels, int tick) {
        assertNativeFrame(test.level, test.registry, channels, tick);
    }

    private void assertNativeFrame(Level level, PaletteOwnershipRegistry registry, Channel[] channels, int tick) {
        for (Channel channel : channels) {
            assertChannelColors(level, channel, tick / channel.period, "Native tick " + tick);
            assertChannelOwner(registry, channel, tick % channel.period == 0 ? channel.owner : "none");
        }
    }

    private void assertChannelColors(Level level, Channel channel, int writeIndex, String context) {
        int address = channel.table + writeIndex % channel.frames * channel.stride;
        for (int color = 0; color < channel.colors; color++) {
            int word = rom.readU16BE(address + color * 2);
            Palette.Color actual = level.getPalette(channel.line).getColor(channel.firstColor + color);
            assertEquals(((word >> 1 & 7) * 255 + 3) / 7, actual.r & 0xFF, context + " red " + channel.owner);
            assertEquals(((word >> 5 & 7) * 255 + 3) / 7, actual.g & 0xFF, context + " green " + channel.owner);
            assertEquals(((word >> 9 & 7) * 255 + 3) / 7, actual.b & 0xFF, context + " blue " + channel.owner);
        }
    }

    private static void assertChannelOwner(PaletteOwnershipRegistry registry, Channel channel, String owner) {
        for (int color = 0; color < channel.colors; color++) {
            assertEquals(owner, registry.ownerAt(PaletteSurface.NORMAL, channel.line, channel.firstColor + color),
                    "Submission cadence for " + channel.owner);
        }
    }

    private static Palette[] copyPalettes(Level level) {
        Palette[] copy = new Palette[4];
        for (int line = 0; line < 4; line++) {
            copy[line] = level.getPalette(line).deepCopy();
        }
        return copy;
    }

    private static void restorePalettes(Level level, Palette[] palettes) {
        for (int line = 0; line < 4; line++) {
            level.getPalette(line).mergeColorsFrom(palettes[line], 0, 15);
        }
    }

    private static byte[] rgbBytes(Level level) {
        byte[] rgb = new byte[4 * 16 * 3];
        int position = 0;
        for (int line = 0; line < 4; line++) {
            for (int color = 0; color < 16; color++) {
                Palette.Color value = level.getPalette(line).getColor(color);
                rgb[position++] = value.r;
                rgb[position++] = value.g;
                rgb[position++] = value.b;
            }
        }
        return rgb;
    }

    private record TestCycler(Sonic2PaletteCycler cycler, Level level, PaletteOwnershipRegistry registry) {
        void tick() {
            registry.beginFrame();
            cycler.update();
        }
    }

    private record Channel(String owner, int table, int line, int firstColor, int colors,
                           int frames, int stride, int period) {
    }

    private enum Alias {
        MTZ3(0x05, 0x04, 0x1A76, Sonic2ZoneConstants.ZONE_MTZ, 2, new Channel[]{
                new Channel("s2.mtz.cycle1", 0x1F2A, 2, 5, 1, 6, 2, 18),
                new Channel("s2.mtz.cycle2", 0x1F36, 2, 1, 3, 3, 2, 3),
                new Channel("s2.mtz.cycle3", 0x1F42, 2, 15, 1, 10, 2, 10)}),
        DEZ(0x0E, 0x0D, 0x1D16, Sonic2ZoneConstants.ZONE_DEZ, 0, new Channel[]{
                new Channel("s2.cpz.cycle1", 0x2022, 3, 12, 3, 9, 6, 8),
                new Channel("s2.cpz.cycle2", 0x2058, 3, 15, 1, 21, 2, 8),
                new Channel("s2.cpz.cycle3", 0x2082, 2, 15, 1, 16, 2, 8)}),
        SCZ(0x10, 0x06, 0x1DB6, Sonic2ZoneConstants.ZONE_SCZ, 0, new Channel[]{
                new Channel("s2.wfz.fireBeltCycle", 0x20A2, 2, 7, 4, 4, 8, 2),
                new Channel("s2.wfz.cycle1", 0x20E2, 2, 14, 1, 34, 2, 4),
                new Channel("s2.wfz.cycle2", 0x2126, 2, 15, 1, 12, 2, 6)});

        final int romZone;
        final int originalRomZone;
        final int routine;
        final int logicalZone;
        final int act;
        final Channel[] channels;

        Alias(int romZone, int originalRomZone, int routine, int logicalZone, int act, Channel[] channels) {
            this.romZone = romZone;
            this.originalRomZone = originalRomZone;
            this.routine = routine;
            this.logicalZone = logicalZone;
            this.act = act;
            this.channels = channels;
        }

        int fullCycleTicks() {
            int longest = 0;
            for (Channel channel : channels) {
                longest = Math.max(longest, channel.frames * channel.period);
            }
            return longest;
        }
    }
}
