package com.openggf.tests;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.TestFbzAct2TraversalPreboss;
import com.openggf.game.sonic3k.objects.TestFbzAct2TraversalPreboss.RouteCompletionEvidence;
import com.openggf.game.sonic3k.runtime.FbzZoneRuntimeState;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cold Act 2 routes for native main characters. These deliberately use the
 * complete production-input controller rather than seeding starpost 6 or
 * treating first boss allocation as completion. Each route is independently
 * selectable in the exhaustive FBZ lane.
 */
@RequiresRom(SonicGame.SONIC_3K)
@Tag("fbz-route")
class TestFbzMainCharacterCompletion {
    @Test
    void tailsCompletesColdAct2ThroughCapsuleAndSandopolisRequest() {
        complete("tails", Tails.class, PlayerCharacter.TAILS_ALONE, 4);
    }

    @Test
    void knucklesCompletesColdAct2ThroughCapsuleAndSandopolisRequest() {
        complete("knuckles", Knuckles.class, PlayerCharacter.KNUCKLES, 0);
    }

    private static void complete(String character, Class<? extends AbstractPlayableSprite> type,
                                 PlayerCharacter nativeCharacter, int groundSnapYOffset) {
        try (ConfigurationScope ignored = new ConfigurationScope(character)) {
            AbstractPlayableSprite[] originalMain = new AbstractPlayableSprite[1];
            RouteCompletionEvidence evidence = TestFbzAct2TraversalPreboss
                    .runNativeStartFixedInputsReachSafeLateFrontierWithAllRouteMilestones(start -> {
                        originalMain[0] = assertInstanceOf(type, start.sprite());
                        assertEquals(character, start.sprite().getCode());
                        assertSame(start.sprite(), GameServices.sprites().getMainPlayable());
                        assertTrue(GameServices.sprites().getSidekicks().isEmpty());
                        assertFalse(CrossGameFeatureProvider.isActive());
                        assertSame(GameServices.module().getRules(), start.sprite().getGameRules());
                        assertEquals(Sonic3kZoneIds.ZONE_FBZ, GameServices.level().getCurrentZone());
                        assertEquals(1, GameServices.level().getCurrentAct());
                        int[] nativeStart = GameServices.module().getZoneRegistry()
                                .getStartPosition(Sonic3kZoneIds.ZONE_FBZ, 1);
                        assertEquals(nativeStart[0], start.sprite().getCentreX() & 0xFFFF);
                        // Same real terrain-snap radius contract as
                        // TestFbzNativeCharacterRoutes: native Tails is 4px shorter.
                        assertEquals(nativeStart[1] + groundSnapYOffset,
                                start.sprite().getCentreY() & 0xFFFF);
                        FbzZoneRuntimeState runtime = assertInstanceOf(FbzZoneRuntimeState.class,
                                GameServices.zoneRuntimeRegistry().current());
                        assertEquals(nativeCharacter, runtime.playerCharacter());
                        assertEquals(320, start.camera().getWidth() & 0xFFFF);
                        GameServices.graphics().setViewport(0, 0, 320, 224);
                    });
            assertMandatoryCompletion(evidence, character);
            assertSame(originalMain[0], GameServices.sprites().getMainPlayable(),
                    "main-character progression authority changed during the route");
            assertInstanceOf(type, GameServices.sprites().getMainPlayable());
            assertTrue(GameServices.sprites().getSidekicks().isEmpty());
            assertFalse(originalMain[0].getDead());
            assertEquals(Sonic3kZoneIds.ZONE_SOZ, GameServices.level().getRequestedZone());
            assertEquals(0, GameServices.level().getRequestedAct());
            assertFalse(evidence.s1DonationUpperLoopAssistConsumed());
            assertFalse(evidence.s1DonationLowerLoopAssistConsumed());
            FbzZoneRuntimeState runtime = assertInstanceOf(FbzZoneRuntimeState.class,
                    GameServices.zoneRuntimeRegistry().current());
            assertNotEquals(FbzZoneRuntimeState.S1DonationSqueezeAssistState.CONSUMED,
                    runtime.s1DonationSqueezeAssistState());
        }
    }

