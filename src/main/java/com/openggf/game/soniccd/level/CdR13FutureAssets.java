package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdHeader;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.media.CdProgramAssets;
import com.openggf.game.soniccd.release.CdRetailReleases;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Detached authenticated original-USA R13C/D source prerequisites, never FULL/Frame/World admission.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Palmtree Panic/Data (Act 3).asm:7-19,62-75;
 * Load Level Data (Act 3).asm; Graphics Lists (Act 3).asm; Object Layout (Act 3).asm;
 * Sonic/Main.asm:797-831,2645-2662,2951-3100; Functions (Misc).asm:69-230.
 * All bytes come from the retained original programme, never native source or another act.
 * PLC/palette records describe source values only; no art service, residency or display is installed.
 */
public final class CdR13FutureAssets {
    public record Identity(CdReleaseVariant variant, CdStageKey stage, CdNativeFile file,
                           CdMmdImage.Domain domain, String mmdSha256, String observedWholeDiscSha256) {
        public Identity {
            Objects.requireNonNull(variant); Objects.requireNonNull(stage); Objects.requireNonNull(file);
            Objects.requireNonNull(domain); Objects.requireNonNull(mmdSha256); Objects.requireNonNull(observedWholeDiscSha256);
            if (variant != CdReleaseVariant.USA || domain != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || file != fileForStage(stage) || !expectedHash(file).equals(mmdSha256)) {
                throw new IllegalArgumentException("Not an original USA R13 future identity");
            }
        }
    }
    /** Finite original source spans. Construction of metadata authenticates no image or runtime use. */
    public enum Asset {
        LEVEL_DATA_INDEX(0xE310, 16, 0xEAD4, 16),
        PLC_LISTS(0xE320, 158, 0xEAE4, 158),
        LEVEL_CHUNKS(0x10000, 54272, 0x10000, 54272),
        LEVEL_BLOCKS(0x38490, 4790, 0x38490, 4772),
        ART_LEVEL_TILES(0x39746, 11158, 0x39734, 11686),
        LEVEL_LAYOUTS(0x381FA, 72, 0x381FA, 72),
        LAYOUT_FG(0x38242, 62, 0x38242, 62),
        LAYOUT_BG(0x38280, 22, 0x38280, 22),
        LEVEL_COL_INDEX(0x1942, 32, 0x19AE, 32),
        LEVEL_COLLISION(0x37EDA, 800, 0x37EDA, 800),
        COL_ANGLE_MAP(0x35DDA, 256, 0x35DDA, 256),
        COL_HEIGHT_MAP(0x35EDA, 4096, 0x35EDA, 4096),
        COL_WIDTH_MAP(0x36EDA, 4096, 0x36EDA, 4096),
        CAM_BOUNDS(0x27FC, 12, 0x2868, 12),
        LEVEL_START_LOC(0x28C0, 4, 0x292C, 4),
        SPEC_CHUNKS(0x28C4, 4, 0x2930, 4),
        OBJECT_INDEX(0x3B82, 256, 0x3B9A, 256),
        OBJECT_LAYOUTS(0x7418, 514, 0x7430, 514),
        MAP_SPR_SONIC(0x2DF20, 2443, 0x2DF20, 2443),
        DPLC_SONIC(0x2E8AC, 1338, 0x2E8AC, 1338),
        ART_SONIC(0x20000, 57120, 0x20000, 57120),
        ANI_SONIC(0x56E8, 578, 0x5700, 578),
        MINI_ANIMATIONS(0x5698, 80, 0x56B0, 80),
        RAMP_CHUNKS(0x445E, 6, 0x4476, 6),
        SINE_TABLE(0x71A, 640, 0x786, 640),
        ARC_TAN_TABLE(0xA00, 258, 0xA6C, 258),
        PALETTE_TABLE(0x4EC, 48, 0x558, 48),
        PAL_SONIC(0x61C, 32, 0x688, 32),
        PAL_LEVEL(0x63C, 96, 0x6A8, 96);
        private final int goodOffset, goodLength, badOffset, badLength;
        Asset(int goodOffset,int goodLength,int badOffset,int badLength) {
            this.goodOffset=goodOffset;this.goodLength=goodLength;this.badOffset=badOffset;this.badLength=badLength;
        }
        public int fileOffset(CdNativeFile file) { return bad(file)?badOffset:goodOffset; }
        public int length(CdNativeFile file) { return bad(file)?badLength:goodLength; }
    }
    public record FileRange(CdNativeFile file,CdMmdImage.Domain domain,int fileOffset,int length) {
        public FileRange {
            bad(file);
            if(domain!=CdMmdImage.Domain.ORIGINAL_WORD_RAM || fileOffset<0 || length<0
                    || fileOffset>0x40000 || length>0x40000-fileOffset) {
                throw new IllegalArgumentException("Not a bounded R13 original-file range");
            }
        }
        public int nativeAddress() { return CdMmdHeader.WORD_RAM_ADDRESS+fileOffset; }
        public int endExclusive() { return fileOffset+length; }
        public CdAssetBytes read(CdMmdImage image) {
            var source=Objects.requireNonNull(image).nativeSlice(domain,nativeAddress(),length);
            byte[] bytes=new byte[length];source.get(bytes);return CdAssetBytes.copyOf(bytes);
        }
    }
    /** Pointer/destination metadata only: not a source reader, submission, output or art authority. */
    public record PlcEntry(long sourceNativeAddress,int vramAddress) {
        public PlcEntry {
            if(sourceNativeAddress<0x200000 || sourceNativeAddress>=0x240000
                    || (vramAddress&~0xFFFF)!=0 || (vramAddress&31)!=0) {
                throw new IllegalArgumentException("R13 PLC pointer/destination outside finite source metadata");
            }
        }
    }
    public record PlcList(int nativeId,FileRange bodyRange,List<PlcEntry> entries) {
        public PlcList {
            Objects.requireNonNull(bodyRange);entries=List.copyOf(entries);
            int root=Asset.PLC_LISTS.fileOffset(bodyRange.file());
            if(nativeId<0 || nativeId>=17 || entries.isEmpty() || entries.size()>10
                    || bodyRange.length()!=2+entries.size()*6 || bodyRange.fileOffset()<root+34
                    || bodyRange.endExclusive()>root+158) {
                throw new IllegalArgumentException("R13 PLC metadata count/range differs");
            }
        }
    }
    public record PaletteBinding(int nativeId,int destinationWord,int longwordCounter,Asset source,CdAssetBytes bytes) {
        public PaletteBinding {
            Objects.requireNonNull(source);Objects.requireNonNull(bytes);
            boolean sonic=nativeId==3 && source==Asset.PAL_SONIC && destinationWord==0xFB00 && longwordCounter==7;
            boolean level=nativeId==4 && source==Asset.PAL_LEVEL && destinationWord==0xFB20 && longwordCounter==23;
            if((!sonic && !level) || bytes.length()!=(longwordCounter+1)*4) {
                throw new IllegalArgumentException("R13 initial palette binding differs");
            }
        }
    }

