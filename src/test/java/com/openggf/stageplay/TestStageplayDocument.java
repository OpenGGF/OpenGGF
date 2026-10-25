package com.openggf.stageplay;

import com.openggf.stageplay.StageplayDocument.Facing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayDocument {
    static StageplayDocument scene() {
        return StageplayDocument.empty("s3k", 0, 0, 100, 200, 320, 224)
                .withSet(new SetSource("s3k", 0, 0, "", ""));
    }

    static ActorKey key(int frame, double x, int animation, int pose, Interpolation interpolation) {
        return new ActorKey(frame, x, 200, animation, pose, Facing.RIGHT, true, 1, interpolation);
    }

    static Actor actor(List<ActorKey> keys) {
        return new Actor("hero", "Hero", "s3k", "sonic", keys);
    }

    @Test
    void nestedListsAreCopiedAndReadOnly() {
        var keys = new ArrayList<>(List.of(key(0, 0, 0, -1, Interpolation.HOLD)));
        var actors = new ArrayList<>(List.of(actor(keys)));
        var document = scene().withActors(actors);
        keys.clear();
        actors.clear();
        assertEquals(1, document.actors().size());
        assertEquals(1, document.actors().getFirst().keys().size());
        assertThrows(UnsupportedOperationException.class, () -> document.actors().clear());
        assertThrows(UnsupportedOperationException.class, () -> document.actors().getFirst().keys().clear());
    }

    @Test
    void rejectsMissingInitialDuplicateAndOutOfDurationKeys() {
        assertThrows(IllegalArgumentException.class,
                () -> actor(List.of(key(1, 0, 0, -1, Interpolation.HOLD))));
        assertThrows(IllegalArgumentException.class,
                () -> actor(List.of(key(0, 0, 0, -1, Interpolation.HOLD), key(0, 1, 0, -1, Interpolation.HOLD))));
        var late = actor(List.of(key(0, 0, 0, -1, Interpolation.HOLD), key(600, 1, 0, -1, Interpolation.HOLD)));
        assertThrows(IllegalArgumentException.class, () -> scene().withActors(List.of(late)));
        assertThrows(IllegalArgumentException.class, () -> scene().withCamera(List.of()));
        assertThrows(IllegalArgumentException.class, () -> scene().withCamera(List.of(
                new CameraKey(1, 0, 0, 1, Interpolation.HOLD))));
    }

    @Test
    void rejectsDuplicateIdsInvalidCharactersAndNonFiniteValues() {
        var hero = actor(List.of(key(0, 0, 0, -1, Interpolation.HOLD)));
        assertThrows(IllegalArgumentException.class, () -> scene().withActors(List.of(hero, hero)));
        assertThrows(IllegalArgumentException.class,
                () -> new Actor("hero", "Hero", "s1", "tails", hero.keys()));
        assertThrows(IllegalArgumentException.class, () -> key(0, Double.NaN, 0, -1, Interpolation.HOLD));
        assertThrows(IllegalArgumentException.class,
                () -> new CameraKey(0, 0, 0, Double.POSITIVE_INFINITY, Interpolation.HOLD));
        assertThrows(IllegalArgumentException.class,
                () -> new ActorKey(0, 0, 0, 0, -1, Facing.RIGHT, true, 0, Interpolation.HOLD));
        assertThrows(IllegalArgumentException.class, () -> scene().withDurationFrames(0));
    }

    @Test
    void portableResourcePathsRejectAbsoluteTraversalAndCrossPlatformEscapes() {
        for (String path : List.of("../set.ogfproject", "/tmp/set.ogfproject", "C:/set.ogfproject",
                "sets/../set.ogfproject", "sets\\set.ogfproject", "sets//set.ogfproject")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SetSource("s3k", 0, 0, "", path), path);
        }
        assertEquals("sets/scene.ogfproject",
                new SetSource("s3k", 0, 0, "", "sets/scene.ogfproject").levelProject());
        assertThrows(IllegalArgumentException.class,
                () -> new AudioCue("line", 0, AudioKind.VOICE, "s3k", -1, "../line.wav", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioCue("line", 0, AudioKind.VOICE, "s3k", -1, "line.gen", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioCue("sound", 0, AudioKind.ROM_SFX, "s3k", 1, "line.wav", 1));
    }

    @Test
    void intervalsAndDocumentLimitsAreValidatedAfterEdits() {
        var document = scene().withCaptions(List.of(new Caption("speech", 3, 600, "Hello", 8, 16, -1)));
        assertThrows(IllegalArgumentException.class, () -> document.withDurationFrames(599));
        assertThrows(IllegalArgumentException.class, () -> new Caption("speech", 3, 3, "Hello", 0, 0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> scene().withAudio(List.of(new AudioCue("cue", 600, AudioKind.MUSIC_STOP, "s3k", -1, "", 1))));
        assertThrows(IllegalArgumentException.class, () -> scene().withSize(159, 224));
        assertThrows(IllegalArgumentException.class, () -> new StageplayDocument(VERSION + 1, "future", 10, 320, 224,
                scene().set(), List.of(), scene().camera(), List.of(), List.of()));
    }

    @Test
    void versionTwoTracksStayImmutableAndSurviveExistingMutators() {
        var effects = new ArrayList<>(List.of(new CameraEffect("quake", 0, 60, 8, 4, 7, 42)));
        var document = scene().withCameraEffects(effects);
        effects.clear();
        assertEquals(VERSION, document.version());
        assertEquals(1, document.withName("Renamed").withSet(document.set()).withSize(640, 448)
                .withDurationFrames(601).withActors(List.of()).withCamera(document.camera())
                .withCaptions(List.of()).withAudio(List.of()).cameraEffects().size());
        assertThrows(UnsupportedOperationException.class, () -> document.cameraEffects().clear());
        assertEquals(VERSION, new StageplayDocument(VERSION, "Current source", 600, 320, 224,
                scene().set(), List.of(), scene().camera(), List.of(), List.of()).version());
        assertThrows(IllegalArgumentException.class, () -> new CameraEffect("bad", 0, 1, Double.NaN, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CameraEffect("bad", 0, 1, 0, 0, 31, 0));
        assertThrows(IllegalArgumentException.class, () -> document.withDurationFrames(59));
    }

    @Test
    void sceneChangesRequireAnchorsAndRejectOverlappingOrClippedTransitions() {
        var change = new SceneChange("arrival", 100, scene().set(), Transition.FADE_BLACK, 20);
        assertThrows(IllegalArgumentException.class, () -> scene().withSceneChanges(List.of(change)));
        var anchored = scene().withCamera(List.of(scene().camera().getFirst(),
                new CameraKey(100, 500, 400, 1, Interpolation.HOLD),
                new CameraKey(105, 700, 400, 1, Interpolation.HOLD)));
        var changed = anchored.withSceneChanges(List.of(change));
        assertEquals(List.of(change), changed.withAudio(List.of()).sceneChanges());
        assertThrows(IllegalArgumentException.class, () -> changed.withActors(List.of(actor(List.of(key(0, 0, 0, -1, Interpolation.HOLD))))));
        assertThrows(IllegalArgumentException.class, () -> anchored.withSceneChanges(List.of(change,
                new SceneChange("overlap", 105, scene().set(), Transition.CUT, 0))));
        assertThrows(IllegalArgumentException.class, () -> anchored.withSceneChanges(List.of(
                new SceneChange("clipped", 100, scene().set(), Transition.FADE_WHITE, 202))));
        assertThrows(IllegalArgumentException.class, () -> changed.withDurationFrames(110));
        assertThrows(IllegalArgumentException.class, () -> new SceneChange("bad-cut", 100, scene().set(), Transition.CUT, 1));
        assertThrows(IllegalArgumentException.class, () -> changed.withCameraEffects(List.of(new CameraEffect("arrival", 0, 10, 1, 1, 5, 0))));
    }

    @Test
    void actorAndAggregateKeyLimitsPreventUnboundedScenes() {
        var oneKey = List.of(key(0, 0, 0, -1, Interpolation.HOLD));
        var cast = new ArrayList<Actor>();
        for (int actor = 0; actor < 33; actor++) {
            cast.add(new Actor("actor-" + actor, "Actor", "s3k", "sonic", oneKey));
        }
        assertThrows(IllegalArgumentException.class, () -> scene().withActors(cast));
        var keys = new ArrayList<ActorKey>();
        for (int frame = 0; frame < 34_000; frame++) keys.add(key(frame, frame, 0, -1, Interpolation.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> scene().withDurationFrames(36_000).withActors(List.of(
                new Actor("one", "One", "s3k", "sonic", keys),
                new Actor("two", "Two", "s3k", "tails", keys),
                new Actor("three", "Three", "s3k", "knuckles", keys))));
    }
}
