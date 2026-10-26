package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * One selected native ID $13 Checkpoint dispatch, never a frame or live pool walk.
 * N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * src/Level/_Objects/Checkpoint/Main.asm:7-189; Object Spawner.asm:329-340,385-446;
 * Object Functions.asm:11-31,137-196,228-237; Object Animate.asm:14-95;
 * Functions (Misc).asm:69-76. The Checkpoint body has no FixBugs alternative.
 *
 * <p>Init returns to its wrapper without Main fallthrough. Allocation borrows the
 * existing 96-slot pool, including dirty ID-zero RAM. The raw full parent long
 * stays in that RAM; GlobalPort resolves only the actually addressed parent byte.
 * No parent entity, generation, ID/alignment check or missing-byte zero exists.
 * Spin and successful animation borrow the shared math and animation owners.
 *
 * <p>Contact publishes its object prefix, then two separate native level stores,
 * then exactly one synchronous save and FM request, then wrapper DrawObject and
 * despawn. Callback failure preserves completed prefixes and escapes unchanged;
 * no rollback, resume, retry, wrapper tail or effect replay is attempted. Returned
 * records describe completed work only. Same-value stores remain distinct calls.
 *
 * <p>Root owns actual global/save publication, phase/full-owner/source/CAS checks,
 * water/time/camera sources, FM queue admission, actual draw queue, failure closure,
 * later physical visits, capture/replay and restart. Value equality here cannot
 * authenticate a port or prove a claimed same-value store occurred. No retained
 * captures, new global store, art bank, residency, Frame/Runtime/World or native
 * sound execution is supplied. All author tests and compilation remain UNRUN.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdCheckpoint {
    public static final int OBJECT_ID = 0x13;

    public enum NativeRoutine { INIT, MAIN, BALL, ANIMATE }
    public enum NativeCall { CALC_SINE, ANIMATE_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN }
    public enum GlobalField { CHECKPOINT, SPAWN_MODE }

    /** ALL rs.b declarations from $90 through FM_CHECKPOINT (_Sound IDs.inc:8-40). */
    public enum FmSound {
        FM_CHECKPOINT_AE(0xAE);
        private final int nativeId;
        FmSound(int nativeId) { this.nativeId = nativeId; }
        public int nativeId() { return nativeId; }
    }

    public sealed interface Effect permits RoutineEntered, CallReturned, AllocationAttempt,
            ChildCreated, GlobalStored, SaveCompleted, FmRequested, SavedFlagChanged, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** FindObjSlot returned once; empty means its native NE exhaustion path. */
    public record AllocationAttempt(OptionalInt wholeIndex) implements Effect {
        public AllocationAttempt {
            Objects.requireNonNull(wholeIndex, "wholeIndex");
            if (wholeIndex.isPresent()) new CdObjectState.Identity(wholeIndex.getAsInt());
        }
    }
    public record ChildCreated(CdObjectState state) implements Effect {
        public ChildCreated { Objects.requireNonNull(state, "state"); }
    }
    public record GlobalStored(GlobalCall call) implements Effect {
        public GlobalStored { Objects.requireNonNull(call, "call"); }
    }
    /** The save already returned and its actual published bytes were compared. */
    public record SaveCompleted(CdCheckpointSaveData.Result result) implements Effect {
        public SaveCompleted { Objects.requireNonNull(result, "result"); }
    }
    /** The synchronous request already returned; never send this observation again. */
    public record FmRequested(FmSound sound) implements Effect {
        public FmRequested { Objects.requireNonNull(sound, "sound"); }
    }
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record Deleted() implements Effect { }

    /**
     * Plain format inputs. Production uses fromObjectAssets on the actual retained
     * authenticated owner. Four counted frames, one script and twelve source tiles
     * do not grant an independently constructed value media or residency authority.
     * Init's tile is the native literal $06CB, never an ObjectTileIDs lookup.
     */
    public record Assets(int mappingAddressBits, CdObjectVisualAssets visual, CdPlayerMath math) {
        public Assets {
            Objects.requireNonNull(visual, "visual");
            Objects.requireNonNull(math, "math");
            if (visual.mappingFrames().size() != 4 || visual.animations().size() != 1) {
                throw new IllegalArgumentException("Checkpoint requires four counted frames and one animation");
            }
            for (var frame : visual.mappingFrames()) {
                for (var piece : frame.pieces()) {
                    if (piece.tileOffset() > 12 - piece.tileCount()) {
                        throw new IllegalArgumentException("Checkpoint mapping exceeds its twelve-tile source");
                    }
                }
            }
        }

        public static Assets fromObjectAssets(CdR11aObjectAssets owner, CdPlayerMath math) {
            Objects.requireNonNull(owner, "owner");
            return new Assets(owner.view(CdR11aObjectAssets.Table.CHECKPOINT_MAPPINGS).range().nativeAddress(),
                    owner.checkpoint(), math);
        }
    }

    /** Raw widths only: even $7F/$FF timeZone values are meaningful reached inputs. */
    public record DespawnGlobals(int rawTimeZoneByte, int rawDirectionByte, int cameraXWord) {
        public DespawnGlobals {
            byteValue(rawTimeZoneByte); byteValue(rawDirectionByte); wordValue(cameraXWord);
        }
    }

    /** Plain description of one reached store, not a retained execution/adoption token. */
    public record GlobalCall(CdObjectState expectedSourceObject, CdLevelGlobals expectedLevel,
                             CdLevelGlobals finalLevel, GlobalField field) {
        public GlobalCall {
            Objects.requireNonNull(expectedSourceObject, "expectedSourceObject");
            Objects.requireNonNull(expectedLevel, "expectedLevel");
            Objects.requireNonNull(finalLevel, "finalLevel"); Objects.requireNonNull(field, "field");
        }
    }

    /**
     * Root's one actual level-flow owner and actual addressed-byte reader. commit
     * must publish exactly one field synchronously, even for a same-value write.
     * readParentByte receives FULL wrapping address bits after the displacement;
     * the adapter resolves the 24-bit bus to its actual owned dynamic/reserved/player
     * bytes or throws UnownedParentByte at that read. Odd byte addresses are valid.
     */
    public interface GlobalPort {
        CdLevelGlobals current();
        void commit(GlobalCall call);
        int readParentByte(int rawAddressBits);
    }

    public record SaveCall(CdObjectState expectedSourceObject, CdLevelGlobals expectedLevel,
                           CdCheckpointSaveData.State expectedSaved) {
        public SaveCall {
            Objects.requireNonNull(expectedSourceObject, "expectedSourceObject");
            Objects.requireNonNull(expectedLevel, "expectedLevel");
            Objects.requireNonNull(expectedSaved, "expectedSaved");
        }
    }

    /**
     * Root calls the real CdCheckpointSaveData.save over its actual state and
     * reached read/commit port. Source values are live, independent water/time/
     * camera/player owners; missing values stop there, never become fake zeros.
     */
    public interface SaveDataPort {
        CdCheckpointSaveData.State currentState();
        CdCheckpointSaveData.Result save(SaveCall call);
    }
    @FunctionalInterface public interface FmCall { void request(FmSound sound); }
    @FunctionalInterface public interface DrawCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, ANIMATION_DIRECTORY_ENTRY, ANIMATION_SPEED_ADDRESS,
        ANIMATION_FRAME_ADDRESS, ANIMATION_OPERAND_ADDRESS, SAVED_FLAGS_ADDRESS
    }
    /** A finite source-view boundary, not a claim every such native CPU read faults. */
    public static final class UnsupportedCheckpoint extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedCheckpoint(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Checkpoint whole slot " + wholeIndex + ", reached value $"
                    + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    /** Actual byte ownership failed, with the full address requested by the body. */
    public static final class UnownedParentByte extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final int rawAddressBits;
        public UnownedParentByte(int rawAddressBits) {
            super(String.format("Unowned Checkpoint parent byte at raw $%08X / bus $%06X",
                    rawAddressBits, rawAddressBits & 0xFFFFFF));
            this.rawAddressBits = rawAddressBits;
        }
        public int rawAddressBits() { return rawAddressBits; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final GlobalPort global;
    private final SaveDataPort saveData;
    private final FmCall fm;
    private final DrawCall draw;
    private boolean running;

    public CdCheckpoint(CdObjectPlacementRuntime placement, Assets assets, GlobalPort global,
                         SaveDataPort saveData, FmCall fm, DrawCall draw) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.global = Objects.requireNonNull(global, "global");
        this.saveData = Objects.requireNonNull(saveData, "saveData");
        this.fm = Objects.requireNonNull(fm, "fm"); this.draw = Objects.requireNonNull(draw, "draw");
    }

    /** ObjCheckpoint:50-65: raw routine is a byte OFFSET into four relative words. */
    public Result run(int wholeIndex, DespawnGlobals globals) {
        if (running) throw new IllegalStateException("Checkpoint body cannot reenter its synchronous dispatch");
        running = true;
        try {
            Objects.requireNonNull(globals, "globals");
            var pool = placement.pool(); var state = pool.state(wholeIndex);
            if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Checkpoint requires live ID $13");
            var c = new Cursor(pool, state);
            int routine = state.routine();
            if ((routine & 1) != 0 || routine > 6) {
                throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, routine);
            }
            switch (routine) {
                case 0 -> init(c);
                case 2 -> main(c);
                case 4 -> ball(c);
                case 6 -> animate(c);
            }
            c.publish(); draw.drawObject(c.pool, c.state.identity());
            c.state = c.pool.state(c.wholeIndex); // Draw owns actual sprite-bit7 and queue output.
            c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            despawn(c, globals);
            return new Result(c.state, c.effects);
        } finally {
            running = false; // Root closes a faulted runtime; this is no retry/resume permission.
        }
    }

    /** ObjCheckpoint_Init:71-120. No fallthrough into Main, even with dirty contact. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveLong(MAPPINGS, assets.mappingAddressBits())
                .moveWord(TILE, 0x06CB).moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 8)
                .moveByte(Y_RADIUS, 0x18).moveByte(PRIORITY, 4);
        c.publish();
        int checkpoint = currentLevel(c).checkpoint();
        if (checkpoint >= c.state.subtype()) c.state = c.state.moveByte(CHECKPOINT_ACTIVE, 1);
        else c.state = c.state.moveByte(COLLISION_TYPE, 0xE3);
        c.publish();
        var found = c.pool.findObjSlot(); // One actual scan of all 96 ID bytes, no clear/reserve.
        c.effects.add(new AllocationAttempt(found));
        if (found.isEmpty()) {
            delete(c); // JMP DeleteObject returns to wrapper DrawObject/despawn, even on ID zero.
            return;
        }
        var child = c.pool.state(found.getAsInt()).moveByte(ID, OBJECT_ID).addByte(ROUTINE, 4);
        if (c.state.unsignedByte(CHECKPOINT_ACTIVE) != 0) child = child.addByte(ROUTINE, 2);
        child = child.moveLong(MAPPINGS, assets.mappingAddressBits()).moveWord(TILE, 0x06CB)
                .moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8)
                .moveByte(PRIORITY, 3).moveByte(MAP_FRAME, 1)
                // RunObjects' LEA objects.w sign extends $D000; MOVE.L a0 keeps those full bits.
                .moveLong(CHECKPOINT_PARENT, 0xFFFFD000 + c.wholeIndex * CdObjectState.BYTE_SIZE)
                .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY()).addWord(Y, -32)
                .moveWord(CHECKPOINT_BALL_X, c.state.centreX())
                .moveWord(CHECKPOINT_BALL_Y, c.state.centreY()).addWord(CHECKPOINT_BALL_Y, -24);
        c.pool.write(child); c.effects.add(new ChildCreated(child));
    }

    /** ObjCheckpoint_Main:126-143. Source-only active/status gates; status stays untouched. */
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        if (c.state.unsignedByte(CHECKPOINT_ACTIVE) != 0) return;
        if (c.state.unsignedByte(COLLISION_STATUS) == 0) return;
        c.state = c.state.moveByte(COLLISION_TYPE, 0).moveByte(CHECKPOINT_ACTIVE, 1); c.publish();
        var level = currentLevel(c);
        var afterCheckpoint = withCheckpoint(level, c.state.subtype());
        storeLevel(c, level, afterCheckpoint, GlobalField.CHECKPOINT);
        var afterSpawn = withSpawnMode(afterCheckpoint, 1);
        storeLevel(c, afterCheckpoint, afterSpawn, GlobalField.SPAWN_MODE);

        var beforeSaved = Objects.requireNonNull(saveData.currentState(), "actual saved state");
        requireContext(c, afterSpawn);
        var result = saveData.save(new SaveCall(c.state, afterSpawn, beforeSaved));
        requireContext(c, afterSpawn);
        if (result == null || !result.state().equals(saveData.currentState())
                || !result.completedStores().equals(List.of(CdCheckpointSaveData.Store.values()))) {
            throw new IllegalStateException("Checkpoint save did not publish its actual state and eighteen ordered stores");
        }
        requireContext(c, afterSpawn);
        c.effects.add(new SaveCompleted(result));
        fm.request(FmSound.FM_CHECKPOINT_AE);
        requireContext(c, afterSpawn);
        if (!result.state().equals(saveData.currentState())) {
            throw new IllegalStateException("Checkpoint FM callback changed the actual saved state");
        }
        requireContext(c, afterSpawn);
        c.effects.add(new FmRequested(FmSound.FM_CHECKPOINT_AE));
    }

    private void storeLevel(Cursor c, CdLevelGlobals before, CdLevelGlobals after, GlobalField field) {
        c.publish();
        var call = new GlobalCall(c.state, before, after, field);
        global.commit(call);
        requireContext(c, after);
        c.effects.add(new GlobalStored(call));
    }

    private CdLevelGlobals currentLevel(Cursor c) {
        requireSource(c);
        var result = Objects.requireNonNull(global.current(), "actual level globals");
        requireSource(c);
        return result;
    }
    private void requireContext(Cursor c, CdLevelGlobals expected) {
        if (!expected.equals(currentLevel(c))) {
            throw new IllegalStateException("Actual Checkpoint level globals differ from the reached store");
        }
    }
    private static void requireSource(Cursor c) {
        if (!c.state.equals(c.pool.state(c.wholeIndex))) {
            throw new IllegalStateException("Checkpoint callback changed its actual published source object");
        }
    }
    private static CdLevelGlobals withCheckpoint(CdLevelGlobals g, int checkpoint) {
        return new CdLevelGlobals(g.levelRestart(), g.updateHudTime(), checkpoint, g.spawnMode(), g.plcLoadFlags(),
                g.flowerCount(), g.fadeEnableDisplay(), g.powerup(), g.timeOver());
    }
    private static CdLevelGlobals withSpawnMode(CdLevelGlobals g, int spawn) {
        return new CdLevelGlobals(g.levelRestart(), g.updateHudTime(), g.checkpoint(), spawn, g.plcLoadFlags(),
                g.flowerCount(), g.fadeEnableDisplay(), g.powerup(), g.timeOver());
    }

    /** ObjCheckpoint_Ball:149-181. Only the raw addressed parent byte governs activation. */
    private void ball(Cursor c) {
        c.enter(NativeRoutine.BALL);
        if (c.state.unsignedByte(CHECKPOINT_ACTIVE) == 0) {
            int address = c.state.longBits(CHECKPOINT_PARENT) + 0x2E; // Full wrapping address addition.
            int parentActive = global.readParentByte(address);
            requireSource(c); byteValue(parentActive);
            if (parentActive == 0) return;
            c.state = c.state.moveByte(CHECKPOINT_ACTIVE, 1); c.publish();
        }
        c.state = c.state.addByte(CHECKPOINT_BALL_ANGLE, 8); c.publish();
        var trig = assets.math().calcSine(c.state.unsignedByte(CHECKPOINT_BALL_ANGLE));
        c.effects.add(new CallReturned(NativeCall.CALC_SINE));
        int xOffset = ((short) trig.sine() * 8) >>> 8; // MULS.W then LSR.L, consumed as low word.
        c.state = c.state.moveWord(X, c.state.word(CHECKPOINT_BALL_X)).addWord(X, xOffset); c.publish();
        int yOffset = ((short) trig.cosine() * -8) >>> 8;
        c.state = c.state.moveWord(Y, c.state.word(CHECKPOINT_BALL_Y)).addWord(Y, yOffset); c.publish();
        if (c.state.unsignedByte(CHECKPOINT_BALL_ANGLE) == 0) {
            c.state = c.state.addByte(ROUTINE, 2); c.publish();
        }
    }

    /** ObjCheckpoint_Animate:187-189. Shared helper owns every successful transition. */
    private void animate(Cursor c) {
        c.enter(NativeRoutine.ANIMATE);
        var original = c.state;
        animationReadPrefixes(c);
        var result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME),
                original.unsignedByte(ANIMATION_TIME), original.unsignedByte(MAP_FRAME), original.spriteFlags(),
                original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, assets.visual().animations().size()));
        c.state = original.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer())
                .moveByte(MAP_FRAME, result.mapFrame()).moveByte(SPRITE_FLAGS, result.spriteFlags())
                .moveByte(FLAGS, result.statusFlags()).moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2());
        c.publish(); c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }

    /**
     * Object Animate.asm:20-33,59-76: publish actual reset/decrement/speed/index
     * prefixes before a finite-view fault. This is no fallback animation; successful
     * flow invokes the shared helper ONCE with the original image, as in TimePost.
     */
    private void animationReadPrefixes(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION)) {
            c.state = c.state.moveByte(PREVIOUS_ANIMATION, animation)
                    .moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
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

    /** CheckObjDespawn/DespawnObject/DespawnObjTimeWarp (Object Spawner.asm:385-446). */
    private void despawn(Cursor c, DespawnGlobals globals) {
        int x = c.state.centreX(); // MOVE.W is reached before testing current sprite sign.
        boolean outside = (c.state.spriteFlags() & 0x80) == 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        if (outside || (globals.rawTimeZoneByte() & 0x80) != 0) {
            if (c.state.savedFlagsId() != 0) {
                var at = savedAddress(c, globals);
                int old = placement.savedFlag(at.id(), at.period());
                placement.bclrSavedFlag(at.id(), at.period(), CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new SavedFlagChanged(at.byteIndex(), old, placement.savedFlag(at.id(), at.period())));
            }
            delete(c);
        }
        c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }

    private record SavedAddress(int id, int period, int byteIndex) { }
    /** Preserve the actual native ADDRESS, using canonical API arguments only after bounds. */
    private static SavedAddress savedAddress(Cursor c, DespawnGlobals globals) {
        int period = globals.rawTimeZoneByte() & 0x7F;
        if ((globals.rawTimeZoneByte() & 0x80) != 0) {
            int adjusted = (period - (byte) globals.rawDirectionByte()) & 0xFFFF;
            period = (short) adjusted < 0 ? 0 : adjusted >= 3 ? 2 : adjusted;
        }
        int offset = (3 * c.state.savedFlagsId() + period) & 0xFFFF;
        int index = 2 + (short) offset;
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c, index);
        }
        return new SavedAddress((index - 2) / 3, (index - 2) % 3, index);
    }
    private static void delete(Cursor c) {
        c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
    }
    private static UnsupportedCheckpoint unsupported(UnsupportedBoundary boundary, Cursor c, int value) {
        return new UnsupportedCheckpoint(boundary, c.wholeIndex, value);
    }
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        Cursor(CdObjectPool pool, CdObjectState state) {
            this.pool = pool; this.state = state; this.wholeIndex = state.identity().wholeIndex();
        }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void publish() { pool.write(state); }
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
