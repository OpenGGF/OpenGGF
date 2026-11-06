package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.TestCdR12bRouteBindings.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Genuine shared outcomes and actual source Frame ports; all ingress geometry is explicitly component data. Java UNRUN. */
class TestCdR12bRouteSolidBindings {
    @BeforeAll static void source()throws Exception{load();}
    @Test void discInitUsesActual0372AfterNativeHeaderAndNoSolidPlayerWork() {
        var h=new H(9);var before=h.p.capture();var old=h.slot();h.disc.run(33,new CdSpinningDisc.Globals(1,0x1000,0,0));var expected=old.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20937C).moveWord(TILE,0x372).moveByte(WIDTH,16).moveByte(Y_RADIUS,8);assertEquals(expected,h.slot());assertTrue(h.solids.isEmpty());assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    @Test void discSubtypeTwoFaultKeepsOnlyReachedHeaderAndDoesNotComputeSolid() {
        var h=new H(9);h.edit(h.slot().moveByte(SUBTYPE2,1));var old=h.slot();var before=h.p.capture();var failure=assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class,()->h.disc.run(33,new CdSpinningDisc.Globals(1,0x1000,0,0)));assertEquals(CdSpinningDisc.UnsupportedBoundary.TILE_ENTRY,failure.boundary());assertEquals(old.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,4).moveLong(MAPPINGS,0x20937C),h.slot());assertTrue(h.solids.isEmpty());assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    @Test void oldDiscConstructorRetainsItsStrict74ReaderEvenWithActual72Value() {
        var h=new H(9);var old=new CdSpinningDisc(h.p,CdSpinningDisc.Assets.fromAct2PastAssets(terrain.objects()),new CdPlayerMath(player.player()),solid(),h::discCommit,h,c->{throw new AssertionError("unreached");},h::draw);var e=assertThrows(CdSpinningDisc.UnsupportedSpinningDisc.class,()->old.run(33,new CdSpinningDisc.Globals(1,0x1000,0,0)));assertEquals(CdSpinningDisc.UnsupportedBoundary.TILE_ADDRESS,e.boundary());assertEquals(2,h.slot().routine());assertEquals(0,h.draws);assertTrue(h.solids.isEmpty());
    }
    @Test void discMainUsesGenuinePlainOutcomeAndFramePublicationWithoutClockOrArtService() {
        var h=new H(9);h.main(9,16,8,0x1000,0x200);var before=h.p.capture();var clock=h.f.vIntClock();var actor=h.f.captureActor();h.disc.run(33,new CdSpinningDisc.Globals(1,0x1000,0,0));assertEquals(1,h.solids.size());assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst());assertEquals(clock,h.f.vIntClock());assertEquals(actor,h.f.captureActor());assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    @Test void horizontalDoorActualRaw72InitReusesGenuineTopAndOriginalObserverReceipts() {
        var h=new H(13);var before=h.p.capture();h.door.run(33,new CdTunnelDoor.Globals(0,0,0x1000),h.doorInputs());assertEquals(0x21D6C4,h.slot().longBits(MAPPINGS));assertEquals(0x8340,h.slot().word(TILE));assertEquals(1,h.draws);assertFalse(h.solids.isEmpty());for(var o:h.solids)assertEquals(CdSolidObject.Entry.TOP_SOLID_OBJECT,((CdSolidObject.Completed)o).completedResult().entry());for(var c:h.seen)assertFalse(h.door.isCurrentCall(c));nonwrites(before,h.p,Set.of(33));
    }
    @Test void doorObserverFailureKeepsTheCompletedSourcePrefixAndRetiresOriginalCall() {
        var h=new H(13);var fault=new Fault();h.doorAfter=c->{throw fault;};var before=h.p.capture();assertSame(fault,assertThrows(Fault.class,()->h.door.run(33,new CdTunnelDoor.Globals(0,0,0x1000),h.doorInputs())));assertEquals(2,h.slot().routine());assertEquals(0,h.draws);assertTrue(h.solids.isEmpty());for(var c:h.seen)assertFalse(h.door.isCurrentCall(c));nonwrites(before,h.p,Set.of(33));
    }
    @Test void verticalInitNative72AndCompleteOutcomePreserveAllOtherBytesAndOwners() {
        var h=new H(53);var before=h.p.capture();h.runVertical();assertEquals(0x20CB52,h.slot().longBits(MAPPINGS));assertEquals(0x8340,h.slot().word(TILE));assertEquals(1,h.solids.size());assertInstanceOf(CdSolidObject.Completed.class,h.solids.getFirst());assertEquals(1,h.draws);assertFalse(h.vertical.isCurrentPrefixSolidCall(h.verticalCalls.getFirst()));nonwrites(before,h.p,Set.of(33));
    }
    @Test void verticalSubtypeFaultRetainsNativeInitHeaderBeforeSharedCall() {
        var h=new H(53);h.edit(h.slot().moveByte(SUBTYPE2,1));var old=h.slot();var before=h.p.capture();var e=assertThrows(CdTunnelDoorVertical.UnsupportedTunnelDoorVertical.class,h::runVertical);assertEquals(CdTunnelDoorVertical.UnsupportedBoundary.TILE_ID_SUBTYPE2,e.boundary());assertEquals(old.addByte(ROUTINE,2).moveLong(MAPPINGS,0x20CB52).moveByte(PRIORITY,1).moveByte(SPRITE_FLAGS,old.spriteFlags()|4).moveByte(WIDTH,4).moveByte(Y_RADIUS,24),h.slot());assertTrue(h.solids.isEmpty());assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    @Test void originalVerticalPrefixCallHasExactReferenceIdentityAndCannotReenterOrRemainPending() {
        var h=new H(53);h.verticalBefore=c->{assertTrue(h.vertical.isCurrentPrefixSolidCall(c));assertFalse(h.vertical.isCurrentPrefixSolidCall(new CdTunnelDoorVertical.PrefixSolidCall(c.callCut(),c.expectedSource(),c.expectedPlayer(),c.expectedGates(),c.expectedGlobals(),c.outcome())));assertThrows(IllegalStateException.class,h::runVertical);};h.runVertical();assertFalse(h.vertical.isCurrentPrefixSolidCall(h.verticalCalls.getFirst()));
    }
    @Test void unadaptedSharedPushAddressThrowsBeforeAnyOutcomeCallbackOrDraw() {
        // Existing shared runWithPrefix adapts StandOnObject/terrain stops, not PushObject slot reads.
        // Keep this unowned prefix gap explicit rather than inventing a NativeStopped result.
        var h=new H(53,frame(0,0,0xE8,0x80,0,7));h.main(53,16,12,0x100,0x80);var before=h.p.capture();var state=h.f.state();var environment=h.f.environment();
        var thrown=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::runVertical);
        assertEquals(CdSolidObject.UnsupportedBoundary.RESERVED_SLOT_ADDRESS,thrown.boundary());assertEquals(CdSolidObject.Routine.PUSH_OBJECT,thrown.routine());assertEquals(7,thrown.slotByte());
        assertTrue(h.solids.isEmpty());assertTrue(h.verticalCalls.isEmpty());assertEquals(0,h.draws);assertEquals(state,h.f.state());assertEquals(environment,h.f.environment());assertEquals(before.pool().slots().get(1).moveWord(TUNNEL_DOOR_TIMER,0).moveByte(ROUTINE,4).moveByte(MAP_FRAME,1),h.slot());assertEquals(List.of("fm"),h.order);nonwrites(before,h.p,Set.of(33));
    }
    @Test void genuineSharedStandReleaseStopPublishesItsOriginalPrefixAndRethrowsBeforeDraw() {
        // Fixed constructed top contact, no coordinate search or synthetic Solid result.
        var h=new H(53,frame(8,7,0x100,0x60,0,0));h.main(53,16,12,0x100,0x80);
        h.edit(h.slot().moveWord(X_VELOCITY,0).moveWord(Y_VELOCITY,0));var before=h.p.capture();
        var thrown=assertThrows(CdSolidObject.UnsupportedSolidObject.class,h::runVertical);
        var stopped=assertInstanceOf(CdSolidObject.NativeStopped.class,h.solids.getFirst());assertEquals(CdSolidObject.StopSite.STAND_RELEASE,stopped.stop().site());
        assertSame(stopped.stop().originalException(),thrown);assertEquals(stopped.prefix().object(),h.slot());assertEquals(stopped.prefix().player(),actor(h.f));assertEquals(stopped.prefix().globals(),globals(h.f));
        assertEquals(1,h.solids.size());assertEquals(0,h.draws);assertEquals("solid",h.order.getLast());nonwrites(before,h.p,Set.of(33));assertFalse(h.vertical.isCurrentPrefixSolidCall(h.verticalCalls.getFirst()));
    }
    @Test void verticalCallbackFaultAfterGenuineAdoptionKeepsPrefixAndRetiresPending() {
        var h=new H(53);var fault=new Fault();h.verticalAfter=c->{throw fault;};var before=h.p.capture();assertSame(fault,assertThrows(Fault.class,h::runVertical));assertEquals(1,h.solids.size());assertEquals(((CdSolidObject.Completed)h.solids.getFirst()).completedResult().object(),h.slot());assertEquals(0,h.draws);assertFalse(h.vertical.isCurrentPrefixSolidCall(h.verticalCalls.getFirst()));nonwrites(before,h.p,Set.of(33));
    }
    @Test void verticalForeignPoolMutationCannotReturnSuccessOrReplayAnyOutcome() {
        var h=new H(53);h.verticalBefore=c->h.p.pool().write(h.p.pool().state(70).moveByte(ID,0x55));assertThrows(IllegalStateException.class,h::runVertical);assertEquals(0x55,h.p.pool().state(70).id());assertEquals(0,h.draws);assertEquals(1,h.verticalCalls.size());assertFalse(h.vertical.isCurrentPrefixSolidCall(h.verticalCalls.getFirst()));
    }
    @Test void verticalDrawErrorRetainsOnlyAllowedClearAndFinallyRetiresOriginalIdentity() {
        var h=new H(53);var error=new AssertionError("original draw");h.drawAfter=()->{throw error;};assertSame(error,assertThrows(AssertionError.class,h::runVertical));assertEquals(0,h.slot().spriteFlags()&128);assertFalse(h.vertical.isCurrentDrawCall(h.p.pool(),h.lastDraw));assertEquals(1,h.solids.size());
    }
    static final class H implements CdSpinningDisc.PlayerOwner,CdTunnelDoor.PrefixSolidOwner,CdTunnelDoor.DispatchObserver {
        final CdObjectPlacementRuntime p=placement();final CdAssetBytes secondary=CdAssetBytes.copyOf(new byte[64]);final CdPlayerFrameRuntime f;CdSpinningDisc disc;CdTunnelDoor door;CdTunnelDoorVertical vertical;int draws;CdObjectState.Identity lastDraw;Object current;final List<CdSolidObject.Outcome> solids=new ArrayList<>();final List<CdTunnelDoorVertical.PrefixSolidCall> verticalCalls=new ArrayList<>();final List<Object> seen=new ArrayList<>();final List<String> order=new ArrayList<>();Consumer<CdTunnelDoorVertical.PrefixSolidCall> verticalBefore=c->{},verticalAfter=c->{};Consumer<CdTunnelDoor.Publication> doorAfter=c->{};Runnable drawAfter=()->{};
        H(int id){this(id,frame(2,0,0x7000,0x7000));}H(int id,CdPlayerFrameRuntime frame){f=frame;seed(p,id);switch(id){case 9->disc=CdSpinningDisc.fromAct2PastAssets(p,terrain,new CdPlayerMath(player.player()),solid(),this::discCommit,this,c->{throw new AssertionError("unreached Disc FM");},this::draw);case 13->door=CdTunnelDoor.fromAct2PastAssets(p,terrain,()->secondary,solid(),this,this::draw,s->order.add("fm"),this);case 53->vertical=CdTunnelDoorVertical.fromAct2PastAssets(p,terrain,solid(),this::verticalCommit,this::draw,s->order.add("fm"));default->throw new AssertionError();}}
        CdObjectState slot(){return p.pool().state(33);}void edit(CdObjectState s){p.pool().write(s);}void main(int id,int width,int yr,int x,int y){edit(slot().moveByte(ID,id).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(WIDTH,width).moveByte(Y_RADIUS,yr).moveByte(SOLID_TYPE,0).moveByte(FLAGS,0).moveWord(X,x).moveWord(Y,y));}
        CdTunnelDoor.SolidInputs doorInputs(){return new CdTunnelDoor.SolidInputs(actor(f),gates(f),globals(f));}CdTunnelDoorVertical.Result runVertical(){return vertical.run(33,new CdTunnelDoorVertical.Globals(0,0,0x1000),new CdTunnelDoorVertical.SolidInputs(actor(f),gates(f),globals(f)));}
        public CdSpinningDisc.PlayerCut read(){return new CdSpinningDisc.PlayerCut(f.state(),f.environment());}public void commit(CdSpinningDisc.PlayerCall c){f.validateObjectDiscPlayerResult(c.expectedPlayer().state(),c.expectedPlayer().environment(),c.result());f.applyObjectDiscPlayerResult(c.expectedPlayer().state(),c.expectedPlayer().environment(),c.result());}
        void discCommit(CdSpinningDisc.SolidCall c){publish(c.actualCut(),c.outcome());}
        void verticalCommit(CdTunnelDoorVertical.PrefixSolidCall c){assertTrue(vertical.isCurrentPrefixSolidCall(c));verticalCalls.add(c);verticalBefore.accept(c);publish(c.callCut(),c.outcome());verticalAfter.accept(c);}
        void publish(CdObjectPool.Snapshot cut,CdSolidObject.Outcome outcome){solids.add(outcome);order.add("solid");CdObjectState object;List<CdObjectState> others;CdPlayerFrameRuntime.ObjectSolidPublication ready;
            if(outcome instanceof CdSolidObject.Completed done){var r=done.completedResult();object=r.object();others=r.otherSlots();ready=f.prepareObjectSolidResult(actor(f),globals(f),r);}else{var r=((CdSolidObject.NativeStopped)outcome).prefix();object=r.object();others=r.otherSlots();ready=f.prepareObjectSolidPrefix(actor(f),globals(f),r);}var images=new ArrayList<CdObjectState>();images.add(object);images.addAll(others);var overlay=CdObjectPool.overlay(cut,images);p.pool().validateRestore(overlay);f.validatePreparedObjectSolidPublication(ready);images.forEach(p.pool()::write);f.applyPreparedObjectSolidPublication(ready);}
        public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.PrefixSolidCut cut){return new CdTunnelDoor.LiveSolid(actor(f),gates(f),globals(f));}public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.PrefixSolidCall c){if(c.cut().participant()==CdTunnelDoor.Participant.PRIMARY)publish(c.cut().callCut(),c.outcome());else{var done=assertInstanceOf(CdSolidObject.Completed.class,c.outcome()).completedResult();solids.add(c.outcome());order.add("secondary");var images=new ArrayList<CdObjectState>();images.add(done.object());images.addAll(done.otherSlots());var prepared=CdObjectPool.overlay(c.cut().callCut(),images);p.pool().validateRestore(prepared);images.forEach(p.pool()::write);}return new CdTunnelDoor.LiveSolid(actor(f),gates(f),globals(f));}
        void draw(CdObjectPool pool,CdObjectState.Identity identity){lastDraw=identity;if(vertical!=null){assertTrue(vertical.isCurrentDrawCall(pool,identity));assertFalse(vertical.isCurrentDrawCall(pool,new CdObjectState.Identity(identity.wholeIndex())));}if(door!=null)assertTrue(door.isCurrentCall(current));draws++;order.add("draw");clearDraw(p,pool.state(identity.wholeIndex()));drawAfter.run();}
        public void beforePublication(CdTunnelDoor.Publication c){assertTrue(door.isCurrentCall(c));seen.add(c);}public void afterPublication(CdTunnelDoor.Publication c){assertTrue(door.isCurrentCall(c));doorAfter.accept(c);}public void beforeCall(Object c){assertTrue(door.isCurrentCall(c));current=c;seen.add(c);}public void afterCall(Object c,Object result){assertTrue(door.isCurrentCall(c));current=null;}
    }
}
