package openggf.timeattack.mp;

import openggf.timeattack.ui.MenuPage;
import openggf.timeattack.ui.RecordingSceneCanvas;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestArenaStartCard {
    private static ArenaStartCard.Card card(int racers, long toRelease) {
        List<ArenaStartCard.Racer> list = new ArrayList<>();
        for (int i = 0; i < racers; i++) list.add(new ArenaStartCard.Racer("Racer " + i, MenuPage.TEXT, i == 0));
        return new ArenaStartCard.Card("DESERT PALACE", 5, racers, toRelease + 4000, toRelease, list, "Q");
    }

    @Test
    void theFadeMirrorsTheNativeFadeInAndEndsBlackAtRelease() {
        assertEquals(0x16 * 1000L / 60, ArenaStartCard.FADE_MILLIS);
        assertEquals(0, ArenaStartCard.fadeAlpha(5000));
        assertEquals(0, ArenaStartCard.fadeAlpha(ArenaStartCard.FADE_MILLIS));
        int half = ArenaStartCard.fadeAlpha(ArenaStartCard.FADE_MILLIS / 2);
        assertTrue(half > 100 && half < 155, "linear: " + half);
        assertEquals(255, ArenaStartCard.fadeAlpha(0));
    }

    @Test
    void theSecondMeterDrainsEveryFrameAndRefillsAtEachDigit() {
        assertEquals(60, ArenaStartCard.secondMeter(5000, 60), "a fresh digit: full");
        assertEquals(30, ArenaStartCard.secondMeter(4500, 60));
        assertEquals(1, ArenaStartCard.secondMeter(4017, 60));
        assertEquals(60, ArenaStartCard.secondMeter(4000, 60), "4000 still shows 0:04 in full");
        assertTrue(ArenaStartCard.secondMeter(4983, 60) < ArenaStartCard.secondMeter(5000, 60),
                "one frame later the meter is shorter");
        assertEquals(0, ArenaStartCard.secondMeter(0, 60));
    }

    @Test
    void aFieldTooLargeForTheGridCountsTheRest() {
        assertEquals(8, ArenaStartCard.visibleNames(320, 224, 8));
        int shown = ArenaStartCard.visibleNames(320, 224, 27);
        assertTrue(shown < 27 && shown >= 12, "three columns of names, the last cell counts the rest: " + shown);
        assertEquals(18, shown + 1, "three columns of six rows at 320 px");
    }

    @ParameterizedTest
    @ValueSource(ints = {320, 400, 640})
    void theCardStaysOnScreenAndCoversItWhileHeld(int width) {
        var canvas = new RecordingSceneCanvas(width);
        ArenaStartCard.draw(canvas, card(27, 3000));
        canvas.assertFillsOnScreen();
        int[] first = canvas.fills.getFirst();
        assertArrayEquals(new int[] {0, 0, width, 224}, new int[] {first[0], first[1], first[2], first[3]});
        assertEquals(0xFF, first[4] >>> 24, "opaque: the held arena is never visible");
        assertTrue(canvas.fills.stream().noneMatch(f -> f[2] == width && f[3] == 224 && f[4] >>> 24 != 0xFF
                && (f[4] & 0xFFFFFF) == 0), "no fade before its last native fade length");

        var fading = new RecordingSceneCanvas(width);
        ArenaStartCard.draw(fading, card(27, ArenaStartCard.FADE_MILLIS / 2));
        int[] last = fading.fills.getLast();
        assertEquals(0, last[4] & 0xFFFFFF, "the last fill is the black fade over the whole card");
        assertEquals(width, last[2]);
    }
}
