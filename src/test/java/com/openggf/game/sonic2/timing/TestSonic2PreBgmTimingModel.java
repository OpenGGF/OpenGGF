package com.openggf.game.sonic2.timing;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2PreBgmTimingModel {
    @Test
    void derivesBranchExactEhzTitleCardWorkFromRetailRom() throws IOException {
        Sonic2PreBgmTimingModel.Evidence evidence = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), 0, 0, OptionalInt.empty(),
                Sonic2PreBgmTimingModel.TimingRegion.NTSC, false);

        assertEquals(new Sonic2PreBgmTimingModel.NemesisWork(
                        752, 6_016, true,
                        7, 22, 2, 246,
                        1_478, 66, 694, 53, 59,
                        430, 10_776, 247_232, 284_792, 43_622),
                evidence.titleCardVram());
        assertEquals(new Sonic2PreBgmTimingModel.NemesisWork(
                        736, 5_888, true,
                        6, 20, 1, 248,
                        1_873, 49, 901, 35, 43,
                        408, 10_496, 306_896, 284_484, 42_694),
                evidence.titleCardRam());
        assertEquals(586_852, evidence.titleCardVram().totalClocks());
        assertEquals(644_978, evidence.titleCardRam().totalClocks());
    }

    @Test
    void vramAndRamStreamsTakeDifferentRealDecoderBranches() throws IOException {
        Sonic2PreBgmTimingModel.Evidence evidence = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), 0, 0, OptionalInt.empty(),
                Sonic2PreBgmTimingModel.TimingRegion.NTSC, false);

        assertNotEquals(evidence.titleCardVram().tableSymbols(),
                evidence.titleCardRam().tableSymbols());
        assertNotEquals(evidence.titleCardVram().tableRefills(),
                evidence.titleCardRam().tableRefills());
        assertNotEquals(evidence.titleCardVram().decodeClocks(),
                evidence.titleCardRam().decodeClocks());
    }

    @Test
    void derivesLevelWorkAndTerminalBucketWithoutAFrameConstant() throws IOException {
        Sonic2PreBgmTimingModel.Evidence evidence = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), 0, 0, OptionalInt.empty(),
                Sonic2PreBgmTimingModel.TimingRegion.NTSC, false);

        assertEquals(new Sonic2PreBgmTimingModel.LevelEntryWork(
                        List.of(0x40, 0x1000, 0x1000),
                        161, 257, 0, 7, 224, 2, 10, 6, 2_730, 640,
                        Sonic2PreBgmTimingModel.WaterPath.NONE, 0),
                evidence.levelEntry());
        assertEquals(1_323_466, evidence.cpu().totalClocks());
        assertEquals(new Sonic2PreBgmTimingModel.CpuWork(
                        1_704, 8_686, 50, 9_722, 1_239_490, 1_088,
                        1_308, 148, 60_204, 74, 0, 246, 604, 142),
                evidence.cpu());
        assertEquals(11, evidence.terminalRowBucket());
        assertTrue(evidence.lowerRows() > 11.3 && evidence.lowerRows() < 11.4,
                () -> "unexpected lower bound " + evidence.lowerRows());
        assertTrue(evidence.upperRows() > 11.5 && evidence.upperRows() < 11.7,
                () -> "unexpected upper bound " + evidence.upperRows());
    }

    @Test
    void derivesRegionAndPreviousWaterLagBranchesAndFailsClosed() throws IOException {
        Sonic2PreBgmTimingModel.Evidence ntsc = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), 0, 0, OptionalInt.empty(),
                Sonic2PreBgmTimingModel.TimingRegion.NTSC, false);
        Sonic2PreBgmTimingModel.Evidence pal = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), 0, 0, OptionalInt.empty(),
                Sonic2PreBgmTimingModel.TimingRegion.PAL, false);
        Sonic2PreBgmTimingModel.Evidence priorWater = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), 0, 0, OptionalInt.empty(),
                Sonic2PreBgmTimingModel.TimingRegion.NTSC, true);

        assertTrue(pal.cpu().vintFade() > ntsc.cpu().vintFade());
        assertTrue(pal.cpu().pendingLagVint() > ntsc.cpu().pendingLagVint());
        assertTrue(priorWater.devices().pendingSpriteMaximumRows()
                < ntsc.devices().pendingSpriteMaximumRows());
        assertThrows(IllegalArgumentException.class,
                () -> Sonic2PreBgmTimingModel.TimingRegion.fromConfiguration("AUTO"));
    }

    @Test
    void derivesNonEhzDescriptorsAndLifeQueueWalksFromRetailRom() throws IOException {
        Sonic2PreBgmTimingModel.Evidence wfz = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), Sonic2ZoneConstants.ROM_ZONE_WFZ, 0,
                OptionalInt.empty(), Sonic2PreBgmTimingModel.TimingRegion.NTSC, false);
        Sonic2PreBgmTimingModel.Evidence tails = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), 0, 0, OptionalInt.of(9),
                Sonic2PreBgmTimingModel.TimingRegion.NTSC, false);

        assertTrue(wfz.levelEntry().titleDescriptorGroups() > 0);
        assertTrue(wfz.levelEntry().titleDescriptorOffset() > 0,
                "WFZ must follow its own Off_TitleCardLetters branch");
        assertEquals(224, wfz.levelEntry().titleVdpLongWrites());
        assertEquals(3, tails.levelEntry().plcCalls());
        assertTrue(tails.levelEntry().plcQueueSlotsWalked()
                > tails.levelEntry().plcEntries());
    }

    @Test
    void derivesEverySupportedWaterArmFromRomStateAndPaletteLength() throws IOException {
        assertWaterPath(Sonic2ZoneConstants.ROM_ZONE_CPZ, 1,
                Sonic2PreBgmTimingModel.WaterPath.CPZ, 1_312);
        assertWaterPath(Sonic2ZoneConstants.ROM_ZONE_ARZ, 0,
                Sonic2PreBgmTimingModel.WaterPath.ARZ, 1_338);
        assertWaterPath(Sonic2ZoneConstants.ROM_ZONE_HPZ, 0,
                Sonic2PreBgmTimingModel.WaterPath.HPZ, 1_330);
    }

    private static void assertWaterPath(int zoneId, int actId,
                                        Sonic2PreBgmTimingModel.WaterPath expectedPath,
                                        int expectedConditionalClocks) throws IOException {
        Sonic2PreBgmTimingModel.Evidence evidence = Sonic2PreBgmTimingModel.analyze(
                TestEnvironment.currentRom(), zoneId, actId, OptionalInt.empty(),
                Sonic2PreBgmTimingModel.TimingRegion.NTSC, false);

        assertEquals(expectedPath, evidence.levelEntry().waterPath());
        assertEquals(32, evidence.levelEntry().underwaterPaletteLongwords());
        assertEquals(expectedConditionalClocks, evidence.cpu().waterConditional());
        assertEquals(11, evidence.terminalRowBucket());
    }
}
