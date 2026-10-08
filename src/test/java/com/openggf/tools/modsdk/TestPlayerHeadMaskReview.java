package com.openggf.tools.modsdk;

import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.io.PngCodec;
import com.openggf.tests.FullReset;
import com.openggf.tests.SingletonResetExtension;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SingletonResetExtension.class) @FullReset @Isolated
class TestPlayerHeadMaskReview {
    @TempDir Path temp;
    @Test @RequiresRom(SonicGame.SONIC_1) void sonic1ProductionContactSheetsAndFeet() throws Exception {
        review(new com.openggf.game.sonic1.Sonic1(TestEnvironment.currentRom()),"s1");
    }
    @Test @RequiresRom(SonicGame.SONIC_2) void sonic2ProductionContactSheets() throws Exception {
        review(new com.openggf.game.sonic2.Sonic2(TestEnvironment.currentRom()),"s2");
    }
    @Test @RequiresRom(SonicGame.SONIC_3K) void sonic3kProductionContactSheets() throws Exception {
        review(new com.openggf.game.sonic3k.Sonic3k(TestEnvironment.currentRom()),"s3k");
    }
    private void review(PlayerSpriteArtProvider provider,String game) throws Exception {
        GraphicsManager.getInstance().initHeadless();
        var art=provider.loadPlayerSpriteArt("sonic");var nativePalette=provider.loadCharacterPalette("sonic");
        int[] palette=new int[64];
        for(int i=0;i<16;i++) {var c=nativePalette.getColor(i);palette[i]=0xFF000000|(c.r&255)<<16|(c.g&255)<<8|c.b&255;}
        Path output=System.getProperty("head.review.dir")==null?temp:Path.of(System.getProperty("head.review.dir"));
        Files.createDirectories(output);
        var log=new ByteArrayOutputStream();
        for(int first=0;first<art.mappingFrames().size();first+=16) {
            Path png=output.resolve("production-"+game+"-"+String.format("%02X",first)+".png");
            PlayerHeadMaskReview.write(art,palette,png,first,16,150,new PrintStream(log));
            // Validate every stock/enlarged pixel at the maximum scale too; the authoring tool rejects cropping.
            PlayerHeadMaskReview.write(art,palette,output.resolve("production-max-"+game+"-"+String.format("%02X",first)+".png"),first,16,200,new PrintStream(new ByteArrayOutputStream()));
            var image=PngCodec.decode(png);
            int cell=PlayerHeadMaskReview.CELL_SIZE, origin=cell/2, block=4*cell;
            assertTrue(image.getWidth()<=4*block);assertTrue(image.getHeight()<=4*(cell+12));
            assertTrue(java.util.Arrays.stream(image.pixels()).distinct().count()>8,"native coloured panels must be nonblank");
            if(game.equals("s1")&&first==0) {
                // Frame1 standing sole at unflipped mapping (0,18), with a 12px row label.
                // Frame1 is the second block on the first row.
                int shoe=image.getRGB(block+origin,12+origin+18);
                assertTrue((shoe>>16&255)>(shoe>>8&255)&&(shoe>>16&255)>(shoe&255),"native sole is red");
                assertEquals(shoe,image.getRGB(block+2*cell+origin,12+origin+18),"body-only panel retains native feet exactly");
                assertEquals(0xFF303040,image.getRGB(block+cell+origin,12+origin+18),"enlarged head cannot contain a shoe pixel");
            }
        }
        assertTrue(log.toString().contains("BALL_STOCK"));assertTrue(log.toString().contains("UNSUPPORTED"));
        if(System.getProperty("head.review.dir")!=null) Files.writeString(output.resolve("production-"+game+"-inventory.csv"),log.toString());
        assertThrows(IllegalArgumentException.class,()->PlayerHeadMaskReview.write(art,palette,temp.resolve("bad.png"),0,1,100,System.out));
    }
}
