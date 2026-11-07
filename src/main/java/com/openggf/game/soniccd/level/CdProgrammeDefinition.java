package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdHeader;
import java.util.*;

/** Closed source-definition contract. Only select() supplies a reader definition, never caller metadata. */
final class CdProgrammeDefinition {
    record Span(Object key, int offset, int length, String sha) { }
    record Operand(int offset, String hex) { }
    record ReadExtent(int length, String sha) { }
    record JavaProduct(ReadExtent input, int tableBytes, int consumed, String outputSha) { }
    /** Empty header means the explicitly registered retained raw header (legacy R31 blocks only). */
    record Compressed(Object key, Span include, OptionalInt header, int output, int capacity,
                      Optional<JavaProduct> javaProduct, Optional<ReadExtent> nativeInput, boolean plc) { }
    record Visual(CdSonicVisualAssets.SourceFormat format, Object mapping, Object dplc, Object art) { }
    record R3(int ordinaryZoneActWord, CdStageTerrain.Shape shape, Map<CdProgrammeReader.Role,Object> roles,
              Object blocks, Object graphics) { R3 { roles = Map.copyOf(roles); } }
    record Slice(Object key, int offset, int length) { }
    record Palette(Object key, int destination, int counter) { }
    enum CycleKind { STANDARD, UPPER }
    enum CycleProgramme { STANDARD_FOUR, UPPER_SIX_OR_STANDARD_FOUR, STANDARD_FOUR_WITH_RETAINED_UPPER, THREE_SOURCE_ACT_GATED }
    record Cycle(CycleKind kind, Slice script, Slice colors) { }
    record SectionRow(int threshold, int initial, int update) { }
    sealed interface Sections permits CameraSections, InitQueueUpdateRts { }
    record CameraSections(List<SectionRow> rows) implements Sections {
        CameraSections { rows = List.copyOf(rows); }
    }
    record InitQueueUpdateRts(int initialQueueId) implements Sections { }
    enum PointerKind { LIVE, COPIED }
    record Pointers(PointerKind kind, Object key, List<Integer> expected) {
        Pointers { expected = List.copyOf(expected); }
    }
    sealed interface Background permits DirectCamera, PointerTables { }
    enum DirectCamera implements Background { INSTANCE }
    record PointerTables(List<Pointers> tables, Optional<Object> rowSelectors) implements Background {
        PointerTables { tables = List.copyOf(tables); }
    }
    record Load(int directoryCount, int maximumCount, List<Integer> roots, int bodyCount,
                List<Object> encountered, List<Palette> palettes, List<Cycle> cycles,
                CycleProgramme cycleProgramme, Sections sections, Background background) {
        Load { roots=List.copyOf(roots); encountered=List.copyOf(encountered);
            palettes=List.copyOf(palettes); cycles=List.copyOf(cycles); }
    }

    private final CdStagePayload payload;
    private final CdNativeFile file;
    private final int fileBytes;
    private final String sha;
    private final CdMmdHeader header;
    private final List<Span> raw;
    private final Map<Object,Span> borrowed;
    private final List<Operand> operands;
    private final List<Compressed> sources;
    private final Map<CdProgrammeReader.LoadRole,Object> loadRoles;
    private final Visual visual;
    private final Optional<R3> r3;
    private final Optional<CdR4DataLayout.Programme> tt;
    private final Load load;

