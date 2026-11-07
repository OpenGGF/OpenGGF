package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Authenticated original R31 data plus explicitly CONSTRUCTED complete caller values.
 * Tests authored first; Author Java was UNRUN; Root qualification is recorded in the implementation plan. No service/World/Frame/selector8/pixel authority. */
class TestCdR31aNativeHud {
 private static final String MANIFEST="""
NUMBERS 330bc 1024 843a1129a5ea39faa84ac2962429474d90801778a4c37f102ed3d45d2091fa42
UPDATE_HUD a940 1118 c61e027ce588825f96b3bd0109909ffd830e035e1995548292b3b16f2937fddb
RESET_TILES ab86 12 3348ed9fdfee6353b33f6e3278709e1d2d73aee6582d4e3cec6c793116b2770c
DIVISORS ace6 40 7c6f11391643421f6f80b1140887b70a76afdcd962023c45939ec248abc0c0ea
VINT_CONSUMER 1dfa 6 ba87bed07542537d09aa62734db209ea43d6f78fcdf1a844a9e80c3d8a2a3f60
""";
 @Test void completeManifestAndOriginalCacheAreIndependentOfTheOld63Views()throws Exception{
  var a=actual();var h=CdR31aHudAssets.fromAssets(a);assertTrue(h.ownsAssets(a));assertSame(a.identity(),h.identity());
  var lines=MANIFEST.strip().lines().toList();assertEquals(5,lines.size());assertEquals(5,h.ranges().size());
  for(int i=0;i<lines.size();i++){var p=lines.get(i).split(" ");var id=CdR31aHudAssets.Source.valueOf(p[0]);var r=h.ranges().get(i);assertEquals(id,r.source());assertEquals(Integer.parseInt(p[1],16),r.fileOffset());assertEquals(Integer.parseInt(p[2]),r.length());assertEquals(p[3],r.sha256());assertEquals(r.fileOffset()+0x200000,r.nativeAddress());assertEquals(r.fileOffset()+r.length(),r.endExclusive());assertEquals(p[3],TestCdR31aAssets.hash(h.view(id).copy()));assertSame(h.view(id),h.view(id));assertThrows(IndexOutOfBoundsException.class,()->h.view(id).unsignedByte(r.length()));}
  assertSame(a.view(CdR31aAssets.Asset.ART_HUD_NUMBERS),h.view(CdR31aHudAssets.Source.NUMBERS));assertSame(h.hudAssets(),h.hudAssets());assertSame(h.view(CdR31aHudAssets.Source.RESET_TILES),h.hudAssets().resetTiles());assertEquals(67,a.ranges().size());assertEquals(15,Arrays.stream(CdR31aAssets.class.getDeclaredMethods()).filter(x->Modifier.isPublic(x.getModifiers())).count());
 }
 @Test void actualPcAbsoluteAndDestinationOperandsBindTheFullConsumers()throws Exception{
  var h=CdR31aHudAssets.fromAssets(actual());var b=h.view(CdR31aHudAssets.Source.UPDATE_HUD);
  assertEquals("4ef90020a940",hex(h.view(CdR31aHudAssets.Source.VINT_CONSUMER)));
  assertEquals(0xAB86,0xAB52+(short)b.unsignedWord(0xAB52-0xA940));assertEquals(0xAB8E,0xAB34+(short)b.unsignedWord(0xAB34-0xA940));assertEquals(0xAD18,0xAB44+(short)b.unsignedWord(0xAB44-0xA940));assertEquals(6,b.unsignedWord(0xAB56-0xA940));assertEquals(2,b.unsignedWord(0xAB38-0xA940));
  for(int at:new int[]{0xAB58,0xABBA,0xAC36,0xACA6,0xAD4C})assertEquals(0x2330BC,b.unsignedLong(at-0xA940+2));
  assertEquals(List.of(new CdR31aHudAssets.Destination(0xB060,0x3C0),new CdR31aHudAssets.Destination(0xB4A0,64)),h.destinations());
  assertEquals(0x70600002L,b.unsignedLong(0xA9AA-0xA940+2));assertEquals(0x73600002L,b.unsignedLong(0xA9CE-0xA940+2));assertEquals(0x74A00002L,b.unsignedLong(0xAD18-0xA940+2));
 }
 @Test void resetAndDivisorTablesHaveTheirExactNativePaddingAndAllTenLongs()throws Exception{
  var h=CdR31aHudAssets.fromAssets(actual());assertEquals("ffffffffffff0000ffff0000",hex(h.hudAssets().resetTiles()));
  long[] v={100000,10000,1000,100,10,1,0x1000,0x100,0x10,1};for(int i=0;i<v.length;i++)assertEquals(v[i],h.hudAssets().divisors().unsignedLong(i*4));
  assertThrows(UnsupportedOperationException.class,()->h.ranges().clear());assertThrows(UnsupportedOperationException.class,()->h.destinations().clear());byte[] copy=h.hudAssets().numbers().copy();copy[0]^=1;assertNotEquals(copy[0],h.hudAssets().numbers().copy()[0]);
 }
 @Test void everyNewSpanMutationAndForeignImageAreRejectedBeforeBinding()throws Exception{
  var f=TestCdR31aAssets.actual();var h=CdR31aHudAssets.fromAssets(f.assets());for(var r:h.ranges()){byte[] b=f.image().readFile(0,262144);b[r.endExclusive()-1]^=1;assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,CdMmdImage.parse(b)),CdStagePayload.R31A.stage()));}
  assertThrows(IOException.class,()->CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.foreign()),CdStagePayload.R31A.stage()));assertThrows(NullPointerException.class,()->CdR31aHudAssets.fromAssets(null));
 }
 @Test void equalPublicIdentityCannotGrantTheRetainedSourceRelation()throws Exception{
  var a=actual();var f=TestCdR31aAssets.actual();var other=CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.image()),CdStagePayload.R31A.stage());var h=CdR31aHudAssets.fromAssets(a);
  assertEquals(a.identity(),other.identity());assertFalse(h.ownsAssets(other));assertThrows(IllegalArgumentException.class,()->CdR31aArtResidency.unknownForNativeHud(other,h));assertThrows(IllegalArgumentException.class,()->new CdLevelHud.R31(other,h,CdR31aSectionArt.fromAssets(other)));
 }
 @Test void explicitProfileAddsOnlyTheProvedDynamicRangesAndStartsUnknown()throws Exception{
  var a=actual();var h=CdR31aHudAssets.fromAssets(a);var old=CdR31aArtResidency.unknown(a);var now=CdR31aArtResidency.unknownForNativeHud(a,h);assertFalse(old.hasNativeHudProfile());assertTrue(now.hasNativeHudProfile());assertTrue(now.ownsAssets(a));
  assertEquals(List.of(new CdR31aArtResidency.Window(0,0x56C0),new CdR31aArtResidency.Window(0x6000,0x300),new CdR31aArtResidency.Window(0x63C0,0x44C0),new CdR31aArtResidency.Window(0xAD00,0x360),new CdR31aArtResidency.Window(0xB420,0x80),new CdR31aArtResidency.Window(0xB500,0xA40),new CdR31aArtResidency.Window(0xD000,0xFC0),new CdR31aArtResidency.Window(0xF5C0,0x1C0)),old.windows());
  assertEquals(List.of(new CdR31aArtResidency.Window(0,0x56C0),new CdR31aArtResidency.Window(0x6000,0x300),new CdR31aArtResidency.Window(0x63C0,0x44C0),new CdR31aArtResidency.Window(0xAD00,0x7E0),new CdR31aArtResidency.Window(0xB500,0xA40),new CdR31aArtResidency.Window(0xD000,0xFC0),new CdR31aArtResidency.Window(0xF5C0,0x1C0)),now.windows());
  for(int at:new int[]{0xB060,0xB200,0xB360,0xB400,0xB4A0,0xB4C0}){assertTrue(now.knownByte(at).isEmpty());assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.readTile(now,at/32));assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.withTile(old,at,fill(32,7)));}
  assertThrows(CdR31aArtResidency.OwnershipBoundary.class,()->CdR31aArtResidency.withTile(now,0xB4E0,fill(32,7)));assertThrows(IllegalArgumentException.class,()->CdR31aArtResidency.withTile(now,0xB061,fill(32,7)));assertThrows(IllegalArgumentException.class,()->CdR31aArtResidency.withTile(now,0xB060,fill(31,7)));
 }
 @Test void pureClearAndTileValuesNeverMakeTheOriginalUnknownProfileKnown()throws Exception{
  var a=actual();var h=CdR31aHudAssets.fromAssets(a);var before=CdR31aArtResidency.unknownForNativeHud(a,h);var cleared=CdR31aArtResidency.clear(before);var after=CdR31aArtResidency.withTile(cleared,0xB4A0,fill(32,0x5A));assertTrue(before.knownByte(0xB4A0).isEmpty());assertEquals(0,cleared.knownByte(0xB4A0).orElseThrow());assertEquals(0x5A,after.knownByte(0xB4A0).orElseThrow());assertEquals(0,after.knownByte(0xB4C0).orElseThrow());
 }
 @Test void lockedInitialCallStillWritesScoreRingsAndLivesBeforeDrawingTime()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var h=CdR31aHudAssets.fromAssets(a);var s=state(a,art,true,0,0x80,0x80,1,16,3,1,1234);var r=new CdLevelHud.R31(a,h,art).run(s,inputs(0,0,1,1));
  assertEquals(List.of(CdLevelHud.NativeCall.DRAW_LIVES,CdLevelHud.NativeCall.RESET_SCORE,CdLevelHud.NativeCall.DRAW_SCORE,CdLevelHud.NativeCall.RESET_RINGS,CdLevelHud.NativeCall.DRAW_RINGS,CdLevelHud.NativeCall.DRAW_MINS,CdLevelHud.NativeCall.DRAW_SECS,CdLevelHud.NativeCall.DRAW_SECS,CdLevelHud.NativeCall.DRAW_LIVES),r.calls());
  assertEquals(0,time(r.state()));assertEquals(0,r.state().score().updateHudScore());assertEquals(0,r.state().ring().updateHudRings());assertEquals(0,r.state().ring().updateHudLives());
  glyph(h,r.state(),0xB4A0,3);glyph(h,r.state(),0xB0E0,1);glyph(h,r.state(),0xB120,2);glyph(h,r.state(),0xB160,3);glyph(h,r.state(),0xB1A0,4);glyph(h,r.state(),0xB3A0,1);glyph(h,r.state(),0xB3E0,6);glyph(h,r.state(),0xB220,0);nonwrites(s.section(),r.state().section());
 }
 @ParameterizedTest @CsvSource({"0000003B,00000100","00003B3B,00010000","00083B3B,00090000","AA003B3B,AA010000","000000FF,00000000","0000FF3B,00000000","00FF3B3B,00000000","00093B3A,00093B3B"})
 void literalNativeByteCarryAndWrapPreserveTheFourthTimeByte(String raw,String expected)throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var s=state(a,art,true,(int)Long.parseUnsignedLong(raw,16),0,0,0,16,3,1,0);var r=facade(a,art).run(s,inputs(0,0,0,1));assertEquals((int)Long.parseUnsignedLong(expected,16),time(r.state()));nonwrites(s.section(),r.state().section());
 }
 @Test void pauseAndDisabledTimeSkipTheUnknownReadWithoutReplacingValues()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);for(int enable:new int[]{0,1}){var s=state(a,art,true,0,0,0,0,16,3,enable,0);s=new CdLevelHud.R31State(s.section(),s.ring(),s.level(),s.score(),CdCheckpointLiveAux.unknown(),s.bonus());var r=facade(a,art).run(s,inputs(0,enable,0,1));assertSame(s,r.state());assertTrue(r.calls().isEmpty());}
 }
 @Test void ringCapChangesTheCountButLivesCapIsOnlyALocalDisplayValue()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var h=CdR31aHudAssets.fromAssets(a);var s=state(a,art,true,0,0,1,1,1000,255,0,0);var r=new CdLevelHud.R31(a,h,art).run(s,inputs(0,0,0,1));assertEquals(999,r.state().ring().rings());assertEquals(255,r.state().ring().lives());glyph(h,r.state(),0xB4A0,9);glyph(h,r.state(),0xB360,9);
 }
 @Test void unknownTimeRetainsTheActualEarlierLivesResetScoreAndRingsPrefix()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var s=state(a,art,true,0,0x80,0x80,1,16,3,1,1234);var in=new CdLevelHud.R31State(s.section(),s.ring(),s.level(),s.score(),CdCheckpointLiveAux.unknown(),s.bonus());var f=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->facade(a,art).run(in,inputs(0,0,1,1)));assertEquals(CdLevelHud.Boundary.LIVE_TIME,f.boundary());assertEquals(0,f.state().score().updateHudScore());assertEquals(0,f.state().ring().updateHudRings());assertEquals(1,f.state().ring().updateHudLives());assertEquals(5,f.calls().size());assertSame(in.liveTime(),f.state().liveTime());nonwrites(s.section(),f.state().section());
 }
 @Test void oldProfileArtFailureKeepsTheOriginalThrowableAndFirstReachedDestination()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var s=state(a,art,false,0,0x80,0x80,1,16,3,1,1234);var f=assertThrows(CdLevelHud.R31ArtFailure.class,()->facade(a,art).run(s,inputs(0,0,1,1)));assertSame(f.original(),f.getCause());assertSame(f.original().state(),f.state().section());assertEquals(CdR31aSectionArt.Reason.RESIDENCY,f.original().reason());assertEquals(0xB4A0,f.original().destinationByteOffset());assertTrue(f.calls().isEmpty());assertSame(s.ring(),f.state().ring());assertSame(s.score(),f.state().score());
 }
 @Test void dirtyScoreStopsAtItsActualFontReadAfterClearingOnlyTheScoreFlag()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var s=state(a,art,true,0,1,0,0,16,3,0,1600000);var f=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->facade(a,art).run(s,inputs(0,0,0,1)));assertEquals(CdLevelHud.Boundary.FONT_ADDRESS,f.boundary());assertEquals(1024,f.sourceOffset());assertEquals(0xB060,f.destinationWord());assertEquals(0,f.state().score().updateHudScore());assertSame(s.section(),f.state().section());assertTrue(f.calls().isEmpty());
 }
 @Test void maxTimeNormalKeepsThePreKillPrefixAndMarkedWarpReturnsBeforeLivesBonus()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var s=state(a,art,true,0x00093B3B,0,0,1,16,3,1,0);var f=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->facade(a,art).run(s,inputs(0,0,0,1)));assertEquals(CdLevelHud.Boundary.MAX_TIME_KILL,f.boundary());assertEquals(0,time(f.state()));assertEquals(0,f.state().level().updateHudTime());assertEquals(1,f.state().ring().updateHudLives());assertSame(s.section(),f.state().section());assertEquals(s.level().timeOver(),f.state().level().timeOver());var marked=facade(a,art).run(s,inputs(0,0,0,0x81));assertSame(s,marked.state());assertTrue(marked.calls().isEmpty());
 }
 @Test void debugPositionCompletesBeforeMissingActualSavedFlagRead()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var s=state(a,art,true,0,0,0,0,16,3,0,0);var f=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->facade(a,art).run(s,inputs(1,0,0,1)));assertEquals(CdLevelHud.Boundary.DEBUG_SAVED_FLAGS,f.boundary());assertEquals(List.of(CdLevelHud.NativeCall.DRAW_POS),f.calls());assertNotSame(s.section(),f.state().section());assertSame(s.ring(),f.state().ring());
 }
 @Test void missingBonusIsNotInventedAndReachedBonusUsesOnlyExistingWindows()throws Exception{
  var a=actual();var art=CdR31aSectionArt.fromAssets(a);var s=state(a,art,true,0,0,0,0,16,3,0,0);var missing=new CdLevelHud.R31State(s.section(),s.ring(),s.level(),s.score(),s.liveTime(),Optional.empty());var f=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->facade(a,art).run(missing,inputs(0,0,0,1)));assertEquals(CdLevelHud.Boundary.BONUS_VALUES,f.boundary());assertSame(missing,f.state());var bonus=new CdLevelHud.R31State(s.section(),s.ring(),s.level(),s.score(),s.liveTime(),Optional.of(new CdLevelHud.BonusValues(123,456,1)));var r=facade(a,art).run(bonus,inputs(0,0,0,1));assertEquals(0,r.state().bonus().orElseThrow().updateHudBonusByte());assertEquals(List.of(CdLevelHud.NativeCall.DRAW_BONUS,CdLevelHud.NativeCall.DRAW_BONUS),r.calls());
 }
 @Test void foreignSectionAndStoppedSectionRejectBeforeAnyHudWork()throws Exception{
  var f=TestCdR31aAssets.actual();var a=f.assets();var other=CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.image()),CdStagePayload.R31A.stage());var art=CdR31aSectionArt.fromAssets(a);var foreignArt=CdR31aSectionArt.fromAssets(other);var foreign=state(other,foreignArt,true,0,0x80,0x80,1,16,3,1,1);assertThrows(IllegalArgumentException.class,()->facade(a,art).run(foreign,inputs(0,0,0,1)));
  var s=state(a,art,false,0,0,0,0,16,3,0,0);var original=assertThrows(CdR31aSectionArt.SectionArtException.class,()->art.writeCurrentTile(s.section(),0xB060,fill(32,7)));var stopped=new CdLevelHud.R31State(original.state(),s.ring(),s.level(),s.score(),s.liveTime(),s.bonus());assertThrows(IllegalStateException.class,()->facade(a,art).run(stopped,inputs(0,0,0,1)));
 }
 @Test void strictSharedFormatAndFacadeValueCensusDoNotAddAnyOwner()throws Exception{
  var h=CdR31aHudAssets.fromAssets(actual());assertEquals(6,CdLevelHud.R31State.class.getRecordComponents().length);assertEquals(2,CdLevelHud.R31Result.class.getRecordComponents().length);assertEquals(3,CdLevelHud.R31.class.getConstructors()[0].getParameterCount());assertEquals(1,CdLevelHud.R31.class.getDeclaredMethods().length);
  for(int width:new int[]{0,1023,1025})assertThrows(IllegalArgumentException.class,()->new CdLevelHud.Assets(fill(width,0),h.hudAssets().resetTiles(),h.hudAssets().divisors()));assertThrows(IllegalArgumentException.class,()->new CdLevelHud.Assets(h.hudAssets().numbers(),fill(11,0),h.hudAssets().divisors()));assertThrows(IllegalArgumentException.class,()->new CdLevelHud.Assets(h.hudAssets().numbers(),h.hudAssets().resetTiles(),fill(39,0)));
 }
 private static CdR31aAssets actual()throws Exception{return TestCdR31aAssets.actual().assets();}
 private static CdLevelHud.R31 facade(CdR31aAssets a,CdR31aSectionArt art){return new CdLevelHud.R31(a,CdR31aHudAssets.fromAssets(a),art);}
 private static CdAssetBytes fill(int n,int v){byte[] b=new byte[n];Arrays.fill(b,(byte)v);return CdAssetBytes.copyOf(b);}
 private static String hex(CdAssetBytes b){return HexFormat.of().formatHex(b.copy());}
 private static CdLevelHud.Inputs inputs(int debug,int paused,int locked,int period){return new CdLevelHud.Inputs(new CdLevelHud.Globals(debug,paused,locked,period,0x40,0x2A2,0x100),new CdLevelHud.DebugData(OptionalInt.empty(),Optional.empty(),OptionalInt.empty()));}
 private static CdLevelHud.R31State state(CdR31aAssets a,CdR31aSectionArt art,boolean nativeHud,int time,int scoreFlag,int ringsFlag,int livesFlag,int rings,int lives,int timeEnabled,int score){
  var resident=nativeHud?CdR31aArtResidency.unknownForNativeHud(a,CdR31aHudAssets.fromAssets(a)):CdR31aArtResidency.unknown(a);
  var section=art.fromValues(fill(512,0x31),resident,0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.XOR,0x1234,10,0x89ABCDEF,0x5678,0x9ABC),0,3,4,5);
  return new CdLevelHud.R31State(section,new CdRingGlobals(rings,lives,0xA5,ringsFlag,livesFlag,0x5678,0x34,0x56),new CdLevelGlobals(0,timeEnabled,0x45,0x67,3,0x89,2,0xAB,0xCD),new CdOpeningObjectRuntime.ScoreGlobals(score,5000,scoreFlag),CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),time),Optional.of(new CdLevelHud.BonusValues(0,0,0)));
 }
 private static int time(CdLevelHud.R31State s){return CdCheckpointLiveAux.read(s.liveTime(),CdCheckpointLiveAux.Field.TIME_LONG);}
 private static void glyph(CdR31aHudAssets h,CdLevelHud.R31State s,int at,int digit){for(int half=0;half<64;half+=32)assertArrayEquals(h.hudAssets().numbers().slice(digit*64+half,32).copy(),CdR31aArtResidency.readTile(s.section().residency(),(at+half)/32).copy());}
 private static void nonwrites(CdR31aSectionArt.State before,CdR31aSectionArt.State after){assertSame(before.queue(),after.queue());assertSame(before.codeTable(),after.codeTable());assertEquals(before.decoder(),after.decoder());assertEquals(before.savedDecoder(),after.savedDecoder());assertEquals(before.sectionWord(),after.sectionWord());assertEquals(before.remainingTilesWord(),after.remainingTilesWord());assertEquals(before.processBudgetWord(),after.processBudgetWord());assertEquals(before.hintWord(),after.hintWord());assertEquals(before.padWord(),after.padWord());}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
