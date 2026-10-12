package com.openggf.game.internal;

import com.openggf.control.InputHandler;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;

/** One policy/history tick per canonical native iteration, including live S2 lag iterations. */
public final class NativeSpecialStageFrame {
    private NativeSpecialStageFrame() { }
    public static void step(SpecialStageProvider provider, WorldSession world, Runnable body) {
        var port = NativeStagePacingOwners.special(provider);
        var before = port == null ? null : port.pacingState();
        WorldSessionPolicyAccess.beforeSpecialStageForwardTick(world);
        body.run();
        if (port != null) {
            var pacing = WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class);
            var after = port.pacingState();
            if (pacing != null && before.entryEpoch() != after.entryEpoch()) pacing.clearPendingInput();
            else NativeSpecialStageInput.acknowledge(pacing, before, after);
        }
    }
    /** Engine rewind only: admit restored internal pending input; external trace/BK2 dispatch remains raw. */
    public static void replay(SpecialStageProvider provider, WorldSession world, InputHandler input) {
        step(provider, world, () -> {
            NativeSpecialStageInput.publish(provider,
                    WorldSessionPolicyAccess.getService(world, GameplayMutatorPacing.class), input, false);
            NativeSpecialStageInput.dispatch(provider, input);
            provider.update();
        });
    }
}
