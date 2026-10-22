package com.openggf.stageplay.runtime;

import com.openggf.stageplay.StageplayDocument;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;

class TestStageplayCueDispatcher {
    private StageplayDocument scene() {
        return StageplayDocument.empty("s2",0,0,0,0,320,224).withAudio(List.of(
                new AudioCue("later",4,AudioKind.MUSIC_STOP,"s2",-1,"",1),
                new AudioCue("a",0,AudioKind.MUSIC_STOP,"s2",-1,"",1),
                new AudioCue("b",0,AudioKind.MUSIC_STOP,"s2",-1,"",1)));
    }
    @Test void forwardFramesDispatchStableCuesOnceAndSeekingIsSilent() {
        var dispatcher=new StageplayCueDispatcher(scene()); var heard=new ArrayList<String>();
        dispatcher.advance(0,cue->heard.add(cue.id())); dispatcher.advance(0,cue->heard.add(cue.id()));
        assertEquals(List.of("a","b"),heard);
        dispatcher.seek(3); assertEquals(List.of("a","b"),heard);
        dispatcher.advance(4,cue->heard.add(cue.id())); assertEquals(List.of("a","b","later"),heard);
        assertThrows(IllegalArgumentException.class,()->dispatcher.advance(2,cue->fail()));
        dispatcher.seek(-1); dispatcher.advance(0,cue->heard.add(cue.id()));
        assertEquals(List.of("a","b","later","a","b"),heard);
    }
    @Test void faultCannotReplayAlreadySubmittedEvents() {
        var dispatcher=new StageplayCueDispatcher(scene());
        assertThrows(IllegalStateException.class,()->dispatcher.advance(4,cue->{throw new IllegalStateException("failed output");}));
        dispatcher.advance(4,cue->fail("faulted admission must not repeat"));
    }
}
