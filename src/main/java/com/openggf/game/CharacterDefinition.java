package com.openggf.game;

import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SecondaryAbility;
import com.openggf.sprites.playable.SidekickRespawnStrategy;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.sprites.playable.SonicRespawnStrategy;

import java.io.IOException;
import java.util.Objects;

/**
 * Immutable creator contribution describing one playable character.
 *
 * <p>The {@code key} must be the owner-scoped key supplied to
 * {@code ModContext.registerCharacter}. The factories and suppliers are invoked
 * through that owner's fault boundary. A null respawn factory selects
 * {@link SonicRespawnStrategy}; a null palette supplier retains the host-game
 * palette fallback. Mod characters must set {@code supportsSuperForm} to false in
 * playable format v2, and the {@link PlayerCharacter#KNUCKLES} archetype requires
 * {@link SecondaryAbility#GLIDE}.</p>
 */
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

    /** Creates a definition that relies on the host-game palette fallback. */
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
        /** Creates a playable at ROM-style centre coordinates for the persisted key. */
        AbstractPlayableSprite create(String persistedCode, int x, int y);
    }

    @ModApi
    @FunctionalInterface
    public interface RespawnStrategyFactory {
        /** Creates the strategy owned by one sidekick controller. */
        SidekickRespawnStrategy create(SidekickCpuController controller);
    }

    @ModApi
    @FunctionalInterface
    public interface ArtSupplier {
        /** Loads the complete playable art set for the canonical persisted key. */
        SpriteArtSet load(String persistedCode) throws IOException;
    }

    /** Optional character palette source; null retains the host-game fallback. */
    @ModApi
    @FunctionalInterface
    public interface PaletteSupplier {
        /** Loads the 16-colour playable palette for the canonical persisted key. */
        com.openggf.level.Palette load(String persistedCode) throws IOException;
    }
}
