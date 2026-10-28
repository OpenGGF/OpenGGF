package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjPowerup Init/TimeStars over the existing reserved bank, native pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Powerup/Main.asm:21-36,88-152.
 * The caller supplies the actual player history and globals at each physical visit.
 * No allocation, actor store, art service, sound, clock, capture owner or runtime
 * execution authority belongs here. Shield/InvStars routines remain unadmitted.
 *
 * <p>Stores precede their reached reads and synchronous calls. A failed call keeps
 * those published prefixes; no rollback or replay is performed. Callback guards
 * observe full-bank values, not atomic custody or an ABA/exact-once certificate.
 * Effects describe completed operations only and must never be executed as commands.</p>
 */
public final class CdTimeStars {
    public static final int OBJECT_ID = 3;
    public static final int FIRST_WHOLE_INDEX = 12;
    public static final int END_WHOLE_INDEX = 16;

    /** Directory structure only; every selected script byte remains a reached read. */
    public record Assets(CdObjectAnimation.AnimationTable animations, int mappingAddressBits) {
        public Assets {
            Objects.requireNonNull(animations, "animations");
            if (animations.tableOffset() != 0 || animations.scriptCount() != 9)
                throw new IllegalArgumentException("Powerup requires its nine-entry animation directory at base zero");
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets objects) {
            Objects.requireNonNull(objects, "authenticated object assets");
            var animations = objects.view(CdR11aObjectAssets.Table.POWERUP_ANIMATIONS);
            var mappings = objects.view(CdR11aObjectAssets.Table.POWERUP_MAPPINGS);
            return new Assets(new CdObjectAnimation.AnimationTable(animations.bytes(), 0, 9),
                    mappings.range().nativeAddress());
        }
    }

