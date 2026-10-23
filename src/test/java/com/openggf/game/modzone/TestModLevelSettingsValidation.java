package com.openggf.game.modzone;

import com.openggf.game.CharacterKey;
import com.openggf.game.GameplayLaunchTeam;
import com.openggf.game.PlayableCharacterRegistry;
import com.openggf.game.patch.DelegatingGameModule;
import com.openggf.game.session.GameplayTeamBootstrap;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestModLevelSettingsValidation {
    @Test void sonic1RejectsRequiredSidekicksButAllowsEachExactSingleCharacter() {
        var host = new Sonic1GameModule();
        assertFalse(host.supportsSidekick());
        var paired = ModLevelSettings.DEFAULT.withTeam(new GameplayLaunchTeam(CharacterKey.SONIC, List.of(CharacterKey.TAILS)));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> ModLevelSettingsValidation.validate(host, paired)).getMessage().contains("required sidekicks"));
        for (var main : List.of(CharacterKey.SONIC, CharacterKey.TAILS, CharacterKey.KNUCKLES)) {
            assertDoesNotThrow(() -> ModLevelSettingsValidation.validate(host,
                    ModLevelSettings.DEFAULT.withTeam(new GameplayLaunchTeam(main, List.of()))));
        }
        assertDoesNotThrow(() -> ModLevelSettingsValidation.validate(host, ModLevelSettings.DEFAULT));
    }

    @Test void nativeSidekickHostsRetainExactMultiMemberTeamsAndProviderValidation() {
        var paired = ModLevelSettings.DEFAULT.withTeam(new GameplayLaunchTeam(CharacterKey.KNUCKLES,
                List.of(CharacterKey.TAILS, CharacterKey.SONIC)));
        for (var host : List.of(new Sonic2GameModule(), new Sonic3kGameModule())) {
            assertTrue(host.supportsSidekick());
            assertDoesNotThrow(() -> ModLevelSettingsValidation.validate(host, paired));
            assertThrows(IllegalArgumentException.class, () -> ModLevelSettingsValidation.validate(host,
                    paired.withBackground(new ModLevelSettings.StockScrollSource(255, 255))));
        }
    }

    @Test void registrationCapabilityCheckDefersKeysUntilTheContributedRegistryIsResolved() {
        var host = new DelegatingGameModule(new Sonic2GameModule(), "empty-registry") {
            @Override public PlayableCharacterRegistry getPlayableCharacterRegistry() { return PlayableCharacterRegistry.empty(); }
        };
        var settings = ModLevelSettings.DEFAULT.withTeam(new GameplayLaunchTeam(CharacterKey.mod("owner", "runner"),
                List.of(CharacterKey.TAILS)));
        assertDoesNotThrow(() -> ModLevelSettingsValidation.validateCapabilities(host, settings));
        assertThrows(GameplayTeamBootstrap.UnavailableRequiredCharacter.class,
                () -> ModLevelSettingsValidation.validate(host, settings));
        var unsupported = new DelegatingGameModule(host, "unsupported-sidekick") {
            @Override public boolean supportsSidekick() { return false; }
        };
        assertThrows(IllegalArgumentException.class,
                () -> ModLevelSettingsValidation.validateCapabilities(unsupported, settings));
    }
}
