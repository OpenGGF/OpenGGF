package com.openggf.level;

import com.openggf.capture.GlReadPixelsGrabber;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.game.GameServices;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.color.DisplayColorConverter;
import com.openggf.mods.scene.host.SceneRomLibrary;
import com.openggf.stageplay.StageplayTimeline;
import com.openggf.stageplay.runtime.StageplayLiveWorld;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.HeadlessGameBoot;
import com.openggf.tools.StageplayScriptCompiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** ROM-decoded floor pixels and the GPU priority mask must share the actors' directed view. */
@RequiresRom(SonicGame.SONIC_3K)
class TestStageplayCinematicCameraRendering {
    @AfterEach void restoreLogicGraphics() { GameServices.graphics().initHeadless(); }

    @ParameterizedTest @ValueSource(ints={120,239})
    void groundedFeetTerrainAndPriorityMaskStayTogetherAcrossDirectorMotion(int atFrame) throws Exception {
        var rom=Path.of(System.getProperty("s3k.rom.path"));
        var config=GameServices.configuration();
        config.setSessionOverride(SonicConfiguration.AUDIO_ENABLED,false);
        config.setSessionOverride(SonicConfiguration.MAIN_CHARACTER_CODE,"sonic");
        config.setSessionOverride(SonicConfiguration.SIDEKICK_CHARACTER_CODE,"");
        config.setSessionOverride(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED,false);
        try(var boot=new HeadlessGameBoot(320,224)) {
            boot.setBootToTitle(true); boot.boot(rom,0,0);
            try(var library=SceneRomLibrary.open(Map.of("s3k",rom));
                var world=StageplayLiveWorld.adoptOffline(library,SessionManager.getCurrentGameplayMode());
                var grabber=new GlReadPixelsGrabber(320,224)) {
                var scene=StageplayScriptCompiler.compile(Files.readAllBytes(Path.of(
                        "examples/stageplay-demo/src/main/resources/scenes/live-ring-run.screenplay.json")));
                var frame=world.resolve(scene,StageplayTimeline.sample(scene,atFrame));
                var level=GameServices.level(); var camera=GameServices.camera();
                var renderer=level.spritePresentationRenderer();
                var nativeCamera=camera.capture(); var tables=renderer.spriteTables.capture();
                var scroll=LevelScrollPresentation.capture(level,renderer.getCurrentAdvancedRenderFrameState());
                var view=new StageplayTimeline.CameraPose(Math.round(frame.camera().x()),Math.round(frame.camera().y()),1);
                System.out.println("Cinematic floor probe f"+atFrame+" directorY="+view.y()+" nativeY="+camera.getY()
                        +" foregroundY="+level.parallaxManager.getVscrollFactorFG()+" backgroundY="+level.parallaxManager.getVscrollFactorBG());
                drawOrdinary(level); byte[] ordinary=grabber.grab().clone();
                world.draw(frame,view); byte[] first=grabber.grab().clone();
                var players=GameServices.sprites().getAllSprites().stream()
                        .filter(s->s instanceof com.openggf.sprites.playable.AbstractPlayableSprite)
                        .map(s->(com.openggf.sprites.playable.AbstractPlayableSprite)s).toList();
                var hidden=players.stream().map(p->p.isHidden()).toList();
                try {
                    players.forEach(p->p.setHidden(true));
                    // Negative control: the former Stageplay override moved actors/camera but
                    // retained native VSRAM. It must demonstrably disagree with ROM tile coordinates.
                    camera.setX((short)view.x()); camera.setY((short)view.y());
                    camera.setXCopy(camera.getX()); camera.setYCopy(camera.getY());
                    try {
                        level.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                        level.drawCinematicWorld(GameServices.sprites()); GameServices.graphics().flush();
                        int displaced=maskMismatches(level,view,priorityMask());
                        System.out.println("Old camera-only mask mismatches f"+atFrame+": "+displaced);
                        assertTrue(displaced>100,
                                "The old camera-only override must reproduce the terrain/mask displacement");
                    } finally { camera.restore(nativeCamera,camera.getFocusedSprite()); }
                    for(int shift:new int[]{0,-32,32}) {
                        var shifted=new StageplayTimeline.CameraPose(view.x()+shift/2,view.y()+shift,1);
                        for(int i=0;i<players.size();i++) players.get(i).setHidden(hidden.get(i));
                        world.draw(frame,shifted); byte[] bodies=grabber.grab().clone();
                        players.forEach(p->p.setHidden(true));
                        world.draw(frame,shifted); byte[] terrain=grabber.grab().clone();
                        int grounded=0,groundedBodyPixels=0;
                        for(var player:players) {
                            if(player.getAir()) continue;
                            int footY=player.getCentreY()+player.getYRadius();
                            if (footY-shifted.y()<3 || footY-shifted.y()>=212) continue;
                            int tested=0,matched=0;
                            for(int y=footY-1;y<footY+12;y++) for(int x=player.getCentreX()-10;x<=player.getCentreX()+10;x++) {
                                int sx=x-(int)shifted.x(),sy=y-(int)shifted.y();
                                if(sx<0 || sx>=320 || sy<0 || sy>=224) continue;
                                var pixel=foregroundPixel(level,x,y);
                                if(pixel==null) continue;
                                tested++; if(rgb(terrain,sx,sy)==pixel.rgb()) matched++;
                            }
                            // Collision silhouettes can extend beyond visible art at a ledge.
                            // Compare every decoded art pixel; require an actual art-backed native
                            // contact in each view rather than inventing art beneath every actor.
                            if(tested>0) {
                                assertTrue(matched>tested*.8,"Grounded feet must meet rendered ROM terrain, f"+atFrame+" shift="+shift
                                        +" actor="+player.getCode()+" floor pixels="+matched+"/"+tested);
                                grounded++;
                            }
                            int bodyPixels=0;
                            for(int y=footY-28;y<footY;y++) for(int x=player.getCentreX()-16;x<=player.getCentreX()+16;x++) {
                                int sx=x-(int)shifted.x(),sy=y-(int)shifted.y();
                                if(sx>=0 && sx<320 && sy>=0 && sy<224 && rgb(bodies,sx,sy)!=rgb(terrain,sx,sy)) bodyPixels++;
                            }
                            groundedBodyPixels+=bodyPixels;
                            System.out.println("Cinematic contact f"+atFrame+" shift="+shift+" "+player.getCode()
                                    +" floor="+matched+"/"+tested+" body="+bodyPixels);
                            if(tested==0 && shift==0) for(var sensor:player.getAllSensors()) {
                                var hit=sensor.getCurrentResult();
                                if(sensor.getDirection()==com.openggf.physics.Direction.DOWN && hit!=null)
                                    System.out.println("Native contact without art "+player.getCode()+" sensor="+sensor.getX()+","+sensor.getY()
                                            +" tile="+hit.tileId()+" distance="+hit.distance());
                            }
                        }
                        assertTrue(grounded>0,"This obligation must actually cover a native grounded performer");
                        // A hurt performer may be in its native invisible blink phase. Require a
                        // real visible grounded body in this view, without overriding that phase.
                        assertTrue(groundedBodyPixels>40,"An actual grounded body must render immediately above the tested floor");
                        byte[] mask=priorityMask(); int checked=0,high=0;
                        for(int y=2;y<222;y+=2) for(int x=2;x<318;x+=2) {
                            var pixel=foregroundPixel(level,(int)shifted.x()+x,(int)shifted.y()+y);
                            boolean expected=pixel!=null && pixel.high();
                            assertEquals(expected,(mask[(223-y)*320+x]&255)>127,"Foreground priority mask must share visible terrain origin at "+x+","+y);
                            checked++; if(expected) high++;
                        }
                        assertTrue(checked>10000 && high>100,"The mask check needs both real high-priority terrain and empty pixels");
                    }
                } finally {
                    for(int i=0;i<players.size();i++) players.get(i).setHidden(hidden.get(i));
                }
                world.draw(frame,view); assertArrayEquals(first,grabber.grab(),"A camera excursion cannot alter the native picture");
                assertEquals(nativeCamera,camera.capture(),"Restore all native camera registers and copies");
                assertEquals(tables,renderer.spriteTables.capture(),"Do not republish native SAT/scroll/CRAM generations");
                assertEquals(scroll,LevelScrollPresentation.capture(level,renderer.getCurrentAdvancedRenderFrameState()),"Do not mutate native parallax output or render policy");
                assertThrows(IllegalStateException.class,()->LevelSpritePresentation.withCinematicCamera(level,
                        (int)view.x()+23,(int)view.y()-17,()-> {
                            level.drawCinematicWorld(GameServices.sprites());
                            throw new IllegalStateException("aborted film frame with queued commands");
                        }));
                assertEquals(nativeCamera,camera.capture(),"An aborted scope also restores the native camera");
                assertEquals(scroll,LevelScrollPresentation.capture(level,renderer.getCurrentAdvancedRenderFrameState()));
                assertEquals(tables,renderer.spriteTables.capture(),"An aborted queued frame cannot publish native presentation");
                drawOrdinary(level);
                assertArrayEquals(ordinary,grabber.grab(),"Cinematic excursions must leave ordinary SAT/scroll rendering byte-identical");
                world.resolve(scene,StageplayTimeline.sample(scene,atFrame-20));
                assertEquals(frame,world.resolve(scene,StageplayTimeline.sample(scene,atFrame)),"Repeated drawing must not change whole-world replay");
                world.draw(frame=world.resolve(scene,StageplayTimeline.sample(scene,atFrame)),view);
                assertArrayEquals(first,grabber.grab(),"Replay must reproduce terrain and actors together");
            }
        }
    }

