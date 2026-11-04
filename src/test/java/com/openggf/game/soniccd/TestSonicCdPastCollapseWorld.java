package com.openggf.game.soniccd;

import com.openggf.control.LogicalInputSnapshot;
import com.openggf.control.PlayerInputState;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.objects.CdCollapsePlatform;
import com.openggf.game.soniccd.objects.CdObjectDrawQueue;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openggf.game.soniccd.TestSonicCdPastLoadingWorld.*;
import static com.openggf.game.soniccd.TestSonicCdPastRingWorld.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine cold-program/controller World ingress to the already-qualified7851 cut, followed by
 * explicit object-only component scenes. No actor/camera/spawner/terrain/art edits or helper replay.
 * Native N1ecc5f3a Collapse/Main.asm:7-308 and Data(Past) own every native operation.
 * Standing is a hard fixed-scene prerequisite, never a search/retry or a natural-contact claim.
 * NativeStopped at a missing Solid leaf remains a component obligation: these World fixtures do
 * not corrupt the actual player's raw standing links or invent an unavailable terrain sample.
 */
@Isolated
class TestSonicCdPastCollapseWorld {
    private static final int WHOLE=40;
    private static final LogicalInputSnapshot NEUTRAL=LogicalInputSnapshot.ofPlayers(PlayerInputState.neutral(),PlayerInputState.neutral());
    private static final int[] MAPS={0x20C378,0x20C4A8,0x20C46A,0x20C7F0};
    private final TestSonicCdPastLoadingWorld fixture=new TestSonicCdPastLoadingWorld();
    @BeforeEach void setup(){fixture.setup();}
    @AfterEach void cleanup(){fixture.cleanup();}

    @Test void objectOnlyPastInitUsesTheActualCachedBodyAndFullCourseReplayFromHealthy7851()throws Exception{
        var p=healthyCut();var pool=p.placement().pool();var frame=p.frame();
        var body=assertInstanceOf(CdCollapsePlatform.class,actualField(p.visit(),"collapsePlatform"));
        assertSame(p.placement(),actualField(body,"placement"));var data=assertInstanceOf(CdCollapsePlatform.Assets.class,actualField(body,"assets"));
        assertEquals(List.of(2,6,7,7),List.of(data.collapsePlatform1().mappingFrames().size(),data.collapsePlatform2().mappingFrames().size(),
                data.collapsePlatform3().mappingFrames().size(),data.collapsePlatform4().mappingFrames().size()));
        var m=frame.state().motion();var source=blank(p).moveByte(ID,0x20).moveByte(SUBTYPE,1).moveByte(SUBTYPE2,0xD7)
                .moveByte(SPRITE_FLAGS,4).moveWord(X,(m.centreX16_16()>>>16)+96).moveWord(Y,m.centreY16_16()>>>16)
                .moveWord(X_FRACTION,0x1234).moveWord(Y_FRACTION,0x5678);
        seed(p,source);replay(p,body,"Init");var actual=pool.state(WHOLE);
        byte[] expected=source.copyBytes();expected[0x24]=2;expected[0x18]=3;word(expected,2,0x44BE);putLong(expected,4,MAPS[0]);
        expected[0x1A]=1;expected[0x17]=expected[0x19]=32;expected[0x16]=26;
        expected[1]=(byte)(4|(actual.spriteFlags()&0x80));assertArrayEquals(expected,actual.copyBytes());
        assertEquals(1,calls(p));assertEquals(0,sounds(p));assertSame(body,actualField(p.visit(),"collapsePlatform"));
        assertFalse(frame.closed());assertRetired(p);
    }

