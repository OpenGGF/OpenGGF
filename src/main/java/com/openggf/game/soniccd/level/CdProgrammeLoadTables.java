package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import java.util.*;

/**
 * Pure parsing of the closed original-USA R31A/R32A load tables. The retained reader
 * supplies every byte and source key. These values confer no service/publication authority.
 * Native N1ecc5f3a: PLCLists/PLC_Stage in Collision Chaos Graphics Lists (Act 1
 * Present / Act 2 Variant); PaletteTable and SectionGfxRanges in their own programmes.
 * This validates the finite original tables, not arbitrary native queue contents.
 */
final class CdProgrammeLoadTables {
    record Entry(Enum<?> source, long sourceNativeAddress, int outputBytes, int vramAddress) { }
    record Body(int fileOffset, List<Entry> entries) {
        Body { entries = List.copyOf(entries); }
    }
    record Plc(int nativeId, Body body) { }
    record Palette(int nativeId, Enum<?> source, long sourceNativeAddress,
                   int destinationWord, int longwordCounter, CdAssetBytes bytes) { }
    record Cycle(CdAssetBytes script, CdAssetBytes colors) { }

    private final List<Plc> plcs;
    private final List<Enum<?>> encounteredSources;
    private final List<Palette> palettes;
    private final List<Cycle> cycles;
    private final List<CdR11bLoadPlan.SectionArt> sections;
    private final List<Integer> backgroundPointers;

    private CdProgrammeLoadTables(CdProgrammeReader reader) {
        Objects.requireNonNull(reader, "actual retained programme reader").requireRetained();
        boolean r31 = reader.file() == CdNativeFile.R31A;
        // Definitions are finite source facts, never caller-supplied parsing instructions.
        int[] roots = r31 ? R31_ROOTS : R32_ROOTS;
        var raw = reader.loadView(CdProgrammeReader.LoadRole.PLC);
        var lists = new ArrayList<Plc>();
        var bodies = new LinkedHashMap<Integer, Body>();
        var encountered = new LinkedHashSet<Enum<?>>();
        for (int id = 0; id < 19; id++) {
            int at = (short) raw.unsignedWord(id * 2);
            if (at < 38 || (at & 1) != 0 || at > raw.length() - 2)
                throw new IllegalArgumentException("Signed PLC root outside finite body span");
            int count = (short) raw.unsignedWord(at) + 1;
            // No negative/empty lists occur in these independently authenticated originals.
            // Do not widen the old R31 finite metadata admission to general queue input.
            if (count < 1 || count > 15 || 6 * count > raw.length() - at - 2)
                throw new IllegalArgumentException("PLC finite counter/extent differs");
            var entries = new ArrayList<Entry>();
            for (int row = 0; row < count; row++) {
                int cursor = at + 2 + row * 6;
                long address = raw.unsignedLong(cursor); // full LONG, never mask a foreign pointer
                var source = reader.loadSource(address); // resolve before reaching destination
                int output = reader.loadOutputBytes(source);
                int destination = raw.unsignedWord(cursor + 4);
                if ((destination & 31) != 0 || output > 65536 - destination)
                    throw new IllegalArgumentException("PLC output/VRAM range differs");
                entries.add(new Entry(source, address, output, destination));
                encountered.add(source);
            }
            var body = new Body(reader.loadFileOffset(CdProgrammeReader.LoadRole.PLC) + at, entries);
            var alias = bodies.putIfAbsent(at, body);
            // Parse every reached alias, but retain one immutable body identity per actual root.
            if (alias != null && !alias.equals(body))
                throw new IllegalArgumentException("PLC alias bytes differ");
            lists.add(new Plc(id, alias == null ? body : alias));
        }
        if (bodies.size() != (r31 ? 14 : 8))
            throw new IllegalArgumentException("PLC distinct body inventory differs");
        for (int id = 0; id < 19; id++)
            if (lists.get(id).body().fileOffset() != roots[id])
                throw new IllegalArgumentException("PLC native root/alias order differs");
        var expected = r31 ? List.of(CdR31aLoadMetadata.SourceId.values()) : r32Encounter();
        if (!List.copyOf(encountered).equals(expected))
            throw new IllegalArgumentException("PLC source encounter order differs");
        plcs = List.copyOf(lists);
        encounteredSources = List.copyOf(encountered);

        var table = reader.loadView(CdProgrammeReader.LoadRole.PALETTE);
        var parsedPalettes = new ArrayList<Palette>();
        for (int id = 0; id < 6; id++) {
            var role = PALETTE_ROLES[id];
            var source = reader.loadKey(role);
            long address = table.unsignedLong(id * 8);
            if (address != 0x200000L + reader.loadFileOffset(role))
                throw new IllegalArgumentException("Actual palette operand differs");
            int destination = table.unsignedWord(id * 8 + 4);
            if (destination != (id < 4 ? 0xFB00 : 0xFB20))
                throw new IllegalArgumentException("Actual palette operand differs");
            int counter = table.unsignedWord(id * 8 + 6);
            if (counter != (id == 3 ? 7 : id < 3 ? 31 : 23))
                throw new IllegalArgumentException("Actual palette operand differs");
            var bytes = reader.loadView(role); // reached only after complete row validation
            if ((counter + 1L) * 4 != bytes.length())
                throw new IllegalArgumentException("Palette source/count extent differs");
            parsedPalettes.add(new Palette(id, source, address, destination, counter, bytes));
        }
        palettes = List.copyOf(parsedPalettes);
        var cycle = reader.loadView(CdProgrammeReader.LoadRole.CYCLES);
        var parsedCycles = new ArrayList<Cycle>();
        for (int i = 0; i < 4; i++)
            parsedCycles.add(new Cycle(cycle.slice(SCRIPTS[i], COLORS[i] - SCRIPTS[i]),
                    cycle.slice(COLORS[i], ENDS[i] - COLORS[i])));
        cycles = List.copyOf(parsedCycles);

        var section = reader.loadView(CdProgrammeReader.LoadRole.SECTIONS);
        int n = r31 ? 4 : 2;
        var parsedSections = new ArrayList<CdR11bLoadPlan.SectionArt>();
        for (int i = 0; i < n; i++) {
            int threshold = section.unsignedWord(i * 2);
            // Preserve the displaced R31 constructor's reached read order: range, initial, update.
            int initial = section.unsignedWord(n * 4 + i * 2);
            int update = section.unsignedWord(n * 2 + i * 2);
            parsedSections.add(new CdR11bLoadPlan.SectionArt(threshold, initial, update));
        }
        sections = List.copyOf(parsedSections);
        if (!sections.equals(r31 ? R31_SECTIONS : CdR32aLoadLayout.SECTIONS))
            throw new IllegalArgumentException("Section range/initial/update operands differ");
        var pointers = reader.loadView(CdProgrammeReader.LoadRole.BG_POINTERS);
        var parsedPointers = new ArrayList<Integer>();
        for (int i = 0; i < 4; i++) parsedPointers.add((int) pointers.unsignedLong(i * 4));
        backgroundPointers = List.copyOf(parsedPointers);
    }

