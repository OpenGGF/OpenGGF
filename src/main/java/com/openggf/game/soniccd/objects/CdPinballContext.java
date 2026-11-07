package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.*;
import com.openggf.game.soniccd.player.*;
import java.util.*;

/** One constructed component invocation, never a World/pass/Frame or live publication authority.
 * Owns a same-owner staging copy of all96 slots and complete actor pair/environment values.
 * Completed Result/Prefix values are inert. Root must provide actual original source admission,
 * publication receipts and typed shared Draw/Despawn/Stand/GetOff/sound joins at their real sites.
 */
public final class CdPinballContext {
    public record Pair(CdPlayerBody.State primary,CdPlayerBody.Environment primaryEnvironment,
                       CdPlayerBody.State secondary,CdPlayerBody.Environment secondaryEnvironment) {
        public Pair {Objects.requireNonNull(primary);Objects.requireNonNull(primaryEnvironment);
            Objects.requireNonNull(secondary);Objects.requireNonNull(secondaryEnvironment);}
    }
    public record Input(CdObjectPool.Snapshot pool,Pair players,CdPlayerTouchResponse.WorldGlobals score,
                        CdAssetBytes savedFlags,int period,int goodFuture,int debugMode,int act) {
        public Input {Objects.requireNonNull(pool);Objects.requireNonNull(players);Objects.requireNonNull(score);
            Objects.requireNonNull(savedFlags);if(savedFlags.length()!=CdObjectPlacementRuntime.SAVED_BANK_BYTES)throw new IllegalArgumentException("Complete saved bank required");
            if((period&~255)!=0||(goodFuture&~255)!=0||(debugMode&~65535)!=0||(act&~255)!=0)throw new IllegalArgumentException("Native globals");}
    }
    public record Result(CdObjectPool.Snapshot pool,Pair players,CdPlayerTouchResponse.WorldGlobals score,
                         CdAssetBytes savedFlags,List<String> completed) {
        public Result {completed=List.copyOf(completed);}
    }
    /** Original synchronous calls, not replay of completed effects. There are no default success ports. */
    public interface Ports {
        void sound(Sound operation);
        void draw(Draw operation);
        void despawn(Despawn operation);
        void attachment(Attachment operation);
        void objectStore(ObjectStore operation);
        void playerStore(PlayerStore operation);
        void scoreStore(ScoreStore operation);
        void savedStore(SavedStore operation);
    }
    public enum SoundKind { FM, SUB_CPU }
    public enum AttachmentKind { STAND_ON_OBJECT, GET_OFF_OBJECT }
    public abstract class Operation {
        private boolean done;
        private Operation() { }
        final void current() {if(pending!=this||done||!running)throw new IllegalStateException("Stale, duplicate or foreign pinball operation");}
        final void finish(String label) {current();done=true;completed.add(label);}
        public final Result before() {current();return result();}
        public final void requireOwner(CdPinballContext owner){current();if(owner!=CdPinballContext.this)throw new IllegalArgumentException("Foreign pinball producer");}
    }
    public final class ObjectStore extends Operation {
        private final CdObjectState before,output;
        private ObjectStore(CdObjectState before,CdObjectState output){this.before=before;this.output=output;}
        public CdObjectState expected(){return before;}public Optional<CdObjectState> output(){return Optional.ofNullable(output);}
        public int wholeIndex(){return before.identity().wholeIndex();}
        public void applyNative(){current();if(!pool.state(wholeIndex()).equals(before))throw new IllegalStateException("Object cut changed");
            if(output==null)pool.deleteObject(wholeIndex());else pool.write(output);finish(output==null?"DELETE":"OBJECT_STORE");}
    }
    public final class PlayerStore extends Operation {
        private final CdPlayerBody.State before,output;
        private PlayerStore(CdPlayerBody.State before,CdPlayerBody.State output){this.before=before;this.output=output;}
        public CdPlayerBody.State expected(){return before;}public CdPlayerBody.State output(){return output;}
        public void applyNative(){current();if(actor!=before)throw new IllegalStateException("Actor cut changed");actor=output;finish("PLAYER_STORE");}
    }
    public final class ScoreStore extends Operation {
        private final CdPlayerTouchResponse.WorldGlobals before,output;
        private ScoreStore(CdPlayerTouchResponse.WorldGlobals output){before=score;this.output=output;}
        public CdPlayerTouchResponse.WorldGlobals expected(){return before;}public CdPlayerTouchResponse.WorldGlobals output(){return output;}
        public void applyNative(){current();if(score!=before)throw new IllegalStateException("Score cut changed");score=output;finish("ADD_POINTS:10");}
    }
    public enum SavedKind { BUMPER_LINEAR_COUNTER, DESPAWN_PERIOD_LOADED }
    public final class SavedStore extends Operation {
        private final SavedKind kind;private final int id,period,at,before,output;
        private SavedStore(SavedKind kind,int id,int period,int at,int output){this.kind=kind;this.id=id;this.period=period;this.at=at;
            this.before=Byte.toUnsignedInt(saved[at]);this.output=output&255;}
        public SavedKind kind(){return kind;}public int savedId(){return id;}public int period(){return period;}
        public int expectedByte(){return before;}public int outputByte(){return output;}
        public void applyNative(){current();if(Byte.toUnsignedInt(saved[at])!=before)throw new IllegalStateException("Saved cut changed");
            saved[at]=(byte)output;finish(kind.name());}
    }
    public final class Sound extends Operation {
        private final SoundKind kind;private final int nativeId;
        private Sound(SoundKind kind,int id){this.kind=kind;this.nativeId=id;}
        public SoundKind kind(){return kind;}public int nativeId(){return nativeId;}
        public void completedNative(){finish(kind+":"+Integer.toHexString(nativeId));}
    }
    public final class Draw extends Operation {
        private final CdObjectState before;
        private Draw(CdObjectState before){this.before=before;}
        public CdObjectState sourceBeforeBit7Clear(){return before;}
        public int wholeIndex(){return whole;}
        public void completedNative(){finish("DRAW");}
    }
    public final class Despawn extends Operation {
        private final int x;private final boolean anchor;private boolean delete;
        private Despawn(int x,boolean anchor){this.x=x;this.anchor=anchor;}
        public int xWord(){return x;}public boolean usesSavedAnchor(){return anchor;}
        public int wholeIndex(){return whole;}
        /** Shared CheckObjDespawn/2 has made its real source predicate decision. */
        public void completedNative(boolean delete){
            current();this.delete=delete;
            finish("DESPAWN:"+delete);
        }
    }
    public final class Attachment extends Operation {
        private final AttachmentKind kind;
        private Attachment(AttachmentKind kind){this.kind=kind;}
        public AttachmentKind kind(){return kind;}public CdObjectState object(){current();return pool.state(whole);}
        public CdPlayerBody.State player(){current();return actor;}
        /** The existing shared Solid owner supplies its complete result; no generic field-write port. */
        public void completedNative(CdObjectState object,CdPlayerBody.State player,List<CdObjectState> detached){
            current();if(object.identity().wholeIndex()!=whole)throw new IllegalArgumentException("Attachment slot changed");
            Objects.requireNonNull(player);detached=List.copyOf(detached);pool.validateWrite(object);
            if(detached.size()>1)throw new IllegalArgumentException("Stand releases at most one old physical parent");
            for(var old:detached){pool.validateWrite(old);int index=old.identity().wholeIndex();
                if(kind!=AttachmentKind.STAND_ON_OBJECT||index!=actor.objectLinks().standObject()||index==whole)
                    throw new IllegalArgumentException("Not the native previous stand parent");
                var expected=pool.state(index).moveByte(CdObjectState.ByteField.FLAGS,pool.state(index).flags()&~8);
                if(!old.equals(expected))throw new IllegalArgumentException("Old parent may only clear stand bit");}
            pool.write(object);for(var old:detached)pool.write(old);actor=player;finish(kind.name());
        }
    }
    private final Input input;final CdR31PinballAssets assets;final CdPlayerMath math;
    private final Ports ports;private final CdObjectPool pool;private final byte[] saved;
    private CdPlayerBody.State actor;private CdPlayerTouchResponse.WorldGlobals score;
    private final List<String> completed=new ArrayList<>();private Operation pending;private boolean running,used;private int whole;
    public CdPinballContext(Input input,CdR31PinballAssets assets,CdPlayerMath math,Ports ports){
        this.input=Objects.requireNonNull(input);this.assets=Objects.requireNonNull(assets);this.math=Objects.requireNonNull(math);
        this.ports=Objects.requireNonNull(ports);pool=CdObjectPool.stagingCopy(input.pool());saved=input.savedFlags().copy();
        actor=input.players().primary();score=input.score();
    }
    /** Exact completed stopped prefix, also available after a synchronous port/read failure. Not retry authority. */
    public Result result(){return new Result(pool.capture(),new Pair(actor,input.players().primaryEnvironment(),
            input.players().secondary(),input.players().secondaryEnvironment()),score,CdAssetBytes.copyOf(saved),completed);}
    Result run(int index,int id,Runnable body){
        if(used)throw new IllegalStateException("One component invocation per context");used=true;whole=index;
        if(pool.state(index).id()!=id)throw new IllegalArgumentException("Native dispatch ID differs");running=true;
        try{body.run();return result();}finally{running=false;pending=null;}
    }
    private void call(Operation op,Runnable invoke){
        if(!running||pending!=null)throw new IllegalStateException("Nested pinball call");pending=op;
        try{invoke.run();if(!op.done)throw new IllegalStateException("Original operation did not complete");}
        finally{pending=null;}
    }
    CdPinballImage image(){return new CdPinballImage(pool.state(whole));}
    void put(CdPinballImage image){store(image.state());}
    private void store(CdObjectState output){var op=new ObjectStore(pool.state(output.identity().wholeIndex()),output);call(op,()->ports.objectStore(op));}
    private void player(CdPlayerBody.State output){var op=new PlayerStore(actor,output);call(op,()->ports.playerStore(op));}
    private void saved(SavedKind kind,int id,int period,int at,int value){var op=new SavedStore(kind,id,period,at,value);call(op,()->ports.savedStore(op));}
    CdPlayerMotionState motion(){return actor.motion();}
    void motion(CdPlayerMotionState m){player(new CdPlayerBody.State(m,actor.animation(),actor.header(),actor.timers(),actor.history(),
            actor.rotationDistance(),actor.objectLinks(),actor.rotationAngle()));}
    // Exact finite actor stores; all unmentioned motion/body/environment/pair fields are retained.
    void launch(int xv,int yv,boolean clearJump){var m=motion();motion(copy(m,m.centreX16_16(),m.centreY16_16(),xv,yv,m.groundVelocity(),
            (m.flags()|2)&~0x30,m.xRadius(),m.yRadius(),m.animation(),clearJump?0:m.playerJump(),m.playerControl()));}
    void velocities(int xv,int yv,int gv){var m=motion();motion(copy(m,m.centreX16_16(),m.centreY16_16(),xv,yv,gv,m.flags(),
            m.xRadius(),m.yRadius(),m.animation(),m.playerJump(),m.playerControl()));}
    void playerY(int word){var m=motion();motion(copy(m,m.centreX16_16(),(word<<16)|(m.centreY16_16()&65535),m.xVelocity(),m.yVelocity(),
            m.groundVelocity(),m.flags(),m.xRadius(),m.yRadius(),m.animation(),m.playerJump(),m.playerControl()));}
    void capturePlayer(int x,int y){var m=motion();motion(copy(m,(x<<16)|(m.centreX16_16()&65535),(y<<16)|(m.centreY16_16()&65535),
            0,-0x800,m.groundVelocity(),m.flags()|4,7,14,2,m.playerJump(),m.playerControl()|1));}
    void control(int set,int clear){var m=motion();motion(copy(m,m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),
            m.groundVelocity(),m.flags(),m.xRadius(),m.yRadius(),m.animation(),m.playerJump(),(m.playerControl()|set)&~clear));}
    private static CdPlayerMotionState copy(CdPlayerMotionState m,int x,int y,int xv,int yv,int gv,int flags,int xr,int yr,int anim,int jump,int ctrl){
        return new CdPlayerMotionState(x,y,(short)xv,(short)yv,(short)gv,m.routine(),m.angle(),flags,m.spriteFlags(),xr,yr,anim,
                m.previousAnimation(),jump,m.charge(),m.moveLock(),m.stick(),ctrl,m.previousPrimaryAngle(),m.previousSecondaryAngle());}
    int playerX(){return motion().centreX16_16()>>>16;}int playerY(){return motion().centreY16_16()>>>16;}
    int period(){return input.period();}int goodFuture(){return input.goodFuture();}int debug(){return input.debugMode();}int act(){return input.act();}
    void sound(SoundKind kind,int id){var op=new Sound(kind,id);call(op,()->ports.sound(op));}
    void draw(){var before=pool.state(whole);var image=image();image.b(1,image.b(1)&127);put(image);
        var op=new Draw(before);call(op,()->ports.draw(op));}
    void despawn(int x,boolean anchor){var op=new Despawn(x,anchor);call(op,()->ports.despawn(op));
        if(op.delete){int id=image().b(0x23);if(id!=0){int period=despawnPeriod();int at=2+3*id+period;Objects.checkIndex(at,saved.length);
            saved(SavedKind.DESPAWN_PERIOD_LOADED,id,period,at,Byte.toUnsignedInt(saved[at])&127);}delete();}}
    // common/object_spawn.asm:DespawnObject..DespawnObjectClearState, actual raw warp selection.
    private int despawnPeriod(){int period=input.period();if((period&128)==0)return period;
        int selected=(period&127)-(byte)input.players().primaryEnvironment().movement().gates().timeWarpDirection();
        return Math.max(0,Math.min(2,selected));}
    void attachment(AttachmentKind kind){var op=new Attachment(kind);call(op,()->ports.attachment(op));}
    void delete(){var op=new ObjectStore(pool.state(whole),null);call(op,()->ports.objectStore(op));}
    void points(){var result=CdPlayerTouchResponse.addPoints(score,10);var op=new ScoreStore(result.world());call(op,()->ports.scoreStore(op));
        if(result.command().isPresent())sound(SoundKind.SUB_CPU,0x7a);
        var free=pool.findObjSlot();completed.add("FIND_POINTS:"+(free.isPresent()?free.getAsInt():"FULL"));
        if(free.isPresent()){var parent=image();var child=new CdPinballImage(pool.state(free.getAsInt()));
            child.b(0,0x1c);child.w(8,parent.w(8));child.w(12,parent.w(12));child.b(0x28,0x80);store(child.state());}}
    void incrementBumperSave(){int id=image().b(0x23);if(id!=0){int at=2+id;if(Byte.toUnsignedInt(saved[at])<0x8a)saved(SavedKind.BUMPER_LINEAR_COUNTER,id,-1,at,Byte.toUnsignedInt(saved[at])+1);}}
    boolean pocketChild(){var free=pool.findObjSlot();completed.add("FIND_POCKET:"+(free.isPresent()?free.getAsInt():"FULL"));
        if(free.isEmpty())return false;int childIndex=free.getAsInt();var p=image();var child=new CdPinballImage(pool.state(childIndex));
        p.w(0x2c,child.state().identity().nativeAddress24());put(p);
        child.w(0x2e,p.state().identity().nativeAddress24());child.b(0,p.b(0));child.w(8,p.w(8));child.w(12,p.w(12));
        store(child.state());return true;}
    int linkedIndex(int lowAddress){int low=lowAddress&65535;int delta=low-0xd000;
        if((delta&63)!=0||delta<32*64||delta>=128*64)throw new UnsupportedOperationException("Reached unowned pocket link $"+Integer.toHexString(low));
        return delta/64;}
    int parentId(int lowAddress){return pool.state(linkedIndex(lowAddress)).id();}
    void releaseChild(int lowAddress){int index=linkedIndex(lowAddress);var child=new CdPinballImage(pool.state(index));
        // Native writes routine4 even when the old child was deleted/reused. No generation repair.
        child.b(0x24,4);store(child.state());}
    void animate(CdR31PinballAssets.Visual visual){var i=image();
        var a=CdObjectAnimation.step(new CdObjectAnimation.State(i.b(0x1c),i.b(0x1d),i.b(0x1b),i.b(0x1e),i.b(0x1a),i.b(1),i.b(0x22),i.b(0x24),i.b(0x25)),
                visual.animation().orElseThrow());
        i.b(0x1c,a.animationId());i.b(0x1d,a.previousId());i.b(0x1b,a.scriptIndex());i.b(0x1e,a.timer());i.b(0x1a,a.mapFrame());
        i.b(1,a.spriteFlags());i.b(0x24,a.routine());i.b(0x25,a.routine2());put(i);completed.add("ANIMATE");}
    static boolean within(int delta,int add,int extent){return ((delta+add)&65535)<extent;}
    static void routine(CdPinballImage i,int... accepted){for(int n:accepted)if(i.b(0x24)==n)return;throw new UnsupportedOperationException("Native pinball routine "+i.b(0x24));}
    static void move(CdPinballImage i,boolean skipZero){int velocity=i.l(0x2a);if(skipZero&&velocity==0)return;
        int at=(short)i.w(0x28)<0?8:12;i.l(at,i.l(at)+velocity);i.w(0x2e,i.w(0x2e)-1);
        if(i.w(0x2e)==0){i.w(0x2e,i.w(0x30));i.l(0x2a,-velocity);}}
}
