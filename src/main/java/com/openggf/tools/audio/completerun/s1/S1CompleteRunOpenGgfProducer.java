package com.openggf.tools.audio.completerun.s1;

import com.openggf.tools.audio.completerun.CompleteRunAudioOpenGgfProducerSupport;
import com.openggf.tools.audio.completerun.CompleteRunAudioProducer;

/** Fixed S1 production BK2 startup; the shared support owns the fail-closed binding gate. */
public final class S1CompleteRunOpenGgfProducer implements CompleteRunAudioProducer {
    @Override public void capture(Request request) throws Exception {
        CompleteRunAudioOpenGgfProducerSupport.capture(request, S1CompleteRunAudioProfile.ID);
    }
}
