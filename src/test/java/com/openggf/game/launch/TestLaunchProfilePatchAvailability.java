package com.openggf.game.launch;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModule;
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

    private SonicConfigurationService configWithS2Main(String character) {
        SonicConfigurationService config = SonicConfigurationService.createStandalone(tempDir);
        config.setConfigValue(SonicConfiguration.LAUNCH_S2_CROSS_GAME_SOURCE, "off");
        config.setConfigValue(SonicConfiguration.LAUNCH_S2_MAIN_CHARACTER, character);
        config.setConfigValue(SonicConfiguration.LAUNCH_S2_SIDEKICK, "none");
        return config;
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
}
