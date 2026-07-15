package com.openggf.game.session;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameModule;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterAvailability;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.SidekickSpawnOffset;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Shared gameplay-team bootstrap used by both runtime startup and headless tests.
 *
 * <p>This mirrors the engine's pre-level-load team construction: create the
 * active main character, register any configured sidekicks with chained CPU
 * controllers, then let the level-load path place them at the actual spawn.
 */
public final class GameplayTeamBootstrap {
    private static final Logger LOG = Logger.getLogger(GameplayTeamBootstrap.class.getName());
    public static final short DEFAULT_MAIN_X = 100;
    public static final short DEFAULT_MAIN_Y = 624;

    private static final int SIDEKICK_SCREEN_SPACING = 0x20;
    private static final int SIDEKICK_Y_OFFSET = 4;

    private GameplayTeamBootstrap() {
    }

    public static BootstrappedTeam registerActiveTeam(GameModule module,
                                                      SpriteManager spriteManager,
                                                      SonicConfigurationService configService) {
        return registerActiveTeam(module, spriteManager, configService,
                DEFAULT_MAIN_X, DEFAULT_MAIN_Y, finding -> LOG.warning(
                        "Playable character " + finding.requestedCode() + " fell back to "
                                + finding.fallbackCode() + ": " + finding.reason()));
    }

    public static BootstrappedTeam registerActiveTeam(GameModule module,
                                                      SpriteManager spriteManager,
                                                      SonicConfigurationService configService,
                                                      short mainX,
                                                      short mainY) {
        return registerActiveTeam(module, spriteManager, configService, mainX, mainY,
                finding -> LOG.warning("Playable character " + finding.requestedCode()
                        + " fell back to " + finding.fallbackCode() + ": " + finding.reason()));
    }

    public static BootstrappedTeam registerActiveTeam(GameModule module,
                                                      SpriteManager spriteManager,
                                                      SonicConfigurationService configService,
                                                      short mainX,
                                                      short mainY,
                                                      CharacterFindingSink findingSink) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(spriteManager, "spriteManager");
        Objects.requireNonNull(configService, "configService");
        Objects.requireNonNull(findingSink, "findingSink");

