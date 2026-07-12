package com.openggf.game;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable module-owned snapshot of playable-character definitions. */
@ModApi
public final class PlayableCharacterRegistry {
    private static final PlayableCharacterRegistry EMPTY = new PlayableCharacterRegistry(Map.of());
    private final Map<CharacterKey, CharacterDefinition> definitions;

    private PlayableCharacterRegistry(Map<CharacterKey, CharacterDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static PlayableCharacterRegistry empty() { return EMPTY; }

    public PlayableCharacterRegistry register(CharacterKey key, CharacterDefinition definition) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(definition, "definition");
        if (!key.equals(definition.key())) throw new IllegalArgumentException("Definition key mismatch");
        LinkedHashMap<CharacterKey, CharacterDefinition> copy = new LinkedHashMap<>(definitions);
        if (copy.putIfAbsent(key, definition) != null) {
            throw new IllegalArgumentException("Duplicate character key: " + key.persisted());
        }
        return new PlayableCharacterRegistry(copy);
    }

    public Optional<CharacterDefinition> find(CharacterKey key) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(key, "key")));
    }

    public Map<CharacterKey, CharacterDefinition> definitions() { return definitions; }

    public Resolution resolve(CharacterKey requested, Set<String> enabledOwners, CharacterKey fallback) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(enabledOwners, "enabledOwners");
        Objects.requireNonNull(fallback, "fallback");
        if (requested.ownerModId().filter(owner -> !enabledOwners.contains(owner)).isPresent()) {
            return new Resolution(requested, fallback, find(fallback), FallbackReason.DISABLED_OWNER);
        }
        Optional<CharacterDefinition> found = find(requested);
        return found.isPresent()
                ? new Resolution(requested, requested, found, FallbackReason.NONE)
                : new Resolution(requested, fallback, find(fallback), FallbackReason.UNKNOWN_KEY);
    }

    @ModApi
    public enum FallbackReason { NONE, UNKNOWN_KEY, DISABLED_OWNER }

    @ModApi
    public record Resolution(CharacterKey requestedKey, CharacterKey resolvedKey,
                             Optional<CharacterDefinition> definition, FallbackReason fallbackReason) {
        public Resolution {
            Objects.requireNonNull(requestedKey, "requestedKey");
            Objects.requireNonNull(resolvedKey, "resolvedKey");
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(fallbackReason, "fallbackReason");
        }
    }
}
