package com.openggf.game.sonic3k.competition;

import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.sonic3k.competition.objects.CompetitionArenaObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionGoalObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionHudObjectInstance;
import com.openggf.game.sonic3k.competition.objects.CompetitionItemObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.NativeSpriteSubmissionAccess;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Render_Sprites_CompetitionMode's retained input-pointer RAM and two physical SAT pages.
 * The owner performs no GL work and never advances gameplay during drawing. Both normal
 * and lag publication consume immutable, already completed pages.
 * Owning routines: Init_SpriteTable_2Player (sonic3k.asm:35992-35998),
 * Render_Sprites_CompetitionMode/loc_1B2E4 (36934-37139), sub_1AF6C/sub_1B070
 * (36598-36930). FixBugs=0 retains the unconditional stale-word pair comparison.
 */
public final class S3kCompetitionSpritePages implements RewindSnapshottable<S3kCompetitionSpritePages.State> {
    public static final String REWIND_KEY="sonic3k.competition.sprite-pages";
    /** A resolved whole SAT footprint. Coordinates have the VDP $80 bias removed. */
    public record Piece(int nativeSlot,int bucket,String artKey,SpriteMappingPiece mapping,
                        int screenX,int screenY,boolean maskPrefix) {
        public Piece { Objects.requireNonNull(mapping); }
    }
    /** Includes the physical P1 mask prefix. Entries retain native SAT order. */
    public record Frame(int slot,List<Piece> pieces) {
        public Frame { if(slot<0||slot>1)throw new IllegalArgumentException("Invalid native view");pieces=List.copyOf(pieces); }
    }
    public record Pair(Frame player1,Frame player2) {
        public Pair { Objects.requireNonNull(player1);Objects.requireNonNull(player2); }
    }
    public record State(int currentPageWord,boolean flipPending,Pair uploaded,List<Pair> physicalPages,
                        List<List<Integer>> inputPointers) {
        public State {
            physicalPages=List.copyOf(physicalPages);
            inputPointers=inputPointers.stream().map(List::copyOf).toList();
        }
    }
    private record Mapping(String key,int frame,int onlyPiece,int x,int y,boolean hFlip,boolean vFlip,
                           Boolean forcedPriority,boolean multi) { }
    private record Source(int x,int y,int halfWidth,int halfHeight,int flags,List<Mapping> mappings,
                          Consumer<Boolean> visibility) { }
    private final Function<String,ObjectSpriteSheet> sheets;
    // Native Sprite_table_input count bytes are drained after building; pointer words are not cleared.
    private final int[][] inputPointers=new int[8][63];
    private final Pair[] pages={emptyPair(),emptyPair()};
    private Pair uploaded=pages[1];
    private int currentPageWord;
    private boolean flipPending;

    public S3kCompetitionSpritePages(S3kCompetitionArtProvider art) { this(Objects.requireNonNull(art)::getSheet); }
    /** Injected immutable ROM sheets; useful to exercise native admission without a GL context. */
    public S3kCompetitionSpritePages(Function<String,ObjectSpriteSheet> sheets) {
        this.sheets=Objects.requireNonNull(sheets);
        for(int[] bucket:inputPointers)Arrays.fill(bucket,-1); // Cleared native pointer $0000 is not Player_1.
    }
    private static Pair emptyPair() { return new Pair(new Frame(0,prefix()),new Frame(1,List.of())); }
    private static List<Piece> prefix() {
        var piece=new SpriteMappingPiece(0,0,1,4,0,false,false,0,false);
        return List.of(new Piece(-1,0,null,piece,-127,107,true),new Piece(-1,0,null,piece,-128,107,true));
    }
    @Override public String key() { return REWIND_KEY; }

    /** Exactly one native BuildSprites call, after the canonical SST pass and camera event boundary. */
    public Pair prepare(ObjectManager objects,List<S3kCompetitionPlayer> players,S3kCompetitionScroll scroll) {
        Objects.requireNonNull(objects);Objects.requireNonNull(players);Objects.requireNonNull(scroll);
        Map<Integer,Source> owners=currentSources(objects,players);
        int[] count=new int[8];
        for(var admission:NativeSpriteSubmissionAccess.admissions(objects)) {
            int bucket=admission.bucket();
            if(count[bucket]>=63)throw new IllegalStateException("Native input bucket exceeded 63 pointers");
            inputPointers[bucket][count[bucket]++]=admission.slot();
        }
        Frame first=build(0,count,owners,scroll);
        // loc_1B2E4 deliberately has NO count guard: untouched pointer words participate.
        if(inputPointers[2][0]==0 && inputPointers[2][1]==1) {
            inputPointers[2][0]=1;inputPointers[2][1]=0;
        }
        Frame second=build(1,count,owners,scroll);
        Pair pair=new Pair(first,second);
        pages[currentPageWord==0?0:1]=pair;
        flipPending=true;
        return pair;
    }

