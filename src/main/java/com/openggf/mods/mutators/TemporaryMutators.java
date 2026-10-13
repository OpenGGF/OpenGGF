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
    /** Completes this owner's active modal lease; a declared win reward waits for native acknowledgement. */
    default ActivationResult complete(long activationId,boolean won) {
        return new ActivationResult(false,0,"Native completion is unavailable");
    }
    List<ActiveActivation> active();
    List<Availability> available();
    /** Native semantic cue, issued from an observation callback, never a draw callback. */
    void playSfx(GameSound sound);
    /** Read-only bounded art decoder for this world's supplied native ROM, when available. */
    default java.util.Optional<com.openggf.mods.scene.SceneRomArt> nativeArt() { return java.util.Optional.empty(); }

    @ModApi record ActivationResult(boolean accepted, long activationId, String reason) { }
    @ModApi record ActiveActivation(long activationId, String localDefinitionId, Map<String,Object> options,
            long agePresentations, long remainingPresentations, boolean oneShot, String status) {
        public ActiveActivation { options = Map.copyOf(options); }
    }
    @ModApi record Availability(String localDefinitionId, boolean available, String reason) { }
}
