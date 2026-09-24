package com.openggf.game.sonic3k.objects.bosses;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestSszMechaAim {
    @ParameterizedTest
    @CsvSource({
        "0, 0, 0, 0, 3, 2048, 0",
        "0, 0, 100, 100, 3, 2048, 2048",
        "0, 0, -100, 100, 2, -1024, 1024",
        "0, 0, 3, 1, 3, 2048, 682",
        "0, 0, -1, -3, 2, -341, -1024",
        "65530, 4, 4, 4, 3, 2048, 0",
        "4, 65530, 4, 4, 2, 0, 1024"
    })
    void preservesNativeWordDifferenceAndTruncatedSlope(int x, int y, int tx, int ty,
                                                       int exponent, int vx, int vy) {
        var actual = SszMechaAim.toward(x, y, tx, ty, exponent);
        assertEquals(vx, actual.x()); assertEquals(vy, actual.y());
    }
}
