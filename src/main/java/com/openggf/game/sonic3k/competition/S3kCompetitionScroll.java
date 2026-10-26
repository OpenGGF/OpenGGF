package com.openggf.game.sonic3k.competition;

import com.openggf.data.Rom;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.competition.S3kCompetitionSession.Arena;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The competition world's two native cameras and background event owner.
 * Locked-on LevelEventArray points into the S3 half for these routines:
 * Comp_ScreenInit/Comp_ScreenEvent/CGZ_ScreenEvent and ALZ..EMZ background
 * events (s3.asm:69220-69727; sonic3k.asm:102315-102334).
 * This owner never installs an ordinary zone handler or ticks a generic Camera.
 */
public final class S3kCompetitionScroll implements RewindSnapshottable<S3kCompetitionScroll.State> {
    public static final int SCREEN_PROFILE_ADDRESS=0x23AA08;
    public static final int COMPETITION_LEVEL_SIZES_ADDRESS=0x1BDA6;
    public static final int ALZ_DELTA_ADDRESS=0x23BE5A;
    public static final int PLAYER_1_RASTER_LINES=0x6C, PLAYER_2_RASTER_LINES=0x74;
    private static final int[] DEFORM_ADDRESS={0x23AA58,0x23AA6E,0,0x23AA7C,0x23AA86};
    private static final int[] DEFORM_WORDS={11,7,0,5,10};

    public record CameraPosition(int x,int y) { }
    public record CameraPair(CameraPosition player1,CameraPosition player2) {
        public CameraPair { Objects.requireNonNull(player1);Objects.requireNonNull(player2); }
    }
    public record CameraBounds(int minimumX,int maximumX,int minimumY,int maximumY) { }
    /** Eight Comp_ScreenInitArray words, with the native refresh-row contract retained. */
    public record Profile(int xWrapMask,int yWrapMask,int cameraYMask,int layoutRowIndexMask,
            int vramDuplicateStride,int foregroundRefreshY,int refreshRowsMinusOne,int refreshColumns,
            CameraBounds bounds) { }
    /** Look distance is the original word ($60), before MoveCameraY halves it. */
    public record Tracking(S3kCompetitionPlayer player,int distanceFromTop,boolean scrollLocked) {
        public Tracking { Objects.requireNonNull(player); }
        public Tracking(S3kCompetitionPlayer player) { this(player,0x60,false); }
    }
    public record TrackingOptions(boolean teleportActive,boolean fastVerticalScroll,boolean maximumYChanging) {
        public static final TrackingOptions NORMAL=new TrackingOptions(false,false,false);
    }
    /** Native packed scroll words: FG in the high word, BG in the low word. */
    public record View(int slot,CameraPosition camera,int cameraYFraction,int horizontalDelta,int verticalDelta,
            int backgroundX,int backgroundY,int foregroundVScroll,int backgroundVScroll,
            int nativeRasterStart,List<Integer> horizontalScroll,List<Integer> intermediate,
            int unwrappedX,int unwrappedY,int cameraLagWord) {
        public View { horizontalScroll=List.copyOf(horizontalScroll);intermediate=List.copyOf(intermediate); }
        public int[] horizontalScrollArray() { return horizontalScroll.stream().mapToInt(Integer::intValue).toArray(); }
    }
    public record ViewState(int cameraX,int cameraYFixed,int horizontalDelta,int verticalDelta,
            int previousX,int unwrappedX,int previousY,int unwrappedY,int backgroundX,int backgroundY,
            int backgroundAuxX,int historyIndex,int cameraLagWord,List<Integer> historyX,List<Integer> table) {
        public ViewState { historyX=List.copyOf(historyX);table=List.copyOf(table); }
    }
    public record State(Arena arena,int act,int totalLaps,boolean initialized,int waveCounter,int cloudDrift,
            int cgzVerticalRatio,boolean maximumYChanging,List<ViewState> views) {
        public State { views=List.copyOf(views); }
    }
    private static final class ViewRuntime {
        int cameraX,cameraYFixed,horizontalDelta,verticalDelta,previousX,unwrappedX,previousY,unwrappedY;
        int backgroundX,backgroundY,backgroundAuxX,historyIndex,cameraLagWord;
        final int[] historyX=new int[64],table=new int[128];
        int cameraY() { return (short)(cameraYFixed>>16); }
        void writeCameraYWord(int y) { cameraYFixed=(y<<16)|(cameraYFixed&0xFFFF); }
    }

