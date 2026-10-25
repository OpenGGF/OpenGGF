package com.openggf.editor.stageplay;

import com.openggf.stageplay.StageplayDocument.Facing;

import com.openggf.control.InputHandler;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayGround;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import static com.openggf.editor.stageplay.StageplayWorkspace.Action.*;
import static com.openggf.stageplay.StageplayDocument.*;
import static com.openggf.stageplay.StageplayGround.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class TestStageplayGroundWorkspace {
    @TempDir Path root;

    private StageplayDocument source() {
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(120);
        return document.withActors(List.of(new Actor("hero", "Hero", "s2", "sonic", List.of(
                        playerKey(0, 100, 200, true, true), playerKey(10, 100, 200, true, false),
                        playerKey(30, 100, 200, true, true), playerKey(60, 300, 400, false, true),
                        playerKey(80, 300, 400, false, false)))))
                .withCamera(List.of(new CameraKey(0, 0, 0, 1, Interpolation.HOLD),
                        new CameraKey(60, 200, 200, 1, Interpolation.HOLD)))
                .withSceneChanges(List.of(new SceneChange("second", 60, document.set(), Transition.CUT, 0)))
                .withPuppets(List.of(new PuppetTrack("hero", false, 1, List.of())));
    }

    private static ActorKey playerKey(int frame, double x, double y, boolean flip, boolean visible) {
        return new ActorKey(frame, x, y, 0, -1, (flip ? Facing.LEFT : Facing.RIGHT), visible, 1, Interpolation.HOLD);
    }

    private static ObjectKey objectKey(int frame, ObjectMode mode, boolean visible) {
        return new ObjectKey(frame, 100, 200, 0, -1, Facing.LEFT, visible, Interpolation.HOLD, mode);
    }

    private static ObjectActor object(String id, int layout, List<ObjectKey> keys) {
        return new ObjectActor(id, id, "s2", 0, 0, 65, 0, layout, keys, List.of());
    }

    private StageplayDocument objects(ObjectActor... actors) {
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(120)
                .withObjectActors(List.of(actors));
    }

    private class Host implements StageplayWorkspace.Host {
        final StageplayDocument document;
        StageplayGround.Batch batch;
        StageplayDocument queriedDocument;
        Path queriedRoot;
        int queriedFrame, queries;
        Runnable duringQuery = () -> { };
        Function<Query, Answer> answer = q -> new Answer(q.id(), Outcome.FOUND,
                List.of(placement(q, 100), placement(q, 198)), List.of(), false);

        Host(StageplayDocument document) { this.document = document; }
        public StageplayDocument initialDocument() { return document; }
        public Path initialAssetRoot() { return root; }
        public Report ground(StageplayDocument document, Path root, int frame, Batch batch) {
            this.batch = batch; queriedDocument = document; queriedRoot = root; queriedFrame = frame; queries++;
            var result = answer.apply(batch.queries().getFirst());
            duringQuery.run();
            return new Report(new SceneFrame(com.openggf.stageplay.StageplayTimeline.sample(document, frame).set(), frame),
                    List.of(result), new Work(100, 0, 1, StopReason.NONE));
        }
    }

    private static Placement placement(Query query, int y) {
        if (query.performer() instanceof Visual visual)
            return new Placement(query.x(), y, y + visual.footOffset(), 0, new VisualSupport(0, 0, 0));
        return new Placement(query.x(), y, y + 19, 0, new PlayerTerrainProposal(Foot.LEFT, 0, 9, 19));
    }

    private StageplayWorkspace workspace(Host host, String actor) {
        var workspace = new StageplayWorkspace(host);
        workspace.activate(); workspace.selectActor(actor);
        return workspace;
    }

    private void query(StageplayWorkspace workspace) {
        workspace.invoke(PLACE_ON_FLOOR);
        assertTrue(workspace.menu().isOpen());
        workspace.menu().submit();
        assertNull(workspace.menu().error());
    }

    @Test void queryIsReadOnlyAndNearestPlacementMovesAllHeldKeysAsOneUndoStep() {
        var host = new Host(source()); var workspace = workspace(host, "hero");
        var before = workspace.controller().document();
        workspace.invoke(PLACE_ON_FLOOR);
        assertEquals("100", workspace.menu().values().get("x"));
        assertEquals("72", workspace.menu().values().get("top"));
        assertEquals("328", workspace.menu().values().get("bottom"));
        workspace.menu().setValue("x", "120"); workspace.menu().submit();
        assertNull(workspace.menu().error());
        assertEquals(before, workspace.controller().document()); assertFalse(workspace.controller().canUndo());
        var query = host.batch.queries().getFirst();
        assertEquals(new Player("s2", "sonic", 1), query.performer()); assertEquals("hero", query.cast());
        assertEquals(before, host.queriedDocument); assertEquals(root, host.queriedRoot); assertEquals(0, host.queriedFrame);

        // The second result is nearest; Enter alone must apply it without reordering the results.
        press(workspace, GLFW_KEY_ENTER);
        assertNull(workspace.menu().error());
        var after = workspace.controller().document();
        var original = before.actors().getFirst().keys(); var actual = after.actors().getFirst().keys();
        for (int i = 0; i < actual.size(); i++) {
            var key = original.get(i);
            assertEquals(key.frame() < 60 ? playerKey(key.frame(), 120, 198, key.facing() == Facing.LEFT, key.visible()) : key, actual.get(i));
        }
        assertEquals(before.puppets(), after.puppets()); assertTrue(workspace.controller().canUndo());
        workspace.invoke(UNDO); assertEquals(before, workspace.controller().document()); assertFalse(workspace.controller().canUndo());
        workspace.invoke(REDO); assertEquals(after, workspace.controller().document());
    }

    @Test void resultsRemainTopFirstAndAnotherPlacementCanBeChosen() {
        var host = new Host(source()); var workspace = workspace(host, "hero"); query(workspace);
        press(workspace, GLFW_KEY_UP); press(workspace, GLFW_KEY_ENTER);
        assertNull(workspace.menu().error());
        assertEquals(100, workspace.controller().document().actors().getFirst().keys().getFirst().y());
    }

    @Test void laterShotPlacementDoesNotMovePreviousShotKeys() {
        var host = new Host(source());
        host.answer = q -> new Answer(q.id(), Outcome.FOUND, List.of(placement(q, 398)), List.of(), false);
        var workspace = workspace(host, "hero"); workspace.seek(60); var before = workspace.controller().document();
        query(workspace); press(workspace, GLFW_KEY_ENTER);
        assertNull(workspace.menu().error());
        var keys = workspace.controller().document().actors().getFirst().keys();
        assertEquals(before.actors().getFirst().keys().subList(0, 3), keys.subList(0, 3));
        assertEquals(playerKey(60, 300, 398, false, true), keys.get(3));
        assertEquals(playerKey(80, 300, 398, false, false), keys.get(4));
    }

    @Test void interiorPhysicalPlayerPlacementIsRefusedBeforeQuerying() {
        var host = new Host(source()); var workspace = workspace(host, "hero"); workspace.seek(10);
        workspace.invoke(PLACE_ON_FLOOR);
        assertEquals(0, host.queries); assertTrue(workspace.status().contains("shot boundary"));
        assertEquals(host.document, workspace.controller().document()); assertFalse(workspace.controller().canUndo());
    }

    @Test void freeAndReleaseToFreeObjectsAreRefusedWithinTheShot() {
        for (var keys : List.of(List.of(objectKey(0, ObjectMode.FREE, true)),
                List.of(objectKey(0, ObjectMode.PUPPET, true), objectKey(30, ObjectMode.FREE, true)))) {
            var host = new Host(objects(object("prop", -1, keys))); var workspace = workspace(host, "prop");
            workspace.invoke(PLACE_ON_FLOOR);
            assertEquals(0, host.queries); assertTrue(workspace.status().contains("FREE"));
            assertEquals(host.document, workspace.controller().document()); assertFalse(workspace.controller().canUndo());
        }
    }

    @Test void aCompletedNativeFreeInitializationPrefixRetainsItsAnchorWhenDirectingLater() {
        var free = objectKey(0,ObjectMode.FREE,true);
        var directed = objectKey(8,ObjectMode.PUPPET,true);
        var host = new Host(objects(object("prop",-1,List.of(free,directed))));
        var workspace = workspace(host,"prop"); workspace.seek(8); query(workspace);
        assertEquals(1,host.queries); press(workspace,GLFW_KEY_ENTER); assertNull(workspace.menu().error());
        var keys = workspace.controller().document().objectActors().getFirst().keys();
        assertEquals(free,keys.getFirst()); assertEquals(198,keys.getLast().y());
        workspace.invoke(UNDO); assertEquals(host.document,workspace.controller().document());
    }

    @Test void duplicatePlacementBindingsAreRejectedByTheDocumentBeforeTheyCanReachApply() {
        for (boolean visible : List.of(false,true)) {
            var failure = assertThrows(IllegalArgumentException.class,() -> objects(
                    object("prop",7,List.of(objectKey(0,ObjectMode.POSE,true),objectKey(20,ObjectMode.POSE,false))),
                    object("duplicate",7,List.of(objectKey(0,ObjectMode.POSE,visible),objectKey(30,ObjectMode.POSE,true)))));
            assertTrue(failure.getMessage().contains("Duplicate placed object identity"));
        }
    }

    @Test void distinctPlacementBindingsRemainAvailable() {
        var host = new Host(objects(object("prop",7,List.of(objectKey(0,ObjectMode.POSE,true))),
                object("other",8,List.of(objectKey(0,ObjectMode.POSE,true)))));
        var workspace = workspace(host,"prop"); query(workspace);
        assertEquals(1,host.queries); assertEquals(host.document,workspace.controller().document());
    }

    @Test void objectPlacementTransmitsExplicitFootprintAndPreservesPartsSourceKindAndCommands() {
        var parts = List.of(new ObjectPartPose("body/child", 3, 4));
        var key = new ObjectKey(0, 100, 200, 2, 5, Facing.RIGHT, true, Interpolation.LINEAR, ObjectMode.PUPPET, parts);
        var next = new ObjectKey(30, 120, 220, 6, 7, Facing.LEFT, false, Interpolation.HOLD, ObjectMode.POSE, parts);
        var commands = List.of(new ObjectCommand("move", 0, ObjectCommandKind.MOVE_TO, 300, 400, 1));
        var object = new ObjectActor("bird", "Bird", "s2", 0, 0, 0, 0, -1, List.of(key, next), commands, ObjectSourceKind.RELEASED_ANIMAL);
        var host = new Host(objects(object)); var workspace = workspace(host, "bird"); workspace.seek(10);
        workspace.invoke(PLACE_ON_FLOOR);
        workspace.menu().setValue("halfWidth", "12"); workspace.menu().setValue("height", "24");
        workspace.menu().setValue("footOffset", "10"); workspace.menu().setValue("collisionPath", "1");
        workspace.menu().submit(); assertNull(workspace.menu().error());
        assertEquals(new Visual(12, 24, 10, ObjectPath.ALTERNATE), host.batch.queries().getFirst().performer());
        assertEquals("bird", host.batch.queries().getFirst().cast());
        assertEquals(host.document, workspace.controller().document());
        press(workspace, GLFW_KEY_ENTER); assertNull(workspace.menu().error());
        var actual = workspace.controller().document().objectActors().getFirst();
        assertEquals(new ObjectActor(object.id(), object.name(), object.gameId(), object.zone(), object.act(),
                object.objectId(), object.subtype(), object.layoutIndex(), List.of(key,
                new ObjectKey(10, 100, 198, 2, 5, Facing.RIGHT, true, Interpolation.LINEAR, ObjectMode.PUPPET, parts), next),
                commands, ObjectSourceKind.RELEASED_ANIMAL), actual);
        workspace.invoke(UNDO); assertEquals(host.document, workspace.controller().document());
        assertFalse(workspace.controller().canUndo());
    }

    @Test void staleDocumentSelectionPlayheadAndRootCannotApplyIncludingChangesThatReturnToOriginalState() throws Exception {
        for (String change : List.of("document", "edit-undo", "undo-redo", "selection", "selection-back", "frame", "frame-back",
                "replace-equal", "play-pause", "advance-back", "root")) {
            var host = new Host(source()); var workspace = workspace(host, "hero");
            if (change.equals("undo-redo")) workspace.controller().edit(d -> d.withName("Prepared"));
            query(workspace);
            switch (change) {
                case "document" -> workspace.controller().edit(d -> d.withName("Changed"));
                case "edit-undo" -> { workspace.controller().edit(d -> d.withName("Changed")); workspace.controller().undo(); }
                case "undo-redo" -> { workspace.controller().undo(); workspace.controller().redo(); }
                case "selection" -> workspace.selectActor("");
                case "selection-back" -> { workspace.selectActor(""); workspace.selectActor("hero"); }
                case "frame" -> workspace.seek(1);
                case "frame-back" -> { workspace.seek(1); workspace.seek(0); }
                case "replace-equal" -> { workspace.controller().replace(host.document); workspace.selectActor("hero"); }
                case "play-pause" -> { workspace.controller().play(); workspace.controller().pause(); }
                case "advance-back" -> { workspace.controller().play(); workspace.controller().advance(); workspace.seek(0); }
                case "root" -> workspace.saveTo(root.resolve("moved/scene.stageplay"));
                default -> fail(change);
            }
            var expected = workspace.controller().document(); boolean canUndo = workspace.controller().canUndo();
            press(workspace, GLFW_KEY_ENTER);
            assertEquals(expected, workspace.controller().document(), change);
            assertEquals(canUndo, workspace.controller().canUndo(), change);
            assertNotNull(workspace.menu().error(), change); assertTrue(workspace.menu().error().contains("Stale ground"), change);
        }
    }

    @Test void noOpControllerOperationsDoNotInvalidateAPlacement() {
        var host = new Host(source()); var workspace = workspace(host, "hero"); query(workspace);
        long generation = StageplayGround.generation(workspace.controller());
        workspace.controller().edit(d -> d.withName(d.name())); workspace.selectActor("hero"); workspace.seek(0);
        workspace.controller().markSaved();
        assertEquals(generation, StageplayGround.generation(workspace.controller()));
        press(workspace, GLFW_KEY_ENTER); assertNull(workspace.menu().error());
        assertEquals(198, workspace.controller().document().actors().getFirst().keys().getFirst().y());
    }

    @Test void mutationsDuringQueryOrBeforeFormSubmissionAreRejected() {
        var host = new Host(source()); var workspace = workspace(host, "hero");
        host.duringQuery = () -> { workspace.seek(1); workspace.seek(0); };
        workspace.invoke(PLACE_ON_FLOOR); workspace.menu().submit();
        assertNotNull(workspace.menu().error()); assertTrue(workspace.menu().error().contains("Stale ground"));
        assertEquals(host.document, workspace.controller().document());

        workspace.invoke(PLACE_ON_FLOOR); workspace.selectActor(""); workspace.selectActor("hero"); workspace.menu().submit();
        assertEquals(1, host.queries); assertTrue(workspace.menu().error().contains("Stale ground"));
        assertFalse(workspace.controller().canUndo());
    }

    @Test void incompleteAndTruncatedPlacementsStayReviewOnly() {
        for (boolean truncated : List.of(false, true)) {
            var host = new Host(source());
            host.answer = q -> new Answer(q.id(), truncated ? Outcome.FOUND : Outcome.INCOMPLETE,
                    List.of(placement(q, 100), placement(q, 198)), List.of(), truncated);
            var workspace = workspace(host, "hero"); query(workspace);
            // The initial focus is Query another band, not an arbitrary partial placement.
            press(workspace, GLFW_KEY_UP); press(workspace, GLFW_KEY_ENTER);
            assertTrue(workspace.menu().error().contains("complete ground query"));
            assertEquals(host.document, workspace.controller().document()); assertFalse(workspace.controller().canUndo());
        }
    }

    @Test void mismatchedEvidenceAndOutOfBandSurfacesCannotApply() {
        for (boolean wrongEvidence : List.of(false, true)) {
            var host = new Host(source());
            host.answer = q -> new Answer(q.id(), Outcome.FOUND, List.of(new Placement(q.x(), 198,
                    wrongEvidence ? 217 : q.bottom() + 1, 0,
                    wrongEvidence ? new VisualSupport(0, 0, 0) : new PlayerTerrainProposal(Foot.LEFT, 0, 9, 19))), List.of(), false);
            var workspace = workspace(host, "hero"); query(workspace); press(workspace, GLFW_KEY_ENTER);
            assertTrue(workspace.menu().error().contains("matching terrain evidence"));
            assertEquals(host.document, workspace.controller().document()); assertFalse(workspace.controller().canUndo());
        }
    }

    @Test void typedRefusalsCanBeReviewedWithoutChangingTheDocument() {
        var host = new Host(source());
        host.answer = q -> new Answer(q.id(), Outcome.FOUND, List.of(placement(q, 198)),
                List.of(new Refusal(240, Reason.OBSTRUCTED, "Standing ceiling is blocked")), false);
        var workspace = workspace(host, "hero"); query(workspace);
        press(workspace, GLFW_KEY_DOWN); press(workspace, GLFW_KEY_ENTER);
        assertEquals("Floor unavailable",workspace.menu().title()); assertTrue(workspace.menu().detailsText().contains("OBSTRUCTED")); assertEquals(host.document, workspace.controller().document());
        press(workspace, GLFW_KEY_ENTER); assertEquals("Floor placements",workspace.menu().title());
        assertFalse(workspace.controller().canUndo());
    }

    @Test void unavailableHostsReportAnErrorWithoutAnEdit() {
        var document = source();
        var workspace = new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return document; }
            public Path initialAssetRoot() { return root; }
        });
        workspace.activate(); workspace.selectActor("hero"); workspace.invoke(PLACE_ON_FLOOR); workspace.menu().submit();
        assertNotNull(workspace.menu().error()); assertTrue(workspace.menu().error().contains("unavailable"));
        assertEquals(document, workspace.controller().document()); assertFalse(workspace.controller().canUndo());
    }

    private static void press(StageplayWorkspace workspace, int key) {
        var input = new InputHandler();
        input.handleKeyEvent(key, GLFW_PRESS); input.refreshLogicalSnapshot(); workspace.menu().update(input); input.update();
        input.handleKeyEvent(key, GLFW_RELEASE); input.update();
    }
}
