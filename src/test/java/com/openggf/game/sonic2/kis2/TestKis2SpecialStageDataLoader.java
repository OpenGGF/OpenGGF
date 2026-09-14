package com.openggf.game.sonic2.kis2;

import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageDataLoader;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@RequiresRom(SonicGame.SONIC_2)
class TestKis2SpecialStageDataLoader {
    private Kis2SpecialStageDataLoader loader;
    private Sonic2SpecialStageDataLoader stock;

    @BeforeEach
    void setup() throws Exception {
        RomByteReader dump = Kis2TestRoms.lockOnDumpOrNull();
        assumeTrue(dump != null, "KiS2 lock-on dump required");
        loader = new Kis2SpecialStageDataLoader(TestEnvironment.currentRom(),
                LockOnAddressSpace.tierTwo(null, null, dump));
        stock = new Sonic2SpecialStageDataLoader(TestEnvironment.currentRom());
    }

    @Test
    void allSevenStagesUseChipTargetsIncludingConfiguredTeams() throws Exception {
        int[][] expected = {{30,70,130,110},{50,90,130,130},{50,100,140,160},
                {40,90,140,150},{40,80,130,130},{70,130,170,170},{50,100,140,140}};
        for (int stage = 0; stage < 7; stage++) {
            for (int quarter = 0; quarter < 4; quarter++) {
                assertEquals(expected[stage][quarter], loader.getRingRequirement(stage, quarter, false));
                assertEquals(expected[stage][quarter], loader.getRingRequirement(stage, quarter, true));
            }
        }
        assertNotEquals(stock.getRingRequirement(6, 2, false), loader.getRingRequirement(6, 2, false));
    }

    @Test
    void everyKnucklesFrameUsesDecodedArtAndPreservesOptionalTailsSources() throws Exception {
        var art = loader.getPlayerArtPatterns();
        var stockArt = stock.getPlayerArtPatterns();
        var plans = loader.getPlayerDplcPlans();
        assertEquals(18, plans.sonic().size());
        assertEquals(stock.getPlayerDplcPlans().tails(), plans.tails());
        assertEquals(stock.getPlayerDplcPlans().tailsTails(), plans.tailsTails());
        assertEquals(26, loader.getMainPlayerFrame(0).pieces.length);
        assertEquals(16, plans.sonic().getFirst().requests().getFirst().count());
        assertEquals(0, plans.sonic().getFirst().requests().getFirst().startTile());
        for (int frame = 0; frame < 18; frame++) {
            for (var piece : loader.getMainPlayerFrame(frame).pieces) {
                assertTrue(piece.tileIndex >= stockArt.length);
                assertTrue(piece.tileIndex < art.length);
            }
            for (var request : plans.sonic().get(frame).requests()) {
                assertTrue(request.startTile() + request.count() <= art.length - stockArt.length);
            }
        }
    }

    @Test
    void resultsReadKnucklesMappingsAndLoadKFromRomLetterList() throws Exception {
        var title = loader.getResultsFrame(1);
        assertEquals(12, title.length); // GOT A (4) plus KNUCKLES (8)
        assertEquals(13, loader.getResultsFrame(28).length); // SUPER KNUCKLES
        var patterns = new com.openggf.level.Pattern[0x700];
        var rom = TestEnvironment.currentRom();
        var titleCard2 = com.openggf.util.PatternDecompressor.nemesis(rom,
                com.openggf.game.sonic2.constants.Sonic2Constants.ART_NEM_TITLE_CARD2_ADDR, "TitleCard2");
        loader.patchResultsPatterns(patterns, 2, titleCard2);
        assertSame(titleCard2[0x22], patterns[0x3E - 2]);
        for (int frame = 0; frame < 30; frame++) {
            assertTrue(loader.getResultsFrame(frame).length > 0);
        }
    }

    @Test
    void chipPresentationRemovesSonicNameAndMovesBombsAwayFromKnucklesPalette() {
        assertFalse(loader.showMainPlayerHudName());
        assertTrue(stock.showMainPlayerHudName());
        assertEquals(2, loader.getBombPaletteLine());
        assertEquals(1, stock.getBombPaletteLine());
        var palettes = loader.getPalettes(0);
        assertEquals(4, palettes.length);
        assertNotEquals(stock.getPalettes(0)[1].getColor(2).r, palettes[1].getColor(2).r);
    }
}
