package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/** Constructed complete component ingress, never a fabricated World Ready or original call.
 * Literal selected R3 Game Over/Main.asm:27-112; actual media and ordinary private physical pass.
 * Current-art/palette/driver execution is not inferred from these CPU/pool component checks. */
class TestSonicCdR31aGameOverPass {
    private static void globals(TestSonicCdR31aObjectPass.Harness h,int lives,int powerup,int timeOver){
        var c=h.cut;var r=c.ring();var g=c.core().level();var level=new CdLevelGlobals(g.levelRestart(),g.updateHudTime(),g.checkpoint(),g.spawnMode(),g.plcLoadFlags(),g.flowerCount(),g.fadeEnableDisplay(),powerup,timeOver);
        h.cut=new SonicCdR31aObjectPass.Cut(c.core().level(level),new CdRingGlobals(r.rings(),lives,r.livesFlags(),r.updateHudRings(),r.updateHudLives(),r.timeStopTimer(),r.ringAnimationTimer(),r.ringAnimationFrame()),c.currentTerrain(),c.contact(),new com.openggf.game.soniccd.player.CdPlayerTouchResponse.WorldGlobals(r.rings(),lives,r.updateHudLives(),c.world().score(),c.world().nextLifeScore(),c.world().updateHudScore()),c.noBumperByte(),c.cameraXWord(),c.cameraYWord(),c.scrollLockWord(),c.layerByte(),c.cpuSat(),c.completedCpu(),c.sounds(),c.amyCapturedByte());
    }
    private static List<CdGameOver.SubCpuCommand> commands(TestSonicCdR31aObjectPass.Harness h){return h.cut.sounds().stream().filter(SonicCdGameModule.PastGameOverSubCpu.class::isInstance).map(SonicCdGameModule.PastGameOverSubCpu.class::cast).map(SonicCdGameModule.PastGameOverSubCpu::command).toList();}
    private static boolean scalar(TestSonicCdR31aObjectPass.Harness h,CdGameOver.Scalar field){var c=h.lastAdoption.gameOverScalar();return c!=null&&c.field()==field;}
    @Test void actualFiniteSourceOwnsDistinctGameAndTimeRootsWithoutArtAuthority()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){var source=CdR31aGameOverSource.fromAssets(h.assets);assertTrue(source.owns(h.assets));assertFalse(source.owns(null));var a=source.data();assertEquals(0x20F262,a.gameOverMappingsAddress());assertEquals(0x20F266,a.timeOverMappingsAddress());assertEquals(56,a.mappings().length());var m=CdObjectMappings.fromR31aAssets(h.assets,h.objects);for(int root:new int[]{0x20F262,0x20F266})for(int frame=0;frame<2;frame++)assertNotNull(m.resolve(root,false,frame));assertThrows(UnsupportedOperationException.class,()->m.resolve(0x20F262,false,2));assertThrows(NullPointerException.class,()->CdR31aGameOverSource.fromAssets(null));}
    }
    @Test void livesRemainingFadesThenWritesHeaderPowerupAndAlwaysBclrsBeforeWholeDelete()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0xA5);h.placement.pool().write(h.placement.pool().state(32).moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF));globals(h,2,0x37,0xFE);var before=h.placement.capture();var state=h.frame.state();var env=h.frame.environment();h.service();state=h.frame.state();env=h.frame.environment();h.walk();
            assertArrayEquals(new byte[64],h.placement.pool().state(32).copyBytes());assertEquals(8,h.cut.core().level().powerup());assertEquals(0xFE,h.cut.core().level().timeOver());assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),commands(h));assertSame(state,h.frame.state());assertSame(env,h.frame.environment());for(int n=1;n<96;n++)assertEquals(before.pool().slots().get(n),h.placement.pool().capture().slots().get(n));assertEquals(before.status(),h.placement.status());assertArrayEquals(before.savedFlags().copy(),h.placement.capture().savedFlags().copy());h.finish();assertTrue(h.cut.completedCpu().orElseThrow().provenance().isEmpty());}
    }
    @Test void timeOverCreatesDirtyLaterChildAndBothExecuteInPhysicalOrder()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0xA5);h.placement.pool().write(h.placement.pool().state(33).moveWord(Y,0x1234).moveWord(Y_FRACTION,0xBEEF).moveByte(SUBTYPE,0x7F));globals(h,2,0x53,255);h.service();h.walk();var p=h.placement.pool().state(32);var child=h.placement.pool().state(33);
            assertEquals(2,p.routine());assertEquals(136,p.centreX());assertEquals(0x20F266,p.longBits(MAPPINGS));assertEquals(0x8544,p.word(TILE));assertEquals(224,p.word(X_FRACTION));assertEquals(0xA5,p.subtype());assertEquals(440,child.centreX());assertEquals(224,child.word(X_FRACTION));assertEquals(1,child.unsignedByte(MAP_FRAME));assertEquals(0x1234,child.centreY());assertEquals(0xBEEF,child.word(Y_FRACTION));assertEquals(0x7F,child.subtype());assertEquals(0x8A,h.cut.core().level().powerup());assertEquals(254,h.cut.core().level().timeOver());assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),commands(h));h.finish();assertEquals(4,h.cut.completedCpu().orElseThrow().spriteCount());}
    }
    @Test void zeroLivesRequestsMusicAfterActualChildStoreAndChildRunsLater()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);globals(h,0,0,1);h.service();h.walk();assertEquals(128,h.placement.pool().state(32).centreX());assertEquals(440,h.placement.pool().state(33).centreX());assertEquals(0x20F262,h.placement.pool().state(33).longBits(MAPPINGS));assertEquals(0x88,h.cut.core().level().powerup());assertEquals(0,h.cut.core().level().timeOver());assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM,CdGameOver.SubCpuCommand.SCMD_GMOVERMUS),commands(h));h.finish();assertEquals(2,h.cut.completedCpu().orElseThrow().spriteCount());}
    }
    @Test void earlierFreeChildWaitsForNextPhysicalWalk()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(40,0x3B,0,0);globals(h,0,0,0);h.service();h.walk();assertEquals(448,h.placement.pool().state(32).centreX());assertEquals(128,h.placement.pool().state(40).centreX());h.finish();assertEquals(0,h.cut.completedCpu().orElseThrow().spriteCount());}
    }
    @Test void fullPoolDeletesBeforeMusicOrDrawAndPreservesFirstOtherFamilyBoundary()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){for(int w=32;w<128;w++)h.dynamic(w,3,0,0);h.dynamic(32,0x3B,0,0);globals(h,0,0,0);var before=h.placement.capture();h.service();var stop=assertThrows(SonicCdR31aObjectPass.Unowned.class,h::walk);assertEquals(33,stop.wholeIndex());assertEquals(3,stop.value());assertArrayEquals(new byte[64],h.placement.pool().state(32).copyBytes());for(int n=1;n<96;n++)assertEquals(before.pool().slots().get(n),h.placement.pool().capture().slots().get(n));assertEquals(0x88,h.cut.core().level().powerup());assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),commands(h));}
    }
    @Test void mainUsesSignedWordComparisonAndPreservesScreenYAndOtherDirtyBytes()throws Exception{
        for(int[] vector:new int[][]{{0x8000,0,0x8008},{0x7FFF,0x8000,0x7FF7},{288,288,288}})try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,2,0xA5);h.placement.pool().write(h.placement.pool().state(32).moveWord(X,vector[0]).moveWord(GAME_OVER_DEST_X,vector[1]).moveWord(X_FRACTION,0xCAFE).moveWord(Y_FRACTION,0xBEEF).moveLong(MAPPINGS,0x20F262));h.service();h.walk();var s=h.placement.pool().state(32);assertEquals(vector[2],s.centreX());assertEquals(0xCAFE,s.word(X_FRACTION));assertEquals(0xBEEF,s.word(Y_FRACTION));assertEquals(0xA5,s.subtype());assertTrue(commands(h).isEmpty());}
    }
    @Test void originalFadeFailureOccursBeforeHeaderAndRetiresReplay()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0xA5);var before=h.placement.capture();h.service();var error=new AssertionError("original FadePCM owner");h.beforeAdopt=()->{if(!h.lastAdoption.next().sounds().isEmpty())throw error;};assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(before.pool().slots(),h.placement.pool().capture().slots());assertTrue(commands(h).isEmpty());assertThrows(IllegalStateException.class,h::walk);assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);}
    }
    @Test void originalAfterFadeFailureRetainsOnlyReachedCommand()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);var before=h.placement.capture();h.service();var error=new AssertionError("after actual FadePCM");h.afterAdopt=()->{throw error;};assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(before.pool().slots(),h.placement.pool().capture().slots());assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),commands(h));assertEquals(0,h.cut.core().level().powerup());assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);}
    }
    @Test void failedScalarPreparationKeepsOriginalCauseHeaderAndEarlierCommand()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0xA5);globals(h,2,0x31,255);h.service();var error=new AssertionError("powerup prebuild");h.beforeAdopt=()->{if(scalar(h,CdGameOver.Scalar.POWERUP))throw error;};assertSame(error,assertThrows(AssertionError.class,h::walk));var p=h.placement.pool().state(32);assertEquals(2,p.routine());assertEquals(128,p.centreX());assertEquals(224,p.word(X_FRACTION));assertEquals(0x20F262,p.longBits(MAPPINGS));assertEquals(0x31,h.cut.core().level().powerup());assertEquals(255,h.cut.core().level().timeOver());assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),commands(h));}
    }
    @Test void bclrPreparationFailureRetainsPowerupButNotItsUnreachedStore()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);globals(h,2,0,255);h.service();var error=new AssertionError("BCLR current-cut prebuild");h.beforeAdopt=()->{if(scalar(h,CdGameOver.Scalar.TIME_OVER))throw error;};assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(8,h.cut.core().level().powerup());assertEquals(255,h.cut.core().level().timeOver());assertEquals(2,h.placement.pool().state(32).routine());assertEquals(0,h.placement.pool().state(33).id());}
    }
    @Test void fullPoolAndReentryGuardsRejectChangedUnvisitedSource()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);h.service();h.beforeAdopt=()->{assertThrows(IllegalStateException.class,h::walk);h.placement.pool().write(h.placement.pool().state(127).moveWord(X_FRACTION,0xCAFE));};assertThrows(IllegalArgumentException.class,h::walk);assertTrue(commands(h).isEmpty());assertEquals(0,h.placement.pool().state(32).routine());assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);}
    }
    @Test void identicalOriginalPreparationFailureIsNeverSelfSuppressed()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);h.service();var error=new AssertionError("same original native command/preparation cause");h.beforeAdopt=()->{h.onRead=()->{throw error;};throw error;};assertSame(error,assertThrows(AssertionError.class,h::walk));for(var verification:error.getSuppressed())assertNotSame(error,verification,"Only distinct verification faults may be suppressed");assertTrue(commands(h).isEmpty());assertEquals(0,h.placement.pool().state(32).routine());}
    }
    @Test void sameValueReplacementOfCompleteEnvironmentRejectsOriginalCommandAdoption()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);h.service();var env=h.frame.environment();var field=com.openggf.game.soniccd.player.CdPlayerFrameRuntime.class.getDeclaredField("environment");field.setAccessible(true);h.beforeAdopt=()->{try{field.set(h.frame,new com.openggf.game.soniccd.player.CdPlayerBody.Environment(env.movement(),env.controls(),env.globals(),env.dynamicSlots()));}catch(IllegalAccessException e){throw new AssertionError(e);}};assertThrows(IllegalArgumentException.class,h::walk);assertTrue(commands(h).isEmpty());assertEquals(0,h.placement.pool().state(32).routine());field.set(h.frame,env);}
    }
    @Test void musicPreparationFailureRetainsChildHeaderBeforeItsUnvisitedMain()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);globals(h,0,0,0);h.service();var error=new AssertionError("GMOVERMUS preparation");h.beforeAdopt=()->{if(h.lastAdoption.next().sounds().size()==2)throw error;};assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),commands(h));assertEquals(128,h.placement.pool().state(32).centreX());var child=h.placement.pool().state(33);assertEquals(0x3B,child.id());assertEquals(2,child.routine());assertEquals(448,child.centreX());assertEquals(1,child.unsignedByte(MAP_FRAME));assertEquals(0x88,h.cut.core().level().powerup());}
    }
    @Test void drawClearPreparationFailureRetainsMovementBeforeOriginalDrawStore()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(SPRITE_FLAGS,0x80));globals(h,2,0,1);h.service();var error=new AssertionError("Draw clear prebuild");h.beforeAdopt=()->{if(h.cut.core().level().powerup()==0x8A&&h.placement.pool().state(32).centreX()==136)throw error;};assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(136,h.placement.pool().state(32).centreX());assertEquals(0x80,h.placement.pool().state(32).spriteFlags());assertEquals(448,h.placement.pool().state(33).centreX());assertEquals(List.of(CdGameOver.SubCpuCommand.SCMD_FADEPCM),commands(h));}
    }
    @Test void afterDrawClearFailureRetainsExactReachedClearAndOriginalCause()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,0,0);h.placement.pool().write(h.placement.pool().state(32).moveByte(SPRITE_FLAGS,0x80));globals(h,2,0,1);h.service();var error=new AssertionError("after original Draw clear");h.beforeAdopt=()->{if(h.cut.core().level().powerup()==0x8A&&h.placement.pool().state(32).centreX()==136)h.afterAdopt=()->{throw error;};};assertSame(error,assertThrows(AssertionError.class,h::walk));assertEquals(136,h.placement.pool().state(32).centreX());assertEquals(0,h.placement.pool().state(32).spriteFlags());assertEquals(448,h.placement.pool().state(33).centreX());assertThrows(IllegalStateException.class,h.lastAdoption::applyNative);}
    }
    @Test void unsupportedFullRawFrameStopsAtActualMapperAfterRetainedMainAndDrawEntry()throws Exception{
        try(var h=new TestSonicCdR31aObjectPass.Harness()){h.dynamic(32,0x3B,2,0xA5);h.placement.pool().write(h.placement.pool().state(32).moveWord(X,128).moveWord(GAME_OVER_DEST_X,288).moveLong(MAPPINGS,0x20F262).moveByte(MAP_FRAME,255));h.service();h.walk();assertEquals(136,h.placement.pool().state(32).centreX());assertThrows(UnsupportedOperationException.class,h::finish);assertEquals(255,h.placement.pool().state(32).unsignedByte(MAP_FRAME));assertTrue(h.cut.completedCpu().isEmpty());assertThrows(IllegalStateException.class,h::walk);}
    }
}
