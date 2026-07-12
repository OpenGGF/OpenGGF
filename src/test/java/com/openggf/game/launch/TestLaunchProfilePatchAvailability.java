package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
import com.openggf.game.CharacterDefinition;
import com.openggf.game.CharacterKey;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.patch.GamePatch;
import com.openggf.game.patch.GameplayLaunchRequest;
import com.openggf.game.patch.LogicalRom;
import com.openggf.game.patch.LogicalRomResolver;
import com.openggf.game.patch.ModuleResolutionService;
import com.openggf.game.patch.PatchContext;
import com.openggf.game.patch.PatchEnablement;
import com.openggf.game.patch.PatchOwner;
import com.openggf.game.patch.RegisteredPatch;
import com.openggf.game.patch.ResolutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.openggf.game.MasterTitleScreen.GameEntry.SONIC_2;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestLaunchProfilePatchAvailability {

    @TempDir
    Path tempDir;

    @Test
    void enabledPatchWithMetPrerequisitesExtendsStockAvailabilityUnion() {
        SonicConfigurationService config = configWithS2Main("knuckles");
        LaunchProfileStore store = patchAwareStore(config, PatchEnablement.ALL_ENABLED, true);

        LaunchProfile profile = store.load(SONIC_2);

        assertEquals("knuckles", profile.mainCharacter());
        assertEquals("none", profile.sidekick());
        assertTrue(store.isCharacterPairStandard(profile, SONIC_2));
    }

    @Test
    void patchCharacterIsStrippedWhenOwnerIsDisabled() {
        PatchEnablement disabled = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return false; }
            @Override public int orderOf(PatchOwner owner) { return 0; }
        };
        LaunchProfile profile = patchAwareStore(configWithS2Main("knuckles"), disabled, true)
                .load(SONIC_2);

        assertEquals("sonic", profile.mainCharacter());
    }

    @Test
    void patchCharacterIsStrippedWhenRomPrerequisiteIsMissing() {
        LaunchProfile profile = patchAwareStore(
                configWithS2Main("knuckles"), PatchEnablement.ALL_ENABLED, false)
                .load(SONIC_2);

        assertEquals("sonic", profile.mainCharacter());
    }

    @Test
    void mainCharacterCyclingUsesTheSameAvailabilityUnion() {
        LaunchProfileStore store = patchAwareStore(
                configWithS2Main("sonic"), PatchEnablement.ALL_ENABLED, true);

        LaunchProfile cycled = store.withPrevious(
                store.load(SONIC_2), LaunchProfile.Row.MAIN_CHARACTER, SONIC_2);

        assertEquals("knuckles", cycled.mainCharacter());
    }

    @Test
    void namespacedCharactersWithSameLocalNameUseTheirRegistryDisplayNames() {
        SonicConfigurationService config = configWithS2Main("owner-a:modchar");
        ModuleResolutionService service = new ModuleResolutionService(
                List.of(), PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> null), config);
        CharacterKey ownerA = CharacterKey.mod("owner-a", "modchar");
        CharacterKey ownerB = CharacterKey.mod("owner-b", "modchar");
        ResolutionContext context = service.newContext(List.of(
                new RegisteredPatch(new PatchOwner.Mod("owner-a"), "owner-a:character",
                        characterPatch("s2", ownerA, "Alpha Runner"), 0),
                new RegisteredPatch(new PatchOwner.Mod("owner-b"), "owner-b:character",
                        characterPatch("s2", ownerB, "Beta Runner"), 1)), Map.of());
        LaunchProfileStore store = new LaunchProfileStore(config, service, context);

        LaunchProfile alpha = store.load(SONIC_2);
        assertEquals("Alpha Runner", store.displayValue(
                alpha, LaunchProfile.Row.MAIN_CHARACTER, SONIC_2));
        LaunchProfile beta = store.withPrevious(
                new LaunchProfile(false, "off", false, "global", "sonic", "none"),
                LaunchProfile.Row.MAIN_CHARACTER, SONIC_2);
        assertEquals("owner-b:modchar", beta.mainCharacter());
        assertEquals("Beta Runner", store.displayValue(
                beta, LaunchProfile.Row.MAIN_CHARACTER, SONIC_2));
    }

    @Test
    void preparedLaunchSnapshotsLabelsAndDeterministicPolicySkipsModPlan() {
        SonicConfigurationService config = configWithS2Main("owner-a:modchar");
        CharacterKey key = CharacterKey.mod("owner-a", "modchar");
        java.util.concurrent.atomic.AtomicInteger scans = new java.util.concurrent.atomic.AtomicInteger();
        ModuleResolutionService service = new ModuleResolutionService(List.of(),
                PatchEnablement.ALL_ENABLED, new LogicalRomResolver(() -> null), config,
                ignored -> {
                    scans.incrementAndGet();
                    return new ModuleResolutionService.PatchPlan(List.of(
                            new RegisteredPatch(new PatchOwner.Mod("owner-a"), "owner-a:character",
                                    characterPatch("s2", key, "Prepared Runner"), 0)), Map.of());
                });

        LaunchProfileStore standard = new LaunchProfileStore(config, service,
                service.prepareLaunch(ModuleResolutionService.LaunchPolicy.STANDARD));
        assertEquals("Prepared Runner", standard.displayValue(
                standard.load(SONIC_2), LaunchProfile.Row.MAIN_CHARACTER, SONIC_2));
        assertEquals(1, scans.get(), "prepared availability must use one frozen plan");

        LaunchProfileStore deterministic = new LaunchProfileStore(config, service,
                service.prepareLaunch(ModuleResolutionService.LaunchPolicy.DETERMINISTIC));
        assertEquals("sonic", deterministic.load(SONIC_2).mainCharacter());
        assertEquals(1, scans.get(), "deterministic launch must not scan mod storage");
    }

    @Test
    void crossOwnerCharacterMetadataFailsTheSpoofingOwner() {
        SonicConfigurationService config = configWithS2Main("owner-b:modchar");
        CharacterKey ownerB = CharacterKey.mod("owner-b", "modchar");
        PatchOwner ownerA = new PatchOwner.Mod("owner-a");
        ModuleResolutionService service = new ModuleResolutionService(
                List.of(), PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> null), config);
        ResolutionContext context = service.newContext(List.of(
                new RegisteredPatch(ownerA, "owner-a:spoof",
                        characterPatch("s2", ownerB, "Spoofed"), 0)), Map.of());

        LaunchProfileStore store = new LaunchProfileStore(config, service, context);

        assertEquals("sonic", store.load(SONIC_2).mainCharacter());
        assertEquals(Set.of(ownerA), context.failedOwners());
    }

    @Test
    void crossOwnerCodeOnlySpoofFailsOwnerAndDependentButKeepsGenuineContribution() {
        SonicConfigurationService config = configWithS2Main("owner-b:modchar");
        PatchOwner ownerA = new PatchOwner.Mod("owner-a");
        PatchOwner dependent = new PatchOwner.Mod("dependent");
        PatchOwner ownerB = new PatchOwner.Mod("owner-b");
        CharacterKey ownerBKey = CharacterKey.mod("owner-b", "modchar");
        ModuleResolutionService service = new ModuleResolutionService(
                List.of(), PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> null), config);
        ResolutionContext context = service.newContext(List.of(
                new RegisteredPatch(ownerA, "owner-a:spoof",
                        characterPatch("s2", "owner-b:modchar"), 0),
                new RegisteredPatch(dependent, "dependent:character",
                        characterPatch("s2", "amy"), 1),
                new RegisteredPatch(ownerB, "owner-b:character",
                        characterPatch("s2", ownerBKey, "Beta Runner"), 2)),
                Map.of(dependent, Set.of(ownerA)));

        LaunchProfileStore store = new LaunchProfileStore(config, service, context);

        LaunchProfile loaded = store.load(SONIC_2);
        assertEquals("owner-b:modchar", loaded.mainCharacter());
        assertEquals("Beta Runner", store.displayValue(
                loaded, LaunchProfile.Row.MAIN_CHARACTER, SONIC_2));
        assertEquals(Set.of(ownerA, dependent), context.failedOwners());
        assertFalse(context.failedOwners().contains(ownerB));
    }

    @Test
    void availabilityIsFrozenWhenTheStoreIsConstructed() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        PatchEnablement mutablePolicy = new PatchEnablement() {
            @Override public boolean isEnabled(PatchOwner owner) { return enabled.get(); }
            @Override public int orderOf(PatchOwner owner) { return 0; }
        };
        LaunchProfileStore store = patchAwareStore(configWithS2Main("knuckles"), mutablePolicy, true);

        enabled.set(false);

        assertEquals("knuckles", store.load(SONIC_2).mainCharacter());
    }

    @Test
    void baseOnlyStoreDoesNotObserveAnotherStoresPatchAvailability() {
        LaunchProfileStore patchAware = patchAwareStore(
                configWithS2Main("knuckles"), PatchEnablement.ALL_ENABLED, true);
        LaunchProfileStore baseOnly = new LaunchProfileStore(configWithS2Main("knuckles"));

        assertEquals("knuckles", patchAware.load(SONIC_2).mainCharacter());
        assertEquals("sonic", baseOnly.load(SONIC_2).mainCharacter());
        assertFalse(baseOnly.isCharacterPairStandard(
                new LaunchProfile(false, "off", false, "global", "knuckles", "none"), SONIC_2));
    }

    @Test
    void masterTitleApplicationPreservesPatchBackedSelection() {
        SonicConfigurationService config = configWithS2Main("knuckles");
        LaunchProfileStore store = patchAwareStore(config, PatchEnablement.ALL_ENABLED, true);
        MasterTitleLaunchCoordinator coordinator = new MasterTitleLaunchCoordinator(
                config, store, new LaunchProfileApplier(config));

        coordinator.prepareExit("s2", false);

        assertEquals("knuckles", config.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        assertEquals("", config.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
    }

    @Test
    void lateMetadataFailureRemovesEarlierContributionFromFrozenUnion() {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.LAUNCH_S1_MAIN_CHARACTER, "amy");
        config.setConfigValue(SonicConfiguration.LAUNCH_S1_SIDEKICK, "none");
        PatchOwner owner = new PatchOwner.Mod("multi-game");
        ModuleResolutionService service = new ModuleResolutionService(
                List.of(), PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> null), config);
        ResolutionContext context = service.newContext(List.of(
                new RegisteredPatch(owner, "multi-game:s1",
                        characterPatch("s1", "amy"), 0),
                new RegisteredPatch(owner, "multi-game:s2-broken",
                        throwingCharacterPatch(), 1)), Map.of());

        LaunchProfileStore store = new LaunchProfileStore(config, service, context);

        assertEquals("sonic", store.load(
                com.openggf.game.MasterTitleScreen.GameEntry.SONIC_1).mainCharacter());
        assertEquals(Set.of(owner), context.failedOwners());
    }

    @Test
    void mixedCasePatchCharacterCodeFailsItsOwner() {
        assertMalformedCharacterMetadataFailsOwner("Amy");
    }

    @Test
    void blankPatchCharacterCodeFailsItsOwner() {
        assertMalformedCharacterMetadataFailsOwner(" ");
    }

    @Test
    void whitespacePaddedPatchCharacterCodeFailsItsOwner() {
        assertMalformedCharacterMetadataFailsOwner("amy ");
    }

    private SonicConfigurationService configWithS2Main(String character) {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.LAUNCH_S2_CROSS_GAME_SOURCE, "off");
        config.setConfigValue(SonicConfiguration.LAUNCH_S2_MAIN_CHARACTER, character);
        config.setConfigValue(SonicConfiguration.LAUNCH_S2_SIDEKICK, "none");
        return config;
    }

    private void assertMalformedCharacterMetadataFailsOwner(String malformedCode) {
        SonicConfigurationService config = configWithS2Main("amy");
        PatchOwner owner = new PatchOwner.Mod("characters");
        ModuleResolutionService service = new ModuleResolutionService(
                List.of(), PatchEnablement.ALL_ENABLED,
                new LogicalRomResolver(() -> null), config);
        ResolutionContext context = service.newContext(List.of(
                new RegisteredPatch(owner, "characters:main",
                        characterPatch("s2", malformedCode), 0)), Map.of());

        LaunchProfile loaded = new LaunchProfileStore(config, service, context).load(SONIC_2);

        assertEquals("sonic", loaded.mainCharacter());
        assertEquals(Set.of(owner), context.failedOwners());
        assertTrue(context.failures().get(owner) instanceof IllegalArgumentException);
    }

    private static LaunchProfileStore patchAwareStore(SonicConfigurationService config,
            PatchEnablement enablement, boolean prerequisiteAvailable) {
        LogicalRomResolver logicalRoms = new LogicalRomResolver(() -> prerequisiteAvailable
                ? new byte[0x200000]
                : null);
        ModuleResolutionService service = new ModuleResolutionService(
                List.of(), enablement, logicalRoms, config);
        ResolutionContext context = service.newContext(List.of(
                new RegisteredPatch(new PatchOwner.Mod("kis2"), "kis2:main",
                        knucklesPatch(), 0)), Map.of());
        return new LaunchProfileStore(config, service, context);
    }

    private static GamePatch knucklesPatch() {
        return new GamePatch() {
            @Override public String id() { return "main"; }
            @Override public String displayName() { return "Knuckles in Sonic 2"; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) {
                return "knuckles".equals(request.mainCharacter());
            }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(LogicalRom.SK); }
            @Override public List<String> providedMainCharacters() { return List.of("knuckles"); }
            @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
        };
    }

    private static GamePatch characterPatch(String gameId, String character) {
        return characterPatch(gameId, new String[] { character });
    }

    private static GamePatch characterPatch(String gameId, String... characters) {
        List<String> provided = List.of(characters);
        return new GamePatch() {
            @Override public String id() { return gameId; }
            @Override public String displayName() { return provided.toString(); }
            @Override public String baseGameId() { return gameId; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return provided; }
            @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
        };
    }

    private static GamePatch characterPatch(String gameId, CharacterKey key, String displayName) {
        CharacterDefinition definition = new CharacterDefinition(key, displayName,
                (code, x, y) -> null, null, PlayerCharacter.SONIC_ALONE,
                com.openggf.sprites.playable.SecondaryAbility.NONE, false,
                code -> com.openggf.sprites.art.SpriteArtSet.EMPTY);
        return new GamePatch() {
            @Override public String id() { return key.persisted(); }
            @Override public String displayName() { return displayName; }
            @Override public String baseGameId() { return gameId; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() { return List.of(key.persisted()); }
            @Override public Map<CharacterKey, CharacterDefinition> providedCharacterDefinitions() {
                return Map.of(key, definition);
            }
            @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
        };
    }

    private static GamePatch throwingCharacterPatch() {
        return new GamePatch() {
            @Override public String id() { return "broken"; }
            @Override public String displayName() { return "broken"; }
            @Override public String baseGameId() { return "s2"; }
            @Override public boolean activatesFor(GameplayLaunchRequest request) { return true; }
            @Override public Set<LogicalRom> romPrerequisites() { return Set.of(); }
            @Override public List<String> providedMainCharacters() {
                throw new IllegalStateException("broken metadata");
            }
            @Override public GameModule apply(GameModule base, PatchContext context) { return base; }
        };
    }
}
