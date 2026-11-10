package com.openggf.game.soniccd.player;

import com.openggf.game.soniccd.objects.CdReservedObjectBank;
import com.openggf.game.soniccd.objects.CdTimeWarpSaveData;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Shared ObjSonic_TimeWarp / MakeTimeWarpStars, detached outputs and opt-in native owners.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/Level/_Objects/Sonic/Main.asm:112-128,573-669; actual reserved star identities
 * are whole12..15 (src/Level/_Definitions.inc:131-146).
 *
 * <p>The caller supplies the actual call-cut values after the one ObjSonic
 * predispatch charge operation and before the originally selected MODE resumes.
 * This owner does not tick a timer, run MODE, animate stars, write a live bank or
 * publish Frame/contact state. V-int timer advancement remains with Frame.
 * Stores and reserved patches describe staged work in native order. Apply final
 * values once through the caller's real owners; effects confer no replay or
 * invocation authority. A stopped prefix is descriptive, never a completed live
 * publication. Pure/legacy entries retain their explicit missing-owner stops. The named
 * active entry calls the real owner at each reached save/restart site, with no effect replay.
 */
public final class CdPlayerTimeWarp {
    private CdPlayerTimeWarp() { }

    /** Raw native bit patterns; ground velocity is the unsigned WORD, not a Java signed value. */
    public record Input(int zoneAct, int timeZone, int xWord, int charge, int groundVelocityWord,
                        int direction, int timer, int timeWarp) {
        public Input {
            word(zoneAct); rawByte(timeZone); word(xWord); rawByte(charge);
            word(groundVelocityWord); rawByte(direction); word(timer); rawByte(timeWarp);
        }
    }

    /** Borrowed only. Missing values are inspected at their reached native reads, never defaulted. */
    public record Context(OptionalInt spawnMode, Optional<CdReservedObjectBank.Snapshot> reserved,
                          Optional<SaveContext> saveContext, OptionalInt levelRestartWord) {
        public Context(OptionalInt spawnMode, Optional<CdReservedObjectBank.Snapshot> reserved) {
            this(spawnMode, reserved, Optional.empty(), OptionalInt.empty());
        }
        public Context(OptionalInt spawnMode, Optional<CdReservedObjectBank.Snapshot> reserved, Optional<SaveContext> saveContext) {
            this(spawnMode, reserved, saveContext, OptionalInt.empty());
        }
        public Context {
            Objects.requireNonNull(spawnMode); Objects.requireNonNull(reserved); Objects.requireNonNull(saveContext);
            Objects.requireNonNull(levelRestartWord);
            levelRestartWord.ifPresent(CdPlayerTimeWarp::word);
        }
    }

    /** Ephemeral primitive reads at the reached save cut; no live owner is retained by a result. */
    public interface SaveReads {
        int readScrollLockWord();
        int read(CdTimeWarpSaveData.Source source);
    }

    public record SaveContext(CdTimeWarpSaveData.State initial, SaveReads reads) {
        public SaveContext { Objects.requireNonNull(initial); Objects.requireNonNull(reads); }
    }

    public sealed interface SaveEffect permits LockWrite, PeriodWrite, SavedWrite, SpawnWrite, SubCpuRequest { }
    public record LockWrite(int expected, int value) implements SaveEffect {
        public LockWrite { word(expected); word(value); }
    }
    public record PeriodWrite(int expected, int value) implements SaveEffect {
        public PeriodWrite { rawByte(expected); rawByte(value); }
    }
    public record SavedWrite(CdTimeWarpSaveData.StoreCall call) implements SaveEffect {
        public SavedWrite { Objects.requireNonNull(call); }
    }
    public record SpawnWrite(int expected, int value) implements SaveEffect {
        public SpawnWrite { rawByte(expected); rawByte(value); }
    }
    /** Main.asm:619-620; semantic SubCPU request only, never playback or a bus operation. */
    public enum SubCpuCommand { SCMD_FADEPCM }
    public record SubCpuRequest(SubCpuCommand command) implements SaveEffect {
        public SubCpuRequest { Objects.requireNonNull(command); }
    }

