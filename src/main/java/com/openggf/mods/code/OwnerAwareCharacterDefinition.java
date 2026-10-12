package com.openggf.mods.code;

import com.openggf.game.CharacterConstructionScope;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;

import java.util.Objects;

/** Shared engine-owned decorator for callbacks held by a mod character definition. */
final class OwnerAwareCharacterDefinition {
    private OwnerAwareCharacterDefinition() { }

    static CharacterDefinition wrap(CharacterKey key, CharacterDefinition definition,
                                    ModFaultBoundary faultBoundary) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(faultBoundary, "faultBoundary");
        String owner=key.ownerModId().orElseThrow(() ->
                new IllegalArgumentException("Registered mod characters require an owner-scoped key"));
        return wrap(owner,key,definition,faultBoundary,new com.openggf.mods.runtime.OwnerBoundCallbacks(
                owner,faultBoundary,com.openggf.io.ModInputLimits.production().maxCollectionEntries(),java.util.Map.of()));
    }

    static CharacterDefinition wrap(String owner, CharacterKey key, CharacterDefinition definition,
            ModFaultBoundary faultBoundary, com.openggf.mods.runtime.OwnerBoundCallbacks callbacks) {
        return new CharacterDefinition(key, definition.displayName(),
                (code, x, y) -> faultBoundary.call(owner,
                        () -> CharacterConstructionScope.call(key,
                                callback -> faultBoundary.call(owner, callback::get),
                                () -> CharacterConstructionScope.validateFactoryResult(key,
                                        definition.spriteFactory().create(code, x, y)))),
                controller -> faultBoundary.call(owner, () -> {
                    var strategy=Objects.requireNonNull(definition.respawnStrategyFactory().create(controller),
                            "Mod respawn factory returned null for " + key.persisted());
                    // Native CPU routines inspect these exact concrete strategies. Their methods
                    // are engine code; custom strategies retain the creator callback boundary.
                    Class<?> type=strategy.getClass();
                    return type==com.openggf.sprites.playable.SonicRespawnStrategy.class
                            || type==com.openggf.sprites.playable.TailsRespawnStrategy.class
                            || type==com.openggf.sprites.playable.KnucklesRespawnStrategy.class
                            ? strategy : callbacks.bind(com.openggf.sprites.playable.SidekickRespawnStrategy.class,strategy);
                }),
                definition.behavesLike(), definition.secondaryAbility(),
                definition.supportsSuperForm(),
                code -> callIo(owner,faultBoundary, () -> Objects.requireNonNull(definition.artSupplier().load(code),
                        "Mod character art supplier returned null for " + key.persisted())),
                definition.paletteSupplier() == null ? null
                        : code -> callIo(owner,faultBoundary, () -> Objects.requireNonNull(definition.paletteSupplier().load(code),
                        "Mod character palette supplier returned null for " + key.persisted())));
    }

    private static <T> T callIo(String owner, ModFaultBoundary boundary, ModFaultBoundary.IoSupplier<T> callback) {
        return boundary.call(owner,()-> {
            try { return callback.get(); }
            catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        });
    }
}
