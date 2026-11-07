package com.openggf;

import com.openggf.control.InputHandler;
import com.openggf.game.arena.HostedArenaRequest;
import com.openggf.game.competition.NativeCompetitionRoster;
import com.openggf.game.nativemenu.NativeMenuHostContext;
import com.openggf.game.session.EngineServices;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.competition.*;
import com.openggf.ghost.GhostFrame;
import com.openggf.graphics.ScreenshotCapture;
import com.openggf.tests.RomTestUtils;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.GameplayCaptureSession;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSoloArenaViewPixels {
    private static void assertUnchanged(com.openggf.game.rewind.CompositeSnapshot before,
            com.openggf.game.rewind.CompositeSnapshot after) {
        assertEquals(before.entries().keySet(),after.entries().keySet());
        before.entries().forEach((key,value) -> assertEquals(List.of(),
                com.openggf.game.rewind.RewindSnapshotDiff.diffKey(key,value,after.entries().get(key)),key));
    }
    @Test void soloViewDraws224RowsAndMiniatureGhostWithoutAnyVisibilityWrites() throws Exception {
        assumeTrue(PresentationOffscreenTestContext.requested(),"requires owned EGL");
        var settings = new GameplayCaptureSession.Settings(320,"sonic","","off",null,null,null);
        try (var capture = new GameplayCaptureSession(settings)) {
            capture.boot(RomTestUtils.ensureSonic3kRomAvailable().toPath(),0,0,settings);
            capture.step(null);
            var engine = EngineServices.current();
            var provider = new S3kHostedArenaProvider();
            for(int arena=0;arena<5;arena++) {
                try (var session = provider.open(new HostedArenaRequest(arena,"sonic",false),
                        new NativeMenuHostContext(engine,new Sonic3kGameModule(),new InputHandler(),capture.loop()::setGameplayMode))) {
                    var context = com.openggf.game.session.SessionManager.getCurrentGameplayMode();
                    var host = (S3kCompetitionNativeHost)NativeCompetitionRoster.from(context.getWorldSession());
                    for (int frame=0;frame<90;frame++) {
                        context.plcFrameLifecycle().runLogicalIteration(() -> {}, token -> {
                            com.openggf.game.competition.NativeCompetitionIterationAccess.runAdmitted(token,
                                    () -> session.step(com.openggf.control.PlayerInputState.neutral()));
                            return null;
                        });
                    }
                    var before = context.getRewindRegistry().capture();
                    glViewport(0,0,320,224);
                    glClearColor(1,0,1,1); glClear(GL_COLOR_BUFFER_BIT);
                    session.draw();
                    var baseline = ScreenshotCapture.captureFramebuffer(320,224);
                    assertUnchanged(before,context.getRewindRegistry().capture());
                    assertEquals(224,baseline.height());
                    assertNotEquals(0xFF00FF,baseline.argb(160,223)&0xFFFFFF,"last row is presented");
                    var ghosts = new GhostRenderer();
                    ghosts.setArtSource(session.ghostArtSource());
                    var player = session.localPlayer();
                    var ghost = new ActiveGhost("remote","sonic",new GhostFrame(player.getCentreX()+40,
                            player.getCentreY(),player.getMappingFrame(),false,false,false,2,false));
                    context.getGhostRenderRegistry().register((bucket,high) -> ghosts.renderForLayer(List.of(ghost),
                            bucket,high,player.getCentreX(),player.getCentreY(),"sonic"));
                    glClear(GL_COLOR_BUFFER_BIT); session.draw();
                    var withGhost = ScreenshotCapture.captureFramebuffer(320,224);
                    assertFalse(Arrays.equals(baseline.pixels(),
                            withGhost.pixels()),"miniature ghost must change pixels");
                    assertUnchanged(before,context.getRewindRegistry().capture());
                    ghosts.clearSlots();
                }
            }
        }
    }
}