    /** Completed immutable value/metadata; no port, Frame, invocation, replay or resume authority. */
    public record SaveResult(int expectedScrollLockWord, int finalScrollLockWord,
                             int expectedPeriodByte, int finalPeriodByte,
                             int expectedSpawnByte, int finalSpawnByte,
                             CdTimeWarpSaveData.State expectedSavedState, CdTimeWarpSaveData.State finalSavedState,
                             List<SaveEffect> effects) {
        public SaveResult {
            word(expectedScrollLockWord); word(finalScrollLockWord);
            rawByte(expectedPeriodByte); rawByte(finalPeriodByte); rawByte(expectedSpawnByte); rawByte(finalSpawnByte);
            Objects.requireNonNull(expectedSavedState); Objects.requireNonNull(finalSavedState);
            effects = List.copyOf(effects);
        }
    }


    /** Native timer230 MOVE.B to the high byte; immutable metadata, never live publication authority. */
    public record RestartResult(int expectedRestartWord, int finalRestartWord) {
        public RestartResult {
            word(expectedRestartWord); word(finalRestartWord);
            if (finalRestartWord != ((expectedRestartWord & 0xFF) | 0x0100)) {
                throw new IllegalArgumentException("Restart write must retain the low byte and set only the high byte to1");
            }
        }
    }

    /** Final scalar values in the existing Frame/contact owners, not another retained live store. */
    public record State(int timer, int direction, int timeWarp) {
        public State { word(timer); rawByte(direction); rawByte(timeWarp); }
    }

    public enum GlobalField { TIMER_WORD, DIRECTION_BYTE, TIME_WARP_BYTE }
    public enum ReservedField { ID, ANIMATION }
    public sealed interface Store permits GlobalWrite, ReservedWrite { }

    /** Includes executed same-value native stores. */
    public record GlobalWrite(GlobalField field, int expectedValue, int value) implements Store {
        public GlobalWrite {
            Objects.requireNonNull(field);
            if (field == GlobalField.TIMER_WORD) { word(expectedValue); word(value); }
            else { rawByte(expectedValue); rawByte(value); }
        }
    }

    /** Only the eight literal ID/animation writes performed by MakeTimeWarpStars. */
    public record ReservedWrite(int wholeIndex, ReservedField field,
                                int expectedValue, int value) implements Store {
        public ReservedWrite {
            starIndex(wholeIndex); Objects.requireNonNull(field); rawByte(expectedValue); rawByte(value);
            if (value != (field == ReservedField.ID ? 3 : wholeIndex - 7)) {
                throw new IllegalArgumentException("Not the native time-star ID/animation store");
            }
        }
    }

    /** Same bank/index and all64 bytes except ID/animation are retained, including dirty routines. */
    public record ReservedPatch(CdReservedObjectBank.Image before, CdReservedObjectBank.Image after) {
        public ReservedPatch {
            Objects.requireNonNull(before); Objects.requireNonNull(after);
            int whole = before.identity().wholeIndex();
            starIndex(whole);
            if (!after.equals(before.moveByteAt(0, 3).moveByteAt(0x1C, whole - 7))) {
                throw new IllegalArgumentException("Time-star patch must retain its owner/index and every nonwrite");
            }
        }
    }

    public record Result(State state, List<Store> stores, List<ReservedPatch> reservedPatches,
                         Optional<SaveResult> saveResult, Optional<RestartResult> restartResult) {
        public Result(State state, List<Store> stores, List<ReservedPatch> reservedPatches) {
            this(state, stores, reservedPatches, Optional.empty(), Optional.empty());
        }
        public Result(State state, List<Store> stores, List<ReservedPatch> reservedPatches, Optional<SaveResult> saveResult) {
            this(state, stores, reservedPatches, saveResult, Optional.empty());
        }
        public Result {
            Objects.requireNonNull(state);
            stores = List.copyOf(stores); reservedPatches = List.copyOf(reservedPatches);
            Objects.requireNonNull(saveResult); Objects.requireNonNull(restartResult);
        }
    }

