package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.level.rings.LostRingObjectInstance;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.FullReset;
import com.openggf.trace.TraceData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
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

    @Test
    void r81bScarabHurtScattersRingsOnTheContactFrame() throws Exception {
        var fixture = CdFixtureReplay.Fixture.under(
                CdTestMedia.nativeDir("rehost-fixtures-mmz2/m-scarab"), CdStagePayload.R81B);
        assumeTrue(fixture.present(), "Missing complete R81B Scarab movie or trace");
        int hurtRow = 1625;
        try (var disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            var outcome = CdFixtureReplay.replay(disc, tempDir, fixture, hurtRow, false, hurtRow, hurtRow,
                    new CdFixtureReplay.Hooks() {
                        @Override
                        public void row(int row, AbstractPlayableSprite sonic) {
                            if (row != hurtRow) return;
                            var rings = GameServices.level().getObjectManager()
                                    .activeObjectsOfType(LostRingObjectInstance.class).stream()
                                    .sorted(Comparator.comparingInt(LostRingObjectInstance::getSlotIndex))
                                    .map(ring -> new RingPosition(ring.getX(), ring.getY()))
                                    .toList();
                            // R81B aux_state.jsonl row 1625, slots 40 and 48-51.
                            assertAll(
                                    () -> assertTrue(sonic.isHurt()),
                                    () -> assertEquals(0, sonic.getRingCount()),
                                    () -> assertEquals(List.of(new RingPosition(0xB44, 0x688),
                                            new RingPosition(0xB45, 0x688), new RingPosition(0xB42, 0x688),
                                            new RingPosition(0xB47, 0x688), new RingPosition(0xB41, 0x689)), rings));
                        }
                    });
            var contact = outcome.row(hurtRow);
            assertAll(
                    () -> assertEquals(contact.expected().xSpeed(), (short) contact.xSpeed(), contact::format),
                    () -> assertEquals(contact.expected().ySpeed(), (short) contact.ySpeed(), contact::format));
        }
    }

    private record RingPosition(int x, int y) { }
}
