package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * ObjSonicHole ($31), bounded native routines 0/2/4 and their shared despawn tail.
 * Source: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Palmtree Panic/Objects/Tunnel Path/Main.asm:11-103,
 * Level/Object Spawner.asm:385-446 and Object Functions.asm:228-237.
 * No FixBugs conditional occurs in this closure.
 *
 * <p>Every native store is published before the next reached call/address boundary.
 * Read/FM ports receive fresh actual same-owner full96 cuts and the exact source64;
 * their complete placement values are checked even on a throw, without rollback.
 * Equal-value checks cannot certify no-op stores or native invocation authority.
 * Root owns actual Frame/private invocation/field sequence/full CAS/FM ledger and
 * World closure. Draw is synchronous and mutating; its live source is reloaded.
 * Results are completed scalar observations, never replay commands or call cuts.
 * No Frame/player writer, Solid, physics, angle, clock, art or native execution is owned.
 *
 * <p>Source authored and frozen once; all Java UNRUN at handoff.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdSonicHole {
    public static final int OBJECT_ID = 0x31;

    public record Assets(CdObjectVisualAssets visual, int mappingsAddressBits) {
        public Assets {
            Objects.requireNonNull(visual, "SonicHole visual");
            if (visual.mappingFrames().size() != 1 || !visual.animations().isEmpty()
                    || visual.animationTable().length() != 0) {
                throw new IllegalArgumentException("SonicHole has one counted frame and no scripts");
            }
            for (var piece : visual.mappingFrames().getFirst().pieces()) {
                if (piece.tileOffset() > 14 - piece.tileCount()) {
                    throw new IllegalArgumentException("SonicHole mapping exceeds fourteen source tiles");
                }
            }
        }

        /** Existing authenticated USA asset owner; constructed Assets alone authenticate nothing. */
        public static Assets fromObjectAssets(CdR11aObjectAssets objects) {
            Objects.requireNonNull(objects, "authenticated opening object assets");
            return new Assets(objects.sonicHole(),
                    objects.view(CdR11aObjectAssets.Table.SONIC_HOLE_MAPPINGS).range().nativeAddress());
        }
    }

    /** Raw unsigned BYTE/BYTE/WORD values, never normalized periods or signed direction inputs. */
    public record Globals(int timeZone, int timeWarpDirection, int cameraXWord) {
        public Globals {
            byteValue(timeZone, "timeZone");
            byteValue(timeWarpDirection, "timeWarpDirection");
            wordValue(cameraXWord, "cameraXWord");
        }
    }

    public enum PlayerField { CONTROL_2C, X_WORD, Y_WORD }
    public enum FmSound {
        FM_A3;
        public int nativeId() { return 0xA3; }
    }

    /** Synchronous actual primary read; must not mutate any placement value, including on a throw. */
    @FunctionalInterface
    public interface PrimaryPlayerRead {
        int read(PlayerField field, CdObjectPool.Snapshot actualPoolCut, CdObjectState source);
    }

    /** Source PlayFMSound cut, after SetDisplay and before routine2->4; metadata, not playback. */
    @FunctionalInterface
    public interface PlayFmCall {
        void playFmSound(FmSound sound, CdObjectPool.Snapshot actualPoolCut, CdObjectState source);
    }

    /** Actual synchronous mutating DrawObject seam; Root owns its current physical/phase entitlement. */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    public enum NativeRoutine { INIT, MAIN, DISPLAY }
    public enum NativeCall { PLAY_FM_SOUND, DRAW_OBJECT }

    public sealed interface Effect
            permits RoutineEntered, SavedBitWrite, PlayerRead, CallReturned, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record SavedBitWrite(int bankIndex, int bit, boolean wasSet, boolean nowSet) implements Effect {
        public SavedBitWrite {
            if (bankIndex < 2 || bankIndex >= CdObjectPlacementRuntime.SAVED_BANK_BYTES
                    || (bit != 6 && bit != 7)) {
                throw new IllegalArgumentException("Not a completed SonicHole saved-bit write");
            }
        }
    }
    public record PlayerRead(PlayerField field, int value) implements Effect {
        public PlayerRead {
            Objects.requireNonNull(field, "player field");
            if (field == PlayerField.CONTROL_2C) byteValue(value, "player control");
            else wordValue(value, "player position");
        }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record DespawnChecked(boolean deleted) implements Effect { }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "actual final source");
            effects = List.copyOf(effects);
        }
        /** Only this visit's shared tail deletion, not an arbitrary callback's ID0 store. */
        public boolean deleted() {
            return effects.stream().anyMatch(e -> e instanceof DespawnChecked d && d.deleted());
        }
    }

    public enum UnsupportedBoundary { ROUTINE_DISPATCH, SAVED_FLAGS_ADDRESS }
    public static final class UnsupportedSonicHole extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final int value;

        private UnsupportedSonicHole(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at SonicHole physical slot " + wholeIndex + ", raw value $"
                    + Integer.toHexString(value)
                    + (boundary == UnsupportedBoundary.SAVED_FLAGS_ADDRESS
                    ? ", native byte address $" + Integer.toHexString(0x00FF1200 + value) : ""));
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
            this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        /** Raw routine BYTE/directory offset, or complete linear saved-bank BYTE index. */
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final PrimaryPlayerRead primaryPlayer;
    private final PlayFmCall playFm;
    private final DrawObjectCall drawObject;
    private boolean running;

    public CdSonicHole(CdObjectPlacementRuntime placement, Assets assets, PrimaryPlayerRead primaryPlayer,
                       PlayFmCall playFm, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.primaryPlayer = Objects.requireNonNull(primaryPlayer, "actual primary player read");
        this.playFm = Objects.requireNonNull(playFm, "synchronous FM call");
        this.drawObject = Objects.requireNonNull(drawObject, "synchronous DrawObject call");
    }

    /** One live physical visit; no outer visibility, debug, time-stop or route skip. */
    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Reentrant ObjSonicHole dispatch");
        Objects.requireNonNull(globals, "globals");
        var state = placement.pool().state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("SonicHole dispatch requires live ID $31 at slot " + wholeIndex);
        }
        int routine = state.routine();
        if (routine != 0 && routine != 2 && routine != 4) {
            throw new UnsupportedSonicHole(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
        }
        running = true;
        try {
            var effects = new ArrayList<Effect>();
            boolean display = routine == 4;
            if (routine == 0) {
                effects.add(new RoutineEntered(NativeRoutine.INIT));
                // Main.asm:25-36: individual BYTE/WORD/LONG stores, in native instruction order.
                state = store(state.addByte(ROUTINE, 2));
                state = store(state.moveByte(SPRITE_FLAGS, state.spriteFlags() | 4));
                state = store(state.moveWord(TILE, 0x03A0));
                if (globals.timeZone() == 0) state = store(state.moveWord(TILE, 0x03BB));
                state = store(state.moveLong(MAPPINGS, assets.mappingsAddressBits()));
                state = store(state.moveByte(WIDTH, 0x10));
                state = store(state.moveByte(Y_RADIUS, 0x10));
                state = store(state.moveByte(PRIORITY, 6));
                var bit = setDisplay(state, globals, effects);
                if (bit.wasSet()) {
                    state = store(state.addByte(ROUTINE, 2));
                    display = true;
                } else {
                    // Main.asm:44-45: same completed address, BSET6 then BCLR6, then fall through.
                    savedBitWrite(bit.index(), 6, false, effects);
                }
            }
            if (!display) {
                effects.add(new RoutineEntered(NativeRoutine.MAIN));
                display = main(wholeIndex, globals, effects);
            }
            if (display) {
                effects.add(new RoutineEntered(NativeRoutine.DISPLAY));
                drawObject.drawObject(placement.pool(), placement.pool().state(wholeIndex).identity());
                effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            }
            // Reload actual live source after any mutating Draw. Never repair its ID or old bytes.
            boolean deleted = checkObjDespawn(placement.pool().state(wholeIndex), globals, effects);
            effects.add(new DespawnChecked(deleted));
            return new Result(placement.pool().state(wholeIndex), effects);
        } finally {
            running = false;
        }
    }

    /** Main.asm:50-75: actual primary control first, then conditional WORD X and Y reads. */
    private boolean main(int wholeIndex, Globals globals, List<Effect> effects) {
        if (readPrimary(PlayerField.CONTROL_2C, wholeIndex, effects) == 0) return false;
        int x = (readPrimary(PlayerField.X_WORD, wholeIndex, effects)
                - placement.pool().state(wholeIndex).centreX() + 0x20) & 0xFFFF;
        if ((short) x < 0 || x >= 0x40) return false;
        int y = (readPrimary(PlayerField.Y_WORD, wholeIndex, effects)
                - placement.pool().state(wholeIndex).centreY() + 0x20) & 0xFFFF;
        if ((short) y < 0 || y >= 0x40) return false;
        setDisplay(placement.pool().state(wholeIndex), globals, effects); // Main ignores old Z.
        var before = placement.capture();
        var source = sourceAt(before, wholeIndex);
        readOnlyPlacementCall(before, () -> {
            playFm.playFmSound(FmSound.FM_A3, before.pool(), source);
            return null;
        });
        effects.add(new CallReturned(NativeCall.PLAY_FM_SOUND));
        store(placement.pool().state(wholeIndex).addByte(ROUTINE, 2)); // Only AFTER FM returns.
        return true;
    }

    private int readPrimary(PlayerField field, int wholeIndex, List<Effect> effects) {
        var before = placement.capture();
        var source = sourceAt(before, wholeIndex);
        int value = readOnlyPlacementCall(before, () -> primaryPlayer.read(field, before.pool(), source));
        // Verification precedes width validation; neither path supplies a normalized native value.
        if (field == PlayerField.CONTROL_2C) byteValue(value, "actual primary control");
        else wordValue(value, "actual primary position");
        effects.add(new PlayerRead(field, value));
        return value;
    }

    private CdObjectState sourceAt(CdObjectPlacementRuntime.Snapshot before, int wholeIndex) {
        placement.validateRestore(before);
        return before.pool().slots().get(wholeIndex - CdObjectPool.FIRST_WHOLE_INDEX);
    }

    /** No rollback. A callback failure keeps its identity and any distinct verification fault. */
    private <T> T readOnlyPlacementCall(CdObjectPlacementRuntime.Snapshot before, Supplier<T> call) {
        Throwable original = null;
        try {
            return call.get();
        } catch (Throwable failure) {
            original = failure;
            throw failure; // Precise rethrow: Supplier declares no checked exceptions.
        } finally {
            try {
                verifyUnchangedPlacement(before);
            } catch (Throwable verification) {
                if (original == null) throw verification;
                if (verification != original) original.addSuppressed(verification);
            }
        }
    }

    private void verifyUnchangedPlacement(CdObjectPlacementRuntime.Snapshot before) {
        placement.validateRestore(before);
        var after = placement.capture();
        boolean status = before.status().equals(after.status());
        boolean saved = Arrays.equals(before.savedFlags().copy(), after.savedFlags().copy());
        boolean pool = before.pool().slots().equals(after.pool().slots());
        if (!status || !saved || !pool) {
            throw new IllegalStateException("SonicHole read/FM changed placement: status=" + status
                    + ", saved766=" + saved + ", pool96=" + pool);
        }
    }

    private record DisplayBit(int index, boolean wasSet) { }

    /** Main.asm:85-96: raw BYTE period, including bit7, and savedID0 are both meaningful. */
    private DisplayBit setDisplay(CdObjectState source, Globals globals, List<Effect> effects) {
        int id = source.savedFlagsId();
        int d0 = ((id + id + id) + globals.timeZone()) & 0xFFFF;
        int index = checkedBankIndex(source, 2 + (short) d0);
        boolean old = savedBitWrite(index, 6, true, effects);
        return new DisplayBit(index, old);
    }

    /** Existing bank primitives after completed raw address authentication, never raw-period admission. */
    private boolean savedBitWrite(int index, int bit, boolean set, List<Effect> effects) {
        int offset = index - 2;
        int id = offset / 3, period = offset % 3;
        boolean old = (placement.savedFlag(id, period) & (1 << bit)) != 0;
        var nativeBit = bit == 6 ? CdObjectPlacementRuntime.SavedBit.MEMBER_7
                : CdObjectPlacementRuntime.SavedBit.LOADED;
        if (set) placement.bsetSavedFlag(id, period, nativeBit);
        else placement.bclrSavedFlag(id, period, nativeBit);
        effects.add(new SavedBitWrite(index, bit, old, set));
        return old;
    }

    /** Object Spawner.asm:385-399,442-446. WORD distance/sign before the raw warp-bit read. */
    private boolean checkObjDespawn(CdObjectState source, Globals globals, List<Effect> effects) {
        int x = source.centreX();
        if (source.signedByte(SPRITE_FLAGS) >= 0) {
            int distance = ((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF;
            if (distance > 0x280) return despawn(source, globals, effects);
        }
        return (globals.timeZone() & 0x80) != 0 && despawn(source, globals, effects);
    }

    /** Object Spawner.asm:403-438: zero saved ID bypasses bank work, then sixteen-LONG delete. */
    private boolean despawn(CdObjectState source, Globals globals, List<Effect> effects) {
        int id = source.savedFlagsId();
        if (id != 0) {
            int period = globals.timeZone() & 0x7F;
            if ((globals.timeZone() & 0x80) != 0) {
                int adjusted = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
                period = (short) adjusted < 0 ? 0 : adjusted < 3 ? adjusted : 2;
            }
            int index = checkedBankIndex(source, 2 + (short) ((id + id + id + period) & 0xFFFF));
            savedBitWrite(index, 7, false, effects);
        }
        placement.pool().deleteObject(source.identity().wholeIndex());
        return true;
    }

    private int checkedBankIndex(CdObjectState source, int index) {
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw new UnsupportedSonicHole(UnsupportedBoundary.SAVED_FLAGS_ADDRESS,
                    source.identity().wholeIndex(), index);
        }
        return index;
    }

    private CdObjectState store(CdObjectState source) {
        placement.pool().write(source);
        return source;
    }
    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException(name + " requires an unsigned BYTE");
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException(name + " requires an unsigned WORD");
    }
}
