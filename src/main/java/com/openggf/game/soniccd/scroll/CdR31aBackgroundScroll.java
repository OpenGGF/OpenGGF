package com.openggf.game.soniccd.scroll;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR31aAssets;
import java.util.Arrays;
import java.util.Objects;
import static com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll.State;

/** Pure original-USA Collision Chaos Act1 Present background slice.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Collision Chaos/Scroll (Act 1 Present).asm:199-232,270-627;
 * src/Level/Scroll Flag Set.asm:102-224.
 * No Frame, event, clock, programme, current-art, renderer or capture authority.
 * State/Result/fault prefixes are immutable values, never native completion receipts. */
public final class CdR31aBackgroundScroll {
    private final CdR31aAssets assets;
    private final CdAssetBytes repeats, bounds, wobble;

    private CdR31aBackgroundScroll(CdR31aAssets assets,CdAssetBytes repeats,CdAssetBytes bounds,CdAssetBytes wobble) {
        this.assets=assets;this.repeats=Objects.requireNonNull(repeats,"repeat counts");
        this.bounds=Objects.requireNonNull(bounds,"water bounds");this.wobble=Objects.requireNonNull(wobble,"wobble bytes");
    }
    /** Exact retained aggregate/views; no media lookup, art preparation or runtime admission. */
    public static CdR31aBackgroundScroll fromAssets(CdR31aAssets assets) {
        Objects.requireNonNull(assets,"original R31A assets");
        return new CdR31aBackgroundScroll(assets,assets.view(CdR31aAssets.Asset.SCROLL_REPEAT_COUNTS),
                assets.view(CdR31aAssets.Asset.SCROLL_WATER_BOUNDS),assets.view(CdR31aAssets.Asset.WOBBLE));
    }
    /** Package-private constructed-format controls only; cannot retain or mint an authentic aggregate. */
    static CdR31aBackgroundScroll fromTables(CdAssetBytes repeats,CdAssetBytes bounds,CdAssetBytes wobble) {
        return new CdR31aBackgroundScroll(null,repeats,bounds,wobble);
    }
    public boolean ownsAssets(CdR31aAssets expected) {return assets!=null&&assets==expected;}

