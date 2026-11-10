package com.openggf.game.soniccd.resources;

import org.junit.jupiter.api.Test;
import java.util.List;
import static com.openggf.game.soniccd.resources.CdR4ArtServiceMetadata.*;
import static org.junit.jupiter.api.Assertions.*;

class TestCdR4ArtServiceMetadata {
    @Test void presentWindowHasThreeOrdered512ByteWritesToSharedStaging(){var out=stageAnimation(Strategy.ACT1_PRESENT_WINDOW,0,0,0x27F,0x15F);assertTrue(out.loadPowerupArt());assertEquals(List.of(new AnimationWindow(0,0x4500,512),new AnimationWindow(1,0x4700,512),new AnimationWindow(2,0x4900,512)),out.orderedScripts());}
    @Test void destinationsMatchLiteralNativeOperandsNotTheCatalogueTranscription(){
        // N r4/stage_gfx_animate_1a.asm:21-23,34-36,47-49: literal commands.
        // Invert the source's semantic destination encoding in common/gfx_queue.asm:183-189.
        int[] commands={0x45000081,0x47000081,0x49000081};
        var windows=stageAnimation(Strategy.ACT1_PRESENT_WINDOW,0,0,0,0).orderedScripts();
        for(int i=0;i<3;i++){
            int destination=windows.get(i).destination();
            int encoded=(((destination&0x3FFF)|0x4000)<<16)|(destination>>>14)|0x80;
            assertEquals(commands[i],encoded);
            assertNotEquals(0x8500+0x200*i,destination,"Catalogue destination was a transcription error");
        }
    }
    @Test void unsignedCameraEdgesKeepPowerupCallButNoStageScripts(){for(int[] xy:new int[][]{{0x280,0},{0,0x160},{0xFFFF,0},{0,0xFFFF}}){var p=stageAnimation(Strategy.ACT1_PRESENT_WINDOW,0,0,xy[0],xy[1]);assertTrue(p.loadPowerupArt());assertTrue(p.orderedScripts().isEmpty());}}
    @Test void presentPausedReturnsBeforePowerupButOtherNineNativeJumpsDoNot(){assertFalse(stageAnimation(Strategy.ACT1_PRESENT_WINDOW,0,128,0,0).loadPowerupArt());var p=stageAnimation(Strategy.POWERUP_ONLY,0,128,0,0);assertTrue(p.loadPowerupArt());assertTrue(p.orderedScripts().isEmpty());}
    @Test void wholeWordTimeStopHasNoArtServiceForEitherStrategy(){for(var s:Strategy.values())for(int stop:new int[]{1,0x100,0x8000,0xFFFF}){var p=stageAnimation(s,stop,0,0,0);assertFalse(p.loadPowerupArt());assertTrue(p.orderedScripts().isEmpty());}}
}
