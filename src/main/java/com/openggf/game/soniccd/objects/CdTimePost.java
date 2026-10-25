package com.openggf.game.soniccd.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * Source-owned normal ID $19 Time Post branch. Native pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/_Objects/Monitor and Time Post/Main.asm:
 * ObjTimePostTimeIcon/ObjTimePost (:64-90), Init (:96-132), Main (:138-173), Spin/Done (:179-197),
 * ObjMonitor_GetSavedFlags (:207-236). Shipped behavior; these paths have no FixBugs alternative.
 * One call is one already selected live physical dynamic-slot dispatch, never a frame or pool walk.
 *
 * <p>Borrow the existing complete Monitor assets and the placement owner's ONE 766-byte bank.
 * TIME_POST_SPIN is the narrow State byte alias at $2A supplied by Root, not separate storage.
 * Raw global inputs are ephemeral; layer is required only at the actual MMZ contact read. The post
 * does not read timeStopTimer. A subtype outside8/9 is not normalized: subtype$A stops at its reserved
 * HUD entry; another raw >=8 byte can reach its actual finite animation-directory boundary later.
 * A directory boundary denotes this authenticated view's limit, not a claim the CPU would fault.
 *
 * <p>Direction and SubCPU callbacks execute synchronously at their native cuts, after publishing the
 * live object prefix. Future writes $FF then$01; same-value stores remain writes. Root commits each
 * direction through ONE existing Frame gate and submits the typed SubCPU request exactly once.
 * Returned effects describe COMPLETED work and must never be replayed or forwarded again. The
 * callback's return proves a completed request, not audible playback. Exceptions preserve prefixes.
 * DrawObject precedes despawn and its live slot is reloaded. Root owns phase/identity/CAS/nonreentry,
 * asset authentication, actual inputs, failure closure and later physical-slot scheduling.
 *
 * <p>No warp continuation is implemented here: after activation the existing Movement
 * TIME_WARP_ACTIVE boundary remains authoritative. Do not clear direction to bypass it. No HUD
 * slot, reserved identity, pool broadening, gameplay-global store or imported runtime binding exists.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
public final class CdTimePost {
    public static final int OBJECT_ID = 0x19;
    public enum NativeRoutine { INIT, MAIN, SPIN, DONE }
    public enum NativeCall { ANIMATE_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN }

    /** ALL System.inc rs declarations from rsset1 (:144), including anonymous rs.b1 (:183). */
    public enum SubCpuCommand {
        SCMD_PASTSFX(0x77), SCMD_FUTURESFX(0x76);
        private final int nativeId;
        SubCpuCommand(int nativeId) { this.nativeId = nativeId; }
        public int nativeId() { return nativeId; }
    }

    public sealed interface Effect permits RoutineEntered, CallReturned, SavedFlagChanged,
            DirectionCommitted, SubCpuRequest, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    /** Completed native BSET/BCLR, including a same-value write to the existing bank byte. */
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record DirectionCommitted(DirectionCall call) implements Effect {
        public DirectionCommitted { Objects.requireNonNull(call); }
    }
    /** The synchronous SubCPU callback ALREADY returned. Descriptive only; NEVER forward again. */
    public record SubCpuRequest(SubCpuCommand command) implements Effect {
        public SubCpuRequest { Objects.requireNonNull(command); }
    }
    public record Deleted() implements Effect { }