    static CdProgrammeLoadTables decode(CdProgrammeReader reader) { return new CdProgrammeLoadTables(reader); }
    List<Plc> plcs() { return plcs; }
    List<Enum<?>> encounteredSources() { return encounteredSources; }
    List<Palette> palettes() { return palettes; }
    List<Cycle> cycles() { return cycles; }
    List<CdR11bLoadPlan.SectionArt> sections() { return sections; }
    List<Integer> backgroundPointers() { return backgroundPointers; }

    private static List<Enum<?>> r32Encounter() {
        // Independent declarative layout, not substitute parsed bytes. Excludes six non-PLC sources.
        var ids = new LinkedHashSet<Enum<?>>();
        for (var list : CdR32aLoadLayout.PLC_LISTS)
            for (var entry : list.entries()) ids.add(entry.source());
        return List.copyOf(ids);
    }
    private static final int[] R31_ROOTS = {
        0xFAF6,0xFB04,0xFB60,0xFAF6,0xFB74,0xFBBE,0xFC02,0xFC4C,0xFC54,
        0xFC9E,0xFCA6,0xFCB4,0xFCC2,0xFB60,0xFB60,0xFB60,0xFCCA,0xFB60,0xFCD2
    };
    private static final int[] R32_ROOTS = {
        0xF1A0,0xF1AE,0xF20A,0xF1A0,0xF25A,0xF2AA,0xF2B2,0xF20A,0xF20A,
        0xF20A,0xF20A,0xF20A,0xF20A,0xF20A,0xF20A,0xF20A,0xF2BA,0xF20A,0xF2C2
    };
    private static final int[] SCRIPTS = {0,14,52,90}, COLORS = {8,48,84,96}, ENDS = {14,52,90,100};
    private static final CdProgrammeReader.LoadRole[] PALETTE_ROLES = {
        CdProgrammeReader.LoadRole.PAL_TITLE, CdProgrammeReader.LoadRole.PAL_TITLE,
        CdProgrammeReader.LoadRole.PAL_SELECT, CdProgrammeReader.LoadRole.PAL_SONIC,
        CdProgrammeReader.LoadRole.PAL_LEVEL, CdProgrammeReader.LoadRole.PAL_END
    };
    private static final List<CdR11bLoadPlan.SectionArt> R31_SECTIONS = List.of(
        new CdR11bLoadPlan.SectionArt(0x4A0,2,7), new CdR11bLoadPlan.SectionArt(0x1000,4,8),
        new CdR11bLoadPlan.SectionArt(0x1800,5,9), new CdR11bLoadPlan.SectionArt(0xFFFF,6,10));
}
