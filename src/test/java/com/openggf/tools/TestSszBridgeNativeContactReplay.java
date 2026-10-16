package com.openggf.tools;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.GameMode;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.CompositeSnapshot;
import com.openggf.game.rewind.RewindSnapshotDiff;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.objects.SszCutsceneBridgeObjectInstance;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Independently fresh SSZ with original controller suffix; native comparison is expectation only. */
@RequiresRom(SonicGame.SONIC_3K)
class TestSszBridgeNativeContactReplay {
    @Test void originalControllersAcquireTheNativeBridgeAndReplayItsWholeWorldTwice() throws Exception {
        var settings=new GameplayCaptureSession.Settings(320,"sonic","tails","off",null,null,null);
        var movie=new Bk2MovieLoader().loadMovieOrInputLog(Path.of(
                "src/test/resources/traces/s3k/runs/s3k-sonic-tails-complete-emeralds/s3k-sonic-tails-complete-emeralds.bk2"));
        try(var session=new GameplayCaptureSession(settings)) {
            GameServices.configuration().setSessionOverride(SonicConfiguration.S3K_SKIP_INTROS,false);
            session.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),10,0,settings);
            // Initial ProcessSprites is not an ordinary controller poll. Keep original suffix448920.
            session.step(new Bk2FrameInput(0,0,0,false,"declared-neutral-initial-process"));
            session.renderFrame();assertEquals(0,GameServices.level().getFrameCounter());
            for(int poll=0;poll<=1341;poll++) {session.step(movie.getFrame(448920+poll));session.renderFrame();}
            var player=GameServices.sprites().getMainPlayable();
            assertEquals(808,player.getCentreX());assertEquals(3193,player.getCentreY());
            assertTrue(player.getAir());assertEquals(512,GameServices.camera().getX());
            assertEquals(3008,GameServices.camera().getY());
            var registry=SessionManager.getCurrentGameplayMode().getRewindRegistry();
            var manager=GameServices.level().getObjectManager();
            var runtime=GameServices.zoneRuntimeRegistry().current();
            int lives=GameServices.gameState().getLives();
            var before=registry.capture();
            Runnable advance=()->{
                for(int poll=1342;poll<=1386;poll++) {
                    session.step(movie.getFrame(448920+poll));session.renderFrame();
                    assertEquals(GameMode.LEVEL,session.loop().getCurrentGameMode());
                    assertSame(manager,GameServices.level().getObjectManager());
                    assertSame(runtime,GameServices.zoneRuntimeRegistry().current());
                    assertEquals(lives,GameServices.gameState().getLives());
                    assertFalse(GameServices.sprites().getMainPlayable().getDead());
                    if(poll==1342) {
                        var live=GameServices.sprites().getMainPlayable();
                        // Genuine native prepared poll1342: bridge x894/y3208, d3=9, d0=-16.
                        assertEquals(808,live.getCentreX());assertEquals(3179,live.getCentreY());
                        assertFalse(live.getAir());assertTrue(live.isOnObject());
                        assertTrue(manager.getActiveObjects().stream().filter(SszCutsceneBridgeObjectInstance.class::isInstance)
                                .anyMatch(object->manager.hasObjectStandingBit(live,object)));
                    }
                }
            };
            advance.run();var after=registry.capture();
            for(int cycle=0;cycle<2;cycle++) {
                registry.restore(before);same(before,registry.capture());
                advance.run();same(after,registry.capture());
                registry.restore(before);same(before,registry.capture());
                registry.restore(after);same(after,registry.capture());
            }
        }
    }
    private static void same(CompositeSnapshot expected,CompositeSnapshot actual) {
        assertEquals(expected.entries().keySet(),actual.entries().keySet());
        for(String key:expected.entries().keySet())assertTrue(
                RewindSnapshotDiff.diffKey(key,expected.get(key),actual.get(key)).isEmpty(),key);
    }
}