    private static final CdMmdHeader HEADER=new CdMmdHeader(0,0,0,0,0x200100,0x20010C,0x200112);
    private static final String LAYOUT_HASH="09c6d1e3e0a86cd64fbc950116868948a81c2a40ea4874dfbf709644efc3df1f";
    private final Identity identity;
    private final CdMmdImage image;
    private final CdSonicVisualAssets sonic;
    private final CdR11aPlayerAssets player;
    private final CdNemesisAsset.Decoded blocks,tiles;
    private final CdStageTerrain terrain;
    private final CdR11bLoadPlan.LevelIndex levelIndex;
    private final CdR11bLoadPlan.Bounds bounds;
    private final CdR11bLoadPlan.InitialCentre initialCentre;
    private final List<PlcList> plcs;
    private final List<PaletteBinding> palettes;
    private final CdAssetBytes initialPalette;
    private final CdObjectPlacements placements;

    private CdR13FutureAssets(Identity identity,CdMmdImage image,CdSonicVisualAssets sonic,CdR11aPlayerAssets player,
                              CdNemesisAsset.Decoded blocks,CdNemesisAsset.Decoded tiles,CdStageTerrain terrain) {
        this.identity=identity;this.image=image;this.sonic=sonic;this.player=player;
        this.blocks=blocks;this.tiles=tiles;this.terrain=terrain;
        var index=view(Asset.LEVEL_DATA_INDEX);
        levelIndex=new CdR11bLoadPlan.LevelIndex(new CdR11bLoadPlan.TaggedPointer(index.unsignedLong(0)),
                new CdR11bLoadPlan.TaggedPointer(index.unsignedLong(4)),(int)index.unsignedLong(8),index.slice(12,2),index.slice(14,2));
        var size=view(Asset.CAM_BOUNDS);
        bounds=new CdR11bLoadPlan.Bounds(size.unsignedWord(0),size.unsignedWord(2),size.unsignedWord(4),size.unsignedWord(6),size.unsignedWord(8),size.unsignedWord(10));
        var start=view(Asset.LEVEL_START_LOC);initialCentre=new CdR11bLoadPlan.InitialCentre(start.unsignedWord(0),start.unsignedWord(2));
        plcs=decodePlcs();
        var table=view(Asset.PALETTE_TABLE);
        palettes=List.of(palette(table,3,Asset.PAL_SONIC,0xFB00,7),palette(table,4,Asset.PAL_LEVEL,0xFB20,23));
        byte[] combined=new byte[128];System.arraycopy(palettes.get(0).bytes().copy(),0,combined,0,32);
        System.arraycopy(palettes.get(1).bytes().copy(),0,combined,32,96);initialPalette=CdAssetBytes.copyOf(combined);
        // All immutable source fields needed by the narrow same-package parser are initialized first.
        // No source values or returned placement records confer gameplay/spawner authority.
        placements=CdObjectPlacements.decodeR13NativeOrder(this);
    }

