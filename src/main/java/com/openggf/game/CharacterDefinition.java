package com.openggf.game;

import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.playable.SidekickRespawnStrategy;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.SonicRespawnStrategy;

import java.io.IOException;
import java.util.Objects;

/** Immutable creator contribution describing one playable character. */
@ModApi
public record CharacterDefinition(
        CharacterKey key,
        String displayName,
        PlayableFactory spriteFactory,
        RespawnStrategyFactory respawnStrategyFactory,
        PlayerCharacter behavesLike,
        SecondaryAbility secondaryAbility,
        boolean supportsSuperForm,
        ArtSupplier artSupplier,
        PaletteSupplier paletteSupplier) {

    /** Compatibility constructor for definitions that rely on the host-game palette. */
    public CharacterDefinition(CharacterKey key, String displayName,
                               PlayableFactory spriteFactory,
                               RespawnStrategyFactory respawnStrategyFactory,
                               PlayerCharacter behavesLike,
                               SecondaryAbility secondaryAbility,
                               boolean supportsSuperForm,
                               ArtSupplier artSupplier) {
        this(key, displayName, spriteFactory, respawnStrategyFactory, behavesLike,
                secondaryAbility, supportsSuperForm, artSupplier, null);
    }

    public CharacterDefinition {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        Objects.requireNonNull(spriteFactory, "spriteFactory");
        respawnStrategyFactory = respawnStrategyFactory == null ? SonicRespawnStrategy::new : respawnStrategyFactory;
        Objects.requireNonNull(behavesLike, "behavesLike");
        Objects.requireNonNull(secondaryAbility, "secondaryAbility");
        Objects.requireNonNull(artSupplier, "artSupplier");
        if (!key.isBuiltin() && supportsSuperForm) {
            throw new IllegalArgumentException("Mod characters cannot enable super forms in playable v2");
        }
        if (behavesLike == PlayerCharacter.KNUCKLES && secondaryAbility != SecondaryAbility.GLIDE) {
            throw new IllegalArgumentException("KNUCKLES archetype requires GLIDE");
        }
    }

    @ModApi
    @FunctionalInterface
    public interface PlayableFactory {
        AbstractPlayableSprite create(String persistedCode, int x, int y);
    }

    @ModApi
    @FunctionalInterface
    public interface RespawnStrategyFactory {
        SidekickRespawnStrategy create(SidekickCpuController controller);
    }

    @ModApi
    @FunctionalInterface
    public interface ArtSupplier {
        SpriteArtSet load(String persistedCode) throws IOException;
    }

    /** Optional character palette source; null retains the host-game fallback. */
    @ModApi
    @FunctionalInterface
    public interface PaletteSupplier {
        com.openggf.level.Palette load(String persistedCode) throws IOException;
    }
}
