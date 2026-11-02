package com.openggf.tools;

import com.openggf.route.*;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The engine route host: explicit launch clocks, complete checkpoint tokens and input ownership. */
@RequiresRom(SonicGame.SONIC_2)
class TestEngineRouteHost {
    private static EngineRouteHosts hosts() {
        return EngineRouteHosts.withRoms(Map.of("s2", RomTestUtils.ensureSonic2RomAvailable().toPath()));
    }

    private static LaunchContract ehz(int vIntStart, Seeds seeds) {
        return new LaunchContract(RomIdentity.standard("s2"), new Inlet.ColdLevel(new LevelId("s2", 0, 0), false),
                new Roster("sonic", "tails"), 320, "off", new InitialState(vIntStart, seeds));
    }

    private static InputTape run(int rows) {
        var list = new ArrayList<PadRow>();
        for (int i = 0; i < rows; i++) {
            list.add(i % 40 == 30 ? PadRow.of(PlayerSlot.P1, PhysicalButton.RIGHT, PhysicalButton.A)
                    : PadRow.of(PlayerSlot.P1, PhysicalButton.RIGHT));
        }
        return InputTape.of(list);
    }

    private static List<RouteSample> play(LaunchContract launch, InputTape tape) {
        var samples = new ArrayList<RouteSample>();
        try (ReplayWorld world = hosts().openColdReplay(launch)) {
            samples.add(world.observe());
            for (int i = 0; i < tape.size(); i++) samples.add(world.step(tape.row(i)).after());
        }
        return samples;
    }

    @Test
    void launchesDeclareTheirClockAndReplayIdenticallyInOneJvm() {
        InputTape tape = run(180);
        List<RouteSample> first = play(ehz(5, Seeds.NONE), tape);
        assertEquals(5, first.getFirst().clocks().vIntRunCount(), "the declared V_int start is written at entry");
        List<RouteSample> skewed = play(ehz(1000, Seeds.NONE), tape);
        assertEquals(1000, skewed.getFirst().clocks().vIntRunCount());
        List<RouteSample> again = play(ehz(5, Seeds.NONE), tape);
        assertEquals(first, again, "a later session in the same JVM does not inherit the earlier clock");
        assertTrue(first.getLast().p1().centreX() > first.getFirst().p1().centreX() + 200, "rows drive the leader");
    }

    @Test
    void checkpointsCarryTheSidekickSeedWindowOfAPositionedEntry() {
        RouteSample entry;
        try (ReplayWorld world = hosts().openColdReplay(ehz(0, Seeds.NONE))) {
            entry = world.observe();
        }
        var positioned = new Seeds(entry.p1().centreX() + 0x60, entry.p1().centreY() - 0x20, false, null, null,
                false, false, null);
        InputTape tape = run(8);
        try (RouteWorld world = hosts().openSearch(ehz(0, positioned))) {
            world.step(tape.row(0));
            WorldCheckpoint checkpoint = world.capture();
            var first = new ArrayList<RouteSample>();
            for (int i = 1; i < tape.size(); i++) first.add(world.step(tape.row(i)).after());
            world.restore(checkpoint);
            assertEquals(1, world.rowsOffered());
            var second = new ArrayList<RouteSample>();
            for (int i = 1; i < tape.size(); i++) second.add(world.step(tape.row(i)).after());
            assertEquals(first, second, "the seed window is host state the checkpoint restores");
            assertTrue(first.getFirst().p2().isPresent(), "the team has a sidekick to seed");
        }
    }

    @Test
    void routeReplayMintsEvidenceForTheExactContent(@TempDir Path dir) throws Exception {
        var route = RouteArtifact.of(ehz(0, Seeds.NONE), run(60), new Provenance("test", "", "", List.of()));
        var result = new RouteReplay(hosts()).replay(route, Cancellation.NONE);
        var passed = assertInstanceOf(ReplayResult.Passed.class, result);
        assertEquals(route.content(), passed.evidence().content());
        assertEquals(60, passed.evidence().rows());
        var written = RouteFiles.writeNew(dir.resolve("ehz"), route, passed.evidence());
        try (var playback = new RouteReplay(hosts()).open(RouteFiles.read(written.manifest()))) {
            RouteSample end = playback.stepTo(60);
            assertTrue(playback.finished());
            assertThrows(IndexOutOfBoundsException.class, playback::step, "no clamping past the last row");
            assertEquals(60, end.rowsOffered());
        }
    }

    @Test
    void creatorFrameControllersThatOwnInputAreRefused() {
        var decorated = hosts().withModuleDecorator(
                com.openggf.mods.code.HostileFrameControllers.neutralInputController("s2"));
        var refused = assertThrows(RouteHostException.class, () -> decorated.openColdReplay(ehz(0, Seeds.NONE)));
        assertEquals(RouteHostException.Reason.INPUT_NOT_OWNED, refused.reason());
        var route = RouteArtifact.of(ehz(0, Seeds.NONE), run(4), new Provenance("test", "", "", List.of()));
        assertInstanceOf(ReplayResult.Unsupported.class, new RouteReplay(decorated).replay(route, Cancellation.NONE));
    }
}
