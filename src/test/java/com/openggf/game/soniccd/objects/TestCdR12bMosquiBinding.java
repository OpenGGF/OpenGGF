package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Consumer;
import static com.openggf.game.soniccd.objects.TestCdR12bEffectsBindings.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual B72 source binding and source-derived conversion; explicit component callbacks, no World claim. Java UNRUN. */
class TestCdR12bMosquiBinding {
    @BeforeAll static void source()throws Exception{load();}
    @Test void actualRaw72CannotEnterLegacy74DtoAndExactPairIsRequired() {
        var o=terrain.objects();assertThrows(IllegalArgumentException.class,()->new CdMosqui.Assets(o.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS),o.normalMosqui(),o.damagedMosqui(),0x20B49C,0x20B480,0x20B4D8,0x20B48E));
        for(var p:List.of(new CdObjectPlacementRuntime(terrain.placements()),CdObjectPlacementRuntime.fromAssets(equalTerrain,bank())))assertThrows(IllegalArgumentException.class,()->CdMosqui.fromAct2PastAssets(p,terrain,null,null,null,null,null));
    }
    @Test void normalAndDamagedInitStoreActualMappingScriptsAndSelectedTile() {
        for(int subtype:new int[]{0,1}){var h=new H();h.edit(h.slot().moveByte(SUBTYPE,subtype));var before=h.p.capture();h.run();assertEquals(subtype==0?0x20B49C:0x20B4D8,h.slot().longBits(MAPPINGS));assertEquals(subtype==0?0x20B480:0x20B48E,h.slot().longBits(MOSQUI_ANIMATION_SCRIPT));assertEquals(0x23EA,h.slot().word(TILE));assertEquals(1,h.draws);assertEquals(0,h.fm);nonwrites(before,h.p,Set.of(33));h.retired();assertThrows(IllegalStateException.class,h.body::assets);}
    }
    @Test void reachedTileFaultRetainsOnlyActualInitPrefixAndNoDraw() {
        var h=new H();h.edit(h.slot().moveByte(SUBTYPE2,255));var before=h.p.capture();var e=assertThrows(CdMosqui.UnsupportedMosqui.class,h::run);assertEquals(CdMosqui.UnsupportedBoundary.TILE_ADDRESS,e.boundary());assertEquals(2,h.slot().routine());assertEquals(0x2B,h.slot().unsignedByte(COLLISION_TYPE));assertEquals(0,h.draws);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void targetConversionDeletesAll64ThenRestoresOnlyIntegerXAndYAndId18() {
        var h=new H();h.globals=new CdMosqui.Globals(1,0,0,0,0x1000,0x7000,0x7000);var original=h.slot();var before=h.p.capture();var result=h.run();byte[] expected=new byte[64];expected[0]=0x18;expected[8]=(byte)(original.centreX()>>>8);expected[9]=(byte)original.centreX();expected[12]=(byte)(original.centreY()>>>8);expected[13]=(byte)original.centreY();assertArrayEquals(expected,result.state().copyBytes());assertEquals(4,h.publications);assertEquals(0,h.draws);assertEquals(0,h.fm);assertTrue(result.effects().stream().noneMatch(CdMosqui.SoundRequest.class::isInstance));nonwrites(before,h.p,Set.of(33));h.retired();assertThrows(IllegalArgumentException.class,h::run);
    }
    @Test void explosionExecutesOnlyOnASeparateExplicitPhysicalVisit() {
        var h=new H();h.globals=new CdMosqui.Globals(1,0,0,0,0,0,0);h.run();assertEquals(0x18,h.slot().id());assertEquals(0,h.draws);int[] draws={0};
        // A separate existing component invocation, not a World walk or same-call Mosqui redispatch.
        var explosion=new CdExplosion(h.p,CdExplosion.Assets.fromAct2PastAssets(terrain.objects()),(pool,id)->{draws[0]++;var s=pool.state(id.wholeIndex());pool.write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&127));});
        explosion.run(33);assertEquals(1,draws[0]);assertEquals(2,h.slot().routine());assertEquals(0x21D714,h.slot().longBits(MAPPINGS));assertEquals(0,h.draws);
    }
    @Test void conversionPredicateUsesRawTimeZoneByteAndZeroSubtypeExceptionExactly() {
        var h=new H();h.globals=new CdMosqui.Globals(1,1,0,0x100,0x1000,0x7000,0x7000);h.run();assertEquals(0x3D,h.slot().id());assertEquals(1,h.draws);
        var marked=new H();marked.globals=new CdMosqui.Globals(1,0x81,0,0,0x1000,0,0);marked.run();assertEquals(0x18,marked.slot().id());assertEquals(0,marked.draws);
    }
    @Test void legacyConstructorStillStopsBeforeGoodFutureConversionWrites() {
        var p=placement();seed(p,33,0x3D);var o=terrain.objects();var bytes=new byte[74];var a=new CdMosqui.Assets(CdAssetBytes.copyOf(bytes),o.normalMosqui(),o.damagedMosqui(),0x20B49C,0x20B480,0x20B4D8,0x20B48E);var b=new CdMosqui(p,a,probes(),(pool,id)->fail("unreached"));var before=p.capture();var e=assertThrows(CdMosqui.UnsupportedMosqui.class,()->b.run(33,new CdMosqui.Globals(1,0,0,0,0,0,0)));assertEquals(CdMosqui.UnsupportedBoundary.GOOD_FUTURE_CONVERSION,e.boundary());nonwrites(before,p,Set.of());
    }
    @Test void throwingConversionObserverRetainsCompletedDeleteAndClearsOrigin() {
        var h=new H();h.globals=new CdMosqui.Globals(1,0,0,0,0,0,0);var fault=new Fault();h.after=c->{throw fault;};var before=h.p.capture();assertSame(fault,assertThrows(Fault.class,h::run));assertArrayEquals(new byte[64],h.slot().copyBytes());assertEquals(0,h.draws);assertEquals(0,h.fm);nonwrites(before,h.p,Set.of(33));h.retired();
    }
    @Test void corruptObserverAndNestedDispatchCannotManufactureCleanConversionReturn() {
        var h=new H();h.globals=new CdMosqui.Globals(1,0,0,0,0,0,0);h.before=c->{assertThrows(IllegalStateException.class,h::run);h.p.pool().write(h.p.pool().state(70).moveByte(ID,0x55));};assertThrows(IllegalStateException.class,h::run);assertEquals(0x3D,h.slot().id());assertEquals(0x55,h.p.pool().state(70).id());h.retired();
    }
    static final class H implements CdMosqui.DispatchObserver {
        final CdObjectPlacementRuntime p=placement();CdMosqui body;CdMosqui.Globals globals=new CdMosqui.Globals(0,0,0,0x100,0x1000,0x7000,0x7000);int draws,fm,publications;final List<Object> seen=new ArrayList<>();Consumer<CdMosqui.Publication> before=c->{},after=c->{};
        H(){seed(p,33,0x3D);body=CdMosqui.fromAct2PastAssets(p,terrain,probes(),c->{check(c);},c->{check(c);fm++;},this,c->{check(c);draws++;var s=p.pool().state(33);p.pool().write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()&127));});}
        CdObjectState slot(){return p.pool().state(33);}void edit(CdObjectState s){p.pool().write(s);}CdMosqui.Result run(){return body.run(33,globals);}void check(Object c){assertTrue(body.isCurrentCall(c));assertFalse(body.isCurrentCall(cloneRecord(c)));seen.add(c);}void retired(){for(var c:seen)assertFalse(body.isCurrentCall(c));}
        public void beforePublication(CdMosqui.Publication c){check(c);before.accept(c);}public void afterPublication(CdMosqui.Publication c){check(c);publications++;after.accept(c);}public void beforeCall(Object c){check(c);}public void afterCall(Object c){check(c);}
    }
}
