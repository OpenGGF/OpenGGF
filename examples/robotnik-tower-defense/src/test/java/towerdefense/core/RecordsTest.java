package towerdefense.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RecordsTest {
    @Test void malformedAndOutOfRangeSavesCannotInventRecords() {
        assertEquals(new Records(0, 0, 0), Records.parse("broken"));
        assertEquals(new Records(0, 0, 0), Records.parse("-1,99,99999999999999"));
        assertEquals(new Records(0, 0, 0), Records.parse("123,8"));
    }

    @Test void recordsRoundTripAndKeepTheBestRun() {
        Records r = new Records(1500, 5, 2);
        assertEquals(r, Records.parse(r.encode()));
        assertEquals(new Records(1500, 5, 2), r.completed(100, 1, false));
        assertEquals(new Records(3000, 15, 3), r.completed(3000, 15, true));
    }
}
