package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR31aAssets;
import static com.openggf.game.soniccd.level.CdR31aAssets.Asset.*;
import com.openggf.game.soniccd.objects.CdObjectPool;
import com.openggf.game.soniccd.objects.CdObjectState;
import java.util.*;

/**
 * Finite R31 PlayerCheckBlock semantic component. N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/r3/player_block_check.asm3-550 (blob7c1c46e1a00262661ed1a7f5c7aa103854ecd80b).
 * Main calls after MODE; Hurt calls after movement/gravity and before recovery
 * (src/objects/player.asm306-312,2206-2218). Not a Body/Frame/World/FULL admission API.
 *
 * Source data is supplied explicitly, never disassembly/runtime fallback. Component actor/NoBumper
 * stores and actual passed pool writes happen once in native order. Actual GetBlock word reads,
 * PlaceBlockAtPos (shared CHUNK DEFINITION plus conditional visible Plane A), FM and SubCPU are
 * reached semantic ports. A port must perform its original helper then acknowledge its original
 * issued call; a constructed value cannot acknowledge another call. These receipts authenticate
 * only this component stack, not a World, source lease, Frame, native callback or rendering owner.
 * Root must authenticate those owners and prebuild complete external aggregates before applyNative.
 *
 * RuntimeException/Error retains this exact earlier prefix, retires admission and is rethrown
 * unchanged. A failed external helper owns its own partial prefix. No rollback/effect replay is
 * provided. Integrators must fold the component's actual stopped actor/NoBumper prefix before
 * propagating failure; the existing throw-atomic Body cannot silently swallow that responsibility.
 */
