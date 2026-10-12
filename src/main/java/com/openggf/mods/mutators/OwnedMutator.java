package com.openggf.mods.mutators;

import com.openggf.game.ModKeySyntax;
import java.util.Objects;

/** Engine-derived registration provenance. Creator definitions contain no claimed owner. */
public record OwnedMutator(String ownerModId, MutatorDefinition definition) {
    public OwnedMutator {
        ModKeySyntax.requireManifestId(ownerModId);
        Objects.requireNonNull(definition, "definition");
        ModKeySyntax.requireOwnedKey(ownerModId, definition.localId());
    }
    public String key() { return ModKeySyntax.requireOwnedKey(ownerModId, definition.localId()); }
}
