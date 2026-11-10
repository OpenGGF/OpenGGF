package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static com.openggf.game.soniccd.CdR4WaterPresentation.*;
import static com.openggf.game.soniccd.CdR4StageServicePlan.Action.*;
import static org.junit.jupiter.api.Assertions.*;

/** Component projection/order controls only; no actual service/capture/World admission. */
class TestCdR4WaterPresentation {
    @Test void normalFullAndSplitSelectOriginalAliasesForBothTerrainAndSprites(){
        var normal=fill(128,0x22);var water=fill(128,0x44);
        var dry=palettes(normal,water,new WaterProjection(900,0,0xDF),true,Screen.LEVEL);for(int y=0;y<224;y++)assertSame(normal,dry.atLine(y));
        var full=palettes(normal,water,new WaterProjection(0,255,0xDF),true,Screen.LEVEL);for(int y=0;y<224;y++)assertSame(water,full.atLine(y));
        var split=palettes(normal,water,new WaterProjection(50,0,50),true,Screen.LEVEL);for(int y=0;y<224;y++)assertSame(y<=50?normal:water,split.atLine(y));assertTrue(split.planesVisible());assertTrue(split.spritesVisible());
    }
    @Test void dirtySplitBytesAreNeitherClampedNorInterpretedAsFullWater(){var n=fill(128,0);var w=fill(128,1);for(int v:new int[]{224,239,255}){var out=palettes(n,w,new WaterProjection(0,0,v),true,Screen.LEVEL);assertEquals(v+1,out.switchAfterLines());assertSame(n,out.atLine(223));}var out=palettes(n,w,new WaterProjection(0,0,0),true,Screen.LEVEL);assertSame(n,out.atLine(0));assertSame(w,out.atLine(1));}
    @Test void backdropAndVisibilityAreSeparateFromPaletteValuesAndUnknownArt(){var n=fill(128,0);var w=fill(128,0);var level=palettes(n,w,new WaterProjection(0,0,223),false,Screen.LEVEL);assertEquals(32,level.backdropIndex());assertFalse(level.planesVisible());assertFalse(level.spritesVisible());assertSame(n,level.atLine(0));assertEquals(0,palettes(n,w,new WaterProjection(0,0,223),true,Screen.WARP).backdropIndex());}
    @Test void paletteRestoreRequiresSameAliasesEvenWithEqualBytes(){var n=fill(128,0);var w=fill(128,0);var water=new WaterProjection(0,0,96);var saved=palettes(n,w,water,true,Screen.LEVEL);requirePaletteAliases(saved,n,w,water);assertThrows(IllegalArgumentException.class,()->requirePaletteAliases(saved,fill(128,0),w,water));assertThrows(IllegalArgumentException.class,()->requirePaletteAliases(saved,n,fill(128,0),water));assertThrows(IllegalArgumentException.class,()->requirePaletteAliases(saved,n,w,new WaterProjection(0,0,95)));}
    @Test void hscrollSharesPrefixAndCarriedPackingWithNativeSignedBitsAndOldTail(){
        int[] words=new int[256];Arrays.fill(words,0x8000FFFF);var previous=carriedHscroll(words);words[0]=0;assertEquals(0x8000FFFFL,previous.unsignedLong(0));var live=fill(1024,0x45);var out=selectHscroll(previous,live);assertArrayEquals(live.slice(0,0x380).copy(),out.slice(0,0x380).copy());assertArrayEquals(previous.slice(0x380,128).copy(),out.slice(0x380,128).copy());assertEquals(0x8000FFFFL,out.unsignedLong(0x380));
    }
    @Test void lowSplitDefersPlanesPlcHudUntilAfterFinishAndCurrentWaterSelection(){
        var p=CdR4StageServicePlan.stage(new WaterProjection(0,0,0x5F),0,0);
        assertEquals(java.util.List.of(FLUSH_FM,READ_JOYPADS,UPDATE_WATER,SELECT_INITIAL_PALETTE,ARM_WATER_SPLIT,COPY_HSCROLL_PREFIX,SELECT_OLD_CPU_SAT,LOAD_PLAYER_GFX,PUBLISH_PLAYER_GFX_IF_DIRTY,ANIMATE_STAGE_GFX,COPY_CAMERAS_32_BYTES,COPY_FLAGS_8_BYTES,SET_DEFERRED_BYTE),p.beforeFinish());
        assertEquals(java.util.List.of(BORED_TIMER,WARP_TIMER,INCREMENT_VBLANK_LONG),p.finish());assertEquals(java.util.List.of(DRAW_PLANES,SLOW_PLC,HUD),p.deferred().orElseThrow().afterWaterPalette());
        var h=CdR4StageServicePlan.waterBoundary(1,0x5F,1);assertEquals(java.util.List.of(CLEAR_SPLIT_ARM_WORD,SELECT_CURRENT_WATER_PALETTE,CLEAR_DEFERRED_BYTE,DRAW_PLANES,SLOW_PLC,HUD),h.actions());assertEquals(0,h.deferredByteAfter());
    }
    @Test void split96AndFullWaterDrawBeforeFinishAndHaveNoDeferredTail(){
        for(int split:new int[]{0x60,0xDF,0xFF}){var p=CdR4StageServicePlan.stage(new WaterProjection(0,split==0xDF?1:0,split),0,0);assertTrue(p.deferred().isEmpty());assertEquals(java.util.List.of(DRAW_PLANES,SLOW_PLC,HUD),p.beforeFinish().subList(p.beforeFinish().size()-3,p.beforeFinish().size()));assertFalse(p.beforeFinish().contains(SET_DEFERRED_BYTE));}
    }
    @Test void pauseOnlyGatesFinishTimersHereAndWholeWordTimeStopGatesAnimation(){var p=CdR4StageServicePlan.stage(new WaterProjection(0,0,96),0x8000,1);assertEquals(java.util.List.of(INCREMENT_VBLANK_LONG),p.finish());assertFalse(p.beforeFinish().contains(ANIMATE_STAGE_GFX));assertTrue(p.beforeFinish().contains(UPDATE_WATER));}
    @Test void disabledArmAndDfSplitRetainDirtyDeferredByteWithoutDrawing(){var p=CdR4StageServicePlan.waterBoundary(0,0,0x80);assertTrue(p.actions().isEmpty());assertEquals(0x80,p.deferredByteAfter());p=CdR4StageServicePlan.waterBoundary(0x8000,0xDF,0x80);assertEquals(java.util.List.of(CLEAR_SPLIT_ARM_WORD),p.actions());assertEquals(0,p.splitArmWordAfter());assertEquals(0x80,p.deferredByteAfter());var h=CdR4StageServicePlan.waterBoundary(1,10,0);assertEquals(java.util.List.of(CLEAR_SPLIT_ARM_WORD,SELECT_CURRENT_WATER_PALETTE),h.actions());}
    @Test void proposedDeferredCaptureCutRejectsAnyNonzeroByteAndPlansAreImmutable(){CdR4StageServicePlan.requireNoDeferredCapture(0);assertThrows(UnsupportedOperationException.class,()->CdR4StageServicePlan.requireNoDeferredCapture(128));var p=CdR4StageServicePlan.stage(new WaterProjection(0,0,0),0,0);assertThrows(UnsupportedOperationException.class,()->p.beforeFinish().clear());assertThrows(UnsupportedOperationException.class,()->p.deferred().orElseThrow().afterWaterPalette().clear());}
    @Test void fullWidthsAndVisibleHullAreExplicit(){assertThrows(IllegalArgumentException.class,()->new WaterProjection(-1,0,0));assertThrows(IllegalArgumentException.class,()->new WaterProjection(0,256,0));var p=palettes(fill(128,0),fill(128,1),new WaterProjection(0,0,0),true,Screen.LEVEL);assertThrows(IllegalArgumentException.class,()->p.atLine(224));assertThrows(IllegalArgumentException.class,()->selectHscroll(fill(1023,0),fill(1024,0)));}
    static CdAssetBytes fill(int n,int v){byte[] b=new byte[n];Arrays.fill(b,(byte)v);return CdAssetBytes.copyOf(b);}
}
