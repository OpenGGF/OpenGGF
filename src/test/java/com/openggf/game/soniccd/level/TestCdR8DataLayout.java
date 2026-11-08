package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.delegatesTo;

/** Real original USA ingress; synthetic fault ports are labelled and cannot authenticate a source. */
class TestCdR8DataLayout {
    record Fixture(Path cue,Map<CdR8DataLayout.Programme,CdProgrammeReader> readers,Map<CdR8DataLayout.Programme,CdMmdImage> images) { }
    private static Fixture retained;
    static synchronized Fixture actual()throws Exception {
        if(retained!=null)return retained;
        var root=System.getProperty("soniccd.media.root");assumeTrue(root!=null&&!root.isBlank(),"Original Sonic CD media absent");
        Path cue=Path.of(root).resolve("USA/Sonic CD (US).cue");
        try(var source=CdProgramAssets.open(cue,Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            assertSame(CdRetailReleases.USA,CdRetailReleases.find(source.identity()).orElseThrow());
            var readers=new EnumMap<CdR8DataLayout.Programme,CdProgrammeReader>(CdR8DataLayout.Programme.class);
            var images=new EnumMap<CdR8DataLayout.Programme,CdMmdImage>(CdR8DataLayout.Programme.class);
            for(var p:CdR8DataLayout.Programme.values()) {readers.put(p,CdProgrammeReader.open(source,p.payload().stage()));images.put(p,source.mmd(p.file()));}
            retained=new Fixture(cue,Map.copyOf(readers),Map.copyOf(images));
        }
        return retained;
    }
    static CdProgrammeReader reader(CdR8DataLayout.Programme p)throws Exception { return actual().readers().get(p); }
    @ParameterizedTest @EnumSource(CdR8DataLayout.Programme.class)
    void authenticatesEveryOriginalRangeOperandAndHeader(CdR8DataLayout.Programme p)throws Exception {
        var r=reader(p);var image=actual().images().get(p);
        assertEquals(p.file(),r.file());assertEquals(p.payload().stage(),r.stage());assertEquals(p.fileSha256(),r.mmdSha());
        assertEquals(CdMmdImage.Domain.ORIGINAL_WORD_RAM,image.originalWordRam().domain());
        assertEquals(new CdMmdHeader(0,0,0,0,0x200100,0x20010C,0x200112),image.header());
        assertEquals(262144,image.fileBytes());assertEquals(0,image.unresolvedCopyBytes());assertEquals(0,image.fileBackedCopyBytes());
        assertEquals(p.ranges().size(),r.definition().raw().size());
        for(var range:p.ranges()) {
            var view=r.view(range);assertEquals(range.length(),view.length());assertSame(view,r.view(range));
            assertEquals(range.sha256(),CdR11aProfile.sha256(view.copy()));assertArrayEquals(image.readFile(range.fileOffset(),range.length()),view.copy());
        }
        for(var op:p.operands())assertEquals(op.encodedHex(),HexFormat.of().formatHex(image.readFile(op.fileOffset(),op.encodedHex().length()/2)));
        assertSame(r.terrain(),r.terrain());
        assertThrows(UnsupportedOperationException.class,r::ordinaryZoneActWord);
        assertEquals(CdSonicVisualAssets.SourceFormat.FRAMES_188_COMPACT_ART,r.sonic().sourceFormat());
        assertSame(r.sonic(),r.sonic());
        assertEquals(p.name().endsWith("C")?OptionalInt.of(1):p.name().endsWith("D")?OptionalInt.of(0):OptionalInt.empty(),p.goodFutureSelectionConstraint());
    }
    @ParameterizedTest @EnumSource(CdR8DataLayout.Programme.class)
    void allPlcAliasesAndOrderedEntriesUseSharedParser(CdR8DataLayout.Programme p)throws Exception {
        var r=reader(p);var t=r.loadTables();assertSame(t,r.loadTables());assertEquals(19,t.plcs().size());
        var aliases=new HashMap<Integer,CdProgrammeLoadTables.Body>();var encountered=new LinkedHashSet<Object>();
        for(var plc:p.plcs()) {
            var parsed=t.plcs().get(plc.id());var body=parsed.body();assertEquals(plc.fileOffset(),body.fileOffset());assertEquals(plc.entries().size(),body.entries().size());
            var old=aliases.putIfAbsent(body.fileOffset(),body);if(old!=null)assertSame(old,body);
            for(int i=0;i<plc.entries().size();i++) {
                var expected=plc.entries().get(i);var entry=body.entries().get(i);var key=p.range(expected.source());
                assertSame(key,entry.source());assertEquals(expected.nativeAddress(),entry.sourceNativeAddress());assertEquals(expected.destinationWord(),entry.vramAddress());
                assertEquals((r.canonicalInput(key).unsignedWord(0)&32767)*32,entry.outputBytes());encountered.add(key);
            }
        }
        assertEquals(List.copyOf(encountered),t.encounteredSources());
        if(p.payload().stage().actNumber()==3) {
            assertSame(CdProgrammeDefinition.NoSections.INSTANCE,t.sectionProgramme());
            var probe=spy(r);clearInvocations(probe);assertSame(CdProgrammeDefinition.NoSections.INSTANCE,CdProgrammeLoadTables.decode(probe).sectionProgramme());
            verify(probe,never()).loadView(CdProgrammeReader.LoadRole.SECTIONS);
            assertThrows(UnsupportedOperationException.class,()->r.loadKey(CdProgrammeReader.LoadRole.SECTIONS));
        }
        else assertEquals(p.payload().stage().actNumber()==1?6:4,assertInstanceOf(CdProgrammeDefinition.CameraSections.class,t.sectionProgramme()).rows().size());
        assertEquals(2,assertInstanceOf(CdProgrammeLoadTables.BackgroundTables.class,t.backgroundProgramme()).tables().size());
        assertThrows(UnsupportedOperationException.class,t::sections);assertThrows(UnsupportedOperationException.class,t::backgroundPointers);
        assertThrows(UnsupportedOperationException.class,()->t.plcs().clear());
    }
    @ParameterizedTest @EnumSource(CdR8DataLayout.Programme.class)
    void actualDispatchAndPlacementsPreserveNativeOrderAndPlayerFormatBoundary(CdR8DataLayout.Programme p)throws Exception {
        var r=reader(p);var table=r.view(p.range("ObjectIndex"));assertEquals(67,p.dispatch().size());
        for(var d:p.dispatch())assertEquals(d.nativeAddress(),table.unsignedLong((d.id()-1)*4));
        assertEquals("PlayerObject",p.dispatch().get(0).handler());assertEquals("PlayerObject",p.dispatch().get(1).handler());
        var layout=r.view(p.range("ObjectLayouts"));int at=12;assertEquals(12,layout.unsignedWord(0));assertEquals(65535,layout.unsignedWord(4));int previous=-1;int rows=0;
        // This is source-data inspection, not admission through the older bounded placement layouts.
        while(layout.unsignedWord(at)!=65535) {
            int x=layout.unsignedWord(at);assertTrue(x>=previous);previous=x;
            int id=layout.unsignedByte(at+4)&127;assertTrue(id>=1&&id<=67);
            assertTrue((layout.unsignedByte(at+6)&224)!=0);at+=8;rows++;
        }
        assertEquals(p.payload().stage().actNumber()==3?118:p.payload().stage().actNumber()==1?284:434,rows);
        assertEquals(0,layout.unsignedLong(at+2));
        var maps=r.view(p.range("PlayerSprites"));var dplc=r.view(p.range("PlayerGfxScript"));var art=r.view(p.range("PlayerGfx"));
        assertEquals(2639,maps.length());assertEquals(1454,dplc.length());assertEquals(59232,art.length());
        for(int i=0;i<188;i++) {
            int m=(short)maps.unsignedWord(i*2);assertTrue(m>=376&&m<maps.length());assertTrue(1+maps.unsignedByte(m)*5<=maps.length()-m);
            int d=(short)dplc.unsignedWord(i*2);assertTrue(d>=376&&d<dplc.length());assertTrue(2+2*dplc.unsignedWord(d)<=dplc.length()-d);
        }
        assertThrows(IllegalArgumentException.class,()->CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_188,maps.copy(),dplc.copy(),art.copy()));
    }
    @ParameterizedTest @EnumSource(CdR8DataLayout.Programme.class)
    void completePaletteAndCycleDescriptorsKeepUnavailableReadExplicit(CdR8DataLayout.Programme p)throws Exception {
        var r=reader(p);var tables=r.loadTables();var raw=r.view(p.range("PaletteTable"));
        assertEquals(p.name().endsWith("A")||p.name().endsWith("B")?6:12,tables.palettes().size());
        assertEquals(tables.palettes().size()*8,raw.length());
        for(var row:tables.palettes()) {
            int at=row.nativeId()*8;assertEquals(raw.unsignedLong(at),row.sourceNativeAddress());
            assertEquals(raw.unsignedWord(at+4),row.destinationWord());assertEquals(raw.unsignedWord(at+6),row.longwordCounter());
            if(row.sourceNativeAddress()==0) {
                assertEquals(p.payload().stage().period()==CdPeriod.BAD_FUTURE?6:10,row.nativeId());assertEquals(65535,row.longwordCounter());
                assertTrue(row.sourceBytes().isEmpty());assertThrows(UnsupportedOperationException.class,row::bytes);
            } else {
                assertEquals((row.longwordCounter()+1)*4,row.bytes().length());
                assertArrayEquals(TestCdR8DataLayout.actual().images().get(p).readFile((int)row.sourceNativeAddress()-0x200000,row.bytes().length()),row.bytes().copy());
            }
        }
        assertEquals(CdProgrammeDefinition.CycleProgramme.SOURCE_ACT_GATED,tables.cycleProgramme());
        if(p.payload().stage().period()==CdPeriod.GOOD_FUTURE) {
            assertEquals(6,r.view(p.range("word_20024A")).length());
            var hull=r.view(p.range("word_20024AReadView"));assertEquals(134,hull.length());
            assertArrayEquals(TestCdR8DataLayout.actual().images().get(p).readFile(p.range("word_20024A").fileOffset(),134),hull.copy());
        }
        for(var cycle:tables.cycles()) {
            var script=cycle.script();assertTrue(script.length()>=2+script.unsignedByte(1)*2);
            for(int i=0;i<script.unsignedByte(1);i++)assertTrue(script.signedByte(3+2*i)*2>=0&&script.signedByte(3+2*i)*2<cycle.colors().length());
        }
    }
    /** Synthetic post-authentication shared-parser fault; earlier reads confer no table product. */
    @Test void malformedPlcDirectoryDoesNotAdoptTablesAndCanRetry()throws Exception {
        var p=CdR8DataLayout.Programme.R81A;
        try(var source=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var r=spy(CdProgrammeReader.open(source,p.payload().stage()));var raw=r.loadView(CdProgrammeReader.LoadRole.PLC);
            var bad=raw.copy();bad[0]=0;bad[1]=0;doReturn(CdAssetBytes.copyOf(bad)).when(r).loadView(CdProgrammeReader.LoadRole.PLC);
            assertThrows(IllegalArgumentException.class,r::loadTables);
            doReturn(raw).when(r).loadView(CdProgrammeReader.LoadRole.PLC);var accepted=r.loadTables();assertSame(accepted,r.loadTables());
        }
    }
    @Test void inheritedCycleOrdinalsAreUnchanged() {
        assertEquals(0,CdProgrammeDefinition.CycleProgramme.STANDARD_FOUR.ordinal());
        assertEquals(1,CdProgrammeDefinition.CycleProgramme.UPPER_SIX_OR_STANDARD_FOUR.ordinal());
        assertEquals(2,CdProgrammeDefinition.CycleProgramme.STANDARD_FOUR_WITH_RETAINED_UPPER.ordinal());
        assertEquals(3,CdProgrammeDefinition.CycleProgramme.THREE_SOURCE_ACT_GATED.ordinal());
        assertEquals(10,CdProgrammeDefinition.CycleProgramme.SOURCE_ACT_GATED.ordinal());
    }
    @Test void catalogueIsExactlyTenOriginalUsaDefinitions() {
        assertEquals(10,CdR8DataLayout.Programme.values().length);
        assertThrows(UnsupportedOperationException.class,()->CdProgrammeDefinition.select(CdStageKey.parse("R11A")));
    }
    @Test void equalAndForeignDeclarationsHaveNoReaderAuthority()throws Exception {
        var p=CdR8DataLayout.Programme.R81A;var r=reader(p);var key=p.range("StageGfx");
        var equal=new CdR8DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256(),key.nativePath(),key.line());assertEquals(key,equal);
        for(var bad:List.of(equal,CdR8DataLayout.Programme.R81B.range("StageGfx"))) {
            assertThrows(IllegalArgumentException.class,()->r.view(bad));assertThrows(IllegalArgumentException.class,()->r.decoded(bad));
        }
    }
    /** Synthetic selected-image fault on genuine source ingress; no native execution. */
    @Test void wrongImageAndChangedOrTruncatedOriginalNeverConstructAReader()throws Exception {
        var p=CdR8DataLayout.Programme.R81A;
        try(var actual=CdProgramAssets.open(actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var source=mock(CdProgramAssets.class,delegatesTo(actual));doReturn(actual.mmd(CdR8DataLayout.Programme.R81B.file())).when(source).mmd(p.file());
            assertThrows(IOException.class,()->CdProgrammeReader.open(source,p.payload().stage()));
            var image=spy(actual.mmd(p.file()));doReturn(image).when(source).mmd(p.file());var original=image.readFile(0,262144);var changed=original.clone();changed[0x100]^=1;
            for(var bad:List.of(changed,Arrays.copyOf(original,262143))) {doReturn(bad).when(image).readFile(0,262144);assertThrows(IOException.class,()->CdProgrammeReader.open(source,p.payload().stage()));}
            doReturn(original).when(image).readFile(0,262144);assertEquals(p.file(),CdProgrammeReader.open(source,p.payload().stage()).file());
        }
    }
}
