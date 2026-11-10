package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import java.util.*;

/** TT routine-body calculations and source-issued primary native primitives; no dispatcher or replay API.
 * Source pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/objects/r4/*.asm.
 * Root must bind operands from the original CdProgramAssets and own draw/despawn/live cuts.
 * Both players and ALL96 dirty images survive every stopped read. Public equal data grants no authority.
 * Native a1 is primary; secondary changes only at a reached shared Top counterpart FLAGS release.
 */
public final class CdR4SolidMover {
    private CdR4SolidMover() { }
    public enum Mapping { BLOCK, WOOD, TWIN, WALL, BREAK, CRUSH, COLLAPSE, SWING, ROTATE,
        H1, H2, H3, H4, H5, H6, V1, V2, V3, V4, V5, V6, V7 }
    public enum Table { BLOCK_SIZES, TWIN_MOTION, BREAK_OFFSETS, BREAK_DELTAS, CRUSH_SHAPES, CRUSH_MOTION,
        ROTATE_FORWARD, ROTATE_BACK, ROTATE_SIDE, ROTATE_TOP, H3_OFFSETS, H3_FAILED_ALLOCATION_OFFSETS, H5_START, H5_MOTION,
        V1_START, V6_START, V10_START, V11_START }
    /** Borrowed finite source operands. No authentication or fallback assets are supplied here. */
    public enum Address { SAVED_FLAGS, WOOD_RIGHT_PROBE, WOOD_LEFT_PROBE }
    /** Finite borrowed bytes labelled by their first offset relative to the native table root.
     * The two reached LONGs after signed ADDA.W can cover relative bytes -32768..32774.
     * The root may lie outside this span; no absent bytes are padded or authenticated here.
     */
    public record RelativeTable(CdAssetBytes bytes,int firstOffset) {
        public RelativeTable { Objects.requireNonNull(bytes);
            if(firstOffset < -32768 || firstOffset > 32775)
                throw new IllegalArgumentException("Finite signed-WORD table origin required"); }
    }
    public interface Data {
        int mapping(Mapping mapping); CdAssetBytes table(Table table); int address(Address address);
        /** Existing canonical views own only offsets0..length-1; a negative borrow is explicit. */
        default RelativeTable relativeTable(Table table) { return new RelativeTable(table(table),0); }
    }
    @FunctionalInterface public interface Sine { CdPlayerMath.SineCosine read(int angleByte); }
    /** Read-only object terrain service; Root binds the existing source-equivalent terrain owner.
     * Returned signed d1 and reached angle writes belong to that original probe, never a synthetic floor.
     */
    public interface Terrain { Probe floor(int xWord, int yWord); Probe wall(boolean right, int xWord, int yWord); }
    public record Probe(int distance, OptionalInt primaryAngle, OptionalInt secondaryAngle) {
        public Probe { if (distance != (short)distance) throw new IllegalArgumentException("d1 WORD");
            Objects.requireNonNull(primaryAngle); Objects.requireNonNull(secondaryAngle); }
    }
    /** A terrain owner may expose its actually reached angle prefix on a failed read. */
    public static final class TerrainStopped extends RuntimeException {
        private final Probe prefix;
        public TerrainStopped(Probe prefix, RuntimeException cause) { super(cause);this.prefix=Objects.requireNonNull(prefix); }
        public Probe prefix() { return prefix; }
    }
    /** Actual live byte19 from BOTH actor headers, independently of byte17 collision radius. */
    public record ActorWidths(int primary, int secondary) {
        public ActorWidths { if(((primary|secondary)&~255)!=0)throw new IllegalArgumentException("actor width BYTE"); }
    }
    public record Water(int height, int staticHeight, int target, int speed) {
        public Water { if(((height|staticHeight|target|speed)&~65535)!=0)throw new IllegalArgumentException("water WORD operands"); }
    }
    public record Inputs(CdObjectPool.Snapshot pool, int wholeIndex, CdSolidObject.Player primary,
                         CdSolidObject.Player secondary, CdSolidObject.Gates gates, CdSolidObject.Globals globals,
                         CdSolidObject solid, Data data, Sine sine, Terrain terrain, CdAssetBytes savedFlags,
                         int timeZone, Water water, ActorWidths widths) {
        public Inputs { Objects.requireNonNull(pool); Objects.requireNonNull(primary); Objects.requireNonNull(secondary);
            Objects.requireNonNull(gates); Objects.requireNonNull(globals); Objects.requireNonNull(solid);
            Objects.requireNonNull(data); Objects.requireNonNull(savedFlags); Objects.requireNonNull(water);Objects.requireNonNull(widths);
            Objects.checkIndex(wholeIndex-32,96);if((timeZone&~255)!=0)throw new IllegalArgumentException("timeZone BYTE"); }
    }
    /** Exact next native cut. DRAW variants require the real DrawObject before their later tail reads.
     * DESPAWN_THEN_DRAW_DESPAWN_X is the nested Init return, not a completed effect. NONE is a genuine RTS.
     */
    public enum Tail { NONE, DRAW, DRAW_DESPAWN_X, DRAW_DESPAWN_ANCHOR_36,
        DRAW_H_WINDOW, DRAW_SWING_WINDOW, DRAW_TWIN_PARENT, DRAW_V_PARENT_23,
        DRAW_V_PARENT_NONZERO, DRAW_WALL_WINDOW, DESPAWN_ANCHOR_30, DESPAWN_ANCHOR_36,
        DESPAWN_THEN_DRAW_DESPAWN_X }
    public sealed interface Observation permits Sound, Solid, Search, Cleared { }
    public record Sound(int nativeId, Prefix before) implements Observation { }
    public record Cleared(Prefix before) implements Observation { }
    public record Solid(CdSolidObject.Outcome outcome, Optional<CdSolidObject.CounterpartFlags> counterpart) implements Observation { }
    public record Search(boolean after, int startWhole, OptionalInt found) implements Observation { }
    public record Prefix(CdObjectPool.Snapshot pool, CdSolidObject.Player primary, CdSolidObject.Player secondary,
                         CdSolidObject.Globals globals, CdAssetBytes savedFlags, Water water,
                         List<Observation> observations) {
        public Prefix { observations=List.copyOf(observations); }
    }
    public record Outcome(Prefix prefix, Tail tail, Optional<Throwable> stopped) {
        /** Only a prefix actually supplied by the shared Solid owner; local/host faults supply none.
         * This descriptive value never grants live adoption or retry authority.
         */
        public Optional<CdSolidObject.NativeStopped> nativeSolidStop() {
            if(stopped.isPresent() && !prefix.observations().isEmpty()
                    && prefix.observations().getLast() instanceof Solid call
                    && call.outcome() instanceof CdSolidObject.NativeStopped nativeStop
                    && nativeStop.stop().originalException()==stopped.orElseThrow())return Optional.of(nativeStop);
            return Optional.empty();
        }
    }
    /** Primary-only original ingress. Actual private caller authenticates this placement/source,
     * Frame and current geometry. No counterpart, width, water, clock or probe value is invented. */
    public record PrimaryInputs(CdObjectPlacementRuntime placement,int wholeIndex,
            CdSolidObject.Player primary,CdSolidObject.Gates gates,CdSolidObject.Globals globals,
            CdSolidObject solid,Data data) {
        public PrimaryInputs {Objects.requireNonNull(placement);Objects.checkIndex(wholeIndex-32,96);
            Objects.requireNonNull(primary);Objects.requireNonNull(gates);Objects.requireNonNull(globals);
            Objects.requireNonNull(solid);Objects.requireNonNull(data);}
    }
    public enum Kind { READ, STORE, SOLID, TAIL, SEARCH, CHILD_STORE, SOUND, DELETE, PRIMARY_Y }
    public enum ReadKind { BYTE, WORD, LONG, MAPPING, TABLE, ADDRESS, RELATIVE_TABLE }
    /** Required synchronous native owners, not effect-list consumers or source admission by values.
     * before/after fence every reached occurrence against the real complete external cut. Solid
     * publishes this issued call's shared outcome/prefix once; tail executes its native Draw/return
     * path. after also runs on before refusal and on landed callbacks that throw. */
    public interface LiveOwner {
        long generation();
        void requireCurrent(Call call);
        void before(Operation operation);
        void after(Operation operation);
        void solid(SolidCall call);
        void tail(TailCall call);
        default void sound(SoundCall call){throw new UnownedLiveOperand("native family sound publication");}
        default CdPlayerMath.SineCosine sine(SineCall call){throw new UnownedLiveOperand("native sine operand");}
        default void primaryY(PrimaryYCall call){throw new UnownedLiveOperand("native primary Y WORD store");}
    }
    public abstract static class Call {
        private final LiveInvocation issuer;
        private final long ordinal;
        private final CdObjectState source;
        private final CdObjectPlacementRuntime.Snapshot before;
        private Call(LiveInvocation issuer){this.issuer=issuer;ordinal=++issuer.ordinal;
            before=issuer.inputs.placement().capture();source=issuer.inputs.placement().pool().state(issuer.inputs.wholeIndex());}
        public final void requireCurrent(LiveOwner actual){
            if(actual!=issuer.owner||!issuer.active||issuer.current!=this||issuer.thread!=Thread.currentThread()
                    ||issuer.generation!=actual.generation())throw new IllegalStateException("Foreign/retired/reentrant TT native mover occurrence");
        }
        final LiveInvocation issuer(){return issuer;}
        final CdObjectPlacementRuntime.Snapshot beforePlacement(){return before;}
        final CdObjectState rawSource(){return source;}
        public final CdObjectPlacementRuntime placement(){requireCurrent(issuer.owner);return issuer.inputs.placement();}
        public final int wholeIndex(){requireCurrent(issuer.owner);return issuer.inputs.wholeIndex();}
        public final CdObjectState source(){requireCurrent(issuer.owner);return source;}
        public final CdObjectPool.Snapshot beforePool(){requireCurrent(issuer.owner);return before.pool();}
        /** Inert comparison value; it cannot reopen or authorize a retired call. */
        public final long ordinal(){return ordinal;}
    }
    public abstract static class Operation extends Call {
        private final Kind kind;private boolean completed;
        private Operation(LiveInvocation issuer,Kind kind){super(issuer);this.kind=kind;}
        public final Kind kind(){requireCurrent(issuer().owner);return kind;}
        /** Store: its actual one pool write landed. Other calls: the native callback returned.
         * A callback may land and throw; the actual owner's private after cut must retain that prefix. */
        public final boolean completed(){return completed;}
    }
    public static final class ReadCall extends Operation {
        private final ReadKind readKind;private final int offset,bytes;
        private final Optional<Mapping> mapping;private final Optional<Table> table;private final Optional<Address> address;
        private ReadCall(LiveInvocation i,ReadKind kind,int offset,int bytes,Mapping mapping,Table table,Address address){
            super(i,Kind.READ);this.readKind=kind;this.offset=offset;this.bytes=bytes;
            this.mapping=Optional.ofNullable(mapping);this.table=Optional.ofNullable(table);this.address=Optional.ofNullable(address);}
        public ReadKind readKind(){requireCurrent(issuer().owner);return readKind;}
        public int offset(){requireCurrent(issuer().owner);return offset;}
        public int bytes(){requireCurrent(issuer().owner);return bytes;}
        public Optional<Mapping> mapping(){requireCurrent(issuer().owner);return mapping;}
        public Optional<Table> table(){requireCurrent(issuer().owner);return table;}
        public Optional<Address> address(){requireCurrent(issuer().owner);return address;}
    }
    /** One actually reached SineCosine read; neither a public constructor nor a replay value. */
    public static final class SineCall extends Operation {
        private final int angle;
        private SineCall(LiveInvocation i,int angle){super(i,Kind.READ);if((angle&~255)!=0)throw new IllegalArgumentException("Native sine BYTE");this.angle=angle;}
        public int angle(){requireCurrent(issuer().owner);return angle;}
    }
    public static final class StoreCall extends Operation {
        private final int offset,bytes,value;private final CdObjectState output;
        private StoreCall(LiveInvocation i,int offset,int bytes,int value){super(i,Kind.STORE);
            this.offset=offset;this.bytes=bytes;this.value=value;output=put(rawSource(),offset,bytes,value);}
        public int offset(){requireCurrent(issuer().owner);return offset;}
        public int bytes(){requireCurrent(issuer().owner);return bytes;}
        public int value(){requireCurrent(issuer().owner);return value;}
        public CdObjectState before(){return source();}
        public CdObjectState output(){requireCurrent(issuer().owner);return output;}
    }
    public static final class SolidCall extends Operation {
        private final CdSolidObject.Entry entry;private final CdSolidObject.Player primary;
        private final CdSolidObject.Gates gates;private final CdSolidObject.Globals globals;
        private CdSolidObject.Outcome outcome;
        private SolidCall(LiveInvocation i,Cut cut,CdSolidObject.Entry entry){super(i,Kind.SOLID);
            this.entry=entry;primary=cut.player;gates=cut.in.gates();globals=cut.globals;}
        public CdSolidObject.Entry entry(){requireCurrent(issuer().owner);return entry;}
        public CdSolidObject.Player primary(){requireCurrent(issuer().owner);return primary;}
        public CdSolidObject.Gates gates(){requireCurrent(issuer().owner);return gates;}
        public CdSolidObject.Globals globals(){requireCurrent(issuer().owner);return globals;}
        /** Available only after this occurrence's actual shared Solid calculation. Inert data
         * alone is not a publisher; the original private issuer/current owner remains mandatory. */
        public CdSolidObject.Outcome outcome(){requireCurrent(issuer().owner);if(outcome==null)throw new IllegalStateException("Shared Solid has not run");return outcome;}
    }
    public static final class TailCall extends Operation {
        private final Tail tail;
        private TailCall(LiveInvocation i,Tail tail){super(i,Kind.TAIL);this.tail=Objects.requireNonNull(tail);}
        public Tail tail(){requireCurrent(issuer().owner);return tail;}
    }
    /** Source-issued full-pool allocator observation; no ID store or synthetic slot is implied. */
    public static final class SearchCall extends Operation {
        private final boolean after;private OptionalInt found;
        private SearchCall(LiveInvocation i,boolean after){super(i,Kind.SEARCH);this.after=after;}
        public boolean after(){requireCurrent(issuer().owner);return after;}
        public OptionalInt found(){requireCurrent(issuer().owner);if(found==null)throw new IllegalStateException("Allocator has not returned");return found;}
    }
    public static final class ChildStoreCall extends Operation {
        private final int target,offset,bytes,value;private final CdObjectState before,output;
        private ChildStoreCall(LiveInvocation i,int target,int offset,int bytes,int value){super(i,Kind.CHILD_STORE);this.target=target;this.offset=offset;this.bytes=bytes;this.value=value;before=i.inputs.placement().pool().state(target);output=put(before,offset,bytes,value);}
        public int targetWhole(){requireCurrent(issuer().owner);return target;}public int offset(){requireCurrent(issuer().owner);return offset;}public int bytes(){requireCurrent(issuer().owner);return bytes;}public int value(){requireCurrent(issuer().owner);return value;}
        public CdObjectState before(){requireCurrent(issuer().owner);return before;}public CdObjectState output(){requireCurrent(issuer().owner);return output;}
    }
    public static final class SoundCall extends Operation {
        private final int nativeId;private SoundCall(LiveInvocation i,int nativeId){super(i,Kind.SOUND);this.nativeId=nativeId;}
        public int nativeId(){requireCurrent(issuer().owner);return nativeId;}
    }
    /** Reached Swing TopSolid alignment only; expected/output retain the entire selected primary. */
    public static final class PrimaryYCall extends Operation {
        private final CdSolidObject.Player expected,output;private final int word;
        private PrimaryYCall(LiveInvocation i,CdSolidObject.Player expected,CdSolidObject.Player output,int word){
            super(i,Kind.PRIMARY_Y);this.expected=expected;this.output=output;this.word=word&65535;}
        public CdSolidObject.Player expectedPlayer(){requireCurrent(issuer().owner);return expected;}
        public CdSolidObject.Player output(){requireCurrent(issuer().owner);return output;}
        public int word(){requireCurrent(issuer().owner);return word;}
    }
    public static final class DeleteCall extends Operation {
        private final CdObjectState output;
        private DeleteCall(LiveInvocation i){super(i,Kind.DELETE);var planned=CdObjectPool.stagingCopy(beforePlacement().pool());planned.deleteObject(i.inputs.wholeIndex());output=planned.state(i.inputs.wholeIndex());}
        public CdObjectState output(){requireCurrent(issuer().owner);return output;}
    }
    public static final class UnownedLiveOperand extends UnsupportedOperationException {
        private UnownedLiveOperand(String operand){super("TT primary-only mover has no reached owner for "+operand);}
    }
    // Internal operand view: existing detached Inputs stays byte-exact. Primary ingress borrows
    // only actual required values and rejects every unused operand if it is genuinely reached.
    interface Operands {Data data();CdSolidObject solid();CdSolidObject.Gates gates();Sine sine();Terrain terrain();int timeZone();ActorWidths widths();}
    private record DetachedOperands(Inputs inputs) implements Operands {
        public Data data(){return inputs.data();}public CdSolidObject solid(){return inputs.solid();}public CdSolidObject.Gates gates(){return inputs.gates();}
        public Sine sine(){return inputs.sine();}public Terrain terrain(){return inputs.terrain();}public int timeZone(){return inputs.timeZone();}public ActorWidths widths(){return inputs.widths();}
    }
    private static final class PrimaryOperands implements Operands {
        private final PrimaryInputs inputs;private final LiveInvocation live;private final Data data;
        PrimaryOperands(PrimaryInputs inputs,LiveInvocation live){this.inputs=inputs;this.live=live;
            data=new Data(){
                public int mapping(Mapping m){return live.read(new ReadCall(live,ReadKind.MAPPING,0,4,m,null,null),()->inputs.data().mapping(m));}
                public CdAssetBytes table(Table t){return live.read(new ReadCall(live,ReadKind.TABLE,0,0,null,t,null),()->inputs.data().table(t));}
                public int address(Address a){return live.read(new ReadCall(live,ReadKind.ADDRESS,0,4,null,null,a),()->inputs.data().address(a));}
                public RelativeTable relativeTable(Table t){return live.read(new ReadCall(live,ReadKind.RELATIVE_TABLE,0,0,null,t,null),()->inputs.data().relativeTable(t));}
            };}
        public Data data(){return data;}public CdSolidObject solid(){return inputs.solid();}public CdSolidObject.Gates gates(){return inputs.gates();}
        public Sine sine(){return live::sine;}public Terrain terrain(){throw new UnownedLiveOperand("extra terrain probe");}
        public int timeZone(){throw new UnownedLiveOperand("period operand");}public ActorWidths widths(){throw new UnownedLiveOperand("primary/counterpart widths");}
    }
    private static final class LiveInvocation {
        final PrimaryInputs inputs;final LiveOwner owner;final Thread thread=Thread.currentThread();final long generation;
        boolean active=true;long ordinal;Operation current;final Set<Integer> allocated=new HashSet<>();
        LiveInvocation(PrimaryInputs inputs,LiveOwner owner){this.inputs=Objects.requireNonNull(inputs);this.owner=Objects.requireNonNull(owner);generation=owner.generation();}
        private void verifyBefore(Operation call){
            call.requireCurrent(owner);var now=inputs.placement().capture();
            if(!same(now,call.beforePlacement()))throw new IllegalStateException("Changed complete TT mover source pool/status/saved cut");
        }
        private boolean same(CdObjectPlacementRuntime.Snapshot a,CdObjectPlacementRuntime.Snapshot b){
            return a.status().equals(b.status())&&a.pool().slots().equals(b.pool().slots())&&Arrays.equals(a.savedFlags().copy(),b.savedFlags().copy());
        }
        private CdObjectPool.Snapshot solidPool(SolidCall c){
            CdObjectState own;List<CdObjectState> others;
            if(c.outcome instanceof CdSolidObject.Completed ok){own=ok.completedResult().object();others=ok.completedResult().otherSlots();}
            else{var prefix=((CdSolidObject.NativeStopped)c.outcome).prefix();own=prefix.object();others=prefix.otherSlots();}
            var all=new ArrayList<CdObjectState>();all.add(own);all.addAll(others);return CdObjectPool.overlay(c.beforePool(),all);
        }
        private void verifyAfter(Operation call){
            call.requireCurrent(owner);var now=inputs.placement().capture();inputs.placement().validateRestore(now);
            // Tail owns its real Draw/despawn conditional reads and stored prefix. The actual
            // private owner verifies that exact cut; there is no returned image to adopt here.
            if(call instanceof TailCall)return;
            var expected=call.beforePlacement().pool();
            if(call instanceof StoreCall store&&call.completed)expected=CdObjectPool.overlay(expected,List.of(store.output));
            if(call instanceof ChildStoreCall store&&call.completed)expected=CdObjectPool.overlay(expected,List.of(store.output));
            if(call instanceof DeleteCall deleted&&call.completed)expected=CdObjectPool.overlay(expected,List.of(deleted.output));
            if(call instanceof SolidCall solid&&solid.outcome!=null){var out=solidPool(solid);
                if(!call.completed&&now.pool().slots().equals(expected.slots()))out=expected;expected=out;}
            if(!now.status().equals(call.beforePlacement().status())||!Arrays.equals(now.savedFlags().copy(),call.beforePlacement().savedFlags().copy())||!now.pool().slots().equals(expected.slots()))
                throw new IllegalStateException("Changed TT mover pool beyond issued native prefix");
        }
        void perform(Operation call,Runnable action){
            if(!active||current!=null||thread!=Thread.currentThread())throw new IllegalStateException("TT mover invocation reentry/retired");
            current=call;Throwable first=null;
            try{call.requireCurrent(owner);owner.requireCurrent(call);owner.before(call);verifyBefore(call);action.run();call.completed=true;}
            catch(RuntimeException|Error original){first=original;throw original;}
            finally{
                Throwable verification=null;
                try{owner.after(call);}catch(RuntimeException|Error after){verification=after;}
                try{verifyAfter(call);}catch(RuntimeException|Error guard){if(verification==null)verification=guard;else if(guard!=verification)verification.addSuppressed(guard);}
                finally{current=null;}
                if(verification!=null){if(first!=null){if(verification!=first)first.addSuppressed(verification);}else if(verification instanceof RuntimeException runtime)throw runtime;else throw (Error)verification;}
            }
        }
        <T>T read(ReadCall call,java.util.function.Supplier<T> read){var box=new Object[1];perform(call,()->box[0]=read.get());@SuppressWarnings("unchecked")T value=(T)box[0];return value;}
        int read(int offset,int bytes,java.util.function.IntSupplier read){return read(new ReadCall(this,bytes==1?ReadKind.BYTE:bytes==2?ReadKind.WORD:ReadKind.LONG,offset,bytes,null,null,null),()->read.getAsInt());}
        void store(int offset,int bytes,int value){var call=new StoreCall(this,offset,bytes,value);perform(call,()->inputs.placement().pool().write(call.output));}
        int solidWord(Cut cut,CdSolidObject.Entry entry){
            var call=new SolidCall(this,cut,entry);
            perform(call,()->{
                call.outcome=cut.plainSolid(entry);Throwable nativeFirst=call.outcome instanceof CdSolidObject.NativeStopped stopped?stopped.stop().originalException():null;
                try{owner.solid(call);}catch(RuntimeException|Error publisher){if(nativeFirst!=null){if(publisher!=nativeFirst)nativeFirst.addSuppressed(publisher);rethrow(nativeFirst);}throw publisher;}
                if(call.outcome instanceof CdSolidObject.Completed ok){cut.player=ok.completedResult().player();cut.globals=ok.completedResult().globals();}
                else{var prefix=((CdSolidObject.NativeStopped)call.outcome).prefix();cut.player=prefix.player();cut.globals=prefix.globals();}
                if(nativeFirst!=null)rethrow(nativeFirst);
            });
            return ((CdSolidObject.Completed)call.outcome).completedResult().d0().orElse(0);
        }
        OptionalInt search(boolean after){var call=new SearchCall(this,after);perform(call,()->{call.found=after?inputs.placement().pool().findNextObjSlotAfterWholeIndex(inputs.wholeIndex()):inputs.placement().pool().findObjSlot();if(call.found.isPresent()){int target=call.found.getAsInt();if(!allocated.add(target))throw new IllegalStateException("Native allocator reused a live child in this occurrence");}});return call.found;}
        void childStore(int target,int offset,int bytes,int value){if(!allocated.contains(target))throw new IllegalStateException("Child store has no completed native allocator in this occurrence");var call=new ChildStoreCall(this,target,offset,bytes,value);perform(call,()->inputs.placement().pool().write(call.output));}
        CdPlayerMath.SineCosine sine(int angle){var call=new SineCall(this,angle);var result=new CdPlayerMath.SineCosine[1];perform(call,()->result[0]=Objects.requireNonNull(owner.sine(call),"Actual native sine result"));return result[0];}
        void primaryY(Cut cut,CdSolidObject.Player output,int word){
            var call=new PrimaryYCall(this,cut.player,output,word);perform(call,()->owner.primaryY(call));cut.player=output;
        }
        void sound(int nativeId){var call=new SoundCall(this,nativeId);perform(call,()->owner.sound(call));}
        void delete(){var call=new DeleteCall(this);perform(call,()->inputs.placement().pool().deleteObject(inputs.wholeIndex()));}
        void tail(Tail tail){var call=new TailCall(this,tail);perform(call,()->owner.tail(call));}
        void close(){active=false;current=null;}
    }
    private static void rethrow(Throwable first){if(first instanceof RuntimeException runtime)throw runtime;throw (Error)first;}
    /** Reusable actual plain Solid call for a source-authenticated family's already reached
     * helper site (for example Spikes). No mover body, Init, Draw or tail runs here. The actual
     * private caller must bind this receipt to its own original native occurrence and full cut. */
    public static int runPlainSolid(PrimaryInputs inputs,LiveOwner owner){
        var live=new LiveInvocation(inputs,owner);
        try{return live.solidWord(new Cut(inputs,live),CdSolidObject.Entry.SOLID_OBJECT);}finally{live.close();}
    }
    /** Same body evaluator, with actual native stores and synchronous source-issued callbacks.
     * This internal seam is opened by each authenticated family overload, never by an effect list. */
    static void runPrimary(PrimaryInputs in,LiveOwner owner,Body body){
        var live=new LiveInvocation(in,owner);
        try{var cut=new Cut(in,live);live.tail(body.run(cut));}finally{live.close();}
    }
    @FunctionalInterface interface Body { Tail run(Cut c); }
    static Outcome calculate(Inputs in, Body body) {
        Cut c=new Cut(in);
        try { return new Outcome(c.finishAfter(body),c.tail,Optional.empty()); }
        catch (RuntimeException | Error failure) { return new Outcome(c.prefix(),Tail.NONE,Optional.of(failure)); }
    }
    static final class Cut {
        final Operands in; final CdObjectPool pool; final int index;
        private LiveInvocation live;
        CdSolidObject.Player player,secondary; CdSolidObject.Globals globals; Water water;
        byte[] saved; final List<Observation> observations=new ArrayList<>(); Tail tail;
        Cut(Inputs in) { this.in=new DetachedOperands(in); pool=CdObjectPool.stagingCopy(in.pool()); index=in.wholeIndex();
            player=in.primary(); secondary=in.secondary(); globals=in.globals(); water=in.water(); saved=in.savedFlags().copy(); }
        Cut(PrimaryInputs in,LiveInvocation live){this.in=new PrimaryOperands(in,live);this.live=live;
            pool=in.placement().pool();index=in.wholeIndex();player=in.primary();globals=in.globals();
            // No secondary or water value exists in this primary-only ingress. They are never
            // read by Block/CollapseFloor/HMove; a reached physical1 release keeps shared Solid's native stop.
            saved=in.placement().capture().savedFlags().copy();}
        Prefix finishAfter(Body body) { tail=body.run(this); return prefix(); }
        Prefix prefix() { if(live!=null)throw new UnownedLiveOperand("detached aggregate Prefix in live scope");return new Prefix(pool.capture(),player,secondary,globals,CdAssetBytes.copyOf(saved),water,observations); }
        CdObjectState s() { return pool.state(index); }
        int b(int at) { return live==null?u8(s(),at):live.read(at,1,()->u8(s(),at)); }
        int w(int at) { return live==null?u16(s(),at):live.read(at,2,()->u16(s(),at)); }
        int l(int at) { return live==null?i32(s(),at):live.read(at,4,()->i32(s(),at)); }
        void b(int at,int v) { if(live==null)pool.write(put(s(),at,1,v));else live.store(at,1,v); }
        void w(int at,int v) { if(live==null)pool.write(put(s(),at,2,v));else live.store(at,2,v); }
        void l(int at,int v) { if(live==null)pool.write(put(s(),at,4,v));else live.store(at,4,v); }
        void ab(int at,int v) { b(at,b(at)+v); } void aw(int at,int v) { w(at,w(at)+v); }
        void al(int at,int v) { l(at,l(at)+v); }
        CdAssetBytes table(Table t) { return Objects.requireNonNull(in.data().table(t),"reached source table"); }
        int tb(Table t,int at) { return table(t).unsignedByte(at); }
        int tw(Table t,int at) { return table(t).unsignedWord(at); }
        int tl(Table t,int at) { return (int)table(t).unsignedLong(at); }
        int trl(Table t,int at) { var view=Objects.requireNonNull(in.data().relativeTable(t),"reached relative table");
            return (int)view.bytes().unsignedLong(Math.subtractExact(at,view.firstOffset())); }
        int sine() { return Objects.requireNonNull(in.sine(),"reached sine owner").read(b(0x3a)).sine(); }
        OptionalInt spawn(boolean after) { if(live!=null)return live.search(after);
            var found=after?pool.findNextObjSlotAfterWholeIndex(index):pool.findObjSlot();
            observations.add(new Search(after,index,found)); return found;
        }
        void child(int slot,int at,int size,int value) { if(live!=null){live.childStore(slot,at,size,value);return;}pool.write(put(pool.state(slot),at,size,value)); }
        CdObjectState raw(int low) {
            int address=low&0xffff;
            if(address<0xd800||address>0xefc0||(address&63)!=0)
                throw new IllegalArgumentException("Unowned/misaligned reached raw object link: "+Integer.toHexString(address));
            return pool.state((address-0xd000)/64);
        }
        int address() { return s().identity().nativeAddress24()&0xffff; }
        CdSolidObject.Outcome plainSolid(CdSolidObject.Entry entry){return in.solid().runWithPrefix(entry,s(),player,in.gates(),globals,pool.capture());}
        boolean solid(CdSolidObject.Entry entry) {
            if(live!=null)return live.solidWord(this,entry)!=0;
            CdSolidObject.Outcome out;
            Optional<CdSolidObject.CounterpartFlags> counterpart=Optional.empty();
            if(entry==CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                var selected=in.solid().runWithPrefix(entry,s(),player,in.gates(),globals,pool.capture(),
                    new CdSolidObject.PlayerSelection(CdSolidObject.PlayerSlot.PRIMARY,secondary.actor().motion().flags()));
                out=selected.outcome();var flags=selected.counterpart();counterpart=Optional.of(flags);
                if(flags.stored())secondary=withFlags(secondary,flags.value());
            } else out=plainSolid(entry);
            observations.add(new Solid(out,counterpart));
            if(out instanceof CdSolidObject.Completed ok) {
                var r=ok.completedResult(); pool.write(r.object()); for(var other:r.otherSlots()) pool.write(other);
                player=r.player(); globals=r.globals(); return r.d0().orElse(0)!=0;
            }
            var stop=(CdSolidObject.NativeStopped)out; var p=stop.prefix(); pool.write(p.object());
            for(var other:p.otherSlots()) pool.write(other); player=p.player(); globals=p.globals();
            throw stop.stop().originalException();
        }
        CdPlayerMotionState m() { return player.actor().motion(); }
        int px() { return m().centreX16_16()>>>16; } int py() { return m().centreY16_16()>>>16; }
        void player(Integer x,Integer y,Integer xv,Integer yv,Integer gv) {
            if(live!=null&&(x!=null||y==null||xv!=null||yv!=null||gv!=null))throw new UnownedLiveOperand("additional primary formula");
            var m=m(); var next=new CdPlayerMotionState(x==null?m.centreX16_16():((x&0xffff)<<16)|(m.centreX16_16()&0xffff),
                y==null?m.centreY16_16():((y&0xffff)<<16)|(m.centreY16_16()&0xffff),
                xv==null?m.xVelocity():(short)(int)xv,yv==null?m.yVelocity():(short)(int)yv,gv==null?m.groundVelocity():(short)(int)gv,
                m.routine(),m.angle(),m.flags(),m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
                m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
            var a=player.actor();var output=new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next,a.tile(),a.priority(),a.hurt(),a.rotationDistance()),
                player.slotId(),player.pushObject(),player.standObject());
            if(live==null)player=output;else live.primaryY(this,output,y);
        }
        int floor(int x,int y) { if(live!=null)throw new UnownedLiveOperand("extra floor probe");
            try { return probe(in.terrain().floor(x,y)); }
            catch(TerrainStopped stopped) { probe(stopped.prefix());throw stopped; }
        }
        int wall(boolean right,int x,int y) { if(live!=null)throw new UnownedLiveOperand("extra wall probe");
            try { return probe(in.terrain().wall(right,x,y)); }
            catch(TerrainStopped stopped) { probe(stopped.prefix());throw stopped; }
        }
        int probe(Probe p) { globals=new CdSolidObject.Globals(globals.invincible(),globals.scoreChain(),
                p.primaryAngle().orElse(globals.primaryAngle()),p.secondaryAngle().orElse(globals.secondaryAngle())); return p.distance(); }
        void sound(int id) { if(live!=null){live.sound(id);return;}observations.add(new Sound(id,prefix())); }
        void clear() { if(live!=null){live.delete();return;}var before=prefix();pool.deleteObject(index);observations.add(new Cleared(before)); }
    }
    static CdSolidObject.Player withFlags(CdSolidObject.Player player,int flags) {
        var a=player.actor();var m=a.motion();
        var next=new CdPlayerMotionState(m.centreX16_16(),m.centreY16_16(),m.xVelocity(),m.yVelocity(),m.groundVelocity(),
            m.routine(),m.angle(),flags,m.spriteFlags(),m.xRadius(),m.yRadius(),m.animation(),m.previousAnimation(),
            m.playerJump(),m.charge(),m.moveLock(),m.stick(),m.playerControl(),m.previousPrimaryAngle(),m.previousSecondaryAngle());
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next,a.tile(),a.priority(),a.hurt(),a.rotationDistance()),
            player.slotId(),player.pushObject(),player.standObject());
    }
    static int u8(CdObjectState s,int at) { return s.copyBytes()[Objects.checkIndex(at,64)]&255; }
    static int u16(CdObjectState s,int at) { return u8(s,at)*256+u8(s,at+1); }
    static int i32(CdObjectState s,int at) { return u16(s,at)<<16|u16(s,at+2); }
    /** Storage-only adapter: use existing ABI aliases without expanding the shared enum in another lane. */
    static CdObjectState put(CdObjectState s,int at,int size,int value) {
        Objects.checkFromIndexSize(at,size,64);
        for(int i=0;i<size;i++) {
            int offset=at+i, v=(value>>>((size-1-i)*8))&255;
            boolean done=false;
            for(var f:CdObjectState.ByteField.values()) if(f.byteOffset()==offset) { s=s.moveByte(f,v);done=true;break; }
            if(done) continue;
            for(var f:CdObjectState.WordField.values()) if(offset>=f.byteOffset()&&offset<f.byteOffset()+2) {
                int shift=(1-offset+f.byteOffset())*8;
                s=s.moveWord(f,(s.word(f)&~(255<<shift))|(v<<shift));done=true;break;
            }
            if(done) continue;
            for(var f:CdObjectState.LongField.values()) if(offset>=f.byteOffset()&&offset<f.byteOffset()+4) {
                int shift=(3-offset+f.byteOffset())*8;
                s=s.moveLong(f,(s.longBits(f)&~(255<<shift))|(v<<shift));done=true;break;
            }
            if(!done) throw new IllegalArgumentException("Unrepresented object storage byte "+offset);
        }
        return s;
    }
}