    public enum UnsupportedBoundary {
        RESERVED_TIME_STAR_READ, SPAWN_MODE_READ, SCROLL_LOCK_WRITE, LEVEL_RESTART_WRITE
    }

    /** No native store at boundary has executed; prefix describes only earlier staged stores. */
    public static final class UnsupportedTimeWarp extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final Result prefix;

        private UnsupportedTimeWarp(UnsupportedBoundary boundary, Result prefix) {
            super(boundary.name());
            this.boundary = boundary; this.prefix = prefix;
        }

        public UnsupportedBoundary boundary() { return boundary; }
        public Result prefix() { return prefix; }
    }

    /** One native subroutine call; it owns no admission, capture, service or continuation loop. */
    public static Result step(Input input, Context context) {
        Objects.requireNonNull(input); Objects.requireNonNull(context);
        return stepShared(input,context,null);
    }

    /** Actual synchronous primitive sites; the unchanged pure entry uses the same body. */
    public interface OriginalOwner {
        void before(OriginalCall call);
        CdReservedObjectBank.Snapshot reserved(OriginalCall call);
        void store(OriginalCall call);
        void after(OriginalCall call);
        void failed(OriginalCall call,Throwable original);
    }
    /** Active continuation opt-in; the legacy OriginalOwner and stepOriginal stops are unchanged. */
    public interface ActiveOwner extends OriginalOwner {
        int scalar(OriginalCall call);
        CdTimeWarpSaveData.State saved(OriginalCall call);
        int source(OriginalCall call);
        void activeStore(OriginalCall call);
    }
    public enum OriginalValue { SPAWN_MODE_BYTE, SCROLL_LOCK_WORD, LEVEL_RESTART_WORD }
    public enum OriginalSite { RESERVED_READ, STORE, SCALAR_READ, SAVED_STATE_READ, SAVED_SOURCE_READ, ACTIVE_STORE, RESTART_STORE, FADE_OUT_MUSIC }
    public static final class OriginalCall {
        private final OriginalInvocation invocation;private final OriginalSite site;private final Store store;private final Object payload;
        private final Thread thread=Thread.currentThread();private final long ordinal;private boolean primitive;
        private OriginalCall(OriginalInvocation i,OriginalSite s,Store w,Object payload){invocation=i;site=s;store=w;this.payload=payload;ordinal=++i.ordinal;}
        public void requireCurrent(OriginalOwner owner){if(invocation.owner!=owner||!invocation.open||invocation.current!=this||thread!=Thread.currentThread())throw new IllegalStateException("No actual original TimeWarp primitive");}
        public void requirePrimitive(OriginalOwner owner){requireCurrent(owner);if(!primitive)throw new IllegalStateException("No TimeWarp primitive callback");}
        public OriginalSite site(){requireCurrent(invocation.owner);return site;}
        public Store store(){requireCurrent(invocation.owner);if(site!=OriginalSite.STORE)throw new IllegalStateException("Not a TimeWarp store");return store;}
        public long ordinal(){requireCurrent(invocation.owner);return ordinal;}
        public OriginalValue value(){requireCurrent(invocation.owner);if(site!=OriginalSite.SCALAR_READ)throw new IllegalStateException("Not a scalar read");return (OriginalValue)payload;}
        public CdTimeWarpSaveData.Source source(){requireCurrent(invocation.owner);if(site!=OriginalSite.SAVED_SOURCE_READ)throw new IllegalStateException("Not a save source read");return (CdTimeWarpSaveData.Source)payload;}
        public SaveEffect saveEffect(){requireCurrent(invocation.owner);if(site!=OriginalSite.ACTIVE_STORE)throw new IllegalStateException("Not a saved native effect");return (SaveEffect)payload;}
        public RestartResult restart(){requireCurrent(invocation.owner);if(site!=OriginalSite.RESTART_STORE)throw new IllegalStateException("Not a native restart store");return (RestartResult)payload;}
    }
    @FunctionalInterface private interface OriginalAction<T>{T run(OriginalCall call);}
    private static final class OriginalInvocation {
        final OriginalOwner owner;final boolean active;OriginalCall current;long ordinal;boolean open=true;
        OriginalInvocation(OriginalOwner owner){this(owner,false);}
        OriginalInvocation(OriginalOwner owner,boolean active){this.owner=Objects.requireNonNull(owner);this.active=active;}
        <T>T at(OriginalSite site,Store store,OriginalAction<T> action){return at(site,store,null,action);}
        <T>T at(OriginalSite site,Store store,Object payload,OriginalAction<T> action){
            if(!open||current!=null)throw new IllegalStateException("Nested original TimeWarp primitive");
            var call=new OriginalCall(this,site,store,payload);current=call;
            try{owner.before(call);T result;call.primitive=true;try{result=action.run(call);}finally{call.primitive=false;}owner.after(call);return result;}
            catch(RuntimeException|Error first){try{owner.failed(call,first);}catch(RuntimeException|Error check){if(check!=first)first.addSuppressed(check);}throw first;}
            finally{call.primitive=false;current=null;}
        }
    }
    public static Result stepOriginal(Input input,Context context,OriginalOwner owner){
        Objects.requireNonNull(input);Objects.requireNonNull(context);var invocation=new OriginalInvocation(owner);
        try{return stepShared(input,context,invocation);}finally{invocation.open=false;invocation.current=null;}
    }
    /** Actual synchronous save/restart operations; detached metadata is never replayed by a World.
     * Only the new opt-in calls ActiveOwner; pure step and legacy stepOriginal retain their contracts. */
    public static Result stepOriginalActive(Input input,Context context,ActiveOwner owner){
        Objects.requireNonNull(input);Objects.requireNonNull(context);var invocation=new OriginalInvocation(owner,true);
        try{return stepShared(input,context,invocation);}finally{invocation.open=false;invocation.current=null;}
    }
    private static Result stepShared(Input input,Context context,OriginalInvocation original){
        var c = new Cursor(input,original);
        // Main.asm:574-583: this restriction precedes even charge/direction reads.
        if (input.zoneAct() == 0 && (input.timeZone() == 0 || input.timeZone() == 2)
                && input.xWord() < 0x0900) {
            c.stopTimeWarp();
            return c.result();
        }
        if (input.charge() != 0 || input.direction() == 0) return c.result();

        // MOVE.W/BPL/NEG.W, then unsigned CMP.W/BCC. $8000 negates to itself.
        int magnitude = (input.groundVelocityWord() & 0x8000) == 0
                ? input.groundVelocityWord() : (-input.groundVelocityWord()) & 0xFFFF;
        if (c.timer == 0) c.timer(1); // First store precedes timer-stage selection, even when then cancelled.

        if (c.timer >= 230) {
            if(original!=null){
                if(!original.active)throw c.stopped(UnsupportedBoundary.LEVEL_RESTART_WRITE);
                int previous=c.scalar(OriginalValue.LEVEL_RESTART_WORD);word(previous);
                c.active(OriginalSite.RESTART_STORE,new RestartResult(previous,(previous&255)|0x100));
                c.active(OriginalSite.FADE_OUT_MUSIC,null);return c.result();
            }
            if (context.levelRestartWord().isEmpty()) throw c.stopped(UnsupportedBoundary.LEVEL_RESTART_WRITE);
            int previous = context.levelRestartWord().getAsInt();
            var result = c.result();
            return new Result(result.state(), result.stores(), result.reservedPatches(), Optional.empty(),
                    Optional.of(new RestartResult(previous, (previous & 0xFF) | 0x0100)));
        }
        if (c.timer >= 210) {
            if ((original==null||!original.active)&&context.spawnMode().isEmpty()) throw c.stopped(UnsupportedBoundary.SPAWN_MODE_READ);
            int spawn = original!=null&&original.active?c.scalar(OriginalValue.SPAWN_MODE_BYTE):context.spawnMode().getAsInt();
            rawByte(spawn);
            if (spawn == 2) return c.result();
            if(original!=null){
                if(!original.active)throw c.stopped(UnsupportedBoundary.SCROLL_LOCK_WRITE);
                var saved=c.saveActive(input,spawn);var result=c.result();
                return new Result(result.state(),result.stores(),result.reservedPatches(),Optional.of(saved));
            }
            if (context.saveContext().isEmpty()) throw c.stopped(UnsupportedBoundary.SCROLL_LOCK_WRITE);
            var saved = save(input, spawn, context.saveContext().orElseThrow());
            var result = c.result();
            return new Result(result.state(), result.stores(), result.reservedPatches(), Optional.of(saved));
        }
        if (c.timer < 90) {
            if (magnitude >= 0x0600) {
                var reserved=c.reserved(context);
                var slots = reserved.slots();
                // Only star1 ID is tested. The other three native stores overwrite dirty IDs without tests.
                if (slots.get(11).byteAt(0) == 0) {
                    c.timeWarp(1);
                    for (int whole = 12; whole <= 15; whole++) {
                        var before = slots.get(whole - 1);
                        c.store(new ReservedWrite(whole, ReservedField.ID, before.byteAt(0), 3));
                        c.store(new ReservedWrite(whole, ReservedField.ANIMATION,
                                before.byteAt(0x1C), whole - 7));
                        c.patches.add(new ReservedPatch(before,
                                before.moveByteAt(0, 3).moveByteAt(0x1C, whole - 7)));
                    }
                }
            } else {
                c.timer(0); c.timeWarp(0); // Main.asm:655-656 retains direction before timer90.
            }
        } else if (magnitude < 0x0600) {
            c.stopTimeWarp();
        }
        return c.result();
    }


    /** Main.asm:615-643 and SaveTimeWarpData:532-567, detached staging in native store order. */
    private static SaveResult save(Input input, int spawn, SaveContext context) {
        int lock = context.reads().readScrollLockWord(); word(lock);
        int locked = (lock & 0xFF) | 0x0100; // MOVE.B #1,scrollLock preserves the adjacent low byte.
        List<SaveEffect> effects = new ArrayList<>(30);
        effects.add(new LockWrite(lock, locked));
        if (input.timeZone() == 0) effects.add(new SubCpuRequest(SubCpuCommand.SCMD_FADEPCM));
        // ADD.B uses the complete raw period byte, followed by signed-negative then unsigned >=3 checks.
        int period = markedPeriod(input);
        effects.add(new PeriodWrite(input.timeZone(), period));

        // This port owns only local immutable staging. Root's separate live publication is not invoked here.
        class SavedPort implements CdTimeWarpSaveData.Port {
            private CdTimeWarpSaveData.State current = context.initial();
            @Override public CdTimeWarpSaveData.State currentState() { return current; }
            @Override public int read(CdTimeWarpSaveData.Source source) { return context.reads().read(source); }
            @Override public void commit(CdTimeWarpSaveData.StoreCall call) {
                effects.add(new SavedWrite(call)); current = call.finalState();
            }
        }
        var port = new SavedPort();
        var saved = CdTimeWarpSaveData.save(context.initial(), port);
        effects.add(new SpawnWrite(spawn, 2)); // Only after every completed saved store.
        return new SaveResult(lock, locked, input.timeZone(), period, spawn, 2,
                context.initial(), saved.state(), effects);
    }

    private static int markedPeriod(Input input){
        int period=(input.timeZone()+input.direction())&255;
        if((byte)period<0)period=0;else if(period>=3)period=2;return period|0x80;
    }

    private static final class Cursor {
        private int timer, direction, timeWarp;
        private final List<Store> stores = new ArrayList<>();
        private final List<ReservedPatch> patches = new ArrayList<>();

        private final OriginalInvocation original;
        private Cursor(Input input,OriginalInvocation original) {
            this.original=original;
            timer = input.timer(); direction = input.direction(); timeWarp = input.timeWarp();
        }

        private void timer(int value) {
            store(new GlobalWrite(GlobalField.TIMER_WORD, timer, value)); timer = value;
        }

        private void direction(int value) {
            store(new GlobalWrite(GlobalField.DIRECTION_BYTE, direction, value)); direction = value;
        }

        private void timeWarp(int value) {
            store(new GlobalWrite(GlobalField.TIME_WARP_BYTE, timeWarp, value)); timeWarp = value;
        }

        private CdReservedObjectBank.Snapshot reserved(Context context){
            if(original!=null)return Objects.requireNonNull(original.at(OriginalSite.RESERVED_READ,null,original.owner::reserved));
            if(context.reserved().isEmpty())throw stopped(UnsupportedBoundary.RESERVED_TIME_STAR_READ);
            return context.reserved().orElseThrow();
        }
        private void store(Store value){
            if(original!=null)original.at(OriginalSite.STORE,value,c->{original.owner.store(c);return null;});
            stores.add(value); // Observations of completed native work; never replayed by the live caller.
        }
        private ActiveOwner activeOwner(){if(original==null||!original.active)throw new IllegalStateException("No active TimeWarp invocation");return (ActiveOwner)original.owner;}
        private int scalar(OriginalValue value){var owner=activeOwner();return original.at(OriginalSite.SCALAR_READ,null,value,owner::scalar);}
        private CdTimeWarpSaveData.State saved(){var owner=activeOwner();return Objects.requireNonNull(original.at(OriginalSite.SAVED_STATE_READ,null,owner::saved));}
        private void active(OriginalSite site,Object payload){var owner=activeOwner();original.at(site,null,payload,c->{owner.activeStore(c);return null;});}
        private SaveResult saveActive(Input input,int spawn){
            int lock=scalar(OriginalValue.SCROLL_LOCK_WORD);word(lock);int locked=(lock&255)|0x100;
            var effects=new ArrayList<SaveEffect>(30);var locking=new LockWrite(lock,locked);active(OriginalSite.ACTIVE_STORE,locking);effects.add(locking);
            if(input.timeZone()==0){var sound=new SubCpuRequest(SubCpuCommand.SCMD_FADEPCM);active(OriginalSite.ACTIVE_STORE,sound);effects.add(sound);}
            int period=markedPeriod(input);var marking=new PeriodWrite(input.timeZone(),period);active(OriginalSite.ACTIVE_STORE,marking);effects.add(marking);
            var before=saved();var owner=activeOwner();
            var port=new CdTimeWarpSaveData.Port(){
                public CdTimeWarpSaveData.State currentState(){return saved();}
                public int read(CdTimeWarpSaveData.Source source){return original.at(OriginalSite.SAVED_SOURCE_READ,null,source,owner::source);}
                public void commit(CdTimeWarpSaveData.StoreCall call){var effect=new SavedWrite(call);active(OriginalSite.ACTIVE_STORE,effect);effects.add(effect);}
            };
            var result=CdTimeWarpSaveData.save(before,port);var spawning=new SpawnWrite(spawn,2);active(OriginalSite.ACTIVE_STORE,spawning);effects.add(spawning);
            // Observation for Body's local period value; all World operations already landed.
            return new SaveResult(lock,locked,input.timeZone(),period,spawn,2,before,result.state(),effects);
        }
        private void stopTimeWarp() { timer(0); direction(0); timeWarp(0); }
        private Result result() { return new Result(new State(timer, direction, timeWarp), stores, patches); }
        private UnsupportedTimeWarp stopped(UnsupportedBoundary boundary) {
            return new UnsupportedTimeWarp(boundary, result());
        }
    }

    private static void starIndex(int value) {
        if (value < 12 || value > 15) throw new IllegalArgumentException("Not a reserved time-star slot");
    }

    private static void rawByte(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Require raw unsigned native byte");
    }

    private static void word(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Require raw unsigned native word");
    }
}
