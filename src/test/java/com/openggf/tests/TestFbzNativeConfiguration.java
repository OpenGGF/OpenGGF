package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.MasterTitleScreen;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.launch.LaunchProfile;
import com.openggf.game.launch.LaunchProfileApplier;
import com.openggf.game.rules.GameRules;
import com.openggf.game.session.ActiveGameplayTeamResolver;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Native-off fixture that must stay invariant before parity comparisons. */
@RequiresRom(SonicGame.SONIC_3K)
class TestFbzNativeConfiguration {
    @ParameterizedTest(name = "native FBZ fixture: {0}")
    @MethodSource("nativeTeams")
    void nativeParityFixtureDisablesExtensionsAndUsesS3kOwnedRules(NativeTeam team) {
        SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        String previousMain = configuration.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        String previousSidekicks = configuration.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        try {
            CrossGameFeatureProvider.getInstance().resetState();
            configuration.clearSessionOverrides();
            new LaunchProfileApplier(configuration).apply(
                    new LaunchProfile(false, "off", false, "NATIVE_4_3",
                            team.main(), team.launchSidekick()),
                    MasterTitleScreen.GameEntry.SONIC_3K);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();

            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withZoneAndAct(Sonic3kZoneIds.ZONE_FBZ, 0)
                    .build();

            assertFalse(configuration.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED));
            assertFalse(CrossGameFeatureProvider.isActive());
            assertFalse(configuration.getBoolean(SonicConfiguration.LIVE_REWIND_ENABLED));
            assertFalse(configuration.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED));
            assertEquals(320, configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS));
            assertEquals(320, fixture.camera().getWidth() & 0xFFFF);

            assertInstanceOf(team.mainType(), fixture.sprite());
            assertEquals(team.main(), fixture.sprite().getCode());
            assertEquals(team.sidekickCodes(), GameServices.sprites().getSidekicks().stream()
                    .map(AbstractPlayableSprite::getCode).toList());
            assertEquals(team.sidekickNames(), ActiveGameplayTeamResolver.resolveSidekicks(configuration));
            assertEquals(team.sidekickNames().size(), GameServices.sprites().getSidekicks().size(),
                    "native fixtures must not inherit extra multi-sidekick participants");
            for (AbstractPlayableSprite sidekick : GameServices.sprites().getSidekicks()) {
                assertInstanceOf(Tails.class, sidekick);
                assertTrue(sidekick.isCpuControlled(),
                        "native P2 must be in the production CPU-sidekick mode");
                assertNotNull(sidekick.getCpuController());
                assertEquals(com.openggf.sprites.playable.SidekickCpuController.State.INIT,
                        sidekick.getCpuController().getState());
            }

            assertSame(GameRules.SONIC_3K, GameServices.module().getRules());
            assertSame(GameRules.SONIC_3K, fixture.sprite().getGameRules());
            assertEquals(GameServices.module().getPhysicsProvider().getProfile(team.main()),
                    fixture.sprite().getPhysicsProfile());
            assertEquals(team.playerCharacter(),
                    assertInstanceOf(FbzZoneRuntimeState.class,
                            GameServices.zoneRuntimeRegistry().current()).playerCharacter());
        } finally {
            CrossGameFeatureProvider.getInstance().resetState();
            configuration.clearSessionOverrides();
            configuration.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    previousMain == null ? "sonic" : previousMain);
            configuration.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    previousSidekicks == null ? "tails" : previousSidekicks);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }

    private static Stream<NativeTeam> nativeTeams() {
        return Stream.of(
                new NativeTeam("Sonic", "sonic", "none", Sonic.class,
                        List.of(), List.of(), PlayerCharacter.SONIC_ALONE),
                new NativeTeam("Tails", "tails", "none", Tails.class,
                        List.of(), List.of(), PlayerCharacter.TAILS_ALONE),
                new NativeTeam("Sonic + Tails", "sonic", "tails", Sonic.class,
                        List.of("tails_p2"), List.of("tails"), PlayerCharacter.SONIC_AND_TAILS),
                new NativeTeam("Knuckles", "knuckles", "none", Knuckles.class,
                        List.of(), List.of(), PlayerCharacter.KNUCKLES));
    }

    private record NativeTeam(
            String label,
            String main,
            String launchSidekick,
            Class<? extends AbstractPlayableSprite> mainType,
            List<String> sidekickCodes,
            List<String> sidekickNames,
            PlayerCharacter playerCharacter) {
        @Override public String toString() { return label; }
    }
}
