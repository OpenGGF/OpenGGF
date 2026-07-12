package com.openggf.sprites.playable;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterAvailability;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameModule;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.mods.ModRuntimeFindingStore;
import com.openggf.mods.ModStateSaveResult;
import com.openggf.mods.code.ModBackedGamePatch;
import com.openggf.mods.code.ModFaultBoundary;
import com.openggf.mods.code.ModRegistrationPlan;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPlayableCharacterRegistry {
    @Test
    void stockModuleRegistryReproducesBuiltinSpriteAndRespawnFactories() {
        GameModule module = mock(GameModule.class, CALLS_REAL_METHODS);
        PlayableCharacterRegistry registry = module.getPlayableCharacterRegistry();

        assertEquals(Set.of(CharacterKey.SONIC, CharacterKey.TAILS, CharacterKey.KNUCKLES),
                registry.definitions().keySet());
        assertInstanceOf(Sonic.class, create(registry, CharacterKey.SONIC));
        assertInstanceOf(Tails.class, create(registry, CharacterKey.TAILS));
        assertInstanceOf(Knuckles.class, create(registry, CharacterKey.KNUCKLES));

        assertInstanceOf(SonicRespawnStrategy.class, respawn(registry, CharacterKey.SONIC));
        assertInstanceOf(TailsRespawnStrategy.class, respawn(registry, CharacterKey.TAILS));
        assertInstanceOf(KnucklesRespawnStrategy.class, respawn(registry, CharacterKey.KNUCKLES));

        AbstractPlayableSprite aliased = registry.find(CharacterKey.SONIC).orElseThrow()
                .spriteFactory().create("sonic_preview", 321, 654);
        Sonic legacy = new Sonic("sonic_preview", (short) 321, (short) 654);
        assertEquals("sonic_preview", aliased.getCode());
        assertEquals(legacy.getX(), aliased.getX());
        assertEquals(legacy.getY(), aliased.getY());
        assertEquals(legacy.getCentreX(), aliased.getCentreX());
        assertEquals(legacy.getCentreY(), aliased.getCentreY());
    }

    @Test
    void ownerTaggedNamesRemainDistinctAndInvalidKnucklesArchetypeIsRejected() {
        CharacterDefinition alpha = definition(CharacterKey.mod("owner-a", "runner"),
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, SonicRespawnStrategy::new);
        CharacterDefinition beta = definition(CharacterKey.mod("owner-b", "runner"),
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, SonicRespawnStrategy::new);
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(alpha.key(), alpha).register(beta.key(), beta);

        assertSame(alpha, registry.find(CharacterKey.mod("owner-a", "runner")).orElseThrow());
        assertSame(beta, registry.find(CharacterKey.mod("owner-b", "runner")).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> definition(
                CharacterKey.mod("owner-a", "climber"), PlayerCharacter.KNUCKLES,
                SecondaryAbility.NONE, KnucklesRespawnStrategy::new));
    }

    @Test
    void absentOwnerFallsBackAsUnknownRatherThanDisabled() {
        GameModule module = mock(GameModule.class, CALLS_REAL_METHODS);
        SonicConfigurationService config = config("missing-owner:runner", null);
        SpriteManager sprites = new SpriteManager(config);
        List<GameplayTeamBootstrap.CharacterFinding> findings = new ArrayList<>();

        var team = GameplayTeamBootstrap.registerActiveTeam(module, sprites, config,
                GameplayTeamBootstrap.DEFAULT_MAIN_X, GameplayTeamBootstrap.DEFAULT_MAIN_Y,
                findings::add);

        assertInstanceOf(Sonic.class, team.mainSprite());
        assertEquals(List.of(new GameplayTeamBootstrap.CharacterFinding(
                "missing-owner:runner", CharacterKey.SONIC.persisted(),
                PlayableCharacterRegistry.FallbackReason.UNKNOWN_KEY)), findings);
    }

    @Test
    void knownEnabledOwnerWithoutLocalDefinitionFallsBackAsUnknown() {
        GameModule module = mock(GameModule.class, CALLS_REAL_METHODS);
        PlayableCharacterRegistry registry = module.getPlayableCharacterRegistry();
        SonicConfigurationService config = config("known-owner:missing", null);
        List<GameplayTeamBootstrap.CharacterFinding> findings = new ArrayList<>();

        GameplayTeamBootstrap.registerActiveTeam(module, registry,
                CharacterAvailability.dynamic(owner -> owner.equals("known-owner"), owner -> true),
                new SpriteManager(config), config, GameplayTeamBootstrap.DEFAULT_MAIN_X,
                GameplayTeamBootstrap.DEFAULT_MAIN_Y, findings::add);

        assertEquals(PlayableCharacterRegistry.FallbackReason.UNKNOWN_KEY,
                findings.getFirst().reason());
    }

    @Test
    void knownDisabledOwnerFallsBackAsDisabledEvenWhenDefinitionIsPinned() {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty()
                .register(CharacterKey.SONIC, builtinSonic())
                .register(key, definition(key, PlayerCharacter.SONIC_ALONE,
                        SecondaryAbility.NONE, SonicRespawnStrategy::new));
        GameModule module = moduleWith(registry);
        SonicConfigurationService config = config(key.persisted(), null);
        List<GameplayTeamBootstrap.CharacterFinding> findings = new ArrayList<>();

        GameplayTeamBootstrap.registerActiveTeam(module, registry,
                CharacterAvailability.dynamic(owner -> true, owner -> false),
                new SpriteManager(config), config, GameplayTeamBootstrap.DEFAULT_MAIN_X,
                GameplayTeamBootstrap.DEFAULT_MAIN_Y, findings::add);

        assertEquals(PlayableCharacterRegistry.FallbackReason.DISABLED_OWNER,
                findings.getFirst().reason());
    }

    @Test
    void processDisabledOwnerCannotReinvokeFactoryFromPinnedRegistryOnRebootstrap() {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        CharacterDefinition contributed = new CharacterDefinition(key, "Runner",
                (code, x, y) -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("sprite");
                }, SonicRespawnStrategy::new, PlayerCharacter.SONIC_ALONE,
                SecondaryAbility.NONE, false, code -> SpriteArtSet.EMPTY);
        Set<String> disabled = new java.util.LinkedHashSet<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), new ModRuntimeFindingStore(),
                owners -> new ModStateSaveResult.Saved(), disabled::addAll);
        GameModule decorated = new ModBackedGamePatch(
                ModRegistrationPlan.characterOnly("owner", "s2", Map.of(key, contributed)), boundary)
                .apply(moduleWith(PlayableCharacterRegistry.empty()
                                .register(CharacterKey.SONIC, builtinSonic())),
                        new PatchContext(ignored -> null, mock(SonicConfigurationService.class)));
        PlayableCharacterRegistry pinned = decorated.getPlayableCharacterRegistry();
        CharacterAvailability availability = CharacterAvailability.dynamic(
                owner -> owner.equals("owner"), owner -> !disabled.contains(owner));
        SonicConfigurationService config = config(key.persisted(), null);

        assertThrows(ModFaultBoundary.CallbackAborted.class, () ->
                GameplayTeamBootstrap.registerActiveTeam(decorated, pinned, availability,
                        new SpriteManager(config), config, GameplayTeamBootstrap.DEFAULT_MAIN_X,
                        GameplayTeamBootstrap.DEFAULT_MAIN_Y, finding -> { }));
        var second = GameplayTeamBootstrap.registerActiveTeam(decorated, pinned, availability,
                new SpriteManager(config), config, GameplayTeamBootstrap.DEFAULT_MAIN_X,
                GameplayTeamBootstrap.DEFAULT_MAIN_Y, finding -> { });

        assertInstanceOf(Sonic.class, second.mainSprite());
        assertEquals(1, calls.get(), "disabled callback must not be invoked again");
    }

    @Test
    void explicitPinnedRegistryIsUsedWithoutRequeryAndSidekickAliasIsRegistered() {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        PlayableCharacterRegistry pinned = PlayableCharacterRegistry.empty()
                .register(CharacterKey.SONIC, builtinSonic())
                .register(key, definition(key, PlayerCharacter.TAILS_ALONE,
                        SecondaryAbility.FLY, TailsRespawnStrategy::new));
        GameModule module = moduleWith(pinned);
        clearInvocations(module);
        SonicConfigurationService config = config("sonic", key.persisted());
        SpriteManager sprites = new SpriteManager(config);

        var team = GameplayTeamBootstrap.registerActiveTeam(module, pinned,
                CharacterAvailability.fromRegistry(pinned), sprites, config,
                GameplayTeamBootstrap.DEFAULT_MAIN_X, GameplayTeamBootstrap.DEFAULT_MAIN_Y,
                finding -> { });

        verify(module, never()).getPlayableCharacterRegistry();
        assertEquals(1, team.sidekicks().size());
        assertEquals(key.persisted() + "_p2", team.sidekicks().getFirst().getCode());
        assertEquals(key.persisted(), sprites.getSidekickCharacterName(team.sidekicks().getFirst()));
    }

    @Test
    void sonicAloneModMainWithSidekicksIsRejectedBeforeAnySpritePublication() {
        CharacterDefinition runner = definition(CharacterKey.mod("owner", "runner"),
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, SonicRespawnStrategy::new);
        GameModule module = moduleWith(PlayableCharacterRegistry.empty()
                .register(CharacterKey.SONIC, builtinSonic())
                .register(runner.key(), runner));
        SonicConfigurationService config = config("owner:runner", "tails");
        SpriteManager sprites = new SpriteManager(config);

        assertThrows(IllegalArgumentException.class,
                () -> GameplayTeamBootstrap.registerActiveTeam(module, sprites, config));
        assertNull(sprites.getSprite("owner:runner"));
        assertTrue(sprites.getSidekicks().isEmpty());
    }

    @Test
    void sonicAloneValidationUsesConfiguredSidekicksEvenWhenHostSuppressesThem() {
        CharacterDefinition runner = definition(CharacterKey.mod("owner", "runner"),
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, SonicRespawnStrategy::new);
        GameModule module = moduleWith(PlayableCharacterRegistry.empty()
                .register(CharacterKey.SONIC, builtinSonic()).register(runner.key(), runner));
        when(module.supportsSidekick()).thenReturn(false);
        SonicConfigurationService config = config("owner:runner", "tails");
        SpriteManager sprites = new SpriteManager(config);

        assertThrows(IllegalArgumentException.class,
                () -> GameplayTeamBootstrap.registerActiveTeam(module, sprites, config));
        assertNull(sprites.getSprite("owner:runner"));
    }

    @Test
    void throwingSpriteAndRespawnFactoriesDisableOwnerClosureAndPublishNoPartialTeam() {
        assertFactoryAbortPublishesNothing(false);
        assertFactoryAbortPublishesNothing(true);
    }

    @Test
    void nullSpriteFactoryResultIsAnOwnerCallbackFailureNotAnEngineNullPointer() {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        CharacterDefinition contributed = new CharacterDefinition(key, "Runner",
                (code, x, y) -> null, SonicRespawnStrategy::new,
                PlayerCharacter.SONIC_ALONE, SecondaryAbility.NONE, false,
                code -> SpriteArtSet.EMPTY);
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of(), findings,
                owners -> new ModStateSaveResult.Saved(), owners -> { });
        GameModule decorated = new ModBackedGamePatch(
                ModRegistrationPlan.characterOnly("owner", "s2", Map.of(key, contributed)), boundary)
                .apply(moduleWith(mock(GameModule.class, CALLS_REAL_METHODS)
                        .getPlayableCharacterRegistry()),
                        new PatchContext(ignored -> null, mock(SonicConfigurationService.class)));

        assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> GameplayTeamBootstrap.registerActiveTeam(decorated, new SpriteManager(config(
                        key.persisted(), null)), config(key.persisted(), null)));
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
    }

    private static void assertFactoryAbortPublishesNothing(boolean throwFromRespawn) {
        CharacterKey key = CharacterKey.mod("owner", "runner");
        CharacterDefinition contributed = new CharacterDefinition(key, "Runner",
                (code, x, y) -> {
                    if (!throwFromRespawn) throw new IllegalStateException("sprite");
                    return new Sonic(code, (short) x, (short) y);
                }, controller -> {
                    if (throwFromRespawn) throw new IllegalStateException("respawn");
                    return new SonicRespawnStrategy(controller);
                }, PlayerCharacter.SONIC_AND_TAILS, SecondaryAbility.NONE, false,
                code -> SpriteArtSet.EMPTY);
        ModRuntimeFindingStore findings = new ModRuntimeFindingStore();
        List<Set<String>> disabled = new ArrayList<>();
        ModFaultBoundary boundary = new ModFaultBoundary(Map.of("dependent", Set.of("owner")),
                findings, owners -> new ModStateSaveResult.Saved(), disabled::add);
        ModRegistrationPlan plan = ModRegistrationPlan.characterOnly("owner", "s2", Map.of(key, contributed));
        GameModule decorated = new ModBackedGamePatch(plan, boundary).apply(
                moduleWith(mock(GameModule.class, CALLS_REAL_METHODS).getPlayableCharacterRegistry()),
                new PatchContext(ignored -> null, mock(SonicConfigurationService.class)));
        SonicConfigurationService config = config(throwFromRespawn ? "sonic" : key.persisted(),
                throwFromRespawn ? key.persisted() : null);
        SpriteManager sprites = new SpriteManager(config);

        ModFaultBoundary.CallbackAborted aborted = assertThrows(ModFaultBoundary.CallbackAborted.class,
                () -> GameplayTeamBootstrap.registerActiveTeam(decorated, sprites, config));

        assertEquals(Set.of("owner", "dependent"), aborted.disabledOwners());
        assertEquals("MOD_CALLBACK_FAILED", findings.findingsFor("owner").getFirst().code());
        assertEquals(List.of(aborted.disabledOwners()), disabled);
        assertNull(sprites.getSprite("sonic"));
        assertNull(sprites.getSprite(key.persisted()));
        assertTrue(sprites.getSidekicks().isEmpty());
    }

    private static AbstractPlayableSprite create(PlayableCharacterRegistry registry, CharacterKey key) {
        return registry.find(key).orElseThrow().spriteFactory().create(key.persisted(), 10, 20);
    }

    private static SidekickRespawnStrategy respawn(PlayableCharacterRegistry registry, CharacterKey key) {
        AbstractPlayableSprite sidekick = create(registry, key);
        SidekickCpuController controller = new SidekickCpuController(sidekick,
                new Sonic("leader", (short) 0, (short) 0));
        return registry.find(key).orElseThrow().respawnStrategyFactory().create(controller);
    }

    private static CharacterDefinition builtinSonic() {
        return definition(CharacterKey.SONIC, PlayerCharacter.SONIC_ALONE,
                SecondaryAbility.NONE, SonicRespawnStrategy::new);
    }

    private static CharacterDefinition definition(CharacterKey key, PlayerCharacter archetype,
                                                  SecondaryAbility ability,
                                                  CharacterDefinition.RespawnStrategyFactory respawn) {
        return new CharacterDefinition(key, key.persisted(),
                (code, x, y) -> new Sonic(code, (short) x, (short) y), respawn,
                archetype, ability, key.isBuiltin(), code -> SpriteArtSet.EMPTY);
    }

    private static GameModule moduleWith(PlayableCharacterRegistry registry) {
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(registry);
        when(module.supportsSidekick()).thenReturn(true);
        return module;
    }

    private static SonicConfigurationService config(String main, String sidekicks) {
        SonicConfigurationService config = mock(SonicConfigurationService.class);
        when(config.getString(SonicConfiguration.MAIN_CHARACTER_CODE)).thenReturn(main);
        when(config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE)).thenReturn(sidekicks);
        return config;
    }
}