    private static void assertMandatoryCompletion(RouteCompletionEvidence evidence, String label) {
        assertEquals(evidence.frames(), evidence.sidekickAuditFrames(),
                label + " lost per-frame configured participant audit");
        assertTrue(evidence.sidekickIdentityOrderPreserved());
        assertTrue(evidence.sidekickControllerEveryFrame());
        assertTrue(evidence.sidekickLeaderChainEveryFrame());
        assertTrue(evidence.cageCapture(), label + " missed the opening cage");
        assertTrue(evidence.elevatorRide(), label + " missed real car support");
        assertTrue(evidence.launcherRide(), label + " missed floor launcher contact");
        assertTrue(evidence.chainControl(), label + " missed chain transport");
        assertTrue(evidence.spiderControl(), label + " missed lower spider-crane transport");
        assertTrue(evidence.nonPersistentSpawn() && evidence.nonPersistentDespawn(),
                label + " did not exercise placement lifecycle");
        assertTrue(evidence.maxPlacedSpawnScreenX() >= 0x200
                        && evidence.maxPlacedSpawnScreenX() < 0x300,
                label + " spawn escaped native load-ahead chunks");
        assertTrue(evidence.minPlacedDespawnScreenX() >= -0x180
                        && evidence.minPlacedDespawnScreenX() <= -0x80,
                label + " despawn escaped native unload chunks");
        assertTrue(evidence.hazardObserved());
        assertTrue(evidence.exactArenaLock(), label + " missed exact boss arena lock");
        assertTrue(evidence.bossCombat(), label + " never fought the boss");
        assertTrue(evidence.bossDefeat(), label + " never defeated the boss");
        assertTrue(evidence.bossArenaPlayerContained(), label + " escaped boss world bounds");
        assertTrue(evidence.capsuleObserved(), label + " never materialized the capsule");
        assertTrue(evidence.capsuleCameraRelease(), label + " missed capsule camera release");
        assertTrue(evidence.exitCameraRelease(), label + " missed exit camera release");
        assertTrue(evidence.forcedExit(), label + " never requested Sandopolis");
        assertFalse(evidence.unsafeFall(), label + " entered a lethal below-camera band");
    }

    private static final class ConfigurationScope implements AutoCloseable {
        private final SonicConfigurationService configuration = SonicConfigurationService.getInstance();
        private final Map<SonicConfiguration, Object> overlays = new EnumMap<>(SonicConfiguration.class);
        private final int viewportX = GameServices.graphics().getViewportX();
        private final int viewportY = GameServices.graphics().getViewportY();
        private final int viewportWidth = GameServices.graphics().getViewportWidth();
        private final int viewportHeight = GameServices.graphics().getViewportHeight();

        private ConfigurationScope(String character) {
            for (SonicConfiguration key : SonicConfiguration.values()) {
                if (configuration.hasSessionOverride(key)) overlays.put(key, configuration.getConfigValue(key));
            }
            configuration.clearSessionOverrides();
            configuration.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, character);
            configuration.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
            configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED, false);
            configuration.setSessionOverride(SonicConfiguration.CROSS_GAME_SOURCE, "off");
            configuration.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, WidescreenAspect.NATIVE_4_3.name());
            configuration.resolveDisplayAspect();
            configuration.setSessionOverride(SonicConfiguration.SCREEN_WIDTH_PIXELS, 320);
            CrossGameFeatureProvider.getInstance().resetState();
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }

        @Override public void close() {
            CrossGameFeatureProvider.getInstance().resetState();
            configuration.clearSessionOverrides();
            overlays.forEach(configuration::setSessionOverride);
            configuration.resolveDisplayAspect();
            GameServices.graphics().setViewport(viewportX, viewportY, viewportWidth, viewportHeight);
            SessionManager.clear();
            TestEnvironment.activeGameplayMode();
        }
    }
}