    /** Actual borrowed immutable history, never a default table or retained snapshot. */
    public record Globals(int zone, int timeWarp, int playerFlags, CdAssetBytes history, int historyIndexWord) {
        public Globals {
            byteValue(zone); byteValue(timeWarp); byteValue(playerFlags); wordValue(historyIndexWord);
            Objects.requireNonNull(history, "actual player position history");
        }
    }
    public interface PowerupPort { int read(); void write(int value); }
    @FunctionalInterface public interface DrawReserved {
        void drawObject(CdReservedObjectBank bank, CdReservedObjectBank.Identity identity);
    }
    public enum NativeRoutine { INIT, TIME_STARS }
    public sealed interface Effect permits RoutineEntered, AnimationReturned, PowerupWritten, DrawReturned, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record AnimationReturned() implements Effect { }
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
        ROUTINE_DISPATCH, LAYER_READ, HISTORY_X_ADDRESS, HISTORY_Y_ADDRESS,
        ANIMATION_DIRECTORY_ADDRESS, ANIMATION_SPEED_ADDRESS, ANIMATION_FRAME_ADDRESS, ANIMATION_OPERAND_ADDRESS
    }
    public static final class UnsupportedTimeStars extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedTimeStars(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super("TimeStars " + boundary + " at whole " + wholeIndex + ": " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdReservedObjectBank bank;
    private final Assets assets;
    private final PowerupPort powerup;
    private final DrawReserved draw;
    private boolean running;

    public CdTimeStars(CdReservedObjectBank bank, Assets assets, PowerupPort powerup, DrawReserved draw) {
        this.bank = Objects.requireNonNull(bank); this.assets = Objects.requireNonNull(assets);
        this.powerup = Objects.requireNonNull(powerup); this.draw = Objects.requireNonNull(draw);
    }

    /** One real ID3 physical visit. Only the four native time-star slots are admitted. */
    public Result run(int wholeIndex, Globals globals) {
        Objects.requireNonNull(globals);
        if (running) throw new IllegalStateException("Reentrant TimeStars invocation");
        if (wholeIndex < FIRST_WHOLE_INDEX || wholeIndex >= END_WHOLE_INDEX)
            throw new IllegalArgumentException("TimeStars requires reserved whole12..15");
        var source = bank.state(wholeIndex);
        if (source.unsignedByte(ID) != OBJECT_ID) throw new IllegalArgumentException("TimeStars requires actual ID3");
        running = true;
        try {
            var c = new Cursor(source);
            switch (source.unsignedByte(ROUTINE)) {
                case 0 -> initialize(c);
                case 6 -> active(c, globals);
                default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, source.unsignedByte(ROUTINE));
            }
            return new Result(c.state, c.effects);
        } finally { running = false; }
    }

    private void initialize(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.INIT));
        c.state = c.state.moveByte(ROUTINE, 2).moveLong(MAPPINGS, assets.mappingAddressBits())
                .moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 1).moveByte(WIDTH, 0x10).moveWord(TILE, 0x544);
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != 0) {
            c.state = c.state.moveByte(ROUTINE, 4);
            if (animation >= 5) c.state = c.state.moveByte(ROUTINE, 6);
        }
        publish(c); // No history, animation, powerup or Draw call in Init.
    }

    private void active(Cursor c, Globals globals) {
        c.effects.add(new RoutineEntered(NativeRoutine.TIME_STARS));
        if (globals.timeWarp() == 0) {
            c.state = c.state.withBytes(new byte[CdReservedObjectBank.BYTE_SIZE]); publish(c);
            c.effects.add(new Deleted()); return;
        }
        if (globals.zone() == 6) {
            // ORI.B addresses HIGH oTile. The first unowned action is the following layer BYTE read.
            c.state = c.state.moveByteAt(2, c.state.byteAt(2) | 0x80); publish(c);
            throw unsupported(UnsupportedBoundary.LAYER_READ, c, 6);
        }
        int animationDelta = (c.state.unsignedByte(ANIMATION) - 1) & 255;
        if (animationDelta >= 4) animationDelta = (animationDelta - 4) & 255;
        animationDelta = (animationDelta << 3) & 255;
        animationDelta = (animationDelta * 3 + 4) & 255;
        int phase = c.state.byteAt(0x30);
        int offset = (short) ((globals.historyIndexWord() & 0xFF00)
                | ((globals.historyIndexWord() - animationDelta - phase) & 255));
        int nextPhase = (phase + 4) & 255;
        if (nextPhase >= 0x18) nextPhase = 0;
        c.state = c.state.moveByteAt(0x30, nextPhase); publish(c);
        c.state = c.state.moveWord(X, historyWord(c, globals.history(), offset, UnsupportedBoundary.HISTORY_X_ADDRESS)); publish(c);
        c.state = c.state.moveWord(Y, historyWord(c, globals.history(), offset + 2, UnsupportedBoundary.HISTORY_Y_ADDRESS)); publish(c);
        c.state = c.state.moveByte(FLAGS, globals.playerFlags()); publish(c);
        animate(c);
        int actualPowerup = readPowerup(c);
        int selector = actualPowerup & 15;
        if (selector >= 8) return;
        if (selector != c.state.unsignedByte(ROUTINE)) {
            int routine = c.state.unsignedByte(ROUTINE);
            writePowerup(c, actualPowerup, routine);
            int atBset = readPowerup(c);
            if (atBset != routine) throw new IllegalStateException("TimeStars powerup MOVE was not adopted by its owner");
            writePowerup(c, atBset, atBset | 0x80);
        }
        draw(c);
    }

    private static int historyWord(Cursor c, CdAssetBytes history, int offset, UnsupportedBoundary boundary) {
        if (offset < 0 || (offset & 1) != 0 || offset > history.length() - 2)
            throw unsupported(boundary, c, offset);
        return history.unsignedWord(offset);
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

    /** Reached reset/SUBQ/speed/FF/FE prefixes only; the one shared animator computes final successful state. */
    private CdObjectAnimation.AnimationTable animationReadPrefixes(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION))
            c.state = c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
        c.state = c.state.moveByte(ANIMATION_TIME, (c.state.unsignedByte(ANIMATION_TIME) - 1) & 255); publish(c);
        var table = assets.animations();
        if (c.state.signedByte(ANIMATION_TIME) >= 0) return table;
        if (animation >= table.scriptCount()) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_ADDRESS, c, 2 * animation);
        int script = (short) table.bytes().unsignedWord(2 * animation);
        int speed = animationByte(c, table, UnsupportedBoundary.ANIMATION_SPEED_ADDRESS, script);
        c.state = c.state.moveByte(ANIMATION_TIME, speed); publish(c);
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        if (frame == 0xFF) {
            c.state = c.state.moveByte(ANIMATION_FRAME, 0); publish(c);
            animationByte(c, table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1);
        } else if (frame == 0xFE) {
            int back = animationByte(c, table, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
            index = (index - back) & 255; c.state = c.state.moveByte(ANIMATION_FRAME, index); publish(c);
            animationByte(c, table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        } else if (frame == 0xFD) {
            animationByte(c, table, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
        }
        return table;
    }

    private static int animationByte(Cursor c, CdObjectAnimation.AnimationTable table, UnsupportedBoundary boundary, int offset) {
        if (offset < 0 || offset >= table.bytes().length()) throw unsupported(boundary, c, offset);
        return table.bytes().unsignedByte(offset);
    }

    private int readPowerup(Cursor c) {
        var cut = callbackCut(c); Throwable original = null;
        try {
            int value = powerup.read(); byteValue(value); return value;
        } catch (RuntimeException | Error failure) { original = failure; throw failure; }
        finally { verifyCallback(c, cut, false, original); }
    }

    private void writePowerup(Cursor c, int previousValue, int value) {
        var cut = callbackCut(c); Throwable original = null;
        try { powerup.write(value); }
        catch (RuntimeException | Error failure) { original = failure; throw failure; }
        finally { verifyCallback(c, cut, false, original); }
        c.effects.add(new PowerupWritten(previousValue, value));
    }

    private void draw(Cursor c) {
        var cut = callbackCut(c); Throwable original = null;
        try { draw.drawObject(bank, c.state.identity()); }
        catch (RuntimeException | Error failure) { original = failure; throw failure; }
        finally {
            c.state = bank.state(c.wholeIndex);
            verifyCallback(c, cut, true, original);
        }
        c.effects.add(new DrawReturned());
    }

    private CdReservedObjectBank.Snapshot callbackCut(Cursor c) {
        var cut = bank.capture();
        if (!cut.slots().get(c.wholeIndex - 1).equals(c.state))
            throw new IllegalStateException("TimeStars source changed before its synchronous callback");
        return cut;
    }

    private void verifyCallback(Cursor c, CdReservedObjectBank.Snapshot cut, boolean drawMayChangeBit7, Throwable original) {
        try {
            bank.validateRestore(cut);
            for (int whole = 1; whole < CdReservedObjectBank.END_WHOLE_INDEX; whole++) {
                var before = cut.slots().get(whole - 1); var actual = bank.state(whole);
                var allowed = drawMayChangeBit7 && whole == c.wholeIndex
                        ? before.moveByte(SPRITE_FLAGS, (before.unsignedByte(SPRITE_FLAGS) & 0x7F) | (actual.unsignedByte(SPRITE_FLAGS) & 0x80))
                        : before;
                if (!allowed.equals(actual)) throw new IllegalStateException("TimeStars callback changed an unowned reserved image at " + whole);
            }
        } catch (RuntimeException | Error verification) {
            if (original != null) original.addSuppressed(verification); else throw verification;
        }
    }

    private void publish(Cursor c) { bank.write(c.state); }
    private static UnsupportedTimeStars unsupported(UnsupportedBoundary boundary, Cursor c, int value) {
        return new UnsupportedTimeStars(boundary, c.wholeIndex, value);
    }
    private static final class Cursor {
        final int wholeIndex;
        final ArrayList<Effect> effects = new ArrayList<>();
        CdReservedObjectBank.Image state;
        Cursor(CdReservedObjectBank.Image state) { this.state = state; wholeIndex = state.identity().wholeIndex(); }
    }
    private static void byteValue(int value) {
        if ((value & ~255) != 0) throw new IllegalArgumentException("Expected unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~65535) != 0) throw new IllegalArgumentException("Expected unsigned native word");
    }
}
