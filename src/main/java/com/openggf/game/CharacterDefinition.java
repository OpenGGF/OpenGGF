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
 * palette fallback. Mod characters enabling super forms must supply a profile.
 * The {@link PlayerCharacter#KNUCKLES} archetype requires
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
        PaletteSupplier paletteSupplier, java.util.Set<MovementAbility> movementAbilities,
        PaletteSupplier underwaterPaletteSupplier, PresentationSupplier presentationSupplier,
        SuperFormSupplier superFormSupplier, CharacterKey defaultSidekick) {

    /** Compatibility constructor: selecting this character defaults to solo. */
    public CharacterDefinition(CharacterKey key, String displayName, PlayableFactory spriteFactory,
            RespawnStrategyFactory respawnStrategyFactory, PlayerCharacter behavesLike,
            SecondaryAbility secondaryAbility, boolean supportsSuperForm, ArtSupplier artSupplier,
            PaletteSupplier paletteSupplier, java.util.Set<MovementAbility> movementAbilities,
            PaletteSupplier underwaterPaletteSupplier, PresentationSupplier presentationSupplier,
            SuperFormSupplier superFormSupplier) {
        this(key, displayName, spriteFactory, respawnStrategyFactory, behavesLike, secondaryAbility,
                supportsSuperForm, artSupplier, paletteSupplier, movementAbilities, underwaterPaletteSupplier,
                presentationSupplier, superFormSupplier, null);
    }

    /** Existing definitions retain all native presentation fallbacks and have no super profile. */
    public CharacterDefinition(CharacterKey key, String displayName, PlayableFactory spriteFactory,
            RespawnStrategyFactory respawnStrategyFactory, PlayerCharacter behavesLike,
            SecondaryAbility secondaryAbility, boolean supportsSuperForm, ArtSupplier artSupplier,
            PaletteSupplier paletteSupplier, java.util.Set<MovementAbility> movementAbilities,
            PaletteSupplier underwaterPaletteSupplier) {
        this(key, displayName, spriteFactory, respawnStrategyFactory, behavesLike, secondaryAbility,
                supportsSuperForm, artSupplier, paletteSupplier, movementAbilities, underwaterPaletteSupplier, null, null);
    }

    @ModApi @FunctionalInterface
    public interface PresentationSupplier {
        CharacterPresentation load(String persistedCode) throws IOException;
    }

    /** Loads the owner-bound super-form profile for the canonical persisted key. */
    @ModApi @FunctionalInterface
    public interface SuperFormSupplier { SuperFormProfile load(String persistedCode) throws IOException; }

    /** Null underwater supplier uses the renderer's zone-specific host palette derivation. */
    public CharacterDefinition(CharacterKey key, String displayName, PlayableFactory spriteFactory,
            RespawnStrategyFactory respawnStrategyFactory, PlayerCharacter behavesLike,
            SecondaryAbility secondaryAbility, boolean supportsSuperForm, ArtSupplier artSupplier,
            PaletteSupplier paletteSupplier, java.util.Set<MovementAbility> movementAbilities) {
        this(key, displayName, spriteFactory, respawnStrategyFactory, behavesLike, secondaryAbility,
                supportsSuperForm, artSupplier, paletteSupplier, movementAbilities, null);
    }

    @ModApi public enum MovementAbility {
        SPINDASH,
        /** Mod-defined S3K flight in hosts that do not natively enable it. */
        CROSS_GAME_FLIGHT,
        /** Twice the native flap acceleration while the authored super form is active. */
        SUPER_FLIGHT_LIFT
    }

    public CharacterDefinition(CharacterKey key, String displayName, PlayableFactory spriteFactory,
            RespawnStrategyFactory respawnStrategyFactory, PlayerCharacter behavesLike,
            SecondaryAbility secondaryAbility, boolean supportsSuperForm, ArtSupplier artSupplier,
            PaletteSupplier paletteSupplier) {
        this(key, displayName, spriteFactory, respawnStrategyFactory, behavesLike, secondaryAbility,
                supportsSuperForm, artSupplier, paletteSupplier, java.util.Set.of());
    }

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
        movementAbilities = java.util.Set.copyOf(movementAbilities);
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        Objects.requireNonNull(spriteFactory, "spriteFactory");
        respawnStrategyFactory = respawnStrategyFactory == null ? SonicRespawnStrategy::new : respawnStrategyFactory;
        Objects.requireNonNull(behavesLike, "behavesLike");
        Objects.requireNonNull(secondaryAbility, "secondaryAbility");
        if (defaultSidekick != null && (behavesLike == PlayerCharacter.SONIC_ALONE
                || !defaultSidekick.ownerModId().equals(key.ownerModId()))) {
            throw new IllegalArgumentException("Default sidekick must share the character owner and team route");
        }
        Objects.requireNonNull(artSupplier, "artSupplier");
        if (!key.isBuiltin() && supportsSuperForm && superFormSupplier == null) {
            throw new IllegalArgumentException("Mod super forms require an authored profile");
        }
        if (behavesLike == PlayerCharacter.KNUCKLES && secondaryAbility != SecondaryAbility.GLIDE) {
            throw new IllegalArgumentException("KNUCKLES archetype requires GLIDE");
        }
    }

    @ModApi
    @FunctionalInterface
    public interface PlayableFactory {
        /**
         * Creates a fresh playable at ROM-style centre coordinates. The host binds the definition's
         * canonical key; runtime sprite names can carry host suffixes and need no identity parsing.
         */
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
