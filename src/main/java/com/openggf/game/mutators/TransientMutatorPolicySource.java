package com.openggf.game.mutators;

import com.openggf.mods.mutators.MutatorPolicy;
import java.util.List;

/** Engine-consumer projection of immutable, owner-tagged temporary policy contributions. */
public interface TransientMutatorPolicySource {
    List<Entry> entries();
    /** Consumes a pending one-shot exactly once. The acknowledgement is historical, so rewind can make it pending again. */
    boolean acknowledge(long activationId);
    /** Bounded instruction/result text exposed to this activation's creator HUD. */
    boolean report(long activationId, String status);
    record Entry(String owner, long activationId, MutatorPolicy policy, long agePresentations,
                 long remainingPresentations, boolean oneShot) { }
}
