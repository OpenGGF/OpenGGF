package com.openggf.game.patch;

import com.openggf.game.GameModule;

import java.util.Objects;

/** Typed result of composing a frozen patch plan for one gameplay launch. */
public sealed interface ResolutionResult permits ResolutionResult.Resolved,
        ResolutionResult.LaunchAborted {

    record Resolved(GameModule module) implements ResolutionResult {
        public Resolved {
            Objects.requireNonNull(module, "module");
        }
    }

    /** Creator apply failures abort because the input module may have been mutated. */
    record LaunchAborted(PatchOwner failedOwner, String patchId, Throwable cause)
            implements ResolutionResult {
        public LaunchAborted {
            Objects.requireNonNull(failedOwner, "failedOwner");
            Objects.requireNonNull(patchId, "patchId");
            Objects.requireNonNull(cause, "cause");
        }
    }
}
