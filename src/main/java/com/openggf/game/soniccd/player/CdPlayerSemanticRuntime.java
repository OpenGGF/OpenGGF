package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.level.CdStagePlayerSource;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import static com.openggf.game.soniccd.player.CdR4WaterAirPorts.*;

/**
 * Synchronous original-programme component binding. This is NOT Module/World admission.
 * Frame must implement Ports privately under its real current original source, thread,
 * generation and operation scope. A deliberately constructed component harness is also
 * possible; neither this facade, DATA, captures nor equal values mint a Frame invocation.
 * There is no issued continuation, restore, reopen, action replay or arbitrary scheduler.
 * N1ecc5f3 src/objects/player.asm:281-343,430-489,2206-2222.
 */
public final class CdPlayerSemanticRuntime {
    public interface Ports {
        long generation();
        /** Validate private source/scope AND complete current State+Environment; before/after
         * EVERY primitive. A post-store fault retains the actual store. Never supply defaults. */
        void requireCurrent(Call call);
        CdPlayerBody.State state(Call call);
        CdPlayerBody.Environment environment(Call call);
        /** Compare the complete actual preimage, then adopt a completed shared-algorithm prefix
         * once. No effects are passed, reconstructed, or replayed. On refusal change nothing;
         * on a later fault keep every actual completed store. */
        void publish(Call call, CdPlayerBody.State before, CdPlayerBody.Environment beforeEnvironment,
                     CdPlayerBody.State next, CdPlayerBody.Environment nextEnvironment);
        Optional<CdPlayerMovement.ObjectContext> objects(Call call);
        void flipperAnimation(Call call,CdPlayerMovement.SlotPatch patch);
        /** Private live leaf adapter: actual primary, reserved7, full96, water/global/RNG/sound
         * owners. Each write/call must validate its exact primitive preimage and postimage.
         * Returned Slot identities remain private to this synchronous occurrence. */
        Calls water(Call call);
        void draw(Call call);
        void sound(Call call, int nativeId);
        void restoreStageMusic(Call call);
        /** Bind the existing source-selected scanner/response algorithms; no empty-pool default. */
        void collideObjects(Call call);
        /** Actual current shared TimeWarp/reserved/save owner; no capture or equal Result grant. */
        void timeWarp(Call call);
        /** Synchronous adapters to the EXISTING shared charge algorithms, at their original sites.
         * Publish their actual writes and sound at source order inside the real owner, not by replay
         * of the returned Result. The result establishes full state consistency only. No default
         * implementation exists: an unbound charge service fails at this reached invocation. */
        CdPlayerCharge.Result moveGroundCharge(Call call,CdPlayerCharge.State before,CdPlayerCharge.Input input);
        CdPlayerCharge.Result moveRollCharge(Call call,CdPlayerCharge.State before,CdPlayerCharge.Input input);
        CdPlayerCharge.Result startRoll(Call call,CdPlayerCharge.State before,CdPlayerCharge.Body body);
        /** Actual current level/ring/placement cut; no absent-secondary or saved-state defaults. */
        CdPlayerBody.LevelContext level(Call call);
        void storeLevel(Call call,CdPlayerBody.LevelField field,int value);
        /** Actual full96 allocation + ID store. Exhausted unchecked alias must stop at its
         * genuine unowned store, never fabricate another slot or clear dirty storage. */
        void spawnGameOver(Call call);
        /** Semantic audio-driver flag write; no Z80/CPU/bus execution is modeled. */
        void restartSoundFlag(Call call);
        void resetSavedObjectFlags(Call call);
        /** Notification only; no returned DTO grants Frame, Art, loading or service authority. */
        void completed(Call call, CdPlayerBody.Boundary boundary);
    }

    /** Optional derived completion observation for Frame capture. This is not Draw, a
     * returned Result/effect publisher or authority to replay the native callback. */
    public interface CompletionObservation {
        void displayValue(Call call,Optional<CdPlayerBody.DisplayPublication> exactBodyValue);
    }

