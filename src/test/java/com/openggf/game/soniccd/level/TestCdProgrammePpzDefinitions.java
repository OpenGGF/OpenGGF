package com.openggf.game.soniccd.level;

// SOURCE-proposed inventory only. All Java UNRUN; Root owns import and queued validation.
import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestCdProgrammePpzDefinitions {
    private record Expected(int ranges,int sources,int bodies,int maximum,int encountered,int palettes,
                            int scripts,int direct,int rows,int past,int present,int future,int descending) { }
    private static final List<Expected> EXPECTED=List.of(
        new Expected(77,36,14,14,35,6,6,0,497,270,227,283,0),
        new Expected(78,37,14,15,36,6,6,0,497,270,227,283,0),
        new Expected(66,35,12,15,34,6,0,2,497,270,227,283,0),
        new Expected(68,35,12,15,34,6,1,2,497,270,227,283,0),
        new Expected(77,36,14,14,35,6,6,0,488,281,279,282,0),
        new Expected(80,39,16,14,37,6,6,0,488,281,279,282,0),
        new Expected(68,37,16,15,35,6,0,2,488,281,279,282,0),
        new Expected(69,36,16,15,34,6,1,2,488,281,279,282,0),
        new Expected(51,21,5,10,19,6,0,2,61,2,2,61,1),
        new Expected(53,21,5,10,19,6,1,2,61,2,2,61,1));
    private record Fixture(Map<CdPpzDataLayout.Programme,CdProgrammeReader> readers,
                           Map<CdPpzDataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained!=null)return retained;
        String root=System.getProperty("soniccd.media.root");
        assumeTrue(root!=null&&!root.isBlank(),"Optional original Sonic CD media absent");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))) {
            var cues=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var programmes=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertSame(CdRetailReleases.USA,CdRetailReleases.find(programmes.identity()).orElseThrow());
            var readers=new EnumMap<CdPpzDataLayout.Programme,CdProgrammeReader>(CdPpzDataLayout.Programme.class);
            var images=new EnumMap<CdPpzDataLayout.Programme,CdMmdImage>(CdPpzDataLayout.Programme.class);
            for(var p:CdPpzDataLayout.Programme.values()) {
                readers.put(p,CdProgrammeReader.open(programmes,p.payload().stage()));images.put(p,programmes.mmd(p.file()));
            }
            retained=new Fixture(Map.copyOf(readers),Map.copyOf(images));
        }
        return retained;
    }
    private static CdProgrammeReader reader(CdPpzDataLayout.Programme p)throws Exception { return actual().readers().get(p); }

    @Test void tenOriginalIdentitiesAreDistinctClosedDataAndEveryOldRegistrationIsRetained() {
        assertEquals(10,CdPpzDataLayout.Programme.values().length);
        var hashes=new HashSet<String>();var stages=new HashSet<CdStageKey>();
        for(var p:CdPpzDataLayout.Programme.values()) {
            assertTrue(hashes.add(p.fileSha256()));assertTrue(stages.add(p.payload().stage()));
            var d=CdProgrammeDefinition.select(p.payload().stage());
            assertSame(p.payload(),d.payload());assertSame(p.file(),d.file());assertEquals(262144,d.fileBytes());
            assertEquals(p.fileSha256(),d.sha());assertTrue(d.dataOnly());
            if(p==CdPpzDataLayout.Programme.R11A||p==CdPpzDataLayout.Programme.R11B)assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,d.player().orElseThrow().graph());
            else assertTrue(d.player().isEmpty());
            assertTrue(d.r3().isEmpty());assertTrue(d.tt().isEmpty());assertEquals(p.ranges().size(),d.raw().size());
        }
        // Preserve each previously registered programme, not just a total count.
        assertOld(CdStagePayload.R31A,"30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e");
        assertOld(CdStagePayload.R32A,CdR32aDataLayout.MMD_SHA256);
        assertNotNull(CdProgrammeDefinition.select(CdStagePayload.R32A.stage()).r3().orElseThrow());
        for(var p:CdR4DataLayout.Programme.values())assertOld(p.payload(),p.fileSha256());
        for(var p:CdR5DataLayout.Programme.values())assertOld(p.payload(),p.fileSha256());
        for(var p:CdR6DataLayout.Programme.values())assertOld(p.payload(),p.fileSha256());
        for(var p:CdR7DataLayout.Programme.values())assertOld(p.payload(),p.fileSha256());
        for(var p:CdR8DataLayout.Programme.values())assertOld(p.payload(),p.fileSha256());
        for(var p:CdCczDataLayout.Programme.values())assertOld(p.payload(),p.fileSha256());
    }
    private static void assertOld(CdStagePayload payload,String sha) {
        var selected=CdProgrammeDefinition.select(payload.stage());assertSame(payload,selected.payload());assertEquals(sha,selected.sha());
        assertTrue(selected.directCycles().isEmpty());
    }

    @ParameterizedTest @EnumSource(CdPpzDataLayout.Programme.class)
    void actualIdentityRangesOperandsAndForeignKeyRejection(CdPpzDataLayout.Programme p)throws Exception {
        var r=reader(p);var data=CdPpzProgrammeData.fromReader(r);var image=actual().images().get(p);
        assertTrue(data.ownsReader(r));assertFalse(data.ownsReader(null));assertSame(p,data.programme());
        assertEquals(p.fileSha256(),data.mmdSha256());assertEquals(p.fileBytes(),image.fileBytes());
        assertEquals(EXPECTED.get(p.ordinal()).ranges(),p.ranges().size());
        for(var range:p.ranges()) {
            var bytes=data.view(range);assertSame(bytes,data.view(range));assertEquals(range.length(),bytes.length());
            assertEquals(range.sha256(),CdR11aProfile.sha256(bytes.copy()));
            assertArrayEquals(image.readFile(range.fileOffset(),range.length()),bytes.copy());
        }
        for(var operand:p.operands())assertEquals(operand.hex(),HexFormat.of().formatHex(image.readFile(operand.fileOffset(),operand.hex().length()/2)));
        var key=p.range("LevelDataIndex");
        assertThrows(IllegalArgumentException.class,()->data.view(new CdPpzDataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256())));
        var foreign=CdPpzDataLayout.Programme.values()[(p.ordinal()+1)%10].range("LevelDataIndex");
        assertThrows(IllegalArgumentException.class,()->data.view(foreign));
        if(p==CdPpzDataLayout.Programme.R11A||p==CdPpzDataLayout.Programme.R11B)assertEquals(CdR11aPlayerAssets.SourceGraph.PPZ,r.playerBinding().graph());
        else assertThrows(UnsupportedOperationException.class,r::playerBinding);
        assertThrows(IllegalArgumentException.class,()->CdProgrammeData.fromReader(r));
    }

    @ParameterizedTest @EnumSource(CdPpzDataLayout.Programme.class)
    void allCanonicalJavaAndNativePrefetchProductsRemainDistinct(CdPpzDataLayout.Programme p)throws Exception {
        var r=reader(p);var facts=CdPpzProgrammeProfiles.facts(p);assertEquals(EXPECTED.get(p.ordinal()).sources(),facts.products().size());
        for(var product:facts.products()) {
            var key=p.range(product.key());var canonical=r.canonicalInput(key);var java=r.javaInput(key);var nativeRead=r.nativeInput(key);
            assertEquals(key.length(),canonical.length());assertEquals(key.sha256(),CdR11aProfile.sha256(canonical.copy()));
            assertEquals(product.javaLength(),java.length());assertEquals(product.javaSha(),CdR11aProfile.sha256(java.copy()));
            assertEquals(product.nativeLength(),nativeRead.length());assertEquals(product.nativeSha(),CdR11aProfile.sha256(nativeRead.copy()));
            var decoded=r.decoded(key);assertSame(decoded,r.decoded(key));
            assertEquals(product.output(),decoded.bytes().length());assertEquals(product.consumed(),decoded.consumedInputBytes());
            assertEquals(product.outputSha(),CdR11aProfile.sha256(decoded.bytes().copy()));
        }
    }

    @ParameterizedTest @EnumSource(CdPpzDataLayout.Programme.class)
    void loadTablesUseActualRootsAliasesSectionsPalettesAndDirectColorFormat(CdPpzDataLayout.Programme p)throws Exception {
        var r=reader(p);var tables=r.loadTables();assertSame(tables,r.loadTables());var f=CdPpzProgrammeProfiles.facts(p);var e=EXPECTED.get(p.ordinal());
        assertEquals(f.roots().size(),tables.plcs().size());assertEquals(e.bodies(),tables.plcs().stream().map(CdProgrammeLoadTables.Plc::body).distinct().count());
        assertEquals(e.encountered(),tables.encounteredSources().size());assertEquals(e.palettes(),tables.palettes().size());
        var raw=r.view(p.range("PLCLists"));int base=p.range("PLCLists").fileOffset();
        for(int i=0;i<tables.plcs().size();i++) {
            var plc=tables.plcs().get(i);int offset=base+(short)raw.unsignedWord(i*2);assertEquals(i,plc.nativeId());assertEquals(offset,plc.body().fileOffset());
            int at=offset-base;assertEquals(raw.unsignedWord(at)+1,plc.body().entries().size());
            for(int j=0;j<plc.body().entries().size();j++) {
                var row=plc.body().entries().get(j);assertEquals(raw.unsignedLong(at+2+j*6),row.sourceNativeAddress());
                assertEquals(raw.unsignedWord(at+6+j*6),row.vramAddress());assertEquals(r.loadSource(row.sourceNativeAddress()),row.source());
            }
            for(int j=0;j<i;j++)if(f.roots().get(i).equals(f.roots().get(j)))assertSame(tables.plcs().get(j).body(),plc.body());
        }
        assertEquals(f.sections(),tables.sectionProgramme());assertEquals(e.scripts(),tables.cycles().size());assertEquals(e.direct(),tables.directCycles().size());
        for(int i=0;i<tables.directCycles().size();i++) {
            var direct=tables.directCycles().get(i);assertEquals(3,direct.frames());assertEquals(4,direct.wordsPerFrame());assertEquals(24,direct.colors().length());
            assertEquals(i==0?7:5,direct.resetByte());assertEquals(i==0?0x6A:0x58,direct.destinationByte());
            assertArrayEquals(r.view(p.range("PalCycleData"+(i+1))).copy(),direct.colors().copy());
        }
        for(var palette:tables.palettes()) {
            assertEquals(0x200000L+p.range("Palette"+palette.nativeId()).fileOffset(),palette.sourceNativeAddress());
            assertEquals((palette.longwordCounter()+1)*4,palette.bytes().length());
        }
        assertInstanceOf(CdProgrammeLoadTables.BackgroundTables.class,tables.backgroundProgramme());
    }

    @ParameterizedTest @EnumSource(CdPpzDataLayout.Programme.class)
    void physicalPlacementOrderFullMasksAndAllSentinelsAreRetained(CdPpzDataLayout.Programme p)throws Exception {
        var r=reader(p);var data=CdPpzProgrammeData.fromReader(r);var placements=data.placements();var e=EXPECTED.get(p.ordinal());
        assertSame(placements,data.placements());assertTrue(placements.isRetainedOriginalProgramme());assertEquals(e.rows(),placements.records().size());
        assertArrayEquals(new byte[]{(byte)255,(byte)255,0,0,0,0},placements.nullList().orElseThrow().copy());
        assertArrayEquals(new byte[]{(byte)255,(byte)255,0,0,0,0,0,0},placements.forwardSentinel().copy());
        assertArrayEquals(placements.forwardSentinel().copy(),placements.backwardSentinel().orElseThrow().copy());
        var raw=r.view(p.range("ObjectLayouts"));int descending=0,previous=-1;
        for(int i=0;i<placements.records().size();i++) {
            var row=placements.records().get(i);var bytes=placements.rawRows().get(i);assertEquals(i,row.index());
            assertArrayEquals(raw.slice(12+i*8,8).copy(),bytes.copy());assertEquals(bytes.unsignedByte(6),row.periodFlags());
            assertEquals(bytes.unsignedByte(7),row.subtype2());assertEquals(bytes.unsignedWord(2),row.packedY());
            for(var period:CdPeriod.values()) {
                int mask=switch(period){case PAST->32;case PRESENT->64;case GOOD_FUTURE,BAD_FUTURE->128;};
                assertEquals((bytes.unsignedByte(6)&mask)!=0,row.appliesTo(period));
            }
            if(row.centreX()<previous)descending++;previous=row.centreX();
        }
        assertEquals(e.descending(),descending);assertEquals(e.past(),placements.forPeriod(CdPeriod.PAST).size());
        assertEquals(e.present(),placements.forPeriod(CdPeriod.PRESENT).size());assertEquals(e.future(),placements.forPeriod(CdPeriod.GOOD_FUTURE).size());
    }

    @ParameterizedTest @EnumSource(CdPpzDataLayout.Programme.class)
    void entryVisualsAndSelectedTerrainNeverSupplyUnbackedBytes(CdPpzDataLayout.Programme p)throws Exception {
        var r=reader(p);var data=CdPpzProgrammeData.fromReader(r);var entry=data.entryData();var f=CdPpzProgrammeProfiles.facts(p);
        assertEquals(16,entry.index().length());assertEquals(12,entry.cameraBounds().length());assertEquals(4,entry.startCentre().length());
        assertEquals(3,entry.index().unsignedLong(0)>>>24);assertEquals(2,entry.index().unsignedLong(4)>>>24);
        assertEquals(f.visual(),data.sonic().sourceFormat());assertEquals(p.name().startsWith("R13")?167:198,data.sonic().mappingFrames().size());
        var terrain=data.terrain();assertSame(terrain,data.terrain());assertEquals(f.terrain(),terrain.profile());
        assertThrows(UnsupportedOperationException.class,terrain::shape);
        var pixel=assertThrows(CdStageTerrain.UnavailableRead.class,()->terrain.pixel(terrain.patternCount(),0,0));
        assertEquals(CdStageTerrain.ReadOwner.PATTERN_SOURCE,pixel.owner());assertEquals(terrain.patternCount(),pixel.availableCount());
        assertThrows(CdStageTerrain.UnavailableRead.class,()->terrain.blockWord(terrain.blockCount(),0,0));
        assertThrows(CdStageTerrain.UnavailableRead.class,()->terrain.chunkWord(terrain.chunkCount()+1,0,0));
        assertThrows(CdStageTerrain.UnavailableRead.class,()->terrain.collision().idForBlock(terrain.collision().ids().length()));
    }

    @Test void originalPlacementFormatRetainsLowMaskBitsAndZeroWithoutChangingLegacyAdmission() {
        var layout=CdObjectPlacements.Layout.ORIGINAL_PPZ3_61_64;
        var low=new CdObjectPlacements.Placement(0,16,0,1,0,0x21,0,layout);
        assertEquals(0x21,low.periodFlags());assertTrue(low.appliesTo(CdPeriod.PAST));assertFalse(low.appliesTo(CdPeriod.PRESENT));
        var zero=new CdObjectPlacements.Placement(0,16,0,1,0,0,0,layout);
        for(var period:CdPeriod.values())assertFalse(zero.appliesTo(period));
        assertThrows(UnsupportedOperationException.class,()->new CdObjectPlacements.Placement(0,16,0,1,0,0,0));
    }
}
