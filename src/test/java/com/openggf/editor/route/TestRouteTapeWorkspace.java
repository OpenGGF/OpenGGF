package com.openggf.editor.route;

import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.route.ContentHash;
import com.openggf.route.GoalProgram;
import com.openggf.route.LevelId;
import com.openggf.route.NativeFacts;
import com.openggf.route.QualificationStamp;
import com.openggf.route.ReplayScope;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;
import com.openggf.route.RouteFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The shared route tape model: files through the facade, row edits, qualification truth and jobs. */
class TestRouteTapeWorkspace {
    @TempDir
    Path dir;

    /** A host whose job completes when the test says so. */
    private static final class Host implements RouteTapeWorkspace.Host {
        final List<RouteTapeJob.Request> requests = new ArrayList<>();
        final AtomicReference<RouteTapeJob.Update> update = new AtomicReference<>();
        Optional<LevelId> level = Optional.empty();
        final List<int[]> focus = new ArrayList<>();
        String content;
        int rows;

        @Override
        public void startJob(RouteTapeJob.Request request) {
            requests.add(request);
            this.content = request.route().content().toString();
            this.rows = request.route().tape().size();
            update.set(new RouteTapeJob.Update(requests.size(), RouteTapeJob.Phase.RUNNING, request.kind(), 1, 2, 0, rows,
                    "running", content, -1, null, null));
        }

        @Override
        public RouteTapeJob.Update pollJob() {
            return update.get();
        }

        @Override
        public void cancelJob() {
            update.set(null);
        }

        @Override
        public Optional<LevelId> currentLevel() {
            return level;
        }

        @Override
        public void focusWorld(int x, int y) {
            focus.add(new int[] {x, y});
        }

        void finish(RouteTapeJob.Phase phase, int row, Path saved, RouteTapeOverlay path) {
            update.set(new RouteTapeJob.Update(requests.size(), phase, requests.getLast().kind(), 2, 2, rows, rows,
                    phase == RouteTapeJob.Phase.FAILED ? "fell" : "openggf test", content, row, path, saved));
        }
    }

    private static void tick(RouteTapeWorkspace workspace) {
        workspace.tick(new InputHandler());
    }

    @Test
    void openEditUndoAndDirtyFollowTheWorkingRowsOnly() throws IOException {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "30 R\n5 R+A\n");
        var workspace = new RouteTapeWorkspace(new Host());
        workspace.open(manifest);
        assertTrue(workspace.active());
        assertEquals(35, workspace.tape().size());
        assertFalse(workspace.dirty());
        assertEquals(RouteTapeWorkspace.Qualification.UNCHECKED, workspace.qualification());

        workspace.setTrack(RouteTapeTimeline.TRACKS.indexOf(new RouteTapeTimeline.Track(PlayerSlot.P2, PhysicalButton.C)));
        workspace.setCursor(3);
        workspace.invoke(RouteTapeWorkspace.Action.MARK);
        workspace.setCursor(5);
        workspace.invoke(RouteTapeWorkspace.Action.TOGGLE);
        for (int row = 0; row < 35; row++) {
            assertEquals(row >= 3 && row <= 5, workspace.tape().row(row).held(PlayerSlot.P2, PhysicalButton.C), "row " + row);
            assertTrue(workspace.tape().row(row).held(PlayerSlot.P1, PhysicalButton.RIGHT));
        }
        assertTrue(workspace.dirty());
        assertEquals(RouteTapeWorkspace.Qualification.EDITED, workspace.qualification());
        workspace.invoke(RouteTapeWorkspace.Action.UNDO);
        assertFalse(workspace.dirty());
        workspace.invoke(RouteTapeWorkspace.Action.REDO);
        assertTrue(workspace.dirty());
        workspace.invoke(RouteTapeWorkspace.Action.DELETE_ROWS);
        assertEquals(32, workspace.tape().size());
        assertEquals(3, workspace.cursor());
        workspace.insertRows(4, true);
        assertEquals(36, workspace.tape().size());
        assertTrue(workspace.tape().row(3).isNeutral());
        assertEquals(35, RouteFiles.readDraft(manifest).tape().size(), "Edits never touch the file");