    private final CdStagePlayerSource source;
    private final Ports ports;
    private Call current;
    private boolean faulted;

    private CdPlayerSemanticRuntime(CdStagePlayerSource source, Ports ports) {
        this.source=Objects.requireNonNull(source,"actual original source");
        this.ports=Objects.requireNonNull(ports,"actual current semantic ports");
        if(source.player().sourceGraph()!=CdR11aPlayerAssets.SourceGraph.R4)
            throw new IllegalArgumentException("Original semantic binding requires actual R4 player data");
    }
    public static CdPlayerSemanticRuntime fromOriginalProgramme(CdStagePlayerSource source, Ports ports) {
        return new CdPlayerSemanticRuntime(source,ports);
    }
    void requireSource(CdStagePlayerSource actual) {
        if(actual!=source)throw new IllegalArgumentException("Different retained original player source");
    }
    void run(CdPlayerBody body) {
        if(current!=null||faulted)throw new IllegalStateException("In-flight or faulted original player binding");
        Call call=new Call(this,Thread.currentThread(),ports.generation());current=call;
        try {
            ports.requireCurrent(call);
            call.state=Objects.requireNonNull(ports.state(call));
            call.environment=Objects.requireNonNull(ports.environment(call));
            call.check();
            body.runOriginalProgramme(call);
            call.check();
        } catch(RuntimeException|Error first) {
            // Keep the original throwable, including callback identity; guard failures are secondary.
            try { call.check(); } catch(RuntimeException|Error guard) { suppress(first,guard); }
            faulted=true;throw first;
        } finally { call.open=false;current=null; }
    }
    static void suppress(Throwable first,Throwable later) {
        if(first==later)return;
        for(Throwable old:first.getSuppressed())if(old==later)return;
        first.addSuppressed(later);
    }

