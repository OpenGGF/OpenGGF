package com.openggf.mods.code;

import com.openggf.mods.scene.SceneRomArt;
import com.openggf.mods.scene.host.SceneRomArtFactory;
import com.openggf.mods.ui.PixelCanvas;
import com.openggf.tests.rules.*;
import com.openggf.tests.TestEnvironment;
import java.nio.file.*;
import java.net.URLClassLoader;
import java.util.*;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import static org.junit.jupiter.api.Assertions.*;

/** Actual locked-on ROM pixels, through the public art recipes and injected CPU canvas. No display. */
@Isolated @RequiresRom(SonicGame.SONIC_3K)
class TestSonic3kChaosModeExampleArt {
    @TempDir Path temp;
    @AfterEach void cleanup() {TestEnvironment.resetAll();}
    @Test void allSidegameMotifsDecodeAndBoardsDrawNativePixels() throws Exception {
        Path classes=Files.createDirectory(temp.resolve("classes"));
        var args=new ArrayList<>(List.of("--release","25","-classpath",System.getProperty("java.class.path"),"-d",classes.toString()));
        for(String source:List.of("ChaosArt","ChaosSidegame"))args.add("examples/sonic3k-chaos-mode/src/main/java/chaos/"+source+".java");
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new)));
        try(var loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},getClass().getClassLoader())) {
            var rom=SceneRomArtFactory.forModule(new com.openggf.game.sonic3k.Sonic3kGameModule(),com.openggf.game.GameServices.rom().getRom());
            var type=loader.loadClass("chaos.ChaosArt");var art=type.getConstructor(SceneRomArt.class).newInstance(rom);
            var motif=loader.loadClass("chaos.ChaosArt$Motif");
            for(Object value:motif.getEnumConstants()) {
                var canvas=new ImageCanvas(64,64);
                type.getMethod("draw",PixelCanvas.class,motif,int.class,int.class,int.class,int.class).invoke(art,canvas,value,8,8,48,48);
                assertTrue(Arrays.stream(canvas.pixels).anyMatch(p->(p>>>24)!=0),"Missing actual ROM pixels for "+value);
                assertTrue(canvas.draws>0 && canvas.draws<4096,"Motif drawing escaped bounded run emission");
            }
            var game=loader.loadClass("chaos.ChaosSidegame");var kind=loader.loadClass("chaos.ChaosSidegame$Kind");
            var input=loader.loadClass("chaos.ChaosSidegame$Input");
            var neutral=input.getConstructor(boolean.class,boolean.class,boolean.class,boolean.class,int.class,boolean.class)
                    .newInstance(false,false,false,false,0,false);
            for(Object value:kind.getEnumConstants()) {
                Object instance=game.getConstructor(kind,long.class).newInstance(value,17L);
                var canvas=new ImageCanvas(320,224);
                game.getMethod("draw",PixelCanvas.class,int.class,int.class,type).invoke(instance,canvas,62,32,art);
                assertTrue(canvas.draws>100,"Sidegame omitted its rendered board: "+value);
                if(value.toString().equals("SNAKE")) {
                    int[] before=canvas.pixels.clone();
                    for(int n=0;n<8;n++)game.getMethod("step",input).invoke(instance,neutral);
                    canvas=new ImageCanvas(320,224);
                    game.getMethod("draw",PixelCanvas.class,int.class,int.class,type).invoke(instance,canvas,62,32,art);
                    assertFalse(Arrays.equals(before,canvas.pixels),"Native-art board did not show actual movement");
                }
            }
        }
    }
    private static final class ImageCanvas implements PixelCanvas {
        final int width,height;final int[] pixels;int draws;
        ImageCanvas(int width,int height) {this.width=width;this.height=height;pixels=new int[width*height];}
        @Override public int width() {return width;}
        @Override public int height() {return height;}
        @Override public void fill(int x,int y,int w,int h,int argb) {
            draws++;assertTrue(x>=0 && y>=0 && (long)x+w<=width && (long)y+h<=height,"UI draw escaped viewport");
            for(int row=y;row<y+h;row++)Arrays.fill(pixels,row*width+x,row*width+x+w,argb);
        }
    }
}
