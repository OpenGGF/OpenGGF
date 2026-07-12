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
        return new CharacterDefinition(key, definition.displayName(),
                (code, x, y) -> faultBoundary.callCharacter(key,
                        () -> CharacterConstructionScope.call(key,
                                callback -> faultBoundary.callCharacter(key, callback::get),
                                () -> CharacterConstructionScope.validateFactoryResult(key,
                                        definition.spriteFactory().create(code, x, y)))),
                controller -> faultBoundary.callCharacter(key,
                        () -> Objects.requireNonNull(
                                definition.respawnStrategyFactory().create(controller),
                                "Mod respawn factory returned null for " + key.persisted())),
                definition.behavesLike(), definition.secondaryAbility(),
                definition.supportsSuperForm(),
                code -> faultBoundary.callCharacterIo(key,
                        () -> Objects.requireNonNull(definition.artSupplier().load(code),
                                "Mod character art supplier returned null for " + key.persisted())),
                definition.paletteSupplier() == null ? null
                        : code -> faultBoundary.callCharacterIo(key,
                        () -> Objects.requireNonNull(definition.paletteSupplier().load(code),
                                "Mod character palette supplier returned null for " + key.persisted())));
    }
}
