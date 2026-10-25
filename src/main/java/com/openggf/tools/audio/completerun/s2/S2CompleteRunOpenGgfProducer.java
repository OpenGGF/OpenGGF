package com.openggf.tools.audio.completerun.s2;

import com.openggf.tools.audio.completerun.CompleteRunAudioOpenGgfProducerSupport;
import com.openggf.tools.audio.completerun.CompleteRunAudioProducer;

/** Fixed S2 engine producer; Task 7 installs its authenticated runtime identity. */
public final class S2CompleteRunOpenGgfProducer implements CompleteRunAudioProducer {
    @Override
    public void capture(Request request) throws Exception {
        CompleteRunAudioOpenGgfProducerSupport.capture(request, S2CompleteRunAudioProfile.ID);
    }
}
