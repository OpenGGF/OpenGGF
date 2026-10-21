package com.openggf.game.sonic3k.specialstage;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.LevelBackdropResultsScreen;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The locked-on {@code SpecialStage_Results} (game mode $48, sonic3k.asm:63015) copies the
 * S&amp;K half's {@code Pal_Results} (sonic3k.asm:63155/63128 -> 63279), whose colour 0 is
 * {@code $CCC} on every line; it never rewrites VDP register 7, so the card's backdrop is
 * that grey. Native frames of the Tails complete run (Game_mode $48, won and failed stages)
 * show (204,204,204). The S3-half copy (s3.asm:53952) has a {@code $EEE} colour 0.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSpecialStageResultsBackdrop {
    /** {@code lea Pal_Results(pc),a0} in SpecialStage_Results (sonic3k.asm:63155). */
    private static final int LEA_PAL_RESULTS = 0x2E0FA;

    @Test
    void resultsPaletteIsTheOneLockedOnSpecialStageResultsLoads() throws Exception {
        Rom rom = GameServices.rom().getRom();
        byte[] lea = rom.readBytes(LEA_PAL_RESULTS, 4);
        assertEquals(0x41FA, ((lea[0] & 0xFF) << 8) | (lea[1] & 0xFF), "lea d16(pc),a0");
        int target = LEA_PAL_RESULTS + 2 + (short) (((lea[2] & 0xFF) << 8) | (lea[3] & 0xFF));
        assertEquals(target, Sonic3kConstants.PAL_RESULTS_ADDR);
    }

    @Test
    void backdropIsResultsPaletteColourZero() throws Exception {
        Rom rom = GameServices.rom().getRom();
        GameServices.gameState().resetSession();
        var screen = new S3kSpecialStageResultsScreen(0, false, 1, 0, 0, PlayerCharacter.TAILS_ALONE);
        var colour = LevelBackdropResultsScreen.resultsBackdropOr(screen, null);
        assertNotNull(colour, "results screen names no backdrop");
        byte[] word = rom.readBytes(0x2E318, 2); // Pal_Results colour 0 (sonic3k.asm:63319)
        assertEquals(0x0CCC, ((word[0] & 0xFF) << 8) | (word[1] & 0xFF));
        var expected = com.openggf.data.PaletteLoader.fromBytes(rom.readBytes(0x2E318, 128))[0].getColor(0);
        assertEquals(expected.r, colour.r);
        assertEquals(expected.g, colour.g);
        assertEquals(expected.b, colour.b);
    }
}
