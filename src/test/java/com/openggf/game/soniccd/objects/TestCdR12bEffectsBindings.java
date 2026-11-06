package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual USA assets plus labelled component ingresses; callbacks grant no World authority. Java UNRUN. */
class TestCdR12bEffectsBindings {
    static CdR12bTerrainAssets terrain,equalTerrain;static CdR12bPlayerAssets player;
    @BeforeAll static synchronized void load() throws Exception {
        if(terrain!=null)return;
        String root=System.getProperty("soniccd.media.root");org.junit.jupiter.api.Assumptions.assumeTrue(root!=null&&!root.isBlank(),"Explicit Sonic CD media root");Path cue;
        try(var files=Files.list(Path.of(root).resolve("USA"))){var cues=files.filter(f->f.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();}
        try(var programs=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){terrain=CdR12bTerrainAssets.fromPrograms(programs);equalTerrain=CdR12bTerrainAssets.fromPrograms(programs);player=CdR12bPlayerAssets.fromPrograms(programs);}
    }
    @Test void actualCachedAssetsKeepNativeFlowerOperandAndLegacyValueShapes() {
        var o=terrain.objects();var e=CdExplosion.Assets.fromAct2PastAssets(o);var f=CdFlower.Assets.fromAct2PastAssets(o);var p=CdPoints.Assets.fromAct2PastAssets(o);
        assertSame(o.explosion(),e.explosion());assertSame(o.flower(),f.flower());assertSame(o.points(),p.points());
        assertEquals(0x21D714,e.mappingsAddress());assertEquals(0x236AB0,f.mappingsAddress());assertNotEquals(0x21DF92,f.mappingsAddress());assertEquals(0x20AA1A,p.mappingsAddress());
        assertEquals(2,CdExplosion.Assets.class.getRecordComponents().length);assertEquals(2,CdFlower.Assets.class.getRecordComponents().length);assertEquals(2,CdPoints.Assets.class.getRecordComponents().length);
    }
    @Test void exactPairAdmissionPrecedesMetadataCallbacksAndHelpers() throws Exception {
        var p=CdObjectPlacementRuntime.fromAssets(terrain,bank());var before=p.capture();
        for(var foreign:List.of(new CdObjectPlacementRuntime(terrain.placements()),CdObjectPlacementRuntime.fromAssets(equalTerrain,bank()))){
            assertThrows(IllegalArgumentException.class,()->CdExplosion.fromAct2PastAssets(foreign,terrain,null,null,null));
            assertThrows(IllegalArgumentException.class,()->CdFlower.fromAct2PastAssets(foreign,terrain,null,null,null,null,null));
            assertThrows(IllegalArgumentException.class,()->CdPoints.fromAct2PastAssets(foreign,terrain,null,null,null));
            assertThrows(IllegalArgumentException.class,()->CdMonitorItem.fromAct2PastAssets(foreign,terrain,null,null,null,null,null,null));
        }
        nonwrites(before,p,Set.of());assertThrows(NullPointerException.class,()->CdExplosion.fromAct2PastAssets(null,terrain,null,null,null));
    }
    @Test void constructorsDoNotDispatchOrWrite() {
        for(int id:new int[]{0x18,0x1F,0x1C,0x1A}){var h=new H(id);var before=h.p.capture();h.bind();nonwrites(before,h.p,Set.of());assertTrue(h.seen.isEmpty());assertEquals(0,h.draws);}
    }
    @Test void explosionActualInitThenAnimationHasNoOtherWritesWhenPointsAreSuppressed() {
        var h=new H(0x18);h.edit(h.slot().moveByte(ROUTINE_2,1));h.bind();var before=h.p.capture();var r=((CdExplosion)h.body).run(33);
        assertEquals(0x21D714,r.state().longBits(MAPPINGS));assertEquals(0x8680,r.state().word(TILE));assertEquals(2,r.state().routine());assertEquals(1,h.draws);assertEquals(1,r.state().unsignedByte(PRIORITY));nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void explosionAllocatesActualPointsInFirstFreeDirtyPhysicalSlotWithoutRedispatch() {
        var h=new H(0x18);h.edit(h.slot().moveByte(ROUTINE_2,0));h.bind();var before=h.p.capture();var child=h.p.pool().state(32);((CdExplosion)h.body).run(33);
        var out=h.p.pool().state(32);assertEquals(0x1C,out.id());assertEquals(child.routine(),out.routine());assertEquals(child.longBits(X_POSITION)&0xFFFF,out.longBits(X_POSITION)&0xFFFF);assertEquals(h.slot().centreX(),out.centreX());assertTrue((out.subtype()&0x80)!=0);assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(32,33));
    }
    @Test void explosionDoneConvertsSameSlotToFlowerWithoutDrawingOrDispatchingIt() {
        var h=new H(0x18);h.edit(h.slot().moveByte(ROUTINE,4).moveByte(ROUTINE_2,0));h.bind();var before=h.p.capture();var expected=h.slot().moveByte(ID,0x1F).moveByte(ROUTINE,0);var r=((CdExplosion)h.body).run(33);
        assertEquals(expected,r.state());assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    @Test void flowerSubtypeZeroRetainsTheExistingExplicitSavedStorageBoundary() {
        var h=new H(0x1F);h.edit(h.slot().moveByte(SUBTYPE,0));h.bind();var before=h.p.capture();var failure=assertThrows(CdFlower.UnsupportedFlower.class,()->((CdFlower)h.body).run(33));
        assertEquals(CdFlower.UnsupportedBoundary.SAVED_FLAGS_INIT,failure.boundary());assertEquals(0x236AB0,h.slot().longBits(MAPPINGS));assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void flowerGenuineTerrainProbePublishesOriginalAngleCallsBeforeDraw() {
        var h=new H(0x1F);h.edit(h.slot().moveByte(SUBTYPE,1));h.bind();var before=h.p.capture();((CdFlower)h.body).run(33);
        assertEquals(0x236AB0,h.slot().longBits(MAPPINGS));assertTrue(h.angles>0);assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void pointsInitUsesActualMapAndIntegerRisePreservesFractionAndFullPool() {
        var h=new H(0x1C);h.edit(h.slot().moveByte(SUBTYPE,0x83));h.bind();var before=h.p.capture();var old=h.slot();var r=((CdPoints)h.body).run(33);
        var expected=old.addByte(ROUTINE,2).moveByte(SPRITE_FLAGS,(old.spriteFlags()|4)&127).moveWord(TILE,0x6C6).moveLong(MAPPINGS,0x20AA1A).moveByte(MAP_FRAME,3).moveByte(POINTS_TIMER,23).addWord(Y,-2);
        assertEquals(expected,r.state());assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    @Test void pointsDeleteStillCallsOriginalDrawOnCompletelyZeroedSlot() {
        var h=new H(0x1C);h.edit(h.slot().moveByte(SUBTYPE,0x80).moveByte(ROUTINE,2).moveByte(POINTS_TIMER,1));h.bind();var before=h.p.capture();var r=((CdPoints)h.body).run(33);
        assertArrayEquals(new byte[64],r.state().copyBytes());assertTrue(r.deleted());assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void monitorItemInitUsesNativeHeaderFreeStaticFramePointerAndExistingMotion() {
        var h=new H(0x1A);h.edit(h.slot().moveByte(ANIMATION,1).moveByte(FLAGS,0));h.bind();var before=h.p.capture();var r=((CdMonitorItem)h.body).run(33,Optional.empty());
        assertEquals(0x20A843,r.state().longBits(MAPPINGS));assertEquals(0x85A8,r.state().word(TILE));assertEquals(-0x2E8,r.state().signedWord(Y_VELOCITY));assertEquals(1,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void monitorUnownedRewardStopsAfterRealPrefixWithoutInventedAwardOrDraw() {
        var h=new H(0x1A);h.edit(h.slot().moveByte(ROUTINE,2).moveByte(ANIMATION,3).moveWord(Y_VELOCITY,0));h.bind();var before=h.p.capture();
        var e=assertThrows(CdMonitorItem.UnsupportedMonitorItem.class,()->((CdMonitorItem)h.body).run(33,Optional.empty()));assertEquals(CdMonitorItem.UnsupportedBoundary.REWARD_INVINCIBILITY,e.boundary());assertEquals(4,h.slot().routine());assertEquals(29,h.slot().word(MONITOR_ITEM_DELETE));assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));
    }
    @Test void originalPublicationExceptionRetainsCompletedPrefixAndClearsReceipt() {
        var h=new H(0x1C);h.edit(h.slot().moveByte(SUBTYPE,0x80));h.bind();var fault=new Fault();h.after=o->{throw fault;};assertSame(fault,assertThrows(Fault.class,()->((CdPoints)h.body).run(33)));assertEquals(2,h.slot().routine());assertEquals(0,h.draws);h.retired();
    }
    static final class H implements CdExplosion.DispatchObserver,CdExplosion.ReceiptGuard,CdFlower.DispatchObserver,CdFlower.ReceiptGuard,CdPoints.DispatchObserver,CdPoints.ReceiptGuard,CdMonitorItem.DispatchObserver,CdMonitorItem.ReceiptGuard {
        final CdObjectPlacementRuntime p=placement();final int id;Object body;int draws,angles;final List<Object> seen=new ArrayList<>();Consumer<Object> after=o->{};
        H(int id){this.id=id;seed(p,33,id);}
        CdObjectState slot(){return p.pool().state(33);}void edit(CdObjectState s){p.pool().write(s);}
        void bind(){body=switch(id){case 0x18->CdExplosion.fromAct2PastAssets(p,terrain,this,c->draw(c.source()),this);case 0x1F->CdFlower.fromAct2PastAssets(p,terrain,probes(),this,c->{check(c);angles++;},c->draw(c.source()),this);case 0x1C->CdPoints.fromAct2PastAssets(p,terrain,this,c->draw(c.source()),this);case 0x1A->CdMonitorItem.fromAct2PastAssets(p,terrain,c->{throw new AssertionError("unreached shoes");},c->{throw new AssertionError("unreached shield");},c->{throw new AssertionError("unreached rings");},(pool,identity)->draw(pool.state(identity.wholeIndex())),this,this);default->throw new AssertionError();};}
        boolean current(Object c){if(body instanceof CdExplosion b)return b.isCurrentCall(c);if(body instanceof CdFlower b)return b.isCurrentCall(c);if(body instanceof CdPoints b)return b.isCurrentCall(c);return ((CdMonitorItem)body).isCurrentCall(c);}
        void check(Object c){assertTrue(current(c));assertFalse(current(cloneRecord(c)));}
        void before(Object c){check(c);seen.add(c);}void done(Object c){check(c);after.accept(c);}void draw(CdObjectState source){draws++;edit(source.moveByte(SPRITE_FLAGS,source.spriteFlags()&127));}
        void retired(){for(var c:seen)assertFalse(current(c));}
        public void beforePublication(CdExplosion.Publication c){before(c);}public void afterPublication(CdExplosion.Publication c){done(c);}public void beforePublication(CdFlower.Publication c){before(c);}public void afterPublication(CdFlower.Publication c){done(c);}public void beforePublication(CdPoints.Publication c){before(c);}public void afterPublication(CdPoints.Publication c){done(c);}
        public void beforeCall(Object c){before(c);}public void afterCall(Object c){done(c);}public void prepared(Object c){check(c);}public void completed(Object c){check(c);}
        public void beforeOperation(Object c){before(c);}public void afterOperation(Object c){done(c);}public void failedOperation(Object c,Throwable t){check(c);}
    }
    static CdPlayerTerrain probes(){return CdPlayerTerrain.fromStageTerrain(terrain.terrain());}
    static CdObjectPlacementRuntime placement(){return CdObjectPlacementRuntime.fromAssets(terrain,bank());}
    static void seed(CdObjectPlacementRuntime p,int at,int id){for(int w=32;w<128;w++)p.pool().write(dirty(p.pool().state(w)).moveByte(ID,0));p.pool().write(p.pool().state(at).moveByte(ID,id).moveByte(ROUTINE,0).moveByte(ROUTINE_2,0).moveByte(SUBTYPE,0).moveByte(SUBTYPE2,0).moveByte(ANIMATION,0).moveByte(PREVIOUS_ANIMATION,0).moveByte(ANIMATION_FRAME,0).moveByte(ANIMATION_TIME,0).moveByte(LAYER,0).moveByte(FLAGS,0).moveByte(SPRITE_FLAGS,0x80).moveWord(X,0x1000).moveWord(Y,0x200));}
    static CdAssetBytes bank(){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];for(int i=0;i<b.length;i++)b[i]=(byte)(i*37+0xA6);return CdAssetBytes.copyOf(b);}
    static void nonwrites(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,Set<Integer> changed){assertEquals(before.status(),p.status());assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());for(var s:before.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,p.pool().state(s.identity().wholeIndex()));}
    static Object cloneRecord(Object r){try{var cs=r.getClass().getRecordComponents();var types=Arrays.stream(cs).map(c->c.getType()).toArray(Class<?>[]::new);var values=new Object[cs.length];for(int i=0;i<cs.length;i++)values[i]=cs[i].getAccessor().invoke(r);return r.getClass().getConstructor(types).newInstance(values);}catch(Exception e){throw new AssertionError(e);}}
    static CdObjectState dirty(CdObjectState s){try{byte[] b=new byte[64];for(int i=0;i<64;i++)b[i]=(byte)(s.identity().wholeIndex()*13+i*7);var f=CdObjectState.class.getDeclaredField("owner");f.setAccessible(true);var c=CdObjectState.class.getDeclaredConstructor(Object.class,CdObjectState.Identity.class,byte[].class);c.setAccessible(true);return c.newInstance(f.get(s),s.identity(),b);}catch(Exception e){throw new AssertionError(e);}}
    static final class Fault extends RuntimeException {private static final long serialVersionUID=1L;}
}