    /** Borrows an actual complete-disc owner; retains only detached immutable source data. */
    public static CdR13FutureAssets fromPrograms(CdProgramAssets programs,CdStageKey stage)throws IOException {
        Objects.requireNonNull(programs,"actual CD program owner");Objects.requireNonNull(stage,"explicit R13 future stage");
        CdNativeFile file=fileForStage(stage);
        if(programs.header().observedVariant().orElse(null)!=CdReleaseVariant.USA)throw new IOException("R13 future assets require original USA media");
        var release=CdRetailReleases.find(programs.identity()).orElseThrow(()->new IOException("R13 future assets require a known complete-disc content profile"));
        if(release.variant()!=CdReleaseVariant.USA)throw new IOException("R13 future source release differs");
        var catalogue=programs.catalogue().orElseThrow(()->new IOException("R13 source catalogue unavailable"));
        if(catalogue.release()!=release.variant())throw new IOException("R13 catalogue and observed release differ");
        var payload=catalogue.stage(stage);
        if(payload==null || !payload.stage().equals(stage) || payload.file()!=file)throw new IOException("R13 catalogue stage/file differs");
        var image=programs.mmd(file);verifyImage(file,image);verifyOperands(file,image);
        var sonic=CdSonicVisualAssets.decode(CdSonicVisualAssets.SourceFormat.FRAMES_167,
                read(image,file,Asset.MAP_SPR_SONIC),read(image,file,Asset.DPLC_SONIC),read(image,file,Asset.ART_SONIC));
        var player=CdR11aPlayerAssets.decode(read(image,file,Asset.ANI_SONIC),
                range(file,Asset.MINI_ANIMATIONS).read(image).slice(0,60).copy(),read(image,file,Asset.RAMP_CHUNKS),
                read(image,file,Asset.SPEC_CHUNKS),read(image,file,Asset.SINE_TABLE),
                range(file,Asset.ARC_TAN_TABLE).read(image).slice(0,257).copy(),sonic);
        boolean good=!bad(file);
        var blocks=CdNemesisAsset.decode(read(image,file,Asset.LEVEL_BLOCKS),good?6080:6048,0x2000);
        var tiles=CdNemesisAsset.decode(read(image,file,Asset.ART_LEVEL_TILES),good?25216:26048,good?25216:26048);
        if(blocks.consumedInputBytes()!=(good?4790:4772) || tiles.consumedInputBytes()!=(good?11158:11685))throw new IOException("R13 finite Nemesis reader cursor differs");
        var terrain=CdStageTerrain.decode(good?CdStageTerrain.Shape.PPZ3_GOOD_FUTURE:CdStageTerrain.Shape.PPZ3_BAD_FUTURE,
                blocks.bytes().copy(),tiles.bytes().copy(),read(image,file,Asset.LEVEL_CHUNKS),read(image,file,Asset.LAYOUT_FG),
                read(image,file,Asset.LAYOUT_BG),read(image,file,Asset.LEVEL_COLLISION),read(image,file,Asset.COL_ANGLE_MAP),
                read(image,file,Asset.COL_HEIGHT_MAP),read(image,file,Asset.COL_WIDTH_MAP));
        return new CdR13FutureAssets(new Identity(CdReleaseVariant.USA,stage,file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,
                expectedHash(file),programs.identity().normalizedSha256()),image,sonic,player,blocks,tiles,terrain);
    }