    private final Arena arena;
    private final int act,totalLaps;
    private final Profile profile;
    private final int[] heights,alzDelta;
    private final ViewRuntime[] views={new ViewRuntime(),new ViewRuntime()};
    private final int[] raster=new int[224];
    private boolean initialized,maximumYChanging;
    private int waveCounter,cloudDrift,cgzVerticalRatio;

    public S3kCompetitionScroll(Rom rom,Arena arena,int totalLaps) throws IOException {
        this(rom,arena,0,totalLaps);
    }
    public S3kCompetitionScroll(Rom rom,Arena arena,int act,int totalLaps) throws IOException {
        this.arena=Objects.requireNonNull(arena);
        if(act<0 || act>1 || totalLaps<1 || totalLaps>255) throw new IllegalArgumentException("Invalid native competition act/lap count");
        this.act=act;this.totalLaps=totalLaps;
        int index=arena.zoneId()-0xE;
        int[] words=readWords(rom,SCREEN_PROFILE_ADDRESS+index*16,8,false);
        int[] bounds=readWords(rom,COMPETITION_LEVEL_SIZES_ADDRESS+index*16+act*8,4,true);
        profile=new Profile(words[0],words[1],words[2],words[3],words[4],words[5],words[6],words[7],
                new CameraBounds(bounds[0],bounds[1],bounds[2],bounds[3]));
        heights=DEFORM_WORDS[index]==0?new int[0]:readWords(rom,DEFORM_ADDRESS[index],DEFORM_WORDS[index],false);
        alzDelta=arena==Arena.AZURE_LAKE?readWords(rom,ALZ_DELTA_ADDRESS,256,true):new int[0];
    }
    private static int[] readWords(Rom rom,int address,int count,boolean signed) throws IOException {
        byte[] bytes=rom.readBytes(address,count*2);int[] result=new int[count];
        for(int i=0;i<count;i++) { int word=(bytes[i*2]&255)<<8 | bytes[i*2+1]&255;result[i]=signed?(short)word:word; }
        return result;
    }
    public Profile profile() { return profile; }
    public Arena arena() { return arena; }
    public int cgzVerticalRatio() { return cgzVerticalRatio; }

    /** Native Get_LevelSizeStart bootstrap; no normal campaign camera is consulted. */
    public void initializeFromPlayers(S3kCompetitionPlayer player1,S3kCompetitionPlayer player2) {
        requireSlot(player1,0);
        if(player2!=null) requireSlot(player2,1);
        S3kCompetitionPlayer second=player2==null?player1:player2;
        // loc_1A454/loc_1A46E write the P1-derived start camera to BOTH slots.
        CameraPosition start=initialCamera(player1);
        initialize(new CameraPair(start,start));
        resetPositionHistory(0,player1.centreX());resetPositionHistory(1,second.centreX());
    }
    private CameraPosition initialCamera(S3kCompetitionPlayer player) {
        int x=Math.max(0,(player.centreX()&0xFFFF)-0xA0);
        int y=Math.max(0,(player.centreY()&0xFFFF)-0x60);
        if((short)y>=profile.bounds().maximumY()) y=profile.bounds().maximumY();
        return new CameraPosition(x,y);
    }
    /** Explicit native camera-copy bootstrap, also useful for bounded presentation probes. */
    public void initialize(CameraPair cameras) {
        waveCounter=cloudDrift=0;maximumYChanging=false;
        cgzVerticalRatio=arena==Arena.CHROME_GADGET?0x900000/((((totalLaps+1)<<8)-0x70)&0xFFFF):0;
        CameraPosition[] positions={cameras.player1(),cameras.player2()};
        for(int slot=0;slot<2;slot++) {
            ViewRuntime v=views[slot];CameraPosition p=positions[slot];
            v.cameraX=(short)p.x();v.cameraYFixed=p.y()<<16;v.horizontalDelta=v.verticalDelta=0;
            v.previousX=v.unwrappedX=v.cameraX;v.previousY=v.unwrappedY=v.cameraY();
            v.backgroundX=v.backgroundY=v.backgroundAuxX=v.historyIndex=v.cameraLagWord=0;
            Arrays.fill(v.table,0);Arrays.fill(v.historyX,(v.cameraX+0xA0)&0xFFFF);
        }
        initialized=true;
        // Every native BackgroundInit executes its BackgroundEvent once.
        backgroundEvent();
    }

