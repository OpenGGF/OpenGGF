package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import java.util.*;
import java.util.function.Supplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
/** Original-USA C/D Capsule15 including Init/Main/Explode/StartResults/ResultsActive/Seed.
* N1ecc5f3a Level/_Objects/Level End/Main.asm105-379,818-907; ACT3=1, no legacy DEMO prefix.
* Existing raw gravity, floor and animation kernels own their algorithms, FixBugs=0 unchanged.
* Capsule itself makes no PLC request: existing Boss PLC4/load-plan owns capsule art. Native8 is
* retained RTS, not deletion. Seed landing publishes ID1F/subtype1/routine0 for a later physical visit.
*
* Calls are privately issued at exact source sites/order with real full96/status/bank cuts and current
* source images. Root additionally authenticates its cached body/actual World invocation/complete Frame,
* Core, scroll/palette/audio pairs, prebuilds typed publication and returns the original owner completion.
* Scalar before/after values and component receipts do not confer that runtime authority. Result effects
* describe completed work only; no call/cut/callback/command is retained for replay. Direct source/child
* writes publish in order; faults preserve their original prefix and rethrow the same exception. Failure
* fences are suppressed, never restored. Draw changes only source sprite bit7 through the existing queue.
* No player/score/current shadow, new art/service/clock/capture owner, Frame or World publication exists.
* All Java/build/native/GL execution UNRUN; caller owns real Results/next programme/fight qualification. */
public final class CdR13Capsule {
    public enum Field {
        PLAYER_FLAGS(8,true,false),PLAYER_X_RADIUS(8,true,false),PLAYER_X(16,true,false),PLAYER_Y_RADIUS(8,true,false),PLAYER_Y(16,true,false),PLAYER_X_VELOCITY(16,true,true),PLAYER_Y_VELOCITY(16,true,true),TIME_ZONE(8,true,false),ZONE_ACT(16,true,false),TIME_MINUTES(8,true,false),TIME_SECONDS(8,true,false),RINGS(16,true,false),UPDATE_HUD_TIME(8,false,true),CONTROL_LOCK(8,false,true),PLAYER_CONTROL_HOLD(16,false,true),UPDATE_HUD_BONUS(8,false,true),TIME_BONUS(16,false,true),RING_BONUS(16,false,true),PRIMARY_ANGLE(8,false,true);
        private final int bits;
        private final boolean read,write;
        Field(int bits,boolean read,boolean write){
            this.bits=bits;
            this.read=read;
            this.write=write;
        }
        public int bits(){
            return bits;
        }
        private int mask(){
            return bits==8?255:65535;
        }
        private void width(int v){
            if((v&~mask())!=0)throw new IllegalArgumentException("Capsule scalar width: "+this);
        }
    }
    public enum Site {
        HIT_FLAGS,HIT_X_RADIUS,HIT_X,HIT_Y_RADIUS,HIT_Y,HUD_STOP,BOUNCE_X,BOUNCE_X_RADIUS,BOUNCE_X_VELOCITY,BOUNCE_Y_VELOCITY,BOUNCE_STORE_X,BOUNCE_STORE_Y,RESTORE_PALETTE,EXPLOSION_SOUND,FLOOR_ANGLE,RESULTS_TIME_ZONE,FADE_PCM,RESULTS_MUSIC,CONTROL_LOCK,CONTROL_HOLD,RESULTS_ZONE_ACT,SSZ_CONTROL_HOLD,HUD_BONUS,TIME_MINUTES,TIME_SECONDS,TIME_BONUS,RINGS,RING_BONUS,DRAW
    }
    public enum StoreForm {
        ASSIGN,BSET0
    }
    public enum CpuCommand {
        FADE_PCM(0x82),RESULTS_MUSIC(0x6B);
        private final int id;
        CpuCommand(int id){
            this.id=id;
        }
        public int nativeId(){
            return id;
        }
    }
    public enum Boundary {
        OBJECT_ID,ROUTINE_DIRECTORY,EXTERNAL_OWNER,RESULTS_ALLOCATION
    }
    public static final class UnownedCapsule extends UnsupportedOperationException {
        private final Boundary boundary;
        private final Site site;
        private final int wholeIndex,value;
        private final List<Effect> completedEffects;
        private UnownedCapsule(Boundary b,Cursor c,Site s,int value){
            super(b+" at Capsule slot "+c.whole+" site "+s+" value "+value);
            boundary=b;
            site=s;
            wholeIndex=c.whole;
            this.value=value;
            completedEffects=List.copyOf(c.effects);
        }
        public Boundary boundary(){
            return boundary;
        }
        public Site site(){
            return site;
        }
        public int wholeIndex(){
            return wholeIndex;
        }
        public int value(){
            return value;
        }
        public List<Effect> completedEffects(){
            return completedEffects;
        }
    }
    public sealed interface Effect permits RoutineEntered,ReadReturned,StoreReturned,PaletteReturned,FmReturned,CpuReturned,DrawReturned,AllocationReturned,ChildCreated,AnimationReturned,GravityReturned,FloorReturned,FlowerRequested {
    }
    public record RoutineEntered(int routine)implements Effect{
    }
    public record ReadReturned(Site site,Field field,int value)implements Effect{
    }
    public record StoreReturned(Site site,Field field,int before,int after)implements Effect{
    }
    public record PaletteReturned(int nativeId)implements Effect{
    }
    public record FmReturned()implements Effect{
    }
    public record CpuReturned(CpuCommand command)implements Effect{
    }
    public record DrawReturned(CdObjectDrawQueue.EnqueueResult result)implements Effect{
    }
    public enum AllocationKind{
        EXPLOSION,SEED,RESULTS
    }
    public record AllocationReturned(AllocationKind kind,OptionalInt whole)implements Effect{
    }
    public record ChildCreated(int whole,int objectId)implements Effect{
    }
    public record AnimationReturned(CdObjectAnimation.State result)implements Effect{
    }
    public record GravityReturned(CdPlayerMovement.RawObjectMoveResult result)implements Effect{
    }
    public record FloorReturned(CdObjectCollisionCheck.FloorDistance result)implements Effect{
    }
    public record FlowerRequested(int whole)implements Effect{
    }
    public record Result(CdObjectState state,List<Effect> effects){
        public Result{
            Objects.requireNonNull(state);
            effects=List.copyOf(effects);
        }
    }
    private interface Receipt{
    }
    public static final class ReadReceipt implements Receipt{
        private final int value;
        private ReadReceipt(int value){
            this.value=value;
        }
        public int value(){
            return value;
        }
    }
    public static final class StoreReceipt implements Receipt{
        private final int before,after;
        private StoreReceipt(int before,int after){
            this.before=before;
            this.after=after;
        }
        public int before(){
            return before;
        }
        public int after(){
            return after;
        }
    }
    public static final class Completion implements Receipt{
        private Completion(){
        }
    }
    public static final class DrawReceipt implements Receipt{
        private final CdObjectDrawQueue.EnqueueResult result;
        private DrawReceipt(CdObjectDrawQueue.EnqueueResult r){
            result=r;
        }
        public CdObjectDrawQueue.EnqueueResult result(){
            return result;
        }
    }
    public abstract sealed class Call permits ReadCall,StoreCall,PaletteCall,FmCall,CpuCall,DrawCall {
        private final Cursor cursor;
        private final Site site;
        private final int order;
        private final CdObjectPlacementRuntime.Snapshot cut;
        private final CdObjectState source;
        private Receipt receipt;
        private Call(Cursor c,Site site){
            cursor=c;
            this.site=site;
            order=c.order++;
            cut=placement.capture();
            source=state(c.whole);
        }
        public final Site site(){
            return site;
        }
        public final int order(){
            return order;
        }
        public final CdObjectPlacementRuntime.Snapshot actualCut(){
            return cut;
        }
        public final CdObjectState expectedSource(){
            return source;
        }
        final void guard(){
            if(!isCurrentCall(this)||receipt!=null)throw new IllegalStateException("Capsule call is not the active original completion");
        }
        final void completed(Receipt r){
            receipt=r;
        }
        final UnownedCapsule unowned(){
            return new UnownedCapsule(Boundary.EXTERNAL_OWNER,cursor,site,0);
        }
    }
    public final class ReadCall extends Call{
        private final Field field;
        private ReadCall(Cursor c,Site s,Field f){
            super(c,s);
            if(!f.read)throw new IllegalArgumentException("Not a Capsule read");
            field=f;
        }
        public Field field(){
            return field;
        }
        public ReadReceipt complete(int value){
            guard();
            field.width(value);
            var r=new ReadReceipt(value);
            completed(r);
            return r;
        }
    }
    public final class StoreCall extends Call{
        private final Field field;
        private final int value;
        private final StoreForm form;
        private StoreCall(Cursor c,Site s,Field f,int value,StoreForm form){
            super(c,s);
            if(!f.write)throw new IllegalArgumentException("Not a Capsule store");
            field=f;
            this.value=value&f.mask();
            this.form=form;
        }
        public Field field(){
            return field;
        }
        public int value(){
            return value;
        }
        public StoreForm form(){
            return form;
        }
        public StoreReceipt complete(int before,int after){
            guard();
            field.width(before);
            field.width(after);
            if(after!=(form==StoreForm.BSET0?before|1:value))throw new IllegalArgumentException("Capsule original scalar completion differs");
            var r=new StoreReceipt(before,after);
            completed(r);
            return r;
        }
    }
    public record PaletteOperands(int nativeId,int destinationWord,int longwordCounter,CdAssetBytes bytes){
        public PaletteOperands{Objects.requireNonNull(bytes);if((nativeId&~255)!=0||(destinationWord&~65535)!=0||longwordCounter<0||bytes.length()!=(longwordCounter+1)*4)throw new IllegalArgumentException("Native palette operands");}
    }
    public final class PaletteCall extends Call{
        private PaletteCall(Cursor c){
            super(c,Site.RESTORE_PALETTE);
        }
        public PaletteOperands sourceOperands(){return data.restoredPalette();}
        public CdR13FutureAssets.PaletteBinding binding(){
            if(assets==null)throw new IllegalStateException("CC Capsule has source-neutral palette operands");
            return assets.restoredPalette();
        }
        public Completion complete(){
            guard();
            var r=new Completion();
            completed(r);
            return r;
        }
    }
    public final class FmCall extends Call{
        private FmCall(Cursor c){
            super(c,Site.EXPLOSION_SOUND);
        }
        public int nativeId(){
            return 0x9E;
        }
        public Completion complete(){
            guard();
            var r=new Completion();
            completed(r);
            return r;
        }
    }
    public final class CpuCall extends Call{
        private final CpuCommand command;
        private CpuCall(Cursor c,CpuCommand command){
            super(c,command==CpuCommand.FADE_PCM?Site.FADE_PCM:Site.RESULTS_MUSIC);
            this.command=command;
        }
        public CpuCommand command(){
            return command;
        }
        public Completion complete(){
            guard();
            var r=new Completion();
            completed(r);
            return r;
        }
    }
    public final class DrawCall extends Call{
        private DrawCall(Cursor c){
            super(c,Site.DRAW);
        }
        public DrawReceipt complete(CdObjectDrawQueue.EnqueueResult value){
            guard();
            var r=new DrawReceipt(Objects.requireNonNull(value));
            completed(r);
            return r;
        }
    }
    public interface Ports{
        default ReadReceipt read(ReadCall c){
            throw c.unowned();
        }
        default StoreReceipt store(StoreCall c){
            throw c.unowned();
        }
        default Completion palette(PaletteCall c){
            throw c.unowned();
        }
        default Completion fm(FmCall c){
            throw c.unowned();
        }
        default Completion subCpu(CpuCall c){
            throw c.unowned();
        }
        default DrawReceipt draw(DrawCall c){
            throw c.unowned();
        }
    }
    private final CdObjectPlacementRuntime placement;
    /** Finite borrowed operands; construction is not source or live invocation authority. */
    public interface SourceData {
        CdAssetBytes view(CdR13CapsuleAssets.View view);
        int mappingAddress();
        int tileWord();
        PaletteOperands restoredPalette();
    }
    private final CdR13CapsuleAssets assets;
    private final SourceData data;
    private final CdPlayerTerrain terrain;
    private final Ports ports;
    private boolean running;
    private transient Cursor active;
    private transient Call pending;
    private CdR13Capsule(CdObjectPlacementRuntime p,CdR13FutureAssets a,Ports ports){
        placement=p;
        assets=CdR13CapsuleAssets.bind(a);
        data=new SourceData() {
            public CdAssetBytes view(CdR13CapsuleAssets.View v){return assets.view(v);}
            public int mappingAddress(){return assets.mappingAddress();}
            public int tileWord(){return assets.tileWord();}
            public PaletteOperands restoredPalette(){var p=assets.restoredPalette();return new PaletteOperands(p.nativeId(),p.destinationWord(),p.longwordCounter(),p.bytes());}
        };
        terrain=CdPlayerTerrain.fromStageTerrain(a.terrain());
        this.ports=ports;
    }
    private CdR13Capsule(CdObjectPlacementRuntime p,SourceData data,CdPlayerTerrain terrain,Ports ports){
        placement=Objects.requireNonNull(p);assets=null;this.data=Objects.requireNonNull(data);
        this.terrain=Objects.requireNonNull(terrain);this.ports=Objects.requireNonNull(ports);
    }
    /** R31 uses the same native six-entry algorithm; Root binds the actual finite source and ports. */
    public static CdR13Capsule collisionChaos(CdObjectPlacementRuntime p,SourceData data,CdPlayerTerrain terrain,Ports ports){
        return new CdR13Capsule(p,data,terrain,ports);
    }
    public static CdR13Capsule fromAct3FutureAssets(CdObjectPlacementRuntime p,CdR13FutureAssets a,Ports ports){
        Objects.requireNonNull(p);
        Objects.requireNonNull(a);
        Objects.requireNonNull(ports);
        if(!p.ownsAct3FutureAssets(a))throw new IllegalArgumentException("Capsule requires actual retained C/D placement/source");
        return new CdR13Capsule(p,a,ports);
    }
    public CdR13CapsuleAssets assets(){
        if(assets==null)throw new IllegalStateException("This Capsule uses borrowed CC operands");
        return assets;
    }
    public boolean isCurrentCall(Call c){
        return running&&active!=null&&c!=null&&pending==c&&c.cursor==active&&c.receipt==null;
    }
    public Result run(int whole){
        if(running)throw new IllegalStateException("Capsule synchronous reentry");
        var c=new Cursor(whole);
        running=true;
        active=c;
        try{
            if(state(whole).id()!=0x15)throw new UnownedCapsule(Boundary.OBJECT_ID,c,null,state(whole).id());
            int r=state(whole).routine();
            if((r&1)!=0||r>10)throw new UnownedCapsule(Boundary.ROUTINE_DIRECTORY,c,null,r);
            c.effects.add(new RoutineEntered(r));
            switch(r){
                case 0->init(c);
                case 2->main(c);
                case 4->explode(c);
                case 6->startResults(c);
                case 8->{
                    /* native ResultsActive RTS; capsule remains until programme clears object RAM */
                }
                case 10->seed(c);
                default->throw new AssertionError(r);
            }
            r=state(whole).routine();
            if(r!=0&&(r==10||r<6))draw(c);
            return new Result(state(whole),c.effects);
        }
        finally{
            pending=null;
            active=null;
            running=false;
        }
    }
    private final class Cursor{
        final int whole;
        int order;
        final List<Effect> effects=new ArrayList<>();
        Cursor(int whole){
            this.whole=whole;
        }
    }
    private CdObjectState state(int whole){
        return placement.pool().state(whole);
    }
    private void write(CdObjectState s){
        placement.pool().write(s);
    }
    private void init(Cursor c){
        var s=state(c.whole);
        write(s.moveByte(SPRITE_FLAGS,s.spriteFlags()|4));
        write(state(c.whole).addByte(ROUTINE,2));
        write(state(c.whole).moveByte(PRIORITY,4));
        write(state(c.whole).moveLong(MAPPINGS,data.mappingAddress()));
        write(state(c.whole).moveWord(TILE,data.tileWord()));
        write(state(c.whole).moveByte(X_RADIUS,32));
        write(state(c.whole).moveByte(WIDTH,32));
        write(state(c.whole).moveByte(Y_RADIUS,24));
        main(c);
    }
    private void main(Cursor c){
        animate(c);
        if(!hit(c))return;
        store(c,Site.HUD_STOP,Field.UPDATE_HUD_TIME,0);
        write(state(c.whole).moveByte(MAP_FRAME,2));
        write(state(c.whole).moveByte(LEVEL_END_TIMER,120));
        write(state(c.whole).addByte(ROUTINE,2));
        int x=read(c,Site.BOUNCE_X,Field.PLAYER_X);
        int radius=(short)((byte)read(c,Site.BOUNCE_X_RADIUS,Field.PLAYER_X_RADIUS)+32);
        int d=(x-state(c.whole).centreX()+radius)&65535;
        boolean horizontal=(short)d<0||d>=((radius+radius)&65535);
        Field velocity=horizontal?Field.PLAYER_X_VELOCITY:Field.PLAYER_Y_VELOCITY;
        int v=(short)read(c,horizontal?Site.BOUNCE_X_VELOCITY:Site.BOUNCE_Y_VELOCITY,velocity);
        store(c,horizontal?Site.BOUNCE_STORE_X:Site.BOUNCE_STORE_Y,velocity,((short)-v)>>2);
    }
    private boolean hit(Cursor c){
        if((read(c,Site.HIT_FLAGS,Field.PLAYER_FLAGS)&4)==0)return false;
        int radius=(short)((byte)read(c,Site.HIT_X_RADIUS,Field.PLAYER_X_RADIUS)+32);
        int d=(read(c,Site.HIT_X,Field.PLAYER_X)-state(c.whole).centreX()+radius)&65535;
        if((short)d<0||d>=((radius+radius)&65535))return false;
        radius=(short)((byte)read(c,Site.HIT_Y_RADIUS,Field.PLAYER_Y_RADIUS)+28);
        d=(read(c,Site.HIT_Y,Field.PLAYER_Y)-state(c.whole).centreY()+radius)&65535;
        return(short)d>=0&&d<((radius+radius)&65535);
    }
    private OptionalInt find(Cursor c,AllocationKind kind){
        var result=placement.pool().findObjSlot();
        c.effects.add(new AllocationReturned(kind,result));
        return result;
    }
    private void explode(Cursor c){
        write(state(c.whole).addByte(LEVEL_END_TIMER,-1));
        var s=state(c.whole);
        int timer=s.unsignedByte(LEVEL_END_TIMER);
        if((byte)timer<0){
            spawnSeeds(c);
            write(state(c.whole).addByte(ROUTINE,2));
            write(state(c.whole).moveByte(LEVEL_END_TIMER,60));
            return;
        }
        if((timer&3)!=0)return;
        int at=((timer>>>2)&7)*2;
        var slot=find(c,AllocationKind.EXPLOSION);
        if(slot.isEmpty())return;
        var call=new FmCall(c);
        invoke(call,()->ports.fm(call));
        c.effects.add(new FmReturned());
        int w=slot.getAsInt();
        write(state(w).moveByte(ID,0x18));
        write(state(w).moveByte(ROUTINE_2,1));
        write(state(w).moveWord(X,state(c.whole).centreX()));
        write(state(w).moveWord(Y,state(c.whole).centreY()));
        var offsets=data.view(CdR13CapsuleAssets.View.EXPLOSION_POSITIONS);
        write(state(w).addWord(X,offsets.signedByte(at)));
        write(state(w).addWord(Y,offsets.signedByte(at+1)));
        c.effects.add(new ChildCreated(w,0x18));
    }
    private void spawnSeeds(Cursor c){
        var palette=new PaletteCall(c);
        invoke(palette,()->ports.palette(palette));
        c.effects.add(new PaletteReturned(palette.sourceOperands().nativeId()));
        for(int n=0;n<7;n++){
            var slot=find(c,AllocationKind.SEED);
            if(slot.isEmpty())return;
            int w=slot.getAsInt();
            write(state(w).moveByte(ID,0x15));
            write(state(w).moveByte(SPRITE_FLAGS,state(w).spriteFlags()|4));
            write(state(w).moveWord(X,state(c.whole).centreX()));
            write(state(w).moveWord(Y,state(c.whole).centreY()));
            write(state(w).moveByte(ROUTINE,10));
            write(state(w).moveLong(MAPPINGS,data.mappingAddress()));
            write(state(w).moveWord(TILE,data.tileWord()));
            write(state(w).moveByte(ANIMATION,1));
            write(state(w).moveWord(Y_VELOCITY,-0x600));
            write(state(w).moveWord(X_VELOCITY,data.view(CdR13CapsuleAssets.View.SEED_VELOCITIES).unsignedWord(n*2)));
            c.effects.add(new ChildCreated(w,0x15));
        }
    }
    private void seed(Cursor c){
        animate(c);
        var s=state(c.whole);
        var result=CdPlayerMovement.moveObjectWithGravity(new CdPlayerMovement.RawObjectMotion(s.longBits(X_POSITION),s.longBits(Y_POSITION),s.signedWord(X_VELOCITY),s.signedWord(Y_VELOCITY),s.flags(),s.unsignedByte(RAW_CONTROL_2C),s.unsignedByte(STAND_LINK_3D)));
        write(state(c.whole).moveWord(Y_VELOCITY,result.yVelocity()));
        write(state(c.whole).moveLong(X_POSITION,result.centreX16_16()));
        write(state(c.whole).moveLong(Y_POSITION,result.centreY16_16()));
        c.effects.add(new GravityReturned(result));
        var floor=CdObjectCollisionCheck.objGetFloorDist(terrain,state(c.whole),v->store(c,Site.FLOOR_ANGLE,Field.PRIMARY_ANGLE,v));
        c.effects.add(new FloorReturned(floor));
        if(floor.distance()<0){
            write(state(c.whole).moveByte(ID,0x1F));
            write(state(c.whole).moveByte(SUBTYPE,1));
            write(state(c.whole).moveByte(ROUTINE,0));
            c.effects.add(new FlowerRequested(c.whole));
        }
    }
    private void startResults(Cursor c){
        write(state(c.whole).addByte(LEVEL_END_TIMER,-1));
        if(state(c.whole).unsignedByte(LEVEL_END_TIMER)!=0)return;
        if(read(c,Site.RESULTS_TIME_ZONE,Field.TIME_ZONE)==0)cpu(c,CpuCommand.FADE_PCM);
        cpu(c,CpuCommand.RESULTS_MUSIC);
        store(c,Site.CONTROL_LOCK,Field.CONTROL_LOCK,1,StoreForm.BSET0);
        store(c,Site.CONTROL_HOLD,Field.PLAYER_CONTROL_HOLD,0x808);
        if(read(c,Site.RESULTS_ZONE_ACT,Field.ZONE_ACT)==0x502)store(c,Site.SSZ_CONTROL_HOLD,Field.PLAYER_CONTROL_HOLD,0);
        write(state(c.whole).moveByte(LEVEL_END_TIMER,180));
        write(state(c.whole).addByte(ROUTINE,2));
        var found=find(c,AllocationKind.RESULTS);
        if(found.isEmpty())throw new UnownedCapsule(Boundary.RESULTS_ALLOCATION,c,null,CdObjectPool.END_WHOLE_INDEX);
        int w=found.getAsInt();
        write(state(w).moveByte(ID,0x3A));
        write(state(w).moveByte(RESULTS_TIMER,16));
        c.effects.add(new ChildCreated(w,0x3A));
        store(c,Site.HUD_BONUS,Field.UPDATE_HUD_BONUS,1);
        int time=read(c,Site.TIME_MINUTES,Field.TIME_MINUTES)*60;
        time=(time+read(c,Site.TIME_SECONDS,Field.TIME_SECONDS))&65535;
        var bonuses=data.view(CdR13CapsuleAssets.View.TIME_BONUSES);
        int index=Math.min(time/15,bonuses.length()/2-1);
        store(c,Site.TIME_BONUS,Field.TIME_BONUS,bonuses.unsignedWord(index*2));
        store(c,Site.RING_BONUS,Field.RING_BONUS,(read(c,Site.RINGS,Field.RINGS)*100)&65535);
    }
    private void animate(Cursor c){
        var s=state(c.whole);
        var input=new CdObjectAnimation.State(s.unsignedByte(ANIMATION),s.unsignedByte(PREVIOUS_ANIMATION),s.unsignedByte(ANIMATION_FRAME),s.unsignedByte(ANIMATION_TIME),s.unsignedByte(MAP_FRAME),s.spriteFlags(),s.flags(),s.routine(),s.unsignedByte(ROUTINE_2));
        var bytes=data.view(CdR13CapsuleAssets.View.ANIMATIONS);
        if(input.animationId()!=input.previousId()){
            write(state(c.whole).moveByte(PREVIOUS_ANIMATION,input.animationId()));
            write(state(c.whole).moveByte(ANIMATION_FRAME,0));
            write(state(c.whole).moveByte(ANIMATION_TIME,0));
        }
        write(state(c.whole).addByte(ANIMATION_TIME,-1));
        if(state(c.whole).signedByte(ANIMATION_TIME)<0&&input.animationId()<2){
            int script=(short)bytes.unsignedWord(input.animationId()*2);
            write(state(c.whole).moveByte(ANIMATION_TIME,bytes.unsignedByte(script)));
        }
        var result=CdObjectAnimation.step(input,new CdObjectAnimation.AnimationTable(bytes,0,2));
        write(s.moveByte(ANIMATION,result.animationId()).moveByte(PREVIOUS_ANIMATION,result.previousId()).moveByte(ANIMATION_FRAME,result.scriptIndex()).moveByte(ANIMATION_TIME,result.timer()).moveByte(MAP_FRAME,result.mapFrame()).moveByte(SPRITE_FLAGS,result.spriteFlags()).moveByte(FLAGS,result.statusFlags()).moveByte(ROUTINE,result.routine()).moveByte(ROUTINE_2,result.routine2()));
        c.effects.add(new AnimationReturned(result));
    }
    private int read(Cursor c,Site site,Field field){
        var call=new ReadCall(c,site,field);
        var result=invoke(call,()->ports.read(call));
        c.effects.add(new ReadReturned(site,field,result.value()));
        return result.value();
    }
    private void store(Cursor c,Site site,Field field,int value){
        store(c,site,field,value,StoreForm.ASSIGN);
    }
    private void store(Cursor c,Site site,Field field,int value,StoreForm form){
        var call=new StoreCall(c,site,field,value,form);
        var result=invoke(call,()->ports.store(call));
        c.effects.add(new StoreReturned(site,field,result.before(),result.after()));
    }
    private void cpu(Cursor c,CpuCommand command){
        var call=new CpuCall(c,command);
        invoke(call,()->ports.subCpu(call));
        c.effects.add(new CpuReturned(command));
    }
    private void draw(Cursor c){
        var call=new DrawCall(c);
        var r=invoke(call,()->ports.draw(call));
        c.effects.add(new DrawReturned(r.result()));
    }
    private void fence(Call c,boolean failed){
        placement.validateRestore(c.cut);
        var now=placement.capture();
        if(!now.status().equals(c.cut.status())||!Arrays.equals(now.savedFlags().copy(),c.cut.savedFlags().copy()))throw new IllegalStateException("Capsule callback changed status/saved bank");
        var expected=c.cut.pool();
        if(c instanceof DrawCall){
            var after=CdObjectPool.overlay(expected,List.of(c.source.moveByte(SPRITE_FLAGS,c.source.spriteFlags()&127)));
            if(now.pool().slots().equals(after.slots())||(failed&&now.pool().slots().equals(expected.slots())))return;
        }
        else if(now.pool().slots().equals(expected.slots()))return;
        throw new IllegalStateException("Capsule callback changed full96/source cut");
    }
    private <R extends Receipt> R invoke(Call c,Supplier<R> callback){
        if(pending!=null)throw new IllegalStateException("Nested Capsule callback");
        pending=c;
        try{
            R result=callback.get();
            if(result==null||result!=c.receipt)throw new IllegalStateException("Capsule missing original completion");
            fence(c,false);
            return result;
        }
        catch(RuntimeException|Error original){
            try{
                fence(c,true);
            }
            catch(RuntimeException|Error failed){
                if(failed!=original)original.addSuppressed(failed);
            }
            throw original;
        }
        finally{
            pending=null;
        }
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
