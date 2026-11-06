package com.openggf.game.soniccd.level;

import com.openggf.game.soniccd.*;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.resources.CdLevelPaletteCycle;
import com.openggf.game.soniccd.scroll.*;
import java.util.*;

/**
 * Detached original-USA Act3 C/D scroll/palette components, never an event/Frame/World/clock owner.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Scroll(Act3):7 includes Scroll(Act2Past),
 * ACT3_SCROLL_VARIANT=1, ACT2_FUTURE_VARIANT=1/2;165-259,265-561; palette Good/Bad Future11-96.
 * Native boss/exit RunLevelEvents, entry clears, order, clocks and adoption stay with the caller.
 * Input values and source views are not receipts that those operations have actually executed.
 * No media open, geometry/plane mutation, black-tile fallback, runtime/capture owner or hardware model.
 */
public final class CdR13FutureScrollPalette {
    /** Operand-derived finite source windows, including all BYTE-indexed dirty palette reads. */
    public enum Source {
        CLOUD_LINE_COUNTS(0x2A64,6,"16534bcf623dca180a01d3b8f6437e26740a817cebcfcaa7e8b2007885301d71",0x2AB0,4,"92f284df9bac152dbb38a5e301610a7e31fcff9ae7597b9aeb725971e3a53ca2"),
        INITIAL_SCROLL(0x28C8,70,"acc207b682bec95da0aa46067a511b884998ecab5702a49be465962a400ee246",0x2934,68,"13a9326bd5d67e5d2ae9cccc407cb381123c68b63ade0a89e300091d6e4d5ad3"),
        LEVEL_SCROLL(0x290E,464,"99007fdac5fc4abe8a603127a26981402e7b47765ec3e834379ca5e909bcfe86",0x2978,382,"fa69c631373eb0d84cc116622cd98f6a73da9bdc1231affa5b8418006fea2579"),
        PALETTE_HEAD(0x184,112,"e2e1d00399f8088ba0625fe8dec635d70a52da51c71a1825aeb5eb6f76bf24ba",0x184,192,"ebccf8eb0724b8898a36bb99da07f0eed55edd1806553a1cd42c1969a2f24579"),
        PALETTE_FIRST_READS(0x1F4,2048,"c2981ff9c83c49a38641c8018ed4f20c8d4dce08a91993620fef31b7d520572f",0x244,2048,"90864a70ffcde7c727385befca8c7ab16ae595473458fe55828e2511195bec1e"),
        PALETTE_SECOND_READS(0x20C,32,"f189c6f6efaf9d0da110ddbc778d5bdad2d8198413635295a6473db4e6807727",0x25C,32,"1b7f21c9a4825fc61fefc6436e838e00f67a377e609d5d5c2c1cbd91e7ef8c8a"),
        THIRD_SCRIPT(-1,0,"",0x274,18,"9fe8575233617616d42181b194daa77992747c795e253247881b8a72116631a0"),
        THIRD_COLOURS(-1,0,"",0x286,10,"859e034cdc7340f0b51336bbe191310918c627c2b223556069339f7b0f2843d5");
        private final int cOffset,cLength,dOffset,dLength;private final String cHash,dHash;
        Source(int cOffset,int cLength,String cHash,int dOffset,int dLength,String dHash){this.cOffset=cOffset;this.cLength=cLength;this.cHash=cHash;this.dOffset=dOffset;this.dLength=dLength;this.dHash=dHash;}
        private Range range(boolean good,CdNativeFile file){int at=good?cOffset:dOffset;if(at<0)throw new UnsupportedOperationException("Original R13C has no third palette script");return new Range(this,file,at,good?cLength:dLength,good?cHash:dHash);}
    }
    /** Comparison metadata only. Neither a Range nor an equal public Identity can bind a component. */
    public record Range(Source source,CdNativeFile file,int fileOffset,int length,String sha256){
        public Range{Objects.requireNonNull(source);Objects.requireNonNull(file);Objects.requireNonNull(sha256);if(fileOffset<0||length<=0||fileOffset>0x40000-length)throw new IllegalArgumentException("Finite original source range required");}
    }
    private final CdR13FutureAssets.Identity identity;
    private final boolean good;
    private final Map<Source,CdAssetBytes> sources;
    private final List<Range> ranges;
    private CdR13FutureScrollPalette(CdR13FutureAssets owner){
        Objects.requireNonNull(owner,"actual R13 source owner");owner.requireAuthenticatedLayout();identity=owner.identity();var image=owner.originalImage();
        good=identity.file()==CdNativeFile.R13C;
        var stage=good?CdStagePayload.R13C.stage():CdStagePayload.R13D.stage();
        String expected=good?"3bc8965dc5668a9d1223392f5f7762df41b930b797ebc76aaa5fcf334bc16b4f":"27e6c42268ea411d184b2b91b79ed626b39871b2823a5362573216ce29fd9356";
        if(identity.variant()!=CdReleaseVariant.USA||identity.domain()!=CdMmdImage.Domain.ORIGINAL_WORD_RAM
                ||identity.file()!=(good?CdNativeFile.R13C:CdNativeFile.R13D)||!stage.equals(identity.stage())
                ||!expected.equals(identity.mmdSha256())||image.fileBytes()!=0x40000||!expected.equals(CdR11aProfile.sha256(image.readFile(0,0x40000))))
            throw new IllegalArgumentException("Not the actual retained original USA C/D image");
        var data=new EnumMap<Source,CdAssetBytes>(Source.class);var spans=new ArrayList<Range>();
        for(var source:Source.values()){
            if(good&&(source==Source.THIRD_SCRIPT||source==Source.THIRD_COLOURS))continue;
            var r=source.range(good,identity.file());var bytes=CdAssetBytes.copyOf(image.readFile(r.fileOffset(),r.length()));
            if(!r.sha256().equals(CdR11aProfile.sha256(bytes.copy())))throw new IllegalArgumentException("R13 display source differs: "+source);
            data.put(source,bytes);spans.add(r);
        }
        sources=Collections.unmodifiableMap(data);ranges=List.copyOf(spans);
        // Actual LEA roots and native immediate/read operands, not equal-byte offset inference.
        require(image,0x184,0x41F9);requireLong(image,0x186,0x200000+Source.PALETTE_FIRST_READS.range(good,identity.file()).fileOffset());
        require(image,0x1BA,0xD0FC);require(image,0x1BC,0x18);
        int second=(int)(readLong(image.readFile(0x186,4),0)-0x200000)+(short)readWord(image.readFile(0x1BC,2),0);
        if(second!=Source.PALETTE_SECOND_READS.range(good,identity.file()).fileOffset())throw new IllegalArgumentException("R13 second palette root differs from actual ADDA operand");
        int cloud=good?0x29EA:0x2A4E;require(image,cloud,0x47F9);requireLong(image,cloud+2,0x200000+Source.CLOUD_LINE_COUNTS.range(good,identity.file()).fileOffset());requireLong(image,cloud+6,0x1A336000);
        int call=good?0x18A6:0x1912;
        requireLong(image,call-8,0x4A7900FF);require(image,call-4,0x190E);require(image,call-2,0x6604);require(image,call,0x6100);
        if(call+2+(short)readWord(image.readFile(call+2,2),0)!=0x184)throw new IllegalArgumentException("R13 actual time-stop-gated palette call differs");
        if(!good){require(image,0x1FA,0x43F9);requireLong(image,0x1FC,0x200274);require(image,0x200,0x45F9);requireLong(image,0x202,0x200286);}
    }
    /** Only the immutable authenticated C/D aggregate can supply original image/source identity. */
    public static CdR13FutureScrollPalette bind(CdR13FutureAssets owner){return new CdR13FutureScrollPalette(owner);}
    public CdR13FutureAssets.Identity identity(){return identity;}
    public List<Range> ranges(){return ranges;}
    public CdAssetBytes source(Source source){var b=sources.get(Objects.requireNonNull(source));if(b==null)throw new UnsupportedOperationException("Original R13C has no third palette script");return b;}
    public CdAssetBytes cloudLineCounts(){return source(Source.CLOUD_LINE_COUNTS);}
    public int paletteCycleCount(){return good?2:3;}

