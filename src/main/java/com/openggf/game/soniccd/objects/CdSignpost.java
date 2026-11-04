package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/**
 * One selected native ObjSignpost ($17), not an act-completion or Results-body owner.
 * Pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/_Objects/Level End/Main.asm:708-907,
 * Object Animate.asm:14-95, Object Spawner.asm:329-340 and Object Functions.asm:137-196.
 * No FixBugs conditional changes this body. Ordinary LevelEndSpawn is FindObjSlot (:89-98).
 *
 * <p>Actual pool writes are completed native prefixes. Reached external reads, stores and sound
 * calls are synchronous typed ports over a fresh complete placement/source cut; these public values
 * confer no invocation, Frame, source, phase or exactly-once authority. Root must authenticate its
 * private producer and complete current external owners, prepare every immutable aggregate before
 * adoption, and reject unrelated stores. This component owns no global bank, timer defaults, player,
 * art upload, input, clock, sound execution or Results dispatch. Effects observe completed work only.
 * A failed later port preserves every preceding store and the identical original exception.
 *
 * <p>The wrapper calls DrawObject unconditionally after a returned routine and has NO despawn tail
 * (:708-714). Do not append GoalPost's LevelEndDespawn or infer deletion from camera/time-zone values.
 */
public final class CdSignpost {
    public static final int OBJECT_ID = 0x17;
    public static final int RESULTS_ID = 0x3A;

