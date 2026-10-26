package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.game.palette.PaletteWriteSupport;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PaletteUploadPresentation;
import com.openggf.level.Level;
import com.openggf.level.Palette;
import com.openggf.level.animation.AnimatedPaletteManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Competition loc_665A (sonic3k.asm:8012-8016).
 * Animate_Palette (sonic3k.asm:5050-5069).
 * Pal_AddColor (sonic3k.asm:4987-5020).
 * AnPal_BPZ (sonic3k.asm:3744-3776).
 * AnPal_CGZ (sonic3k.asm:3778-3795).
 * AnPal_EMZ (sonic3k.asm:3797-3838).
 * Current and target words are RAM,
 * including bits hidden by CRAM's RGB decode; there is no display-only fade multiplier.
 */
public final class S3kCompetitionPaletteAnimator implements AnimatedPaletteManager,
        RewindSnapshottable<S3kCompetitionPaletteAnimator.State> {
    public static final String NATIVE_OWNER="s3k.competition.native-palette";
    public static final String OBJECT_OWNER="s3k.competition.object";
    // The competition loop runs Animate_Palette after all SSTs and Render_Sprites.
    // This priority applies ONLY to a word actually written by that native phase.
    public static final int NATIVE_PHASE_PRIORITY=S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE;
    public static final int BPZ_BALLOONS=0x34CC,BPZ_BACKGROUND=0x34DE,CGZ_LIGHTS=0x355C,
            EMZ_GLOW=0x35AC,EMZ_BACKGROUND=0x35E8;

    public record ColorWrite(int line,int color,int word) {
        public ColorWrite {
            if(line<0 || line>=4 || color<0 || color>=16 || word<0 || word>0xFFFF)
                throw new IllegalArgumentException("Invalid native palette write");
        }
    }
    public record State(S3kCompetitionSession.Arena arena,boolean prepared,int fadeTimer,
            int counter0,int counter2,int timer1,int timer2,List<Integer> current,List<Integer> target,
            List<ColorWrite> nativeWrites) {
        public State {
            Objects.requireNonNull(arena);current=List.copyOf(current);target=List.copyOf(target);
            nativeWrites=List.copyOf(nativeWrites);
            if(current.size()!=64 || target.size()!=64)throw new IllegalArgumentException("Native palette bank must contain 64 words");
            for(int word:current)if((word&~0xFFFF)!=0)throw new IllegalArgumentException("Invalid current palette word");
            for(int word:target)if((word&~0xFFFF)!=0)throw new IllegalArgumentException("Invalid target palette word");
        }
    }
    private final Level level;
    private final PaletteOwnershipRegistry registry;
    private final GraphicsManager graphics;
    private final S3kCompetitionSession.Arena arena;
    private final byte[] bpzBalloons,bpzBackground,cgzLights,emzGlow,emzBackground;
    private final int[] current=new int[64],target=new int[64];
    private final List<ColorWrite> nativeWrites=new ArrayList<>();
    private boolean prepared;
    private int fadeTimer=0xFFFF,counter0,counter2,timer1,timer2;

    public S3kCompetitionPaletteAnimator(Rom rom,Level level,PaletteOwnershipRegistry registry,
            GraphicsManager graphics) throws IOException {
        this.level=Objects.requireNonNull(level);this.registry=Objects.requireNonNull(registry);this.graphics=graphics;
        arena=S3kCompetitionSession.Arena.forZone(level.getZoneIndex());
        if(level.getPaletteCount()!=4)throw new IllegalArgumentException("Competition requires four live palette lines");
        var reader=RomByteReader.fromRom(Objects.requireNonNull(rom));
        bpzBalloons=reader.slice(BPZ_BALLOONS,18);bpzBackground=reader.slice(BPZ_BACKGROUND,126);
        cgzLights=reader.slice(CGZ_LIGHTS,80);
        // MOVE.W 4(a0,d0.w), d0=$3A, reads into the next table in the shipped ROM.
        emzGlow=reader.slice(EMZ_GLOW,64);emzBackground=reader.slice(EMZ_BACKGROUND,52);
        for(int line=0;line<4;line++)Objects.requireNonNull(level.getPalette(line),"Native live palette line");
    }
    public S3kCompetitionPaletteAnimator(Rom rom,Level level,PaletteOwnershipRegistry registry) throws IOException {
        this(rom,level,registry,null);
    }

    /** Load setup before the initial object/render pass: line 0 visible, the remaining lines black. */
    public void prepareInitialPalette(Palette[] targetPalette) {
        if(targetPalette.length!=4)throw new IllegalArgumentException("Competition target requires four lines");
        int[] words=new int[64];
        for(int line=0;line<4;line++)for(int color=0;color<16;color++)
            words[line*16+color]=PaletteWriteSupport.segaWordFromColor(targetPalette[line].getColor(color));
        prepareInitialPalette(words);
    }
    public void prepareInitialPalette(int[] targetWords) {
        if(targetWords.length!=64)throw new IllegalArgumentException("Competition target requires 64 words");
        prepared=true;fadeTimer=0xFFFF;counter0=counter2=timer1=timer2=0;nativeWrites.clear();
        for(int i=0;i<64;i++){target[i]=targetWords[i]&0xFFFF;current[i]=i<16?target[i]:0;}
        publishTarget();
        for(int i=0;i<64;i++)writeNative(i,current[i]);
        resolve(graphics);
    }
    /** loc_665A, after the initial Process_Sprites/Render_Sprites/Animate_Tiles pass. */
    public void armLevelFade() {
        requirePrepared();nativeWrites.clear();
        for(int i=0;i<64;i++)writeNative(i,0);
        fadeTimer=0x16;resolve(graphics);
    }

    /** The native object mutation surface: immediately readable by later SSTs. */
    public int readCurrentColor(int line,int color){return current[index(line,color)];}
    public void writeCurrentColor(int line,int color,int word) {
        requirePrepared();int index=index(line,color);current[index]=word&0xFFFF;
        level.getPalette(line).getColor(color).fromSegaFormat(word);
        registry.submit(PaletteWrite.normal(OBJECT_OWNER,S3kPaletteOwners.PRIORITY_OBJECT_OVERRIDE,
                line,color,bytes(word)));
    }
    public int targetColor(int line,int color){return target[index(line,color)];}
    public int fadeTimer(){return fadeTimer;}
    public List<ColorWrite> lastNativeWrites(){return List.copyOf(nativeWrites);}

    @Override public void update() {
        requirePrepared();nativeWrites.clear();
        // Apply preceding object claims before the ROM reads Normal_palette. The
        // final resolution below is the sole queue upload for this palette phase.
        resolve(null);reconcileLivePalette();
        if((short)fadeTimer>=0) {
            if(fadeTimer!=0) {
                fadeTimer=(fadeTimer-1)&0xFFFF;
                for(int i=0;i<64;i++)if(current[i]!=target[i])writeNative(i,addColor(current[i],target[i]));
            } else animateArena();
        }
        resolve(graphics);
    }
    /** Pal_AddColor's unsigned word comparisons, including wrap/carry into non-CRAM bits. */
    public static int addColor(int current,int target) {
        current&=0xFFFF;target&=0xFFFF;if(current==target)return current;
        int blue=(current+0x200)&0xFFFF;if(blue<=target)return blue;
        int green=(current+0x20)&0xFFFF;if(green<=target)return green;
        return (current+2)&0xFFFF;
    }
    private void animateArena() {
        switch(arena) {
            case BALLOON_PARK -> {
                timer1=(timer1-1)&0xFFFF;
                if((short)timer1<0){timer1=7;int old=counter0;counter0=advance(counter0,6,0x12);writeTable(2,13,bpzBalloons,old,3);}
                timer2=(timer2-1)&0xFFFF;
                if((short)timer2<0){timer2=0x11;int old=counter2;counter2=advance(counter2,6,0x7E);writeTable(3,2,bpzBackground,old,3);}
            }
            case CHROME_GADGET -> {
                timer1=(timer1-1)&0xFFFF;
                if((short)timer1<0){timer1=9;int old=counter0;counter0=advance(counter0,8,0x50);writeTable(2,2,cgzLights,old,4);}
            }
            case ENDLESS_MINE -> {
                timer1=(timer1-1)&0xFFFF;
                if((short)timer1<0){timer1=7;int old=counter0;counter0=advance(counter0,2,0x3C);writeTable(2,14,emzGlow,old+4,1);}
                timer2=(timer2-1)&0xFFFF;
                if((short)timer2<0){timer2=0x1F;int old=counter2;counter2=advance(counter2,4,0x34);writeTable(3,9,emzBackground,old,2);}
            }
            case AZURE_LAKE,DESERT_PALACE -> { /* AnPal_None: no counters or palette writes. */ }
        }
    }
    private static int advance(int counter,int step,int limit){int next=(counter+step)&0xFFFF;return next<limit?next:0;}
    private void writeTable(int line,int color,byte[] table,int offset,int count) {
        for(int i=0;i<count;i++)writeNative(line*16+color+i,((table[offset+i*2]&0xFF)<<8)|(table[offset+i*2+1]&0xFF));
    }
    private void writeNative(int index,int word) {
        current[index]=word&0xFFFF;int line=index/16,color=index%16;
        level.getPalette(line).getColor(color).fromSegaFormat(word);
        nativeWrites.add(new ColorWrite(line,color,word&0xFFFF));
        registry.submit(PaletteWrite.normal(NATIVE_OWNER,NATIVE_PHASE_PRIORITY,line,color,bytes(word)));
    }
    private void reconcileLivePalette() {
        for(int line=0;line<4;line++)for(int color=0;color<16;color++) {
            int index=line*16+color,projected=PaletteWriteSupport.segaWordFromColor(level.getPalette(line).getColor(color));
            // Preserve RAM bits which Palette.Color cannot represent. Native
            // object writes use writeCurrentColor and therefore do not alias here.
            if((current[index]&0xEEE)!=projected)current[index]=projected;
        }
    }
    private void resolve(GraphicsManager graphics) {
        Palette[] palettes={level.getPalette(0),level.getPalette(1),level.getPalette(2),level.getPalette(3)};
        registry.resolveInto(palettes,null,graphics,palettes[0]);
    }
    private void publishTarget() {
        for(int line=0;line<4;line++)registry.applyTargetPatch(NATIVE_OWNER,line,0,lineBytes(target,line));
    }
    private static byte[] lineBytes(int[] bank,int line) {
        byte[] bytes=new byte[32];for(int i=0;i<16;i++){int word=bank[line*16+i];bytes[i*2]=(byte)(word>>>8);bytes[i*2+1]=(byte)word;}return bytes;
    }
    private static byte[] bytes(int word){return new byte[]{(byte)(word>>>8),(byte)word};}
    private static int index(int line,int color){if(line<0 || line>=4 || color<0 || color>=16)throw new IllegalArgumentException("Invalid native palette address");return line*16+color;}
    private void requirePrepared(){if(!prepared)throw new IllegalStateException("Native competition palette has not been prepared");}
    @Override public String key(){return "sonic3k.competition.animated-palette";}
    @Override public State capture(){return new State(arena,prepared,fadeTimer,counter0,counter2,timer1,timer2,
            Arrays.stream(current).boxed().toList(),Arrays.stream(target).boxed().toList(),nativeWrites);}
    @Override public void restore(State state) {
        if(state.arena()!=arena)throw new IllegalArgumentException("Native palette snapshot belongs to another arena");
        prepared=state.prepared();fadeTimer=state.fadeTimer();counter0=state.counter0();counter2=state.counter2();timer1=state.timer1();timer2=state.timer2();
        for(int i=0;i<64;i++){current[i]=state.current().get(i);target[i]=state.target().get(i);}
        nativeWrites.clear();nativeWrites.addAll(state.nativeWrites());publishTarget();
        for(int line=0;line<4;line++) {
            var palette=level.getPalette(line);palette.fromSegaFormat(lineBytes(current,line));
            // Existing native V-int publication owns when this queued line becomes visible.
            if(graphics!=null && graphics.isGlInitialized())PaletteUploadPresentation.cacheLevelPalette(graphics,palette,line);
        }
    }
}
