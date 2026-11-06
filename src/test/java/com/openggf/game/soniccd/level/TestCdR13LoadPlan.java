package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.resources.CdR11aPlcBuffer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import java.util.*;
import static com.openggf.game.soniccd.level.TestCdR13SectionSources.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure load metadata and existing descriptor-format controls only. No clear/bit/service/adoption. Java UNRUN.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
class TestCdR13LoadPlan {
    @Test void originalLevelStartDescribesImmediateThreeThenOneInActualRowOrder()throws Exception {
        for(var a:actual().all()){var p=CdR13LoadPlan.fromAssets(a);var operations=p.levelStartOperations();assertEquals(2,operations.size());assertEquals(List.of(3,1),operations.stream().map(CdR13LoadPlan.PlcOperation::nativeId).toList());
            for(var op:operations){assertEquals(CdR13LoadPlan.Delivery.IMMEDIATE,op.delivery());assertEquals(CdR13LoadPlan.Condition.ALWAYS,op.condition());assertSame(a.plcs().get(op.nativeId()),op.sourceList());assertRows(a,op);}
            assertEquals(a.plcs().get(0).bodyRange(),operations.getFirst().sourceList().bodyRange());assertEquals(0,operations.getFirst().entries().getFirst().destination());
        }
    }
    @Test void levelDataDescribesOnlyQueuedTwoWithExplicitNativeBitOneCondition()throws Exception {
        for(var a:actual().all()){var p=CdR13LoadPlan.fromAssets(a);assertEquals(1,p.levelDataOperations().size());var op=p.levelDataOperations().getFirst();assertEquals(2,op.nativeId());assertEquals(a.levelIndex().blocks().plcId(),op.nativeId());assertEquals(CdR13LoadPlan.Delivery.QUEUED,op.delivery());assertEquals(CdR13LoadPlan.Condition.PLC_LOAD_FLAGS_BIT_1_SET,op.condition());assertSame(a.plcs().get(2),op.sourceList());assertRows(a,op);}
    }
    @Test void actThreeSectionRtsDescribesNoThresholdOrSubmission()throws Exception {
        for(var a:actual().all()){var p=CdR13LoadPlan.fromAssets(a);assertTrue(p.initSectionOperations().isEmpty());assertTrue(p.updateSectionOperations().isEmpty());assertThrows(UnsupportedOperationException.class,()->p.initSectionOperations().clear());assertThrows(UnsupportedOperationException.class,()->p.updateSectionOperations().clear());}
    }
    @Test void planBorrowsActualProductsPaletteBoundsAndAllNativeMetadata()throws Exception {
        for(var a:actual().all()){var p=CdR13LoadPlan.fromAssets(a);assertTrue(p.ownsAssets(a));assertSame(a.identity(),p.identity());assertSame(a.terrain(),p.terrain());assertSame(a.blocksDecode(),p.blocksDecode());assertSame(a.levelTilesDecode(),p.levelTilesDecode());assertSame(a.levelIndex(),p.levelIndex());assertSame(a.bounds(),p.bounds());assertSame(a.initialCentre(),p.initialCentre());assertSame(a.initialPalette(),p.initialPalette());assertSame(a.paletteBindings(),p.paletteBindings());assertSame(a.plcs(),p.plcs());assertTrue(p.sources().ownsAssets(a));assertSame(a.levelTilesDecode(),p.sources().decoded(CdR13SectionSources.SourceId.ART_LEVEL_TILES));}
    }
    @Test void descriptorMetadataCanBeUsedOnlyByAnExplicitCallerOfTheExistingBuffer()throws Exception {
        // Constructed descriptor-format control; no native clear or runtime queue is claimed.
        var a=actual().good();var plan=CdR13LoadPlan.fromAssets(a);byte[] incoming=new byte[96];for(int i=0;i<96;i++)incoming[i]=(byte)(i*13+1);putLong(incoming,0,0x234560);putWord(incoming,4,0x20);putLong(incoming,6,0);var before=CdR11aPlcBuffer.fromBytes(CdAssetBytes.copyOf(incoming));
        var metadata=plan.levelDataOperations().getFirst();assertArrayEquals(incoming,before.bytes().copy());var after=CdR11aPlcBuffer.append(before,metadata.entries());assertArrayEquals(incoming,before.bytes().copy());
        for(int i=0;i<metadata.entries().size();i++){var e=metadata.entries().get(i);assertEquals(e.source(),after.bytes().unsignedLong(6+i*6));assertEquals(e.destination(),after.bytes().unsignedWord(10+i*6));}assertArrayEquals(Arrays.copyOfRange(incoming,30,96),after.bytes().slice(30,66).copy());
    }
    @Test void repeatedPlansAndEqualIdentityOwnersDoNotCreateInvocationOrRawIngress()throws Exception {
        var f=actual();var first=CdR13LoadPlan.fromAssets(f.good());var again=CdR13LoadPlan.fromAssets(f.good());assertNotSame(first,again);assertTrue(again.ownsAssets(f.good()));assertFalse(first.ownsAssets(f.sameGood()));assertFalse(first.ownsAssets(f.bad()));assertFalse(first.ownsAssets(null));assertThrows(NullPointerException.class,()->CdR13LoadPlan.fromAssets(null));
        assertTrue(Modifier.isFinal(CdR13LoadPlan.class.getModifiers()));for(var c:CdR13LoadPlan.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));
        var factories=Arrays.stream(CdR13LoadPlan.class.getDeclaredMethods()).filter(m->Modifier.isPublic(m.getModifiers())&&Modifier.isStatic(m.getModifiers())).toList();assertEquals(1,factories.size());assertArrayEquals(new Class<?>[]{CdR13FutureAssets.class},factories.getFirst().getParameterTypes());
        assertThrows(UnsupportedOperationException.class,()->first.levelStartOperations().clear());assertThrows(UnsupportedOperationException.class,()->first.levelStartOperations().getFirst().entries().clear());
        for(var m:CdR13LoadPlan.class.getDeclaredMethods())if(Modifier.isPublic(m.getModifiers())){assertFalse(Arrays.asList(m.getParameterTypes()).contains(boolean.class));assertNotEquals(CdR11aPlcBuffer.State.class,m.getReturnType());}
    }
    private static void assertRows(CdR13FutureAssets a,CdR13LoadPlan.PlcOperation op){assertEquals(a.plcs().get(op.nativeId()).entries().size(),op.entries().size());for(int i=0;i<op.entries().size();i++){var raw=a.plcs().get(op.nativeId()).entries().get(i);assertEquals(raw.sourceNativeAddress(),op.entries().get(i).source());assertEquals(raw.vramAddress(),op.entries().get(i).destination());}}
    private static void putLong(byte[] b,int at,long v){b[at]=(byte)(v>>>24);b[at+1]=(byte)(v>>>16);b[at+2]=(byte)(v>>>8);b[at+3]=(byte)v;}private static void putWord(byte[] b,int at,int v){b[at]=(byte)(v>>>8);b[at+1]=(byte)v;}
}
