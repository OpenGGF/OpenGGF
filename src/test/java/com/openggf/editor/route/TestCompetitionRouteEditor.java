package com.openggf.editor.route;

import com.openggf.route.*;
import com.openggf.tools.StageplayCompetitionRouteHosts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Two-player editor checkpoint: programme identity, prefix ownership and shared presentation. */
class TestCompetitionRouteEditor {
    @TempDir Path dir;

    @Test void titleSetupPreservesBothPhysicalPadsAndLocksTheMenuPrefix() {
        var workspace = new RouteTapeWorkspace(new RouteTapeWorkspace.Host() {
            public boolean competitionAvailable() { return true; }
        });
        workspace.createCompetition(dir.resolve("cnz.route.json"), 2);
        assertInstanceOf(Inlet.Title.class, workspace.loaded().launch().inlet());
        assertEquals(StageplayCompetitionRouteHosts.menuPrefix(2), workspace.tape());
        assertEquals(1200, workspace.immutablePrefixRows());
        workspace.setCursor(900);
        assertThrows(IllegalArgumentException.class, () -> workspace.insertRows(1, true));
        assertEquals(StageplayCompetitionRouteHosts.menuPrefix(2), workspace.tape());
    }

    @Test void unverifiedHostDoesNotAdmitCompetition() {
        var workspace = new RouteTapeWorkspace(new RouteTapeWorkspace.Host() { });
        assertThrows(IllegalStateException.class, () -> workspace.createCompetition(dir.resolve("no.route.json"), 0));
    }

    @Test void frozenRoutesRoundTripWithoutLosingEitherPadOrGoals() throws Exception {
        for (String name : List.of("cnz1-slot-prefix", "cnz1-slot-flip")) {
            Path stem = Path.of("src/test/resources/routes/s2/competition", name);
            var route = RouteFiles.read(Path.of(stem + ".route.json"));
            assertEquals(route.tape(), RouteFiles.tapeFromScript(RouteFiles.scriptText(route.tape())));
            assertEquals(Files.readString(Path.of(stem + ".route.json")),
                    RouteCodec.formatManifest(route.manifest(), name + ".script"));
            var workspace = new RouteTapeWorkspace(new RouteTapeWorkspace.Host() { });
            workspace.open(Path.of(stem + ".route.json"));
            assertTrue(workspace.nativeFacts().stream().noneMatch(f -> f[0].equals("PROVISIONAL")));
            assertTrue(workspace.exportBlocker().isEmpty());
            assertTrue(workspace.nativeFacts().stream().anyMatch(f -> f[1].contains("slot-in-use-in-view")));
            Path movie=dir.resolve(name+".bk2");
            workspace.exportBk2(movie);
            var entry=route.manifest().nativeFacts().entry().orElseThrow();
            var expected=RouteFiles.inputPrefix(Path.of(entry.movie()),entry.prefixRows())
                    .append(route.tape().slice(entry.engineAnchorRows(),route.tape().size()));
            assertEquals(expected,RouteFiles.tapeFromMovie(movie,expected.size()));
            assertTrue(route.manifest().qualification().stream().anyMatch(q ->
                    q.scope()==ReplayScope.BIZHAWK && q.passed() && !q.coverage().isEmpty()));
            assertEquals(route.checks().startRow(), workspace.immutablePrefixRows());
            if (name.endsWith("prefix")) assertTrue(workspace.goalRows().stream().anyMatch(row -> row.player().equals("P1")));
            if (name.endsWith("flip")) {
                assertTrue(workspace.goalRows().stream().anyMatch(row -> row.player().equals("P2")));
                workspace.setCursor(route.tape().size()-1);
                assertTrue(workspace.timeline().runs().stream().anyMatch(run ->
                        RouteTapeTimeline.TRACKS.get(run.track()).slot() == PlayerSlot.P2));
            }
        }
    }

    @Test void conjunctionChipNamesBothPlayers() {
        var workspace = new RouteTapeWorkspace(new RouteTapeWorkspace.Host() { });
        workspace.create(dir.resolve("both.route.json"), RouteTapeFixtures.ehz(), 10);
        var region = Region.around(10, 10, 5, 5);
        workspace.setGoals(new GoalProgram(Guard.NONE, List.of(Leg.of("both", new RouteGoal.Achieve(
                new SamplePredicate.All(List.of(new SamplePredicate.PlayerIn(PlayerSlot.P1, region),
                        new SamplePredicate.PlayerIn(PlayerSlot.P2, region)))), 10))));
        assertEquals("Both", workspace.goalRows().getFirst().player());
    }
}
