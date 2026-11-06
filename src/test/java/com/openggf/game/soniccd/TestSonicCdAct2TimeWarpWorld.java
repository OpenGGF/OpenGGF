package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.scroll.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.util.*;
import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine cold/Past/Results/ordinary Act2/Title ingress. Edited timer, actor and knowledge inputs
 * below are explicitly constructed controls, never controller-only13487 evidence.
 * N1ecc5f3 Sonic/Main.asm:615-643,532-567. All Java/engine/EGL checks UNRUN by this author.
 * Authored before the three owned production mirrors existed.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2TimeWarpWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,
                         CdObjectPlacementRuntime placement,CdR12aTerrainAssets assets) { }

    @Test void normalOrdinaryAdmissionIsReadonlyAndRetainsExactPastSavedState() throws Exception {
        var old=oldReturned();var retained=actualField(actualField(old.harness().module(),"pastLoading"),"saved");var s=sceneFrom(old);
        assertSame(retained,saved(s));assertTrue(completed(s).isEmpty());
        var before=captureNative(s.h());assertNull(s.h().module().requireAct2TimeWarpAdmission(s.frame()));
        assertNativeCapture(before,s.h());assertEquals(125,SonicCdGameModule.Act2LoadCall.values().length);
    }
    @Test void actualWorldSaveUsesPostChargeLocalEightAndCurrentWorldScrollSources() throws Exception {
        var s=prepared209();var oldSaved=saved(s);var oldState=s.frame().state();
        var oldEnvironment=s.frame().environment();var fg=presentation(s).act2ForegroundImage();var bg=presentation(s).act2BackgroundImage();
        var pool=s.placement().capture();var reserved=s.h().module().act2LoadState().reservedObjects();
        step(s.h(),neutral());healthy(s);var saved=saved(s);
        assertNotSame(oldSaved,saved);assertEquals(210,s.frame().environment().globals().timeWarpTimer());
        assertEquals(0x80,s.frame().environment().movement().gates().timeZone());assertEquals(2,s.h().module().act2LoadState().level().spawnMode());
        assertEquals(0,saved.read(CdTimeWarpSaveData.SavedField.SPAWN_MODE));
        assertEquals(oldState.motion().centreX16_16()>>>16,saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_X));
        assertEquals(oldState.motion().centreY16_16()>>>16,saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_Y));
        assertEquals(oldState.motion().groundVelocity()&65535,saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_GVEL));
        assertEquals(oldState.motion().xVelocity()&65535,saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_X_VELOCITY));
        assertEquals(oldState.motion().yVelocity()&65535,saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_Y_VELOCITY));
        assertEquals(oldState.motion().flags()&~0x48,saved.read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS));
        assertEquals(oldEnvironment.movement().gates().miniSonic(),saved.read(CdTimeWarpSaveData.SavedField.MINI_SONIC));
        assertEquals(oldEnvironment.movement().bounds().bottom(),saved.read(CdTimeWarpSaveData.SavedField.BOTTOM_BOUND));
        assertEquals(fg.cameraX(),saved.read(CdTimeWarpSaveData.SavedField.FG_X));assertEquals(fg.cameraYWord(),saved.read(CdTimeWarpSaveData.SavedField.FG_Y));
        assertEquals(bg.cameraBgXWord(),saved.read(CdTimeWarpSaveData.SavedField.BG_X));assertEquals(bg.cameraBgYWord(),saved.read(CdTimeWarpSaveData.SavedField.BG_Y));
        assertEquals(bg.cameraBg2XWord(),saved.read(CdTimeWarpSaveData.SavedField.BG2_X));assertEquals(bg.cameraBg2YWord(),saved.read(CdTimeWarpSaveData.SavedField.BG2_Y));
        assertEquals(bg.cameraBg3XWord(),saved.read(CdTimeWarpSaveData.SavedField.BG3_X));assertEquals(bg.cameraBg3YWord(),saved.read(CdTimeWarpSaveData.SavedField.BG3_Y));
        for(byte known:saved.knownMask().copy())assertEquals(1,known);
        assertEquals(pool.pool().slots(),s.placement().capture().pool().slots());assertArrayEquals(pool.savedFlags().copy(),s.placement().capture().savedFlags().copy());
        assertArrayEquals(Arrays.copyOfRange(reserved.copy(),11*64,15*64),
                Arrays.copyOfRange(s.h().module().act2LoadState().reservedObjects().copy(),11*64,15*64),
                "Jumping directly to210 does not create or fabricate time stars");
        assertEquals(0x100,presentation(s).act2ForegroundImage().scrollLock());assertNull(s.h().module().requireAct2TimeWarpAdmission(s.frame()));
    }
    @Test void uncontrolledOriginalModeResumesAfterItsSingleRealSaveAndSuppressesPrimaryDraw() throws Exception {
        var s=prepared209();var actor=s.frame().captureActor();s.frame().restoreActor(copy(actor,"state",
                copy(actor.state(),"motion",copy(actor.state().motion(),"playerControl",0))));
        replayNext(s);assertEquals(0,s.frame().state().motion().playerControl());assertEquals(0x80,s.frame().environment().movement().gates().timeZone());
        assertEquals(2,s.h().module().act2LoadState().level().spawnMode());assertTrue(s.h().module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.PRIMARY_MAIN));
        @SuppressWarnings("unchecked") var cpu=(Optional<CdObjectDrawQueue.CpuSat>)actualField(actualField(actualField(s.visit(),"values"),"loop"),"completedDraw");
        assertTrue(cpu.orElseThrow().provenance().stream().noneMatch(p->p.slotRef().wholeIndex()==0));
    }
    @Test void spawnTwoNextFrameIsLazyAndRetainsSavedAndLockReferences() throws Exception {
        var s=prepared209();step(s.h(),neutral());healthy(s);var saved=saved(s);var receipt=completed(s);var fg=presentation(s).act2ForegroundImage();
        step(s.h(),neutral());healthy(s);assertEquals(211,s.frame().environment().globals().timeWarpTimer());
        assertSame(saved,saved(s));assertSame(receipt,completed(s));assertSame(fg,presentation(s).act2ForegroundImage());
    }
    @Test void fullAndCourseReplayRestoreSavedCustodyAndMarkedOldProgramme() throws Exception {
        var s=prepared209();var beforeSaved=saved(s);replayNext(s);var afterSaved=saved(s);assertNotSame(beforeSaved,afterSaved);
        assertTrue(completed(s).isPresent());replayNext(s);assertSame(afterSaved,saved(s));
    }
    @Test void rawMarkedFrameBeforeCompletedSaveCannotAuthorizeOrdinaryOrCapture() throws Exception {
        var s=scene();var shared=s.frame().captureShared();
        for(int marker:new int[]{0x80,0x81,0x82}){setPeriod(s,marker);
            assertThrows(IllegalStateException.class,()->s.h().module().requireAct2TimeWarpAdmission(s.frame()));
            assertThrows(IllegalStateException.class,()->presentation(s).borrowAct2TimeWarpWorld(s.frame()));s.frame().restoreShared(shared);}
        assertNull(s.h().module().requireAct2TimeWarpAdmission(s.frame()));
    }
    @Test void completedRawEightyReceiptCannotAuthorizeAChangedRawEightyOneMarker() throws Exception {
        var s=prepared209();step(s.h(),neutral());healthy(s);var shared=s.frame().captureShared();var saved=saved(s);
        var p=presentation(s);var capturedScroll=p.captureAct2Scroll();var before=captureNative(s.h());
        String[] imageFields={"scrollState","backgroundState","planeState","drawCopies","publishedPlane","preparedScroll","publishedScroll"};
        Object[] images=new Object[imageFields.length];for(int i=0;i<images.length;i++)images[i]=actualField(p,imageFields[i]);
        for(int marker:new int[]{0x81,1}) {
            setPeriod(s,marker);assertThrows(IllegalStateException.class,()->s.h().module().requireAct2TimeWarpAdmission(s.frame()));
            assertThrows(IllegalStateException.class,p::captureAct2Scroll);
            assertThrows(IllegalStateException.class,p::validateAct2LevelLoadPlane);
            assertThrows(IllegalStateException.class,()->p.restoreAct2Scroll(capturedScroll));
            for(int i=0;i<images.length;i++)assertSame(images[i],actualField(p,imageFields[i]),imageFields[i]);
            assertSame(saved,saved(s));assertFalse(s.frame().closed());s.frame().restoreShared(shared);
            assertNull(s.h().module().requireAct2TimeWarpAdmission(s.frame()));p.validateAct2LevelLoadPlane();p.restoreAct2Scroll(capturedScroll);
            assertTrue(p.sameAct2Scroll(capturedScroll));assertNativeCapture(before,s.h());
        }
        assertNull(s.h().module().requireAct2TimeWarpAdmission(s.frame()));
    }
    @Test void equalPublicLockMetadataIsNotClosedWorldPublicationAuthority() throws Exception {
        var s=prepared209();step(s.h(),neutral());healthy(s);var cut=presentation(s).borrowAct2TimeWarpWorld(s.frame());
        var equal=new CdOpeningObjectRuntime.TimeWarpLockPublication(cut,cut.foreground());var before=captureNative(s.h());
        assertThrows(IllegalStateException.class,()->presentation(s).validateAct2TimeWarpLock(s.frame(),equal));
        assertThrows(IllegalStateException.class,()->presentation(s).applyAct2TimeWarpLock(s.frame(),equal));assertNativeCapture(before,s.h());
    }
    @Test void differentFrameReferenceCannotBorrowActualWorldAdmission() throws Exception {
        var s=scene();var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->s.h().module().requireAct2TimeWarpAdmission(null));assertNativeCapture(before,s.h());
    }
    @Test void nativeLockStorePreservesDirtyLowByteAndAllOtherScrollFields() throws Exception {
        var s=prepared209();var p=presentation(s);var fg=p.act2ForegroundImage();var dirty=copy(fg,"scrollLock",0xAB);
        set(p,"scrollState",dirty);step(s.h(),neutral());healthy(s);
        assertEquals(copy(dirty,"scrollLock",0x1AB),p.act2ForegroundImage());
    }
    @Test void sameValueHighByteLockStillRequiresCompletedSaveAndOriginalInvocation() throws Exception {
        var s=prepared209();var p=presentation(s);set(p,"scrollState",copy(p.act2ForegroundImage(),"scrollLock",0x137));
        step(s.h(),neutral());healthy(s);assertEquals(0x137,p.act2ForegroundImage().scrollLock());assertTrue(completed(s).isPresent());
        assertNull(s.h().module().requireAct2TimeWarpAdmission(s.frame()));
    }
    @Test void unknownReachedWaterReadKeepsOriginalTypedFailureAndNoDeferredSavePublication() throws Exception {
        var s=prepared209();var saved=saved(s);var fg=presentation(s).act2ForegroundImage();
        // Explicit constructed live-knowledge control, not a native clear or publication certificate.
        var v=actualField(s.visit(),"values");var g=actualField(v,"globals");var live=(CdCheckpointLiveAux.State)actualField(g,"live");
        var raw=new byte[8];for(int i=0;i<4;i++)raw[i]=(byte)live.knownByte(0xFF1514+i).orElseThrow();
        set(s.visit(),"values",copy(v,"globals",copy(g,"live",CdCheckpointLiveAux.declaredDevIngress(CdAssetBytes.copyOf(raw),15))));
        var failure=assertThrows(CdCheckpointLiveAux.UnknownLiveByte.class,()->step(s.h(),neutral()));assertEquals(0xFFF64D,failure.nativeAddress());assertEquals(0,failure.getSuppressed().length);assertTrue(s.frame().closed());
        assertSame(saved,saved(s));assertTrue(completed(s).isEmpty());assertSame(fg,actualField(presentation(s),"scrollState"));
        assertEquals(0,((CdLevelGlobals)actualField(actualField(s.visit(),"values"),"level")).spawnMode());assertEquals(1,s.frame().environment().movement().gates().timeZone());
        assertEquals(210,s.frame().environment().globals().timeWarpTimer(),"Earlier real V-int service survives the failed Body");retired(s);
    }
    @Test void laterAnimationFailureDoesNotPublishEarlierDetachedSaveOrMarker() throws Exception {
        var s=prepared209();var actor=s.frame().captureActor();var bad=copy(actor.state().motion(),"animation",255);bad=copy(bad,"previousAnimation",255);
        s.frame().restoreActor(copy(actor,"state",copy(actor.state(),"motion",bad)));var saved=saved(s);var fg=presentation(s).act2ForegroundImage();
        // Explicit invalid-animation fault at Animate, after TimeWarp stages its detached save.
        var failure=assertThrowsExactly(IllegalArgumentException.class,()->step(s.h(),neutral()));
        assertEquals("oAnim outside the 60 admitted R11A scripts",failure.getMessage());assertEquals(0,failure.getSuppressed().length);
        assertSame(saved,saved(s));assertTrue(completed(s).isEmpty());
        assertSame(fg,actualField(presentation(s),"scrollState"));assertEquals(1,s.frame().environment().movement().gates().timeZone());
        assertEquals(0,((CdLevelGlobals)actualField(actualField(s.visit(),"values"),"level")).spawnMode());assertEquals(210,s.frame().environment().globals().timeWarpTimer());retired(s);
    }
    @Test void savedFlagsClearStandingAndWaterWithoutChangingLiveActorBits() throws Exception {
        var s=prepared209();var actor=s.frame().captureActor();var motion=copy(actor.state().motion(),"flags",0x4A);
        s.frame().restoreActor(copy(actor,"state",copy(actor.state(),"motion",motion)));step(s.h(),neutral());healthy(s);
        assertEquals(2,saved(s).read(CdTimeWarpSaveData.SavedField.PRIMARY_FLAGS));assertEquals(0x4A,s.frame().state().motion().flags());
    }
    @Test void restoringPreSaveCutRemovesCompletedCustodyWithoutAGlobalReceiptCache() throws Exception {
        var s=prepared209();var registry=s.h().mode().getRewindRegistry();var before=registry.capture();var beforeSaved=saved(s);
        step(s.h(),neutral());healthy(s);assertTrue(completed(s).isPresent());registry.restore(before);
        assertSame(beforeSaved,saved(s));assertTrue(completed(s).isEmpty());setPeriod(s,0x80);
        assertThrows(IllegalStateException.class,()->s.h().module().requireAct2TimeWarpAdmission(s.frame()));
    }

    @Test void retiredActualFrameRejectsItsEarlierGenuineWorldSnapshotAndAdmission() throws Exception {
        var s=prepared209();step(s.h(),neutral());healthy(s);var capture=s.visit().capture();var programme=s.h().module().levelProgramState();
        s.frame().close();assertThrows(IllegalStateException.class,()->s.h().module().requireAct2TimeWarpAdmission(s.frame()));
        assertThrows(IllegalStateException.class,()->s.visit().validateRestore(capture,programme));
    }

    /** Same-owner public Frame restoration supplies constructed timer/actor inputs only. */
    private static Scene prepared209() throws Exception {
        var s=scene();for(int whole=32;whole<128;whole++)s.placement().pool().deleteObject(whole);
        var actor=s.frame().captureActor();var motion=copy(actor.state().motion(),"playerControl",1);
        motion=copy(motion,"charge",0);motion=copy(motion,"xVelocity",0x123);motion=copy(motion,"yVelocity",-0x234);
        motion=copy(motion,"groundVelocity",0x789);s.frame().restoreActor(copy(actor,"state",copy(actor.state(),"motion",motion)));
        var shared=s.frame().captureShared();var env=shared.environment();var movement=env.movement();
        movement=copy(movement,"gates",copy(movement.gates(),"timeWarpDirection",255));
        env=copy(env,"movement",movement);env=copy(env,"globals",copy(env.globals(),"timeWarpTimer",209));
        s.frame().restoreShared(copy(shared,"environment",env));return s;
    }
    private static void setPeriod(Scene s,int period) throws Exception {
        var shared=s.frame().captureShared();var env=shared.environment();var move=env.movement();
        s.frame().restoreShared(copy(shared,"environment",copy(env,"movement",copy(move,"gates",copy(move.gates(),"timeZone",period)))));
    }
    private static SonicCdPlayerPresentation presentation(Scene s)throws Exception{return (SonicCdPlayerPresentation)actualField(s.visit(),"presentation");}
    private static Object custody(Scene s)throws Exception{return actualField(actualField(actualField(s.visit(),"values"),"globals"),"timeWarpCustody");}
    private static CdTimeWarpSaveData.State saved(Scene s)throws Exception{return (CdTimeWarpSaveData.State)actualField(custody(s),"saved");}
    private static Optional<?> completed(Scene s)throws Exception{return (Optional<?>)actualField(custody(s),"completed");}
    /** Immutable constructed input copying only; never clones a native invocation/call/receipt/Snapshot. */
    @SuppressWarnings("unchecked") private static <T>T copy(T value,String field,Object replacement)throws Exception{
        var components=value.getClass().getRecordComponents();var types=new Class<?>[components.length];var args=new Object[components.length];boolean found=false;
        for(int n=0;n<components.length;n++){types[n]=components[n].getType();var getter=components[n].getAccessor();getter.setAccessible(true);args[n]=getter.invoke(value);if(components[n].getName().equals(field)){args[n]=replacement;found=true;}}
        assertTrue(found,field);var ctor=value.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return (T)ctor.newInstance(args);
    }
    private static void set(Object owner,String name,Object value)throws Exception{var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Scene scene() throws Exception { return sceneFrom(oldReturned()); }
    private static Scene sceneFrom(Prepared old) throws Exception {
        step(old.harness(),neutral()); var h=old.harness();
        for(int service=0;service<64;service++) {
            step(h,neutral());
            if(h.module().act2LoadState().calls().contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN)) {
                var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
                var scene=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"),h.module().act2LoadState().assets());
                assertEquals(0,scene.placement().pool().state(127).id()); healthy(scene);
                for(int ordinary=0;ordinary<256;ordinary++) {
                    if(scene.frame().environment().controls().controlLocked()==0
                            && scene.h().module().act2LoadState().section().queue().headSource()==0) {
                        assertEquals(1,scene.frame().environment().movement().gates().timeZone());return scene;
                    }
                    step(h,neutral());healthy(scene);
                }
                throw new AssertionError("Actual Title/PLC2 did not release within its native finite bound");
            }
        }
        throw new AssertionError("Actual normal Act2 load did not reach its first ordinary cut");
    }
    private static void beforeForward(Scene s) throws Exception {
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        registry.restore(full);assertNativeCapture(before,s.h());registry.restoreCourse(course);assertNativeCapture(before,s.h());
    }
    private static void replayNext(Scene s) throws Exception {replayNext(s,neutral());}
    private static void replayNext(Scene s,LogicalInputSnapshot input) throws Exception {
        var h=s.h(); var registry=h.mode().getRewindRegistry(); var full=registry.capture(); var course=registry.captureCourse();
        var before=captureNative(h); var source=s.frame().stageSource(); var world=h.mode().getWorldSession();
        long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        registry.restore(full); assertNativeCapture(before,h); registry.restoreCourse(course); assertNativeCapture(before,h);
        step(h,input); healthy(s); var after=captureNative(h);
        registry.restore(full); assertNativeCapture(before,h); step(h,input); healthy(s); assertNativeCapture(after,h);
        registry.restoreCourse(course); assertNativeCapture(before,h); step(h,input); healthy(s); assertNativeCapture(after,h);
        assertSame(source,s.frame().stageSource()); assertSame(world,h.mode().getWorldSession());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static void healthy(Scene s) throws Exception {
        assertSame(s.frame(),s.h().module().requirePlayerFrame()); assertFalse(s.frame().closed());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,s.h().module().act2LoadState().calls().getLast()); retired(s);
    }
    private static void retired(Scene s) throws Exception {
        for(String name:new String[]{"activeSwing","activeMovingSpring","activeRobotGenerator","activeTunnelDoorSplash","activeDoor","activeChildEffect","activeLogMosqui","activeAnton","activePata","activeBoulder","activePlatform","activeMonitor","activeTitle","activePrimary"})assertNull(actualField(s.visit(),name),name);
        for(String name:new String[]{"swingPort","drawingSwing","movingSpringPort","drawingMovingSpring","robotPort","drawingRobotGenerator","splashPort","drawingSplash","doorPort","drawingDoor","drawingChildEffect","logMosquiPort","drawingLogMosqui","antonPort","drawingAnton","drawingPata","boulderPort","drawingBoulder","platformPort","drawingPlatform","monitorPort","drawingMonitor","runningObjects"})assertFalse((boolean)actualField(s.visit(),name),name);
    }
    private static Prepared oldReturned() throws Exception {
        var old = ready();
        for (int call = old.returned() + 1; call <= 10419; call++) step(old.harness(), controller(call));
        assertEquals(SonicCdGameModule.PastLoadPhase.OLD_IPX_RETURNED, old.harness().module().pastLoadState().phase());
        assertFalse(old.frame().closed());
        return old;
    }
    /** Exact immutable controller fixture recipe; finite endpoint12884 supplies no gameplay values. */
    private static LogicalInputSnapshot controller(int call) {
        if(call<1 || call>12884)throw new IllegalArgumentException("Bounded source recipe ends12884");
        if(call<=8276)return route(call);
        int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        if(call>=11214)actions=(call-11214)%128<32?2:0;
        if(call==11587 || call>=12671 && call<=12699)actions=0;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }

    private static LogicalInputSnapshot neutral() { return LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral()); }
}
