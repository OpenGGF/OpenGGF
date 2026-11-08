package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.nio.file.Path;
import java.util.*;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.EnumSource;
import static org.mockito.Mockito.*;
import static com.openggf.game.soniccd.level.CdProgrammeDefinition.*;
import static com.openggf.game.soniccd.level.CdProgrammeReader.LoadRole.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

/** Original-media DATA, never palette/queue service or live stage admission. */
class TestCdR7LoadFormats {
    private static CdProgramAssets source;
    private static synchronized CdProgramAssets media()throws Exception {
        if(source==null)source=CdProgramAssets.open(Path.of(System.getProperty("soniccd.media.root")).resolve("USA/Sonic CD (US).cue"),Optional.of(CdReleaseVariant.USA),Optional.empty());
        return source;
    }
    @AfterAll static void close()throws Exception {if(source!=null)source.close();source=null;}
    @ParameterizedTest @ValueSource(strings={"R71D","R72D","R73C","R73D"})
    void formerlyBlockedOriginalLoadAccess(String id)throws Exception {
        var reader=CdProgrammeReader.open(media(),CdStageKey.parse(id));
        var tables=reader.loadTables();assertSame(tables,reader.loadTables());
        assertEquals(19,tables.plcs().size());assertFalse(tables.palettes().isEmpty());
    }

