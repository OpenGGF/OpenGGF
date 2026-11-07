package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdProgramAssets;
import java.nio.ByteBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static com.openggf.game.soniccd.level.CdProgrammeDefinition.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Original-media independent section columns; DATA only, no camera/queue invocation authority. */
class TestCdProgrammeSectionColumns {
    private static final CdR5DataLayout.Programme C=CdR5DataLayout.Programme.R53C;
    private static CdProgrammeReader reader(CdR5DataLayout.Programme p)throws Exception {
        return TestCdR5DataLayout.actual().readers().get(p);
    }
    private static List<SectionRow> rows() { return List.of(new SectionRow(0x1800,2,5),new SectionRow(0xFFFF,4,6)); }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,names={"R53C","R53D"})
    void originalAct3ProductsExposeTwoActiveRowsWithoutDiscardingUnusedColumnWords(CdR5DataLayout.Programme p)throws Exception {
        var r=reader(p);var tables=r.loadTables();var sections=(CameraSections)tables.sectionProgramme();
        assertEquals(rows(),sections.rows());var columns=assertInstanceOf(SectionColumns.class,sections.operands());
        assertEquals(2,columns.activeThresholdCount());assertEquals(List.of(0x1800,0xFFFF),columns.thresholds().words());
        assertEquals(List.of(5,6,5),columns.update().words());assertEquals(List.of(2,4,2),columns.initial().words());
        assertSame(p.range("SectionGfxRangesRead"),columns.thresholds().source().key());
        assertSame(p.range("SectionGfxUpdateListsRead"),columns.update().source().key());
        assertSame(p.range("SectionGfxInitListsRead"),columns.initial().source().key());
        for(var column:List.of(columns.thresholds(),columns.initial(),columns.update())) {
            assertEquals(column.words().size()*2,column.source().length());assertEquals(0,column.source().offset());
            var raw=r.view(column.source().key());var inverse=ByteBuffer.allocate(column.source().length());
            column.words().forEach(word->inverse.putShort((short)(int)word));assertArrayEquals(raw.copy(),inverse.array());
            assertThrows(UnsupportedOperationException.class,()->column.words().clear());
        }
        assertThrows(UnsupportedOperationException.class,()->sections.rows().clear());
        assertEquals(19,tables.plcs().size());assertEquals(7,tables.palettes().size());assertSame(tables,r.loadTables());
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,names={"R53C","R53D"})
    void nativeUnsignedThresholdEdgesExposeNoManufacturedThirdRow(CdR5DataLayout.Programme p)throws Exception {
        var sections=(CameraSections)reader(p).loadTables().sectionProgramme();
        assertEquals(OptionalInt.of(0),finiteNativeIndex(0,sections.rows()));
        assertEquals(OptionalInt.of(0),finiteNativeIndex(0x17FF,sections.rows()));
        assertEquals(OptionalInt.of(1),finiteNativeIndex(0x1800,sections.rows()));
        assertEquals(OptionalInt.of(1),finiteNativeIndex(0xFFFE,sections.rows()));
        assertTrue(finiteNativeIndex(0xFFFF,sections.rows()).isEmpty());assertEquals(2,sections.rows().size());
    }
    // Bounded source predicate only: N r5/section_gfx_3c.asm:9-20/33-48, strict unsigned BCS.
    // No camera service is executed; raw FFFF goes past this finite source proof, not to a default row.
    private static OptionalInt finiteNativeIndex(int rawWord,List<SectionRow> rows) {
        for(int i=0;i<rows.size();i++)if(rawWord<rows.get(i).threshold())return OptionalInt.of(i);
        return OptionalInt.empty();
    }
    private static SectionColumns columns(CdProgrammeReader r) { return (SectionColumns)((CameraSections)r.definition().load().sections()).operands(); }
    private static CdProgrammeReader format(CdProgrammeReader r,CameraSections sections) {
        var f=r.definition().load();var load=new Load(f.directoryCount(),f.maximumCount(),f.roots(),f.bodyCount(),f.encountered(),
            f.palettes(),f.cycles(),f.cycleProgramme(),sections,f.background());
        var definition=spy(r.definition());doReturn(load).when(definition).load();
        var control=spy(r);doReturn(definition).when(control).definition();clearInvocations(control);return control;
    }
    private static byte[] word(byte[] input,int at,int value) {var out=input.clone();ByteBuffer.wrap(out).putShort(at,(short)value);return out;}
    @Test void realIndependentOffsetsCanShareOneAuthenticatedSourceBorrow()throws Exception {
        var r=reader(C);var key=C.range("SectionGfxRanges");assertEquals(16,r.view(key).length());
        var c=new SectionColumns(2,new SectionWords(new Slice(key,0,4),List.of(0x1800,0xFFFF)),
            new SectionWords(new Slice(key,10,6),List.of(2,4,2)),new SectionWords(new Slice(key,4,6),List.of(5,6,5)));
        var control=format(r,new CameraSections(rows(),c));var parsed=(CameraSections)CdProgrammeLoadTables.decode(control).sectionProgramme();
        assertEquals(rows(),parsed.rows());assertEquals(c,parsed.operands());verify(control,times(1)).loadViewForKey(key);
    }
    @Test void malformedColumnDeclarationsRejectWithoutSourceAccess() {
        Object key=new Object();
        for(var slice:List.of(new Slice(key,-2,4),new Slice(key,1,4),new Slice(key,0,3),
                new Slice(key,0,6),new Slice(key,Integer.MAX_VALUE-1,4)))
            assertThrows(IllegalArgumentException.class,()->new SectionWords(slice,List.of(0x1800,0xFFFF)));
        assertThrows(IllegalArgumentException.class,()->new SectionWords(new Slice(key,0,0),List.of()));
        assertThrows(IllegalArgumentException.class,()->new SectionWords(new Slice(key,0,2),List.of(-1)));
        assertThrows(IllegalArgumentException.class,()->new SectionWords(new Slice(key,0,2),List.of(0x10000)));
        assertThrows(NullPointerException.class,()->new SectionWords(new Slice(null,0,2),List.of(0)));
        var two=new SectionWords(new Slice(key,0,4),List.of(0x1800,0xFFFF));var one=new SectionWords(new Slice(key,0,2),List.of(2));
        assertThrows(IllegalArgumentException.class,()->new SectionColumns(0,two,two,two));
        assertThrows(IllegalArgumentException.class,()->new SectionColumns(3,two,two,two));
        assertThrows(IllegalArgumentException.class,()->new SectionColumns(2,two,one,two));
        assertThrows(IllegalArgumentException.class,()->new SectionColumns(2,two,two,one));
        assertThrows(IllegalArgumentException.class,()->new CameraSections(rows().subList(0,1),new SectionColumns(2,two,two,two)));
        var mutable=new ArrayList<>(List.of(0x1800,0xFFFF));var retained=new SectionWords(new Slice(key,0,4),mutable);mutable.clear();assertEquals(2,retained.words().size());
    }
    @ParameterizedTest @EnumSource(value=CdR5DataLayout.Programme.class,names={"R53C","R53D"})
    void everyUnusedInitialAndUpdateWordIsValidatedBeforeProductAdoption(CdR5DataLayout.Programme p)throws Exception {
        var r=reader(p);var cols=columns(r);
        for(var col:List.of(cols.initial(),cols.update()))for(int value:new int[]{18,19}) {
            var control=spy(r);var actual=r.loadViewForKey(col.source().key());
            doReturn(CdAssetBytes.copyOf(word(actual.copy(),4,value))).when(control).loadViewForKey(col.source().key());clearInvocations(control);
            assertEquals(value==19?"Section PLC ID outside native directory":"Section column operand differs",
                assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control,never()).loadViewForKey(p.range("BackgroundPointers"));
        }
    }
    @Test void shortUnusedInitialTailFailsAfterActiveRowsAndBeforeUnusedUpdateOrBackground()throws Exception {
        var r=reader(C);var c=columns(r);var threshold=spy(r.loadViewForKey(c.thresholds().source().key()));
        var initial=spy(CdAssetBytes.copyOf(Arrays.copyOf(r.loadViewForKey(c.initial().source().key()).copy(),4)));
        var update=spy(r.loadViewForKey(c.update().source().key()));var control=spy(r);
        doReturn(threshold).when(control).loadViewForKey(c.thresholds().source().key());
        doReturn(initial).when(control).loadViewForKey(c.initial().source().key());doReturn(update).when(control).loadViewForKey(c.update().source().key());
        clearInvocations(threshold,initial,update,control);
        assertThrows(IndexOutOfBoundsException.class,()->CdProgrammeLoadTables.decode(control));
        var order=inOrder(threshold,initial,update);
        order.verify(threshold).unsignedWord(0);order.verify(initial).unsignedWord(0);order.verify(update).unsignedWord(0);
        order.verify(threshold).unsignedWord(2);order.verify(initial).unsignedWord(2);order.verify(update).unsignedWord(2);order.verify(initial).unsignedWord(4);
        verify(update,never()).unsignedWord(4);verify(control,never()).loadViewForKey(C.range("BackgroundPointers"));
    }
    @Test void firstThresholdMismatchDoesNotBorrowInitialUpdateOrPackedRole()throws Exception {
        var r=reader(C);var c=columns(r);var control=spy(r);var key=c.thresholds().source().key();
        doReturn(CdAssetBytes.copyOf(word(r.loadViewForKey(key).copy(),0,0x1801))).when(control).loadViewForKey(key);clearInvocations(control);
        assertEquals("Section column operand differs",assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(control)).getMessage());
        verify(control,never()).loadViewForKey(c.initial().source().key());verify(control,never()).loadViewForKey(c.update().source().key());
        verify(control,never()).loadView(CdProgrammeReader.LoadRole.SECTIONS);verify(control,never()).loadViewForKey(C.range("BackgroundPointers"));
    }
    @Test void initialBorrowErrorEscapesUnchangedBeforeUpdateAndBackground()throws Exception {
        var r=reader(C);var c=columns(r);var control=spy(r);var original=new AssertionError("constructed column read fault");
        doThrow(original).when(control).loadViewForKey(c.initial().source().key());clearInvocations(control);
        assertSame(original,assertThrows(AssertionError.class,()->CdProgrammeLoadTables.decode(control)));
        verify(control).loadViewForKey(c.thresholds().source().key());verify(control,never()).loadViewForKey(c.update().source().key());
        verify(control,never()).loadViewForKey(C.range("BackgroundPointers"));
    }
    @Test void foreignColumnIdentityRejectsBeforeLaterBorrows()throws Exception {
        var r=reader(C);var c=columns(r);var key=C.range("SectionGfxRangesRead");
        var equal=new CdR5DataLayout.Range(key.key(),key.fileOffset(),key.length(),key.sha256(),key.nativePath(),key.line());assertEquals(key,equal);
        var bad=new SectionColumns(2,new SectionWords(new Slice(equal,0,4),c.thresholds().words()),c.initial(),c.update());
        var control=format(r,new CameraSections(rows(),bad));assertThrows(IllegalArgumentException.class,()->CdProgrammeLoadTables.decode(control));
        verify(control,never()).loadViewForKey(c.initial().source().key());verify(control,never()).loadViewForKey(c.update().source().key());
    }
    @Test void failedUnusedValidationIsNotCachedAndCanRetryWithTheRealSource()throws Exception {
        try(var source=CdProgramAssets.open(TestCdR5DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            var r=CdProgrammeReader.open(source,C.payload().stage());var c=columns(r);var key=c.update().source().key();var good=r.loadViewForKey(key);var control=spy(r);
            doReturn(CdAssetBytes.copyOf(word(good.copy(),4,18))).when(control).loadViewForKey(key);clearInvocations(control);
            assertThrows(IllegalArgumentException.class,control::loadTables);assertThrows(IllegalArgumentException.class,control::loadTables);
            verify(control,never()).loadViewForKey(C.range("BackgroundPointers"));
            doReturn(good).when(control).loadViewForKey(key);var success=control.loadTables();assertSame(success,control.loadTables());
            assertEquals(rows(),((CameraSections)success.sectionProgramme()).rows());verify(control,times(3)).loadViewForKey(key);
            assertNotSame(success,r.loadTables());
        }
    }
    @Test void allTwentyTwoDefinitionsKeepTheirSectionAlternativeAndLegacyPackedContract()throws Exception {
        var payloads=new ArrayList<>(List.of(CdStagePayload.R31A,CdStagePayload.R32A));
        for(var p:CdR4DataLayout.Programme.values())payloads.add(p.payload());for(var p:CdR5DataLayout.Programme.values())payloads.add(p.payload());
        int separate=0,packed=0,rts=0;
        try(var source=CdProgramAssets.open(TestCdR5DataLayout.actual().cue(),Optional.of(CdReleaseVariant.USA),Optional.empty())) {
            for(var payload:payloads) {
                var r=CdProgrammeReader.open(source,payload.stage());var expected=r.definition().load().sections();var actual=r.loadTables().sectionProgramme();
                assertEquals(expected,actual,payload.name());
                if(actual instanceof CameraSections camera) {
                    if(camera.operands() instanceof SectionColumns)separate++;
                    else {packed++;assertSame(PackedSectionRows.INSTANCE,camera.operands());assertEquals(new CameraSections(camera.rows()),camera);}
                } else {rts++;assertInstanceOf(InitQueueUpdateRts.class,actual);}
            }
        }
        assertEquals(2,separate);assertEquals(22,packed+separate+rts);assertTrue(packed>0);assertTrue(rts>0);
    }
}
