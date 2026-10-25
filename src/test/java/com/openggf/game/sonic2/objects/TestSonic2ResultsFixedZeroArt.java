package com.openggf.game.sonic2.objects;

import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageResultsScreen;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.Pattern;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RequiresRom(SonicGame.SONIC_2)
@ExtendWith(SingletonResetExtension.class)
class TestSonic2ResultsFixedZeroArt {
    @Test
    void ordinaryResultsRetainTheNativeFixedHudZeroPixels() throws Exception {
        var rom = TestEnvironment.currentRom();
        var art = new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom)).loadForZone(0);

        assertZeroPair(art.resultsSheet().getPatterns(), 0x6F0 - Sonic2Constants.VRAM_BASE_NUMBERS);
    }

    @Test
    void specialResultsRetainTheNativeFixedHudZeroPixelsAfterDigitUpdates() throws Exception {
        RomManager romManager = mock(RomManager.class);
        when(romManager.isRomAvailable()).thenReturn(true);
        var services = new TestObjectServices().withRom(TestEnvironment.currentRom()).withRomManager(romManager);
        var results = new Sonic2SpecialStageResultsScreen(17, true, 0, 1, services);
        var load = Sonic2SpecialStageResultsScreen.class.getDeclaredMethod("loadArt");
        load.setAccessible(true);
        load.invoke(results);
        var field = Sonic2SpecialStageResultsScreen.class.getDeclaredField("combinedPatterns");
        field.setAccessible(true);
        var patterns = (Pattern[]) field.get(results);
        assertNotNull(patterns);
        assertZeroPair(patterns, 0x6F0 - 2);

        var update = Sonic2SpecialStageResultsScreen.class.getDeclaredMethod("updateBonusPatterns");
        update.setAccessible(true);
        update.invoke(results);
        assertZeroPair(patterns, 0x6F0 - 2);
    }

    private static void assertZeroPair(Pattern[] patterns, int index) throws Exception {
        byte[] nativeZero = TestEnvironment.currentRom().readBytes(Sonic2Constants.ART_UNC_HUD_NUMBERS_ADDR, 64);
        int visiblePixels = 0;
        for (int tile = 0; tile < 2; tile++) {
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int packed = nativeZero[tile * 32 + y * 4 + x / 2] & 0xFF;
                    int expected = (x & 1) == 0 ? packed >>> 4 : packed & 0xF;
                    assertEquals(expected, patterns[index + tile].getPixel(x, y) & 0xF,
                            "Hud_Base must copy the ROM's zero glyph at $6F0");
                    if (expected != 0) visiblePixels++;
                }
            }
        }
        assertTrue(visiblePixels > 0, "the native fixed zero must be visible rather than a blank pair");
    }
}
