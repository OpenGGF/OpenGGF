package com.openggf.tests;

import com.openggf.GameLoop;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.configuration.WidescreenAspect;
import com.openggf.control.InputHandler;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.DdzFlightControllerObjectInstance;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Doomsday ($C00) ring-out death and restart through the production loop. With no rings the powered form
 * ends ({@code Sonic_Super} drain), {@code sub_82742} sends the controller to {@code loc_8179E}, which
 * drops Player 1 until it passes {@code Camera_Y + $F0} and calls {@code Kill_Character}; the death
 * countdown then reloads the zone, whose {@code DDZ_ScreenInit} allocates a fresh controller.
 */
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kDdzLifecycleProduction {
    @AfterEach
    void reset() {
        SonicConfigurationService.getInstance().clearSessionOverrides();
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
    }

    @ParameterizedTest
    @EnumSource(WidescreenAspect.class)
    void ringOutEndsTheFormFallsAndReloadsDoomsday(WidescreenAspect aspect) {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        SozAcceptanceConfigurations.selectDisplay(aspect);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle()
                .build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var input = new InputHandler();
        var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
        input.setLogicalOverride(com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);loop.setGameplayMode(fixture.gameplayMode());loop.setGameMode(GameMode.LEVEL);
        assertTrue(loop.ownsGameplayFadeLifecycle());
        try {
            awaitReleasedFlight(fixture,loop);
            for(int cycle=0;cycle<2;cycle++) {
                for(int frame=0;frame<30;frame++)step(loop);
                var manager=GameServices.level().getObjectManager();
                var runtime=GameServices.zoneRuntimeRegistry().current();
                var player=GameServices.sprites().getMainPlayable();
                int lives=GameServices.gameState().getLives();
                var outgoing=fixture.gameplayMode().getRewindController();
                assertTrue(outgoing!=null&&outgoing.currentFrame()>10,"actual outgoing flight history");
                player.setRingCount(0); // Declared ring-out stimulus; native controller owns fall and death.
                int fallFrame=-1;
                for(int frame=0;frame<240&&!player.getDead();frame++) {
                    step(loop);
                    if(fallFrame<0&&!player.isSuperSonic())fallFrame=frame;
                }
                assertTrue(fallFrame>=0,"Sonic_Super ends the form with no rings");
                assertTrue(player.getDead(),"loc_8179E kills below Camera_Y + $F0");
                assertTrue((player.getCentreY()&0xFFFF)>((GameServices.camera().getY()&0xFFFF)+0xF0));
                boolean reloaded=false;int largestOutgoingFrame=outgoing.currentFrame();
                for(int frame=0;frame<1200;frame++) {
                    step(loop);var rewind=fixture.gameplayMode().getRewindController();
                    if(GameServices.level().getObjectManager()!=manager) {
                        reloaded=true;
                        assertEquals(lives-1,GameServices.gameState().getLives(),"exact life loss at assembly");
                        assertNotSame(runtime,GameServices.zoneRuntimeRegistry().current());
                        assertTrue(largestOutgoingFrame>10);
                        assertTrue(rewind==null||rewind.currentFrame()<largestOutgoingFrame,"isolated incoming timeline");
                        assertDdzOwners(aspect);break;
                    }
                    if(rewind!=null)largestOutgoingFrame=Math.max(largestOutgoingFrame,rewind.currentFrame());
                }
                assertTrue(reloaded,"actual DDZ ring-out reload cycle "+cycle);
                awaitReleasedFlight(fixture,loop);
                assertEquals(lives-1,GameServices.gameState().getLives(),"release hides no restart");
                int checkpoint=GameServices.level().getCheckpointState().getLastCheckpointIndex();
                CheckpointIncomingReplay.verify(fixture,loop,input,checkpoint,()->assertDdzOwners(aspect));
            }
        }finally{loop.closePresence();}
    }
    private static void step(GameLoop loop) {
        loop.step();GameServices.graphics().runPendingRenderThreadTasks();
    }
    private static void awaitReleasedFlight(HeadlessTestFixture fixture,GameLoop loop) {
        boolean released=false;
        for(int frame=0;frame<500;frame++) {
            step(loop);var title=GameServices.module().getTitleCardProvider();
            var player=GameServices.sprites().getMainPlayable();
            if(loop.getCurrentGameMode()==GameMode.LEVEL&&(title==null||title.isComplete())
                    &&!fixture.gameplayMode().getFadeManager().isActive()
                    &&!fixture.gameplayMode().getFadeManager().hasPendingCompletion()
                    &&!GameServices.level().hasPendingFreshLevelTransitionBoundary()
                    &&player.isSuperSonic()&&player.getXSpeed()==0x1000) {released=true;break;}
        }
        assertTrue(released,"actual DDZ title/fade and loc_8167C flight release");
        assertFalse(GameServices.sprites().getMainPlayable().getDead());
    }
    private static void assertDdzOwners(WidescreenAspect aspect) {
        assertEquals(Sonic3kZoneIds.ZONE_DDZ,GameServices.level().getCurrentZone());
        assertEquals(0,GameServices.level().getCurrentAct());
        var player=GameServices.sprites().getMainPlayable();
        assertSame(player,GameServices.camera().getFocusedSprite());assertFalse(player.getDead());
        assertEquals(com.openggf.game.CharacterKey.SONIC,player.characterKey());
        assertTrue(GameServices.sprites().getRegisteredSidekicks().isEmpty());
        SozAcceptanceConfigurations.assertDisplay(aspect);
        assertTrue(S3kRuntimeStates.currentDdz(GameServices.zoneRuntimeRegistry()).isPresent());
        assertEquals(1,GameServices.level().getObjectManager().getActiveObjects().stream()
                .filter(o->o instanceof DdzFlightControllerObjectInstance).count());
    }
    @ParameterizedTest
    @EnumSource(WidescreenAspect.class)
    void endingRequestSavesProgressAndStartsAnIsolatedTimeline(WidescreenAspect aspect) throws Exception {
        var config = SonicConfigurationService.getInstance();
        config.clearSessionOverrides();
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");
        config.setSessionOverride(SonicConfiguration.LIVE_REWIND_ENABLED, true);
        config.setSessionOverride(SonicConfiguration.DISCORD_RICH_PRESENCE_ENABLED, false);
        SozAcceptanceConfigurations.selectDisplay(aspect);
        CrossGameFeatureProvider.getInstance().resetState();
        SessionManager.clear();
        TestEnvironment.activeGameplayMode();
        var fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_DDZ, 0)
                .withFreshLevelStartLifecycle().build();
        GameServices.gameState().restoreS3kEmeraldProgress(List.of(3, 3, 3, 3, 3, 3, 3), true);
        var input = new InputHandler();
        var neutral = new com.openggf.debug.playback.Bk2FrameInput(0, 0, 0, false, "");
        input.setLogicalOverride(
                com.openggf.debug.playback.RecordedInputSnapshots.fromBk2(neutral, neutral));
        var loop = new GameLoop(input);
        loop.setGameplayMode(fixture.gameplayMode());
        loop.setGameMode(GameMode.LEVEL);
        assertTrue(loop.ownsGameplayFadeLifecycle());
        try {
            for (int frame = 0; frame < 60; frame++) {
                step(loop);
            }
            var rewind = fixture.gameplayMode().getRewindController();
            assertTrue(rewind != null && rewind.currentFrame() > 10,
                    "outgoing DDZ flight has live history");
            int outgoingFrame = rewind.currentFrame();
            var objects = GameServices.level().getObjectManager();
            // Isolate loc_81CA4 after the white fade. Full controller routes own the
            // preceding fight; this declared setup tests the real save/load boundary.
            var boss = new com.openggf.game.sonic3k.objects.DdzEndBossObjectInstance(
                    new com.openggf.level.objects.ObjectSpawn(0x500, 0x80, 0, 0, 0, false, 0));
            objects.addDynamicObject(boss);
            var services = org.mockito.Mockito.spy(TestEnvironment.objectServices());
            boss.setServices(services);
            var exit = boss.getClass().getDeclaredField("exitMode");
            exit.setAccessible(true);
            exit.setBoolean(boss, true);
            var routine = boss.getClass().getDeclaredField("routine");
            routine.setAccessible(true);
            routine.setInt(boss, 6);
            boolean loaded = false;
            for (int frame = 0; frame < 240 && !loaded; frame++) {
                step(loop);
                loaded = GameServices.level().getObjectManager() != objects;
            }
            org.mockito.Mockito.verify(services).requestSessionSave(
                    com.openggf.game.save.SaveReason.PROGRESSION_SAVE);
            assertTrue(loaded, "loc_81CA4's ending request is consumed by GameLoop");
            assertEquals(0x0D, GameServices.level().getCurrentZone());
            assertEquals(1, GameServices.level().getCurrentAct());
            SozAcceptanceConfigurations.assertDisplay(aspect);
            assertSame(GameServices.sprites().getMainPlayable(), GameServices.camera().getFocusedSprite());
            var endingRewind = fixture.gameplayMode().getRewindController();
            assertTrue(endingRewind == null || endingRewind.currentFrame() < outgoingFrame,
                    "the ending load cannot continue the outgoing DDZ timeline");
        } finally {
            loop.closePresence();
        }
    }

}
