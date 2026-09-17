package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.unpackBG;
import static com.openggf.level.scroll.M68KMath.unpackFG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Lava Reef background scroll parity against {@code LRZ1_Deform} (sonic3k.asm:115389-115435,
 * reached from {@code LRZ1_BackgroundInit}/{@code LRZ1_BackgroundEvent}) and {@code sub_57082}
 * (115739-115820, reached from {@code LRZ2_BackgroundInit}/{@code LRZ2_BackgroundEvent}).
 *
 * <p>Both routines build the same two fractions from {@code Camera_X_pos_copy} held as a 16.16
 * long: {@code b = X/8} after {@code asr.l #3} and {@code s = b/4} after a further
 * {@code asr.l #2}. They then scatter {@code b + n*s} across {@code HScroll_table}, once
 * descending with {@code move.w d1,-(a1)} (eight words) and once ascending with
 * {@code move.w d2,(a1)+} at twice the step (five words). The acts differ only in where those
 * runs start, in the vertical ratio, and in the band table {@code ApplyDeformation} consumes:
 *
 * <ul>
 *   <li>Act 1 writes {@code HScroll_table+$004 = b}, {@code +$01A} down to {@code +$00C} =
 *       {@code b, b+s ... b+7s} and {@code +$01C} up to {@code +$024} =
 *       {@code b+s, b+3s, b+5s, b+7s, b+9s}; {@code Camera_Y_pos_BG_copy} is
 *       {@code ((Camera_Y_pos_copy - shake) asr 3) + shake}; {@code ApplyDeformation} is entered
 *       with {@code a5 = HScroll_table+$00C} and {@code LRZ1_BGDeformArray}.</li>
 *   <li>Act 2 writes {@code +$00E} down to {@code +$000} = {@code b, b+s ... b+7s} (so
 *       {@code +$004} carries {@code b+5s}) and {@code +$010} up to {@code +$018} =
 *       {@code b+s ... b+9s}; {@code Camera_Y_pos_BG_copy} is {@code (Y/8 - Y/32) + shake},
 *       three sixteenths of a half, with {@code Y = Camera_Y_pos_copy - shake};
 *       {@code ApplyDeformation} is entered with {@code a5 = HScroll_table} and
 *       {@code LRZ2_BGDeformArray}.</li>
 * </ul>
 *
 * <p>Band heights come straight from the ROM arrays at sonic3k.asm:115643 and 115845.
 */
class SwScrlLrzTest {

    @BeforeEach
    void clearRuntimeState() {
        SessionManager.clear();
    }

    @Test
    void act1WritesTheDescendingAndAscendingHScrollRuns() {
        int cameraX = 0x1234;
        short[] table = new SwScrlLrz().buildHScrollTableForTest(0, cameraX);
        short b = (short) (cameraX >> 3);
        int step = (cameraX << 16) >> 5;

        assertEquals(b, table[2], "HScroll_table+$004 = Camera_X_pos_BG_copy = b");
        for (int i = 0; i < 8; i++) {
            // move.w d1,-(a1) from HScroll_table+$01C: $01A, $018 ... $00C
            assertEquals(wordOfFraction(cameraX, i, step), table[13 - i],
                    "descending run word " + (13 - i));
        }
        for (int i = 0; i < 5; i++) {
            // move.w d2,(a1)+ from HScroll_table+$01C with d2 = b+s and the step doubled
            assertEquals(wordOfFraction(cameraX, 1 + 2 * i, step), table[14 + i],
                    "ascending run word " + (14 + i));
        }
    }

    @Test
    void act2WritesTheSameRunsFourWordsLower() {
        int cameraX = 0x1234;
        short[] table = new SwScrlLrz().buildHScrollTableForTest(1, cameraX);
        int step = (cameraX << 16) >> 5;

        for (int i = 0; i < 8; i++) {
            // move.w d1,-(a1) from HScroll_table+$010: $00E, $00C ... $000
            assertEquals(wordOfFraction(cameraX, i, step), table[7 - i],
                    "descending run word " + (7 - i));
        }
        assertEquals(wordOfFraction(cameraX, 5, step), table[2],
                "act 2 never writes HScroll_table+$004 directly; the descending run leaves b+5s there");
        for (int i = 0; i < 5; i++) {
            assertEquals(wordOfFraction(cameraX, 1 + 2 * i, step), table[8 + i],
                    "ascending run word " + (8 + i));
        }
    }

    @Test
    void act1BackgroundYIsAnEighthOfTheCameraAndActTwoIsThreeThirtySeconds() {
        SwScrlLrz handler = new SwScrlLrz();
        int[] buffer = new int[VISIBLE_LINES];

        handler.update(buffer, 0x0800, 0x0640, 0, 0);
        // ((Camera_Y_pos_copy - shake) asr 3) + shake with no shake: $640 >> 3
        assertEquals((short) 0xC8, handler.getVscrollFactorBG(), "act 1 Camera_Y_pos_BG_copy");

        handler.update(buffer, 0x0800, 0x0640, 0, 1);
        // Y/8 - Y/32 in 16.16, i.e. 3Y/32: $640 * 3 / 32
        assertEquals((short) 0x96, handler.getVscrollFactorBG(), "act 2 Camera_Y_pos_BG_copy");
    }

    /**
     * {@code ApplyDeformation} with {@code a5 = HScroll_table+$00C} and
     * {@code LRZ1_BGDeformArray}. Hand-walked for camera {@code ($800,$320)}:
     * {@code b = $100}, {@code s = $40}, {@code Camera_Y_pos_BG_copy = $320 >> 3 = 100}.
     * The band walk consumes {@code $40} then {@code $20} (leaving 4) and enters the third
     * band {@code $10} 12 lines from its end, so the screen reads
     * 12 lines of {@code HScroll_table+$010} ({@code b+5s}), then four {@code $10} bands
     * ({@code b+4s}, {@code b+3s}, {@code b+2s}, {@code b+s}) and the {@code $100} band
     * ({@code b}) for the remaining 148 lines. {@code ApplyDeformation} writes the negated word.
     */
    @Test
    void act1BandsWalkTheDeformArrayFromHScrollTablePlus00C() {
        assertBackgroundRuns(0, 0x0800, 0x0320, new int[][] {
                {12, 0x240}, {16, 0x200}, {16, 0x1C0}, {16, 0x180}, {16, 0x140}, {148, 0x100}});
    }

    /**
     * {@code ApplyDeformation} with {@code a5 = HScroll_table} and {@code LRZ2_BGDeformArray}.
     * Same camera: {@code Camera_Y_pos_BG_copy} is {@code 3 * $320 / 32 = 75}, the walk consumes
     * two {@code $20} bands (leaving 11) and enters the third 21 lines from its end, so the screen
     * reads 21 lines of {@code HScroll_table+$004} ({@code b+5s}, which act 2 only ever reaches
     * through the descending run), four {@code $10} bands and the {@code $F0} band.
     */
    @Test
    void act2BandsWalkTheDeformArrayFromHScrollTableWordZero() {
        assertBackgroundRuns(1, 0x0800, 0x0320, new int[][] {
                {21, 0x240}, {16, 0x200}, {16, 0x1C0}, {16, 0x180}, {16, 0x140}, {139, 0x100}});
    }

    @Test
    void providerRoutesBothLavaReefActsToTheDedicatedHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        ZoneScrollHandler act1 = provider.getHandler(Sonic3kZoneIds.ZONE_LRZ, 0);
        assertInstanceOf(SwScrlLrz.class, act1, "LRZ act 1 must not use the generic S3K fallback");
        assertSame(act1, provider.getHandler(Sonic3kZoneIds.ZONE_LRZ, 1),
                "both acts run the same handler and differ only by act id");
        assertSame(act1, provider.getHandler(Sonic3kZoneIds.ZONE_LRZ),
                "the zone-only lookup keeps the Lava Reef handler");
    }

    /**
     * The scroll provider mapped zones {@code $16} and {@code $17} to {@code SwScrlHpz} on the
     * zone alone. Only act 1 of each is a Hidden Palace layout: {@code $1601} is the playable act
     * and {@code $1701} the sanctuary. {@code $1600} is the Lava Reef boss act and {@code $1700}
     * Death Egg act 3, and neither runs {@code HPZ_BackgroundEvent}.
     */
    @Test
    void bossActsNoLongerBorrowTheHiddenPalaceHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        ZoneScrollHandler hiddenPalace = provider.getHandler(Sonic3kZoneIds.ZONE_HPZ, 1);
        assertInstanceOf(SwScrlHpz.class, hiddenPalace, "$1601 keeps SwScrlHpz");
        assertSame(hiddenPalace, provider.getHandler(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 1),
                "$1701 shares the sanctuary handler");

        assertNotSame(hiddenPalace, provider.getHandler(Sonic3kZoneIds.ZONE_HPZ, 0),
                "$1600 is the Lava Reef boss act");
        assertNotSame(hiddenPalace, provider.getHandler(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA, 0),
                "$1700 is Death Egg act 3");
    }

    /** The zone-only lookup, used where no act is known, keeps the historic Hidden Palace mapping. */
    @Test
    void zoneOnlyLookupStillReturnsTheHiddenPalaceHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());
        assertInstanceOf(SwScrlHpz.class, provider.getHandler(Sonic3kZoneIds.ZONE_HPZ));
        assertInstanceOf(SwScrlHpz.class, provider.getHandler(Sonic3kZoneIds.ZONE_DEZ_BOSS_SS_ARENA));
    }

    /** Once a level load declares the act, the zone-only lookup follows it. */
    @Test
    void initForZoneMakesTheZoneOnlyLookupActAware() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        provider.initForZone(Sonic3kZoneIds.ZONE_HPZ, 0, 0, 0);
        assertNotSame(provider.getHandler(Sonic3kZoneIds.ZONE_HPZ, 1),
                provider.getHandler(Sonic3kZoneIds.ZONE_HPZ),
                "$1600 must not fall back to SwScrlHpz once the act is known");

        provider.initForZone(Sonic3kZoneIds.ZONE_HPZ, 1, 0, 0);
        assertSame(provider.getHandler(Sonic3kZoneIds.ZONE_HPZ, 1),
                provider.getHandler(Sonic3kZoneIds.ZONE_HPZ));
    }

    @Test
    void foregroundScrollIsTheNegatedCameraOnEveryLine() {
        SwScrlLrz handler = new SwScrlLrz();
        int[] buffer = new int[VISIBLE_LINES];
        handler.update(buffer, 0x1234, 0x0640, 0, 0);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            assertEquals((short) -0x1234, unpackFG(buffer[line]), "foreground word on line " + line);
        }
    }

    /**
     * Asserts the 224 background words as runs of {@code {lineCount, HScroll_table word}}.
     * {@code ApplyDeformation} writes the negated word, so the expected scanline value is
     * {@code -word}.
     */
    private void assertBackgroundRuns(int actId, int cameraX, int cameraY, int[][] runs) {
        SwScrlLrz handler = new SwScrlLrz();
        int[] buffer = new int[VISIBLE_LINES];
        handler.update(buffer, cameraX, cameraY, 0, actId);

        int line = 0;
        for (int[] run : runs) {
            for (int i = 0; i < run[0]; i++) {
                assertEquals((short) -run[1], unpackBG(buffer[line]),
                        "background word on line " + line);
                line++;
            }
        }
        assertEquals(VISIBLE_LINES, line, "the expected runs must cover every scanline");
    }

    /** {@code b + n*s} as the ROM reads it: the high word of the 16.16 accumulator. */
    private static short wordOfFraction(int cameraX, int multiples, int step) {
        int value = (cameraX << 16) >> 3;
        for (int i = 0; i < multiples; i++) {
            value += step;
        }
        return (short) (value >> 16);
    }
}
