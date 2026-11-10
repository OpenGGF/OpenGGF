package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import com.openggf.game.soniccd.SonicCdR31aLoadingVisit.OrdinaryAccess;
import com.openggf.game.soniccd.SonicCdR31aLoadingVisit.NativeClearCall;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.openggf.game.soniccd.SonicCdR31aLoadingVisit.*;

/** Tests FIRST. Actual immutable USA data with CONSTRUCTED complete Entry/Host controls.
 * No boot/SPX/Module/World, native source invocation, Title execution, pixels or ordinary route claim.
 * Author Java was UNRUN; Root qualification is recorded in the implementation plan. Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestSonicCdR31aLoadingVisit {
 @Test void finitePrivateSurfaceDoesNotMintAnInvocationFromCompleteRecords(){assertFalse(java.lang.reflect.Modifier.isPublic(SonicCdR31aLoadingVisit.class.getModifiers()));assertEquals(1,SonicCdR31aLoadingVisit.class.getDeclaredConstructors().length);assertEquals(5,SonicCdR31aLoadingVisit.class.getDeclaredConstructors()[0].getParameterCount());assertEquals(12,Entry.class.getRecordComponents().length);assertEquals(9,Program.class.getRecordComponents().length);assertEquals(20,State.class.getRecordComponents().length);assertEquals(12,Host.class.getDeclaredMethods().length);assertTrue(Arrays.stream(NativeClearCall.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));assertFalse(Snapshot.class.isRecord());assertTrue(Arrays.stream(Snapshot.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));assertTrue(Arrays.stream(OrdinaryAccess.class.getDeclaredConstructors()).allMatch(c->java.lang.reflect.Modifier.isPrivate(c.getModifiers())));}
 @Test void constructorIsDataOnlyAndRetainsUnknownAbsoluteClock()throws Exception{
  try(var h=new Harness(3,0,0)){var s=h.core.state();assertEquals(Phase.START,s.phase());assertSame(h.entry.section(),s.section());assertThrows(com.openggf.game.soniccd.resources.CdBlockDefinitionStaging.UnknownRead.class,()->s.blockDefinitions().unsignedByte(0));assertSame(h.entry.initialization(),s.initialization());assertSame(h.entry.reserved(),s.reserved());assertTrue(h.frame.vIntClock().absoluteSeed().isEmpty());assertEquals(7,h.frame.vIntClock().deltaSinceAdmittedCut());assertTrue(h.order.isEmpty());assertFalse(h.frame.closed());assertEquals(0,known(s.section()));}
 }
 @Test void pureArtValidatorHasExactlyEighteenMethodsAndCannotSubmitSectionWork()throws Exception{
  try(var h=new Harness(3,0,0)){var a=h.art;var s=h.entry.section();a.validateState(s);assertEquals(0,s.queue().headSource());assertSame(s.codeTable(),h.entry.section().codeTable());assertEquals(0,known(s));assertThrows(NullPointerException.class,()->a.validateState(null));assertThrows(IllegalArgumentException.class,()->CdR31aSectionArt.fromAssets(h.assets).validateState(s));var stopped=assertThrows(CdR31aSectionArt.SectionArtException.class,()->a.appendList(s,19)).state();assertThrows(IllegalStateException.class,()->a.validateState(stopped));
   var names=Arrays.stream(CdR31aSectionArt.class.getDeclaredMethods()).filter(m->java.lang.reflect.Modifier.isPublic(m.getModifiers())).map(m->m.getName()+"/"+m.getParameterCount()).sorted().toList();assertEquals(List.of("appendList/2","clearCurrentPatterns/1","clearPlcs/1","decodeBlocksToRam/1","fromAssets/1","fromValues/10","identity/0","initList/2","initSection/2","loadImmediateList/2","loadLifeIcon/2","ownsAssets/1","prepare/1","serviceFast/1","serviceSlow/2","updateSection/2","validateState/1","writeCurrentTile/3"),names);
  }
 }
 @Test void startSkipBranchRetainsVersionKnowledgeButActuallyInitializesVideoAndControllers()throws Exception{
  try(var h=new Harness(3,0,0)){h.core.begin();assertEquals(Phase.FIRST_PLC,h.core.state().phase());assertFalse(h.order.contains(Action.START_SCRATCH_CLEAR));assertTrue(h.order.indexOf(Action.INIT_VDP)<h.order.indexOf(Action.INIT_CONTROLLERS));assertTrue(h.order.indexOf(Action.INIT_CONTROLLERS)<h.order.indexOf(Action.ENTRY_STORES));assertEquals(0x81,h.core.state().initialization().versionCache().value());assertEquals(0xFF,h.core.state().initialization().versionCache().knownBits());assertFalse(h.core.state().displayEnabled());assertEquals(0,h.core.state().displayedPalette().unsignedWord(0));}
 }
 @Test void doInitStoresVersionAfterActualScratchAndRequiresExplicitPlatformAtThatRead()throws Exception{
  try(var h=new Harness(3,0,0)){h.init(new CdProgramInitialization.State(CdProgramInitialization.KnownBit.CLEAR,CdProgramInitialization.MarkerKnowledge.UNKNOWN,new CdProgramInitialization.VersionCache(0,0),CdProgramInitialization.KnownBit.CLEAR),Optional.empty());var f=assertThrows(CdProgramInitialization.UnknownRead.class,h.core::begin);assertEquals(CdProgramInitialization.Read.EXECUTION_REGION,f.read());assertEquals(List.of(Action.START_SCRATCH_CLEAR),h.order);assertEquals(0,h.core.state().initialization().versionCache().knownBits());assertFalse(h.core.state().calls().contains(Call.INIT_VDP));assertTrue(h.frame.closed());}
  try(var h=new Harness(3,0,0)){h.init(new CdProgramInitialization.State(CdProgramInitialization.KnownBit.CLEAR,CdProgramInitialization.MarkerKnowledge.UNKNOWN,new CdProgramInitialization.VersionCache(0,0),CdProgramInitialization.KnownBit.CLEAR),Optional.of(new CdProgramInitialization.ExecutionRegion(0x40)));h.core.begin();assertEquals(0x40,h.core.state().initialization().versionCache().value());assertEquals(CdProgramInitialization.MarkerKnowledge.EQUALS_INIT,h.core.state().initialization().initMarker());assertEquals(CdProgramInitialization.KnownBit.SET,h.core.state().initialization().controllerSetupBit());assertTrue(h.order.indexOf(Action.VERSION_CACHE)<h.order.indexOf(Action.INIT_MARKER));}
 }
 @Test void unknownStartReadStopsBeforeAnyNativeStore()throws Exception{
  try(var h=new Harness(3,0,0)){h.init(CdProgramInitialization.unknown(),Optional.empty());var s=h.core.state();var f=assertThrows(CdProgramInitialization.UnknownRead.class,h.core::begin);assertEquals(CdProgramInitialization.Read.CONTROLLER_SETUP_BIT,f.read());assertSame(s,h.core.state());assertTrue(h.order.isEmpty());}
 }
 @Test void compulsoryFirstCPrecedesFlowerClockResetAndImmediateThreeThenOne()throws Exception{
  try(var h=new Harness(3,0,0)){h.core.begin();assertEquals(7,h.frame.vIntClock().deltaSinceAdmittedCut());h.core.service(8);assertEquals(1,h.publications.size());assertEquals(0xC,h.publications.getFirst().selector());assertTrue(h.clockAtFirstPublication.absoluteSeed().isEmpty());assertEquals(7,h.clockAtFirstPublication.deltaSinceAdmittedCut());assertEquals(0,h.frame.vIntClock().absoluteValue().orElseThrow());assertEquals(Phase.LEVEL_PLC,h.core.state().phase());var c=h.core.state().calls();assertTrue(c.indexOf(Call.FIRST_WAIT)<c.indexOf(Call.PLAY_LEVEL_MUSIC));assertTrue(c.indexOf(Call.IMMEDIATE_LEVEL)<c.indexOf(Call.IMMEDIATE_STANDARD));assertTrue(c.indexOf(Call.IMMEDIATE_STANDARD)<c.indexOf(Call.CLEAR_FLOWERS));assertSame(h.assets.blocksDecode().bytes(),h.core.state().blocksRam().orElseThrow());assertArrayEquals(h.assets.terrain().layout().ram().copy(),h.core.state().layoutRam().orElseThrow().copy());assertEquals(0x100,h.frame.environment().movement().gates().zoneAct());assertEquals(1,h.frame.environment().movement().gates().timeZone());assertEquals(0x510,h.frame.environment().movement().bounds().bottom());assertEquals(1,h.events);}
 }
 @ParameterizedTest @CsvSource({"1,false,true","3,true,false"})
 void titleBitControlsRealQueuedTwoVersusNoPlc(int flags,boolean queued,boolean title)throws Exception{
  try(var h=new Harness(flags,0,0)){h.core.begin();h.core.service(0);assertEquals(queued,h.core.state().calls().contains(Call.QUEUED_LEVEL));assertEquals(!queued,h.core.state().calls().contains(Call.NO_PLC));ready(h);assertEquals(title,h.order.contains(Action.TITLE_CARD_CREATE));assertEquals(title?0x3C:0,h.core.state().reserved().unsignedByte(192));assertEquals(title?1:0,h.frame.environment().controls().controlLocked());assertEquals(0x1C,h.core.state().reserved().unsignedByte(64));assertEquals(0x19,h.core.state().reserved().unsignedByte(30*64));assertEquals(22,h.core.state().fromFadeSteps());assertEquals(1,h.core.state().program().levelStartedByte());assertEquals(0,h.frame.state().motion().routine());assertEquals(0,h.cycleCalls);}
 }
 @Test void freshBitResetPrefixIsSourceOrderedAndDoesNotInventHistoricalKnowledge()throws Exception{
  try(var h=new Harness(0,2,1)){h.core.begin();assertEquals(0,h.core.state().level().checkpoint());assertEquals(0,h.core.state().level().spawnMode());assertEquals(1,h.core.state().level().plcLoadFlags());assertEquals(3,h.lives);assertTrue(h.order.indexOf(Action.FRESH_RESET_PREFIX)<h.order.indexOf(Action.RESET_SAVED_OBJECT_FLAGS));assertTrue(h.order.indexOf(Action.RESET_SAVED_OBJECT_FLAGS)<h.order.indexOf(Action.FRESH_RESET_SUFFIX));assertEquals(Phase.FIRST_PLC,h.core.state().phase());}
 }
 @ParameterizedTest @CsvSource({"127,0,1,3","127,1,0,1","126,0,0,3"})
 void stonesAndTimeAttackUseActualCallerBytesAtNativeEntry(int stones,int attack,int good,int lives)throws Exception{try(var h=new Harness(0,0,0,stones,attack)){h.core.begin();assertEquals(good,h.core.state().program().goodFutureByte());assertEquals(lives,h.lives);assertEquals(stones,h.core.state().program().timeStonesByte());assertEquals(attack,h.frame.environment().globals().timeAttackMode());}}
 @Test void nonzeroSpawnStopsAtRealCheckpointReadAfterLoadedBounds()throws Exception{
  try(var h=new Harness(3,1,0)){h.core.begin();var f=assertThrows(UnsupportedLoading.class,()->h.core.service(0));assertEquals(Boundary.CHECKPOINT_LOAD,f.boundary());assertTrue(h.core.state().calls().contains(Call.LEVEL_SIZE));assertFalse(h.core.state().calls().contains(Call.SETUP_CAMERA_AND_SCROLL));assertEquals(0x2E97,h.frame.environment().movement().bounds().right());assertTrue(h.core.failed());}
 }
 @ParameterizedTest @CsvSource({"2,3,NEXT_LEVEL","0,0,NO_LIVES"})
 void nativeBlackReturnClearsThenWaitsOneCBeforeRts(int restart,int lives,ReturnKind kind)throws Exception{
  try(var h=new Harness(3,0,0)){h.lives=lives;h.level(restart);h.core.begin();assertEquals(Phase.CLEAR_RETURN_WAIT,h.core.state().phase());assertEquals(0,h.core.state().level().levelRestart());assertEquals(0,h.core.state().program().palFadeFlagsByte());if(lives==0)assertEquals(0,h.core.state().level().plcLoadFlags());assertEquals(0,h.core.state().normalPalette().unsignedWord(0));h.core.service(0);assertEquals(Phase.RETURNED,h.core.state().phase());assertEquals(kind,h.core.state().returnKind());assertEquals(1,h.publications.size());assertFalse(h.order.contains(Action.PLAY_LEVEL_MUSIC));var clock=h.frame.vIntClock();assertEquals(Boundary.MMD_RETURN_OWNER,assertThrows(UnsupportedLoading.class,()->h.core.service(0)).boundary());assertSame(clock,h.frame.vIntClock());assertFalse(h.frame.closed());}
 }
 @Test void whiteRestartAndDemoRtsDoNotIssueAnInventedWait()throws Exception{
  try(var h=new Harness(3,0,0)){h.special(1);h.level(7);h.core.begin();for(int i=0;i<22;i++)h.core.service(0);assertEquals(Phase.RETURNED,h.core.state().phase());assertEquals(ReturnKind.WHITE_RESTART,h.core.state().returnKind());assertEquals(0,h.core.state().level().levelRestart());assertEquals(22,h.publications.size());assertFalse(h.order.contains(Action.PLAY_LEVEL_MUSIC));}
  try(var h=new Harness(3,0,0)){h.demo(0x800);h.core.begin();assertEquals(Phase.RETURNED,h.core.state().phase());assertEquals(ReturnKind.DEMO,h.core.state().returnKind());assertEquals(0,h.core.state().program().demoDataIndexWord());assertTrue(h.publications.isEmpty());}
 }
 @Test void normalThenWaterReadsCurrentCpuTailAndKeepsNormalPrefixOnOriginalReadFailure()throws Exception{
  try(var h=new Harness(1,0,0)){h.core.begin();toFade(h);assertEquals(Phase.FROM_BLACK,h.core.state().phase());h.badCpu=true;var water=h.core.state().waterPalette();var original=assertThrows(IllegalArgumentException.class,()->h.core.service(0));assertEquals("Complete explicit native value required",original.getMessage());assertSame(water,h.core.state().waterPalette());assertTrue(h.core.state().calls().contains(Call.FROM_NORMAL_COLOURS));assertFalse(h.core.state().calls().contains(Call.FROM_WATER_COLOURS));assertTrue(h.core.failed());}
  try(var h=new Harness(1,0,0)){h.core.begin();toFade(h);var before=h.core.state();h.core.service(0);var after=h.core.state();assertArrayEquals(CdLevelPaletteFade.stepFromBlack(before.waterPalette(),h.cpu.slice(512,128),0x202F).copy(),after.waterPalette().copy());assertArrayEquals(before.waterPalette().slice(0,32).copy(),after.waterPalette().slice(0,32).copy());assertEquals(1,h.cpuReads);assertTrue(after.calls().indexOf(Call.FROM_NORMAL_COLOURS)<after.calls().indexOf(Call.FROM_WATER_COLOURS));}
 }
 @Test void allTwentyTwoBlackOrWhiteIterationsPreserveOriginalPaletteTargets()throws Exception{
  for(boolean white:new boolean[]{false,true})try(var h=new Harness(1,0,0)){if(white)h.palClear(CdProgramInitialization.KnownBit.SET);h.core.begin();toFade(h);var fade=h.core.state().fadePalette();ready(h);assertEquals(22,h.cpuReads);assertSame(fade,h.core.state().fadePalette());assertEquals(CdProgramInitialization.KnownBit.CLEAR,h.core.state().initialization().palClearFlagBit0());assertTrue(h.core.state().displayEnabled());assertEquals(1,h.order.stream().filter(x->x==Action.ENABLE_DISPLAY).count());assertEquals(22,h.publications.stream().filter(p->p.selector()==0x12).count());}
 }
 @Test void readyWithoutRealOrdinaryOwnerStopsBeforeInputOrClockAndHealthyReentryDoesNotClose()throws Exception{
  try(var h=new Harness(1,0,0)){assertThrows(IllegalStateException.class,()->h.core.service(0));assertFalse(h.frame.closed());h.core.begin();ready(h);var shared=h.frame.captureShared();var actor=h.frame.captureActor();var s=h.core.state();assertEquals(Boundary.ORDINARY_OWNER,assertThrows(UnsupportedLoading.class,()->h.core.service(8)).boundary());assertSame(s,h.core.state());assertEquals(shared,h.frame.captureShared());assertEquals(actor,h.frame.captureActor());assertThrows(IllegalStateException.class,h.core::begin);assertFalse(h.frame.closed());}
 }
 @Test void privateOriginalPublicationActionScrollAndCpuReadScopesCannotBeReused()throws Exception{
  try(var h=new Harness(1,0,0)){h.checkClones=true;h.core.begin();h.core.service(0);assertThrows(IllegalStateException.class,()->h.core.requireAction(h,Action.INIT_VDP,h.core.state()));assertThrows(IllegalStateException.class,()->h.core.requirePublication(h,h.publications.getFirst()));assertThrows(IllegalStateException.class,()->h.core.requireScrollStore(h,h.scrollState,h.scrollState));assertThrows(IllegalStateException.class,()->h.core.requireCpuSat(h,h.core.state()));assertFalse(h.frame.closed());}
 }
 @Test void originalThrowableAndErrorRetainEarlierPrefixesAndRetireEveryScope()throws Exception{
  for(boolean error:new boolean[]{false,true})try(var h=new Harness(1,0,0)){h.core.begin();h.failAction=Action.INITIAL_BACKGROUND;h.failure=error?new AssertionError("original"):new IllegalStateException("original");assertSame(h.failure,assertThrows(Throwable.class,()->h.core.service(0)));assertTrue(h.core.state().calls().contains(Call.INITIAL_FOREGROUND));assertFalse(h.core.state().calls().contains(Call.INITIAL_BACKGROUND));assertTrue(h.core.state().blocksRam().isPresent());assertTrue(h.core.failed());assertTrue(h.frame.closed());assertThrows(IllegalStateException.class,h.core::capture);}
 }
 @Test void ownFullComponentCaptureRestoresClockScrollArtAndAllCompletedCutsWithoutEffectsReplay()throws Exception{
  try(var h=new Harness(1,0,0);var other=new Harness(1,0,0)){h.core.begin();h.core.service(0);var snap=h.core.capture();ready(h);var first=h.core.state();var clock=h.frame.vIntClock();var scroll=h.scrollState;var order=List.copyOf(h.order);h.core.restore(snap);ready(h);assertStateValue(first,h.core.state());assertEquals(clock,h.frame.vIntClock());assertScrollValue(scroll,h.scrollState);assertEquals(order,h.order);assertThrows(IllegalArgumentException.class,()->other.core.validateRestore(snap));assertFalse(other.frame.closed());}
 }
 @Test void originalOrdinaryAccessIsPrivateAndFourCyclesTouchOnlyTheirActualSlots()throws Exception{
  try(var h=new Harness(1,0,0)){final OrdinaryAccess[] saved={null};h.core.bindOrdinaryOwner(h,new OrdinaryOwner(){public void run(int held,OrdinaryAccess access){access.requireOwner(this);saved[0]=access;var timers=fill(7,1);var steps=fill(7,0x7A);var out=access.preparePaletteCycles(access.state(),timers,steps,0);h.cycleCalls++;for(int i=0;i<4;i++)assertEquals(0,out.timers().unsignedByte(i));for(int i=4;i<7;i++){assertEquals(1,out.timers().unsignedByte(i));assertEquals(0x7A,out.steps().unsignedByte(i));}var before=access.state();var stopped=access.preparePaletteCycles(before,timers,steps,1);assertSame(timers,stopped.timers());assertSame(steps,stopped.steps());assertSame(before.normalPalette(),stopped.palette());var fresh=access.preparePaletteCycles(before,fill(7,0),fill(7,255),0);assertEquals(List.of(47,60,61,63),fresh.writes().stream().map(CdLevelPaletteCycle.Write::paletteIndex).toList());assertEquals(List.of(0xEE,0xEE,0,0xEE0),fresh.writes().stream().map(CdLevelPaletteCycle.Write::afterWord).toList());assertEquals(List.of(1,1,22,1),java.util.stream.IntStream.range(0,4).map(i->fresh.timers().unsignedByte(i)).boxed().toList());assertSame(before,access.state());assertThrows(IllegalStateException.class,()->h.core.service(held));}});h.core.begin();ready(h);var clock=h.frame.vIntClock();h.core.service(8);assertEquals(1,h.cycleCalls);assertSame(clock,h.frame.vIntClock());assertThrows(IllegalStateException.class,()->saved[0].state());}
 }
 private static void assertStateValue(State a,State b){assertEquals(a.phase(),b.phase());assertEquals(a.initialization(),b.initialization());assertEquals(a.program(),b.program());assertEquals(a.level(),b.level());assertEquals(a.section(),b.section());assertEquals(a.initialFadeSteps(),b.initialFadeSteps());assertEquals(a.fromFadeSteps(),b.fromFadeSteps());assertEquals(a.displayEnabled(),b.displayEnabled());assertEquals(a.calls(),b.calls());assertEquals(a.returnKind(),b.returnKind());assertEquals(a.music(),b.music());for(var pair:List.of(List.of(a.normalPalette(),b.normalPalette()),List.of(a.fadePalette(),b.fadePalette()),List.of(a.waterPalette(),b.waterPalette()),List.of(a.displayedPalette(),b.displayedPalette()),List.of(a.reserved(),b.reserved()),List.of(a.blocksRam().orElseThrow(),b.blocksRam().orElseThrow()),List.of(a.layoutRam().orElseThrow(),b.layoutRam().orElseThrow())))assertArrayEquals(pair.get(0).copy(),pair.get(1).copy());}
 private static void assertScrollValue(Scroll a,Scroll b){assertEquals(a.foreground(),b.foreground());assertEquals(a.copies(),b.copies());assertEquals(a.foregroundXFractionWord(),b.foregroundXFractionWord());assertEquals(a.topBoundWord(),b.topBoundWord());assertEquals(a.waterDeformWord(),b.waterDeformWord());var x=a.background();var y=b.background();assertEquals(List.of(x.cameraBgX(),x.cameraBgY(),x.cameraBg2X(),x.cameraBg2Y(),x.cameraBg3X(),x.cameraBg3Y(),x.scrollFlagsBg(),x.scrollFlagsBg2(),x.scrollFlagsBg3(),x.vscroll()),List.of(y.cameraBgX(),y.cameraBgY(),y.cameraBg2X(),y.cameraBg2Y(),y.cameraBg3X(),y.cameraBg3Y(),y.scrollFlagsBg(),y.scrollFlagsBg2(),y.scrollFlagsBg3(),y.vscroll()));for(var pair:List.of(List.of(x.crossingRam(),y.crossingRam()),List.of(x.cloudRam(),y.cloudRam()),List.of(x.deformationRam(),y.deformationRam()),List.of(x.hscrollRam(),y.hscrollRam())))assertArrayEquals(pair.get(0).copy(),pair.get(1).copy());}
 private static int known(CdR31aSectionArt.State s){int n=0;for(var w:s.residency().windows())for(int i=0;i<w.byteLength();i+=32)if(s.residency().knownByte(w.destinationWord()+i).isPresent())n++;return n;}
 private static void toFade(Harness h){for(int i=0;i<512;i++){if(h.core.state().phase()==Phase.FROM_BLACK||h.core.state().phase()==Phase.FROM_WHITE)return;h.core.service(0);}fail("finite fade entry");}
 private static void ready(Harness h){for(int i=0;i<512;i++){if(h.core.state().phase()==Phase.READY)return;h.core.service(0);}fail("finite constructed load");}
 private static CdAssetBytes fill(int n,int value){byte[] b=new byte[n];Arrays.fill(b,(byte)value);return CdAssetBytes.copyOf(b);}
 @ParameterizedTest @CsvSource({"0","1"})
 void initialFadeTwelveEnablesDisplayForBothNativeColourDirections(int special)throws Exception{
  try(var h=new Harness(3,0,1)){
   retainedInitialDisplay(h,special);
   h.core.service(0);
   assertEquals(2,h.core.state().level().fadeEnableDisplay());
   assertTrue(h.core.state().displayEnabled());
   assertEquals(1,h.publications.size());
   assertEquals(0x12,h.publications.getFirst().selector());
   assertFalse(h.publications.getFirst().displayEnabled(),"Publication precedes the native display-enable store");
   assertEquals(1,h.order.stream().filter(a->a==Action.ENABLE_DISPLAY).count());
   assertEquals(1,h.core.state().initialFadeSteps());
  }
 }
 @Test void initialFadeEnableFaultRetainsPublishedAndBytePrefixes()throws Exception{
  try(var h=new Harness(3,0,1)){
   retainedInitialDisplay(h,0);
   var original=new IllegalStateException("Constructed display output fault");
   h.failAction=Action.ENABLE_DISPLAY;h.failure=original;
   assertSame(original,assertThrows(IllegalStateException.class,()->h.core.service(0)));
   assertEquals(1,h.publications.size());
   assertEquals(0x12,h.publications.getFirst().selector());
   assertEquals(2,h.core.state().level().fadeEnableDisplay(),"Native byte increment precedes display output");
   assertEquals(0,h.core.state().initialFadeSteps(),"Colour pass follows the failed V-int prefix");
   assertTrue(h.core.failed());assertTrue(h.frame.closed());
  }
 }
 /** Constructed retained pre-ClearFlowers cut; Main.asm VInt_PalFade894-907 has no initial-phase gate. */
 private static void retainedInitialDisplay(Harness h,int special)throws Exception{
  if(special!=0)h.special(special);
  var e=h.entry;var l=e.level();
  h.entry=new Entry(e.initialization(),e.executionEnvironment(),e.section(),e.program(),
   new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),1,l.powerup(),l.timeOver()),
   e.normalPalette(),e.fadePalette(),e.waterPalette(),e.displayedPalette(),e.reserved(),e.displayEnabled());
  h.reset();h.core.begin();
  assertEquals(special==0?Phase.INITIAL_TO_BLACK:Phase.INITIAL_TO_WHITE,h.core.state().phase());
  assertFalse(h.core.state().displayEnabled());
 }

 @Test void selectorTwelveRetainsTheActuallyPublishedWaterPaletteBeforeMiscClear()throws Exception{
  try(var h=new Harness(3,0,1)){
   h.waterFullscreen=0x80;h.core.begin();
   assertEquals(Phase.INITIAL_TO_BLACK,h.core.state().phase());
   h.core.service(0);
   assertEquals(1,h.waterReads);
   assertFalse(h.order.contains(Action.CLEAR_MISC));
   var p=h.publications.getFirst();assertEquals(0x12,p.selector());
   assertSame(p.waterPalette(),h.actualDisplayed);
   assertArrayEquals(h.actualDisplayed.copy(),h.core.state().displayedPalette().copy(),
    "DoVIntUpdates must retain the water image selected by the original Host byte read");
  }
 }
 @Test void selectorCUsesNormalColoursWithoutReadingTheRetainedWaterByte()throws Exception{
  try(var h=new Harness(3,0,0)){
   h.waterFullscreen=0x80;h.waterUnknown=true;h.core.begin();
   assertEquals(Phase.FIRST_PLC,h.core.state().phase());
   h.core.service(0);
   assertEquals(0,h.waterReads);
   var p=h.publications.getFirst();assertEquals(0xC,p.selector());
   assertSame(p.normalPalette(),h.actualDisplayed);
   assertArrayEquals(h.actualDisplayed.copy(),h.core.state().displayedPalette().copy());
  }
 }

 @Test void selectorCRejectsAWaterResultAfterTheOriginalNormalPublication()throws Exception{
  try(var h=new Harness(3,0,0)){
   h.publicationResult=1;h.core.begin();
   assertThrows(IllegalArgumentException.class,()->h.core.service(0));
   assertEquals(1,h.publications.size());var p=h.publications.getFirst();
   assertEquals(0xC,p.selector());assertSame(p.normalPalette(),h.actualDisplayed);
   assertEquals(0,h.waterReads);assertTrue(h.core.failed());assertTrue(h.frame.closed());
   assertThrows(IllegalStateException.class,()->h.core.requirePublication(h,p));
  }
 }
 @Test void selectorTwelveRejectsAForeignResultAndRetiresTheCompletedPublicationScope()throws Exception{
  try(var h=new Harness(3,0,1)){
   h.waterFullscreen=0x80;h.publicationResult=2;h.core.begin();
   assertThrows(IllegalArgumentException.class,()->h.core.service(0));
   assertEquals(1,h.publications.size());var p=h.publications.getFirst();
   assertEquals(0x12,p.selector());assertSame(p.waterPalette(),h.actualDisplayed);
   assertEquals(1,h.waterReads);assertTrue(h.core.failed());assertTrue(h.frame.closed());
   assertThrows(IllegalStateException.class,()->h.core.requirePublication(h,p));
  }
 }


 // BEGIN R31 ORDINARY CORE ACCESS CHECKS 20261107. Tests authored before production.
 // Actual immutable source data, constructed complete Host/Entry and genuinely issued access.
 // No Module/World/native invocation, callback completion or ordinary gameplay claim.
 @Test void ordinaryPreparedPairIsPureAndRetainsEveryNonwrite()throws Exception{
  try(var h=new Harness(1,0,0)){
   ordinaryReady(h,h,a->{var b=a.state();var actor=h.frame.captureActor();var shared=h.frame.captureShared();var external=h.capture();var n=fill(128,0x31);var d=fill(128,0x72);var slots=fill(1984,0x19);var l=new CdLevelGlobals(1,2,3,4,5,6,7,8,9);
    var candidate=a.prepare(b,b.section(),l,slots,n,d);assertSame(b,a.state());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertEquals(external,h.capture());ordinaryNonwrites(b,candidate);assertSame(b.section(),candidate.section());assertSame(l,candidate.level());assertSame(slots,candidate.reserved());assertSame(n,candidate.normalPalette());assertSame(d,candidate.displayedPalette());a.validatePrepared(b,candidate);assertSame(b,a.state());
   });h.core.service(0);
  }
 }
 @Test void ordinaryPreparedPairRequiresBothActualReferenceIdentities()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();var candidate=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());var clone=ordinaryClone(candidate);assertEquals(candidate,clone);assertNotSame(candidate,clone);assertThrows(IllegalArgumentException.class,()->a.validatePrepared(b,clone));assertThrows(IllegalArgumentException.class,()->a.validatePrepared(ordinaryClone(b),candidate));assertThrows(IllegalStateException.class,()->a.adoptAfterNative(b,clone));assertThrows(IllegalStateException.class,()->a.adoptAfterNative(ordinaryClone(b),candidate));assertSame(b,a.state());a.validatePrepared(b,candidate);a.adoptAfterNative(b,candidate);assertSame(candidate,a.state());});h.core.service(0);}
 }
 @Test void ordinaryNewPreparationRetiresOnlyThePreviousCandidate()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();var first=a.prepare(b,b.section(),b.level(),b.reserved(),fill(128,1),b.displayedPalette());var second=a.prepare(b,b.section(),b.level(),b.reserved(),fill(128,2),b.displayedPalette());assertThrows(IllegalArgumentException.class,()->a.validatePrepared(b,first));assertThrows(IllegalStateException.class,()->a.adoptAfterNative(b,first));a.validatePrepared(b,second);a.adoptAfterNative(b,second);assertEquals(2,a.state().normalPalette().unsignedByte(0));});h.core.service(0);}
 }
 @ParameterizedTest @CsvSource({"1983,128,128","1985,128,128","1984,127,128","1984,129,128","1984,128,127","1984,128,129"})
 void ordinaryPreparationRejectsEveryIncompleteAggregateWithoutLosingThePriorPair(int reserved,int normal,int displayed)throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());assertThrows(IllegalArgumentException.class,()->a.prepare(b,b.section(),b.level(),fill(reserved,1),fill(normal,2),fill(displayed,3)));assertSame(b,a.state());a.validatePrepared(b,ready);a.adoptAfterNative(b,ready);});h.core.service(0);}
 }
 @Test void ordinaryPreparationRejectsEveryNullAggregateBeforeReplacingItsPendingPair()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());assertThrows(NullPointerException.class,()->a.prepare(b,null,b.level(),b.reserved(),b.normalPalette(),b.displayedPalette()));assertThrows(NullPointerException.class,()->a.prepare(b,b.section(),null,b.reserved(),b.normalPalette(),b.displayedPalette()));assertThrows(NullPointerException.class,()->a.prepare(b,b.section(),b.level(),null,b.normalPalette(),b.displayedPalette()));assertThrows(NullPointerException.class,()->a.prepare(b,b.section(),b.level(),b.reserved(),null,b.displayedPalette()));assertThrows(NullPointerException.class,()->a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),null));assertSame(b,a.state());a.validatePrepared(b,ready);});h.core.service(0);}
 }
 @Test void ordinaryPureSectionValidationRejectsForeignAndStoppedOwnersWithoutCallingInitSection()throws Exception{
  try(var h=new Harness(1,0,0)){
   var foreign=CdR31aSectionArt.fromAssets(h.assets);var e=h.entry.section();var other=foreign.fromValues(e.codeTable(),e.residency(),e.sectionWord(),e.queue(),e.decoder(),e.savedDecoder(),e.remainingTilesWord(),e.processBudgetWord(),e.hintWord(),e.padWord());
   var stopped=assertThrows(CdR31aSectionArt.SectionArtException.class,()->h.art.appendList(e,19)).state();
   ordinaryReady(h,h,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());assertThrows(IllegalArgumentException.class,()->a.prepare(b,other,b.level(),b.reserved(),b.normalPalette(),b.displayedPalette()));assertThrows(IllegalStateException.class,()->a.prepare(b,stopped,b.level(),b.reserved(),b.normalPalette(),b.displayedPalette()));assertSame(b,a.state());assertEquals(0x005A,ready.section().sectionWord());assertSame(b.section(),ready.section());assertSame(b.section().queue(),ready.section().queue());a.validatePrepared(b,ready);});h.core.service(0);
  }
 }
 @Test void ordinaryAdoptionWithoutTheOriginalPrebuildIsANonwrite()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();assertThrows(IllegalArgumentException.class,()->a.validatePrepared(b,b));assertThrows(IllegalStateException.class,()->a.adoptAfterNative(b,b));assertSame(b,a.state());});h.core.service(0);}
 }
 @Test void ordinaryAlreadyAdoptedPairCannotBeAppliedTwice()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());a.validatePrepared(b,ready);a.adoptAfterNative(b,ready);assertSame(ready,a.state());assertThrows(IllegalStateException.class,()->a.adoptAfterNative(b,ready));assertThrows(IllegalArgumentException.class,()->a.validatePrepared(ready,ready));assertSame(ready,a.state());});h.core.service(0);}
 }
 @Test void ordinaryOriginalAccessAndPendingPairExpireAtTheActualCallbackReturn()throws Exception{
  try(var h=new Harness(1,0,0)){var access=new OrdinaryAccess[1];var pair=new State[2];ordinaryReady(h,h,a->{access[0]=a;pair[0]=a.state();pair[1]=a.prepare(pair[0],pair[0].section(),pair[0].level(),pair[0].reserved(),pair[0].normalPalette(),pair[0].displayedPalette());});h.core.service(0);var before=h.core.state();assertThrows(IllegalStateException.class,()->access[0].validatePrepared(pair[0],pair[1]));assertThrows(IllegalStateException.class,()->access[0].adoptAfterNative(pair[0],pair[1]));assertThrows(IllegalStateException.class,()->access[0].preparePlc(before));assertThrows(IllegalStateException.class,()->access[0].serviceSlowPlc(before,0));assertThrows(IllegalStateException.class,()->access[0].updateSection(before,0));assertSame(before,h.core.state());assertFalse(h.frame.closed());}
 }
 @Test void ordinaryPreparationCannotSurviveAGenuineInterveningTitleQueueWrite()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());a.appendTitlePlc(b);var current=a.state();assertNotSame(b,current);assertEquals(Call.TITLE_PLC_APPEND,current.calls().getLast());assertThrows(IllegalArgumentException.class,()->a.validatePrepared(b,ready));assertThrows(IllegalArgumentException.class,()->a.validatePrepared(current,ready));assertThrows(IllegalStateException.class,()->a.adoptAfterNative(b,ready));assertSame(current,a.state());});h.core.service(0);}
 }
 @Test void ordinaryPostNativeAdoptionPerformsNoHostCallbackOrFrameRead()throws Exception{
  try(var h=new Harness(1,0,0)){var probe=ordinaryProbe(h);ordinaryReady(h,probe,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),fill(128,0x53),b.displayedPalette());a.validatePrepared(b,ready);int callbacks=probe.callbacks;probe.forbid=true;try{a.adoptAfterNative(b,ready);}finally{probe.forbid=false;}assertEquals(callbacks,probe.callbacks);assertSame(ready,a.state());assertEquals(0x53,a.state().normalPalette().unsignedByte(0));});h.core.service(0);}
 }
 @Test void ordinaryHostValidationReentryRejectsBeforeChangingThePreparedCut()throws Exception{
  try(var h=new Harness(1,0,0)){var probe=ordinaryProbe(h);ordinaryReady(h,probe,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());probe.onValidate=()->assertThrows(IllegalStateException.class,()->a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette()));try{a.validatePrepared(b,ready);}finally{probe.onValidate=null;}assertSame(b,a.state());a.validatePrepared(b,ready);a.adoptAfterNative(b,ready);});h.core.service(0);}
 }
 @Test void ordinaryPlcPreparationUsesTheGenuinePendingSourceAndPreservesFrameAndHost()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();ordinaryInstall(a,b,ordinaryQueued(h,b.section(),h.assets.loadMetadata().source(CdR31aLoadMetadata.SourceId.RING_GFX).nativeAddress(),0xF5C0));b=a.state();var actor=h.frame.captureActor();var shared=h.frame.captureShared();var external=h.capture();var before=b.section();a.preparePlc(b);var after=a.state();assertEquals(14,after.section().remainingTilesWord());assertTrue(after.section().decoder().isPresent());assertNotSame(before.codeTable(),after.section().codeTable());assertSame(before.residency(),after.section().residency());assertEquals(0xF5C0,after.section().queue().headDestination());ordinaryNonwrites(b,after);assertSame(b.level(),after.level());assertSame(b.reserved(),after.reserved());assertSame(b.normalPalette(),after.normalPalette());assertSame(b.displayedPalette(),after.displayedPalette());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertEquals(external,h.capture());});h.core.service(0);}
 }
 @ParameterizedTest @CsvSource({"0,11,63008,0","255,11,63008,0","256,0,0,5","65535,0,0,5"})
 void ordinarySlowPlcReadsTheWholeNativeScrollLockWord(int lock,int remaining,int destination,int finalBudget)throws Exception{
  try(var h=new Harness(1,0,0)){var decoded=h.assets.loadMetadata().decoded(CdR31aLoadMetadata.SourceId.RING_GFX).bytes();ordinaryReady(h,h,a->{var b=a.state();ordinaryInstall(a,b,ordinaryQueued(h,b.section(),h.assets.loadMetadata().source(CdR31aLoadMetadata.SourceId.RING_GFX).nativeAddress(),0xF5C0));a.preparePlc(a.state());var prepared=a.state();var expected=h.art.serviceSlow(prepared.section(),lock);var actor=h.frame.captureActor();var shared=h.frame.captureShared();int calls=h.order.size();a.serviceSlowPlc(prepared,lock);var after=a.state();assertEquals(expected,after.section());assertEquals(remaining,after.section().remainingTilesWord());assertEquals(destination,after.section().queue().headDestination());assertEquals(finalBudget,after.section().processBudgetWord());assertEquals(calls+1,h.order.size());assertEquals(Action.CURRENT_ART,h.order.getLast());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());for(int i=0;i<(lock>>>8==0?3:14);i++)assertArrayEquals(decoded.slice(i*32,32).copy(),CdR31aArtResidency.readTile(after.section().residency(),0xF5C0/32+i).copy());});h.core.service(0);}
 }
 @Test void ordinaryIdlePlcServicesDoNotPublishCurrentArtOrChangeDecoderFields()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();assertEquals(0,b.section().remainingTilesWord());assertEquals(0,b.section().queue().headSource());int count=h.order.size();a.serviceSlowPlc(b,0xFFFF);var slow=a.state();assertSame(b.section(),slow.section());a.preparePlc(slow);assertSame(slow.section(),a.state().section());assertEquals(count,h.order.size());});h.core.service(0);}
 }
 @ParameterizedTest @CsvSource({"0,0,7","1183,0,7","1184,2,8","4096,4,9","6144,6,10","65534,6,10"})
 void ordinarySectionUpdateUsesActualR31RangesAndInitPlcsInsteadOfAnR13Rts(int camera,int selector,int plc)throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();var frame=h.frame.captureActor();var shared=h.frame.captureShared();int calls=h.order.size();boolean sameSelector=(b.section().sectionWord()>>>8)==selector;var expected=sameSelector?b.section().queue():h.art.initList(b.section(),plc).queue();a.updateSection(b,camera);var changed=a.state();assertEquals((selector<<8)|0x5A,changed.section().sectionWord());if(sameSelector)assertSame(b.section(),changed.section());assertEquals(expected,changed.section().queue());assertEquals(CdR11aSectionArt.Writer.CLEARED,changed.section().savedDecoder().writer());assertSame(b.section().codeTable(),changed.section().codeTable());assertSame(b.section().residency(),changed.section().residency());assertEquals(calls,h.order.size());assertEquals(frame,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());var section=changed.section();a.updateSection(changed,camera);assertSame(section,a.state().section());});h.core.service(0);}
 }
 @Test void ordinaryNativeWordInputsRejectBeforeAnyCoreOrArtWrite()throws Exception{
  try(var h=new Harness(1,0,0)){ordinaryReady(h,h,a->{var b=a.state();for(int raw:new int[]{-1,65536,Integer.MIN_VALUE,Integer.MAX_VALUE}){assertThrows(IllegalArgumentException.class,()->a.serviceSlowPlc(b,raw));assertThrows(IllegalArgumentException.class,()->a.updateSection(b,raw));assertSame(b,a.state());}assertThrows(IllegalArgumentException.class,()->a.preparePlc(ordinaryClone(b)));assertSame(b,a.state());});h.core.service(0);}
 }
 @Test void ordinarySectionReadFaultKeepsTheGenuineStoppedPrefixAndRetiresTheAccess()throws Exception{
  try(var h=new Harness(1,0,0)){var before=new State[1];var issued=new OrdinaryAccess[1];ordinaryReady(h,h,a->{before[0]=a.state();issued[0]=a;a.updateSection(before[0],65535);});var original=assertThrows(CdR31aSectionArt.SectionArtException.class,()->h.core.service(0));assertEquals(CdR31aSectionArt.Reason.SECTION_TABLE,original.reason());assertSame(original.state(),h.core.state().section());assertTrue(original.state().stopped());assertEquals(before[0].section().sectionWord(),original.state().sectionWord());assertSame(before[0].section().queue(),original.state().queue());assertSame(before[0].section().residency(),original.state().residency());assertEquals(Action.CURRENT_ART,h.order.getLast());assertTrue(h.core.failed());assertTrue(h.frame.closed());assertThrows(IllegalStateException.class,()->issued[0].state());}
 }
 @Test void ordinaryUnknownPlcSourceFaultRetainsItsActualDescriptorAndOriginalException()throws Exception{
  try(var h=new Harness(1,0,0)){var observed=new Throwable[1];var queued=new CdR31aSectionArt.State[1];ordinaryReady(h,h,a->{var b=a.state();queued[0]=ordinaryQueued(h,b.section(),0x200100,0xF5C0);ordinaryInstall(a,b,queued[0]);try{a.preparePlc(a.state());}catch(CdR31aSectionArt.SectionArtException failure){observed[0]=failure;throw failure;}});var original=assertThrows(CdR31aSectionArt.SectionArtException.class,()->h.core.service(0));assertSame(observed[0],original);assertEquals(CdR31aSectionArt.Reason.UNKNOWN_SOURCE,original.reason());assertEquals(0x200100L,original.sourcePointer());assertSame(queued[0].queue(),original.state().queue());assertSame(original.state(),h.core.state().section());assertTrue(h.core.failed());assertTrue(h.frame.closed());}
 }
 @Test void ordinarySlowResidencyFaultKeepsPreadvanceAndSuppressesTheFailedCurrentPublication()throws Exception{
  try(var h=new Harness(1,0,0)){var before=new State[1];var expected=new AssertionError("original CURRENT_ART publication");ordinaryReady(h,h,a->{var b=a.state();ordinaryInstall(a,b,ordinaryQueued(h,b.section(),h.assets.loadMetadata().source(CdR31aLoadMetadata.SourceId.RING_GFX).nativeAddress(),0x56C0));a.preparePlc(a.state());before[0]=a.state();h.failAction=Action.CURRENT_ART;h.failure=expected;a.serviceSlowPlc(before[0],0);});var original=assertThrows(CdR31aSectionArt.SectionArtException.class,()->h.core.service(0));assertEquals(CdR31aSectionArt.Reason.RESIDENCY,original.reason());assertEquals(List.of(expected),Arrays.asList(original.getSuppressed()));assertSame(original.state(),h.core.state().section());assertEquals(0x5720,original.state().queue().headDestination());assertEquals(14,original.state().remainingTilesWord());assertEquals(3,original.state().processBudgetWord());assertSame(before[0].section().residency(),original.state().residency());assertSame(before[0].section().savedDecoder(),original.state().savedDecoder());assertTrue(h.core.failed());assertTrue(h.frame.closed());}
 }
 private static void ordinaryReady(Harness h,Host binding,java.util.function.Consumer<OrdinaryAccess> operation)throws Exception{
  h.core.bindOrdinaryOwner(binding,new OrdinaryOwner(){public void run(int held,OrdinaryAccess access){access.requireOwner(this);operation.accept(access);}});h.core.begin();ready(h);
 }
 private static void ordinaryInstall(OrdinaryAccess access,State before,CdR31aSectionArt.State section){var ready=access.prepare(before,section,before.level(),before.reserved(),before.normalPalette(),before.displayedPalette());access.validatePrepared(before,ready);access.adoptAfterNative(before,ready);}
 private static CdR31aSectionArt.State ordinaryQueued(Harness h,CdR31aSectionArt.State before,long source,int destination){var queue=CdR11aPlcBuffer.replace(before.queue(),List.of(new CdR11aPlcBuffer.Entry(source,destination)));return h.art.fromValues(before.codeTable(),before.residency(),before.sectionWord(),queue,Optional.empty(),before.savedDecoder(),0,before.processBudgetWord(),before.hintWord(),before.padWord());}
 @Test void legacyEighteenComponentStateDoesNotBorrowTheOriginalClearedPositionBytes()throws Exception{
  try(var h=new Harness(1,0,0)){
   ordinaryReady(h,h,a->{var s=a.state();assertTrue(s.flowerPositions().clearedByOriginal());assertEquals(768,s.flowerPositions().knownBytes());
    var legacy=new State(s.phase(),s.initialization(),s.section(),s.program(),s.level(),s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),s.reserved(),s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),s.calls(),s.returnKind(),s.music());
    assertEquals(0,legacy.flowerPositions().knownBytes());assertFalse(legacy.flowerPositions().clearedByOriginal());assertThrows(CdFlowerPositions.Unavailable.class,()->legacy.flowerPositions().word(0));assertNotEquals(s,legacy);assertSame(s,a.state());
   });h.core.service(0);
  }
 }
 private static State ordinaryClone(State s){return new State(s.phase(),s.initialization(),s.section(),s.program(),s.level(),s.normalPalette(),s.fadePalette(),s.waterPalette(),s.displayedPalette(),s.reserved(),s.blocksRam(),s.layoutRam(),s.initialFadeSteps(),s.fromFadeSteps(),s.displayEnabled(),s.calls(),s.returnKind(),s.music(),s.flowerPositions(),s.blockDefinitions());}
 private static void ordinaryNonwrites(State before,State after){assertSame(before.phase(),after.phase());assertSame(before.initialization(),after.initialization());assertSame(before.program(),after.program());assertSame(before.fadePalette(),after.fadePalette());assertSame(before.waterPalette(),after.waterPalette());assertSame(before.blocksRam(),after.blocksRam());assertSame(before.layoutRam(),after.layoutRam());assertEquals(before.initialFadeSteps(),after.initialFadeSteps());assertEquals(before.fromFadeSteps(),after.fromFadeSteps());assertEquals(before.displayEnabled(),after.displayEnabled());assertEquals(before.calls(),after.calls());assertSame(before.returnKind(),after.returnKind());assertSame(before.music(),after.music());assertSame(before.flowerPositions(),after.flowerPositions());}
 private static OrdinaryHostProbe ordinaryProbe(Harness h)throws Exception{var probe=new OrdinaryHostProbe(h);h.core=new SonicCdR31aLoadingVisit(probe,h.assets,h.frame,h.art,h.entry);return probe;}
 private static final class OrdinaryHostProbe implements Host{
  private final Harness delegate;private boolean forbid;private int callbacks;private Runnable onValidate;
  private OrdinaryHostProbe(Harness delegate){this.delegate=delegate;}
  private void called(){callbacks++;if(forbid)throw new AssertionError("Post-native adoption called Host");}
  public void validate(SonicCdR31aLoadingVisit core,CdPlayerFrameRuntime frame,CdR31aAssets assets,CdR31aSectionArt art){called();if(onValidate!=null)onValidate.run();delegate.validate(core,frame,assets,art);}
  public int livesByte(){called();return delegate.livesByte();}public Scroll scroll(){called();return delegate.scroll();}
  public void storeScroll(Scroll before,Scroll ready){called();delegate.core.requireScrollStore(this,before,ready);assertSame(delegate.scrollState,before);delegate.scrollState=ready;}
  public void events(State state){called();delegate.core.requireEvents(this,state);assertEquals(0,delegate.scrollState.foreground().scrollFlags());assertEquals(0x510,delegate.scrollState.foreground().destBottomBound());delegate.events++;}
  public CdAssetBytes cpuSat(State state){called();delegate.core.requireCpuSat(this,state);delegate.cpuReads++;return delegate.badCpu?fill(512,0):delegate.cpu;}
  public void clear(NativeClearCall c){called();c.requireOwner(this);act(c.action(),c.before());delegate.scrollState=c.scroll();c.applyNative();}
  public void act(Action action,State state){called();delegate.core.requireAction(this,action,state);delegate.order.add(action);if(action==Action.CLEAR_DISPLAY_PALETTE)delegate.actualDisplayed=state.displayedPalette();if(action==Action.CLEAR_MISC)delegate.waterFullscreen=0;if(action==Action.FRESH_RESET_SUFFIX)delegate.lives=delegate.frame.environment().globals().timeAttackMode()==0?3:1;if(action==Action.TITLE_CARD_CREATE)delegate.frame.applyPreparedObjectSolidPublication(delegate.frame.prepareObjectTitleCardGlobal(delegate.frame.state(),delegate.frame.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1));}
  public CdAssetBytes publish(Publication publication){called();delegate.core.requirePublication(this,publication);if(delegate.clockAtFirstPublication==null)delegate.clockAtFirstPublication=delegate.frame.vIntClock();CdAssetBytes selected=publication.selector()==0xC||delegate.waterFullscreen==0?publication.normalPalette():publication.waterPalette();delegate.actualDisplayed=selected;delegate.publications.add(publication);return selected;}
  public Object capture(){called();return delegate.capture();}public void validateRestore(Object snapshot){called();delegate.validateRestore(snapshot);}public void restore(Object snapshot){called();delegate.restore(snapshot);}
 }
 // END R31 ORDINARY CORE ACCESS CHECKS 20261107.


 // BEGIN R31 ORDINARY NATIVE HUD LANE CHECKS 20261107. Tests FIRST, explicit component values.
 @Test void ordinaryNativeHudLaneKeepsTheFacadePrivateAndLazilyCached()throws Exception{
  assertFalse(java.lang.reflect.Modifier.isPublic(OrdinaryAccess.class.getDeclaredMethod("runHud",State.class,CdLevelHud.R31State.class,CdLevelHud.Inputs.class).getModifiers()));
  assertTrue(java.lang.reflect.Modifier.isPrivate(OrdinaryAccess.class.getDeclaredMethod("retainHud",CdLevelHud.R31State.class).getModifiers()));
  var field=SonicCdR31aLoadingVisit.class.getDeclaredField("hud");assertTrue(java.lang.reflect.Modifier.isPrivate(field.getModifiers()));assertEquals(CdLevelHud.R31.class,field.getType());
  try(var h=new Harness(1,0,0)){assertNull(coreHudCache(h));coreHudProfile(h);ordinaryReady(h,h,a->{assertNull(coreHudCache(h));var b=a.state();var input=coreHudInput(b,0,0,0,CdCheckpointLiveAux.unknown(),0);var result=a.runHud(b,input,coreHudInputs(1,1));assertSame(input,result.state());assertTrue(result.calls().isEmpty());assertNotNull(coreHudCache(h));});h.core.service(0);}
 }
 @Test void ordinaryNativeHudAdoptsActualGlyphsAndOnlyItsCorePair()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();assertEquals(1,b.level().updateHudTime());var actor=h.frame.captureActor();var shared=h.frame.captureShared();var external=(Harness.Image)h.capture();var life=CdR31aArtResidency.readTile(b.section().residency(),0xB420/32);var input=coreHudInput(b,0x80,0x80,1,CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0),1234);var result=a.runHud(b,input,coreHudInputs(0,1));var after=a.state();assertSame(result.state().section(),after.section());assertSame(result.state().level(),after.level());coreHudNonwrites(b,after);assertNotSame(b.section().residency(),after.section().residency());assertEquals(0,result.state().score().updateHudScore());assertEquals(0,result.state().ring().updateHudRings());assertEquals(0,result.state().ring().updateHudLives());assertEquals(0,CdCheckpointLiveAux.read(result.state().liveTime(),CdCheckpointLiveAux.Field.TIME_LONG));assertEquals(List.of(CdLevelHud.NativeCall.DRAW_LIVES,CdLevelHud.NativeCall.RESET_SCORE,CdLevelHud.NativeCall.DRAW_SCORE,CdLevelHud.NativeCall.RESET_RINGS,CdLevelHud.NativeCall.DRAW_RINGS,CdLevelHud.NativeCall.DRAW_MINS,CdLevelHud.NativeCall.DRAW_SECS,CdLevelHud.NativeCall.DRAW_SECS,CdLevelHud.NativeCall.DRAW_LIVES),result.calls());for(var cell:List.of(new int[]{0xB4A0,3},new int[]{0xB0E0,1},new int[]{0xB120,2},new int[]{0xB160,3},new int[]{0xB1A0,4},new int[]{0xB3A0,1},new int[]{0xB3E0,6},new int[]{0xB220,0}))coreHudGlyph(h,result.state(),cell[0],cell[1]);assertArrayEquals(life.copy(),CdR31aArtResidency.readTile(after.section().residency(),0xB420/32).copy());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());coreHudHostNonwrites(external,(Harness.Image)h.capture());assertEquals(external.order().size()+1,h.order.size());assertEquals(Action.CURRENT_ART,h.order.getLast());});h.core.service(0);}
 }
 @Test void ordinaryNativeHudReturnsExternalTimeWithoutAdoptingItIntoFrame()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();var actor=h.frame.captureActor();var shared=h.frame.captureShared();var time=CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0xAA003B3B);var input=coreHudInput(b,0,0,0,time,0);var result=a.runHud(b,input,coreHudInputs(0,0));assertEquals(0xAA010000,CdCheckpointLiveAux.read(result.state().liveTime(),CdCheckpointLiveAux.Field.TIME_LONG));assertEquals(0xAA003B3B,CdCheckpointLiveAux.read(time,CdCheckpointLiveAux.Field.TIME_LONG));assertEquals(List.of(CdLevelHud.NativeCall.DRAW_MINS,CdLevelHud.NativeCall.DRAW_SECS,CdLevelHud.NativeCall.DRAW_SECS),result.calls());assertSame(result.state().section(),a.state().section());assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());});h.core.service(0);}
 }
 @Test void ordinaryNativeHudRejectsEqualButNoncurrentSectionAndLevelBeforeWork()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();var section=b.section();var clone=h.art.fromValues(section.codeTable(),section.residency(),section.sectionWord(),section.queue(),section.decoder(),section.savedDecoder(),section.remainingTilesWord(),section.processBudgetWord(),section.hintWord(),section.padWord());assertEquals(section,clone);var input=coreHudInput(b,0x80,0x80,1,CdCheckpointLiveAux.unknown(),1234);var foreign=new CdLevelHud.R31State(clone,input.ring(),input.level(),input.score(),input.liveTime(),input.bonus());var l=b.level();var level=new CdLevelGlobals(l.levelRestart(),l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver());assertEquals(l,level);var wrongLevel=new CdLevelHud.R31State(input.section(),input.ring(),level,input.score(),input.liveTime(),input.bonus());int actions=h.order.size();assertThrows(IllegalArgumentException.class,()->a.runHud(b,foreign,coreHudInputs(0,1)));assertThrows(IllegalArgumentException.class,()->a.runHud(b,wrongLevel,coreHudInputs(0,1)));assertSame(b,a.state());assertEquals(actions,h.order.size());assertNull(coreHudCache(h));});h.core.service(0);}
 }
 @Test void ordinaryNativeHudRejectsStaleExpectedStateBeforeBorrowingSource()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();var input=coreHudInput(b,0x80,0x80,1,CdCheckpointLiveAux.unknown(),1234);var ready=a.prepare(b,b.section(),b.level(),b.reserved(),fill(128,0x42),b.displayedPalette());a.validatePrepared(b,ready);a.adoptAfterNative(b,ready);int actions=h.order.size();assertThrows(IllegalArgumentException.class,()->a.runHud(b,input,coreHudInputs(0,1)));assertSame(ready,a.state());assertEquals(actions,h.order.size());assertNull(coreHudCache(h));});h.core.service(0);}
 }
 @Test void ordinaryNativeHudRejectsNullInputsWithoutChangingTheCurrentPair()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();var input=coreHudInput(b,0,0,0,CdCheckpointLiveAux.unknown(),0);assertThrows(NullPointerException.class,()->a.runHud(b,null,coreHudInputs(1,1)));assertThrows(NullPointerException.class,()->a.runHud(b,input,null));assertSame(b,a.state());assertNull(coreHudCache(h));});h.core.service(0);}
 }
 @Test void ordinaryNativeHudReusesOnlyItsSourceBoundFacadeAcrossActualCalls()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();var first=a.runHud(b,coreHudInput(b,0,0,0,CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0),0),coreHudInputs(0,1));var cached=coreHudCache(h);assertNotNull(cached);var current=a.state();var second=a.runHud(current,coreHudInput(current,0,0,0,first.state().liveTime(),0),coreHudInputs(0,1));assertSame(cached,coreHudCache(h));assertSame(second.state().section(),a.state().section());assertEquals(first.calls(),second.calls());});h.core.service(0);}
 }
 @Test void ordinaryNativeHudMutationMakesAnEarlierPreparedCoreCandidateStale()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();var ready=a.prepare(b,b.section(),b.level(),b.reserved(),b.normalPalette(),b.displayedPalette());a.runHud(b,coreHudInput(b,0,0,0,CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0),0),coreHudInputs(0,1));var current=a.state();assertNotSame(b,current);assertThrows(IllegalArgumentException.class,()->a.validatePrepared(b,ready));assertThrows(IllegalStateException.class,()->a.adoptAfterNative(b,ready));assertSame(current,a.state());});h.core.service(0);}
 }
 @Test void ordinaryNativeHudUnknownTimeKeepsItsCompletedGlyphPrefixBeforeTerminalClosure()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);var before=new State[1];var input=new CdLevelHud.R31State[1];var issued=new OrdinaryAccess[1];ordinaryReady(h,h,a->{before[0]=a.state();issued[0]=a;input[0]=coreHudInput(before[0],0x80,0x80,1,CdCheckpointLiveAux.unknown(),1234);a.runHud(before[0],input[0],coreHudInputs(0,1));});var original=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->h.core.service(0));assertEquals(CdLevelHud.Boundary.LIVE_TIME,original.boundary());assertSame(original.state().section(),h.core.state().section());assertSame(original.state().level(),h.core.state().level());assertNotSame(before[0].section().residency(),h.core.state().section().residency());assertEquals(0,original.state().score().updateHudScore());assertEquals(0,original.state().ring().updateHudRings());assertEquals(1,original.state().ring().updateHudLives());assertSame(input[0].liveTime(),original.state().liveTime());assertEquals(List.of(CdLevelHud.NativeCall.DRAW_LIVES,CdLevelHud.NativeCall.RESET_SCORE,CdLevelHud.NativeCall.DRAW_SCORE,CdLevelHud.NativeCall.RESET_RINGS,CdLevelHud.NativeCall.DRAW_RINGS),original.calls());coreHudGlyph(h,original.state(),0xB4A0,3);assertEquals(Action.CURRENT_ART,h.order.getLast());coreHudNonwrites(before[0],h.core.state());assertTrue(h.core.failed());assertTrue(h.frame.closed());assertThrows(IllegalStateException.class,()->issued[0].runHud(h.core.state(),input[0],coreHudInputs(0,1)));}
 }
 @Test void ordinaryNativeHudMaxTimeKeepsTheNewLevelBeforeTheUnownedKill()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);var before=new State[1];var actions=new int[1];ordinaryReady(h,h,a->{before[0]=a.state();actions[0]=h.order.size();a.runHud(before[0],coreHudInput(before[0],0,0,1,CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0x00093B3B),0),coreHudInputs(0,0));});var original=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->h.core.service(0));assertEquals(CdLevelHud.Boundary.MAX_TIME_KILL,original.boundary());assertEquals(0,original.state().level().updateHudTime());assertSame(original.state().level(),h.core.state().level());assertNotSame(before[0].level(),h.core.state().level());assertSame(before[0].section(),h.core.state().section());assertEquals(before[0].level().timeOver(),h.core.state().level().timeOver());assertEquals(0,CdCheckpointLiveAux.read(original.state().liveTime(),CdCheckpointLiveAux.Field.TIME_LONG));assertEquals(actions[0],h.order.size());assertTrue(h.core.failed());assertTrue(h.frame.closed());}
 }
 @Test void ordinaryNativeHudOldProfileStopsAtFirstLivesGlyphAndKeepsTheOriginalArtFailure()throws Exception{
  try(var h=new Harness(1,0,0)){var before=new State[1];var actions=new int[1];ordinaryReady(h,h,a->{before[0]=a.state();assertFalse(before[0].section().residency().hasNativeHudProfile());actions[0]=h.order.size();a.runHud(before[0],coreHudInput(before[0],0x80,0x80,1,CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0),1234),coreHudInputs(0,1));});var carrier=assertThrows(CdLevelHud.R31ArtFailure.class,()->h.core.service(0));assertSame(carrier.original(),carrier.getCause());assertEquals(CdR31aSectionArt.Reason.RESIDENCY,carrier.original().reason());assertEquals(0xB4A0,carrier.original().destinationByteOffset());assertSame(carrier.original().state(),carrier.state().section());assertSame(carrier.state().section(),h.core.state().section());assertTrue(h.core.state().section().stopped());assertSame(before[0].section().residency(),h.core.state().section().residency());assertSame(before[0].level(),h.core.state().level());assertTrue(carrier.calls().isEmpty());assertEquals(0x80,carrier.state().score().updateHudScore());assertEquals(actions[0],h.order.size());assertTrue(h.core.failed());assertTrue(h.frame.closed());}
 }
 @ParameterizedTest @CsvSource({"false","true"})
 void ordinaryNativeHudSuppressesFailedCurrentPublicationOnTheOriginalUnsupportedBoundary(boolean error)throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);Throwable publication=error?new AssertionError("original HUD CURRENT_ART error"):new IllegalStateException("original HUD CURRENT_ART failure");var originalSeen=new CdLevelHud.UnsupportedR31Hud[1];ordinaryReady(h,h,a->{var b=a.state();h.failAction=Action.CURRENT_ART;h.failure=publication;try{a.runHud(b,coreHudInput(b,0x80,0x80,1,CdCheckpointLiveAux.unknown(),1234),coreHudInputs(0,1));}catch(CdLevelHud.UnsupportedR31Hud original){originalSeen[0]=original;throw original;}});var original=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->h.core.service(0));assertSame(originalSeen[0],original);assertEquals(CdLevelHud.Boundary.LIVE_TIME,original.boundary());assertEquals(List.of(publication),Arrays.asList(original.getSuppressed()));assertSame(original.state().section(),h.core.state().section());assertSame(original.state().level(),h.core.state().level());assertEquals(0,original.state().score().updateHudScore());assertTrue(h.core.failed());assertTrue(h.frame.closed());}
 }
 @Test void ordinaryNativeHudSourceFontBoundaryWithoutTilesDoesNotPublishCurrentArt()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);var before=new State[1];var actions=new int[1];ordinaryReady(h,h,a->{before[0]=a.state();actions[0]=h.order.size();a.runHud(before[0],coreHudInput(before[0],1,0,0,CdCheckpointLiveAux.unknown(),1600000),coreHudInputs(1,0));});var original=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->h.core.service(0));assertEquals(CdLevelHud.Boundary.FONT_ADDRESS,original.boundary());assertEquals(1024,original.sourceOffset());assertEquals(0xB060,original.destinationWord());assertEquals(0,original.state().score().updateHudScore());assertSame(before[0].section(),h.core.state().section());assertEquals(actions[0],h.order.size());assertTrue(h.core.failed());assertTrue(h.frame.closed());}
 }
 @Test void ordinaryNativeHudNoTileReturnDoesNotPublishCurrentArtOrInventTime()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);ordinaryReady(h,h,a->{var b=a.state();var input=coreHudInput(b,0,0,0,CdCheckpointLiveAux.unknown(),0);int actions=h.order.size();var result=a.runHud(b,input,coreHudInputs(1,1));assertSame(input,result.state());assertTrue(result.calls().isEmpty());assertSame(b.section(),a.state().section());assertSame(b.level(),a.state().level());assertSame(input.liveTime(),result.state().liveTime());assertEquals(actions,h.order.size());coreHudNonwrites(b,a.state());});h.core.service(0);}
 }
 @Test void ordinaryNativeHudAccessCannotBeReplayedAfterTheOwnerReturns()throws Exception{
  try(var h=new Harness(1,0,0)){coreHudProfile(h);var access=new OrdinaryAccess[1];var input=new CdLevelHud.R31State[1];ordinaryReady(h,h,a->{access[0]=a;var b=a.state();input[0]=coreHudInput(b,0,0,0,CdCheckpointLiveAux.unknown(),0);a.runHud(b,input[0],coreHudInputs(1,1));});h.core.service(0);var before=h.core.state();int actions=h.order.size();assertThrows(IllegalStateException.class,()->access[0].runHud(before,input[0],coreHudInputs(1,1)));assertSame(before,h.core.state());assertEquals(actions,h.order.size());assertFalse(h.frame.closed());}
 }
 private static void coreHudProfile(Harness h)throws Exception{var before=h.entry;var s=before.section();var resident=CdR31aArtResidency.unknownForNativeHud(h.assets,CdR31aHudAssets.fromAssets(h.assets));var section=h.art.fromValues(s.codeTable(),resident,s.sectionWord(),s.queue(),s.decoder(),s.savedDecoder(),s.remainingTilesWord(),s.processBudgetWord(),s.hintWord(),s.padWord());h.entry=new Entry(before.initialization(),before.executionEnvironment(),section,before.program(),before.level(),before.normalPalette(),before.fadePalette(),before.waterPalette(),before.displayedPalette(),before.reserved(),before.displayEnabled());h.reset();}
 private static CdLevelHud.R31State coreHudInput(State core,int scoreFlag,int ringsFlag,int livesFlag,CdCheckpointLiveAux.State time,int score){return new CdLevelHud.R31State(core.section(),new CdRingGlobals(16,3,0xA5,ringsFlag,livesFlag,0x5678,0x34,0x56),core.level(),new CdOpeningObjectRuntime.ScoreGlobals(score,5000,scoreFlag),time,Optional.of(new CdLevelHud.BonusValues(0,0,0)));}
 private static CdLevelHud.Inputs coreHudInputs(int paused,int locked){return new CdLevelHud.Inputs(new CdLevelHud.Globals(0,paused,locked,1,0x40,0x2A2,0x100),new CdLevelHud.DebugData(OptionalInt.empty(),Optional.empty(),OptionalInt.empty()));}
 private static Object coreHudCache(Harness h){try{var field=SonicCdR31aLoadingVisit.class.getDeclaredField("hud");field.setAccessible(true);return field.get(h.core);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}}
 private static void coreHudGlyph(Harness h,CdLevelHud.R31State state,int at,int digit){var source=CdR31aHudAssets.fromAssets(h.assets).hudAssets().numbers();for(int half=0;half<64;half+=32)assertArrayEquals(source.slice(digit*64+half,32).copy(),CdR31aArtResidency.readTile(state.section().residency(),(at+half)/32).copy());}
 private static void coreHudNonwrites(State before,State after){assertSame(before.phase(),after.phase());assertSame(before.initialization(),after.initialization());assertSame(before.program(),after.program());assertSame(before.fadePalette(),after.fadePalette());assertSame(before.waterPalette(),after.waterPalette());assertSame(before.blocksRam(),after.blocksRam());assertSame(before.layoutRam(),after.layoutRam());assertEquals(before.initialFadeSteps(),after.initialFadeSteps());assertEquals(before.fromFadeSteps(),after.fromFadeSteps());assertEquals(before.displayEnabled(),after.displayEnabled());assertSame(before.returnKind(),after.returnKind());assertSame(before.music(),after.music());var calls=new ArrayList<>(before.calls());if(before.section().residency()!=after.section().residency())calls.add(Call.CURRENT_ART);assertEquals(calls,after.calls());assertSame(before.reserved(),after.reserved());assertSame(before.normalPalette(),after.normalPalette());assertSame(before.displayedPalette(),after.displayedPalette());}
 private static void coreHudHostNonwrites(Harness.Image before,Harness.Image after){assertSame(before.scroll(),after.scroll());assertEquals(before.publications(),after.publications());assertEquals(before.lives(),after.lives());assertEquals(before.events(),after.events());assertEquals(before.waterFullscreen(),after.waterFullscreen());assertSame(before.actualDisplayed(),after.actualDisplayed());}
 // END R31 ORDINARY NATIVE HUD LANE CHECKS 20261107.

 @Test void nativeClearCallRejectsForeignOwnerAndReplayAfterActualCompletion()throws Exception{
  try(var h=new Harness(3,0,0);var other=new Harness(3,0,0)){
   var seen=new ArrayList<NativeClearCall>();h.onClear=c->{seen.add(c);assertThrows(IllegalStateException.class,()->c.requireOwner(other));};h.core.begin();h.core.service(0);assertEquals(3,seen.size());
   var state=h.frame.state();var env=h.frame.environment();for(var c:seen)assertThrows(IllegalStateException.class,c::applyNative);assertSame(state,h.frame.state());assertSame(env,h.frame.environment());assertFalse(h.frame.closed());
  }
 }
 @Test void failedFlowersPreparationPreservesOriginalErrorAndTheWholeUnadoptedFrameCut()throws Exception{
  try(var h=new Harness(3,0,0)){h.core.begin();var original=new AssertionError("actual flowers preparation");var state=new CdPlayerBody.State[1];var env=new CdPlayerBody.Environment[1];var clock=new CdPlayerFrameRuntime.VIntClock[1];var call=new NativeClearCall[1];
   h.onClear=c->{if(c.action()==Action.CLEAR_FLOWERS){call[0]=c;state[0]=h.frame.state();env[0]=h.frame.environment();clock[0]=h.frame.vIntClock();throw original;}};
   assertSame(original,assertThrows(AssertionError.class,()->h.core.service(8)));assertSame(state[0],h.frame.state());assertSame(env[0],h.frame.environment());assertSame(clock[0],h.frame.vIntClock());assertFalse(h.core.state().calls().contains(Call.CLEAR_FLOWERS));assertTrue(h.core.state().calls().contains(Call.IMMEDIATE_STANDARD));assertThrows(IllegalStateException.class,call[0]::applyNative);assertTrue(h.frame.closed());
  }
 }
 @Test void failedObjectsPreparationCannotClearReservedBankBeforeThePrimary()throws Exception{
  try(var h=new Harness(3,0,0)){h.frame.initializeLevelSizeCentrePlayerValues(0x1234,0x5678);h.core.begin();var original=new AssertionError("actual object-group preparation");var before=new CdPlayerBody.State[1];var reserved=new CdAssetBytes[1];h.onClear=c->{if(c.action()==Action.CLEAR_OBJECTS){before[0]=h.frame.state();reserved[0]=h.core.state().reserved();assertEquals(0x1234,before[0].motion().centreX16_16()>>>16);throw original;}};
   assertSame(original,assertThrows(AssertionError.class,()->h.core.service(8)));assertSame(before[0],h.frame.state());assertSame(reserved[0],h.core.state().reserved());assertEquals(0xA5,reserved[0].unsignedByte(0));assertTrue(h.core.state().calls().contains(Call.CLEAR_FLOWERS));assertFalse(h.core.state().calls().contains(Call.CLEAR_OBJECTS));assertEquals(0,h.frame.vIntClock().absoluteValue().orElseThrow());
  }
 }
 @Test void originalClearRejectsAnotherThreadBeforeAnyNativeAssignment()throws Exception{
  try(var h=new Harness(3,0,0)){h.core.begin();h.onClear=c->{if(c.action()==Action.CLEAR_FLOWERS){var before=h.frame.state();var env=h.frame.environment();var result=new Throwable[1];Thread t=new Thread(()->{try{c.applyNative();result[0]=new AssertionError("Foreign clear admitted");}catch(Throwable fault){result[0]=fault;}});t.start();try{t.join(2000);}catch(InterruptedException e){throw new AssertionError(e);}assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,result[0]);assertSame(before,h.frame.state());assertSame(env,h.frame.environment());}};h.core.service(8);assertTrue(h.core.state().calls().contains(Call.CLEAR_FLOWERS));assertFalse(h.frame.closed());}
 }
 private static final class Harness implements Host,AutoCloseable {
  final CdR31aAssets assets;final CdR31aSectionArt art;final CdPlayerFrameRuntime frame;SonicCdR31aLoadingVisit core;Entry entry;Scroll scrollState;int lives=3,events,cpuReads,cycleCalls;boolean badCpu,checkClones,waterUnknown;int waterFullscreen,waterReads,publicationResult;CdAssetBytes actualDisplayed;final CdAssetBytes cpu=fill(640,0x44);final List<Action> order=new ArrayList<>();final List<Publication> publications=new ArrayList<>();CdPlayerFrameRuntime.VIntClock clockAtFirstPublication;Action failAction;Throwable failure;java.util.function.Consumer<NativeClearCall> onClear;
  Harness(int flags,int spawn,int fade)throws Exception{this(flags,spawn,fade,0,0);}
  Harness(int flags,int spawn,int fade,int stones,int attack)throws Exception{assets=actual();art=CdR31aSectionArt.fromAssets(assets);var m=new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);var s=new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(fill(256,0x71),0x2345),0,new CdPlayerBody.ObjectLinks(0,0),0);var e=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x345,0x23,0x45),new CdPlayerMovement.Bounds(3,0x1234,0x456,0),new CdPlayerMovement.Gates(0x100,1,0,0,0,1,0),new CdPlayerMovement.Globals(3,0x78,4,5,0x6789,9,10,11,12,13)),new CdPlayerBody.Controls(0x20,0x20,0x10,0x10,0),new CdPlayerBody.Globals(0x4567,0x89,attack,0x2345,0,0x3456,1,1),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);frame=CdPlayerFrameRuntime.fromR31aEntry(assets,s,e,new CdPlayerArtRuntime.State(fill(768,0x55),fill(768,0x66),0xA1,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),7));
   var section=art.fromValues(fill(512,0x5A),CdR31aArtResidency.unknown(assets),0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.XOR,0x1234,10,0x89ABCDEF,0x5678,0x9ABC),0,0x2345,0x3456,0x4567);entry=new Entry(new CdProgramInitialization.State(CdProgramInitialization.KnownBit.SET,CdProgramInitialization.MarkerKnowledge.EQUALS_INIT,new CdProgramInitialization.VersionCache(0x81,255),CdProgramInitialization.KnownBit.CLEAR),new CdProgramInitialization.ExecutionEnvironment(Optional.empty()),section,new Program(0,stones,0,0,0,fade,0,0,0x1234),new CdLevelGlobals(0,0,2,spawn,flags,0x123456,7,0,5),fill(128,0x22),fill(128,0x44),fill(128,0x66),fill(128,0x88),fill(1984,0xA5),true);actualDisplayed=entry.displayedPalette();
   var fg=new CdR11aForegroundScroll.State(0x1234,0x56789ABC,2,3,4,5,6,7,8,9,10);var bg=new CdR11aBackgroundScroll.State(1,2,3,4,5,6,fill(8,0x71),7,8,9,fill(512,0x72),fill(512,0x73),fill(1024,0x74),0x12345678);var c=new CdR11aBackgroundPlane.Camera(1,2);scrollState=new Scroll(fg,bg,new Copies(1,2,new CdR11aBackgroundPlane.CopiedCameras(c,c,c),3,new CdR11aBackgroundPlane.FlagWords(4,5,6)),0x3456,0x78,0x3456);reset();
  }
  void reset()throws Exception{core=new SonicCdR31aLoadingVisit(this,assets,frame,art,entry);}
  void init(CdProgramInitialization.State s,Optional<CdProgramInitialization.ExecutionRegion> r)throws Exception{entry=new Entry(s,new CdProgramInitialization.ExecutionEnvironment(r),entry.section(),entry.program(),entry.level(),entry.normalPalette(),entry.fadePalette(),entry.waterPalette(),entry.displayedPalette(),entry.reserved(),entry.displayEnabled());reset();}
  void palClear(CdProgramInitialization.KnownBit b)throws Exception{var i=entry.initialization();init(new CdProgramInitialization.State(i.controllerSetupBit(),i.initMarker(),i.versionCache(),b),entry.executionEnvironment().region());}
  void program(Program p)throws Exception{entry=new Entry(entry.initialization(),entry.executionEnvironment(),entry.section(),p,entry.level(),entry.normalPalette(),entry.fadePalette(),entry.waterPalette(),entry.displayedPalette(),entry.reserved(),entry.displayEnabled());reset();}
  void special(int b)throws Exception{var p=entry.program();program(new Program(p.demoModeWord(),p.timeStonesByte(),p.goodFutureByte(),p.usePlayer2Byte(),b,p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),p.demoDataIndexWord()));}
  void demo(int w)throws Exception{var p=entry.program();program(new Program(p.demoModeWord(),p.timeStonesByte(),p.goodFutureByte(),p.usePlayer2Byte(),p.specialStageByte(),p.palFadeFlagsByte(),p.gameModeByte(),p.levelStartedByte(),w));}
  void level(int restart)throws Exception{var l=entry.level();entry=new Entry(entry.initialization(),entry.executionEnvironment(),entry.section(),entry.program(),new CdLevelGlobals(restart,l.updateHudTime(),l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()),entry.normalPalette(),entry.fadePalette(),entry.waterPalette(),entry.displayedPalette(),entry.reserved(),entry.displayEnabled());reset();}
  public void validate(SonicCdR31aLoadingVisit v,CdPlayerFrameRuntime f,CdR31aAssets a,CdR31aSectionArt art){if(v!=core||f!=frame||a!=assets||art!=this.art)throw new IllegalStateException("Wrong constructed owner");}
  public int livesByte(){return lives;}public Scroll scroll(){return scrollState;}
  public void storeScroll(Scroll before,Scroll ready){core.requireScrollStore(this,before,ready);assertSame(scrollState,before);scrollState=ready;}
  public void events(State s){core.requireEvents(this,s);assertEquals(0,scrollState.foreground().scrollFlags());assertEquals(0x510,scrollState.foreground().destBottomBound());events++;}
  public CdAssetBytes cpuSat(State s){core.requireCpuSat(this,s);cpuReads++;return badCpu?fill(512,0):cpu;}
  public void clear(NativeClearCall c){c.requireOwner(this);if(onClear!=null)onClear.accept(c);act(c.action(),c.before());scrollState=c.scroll();c.applyNative();}
  public void act(Action a,State s){core.requireAction(this,a,s);if(a==failAction){if(failure instanceof Error e)throw e;throw(RuntimeException)failure;}order.add(a);if(a==Action.CLEAR_DISPLAY_PALETTE)actualDisplayed=s.displayedPalette();if(a==Action.CLEAR_MISC)waterFullscreen=0;if(a==Action.FRESH_RESET_SUFFIX)lives=frame.environment().globals().timeAttackMode()==0?3:1;if(a==Action.TITLE_CARD_CREATE)frame.applyPreparedObjectSolidPublication(frame.prepareObjectTitleCardGlobal(frame.state(),frame.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,1));}
  public CdAssetBytes publish(Publication p){core.requirePublication(this,p);if(checkClones)assertThrows(IllegalArgumentException.class,()->core.requirePublication(this,new Publication(p.selector(),p.phase(),p.normalPalette(),p.waterPalette(),p.displayEnabled(),p.section())));if(clockAtFirstPublication==null)clockAtFirstPublication=frame.vIntClock();CdAssetBytes selected;if(p.selector()==0xC)selected=p.normalPalette();else{waterReads++;if(waterUnknown)throw new IllegalStateException("Constructed UNKNOWN waterFullscreen");selected=waterFullscreen==0?p.normalPalette():p.waterPalette();}actualDisplayed=selected;publications.add(p);return publicationResult==1?p.waterPalette():publicationResult==2?fill(128,0x7E):selected;}
  record Image(Scroll scroll,List<Action> order,List<Publication> publications,int lives,int events,int waterFullscreen,CdAssetBytes actualDisplayed){}
  public Object capture(){return new Image(scrollState,List.copyOf(order),List.copyOf(publications),lives,events,waterFullscreen,actualDisplayed);}
  public void validateRestore(Object s){if(!(s instanceof Image))throw new IllegalArgumentException("Foreign constructed image");}
  public void restore(Object s){validateRestore(s);var i=(Image)s;scrollState=i.scroll();order.clear();order.addAll(i.order());publications.clear();publications.addAll(i.publications());lives=i.lives();events=i.events();waterFullscreen=i.waterFullscreen();actualDisplayed=i.actualDisplayed();}
  public void close(){frame.close();}
 }
 private static CdR31aAssets retained;
 private static synchronized CdR31aAssets actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media absent");Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){var all=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,all.size());cue=all.getFirst();}try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=CdR31aAssets.fromPrograms(p,CdStagePayload.R31A.stage());}return retained;}
 @Test void coreHudUnsupportedOperandFaultThrownByHostCannotReplaceCompletedNativePrefix()throws Exception{
  coreHudTypedHostFault(false);
 }
 @Test void coreHudArtFaultThrownByHostCannotReplaceCompletedNativePrefix()throws Exception{
  coreHudTypedHostFault(true);
 }
 /** A genuinely obtained standalone fault is Host failure data, never a fresh failure of this HUD call. */
 private static void coreHudTypedHostFault(boolean artFault)throws Exception{
  try(var h=new Harness(1,0,0);var foreign=new Harness(1,0,0)){
   coreHudProfile(h);
   if(!artFault)coreHudProfile(foreign);
   var foreignBefore=foreign.core.state();var l=foreignBefore.level();
   var raw=coreHudInput(foreignBefore,0x80,0x80,1,CdCheckpointLiveAux.unknown(),1234);
   var foreignInput=new CdLevelHud.R31State(raw.section(),raw.ring(),
    new CdLevelGlobals(l.levelRestart(),1,l.checkpoint(),l.spawnMode(),l.plcLoadFlags(),l.flowerCount(),l.fadeEnableDisplay(),l.powerup(),l.timeOver()),
    raw.score(),raw.liveTime(),raw.bonus());
   var facade=new CdLevelHud.R31(foreign.assets,CdR31aHudAssets.fromAssets(foreign.assets),foreign.art);
   RuntimeException original;
   if(artFault)original=assertThrows(CdLevelHud.R31ArtFailure.class,()->facade.run(foreignInput,coreHudInputs(0,1)));
   else original=assertThrows(CdLevelHud.UnsupportedR31Hud.class,()->facade.run(foreignInput,coreHudInputs(0,1)));
   var foreignSection=artFault?((CdLevelHud.R31ArtFailure)original).state().section():((CdLevelHud.UnsupportedR31Hud)original).state().section();
   var before=new State[1];
   ordinaryReady(h,h,a->{before[0]=a.state();h.failAction=Action.CURRENT_ART;h.failure=original;
    a.runHud(before[0],coreHudInput(before[0],0x80,0x80,1,CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0),1234),coreHudInputs(0,1));
   });
   assertSame(original,assertThrows(RuntimeException.class,()->h.core.service(0)));
   var retained=h.core.state();
   assertNotSame(foreignSection,retained.section(),"Host's typed failure must not supply this call's prefix");
   assertDoesNotThrow(()->h.art.validateState(retained.section()));
   assertSame(before[0].level(),retained.level());
   assertNotSame(before[0].section().residency(),retained.section().residency());
   coreHudGlyph(h,coreHudInput(retained,0,0,0,CdCheckpointLiveAux.unknown(),0),0xB4A0,3);
   assertTrue(h.core.failed());assertTrue(h.frame.closed());
  }
 }

 @Test void originalClearPreparesKnowledgeBeforeActualCoreAdoptionAndRetiresItsAuthority()throws Exception{
  try(var h=new Harness(3,0,0)){assertEquals(0,h.core.state().flowerPositions().knownBytes());var seen=new NativeClearCall[1];
   h.onClear=c->{if(c.action()==Action.CLEAR_FLOWERS){seen[0]=c;assertEquals(0,h.core.state().flowerPositions().knownBytes());assertEquals(768,c.ready().flowerPositions().knownBytes());}};
   h.core.begin();h.core.service(0);assertNotNull(seen[0]);assertEquals(768,h.core.state().flowerPositions().knownBytes());assertThrows(IllegalStateException.class,()->CdFlowerPositions.originalClear(seen[0]));}
 }

}