    @ParameterizedTest @EnumSource(CdR7DataLayout.Programme.class)
    void everyOriginalDescriptorAndCanonicalCycleKeepsNativeIndexAndByteInverse(CdR7DataLayout.Programme p)throws Exception {
        var r=CdProgrammeReader.open(media(),p.payload().stage());var tables=r.loadTables();var raw=r.view(p.range("PaletteDirectory"));
        var inverse=ByteBuffer.allocate(raw.length());assertEquals(p.palettes().size(),tables.palettes().size());
        for(int id=0;id<tables.palettes().size();id++) {
            var row=tables.palettes().get(id);var expected=p.palettes().get(id);assertEquals(id,row.nativeId());assertEquals(id,expected.slot());
            assertEquals(raw.unsignedLong(id*8),row.sourceNativeAddress());assertEquals(raw.unsignedWord(id*8+4),row.destinationWord());assertEquals(raw.unsignedWord(id*8+6),row.longwordCounter());
            inverse.putInt((int)row.sourceNativeAddress()).putShort((short)row.destinationWord()).putShort((short)row.longwordCounter());
            if(expected.unmapped()) {
                assertSame(UnavailablePaletteSource.ZERO_ADDRESS,row.source());assertEquals(0,row.sourceNativeAddress());assertEquals(0,row.destinationWord());assertEquals(65535,row.longwordCounter());assertTrue(row.sourceBytes().isEmpty());
                assertEquals("Native zero-address palette read is unavailable",assertThrows(UnsupportedOperationException.class,row::bytes).getMessage());
                assertThrows(IllegalArgumentException.class,()->r.view(row.source()));
            } else {
                assertSame(p.range("PaletteRead"+id),row.source());assertSame(r.view(row.source()),row.bytes());assertSame(row.bytes(),row.sourceBytes().orElseThrow());
                assertEquals((row.longwordCounter()+1L)*4,row.bytes().length());assertArrayEquals(media().mmd(p.file()).readFile(p.range("PaletteRead"+id).fileOffset(),row.bytes().length()),row.bytes().copy());
            }
        }
        assertArrayEquals(raw.copy(),inverse.array());
        if(p.name().endsWith("D")) {
            assertEquals(7,tables.palettes().size());assertEquals(6,tables.palettes().get(6).nativeId());assertEquals(12,tables.palettes().get(6).bytes().length());
            assertEquals(CycleProgramme.SOURCE_ACT_GATED,tables.cycleProgramme());assertEquals(7,tables.cycles().size());
            String[][] keys={{"Scalar0","Scalar1"},{"Scalar2","Scalar3"},{"Scalar4","Scalar5"},{"Scalar6","Scalar7"},{"Scalar10","Scalar11"},{"Scalar12","Scalar13"},{"Scalar14","Scalar13"}};
            for(int i=0;i<keys.length;i++) {assertSame(r.view(p.range(keys[i][0])),tables.cycles().get(i).script());assertSame(r.view(p.range(keys[i][1])),tables.cycles().get(i).colors());}
            assertSame(tables.cycles().get(5).colors(),tables.cycles().get(6).colors());
        }
    }
    private static CdProgrammeReader paletteControl(CdProgrammeReader r,byte[] bytes) {
        var c=spy(r);doReturn(CdAssetBytes.copyOf(bytes)).when(c).loadView(PALETTE);clearInvocations(c);return c;
    }
    private static byte[] changed(byte[] original,int offset,int bytes,int value,int length) {
        var raw=Arrays.copyOf(original,length);var b=ByteBuffer.wrap(raw);if(bytes==4)b.putInt(offset,value);else b.putShort(offset,(short)value);return raw;
    }
    @ParameterizedTest @ValueSource(strings={"R71D","R72D","R73D"})
    void zeroDescriptorFaultsKeepPointerDestinationCounterThenNextSlotOrder(String id)throws Exception {
        var p=CdR7DataLayout.Programme.valueOf(id);var r=CdProgrammeReader.open(media(),p.payload().stage());var raw=r.loadView(PALETTE).copy();
        for(var bytes:List.of(changed(raw,40,4,0x200000,44),changed(raw,44,2,0xFB20,46),changed(raw,46,2,0,48))) {
            var c=paletteControl(r,bytes);assertEquals("Actual palette operand differs",assertThrows(IllegalArgumentException.class,c::loadTables).getMessage());
            verify(c,never()).loadViewForKey(p.range("PaletteRead6"));verify(c,never()).fileOffset(UnavailablePaletteSource.ZERO_ADDRESS);verify(c,never()).loadViewForKey(UnavailablePaletteSource.ZERO_ADDRESS);verify(c,never()).loadView(SECTIONS);
        }
        for(int length:new int[]{44,46,48}) { // missing destination, counter, then actual slot6 pointer respectively
            var c=paletteControl(r,Arrays.copyOf(raw,length));assertThrows(IndexOutOfBoundsException.class,c::loadTables);
            verify(c,never()).loadViewForKey(p.range("PaletteRead6"));verify(c,never()).fileOffset(UnavailablePaletteSource.ZERO_ADDRESS);verify(c,never()).loadViewForKey(UnavailablePaletteSource.ZERO_ADDRESS);
        }
        var c=paletteControl(r,changed(raw,40,4,0x200000,raw.length));var first=new AssertionError("first original palette source unavailable");
        doThrow(first).when(c).loadViewForKey(p.range("PaletteRead0"));assertSame(first,assertThrows(AssertionError.class,c::loadTables));
        doReturn(r.view(p.range("PaletteRead0"))).when(c).loadViewForKey(p.range("PaletteRead0"));assertThrows(IllegalArgumentException.class,c::loadTables);
        doReturn(CdAssetBytes.copyOf(raw)).when(c).loadView(PALETTE);var success=c.loadTables();clearInvocations(c);assertSame(success,c.loadTables());verify(c,never()).loadView(any());
    }
    @ParameterizedTest @ValueSource(strings={"R73C","R73D"})
    void trueRtsSectionsNeverReadAnAbsentRoleAndRetainLaterFirstFault(String id)throws Exception {
        var p=CdR7DataLayout.Programme.valueOf(id);var r=CdProgrammeReader.open(media(),p.payload().stage());var c=spy(r);clearInvocations(c);
        assertThrows(UnsupportedOperationException.class,()->r.loadKey(SECTIONS));assertSame(NoSections.INSTANCE,c.loadTables().sectionProgramme());
        verify(c,never()).loadView(SECTIONS);verify(c,never()).loadView(CYCLES);assertThrows(UnsupportedOperationException.class,c.loadTables()::sections);
        var unavailable=spy(CdProgrammeReader.open(media(),p.payload().stage()));var reached=new AssertionError("reached actual copied-pointer source unavailable");
        doThrow(reached).when(unavailable).loadViewForKey(p.range("CopiedPlanePointers"));assertSame(reached,assertThrows(AssertionError.class,unavailable::loadTables));verify(unavailable,never()).loadView(SECTIONS);
        doReturn(r.view(p.range("CopiedPlanePointers"))).when(unavailable).loadViewForKey(p.range("CopiedPlanePointers"));assertSame(NoSections.INSTANCE,unavailable.loadTables().sectionProgramme());
    }
    @Test void foreignPaletteAndChangedBackgroundRemainActualFirstFaults()throws Exception {
        var p=CdR7DataLayout.Programme.R71D;var r=CdProgrammeReader.open(media(),p.payload().stage());var foreign=CdProgrammeReader.open(media(),CdStagePayload.R73C.stage());
        var c=paletteControl(r,foreign.loadView(PALETTE).copy());assertEquals("Actual palette operand differs",assertThrows(IllegalArgumentException.class,c::loadTables).getMessage());verify(c,never()).loadViewForKey(p.range("PaletteRead0"));
        assertThrows(IllegalArgumentException.class,()->r.view(CdR7DataLayout.Programme.R72D.range("PaletteRead6")));
        var pointers=spy(CdProgrammeReader.open(media(),p.payload().stage()));var key=p.range("CopiedPlanePointers");var raw=r.view(key).copy();raw[0]^=1;doReturn(CdAssetBytes.copyOf(raw)).when(pointers).loadViewForKey(key);
        assertEquals("Background pointer operands differ",assertThrows(IllegalArgumentException.class,pointers::loadTables).getMessage());verify(pointers,never()).loadViewForKey(p.range("LivePlanePointers"));
    }
    @Test void genericSemanticAdditionsKeepOldConstructedPaletteAndEnumPrefix()throws Exception {
        var bytes=CdAssetBytes.copyOf(new byte[]{0,1,2,3});var key=new Object();var old=new CdProgrammeLoadTables.Palette(0,key,0x200100,0xFB00,0,bytes);
        assertSame(bytes,old.bytes());assertSame(bytes,old.sourceBytes().orElseThrow());
        assertTrue(java.lang.reflect.Modifier.isPublic(CdProgrammeLoadTables.Palette.class.getDeclaredMethod("bytes").getModifiers()));
        var absent=new CdProgrammeLoadTables.Palette(5,UnavailablePaletteSource.ZERO_ADDRESS,0,0,65535,Optional.empty());assertThrows(UnsupportedOperationException.class,absent::bytes);
        assertEquals(List.of("STANDARD_FOUR","UPPER_SIX_OR_STANDARD_FOUR","STANDARD_FOUR_WITH_RETAINED_UPPER","THREE_SOURCE_ACT_GATED","R5_PRESENT_BACKGROUND_CONVEYOR","R5_PAST_BACKGROUND_CONVEYOR","R5_FUTURE_BOSS_GATED_CONVEYOR","FOUR_WITH_BOSS_COLOR_OVERRIDE","THREE_MIRRORED","SOURCE_RTS"),Arrays.stream(CycleProgramme.values()).limit(10).map(Enum::name).toList());
        assertEquals("SOURCE_ACT_GATED",CycleProgramme.values()[10].name());
    }
}
