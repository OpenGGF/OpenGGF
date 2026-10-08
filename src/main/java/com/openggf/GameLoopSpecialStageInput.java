package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.internal.NativeSpecialStageInput;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;

/** Arms fresh host pulse retention only around an eligible interactive presentation tick. */
final class GameLoopSpecialStageInput {
    private boolean interactivePresentationTick;
    private boolean retainFresh;
    private SpecialStageProvider armedProvider;
    private long armedEntryEpoch;
    void runPresentationTick(SpecialStageProvider provider, boolean freshInput, Runnable step) {
        boolean old = interactivePresentationTick; boolean oldFresh = retainFresh; var oldProvider = armedProvider; long oldEpoch = armedEntryEpoch;
        interactivePresentationTick = true; retainFresh = freshInput; armedProvider = provider;
        var owner = NativeStagePacingOwners.special(provider);
        armedEntryEpoch = owner == null ? 0 : owner.pacingState().entryEpoch();
        try { step.run(); } finally {
            interactivePresentationTick = old; retainFresh = oldFresh; armedProvider = oldProvider; armedEntryEpoch = oldEpoch;
        }
    }
    void apply(SpecialStageProvider provider, InputHandler input, WorldSession world) {
        if (interactivePresentationTick) {
            var pacing = WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class);
            var owner = NativeStagePacingOwners.special(provider);
            if (provider == armedProvider && owner != null
                    && owner.pacingState().entryEpoch() == armedEntryEpoch && owner.pacingState().interactive())
                NativeSpecialStageInput.publish(provider, pacing, input, retainFresh);
            else if (pacing != null) pacing.clearPendingInput();
        }
        NativeSpecialStageInput.dispatch(provider, input);
    }
}
