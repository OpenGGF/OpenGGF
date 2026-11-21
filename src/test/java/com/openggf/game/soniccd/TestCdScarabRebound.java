package com.openggf.game.soniccd;

import com.openggf.tests.FullReset;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@FullReset
class TestCdScarabRebound {
    @TempDir
    Path tempDir;

    @Test
    void r81bScarabKillMatchesNativeRebound() throws Exception {
        var fixture = CdFixtureReplay.Fixture.under(CdTestMedia.nativeDir("u50-mmz-act-end"), CdStagePayload.R81B);
        assumeTrue(fixture.present(), "Missing complete R81B movie or trace");
        int killRow = 2635;
        int lastRow = TraceData.load(fixture.traceDir()).frameCount() - 1;

        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var outcome = CdFixtureReplay.replay(disc, tempDir, fixture, lastRow, true, killRow, killRow);
            System.out.printf("[R81B] compared %d rows, first divergence %d: %s%n",
                    outcome.rowsCompared(), outcome.firstErrorRow(), outcome.fields());
            assertTrue(outcome.rowsCompared() > killRow, "Replay must reach the Scarab kill");
            var contact = outcome.row(killRow);
            assertEquals(contact.expected().ySpeed(), (short) contact.ySpeed(), contact::format);
        }
    }
}
