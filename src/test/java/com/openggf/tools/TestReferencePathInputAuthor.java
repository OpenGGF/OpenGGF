package com.openggf.tools;

import com.openggf.route.InputTape;
import com.openggf.route.PadRow;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlanResult;
import com.openggf.route.PlayerSlot;
import com.openggf.route.RepairReport;
import com.openggf.route.RomIdentity;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteReplay;
import com.openggf.route.RouteSample;
import com.openggf.route.Roster;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reference-path front-end follows a reference, and finds it again after a forced
 * divergence, through the shared planner; its frozen rows replay cold to the same state.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestReferencePathInputAuthor {
    @Test
    void recoversTheReferencePathAfterADeliberateDivergenceAndReplaysUninterrupted() throws Exception {
        Path rom = RomTestUtils.ensureSonic3kRomAvailable().toPath();
        var hosts = EngineRouteHosts.withRoms(Map.of("s3k", rom));
        var launch = ReferencePathInputAuthor.launch(RomIdentity.ofFile("s3k", rom), 10, 0, new Roster("sonic", "tails"),
                320, ReferencePathInputAuthor.DEFAULT_V_INT_START);
        InputTape movie = RouteFiles.read(
                Path.of("src/test/resources/routes/s3k/ssz1-sonic-tails-cold-complete-320.route.json")).tape().slice(0, 2400);
        // Up to the open bridge the run is the reference run itself; then forty held-Left rows
        // the reference never pressed here.
        var prefix = new InputTape.Builder().addAll(movie.slice(0, 1851));
        for (int n = 0; n < 40; n++) prefix.add(PadRow.of(PlayerSlot.P1, PhysicalButton.LEFT));
        InputTape immutable = prefix.build();

        PlanResult result = ReferencePathInputAuthor.plan(hosts, launch, immutable, launch, movie, 1850, 2050);
        var solved = assertInstanceOf(PlanResult.Solved.class, result, result.toString());
        assertEquals(immutable, solved.route().tape().slice(0, immutable.size()), "the prefix is kept row for row");
        var kept = assertInstanceOf(RepairReport.Span.Original.class, solved.repair().spans().getFirst());
        assertEquals(immutable.size(), kept.length());
        assertEquals(1.0, solved.repair().keptShare(), solved.repair().summary());
        assertEquals(immutable.size(), solved.repair().originalSlices().getFirst().length());
        RouteSample end = solved.replayEnd();
        assertFalse(end.p1().dead());
        // The reference runs right along this stretch (X 1146 at 1850, 1743 at 2050).
        assertTrue(end.p1().centreX() >= 1600, "P1 is past the stretch: " + end.p1().centreX());
        assertEquals(solved.searchEnd(), solved.replayEnd(), "restores never leak into the recorded rows");

        // An independent cold playback of the frozen rows lands on the same observation.
        try (var playback = new RouteReplay(hosts).open(solved.route())) {
            assertEquals(end, playback.stepTo(playback.size()));
        }
    }
}
