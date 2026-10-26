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
 * ObjBoulder ($1B), Level/_Objects/Boulder/Main.asm:7-51 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. One run is one live dynamic-slot dispatch: the two-word routine
 * directory, Init falling into Main, unconditional DrawObject, then CheckObjDespawn. The body has no FixBugs
 * conditional; the shared Solid owner retains shipped FixBugs=0 semantics.
 *
 * <p>Init publishes its native prefix BEFORE the reached SetObjectTileID read. A negative sprite byte then makes
 * exactly one primary-player SOLID_OBJECT call: publish a0, capture the actual pool once, compute through the
 * shared owner, synchronously invoke Root's commit, and require the final a0 and other-slot images to be live.
 * Root owns whole-pool CAS, Frame validation/publication and ordered FM append exactly once. Returned effects
 * describe completed work, including nested helper observations; reading or replaying them performs no work.
 *
 * <p>DrawObject receives the real pool and physical identity. Its actual live output is reloaded before the native
 * despawn tail. Any tile/helper/commit/draw/tail exception propagates with the preceding published source prefix
 * intact. The caller closes the failed cycle; this body neither retries nor rolls back. A transient instance
 * guard rejects synchronous port reentry; Root additionally guards its whole object phase and shared ports.
 * No Frame, pool capture, player projection or effect list is retained between dispatches.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdBoulder {
    public static final int OBJECT_ID = 0x1B;
    /** ObjBoulder_Init:34-35, MOVEQ #$B / JSR SetObjectTileID. */
    public static final int TILE_SELECTOR = 0x0B;

    public enum NativeRoutine { INIT, MAIN }
    public enum NativeCall { DRAW_OBJECT }

    /** Ordered completed metadata only. Pool, player, global and FM work must never be replayed. */
    public sealed interface Effect permits RoutineEntered, Solid, CallReturned, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    /** The one completed primary SolidObject call, already published by SolidCommit. */
    public record Solid(CdSolidObject.Result result) implements Effect {
        public Solid {
            Objects.requireNonNull(result, "result");
            if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalArgumentException("ObjBoulder calls only SolidObject");
            }
        }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** CheckObjDespawn returned: true means its saved-flag/deletion path completed. */
    public record DespawnChecked(boolean deleted) implements Effect { }

    /** Raw native timeZone/timeWarpDir bytes and the pre-scroll foreground cameraX word. */
    public record Globals(int timeZone, int timeWarpDirection, int cameraXWord) {
        public Globals {
            byteValue(timeZone, "timeZone");
            byteValue(timeWarpDirection, "timeWarpDirection");
            wordValue(cameraXWord, "cameraX");
            if ((timeZone & 0x7F) > 2) {
                throw new IllegalArgumentException("Native CD period must be 0, 1 or 2");
            }
        }
    }

    /** The actual current primary player, gates and shared writable globals at the reached helper cut. */
    public record SolidInputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public SolidInputs {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(gates, "gates");
            Objects.requireNonNull(globals, "globals");
        }
    }

    /**
     * Declared component data; construction authenticates nothing. Production binds the retained ROM pipeline
     * through fromObjectAssets. One static counted frame consumes only offsets 0..18 of its 19-tile source;
     * this class contains no mapping bytes, art bytes, native pointer literal or default tile word.
     */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets visual, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds");
            Objects.requireNonNull(visual, "visual");
            if (objectTileIds.length() != 74) {
                throw new IllegalArgumentException("ObjectTileIDs extent");
            }
            if (visual.mappingFrames().size() != 1 || !visual.animations().isEmpty()) {
                throw new IllegalArgumentException("MapSpr_Boulder has one counted frame and no animation scripts");
            }
            for (var piece : visual.mappingFrames().getFirst().pieces()) {
                if (piece.tileOffset() > 19 - piece.tileCount()) {
                    throw new IllegalArgumentException("Boulder mapping exceeds its 19-tile source");
                }
            }
        }

        /** Original authenticated mapping address, not a reconstructed/disassembly-backed runtime picture. */
        public static Assets fromObjectAssets(CdR11aObjectAssets objects) {
            Objects.requireNonNull(objects, "objects");
            return new Assets(objects.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(), objects.boulder(),
                    objects.view(CdR11aObjectAssets.Table.BOULDER_MAPPINGS).range().nativeAddress());
        }
    }

    /** Borrowed actual-pool cut and exact Frame inputs for ONE pure shared SolidObject result. */
    public record SolidCall(CdObjectPool.Snapshot callCut, CdSolidObject.Player expectedPlayer,
                            CdSolidObject.Globals expectedGlobals, CdSolidObject.Result result) {
        public SolidCall {
            Objects.requireNonNull(callCut, "callCut");
            Objects.requireNonNull(expectedPlayer, "expectedPlayer");
            Objects.requireNonNull(expectedGlobals, "expectedGlobals");
            Objects.requireNonNull(result, "result");
            if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalArgumentException("ObjBoulder publication requires SolidObject");
            }
        }
    }

    /**
     * Root must synchronously preflight the complete same-owner live pool against callCut and the current ID $1B,
     * validate expectedPlayer/expectedGlobals with Frame.validateObjectSolidResult, then publish the result's a0,
     * all otherSlots and Frame stores without intervening callbacks, and append fmRequests in order ONCE. It must
     * not retain this cut, reenter or dispatch another body. On return this body requires the S4 images to be live;
     * it cannot certify Frame/FM publication itself. A failed callback retains whatever prefix its owner published.
     */
    @FunctionalInterface
    public interface SolidCommit {
        void commit(SolidCall call);
    }

    /**
     * Native DrawObject (Object Functions.asm:137-196), over the actual pool. Its entry clears sprite bit 7;
     * any subsequent live mutations must survive. Root supplies its synchronous phase-guarded shared draw owner;
     * it must not dispatch bodies. The instance run guard rejects reentry through this port as well as commit.
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
        /** Odd or greater-than-two routine indexes outside the two-word directory (Boulder/Main.asm:8-20). */
        ROUTINE_DISPATCH,
        /** Reached unreviewed selector-$B subtype2 word AFTER the completed Init prefix (:27-35). */
        TILE_ID_SUBTYPE2
    }
    /** The boundary itself is not executed; earlier native writes remain live. */
    public static final class UnsupportedBoulder extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private UnsupportedBoulder(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdSolidObject sharedSolid;
    private final SolidCommit commit;
    private final DrawObjectCall drawObject;
    private boolean running;

    public CdBoulder(CdObjectPlacementRuntime placement, Assets assets, CdSolidObject sharedSolid,
                     SolidCommit commit, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.sharedSolid = Objects.requireNonNull(sharedSolid, "sharedSolid");
        this.commit = Objects.requireNonNull(commit, "commit");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /**
     * One physical RunObjects visit. SolidInputs is dereferenced only if Main's incoming sprite byte is negative;
     * the record's ordinary Java construction checks do not claim to reproduce native player reads.
     */
    public Result run(int wholeIndex, Globals globals, SolidInputs inputs) {
        if (running) { throw new IllegalStateException("Reentrant ObjBoulder dispatch"); }
        Objects.requireNonNull(globals, "globals");
        var pool = placement.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch requires ID $1B at slot " + wholeIndex);
        }
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 2) {
            throw new UnsupportedBoulder(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine $" + Integer.toHexString(routine) + " indexes outside the two-word directory");
        }
        running = true;
        try {
            var effects = new ArrayList<Effect>();
            if (routine == 0) {
                effects.add(new RoutineEntered(NativeRoutine.INIT));
                // ObjBoulder_Init:27-33. ORI.B retains ALL dirty sprite bits. No speed/type/fraction resets.
                state = state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)
                        .moveByte(PRIORITY, 4).moveLong(MAPPINGS, assets.mappingsAddress())
                        .moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 0x10).moveByte(MAP_FRAME, 0);
                pool.write(state); // These stores precede the reached SetObjectTileID instruction.
                if (state.subtype2() != 0) {
                    throw new UnsupportedBoulder(UnsupportedBoundary.TILE_ID_SUBTYPE2, wholeIndex,
                            "selector $B / subtype2 $" + Integer.toHexString(state.subtype2()) + " is unreviewed");
                }
                int tile = CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), TILE_SELECTOR, state.subtype2());
                state = state.moveWord(TILE, tile); // SetObjectTileID:16, only after its bounded reads succeed.
                pool.write(state);
            }
            effects.add(new RoutineEntered(NativeRoutine.MAIN)); // Init falls through, same JSR (:35-41).
            if ((state.spriteFlags() & 0x80) != 0) { // TST.B / BPL (:42-43) precedes every player/helper read.
                Objects.requireNonNull(inputs, "reached SolidObject inputs");
                pool.write(state);
                var cut = pool.capture(); // ONE actual cut, no overlay or synthetic pool.
                var result = sharedSolid.run(CdSolidObject.Entry.SOLID_OBJECT, state,
                        inputs.player(), inputs.gates(), inputs.globals(), cut);
                commit.commit(new SolidCall(cut, inputs.player(), inputs.globals(), result));
                if (!pool.state(wholeIndex).equals(result.object())) {
                    throw new IllegalStateException("SolidObject commit did not publish a0's final image");
                }
                for (var other : result.otherSlots()) {
                    if (!pool.state(other.identity().wholeIndex()).equals(other)) {
                        throw new IllegalStateException("SolidObject commit did not publish a2 slot " + other.identity().wholeIndex());
                    }
                }
                effects.add(new Solid(result));
                state = result.object(); // Final player/globals are exposed in this same completed Result metadata.
            }
            pool.write(state);
            drawObject.drawObject(pool, state.identity()); // Unconditional wrapper JSR DrawObject (:13).
            state = pool.state(wholeIndex); // NEVER overwrite the port's actual live output with an old image.
            effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            boolean deleted = checkObjDespawn(state, globals); // Wrapper JMP CheckObjDespawn (:14).
            effects.add(new DespawnChecked(deleted));
            return new Result(pool.state(wholeIndex), effects);
        } finally {
            running = false;
        }
    }

    /**
     * CheckObjDespawn / DespawnObject / DespawnObjTimeWarp (Object Spawner.asm:385-446). Uses the existing placement
     * bank primitives; no new bank, saved-state shadow, helper port or reserved-slot addressing is introduced.
     */
    private boolean checkObjDespawn(CdObjectState state, Globals globals) {
        int x = state.centreX(); // MOVE.W oX happens before the sprite sign test (:385-390).
        boolean outside = (state.spriteFlags() & 0x80) == 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean warp = (globals.timeZone() & 0x80) != 0;
        if (!outside && !warp) { return false; } // BLS keeps equality; negative sprite still tests the raw warp bit.
        int savedId = state.savedFlagsId();
        if (savedId != 0) {
            int period = globals.timeZone() & 0x7F;
            if (warp) {
                int remapped = (period - (byte) globals.timeWarpDirection()) & 0xFFFF; // EXT.W / NEG.W / ADD.W :418-421.
                period = (short) remapped < 0 ? 0 : remapped < 3 ? remapped : 2; // Past/Future clamps :422-429.
            }
            // Reached address failure leaves ALL preceding Init/Solid/Draw stores live; DeleteObject is later.
            placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        }
        placement.pool().deleteObject(state.identity().wholeIndex()); // Sixteen long clears, Object Functions:228-237.
        return true;
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
