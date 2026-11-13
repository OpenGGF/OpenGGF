package openggf.timeattack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TestWrapGhostPlacement {
    @Test void drawsNearestImageOnEitherSideAndOnVerticalWrap() {
        assertEquals(514, RaceProgressEstimate.nearestImage(2, 510, 512));
        assertEquals(-2, RaceProgressEstimate.nearestImage(510, 2, 512));
        assertEquals(258, RaceProgressEstimate.nearestImage(2, 254, 256));
        assertEquals(900, RaceProgressEstimate.nearestImage(900, 10, 0));
    }
}
