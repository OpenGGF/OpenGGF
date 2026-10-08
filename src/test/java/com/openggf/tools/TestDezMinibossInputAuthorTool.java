package com.openggf.tools;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Author must not mistake the first native eight-hit counter for final defeat. */
class TestDezMinibossInputAuthorTool {
    @Test
    void firstEightHitsAndTheirTransitionLeaveTheSecondPhaseOutstanding() {
        assertEquals(16, DezMinibossInputAuthorTool.remainingHits(0x7DE28, 0));
        assertEquals(15, DezMinibossInputAuthorTool.remainingHits(0x7DE6E, 1));
        assertEquals(8, DezMinibossInputAuthorTool.remainingHits(0x7DE6E, 8));
        assertEquals(8, DezMinibossInputAuthorTool.remainingHits(0x7DF8C, 8));
    }

    @Test
    void secondPhaseResetsItsNativeCounterWithoutResettingTotalProgress() {
        assertEquals(8, DezMinibossInputAuthorTool.remainingHits(0x7E0A6, 0));
        assertEquals(7, DezMinibossInputAuthorTool.remainingHits(0x7E0A6, 1));
        assertEquals(1, DezMinibossInputAuthorTool.remainingHits(0x7E0A6, 7));
        assertEquals(0, DezMinibossInputAuthorTool.remainingHits(0x7E0A6, 8));
    }

    @Test
    void nativeDefeatCountdownIsTerminalBeforeObjectRemoval() {
        assertEquals(0, DezMinibossInputAuthorTool.remainingHits(0x85668, 8));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {-1, 2})
    void rejectsOutOfMoviePrefixesBeforeBoot(int prefix,
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path temporary) throws Exception {
        var input = temporary.resolve("one-frame.bk2");
        InputLogAuthorTool.author("1 -", input, "s3k");
        var settings = new GameplayCaptureSession.Settings(800, "sonic", "", "off", null, null, null);
        assertThrows(IllegalArgumentException.class, () -> DezMinibossInputAuthorTool.run(
                temporary.resolve("not-a-rom.gen"), settings, input, prefix,
                DezMinibossInputAuthorTool.Encounter.DEZ, temporary.resolve("output"), 1));
    }

}