    @Test void allFourRealPastMappingRootsReachTheActualCpuDrainWithoutAnAFrameCountFallback()throws Exception{
        var p=healthyCut();var body=assertInstanceOf(CdCollapsePlatform.class,actualField(p.visit(),"collapsePlatform"));
        var registry=p.harness().mode().getRewindRegistry();var base=registry.capture();var before=captureNative(p.harness());
        int[] frames={1,5,6,6};
        for(int root=0;root<4;root++){
            registry.restore(base);complete(before,captureNative(p.harness()),"root restore before seed");
            var s=picture(p,MAPS[root],frames[root],4).moveWord(COLLAPSE_DELAY,2).moveByte(COLLAPSE_SOLID_MARKER,0);
            seed(p,s);replay(p,body,"root"+root);var actual=p.placement().pool().state(WHOLE);
            assertEquals(1,actual.word(COLLAPSE_DELAY));assertEquals(4,actual.routine());assertEquals(MAPS[root],actual.longBits(MAPPINGS));
            var cpu=completedDraw(p);assertTrue(cpu.provenance().stream().anyMatch(piece->piece.slotRef().wholeIndex()==WHOLE),
                    "Fixed visible native B mapping must contribute genuine SAT pieces: root"+root);
            assertEquals(1,calls(p));assertEquals(0,sounds(p));
        }
    }

    @Test void delayTransitionsWithoutFallThenNextPhysicalVisitAddsItsRealLongDelta()throws Exception{
        var p=healthyCut();var body=assertInstanceOf(CdCollapsePlatform.class,actualField(p.visit(),"collapsePlatform"));
        var source=picture(p,MAPS[3],6,4).moveWord(COLLAPSE_DELAY,1).moveByte(COLLAPSE_SOLID_MARKER,0)
                .moveLong(COLLAPSE_FALL_DELTA,0x20000).moveWord(Y_FRACTION,0x5678);
        seed(p,source);replay(p,body,"Delay1->0");var delayed=p.placement().pool().state(WHOLE);
        assertEquals(6,delayed.routine());assertEquals(0,delayed.word(COLLAPSE_DELAY));assertEquals(source.longBits(Y_POSITION),delayed.longBits(Y_POSITION));
        replay(p,body,"Fall");var falling=p.placement().pool().state(WHOLE);
        assertEquals(0x20,falling.id(),"Fixed component must remain within native player-Y deletion distance");
        byte[] expected=delayed.copyBytes();putLong(expected,12,delayed.longBits(Y_POSITION)+0x20000);putLong(expected,0x2C,0x24000);
        expected[1]=(byte)((expected[1]&0x7F)|(falling.spriteFlags()&0x80));assertArrayEquals(expected,falling.copyBytes());
        assertEquals(0,sounds(p));assertRetired(p);
    }

