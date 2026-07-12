package com.openggf.game;

/** Stable persisted identity for a stock or mod-provided zone. */
@ModApi
public sealed interface ZoneKey permits ZoneKey.Stock, ZoneKey.Mod {
    static ZoneKey stock(int zoneIndex) { return new Stock(zoneIndex); }
    static ZoneKey mod(String ownerModId, String localName) { return new Mod(ownerModId, localName); }

    @ModApi
    record Stock(int zoneIndex) implements ZoneKey {
        public Stock {
            if (zoneIndex < 0) throw new IllegalArgumentException("Stock zone index must be non-negative");
        }
    }

    @ModApi
    record Mod(String ownerModId, String localName) implements ZoneKey {
        public Mod {
            ownerModId = ModKeySyntax.requireManifestId(ownerModId);
            localName = ModKeySyntax.requireLocalName(localName);
        }
    }
}
