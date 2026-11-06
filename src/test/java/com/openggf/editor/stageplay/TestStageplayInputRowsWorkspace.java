package com.openggf.editor.stageplay;

import com.openggf.control.InputHandler;
import com.openggf.editor.route.RouteTapeTimeline;
import com.openggf.editor.route.RouteTapeWorkspace;
import com.openggf.route.InputTape;
import com.openggf.route.PhysicalButton;
import com.openggf.route.PlayerSlot;
import com.openggf.route.RouteFiles;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.route.StageplayRoutes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

/** Stageplay frozen input rows open in the shared route tape editor and come back as one scene undo step. */
class TestStageplayInputRowsWorkspace {
    private static StageplayDocument scene() {
        var document = StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withDurationFrames(120)
                .withActors(List.of(new Actor("hero", "Hero", "s2", "sonic",
                        List.of(new ActorKey(0, 100, 200, 0, -1, Facing.RIGHT, true, 1, Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero", false, 0, List.of())));
        return StageplayRoutes.install(document, StageplayRoutes.track("route1", "hero", 10,
                RouteFiles.tapeFromScript("30 R\n4 R+A\n")));
    }

    @Test
    void inputRowsEditInTheRouteTimelineAndApplyAsOneSceneUndoStep() {
        var routes = new RouteTapeWorkspace(new RouteTapeWorkspace.Host() { });
        var opened = new AtomicReference<RouteTapeWorkspace.SceneRows>();
        var stageplay = new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return scene(); }
            public void editInputRows(RouteTapeWorkspace.SceneRows rows) {
                opened.set(rows);
                routes.openSceneRows(rows);
            }
        });
        stageplay.activate();
        stageplay.selectActor("hero");
        var before = stageplay.controller().document();
        stageplay.invoke(StageplayWorkspace.Action.INPUT_ROWS);
        assertNotNull(opened.get(), stageplay.status());
        assertTrue(routes.active());
        assertEquals(RouteTapeWorkspace.Qualification.SCENE, routes.qualification());
        assertEquals(34, routes.tape().size());
        assertTrue(routes.launchFacts().get(1)[1].contains("10") && routes.launchFacts().get(1)[1].contains("43"));

        routes.setTrack(RouteTapeTimeline.TRACKS.indexOf(new RouteTapeTimeline.Track(PlayerSlot.P1, PhysicalButton.B)));
        routes.setCursor(5);
        routes.invoke(RouteTapeWorkspace.Action.TOGGLE);
        assertTrue(routes.dirty());
        routes.invoke(RouteTapeWorkspace.Action.REPLAY);
        assertTrue(routes.status().contains("Scene inputs"), "Scene rows are applied, not replayed as a route");
        assertEquals(before, stageplay.controller().document(), "Nothing reaches the scene before Apply");

        routes.invoke(RouteTapeWorkspace.Action.APPLY_ROWS);
        assertFalse(routes.active());
        assertTrue(stageplay.active(), "Apply returns to Stageplay");
        var track = stageplay.controller().document().inputTracks().getFirst();
        InputTape applied = StageplayRoutes.tape(track);
        assertTrue(applied.row(5).held(PlayerSlot.P1, PhysicalButton.B));
        assertEquals(before.inputTracks().getFirst().lanes(), track.lanes(), "Lane bindings are kept");
        stageplay.invoke(StageplayWorkspace.Action.UNDO);
        assertEquals(before, stageplay.controller().document(), "Applied rows are one scene undo step");
    }

    @Test
    void unappliedRowsAskBeforeLeavingAndAnActorWithoutRowsSaysSo() {
        var routes = new RouteTapeWorkspace(new RouteTapeWorkspace.Host() { });
        var stageplay = new StageplayWorkspace(new StageplayWorkspace.Host() {
            public StageplayDocument initialDocument() { return scene(); }
            public void editInputRows(RouteTapeWorkspace.SceneRows rows) { routes.openSceneRows(rows); }
        });
        stageplay.activate();
        stageplay.selectActor("hero");
        stageplay.invoke(StageplayWorkspace.Action.INPUT_ROWS);
        stageplay.deactivate();
        routes.invoke(RouteTapeWorkspace.Action.DELETE_ROWS);
        routes.invoke(RouteTapeWorkspace.Action.EXIT);
        assertTrue(routes.menu().isOpen(), "Unapplied rows ask before going back");
        routes.menu().close();
        assertTrue(routes.active());
        routes.invoke(RouteTapeWorkspace.Action.UNDO);
        routes.invoke(RouteTapeWorkspace.Action.EXIT);
        assertFalse(routes.active());
        assertTrue(stageplay.active(), "Unchanged rows go straight back");

        stageplay.tick(new InputHandler());
        var empty = new StageplayWorkspace(() -> StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224));
        empty.activate();
        empty.invoke(StageplayWorkspace.Action.INPUT_ROWS);
        assertTrue(empty.status().contains("no input rows"), "No rows is reported, not ignored: " + empty.status());
    }
}