    @Test void fixedRealTerrainStandingBreaksOnceAndKeepsEarlierLaterChildPhysicalOrder()throws Exception{
        var p=healthyCut();var frame=p.frame();var body=assertInstanceOf(CdCollapsePlatform.class,actualField(p.visit(),"collapsePlatform"));
        var m=frame.state().motion();assertEquals(2,m.routine());assertTrue(m.yVelocity()>=0,"Fixed standing scene requires nonrising actual Sonic");
        assertEquals(0,m.flags()&(8|0x20),"No preexisting support/push relation may be replaced by the fixture");
        assertEquals(0,frame.state().objectLinks().standObject());assertEquals(0,m.charge());
        int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
        var terrain=CdPlayerTerrain.fromStageTerrain(p.assets().terrain());
        for(int foot:new int[]{x-m.xRadius(),x+m.xRadius()})assertTrue(terrain.floor(foot,y+m.yRadius(),13,0,16,m.spriteFlags()).distance()>=0,
                "Fixed two-foot real terrain prerequisite; no Y/coordinate search");
        var registry=p.harness().mode().getRewindRegistry();var pristine=registry.capture();var before=captureNative(p.harness());
        // Two fixed physical boundaries exercise both native walk orders without clearing or filling slots.
        // At7851, slots32..39 are occupied; physical40 therefore has only later first-free children.
        // Physical127 is the already-qualified genuine clear high slot, so its children are all earlier.
        for(int parent:new int[]{WHOLE,127}){
            registry.restore(pristine);complete(before,captureNative(p.harness()),"physical"+parent+".pristine");
            var blank=p.placement().pool().state(parent);assertArrayEquals(new byte[64],blank.copyBytes(),"Fixed parent must genuinely be clear");
            var source=blank.moveByte(ID,0x20).moveByte(ROUTINE,2).moveByte(SPRITE_FLAGS,0x84).moveByte(PRIORITY,3)
                    .moveWord(TILE,0x44BE).moveLong(MAPPINGS,MAPS[0]).moveByte(X_RADIUS,40).moveByte(WIDTH,40).moveByte(Y_RADIUS,34)
                    .moveWord(X,x).moveWord(Y,y+m.yRadius()+34);
            var free=new ArrayList<Integer>();for(int whole=32;whole<128;whole++)if(whole!=parent&&p.placement().pool().state(whole).id()==0)free.add(whole);
            assertTrue(free.size()>=18,"Native breakup requires18 genuine clear/free images, never fillers or preclaimed children");
            if(parent==WHOLE)assertTrue(free.getFirst()>parent,"Fixed low parent exercises later children");
            else assertTrue(free.get(17)<parent,"Fixed high parent exercises earlier children");
            seed(p,source);replay(p,body,"standing multi breakup parent"+parent);assertArrayEquals(new byte[64],p.placement().pool().state(parent).copyBytes());
            assertEquals(1,sounds(p),"Only actual completed Top/GetOff may authorize direct A3");
            int[] script={-1,-1,0,0,0,1,2,3,3,4,0,5,5,5,5,6,6,6,6,6};int ordinal=0;
            for(int cell=0;cell<script.length;cell++)if(script[cell]>=0){
                int whole=free.get(ordinal++),row=cell/5,col=cell%5;var child=p.placement().pool().state(whole);
                assertEquals(0x20,child.id());assertEquals(4,child.routine());assertEquals(MAPS[2],child.longBits(MAPPINGS));assertEquals(0x44BE,child.word(TILE));
                assertEquals(script[cell],child.unsignedByte(MAP_FRAME));assertEquals(x-32+16*col,child.centreX());assertEquals(source.centreY()+24-16*row,child.centreY());
                assertEquals(9+5*row+12*col-(whole>parent?1:0),child.word(COLLAPSE_DELAY));assertEquals(0x20000,child.longBits(COLLAPSE_FALL_DELTA));
            }
            assertEquals(18,ordinal);assertRetired(p);
        }
    }

