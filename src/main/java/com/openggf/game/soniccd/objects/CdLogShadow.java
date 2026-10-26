package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * ObjLogShadow ($33), Level/Palmtree Panic/Objects/Log Shadow/Main.asm:7-70 at
 * scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Init, Main's RTS, one unconditional
 * DrawObject, then CheckObjDespawn. No player, movement, animation, clock, collision or allocation owner.
 * There is no FixBugs conditional in this closure; the shipped native behavior is retained.
 *
 * <p>Init publishes its first seven stores before the reached base WORD read, and both tile stores before
 * DrawObject. The synchronous port mutates the actual pool; the tail reloads its actual live output, including
 * changed ID/flags/X/saved ID. A thrown call retains every completed prefix and the original exception identity.
 * Returned effects describe completed observations only. They confer no mutation or replay authority.
 * Root owns phase guarding, failed-world closure, mapping/frame selection, current art, and capture partitions.
 * The transient guard rejects synchronous reentry; no slot, capture, effects or run flag is saved as gameplay.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdLogShadow {
    public static final int OBJECT_ID = 0x33;

    public enum NativeRoutine { INIT, MAIN }
    public enum NativeCall { DRAW_OBJECT }

    /** Metadata returned only after the entire visit completes; never commands to execute. */
    public sealed interface Effect permits RoutineEntered, CallReturned, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** True only when this visit's shared despawn/delete tail completed its deletion path. */
    public record DespawnChecked(boolean deleted) implements Effect { }

    /** First four values are raw unsigned native BYTEs; cameraXWord is the pre-scroll FG WORD. */
    public record Globals(int timeZone, int goodFuture, int act, int timeWarpDirection, int cameraXWord) {
        public Globals {
            byteValue(timeZone, "timeZone");
            byteValue(goodFuture, "goodFuture");
            byteValue(act, "act");
            byteValue(timeWarpDirection, "timeWarpDirection");
            wordValue(cameraXWord, "cameraXWord");
        }
    }

    /**
     * Constructed component data authenticates nothing. The base view may be short or odd: only the reached
     * selected WORD is checked, AFTER Init's earlier stores. The raw mapping LONG is never masked.
     * Visual validation describes the two unscripted counted frames and their sixteen-tile source capacity;
     * this body performs no frame selection, mapping resolution, art read, decode or preload.
     */
    public record Assets(CdAssetBytes baseTileWords, CdObjectVisualAssets visual, int mappingsAddressBits) {
        public Assets {
            Objects.requireNonNull(baseTileWords, "baseTileWords");
            Objects.requireNonNull(visual, "visual");
            if (visual.mappingFrames().size() != 2 || !visual.animations().isEmpty()) {
                throw new IllegalArgumentException("MapSpr_LogShadow has two counted frames and no scripts");
            }
            for (var frame : visual.mappingFrames()) {
                for (var piece : frame.pieces()) {
                    if (piece.tileOffset() > 16 - piece.tileCount()) {
                        throw new IllegalArgumentException("Log Shadow mapping exceeds its sixteen-tile source");
                    }
                }
            }
        }

        /** Retains the authenticated owner's raw base view and actual mapping-view native address. */
        public static Assets fromObjectAssets(CdR11aObjectAssets objects) {
            Objects.requireNonNull(objects, "objects");
            return new Assets(objects.view(CdR11aObjectAssets.Table.LOG_SHADOW_BASE_TILES).bytes(),
                    objects.logShadow(),
                    objects.view(CdR11aObjectAssets.Table.LOG_SHADOW_MAPPINGS).range().nativeAddress());
        }
    }

    /**
     * Existing synchronous mutating DrawObject seam over the actual pool and physical identity.
     * Its source entry clears sprite bit7 before culling/capacity. Root must not dispatch bodies through it;
     * this instance also rejects synchronous reentry. Any port mutations/exception remain authoritative.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() {
            return effects.stream().anyMatch(effect -> effect instanceof DespawnChecked checked && checked.deleted());
        }
    }

    public enum UnsupportedBoundary {
        /** Reached BYTE offset outside the two-word routine directory (Main.asm:8-19). */
        ROUTINE_DISPATCH,
        /** Reached selected WORD outside the sixteen-byte base directory or supplied view (:41-64). */
        BASE_TILE_ADDRESS,
        /** Reached saved-bank byte outside its existing 2+$2FC owner (Object Spawner.asm:431-433). */
        SAVED_FLAGS_ADDRESS
    }

    /** The selected unsupported read/write is not performed; all preceding native stores remain live. */
    public static final class UnsupportedLogShadow extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final int value;

        private UnsupportedLogShadow(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Log Shadow whole slot " + wholeIndex + ", reached value $"
                    + Integer.toHexString(value));
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
            this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        /** Routine BYTE, base-directory BYTE offset, or complete saved-bank BYTE index, by boundary. */
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final DrawObjectCall drawObject;
    private boolean running;

    public CdLogShadow(CdObjectPlacementRuntime placement, Assets assets, DrawObjectCall draw) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.drawObject = Objects.requireNonNull(draw, "draw");
    }

    /** One RunObjects physical dynamic-slot visit; globals retain native widths without period/act normalization. */
    public Result run(int wholeIndex, Globals globals) {
        if (running) { throw new IllegalStateException("Reentrant ObjLogShadow dispatch"); }
        Objects.requireNonNull(globals, "globals");
        var pool = placement.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch requires ID $33 at slot " + wholeIndex);
        }
        int routine = state.routine();
        if (routine != 0 && routine != 2) {
            throw new UnsupportedLogShadow(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
        }
        running = true;
        try {
            var effects = new ArrayList<Effect>();
            if (routine == 0) {
                effects.add(new RoutineEntered(NativeRoutine.INIT));
                // ObjLogShadow_Init:23-29, in source order. ORI.B keeps every dirty sprite bit.
                state = state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)
                        .moveByte(PRIORITY, 6).moveLong(MAPPINGS, assets.mappingsAddressBits())
                        .moveByte(MAP_FRAME, state.subtype()).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 0x10);
                pool.write(state); // All seven stores precede even a selected short-view fault.
                int tile = selectedBaseWord(wholeIndex, globals);
                state = state.moveWord(TILE, tile); // MOVE.W, :53.
                pool.write(state);
                state = state.moveWord(TILE, state.word(TILE) | 0x4000); // ORI.W, :54.
                pool.write(state);
            }
            effects.add(new RoutineEntered(NativeRoutine.MAIN)); // Init falls through into the RTS (:35-36).
            drawObject.drawObject(pool, state.identity()); // Wrapper ALWAYS calls exactly once (:12).
            state = pool.state(wholeIndex); // Never repair ID or overwrite the callback's live image.
            effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            boolean deleted = checkObjDespawn(state, globals); // Wrapper tail JMP (:13).
            effects.add(new DespawnChecked(deleted));
            return new Result(pool.state(wholeIndex), effects);
        } finally {
            running = false;
        }
    }

    /** SetBaseTile:41-54: ADD.B never carries into the high byte; ADD.W retains the entire word. */
    private int selectedBaseWord(int wholeIndex, Globals globals) {
        int d0 = globals.timeZone() & 0x7F;
        if (d0 == 2) {
            d0 = (d0 & 0xFF00) | ((d0 + globals.goodFuture()) & 0xFF);
        }
        d0 = (d0 + d0) & 0xFFFF;
        d0 = (d0 & 0xFF00) | ((d0 + globals.act()) & 0xFF);
        int offset = (short) ((d0 + d0) & 0xFFFF);
        // Finite reviewed directory only; extra constructed bytes never extend native ownership.
        if (offset < 0 || (offset & 1) != 0 || offset > 14 || offset > assets.baseTileWords().length() - 2) {
            throw new UnsupportedLogShadow(UnsupportedBoundary.BASE_TILE_ADDRESS, wholeIndex, offset);
        }
        return assets.baseTileWords().unsignedWord(offset);
    }

    /** Object Spawner.asm:385-399,442-446. X/sign/distance precede the conditional raw warp-bit read. */
    private boolean checkObjDespawn(CdObjectState state, Globals globals) {
        int x = state.centreX(); // MOVE.W oX before TST.B sprite.
        if (state.signedByte(SPRITE_FLAGS) >= 0) {
            int distance = ((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF;
            if (distance > 0x280) { return despawnObject(state, globals); } // BLS keeps equality.
        }
        if ((globals.timeZone() & 0x80) != 0) { return despawnObject(state, globals); }
        return false;
    }

    /** DespawnObject:403-438. Zero saved ID bypasses all period/direction/bank arithmetic. */
    private boolean despawnObject(CdObjectState state, Globals globals) {
        int savedId = state.savedFlagsId();
        if (savedId != 0) {
            int offset = (savedId + savedId + savedId) & 0xFFFF;
            int rawPeriod = globals.timeZone();
            int period = rawPeriod & 0x7F;
            if ((rawPeriod & 0x80) != 0) {
                int adjusted = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
                period = (short) adjusted < 0 ? 0 : adjusted < 3 ? adjusted : 2;
            }
            int index = 2 + (short) ((offset + period) & 0xFFFF);
            if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
                throw new UnsupportedLogShadow(UnsupportedBoundary.SAVED_FLAGS_ADDRESS,
                        state.identity().wholeIndex(), index);
            }
            // Translate the COMPLETED native byte address to existing public bank primitives, not the raw period.
            int bodyOffset = index - 2;
            placement.bclrSavedFlag(bodyOffset / 3, bodyOffset % 3, CdObjectPlacementRuntime.SavedBit.LOADED);
        }
        placement.pool().deleteObject(state.identity().wholeIndex()); // DeleteObject:228-237, all sixteen LONGs.
        return true;
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
