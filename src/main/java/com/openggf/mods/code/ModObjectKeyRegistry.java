package com.openggf.mods.code;

import com.openggf.game.ModKeySyntax;
import com.openggf.level.objects.ObjectFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable registry of compiled-mod object factories keyed outside the stock byte-id namespace. */
public final class ModObjectKeyRegistry {
    private final Map<String, OwnedFactory> factories;

    public ModObjectKeyRegistry(Collection<Registration> registrations) {
        Objects.requireNonNull(registrations, "registrations");
        Map<String, OwnedFactory> built = new LinkedHashMap<>();
        for (Registration registration : registrations) {
            Objects.requireNonNull(registration, "registration");
            if (registration.ownerModId() == null) {
                throw new IllegalArgumentException("Object registration owner is required");
            }
            String owner = ModKeySyntax.requireManifestId(registration.ownerModId());
            String key = ModKeySyntax.requireDisplayKey(registration.namespacedKey());
            if (!key.startsWith(owner + ":")) {
                throw new IllegalArgumentException("Object key owner does not match registration owner");
            }
            OwnedFactory previous = built.putIfAbsent(key,
                    new OwnedFactory(owner, Objects.requireNonNull(registration.factory(), "factory")));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate object key: " + key);
            }
        }
        factories = java.util.Collections.unmodifiableMap(built);
    }

    private ModObjectKeyRegistry(Map<String, OwnedFactory> factories) {
        this.factories = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(factories));
    }

    ModObjectKeyRegistry mergedWith(ModObjectKeyRegistry later) {
        Objects.requireNonNull(later, "later");
        Map<String, OwnedFactory> merged = new LinkedHashMap<>(factories);
        for (Map.Entry<String, OwnedFactory> entry : later.factories.entrySet()) {
            if (merged.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Duplicate object key: " + entry.getKey());
            }
        }
        return new ModObjectKeyRegistry(merged);
    }

    ObjectFactory requireFactory(String ownerModId, String namespacedKey) {
        String owner = ModKeySyntax.requireManifestId(ownerModId);
        String key = ModKeySyntax.requireDisplayKey(namespacedKey);
        OwnedFactory found = factories.get(key);
        if (found == null || !found.ownerModId().equals(owner)) {
            throw new IllegalArgumentException("No object factory registered for " + owner + " / " + key);
        }
        return found.factory();
    }

    boolean contains(String namespacedKey) {
        return factories.containsKey(ModKeySyntax.requireDisplayKey(namespacedKey));
    }

    java.util.List<String> keys() {
        return java.util.List.copyOf(factories.keySet());
    }

    public record Registration(String ownerModId, String namespacedKey, ObjectFactory factory) {}

    private record OwnedFactory(String ownerModId, ObjectFactory factory) {}
}
