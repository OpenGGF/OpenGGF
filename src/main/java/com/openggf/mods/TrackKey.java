package com.openggf.mods;

import com.openggf.io.ModKeySyntax;

public record TrackKey(String modId, String localName) {
    public TrackKey {
        ModKeySyntax.requireOwnedKey(modId, localName);
    }
}
