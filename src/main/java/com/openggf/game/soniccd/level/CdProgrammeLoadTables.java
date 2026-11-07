package com.openggf.game.soniccd.level;

import java.util.*;
import static com.openggf.game.soniccd.level.CdProgrammeDefinition.*;

/**
 * One finite original-source parser for every closed definition. All input bytes belong
 * to the actual retained reader. Parsed metadata never grants live invocation authority.
 */
final class CdProgrammeLoadTables {
    record Entry(Object source,long sourceNativeAddress,int outputBytes,int vramAddress) { }
    record Body(int fileOffset,List<Entry> entries) { Body { entries=List.copyOf(entries); } }
    record Plc(int nativeId,Body body) { }
    record Palette(int nativeId,Object source,long sourceNativeAddress,
                   int destinationWord,int longwordCounter,Optional<CdAssetBytes> sourceBytes) {
        Palette(int nativeId,Object source,long address,int destination,int counter,CdAssetBytes bytes) {
            this(nativeId,source,address,destination,counter,Optional.of(bytes));
        }
        Palette { Objects.requireNonNull(sourceBytes); }
        CdAssetBytes bytes() { return sourceBytes.orElseThrow(()->new UnsupportedOperationException("Native zero-address palette read is unavailable")); }
    }
    record Cycle(CdAssetBytes script,CdAssetBytes colors,CycleKind kind) { }
    sealed interface BackgroundData permits DirectCameraData,BackgroundTables { }
    enum DirectCameraData implements BackgroundData { INSTANCE }
    record PointerTable(PointerKind kind,List<Integer> values) { PointerTable { values=List.copyOf(values); } }
    record BackgroundTables(List<PointerTable> tables,Optional<CdAssetBytes> rowSelectors) implements BackgroundData {
        BackgroundTables { tables=List.copyOf(tables); }
    }

    private final List<Plc> plcs;
    private final List<Object> encounteredSources;
    private final List<Palette> palettes;
    private final List<Cycle> cycles;
    private final CycleProgramme cycleProgramme;
    private final Sections sectionProgramme;
    private final BackgroundData backgroundProgramme;
    private final Optional<List<CdR11bLoadPlan.SectionArt>> sections;
    private final Optional<List<Integer>> backgroundPointers;

