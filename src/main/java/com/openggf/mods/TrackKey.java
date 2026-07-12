package com.openggf.mods;

import com.openggf.game.ModKeySyntax;

@com.openggf.game.ModApi
public record TrackKey(String modId, String localName) {
    public TrackKey {
        ModKeySyntax.requireOwnedKey(modId, localName);
    }
}