        workspace.invoke(RouteTapeWorkspace.Action.OPEN);
        assertTrue(workspace.menu().isOpen(), "Unsaved edits ask before another route replaces them");
        assertEquals(2, workspace.menu().actionEntries().size());
        assertNotNull(workspace.menu().actionView(), "Route prompts publish a structured view for native front ends");
        workspace.menu().close();
        workspace.invoke(RouteTapeWorkspace.Action.INSERT_ROWS);
        assertNotNull(workspace.menu().formView(), "Route forms publish a structured view for native front ends");
    }

    @Test
    void replayResultsBelongToTheContentTheyReplayedAndSaveIsAQualifiedWorkerJob() throws IOException {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "10 R\n");
        var host = new Host();
        var workspace = new RouteTapeWorkspace(host);
        workspace.open(manifest);
        workspace.invoke(RouteTapeWorkspace.Action.REPLAY);
        tick(workspace);
        assertTrue(workspace.jobRunning());
        var request = host.requests.getFirst();
        assertEquals(RouteTapeJob.Kind.REPLAY, request.kind());
        assertEquals(workspace.tape(), request.route().tape());
        assertEquals(RouteFiles.readDraft(manifest).launch(), request.route().launch());
        assertEquals(workspace.workingContent(), host.content);
        workspace.invoke(RouteTapeWorkspace.Action.REPLAY);
        assertEquals(1, host.requests.size(), "One job at a time");

        var path = new RouteTapeOverlay(List.of(new RouteTapeOverlay.Point(0, 0, 50, 60, false),
                new RouteTapeOverlay.Point(6, 0, 70, 60, false)), List.of(), 1, 6);
        host.finish(RouteTapeJob.Phase.FAILED, 6, null, path);
        tick(workspace);
        assertEquals(RouteTapeWorkspace.Qualification.NOT_QUALIFIED, workspace.qualification());
        assertEquals(6, workspace.divergedRow());
        assertEquals(6, workspace.cursor(), "A failed replay moves the cursor to its first diverging row");
        assertTrue(workspace.timeline().markers().stream()
                .anyMatch(marker -> marker.kind() == RouteTapeTimeline.MarkerKind.DIVERGED && marker.from() == 6));
        assertTrue(host.focus.isEmpty(), "No level-view focus while the editor shows another level");

        workspace.setTrack(0);
        workspace.invoke(RouteTapeWorkspace.Action.TOGGLE);
        assertEquals(RouteTapeWorkspace.Qualification.EDITED, workspace.qualification(),
                "A verdict for other content no longer describes the rows");
        assertTrue(workspace.pathStale());
        assertEquals(-1, workspace.divergedRow());
        workspace.invoke(RouteTapeWorkspace.Action.UNDO);
        assertEquals(RouteTapeWorkspace.Qualification.NOT_QUALIFIED, workspace.qualification());

        workspace.invoke(RouteTapeWorkspace.Action.SAVE);
        var save = host.requests.getLast();
        assertEquals(RouteTapeJob.Kind.SAVE, save.kind());
        assertEquals(dir.resolve("ehz"), save.target());
        assertTrue(save.replace());
        host.finish(RouteTapeJob.Phase.PASSED, 10, null, path);
        tick(workspace);
        assertEquals(RouteTapeWorkspace.Qualification.PASSES_UNSAVED, workspace.qualification(),
                "A pass without a stamp in the file is not a saved qualification");
    }

    @Test
    void qualifiedStampsNativeFactsAndLagMarkersAreReadFromTheManifestForTheWorkingContent() throws IOException {
        var other = new ContentHash("b".repeat(64));
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "10 R\n", GoalProgram.NONE, false,
                content -> List.of(QualificationStamp.passed(ReplayScope.ENGINE, content, "test", 10),
                        QualificationStamp.diverged(ReplayScope.BIZHAWK, 7, "x", content, "bizhawk 2.11", 10)),
                content -> new NativeFacts(Optional.of(new NativeFacts.NativeEntry("native/ehz.bk2", 420,
                        new ContentHash("a".repeat(64)))), List.of(new NativeFacts.LagFact(4, "tracechaser 9", content),
                        new NativeFacts.LagFact(6, "tracechaser 8", other))));
        var host = new Host();
        var workspace = new RouteTapeWorkspace(host);
        workspace.open(manifest);
        assertEquals(RouteTapeWorkspace.Qualification.QUALIFIED, workspace.qualification());
        var facts = workspace.nativeFacts();
        assertTrue(facts.get(0)[1].contains("native/ehz.bk2") && facts.get(0)[1].contains("420"));
        assertTrue(facts.get(1)[1].contains("2") && facts.get(1)[1].contains("1"), facts.get(1)[1]);
        assertEquals("Engine-qualified, native-diverged at row 7: x", facts.get(2)[1]);
        var lag = workspace.timeline().markers().stream()
                .filter(marker -> marker.kind() == RouteTapeTimeline.MarkerKind.NATIVE_LAG).toList();
        assertEquals(1, lag.size(), "Lag recorded against other content is not drawn on these rows");
        assertEquals(4, lag.getFirst().from());
        assertEquals(List.of(4, 7), workspace.factRows(), "Native lag and the native divergence are both facts");
        assertFalse(workspace.needsNativeCheck());
        workspace.invoke(RouteTapeWorkspace.Action.NEXT_FACT);
        assertEquals(4, workspace.cursor());
        workspace.invoke(RouteTapeWorkspace.Action.NEXT_FACT);
        assertEquals(7, workspace.cursor());

        workspace.invoke(RouteTapeWorkspace.Action.TOGGLE);
        assertEquals(RouteTapeWorkspace.Qualification.EDITED, workspace.qualification());
        assertTrue(workspace.needsNativeCheck(), "Edited rows need a fresh native check");
        assertEquals("Needed for these rows", workspace.nativeFacts().getLast()[1]);
        assertEquals("Diverged at row 7: x (other content)", workspace.nativeFacts().get(2)[1],
                "The native verdict stays visible, marked as describing other content");
        assertTrue(workspace.timeline().markers().isEmpty());
    }

    @Test
    void draftsSaveInProcessReopenAsDraftsAndNeverReplaceAQualifiedRoute() throws IOException {
        var workspace = new RouteTapeWorkspace(new Host());
        workspace.create(dir.resolve("fresh.route.json"), RouteTapeFixtures.ehz(), 30);
        assertTrue(workspace.dirty(), "A new route is unsaved");
        assertEquals(RouteTapeWorkspace.Qualification.EDITED, workspace.qualification());
        assertEquals(30, workspace.tape().size());
        assertTrue(workspace.tape().row(29).isNeutral());
        workspace.setTrack(RouteTapeTimeline.TRACKS.indexOf(new RouteTapeTimeline.Track(PlayerSlot.P1, PhysicalButton.RIGHT)));
        workspace.invoke(RouteTapeWorkspace.Action.TOGGLE);
        workspace.invoke(RouteTapeWorkspace.Action.SAVE_DRAFT);
        assertFalse(workspace.dirty());
        assertEquals(RouteTapeWorkspace.Qualification.DRAFT, workspace.qualification());
        Path draft = dir.resolve("fresh.route.json");
        assertTrue(RouteFiles.readDraft(draft).manifest().draft());
        assertThrows(IOException.class, () -> RouteFiles.read(draft), "Consumers refuse drafts");
        var reopenedHost = new Host();
        var reopened = new RouteTapeWorkspace(reopenedHost);
        reopened.open(draft);
        assertEquals(RouteTapeWorkspace.Qualification.DRAFT, reopened.qualification());
        assertTrue(reopened.tape().row(0).held(PlayerSlot.P1, PhysicalButton.RIGHT));
        reopened.invoke(RouteTapeWorkspace.Action.SAVE);
        var save = reopenedHost.requests.getLast();
        assertTrue(save.replace(), "A qualified save replaces the draft on disk");

        Path qualified = RouteTapeFixtures.write(dir, "ehz", "10 R\n", GoalProgram.NONE, false,
                content -> List.of(QualificationStamp.passed(ReplayScope.ENGINE, content, "test", 10)),
                content -> NativeFacts.NONE);
        var edited = new RouteTapeWorkspace(new Host());
        edited.open(qualified);
        edited.invoke(RouteTapeWorkspace.Action.TOGGLE);
        edited.invoke(RouteTapeWorkspace.Action.SAVE_DRAFT);
        assertTrue(edited.dirty(), "A draft never replaces the qualified route");
        assertTrue(RouteFiles.read(qualified).engineQualified());
        assertThrows(IllegalArgumentException.class,
                () -> workspace.create(dir.resolve("plain.json"), RouteTapeFixtures.ehz(), 1));
    }

    @Test
    void solvedRepairsAdoptAsOneUndoStepWithSpansAndStaleOrStoppedOnesNeverReplaceRows() throws IOException {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "40 R\n");
        var host = new Host();
        var workspace = new RouteTapeWorkspace(host);
        workspace.open(manifest);
        workspace.setCursor(20);
        workspace.invoke(RouteTapeWorkspace.Action.REPAIR);
        assertNotNull(workspace.menu().formView());
        workspace.menu().setValue("authored", "90");
        workspace.menu().submit();
        assertNull(workspace.menu().error(), workspace.menu().error());
        var request = host.requests.getLast();
        assertEquals(RouteTapeJob.Kind.REPAIR, request.kind());
        assertEquals(new RouteTapeJob.RepairBounds(20, 90, 24, 20, -1), request.repair());
        assertEquals(RouteFiles.readDraft(manifest), request.reference(), "This route's saved rows are the reference");
        String original = workspace.workingContent();

        var repaired = RouteFiles.tapeFromScript("20 R\n6 R+A\n14 R\n");
        var outcome = new RouteTapeJob.RepairOutcome(true, repaired, List.of(
                new RouteTapeJob.RepairSpan(RouteTapeTimeline.MarkerKind.KEPT, 0, 0, 20),
                new RouteTapeJob.RepairSpan(RouteTapeTimeline.MarkerKind.SEARCHED, -1, 20, 6),
                new RouteTapeJob.RepairSpan(RouteTapeTimeline.MarkerKind.KEPT, 26, 26, 14)), 40, "kept 34 of 40");
        // The planner's solved artifact: the original launch over the repaired rows, no goal program.
        String solvedContent = com.openggf.route.RouteArtifact.of(RouteFiles.readDraft(manifest).launch(), repaired,
                new com.openggf.route.Provenance("planner", "", "", List.of())).content().toString();
        host.update.set(new RouteTapeJob.Update(1, RouteTapeJob.Phase.SOLVED, RouteTapeJob.Kind.REPAIR, 2, 2, 1, 1,
                "kept 34 of 40", solvedContent, 40, null, null, outcome));
        tick(workspace);
        assertEquals(repaired, workspace.tape(), "A solved repair's rows replace the working rows");
        assertEquals(RouteTapeWorkspace.Qualification.PASSES_UNSAVED, workspace.qualification(),
                "The planner's cold replay qualified exactly these rows; they are not saved yet");
        assertTrue(workspace.repairText().contains("34") && workspace.repairText().contains("85%"), workspace.repairText());
        assertTrue(workspace.timeline().markers().stream().anyMatch(m -> m.kind() == RouteTapeTimeline.MarkerKind.SEARCHED
                && m.from() == 20 && m.to() == 26));
        workspace.invoke(RouteTapeWorkspace.Action.UNDO);
        assertEquals(original, workspace.workingContent(), "Adopting a repair is one undo step");
        assertFalse(workspace.repairCurrent());
        assertTrue(workspace.timeline().markers().isEmpty(), "Spans describe only the repaired rows");

        workspace.invoke(RouteTapeWorkspace.Action.REDO);
        host.update.set(null);
        workspace.startRepair(RouteFiles.readDraft(manifest), new RouteTapeJob.RepairBounds(0, 10, 1, 0, -1));
        workspace.setTrack(0);
        workspace.invoke(RouteTapeWorkspace.Action.TOGGLE);
        var editedRows = workspace.tape();
        host.update.set(new RouteTapeJob.Update(2, RouteTapeJob.Phase.SOLVED, RouteTapeJob.Kind.REPAIR, 2, 2, 1, 1,
                "kept", solvedContent, 40, null, null, outcome));
        tick(workspace);
        assertEquals(editedRows, workspace.tape(), "Rows edited while a repair ran are never replaced");

        host.update.set(null);
        workspace.startRepair(RouteFiles.readDraft(manifest), new RouteTapeJob.RepairBounds(0, 10, 1, 0, -1));
        var attempt = RouteFiles.tapeFromScript("12 R\n");
        host.update.set(new RouteTapeJob.Update(3, RouteTapeJob.Phase.STOPPED, RouteTapeJob.Kind.REPAIR, 2, 2, 1, 1,
                "BUDGET_EXHAUSTED: no progress", workspace.workingContent(), 12, null, null,
                new RouteTapeJob.RepairOutcome(false, attempt, List.of(), 0, "BUDGET_EXHAUSTED: no progress")));
        tick(workspace);
        assertEquals(editedRows, workspace.tape(), "A stopped repair changes nothing by itself");
        assertTrue(workspace.repairText().contains("row 12") && workspace.repairText().contains("BUDGET_EXHAUSTED"),
                workspace.repairText());
        workspace.invoke(RouteTapeWorkspace.Action.USE_ATTEMPT);
        assertEquals(attempt, workspace.tape(), "The best attempt can be inspected as working rows");
        assertEquals(RouteTapeWorkspace.Qualification.EDITED, workspace.qualification());
    }

    @Test
    void sceneRowsRefuseRouteFileCommandsAndNeverReplaceAnUnsavedRoute() throws IOException {
        var workspace = new RouteTapeWorkspace(new Host());
        var applied = new java.util.ArrayList<com.openggf.route.InputTape>();
        var rows = new RouteTapeWorkspace.SceneRows("Hero inputs", "Frames 10 to 43",
                RouteFiles.tapeFromScript("34 R\n"), applied::add, () -> { });
        workspace.open(RouteTapeFixtures.write(dir, "ehz", "10 R\n"));
        workspace.invoke(RouteTapeWorkspace.Action.TOGGLE);
        assertThrows(IllegalStateException.class, () -> workspace.openSceneRows(rows),
                "Unsaved route rows are never discarded by opening scene rows");
        workspace.invoke(RouteTapeWorkspace.Action.UNDO);
        workspace.openSceneRows(rows);
        assertNull(workspace.manifestPath());
        for (var action : List.of(RouteTapeWorkspace.Action.SAVE, RouteTapeWorkspace.Action.SAVE_DRAFT,
                RouteTapeWorkspace.Action.REPAIR, RouteTapeWorkspace.Action.EXPORT_BK2)) {
            workspace.menu().close();
            workspace.invoke(action);
            assertTrue(workspace.status().contains("Scene inputs"), action + ": " + workspace.status());
        }
        workspace.menu().close();
        workspace.invoke(RouteTapeWorkspace.Action.APPLY_ROWS);
        assertEquals(List.of(RouteFiles.tapeFromScript("34 R\n")), applied);
    }

    @Test
    void pathFollowsTheCursorOnlyOnTheRouteLevelAndExportWritesALoadableMovieWithoutOverwriting() throws IOException {
        Path manifest = RouteTapeFixtures.write(dir, "ehz", "3 R\n2 R+A / L\n");
        var host = new Host();
        var workspace = new RouteTapeWorkspace(host);
        workspace.open(manifest);
        workspace.invoke(RouteTapeWorkspace.Action.REPLAY);
        var points = new ArrayList<RouteTapeOverlay.Point>();
        for (int row = 0; row < 5; row++) points.add(new RouteTapeOverlay.Point(row, 0, 100 + row, 200, row >= 3));
        host.finish(RouteTapeJob.Phase.PASSED, 5, null, new RouteTapeOverlay(points, List.of(), 1, -1));
        tick(workspace);
        workspace.setCursor(2);
        assertTrue(host.focus.isEmpty());
        assertFalse(workspace.levelMatches());
        var painted = new ArrayList<Integer>();
        workspace.paintOverlay((x1, y1, x2, y2, rgb, width) -> painted.add(rgb), new RouteTapeOverlay.Camera(0, 0, 0, 0, 1));
        assertTrue(painted.isEmpty(), "The path is never drawn over a different level");
        host.level = Optional.of(new LevelId("s2", 0, 0));
        workspace.setCursor(3);
        assertArrayEquals(new int[] {103, 200}, host.focus.getLast());
        workspace.paintOverlay((x1, y1, x2, y2, rgb, width) -> painted.add(rgb), new RouteTapeOverlay.Camera(0, 0, 0, 0, 1));
        assertTrue(painted.contains(RouteTapeOverlay.AIR));

        Path movie = workspace.exportBk2(dir.resolve("out/ehz.bk2"));
        var frames = new Bk2MovieLoader().loadMovieOrInputLog(movie).getFrames();
        assertEquals(5, frames.size());
        assertEquals(workspace.tape().row(4), com.openggf.route.PadRow.fromEngine(frames.get(4)));
        assertThrows(IOException.class, () -> workspace.exportBk2(movie));
        assertFalse(Files.exists(dir.resolve("ehz.bk2")), "Export never writes a third committed copy beside the route");
    }
}
