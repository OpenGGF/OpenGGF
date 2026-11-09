package com.openggf.net.hub;

import com.openggf.net.protocol.ControlMessage;
import com.openggf.game.arena.RaceEvent;
import openggf.timeattack.*;
import openggf.racing.server.master.VerificationJobQueue;
import openggf.racing.server.verifier.VerifierSwapLog;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TestSwapRefusalReceipts {
    private final HostTeleportSwap host = new HostTeleportSwap();
    private final List<ControlMessage> sent = new ArrayList<>();
    private HostTeleportSwap.Observation player(int slot, int frame) {
        return new HostTeleportSwap.Observation(slot, frame, 1000, false, slot,
                new ControlMessage.SwapTarget(slot*100,100,0,0,false,false,0,false,0,0,0));
    }
    private ControlMessage request(int cap) {
        return host.request(0,1,10,1,1000,cap,List.of(player(0,10)),(s,m)->sent.add(m));
    }
    private void verify(ControlMessage.TeleportNoPartner receipt, int apply, String outcome) {
        host.outcome(0,new ControlMessage.TeleportOutcome(receipt.swapId(),apply,outcome));
        var start = new AttemptStartDescriptor("s2",0,0,"sonic","fp",
                com.openggf.mods.run.TrackKind.ACT, CompetitionRoomSettings.Mode.COMPETITION,
                com.openggf.game.arena.MonitorRule.COMPETITION_ITEMS,
                com.openggf.game.session.GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT,true);
        var recording = new AttemptInputRecording(start);
        recording.appendEvent(apply,new RaceEvent.SwapFallback(receipt.swapId()));
        var receipts = VerificationJobQueue.swapReceipts(host.log(),0);
        assertEquals(10,receipts.getLast().nativeRequestFrame());
        assertTrue(VerifierSwapLog.matches(recording,List.of(receipts.getLast())));
        assertFalse(VerifierSwapLog.matches(new AttemptInputRecording(start),List.of(receipts.getLast())));
    }
    @Test void sourceCapHasReasonAndVerifiableFallback() {
        var receipt = assertInstanceOf(ControlMessage.TeleportNoPartner.class,request(0));
        assertEquals("SOURCE_CAP",receipt.reason()); verify(receipt,11,"REFUSED");
    }
    @Test void cooldownHasReasonAndVerifiableFallback() {
        var swap = assertInstanceOf(ControlMessage.TeleportSwap.class,host.request(0,1,10,1,1000,12,
                List.of(player(0,10),player(1,10)),(s,m)->sent.add(m)));
        host.outcome(0,new ControlMessage.TeleportOutcome(swap.swapId(),11,"APPLIED"));
        var receipt = assertInstanceOf(ControlMessage.TeleportNoPartner.class,request(12));
        assertEquals("COOLDOWN",receipt.reason()); verify(receipt,12,"REFUSED");
    }
    @Test void expiredDeferralAndLateReceiptRemainVerifiable() {
        assertNull(host.request(0,1,10,1,1000,12,List.of(player(0,9)),(s,m)->sent.add(m)));
        host.drain(1251,List.of(),(s,m)->sent.add(m),(s,d)->fail(d));
        var receipt = assertInstanceOf(ControlMessage.TeleportNoPartner.class,sent.getLast());
        assertEquals("DEFERRAL_EXPIRED",receipt.reason()); verify(receipt,200,"EXPIRED");
    }
}
