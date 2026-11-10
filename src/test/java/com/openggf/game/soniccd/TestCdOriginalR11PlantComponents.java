package com.openggf.game.soniccd;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.objects.Cd3dPlant;
import com.openggf.game.soniccd.objects.CdObjectPlacementRuntime;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import com.openggf.game.soniccd.objects.CdPhysicalObjectWalk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual original-R11 assets; explicitly CONSTRUCTED component pool/player/global controls.
 * These are not the private World placement, a supplied Frame, or replay authority. Java UNRUN. */
@Isolated
class TestCdOriginalR11PlantComponents {
    private static final int ANCHOR=0x700, Y_WORD=0x420;
    private static final Cd3dPlant.Player RAMP_PLAYER=new Cd3dPlant.Player(0x790,2);
    private static final Cd3dPlant.Globals PRESENT=new Cd3dPlant.Globals(1,0,ANCHOR);

    @Test void originalMetadataSuppliesTheExistingStaticRootAndBothNativeOffsetTables()throws Exception{
        var h=new Harness(0);var a=h.body.assets();
        assertEquals(0x2C,Cd3dPlant.OBJECT_ID,"Native original ObjectIndex row2C");
        assertEquals(0x4424,Cd3dPlant.NATIVE_TILE_WORD,"Obj3DPlant_Init native word");
        var expectedPlant=h.assets.openingObjects().plant();
        assertEquals(expectedPlant.mappingFrames().size(),a.plant().mappingFrames().size());
        for(int i=0;i<expectedPlant.mappingFrames().size();i++){
            var expected=expectedPlant.mappingFrames().get(i);var actual=a.plant().mappingFrames().get(i);
            assertEquals(expected.relativeOffset(),actual.relativeOffset());assertEquals(expected.pieces(),actual.pieces());
            assertArrayEquals(expected.bytes().copy(),actual.bytes().copy());
        }
        assertEquals(h.assets.openingObjects().plantOffsets(),a.offsets());
        assertEquals(h.assets.openingObjects().view(CdR11aObjectAssets.Table.PLANT_MAPPINGS).range().nativeAddress(),a.mappingsAddress());
        assertEquals(2,a.plant().mappingFrames().size());
        assertEquals(List.of(0x40,0x80,-0x40,-0x80),a.offsets().main());assertEquals(List.of(0,0x60,-0x60),a.offsets().small());
        assertTrue(h.draws.isEmpty());assertTrue(h.runtime.pool().capture().slots().stream().allMatch(s->s.id()==0));
    }

    @Test void completeEvenFamilyRunsLaterChildrenInTheSameIncreasingPhysicalPass()throws Exception{
        var h=new Harness(0);h.parent(32,0);h.walk();
        assertEquals(List.of(32,33,34,35,36,37,38),h.draws);
        int[] x={ANCHOR+0x40,ANCHOR+0x80+0x48,ANCHOR-0x40+0x48,ANCHOR-0x80+0x48,ANCHOR,ANCHOR+0x60,ANCHOR-0x60};
        for(int i=0;i<x.length;i++){
            var s=h.runtime.pool().state(32+i);assertEquals(Cd3dPlant.OBJECT_ID,s.id());assertEquals(2,s.routine());
            assertEquals(x[i],s.centreX());assertEquals(Y_WORD,s.centreY());assertEquals(ANCHOR,s.word(PLANT_ANCHOR_X));
            assertEquals(Cd3dPlant.NATIVE_TILE_WORD,s.word(TILE));assertEquals(h.body.assets().mappingsAddress(),s.longBits(CdObjectState.LongField.MAPPINGS));
            if(i>=4){assertEquals(1,s.subtype());assertEquals(1,s.unsignedByte(MAP_FRAME));assertEquals(4,s.unsignedByte(PRIORITY));}
        }
        assertEquals(0,h.runtime.pool().state(39).id());
    }

    @Test void oddParentOwnsOnlyTwoMainPlantsAndStillAllThreeSmallChildren()throws Exception{
        var h=new Harness(0);h.parent(32,1);h.walk();
        assertEquals(List.of(32,33,34,35,36),h.draws);assertEquals(0,h.runtime.pool().state(32).subtype());
        assertEquals(0,h.runtime.pool().state(33).subtype());
        for(int w=34;w<=36;w++){assertEquals(1,h.runtime.pool().state(w).subtype());assertEquals(1,h.runtime.pool().state(w).unsignedByte(MAP_FRAME));}
        assertEquals(0,h.runtime.pool().state(37).id());
    }

    @Test void originalInitRetainsUnwrittenDirtyFieldsOfBothChildKinds()throws Exception{
        var h=new Harness(0);h.parent(32,0);
        h.runtime.pool().write(h.runtime.pool().state(33).moveByte(SUBTYPE,0x80).moveByte(PRIORITY,0x5D).moveWord(X_FRACTION,0xA123));
        h.runtime.pool().write(h.runtime.pool().state(36).moveWord(PLANT_BASE_X,0xCAFE).moveWord(Y_FRACTION,0xB234));
        h.body.run(32,RAMP_PLAYER,PRESENT);
        var main=h.runtime.pool().state(33);assertEquals(0x80,main.subtype());assertEquals(0x5D,main.unsignedByte(PRIORITY));assertEquals(0xA123,main.word(X_FRACTION));
        var small=h.runtime.pool().state(36);assertEquals(0xCAFE,small.word(PLANT_BASE_X));assertEquals(0xB234,small.word(Y_FRACTION));
        assertEquals(List.of(32),h.draws,"This constructed control dispatched only the parent");
    }

