package com.openggf.game.patch;

import java.util.Objects;

public sealed interface PatchOwner permits PatchOwner.BuiltIn, PatchOwner.Mod {
    record BuiltIn(String id) implements PatchOwner {
        public BuiltIn {
            Objects.requireNonNull(id, "id");
        }
    }

    record Mod(String modId) implements PatchOwner {
        public Mod {
            Objects.requireNonNull(modId, "modId");
        }
    }
}
