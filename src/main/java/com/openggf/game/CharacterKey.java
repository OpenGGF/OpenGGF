package com.openggf.game;

import java.util.Objects;
import java.util.Optional;

/** Stable persisted playable-character identity. */
@ModApi
public final class CharacterKey {
    public static final CharacterKey SONIC = builtin("sonic");
    public static final CharacterKey TAILS = builtin("tails");
    public static final CharacterKey KNUCKLES = builtin("knuckles");

    private final String persisted;
    private final String ownerModId;

    private CharacterKey(String persisted, String ownerModId) {
        this.persisted = persisted;
        this.ownerModId = ownerModId;
    }

    public static CharacterKey builtin(String persisted) {
        Objects.requireNonNull(persisted, "persisted");
        if (!persisted.equals("sonic") && !persisted.equals("tails") && !persisted.equals("knuckles")) {
            throw new IllegalArgumentException("Unknown builtin character key: " + persisted);
        }
        return new CharacterKey(persisted, null);
    }

    public static CharacterKey mod(String ownerModId, String localName) {
        String owner = ModKeySyntax.requireManifestId(ownerModId);
        return new CharacterKey(ModKeySyntax.requireOwnedKey(owner, localName), owner);
    }

    public static CharacterKey parsePersisted(String persisted) {
        Objects.requireNonNull(persisted, "persisted");
        return switch (persisted) {
            case "sonic" -> SONIC;
            case "tails" -> TAILS;
            case "knuckles" -> KNUCKLES;
            default -> {
                String canonical = ModKeySyntax.requireDisplayKey(persisted);
                int separator = canonical.indexOf(':');
                yield mod(canonical.substring(0, separator), canonical.substring(separator + 1));
            }
        };
    }

    public String persisted() { return persisted; }
    public Optional<String> ownerModId() { return Optional.ofNullable(ownerModId); }
    public boolean isBuiltin() { return ownerModId == null; }

    @Override public boolean equals(Object other) {
        return other instanceof CharacterKey key && persisted.equals(key.persisted);
    }
    @Override public int hashCode() { return persisted.hashCode(); }
    @Override public String toString() { return persisted; }
}
