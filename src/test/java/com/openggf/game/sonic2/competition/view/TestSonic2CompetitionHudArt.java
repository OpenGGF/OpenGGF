package com.openggf.game.sonic2.competition.view;

import static org.junit.jupiter.api.Assertions.*;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionHudArt {
    @Test
    void initialBaseReplacesLifePatchColonOnlyAtPublicationAndRestoresPendingUpload() throws Exception {
        var rom = TestEnvironment.currentRom();
        var art = new Sonic2CompetitionArtResidency(rom, new Sonic2PlcService(rom));
        art.plcService().append(6); // Actual MilesLife2P cue, before Hud_Base.
        while (art.plcService().isBusy()) {
            art.prepareAfterLoop(PlcLifecyclePhase.LEVEL_TITLE_CARD);
            art.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        }
        byte[] lifePatchColon = art.patternBytes(0x6F6);
        assertFalse(java.util.Arrays.equals(lifePatchColon, rom.readBytes(0x4134C + 20 * 32, 32)),
                "the real life patch must discriminate the missing Hud_Base upload");
        var hud = new Sonic2CompetitionHudArt(rom, art);
        hud.initialize(3, 12);
        assertArrayEquals(lifePatchColon, art.patternBytes(0x6F6), "Art_Hud is queued, not prematurely resident");
        assertArrayEquals(new byte[32], art.patternBytes(0x7DD));
        assertArrayEquals(rom.readBytes(0x4164C + 3 * 32, 32), art.patternBytes(0x7DF));
        assertArrayEquals(rom.readBytes(0x4164C + 32, 32), art.patternBytes(0x6FD));
        assertArrayEquals(rom.readBytes(0x4164C + 2 * 32, 32), art.patternBytes(0x6FF));
        var pending = art.capture();
        art.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        for (int tile = 0; tile < 22; tile++)
            assertArrayEquals(rom.readBytes(0x4134C + tile * 32, 32), art.patternBytes(0x6E2 + tile));
        var expected = art.capture();
        art.resetForNativeEntry();
        art.restore(pending);
        art.serviceVBlank(PlcLifecyclePhase.LEVEL_TITLE_CARD);
        assertEquals(expected, art.capture());
        var patterns = art.fields().live();
        for (int y = 0; y < 8; y++)
            for (int x = 0; x < 8; x++)
                assertEquals(Im2FieldOracle.pixel(rom.readBytes(0x4134C, 22 * 32), 10, x, y * 2),
                        patterns[0x37B].getPixel(x, y), "HUD colon cell must hold Art_Hud, not MilesLife2P");
    }
}
