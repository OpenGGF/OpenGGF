package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.ready;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Public GameLoop/controller ingress, then explicitly constructed dynamic/reserved object cuts.
 * No failed13063 state, trace row, private Snapshot token or receipt supplies ingress/authority.
 * Uses real setup/audio/cleanup. Source-only authoring: every Java case is UNRUN.
 * N1ecc5f3a Monitor/Main419-638; Powerup/Main21-70,137-186; Object Functions11-33,91-196.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com> */
@Isolated
class TestSonicCdAct2MonitorItemShieldWorld {
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}
    private record Scene(Harness h,SonicCdAct2LoadingVisit visit,CdPlayerFrameRuntime frame,CdObjectPlacementRuntime placement) { }

    @Test void constructedItemInitUsesActualOddStaticPointerMotionAndLaterDrainWithBothReplays() throws Exception {
        var s=scene();var source=seedItem(s,127,0,2,-17);var b=item(s);var o=itemObserver(b);
        var operations=new ArrayList<Class<?>>();var completed=new AtomicReference<CdMonitorItem.Result>();
        set(b,"observer",new ItemForward(o){
            @Override public void beforeOperation(Object c){operations.add(c.getClass());super.beforeOperation(c);}
            @Override public void afterOperation(Object c){super.afterOperation(c);if(c instanceof CdMonitorItem.CompletionCall)completed.set((CdMonitorItem.Result)b.currentResult(c));}
        });
        replayNext(s);
        var actual=s.placement().pool().state(127);
        assertEquals(2,actual.routine());assertEquals(0x20A8CB,actual.longBits(MAPPINGS));assertEquals(1,actual.longBits(MAPPINGS)&1);
        assertEquals(source.longBits(X_POSITION)+(source.signedWord(X_VELOCITY)<<8),actual.longBits(X_POSITION));
        assertEquals(source.longBits(Y_POSITION)+(-0x300<<8),actual.longBits(Y_POSITION));
        assertEquals(-0x2E8,actual.signedWord(Y_VELOCITY));assertEquals(2,actual.unsignedByte(MAP_FRAME));
        assertTrue((actual.spriteFlags()&0x20)!=0);assertNotNull(completed.get());
        assertTrue(operations.contains(CdMonitorItem.MappingReadCall.class));assertTrue(operations.contains(CdMonitorItem.MotionCall.class));
        assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==127));
        assertSame(b,item(s));assertSame(actualField(s.visit(),"monitorItemAssets"),b.assets());
        retired(s);
    }

    @Test void dirtyItemMainReadsRealPrimarySupportAndPreservesEveryOtherByte() throws Exception {
        var s=scene();var source=seedItem(s,127,2,2,-0x100).moveByte(FLAGS,8).moveByte(STAND_LINK_3D,0);
        s.placement().pool().write(source);var retained=source;var b=item(s);var o=itemObserver(b);var calls=new int[1];
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.MotionCall m){calls[0]++;assertEquals(Optional.of(new CdMonitorItem.PrimarySupport(s.frame().state().header().slotId(),s.frame().state().motion().flags())),m.primarySupport());}
            super.beforeOperation(c);
        }});next(s);assertEquals(1,calls[0]);
        var expected=retained.addLong(X_POSITION,retained.signedWord(X_VELOCITY)<<8)
                .addLong(Y_POSITION,retained.signedWord(Y_VELOCITY)<<8).addWord(Y_VELOCITY,24);
        assertExceptScreenBit(expected,s.placement().pool().state(127));retired(s);
    }

    @Test void thirtyThirdItemAwardHasContactThenOnlyReservedIdThenSingleFmAndEarlierSlotWaits() throws Exception {
        var s=scene();seedItem(s,127,0,2,0);byte[] dirty=new byte[64];
        for(int at=1;at<64;at++)dirty[at]=(byte)(at*7+3);
        dirty[0x24]=0;dirty[0x1C]=0;seedReserved(s,6,CdAssetBytes.copyOf(dirty));
        var b=item(s);var o=itemObserver(b);var awards=new int[1];
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.ShieldCall a){
                awards[0]++;assertEquals(4,a.source().routine());assertEquals(29,a.source().word(MONITOR_ITEM_DELETE));
                assertEquals(0,rawUnchecked(s).unsignedByte(5*64));
            }super.beforeOperation(c);
        }@Override public void afterOperation(Object c){super.afterOperation(c);if(c instanceof CdMonitorItem.ShieldCall){
            assertEquals(1,assertDoesNotThrow(()->contact(s)).shield());
            byte[] expected=dirty.clone();expected[0]=3;assertArrayEquals(expected,rawUnchecked(s).slice(5*64,64).copy());
            assertEquals(1,assertDoesNotThrow(()->ledger(s)).stream().filter(SonicCdGameModule.PastMonitorItemShieldFm.class::isInstance).count());
        }}});
        for(int call=1;call<=32;call++){next(s);assertEquals(0,awards[0]);}
        var powerup=level(s).powerup();next(s);
        assertEquals(1,awards[0]);assertEquals(4,s.placement().pool().state(127).routine());
        assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));assertEquals(0,raw(s).unsignedByte(5*64+0x24));
        assertEquals(powerup,level(s).powerup());assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.SHIELD_BODY));
        assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_SHIELD_AWARD));
        // Whole6 has already been visited; no recursive Shield/art work is entitled.
        next(s);assertEquals(2,raw(s).unsignedByte(5*64+0x24));assertEquals(28,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));
        assertTrue(calls(s).contains(SonicCdGameModule.Act2LoadCall.SHIELD_INIT));retired(s);
    }

    @Test void fullDynamicPoolNeedsNoAllocationAndAnyPhysicalItemCanAward() throws Exception {
        var s=scene();
        for(int whole=32;whole<128;whole++)seedItem(s,whole,2,2,-0x100);
        seedItem(s,127,2,2,0);assertTrue(s.placement().pool().findObjSlot().isEmpty());
        next(s);assertEquals(3,raw(s).unsignedByte(5*64));assertTrue(s.placement().pool().findObjSlot().isEmpty());
        assertEquals(1,ledger(s).stream().filter(SonicCdGameModule.PastMonitorItemShieldFm.class::isInstance).count());retired(s);
    }

    @Test void unsupportedRewardKeepsRoutineFourWord29WithoutShieldStores() throws Exception {
        var s=scene();seedItem(s,127,2,1,0);var before=raw(s);var contact=contact(s);
        var failure=assertThrows(CdMonitorItem.UnsupportedMonitorItem.class,()->next(s));
        assertEquals(CdMonitorItem.UnsupportedBoundary.REWARD_RINGS,failure.boundary());
        assertEquals(4,s.placement().pool().state(127).routine());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));
        assertArrayEquals(before.slice(5*64,64).copy(),raw(s).slice(5*64,64).copy());assertEquals(contact.shield(),contact(s).shield());
        assertTrue(ledger(s).stream().noneMatch(SonicCdGameModule.PastMonitorItemShieldFm.class::isInstance));retired(s);
    }

    @Test void shoesRemainsAnExplicitActualOwnerBoundaryAfterTheRealPrefix() throws Exception {
        var s=scene();seedItem(s,127,2,4,0);
        assertEquals(SonicCdGameModule.Act2Boundary.MONITOR_ITEM_REWARD,
                assertThrows(SonicCdGameModule.UnsupportedAct2Loading.class,()->next(s)).boundary());
        assertEquals(4,s.placement().pool().state(127).routine());assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));retired(s);
    }

    @Test void deletionStillCallsWrapperDrawWithLiveIdZero() throws Exception {
        var s=scene();seedItem(s,127,4,2,0);var entry=s.placement().pool().state(127).moveWord(MONITOR_ITEM_DELETE,0);
        s.placement().pool().write(entry);var b=item(s);var o=itemObserver(b);var draw=new int[1];
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            if(c instanceof CdMonitorItem.DrawCall d){draw[0]++;assertEquals(0,d.source().id());assertArrayEquals(new byte[64],d.source().copyBytes());}super.beforeOperation(c);
        }});next(s);assertEquals(1,draw[0]);assertEquals(0,s.placement().pool().state(127).id());retired(s);
    }

    @Test void shieldInitHasSixRealPublicationsNoReadNoDrawAndPreservesDirtyFractions() throws Exception {
        var s=scene();var raw=shieldImage(s,0);seedReserved(s,6,raw);var b=shield(s);var o=shieldObserver(b);var count=new int[1];
        set(b,"observer",new ShieldForward(o){@Override public void beforeOperation(CdShieldPowerup.Call c){
            assertInstanceOf(CdShieldPowerup.Publication.class,c);count[0]++;super.beforeOperation(c);
        }});replayNext(s);assertEquals(18,count[0],"Six Init stores per forward, full restore, course restore");
        byte[] expected=raw.copy();expected[0x24]=2;expected[1]=4;expected[0x18]=1;expected[0x19]=16;put(expected,2,0x544,2);put(expected,4,0x21D528,4);
        assertArrayEquals(expected,raw(s).slice(5*64,64).copy());assertFalse(calls(s).contains(SonicCdGameModule.Act2LoadCall.SHIELD_ACTIVE));retired(s);
    }

    @Test void shieldActiveReadsActualOwnersAnimatesAndPowerupUploadsOnlyAtNextVint() throws Exception {
        var s=scene();seedContact(s,1,0);seedReserved(s,6,shieldImage(s,2));var b=shield(s);var o=shieldObserver(b);
        var reads=new ArrayList<CdShieldPowerup.ReadField>();var writes=new ArrayList<Integer>();
        set(b,"observer",new ShieldForward(o){@Override public void beforeOperation(CdShieldPowerup.Call c){
            if(c instanceof CdShieldPowerup.ReadCall r)reads.add(r.field());
            if(c instanceof CdShieldPowerup.PowerupWriteCall w){writes.add(w.value());}
            super.beforeOperation(c);
        }});
        var beforeCpu=powerupCpu(s);replayNext(s);
        assertEquals(List.of(2,130,2,130,2,130),writes);assertEquals(0x82,level(s).powerup());
        var expected=List.of(CdShieldPowerup.ReadField.SHIELD,CdShieldPowerup.ReadField.TIME_WARP,CdShieldPowerup.ReadField.INVINCIBLE,
                CdShieldPowerup.ReadField.PLAYER_X_WORD,CdShieldPowerup.ReadField.PLAYER_Y_WORD,CdShieldPowerup.ReadField.PLAYER_FLAGS,CdShieldPowerup.ReadField.ZONE);
        assertEquals(expected,reads.subList(0,7));assertFalse(reads.contains(CdShieldPowerup.ReadField.LAYER));
        assertArrayEquals(beforeCpu.copy(),powerupCpu(s).copy(),"Body does not upload art");
        var actor=s.frame().state().motion();assertEquals(actor.centreX16_16()>>>16,raw(s).unsignedWord(5*64+8));
        assertEquals(actor.centreY16_16()>>>16,raw(s).unsignedWord(5*64+12));
        assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==6));
        var tail=powerupCpu(s).slice(1024,128);next(s);
        assertEquals(2,level(s).powerup());assertEquals(1152,powerupCpu(s).length());assertArrayEquals(tail.copy(),powerupCpu(s).slice(1024,128).copy());retired(s);
    }

    @Test void shieldDeleteAndWarpGateDoNotDrawOrEagerlyReadLaterFields() throws Exception {
        var s=scene();seedContact(s,0,0);seedReserved(s,6,shieldImage(s,2));var b=shield(s);var o=shieldObserver(b);var reads=new ArrayList<CdShieldPowerup.ReadField>();var deletionPhase=new boolean[]{true};
        set(b,"observer",new ShieldForward(o){@Override public void beforeOperation(CdShieldPowerup.Call c){
            if(c instanceof CdShieldPowerup.ReadCall r)reads.add(r.field());
            if(deletionPhase[0])assertFalse(c instanceof CdShieldPowerup.DrawCall);super.beforeOperation(c);
        }});next(s);assertEquals(List.of(CdShieldPowerup.ReadField.SHIELD),reads);assertArrayEquals(new byte[64],raw(s).slice(5*64,64).copy());
        reads.clear();deletionPhase[0]=false;seedContact(s,1,1);seedReserved(s,6,shieldImage(s,2));var before=raw(s).slice(5*64,64);next(s);
        // Primary's real time-warp owner may change contact before reserved6; compare only actually observed condition.
        if(contact(s).timeWarp()!=0){assertEquals(List.of(CdShieldPowerup.ReadField.SHIELD,CdShieldPowerup.ReadField.TIME_WARP),reads);assertArrayEquals(before.copy(),raw(s).slice(5*64,64).copy());}
        retired(s);
    }

    @Test void originalItemCallbacksRejectClonesStaleReentryCaptureRenderAndOtherFamiliesWithoutClosingHealthyFrame() throws Exception {
        var s=scene();seedItem(s,127,0,2,0);var b=item(s);var o=itemObserver(b);var seen=new int[1];var stale=new AtomicReference<Object>();
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            seen[0]++;stale.set(c);assertThrows(IllegalStateException.class,()->o.beforeOperation(cloneUnchecked(c)));
            assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().capture());
            assertThrows(IllegalStateException.class,()->s.h().mode().getRewindRegistry().captureCourse());
            assertThrows(IllegalStateException.class,()->registry.restore(full));
            assertThrows(IllegalStateException.class,()->registry.restoreCourse(course));
            assertThrows(IllegalStateException.class,()->s.h().module().act2SelectedDisplay());
            assertThrows(IllegalStateException.class,()->s.h().module().serviceAct2Loading(8));
            assertThrows(IllegalStateException.class,()->b.run(127,Optional.empty()));
            assertThrows(IllegalStateException.class,()->invoke(s.visit(),"runMonitor",new Class<?>[]{int.class},127));
            assertFalse(s.frame().closed());super.beforeOperation(c);
        }});next(s);assertTrue(seen[0]>0);assertFalse(s.frame().closed());retired(s);
        // End the in-invocation adversary before testing the genuine owner's out-of-scope rejection.
        set(b,"observer",o);
        assertThrows(IllegalStateException.class,()->b.run(127,Optional.empty()));
        assertThrows(IllegalStateException.class,()->o.beforeOperation(stale.get()));assertFalse(s.frame().closed());
    }

    @Test void skippedItemPreparationCannotPublish() throws Exception {
        var s=scene();var source=seedItem(s,127,0,2,0);var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){}});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals(source,s.placement().pool().state(127));retired(s);
    }

    @Test void skippedItemCompletionRetainsActualInitPrefixAndNoMappingPointer() throws Exception {
        var s=scene();var source=seedItem(s,127,0,2,0);var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void afterOperation(Object c){}});
        assertThrows(IllegalStateException.class,()->next(s));var actual=s.placement().pool().state(127);
        assertEquals(2,actual.routine());assertEquals(0x85A8,actual.word(TILE));assertEquals(source.longBits(MAPPINGS),actual.longBits(MAPPINGS));retired(s);
    }

    @Test void equalNoopShieldReadCannotSupplyPrivateAdoptionReceipt() throws Exception {
        var s=scene();seedContact(s,1,0);seedReserved(s,6,shieldImage(s,2));var b=shield(s);
        set(b,"reads",(CdShieldPowerup.ReadPort)field->1);
        assertThrows(IllegalStateException.class,()->next(s));retired(s);
    }

    @Test void equalNoopShieldPowerupReadCannotSupplyReceipt() throws Exception {
        var s=scene();seedContact(s,1,0);seedReserved(s,6,shieldImage(s,2));var b=shield(s);
        int actual=level(s).powerup();
        set(b,"powerup",new CdShieldPowerup.PowerupPort(){public int read(){return actual;}public void write(int v){fail("Not reached");}});
        assertThrows(IllegalStateException.class,()->next(s));retired(s);
    }

    @Test void noOpShieldDrawKeepsActualMoveBsetPrefixAndRetires() throws Exception {
        var s=scene();seedContact(s,1,0);seedReserved(s,6,shieldImage(s,2));var b=shield(s);
        set(b,"draw",(CdShieldPowerup.DrawReserved)(bank,id)->{});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals(0x82,level(s).powerup());retired(s);
    }

    @Test void unselectedDynamicGuardRejectsBeforeOriginalPublication() throws Exception {
        var s=scene();var source=seedItem(s,127,0,2,0);var b=item(s);var o=itemObserver(b);
        set(b,"observer",new ItemForward(o){@Override public void beforeOperation(Object c){
            var other=s.placement().pool().state(126);s.placement().pool().write(other.moveByte(SUBTYPE,other.subtype()^64));
            super.beforeOperation(c);
        }});assertThrows(IllegalStateException.class,()->next(s));assertEquals(source,s.placement().pool().state(127));retired(s);
    }

    @Test void originalPostAwardFailureKeepsContactReservedIdAndFmExactlyOnce() throws Exception {
        var s=scene();seedItem(s,127,2,2,0);var b=item(s);var o=itemObserver(b);var sentinel=new IllegalStateException("after-real-shield-award");
        set(b,"observer",new ItemForward(o){@Override public void afterOperation(Object c){super.afterOperation(c);if(c instanceof CdMonitorItem.ShieldCall)throw sentinel;}});
        assertSame(sentinel,assertThrows(IllegalStateException.class,()->next(s)));assertEquals(0,sentinel.getSuppressed().length);
        assertEquals(1,contact(s).shield());assertEquals(3,raw(s).unsignedByte(5*64));
        assertEquals(1,ledger(s).stream().filter(SonicCdGameModule.PastMonitorItemShieldFm.class::isInstance).count());retired(s);
    }

    @Test void controllerIngressThenExplicitUnowned38StopsAfterRealItemAndExistingNonBadnikExplosion() throws Exception {
        var s=scene();for(int call=12893;call<=13062;call++)step(s.h(),input(call));
        // All ingress is the ordinary healthy controller. This next terminal is an explicitly CONSTRUCTED object cut.
        // Native ID$18 is Explosion, already bound in D221; Spikes is$26. No natural next frontier is asserted.
        var pool=s.placement().pool();var visit=s.visit();var frame=s.frame();var beforeExplosion=pool.state(37);
        assertEquals(0x18,beforeExplosion.id());assertEquals(0,beforeExplosion.routine());
        assertEquals(1,beforeExplosion.unsignedByte(ROUTINE_2));assertEquals(1,beforeExplosion.subtype());
        pool.write(pool.state(38).moveByte(ID,0x7F));
        // Keep references before forward. Failure closes Frame: no capture/public observation afterward.
        var failure=assertThrows(SonicCdGameModule.UnsupportedAct2Loading.class,()->step(s.h(),input(13063)));
        assertEquals(SonicCdGameModule.Act2Boundary.DYNAMIC_OBJECTS,failure.boundary());
        assertEquals(0x7F,pool.state(38).id());
        assertEquals(0x1A,pool.state(32).id());assertEquals(2,pool.state(32).routine());assertEquals(0x20A8CB,pool.state(32).longBits(MAPPINGS));
        assertEquals(0x18,pool.state(37).id());assertEquals(2,pool.state(37).routine());assertEquals(0x21D714,pool.state(37).longBits(MAPPINGS));
        assertEquals(1,pool.state(37).unsignedByte(ANIMATION));
        @SuppressWarnings("unchecked") var completed=(List<SonicCdGameModule.Act2LoadCall>)actualField(actualField(visit,"values"),"calls");
        assertTrue(completed.contains(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_BODY));
        assertTrue(completed.lastIndexOf(SonicCdGameModule.Act2LoadCall.EXPLOSION_BODY)>completed.indexOf(SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_BODY));
        assertFalse(completed.contains(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN));
        assertTrue(frame.closed());retired(s);
    }

    @Test void exactAppendOnlyEnumAndRetiredFacadeCannotCreateASecondOwner() throws Exception {
        var s=scene();assertEquals(119,SonicCdGameModule.Act2LoadCall.values().length);
        assertEquals(111,SonicCdGameModule.Act2LoadCall.TIME_STARS_INIT.ordinal());assertEquals(114,SonicCdGameModule.Act2LoadCall.MONITOR_ITEM_BODY.ordinal());
        var bank=(CdReservedObjectBank)actualField(s.visit(),"timeStarBank");assertNull(actualField(bank,"slots"));
        assertThrows(IllegalStateException.class,bank::capture);assertSame(actualField(s.visit(),"timeStarsAssets"),shield(s).assets());
        assertThrows(IllegalStateException.class,()->shield(s).run(6));assertFalse(s.frame().closed());retired(s);
    }


    @Test void allEightActualStaticIconsDrainWithoutReplacingTheCountedTables() throws Exception {
        var s=scene();int[] pointers={0x20A89F,0x20A8B5,0x20A8CB,0x20A8E1,0x20A8F7,0x20A90D,0x20A923,0x20A939};
        for(int icon=0;icon<8;icon++)seedItem(s,120+icon,0,icon,0);
        next(s);
        for(int icon=0;icon<8;icon++){
            assertEquals(pointers[icon],s.placement().pool().state(120+icon).longBits(MAPPINGS));
            int whole=120+icon;assertTrue(cpu(s).provenance().stream().anyMatch(p->p.slotRef().wholeIndex()==whole));
        }
        retired(s);
    }
    @Test void unselectedReservedGuardRejectsOriginalShieldPublicationBeforeAnyStore() throws Exception {
        var s=scene();var source=shieldImage(s,0);seedReserved(s,6,source);var b=shield(s);var o=shieldObserver(b);
        set(b,"observer",new ShieldForward(o){@Override public void beforeOperation(CdShieldPowerup.Call c){
            try {byte[] all=raw(s).copy();all[29*64+0x37]^=64;
                set(s.visit(),"values",cloneRecord(actualField(s.visit(),"values"),Map.of("hudObjects",CdAssetBytes.copyOf(all))));
            } catch(Exception e){throw new AssertionError(e);}super.beforeOperation(c);
        }});assertThrows(IllegalStateException.class,()->next(s));assertArrayEquals(source.copy(),raw(s).slice(5*64,64).copy());retired(s);
    }
    @Test void missingShieldCompletionRetainsOnlyItsFirstActualRoutineStore() throws Exception {
        var s=scene();var source=shieldImage(s,0);seedReserved(s,6,source);var b=shield(s);var o=shieldObserver(b);
        set(b,"observer",new ShieldForward(o){@Override public void afterOperation(CdShieldPowerup.Call c){}});
        assertThrows(IllegalStateException.class,()->next(s));byte[] expected=source.copy();expected[0x24]=2;
        assertArrayEquals(expected,raw(s).slice(5*64,64).copy());retired(s);
    }
    @Test void reservedSupportReadStopsAtItsActualMotionBoundaryWithoutMovingItem() throws Exception {
        var s=scene();var source=seedItem(s,127,2,2,-0x100).moveByte(FLAGS,8).moveByte(STAND_LINK_3D,5);
        s.placement().pool().write(source);
        assertEquals(CdPlayerMovement.UnsupportedBoundary.RESERVED_OBJECT_SLOT,
                assertThrows(CdPlayerMovement.UnsupportedMotion.class,()->next(s)).boundary());
        assertEquals(source,s.placement().pool().state(127));retired(s);
    }


    @Test void publicEqualShieldAwardResultCannotStandInForActualStoresAndLedger() throws Exception {
        var s=scene();seedItem(s,127,2,2,0);var before=raw(s).slice(5*64,64);var b=item(s);
        set(b,"shieldAward",(CdMonitorItem.ShieldAwardPort)c->new CdMonitorItem.ShieldAwardResult(CdMonitorItem.ShieldSound.FM_SHIELD));
        assertThrows(IllegalStateException.class,()->next(s));assertArrayEquals(before.copy(),raw(s).slice(5*64,64).copy());
        assertEquals(29,s.placement().pool().state(127).word(MONITOR_ITEM_DELETE));
        assertTrue(ledger(s).stream().noneMatch(SonicCdGameModule.PastMonitorItemShieldFm.class::isInstance));retired(s);
    }


    @Test void noOpShieldPowerupWriteCannotCrossTheActualMoveReadbackCut() throws Exception {
        var s=scene();seedContact(s,1,0);seedReserved(s,6,shieldImage(s,2));var b=shield(s);
        var port=(CdShieldPowerup.PowerupPort)actualField(b,"powerup");var previous=new int[1];var writes=new int[1];
        set(b,"powerup",new CdShieldPowerup.PowerupPort(){public int read(){previous[0]=port.read();return previous[0];}public void write(int value){writes[0]++;}});
        assertThrows(IllegalStateException.class,()->next(s));assertEquals(1,writes[0]);assertEquals(previous[0],level(s).powerup());retired(s);
    }
    @Test void shieldOriginalCallbacksRejectPublicClonesAndDirectBankWrites() throws Exception {
        var s=scene();seedReserved(s,6,shieldImage(s,0));var b=shield(s);var o=shieldObserver(b);
        var bank=(CdReservedObjectBank)actualField(s.visit(),"timeStarBank");
        set(b,"observer",new ShieldForward(o){@Override public void beforeOperation(CdShieldPowerup.Call c){
            assertThrows(IllegalStateException.class,()->o.beforeOperation((CdShieldPowerup.Call)cloneUnchecked(c)));
            super.beforeOperation(c);
            var publication=(CdShieldPowerup.Publication)c;
            assertThrows(IllegalStateException.class,()->bank.write(publication.output()));
        }});next(s);assertFalse(s.frame().closed());retired(s);
    }

    private static Scene scene() throws Exception {
        var old=ready();var h=old.harness();
        for(int call=old.returned()+1;call<=12892;call++)step(h,input(call));
        var visit=(SonicCdAct2LoadingVisit)actualField(h.module(),"act2Loading");
        var s=new Scene(h,visit,h.module().requirePlayerFrame(),(CdObjectPlacementRuntime)actualField(visit,"placement"));
        assertFalse(s.frame().closed());assertEquals(SonicCdGameModule.Act2LoadCall.ORDINARY_MAIN_RETURN,calls(s).getLast());
        assertEquals(0,s.frame().environment().movement().gates().zoneAct()>>>8);retired(s);return s;
    }
    /** Same source-root recipe as D221 TimeStarsWorld; no failed state or diagnostic controller rows. */
    private static LogicalInputSnapshot input(int call) {
        if(call<=8276)return route(call);
        int directions=8,actions=call<=8308?2:0;
        if(call>=9001 && call<=9203)directions=4;
        else if(call>=9206 && call<=9217)directions=0;
        else if(call>=9218 && call<=9263){directions=2;actions=call==9219?2:0;}
        else if(call>=9336 && call<=9367)actions=2;
        if(call>10419 && call>=11214 && !(call>=12671 && call<=12699))actions=(call-11214)%128<32?2:0;
        return LogicalInputSnapshot.ofPlayers(PlayerInputState.of(directions,0,actions,0,false,false),PlayerInputState.neutral());
    }
    private static void next(Scene s){step(s.h(),input(12893));}
    private static void replayNext(Scene s) throws Exception {
        var registry=s.h().mode().getRewindRegistry();var full=registry.capture();var course=registry.captureCourse();var before=captureNative(s.h());
        var source=s.frame().stageSource();var world=s.h().mode().getWorldSession();long generation=s.h().mode().getLevelManager().getCompletedProductionLoadGeneration();
        next(s);var after=captureNative(s.h());retired(s);
        registry.restore(full);assertNativeCapture(before,s.h());next(s);assertNativeCapture(after,s.h());
        registry.restoreCourse(course);assertNativeCapture(before,s.h());next(s);assertNativeCapture(after,s.h());
        assertSame(source,s.frame().stageSource());assertSame(world,s.h().mode().getWorldSession());
        assertSame(s.frame(),s.h().module().requirePlayerFrame());assertEquals(generation,s.h().mode().getLevelManager().getCompletedProductionLoadGeneration());retired(s);
    }
    /** Constructed object bytes only. Preserve all other rows, placement status and saved bank. */
    private static CdObjectState seedItem(Scene s,int whole,int routine,int animation,int velocity) {
        var fg=s.h().module().act2LoadState().actor().state().motion();
        var row=s.placement().pool().state(whole).moveByte(ID,0x1A).moveByte(ROUTINE,routine).moveByte(ANIMATION,animation)
                .moveByte(SPRITE_FLAGS,0x24).moveByte(PRIORITY,3).moveByte(WIDTH,8).moveByte(FLAGS,0).moveByte(LAYER,0)
                .moveLong(MAPPINGS,0x20A8CB).moveLong(X_POSITION,fg.centreX16_16()).moveLong(Y_POSITION,fg.centreY16_16())
                .moveWord(X_VELOCITY,0x80).moveWord(Y_VELOCITY,velocity).moveWord(MONITOR_ITEM_DELETE,10);
        s.placement().pool().write(row);return row;
    }
    private static CdAssetBytes shieldImage(Scene s,int routine) {
        byte[] bytes=new byte[64];for(int at=0;at<64;at++)bytes[at]=(byte)(at*3+5);
        bytes[0]=3;bytes[0x24]=(byte)routine;bytes[0x1C]=0;bytes[0x1D]=0;bytes[0x1B]=0;bytes[0x1E]=0;
        bytes[1]=4;bytes[0x18]=1;bytes[0x19]=16;put(bytes,2,0x544,2);put(bytes,4,0x21D528,4);return CdAssetBytes.copyOf(bytes);
    }
    private static void seedReserved(Scene s,int whole,CdAssetBytes image) throws Exception {
        byte[] all=raw(s).copy();System.arraycopy(image.copy(),0,all,(whole-1)*64,64);
        set(s.visit(),"values",cloneRecord(actualField(s.visit(),"values"),Map.of("hudObjects",CdAssetBytes.copyOf(all))));
    }
    private static void seedContact(Scene s,int shield,int warp) throws Exception {
        var values=actualField(s.visit(),"values");var globals=actualField(values,"globals");var c=contact(s);
        set(s.visit(),"values",cloneRecord(values,Map.of("globals",cloneRecord(globals,Map.of("contact",new CdPlayerBody.ContactGlobals(warp,shield,c.combineRing()))))));
    }
    private static void put(byte[] b,int at,int value,int width){for(int j=0;j<width;j++)b[at+j]=(byte)(value>>>((width-1-j)*8));}
    private static void assertExceptScreenBit(CdObjectState expected,CdObjectState actual){
        assertEquals(expected.moveByte(SPRITE_FLAGS,(expected.spriteFlags()&127)|(actual.spriteFlags()&128)),actual);
    }
    private static CdMonitorItem item(Scene s) throws Exception{return (CdMonitorItem)actualField(s.visit(),"monitorItem");}
    private static CdShieldPowerup shield(Scene s) throws Exception{return (CdShieldPowerup)actualField(s.visit(),"shield");}
    private static CdMonitorItem.DispatchObserver itemObserver(CdMonitorItem b) throws Exception{return (CdMonitorItem.DispatchObserver)actualField(b,"observer");}
    private static CdShieldPowerup.DispatchObserver shieldObserver(CdShieldPowerup b) throws Exception{return (CdShieldPowerup.DispatchObserver)actualField(b,"observer");}
    private static Object rawValue(Scene s,String name) throws Exception{return actualField(actualField(s.visit(),"values"),name);}
    private static CdAssetBytes raw(Scene s) throws Exception{return (CdAssetBytes)rawValue(s,"hudObjects");}
    private static CdAssetBytes rawUnchecked(Scene s){try{return raw(s);}catch(Exception e){throw new AssertionError(e);}}
    private static CdLevelGlobals level(Scene s) throws Exception{return (CdLevelGlobals)rawValue(s,"level");}
    private static CdPlayerBody.ContactGlobals contact(Scene s) throws Exception{return (CdPlayerBody.ContactGlobals)actualField(rawValue(s,"globals"),"contact");}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.PastSound> ledger(Scene s) throws Exception{return (List<SonicCdGameModule.PastSound>)actualField(s.visit(),"sounds");}
    @SuppressWarnings("unchecked") private static List<SonicCdGameModule.Act2LoadCall> calls(Scene s) throws Exception{return (List<SonicCdGameModule.Act2LoadCall>)rawValue(s,"calls");}
    @SuppressWarnings("unchecked") private static CdAssetBytes powerupCpu(Scene s) throws Exception{return ((Optional<CdAssetBytes>)actualField(rawValue(s,"loop"),"powerupCpu")).orElseThrow();}
    @SuppressWarnings("unchecked") private static CdObjectDrawQueue.CpuSat cpu(Scene s) throws Exception{return ((Optional<CdObjectDrawQueue.CpuSat>)actualField(rawValue(s,"loop"),"completedDraw")).orElseThrow();}
    private static void retired(Scene s) throws Exception {
        for(String field:new String[]{"activeItemShield","activeTimeStars","activePrimary","activeDrain","activeReserved","activeMonitor"})assertNull(actualField(s.visit(),field),field);
        for(String field:new String[]{"itemShieldPort","drawingItemShield","runningObjects"})assertFalse((boolean)actualField(s.visit(),field),field);
        for(Object b:new Object[]{item(s),shield(s)}){assertFalse((boolean)actualField(b,"running"));assertFalse((boolean)actualField(b,"primitiveActive"));assertNull(actualField(b,"pendingOperation"));}
    }
    private static class ItemForward implements CdMonitorItem.DispatchObserver {
        final CdMonitorItem.DispatchObserver actual;ItemForward(CdMonitorItem.DispatchObserver actual){this.actual=actual;}
        public void beforeOperation(Object c){actual.beforeOperation(c);}public void afterOperation(Object c){actual.afterOperation(c);}
        public void failedOperation(Object c,Throwable t){actual.failedOperation(c,t);}
    }
    private static class ShieldForward implements CdShieldPowerup.DispatchObserver {
        final CdShieldPowerup.DispatchObserver actual;ShieldForward(CdShieldPowerup.DispatchObserver actual){this.actual=actual;}
        public void beforeOperation(CdShieldPowerup.Call c){actual.beforeOperation(c);}public void afterOperation(CdShieldPowerup.Call c){actual.afterOperation(c);}
        public void failedOperation(CdShieldPowerup.Call c,Throwable t){actual.failedOperation(c,t);}
    }
    private static Object cloneUnchecked(Object r){try{return cloneRecord(r,Map.of());}catch(Exception e){throw new AssertionError(e);}}
    private static Object cloneRecord(Object r,Map<String,Object> changes) throws Exception {
        var cs=r.getClass().getRecordComponents();Class<?>[] types=new Class<?>[cs.length];Object[] values=new Object[cs.length];
        for(int j=0;j<cs.length;j++){var c=cs[j];types[j]=c.getType();var a=c.getAccessor();a.setAccessible(true);values[j]=changes.containsKey(c.getName())?changes.get(c.getName()):a.invoke(r);}
        var ctor=r.getClass().getDeclaredConstructor(types);ctor.setAccessible(true);return ctor.newInstance(values);
    }
    private static void set(Object owner,String name,Object value) throws Exception {var f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...args) throws Exception {
        var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(owner,args);}catch(InvocationTargetException e){var c=e.getCause();if(c instanceof RuntimeException r)throw r;if(c instanceof Error r)throw r;throw new AssertionError(c);}
    }
}