    /**
     * The competition camera boundary calls this once after object execution.
     * Null P2 retains the second camera for a one-human view. Position history
     * is recorded separately immediately after each actor, before object solid
     * contacts can change that actor's position.
     */
    public void trackPlayers(Tracking player1,Tracking player2,TrackingOptions options) {
        requireInitialized();Objects.requireNonNull(player1);Objects.requireNonNull(options);
        requireSlot(player1.player(),0);
        if(player2!=null) requireSlot(player2.player(),1);
        maximumYChanging=options.maximumYChanging();
        Tracking[] tracking={player1,player2};
        for(int slot=0;slot<2;slot++) {
            ViewRuntime v=views[slot];v.horizontalDelta=v.verticalDelta=0;
            Tracking input=tracking[slot];
            if(input==null || input.scrollLocked()) continue;
            trackX(v,input.player(),options.teleportActive());
            trackY(v,input,options.fastVerticalScroll());
        }
        screenEvent();backgroundEvent();
    }
    public void trackPlayers(S3kCompetitionPlayer player1,S3kCompetitionPlayer player2) {
        trackPlayers(new Tracking(player1),player2==null?null:new Tracking(player2),TrackingOptions.NORMAL);
    }
    /** Event/deformation pass for a host that already supplies native camera words. */
    public void advance(CameraPair cameras) {
        requireInitialized();
        views[0].cameraX=(short)cameras.player1().x();views[0].writeCameraYWord(cameras.player1().y());
        views[1].cameraX=(short)cameras.player2().x();views[1].writeCameraYWord(cameras.player2().y());
        screenEvent();backgroundEvent();
    }
    private void trackX(ViewRuntime v,S3kCompetitionPlayer player,boolean teleportActive) {
        if(teleportActive) return;
        int trackedX=player.centreX();
        if(v.cameraLagWord!=0) {
            v.cameraLagWord=(v.cameraLagWord-0x100)&0xFFFF;
            int back=(((v.cameraLagWord>>>8)<<2)+4)&255;
            trackedX=v.historyX[((v.historyIndex-back)&255)>>>2]&0x7FFF;
        }
        int distance=(trackedX-v.cameraX)&profile.xWrapMask();
        int delta=distance-0x90;
        if(delta>=0) {delta-=0x10;if(delta<0) return;delta=Math.min(0x10,delta);}
        else delta=Math.max(-0x10,delta);
        v.horizontalDelta=(short)(delta<<8);
        v.cameraX=(short)(((v.cameraX+delta)&profile.xWrapMask())+0x400);
    }
    private void trackY(ViewRuntime v,Tracking input,boolean fastVerticalScroll) {
        S3kCompetitionPlayer player=input.player();
        int error=(short)(player.centreY()-v.cameraY());
        // Native MoveCameraY tests P1 minimum Y for both slots (s3.asm:31730).
        if(profile.bounds().minimumY()==-0x100) error&=profile.yWrapMask();
        if(player.rolling()) error=(short)(error-5);
        int target=(input.distanceFromTop()&0xFFFF)>>>1;
        boolean fast=false;
        if(player.airborne()) {
            int positioned=(error+0x20)&0xFFFF;
            boolean below=positioned<target;
            int adjusted=(positioned-target)&0xFFFF;
            if(below) {error=(short)adjusted;fast=true;}
            else if(adjusted>=0x40) {error=(short)(adjusted-0x40);fast=true;}
            else {
                if(!maximumYChanging) return;
                maximumYChanging=false;error=0;
            }
        } else {
            error=(short)(error-target);
            if(error==0) {
                if(!maximumYChanging) return;
                maximumYChanging=false;
            }
        }
        int threshold,speed;
        if(fast || input.distanceFromTop()==0x60 && (fastVerticalScroll || Math.abs(player.groundVelocity())>=0x800)) {
            threshold=0x18;speed=0x1800;
        } else if(input.distanceFromTop()==0x60) {threshold=6;speed=0x600;}
        else {threshold=2;speed=0x200;}
        int oldFixed=v.cameraYFixed;
        int candidate=error>threshold?oldFixed+(speed<<8):error< -threshold?oldFixed-(speed<<8)
                :((short)(v.cameraY()+error))<<16;
        int y=(short)(candidate>>16);
        if(error<0) {
            if(y<=profile.bounds().minimumY()) {
                y=y<=-0x100?y&profile.yWrapMask():profile.bounds().minimumY();
                candidate=(y<<16)|(candidate&0xFFFF);
            }
        } else if(y>=profile.bounds().maximumY()) {
            int span=(profile.yWrapMask()+1)&0xFFFF;
            if((y&0xFFFF)>=span) {
                y=(short)(y-span);
                oldFixed=(((short)((oldFixed>>16)-span))<<16)|(oldFixed&0xFFFF);
            } else y=profile.bounds().maximumY();
            candidate=(y<<16)|(candidate&0xFFFF);
        }
        v.verticalDelta=(short)Integer.rotateRight(candidate-oldFixed,8);
        v.cameraYFixed=((y&profile.yWrapMask())<<16)|(candidate&0xFFFF);
    }