    private CdProgrammeLoadTables(CdProgrammeReader reader) {
        Objects.requireNonNull(reader,"actual retained programme reader").requireRetained();
        var definition=reader.definition();var profile=definition.load();int directoryCount=profile.directoryCount();
        var raw=reader.loadView(CdProgrammeReader.LoadRole.PLC);
        var lists=new ArrayList<Plc>();var bodies=new LinkedHashMap<Integer,Body>();
        var encountered=new LinkedHashSet<Object>();
        for(int id=0;id<directoryCount;id++) {
            int at=(short)raw.unsignedWord(id*2);
            if(at<directoryCount*2 || (at&1)!=0 || at>raw.length()-2)
                throw new IllegalArgumentException("Signed PLC root outside finite body span");
            int count=(short)raw.unsignedWord(at)+1;
            if(count<1 || count>profile.maximumCount() || 6*count>raw.length()-at-2)
                throw new IllegalArgumentException("PLC finite counter/extent differs");
            var entries=new ArrayList<Entry>();
            for(int row=0;row<count;row++) {
                int cursor=at+2+row*6;
                long address=raw.unsignedLong(cursor);
                var source=reader.loadSource(address); // Full LONG resolution, then output, before WORD destination.
                int output=reader.loadOutputBytes(source);
                int destination=raw.unsignedWord(cursor+4);
                if((destination&31)!=0 || output>65536-destination)
                    throw new IllegalArgumentException("PLC output/VRAM range differs");
                entries.add(new Entry(source,address,output,destination));encountered.add(source);
            }
            var body=new Body(reader.loadFileOffset(CdProgrammeReader.LoadRole.PLC)+at,entries);
            var alias=bodies.putIfAbsent(at,body);
            if(alias!=null && !alias.equals(body))throw new IllegalArgumentException("PLC alias bytes differ");
            lists.add(new Plc(id,alias==null?body:alias));
        }
        if(bodies.size()!=profile.bodyCount())throw new IllegalArgumentException("PLC distinct body inventory differs");
        for(int id=0;id<directoryCount;id++)
            if(lists.get(id).body().fileOffset()!=profile.roots().get(id))
                throw new IllegalArgumentException("PLC native root/alias order differs");
        if(!List.copyOf(encountered).equals(profile.encountered()))
            throw new IllegalArgumentException("PLC source encounter order differs");
        plcs=List.copyOf(lists);encounteredSources=List.copyOf(encountered);

        var table=reader.loadView(CdProgrammeReader.LoadRole.PALETTE);var parsedPalettes=new ArrayList<Palette>();
        for(int id=0;id<profile.palettes().size();id++) {
            var expected=profile.palettes().get(id);var source=expected.key();
            long address=table.unsignedLong(id*8);
            if(address!=(source==UnavailablePaletteSource.ZERO_ADDRESS?0:0x200000L+reader.fileOffset(source)))throw new IllegalArgumentException("Actual palette operand differs");
            int destination=table.unsignedWord(id*8+4);
            if(destination!=expected.destination())throw new IllegalArgumentException("Actual palette operand differs");
            int counter=table.unsignedWord(id*8+6);
            if(counter!=expected.counter())throw new IllegalArgumentException("Actual palette operand differs");
            if(source==UnavailablePaletteSource.ZERO_ADDRESS) {
                if(destination!=0 || counter!=65535)throw new IllegalArgumentException("Unavailable palette descriptor differs");
                parsedPalettes.add(new Palette(id,source,address,destination,counter,Optional.empty()));
                continue; // No address-zero bytes, fabricated padding or executable palette product.
            }
            var bytes=reader.loadViewForKey(source); // Validate pointer, destination, counter before the full source borrow.
            if((counter+1L)*4!=bytes.length())throw new IllegalArgumentException("Palette source/count extent differs");
            parsedPalettes.add(new Palette(id,source,address,destination,counter,bytes));
        }
        palettes=List.copyOf(parsedPalettes);
        var parsedCycles=new ArrayList<Cycle>();var cycleViews=new IdentityHashMap<Object,CdAssetBytes>();
        for(var cycle:profile.cycles())parsedCycles.add(new Cycle(
            slice(reader,cycle.script(),cycleViews),slice(reader,cycle.colors(),cycleViews),cycle.kind()));
        cycles=List.copyOf(parsedCycles);cycleProgramme=profile.cycleProgramme();

        // Select the actual semantic alternative BEFORE attempting a possibly absent section role.
        sectionProgramme=switch(profile.sections()) {
            case NoSections noSections -> noSections; // Actual Init/Update RTS: no invented initial queue ID.
            case CameraSections expected -> {
                if(expected.operands() instanceof SectionColumns columns)
                    yield sectionColumns(reader,expected,columns,directoryCount);
                var bytes=reader.loadView(CdProgrammeReader.LoadRole.SECTIONS);int n=expected.rows().size();
                var parsed=new ArrayList<SectionRow>();
                for(int i=0;i<n;i++) {
                    int threshold=bytes.unsignedWord(i*2);
                    int initial=bytes.unsignedWord(n*4+i*2);
                    int update=bytes.unsignedWord(n*2+i*2);
                    if(initial>=directoryCount || update>=directoryCount)
                        throw new IllegalArgumentException("Section PLC ID outside native directory");
                    parsed.add(new SectionRow(threshold,initial,update));
                }
                if(!parsed.equals(expected.rows()))throw new IllegalArgumentException("Section range/initial/update operands differ");
                yield new CameraSections(parsed);
            }
            case InitQueueUpdateRts expected -> {
                if(expected.initialQueueId()<0 || expected.initialQueueId()>=directoryCount)
                    throw new IllegalArgumentException("Section PLC ID outside native directory");
                yield expected; // Declarative init operand and actual update RTS; no queue/service is executed.
            }
        };
        backgroundProgramme=switch(profile.background()) {
            case DirectCamera ignored -> DirectCameraData.INSTANCE;
            case PointerTables expected -> {
                var pointers=new ArrayList<PointerTable>();
                for(var input:expected.tables()) {
                    var bytes=reader.loadViewForKey(input.key());var values=new ArrayList<Integer>();
                    for(int i=0;i<input.expected().size();i++)values.add((int)bytes.unsignedLong(i*4));
                    if(!values.equals(input.expected()))throw new IllegalArgumentException("Background pointer operands differ");
                    pointers.add(new PointerTable(input.kind(),values));
                }
                yield new BackgroundTables(pointers,expected.rowSelectors().map(reader::view));
            }
        };
        // Old typed R3 adapters retain their exact list identities; TT never gains a fake legacy shape/route.
        sections=definition.r3().map(binding->((CameraSections)sectionProgramme).rows().stream()
            .map(row->new CdR11bLoadPlan.SectionArt(row.threshold(),row.initial(),row.update())).toList());
        backgroundPointers=definition.r3().map(binding->((BackgroundTables)backgroundProgramme).tables().getFirst().values());
    }
    /** Separate source columns; active reads retain threshold/initial/update ordering.
     * Read unused words afterwards as DATA validation, never as synthetic camera rows.
     * N1ecc5f3 r5/section_gfx_3c.asm:3-65 and section_gfx_3d.asm:3-65.
     */
    private static CameraSections sectionColumns(CdProgrammeReader reader,CameraSections expected,
                                                 SectionColumns columns,int directoryCount) {
        var views=new IdentityHashMap<Object,CdAssetBytes>();
        var thresholds=new ArrayList<Integer>();var initial=new ArrayList<Integer>();var update=new ArrayList<Integer>();
        var rows=new ArrayList<SectionRow>();int n=columns.activeThresholdCount();
        for(int i=0;i<n;i++) {
            int threshold=sectionWord(reader,columns.thresholds(),i,false,directoryCount,thresholds,views);
            int init=sectionWord(reader,columns.initial(),i,true,directoryCount,initial,views);
            int next=sectionWord(reader,columns.update(),i,true,directoryCount,update,views);
            rows.add(new SectionRow(threshold,init,next));
        }
        for(int i=n;i<columns.thresholds().words().size();i++)
            sectionWord(reader,columns.thresholds(),i,false,directoryCount,thresholds,views);
        for(int i=n;i<Math.max(columns.initial().words().size(),columns.update().words().size());i++) {
            if(i<columns.initial().words().size())sectionWord(reader,columns.initial(),i,true,directoryCount,initial,views);
            if(i<columns.update().words().size())sectionWord(reader,columns.update(),i,true,directoryCount,update,views);
        }
        if(!rows.equals(expected.rows()))throw new IllegalArgumentException("Section range/initial/update operands differ");
        return new CameraSections(rows,new SectionColumns(n,
            new SectionWords(columns.thresholds().source(),thresholds),new SectionWords(columns.initial().source(),initial),
            new SectionWords(columns.update().source(),update)));
    }
    private static int sectionWord(CdProgrammeReader reader,SectionWords column,int index,boolean plc,
                                   int directoryCount,List<Integer> retained,Map<Object,CdAssetBytes> views) {
        // Borrow only when this column is reached. Bounds are checked at each actual WORD,
        // so a missing unused tail cannot displace a preceding active field/read fault.
        var source=column.source();var bytes=views.computeIfAbsent(source.key(),reader::loadViewForKey);
        int word=bytes.unsignedWord(source.offset()+index*2);
        if(plc && word>=directoryCount)throw new IllegalArgumentException("Section PLC ID outside native directory");
        if(word!=column.words().get(index))throw new IllegalArgumentException("Section column operand differs");
        retained.add(word);return word;
    }
    private static CdAssetBytes slice(CdProgrammeReader reader,Slice slice,Map<Object,CdAssetBytes> views) {
        var bytes=views.computeIfAbsent(slice.key(),reader::loadViewForKey);
        return slice.offset()==0 && slice.length()==bytes.length()?bytes:bytes.slice(slice.offset(),slice.length());
    }
    static CdProgrammeLoadTables decode(CdProgrammeReader reader) { return new CdProgrammeLoadTables(reader); }
    List<Plc> plcs() { return plcs; }
    List<Object> encounteredSources() { return encounteredSources; }
    List<Palette> palettes() { return palettes; }
    List<Cycle> cycles() { return cycles; }
    CycleProgramme cycleProgramme() { return cycleProgramme; }
    Sections sectionProgramme() { return sectionProgramme; }
    BackgroundData backgroundProgramme() { return backgroundProgramme; }
    List<CdR11bLoadPlan.SectionArt> sections() {
        return sections.orElseThrow(()->new UnsupportedOperationException("No legacy R3 section binding"));
    }
    List<Integer> backgroundPointers() {
        return backgroundPointers.orElseThrow(()->new UnsupportedOperationException("No legacy R3 background binding"));
    }
}
