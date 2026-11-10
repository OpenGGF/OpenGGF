package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdCommand;
import com.openggf.game.soniccd.resources.CdR4ArtServiceMetadata;
import com.openggf.game.soniccd.scroll.CdR4BackgroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundPlane;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.media.CdMmdImage;
import java.io.IOException;
import java.util.*;

/**
 * Internal read-only loading DATA facade. The original R11 aggregate remains supported;
 * canonical R11 standard and TT water DATA retain their SAME private programme readers. This is neither a scheduler nor
 * an entry/World capability. It supplies no clears, checkpoints, clocks or Ready state.
 * All finite decode work stays in the existing reader/decoders. Callers request only the
 * reached facts; in particular checkpoint loading must not request an initial spawn.
 */
public final class CdLevelLoadingSource {
    private sealed interface Origin permits OriginalR11, Programme { }
    private record OriginalR11(CdR11aAssets assets) implements Origin { }
    private record Programme(CdProgrammeReader reader,Completion completion) implements Origin { }
    public record PlcEntry(long sourceNativeAddress,int outputBytes,int vramAddress) { }
    public record PlcList(int nativeId,int bodyFileOffset,List<PlcEntry> entries) {
        public PlcList { entries=List.copyOf(entries); }
    }
    public record Palette(int nativeId,long sourceNativeAddress,int destinationWord,
                          int longwordCounter,Optional<CdAssetBytes> sourceBytes) {
        public Palette { Objects.requireNonNull(sourceBytes); }
        public CdAssetBytes bytes() { return sourceBytes.orElseThrow(()->new UnsupportedOperationException("Native zero-address palette source")); }
    }
    public record Section(int rightThresholdWord,int initialPlcId,int updatePlcId) { }
    public sealed interface Sections permits CameraSections, InitialQueueUpdateRts { }
    public record CameraSections(List<Section> rows) implements Sections {
        public CameraSections { rows=List.copyOf(rows); }
    }
    /** Native Init enqueues this ID; Update is RTS. No synthetic FFFF section row. */
    public record InitialQueueUpdateRts(int initialQueueId) implements Sections { }
    public enum CycleKind { STANDARD, UPPER, BYTE_HEADER_4 }
    public record Cycle(CdAssetBytes script,CdAssetBytes colors,CycleKind kind) { }
    public sealed interface Background permits DirectCamera, PointerTables { }
    public enum DirectCamera implements Background { INSTANCE }
    public enum PointerKind { LIVE, COPIED }
    public record PointerTable(PointerKind kind,List<Integer> values) {
        public PointerTable { values=List.copyOf(values); }
    }
    public record PointerTables(List<PointerTable> tables,Optional<CdAssetBytes> rowSelectors) implements Background {
        public PointerTables { tables=List.copyOf(tables); }
    }
    /** Parsed products remain borrowed immutable DATA, including unavailable palette descriptors. */
    public record LoadTables(List<PlcList> plcs,List<Palette> palettes,Sections sections) {
        public LoadTables { plcs=List.copyOf(plcs);palettes=List.copyOf(palettes);Objects.requireNonNull(sections); }
    }
    /** Retained source metadata and lazy finite inputs; public values cannot manufacture one. */
    public static final class ArtSource {
        private final CdLevelLoadingSource owner;
        private final Object key;
        private final int offset,length,output;
        private ArtSource(CdLevelLoadingSource owner,Object key,int offset,int length,int output) {
            this.owner=owner;this.key=key;this.offset=offset;this.length=length;this.output=output;
        }
        public boolean belongsTo(CdLevelLoadingSource actual) { return owner==actual; }
        public CdNativeFile file() { return owner.file(); }
        public CdMmdImage.Domain domain() { return CdMmdImage.Domain.ORIGINAL_WORD_RAM; }
        public int fileOffset() { return offset; }
        public int includeBytes() { return length; }
        public long nativeAddress() { return 0x200000L+offset; }
        public int outputBytes() { return output; }
        /** Existing native-prefetch envelope, not a decoded/preloaded bank or padded include. */
        public CdAssetBytes readerBytes()throws IOException {
            return switch(owner.origin) {
                case Programme p -> {if(p.completion()==Completion.STANDARD)owner.requireRetained();yield p.reader().nativeInput(key);}
                case OriginalR11 r -> (key==CdR11aAsset.LEVEL_BLOCKS?r.assets().loadingArtInputs().blocks()
                    :r.assets().loadingArtInputs().source((CdR11aAsset)key)).readerBytes();
            };
        }
    }

