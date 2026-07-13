package com.openggf.level.objects;

/** Neutral owner-scoped sound key accepted by creator object services. */
@com.openggf.game.ModApi
public interface ObjectSfxKey {
    String modId();
    String localName();
}
