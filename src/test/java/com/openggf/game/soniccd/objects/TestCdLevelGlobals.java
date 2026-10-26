package com.openggf.game.soniccd.objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Authored before CdLevelGlobals; every case is UNCOMPILED and UNRUN by the external author.
 * Widths are the native declarations at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/_Include/Main CPU Variables.inc (MAINVARS = WORKRAM+$F00): levelRestart rs.w (:51, $FF1502),
 * timeOver rs.b (:59, $FF150C), updateHUDTime rs.b (:63, $FF1510), plcLoadFlags rs.b (:71, $FF151C),
 * spawnMode rs.b (:77, $FF1522), powerup rs.b (:120, $FF156B), checkpoint rs.b (:141, $FF158E),
 * flowerCount rs.b 3 (:149, $FF1900) and fadeEnableDisplay rs.b (:150, $FF1903).
 * The record has no default, cold or reset factory; values come only from explicit ingress or a capture.
 */
class TestCdLevelGlobals {
    @ParameterizedTest(name = "{0} holds 0..{1}")
    @CsvSource({"levelRestart, 0xFFFF", "updateHudTime, 0xFF", "checkpoint, 0xFF", "spawnMode, 0xFF",
            "plcLoadFlags, 0xFF", "flowerCount, 0xFFFFFF", "fadeEnableDisplay, 0xFF", "powerup, 0xFF", "timeOver, 0xFF"})
    void eachFieldHoldsExactlyItsNativeWidth(String field, String max) {
        int limit = Integer.decode(max);
        assertEquals(limit, read(only(field, limit), field));
        assertEquals(0, read(only(field, 0), field));
        assertThrows(IllegalArgumentException.class, () -> only(field, limit + 1));
        assertThrows(IllegalArgumentException.class, () -> only(field, -1));
        assertThrows(IllegalArgumentException.class, () -> only(field, Integer.MIN_VALUE));
    }

    @Test void flowerCountIsThreeBytesAndTheAdjacentFadeByteIsItsOwnField() {
        // CLR.L flowerCount (Sonic/Main.asm:2466) spans both declarations. Here they stay two native
        // fields, so a fourth count byte can never alias fadeEnableDisplay.
        var full = new CdLevelGlobals(0, 0, 0, 0, 0, 0xFFFFFF, 0xFF, 0, 0);
        assertEquals(0xFFFFFF, full.flowerCount());
        assertEquals(0xFF, full.fadeEnableDisplay());
        assertThrows(IllegalArgumentException.class, () -> new CdLevelGlobals(0, 0, 0, 0, 0, 0x1000000, 0, 0, 0));
    }

    @Test void positionalIngressKeepsEveryDeclaredFieldDistinct() {
        // Callers supply nine positional literals; distinct values expose any transposed component.
        var g = new CdLevelGlobals(0x0102, 0x03, 0x04, 0x05, 0x06, 0x070809, 0x0A, 0x0B, 0x0C);
        assertEquals(0x0102, g.levelRestart());
        assertEquals(0x03, g.updateHudTime());
        assertEquals(0x04, g.checkpoint());
        assertEquals(0x05, g.spawnMode());
        assertEquals(0x06, g.plcLoadFlags());
        assertEquals(0x070809, g.flowerCount());
        assertEquals(0x0A, g.fadeEnableDisplay());
        assertEquals(0x0B, g.powerup());
        assertEquals(0x0C, g.timeOver());
        assertEquals(new CdLevelGlobals(0x0102, 0x03, 0x04, 0x05, 0x06, 0x070809, 0x0A, 0x0B, 0x0C), g);
        assertNotEquals(new CdLevelGlobals(0x0102, 0x03, 0x04, 0x05, 0x06, 0x070809, 0x0A, 0x0B, 0x0D), g);
    }

    /** Every other field is zero, so a rejection can come only from the named field. */
    private static CdLevelGlobals only(String field, int value) {
        return switch (field) {
            case "levelRestart" -> new CdLevelGlobals(value, 0, 0, 0, 0, 0, 0, 0, 0);
            case "updateHudTime" -> new CdLevelGlobals(0, value, 0, 0, 0, 0, 0, 0, 0);
            case "checkpoint" -> new CdLevelGlobals(0, 0, value, 0, 0, 0, 0, 0, 0);
            case "spawnMode" -> new CdLevelGlobals(0, 0, 0, value, 0, 0, 0, 0, 0);
            case "plcLoadFlags" -> new CdLevelGlobals(0, 0, 0, 0, value, 0, 0, 0, 0);
            case "flowerCount" -> new CdLevelGlobals(0, 0, 0, 0, 0, value, 0, 0, 0);
            case "fadeEnableDisplay" -> new CdLevelGlobals(0, 0, 0, 0, 0, 0, value, 0, 0);
            case "powerup" -> new CdLevelGlobals(0, 0, 0, 0, 0, 0, 0, value, 0);
            case "timeOver" -> new CdLevelGlobals(0, 0, 0, 0, 0, 0, 0, 0, value);
            default -> throw new AssertionError("Unknown field " + field);
        };
    }

    private static int read(CdLevelGlobals g, String field) {
        return switch (field) {
            case "levelRestart" -> g.levelRestart();
            case "updateHudTime" -> g.updateHudTime();
            case "checkpoint" -> g.checkpoint();
            case "spawnMode" -> g.spawnMode();
            case "plcLoadFlags" -> g.plcLoadFlags();
            case "flowerCount" -> g.flowerCount();
            case "fadeEnableDisplay" -> g.fadeEnableDisplay();
            case "powerup" -> g.powerup();
            case "timeOver" -> g.timeOver();
            default -> throw new AssertionError("Unknown field " + field);
        };
    }
}