    /** Finite ROM format views, not art readiness, authentication or a new producer/bank. */
    public record Assets(CdObjectVisualAssets visual, int mappingsAddress, CdAssetBytes animations,
                         CdAssetBytes timeBonuses) {
        public Assets {
            Objects.requireNonNull(visual); Objects.requireNonNull(animations); Objects.requireNonNull(timeBonuses);
            if (visual.mappingFrames().size() != 6 || !visual.animations().isEmpty())
                throw new IllegalArgumentException("Signpost uses the six common static mapping frames");
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces())
                if (piece.tileOffset() > 76 - piece.tileCount())
                    throw new IllegalArgumentException("Signpost mapping exceeds its seventy-six native source tiles");
        }
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "actual Past assets");
            var map = owner.view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST);
            return new Assets(CdObjectVisualAssets.decodeStatic(map.bytes().copy(), 6, 76), map.range().nativeAddress(),
                    owner.view(CdR11bAssets.Asset.ANI_SIGNPOST).bytes(),
                    owner.view(CdR11bAssets.Asset.LEVEL_END_TIME_BONUSES).bytes());
        }
    }
    public enum NativeRoutine { INIT, MAIN, SPIN, START_RESULTS, RESULTS_ACTIVE }
    /** Each operand is borrowed only where the native instruction is actually reached. */
    public enum ReadField {
        ZONE_BYTE(8), PLAYER_Y_WORD(16), PLAYER_X_WORD(16), CAMERA_X_WORD(16), TIME_ZONE_BYTE(8),
        ZONE_ACT_WORD(16), TIME_MINUTES_BYTE(8), TIME_SECONDS_BYTE(8), RINGS_WORD(16);
        private final int widthBits;
        ReadField(int widthBits) { this.widthBits = widthBits; }
        public int widthBits() { return widthBits; }
    }
    /** CONTROL_LOCK_BIT0 means native BSET #0: retain every other current ctrlLocked bit. */
    public enum StoreField {
        LEFT_BOUND_WORD(16), DEST_LEFT_BOUND_WORD(16), UPDATE_HUD_TIME_BYTE(8), SPEED_SHOES_BYTE(8),
        INVINCIBLE_BYTE(8), CONTROL_LOCK_BIT0(8), PLAYER_CONTROL_HOLD_WORD(16), UPDATE_HUD_BONUS_BYTE(8),
        TIME_BONUS_WORD(16), RING_BONUS_WORD(16);
        private final int widthBits;
        StoreField(int widthBits) { this.widthBits = widthBits; }
        public int widthBits() { return widthBits; }
    }
    public enum Sound {
        FM_SIGNPOST(0x9D), SCMD_FADEPCM(0x82), SCMD_RESULTMUS(0x6B);
        private final int nativeId;
        Sound(int nativeId) { this.nativeId = nativeId; }
        public int nativeId() { return nativeId; }
    }
    public record ReadCall(ReadField field, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public ReadCall { Objects.requireNonNull(field); requireSource(actualCut, expectedSource); }
    }
    public record StoreCall(StoreField field, int value, CdObjectPlacementRuntime.Snapshot actualCut,
                            CdObjectState expectedSource) {
        public StoreCall {
            Objects.requireNonNull(field); requireSource(actualCut, expectedSource); width(value, field.widthBits());
            if (field == StoreField.CONTROL_LOCK_BIT0 && value != 1)
                throw new IllegalArgumentException("Signpost's BSET operand is bit zero");
        }
    }
    public record SoundCall(Sound sound, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public SoundCall { Objects.requireNonNull(sound); requireSource(actualCut, expectedSource); }
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public DrawCall { requireSource(actualCut, expectedSource); }
    }
    @FunctionalInterface public interface NativeRead { int read(ReadCall call); }
    @FunctionalInterface public interface EndOfActStore { void store(StoreCall call); }
    @FunctionalInterface public interface SoundCallPort { void request(SoundCall call); }
    @FunctionalInterface public interface DrawObjectCall { void drawObject(DrawCall call); }

    public sealed interface Effect permits RoutineEntered, ReadReturned, StoreCompleted, SoundRequested,
            AnimationReturned, SlotSearchReturned, ChildCreated, DrawReturned { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { }
    public record ReadReturned(ReadField field, int value) implements Effect { }
    public record StoreCompleted(StoreField field, int value) implements Effect { }
    public record SoundRequested(Sound sound) implements Effect { }
    public record AnimationReturned() implements Effect { }
    public record SlotSearchReturned(OptionalInt wholeIndex) implements Effect { }
    public record ChildCreated(CdObjectState state) implements Effect { }
    public record DrawReturned() implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, READ_OWNER, STORE_OWNER, SOUND_OWNER, READ_VALUE_WIDTH,
        ANIMATION_DIRECTORY_READ, ANIMATION_SPEED_READ, ANIMATION_FRAME_READ, ANIMATION_OPERAND_READ,
        RESULTS_ALLOCATION_ADDRESS, TIME_BONUS_READ
    }
    public static final class UnsupportedSignpost extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedSignpost(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Signpost slot " + wholeIndex + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final NativeRead nativeRead;
    private final EndOfActStore endOfActStore;
    private final SoundCallPort soundCall;
    private final DrawObjectCall drawObject;
    private boolean running;

    /** Missing external owners are fenced only at their first genuinely reached native operation. */
    public CdSignpost(CdObjectPlacementRuntime placement, Assets assets, NativeRead nativeRead,
                      EndOfActStore endOfActStore, SoundCallPort soundCall, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.nativeRead = nativeRead; this.endOfActStore = endOfActStore; this.soundCall = soundCall;
        this.drawObject = Objects.requireNonNull(drawObject);
    }
    public Result run(int wholeIndex) {
        if (running) throw new IllegalStateException("Reentrant ObjSignpost dispatch");
        var state = placement.pool().state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Selected slot is not Signpost $17");
        if ((state.routine() & 1) != 0 || state.routine() > 8)
            throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, state.routine());
        running = true;
        try {
            var c = new Cursor(state);
            switch (state.routine()) {
                case 0 -> init(c);
                case 2 -> main(c);
                case 4 -> spin(c);
                case 6 -> startResults(c);
                case 8 -> c.effects.add(new RoutineEntered(NativeRoutine.RESULTS_ACTIVE));
            }
            draw(c); // ObjSignpost:714 is the unconditional tail; no CheckObjDespawn follows it.
            return new Result(c.state, c.effects);
        } finally { running = false; }
    }
    private void init(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.INIT));
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(X_RADIUS, 24).moveByte(WIDTH, 24).moveByte(Y_RADIUS, 32)
                .moveByte(PRIORITY, 4).moveWord(TILE, 0x043C));
        // ORI.B addresses the HIGH tile byte at offset2 (:749), hence word bit15, not low-byte bit7.
        if (read(c, ReadField.ZONE_BYTE) != 3) c.write(c.state.moveWord(TILE, c.state.word(TILE) | 0x8000));
        c.write(c.state.moveLong(MAPPINGS, assets.mappingsAddress()));
        main(c); // Native Init falls through into Main without another dispatch.
    }
    private void main(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.MAIN));
        int dy = (read(c, ReadField.PLAYER_Y_WORD) - c.state.centreY() + 128) & 0xFFFF;
        if ((short) dy < 0 || dy >= 256) return;
        if (c.state.centreX() >= read(c, ReadField.PLAYER_X_WORD)) return; // CMP.W/BCC: STRICT unsigned playerX > signX.
        store(c, StoreField.LEFT_BOUND_WORD, read(c, ReadField.CAMERA_X_WORD));
        store(c, StoreField.DEST_LEFT_BOUND_WORD, read(c, ReadField.CAMERA_X_WORD));
        store(c, StoreField.UPDATE_HUD_TIME_BYTE, 0);
        c.write(c.state.moveByte(LEVEL_END_TIMER, 120).moveByte(MAP_FRAME, 0).addByte(ROUTINE, 2));
        store(c, StoreField.SPEED_SHOES_BYTE, 0);
        store(c, StoreField.INVINCIBLE_BYTE, 0);
        sound(c, Sound.FM_SIGNPOST);
    }
    private void spin(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.SPIN));
        animate(c);
        c.write(c.state.addByte(LEVEL_END_TIMER, -1));
        if (c.state.unsignedByte(LEVEL_END_TIMER) == 0)
            c.write(c.state.addByte(ROUTINE, 2).moveByte(MAP_FRAME, 3).moveByte(LEVEL_END_TIMER, 60));
    }
    private void startResults(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.START_RESULTS));
        c.write(c.state.addByte(LEVEL_END_TIMER, -1));
        if (c.state.unsignedByte(LEVEL_END_TIMER) != 0) return;
        if (read(c, ReadField.TIME_ZONE_BYTE) == 0) sound(c, Sound.SCMD_FADEPCM);
        sound(c, Sound.SCMD_RESULTMUS);
        store(c, StoreField.CONTROL_LOCK_BIT0, 1);
        store(c, StoreField.PLAYER_CONTROL_HOLD_WORD, 0x0808);
        if (read(c, ReadField.ZONE_ACT_WORD) == 0x502) store(c, StoreField.PLAYER_CONTROL_HOLD_WORD, 0);
        c.write(c.state.moveByte(LEVEL_END_TIMER, 180).addByte(ROUTINE, 2));
        var found = placement.pool().findObjSlot();
        c.effects.add(new SlotSearchReturned(found));
        // Native has NO BNE after FindObjSlot (:840-842). Failed a1 points beyond this owned dynamic pool.
        // Stop before that unowned address's first write; do not emulate corrupt RAM or invent a child.
        if (found.isEmpty()) throw unsupported(UnsupportedBoundary.RESULTS_ALLOCATION_ADDRESS, c.whole, CdObjectPool.END_WHOLE_INDEX);
        int childWhole = found.getAsInt();
        placement.pool().write(placement.pool().state(childWhole).moveByte(ID, RESULTS_ID));
        placement.pool().write(placement.pool().state(childWhole).moveByte(RESULTS_TIMER, 16));
        c.effects.add(new ChildCreated(placement.pool().state(childWhole)));
        store(c, StoreField.UPDATE_HUD_BONUS_BYTE, 1);
        int seconds = read(c, ReadField.TIME_MINUTES_BYTE) * 60;
        seconds = (seconds + read(c, ReadField.TIME_SECONDS_BYTE)) & 0xFFFF;
        int index = Math.min(seconds / 15, 20); // DIVU.W and the literal native directory maximum (:845-855).
        int at = index * 2;
        if (at > assets.timeBonuses().length() - 2) throw unsupported(UnsupportedBoundary.TIME_BONUS_READ, c.whole, at);
        store(c, StoreField.TIME_BONUS_WORD, assets.timeBonuses().unsignedWord(at));
        store(c, StoreField.RING_BONUS_WORD, (read(c, ReadField.RINGS_WORD) * 100) & 0xFFFF);
    }

    /** Shared owner computes successful frame/command transitions; literal prefixes survive selected read faults. */
    private void animate(Cursor c) {
        var original = c.state;
        animationHead(c); // Real compare/reset/SUBQ.B stores occur before any script directory read.
        boolean held = c.state.signedByte(ANIMATION_TIME) >= 0;
        if (assets.animations().length() < 2) {
            if (!held) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_READ, c.whole, 2 * c.state.unsignedByte(ANIMATION));
            // Shared AnimationTable's constructor requires a directory even for this native no-read return.
            // The already-published literal head is the ENTIRE native waiting path; no substitute table is supplied.
            c.effects.add(new AnimationReturned()); return;
        }
        CdObjectAnimation.State result;
        try {
            result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                    original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME),
                    original.unsignedByte(MAP_FRAME), original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)),
                    new CdObjectAnimation.AnimationTable(assets.animations(), 0, 1));
        } catch (UnsupportedOperationException originalFailure) {
            try { animationReadTail(c); }
            catch (UnsupportedSignpost reached) { reached.initCause(originalFailure); throw reached; }
            throw new IllegalStateException("Shared Signpost animation failure has no matching reached read", originalFailure);
        }
        c.write(original.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer()).moveByte(MAP_FRAME, result.mapFrame())
                .moveByte(SPRITE_FLAGS, result.spriteFlags()).moveByte(FLAGS, result.statusFlags())
                .moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2()));
        c.effects.add(new AnimationReturned());
    }
    private void animationHead(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION))
            c.write(c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0));
        c.write(c.state.addByte(ANIMATION_TIME, -1));
    }
    /** Reads/writes only up to a fault; never a fallback successful animation or a second shared step. */
    private void animationReadTail(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != 0) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_READ, c.whole, 2 * animation);
        int script = (short) assets.animations().unsignedWord(0);
        c.write(c.state.moveByte(ANIMATION_TIME, animationByte(c, script, UnsupportedBoundary.ANIMATION_SPEED_READ)));
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ);
        if (frame == 0xFF) {
            c.write(c.state.moveByte(ANIMATION_FRAME, 0));
            animationByte(c, script + 1, UnsupportedBoundary.ANIMATION_FRAME_READ);
        } else if (frame == 0xFE) {
            int back = animationByte(c, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ);
            index = (index - back) & 0xFF; c.write(c.state.moveByte(ANIMATION_FRAME, index));
            animationByte(c, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ);
        } else if (frame == 0xFD) animationByte(c, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ);
    }
    private int animationByte(Cursor c, int at, UnsupportedBoundary boundary) {
        if (at < 0 || at >= assets.animations().length()) throw unsupported(boundary, c.whole, at);
        return assets.animations().unsignedByte(at);
    }
    private int read(Cursor c, ReadField field) {
        if (nativeRead == null) throw unsupported(UnsupportedBoundary.READ_OWNER, c.whole, field.ordinal());
        var cut = capture(c); var call = new ReadCall(field, cut, c.state);
        int value = readValue(cut, () -> nativeRead.read(call));
        int mask = field.widthBits() == 8 ? 0xFF : 0xFFFF;
        if ((value & ~mask) != 0) throw unsupported(UnsupportedBoundary.READ_VALUE_WIDTH, c.whole, value);
        c.effects.add(new ReadReturned(field, value)); return value;
    }
    private void store(Cursor c, StoreField field, int value) {
        if (endOfActStore == null) throw unsupported(UnsupportedBoundary.STORE_OWNER, c.whole, field.ordinal());
        var cut = capture(c); var call = new StoreCall(field, value, cut, c.state);
        readOnly(cut, () -> endOfActStore.store(call)); c.effects.add(new StoreCompleted(field, value));
    }
    private void sound(Cursor c, Sound sound) {
        if (soundCall == null) throw unsupported(UnsupportedBoundary.SOUND_OWNER, c.whole, sound.nativeId());
        var cut = capture(c); var call = new SoundCall(sound, cut, c.state);
        readOnly(cut, () -> soundCall.request(call)); c.effects.add(new SoundRequested(sound));
    }
    private void draw(Cursor c) {
        var cut = capture(c); var call = new DrawCall(cut, c.state);
        var expected = CdObjectPool.overlay(cut.pool(), List.of(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() & 0x7F)));
        try { drawObject.drawObject(call); assertPlacement(cut, expected); }
        catch (RuntimeException | Error original) {
            suppress(original, () -> { if (!samePlacement(cut, cut.pool())) assertPlacement(cut, expected); }); throw original;
        }
        c.state = placement.pool().state(c.whole); c.effects.add(new DrawReturned());
    }
    private CdObjectPlacementRuntime.Snapshot capture(Cursor c) {
        var cut = placement.capture(); requireSource(cut, c.state); assertPlacement(cut, cut.pool()); return cut;
    }
    private void readOnly(CdObjectPlacementRuntime.Snapshot cut, Runnable callback) {
        assertPlacement(cut, cut.pool());
        try { callback.run(); assertPlacement(cut, cut.pool()); }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
    }
    private int readValue(CdObjectPlacementRuntime.Snapshot cut, IntSupplier callback) {
        assertPlacement(cut, cut.pool());
        try { int value = callback.getAsInt(); assertPlacement(cut, cut.pool()); return value; }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        var now = placement.capture(); return now.status().equals(cut.status())
                && Arrays.equals(now.savedFlags().copy(), cut.savedFlags().copy()) && now.pool().slots().equals(expectedPool.slots());
    }
    private void assertPlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        placement.validateRestore(cut); placement.pool().validateRestore(expectedPool);
        if (!samePlacement(cut, expectedPool)) throw new IllegalStateException("Signpost callback changed its actual full placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("Signpost source is not this full placement capture's actual image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error failed) { if (failed != original) original.addSuppressed(failed); }
    }
    private static UnsupportedSignpost unsupported(UnsupportedBoundary boundary, int whole, int value) {
        return new UnsupportedSignpost(boundary, whole, value);
    }
    private final class Cursor {
        final int whole; final List<Effect> effects = new ArrayList<>(); CdObjectState state;
        Cursor(CdObjectState state) { this.state = state; whole = state.identity().wholeIndex(); }
        void write(CdObjectState next) { placement.pool().write(next); state = next; }
    }
    private static void width(int value, int bits) {
        int mask = bits == 8 ? 0xFF : 0xFFFF;
        if ((value & ~mask) != 0) throw new IllegalArgumentException("Expected unsigned native " + bits + "-bit value");
    }
}
