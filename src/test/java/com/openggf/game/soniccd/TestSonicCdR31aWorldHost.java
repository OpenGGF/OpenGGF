package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import com.openggf.game.soniccd.scroll.*;
import com.openggf.graphics.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static com.openggf.game.soniccd.SonicCdR31aLoadingVisit.*;

/** Tests FIRST, author Java/GL UNRUN. Actual immutable original USA source with explicitly
 * CONSTRUCTED complete Host/Entry/Authority controls, not Module/boot/World/FULL/pixel admission. */
class TestSonicCdR31aWorldHost {
 @Test void finiteSurfaceRetainsConstructorFiveAndUnchangedHostEleven(){assertEquals(5,SonicCdR31aWorldHost.class.getDeclaredConstructors()[0].getParameterCount());assertEquals(6,SonicCdR31aWorldHost.Initial.class.getRecordComponents().length);assertEquals(11,Host.class.getDeclaredMethods().length);assertFalse(java.lang.reflect.Modifier.isPublic(SonicCdR31aWorldHost.class.getModifiers()));}
 @Test void constructorRetainsTheActualPoolBankFrameAndUnknownClockWithoutInit()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.empty())){assertEquals(Phase.START,h.core.state().phase());assertSame(h.pool,h.host.placement());assertEquals(h.poolBefore.status(),h.pool.status());assertEquals(h.poolBefore.pool().slots(),h.pool.capture().pool().slots());assertArrayEquals(h.poolBefore.savedFlags().copy(),h.pool.capture().savedFlags().copy());assertSame(h.initial.video(),h.host.video());assertSame(h.initial.scroll(),h.host.scroll());assertSame(h.initial.globals(),h.host.globals());assertTrue(h.frame.vIntClock().absoluteSeed().isEmpty());assertEquals(7,h.frame.vIntClock().deltaSinceAdmittedCut());assertTrue(h.host.clears().isEmpty());assertTrue(h.selections.isEmpty());assertFalse(h.host.geometryReady());assertEquals(OptionalInt.of(0xBEEF),h.host.targetRightBoundWord());assertEquals(OptionalLong.of(0x123456),h.host.collisionPointer());assertEquals(OptionalLong.of(0x89ABCDEFL),h.host.rngSeedLong());assertEquals(OptionalInt.of(0x1234),h.host.paletteFadeInfoWord());}
 }
 @Test void duplicateInstallIdleActionsAndForeignCoreRejectWithoutClosingHealthyFrame()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0));var other=new Harness(3,0,OptionalInt.of(0))){var before=h.core.state();assertThrows(IllegalArgumentException.class,()->h.host.install(h.core));assertThrows(IllegalArgumentException.class,()->h.host.validate(other.core,h.frame,h.assets,h.art));assertThrows(IllegalStateException.class,()->h.host.act(Action.CLEAR_OBJECTS,before));assertThrows(IllegalStateException.class,()->h.host.events(before));assertThrows(IllegalStateException.class,()->h.host.cpuSat(before));assertFalse(h.frame.closed());assertSame(before,h.core.state());}
 }
 @Test void startProjectsActualDisabledVideoThenNativeCurrentAndPlaneClears()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.empty())){h.core.begin();assertEquals(Phase.FIRST_PLC,h.core.state().phase());assertFalse(scene(h.host.selected()).displayEnabled());assertTrue(zero(h.host.foreground().nameTable()));assertTrue(zero(h.host.background().nameTable()));assertSame(h.initial.video().cpuSat(),h.host.video().cpuSat());assertTrue(h.host.video().current().hasNativeHudProfile());assertEquals(0,h.host.video().current().knownByte(0xB4A0).orElseThrow());assertSame(h.initial.globals(),h.host.globals());assertEquals(h.poolBefore.pool().slots(),h.pool.capture().pool().slots());assertTrue(h.host.clears().contains(Action.INIT_CONTROLLERS));}
 }
 @Test void firstCDoesNotReadUnknownWaterAndCompulsoryServicePrecedesActualClockReset()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.empty())){h.core.begin();h.core.service(8);assertEquals(0xC,h.selections.getFirst().selector());assertSame(h.selections.getFirst().normalPalette(),h.palettes.getFirst());assertTrue(h.firstClock.absoluteSeed().isEmpty());assertEquals(7,h.firstClock.deltaSinceAdmittedCut());assertEquals(0,h.frame.vIntClock().absoluteValue().orElseThrow());assertTrue(h.core.state().calls().indexOf(Call.FIRST_WAIT)<h.core.state().calls().indexOf(Call.PLAY_LEVEL_MUSIC));}
 }
 @ParameterizedTest @CsvSource({"0,false","128,true"})
 void originalTwelveReturnsTheExactNormalOrWaterReference(int fullscreen,boolean water)throws Exception{
  try(var h=new Harness(3,1,OptionalInt.of(fullscreen))){h.core.begin();h.core.service(0);var p=h.selections.getFirst();assertEquals(0x12,p.selector());assertSame(water?p.waterPalette():p.normalPalette(),h.palettes.getFirst());assertSame(h.palettes.getFirst(),h.core.state().displayedPalette());var i=((SonicCdR31aWorldHost.Published)h.host.selected()).selection().inputs();assertSame(h.palettes.getFirst(),i.palette());}
 }
 @Test void unknownTwelveStopsAtActualWaterReadAfterVscrollAndKeepsOriginalException()throws Exception{
  try(var h=new Harness(3,1,OptionalInt.empty())){h.core.begin();var before=h.host.selected();var f=assertThrows(CdCheckpointLiveAux.UnknownLiveByte.class,()->h.core.service(0));assertEquals(CdCheckpointLiveAux.Field.WATER_FULLSCREEN.nativeAddress(),f.nativeAddress());assertTrue(h.selections.isEmpty());assertTrue(h.core.failed());assertTrue(h.frame.closed());assertFalse(h.core.state().calls().contains(Call.INITIAL_FADE_SERVICE));assertNotNull(before);}
 }
 @Test void actualLoadingClearsOnlyItsOwnedFieldsAndPoolWhileRetainingSavedBody()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0x80))){h.core.begin();h.core.service(0);var g=h.host.globals();assertEquals(0,CdCheckpointLiveAux.read(g.live(),CdCheckpointLiveAux.Field.WATER_FULLSCREEN));assertEquals(0,CdCheckpointLiveAux.read(g.live(),CdCheckpointLiveAux.Field.WATER_HEIGHT2));assertEquals(30,g.drownTimerWord().orElseThrow());assertSame(h.initial.globals().checkpointSaved(),g.checkpointSaved());assertEquals(h.initial.globals().projectorByte(),g.projectorByte());assertEquals(h.initial.globals().score(),g.score());assertEquals(h.initial.globals().ring().rings(),g.ring().rings());assertEquals(0,g.ring().timeStopTimer());assertEquals(0,g.debugBlockWord().orElseThrow());assertEquals(0,g.pauseMusicBit().orElseThrow());assertTrue(zero(g.paletteSteps7()));assertTrue(zero(g.paletteTimers7()));for(var s:h.pool.capture().pool().slots())assertTrue(zero(CdAssetBytes.copyOf(s.copyBytes())));assertArrayEquals(h.poolBefore.savedFlags().copy(),h.pool.capture().savedFlags().copy());assertEquals(h.poolBefore.status(),h.pool.status());assertTrue(zero(h.host.video().cpuSat()));assertTrue(h.host.video().completedCpu().isEmpty());assertEquals(OptionalLong.of(0),h.host.rngSeedLong());assertEquals(OptionalInt.of(0x2E97),h.host.targetRightBoundWord());}
 }
 @Test void freshBitExecutesOriginalSavedFlagResetAndDoesNotDefaultHistoricalFlags()throws Exception{
  try(var h=new Harness(0,0,OptionalInt.of(0))){h.core.begin();assertEquals(1,h.resets);var b=h.pool.capture().savedFlags();assertEquals(1,b.unsignedByte(0));assertEquals(1,b.unsignedByte(1));for(int n=2;n<b.length();n++)assertEquals(0,b.unsignedByte(n));assertEquals(3,h.host.globals().ring().lives());assertEquals(0,h.host.globals().score().score());assertEquals(5000,h.host.globals().score().nextLifeScore());assertEquals(0,h.core.state().level().checkpoint());assertSame(h.initial.globals().checkpointSaved(),h.host.globals().checkpointSaved());assertTrue(zero(h.host.globals().checkpointSaved().knownMask()));}
 }
 @Test void originalMusicCommandIsCachedAndNoR13SelectionIsBorrowed()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0))){h.core.begin();h.core.service(0);assertEquals(1,h.music.size());assertSame(h.core.state().music().orElseThrow(),h.music.getFirst());assertEquals(List.of(new SonicCdGameModule.PastLevelMusic(h.music.getFirst())),h.host.sounds());assertEquals(CdR31aProgrammeAssets.fromAssets(h.assets).selectLevelMusic(0x100,1,0,0),h.music.getFirst());}
 }
 @Test void realR31EventsAndInitialPlanesUseSourceOwnersBeforeLoadingReady()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){h.core.begin();h.core.service(0);assertTrue(h.host.geometryReady());assertEquals(OptionalLong.of(h.assets.range(CdR31aAssets.Asset.LEVEL_COLLISION).nativeAddress()),h.host.collisionPointer());assertEquals(0x510,h.host.scroll().foreground().destBottomBound());assertEquals(0x510,h.frame.environment().movement().bounds().bottom());assertFalse(zero(h.host.foreground().nameTable()));assertFalse(zero(h.host.background().nameTable()));assertTrue(h.core.state().calls().contains(Call.NO_PLC));assertTrue(h.core.state().calls().contains(Call.INITIAL_FOREGROUND));assertTrue(h.core.state().calls().contains(Call.INITIAL_BACKGROUND));var selected=((SonicCdR31aWorldHost.Published)h.host.selected()).selection().inputs();assertSame(h.host.foreground(),selected.foreground());assertSame(h.host.background(),selected.background());assertSame(h.palettes.getFirst(),selected.palette());assertTrue(selected.selectedCpu().isEmpty());}
 }
 @ParameterizedTest @CsvSource({"1,true","3,false"})
 void finiteLoadingReadyKeepsActualTitleCreationAndNoOrdinaryOwner(int flags,boolean title)throws Exception{
  try(var h=new Harness(flags,0,OptionalInt.of(0))){h.core.begin();ready(h);assertEquals(Phase.READY,h.core.state().phase());assertEquals(22,h.core.state().fromFadeSteps());assertInstanceOf(SonicCdPastDisplay.Ready.class,((SonicCdR31aWorldHost.Published)h.host.selected()).selection().projection());assertEquals(title?0x3C:0,h.core.state().reserved().unsignedByte(192));assertEquals(title?1:0,h.frame.environment().controls().controlLocked());assertEquals(0x1C,h.core.state().reserved().unsignedByte(64));assertEquals(0x19,h.core.state().reserved().unsignedByte(30*64));assertEquals(0,h.host.globals().ring().rings());assertEquals(0,time(h.host.globals().live()));assertEquals(0x80,h.host.globals().ring().updateHudRings());assertEquals(0x80,h.host.globals().score().updateHudScore());var actor=h.frame.captureActor();var clock=h.frame.vIntClock();var s=h.core.state();assertEquals(Boundary.ORDINARY_OWNER,assertThrows(UnsupportedLoading.class,()->h.core.service(8)).boundary());assertSame(s,h.core.state());assertEquals(actor,h.frame.captureActor());assertSame(clock,h.frame.vIntClock());assertFalse(h.frame.closed());}
 }
 @Test void fullCpuTailIsTheOriginalClearedSpritesAliasForWaterFade()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){h.core.begin();toFade(h);assertTrue(zero(h.host.video().cpuSat().slice(512,128)));var b=h.core.state();h.core.service(0);assertArrayEquals(CdLevelPaletteFade.stepFromBlack(b.waterPalette(),h.host.video().cpuSat().slice(512,128),0x202F).copy(),h.core.state().waterPalette().copy());assertTrue(h.core.state().calls().indexOf(Call.FROM_NORMAL_COLOURS)<h.core.state().calls().indexOf(Call.FROM_WATER_COLOURS));assertThrows(IllegalStateException.class,()->h.host.cpuSat(h.core.state()));}
 }
 @Test void originalPublicationCloneIdleAndNestedReentryCannotSelectAnotherImage()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0))){h.checkReentry=true;h.core.begin();h.core.service(0);var p=h.selections.getFirst();assertThrows(IllegalStateException.class,()->h.host.publish(p));assertThrows(IllegalStateException.class,()->h.host.requireLoadingPublication(p,h.palettes.getFirst()));assertFalse(h.frame.closed());}
 }
 @Test void completedPublicationPrefixSurvivesOriginalAuthorityExceptionOrError()throws Exception{
  for(boolean error:new boolean[]{false,true})try(var h=new Harness(3,0,OptionalInt.of(0))){h.failure=error?new AssertionError("original publication"):new IllegalStateException("original publication");h.core.begin();assertSame(h.failure,assertThrows(Throwable.class,()->h.core.service(0)));assertEquals(1,h.selections.size());assertSame(h.palettes.getFirst(),h.inputsAtFailure.palette());assertEquals(0xC,h.selections.getFirst().selector());assertTrue(h.core.failed());assertTrue(h.frame.closed());assertThrows(IllegalStateException.class,()->h.host.requireLoadingPublication(h.selections.getFirst(),h.palettes.getFirst()));}
 }
 @Test void musicFailureKeepsTheEarlierCAndCurrentClearWithoutFalseSoundCompletion()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0))){h.musicFailure=new AssertionError("music");h.core.begin();assertSame(h.musicFailure,assertThrows(AssertionError.class,()->h.core.service(0)));assertEquals(1,h.selections.size());assertTrue(h.core.state().music().isPresent());assertFalse(h.core.state().calls().contains(Call.PLAY_LEVEL_MUSIC));assertFalse(h.core.state().calls().contains(Call.IMMEDIATE_LEVEL));assertTrue(h.frame.closed());assertNotNull(h.soundBeforeFailure);assertTrue(h.soundBeforeFailure.isEmpty());}
 }
 @Test void actualComponentCaptureRestoresAllHostPoolAndSelectedPartitionsWithoutNativeReplay()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0));var other=new Harness(1,0,OptionalInt.of(0))){h.core.begin();h.core.service(0);var snap=h.core.capture();var cut=h.host.capture();ready(h);var end=h.host.capture();var state=h.core.state();var clk=h.frame.vIntClock();int services=h.vints;h.core.restore(snap);assertEquals(cut,h.host.capture());ready(h);assertEquals(end,h.host.capture());assertEquals(state.calls(),h.core.state().calls());assertEquals(clk,h.frame.vIntClock());assertTrue(h.vints>services);assertThrows(IllegalArgumentException.class,()->other.host.validateRestore(cut));assertFalse(other.frame.closed());}
 }
 @Test void staleAuthorityRejectsBeforeAHostMutationOrHealthyFrameClosure()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){var s=h.host.capture();h.stale=true;assertThrows(IllegalStateException.class,h.host::capture);assertThrows(IllegalStateException.class,h.host::globals);h.stale=false;assertEquals(s,h.host.capture());assertFalse(h.frame.closed());}
 }
 @Test void publicCompletePublicationValuesCannotCreateThePrivateLoadingContinuation()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){var s=h.core.state();var p=new Publication(0xC,Phase.START,s.normalPalette(),s.waterPalette(),false,s.section());assertThrows(IllegalStateException.class,()->h.host.publish(p));assertThrows(IllegalStateException.class,()->h.host.requireLoadingPublication(p,p.normalPalette()));assertEquals(Phase.START,h.core.state().phase());assertFalse(h.frame.closed());}
 }
 @Test void lazyRetirementIsIdempotentAndDoesNotRunGLOrCloseTheCallersFrame()throws Exception{
  var h=new Harness(1,0,OptionalInt.of(0));h.host.close();h.host.close();assertFalse(h.frame.closed());assertThrows(IllegalStateException.class,h.host::globals);assertThrows(IllegalStateException.class,()->h.host.handler());h.frame.close();
 }
 @Test void actualInitialPlacementAndCurrentSourceCannotBeSubstituted()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){var detached=new CdObjectPlacementRuntime(h.assets.placements());var i=h.initial;var bad=new SonicCdR31aWorldHost.Initial(i.globals(),i.scroll(),i.planes(),i.video(),detached,i.nativeCarry());assertThrows(IllegalArgumentException.class,()->new SonicCdR31aWorldHost(h,h.assets,h.frame,h.art,bad));assertThrows(IllegalArgumentException.class,()->new SonicCdR31aWorldHost.Video(fill(512,0),Optional.empty(),h.frame.artState(),i.video().current(),i.video().selected()));}
 }
 @Test void initializedSpawnerStopsAtTheActualCameraClearWithoutRollingBackEarlierPoolClear()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0))){
   // Explicit component-only prior spawner invocation, not a natural R31 World ingress.
   h.pool.spawnObjects(0,1);assertNotEquals(0,h.pool.status().routine());h.core.begin();var f=assertThrows(SonicCdR31aWorldHost.HostBoundary.class,()->h.core.service(0));assertEquals(SonicCdR31aWorldHost.Boundary.SPAWNER_CAMERA_CLEAR_OWNER,f.boundary());assertTrue(h.core.state().calls().contains(Call.CLEAR_OBJECTS));assertFalse(h.core.state().calls().contains(Call.CLEAR_CAMERA));for(var slot:h.pool.capture().pool().slots())assertTrue(zero(CdAssetBytes.copyOf(slot.copyBytes())));assertNotEquals(0,h.pool.status().routine());assertTrue(h.frame.closed());
  }
 }
 @Test void publicationFullPoolFenceRejectsAnUnownedCallbackMutationWithoutHidingItsPrefix()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0))){h.changePool=true;h.core.begin();var f=assertThrows(IllegalStateException.class,()->h.core.service(0));assertEquals("Authority callback changed complete Host/Core/Frame/pool cut",f.getMessage());assertEquals(1,h.selections.size());assertEquals(0x19,h.pool.pool().state(33).id());assertTrue(h.frame.closed());}
 }
 @Test void doInitUsesActualScratchVersionMarkerAndControllerOrderWithoutAnAssumedRegion()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0),true,Optional.empty())){var f=assertThrows(CdProgramInitialization.UnknownRead.class,h.core::begin);assertEquals(CdProgramInitialization.Read.EXECUTION_REGION,f.read());assertEquals(List.of(Call.START_SCRATCH_CLEAR),h.core.state().calls());assertEquals(0,h.core.state().initialization().versionCache().knownBits());assertTrue(h.frame.closed());}
  try(var h=new Harness(3,0,OptionalInt.of(0),true,Optional.of(new CdProgramInitialization.ExecutionRegion(0x40)))){h.core.begin();assertEquals(0x40,h.core.state().initialization().versionCache().value());assertEquals(CdProgramInitialization.MarkerKnowledge.EQUALS_INIT,h.core.state().initialization().initMarker());assertTrue(h.core.state().calls().indexOf(Call.START_SCRATCH_CLEAR)<h.core.state().calls().indexOf(Call.VERSION_CACHE));assertTrue(h.core.state().calls().indexOf(Call.VERSION_CACHE)<h.core.state().calls().indexOf(Call.INIT_MARKER));assertTrue(h.host.clears().contains(Action.INIT_CONTROLLERS));}
 }
 @Test void nativeCarryKnowledgeIsExplicitAndDuplicateFadeOwnershipRejects()throws Exception{
  var unknown=new SonicCdR31aWorldHost.NativeCarry(OptionalLong.empty(),OptionalInt.empty(),OptionalLong.empty(),OptionalInt.empty());assertTrue(unknown.rngSeedLong().isEmpty());assertThrows(IllegalArgumentException.class,()->new SonicCdR31aWorldHost.NativeCarry(OptionalLong.of(0x1_0000_0000L),OptionalInt.empty(),OptionalLong.empty(),OptionalInt.empty()));assertThrows(IllegalArgumentException.class,()->new SonicCdR31aWorldHost.NativeCarry(OptionalLong.empty(),OptionalInt.of(0x10000),OptionalLong.empty(),OptionalInt.empty()));
  try(var h=new Harness(3,0,OptionalInt.of(0))){var i=h.initial;assertThrows(IllegalArgumentException.class,()->new SonicCdR31aWorldHost.Initial(i.globals(),i.scroll(),i.planes(),i.video(),i.placement(),new SonicCdR31aWorldHost.NativeCarry(OptionalLong.empty(),OptionalInt.of(0),OptionalLong.empty(),OptionalInt.empty())));}
 }
 @Test void anotherThreadCannotReadOrRetireTheActualHostLifetime()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){var seen=new java.util.concurrent.atomic.AtomicReference<Throwable[]>();var t=new Thread(()->{Throwable read,close;try{h.host.globals();read=new AssertionError("foreign thread admitted");}catch(Throwable original){read=original;}try{h.host.close();close=new AssertionError("foreign close admitted");}catch(Throwable original){close=original;}seen.set(new Throwable[]{read,close});});t.start();t.join(2000);assertFalse(t.isAlive());assertInstanceOf(IllegalStateException.class,seen.get()[0]);assertInstanceOf(IllegalStateException.class,seen.get()[1]);assertFalse(h.frame.closed());assertSame(h.initial.globals(),h.host.globals());}
 }
 private static void toFade(Harness h){for(int i=0;i<512;i++){if(h.core.state().phase()==Phase.FROM_BLACK||h.core.state().phase()==Phase.FROM_WHITE)return;h.core.service(0);}fail("finite constructed fade");}
 private static void ready(Harness h){for(int i=0;i<512;i++){if(h.core.state().phase()==Phase.READY)return;h.core.service(0);}fail("finite constructed loading return");}
 private static CdAssetBytes fill(int n,int v){byte[] b=new byte[n];Arrays.fill(b,(byte)v);return CdAssetBytes.copyOf(b);}
 private static boolean zero(CdAssetBytes b){for(byte v:b.copy())if(v!=0)return false;return true;}
 private static int time(CdCheckpointLiveAux.State s){return CdCheckpointLiveAux.read(s,CdCheckpointLiveAux.Field.TIME_LONG);}
 private static RetainedPlaneSceneRenderer.Scene scene(SonicCdR31aWorldHost.Selected selected){if(selected instanceof SonicCdR31aWorldHost.Carried c)return c.scene();var p=((SonicCdR31aWorldHost.Published)selected).selection().projection();if(p instanceof SonicCdPastDisplay.Ready r)return r.scene();if(p instanceof SonicCdPastDisplay.Disabled d)return d.scene();throw new AssertionError(p);}

 @Test void validationAuthorityCannotMutateTheCompletePoolOnReturn()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){
   var before=h.pool.capture();var actor=h.frame.captureActor();var shared=h.frame.captureShared();
   h.authorityMutation=()->h.pool.pool().write(h.pool.pool().state(33).moveByte(CdObjectState.ByteField.ID,0x19));
   assertThrows(IllegalStateException.class,h.host::globals);
   assertNotEquals(before.pool().slots(),h.pool.capture().pool().slots());
   assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());
   assertFalse(h.frame.closed());
  }
 }
 @Test void validationAuthorityPreservesItsOriginalErrorAndSuppressedFrameMutationFault()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){
   var original=new AssertionError("original validation callback");
   h.authorityMutation=()->h.frame.applyPreparedObjectSolidPublication(h.frame.prepareObjectTitleCardGlobal(
    h.frame.state(),h.frame.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,h.frame.environment().controls().controlLocked()^1));
   h.authorityFailure=original;
   assertSame(original,assertThrows(AssertionError.class,h.host::globals));
   assertEquals(1,original.getSuppressed().length);
   assertInstanceOf(IllegalStateException.class,original.getSuppressed()[0]);
   assertFalse(h.frame.closed());
  }
 }
 @Test void initVdpClearsSeparateSonicResidencyWithoutClearingCpuCueOrDirtyFlag()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){
   var before=h.frame.artState();assertFalse(zero(before.residentBank()));var clock=h.frame.vIntClock();
   h.core.begin();var after=h.frame.artState();
   assertTrue(zero(after.residentBank()),"native InitVDP clears the independently owned F000..F300 window");
   assertSame(before.cpuBuffer(),after.cpuBuffer());assertEquals(before.lastFrame(),after.lastFrame());assertEquals(before.dirty(),after.dirty());
   assertSame(clock,h.frame.vIntClock());assertSame(after,h.host.video().playerArt());
   assertFalse(zero(before.residentBank()),"immutable ingress retains its original bytes");
  }
 }

 @Test void preparedPlayerPatternClearIsPureSameOwnerAndChecksTheCompleteClosedPair()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0));var other=new Harness(1,0,OptionalInt.of(0))){
   var actor=h.frame.captureActor();var shared=h.frame.captureShared();var art=h.frame.artState();
   var ready=h.frame.preparePlayerPatternClear();
   assertEquals(actor,h.frame.captureActor());assertEquals(shared,h.frame.captureShared());assertSame(art,h.frame.artState());
   assertSame(art.cpuBuffer(),ready.artState().cpuBuffer());assertTrue(zero(ready.artState().residentBank()));
   assertEquals(art.lastFrame(),ready.artState().lastFrame());assertEquals(art.dirty(),ready.artState().dirty());
   assertThrows(IllegalArgumentException.class,()->other.frame.applyPreparedPlayerPatternClear(ready));
   h.frame.applyPreparedObjectSolidPublication(h.frame.prepareObjectTitleCardGlobal(h.frame.state(),h.frame.environment(),CdTitleCard.StoreField.CONTROL_LOCK_BYTE,h.frame.environment().controls().controlLocked()^1));
   assertThrows(IllegalStateException.class,()->h.frame.applyPreparedPlayerPatternClear(ready));assertSame(art,h.frame.artState());
  }
 }
 @Test void preparedPlayerPatternClearAdoptsOnlyTheReadyResidentBytesWithoutService()throws Exception{
  try(var h=new Harness(1,0,OptionalInt.of(0))){
   var actor=h.frame.captureActor();var env=h.frame.environment();var clock=h.frame.vIntClock();var paused=h.frame.paused();var before=h.frame.artState();
   var ready=h.frame.preparePlayerPatternClear();h.frame.validatePreparedPlayerPatternClear(ready);h.frame.applyPreparedPlayerPatternClear(ready);
   assertEquals(actor,h.frame.captureActor());assertSame(env,h.frame.environment());assertSame(clock,h.frame.vIntClock());assertEquals(paused,h.frame.paused());
   assertSame(ready.artState(),h.frame.artState());assertSame(before.cpuBuffer(),h.frame.artState().cpuBuffer());assertTrue(zero(h.frame.artState().residentBank()));
   assertEquals(before.lastFrame(),h.frame.artState().lastFrame());assertEquals(before.dirty(),h.frame.artState().dirty());assertEquals(0,h.vints);
  }
 }

 @Test void carriedSelectedHscrollKeepsNonzeroRawTailWithoutMutatingOwners()throws Exception{
  int[] carried=new int[256];Arrays.fill(carried,0x89ABCDEF);carried[255]=0xFEDCBA98;
  try(var h=new Harness(3,0,OptionalInt.of(0),false,Optional.empty(),carried)){
   var host=h.host.capture();var actor=h.frame.state();var environment=h.frame.environment();var art=h.frame.artState();var clock=h.frame.vIntClock();
   var method=SonicCdR31aWorldHost.class.getDeclaredMethod("selectedHscroll",CdAssetBytes.class);method.setAccessible(true);var projected=(CdAssetBytes)method.invoke(h.host,fill(1024,0x5C));
   assertEquals(0x5C,projected.unsignedByte(0));assertEquals(0x5C,projected.unsignedByte(0x37F));for(int word=224;word<255;word++)assertEquals(0x89ABCDEFL,projected.unsignedLong(word*4));assertEquals(0xFEDCBA98L,projected.unsignedLong(1020));
   assertEquals(host,h.host.capture());assertSame(actor,h.frame.state());assertSame(environment,h.frame.environment());assertSame(art,h.frame.artState());assertSame(clock,h.frame.vIntClock());
  }
 }
 @Test void actualLoadingPublicationKeepsPublishedTailAndOriginalCpu()throws Exception{
  try(var h=new Harness(3,0,OptionalInt.of(0))){
   h.core.begin();h.core.service(0);var old=((SonicCdR31aWorldHost.Published)h.host.selected()).selection().inputs();var live=h.host.scroll().background().hscrollRam().copy();var cpu=h.host.video().completedCpu();
   h.core.service(0);var selected=((SonicCdR31aWorldHost.Published)h.host.selected()).selection().inputs();assertArrayEquals(Arrays.copyOfRange(live,0,0x380),selected.hscroll().slice(0,0x380).copy());assertArrayEquals(old.hscroll().slice(0x380,128).copy(),selected.hscroll().slice(0x380,128).copy());assertSame(cpu,selected.selectedCpu());assertEquals(2,h.selections.size());
  }
 }
 private static final class Harness implements SonicCdR31aWorldHost.Authority,AutoCloseable {
  Runnable authorityMutation;Throwable authorityFailure;
  final CdR31aAssets assets;final CdR31aSectionArt art;final CdPlayerFrameRuntime frame;final CdObjectPlacementRuntime pool;final CdObjectPlacementRuntime.Snapshot poolBefore;final SonicCdR31aWorldHost.Initial initial;final SonicCdR31aWorldHost host;final SonicCdR31aLoadingVisit core;final List<Publication> selections=new ArrayList<>();final List<CdAssetBytes> palettes=new ArrayList<>();final List<CdCommand> music=new ArrayList<>();int resets,vints;boolean stale,checkReentry,changePool;Throwable failure;Error musicFailure;List<SonicCdGameModule.PastSound> soundBeforeFailure;SonicCdR31aWorldHost.Inputs inputsAtFailure;CdPlayerFrameRuntime.VIntClock firstClock;
  Harness(int flags,int fade,OptionalInt fullscreen)throws Exception{this(flags,fade,fullscreen,false,Optional.empty());}
  Harness(int flags,int fade,OptionalInt fullscreen,boolean doInit,Optional<CdProgramInitialization.ExecutionRegion> region)throws Exception{this(flags,fade,fullscreen,doInit,region,new int[256]);}
  Harness(int flags,int fade,OptionalInt fullscreen,boolean doInit,Optional<CdProgramInitialization.ExecutionRegion> region,int[] carriedHscroll)throws Exception{
   assets=actual();art=CdR31aSectionArt.fromAssets(assets);var m=new CdPlayerMotionState(0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);var actor=new CdPlayerBody.State(m,new CdPlayerBody.AnimationCursor(0,0,0),new CdPlayerBody.Header(0,0,0,0,0),new CdPlayerBody.Timers(0,0,0,0),new CdPlayerBody.History(fill(256,0x71),0x2345),0,new CdPlayerBody.ObjectLinks(0,0),0);var env=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(new CdPlayerMovement.Physics(0x345,0x23,0x45),new CdPlayerMovement.Bounds(3,0x1234,0x456,0),new CdPlayerMovement.Gates(0x100,1,0,0,0,1,0),new CdPlayerMovement.Globals(3,0x78,4,5,0x6789,9,10,11,12,13)),new CdPlayerBody.Controls(0x20,0x20,0x10,0x10,0),new CdPlayerBody.Globals(0x4567,0x89,0,0x2345,0,0x3456,1,1),CdPlayerBody.DynamicSlots.UNIMPLEMENTED);frame=CdPlayerFrameRuntime.fromR31aEntry(assets,actor,env,new CdPlayerArtRuntime.State(fill(768,0x55),fill(768,0x66),0xA1,true),new CdPlayerFrameRuntime.VIntClock(OptionalLong.empty(),7));
   pool=CdObjectPlacementRuntime.fromR31aAssets(assets,fill(766,0xA7));pool.pool().write(pool.pool().state(32).moveByte(CdObjectState.ByteField.ID,0x19).moveWord(CdObjectState.WordField.X,0x1234));poolBefore=pool.capture();
   var fg=new CdR11aForegroundScroll.State(0x1234,0x56789ABC,2,3,4,5,6,7,8,9,10);var bg=new CdR11aBackgroundScroll.State(1,2,3,4,5,6,fill(8,0x71),7,8,9,fill(512,0x72),fill(512,0x73),fill(1024,0x74),0x12345678);var cam=new CdR11aBackgroundPlane.Camera(1,2);var scroll=new Scroll(fg,bg,new Copies(1,2,new CdR11aBackgroundPlane.CopiedCameras(cam,cam,cam),3,new CdR11aBackgroundPlane.FlagWords(4,5,6)),0x3456,0x78,0x3456);
   var live=CdCheckpointLiveAux.writeTimeLong(CdCheckpointLiveAux.unknown(),0xAABBCCDD);if(fullscreen.isPresent())live=CdCheckpointLiveAux.writeWaterFullscreen(CdCheckpointLiveAux.writeWaterRoutine(CdCheckpointLiveAux.writeWaterHeight2(live,0x1234),0x56),fullscreen.getAsInt());
   var globals=new SonicCdR31aWorldHost.Globals(new CdRingGlobals(16,3,0xA5,0x12,0x34,0x5678,0x78,0x9A),live,new CdPlayerBody.ContactGlobals(1,1,0x34),new CdOpeningObjectRuntime.ScoreGlobals(1234,9000,0x56),OptionalInt.of(0x77),CdCheckpointSaveData.State.unknown(),OptionalInt.of(0x4567),OptionalInt.of(0x3456),OptionalInt.of(0x80),Optional.of(new CdLevelHud.BonusValues(12,34,0x56)),Optional.of(new SonicCdR31aWorldHost.Tail(1,2,3,4,0x1234)),Optional.of(new CdGlobalObjectAnimation.Extras(1,2,3,4,new CdRingSpillGlobals(5,6,7))),fill(7,0x54),fill(7,0x76),OptionalInt.of(0x87),OptionalInt.empty(),OptionalInt.empty());
   var current=CdR31aArtResidency.unknownForNativeHud(assets,CdR31aHudAssets.fromAssets(assets));int[] carriedFg=new int[2048],carriedBg=new int[2048];Arrays.fill(carriedFg,0x1212);Arrays.fill(carriedBg,0x3434);var carried=new RetainedPlaneSceneRenderer.Scene(new RetainedPlaneSceneRenderer.PlaneImage(carriedFg),new RetainedPlaneSceneRenderer.PlaneImage(carriedBg),carriedHscroll,0x76543210,fill(128,0x88).copy(),true,SpritePresentation.Frame.empty(),Map.of(),0x20);var video=new SonicCdR31aWorldHost.Video(fill(640,0x44),Optional.empty(),frame.artState(),current,new SonicCdR31aWorldHost.Carried(carried));initial=new SonicCdR31aWorldHost.Initial(globals,scroll,new SonicCdR31aWorldHost.Planes(new CdR11aBackgroundPlane.State(fill(4096,0x12)),new CdR11aBackgroundPlane.State(fill(4096,0x34))),video,pool,new SonicCdR31aWorldHost.NativeCarry(OptionalLong.of(0x123456),OptionalInt.empty(),OptionalLong.of(0x89ABCDEFL),OptionalInt.of(0xBEEF)));host=new SonicCdR31aWorldHost(this,assets,frame,art,initial);
   var section=art.fromValues(fill(512,0x5A),current,0xA55A,CdR11aPlcBuffer.cleared(),Optional.empty(),new CdR11aSectionArt.SavedDecoder(CdR11aSectionArt.Writer.XOR,0x1234,10,0x89ABCDEF,0x5678,0x9ABC),0,0x2345,0x3456,0x4567);var entry=new Entry(new CdProgramInitialization.State(doInit?CdProgramInitialization.KnownBit.CLEAR:CdProgramInitialization.KnownBit.SET,doInit?CdProgramInitialization.MarkerKnowledge.UNKNOWN:CdProgramInitialization.MarkerKnowledge.EQUALS_INIT,new CdProgramInitialization.VersionCache(doInit?0:0x81,doInit?0:255),CdProgramInitialization.KnownBit.CLEAR),new CdProgramInitialization.ExecutionEnvironment(region),section,new Program(0,0,0,0,0,fade,0,0,0x1234),new CdLevelGlobals(0,0,2,0,flags,0x123456,7,0,5),fill(128,0x22),fill(128,0x44),fill(128,0x66),fill(128,0x88),fill(1984,0xA5),true);core=new SonicCdR31aLoadingVisit(host,assets,frame,art,entry);host.install(core);
  }
  public void require(SonicCdR31aWorldHost h,SonicCdR31aLoadingVisit c,CdPlayerFrameRuntime f,CdR31aAssets a,CdR31aSectionArt art){if(stale||h!=host||f!=frame||a!=assets||art!=this.art||(core!=null&&c!=core))throw new IllegalStateException("Constructed actual-source owner fence");if(authorityMutation!=null){var once=authorityMutation;authorityMutation=null;once.run();}if(authorityFailure!=null){var once=authorityFailure;authorityFailure=null;if(once instanceof Error e)throw e;throw(RuntimeException)once;}}
  public void levelMusic(SonicCdR31aWorldHost h,CdCommand command){assertSame(host,h);if(musicFailure!=null){soundBeforeFailure=h.sounds();throw musicFailure;}music.add(command);}
  public void resetGoodFutureFlags(SonicCdR31aWorldHost h){assertSame(host,h);resets++;}
  public void serviceVInt(SonicCdR31aWorldHost h){assertSame(host,h);vints++;}
  public void loadingPublication(SonicCdR31aWorldHost h,SonicCdR31aLoadingVisit c,Publication p,CdAssetBytes palette){h.requireLoadingPublication(p,palette);if(firstClock==null)firstClock=frame.vIntClock();if(checkReentry){var before=h.selected();assertThrows(IllegalStateException.class,()->h.publish(p));assertSame(before,h.selected());assertThrows(IllegalArgumentException.class,()->c.requirePublication(h,new Publication(p.selector(),p.phase(),p.normalPalette(),p.waterPalette(),p.displayEnabled(),p.section())));assertThrows(IllegalStateException.class,h::capture);}selections.add(p);palettes.add(palette);if(changePool)pool.pool().write(pool.pool().state(33).moveByte(CdObjectState.ByteField.ID,0x19));if(failure!=null){inputsAtFailure=((SonicCdR31aWorldHost.Published)h.selected()).selection().inputs();if(failure instanceof Error e)throw e;throw(RuntimeException)failure;}}
  public void close(){host.close();frame.close();}
 }
 private static CdR31aAssets retained;
 private static synchronized CdR31aAssets actual()throws Exception{if(retained!=null)return retained;String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original CD media absent");Path cue;try(var paths=Files.list(Path.of(root).resolve("USA"))){var all=paths.filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,all.size());cue=all.getFirst();}try(var p=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){retained=CdR31aAssets.fromPrograms(p,CdStagePayload.R31A.stage());}return retained;}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
