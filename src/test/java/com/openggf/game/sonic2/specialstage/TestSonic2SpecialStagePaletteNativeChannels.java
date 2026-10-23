package com.openggf.game.sonic2.specialstage;

import org.junit.jupiter.api.Test;
import com.openggf.data.Rom;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** Native CRAM channels survive until the renderer selects intensity/profile/output. */
class TestSonic2SpecialStagePaletteNativeChannels {
    @Test
    void preservesAll512NativeColoursAndIgnoresUnusedCramBits() {
        int[] canonical = {0, 36, 73, 109, 146, 182, 219, 255};
        for (int red = 0; red < 8; red++) {
            for (int green = 0; green < 8; green++) {
                for (int blue = 0; blue < 8; blue++) {
                    int word = (red << 1) | (green << 5) | (blue << 9);
                    for (int unused : new int[] {0, 0xF111}) {
                        var colour = Sonic2SpecialStagePalette.genesisColorToPaletteColor(word | unused);
                        assertEquals(canonical[red], colour.r & 255, "red native code " + red);
                        assertEquals(canonical[green], colour.g & 255, "green native code " + green);
                        assertEquals(canonical[blue], colour.b & 255, "blue native code " + blue);
                    }
                }
            }
        }
    }

    @Test
    void sessionRomOwnsStageAndEmeraldColoursWithoutGlobalCacheContamination() throws Exception {
        Rom first = paletteRom(0x000E, 0x00E0, 0x0E00);
        Rom second = paletteRom(0x0E00, 0x000E, 0x00E0);
        var firstLoader = new Sonic2SpecialStageDataLoader(first);
        var secondLoader = new Sonic2SpecialStageDataLoader(second);
        assertEquals(255, firstLoader.getPalettes(2)[1].getColor(7).r & 255);
        assertEquals(255, secondLoader.getPalettes(2)[1].getColor(7).b & 255);
        assertEquals(255, firstLoader.getPalettes(2)[3].getColor(7).g & 255);
        assertEquals(255, secondLoader.getPalettes(2)[3].getColor(7).r & 255);
        assertArrayEquals(new int[] {0x0E00, 0x0E00, 0x0E00}, firstLoader.getEmeraldColors(2));
        assertArrayEquals(new int[] {0x00E0, 0x00E0, 0x00E0}, secondLoader.getEmeraldColors(2));
        assertEquals(255, firstLoader.getPalettes(2)[1].getColor(7).r & 255);
        verify(first, times(3)).readBytes(0x3162L, 96);
        verify(second, times(2)).readBytes(0x3202L, 32);
    }

    @Test
    void absentOrShortSessionRomDataFailsInsteadOfSubstitutingGreyOrStaleColours() throws Exception {
        Rom unavailable = mock(Rom.class);
        when(unavailable.readBytes(anyLong(), anyInt())).thenThrow(new IOException("ROM unavailable"));
        var loader = new Sonic2SpecialStageDataLoader(unavailable);
        assertThrows(UncheckedIOException.class, () -> loader.getPalettes(0));
        assertThrows(UncheckedIOException.class, () -> loader.getEmeraldColors(0));
        Rom shortRom = mock(Rom.class);
        when(shortRom.readBytes(anyLong(), anyInt())).thenReturn(new byte[31]);
        assertThrows(UncheckedIOException.class,
                () -> new Sonic2SpecialStageDataLoader(shortRom).getPalettes(0));
    }

    private static Rom paletteRom(int main, int stage, int emerald) throws Exception {
        Rom rom = mock(Rom.class);
        when(rom.readBytes(0x3162L, 96)).thenReturn(words(48, main));
        when(rom.readBytes(0x3202L, 32)).thenReturn(words(16, stage));
        when(rom.readBytes(0x35F92L, 42)).thenReturn(words(21, emerald));
        return rom;
    }

    private static byte[] words(int count, int word) {
        byte[] result = new byte[count * 2];
        for (int i = 0; i < count; i++) {
            result[i * 2] = (byte) (word >>> 8);
            result[i * 2 + 1] = (byte) word;
        }
        return result;
    }
}
