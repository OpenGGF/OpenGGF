package openggf.racing.server.verifier;

import openggf.timeattack.AttemptInputRecording;
import openggf.racing.server.master.VerificationJobQueue.SwapReceipt;
import com.openggf.mods.run.RaceEvent;
import com.openggf.mods.run.SwapTarget;
import java.util.List;

/** Compares recorded external inputs with host receipts; an absent receipt never grants a swap. */
public final class VerifierSwapLog {
    private VerifierSwapLog() { }
    public static boolean matches(AttemptInputRecording recording, List<SwapReceipt> receipts) {
        if (recording.events().size() != receipts.size()) return false;
        var seen = new java.util.HashSet<Long>();
        for (var recorded : recording.events()) {
            long id = recorded.event() instanceof RaceEvent.SwapApply a ? a.swapId()
                    : ((RaceEvent.SwapFallback) recorded.event()).swapId();
            if (!seen.add(id)) return false;
            var matches = receipts.stream().filter(r -> r.swapId() == id).toList();
            if (matches.size() != 1) return false;
            var r = matches.getFirst();
            if (recorded.step() != r.applyFrame() || r.applyFrame() < r.requestFrame()
                    || r.applyFrame() > r.requestFrame() + 180) return false;
            if (recorded.event() instanceof RaceEvent.SwapFallback) {
                if (!"FALLBACK".equals(r.outcome())) return false;
            } else {
                if (!"APPLIED".equals(r.outcome()) || r.target() == null) return false;
                var t = r.target();
                var target = new SwapTarget(t.centreX(),t.centreY(),t.xVel(),t.yVel(),t.facingLeft(),
                        t.secondaryPath(),t.priorityBucket(),t.highPriority(),t.shoeKind(),t.shoeTimer(),t.progress());
                if (!target.equals(((RaceEvent.SwapApply)recorded.event()).target())) return false;
            }
        }
        return true;
    }
}
