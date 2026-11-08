package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdHeader;
import java.util.*;

/** Closed source-definition contract. Only select() supplies a reader definition, never caller metadata. */
final class CdProgrammeDefinition {
    record PlacementBinding(Object key,CdObjectPlacements.Layout layout,int rows) {
        PlacementBinding { Objects.requireNonNull(key);Objects.requireNonNull(layout);
            if(rows<0 || rows>layout.rows)throw new IllegalArgumentException("Finite placement count differs"); }
    }
    record Span(Object key, int offset, int length, String sha) { }
    record Operand(int offset, String hex) { }
    record ReadExtent(int length, String sha) { }
    record JavaProduct(ReadExtent input, int tableBytes, int consumed, String outputSha) { }
    /** Empty header means the explicitly registered retained raw header (legacy R31 blocks only). */
    record Compressed(Object key, Span include, OptionalInt header, int output, int capacity,
                      Optional<JavaProduct> javaProduct, Optional<ReadExtent> nativeInput, boolean plc) { }
    sealed interface VisualBinding permits Visual, UnsupportedVisual { }
    record Visual(CdSonicVisualAssets.SourceFormat format, Object mapping, Object dplc, Object art) implements VisualBinding { }
    record UnsupportedVisual(String reason) implements VisualBinding { UnsupportedVisual { Objects.requireNonNull(reason); } }
    /** Literal zero pointer / FFFF DBF count is retained, never treated as an executable no-op. */
    enum UnavailablePaletteSource { ZERO_ADDRESS }
    record R3(int ordinaryZoneActWord, CdStageTerrain.Shape shape, Map<CdProgrammeReader.Role,Object> roles,
              Object blocks, Object graphics) { R3 { roles = Map.copyOf(roles); } }
    /** Immutable terrain DATA is independent of R3 player/source-family admission. */
    record Terrain(CdStageTerrain.Profile profile,Optional<CdStageTerrain.Shape> legacyShape,
                   Map<CdProgrammeReader.Role,Object> roles,Object blocks,Object graphics) {
        Terrain {
            Objects.requireNonNull(profile);Objects.requireNonNull(legacyShape);roles=Map.copyOf(roles);
            Objects.requireNonNull(blocks);Objects.requireNonNull(graphics);
            for(var role:List.of(CdProgrammeReader.Role.CHUNKS,CdProgrammeReader.Role.FG,CdProgrammeReader.Role.BG,
                    CdProgrammeReader.Role.COLLISION,CdProgrammeReader.Role.ANGLE,CdProgrammeReader.Role.HEIGHT,CdProgrammeReader.Role.WIDTH))
                if(!roles.containsKey(role))throw new IllegalArgumentException("Missing terrain DATA role: "+role);
        }
    }
    record Slice(Object key, int offset, int length) { }
    record Palette(Object key, int destination, int counter) { }
    enum CycleKind { STANDARD, UPPER, BYTE_HEADER_4 }
    enum CycleProgramme { STANDARD_FOUR, UPPER_SIX_OR_STANDARD_FOUR, STANDARD_FOUR_WITH_RETAINED_UPPER, THREE_SOURCE_ACT_GATED, R5_PRESENT_BACKGROUND_CONVEYOR, R5_PAST_BACKGROUND_CONVEYOR, R5_FUTURE_BOSS_GATED_CONVEYOR, FOUR_WITH_BOSS_COLOR_OVERRIDE, THREE_MIRRORED, SOURCE_RTS, SOURCE_ACT_GATED, STANDARD_CONFIGURED }
    record Cycle(CycleKind kind, Slice script, Slice colors) { }
    record SectionRow(int threshold, int initial, int update) { }
    sealed interface Sections permits CameraSections, InitQueueUpdateRts, NoSections { }
    sealed interface SectionOperands permits PackedSectionRows, SectionColumns { }
    /** Original contiguous threshold/update/initial n/n/n layout. */
    enum PackedSectionRows implements SectionOperands { INSTANCE }
    /** Complete bounded column, including authentic words unused by the active threshold rows. */
    record SectionWords(Slice source, List<Integer> words) {
        SectionWords {
            Objects.requireNonNull(source);Objects.requireNonNull(source.key());words=List.copyOf(words);
            if(source.offset()<0 || (source.offset()&1)!=0 || words.isEmpty()
                    || source.length()!=words.size()*2L || (long)source.offset()+source.length()>Integer.MAX_VALUE)
                throw new IllegalArgumentException("Section column slice/word extent differs");
            for(int word:words)if((word&~0xFFFF)!=0)throw new IllegalArgumentException("Section operand is not a WORD");
        }
    }
    record SectionColumns(int activeThresholdCount, SectionWords thresholds, SectionWords initial,
                          SectionWords update) implements SectionOperands {
        SectionColumns {
            Objects.requireNonNull(thresholds);Objects.requireNonNull(initial);Objects.requireNonNull(update);
            if(activeThresholdCount<1 || activeThresholdCount>thresholds.words().size()
                    || activeThresholdCount>initial.words().size() || activeThresholdCount>update.words().size())
                throw new IllegalArgumentException("Active section rows exceed actual columns");
        }
    }
    record CameraSections(List<SectionRow> rows, SectionOperands operands) implements Sections {
        CameraSections(List<SectionRow> rows) { this(rows,PackedSectionRows.INSTANCE); }
        CameraSections {
            rows=List.copyOf(rows);Objects.requireNonNull(operands);
            if(operands instanceof SectionColumns columns && rows.size()!=columns.activeThresholdCount())
                throw new IllegalArgumentException("Active section row inventory differs");
        }
    }
    record InitQueueUpdateRts(int initialQueueId) implements Sections { }
    enum NoSections implements Sections { INSTANCE }
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
    private final VisualBinding visual;
    private final Optional<R3> r3;
    private final Optional<Terrain> terrain;
    private final Optional<CdR4DataLayout.Programme> tt;
    private final Optional<Load> load;
    private final Optional<PlacementBinding> placements;
    private final boolean dataOnly;
    private final String loadBoundary;

