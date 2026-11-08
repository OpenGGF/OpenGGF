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
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

@Isolated
@RequiresRom(SonicGame.SONIC_3K)
class TestS3kSoloArenaViewPixels {
    private static boolean sameRgb(com.openggf.graphics.RgbaImage first,com.openggf.graphics.RgbaImage second) {
        // A changed framebuffer alpha alone does not prove visible ghost compositing.
        return java.util.stream.IntStream.range(0,first.pixels().length).allMatch(index ->
                (first.pixels()[index]&0xFFFFFF)==(second.pixels()[index]&0xFFFFFF));
    }
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
            var view = new java.util.concurrent.atomic.AtomicReference<S3kSoloArenaViews>();
            var provider = new S3kHostedArenaProvider(ctx -> {
                var next = new S3kSoloArenaViews(ctx.engine().graphics());
                view.set(next);
                return next;
            });
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
                    assertTrue(java.util.stream.IntStream.range(0,16)
                            .anyMatch(colour -> host.palettes().readCurrentColor(0,colour)!=0),
                            "native player palette must be visible before pixel comparison");
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
                    // A nearby high-priority airborne pose tests the same loop image as P1 without terrain occlusion.
                    var source = session.ghostArtSource();
                    var nativePiece = view.get().projectNativeSprites(context,host,host.scroll())
                            .pieces().stream().filter(piece -> piece.nativeSlot()==0).findFirst().orElseThrow();
                    var ghost = new ActiveGhost("remote","sonic",new GhostFrame(player.getCentreX()+40,
                            player.getCentreY()-32,host.players().getFirst().mappingFrame(),false,false,false,2,true));
                    var activeGhost = new java.util.concurrent.atomic.AtomicReference<>(ghost);
                    context.getGhostRenderRegistry().register((bucket,high) -> ghosts.renderForLayer(List.of(activeGhost.get()),
                            bucket,high,player.getCentreX(),player.getCentreY(),"sonic"));
                    var prepared = view.get().prepareSprites(context,host,host.scroll());
                    var ghostTiles = prepared.tiles().stream().filter(com.openggf.graphics.SpritePresentation.Tile::ghost).toList();
                    System.out.println("arena="+arena+" player="+player.getCentreX()+","+player.getCentreY()
                            +" camera="+session.ghostArtSource().cameraX()+","+session.ghostArtSource().cameraY()
                            +" spriteMapping="+player.getMappingFrame()+" nativeMapping="+host.players().getFirst().mappingFrame()
                            +" ghostTiles="+ghostTiles);
                    glClear(GL_COLOR_BUFFER_BIT); session.draw();
                    var withGhost = ScreenshotCapture.captureFramebuffer(320,224);
                    var pictures = java.nio.file.Path.of("target/lane-b/pixel-diagnostics");
                    java.nio.file.Files.createDirectories(pictures);
                    ScreenshotCapture.savePNG(baseline,pictures.resolve("arena-"+arena+"-baseline.png"));
                    ScreenshotCapture.savePNG(withGhost,pictures.resolve("arena-"+arena+"-ghost.png"));
                    assertFalse(ghostTiles.isEmpty(),"SAT must retain ghost tiles in arena "+arena);
                    assertEquals((float)(nativePiece.screenX()+40),ghostTiles.getFirst().x(),"ghost shares P1's horizontal loop image");
                    assertEquals((float)(nativePiece.screenY()-32),ghostTiles.getFirst().y(),"ghost shares P1's vertical loop image");
                    assertTrue(ghostTiles.stream().anyMatch(tile -> {
                        var pattern = prepared.patternVersions().get(tile.patternId());
                        return pattern != null && (pattern.a()!=0 || pattern.b()!=0 || pattern.c()!=0 || pattern.d()!=0);
                    }),"prepared ghost must retain nonempty ROM pattern versions in arena "+arena);
                    assertFalse(sameRgb(baseline,withGhost),"miniature ghost must change pixels in arena "+arena);
                    activeGhost.set(new ActiveGhost(ghost.slotId(),ghost.characterCode(),ghost.frame(),null,.5f));
                    glClear(GL_COLOR_BUFFER_BIT); session.draw();
                    var translucent = ScreenshotCapture.captureFramebuffer(320,224);
                    ScreenshotCapture.savePNG(translucent,pictures.resolve("arena-"+arena+"-half-alpha.png"));
                    assertFalse(sameRgb(withGhost,translucent),
                            "SAT must retain ghost translucency in arena "+arena);
                    assertUnchanged(before,context.getRewindRegistry().capture());
                    ghosts.clearSlots();
                }
            }
        }
    }
}
