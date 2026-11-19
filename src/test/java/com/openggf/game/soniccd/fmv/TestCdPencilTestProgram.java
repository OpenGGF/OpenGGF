package com.openggf.game.soniccd.fmv;

import com.openggf.game.soniccd.CdDiscDataSource;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdTestMedia;
import com.openggf.game.soniccd.media.CdHostedMedia;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.program.CdProgramScreenRenderer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class TestCdPencilTestProgram {
    @Test
    void publishesAllDiscBanksAtTheMuteStreamCadenceAndDrainsTheLastPicture() throws Exception {
        try (var source = (CdDiscDataSource) new CdHostedMedia().openMedia(CdTestMedia.usaCue())) {
            var disc = source.assets();
            assertEquals(8_601_600, disc.entry(CdNativeFile.PENCILSTM).size());
            var program = new CdPencilTestProgram(disc, null);
            int pictures = 0;
            int previous = 0;
            int tick = 0;
            while (!program.done() && tick < 3500) {
                tick++;
                // Start is a native button, distinct from OpenGGF's Back.
                program.frame(0x80);
                var picture = program.screen().frame();
                if (picture.dirtyTiles().isEmpty()) continue;
                if (pictures == 0) assertEquals(26, tick, "seek, five sectors and presentation");
                else assertTrue(tick - previous == 3 || tick - previous == 4, "five sectors at 75 Hz");
                if (pictures == 0 || pictures == 1 || pictures == 839) {
                    assertDiscPicture(disc, picture, pictures);
                }
                picture.dirtyTiles().clear();
                previous = tick;
                pictures++;
            }
            assertTrue(program.done(), "EOF returns within the playback budget");
            assertEquals(840, pictures);
            assertEquals(61, tick - previous, "the last picture stays visible before returning");
            program.frame(0);
            assertTrue(program.done());
        }
    }

    private static void assertDiscPicture(CdProgramAssets disc, CdProgramScreenRenderer.Frame picture, int bank)
            throws Exception {
        byte[] bytes = disc.read(CdNativeFile.PENCILSTM, bank * 10_240L, 10_240);
        assertEquals(0x31, bytes[0]);
        assertEquals(0x35, bytes[1]);
        assertTrue(picture.display());
        for (int color = 0; color < 16; color++) {
            int offset = 0x10 + color * 2;
            int expected = ((bytes[offset] & 0xff) << 8 | (bytes[offset + 1] & 0xff)) & 0x0eee;
            assertEquals(expected, picture.palette()[color]);
        }
        int[] cells = picture.planeA();
        for (int row = 0; row < 14; row++) {
            int tile = cells[(7 + row) * 64 + 10];
            assertArrayEquals(Arrays.copyOfRange(bytes, 0x30 + row * 21 * 32, 0x30 + (row + 1) * 21 * 32),
                    Arrays.copyOfRange(picture.tiles(), tile * 32, (tile + 21) * 32));
            for (int column = 0; column < 21; column++) {
                assertEquals(tile + column, cells[(7 + row) * 64 + 10 + column]);
            }
        }
    }
}
