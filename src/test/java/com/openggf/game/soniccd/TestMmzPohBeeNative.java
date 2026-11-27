package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestMmzPohBeeNative {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @CsvSource({"R81A, 53", "R81B, 51"})
    void pohBeeSlotsMatchTheRetailRoute(CdStagePayload payload, int appearances) throws Exception {
        Path root = CdTestMedia.nativeFile("rehost-long/README.md").getParent();
        var fixture = CdFixtureReplay.Fixture.under(root, payload);
        Path cue = CdTestMedia.usaCue();
        TraceData trace = TraceData.load(fixture.traceDir());
        var expected = new TreeSet<Integer>();
        List<String> differences = new ArrayList<>();
        int[] nextNativeRow = {0};
        int[] created = {0};
        try (CdDiscDataSource disc = CdDiscDataSource.open(cue)) {
            CdFixtureReplay.replay(disc, tempDir, fixture, Integer.MAX_VALUE, true, -1, -1, engineRow -> {
                int nativeRow = CdFixtureReplay.completedRow(trace, engineRow);
                while (nextNativeRow[0] <= nativeRow) {
                    for (var event : trace.getEventsForFrame(nextNativeRow[0]++)) {
                        if (event instanceof TraceEvent.ObjectAppeared e && "0x2E".equalsIgnoreCase(e.objectType())) {
                            expected.add(e.slot());
                            created[0]++;
                        } else if (event instanceof TraceEvent.ObjectRemoved e && "0x2E".equalsIgnoreCase(e.objectType())) {
                            expected.remove(e.slot());
                        }
                    }
                }
                var objects = GameServices.level().getObjectManager();
                var actual = new TreeSet<Integer>();
                for (var obj : objects.getActiveObjects()) {
                    if (obj instanceof AbstractObjectInstance actor && !actor.isDestroyed()
                            && actor.getSpawn().objectId() == 0x2E) {
                        actual.add(actor.getSlotIndex());
                    }
                }
                if (!actual.equals(expected) && differences.size() < 5) {
                    differences.add("row " + engineRow + " (native " + nativeRow + "): " + actual + " vs " + expected);
                }
            });
        }
        assertEquals(List.of(appearances, List.of()), List.of(created[0], differences));
    }
}
