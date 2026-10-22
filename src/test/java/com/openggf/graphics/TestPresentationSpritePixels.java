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
    @Test
    void finalCroppedQuadCullMatchesUnculledPixelsAtNativeAndWideEdges() throws Exception {
        assumeTrue(com.openggf.tools.PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var offscreen = com.openggf.tools.PresentationOffscreenTestContext.open(640, 224)) {
            var graphics = new GraphicsManager();
            try {
                glDisable(GL_DITHER);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                var palette = new Palette(); palette.setColor(1, new Palette.Color((byte)255, (byte)100, (byte)40));
                graphics.cachePaletteTexture(palette, 0);
                for (int width : new int[]{320, 640}) {
                    graphics.setViewport(0, 0, width, 224); graphics.setProjectionWidth(width);
                    float[] projection = new Matrix4f().ortho(0, width, 0, 224, -1, 1).get(new float[16]);
                    graphics.setProjectionMatrixBuffer(projection);
                    var source = picture(10, 20, false, false, 1);
                    var tiles = new ArrayList<Tile>();
                    for (int[] point : new int[][]{{width+4,30,width-4,30}, {width-1,60,width-1,60},
                            {-6,90,0,90}, {40,-8,44,0}, {width+40,150,width+44,150}}) {
                        var subject = new Subject("edge-" + tiles.size(), Part.BODY, false, 100, null,
                                new Body(Role.BADNIK, point[2], point[3]));
                        tiles.add(new Tile(Layer.OBJECT, source.tiles().getFirst().patternId(), 0, false, false, false,
                                point[0],point[1],8,8,false,tiles.size()%2==0?15:0,false,1,2,7,subject));
                    }
                    var frame = new Frame(tiles, List.of(), source.patternVersions());
                    var runtime = mock(LivePresentationRuntime.class);
                    when(runtime.trails()).thenReturn(List.of());
                    when(runtime.spritePolicies()).thenReturn(List.of(new TransientMutatorPolicySource.Entry("test", 1,
                            new SpritePresentationPolicy(SpritePresentationPolicy.Mode.ROTATE,
                                    SpritePresentationPolicy.Target.BADNIKS,100,120),60,100,false)));
                    // Unknown projection at emission deliberately exercises the fail-open reference path.
                    byte[] reference = renderWithEmissionProjection(graphics,frame,runtime,width,projection,false);
                    assertArrayEquals(reference, renderWithEmissionProjection(graphics,frame,runtime,width,projection,true));
                    assertTrue(java.util.stream.IntStream.range(0,reference.length/4).anyMatch(i -> (reference[i*4]&255)!=0));
                }
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    private static byte[] renderWithEmissionProjection(GraphicsManager graphics, Frame frame, LivePresentationRuntime runtime,
                                                       int width, float[] projection, boolean cull) {
        glViewport(0,0,width,224); glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT);
        graphics.executeCapturedCommands(() -> {
            graphics.setProjectionMatrixBuffer(cull ? projection : null);
            try { SpritePresentationRenderer.draw(graphics,frame,0,0,l -> true,runtime); }
            finally { graphics.setProjectionMatrixBuffer(projection); }
        },0,0,width,224);
        glFinish(); ByteBuffer pixels = MemoryUtil.memAlloc(width*224*4);
        try { glReadPixels(0,0,width,224,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            byte[] result=new byte[pixels.remaining()];pixels.get(result);return result; }
        finally { MemoryUtil.memFree(pixels); }
    }

    @Test
    void adjacentTransformedOverlappingTilesMatchUnbatchedPixels() throws Exception {
        assumeTrue(com.openggf.tools.PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var offscreen = com.openggf.tools.PresentationOffscreenTestContext.open(320, 224)) {
            var graphics = new GraphicsManager();
            try {
                glDisable(GL_DITHER);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionWidth(320);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                var palette = new Palette();
                palette.setColor(1, new Palette.Color((byte)255, (byte)40, (byte)80));
                palette.setColor(2, new Palette.Color((byte)20, (byte)255, (byte)80));
                palette.setColor(3, new Palette.Color((byte)40, (byte)80, (byte)255));
                palette.setColor(4, new Palette.Color((byte)255, (byte)255, (byte)255));
                graphics.cachePaletteTexture(palette, 0);
                var source = picture(20, 30, false, false, 0);
                var tiles = new ArrayList<Tile>();
                for (int i = 0; i < 23; i++) {
                    var t = source.tiles().getFirst();
                    tiles.add(new Tile(t.layer(), t.patternId(), 0, (i & 1) != 0, (i & 2) != 0, t.priority(),
                            20 + i * 3, 30 + i % 3, 8, 8, false, 5, true, .6f,
                            i % 3, 8 - i % 2, t.subject()));
                }
                var frame = new Frame(tiles, List.of(), source.patternVersions());
                var runtime = mock(LivePresentationRuntime.class);
                when(runtime.trails()).thenReturn(List.of());
                var policy = new TransientMutatorPolicySource.Entry("test", 1,
                        new SpritePresentationPolicy(SpritePresentationPolicy.Mode.ROTATE,
                                SpritePresentationPolicy.Target.BADNIKS, 100, 120), 13, 100, false);
                var iterations = new java.util.concurrent.atomic.AtomicInteger();
                List<TransientMutatorPolicySource.Entry> policies = new AbstractList<>() {
                    @Override public int size() { return 1; }
                    @Override public TransientMutatorPolicySource.Entry get(int index) { return List.of(policy).get(index); }
                    @Override public Iterator<TransientMutatorPolicySource.Entry> iterator() {
                        iterations.incrementAndGet(); return List.of(policy).iterator();
                    }
                };
                when(runtime.spritePolicies()).thenReturn(policies);
                graphics.setBatchingEnabled(false);
                byte[] expected = render(graphics, frame, runtime, 320);
                graphics.setBatchingEnabled(true);
                assertArrayEquals(expected, render(graphics, frame, runtime, 320),
                        "overlapping translucent triangles must preserve native painter order, crop and flips");
                verify(runtime, times(2)).spritePolicies();
                assertEquals(2, iterations.get(), "23 tiles share one actor policy/transform computation per picture");
                assertTrue(java.util.stream.IntStream.range(0, expected.length / 4)
                        .anyMatch(i -> (expected[i * 4] & 255) != 0), "a cleared/blank image cannot certify batching");
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    @Test
    void savedExpansionReuploadsResidentPaletteAndChangesActualFramebufferPixels() throws Exception {
        assumeTrue(com.openggf.tools.PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var offscreen = com.openggf.tools.PresentationOffscreenTestContext.open(320, 224)) {
            GraphicsManager graphics = new GraphicsManager();
            try {
                glDisable(GL_DITHER);
                graphics.init("shaders/shader_the_hedgehog.glsl");
                graphics.setBatchingEnabled(false);
                graphics.setViewport(0, 0, 320, 224);
                graphics.setProjectionWidth(320);
                graphics.setProjectionMatrixBuffer(new Matrix4f().ortho(0, 320, 0, 224, -1, 1).get(new float[16]));
                Palette palette = new Palette();
                palette.setColor(1, new Palette.Color((byte) 149, (byte) 87, (byte) 227));
                graphics.cachePaletteTexture(palette, 0);
                Frame frame = picture(20, 30, false, false, 1);
                assertPicturePixel(graphics, frame, new int[]{149, 87, 227});
                graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.LIMITED);
                assertPicturePixel(graphics, frame, new int[]{139, 81, 212});
                graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.FULL);
                assertPicturePixel(graphics, frame, new int[]{149, 87, 227});
                assertEquals(149, palette.getColor(1).r & 255, "display settings do not mutate creator palette bytes");
                graphics.setPaletteFadePresentation(PaletteFadePresentation.Mode.TO_BLACK, 1, 1);
                graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.LIMITED);
                assertPicturePixel(graphics, frame, new int[]{102, 68, 204});
                graphics.clearPaletteFadePresentation();
                assertPicturePixel(graphics, frame, new int[]{139, 81, 212});
                assertEquals(GL_NO_ERROR, glGetError());
            } finally { graphics.cleanup(); RenderContext.reset(); }
        }
    }

    @Test
    void paletteBackdropClearUsesTheDisplayedProfileAndExpansion() throws Exception {
        assumeTrue(com.openggf.tools.PresentationOffscreenTestContext.requested(), "requires owned surfaceless EGL");
        try (var offscreen = com.openggf.tools.PresentationOffscreenTestContext.open(320, 224)) {
            GraphicsManager graphics = new GraphicsManager();
            glDisable(GL_DITHER);
            glViewport(0, 0, 320, 224);
            Palette.Color white = new Palette.Color((byte) 255, (byte) 255, (byte) 255);
            Palette.Color creator = new Palette.Color((byte) 149, (byte) 87, (byte) 227);
            assertBackdropPixel(graphics, white, new int[]{255, 255, 255});
            graphics.setDisplayColorExpansion(com.openggf.graphics.color.DisplayColorExpansion.LIMITED);
            assertBackdropPixel(graphics, white, new int[]{238, 238, 238});
            assertBackdropPixel(graphics, creator, new int[]{139, 81, 212});
            graphics.setDisplayColorProfile(com.openggf.graphics.color.DisplayColorProfile.MD_ANALOG);
            int[] analog = com.openggf.graphics.color.DisplayColorConverter.toRgbBytes(149, 87, 227,
                    com.openggf.graphics.color.DisplayColorProfile.MD_ANALOG,
                    com.openggf.graphics.color.DisplayColorExpansion.LIMITED);
            assertBackdropPixel(graphics, creator, analog);
            assertEquals(149, creator.r & 255, "display settings do not mutate the backdrop palette entry");
            assertEquals(GL_NO_ERROR, glGetError());
        }
    }

    private static void assertBackdropPixel(GraphicsManager graphics, Palette.Color colour, int[] expected) {
        com.openggf.level.PaletteDisplayColor.glClear(colour, graphics);
        glClear(GL_COLOR_BUFFER_BIT);
        glFinish();
        ByteBuffer pixel = MemoryUtil.memAlloc(4);
        try {
            glReadPixels(160, 112, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
            for (int channel = 0; channel < 3; channel++) assertEquals(expected[channel], pixel.get(channel) & 255);
        } finally { MemoryUtil.memFree(pixel); }
    }

    private static void assertPicturePixel(GraphicsManager graphics, Frame frame, int[] expected) {
        byte[] image = render(graphics, frame, null, 320);
        int offset = ((223 - 33) * 320 + 23) * 4;
        for (int channel = 0; channel < 3; channel++) assertEquals(expected[channel], image[offset + channel] & 255);
        assertEquals(255, image[offset + 3] & 255);
        ByteBuffer uploaded = MemoryUtil.memAlloc(16 * RenderContext.getTotalPaletteLines() * 4);
        try {
            glBindTexture(GL_TEXTURE_2D, graphics.getCombinedPaletteTextureId());
            glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, uploaded);
            for (int channel = 0; channel < 3; channel++) assertEquals(expected[channel], uploaded.get(4 + channel) & 255);
            assertEquals(0, uploaded.get(3) & 255, "palette colour zero remains transparent");
        } finally { MemoryUtil.memFree(uploaded); }
    }

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
                    assertNativeCroppedPixels(nativePixels, width, h, v);
                    for(var mode:List.of(SpritePresentationPolicy.Mode.SCALE,SpritePresentationPolicy.Mode.ROTATE,SpritePresentationPolicy.Mode.SQUASH)) {
                        // Avoid pixel-center crop edges, whose fragment ownership OpenGL leaves implementation-defined.
                        var runtime=mock(LivePresentationRuntime.class);int strength=mode==SpritePresentationPolicy.Mode.SCALE?200:mode==SpritePresentationPolicy.Mode.SQUASH?115:100;
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
    private static void assertNativeCroppedPixels(byte[] pixels, int width, boolean hFlip, boolean vFlip) {
        int[][] colours = {{0, 0, 0}, {255, 0, 0}, {0, 255, 0}, {0, 0, 255}, {255, 255, 255}};
        for (int y = 28; y < 40; y++) for (int x = 18; x < 30; x++) {
            int colour = 0;
            if (x >= 20 && x < 28 && y >= 32 && y < 37) {
                int sourceColumn = hFlip ? 27 - x : x - 20;
                int sourceRow = vFlip ? 37 - y : y - 30;
                colour = 1 + sourceColumn / 4 + 2 * (sourceRow / 4);
            }
            for (int channel = 0; channel < 3; channel++) {
                assertEquals(colours[colour][channel], pixels[((223 - y) * width + x) * 4 + channel] & 255,
                        "native crop flip=" + hFlip + "," + vFlip + " at=" + x + "," + y);
            }
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
