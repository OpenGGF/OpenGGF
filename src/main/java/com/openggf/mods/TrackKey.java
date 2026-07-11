package com.openggf.mods;

import com.openggf.game.ModKeySyntax;

public record TrackKey(String modId, String localName) {
    public TrackKey {
        ModKeySyntax.requireOwnedKey(modId, localName);
    }
}
