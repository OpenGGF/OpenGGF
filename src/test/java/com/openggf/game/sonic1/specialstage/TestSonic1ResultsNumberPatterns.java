package com.openggf.game.sonic1.specialstage;

import com.openggf.game.GameServices;
import com.openggf.game.sonic1.Sonic1ObjectArtProvider;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.objects.Sonic1ResultsScreenObjectInstance;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Decodes the production ROM art banks, then inspects the actual output glyphs. */
@RequiresRom(SonicGame.SONIC_1)
class TestSonic1ResultsNumberPatterns {
    private static final int SCORE_START =
            Sonic1Constants.VRAM_RESULTS_HUD_TEXT + 0x1A - Sonic1Constants.VRAM_RESULTS_BASE;
    private static final int FIXED_ZERO = 0x6F0 - Sonic1Constants.VRAM_RESULTS_BASE;

    @Test
    void ordinaryResultsUseSixScoreDigitsAndOneSharedFixedZero() throws Exception {
        GameServices.graphics().setHeadlessMode(true);
        Sonic1ObjectArtProvider provider = new Sonic1ObjectArtProvider();
        provider.loadArtForZone(0);
        ObjectSpriteSheet sheet = provider.getSheet(ObjectArtKeys.RESULTS);
        assertNotNull(sheet);
        assertEquals(FIXED_ZERO, sheet.getFrame(3).pieces().getLast().tileIndex(), "time bonus's last digit");
        assertEquals(FIXED_ZERO, sheet.getFrame(4).pieces().getLast().tileIndex(), "ring bonus's last digit");
        verifyNumberWriters(new Sonic1ResultsScreenObjectInstance(0, 0, 1),
                sheet.getPatterns(), provider.getHudDigitPatterns());
    }

    @Test
    void specialResultsUseTheSameDisplayedPointsAndFixedZeroContract() throws Exception {
        GameServices.graphics().setHeadlessMode(true);
        Sonic1SpecialStageResultsScreen screen = new Sonic1SpecialStageResultsScreen(0, false, 0, 0);
        Method loadArt = screen.getClass().getDeclaredMethod("loadArt");
        loadArt.setAccessible(true);
        loadArt.invoke(screen);
        verifyNumberWriters(screen, field(screen, "combinedPatterns"), field(screen, "sourceDigitPatterns"));
    }

    private void verifyNumberWriters(Object screen, Pattern[] patterns, Pattern[] digits) throws Exception {
        assertNotNull(patterns);
        assertNotNull(digits);
        assertColumns(patterns, FIXED_ZERO, "0", digits);
        Method score = screen.getClass().getDeclaredMethod("writeScoreValue", Pattern[].class, int.class, Pattern[].class);
        Method bonus = screen.getClass().getDeclaredMethod("writeBonusValue", Pattern[].class, int.class, int.class, Pattern[].class);
        score.setAccessible(true);
        bonus.setAccessible(true);

        // The seven displayed columns spell 10700, not 107000: six variable
        // ROM digits represent 1070, followed by the shared fixed zero.
        score.invoke(screen, patterns, 10_700, digits);
        assertColumns(patterns, SCORE_START, "  1070", digits);
        assertColumns(patterns, FIXED_ZERO, "0", digits);
        bonus.invoke(screen, patterns, 8, 1070, digits);
        assertColumns(patterns, 8, "1070", digits);
        assertColumns(patterns, FIXED_ZERO, "0", digits);

        score.invoke(screen, patterns, 0, digits);
        bonus.invoke(screen, patterns, 8, 0, digits);
        assertColumns(patterns, SCORE_START, "      ", digits);
        assertColumns(patterns, 8, "    ", digits);
        assertColumns(patterns, FIXED_ZERO, "0", digits);
    }

    private void assertColumns(Pattern[] output, int start, String columns, Pattern[] digits) {
        for (int column = 0; column < columns.length(); column++) {
            char digit = columns.charAt(column);
            for (int half = 0; half < 2; half++) {
                Pattern expected = digit == ' ' ? new Pattern() : digits[(digit - '0') * 2 + half];
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        assertEquals(expected.getPixel(x, y), output[start + column * 2 + half].getPixel(x, y),
                                "column " + column + " must show '" + digit + "'");
                    }
                }
            }
        }
    }

    private Pattern[] field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (Pattern[]) field.get(target);
    }
}