    public Identity identity(){return identity;}
    public FileRange range(Asset asset){return range(identity.file(),Objects.requireNonNull(asset));}
    public CdAssetBytes view(Asset asset){return range(asset).read(image);}
    public CdSonicVisualAssets sonic(){return sonic;}
    public CdR11aPlayerAssets player(){return player;}
    public CdStageTerrain terrain(){return terrain;}
    public CdNemesisAsset.Decoded blocksDecode(){return blocks;}
    public CdNemesisAsset.Decoded levelTilesDecode(){return tiles;}
    public CdR11bLoadPlan.LevelIndex levelIndex(){return levelIndex;}
    public CdR11bLoadPlan.Bounds bounds(){return bounds;}
    public CdR11bLoadPlan.InitialCentre initialCentre(){return initialCentre;}
    public List<PlcList> plcs(){return plcs;}
    public List<PaletteBinding> paletteBindings(){return palettes;}
    public CdAssetBytes initialPalette(){return initialPalette;}
    public CdObjectPlacements placements(){return placements;}

    /** Actual immutable owner handshake only; equal public metadata records are not this owner. */
    void requireAuthenticatedLayout() {
        try { verifyImage(identity.file(),image); }
        catch(IOException fault){throw new IllegalStateException("R13 retained source authentication differs",fault);}
        if(identity.variant()!=CdReleaseVariant.USA || identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                || identity.file()!=fileForStage(identity.stage()) || !identity.mmdSha256().equals(expectedHash(identity.file()))
                || !LAYOUT_HASH.equals(CdR11aProfile.sha256(view(Asset.OBJECT_LAYOUTS).copy()))) {
            throw new IllegalStateException("R13 retained layout owner differs");
        }
    }
    CdAssetBytes nativeObjectLayout(){return view(Asset.OBJECT_LAYOUTS);}

