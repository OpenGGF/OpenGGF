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
import com.openggf.graphics.SpritePresentation;
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
    /** Screen bounds of the prepared tiles that satisfy {@code filter}: minX, minY, maxX, maxY. */
    private static int[] bounds(SpritePresentation.Frame frame, java.util.function.Predicate<SpritePresentation.Tile> filter) {
        int[] box = {Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MIN_VALUE,Integer.MIN_VALUE};
        for (var tile : frame.tiles()) {
            if (!filter.test(tile)) continue;
            box[0]=Math.min(box[0],(int)tile.x()); box[1]=Math.min(box[1],(int)tile.y());
            box[2]=Math.max(box[2],(int)(tile.x()+tile.width())); box[3]=Math.max(box[3],(int)(tile.y()+tile.height()));
        }
        return box;
    }

    @Test void zoomDrawsFullSizeBodyAndGhostAtDoubledPositionsWithoutAnyStateWrites() throws Exception {
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
                    // Past the native countdown, so only TIME and the lap digit share the screen.
                    for (int frame=0;frame<330;frame++) {
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
                    assertNotEquals(0xFF00FF,baseline.argb(160,223)&0xFFFFFF,"last row is presented");
                    var source = session.ghostArtSource();
                    var player = session.localPlayer();
                    var standing = view.get().prepareOverlay(context,host);
                    int bodyX = source.screenX(player.getCentreX()), bodyY = source.screenY(player.getCentreY());
                    int[] body = bounds(standing, tile -> !tile.ghost() && tile.y() >= 32
                            && Math.abs(tile.x()+4-bodyX) < 32 && Math.abs(tile.y()+4-bodyY) < 40);
                    assertTrue(body[3]-body[1] >= 32,
                            "arena "+arena+" body must use the full-size art, not a 2x miniature: "+java.util.Arrays.toString(body));

                    var ghosts = new GhostRenderer();
                    ghosts.setArtSource(source);
                    // The local pose, 40 px right and 32 px up in world pixels: 80 and 64 on screen.
                    var ghost = new ActiveGhost("remote","sonic",new GhostFrame(player.getCentreX()+40,
                            player.getCentreY()-32,host.players().getFirst().mappingFrame(),
                            (host.players().getFirst().renderFlips()&1)!=0,false,false,2,true));
                    var activeGhost = new java.util.concurrent.atomic.AtomicReference<>(ghost);
                    com.openggf.game.ghost.GhostRenderRegistry.GhostLayerRenderer hook = (bucket,high) ->
                            ghosts.renderForLayer(List.of(activeGhost.get()),bucket,high,player.getCentreX(),player.getCentreY(),"sonic");
                    context.getGhostRenderRegistry().register(hook);
                    var prepared = view.get().prepareOverlay(context,host);
                    int[] ghostBox = bounds(prepared, SpritePresentation.Tile::ghost);
                    assertNotEquals(Integer.MAX_VALUE,ghostBox[0],"overlay must retain ghost tiles in arena "+arena);
                    assertEquals(body[0]+80,ghostBox[0],"ghost shares P1's horizontal loop image at double scale");
                    assertEquals(body[1]-64,ghostBox[1],"ghost shares P1's vertical loop image at double scale");
                    assertTrue(prepared.tiles().stream().filter(SpritePresentation.Tile::ghost).anyMatch(tile -> {
                        var pattern = prepared.patternVersions().get(tile.patternId());
                        return pattern != null && (pattern.a()!=0 || pattern.b()!=0 || pattern.c()!=0 || pattern.d()!=0);
                    }),"prepared ghost must retain nonempty ROM pattern versions in arena "+arena);
                    glClear(GL_COLOR_BUFFER_BIT); session.draw();
                    var withGhost = ScreenshotCapture.captureFramebuffer(320,224);
                    var pictures = java.nio.file.Path.of("target/arena-zoom/pixel-diagnostics");
                    java.nio.file.Files.createDirectories(pictures);
                    ScreenshotCapture.savePNG(baseline,pictures.resolve("arena-"+arena+"-baseline.png"));
                    ScreenshotCapture.savePNG(withGhost,pictures.resolve("arena-"+arena+"-ghost.png"));
                    assertFalse(sameRgb(baseline,withGhost),"full-size ghost must change pixels in arena "+arena);
                    activeGhost.set(new ActiveGhost(ghost.slotId(),ghost.characterCode(),ghost.frame(),null,.5f));
                    glClear(GL_COLOR_BUFFER_BIT); session.draw();
                    var translucent = ScreenshotCapture.captureFramebuffer(320,224);
                    assertFalse(sameRgb(withGhost,translucent),"ghost translucency must survive in arena "+arena);
                    assertUnchanged(before,context.getRewindRegistry().capture());
                    context.getGhostRenderRegistry().unregister(hook);
                    ghosts.clearSlots();
                }
            }
        }
    }
}