    private Frame build(int slot,int[] count,Map<Integer,Source> owners,S3kCompetitionScroll scroll) {
        var output=new ArrayList<Piece>(80);
        if(slot==0)output.addAll(prefix());
        var camera=scroll.view(slot).camera();
        int xMask=scroll.profile().xWrapMask(),yMask=scroll.profile().yWrapMask();
        for(int bucket=0;bucket<8;bucket++)for(int i=0;i<count[bucket];i++) {
            int pointer=inputPointers[bucket][i];
            Source source=owners.get(pointer);
            if(source==null)continue;
            // P1 clears before even testing its view-skip flag; P2 only ORs successful range.
            if(slot==0)source.visibility().accept(false);
            int skip=slot==0?0x10:8;
            if((source.flags()&skip)!=0)continue;
            boolean world=(source.flags()&0x44)!=0;
            int originX=source.x(),originY=source.y()+slot*112;
            if(world) {
                int qx=(source.x()-camera.x()+(source.halfWidth()&255))&xMask;
                int qy=(source.y()-camera.y()+(source.halfHeight()&255))&yMask;
                if(qx>=320+2*(source.halfWidth()&255)||qy>=112+2*(source.halfHeight()&255))continue;
                originX=qx+128-(source.halfWidth()&255);
                originY=qy+128-(source.halfHeight()&255)+slot*112;
            }
            source.visibility().accept(true); // BEFORE full SAT or empty frame tests.
            if(output.size()>=80)continue;
            for(Mapping mapping:source.mappings()) {
                int mx=originX+mapping.x(),my=originY+mapping.y();
                if(mapping.multi()) {
                    mx=(mapping.x()-camera.x()+128)&xMask;
                    my=((mapping.y()-camera.y()+128)&yMask)+slot*112;
                }
                emit(output,pointer,bucket,mapping,mx,my);
                if(output.size()>=80)break;
            }
        }
        return new Frame(slot,output);
    }

    private void emit(List<Piece> output,int pointer,int bucket,Mapping mapping,int originX,int originY) {
        ObjectSpriteSheet sheet=sheets.apply(mapping.key());
        if(sheet==null)throw new IllegalStateException("Missing ROM competition mapping "+mapping.key());
        if(mapping.frame()<0||mapping.frame()>=sheet.getFrameCount())
            throw new IllegalStateException("Native competition mapping left its ROM bank: "+mapping.key()+"/"+mapping.frame());
        List<SpriteMappingPiece> pieces=sheet.getFrame(mapping.frame()).pieces();
        for(int i=0;i<pieces.size()&&output.size()<80;i++) {
            if(mapping.onlyPiece()>=0&&mapping.onlyPiece()!=i)continue;
            SpriteMappingPiece piece=pieces.get(i);
            int dx=mapping.hFlip()?-piece.xOffset()-piece.widthTiles()*8:piece.xOffset();
            int dy=mapping.vFlip()?-piece.yOffset()-piece.heightTiles()*8:piece.yOffset();
            int x=(originX+dx)&0xFFFF,y=(originY+dy)&0xFFFF;
            if(mapping.multi()) {
                // sub_1B070 clips physical anchors, with strict unsigned comparisons.
                if(x<=96||x>=448||y<=96||y>=352)continue;
            } else {
                x&=0x1FF;if(x==0)x=1;
            }
            var resolved=new SpriteMappingPiece(0,0,piece.widthTiles(),piece.heightTiles(),piece.tileIndex(),
                    piece.hFlip()^mapping.hFlip(),piece.vFlip()^mapping.vFlip(),piece.paletteIndex(),
                    mapping.forcedPriority()==null?piece.priority():mapping.forcedPriority());
            output.add(new Piece(pointer,bucket,mapping.key(),resolved,x-128,(y&0x3FF)-128,false));
        }
    }