    /** Actual source-byte/word inputs. Missing layer is an explicit unowned source, never zero. */
    public record Globals(int rawTimeZone, int rawTimeWarpDirection, int cameraXWord,
                          int timeAttackMode, int zone, OptionalInt layer) {
        public Globals {
            byteValue(rawTimeZone); byteValue(rawTimeWarpDirection); wordValue(cameraXWord);
            byteValue(timeAttackMode); byteValue(zone); Objects.requireNonNull(layer);
            if (layer.isPresent()) byteValue(layer.getAsInt());
        }
    }
    /** One native timeWarpDir.b store, with the actual immediately preceding raw value. */
    public record DirectionCall(CdObjectState.Identity identity, int expectedRawDirection, int rawDirection) {
        public DirectionCall { Objects.requireNonNull(identity); byteValue(expectedRawDirection); byteValue(rawDirection); }
    }
    /**
     * Root validates open object phase, identity/live prefix and expected direction, then synchronously
     * commits exactly this byte through its one Frame gate. No pool writes, deferred store, retained
     * context, no-op success or body reentry. Future's second call expects the already committed$FF.
     */
    @FunctionalInterface public interface DirectionCommit { void commit(DirectionCall call); }
    /** Mandatory actual SubCPUCmd request, after all direction stores and BEFORE wrapper DrawObject. */
    @FunctionalInterface public interface SubCpuCall { void request(SubCpuCommand command); }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum UnsupportedBoundary {
        MONITOR_BRANCH, TIME_ICON_RESERVED_SLOT, ROUTINE_DISPATCH, MMZ_LAYER_READ, SAVED_FLAGS_ADDRESS,
        ANIMATION_DIRECTORY_ENTRY, ANIMATION_SPEED_ADDRESS, ANIMATION_FRAME_ADDRESS, ANIMATION_OPERAND_ADDRESS
    }
    public static final class UnsupportedTimePost extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedTimePost(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Time Post whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final CdMonitor.Assets assets;
    private final DirectionCommit directionCommit;
    private final SubCpuCall subCpu;
    private final CdMonitor.DrawObjectCall drawObject;

    public CdTimePost(CdObjectPlacementRuntime runtime, CdMonitor.Assets assets,
                      DirectionCommit directionCommit, SubCpuCall subCpu, CdMonitor.DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime); this.assets = Objects.requireNonNull(assets);
        this.directionCommit = Objects.requireNonNull(directionCommit); this.subCpu = Objects.requireNonNull(subCpu);
        this.drawObject = Objects.requireNonNull(drawObject);
    }

