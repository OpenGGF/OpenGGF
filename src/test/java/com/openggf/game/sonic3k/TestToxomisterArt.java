package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestToxomisterArt {
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void bothActsLoadTheBodyStemAndAllSixCloudFrames(int act) {
        HeadlessTestFixture.builder().withZoneAndAct(9, act).build();
        var provider = (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
        var sheet = provider.getSheet(Sonic3kObjectArtKeys.TOXOMISTER);
        assertNotNull(sheet);
        assertEquals(1, sheet.getPaletteIndex());
        assertEquals(8, sheet.getFrameCount());
        assertEquals(4, sheet.getFrame(0).pieces().size());
        int[] tile = {0, 6, 7, 8, 12, 16, 20};
        for (int frame = 1; frame < 8; frame++) {
            assertEquals(1, sheet.getFrame(frame).pieces().size());
            var piece = sheet.getFrame(frame).pieces().getFirst();
            assertEquals(tile[frame - 1], piece.tileIndex());
        }
    }
}
