package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.level.CdR11bLoadPlan.Bounds;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.InitialCentre;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.LevelIndex;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.SectionArt;
import com.openggf.game.soniccd.level.CdR11bLoadPlan.TaggedPointer;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.level.CdR12bProfile.Asset.*;

/**
 * R12B static original-file load metadata. No executing loader, palette/current-art owner or capability.
 * N1ecc5f3a: Palmtree Panic/Load Level Data.asm:11-39,57-98;
 * Scroll (Act 2 Past).asm:21-49,67-80,143-159; Graphics Lists (Act 2 Past).asm:7-26 and full bodies;
 * Section Data (Act 2 Past).asm:7-26; Palette Data (Act 1 Past).asm:11-29.
 * Historical R11b value records are shared format vocabulary, never R11B asset/profile admission.
 */
public final class CdR12bLoadPlan {
    /** Source pointer and pattern-byte destination only; no source length, decoder, output or replay command. */
    public record PlcEntry(long sourceNativeAddress,int vramAddress) {
        public PlcEntry {
            if(sourceNativeAddress<0x200000 || sourceNativeAddress>=0x240000
                    || (vramAddress&~0xFFFF)!=0 || (vramAddress&31)!=0)
                throw new IllegalArgumentException("R12B PLC pointer/destination outside finite original metadata");
        }
    }
    public record PlcList(int nativeId,CdR12bProfile.FileRange bodyRange,List<PlcEntry> entries) {
        public PlcList {
            Objects.requireNonNull(bodyRange);entries=List.copyOf(entries);
            if(nativeId<0 || nativeId>=19 || entries.isEmpty() || entries.size()>15
                    || bodyRange.length()!=2+6*entries.size()
                    || bodyRange.fileOffset()<PLC_LISTS.fileOffset()+38
                    || bodyRange.endExclusive()>PLC_LISTS.fileOffset()+PLC_LISTS.length())
                throw new IllegalArgumentException("R12B PLC directory/body count or extent differs");
        }
    }
    public record PaletteBinding(int nativeId,int destinationWord,int longwordCounter,
                                 CdR12bProfile.Asset source,CdAssetBytes bytes) {
        public PaletteBinding {
            Objects.requireNonNull(source);Objects.requireNonNull(bytes);
            boolean sonic=nativeId==3 && source==PAL_SONIC && destinationWord==0xFB00 && longwordCounter==7;
            boolean level=nativeId==5 && source==PAL_LEVEL && destinationWord==0xFB20 && longwordCounter==23;
            if((!sonic && !level) || bytes.length()!=source.length())
                throw new IllegalArgumentException("R12B palette source/count/destination differs");
        }
        public int colorCount() { return bytes.length()/2; }
    }

    private static final String PLC_SHA256="b6cca97acae551eb32344f305de50e126cd21d7712e3f41149a992d184198a5f";
    private static final List<SectionArt> SECTIONS=List.of(new SectionArt(0xB00,2,10),new SectionArt(0x1500,6,11),
            new SectionArt(0x1800,7,12),new SectionArt(0x1C00,8,13),new SectionArt(0xFFFF,9,14));
    private final LevelIndex index;
    private final InitialCentre centre;
    private final Bounds bounds;
    private final List<PlcList> plcs;
    private final List<Long> sourcePointers;
    private final List<SectionArt> sections;
    private final List<PaletteBinding> palettes;
    private final CdAssetBytes palette;

    private CdR12bLoadPlan(LevelIndex index,InitialCentre centre,Bounds bounds,List<PlcList> plcs,
                          List<Long> sourcePointers,List<SectionArt> sections,List<PaletteBinding> palettes,byte[] palette) {
        this.index=index;this.centre=centre;this.bounds=bounds;this.plcs=List.copyOf(plcs);
        this.sourcePointers=List.copyOf(sourcePointers);this.sections=List.copyOf(sections);
        this.palettes=List.copyOf(palettes);this.palette=CdAssetBytes.copyOf(palette);
    }

