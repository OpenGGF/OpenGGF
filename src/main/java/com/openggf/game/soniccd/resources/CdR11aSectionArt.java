package com.openggf.game.soniccd.resources;

// Bounded SOURCE packet; authored Java UNRUN. Root owns queued validation.

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdLevelLoadingSource;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aLoadPlan;
import com.openggf.game.soniccd.level.CdStagePlayerSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pure immutable R11A section requests, native PLC preparation and selected
 * complete-tile service. No frame, device, actor, graphics or timing owner.
 * Sources: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Section Art Load.asm:7-46; Palmtree Panic/Section Data
 * (Act 1 Present).asm:7-26; Functions (General).asm:588-785.
 *
 * <p>The supplied plan selects whole lists. Source programs supply finite
 * native inputs, never an object-selected pixel bank or resident certificate.
 * The actual queue destination owns writes; canonical source metadata never
 * selects a list or overrides a relocated destination. The aggregate State
 * owns the single current residency value and is itself the capture value.
 * Reusing it performs no decoding, upload or implicit preparation.</p>
 *
 * <p>State captures relevant low decoder fields, not all raw CPU register
 * halves. In particular completion POP does not save final decoder RAM.
 * A malformed declared-source fault is terminal: its decoder diagnostic may
 * describe intra-tile rows not committed to this complete-tile residency
 * owner. Such evidence is not a resumable midrow/native-prefix snapshot.</p>
 */
public final class CdR11aSectionArt {
    /** Plain declared input, with no authentication or native ingress claim. */
    public record SourceProgram(CdR11aLoadPlan.PlcEntry entry, CdAssetBytes readerBytes) {
        public SourceProgram {
            checkedEntry(entry);
            Objects.requireNonNull(readerBytes, "readerBytes");
        }
    }

    public enum Writer { CLEARED, NORMAL, XOR }

    /**
     * Semantic content of the six saved decoder longs. Only repeat/read/shift
     * low WORDs, pixel low nibble, full XOR history and writer choice are owned;
     * incoming unowned high halves are not asserted to be zero.
     */
    public record SavedDecoder(Writer writer, int repeatWord, int pixelNibble,
                               int previousRow, int readWord, int shiftWord) {
        public SavedDecoder {
            Objects.requireNonNull(writer, "writer");
            word(repeatWord); word(readWord); word(shiftWord);
            if ((pixelNibble & ~15) != 0) throw new IllegalArgumentException("Pixel must be a nibble");
        }
    }

    public enum Reason {
        SECTION_TABLE, PLC_LIST, DESCRIPTOR_BUFFER, UNKNOWN_SOURCE,
        DECODE_PREPARE, DECODE_TILE, RESIDENCY,
        ACTIVE_QUEUED_DECODE,IMMEDIATE_HEADER_COUNT,LIFE_ICON_SOURCE,BLOCK_PRODUCT_MISMATCH
    }

    /** Reached boundary with all prior complete owned effects, terminal thereafter. */
    public static final class SectionArtException extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final Reason reason;
        private Operation operation=Operation.QUEUED;private CdAssetBytes ramPrefix;
        private final State state;
        private final long sourcePointer;
        private final int destinationWord;

        private SectionArtException(Reason reason, State state, long sourcePointer,
                                    int destinationWord, Throwable cause) {
            super("R11A section art " + reason + " at source $" + Long.toHexString(sourcePointer)
                    + ", VRAM $" + Integer.toHexString(destinationWord), cause);
            this.reason = reason;
            this.state = state;
            this.sourcePointer = sourcePointer;
            this.destinationWord = destinationWord;
        }

