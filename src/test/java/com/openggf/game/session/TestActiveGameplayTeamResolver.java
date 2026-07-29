package com.openggf.game.session;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.RuntimeArtCoordinator;
import com.openggf.game.save.SaveSessionContext;
import com.openggf.game.save.SelectedTeam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Isolated
class TestActiveGameplayTeamResolver {

    private SonicConfigurationService config;

    @BeforeEach
    void setUp() {
        config = SonicConfigurationService.getInstance();
        config.resetToDefaults();
    }

    @AfterEach
    void tearDown() {
        SessionManager.clear();
        config.resetToDefaults();
    }

    // --- resolveMainCharacterCode ---

    @Test
    void resolveMainCharacterCode_noSession_returnsConfig() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        assertEquals("knuckles", ActiveGameplayTeamResolver.resolveMainCharacterCode(config));
    }

    @Test
    void resolveMainCharacterCode_withSession_prefersSessionOverConfig() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        SessionManager.openGameplaySession(neutralGameModule(),
                SaveSessionContext.noSave("s3k", new SelectedTeam("knuckles", List.of()), 0, 0));
        assertEquals("knuckles", ActiveGameplayTeamResolver.resolveMainCharacterCode(config));
    }

    @Test
    void resolveMainCharacterCode_nullConfig_returnsSonic() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "");
        assertEquals("sonic", ActiveGameplayTeamResolver.resolveMainCharacterCode(config));
    }

    // --- resolvePlayerCharacter ---

    @Test
    void resolvePlayerCharacter_noSession_sonicWithTails_returnsSonicAndTails() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        assertEquals(PlayerCharacter.SONIC_AND_TAILS,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_noSession_sonicAlone_returnsSonicAlone() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        assertEquals(PlayerCharacter.SONIC_ALONE,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_noSession_knuckles_returnsKnuckles() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        assertEquals(PlayerCharacter.KNUCKLES,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_noSession_tails_returnsTailsAlone() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "tails");
        assertEquals(PlayerCharacter.TAILS_ALONE,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_sessionKnuckles_configSonic_returnsKnuckles() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        SessionManager.openGameplaySession(neutralGameModule(),
                SaveSessionContext.noSave("s3k", new SelectedTeam("knuckles", List.of()), 0, 0));
        assertEquals(PlayerCharacter.KNUCKLES,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_sessionSonicWithTails_configKnuckles_returnsSonicAndTails() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        SessionManager.openGameplaySession(neutralGameModule(),
                SaveSessionContext.noSave("s3k",
                        new SelectedTeam("sonic", List.of("tails")), 0, 0));
        assertEquals(PlayerCharacter.SONIC_AND_TAILS,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_sessionTails_configSonic_returnsTailsAlone() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        SessionManager.openGameplaySession(neutralGameModule(),
                SaveSessionContext.noSave("s3k",
                        new SelectedTeam("tails", List.of()), 0, 0));
        assertEquals(PlayerCharacter.TAILS_ALONE,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_sessionSonicAlone_configSonicAndTails_returnsSonicAlone() {
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        SessionManager.openGameplaySession(neutralGameModule(),
                SaveSessionContext.noSave("s3k",
                        new SelectedTeam("sonic", List.of()), 0, 0));
        assertEquals(PlayerCharacter.SONIC_ALONE,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_modMain_usesEveryDeclaredArchetype() {
        for (PlayerCharacter archetype : PlayerCharacter.values()) {
            CharacterKey key = CharacterKey.mod("owner-a", "hero-" + archetype.name().toLowerCase());
            openSession(registryWith(definition(key, archetype)),
                    new SelectedTeam(key.persisted(), List.of()));

            assertEquals(archetype, ActiveGameplayTeamResolver.resolvePlayerCharacter(config),
                    () -> "declared archetype for " + key.persisted());
        }
    }

    @Test
    void resolvePlayerCharacter_sameLocalName_keepsOwnerQualifiedDefinitionsDistinct() {
        CharacterKey ownerA = CharacterKey.mod("owner-a", "hero");
        CharacterKey ownerB = CharacterKey.mod("owner-b", "hero");
        PlayableCharacterRegistry registry = registryWith(
                definition(ownerA, PlayerCharacter.TAILS_ALONE),
                definition(ownerB, PlayerCharacter.KNUCKLES));

        openSession(registry, new SelectedTeam(ownerA.persisted(), List.of()));
        assertEquals(PlayerCharacter.TAILS_ALONE,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));

        openSession(registry, new SelectedTeam(ownerB.persisted(), List.of()));
        assertEquals(PlayerCharacter.KNUCKLES,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_unknownOrDisabledMod_fallsBackToExistingSonicSemantics() {
        openSession(PlayableCharacterRegistry.empty(),
                new SelectedTeam("unknown-owner:hero", List.of()));
        assertEquals(PlayerCharacter.SONIC_ALONE,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));

        openSession(PlayableCharacterRegistry.empty(),
                new SelectedTeam("disabled-owner:hero", List.of("tails")));
        assertEquals(PlayerCharacter.SONIC_AND_TAILS,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_sonicAloneModWithSidekick_isRejectedNotRemapped() {
        CharacterKey key = CharacterKey.mod("owner-a", "solo");
        openSession(registryWith(definition(key, PlayerCharacter.SONIC_ALONE)),
                new SelectedTeam(key.persisted(), List.of("tails")));

        assertThrows(IllegalArgumentException.class,
                () -> ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolvePlayerCharacter_nonSonicModWithSidekick_keepsDeclaredArchetype() {
        CharacterKey key = CharacterKey.mod("owner-a", "glider");
        openSession(registryWith(definition(key, PlayerCharacter.KNUCKLES)),
                new SelectedTeam(key.persisted(), List.of("tails")));

        assertEquals(PlayerCharacter.KNUCKLES,
                ActiveGameplayTeamResolver.resolvePlayerCharacter(config));
    }

    @Test
    void resolveSidekicks_noSession_parsesCommaSeparatedConfig() {
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails, knuckles, sonic");

        assertEquals(List.of("tails", "knuckles", "sonic"),
                ActiveGameplayTeamResolver.resolveSidekicks(config));
    }

    private static CharacterDefinition definition(CharacterKey key, PlayerCharacter archetype) {
        CharacterDefinition definition = mock(CharacterDefinition.class);
        when(definition.key()).thenReturn(key);
        when(definition.behavesLike()).thenReturn(archetype);
        return definition;
    }

    private static PlayableCharacterRegistry registryWith(CharacterDefinition... definitions) {
        PlayableCharacterRegistry registry = PlayableCharacterRegistry.empty();
        for (CharacterDefinition definition : definitions) {
            registry = registry.register(definition.key(), definition);
        }
        return registry;
    }

    private static void openSession(PlayableCharacterRegistry registry, SelectedTeam team) {
        GameModule module = mock(GameModule.class);
        when(module.getPlayableCharacterRegistry()).thenReturn(registry);
        when(module.createRuntimeArtCoordinator(
                org.mockito.ArgumentMatchers.any())).thenReturn(RuntimeArtCoordinator.NONE);
        SessionManager.openGameplaySession(module,
                SaveSessionContext.noSave("s3k", team, 0, 0));
    }
    private static GameModule neutralGameModule() {
        GameModule module = mock(GameModule.class);
        when(module.createRuntimeArtCoordinator(
                org.mockito.ArgumentMatchers.any())).thenReturn(RuntimeArtCoordinator.NONE);
        return module;
    }
}
