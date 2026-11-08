package com.openggf.game.sonic2.titlecard;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.control.*;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.session.*;
import com.openggf.game.sonic2.competition.*;
import com.openggf.game.sonic2.competition.view.*;
import com.openggf.graphics.*;
import com.openggf.tests.*;
import com.openggf.tests.rules.*;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/** Production host leave/tail cuts and destructive mid-card registry restore. No reference hydration. */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class TestObj34CompetitionPixels {
    private static final Path OUT=Path.of("target/obj34-2p-pixels");
    @Test void nativeTitlePagesAndPlanesSurviveDestructiveMidCardRestoreAndForwardReplay() throws Exception {
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var services=EngineServices.current();var root=SessionManager.getCurrentWorldSession().rootGameModule();
        try(var gl=PresentationOffscreenTestContext.openConfigured(320,224,PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            AutoCloseable cleanup=()->{try{SessionManager.closeGameplaySession();}finally{services.graphics().cleanup();}};
            var views=new Sonic2CompetitionViews(services.graphics())) {
            var graphics=services.graphics();graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0,0,320,224);glViewport(0,0,320,224);Files.createDirectories(OUT);
            var title=(TitleCardManager)root.getTitleCardProvider();
            var restored=new boolean[1];var full=new boolean[1];var forwardChecked=new boolean[1];
            var saved=new Object[1];var original=new int[1][];var forward=new int[1][];
            var probe=new Sonic2NativeCompetitionHost.Views(){
                public void prepare(GameplayModeContext context,Sonic2CompetitionSession session,
                                    Sonic2CompetitionLevelProfile profile,Sonic2CompetitionNativeArt art){
                    views.prepare(context,session,profile,art);
                    var video=context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
                    int pass=title.capture().leavePass();
                    if(!full[0] && pass==1){
                        full[0]=true;var registers=video.registers().capture();
                        // Explicit component cut at the omitted arrival boundary. No
                        // VInt/art/clock is executed: publish the prepared page only.
                        var palette=new ArrayList<Integer>(64);
                        var level=context.getLevelManager().getCurrentLevel();
                        for(int line=0;line<4;line++)for(int color=0;color<16;color++){
                            var rgb=level.getPalette(line).getColor(color);
                            palette.add((((rgb.r&255)*7+127)/255<<1)|(((rgb.g&255)*7+127)/255<<5)|(((rgb.b&255)*7+127)/255<<9));
                        }
                        video.registers().publish(PlcLifecyclePhase.LEVEL_TITLE_CARD,0,(-224)<<16,new int[224],
                                session.camera(0).capture(),session.camera(1).capture(),video.spritePages().completed(),palette,0,false);
                        capture(views,context,session,"f1300-engine");video.registers().restore(registers);
                    }
                    if(pass==7 && saved[0]==null){
                        saved[0]=context.getRewindRegistry().capture();
                        original[0]=capture(views,context,session,"f1310-engine");
                    }
                    if(pass==22 && !restored[0]){
                        forward[0]=capture(views,context,session,"f1325-engine");
                        assertFalse(Arrays.equals(original[0],forward[0]),"leave progression changes title coverage");
                        for(var plane:Sonic2CompetitionPlanes.PlaneId.values())
                            for(int row=0;row<32;row++)for(int col=0;col<64;col++)video.planes().write(plane,col,row,0xffff);
                        video.art().resetForMissingSnapshot();video.planeScroll().menuClear();
                        video.spritePages().restore(new Sonic2CompetitionSpritePages.Pair(
                                Sonic2CompetitionSatBuilder.Page.empty(0),Sonic2CompetitionSatBuilder.Page.empty(1)));
                        title.obj34.parts[0].x=0xffff;
                        // The actual registry captures Obj34 once, along with the existing
                        // planes/art/pages/registers owners. No consumer has saved state.
                        restore(context,saved[0]);restored[0]=true;
                        assertArrayEquals(original[0],capture(views,context,session,"restored-f1310"));
                        int[] broken=original[0].clone();broken[0]^=1;
                        assertThrows(AssertionError.class,()->assertArrayEquals(broken,original[0]),"negative pixel control");
                    }else if(pass==22 && restored[0] && !forwardChecked[0]){
                        forwardChecked[0]=true;
                        assertArrayEquals(forward[0],capture(views,context,session,"replayed-f1325"),"forward replay after destructive restore");
                    }
                }
                public void draw(GameplayModeContext context,Sonic2CompetitionSession session){views.draw(context,session);}
            };
            var host=Sonic2NativeCompetitionHost.production(root,services,ignored->{},probe,false,true,
                    new com.openggf.game.sonic2.objects.Sonic2TeleportObjectFieldsAccess());
            host.clearMenuCameraRam();
            var input=new InputHandler();input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical,()->false);
            var session=new Sonic2CompetitionSession();session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral()),true);
            host.loadAct(session,0,0);
            for (int wait=0;wait<300 && host.titleCardActive();wait++) {
                var mode=host.context();
                mode.plcFrameLifecycle().runLogicalIteration(()->{},frame->{
                    com.openggf.game.competition.NativeCompetitionIterationAccess.runAdmitted(frame,
                            ()->host.step(session,input));
                    return null;
                });
            }
            assertFalse(host.titleCardActive());
            assertTrue(restored[0]);assertTrue(forwardChecked[0]);
            for(int i=0;i<50;i++)step(host,input);
            capture(views,host.context(),session,"f1380-engine");
            assertEquals(GL_NO_ERROR,glGetError());
        }finally{TestEnvironment.resetAll();}
    }
    private static void restore(GameplayModeContext context,Object saved){
        context.getRewindRegistry().restore((com.openggf.game.rewind.CompositeSnapshot)saved);
    }
    private static int[] capture(Sonic2CompetitionViews views,GameplayModeContext context,Sonic2CompetitionSession session,String name){
        try{
            var graphics=GameServices.graphics();graphics.bindRuntimeManagedReferences(context.getCamera(),context.getFadeManager());
            PaletteUploadPresentation.withoutDisplayedFade(graphics,()->views.draw(context,session));
            var image=ScreenshotCapture.captureFramebuffer(320,224);
            assertTrue(Arrays.stream(image.pixels()).distinct().count()>3,"actual native renderer output");
            ScreenshotCapture.savePNG(image,OUT.resolve(name+".png"));
            var video=context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
            Files.writeString(OUT.resolve(name+".txt"),"title="+TitleCardNativeDisplayAccess.read((TitleCardManager)SessionManager.getCurrentWorldSession().rootGameModule().getTitleCardProvider())
                    +"\nregisters="+video.registers().capture()+"\nplanes="+Arrays.deepToString(video.planes().capture().cells()));
            return image.pixels();
        }catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    private static void step(Sonic2NativeCompetitionHost host,InputHandler input){
        var context=host.context();LevelFrameStep.updateTimers(LevelFrameContext.from(context));
        context.plcFrameLifecycle().runLogicalIteration(context::updateFade,frame->{
            var result=new LevelFrameResult[1];NativeCompetitionIterationAccess.runAdmitted(frame,()->result[0]=host.step(input,frame));return result[0];});
    }
}