    /** The real USA caller owns complete RunLevelEvents (including PPZ3/bottom shift) before X/Y. */
    public CdR11aForegroundScroll.Result foregroundAfterEvents(CdR11aForegroundScroll.State before,CdR11aForegroundScroll.Input completedEvents){
        if(Objects.requireNonNull(completedEvents).region()!=CdReleaseVariant.USA)throw new IllegalArgumentException("This source is original USA only");
        return CdR11aForegroundScroll.stepAfterEvents(before,completedEvents);
    }
    /** Explicit complete SetupCamera integer-register cut; FG fractions are not borrowed as d0/d1. */
    public CdR11aBackgroundScroll.State initializeBackground(CdR11aBackgroundScroll.State before,CdR11aForegroundScroll.State setup){
        Objects.requireNonNull(before);Objects.requireNonNull(setup);
        int part=((short)setup.cameraYWord())<<12,y=part+part+part;
        int x=good?((short)setup.cameraX()>>3)&65535:setup.cameraX()>>>3;
        int half=good?((short)x>>1)&65535:x>>>1,x2=(half*3)&65535;
        half=good?((short)half>>1)&65535:half>>>1;int x3=(half*3)&65535;
        byte[] deform=before.deformationRam().copy();Arrays.fill(deform,0,good?20:16,(byte)0);
        return new CdR11aBackgroundScroll.State(storeWord(before.cameraBgX(),x),y,storeWord(before.cameraBg2X(),x2),storeWord(before.cameraBg2Y(),y>>>16),storeWord(before.cameraBg3X(),x3),storeWord(before.cameraBg3Y(),y>>>16),before.crossingRam(),before.scrollFlagsBg(),before.scrollFlagsBg2(),before.scrollFlagsBg3(),before.cloudRam(),CdAssetBytes.copyOf(deform),before.hscrollRam(),before.vscroll());
    }
    public record BackgroundInput(int cameraXWord,int cameraYWord,int scrollXDiff,int scrollYDiff,int scrollLockByte){
        public BackgroundInput{word(cameraXWord);word(cameraYWord);signedWord(scrollXDiff);signedWord(scrollYDiff);octet(scrollLockByte);}
    }
    /** Post-event/foreground BG continuation. Each source state/output is immutable; no publication. */
    public CdR11aBackgroundScroll.State background(CdR11aBackgroundScroll.State before,BackgroundInput actual){
        Objects.requireNonNull(before);Objects.requireNonNull(actual);if(actual.scrollLockByte()!=0)return before;
        int dx=actual.scrollXDiff(),dy=actual.scrollYDiff();int x=before.cameraBgX()+(dx<<5),x2=before.cameraBg2X()+(dx<<4)*3,x3=before.cameraBg3X()+(dx<<3)*3,y=before.cameraBgY()+(dy<<4)*3;
        byte[] crossed=before.crossingRam().copy();int f3=crossing(crossed,4,before.cameraBg3X(),x3,6),f2=crossing(crossed,2,before.cameraBg2X(),x2,4),f=crossing(crossed,0,before.cameraBgX(),x,2)|crossing(crossed,1,before.cameraBgY(),y,0);
        f|=(f2|f3)&0xFF00;f2&=255;f3&=255;
        byte[] deform=before.deformationRam().copy();int[] speeds=good?new int[]{0x10000,0xE000,0xC000,0xA000,0x8000}:new int[]{0x10000,0xC000,0x8000,0x4000};
        for(int i=0;i<speeds.length;i++)putLong(deform,4*i,readLong(deform,4*i)+speeds[i]);
        int start=good?20:16,cursor=start;for(int i=0;i<speeds.length;i++)cursor=words(deform,cursor,cloudLineCounts().unsignedByte(speeds.length-1-i)+1,-(readWord(deform,4*i)+(x3>>>16)));
        cursor=words(deform,cursor,good?6:8,-(x3>>>16));cursor=words(deform,cursor,2,-(x>>>16));words(deform,cursor,good?8:6,-(x2>>>16));
        byte[] hscroll=before.hscrollRam().copy();int high=((-actual.cameraXWord())&65535)<<16,bgY=y>>>16,destination=0;
        if(good){
            int coarse=(bgY&0x1F8)>>>3,blocks=31-coarse,gradientBlocks=28;
            if(blocks>=0){blocks=Math.min(blocks,27);gradientBlocks-=blocks;destination=fill(hscroll,destination,deform,start+((bgY&0x1F8)>>>2),blocks+1,bgY&7,high);}
            // Literal native gradient tail, Scroll:491-515; DIVS #256 is exact for signed WORD displacement.
            int increment=((short)(actual.cameraXWord()-(x2>>>16)))<<8,accumulator=(x2>>>16)<<16;
            for(int line=0;line<gradientBlocks*8;line++){putLong(hscroll,destination,high|((-(accumulator>>>16))&65535));destination+=4;accumulator+=increment;}
        }else fill(hscroll,0,deform,start+((bgY&0x1F8)>>>2),29,bgY&7,high);
        return new CdR11aBackgroundScroll.State(x,y,x2,storeWord(before.cameraBg2Y(),bgY),x3,storeWord(before.cameraBg3Y(),bgY),CdAssetBytes.copyOf(crossed),f,f2,f3,before.cloudRam(),CdAssetBytes.copyOf(deform),CdAssetBytes.copyOf(hscroll),(actual.cameraYWord()<<16)|bgY);
    }
    /** Native seven allocated timers/steps; C touches two, D three. Other bytes remain supplied. */
    public record PaletteState(CdAssetBytes palette,CdAssetBytes timers,CdAssetBytes steps){
        public PaletteState{exact(palette,128);exact(timers,7);exact(steps,7);}
        @Override public boolean equals(Object o){return o instanceof PaletteState s&&same(palette,s.palette)&&same(timers,s.timers)&&same(steps,s.steps);}
        @Override public int hashCode(){return 31*(31*Arrays.hashCode(palette.copy())+Arrays.hashCode(timers.copy()))+Arrays.hashCode(steps.copy());}
    }
    public record PaletteResult(PaletteState state,List<CdLevelPaletteCycle.Write> writes,Optional<CdLevelPaletteCycle.Fault> fault){
        public PaletteResult{Objects.requireNonNull(state);writes=List.copyOf(writes);Objects.requireNonNull(fault);}
        public boolean completed(){return fault.isEmpty();}
    }
    /** Actual Level_MainLoop WORD gate is supplied; no timer/clock/default or hidden service exists. */
    public PaletteResult palette(PaletteState before,int timeStopTimerWord){
        Objects.requireNonNull(before);word(timeStopTimerWord);if(timeStopTimerWord!=0)return new PaletteResult(before,List.of(),Optional.empty());
        byte[] palette=before.palette().copy(),timers=before.timers().copy(),steps=before.steps().copy();var writes=new ArrayList<CdLevelPaletteCycle.Write>();
        for(int cycle=0;cycle<2;cycle++){
            timers[cycle]=(byte)(timers[cycle]-1);if(timers[cycle]>=0)continue;timers[cycle]=(byte)(cycle==0?7:5);
            int step=(steps[cycle]&255)==2?0:((steps[cycle]&255)+1)&255;steps[cycle]=(byte)step;
            int at=(cycle==0?step:step&3)*8,destination=cycle==0?0x6A:0x58;var colours=source(cycle==0?Source.PALETTE_FIRST_READS:Source.PALETTE_SECOND_READS);
            for(int i=0;i<4;i++){int previous=readWord(palette,destination+i*2),next=colours.unsignedWord(at+i*2);putWord(palette,destination+i*2,next);writes.add(new CdLevelPaletteCycle.Write(cycle,destination/2+i,previous,next));}
        }
        Optional<CdLevelPaletteCycle.Fault> fault=Optional.empty();
        if(!good){var out=CdLevelPaletteCycle.single(new CdLevelPaletteCycle.Script(source(Source.THIRD_SCRIPT),source(Source.THIRD_COLOURS)),new CdLevelPaletteCycle.SingleState(CdAssetBytes.copyOf(palette),timers[2]&255,steps[2]&255));palette=out.state().palette().copy();timers[2]=(byte)out.state().timerByte();steps[2]=(byte)out.state().stepByte();for(var w:out.writes())writes.add(new CdLevelPaletteCycle.Write(2,w.paletteIndex(),w.beforeWord(),w.afterWord()));fault=out.fault().map(f->new CdLevelPaletteCycle.Fault(2,f.access(),f.byteOffset(),f.bytes()));}
        return new PaletteResult(new PaletteState(CdAssetBytes.copyOf(palette),CdAssetBytes.copyOf(timers),CdAssetBytes.copyOf(steps)),writes,fault);
    }
    private static int crossing(byte[] b,int index,int old,int next,int bit){if((((next>>>16)&16)^(b[index]&255))!=0)return 0;b[index]^=16;return 1<<((next-old<0?bit:bit+1)+8);}
    private static int storeWord(int previous,int word){return(word<<16)|(previous&65535);}
    private static int words(byte[] b,int at,int count,int value){for(int i=0;i<count;i++){putWord(b,at,value);at+=2;}return at;}
    private static int fill(byte[] h,int destination,byte[] d,int source,int blocks,int fine,int high){for(int i=0;i<blocks;i++){int value=high|readWord(d,source);source+=2;for(int line=0;line<(i==0?8-fine:8);line++){putLong(h,destination,value);destination+=4;}}return destination;}
    private static int readWord(byte[] b,int at){return(b[at]&255)<<8|(b[at+1]&255);}
    private static int readLong(byte[] b,int at){return readWord(b,at)<<16|readWord(b,at+2);}
    private static void putWord(byte[] b,int at,int value){b[at]=(byte)(value>>>8);b[at+1]=(byte)value;}
    private static void putLong(byte[] b,int at,int value){putWord(b,at,value>>>16);putWord(b,at+2,value);}
    private static void exact(CdAssetBytes b,int length){if(Objects.requireNonNull(b).length()!=length)throw new IllegalArgumentException("Complete explicit native value required");}
    private static boolean same(CdAssetBytes a,CdAssetBytes b){return Arrays.equals(a.copy(),b.copy());}
    private static void octet(int value){if((value&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
    private static void word(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void signedWord(int value){if(value<Short.MIN_VALUE||value>Short.MAX_VALUE)throw new IllegalArgumentException("Signed native WORD required");}
    private static void require(CdMmdImage image,int at,int word){if(readWord(image.readFile(at,2),0)!=word)throw new IllegalArgumentException("R13 display operand differs");}
    private static void requireLong(CdMmdImage image,int at,int value){if(readLong(image.readFile(at,4),0)!=value)throw new IllegalArgumentException("R13 display operand differs");}
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
