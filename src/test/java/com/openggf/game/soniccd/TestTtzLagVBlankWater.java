package com.openggf.game.soniccd;

import com.openggf.game.GameServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tidal Tempest's water line on every native row, lag rows included. {@code VBlankLag} runs {@code UpdateWater}
 * (r4/main.asm:994-1022, the call at :1010), so {@code water_angle} advances by 2 on every V-INT, not per level frame;
 * the engine's lag V-INT ({@code CdLevelEventProvider.advanceVblankOnlyState}) does the same through the zone's
 * {@code CdZoneFragment.lagVBlankUpdatesWater}. Native: the boss probe's {@code water_y} ($FFF646,
 * {@code globals.csv} column 22), post-frame RAM of the trace's own movie; compared on every row before the
 * replay's first player/camera divergence. Water is V-INT state, so it is compared on the row itself, not on
 * the completed level frame.
 */
class TestTtzLagVBlankWater {
    @TempDir
    Path tempDir;

    @Test
    void waterLineEqualsNativeOnEveryRowIncludingLagRows() throws Exception {
        String movie = "R43C-fight";
        Path root = CdTestMedia.nativeFile("rehost-boss-ttz/" + movie + "/manifest.json").getParent();
        Path traceDir = root.resolve("trace");
        int offset = com.openggf.trace.TraceData.load(traceDir).metadata().bk2FrameOffset();
        Map<Integer, Integer> nativeWater = new HashMap<>();
        List<String> lines = Files.readAllLines(root.resolve("probe/globals.csv"));
        for (String line : lines.subList(1, lines.size())) {
            String[] c = line.split(",");
            nativeWater.put(Integer.parseInt(c[0]) - offset - 1, Integer.parseInt(c[22]));    // probe frame f = BK2 row f-1
        }
        Map<Integer, Integer> engineWater = new HashMap<>();
        int[] vIntAtRow0 = {-1};
        CdFixtureReplay.Outcome outcome;
        try (CdDiscDataSource disc = CdDiscDataSource.open(CdTestMedia.usaCue())) {
            outcome = CdFixtureReplay.replay(disc, tempDir,
                    new CdFixtureReplay.Fixture(CdStagePayload.R43C, traceDir, root.resolve("movie.bk2")), 4000,
                    true, -1, -1, row -> {
                        var levels = GameServices.level();
                        if (row == 0) vIntAtRow0[0] = levels.getObjectManager().getVblaCounter();
                        var water = GameServices.waterOrNull();
                        engineWater.put(row, water == null ? -1
                                : water.getGameplayWaterLevelY(levels.getFeatureZoneId(), levels.getFeatureActId()));
                    });
        }
        int last = outcome.firstErrorRow() < 0 ? outcome.rowsCompared() - 1 : outcome.firstErrorRow() - 1;
        var trace = outcome.trace();
        int lagRows = 0;
        List<String> mismatches = new ArrayList<>();
        for (int row = 0; row <= last; row++) {
            if (row > 0 && trace.getFrame(row).gameplayFrameCounter() == trace.getFrame(row - 1).gameplayFrameCounter()) {
                lagRows++;
            }
            Integer n = nativeWater.get(row), e = engineWater.get(row);
            if (n == null || !n.equals(e)) mismatches.add("row " + row + " native " + n + " engine " + e);
        }
        // Diagnostic only: the V-INT counter's base at row 0 (load-time V-INTs; the lost rings' floor check and the
        // HUD's RINGS flash read its low byte). Not asserted; see the LAG report's decision table.
        System.out.println("TTZLAGWATER levelVIntCounter at row 0: native " + trace.getFrame(0).vblankCounter()
                + " engine " + vIntAtRow0[0]);
        System.out.println("TTZLAGWATER " + movie + " rows 0-" + last + " plateau rows " + lagRows + " mismatches "
                + mismatches.size() + " " + mismatches.stream().limit(8).toList());
        assertTrue(last > 2000, "the replay must hold long enough to see lag rows: " + outcome.fields());
        assertTrue(lagRows > 10, "the compared rows must include lag rows");
        assertTrue(mismatches.isEmpty(), mismatches.size() + " water_y rows differ, first " + mismatches.stream().findFirst().orElse(""));
    }
}
