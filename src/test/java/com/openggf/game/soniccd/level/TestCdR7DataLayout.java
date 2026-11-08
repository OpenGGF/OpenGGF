package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Actual original-media DATA; no World/player/terrain/current-art admission. */
class TestCdR7DataLayout {
    record Fixture(Path cue,Map<CdR7DataLayout.Programme,CdProgrammeReader> readers,
                   Map<CdR7DataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    static synchronized Fixture actual()throws Exception {
        if(retained!=null)return retained;
        var root=System.getProperty("soniccd.media.root");assertNotNull(root,"Original media property required");
        var cue=Path.of(root).resolve("USA/Sonic CD (US).cue");assertTrue(Files.isRegularFile(cue));
        var readers=new EnumMap<CdR7DataLayout.Programme,CdProgrammeReader>(CdR7DataLayout.Programme.class);
        var images=new EnumMap<CdR7DataLayout.Programme,CdMmdImage>(CdR7DataLayout.Programme.class);
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertTrue(source.hasExpectedTrackShape());
            for(var p:CdR7DataLayout.Programme.values()) {
                assertEquals(p.isoLba(),source.entry(p.file()).extentBlock());assertEquals(p.fileBytes(),source.entry(p.file()).size());
                readers.put(p,CdProgrammeReader.open(source,p.payload().stage()));images.put(p,source.mmd(p.file()));
            }
        }
        retained=new Fixture(cue,Map.copyOf(readers),Map.copyOf(images));return retained;
    }
    static void word(ByteArrayOutputStream out,int n) {out.write(n>>>8);out.write(n);}
    static void longword(ByteArrayOutputStream out,long n) {word(out,(int)(n>>>16));word(out,(int)n);}
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void originalIdentitiesAndAllCanonicalReadRangesUseTheCommonReader(CdR7DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var image=actual().images().get(p);
        assertEquals(p.file(),r.file());assertEquals(p.payload().stage(),r.stage());assertEquals(p.fileSha256(),r.mmdSha());
        assertEquals(p.fileSha256(),CdR11aProfile.sha256(image.readFile(0,p.fileBytes())));
        assertEquals(p.domain(),image.originalWordRam().domain());assertEquals(0,image.fileBackedCopyBytes());assertEquals(0,image.unresolvedCopyBytes());
        assertEquals(p.rawZoneActWord(),switch(p){case R71A,R71B,R71C,R71D->0x500;case R72A,R72B,R72C,R72D->0x501;case R73C,R73D->0x502;});
        assertEquals(p.timeZoneByte(),switch(p){case R71A,R72A->1;case R71B,R72B->0;default->2;});
        var goodFuture=p.goodFutureSelectionConstraint();
        switch(p) {
            case R71A,R71B,R72A,R72B -> assertTrue(goodFuture.isEmpty());
            case R71C,R72C,R73C -> assertEquals(1,goodFuture.orElseThrow());
            case R71D,R72D,R73D -> assertEquals(0,goodFuture.orElseThrow());
        }
        assertTrue(r.definition().dataOnly());
        for(var span:p.ranges()) {
            var bytes=r.view(span);assertSame(bytes,r.view(span));assertEquals(span.length(),bytes.length());
            assertEquals(span.sha256(),CdR11aProfile.sha256(bytes.copy()));assertArrayEquals(image.readFile(span.fileOffset(),span.length()),bytes.copy());
            var equal=new CdR7DataLayout.Range(span.key(),span.fileOffset(),span.length(),span.sha256());assertEquals(span,equal);
            assertThrows(IllegalArgumentException.class,()->r.view(equal));
        }
        for(var operand:p.operands())assertEquals(operand.hex(),HexFormat.of().formatHex(image.readFile(operand.fileOffset(),operand.hex().length()/2)));
        assertThrows(UnsupportedOperationException.class,r::terrain);assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);
        assertThrows(IllegalArgumentException.class,()->CdProgrammeData.fromReader(r));
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void all167PlayerFramesAndLateArtReadsKeepTheActual578ByteAnimationFormat(CdR7DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var visual=r.sonic();var signature=new ByteArrayOutputStream();
        assertEquals(p.sonicFormat(),visual.sourceFormat());assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_167,visual.sourceFormat());
        assertEquals(2443,visual.mappings().length());assertEquals(1338,visual.dplcs().length());assertEquals(57120,visual.rawArt().length());assertEquals(57120,visual.artReadView().length());
        assertEquals(167,visual.mappingFrames().size());assertEquals(167,visual.dplcFrames().size());
        for(int frame=0;frame<167;frame++) {
            var map=visual.mappingFrames().get(frame);var dplc=visual.dplcFrames().get(frame);
            word(signature,map.relativeOffset());word(signature,map.pieceCount());word(signature,dplc.relativeOffset());word(signature,dplc.pieces().size());word(signature,dplc.outputBytes());
            assertEquals(1+map.pieceCount()*5,map.bytes().length());assertTrue(dplc.outputBytes()<=768);
            for(int i=0;i<dplc.pieces().size();i++) {
                var piece=dplc.pieces().get(i);word(signature,piece.rawWord());
                assertArrayEquals(r.view(p.range("PlayerGfx")).slice(piece.artByteOffset(),piece.byteLength()).copy(),visual.artPiece(frame,i).copy());
            }
        }
        assertEquals(playerSignature(p),CdR11aProfile.sha256(signature.toByteArray()));
        var ani=r.view(p.range("PlayerAnims"));assertEquals(578,ani.length());
        for(int i=0;i<60;i++) {int at=(short)ani.unsignedWord(i*2);assertTrue(at>=120&&at<578);}
        assertEquals(80,r.view(p.range("MiniAnimations")).length());
        for(int extent:List.of(166*2,2445))assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(p.sonicFormat(),Arrays.copyOf(visual.mappings().copy(),extent),visual.dplcs().copy(),visual.rawArt().copy()));
        assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188,visual.mappings().copy(),visual.dplcs().copy(),visual.rawArt().copy()));
        assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(p.sonicFormat(),visual.mappings().copy(),visual.dplcs().copy(),Arrays.copyOf(visual.rawArt().copy(),57121)));
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void all190PlcSlotsKeepOriginalSignedAliasesBodiesSourcesAndDestinations(CdR7DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var directory=r.view(p.range("PlcDirectory"));var hull=r.loadView(CdProgrammeReader.LoadRole.PLC);var signature=new ByteArrayOutputStream();
        assertEquals(38,directory.length());
        for(int slot=0;slot<19;slot++) {
            int at=(short)directory.unsignedWord(slot*2);assertTrue(at>=38);int count=(short)hull.unsignedWord(at)+1;
            assertTrue(count>0&&count<=15);word(signature,slot);longword(signature,p.range("PlcRead").fileOffset()+at);word(signature,count);
            for(int i=0;i<count;i++) {
                long address=hull.unsignedLong(at+2+i*6);int destination=hull.unsignedWord(at+6+i*6);var source=r.loadSource(address);
                assertEquals(address,0x200000L+r.fileOffset(source));assertTrue(r.loadOutputBytes(source)<=65536-destination);
                longword(signature,address);word(signature,destination);
            }
        }
        assertEquals(plcSignature(p),CdR11aProfile.sha256(signature.toByteArray()));
        if(CdR7ProgrammeProfiles.loadBoundary(p).isEmpty()) {
            var tables=r.loadTables();assertSame(tables,r.loadTables());assertEquals(19,tables.plcs().size());
            for(var slot:tables.plcs())for(var alias:tables.plcs())if(slot.body().fileOffset()==alias.body().fileOffset())assertSame(slot.body(),alias.body());
        }
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void commonLoadParserAdmitsSixExactFormatsAndRejectsFourNamedBoundaries(CdR7DataLayout.Programme p)throws Exception {
        // Historical selector retained: the four named gates now parse immutable DATA.
        var r=actual().readers().get(p);assertTrue(CdR7ProgrammeProfiles.loadBoundary(p).isEmpty());
        var tables=r.loadTables();assertEquals(p.palettes().size(),tables.palettes().size());
        for(var palette:tables.palettes()) {
            if(palette.sourceBytes().isPresent())assertEquals((palette.longwordCounter()+1L)*4,palette.bytes().length());
            else assertThrows(UnsupportedOperationException.class,palette::bytes);
        }
        boolean mirror=List.of(CdR7DataLayout.Programme.R71A,CdR7DataLayout.Programme.R72A).contains(p),gated=p.name().endsWith("D");
        assertEquals(gated?CdProgrammeDefinition.CycleProgramme.SOURCE_ACT_GATED:mirror?CdProgrammeDefinition.CycleProgramme.THREE_MIRRORED:CdProgrammeDefinition.CycleProgramme.SOURCE_RTS,tables.cycleProgramme());
        assertEquals(gated?7:mirror?3:0,tables.cycles().size());
        for(var cycle:tables.cycles()) {
            int count=cycle.script().unsignedByte(1);assertEquals(2+count*2,cycle.script().length());
            if(mirror)assertEquals(count*4,cycle.colors().length()); // D retains canonical DATA, not a live read-hull promise.
        }
        boolean camera=List.of(CdR7DataLayout.Programme.R71A,CdR7DataLayout.Programme.R71B,CdR7DataLayout.Programme.R72A,CdR7DataLayout.Programme.R72B).contains(p);
        if(camera)assertEquals(2,((CdProgrammeDefinition.CameraSections)tables.sectionProgramme()).rows().size());
        else if(p.name().startsWith("R73"))assertSame(CdProgrammeDefinition.NoSections.INSTANCE,tables.sectionProgramme());
        else assertEquals(new CdProgrammeDefinition.InitQueueUpdateRts(2),tables.sectionProgramme());
        var bg=(CdProgrammeLoadTables.BackgroundTables)tables.backgroundProgramme();assertEquals(2,bg.tables().size());assertTrue(bg.rowSelectors().isPresent());
        assertThrows(UnsupportedOperationException.class,tables::sections);assertThrows(UnsupportedOperationException.class,tables::backgroundPointers);
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void nullPaletteRecordsNeverBecomeAnEmptyMappedPalette(CdR7DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var bytes=r.view(p.range("PaletteDirectory"));
        for(var row:p.palettes()) {
            assertEquals(row.sourceAddress(),bytes.unsignedLong(row.slot()*8));assertEquals(row.destination(),bytes.unsignedWord(row.slot()*8+4));assertEquals(row.counter(),bytes.unsignedWord(row.slot()*8+6));
            assertEquals((row.counter()+1L)*4,row.declaredReadBytes());
            if(row.unmapped()) {
                assertEquals(5,row.slot());assertEquals(0,row.sourceAddress());assertEquals(0,row.destination());assertEquals(65535,row.counter());assertEquals(262144,row.declaredReadBytes());
                assertThrows(IllegalArgumentException.class,()->p.range("PaletteRead5"));assertTrue(CdR7ProgrammeProfiles.loadBoundary(p).isEmpty());
                var parsed=r.loadTables().palettes().get(5);assertEquals(5,parsed.nativeId());assertTrue(parsed.sourceBytes().isEmpty());assertThrows(UnsupportedOperationException.class,parsed::bytes);
            } else assertEquals(row.declaredReadBytes(),r.view(p.range("PaletteRead"+row.slot())).length());
        }
    }
    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void completePlacementOrderingDispatchAndAll36LayoutSlotsStayNativeData(CdR7DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var bytes=r.view(p.range("PlacementRead"));var signature=new ByteArrayOutputStream();
        assertEquals(p.placementCount()*8+8,bytes.length());assertEquals(65535,bytes.unsignedWord(bytes.length()-8));int active=0,previous=-1;
        for(int i=0;i<p.placementCount();i++) {
            int at=i*8,x=bytes.unsignedWord(at),packedY=bytes.unsignedWord(at+2),id=bytes.unsignedByte(at+4),sub=bytes.unsignedByte(at+5),mask=bytes.unsignedByte(at+6),sub2=bytes.unsignedByte(at+7);
            assertTrue(x>=previous);previous=x;assertTrue((id&127)>=1&&(id&127)<=60);
            word(signature,x);word(signature,packedY);signature.write(id);signature.write(sub);signature.write(mask);signature.write(sub2);
            if((mask&(0x20<<p.timeZoneByte()))!=0)active++;
        }
        assertEquals(placementSignature(p),CdR11aProfile.sha256(signature.toByteArray()));assertEquals(p.periodActiveCount(),active);
        if(p.placementCount()>497)assertThrows(IllegalArgumentException.class,()->CdObjectPlacements.decodeRecords(bytes.copy()));
        else {
            assertEquals(0,bytes.unsignedByte(30*8+6));
            assertEquals("Unverified placement period encoding",assertThrows(UnsupportedOperationException.class,()->CdObjectPlacements.decodeRecords(bytes.copy())).getMessage());
        }
        var dispatch=r.view(p.range("Dispatch"));assertEquals(60,p.dispatch().size());
        for(var row:p.dispatch())assertEquals(Integer.toUnsignedLong(row.address()),dispatch.unsignedLong((row.id()-1)*4));
        var layouts=r.view(p.range("LayoutDirectory"));assertEquals(72,layouts.length());
        for(var row:p.layouts()) {
            assertEquals(row.signedOffset(),(short)layouts.unsignedWord(row.slot()*2));var read=r.view(p.range("LayoutRead"+row.slot()));
            assertEquals(row.columns(),read.unsignedByte(0)+1);assertEquals(row.rows(),read.unsignedByte(1)+1);assertEquals(2+row.columns()*row.rows(),read.length());
            assertEquals(p.range("LayoutDirectory").fileOffset()+row.signedOffset(),p.range("LayoutRead"+row.slot()).fileOffset());
        }
    }
    @Test void presentAndPastIdentityNeverSupplyARetainedGoodFutureValue() {
        for(var p:List.of(CdR7DataLayout.Programme.R71A,CdR7DataLayout.Programme.R71B,CdR7DataLayout.Programme.R72A,CdR7DataLayout.Programme.R72B))
            assertTrue(p.goodFutureSelectionConstraint().isEmpty());
    }
    @Test void wrongImageRegionAndAbsentBossPeriodsFailClosed()throws Exception {
        try(var source=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertThrows(IOException.class,()->CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.JAPAN),Optional.empty()));
            var wrong=mock(CdProgramAssets.class,delegatesTo(source));doReturn(source.mmd(CdNativeFile.R71B)).when(wrong).mmd(CdNativeFile.R71A);
            assertThrows(IOException.class,()->CdProgrammeReader.open(wrong,CdStagePayload.R71A.stage()));
            for(var absent:List.of("R73A","R73B"))assertThrows(IllegalArgumentException.class,()->CdStageKey.parse(absent));
        }
    }
    private static String playerSignature(CdR7DataLayout.Programme p) { return switch(p) {
        case R71A -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R71B -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R71C -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R71D -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R72A -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R72B -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R72C -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R72D -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R73C -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
        case R73D -> "3398d4ff663fba4e5446753f9255129cf094d48ce6b7a7c0315429f24a7f2855";
    }; }
    private static String plcSignature(CdR7DataLayout.Programme p) { return switch(p) {
        case R71A -> "349e255aef9c808c4d04894617a1609c356ed0db7b3b92420dc0b7f06525074d";
        case R71B -> "a84197d9191cf4a4c35422fc487cdbed4938a99a1f41e0d8a2726feacd9985a1";
        case R71C -> "c85440b1afd90a279871b8fda8b9ee69aa03c7b4a70189fc4c003041e65272e0";
        case R71D -> "4c5856fe51922f515d2e11205c9d44a066adcaa138493272846aa51d417ac0ea";
        case R72A -> "2607f227578c06076f2ed77dac8fd9db1f19aded7957315b9c3cc95b5f73d98f";
        case R72B -> "647a6904bb6e69137ce71b9d5e17b652009cf708e02e14bec3d18ce8730a402b";
        case R72C -> "e19cab0815dcb0e8fbf8f7f00e6b259661440236398811a9a9b06a35f3d9f0d8";
        case R72D -> "91f94927a60e99c9a46a2562436e199ae35a6d026a30848877372fb0bbfa6d59";
        case R73C -> "8c99bf22a8824d25632dfaa05239de4b385775ebf3ca4679ae65c61805189abe";
        case R73D -> "b49a34573c5d7d925d44e11a9d6b251e6c233c932e63836cf5e2c60f8e402e66";
    }; }
    private static String placementSignature(CdR7DataLayout.Programme p) { return switch(p) {
        case R71A -> "4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06";
        case R71B -> "4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06";
        case R71C -> "4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06";
        case R71D -> "4b611fb0fd1c0b2ec242d42456ba8e0179bf5d124f2562d40dc649403564ea06";
        case R72A -> "2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b";
        case R72B -> "2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b";
        case R72C -> "2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b";
        case R72D -> "2bf502d8b868952b748e48650c7680dd8efd27dde63d29f30ba51cec317d763b";
        case R73C -> "ecbd942234a20a891332e8ec9e13078851a77ad251b53ab73304d1c129d5c0f5";
        case R73D -> "ecbd942234a20a891332e8ec9e13078851a77ad251b53ab73304d1c129d5c0f5";
    }; }
}
