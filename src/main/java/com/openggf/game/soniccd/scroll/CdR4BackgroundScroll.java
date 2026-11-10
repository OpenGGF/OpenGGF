package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import java.util.Arrays;
import java.util.Objects;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State;

/** Detached TT background semantics, N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Historical R41A Legacy Scroll sub_202C94:111-161, UpdateScroll:165-366;
 * R41B/C/D Legacy Scroll sub_202C32/sub_202C6C/sub_202C5A:172-197, UpdateScroll:201-320;
 * r4/scroll_2[a-d]:172-191,195-314; scroll_3c:146-165,169-294; scroll_3d:161-180,184-309.
 * Root owns authenticated programme selection, events/FG, retained state and actual invocation.
 * No water events/sway, clocks, current application, loading, draw or publication authority.
 */
public final class CdR4BackgroundScroll {
    private CdR4BackgroundScroll() { }
    /** Distinct algorithms/operands, not source identities or admission tokens. */
    public enum Profile {
        ACT1_PRESENT(true,0x7F8,true), ACT1_OTHER(false,0x7F8,true), ACT2_OR_3(false,0x3F8,false);
        final boolean perspective, clearInitialBands; final int rowMask;
        Profile(boolean perspective,int rowMask,boolean clear){this.perspective=perspective;this.rowMask=rowMask;clearInitialBands=clear;}
    }
    /** Borrowed finite media views; optional reached RAM beyond the 512-byte band allocation.
     * Empty/short views remain unknown, never padded. Equal bytes confer no producer authority. */
    public record Tables(CdAssetBytes wobble,CdAssetBytes perspectiveRepeats,CdAssetBytes adjacentBandRam) {
        public Tables {Objects.requireNonNull(wobble);Objects.requireNonNull(perspectiveRepeats);Objects.requireNonNull(adjacentBandRam);}
    }
    /** Post-event/FG values; scrollLock is the high BYTE of its native WORD.
     * waterY belongs to the external UpdateWater owner; deform is the separate bg_water_deform WORD. */
    public record Input(int cameraX,int cameraY,int scrollXDiff,int scrollYDiff,int scrollLock,int waterY,int deform) {
        public Input {word(cameraX);word(cameraY);signed(scrollXDiff);signed(scrollYDiff);octet(scrollLock);word(waterY);word(deform);}
    }
    public enum Access { REPEATS, WOBBLE, BAND_READ, BAND_WRITE, HSCROLL_WRITE }
    public record Result(State state,int deform,int bandWordsWritten,int hscrollWordsWritten) { }
    public static final class ScrollFault extends IllegalArgumentException {
        private final Access access; private final int offset,bytes; private final Result prefix;
        private ScrollFault(Access access,int offset,int bytes,Result prefix){super("TT "+access+" at "+offset+" / "+bytes);this.access=access;this.offset=offset;this.bytes=bytes;this.prefix=prefix;}
        public Access access(){return access;} public int offset(){return offset;} public int bytes(){return bytes;} public Result prefix(){return prefix;}
    }
    /** Actual completed SetupCamera integer words only; low camera fractions not written remain dirty. */
    public static State initialize(Profile profile,State before,int cameraX,int cameraY) {
        Objects.requireNonNull(profile);Objects.requireNonNull(before);word(cameraX);word(cameraY);
        int y=profile.perspective?presentY(cameraX,cameraY):((cameraY>>>1)<<16);
        int x3=cameraX>>>4,x2=(x3>>>1)*3,x=(x3>>>2)*3;
        byte[] bands=before.deformationRam().copy();if(profile.clearInitialBands)Arrays.fill(bands,0,60,(byte)0);
        return new State(store(before.cameraBgX(),x),y,store(before.cameraBg2X(),x2),store(before.cameraBg2Y(),y>>>16),
                store(before.cameraBg3X(),x3),store(before.cameraBg3Y(),y>>>16),before.crossingRam(),before.scrollFlagsBg(),before.scrollFlagsBg2(),before.scrollFlagsBg3(),before.cloudRam(),CdAssetBytes.copyOf(bands),before.hscrollRam(),before.vscroll());
    }
    /** Starts AFTER actual flag clears, USA events, foreground X/Y; does not repeat those operations.
     * Lock returns before all scalar/table reads and writes. Failure retains precisely reached stores. */
    public static Result step(Profile profile,State before,Input input,Tables tables) {
        Objects.requireNonNull(profile);Objects.requireNonNull(before);Objects.requireNonNull(input);
        if(input.scrollLock()!=0)return new Result(before,input.deform(),0,0);
        Objects.requireNonNull(tables);var w=new Work(before,input,tables);
        int dx=input.scrollXDiff(),dy=input.scrollYDiff();
        if(profile.perspective){
            w.x3+=dx<<4;w.f3|=crossing(w.crossed,4,before.cameraBg3X(),w.x3,6);
            w.x2+=(dx<<3)*3;w.f2|=crossing(w.crossed,2,before.cameraBg2X(),w.x2,4);
        }
        w.x+=(dx<<2)*3;w.f|=crossing(w.crossed,0,before.cameraBgX(),w.x,2);
        if(profile.perspective){int y=presentY(input.cameraX(),input.cameraY())>>>16;w.y=store(w.y,y);w.f|=crossingY(w.crossed,before.cameraBgYWord(),y);}
        else {w.y+=dy<<7;w.f|=crossing(w.crossed,1,before.cameraBgY(),w.y,0);}
        int y=w.y>>>16;w.y2=store(w.y2,y);w.y3=store(w.y3,y);w.vscroll=(input.cameraY()<<16)|y;
        w.f|=(w.f2|w.f3)&0xFF00;w.f2&=255;w.f3&=255;
        if(profile.perspective){
            w.repeat(16,-(w.x>>>16));
            // Native DIVU overflow retains numerator; EXT.L uses its low signed WORD.
            int numerator=((short)(input.cameraX()-(w.x3>>>16)))<<7;
            long quotient=Integer.toUnsignedLong(numerator)/5;
            int increment=((short)(quotient>65535?numerator:(int)quotient))<<9;
            int position=((dx<<3)&0xFFFF0000)|(w.x3>>>16);
            for(int group=4;group>=0;group--){int count=w.read(tables.perspectiveRepeats(),group,Access.REPEATS)+1;w.repeat(count,-position);position=swap(swap(position)+increment);}
            w.repeat(144,-(w.x2>>>16));
        }else w.repeat(192,-(w.x>>>16));
        int source=(y&profile.rowMask)>>>2,phase=((input.deform()>>>8)+y)&255;
        w.deform=(input.deform()+0x80)&65535;
        int fine=y&7,bg=w.band(source);source+=2;
        int foreground=(-input.cameraX())&65535,lineY=input.cameraY();boolean underwater=false;
        for(int line=0;line<232;line++){
            // Once native enters the wet loop it never returns to the signed-water compare.
            if(!underwater&&(short)lineY>=(short)input.waterY())underwater=true;
            if(underwater){w.hword(foreground);int wave=(byte)w.read(tables.wobble(),phase,Access.WOBBLE);w.hword(bg+wave);}
            else {w.hlong((foreground<<16)|bg);lineY=(lineY+1)&65535;}
            phase=(phase+1)&255;
            if(++fine==8){fine=0;bg=w.band(source);source+=2;} // Also reached AFTER final output.
        }
        return w.result();
    }
    private static int presentY(int x,int y){if(y<0x200)return (x<0x280?y:0x200)<<16;return ((y-0x200)<<14)*3+0x2000000;}
    private static int crossing(byte[] b,int at,int old,int next,int bit){if((((next>>>16)&16)^(b[at]&255))!=0)return 0;b[at]^=16;return 1<<((next-old<0?bit:bit+1)+8);}
    private static int crossingY(byte[] b,int old,int next){if(((next&16)^(b[1]&255))!=0)return 0;b[1]^=16;return 1<<(((short)(next-old)<0?0:1)+8);}
    private static int store(int old,int word){return(word<<16)|(old&65535);}
    private static int swap(int v){return v<<16|v>>>16;}
    private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void octet(int v){if((v&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
    private static void signed(int v){if(v<Short.MIN_VALUE||v>Short.MAX_VALUE)throw new IllegalArgumentException("Signed WORD required");}
    private static final class Work {
        final State before;final Tables tables;final byte[] crossed,bands,hscroll;
        int x,y,x2,y2,x3,y3,f,f2,f3,vscroll,deform,bandWords,hwords;
        Work(State b,Input i,Tables t){before=b;tables=t;crossed=b.crossingRam().copy();bands=b.deformationRam().copy();hscroll=b.hscrollRam().copy();x=b.cameraBgX();y=b.cameraBgY();x2=b.cameraBg2X();y2=b.cameraBg2Y();x3=b.cameraBg3X();y3=b.cameraBg3Y();f=b.scrollFlagsBg();f2=b.scrollFlagsBg2();f3=b.scrollFlagsBg3();vscroll=(i.cameraY()<<16)|b.cameraBgYWord();deform=i.deform();}
        Result result(){return new Result(new State(x,y,x2,y2,x3,y3,CdAssetBytes.copyOf(crossed),f,f2,f3,before.cloudRam(),CdAssetBytes.copyOf(bands),CdAssetBytes.copyOf(hscroll),vscroll),deform,bandWords,hwords);}
        void check(int length,int at,int width,Access access){if(at<0||at>length-width)throw new ScrollFault(access,at,width,result());}
        int read(CdAssetBytes b,int at,Access access){check(b.length(),at,1,access);return b.unsignedByte(at);}
        int band(int at){int length=bands.length+tables.adjacentBandRam().length();check(length,at,2,Access.BAND_READ);int hi=at<512?bands[at]&255:tables.adjacentBandRam().unsignedByte(at-512);int lo=at+1<512?bands[at+1]&255:tables.adjacentBandRam().unsignedByte(at+1-512);return hi<<8|lo;}
        void repeat(int n,int v){for(int i=0;i<n;i++){int at=bandWords*2;check(bands.length,at,2,Access.BAND_WRITE);bands[at]=(byte)(v>>>8);bands[at+1]=(byte)v;bandWords++;}}
        void hword(int v){int at=hwords*2;check(hscroll.length,at,2,Access.HSCROLL_WRITE);hscroll[at]=(byte)(v>>>8);hscroll[at+1]=(byte)v;hwords++;}
        void hlong(int v){check(hscroll.length,hwords*2,4,Access.HSCROLL_WRITE);hword(v>>>16);hword(v);}
    }
}
