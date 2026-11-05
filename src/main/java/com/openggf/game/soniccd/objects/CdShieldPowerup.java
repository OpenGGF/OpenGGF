package com.openggf.game.soniccd.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Reserved whole6 ObjPowerup Init/Shield/ChkSaveRout, native pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Powerup/Main.asm:21-70,137-153.
 * Uses the existing bank (including its raw-backed facade) and borrowed Powerup
 * source views. No slot clearing at award, allocation, player/global store other
 * than Powerup, art upload, sound, clock, capture owner or World authority.
 * Init returns without drawing; the award ID-byte store belongs to the caller.
 * There is no FixBugs alternative in these routines.
 *
 * <p>Finite reads occur only when reached, in source order. Every completed store
 * prefix survives failure. Animation runs once on its original image; separately
 * published read prefixes model only stores preceding potentially missing bytes.
 * No mapping pointer is dereferenced here, including on a held animation timer.
 * Returned effects are completed scalar observations, never commands to replay.
 *
 * <p>The opt-in observer/guard exposes original pending/primitive/completion cuts
 * for a private synchronous World adapter. Reference identity and value checks
 * alone authenticate no native producer, external read/store, queue write, no-op,
 * ABA, generation or exact-once lifetime. The caller must own those receipts and
 * full96/Frame/Values/saved/scroll/queue/global checks. Ordinary construction
 * provides value-only nonwrite checks. This component is single-thread confined.
 */
public final class CdShieldPowerup {
    public static final int OBJECT_ID = 3;
    public static final int WHOLE_INDEX = 6;

