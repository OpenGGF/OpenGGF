package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.nio.file.Path;
import java.nio.ReadOnlyBufferException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Actual original placement DATA only: no spawner/saved bank/Frame/World authority. */
class TestCdOriginalPlacementData {
    private record Fact(int offset,int bytes,int count,String sha,String rowsSha,int past,int present,int future) { }
    private static CdProgramAssets source;
    private static synchronized CdProgramAssets media()throws Exception {
        if(source==null)source=CdProgramAssets.open(Path.of(System.getProperty("soniccd.media.root")).resolve("USA/Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty());
        return source;
    }
    @AfterAll static void close()throws Exception {if(source!=null)source.close();source=null;}
    private static void actual(String id)throws Exception {
        var stage=CdStageKey.parse(id);var f=fact(id);var reader=CdProgrammeReader.open(media(),stage);
        var data=reader.placements();assertSame(data,reader.placements());
        var exposed=CdObjectPlacements.fromProgrammeAssets(media(),stage);assertEquals(data.records(),exposed.records());assertNotSame(data,exposed);
        assertEquals(f.count(),data.records().size());var raw=CdAssetBytes.copyOf(media().mmd(CdNativeFile.valueOf(id)).readFile(f.offset(),f.bytes()));
        assertEquals(f.sha(),CdR11aProfile.sha256(raw.copy()));assertEquals(f.rowsSha(),CdR11aProfile.sha256(raw.slice(12,f.count()*8).copy()));
        assertArrayEquals(raw.slice(0,4).copy(),data.layoutHeader().orElseThrow().copy());
        assertArrayEquals(raw.slice(4,8).copy(),data.backwardSentinel().orElseThrow().copy());
        assertArrayEquals(raw.slice(raw.length()-14,8).copy(),data.forwardSentinel().copy());
        assertArrayEquals(raw.slice(raw.length()-6,6).copy(),data.nullList().orElseThrow().copy());
        for(int i=0;i<f.count();i++) {
            var row=data.records().get(i);var bytes=raw.slice(12+i*8,8);assertArrayEquals(bytes.copy(),data.rawRows().get(i).copy());
            assertEquals(i,row.index());assertEquals(bytes.unsignedWord(0),row.centreX());assertEquals(bytes.unsignedWord(2),row.packedY());
            assertEquals(bytes.unsignedByte(4),row.encodedId());assertEquals(bytes.unsignedByte(5),row.subtype());assertEquals(bytes.unsignedByte(6),row.periodFlags());assertEquals(bytes.unsignedByte(7),row.subtype2());
            assertEquals(row.packedY()&4095,row.centreY());assertEquals(row.packedY()&0xF000,row.nativeYFlags());assertEquals((row.packedY()&0x4000)!=0,row.flipX());assertEquals((row.packedY()&0x8000)!=0,row.flipY());
            assertEquals((row.encodedId()&128)!=0,row.hasSavedFlag());assertEquals(row.encodedId()&127,row.objectId());
        }
        assertEquals(f.past(),data.forPeriod(CdPeriod.PAST).size());assertEquals(f.present(),data.forPeriod(CdPeriod.PRESENT).size());assertEquals(f.future(),data.forPeriod(CdPeriod.GOOD_FUTURE).size());assertEquals(data.forPeriod(CdPeriod.GOOD_FUTURE),data.forPeriod(CdPeriod.BAD_FUTURE));
        if(id.startsWith("R73")) {var zero=data.records().get(30);assertEquals(0,zero.periodFlags());for(var period:CdPeriod.values())assertFalse(data.forPeriod(period).contains(zero));}
        if(id.startsWith("R63")) {assertEquals(0xA08,data.records().get(64).centreX());assertEquals(0xA07,data.records().get(65).centreX());assertEquals(0xA06,data.records().get(66).centreX());}
        assertThrows(UnsupportedOperationException.class,()->data.rawRows().clear());assertThrows(UnsupportedOperationException.class,()->data.records().clear());
        assertThrows(ReadOnlyBufferException.class,()->data.rawRows().getFirst().buffer().put(0,(byte)0));
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void allR7OriginalPhysicalData(CdR7DataLayout.Programme p)throws Exception {actual(p.name());}
    @ParameterizedTest @EnumSource(CdR6DataLayout.Programme.class)
    void allCompatibleR6OriginalOrderControls(CdR6DataLayout.Programme p)throws Exception {actual(p.name());}
    @Test void existingR31AndR32ConsumersKeepTheirExactRowsAndProfiles()throws Exception {
        var a=CdR31aAssets.fromPrograms(media(),CdStagePayload.R31A.stage());var data=CdObjectPlacements.fromProgrammeAssets(media(),CdStagePayload.R31A.stage());
        assertEquals(a.placements().records(),data.records());assertSame(a.placementRows(),a.placements().rawRows());assertSame(a.placements(),CdObjectPlacements.fromR31aAssets(a));
        assertEquals(517,data.records().size());assertTrue(data.records().stream().allMatch(r->r.layout()==CdObjectPlacements.Layout.R31A_517));
        var r32=CdObjectPlacements.fromProgrammeAssets(media(),CdStagePayload.R32A.stage());assertEquals(454,r32.records().size());assertTrue(r32.records().stream().allMatch(r->r.layout()==CdObjectPlacements.Layout.LEGACY_497));
    }
    @Test void originalBytePredicatePreservesZeroLowBitsAndAllFourSemanticQueries() {
        var layout=CdObjectPlacements.Layout.ORIGINAL_79_60;
        for(int mask=0;mask<256;mask++) {
            var row=new CdObjectPlacements.Placement(0,1,0xF123,1,255,mask,255,layout);
            int nativeBits=(((mask<<3)|(mask>>>5))&255)&7;
            assertEquals((nativeBits&1)!=0,row.appliesTo(CdPeriod.PAST));assertEquals((nativeBits&2)!=0,row.appliesTo(CdPeriod.PRESENT));
            assertEquals((nativeBits&4)!=0,row.appliesTo(CdPeriod.GOOD_FUTURE));assertEquals(row.appliesTo(CdPeriod.GOOD_FUTURE),row.appliesTo(CdPeriod.BAD_FUTURE));
        }
        assertThrows(UnsupportedOperationException.class,()->new CdObjectPlacements.Placement(0,1,0,1,0,256,0,layout));
        assertThrows(UnsupportedOperationException.class,()->new CdObjectPlacements.Placement(0,1,0,1,0,-1,0,layout));
        assertThrows(IllegalArgumentException.class,()->new CdObjectPlacements.Placement(79,1,0,1,0,0,0,layout));
        assertThrows(UnsupportedOperationException.class,()->new CdObjectPlacements.Placement(0,1,0,1,0,0,0));
        assertThrows(UnsupportedOperationException.class,()->new CdObjectPlacements.Placement(0,1,0,1,0,0x41,0));
    }
    @Test void malformedHeadersCountsShortSentinelsAndDirtyTrailingFieldsNeverCache()throws Exception {
        var r=spy(CdProgrammeReader.open(media(),CdStagePayload.R73C.stage()));var binding=r.definition().placements().orElseThrow();var original=r.view(binding.key());int n=original.length();
        var bad=new ArrayList<byte[]>();bad.add(Arrays.copyOf(original.copy(),n-1));bad.add(Arrays.copyOf(original.copy(),n+8));
        for(int at:new int[]{0,2,4,6,10,n-14,n-12,n-8,n-6,n-4}) {var b=original.copy();b[at]^=1;bad.add(b);}
        var early=original.copy();early[12]=early[13]=(byte)255;bad.add(early);
        var dirtyMask=original.copy();dirtyMask[18]^=1;bad.add(dirtyMask);
        var dirtySubtype2=original.copy();dirtySubtype2[19]^=1;bad.add(dirtySubtype2);
        for(var bytes:bad) {
            doReturn(CdAssetBytes.copyOf(bytes)).when(r).view(binding.key());assertThrows(IllegalArgumentException.class,r::placements);
        }
        doReturn(original).when(r).view(binding.key());var success=r.placements();assertSame(success,r.placements());assertEquals(79,success.records().size());
        assertArrayEquals(original.copy(),r.view(binding.key()).copy());
    }
    @Test void foreignBindingAndFirstUnavailableReadRemainUnpublishedAndCanRetry()throws Exception {
        var r=spy(CdProgrammeReader.open(media(),CdStagePayload.R73C.stage()));var own=r.definition();var key=own.placements().orElseThrow().key();var bytes=r.view(key);clearInvocations(r);
        doReturn(CdProgrammeDefinition.select(CdStagePayload.R73D.stage())).when(r).definition();assertThrows(IllegalArgumentException.class,r::placements);
        doReturn(own).when(r).definition();var reached=new AssertionError("first placement source unavailable");doThrow(reached).when(r).view(key);
        assertSame(reached,assertThrows(AssertionError.class,r::placements));doReturn(bytes).when(r).view(key);var success=r.placements();clearInvocations(r);assertSame(success,r.placements());verify(r,never()).view(any());
        var unavailable=spy(CdProgrammeReader.open(media(),CdStagePayload.R73C.stage()));doReturn(CdProgrammeDefinition.select(CdStagePayload.R41A.stage())).when(unavailable).definition();clearInvocations(unavailable);
        assertEquals("No authenticated original placement profile",assertThrows(UnsupportedOperationException.class,unavailable::placements).getMessage());verify(unavailable,never()).view(any());
    }
    @Test void publicEqualValuesAndWrongImageNeverCreateAnAuthenticatedSource()throws Exception {
        var p=CdObjectPlacements.fromProgrammeAssets(media(),CdStagePayload.R61A.stage());var row=p.records().getFirst();
        assertEquals(row,new CdObjectPlacements.Placement(row.index(),row.centreX(),row.packedY(),row.encodedId(),row.subtype(),row.periodFlags(),row.subtype2(),row.layout()));
        for(var c:CdObjectPlacements.class.getDeclaredConstructors())assertTrue(java.lang.reflect.Modifier.isPrivate(c.getModifiers()));
        var wrong=mock(CdProgramAssets.class,delegatesTo(media()));doReturn(media().mmd(CdNativeFile.R71B)).when(wrong).mmd(CdNativeFile.R71A);
        assertThrows(java.io.IOException.class,()->CdObjectPlacements.fromProgrammeAssets(wrong,CdStagePayload.R71A.stage()));
        assertThrows(NullPointerException.class,()->CdObjectPlacements.fromProgrammeAssets(null,CdStagePayload.R71A.stage()));
        var r=CdProgrammeReader.open(media(),CdStagePayload.R73C.stage());var key=r.definition().placements().orElseThrow().key();var other=CdProgrammeReader.open(media(),CdStagePayload.R73D.stage()).definition().placements().orElseThrow().key();
        assertThrows(IllegalArgumentException.class,()->r.view(other));assertNotSame(r.placements(),CdProgrammeReader.open(media(),CdStagePayload.R73C.stage()).placements());
    }
    private static Fact fact(String id) {return switch(id) {
        case "R61A" -> new Fact(0x79CC,3714,461,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a","134024ccde96118a87d6f6a8ea29439ba810326668aec931efccfe520e17aed7",307,306,302);
        case "R61B" -> new Fact(0x796A,3714,461,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a","134024ccde96118a87d6f6a8ea29439ba810326668aec931efccfe520e17aed7",307,306,302);
        case "R61C" -> new Fact(0x797C,3714,461,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a","134024ccde96118a87d6f6a8ea29439ba810326668aec931efccfe520e17aed7",307,306,302);
        case "R61D" -> new Fact(0x79A4,3714,461,"bdc535df23e72e55cc38f8a57cac761832e22841871a2477d758a777b744f81a","134024ccde96118a87d6f6a8ea29439ba810326668aec931efccfe520e17aed7",307,306,302);
        case "R62A" -> new Fact(0x7980,2890,358,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49","6f8efd874fad5b621498b19b4b15768fd00781db09160ffeb93b6021a7eabd88",269,299,286);
        case "R62B" -> new Fact(0x7966,2890,358,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49","6f8efd874fad5b621498b19b4b15768fd00781db09160ffeb93b6021a7eabd88",269,299,286);
        case "R62C" -> new Fact(0x7924,2890,358,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49","6f8efd874fad5b621498b19b4b15768fd00781db09160ffeb93b6021a7eabd88",269,299,286);
        case "R62D" -> new Fact(0x794C,2890,358,"e57c8993c92f17bbb151d4159af305c2fe8e3e142eddcad2d15c74b372d60f49","6f8efd874fad5b621498b19b4b15768fd00781db09160ffeb93b6021a7eabd88",269,299,286);
        case "R63C" -> new Fact(0x78D8,602,72,"b5d03a0be3fde57fa3d243f1203905bc7094ea9b17e5d7bab68f0756db8e2db7","2c020ccfc4112d771906a1c0975505053f444d349f2a983fc94316f04698fef8",0,0,72);
        case "R63D" -> new Fact(0x7900,602,72,"b5d03a0be3fde57fa3d243f1203905bc7094ea9b17e5d7bab68f0756db8e2db7","2c020ccfc4112d771906a1c0975505053f444d349f2a983fc94316f04698fef8",0,0,72);
        case "R71A" -> new Fact(0x78E4,5146,640,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611","4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06",415,477,470);
        case "R71B" -> new Fact(0x791A,5146,640,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611","4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06",415,477,470);
        case "R71C" -> new Fact(0x79A6,5146,640,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611","4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06",415,477,470);
        case "R71D" -> new Fact(0x7996,5146,640,"b810bf1b43228a6ac2cd25449f37bf1dffeb7a52e281cfd2dab9682a3fa8a611","4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06",415,477,470);
        case "R72A" -> new Fact(0x78E4,6602,822,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58","2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b",558,605,579);
        case "R72B" -> new Fact(0x791A,6602,822,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58","2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b",558,605,579);
        case "R72C" -> new Fact(0x79A6,6602,822,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58","2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b",558,605,579);
        case "R72D" -> new Fact(0x7996,6602,822,"7395b40cd0d5e26c5a99209b16d1b8ca827fac1bbdc1aa542933ce23f7d02f58","2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b",558,605,579);
        case "R73C" -> new Fact(0x783A,658,79,"f7846f26330b6ef436032f03dd5b72ae7cadd6c43e79f218c83ef76ddd623b4b","ecbd942234a20a891332e8ec9e13078851a77ad251b53ab73304d1c129d5c0f5",0,0,78);
        case "R73D" -> new Fact(0x7848,658,79,"f7846f26330b6ef436032f03dd5b72ae7cadd6c43e79f218c83ef76ddd623b4b","ecbd942234a20a891332e8ec9e13078851a77ad251b53ab73304d1c129d5c0f5",0,0,78);
        default -> throw new IllegalArgumentException("Unreviewed test programme");
    };}
}