    private List<PlcList> decodePlcs() {
        var raw=view(Asset.PLC_LISTS);var result=new ArrayList<PlcList>();
        for(int id=0;id<17;id++) {
            int at=(short)raw.unsignedWord(id*2);
            if(at<34 || (at&1)!=0 || at>raw.length()-2)throw new IllegalArgumentException("R13 signed PLC pointer outside source");
            int count=raw.unsignedWord(at)+1;
            if(count>10 || 2+6*count>raw.length()-at)throw new IllegalArgumentException("R13 PLC body exceeds source");
            var entries=new ArrayList<PlcEntry>();
            for(int row=0;row<count;row++)entries.add(new PlcEntry(raw.unsignedLong(at+2+6*row),raw.unsignedWord(at+6+6*row)));
            result.add(new PlcList(id,new FileRange(identity.file(),identity.domain(),range(Asset.PLC_LISTS).fileOffset()+at,2+6*count),entries));
        }
        return List.copyOf(result);
    }
    private PaletteBinding palette(CdAssetBytes table,int id,Asset source,int destination,int counter) {
        if(table.unsignedLong(id*8)!=range(source).nativeAddress() || table.unsignedWord(id*8+4)!=destination
                || table.unsignedWord(id*8+6)!=counter)throw new IllegalArgumentException("R13 native palette pointer/count differs");
        return new PaletteBinding(id,destination,counter,source,view(source));
    }
    private static FileRange range(CdNativeFile file,Asset asset){return new FileRange(file,CdMmdImage.Domain.ORIGINAL_WORD_RAM,asset.fileOffset(file),asset.length(file));}
    private static byte[] read(CdMmdImage image,CdNativeFile file,Asset asset){return range(file,asset).read(image).copy();}
    private static void verifyImage(CdNativeFile file,CdMmdImage image)throws IOException {
        Objects.requireNonNull(image,"actual selected programme");
        if(image.fileBytes()!=0x40000 || !HEADER.equals(image.header()) || image.fileBackedCopyBytes()!=0
                || image.unresolvedCopyBytes()!=0 || !expectedHash(file).equals(CdR11aProfile.sha256(image.readFile(0,image.fileBytes())))) {
            throw new IOException("Original USA R13 programme, header or finite image differs");
        }
    }
    private static void verifyOperands(CdNativeFile file,CdMmdImage image)throws IOException {
        int d=bad(file)?0x18:0,math=bad(file)?0x6C:0;
        require(image,0x335A+d,bad(file)?"700045f90020ead4":"700045f90020e310");
        require(image,0x33C6+d,"7000d04143f9002381fa");
        require(image,0x7180+d,bad(file)?"41f900207430":"41f900207418");
        require(image,0x3DB4+d,"217c0022df200004");require(image,0x5940+d,"45f90022e8ac");require(image,0x5970+d,"43f900220000");
        require(image,0x53F8+d,bad(file)?"43f900205700":"43f9002056e8");require(image,0x5692+d,"103b0004");
        require(image,0x441E+d,bad(file)?"45f900204476":"45f90020445e");
        require(image,0x70C+math,"323b000c");require(image,0x714+math,"303b0004");require(image,0x9C8+math,"103b4036");require(image,0x9D4+math,"903b302a");
        var index=range(file,Asset.LEVEL_DATA_INDEX).read(image);
        if(index.unsignedLong(0)!=(0x03000000L|range(file,Asset.ART_LEVEL_TILES).nativeAddress())
                || index.unsignedLong(4)!=(0x02000000L|range(file,Asset.LEVEL_BLOCKS).nativeAddress())
                || index.unsignedLong(8)!=range(file,Asset.LEVEL_CHUNKS).nativeAddress()
                || index.unsignedWord(12)!=0x0081 || index.unsignedWord(14)!=0x0404)throw new IOException("R13 native load index differs");
        var layout=range(file,Asset.LEVEL_LAYOUTS).read(image);
        if(range(file,Asset.LEVEL_LAYOUTS).fileOffset()+(short)layout.unsignedWord(0)!=range(file,Asset.LAYOUT_FG).fileOffset()
                || range(file,Asset.LEVEL_LAYOUTS).fileOffset()+(short)layout.unsignedWord(2)!=range(file,Asset.LAYOUT_BG).fileOffset())throw new IOException("R13 native plane pointers differ");
        var collision=range(file,Asset.LEVEL_COL_INDEX).read(image);
        for(int i=0;i<8;i++)if(collision.unsignedLong(i*4)!=range(file,Asset.LEVEL_COLLISION).nativeAddress())throw new IOException("R13 native collision pointer differs");
    }
    private static void require(CdMmdImage image,int at,String hex)throws IOException {
        if(!java.util.HexFormat.of().formatHex(image.readFile(at,hex.length()/2)).equals(hex))throw new IOException("R13 original consumer operand differs at "+Integer.toHexString(at));
    }
    private static boolean bad(CdNativeFile file) {
        if(file==CdNativeFile.R13C)return false;if(file==CdNativeFile.R13D)return true;
        throw new IllegalArgumentException("Only original R13C/D metadata is reviewed");
    }
    private static CdNativeFile fileForStage(CdStageKey stage) {
        Objects.requireNonNull(stage);
        if(stage.equals(CdStagePayload.R13C.stage()))return CdNativeFile.R13C;
        if(stage.equals(CdStagePayload.R13D.stage()))return CdNativeFile.R13D;
        throw new UnsupportedOperationException("Only original USA R13C/D asset stages are reviewed");
    }
    private static String expectedHash(CdNativeFile file) {
        return bad(file)?"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356"
                :"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f";
    }
}