    /** Completed SetupCamera integer WORDs; its unused register high halves have no persisted Init consumer. */
    public record InitInput(int cameraXWord,int cameraYWord) {
        public InitInput {word(cameraXWord);word(cameraYWord);}
    }
    /** Actual post-event/foreground values. scrollLockByte is the native WORD's HIGH byte;
     * waterDeformWord is the complete separate bgWaterDeform WORD, not an invented timer. */
    public record Input(int cameraXWord,int cameraYWord,int scrollXDiff,int scrollLockByte,int waterDeformWord) {
        public Input {word(cameraXWord);word(cameraYWord);if(scrollXDiff<Short.MIN_VALUE||scrollXDiff>Short.MAX_VALUE)throw new IllegalArgumentException("Signed native scrollXDiff WORD required");octet(scrollLockByte);word(waterDeformWord);}
    }
    public enum Boundary { SCROLL_LOCKED, COMPLETE, STOPPED }
    public enum Access { REPEAT_COUNTS, WATER_BOUNDS, WOBBLE, DEFORMATION_READ, DEFORMATION_WRITE, HSCROLL_WRITE }
    public record Result(State state,int waterDeformWord,Boundary boundary,int deformationWordsWritten,int hscrollLongsWritten) {
        public Result {Objects.requireNonNull(state);word(waterDeformWord);Objects.requireNonNull(boundary);if(deformationWordsWritten<0||hscrollLongsWritten<0)throw new IllegalArgumentException("Negative completed write count");}
    }
    /** First unowned finite view/RAM span. The prefix is comparison-only, not adoption or resume authority. */
    public static final class ScrollFault extends IllegalArgumentException {
        private final Access access;private final int byteOffset,bytes;private final Result prefix;
        private ScrollFault(Access access,int byteOffset,int bytes,Result prefix) {
            super("R31 background "+access+" at byte "+byteOffset+" / "+bytes);this.access=access;this.byteOffset=byteOffset;this.bytes=bytes;this.prefix=prefix;
        }
        public Access access(){return access;}public int byteOffset(){return byteOffset;}public int bytes(){return bytes;}public Result prefix(){return prefix;}
    }
    /** Init199-232: three X WORDs, BG1 complete Y LONG, BG2/3 Y WORDs and15 CLR.Ls only. */
    public State initialize(State before,InitInput input) {
        Objects.requireNonNull(before,"complete background state");Objects.requireNonNull(input,"completed camera setup");
        int y=backgroundY(input.cameraYWord());int x3=input.cameraXWord()>>>4,x2=(x3>>>1)*3,x=(x3>>>2)*3;
        byte[] deformation=before.deformationRam().copy();Arrays.fill(deformation,0,60,(byte)0);
        return new State(storeWord(before.cameraBgX(),x),y<<16,storeWord(before.cameraBg2X(),x2),storeWord(before.cameraBg2Y(),y),
                storeWord(before.cameraBg3X(),x3),storeWord(before.cameraBg3Y(),y),before.crossingRam(),before.scrollFlagsBg(),before.scrollFlagsBg2(),before.scrollFlagsBg3(),
                before.cloudRam(),CdAssetBytes.copyOf(deformation),before.hscrollRam(),before.vscroll());
    }
    /** Slice270-627 AFTER the caller's full flag clears251-254, USA events, then FG X/Y257-259.
     * It consumes those actual post-FG flag WORDs. Dirty constructed values retain all bits the slice
     * does not store; this method never relocates the earlier clears across external events.
     * The original244 lock early return performs no table/scalar writes. */
    public Result step(State before,Input input) {
        Objects.requireNonNull(before,"complete background state");Objects.requireNonNull(input,"completed foreground input");
        if(input.scrollLockByte()!=0)return new Result(before,input.waterDeformWord(),Boundary.SCROLL_LOCKED,0,0);
        var w=new Work(before,input);int dx=input.scrollXDiff();
        w.x3=before.cameraBg3X()+(dx<<4);w.f3|=crossing(w.crossed,4,before.cameraBg3X(),w.x3,6);
        int part=dx<<3;w.x2=before.cameraBg2X()+part+part+part;w.f2|=crossing(w.crossed,2,before.cameraBg2X(),w.x2,4);
        part=dx<<2;w.x=before.cameraBgX()+part+part+part;w.f|=crossing(w.crossed,0,before.cameraBgX(),w.x,2);
        int y=backgroundY(input.cameraYWord()),oldY=before.cameraBgYWord();w.y=storeWord(w.y,y);
        if((((y&16)^(w.crossed[1]&255))&255)==0){w.crossed[1]^=16;w.f|=((short)(y-oldY)<0?1:2)<<8;}
        w.y2=storeWord(w.y2,y);w.y3=storeWord(w.y3,y);w.vscroll=(input.cameraYWord()<<16)|y;
        w.f|=(w.f2|w.f3)&0xFF00;w.f2&=255;w.f3&=255;

        int[] increments={0xC000,0x8000,0x6000,0x4000,0x3000,0x2000,0x1000,0x800,0x1000,0x2000,0x3000,0x4000,0x6000,0x8000,0xC000};
        for(int n=0;n<15;n++)w.dlong(n*4,w.dlong(n*4)+increments[n]);
        for(int n=0;n<64;n++)w.dlong(60+n*4,w.dlong(60+n*4)+0x8000+n*0x400);
        int bg=w.x>>>16,bg2=w.x2>>>16,bg3=w.x3>>>16;
        int increment=perspectiveIncrement(input.cameraXWord(),bg,28);
        // BG1 helper's MOVE.B crossing and Y helper's MOVE.W preserve this scratch high WORD.
        // Treating it as a new zero fraction changes even the first negative-delta perspective.
        int position=((dx<<3)&0xFFFF0000)|bg;
        for(int n=0;n<7;n++){w.dword(0x148-n*2,-position);position=swap(swap(position)+increment);}
        int at=0x14A;for(int n=0;n<3;n++,at+=2)w.dword(at,-bg);for(int n=0;n<4;n++,at+=2)w.dword(at,-bg3);
        for(int n=0;n<15;n++){
            int value=-((w.dlong(n*4)>>>16)+bg),repeat=w.read(repeats,14-n,1,Access.REPEAT_COUNTS)+1;
            for(int i=0;i<repeat;i++,at+=2)w.dword(at,value);
        }
        for(int n=0;n<6;n++,at+=2)w.dword(at,-bg);for(int n=0;n<4;n++,at+=2)w.dword(at,-bg3);
        for(int n=0;n<8;n++,at+=2)w.dword(at,-bg2);for(int n=0;n<4;n++,at+=2)w.dword(at,-bg);
        increment=perspectiveIncrement(input.cameraXWord(),bg,40);position=(position&0xFFFF0000)|bg;
        for(int n=0;n<10;n++,at+=2){w.dword(at,-position);position=swap(swap(position)+increment);}
        for(int n=0;n<8;n++,at+=2)w.dword(at,-bg);

        int rowOffset=(y&0x3F8)>>>2,groups=0x57-(rowOffset>>>1);
        // backgroundY's actual carry/clamp produces0..536, hence no source463 borrow branch.
        if(groups>=0x1B)groups=0x1C;int finalGroups=0x1D-groups;
        w.water=uw(w.water+0x40);var rows=new Rows(w,y,0x13C+rowOffset,groups,(-input.cameraXWord())<<16);
        rows.initial();int relativeY=uw(rows.y-y),phase=w.water>>>8,foreground=uw(-input.cameraXWord());
        for(int n=0;n<finalGroups*8;n++){
            int background=uw(-((w.dlong(60+n*4)>>>16)+bg));
            if(relativeY>=uw(0x5C0-input.cameraYWord()))foreground=uw(-(input.cameraXWord()+(byte)w.read(wobble,(phase&127)*2,1,Access.WOBBLE)));
            w.hlong((foreground<<16)|background);relativeY=uw(relativeY+1);phase=uw(phase+1);
        }
        return w.result(Boundary.COMPLETE);
    }