public final class CdR31SpecialBlockCollision {
    public enum Site { MAIN_AFTER_MODE, HURT_BEFORE_RECOVERY }
    public enum Boundary { PERIOD_BYTE, GOOD_FUTURE_BYTE, BLOCK_LIST_SELECTOR, BLOCK_LIST_WORD, BUMPER_SHAPE_BYTE, SINE_WORD, ATAN_BYTE, BLOCK_WORD_READ, ACTOR_STORE_OWNER, SPAWN_OBJECT_POOL, SHARED_CHUNK_MUTATION_OWNER, FM_OWNER, SUB_CPU_OWNER }
    public static final class Unowned extends UnsupportedOperationException {
        private final Boundary boundary; private final int index;
        private Unowned(Boundary boundary,int index){super("R31 special block unowned "+boundary+" at "+index);this.boundary=boundary;this.index=index;}
        public Boundary boundary(){return boundary;} public int index(){return index;}
    }
    /** Complete raw source operands; absent bytes are read only when the native branch reaches them. */
    public record Source(int zoneActWord,OptionalInt rawPeriodByte,OptionalInt goodFutureByte){
        public Source{word(zoneActWord);optionalByte(rawPeriodByte);optionalByte(goodFutureByte);}
    }
    /**
     * Explicit source-only views. Lists are in the ORIGINAL pointer order B,A,D,C; each retains its
     * count WORD followed by source ID WORDs. Shapes are the three actual 16-byte profiles.
     * Partial finite views may be supplied: an absent/reached word stops before the next mutation.
     * This is format data, not authentication or permission to invent a missing original range.
     */
    public record Data(List<Optional<CdAssetBytes>> blockLists,List<Optional<CdAssetBytes>> bumperShapes,
                       Optional<CdAssetBytes> sineWords,Optional<CdAssetBytes> arcTanBytes){
        public Data{
            blockLists=List.copyOf(Objects.requireNonNull(blockLists));bumperShapes=List.copyOf(Objects.requireNonNull(bumperShapes));
            if(blockLists.size()!=4||bumperShapes.size()!=3)throw new IllegalArgumentException("Original four lists/three profiles required");
            for(var b:blockLists){Objects.requireNonNull(b);b.ifPresent(v->{if(v.length()>34)throw new IllegalArgumentException("List exceeds native largest counted view");});}
            for(var b:bumperShapes){Objects.requireNonNull(b);b.ifPresent(v->{if(v.length()>16)throw new IllegalArgumentException("Bumper profile exceeds16 bytes");});}
            Objects.requireNonNull(sineWords);Objects.requireNonNull(arcTanBytes);
            sineWords.ifPresent(v->{if(v.length()>640)throw new IllegalArgumentException("Sine view exceeds640");});
            arcTanBytes.ifPresent(v->{if(v.length()>258)throw new IllegalArgumentException("Atan raw view exceeds258 including actual EVEN tail");});
        }
        public static Data unowned(){return new Data(Collections.nCopies(4,Optional.empty()),Collections.nCopies(3,Optional.empty()),Optional.empty(),Optional.empty());}
        /**
         * Finite bridge from the existing final/private-constructed original-USA R31A data owner.
         * CdR31aAssets authenticates the complete release/image and every immutable source span
         * before construction; an Identity, Range or caller byte array is not an accepted owner.
         * Check all three original directories against their fixed selected metadata, then retain
         * only nine immutable byte views. This returns format DATA, never Source/World authority.
         * No owner reference, callback, missing-data default or runtime admission is retained.
         */
        public static Data fromAssets(CdR31aAssets assets){
            Objects.requireNonNull(assets,"authenticated original-USA R31A data owner");
            var blockRoot=fixedView(assets,PLAYER_BLOCKS,0x6858,8);
            var blockIds=List.of(PLAYER_BLOCKS_B,PLAYER_BLOCKS_A,PLAYER_BLOCKS_D,PLAYER_BLOCKS_C);
            int[] blockOffsets={0x6882,0x6860,0x68AA,0x6888},blockLengths={6,34,34,34},counts={1,15,15,15};
            var lists=new ArrayList<Optional<CdAssetBytes>>();
            for(int i=0;i<4;i++){
                var raw=fixedView(assets,blockIds.get(i),blockOffsets[i],blockLengths[i]);
                pointsTo(assets,PLAYER_BLOCKS,blockRoot,i,blockIds.get(i));
                int count=raw.unsignedWord(0);
                if(count!=counts[i]||raw.length()!=2+2*(count+1))throw new IllegalArgumentException("Original R31 counted list differs");
                lists.add(Optional.of(raw));
            }
            var routines=fixedView(assets,PLAYER_BLOCK_ROUTINES,0x6922,32);
            int[] selectors={0x20,0x20,0xD0,0xD0,0x266,0x122,0x134,0x134,0x242,0x19A,0x19A,0x19A,0x17E,0x122,0x122,0x146};
            for(int i=0;i<16;i++)if(routines.unsignedWord(i*2)!=selectors[i])throw new IllegalArgumentException("Original R31 selector directory differs");
            var bumperRoot=fixedView(assets,PLAYER_BUMPER_COLLIDE,0x6B2E,6);
            var bumperIds=List.of(PLAYER_BUMPER_COLLIDE_1,PLAYER_BUMPER_COLLIDE_2,PLAYER_BUMPER_COLLIDE_3);
            var shapes=new ArrayList<Optional<CdAssetBytes>>();
            for(int i=0;i<3;i++){
                var raw=fixedView(assets,bumperIds.get(i),0x6B34+16*i,16);
                pointsTo(assets,PLAYER_BUMPER_COLLIDE,bumperRoot,i,bumperIds.get(i));
                shapes.add(Optional.of(raw));
            }
            return new Data(lists,shapes,Optional.of(fixedView(assets,SINE_TABLE,0x79A,640)),
                    Optional.of(fixedView(assets,ARC_TAN_TABLE,0xA80,258)));
        }
        private static CdAssetBytes fixedView(CdR31aAssets assets,CdR31aAssets.Asset id,int offset,int length){
            var range=assets.range(id);var raw=assets.view(id);
            if(range.asset()!=id||range.fileOffset()!=offset||range.nativeAddress()!=0x200000+offset
                    ||range.endExclusive()!=offset+length||range.length()!=length||raw.length()!=length)
                throw new IllegalArgumentException("Original R31 fixed source metadata differs: "+id);
            return raw;
        }
        private static void pointsTo(CdR31aAssets assets,CdR31aAssets.Asset root,CdAssetBytes directory,int slot,CdR31aAssets.Asset selected){
            if(assets.range(root).nativeAddress()+(short)directory.unsignedWord(slot*2)!=assets.range(selected).nativeAddress())
                throw new IllegalArgumentException("Original R31 directory target differs: "+selected);
        }

    }
    /** One complete immutable primary image and distinct r3_no_bumper BYTE; no offset/RAM API. */
    public record State(CdPlayerBody.State actor,OptionalInt noBumperByte){public State{Objects.requireNonNull(actor);optionalByte(noBumperByte);}}
    public record Result(State state,boolean contacted,List<Call> calls){public Result{Objects.requireNonNull(state);calls=List.copyOf(calls);}}
    /** Observational source-order audit only; never executable effects or helper completions. */
    public enum Call { ATAN, SINE_COSINE, READ_BLOCK, PERIOD, GOOD_FUTURE, LIST_COUNT, LIST_WORD, STORE,
        BUMPER_SHAPE, SPAWN_OBJECT, CHILD_ID, CHILD_X, CHILD_Y, PLACE_BLOCK, FM, SUB_CPU }
    public enum Field { ROUTINE, HURT_TIMER, NO_BUMPER, X_SPEED, Y_SPEED, FLAGS, PLAYER_JUMP }
    public enum Form { ASSIGN, SET_BIT, CLEAR_BIT }
    public interface Ports {
        /** Actual GetBlock followed by MOVE.W (a1), including an original first chunk WORD on blank pointer.
         * No invented zero for a blank/unavailable geometry product. Return an unsigned native WORD. */
        default int readBlock(BlockRead call){throw new Unowned(Boundary.BLOCK_WORD_READ,call.ordinal());}
        /** Prebuild actual external Frame/Host full pair, call applyNative once, then adopt that pair. */
        default void store(Store call){throw new Unowned(Boundary.ACTOR_STORE_OWNER,call.field().ordinal());}
        /** Actual PlaceBlockAtPos, including chunk-definition write before its on-screen draw decision. */
        default void placeBlock(PlaceBlock call){throw new Unowned(Boundary.SHARED_CHUNK_MUTATION_OWNER,call.ordinal());}
        default void fm(Fm call){throw new Unowned(Boundary.FM_OWNER,call.ordinal());}
        default void subCpu(SubCpu call){throw new Unowned(Boundary.SUB_CPU_OWNER,call.ordinal());}
    }
    public abstract class Operation {
        private final Object scope;private final Thread thread;private final int ordinal;private boolean consumed;
        private Operation(){scope=activeScope;thread=Thread.currentThread();ordinal=++operationOrdinal;}
        public final int ordinal(){return ordinal;}
        public final void requireOwner(CdR31SpecialBlockCollision expected){current();if(expected!=CdR31SpecialBlockCollision.this)throw new IllegalArgumentException("Another R31 component owner");}
        public final Site site(){return activeSiteFor(this);}
        public final Source source(){return sourceFor(this);}
        final void current(){
            if(!running||failed||!inPort||pending!=this||scope!=activeScope||thread!=Thread.currentThread())
                throw new IllegalStateException("Expired/foreign original R31 component call");
        }
        final void consume(){current();if(consumed)throw new IllegalStateException("Original R31 call already consumed");consumed=true;}
    }
    public final class BlockRead extends Operation {
        private final int x,y,spriteFlags;private final State before;
        private BlockRead(int x,int y){this.x=x;this.y=y;spriteFlags=state.actor().motion().spriteFlags();before=state;}
        public int xWord(){return x;}public int yWord(){return y;}public int spriteFlagsByte(){return spriteFlags;}public State before(){return before;}
    }
    public final class Store extends Operation {
        private final Field field;private final Form form;private final OptionalInt expected;private final int operand,result;private final State before,after;
        private Store(Field field,Form form,OptionalInt expected,int operand,int result,State after){this.field=field;this.form=form;this.expected=expected;this.operand=operand;this.result=result;before=state;this.after=after;}
        public Field field(){return field;}public Form form(){return form;}public OptionalInt expectedRaw(){return expected;}
        public int operand(){return operand;}public int resultRaw(){return result;}public State before(){return before;}public State after(){return after;}
        public void applyNative(){super.current();if(state!=before)throw new IllegalStateException("Earlier complete component cut changed");super.consume();state=after;audit(Call.STORE);}
    }
    public final class PlaceBlock extends Operation {
        private final int x,y;private PlaceBlock(int x,int y){this.x=x;this.y=y;}
        public int xWord(){return x;}public int yWord(){return y;}public int blockWord(){return 0;}
        public void completedNative(){super.consume();audit(Call.PLACE_BLOCK);}
    }
    public final class Fm extends Operation {
        private final int id;private Fm(int id){this.id=id;}public int nativeId(){return id;}
        public void completedNative(){super.consume();audit(Call.FM);}
    }
    public final class SubCpu extends Operation {
        private final int id;private SubCpu(int id){this.id=id;}public int nativeId(){return id;}
        public void completedNative(){super.consume();audit(Call.SUB_CPU);}
    }
    private final Data data;private final Optional<CdObjectPool> pool;private final Ports ports;
    private State state;private Source source;private Site site;private Object activeScope;private Operation pending;
    private boolean running,inPort,failed;private int operationOrdinal;private final List<Call> calls=new ArrayList<>();
    public CdR31SpecialBlockCollision(Data data,Optional<CdObjectPool> pool,Ports ports){this.data=Objects.requireNonNull(data);this.pool=Objects.requireNonNull(pool);this.ports=Objects.requireNonNull(ports);}
    public boolean failed(){return failed;}
    /** Latest original successful/stopped prefix. A returned value grants no external adoption authority. */
    public State state(){return state;}
    public List<Call> calls(){return List.copyOf(calls);}
    /** Main may already hold routine6 after MODE LevelBound/KillPlayer; the selected Main call still
     * reaches this helper. Site metadata never proves that original stack or a World invocation. */
    public Result run(Site site,Source source,State before){
        if(running||inPort||failed)throw new IllegalStateException("R31 special block admission active or retired");
        Objects.requireNonNull(site);Objects.requireNonNull(source);Objects.requireNonNull(before);
        int routine=before.actor().motion().routine();
        if(source.zoneActWord()!=0x100||(site==Site.HURT_BEFORE_RECOVERY?routine!=4:routine!=2&&routine!=6))
            throw new IllegalArgumentException("R31 raw0100 and Main routine2/6 or Hurt routine4 pre-call cut required");
        this.site=site;this.source=source;state=before;calls.clear();operationOrdinal=0;activeScope=new Object();running=true;
        try{boolean hit=false;if(state.actor().motion().animation()!=0x2B)hit=check();return new Result(state,hit,calls);}
        catch(RuntimeException|Error original){failed=true;throw original;}
        finally{pending=null;inPort=false;running=false;activeScope=null;}
    }
    private Site activeSiteFor(Operation operation){operation.current();return site;}
    private Source sourceFor(Operation operation){operation.current();return source;}
    private void audit(Call call){calls.add(call);}
    /** Call guards are local custody only. Root must also guard actual source/World/Frame/full native cut. */
    private void invoke(Operation call,Runnable action,boolean acknowledgement){
        if(pending!=null||!running)throw new IllegalStateException("R31 helper reentry");
        var beforePool=pool.map(CdObjectPool::capture);pending=call;inPort=true;
        try{action.run();call.current();if(beforePool.isPresent()&&!beforePool.orElseThrow().slots().equals(pool.orElseThrow().capture().slots()))throw new IllegalStateException("R31 semantic port changed unrelated pool bytes");
            if(acknowledgement&&!call.consumed)throw new IllegalStateException("Original R31 helper/store did not complete");}
        finally{pending=null;inPort=false;}
    }
    private boolean check(){
        var m=state.actor().motion();int quadrant=((angle(m.xVelocity(),m.yVelocity())-0x20)&255)&0xC0;
        if(probe(quadrant,false))return true;
        return probe(quadrant,true); // Re-read ACTUAL actor coordinates/radii after a rejected first probe.
    }
    private boolean probe(int quadrant,boolean second){
        var m=state.actor().motion();int x=m.centreX16_16()>>>16,y=m.centreY16_16()>>>16;
        int xr=(byte)m.xRadius(),yr=(byte)m.yRadius();
        if(quadrant==0||quadrant==0x80){x=uw(x+(second?xr:-xr));y=uw(y+(quadrant==0?yr:-yr));}
        else{x=uw(x+(quadrant==0x40?-xr:xr));y=uw(y+(second?yr:-(byte)((m.yRadius()-6)&255)));}
        var query=new BlockRead(x,y);int[] raw={0};audit(Call.READ_BLOCK);invoke(query,()->raw[0]=ports.readBlock(query),false);word(raw[0]);
        int block=raw[0]&0x7FF;if(block==0)return false;
        int period=read(source.rawPeriodByte(),Boundary.PERIOD_BYTE);audit(Call.PERIOD);period&=0x7F;
        if(period==2){int good=read(source.goodFutureByte(),Boundary.GOOD_FUTURE_BYTE);audit(Call.GOOD_FUTURE);period=(period+good)&255;}
        if(period>=4)throw new Unowned(Boundary.BLOCK_LIST_SELECTOR,period);
        var list=data.blockLists().get(period);int count=readWord(list,0,Boundary.BLOCK_LIST_WORD);audit(Call.LIST_COUNT);
        for(int i=0;i<=count;i++){int id=readWord(list,2+2*i,Boundary.BLOCK_LIST_WORD);audit(Call.LIST_WORD);if(id==block)return matched(i,x,y,raw[0]);}
        return false;
    }
    private boolean matched(int index,int x,int y,int blockWord){
        if(state.actor().motion().routine()==4){store(Field.ROUTINE,Form.ASSIGN,2);store(Field.HURT_TIMER,Form.ASSIGN,120);}
        store(Field.NO_BUMPER,Form.ASSIGN,0);
        // off_206930279-295: sixteen code branches, not asset or runtime-state defaults.
        switch(index){
            case 0,1->destructive(index,x,y,blockWord);
            case 2,3->radial(x,y,true);
            case 4->{if(((x&15)<8)^((blockWord&0x800)!=0))radial(x,y,false);else bounceY();}
            case 5,13,14->bounceY();
            case 6,7->bounceX();
            case 8->{if((blockWord&0x800)==0)reflect(index,x,y,blockWord);else if((x&15)<8)bounceX();else bounceY();}
            case 9,10,11->reflect(index,x,y,blockWord);
            case 12->{if(((x&15)<8)^((blockWord&0x800)!=0))bounceY();else bounceX();}
            case 15->radial(x,y,false);
            default->throw new Unowned(Boundary.BLOCK_LIST_WORD,index*2);
        }
        int byteOffset=(index*2)&65535;
        if(byteOffset>=3){var call=new Fm(byteOffset==4||byteOffset==6?0xBE:0xB5);invoke(call,()->ports.fm(call),true);}
        return state.noBumperByte().orElseThrow()==0;
    }
    private void radial(int x,int y,boolean half){
        int targetX=uw((x&0xFFF0)+8),targetY=uw((y&0xFFF0)+8);
        vector(angle(sw(targetX-x()),sw(targetY-y())),half);flags();
    }
    private void vector(int angle,boolean half){
        var sc=sine(angle);int xv=(-0x700*sc[1])>>8,yv=(-0x700*sc[0])>>8;
        if(half){xv>>=1;yv>>=1;}
        store(Field.X_SPEED,Form.ASSIGN,uw(xv));store(Field.Y_SPEED,Form.ASSIGN,uw(yv));
    }
    private void flags(){store(Field.FLAGS,Form.SET_BIT,1);store(Field.FLAGS,Form.CLEAR_BIT,4);store(Field.FLAGS,Form.CLEAR_BIT,5);store(Field.PLAYER_JUMP,Form.ASSIGN,0);}
    private void bounceY(){store(Field.Y_SPEED,Form.ASSIGN,state.actor().motion().yVelocity()<0?0x700:0xF900);flags();}
    private void bounceX(){store(Field.X_SPEED,Form.ASSIGN,state.actor().motion().xVelocity()<0?0x700:0xF900);flags();}
    private void reflect(int index,int x,int y,int blockWord){
        // Routine12 SUBI.W #$12. Routine10 (index8) enters with -2 and skips shape lookup.
        int offset=(short)(index*2-0x12);
        if(offset>=0){int shape=offset/2;int column=x&15;if((blockWord&0x800)==0)column=(-column+15)&255;
            if(shape>=3)throw new Unowned(Boundary.BUMPER_SHAPE_BYTE,shape);
            int height=readByte(data.bumperShapes().get(shape),column,Boundary.BUMPER_SHAPE_BYTE);audit(Call.BUMPER_SHAPE);
            if((y&15)<height){store(Field.NO_BUMPER,Form.ASSIGN,1);return;}}
        int a=angle(state.actor().motion().xVelocity(),state.actor().motion().yVelocity());
        a=(-(a-0x80)-0x20)&255;if((blockWord&0x800)!=0)a=(a+0x40)&255;
        vector(a,false);flags();
    }
    private void destructive(int index,int x,int y,int blockWord){
        int targetY=uw((y&0xFFF0)+(index==0?16:0));int targetX=uw((x&0xFFF0)+((blockWord&0x800)==0?16:0));
        vector(angle(sw(targetX-x()),sw(targetY-y())),false);flags();
        audit(Call.SPAWN_OBJECT);var actualPool=pool.orElseThrow(()->new Unowned(Boundary.SPAWN_OBJECT_POOL,0));var free=actualPool.findObjSlot();
        if(free.isPresent()){
            int whole=free.getAsInt();actualPool.write(actualPool.state(whole).moveByte(CdObjectState.ByteField.ID,0x29));audit(Call.CHILD_ID);
            actualPool.write(actualPool.state(whole).moveWord(CdObjectState.WordField.X,targetX));audit(Call.CHILD_X);
            actualPool.write(actualPool.state(whole).moveWord(CdObjectState.WordField.Y,targetY));audit(Call.CHILD_Y);
        }
        place(targetX,targetY);place(uw(targetX-16),targetY);place(uw(targetX-16),uw(targetY-16));place(targetX,uw(targetY-16));
        var call=new SubCpu(0x92);invoke(call,()->ports.subCpu(call),true);
    }
    private void place(int x,int y){var call=new PlaceBlock(x,y);invoke(call,()->ports.placeBlock(call),true);}
    private void store(Field field,Form form,int operand){
        var before=state;var a=before.actor();var m=a.motion();OptionalInt expected=field==Field.NO_BUMPER?before.noBumperByte():OptionalInt.of(switch(field){
            case ROUTINE->m.routine();case HURT_TIMER->a.timers().hurt();case X_SPEED->uw(m.xVelocity());case Y_SPEED->uw(m.yVelocity());case FLAGS->m.flags();case PLAYER_JUMP->m.playerJump();default->throw new AssertionError();});
        int result=form==Form.ASSIGN?operand:form==Form.SET_BIT?expected.orElseThrow()|(1<<operand):expected.orElseThrow()&~(1<<operand);
        State after;
        if(field==Field.NO_BUMPER)after=new State(a,OptionalInt.of(result));
        else{
            var next=m;
            if(field!=Field.HURT_TIMER)next=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),field==Field.X_SPEED?sw(result):m.xVelocity(),field==Field.Y_SPEED?sw(result):m.yVelocity(),m.groundVelocity(),field==Field.ROUTINE?result:m.routine(),m.angle(),field==Field.FLAGS?result:m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),field==Field.PLAYER_JUMP?result:m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
            var t=a.timers();var timers=field==Field.HURT_TIMER?new CdPlayerBody.Timers(result,t.invincibility(),t.speedShoes(),t.reset()):t;
            after=new State(new CdPlayerBody.State(next,a.animation(),a.header(),timers,a.history(),a.rotationDistance(),a.objectLinks(),a.rotationAngle()),before.noBumperByte());
        }
        var call=new Store(field,form,expected,operand,result,after);invoke(call,()->ports.store(call),true);
    }
    private int x(){return state.actor().motion().centreX16_16()>>>16;}private int y(){return state.actor().motion().centreY16_16()>>>16;}
    /** Same scalar CalcAngle algorithm as CdPlayerMath48-79 over independent raw views; that existing
     * constructor requires the forbidden PP ramp holder, so no fake CdR11aPlayerAssets is created. */
    private int angle(int x,int y){
        audit(Call.ATAN);x=sw(x);y=sw(y);if(x==0&&y==0)return 0x40;
        int ax=x<0?uw(-x):x,ay=y<0?uw(-y):y,a;
        if(ay>=ax)a=(0x40-readByte(data.arcTanBytes(),(ax<<8)/ay,Boundary.ATAN_BYTE))&255;
        else a=readByte(data.arcTanBytes(),(ay<<8)/ax,Boundary.ATAN_BYTE);
        if(x<0)a=uw(-a+0x80);if(y<0)a=uw(-a+0x100);return a;
    }
    /** Native CalcSine reads cosine FIRST, then sine (Functions Misc69-76). */
    private int[] sine(int angle){audit(Call.SINE_COSINE);int i=angle&255;int cosine=sw(readWord(data.sineWords(),(i+64)*2,Boundary.SINE_WORD));int sine=sw(readWord(data.sineWords(),i*2,Boundary.SINE_WORD));return new int[]{sine,cosine};}
    private static int read(OptionalInt value,Boundary boundary){return value.orElseThrow(()->new Unowned(boundary,0));}
    private static int readByte(Optional<CdAssetBytes> value,int offset,Boundary boundary){var b=value.orElseThrow(()->new Unowned(boundary,offset));if(offset<0||offset>=b.length())throw new Unowned(boundary,offset);return b.unsignedByte(offset);}
    private static int readWord(Optional<CdAssetBytes> value,int offset,Boundary boundary){var b=value.orElseThrow(()->new Unowned(boundary,offset));if(offset<0||offset>b.length()-2)throw new Unowned(boundary,offset);return b.unsignedWord(offset);}
    private static int uw(int value){return value&65535;}private static int sw(int value){return(short)value;}
    private static void word(int v){if((v&~65535)!=0)throw new IllegalArgumentException("Native WORD required");}
    private static void optionalByte(OptionalInt v){Objects.requireNonNull(v);if(v.isPresent()&&(v.getAsInt()&~255)!=0)throw new IllegalArgumentException("Native BYTE required");}
}
