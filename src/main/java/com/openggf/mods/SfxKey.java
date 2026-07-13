package com.openggf.mods;

import com.openggf.game.ModKeySyntax;

public record SfxKey(String modId, String localName) {
    public SfxKey {
        ModKeySyntax.requireOwnedKey(modId, localName);
    }
}
