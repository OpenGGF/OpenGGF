package com.openggf.stageplay.runtime;

import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native-body readback from the cinematic priority renderer, not an image-existence check. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayLiveWorldRendering {
    @org.junit.jupiter.api.AfterEach void restoreLogicGraphics() { GameServices.graphics().initHeadless(); }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"first", "hud", "platform", "air", "boundary"})
    void nativeBodiesAppearAtTheResolvedDirectorCoordinatesWithoutTicking(String scene) throws Exception {
        var path=Path.of(System.getProperty("s3k.rom.path"));
        var config=GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        try(var boot=new HeadlessGameBoot(320,224)) {
            boot.setBootToTitle(true); boot.boot(path,0,0);
            try(var library=SceneRomLibrary.open(Map.of("s3k",path));
                var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode());
                var grabber=new GlReadPixelsGrabber(320,224)) {
                String source=scene.equals("boundary") || scene.equals("first") ? "one-more-take" : "live-air-traffic";
                var document=(scene.equals("platform") || scene.equals("hud")) ? TestStageplayLiveWorldRom.platform()
                        : com.openggf.tools.StageplayScriptCompiler.compile(java.nio.file.Files.readAllBytes(
                            Path.of("examples/stageplay-demo/src/main/resources/scenes/"+source+".screenplay.json")));
                byte[] entry=null;
                if(scene.equals("boundary")) {
                    var first=world.resolve(document,StageplayTimeline.sample(document,35));
                    world.draw(first);entry=grabber.grab().clone();
                    var other=world.resolve(document,StageplayTimeline.sample(document,1375));world.draw(other);
                }
                int atFrame=scene.equals("first") ? 0 : scene.equals("boundary") ? 2255 : (scene.equals("platform") || scene.equals("hud")) ? 35 : 115;
                var frame=world.resolve(document,StageplayTimeline.sample(document,atFrame));
                var palette = com.openggf.graphics.PaletteUploadPresentation.captureDisplayedFade(GameServices.graphics());
                int clock = GameServices.level().getFrameCounter();
                int nativeX = GameServices.sprites().getMainPlayable().getCentreX(), nativeY = GameServices.sprites().getMainPlayable().getCentreY();
                world.draw(frame); byte[] shown=grabber.grab().clone();
                assertEquals(palette, com.openggf.graphics.PaletteUploadPresentation.captureDisplayedFade(GameServices.graphics()));
                assertEquals(clock, GameServices.level().getFrameCounter());
                assertEquals(nativeX, GameServices.sprites().getMainPlayable().getCentreX());
                assertEquals(nativeY, GameServices.sprites().getMainPlayable().getCentreY());
                world.draw(frame); assertArrayEquals(shown, grabber.grab(), "First and repeated draw must show the same initialized world");
                if (scene.equals("hud")) assertNoHudPixels(world, frame, grabber, shown);
                int coloured=0;
                for(int at=0;at<shown.length;at+=4)
                    if((shown[at]&255)>16 || (shown[at+1]&255)>16 || (shown[at+2]&255)>16) coloured++;
                assertTrue(coloured>320*224/3,"Native terrain must remain visible, not only bodies/objects: "+coloured);
                if(entry!=null) assertArrayEquals(entry,shown,"Returning from another roster/act must republish the native picture");
                GameServices.sprites().getAllSprites().forEach(s->{
                    if(s instanceof com.openggf.sprites.playable.AbstractPlayableSprite p) p.setHidden(true);
                });
                world.draw(frame); byte[] hidden=grabber.grab();
                for(var actor:frame.actors()) {
                    if(!actor.visible()) continue;
                    int changed=0;
                    // Probe the composed film, whose camera scales world offsets.
                    // The air-traffic fixture includes an animated fractional pull-out.
                    double zoom=frame.camera().zoom();
                    int x=(int)Math.round((actor.x()-frame.camera().x())*zoom),
                            y=(int)Math.round((actor.y()-frame.camera().y())*zoom);
                    assertTrue(x>=20 && x<300 && y>=20 && y<204,
                            "Followed actor must be in picture: "+actor.actorId()+" at "+x+","+y);
                    for(int yy=Math.max(0,y-24);yy<Math.min(224,y+24);yy++) for(int xx=Math.max(0,x-24);xx<Math.min(320,x+24);xx++) {
                        int at=((223-yy)*320+xx)*4;
                        if(shown[at]!=hidden[at] || shown[at+1]!=hidden[at+1] || shown[at+2]!=hidden[at+2]) changed++;
                    }
                    assertTrue(changed>40,"Native body pixels at "+actor.actorId()+"; changed="+changed
                            +"; film centre="+x+","+y+"; zoom="+zoom);
                }
                assertSame(frame,world.resolve(document,StageplayTimeline.sample(document,atFrame)),"Drawing cannot tick the world");
            }
        }
    }
    /** Removing real HUD rows must alter ordinary HUD pixels while leaving the film byte-identical. */
    private static void assertNoHudPixels(StageplayLiveWorld world, StageplayTimeline.Frame frame,
                                          GlReadPixelsGrabber grabber, byte[] film) {
        var level = GameServices.level(); var graphics = GameServices.graphics();
        var hud = graphics.getUiRenderPipeline().getHudRenderManager();
        var profile = com.openggf.level.objects.HudProfileAccess.current(hud);
        byte[] withHud = gameplayPicture(grabber);
        try {
            com.openggf.level.objects.HudProfileAccess.install(hud, new com.openggf.level.objects.HudProfile(java.util.List.of()));
            com.openggf.level.LevelSpritePresentation.prepare(level, GameServices.sprites());
            com.openggf.level.LevelSpritePresentation.publishPreparedScene(level);
            byte[] withoutHud = gameplayPicture(grabber);
            int changed = 0;
            for (int y = 0; y < 224; y++) for (int x = 8; x < 120; x++) {
                if (y >= 64 && y < 192) continue;
                int at = ((223-y)*320+x)*4;
                if (withHud[at]!=withoutHud[at] || withHud[at+1]!=withoutHud[at+1] || withHud[at+2]!=withoutHud[at+2]) changed++;
            }
            assertTrue(changed > 100, "Positive control: real score/time/rings/lives glyphs must change ordinary HUD pixels: " + changed);
            world.draw(frame);
            assertArrayEquals(film, grabber.grab(), "Cinematic pixels must be independent of every gameplay HUD row");
        } finally {
            com.openggf.level.objects.HudProfileAccess.install(hud, profile);
            com.openggf.level.LevelSpritePresentation.prepare(level, GameServices.sprites());
            com.openggf.level.LevelSpritePresentation.publishPreparedScene(level);
        }
    }

    private static byte[] gameplayPicture(GlReadPixelsGrabber grabber) {
        var level = GameServices.level(); level.setClearColor();
        org.lwjgl.opengl.GL11.glClear(org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT | org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(), com.openggf.level.LevelManager.LevelRenderOptions.gameplay());
        GameServices.graphics().flush();
        return grabber.grab().clone();
    }

}
