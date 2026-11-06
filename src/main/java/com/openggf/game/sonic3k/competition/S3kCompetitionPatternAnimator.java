package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.level.animation.AniPlcParser;
import com.openggf.level.animation.AniPlcScriptState;
import com.openggf.level.animation.InitialLevelAnimationPass;
import com.openggf.level.animation.StageRingAnimationFrameProvider;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Native arena Animate_Tiles, including DPZ's separate camera-driven uploads.
 * Offs_AniFunc (sonic3k.asm:53881-53958), AnimateTiles_DPZ (sonic3k.asm:55216-55308).
 * Script counters submit immutable DMA payloads; only the VBlank owner publishes them.
 */
public final class S3kCompetitionPatternAnimator implements AnimatedPatternManager,
        InitialLevelAnimationPass, StageRingAnimationFrameProvider, RewindSnapshottable<byte[]> {
    public static final int DISPATCH_ADDRESS=0x27778;
    private static final int DPZ_SOURCE=0x2C0180, DPZ_SPLIT_TABLE=0x28446;
    private final RomByteReader reader;
    private final S3kCompetitionResources resources;
    private final Level level;
    private final GraphicsManager graphics;
    private final S3kCompetitionSession.Arena arena;
    private final Supplier<S3kCompetitionScroll> scroll;
    private final List<AniPlcScriptState> scripts;
    private final List<Upload> pending=new ArrayList<>();
    private final BitSet destinations=new BitSet();
    private final int[] dpzPhase=new int[2];
    private transient Runnable afterTilePass=()->{};

    public S3kCompetitionPatternAnimator(Rom rom,Level level,S3kCompetitionSession.Arena arena,
            Supplier<S3kCompetitionScroll> scroll,GraphicsManager graphics) throws IOException {
        this(rom,level,arena,scroll,graphics,S3kCompetitionResources.LOCKED_ON);
    }
    public S3kCompetitionPatternAnimator(Rom rom,Level level,S3kCompetitionSession.Arena arena,
            Supplier<S3kCompetitionScroll> scroll,GraphicsManager graphics,S3kCompetitionResources resources) throws IOException {
        this.resources=Objects.requireNonNull(resources);
        reader=RomByteReader.fromRom(Objects.requireNonNull(rom));
        this.level=Objects.requireNonNull(level);this.arena=Objects.requireNonNull(arena);
        this.scroll=Objects.requireNonNull(scroll);this.graphics=Objects.requireNonNull(graphics);
        int dispatch=resources.at(DISPATCH_ADDRESS);
        int entry=dispatch+arena.zoneId()*8;
        int routine=dispatch+(short)reader.readU16BE(entry);
        int script=dispatch+(short)reader.readU16BE(entry+2);
        int expected=arena==S3kCompetitionSession.Arena.DESERT_PALACE?0x283E2
                :arena==S3kCompetitionSession.Arena.CHROME_GADGET
                ||arena==S3kCompetitionSession.Arena.ENDLESS_MINE?0x27838:0x286E4;
        if(routine!=resources.at(expected)) throw new IOException("Unsupported competition animation dispatch");
        scripts=routine==resources.at(0x27838)?List.of():AniPlcParser.parseScripts(reader,script);
        AniPlcParser.ensurePatternCapacity(scripts,level);
        for(var state:scripts) destinations.set(state.destinationTileIndex(),state.requiredPatternCount());
        if(arena==S3kCompetitionSession.Arena.DESERT_PALACE) {
            level.ensurePatternCapacity(0x193);destinations.set(0x153,0x193);
        }
    }

    /** loc_66EA follows Animate_Tiles with Render_Sprites, before Animate_Palette. */
    public void setAfterTilePass(Runnable callback) { afterTilePass=Objects.requireNonNull(callback); }
    @Override public void update() { updateTiles(); afterTilePass.run(); }
    private void updateTiles() {
        if(arena==S3kCompetitionSession.Arena.DESERT_PALACE) {
            var cameras=Objects.requireNonNull(scroll.get(),"DPZ native cameras");
            for(int slot=0;slot<2;slot++) {
                int background=cameras.view(slot).backgroundX();
                int phase=((background>>2)-background)&0x3F;
                if(phase==dpzPhase[slot]) continue;
                dpzPhase[slot]=phase;
                int column=(phase&7)<<10, row=(phase&0x38)<<4;
                int table=resources.at(DPZ_SPLIT_TABLE)+((phase&0x38)>>>1);
                int firstWords=reader.readU16BE(table),secondWords=reader.readU16BE(table+2);
                int destination=0x153+slot*0x20;
                submit(destination,reader.slice(resources.at(DPZ_SOURCE)+column+row,firstWords*2));
                if(secondWords!=0) submit(destination+firstWords/16,
                        reader.slice(resources.at(DPZ_SOURCE)+column,secondWords*2));
            }
        }
        for(var script:scripts) script.tickSubmission(tile -> submit(script.destinationTileIndex(),script.copyFramePayload(tile)));
    }
    @Override public void runInitialLevelAnimationPass() { updateTiles(); }
    private void submit(int destination,byte[] bytes) { pending.add(new Upload(destination,bytes)); }

    /** Native Add_To_DMA_Queue order; payload queries never mutate either CPU or GPU tiles. */
    public List<Upload> pendingTransfers() { return List.copyOf(pending); }
    public record Upload(int destination,byte[] bytes) {
        public Upload {
            if(destination<0 || bytes.length==0 || bytes.length%32!=0 || destination+bytes.length/32>2048)
                throw new IllegalArgumentException("Invalid native animated upload");
            bytes=bytes.clone();
        }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    public void publishAtVBlank() {
        var ready=List.copyOf(pending);pending.clear();
        for(var upload:ready) publish(upload);
    }
    private void publish(Upload upload) {
        byte[] bytes=upload.bytes();
        for(int offset=0;offset<bytes.length;offset+=32) {
            int tile=upload.destination()+offset/32;
            Pattern pattern=Objects.requireNonNull(level.getPattern(tile),"Native animated destination tile");
            pattern.fromSegaFormat(java.util.Arrays.copyOfRange(bytes,offset,offset+32));
            if(graphics.isGlInitialized()) graphics.updatePatternTexture(pattern,tile);
        }
    }
    // loc_66EA (sonic3k.asm:8052-8070) dispatches no shared ring-animation step.
    @Override public int stageRingAnimationFrame() { return 0; }
    @Override public boolean advancesSharedSpillAnimation() { return false; }
    @Override public String key() { return "sonic3k.competition.animated-tiles"; }
    @Override public byte[] capture() {
        int size=24+scripts.size()*8+destinations.cardinality()*36;
        for(var upload:pending) size+=8+upload.bytes.length;
        ByteBuffer out=ByteBuffer.allocate(size);
        out.putInt(arena.zoneId()).putInt(dpzPhase[0]).putInt(dpzPhase[1]).putInt(scripts.size());
        for(var script:scripts) out.putInt(script.getTimer()).putInt(script.getFrameIndex());
        out.putInt(destinations.cardinality());
        for(int tile=destinations.nextSetBit(0);tile>=0;tile=destinations.nextSetBit(tile+1)) {
            out.putInt(tile);Pattern pattern=level.getPattern(tile);
            for(int y=0;y<8;y++) for(int x=0;x<8;x+=2)
                out.put((byte)(pattern.getPixel(x,y)<<4|pattern.getPixel(x+1,y)));
        }
        out.putInt(pending.size());
        for(var upload:pending) out.putInt(upload.destination()).putInt(upload.bytes.length).put(upload.bytes);
        return out.array();
    }
    @Override public void restore(byte[] snapshot) {
        ByteBuffer in=ByteBuffer.wrap(snapshot);
        if(in.getInt()!=arena.zoneId()) throw new IllegalArgumentException("Animation snapshot belongs to another arena");
        int first=in.getInt(),second=in.getInt(),count=in.getInt();
        if(count!=scripts.size() || first<0 || first>63 || second<0 || second>63)
            throw new IllegalArgumentException("Invalid native animation state");
        int[] timers=new int[count],frames=new int[count];
        for(int i=0;i<count;i++) {timers[i]=in.getInt();frames[i]=in.getInt();}
        int presented=count(in.getInt(),2048);List<Upload> restored=new ArrayList<>();
        for(int i=0;i<presented;i++) {int tile=in.getInt();byte[] bytes=new byte[32];in.get(bytes);restored.add(new Upload(tile,bytes));}
        int queued=count(in.getInt(),4096);List<Upload> waiting=new ArrayList<>();
        for(int i=0;i<queued;i++) {int tile=in.getInt();byte[] bytes=new byte[count(in.getInt(),65536)];in.get(bytes);waiting.add(new Upload(tile,bytes));}
        if(in.hasRemaining()) throw new IllegalArgumentException("Trailing native animation state");
        dpzPhase[0]=first;dpzPhase[1]=second;
        for(int i=0;i<count;i++) scripts.get(i).restoreCounters(timers[i],frames[i]);
        pending.clear();pending.addAll(waiting);
        for(var upload:restored) publish(upload);
    }
    private static int count(int value,int maximum) {
        if(value<0 || value>maximum) throw new IllegalArgumentException("Invalid native animation count");return value;
    }
}
