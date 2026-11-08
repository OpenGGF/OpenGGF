package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Actual original-media DATA only. No runtime, native execution, routes or pixel claims. */
class TestCdR5DataLayout {
    record Fixture(Path cue,Map<CdR5DataLayout.Programme,CdProgrammeReader> readers,
                   Map<CdR5DataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    static synchronized Fixture actual() throws Exception {
        if(retained!=null)return retained;
        String root=System.getProperty("soniccd.media.root");
        assertNotNull(root,"R5 actual-media checks require explicit original media");assertTrue(Path.of(root).isAbsolute());
        Path cue;try(var files=Files.list(Path.of(root).resolve("USA"))) {
            var cues=files.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        var readers=new EnumMap<CdR5DataLayout.Programme,CdProgrammeReader>(CdR5DataLayout.Programme.class);
        var images=new EnumMap<CdR5DataLayout.Programme,CdMmdImage>(CdR5DataLayout.Programme.class);
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var p:CdR5DataLayout.Programme.values()) {readers.put(p,CdProgrammeReader.open(source,p.payload().stage()));images.put(p,source.mmd(p.file()));}
        }
        return retained=new Fixture(cue,Map.copyOf(readers),Map.copyOf(images));
    }
    @ParameterizedTest @EnumSource(CdR5DataLayout.Programme.class)
    void allTenWholeImagesOperandsAndCanonicalOrReadSpansBelongToActualOriginals(CdR5DataLayout.Programme p)throws Exception {
        var f=actual();var r=f.readers().get(p);var image=f.images().get(p);
        assertEquals(p.payload(),r.definition().payload());assertTrue(r.definition().r3().isEmpty());assertTrue(r.definition().tt().isEmpty());
        assertEquals(p.file(),r.file());assertEquals(p.domain(),image.originalWordRam().domain());
        assertEquals(p.fileSha256(),CdR11aProfile.sha256(image.readFile(0,262144)));assertEquals(p.fileSha256(),r.mmdSha());
        assertEquals("a6b026a2381f926485cebb2da0bded29319cfd515ef7956a3ca79b18645bdb2b",r.discSha());
        int[] jumps={p.startAddress(),p.hblankAddress(),p.vblankAddress()};
        int[] cuts={0x100,0x10C,0x112};
        for(int i=0;i<3;i++)assertEquals(jumps[i],CdAssetBytes.copyOf(image.readFile(cuts[i]+2,4)).unsignedLong(0));
        for(var key:p.ranges()) {
            var view=r.view(key);assertSame(view,r.view(key));assertEquals(key.length(),view.length());
            assertEquals(key.sha256(),CdR11aProfile.sha256(view.copy()),p+":"+key.key());
            assertArrayEquals(image.readFile(key.fileOffset(),key.length()),view.copy());
        }
        for(var op:p.operands())assertEquals(op.hex(),HexFormat.of().formatHex(image.readFile(op.fileOffset(),op.hex().length()/2)),op.target());
        assertNotNull(r.terrain());
        assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);
        if(p.rawTimeZone()!=2)assertTrue(p.goodFutureConstraint().isEmpty());
    }
    @ParameterizedTest @EnumSource(CdR5DataLayout.Programme.class)
    void commonTableProductsOrExplicitUnequalAct3SectionBoundary(CdR5DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);
        // The original case identity is retained; the former R53 format boundary now yields real products.
        var tables=r.loadTables();assertSame(tables,r.loadTables());
        var raw=r.view(p.range("GfxLists"));assertEquals(19,tables.plcs().size());
        var bodies=new HashMap<Integer,CdProgrammeLoadTables.Body>();var encountered=new LinkedHashSet<Object>();
        for(int i=0;i<19;i++) {
            int at=(short)raw.unsignedWord(i*2);var body=tables.plcs().get(i).body();
            assertEquals(p.range("GfxLists").fileOffset()+at,body.fileOffset());int count=(short)raw.unsignedWord(at)+1;
            assertEquals(count,body.entries().size());
            var previous=bodies.putIfAbsent(at,body);if(previous!=null)assertSame(previous,body);
            for(int j=0;j<count;j++) {
                var e=body.entries().get(j);assertEquals(raw.unsignedLong(at+2+j*6),e.sourceNativeAddress());
                assertEquals(raw.unsignedWord(at+6+j*6),e.vramAddress());
                var key=(CdR5DataLayout.Range)e.source();assertSame(p.range(key.key()),key);
                assertEquals(0x200000L+key.fileOffset(),e.sourceNativeAddress());assertEquals(r.loadOutputBytes(key),e.outputBytes());
                encountered.add(key);
            }
        }
        assertEquals(List.copyOf(encountered),tables.encounteredSources());assertSame(tables.plcs().get(0).body(),tables.plcs().get(3).body());
        assertEquals(p.rawTimeZone()==2?7:6,tables.palettes().size());
        for(var palette:tables.palettes()) {
            assertEquals((palette.longwordCounter()+1)*4,palette.bytes().length());
            assertSame(r.view(p.range("Palette"+palette.nativeId())),palette.bytes());
        }
        var sections=(CdProgrammeDefinition.CameraSections)tables.sectionProgramme();
        assertEquals(p.payload().stage().actNumber()==1?3:2,sections.rows().size());
        for(var cycle:tables.cycles()) {
            assertEquals(CdProgrammeDefinition.CycleKind.BYTE_HEADER_4,cycle.kind());
            assertEquals(4,cycle.script().length());assertEquals(cycle.script().unsignedByte(1)*cycle.script().unsignedByte(2)*2,cycle.colors().length());
        }
        var bg=(CdProgrammeLoadTables.BackgroundTables)tables.backgroundProgramme();
        assertEquals(List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718),bg.tables().getFirst().values());
        assertSame(r.view(p.range("BackgroundRows")),bg.rowSelectors().orElseThrow());
    }
    @ParameterizedTest @EnumSource(CdR5DataLayout.Programme.class)
    void everySignedMapBlockAndPathSlotHasItsExactFiniteReadHull(CdR5DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var directory=r.view(p.range("StageMapsDirectory"));assertEquals(36,p.layouts().size());
        for(var layout:p.layouts()) {
            var key=p.range(layout.readKey());var bytes=r.view(key);assertEquals(layout.signedOffset(),(short)directory.unsignedWord(layout.slot()*2));
            assertEquals(p.range("StageMapsDirectory").fileOffset()+layout.signedOffset(),key.fileOffset());
            assertEquals(layout.columns(),bytes.unsignedByte(0)+1);assertEquals(layout.rows(),bytes.unsignedByte(1)+1);
            assertEquals(2+layout.columns()*layout.rows(),bytes.length());int maxChunk=0,maxRender=0,maxCollision=0;
            var chunks=layout.chunkHull().map(p::range).map(r::view);
            for(int i=2;i<bytes.length();i++) {
                var cell=new CdStageTerrain.LayoutCell(bytes.unsignedByte(i));maxChunk=Math.max(maxChunk,cell.chunkId());
                if(cell.blank())continue;
                for(int j=0;j<512;j+=2) {
                    var block=new CdStageTerrain.BlockReference(chunks.orElseThrow().unsignedWord((cell.chunkId()-1)*512+j));
                    maxRender=Math.max(maxRender,block.renderBlockIndex());maxCollision=Math.max(maxCollision,block.collisionBlockIndex());
                }
            }
            assertEquals(layout.maxRenderBlock(),maxRender);assertEquals(layout.maxCollisionBlock(),maxCollision);
            if(maxChunk!=0) {assertEquals(maxChunk*512,chunks.orElseThrow().length());assertEquals(maxCollision+1,r.view(p.range(layout.collisionHull().orElseThrow())).length());}
        }
        assertEquals(4,p.playerBlocks().size());var bd=r.view(p.range("PlayerBlocksDirectory"));
        for(var b:p.playerBlocks()) {assertEquals(b.signedOffset(),(short)bd.unsignedWord(b.slot()*2));var bytes=r.view(p.range("PlayerBlocks"+b.slot()+"Read"));assertEquals(b.count(),bytes.unsignedWord(0)+1);assertEquals(2+b.count()*2,bytes.length());}
        var spawn=r.view(p.range("StagePlayerSpawnRead"));assertEquals(4,spawn.length());assertEquals(0x40,spawn.unsignedWord(0));
        assertEquals(p.payload().stage().actNumber()==2?0x16C:0x26C,spawn.unsignedWord(2));assertEquals(80,r.view(p.range("PlayerShrunkAnimationMapRead")).length());
        assertEquals(22,p.tunnelPaths().size());var td=r.view(p.range("TunnelPathsDirectory"));
        for(var t:p.tunnelPaths()) {assertEquals(t.signedOffset(),(short)td.unsignedWord(t.slot()*2));var bytes=r.view(p.range("TunnelPaths"+t.slot()+"Read"));assertEquals(t.controlWord(),bytes.unsignedWord(0));assertEquals(2+(t.controlWord()&255),bytes.length());assertEquals(0,(bytes.length()-2)%4);}
    }
    @ParameterizedTest @EnumSource(CdR5DataLayout.Programme.class)
    void allPlacementsAndNativeNullOrRemappedDispatchRemainProgrammeLocal(CdR5DataLayout.Programme p)throws Exception {
        var r=actual().readers().get(p);var index=r.view(p.range("ObjectIndex"));assertEquals(60,p.dispatch().size());
        assertArrayEquals(HexFormat.of().parseHex("10bc00004e75"),r.view(p.range("NullObject")).copy());
        for(var d:p.dispatch())assertEquals(d.nativeAddress(),index.unsignedLong((d.id()-1)*4));
        for(var root:p.placementRoots()) {
            var data=CdObjectPlacements.decodeRecords(r.view(p.range("PlacementRoot"+root.slot())).copy());assertEquals(root.count(),data.records().size());
            assertEquals(root.count()*8+2,r.view(p.range("PlacementRoot"+root.slot()+"Read")).length());
            if(root.slot()==0)assertEquals(p.activePlacementCount(),data.forPeriod(p.payload().stage().period()).size());
            for(var row:data.records()) {assertTrue(row.objectId()<=60);assertEquals(row.objectId(),p.dispatch().get(row.objectId()-1).id());}
        }
        if(p.payload().stage().actNumber()==3) {assertEquals("SpikeWallObject",p.dispatch().get(32).handler());assertEquals("NullObject",p.dispatch().get(35).handler());}
        else {assertEquals("SasoriObject",p.dispatch().get(32).handler());assertEquals("BlockObject",p.dispatch().get(35).handler());}
    }
    @Test void distinctOrEqualPublicKeysAndWrongActualImagesNeverAcquireSourceAuthority()throws Exception {
        var p=CdR5DataLayout.Programme.R51A;var r=actual().readers().get(p);var key=p.range("StageGfx");
        var equal=new CdR5DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256(),key.nativePath(),key.line());assertEquals(key,equal);
        for(var bad:List.of(equal,CdR5DataLayout.Programme.R51B.range("StageGfx"),CdR4DataLayout.Programme.R41A.range("StageGfx")))assertThrows(IllegalArgumentException.class,()->r.view(bad));
        try(var source=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var wrong=mock(CdProgramAssets.class,delegatesTo(source));doReturn(source.mmd(CdNativeFile.R51B)).when(wrong).mmd(CdNativeFile.R51A);
            assertThrows(java.io.IOException.class,()->CdProgrammeReader.open(wrong,p.payload().stage()));
            var changed=spy(source.mmd(CdNativeFile.R51A));byte[] bytes=changed.readFile(0,262144);bytes[262143]^=1;
            doReturn(bytes).when(changed).readFile(0,262144);var fault=mock(CdProgramAssets.class,delegatesTo(source));doReturn(changed).when(fault).mmd(CdNativeFile.R51A);
            assertThrows(java.io.IOException.class,()->CdProgrammeReader.open(fault,p.payload().stage()));
        }
    }
    @Test void includesReadHullsAndUnqualifiedTerrainDoNotCollapseIntoPaddedAssets()throws Exception {
        var p=CdR5DataLayout.Programme.R51A;assertEquals(178,p.range("StageMapFg").length());assertEquals(222,p.range("StageMaps0Read").length());
        assertEquals(800,p.range("StageCollision").length());assertTrue(CdR5DataLayout.Programme.R53C.range("Map0Collision").length()>800);
        for(var programme:CdR5DataLayout.Programme.values()) {
            var r=actual().readers().get(programme);assertNotNull(r.terrain());
            var key=programme.range("StageGfx");assertThrows(IllegalArgumentException.class,()->r.nativeInput(programme.range("PlayerGfx")));
            assertEquals(programme.range("StageMaps0Read").length(),r.role(CdProgrammeReader.Role.FG).length());assertNotNull(r.decoded(key));
        }
    }
    @Test void everyProgrammeRejectsForeignRegionCatalogueAndOriginalDomainBeforeAdoption()throws Exception {
        try(var source=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var p:CdR5DataLayout.Programme.values()) {
                var region=mock(CdProgramAssets.class,delegatesTo(source));var header=mock(CdDiscHeader.class);
                when(header.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.JAPAN));doReturn(header).when(region).header();
                assertThrows(java.io.IOException.class,()->CdProgrammeReader.open(region,p.payload().stage()));verify(region,never()).mmd(any());
                var foreign=mock(CdProgramAssets.class,delegatesTo(source));var catalogue=mock(CdProgramCatalogue.class);
                when(catalogue.release()).thenReturn(CdReleaseVariant.USA);when(catalogue.stage(p.payload().stage())).thenReturn(CdStagePayload.R31A);
                doReturn(Optional.of(catalogue)).when(foreign).catalogue();
                assertThrows(java.io.IOException.class,()->CdProgrammeReader.open(foreign,p.payload().stage()));verify(foreign,never()).mmd(any());
                var image=spy(source.mmd(p.file()));var original=image.originalWordRam();
                doReturn(new CdMmdImage.NativeRange(CdMmdImage.Domain.RELOCATED_COPY,original.address(),original.length(),original.fileSlice())).when(image).originalWordRam();
                var bad=mock(CdProgramAssets.class,delegatesTo(source));doReturn(image).when(bad).mmd(p.file());
                assertThrows(java.io.IOException.class,()->CdProgrammeReader.open(bad,p.payload().stage()));
            }
        }
    }
}
