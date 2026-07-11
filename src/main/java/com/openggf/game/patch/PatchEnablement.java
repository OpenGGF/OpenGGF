package com.openggf.game.patch;

import java.util.Objects;

/**
 * Enablement + ordering gate for patch composition (Phase 0 spec Amendment 2).
 *
 * <p>Enablement is based on explicit owner provenance, never patch-id inference.</p>
 */
@com.openggf.game.ModApi
public interface PatchEnablement {

    int BUILTIN_ORDER = -1;

    /** Returns whether the non-null owner participates in composition. */
    boolean isEnabled(PatchOwner owner);

    /** Returns the non-null owner's composition order; built-ins precede mods. */
    int orderOf(PatchOwner owner);

    /** Default: everything enabled, everything unmanaged (registration order). */
    PatchEnablement ALL_ENABLED = new PatchEnablement() {
        @Override
        public boolean isEnabled(PatchOwner owner) {
            Objects.requireNonNull(owner, "owner");
            return true;
        }

        @Override
        public int orderOf(PatchOwner owner) {
            Objects.requireNonNull(owner, "owner");
            return owner instanceof PatchOwner.BuiltIn ? BUILTIN_ORDER : 0;
        }
    };
}
