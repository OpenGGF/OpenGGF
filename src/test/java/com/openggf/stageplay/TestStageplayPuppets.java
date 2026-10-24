package com.openggf.stageplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayPuppets {
    private static ActorKey key(int frame, double x, boolean visible) {
        return new ActorKey(frame, x, 200, 0, -1, false, visible, 1, Interpolation.HOLD);
    }
    private static StageplayDocument manual() {
        return StageplayDocument.empty("s2", 0, 0, 0, 0, 320, 224).withSet(new SetSource("s2",0,0,"","")).withActors(List.of(
                new Actor("hero", "Hero", "s2", "sonic", List.of(key(0, 100, true)))));
    }
    private static PuppetCommand move(int frame) {
        return new PuppetCommand("go", frame, PuppetCommandKind.MOVE_TO, 400, 200, .6, 0);
    }
    private static PuppetTrack track(PuppetCommand... commands) {
        return new PuppetTrack("hero", true, 0, List.of(commands));
    }

    @Test void manualChoreographyRoundTripsWithoutCreatingPhysicsTracks() throws IOException {
        var scene = manual().withActors(List.of(new Actor("hero", "Hero", "s2", "sonic", List.of(
                new ActorKey(0, 100, 200, 2, -1, false, true, 1, Interpolation.LINEAR),
                new ActorKey(30, 200, 50, 2, -1, false, true, 1, Interpolation.LINEAR)))));
        var decoded = StageplayStore.decode(StageplayStore.encode(scene));
        assertTrue(decoded.puppets().isEmpty());
        assertEquals(125, StageplayTimeline.sample(decoded, 15).actors().getFirst().y());
        assertEquals(scene, decoded);
    }

    @Test void tracksAreImmutableAndSurviveAllExistingMutators() throws IOException {
        var commands = new ArrayList<>(List.of(move(0)));
        var track = new PuppetTrack("hero", true, 1, commands);
        var document = manual().withPuppets(List.of(track)); commands.clear();
        var edited = document.withName("New name").withSet(document.set()).withSize(640, 448)
                .withActors(document.actors()).withCamera(document.camera()).withDurationFrames(601)
                .withCaptions(List.of()).withAudio(List.of()).withCameraEffects(List.of()).withSceneChanges(List.of())
                .withDialogue(List.of()).withDialogueSkins(List.of()).withDialogueTypers(List.of());
        assertEquals(List.of(track), edited.puppets());
        assertEquals(1, track.commands().size());
        assertThrows(UnsupportedOperationException.class, () -> track.commands().clear());
        assertEquals(document, StageplayStore.decode(StageplayStore.encode(document)));
    }

    @Test void moveAndJumpCanShareAFrameButMovementOrJumpHoldsCannotConflict() {
        var jump = new PuppetCommand("jump", 0, PuppetCommandKind.JUMP, 0, 0, 0, 12);
        assertDoesNotThrow(() -> manual().withPuppets(List.of(track(move(0), jump))));
        assertThrows(IllegalArgumentException.class, () -> track(move(0),
                new PuppetCommand("stop", 0, PuppetCommandKind.STOP, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> track(jump,
                new PuppetCommand("jump2", 11, PuppetCommandKind.JUMP, 0, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> track(jump,
                new PuppetCommand("adjacent", 12, PuppetCommandKind.JUMP, 0, 0, 0, 1)),
                "Adjacent holds would collapse into one continuous native input");
        assertDoesNotThrow(() -> track(jump,
                new PuppetCommand("released", 13, PuppetCommandKind.JUMP, 0, 0, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> new PuppetCommand("bad", 0,
                PuppetCommandKind.MOVE_TO, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PuppetCommand("bad", 0,
                PuppetCommandKind.JUMP, 1, 0, 0, 12));
        assertThrows(IllegalArgumentException.class, () -> new PuppetCommand("bad", 0,
                PuppetCommandKind.MOVE_TO, Double.NaN, 0, .5, 0));
        assertThrows(IllegalArgumentException.class, () -> manual().withPuppets(List.of(track(
                new PuppetCommand("late", 599, PuppetCommandKind.JUMP, 0, 0, 0, 2)))));
    }

    @Test void physicalKeysAllowVisibilityButRejectPositionTweenAndMissingActors() {
        var hidden = manual().withActors(List.of(new Actor("hero", "Hero", "s2", "sonic",
                List.of(key(0, 100, true), key(20, 100, false))))).withPuppets(List.of(track(move(0))));
        assertFalse(StageplayTimeline.sample(hidden, 20).actors().getFirst().visible());
        assertThrows(IllegalArgumentException.class, () -> hidden.withActors(List.of(
                new Actor("hero", "Hero", "s2", "sonic", List.of(key(0, 100, true), key(20, 101, false))))));
        assertThrows(IllegalArgumentException.class, () -> hidden.withActors(List.of()));
        assertThrows(IllegalArgumentException.class, () -> manual().withPuppets(List.of(track(), track())));
        assertThrows(IllegalArgumentException.class, () -> manual().withPuppets(List.of(track(
                new PuppetCommand("hero", 0, PuppetCommandKind.STOP, 0, 0, 0, 0)))));
    }

    @Test void pureSamplingReportsUnresolvedIntentAndSceneSwitchClearsGoal() {
        var base = manual();
        var doc = new StageplayDocument(VERSION, "Two shots", 600, 320, 224, base.set(), List.of(
                new Actor("hero", "Hero", "s2", "sonic", List.of(key(0, 100, true), key(100, 500, true)))),
                List.of(base.camera().getFirst(), new CameraKey(100, 400, 0, 1, Interpolation.HOLD)),
                List.of(), List.of(), List.of(), List.of(new SceneChange("cut", 100, base.set(), Transition.CUT, 0)),
                List.of(), List.of(), List.of(), List.of(track(move(0))));
        var before = StageplayTimeline.sample(doc, 99);
        assertEquals("go", before.puppets().getFirst().commandId());
        assertEquals(StageplayTimeline.PuppetStatus.UNRESOLVED, before.puppets().getFirst().status());
        assertEquals(100, before.actors().getFirst().x());
        assertEquals("", StageplayTimeline.sample(doc, 100).puppets().getFirst().commandId());
        assertEquals(500, StageplayTimeline.sample(doc, 100).actors().getFirst().x());
        assertThrows(IllegalArgumentException.class, () -> doc.withPuppets(List.of(track(
                new PuppetCommand("jump", 95, PuppetCommandKind.JUMP, 0, 0, 0, 10)))));
    }
}