        PlayableCharacterRegistry registry = Objects.requireNonNull(
                module.getPlayableCharacterRegistry(), "playableCharacterRegistry");
        return registerActiveTeam(module, registry, CharacterAvailability.fromRegistry(registry),
                spriteManager, configService, mainX, mainY, findingSink);
    }

    public static BootstrappedTeam registerActiveTeam(GameModule module,
                                                      PlayableCharacterRegistry registry,
                                                      CharacterAvailability availability,
                                                      SpriteManager spriteManager,
                                                      SonicConfigurationService configService,
                                                      short mainX,
                                                      short mainY,
                                                      CharacterFindingSink findingSink) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(availability, "availability");
        Objects.requireNonNull(spriteManager, "spriteManager");
        Objects.requireNonNull(configService, "configService");
        Objects.requireNonNull(findingSink, "findingSink");

        String mainCharacter = ActiveGameplayTeamResolver.resolveMainCharacterCode(configService);
        ResolvedCharacter main = resolve(registry, availability, mainCharacter, findingSink);
        List<String> configuredSidekickNames = ActiveGameplayTeamResolver.resolveSidekicks(configService);
        if (!main.key().isBuiltin() && main.definition().behavesLike()
                == com.openggf.game.PlayerCharacter.SONIC_ALONE && !configuredSidekickNames.isEmpty()) {
            throw new IllegalArgumentException("SONIC_ALONE mod main cannot be configured with sidekicks: "
                    + mainCharacter);
        }
        List<String> sidekickNames = (module.supportsSidekick() || CrossGameFeatureProvider.isActive())
                ? configuredSidekickNames : List.of();
        AbstractPlayableSprite mainSprite = main.definition().spriteFactory()
                .create(mainCharacter, mainX, mainY);

        List<AbstractPlayableSprite> sidekicks = new ArrayList<>();
        List<String> resolvedSidekickNames = new ArrayList<>();
        if (!sidekickNames.isEmpty()) {
            AbstractPlayableSprite previousLeader = mainSprite;
            for (int i = 0; i < sidekickNames.size(); i++) {
                String characterName = sidekickNames.get(i);
                ResolvedCharacter resolved = resolve(
                        registry, availability, characterName, findingSink);
                String code = characterName + "_p" + (i + 2);
                int desiredX = mainSprite.getX() - SIDEKICK_SCREEN_SPACING * (i + 1);
                int spawnX = Math.max(0, desiredX);
                boolean offScreen = desiredX < 0;

                AbstractPlayableSprite sidekick = resolved.definition().spriteFactory().create(
                        code, spawnX, mainSprite.getY() + SIDEKICK_Y_OFFSET);
                sidekick.setCpuControlled(true);

                SidekickCpuController controller = new SidekickCpuController(sidekick, previousLeader);
                controller.setSidekickCount(sidekickNames.size());
                if (offScreen) {
                    controller.setInitialState(SidekickCpuController.State.SPAWNING);
                }
                controller.setRespawnStrategy(
                        resolved.definition().respawnStrategyFactory().create(controller));
                sidekick.setCpuController(controller);

                // Wire any game-specific sidekick carry trigger (S3K CNZ1 Tails carry).
                // S1/S2 modules return null from getSidekickCarryTrigger(), so this is
                // a no-op branch for those games and for S3K zones outside CNZ1.
                controller.setCarryTrigger(module.getSidekickCarryTrigger());

                sidekicks.add(sidekick);
                resolvedSidekickNames.add(resolved.key().persisted());
                previousLeader = sidekick;
            }
        }

        // Factories are creator callbacks. Publish only after the entire graph is built so
        // a typed callback abort cannot leave a main or prefix of the sidekick chain live.
        spriteManager.addSprite(mainSprite);
        for (int i = 0; i < sidekicks.size(); i++) {
            spriteManager.addSprite(sidekicks.get(i), resolvedSidekickNames.get(i));
        }

        return new BootstrappedTeam(mainSprite, List.copyOf(sidekicks));
    }

    public static void repositionRegisteredSidekicks(GameModule module, LevelManager levelManager) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(levelManager, "levelManager");

        SidekickSpawnOffset offset = module.getLevelInitProfile().sidekickSpawnOffset();
        levelManager.spawnSidekicks(offset.xOffset(), offset.yOffset());
    }

    /** Rejects a required launch-local team unless every exact key is playable. */
    public static void requireExactTeam(PlayableCharacterRegistry registry,
                                        GameplayLaunchTeam team) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(team, "team");
        requireExactCharacter(registry, team.main());
        team.sidekicks().forEach(key -> requireExactCharacter(registry, key));
    }

    private static void requireExactCharacter(PlayableCharacterRegistry registry, CharacterKey key) {
        if (registry.find(key).isEmpty()) throw new UnavailableRequiredCharacter(key);
    }

    public static final class UnavailableRequiredCharacter extends IllegalArgumentException {
        private final CharacterKey key;

        private UnavailableRequiredCharacter(CharacterKey key) {
            super("Required launch character is unavailable: " + key.persisted());
            this.key = key;
        }

        public CharacterKey key() {
            return key;
        }
    }

    private static ResolvedCharacter resolve(PlayableCharacterRegistry registry,
                                             CharacterAvailability availability,
                                             String requestedCode,
                                             CharacterFindingSink findingSink) {
        CharacterKey requested;
        try {
            String canonical = requestedCode.equalsIgnoreCase("sonic")
                    || requestedCode.equalsIgnoreCase("tails")
                    || requestedCode.equalsIgnoreCase("knuckles")
                    ? requestedCode.toLowerCase(java.util.Locale.ROOT) : requestedCode;
            requested = CharacterKey.parsePersisted(canonical);
        } catch (IllegalArgumentException failure) {
            CharacterDefinition sonic = registry.find(CharacterKey.SONIC).orElseThrow(() ->
                    new IllegalStateException("Resolved module has no Sonic fallback", failure));
            findingSink.record(new CharacterFinding(requestedCode, CharacterKey.SONIC.persisted(),
                    PlayableCharacterRegistry.FallbackReason.UNKNOWN_KEY));
            return new ResolvedCharacter(CharacterKey.SONIC, sonic);
        }
        java.util.Optional<String> owner = requested.ownerModId();
        PlayableCharacterRegistry.Resolution resolution;
        if (owner.isPresent() && !availability.isKnownOwner(owner.orElseThrow())) {
            resolution = fallback(registry, requested,
                    PlayableCharacterRegistry.FallbackReason.UNKNOWN_KEY);
        } else if (owner.isPresent() && !availability.isEnabledOwner(owner.orElseThrow())) {
            resolution = fallback(registry, requested,
                    PlayableCharacterRegistry.FallbackReason.DISABLED_OWNER);
        } else {
            java.util.Optional<CharacterDefinition> found = registry.find(requested);
            resolution = found.isPresent()
                    ? new PlayableCharacterRegistry.Resolution(requested, requested, found,
                    PlayableCharacterRegistry.FallbackReason.NONE)
                    : fallback(registry, requested,
                    PlayableCharacterRegistry.FallbackReason.UNKNOWN_KEY);
        }
        CharacterDefinition definition = resolution.definition().orElseThrow(() ->
                new IllegalStateException("Resolved module has no playable definition for "
                        + resolution.resolvedKey().persisted()));
        if (resolution.fallbackReason() != PlayableCharacterRegistry.FallbackReason.NONE) {
            findingSink.record(new CharacterFinding(requestedCode,
                    resolution.resolvedKey().persisted(), resolution.fallbackReason()));
        }
        return new ResolvedCharacter(resolution.resolvedKey(), definition);
    }

    private static PlayableCharacterRegistry.Resolution fallback(
            PlayableCharacterRegistry registry, CharacterKey requested,
            PlayableCharacterRegistry.FallbackReason reason) {
        return new PlayableCharacterRegistry.Resolution(requested, CharacterKey.SONIC,
                registry.find(CharacterKey.SONIC), reason);
    }

    private record ResolvedCharacter(CharacterKey key, CharacterDefinition definition) { }

    @FunctionalInterface
    public interface CharacterFindingSink {
        void record(CharacterFinding finding);
    }

    public record CharacterFinding(String requestedCode, String fallbackCode,
                                   PlayableCharacterRegistry.FallbackReason reason) {
        public CharacterFinding {
            Objects.requireNonNull(requestedCode, "requestedCode");
            Objects.requireNonNull(fallbackCode, "fallbackCode");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public record BootstrappedTeam(AbstractPlayableSprite mainSprite,
                                   List<AbstractPlayableSprite> sidekicks) {
        public BootstrappedTeam {
            sidekicks = List.copyOf(sidekicks);
        }
    }
}
