package com.openggf.game.patch;

import com.openggf.game.GameModule;

import java.util.Objects;
import java.util.Set;

/** Typed result of composing a frozen patch plan for one gameplay launch. */
@com.openggf.game.ModApi
public sealed interface ResolutionResult permits ResolutionResult.Resolved,
        ResolutionResult.LaunchAborted {

    @com.openggf.game.ModApi
    record Resolved(GameModule module, java.util.Map<PatchOwner, Throwable> ownerFailures)
            implements ResolutionResult {
        public Resolved {
            Objects.requireNonNull(module, "module");
            ownerFailures = java.util.Map.copyOf(Objects.requireNonNull(ownerFailures,
                    "ownerFailures"));
        }

        public Resolved(GameModule module) {
            this(module, java.util.Map.of());
        }
    }

    /** Creator apply failures abort because the input module may have been mutated. */
    @com.openggf.game.ModApi
    record LaunchAborted(PatchOwner failedOwner, String patchId, Throwable cause,
                         Set<PatchOwner> failedOwners)
            implements ResolutionResult {
        public LaunchAborted {
            Objects.requireNonNull(failedOwner, "failedOwner");
            Objects.requireNonNull(patchId, "patchId");
            Objects.requireNonNull(cause, "cause");
            failedOwners = Set.copyOf(Objects.requireNonNull(failedOwners, "failedOwners"));
            if (!failedOwners.contains(failedOwner)) {
                throw new IllegalArgumentException("failedOwners must contain failedOwner");
            }
        }

        public LaunchAborted(PatchOwner failedOwner, String patchId, Throwable cause) {
            this(failedOwner, patchId, cause, Set.of(failedOwner));
        }
    }
}
