package com.openggf.game.sonic2.competition.view;

import com.openggf.LevelFrameContext;
import com.openggf.LevelFrameResult;
import com.openggf.LevelFrameStep;
import com.openggf.audio.NullAudioBackend;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.debug.playback.RecordedInputSnapshots;
import com.openggf.game.GameServices;
import com.openggf.game.competition.NativeCompetitionIterationAccess;
import com.openggf.game.session.EngineServices;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.competition.*;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.graphics.*;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tools.PresentationOffscreenTestContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.parallel.Isolated;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Purpose: bounded C2 artifacts from the actual host and two-controller input movies.
 * Inputs: supplied S2 REV01 and the external native-evidence BK2s; no physics/aux/CSV input.
 * Origin: A2-S C2 checkpoint, 2026-11-07. Explicit Debug selector only; no CI fixture dependency.
 */
@Isolated @RequiresRom(SonicGame.SONIC_2)
class DebugSonic2CompetitionC2Capture {
    private static final Path REFERENCES=Path.of("/tmp/s2comp-analysis-20261107/native-refs");
    private static final Path OUT=Path.of("target/s2comp-c2");
    @ParameterizedTest @ValueSource(strings={"flowers","finish","monitor"})
    void captureAvailableC2MovieCuts(String film) throws Exception {
        String movieName=switch(film){case "flowers"->"ehz-flowers";case "finish"->"ehz-finish";default->"ehz-monitor-high";};
        var movie=new Bk2MovieLoader().load(REFERENCES.resolve(movieName+".bk2"));
        GameServices.audio().setBackend(new NullAudioBackend());
        HeadlessTestFixture.builder().withZoneAndAct(0,0).build();
        var services=EngineServices.current();var root=SessionManager.getCurrentWorldSession().rootGameModule();
        var config=GameServices.configuration();boolean limits=config.getBoolean(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT);
        config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT,true);
        try(var gl=PresentationOffscreenTestContext.openConfigured(320,224,PresentationOffscreenTestContext.LegacyProfile.CORE_41);
            AutoCloseable cleanup=()->{try{SessionManager.closeGameplaySession();}finally{services.graphics().cleanup();}};
            var views=new Sonic2CompetitionViews(services.graphics())) {
            var graphics=services.graphics();graphics.setHeadlessMode(false);
            graphics.init(com.openggf.Engine.RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
            graphics.setViewport(0,0,320,224);glViewport(0,0,320,224);Files.createDirectories(OUT);
            Files.deleteIfExists(OUT.resolve(film+"-failure.txt"));
            var captured=new HashSet<String>();
            var probe=new Sonic2NativeCompetitionHost.Views(){
                public void prepare(GameplayModeContext context,Sonic2CompetitionSession session,
                                    Sonic2CompetitionLevelProfile profile,Sonic2CompetitionNativeArt art){
                    views.prepare(context,session,profile,art);
                    if(!film.equals("flowers"))return;
                    var title=(TitleCardManager)root.getTitleCardProvider();
                    var video=context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
                    String name=title.capture().leavePass()==22 && !context.getCamera().isLevelStarted()
                            && video.registers().capture().published()?"title-locked":
                            context.getLevelManager().getFrameCounter()==50?"title-leave":null;
                    if(name!=null && captured.add(name))try{capture(views,context,session,name,"existing title owner; native Obj34 field migration blocked",0);}
                    catch(Exception failure){throw new RuntimeException(failure);}
                }
                public void draw(GameplayModeContext context,Sonic2CompetitionSession session){views.draw(context,session);}
            };
            var host=Sonic2NativeCompetitionHost.production(root,services,ignored->{},probe,false,true,
                    new com.openggf.game.sonic2.objects.Sonic2TeleportObjectFieldsAccess());
            host.clearMenuCameraRam(); // The movie enters through the real 2P menu camera-clear event.
            var input=new InputHandler();input.setLogicalOverride(LogicalInputSnapshot.neutral());
            host.bindNativeWaitInput(input::logical,()->false);
            var session=new Sonic2CompetitionSession();
            session.updateMenu(LogicalInputSnapshot.ofPlayers(
                    PlayerInputState.of(0,0,0,0,true,true),PlayerInputState.neutral()),true);
            host.loadAct(session,0,0);
            var cuts=new TreeMap<Integer,String>();
            if(film.equals("flowers")){cuts.put(470,"flowers-1800");cuts.put(487,"flowers-1817");}
            else if(film.equals("finish")){cuts.put(578,"hud-ring-1908");cuts.put(9570,"hud-lives-10900");}
            else cuts.put(545,"monitor-1875");
            int target=cuts.lastKey(),attempts=0;
            while(host.context().getLevelManager().getFrameCounter()<target && attempts++<target+16){
                var context=host.context();int next=context.getLevelManager().getFrameCounter()+1;
                // Native evidence first ordinary LFC0 is physical1329. Read only
                // that movie's controller row for the following completed tick.
                int physical=1329+next;input.setLogicalOverride(at(movie,physical));
                LevelFrameStep.updateTimers(LevelFrameContext.from(context));
                try {
                    context.plcFrameLifecycle().runLogicalIteration(context::updateFade,frame->{
                        var result=new LevelFrameResult[1];
                        NativeCompetitionIterationAccess.runAdmitted(frame,()->result[0]=host.step(input,frame));
                        return result[0];
                    });
                } catch(RuntimeException failure) {
                    Files.writeString(OUT.resolve(film+"-failure.txt"),"physicalInput="+physical
                            +" levelFrame="+context.getLevelManager().getFrameCounter()+"\n"+failure+"\n");
                    throw failure;
                }
                int now=context.getLevelManager().getFrameCounter();String name=cuts.get(now);
                if(name!=null && captured.add(name))capture(views,context,session,name,
                        movieName+".bk2 controller-only cut; first ordinary physical1329; no state hydration",physical);
            }
            for(String name:cuts.values())assertTrue(captured.contains(name),"capture cut not reached: "+name);
            if(film.equals("flowers")){assertTrue(captured.contains("title-locked"));assertTrue(captured.contains("title-leave"));}
            assertInstanceOf(NullAudioBackend.class,services.audio().getBackend());assertEquals(GL_NO_ERROR,glGetError());
        } finally {config.setConfigValue(SonicConfiguration.DISPLAY_SPRITE_SCANLINE_LIMIT,limits);TestEnvironment.resetAll();}
    }
    private static LogicalInputSnapshot at(Bk2Movie movie,int physical){
        int index=physical-1;
        if(index<0 || index>=movie.getFrameCount())throw new IllegalArgumentException("Input movie does not contain physical frame "+physical);
        return RecordedInputSnapshots.fromBk2(movie.getFrame(index),index==0?null:movie.getFrame(index-1));
    }
    private static void capture(Sonic2CompetitionViews views,GameplayModeContext context,
                                Sonic2CompetitionSession session,String name,String source,int physical) throws Exception {
        var graphics=GameServices.graphics();graphics.bindRuntimeManagedReferences(context.getCamera(),context.getFadeManager());
        var video=context.getWorldSession().getGameModule().getGameService(Sonic2CompetitionVideoFrame.class);
        var registers=video.registers().capture();
        int lfc=context.getLevelManager().getFrameCounter(),vint=context.getObjectManager().getVblaCounter();
        var first=session.player(0).getCentreX()+","+session.player(0).getCentreY();
        var second=session.player(1).getCentreX()+","+session.player(1).getCentreY();
        PaletteUploadPresentation.withoutDisplayedFade(graphics,()->views.draw(context,session));
        var image=ScreenshotCapture.captureFramebuffer(320,224);
        assertTrue(Arrays.stream(image.pixels()).distinct().count()>2,"image must contain actual renderer output");
        TestSonic2CompetitionFieldPatternOwnership.assertAtlas(video.art(),graphics);
        ScreenshotCapture.savePNG(image,OUT.resolve(name+".png"));
        var text=new StringBuilder("source="+source+"\nphysicalInput="+physical+"\nlevelFrame="+lfc+" vint="+vint+"\n"
                +"upperPlane="+registers.upperPlane()+"\nP1="+first+" P2="+second+"\n"
                +"P1Lives="+session.gameState(0).getLives()+" P2Lives="+session.gameState(1).getLives()+"\n"
                +"P1Rings="+session.levelState(0).getRings()+" P2Rings="+session.levelState(1).getRings()+"\n"
                +"ringPhasePort="+video.displayState().ringsAnimationFrame()+"\n"
                +"NativeState=E1 production host with A1 backgrounds, retained plane owner and concrete C object fields\n"
                +"TitleCard=native Obj34 single-owner migration blocked; title planes/art remain baseline\n"
                +"deferred="+Sonic2CompetitionObjectDisplay.deferred(context.getObjectManager())+"\n");
        var animation=context.getLevelManager().getAnimatedPatternManager();
        if(animation instanceof Sonic2CompetitionAnimatedArt owner)text.append("animation=").append(owner.capture()).append('\n');
        text.append("camera1=").append(session.camera(0).capture()).append("\ncamera2=").append(session.camera(1).capture()).append('\n');
        for(var page:List.of(registers.pages().first(),registers.pages().second())){
            text.append("page ").append(page.player()).append(" count ").append(page.spriteCount()).append('\n');
            for(var piece:page.entries())text.append(piece).append('\n');
        }
        var empty=new Sonic2CompetitionSpritePages.Pair(Sonic2CompetitionSatBuilder.Page.empty(0),Sonic2CompetitionSatBuilder.Page.empty(1));
        video.registers().restore(new Sonic2CompetitionViewRegisters.State(registers.published(),
                registers.foreground1(),registers.background1(),registers.foreground2(),registers.background2(),
                registers.horizontal(),registers.upperPlane(),registers.nextUpperPlane(),
                registers.firstCamera(),registers.secondCamera(),empty,registers.paletteWords()));
        try {
            PaletteUploadPresentation.withoutDisplayedFade(graphics,()->views.draw(context,session));
            ScreenshotCapture.savePNG(ScreenshotCapture.captureFramebuffer(320,224),OUT.resolve(name+"-planes-only.png"));
        } finally {video.registers().restore(registers);}
        assertEquals(registers,video.registers().capture());
        Files.writeString(OUT.resolve(name+".txt"),text);
        assertEquals(lfc,context.getLevelManager().getFrameCounter());assertEquals(vint,context.getObjectManager().getVblaCounter());
        assertEquals(first,session.player(0).getCentreX()+","+session.player(0).getCentreY());
        assertEquals(second,session.player(1).getCentreX()+","+session.player(1).getCentreY());
    }
}
