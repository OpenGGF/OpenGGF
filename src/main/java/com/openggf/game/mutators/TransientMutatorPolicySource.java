package com.openggf.game.mutators;

import com.openggf.mods.mutators.MutatorPolicy;
import java.util.List;

/** Engine-consumer projection of immutable, owner-tagged temporary policy contributions. */
public interface TransientMutatorPolicySource {
    List<Entry> entries();
    /** Authoritative host quarantine/retirement; completed native tails retain their captured owner. */
    default boolean ownerAvailable(String owner) { return true; }
    /** Consumes a pending one-shot exactly once. The acknowledgement is historical, so rewind can make it pending again. */
    boolean acknowledge(long activationId);
    default List<Completion> completions() { return List.of(); }
    default boolean acknowledgeCompletion(long activationId) { return false; }
    record Completion(String owner,long activationId,int ringReward) {
        public Completion {
            java.util.Objects.requireNonNull(owner);
            if(activationId==0 || ringReward<1 || ringReward>10)throw new IllegalArgumentException("Invalid native completion");
        }
    }
    /** Bounded instruction/result text exposed to this activation's creator HUD. */
    boolean report(long activationId, String status);
    record Entry(String owner, long activationId, MutatorPolicy policy, long agePresentations,
                 long remainingPresentations, boolean oneShot) { }
}
