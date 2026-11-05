package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import com.openggf.game.soniccd.objects.CdLevelGlobals;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine controller-only healthy12884 ingress; call12885 retains the actual held-jump row.
 * Constructed reserved64/value controls below are labelled; no actor/global/history/art hydration,
 * failed-cut ingress, replay of helper/store metadata or private producer authority is manufactured.
 * Native N1ecc5f3a Sonic/Main112-128,573-669; Powerup/Main21-36,88-173.
 * Source-only authoring: every Java check is UNRUN by this author.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2TimeStarsWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame) { }

    @Test void genuinePrimaryPatchThenFourFreshInitsPublishOnceInPhysicalOrderAndReplayBothScopes() throws Exception {
        var s=scene();var b=body(s);var observer=observer(b);var bank=bank(s);
        var entry=raw(s);var beforeHistory=s.frame().state().history();var publications=new ArrayList<Integer>();
        var postBody=new AtomicReference<CdPlayerBody.State>();
        set(b,"observer",new Forward(observer){
            @Override public void beforeOperation(Object call){
                assertFalse(b.isCurrentPrimitive(call));
                var c=assertInstanceOf(CdTimeStars.Publication.class,call);int whole=c.source().identity().wholeIndex();
                publications.add(whole);
                if(whole==12){
                    postBody.set(s.frame().state());
                    for(int star=12;star<16;star++)assertArrayEquals(patched(entry.slice((star-1)*64,64),star).copy(),c.cut().slots().get(star-1).copyBytes());
                    assertEquals(1,s.frame().environment().globals().timeWarpTimer());
                    assertEquals(1,assertDoesNotThrow(()->contact(s)).timeWarp());
                    var h=s.frame().state().history();assertEquals((beforeHistory.index()+4)&255,h.index());
                    byte[] expected=beforeHistory.bytes().copy();int at=beforeHistory.index();var m=s.frame().state().motion();
                    putWord(expected,at,m.centreX16_16()>>>16);putWord(expected,at+2,m.centreY16_16()>>>16);
                    assertArrayEquals(expected,h.bytes().copy(),"Real post-Body RecordPos precedes reserved Init");
                }
                assertEquals(0,c.source().byteAt(0x24));
                assertArrayEquals(init(CdAssetBytes.copyOf(c.source().copyBytes())).copy(),c.output().copyBytes());
                super.beforeOperation(call);
            }
            @Override public void afterOperation(Object call){
                super.afterOperation(call);var c=(CdTimeStars.Publication)call;assertFalse(b.isCurrentPrimitive(call));
                assertFull31Publication(c,rawUnchecked(s));
            }
        });
        var powerup=powerup(b);
        set(b,"powerup",new CdTimeStars.PowerupPort(){public int read(){fail("Fresh Init must not read Powerup");return 0;}public void write(int value){fail("Fresh Init must not write Powerup");}});
        var draw=(CdTimeStars.DrawReserved)actualField(b,"draw");set(b,"draw",(CdTimeStars.DrawReserved)(actual,identity)->fail("Fresh Init must not Draw"));
        assertThrows(IllegalStateException.class,()->bank.state(12));
        replayNext(s);
        assertEquals(List.of(12,13,14,15,12,13,14,15,12,13,14,15),publications);
        for(int whole=12;whole<16;whole++)assertArrayEquals(init(patched(entry.slice((whole-1)*64,64),whole)).copy(),raw(s).slice((whole-1)*64,64).copy());
        assertNotNull(postBody.get());assertEquals(0,s.frame().state().motion().playerControl());
        assertEquals(255,s.frame().environment().movement().gates().timeWarpDirection());assertEquals(0,s.frame().state().motion().charge());
        assertEquals(1,s.frame().environment().movement().gates().timeZone());assertEquals(1,s.frame().environment().globals().timeWarpTimer());
        assertEquals(4,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TIME_STARS_INIT).count());
        assertEquals(4,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TIME_STARS_BODY).count());
        assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.TIME_STARS_ACTIVE));
        assertFalse(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()>=12&&p.slotRef().wholeIndex()<16));
        set(b,"powerup",powerup);set(b,"draw",draw);retired(s);
    }

    @Test void constructedWholeTwelveOccupiedRetainsNativeEarlyReturnWithoutCreatingTheOtherThree() throws Exception {
        var s=scene();byte[] all=raw(s).copy();byte[] star=new byte[64];Arrays.fill(star,(byte)0x51);
        star[0]=3;star[0x24]=0;star[0x1C]=5;System.arraycopy(star,0,all,11*64,64);seedReserved(s,CdAssetBytes.copyOf(all));
        var entry=raw(s);replayNext(s);
        assertEquals(1,s.frame().environment().globals().timeWarpTimer());assertEquals(0,contact(s).timeWarp());
        assertArrayEquals(init(entry.slice(11*64,64)).copy(),raw(s).slice(11*64,64).copy());
        for(int whole=13;whole<16;whole++)assertArrayEquals(entry.slice((whole-1)*64,64).copy(),raw(s).slice((whole-1)*64,64).copy());
        assertEquals(1,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TIME_STARS_BODY).count());
    }

    @Test void constructedDirtyThirteenThroughFifteenRetainEveryNonwriteAcrossRealPatchAndInit() throws Exception {
        var s=scene();seedDirty(s,0);var before=raw(s);replayNext(s);
        for(int whole=12;whole<16;whole++)assertArrayEquals(init(patched(before.slice((whole-1)*64,64),whole)).copy(),raw(s).slice((whole-1)*64,64).copy());
        assertEquals(1,contact(s).timeWarp());assertEquals(4,calls(s).stream().filter(c->c==SonicCdGameModule.Act2LoadCall.TIME_STARS_INIT).count());
    }

    @Test void constructedDirtyRoutineSixUsesActualPostBodyHistoryAndPowerupMoveReadbackBsetThenDraw() throws Exception {
        var s=scene();seedDirty(s,6);var b=body(s);var observer=observer(b);var operations=new ArrayList<String>();
        var history=new AtomicReference<CdPlayerBody.History>();var cut=new AtomicReference<CdReservedObjectBank.Image>();
        var sentinel=new IllegalStateException("after-first-actual-active-star-draw");
        set(b,"observer",new Forward(observer){
            @Override public void beforeOperation(Object call){
                if(call instanceof CdTimeStars.Publication c && c.source().identity().wholeIndex()==13 && cut.get()==null){cut.set(c.source());history.set(s.frame().state().history());}
                if(call instanceof CdTimeStars.PowerupReadCall)operations.add("read");
                if(call instanceof CdTimeStars.PowerupWriteCall c)operations.add("write"+c.value());
                if(call instanceof CdTimeStars.DrawCall)operations.add("draw");super.beforeOperation(call);
            }
            @Override public void afterOperation(Object call){super.afterOperation(call);if(call instanceof CdTimeStars.DrawCall)throw sentinel;}
        });
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),input(12885))));
        assertNotNull(history.get());assertEquals(0x86,rawLevel(s).powerup());
        assertEquals(List.of("read","write6","read","write134","draw"),operations);
        var first=cut.get();int delta=(((first.byteAt(0x1C)-5)&255)<<3);delta=(delta*3+4)&255;
        int offset=(short)((history.get().index()&0xFF00)|((history.get().index()-delta-first.byteAt(0x30))&255));
        var live=raw(s).slice(12*64,64);assertEquals(history.get().bytes().unsignedWord(offset),live.unsignedWord(8));
        assertEquals(history.get().bytes().unsignedWord(offset+2),live.unsignedWord(0x0C));
        // Original host failure ends this constructed active prefix. No failed-cut forward,
        // selected active pixels or later LoadShieldArt/current readiness is inferred.
        assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void constructedDirtyRoutineTwoKeepsNativeUnsupportedSelectionAfterRealPrimaryAndEarlierInit() throws Exception {
        var s=scene();seedDirty(s,2);var before=raw(s);
        var e=assertThrows(CdTimeStars.UnsupportedTimeStars.class,()->step(s.h(),input(12885)));
        assertEquals(CdTimeStars.UnsupportedBoundary.ROUTINE_DISPATCH,e.boundary());assertEquals(13,e.wholeIndex());
        assertArrayEquals(init(patched(before.slice(11*64,64),12)).copy(),raw(s).slice(11*64,64).copy());
        for(int whole=13;whole<16;whole++)assertArrayEquals(patched(before.slice((whole-1)*64,64),whole).copy(),raw(s).slice((whole-1)*64,64).copy());
        retired(s);
    }

    @Test void originalPendingCloneDuplicateAndOutOfPrimitiveSameValueWritesCannotMintAuthority() throws Exception {
        var s=scene();var b=body(s);var o=observer(b);var bank=bank(s);var count=new int[1];
        set(b,"observer",new Forward(o){
            @Override public void beforeOperation(Object c){
                assertFalse(b.isCurrentPrimitive(c));assertThrows(IllegalStateException.class,()->o.beforeOperation(cloneRecord(c,Map.of())));
                var p=(CdTimeStars.Publication)c;assertThrows(IllegalStateException.class,()->bank.write(p.source()));
                super.beforeOperation(c);assertThrows(IllegalStateException.class,()->o.beforeOperation(c));
                assertThrows(IllegalStateException.class,()->b.run(12,new CdTimeStars.Globals(0,1,0,CdAssetBytes.copyOf(new byte[256]),0)));
                assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runRing",new Class<?>[]{int.class},127));count[0]++;
            }
            @Override public void afterOperation(Object c){super.afterOperation(c);assertThrows(IllegalStateException.class,()->o.afterOperation(c));
                assertThrows(IllegalStateException.class,()->bank.write(((CdTimeStars.Publication)c).output()));}
        });replayNext(s);assertEquals(12,count[0]);retired(s);
    }

    @Test void skippedPreflightCannotPublishTheFirstInit() throws Exception {
        var s=scene();var entry=raw(s);var b=body(s);var o=observer(b);
        set(b,"observer",new Forward(o){@Override public void beforeOperation(Object c){}});
        assertThrows(IllegalStateException.class,()->step(s.h(),input(12885)));
        for(int whole=12;whole<16;whole++)assertArrayEquals(patched(entry.slice((whole-1)*64,64),whole).copy(),raw(s).slice((whole-1)*64,64).copy());retired(s);
    }

    @Test void skippedCompletionStopsAfterTheFirstActualInitBeforeTheNextPhysicalRead() throws Exception {
        var s=scene();var entry=raw(s);var b=body(s);var o=observer(b);
        set(b,"observer",new Forward(o){@Override public void afterOperation(Object c){}});
        assertThrows(IllegalStateException.class,()->step(s.h(),input(12885)));
        assertArrayEquals(init(patched(entry.slice(11*64,64),12)).copy(),raw(s).slice(11*64,64).copy());
        for(int whole=13;whole<16;whole++)assertArrayEquals(patched(entry.slice((whole-1)*64,64),whole).copy(),raw(s).slice((whole-1)*64,64).copy());retired(s);
    }

    @Test void noOpPowerupReadFailsEvenWhenItReturnsTheEqualActualByte() throws Exception {
        var s=scene();seedDirty(s,6);var b=body(s);int value=rawLevel(s).powerup();
        set(b,"powerup",new CdTimeStars.PowerupPort(){public int read(){return value;}public void write(int value){fail("No later write is entitled");}});
        assertThrows(IllegalStateException.class,()->step(s.h(),input(12885)));assertEquals(value,rawLevel(s).powerup());retired(s);
    }

    @Test void noOpPowerupWriteCannotCrossTheNativeMoveReadbackCut() throws Exception {
        var s=scene();seedDirty(s,6);var b=body(s);var p=powerup(b);int value=rawLevel(s).powerup();var writes=new int[1];
        set(b,"powerup",new CdTimeStars.PowerupPort(){public int read(){return p.read();}public void write(int value){writes[0]++;}});
        assertThrows(IllegalStateException.class,()->step(s.h(),input(12885)));assertEquals(1,writes[0]);assertEquals(value,rawLevel(s).powerup());retired(s);
    }

    @Test void noOpDrawCannotCompleteAfterTheActualPowerupPrefix() throws Exception {
        var s=scene();seedDirty(s,6);var b=body(s);set(b,"draw",(CdTimeStars.DrawReserved)(bank,id)->{});
        assertThrows(IllegalStateException.class,()->step(s.h(),input(12885)));assertEquals(0x86,rawLevel(s).powerup());retired(s);
    }

    @Test void staleUnselectedRawImageBlocksTheOriginalFullOwnerPreflight() throws Exception {
        var s=scene();var b=body(s);var o=observer(b);var before=new AtomicReference<CdAssetBytes>();
        set(b,"observer",new Forward(o){@Override public void beforeOperation(Object c){
            try {var all=raw(s).copy();all[29*64+0x37]^=0x40;seedReserved(s,CdAssetBytes.copyOf(all));before.set(raw(s));}
            catch(Exception e){throw new AssertionError(e);}super.beforeOperation(c);
        }});assertThrows(IllegalStateException.class,()->step(s.h(),input(12885)));assertArrayEquals(before.get().copy(),raw(s).copy());retired(s);
    }

    @Test void staleUnselectedDynamicImageAlsoBlocksBeforeTheStarStore() throws Exception {
        var s=scene();var b=body(s);var o=observer(b);var placement=(CdObjectPlacementRuntime)actualField(s.visit(),"placement");
        var before=new AtomicReference<CdAssetBytes>();
        set(b,"observer",new Forward(o){@Override public void beforeOperation(Object c){
            before.set(rawUnchecked(s));var old=placement.pool().state(127);placement.pool().write(old.moveByte(CdObjectState.ByteField.SUBTYPE,old.subtype()^0x40));super.beforeOperation(c);
        }});assertThrows(IllegalStateException.class,()->step(s.h(),input(12885)));assertArrayEquals(before.get().copy(),raw(s).copy());retired(s);
    }

    @Test void originalFaultAfterActualAdoptionKeepsTheRealPrefixAndRetiresEveryScope() throws Exception {
        var s=scene();var b=body(s);var o=observer(b);var sentinel=new IllegalStateException("after-original-star-adoption");
        var retained=new AtomicReference<CdAssetBytes>();
        set(b,"observer",new Forward(o){@Override public void afterOperation(Object c){super.afterOperation(c);retained.set(rawUnchecked(s));throw sentinel;}});
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),input(12885))));
        assertArrayEquals(retained.get().copy(),raw(s).copy());assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void originalFaultBeforeActualAdoptionKeepsOnlyTheCompletedPrimaryPatches() throws Exception {
        var s=scene();var b=body(s);var o=observer(b);var entry=raw(s);var sentinel=new IllegalStateException("before-original-star-adoption");
        set(b,"observer",new Forward(o){@Override public void beforeOperation(Object c){throw sentinel;}});
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),input(12885))));
        for(int whole=12;whole<16;whole++)assertArrayEquals(patched(entry.slice((whole-1)*64,64),whole).copy(),raw(s).slice((whole-1)*64,64).copy());
        assertEquals(0,sentinel.getSuppressed().length);retired(s);
    }

    @Test void verificationFaultsAreSuppressedBehindTheOriginalHostFailure() throws Exception {
        var s=scene();var b=body(s);var o=observer(b);var sentinel=new IllegalStateException("original-host-star-failure");
        var placement=(CdObjectPlacementRuntime)actualField(s.visit(),"placement");
        set(b,"observer",new Forward(o){@Override public void beforeOperation(Object c){
            super.beforeOperation(c);var old=placement.pool().state(127);placement.pool().write(old.moveByte(CdObjectState.ByteField.SUBTYPE,old.subtype()^0x40));throw sentinel;
        }});
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->step(s.h(),input(12885))));
        assertTrue(sentinel.getSuppressed().length>0);retired(s);
    }

    @Test void genuineFacadeRetiresAtClosedFrameAndHasNoSecondCapturePartition() throws Exception {
        var s=scene();var bank=bank(s);var b=body(s);assertNull(actualField(bank,"slots"));
        assertEquals(123,SonicCdGameModule.Act2LoadCall.values().length);assertEquals(111,SonicCdGameModule.Act2LoadCall.TIME_STARS_INIT.ordinal());
        var before=captureNative(s.h());assertThrows(IllegalStateException.class,()->bank.capture());
        var foreign=CdReservedObjectBank.backedBy(new RawOwner());assertThrows(IllegalArgumentException.class,()->bank.write(foreign.state(12)));
        assertThrows(IllegalStateException.class,()->b.run(12,new CdTimeStars.Globals(0,0,0,CdAssetBytes.copyOf(new byte[256]),0)));
        assertNativeCapture(before,s.h());var cut=new AtomicReference<CdReservedObjectBank.Snapshot>();var o=observer(b);
        set(b,"observer",new Forward(o){@Override public void beforeOperation(Object c){if(c instanceof CdTimeStars.Publication p)cut.set(p.cut());super.beforeOperation(c);}});
        replayNext(s);s.frame().close();
        assertThrows(IllegalStateException.class,()->bank.state(12));assertThrows(IllegalStateException.class,()->bank.restore(cut.get()));
        retired(s);
    }

    @Test void labelledComponentControlsKeepDeletionZoneSixAndSourceSelectedConditionalLimits() throws Exception {
        var s=scene();var actual=body(s).assets();
        var owner=new RawOwner();var bank=CdReservedObjectBank.backedBy(owner);var source=bank.state(12).moveByteAt(0,3).moveByteAt(0x24,6).moveByteAt(0x1C,5);
        bank.write(source);var p=new int[1];var draws=new int[1];
        var component=new CdTimeStars(bank,actual,new CdTimeStars.PowerupPort(){public int read(){return p[0];}public void write(int value){p[0]=value;}},(b,id)->draws[0]++);
        var deleted=component.run(12,new CdTimeStars.Globals(0,0,0,CdAssetBytes.copyOf(new byte[256]),0));
        assertTrue(deleted.deleted());assertArrayEquals(new byte[64],deleted.state().copyBytes());assertEquals(0,draws[0]);
        bank.write(source);var e=assertThrows(CdTimeStars.UnsupportedTimeStars.class,()->component.run(12,new CdTimeStars.Globals(6,1,0,CdAssetBytes.copyOf(new byte[256]),0)));
        assertEquals(CdTimeStars.UnsupportedBoundary.LAYER_READ,e.boundary());assertEquals(source.byteAt(2)|0x80,bank.state(12).byteAt(2));
        bank.write(source);p[0]=8;component.run(12,new CdTimeStars.Globals(0,1,0,CdAssetBytes.copyOf(new byte[256]),0));
        assertEquals(0,draws[0]);assertEquals(8,p[0],"Selector>=8 skips Draw and Powerup stores");
        // Save210/restart230 stay with the unchanged helper; no World save owner is fabricated.
        var cut=bank.capture();var at210=new CdPlayerTimeWarp.Input(1,1,0,0,0x600,255,210,1);
        assertEquals(CdPlayerTimeWarp.UnsupportedBoundary.SCROLL_LOCK_WRITE,
                assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,()->CdPlayerTimeWarp.step(at210,new CdPlayerTimeWarp.Context(OptionalInt.of(0),Optional.of(cut)))).boundary());
        assertTrue(CdPlayerTimeWarp.step(at210,new CdPlayerTimeWarp.Context(OptionalInt.of(2),Optional.of(cut))).saveResult().isEmpty());
        var at230=new CdPlayerTimeWarp.Input(1,1,0,0,0x600,255,230,1);
        assertEquals(CdPlayerTimeWarp.UnsupportedBoundary.LEVEL_RESTART_WRITE,
                assertThrows(CdPlayerTimeWarp.UnsupportedTimeWarp.class,()->CdPlayerTimeWarp.step(at230,new CdPlayerTimeWarp.Context(OptionalInt.of(0),Optional.of(cut)))).boundary());
        assertEquals(0x01A9,CdPlayerTimeWarp.step(at230,new CdPlayerTimeWarp.Context(OptionalInt.of(0),Optional.of(cut),Optional.empty(),OptionalInt.of(0x56A9)))
                .restartResult().orElseThrow().finalRestartWord());
        bank.write(source);var historyFault=assertThrows(CdTimeStars.UnsupportedTimeStars.class,
                ()->component.run(12,new CdTimeStars.Globals(0,1,0,CdAssetBytes.copyOf(new byte[0]),0)));
        assertEquals(CdTimeStars.UnsupportedBoundary.HISTORY_X_ADDRESS,historyFault.boundary());
        assertEquals(source.moveByteAt(0x30,4),bank.state(12),"History fault retains the reached phase store only");
    }

    private static Scene scene() throws Exception {
        var old=ready();var h=old.harness();
        for(int call=old.returned()+1;call<=12884;call++)step(h,input(call));
        var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");var frame=h.module().requirePlayerFrame();
        assertTrue(old.frame().closed());assertFalse(frame.closed());assertEquals(0,frame.state().motion().playerControl());
        assertEquals(0,frame.environment().globals().timeWarpTimer());assertEquals(255,frame.environment().movement().gates().timeWarpDirection());
        assertEquals(1,frame.environment().movement().gates().timeZone());assertEquals(0,frame.state().motion().charge());
        assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,h.module().act2LoadState().calls().getLast());
        var s=new Scene(h,visit,frame);for(int whole=12;whole<16;whole++)assertEquals(0,raw(s).unsignedByte((whole-1)*64));retired(s);return s;
    }
    /** Exact pinned controller recipe through10419, reproduced because the original helper is private. */
    private static LogicalInputSnapshot controller(int call) {
        if(call<1 || call>10419)throw new IllegalArgumentException("Pinned programme recipe ends10419");
        if(call<=8276)return route(call);int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }
    private static LogicalInputSnapshot input(int call) {
        if(call<=10419)return controller(call);var right=controller(10419);
        if(call<11214 || call>=12671 && call<=12699)return right;
        return (call-11214)%128<32?LogicalInputSnapshot.ofPlayers(PlayerInputState.of(8,0,2,0,false,false),PlayerInputState.neutral()):right;
    }
    private static void replayNext(Scene s) throws Exception {
        var h=s.h();var registry=h.mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(h);
        var source=s.frame().stageSource();var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        step(h,input(12885));var after=captureNative(h);retired(s);
        registry.restore(full);assertNativeCapture(before,h);step(h,input(12885));assertNativeCapture(after,h);retired(s);
        registry.restoreCourse(course);assertNativeCapture(before,h);step(h,input(12885));assertNativeCapture(after,h);retired(s);
        assertSame(source,s.frame().stageSource());assertSame(world,h.mode().getWorldSession());assertSame(s.frame(),h.module().requirePlayerFrame());
        assertEquals(generation,h.mode().getLevelManager().getCompletedProductionLoadGeneration());
    }
    private static CdTimeStars body(Scene s) throws Exception {return (CdTimeStars)actualField(s.visit(),"timeStars");}
    private static CdReservedObjectBank bank(Scene s) throws Exception {return (CdReservedObjectBank)actualField(s.visit(),"timeStarBank");}
    private static CdTimeStars.DispatchObserver observer(CdTimeStars b) throws Exception {return (CdTimeStars.DispatchObserver)actualField(b,"observer");}
    private static CdTimeStars.PowerupPort powerup(CdTimeStars b) throws Exception {return (CdTimeStars.PowerupPort)actualField(b,"powerup");}
    private static CdAssetBytes raw(Scene s) throws Exception {return (CdAssetBytes)actualField(actualField(s.visit(),"values"),"hudObjects");}
    private static CdAssetBytes rawUnchecked(Scene s) {try{return raw(s);}catch(Exception e){throw new AssertionError(e);}}
    private static CdLevelGlobals rawLevel(Scene s) throws Exception {return (CdLevelGlobals)actualField(actualField(s.visit(),"values"),"level");}
    private static CdPlayerBody.ContactGlobals contact(Scene s) throws Exception {return (CdPlayerBody.ContactGlobals)actualField(actualField(actualField(s.visit(),"values"),"globals"),"contact");}
    private static List<SonicCdGameModule.Act2LoadCall> calls(Scene s){return s.h().module().act2LoadState().calls();}
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat cpu(Scene s) throws Exception {
        return ((Optional<CdObjectDrawQueue.CpuSat>)actualField(actualField(actualField(s.visit(),"values"),"loop"),"completedDraw")).orElseThrow();
    }
    private static CdAssetBytes patched(CdAssetBytes source,int whole) {byte[] b=source.copy();b[0]=3;b[0x1C]=(byte)(whole-7);return CdAssetBytes.copyOf(b);}
    private static CdAssetBytes init(CdAssetBytes source) {
        byte[] b=source.copy();b[0x24]=6;b[1]=4;b[0x18]=1;b[0x19]=0x10;putWord(b,2,0x544);
        b[4]=0;b[5]=0x21;b[6]=(byte)0xD5;b[7]=0x28;return CdAssetBytes.copyOf(b);
    }
    private static void putWord(byte[] bytes,int offset,int value){bytes[offset]=(byte)(value>>>8);bytes[offset+1]=(byte)value;}
    private static void assertFull31Publication(CdTimeStars.Publication c,CdAssetBytes actual) {
        for(int whole=1;whole<32;whole++)assertArrayEquals((whole==c.source().identity().wholeIndex()?c.output():c.cut().slots().get(whole-1)).copyBytes(),actual.slice((whole-1)*64,64).copy());
    }
    /** Explicit constructed reserved64 controls only; all other Values components retain identity. */
    private static void seedReserved(Scene s,CdAssetBytes raw) throws Exception {
        assertEquals(31*64,raw.length());Object values=actualField(s.visit(),"values");set(s.visit(),"values",cloneRecord(values,Map.of("hudObjects",raw)));
    }
    private static void seedDirty(Scene s,int routine) throws Exception {
        byte[] all=raw(s).copy();
        for(int whole=13;whole<16;whole++){
            byte[] image=new byte[64];for(int at=0;at<64;at++)image[at]=(byte)(whole*13+at*7);
            image[0]=(byte)(0x61+whole);image[0x24]=(byte)routine;image[0x1C]=0x72;image[0x30]=0;
            if(routine==6){
                image=init(CdAssetBytes.copyOf(image)).copy();image[0]=(byte)(0x61+whole);image[0x1C]=0x72;
            }
            image[0x22]=(byte)s.frame().state().motion().flags();System.arraycopy(image,0,all,(whole-1)*64,64);
        }
        seedReserved(s,CdAssetBytes.copyOf(all));
    }
    private static void retired(Scene s) throws Exception {
        for(String field:new String[]{"activeTimeStars","activePrimary","activeReserved","activeDrain","activeTitle","activeRing","activeSpring","activeMonitor","activeTunnelPath","activeFlower","activeDoor","activeChildEffect","activeLogMosqui"})assertNull(actualField(s.visit(),field),field);
        for(String field:new String[]{"timeStarsPort","drawingTimeStars","runningObjects"})assertFalse((boolean)actualField(s.visit(),field),field);
        var b=body(s);assertFalse((boolean)actualField(b,"running"));assertFalse((boolean)actualField(b,"primitiveActive"));assertNull(actualField(b,"pendingOperation"));
    }
    private static class Forward implements CdTimeStars.DispatchObserver {
        final CdTimeStars.DispatchObserver actual;Forward(CdTimeStars.DispatchObserver actual){this.actual=actual;}
        public void beforeOperation(Object c){actual.beforeOperation(c);}public void afterOperation(Object c){actual.afterOperation(c);}
        public void failedOperation(Object c,Throwable original){actual.failedOperation(c,original);}
    }
    private static Object cloneRecord(Object record,Map<String,Object> replacements) throws Exception {
        var components=record.getClass().getRecordComponents();Class<?>[] types=new Class<?>[components.length];Object[] values=new Object[components.length];
        for(int at=0;at<components.length;at++){var c=components[at];types[at]=c.getType();var a=c.getAccessor();a.setAccessible(true);values[at]=replacements.containsKey(c.getName())?replacements.get(c.getName()):a.invoke(record);}
        var constructor=record.getClass().getDeclaredConstructor(types);constructor.setAccessible(true);return constructor.newInstance(values);
    }
    private static void set(Object owner,String name,Object value) throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...args) throws Exception {
        var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(owner,args);}catch(InvocationTargetException e){var c=e.getCause();if(c instanceof RuntimeException r)throw r;if(c instanceof Error r)throw r;throw new AssertionError(c);}
    }
    /** Component/value test owner, deliberately unrelated to the actual private World facade. */
    private static final class RawOwner implements CdReservedObjectBank.RawBacking {
        CdAssetBytes bytes=CdAssetBytes.copyOf(new byte[31*64]);
        public CdAssetBytes read(){return bytes;}
        public boolean compareAndSet(CdAssetBytes expected,CdAssetBytes next){if(!Arrays.equals(bytes.copy(),expected.copy()))return false;bytes=next;return true;}
    }
}
