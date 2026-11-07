package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdPeriod;
import com.openggf.game.soniccd.CdStagePayload;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.ReadOnlyBufferException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.openggf.game.soniccd.level.TestCdR31aAssets.*;

/** Authored first; original-media DATA relation, not a World/Frame/RunR31 admission. Java UNRUN. */
class TestCdR31aPlacementSource {
    @Test void all517RowsRetainEveryOriginalFieldAndTrueSourceIndex() throws Exception {
        var a=actual().assets();var p=a.placements();var raw=a.view(CdR31aAssets.Asset.OBJECT_LAYOUTS);
        assertSame(p,CdObjectPlacements.fromR31aAssets(a));assertSame(p,a.placements());
        assertEquals(517,p.records().size());assertEquals(63,CdR31aAssets.Asset.values().length);
        for(int i=0;i<517;i++){
            var row=p.records().get(i);int at=12+8*i;
            assertEquals(i,row.index());assertEquals(CdObjectPlacements.Layout.R31A_517,row.layout());
            assertEquals(raw.unsignedWord(at),row.centreX());assertEquals(raw.unsignedWord(at+2),row.packedY());
            assertEquals(raw.unsignedByte(at+4),row.encodedId());assertEquals(raw.unsignedByte(at+5),row.subtype());
            assertEquals(raw.unsignedByte(at+6),row.periodFlags());assertEquals(raw.unsignedByte(at+7),row.subtype2());
            assertArrayEquals(a.placementRows().get(i).copy(),raw.slice(at,8).copy());
            assertEquals(row.packedY()&0xFFF,row.centreY());assertEquals(row.packedY()&0xF000,row.nativeYFlags());
            assertEquals((row.packedY()&0x4000)!=0,row.flipX());assertEquals((row.packedY()&0x8000)!=0,row.flipY());
            assertEquals(row.encodedId()&0x7F,row.objectId());assertEquals((row.encodedId()&0x80)!=0,row.hasSavedFlag());
            if(i>0)assertTrue(row.centreX()>=p.records().get(i-1).centreX());
        }
        assertEquals("cd825a16352a1f385b0f5cc3dec98ec7d8b05c9a0f4621506f0e3011042cbce5",hash(raw.copy()));
    }

    @Test void actualDirectoryAndThreeSentinelsKeepTheirSeparateNativeSpans() throws Exception {
        var a=actual().assets();var raw=a.view(CdR31aAssets.Asset.OBJECT_LAYOUTS);var p=a.placements();
        assertEquals(4162,raw.length());assertEquals(0x207BAE,a.range(CdR31aAssets.Asset.OBJECT_LAYOUTS).nativeAddress());
        assertEquals(12,raw.unsignedWord(0));assertEquals(4156,raw.unsignedWord(2));
        assertEquals("ffff000000000000",HexFormat.of().formatHex(p.backwardSentinel().orElseThrow().copy()));
        assertEquals("ffff000000000000",HexFormat.of().formatHex(p.forwardSentinel().copy()));
        assertEquals("ffff00000000",HexFormat.of().formatHex(p.nullList().orElseThrow().copy()));
        assertArrayEquals(raw.slice(4,8).copy(),p.backwardSentinel().orElseThrow().copy());
        assertArrayEquals(raw.slice(4148,8).copy(),p.forwardSentinel().copy());
        assertArrayEquals(raw.slice(4156,6).copy(),p.nullList().orElseThrow().copy());
    }

    @Test void lateIndicesAreNotWrappedIntoEarlierRowsOrSavedByteIds() throws Exception {
        var p=actual().assets().placements();
        int[] indices={0,254,255,256,496,497,513,514,515,516};
        String[] hex={"01a403b09902e000","16a843d00a04e000","16c003309024e000","16c803a89908c000","29c002809900a000","29c0028099014000","2b8004882fffc000","2bc005849600e000","2f3805949700e000","2f9005309400e000"};
        for(int i=0;i<indices.length;i++)assertEquals(hex[i],HexFormat.of().formatHex(actual().assets().placementRows().get(indices[i]).copy()));
        assertEquals(497,p.records().get(497).index());assertEquals(516,p.records().get(516).index());
        assertNotEquals(p.records().get(241),p.records().get(497));
        assertEquals(224,p.records().stream().filter(CdObjectPlacements.Placement::hasSavedFlag).count());
        int saved=0;for(var r:p.records()){if(r.hasSavedFlag())saved++;if(r.index()==497)assertEquals(215,saved);}
        assertEquals(224,saved,"Native byte header finishes at225, without wrap for this source");
    }

