package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Actual USA R12B metadata/terrain/math; every pool image/global/callback below is
 * explicitly constructed component ingress. No World, Frame, native invocation,
 * actual DrawObjects/SAT, current patterns, pixels or route proof. Java UNRUN.
 * N1ecc5f3: Anton/Main24-63; Tamabboh/Main29-72,226-252;
 * Swing/Main79-197; Object Tile ID Set7-17; Object Tile IDs (Act2Past)7-62.
 */
class TestCdR12bAntonTamabbohSwingBindings {
    private static final int SLOT=40, ANTON_MAP=0x20B160, TAM_MAP=0x20B71E, TAM_DAMAGED=0x20B734,
            TAM_MISSILE=0x20B826, NORMAL_ANI=0x20B6F8, DAMAGED_ANI=0x20B704, SWING_MAP=0x20ECEC;
    private static CdR12bTerrainAssets retail,independent;
    private static CdR12bPlayerAssets player;

    @BeforeAll static void actualOriginalSources()throws Exception{
        String root=System.getProperty("soniccd.media.root");assertNotNull(root);assertFalse(root.isBlank());Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))){
            var cues=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var media=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())){
            retail=CdR12bTerrainAssets.fromPrograms(media);independent=CdR12bTerrainAssets.fromPrograms(media);player=CdR12bPlayerAssets.fromPrograms(media);
        }
        assertEquals(retail.identity(),independent.identity());assertNotSame(retail.placements(),independent.placements());
    }

    @Test void namedFactoriesAddOnlyFourSixEightWhileLegacyRecordsAndConstructorsStayStrict()throws Exception{
        assertEquals(4,method(CdAnton.class,"fromAct2PastAssets").getParameterCount());
        assertEquals(6,method(CdTamabboh.class,"fromAct2PastAssets").getParameterCount());
        assertEquals(8,method(CdSwing.class,"fromAct2PastAssets").getParameterCount());
        assertEquals(1,CdAnton.class.getConstructors().length);assertEquals(5,CdAnton.class.getConstructors()[0].getParameterCount());
        assertEquals(1,CdTamabboh.class.getConstructors().length);assertEquals(7,CdTamabboh.class.getConstructors()[0].getParameterCount());
        assertEquals(1,CdSwing.class.getConstructors().length);assertEquals(6,CdSwing.class.getConstructors()[0].getParameterCount());
        assertEquals(3,CdAnton.Assets.class.getRecordComponents().length);assertEquals(9,CdTamabboh.Assets.class.getRecordComponents().length);
        assertEquals(4,CdSwing.Assets.class.getRecordComponents().length);
        var o=retail.objects();var tiles=o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS);assertEquals(72,tiles.length());
        assertThrows(IllegalArgumentException.class,()->new CdAnton.Assets(tiles,o.anton(),ANTON_MAP));
        assertThrows(IllegalArgumentException.class,()->new CdTamabboh.Assets(tiles,o.tamabbohNormal(),o.tamabbohDamaged(),o.tamabbohMissile(),
                TAM_MAP,TAM_DAMAGED,TAM_MISSILE,NORMAL_ANI,DAMAGED_ANI));
        assertThrows(IllegalArgumentException.class,()->new CdSwing.Assets(tiles,o.view(CdR12bObjectAssets.View.SWING_MOTION),o.swing(),SWING_MAP));
        var h=new H();assertThrows(IllegalStateException.class,h.swing::assets);
        // Explicit historical format value; grants no R12A/R12B source authority.
        var legacy=new CdSwing.Assets(tiles,o.view(CdR12bObjectAssets.View.SWING_MOTION),o.swing(),CdSwing.MAPPING_ADDRESS);
        assertSame(legacy,new CdSwing(h.placement,legacy,new CdPlayerMath(player.player()),h.solid,h,h::swingDraw).assets());
    }

    @Test void actualFactoriesBorrowCachedViewsAndOriginalMathWithoutAnyNativeStoreOrCallback()throws Exception{
        var p=placement(retail);var cut=p.capture();var o=retail.objects();byte[] tiles=o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS).copy();
        var h=new H(p);unchanged(cut,p,Set.of());assertEquals(0,h.calls.size());assertEquals(0,h.draws.size());
        assertSame(o.anton(),field(field(h.anton,"assets"),"visual"));
        assertSame(o.tamabbohNormal(),field(field(h.tam,"assets"),"normal"));
        assertSame(o.tamabbohDamaged(),field(field(h.tam,"assets"),"damaged"));
        assertSame(o.tamabbohMissile(),field(field(h.tam,"assets"),"missile"));
        assertSame(o.swing(),field(field(h.swing,"assets"),"visual"));
        assertSame(o.view(CdR12bObjectAssets.View.SWING_MOTION),field(field(h.swing,"assets"),"motion"));
        assertSame(player.player(),field(field(h.swing,"math"),"assets"));
        assertArrayEquals(tiles,o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS).copy());assertEquals(72,tiles.length);
    }

    @Test void equalIdentityForeignAndConstructedPlacementRejectBeforeSourceOrPortMutation()throws Exception{
        for(var p:List.of(placement(independent),new CdObjectPlacementRuntime(retail.placements()))){
            var h=new H();var cut=p.capture();
            assertThrows(IllegalArgumentException.class,()->CdAnton.fromAct2PastAssets(p,retail,h::antonAngle,h::draw));
            assertThrows(IllegalArgumentException.class,()->CdTamabboh.fromAct2PastAssets(p,retail,h::tamAngle,h::tamPlayer,h::fm,h::draw));
            assertThrows(IllegalArgumentException.class,()->CdSwing.fromAct2PastAssets(p,retail,player,h.solid,h,h::swingDraw,h,h));
            unchanged(cut,p,Set.of());assertTrue(h.calls.isEmpty());assertTrue(h.draws.isEmpty());
        }
        var h=new H();var cut=h.placement.capture();
        assertThrows(NullPointerException.class,()->CdSwing.fromAct2PastAssets(h.placement,retail,null,h.solid,h,h::swingDraw,h,h));
        unchanged(cut,h.placement,Set.of());
    }

    @Test void antonInitUsesB2399AndItsRealFollowingWordAliasBeforeTheFirstFloorRead()throws Exception{
        for(int subtype:new int[]{0,255})for(int secondary:new int[]{0,1}){
            var h=new H();h.put(seed(h.state(),0x3F).moveByte(SUBTYPE,subtype).moveByte(SUBTYPE2,secondary));
            var before=h.state();var cut=h.placement.capture();h.failAngle=true;byte[] expected=antonHeader(before.copyBytes());
            w(expected,2,secondary==0?0x2399:0x241E);l(expected,0x2A,subtype==0?-0x10000:-0x8000);expected[0x1C]=(byte)(subtype==0?0:1);
            l(expected,12,before.longBits(Y_POSITION)+0x10000);
            assertSame(h.fault,assertThrows(Fault.class,h::antonRun));assertArrayEquals(expected,h.state().copyBytes());
            assertEquals(1,h.calls.size());assertTrue(h.draws.isEmpty());unchanged(cut,h.placement,Set.of(SLOT));
        }
    }

    @Test void antonInvalidSecondaryStopsAfterHeaderButGoodFutureStillConvertsBeforeSelector()throws Exception{
        var h=new H();h.put(seed(h.state(),0x3F).moveByte(SUBTYPE2,2));var before=h.state();var cut=h.placement.capture();
        var stop=assertThrows(CdAnton.UnsupportedAnton.class,h::antonRun);assertEquals(CdAnton.UnsupportedBoundary.TILE_ENTRY,stop.boundary());
        assertArrayEquals(antonHeader(before.copyBytes()),h.state().copyBytes());assertTrue(h.calls.isEmpty());assertTrue(h.draws.isEmpty());unchanged(cut,h.placement,Set.of(SLOT));
        var g=new H();g.put(seed(g.state(),0x3F).moveByte(ROUTINE,255).moveByte(SUBTYPE2,255));before=g.state();
        var result=g.anton.run(SLOT,new CdAnton.Globals(1,0,0,0x150,0x55));byte[] converted=new byte[64];converted[0]=0x18;w(converted,8,before.centreX());w(converted,12,before.centreY());
        assertArrayEquals(converted,result.state().copyBytes());assertTrue(g.calls.isEmpty());assertTrue(g.draws.isEmpty());
    }

    @Test void tamabbohNormalAndDamagedInitUseActualPointersAnd23afBeforeFirstFloor()throws Exception{
        for(int subtype:new int[]{0,255}){
            var h=new H();h.put(seed(h.state(),0x22).moveByte(SUBTYPE,subtype));var before=h.state();var cut=h.placement.capture();h.failAngle=true;
            byte[] expected=tamHeader(before.copyBytes());w(expected,2,0x23AF);l(expected,4,subtype==0?TAM_MAP:TAM_DAMAGED);
            l(expected,0x30,subtype==0?NORMAL_ANI:DAMAGED_ANI);l(expected,0x2C,subtype==0?-0xA000:-0x5000);
            l(expected,12,before.longBits(Y_POSITION)+0x10000);
            assertSame(h.fault,assertThrows(Fault.class,h::tamRun));assertArrayEquals(expected,h.state().copyBytes());
            assertEquals(1,h.calls.size());assertTrue(h.draws.isEmpty());unchanged(cut,h.placement,Set.of(SLOT));
        }
    }

    @Test void tamabbohReachedSelectorFenceRetainsHeaderWithoutPaddingOrAnEarlyMapRead()throws Exception{
        var h=new H();h.put(seed(h.state(),0x22).moveByte(SUBTYPE2,1));var before=h.state();var cut=h.placement.capture();
        var stop=assertThrows(CdTamabboh.UnsupportedTamabboh.class,h::tamRun);assertEquals(CdTamabboh.UnsupportedBoundary.TILE_ENTRY,stop.boundary());
        assertArrayEquals(tamHeader(before.copyBytes()),h.state().copyBytes());assertEquals(before.longBits(MAPPINGS),h.state().longBits(MAPPINGS));
        assertTrue(h.calls.isEmpty());assertTrue(h.draws.isEmpty());unchanged(cut,h.placement,Set.of(SLOT));
    }

    @Test void tamabbohMissileSubtypeOneBypassesGoodFutureAndTileSelectorUsingTheActualMissileRoot()throws Exception{
        var h=new H();h.put(seed(h.state(),0x22).moveByte(SUBTYPE,1).moveByte(SUBTYPE2,255).moveByte(TAMABBOH_MISSILE_DIRECTION,255));
        var before=h.state();var cut=h.placement.capture();byte[] expected=before.copyBytes();expected[0x24]=2;expected[1]|=4;expected[0x20]=(byte)0xAD;
        expected[0x16]=expected[0x17]=expected[0x19]=8;l(expected,4,TAM_MISSILE);l(expected,0x32,0);l(expected,0x36,0x2000);
        l(expected,0x2A,-0x20000);l(expected,0x2E,-0x40000);
        var result=h.tam.run(SLOT,new CdTamabboh.Globals(1,0x80,0,0,0x150,0x55));
        assertArrayEquals(expected,result.state().copyBytes());assertEquals(before.word(TILE),result.state().word(TILE));
        assertEquals(1,h.draws.size());assertTrue(h.calls.isEmpty());unchanged(cut,h.placement,Set.of(SLOT));
    }

    @Test void positionBodiesUseActualR12bTerrainAndOnlyItsReturnedSignChangesRoutine()throws Exception{
        var a=new H();a.put(seed(a.state(),0x3F).moveByte(ROUTINE,2).moveWord(ANTON_ANCHOR_X,0x150));var before=a.state();var cut=a.placement.capture();
        var ar=a.antonRun();var af=ar.effects().stream().filter(CdAnton.FloorReturned.class::isInstance).map(CdAnton.FloorReturned.class::cast).toList();
        assertEquals(1,af.size());byte[] expected=before.copyBytes();l(expected,12,before.longBits(Y_POSITION)+0x10000);expected[0x24]=(byte)(af.getFirst().result().distance()<0?4:2);
        assertArrayEquals(expected,ar.state().copyBytes());assertFalse(a.calls.isEmpty());assertEquals(1,a.draws.size());unchanged(cut,a.placement,Set.of(SLOT));
        var t=new H();t.put(seed(t.state(),0x22).moveByte(ROUTINE,2).moveWord(TAMABBOH_ANCHOR_X,0x150));before=t.state();cut=t.placement.capture();
        var tr=t.tamRun();var tf=tr.effects().stream().filter(CdTamabboh.FloorReturned.class::isInstance).map(CdTamabboh.FloorReturned.class::cast).toList();
        assertEquals(1,tf.size());expected=before.copyBytes();l(expected,12,before.longBits(Y_POSITION)+0x10000);expected[0x24]=(byte)(tf.getFirst().result().distance()<0?4:2);
        assertArrayEquals(expected,tr.state().copyBytes());assertEquals(1,t.draws.size());unchanged(cut,t.placement,Set.of(SLOT));
    }

    @Test void mainPrefixesRetainTheRealXStoreBeforeAStoppedAnglePortWithoutNewOwners()throws Exception{
        var a=new H();a.put(seed(a.state(),0x3F).moveByte(ROUTINE,4).moveWord(ANTON_ANCHOR_X,0x150).moveLong(ANTON_X_DELTA,-0x10000));
        var before=a.state();var cut=a.placement.capture();a.failAngle=true;byte[] expected=before.copyBytes();l(expected,8,before.longBits(X_POSITION)-0x10000);
        assertSame(a.fault,assertThrows(Fault.class,a::antonRun));assertArrayEquals(expected,a.state().copyBytes());assertTrue(a.draws.isEmpty());unchanged(cut,a.placement,Set.of(SLOT));
        var t=new H();t.put(seed(t.state(),0x22).moveByte(ROUTINE,4).moveWord(TAMABBOH_ANCHOR_X,0x150).moveLong(TAMABBOH_X_DELTA,-0xA000));
        before=t.state();cut=t.placement.capture();t.failAngle=true;expected=before.copyBytes();l(expected,8,before.longBits(X_POSITION)-0xA000);
        assertSame(t.fault,assertThrows(Fault.class,()->t.tam.run(SLOT,new CdTamabboh.Globals(0,0,0,1,0x150,0x55))));
        assertArrayEquals(expected,t.state().copyBytes());assertTrue(t.draws.isEmpty());unchanged(cut,t.placement,Set.of(SLOT));
    }

    @Test void swingParentInitUses20ecec046cAndOnlyLiteralChildFieldsOverRealMath()throws Exception{
        var h=new H();h.put(seed(h.state(),0x29).moveByte(SUBTYPE,2).moveByte(SWING_PART,0).moveWord(SWING_ANGLE,0).moveByte(SWING_REVERSING,0));
        var before=h.state();var cut=h.placement.capture();var result=h.swingRun();byte[] expected=before.copyBytes();
        expected[0x24]=2;expected[1]|=4;expected[0x18]=3;l(expected,4,SWING_MAP);w(expected,2,0x46C);expected[0x19]=0x18;expected[0x16]=8;
        w(expected,0x36,before.centreX());w(expected,0x32,before.centreY());expected[0x2F]=2;w(expected,0x3A,0xC008);w(expected,0x3E,8);w(expected,0x34,0x200);w(expected,0x38,0xFE00);w(expected,0x30,8);expected[0x3D]=0;
        assertArrayEquals(expected,result.state().copyBytes());assertTrue(h.calls.isEmpty());assertEquals(1,h.draws.size());
        for(int i=0;i<2;i++){byte[] child=cut.pool().slots().get(i).copyBytes();child[0]=0x29;w(child,0x36,before.centreX());w(child,0x32,before.centreY());child[0x28]=2;child[0x2F]=2;child[0x3C]=(byte)(2-i);child[0x1A]=(byte)(i==0?1:2);assertArrayEquals(child,h.placement.pool().state(32+i).copyBytes());}
        var motion=result.effects().stream().filter(CdSwing.MotionCalculated.class::isInstance).map(CdSwing.MotionCalculated.class::cast).findFirst().orElseThrow();
        var trig=new CdPlayerMath(player.player()).calcSine(0xC0);assertEquals(trig.sine()&65535,motion.sineWord());assertEquals(trig.cosine()&65535,motion.cosineWord());assertEquals(0,motion.distanceWord());
        unchanged(cut,h.placement,Set.of(SLOT,32,33));
    }

    @Test void swingReachedSecondaryAndMotionBoundsKeepTheExactEarlierNativePrefix()throws Exception{
        var h=new H();h.put(seed(h.state(),0x29).moveByte(SUBTYPE2,1));var before=h.state();var cut=h.placement.capture();byte[] expected=before.copyBytes();
        expected[0x24]=2;expected[1]|=4;expected[0x18]=3;l(expected,4,SWING_MAP);
        var stop=assertThrows(CdSwing.UnsupportedSwing.class,h::swingRun);assertEquals(CdSwing.UnsupportedBoundary.TILE_ID_SUBTYPE2,stop.boundary());
        assertArrayEquals(expected,h.state().copyBytes());assertTrue(h.calls.isEmpty());assertTrue(h.draws.isEmpty());unchanged(cut,h.placement,Set.of(SLOT));
        var m=new H();m.put(seed(m.state(),0x29).moveByte(SUBTYPE,0x81).moveByte(SWING_PART,1).moveByte(SWING_COUNT,2));
        var failure=assertThrows(CdSwing.UnsupportedSwing.class,m::swingRun);assertEquals(CdSwing.UnsupportedBoundary.MOTION_WORD_READ,failure.boundary());assertEquals(64,failure.value());
        assertEquals(SWING_MAP,m.state().longBits(MAPPINGS));assertEquals(0x46C,m.state().word(TILE));assertEquals(0,m.placement.pool().state(32).id());assertTrue(m.draws.isEmpty());
    }

    @Test void swingEndLinkStopsAtTheActualPlayerReadAfterMotionWithoutFabricatingSolidOutcomes()throws Exception{
        var h=new H();h.put(seed(h.state(),0x29).moveByte(ROUTINE,2).moveByte(SWING_PART,1).moveByte(SWING_COUNT,1)
                .moveWord(SWING_PHASE,0x1234).moveWord(SWING_STEP,0).moveWord(SWING_ANGLE,0).moveWord(SWING_CENTRE_X,0x150).moveWord(SWING_CENTRE_Y,0x250));
        var cut=h.placement.capture();assertSame(h.fault,assertThrows(Fault.class,h::swingRun));
        assertEquals(1,h.calls.size());assertInstanceOf(CdSwing.PlayerReadCall.class,h.calls.getFirst());assertTrue(h.draws.isEmpty());
        assertEquals(8,h.state().unsignedByte(Y_RADIUS));unchanged(cut,h.placement,Set.of(SLOT));
    }

    private static final class Fault extends RuntimeException{}
    private static final class H implements CdSwing.PlayerPort,CdSwing.DispatchObserver,CdSwing.ReceiptGuard{
        final CdObjectPlacementRuntime placement;final CdSolidObject solid;final CdAnton anton;final CdTamabboh tam;final CdSwing swing;
        final Fault fault=new Fault();final List<Object> calls=new ArrayList<>(),draws=new ArrayList<>();
        Object pending;boolean failAngle;int angle=0x55;
        H(){this(placement(retail));}
        H(CdObjectPlacementRuntime p){
            placement=p;solid=new CdSolidObject(CdPlayerMovement.fromStageSource(com.openggf.game.soniccd.level.CdStagePlayerSource.fromAct2Past(retail,player)));
            anton=CdAnton.fromAct2PastAssets(p,retail,this::antonAngle,this::draw);
            tam=CdTamabboh.fromAct2PastAssets(p,retail,this::tamAngle,this::tamPlayer,this::fm,this::draw);
            swing=CdSwing.fromAct2PastAssets(p,retail,player,solid,this,this::swingDraw,this,this);
        }
        CdObjectState state(){return placement.pool().state(SLOT);}void put(CdObjectState state){placement.pool().write(state);}
        CdAnton.Result antonRun(){return anton.run(SLOT,new CdAnton.Globals(0,0,0,0x150,angle));}
        CdTamabboh.Result tamRun(){return tam.run(SLOT,new CdTamabboh.Globals(0,0,0,0,0x150,angle));}
        CdSwing.Result swingRun(){return swing.run(SLOT,new CdSwing.Globals(0x150,0,0));}
        void antonAngle(CdAnton.PrimaryAngleCall call){assertTrue(anton.isCurrentPrimaryAngleCall(call));assertEquals(angle,call.expectedPrimaryAngle());calls.add(call);if(failAngle)throw fault;angle=call.value();}
        void tamAngle(CdTamabboh.PrimaryAngleCall call){assertTrue(tam.isCurrentPrimaryAngleCall(call));assertEquals(angle,call.expectedPrimaryAngle());calls.add(call);if(failAngle)throw fault;angle=call.value();}
        int tamPlayer(CdTamabboh.PlayerReadCall call){calls.add(call);throw fault;}
        void fm(CdTamabboh.FmCall call){calls.add(call);throw fault;}
        void draw(CdObjectPool pool,CdObjectState.Identity identity){assertSame(placement.pool(),pool);draws.add(pool.state(identity.wholeIndex()));}
        void swingDraw(CdSwing.DrawCall call){assertTrue(swing.isCurrentCall(call));draws.add(call);var s=placement.pool().state(call.expectedSource().identity().wholeIndex());placement.pool().write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&127));}
        public CdSwing.PlayerCut read(CdSwing.PlayerReadCall call){assertTrue(swing.isCurrentCall(call));calls.add(call);throw fault;}
        public void commitSolid(CdSwing.SolidCall call){fail("No constructed successful player/solid outcome is supplied");}
        public void commitY(CdSwing.PlayerYCall call){fail("No constructed successful player Y outcome is supplied");}
        public void beforePublication(CdSwing.Publication call){}public void afterPublication(CdSwing.Publication call){}
        public void beforeCall(Object call){}public void afterCall(Object call){}
        public void prepared(Object call){assertNull(pending);assertTrue(swing.isCurrentCall(call));assertTrue(swing.currentCallOrdinal(call)>0);pending=call;}
        public void completed(Object call){assertSame(pending,call);pending=null;}
    }
    private static CdObjectPlacementRuntime placement(CdR12bTerrainAssets a){byte[] b=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];Arrays.fill(b,(byte)0x56);return CdObjectPlacementRuntime.fromAssets(a,CdAssetBytes.copyOf(b));}
    private static CdObjectState seed(CdObjectState s,int id){return s.moveByte(ID,id).moveByte(ROUTINE,0).moveByte(SUBTYPE,0).moveByte(SUBTYPE2,0)
            .moveByte(SPRITE_FLAGS,1).moveByte(SAVED_FLAGS_ID,0).moveLong(X_POSITION,0x01501234).moveLong(Y_POSITION,0x02505678)
            .moveWord(TILE,0xBEEF).moveLong(MAPPINGS,0x12345678).moveByte(ANIMATION,0x37).moveByte(ROUTINE_2,0x45);}
    private static byte[] antonHeader(byte[] raw){byte[] b=raw.clone();b[1]|=4;b[0x18]=4;l(b,4,ANTON_MAP);b[0x17]=b[0x19]=0x18;b[0x16]=0x13;b[0x20]=0x29;w(b,0x2E,ByteBuffer.wrap(raw).getShort(8)&65535);return b;}
    private static byte[] tamHeader(byte[] raw){byte[] b=raw.clone();b[0x24]+=2;b[1]|=4;b[0x18]=4;b[0x20]=0x2C;b[0x17]=b[0x19]=16;b[0x16]=15;w(b,0x2A,ByteBuffer.wrap(raw).getShort(8)&65535);return b;}
    private static void unchanged(CdObjectPlacementRuntime.Snapshot before,CdObjectPlacementRuntime p,Set<Integer> changed){assertEquals(before.status(),p.status());assertArrayEquals(before.savedFlags().copy(),p.capture().savedFlags().copy());for(var s:before.pool().slots())if(!changed.contains(s.identity().wholeIndex()))assertEquals(s,p.pool().state(s.identity().wholeIndex()));}
    private static java.lang.reflect.Method method(Class<?> type,String name){return Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().equals(name)).findFirst().orElseThrow();}
    private static Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static void w(byte[] b,int at,int value){ByteBuffer.wrap(b).putShort(at,(short)value);}
    private static void l(byte[] b,int at,int value){ByteBuffer.wrap(b).putInt(at,value);}
}
