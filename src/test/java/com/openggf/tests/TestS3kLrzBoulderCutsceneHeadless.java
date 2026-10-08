package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.sonic3k.objects.LrzBoulderCutsceneObjectInstance;
import com.openggf.game.sonic3k.objects.LrzCutsceneBoulderObjectInstance;
import com.openggf.game.sonic3k.objects.CutsceneKnucklesLrz2Instance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.session.SessionManager;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kLrzBoulderCutsceneHeadless {
    @AfterEach void reset() {
        CrossGameFeatureProvider.getInstance().resetState();
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
    }

    @ParameterizedTest(name="boulder {0}px {1} {2}+{3}")
    @CsvSource({"320,off,sonic,tails", "352,off,sonic,tails", "400,off,sonic,tails", "528,off,sonic,tails", "800,off,sonic,tails",
            "320,s1,sonic,none", "352,s1,sonic,none", "400,s1,sonic,none", "528,s1,sonic,none", "800,s1,sonic,none",
            "320,s2,sonic,tails", "352,s2,sonic,tails", "400,s2,sonic,tails", "528,s2,sonic,tails", "800,s2,sonic,tails",
            "320,off,tails,none", "320,off,sonic,none"})
    void positionedApproachCompletesBoulderRideAndRewindsCapturedPlayers(int width, String donor, String character, String sidekick) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, character);
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, sidekick.equals("none") ? "" : sidekick);
        var aspect = java.util.Arrays.stream(WidescreenAspect.values()).filter(a -> a.pixelWidth() == width).findFirst().orElseThrow();
        config.setSessionOverride(SonicConfiguration.DISPLAY_ASPECT, aspect.name()); config.resolveDisplayAspect();
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short)0x3880, (short)0x1C0).startPositionIsCentre()
                .withCrossGameDonation(donor.equals("off") ? null : donor).build();
        assertEquals(width, GameServices.camera().getWidth() & 0xFFFF);
        assertEquals(character, fixture.sprite().getCode());
        assertEquals(!donor.equals("off"), CrossGameFeatureProvider.isActive());
        fixture.sprite().setRingCount(37);
        boolean carriesFire = width == 320 && donor.equals("off") && character.equals("sonic");
        if (carriesFire) fixture.sprite().giveShield(com.openggf.game.ShieldType.FIRE);
        boolean sawBoulder = false, sawCapture = false, rewound = false;
        for (int frame = 0; frame < 1000 && !GameServices.level().isLevelInactiveForTransition(); frame++) {
            fixture.stepFrame(false, false, false, true, frame % 60 < 20);
            var objects = GameServices.level().getObjectManager().getActiveObjects();
            sawBoulder |= objects.stream().anyMatch(LrzCutsceneBoulderObjectInstance.class::isInstance);
            objects.stream().filter(LrzCutsceneBoulderObjectInstance.class::isInstance)
                    .map(LrzCutsceneBoulderObjectInstance.class::cast).findFirst().ifPresent(boulder -> {
                        int moves = (0x3A08 - boulder.getX()) / 2;
                        if (moves > 0 && moves <= 49) {
                            int falling = Math.max(0, moves - 21);
                            // Read-only native 428426..428475 and MoveSprite_LightGravity:
                            // 21 rolling moves, then old velocity before +$20 gravity.
                            assertEquals(0xE2 + (falling * (falling - 1) / 16), boulder.getY());
                        }
                    });
            if (fixture.sprite().isObjectMappingFrameControl()) {
                sawCapture = true;
                if (!rewound) {
                    var registry = fixture.gameplayMode().getRewindRegistry();
                    var before = registry.capture();
                    fixture.stepFrame(false, false, false, true, false);
                    var after = registry.capture();
                    objects.stream().filter(o -> o instanceof LrzBoulderCutsceneObjectInstance || o instanceof LrzCutsceneBoulderObjectInstance
                            || o instanceof CutsceneKnucklesLrz2Instance).forEach(o -> ((com.openggf.level.objects.AbstractObjectInstance) o).setDestroyed(true));
                    fixture.stepFrame(false, false, false, true, false);
                    registry.restore(before);
                    for (String key : before.entries().keySet()) {
                        assertTrue(RewindSnapshotDiff.diffKey(key, before.get(key), registry.capture().get(key)).isEmpty(), key);
                    }
                    fixture.stepFrame(false, false, false, true, false);
                    var replay = registry.capture();
                    var differences = new java.util.ArrayList<String>();
                    for (String key : after.entries().keySet()) {
                        differences.addAll(RewindSnapshotDiff.diffKey(key, after.get(key), replay.get(key)));
                    }
                    assertTrue(differences.isEmpty(), () -> differences.toString());
                    rewound = true;
                }
            }
        }
        assertTrue(sawBoulder, "approach must spawn boulder");
        assertTrue(sawCapture, "boulder must catch the player: " + fixture.sprite().getCentreX() + "," + fixture.sprite().getCentreY()
                + " flags=" + S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().cutsceneFlags());
        assertTrue(GameServices.level().isLevelInactiveForTransition(), "ride must request destination");
        assertEquals(22, GameServices.level().getRequestedZone());
        assertEquals(0, GameServices.level().getRequestedAct());
        assertFalse(fixture.sprite().getDead());
        long savedTime = GameServices.level().getLevelGamestate().getTimerFrames();
        assertTrue(GameServices.level().consumeZoneActRequest());
        GameServices.level().loadZoneAndActWithTitleCard(22, 0);
        assertFalse(GameServices.level().consumeTitleCardRequest());
        if (carriesFire) assertEquals(com.openggf.game.ShieldType.FIRE, fixture.sprite().getShieldType());
        ((com.openggf.game.sonic3k.Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider())
                .getLrzEventsForTest().update(0, 0);
        assertEquals(37, GameServices.level().getLevelGamestate().getRings());
        assertEquals(savedTime, GameServices.level().getLevelGamestate().getTimerFrames());
        assertFalse(GameServices.level().getLevelGamestate().isTimerPaused());
    }
    @Test void knucklesDoesNotStartTheSonicTailsBoulderSequence() {
        var config = SonicConfigurationService.getInstance();
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "knuckles");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        SessionManager.clear(); TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder().withZoneAndAct(9, 1)
                .startPosition((short)0x38A8, (short)0x1C0).startPositionIsCentre().build();
        for (int i = 0; i < 5; i++) fixture.stepFrame(false, false, false, false, false);
        assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream().noneMatch(
                o -> o instanceof LrzBoulderCutsceneObjectInstance || o instanceof LrzCutsceneBoulderObjectInstance
                        || o instanceof CutsceneKnucklesLrz2Instance));
        assertEquals(0, S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).orElseThrow().cutsceneFlags());
    }
}
