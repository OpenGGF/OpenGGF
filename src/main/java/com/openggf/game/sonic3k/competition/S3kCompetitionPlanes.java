package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.game.internal.BackgroundDescriptorOverride;
import com.openggf.game.internal.ForegroundDescriptorOverride;
import com.openggf.game.internal.RetainedPlaneDescriptorSource;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The two retained 8 KiB competition nametables, not the finite level layout.
 * Comp_ScreenInit (s3.asm:69276-69299) and Comp_BackgroundInit (s3.asm:69365-69377)
 * fill them once; the five screen/background events only change scroll state.
 * Setup_TileRowDraw_Competition (sonic3k.asm:103051-103082) emits separate top
 * and bottom payloads, consumed sequentially by VInt_DrawLevel_Competition
 * (sonic3k.asm:102496-102517). No camera-dependent redraw is invented here.
 */
public final class S3kCompetitionPlanes implements ForegroundDescriptorOverride,
        BackgroundDescriptorOverride,RetainedPlaneDescriptorSource,RewindSnapshottable<S3kCompetitionPlanes.State> {
    public static final int FOREGROUND_VRAM=0x8000,BACKGROUND_VRAM=0xA000,PLANE_BYTES=0x2000;
    private static final int PROFILE_ADDRESS=0x23AA08;
    private static final int DEFAULT_SIZE_REGISTER_ADDRESS=0x61B0,BPZ_SIZE_REGISTER_ADDRESS=0x61BC;

    public record Profile(Arena arena,int screenSizeRegister,int widthTiles,int heightTiles,
            int rowStrideBytes,int initialForegroundY,int refreshRowsMinusOne,int refreshColumns,
            int layoutRowIndexMask,int layoutAddress) { }
    public record Cell(ChunkDesc descriptor,Chunk chunk) {
        public Cell { Objects.requireNonNull(descriptor);Objects.requireNonNull(chunk); }
    }
    /** Reads the live ROM-decoded 128x128 block and its 16x16 descriptor. */
    @FunctionalInterface public interface ChunkSource { Cell cell(int layer,int sourceX,int sourceY); }
    public record State(Profile profile,long foregroundRevision,long backgroundRevision,
            List<Integer> foreground,List<Integer> background) {
        public State {
            Objects.requireNonNull(profile);foreground=List.copyOf(foreground);background=List.copyOf(background);
        }
    }
    private final Profile profile;
    private final ChunkSource source;
    private State state;
    private final Plane foreground=plane(0),background=plane(1);

    private Plane plane(int layer) {
        return new Plane() {
            @Override public int widthTiles() { return S3kCompetitionPlanes.this.widthTiles(); }
            @Override public int heightTiles() { return S3kCompetitionPlanes.this.heightTiles(); }
            @Override public long revision() { return layer==0?foregroundDescriptorRevision():backgroundDescriptorRevision(); }
            @Override public int descriptorAt(int x,int y) { return S3kCompetitionPlanes.this.descriptorAt(layer,x,y); }
        };
    }
    @Override public Plane foregroundPlane() { return foreground; }
    @Override public Plane backgroundPlane() { return background; }

    public S3kCompetitionPlanes(Rom rom,Arena arena,LevelManager level) throws IOException {
        this(rom,arena,level,S3kCompetitionResources.LOCKED_ON);
    }
    public S3kCompetitionPlanes(Rom rom,Arena arena,LevelManager level,S3kCompetitionResources resources) throws IOException {
        this(loadProfile(rom,arena,resources),rom,level);
    }
    private S3kCompetitionPlanes(Profile profile,Rom rom,LevelManager level) throws IOException {
        this(profile,new LevelChunkSource(rom,profile,level));
    }
    /** The same native row writer can consume an injected decoded-geometry owner. */
    public S3kCompetitionPlanes(Rom rom,Arena arena,ChunkSource source) throws IOException {
        this(loadProfile(rom,arena,S3kCompetitionResources.LOCKED_ON),source);
    }
    private S3kCompetitionPlanes(Profile profile,ChunkSource source) {
        this.profile=profile;this.source=Objects.requireNonNull(source);
        List<Integer> empty=Arrays.stream(new int[PLANE_BYTES/2]).boxed().toList();
        state=new State(profile,0,0,empty,empty);
    }
    private static Profile loadProfile(Rom rom,Arena arena,S3kCompetitionResources resources) throws IOException {
        Objects.requireNonNull(rom);Objects.requireNonNull(arena);
        byte[] words=rom.readBytes(resources.at(PROFILE_ADDRESS)+(arena.zoneId()-0xE)*16,16);
        int register=word(rom.readBytes(resources.at(arena==Arena.BALLOON_PARK?BPZ_SIZE_REGISTER_ADDRESS:DEFAULT_SIZE_REGISTER_ADDRESS),2),0);
        if((register&0xFF00)!=0x9000) throw new IOException("Missing native competition VDP size register");
        int width=dimension(register&3),height=dimension(register>>>4&3);
        int stride=word(words,8),rows=word(words,12),columns=word(words,14);
        if(width*height!=PLANE_BYTES/2 || stride!=width*2 || columns*2!=width || (rows+1)*2!=height)
            throw new IOException("Competition VDP geometry disagrees with native row-refresh profile");
        byte[] pointer=rom.readBytes(resources.layouts()+arena.zoneId()*8,4);
        int layout=(pointer[1]&255)<<16|(pointer[2]&255)<<8|pointer[3]&255;
        return new Profile(arena,register,width,height,stride,word(words,10),rows,columns,word(words,6),layout);
    }
    private static int dimension(int selector) throws IOException {
        return switch(selector) {case 0->32;case 1->64;case 3->128;default->throw new IOException("Invalid native VDP plane-size selector");};
    }
    private static int word(byte[] bytes,int offset){return (bytes[offset]&255)<<8|bytes[offset+1]&255;}
    public Profile profile(){return profile;}
    public int widthTiles(){return profile.widthTiles();}
    public int heightTiles(){return profile.heightTiles();}

    /** Host calls this at native LevelSetup, after the live level is installed. */
    public void initialize() {
        List<Integer> foreground=refresh(0,profile.initialForegroundY());
        List<Integer> background=refresh(1,0);
        state=new State(profile,next(state.foregroundRevision()),next(state.backgroundRevision()),foreground,background);
    }
    public void screenInit() {
        state=new State(profile,next(state.foregroundRevision()),state.backgroundRevision(),
                refresh(0,profile.initialForegroundY()),state.background());
    }
    public void backgroundInit() {
        state=new State(profile,state.foregroundRevision(),next(state.backgroundRevision()),
                state.foreground(),refresh(1,0));
    }
    private static long next(long revision){return revision==Long.MAX_VALUE?1:revision+1;}
    private List<Integer> refresh(int layer,int firstY) {
        int[] plane=new int[PLANE_BYTES/2];
        for(int row=0;row<=profile.refreshRowsMinusOne();row++) {
            int sourceY=(short)(firstY+row*16);
            int destination=(sourceY*(profile.rowStrideBytes()/8))&(PLANE_BYTES-1);
            // loc_39AA8 lays out all TOP longwords before all BOTTOM longwords.
            // VInt's second write starts after the first payload in the buffer.
            int[] top=new int[profile.refreshColumns()*2],bottom=new int[top.length];
            for(int column=0;column<profile.refreshColumns();column++) {
                Cell cell=Objects.requireNonNull(source.cell(layer,column*16,sourceY),"Native competition chunk source");
                boolean h=cell.descriptor().getHFlip(),v=cell.descriptor().getVFlip();
                int xor=(h?0x800:0)|(v?0x1000:0);
                for(int x=0;x<2;x++) {
                    top[column*2+x]=(cell.chunk().getPatternDesc(h?1-x:x,v?1:0).get()^xor)&0xFFFF;
                    bottom[column*2+x]=(cell.chunk().getPatternDesc(h?1-x:x,v?0:1).get()^xor)&0xFFFF;
                }
            }
            writePayload(plane,destination,top);
            writePayload(plane,destination+profile.rowStrideBytes(),bottom);
        }
        return Arrays.stream(plane).boxed().toList();
    }
    private static void writePayload(int[] plane,int destination,int[] payload) {
        for(int index=0;index<payload.length;index++) plane[((destination>>>1)+index)&(plane.length-1)]=payload[index];
    }
    private int descriptorAt(int layer,int sourceX,int sourceY) {
        if((layer==0?state.foregroundRevision():state.backgroundRevision())==0)
            throw new IllegalStateException("Competition plane has not run native initialization");
        int x=Math.floorMod(sourceX>>3,widthTiles()),y=Math.floorMod(sourceY>>3,heightTiles());
        return (layer==0?state.foreground():state.background()).get(y*widthTiles()+x);
    }
    @Override public long foregroundDescriptorRevision(){return state.foregroundRevision();}
    @Override public long backgroundDescriptorRevision(){return state.backgroundRevision();}
    @Override public int foregroundDescriptorRingRows(){return heightTiles();}
    @Override public int foregroundDescriptorAt(int sourceX,int sourceY){return descriptorAt(0,sourceX,sourceY);}
    @Override public int backgroundDescriptorAt(int sourceX,int sourceY){return descriptorAt(1,sourceX,sourceY);}
    public int physicalVramWord(int address) {
        int nativeAddress=address&0xFFFF;
        if((nativeAddress&1)!=0 || nativeAddress<FOREGROUND_VRAM || nativeAddress>=BACKGROUND_VRAM+PLANE_BYTES)
            throw new IllegalArgumentException("Address is outside the two competition descriptor planes");
        int layer=nativeAddress<BACKGROUND_VRAM?0:1;
        int offset=nativeAddress-(layer==0?FOREGROUND_VRAM:BACKGROUND_VRAM);
        return descriptorAt(layer,(offset/2%widthTiles())*8,(offset/2/widthTiles())*8);
    }
    @Override public String key(){return "sonic3k.competition.planes";}
    @Override public State capture(){return state;}
    @Override public void restore(State snapshot) {
        if(!profile.equals(snapshot.profile()) || snapshot.foreground().size()!=PLANE_BYTES/2
                || snapshot.background().size()!=PLANE_BYTES/2)
            throw new IllegalArgumentException("Competition plane snapshot belongs to another native profile");
        state=snapshot;
    }
    public static S3kCompetitionPlanes recreate(Rom rom,Arena arena,LevelManager level,State snapshot) throws IOException {
        var planes=new S3kCompetitionPlanes(rom,arena,level);planes.restore(snapshot);return planes;
    }

    /**
     * Native Get_LevelAddrChunkRow/Get_ChunkRow, retaining raw interleaved row
     * pointers. BPZ's fourth BG row has pointer0: MOVEA.W reads ROM0, not a
     * wrapped ordinary layout row. The locked-on ROM bytes0..3 select block0.
     */
    private static final class LevelChunkSource implements ChunkSource {
        private final Profile profile;
        private final LevelManager manager;
        private final byte[] layout,lowRom;
        LevelChunkSource(Rom rom,Profile profile,LevelManager manager) throws IOException {
            this.profile=profile;this.manager=Objects.requireNonNull(manager);
            layout=rom.readBytes(profile.layoutAddress(),0x1000);lowRom=rom.readBytes(0,0x8000);
        }
        @Override public Cell cell(int layer,int sourceX,int sourceY) {
            Level level=Objects.requireNonNull(manager.getCurrentLevel(),"Live competition level");
            if(level.getZoneIndex()!=profile.arena().zoneId()) throw new IllegalStateException("Wrong competition level for plane refresh");
            int row=((sourceY>>5)&profile.layoutRowIndexMask())+8+layer*2;
            int pointer=word(layout,row),column=(short)sourceX>>7;
            int blockId;
            if((pointer&0x8000)!=0) {
                int offset=pointer-0x8000+column;
                if(offset<0 || offset>=layout.length) throw new IllegalStateException("Competition row points outside native layout workspace");
                blockId=layout[offset]&255;
            } else {
                int address=pointer+column;
                if(address<0 || address>=lowRom.length) throw new IllegalStateException("Competition row points outside low ROM");
                blockId=lowRom[address]&255;
            }
            if(blockId>=level.getBlockCount()) throw new IllegalStateException("Competition row references unloaded native block "+blockId);
            Block block=Objects.requireNonNull(level.getBlock(blockId),"Native competition block");
            ChunkDesc descriptor=block.getChunkDesc((sourceX&0x70)>>4,(sourceY&0x70)>>4);
            int chunkId=descriptor.getChunkIndex();
            if(chunkId>=level.getChunkCount()) throw new IllegalStateException("Competition block references unloaded native chunk "+chunkId);
            return new Cell(descriptor,Objects.requireNonNull(level.getChunk(chunkId),"Native competition chunk"));
        }
    }
}
