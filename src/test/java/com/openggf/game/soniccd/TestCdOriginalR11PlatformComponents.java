package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.objects.*;
import com.openggf.game.soniccd.player.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests FIRST. Genuine original assets with explicit constructed component/pool controls;
 * these controls grant no World/Frame invocation, route or current-art authority. Java UNRUN. */
class TestCdOriginalR11PlatformComponents {
    @Test void originalFactoriesRejectConstructedPlacementBeforeBorrowingCallbacks()throws Exception{
        var a=TestCdOriginalBootPrefix.source().assets();var p=new CdObjectPlacementRuntime(a.placements());var solid=CdSolidObject.fromAssets(a);
        assertThrows(IllegalArgumentException.class,()->CdPlatform.fromOriginalPresentAssets(p,a,solid,c->fail(),c->fail(),(pool,id)->fail()));
        assertThrows(IllegalArgumentException.class,()->CdCollapsePlatform.fromOriginalPresentAssets(p,a,solid,c->fail(),(pool,id)->fail(),c->fail(),c->fail()));
    }
    @Test void actualSourcePlatformInitKeepsDirtyChildPrefixesAndReturnsThroughOneLiveDraw()throws Exception{
        var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);var pool=p.pool();
        var parent=pool.state(40).moveByte(ID,0x21).moveByte(SUBTYPE,1).moveByte(SUBTYPE2,0xFA).moveWord(X,0x600).moveWord(Y,0x400).moveWord(X_FRACTION,0x1111).moveWord(Y_FRACTION,0x2222);
        pool.write(parent);pool.write(pool.state(32).moveWord(X_FRACTION,0xABCD).moveWord(Y_FRACTION,0x9876).moveByte(ROUTINE,0x66).moveByte(SPRITE_FLAGS,0xA1));
        var before=pool.capture();var draws=new ArrayList<CdObjectState>();var body=new CdPlatform[1];
        body[0]=CdPlatform.fromOriginalPresentAssets(p,a,CdSolidObject.fromAssets(a),c->fail("Init must not invoke Main Solid"),c->fail(),(actual,id)->{
            assertSame(pool,actual);assertTrue(body[0].isCurrentDrawCall(actual,id));
            assertFalse(body[0].isCurrentDrawCall(actual,new CdObjectPool().state(id.wholeIndex()).identity()));
            assertThrows(IllegalStateException.class,()->body[0].run(40,null,null));draws.add(actual.state(id.wholeIndex()));
        });
        var result=body[0].run(40,new CdPlatform.Globals(0,0,0x600,0x400,1),null);
        assertEquals(1,draws.size());assertEquals(2,result.state().routine());assertEquals(0x20CCC8,result.state().longBits(MAPPINGS));assertEquals(0x44BE,result.state().word(TILE));
        assertEquals(0x1111,result.state().word(X_FRACTION));assertEquals(0x2222,result.state().word(Y_FRACTION));
        var child=pool.state(32);assertEquals(0x0A,child.id());assertEquals(0x6F8,child.centreX());assertEquals(0x3F0,child.centreY());
        assertEquals(0xF0,child.unsignedByte(SPRING_PARENT_Y_OFFSET));assertEquals(0xF8,child.unsignedByte(SPRING_PARENT_X_OFFSET));assertEquals(2,child.subtype());
        assertEquals(parent.identity().nativeAddress24()&65535,child.word(SPRING_PARENT));assertEquals(0xABCD,child.word(X_FRACTION));assertEquals(0x9876,child.word(Y_FRACTION));assertEquals(0x66,child.routine());assertEquals(0xA1,child.spriteFlags());
        for(var old:before.slots())if(old.identity().wholeIndex()!=32&&old.identity().wholeIndex()!=40)assertArrayEquals(old.copyBytes(),pool.state(old.identity().wholeIndex()).copyBytes());
        assertFalse(body[0].isCurrentDrawCall(pool,result.state().identity()));
    }
    @Test void actualSourcePlatformFullPoolDeletesOnlyParentAndStillCallsGuardedDraw()throws Exception{
        var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);var pool=p.pool();
        for(int w=32;w<128;w++)pool.write(pool.state(w).moveByte(ID,w==40?0x21:0x7F).moveWord(X_FRACTION,w).moveWord(Y_FRACTION,255-w));
        pool.write(pool.state(40).moveByte(SUBTYPE2,2).moveByte(SAVED_FLAGS_ID,0).moveWord(X,0x600).moveWord(Y,0x400));var before=pool.capture();var calls=new int[1];var body=new CdPlatform[1];
        body[0]=CdPlatform.fromOriginalPresentAssets(p,a,CdSolidObject.fromAssets(a),c->fail(),c->fail(),(actual,id)->{assertTrue(body[0].isCurrentDrawCall(actual,id));assertEquals(0,actual.state(40).id());calls[0]++;});
        body[0].run(40,new CdPlatform.Globals(0,0,0x600,0x400,1),null);assertEquals(1,calls[0]);assertArrayEquals(new byte[64],pool.state(40).copyBytes());
        for(var old:before.slots())if(old.identity().wholeIndex()!=40)assertArrayEquals(old.copyBytes(),pool.state(old.identity().wholeIndex()).copyBytes());
    }
    @Test void originalPlatformDrawFailureRetainsInitAndIdenticalThrowableWithoutReplay()throws Exception{
        var a=TestCdOriginalBootPrefix.source().assets();var p=CdObjectPlacementRuntime.fromAssets(a);p.pool().write(p.pool().state(40).moveByte(ID,0x21).moveWord(X,0x600).moveWord(Y,0x400));
        var failure=new IllegalStateException("actual Draw refusal");var body=new CdPlatform[1];
        body[0]=CdPlatform.fromOriginalPresentAssets(p,a,CdSolidObject.fromAssets(a),c->fail(),c->fail(),(pool,id)->{assertTrue(body[0].isCurrentDrawCall(pool,id));throw failure;});
        assertSame(failure,assertThrows(IllegalStateException.class,()->body[0].run(40,new CdPlatform.Globals(0,0,0x600,0x400,1),null)));
        assertEquals(2,p.pool().state(40).routine());assertEquals(0x20CCC8,p.pool().state(40).longBits(MAPPINGS));assertFalse(body[0].isCurrentDrawCall(p.pool(),p.pool().state(40).identity()));
    }
    @Test void actualPlatformAndCollapseDataReuseAllExistingMappingAndScriptOwners()throws Exception{
        var a=TestCdOriginalBootPrefix.source().assets();var data=a.openingObjects();var platform=CdPlatform.Assets.fromObjectAssets(data);var collapse=CdCollapsePlatform.Assets.fromObjectAssets(data);
        assertSame(data.platform(),platform.visual());assertSame(data.collapsePlatform1(),collapse.collapsePlatform1());assertSame(data.collapsePlatform2(),collapse.collapsePlatform2());assertSame(data.collapsePlatform3(),collapse.collapsePlatform3());assertSame(data.collapsePlatform4(),collapse.collapsePlatform4());
        assertEquals(0x20CCC8,platform.mappingsAddress());assertEquals(0x20C520,collapse.mappings1());assertEquals(0x20C5EC,collapse.mappings2());assertEquals(0x20C5AE,collapse.mappings3());assertEquals(0x20C7AC,collapse.mappings4());
        assertArrayEquals(new byte[]{16,32,48,0},platform.widths().copy());assertArrayEquals(new byte[]{2,3,4,6},platform.ranges().copy());assertEquals(24,collapse.sizes1().length());assertEquals(40,collapse.sizes2().length());
        assertEquals(List.of(1,6,7,6),List.of(collapse.collapsePlatform1().mappingFrames().size(),collapse.collapsePlatform2().mappingFrames().size(),collapse.collapsePlatform3().mappingFrames().size(),collapse.collapsePlatform4().mappingFrames().size()));
    }
    @Test void actualSourceCollapseBreaksThroughOrderedOwnersAndKeepsDirtyFragmentFractions()throws Exception{
        var h=new Collapse();h.pool().write(h.pool().state(32).moveWord(X_FRACTION,0xABCD).moveWord(Y_FRACTION,0x7654));h.run();
        assertEquals(List.of("solid:MAIN_TOP","solid:MAIN_GET_OFF","fm","draw"),h.order);assertEquals(List.of(0xA3),h.sounds);
        assertEquals(0,h.pool().state(40).id());var fragments=h.pool().capture().slots().stream().filter(s->s.id()==0x20).toList();assertEquals(6,fragments.size());
        for(var child:fragments){assertEquals(4,child.routine());assertEquals(0x20C7AC,child.longBits(MAPPINGS));assertEquals(0x20000,child.longBits(COLLAPSE_FALL_DELTA));assertEquals(0x44BE,child.word(TILE));}
        assertEquals(0xABCD,h.pool().state(32).word(X_FRACTION));assertEquals(0x7654,h.pool().state(32).word(Y_FRACTION));assertFalse(h.body.isCurrentFmRequest(h.lastFm));
    }
    @Test void actualSourceCollapseCapacityFailureKeepsEveryOtherSlotAndNativeDeletionTail()throws Exception{
        var h=new Collapse();for(int w=32;w<128;w++)if(w!=40)h.pool().write(h.pool().state(w).moveByte(ID,0x7F).moveWord(X_FRACTION,w).moveWord(Y_FRACTION,255-w));var before=h.pool().capture();h.run();
        assertEquals(List.of(0xA3),h.sounds);assertEquals("draw",h.order.getLast());assertEquals(0,h.pool().state(40).id());
        for(var old:before.slots())if(old.identity().wholeIndex()!=40)assertArrayEquals(old.copyBytes(),h.pool().state(old.identity().wholeIndex()).copyBytes());
    }
    @Test void actualSourceCollapseFmFailureKeepsReachedTopGetOffPrefixAndIdenticalThrowable()throws Exception{
        var h=new Collapse();var failure=new IllegalStateException("actual FM refusal");h.fmFailure=failure;
        assertSame(failure,assertThrows(IllegalStateException.class,h::run));assertEquals(List.of("solid:MAIN_TOP","solid:MAIN_GET_OFF","fm"),h.order);
        assertEquals(h.lastFm.actualCut().slots(),h.pool().capture().slots());assertEquals(2,h.pool().state(40).routine());assertTrue(h.sounds.isEmpty());assertFalse(h.body.isCurrentFmRequest(h.lastFm));
    }
    @Test void actualOriginalPlacementRowsRetainEveryNativeIdSubtypePeriodAndSecondaryByte()throws Exception{
        // Independent N1ecc Objects(Act1).bin rows, not a route/input/state source.
        var rows=TestCdOriginalBootPrefix.source().assets().placements().records();
        var actual=rows.stream().filter(r->r.objectId()==0x20||r.objectId()==0x21).map(r->r.index()+":"+r.centreX()+":"+r.packedY()+":"+r.encodedId()+":"+r.subtype()+":"+r.periodFlags()+":"+r.subtype2()).toList();
        assertEquals(List.of(
            "34:1384:544:160:16:32:0",
            "125:3200:536:160:17:32:0",
            "132:3248:784:160:192:64:0",
            "137:3320:544:160:0:224:0",
            "167:3824:348:33:64:224:0",
            "169:3824:618:33:64:32:0",
            "177:3920:161:160:224:224:0",
            "183:4016:161:160:193:224:0",
            "187:4187:155:33:21:192:0",
            "198:4384:368:33:77:32:0",
            "199:4397:155:33:93:224:0",
            "206:4581:171:33:93:224:0",
            "212:4824:288:160:0:224:0",
            "244:5266:619:33:17:96:0",
            "245:5280:616:33:81:128:0",
            "255:5403:482:33:5:224:0",
            "293:5808:614:33:109:32:0",
            "317:6480:427:33:64:224:0",
            "319:6480:656:33:64:224:0",
            "321:6482:427:33:64:160:0",
            "353:7130:383:33:81:128:0",
            "387:7743:672:160:194:32:0",
            "398:8064:353:160:197:32:0",
            "449:8792:712:33:53:32:0"),actual);
    }
    /** Declared callback contract over real shared Solid: no Frame/World effect replay. */
    private static final class Collapse{
        final CdObjectPlacementRuntime placement;final CdCollapsePlatform body;final List<String> order=new ArrayList<>();final List<Integer> sounds=new ArrayList<>();
        CdSolidObject.Player player;CdSolidObject.Globals globals=new CdSolidObject.Globals(0,4,0x12,0x34);CdCollapsePlatform.FmRequest lastFm;RuntimeException fmFailure;
        Collapse()throws Exception{
            var a=TestCdOriginalBootPrefix.source().assets();placement=CdObjectPlacementRuntime.fromAssets(a);
            // Explicit constructed on-screen precondition for real TopSolid, never a pixel claim.
            pool().write(pool().state(40).moveByte(ID,0x20).moveByte(SUBTYPE,0xC0).moveByte(SPRITE_FLAGS,0x80).moveWord(X,3248).moveWord(Y,784));
            var motion=new CdPlayerMotionState((3248<<16)|0x8000,((784-18-19)<<16)|0x1234,0,0x100,0,2,0x10,2,4,9,19,0,0,1,0,0,0,0,0,0);
            player=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(motion,0x780,2,0,0),1,0,0);
            body=CdCollapsePlatform.fromOriginalPresentAssets(placement,a,CdSolidObject.fromAssets(a),this::solid,this::draw,this::fm,this::observed);
        }
        CdObjectPool pool(){return placement.pool();}
        void run(){body.run(40,new CdCollapsePlatform.DespawnGlobals(1,0,3248-128),new CdCollapsePlatform.Inputs(player,new CdSolidObject.Gates(0,0),globals));}
        void solid(CdCollapsePlatform.SolidCall call){
            assertTrue(body.isCurrentSolidCall(call));assertEquals(player,call.expectedPlayer());assertEquals(globals,call.expectedGlobals());assertEquals(call.actualCut().slots(),pool().capture().slots());
            CdObjectState own;List<CdObjectState> other;
            if(call.outcome() instanceof CdSolidObject.Completed c){var r=c.completedResult();own=r.object();other=r.otherSlots();player=r.player();globals=r.globals();}
            else{var r=((CdSolidObject.NativeStopped)call.outcome()).prefix();own=r.object();other=r.otherSlots();player=r.player();globals=r.globals();}
            var images=new ArrayList<CdObjectState>();images.add(own);images.addAll(other);pool().restore(CdObjectPool.overlay(call.actualCut(),images));order.add("solid:"+call.site());
        }
        void fm(CdCollapsePlatform.FmRequest call){assertTrue(body.isCurrentFmRequest(call));assertEquals(call.actualCut().slots(),pool().capture().slots());lastFm=call;order.add("fm");if(fmFailure!=null)throw fmFailure;sounds.add(call.soundId());}
        void draw(CdObjectPool pool,CdObjectState.Identity id){assertTrue(body.isCurrentDrawCall(pool,id));order.add("draw");}
        void observed(Object call){assertTrue(body.isCurrentOperation(call));}
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
