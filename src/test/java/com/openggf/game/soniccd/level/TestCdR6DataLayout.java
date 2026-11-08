package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.resources.CdNemesisPlcDecoder;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Original-media DATA only. Expected signatures independently follow pinned native source tables.
 * No native execution, World, route, pixel, art-residency or regional qualification.
 */
class TestCdR6DataLayout {
    private record Fixture(Path cue,Map<CdR6DataLayout.Programme,CdProgrammeReader> readers,
                           Map<CdR6DataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained!=null)return retained;
        var root=System.getProperty("soniccd.media.root");assertNotNull(root,"Original media property required");
        var cue=Path.of(root).resolve("USA/Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
        var readers=new EnumMap<CdR6DataLayout.Programme,CdProgrammeReader>(CdR6DataLayout.Programme.class);
        var images=new EnumMap<CdR6DataLayout.Programme,CdMmdImage>(CdR6DataLayout.Programme.class);
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var p:CdR6DataLayout.Programme.values()) {
                readers.put(p,CdProgrammeReader.open(source,p.payload().stage()));images.put(p,source.mmd(p.file()));
            }
        }
        retained=new Fixture(cue,Map.copyOf(readers),Map.copyOf(images));return retained;
    }
    private static void word(ByteArrayOutputStream out,int n) {out.write(n>>>8);out.write(n);}
    private static void longword(ByteArrayOutputStream out,long n) {word(out,(int)(n>>>16));word(out,(int)n);}
    @ParameterizedTest @EnumSource(CdR6DataLayout.Programme.class)
    void everyIdentityAndRangeUsesTheActualCommonReader(CdR6DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var image=actual().images().get(p);
        assertEquals(p.rawZoneActWord(),switch(p) {case R61A,R61B,R61C,R61D->0x400;case R62A,R62B,R62C,R62D->0x401;case R63C,R63D->0x402;});
        assertEquals(p.timeZoneByte(),switch(p) {case R61A,R62A->1;case R61B,R62B->0;default->2;});
        assertEquals(switch(p) {case R61C,R62C,R63C->OptionalInt.of(1); case R61D,R62D,R63D->OptionalInt.of(0); default->OptionalInt.empty();},p.goodFutureConstraint());
        assertEquals(p.file(),r.file());assertEquals(p.payload().stage(),r.stage());
        assertEquals(p.fileSha256(),r.mmdSha());assertEquals(p.domain(),image.originalWordRam().domain());
        assertEquals(262144,image.fileBytes());assertEquals(0,image.fileBackedCopyBytes());assertEquals(0,image.unresolvedCopyBytes());
        assertEquals(p.fileSha256(),CdR11aProfile.sha256(image.readFile(0,262144)));
        for(var span:p.ranges()) {
            var bytes=r.view(span);assertSame(bytes,r.view(span));
            assertEquals(span.length(),bytes.length());assertEquals(span.sha256(),CdR11aProfile.sha256(bytes.copy()));
            assertArrayEquals(image.readFile(span.fileOffset(),span.length()),bytes.copy());
            var equal=new CdR6DataLayout.Range(span.key(),span.fileOffset(),span.length(),span.sha256());
            assertEquals(span,equal);assertThrows(IllegalArgumentException.class,()->r.view(equal));
        }
        assertEquals(p.payload(),r.definition().payload());assertTrue(r.definition().r3().isEmpty());assertTrue(r.definition().tt().isEmpty());
        assertEquals(p.terrain().blockBytes()/8,r.terrain().blockCount());assertThrows(UnsupportedOperationException.class,r.terrain()::shape);
        assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);
        assertThrows(IllegalArgumentException.class,()->CdProgrammeData.fromReader(r));
    }
    @ParameterizedTest @EnumSource(CdR6DataLayout.Programme.class)
    void all167PlayerFramesAndEveryLateArtReadMatchSourceContracts(CdR6DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var visual=r.sonic();var sig=new ByteArrayOutputStream();
        assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_167,visual.sourceFormat());
        assertEquals(167,visual.mappingFrames().size());assertEquals(167,visual.dplcFrames().size());
        assertEquals(57120,visual.rawArt().length());assertEquals(57120,visual.artReadView().length());
        assertEquals(p==CdR6DataLayout.Programme.R61A?2444:2443,r.view(p.range("DataMap")).length());
        if(p==CdR6DataLayout.Programme.R61A)assertEquals(0,r.view(p.range("DataMap")).unsignedByte(2443));
        for(int frame=0;frame<167;frame++) {
            var map=visual.mappingFrames().get(frame);var dplc=visual.dplcFrames().get(frame);
            word(sig,map.relativeOffset());word(sig,map.pieceCount());word(sig,dplc.relativeOffset());word(sig,dplc.pieces().size());word(sig,dplc.outputBytes());
            assertEquals(1+map.pieceCount()*5,map.bytes().length());assertTrue(dplc.outputBytes()<=768);
            for(int i=0;i<dplc.pieces().size();i++) {
                var piece=dplc.pieces().get(i);word(sig,piece.rawWord());
                assertArrayEquals(r.view(p.range("DataArt")).slice(piece.artByteOffset(),piece.byteLength()).copy(),visual.artPiece(frame,i).copy());
            }
        }
        assertEquals(playerSignature(p),CdR11aProfile.sha256(sig.toByteArray()));
        var ani=r.view(p.range("DataAnimation"));assertEquals(582,ani.length());
        for(int i=0;i<60;i++){int at=(short)ani.unsignedWord(i*2);assertTrue(at>=120&&at<582);}
        assertEquals(80,r.view(p.range("DataMini")).length());
        for(int n:List.of(166*2,2443+2)) {
            var bad=Arrays.copyOf(r.view(p.range("PlayerMapRead")).copy(),n);
            assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167,bad,r.view(p.range("DataDplc")).copy(),visual.rawArt().copy()));
        }
        assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167,r.view(p.range("DataMap")).copy(),r.view(p.range("DataDplc")).copy(),Arrays.copyOf(visual.rawArt().copy(),57121)));
    }
    private static void wordAt(byte[] bytes,int at,int word) {bytes[at]=(byte)(word>>>8);bytes[at+1]=(byte)word;}
    @ParameterizedTest @EnumSource(CdR6DataLayout.Programme.class)
    void everyPlcAliasBodySourceDestinationAndPaletteIsParsedByTheCommonOwner(CdR6DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var tables=r.loadTables();assertSame(tables,r.loadTables());
        assertEquals(p.name().startsWith("R63")?21:19,tables.plcs().size());var sig=new ByteArrayOutputStream();
        for(var slot:tables.plcs()) {
            word(sig,slot.nativeId());longword(sig,slot.body().fileOffset());word(sig,slot.body().entries().size());
            for(var entry:slot.body().entries()) {longword(sig,entry.sourceNativeAddress());word(sig,entry.vramAddress());assertTrue(entry.outputBytes()<=65536-entry.vramAddress());}
            for(var alias:tables.plcs())if(slot.body().fileOffset()==alias.body().fileOffset())assertSame(slot.body(),alias.body());
        }
        assertEquals(plcSignature(p),CdR11aProfile.sha256(sig.toByteArray()));
        assertEquals(p.name().endsWith("A")||p.name().endsWith("B")?6:7,tables.palettes().size());
        for(var palette:tables.palettes())assertEquals((palette.longwordCounter()+1)*4,palette.bytes().length());
        assertEquals(new CdProgrammeDefinition.InitQueueUpdateRts(2),tables.sectionProgramme());
        assertThrows(UnsupportedOperationException.class,tables::sections);assertThrows(UnsupportedOperationException.class,tables::backgroundPointers);
        assertEquals(4,tables.cycles().size());
        for(var cycle:tables.cycles()) {
            int count=cycle.script().unsignedByte(1);assertEquals(2+2*count,cycle.script().length());
            for(int i=0;i<count;i++) {int index=(byte)cycle.script().unsignedByte(3+i*2);assertTrue(index>=0&&index*2<cycle.colors().length());}
        }
        var bg=(CdProgrammeLoadTables.BackgroundTables)tables.backgroundProgramme();assertEquals(2,bg.tables().size());assertTrue(bg.rowSelectors().isPresent());
        if(tables.palettes().size()==7)assertEquals(CdProgrammeDefinition.CycleProgramme.FOUR_WITH_BOSS_COLOR_OVERRIDE,tables.cycleProgramme());
    }
    @ParameterizedTest @EnumSource(CdR6DataLayout.Programme.class)
    void allLayoutSlotsDispatchAndPlacementFieldsRemainOriginalData(CdR6DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var directory=r.view(p.range("DataLayoutDirectory"));assertEquals(72,directory.length());
        for(var slot:p.layouts()) {
            assertEquals(slot.signedOffset(),(short)directory.unsignedWord(slot.slot()*2));var bytes=r.view(p.range("LayoutRead"+slot.slot()));
            assertEquals(slot.columns(),bytes.unsignedByte(0)+1);assertEquals(slot.rows(),bytes.unsignedByte(1)+1);
            assertEquals(2+slot.columns()*slot.rows(),bytes.length());
            assertEquals(p.range("DataLayoutDirectory").fileOffset()+slot.signedOffset(),p.range("LayoutRead"+slot.slot()).fileOffset());
        }
        var raw=r.view(p.range("PlacementRead"));assertEquals(p.placementCount()*8+8,raw.length());
        assertEquals(65535,raw.unsignedWord(raw.length()-8));int active=0;
        for(int at=0;at<raw.length()-8;at+=8) {
            int id=raw.unsignedByte(at+4)&127;assertTrue(id>=1&&id<=p.dispatch().size());
            if((raw.unsignedByte(at+6)&(1<<(5+p.timeZoneByte())))!=0)active++;
        }
        assertEquals(p.periodActiveCount(),active);
        if(p.name().startsWith("R63")) {
            var fault=assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeRecords(raw.copy()));
            assertEquals("Placements are not in native X order",fault.getMessage());
            assertEquals(0xA08,raw.unsignedWord(64*8));assertEquals(0xA07,raw.unsignedWord(65*8));assertEquals(0xA06,raw.unsignedWord(66*8));
        } else {
            var placements=CdObjectPlacements.decodeRecords(raw.copy());assertEquals(p.placementCount(),placements.records().size());
            assertEquals(p.periodActiveCount(),placements.records().stream().filter(z->z.appliesTo(p.payload().stage().period())).count());
        }
        var dispatch=r.view(p.range("DataDispatch"));
        for(var row:p.dispatch())assertEquals(Integer.toUnsignedLong(row.address()),dispatch.unsignedLong((row.id()-1)*4));
        if(p==CdR6DataLayout.Programme.R63C) {
            var chunks=r.view(p.range("DataChunks"));
            assertEquals(0x600A2005L,chunks.unsignedLong(0xB946));assertEquals(0x4014000FL,chunks.unsignedLong(0xB966));assertEquals(0x601E600AL,chunks.unsignedLong(0xB986));
        }
    }
    private record NativeProduct(byte[] bytes,int prepared,int consumed) { }
    private static NativeProduct nativeProduct(byte[] input,long address,byte seed) {
        var table=new byte[512];Arrays.fill(table,seed);
        var state=CdNemesisPlcDecoder.prepare(CdAssetBytes.copyOf(input),address,CdAssetBytes.copyOf(table));int prepared=state.inputOffset();
        var out=new ByteArrayOutputStream();while(state.remainingTiles()>0) {var tile=CdNemesisPlcDecoder.decodeTile(state);out.writeBytes(tile.bytes().copy());state=tile.state();}
        return new NativeProduct(out.toByteArray(),prepared,state.inputOffset());
    }
    @ParameterizedTest @EnumSource(CdR6DataLayout.Programme.class)
    void everyCompressedProductHasSeparateCanonicalJavaAndNativeReadProofs(CdR6DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);int products=0;
        for(var source:r.definition().sources()) {
            products++;var key=(CdR6DataLayout.Range)source.key();var fact=CdR6ProgrammeProfiles.contract(p,key);
            var canonical=r.canonicalInput(key);var java=r.javaInput(key);var nativeBytes=r.nativeInput(key);
            assertEquals(key.length(),canonical.length());assertEquals(fact.javaProduct().input().length(),java.length());assertEquals(fact.nativeInput().length(),nativeBytes.length());
            assertEquals(fact.javaProduct().input().sha(),CdR11aProfile.sha256(java.copy()));assertEquals(fact.nativeInput().sha(),CdR11aProfile.sha256(nativeBytes.copy()));
            assertArrayEquals(canonical.copy(),java.slice(0,canonical.length()).copy());assertArrayEquals(canonical.copy(),nativeBytes.slice(0,canonical.length()).copy());
            var nativeResult=nativeProduct(nativeBytes.copy(),0x200000L+key.fileOffset(),(byte)0);
            var poison=nativeProduct(nativeBytes.copy(),0x200000L+key.fileOffset(),(byte)255);
            assertEquals(fact.nativePrepared(),nativeResult.prepared());assertEquals(fact.nativeConsumed(),nativeResult.consumed());
            assertArrayEquals(nativeResult.bytes(),poison.bytes());assertEquals(nativeResult.consumed(),poison.consumed());
            var decoded=r.decoded(key);assertSame(decoded,r.decoded(key));assertArrayEquals(nativeResult.bytes(),decoded.bytes().copy());
            assertEquals(fact.javaProduct().outputSha(),CdR11aProfile.sha256(decoded.bytes().copy()));assertEquals(fact.javaProduct().consumed(),decoded.consumedInputBytes());
            assertThrows(IOException.class,()->CdNemesisAsset.decode(Arrays.copyOf(java.copy(),fact.javaProduct().consumed()-1),source.output(),source.capacity()));
            var fault=assertThrows(CdNemesisPlcDecoder.DecodeException.class,()->nativeProduct(Arrays.copyOf(nativeBytes.copy(),fact.nativeConsumed()-1),0x200000L+key.fileOffset(),(byte)0));
            assertEquals(CdNemesisPlcDecoder.Reason.SOURCE_EXHAUSTED,fault.reason());
            var equal=new CdR6DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256());
            assertThrows(IllegalArgumentException.class,()->r.decoded(equal));assertThrows(IllegalArgumentException.class,()->CdR6ProgrammeProfiles.contract(p,equal));
        }
        assertEquals(productCount(p),products);
    }
    @Test void wrongOriginalImageRegionAndAbsentBossVariantsFailClosed()throws Exception {
        try(var source=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertThrows(IOException.class,()->CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.JAPAN),Optional.empty()));
            var p=CdR6DataLayout.Programme.R61A;var wrong=mock(CdProgramAssets.class,delegatesTo(source));
            doReturn(source.mmd(CdNativeFile.R61B)).when(wrong).mmd(p.file());assertThrows(IOException.class,()->CdProgrammeReader.open(wrong,p.payload().stage()));
            for(var absent:List.of("R63A","R63B"))assertThrows(IllegalArgumentException.class,()->CdStageKey.parse(absent));
        }
    }
    private static String playerSignature(CdR6DataLayout.Programme p) {
        return switch(p) {
            case R61A -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R61B -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R61C -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R61D -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R62A -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R62B -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R62C -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R62D -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R63C -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
            case R63D -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        };
    }
    private static String plcSignature(CdR6DataLayout.Programme p) {
        return switch(p) {
            case R61A -> "6ee67156f930f80785b5530810be3ed28865c6f934bf6a495f756a931842aab3";
            case R61B -> "896a769736e41ad1a1f86aa84488161af271cc560ff8103ae591f4dc0b16913c";
            case R61C -> "800898c03d6c8a1ac84769b6b5a75d7bbdcbc7d3d3e41d4d1316113ed9f7ca68";
            case R61D -> "f0951a4b14ad4dbf0932fac499618c98938bc7dfe807b034cf6ec49c61012623";
            case R62A -> "e0aae617b30e61a4010df7595c7c2c25a4d492d1a9d4d5ee3acd455a74d57750";
            case R62B -> "00e8dd6486f8e127325708be24b5c880a9ca8d9ef347c7e0a0662cad2b5ae8c2";
            case R62C -> "a661e2ad76f78dc69c113a7213611f956fef7dfb51ef84a33e24fb7ede233157";
            case R62D -> "e6d9c7f1a796261b10b15ad52eafa35d104ab18243d229d3f69ac490dde67f8b";
            case R63C -> "f0e3b771b6d49367d8bb8abd647f6d3ee469b1f50e5c5d4d593b64063c399c9c";
            case R63D -> "3c372f87ab2d60957c5920ed066154a19f1198dbc56bb265d9405512a38843c4";
        };
    }
    private static int productCount(CdR6DataLayout.Programme p) {
        return switch(p) {
            case R61A -> 36;
            case R61B -> 37;
            case R61C -> 35;
            case R61D -> 35;
            case R62A -> 38;
            case R62B -> 37;
            case R62C -> 36;
            case R62D -> 36;
            case R63C -> 35;
            case R63D -> 35;
        };
    }
}
