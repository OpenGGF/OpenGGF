package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.openggf.game.soniccd.level.CdR31aLevelEvents.*;

/** Tests FIRST. Actual original USA source binding + CONSTRUCTED immutable arithmetic inputs.
 * No original World/Frame invocation, clock, callback, capture or publication is established.
 * Author Java was UNRUN; Root qualification is recorded in the implementation plan. Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR31aLevelEvents {
 private static final String MANIFEST="""
SCROLL_CONSUMER 299c 40 f09d5db869f9bbc1c097376e27dc587672abae50e029f1aa7ab47225600a9d3b
COMMON 3626 122 2493b1c9b4618523ae9aeaa6c07309e35f029bdbb73c599a8cd5c46baa52757d
ZONE_DIRECTORY 36a0 14 5c179ea1f1742c6118cd93d38a155ae70122a0dc43a4a309f9cc0ec20eca6d9e
CCZ_DISPATCH 3748 18 aa81abf31c052c7c4fe78e285df0ae88f5e01be62218e75c201e98d6b397c632
ACT_DIRECTORY 375a 6 525fe3e7f297271e170a7be4b8fdd703218ffb4db521d170037787fbfd24692b
CCZ12 3760 8 c20949248a7b300e9310bfee37cf78abf4da9a88b09ea17e7e661f0b80cfaa41
""";
 @Test void exactActualSourceManifestsAndAllFiniteDirectoriesAreRetained()throws Exception{
  var f=TestCdR31aAssets.actual();var a=f.assets();var e=fromAssets(a);assertTrue(e.ownsAssets(a));assertFalse(e.ownsAssets(null));assertSame(a.identity(),e.identity());assertEquals(63,CdR31aAssets.Asset.values().length);assertEquals(15,Arrays.stream(CdR31aAssets.class.getDeclaredMethods()).filter(m->java.lang.reflect.Modifier.isPublic(m.getModifiers())).count());
  var rows=MANIFEST.strip().lines().toList();assertEquals(6,rows.size());assertEquals(6,e.ranges().size());for(int i=0;i<rows.size();i++){var v=rows.get(i).split(" ");var id=Source.valueOf(v[0]);var r=e.ranges().get(i);assertEquals(id,r.source());assertEquals(Integer.parseInt(v[1],16),r.fileOffset());assertEquals(Integer.parseInt(v[2]),r.length());assertEquals(v[3],r.sha256());assertEquals(v[3],TestCdR31aAssets.hash(e.view(id).copy()));assertArrayEquals(f.image().readFile(r.fileOffset(),r.length()),e.view(id).copy());assertSame(e.view(id),e.view(id));assertThrows(IndexOutOfBoundsException.class,()->e.view(id).unsignedByte(r.length()));}
  assertEquals(List.of(0x36AE,0x3748,0x386C,0x37DE,0x3780,0x38E2,0x3828),targets(e.view(Source.ZONE_DIRECTORY),0x36A0));assertEquals(List.of(0x3760,0x3760,0x3768),targets(e.view(Source.ACT_DIRECTORY),0x375A));assertEquals(0x510,e.view(Source.CCZ12).unsignedWord(2));assertEquals(0xF726,e.view(Source.CCZ12).unsignedWord(4));assertEquals(0x4E75,e.view(Source.CCZ12).unsignedWord(6));assertEquals(0x3626,0x29BA+(short)e.view(Source.SCROLL_CONSUMER).unsignedWord(30));assertEquals(0x2CF2,0x29BE+(short)e.view(Source.SCROLL_CONSUMER).unsignedWord(34));assertEquals(0x2D92,0x29C2+(short)e.view(Source.SCROLL_CONSUMER).unsignedWord(38));
 }
 @Test void actualBoundColdCutStillRecordsTheUnchangedDefaultTargetStore()throws Exception{
  var e=actual();var f=new CdR11aForegroundScroll.State(0,0x038C0000,16,16,0,0,0,0x510,0,0,0);var b=new CdPlayerMovement.Bounds(0,0x2E97,0x510,0);var r=e.run(f,b,new Player(0,0),new Gates(0x100));assertSame(f,r.foreground());assertSame(b,r.bounds());assertEquals(List.of(new Write(Field.DEST_BOTTOM_WORD,0x510,0x510)),r.writes());
 }
 @ParameterizedTest @CsvSource({
 "1296,908,0,0,1296,42330,1",
 "1536,1280,0,0,1532,346,3",
 "1536,1297,0,0,1292,346,5",
 "1280,1271,0,2,1284,346,3",
 "1280,1272,0,2,1296,346,3",
 "1280,1272,0,0,1284,346,3",
 "1280,65528,0,2,1284,346,3",
 "32768,0,0,0,32764,346,3",
 "1536,65535,0,0,65530,346,5",
 "1295,0,0,0,1299,346,3",
 "1299,0,0,0,1295,346,3"
 })
 void literalNativeUnsignedWordAndShiftVectors(int bottom,int y,int animation,int flags,int expectedBottom,int expectedShift,int writes)throws Exception{
  var e=actual();var f=dirty(y);var b=bounds(bottom);var r=e.run(f,b,new Player(animation,flags),new Gates(0x100));assertEquals(expectedBottom,r.bounds().bottom());assertEquals(0x510,r.foreground().destBottomBound());assertEquals(expectedShift,r.foreground().bottomBoundShift());assertEquals(writes,r.writes().size());assertEquals(new Write(Field.DEST_BOTTOM_WORD,0xDEAD,0x510),r.writes().getFirst());nonwrites(f,b,r);assertEquals(bottom,b.bottom());assertEquals(0xDEAD,f.destBottomBound());assertEquals(0xA55A,f.bottomBoundShift());
 }
 @Test void givingUpUsesCameraWordAndDoesNotTouchDirtyShift()throws Exception{
  var f=dirty(0x315);var b=bounds(0x600);var r=actual().run(f,b,new Player(0x2B,0xFF),new Gates(0x100));assertEquals(0x315,r.bounds().bottom());assertEquals(0x315,r.foreground().destBottomBound());assertEquals(0xA55A,r.foreground().bottomBoundShift());assertEquals(List.of(new Write(Field.DEST_BOTTOM_WORD,0xDEAD,0x510),new Write(Field.BOTTOM_WORD,0x600,0x315),new Write(Field.DEST_BOTTOM_WORD,0x510,0x315)),r.writes());nonwrites(f,b,r);
 }
 @Test void upwardBaselineMoveAndAndiAreSeparateOrderedWordObservations()throws Exception{
  var r=actual().run(dirty(0x511),bounds(0x600),new Player(0,0),new Gates(0x100));assertEquals(List.of(new Write(Field.DEST_BOTTOM_WORD,0xDEAD,0x510),new Write(Field.BOTTOM_WORD,0x600,0x511),new Write(Field.BOTTOM_WORD,0x511,0x510),new Write(Field.BOTTOM_WORD,0x510,0x50C),new Write(Field.SHIFT_HIGH_BYTE,0xA5,1)),r.writes());
 }
 @Test void highShiftByteIsAssignedAndLowByteNeverTreatedAsAShiftFlag()throws Exception{
  var f=dirty(0);var r=actual().run(f,bounds(0x500),new Player(0,0),new Gates(0x100));assertEquals(0x015A,r.foreground().bottomBoundShift());assertEquals(8,r.writes().getLast().field().bits());assertEquals(16,r.writes().getFirst().field().bits());assertEquals(0xA5,r.writes().getLast().before());assertEquals(1,r.writes().getLast().after());
 }
 @Test void commonRunEventsDoesNotSampleScrollLockOrAnyPeriod()throws Exception{
  // Actual LevelScroll owns lock eligibility. A direct pure RunLevelEvents cut has no lock/period read.
  var f=dirty(0);var locked=new CdR11aForegroundScroll.State(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destBottomBound(),f.destLeftBound(),f.bottomBoundShift(),0x81F3);var r=actual().run(locked,bounds(0x500),new Player(0,0),new Gates(0x100));assertEquals(0x504,r.bounds().bottom());assertEquals(0x81F3,r.foreground().scrollLock());assertEquals(1,Gates.class.getRecordComponents().length);assertEquals("rawZoneActWord",Gates.class.getRecordComponents()[0].getName());
 }
 @Test void foreignZoneActDispatchNeverNormalizesToTheOwnedAct()throws Exception{
  var e=actual();var f=dirty(0);var b=bounds(0x510);for(int raw:new int[]{0,1,2,0x101,0x102,0x200,0xFFFF}){var x=assertThrows(UnsupportedDispatch.class,()->e.run(f,b,new Player(0,0),new Gates(raw)));assertEquals(raw,x.rawZoneActWord());assertEquals(0xDEAD,f.destBottomBound());assertEquals(0x510,b.bottom());}
 }
 @Test void missingCompleteInputsFailWithoutChangingEitherExistingValue()throws Exception{var e=actual();var f=dirty(0);var b=bounds(0x510);var player=new Player(0,0);var gates=new Gates(0x100);assertThrows(NullPointerException.class,()->e.run(null,b,player,gates));assertThrows(NullPointerException.class,()->e.run(f,null,player,gates));assertThrows(NullPointerException.class,()->e.run(f,b,null,gates));assertThrows(NullPointerException.class,()->e.run(f,b,player,null));assertEquals(0xDEAD,f.destBottomBound());assertEquals(0x510,b.bottom());}
 @Test void immutableCachedSourceAndComparisonDataCannotBeEdited()throws Exception{
  var e=actual();var view=e.view(Source.CCZ12);var copy=view.copy();copy[2]^=1;assertEquals(0x510,view.unsignedWord(2));assertThrows(java.nio.ReadOnlyBufferException.class,()->view.buffer().put(0,(byte)1));assertThrows(UnsupportedOperationException.class,()->e.ranges().clear());var r=e.run(dirty(0),bounds(0x500),new Player(0,0),new Gates(0x100));assertThrows(UnsupportedOperationException.class,()->r.writes().clear());assertThrows(NullPointerException.class,()->e.view(null));
 }
 @Test void mutatedEveryNewSourceSpanFailsOriginalAggregateAuthentication()throws Exception{
  var f=TestCdR31aAssets.actual();var e=fromAssets(f.assets());for(var range:e.ranges()){var b=f.image().readFile(0,262144);b[range.fileOffset()+range.length()-1]^=1;assertThrows(java.io.IOException.class,()->CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,CdMmdImage.parse(b)),CdStagePayload.R31A.stage()),range.source().name());}
 }
 @Test void foreignActualImageAndEqualPublicIdentityMockCannotMintSourceCustody()throws Exception{
  var f=TestCdR31aAssets.actual();assertThrows(java.io.IOException.class,()->CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.foreign()),CdStagePayload.R31A.stage()));var other=CdR31aAssets.fromPrograms(TestCdR31aAssets.source(f,f.image()),CdStagePayload.R31A.stage());var e=fromAssets(f.assets());assertFalse(e.ownsAssets(other));var fake=mock(CdR31aAssets.class);when(fake.identity()).thenReturn(f.assets().identity());when(fake.levelEventsSource()).thenReturn(f.assets().levelEventsSource());assertThrows(IllegalArgumentException.class,()->fromAssets(fake));assertThrows(NullPointerException.class,()->fromAssets(null));
 }
 @Test void canonicalWidthsNullsAndWriteMetadataStayFiniteWithoutMedia(){
  for(int bad:new int[]{-1,256,65536}){assertThrows(IllegalArgumentException.class,()->new Player(bad,0));assertThrows(IllegalArgumentException.class,()->new Player(0,bad));}for(int bad:new int[]{-1,65536})assertThrows(IllegalArgumentException.class,()->new Gates(bad));assertThrows(IllegalArgumentException.class,()->new Write(Field.SHIFT_HIGH_BYTE,256,1));assertThrows(IllegalArgumentException.class,()->new Write(Field.BOTTOM_WORD,0,65536));assertThrows(NullPointerException.class,()->new Write(null,0,0));assertThrows(IllegalArgumentException.class,()->new Range(Source.COMMON,0,122,"0".repeat(64)));
 }
 @Test void noFrameServiceCaptureOwnerOrPublicRawFactoryIsIntroduced(){
  var methods=Arrays.stream(CdR31aLevelEvents.class.getDeclaredMethods()).filter(m->java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList();assertEquals(List.of("fromAssets/1","identity/0","ownsAssets/1","ranges/0","run/4","view/1"),methods);assertTrue(Arrays.stream(CdR31aLevelEvents.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));assertEquals(2,Player.class.getRecordComponents().length);assertEquals(1,Gates.class.getRecordComponents().length);assertEquals(3,Result.class.getRecordComponents().length);
 }
 private static CdR31aLevelEvents actual()throws Exception{return fromAssets(TestCdR31aAssets.actual().assets());}
 private static List<Integer> targets(CdAssetBytes view,int base){return java.util.stream.IntStream.range(0,view.length()/2).map(i->base+(short)view.unsignedWord(i*2)).boxed().toList();}
 private static CdR11aForegroundScroll.State dirty(int y){return new CdR11aForegroundScroll.State(0x1234,(y<<16)|0xABCD,0x12,0x34,0x5678,0x9ABC,0xA55A,0xDEAD,0xBEEF,0xA55A,0x00F3);}
 private static CdPlayerMovement.Bounds bounds(int bottom){return new CdPlayerMovement.Bounds(0x1111,0x2222,bottom,0xA5);}
 private static void nonwrites(CdR11aForegroundScroll.State f,CdPlayerMovement.Bounds b,Result r){var o=r.foreground();assertEquals(List.of(f.cameraX(),f.cameraY16_16(),f.horizontalCrossed(),f.verticalCrossed(),f.scrollXDiff(),f.scrollYDiff(),f.scrollFlags(),f.destLeftBound(),f.scrollLock()),List.of(o.cameraX(),o.cameraY16_16(),o.horizontalCrossed(),o.verticalCrossed(),o.scrollXDiff(),o.scrollYDiff(),o.scrollFlags(),o.destLeftBound(),o.scrollLock()));assertEquals(b.left(),r.bounds().left());assertEquals(b.right(),r.bounds().right());assertEquals(b.bossFight(),r.bounds().bossFight());}
}