    private static Mapping mapping(String key,int frame,boolean h,boolean v,Boolean priority) {
        return new Mapping(key,frame,-1,0,0,h,v,priority,false);
    }
    private Map<Integer,Source> currentSources(ObjectManager objects,List<S3kCompetitionPlayer> players) {
        var sources=new HashMap<Integer,Source>();
        for(var player:players) {
            String key=switch(player.character()) {
                case SONIC->"competition_sonic";case TAILS->"competition_tails";case KNUCKLES->"competition_knuckles";
            };
            int flags=player.nativeRenderFlags();
            sources.put(player.slot(),new Source(player.centreX(),player.centreY(),12,12,flags,
                    List.of(mapping(key,player.mappingFrame(),(flags&1)!=0,(flags&2)!=0,player.highPriority())),
                    player::refreshNativeVisibility));
        }
        // Presentation-only late lookup. Gameplay touch uses the canonical previous pointer list.
        for(var object:objects.getActiveObjects()) {
            if(!(object instanceof AbstractObjectInstance current)||current.isDestroyed())continue;
            int pointer=current.getSlotIndex();
            if((current.getLiveObjectId()&255)==0) { sources.remove(pointer);continue; }
            Source source=source(current);
            if(source!=null)sources.put(pointer,source);
        }
        return sources;
    }
    private static Source source(AbstractObjectInstance object) {
        List<Mapping> mappings=new ArrayList<>();
        int flags=4;
        Consumer<Boolean> visibility=ignored->{ };
        if(object instanceof CompetitionArenaObjectInstance arena) {
            if(arena.artKey()!=null) {
                if(arena.kind()==CompetitionArenaObjectInstance.Kind.ELEPHANT) {
                    flags|=0x40;
                    mappings.add(new Mapping(arena.artKey(),arena.mappingFrame(),-1,
                            arena.nativeRenderX(),arena.nativeRenderY(),arena.nativeRenderXFlip(),
                            arena.nativeRenderYFlip(),null,true));
                } else mappings.add(mapping(arena.artKey(),arena.mappingFrame(),arena.nativeRenderXFlip(),
                        arena.nativeRenderYFlip(),arena.isHighPriority()?Boolean.TRUE:null));
            }
            visibility=arena::refreshNativeVisibility;
        } else if(object instanceof CompetitionItemObjectInstance item) {
            mappings.add(mapping(CompetitionItemObjectInstance.ART_KEY,item.mappingFrame(),false,false,
                    item.isHighPriority()?Boolean.TRUE:null));
        } else if(object instanceof CompetitionGoalObjectInstance goal) {
            mappings.add(mapping(CompetitionGoalObjectInstance.ART_KEY,goal.mappingFrame(),false,false,null));
        } else if(object instanceof CompetitionHudObjectInstance hud) {
            flags=hud.viewSlot()==0?8:hud.viewSlot()==1?0x10:0;
            for(var draw:hud.nativeDrawPieces())mappings.add(new Mapping(draw.artKey(),draw.mappingFrame(),draw.pieceIndex(),
                    draw.screenX()-(hud.nativeScreenX()-128),draw.screenY()-(hud.nativeScreenY()-128),false,false,null,false));
        } else if(object instanceof S3kMiniatureTail tail) {
            flags=tail.nativeRenderFlags();visibility=tail::refreshNativeVisibility;
            mappings.add(mapping(S3kMiniatureTail.ART_KEY,tail.mappingFrame(),(flags&1)!=0,(flags&2)!=0,tail.isHighPriority()));
        } else if(object instanceof S3kMiniatureDust dust) {
            flags=dust.nativeRenderFlags();visibility=dust::refreshNativeVisibility;
            mappings.add(mapping(S3kMiniatureDust.ART_KEY,dust.mappingFrame(),(flags&1)!=0,(flags&2)!=0,dust.isHighPriority()));
        } else if(object instanceof S3kCompetitionRingLoss ring) {
            flags=ring.nativeRenderFlags();visibility=ring::refreshNativeVisibility;
            mappings.add(mapping(S3kCompetitionRingLoss.ART_KEY,ring.mappingFrame(),(flags&1)!=0,(flags&2)!=0,null));
        } else return null;
        return new Source(object.getX(),object.getY(),object.getOnScreenHalfWidth(),object.getOnScreenHalfHeight(),flags,
                List.copyOf(mappings),visibility);
    }

    /** VInt8/10/A/C flip completed pages; lag retains front; palette fade uploads physical page zero. */
    public Pair publish(PlcLifecyclePhase phase) {
        boolean flip=phase==PlcLifecyclePhase.ORDINARY_LEVEL||phase==PlcLifecyclePhase.NORMAL_PAUSE
                ||phase==PlcLifecyclePhase.LEVEL_TITLE_CARD;
        if(flip&&flipPending) { currentPageWord^=0xFFFF;flipPending=false; }
        uploaded=phase==PlcLifecyclePhase.PALETTE_FADE?pages[0]:front();
        return uploaded;
    }
    public Pair front() { return pages[currentPageWord==0?1:0]; }
    /** The SAT bytes most recently DMA-published, including the fixed-page fade variant. */
    public Pair uploaded() { return uploaded; }
    public int currentPageWord() { return currentPageWord; }
    public boolean flipPending() { return flipPending; }
    public int inputPointer(int bucket,int word) { return inputPointers[bucket][word]; }
    @Override public State capture() {
        return new State(currentPageWord,flipPending,uploaded,List.of(pages[0],pages[1]),
                Arrays.stream(inputPointers).map(row->Arrays.stream(row).boxed().toList()).toList());
    }
    @Override public void restore(State state) {
        Objects.requireNonNull(state);
        if((state.currentPageWord()!=0&&state.currentPageWord()!=0xFFFF)||state.physicalPages().size()!=2
                ||state.inputPointers().size()!=8||state.inputPointers().stream().anyMatch(row->row.size()!=63))
            throw new IllegalArgumentException("Invalid native competition sprite RAM snapshot");
        currentPageWord=state.currentPageWord();flipPending=state.flipPending();uploaded=Objects.requireNonNull(state.uploaded());
        pages[0]=state.physicalPages().get(0);pages[1]=state.physicalPages().get(1);
        for(int b=0;b<8;b++)for(int i=0;i<63;i++)inputPointers[b][i]=state.inputPointers().get(b).get(i);
    }
}
