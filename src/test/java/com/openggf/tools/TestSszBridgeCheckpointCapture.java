package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.game.CharacterKey;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SszArrivalControllerObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.game.CrossGameFeatureProvider;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Cold arrival -> loc_44FBA's implicit checkpoint -> production death/reload, twice. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszBridgeCheckpointCapture {
    @AfterEach void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        SessionManager.clear();
    }

    static Stream<Arguments> configurations() {
        return Stream.of(WidescreenAspect.values()).flatMap(aspect -> Stream.of(
                Arguments.of(aspect.pixelWidth(), "sonic", "", "off"),
                Arguments.of(aspect.pixelWidth(), "tails", "", "off"),
                Arguments.of(aspect.pixelWidth(), "sonic", "tails", "off"),
                Arguments.of(aspect.pixelWidth(), "sonic", "", "s1"),
                Arguments.of(aspect.pixelWidth(), "sonic", "tails", "s2")));
    }

    @ParameterizedTest
    @MethodSource("configurations")
    void coldBridgeCheckpointSurvivesTwoRealReloads(int width, String main, String follower, String donor) throws Exception {
        var requested = new com.openggf.game.launch.LaunchProfile(false, donor, false, "global",
                main, follower.isEmpty() ? "none" : follower);
        assertEquals(requested, requested.sanitizedFor(com.openggf.game.MasterTitleScreen.GameEntry.SONIC_3K),
                "exercise supported menu configurations, not raw unsupported donor/character overrides");
        var donorRom = switch (donor) {
            case "s1" -> RomTestUtils.ensureSonic1RomAvailable().toPath();
            case "s2" -> RomTestUtils.ensureSonic2RomAvailable().toPath();
            default -> null;
        };
        var settings = new GameplayCaptureSession.Settings(width, main, follower, donor, donorRom, null, null);
        var drawing = new RouteFrameDrawing();
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 10, 0, settings);
            assertConfiguration(width, donor);
            GameServices.configuration().setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
            boolean bridgeReleased = false;
            for (int frame = 0; frame < 1800; frame++) {
                step(session, drawing, frame > 200);
                assertFalse(GameServices.sprites().getMainPlayable().getDead(), "arrival must reach the bridge alive");
                var state = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
                var checkpoint = GameServices.level().getCheckpointState();
                if (state.eventsBgByte(5) == 0 && checkpoint.getLastCheckpointIndex() == 1) {
                    bridgeReleased = true;
                    break;
                }
            }
            drawing.checkpoint(session);
            assertTrue(bridgeReleased, "ordinary input must finish the bridge and publish its checkpoint");
            assertCheckpoint();
            for (int cycle = 0; cycle < 2; cycle++) {
                for (int frame = 0; frame < 30; frame++) step(session, drawing, false);
                drawing.checkpoint(session);
                var objects = GameServices.level().getObjectManager();
                var oldState = S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow();
                var rewind = SessionManager.getCurrentGameplayMode().getRewindController();
                assertNotNull(rewind);
                long history = rewind.currentFrame();
                assertTrue(history > 10, "actual outgoing GameLoop history exists");
                // Declared lethal action; checkpoint and saved positions are never injected.
                assertTrue(GameServices.sprites().getMainPlayable().applyPitDeath());
                boolean reloaded = false;
                for (int frame = 0; frame < 1000; frame++) {
                    step(session, drawing, false);
                    if (objects != GameServices.level().getObjectManager()) {
                        var incomingRewind = SessionManager.getCurrentGameplayMode().getRewindController();
                        assertTrue(incomingRewind == null || incomingRewind.currentFrame() < history,
                                "reload isolates the old live timeline");
                        reloaded = true;
                        break;
                    }
                }
                drawing.checkpoint(session);
                assertTrue(reloaded, "production death lifecycle must reload SSZ");
                assertCheckpoint();
                assertConfiguration(width, donor);
                assertEquals(0x140, GameServices.sprites().getMainPlayable().getCentreX());
                assertEquals(0xC6C, GameServices.sprites().getMainPlayable().getCentreY());
                assertNotSame(oldState, S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElseThrow());
                for (int frame = 0; frame < 180; frame++) {
                    step(session, drawing, false);
                    assertTrue(GameServices.level().getObjectManager().getActiveObjects().stream()
                            .noneMatch(object -> object instanceof SszArrivalControllerObjectInstance && !object.isDestroyed()),
                            "Last_star_post_hit must suppress the arrival throughout the restart");
                }
                drawing.checkpoint(session);
                assertFalse(GameServices.sprites().getMainPlayable().getDead());
                assertFalse(GameServices.sprites().getMainPlayable().isObjectControlled());
                assertEquals(main.equals("tails") ? CharacterKey.TAILS : CharacterKey.SONIC, GameServices.sprites().getMainPlayable().characterKey());
                var followers = GameServices.sprites().getSidekicks();
                assertEquals(follower.isEmpty() ? 0 : 1, followers.size());
                if (!follower.isEmpty()) assertEquals(CharacterKey.TAILS, followers.getFirst().characterKey());
                var participants = new java.util.ArrayList<AbstractPlayableSprite>();
                participants.add(GameServices.sprites().getMainPlayable());
                participants.addAll(followers);
                for (var participant : participants) {
                    assertNotNull(participant.getSpriteRenderer(), "ROM-backed participant renderer after reload");
                    assertNotNull(participant.getAnimationSet());
                    assertTrue(participant.getAnimationFrameCount() > 0, "decoded participant mappings");
                }
                int startX = GameServices.sprites().getMainPlayable().getCentreX();
                boolean sawExtendedBridge = false;
                for (int frame = 0; frame < 180; frame++) {
                    step(session, drawing, true);
                    for (var bridge : GameServices.level().getObjectManager().activeObjectsOfType(
                            com.openggf.game.sonic3k.objects.SszCutsceneBridgeObjectInstance.class)) {
                        assertTrue(bridge.extendedForTest(), "restart bridge must enter loc_4501A");
                        assertEquals(0, bridge.offsetForTest(), "restart bridge stays at its ROM placement");
                        sawExtendedBridge = true;
                    }
                }
                drawing.checkpoint(session);
                assertFalse(GameServices.sprites().getMainPlayable().getDead(), "bridge approach stays playable");
                assertTrue(sawExtendedBridge, "ordinary movement reaches the restarted bridge");
                assertTrue(GameServices.sprites().getMainPlayable().getCentreX() > startX + 8, "restart is playable");
            }
        }
    }

    private static void assertConfiguration(int width, String donor) {
        assertEquals(width, GameServices.camera().getWidth());
        assertEquals(!donor.equals("off"), CrossGameFeatureProvider.isActive());
        if (!donor.equals("off")) assertEquals(donor, CrossGameFeatureProvider.getInstance().getDonorGameId());
        assertEquals(!donor.equals("s1"), GameServices.sprites().getMainPlayable()
                .getGameRules().playerCapability().spindashEnabled());
    }

    private static void assertCheckpoint() {
        var checkpoint = GameServices.level().getCheckpointState();
        // loc_44FBA: Saved_X=$140, Saved_Y=$C6C, Last_star_post_hit=1, Save_Level_Data.
        assertEquals(1, checkpoint.getLastCheckpointIndex());
        assertEquals(0x140, checkpoint.getSavedX());
        assertEquals(0xC6C, checkpoint.getSavedY());
        assertEquals(10, GameServices.level().getCurrentZone());
        assertEquals(0, GameServices.level().getCurrentAct());
    }

    private static void step(GameplayCaptureSession session, RouteFrameDrawing drawing, boolean right) {
        session.step(new Bk2FrameInput(0, right ? AbstractPlayableSprite.INPUT_RIGHT : 0, 0, false, ""));
        drawing.afterStep(session);
    }
}
