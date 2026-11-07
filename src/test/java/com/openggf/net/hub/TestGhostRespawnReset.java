package com.openggf.net.hub;

import com.openggf.net.ghost.*;
import com.openggf.net.protocol.GhostPackets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestGhostRespawnReset {
    long now;
    GhostStreamValidator validator(boolean enabled) {
        var v = new GhostStreamValidator(new TrackValidationProfile(8192, 1024, 32, 60), () -> now, (k,d) -> {});
        v.enableRespawns(enabled); v.onAttemptStart(1); return v;
    }
    GhostStreamValidator.Verdict send(GhostStreamValidator v, int index, int x, int progress, boolean reset) {
        now = index * 1000L / 60;
        byte[] data = new byte[7];
        var frame = new GhostFrame(x,100,0,false,false,false,0,false,false,progress,false,0,reset);
        GhostFrameCodec.encode(frame,data,0);
        assertEquals(frame, GhostFrameCodec.decode(data,0));
        return v.onBatch(new GhostPackets.FramesBatch(1,index,1,data));
    }
    @Test void boundedReturnAcceptedButRepeatedUnanchoredAndForwardResetsRejected() {
        var v = validator(true);
        for (int i=0;i<130;i++) assertEquals(GhostStreamValidator.Verdict.ACCEPT,send(v,i,100+i*10,1,false));
        assertEquals(GhostStreamValidator.Verdict.DROP,send(v,130,4000,1,true));
        assertEquals(GhostStreamValidator.Verdict.DROP,send(v,130,100,2,true));
        assertEquals(GhostStreamValidator.Verdict.ACCEPT,send(v,130,100,1,true));
        assertEquals(GhostStreamValidator.Verdict.DROP,send(v,131,100,1,true));
        assertEquals(GhostStreamValidator.Verdict.DROP,send(v,131,4000,1,false));
    }
    @Test void jumpThroughAtMaximumActivationHeightCanReturnToSavedPostCentre() {
        var v = validator(true);
        byte[] data = new byte[7];
        for (int i=0;i<=120;i++) {
            now = i * 1000L / 60;
            GhostFrameCodec.encode(new GhostFrame(100,i==120 ? 164 : 100,0,false,false,false,0,false,false,1,false,0,i==120),data,0);
            assertEquals(GhostStreamValidator.Verdict.ACCEPT,v.onBatch(new GhostPackets.FramesBatch(1,i,1,data)));
        }
        assertEquals(0,v.violationCount());
    }
    @Test void timeAttackAndUnanchoredFirstFrameCannotReset() {
        var v = validator(false);
        assertEquals(GhostStreamValidator.Verdict.DROP,send(v,0,100,1,true));
        assertEquals(GhostStreamValidator.Verdict.ACCEPT,send(v,0,100,1,false));
        assertEquals(GhostStreamValidator.Verdict.DROP,send(v,1,100,1,true));
    }
    @Test void perLapOrCheckpointCapCannotBeRefilledByWaiting() {
        var v = validator(true); int index=0;
        for (int reset=0;reset<8;reset++) {
            for (int j=0;j<120;j++) assertEquals(GhostStreamValidator.Verdict.ACCEPT,send(v,index++,100,1,false));
            assertEquals(GhostStreamValidator.Verdict.ACCEPT,send(v,index++,100,1,true));
        }
        for(int j=0;j<120;j++) assertEquals(GhostStreamValidator.Verdict.ACCEPT,send(v,index++,100,1,false));
        assertEquals(GhostStreamValidator.Verdict.DROP,send(v,index,100,1,true));
    }
}
