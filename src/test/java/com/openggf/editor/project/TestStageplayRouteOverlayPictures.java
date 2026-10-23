package com.openggf.editor.project;

import com.openggf.audio.AudioManager;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.data.RomManager;
import com.openggf.editor.*;
import com.openggf.editor.hud.EditorHudRuntime;
import com.openggf.editor.stageplay.StageplayWorkspace;
import com.openggf.game.GameServices;
import com.openggf.stageplay.*;
import com.openggf.stageplay.editor.*;
import com.openggf.stageplay.route.*;
import com.openggf.tools.HeadlessGameBoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.openggf.stageplay.StageplayDocument.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.opengl.GL11.*;

/** Production worker + authoring frontend evidence; explicit opt-in, offline GL/audio only.
 * Origin: 2026-10-23 Stageplay route overlay. Inputs: S2 ROM and evidence output directory.
 */
@Isolated
class TestStageplayRouteOverlayPictures {
    private static final int WIDTH=1280, HEIGHT=720;
    @Test void captureProposedFailedAndAppliedRoutePlayback() throws Exception {
        assumeTrue(Boolean.getBoolean("openggf.stageplay.hudPictures"), "Explicit HUD picture refresh only");
        assertEquals("surfaceless-egl",System.getProperty("openggf.headless.gl"));
        Path output=Path.of(System.getProperty("openggf.stageplay.routeEvidence","target/stageplay-route-overlay")).toAbsolutePath();
        Files.createDirectories(output);
        var source=scene();
        try (var boot=new HeadlessGameBoot(WIDTH,HEIGHT,320,224,320,224)) {
            boot.setBootToTitle(true); boot.boot(Path.of(System.getProperty("sonic2.rom.path")),0,0);
            GameServices.configuration().setSessionOverride(SonicConfiguration.SONIC_2_ROM,System.getProperty("sonic2.rom.path"));
            try (var session=new StageplayEditorSession(GameServices::module,RomManager.getInstance(),AudioManager.getInstance());
                 var chrome=new EditorHudRuntime(); var modal=new com.openggf.editor.project.EditorProjectMenuRenderer(); var video=new Video(output.resolve("engine-route-overlay.mp4"))) {
                var workspace=new StageplayWorkspace(new StageplayWorkspace.Host() {
                    public StageplayDocument initialDocument(){return source;}
                    public Path initialAssetRoot(){return output;}
                    public StageplayTimeline.Frame resolvePreview(StageplayDocument d,Path root,int frame) throws java.io.IOException {
                        return session.resolvePreview(d,root,frame);
                    }
                    public StageplayTimeline.Frame resolvePreview(StageplayDocument d,Path root,StageplayTimeline.Frame frame) throws java.io.IOException {
                        return session.resolvePreview(d,root,frame);
                    }
                    public java.util.Set<com.openggf.game.CharacterDefinition.MovementAbility> movementAbilities(Actor actor,Path root) throws java.io.IOException { return session.movementAbilities(actor,root); }
                    public void startRoute(StageplayDocument d,Path root,StageplayRouteRequest r) throws java.io.IOException {session.startRoute(d,root,r);}
                    public StageplayRouteJob.Update pollRoute(){return session.pollRoute();}
                    public void requestRouteApply(StageplayDocument d,Path root) throws java.io.IOException {session.requestRouteApply(d,root);}
                    public void cancelRoute(){session.cancelRoute();}
                });
                workspace.activate();workspace.selectActor("hero");workspace.seek(30);
                var level=new LevelEditorController();
                chrome.init(new EditorWindowChrome.Host(){
                    public LevelEditorController controller(){return level;}
                    public void dispatch(EditorInputHandler.Action action){fail("Route authoring cannot dispatch level edits");}
                    public List<EditorCommandPalette.HostCommand> commands(){return List.of();}
                    public StageplayWorkspace stageplay(){return workspace;}
                },1);
                assertTrue(chrome.setActive(true));
                workspace.invoke(StageplayWorkspace.Action.ROUTE_HELPER);
                assertEquals("Walk",workspace.menu().values().get("travelSpeed"));
                for(int i=0;i<120;i++){
                    draw(chrome,session,workspace);
                    glViewport(0,0,WIDTH,HEIGHT);
                    int menuWidth=Math.round(WIDTH*224f/HEIGHT);
                    modal.render(workspace.menu(),new org.joml.Matrix4f().ortho2D(0,menuWidth,0,224).get(new float[16]),menuWidth);
                    video.frame(i==0?output.resolve("engine-motion-settings.png"):null);
                }
                workspace.menu().close();
                plan(workspace,"400","624","1024");
                await(workspace,session,StageplayRouteJob.Phase.READY);
                var success=session.pollRoute().result();
                assertTrue(success.success(),success.message());
                assertTrue(success.commands().stream().noneMatch(c->c.kind()==PuppetCommandKind.JUMP),"The Walk rehearsal must stay grounded");
                assertEquals(1,success.commands().stream().filter(c->c.kind()==PuppetCommandKind.MOVE_TO).count(),"A walk uses one destination");
                Files.writeString(output.resolve("engine-success-result.json"),StageplayRouteProtocol.encodeResult(success));
                StageplayStore.write(output.resolve("engine-source.stageplay"),source);
                assertNotNull(workspace.routeOverlay()); assertFalse(workspace.routeOverlay().applied());
                assertEquals("Route proposed",workspace.routeOverlay().title());
                workspace.menu().close();
                for(int i=0;i<60;i++){draw(chrome,session,workspace);video.frame(i==0?output.resolve("engine-proposed.png"):null);}
                // The film renderer owns no route state. Redrawing its opaque view removes every
                // route pixel even after the frontend has painted the overlay over the same buffer.
                var viewport=chrome.gameViewport(WIDTH,HEIGHT,WIDTH,HEIGHT,source.width(),source.height());
                glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
                session.draw(workspace.previewDocument(),workspace.previewFrame(),output,f->fail(f));
                byte[] clean=read(viewport);
                chrome.render(WIDTH,HEIGHT,WIDTH,HEIGHT);
                byte[] overlaid=read(viewport);
                assertFalse(java.util.Arrays.equals(clean,overlaid),"Frontend must actually paint pixels in the viewport");
                int headerBytes=viewport[2]*Math.min(40,viewport[3])*4;
                assertArrayEquals(java.util.Arrays.copyOfRange(clean,clean.length-headerBytes,clean.length),
                        java.util.Arrays.copyOfRange(overlaid,overlaid.length-headerBytes,overlaid.length),
                        "The top of the film must stay clear of route labels");
                glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
                session.draw(workspace.previewDocument(),workspace.previewFrame(),output,f->fail(f));
                assertArrayEquals(clean,read(viewport),"Film drawing must remain free of authoring overlays");
                workspace.invoke(StageplayWorkspace.Action.ROUTE_HELPER);
                session.requestRouteApply(workspace.controller().document(),output);
                awaitApplied(workspace);
                assertTrue(workspace.routeOverlay().applied());
                assertEquals("Route applied",workspace.routeOverlay().title());workspace.menu().close();
                workspace.invoke(StageplayWorkspace.Action.PLAY);
                for(int i=0;i<300;i++) {
                    workspace.tick(new InputHandler()); draw(chrome,session,workspace);
                    video.frame(i==60?output.resolve("engine-applied-motion.png"):i==299?output.resolve("engine-applied-arrived.png"):null);
                }
                workspace.stop(); workspace.seek(30);
                plan(workspace,"400","300","8");
                await(workspace,session,StageplayRouteJob.Phase.FAILED);
                var failed=session.pollRoute().result();
                assertFalse(failed.success());assertFalse(failed.path().isEmpty(),failed.message());
                assertEquals("Route search limit reached.",workspace.status());
                assertTrue(workspace.routeOverlay().reason().contains("Reachability is still unknown"));
                Files.writeString(output.resolve("engine-failed-result.json"),StageplayRouteProtocol.encodeResult(failed));
                workspace.menu().close();
                for(int i=0;i<60;i++){draw(chrome,session,workspace);video.frame(i==0?output.resolve("engine-failed.png"):null);}
                // A camera draft changes only projection, never the samples.
                var path=workspace.routeOverlay().path();workspace.invoke(StageplayWorkspace.Action.CAMERA_MODE);
                var input=new InputHandler();input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_D,org.lwjgl.glfw.GLFW.GLFW_PRESS);
                for(int i=0;i<20;i++){input.refreshLogicalSnapshot();workspace.tick(input);input.update();draw(chrome,session,workspace);video.frame(null);}
                input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_D,org.lwjgl.glfw.GLFW.GLFW_RELEASE);input.update();
                input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_E,org.lwjgl.glfw.GLFW.GLFW_PRESS);input.refreshLogicalSnapshot();workspace.tick(input);input.update();
                input.handleKeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_E,org.lwjgl.glfw.GLFW.GLFW_RELEASE);input.update();
                for(int i=0;i<40;i++){
                    if(i<20)workspace.moveCamera(-16,-2,1);
                    draw(chrome,session,workspace);video.frame(i==25?output.resolve("engine-pan-zoom.png"):null);
                }
                var terminal=path.getLast();var view=workspace.previewFrame().camera();
                float markerX=StageplayRouteOverlay.screenX(terminal.x(),view,source.width(),0,source.width());
                float markerY=StageplayRouteOverlay.screenY(terminal.y(),view,source.height(),0,source.height());
                assertTrue(markerX>0 && markerX<source.width() && markerY>60 && markerY<source.height(),"Keep the failed endpoint in view after zoom/pan");
                assertEquals(path,workspace.routeOverlay().path());
                assertEquals(GL_NO_ERROR,glGetError());
                System.out.println("ROUTE_EVIDENCE success samples="+success.path().size()+" commands="+success.commands().size()
                        +" failure="+failed.status()+" failureSamples="+failed.path().size()+" frames=600");
            }
        }
    }
    private static void plan(StageplayWorkspace workspace,String x,String y,String nodes){
        workspace.invoke(StageplayWorkspace.Action.ROUTE_HELPER);
        workspace.menu().setValue("x",x);workspace.menu().setValue("y",y);workspace.menu().setValue("frames","300");
        workspace.menu().setValue("nodes",nodes);workspace.menu().submit();assertNull(workspace.menu().error());
    }
    private static void await(StageplayWorkspace w,StageplayEditorSession s,StageplayRouteJob.Phase phase)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(120);
        while(System.nanoTime()<deadline){w.tick(new InputHandler());var update=s.pollRoute();
            if(update.phase()==phase)return;
            assertNotEquals(StageplayRouteJob.Phase.FAILED,update.phase(),update.message());Thread.sleep(25);
        } fail("Route worker did not complete");
    }
    private static void awaitApplied(StageplayWorkspace w)throws Exception{
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
        while(System.nanoTime()<deadline){w.tick(new InputHandler());if(w.routeOverlay()!=null&&w.routeOverlay().applied())return;Thread.sleep(25);}
        fail("Fresh route did not apply: "+w.status());
    }
    private static void draw(EditorHudRuntime chrome,StageplayEditorSession session,StageplayWorkspace w){
        chrome.update(new InputHandler());
        int[] viewport=chrome.gameViewport(WIDTH,HEIGHT,WIDTH,HEIGHT,w.controller().document().width(),w.controller().document().height());
        glViewport(0,0,WIDTH,HEIGHT);glDisable(GL_SCISSOR_TEST);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
        glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
        session.draw(w.previewDocument(),w.previewFrame(),w.assetRoot(),f->fail(f));
        chrome.render(WIDTH,HEIGHT,WIDTH,HEIGHT);
    }
    private static StageplayDocument scene(){
        var set=new SetSource("s2",0,0,"","");
        return StageplayDocument.empty("s2",0,0,0,480,480,270).withDurationFrames(330).withName("Route rehearsal")
                .withCamera(List.of(new CameraKey(0,0,480,1,Interpolation.HOLD),new CameraKey(30,0,480,1,Interpolation.HOLD)))
                .withSceneChanges(List.of(new SceneChange("route-shot",30,set,Transition.CUT,0)))
                .withWorldCommands(List.of(new WorldCommand("clear0",0,WorldCommandKind.CLEAR_OBJECTS,"",ObjectTarget.none()),
                        new WorldCommand("clear30",30,WorldCommandKind.CLEAR_OBJECTS,"",ObjectTarget.none())))
                .withActors(List.of(new Actor("hero","Sonic","s2","sonic",List.of(
                        new ActorKey(0,100,656,0,-1,false,true,1,Interpolation.HOLD),
                        new ActorKey(30,100,656,0,-1,false,true,1,Interpolation.HOLD)))))
                .withPuppets(List.of(new PuppetTrack("hero",true,0,List.of())));
    }
    private static byte[] read(int[] v){
        ByteBuffer buffer=MemoryUtil.memAlloc(v[2]*v[3]*4);
        try{glFinish();glReadPixels(v[0],v[1],v[2],v[3],GL_RGBA,GL_UNSIGNED_BYTE,buffer);byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);return bytes;}
        finally{MemoryUtil.memFree(buffer);}
    }
    private static final class Video implements AutoCloseable {
        final Process process;final java.io.OutputStream pipe;
        Video(Path file)throws Exception{
            process=new ProcessBuilder("ffmpeg","-v","error","-y","-f","rawvideo","-pix_fmt","rgba","-s",WIDTH+"x"+HEIGHT,"-r","60",
                    "-i","pipe:0","-vf","vflip","-c:v","libx264","-crf","18","-pix_fmt","yuv420p",file.toString()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
            pipe=new java.io.BufferedOutputStream(process.getOutputStream());
        }
        void frame(Path still)throws Exception{
            byte[] rgba=read(new int[]{0,0,WIDTH,HEIGHT});pipe.write(rgba);
            if(still!=null){var image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int i=((HEIGHT-1-y)*WIDTH+x)*4;
                    image.setRGB(x,y,(rgba[i]&255)<<16|(rgba[i+1]&255)<<8|rgba[i+2]&255);}
                assertTrue(ImageIO.write(image,"png",still.toFile()));}
        }
        public void close()throws Exception{pipe.close();assertTrue(process.waitFor(30,TimeUnit.SECONDS));assertEquals(0,process.exitValue());}
    }
}
