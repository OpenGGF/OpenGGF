package com.openggf.mods.mutators;

import com.openggf.audio.GameSound;
import com.openggf.game.ModApi;
import java.util.List;
import java.util.Map;

/** Engine-minted callback-scoped activation authority for the current owner's registered LIVE families. */
@ModApi
public interface TemporaryMutators {
    /** Zero duration submits a one-shot which waits for native acknowledgement. Timed duration is 1..3600 presentations. */
    ActivationResult activate(String localDefinitionId, Map<String, Object> options, long durationPresentations);
    /** Changes bounded options without changing identity, family or expiry. */
    ActivationResult replace(long activationId, Map<String, Object> options);
    boolean cancel(long activationId);
    List<ActiveActivation> active();
    List<Availability> available();
    /** Native semantic cue, issued from an observation callback, never a draw callback. */
    void playSfx(GameSound sound);

    @ModApi record ActivationResult(boolean accepted, long activationId, String reason) { }
    @ModApi record ActiveActivation(long activationId, String localDefinitionId, Map<String,Object> options,
            long agePresentations, long remainingPresentations, boolean oneShot, String status) {
        public ActiveActivation { options = Map.copyOf(options); }
    }
    @ModApi record Availability(String localDefinitionId, boolean available, String reason) { }
}