    private CdProgrammeDefinition(Builder b) {
        payload=Objects.requireNonNull(b.payload); file=Objects.requireNonNull(b.file);
        fileBytes=b.fileBytes;sha=Objects.requireNonNull(b.sha); header=Objects.requireNonNull(b.header);
        if (payload.file()!=file) throw new IllegalArgumentException("Programme file/payload differs");
        raw=List.copyOf(b.raw); borrowed=Collections.unmodifiableMap(new IdentityHashMap<>(b.borrowed));
        operands=List.copyOf(b.operands); sources=List.copyOf(b.sources);
        loadRoles=Map.copyOf(b.loadRoles); visual=Objects.requireNonNull(b.visual);
        r3=Optional.ofNullable(b.r3); tt=Optional.ofNullable(b.tt); load=Objects.requireNonNull(b.load);
        if(r3.isPresent()==tt.isPresent())throw new IllegalArgumentException("Selected data binding differs");
        if (load.roots().size()!=load.directoryCount() || load.directoryCount()<1
                || load.maximumCount()<1 || load.maximumCount()>15)
            throw new IllegalArgumentException("Finite programme directory differs");
    }
    static CdProgrammeDefinition select(CdStageKey stage) {
        return Definitions.ALL.stream().filter(d->d.payload.stage().equals(stage)).findFirst()
                .orElseThrow(()->new UnsupportedOperationException("Only original USA reviewed programme data admitted"));
    }
    CdStagePayload payload() { return payload; }
    CdNativeFile file() { return file; }
    String sha() { return sha; }
    CdMmdHeader header() { return header; }
    int fileBytes() { return fileBytes; }
    List<Span> raw() { return raw; }
    Map<Object,Span> borrowed() { return borrowed; }
    List<Operand> operands() { return operands; }
    List<Compressed> sources() { return sources; }
    Visual visual() { return visual; }
    Load load() { return load; }
    Optional<R3> r3() { return r3; }
    Optional<CdR4DataLayout.Programme> tt() { return tt; }
    Object loadKey(CdProgrammeReader.LoadRole role) {
        var key=loadRoles.get(Objects.requireNonNull(role));
        if(key==null)throw new UnsupportedOperationException(payload+" has no "+role+" table role");
        return key;
    }
    Optional<CdProgrammeReader.LoadRole> roleFor(Object key) {
        return loadRoles.entrySet().stream().filter(e->e.getValue()==key).map(Map.Entry::getKey).findFirst();
    }
    private static final class JoinedKey {
        private final String name;
        private JoinedKey(String name) { this.name=name; }
        @Override public String toString() { return name; }
    }
    /** Only closed builders below receive this object. There is no registration or descriptor ingress. */
    static final class Builder {
        private final CdStagePayload payload;
        private final CdNativeFile file;
        private final int fileBytes;
        private final String sha;
        private final CdMmdHeader header;
        final List<Span> raw=new ArrayList<>();
        final Map<Object,Span> borrowed=new IdentityHashMap<>();
        final List<Operand> operands=new ArrayList<>();
        final List<Compressed> sources=new ArrayList<>();
        final Map<CdProgrammeReader.LoadRole,Object> loadRoles=new EnumMap<>(CdProgrammeReader.LoadRole.class);
        Visual visual;
        R3 r3;
        CdR4DataLayout.Programme tt;
        Load load;
        private Builder(CdStagePayload payload,CdNativeFile file,int fileBytes,String sha,CdMmdHeader header) {
            this.payload=payload;this.file=file;this.fileBytes=fileBytes;this.sha=sha;this.header=header;
        }
        void raw(Object key,int offset,int length,String sha) { raw.add(new Span(key,offset,length,sha)); }
        void operand(int offset,String hex) { operands.add(new Operand(offset,hex)); }
        void borrow(Object key,int offset,int length) { borrowed.put(key,new Span(key,offset,length,null)); }
        Object joined(String name,int offset,int length,String sha) {
            var key=new JoinedKey(payload+":"+name);raw(key,offset,length,sha);return key;
        }
        void loadRole(CdProgrammeReader.LoadRole role,Object key) { loadRoles.put(role,key); }
    }
    private static final class Definitions {
        static final List<CdProgrammeDefinition> ALL=create();
        private static List<CdProgrammeDefinition> create() {
            var result=new ArrayList<CdProgrammeDefinition>();result.add(r31());result.add(r32());
            for(var p:CdR4DataLayout.Programme.values()) {
                var b=new Builder(p.payload(),p.file(),p.fileBytes(),p.fileSha256(),originalHeader());
                CdR4ProgrammeProfiles.configure(b,p);result.add(new CdProgrammeDefinition(b));
            }
            return List.copyOf(result);
        }
    }
    private static CdMmdHeader originalHeader() { return new CdMmdHeader(0,0,0,0,0x200100,0x20010C,0x200112); }
    private static CdProgrammeDefinition r31() {
        var b=new Builder(CdStagePayload.R31A,CdNativeFile.R31A,262144,
            "30bb932c3f7a18f0629dcee56fb982280e0b6d6063fb67392b23771f77e7682e",originalHeader());
        b.operand(0x15E6,"45f90020fac0");
        b.operand(0x3580,"45f90020fac0");
        b.operand(0x35FC,"43f90021f9bc");
        b.operand(0x2450,"43f90020fad0");
        b.operand(0x39A2,"43f900203db4");
        b.operand(0x283A,"41f90020287c");
        b.operand(0x28C8,"43f900202940");
        b.operand(0x2934,"43f900202944");
        b.operand(0x3FD6,"217c0022df200004");
        b.operand(0x5B62,"45f90022e8ac");
        b.operand(0x5B92,"43f900220000");
        b.operand(0x561A,"43f90020590a");
        b.operand(0x71CA,"45f90020754a");
        b.operand(0x2BE8,"47f900202c1249f90023a746");
        b.operand(0xFAC0,"03237bee02236c760021000000810404");
        b.operand(0x6838,"d241323b101c43fb10187c003c197200b0711000677e544151cefff6");
        b.operand(0x68E8,"303b10384ebb0034");
        b.operand(0x6ABC,"044100126b2c303b106a43fb00660242000f0243000f");
        b.operand(0x15E4,"700045f90020fac070001012670461000fca700161000fc4");
        b.operand(0x3580,"45f90020fac02f0a588a221a0281003fffff204149f900ff20006100ed82");
        b.operand(0x35B4,"4a3900ff152267064ef90020bef60839000100ff151c670a7000101267046100ee784e75");
        b.operand(0x1AD6,"203c742000027400143900ff152e08820007ef4a2d40000443f900232f3c47f12000");
        b.operand(0xBEF6,"43fa0050700072003038f700b0596504540160f813c100ff1574303b10464ef90020244c");
        b.operand(0xBF1A,"43fa002c700072003038f700b0596504540160f8b23900ff157466024e7513c100ff1574303b10104ef90020247e");
        for(var a:CdR31aAssets.Asset.values())b.raw(a,a.offset,a.length,a.sha);
        var nativeInputs=new EnumMap<CdR31aLoadMetadata.SourceId,ReadExtent>(CdR31aLoadMetadata.SourceId.class);
        for(var line:CdR31aLoadPlan.NATIVE_READERS.strip().lines().toList()) {
            var w=line.split(" ");var id=CdR31aLoadMetadata.SourceId.valueOf(w[0]);
            if(id.ordinal()!=nativeInputs.size())throw new IllegalArgumentException("R31A native source order differs");
            var profile=new CdR31aLoadPlan.NativeReader(new CdR31aLoadMetadata.Source(id),Integer.parseInt(w[1]),Integer.parseInt(w[2]),Integer.parseInt(w[3]),w[4]);
            nativeInputs.put(id,new ReadExtent(profile.byteLength(),profile.sha256()));
        }
        for(var id:CdR31aLoadMetadata.SourceId.values()) {
            var f=new CdR31aLoadMetadata.Source(id);
            b.sources.add(new Compressed(id,new Span(id,f.fileOffset(),f.includeBytes(),f.includeSha256()),
                OptionalInt.of(f.headerWord()),f.outputBytes(),65536,
                Optional.of(new JavaProduct(new ReadExtent(f.javaReaderBytes(),f.javaReaderSha256()),f.codeTableBytes(),f.expectedJavaConsumedBytes(),f.decodedSha256())),
                Optional.of(nativeInputs.get(id)),true));
        }
        b.sources.add(new Compressed(CdProgrammeReader.Blocks.R31,new Span(CdR31aAssets.Asset.LEVEL_BLOCKS,0x36C76,3960,
            "51c6a77d51fc3817ff0935c4095cd2ad6c3e809e9348507299b03f313dff09a3"),OptionalInt.empty(),5248,0x2000,
            Optional.of(new JavaProduct(new ReadExtent(3960,"51c6a77d51fc3817ff0935c4095cd2ad6c3e809e9348507299b03f313dff09a3"),0,3959,
                "108e1dc9e6fb4c14578ca3073fbd9099f9fe5028d10fed98dad102ce56076964")),Optional.empty(),false));
        // Fixed borrowed views are separate from the63 public ranges. Root's finite P5 registration belongs here.
        b.borrow(CdProgrammeReader.Borrow.MUSIC_CONSUMER,0x1A76,58);
        b.borrow(CdProgrammeReader.Borrow.MUSIC_COMMANDS,0x1AB0,28);
        b.borrow(CdProgrammeReader.Borrow.SCROLL_CONSUMER,0x299C,40);
        b.borrow(CdProgrammeReader.Borrow.COMMON_EVENTS,0x3626,122);
        b.borrow(CdProgrammeReader.Borrow.ZONE_DIRECTORY,0x36A0,14);
        b.borrow(CdProgrammeReader.Borrow.CCZ_DISPATCH,0x3748,18);
        b.borrow(CdProgrammeReader.Borrow.ACT_DIRECTORY,0x375A,6);
        b.borrow(CdProgrammeReader.Borrow.CCZ12,0x3760,8);
        b.borrow(CdProgrammeReader.Borrow.HUD_UPDATE,0xA940,1118);
        b.borrow(CdProgrammeReader.Borrow.HUD_RESET,0xAB86,12);
        b.borrow(CdProgrammeReader.Borrow.HUD_DIVISORS,0xACE6,40);
        b.borrow(CdProgrammeReader.Borrow.HUD_VINT,0x1DFA,6);
        for(var view:CdR31IntroAssets.View.values())b.borrow(view,view.offset,view.length);
        r3Bindings(b,0x100,CdStageTerrain.Shape.CCZ1_PRESENT,CdProgrammeReader.Blocks.R31,CdR31aLoadMetadata.SourceId.STAGE_GFX,
            new Object[]{CdR31aAssets.Asset.ANI_SONIC,CdR31aAssets.Asset.MINI_ANIMATIONS,CdR31aAssets.Asset.SPEC_CHUNKS,CdR31aAssets.Asset.SINE_TABLE,CdR31aAssets.Asset.ARC_TAN_TABLE,CdR31aAssets.Asset.MAP_SPR_SONIC,CdR31aAssets.Asset.DPLC_SONIC,CdR31aAssets.Asset.ART_SONIC,CdR31aAssets.Asset.LEVEL_CHUNKS,CdR31aAssets.Asset.LAYOUT_FG,CdR31aAssets.Asset.LAYOUT_BG,CdR31aAssets.Asset.LEVEL_COLLISION,CdR31aAssets.Asset.COL_ANGLE_MAP,CdR31aAssets.Asset.COL_HEIGHT_MAP,CdR31aAssets.Asset.COL_WIDTH_MAP,CdR31aAssets.Asset.OBJECT_LAYOUTS},
            new Object[]{CdR31aAssets.Asset.PLC_LISTS,CdR31aAssets.Asset.PALETTE_TABLE,CdR31aAssets.Asset.PALETTE_CYCLES,CdR31aAssets.Asset.SECTIONS,CdR31aAssets.Asset.BG_POINTERS,CdR31aAssets.Asset.PAL_S1_TITLE,CdR31aAssets.Asset.PAL_S1_LEVEL_SELECT,CdR31aAssets.Asset.PAL_SONIC,CdR31aAssets.Asset.PAL_LEVEL,CdR31aAssets.Asset.PAL_LEVEL_END});
        b.load=r3Load(b,List.of(0xFAF6,0xFB04,0xFB60,0xFAF6,0xFB74,0xFBBE,0xFC02,0xFC4C,0xFC54,        0xFC9E,0xFCA6,0xFCB4,0xFCC2,0xFB60,0xFB60,0xFB60,0xFCCA,0xFB60,0xFCD2),14,
            List.of(CdR31aLoadMetadata.SourceId.values()),List.of(
                new SectionRow(0x4A0,2,7),new SectionRow(0x1000,4,8),new SectionRow(0x1800,5,9),new SectionRow(0xFFFF,6,10)));
        return new CdProgrammeDefinition(b);
    }
    private static CdProgrammeDefinition r32() {
        var b=new Builder(CdStagePayload.R32A,CdNativeFile.R32A,CdR32aDataLayout.FILE_BYTES,CdR32aDataLayout.MMD_SHA256,originalHeader());
        for(var o:CdR32aDataLayout.OPERANDS)b.operand(o.fileOffset(),o.hexBytes());
        for(var a:CdR32aDataLayout.Asset.values())b.raw(a,a.fileOffset(),a.length(),a.sha256());
        for(var f:CdR32aLoadLayout.SourceId.values())b.sources.add(new Compressed(f,
            new Span(f,f.fileOffset(),f.includeBytes(),f.includeSha256()),OptionalInt.of(f.headerWord()),f.outputBytes(),
            f==CdR32aLoadLayout.SourceId.STAGE_BLOCKS?0x2000:65536,
            Optional.of(new JavaProduct(new ReadExtent(f.readerBytes(),f.readerSha256()),f.codeTableBytes(),f.consumedInputBytes(),f.outputSha256())),Optional.empty(),true));
        r3Bindings(b,0x101,CdStageTerrain.Shape.CCZ2_PRESENT,CdR32aLoadLayout.SourceId.STAGE_BLOCKS,CdR32aLoadLayout.SourceId.STAGE_GFX,
            new Object[]{CdR32aDataLayout.Asset.ANI_SONIC,CdR32aDataLayout.Asset.MINI_ANIMATIONS,CdR32aDataLayout.Asset.SPEC_CHUNKS,CdR32aDataLayout.Asset.SINE_TABLE,CdR32aDataLayout.Asset.ARC_TAN_TABLE,CdR32aDataLayout.Asset.MAP_SPR_SONIC,CdR32aDataLayout.Asset.DPLC_SONIC,CdR32aDataLayout.Asset.ART_SONIC,CdR32aDataLayout.Asset.LEVEL_CHUNKS,CdR32aDataLayout.Asset.LAYOUT_FG,CdR32aDataLayout.Asset.LAYOUT_BG,CdR32aDataLayout.Asset.LEVEL_COLLISION,CdR32aDataLayout.Asset.COL_ANGLE_MAP,CdR32aDataLayout.Asset.COL_HEIGHT_MAP,CdR32aDataLayout.Asset.COL_WIDTH_MAP,CdR32aDataLayout.Asset.OBJECT_LAYOUTS},
            new Object[]{CdR32aDataLayout.Asset.PLC_LISTS,CdR32aDataLayout.Asset.PALETTE_TABLE,CdR32aDataLayout.Asset.PALETTE_CYCLES,CdR32aDataLayout.Asset.SECTIONS,CdR32aDataLayout.Asset.BG_POINTERS,CdR32aDataLayout.Asset.PAL_S1_TITLE,CdR32aDataLayout.Asset.PAL_S1_LEVEL_SELECT,CdR32aDataLayout.Asset.PAL_SONIC,CdR32aDataLayout.Asset.PAL_LEVEL,CdR32aDataLayout.Asset.PAL_LEVEL_END});
        var encountered=new LinkedHashSet<Object>();
        for(var list:CdR32aLoadLayout.PLC_LISTS)for(var e:list.entries())encountered.add(e.source());
        b.load=r3Load(b,CdR32aLoadLayout.PLC_LISTS.stream().map(CdR32aLoadLayout.PlcList::fileOffset).toList(),8,
            List.copyOf(encountered),CdR32aLoadLayout.SECTIONS.stream().map(r->new SectionRow(r.rightThresholdWord(),r.initialPlcId(),r.updatePlcId())).toList());
        return new CdProgrammeDefinition(b);
    }
    private static void r3Bindings(Builder b,int word,CdStageTerrain.Shape shape,Object blocks,Object graphics,Object[] roles,Object[] loads) {
        var roleMap=new EnumMap<CdProgrammeReader.Role,Object>(CdProgrammeReader.Role.class);
        for(var r:CdProgrammeReader.Role.values())roleMap.put(r,roles[r.ordinal()]);
        for(var r:CdProgrammeReader.LoadRole.values())b.loadRole(r,loads[r.ordinal()]);
        b.r3=new R3(word,shape,roleMap,blocks,graphics);
        b.visual=new Visual(CdSonicVisualAssets.SourceFormat.FRAMES_167,roleMap.get(CdProgrammeReader.Role.MAP),roleMap.get(CdProgrammeReader.Role.DPLC),roleMap.get(CdProgrammeReader.Role.ART));
    }
    private static Load r3Load(Builder b,List<Integer> roots,int bodies,List<?> encountered,List<SectionRow> sections) {
        var palettes=new ArrayList<Palette>();
        var roles=List.of(CdProgrammeReader.LoadRole.PAL_TITLE,CdProgrammeReader.LoadRole.PAL_TITLE,CdProgrammeReader.LoadRole.PAL_SELECT,
            CdProgrammeReader.LoadRole.PAL_SONIC,CdProgrammeReader.LoadRole.PAL_LEVEL,CdProgrammeReader.LoadRole.PAL_END);
        for(int id=0;id<6;id++)palettes.add(new Palette(b.loadRoles.get(roles.get(id)),id<4?0xFB00:0xFB20,id==3?7:id<3?31:23));
        var cycles=new ArrayList<Cycle>();Object key=b.loadRoles.get(CdProgrammeReader.LoadRole.CYCLES);
        int[] scripts={0,14,52,90},colors={8,48,84,96},ends={14,52,90,100};
        for(int i=0;i<4;i++)cycles.add(new Cycle(CycleKind.STANDARD,new Slice(key,scripts[i],colors[i]-scripts[i]),new Slice(key,colors[i],ends[i]-colors[i])));
        return new Load(19,15,roots,bodies,new ArrayList<Object>(encountered),palettes,cycles,CycleProgramme.STANDARD_FOUR,new CameraSections(sections),
            new PointerTables(List.of(new Pointers(PointerKind.LIVE,b.loadRoles.get(CdProgrammeReader.LoadRole.BG_POINTERS),
                List.of(0xFFF708,0xFFF708,0xFFF710,0xFFF718))),Optional.empty()));
    }
}