    @Test void allPeriodColumnsAndEncodedObjectIdsHaveTheLiteralSourceCensus() throws Exception {
        var p=actual().assets().placements();Map<Integer,Long> masks=new TreeMap<>();Map<Integer,Long> ids=new TreeMap<>();
        for(var r:p.records()){masks.merge(r.periodFlags(),1L,Long::sum);ids.merge(r.objectId(),1L,Long::sum);}
        assertEquals(Map.of(0x20,46L,0x40,60L,0x60,29L,0x80,56L,0xA0,6L,0xC0,25L,0xE0,295L),masks);
        assertEquals(376,p.forPeriod(CdPeriod.PAST).size());assertEquals(409,p.forPeriod(CdPeriod.PRESENT).size());
        assertEquals(382,p.forPeriod(CdPeriod.GOOD_FUTURE).size());assertEquals(p.forPeriod(CdPeriod.GOOD_FUTURE),p.forPeriod(CdPeriod.BAD_FUTURE));
        assertEquals(Map.ofEntries(Map.entry(0x0A,103L),Map.entry(0x10,89L),Map.entry(0x13,4L),Map.entry(0x14,1L),Map.entry(0x16,1L),Map.entry(0x17,1L),Map.entry(0x19,28L),Map.entry(0x1D,63L),Map.entry(0x1E,8L),Map.entry(0x20,25L),Map.entry(0x21,13L),Map.entry(0x22,15L),Map.entry(0x23,9L),Map.entry(0x24,17L),Map.entry(0x26,1L),Map.entry(0x27,6L),Map.entry(0x2A,6L),Map.entry(0x2C,2L),Map.entry(0x2D,27L),Map.entry(0x2E,23L),Map.entry(0x2F,5L),Map.entry(0x30,4L),Map.entry(0x32,1L),Map.entry(0x35,32L),Map.entry(0x37,1L),Map.entry(0x38,1L),Map.entry(0x39,31L)),ids);
    }

