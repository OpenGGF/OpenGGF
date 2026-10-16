package com.openggf.tools;

import com.openggf.game.GameServices;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A capture that omits title drawing must still finish the real destination assembly. */
@RequiresRom(SonicGame.SONIC_3K)
class TestGameplayCaptureFreshLoad {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("lockedScreenInitScenes")
    void bootRetainsTheNativeScreenInitCameraAcrossSupportedViewports(int width,int zone,int act,int x,int y) throws Exception {
        var settings=new GameplayCaptureSession.Settings(width,"sonic","","off",null,null,null);
        try(var session=new GameplayCaptureSession(settings)){
            // Explicit unskipped declared entry; all camera values remain game-owned.
            GameServices.configuration().setSessionOverride(
                    com.openggf.configuration.SonicConfiguration.S3K_SKIP_INTROS,false);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),zone,act,settings);
            var camera=GameServices.camera();
            assertEquals(width,camera.getWidth());
            if(zone==13){
                // Ending_ScreenInit runs during level load, before capture boot returns.
                assertTrue(camera.getFrozen());assertEquals(x,camera.getX());assertEquals(y,camera.getY());
                assertEquals(x,camera.getXCopy());assertEquals(y,camera.getYCopy());
            }else{
                // SSZ1_ScreenInit is prepared at the actual initial-pass boundary.
                var state=(com.openggf.game.sonic3k.runtime.SszZoneRuntimeState)GameServices.zoneRuntimeState();
                assertFalse(state.screenInitApplied());
                assertTrue(GameServices.level().hasPendingInitialProcessSpritesPass());
            }
            session.step(null); // Production initial Process_Sprites, not a forced camera resnap.
            assertTrue(camera.getFrozen(),"ScreenInit owns Scroll_lock");
            assertEquals(x,camera.getX());assertEquals(y,camera.getY());
            assertEquals(x,camera.getXCopy());assertEquals(y,camera.getYCopy());
            assertSame(GameServices.sprites().getMainPlayable(),camera.getFocusedSprite());
        }
    }
    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> lockedScreenInitScenes(){
        // Ending_ScreenInit ($5AAA2) and SSZ1_ScreenInit ($571F6) write these words
        // before their initial objects; the physical ROM camera does not widen.
        return java.util.stream.IntStream.of(320,352,400,528,800).boxed().flatMap(width->
                java.util.stream.Stream.of(
                        org.junit.jupiter.params.provider.Arguments.of(width,13,1,0x200,0x200),
                        org.junit.jupiter.params.provider.Arguments.of(width,10,0,0x60,0xF49)));
    }

    @Test
    void omittedDestinationTitleFinishesDeferredAssemblyAndReleasesTheLivePlayer() throws Exception {
        var settings = new GameplayCaptureSession.Settings(
                320, "sonic", "tails", "off", null, null, null);
        try (var session = new GameplayCaptureSession(settings)) {
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(), 7, 0, settings);
            session.player().setRingCount(28);
            GameServices.level().requestZoneAndAct(4, 0);
            boolean destinationSeen = false;
            boolean pendingAssemblySeen = false;
            boolean releasedCounterSeen = false;
            for (int frame = 0; frame < 400; frame++) {
                session.step(null);
                // Prepared resources publish through the real render-thread queue.
                session.renderFrame();
                var level = GameServices.level();
                if (level.getCurrentZone() != 4) continue;
                destinationSeen = true;
                pendingAssemblySeen |= level.hasPendingFreshLevelTransitionBoundary();
                if (!level.hasPendingFreshLevelTransitionBoundary() && !releasedCounterSeen) {
                    // loc_63B4's ordinary destination count must replace the
                    // source count retained only for the locked title loop.
                    assertEquals(0, session.player().getRingCount());
                    releasedCounterSeen = true;
                }
                if (!level.hasPendingFreshLevelTransitionBoundary()
                        && !GameServices.fade().hasPendingCompletion()
                        && level.getFrameCounter() > 0) {
                    var livePlayer = GameServices.sprites().getMainPlayable();
                    assertSame(livePlayer, session.player(), "capture reports the actual live leader");
                    assertSame(livePlayer, GameServices.camera().getFocusedSprite());
                    assertNotEquals(0, livePlayer.getCentreX(), "destination player is assembled");
                    assertFalse(livePlayer.isObjectControlled());
                    assertFalse(livePlayer.isControlLocked());
                    assertTrue(pendingAssemblySeen, "the test crosses the production deferred boundary");
                    assertTrue(releasedCounterSeen);
                    return;
                }
            }
            assertTrue(destinationSeen, "the production zone load executes");
            fail("omitting title presentation stranded the fresh destination assembly");
        }
    }
}
