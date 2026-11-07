package openggf.racing.server.verifier;
import openggf.racing.server.master.VerificationJobQueue.SwapReceipt;
import openggf.timeattack.*;
import com.openggf.mods.run.*;
import com.openggf.game.session.GameplayRunPolicy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TestVerifierFallbackReceipt {
    @Test void fallbackIdAndWindowMustMatchHostReceipt() {
        var recording = new AttemptInputRecording(new AttemptStartDescriptor("s2",0,0,"sonic","fp",TrackKind.ACT,
                CompetitionRoomSettings.Mode.COMPETITION,MonitorRule.COMPETITION_ITEMS,
                GameplayRunPolicy.DeathRestart.FAST_FROM_CHECKPOINT,true));
        recording.appendEvent(5,new RaceEvent.SwapFallback(42));
        var wire = new com.openggf.net.protocol.ControlMessage.TeleportNoPartner(42,1,0);
        var codec = new com.openggf.net.protocol.ControlJsonCodec();
        assertEquals(wire,codec.decode(codec.encode(null,wire)).message());
        var decision = new com.openggf.net.hub.HostTeleportSwap.Decision(0,1,0,1000,null,List.of(),wire.swapId(),null,
                java.util.Map.of(0,new com.openggf.net.protocol.ControlMessage.TeleportOutcome(wire.swapId(),5,"FALLBACK")),false);
        var receipts = openggf.racing.server.master.VerificationJobQueue.swapReceipts(List.of(decision),0);
        assertEquals(wire.swapId(),receipts.getFirst().swapId());
        assertTrue(VerifierSwapLog.matches(recording,receipts));
        assertFalse(VerifierSwapLog.matches(recording,List.of(new SwapReceipt(0,0,5,"FALLBACK",null))));
        assertFalse(VerifierSwapLog.matches(recording,List.of(new SwapReceipt(42,0,6,"FALLBACK",null))));
        assertFalse(VerifierSwapLog.matches(recording,List.of(new SwapReceipt(42,6,5,"FALLBACK",null))));
        assertFalse(VerifierSwapLog.matches(recording,List.of()));
    }
}
