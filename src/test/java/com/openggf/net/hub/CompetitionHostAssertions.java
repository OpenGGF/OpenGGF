package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only package bridge; production host capabilities remain room-thread scoped. */
public final class CompetitionHostAssertions {
    private CompetitionHostAssertions() { }
    public static void acceptsBpzFinish(ControlMessage.AttemptFinish finish) {
        long[] now={0};
        var host=new HostRoundEngine(()->now[0],ignored->{});
        assertTrue(host.startRound(new ControlMessage.RoundConfig("s3k",1,0,600,"OPEN",null,
                "ARENA","COMPETITION","OFF",30)));
        now[0]=10_000;host.onTick();
        host.profile(new TrackValidationProfile(4096,1024,32,60,512,0,5,0));
        host.register(0,"p","Player","sonic");
        assertTrue(host.startAttempt("p",1));
        assertNotNull(host.onAttemptFinish(0,"p",null,"Player","sonic",finish,false));
    }
}
