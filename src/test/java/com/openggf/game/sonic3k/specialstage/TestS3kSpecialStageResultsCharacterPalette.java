package com.openggf.game.sonic3k.specialstage;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Palette;
import com.openggf.level.PaletteLoader;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code sub_2E2C0} (sonic3k.asm:63282-63311) runs after every {@code Pal_Results} copy, not
 * only on a Super Emerald stage: with Player_mode 3 it recolours line 1 colours 2-5 so the
 * Knuckles continue icon ({@code Map_Results} frame $2B) is red rather than Sonic blue.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStageResultsCharacterPalette {

    @Test
    void romSubroutineHoldsTheKnucklesColours() throws Exception {
        Rom rom = GameServices.rom().getRom();
        // sub_2E2C0 sits directly before Pal_Results; both move.l immediates live there.
        int start = Sonic3kConstants.PAL_RESULTS_ADDR - 0x60;
        byte[] code = rom.readBytes(start, 0x60);
        assertTrue(contains(code, new byte[] {0x20, 0x3C, 0x08, 0x4E, 0x04, 0x0C}),
                "move.l #$84E040C,d0 (sonic3k.asm:63286)");
        assertTrue(contains(code, new byte[] {0x20, 0x3C, 0x02, 0x06, 0x00, (byte) 0x80}),
                "move.l #$2060080,d0 (sonic3k.asm:63289)");
    }

    @Test
    void knucklesChaosStageResultsRecolourLineOne() throws Exception {
        Rom rom = GameServices.rom().getRom();
        GameServices.gameState().resetSession();
        var screen = new S3kSpecialStageResultsScreen(50, true, 0, 0, 1, PlayerCharacter.KNUCKLES);
        Palette line = screen.resultsPaletteLineForTest(0);
        assertNotNull(line, "results palette not loaded");
        byte[] expectedBytes = rom.readBytes(Sonic3kConstants.PAL_RESULTS_ADDR, 0x20);
        int[] words = {0x084E, 0x040C, 0x0206, 0x0080};
        for (int i = 0; i < words.length; i++) {
            expectedBytes[4 + i * 2] = (byte) (words[i] >> 8);
            expectedBytes[5 + i * 2] = (byte) words[i];
        }
        Palette expected = PaletteLoader.fromBytes(expectedBytes)[0];
        for (int c = 0; c < 16; c++) {
            assertEquals(expected.getColor(c).r, line.getColor(c).r, "colour " + c + " r");
            assertEquals(expected.getColor(c).g, line.getColor(c).g, "colour " + c + " g");
            assertEquals(expected.getColor(c).b, line.getColor(c).b, "colour " + c + " b");
        }
    }

    @Test
    void otherCharactersKeepPalResults() throws Exception {
        Rom rom = GameServices.rom().getRom();
        Palette stock = PaletteLoader.fromBytes(rom.readBytes(Sonic3kConstants.PAL_RESULTS_ADDR, 0x20))[0];
        for (PlayerCharacter character : new PlayerCharacter[] {
                PlayerCharacter.SONIC_AND_TAILS, PlayerCharacter.SONIC_ALONE, PlayerCharacter.TAILS_ALONE}) {
            Palette line = stock.deepCopy();
            S3kSpecialStageResultsScreen.applyChaosStageCharacterColors(line, character);
            assertTrue(stock.dataEquals(line), character.name());
        }
        Palette knuckles = stock.deepCopy();
        S3kSpecialStageResultsScreen.applyChaosStageCharacterColors(knuckles, PlayerCharacter.KNUCKLES);
        assertTrue(!stock.dataEquals(knuckles), "Knuckles palette must differ from stock Pal_Results");
        for (int c = 0; c < 16; c++) {
            if (c >= 2 && c <= 5) continue;
            assertEquals(stock.getColor(c).r, knuckles.getColor(c).r, "only colours 2-5 change: " + c);
            assertEquals(stock.getColor(c).g, knuckles.getColor(c).g, "only colours 2-5 change: " + c);
            assertEquals(stock.getColor(c).b, knuckles.getColor(c).b, "only colours 2-5 change: " + c);
        }
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
