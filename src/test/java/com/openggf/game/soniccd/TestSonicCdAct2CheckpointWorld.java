package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.control.InputActionMasks;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.resources.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine cold/Past/Results Next8/normal Act2 ingress; only labelled dynamic full64 object inputs.
 * Source N1ecc5f3a Checkpoint/Main7-189 and SaveData18-43, Object Animate14-95,
 * Collision Check/Spawner/Player Object Collision. No actor/global/art/queue/bank/camera/clock seed.
 * Private callbacks observe the actual reached source or throw a named fault, never forge success.
 * All Java UNRUN. Natural Checkpoint continuation remains Root's independent execution obligation. */
@Isolated
class TestSonicCdAct2CheckpointWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void initCreatesTheRealDirtyFirstFreeBallWithoutMainFallthroughOrSavedStateWrites() throws Exception {
        var s=scene();assertEquals(0,s.h().module().act2LoadState().level().checkpoint(),"Authentic cold-chain Init prerequisite");var source=image(s,127,0,1);seed(s,source);var saved=saved(s);
        var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).findFirst().orElseThrow();
        seed(s,free.moveByte(ROUTINE,0).moveWord(X_FRACTION,0x1357).moveWord(Y_FRACTION,0x2468));var dirty=s.placement().pool().state(free.identity().wholeIndex());
        replayNext(s);var expected=header(source).moveByte(COLLISION_TYPE,0xE3);assertImageExceptScreen(expected,s.placement().pool().state(127));
        var child=child(dirty,source,4);assertEquals(child,s.placement().pool().state(child.identity().wholeIndex()));assertSame(saved,saved(s));
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.CHECKPOINT_GLOBAL));assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE));
        assertNotNull(actualField(s.visit(),"checkpoint"));
    }

    @Test void zeroSubtypeIsAlreadyActiveAndNewLaterPhysicalBallRunsInThisSameNativeWalk() throws Exception {
        var s=scene();var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).mapToInt(x->x.identity().wholeIndex()).min().orElseThrow();
        var parent=image(s,free,0,0);seed(s,parent);var childSlot=s.placement().capture().pool().slots().stream().filter(x->x.id()==0).findFirst().orElseThrow();assertTrue(childSlot.identity().wholeIndex()>free);
        replayNext(s);var a=s.placement().pool().state(free);assertEquals(1,a.unsignedByte(CHECKPOINT_ACTIVE));assertEquals(2,a.routine());
        var b=s.placement().pool().state(childSlot.identity().wholeIndex());assertEquals(6,b.routine());assertEquals(2,b.unsignedByte(MAP_FRAME));assertEquals(1,b.unsignedByte(ANIMATION_FRAME));assertEquals(3,b.unsignedByte(ANIMATION_TIME));
        assertEquals(0xFFFFD000+free*64,b.longBits(CHECKPOINT_PARENT));assertEquals(2,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_BODY).count());
    }

    @Test void genuinePrimarySpecialContactPublishesBothLevelBytesAllEighteenSavedStoresAndOneFm() throws Exception {
        var s=scene();var m=s.frame().state().motion();var source=image(s,127,2,7).moveWord(X,m.centreX16_16()>>>16).moveWord(Y,m.centreY16_16()>>>16)
                .moveByte(COLLISION_TYPE,0xE3).moveByte(COLLISION_STATUS,0);seed(s,source);
        var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");
        var inputs=new AtomicReference<EnumMap<CdCheckpointSaveData.Source,Integer>>();
        set(body,"livePort",new Forward(original){@Override public CdCheckpointSaveData.Result save(CdCheckpoint.SaveDataCall c){inputs.set(saveInputs(s));return original.save(c);}});
        replayNext(s);assertNotNull(inputs.get());assertSavedInputs(inputs.get(),saved(s));var parent=s.placement().pool().state(127);assertEquals(1,parent.unsignedByte(COLLISION_STATUS),"Actual primary SPECIAL selector23 must supply contact");
        assertEquals(1,parent.unsignedByte(CHECKPOINT_ACTIVE));assertEquals(0,parent.unsignedByte(COLLISION_TYPE));
        assertEquals(7,s.h().module().act2LoadState().level().checkpoint());assertEquals(1,s.h().module().act2LoadState().level().spawnMode());
        assertEquals(2,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_GLOBAL).count());
        assertEquals(18,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE).count());
        assertEquals(1,s.h().module().act2LoadState().sounds().stream().filter(x->x instanceof SonicCdGameModule.PastCheckpointFm).count());
        var save=saved(s);assertEquals(1,save.read(CdCheckpointSaveData.SavedField.SPAWN_MODE));
        assertEquals(s.frame().state().motion().centreX16_16()>>>16,save.read(CdCheckpointSaveData.SavedField.PRIMARY_X));
        assertEquals(s.frame().state().motion().centreY16_16()>>>16,save.read(CdCheckpointSaveData.SavedField.PRIMARY_Y));assertEquals(31,java.util.stream.IntStream.range(0,31).filter(i->save.knownMask().unsignedByte(i)==1).count());
    }

    @Test void rawParentLongReadsTheAddressedIdZeroDirtyByteWithoutFilteringAParentEntity() throws Exception {
        var s=scene();var free=s.placement().capture().pool().slots().stream().filter(x->x.id()==0&&x.identity().wholeIndex()!=127).findFirst().orElseThrow();
        seed(s,free.moveByte(CHECKPOINT_ACTIVE,1));var source=image(s,127,4,0).moveLong(CHECKPOINT_PARENT,0xFFFFD000+free.identity().wholeIndex()*64)
                .moveByte(CHECKPOINT_BALL_ANGLE,248);seed(s,source);replayNext(s);
        var expected=source.moveByte(CHECKPOINT_ACTIVE,1).moveByte(CHECKPOINT_BALL_ANGLE,0).moveWord(X,source.word(CHECKPOINT_BALL_X))
                .moveWord(Y,source.word(CHECKPOINT_BALL_Y)-8).addByte(ROUTINE,2);assertImageExceptScreen(expected,s.placement().pool().state(127));
        assertEquals(0,s.placement().pool().state(free.identity().wholeIndex()).id());
    }

    @Test void wrappingOddParentAddressMayResolveAnActualReservedByteWithoutIdOrAlignmentGates() throws Exception {
        var s=scene();var reserved=bytes(s,"hudObjects");
        // Score whole2's native tile-low byte is odd-addressed and survives its earlier Draw.
        // Its sprite visibility byte instead clears before this later physical127 read.
        int offset=(2-1)*64+3;assertEquals(0x68,reserved.unsignedByte(offset));int raw=0xFFD040+offset;
        var source=image(s,127,4,0).moveLong(CHECKPOINT_PARENT,(raw-0x2E)|0xA5000000).moveByte(CHECKPOINT_BALL_ANGLE,248);seed(s,source);replayNext(s);
        assertEquals(1,s.placement().pool().state(127).unsignedByte(CHECKPOINT_ACTIVE));assertEquals(6,s.placement().pool().state(127).routine());
    }

    @Test void unownedPrimaryParentByteStopsAtTheOriginalRawAddressBeforeAnySpinOrDraw() throws Exception {
        var s=scene();int raw=0x12FFD02E;var source=image(s,127,4,0).moveLong(CHECKPOINT_PARENT,raw-0x2E);seed(s,source);beforeForward(s);
        var failure=assertThrows(CdCheckpoint.UnownedParentByte.class,()->step(s.h(),neutral()));assertEquals(raw,failure.rawAddressBits());assertEquals(source,s.placement().pool().state(127));assertTrue(s.frame().closed());retired(s);
    }

    @Test void genuineAllocationExhaustionDeletesAllParentBytesWithoutAChildOrSave() throws Exception {
        var s=scene();for(var row:s.placement().capture().pool().slots())if(row.id()==0&&row.identity().wholeIndex()!=127)seed(s,row.moveByte(ID,0x12).moveByte(ROUTINE,12));
        seed(s,image(s,127,0,1));replayNext(s);assertArrayEquals(new byte[64],s.placement().pool().state(127).copyBytes());assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE));
    }

    @Test void clonedLevelReadAndStoreCallsCannotPublishBeforeTheActualPendingCall() throws Exception {
        var s=scene();seed(s,image(s,127,2,1).moveByte(COLLISION_STATUS,1));var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");
        set(body,"livePort",new Forward(original){
            @Override public CdLevelGlobals readLevel(CdCheckpoint.LevelReadCall c){assertThrows(IllegalStateException.class,()->original.readLevel(new CdCheckpoint.LevelReadCall(c.cut())));return original.readLevel(c);}
            @Override public void store(CdCheckpoint.GlobalStoreCall c){assertThrows(IllegalStateException.class,()->original.store(new CdCheckpoint.GlobalStoreCall(c.cut(),c.store())));original.store(c);assertThrows(IllegalStateException.class,()->original.store(c));}
        });replayNext(s);assertEquals(18,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE).count());
    }

    @Test void globalStoreFaultAfterTheActualCheckpointByteKeepsOnlyItsNativePrefix() throws Exception {
        var s=scene();var source=image(s,127,2,1).moveByte(COLLISION_STATUS,1);seed(s,source);var saved=saved(s);var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");var fault=new AssertionError("checkpoint first global prefix");
        set(body,"livePort",new Forward(original){@Override public void store(CdCheckpoint.GlobalStoreCall c){original.store(c);throw fault;}});beforeForward(s);
        assertSame(fault,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(1,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_GLOBAL).count());assertSame(saved,savedRaw(s));
        assertEquals(source.moveByte(COLLISION_TYPE,0).moveByte(CHECKPOINT_ACTIVE,1),s.placement().pool().state(127));assertTrue(s.frame().closed());retired(s);
    }

    @Test void noopOrSubstitutedLevelReadIsRejectedBeforeTheNextPublication() throws Exception {
        var s=scene();seed(s,image(s,127,0,1));var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");
        set(body,"livePort",new Forward(original){@Override public CdLevelGlobals readLevel(CdCheckpoint.LevelReadCall c){return c.cut().source().subtype()==1?new CdLevelGlobals(0,0,255,0,0,0,0,0,0):original.readLevel(c);}});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void completedInitCannotBeRestoredAtItsDrawEvenWithMatchingPublicValues() throws Exception {
        var s=scene();var source=image(s,127,0,1);seed(s,source);var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.DrawCall)actualField(body,"draw");
        set(body,"draw",(CdCheckpoint.DrawCall)(pool,id)->{pool.write(source);original.drawObject(pool,id);});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    @Test void actualProgrammeHandoffsPreserveAllSavedBytesAndKnowledgeInTheExistingFullAndCourseOwners() throws Exception {
        var h=opening();var old=assertInstanceOf(CdOpeningObjectRuntime.class,h.module().getGameService(com.openggf.game.internal.NativeDynamicObjectPass.class));
        for(int call=1;call<=7728;call++)step(h,route(call));
        assertEquals(SonicCdGameModule.LevelProgramPhase.PAST_PENDING,h.module().levelProgramState().phase());
        var original=old.checkpointState().saved();var bank=old.placement().capture().savedFlags();step(h,route(7729));
        int returned=7729;while(h.module().pastLoadState().phase()!=SonicCdGameModule.PastLoadPhase.BEGIN_LEVEL){assertTrue(returned<7800);step(h,route(++returned));}
        var past=actualField(h.module(),"pastLoading");assertSame(original,savedValue(past));assertSaved(original,savedValue(past));
        var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(h);
        registry.restore(full);assertNativeCapture(before,h);assertSaved(original,savedValue(past));registry.restoreCourse(course);assertNativeCapture(before,h);assertSaved(original,savedValue(past));
        step(h,route(++returned));var after=captureNative(h);assertSaved(original,savedValue(past));
        registry.restore(full);assertNativeCapture(before,h);assertSaved(original,savedValue(past));step(h,route(returned));assertNativeCapture(after,h);
        registry.restoreCourse(course);assertNativeCapture(before,h);assertSaved(original,savedValue(past));step(h,route(returned));assertNativeCapture(after,h);
        var prepared=new Prepared(h,past,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(past,"placement"),h.module().pastLoadState().assets(),bank,returned);
        var s=scene(prepared);assertSame(original,saved(s));assertSaved(original,saved(s));var body=actualField(s.visit(),"checkpoint");
        seed(s,image(s,127,0,1));beforeForward(s);assertSaved(original,saved(s));replayNext(s);assertSame(original,saved(s));assertSaved(original,saved(s));assertSame(body,actualField(s.visit(),"checkpoint"));
    }

    @Test void genuineSavedHelperCompletionFaultKeepsAllEighteenStoresAndDoesNotPublishFmOrDraw() throws Exception {
        var s=scene();seed(s,image(s,127,2,3).moveByte(COLLISION_STATUS,1));var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");var fault=new AssertionError("checkpoint after actual save");
        set(body,"livePort",new Forward(original){@Override public CdCheckpointSaveData.Result save(CdCheckpoint.SaveDataCall c){original.save(c);throw fault;}});beforeForward(s);
        assertSame(fault,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(18,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE).count());
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.CHECKPOINT_FM));assertEquals(1,savedRaw(s).read(CdCheckpointSaveData.SavedField.SPAWN_MODE));assertTrue(s.frame().closed());retired(s);
    }

    @Test void fmFaultAfterActualPublicationKeepsTheCompletedSaveAndExactlyOneOrderedSound() throws Exception {
        var s=scene();seed(s,image(s,127,2,3).moveByte(COLLISION_STATUS,1));var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");var fault=new AssertionError("checkpoint after actual FM");
        set(body,"livePort",new Forward(original){@Override public void fm(CdCheckpoint.FmRequestCall c){original.fm(c);throw fault;}});beforeForward(s);
        assertSame(fault,assertThrows(AssertionError.class,()->step(s.h(),neutral())));assertEquals(1,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_FM).count());
        assertEquals(18,rawCalls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE).count());assertTrue(s.frame().closed());retired(s);
    }

    @Test void skippedSameValueGlobalStoreCannotLetTheNextActualReadOrStorePublish() throws Exception {
        var s=scene();seed(s,image(s,127,2,0).moveByte(COLLISION_STATUS,1));var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");
        set(body,"livePort",new Forward(original){@Override public void store(CdCheckpoint.GlobalStoreCall c){}});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.CHECKPOINT_GLOBAL));
        assertFalse(rawCalls(s).contains(SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE));assertTrue(s.frame().closed());retired(s);
    }

    @Test void clonedSaveAndFmCallsBeforeTheOriginalCannotBorrowTheSameOwnerAndFullCut() throws Exception {
        var s=scene();seed(s,image(s,127,2,3).moveByte(COLLISION_STATUS,1));var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");var original=(CdCheckpoint.LivePort)actualField(body,"livePort");
        set(body,"livePort",new Forward(original){
            @Override public CdCheckpointSaveData.Result save(CdCheckpoint.SaveDataCall c){assertThrows(IllegalStateException.class,()->original.save(new CdCheckpoint.SaveDataCall(c.cut(),c.save())));var r=original.save(c);assertThrows(IllegalStateException.class,()->original.save(c));return r;}
            @Override public void fm(CdCheckpoint.FmRequestCall c){assertThrows(IllegalStateException.class,()->original.fm(new CdCheckpoint.FmRequestCall(c.cut(),c.sound())));original.fm(c);assertThrows(IllegalStateException.class,()->original.fm(c));}
        });replayNext(s);assertEquals(18,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_SAVE_STORE).count());assertEquals(1,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.CHECKPOINT_FM).count());
    }

    @Test void savedAndLevelValuesAtAClosedCutCannotMintARealCheckpointCallback() throws Exception {
        var s=scene();var source=image(s,127,2,1);seed(s,source);var cut=new CdCheckpoint.CallCut(s.placement().pool().capture(),source);
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"readCheckpointLevel",new Class<?>[]{CdCheckpoint.LevelReadCall.class},new CdCheckpoint.LevelReadCall(cut)));
        assertThrows(IllegalStateException.class,()->invoke(s.visit(),"readCheckpointSaved",new Class<?>[]{CdCheckpoint.SavedReadCall.class},new CdCheckpoint.SavedReadCall(cut)));healthy(s);
    }

    @Test void nativeAnimateReadFaultRetainsTheResetDecrementPrefixBeforeAnyDraw() throws Exception {
        var s=scene();var source=image(s,127,6,0).moveByte(ANIMATION,255);seed(s,source);beforeForward(s);
        var fault=assertThrows(CdCheckpoint.UnsupportedCheckpoint.class,()->step(s.h(),neutral()));assertEquals(CdCheckpoint.UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY,fault.boundary());
        assertEquals(source.moveByte(PREVIOUS_ANIMATION,255).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,255),s.placement().pool().state(127));assertTrue(s.frame().closed());retired(s);
    }

    @Test void ballMayReadTheActualSavedSpawnByteAfterTheRealContactSaveWithoutAParentEntity() throws Exception {
        var s=scene();seed(s,image(s,127,2,4).moveByte(COLLISION_STATUS,1));replayNext(s);var save=saved(s);assertEquals(1,save.read(CdCheckpointSaveData.SavedField.SPAWN_MODE));
        var source=image(s,127,4,0).moveLong(CHECKPOINT_PARENT,0x93FF1523-0x2E).moveByte(CHECKPOINT_BALL_ANGLE,248);seed(s,source);replayNext(s);
        assertEquals(6,s.placement().pool().state(127).routine());assertEquals(1,s.placement().pool().state(127).unsignedByte(CHECKPOINT_ACTIVE));assertSame(save,saved(s));
    }

    @Test void skippedDrawCannotReturnHealthyAfterAnOtherwiseCompleteNativeInitAndAllocation() throws Exception {
        var s=scene();seed(s,image(s,127,0,1));var body=(CdCheckpoint)actualField(s.visit(),"checkpoint");set(body,"draw",(CdCheckpoint.DrawCall)(pool,id)->{});beforeForward(s);
        assertThrows(IllegalStateException.class,()->step(s.h(),neutral()));assertTrue(s.frame().closed());retired(s);
    }

    private static EnumMap<CdCheckpointSaveData.Source,Integer> saveInputs(Scene s) {
        try {
            var result=new EnumMap<CdCheckpointSaveData.Source,Integer>(CdCheckpointSaveData.Source.class);var f=s.frame();var state=f.state();var env=f.environment();
            var values=actualField(s.visit(),"values");var level=(CdLevelGlobals)actualField(values,"level");var live=(CdCheckpointLiveAux.State)actualField(actualField(values,"globals"),"live");
            var presentation=(SonicCdPlayerPresentation)actualField(s.h().module(),"playerPresentation");var fg=presentation.act2ForegroundImage();var bg=presentation.act2BackgroundImage();
            for(var field:CdCheckpointSaveData.Source.values())result.put(field,switch(field){
                case SPAWN_MODE->level.spawnMode();case PRIMARY_X->state.motion().centreX16_16()>>>16;case PRIMARY_Y->state.motion().centreY16_16()>>>16;
                case BOTTOM_BOUND->env.movement().bounds().bottom();case MINI_SONIC->env.movement().gates().miniSonic();
                case WATER_ROUTINE->CdCheckpointLiveAux.read(live,CdCheckpointLiveAux.Field.WATER_ROUTINE);case WATER_HEIGHT2->CdCheckpointLiveAux.read(live,CdCheckpointLiveAux.Field.WATER_HEIGHT2);
                case WATER_FULLSCREEN->CdCheckpointLiveAux.read(live,CdCheckpointLiveAux.Field.WATER_FULLSCREEN);case TIME_LONG->CdCheckpointLiveAux.read(live,CdCheckpointLiveAux.Field.TIME_LONG);
                case FG_X->fg.cameraX();case FG_Y->fg.cameraYWord();case BG_X->bg.cameraBgXWord();case BG_Y->bg.cameraBgYWord();case BG2_X->bg.cameraBg2XWord();case BG2_Y->bg.cameraBg2YWord();case BG3_X->bg.cameraBg3XWord();case BG3_Y->bg.cameraBg3YWord();});
            return result;
        }catch(Exception failure){throw new AssertionError("actual checkpoint source observation",failure);}
    }
    private static void assertSavedInputs(EnumMap<CdCheckpointSaveData.Source,Integer> source,CdCheckpointSaveData.State saved) {
        var expected=new HashMap<Integer,Integer>();
        for(var store:CdCheckpointSaveData.Store.values()){int raw=source.get(store.source());if(store==CdCheckpointSaveData.Store.TIME_LONG&&Integer.compareUnsigned(raw,0x50000)>=0)raw=0x50000;
            for(int i=0;i<store.width();i++)expected.put(store.nativeAddress()+i,(raw>>>(8*(store.width()-1-i)))&255);}
        assertEquals(31,expected.size());for(var entry:expected.entrySet())assertEquals(OptionalInt.of(entry.getValue()),saved.knownByte(entry.getKey()),"native saved address "+Integer.toHexString(entry.getKey()));
    }

    private static CdCheckpointSaveData.State savedValue(Object visit)throws Exception{return (CdCheckpointSaveData.State)actualField(actualField(actualField(visit,"values"),"globals"),"checkpointSaved");}
    private static void assertSaved(CdCheckpointSaveData.State expected,CdCheckpointSaveData.State actual){assertEquals(expected,actual);assertArrayEquals(expected.knownMask().copy(),actual.knownMask().copy());
        int[] starts={0xFF1523,0xFF152A,0xFF1530,0xFF1591},lengths={5,4,21,1};for(int n=0;n<starts.length;n++)for(int i=0;i<lengths[n];i++)assertEquals(expected.knownByte(starts[n]+i),actual.knownByte(starts[n]+i));}

    private abstract static class Forward implements CdCheckpoint.LivePort {
        private final CdCheckpoint.LivePort base;Forward(CdCheckpoint.LivePort base){this.base=base;}
        @Override public CdLevelGlobals readLevel(CdCheckpoint.LevelReadCall c){return base.readLevel(c);}
        @Override public int readParent(CdCheckpoint.ParentByteCall c){return base.readParent(c);}
        @Override public CdCheckpointSaveData.State readSaved(CdCheckpoint.SavedReadCall c){return base.readSaved(c);}
        @Override public void store(CdCheckpoint.GlobalStoreCall c){base.store(c);}
        @Override public CdCheckpointSaveData.Result save(CdCheckpoint.SaveDataCall c){return base.save(c);}
        @Override public void fm(CdCheckpoint.FmRequestCall c){base.fm(c);}
    }
    private static CdObjectState image(Scene s,int whole,int routine,int subtype){var m=s.frame().state().motion();int x=(m.centreX16_16()>>>16)+64,y=m.centreY16_16()>>>16;
        return s.placement().pool().state(whole).moveByte(ID,0x13).moveByte(ROUTINE,routine).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,0).moveByte(SAVED_FLAGS_ID,0)
                .moveByte(SPRITE_FLAGS,0x84).moveByte(FLAGS,0).moveByte(COLLISION_TYPE,0).moveByte(COLLISION_STATUS,0).moveByte(PRIORITY,4).moveByte(WIDTH,8).moveByte(Y_RADIUS,24)
                .moveWord(X,x).moveWord(Y,y).moveWord(TILE,0x6CB).moveLong(MAPPINGS,0x21D8AC).moveByte(CHECKPOINT_ACTIVE,0).moveByte(CHECKPOINT_BALL_ANGLE,0)
                .moveWord(CHECKPOINT_BALL_X,x).moveWord(CHECKPOINT_BALL_Y,y-24).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(MAP_FRAME,0);}
    private static CdObjectState header(CdObjectState s){return s.addByte(ROUTINE,2).moveLong(MAPPINGS,0x21D8AC).moveWord(TILE,0x6CB).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,8).moveByte(Y_RADIUS,24).moveByte(PRIORITY,4);}
    private static CdObjectState child(CdObjectState old,CdObjectState parent,int routine){return old.moveByte(ID,0x13).addByte(ROUTINE,routine).moveLong(MAPPINGS,0x21D8AC).moveWord(TILE,0x6CB).moveByte(SPRITE_FLAGS,4).moveByte(WIDTH,8).moveByte(Y_RADIUS,8).moveByte(PRIORITY,3).moveByte(MAP_FRAME,1)
            .moveLong(CHECKPOINT_PARENT,0xFFFFD000+parent.identity().wholeIndex()*64).moveWord(X,parent.centreX()).moveWord(Y,parent.centreY()-32).moveWord(CHECKPOINT_BALL_X,parent.centreX()).moveWord(CHECKPOINT_BALL_Y,parent.centreY()-24);}
    private static void assertImageExceptScreen(CdObjectState a,CdObjectState b){assertEquals(a.moveByte(SPRITE_FLAGS,a.spriteFlags()&127),b.moveByte(SPRITE_FLAGS,b.spriteFlags()&127));}
    private static CdCheckpointSaveData.State saved(Scene s)throws Exception{return savedRaw(s);}
    private static CdCheckpointSaveData.State savedRaw(Scene s)throws Exception{return (CdCheckpointSaveData.State)actualField(actualField(actualField(s.visit(),"values"),"globals"),"checkpointSaved");}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> rawCalls(Scene s)throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(s.visit(),"values"),"calls");}

    private static Scene scene() throws Exception {return scene(ready());}
    private static Scene scene(Prepared past) throws Exception {
        var old=oldReturned(past); step(old.harness(),neutral()); var h=old.harness();
        for(int service=0;service<64;service++) {
            step(h,neutral());
            if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) {
                var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
                var scene=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"),h.module().act2LoadState().assets());
                assertEquals(0,scene.placement().pool().state(127).id()); healthy(scene);
                for(int ordinary=0;ordinary<256;ordinary++) {
                    if(scene.frame().environment().controls().controlLocked()==0
                            && scene.h().module().act2LoadState().section().queue().headSource()==0) return scene;
                    step(h,neutral());healthy(scene);
                }
                throw new AssertionError("Actual Title/PLC2 did not release within its native finite bound");
            }
        }
        throw new AssertionError("Actual normal Act2 load did not reach its first ordinary cut");
    }
    private static void seed(Scene s,CdObjectState image) throws Exception {
        var before=s.placement().capture(); var actor=s.frame().captureActor(); var shared=s.frame().captureShared();
        s.placement().pool().write(image);
        assertEquals(before.status(),s.placement().status()); assertArrayEquals(before.savedFlags().copy(),s.placement().capture().savedFlags().copy());
        assertEquals(actor,s.frame().captureActor()); assertEquals(shared,s.frame().captureShared());
        for(var old:before.pool().slots()) if(old.identity().wholeIndex()!=image.identity().wholeIndex())
            assertEquals(old,s.placement().pool().state(old.identity().wholeIndex()));
    }
    private static void beforeForward(Scene s) throws Exception {
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        registry.restore(full);assertNativeCapture(before,s.h());registry.restoreCourse(course);assertNativeCapture(before,s.h());
    }
    private static void replayNext(Scene s) throws Exception {
        var h=s.h(); var registry=h.mode().getRewindRegistry(); var full=registry.capture(); var course=registry.captureCourse();
        var before=captureNative(h); var source=s.frame().stageSource(); var world=h.mode().getWorldSession();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        registry.restore(full); assertNativeCapture(before,h); registry.restoreCourse(course); assertNativeCapture(before,h);
        step(h,neutral()); healthy(s); var after=captureNative(h);
        registry.restore(full); assertNativeCapture(before,h); step(h,neutral()); healthy(s); assertNativeCapture(after,h);
        registry.restoreCourse(course); assertNativeCapture(before,h); step(h,neutral()); healthy(s); assertNativeCapture(after,h);
        assertSame(source,s.frame().stageSource()); assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void healthy(Scene s) throws Exception {
        assertSame(s.frame(),s.h().module().requirePlayerFrame()); assertFalse(s.frame().closed());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,s.h().module().act2LoadState().calls().getLast()); retired(s);
    }
    private static void retired(Scene s) throws Exception {
        for(String name:new String[]{"activeCheckpoint","activeTamabboh","activeFloat","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"checkpointPort","drawingCheckpoint","tamabbohPort","drawingTamabboh","floatPort","drawingFloat","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
    }
    private static List<SonicCdGameModule.Act2LoadCall> calls(Scene s) { return s.h().module().act2LoadState().calls(); }
    private static CdAssetBytes bytes(Scene s,String name) throws Exception { return (CdAssetBytes)actualField(actualField(s.visit(),"values"),name); }
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat cpu(Scene s) throws Exception {
        return ((Optional<CdObjectDrawQueue.CpuSat>)actualField(actualField(actualField(s.visit(),"values"),"loop"),"completedDraw")).orElseThrow();
    }
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...arguments) throws Exception {
        var method=owner.getClass().getDeclaredMethod(name,types); method.setAccessible(true);
        try { return method.invoke(owner,arguments); }
        catch(InvocationTargetException wrapped) {
            var original=wrapped.getCause(); if(original instanceof Error error) throw error;
            if(original instanceof RuntimeException error) throw error; if(original instanceof Exception error) throw error;
            throw new AssertionError(original);
        }
    }
    private static void set(Object owner,String name,Object value) throws Exception {
        var field=owner.getClass().getDeclaredField(name); field.setAccessible(true); field.set(owner,value);
    }
    private static Prepared oldReturned() throws Exception { return oldReturned(ready()); }
    private static Prepared oldReturned(Prepared p) throws Exception {
        for(int call=p.returned()+1;call<=8278;call++) step(p.harness(),route(call));
        seed(p,fixed(p,127).moveByte(ROUTINE,8)); step(p.harness(),neutral());
        for(int wait=0;wait<22;wait++) step(p.harness(),neutral());
        step(p.harness(),neutral()); step(p.harness(),neutral());
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED,p.harness().module().pastLoadState().phase());
        assertFalse(p.frame().closed()); return p;
    }
    private static CdObjectState fixed(Prepared p, int whole) {
        var old = p.placement().pool().state(whole); assertEquals(0, old.id());
        return old.moveByte(ID, 0x3A).moveByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 0).moveWord(X, 288)
                .moveWord(RESULTS_DEST_X, 288).moveWord(RESULTS_SCREEN_Y_WORD, 204).moveWord(Y, 0x2468)
                .moveWord(Y_FRACTION, 0x1357).moveByte(PRIORITY, 0).moveByte(MAP_FRAME, 0).moveWord(TILE, 0x83C4)
                .moveLong(MAPPINGS, p.assets().view(CdR11bAssets.Asset.MAP_SPR_RESULTS).range().nativeAddress());
    }
    private static void seed(Prepared p, CdObjectState row) {
        var before = p.placement().capture(); p.placement().pool().write(row);
        assertEquals(before.status(), p.placement().status());
        assertArrayEquals(before.savedFlags().copy(), p.placement().capture().savedFlags().copy());
        for (var other : before.pool().slots()) if (other.identity().wholeIndex() != row.identity().wholeIndex())
            assertEquals(other, p.placement().pool().state(other.identity().wholeIndex()));
    }
    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(), PlayerInputState.neutral()); }
}
