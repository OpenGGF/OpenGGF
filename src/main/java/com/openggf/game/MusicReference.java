package com.openggf.game;

/** Stable level-music identity without coupling game APIs to the mod runtime. */
@ModApi
public sealed interface MusicReference permits MusicReference.Stock, MusicReference.Namespaced {
    static MusicReference stock(int musicId) { return new Stock(musicId); }
    static MusicReference namespaced(String owner, String localName) {
        return new Namespaced(owner, localName);
    }

    @ModApi
    record Stock(int musicId) implements MusicReference { }

    @ModApi
    record Namespaced(String owner, String localName) implements MusicReference {
        public Namespaced { ModKeySyntax.requireOwnedKey(owner, localName); }
    }
}