    /** Outer Monitor subtype comparison (:279-280), then post/time-icon wrapper (:64-90). */
    public Result run(int wholeIndex, Globals globals) {
        Objects.requireNonNull(globals);
        var pool = runtime.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Time Post requires live ID $19 at whole " + wholeIndex);
        var c = new Cursor(pool, state, globals.rawTimeWarpDirection());
        if (state.subtype() < 8) throw unsupported(UnsupportedBoundary.MONITOR_BRANCH, c, state.subtype());
        if (globals.timeAttackMode() != 0) {
            delete(c); // Native JMP DeleteObject: no saved access, routine lookup or wrapper tail.
            return c.finish();
        }
        if (state.subtype() == 0xA) throw unsupported(UnsupportedBoundary.TIME_ICON_RESERVED_SLOT, c, state.subtype());
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 6) throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, routine);
        switch (routine) {
            case 0 -> { if (init(c, globals)) main(c, globals); }
            case 2 -> main(c, globals);
            case 4 -> spin(c);
            case 6 -> c.enter(NativeRoutine.DONE);
        }
        draw(c);
        checkObjDespawn(c, globals);
        return c.finish();
    }

    /** ObjTimePost_Init (:96-132). Activated Init returns to the wrapper, never directly to its caller. */
    private boolean init(Cursor c, Globals globals) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveByte(Y_RADIUS, 0x20).moveByte(X_RADIUS, 0xE)
                .moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, 0x5A8)
                .moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 3);
        if (globals.zone() == 6 && c.state.unsignedByte(LAYER) == 0) {
            c.state = c.state.moveByte(PRIORITY, 0).moveWord(TILE, c.state.word(TILE) | 0x8000);
        }
        c.state = c.state.moveByte(WIDTH, 0xF).moveByte(ANIMATION, c.state.subtype());
        c.publish(); // All these stores precede the finite saved-address read.
        var at = savedAddress(c, globals);
        saved(c, at, CdObjectPlacementRuntime.SavedBit.LOADED, false);
        c.state = c.state.moveByte(MAP_FRAME, c.state.subtype() == 8 ? 0xA : 0xC);
        if ((runtime.savedFlag(at.id(), at.period()) & 1) != 0) {
            c.state = c.state.addByte(MAP_FRAME, 1).moveByte(ROUTINE, 6); c.publish();
            return false;
        }
        c.state = c.state.moveByte(COLLISION_TYPE, 0xDF); c.publish();
        return true;
    }

    /** ObjTimePost_Main (:138-173). Clear nonzero collision BEFORE the optional MMZ layer read. */
    private void main(Cursor c, Globals globals) {
        c.enter(NativeRoutine.MAIN);
        if (c.state.unsignedByte(COLLISION_STATUS) == 0) return;
        c.state = c.state.moveByte(COLLISION_STATUS, 0); c.publish();
        if (globals.zone() == 6) {
            int objectLayer = c.state.unsignedByte(LAYER);
            if (globals.layer().isEmpty()) throw unsupported(UnsupportedBoundary.MMZ_LAYER_READ, c, objectLayer);
            if ((objectLayer == 0) != (globals.layer().getAsInt() == 0)) return;
        }
        c.state = c.state.moveByte(TIME_POST_SPIN, 60).addByte(ROUTINE, 2); c.publish();
        saved(c, savedAddress(c, globals), CdObjectPlacementRuntime.SavedBit.MEMBER_1, true);
        direction(c, 0xFF); // ALWAYS performed, even Future and same-value Past.
        var command = SubCpuCommand.SCMD_PASTSFX;
        if (c.state.subtype() != 8) {
            direction(c, 1); // Immediately preceding expected value is the committed$FF, not entry globals.
            command = SubCpuCommand.SCMD_FUTURESFX;
        }
        c.publish(); subCpu.request(command);
        requireUnchangedSlot(c, "SubCPU callback");
        c.effects.add(new SubCpuRequest(command));
    }

    private void direction(Cursor c, int value) {
        c.publish();
        var call = new DirectionCall(c.state.identity(), c.direction, value);
        directionCommit.commit(call);
        requireUnchangedSlot(c, "Direction callback");
        c.direction = value; // Invocation-local observation of the completed Frame store, not another global owner.
        c.effects.add(new DirectionCommitted(call));
    }

    /** ObjTimePost_Spin/Done (:179-197). Byte decrement first; no timeStopTimer gate. */
    private void spin(Cursor c) {
        c.enter(NativeRoutine.SPIN);
        c.state = c.state.addByte(TIME_POST_SPIN, -1); c.publish();
        if (c.state.unsignedByte(TIME_POST_SPIN) != 0) {
            animate(c);
            return;
        }
        c.state = c.state.addByte(ROUTINE, 2).moveByte(MAP_FRAME, c.state.subtype() == 8 ? 0xB : 0xD);
        c.publish(); c.enter(NativeRoutine.DONE);
    }

    /**
     * Shared animator owns successful transitions. The finite-read preflight below publishes only
     * native prefixes needed if a reached read is outside the authenticated view; it never supplies
     * a replacement script, frame or animation result. Step receives the ORIGINAL call image once.
     */
    private void animate(Cursor c) {
        var original = c.state;
        animationReadPrefixes(c);
        var r = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME),
                original.unsignedByte(ANIMATION_TIME), original.unsignedByte(MAP_FRAME), original.spriteFlags(),
                original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, assets.visual().animations().size()));
        c.state = original.moveByte(ANIMATION, r.animationId()).moveByte(PREVIOUS_ANIMATION, r.previousId())
                .moveByte(ANIMATION_FRAME, r.scriptIndex()).moveByte(ANIMATION_TIME, r.timer())
                .moveByte(MAP_FRAME, r.mapFrame()).moveByte(SPRITE_FLAGS, r.spriteFlags())
                .moveByte(FLAGS, r.statusFlags()).moveByte(ROUTINE, r.routine()).moveByte(ROUTINE_2, r.routine2());
        c.publish(); c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }

    /** AnimateObject (:20-33,59-76): retain reset, SUBQ.B, speed and rewind-index writes before a bad read. */
    private void animationReadPrefixes(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION)) {
            c.state = c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
        }
        c.state = c.state.addByte(ANIMATION_TIME, -1); c.publish();
        if (c.state.signedByte(ANIMATION_TIME) >= 0) return;
        int directoryByte = 2 * animation;
        if (animation >= assets.visual().animations().size()) {
            throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY, c, directoryByte);
        }
        int script = (short) assets.visual().animationTable().unsignedWord(directoryByte);
        int speed = animationByte(c, UnsupportedBoundary.ANIMATION_SPEED_ADDRESS, script);
        c.state = c.state.moveByte(ANIMATION_TIME, speed); c.publish();
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        if (frame == 0xFF) {
            c.state = c.state.moveByte(ANIMATION_FRAME, 0); c.publish();
            animationByte(c, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1);
        } else if (frame == 0xFE) {
            int back = animationByte(c, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
            index = (index - back) & 0xFF;
            c.state = c.state.moveByte(ANIMATION_FRAME, index); c.publish();
            animationByte(c, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        } else if (frame == 0xFD) {
            animationByte(c, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
        }
    }
    private int animationByte(Cursor c, UnsupportedBoundary boundary, int address) {
        var bytes = assets.visual().animationTable();
        if (address < 0 || address >= bytes.length()) throw unsupported(boundary, c, address);
        return bytes.unsignedByte(address);
    }

    /** ObjTimePost wrapper (:81-82): actual DrawObject, then reload its live sprite-bit7 writes. */
    private void draw(Cursor c) {
        c.publish(); drawObject.drawObject(c.pool, c.state.identity());
        c.state = c.pool.state(c.wholeIndex); c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    /** CheckObjDespawn/DespawnObject/DespawnObjTimeWarp (Object Spawner.asm:385-446). */
    private void checkObjDespawn(Cursor c, Globals globals) {
        int x = c.state.centreX(); // MOVE.W precedes the current sprite-sign gate.
        boolean outside = (c.state.spriteFlags() & 0x80) == 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        if (outside || (globals.rawTimeZone() & 0x80) != 0) {
            if (c.state.savedFlagsId() != 0) saved(c, savedAddress(c, globals), CdObjectPlacementRuntime.SavedBit.LOADED, false);
            delete(c);
        }
        c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }
    private static void delete(Cursor c) {
        c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
    }
    private void saved(Cursor c, SavedAddress at, CdObjectPlacementRuntime.SavedBit bit, boolean set) {
        int old = runtime.savedFlag(at.id(), at.period());
        if (set) runtime.bsetSavedFlag(at.id(), at.period(), bit);
        else runtime.bclrSavedFlag(at.id(), at.period(), bit);
        c.effects.add(new SavedFlagChanged(at.byteIndex(), old, runtime.savedFlag(at.id(), at.period())));
    }
    private record SavedAddress(int id, int period, int byteIndex) { }
    /** Actual raw byte/word arithmetic; quotient/remainder preserves the SAME existing linear byte. */
    private static SavedAddress savedAddress(Cursor c, Globals globals) {
        int period = globals.rawTimeZone() & 0x7F;
        if ((globals.rawTimeZone() & 0x80) != 0) {
            int d1 = (period - (byte) c.direction) & 0xFFFF;
            period = (short) d1 < 0 ? 0 : d1 >= 3 ? 2 : d1;
        }
        int offset = (3 * c.state.savedFlagsId() + period) & 0xFFFF;
        int index = 2 + (short) offset;
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c, index);
        }
        return new SavedAddress(offset / 3, offset % 3, index);
    }
    private static void requireUnchangedSlot(Cursor c, String port) {
        if (!c.pool.state(c.wholeIndex).equals(c.state)) throw new IllegalStateException(port + " changed the Time Post slot");
    }
    private static UnsupportedTimePost unsupported(UnsupportedBoundary boundary, Cursor c, int value) {
        return new UnsupportedTimePost(boundary, c.wholeIndex, value);
    }
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        int direction;
        Cursor(CdObjectPool pool, CdObjectState state, int direction) {
            this.pool = pool; this.state = state; this.direction = direction; wholeIndex = state.identity().wholeIndex();
        }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void publish() { pool.write(state); }
        Result finish() { return new Result(state, effects); }
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