        public Reason reason() { return reason; }
        public Operation operation(){return operation;}
        /** Actual completed LONG rows, including a faulting tile's completed prefix. */
        public Optional<CdAssetBytes> completedRamPrefix(){return Optional.ofNullable(ramPrefix);}
        public Optional<CdBlockDefinitionStaging> completedBlockDefinitions(){
            return completedRamPrefix().map(p->CdBlockDefinitionStaging.unknown().withCompletedPrefix(p));
        }
        public State state() { return state; }
        public long sourcePointer() { return sourcePointer; }
        public int destinationWord() { return destinationWord; }
    }

    /**
     * One immutable aggregate. A decoder may remain diagnostically present with
     * count0 after completion; the native count WORD alone admits tile service.
     * savedDecoder is the separately retained semantic RAM image, not a second
     * resumable decoder. Hint/pad enter as explicit DEV0 with no external writer.
     */
    public enum Operation { QUEUED,IMMEDIATE_LIST,BLOCKS_RAM,LIFE_ICON }
    /** Actual shared-kernel result; no public construction or adoption port. */
    public static final class BlocksResult {
        private final State state;
        private final CdAssetBytes blocks;
        private final CdBlockDefinitionStaging blockDefinitions;
        private BlocksResult(State state,CdAssetBytes blocks,CdBlockDefinitionStaging blockDefinitions){
            this.state=state;this.blocks=blocks;this.blockDefinitions=blockDefinitions;
        }
        public State state(){return state;}
        public CdAssetBytes blocks(){return blocks;}
        public CdBlockDefinitionStaging blockDefinitions(){return blockDefinitions;}
        @Override public boolean equals(Object other){return this==other||other instanceof BlocksResult result
                &&state.equals(result.state)&&blocks.equals(result.blocks)&&blockDefinitions.equals(result.blockDefinitions);}
        @Override public int hashCode(){return Objects.hash(state,blocks,blockDefinitions);}
    }
    public static final class State {
        private final CdR11aSectionArt owner;
        private final int sectionWord;
        private final CdR11aPlcBuffer.State queue;
        private final CdAssetBytes codeTable;
        private final CdNemesisPlcDecoder.State decoder;
        private final SavedDecoder savedDecoder;
        private final int remainingTilesWord;
        private final int processBudgetWord;
        private final int hintWord;
        private final int padWord;
        private final CdR11aCameraArtResidency.State residency;
        private final boolean stopped;

        private State(CdR11aSectionArt owner, int sectionWord, CdR11aPlcBuffer.State queue,
                      CdAssetBytes codeTable, CdNemesisPlcDecoder.State decoder,
                      SavedDecoder savedDecoder, int remainingTilesWord, int processBudgetWord,
                      int hintWord, int padWord, CdR11aCameraArtResidency.State residency,
                      boolean stopped) {
            this.owner = owner;
            this.sectionWord = sectionWord;
            this.queue = queue;
            this.codeTable = codeTable;
            this.decoder = decoder;
            this.savedDecoder = savedDecoder;
            this.remainingTilesWord = remainingTilesWord;
            this.processBudgetWord = processBudgetWord;
            this.hintWord = hintWord;
            this.padWord = padWord;
            this.residency = residency;
            this.stopped = stopped;
        }

        public int sectionWord() { return sectionWord; }
        public CdR11aPlcBuffer.State queue() { return queue; }
        public CdAssetBytes codeTable() { return codeTable; }
        public Optional<CdNemesisPlcDecoder.State> decoder() { return Optional.ofNullable(decoder); }
        public SavedDecoder savedDecoder() { return savedDecoder; }
        public int remainingTilesWord() { return remainingTilesWord; }
        public int processBudgetWord() { return processBudgetWord; }
        public int hintWord() { return hintWord; }
        public int padWord() { return padWord; }
        public CdR11aCameraArtResidency.State residency() { return residency; }
        public boolean stopped() { return stopped; }

        @Override public boolean equals(Object other) {
            return this == other || other instanceof State state && owner == state.owner
                    && sectionWord == state.sectionWord && queue.equals(state.queue)
                    && Arrays.equals(codeTable.copy(), state.codeTable.copy())
                    && Objects.equals(decoder, state.decoder) && savedDecoder.equals(state.savedDecoder)
                    && remainingTilesWord == state.remainingTilesWord
                    && processBudgetWord == state.processBudgetWord && hintWord == state.hintWord
                    && padWord == state.padWord && residency.equals(state.residency) && stopped == state.stopped;
        }

        @Override public int hashCode() {
            return 31 * Objects.hash(System.identityHashCode(owner), sectionWord, queue, decoder,
                    savedDecoder, remainingTilesWord, processBudgetWord, hintWord, padWord,
                    residency, stopped) + Arrays.hashCode(codeTable.copy());
        }
    }

    private static final SavedDecoder CLEARED = new SavedDecoder(Writer.CLEARED, 0, 0, 0, 0, 0);
    private final CdSectionArtKernel<State, CdR11aCameraArtResidency.State> kernel;
    private CdImmediateArtKernel<State,CdR11aCameraArtResidency.State> immediate;
    private com.openggf.game.soniccd.level.CdR11aLoadingArtInputs loading;
    private CdLevelLoadingSource loadingSource;
    private int sourceInitialQueueId=-1;

    /**
     * Copies the declared plan/table/source containers. Plain construction
     * authenticates no media. Producer/range collisions are rejected rather
     * than allowing canonical destination or list membership to select bytes.
     * Missing producers/lists are reached typed boundaries, not eager filters.
     */
    public CdR11aSectionArt(List<CdR11aLoadPlan.PlcList> lists,
                           List<CdR11aLoadPlan.SectionArt> sections, List<SourceProgram> sources) {
        this(lists, sections, sources, null);
    }

    private CdR11aSectionArt(List<CdR11aLoadPlan.PlcList> lists,
                            List<CdR11aLoadPlan.SectionArt> sections, List<SourceProgram> sources,
                            CdR11aAssets.Identity identity) {
        var copiedLists = List.copyOf(Objects.requireNonNull(lists, "lists"));
        var copiedSections = List.copyOf(Objects.requireNonNull(sections, "sections"));
        var copiedSources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (copiedSections.isEmpty() || copiedSections.size() > 128) {
            throw new IllegalArgumentException("A bounded section byte-offset table is required");
        }
        int previous = -1;
        for (var section : copiedSections) {
            word(section.rightThresholdWord()); word(section.initialPlcId()); word(section.updatePlcId());
            if (section.rightThresholdWord() <= previous) {
                throw new IllegalArgumentException("Section thresholds must increase unsigned");
            }
            previous = section.rightThresholdWord();
        }
        if (previous != 0xFFFF) throw new IllegalArgumentException("Terminal section threshold must be FFFF");
        var declared = new HashMap<Long, CdR11aLoadPlan.PlcEntry>();
        var listMap = new HashMap<Integer, List<CdR11aPlcBuffer.Entry>>();
        for (var list : copiedLists) {
            word(list.nativeId());
            Objects.requireNonNull(list.bodyRange(), "bodyRange");
            var rows = new ArrayList<CdR11aPlcBuffer.Entry>();
            for (var entry : list.entries()) {
                checkedEntry(entry);
                declare(declared, entry);
                rows.add(new CdR11aPlcBuffer.Entry(entry.sourceRange().nativeAddress(), entry.vramAddress()));
            }
            if (listMap.putIfAbsent(list.nativeId(), List.copyOf(rows)) != null) {
                throw new IllegalArgumentException("Duplicate PLC list ID");
            }
        }
        var immutableLists = Map.copyOf(listMap);
        var sourceMap = new HashMap<Long, SourceProgram>();
        for (var program : copiedSources) {
            declare(declared, program.entry());
            long address = program.entry().sourceRange().nativeAddress();
            if (sourceMap.putIfAbsent(address, program) != null) {
                throw new IllegalArgumentException("Duplicate native source program");
            }
        }
        var immutablePrograms = Map.copyOf(sourceMap);
        var binding = new CdSectionArtKernel.Binding(identity == null ? this : identity,
                com.openggf.game.soniccd.CdNativeFile.R11A,
                com.openggf.game.soniccd.media.CdMmdImage.Domain.ORIGINAL_WORD_RAM);
        var normalizedSections = new ArrayList<CdSectionArtKernel.Section>();
        for (var section : copiedSections) {
            normalizedSections.add(new CdSectionArtKernel.Section(section.rightThresholdWord(),
                    section.initialPlcId(), section.updatePlcId()));
        }
        var normalizedPrograms = new ArrayList<CdSectionArtKernel.Source>();
        for (var program : immutablePrograms.values()) {
            var range = program.entry().sourceRange();
            normalizedPrograms.add(new CdSectionArtKernel.Source(binding, program.entry().source(),
                    range.file(), range.domain(), range.fileOffset(), range.length(),
                    Integer.toUnsignedLong(range.nativeAddress()), program.readerBytes()));
        }
        this.kernel = new CdSectionArtKernel<>(binding, immutableLists, normalizedSections,
                normalizedPrograms, new KernelStates(), CLEARED);
    }

    /**
     * Explicit authenticated-assets opt-in. Reuses the actual USA plan and all
     * seventeen finite source views, including cached Animals at canonical PLC2 row1; never decodes
     * or preloads their output here. Canonical metadata describes the source;
     * the actual queued entry retains its destination and source-range authority.
     * Asset/region authentication remains owned by the existing asset pipeline.
     */
    public static CdR11aSectionArt fromAssets(CdR11aAssets assets) throws IOException {
        Objects.requireNonNull(assets, "assets");
        var plan = assets.loadPlan();
        var bank = assets.openingObjects().sectionArtSources();
        if (bank.sources().size() != 17) throw new IllegalArgumentException("Seventeen reviewed source views required");
        var sources = new ArrayList<SourceProgram>();
        for (var source : bank.sources()) {
            var entry = source.canonicalPlc();
            var reader = source.readerInputRange();
            var blob = entry.sourceRange();
            if (source.canonicalPlcId() < 0 || source.canonicalPlcId() >= plan.plcs().size()
                    || source.canonicalRowIndex() < 0
                    || source.canonicalRowIndex() >= plan.plcs().get(source.canonicalPlcId()).entries().size()
                    || !entry.equals(plan.plcs().get(source.canonicalPlcId()).entries().get(source.canonicalRowIndex()))
                    || source.source() != entry.source() || !source.decode().sourceBlobRange().equals(blob)
                    || reader.file() != blob.file() || reader.domain() != blob.domain()
                    || reader.fileOffset() != blob.fileOffset() || reader.length() != source.readerBytes().length()
                    || source.readerBytes().length() < 2
                    || (source.readerBytes().unsignedWord(0) & 0x7FFF) != source.tileCount()) {
                throw new IllegalArgumentException("Source bank and actual load plan differ");
            }
            sources.add(new SourceProgram(entry, source.readerBytes()));
        }
        return new CdR11aSectionArt(plan.plcs(), plan.sections(), sources, assets.identity());
    }

    /** Authenticated full loading source adapter, no clear/decode/service/World authority. */
    public static CdR11aSectionArt fromAssetsForLoading(CdR11aAssets assets)throws IOException{
        var input=assets.loadingArtInputs();var plan=assets.loadPlan();var sources=new ArrayList<SourceProgram>();
        for(var source:input.sources()){
            CdR11aLoadPlan.PlcEntry entry=null;for(var list:plan.plcs())for(var e:list.entries())if(e.source()==source.asset()&&entry==null)entry=e;
            sources.add(new SourceProgram(Objects.requireNonNull(entry),source.readerBytes()));
        }
        var art=new CdR11aSectionArt(plan.plcs(),plan.sections(),sources,assets.identity());art.loading=input;
        var binding=new CdSectionArtKernel.Binding(assets.identity(),assets.identity().file(),assets.identity().domain());
        var lists=new HashMap<Integer,List<CdR11aPlcBuffer.Entry>>();for(var list:plan.plcs())lists.put(list.nativeId(),list.entries().stream().map(e->new CdR11aPlcBuffer.Entry(e.sourceRange().nativeAddress(),e.vramAddress())).toList());
        var registered=new HashMap<Long,CdSectionArtKernel.Source>();for(var source:input.sources()){var r=source.include();registered.put(Integer.toUnsignedLong(r.nativeAddress()),new CdSectionArtKernel.Source(binding,source.asset(),r.file(),r.domain(),r.fileOffset(),r.length(),Integer.toUnsignedLong(r.nativeAddress()),source.readerBytes()));}
        var b=input.blocks();var r=b.include();var blocks=new CdSectionArtKernel.Source(binding,b.asset(),r.file(),r.domain(),r.fileOffset(),r.length(),Integer.toUnsignedLong(r.nativeAddress()),b.readerBytes());
        art.immediate=new CdImmediateArtKernel<>(lists,registered,blocks,()->assets.decoded(com.openggf.game.soniccd.level.CdR11aAsset.LEVEL_BLOCKS),input.lifeIcon(),assets.profile().range(com.openggf.game.soniccd.level.CdR11aAsset.ART_LIFE_ICON).nativeAddress(),art.new ImmediateStates());
        return art;
    }
    /** Same immutable facade and existing kernels, selected from authentic programme metadata.
     * No decoded art, queue state, residency pixels, storage clears or execution order supplied. */
    public static CdR11aSectionArt fromLoadingSource(CdLevelLoadingSource actual)throws IOException {
        Objects.requireNonNull(actual);
        if(actual.hasOriginalR11Aggregate())
            return fromAssetsForLoading(actual.originalR11Assets());
        return new CdR11aSectionArt(actual);
    }
    private CdR11aSectionArt(CdLevelLoadingSource actual)throws IOException {
        loadingSource=actual;
        var binding=new CdSectionArtKernel.Binding(actual,actual.file(),
            com.openggf.game.soniccd.media.CdMmdImage.Domain.ORIGINAL_WORD_RAM);
        var lists=new HashMap<Integer,List<CdR11aPlcBuffer.Entry>>();
        for(var list:actual.loadTables().plcs())lists.put(list.nativeId(),list.entries().stream()
            .map(e->new CdR11aPlcBuffer.Entry(e.sourceNativeAddress(),e.vramAddress())).toList());
        var registered=new HashMap<Long,CdSectionArtKernel.Source>();
        for(var input:actual.queuedArtSources()) {
            var source=loadingProgram(actual,binding,input);registered.put(source.nativeAddress(),source);
        }
        var states=new KernelStates();
        kernel=switch(actual.loadTables().sections()) {
            case CdLevelLoadingSource.CameraSections sections->new CdSectionArtKernel<>(binding,lists,
                sections.rows().stream().map(s->new CdSectionArtKernel.Section(s.rightThresholdWord(),s.initialPlcId(),s.updatePlcId())).toList(),
                List.copyOf(registered.values()),states,CLEARED);
            case CdLevelLoadingSource.InitialQueueUpdateRts section->{
                sourceInitialQueueId=section.initialQueueId();
                yield CdSectionArtKernel.nativeSectionRts(binding,lists,List.copyOf(registered.values()),states,CLEARED);
            }
        };
        var blocks=loadingProgram(actual,binding,actual.blocksSource());
        immediate=new CdImmediateArtKernel<>(lists,registered,blocks,()->{
            try{return actual.decodedBlocks();}catch(IOException fault){throw new java.io.UncheckedIOException(fault);}
        },actual.lifeIcon(),actual.lifeIconNativeAddress(),new ImmediateStates());
    }
    private static CdSectionArtKernel.Source loadingProgram(CdLevelLoadingSource actual,
            CdSectionArtKernel.Binding binding,CdLevelLoadingSource.ArtSource input)throws IOException {
        if(!input.belongsTo(actual))throw new IllegalArgumentException("Foreign loading art source");
        return new CdSectionArtKernel.Source(binding,input,input.file(),input.domain(),input.fileOffset(),
            input.includeBytes(),input.nativeAddress(),standardSource(actual)?actual.sectionArtBytes(input):input.readerBytes());
    }
    private static boolean standardSource(CdLevelLoadingSource actual){
        return actual!=null&&!actual.hasOriginalR11Aggregate()&&actual.completion()==CdLevelLoadingSource.Completion.STANDARD
            &&(actual.file()==com.openggf.game.soniccd.CdNativeFile.R11A||actual.file()==com.openggf.game.soniccd.CdNativeFile.R11B);
    }
    public boolean ownsLoadingSource(CdLevelLoadingSource actual) {
        return actual!=null&&(loadingSource==actual||loading!=null&&actual.ownsOriginalR11(loading.assets()));
    }
    /** Exact immutable scene source/player pair; no A aggregate cast, clear or live grant. */
    public boolean ownsLoadingSource(CdLevelLoadingSource actual,CdStagePlayerSource player){
        return player!=null&&ownsLoadingSource(actual)&&actual.ownsPlayerSource(player);
    }
    public boolean ownsLoadingAssets(CdR11aAssets actual){return loading!=null&&loading.ownsAssets(actual)||standardSource(loadingSource)&&loadingSource.ownsRetainedStandardAssets(actual);}
    private void requireImmediate(State state){
        checked(state);
        boolean same=loadingSource!=null?state.residency.ownsLoadingSource(loadingSource)
            :loading!=null&&state.residency.ownsLoadingAssets(loading.assets());
        if(immediate==null||!same)throw new IllegalStateException("No full original loading art pair");
    }
    public State loadImmediateList(State state,int id){requireImmediate(state);return immediate.loadImmediateList(state,id);}
    public BlocksResult decodeBlocksToRam(State state){requireImmediate(state);var b=immediate.decodeBlocksToRam(state);return new BlocksResult(b.state(),b.bytes(),b.blockDefinitions());}
    public State loadLifeIcon(State state,int rawPeriod){requireImmediate(state);return immediate.loadLifeIcon(state,rawPeriod);}
    private State withValues(State s,CdAssetBytes table,CdR11aCameraArtResidency.State resident){return new State(this,s.sectionWord,s.queue,table,s.decoder,s.savedDecoder,s.remainingTilesWord,s.processBudgetWord,s.hintWord,s.padWord,resident,s.stopped);}
    private final class ImmediateStates implements CdImmediateArtKernel.States<State,CdR11aCameraArtResidency.State>{
        public void checked(State s){CdR11aSectionArt.this.checked(s);}public CdAssetBytes codeTable(State s){return s.codeTable;}public CdR11aCameraArtResidency.State residency(State s){return s.residency;}
        public int remainingTilesWord(State s){return s.remainingTilesWord;}public CdR11aPlcBuffer.State queue(State s){return s.queue;}
        public State withValues(State s,CdAssetBytes table,CdR11aCameraArtResidency.State r){return CdR11aSectionArt.this.withValues(s,table,r);}
        public RuntimeException failure(CdImmediateArtKernel.Reason reason,State s,CdImmediateArtKernel.Operation op,long source,int destination,CdAssetBytes prefix,Throwable cause){var f=CdR11aSectionArt.this.failure(Reason.valueOf(reason.name()),s,source,destination,cause);f.operation=Operation.valueOf(op.name());f.ramPrefix=prefix;return f;}
        public void admitDestination(State s,int destination,CdImmediateArtKernel.Operation op,long source){try{CdR11aSectionArt.this.admitDestination(s,destination);}catch(SectionArtException f){throw failure(CdImmediateArtKernel.Reason.RESIDENCY,s,op,source,destination,null,f.getCause());}}
        public CdR11aCameraArtResidency.State commitTile(State s,int destination,CdAssetBytes bytes,CdImmediateArtKernel.Operation op,long source){try{return CdR11aCameraArtResidency.writeTile(s.residency,destination,bytes);}catch(CdR11aCameraArtResidency.OwnershipBoundary cause){throw failure(CdImmediateArtKernel.Reason.RESIDENCY,s,op,source,destination,null,cause);}}
    }

    /** Explicit DEV ingress, never a native cold/title/restart or zero-VRAM assertion. */
    public State devState(CdR11aCameraArtResidency.State resident, CdAssetBytes table512, int rawSectionWord) {
        Objects.requireNonNull(resident, "resident"); Objects.requireNonNull(table512, "table512");
        word(rawSectionWord);
        if (table512.length() != 512) throw new IllegalArgumentException("Explicit 512-byte code table required");
        return new State(this, rawSectionWord, CdR11aPlcBuffer.cleared(), table512, null,
                CLEARED, 0, 0, 0, 0, resident, false);
    }

    /** Explicit inactive native values, e.g. the actual original BRAM/MAINVARS clear products.
     * No DEV preload/clear, source execution, active decoder, or World authority is inferred.
     * The source-bound caller owns the provenance of every supplied field and current byte. */
    public State fromInactiveValues(CdR11aCameraArtResidency.State resident,CdAssetBytes table512,
            int rawSectionWord,CdR11aPlcBuffer.State queue,SavedDecoder saved,
            int remainingTilesWord,int processBudgetWord,int hintWord,int padWord){
        Objects.requireNonNull(resident);Objects.requireNonNull(table512);Objects.requireNonNull(queue);Objects.requireNonNull(saved);
        if(loadingSource!=null&&!resident.ownsLoadingSource(loadingSource))throw new IllegalArgumentException("Foreign current loading residency/source");
        word(rawSectionWord);word(remainingTilesWord);word(processBudgetWord);word(hintWord);word(padWord);
        if(remainingTilesWord!=0)throw new IllegalArgumentException("Inactive ingress cannot replace an active typed decoder");
        if(table512.length()!=512)throw new IllegalArgumentException("Explicit512-byte code table required");
        return new State(this,rawSectionWord,queue,table512,null,saved,0,processBudgetWord,hintWord,padWord,resident,false);
    }
    /** Pure owner/retirement validation, no PLC operation or state repair. */
    public void validateState(State state){checked(state);}
    /** Main278 native CLR.B sectionID (the HIGH byte), retaining active queue/decoder,
     * NEMBUFFER, low byte and current pixels. Only the original loader owns invocation. */
    /** Native do_hblank WORD aliases this existing captured hint word. */
    public State writeHintWord(State state,int value){checked(state);word(value);return new State(this,state.sectionWord,state.queue,state.codeTable,state.decoder,state.savedDecoder,state.remainingTilesWord,state.processBudgetWord,value,state.padWord,state.residency,state.stopped);}
    public State clearSectionHighByte(State state){checked(state);return new State(this,state.sectionWord&255,state.queue,state.codeTable,state.decoder,state.savedDecoder,state.remainingTilesWord,state.processBudgetWord,state.hintWord,state.padWord,state.residency,state.stopped);}
    /** Results/Main.asm282 CLR.W, retaining the current art and every decoder field. */
    public State clearSectionWord(State state){checked(state);return new State(this,0,state.queue,state.codeTable,state.decoder,state.savedDecoder,state.remainingTilesWord,state.processBudgetWord,state.hintWord,state.padWord,state.residency,state.stopped);}



    /** Current font tile writer; actual shared HUD Cursor alone chooses its source/destination order.
     * Source/World invocation stays with CdWorld. No PLC/table/decoder service is performed. */
    public State writeCurrentPowerupTile(State state,int destination,CdAssetBytes bytes){
        if(loading==null&&standardSource(loadingSource)){
            checked(state);
            if(!loadingSource.standardPowerupDestination(destination)||destination<CdPowerupArtRuntime.VRAM_BYTE_ADDRESS
                    ||destination>CdPowerupArtRuntime.VRAM_BYTE_ADDRESS+CdPowerupArtRuntime.WINDOW_BYTES-32||bytes.length()!=32)
                throw new IllegalArgumentException("Actual powerup current tile");
            return withValues(state,state.codeTable,CdR11aCameraArtResidency.writeTile(state.residency,destination,bytes));
        }
checked(state);if((loading==null&&(loadingSource==null||!loadingSource.objectSource().powerupDestination(destination)))||destination<CdPowerupArtRuntime.VRAM_BYTE_ADDRESS||destination>CdPowerupArtRuntime.VRAM_BYTE_ADDRESS+CdPowerupArtRuntime.WINDOW_BYTES-32||bytes.length()!=32)throw new IllegalArgumentException("Actual powerup current tile");return withValues(state,state.codeTable,CdR11aCameraArtResidency.writeTile(state.residency,destination,bytes));
    }
    /** Reached stage-animation CPU tile publication, distinct from HUD/powerup windows.
     * Metadata selects only the authenticated R41A native $4500/$4700/$4900 windows.
     * The live Module call owns invocation; this immutable writer grants none. */
    public State writeCurrentStageAnimationTile(State state,int destination,CdAssetBytes tile){
        checked(state);Objects.requireNonNull(tile);
        if(loadingSource==null||loadingSource.artStrategy()!=CdR4ArtServiceMetadata.Strategy.ACT1_PRESENT_WINDOW)
            throw new IllegalStateException("No original stage-animation source");
        if(tile.length()!=32||(destination&31)!=0||destination<0x4500||destination>0x4AE0)
            throw new IllegalArgumentException("Outside actual R41A stage-animation tile windows");
        return withValues(state,state.codeTable,CdR11aCameraArtResidency.writeTile(state.residency,destination,tile));
    }
    public State writeCurrentHudTile(State state,int destination,CdAssetBytes tile){
        checked(state);boolean admitted;
        if(loading!=null){
            try{admitted=loading.assets().startupObjects().hudDestination(destination);}
            catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        }else if(loadingSource!=null){
            admitted=standardSource(loadingSource)?loadingSource.standardHudDestination(destination):loadingSource.objectSource().hudDestination(destination);
        }else throw new IllegalStateException("No actual loading source");
        if(!admitted)throw new IllegalArgumentException("Outside authenticated original HUD destinations");
        if(tile.length()!=32)throw new IllegalArgumentException("One actual font tile required");
        return withValues(state,state.codeTable,CdR11aCameraArtResidency.writeTile(state.residency,destination,tile));
    }

    /** UpdateSectionArt stores the addressed HIGH byte before InitPLC. */
    public State updateSection(State state, int cameraXword) {
        return kernel.updateSection(state, cameraXword);
    }

    /** LoadSectionArt stores HIGH then appends the complete initial list without cancel. */
    public State loadSection(State state, int cameraXword) {
        return sourceInitialQueueId>=0?kernel.appendList(state,sourceInitialQueueId):kernel.loadSection(state, cameraXword);
    }

    /**
     * ClearPLCs:651-658 and _Definitions:225-235:96 descriptor bytes PLUS32 bytes of saved
     * decoder/count/budget/hint/pad RAM. NEMBUFFER512, section and resident art are outside it.
     * A prior decoder object cannot remain a resumable continuation after its native count clears.
     */
    public State clearPlcs(State state) {
        return kernel.clearPlcs(state);
    }

    /** Resolve before ClearPLCs; clear the owned128 CPU bytes, not NEMBUFFER or residency. */
    public State initList(State state, int id) {
        return kernel.initList(state, id);
    }

    /** Native source-long free scan, including for an empty list; active work survives. */
    public State appendList(State state, int id) {
        return kernel.appendList(state, id);
    }

    /** ProcessPLCs prepares only. Header0 still builds table and prefetches; no silent POP. */
    public State prepare(State state) {
        return kernel.prepare(state);
    }

    /** scrollLock.b addresses the HIGH byte of this raw big-endian WORD. */
    public State serviceSlow(State state, int scrollLockWORD) {
        return kernel.serviceSlow(state, scrollLockWORD);
    }

    public State serviceFast(State state) {
        return kernel.serviceFast(state);
    }



    /** Admission uses physical extent, not known-ingress pixels or producer identity. */
    private void admitDestination(State state, int destination) {
        for (var region : state.residency.windows()) {
            if (destination >= region.destinationWord()
                    && destination <= region.destinationWord() + region.byteLength() - 32) return;
        }
        // Obtain the existing typed outside-owner cause without attempting a
        // decode or write. This path cannot return any tile from an owned region.
        try {
            CdR11aCameraArtResidency.readTile(state.residency, destination >>> 5);
        } catch (CdR11aCameraArtResidency.OwnershipBoundary boundary) {
            throw failure(Reason.RESIDENCY, state, state.decoder==null?0:state.decoder.sourcePointer(), destination, boundary);
        }
        throw new IllegalStateException("Residency extent and native reader disagree");
    }





    private State checked(State state) {
        Objects.requireNonNull(state, "state");
        if (state.owner != this) throw new IllegalArgumentException("Foreign section/source/state owner");
        if(loadingSource!=null&&!state.residency.ownsLoadingSource(loadingSource))
            throw new IllegalArgumentException("Foreign current loading residency/source");
        if (state.stopped) throw new IllegalStateException("Reached boundary is terminal; no midrow continuation");
        return state;
    }





    private SectionArtException failure(Reason reason, State state, long source, int destination, Throwable cause) {
        State terminal = new State(this, state.sectionWord, state.queue, state.codeTable, state.decoder,
                state.savedDecoder, state.remainingTilesWord, state.processBudgetWord,
                state.hintWord, state.padWord, state.residency, true);
        return new SectionArtException(reason, terminal, source, destination, cause);
    }



    /**
     * Internal immutable-value glue only. The facade retains its exact State, identity,
     * exception and residency contracts; this supplies no lifecycle or execution authority.
     */
    private final class KernelStates implements CdSectionArtKernel.States<State, CdR11aCameraArtResidency.State> {
        @Override public void checked(State state) { CdR11aSectionArt.this.checked(state); }
        @Override public int sectionWord(State state) { return state.sectionWord; }
        @Override public CdR11aPlcBuffer.State queue(State state) { return state.queue; }
        @Override public CdAssetBytes codeTable(State state) { return state.codeTable; }
        @Override public CdNemesisPlcDecoder.State decoder(State state) { return state.decoder; }
        @Override public SavedDecoder savedDecoder(State state) { return state.savedDecoder; }
        @Override public int remainingTilesWord(State state) { return state.remainingTilesWord; }
        @Override public int processBudgetWord(State state) { return state.processBudgetWord; }
        @Override public int hintWord(State state) { return state.hintWord; }
        @Override public int padWord(State state) { return state.padWord; }
        @Override public CdR11aCameraArtResidency.State residency(State state) { return state.residency; }
        @Override public boolean stopped(State state) { return state.stopped; }

        @Override public State make(int sectionWord, CdR11aPlcBuffer.State queue, CdAssetBytes codeTable,
                                    CdNemesisPlcDecoder.State decoder, SavedDecoder savedDecoder,
                                    int remainingTilesWord, int processBudgetWord, int hintWord, int padWord,
                                    CdR11aCameraArtResidency.State resident, boolean stopped) {
            return new State(CdR11aSectionArt.this, sectionWord, queue, codeTable, decoder, savedDecoder,
                    remainingTilesWord, processBudgetWord, hintWord, padWord, resident, stopped);
        }
        @Override public SectionArtException failure(Reason reason, State state, long source,
                                                    int destination, Throwable cause) {
            return CdR11aSectionArt.this.failure(reason, state, source, destination, cause);
        }
        @Override public void admitDestination(State state, int destination) {
            CdR11aSectionArt.this.admitDestination(state, destination);
        }
        @Override public CdR11aCameraArtResidency.State writeTile(State state, int destination, CdAssetBytes bytes) {
            try {
                return CdR11aCameraArtResidency.writeTile(state.residency, destination, bytes);
            } catch (CdR11aCameraArtResidency.OwnershipBoundary boundary) {
                throw CdR11aSectionArt.this.failure(Reason.RESIDENCY, state,
                        state.decoder==null?0:state.decoder.sourcePointer(), destination, boundary);
            }
        }
    }

    private static void declare(Map<Long, CdR11aLoadPlan.PlcEntry> declared, CdR11aLoadPlan.PlcEntry entry) {
        long source = entry.sourceRange().nativeAddress();
        var existing = declared.putIfAbsent(source, entry);
        if (existing != null && (existing.source() != entry.source()
                || !existing.sourceRange().equals(entry.sourceRange()))) {
            throw new IllegalArgumentException("Ambiguous native producer/source extent");
        }
    }

    private static void checkedEntry(CdR11aLoadPlan.PlcEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.source(), "source"); Objects.requireNonNull(entry.sourceRange(), "sourceRange");
        word(entry.vramAddress());
        if ((entry.vramAddress() & 31) != 0) throw new IllegalArgumentException("PLC destination must align to32");
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Native WORD must be unsigned16");
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