    @Test void reachedInvalidSizeReadKeepsTheActualInitPrefixAndRetiresTheFailedWorldScope()throws Exception{
        var p=healthyCut();var frame=p.frame();var pool=p.placement().pool();var source=picture(p,MAPS[0],0,0).moveByte(SUBTYPE,15);
        seed(p,source);var later=pool.state(41);var failure=assertThrows(CdCollapsePlatform.UnsupportedCollapsePlatform.class,()->step(p.harness(),NEUTRAL));
        assertEquals(WHOLE,failure.wholeIndex());assertEquals(CdCollapsePlatform.NativeTable.SIZES_1,failure.table());
        assertEquals(CdCollapsePlatform.UnsupportedBoundary.SIZE_HEADER_READ,failure.boundary());
        byte[] expected=source.copyBytes();expected[0x24]=2;expected[1]|=4;expected[0x18]=3;word(expected,2,0x44BE);putLong(expected,4,MAPS[0]);expected[0x1A]=15;
        assertArrayEquals(expected,pool.state(WHOLE).copyBytes());assertEquals(later,pool.state(41));assertTrue(frame.closed());assertRetired(p);
        assertEquals(CdObjectDrawQueue.Phase.CLOSED,assertInstanceOf(CdObjectDrawQueue.class,actualField(p.visit(),"drawQueue")).phase());
        var retained=assertInstanceOf(List.class,actualField(actualField(p.visit(),"values"),"calls"));
        assertFalse(retained.contains(SonicCdGameModule.PastLoadCall.COLLAPSE_PLATFORM_BODY));assertFalse(retained.contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
    }

    private static Prepared healthyCut()throws Exception{
        var p=ready();for(int call=7775;call<=7851;call++)step(p.harness(),route(call));assertFalse(p.frame().closed());
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));
        var presentation=assertInstanceOf(SonicCdPlayerPresentation.class,actualField(p.harness().module(),"playerPresentation"));
        assertEquals(presentation.pastForegroundImage().cameraX()&0xFF80,p.placement().status().previousChunkWord(),"Fixed scene prespawn must return in the retained chunk");
        assertArrayEquals(new byte[64],p.placement().pool().state(WHOLE).copyBytes(),"Fixed physical40 must genuinely be clear/free");
        for(int whole=32;whole<128;whole++)assertNotEquals(0x20,p.placement().pool().state(whole).id(),"No natural Collapse is assumed in this constructed component scene");
        return p;
    }
    private static CdObjectState blank(Prepared p){return p.placement().pool().state(WHOLE);}
    private static CdObjectState picture(Prepared p,int map,int frame,int routine)throws Exception{
        var view=assertInstanceOf(SonicCdPlayerPresentation.class,actualField(p.harness().module(),"playerPresentation"));
        return blank(p).moveByte(ID,0x20).moveByte(ROUTINE,routine).moveByte(SPRITE_FLAGS,4).moveByte(PRIORITY,3).moveWord(TILE,0x44BE)
                .moveLong(MAPPINGS,map).moveByte(MAP_FRAME,frame).moveByte(X_RADIUS,40).moveByte(WIDTH,40).moveByte(Y_RADIUS,34)
                .moveWord(X,view.pastForegroundImage().cameraX()+128).moveWord(Y,view.pastForegroundImage().cameraYWord()+112);
    }
    private static void seed(Prepared p,CdObjectState source)throws Exception{
        var actor=p.frame().captureActor();var shared=p.frame().captureShared();var before=p.placement().capture();var values=actualField(p.visit(),"values");
        p.placement().pool().write(source);assertEquals(actor,p.frame().captureActor());assertEquals(shared,p.frame().captureShared());assertSame(values,actualField(p.visit(),"values"));
        var after=p.placement().capture();assertEquals(before.status(),after.status());assertArrayEquals(before.savedFlags().copy(),after.savedFlags().copy());
        for(int at=0;at<96;at++)if(at!=source.identity().wholeIndex()-32)assertEquals(before.pool().slots().get(at),after.pool().slots().get(at));
    }
    private static void replay(Prepared p,CdCollapsePlatform body,String name)throws Exception{
        var h=p.harness();var registry=h.mode().getRewindRegistry();var world=h.mode().getWorldSession();long generation=h.mode().getLevelManager().getCompletedProductionLoadGeneration();
        var full=registry.capture();var course=registry.captureCourse();var before=captureNative(h);
        step(h,NEUTRAL);healthy(p,body,world,generation);var after=captureNative(h);
        registry.restore(full);complete(before,captureNative(h),name+".full.before");step(h,NEUTRAL);healthy(p,body,world,generation);complete(after,captureNative(h),name+".full.after");
        registry.restoreCourse(course);complete(before,captureNative(h),name+".course.before");step(h,NEUTRAL);healthy(p,body,world,generation);complete(after,captureNative(h),name+".course.after");
    }
    private static void healthy(Prepared p,CdCollapsePlatform body,Object world,long generation)throws Exception{
        assertFalse(p.frame().closed());assertSame(p.frame(),p.harness().module().requirePlayerFrame());assertSame(world,p.harness().mode().getWorldSession());
        assertEquals(generation,p.harness().mode().getLevelManager().getCompletedProductionLoadGeneration());assertSame(body,actualField(p.visit(),"collapsePlatform"));
        assertTrue(p.harness().module().pastLoadState().calls().contains(SonicCdGameModule.PastLoadCall.ORDINARY_MAIN_RETURN));assertRetired(p);
    }
    private static long calls(Prepared p){return p.harness().module().pastLoadState().calls().stream().filter(c->c==SonicCdGameModule.PastLoadCall.COLLAPSE_PLATFORM_BODY).count();}
    private static long sounds(Prepared p){return p.harness().module().pastLoadState().sounds().stream().filter(s->s instanceof SonicCdGameModule.PastCollapsePlatformFm fm&&fm.nativeId()==0xA3).count();}
    private static void assertRetired(Prepared p)throws Exception{assertNull(actualField(p.visit(),"activeCollapse"));assertFalse((boolean)actualField(p.visit(),"collapsePort"));assertFalse((boolean)actualField(p.visit(),"drawingCollapse"));}
    private static CdObjectDrawQueue.CpuSat completedDraw(Prepared p)throws Exception{return assertInstanceOf(CdObjectDrawQueue.CpuSat.class,((Optional<?>)actualField(actualField(actualField(p.visit(),"values"),"loop"),"completedDraw")).orElseThrow());}
    private static void word(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    private static void putLong(byte[] b,int at,int value){word(b,at,value>>>16);word(b,at+2,value);}
    private static void complete(Object expected,Object actual,String name)throws Exception{
        if(expected==actual)return;assertNotNull(expected,name);assertNotNull(actual,name);assertEquals(expected.getClass(),actual.getClass(),name);
        if(expected instanceof CdAssetBytes b)assertArrayEquals(b.copy(),((CdAssetBytes)actual).copy(),name);
        else if(expected instanceof CdObjectDrawQueue.CpuSat b){var a=(CdObjectDrawQueue.CpuSat)actual;assertArrayEquals(b.bytes(),a.bytes(),name);assertEquals(b.spriteCount(),a.spriteCount(),name);assertEquals(b.provenance(),a.provenance(),name);}
        else if(expected instanceof Optional<?> b){var a=(Optional<?>)actual;assertEquals(b.isPresent(),a.isPresent(),name);if(b.isPresent())complete(b.orElseThrow(),a.orElseThrow(),name+".value");}
        else if(expected instanceof Map<?,?> b){var a=(Map<?,?>)actual;assertEquals(b.keySet(),a.keySet(),name);for(var key:b.keySet())complete(b.get(key),a.get(key),name+"["+key+"]");}
        else if(expected instanceof List<?> b){var a=(List<?>)actual;assertEquals(b.size(),a.size(),name);for(int n=0;n<b.size();n++)complete(b.get(n),a.get(n),name+'['+n+']');}
        else if(expected.getClass().isArray()){assertEquals(Array.getLength(expected),Array.getLength(actual),name);for(int n=0;n<Array.getLength(expected);n++)complete(Array.get(expected,n),Array.get(actual,n),name+'['+n+']');}
        else if(expected.getClass().isRecord()){for(var component:expected.getClass().getRecordComponents()){var read=component.getAccessor();read.setAccessible(true);complete(read.invoke(expected),read.invoke(actual),name+'.'+component.getName());}}
        else if(Set.of("com.openggf.game.soniccd.SonicCdPastLoadingVisit$Snapshot","com.openggf.game.soniccd.objects.CdObjectPlacementRuntime$Snapshot","com.openggf.game.soniccd.objects.CdObjectPool$Snapshot").contains(expected.getClass().getName())){
            for(var field:expected.getClass().getDeclaredFields()){if(Modifier.isStatic(field.getModifiers()))continue;field.setAccessible(true);complete(field.get(expected),field.get(actual),name+'.'+field.getName());}}
        else assertEquals(expected,actual,name);
    }
}
