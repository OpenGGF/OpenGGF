package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.openggf.game.soniccd.level.CdProgrammeReader.LoadRole.*;
import static com.openggf.game.soniccd.level.CdR4DataLayout.Programme.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Genuine original media ingress; explicitly marked synthetic controls exercise pure reached parsing. */
class TestCdProgrammeTtDefinitions {
    private record Expected(int ranges,int roots,int bodies,int encountered,int palettes,int cycles,int sections,int sources) { }
    private static final List<Expected> EXPECTED=List.of(
        new Expected(133,19,8,36,5,10,3,43),new Expected(114,19,10,31,5,3,5,45),
        new Expected(108,19,5,32,6,3,0,46),new Expected(107,19,5,32,6,3,0,45),
        new Expected(122,20,9,38,5,10,3,42),new Expected(114,19,10,32,5,3,5,45),
        new Expected(108,19,5,33,6,3,0,46),new Expected(107,19,5,33,6,3,0,45),
        new Expected(92,19,6,22,6,3,0,31),new Expected(94,19,6,22,6,3,0,31));
    private record Fixture(Path cue,CdDiscHeader header,CdObservedMediaIdentity identity,CdProgramCatalogue catalogue,
                           Map<CdR4DataLayout.Programme,CdProgrammeReader> readers,
                           Map<CdR4DataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    private static synchronized Fixture actual()throws Exception {
        if(retained!=null)return retained;
        String root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Optional original Sonic CD media absent");
        Path cue;
        try(var paths=Files.list(Path.of(root).resolve("USA"))) {
            var cues=paths.filter(p->p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();assertEquals(1,cues.size());cue=cues.getFirst();
        }
        try(var programmes=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertSame(CdRetailReleases.USA,CdRetailReleases.find(programmes.identity()).orElseThrow());
            var readers=new EnumMap<CdR4DataLayout.Programme,CdProgrammeReader>(CdR4DataLayout.Programme.class);
            var images=new EnumMap<CdR4DataLayout.Programme,CdMmdImage>(CdR4DataLayout.Programme.class);
            for(var p:CdR4DataLayout.Programme.values()) {
                readers.put(p,CdProgrammeReader.open(programmes,p.payload().stage()));images.put(p,programmes.mmd(p.file()));
            }
            retained=new Fixture(cue,programmes.header(),programmes.identity(),programmes.catalogue().orElseThrow(),Map.copyOf(readers),Map.copyOf(images));
        }
        return retained;
    }
    private static CdProgrammeReader reader(CdR4DataLayout.Programme p)throws Exception { return actual().readers().get(p); }
    private static CdProgrammeReader fresh(CdR4DataLayout.Programme p)throws Exception {
        try(var programmes=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            return CdProgrammeReader.open(programmes,p.payload().stage());
        }
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void everyOriginalUsesItsOwnDefinitionRangesHeadersOperandsAnd188Visuals(CdR4DataLayout.Programme p)throws Exception {
        var r=reader(p);var expected=EXPECTED.get(p.ordinal());var data=CdProgrammeData.fromReader(r);
        assertSame(p,data.programme());assertTrue(data.ownsReader(r));assertFalse(data.ownsReader(null));
        assertEquals(p.payload().stage(),r.stage());assertEquals(p.file(),r.file());assertEquals(p.fileSha256(),r.mmdSha());
        assertEquals(CdR4DataLayout.USA_DISC_SHA256,r.discSha());assertEquals(expected.ranges(),p.ranges().size());
        for(var range:p.ranges()) {
            var bytes=data.view(range);assertSame(bytes,data.view(range));assertEquals(range.length(),bytes.length());
            assertEquals(range.sha256(),CdR11aProfile.sha256(bytes.copy()));
            assertArrayEquals(actual().images().get(p).readFile(range.fileOffset(),range.length()),bytes.copy());
        }
        var visual=data.sonic();assertSame(visual,data.sonic());assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_188,visual.sourceFormat());
        assertEquals(188,visual.mappingFrames().size());assertEquals(188,visual.dplcFrames().size());
        assertEquals(76928,data.view(p.range("PlayerGfx")).length());assertEquals(82944,data.view(p.range("PlayerArtReadView")).length());
        assertArrayEquals(data.view(p.range("PlayerGfx")).copy(),data.view(p.range("PlayerArtReadView")).slice(0,76928).copy());
        assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188,
            data.view(p.range("PlayerSprites")).copy(),data.view(p.range("PlayerGfxScript")).copy(),data.view(p.range("PlayerGfx")).copy()));
        assertSame(r.terrain(),r.terrain());assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);
        assertThrows(UnsupportedOperationException.class,()->r.role(CdProgrammeReader.Role.MINI));
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void all191ActualRootsAndRowsMatchIndependentDeclarationsAndAliasBodies(CdR4DataLayout.Programme p)throws Exception {
        var r=reader(p);var t=r.loadTables();var expected=EXPECTED.get(p.ordinal());
        assertSame(t,r.loadTables());assertSame(t,CdProgrammeData.fromReader(r).tables());assertEquals(expected.roots(),t.plcs().size());
        assertEquals(expected.encountered(),t.encounteredSources().size());
        var bodies=new HashMap<Integer,CdProgrammeLoadTables.Body>();var raw=r.loadView(PLC);
        for(var declared:p.plcs()) {
            var list=t.plcs().get(declared.id());var body=list.body();assertEquals(declared.id(),list.nativeId());
            assertEquals(declared.fileOffset(),body.fileOffset());assertEquals(declared.entries().size(),body.entries().size());
            var prior=bodies.putIfAbsent(body.fileOffset(),body);if(prior!=null)assertSame(prior,body);
            int relative=(short)raw.unsignedWord(2*declared.id());assertEquals(declared.fileOffset(),r.loadFileOffset(PLC)+relative);
            assertEquals(declared.entries().size()-1,(short)raw.unsignedWord(relative));
            for(int i=0;i<declared.entries().size();i++) {
                var e=declared.entries().get(i);var row=body.entries().get(i);var source=p.range(e.source());
                assertSame(source,row.source());assertEquals(e.nativeAddress(),row.sourceNativeAddress());assertEquals(e.destinationWord(),row.vramAddress());
                assertEquals((r.canonicalInput(source).unsignedWord(0)&0x7FFF)*32,row.outputBytes());
                assertEquals(e.nativeAddress(),raw.unsignedLong(relative+2+6*i));assertEquals(e.destinationWord(),raw.unsignedWord(relative+6+6*i));
            }
        }
        assertEquals(expected.bodies(),bodies.size());assertSame(t.plcs().get(0).body(),t.plcs().get(3).body());
        assertThrows(UnsupportedOperationException.class,()->t.plcs().clear());
        assertThrows(UnsupportedOperationException.class,()->t.plcs().getFirst().body().entries().clear());
    }
    @ParameterizedTest @EnumSource(CdR4DataLayout.Programme.class)
    void palettesCycleProgrammesAndSectionAlternativesAreParsedActualBytes(CdR4DataLayout.Programme p)throws Exception {
        var r=reader(p);var t=r.loadTables();var expected=EXPECTED.get(p.ordinal());var palette=r.loadView(PALETTE);
        assertEquals(expected.palettes(),t.palettes().size());
        for(int i=0;i<t.palettes().size();i++) {
            var row=t.palettes().get(i);assertEquals(i,row.nativeId());assertEquals(palette.unsignedLong(i*8),row.sourceNativeAddress());
            assertEquals(palette.unsignedWord(i*8+4),row.destinationWord());assertEquals(palette.unsignedWord(i*8+6),row.longwordCounter());
            assertEquals((row.longwordCounter()+1)*4,row.bytes().length());assertSame(r.view(row.source()),row.bytes());
        }
        assertSame(t.palettes().get(0).bytes(),t.palettes().get(1).bytes());
        assertEquals(128,t.palettes().get(2).bytes().length());assertEquals(32,t.palettes().get(3).bytes().length());
        assertSame(p.range(p.name().endsWith("A")?"PlayerPaletteFullRead":"S1LevelSelectPalette"),t.palettes().get(2).source());
        if(expected.palettes()==6) {assertEquals(7,t.palettes().get(5).longwordCounter());assertEquals(32,t.palettes().get(5).bytes().length());}
        assertEquals(expected.cycles(),t.cycles().size());
        for(var cycle:t.cycles()) {assertEquals(2+2*cycle.script().unsignedByte(1),cycle.script().length());assertTrue(cycle.colors().length()>0);}
        if(p.name().endsWith("A")) {
            assertEquals(p==R41A?CdProgrammeDefinition.CycleProgramme.UPPER_SIX_OR_STANDARD_FOUR:CdProgrammeDefinition.CycleProgramme.STANDARD_FOUR_WITH_RETAINED_UPPER,t.cycleProgramme());
            assertEquals(6,t.cycles().stream().filter(c->c.kind()==CdProgrammeDefinition.CycleKind.UPPER).count());
            assertEquals(4,t.cycles().stream().filter(c->c.kind()==CdProgrammeDefinition.CycleKind.STANDARD).count());
            assertSame(t.cycles().get(6).colors(),t.cycles().get(9).colors());
        } else {
            assertEquals(CdProgrammeDefinition.CycleProgramme.THREE_SOURCE_ACT_GATED,t.cycleProgramme());
            assertSame(t.cycles().get(0).colors(),t.cycles().get(2).colors());
        }
        if(expected.sections()>0) {
            var camera=assertInstanceOf(CdProgrammeDefinition.CameraSections.class,t.sectionProgramme());assertEquals(expected.sections(),camera.rows().size());
            var ranges=r.view(p.range("SectionGfxRanges"));var initial=r.view(p.range("SectionGfxInitLists"));var update=r.view(p.range("SectionGfxUpdateLists"));
            for(int i=0;i<camera.rows().size();i++) {var row=camera.rows().get(i);assertEquals(ranges.unsignedWord(i*2),row.threshold());assertEquals(initial.unsignedWord(i*2),row.initial());assertEquals(update.unsignedWord(i*2),row.update());}
        } else assertEquals(new CdProgrammeDefinition.InitQueueUpdateRts(2),t.sectionProgramme());
        assertThrows(UnsupportedOperationException.class,t::sections);assertThrows(UnsupportedOperationException.class,t::backgroundPointers);
    }
    @Test void publicEqualRangeValuesAndForeignKeysCannotSelectOwnedBytesOrProducts()throws Exception {
        var r=reader(R41A);var data=CdProgrammeData.fromReader(r);var a=R41A.range("PlayerGfx");
        var equal=new CdR4DataLayout.Range(a.key(),a.fileOffset(),a.length(),a.sha256(),a.nativePath(),a.line());assertEquals(a,equal);
        assertThrows(IllegalArgumentException.class,()->data.view(equal));assertThrows(IllegalArgumentException.class,()->data.view(R42A.range("PlayerGfx")));
        assertThrows(IllegalArgumentException.class,()->r.view(CdR31aAssets.Asset.ART_SONIC));assertThrows(IllegalArgumentException.class,()->r.view("PlayerGfx"));
        assertThrows(IllegalArgumentException.class,()->reader(R42A).view(r.loadKey(PLC)));
        assertThrows(IllegalArgumentException.class,()->r.canonicalInput(R41B.range("StageGfx")));
        assertThrows(NullPointerException.class,()->data.view(null));
    }
    @Test void separateEqualOriginalsKeepTheirActualReaderAndVisualTableCacheIdentity()throws Exception {
        var a=reader(R41A);var b=fresh(R41A);var da=CdProgrammeData.fromReader(a);var db=CdProgrammeData.fromReader(b);
        assertEquals(a.mmdSha(),b.mmdSha());assertNotSame(a,b);assertFalse(da.ownsReader(b));assertFalse(db.ownsReader(a));
        assertNotSame(da.sonic(),db.sonic());assertNotSame(da.view(R41A.range("PlayerGfx")),db.view(R41A.range("PlayerGfx")));
        assertNotSame(da.tables(),db.tables());assertNotSame(da.tables().plcs().get(0).body(),db.tables().plcs().get(0).body());
    }
    @Test void all419CompressedIncludesAndQualifiedProductsUseTheirOriginalBounds()throws Exception {
        int total=0;
        for(var p:CdR4DataLayout.Programme.values()) {
            var r=reader(p);var data=CdProgrammeData.fromReader(r);assertEquals(EXPECTED.get(p.ordinal()).sources(),data.compressedRanges().size());
            for(var key:data.compressedRanges()) {
                total++;var include=data.canonicalInput(key);assertSame(include,data.canonicalInput(key));assertEquals(key.length(),include.length());
                assertEquals(key.sha256(),CdR11aProfile.sha256(include.copy()));assertEquals((include.unsignedWord(0)&32767)*32,r.loadOutputBytes(key));
                var contract=data.productContract(key);
                assertEquals(contract.javaProduct().input().length(),data.javaInput(key).length());
                assertEquals(contract.nativeInput().length(),data.nativeInput(key).length());
                assertEquals(contract.javaProduct().outputSha(),CdR11aProfile.sha256(data.decoded(key).bytes().copy()));
                assertSame(data.decoded(key),data.decoded(key));
                assertSame(include,data.canonicalInput(key));
            }
        }
        assertEquals(419,total);
    }
    @Test void unsupportedSourceFamiliesStillRejectBeforeAnyProgrammeOwnerRead() throws Exception {
        var source=mock(CdProgramAssets.class);
        for(var payload:List.of(CdStagePayload.R12A,CdStagePayload.R11A))
            assertThrows(UnsupportedOperationException.class,()->CdProgrammeReader.open(source,payload.stage()));
        verifyNoInteractions(source);
        // R31B now has authenticated DATA; preserve its still-unadmitted source-family boundary.
        try(var actual=TestCdCczProgrammeData.open()) {
            var probe=spy(actual);var reader=CdProgrammeReader.open(probe,CdStagePayload.R31B.stage());
            clearInvocations(probe);assertThrows(UnsupportedOperationException.class,()->CdStagePlayerSource.fromProgramme(reader));verifyNoInteractions(probe);
        }
    }
    private static CdProgramAssets negativeSource(CdR4DataLayout.Programme p,CdMmdImage image)throws Exception {
        var f=actual();var source=mock(CdProgramAssets.class);when(source.header()).thenReturn(f.header());when(source.identity()).thenReturn(f.identity());
        when(source.catalogue()).thenReturn(Optional.of(f.catalogue()));when(source.mmd(p.file())).thenReturn(image);return source;
    }
    @Test void eachProgrammeRetainsRegionWholeDiscAndExactCatalogueChecksBeforeMmdLookup()throws Exception {
        for(var p:CdR4DataLayout.Programme.values()) {
            var region=negativeSource(p,actual().images().get(p));var header=mock(CdDiscHeader.class);
            when(header.observedVariant()).thenReturn(Optional.of(CdReleaseVariant.JAPAN));when(region.header()).thenReturn(header);
            assertThrows(IOException.class,()->CdProgrammeReader.open(region,p.payload().stage()));verify(region,never()).mmd(any());
            var wrong=negativeSource(p,actual().images().get(p));var catalogue=mock(CdProgramCatalogue.class);
            when(catalogue.release()).thenReturn(CdReleaseVariant.USA);when(catalogue.stage(p.payload().stage())).thenReturn(CdStagePayload.R31A);
            when(wrong.catalogue()).thenReturn(Optional.of(catalogue));assertThrows(IOException.class,()->CdProgrammeReader.open(wrong,p.payload().stage()));verify(wrong,never()).mmd(any());
            var unknown=negativeSource(p,actual().images().get(p));var id=actual().identity();
            when(unknown.identity()).thenReturn(new CdObservedMediaIdentity(id.definition(),"0".repeat(64),id.cue(),id.binaryFiles(),id.originalContainer(),id.tracks()));
            assertThrows(IOException.class,()->CdProgrammeReader.open(unknown,p.payload().stage()));verify(unknown,never()).mmd(any());
        }
    }
    @Test void foreignOriginalsTruncatedImagesAndChangedOperandsNeverBecomeRetainedData()throws Exception {
        for(var p:CdR4DataLayout.Programme.values()) {
            var image=actual().images().get(p);var foreign=actual().images().get(p==R41A?R41B:R41A);
            var wrong=negativeSource(p,foreign);assertThrows(IOException.class,()->CdProgrammeReader.open(wrong,p.payload().stage()));
            byte[] shortBytes=image.readFile(0,262143);var shortSource=negativeSource(p,CdMmdImage.parse(shortBytes));
            assertThrows(IOException.class,()->CdProgrammeReader.open(shortSource,p.payload().stage()));
            byte[] bytes=image.readFile(0,262144);bytes[p.operands().getFirst().fileOffset()+5]^=1;
            var changed=negativeSource(p,CdMmdImage.parse(bytes));assertThrows(IOException.class,()->CdProgrammeReader.open(changed,p.payload().stage()));
        }
    }
    private static byte[] word(byte[] bytes,int at,int value) {var result=bytes.clone();ByteBuffer.wrap(result).putShort(at,(short)value);return result;}
    private static CdProgrammeReader changed(CdProgrammeReader source,CdProgrammeReader.LoadRole role,byte[] bytes) {
        var control=spy(source);doReturn(CdAssetBytes.copyOf(bytes)).when(control).loadView(role);clearInvocations(control);return control;
    }
    @Test void syntheticTwentyRootAndSignedCounterFaultsStopBeforeSourceAndPaletteReads()throws Exception {
        var r=reader(R42A);var bytes=r.loadView(PLC).copy();
        for(int invalid:new int[]{38,41,0x8000,0xFFFF,bytes.length}) {
            var control=changed(r,PLC,word(bytes,0,invalid));
            assertEquals("Signed PLC root outside finite body span",assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control,never()).loadSource(anyLong());verify(control,never()).loadView(PALETTE);
        }
        for(int invalid:new int[]{0x8000,0xFFFF,15,32767}) {
            var control=changed(r,PLC,word(bytes,40,invalid));
            assertEquals("PLC finite counter/extent differs",assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control,never()).loadSource(anyLong());verify(control,never()).loadView(PALETTE);
        }
    }
    @Test void syntheticFullLongSourceAndOutputFaultsPrecedeWordDestinationAndLaterTables()throws Exception {
        var r=reader(R42A);var bytes=r.loadView(PLC).copy();long first=R42A.plcs().getFirst().entries().getFirst().nativeAddress();
        var foreign=bytes.clone();ByteBuffer.wrap(foreign).putInt(42,(int)(first|0x80000000L)).putShort(46,(short)1);
        var bad=changed(r,PLC,foreign);assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(bad));
        verify(bad).loadSource(first|0x80000000L);verify(bad,never()).loadOutputBytes(any());verify(bad,never()).loadView(PALETTE);
        for(int invalid:new int[]{1,0xFFE0}) {
            var control=changed(r,PLC,word(bytes,46,invalid));
            assertEquals("PLC output/VRAM range differs",assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control,times(1)).loadSource(first);verify(control).loadOutputBytes(R42A.range("StageGfx"));verify(control,never()).loadView(PALETTE);
        }
    }
    @Test void syntheticPaletteRowTwoKeepsPointerDestinationCounterThenFullReadFaultOrder()throws Exception {
        var r=reader(R41A);var bytes=r.loadView(PALETTE).copy();
        var wrongPointer=Arrays.copyOf(bytes,20);ByteBuffer.wrap(wrongPointer).putInt(16,0x802006CC);
        var pointer=changed(r,PALETTE,wrongPointer);
        assertEquals("Actual palette operand differs",assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(pointer)).getMessage());
        var validPointer=changed(r,PALETTE,Arrays.copyOf(bytes,20));assertThrows(IndexOutOfBoundsException.class,()->CdProgrammeLoadTables.decode(validPointer));
        var destination=changed(r,PALETTE,Arrays.copyOf(word(bytes,20,0xFB80),22));
        assertEquals("Actual palette operand differs",assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(destination)).getMessage());
        var validDestination=changed(r,PALETTE,Arrays.copyOf(bytes,22));assertThrows(IndexOutOfBoundsException.class,()->CdProgrammeLoadTables.decode(validDestination));
        for(var control:List.of(pointer,validPointer,destination,validDestination)) {
            verify(control,never()).loadViewForKey(R41A.range("PlayerPaletteFullRead"));
            verify(control,never()).loadViewForKey(R41A.range("R4PaletteCycleAct1UpperGroup1Timing"));
        }
    }
    @Test void explicitRtsAndDirectCameraProgrammesNeverReadAbsentTableRoles()throws Exception {
        for(var p:List.of(R41C,R41D,R42C,R42D,R43C,R43D)) {
            var control=spy(reader(p));clearInvocations(control);var parsed=CdProgrammeLoadTables.decode(control);
            assertEquals(new CdProgrammeDefinition.InitQueueUpdateRts(2),parsed.sectionProgramme());
            assertSame(CdProgrammeLoadTables.DirectCameraData.INSTANCE,parsed.backgroundProgramme());
            verify(control,never()).loadView(SECTIONS);verify(control,never()).loadView(BG_POINTERS);
            assertThrows(UnsupportedOperationException.class,()->control.loadKey(SECTIONS));
        }
    }
    @Test void copiedAndLivePointerTablesAnd130ByteSelectorRemainIndependentActualData()throws Exception {
        var r=reader(R41A);var parsed=assertInstanceOf(CdProgrammeLoadTables.BackgroundTables.class,r.loadTables().backgroundProgramme());
        assertEquals(2,parsed.tables().size());assertEquals(CdProgrammeDefinition.PointerKind.COPIED,parsed.tables().get(0).kind());
        assertEquals(List.of(0xFF192E,0xFF192E,0xFF1936,0xFF193E),parsed.tables().get(0).values());
        assertEquals(CdProgrammeDefinition.PointerKind.LIVE,parsed.tables().get(1).kind());
        assertEquals(List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718),parsed.tables().get(1).values());
        assertSame(r.view(R41A.range("unk_203818")),parsed.rowSelectors().orElseThrow());assertEquals(130,parsed.rowSelectors().orElseThrow().length());
        for(var p:CdR4DataLayout.Programme.values())if(p!=R41A)assertSame(CdProgrammeLoadTables.DirectCameraData.INSTANCE,reader(p).loadTables().backgroundProgramme());
    }
    @Test void lateSyntheticFailurePublishesNoSuccessAndRetryRetainsActualAliasBodies()throws Exception {
        var r=fresh(R41A);var key=R41A.range("R4PaletteCycleAct1UpperGroup1Timing");var control=spy(r);
        doReturn(CdAssetBytes.copyOf(new byte[key.length()-1])).when(control).loadViewForKey(key);clearInvocations(control);
        assertThrows(IndexOutOfBoundsException.class,control::loadTables);assertThrows(IndexOutOfBoundsException.class,control::loadTables);
        verify(control,never()).loadView(SECTIONS);doReturn(r.view(key)).when(control).loadViewForKey(key);
        var tables=control.loadTables();assertSame(tables,control.loadTables());assertSame(tables.plcs().get(0).body(),tables.plcs().get(3).body());
        assertNotSame(tables,r.loadTables());
    }
    @Test void r42bRetainedGoalAfterGfxEndIsNotAParsedNinthEntryAndBossListsStayDistinct()throws Exception {
        var b=reader(R42B).loadTables().plcs().get(4).body();assertEquals(8,b.entries().size());
        assertSame(R42B.range("AnimalsGfx"),b.entries().getLast().source());
        var following=CdAssetBytes.copyOf(actual().images().get(R42B).readFile(b.fileOffset()+2+8*6,6));
        assertEquals(R42B.range("GoalGfx").nativeAddress(),following.unsignedLong(0));assertEquals(0x9E40,following.unsignedWord(4));
        for(var p:List.of(R43C,R43D)) {
            var t=reader(p).loadTables();assertSame(p.range("BossBubbleGfx"),t.plcs().get(4).body().entries().getFirst().source());
            assertEquals(0x63C0,t.plcs().get(4).body().entries().getFirst().vramAddress());
            assertSame(p.range("CapsuleGfx"),t.plcs().get(18).body().entries().getFirst().source());assertEquals(0x9020,t.plcs().get(18).body().entries().getFirst().vramAddress());
        }
    }
}
