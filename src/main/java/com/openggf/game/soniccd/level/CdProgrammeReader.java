package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.*;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.util.*;

/** Actual original-USA source custody and shared finite algorithms; never live publication authority. */
final class CdProgrammeReader {
    enum Borrow { MUSIC_CONSUMER,MUSIC_COMMANDS,SCROLL_CONSUMER,COMMON_EVENTS,ZONE_DIRECTORY,
                  CCZ_DISPATCH,ACT_DIRECTORY,CCZ12,HUD_UPDATE,HUD_RESET,HUD_DIVISORS,HUD_VINT }
    enum Blocks { R31 }
    enum LoadingBorrow { R43C_SPAWN }
    enum Role { ANI, MINI, SPECIAL, SINE, ATAN, MAP, DPLC, ART, CHUNKS, FG, BG, COLLISION, ANGLE, HEIGHT, WIDTH, PLACEMENTS, RAMP }
    enum LoadRole { PLC, PALETTE, CYCLES, SECTIONS, BG_POINTERS, PAL_TITLE, PAL_SELECT, PAL_SONIC, PAL_LEVEL, PAL_END }
    private record Source(CdProgrammeDefinition.Compressed definition,int header) { }
    private final CdProgrammeDefinition definition;
    private final CdMmdImage image;
    private final String discSha;
    // Enum identity for legacy keys; exact canonical declaration identity for TT keys.
    // Equal/foreign public Range values can never select a registered source.
    private final Map<Object,CdProgrammeDefinition.Span> spans=new IdentityHashMap<>();
    private final Map<Object,CdAssetBytes> views=new IdentityHashMap<>();
    private final Map<Object,Source> compressed=new IdentityHashMap<>();
    private final Map<Long,Object> plcSources=new HashMap<>();
    private final Map<Object,CdAssetBytes[]> inputs=new IdentityHashMap<>();
    private final Map<Object,CdNemesisAsset.Decoded> products=new IdentityHashMap<>();
    private final Optional<CdSonicVisualAssets> sonic;
    private CdObjectPlacements placements;
    private CdStageTerrain terrain;
    private CdProgrammeLoadTables loadTables;
    private CdStagePlayerSource playerSource;
    private CdR11aPlayerAssets originalR11Player;
    private CdR11aLoadPlan originalR11Plan;
    private CdR11aLoadPlan.Entry standardPpzEntry;
    private CdR11aTerrainProducts originalR11Terrain;
    private CdLevelLoadingSource loadingSource;
    private CdProgrammeObjectSource objectSource;
    private final Map<CdProgrammeObjectSource.Table,CdAssetBytes> objectViews=new EnumMap<>(CdProgrammeObjectSource.Table.class);

