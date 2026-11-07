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
    enum Role { ANI, MINI, SPECIAL, SINE, ATAN, MAP, DPLC, ART, CHUNKS, FG, BG, COLLISION, ANGLE, HEIGHT, WIDTH, PLACEMENTS }
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
    private CdStageTerrain terrain;
    private CdProgrammeLoadTables loadTables;

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
    private String name() { return definition.payload().name(); }
    private CdAssetBytes read(int offset,int length) {
        if(offset<0 || length<0 || offset>image.fileBytes() || length>image.fileBytes()-offset)
            throw new IllegalArgumentException(name()+" finite original span");
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
            if(fixed!=null) {bytes=read(fixed.offset(),fixed.length());views.put(key,bytes);}
        }
        if(bytes==null)throw new IllegalArgumentException(name()+" foreign fixed source key: "+key);
        return bytes;
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
    int ordinaryZoneActWord() { return r3().ordinaryZoneActWord(); }
    CdStageTerrain.Shape terrainShape() { return r3().shape(); }
    CdAssetBytes role(Role role) { return view(r3().roles().get(Objects.requireNonNull(role))); }
    synchronized CdStageTerrain terrain()throws IOException {
        var binding=r3();
        if(terrain==null)terrain=CdStageTerrain.decode(binding.shape(),decoded(binding.blocks()).bytes().copy(),decoded(binding.graphics()).bytes().copy(),
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
    synchronized CdProgrammeLoadTables loadTables() {
        if(loadTables==null)loadTables=CdProgrammeLoadTables.decode(this);
        return loadTables;
    }
}
