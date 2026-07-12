package com.openggf.game;

import java.util.Objects;
import java.util.function.Supplier;

/** Engine-owned construction identity binding for playable-character factories. */
public final class CharacterConstructionScope {
    private static final ThreadLocal<CharacterKey> EXPECTED_KEY = new ThreadLocal<>();

    private CharacterConstructionScope() { }

    /** Runs a character factory under its registry-owned identity. */
    public static <T> T call(CharacterKey expectedKey, Supplier<T> factory) {
        CharacterKey expected = Objects.requireNonNull(expectedKey, "expectedKey");
        Supplier<T> callback = Objects.requireNonNull(factory, "factory");
        CharacterKey previous = EXPECTED_KEY.get();
        if (previous != null && !previous.equals(expected)) {
            throw mismatch(previous, expected);
        }
        if (previous == null) EXPECTED_KEY.set(expected);
        try {
            return callback.get();
        } finally {
            if (previous == null) EXPECTED_KEY.remove();
        }
    }

    /** Binds the scoped registry identity, or a safe built-in fallback outside a factory scope. */
    public static CharacterKey bindExpectedOrDefault(CharacterKey defaultKey) {
        CharacterKey fallback = Objects.requireNonNull(defaultKey, "defaultKey");
        CharacterKey expected = EXPECTED_KEY.get();
        return expected == null ? fallback : expected;
    }

    /** Revalidates a factory result before it can be published. */
    public static void validateDeclared(CharacterKey expectedKey, CharacterKey declaredKey) {
        CharacterKey expected = Objects.requireNonNull(expectedKey, "expectedKey");
        CharacterKey declared = Objects.requireNonNull(declaredKey,
                "Playable character declared a null characterKey");
        if (!expected.equals(declared)) throw mismatch(expected, declared);
    }

    /** Validates an engine playable returned by an untrusted factory without exposing it to mods code. */
    public static <T> T validateFactoryResult(CharacterKey expectedKey, T result) {
        Object value = Objects.requireNonNull(result, "Mod sprite factory returned null");
        if (!(value instanceof com.openggf.sprites.playable.AbstractPlayableSprite sprite)) {
            throw new IllegalArgumentException("Mod character factory returned a non-playable result");
        }
        // A stable declaration must survive repeated observation after construction.
        validateDeclared(expectedKey, sprite.characterKey());
        validateDeclared(expectedKey, sprite.characterKey());
        return result;
    }

    private static IllegalArgumentException mismatch(CharacterKey expected, CharacterKey declared) {
        return new IllegalArgumentException("Playable character identity mismatch: expected "
                + expected.persisted() + " but declared " + declared.persisted());
    }
}