    /** Sonic_RecordPosCompetition, once after each corresponding actor pass. */
    public void recordPosition(S3kCompetitionPlayer player) {
        requireInitialized();ViewRuntime v=views[player.slot()];
        v.historyX[v.historyIndex>>>2]=player.centreX()&0xFFFF;v.historyIndex=(v.historyIndex+4)&255;
    }
    public void recordPositions(S3kCompetitionPlayer player1,S3kCompetitionPlayer player2) {
        recordPosition(player1);if(player2!=null) recordPosition(player2);
    }
    public void resetPositionHistory(int slot,int centreX) {
        ViewRuntime v=views[slot];Arrays.fill(v.historyX,centreX&0xFFFF);v.historyIndex=0;
    }
    /** The exact native word written at a successful spindash release. */
    public void requestSpinDashLag(int slot,int releasedGroundVelocity) {
        int speed=Math.abs((short)releasedGroundVelocity);
        views[slot].cameraLagWord=(0x2000-(((speed-0x800)*2)&0x1F00))&0xFFFF;
    }
    public void setCameraLagWord(int slot,int word) { views[slot].cameraLagWord=word&0xFFFF; }

    private void screenEvent() {
        for(ViewRuntime v:views) {
            v.unwrappedX=adjustDuringLoop(v.cameraX,v.previousX,v.unwrappedX,profile.xWrapMask());
            v.previousX=v.cameraX;
            if(arena==Arena.CHROME_GADGET) {
                v.unwrappedY=adjustDuringLoop(v.cameraY(),v.previousY,v.unwrappedY,profile.yWrapMask());
                v.previousY=v.cameraY();
            }
        }
    }
    private static int adjustDuringLoop(int current,int previous,int accumulated,int mask) {
        int difference=(short)(current-previous),span=(mask+1)&0xFFFF,half=span>>>1;
        if(difference<0) {
            int magnitude=(-difference)&0xFFFF;
            if(magnitude>=half) magnitude=(magnitude-span)&0xFFFF;
            return (short)(accumulated-magnitude);
        }
        if(difference>=half) difference=(difference-span)&0xFFFF;
        return (short)(accumulated+difference);
    }
    private void backgroundEvent() {
        if(arena==Arena.AZURE_LAKE) {waveCounter=(waveCounter+3)&0xFFFF;cloudDrift+=0x1000;}
        for(int slot=0;slot<2;slot++) {
            ViewRuntime v=views[slot];int fixed=((short)v.unwrappedX)<<16;
            switch(arena) {
                case AZURE_LAKE -> {
                    v.backgroundY=threeQuarterY(v.cameraY());
                    fixed>>=1;v.table[72]=(short)(fixed>>16);v.table[73]=(short)fixed;fixed>>=3;
                    int step=fixed>>2,acc=step;int[] first=new int[6];
                    for(int i=5;i>=0;i--) {acc+=cloudDrift;first[i]=(short)(acc>>16);acc+=step;}
                    int[] permutation={1,5,0,3,2,4};
                    for(int i=0;i<6;i++) v.table[i]=first[permutation[i]];
                    acc=fixed;step=fixed>>1;
                    for(int i=6;i<9;i++) {v.table[i]=(short)(acc>>16);acc+=step;}
                    int deltaIndex=((waveCounter>>>3)&0x3E)>>>1;
                    for(int i=0;i<63;i++) v.table[9+i]=(short)((fixed>>16)+alzDelta[deltaIndex+i]);
                }
                case BALLOON_PARK -> {
                    v.backgroundY=(short)((((short)(v.cameraY()-0x2C8))>>1)+0x90);
                    fixed>>=1;int step=fixed>>3;
                    for(int i=6;i>=0;i--) {v.table[i]=(short)(fixed>>16);fixed-=step;}
                }
                case DESERT_PALACE -> {
                    v.backgroundY=(short)((((short)(v.cameraY()-0x148))>>4)+8+(slot==1?0x80:0));
                    v.backgroundX=(short)(((short)v.unwrappedX)>>1);
                    v.backgroundAuxX=(short)(v.backgroundX>>2);
                }
                case CHROME_GADGET -> {
                    v.backgroundY=(short)(v.unwrappedY<0?0:((v.unwrappedY&0xFFFF)*cgzVerticalRatio)>>>16);
                    fixed>>=1;int step=fixed>>2;
                    for(int i=4;i>=1;i--) {v.table[i]=(short)(fixed>>16);fixed-=step;}
                    v.table[0]=(short)(((step>>2)>>16)+0x100);
                }
                case ENDLESS_MINE -> {
                    v.backgroundY=threeQuarterY(v.cameraY());
                    fixed>>=3;int step=fixed;
                    v.table[7]=(short)(fixed>>16);fixed+=step;
                    v.table[6]=v.table[8]=(short)(fixed>>16);fixed+=step;
                    v.table[3]=v.table[5]=v.table[9]=(short)(fixed>>16);fixed+=step;
                    v.table[0]=v.table[2]=v.table[4]=(short)(fixed>>16);fixed+=step;
                    v.table[1]=(short)(fixed>>16);
                }
            }
        }
        rebuildRaster();
    }
    private static int threeQuarterY(int y) {
        int fixed=((short)(y-0x148))<<16;
        return (short)(((fixed-(fixed>>2))>>16)+0x48);
    }
    private void rebuildRaster() {
        for(int slot=0;slot<2;slot++) {
            ViewRuntime v=views[slot];int start=slot==0?0:PLAYER_1_RASTER_LINES;
            int count=slot==0?PLAYER_1_RASTER_LINES:PLAYER_2_RASTER_LINES;
            if(arena==Arena.DESERT_PALACE) Arrays.fill(raster,start,start+count,packScroll(v.cameraX,v.backgroundX));
            else applyDeformation(v,slot==0?v.backgroundY:(short)(v.backgroundY-4),start,count);
        }
    }
    /** ApplyDeformation2: height bit15 consumes one intermediate word per raster. */
    private void applyDeformation(ViewRuntime v,int backgroundY,int start,int count) {
        int offset=(short)backgroundY,band=0,tableIndex=0;
        while(true) {
            if(band>=heights.length) throw new IllegalStateException("Native competition deformation exhausted its ROM heights");
            int height=heights[band]&0x7FFF;
            boolean perLine=(heights[band]&0x8000)!=0;
            offset=(short)(offset-height);
            if(offset<0) {
                if(perLine) tableIndex+=height+offset;
                int amount=-offset,remaining=count,written=0;
                while(remaining>0) {
                    int take=Math.min(amount,remaining);
                    for(int i=0;i<take;i++) {
                        if(tableIndex<0 || tableIndex>=v.table.length) throw new IllegalStateException("Native competition HScroll_table overflow");
                        raster[start+written++]=packScroll(v.cameraX,v.table[tableIndex]);
                        if(perLine) tableIndex++;
                    }
                    if(!perLine) tableIndex++;
                    remaining-=take;
                    if(remaining==0) return;
                    band++;
                    if(band>=heights.length) throw new IllegalStateException("Native competition deformation has incomplete raster coverage");
                    amount=heights[band]&0x7FFF;perLine=(heights[band]&0x8000)!=0;
                }
            }
            tableIndex+=perLine?height:1;band++;
        }
    }
    private static int packScroll(int foreground,int background) {
        return ((-foreground&0xFFFF)<<16)|(-background&0xFFFF);
    }
    public View view(int slot) {
        requireInitialized();ViewRuntime v=views[slot];int start=slot==0?0:PLAYER_1_RASTER_LINES;
        int count=slot==0?PLAYER_1_RASTER_LINES:PLAYER_2_RASTER_LINES;
        return new View(slot,new CameraPosition(v.cameraX,v.cameraY()),v.cameraYFixed&0xFFFF,
                v.horizontalDelta,v.verticalDelta,v.backgroundX,v.backgroundY,
                (short)(v.cameraY()-(slot==1?0x70:0)),(short)(v.backgroundY-(slot==1?0x70:0)),
                start,integers(Arrays.copyOfRange(raster,start,start+count)),integers(v.table),
                v.unwrappedX,v.unwrappedY,v.cameraLagWord);
    }
    public int[] horizontalScrollBuffer() { requireInitialized();return raster.clone(); }
    private static List<Integer> integers(int[] words) { return Arrays.stream(words).boxed().toList(); }
    private static void requireSlot(S3kCompetitionPlayer player,int slot) {
        if(player.slot()!=slot) throw new IllegalArgumentException("Competition camera participant is in the wrong native slot");
    }
    private void requireInitialized() { if(!initialized) throw new IllegalStateException("Competition cameras are not initialized"); }
    @Override public String key() { return "sonic3k.competition.scroll"; }
    @Override public State capture() {
        List<ViewState> captured=new ArrayList<>(2);
        for(ViewRuntime v:views) captured.add(new ViewState(v.cameraX,v.cameraYFixed,v.horizontalDelta,v.verticalDelta,
                v.previousX,v.unwrappedX,v.previousY,v.unwrappedY,v.backgroundX,v.backgroundY,v.backgroundAuxX,
                v.historyIndex,v.cameraLagWord,integers(v.historyX),integers(v.table)));
        return new State(arena,act,totalLaps,initialized,waveCounter,cloudDrift,cgzVerticalRatio,maximumYChanging,captured);
    }
    @Override public void restore(State state) {
        if(state.arena()!=arena || state.act()!=act || state.totalLaps()!=totalLaps || state.views().size()!=2)
            throw new IllegalArgumentException("Competition scroll snapshot belongs to another arena/profile");
        initialized=state.initialized();waveCounter=state.waveCounter();cloudDrift=state.cloudDrift();
        cgzVerticalRatio=state.cgzVerticalRatio();maximumYChanging=state.maximumYChanging();
        for(int slot=0;slot<2;slot++) {
            ViewState s=state.views().get(slot);ViewRuntime v=views[slot];
            if(s.historyX().size()!=64 || s.table().size()!=128) throw new IllegalArgumentException("Incorrect native camera snapshot shape");
            v.cameraX=s.cameraX();v.cameraYFixed=s.cameraYFixed();v.horizontalDelta=s.horizontalDelta();v.verticalDelta=s.verticalDelta();
            v.previousX=s.previousX();v.unwrappedX=s.unwrappedX();v.previousY=s.previousY();v.unwrappedY=s.unwrappedY();
            v.backgroundX=s.backgroundX();v.backgroundY=s.backgroundY();v.backgroundAuxX=s.backgroundAuxX();
            v.historyIndex=s.historyIndex();v.cameraLagWord=s.cameraLagWord();
            for(int i=0;i<64;i++) v.historyX[i]=s.historyX().get(i);
            for(int i=0;i<128;i++) v.table[i]=s.table().get(i);
        }
        if(initialized) rebuildRaster();else Arrays.fill(raster,0);
    }
}
