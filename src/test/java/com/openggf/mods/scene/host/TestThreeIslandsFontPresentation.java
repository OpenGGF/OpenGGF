package com.openggf.mods.scene.host;

import com.openggf.mods.code.ExampleModHarness;
import com.openggf.mods.scene.SceneCanvas;
import com.openggf.mods.ui.SceneCanvasTransform;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Pre-migration recorded glyph pixels retain the authored font's case, fallback and local styling. */
class TestThreeIslandsFontPresentation {
    @TempDir static Path work;
    static ExampleModHarness islands;
    static Object font;
    static Method width,draw,shadowed,centered,wrap;
    static final String TEXT="Aa Zz Ij ? @ snowman \u2603";
    @BeforeAll static void build()throws Exception{
        islands=ExampleModHarness.build(Path.of("examples/three-islands"),work.resolve("islands"));
        Class<?> type=islands.loader().loadClass("threeislands.art.Font");
        font=type.getConstructor(byte[].class).newInstance((Object)Files.readAllBytes(Path.of("examples/three-islands/src/main/resources/text/font.txt")));
        width=type.getMethod("width",String.class);draw=type.getMethod("draw",SceneCanvas.class,String.class,int.class,int.class,int.class,int.class);
        shadowed=type.getMethod("shadowed",SceneCanvas.class,String.class,int.class,int.class,int.class,int.class);
        centered=type.getMethod("centered",SceneCanvas.class,String.class,int.class,int.class,int.class);
        wrap=type.getMethod("wrap",String.class,int.class);
    }
    @AfterAll static void close()throws Exception{if(islands!=null)islands.close();}
    @Test void mixedCaseFallbackPixelsAndLocalStylingMatchPreMigration()throws Exception{
        List<String> fingerprints=new ArrayList<>();
        for(int w:TestTitleCardPresentationExamples.WIDTHS){
            RecordingCanvas c=new RecordingCanvas(w,224,null);
            draw.invoke(font,c,TEXT,4,5,0xFF80C020,1);shadowed.invoke(font,c,TEXT,3,25,0xC0A0B0D0,2);
            centered.invoke(font,c,TEXT,w/2,55,0xFFFF8040);
            try(var outer=new SceneCanvasTransform(c,7,-2,0.5f);var inner=new SceneCanvasTransform(outer,-2,5,0.5f)){
                draw.invoke(font,inner,TEXT,4,85,0xC080C020,2);
            }
            MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(TestTitleCardPresentationExamples.pixels(c));
            fingerprints.add(HexFormat.of().formatHex(digest.digest()));
        }
        assertEquals(List.of("56c6e24309525c32e85bb54d2776c4668de012d640ce511ccd4ad38b3fd49586","0df310fab36f290bf7398f43c45814fc64934d3140bae6caafd1f6f0d3c348bd","d915df184a08a0c1a7096a29e723b39aeb998547d0489bcb3bbb3b1ad19bfad3","7da61a9cffa438c0198e64c6409949f0ee7632afa500590b11b4567d0b71ba44"),fingerprints,"recorded font pixels");
    }
    @Test void textMetricsWrappingAndFallbackAreKept()throws Exception{
        assertEquals(width.invoke(font,"?"),width.invoke(font,"\u2603"));
        assertEquals(0,width.invoke(font,""));assertEquals(3,width.invoke(font," "));
        assertEquals(List.of("Aa","Zz","Ij"),wrap.invoke(font," Aa  Zz Ij ",6));
        assertEquals(List.of(TEXT),wrap.invoke(font,TEXT,10000));
    }
}
