package com.openggf.graphics;

import com.openggf.game.mutators.*;
import com.openggf.graphics.SpritePresentation.*;
import com.openggf.level.Pattern;
import com.openggf.level.Palette;
import com.openggf.level.render.SpritePresentationRenderer;
import com.openggf.mods.mutators.SpritePresentationPolicy;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import java.nio.ByteBuffer;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/** Actual source-pixel replay through the private quad bridge, using an owned EGL/null display. */
@Isolated
class TestPresentationSpritePixels {
    @Test void admittedRowsFlipsWholeBodyTransformsAndDistinctFrozenTrailVersionsRenderAt320And640() throws Exception {
        long window=0;boolean initialized=false;GraphicsManager graphics=null;
        com.openggf.tools.PresentationOffscreenTestContext offscreen=null;
        try {
            if(com.openggf.tools.PresentationOffscreenTestContext.requested()) {
                offscreen=com.openggf.tools.PresentationOffscreenTestContext.open(640,224);
            } else {
            glfwInitHint(GLFW_PLATFORM,GLFW_PLATFORM_NULL);initialized=glfwInit();assumeTrue(initialized,"GLFW null platform unavailable");
            glfwDefaultWindowHints();glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,4);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,1);
            glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);glfwWindowHint(GLFW_CONTEXT_CREATION_API,GLFW_EGL_CONTEXT_API);
            window=glfwCreateWindow(640,224,"owned sprite presentation pixels",0,0);assumeTrue(window!=0,"Offscreen OpenGL4.1 unavailable");
            glfwMakeContextCurrent(window);GL.createCapabilities();
            }
            glDisable(GL_DITHER);
            graphics=new GraphicsManager();graphics.init("shaders/shader_the_hedgehog.glsl");graphics.setBatchingEnabled(false);
            var palette=new Palette();palette.colors[1]=new Palette.Color((byte)255,(byte)0,(byte)0);
            palette.colors[2]=new Palette.Color((byte)0,(byte)255,(byte)0);palette.colors[3]=new Palette.Color((byte)0,(byte)0,(byte)255);
            palette.colors[4]=new Palette.Color((byte)255,(byte)255,(byte)255);
            for(int line=0;line<4;line++)graphics.cachePaletteTexture(palette,line);
            for(int width:new int[]{320,640}) {
                graphics.setViewport(0,0,width,224);graphics.setProjectionWidth(width);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0,width,0,224,-1,1).get(new float[16]));
                for(boolean h:new boolean[]{false,true})for(boolean v:new boolean[]{false,true}) {
                    Frame source=picture(20,30,h,v,0);
                    byte[] nativePixels=render(graphics,source,null,width);
                    for(var mode:List.of(SpritePresentationPolicy.Mode.SCALE,SpritePresentationPolicy.Mode.ROTATE,SpritePresentationPolicy.Mode.SQUASH)) {
                        var runtime=mock(LivePresentationRuntime.class);int strength=mode==SpritePresentationPolicy.Mode.SCALE?200:mode==SpritePresentationPolicy.Mode.SQUASH?120:100;
                        var entries=List.of(new TransientMutatorPolicySource.Entry("test",1,new SpritePresentationPolicy(mode,SpritePresentationPolicy.Target.BADNIKS,strength,120),30,100,false));
                        when(runtime.spritePolicies()).thenReturn(entries);when(runtime.trails()).thenReturn(List.of());
                        byte[] projected=render(graphics,source,runtime,width);
                        var transform=SpritePictureTransform.forTile(source.tiles().getFirst(),0,0,entries);
                        double determinant=transform.xx()*transform.yy()-transform.xy()*transform.yx();
                        for(int y=18;y<48;y++)for(int x=8;x<44;x++) {
                            double px=x+.5-transform.tx(),py=y+.5-transform.ty();
                            int sx=(int)Math.floor((transform.yy()*px-transform.xy()*py)/determinant);
                            int sy=(int)Math.floor((-transform.yx()*px+transform.xx()*py)/determinant);
                            for(int channel=0;channel<3;channel++) {
                                int expected=sx>=0&&sx<width&&sy>=0&&sy<224?nativePixels[((223-sy)*width+sx)*4+channel]&255:0;
                                assertEquals(expected,projected[((223-y)*width+x)*4+channel]&255,mode+" flip="+h+","+v+" at="+x+","+y);
                            }
                        }
                    }
                    var disco=mock(LivePresentationRuntime.class);
                    when(disco.trails()).thenReturn(List.of());when(disco.spritePolicies()).thenReturn(List.of(new TransientMutatorPolicySource.Entry("test",2,
                            new SpritePresentationPolicy(SpritePresentationPolicy.Mode.RAINBOW,SpritePresentationPolicy.Target.BADNIKS,100,120),30,100,false)));
                    assertFalse(Arrays.equals(nativePixels,render(graphics,source,disco,width)));
                    assertArrayEquals(nativePixels,render(graphics,source,null,width),"disco colour uniforms must not leak into stock draws");
                }
                Object owner=new Object();var bank=PresentationGraphicsAccess.acquire(graphics,owner);
                var runtime=mock(LivePresentationRuntime.class);when(runtime.trailBank(graphics)).thenReturn(PresentationGraphicsAccess.base(bank,graphics,owner));
                when(runtime.spritePolicies()).thenReturn(List.of());
                when(runtime.trails()).thenReturn(List.of(new LivePresentationRuntime.Pose(picture(20,30,false,false,1),0,0),
                        new LivePresentationRuntime.Pose(picture(40,30,false,false,3),0,0)));
                byte[] image=render(graphics,picture(60,30,false,false,2),runtime,width);
                int red=((223-33)*width+23)*4,blue=((223-33)*width+43)*4,green=((223-33)*width+63)*4;
                assertTrue((image[red]&255)>20);assertEquals(0,image[red+2]&255,"old red pose must not sample newest DPLC bytes");
                assertTrue((image[blue+2]&255)>40);assertEquals(0,image[blue]&255);
                assertEquals(255,image[green+1]&255);assertEquals(0,image[green]&255);
                var crowdRuntime=mock(LivePresentationRuntime.class);
                when(crowdRuntime.trailBank(graphics)).thenReturn(PresentationGraphicsAccess.base(bank,graphics,owner));
                when(crowdRuntime.spritePolicies()).thenReturn(List.of());
                var crowd=new ArrayList<LivePresentationRuntime.Pose>();
                for(int echo=0;echo<24;echo++)crowd.add(new LivePresentationRuntime.Pose(
                        picture(12+(echo%12)*24,70+(echo/12)*32,false,false,echo%2==0?1:3),0,0));
                when(crowdRuntime.trails()).thenReturn(List.copyOf(crowd));
                byte[] crowded=render(graphics,picture(300,150,false,false,2),crowdRuntime,width);
                for(int echo=0;echo<24;echo++) {
                    int at=((223-(73+(echo/12)*32))*width+15+(echo%12)*24)*4;
                    int colour=echo%2==0?0:2;
                    assertTrue((crowded[at+colour]&255)>=160,"every crowd member must remain strongly visible: "+echo);
                    assertEquals(0,crowded[at+(echo%2==0?2:0)]&255,"each historical DPLC generation retains its own pixels");
                }
                Frame visible=picture(60,30,false,false,2);Tile tile=visible.tiles().getFirst();
                var hidden=new Tile(tile.layer(),tile.patternId(),tile.palette(),tile.hFlip(),tile.vFlip(),tile.priority(),
                        tile.x(),tile.y(),tile.width(),tile.height(),tile.priorityShader(),tile.occlusionMask(),tile.ghost(),tile.ghostAlpha(),
                        tile.rowStart(),tile.rowEnd(),new Subject(tile.subject().id(),tile.subject().part(),true,100,null,tile.subject().body()));
                byte[] stealth=render(graphics,new Frame(List.of(hidden),List.of(),visible.patternVersions()),runtime,width);
                for(int pixel:new int[]{red,blue,green})for(int channel=0;channel<3;channel++)
                    assertEquals(0,stealth[pixel+channel]&255,"stealth must suppress current artwork and every retained pose");
                for(var part:List.of(Part.APPENDAGE,Part.ATTACHED_EFFECT)) {
                    var sibling=new Tile(tile.layer(),tile.patternId(),tile.palette(),tile.hFlip(),tile.vFlip(),tile.priority(),
                            80,tile.y(),tile.width(),tile.height(),tile.priorityShader(),tile.occlusionMask(),tile.ghost(),tile.ghostAlpha(),
                            tile.rowStart(),tile.rowEnd(),new Subject(tile.subject().id(),part,false,100,null,tile.subject().body()));
                    byte[] partial=render(graphics,new Frame(List.of(hidden,sibling),List.of(),visible.patternVersions()),runtime,width);
                    for(int pixel:new int[]{red,blue,green})for(int channel=0;channel<3;channel++)
                        assertEquals(0,partial[pixel+channel]&255,"visible "+part+" cannot reveal older hidden body poses");
                    assertEquals(255,partial[((223-33)*width+83)*4+1]&255,"visible sibling still draws natively");
                }
                PresentationGraphicsAccess.release(bank,owner);assertEquals(GL_NO_ERROR,glGetError());
                var priority=graphics.getTilePriorityFBO(width,224);assertNotNull(priority);priority.begin();
                glClearColor(1,0,0,1);glClear(GL_COLOR_BUFFER_BIT);priority.end();
                var scaled=mock(LivePresentationRuntime.class);when(scaled.trails()).thenReturn(List.of());
                when(scaled.spritePolicies()).thenReturn(List.of(new TransientMutatorPolicySource.Entry("test",9,
                        new SpritePresentationPolicy(SpritePresentationPolicy.Mode.SCALE,SpritePresentationPolicy.Target.BADNIKS,200,120),0,100,false)));
                Frame art=picture(20,30,false,false,0);Tile source=art.tiles().getFirst();
                var low=new Tile(source.layer(),source.patternId(),source.palette(),source.hFlip(),source.vFlip(),false,
                        source.x(),source.y(),source.width(),source.height(),true,5,false,1,source.rowStart(),source.rowEnd(),source.subject());
                byte[] occluded=render(graphics,new Frame(List.of(low),List.of(),art.patternVersions()),scaled,width);
                for(int channel=0;channel<3;channel++)assertEquals(0,occluded[((223-33)*width+23)*4+channel]&255,
                        "queued transformed low-priority artwork must use the captured priority shader");
                var high=new Tile(low.layer(),low.patternId(),low.palette(),low.hFlip(),low.vFlip(),true,low.x(),low.y(),low.width(),low.height(),
                        true,low.occlusionMask(),false,1,low.rowStart(),low.rowEnd(),low.subject());
                byte[] bypass=render(graphics,new Frame(List.of(high),List.of(),art.patternVersions()),scaled,width);
                int at=((223-33)*width+23)*4;assertTrue((bypass[at]&255)+(bypass[at+1]&255)+(bypass[at+2]&255)>0,
                        "native high-priority art word bypasses terrain mask");
                assertFalse(graphics.isUseSpritePriorityShader(),"picture submission must restore the producer flag");
            }
        } finally {
            if(graphics!=null)graphics.cleanup();
            if(offscreen!=null)offscreen.close();
            if(window!=0){GL.setCapabilities(null);glfwDestroyWindow(window);}if(initialized)glfwTerminate();glfwInitHint(GLFW_PLATFORM,GLFW_ANY_PLATFORM);
        }
    }
    private static Frame picture(int x,int y,boolean h,boolean v,int uniform) {
        var pattern=new Pattern();for(int row=0;row<8;row++)for(int col=0;col<8;col++)pattern.setPixel(col,row,(byte)(uniform==0?1+col/4+2*(row/4):uniform));
        var subject=new Subject("native-actor",Part.BODY,false,100,null,new Body(uniform==0?Role.BADNIK:Role.LEADER,x+4,y+4));
        var tile=new Tile(Layer.PLAYER,PatternAtlasRange.OBJECTS.base(),0,h,v,true,x,y,8,8,false,5,false,1,2,7,subject);
        return new Frame(List.of(tile),List.of(),Map.of(tile.patternId(),SpritePresentationRenderer.version(pattern)));
    }
    private static byte[] render(GraphicsManager graphics,Frame frame,LivePresentationRuntime runtime,int width) {
        glViewport(0,0,width,224);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
        graphics.executeCapturedCommands(()->SpritePresentationRenderer.draw(graphics,frame,0,0,l->true,runtime),0,0,width,224);
        glFinish();ByteBuffer pixels=MemoryUtil.memAlloc(width*224*4);
        try {glReadPixels(0,0,width,224,GL_RGBA,GL_UNSIGNED_BYTE,pixels);byte[] result=new byte[pixels.remaining()];pixels.get(result);return result;}
        finally {MemoryUtil.memFree(pixels);}
    }
}
