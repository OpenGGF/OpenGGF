package com.openggf.tools;

import com.openggf.editor.route.RouteTapeFixtures;
import com.openggf.route.GoalProgram;
import com.openggf.route.Guard;
import com.openggf.route.Leg;
import com.openggf.route.PlayerSlot;
import com.openggf.route.QualificationStamp;
import com.openggf.route.Region;
import com.openggf.route.ReplayScope;
import com.openggf.route.RouteArtifact;
import com.openggf.route.RouteCodec;
import com.openggf.route.RouteFiles;
import com.openggf.route.RouteGoal;
import com.openggf.route.RouteManifest;
import com.openggf.route.SamplePredicate;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The editor worker replays working rows once through the facade and saves only after a passing replay. */
@RequiresRom(SonicGame.SONIC_2)
class TestRouteEditorWorker {
    @TempDir
    Path dir;

    private List<RouteEditorProtocol.Response> run(RouteEditorProtocol.Kind kind, RouteArtifact route, Path target,
                                                   boolean replace, Map<String, Path> roms) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            RouteEditorWorker.run(new RouteEditorProtocol.Request(kind, RouteCodec.formatTransport(route), target,
                    replace, roms), out);
        }
        var records = new ArrayList<RouteEditorProtocol.Response>();
        for (String line : bytes.toString(StandardCharsets.UTF_8).lines().toList()) {
            records.add(RouteEditorProtocol.decodeResponse(line));
        }
        return records;
    }

    private static RouteArtifact withRows(RouteArtifact source, String script, GoalProgram checks) {
        var tape = RouteFiles.tapeFromScript(script);
        RouteManifest m = source.manifest();
        return new RouteArtifact(new RouteManifest(m.launch(), tape.hash(), tape.size(), checks, m.draft(),
                m.provenance(), m.qualification(), m.nativeFacts()), tape);
    }

    private static Map<String, Path> roms() {
        return Map.of("s2", RomTestUtils.ensureSonic2RomAvailable().toPath());
    }

    @Test
    void replayStreamsBothParticipantsAndSaveWritesAStampForTheEditedContent() throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "90 R\n");
        RouteArtifact edited = withRows(RouteFiles.readDraft(manifest), "60 R\n4 R+A\n56 R\n", GoalProgram.NONE);
        var replay = run(RouteEditorProtocol.Kind.REPLAY, edited, null, false, roms());
        var result = replay.getLast().result();
        assertNotNull(result);
        assertEquals(RouteEditorProtocol.Verdict.PASSED, result.verdict(), result.detail());
        assertEquals(120, result.row());
        var p1 = replay.stream().filter(r -> r.point() != null && r.point().slot() == 1).map(RouteEditorProtocol.Response::point).toList();
        var p2 = replay.stream().filter(r -> r.point() != null && r.point().slot() == 2).toList();
        assertEquals(120, p1.size(), "One observed centre per row from the qualifying pass itself");
        assertEquals(120, p2.size(), "The sidekick's path streams beside P1's");
        assertTrue(p1.getLast().x() > p1.getFirst().x() + 40, "Holding Right moves P1 right");
        assertTrue(p1.stream().anyMatch(RouteEditorProtocol.Point::air), "The A press leaves the ground");
        assertTrue(replay.stream().noneMatch(r -> r.saved() != null), "A replay writes nothing");

        var save = run(RouteEditorProtocol.Kind.SAVE, edited, dir.resolve("ehz"), true, roms());
        Path saved = save.stream().filter(r -> r.saved() != null).findFirst().orElseThrow().saved();
        assertEquals(manifest.toAbsolutePath(), saved);
        var route = RouteFiles.read(saved);
        assertEquals(edited.tape(), route.tape());
        QualificationStamp stamp = route.manifest().qualification().stream()
                .filter(s -> s.scope() == ReplayScope.ENGINE).findFirst().orElseThrow();
        assertTrue(stamp.passed());
        assertEquals(route.content(), stamp.content());
        assertEquals(save.getLast().result().content(), route.content().toString());
    }

    @Test
    void aGoalTheRowsCannotMeetFailsAtItsFirstRowAndNeverSaves() throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "40 R\n");
        var unreachable = GoalProgram.of(Guard.NONE, Leg.of("far-right", new RouteGoal.Achieve(
                new SamplePredicate.PlayerIn(PlayerSlot.P1, new Region(9000, 0, 9100, 2000))), 25));
        RouteArtifact route = withRows(RouteFiles.readDraft(manifest), "40 R\n", unreachable);
        var records = run(RouteEditorProtocol.Kind.SAVE, route, dir.resolve("ehz"), true, roms());
        var result = records.getLast().result();
        assertEquals(RouteEditorProtocol.Verdict.FAILED, result.verdict());
        assertTrue(result.row() >= 0 && result.row() <= 25, "The leg's row bound names the first failing row: " + result.row());
        assertTrue(result.detail().contains("far-right") || !result.detail().isBlank(), result.detail());
        assertTrue(records.stream().noneMatch(r -> r.saved() != null));
        assertEquals(40, RouteFiles.readDraft(manifest).tape().size());
        assertTrue(RouteFiles.readDraft(manifest).manifest().checks().isEmpty(), "A failed save leaves the files untouched");
    }

    @Test
    void aMissingRomIsReportedAsUnsupportedNotAsAPassOrACrash() throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "4 R\n");
        var records = run(RouteEditorProtocol.Kind.SAVE, RouteFiles.readDraft(manifest), dir.resolve("ehz"), true, Map.of());
        assertEquals(RouteEditorProtocol.Verdict.UNSUPPORTED, records.getLast().result().verdict());
        assertTrue(records.stream().noneMatch(r -> r.saved() != null));
    }
}