    /** Private-issued observation of one synchronous component occurrence. No public factory. */
    public static final class Call {
        private final CdPlayerSemanticRuntime runtime;
        private final Thread thread;
        private final long generation;
        private boolean open=true,inPrimitive,failed;
        private CdPlayerBody.State state;
        private CdPlayerBody.Environment environment;
        private Call(CdPlayerSemanticRuntime runtime,Thread thread,long generation) {
            this.runtime=runtime;this.thread=thread;this.generation=generation;
        }
        public void requireCurrent(CdPlayerSemanticRuntime actual) {
            if(actual!=runtime||!open||runtime.current!=this||Thread.currentThread()!=thread
                    ||runtime.ports.generation()!=generation)
                throw new IllegalStateException("Foreign, stale, closed or off-thread player occurrence");
        }
        public CdStagePlayerSource source() { requireCurrent(runtime);return runtime.source; }
        private void check() {
            requireCurrent(runtime);runtime.ports.requireCurrent(this);
            if(state!=null&&(!state.equals(runtime.ports.state(this))
                    ||!environment.equals(runtime.ports.environment(this))))
                throw new IllegalStateException("Complete current player image changed outside its primitive");
            requireCurrent(runtime);
        }
        private <T>T primitive(Supplier<T> operation,boolean mayWrite) {
            if(inPrimitive||failed)throw new IllegalStateException("Reentrant or failed semantic primitive");
            check();inPrimitive=true;
            try {
                T value=operation.get();
                requireCurrent(runtime);runtime.ports.requireCurrent(this);
                var next=Objects.requireNonNull(runtime.ports.state(this));
                var nextEnvironment=Objects.requireNonNull(runtime.ports.environment(this));
                if(!mayWrite&&(!state.equals(next)||!environment.equals(nextEnvironment)))
                    throw new IllegalStateException("Read-only primitive changed the complete player image");
                state=next;environment=nextEnvironment;check();return value;
            } catch(RuntimeException|Error first) {
                failed=true;
                // A writer may already have completed. Refresh its actual prefix even when it throws;
                // do not restore the preimage, call the writer again, or replace its throwable.
                try {
                    requireCurrent(runtime);runtime.ports.requireCurrent(this);
                    state=Objects.requireNonNull(runtime.ports.state(this));
                    environment=Objects.requireNonNull(runtime.ports.environment(this));
                } catch(RuntimeException|Error guard) { suppress(first,guard); }
                throw first;
            } finally { inPrimitive=false; }
        }
        private void write(Runnable operation) {primitive(()->{operation.run();return null;},true);}
        CdPlayerBody.State state(){check();return state;}
        CdPlayerBody.Environment environment(){check();return environment;}
        void publishMotionFailure(Runnable publish,Throwable first){
            if(failed)return;
            try{publish.run();}catch(RuntimeException|Error later){suppress(first,later);}
        }
        void rejectCurrent(){failed=true;}
        void publishAlgorithmFailure(CdPlayerBody.State next,CdPlayerBody.Environment nextEnvironment,Throwable first) {
            // Mutable leaf failures already retained their actual prefix. Stale pure cursors MUST
            // never overwrite it. Only an algorithm's own pre-primitive prefix may be adopted here.
            if(failed)return;
            try{publish(next,nextEnvironment);}catch(RuntimeException|Error later){suppress(first,later);}
        }
        void publish(CdPlayerBody.State next,CdPlayerBody.Environment nextEnvironment) {
            Objects.requireNonNull(next);Objects.requireNonNull(nextEnvironment);check();
            var before=state;var beforeEnvironment=environment;
            write(()->runtime.ports.publish(this,before,beforeEnvironment,next,nextEnvironment));
            if(!state.equals(next)||!environment.equals(nextEnvironment)){
                failed=true;throw new IllegalStateException("Completed prefix was not published exactly");
            }
        }
        Optional<CdPlayerMovement.ObjectContext> objects(){return primitive(()->Objects.requireNonNull(runtime.ports.objects(this)),false);}
        void draw(){
            check();var before=state;var beforeEnvironment=environment;var m=before.motion();
            // DrawObject (src/common/object.asm:100-159, N1ecc5f3) clears only sprite bit7
            // before culling/capacity. Setting it belongs to the later DrawObjects drain.
            var motion=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
                    m.routine(),m.angle(),m.flags(),m.spriteFlags()&127,m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
                    m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
            var cleared=new CdPlayerBody.State(motion,before.animation(),before.header(),before.timers(),before.history(),
                    before.rotationDistance(),before.objectLinks(),before.rotationAngle());
            primitive(()->{
                try{runtime.ports.draw(this);}
                catch(RuntimeException|Error first){
                    // A refused draw may precede the clear; a later fault keeps its actual clear.
                    try{requireDrawPostimage(before,cleared,beforeEnvironment,false);}
                    catch(RuntimeException|Error guard){suppress(first,guard);}throw first;
                }
                requireDrawPostimage(before,cleared,beforeEnvironment,true);return null;
            },true);
        }
        private void requireDrawPostimage(CdPlayerBody.State before,CdPlayerBody.State cleared,
                CdPlayerBody.Environment beforeEnvironment,boolean returned){
            requireCurrent(runtime);runtime.ports.requireCurrent(this);
            var actual=Objects.requireNonNull(runtime.ports.state(this));
            var actualEnvironment=Objects.requireNonNull(runtime.ports.environment(this));
            if(!beforeEnvironment.equals(actualEnvironment)
                    ||(!cleared.equals(actual)&&(returned||!before.equals(actual))))
                throw new IllegalStateException("DrawObject changed fields outside its native sprite bit7 clear");
        }
        void sound(int nativeId){primitive(()->{runtime.ports.sound(this,nativeId);return null;},false);}
        void stageMusic(){primitive(()->{runtime.ports.restoreStageMusic(this);return null;},false);}
        void collideObjects(){write(()->runtime.ports.collideObjects(this));}
        void timeWarp(){
            var before=state;write(()->runtime.ports.timeWarp(this));
            if(!before.equals(state)){failed=true;throw new IllegalStateException("TimeWarp wrote the primary actor");}
        }
        void flipper(CdPlayerMovement.SlotPatch patch){write(()->runtime.ports.flipperAnimation(this,patch));}
        void completed(CdPlayerBody.Boundary boundary){primitive(()->{runtime.ports.completed(this,boundary);return null;},false);}
        void completed(CdPlayerBody.Boundary boundary,Optional<CdPlayerBody.DisplayPublication> exactBodyValue){
            Objects.requireNonNull(exactBodyValue);
            if(runtime.ports instanceof CompletionObservation observer)
                primitive(()->{observer.displayValue(this,exactBodyValue);return null;},false);
            completed(boundary);
        }
        CdPlayerCharge.Result moveGroundCharge(CdPlayerCharge.State before,CdPlayerCharge.Input input){
            return charge(before,()->runtime.ports.moveGroundCharge(this,before,input));
        }
        CdPlayerCharge.Result moveRollCharge(CdPlayerCharge.State before,CdPlayerCharge.Input input){
            return charge(before,()->runtime.ports.moveRollCharge(this,before,input));
        }
        CdPlayerCharge.Result startRoll(CdPlayerCharge.State before,CdPlayerCharge.Body body){
            return charge(before,()->runtime.ports.startRoll(this,before,body));
        }
        private CdPlayerCharge.Result charge(CdPlayerCharge.State supplied,Supplier<CdPlayerCharge.Result> operation){
            check();var initial=state;var initialEnvironment=environment;
            var m=initial.motion();var e=initialEnvironment.movement();var g=e.globals();
            var actualBefore=new CdPlayerCharge.State(m.charge(),m.groundVelocity(),m.xVelocity(),m.yVelocity(),m.flags(),
                    m.xRadius(),m.yRadius(),m.centreY16_16(),m.animation(),g.lookMode(),g.cameraYCentre());
            if(!actualBefore.equals(supplied))throw new IllegalArgumentException("Stale current charge operands");
            var result=primitive(()->Objects.requireNonNull(operation.get()),true);var r=result.state();
            var expectedMotion=new CdPlayerMotionState(m.centreX16_16(),r.centreY16_16(),r.storedXVelocity(),r.storedYVelocity(),
                    r.groundVelocity(),m.routine(),m.angle(),r.flags(),m.spriteFlags(),r.xRadius(),r.yRadius(),r.animation(),
                    m.previousAnimation(),m.playerJump(),r.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
            var expected=new CdPlayerBody.State(expectedMotion,initial.animation(),initial.header(),initial.timers(),initial.history(),
                    initial.rotationDistance(),initial.objectLinks(),initial.rotationAngle());
            var expectedEnvironment=new CdPlayerBody.Environment(new CdPlayerMovement.Environment(e.physics(),e.bounds(),e.gates(),
                    new CdPlayerMovement.Globals(r.lookMode(),r.cameraYCentre(),g.primaryAngle(),g.secondaryAngle(),g.boredTimer(),g.scoreChain(),
                        g.debugAngle(),g.debugAngleShift(),g.debugQuadrant(),g.debugFloorDistance())),
                    initialEnvironment.controls(),initialEnvironment.globals(),initialEnvironment.dynamicSlots());
            if(!expected.equals(state)||!expectedEnvironment.equals(environment)){
                failed=true;throw new IllegalStateException("Charge return differs from its actual complete publication");
            }
            return result; // Consistency only; the actual synchronous owner call already occurred.
        }
        CdPlayerBody.LevelContext level(){return primitive(()->Objects.requireNonNull(runtime.ports.level(this)),false);}
        void level(CdPlayerBody.LevelField field,int value){write(()->runtime.ports.storeLevel(this,field,value));}
        void spawnGameOver(){write(()->runtime.ports.spawnGameOver(this));}
        void restartSoundFlag(){write(()->runtime.ports.restartSoundFlag(this));}
        void resetSavedObjectFlags(){write(()->runtime.ports.resetSavedObjectFlags(this));}
        void water(){
            Calls actual=primitive(()->Objects.requireNonNull(runtime.ports.water(this)),false);
            try{CdR4PlayerWater.check(new GuardedCalls(actual));}
            catch(RuntimeException|Error first){failed=true;throw first;}
        }
        /** Exact existing leaf interface, wrapped per reached primitive, never a captured water image. */
        private final class GuardedCalls implements Calls {
            private final Calls actual;
            GuardedCalls(Calls actual){this.actual=actual;}
            private Slot slot(Slot original){return new GuardedSlot(Objects.requireNonNull(original));}
            private Slot unwrap(Slot supplied){
                if(!(supplied instanceof GuardedSlot guarded)||guarded.call()!=Call.this)
                    throw new IllegalArgumentException("Foreign water slot");
                return guarded.actual;
            }
            public int read(Value f){
                int value=primitive(()->actual.read(f),false);
                var gates=environment.movement().gates();
                int expected=switch(f){
                    case ZONE->runtime.source.nativeZoneActWord()>>>8;
                    case ACT->runtime.source.nativeZoneActWord()&255;
                    case ZONE_WORD->gates.zoneAct();case TIME_ZONE->gates.timeZone();
                    case INVINCIBLE->environment.globals().invincible();
                    case MAX_SPEED->environment.movement().physics().topSpeed()&65535;
                    case ACCELERATION->environment.movement().physics().acceleration()&65535;
                    case DECELERATION->environment.movement().physics().deceleration()&65535;
                    case HELD->environment.controls().logicalHeld();
                    case DEBUG_MODE->environment.globals().debugMode();
                    case WATER_CURRENT->gates.windTunnel();
                    case STAGE_FRAMES->environment.globals().levelFrames();
                    case STAGE_FRAMES_LOW->environment.globals().levelFrames()&255;
                    default->value; // Remaining operands belong to actual extra World storage.
                };
                if(value!=expected)throw new IllegalStateException("Inconsistent current water operand "+f);
                return value;
            }
            public void write(Value f,int v){Call.this.write(()->actual.write(f,v));}
            public Slot player(){return slot(primitive(actual::player,false));}
            public Slot reservedBubbles(){return slot(primitive(actual::reservedBubbles,false));}
            public Optional<Slot> spawn(){return primitive(actual::spawn,true).map(this::slot);}
            public int random(){return primitive(actual::random,true);}
            public void sound(int id){Call.this.write(()->actual.sound(id));}
            public void music(int id){Call.this.write(()->actual.music(id));}
            public CdPlayerMath.SineCosine sineCosine(int angle){return primitive(()->actual.sineCosine(angle),false);}
            public void setPlayerGround(){Call.this.write(actual::setPlayerGround);}
            public void move(Slot s){var a=unwrap(s);Call.this.write(()->actual.move(a));}
            public void animate(Slot s,Animation a){var original=unwrap(s);Call.this.write(()->actual.animate(original,a));}
            public void draw(Slot s){var a=unwrap(s);Call.this.write(()->actual.draw(a));}
            public void delete(Slot s){var a=unwrap(s);Call.this.write(()->actual.delete(a));}
        }
        private final class GuardedSlot implements Slot {
            private final Slot actual;
            GuardedSlot(Slot actual){this.actual=actual;}
            Call call(){return Call.this;}
            public int read(B f){return primitive(()->actual.read(f),false);}
            public int read(W f){return primitive(()->actual.read(f),false);}
            public int read(L f){return primitive(()->actual.read(f),false);}
            public void write(B f,int v){Call.this.write(()->actual.write(f,v));}
            public void write(W f,int v){Call.this.write(()->actual.write(f,v));}
            public void write(L f,int v){Call.this.write(()->actual.write(f,v));}
        }
    }
}