    @Test void originalSevenArgumentConstructorKeepsIts497RowAnd64IdAdmission() {
        var row=new CdObjectPlacements.Placement(496,1,0,64,255,0xE0,255);
        assertEquals(CdObjectPlacements.Layout.LEGACY_497,row.layout());
        assertEquals(row,new CdObjectPlacements.Placement(496,1,0,64,255,0xE0,255,CdObjectPlacements.Layout.LEGACY_497));
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(497,1,0,1,0,0xE0,0));
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(-1,1,0,1,0,0xE0,0));
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(0,1,0,65,0,0xE0,0));
        assertThrows(UnsupportedOperationException.class,()->new CdObjectPlacements.Placement(0,1,0,1,0,0,0));
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(497,1,0,1,0,0,0));
        for(int[] r:new int[][]{{0,-1,0,1,0,0xE0,0},{0,65535,0,1,0,0xE0,0},{0,1,65536,1,0,0xE0,0},{0,1,0,128,0,0xE0,0},{0,1,0,256,0,0xE0,0},{0,1,0,1,256,0xE0,0},{0,1,0,1,0,0xE0,-1}})
            assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(r[0],r[1],r[2],r[3],r[4],r[5],r[6]));
        assertThrows(UnsupportedOperationException.class,()->new CdObjectPlacements.Placement(0,1,0,1,0,0xE1,0));
    }

    @Test void finiteR31ValueProfileHasNoUniversalWideningOrSourceAuthority() {
        assertEquals(516,new CdObjectPlacements.Placement(516,1,0,60,0,0xE0,0,CdObjectPlacements.Layout.R31A_517).index());
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(517,1,0,1,0,0xE0,0,CdObjectPlacements.Layout.R31A_517));
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(0,1,0,61,0,0xE0,0,CdObjectPlacements.Layout.R31A_517));
        assertThrows(NullPointerException.class,()->new CdObjectPlacements.Placement(0,1,0,1,0,0xE0,0,null));
        assertEquals(List.of("LEGACY_497","R31A_517"),Arrays.stream(CdObjectPlacements.Layout.values()).map(Enum::name).toList());
        assertEquals(List.of("index","centreX","packedY","encodedId","subtype","periodFlags","subtype2","layout"),Arrays.stream(CdObjectPlacements.Placement.class.getRecordComponents()).map(c->c.getName()).toList());
    }

    @Test void genericDecoderBoundsAndExistingLegacyDecodedProfileStayStrict() throws Exception {
        var a=actual().assets();var raw=a.view(CdR31aAssets.Asset.OBJECT_LAYOUTS);
        assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeLayout(raw.copy()));
        assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeRecords(raw.slice(12,4144).copy()));
        byte[] records=new byte[3984];for(int i=0;i<497;i++){records[i*8+1]=1;records[i*8+4]=64;records[i*8+6]=(byte)0xE0;}records[3976]=records[3977]=(byte)0xFF;
        var decoded=CdObjectPlacements.decodeRecords(records);assertEquals(497,decoded.records().size());
        assertTrue(decoded.records().stream().allMatch(r->r.layout()==CdObjectPlacements.Layout.LEGACY_497));
        assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeRecords(new byte[3992]));
        assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeLayout(new byte[4003]));
    }

    @Test void retainedSourceBridgeRejectsEqualPublicValuesFromAnotherOwner() throws Exception {
        var f=actual();var a=f.assets();var independent=CdR31aAssets.fromPrograms(source(f,f.image()),CdStagePayload.R31A.stage());
        assertEquals(a.identity(),independent.identity());assertEquals(a.placements().records(),independent.placements().records());
        assertNotSame(a.placements(),independent.placements());assertSame(independent.placements(),CdObjectPlacements.fromR31aAssets(independent));
        var fake=mock(CdR31aAssets.class);when(fake.identity()).thenReturn(a.identity());when(fake.placements()).thenReturn(a.placements());
        assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.fromR31aAssets(fake));
        assertThrows(NullPointerException.class,()->CdObjectPlacements.fromR31aAssets(null));
    }

    @Test void cachedRowsAndSentinelBytesRemainImmutableAfterSourceClosure() throws Exception {
        var a=actual().assets();var p=a.placements();var original=p.forwardSentinel().copy();var detached=p.forwardSentinel().copy();Arrays.fill(detached,(byte)0);
        assertArrayEquals(original,p.forwardSentinel().copy());assertThrows(ReadOnlyBufferException.class,()->p.forwardSentinel().buffer().put(0,(byte)0));
        assertThrows(UnsupportedOperationException.class,()->p.records().clear());assertThrows(UnsupportedOperationException.class,()->p.forPeriod(CdPeriod.PRESENT).clear());
        assertThrows(NullPointerException.class,()->p.forPeriod(null));assertSame(p,a.placements());
    }

    @Test void onlyAuthenticatedR31FactoryAndFiniteValueProfileAreNewPublicSurface() throws Exception {
        assertEquals(CdObjectPlacements.class,CdObjectPlacements.class.getDeclaredMethod("fromR31aAssets",CdR31aAssets.class).getReturnType());
        assertFalse(Modifier.isPublic(CdObjectPlacements.class.getDeclaredMethod("decodeR31NativeOrder",CdR31aAssets.class).getModifiers()));
        for(var c:CdObjectPlacements.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(c.getModifiers()));
        assertEquals(List.of(7,8),Arrays.stream(CdObjectPlacements.Placement.class.getConstructors()).map(c->c.getParameterCount()).sorted().toList());
        assertEquals(CdObjectPlacements.class,CdR31aAssets.class.getDeclaredMethod("placements").getReturnType());
        assertEquals(63,aCount());
    }
    private static int aCount(){return CdR31aAssets.Asset.values().length;}
}
