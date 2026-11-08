package com.openggf.game.sonic2.competition.view;

import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.game.sonic2.slotmachine.CNZSlotMachineManager;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.*;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

@RequiresRom(SonicGame.SONIC_2)
class TestSonic2CompetitionArtSourcesRestore {
    @Test void destructiveRestoreKeepsHudWritesPendingFlowersFlipsAndLiveReelPicturesThenReplaysThirtyPasses() throws Exception {
        var rom=TestEnvironment.currentRom();
        for(int zone:new int[]{0,12}) {
            var art=new Sonic2CompetitionArtResidency(rom,new Sonic2PlcService(rom));
            var manager=new CNZSlotMachineManager();
            var pictures=zone==12?new Sonic2CompetitionSlotPictures(RomByteReader.fromRom(rom),art,manager::snapshot):null;
            var animated=new Sonic2CompetitionAnimatedArt(rom,zone,art,pictures);
            var hud=new Sonic2CompetitionHudArt(rom,art);hud.initialize(3,3);
            var registry=new RewindRegistry();registry.register(art);registry.register(hud);registry.register(animated);
            if(zone==12)registry.register(manager.rewindAdapter());
            int savedPass=0;
            do {
                assertTrue(savedPass<512,"ROM scripts must produce a pending publication boundary");
                art.serviceDmaQueueVBlank();manager.update(savedPass++);animated.update();
            } while(savedPass<77 || art.capture().pending().isEmpty());
            hud.update(4,12,false);
            assertFalse(art.capture().pending().isEmpty(),"save before VInt consumes the actual animation queue");
            var saved=registry.capture();var expectedArt=art.capture();var expectedHud=hud.capture();
            var expectedAnimation=animated.capture();var expectedPixels=pixels(art);
            for(int pass=savedPass;pass<savedPass+30;pass++) {
                art.serviceDmaQueueVBlank();manager.update(pass);animated.update();hud.update(4+(pass/7)%2,12,false);
            }
            var forwardArt=art.capture();var forwardHud=hud.capture();var forwardAnimation=animated.capture();
            var forwardPixels=pixels(art);
            assertFalse(Arrays.equals(expectedPixels,forwardPixels),"the continuation changes resident art");
            var damaged=new byte[2048*32];Arrays.fill(damaged,(byte)0xFF);art.writePatterns(0,damaged);
            hud.update(9,9,false);for(int i=0;i<30;i++){manager.update(200+i);animated.update();}
            assertNotEquals(expectedArt,art.capture());
            registry.restore(saved);
            assertEquals(expectedArt,art.capture(),"pending queue and physical residency restore together");
            assertEquals(expectedHud,hud.capture());assertEquals(expectedAnimation,animated.capture());
            assertArrayEquals(expectedPixels,pixels(art),"derived field patterns rebuild immediately");
            assertArrayEquals(expectedPixels,pixels(art),"repeated observation advances no owner");
            for(int pass=savedPass;pass<savedPass+30;pass++) {
                art.serviceDmaQueueVBlank();manager.update(pass);animated.update();hud.update(4+(pass/7)%2,12,false);
            }
            assertEquals(forwardArt,art.capture());assertEquals(forwardHud,hud.capture());
            assertEquals(forwardAnimation,animated.capture());assertArrayEquals(forwardPixels,pixels(art));
        }
    }
    private static int[] pixels(Sonic2CompetitionArtResidency art) {
        var patterns=art.fields().live();var result=new int[patterns.length*64];
        for(int tile=0;tile<patterns.length;tile++)for(int y=0;y<8;y++)for(int x=0;x<8;x++)
            result[tile*64+y*8+x]=patterns[tile].getPixel(x,y);
        return result;
    }
}
