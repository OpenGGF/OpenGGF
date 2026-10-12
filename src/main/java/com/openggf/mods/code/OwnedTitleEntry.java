package com.openggf.mods.code;

import java.util.Objects;

/**
 * A mod's master-title entry as the engine shows and opens it: the owner, its label and a
 * scene factory whose every callback runs inside that owner's fault boundary. Engine-internal;
 * only this package can construct one, so an entry cannot forge its owner.
 */
public record OwnedTitleEntry(String ownerModId, String label, OwnedSceneFactory scene) {
    public OwnedTitleEntry {
        Objects.requireNonNull(ownerModId, "ownerModId");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(scene, "scene");
    }
}
