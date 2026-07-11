package com.openggf.game.patch;

import java.util.Objects;

@com.openggf.game.ModApi
public sealed interface PatchOwner permits PatchOwner.BuiltIn, PatchOwner.Mod {
    @com.openggf.game.ModApi
    record BuiltIn(String id) implements PatchOwner {
        public BuiltIn {
            Objects.requireNonNull(id, "id");
        }
    }

    @com.openggf.game.ModApi
    record Mod(String modId) implements PatchOwner {
        public Mod {
            Objects.requireNonNull(modId, "modId");
        }
    }
}
