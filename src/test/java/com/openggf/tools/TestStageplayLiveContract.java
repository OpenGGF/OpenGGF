package com.openggf.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openggf.stageplay.StageplayDocument;
import com.openggf.stageplay.StageplayStore;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayLiveContract {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SOURCE = """
            {"version":6,"name":"Native intent","width":320,"height":224,
             "cast":[{"id":"pilot","name":"Tails","gameId":"s3k","character":"tails"}],
             "shots":[{"id":"flight","durationFrames":600,
               "set":{"gameId":"s3k","zone":0,"act":0,"worldPolicy":"LIVE"},
               "camera":[{"frame":0,"x":5000,"y":900,"follow":{"actors":["pilot"],"deadZoneX":12,
                   "deadZoneY":8,"lookAhead":4,"smoothing":0.2,"offsetX":0,"offsetY":-8}}],
               "actors":{"pilot":[{"frame":0,"x":5200,"y":1040}]},
               "actions":{"pilot":[{"frame":0,"kind":"WAIT","condition":"GROUNDED","timeoutFrames":60},
                 {"frame":1,"kind":"FLY_TO","x":5300,"y":900,"urgency":0.4,"timeoutFrames":180},
                 {"frame":200,"kind":"HOVER","holdFrames":90},{"frame":300,"kind":"RELEASE"}]}}]}
            """;
    private static StageplayDocument compile(String source) throws IOException {
        return StageplayScriptCompiler.compile(source.getBytes(StandardCharsets.UTF_8));
    }
    @Test void liveContractRoundTripsAndCompilerKeepsBoundedSemanticIntent() throws Exception {
        var scene=compile(SOURCE);
        assertEquals(StageplayDocument.WorldPolicy.LIVE,scene.set().worldPolicy());
        assertEquals(List.of("pilot"),scene.camera().getFirst().follow().actors());
        assertEquals(60,scene.puppets().getFirst().commands().getFirst().timeoutFrames());
        assertEquals(scene,StageplayStore.decode(StageplayStore.encode(scene)));
    }
    @Test void liveWorldMayUseTheExistingVisitBoundInteractivePlayerLease() throws Exception {
        var scene=compile(SOURCE);
        assertDoesNotThrow(()->new com.openggf.stageplay.StageplayPlayback(scene,"pilot",
                com.openggf.stageplay.runtime.StageplayNativeBinding.FinishMode.RESTORE_ENTRY));
        assertDoesNotThrow(()->new com.openggf.stageplay.StageplayPlayback(scene));
    }

    @Test void rejectsUnsupportedUnboundedAndUnknownReferences() {
        for(String invalid:List.of(SOURCE.replace("\"timeoutFrames\":60","\"timeoutFrames\":0"),
                SOURCE.replace("\"gameId\":\"s3k\",\"character\":\"tails\"","\"gameId\":\"s2\",\"character\":\"tails\""),
                SOURCE.replace("\"actors\":[\"pilot\"]","\"actors\":[\"missing\"]"),
                SOURCE.replace("\"worldPolicy\":\"LIVE\"","\"worldPolicy\":\"TERRAIN_ONLY\""),
                SOURCE.replace("\"condition\":\"GROUNDED\"","\"condition\":\"TARGET_AVAILABLE\"")))
            assertThrows(IOException.class,()->compile(invalid),invalid);
    }
    @Test void allNativeWorldSizesAndZoomsUseTheSameIntentContract() {
        assertDoesNotThrow(() -> compile(SOURCE.replace("\"height\":224", "\"height\":384")
                .replace("\"x\":5000", "\"zoom\":2,\"x\":5000")));
    }
}