    private CdProgrammeReader(CdProgrammeDefinition definition,CdMmdImage image,String discSha)throws IOException {
        this.definition=definition;this.image=image;this.discSha=discSha;
        verifyImage();
        for(var operand:definition.operands())operand(operand.offset(),operand.hex());
        for(var span:definition.raw()) {
            if(spans.put(span.key(),span)!=null)throw new IllegalArgumentException(name()+" duplicate fixed source key");
            var bytes=read(span.offset(),span.length());
            if(!span.sha().equals(CdR11aProfile.sha256(bytes.copy())))
                throw new IOException(name()+" original source span differs: "+span.key());
            views.put(span.key(),bytes);
        }
        for(var source:definition.sources()) {
            var include=source.include();
            int header=source.header().isPresent()?source.header().getAsInt():view(include.key()).unsignedWord(0);
            if(include.offset()<0 || include.length()<2 || include.length()>image.fileBytes()-include.offset()
                    || (header&~65535)!=0 || (header&0x7FFF)*32!=source.output()
                    || source.output()<0 || source.output()>source.capacity())
                throw new IllegalArgumentException(name()+" fixed source extent differs");
            if(source.javaProduct().isPresent()) {
                var java=source.javaProduct().orElseThrow();int size=java.input().length();
                if(size<include.length() || size>include.length()+1 || size>image.fileBytes()-include.offset()
                        || java.consumed()>size || java.tableBytes()<0 || java.tableBytes()>java.consumed())
                    throw new IllegalArgumentException(name()+" fixed source extent differs");
            }
            // Header-only definitions retain real raw includes, not decoder cursors inferred from them.
            var raw=views.get(source.key());
            if(raw!=null && raw.unsignedWord(0)!=header)
                throw new IOException(name()+" original compressed header differs: "+source.key());
            if(compressed.put(source.key(),new Source(source,header))!=null)
                throw new IllegalArgumentException(name()+" duplicate compressed source key");
            if(source.plc() && plcSources.putIfAbsent(0x200000L+include.offset(),source.key())!=null)
                throw new IllegalArgumentException(name()+" duplicate PLC source address");
        }
        sonic=switch(definition.visual()) {
            case CdProgrammeDefinition.Visual visual -> Optional.of(CdSonicVisualAssets.decode(visual.format(),
                view(visual.mapping()).copy(),view(visual.dplc()).copy(),view(visual.art()).copy()));
            case CdProgrammeDefinition.UnsupportedVisual ignored -> Optional.empty();
        };
    }
    static CdProgrammeReader open(CdProgramAssets actual,CdStageKey selected)throws IOException {
        Objects.requireNonNull(actual,"actual complete-disc data source");Objects.requireNonNull(selected,"explicit selected stage");
        var definition=CdProgrammeDefinition.select(selected);var payload=definition.payload();String name=payload.name();
        if(actual.header().observedVariant().orElse(null)!=CdReleaseVariant.USA)throw new IOException(name+" requires original USA media");
        var release=CdRetailReleases.find(actual.identity()).orElseThrow(()->new IOException(name+" requires a known complete-disc content profile"));
        if(release.variant()!=CdReleaseVariant.USA)throw new IOException(name+" source release differs");
        var catalogue=actual.catalogue().orElseThrow(()->new IOException(name+" source catalogue unavailable"));
        if(catalogue.release()!=CdReleaseVariant.USA || catalogue.stage(selected)!=payload || payload.file()!=definition.file()
                || !payload.stage().equals(selected))throw new IOException(name+" catalogue stage/file/release differs");
        return new CdProgrammeReader(definition,actual.mmd(definition.file()),actual.identity().normalizedSha256());
    }
    /** Fixed package-owned bridge for the existing actual-media R11A loader. The image is
     * borrowed from THIS reader; no caller image, descriptor or arbitrary address is admitted. */
    CdMmdImage retainedOriginalR11Image() {
        if(definition!=CdProgrammeDefinition.select(CdStagePayload.R11A.stage()))
            throw new IllegalArgumentException("The existing original R11A loader requires its canonical reader");
        requireRetained();return image;
    }
    CdPpzDataLayout.Programme standardPpzProgramme(){
        if(definition==CdProgrammeDefinition.select(CdStagePayload.R11A.stage()))return CdPpzDataLayout.Programme.R11A;
        if(definition==CdProgrammeDefinition.select(CdStagePayload.R11B.stage()))return CdPpzDataLayout.Programme.R11B;
        throw new IllegalArgumentException("The existing original R11A loader requires its canonical reader");
    }
    CdMmdImage retainedStandardPpzImage(){
        if(standardPpzProgramme()==CdPpzDataLayout.Programme.R11A)return retainedOriginalR11Image();
        requireRetained();return image;
    }
    CdAssetBytes standardPpzLoadingView(CdPpzProgrammeProfiles.R11LoadingView role){
        return view(CdPpzProgrammeProfiles.loadingKey(standardPpzProgramme(),role));
    }
    int standardPpzLoadingOffset(CdPpzProgrammeProfiles.R11LoadingView role){
        return CdPpzProgrammeProfiles.loadingOffset(standardPpzProgramme(),role);
    }
    synchronized CdR11aLoadPlan.Entry standardPpzEntry(){
        var p=standardPpzProgramme();
        if(p==CdPpzDataLayout.Programme.R11A)return originalR11LoadPlan().entry();
        retainedStandardPpzImage();
        if(standardPpzEntry==null)standardPpzEntry=CdR11aLoadPlan.readEntry(
            ()->view(p.range("LevelDataIndex")),()->view(p.range("LevelStartLoc")),()->view(p.range("CamBounds")),
            bytes->CdR11aLoadPlan.decodeLevelIndex(bytes,(0x200000+p.range("Art_LevelTiles").fileOffset()),
                (0x200000+p.range("LevelBlocks").fileOffset()),(0x200000+p.range("LevelChunks").fileOffset()),0x81,0x0505),
            ()->{
                var layouts=view(p.range("LevelLayouts"));
                CdR11aLoadPlan.validatePlanePointers(layouts,p.range("LevelLayouts").fileOffset(),
                    p.range("ForegroundRead").fileOffset(),p.range("BackgroundRead").fileOffset());
                CdR11aLoadPlan.validateCollisionPointers(view(CdPpzProgrammeProfiles.R11PastLoadingView.COLLISION_INDEX),
                    (0x200000+p.range("LevelCollision").fileOffset()));
            });
        return standardPpzEntry;
    }
    void requireOriginalR11Image(CdMmdImage actual) {
        if(actual!=image)throw new IllegalArgumentException("Foreign original R11A retained image");
        retainedOriginalR11Image();
    }
    private String name() { return definition.payload().name(); }
    private CdAssetBytes read(int offset,int length) {return retainedSpan(image,offset,length,name());}
    /** Shared finite original-file reader. Caller authenticates its private retained image relation. */
    static CdAssetBytes retainedSpan(CdMmdImage image,int offset,int length,String name){
        Objects.requireNonNull(image);
        if(offset<0||length<0||offset>image.fileBytes()||length>image.fileBytes()-offset)
            throw new IllegalArgumentException(name+" finite original span");
        return CdAssetBytes.copyOf(image.readFile(offset,length));
    }
    private void verifyImage()throws IOException {
        Objects.requireNonNull(image,"actual selected original MMD");
        if(image.fileBytes()!=definition.fileBytes() || !definition.header().equals(image.header())
                || image.originalWordRam().domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || image.fileBackedCopyBytes()!=0 || image.unresolvedCopyBytes()!=0
                || !definition.sha().equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes()))))
            throw new IOException("Original USA "+name()+" image/header differs");
    }
    private void operand(int offset,String hex)throws IOException {
        if(!HexFormat.of().formatHex(read(offset,hex.length()/2).copy()).equals(hex))
            throw new IOException(name()+" original selected operand differs at "+Integer.toHexString(offset));
    }
    synchronized CdAssetBytes view(Object key) {
        Objects.requireNonNull(key);var bytes=views.get(key);
        if(bytes==null) {
            var fixed=definition.borrowed().get(key);
            if(fixed!=null) {
                bytes=read(fixed.offset(),fixed.length());
                if(fixed.sha()!=null && !fixed.sha().equals(CdR11aProfile.sha256(bytes.copy())))
                    throw new IllegalArgumentException(name()+" original borrowed source differs: "+key);
                views.put(key,bytes);
            }
        }
        if(bytes==null)throw new IllegalArgumentException(name()+" foreign fixed source key: "+key);
        return bytes;
    }
    /** Finite original R41 script view, anchored by its authenticated adjacent definition.
     * Whole original image retention is checked before the borrow. No arbitrary public address reader. */
    synchronized CdAssetBytes loadingAnimationTables(){
        requireRetained();var p=definition.tt().orElseThrow();
        if(p!=CdR4DataLayout.Programme.R41A)throw new UnsupportedOperationException("No selected original loading script profile");
        int end=p.range("StageDataIndex").fileOffset();return read(end-3*82,3*82);
    }
    /** Finite semantic object tables only. Caller cannot supply an arbitrary address or equal descriptor. */
    synchronized CdAssetBytes objectView(CdProgrammeObjectSource.Table table){
        Objects.requireNonNull(table);
        if(definition.tt().orElse(null)!=CdR4DataLayout.Programme.R41A)
            throw new UnsupportedOperationException("Original object table profile currently binds R41A only");
        var cached=objectViews.get(table);if(cached!=null)return cached;
        var bytes=table==CdProgrammeObjectSource.Table.DISPATCH?view(CdR4DataLayout.Programme.R41A.range("ObjectIndex")):read(table.fileOffset(),table.length());
        if(!table.sha256().equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("Retained original object table differs: "+table);
        objectViews.put(table,bytes);return bytes;
    }
    /** One semantic reached BubbleSpawner pattern BYTE. Raw pointer and unsigned index stay intact.
     * The finite owner includes the preceding complete RTS and following complete USA CMPI.B, not
     * arbitrary original-image bytes. Unknown dirty addresses stop at this read after child stores. */
    int bubblePatternByte(int pointerBits,int unsignedIndex){
        if(definition.tt().orElse(null)!=CdR4DataLayout.Programme.R41A)throw new UnsupportedOperationException("Original bubble pattern source profile");
        if((unsignedIndex&~255)!=0)throw new IllegalArgumentException("Bubble pattern unsigned BYTE index");
        var table=CdProgrammeObjectSource.Table.BUBBLE_PATTERN_READS;long base=Integer.toUnsignedLong(table.nativeAddress());
        long address=Integer.toUnsignedLong(pointerBits)+unsignedIndex;
        if(address<base||address>=base+table.length())throw new CdProgrammeObjectSource.UnownedBubblePatternRead(pointerBits,unsignedIndex);
        return objectView(table).unsignedByte(Math.toIntExact(address-base));
    }
    synchronized CdProgrammeObjectSource objectSource(){
        if(objectSource==null)objectSource=CdProgrammeObjectSource.fromReader(this);return objectSource;
    }
    private Source source(Object key) {
        var source=compressed.get(Objects.requireNonNull(key));
        if(source==null)throw new IllegalArgumentException(name()+" foreign compressed source key: "+key);
        return source;
    }
    synchronized CdAssetBytes canonicalInput(Object key) { return input(key,0); }
    synchronized CdAssetBytes javaInput(Object key) { return input(key,1); }
    synchronized CdAssetBytes nativeInput(Object key) { return input(key,2); }
    private CdAssetBytes input(Object key,int kind) {
        var actual=source(key);var source=actual.definition();
        var extent=switch(kind) {
            case 0 -> new CdProgrammeDefinition.ReadExtent(source.include().length(),source.include().sha());
            case 1 -> source.javaProduct().orElseThrow(()->new UnsupportedOperationException(name()+" Java decoder profile unavailable: "+key)).input();
            case 2 -> source.nativeInput().orElseThrow(()->new UnsupportedOperationException(name()+" native prefetch profile unavailable: "+key));
            default -> throw new IllegalArgumentException("Unowned input kind");
        };
        var cache=inputs.get(key);if(cache!=null && cache[kind]!=null)return cache[kind];
        CdAssetBytes bytes=null;
        if(cache!=null)for(var existing:cache)if(existing!=null && existing.length()==extent.length()) {bytes=existing;break;}
        if(bytes==null)bytes=read(source.include().offset(),extent.length());
        if(bytes.unsignedWord(0)!=actual.header() || !extent.sha().equals(CdR11aProfile.sha256(bytes.copy())))
            throw new IllegalArgumentException(name()+" original bounded compressed source differs");
        if(cache==null) {cache=new CdAssetBytes[3];inputs.put(key,cache);}
        cache[kind]=bytes;return bytes;
    }
    synchronized CdNemesisAsset.Decoded decoded(Object key)throws IOException {
        var source=source(key).definition();
        var java=source.javaProduct().orElseThrow(()->new UnsupportedOperationException(name()+" Java decoder profile unavailable: "+key));
        var cached=products.get(key);if(cached!=null)return cached;
        var result=CdNemesisAsset.decode(javaInput(key).copy(),source.output(),source.capacity());
        if(result.consumedInputBytes()!=java.consumed() || !java.outputSha().equals(CdR11aProfile.sha256(result.bytes().copy())))
            throw new IOException(name()+" existing Java source product/cursor differs");
        products.put(key,result);return result;
    }
    CdSonicVisualAssets sonic() { return sonic.orElseThrow(()->new UnsupportedOperationException(((CdProgrammeDefinition.UnsupportedVisual)definition.visual()).reason())); }
    void requireRetained() { try {verifyImage();} catch(IOException fault) {throw new IllegalStateException("Retained "+name()+" source differs",fault);} }
    CdProgrammeDefinition definition() { return definition; }
    CdStageKey stage() { return definition.payload().stage(); }
    CdNativeFile file() { return definition.file(); }
    String mmdSha() { return definition.sha(); }
    String discSha() { return discSha; }
    private CdProgrammeDefinition.R3 r3() {
        return definition.r3().orElseThrow(()->new UnsupportedOperationException(name()+" has no R3 terrain/player binding"));
    }
    CdProgrammeDefinition.Player playerBinding() {
        if(definition()!=definition)throw new IllegalArgumentException("Selected player definition and retained source differ");
        // Keep the historical unowned-family error, without reading or staging any table.
        return definition.player().orElseGet(()->{r3();throw new IllegalStateException("Missing reviewed player binding");});
    }
    int ordinaryZoneActWord() { return playerBinding().ordinaryZoneActWord(); }
    int playerMappingAddress() { return 0x200000+fileOffset(playerBinding().visual().mapping()); }
    /** Identity is checked against private retained products, never against public equal metadata. */
    void requirePlayerRelation(CdSonicVisualAssets selected,int mapping) {
        var binding=playerBinding();var span=spans.get(binding.visual().mapping());
        if(selected!=sonic.orElseThrow() || span==null || mapping!=0x200000+span.offset())
            throw new IllegalArgumentException("Selected player visual/mapping and retained source differ");
    }
    void requirePlayerTerrain(CdStageTerrain selected) {
        if(selected!=terrain)throw new IllegalArgumentException("Selected player terrain and retained source differ");
    }
    CdAssetBytes playerRole(Role role) {
        // The existing R3 path preserves its exact role/read/error order.
        if(definition.tt().isEmpty())return role(role);
        var key=playerBinding().roles().get(Objects.requireNonNull(role));
        if(key==null)throw new IllegalArgumentException("Unowned player table role: "+role);
        var selected=role(role);
        if(selected!=views.get(key))throw new IllegalArgumentException("Selected player table and retained source differ: "+role);
        return selected;
    }
    CdStageTerrain.Shape terrainShape() { return r3().shape(); }
    CdAssetBytes role(Role role) {
        var data=definition.terrain();
        if(data.isPresent()) {
            var key=data.orElseThrow().roles().get(Objects.requireNonNull(role));
            if(key!=null)return view(key);
        }
        var player=definition.player();
        if(player.isPresent()) {
            var key=player.orElseThrow().roles().get(Objects.requireNonNull(role));
            if(key!=null)return view(key);
        }
        return view(r3().roles().get(Objects.requireNonNull(role)));
    }
    synchronized CdStageTerrain terrain()throws IOException {
        var binding=definition.terrain().orElseThrow(()->new UnsupportedOperationException(name()+" has no reviewed terrain DATA binding"));
        if(terrain==null)terrain=CdStageTerrain.decodeData(binding.profile(),binding.legacyShape(),decoded(binding.blocks()).bytes().copy(),decoded(binding.graphics()).bytes().copy(),
            role(Role.CHUNKS).copy(),role(Role.FG).copy(),role(Role.BG).copy(),role(Role.COLLISION).copy(),role(Role.ANGLE).copy(),role(Role.HEIGHT).copy(),role(Role.WIDTH).copy());
        return terrain;
    }
    Object loadKey(LoadRole role) { return definition.loadKey(role); }
    CdAssetBytes loadView(LoadRole role) { return view(loadKey(role)); }
    int loadFileOffset(LoadRole role) { return spans.get(loadKey(role)).offset(); }
    CdAssetBytes loadViewForKey(Object key) {
        var role=definition.roleFor(key);
        return role.isPresent()?loadView(role.orElseThrow()):view(key);
    }
    int fileOffset(Object key) {
        var span=spans.get(Objects.requireNonNull(key));
        if(span==null)throw new IllegalArgumentException(name()+" foreign fixed source key: "+key);
        return span.offset();
    }
    Object loadSource(long nativeAddress) {
        var key=plcSources.get(nativeAddress);
        if(key==null)throw new IllegalArgumentException(name()+" unknown PLC compressed source: "+Long.toHexString(nativeAddress));
        return key;
    }
    int loadOutputBytes(Object key) { return source(key).definition().output(); }
    /** One semantic PPZ DATA cache per retained reader. The original A path delegates to
     * its existing bridge, preserving that source/read/error order and product identity.
     * Other registered graphs cannot fill this cache; data construction grants no lifetime. */
    synchronized CdR11aPlayerAssets ppzPlayerAssets(){
        if(definition==CdProgrammeDefinition.select(CdStagePayload.R11A.stage()))return originalR11PlayerAssets();
        if(originalR11Player==null)originalR11Player=CdR11aPlayerAssets.fromPpzProgramme(this);
        else requireRetained();
        return originalR11Player;
    }
    /** Existing actual R11A caller and common DATA adapter share one immutable Player product.
     * All five extra source reads stay lazy at the original loader's player-read cut.
     * A cache hit rechecks retained custody; no capture or equal reader can select this product. */
    synchronized CdR11aPlayerAssets originalR11PlayerAssets() {
        if(originalR11Player==null)originalR11Player=CdR11aPlayerAssets.fromOriginalR11Programme(this);
        else retainedOriginalR11Image();
        return originalR11Player;
    }
    /** Original USA R11A DATA products; no legacy Shape or current block-staging authority. */
    synchronized CdR11aTerrainProducts originalR11TerrainProducts()throws IOException {
        if(originalR11Terrain==null)originalR11Terrain=new CdR11aTerrainProducts(this);
        else retainedOriginalR11Image();
        return originalR11Terrain;
    }
    synchronized void requireOriginalR11TerrainProducts(CdR11aAssets.NemesisRead blocks,
            CdR11aAssets.NemesisRead tiles,CdR11aTerrain terrain) {
        if(originalR11Terrain==null)throw new IllegalArgumentException("No retained original R11A terrain products");
        originalR11Terrain.require(blocks,tiles,terrain);
    }
    /** Original USA R11A plan retains the common parser and revalidates its exact source on reuse. */
    synchronized CdR11aLoadPlan originalR11LoadPlan() {
        if(originalR11Plan==null)originalR11Plan=CdR11aLoadPlan.fromOriginalReader(this);
        else retainedOriginalR11Image();
        return originalR11Plan;
    }
    /** One immutable borrow per actual retained reader; no reopen or equal-owner substitution. */
    synchronized CdStagePlayerSource playerSource()throws IOException {
        if(playerSource==null)playerSource=CdStagePlayerSource.fromProgramme(this);
        return playerSource;
    }
    synchronized CdLevelLoadingSource levelLoadingSource() {
        if(loadingSource==null)loadingSource=CdLevelLoadingSource.fromProgramme(this);
        else if(loadingSource.completion()==CdLevelLoadingSource.Completion.STANDARD)retainedStandardPpzImage();
        return loadingSource;
    }
    /** Only the authenticated bootstrap operand immediately preceding its registered bounds table.
     * InitScroll ends with MOVE.W #focusX,scroll_focus_x; BRA.W, then the bounds table.
     * A foreign declaration cannot select a read, even if its offset/bytes are equal. */
    int loadingCameraCentreX(CdR4DataLayout.Range bounds) {
        var selected=definition.tt().orElseThrow();
        if(selected.ranges().stream().noneMatch(r->r==bounds))throw new IllegalArgumentException("Foreign bootstrap bounds");
        int at=bounds.fileOffset()-10;var instruction=read(at,6);
        if(instruction.unsignedWord(0)!=0x31FC||instruction.unsignedWord(4)!=0xF7A0)throw new IllegalArgumentException("Original focus-X store differs");
        return instruction.unsignedWord(2);
    }
    synchronized CdObjectPlacements placements() {
        if(placements==null)placements=CdObjectPlacements.decodeProgramme(this);
        return placements;
    }
    synchronized CdProgrammeLoadTables loadTables() {
        if(loadTables==null)loadTables=CdProgrammeLoadTables.decode(this);
        return loadTables;
    }
}
