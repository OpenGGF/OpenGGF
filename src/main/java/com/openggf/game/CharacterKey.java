package com.openggf.game;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable persisted playable-character identity.
 *
 * <p>Built-ins persist as {@code sonic}, {@code tails}, or {@code knuckles}. A mod
 * character persists as {@code owner-mod-id:local-name}; the owner portion is used
 * for class-loader recreation, enablement fallback, and callback fault ownership.</p>
 */
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

    /** Returns one of the three canonical built-in identities. */
    public static CharacterKey builtin(String persisted) {
        Objects.requireNonNull(persisted, "persisted");
        if (!persisted.equals("sonic") && !persisted.equals("tails") && !persisted.equals("knuckles")) {
            throw new IllegalArgumentException("Unknown builtin character key: " + persisted);
        }
        return new CharacterKey(persisted, null);
    }

    /** Creates an owner-scoped key from a manifest id and owner-local name. */
    public static CharacterKey mod(String ownerModId, String localName) {
        String owner = ModKeySyntax.requireManifestId(ownerModId);
        return new CharacterKey(ModKeySyntax.requireOwnedKey(owner, localName), owner);
    }

    /** Parses a built-in or canonical owner-scoped persisted identity. */
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

    /** Returns the stable value written to launch profiles, saves, and rewind state. */
    public String persisted() { return persisted; }
    /** Returns the manifest owner for a mod key, or empty for a built-in. */
    public Optional<String> ownerModId() { return Optional.ofNullable(ownerModId); }
    /** Reports whether this is one of the three engine-owned identities. */
    public boolean isBuiltin() { return ownerModId == null; }

    @Override public boolean equals(Object other) {
        return other instanceof CharacterKey key && persisted.equals(key.persisted);
    }
    @Override public int hashCode() { return persisted.hashCode(); }
    @Override public String toString() { return persisted; }
}
