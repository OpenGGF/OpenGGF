package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionFieldPatterns {
    @Test void bothBanksSampleEveryCellAndOnlyAChangedPairRecomputes() {
        var residency = new Sonic2CompetitionArtResidency(TestEnvironment.currentRom(),
                new Sonic2PlcService(TestEnvironment.currentRom()));
        byte[] bytes = new byte[65536];
        new java.util.Random(0x8BCA5DCE).nextBytes(bytes);
        residency.writePatterns(0, bytes);
        var fields = new Sonic2CompetitionFieldPatterns(residency);
        var patterns = fields.live();
        for (int bank = 0; bank < 2; bank++) for (int cell = 0; cell < 1024; cell++)
            for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++)
                assertEquals(Im2FieldOracle.pixel(bytes, cell, x, y * 2 + bank),
                        patterns[(bank << 10) | cell].getPixel(x, y));
        // A sentinel detects rewriting an unchanged cell, rather than merely comparing equal content.
        patterns[7].setPixel(0, 0, (byte) 13);
        var replacement = new byte[32]; java.util.Arrays.fill(replacement, (byte) 0xAB);
        residency.writePatterns(18, replacement);
        fields.refresh();
        assertEquals(13, patterns[7].getPixel(0, 0));
        assertEquals(10, patterns[9].getPixel(0, 0));
        assertEquals(11, patterns[9].getPixel(1, 0));
        assertEquals(10, patterns[1033].getPixel(0, 0));
        assertSame(patterns, fields.live(), "level retains one live pattern table");
    }
}
