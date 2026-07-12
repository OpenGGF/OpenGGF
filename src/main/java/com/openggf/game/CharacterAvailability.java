package com.openggf.game;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Dynamic owner availability used when resolving a pinned character registry. */
public interface CharacterAvailability {
    boolean isKnownOwner(String ownerModId);

    boolean isEnabledOwner(String ownerModId);

    static CharacterAvailability fromRegistry(PlayableCharacterRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        Set<String> owners = registry.definitions().keySet().stream()
                .map(CharacterKey::ownerModId)
                .flatMap(java.util.Optional::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return dynamic(owners::contains, owners::contains);
    }

    static CharacterAvailability dynamic(Predicate<String> knownOwner,
                                         Predicate<String> enabledOwner) {
        Objects.requireNonNull(knownOwner, "knownOwner");
        Objects.requireNonNull(enabledOwner, "enabledOwner");
        return new CharacterAvailability() {
            @Override public boolean isKnownOwner(String ownerModId) {
                return knownOwner.test(Objects.requireNonNull(ownerModId, "ownerModId"));
            }

            @Override public boolean isEnabledOwner(String ownerModId) {
                return enabledOwner.test(Objects.requireNonNull(ownerModId, "ownerModId"));
            }
        };
    }
}