    private final Origin origin;
    private final Map<Object,ArtSource> artSources=new IdentityHashMap<>();
    private CdStagePlayerSource originalPlayer;
    private LoadTables tables;
    private CdLevelLoadingSource(Origin origin) { this.origin=Objects.requireNonNull(origin); }
    static CdLevelLoadingSource fromProgramme(CdProgrammeReader actual) {
        Objects.requireNonNull(actual).requireRetained();
        if(actual.definition().tt().isPresent())
            return new CdLevelLoadingSource(new Programme(actual,Completion.WATER_PALETTE_AFTER_FADE));
        actual.retainedStandardPpzImage();
        var player=actual.playerBinding();
        if(player.graph()!=CdR11aPlayerAssets.SourceGraph.PPZ || player.ordinaryZoneActWord()!=0
                || player.ordinaryPeriod()!=(actual.standardPpzProgramme()==CdPpzDataLayout.Programme.R11A?1:0) || player.visual().format()!=CdSonicVisualAssets.SourceFormat.FRAMES_198_WITH_EVEN_BYTE)
            throw new IllegalArgumentException("Explicit original PPZ standard loading DATA required");
        return new CdLevelLoadingSource(new Programme(actual,Completion.STANDARD));
    }
    public static CdLevelLoadingSource fromOriginalR11(CdR11aAssets actual) {
        return new CdLevelLoadingSource(new OriginalR11(Objects.requireNonNull(actual,"Original R11 assets")));
    }
    private boolean standardProgramme(){return origin instanceof Programme p&&p.completion()==Completion.STANDARD;}
    private CdProgrammeReader standardReader(){
        if(!(origin instanceof Programme p)||p.completion()!=Completion.STANDARD)
            throw new UnsupportedOperationException("Not original standard loading DATA");
        p.reader().retainedStandardPpzImage();return p.reader();
    }
    private CdAssetBytes standardView(CdPpzProgrammeProfiles.R11LoadingView key){return standardReader().standardPpzLoadingView(key);}
    /** Distinguishes the retained legacy aggregate origin from immutable programme DATA. */
    public boolean hasOriginalR11Aggregate(){return origin instanceof OriginalR11;}
    /** Immutable actual Assets-to-private-reader relation only; equal descriptors grant nothing. */
    public boolean ownsRetainedStandardAssets(CdR11aAssets actual){
        if(actual==null||!standardProgramme()||!actual.hasRetainedStandardLoadingSource())return false;
        try{return actual.retainedStandardLoadingSource()==this;}
        catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    /** Source-selected semantic algorithms, not a live scroll or programme permit.
     * N1ecc PPZ InitLevelScroll: Present135-170 clears separate cloud speeds;
     * Past153-177 clears four deformation LONGs and uses different parallax ratios.
     * The private loading/ordinary caller still owns invocation, ordered inputs and stores. */
    public enum StandardBackgroundProgram { CLOUDS_AND_WATER, PARALLAX_CLOUD_BANDS }
    public StandardBackgroundProgram standardBackgroundProgram(){
        if(origin instanceof OriginalR11)return StandardBackgroundProgram.CLOUDS_AND_WATER;
        return switch(standardReader().standardPpzProgramme()){
            case R11A -> StandardBackgroundProgram.CLOUDS_AND_WATER;
            case R11B -> StandardBackgroundProgram.PARALLAX_CLOUD_BANDS;
            default -> throw new UnsupportedOperationException("Unbound standard background programme");
        };
    }
    private CdR11bObjectData standardPastObjects;
    /** One immutable Past mapper/catalogue over this exact retained Reader. It supplies
     * no physical pass, allocation, current art or World authority. Failed construction
     * publishes no cache and neither this adapter nor its consumers reopen the media. */
    public synchronized CdR11bObjectData standardPastObjectSource(){
        var reader=standardReader();
        if(reader.standardPpzProgramme()!=CdPpzDataLayout.Programme.R11B)
            throw new UnsupportedOperationException("Original Past object source required");
        if(standardPastObjects==null)standardPastObjects=CdPpzProgrammeData.fromReader(reader).objectSource();
        else standardPastObjects.requireRetained();
        return standardPastObjects;
    }
    /** Actual original B ShieldArtIndex on this same retained reader. Each selected copy is
     * exactly the native $100 LONGs, including genuine adjacent file bytes where an include
     * is shorter. The authenticated pointer table and full B MMD own those finite spans;
     * no A source address, decoded art, resident-tail default or new source cache is used.
     * N1ecc Powerup/Main157-186; PPZ Data(Act1Past)346-364. */
    public com.openggf.game.soniccd.resources.CdPowerupArtRuntime.RawSources shield(){
        var reader=standardReader();
        if(reader.standardPpzProgramme()!=CdPpzDataLayout.Programme.R11B)
            throw new UnsupportedOperationException("Original Past ShieldArtIndex source required");
        var index=reader.view(CdPpzProgrammeProfiles.R11PastLoadingView.SHIELD_ART_INDEX);
        return slot->{
            Objects.checkIndex(slot,5);var image=reader.retainedStandardPpzImage();
            // Only the closed, hash-authenticated B index supplies a pointer. Never caller metadata.
            long offset=index.unsignedLong(slot*4)-0x200000L;
            if(offset<0||offset>image.fileBytes()-1024L)
                throw new IllegalArgumentException("Unbacked original Past ShieldArtIndex copy at slot "+slot);
            return CdProgrammeReader.retainedSpan(image,(int)offset,1024,"Original Past ShieldArtIndex copy "+slot);
        };
    }
    private CdAssetBytes standardHudWriter;
    private CdR11aStartupSource.PowerupWindow standardPowerupWindow;
    private List<Integer> standardHudDestinations,standardPowerupDestinations;
    private final Map<ArtSource,CdAssetBytes> standardSectionInputs=new IdentityHashMap<>();
    /** Source-authenticated membership only; no current bytes or invocation permit. */
    public synchronized List<Integer> standardHudDestinations()throws IOException{
        var reader=standardReader();
        if(standardHudDestinations==null){
            standardHudWriter=CdR11aStartupSource.standardHudWriter(reader);
            var values=new ArrayList<Integer>();
            for(int at=0;at<65536;at+=32)if(com.openggf.game.soniccd.resources.CdLevelHud.isFontDestination(at))values.add(at);
            standardHudDestinations=List.copyOf(values);
        }
        return standardHudDestinations;
    }
    public boolean standardHudDestination(int destination){
        try{standardHudDestinations();return com.openggf.game.soniccd.resources.CdLevelHud.isFontDestination(destination);}
        catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    public synchronized List<Integer> standardPowerupDestinations()throws IOException{
        var reader=standardReader();
        if(standardPowerupDestinations==null){
            var window=CdR11aStartupSource.standardPowerupWindow(reader);
            if(window.destination()!=com.openggf.game.soniccd.resources.CdPowerupArtRuntime.VRAM_BYTE_ADDRESS
                    ||window.bytes()!=com.openggf.game.soniccd.resources.CdPowerupArtRuntime.WINDOW_BYTES)
                throw new IOException("Original powerup destination differs from shared kernel");
            var values=new ArrayList<Integer>();for(int at=window.destination();at<window.destination()+window.bytes();at+=32)values.add(at);
            standardPowerupWindow=window;standardPowerupDestinations=List.copyOf(values);
        }
        return standardPowerupDestinations;
    }
    public boolean standardPowerupDestination(int destination){
        try{standardPowerupDestinations();return (destination&31)==0&&destination>=standardPowerupWindow.destination()
                &&destination<=standardPowerupWindow.destination()+standardPowerupWindow.bytes()-32;}
        catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    /** The original current-art kernel's finite +2 REAL adjacent-byte envelope. The common
     * native-prefetch product remains a separate contract; this never pads or reopens a reader. */
    public synchronized CdAssetBytes sectionArtBytes(ArtSource input)throws IOException{
        Objects.requireNonNull(input);if(!input.belongsTo(this))throw new IllegalArgumentException("Foreign loading art source");
        if(!standardProgramme())return input.readerBytes();
        var image=standardReader().retainedStandardPpzImage();var cached=standardSectionInputs.get(input);if(cached!=null)return cached;
        int bytes=Math.min(input.includeBytes()+2,image.fileBytes()-input.fileOffset());
        var value=CdProgrammeReader.retainedSpan(image,input.fileOffset(),bytes,"Original R11 current art source");
        standardSectionInputs.put(input,value);return value;
    }
    public boolean ownsOriginalR11(CdR11aAssets actual) { return origin instanceof OriginalR11 r && r.assets()==actual; }
    /** Existing authenticated aggregate borrow, only for migration of its current source adapters. */
    public CdR11aAssets originalR11Assets() {
        if(origin instanceof OriginalR11 r)return r.assets();
        throw new UnsupportedOperationException("Selected loading source is not original R11");
    }
    public CdNativeFile file() { return switch(origin) {case OriginalR11 r->r.assets().identity().file();case Programme p->p.reader().file();}; }
    public CdStageKey stage() { return switch(origin) {case OriginalR11 r->r.assets().identity().stage();case Programme p->p.reader().stage();}; }
    public int nativeZoneActWord() { return switch(origin) {case OriginalR11 ignored->0;case Programme p->p.reader().ordinaryZoneActWord();}; }
    public int ordinaryPeriod() { return switch(origin) {case OriginalR11 ignored->1;case Programme p->p.reader().playerBinding().ordinaryPeriod();}; }
    public synchronized CdStagePlayerSource playerSource()throws IOException {
        if(standardProgramme())return standardReader().playerSource();
        return switch(origin) {
            case OriginalR11 r -> {if(originalPlayer==null)originalPlayer=CdStagePlayerSource.fromPresent(r.assets());yield originalPlayer;}
            case Programme p -> p.reader().playerSource();
        };
    }
    /** Exact actual relation, never equal stage/identity/decoded bytes as custody. */
    public void requirePlayerSource(CdStagePlayerSource actual) {
        Objects.requireNonNull(actual);
        boolean same=switch(origin) {
            case OriginalR11 r -> actual.ownsPresent(r.assets());
            case Programme p -> actual.ownsProgrammeReader(p.reader());
        };
        if(!same)throw new IllegalArgumentException("Foreign loading/player source relation");
    }
    public CdStageTerrain terrain()throws IOException {if(standardProgramme())return standardReader().terrain();return switch(origin){case OriginalR11 r->r.assets().terrain().stageTerrain();case Programme p->p.reader().terrain();};}
    public CdSonicVisualAssets sonic() {if(standardProgramme())return standardReader().sonic();return switch(origin){case OriginalR11 r->r.assets().sonic();case Programme p->p.reader().sonic();};}
    public CdObjectPlacements placements() {if(standardProgramme())return standardReader().placements();return switch(origin){case OriginalR11 r->r.assets().placements();case Programme p->p.reader().placements();};}
    /** Canonical declaration identity is checked by the retained reader before any borrow. */
    public CdAssetBytes view(CdR4DataLayout.Range canonical) {
        if(standardProgramme())throw new UnsupportedOperationException("Standard source cannot borrow TT declarations");
        return programmeReader().view(canonical);
    }
    /** Canonical PPZ declarations retain the exact same private reader; equal descriptors fail. */
    public CdAssetBytes view(CdPpzDataLayout.Range canonical) {return standardReader().view(canonical);}
    /** Same private reader and canonical actual source cache; no ordinary invocation or art preload. */
    public CdProgrammeObjectSource objectSource(){return programmeReader().objectSource();}
    /** Finite admitted ordinary-object DATA, not a World/service or default source. */
    public Optional<CdProgrammeObjectSource> ordinaryObjectSource(){
        if(origin instanceof Programme p&&p.reader().definition().tt().orElse(null)==CdR4DataLayout.Programme.R41A)
            return Optional.of(p.reader().objectSource());
        return Optional.empty();
    }

    private CdProgrammeReader programmeReader() {
        if(origin instanceof Programme p)return p.reader();
        throw new UnsupportedOperationException("Original R11 uses its existing typed assets");
    }
    private CdR4DataLayout.Programme programme() {return programmeReader().definition().tt().orElseThrow(()->new UnsupportedOperationException("Standard loading DATA has no TT water programme"));}
    private CdR4DataLayout.Range boundsRange() {
        var p=programme();return p.range(switch(p) {
            case R41A->"unk_202C12";case R41B,R42B->"unk_202B6A";
            case R41C,R42C->"unk_202BA4";case R41D,R42D->"unk_202B92";
            case R42A->"unk_202C24";case R43C,R43D->"word_202BA4";
        });
    }
    /** Same finite bounds decoder as R11; R43C's extra demo words are not decoded as bounds. */
    public CdR11aLoadPlan.Bounds bounds() {if(standardProgramme())return standardReader().standardPpzEntry().bounds();return switch(origin) {
        case OriginalR11 r->r.assets().loadPlan().bounds();
        case Programme ignored->CdR11aLoadPlan.decodeBounds(view(boundsRange()).slice(0,12).copy());
    };}
    public int cameraCentreX() {if(standardProgramme())return standardView(CdPpzProgrammeProfiles.R11LoadingView.CAMERA_CENTRE).unsignedWord(2);return switch(origin) {
        case OriginalR11 ignored->0xA0; // Existing native R11 InitScroll immediate.
        case Programme p->p.reader().loadingCameraCentreX(boundsRange());
    };}
    /** Ordinary first spawn only. No checkpoint/demo selector or fabricated checkpoint coordinates. */
    public CdR11aLoadPlan.InitialCentre initialCentre() {if(standardProgramme())return standardReader().standardPpzEntry().centre();return switch(origin) {
        case OriginalR11 r->r.assets().loadPlan().initialCentre();
        case Programme ignored->CdR11aLoadPlan.decodeInitialCentre((programme()==CdR4DataLayout.Programme.R43C
            ?programmeReader().view(CdProgrammeReader.LoadingBorrow.R43C_SPAWN):view(programme().range("StagePlayerSpawn"))).copy());
    };}
    /** Borrow native pointer tags and palette IDs, without R11-specific pointer admission. */
    public CdR11aLoadPlan.LevelIndex levelIndex() {if(standardProgramme())return standardReader().standardPpzEntry().index();return switch(origin) {
        case OriginalR11 r->r.assets().loadPlan().levelIndex();
        case Programme ignored->{
            var bytes=view(programme().range("StageDataIndex"));
            yield new CdR11aLoadPlan.LevelIndex(new CdR11aLoadPlan.TaggedPointer(bytes.unsignedLong(0)),
                new CdR11aLoadPlan.TaggedPointer(bytes.unsignedLong(4)),Math.toIntExact(bytes.unsignedLong(8)),
                bytes.slice(12,2),bytes.slice(14,2));
        }
    };}
    /** Native reached WORD lookup only. No water/current/target/routine/speed stores here.
     * r4/main.asm191-212: act*8 + (raw period &7F)*2. Out-of-span reads fail, never pad.
     */
    public int initialWaterHeight(int rawPeriodByte) {
        if((rawPeriodByte&~255)!=0)throw new IllegalArgumentException("Raw period BYTE");
        var p=programme();int index=(nativeZoneActWord()&255)*8+(rawPeriodByte&127)*2;
        return view(p.range("WaterHeights")).unsignedWord(index);
    }
    public synchronized LoadTables loadTables() {
        if(standardProgramme())standardReader();
        if(tables!=null){if(standardProgramme())standardReader();return tables;}
        tables=switch(origin) {
            case OriginalR11 r -> {
                var plan=r.assets().loadPlan();
                var plcs=plan.plcs().stream().map(l->new PlcList(l.nativeId(),l.bodyRange().fileOffset(),
                    l.entries().stream().map(e->new PlcEntry(Integer.toUnsignedLong(e.sourceRange().nativeAddress()),
                        (r.assets().view(e.source()).bytes().unsignedWord(0)&0x7FFF)*32,e.vramAddress())).toList())).toList();
                // Preserve its already-decoded source/cache objects and existing palette-ID subset.
                var palettes=plan.paletteBindings().stream().map(p->new Palette(p.nativeId(),
                    Integer.toUnsignedLong(r.assets().profile().range(p.source()).nativeAddress()),p.destinationWord(),p.longwordCounter(),Optional.of(p.bytes()))).toList();
                yield new LoadTables(plcs,palettes,new CameraSections(plan.sections().stream()
                    .map(s->new Section(s.rightThresholdWord(),s.initialPlcId(),s.updatePlcId())).toList()));
            }
            case Programme p -> {
                var parsed=p.reader().loadTables();
                var plcs=parsed.plcs().stream().map(l->new PlcList(l.nativeId(),l.body().fileOffset(),
                    l.body().entries().stream().map(e->new PlcEntry(e.sourceNativeAddress(),e.outputBytes(),e.vramAddress())).toList())).toList();
                var palettes=parsed.palettes().stream().map(v->new Palette(v.nativeId(),v.sourceNativeAddress(),
                    v.destinationWord(),v.longwordCounter(),v.sourceBytes())).toList();
                var sections=switch(parsed.sectionProgramme()) {
                    case CdProgrammeDefinition.CameraSections s->new CameraSections(s.rows().stream()
                        .map(v->new Section(v.threshold(),v.initial(),v.update())).toList());
                    case CdProgrammeDefinition.InitQueueUpdateRts s->new InitialQueueUpdateRts(s.initialQueueId());
                    case CdProgrammeDefinition.NoSections ignored->throw new UnsupportedOperationException("No selected section binding");
                };
                yield new LoadTables(plcs,palettes,sections);
            }
        };return tables;
    }
    /** Actual TT scroll table formats, retaining LIVE versus COPIED and direct-camera differences. */
    public Background background() {
        if(standardProgramme())standardReader();
        var parsed=programmeReader().loadTables().backgroundProgramme();
        return switch(parsed) {
            case CdProgrammeLoadTables.DirectCameraData ignored->DirectCamera.INSTANCE;
            case CdProgrammeLoadTables.BackgroundTables b->new PointerTables(b.tables().stream().map(t->
                new PointerTable(PointerKind.valueOf(t.kind().name()),t.values())).toList(),b.rowSelectors());
        };
    }
    public List<Cycle> cycles() {if(standardProgramme())standardReader();return programmeReader().loadTables().cycles().stream().map(c->
        new Cycle(c.script(),c.colors(),CycleKind.valueOf(c.kind().name()))).toList();}
    /** Source-only keys supplied by the selected closed definition; no eager native input reads. */
    public synchronized List<ArtSource> queuedArtSources()throws IOException {
        if(standardProgramme()) {
            var reader=standardReader();
            return reader.loadTables().encounteredSources().stream().map(k->artSources.computeIfAbsent(k,
                key->{var r=(CdPpzDataLayout.Range)key;return new ArtSource(this,key,r.fileOffset(),r.length(),reader.loadOutputBytes(key));})).toList();
        }
        return switch(origin) {
            case OriginalR11 r -> {
                var input=r.assets().loadingArtInputs();var list=new ArrayList<ArtSource>();
                for(var s:input.sources())list.add(artSources.computeIfAbsent(s.asset(),k->new ArtSource(this,k,
                    s.include().fileOffset(),s.include().length(),s.tileCount()*32)));
                yield List.copyOf(list);
            }
            case Programme p -> p.reader().loadTables().encounteredSources().stream().map(k->artSources.computeIfAbsent(k,
                key->{var r=(CdR4DataLayout.Range)key;return new ArtSource(this,key,r.fileOffset(),r.length(),p.reader().loadOutputBytes(key));})).toList();
        };
    }
    public synchronized ArtSource blocksSource()throws IOException {
        if(standardProgramme()) {
            var reader=standardReader();var key=reader.standardPpzProgramme().range("LevelBlocks");
            return artSources.computeIfAbsent(key,k->new ArtSource(this,k,key.fileOffset(),key.length(),reader.loadOutputBytes(k)));
        }
        return switch(origin) {
            case OriginalR11 r -> {
                var s=r.assets().loadingArtInputs().blocks();yield artSources.computeIfAbsent(s.asset(),k->new ArtSource(this,k,
                    s.include().fileOffset(),s.include().length(),s.tileCount()*32));
            }
            case Programme p -> {var key=programme().range("StageBlocks");yield artSources.computeIfAbsent(key,k->new ArtSource(this,k,key.fileOffset(),key.length(),p.reader().loadOutputBytes(k)));}
        };
    }
    /** Existing canonical decoder product, requested only by the actual RAM decode consistency check. */
    public CdAssetBytes decodedBlocks()throws IOException {if(standardProgramme()){var reader=standardReader();return reader.decoded(reader.standardPpzProgramme().range("LevelBlocks")).bytes();}return switch(origin) {
        case OriginalR11 r->r.assets().decoded(CdR11aAsset.LEVEL_BLOCKS);
        case Programme p->p.reader().decoded(programme().range("StageBlocks")).bytes();
    };}
    public CdAssetBytes lifeIcon()throws IOException {if(standardProgramme())return standardView(CdPpzProgrammeProfiles.R11LoadingView.LIFE_ICON);return switch(origin) {
        case OriginalR11 r->r.assets().loadingArtInputs().lifeIcon();
        case Programme ignored->view(programme().range("LivesIconsGfx"));
    };}
    public long lifeIconNativeAddress() {if(standardProgramme()){var reader=standardReader();return 0x200000L+reader.standardPpzLoadingOffset(CdPpzProgrammeProfiles.R11LoadingView.LIFE_ICON);}return switch(origin) {
        case OriginalR11 r->Integer.toUnsignedLong(r.assets().profile().range(CdR11aAsset.ART_LIFE_ICON).nativeAddress());
        case Programme ignored->Integer.toUnsignedLong(programme().range("LivesIconsGfx").nativeAddress());
    };}
    public enum Completion { STANDARD, WATER_PALETTE_AFTER_FADE }
    public record PaletteCopy(int byteOffset,int longCount,CdAssetBytes bytes) {
        public PaletteCopy {Objects.requireNonNull(bytes);if(byteOffset<0||longCount<1||bytes.length()!=longCount*4)throw new IllegalArgumentException("Exact palette LONG span required");}
    }
    /** DATA retention only; the Module independently checks its live invocation and lifetime. */
    public void requireRetained() {
        if(origin instanceof Programme p)p.reader().requireRetained();
        else {var a=((OriginalR11)origin).assets();
            if(a.identity().file()!=CdNativeFile.R11A||a.identity().domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM)
                throw new IllegalStateException("Not retained original R11 material");}
    }
    public boolean ownsPlayerSource(CdStagePlayerSource actual) {
        if(actual==null)return false;
        return switch(origin){case OriginalR11 r->actual.ownsPresent(r.assets());case Programme p->actual.ownsProgrammeReader(p.reader());};
    }
    /** Preserves the existing checked reader API; synchronous private loading callbacks cannot throw IOException. */
    public CdStagePlayerSource retainedPlayerSource() {
        try{return playerSource();}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    public Completion completion(){return origin instanceof Programme p?p.completion():Completion.STANDARD;}
    public int levelPlcId(){return levelIndex().levelArt().plcId();}
    public int blocksPlcId(){return levelIndex().blocks().plcId();}
    private Palette palette(int selector){return loadTables().palettes().stream().filter(p->p.nativeId()==selector).findFirst().orElseThrow(()->new IllegalArgumentException("Unavailable native palette selector "+selector));}
    public CdAssetBytes playerPalette(){return palette(3).bytes();}
    public CdAssetBytes levelPalette(){return palette(levelIndex().paletteIds().unsignedByte(1)).bytes();}
    /** A finite source-data read boundary, not an executable music or loading capability. */
    public static final class StandardMusicReadBoundary extends UnsupportedOperationException {
        private final long address;private final int index;
        private StandardMusicReadBoundary(long address,int index){super("Unowned standard R11 music byte at $"+Long.toHexString(address));this.address=address;this.index=index;}
        public long address(){return address;}public int index(){return index;}
    }
    public CdCommand selectLevelMusic(int zoneAct,int rawPeriod,int timeAttack,int goodFuture) {
        if(standardProgramme()) {
            int index=CdLevelMusicIndex.select(zoneAct,rawPeriod,timeAttack,goodFuture);
            standardView(CdPpzProgrammeProfiles.R11LoadingView.MUSIC_CONSUMER);
            var music=standardView(CdPpzProgrammeProfiles.R11LoadingView.MUSIC);
            if(index>=music.length())throw new StandardMusicReadBoundary(0x200000L+programmeReader().standardPpzLoadingOffset(CdPpzProgrammeProfiles.R11LoadingView.MUSIC)+index,index);
            return CdCommand.fromId(music.unsignedByte(index));
        }
        if(origin instanceof OriginalR11 r){try{return r.assets().loadingArtInputs().selectLevelMusic(zoneAct,rawPeriod,timeAttack,goodFuture);}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}}
        int index=CdLevelMusicIndex.select(zoneAct,rawPeriod,timeAttack,goodFuture);
        return CdCommand.fromId(view(programme().range("StageMusicCommandTable")).unsignedByte(index));
    }
    public CdAssetBytes backgroundLayout(){if(standardProgramme()){var reader=standardReader();return reader.view(reader.standardPpzProgramme().range("BackgroundRead"));}return switch(origin){case OriginalR11 r->r.assets().view(CdR11aAsset.LAYOUT_BG).bytes();case Programme ignored->view(programme().range("StageMapBg"));};}
    public CdAssetBytes backgroundSectionIds(){
        if(standardProgramme()){var reader=standardReader();return reader.view(reader.standardPpzProgramme().range("BackgroundRows"));}
        if(origin instanceof OriginalR11 r)return r.assets().view(CdR11aAsset.BG_CAMERA_SECT_IDS).bytes();
        if(background() instanceof PointerTables p)return p.rowSelectors().orElseThrow(()->new UnsupportedOperationException("Original TT background row selectors unavailable"));
        throw new UnsupportedOperationException("Original TT direct-camera plane algorithm is not admitted by this adapter");
    }
    /** Pure source-bound geometry: no live camera, plane clear or draw invocation is issued. */
    public CdAssetBytes standardBackgroundPointers(){
        var reader=standardReader();return reader.view(reader.standardPpzProgramme().range("BackgroundPointers"));
    }
    public int standardBackgroundRowMask(){
        var reader=standardReader();
        if(reader.standardPpzProgramme()==CdPpzDataLayout.Programme.R11A)return 0x7F0;
        var operand=reader.view(CdPpzProgrammeProfiles.R11PastLoadingView.BACKGROUND_MASK);
        if(operand.unsignedWord(0)!=0x0240||operand.unsignedWord(2)!=0x1F0)
            throw new IllegalArgumentException("Original Past InitLevelDrawBG selector mask differs");
        return operand.unsignedWord(2);
    }
    /** The retained PPZ plane factory still requires an aggregate; no TT copied-table substitution. */
    public static final class StandardPlaneBindingBoundary extends UnsupportedOperationException {
        private StandardPlaneBindingBoundary(){super("Original R11 same-reader background-plane factory remains unowned");}
    }
    public CdR11aBackgroundPlane backgroundPlane(){
        if(origin instanceof OriginalR11 r)return CdR11aBackgroundPlane.bind(r.assets());
        try{return CdR11aBackgroundPlane.fromLoadingSource(this);}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
    }
    public long collisionPointer(int actualZoneAct){
        if(actualZoneAct!=nativeZoneActWord())throw new IllegalArgumentException("Foreign original collision selector");
        if(standardProgramme())return standardView(CdPpzProgrammeProfiles.R11LoadingView.COLLISION_INDEX).unsignedLong((actualZoneAct&255)*4);
        if(origin instanceof OriginalR11 r)return r.assets().view(CdR11aAsset.LEVEL_COL_INDEX).bytes().unsignedLong((actualZoneAct&255)*4);
        // R41A LoadStageCollision index resolves this authenticated original operand; no decoder/profile fallback.
        requireR41A();return collisionSourceAddress();
    }
    public long collisionSourceAddress(){if(standardProgramme()){var reader=standardReader();return 0x200000L+reader.standardPpzProgramme().range("LevelCollision").fileOffset();}return switch(origin){case OriginalR11 r->Integer.toUnsignedLong(r.assets().profile().range(CdR11aAsset.LEVEL_COLLISION).nativeAddress());case Programme ignored->Integer.toUnsignedLong(programme().range("StageCollision").nativeAddress());};}
    public int waterHeight(int sourceByteOffset){return view(programme().range("WaterHeights")).unsignedWord(sourceByteOffset);}
    public int loadingSplitByte(){requireR41A();return 0xDF;} // r4/main.asm188-190: $8ADF operand.
    public int loopChunksLong(){requireR41A();return (int)view(programme().range("unk_202C90")).unsignedLong(0);}
    public int initialEventTargetBottomWord(){requireR41A();return 0x510;} // R41Events, r4/events.asm:61-63.
    public CdR4BackgroundScroll.Profile waterScrollProfile(){requireR41A();return CdR4BackgroundScroll.Profile.ACT1_PRESENT;}
    public CdR4BackgroundScroll.Tables waterScrollTables(){
        requireR41A();return new CdR4BackgroundScroll.Tables(view(programme().range("WobbleTable")),
            view(programme().range("byte_202E1C")),CdAssetBytes.copyOf(new byte[0]));
        // Empty adjacent RAM is UNKNOWN, not a zero-filled extension; a reached read faults in the existing kernel.
    }
    public CdR4ArtServiceMetadata.Strategy artStrategy(){requireR41A();return CdR4ArtServiceMetadata.Strategy.ACT1_PRESENT_WINDOW;}
    private void requireR41A(){if(!(origin instanceof Programme)||standardProgramme()||programme()!=CdR4DataLayout.Programme.R41A)throw new UnsupportedOperationException("Original loading assembly currently binds authentic R41A only");}
    /** Script tables immediately precede StageDataIndex in R41A Legacy Shared.asm99-102.
     * Three source tables are each BYTE header2 +24*2 timing bytes +8 LONG pointers=82 bytes.
     * Anchor to the authenticated StageDataIndex, never stale address-looking label spellings. */
    public long stageAnimationScript(int ordinal){
        requireR41A();Objects.checkIndex(ordinal,3);
        return 0x200000L+programme().range("StageDataIndex").fileOffset()-3*82+ordinal*82;
    }
    private CdAssetBytes animationRead(long address,int bytes){
        requireR41A();long first=stageAnimationScript(0),end=stageAnimationScript(2)+82;
        if(address>=first&&address<=end-bytes)return programmeReader().loadingAnimationTables().slice(Math.toIntExact(address-first),bytes);
        for(String key:List.of("byte_238A42","byte_238C42","byte_238E42","byte_239042","byte_239242","byte_239442","byte_239642","byte_239842")){
            var range=programme().range(key);long base=Integer.toUnsignedLong(range.nativeAddress());
            if(address>=base&&address<=base+range.length()-bytes)return view(range).slice(Math.toIntExact(address-base),bytes);
        }
        throw new IllegalArgumentException("Outside authenticated original R41 animation sources: $"+Long.toHexString(address));
    }
    public int stageAnimationByte(long address){return animationRead(address,1).unsignedByte(0);}
    public int stageAnimationLong(long address){return (int)animationRead(address,4).unsignedLong(0);}
    public PaletteCopy waterPalette(int selector){
        requireR41A();var p=palette(selector);int at=(p.destinationWord()-0x80)&0xFFFF;
        if(at<0xFA80||at>0xFAFC)throw new IllegalArgumentException("Native water-palette destination outside owned bank");
        return new PaletteCopy(at-0xFA80,p.longwordCounter()+1,p.bytes());
    }

}
