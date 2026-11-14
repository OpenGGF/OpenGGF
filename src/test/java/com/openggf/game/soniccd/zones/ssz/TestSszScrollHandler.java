package com.openggf.game.soniccd.zones.ssz;

import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Hand-derived checks of {@link SszScrollHandler} that need no disc: layout totals and a few lines whose value follows
 * from the routine's arithmetic alone (docs/scddisasm r7/scroll_*.asm). The native picture comparison is
 * {@code TestSszNativePictures}.
 */
class TestSszScrollHandler {
    private static final int LINES = ScrollEffectComposer.VISIBLE_LINES;
    private static final CdStagePayload[] STAGES = {
            CdStagePayload.R71A, CdStagePayload.R71B, CdStagePayload.R71C, CdStagePayload.R71D,
            CdStagePayload.R72A, CdStagePayload.R72B, CdStagePayload.R72C, CdStagePayload.R72D,
            CdStagePayload.R73C, CdStagePayload.R73D};

    private static int[] sineRamp() {
        int[] table = new int[320];
        for (int i = 0; i < table.length; i++) table[i] = i;
        return table;
    }

    private static int bgWord(int packed) {
        return (short) packed;
    }

    private static int fgWord(int packed) {
        return (short) (packed >> 16);
    }

    private static int[] frame(SszScrollHandler handler, int frame, int cameraX, int cameraY) {
        int[] buffer = new int[LINES];
        handler.update(buffer, cameraX, cameraY, frame, 0);
        return buffer;
    }

    /** Every stage fills 96 words of bg_scroll_lines except Past, whose last 20 words are the BG3 run before the wobble (76). */
    @Test
    void everyLayoutWritesTheWordsTheRoutineWrites() {
        for (CdStagePayload payload : STAGES) {
            int total = 0;
            for (SszScrollHandler.Op op : SszScrollHandler.layoutFor(payload).ops()) {
                total += op instanceof SszScrollHandler.Flat flat ? flat.words() : ((SszScrollHandler.Curve) op).words();
            }
            boolean past = payload == CdStagePayload.R71B || payload == CdStagePayload.R72B;
            assertEquals(past ? 76 : 96, total, payload.name());
            assertEquals(past, SszScrollHandler.layoutFor(payload).wobble(), payload + " wobble");
        }
    }

    @Test
    void aCurveThatWritesFewerWordsThanItsAddaIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SszScrollHandler.Curve(
                SszScrollHandler.Source.BG, SszScrollHandler.Source.FG, false, 7, 0x25, 9, false, 0x58 / 2, new int[8]));
    }

    /**
     * R73C, camera moved 16 px from the origin: scroll_x_move = $1000, BG2 += 3 * ($1000 << 7) (scroll_3c.asm:153-160)
     * so its word is 24, BG += 3 * ($1000 << 4) (:162-169) so its word is 3; the foreground word is -16. With the
     * camera Y at 672 the background Y is 672 (:170-171), word 84, where the BG2 run of 12 words starts
     * (scroll_3c.asm:197-199) and the 18 words behind it were never written (0).
     */
    @Test
    void goodFutureActThreeFlatRunsFollowTheCameraDelta() {
        SszScrollHandler handler = new SszScrollHandler(CdStagePayload.R73C);
        handler.init(0, 0, 672);
        int[] out = frame(handler, 1, 16, 672);
        for (int line = 0; line < 96; line++) {
            assertEquals(-24, bgWord(out[line]), "BG2 run, line " + line);
            assertEquals(-16, fgWord(out[line]), "foreground, line " + line);
        }
        assertEquals(0, bgWord(out[96]), "cold RAM behind the run");
        assertEquals(672, handler.getVscrollFactorBG());
    }

    /**
     * R71A, camera moved 16 px: BG += 3 * ($1000 << 4) so its word is 3 (scroll_1a.asm:172-179). Camera Y 928: a quarter
     * is 232, plus its half 116, so the background Y is 348 (:180-185), word 43 with 4 lines skipped; the 4 BG words at
     * 44-47 (scroll_1a.asm:199-204) are lines 4 to 35.
     */
    @Test
    void presentActOneBackgroundRunFollowsTheCameraDelta() {
        SszScrollHandler handler = new SszScrollHandler(CdStagePayload.R71A);
        handler.init(0, 0, 928);
        int[] out = frame(handler, 1, 16, 928);
        assertEquals(348, handler.getVscrollFactorBG());
        for (int line = 4; line < 36; line++) assertEquals(-3, bgWord(out[line]), "BG run, line " + line);
    }

    /**
     * R71B, camera Y 1632: background Y 612, word 76 would be next, but the first block's end is at 612 + 8 - 4 + 8 = 624,
     * at or past $268, so lines 4 onward are the wobble (scroll_1b.asm:367). The water phase is $80 after the first
     * update, so the angle starts at 0 and gains 2 per line; with a ramp for the sine table and BG3 at 0 each line is
     * -((angle) lsr.w 5) (:392-400).
     */
    @Test
    void pastBottomOfThePlaneIsTheSineWobble() {
        SszScrollHandler handler = new SszScrollHandler(CdStagePayload.R71B, sineRamp());
        handler.init(0, 0, 1632);
        int[] out = frame(handler, 1, 0, 1632);
        assertEquals(612, handler.getVscrollFactorBG());
        for (int line = 4; line < LINES; line++) {
            int angle = 2 * (line - 4);
            assertEquals(-((angle & 0xFF) >>> 5), bgWord(out[line]), "wobble line " + line);
        }
    }

    @Test
    void theSameFrameRecomputedDoesNotAdvanceTheLayers() {
        SszScrollHandler handler = new SszScrollHandler(CdStagePayload.R71A);
        handler.init(0, 0, 0);
        frame(handler, 1, 16, 0);
        int[] first = frame(handler, 2, 32, 0);
        int[] again = frame(handler, 2, 32, 0);
        assertEquals(java.util.Arrays.toString(first), java.util.Arrays.toString(again));
    }
}
