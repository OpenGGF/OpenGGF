package com.openggf.game.sonic2.competition.view;

import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import com.openggf.data.RomByteReader;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import static org.mockito.Mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionFieldSprites {
    @Test void wholeMultiCellPiecesMatchNativeRowsAtOddEvenYAndEveryFlip() {
        byte[] source = new byte[65536]; new java.util.Random(8416).nextBytes(source);
        var art = new Sonic2CompetitionArtResidency(TestEnvironment.currentRom(), new Sonic2PlcService(TestEnvironment.currentRom()));
        art.writePatterns(0, source); var fields = art.fields().live();
        for (int nativeY : new int[]{-3, 0, 1, 6}) for (int flip = 0; flip < 4; flip++) {
            boolean h = (flip & 1) != 0, v = (flip & 2) != 0;
            int word = 19 | (h ? 0x800 : 0) | (v ? 0x1000 : 0);
            var entry = new Sonic2CompetitionSatBuilder.SatEntry(nativeY + 256, 9, 0, word, 128 + 31);
            var page = new Sonic2CompetitionSatBuilder.Page(0, List.of(entry), 1, List.of(), false);
            var projected = Sonic2CompetitionFieldSprites.project(page);
            assertEquals(1, projected.size()); assertEquals(6, projected.getFirst().fragments().size());
            for (var tile : projected.getFirst().fragments()) for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                int localX = tile.x() + x - 31;
                int localY = 2 * (tile.y() + y) - nativeY;
                if (h) localX = 23 - localX;
                if (v) localY = 31 - localY;
                int cell = (19 + localX / 8 * 2 + localY / 16) & 1023;
                int expected = Im2FieldOracle.pixel(source, cell, localX & 7, localY & 15);
                int actual = fields[tile.firstPatternIndex()].getPixel(h ? 7 - x : x, v ? 7 - y : y);
                assertEquals(expected, actual, "field sprite origin " + nativeY + " flip " + flip);
            }
        }
    }
    @Test void bodyUsesStoredAnimationFlipsNativeArtAndCentreWithoutAdvancingItsOwner() {
        var player = mock(AbstractPlayableSprite.class);
        when(player.shouldRefreshRenderFlagThisFrame()).thenReturn(true);
        when(player.getRenderHFlip()).thenReturn(true);
        when(player.getRenderVFlip()).thenReturn(true);
        when(player.getCentreX()).thenReturn((short)123);
        when(player.getCentreY()).thenReturn((short)456);
        when(player.getMappingFrame()).thenReturn(7);
        when(player.isHighPriority()).thenReturn(true);
        var body = Sonic2CompetitionPlayerDisplay.body(new RomByteReader(new byte[512]), player, 1);
        assertEquals(7, body.renderFlags());
        assertEquals(123, body.x()); assertEquals(456, body.y());
        assertEquals(7, body.mapping().frame()); assertEquals(0x83D0, body.artTile());
        verify(player, never()).setRenderFlips(anyBoolean(), anyBoolean());
        when(player.shouldRefreshRenderFlagThisFrame()).thenReturn(false);
        assertNull(Sonic2CompetitionPlayerDisplay.body(new RomByteReader(new byte[512]), player, 0));
    }
    @Test void inactiveDisplayPortDoesNotInferTeleportFromOtherState() {
        assertFalse(Sonic2CompetitionDisplayState.INACTIVE.teleportFlag());
        assertFalse(Sonic2CompetitionDisplayState.INACTIVE.gamePaused());
        assertEquals(0, Sonic2CompetitionDisplayState.INACTIVE.teleportTimer());
    }
}
