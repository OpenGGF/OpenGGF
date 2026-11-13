package openggf.timeattack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TestRaceProgressEstimate {
    @Test void ordinalPrecedesDistanceAndLapStartsAtLine() {
        assertTrue(RaceProgressEstimate.progress(2, 1, 512, 512, 0)
                > RaceProgressEstimate.progress(1, 511, 512, 512, 0));
        assertEquals(3.5, RaceProgressEstimate.progress(3, 0, 512, 512, 256));
        assertEquals(2.5, RaceProgressEstimate.progress(2, 500, 1000, 0, 0));
    }
}
