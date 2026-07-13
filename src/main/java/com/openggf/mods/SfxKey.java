package com.openggf.mods;

import com.openggf.game.ModKeySyntax;

@com.openggf.game.ModApi
public record SfxKey(String modId, String localName)
        implements com.openggf.level.objects.ObjectSfxKey {
    public SfxKey {
        ModKeySyntax.requireOwnedKey(modId, localName);
    }
}
