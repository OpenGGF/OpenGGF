package com.openggf.stageplay;

import com.openggf.stageplay.StageplayDocument.Facing;
import com.openggf.tools.StageplayScriptCompiler;
import com.openggf.tools.StageplayScriptDecompiler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Correction guard for Day Off and the earlier Eggman's Sky / Super Sonic Bros raw-flip mistakes. */
@Tag("guards")
class TestStageplayFacingGuard {
    private static void requireSemanticFacing(Class<?> key) {
        var component = Arrays.stream(key.getRecordComponents()).filter(c -> c.getName().equals("facing")).findFirst();
        if (component.isEmpty() || component.get().getType() != Facing.class
                || Arrays.stream(key.getRecordComponents()).anyMatch(c -> c.getName().equals("flipX")))
            throw new IllegalStateException(key.getName() + ": direct with com.openggf.stageplay.StageplayDocument.Facing.LEFT/RIGHT; "
                    + "raw flipX belongs inside the native presentation owner");
    }

    @Test void authoringKeysRequireTheSameSemanticFacingType() {
        requireSemanticFacing(StageplayDocument.ActorKey.class);
        requireSemanticFacing(StageplayDocument.ObjectKey.class);
    }

    // The exact direction-bearing field shape of ObjectKey at ac46111bc:413-419.
    private record HistoricalObjectKey(int frame, double x, double y, int animation, int pose,
                                       boolean flipX, boolean visible) { }

    @Test void guardFailsOnTheHistoricalRawFlipContract() {
        var failure = assertThrows(IllegalStateException.class, () -> requireSemanticFacing(HistoricalObjectKey.class));
        assertTrue(failure.getMessage().contains("Facing.LEFT/RIGHT"));
    }

    private static byte[] recipe(String directionField) {
        // Day Off drive(..., +1, flip=False), reduced to its two rightward Motobug keys.
        return ("""
                {"version":%d,"name":"Day Off facing regression","cast":[],
                 "objectCast":[{"id":"moto","name":"Motobug","gameId":"s1","objectId":64}],
                 "shots":[{"id":"loop","durationFrames":20,"set":{"gameId":"s1","zone":0,"act":0},
                  "camera":[{"frame":0,"x":5260,"y":470}],
                  "objects":{"moto":[{"frame":0,"x":5490,"y":689,"mode":"POSE",%s},
                                       {"frame":8,"x":5498,"y":689,"mode":"POSE",%s}]}}]}
                """).formatted(StageplayScriptCompiler.VERSION, directionField, directionField).getBytes(StandardCharsets.UTF_8);
    }

    @Test void thePastDayOffMistakeIsRejectedWithTheReplacementNamed() {
        var failure = assertThrows(IOException.class,
                () -> StageplayScriptCompiler.compile(recipe("\"flipX\":false")));
        assertTrue(failure.getMessage().contains("facing: LEFT or RIGHT"), failure.getMessage());
    }

    @Test void leftAndRightSurviveCanonicalAndScreenplayRoundTrips() throws Exception {
        for (var facing : Facing.values()) {
            var scene = StageplayScriptCompiler.compile(recipe("\"facing\":\"" + facing + "\""));
            assertEquals(facing, scene.objectActors().getFirst().keys().getFirst().facing());
            assertEquals(scene, StageplayStore.decode(StageplayStore.encode(scene)));
            assertEquals(scene, StageplayScriptCompiler.compile(StageplayScriptDecompiler.decompile(scene)));
            assertFalse(new String(StageplayStore.encode(scene), StandardCharsets.UTF_8).contains("flipX"));
        }
    }
}
