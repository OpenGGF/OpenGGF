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
        // Progressive output retains both 8-row halves of each native 8x16 cell.
        // loc_410BC (s2.asm:88195-88200) still publishes exactly the same 22 patterns.
        var patterns = art.fields().live();
        for (int y = 0; y < 16; y++)
            for (int x = 0; x < 8; x++)
                assertEquals(Im2FieldOracle.pixel(rom.readBytes(0x4134C, 22 * 32), 10, x, y),
                        patterns[0x6F6 + y / 8].getPixel(x, y % 8), "HUD colon cell must hold Art_Hud, not MilesLife2P");
    }
    @Test void livesFlagsRewriteOnlyTheChangedPlayersPatternsAndPauseRetainsTheRequest() throws Exception {
        var rom=TestEnvironment.currentRom();var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
        var hud=new Sonic2CompetitionHudArt(rom,art);hud.initialize(3,12);art.serviceDmaQueueVBlank();
        var original=art.capture();var state=hud.capture();
        hud.update(3,2,true);assertEquals(state,hud.capture());assertEquals(original,art.capture());
        hud.update(3,2,false);var changed=art.capture();
        for(int slot=0;slot<2048;slot++) {
            if(slot==0x6FD || slot==0x6FF)assertNotSame(original.slots()[slot],changed.slots()[slot]);
            else assertSame(original.slots()[slot],changed.slots()[slot],"only P2 lives writes at slot "+slot);
        }
        assertArrayEquals(new byte[32],art.patternBytes(0x6FD));
        assertArrayEquals(rom.readBytes(0x4164C+2*32,32),art.patternBytes(0x6FF));
        var next=art.capture();hud.update(13,2,false);
        assertArrayEquals(rom.readBytes(0x4164C+32,32),art.patternBytes(0x7DD));
        assertArrayEquals(rom.readBytes(0x4164C+3*32,32),art.patternBytes(0x7DF));
        for(int tile=0;tile<22;tile++)assertSame(next.slots()[0x6E2+tile],art.capture().slots()[0x6E2+tile],
                "2P HUD selects the shared number alphabet; it never rewrites per-player ring/time art");
        art.restore(original);hud.restore(state);hud.update(3,12,false);assertEquals(original,art.capture());
    }

}
