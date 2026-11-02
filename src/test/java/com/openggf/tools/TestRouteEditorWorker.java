package com.openggf.tools;

import com.openggf.editor.route.RouteTapeFixtures;
import com.openggf.route.QualificationStamp;
import com.openggf.route.ReplayScope;
import com.openggf.route.RouteFiles;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The editor worker replays working rows through the facade and saves only after a passing replay. */
@RequiresRom(SonicGame.SONIC_2)
class TestRouteEditorWorker {
    @TempDir
    Path dir;

    private List<RouteEditorProtocol.Response> run(RouteEditorProtocol.Request request) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            RouteEditorWorker.run(request, out);
        }
        var records = new ArrayList<RouteEditorProtocol.Response>();
        for (String line : bytes.toString(StandardCharsets.UTF_8).lines().toList()) {
            records.add(RouteEditorProtocol.decodeResponse(line));
        }
        return records;
    }

    @Test
    void replayStreamsBothParticipantsAndSaveWritesAStampForTheEditedContent() throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "90 R\n");
        String edited = "60 R\n4 R+A\n56 R\n";
        var roms = Map.of("s2", RomTestUtils.ensureSonic2RomAvailable().toPath());
        var replay = run(new RouteEditorProtocol.Request(RouteEditorProtocol.Kind.REPLAY, manifest, edited, null, false, roms));
        var result = replay.getLast().result();
        assertNotNull(result);
        assertEquals(RouteEditorProtocol.Verdict.PASSED, result.verdict(), result.detail());
        assertEquals(120, result.row());
        var p1 = replay.stream().filter(r -> r.point() != null && r.point().slot() == 1).map(RouteEditorProtocol.Response::point).toList();
        var p2 = replay.stream().filter(r -> r.point() != null && r.point().slot() == 2).toList();
        assertEquals(120, p1.size());
        assertEquals(120, p2.size(), "The sidekick's path streams beside P1's");
        assertTrue(p1.getLast().x() > p1.getFirst().x() + 40, "Holding Right moves P1 right");
        assertTrue(replay.stream().anyMatch(r -> r.progress() != null && r.progress().pass() == 2));
        assertTrue(replay.stream().noneMatch(r -> r.saved() != null), "A replay writes nothing");
        assertEquals(90, RouteFiles.read(manifest).tape().size());

        var save = run(new RouteEditorProtocol.Request(RouteEditorProtocol.Kind.SAVE, manifest, edited,
                dir.resolve("ehz"), true, roms));
        Path saved = save.stream().filter(r -> r.saved() != null).findFirst().orElseThrow().saved();
        assertEquals(manifest.toAbsolutePath(), saved);
        var route = RouteFiles.read(saved);
        assertEquals(RouteFiles.tapeFromScript(edited), route.tape());
        QualificationStamp stamp = route.manifest().qualification().stream()
                .filter(s -> s.scope() == ReplayScope.ENGINE).findFirst().orElseThrow();
        assertTrue(stamp.passed());
        assertEquals(route.content(), stamp.content());
        assertEquals(save.getLast().result().content(), route.content().toString());

        Files.writeString(dir.resolve("ehz.script"), "1 R\n");
        var refused = assertThrows(java.io.IOException.class, () -> run(new RouteEditorProtocol.Request(
                RouteEditorProtocol.Kind.REPLAY, manifest, edited, null, false, roms)));
        assertTrue(refused.getMessage().contains("rows"), "A manifest whose script no longer matches is refused");
    }

    @Test
    void aMissingRomIsReportedAsUnsupportedNotAsAPassOrACrash() throws Exception {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "4 R\n");
        var records = run(new RouteEditorProtocol.Request(RouteEditorProtocol.Kind.SAVE, manifest, "4 R\n",
                dir.resolve("ehz"), true, Map.of()));
        assertEquals(RouteEditorProtocol.Verdict.UNSUPPORTED, records.getLast().result().verdict());
        assertTrue(records.stream().noneMatch(r -> r.saved() != null));
    }
}