    private record Pixel(int rgb,boolean high) { }
    private static void drawOrdinary(LevelManager level) {
        level.setClearColor(); glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        level.drawWithRenderOptions(GameServices.sprites(),LevelManager.LevelRenderOptions.gameplay());
        GameServices.graphics().flush();
    }
    private static int maskMismatches(LevelManager level,StageplayTimeline.CameraPose view,byte[] mask) {
        int count=0;
        for(int y=2;y<222;y+=2) for(int x=2;x<318;x+=2) {
            var pixel=foregroundPixel(level,(int)view.x()+x,(int)view.y()+y);
            if((pixel!=null && pixel.high()) != ((mask[(223-y)*320+x]&255)>127)) count++;
        }
        return count;
    }
    private static Pixel foregroundPixel(LevelManager manager,int x,int y) {
        var level=manager.getCurrentLevel(); var block=manager.getBlockAtPosition((byte)0,x,y);
        if(block==null) return null;
        int side=level.getBlockPixelSize();
        var descriptor=block.getChunkDesc(Math.floorMod(x,side)/16,Math.floorMod(y,side)/16);
        int cx=x&15,cy=y&15;
        if(descriptor.getHFlip()) cx=15-cx;
        if(descriptor.getVFlip()) cy=15-cy;
        var pattern=level.getChunk(descriptor.getChunkIndex()).getPatternDesc(cx/8,cy/8);
        int px=cx&7,py=cy&7;
        if(pattern.getHFlip()) px=7-px;
        if(pattern.getVFlip()) py=7-py;
        int index=level.getPattern(pattern.getPatternIndex()).getPixel(px,py)&15;
        if(index==0) return null;
        var colour=level.getPalette(pattern.getPaletteIndex()).getColor(index); var graphics=GameServices.graphics();
        return new Pixel(DisplayColorConverter.toPackedRgb(colour.r&255,colour.g&255,colour.b&255,
                graphics.getDisplayColorProfile(),graphics.getDisplayColorExpansion()),pattern.getPriority());
    }
    private static int rgb(byte[] rgba,int x,int y) {
        int at=((223-y)*320+x)*4;
        return ((rgba[at]&255)<<16)|((rgba[at+1]&255)<<8)|(rgba[at+2]&255);
    }
    private static byte[] priorityMask() {
        int bound=glGetInteger(GL_TEXTURE_BINDING_2D);
        var buffer=MemoryUtil.memAlloc(320*224);
        try {
            glBindTexture(GL_TEXTURE_2D,GameServices.graphics().getTilePriorityFBO().getTextureId());
            glGetTexImage(GL_TEXTURE_2D,0,GL_RED,GL_UNSIGNED_BYTE,buffer);
            byte[] result=new byte[buffer.remaining()]; buffer.get(result); return result;
        } finally { glBindTexture(GL_TEXTURE_2D,bound); MemoryUtil.memFree(buffer); }
    }
}
