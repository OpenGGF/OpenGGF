package openggf.timeattack.ui;

import com.openggf.mods.run.TrackKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Competition clock as the ROM draws it: per-lap hundredths through byte_37E52, summed. */
class TestLapTimeFormat {
    @Test
    void framesBecomeRomHundredthsTruncatedPerLap() {
        // byte_37E52 (sonic3k.asm:74906-74909) starts 0, 1, 3, 5, 6, 8.
        assertEquals(List.of(0, 1, 3, 5, 6, 8), List.of(0, 1, 2, 3, 4, 5).stream()
                .map(LapTimeFormat::centiseconds).toList());
        assertEquals("0'21\"50", LapTimeFormat.lap(21 * 60 + 30));
        assertEquals("0'19\"83", LapTimeFormat.lap(19 * 60 + 50));
        assertEquals("9'59\"99", LapTimeFormat.lap(Integer.MAX_VALUE), "the ROM clock's cap");
    }

    @Test
    void aTotalIsTheSumOfTruncatedLapsNotTheTruncatedSum() {
        // Two laps of 1 frame each: each shows 0'00"01, so the total is 0'00"02 (exact frames: 0'00"03).
        assertEquals("0'00\"02", LapTimeFormat.total(List.of(1, 1)));
        assertEquals("0'00\"02", LapTimeFormat.finish(TrackKind.ARENA, 2, List.of(1, 1)));
        assertEquals("0:00.03", LapTimeFormat.finish(TrackKind.ACT, 2, List.of()), "acts keep Time Attack's clock");
    }

    @Test
    void countdownsRoundUpToWholeSeconds() {
        assertEquals("0:08", LapTimeFormat.countdown(7_400));
        assertEquals("0:42", LapTimeFormat.countdown(41_001));
        assertEquals("0:00", LapTimeFormat.countdown(0));
        assertEquals("1:00", LapTimeFormat.countdown(60_000));
    }
}