    /** Actual external Contact/Frame/header reads, with no defaults or eager snapshot. */
    public enum ReadField {
        SHIELD, TIME_WARP, INVINCIBLE, PLAYER_X_WORD, PLAYER_Y_WORD, PLAYER_FLAGS, ZONE, LAYER
    }
    @FunctionalInterface public interface ReadPort { int read(ReadField field); }
    public interface PowerupPort { int read(); void write(int value); }
    @FunctionalInterface public interface DrawReserved {
        void drawObject(CdReservedObjectBank bank, CdReservedObjectBank.Identity identity);
    }
    public enum NativeRoutine { INIT, SHIELD }
    public sealed interface Effect permits RoutineEntered, ValueRead, AnimationReturned,
            PowerupRead, PowerupWritten, DrawReturned, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record ValueRead(ReadField field, int value) implements Effect {
        public ValueRead { readValue(Objects.requireNonNull(field), value); }
    }
    public record AnimationReturned() implements Effect { }
    public record PowerupRead(int value) implements Effect {
        public PowerupRead { byteValue(value); }
    }
    public record PowerupWritten(int previousValue, int value) implements Effect {
        public PowerupWritten { byteValue(previousValue); byteValue(value); }
    }
    public record DrawReturned() implements Effect { }
    public record Deleted() implements Effect { }
    public record Result(CdReservedObjectBank.Image state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return state.unsignedByte(ID) == 0; }
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, INVINCIBILITY_STARS_ROUTINE, TIME_STARS_ROUTINE,
        ANIMATION_DIRECTORY_ADDRESS, ANIMATION_SPEED_ADDRESS, ANIMATION_FRAME_ADDRESS,
        ANIMATION_OPERAND_ADDRESS
    }
    public static final class UnsupportedShieldPowerup extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int value;
        private UnsupportedShieldPowerup(UnsupportedBoundary boundary, int value) {
            super("ShieldPowerup " + boundary + " at whole6: " + value);
            this.boundary = boundary; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return WHOLE_INDEX; }
        public int value() { return value; }
    }

    /** Public immutable descriptions are not producer/adoption authority. */
    public sealed interface Call permits Publication, ReadCall, PowerupReadCall, PowerupWriteCall, DrawCall {
        CdReservedObjectBank.Snapshot cut();
        CdReservedObjectBank.Image source();
    }
    public record Publication(CdReservedObjectBank.Snapshot cut, CdReservedObjectBank.Image source,
                              CdReservedObjectBank.Image output) implements Call { }
    public record ReadCall(CdReservedObjectBank.Snapshot cut, CdReservedObjectBank.Image source,
                           ReadField field) implements Call { }
    public record PowerupReadCall(CdReservedObjectBank.Snapshot cut, CdReservedObjectBank.Image source) implements Call { }
    public record PowerupWriteCall(CdReservedObjectBank.Snapshot cut, CdReservedObjectBank.Image source,
                                   int previousValue, int value) implements Call { }
    public record DrawCall(CdReservedObjectBank.Snapshot cut, CdReservedObjectBank.Image source) implements Call { }
    public interface DispatchObserver {
        void beforeOperation(Call call);
        void afterOperation(Call call);
        void failedOperation(Call call, Throwable original);
    }
    public interface ReceiptGuard { void prepared(Call call); void completed(Call call); }

    private final CdReservedObjectBank bank;
    private final CdTimeStars.Assets assets;
    private final ReadPort reads;
    private final PowerupPort powerup;
    private final DrawReserved draw;
    private DispatchObserver observer;
    private ReceiptGuard receiptGuard;
    private boolean running, primitiveActive, resultReady;
    private Call pendingOperation;
    private Object operationResult;
    private long operationOrdinal;

    /** Reuse CdTimeStars.Assets.fromAct2Assets(actualOwner); no second media/table owner. */
    public CdShieldPowerup(CdReservedObjectBank bank, CdTimeStars.Assets assets, ReadPort reads,
                           PowerupPort powerup, DrawReserved draw) {
        this.bank = Objects.requireNonNull(bank); this.assets = Objects.requireNonNull(assets);
        this.reads = Objects.requireNonNull(reads); this.powerup = Objects.requireNonNull(powerup);
        this.draw = Objects.requireNonNull(draw);
    }
    public static CdShieldPowerup withWorldOwner(CdReservedObjectBank bank, CdTimeStars.Assets assets,
                                                ReadPort reads, PowerupPort powerup, DrawReserved draw,
                                                DispatchObserver observer, ReceiptGuard guard) {
        var body = new CdShieldPowerup(bank, assets, reads, powerup, draw);
        body.observer = Objects.requireNonNull(observer); body.receiptGuard = Objects.requireNonNull(guard);
        return body;
    }
    public CdTimeStars.Assets assets() { return assets; }
    public boolean isCurrentCall(Object call) { return running && call != null && call == pendingOperation; }
    /** Only true during the actual original operation, never in observer/guard callbacks. */
    public boolean isCurrentPrimitive(Object call) { return isCurrentCall(call) && primitiveActive; }
    public long currentOrdinal(Object call) {
        if (!isCurrentCall(call)) throw new IllegalStateException("No original pending ShieldPowerup operation");
        return operationOrdinal;
    }
    /** Integer for a completed read, null for a completed void primitive; not an adoption receipt. */
    public Object currentResult(Object call) {
        if (!isCurrentCall(call) || !resultReady || primitiveActive)
            throw new IllegalStateException("No completed original ShieldPowerup operation");
        return operationResult;
    }

    /** Exactly one real physical visit, not a scheduler and not an award/revisit operation. */
    public Result run(int wholeIndex) {
        if (running) throw new IllegalStateException("Reentrant ShieldPowerup invocation");
        if (wholeIndex != WHOLE_INDEX) throw new IllegalArgumentException("ShieldPowerup requires reserved whole6");
        running = true; operationOrdinal = 0;
        try {
            var source = bank.state(WHOLE_INDEX);
            if (source.unsignedByte(ID) != OBJECT_ID) throw new IllegalArgumentException("ShieldPowerup requires actual ID3");
            var c = new Cursor(source);
            int routine = source.unsignedByte(ROUTINE);
            switch (routine) {
                case 0 -> initialize(c);
                case 2 -> shield(c);
                case 4 -> throw unsupported(UnsupportedBoundary.INVINCIBILITY_STARS_ROUTINE, routine);
                case 6 -> throw unsupported(UnsupportedBoundary.TIME_STARS_ROUTINE, routine);
                default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, routine);
            }
            return new Result(c.state, c.effects);
        } finally { running = false; }
    }

    private void initialize(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.INIT));
        c.state = c.state.moveByte(ROUTINE, c.state.unsignedByte(ROUTINE) + 2); publish(c);
        c.state = c.state.moveLong(MAPPINGS, assets.mappingAddressBits()); publish(c);
        c.state = c.state.moveByte(SPRITE_FLAGS, 4); publish(c);
        c.state = c.state.moveByte(PRIORITY, 1); publish(c);
        c.state = c.state.moveByte(WIDTH, 0x10); publish(c);
        c.state = c.state.moveWord(TILE, 0x544); publish(c);
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != 0) {
            c.state = c.state.moveByte(ROUTINE, c.state.unsignedByte(ROUTINE) + 2); publish(c);
            if (animation >= 5) {
                c.state = c.state.moveByte(ROUTINE, c.state.unsignedByte(ROUTINE) + 2); publish(c);
            }
        }
        // .End RTS: no fallthrough to Shield, animation, selector or wrapper Draw.
    }

    private void shield(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.SHIELD));
        if (read(c, ReadField.SHIELD) == 0) {
            c.state = c.state.withBytes(new byte[CdReservedObjectBank.BYTE_SIZE]); publish(c);
            c.effects.add(new Deleted()); return; // DeleteObject tail jump; no wrapper Draw.
        }
        if (read(c, ReadField.TIME_WARP) != 0) return;
        if (read(c, ReadField.INVINCIBLE) != 0) return;
        int x = read(c, ReadField.PLAYER_X_WORD);
        c.state = c.state.moveWord(X, x); publish(c);
        int y = read(c, ReadField.PLAYER_Y_WORD);
        c.state = c.state.moveWord(Y, y); publish(c);
        int flags = read(c, ReadField.PLAYER_FLAGS);
        c.state = c.state.moveByte(FLAGS, flags); publish(c);
        if (read(c, ReadField.ZONE) == 6) {
            c.state = c.state.moveByteAt(2, c.state.byteAt(2) | 0x80); publish(c);
            if (read(c, ReadField.LAYER) != 0) {
                c.state = c.state.moveByteAt(2, c.state.byteAt(2) & 0x7F); publish(c);
            }
        }
        animate(c);
        int actual = readPowerup(c);
        int selector = actual & 15;
        if (selector >= 8) return;
        if (selector != c.state.unsignedByte(ROUTINE)) {
            int routine = c.state.unsignedByte(ROUTINE);
            writePowerup(c, actual, routine);
            int atBset = readPowerup(c); // BSET's actual read-modify-write source, not a replay command.
            if (atBset != routine) throw new IllegalStateException("ShieldPowerup MOVE was not adopted by its owner");
            writePowerup(c, atBset, atBset | 0x80);
        }
        draw(c);
    }

    private void animate(Cursor c) {
        var original = c.state;
        var table = animationReadPrefixes(c);
        var result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME),
                original.unsignedByte(MAP_FRAME), original.unsignedByte(SPRITE_FLAGS), original.unsignedByte(FLAGS),
                original.unsignedByte(ROUTINE), original.unsignedByte(ROUTINE_2)), table);
        c.state = original.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer()).moveByte(MAP_FRAME, result.mapFrame())
                .moveByte(SPRITE_FLAGS, result.spriteFlags()).moveByte(FLAGS, result.statusFlags())
                .moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2());
        publish(c); c.effects.add(new AnimationReturned());
    }

    /** Reached reset/SUBQ/speed/FF/FE prefixes, as in the saved TimeStars implementation. */
    private CdObjectAnimation.AnimationTable animationReadPrefixes(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION)) {
            c.state = c.state.moveByte(PREVIOUS_ANIMATION, animation); publish(c);
            c.state = c.state.moveByte(ANIMATION_FRAME, 0); publish(c);
            c.state = c.state.moveByte(ANIMATION_TIME, 0); publish(c);
        }
        c.state = c.state.moveByte(ANIMATION_TIME, (c.state.unsignedByte(ANIMATION_TIME) - 1) & 255); publish(c);
        var table = assets.animations();
        if (c.state.signedByte(ANIMATION_TIME) >= 0) return table;
        if (animation >= table.scriptCount()) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_ADDRESS, 2 * animation);
        int script = (short) table.bytes().unsignedWord(2 * animation);
        int speed = animationByte(table, UnsupportedBoundary.ANIMATION_SPEED_ADDRESS, script);
        c.state = c.state.moveByte(ANIMATION_TIME, speed); publish(c);
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        if (frame == 0xFF) {
            c.state = c.state.moveByte(ANIMATION_FRAME, 0); publish(c);
            animationByte(table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1);
        } else if (frame == 0xFE) {
            int back = animationByte(table, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
            index = (index - back) & 255; c.state = c.state.moveByte(ANIMATION_FRAME, index); publish(c);
            animationByte(table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        } else if (frame == 0xFD) {
            animationByte(table, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
        }
        return table;
    }
    private static int animationByte(CdObjectAnimation.AnimationTable table, UnsupportedBoundary boundary, int offset) {
        if (offset < 0 || offset >= table.bytes().length()) throw unsupported(boundary, offset);
        return table.bytes().unsignedByte(offset);
    }

    private int read(Cursor c, ReadField field) {
        var cut = callbackCut(c);
        int result = observed(new ReadCall(cut, c.state, field), () -> {
            Throwable original = null;
            try { int value = reads.read(field); readValue(field, value); return value; }
            catch (RuntimeException | Error failure) { original = failure; throw failure; }
            finally { verifyCallback(c, cut, false, original); }
        });
        c.effects.add(new ValueRead(field, result)); return result;
    }
    private int readPowerup(Cursor c) {
        var cut = callbackCut(c);
        int result = observed(new PowerupReadCall(cut, c.state), () -> {
            Throwable original = null;
            try { int value = powerup.read(); byteValue(value); return value; }
            catch (RuntimeException | Error failure) { original = failure; throw failure; }
            finally { verifyCallback(c, cut, false, original); }
        });
        c.effects.add(new PowerupRead(result)); return result;
    }
    private void writePowerup(Cursor c, int previousValue, int value) {
        var cut = callbackCut(c);
        observed(new PowerupWriteCall(cut, c.state, previousValue, value), () -> {
            Throwable original = null;
            try { powerup.write(value); }
            catch (RuntimeException | Error failure) { original = failure; throw failure; }
            finally { verifyCallback(c, cut, false, original); }
            return null;
        });
        c.effects.add(new PowerupWritten(previousValue, value));
    }
    private void draw(Cursor c) {
        var cut = callbackCut(c);
        observed(new DrawCall(cut, c.state), () -> {
            Throwable original = null;
            try { draw.drawObject(bank, c.state.identity()); }
            catch (RuntimeException | Error failure) { original = failure; throw failure; }
            finally { verifyCallback(c, cut, true, original); }
            c.state = bank.state(WHOLE_INDEX); c.published = c.state;
            return null;
        });
        c.effects.add(new DrawReturned());
    }
    private CdReservedObjectBank.Snapshot callbackCut(Cursor c) {
        var cut = bank.capture();
        if (!cut.slots().get(WHOLE_INDEX - 1).equals(c.state))
            throw new IllegalStateException("ShieldPowerup source changed before its synchronous callback");
        return cut;
    }
    private void verifyCallback(Cursor c, CdReservedObjectBank.Snapshot cut, boolean drawBit7, Throwable original) {
        try {
            var allowed = drawBit7 ? c.state.moveByte(SPRITE_FLAGS,
                    (c.state.unsignedByte(SPRITE_FLAGS) & 0x7F) | (bank.state(WHOLE_INDEX).unsignedByte(SPRITE_FLAGS) & 0x80)) : c.state;
            verifyBank(cut, allowed);
        } catch (RuntimeException | Error verification) {
            if (original != null) { if (verification != original) original.addSuppressed(verification); }
            else throw verification;
        }
    }
    private void verifyBank(CdReservedObjectBank.Snapshot cut, CdReservedObjectBank.Image ownOutput) {
        bank.validateRestore(cut);
        var actual = bank.capture();
        for (int whole = 1; whole < CdReservedObjectBank.END_WHOLE_INDEX; whole++) {
            var expected = whole == WHOLE_INDEX ? ownOutput : cut.slots().get(whole - 1);
            if (!expected.equals(actual.slots().get(whole - 1)))
                throw new IllegalStateException("ShieldPowerup changed an unowned reserved value at " + whole);
        }
    }
    private void publish(Cursor c) {
        var cut = bank.capture(); var source = cut.slots().get(WHOLE_INDEX - 1); var output = c.state;
        if (!source.equals(c.published)) throw new IllegalStateException("ShieldPowerup publication lost its original source");
        observed(new Publication(cut, source, output), () -> {
            bank.write(output); verifyBank(cut, output); return null;
        });
        c.published = output;
    }

    @FunctionalInterface private interface Operation<T> { T run(); }
    private <T> T observed(Call call, Operation<T> operation) {
        if (observer == null) return operation.run();
        if (pendingOperation != null) throw new IllegalStateException("Nested ShieldPowerup producer");
        pendingOperation = call; operationOrdinal++; resultReady = false;
        try {
            observer.beforeOperation(call); receiptGuard.prepared(call);
            verifyBank(call.cut(), call.source());
            T result;
            primitiveActive = true;
            try { result = operation.run(); } finally { primitiveActive = false; }
            operationResult = result; resultReady = true;
            var completedCut = bank.capture();
            observer.afterOperation(call); receiptGuard.completed(call);
            verifyBank(completedCut, completedCut.slots().get(WHOLE_INDEX - 1));
            return result;
        } catch (RuntimeException | Error original) {
            try {
                var failedCut = bank.capture();
                try { observer.failedOperation(call, original); }
                finally { verifyBank(failedCut, failedCut.slots().get(WHOLE_INDEX - 1)); }
            } catch (RuntimeException | Error verification) {
                if (verification != original) original.addSuppressed(verification);
            }
            throw original;
        } finally {
            primitiveActive = false; pendingOperation = null; operationResult = null; resultReady = false;
        }
    }
    private static UnsupportedShieldPowerup unsupported(UnsupportedBoundary boundary, int value) {
        return new UnsupportedShieldPowerup(boundary, value);
    }
    private static final class Cursor {
        final ArrayList<Effect> effects = new ArrayList<>();
        CdReservedObjectBank.Image state, published;
        Cursor(CdReservedObjectBank.Image source) { state = source; published = source; }
    }
    private static void readValue(ReadField field, int value) {
        if (field == ReadField.PLAYER_X_WORD || field == ReadField.PLAYER_Y_WORD) {
            if ((value & ~65535) != 0) throw new IllegalArgumentException("Expected unsigned native player word");
        } else byteValue(value);
    }
    private static void byteValue(int value) {
        if ((value & ~255) != 0) throw new IllegalArgumentException("Expected unsigned native byte");
    }
}