    /** Format decoding only. Full original-source authentication remains in the aggregate factory. */
    public static CdR12bLoadPlan decode(CdR12bProfile profile,CdMmdImage image) {
        Objects.requireNonNull(profile);Objects.requireNonNull(image);
        if(!CdR12bProfile.originalShape(image))throw new IllegalArgumentException("R12B metadata requires its original finite image shape");
        var raw=profile.range(LEVEL_DATA_INDEX).read(image);
        var art=new TaggedPointer(raw.unsignedLong(0));var blocks=new TaggedPointer(raw.unsignedLong(4));
        if(art.plcId()!=3 || art.nativeAddress()!=ART_LEVEL_TILES.nativeAddress()
                || blocks.plcId()!=2 || blocks.nativeAddress()!=LEVEL_BLOCKS.nativeAddress()
                || raw.unsignedLong(8)!=LEVEL_CHUNKS.nativeAddress() || raw.unsignedWord(14)!=0x0505)
            throw new IllegalArgumentException("R12B index tags, pointers or palette IDs differ");
        if(raw.unsignedWord(12)!=0x0081)throw new UnsupportedOperationException("Unreviewed R12B trailing index word");
        var index=new LevelIndex(art,blocks,LEVEL_CHUNKS.nativeAddress(),raw.slice(12,2),raw.slice(14,2));
        var position=profile.range(LEVEL_START_LOC).read(image);
        var centre=new InitialCentre(position.unsignedWord(0),position.unsignedWord(2));
        var size=profile.range(CAM_BOUNDS).read(image);
        var bounds=new Bounds(size.unsignedWord(0),size.unsignedWord(2),size.unsignedWord(4),size.unsignedWord(6),size.unsignedWord(8),size.unsignedWord(10));
        var layouts=profile.range(LEVEL_LAYOUTS).read(image);int base=LEVEL_LAYOUTS.fileOffset();
        if(base+(short)layouts.unsignedWord(0)!=LAYOUT_FG.fileOffset()
                || base+(short)layouts.unsignedWord(2)!=LAYOUT_BG.fileOffset())
            throw new IllegalArgumentException("R12B native layout pointers differ");
        var collision=profile.range(LEVEL_COL_INDEX).read(image);
        for(int i=0;i<8;i++)if(collision.unsignedLong(i*4)!=LEVEL_COLLISION.nativeAddress())
            throw new IllegalArgumentException("R12B native collision pointer differs");
        var plcs=decodePlcs(profile,profile.range(PLC_LISTS).read(image));
        var pointers=new LinkedHashSet<Long>();
        for(var list:plcs)for(var entry:list.entries())pointers.add(entry.sourceNativeAddress());
        if(pointers.size()!=37)throw new IllegalArgumentException("R12B requires its37 observed PLC source pointers");
        var sections=decodeSections(profile.range(SECTION_DATA).read(image));
        var table=profile.range(PALETTE_TABLE).read(image);
        var sonic=palette(profile,image,table,3,PAL_SONIC,0xFB00,7);
        var level=palette(profile,image,table,5,PAL_LEVEL,0xFB20,23);
        byte[] colors=new byte[128];System.arraycopy(sonic.bytes().copy(),0,colors,0,32);System.arraycopy(level.bytes().copy(),0,colors,32,96);
        return new CdR12bLoadPlan(index,centre,bounds,plcs,List.copyOf(pointers),sections,List.of(sonic,level),colors);
    }

    private static List<PlcList> decodePlcs(CdR12bProfile profile,CdAssetBytes data) {
        // The full fixed native metadata span authenticates neither art lengths nor reader outcomes.
        if(!PLC_SHA256.equals(CdR11aProfile.sha256(data.copy())))
            throw new IllegalArgumentException("Original R12B PLC metadata differs");
        var lists=new ArrayList<PlcList>();
        for(int slot=0;slot<19;slot++) {
            int offset=(short)data.unsignedWord(slot*2);
            if(offset<38 || (offset&1)!=0 || offset>data.length()-2)
                throw new IllegalArgumentException("R12B signed PLC directory pointer outside reviewed span");
            int count=data.unsignedWord(offset)+1;
            if(count>15 || 2+6*count>data.length()-offset)
                throw new IllegalArgumentException("R12B PLC body exceeds reviewed span");
            var rows=new ArrayList<PlcEntry>();
            for(int row=0;row<count;row++)rows.add(new PlcEntry(data.unsignedLong(offset+2+6*row),data.unsignedWord(offset+6+6*row)));
            lists.add(new PlcList(slot,new CdR12bProfile.FileRange(profile.file(),profile.domain(),
                    PLC_LISTS.fileOffset()+offset,2+6*count),rows));
        }
        return List.copyOf(lists);
    }
    private static List<SectionArt> decodeSections(CdAssetBytes data) {
        var result=new ArrayList<SectionArt>();
        for(int i=0;i<5;i++) {
            var section=new SectionArt(data.unsignedWord(i*2),data.unsignedWord(20+i*2),data.unsignedWord(10+i*2));
            if(!section.equals(SECTIONS.get(i)))throw new IllegalArgumentException("R12B section table differs");
            result.add(section);
        }
        return List.copyOf(result);
    }
    private static PaletteBinding palette(CdR12bProfile profile,CdMmdImage image,CdAssetBytes table,
                                          int id,CdR12bProfile.Asset source,int destination,int counter) {
        int row=id*8;
        if(table.unsignedLong(row)!=source.nativeAddress() || table.unsignedWord(row+4)!=destination || table.unsignedWord(row+6)!=counter)
            throw new IllegalArgumentException("R12B palette table binding differs");
        return new PaletteBinding(id,destination,counter,source,profile.range(source).read(image));
    }

    public LevelIndex levelIndex() { return index; }
    public InitialCentre initialCentre() { return centre; }
    public Bounds bounds() { return bounds; }
    public List<PlcList> plcs() { return plcs; }
    /** First original directory/body encounter order; aliases preserve the same pointer, not new readers. */
    public List<Long> sourcePointers() { return sourcePointers; }
    public List<SectionArt> sections() { return sections; }
    public List<PaletteBinding> paletteBindings() { return palettes; }
    public CdAssetBytes initialPalette() { return palette; }
    public int colorWord(int index) { Objects.checkIndex(index,64);return palette.unsignedWord(index*2); }
}
