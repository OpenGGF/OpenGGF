package com.openggf.stageplay;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayDocument {
    static StageplayDocument scene() {
        return StageplayDocument.empty("s3k", 0, 0, 100, 200, 320, 224);
    }

    static ActorKey key(int frame, double x, int animation, int pose, Interpolation interpolation) {
        return new ActorKey(frame, x, 200, animation, pose, false, true, 1, interpolation);
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
                () -> new ActorKey(0, 0, 0, 0, -1, false, true, 0, Interpolation.HOLD));
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
        assertThrows(IllegalArgumentException.class, () -> new StageplayDocument(2, "future", 10, 320, 224,
                scene().set(), List.of(), scene().camera(), List.of(), List.of()));
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