    /** Native unsigned carry followed by WORD N, not a signed camera interpretation. */
    private static int backgroundY(int cameraY) {
        if(cameraY>0x520)return 0x218;int y=uw(0x218-((0x520-cameraY)>>>1));return(short)y<0?0:y;
    }
    private static int perspectiveIncrement(int cameraX,int bgX,int divisor) {
        int numerator=((short)(cameraX-bgX))<<6,quotient=numerator/divisor;
        // DIVS overflow leaves the original destination unchanged; the next EXT.L sees its low WORD.
        int raw=quotient<Short.MIN_VALUE||quotient>Short.MAX_VALUE?numerator:quotient;return((short)raw)<<10;
    }
    private static int crossing(byte[] crossed,int at,int old,int next,int bit) {
        if(((((next>>>16)&16)^(crossed[at]&255))&255)!=0)return 0;
        crossed[at]^=16;return 1<<(((next-old)<0?bit:bit+1)+8);
    }
    private final class Work {
        final State before;final byte[] crossed,deformation,hscroll;
        int x,y,x2,y2,x3,y3,f,f2,f3,vscroll,water,deformWrites,hscrollWrites;
        Work(State before,Input input){this.before=before;x=before.cameraBgX();y=before.cameraBgY();x2=before.cameraBg2X();y2=before.cameraBg2Y();x3=before.cameraBg3X();y3=before.cameraBg3Y();f=before.scrollFlagsBg();f2=before.scrollFlagsBg2();f3=before.scrollFlagsBg3();vscroll=before.vscroll();water=input.waterDeformWord();crossed=before.crossingRam().copy();deformation=before.deformationRam().copy();hscroll=before.hscrollRam().copy();}
        Result result(Boundary boundary){return new Result(new State(x,y,x2,y2,x3,y3,CdAssetBytes.copyOf(crossed),f,f2,f3,before.cloudRam(),CdAssetBytes.copyOf(deformation),CdAssetBytes.copyOf(hscroll),vscroll),water,boundary,deformWrites,hscrollWrites);}
        ScrollFault fault(Access access,int at,int width){return new ScrollFault(access,at,width,result(Boundary.STOPPED));}
        void check(int length,int at,int width,Access access){if(at<0||width>length||at>length-width)throw fault(access,at,width);}
        int read(CdAssetBytes source,int at,int width,Access access){check(source.length(),at,width,access);return width==1?source.unsignedByte(at):source.unsignedWord(at);}
        int dword(int at){check(deformation.length,at,2,Access.DEFORMATION_READ);return((deformation[at]&255)<<8)|(deformation[at+1]&255);}
        int dlong(int at){check(deformation.length,at,4,Access.DEFORMATION_READ);return(dword(at)<<16)|dword(at+2);}
        void dword(int at,int value){check(deformation.length,at,2,Access.DEFORMATION_WRITE);deformation[at]=(byte)(value>>>8);deformation[at+1]=(byte)value;deformWrites++;}
        void dlong(int at,int value){check(deformation.length,at,4,Access.DEFORMATION_WRITE);dword(at,value>>>16);dword(at+2,value);}
        void hlong(int value){int at=hscrollWrites*4;check(hscroll.length,at,4,Access.HSCROLL_WRITE);hscroll[at]=(byte)(value>>>24);hscroll[at+1]=(byte)(value>>>16);hscroll[at+2]=(byte)(value>>>8);hscroll[at+3]=(byte)value;hscrollWrites++;}
    }
    /** Finite native water-run arithmetic, with exact first table reads and DBF counters. */
    private final class Rows {
        final Work w;int y,source,groups,packed,boundAt;
        Rows(Work w,int y,int source,int groups,int packed){this.w=w;this.y=y;this.source=source;this.groups=groups;this.packed=packed;}
        int boundary(){return w.read(bounds,boundAt,2,Access.WATER_BOUNDS);}
        int advanceBoundary(){int v=boundary();boundAt+=2;return v;}
        void value(){packed=(packed&0xFFFF0000)|w.dword(source);source+=2;}
        void run(int lines){for(int n=0;n<lines;n++)w.hlong(packed);groups=uw(groups-1);}
        void interpolate(int lines){
            int phase=w.water>>>8;for(int n=0;n<lines;n++){int sample=(byte)w.read(wobble,phase&255,1,Access.WOBBLE);packed=(packed&0xFFFF0000)|uw(-(sample+(w.x>>>16)));w.hlong(packed);y=uw(y+1);phase=uw(phase+1);}
            // Native retains the last loaded WORD even though these source rows are consumed silently.
            int skipped=((lines-1)>>>3)+1;for(int n=0;n<skipped;n++){value();groups=uw(groups-1);}
        }
        void initial(){
            int transition=0;
            if(y>=boundary()){
                transition=(short)(uw(advanceBoundary()+24)-y);
                if(transition<=0&&y>=boundary())transition=(short)(uw(advanceBoundary()+24)-y);
            }
            if(transition>0)interpolate(transition);
            else{int alignment=y&7;y=uw(y+8-alignment);value();run(8-alignment);}
            while((short)groups>=0){
                if(y>=boundary()){
                    transition=(short)(uw(advanceBoundary()+24)-y);
                    if(transition>0){interpolate(transition);continue;}
                }
                value();y=uw(y+8);run(8);
            }
        }
    }
    private static int storeWord(int before,int word){return(before&65535)|(word<<16);}
    private static int swap(int value){return(value<<16)|(value>>>16);}
    private static int uw(int value){return value&65535;}
    private static void word(int value){if((value&~65535)!=0)throw new IllegalArgumentException("Unsigned native WORD required");}
    private static void octet(int value){if((value&~255)!=0)throw new IllegalArgumentException("Unsigned native BYTE required");}
}