    @Test void childrenAllocatedBeforeTheCurrentSlotWaitForTheNextPhysicalPass()throws Exception{
        var h=new Harness(0);h.parent(35,0);h.walk();
        assertEquals(List.of(35,36,37,38),h.draws);assertEquals(ANCHOR+0x80,h.runtime.pool().state(32).centreX());
        h.draws.clear();h.walk();assertEquals(List.of(32,33,34,35,36,37,38),h.draws);
        assertEquals(ANCHOR+0x80+0x48,h.runtime.pool().state(32).centreX());assertEquals(0,h.runtime.pool().state(39).id());
    }

    @Test void fullPoolRetainsTheParentInitPrefixBeforeTheFirstUntestedFindFailure()throws Exception{
        var h=new Harness(0);h.fill();h.parent(32,0);var before=h.runtime.pool().capture();
        var stop=assertThrows(Cd3dPlant.UnsupportedPlant.class,()->h.body.run(32,RAMP_PLAYER,PRESENT));
        assertEquals(Cd3dPlant.UnsupportedBoundary.POOL_EXHAUSTED,stop.boundary());assertEquals(32,stop.wholeIndex());
        var parent=h.runtime.pool().state(32);assertEquals(2,parent.routine());assertEquals(ANCHOR+0x40,parent.centreX());assertEquals(ANCHOR,parent.word(PLANT_ANCHOR_X));
        for(int w=33;w<128;w++)assertArrayEquals(before.slots().get(w-32).copyBytes(),h.runtime.pool().state(w).copyBytes());
        assertTrue(h.draws.isEmpty());
    }

    @Test void partiallyFreePoolRetainsEveryCompletedChildAndNeverReplaysEffects()throws Exception{
        var h=new Harness(0);h.fill();h.runtime.pool().deleteObject(33);h.runtime.pool().deleteObject(34);h.parent(32,0);
        var stop=assertThrows(Cd3dPlant.UnsupportedPlant.class,()->h.body.run(32,RAMP_PLAYER,PRESENT));
        assertEquals(Cd3dPlant.UnsupportedBoundary.POOL_EXHAUSTED,stop.boundary());
        for(int w=32;w<=34;w++){assertEquals(Cd3dPlant.OBJECT_ID,h.runtime.pool().state(w).id());assertEquals(2,h.runtime.pool().state(w).routine());}
        assertEquals(ANCHOR+0x80,h.runtime.pool().state(33).centreX());assertEquals(ANCHOR-0x40,h.runtime.pool().state(34).centreX());
        assertEquals(0x06,h.runtime.pool().state(35).id());assertTrue(h.draws.isEmpty());
    }

    @Test void originalDrawFailurePreservesTheCompleteGraphAndSkipsTheDespawnTail()throws Exception{
        var h=new Harness(0);h.parent(32,0);var original=new AssertionError("constructed original draw fault");
        var body=new Cd3dPlant(h.runtime,h.body.assets(),(pool,slot)->{assertSame(h.runtime.pool(),pool);assertEquals(32,slot.wholeIndex());throw original;});
        assertSame(original,assertThrows(AssertionError.class,()->body.run(32,RAMP_PLAYER,new Cd3dPlant.Globals(1,0,0))));
        for(int w=32;w<=38;w++)assertEquals(Cd3dPlant.OBJECT_ID,h.runtime.pool().state(w).id());
    }

    @Test void savedDespawnClearsOnlyTheActualPresentLoadedBitAfterDraw()throws Exception{
        var h=new Harness(0xA5);h.parent(32,1);h.runtime.pool().write(h.runtime.pool().state(32).moveByte(SAVED_FLAGS_ID,7));
        var before=h.runtime.capture().savedFlags().copy();h.body.run(32,RAMP_PLAYER,new Cd3dPlant.Globals(1,0,0));
        assertEquals(List.of(32),h.draws);assertArrayEquals(new byte[64],h.runtime.pool().state(32).copyBytes());
        var after=h.runtime.capture().savedFlags().copy();int selected=2+3*7+1;
        for(int i=0;i<before.length;i++)assertEquals(i==selected?(before[i]&0x7F):Byte.toUnsignedInt(before[i]),Byte.toUnsignedInt(after[i]),"saved byte "+i);
    }

    private static final class Harness {
        final CdR11aAssets assets;final CdObjectPlacementRuntime runtime;final Cd3dPlant body;final List<Integer> draws=new ArrayList<>();
        Harness(int bankByte)throws Exception{
            assets=TestCdOriginalBootPrefix.source().assets();byte[] saved=new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];Arrays.fill(saved,(byte)bankByte);
            runtime=CdObjectPlacementRuntime.fromAssets(assets,CdAssetBytes.copyOf(saved));
            body=new Cd3dPlant(runtime,Cd3dPlant.Assets.fromObjectAssets(assets.openingObjects()),(pool,slot)->{assertSame(runtime.pool(),pool);draws.add(slot.wholeIndex());});
        }
        void parent(int whole,int subtype){runtime.pool().write(runtime.pool().state(whole).moveByte(ID,Cd3dPlant.OBJECT_ID).moveByte(ROUTINE,0).moveByte(SUBTYPE,subtype).moveWord(X,ANCHOR).moveWord(Y,Y_WORD));}
        void fill(){for(int w=32;w<128;w++)runtime.pool().write(runtime.pool().state(w).moveByte(ID,0x06));}
        void walk(){CdPhysicalObjectWalk.run(32,128,w->{if(runtime.pool().state(w).id()==Cd3dPlant.OBJECT_ID)body.run(w,RAMP_PLAYER,PRESENT);});}
    }
}
