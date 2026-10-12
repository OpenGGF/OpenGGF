package com.openggf;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.SpecialStageDebugCapabilities;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.internal.NativeSpecialStageInput;
import com.openggf.game.internal.NativeStagePacingOwners;
import com.openggf.game.mutators.GameplayMutatorPacing;
import com.openggf.game.session.WorldSession;
import com.openggf.game.session.WorldSessionPolicyAccess;

import java.util.function.IntPredicate;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_F1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F4;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;

/** Routes special-stage debug input and retains fresh pulses around eligible presentation ticks. */
final class GameLoopSpecialStageInput {
    private boolean interactivePresentationTick;
    private boolean retainFresh;
    private SpecialStageProvider armedProvider;
    private long armedEntryEpoch;

    /** Returns true while alignment mode consumes the tick before native input dispatch. */
    static boolean handleDebugInput(SpecialStageProvider provider, SonicConfigurationService config,
                                    IntPredicate unmodifiedDebugKeyPressed) {
        int leftKey = config.getInt(SonicConfiguration.LEFT);
        int rightKey = config.getInt(SonicConfiguration.RIGHT);
        int upKey = config.getInt(SonicConfiguration.UP);
        int downKey = config.getInt(SonicConfiguration.DOWN);
        int debugModeKey = config.getInt(SonicConfiguration.DEBUG_MODE_KEY);
        SpecialStageDebugCapabilities capabilities =
                SpecialStageDebugCapabilities.orNone(provider.debugCapabilities());

        if (capabilities.gameplayMovement() && unmodifiedDebugKeyPressed.test(debugModeKey)) {
            provider.toggleGameplayDebugMode();
        }
        if (capabilities.alignment() && unmodifiedDebugKeyPressed.test(GLFW_KEY_F4)) {
            provider.toggleAlignmentTestMode();
        }
        if (capabilities.lagCompensation() && unmodifiedDebugKeyPressed.test(GLFW_KEY_F1)) {
            provider.toggleLagCompensationDisplay();
        }
        if (capabilities.alignment() && provider.isAlignmentTestMode()) {
            if (unmodifiedDebugKeyPressed.test(leftKey)) {
                provider.adjustAlignmentOffset(-1);
            }
            if (unmodifiedDebugKeyPressed.test(rightKey)) {
                provider.adjustAlignmentOffset(1);
            }
            if (unmodifiedDebugKeyPressed.test(upKey)) {
                provider.adjustAlignmentSpeed(0.1);
            }
            if (unmodifiedDebugKeyPressed.test(downKey)) {
                provider.adjustAlignmentSpeed(-0.1);
            }
            if (unmodifiedDebugKeyPressed.test(GLFW_KEY_SPACE)) {
                provider.toggleAlignmentStepMode();
            }
            return true;
        }
        return false;
    }

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
