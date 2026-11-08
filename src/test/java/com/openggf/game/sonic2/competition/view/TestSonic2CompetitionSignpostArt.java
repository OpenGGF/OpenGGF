package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionSignpostArt {
    @Test void poleAndEveryFaceUseRomUploadsAtTheNextInterruptAndSurviveRewind() throws Exception {
        var rom = TestEnvironment.currentRom();
        var art = new Sonic2CompetitionArtResidency(rom, new Sonic2PlcService(rom));
        art.initializeSignpostPole();
        assertArrayEquals(new byte[32], art.patternBytes(0x7FC));
        var pending = art.capture();
        art.serviceDmaQueueVBlank();
        for (int tile = 0; tile < 2; tile++)
            assertArrayEquals(rom.readBytes(0x7A18A + (0x20 + tile) * 32, 32), art.patternBytes(0x7FC + tile));
        art.restore(pending);
        art.serviceDmaQueueVBlank();
        for (int frame = 0; frame < 6; frame++) {
            art.loadSignpostFrame(frame);
            var queued = art.capture();
            art.serviceDmaQueueVBlank();
            int descriptor = 0x196EE + (short) rom.read16BitAddr(0x196EE + frame * 2);
            int count = rom.read16BitAddr(descriptor);
            int destination = 0x5E8;
            for (int i = 0; i < count; i++) {
                int entry = rom.read16BitAddr(descriptor + 2 + i * 2);
                for (int tile = 0; tile <= (entry >>> 12); tile++)
                    assertArrayEquals(rom.readBytes(0x7A18A + ((entry & 0xFFF) + tile) * 32, 32),
                            art.patternBytes(destination++), "face " + frame + " tile " + tile);
            }
            var published = art.capture();
            art.restore(queued); art.serviceDmaQueueVBlank();
            assertEquals(published, art.capture());
        }
    }
}
