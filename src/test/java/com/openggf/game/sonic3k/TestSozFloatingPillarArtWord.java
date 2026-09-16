package com.openggf.game.sonic3k;

import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestSozFloatingPillarArtWord {
    @Test
    void bothSpikeDirectionsResolveTheNativeArtWordCarry() {
        HeadlessTestFixture.builder().withZoneAndAct(8, 1).build();
        var provider = (Sonic3kObjectArtProvider) GameModuleRegistry.getCurrent().getObjectArtProvider();
        var sheet = provider.getSheet(Sonic3kObjectArtKeys.SOZ_FLOATING_PILLAR);
        assertNotNull(sheet);
        assertEquals(3, sheet.getFrameCount());
        assertEquals(0, sheet.getPaletteIndex(), "resolved palettes must not receive a second addition");
        for (int frame : new int[]{1, 2}) {
            assertEquals(20, sheet.getFrame(frame).pieces().size());
            for (int piece = 16; piece < 20; piece++) {
                var spike = sheet.getFrame(frame).pieces().get(piece);
                assertFalse(spike.priority(), "$C49B/$D49B + $4001 clears the priority bit");
                assertEquals(0, spike.paletteIndex());
                assertEquals(frame == 2, spike.vFlip());
                assertEquals(2, spike.widthTiles());
                assertEquals(4, spike.heightTiles());
                assertSame(GameServices.level().getCurrentLevel().getPattern(0x49C),
                        sheet.getPatterns()[spike.tileIndex()]);
            }
            var body = sheet.getFrame(frame).pieces().getFirst();
            assertEquals(2, body.paletteIndex());
            assertFalse(body.priority());
        }
    }
}