    private CdProgrammeDefinition(Builder b) {
        payload=Objects.requireNonNull(b.payload); file=Objects.requireNonNull(b.file);
        fileBytes=b.fileBytes;sha=Objects.requireNonNull(b.sha); header=Objects.requireNonNull(b.header);
        if (payload.file()!=file) throw new IllegalArgumentException("Programme file/payload differs");
        raw=List.copyOf(b.raw); borrowed=Collections.unmodifiableMap(new IdentityHashMap<>(b.borrowed));
        operands=List.copyOf(b.operands); sources=List.copyOf(b.sources);
        loadRoles=Map.copyOf(b.loadRoles); visual=Objects.requireNonNull(b.visual);
        terrain=Optional.ofNullable(b.terrain);
        r3=Optional.ofNullable(b.r3); tt=Optional.ofNullable(b.tt); load=Optional.ofNullable(b.load);
        placements=Optional.ofNullable(b.placements);
        dataOnly=b.dataOnly;loadBoundary=b.loadBoundary;
        if((r3.isPresent()?1:0)+(tt.isPresent()?1:0)+(dataOnly?1:0)!=1)
            throw new IllegalArgumentException("Selected data binding differs");
        if(load.isEmpty()) {
            if(!dataOnly || loadBoundary==null || loadBoundary.isBlank())
                throw new IllegalArgumentException("Missing explicit data-only load boundary");
        } else {
            if(loadBoundary!=null)throw new IllegalArgumentException("Admitted load has a boundary");
            var admitted=load.orElseThrow();
            if(admitted.roots().size()!=admitted.directoryCount() || admitted.directoryCount()<1
                    || admitted.maximumCount()<1 || admitted.maximumCount()>15)
                throw new IllegalArgumentException("Finite programme directory differs");
        }
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
    VisualBinding visual() { return visual; }
    Load load() { return load.orElseThrow(()->new UnsupportedOperationException(loadBoundary)); }
    Optional<PlacementBinding> placements() { return placements; }
    boolean dataOnly() { return dataOnly; }
    Optional<R3> r3() { return r3; }
    Optional<Terrain> terrain() { return terrain; }
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
        VisualBinding visual;
        R3 r3;
        Terrain terrain;
        CdR4DataLayout.Programme tt;
        boolean dataOnly; // Generic source-only data, not live terrain/player/World admission.
        Load load;
        PlacementBinding placements;
        String loadBoundary;
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
            for(var p:CdR5DataLayout.Programme.values()) {
                var b=new Builder(p.payload(),p.file(),p.fileBytes(),p.fileSha256(),originalHeader());
                CdR5ProgrammeProfiles.configure(b,p);result.add(new CdProgrammeDefinition(b));
            }
            for(var p:CdR6DataLayout.Programme.values()) {
                var b=new Builder(p.payload(),p.file(),p.fileBytes(),p.fileSha256(),originalHeader());
                CdR6ProgrammeProfiles.configure(b,p);result.add(new CdProgrammeDefinition(b));
            }
            for(var p:CdR7DataLayout.Programme.values()) {
                var b=new Builder(p.payload(),p.file(),p.fileBytes(),p.fileSha256(),originalHeader());
                CdR7ProgrammeProfiles.configure(b,p);result.add(new CdProgrammeDefinition(b));
            }
            for(var p:CdR8DataLayout.Programme.values()) {
                var b=new Builder(p.payload(),p.file(),262144,p.fileSha256(),originalHeader());
                CdR8ProgrammeProfiles.configure(b,p);result.add(new CdProgrammeDefinition(b));
            }
            for(var p:CdCczDataLayout.Programme.values()) {
                var b=new Builder(p.payload(),p.file(),262144,p.fileSha256(),originalHeader());
                CdCczProgrammeProfiles.configure(b,p);result.add(new CdProgrammeDefinition(b));
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
        b.raw(CdR31aGameOverSource.View.CODE,0xF17E,0xE4,"2bbfc2728531c5ca3612ea7e87b34ef9056fc02a8490decae80e25d2d6a2e58b");
        b.raw(CdR31aGameOverSource.View.MAPPINGS,0xF262,56,"ab185aa60f83919e611dd47457d7fb9b5b87e99f2b9210d0dc0508c71dcea44b");
        // Genuine same-owner P3 DATA; only Bumper is physically admitted.
        b.operand(0xADB4,"217c0020b0aa0004");
        b.operand(0xAF1E,"217c0020b0ea0004");
        b.operand(0xB2E0,"217c0020b30e0004");
        b.operand(0xB154,"217c0020b26c0004");
        b.operand(0xB63E,"217c0020b8800004");
        b.operand(0xB8E0,"217c0020b9180004");
        b.operand(0xAEEE,"43f90020b09a");
        b.operand(0xAF52,"43f90020b0dc");
        b.operand(0xB2F4,"43fa000e");
        b.operand(0xB8FC,"43fa0014");
        b.operand(0xAFB0,"47f90020b01a");
        for(var view:CdR31aBumperSource.View.values())b.raw(view,view.offset,view.length,view.sha);
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
        // Fixed borrowed views are separate from the public ranges. Root's finite P5 registration belongs here.
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
        for(var view:CdR31aSolidsSource.View.values())b.borrow(view,view.offset,view.length);
        for(var view:List.of(CdR31EcologyEndAssets.View.FLOWER_MAP,CdR31EcologyEndAssets.View.FLOWER_ANI))b.borrow(view,view.fileOffset(CdNativeFile.R31A),view.length());
        for(var view:CdR31aObjectAssets.FlowerView.values())b.borrow(view,view.offset,view.length);
        for(var view:CdR31aObjectAssets.KamaView.values())b.borrow(view,view.offset,view.length);
        for(var view:CdR31aObjectAssets.EffectView.values())b.borrow(view,view.offset,view.length);
        r3Bindings(b,0x100,CdStageTerrain.Shape.CCZ1_PRESENT,CdProgrammeReader.Blocks.R31,CdR31aLoadMetadata.SourceId.STAGE_GFX,
            new Object[]{CdR31aAssets.Asset.ANI_SONIC,CdR31aAssets.Asset.MINI_ANIMATIONS,CdR31aAssets.Asset.SPEC_CHUNKS,CdR31aAssets.Asset.SINE_TABLE,CdR31aAssets.Asset.ARC_TAN_TABLE,CdR31aAssets.Asset.MAP_SPR_SONIC,CdR31aAssets.Asset.DPLC_SONIC,CdR31aAssets.Asset.ART_SONIC,CdR31aAssets.Asset.LEVEL_CHUNKS,CdR31aAssets.Asset.LAYOUT_FG,CdR31aAssets.Asset.LAYOUT_BG,CdR31aAssets.Asset.LEVEL_COLLISION,CdR31aAssets.Asset.COL_ANGLE_MAP,CdR31aAssets.Asset.COL_HEIGHT_MAP,CdR31aAssets.Asset.COL_WIDTH_MAP,CdR31aAssets.Asset.OBJECT_LAYOUTS},
            new Object[]{CdR31aAssets.Asset.PLC_LISTS,CdR31aAssets.Asset.PALETTE_TABLE,CdR31aAssets.Asset.PALETTE_CYCLES,CdR31aAssets.Asset.SECTIONS,CdR31aAssets.Asset.BG_POINTERS,CdR31aAssets.Asset.PAL_S1_TITLE,CdR31aAssets.Asset.PAL_S1_LEVEL_SELECT,CdR31aAssets.Asset.PAL_SONIC,CdR31aAssets.Asset.PAL_LEVEL,CdR31aAssets.Asset.PAL_LEVEL_END});
        b.placements=new PlacementBinding(CdR31aAssets.Asset.OBJECT_LAYOUTS,CdObjectPlacements.Layout.R31A_517,517);
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
        b.placements=new PlacementBinding(CdR32aDataLayout.Asset.OBJECT_LAYOUTS,CdObjectPlacements.Layout.LEGACY_497,454);
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
        b.terrain=new Terrain(CdStageTerrain.profileFor(shape),Optional.of(shape),roleMap,blocks,graphics);
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
