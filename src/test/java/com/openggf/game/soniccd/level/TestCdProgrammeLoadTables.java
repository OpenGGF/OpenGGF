package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.openggf.game.soniccd.level.CdProgrammeReader.LoadRole.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

/** Actual independent media ingress plus explicitly synthetic parser fault controls. No live art service. */
class TestCdProgrammeLoadTables {
    private static final Map<CdStagePayload, CdProgrammeReader> READERS = new EnumMap<>(CdStagePayload.class);

    private static Path cue() throws Exception {
        String root = System.getProperty("soniccd.media.root");
        assumeTrue(root != null && !root.isBlank(), "Optional original Sonic CD media absent");
        try (var files = Files.list(Path.of(root).resolve("USA"))) {
            var paths = files.filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".cue")).toList();
            assertEquals(1, paths.size()); return paths.getFirst();
        }
    }
    private static CdProgrammeReader fresh(CdStagePayload payload) throws Exception {
        try (var actual = CdProgramAssets.open(cue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            return CdProgrammeReader.open(actual, payload.stage());
        }
    }
    private static synchronized CdProgrammeReader reader(CdStagePayload payload) throws Exception {
        var r = READERS.get(payload);
        if (r == null) { r = fresh(payload); READERS.put(payload, r); }
        return r;
    }

    @Test void bothActualProgrammesRetainAllRootsBodiesSourcesOutputsAndAliasIdentity() throws Exception {
        for (var payload : List.of(CdStagePayload.R31A, CdStagePayload.R32A)) {
            var reader = reader(payload); var tables = reader.loadTables();
            assertSame(tables, reader.loadTables());
            assertEquals(19, tables.plcs().size());
            var raw = reader.loadView(PLC);
            var bodies = new HashMap<Integer, CdProgrammeLoadTables.Body>();
            for (int id = 0; id < 19; id++) {
                var list = tables.plcs().get(id); var body = list.body();
                int at = (short) raw.unsignedWord(id * 2);
                assertEquals(id, list.nativeId());
                assertEquals(reader.loadFileOffset(PLC) + at, body.fileOffset());
                assertEquals((short) raw.unsignedWord(at) + 1, body.entries().size());
                var prior = bodies.putIfAbsent(at, body);
                if (prior != null) assertSame(prior, body);
                var inverse = ByteBuffer.allocate(2 + body.entries().size() * 6);
                inverse.putShort((short) (body.entries().size() - 1));
                for (var entry : body.entries()) {
                    assertSame(entry.source(), reader.loadSource(entry.sourceNativeAddress()));
                    assertEquals(reader.loadOutputBytes(entry.source()), entry.outputBytes());
                    assertTrue(entry.outputBytes() <= 65536 - entry.vramAddress());
                    inverse.putInt((int) entry.sourceNativeAddress()).putShort((short) entry.vramAddress());
                }
                assertArrayEquals(raw.slice(at, inverse.capacity()).copy(), inverse.array());
            }
            assertEquals(payload == CdStagePayload.R31A ? 14 : 8, bodies.size());
            assertEquals(payload == CdStagePayload.R31A ? 37 : 35, tables.encounteredSources().size());
            assertThrows(UnsupportedOperationException.class, () -> tables.plcs().clear());
            assertThrows(UnsupportedOperationException.class, () -> tables.plcs().getFirst().body().entries().clear());
        }
    }

    @Test void r31RealCallerAdaptsCommonTablesWithoutRepeatingParsingOrLosingAliases() throws Exception {
        var assets = TestCdR31aAssets.actual().assets(); var tables = assets.internalReader().loadTables();
        var metadata = assets.loadMetadata();
        for (int id = 0; id < 19; id++) {
            var actual = metadata.plcs().get(id); var common = tables.plcs().get(id);
            assertEquals(common.body().fileOffset(), actual.bodyFileOffset());
            assertEquals(common.body().entries().stream().map(CdProgrammeLoadTables.Entry::source).toList(),
                    actual.entries().stream().map(CdR31aLoadMetadata.PlcEntry::source).toList());
        }
        assertSame(metadata.plcs().get(0).entries(), metadata.plcs().get(3).entries());
        assertSame(metadata.plcs().get(2).entries(), metadata.plcs().get(17).entries());
        assertSame(tables.sections(), metadata.sections());
        assertSame(tables.backgroundPointers(), metadata.backgroundPointers());
        assertEquals(Arrays.asList(CdR31aLoadMetadata.SourceId.values()), tables.encounteredSources());
    }

    @Test void r32DirectMediaConsumerMatchesIndependentDeclarationsIncludingOverlappingDestinations() throws Exception {
        CdProgrammeLoadTables actual;
        try (var programs = CdProgramAssets.open(cue(), Optional.of(CdReleaseVariant.USA), Optional.empty())) {
            actual = CdR32aLoadLayout.readTables(programs);
        }
        for (var expected : CdR32aLoadLayout.PLC_LISTS) {
            var list = actual.plcs().get(expected.nativeId());
            assertEquals(expected.fileOffset(), list.body().fileOffset());
            assertEquals(expected.entries(), list.body().entries().stream()
                    .map(e -> new CdR32aLoadLayout.PlcEntry((CdR32aLoadLayout.SourceId) e.source(), e.vramAddress())).toList());
        }
        var entries = actual.plcs().get(2).body().entries();
        assertSame(CdR32aLoadLayout.SourceId.TELEPORTER_GFX, entries.get(7).source());
        assertSame(CdR32aLoadLayout.SourceId.POCKET_GFX_A, entries.get(8).source());
        assertEquals(0x75E0, entries.get(7).vramAddress()); assertEquals(0x75E0, entries.get(8).vramAddress());
        assertEquals(41, CdR32aLoadLayout.SourceId.values().length);
        assertFalse(actual.encounteredSources().contains(CdR32aLoadLayout.SourceId.STAGE_BLOCKS));
        assertFalse(actual.encounteredSources().contains(CdR32aLoadLayout.SourceId.CAPSULE_GFX));
        assertEquals(CdR32aLoadLayout.SECTIONS, actual.sections());
        var r = reader(CdStagePayload.R32A);
        assertThrows(UnsupportedOperationException.class, () -> r.nativeInput(CdR32aLoadLayout.SourceId.STAGE_GFX));
        assertThrows(IllegalArgumentException.class, () -> r.loadOutputBytes(CdR31aLoadMetadata.SourceId.STAGE_GFX));
    }

    @Test void palettesCyclesAndIndependentSectionsInvertToEachActualSource() throws Exception {
        int[] scriptLengths = {8,34,32,6}, colorLengths = {6,4,6,4};
        for (var payload : List.of(CdStagePayload.R31A, CdStagePayload.R32A)) {
            var r = reader(payload); var tables = r.loadTables(); var inverse = ByteBuffer.allocate(48);
            for (int id = 0; id < 6; id++) {
                var row = tables.palettes().get(id);
                assertEquals(id, row.nativeId());
                assertEquals((id < 4 ? 0xFB00 : 0xFB20), row.destinationWord());
                assertEquals(id == 3 ? 7 : id < 3 ? 31 : 23, row.longwordCounter());
                assertEquals((row.longwordCounter() + 1) * 4, row.bytes().length());
                assertSame(r.view(row.source()), row.bytes());
                inverse.putInt((int) row.sourceNativeAddress()).putShort((short) row.destinationWord()).putShort((short) row.longwordCounter());
                if (payload == CdStagePayload.R32A) {
                    var expected = CdR32aLoadLayout.PALETTES.get(id);
                    assertSame(expected.source(), row.source()); assertEquals(expected.destinationWord(), row.destinationWord());
                    assertEquals(expected.longwordCounter(), row.longwordCounter());
                }
            }
            assertArrayEquals(r.loadView(PALETTE).copy(), inverse.array());
            assertSame(tables.palettes().get(0).bytes(), tables.palettes().get(1).bytes());
            var cycles = ByteBuffer.allocate(100);
            for (int i = 0; i < 4; i++) {
                var cycle = tables.cycles().get(i);
                assertEquals(scriptLengths[i], cycle.script().length()); assertEquals(colorLengths[i], cycle.colors().length());
                cycles.put(cycle.script().copy()).put(cycle.colors().copy());
            }
            assertArrayEquals(r.loadView(CYCLES).copy(), cycles.array());
            int n = payload == CdStagePayload.R31A ? 4 : 2;
            var section = ByteBuffer.allocate(n * 6);
            for (var row : tables.sections()) section.putShort((short) row.rightThresholdWord());
            for (var row : tables.sections()) section.putShort((short) row.updatePlcId());
            for (var row : tables.sections()) section.putShort((short) row.initialPlcId());
            assertArrayEquals(r.loadView(SECTIONS).copy(), section.array());
            assertEquals(List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718), tables.backgroundPointers());
        }
    }

    private static CdProgrammeReader changed(CdProgrammeReader actual, CdProgrammeReader.LoadRole role, byte[] bytes) {
        var spy = spy(actual); doReturn(CdAssetBytes.copyOf(bytes)).when(spy).loadView(role); clearInvocations(spy); return spy;
    }
    private static byte[] word(byte[] bytes, int offset, int value) {
        var copy = bytes.clone(); ByteBuffer.wrap(copy).putShort(offset, (short) value); return copy;
    }
    @Test void signedInvalidRootsRejectBeforeCountSourceOrLaterTables() throws Exception {
        var r = reader(CdStagePayload.R31A); var raw = r.loadView(PLC).copy();
        for (int value : new int[]{0x8000,0xFFFF,0,37,39,534}) {
            var control = changed(r, PLC, word(raw, 0, value));
            assertEquals("Signed PLC root outside finite body span", assertThrows(IllegalArgumentException.class,
                    () -> CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control, never()).loadSource(anyLong()); verify(control, never()).loadView(PALETTE);
        }
    }
    @Test void signedMinusOneAndFiniteCountsRejectBeforeAnyRows() throws Exception {
        var r = reader(CdStagePayload.R32A); var raw = r.loadView(PLC).copy();
        for (int value : new int[]{0xFFFF,0x8000,15,32767}) {
            var control = changed(r, PLC, word(raw, 38, value));
            assertEquals("PLC finite counter/extent differs", assertThrows(IllegalArgumentException.class,
                    () -> CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control, never()).loadSource(anyLong()); verify(control, never()).loadView(PALETTE);
        }
        var control = changed(r, PLC, Arrays.copyOf(word(raw,38,1), 46)); // second row beyond span
        assertThrows(IllegalArgumentException.class, () -> CdProgrammeLoadTables.decode(control));
        verify(control, never()).loadSource(anyLong());
    }
    @Test void fullForeignSourcePointerWinsBeforeBadDestinationAndLaterTables() throws Exception {
        var r = reader(CdStagePayload.R31A); var raw = r.loadView(PLC).copy();
        ByteBuffer.wrap(raw).putInt(40, 0x80237BEE).putShort(44, (short) 1);
        var control = changed(r, PLC, raw);
        assertEquals("R31A unknown PLC compressed source: 80237bee", assertThrows(IllegalArgumentException.class,
                () -> CdProgrammeLoadTables.decode(control)).getMessage());
        verify(control, never()).loadOutputBytes(any()); verify(control, never()).loadView(PALETTE);
    }
    @Test void outputOverflowAndUnalignedDestinationFailAfterReachedSourceBeforeNextRow() throws Exception {
        var r = reader(CdStagePayload.R31A); var raw = r.loadView(PLC).copy();
        for (int value : new int[]{1,0xFFE0}) {
            var control = changed(r, PLC, word(raw,44,value));
            assertEquals("PLC output/VRAM range differs", assertThrows(IllegalArgumentException.class,
                    () -> CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control).loadSource(0x237BEEL); verify(control).loadOutputBytes(CdR31aLoadMetadata.SourceId.STAGE_GFX);
            verify(control, times(1)).loadSource(anyLong()); verify(control, never()).loadView(PALETTE);
        }
    }
    @Test void truncatedDirectoryAndBodyAreReachedFailuresWithoutSyntheticPadding() throws Exception {
        var r = reader(CdStagePayload.R31A); var raw = r.loadView(PLC).copy();
        var control = changed(r, PLC, Arrays.copyOf(raw,1));
        assertThrows(IndexOutOfBoundsException.class, () -> CdProgrammeLoadTables.decode(control));
        verify(control, never()).loadSource(anyLong()); verify(control, never()).loadView(PALETTE);
        var c = changed(r, PLC, Arrays.copyOf(raw,39));
        assertThrows(IllegalArgumentException.class, () -> CdProgrammeLoadTables.decode(c));
        verify(c, never()).loadSource(anyLong());
    }
    @Test void aliasMismatchIsRejectedEvenWhenBodiesAndEncounterSetStillMatch() throws Exception {
        var r = reader(CdStagePayload.R32A); var raw = r.loadView(PLC).copy();
        var control = changed(r, PLC, word(raw, 6, 0x34)); // id3 aliases standard instead of stage
        assertEquals("PLC native root/alias order differs", assertThrows(IllegalArgumentException.class,
                () -> CdProgrammeLoadTables.decode(control)).getMessage());
        verify(control, never()).loadView(PALETTE);
    }
    @Test void paletteFaultDoesNotBorrowColorsOrReachCyclesAndSections() throws Exception {
        var r = reader(CdStagePayload.R32A); var raw = r.loadView(PALETTE).copy();
        for (var bytes : List.of(word(raw,4,0xFB80), word(raw,6,0xFFFF))) {
            var control = changed(r, PALETTE, bytes);
            assertEquals("Actual palette operand differs", assertThrows(IllegalArgumentException.class,
                    () -> CdProgrammeLoadTables.decode(control)).getMessage());
            verify(control, never()).loadView(PAL_TITLE); verify(control, never()).loadView(CYCLES);
            verify(control, never()).loadView(SECTIONS);
        }
    }
    @Test void cycleAndSectionFaultsStopBeforeUnreachedInputs() throws Exception {
        var r = reader(CdStagePayload.R32A);
        var cycle = changed(r, CYCLES, Arrays.copyOf(r.loadView(CYCLES).copy(),99));
        assertThrows(IndexOutOfBoundsException.class, () -> CdProgrammeLoadTables.decode(cycle));
        verify(cycle, never()).loadView(SECTIONS);
        var section = changed(r, SECTIONS, Arrays.copyOf(r.loadView(SECTIONS).copy(),9));
        assertThrows(IndexOutOfBoundsException.class, () -> CdProgrammeLoadTables.decode(section));
        verify(section, never()).loadView(BG_POINTERS);
        var wrong = changed(r, SECTIONS, word(r.loadView(SECTIONS).copy(),0,0xFFFF));
        assertEquals("Section range/initial/update operands differ", assertThrows(IllegalArgumentException.class,
                () -> CdProgrammeLoadTables.decode(wrong)).getMessage());
        verify(wrong, never()).loadView(BG_POINTERS);
    }
    @Test void failedParsePublishesNoCachedSuccessAndEqualSourcesHaveSeparateResults() throws Exception {
        var r = fresh(CdStagePayload.R32A); var raw = r.loadView(PLC);
        var control = changed(r, PLC, word(raw.copy(),0,0xFFFF));
        assertThrows(IllegalArgumentException.class, control::loadTables);
        assertThrows(IllegalArgumentException.class, control::loadTables);
        doReturn(raw).when(control).loadView(PLC);
        var result = control.loadTables(); assertSame(result, control.loadTables());
        assertNotSame(result, r.loadTables());
        assertNotSame(result.plcs().get(0).body(), r.loadTables().plcs().get(0).body());
        assertThrows(NullPointerException.class, () -> CdProgrammeLoadTables.decode(null));
    }
    @Test void paletteMalformedRowKeepsDisplacedConditionalReadOrder() throws Exception {
        var r = reader(CdStagePayload.R31A); var raw = r.loadView(PALETTE).copy();
        var badPointer = Arrays.copyOf(raw,4); ByteBuffer.wrap(badPointer).putInt(0,0x8020055C);
        var pointer = changed(r, PALETTE, badPointer);
        assertEquals("Actual palette operand differs", assertThrows(IllegalArgumentException.class,
                () -> CdProgrammeLoadTables.decode(pointer)).getMessage());
        var goodPointerOnly = changed(r, PALETTE, Arrays.copyOf(raw,4));
        assertThrows(IndexOutOfBoundsException.class, () -> CdProgrammeLoadTables.decode(goodPointerOnly));
        var badDestination = changed(r, PALETTE, Arrays.copyOf(word(raw,4,0xFB80),6));
        assertEquals("Actual palette operand differs", assertThrows(IllegalArgumentException.class,
                () -> CdProgrammeLoadTables.decode(badDestination)).getMessage());
        var goodPointerAndDestination = changed(r, PALETTE, Arrays.copyOf(raw,6));
        assertThrows(IndexOutOfBoundsException.class, () -> CdProgrammeLoadTables.decode(goodPointerAndDestination));
        for (var control : List.of(pointer,goodPointerOnly,badDestination,goodPointerAndDestination)) {
            verify(control, never()).loadView(PAL_TITLE); verify(control, never()).loadView(CYCLES);
        }
    }

}
