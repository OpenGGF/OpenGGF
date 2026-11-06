package com.openggf.game.soniccd.objects;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR13BossAssets;
import com.openggf.game.soniccd.level.CdR13FutureAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import java.util.*;
import java.util.function.Supplier;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
/**
* Original-USA R13C/D Eggman2A and machine2B..32 component closure. Native source is
* objects/r1/boss.asm:3-2745 and Level/Palmtree Panic/Objects/Boss/Main.asm at
* N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. No FixBugs alternative occurs in this body.
* All63 directory entries (Arm4/8 alias),14 allocation declarations and shipped helper clobbers
* are translated as semantic object operations, never instructions, stack/bus or address-space emulation.
*
* <p>Only original retained assets admit construction. One run visits ONE current physical object.
* FindObjSlot is the existing ID-only physical scan; it never clears dirty free slots. Raw native WORD
* links resolve only an aligned dynamic physical identity, and are read again at each reached dereference,
* including ID-zero/reused targets. The caller owns walk order, so children earlier/later than their parent
* wait/run according to that caller's real physical walk. No private allocation generations are invented.
*
* <p>Direct object stores publish in native order. Typed external calls carry the actual full96/status/bank
* cut, source image, native site and invocation order. The private pending reference plus one-shot completion
* authenticates only this cached component's suspended call. Root must additionally authenticate its actual
* World/Frame/Core invocation and complete pair, prebuild existing typed publication, perform the original
* owner call, publish once and return the original receipt. Public inputs, effects or receipts never create
* that authority. Scalar completion records its actual before/after value; it does not verify Frame itself.
* No callback or executable command is retained in a Result. An absent owner stops at its exact reached call.
*
* <p>Callbacks must preserve the complete placement cut; DrawObject may only clear source sprite bit7.
* Failures preserve direct stores and legitimate external prefixes and rethrow the identical original fault;
* a failed fence is suppressed onto that fault. There is no rollback, generic state restore, player/score/
* camera/current shadow, clock, renderer, art source, PLC service, capture owner or World admission here.
* Shared animation/math/floor helpers are called once. Their reached stores precede any later read fault.
* All Java authored UNRUN; Root owns compilation, real ports, natural route, defeat and pixel/parity evidence.
*/
public final class CdR13Boss {
    /** PLAYER_HURT_TIMER is oPlayerHurt WORD30 (_Definitions.inc83), not either control-lock field. */
    public enum Field {
        BOSS_FLAGS(8,true,true), BOSS_FIGHT(8,false,true), GOOD_FUTURE(8,true,false),
        BOTTOM_BOUND(16,false,true), TARGET_BOTTOM_BOUND(16,false,true),
        LEFT_BOUND(16,true,true), TARGET_LEFT_BOUND(16,false,true),
        RIGHT_BOUND(16,true,true), TARGET_RIGHT_BOUND(16,true,true), CAMERA_Y_CENTRE(16,true,true),
        PLAYER_HURT_TIMER(16,true,false), PLAYER_ROUTINE(8,true,false), PLAYER_X(16,true,false),
        PLAYER_Y(16,true,false), PLAYER_FLAGS(8,true,false), PLAYER_ANGLE(8,false,true),
        PLAYER_GROUND_SPEED(16,false,true), PLAYER_X_SPEED(16,false,true), PLAYER_Y_SPEED(16,false,true),
        PRIMARY_ANGLE(8,false,true);
        private final int bits;
        private final boolean readable,writable;
        Field(int bits,boolean readable,boolean writable){
            this.bits=bits;
            this.readable=readable;
            this.writable=writable;
        }
        public int bits(){
            return bits;
        }
        private int mask(){
            return bits==8?255:65535;
        }
        private void value(int v){
            if((v&~mask())!=0)throw new IllegalArgumentException("Boss scalar width: "+this);
        }
    }
    /** Named native read/store/call sites, not numeric commands or programme authority. */
    public enum Site {
        BOTTOM_FLAGS, BOTTOM_STORE, BOTTOM_TARGET_STORE, PLAYER_REACTION_HURT, PLAYER_REACTION_ROUTINE,
        RECOIL_GROUND_SPEED, RECOIL_FLAGS, RECOIL_ANGLE, RECOIL_PLAYER_X, RECOIL_X_SPEED, RECOIL_Y_SPEED,
        HIT_FM, INIT_PLC, INIT_BOSS_FIGHT, INIT_PALETTE,
        ENTRY_RIGHT, ENTRY_TARGET_RIGHT, ENTRY_PLAYER_X_THRESHOLD, ENTRY_PLAYER_X_LEFT,
        ENTRY_LEFT_READ, ENTRY_PLAYER_X_LIMIT, ENTRY_RIGHT_LOCK, ENTRY_TARGET_RIGHT_LOCK,
        ENTRY_LEFT, ENTRY_TARGET_LEFT, FOCUS_READ, FOCUS_STORE, BOSS_MUSIC, START_BOSS_FLAGS,
        DEFEAT_POINTS, ESCAPE_GOOD_FUTURE, ESCAPE_MUSIC, ESCAPE_CAPSULE_PALETTE,
        ESCAPE_BOSS_FLAGS, ESCAPE_BOSS_FIGHT, EXIT_RIGHT_READ, EXIT_RIGHT_STORE,
        EXIT_TARGET_RIGHT_READ, EXIT_TARGET_RIGHT_STORE, EXIT_COMPARE_RIGHT, EXIT_CLAMP_RIGHT,
        EXIT_CLAMP_TARGET_RIGHT, FOREARM_PLAYER_Y, FLOOR_PRIMARY_ANGLE, FOOT_IMPACT, EXPLOSION_FM, DRAW
    }
    public enum StoreForm {
        ASSIGN, XOR
    }
    public enum FmSound {
        HIT(0x98), EXPLOSION(0x9E);
        private final int id;
        FmSound(int id){
            this.id=id;
        }
        public int nativeId(){
            return id;
        }
    }
    public enum CpuCommand {
        BOSS_MUSIC(0x67), FOOT_IMPACT(0x7E), GOOD_MUSIC(0x10), BAD_MUSIC(0x11);
        private final int id;
        CpuCommand(int id){
            this.id=id;
        }
        public int nativeId(){
            return id;
        }
    }
    public enum Boundary {
        OBJECT_ID, ROUTINE_DIRECTORY, LINK_READ, POSE_READ, EXTERNAL_OWNER
    }
    public static final class UnownedBoss extends UnsupportedOperationException {
        private final Boundary boundary;
        private final int wholeIndex,value;
        private final Site site;
        private final List<Effect> completedEffects;
        private UnownedBoss(Boundary b,Cursor c,int value,Site site){
            super(b+" at boss slot "+c.whole+" value "+value+(site==null?"":" site "+site));
            boundary=b;
            wholeIndex=c.whole;
            this.value=value;
            this.site=site;
            completedEffects=List.copyOf(c.effects);
        }
        public Boundary boundary(){
            return boundary;
        }
        public int wholeIndex(){
            return wholeIndex;
        }
        public int value(){
            return value;
        }
        public Site site(){
            return site;
        }
        public List<Effect> completedEffects(){
            return completedEffects;
        }
    }
    public sealed interface Effect permits RoutineEntered,ScalarRead,ScalarStored,PlcLoaded,PaletteLoaded,
    PointsAdded,FmReturned,CpuReturned,CapsulePaletteLoaded,DrawReturned,Allocated,Deleted,
    FloorReturned,AnimationReturned,MathReturned {
    }
    public record RoutineEntered(CdR13BossAssets.Part part,int routine) implements Effect {
    }
    public record ScalarRead(Site site,Field field,int value) implements Effect {
    }
    public record ScalarStored(Site site,Field field,int before,int after) implements Effect {
    }
    public record PlcLoaded(int nativeId) implements Effect {
    }
    public record PaletteLoaded(int nativeId) implements Effect {
    }
    public record PointsAdded(int nativeUnits) implements Effect {
    }
    public record FmReturned(FmSound sound) implements Effect {
    }
    public record CpuReturned(CpuCommand command) implements Effect {
    }
    public record CapsulePaletteLoaded() implements Effect {
    }
    public record DrawReturned(CdObjectDrawQueue.EnqueueResult result) implements Effect {
    }
    /** declarationOrdinal -1 is the single explosion allocator, -2 is the ten-pair explosion allocator. */
    public record Allocated(int declarationOrdinal,OptionalInt wholeIndex) implements Effect {
        public Allocated{
            Objects.requireNonNull(wholeIndex);
        }
    }
    public record Deleted(int wholeIndex) implements Effect {
    }
    public record FloorReturned(CdObjectCollisionCheck.FloorDistance result) implements Effect {
    }
    public record AnimationReturned(CdObjectAnimation.State result) implements Effect {
    }
    public record MathReturned(int angle,CdPlayerMath.SineCosine result) implements Effect {
    }
    public record Result(CdObjectState state,List<Effect> effects){
        public Result{
            Objects.requireNonNull(state);
            effects=List.copyOf(effects);
        }
    }
    private interface Receipt {
    }
    public static final class ReadReceipt implements Receipt {
        private final Call call;
        private final int value;
        private ReadReceipt(Call c,int v){
            call=c;
            value=v;
        }
        public int value(){
            return value;
        }
    }
    public static final class StoreReceipt implements Receipt {
        private final Call call;
        private final int before,after;
        private StoreReceipt(Call c,int b,int a){
            call=c;
            before=b;
            after=a;
        }
        public int before(){
            return before;
        }
        public int after(){
            return after;
        }
    }
    public static final class Completion implements Receipt {
        private final Call call;
        private Completion(Call c){
            call=c;
        }
    }
    public static final class DrawReceipt implements Receipt {
        private final Call call;
        private final CdObjectDrawQueue.EnqueueResult result;
        private DrawReceipt(Call c,CdObjectDrawQueue.EnqueueResult r){
            call=c;
            result=r;
        }
        public CdObjectDrawQueue.EnqueueResult result(){
            return result;
        }
    }
    /** Private construction and exact pending-reference check; public values are comparison data only. */
    public abstract sealed class Call permits ReadCall,StoreCall,ActionCall,DrawCall {
        private final Cursor cursor;
        private final Site site;
        private final int order;
        private final CdObjectPlacementRuntime.Snapshot cut;
        private final CdObjectState source;
        private Receipt completion;
        private boolean consumed;
        private Call(Cursor c,Site site){
            cursor=c;
            this.site=site;
            order=c.order++;
            cut=capture(c);
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
        final void requireCompletion(){
            if(!isCurrentCall(this)||completion!=null||consumed)throw new IllegalStateException("Boss call is not this active original completion");
        }
        final void completed(Receipt receipt){
            completion=receipt;
        }
        final UnownedBoss unowned(){
            return stop(Boundary.EXTERNAL_OWNER,cursor,0,site);
        }
    }
    public final class ReadCall extends Call {
        private final Field field;
        private ReadCall(Cursor c,Site s,Field f){
            super(c,s);
            if(!f.readable)throw new IllegalArgumentException("Not a boss read");
            field=f;
        }
        public Field field(){
            return field;
        }
        public ReadReceipt complete(int actualValue){
            requireCompletion();
            field.value(actualValue);
            var r=new ReadReceipt(this,actualValue);
            completed(r);
            return r;
        }
    }
    public final class StoreCall extends Call {
        private final Field field;
        private final StoreForm form;
        private final int operand;
        private StoreCall(Cursor c,Site s,Field f,StoreForm form,int operand){
            super(c,s);
            if(!f.writable)throw new IllegalArgumentException("Not a boss store");
            field=f;
            this.form=form;
            this.operand=operand&f.mask();
        }
        public Field field(){
            return field;
        }
        public StoreForm form(){
            return form;
        }
        public int operand(){
            return operand;
        }
        /** Root supplies the actual before/after of its completed typed store, preserving all other owner fields. */
        public StoreReceipt complete(int actualBefore,int actualAfter){
            requireCompletion();
            field.value(actualBefore);
            field.value(actualAfter);
            if(actualAfter!=(form==StoreForm.ASSIGN?operand:actualBefore^operand))throw new IllegalArgumentException("Boss native scalar completion mismatch");
            var r=new StoreReceipt(this,actualBefore,actualAfter);
            completed(r);
            return r;
        }
    }
    public abstract sealed class ActionCall extends Call permits PlcCall,PaletteCall,PointsCall,FmCall,CpuCall,CapsulePaletteCall {
        private ActionCall(Cursor c,Site s){
            super(c,s);
        }
        public final Completion complete(){
            requireCompletion();
            var r=new Completion(this);
            completed(r);
            return r;
        }
    }
    public final class PlcCall extends ActionCall {
        private final CdR13FutureAssets.PlcList list;
        private PlcCall(Cursor c){
            super(c,Site.INIT_PLC);
            list=CdR13Boss.this.source.plcs().get(4);
        }
        public CdR13FutureAssets.PlcList list(){
            return list;
        }
    }
    public final class PaletteCall extends ActionCall {
        private PaletteCall(Cursor c){
            super(c,Site.INIT_PALETTE);
        }
        public CdR13BossAssets.Palette palette(){
            return assets.palette();
        }
    }
    public final class PointsCall extends ActionCall {
        private PointsCall(Cursor c){
            super(c,Site.DEFEAT_POINTS);
        }
        public int nativeUnits(){
            return 100;
        }
    }
    public final class FmCall extends ActionCall {
        private final FmSound sound;
        private FmCall(Cursor c,Site s,FmSound sound){
            super(c,s);
            this.sound=sound;
        }
        public FmSound sound(){
            return sound;
        }
    }
    public final class CpuCall extends ActionCall {
        private final CpuCommand command;
        private CpuCall(Cursor c,Site s,CpuCommand command){
            super(c,s);
            this.command=command;
        }
        public CpuCommand command(){
            return command;
        }
    }
    public final class CapsulePaletteCall extends ActionCall {
        private CapsulePaletteCall(Cursor c){
            super(c,Site.ESCAPE_CAPSULE_PALETTE);
        }
    }
    public final class DrawCall extends Call {
        private DrawCall(Cursor c){
            super(c,Site.DRAW);
        }
        public DrawReceipt complete(CdObjectDrawQueue.EnqueueResult actualOutcome){
            requireCompletion();
            var r=new DrawReceipt(this,Objects.requireNonNull(actualOutcome));
            completed(r);
            return r;
        }
    }
    /** Every default is a typed first-unowned stop, never a success/default scalar. */
    public interface Ports {
        default ReadReceipt read(ReadCall c){
            throw c.unowned();
        }
        default StoreReceipt store(StoreCall c){
            throw c.unowned();
        }
        default Completion loadPlc(PlcCall c){
            throw c.unowned();
        }
        default Completion loadBossPalette(PaletteCall c){
            throw c.unowned();
        }
        default Completion addPoints(PointsCall c){
            throw c.unowned();
        }
        default Completion fm(FmCall c){
            throw c.unowned();
        }
        default Completion subCpu(CpuCall c){
            throw c.unowned();
        }
        default Completion loadCapsulePalette(CapsulePaletteCall c){
            throw c.unowned();
        }
        default DrawReceipt draw(DrawCall c){
            throw c.unowned();
        }
    }
    private final CdObjectPlacementRuntime placement;
    private final CdR13FutureAssets source;
    private final CdR13BossAssets assets;
    private final CdPlayerMath math;
    private final CdPlayerTerrain terrain;
    private final Ports ports;
    private boolean running;
    private transient Cursor active;
    private transient Call pending;
    private CdR13Boss(CdObjectPlacementRuntime p,CdR13FutureAssets a,Ports ports){
        placement=p;
        source=a;
        this.ports=ports;
        assets=CdR13BossAssets.bind(a);
        math=new CdPlayerMath(a.player());
        terrain=CdPlayerTerrain.fromStageTerrain(a.terrain());
    }
    public static CdR13Boss fromAct3FutureAssets(CdObjectPlacementRuntime placement,CdR13FutureAssets source,Ports ports){
        Objects.requireNonNull(placement);
        Objects.requireNonNull(source);
        Objects.requireNonNull(ports);
        if(!placement.ownsAct3FutureAssets(source))throw new IllegalArgumentException("Boss requires the actual retained R13 placement/source");
        return new CdR13Boss(placement,source,ports);
    }
    public CdR13BossAssets assets(){
        return assets;
    }
    public boolean isCurrentCall(Call call){
        return running&&active!=null&&call!=null&&pending==call&&call.cursor==active&&call.completion==null&&!call.consumed;
    }
    public Result run(int wholeIndex){
        if(running)throw new IllegalStateException("Boss component synchronous reentry");
        var s=state(wholeIndex);
        var c=new Cursor(wholeIndex);
        running=true;
        active=c;
        try{
            if(s.id()<0x2A||s.id()>0x32)throw stop(Boundary.OBJECT_ID,c,s.id(),null);
            c.part=CdR13BossAssets.Part.values()[s.id()-0x2A];
            if(c.part==CdR13BossAssets.Part.EGGMAN)prelude(c);
            int r=state(wholeIndex).routine();
            if((r&1)!=0||r>=c.part.routineCount()*2)throw stop(Boundary.ROUTINE_DIRECTORY,c,r,null);
            c.effects.add(new RoutineEntered(c.part,r));
            boolean wrapper=switch(c.part){
                case EGGMAN->eggman(c,r);
                case BODY->body(c,r);
                case THIGH->thigh(c,r);
                case CALF->calf(c,r);
                case FOOT->foot(c,r);
                case SHOULDER->shoulder(c,r);
                case ARM->arm(c,r);
                case FOREARM->forearm(c,r);
                case BUMPER->bumper(c,r);
            };
            if(wrapper){
                if(c.part==CdR13BossAssets.Part.EGGMAN)animate(c);
                if(c.part!=CdR13BossAssets.Part.ARM||!bit(c.whole,2))draw(c);
            }
            return new Result(state(wholeIndex),c.effects);
        }
        finally{
            pending=null;
            active=null;
            running=false;
        }
    }
    private final class Cursor {
        final int whole;
        int order;
        CdR13BossAssets.Part part;
        final List<Effect> effects=new ArrayList<>();
        Cursor(int whole){
            this.whole=whole;
        }
    }
    private CdObjectState state(int whole){
        return placement.pool().state(whole);
    }
    private int b(int whole,CdObjectState.ByteField field){
        return state(whole).unsignedByte(field);
    }
    private int w(int whole,CdObjectState.WordField field){
        return state(whole).word(field);
    }
    private int sw(int whole,CdObjectState.WordField field){
        return state(whole).signedWord(field);
    }
    private int l(int whole,CdObjectState.LongField field){
        return state(whole).longBits(field);
    }
    private void put(int whole,CdObjectState.ByteField field,int v){
        placement.pool().write(state(whole).moveByte(field,v));
    }
    private void put(int whole,CdObjectState.WordField field,int v){
        placement.pool().write(state(whole).moveWord(field,v));
    }
    private void put(int whole,CdObjectState.LongField field,int v){
        placement.pool().write(state(whole).moveLong(field,v));
    }
    private void add(int whole,CdObjectState.ByteField f,int v){
        put(whole,f,b(whole,f)+v);
    }
    private void add(int whole,CdObjectState.WordField f,int v){
        put(whole,f,w(whole,f)+v);
    }
    private void add(int whole,CdObjectState.LongField f,int v){
        put(whole,f,l(whole,f)+v);
    }
    private boolean bit(int whole,int n){
        return (b(whole,BOSS_FLAGS)&(1<<n))!=0;
    }
    private boolean set(int whole,int n){
        boolean old=bit(whole,n);
        put(whole,BOSS_FLAGS,b(whole,BOSS_FLAGS)|(1<<n));
        return old;
    }
    private boolean clear(int whole,int n){
        boolean old=bit(whole,n);
        put(whole,BOSS_FLAGS,b(whole,BOSS_FLAGS)&~(1<<n));
        return old;
    }
    private boolean toggle(int whole,int n){
        boolean old=bit(whole,n);
        put(whole,BOSS_FLAGS,b(whole,BOSS_FLAGS)^(1<<n));
        return old;
    }
    private int pointer(int whole){
        return state(whole).identity().nativeAddress24()&0xFFFF;
    }
    /** Mirrors existing Spring dynamic-word identity resolution; no virtual RAM or occupant-ID check. */
    private int dereference(Cursor c,int raw){
        int address=0xFF0000|raw;
        if(address<0xFFD800||address>=0xFFF000||((address-0xFFD000)&63)!=0)throw stop(Boundary.LINK_READ,c,raw,null);
        return(address-0xFFD000)>>>6;
    }
    private int link(Cursor c,int whole,CdObjectState.WordField f){
        return dereference(c,w(whole,f));
    }
    private int next(Cursor c,int whole){
        return link(c,whole,BOSS_NEXT);
    }
    private int parent(Cursor c,int whole){
        return link(c,whole,BOSS_PARENT);
    }
    private int branch(Cursor c,int whole){
        return link(c,whole,BOSS_BRANCH);
    }
    private void xy(int destination,int origin,int dx,int dy){
        put(destination,X,w(origin,X)+dx);
        put(destination,Y,w(origin,Y)+dy);
    }
    private int read(Cursor c,Site site,Field field){
        var call=new ReadCall(c,site,field);
        var r=invoke(call,()->ports.read(call));
        c.effects.add(new ScalarRead(site,field,r.value()));
        return r.value();
    }
    private void store(Cursor c,Site site,Field field,int value){
        store(c,site,field,StoreForm.ASSIGN,value);
    }
    private void store(Cursor c,Site site,Field field,StoreForm form,int value){
        var call=new StoreCall(c,site,field,form,value);
        var r=invoke(call,()->ports.store(call));
        c.effects.add(new ScalarStored(site,field,r.before(),r.after()));
    }
    private void fm(Cursor c,FmSound sound){
        var call=new FmCall(c,sound==FmSound.HIT?Site.HIT_FM:Site.EXPLOSION_FM,sound);
        invoke(call,()->ports.fm(call));
        c.effects.add(new FmReturned(sound));
    }
    private void cpu(Cursor c,Site site,CpuCommand command){
        var call=new CpuCall(c,site,command);
        invoke(call,()->ports.subCpu(call));
        c.effects.add(new CpuReturned(command));
    }
    private void draw(Cursor c){
        var call=new DrawCall(c);
        var r=invoke(call,()->ports.draw(call));
        c.effects.add(new DrawReturned(r.result()));
    }
    private CdPlayerMath.SineCosine sine(Cursor c,int angle){
        var result=math.calcSine(angle);
        c.effects.add(new MathReturned(angle&255,result));
        return result;
    }
    private CdObjectCollisionCheck.FloorDistance floor(Cursor c){
        var r=CdObjectCollisionCheck.objGetFloorDist(terrain,state(c.whole),v->store(c,Site.FLOOR_PRIMARY_ANGLE,Field.PRIMARY_ANGLE,v));
        c.effects.add(new FloorReturned(r));
        return r;
    }
    private CdObjectPlacementRuntime.Snapshot capture(Cursor c){
        var cut=placement.capture();
        if(!cut.pool().slots().get(c.whole-32).equals(state(c.whole)))throw new IllegalStateException("Boss source changed before call");
        return cut;
    }
    private void fence(Call call,boolean failed){
        placement.validateRestore(call.cut);
        var now=placement.capture();
        if(!now.status().equals(call.cut.status())||!Arrays.equals(now.savedFlags().copy(),call.cut.savedFlags().copy()))throw new IllegalStateException("Boss callback changed placement/status/saved bank");
        var expected=call.cut.pool();
        if(call instanceof DrawCall){
            var drawn=CdObjectPool.overlay(expected,List.of(call.source.moveByte(SPRITE_FLAGS,call.source.spriteFlags()&127)));
            if(now.pool().slots().equals(drawn.slots())||(failed&&now.pool().slots().equals(expected.slots())))return;
        }
        else if(now.pool().slots().equals(expected.slots()))return;
        throw new IllegalStateException("Boss callback changed its full96/source cut");
    }
    private <R extends Receipt> R invoke(Call call,Supplier<R> callback){
        if(pending!=null)throw new IllegalStateException("Nested boss callback");
        pending=call;
        try{
            R result=callback.get();
            if(result==null||result!=call.completion||call.consumed)throw new IllegalStateException("Boss callback did not return its original once-only completion");
            fence(call,false);
            call.consumed=true;
            return result;
        }
        catch(RuntimeException|Error original){
            try{
                fence(call,true);
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
    private static UnownedBoss stop(Boundary b,Cursor c,int value,Site site){
        return new UnownedBoss(b,c,value,site);
    }
    // EggmanObject's four ordered helpers, BEFORE its routine directory (boss.asm:3-167).
    private void head(Cursor c,int animation){
        int a=c.whole;
        put(a,MAP_FRAME,0);
        put(a,ANIMATION_FRAME,0);
        put(a,ANIMATION_TIME,0);
        put(a,BOSS_ANIMATION_FLAGS,0);
        put(a,ANIMATION,animation);
    }
    private void prelude(Cursor c){
        int a=c.whole;
        if(b(a,BOSS_TIMER)!=0){
            add(a,BOSS_TIMER,-1);
            if(b(a,BOSS_TIMER)==0)head(c,0);
        }
        int bottom=0x310;
        if(read(c,Site.BOTTOM_FLAGS,Field.BOSS_FLAGS)!=0){
            bottom=0x100;
            if(b(a,BOSS_SHAKE)!=0){
                add(a,BOSS_SHAKE,-1);
                bottom+=(b(a,BOSS_SHAKE)&1)==0?1:-1;
            }
        }
        store(c,Site.BOTTOM_STORE,Field.BOTTOM_BOUND,bottom);
        store(c,Site.BOTTOM_TARGET_STORE,Field.TARGET_BOTTOM_BOUND,bottom);
        playerReaction(c);
        hitCheck(c);
    }
    private void playerReaction(Cursor c){
        int a=c.whole;
        if(b(a,ANIMATION)!=0)return;
        if(read(c,Site.PLAYER_REACTION_HURT,Field.PLAYER_HURT_TIMER)!=0||read(c,Site.PLAYER_REACTION_ROUTINE,Field.PLAYER_ROUTINE)==6){
            head(c,1);
            put(a,BOSS_TIMER,0x3C);
        }
    }
    private void hitCheck(Cursor c){
        int a=c.whole;
        if(b(a,BOSS_HIT_TIMER)!=0){
            add(a,BOSS_HIT_TIMER,-1);
            if(b(a,BOSS_HIT_TIMER)==0)rearm(c);
            return;
        }
        if(!bit(a,3))return;
        int hit=a;
        if(b(hit,COLLISION_TYPE)==0){
            hit(c,hit);
            return;
        }
        hit=next(c,next(c,next(c,a)));
        if(b(hit,COLLISION_TYPE)==0){
            hit(c,hit);
            return;
        }
        hit=next(c,hit);
        if(b(hit,COLLISION_TYPE)==0){
            hit(c,hit);
            return;
        }
        hit=next(c,branch(c,next(c,a)));
        if(b(hit,COLLISION_TYPE)==0){
            hit(c,hit);
            return;
        }
        hit=next(c,hit);
        if(b(hit,COLLISION_TYPE)==0){
            hit(c,hit);
            return;
        }
        if(b(a,BOSS_COUNTER)==3){
            hit=next(c,next(c,branch(c,branch(c,a))));
            if(b(hit,COLLISION_TYPE)==0){
                bumperHit(c,hit);
                return;
            }
        }
        else if(b(a,BOSS_COUNTER)!=2)return;
        hit=next(c,next(c,next(c,branch(c,a))));
        if(b(hit,COLLISION_TYPE)==0)bumperHit(c,hit);
    }
    private void recoil(Cursor c,int target){
        store(c,Site.RECOIL_GROUND_SPEED,Field.PLAYER_GROUND_SPEED,0x400);
        int dy=-0x400;
        if((read(c,Site.RECOIL_FLAGS,Field.PLAYER_FLAGS)&2)==0){
            store(c,Site.RECOIL_ANGLE,Field.PLAYER_ANGLE,StoreForm.XOR,0x80);
            dy=0;
        }
        int dx=0x400;
        if(read(c,Site.RECOIL_PLAYER_X,Field.PLAYER_X)<w(target,X))dx=-dx;
        store(c,Site.RECOIL_X_SPEED,Field.PLAYER_X_SPEED,dx);
        store(c,Site.RECOIL_Y_SPEED,Field.PLAYER_Y_SPEED,dy);
    }
    private void bumperHit(Cursor c,int target){
        set(target,4);
        recoil(c,target);
        rearm(c);
        fm(c,FmSound.HIT);
        playerReaction(c);
        /* native PlayFmSound falls through into sub_20BAC0 (:144); the top-level earlier call is distinct */
    }
    private void hit(Cursor c,int target){
        int a=c.whole;
        put(a,BOSS_HIT_TIMER,0x14);
        recoil(c,target);
        head(c,2);
        put(a,BOSS_TIMER,0x78);
        add(a,BOSS_COUNTER,-1);
        if(b(a,BOSS_COUNTER)==0){
            head(c,2);
            put(a,BOSS_TIMER,0);
            clear(a,3);
            put(a,ROUTINE_2,0);
            put(a,ROUTINE,6);
            put(a,COLLISION_TYPE,0);
            put(a,COLLISION_STATUS,0);
            put(branch(c,a),ROUTINE,4);
            int body=next(c,a);
            put(body,ROUTINE,14);
            destroyLeg(c,next(c,body));
            destroyLeg(c,branch(c,body));
            return;
        }
        int shoulder=branch(c,a);
        if(b(a,BOSS_COUNTER)==2){
            set(shoulder,5);
            rearmFirstArmAndBody(c);
        }
        else{
            set(shoulder,6);
            rearmBody(c);
        }
    }
    private void destroyLeg(Cursor c,int thigh){
        put(thigh,ROUTINE,24);
        int calf=next(c,thigh);
        put(calf,ROUTINE,14);
        put(calf,COLLISION_TYPE,0);
        put(calf,COLLISION_STATUS,0);
        int foot=next(c,calf);
        put(foot,ROUTINE,12);
        put(foot,COLLISION_TYPE,0);
        put(foot,COLLISION_STATUS,0);
    }
    private void rearm(Cursor c){
        int health=b(c.whole,BOSS_COUNTER);
        if(health==3){
            int shoulder=branch(c,c.whole);
            rearmBumper(c,branch(c,shoulder));
            rearmFirstArmAndBody(c);
        }
        else if(health==2)rearmFirstArmAndBody(c);
        else if(health==1)rearmBody(c);
    }
    private void rearmFirstArmAndBody(Cursor c){
        rearmBumper(c,next(c,branch(c,c.whole)));
        rearmBody(c);
    }
    private void rearmBumper(Cursor c,int arm){
        int bumper=next(c,next(c,arm));
        put(bumper,COLLISION_TYPE,0xFF);
        put(bumper,COLLISION_STATUS,2);
    }
    private void rearmBody(Cursor c){
        int a=c.whole;
        put(a,COLLISION_TYPE,0xFC);
        put(a,COLLISION_STATUS,2);
        int body=next(c,a);
        int calf=next(c,next(c,body));
        put(calf,COLLISION_TYPE,0xBD);
        put(calf,COLLISION_STATUS,2);
        int foot=next(c,calf);
        put(foot,COLLISION_TYPE,0xBE);
        put(foot,COLLISION_STATUS,2);
        calf=next(c,branch(c,body));
        put(calf,COLLISION_TYPE,0xBD);
        put(calf,COLLISION_STATUS,2);
        foot=next(c,calf);
        put(foot,COLLISION_TYPE,0xBE);
        put(foot,COLLISION_STATUS,2);
    }
    private boolean eggman(Cursor c,int r){
        int a=c.whole;
        switch(r){
            case 0->{
                var plc=new PlcCall(c);
                invoke(plc,()->ports.loadPlc(plc));
                c.effects.add(new PlcLoaded(plc.list().nativeId()));
                store(c,Site.INIT_BOSS_FIGHT,Field.BOSS_FIGHT,1);
                put(a,FLAGS,0);
                put(a,ROUTINE,2);
                put(a,SPRITE_FLAGS,4);
                put(a,PRIORITY,6);
                put(a,WIDTH,0x14);
                put(a,Y_RADIUS,8);
                put(a,TILE,assets.body(CdR13BossAssets.Part.EGGMAN).tileWord());
                mapping(a,CdR13BossAssets.Visual.EGGMAN);
                put(a,ANIMATION,1);
                put(a,MAP_FRAME,0);
                put(a,ANIMATION_FRAME,0);
                put(a,ANIMATION_TIME,0);
                put(a,BOSS_ANIMATION_FLAGS,0);
                put(a,X,0xC52);
                put(a,Y,0x78);
                var pal=new PaletteCall(c);
                invoke(pal,()->ports.loadBossPalette(pal));
                c.effects.add(new PaletteLoaded(5));
            }
            case 2->{
                store(c,Site.ENTRY_RIGHT,Field.RIGHT_BOUND,0xAC0);
                store(c,Site.ENTRY_TARGET_RIGHT,Field.TARGET_RIGHT_BOUND,0xAC0);
                if((short)read(c,Site.ENTRY_PLAYER_X_THRESHOLD,Field.PLAYER_X)<0xA6A)break;
                int d0=(read(c,Site.ENTRY_PLAYER_X_LEFT,Field.PLAYER_X)-0xA0)&65535;
                if((short)d0<(short)read(c,Site.ENTRY_LEFT_READ,Field.LEFT_BOUND))break;
                if((short)read(c,Site.ENTRY_PLAYER_X_LIMIT,Field.PLAYER_X)>=0xB60){
                    put(a,ROUTINE,12);
                    store(c,Site.ENTRY_RIGHT_LOCK,Field.RIGHT_BOUND,0xAC0);
                    store(c,Site.ENTRY_TARGET_RIGHT_LOCK,Field.TARGET_RIGHT_BOUND,0xAC0);
                    d0=0xAC0;
                }
                store(c,Site.ENTRY_LEFT,Field.LEFT_BOUND,d0);
                store(c,Site.ENTRY_TARGET_LEFT,Field.TARGET_LEFT_BOUND,d0);
            }
            case 4->{
                int body=next(c,a);
                if(!clear(body,0))break;
                if(b(a,ROUTINE_2)==2){
                    put(a,ANIMATION_WORD,0);
                    put(a,MAP_FRAME,0);
                    put(a,ANIMATION_FRAME,0);
                    put(a,ANIMATION_TIME,0);
                    put(a,BOSS_ANIMATION_FLAGS,0);
                    put(a,BOSS_COUNTER,3);
                    set(a,3);
                    rearm(c);
                    body=next(c,a);
                }
                add(a,ROUTINE_2,2);
                clear(body,1);
                var pose=assets.view(CdR13BossAssets.Table.POSE_SEQUENCE);
                int at=b(a,ROUTINE_2);
                if(at>=pose.length())throw stop(Boundary.POSE_READ,c,at,null);
                if(pose.signedByte(at)<0){
                    put(a,ROUTINE_2,6);
                    at=6;
                }
                put(body,ROUTINE,pose.unsignedByte(at));
                if(at+1>=pose.length())throw stop(Boundary.POSE_READ,c,at+1,null);
                put(body,BOSS_PARAMETER,pose.unsignedByte(at+1));
            }
            case 6->{
                add(a,BOSS_COUNTER,1);
                explosion(c,true);
                if(b(a,BOSS_COUNTER)==0x5E){
                    put(a,BOSS_SAVED_X,w(a,X));
                    put(a,X_VELOCITY,w(a,Y));
                    put(a,ANIMATION,3);
                    put(a,MAP_FRAME,0);
                    put(a,ANIMATION_FRAME,0);
                    put(a,ANIMATION_TIME,0);
                    put(a,BOSS_ANIMATION_FLAGS,0);
                    set(next(c,a),0);
                }
                if(b(a,BOSS_COUNTER)>=0x78){
                    put(a,BOSS_COUNTER,0);
                    put(a,ROUTINE,8);
                    put(a,WIDTH,0x20);
                    put(a,Y_RADIUS,0x20);
                    var points=new PointsCall(c);
                    invoke(points,()->ports.addPoints(points));
                    c.effects.add(new PointsAdded(100));
                }
            }
            case 8->{
                if(b(a,ROUTINE_2)!=0){
                    add(a,Y,-w(a,BOSS_DY));
                    add(a,BOSS_COUNTER,3);
                    int dy=sine(c,b(a,BOSS_COUNTER)).sine()>>5;
                    put(a,BOSS_DY,dy);
                    add(a,Y,dy);
                    add(a,X_POSITION,0x28000);
                    if(sw(a,X)<0xC80)break;
                    put(a,BOSS_COUNTER,0);
                    var command=read(c,Site.ESCAPE_GOOD_FUTURE,Field.GOOD_FUTURE)==0?CpuCommand.BAD_MUSIC:CpuCommand.GOOD_MUSIC;
                    cpu(c,Site.ESCAPE_MUSIC,command);
                    var pal=new CapsulePaletteCall(c);
                    invoke(pal,()->ports.loadCapsulePalette(pal));
                    c.effects.add(new CapsulePaletteLoaded());
                    store(c,Site.ESCAPE_BOSS_FLAGS,Field.BOSS_FLAGS,0);
                    store(c,Site.ESCAPE_BOSS_FIGHT,Field.BOSS_FIGHT,0);
                    put(a,ROUTINE,10);
                    return exit(c);
                }
                else{
                    add(a,BOSS_COUNTER,1);
                    int x=w(a,X),y=w(a,Y);
                    put(a,X,w(a,BOSS_SAVED_X));
                    put(a,Y,w(a,X_VELOCITY));
                    explosion(c,true);
                    put(a,X,x);
                    put(a,Y,y);
                    add(a,X_POSITION,0x8000);
                    add(a,Y_POSITION,-0x20000);
                    if(sw(a,Y)<=0x158){
                        add(a,ROUTINE_2,1);
                        head(c,4);
                        put(a,BOSS_COUNTER,0x40);
                        put(a,BOSS_DY,8);
                    }
                }
            }
            case 10->{
                return exit(c);
            }
            case 12->{
                int focus=(read(c,Site.FOCUS_READ,Field.CAMERA_Y_CENTRE)+6)&65535;
                store(c,Site.FOCUS_STORE,Field.CAMERA_Y_CENTRE,focus);
                if((short)focus>=0xC8){
                    cpu(c,Site.BOSS_MUSIC,CpuCommand.BOSS_MUSIC);
                    store(c,Site.START_BOSS_FLAGS,Field.BOSS_FLAGS,1);
                    put(a,ROUTINE,14);
                }
            }
            case 14->{
                add(a,BOSS_COUNTER,1);
                if(b(a,BOSS_COUNTER)==0x3C){
                    put(a,BOSS_COUNTER,0);
                    put(a,ROUTINE,4);
                    put(a,X,0xBD2);
                    put(a,Y,0x78);
                    allocateMachine(c);
                }
            }
            default->throw new AssertionError(r);
        }
        return true;
    }
    private boolean exit(Cursor c){
        /* CamBounds+2 left WORD is read/discarded; its following original right is NOT a live-camera default. */ var bounds=source.view(CdR13FutureAssets.Asset.CAM_BOUNDS);
        bounds.unsignedWord(2);
        int right=bounds.unsignedWord(4);
        store(c,Site.EXIT_RIGHT_STORE,Field.RIGHT_BOUND,read(c,Site.EXIT_RIGHT_READ,Field.RIGHT_BOUND)+6);
        store(c,Site.EXIT_TARGET_RIGHT_STORE,Field.TARGET_RIGHT_BOUND,read(c,Site.EXIT_TARGET_RIGHT_READ,Field.TARGET_RIGHT_BOUND)+6);
        if((short)right<=(short)read(c,Site.EXIT_COMPARE_RIGHT,Field.RIGHT_BOUND)){
            store(c,Site.EXIT_CLAMP_RIGHT,Field.RIGHT_BOUND,right);
            store(c,Site.EXIT_CLAMP_TARGET_RIGHT,Field.TARGET_RIGHT_BOUND,right);
            delete(c);
        }
        return false;
        /* source ADDQ #4,SP skips Eggman's AnimateObject+DrawObject wrapper */
    }
    private void mapping(int whole,CdR13BossAssets.Visual visual){
        put(whole,MAPPINGS,assets.range(visual.mappingTable()).nativeAddress());
    }
    private int allocate(Cursor c,int declaration){
        var found=placement.pool().findObjSlot();
        c.effects.add(new Allocated(declaration,found));
        if(found.isEmpty())return -1;
        int child=found.getAsInt();
        put(child,X,w(c.whole,X));
        put(child,Y,w(c.whole,Y));
        return child;
    }
    private void childId(int whole,int declaration){
        put(whole,ID,assets.children().get(declaration).objectId());
    }
    private void allocateMachine(Cursor c){
        int a=c.whole,body,thigh,calf,foot,shoulder,arm,forearm,bumper;
        body=allocate(c,0);
        if(body<0)return;
        put(a,BOSS_NEXT,pointer(body));
        put(body,BOSS_PARENT,pointer(a));
        put(body,PRIORITY,6);
        childId(body,0);
        thigh=allocate(c,1);
        if(thigh<0)return;
        put(body,BOSS_NEXT,pointer(thigh));
        put(thigh,BOSS_PARENT,pointer(body));
        put(thigh,PRIORITY,6);
        childId(thigh,1);
        calf=allocate(c,2);
        if(calf<0)return;
        put(thigh,BOSS_NEXT,pointer(calf));
        put(calf,BOSS_PARENT,pointer(thigh));
        put(calf,PRIORITY,3);
        childId(calf,2);
        foot=allocate(c,3);
        if(foot<0)return;
        put(calf,BOSS_NEXT,pointer(foot));
        put(foot,BOSS_PARENT,pointer(calf));
        put(foot,PRIORITY,2);
        childId(foot,3);
        put(foot,BOSS_NEXT,pointer(body));
        thigh=allocate(c,4);
        if(thigh<0)return;
        put(body,BOSS_BRANCH,pointer(thigh));
        put(thigh,BOSS_PARENT,pointer(body));
        put(thigh,PRIORITY,7);
        childId(thigh,4);
        set(thigh,2);
        calf=allocate(c,5);
        if(calf<0)return;
        put(thigh,BOSS_NEXT,pointer(calf));
        put(calf,BOSS_PARENT,pointer(thigh));
        put(calf,PRIORITY,6);
        childId(calf,5);
        set(calf,2);
        foot=allocate(c,6);
        if(foot<0)return;
        put(calf,BOSS_NEXT,pointer(foot));
        put(foot,BOSS_PARENT,pointer(calf));
        put(foot,PRIORITY,5);
        childId(foot,6);
        set(foot,2);
        put(foot,BOSS_NEXT,pointer(body));
        shoulder=allocate(c,7);
        if(shoulder<0)return;
        put(shoulder,BOSS_PARENT,pointer(body));
        int root=parent(c,body);
        put(root,BOSS_BRANCH,pointer(shoulder));
        put(shoulder,PRIORITY,3);
        childId(shoulder,7);
        arm=allocate(c,8);
        if(arm<0)return;
        put(shoulder,BOSS_NEXT,pointer(arm));
        put(arm,BOSS_TIMER,0x80);
        put(arm,BOSS_PARENT,pointer(shoulder));
        put(shoulder,BOSS_NEXT,pointer(arm));
        put(arm,PRIORITY,5);
        childId(arm,8);
        forearm=allocate(c,9);
        if(forearm<0)return;
        put(forearm,BOSS_PARENT,pointer(arm));
        put(arm,BOSS_NEXT,pointer(forearm));
        put(forearm,PRIORITY,4);
        mapping(forearm,CdR13BossAssets.Visual.FOREARM1);
        childId(forearm,9);
        bumper=allocate(c,10);
        if(bumper<0)return;
        put(bumper,BOSS_PARENT,pointer(forearm));
        put(forearm,BOSS_NEXT,pointer(bumper));
        put(bumper,PRIORITY,3);
        childId(bumper,10);
        put(bumper,BOSS_NEXT,pointer(body));
        shoulder=parent(c,parent(c,parent(c,bumper)));
        arm=allocate(c,11);
        if(arm<0)return;
        put(shoulder,BOSS_BRANCH,pointer(arm));
        put(arm,BOSS_PARENT,pointer(shoulder));
        put(arm,PRIORITY,7);
        childId(arm,11);
        set(arm,2);
        forearm=allocate(c,12);
        if(forearm<0)return;
        put(forearm,BOSS_PARENT,pointer(arm));
        put(arm,BOSS_NEXT,pointer(forearm));
        put(forearm,PRIORITY,7);
        mapping(forearm,CdR13BossAssets.Visual.FOREARM2);
        childId(forearm,12);
        set(forearm,2);
        bumper=allocate(c,13);
        if(bumper<0)return;
        put(bumper,BOSS_PARENT,pointer(forearm));
        put(forearm,BOSS_NEXT,pointer(bumper));
        put(bumper,PRIORITY,6);
        childId(bumper,13);
        set(bumper,2);
        put(bumper,BOSS_NEXT,pointer(body));
        put(a,BOSS_COUNTER,3);
        rearm(c);
    }
    private void explosion(Cursor c,boolean offsets){
        int count=b(c.whole,BOSS_COUNTER);
        if(offsets&&(count&3)!=0)return;
        int at=offsets?((count/4)%10)*4:0;
        var found=placement.pool().findObjSlot();
        c.effects.add(new Allocated(offsets?-2:-1,found));
        if(found.isEmpty())return;
        int child=found.getAsInt();
        put(child,ROUTINE_2,0xFF);
        put(child,ID,0x18);
        put(child,X,w(c.whole,X));
        put(child,Y,w(c.whole,Y));
        if(offsets){
            CdAssetBytes table=assets.view(CdR13BossAssets.Table.EXPLOSION_OFFSETS);
            add(child,X,(short)table.unsignedWord(at));
            add(child,Y,(short)table.unsignedWord(at+2));
        }
        fm(c,FmSound.EXPLOSION);
    }
    private void animate(Cursor c){
        int a=c.whole;
        var old=state(a);
        var input=new CdObjectAnimation.State(b(a,ANIMATION),b(a,PREVIOUS_ANIMATION),b(a,ANIMATION_FRAME),b(a,ANIMATION_TIME),b(a,MAP_FRAME),b(a,SPRITE_FLAGS),b(a,FLAGS),b(a,ROUTINE),b(a,ROUTINE_2));
        var table=assets.view(CdR13BossAssets.Table.EGGMAN_ANIMATIONS);
        // Publish only the native prefix before calling the shared SUCCESS algorithm once. The five original
        // scripts contain only frames and FF. On an out-of-extent index, delay is a prior legitimate store.
        if(input.animationId()!=input.previousId()){
            put(a,PREVIOUS_ANIMATION,input.animationId());
            put(a,ANIMATION_FRAME,0);
            put(a,ANIMATION_TIME,0);
        }
        add(a,ANIMATION_TIME,-1);
        if((byte)b(a,ANIMATION_TIME)<0&&b(a,ANIMATION)<5){
            int script=(short)table.unsignedWord(2*b(a,ANIMATION));
            put(a,ANIMATION_TIME,table.unsignedByte(script));
        }
        var result=CdObjectAnimation.step(input,new CdObjectAnimation.AnimationTable(table,0,5));
        placement.pool().write(old.moveByte(ANIMATION,result.animationId()).moveByte(PREVIOUS_ANIMATION,result.previousId()).moveByte(ANIMATION_FRAME,result.scriptIndex()).moveByte(ANIMATION_TIME,result.timer()).moveByte(MAP_FRAME,result.mapFrame()).moveByte(SPRITE_FLAGS,result.spriteFlags()).moveByte(FLAGS,result.statusFlags()).moveByte(ROUTINE,result.routine()).moveByte(ROUTINE_2,result.routine2()));
        c.effects.add(new AnimationReturned(result));
    }
    private void delete(Cursor c){
        placement.pool().deleteObject(c.whole);
        c.effects.add(new Deleted(c.whole));
    }
    // BossBodyObject (:699-1335). Repeated A2.next read in :852/1091/1200 is shipped, not repaired.
    private void chainFlag(Cursor c,int thigh,int flag,boolean value){
        int target=thigh;
        for(int n=0;n<3;n++){
            if(value)set(target,flag);
            else clear(target,flag);
            if(n!=2)target=next(c,target);
        }
    }
    private int shoulderOfBody(Cursor c){
        return branch(c,parent(c,c.whole));
    }
    private void arms(Cursor c,int mode){
        int shoulder=shoulderOfBody(c);
        int raw=w(shoulder,BOSS_BRANCH);
        if(raw!=0){
            int arm=dereference(c,raw);
            put(arm,ROUTINE,mode==1?6:2);
            int f=next(c,arm);
            put(f,ROUTINE,mode==2?10:mode==1?6:8);
            put(f,MAP_FRAME,mode==2?1:0);
        }
        raw=w(shoulder,BOSS_NEXT);
        if(raw!=0){
            int arm=dereference(c,raw);
            put(arm,ROUTINE,mode==0?6:2);
            int f=next(c,arm);
            put(f,ROUTINE,mode==2?10:mode==0?6:8);
            put(f,MAP_FRAME,mode==2?1:0);
        }
    }
    private record LimbContinuation(int firstRaw,int secondRaw) { }
    /** sub_20C500/5AE leave the first target at arm1's bumper (or raw zero)
     * and the second at the shoulder. Body routineC consumes both targets. */
    private LimbContinuation limbSpeeds(Cursor c,boolean fast){
        int body=c.whole;
        int thigh=next(c,body);
        put(thigh,BOSS_LIMB_SPEED,fast?8:2);
        int calf=next(c,thigh);
        put(calf,BOSS_VX_LONG,fast?0x10000:0x8000);
        put(calf,BOSS_FALL_Y,fast?0x8000:0x4000);
        int foot=next(c,calf);
        put(foot,BOSS_VX_LONG,fast?0xC000:0x4000);
        put(foot,BOSS_FALL_Y,fast?0x18000:0x8000);
        thigh=branch(c,body);
        put(thigh,BOSS_LIMB_SPEED,fast?8:2);
        calf=next(c,thigh);
        put(calf,BOSS_VX_LONG,fast?0x10000:0x8000);
        put(calf,BOSS_FALL_Y,fast?0x8000:0x4000);
        foot=next(c,calf);
        put(foot,BOSS_VX_LONG,fast?0xC000:0x4000);
        put(foot,BOSS_FALL_Y,fast?0x18000:0x8000);
        int shoulder=shoulderOfBody(c);
        int raw=w(shoulder,BOSS_BRANCH);
        if(raw!=0){
            int forearm=next(c,dereference(c,raw));
            if(fast)set(forearm,7);
            else clear(forearm,7);
            int bumper=next(c,forearm);
            if(fast)set(bumper,7);
            else clear(bumper,7);
        }
        raw=w(shoulder,BOSS_NEXT);
        if(raw!=0){
            int forearm=next(c,dereference(c,raw));
            if(fast)set(forearm,7);
            else clear(forearm,7);
            int bumper=next(c,forearm);
            if(fast)set(bumper,7);
            else clear(bumper,7);
            raw=pointer(bumper);
        }
        return new LimbContinuation(raw,pointer(shoulder));
    }
    private void clearStepFlags(Cursor c,int leading,int following){
        int footRaw=w(next(c,leading),BOSS_NEXT);
        w(following,BOSS_NEXT);
        int calfRaw=w(following,BOSS_NEXT);
        /* repeated source read, same calf; FixBugs=0 */ clear(leading,0);
        clear(dereference(c,footRaw),0);
        clear(following,0);
        clear(dereference(c,calfRaw),0);
    }
    private boolean body(Cursor c,int r){
        int a=c.whole;
        switch(r){
            case 0->{
                put(a,FLAGS,0);
                put(a,ROUTINE,2);
                put(a,SPRITE_FLAGS,4);
                put(a,WIDTH,0x24);
                put(a,Y_RADIUS,0x20);
                put(a,TILE,assets.body(CdR13BossAssets.Part.BODY).tileWord());
                mapping(a,CdR13BossAssets.Visual.BODY);
                limbSpeeds(c,false);
            }
            case 2->{
                int t=branch(c,a);
                put(t,ROUTINE,16);
                int calf=next(c,t);
                put(calf,ROUTINE,4);
                put(next(c,calf),ROUTINE,10);
                t=next(c,a);
                chainFlag(c,t,5,true);
                if(bit(t,4))set(a,0);
                else{
                    add(a,Y_POSITION,0x18000);
                    add(parent(c,a),Y_POSITION,0x18000);
                }
            }
            case 4->{
                int thigh=branch(c,a);
                clear(thigh,0);
                thigh=next(c,a);
                if(!clear(thigh,0))break;
                if(b(thigh,ROUTINE)==12){
                    put(thigh,ROUTINE,10);
                    break;
                }
                if(b(thigh,ROUTINE)==10){
                    put(thigh,ROUTINE,14);
                    break;
                }
                put(thigh,ROUTINE,2);
                chainFlag(c,thigh,5,false);
                chainFlag(c,thigh,4,false);
                thigh=branch(c,a);
                put(thigh,ROUTINE,8);
                chainFlag(c,thigh,5,true);
                chainFlag(c,thigh,4,true);
                int calf=next(c,thigh);
                put(calf,ROUTINE,2);
                put(next(c,calf),ROUTINE,4);
                set(a,0);
            }
            case 6->{
                if(!set(a,1))arms(c,1);
                if(bit(next(c,a),0)&&bit(branch(c,a),0)){
                    clear(a,1);
                    set(a,6);
                    set(a,0);
                }
            }
            case 8->{
                int leadingRaw=bit(a,6)?w(a,BOSS_BRANCH):w(a,BOSS_NEXT),followingRaw=bit(a,6)?w(a,BOSS_NEXT):w(a,BOSS_BRANCH);
                int leading=dereference(c,leadingRaw);
                if(!bit(leading,0))break;
                int following=dereference(c,followingRaw);
                if(!bit(following,0))break;
                clearStepFlags(c,leading,following);
                if(sw(a,X)<=0xB58)put(a,BOSS_PARAMETER,1);
                add(a,BOSS_PARAMETER,-1);
                if(b(a,BOSS_PARAMETER)==0){
                    set(a,0);
                    clear(a,1);
                    break;
                }
                boolean old=toggle(a,6);
                int first=next(c,a);
                if(old){
                    put(first,ROUTINE,8);
                    chainFlag(c,first,5,true);
                    chainFlag(c,first,4,true);
                    put(next(c,first),ROUTINE,6);
                    int second=branch(c,a);
                    put(second,ROUTINE,2);
                    chainFlag(c,second,5,false);
                    chainFlag(c,second,4,false);
                    put(next(c,second),ROUTINE,8);
                    arms(c,0);
                }
                else{
                    put(first,ROUTINE,2);
                    chainFlag(c,first,5,false);
                    chainFlag(c,first,4,false);
                    put(next(c,first),ROUTINE,8);
                    int second=branch(c,a);
                    put(second,ROUTINE,8);
                    chainFlag(c,second,5,true);
                    chainFlag(c,second,4,true);
                    put(next(c,second),ROUTINE,6);
                    arms(c,1);
                }
            }
            case 10->{
                int leadingRaw=bit(a,6)?w(a,BOSS_BRANCH):w(a,BOSS_NEXT),followingRaw=bit(a,6)?w(a,BOSS_NEXT):w(a,BOSS_BRANCH);
                int leading=dereference(c,leadingRaw);
                if(!bit(leading,0))break;
                int following=dereference(c,followingRaw);
                if(!bit(following,0))break;
                if(!set(a,1))limbSpeeds(c,false);
                else{
                    clearStepFlags(c,leading,following);
                    if(sw(a,X)>=0xBA0)put(a,BOSS_PARAMETER,1);
                    add(a,BOSS_PARAMETER,-1);
                    if(b(a,BOSS_PARAMETER)==0){
                        clear(a,1);
                        set(a,0);
                        break;
                    }
                }
                boolean old=toggle(a,6);
                int first=next(c,a);
                if(old){
                    put(first,ROUTINE,22);
                    chainFlag(c,first,5,true);
                    chainFlag(c,first,4,true);
                    put(next(c,first),ROUTINE,8);
                    int second=branch(c,a);
                    put(second,ROUTINE,18);
                    chainFlag(c,second,5,false);
                    chainFlag(c,second,4,false);
                    put(next(c,second),ROUTINE,6);
                    arms(c,1);
                }
                else{
                    put(first,ROUTINE,18);
                    chainFlag(c,first,5,false);
                    chainFlag(c,first,4,false);
                    put(next(c,first),ROUTINE,6);
                    int second=branch(c,a);
                    put(second,ROUTINE,22);
                    chainFlag(c,second,5,true);
                    chainFlag(c,second,4,true);
                    put(next(c,second),ROUTINE,8);
                    arms(c,0);
                }
            }
            case 12->{
                int leadingRaw=bit(a,6)?w(a,BOSS_BRANCH):w(a,BOSS_NEXT),followingRaw=bit(a,6)?w(a,BOSS_NEXT):w(a,BOSS_BRANCH);
                if(!set(a,1)){
                    var continuation=limbSpeeds(c,true);
                    leadingRaw=continuation.firstRaw();
                    followingRaw=continuation.secondRaw();
                }
                int leading=dereference(c,leadingRaw);
                if(b(leading,ROUTINE)==4){
                    put(next(c,leading),ROUTINE,10);
                    put(next(c,dereference(c,followingRaw)),ROUTINE,12);
                }
                if(!bit(leading,0))break;
                int following=dereference(c,followingRaw);
                if(!bit(following,0))break;
                clearStepFlags(c,leading,following);
                add(a,BOSS_PARAMETER,-1);
                if(b(a,BOSS_PARAMETER)==0){
                    set(a,0);
                    clear(a,1);
                    break;
                }
                boolean old=toggle(a,6);
                int first=next(c,a);
                clear(first,1);
                if(old){
                    chainFlag(c,first,5,true);
                    chainFlag(c,first,4,true);
                    int calf=next(c,first);
                    put(calf,ROUTINE,10);
                    put(next(c,calf),ROUTINE,2);
                    int second=branch(c,a);
                    clear(second,1);
                    put(second,ROUTINE,2);
                    chainFlag(c,second,5,false);
                    chainFlag(c,second,4,false);
                    put(next(c,second),ROUTINE,12);
                }
                else{
                    put(first,ROUTINE,2);
                    chainFlag(c,first,5,false);
                    chainFlag(c,first,4,false);
                    put(next(c,first),ROUTINE,12);
                    int second=branch(c,a);
                    clear(second,1);
                    chainFlag(c,second,5,true);
                    chainFlag(c,second,4,true);
                    int calf=next(c,second);
                    put(calf,ROUTINE,10);
                    put(next(c,calf),ROUTINE,2);
                }
                arms(c,2);
            }
            case 14->{
                if(bit(a,0)){
                    delete(c);
                    return false;
                }
                if(floor(c).distance()>0){
                    int root=parent(c,a);
                    add(a,Y,2);
                    add(root,Y,2);
                }
            }
            case 16->{
                add(a,BOSS_PARAMETER,-1);
                if(b(a,BOSS_PARAMETER)==0){
                    limbSpeeds(c,false);
                    set(a,0);
                    clear(a,1);
                }
            }
            default->throw new AssertionError(r);
        }
        return true;
    }
    private void initPart(Cursor c,int width,int height,CdR13BossAssets.Visual visual){
        int a=c.whole;
        put(a,FLAGS,0);
        put(a,SPRITE_FLAGS,4);
        put(a,WIDTH,width);
        put(a,Y_RADIUS,height);
        put(a,TILE,assets.body(c.part).tileWord());
        if(visual!=null)mapping(a,visual);
    }
    private boolean detached(Cursor c,int initialVx,int initialVy,int accelX,int accelY){
        int a=c.whole;
        if(!set(a,1)){
            put(a,BOSS_VX_LONG,initialVx);
            put(a,BOSS_FALL_Y,initialVy);
        }
        else{
            add(a,BOSS_VX_LONG,accelX);
            add(a,BOSS_FALL_Y,accelY);
        }
        return ballistic(c);
    }
    private boolean ballistic(Cursor c){
        add(c.whole,X_POSITION,l(c.whole,BOSS_VX_LONG));
        add(c.whole,Y_POSITION,l(c.whole,BOSS_FALL_Y));
        if(sw(c.whole,Y)>=0x240){
            delete(c);
            return false;
        }
        return true;
    }
    private boolean shoulder(Cursor c,int r){
        int a=c.whole;
        if(r==0){
            initPart(c,0x10,12,CdR13BossAssets.Visual.SHOULDER);
            put(a,ROUTINE,2);
            r=2;
        }
        if(r==2){
            xy(a,parent(c,a),0x18,-12);
            if(clear(a,6)){
                int arm=next(c,a);
                put(arm,ROUTINE,10);
                put(a,BOSS_NEXT,0);
                /* boss.asm1392-1393 load only dead a1 scratch; DrawObject overwrites a1 before use. Elide the dead load, without fabricating a value or completed-read effect. */
            }
            else if(clear(a,5)){
                int arm=branch(c,a);
                put(arm,ROUTINE,10);
                put(a,BOSS_BRANCH,0);
                put(arm,MAP_FRAME,0);
            }
            return true;
        }
        return detached(c,-0x10000,-0x20000,-0x600,0x1800);
    }
    private boolean arm(Cursor c,int r){
        int a=c.whole;
        if(r==0){
            initPart(c,8,8,CdR13BossAssets.Visual.ARM);
            put(a,ROUTINE,6);
            r=2;
            /* source falls into Routine2 despite stored byte6 */
        }
        if(r==10){
            if(!set(a,1)){
                put(a,BOSS_COUNTER,0);
                put(a,BOSS_VX_LONG,0x10000);
                put(a,BOSS_FALL_Y,-0x20000);
                put(next(c,a),ROUTINE,4);
            }
            else{
                add(a,BOSS_VX_LONG,0x600);
                add(a,BOSS_FALL_Y,0x1F00);
            }
            return ballistic(c);
        }
        if(r==2){
            int old=b(a,BOSS_TIMER);
            add(a,BOSS_TIMER,-2);
            if(old<=2){
                put(a,ROUTINE,4);
                put(a,BOSS_TIMER,0);
                set(a,0);
            }
        }
        else if(r==6){
            add(a,BOSS_TIMER,2);
            if(b(a,BOSS_TIMER)>=0x30){
                put(a,ROUTINE,8);
                put(a,BOSS_TIMER,0x30);
                set(a,0);
            }
        }
        int p=parent(c,a);
        xy(a,p,0,0);
        var sc=sine(c,(b(a,BOSS_TIMER)+0x40)&255);
        add(a,X,sc.cosine()>>4);
        add(a,Y,sc.sine()>>4);
        if(bit(a,2))add(a,X,-10);
        return true;
    }
    private boolean forearm(Cursor c,int r){
        int a=c.whole;
        if(r==0){
            initPart(c,0x20,8,null);
            put(a,ROUTINE,2);
            r=2;
        }
        if(r==4){
            if(!set(a,1)){
                put(a,BOSS_COUNTER,1);
                put(a,BOSS_TIMER,0);
                put(a,BOSS_VX_LONG,0);
                put(a,BOSS_FALL_Y,0x10000);
                put(next(c,a),ROUTINE,4);
            }
            else{
                add(a,BOSS_VX_LONG,-0x620);
                add(a,BOSS_FALL_Y,0x1220);
            }
            if(!ballistic(c))return false;
            add(a,BOSS_TIMER,1);
            if(b(a,BOSS_TIMER)%7==0)explosion(c,false);
            return true;
        }
        if(r==6&&sw(a,BOSS_DX)>0)add(a,BOSS_DX_LONG,-0x8000);
        else if(r==8&&sw(a,BOSS_DX)<0x10)add(a,BOSS_DX_LONG,0x8000);
        else if(r==10){
            if(sw(a,BOSS_DX)<8)add(a,BOSS_DX_LONG,0x8000);
            else put(a,MAP_FRAME,1);
        }
        int p=parent(c,a);
        put(a,X,w(p,X)-0x24);
        add(a,X,w(a,BOSS_DX));
        put(a,Y,w(p,Y));
        add(a,Y,0);
        boolean down=r==10||bit(a,7);
        if(!down)down=(short)read(c,Site.FOREARM_PLAYER_Y,Field.PLAYER_Y)>sw(a,Y);
        if(down){
            if(sw(a,BOSS_DY)<8)add(a,BOSS_DY_LONG,0x10000);
        }
        else if(sw(a,BOSS_DY)>-8)add(a,BOSS_DY_LONG,-0x10000);
        add(a,Y,w(a,BOSS_DY));
        return true;
    }
    private boolean bumper(Cursor c,int r){
        int a=c.whole;
        if(r==0){
            initPart(c,12,16,CdR13BossAssets.Visual.BUMPER);
            put(a,ROUTINE,2);
            r=2;
        }
        if(r==2){
            int p=parent(c,a);
            xy(a,p,-12,0);
            int frame=b(p,MAP_FRAME);
            if(frame!=0)add(a,X,frame==1?8:16);
            if(clear(a,4))add(a,X,-4);
            return true;
        }
        if(!set(a,1)){
            put(a,BOSS_COUNTER,0);
            put(a,COLLISION_TYPE,0);
            put(a,COLLISION_STATUS,0);
            put(a,BOSS_VX_LONG,0);
            put(a,BOSS_FALL_Y,-0x18000);
        }
        else{
            add(a,BOSS_VX_LONG,-0x500);
            add(a,BOSS_FALL_Y,0x1800);
        }
        return ballistic(c);
    }
    private int linkedFootRaw(Cursor c,int thigh){
        return w(next(c,thigh),BOSS_NEXT);
    }
    private int linkedFoot(Cursor c,int thigh){
        return dereference(c,linkedFootRaw(c,thigh));
    }
    private boolean thigh(Cursor c,int r){
        int a=c.whole;
        if(r==0){
            initPart(c,8,8,CdR13BossAssets.Visual.THIGH);
            put(a,ROUTINE,12);
            put(a,BOSS_TIMER,0x58);
            put(a,BOSS_LIMB_SPEED,2);
            return true;
        }
        if(r==24)return detached(c,0,-0x28000,bit(a,4)?-0x600:0x600,0x1860);
        if(r==16){
            int other=next(c,parent(c,a));
            xy(a,other,-10,0);
            put(a,BOSS_ANGLE,w(other,BOSS_ANGLE));
            return true;
        }
        switch(r){
            case 2->{
                int foot=linkedFoot(c,a);
                if(b(foot,ROUTINE)!=6&&b(foot,ROUTINE)!=8){
                    clear(foot,0);
                    put(foot,ROUTINE,6);
                }
                if((byte)b(a,BOSS_TIMER)>0){
                    add(a,BOSS_TIMER,-b(a,BOSS_LIMB_SPEED));
                    if((byte)b(a,BOSS_TIMER)>0)break;
                }
                put(a,BOSS_TIMER,0);
                if(b(foot,ROUTINE)==8||clear(foot,0))put(a,ROUTINE,4);
            }
            case 4->{
                int footRaw=linkedFootRaw(c,a);
                add(a,BOSS_TIMER,b(a,BOSS_LIMB_SPEED));
                if(b(a,BOSS_TIMER)>=0x58){
                    put(a,BOSS_TIMER,0x58);
                    put(a,ROUTINE,6);
                    put(dereference(c,footRaw),ROUTINE,2);
                }
            }
            case 6->{
                if(bit(linkedFoot(c,a),4))set(a,0);
            }
            case 8->{
                if(!bit(a,0)){
                    add(a,BOSS_TIMER,-b(a,BOSS_LIMB_SPEED));
                    if(b(a,BOSS_TIMER)<0x18){
                        put(a,BOSS_TIMER,0x18);
                        set(a,0);
                    }
                }
            }
            case 10->thighRetract(c);
            case 12->thighExtend(c);
            case 14->{
                int angle=(byte)b(a,BOSS_TIMER);
                if(angle==0x18){
                    if(bit(linkedFoot(c,a),0))set(a,0);
                }
                else if(angle<0x18){
                    add(a,BOSS_TIMER,b(a,BOSS_LIMB_SPEED));
                    set(linkedFoot(c,a),7);
                    int other=linkedFoot(c,branch(c,parent(c,a)));
                    set(other,7);
                    thighExtend(c);
                }
                else{
                    add(a,BOSS_TIMER,-b(a,BOSS_LIMB_SPEED));
                    thighRetract(c);
                }
            }
            case 18->{
                int foot=linkedFoot(c,a);
                if(b(foot,ROUTINE)==6)break;
                if(b(foot,ROUTINE)!=8){
                    clear(foot,0);
                    put(foot,ROUTINE,6);
                    break;
                }
                if(b(a,BOSS_TIMER)!=0x20){
                    int old=(byte)b(a,BOSS_TIMER),amount=(byte)b(a,BOSS_LIMB_SPEED);
                    add(a,BOSS_TIMER,-amount);
                    if(old-amount>0)break;
                    /* BGT immediately after SUB.B uses N==V, not signed wrapped result. */
                }
                put(a,BOSS_TIMER,0x20);
                clear(foot,0);
                put(a,ROUTINE,20);
            }
            case 20->{
                int footRaw=linkedFootRaw(c,a);
                add(a,BOSS_TIMER,b(a,BOSS_LIMB_SPEED));
                if((byte)b(a,BOSS_TIMER)>=0x30){
                    put(a,BOSS_TIMER,0x30);
                    put(a,ROUTINE,6);
                    put(dereference(c,footRaw),ROUTINE,2);
                }
            }
            case 22->{
                if(!bit(a,0)){
                    int foot=linkedFoot(c,a);
                    if(b(foot,ROUTINE)==4){
                        add(a,BOSS_TIMER,b(a,BOSS_LIMB_SPEED));
                        if((byte)b(a,BOSS_TIMER)>=0x50)set(a,0);
                    }
                    else if(b(foot,ROUTINE)!=2)put(foot,ROUTINE,2);
                }
            }
            default->throw new AssertionError(r);
        }
        thighPose(c);
        return true;
    }
    private void thighRetract(Cursor c){
        int a=c.whole,foot=linkedFoot(c,a);
        if(b(foot,ROUTINE)!=8){
            put(foot,ROUTINE,6);
            if(b(a,BOSS_TIMER)!=0)add(a,BOSS_TIMER,-b(a,BOSS_LIMB_SPEED));
        }
        else if(b(a,BOSS_TIMER)!=0)add(a,BOSS_TIMER,-b(a,BOSS_LIMB_SPEED));
        else if(clear(foot,0))set(a,0);
    }
    private void thighExtend(Cursor c){
        int a=c.whole,foot=linkedFoot(c,a);
        if(b(foot,ROUTINE)!=4){
            put(foot,ROUTINE,2);
            if((byte)b(a,BOSS_TIMER)<0x58)add(a,BOSS_TIMER,b(a,BOSS_LIMB_SPEED));
        }
        else if((byte)b(a,BOSS_TIMER)<0x58)add(a,BOSS_TIMER,b(a,BOSS_LIMB_SPEED));
        else if(clear(foot,0))set(a,0);
    }
    private void thighPose(Cursor c){
        int a=c.whole;
        var sc=sine(c,b(a,BOSS_TIMER));
        int sy=sc.sine()>>4,cx=sc.cosine()>>4;
        if(bit(a,4)){
            if(bit(a,5)){
                if(!set(a,1)){
                    put(a,BOSS_DX,cx);
                    put(a,BOSS_DY,sy);
                }
                int dx=(short)(cx-w(a,BOSS_DX)),dy=(short)(sy-w(a,BOSS_DY));
                put(a,BOSS_DX,cx);
                put(a,BOSS_DY,sy);
                int body=parent(c,a);
                add(body,X,-dx);
                add(body,Y,-dy);
                int root=parent(c,body);
                add(root,X,-dx);
                add(root,Y,-dy);
                return;
            }
        }
        else clear(a,1);
        int body=parent(c,a);
        put(a,X,w(body,X)+12+cx+(bit(a,2)?-10:0));
        put(a,Y,w(body,Y)+20+sy);
    }
    private boolean calf(Cursor c,int r){
        int a=c.whole;
        if(r==0){
            put(a,FLAGS,0);
            put(a,ROUTINE,2);
            put(a,SPRITE_FLAGS,4);
            put(a,WIDTH,8);
            put(a,Y_RADIUS,0x14);
            put(a,TILE,assets.body(c.part).tileWord());
            mapping(a,CdR13BossAssets.Visual.CALF);
            put(a,BOSS_VX_LONG,0x8000);
            put(a,BOSS_FALL_Y,0x4000);
            return true;
        }
        if(r==14)return detached(c,0,-0x20000,bit(a,4)?0x600:-0x600,0x1A60);
        if(r==2){
            xy(a,parent(c,a),4,16);
            return true;
        }
        if(r==4){
            int other=next(c,next(c,parent(c,parent(c,a))));
            xy(a,other,-10,0);
            return true;
        }
        int dx=0,dy=0;
        boolean anchor=false;
        switch(r){
            case 6->{
                if(sw(a,BOSS_DX)<8){
                    dx=l(a,BOSS_VX_LONG);
                    add(a,BOSS_DX_LONG,dx);
                    if(bit(a,4)){
                        dx=-dx;
                        anchor=true;
                    }
                }
            }
            case 8->{
                put(a,BOSS_DY,0);
                if(sw(a,BOSS_DX)>-8){
                    dx=l(a,BOSS_VX_LONG);
                    add(a,BOSS_DX_LONG,-dx);
                    if(bit(a,4))anchor=true;
                }
            }
            case 10->{
                if(sw(a,BOSS_DX)>-8){
                    dx=l(a,BOSS_VX_LONG);
                    add(a,BOSS_DX_LONG,-dx);
                }
                if(sw(a,BOSS_DY)>-4){
                    dy=l(a,BOSS_FALL_Y);
                    add(a,BOSS_DY_LONG,-dy);
                }
                anchor=bit(a,4);
            }
            case 12->{
                if(sw(a,BOSS_DX)>-8){
                    dx=l(a,BOSS_VX_LONG);
                    add(a,BOSS_DX_LONG,-dx);
                }
                if(sw(a,BOSS_DY)<4){
                    dy=l(a,BOSS_FALL_Y);
                    add(a,BOSS_DY_LONG,dy);
                }
                if(bit(a,4)){
                    dy=-dy;
                    anchor=true;
                }
            }
            default->throw new AssertionError(r);
        }
        if(anchor){
            if(bit(a,5)){
                int p=a;
                for(int n=0;n<3;n++){
                    p=parent(c,p);
                    add(p,X_POSITION,dx);
                    add(p,Y_POSITION,dy);
                }
            }
            return true;
        }
        int p=parent(c,a);
        put(a,X,w(p,X)+4);
        add(a,X,w(a,BOSS_DX));
        put(a,Y,w(p,Y)+16);
        add(a,Y,w(a,BOSS_DY));
        return true;
    }
    private boolean foot(Cursor c,int r){
        int a=c.whole;
        if(r==0){
            initPart(c,0x20,0x14,CdR13BossAssets.Visual.FOOT);
            put(a,BOSS_VX_LONG,0x4000);
            put(a,BOSS_FALL_Y,0x8000);
            put(a,ROUTINE,4);
            set(a,0);
            put(a,BOSS_DX,-8);
            put(a,BOSS_DY,16);
            footPlace(c);
            return true;
        }
        if(r==12)return detached(c,0,-0x23000,bit(a,4)?0x660:-0x660,0x1660);
        switch(r){
            case 2->{
                add(a,BOSS_DX_LONG,-l(a,BOSS_VX_LONG));
                add(a,BOSS_DY_LONG,l(a,BOSS_FALL_Y));
                int limit=bit(a,4)&&bit(a,7)?12:16;
                if(sw(a,BOSS_DY)>=limit){
                    set(a,0);
                    put(a,ROUTINE,4);
                    break;
                }
                footMove(c);
                if(floor(c).distance()<=0)footContact(c);
            }
            case 4->{
                if(!bit(a,4)){
                    footPlace(c);
                    if(floor(c).distance()<=0)footContact(c);
                }
            }
            case 6->{
                add(a,BOSS_DX_LONG,l(a,BOSS_VX_LONG));
                add(a,BOSS_DY_LONG,-l(a,BOSS_FALL_Y));
                if(sw(a,BOSS_DY)<=0){
                    put(a,BOSS_DX,0);
                    put(a,BOSS_DX_FRACTION,0);
                    /* CLR.W var36, preserving no DX fraction */ put(a,BOSS_DY,0);
                    put(a,BOSS_DY_FRACTION,0);
                    /* CLR.W var3A */ set(a,0);
                    put(a,ROUTINE,8);
                }
                footMove(c);
            }
            case 8->footPlace(c);
            case 10->{
                int other=next(c,next(c,next(c,parent(c,parent(c,parent(c,a))))));
                xy(a,other,-10,0);
                put(a,BOSS_DX,w(other,BOSS_DX));
                put(a,BOSS_DY,w(other,BOSS_DY));
            }
            default->throw new AssertionError(r);
        }
        return true;
    }
    private void footPlace(Cursor c){
        int a=c.whole,p=parent(c,a);
        put(a,X,w(p,X)-11);
        add(a,X,w(a,BOSS_DX));
        put(a,Y,w(p,Y)+14);
        add(a,Y,w(a,BOSS_DY));
    }
    private void footMove(Cursor c){
        int a=c.whole;
        if(!bit(a,4)){
            footPlace(c);
            return;
        }
        if(!bit(a,5))return;
        int dx=l(a,BOSS_VX_LONG),dy=l(a,BOSS_FALL_Y);
        if(b(a,ROUTINE)!=6){
            dx=-dx;
            dy=-dy;
        }
        int p=a;
        for(int n=0;n<4;n++){
            p=parent(c,p);
            add(p,X_POSITION,-dx);
            add(p,Y_POSITION,dy);
        }
    }
    private void footContact(Cursor c){
        int a=c.whole;
        set(a,4);
        int p=parent(c,a);
        set(p,4);
        p=parent(c,p);
        set(p,4);
        p=parent(c,p);
        p=parent(c,p);
        put(p,BOSS_SHAKE,8);
        cpu(c,Site.FOOT_IMPACT,CpuCommand.FOOT_IMPACT);
    }
}
// Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
