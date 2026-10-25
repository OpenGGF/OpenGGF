package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.Actor;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.PlayerGlobals;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse.WorldGlobals;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * Common ObjSpikes ($26), Level/_Objects/Spikes/Main.asm:7-83 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. One run is one physical primary-player object dispatch.
 * Init falls through into Main in that same call. The common body has no FixBugs conditional; the shared
 * Solid/Hurt/Reset owners preserve shipped FixBugs=0. No other zone's spike family is selected here.
 *
 * <p>Native publication cuts are separate and synchronous: Init's prefix before its reached tile read;
 * ONE shared SolidObject with one actual live-pool capture; the full player-Y LONG store; then direct
 * shared HurtPlayer with a FRESH actual capture and the post-prefix Actor. Root's ports own whole-pool CAS,
 * Frame/contact/world validation and publication, and ordered FM requests once. A failed later call leaves
 * all earlier published cuts intact. This body never retries, rolls back, scans touches, dispatches another
 * player body, allocates directly, emits sound, or replays helper metadata.
 *
 * <p>Draw receives the actual pool and physical identity, after reloading live a0. Its actual output is
 * reloaded before the private CheckObjDespawn tail using existing placement/pool primitives. This instance's
 * transient guard rejects synchronous reentry through every port; Root also guards the object phase and
 * its shared publication/draw/sound ports. No pool snapshot, Frame, input projection or effect list is
 * retained between dispatches. Returned effects describe completed work, never instructions to execute.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdSpikes {
    public static final int OBJECT_ID = 0x26;
    /** ObjSpikes_Init:24-25, MOVEQ #$A / JSR SetObjectTileID. */
    public static final int TILE_SELECTOR = 0x0A;

    public enum NativeRoutine { INIT, MAIN }
    public enum NativeCall { DRAW_OBJECT }

    /** Immutable, ordered observations of completed native work. No call-cut captures or replay authority. */
    public sealed interface Effect permits RoutineEntered, Solid, YRewound, Hurt, CallReturned, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record Solid(CdSolidObject.Result result) implements Effect {
        public Solid {
            Objects.requireNonNull(result, "result");
            if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalArgumentException("ObjSpikes calls only SolidObject");
            }
        }
    }
    /** The explicit MOVE.L at ObjSpikes_Main:75 completed, including a same-value store. */
    public record YRewound(int finalYBits) implements Effect { }
    public record Hurt(CdPlayerTouchResponse.Result result) implements Effect {
        public Hurt { Objects.requireNonNull(result, "result"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** True only after the saved-bit operation (if any) and full DeleteObject both completed. */
    public record DespawnChecked(boolean deleted) implements Effect { }

    /** Raw timeZone/timeWarpDir bytes and the actual pre-scroll foreground cameraX integer WORD. */
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

    /**
     * One complete primary-player cut. The Actor lives only in player; Solid's writable globals and gates
     * overlap PlayerGlobals explicitly, so disagreeing projections are rejected rather than normalized.
     * Read-only timeWarp/shield/combineRing/debug values survive Solid; its invincible/scoreChain and both
     * collision buffers are threaded from the actual shared Result before any direct-Hurt gate is read.
     */
    public record Inputs(CdSolidObject.Player player, CdSolidObject.Gates gates,
                         CdSolidObject.Globals solidGlobals, PlayerGlobals playerGlobals, WorldGlobals worldGlobals) {
        public Inputs {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(gates, "gates");
            Objects.requireNonNull(solidGlobals, "solidGlobals");
            Objects.requireNonNull(playerGlobals, "playerGlobals");
            Objects.requireNonNull(worldGlobals, "worldGlobals");
            if (solidGlobals.invincible() != playerGlobals.invincible()
                    || solidGlobals.scoreChain() != playerGlobals.scoreChain()
                    || gates.debugMode() != playerGlobals.debugMode()
                    || gates.miniSonic() != playerGlobals.miniSonic()) {
                throw new IllegalArgumentException("Solid and Hurt projections must describe the same native globals");
            }
        }
    }

    /**
     * Declared component data, not an authentication owner. Production obtains the one static frame,
     * eight-tile format and original pointer from the existing retained ROM object-asset owner. No mapping
     * bytes, tile-word default, address guess or runtime disassembly fallback exists in this body.
     */
    public record BodyAssets(CdAssetBytes objectTileIds, CdObjectVisualAssets visual, int mappingsAddress) {
        public BodyAssets {
            Objects.requireNonNull(objectTileIds, "objectTileIds");
            Objects.requireNonNull(visual, "visual");
            if (objectTileIds.length() != 74) {
                throw new IllegalArgumentException("ObjectTileIDs extent");
            }
            if (visual.mappingFrames().size() != 1 || !visual.animations().isEmpty()) {
                throw new IllegalArgumentException("Common Spikes has one counted frame and no animations");
            }
            for (var piece : visual.mappingFrames().getFirst().pieces()) {
                if (piece.tileOffset() > 8 - piece.tileCount()) {
                    throw new IllegalArgumentException("Spikes mapping exceeds its eight-tile source");
                }
            }
        }

        public static BodyAssets fromObjectAssets(CdR11aObjectAssets objects) {
            Objects.requireNonNull(objects, "objects");
            return new BodyAssets(objects.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(), objects.spikes(),
                    objects.view(CdR11aObjectAssets.Table.SPIKES_MAPPINGS).range().nativeAddress());
        }
    }

    /** Borrowed ONE actual-pool cut, exact pre-call player/globals, and the one pure shared result. */
    public record SolidCall(CdObjectPool.Snapshot callCut, CdSolidObject.Player expectedPlayer,
                            CdSolidObject.Globals expectedGlobals, CdSolidObject.Result result) {
        public SolidCall {
            Objects.requireNonNull(callCut, "callCut");
            Objects.requireNonNull(expectedPlayer, "expectedPlayer");
            Objects.requireNonNull(expectedGlobals, "expectedGlobals");
            Objects.requireNonNull(result, "result");
            if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalArgumentException("ObjSpikes publication requires SolidObject");
            }
        }
    }

    /** Literal native identity and exact POST-Solid Actor for the standalone MOVE.L oY publication. */
    public record YRewindCall(CdObjectState.Identity sourceIdentity, Actor expectedActor, int finalYBits) {
        public YRewindCall {
            Objects.requireNonNull(sourceIdentity, "sourceIdentity");
            Objects.requireNonNull(expectedActor, "expectedActor");
        }
    }

    /**
     * FRESH actual-pool cut after successful prefix publication, exact live a2 image, current Actor and both
     * complete global projections. The Result is from the shared STATIC direct HurtPlayer entry, never a
     * scanner selection/Hazard wrapper. This record borrows the cut for one synchronous commit only.
     */
    public record HurtCall(CdObjectPool.Snapshot callCut, CdObjectState sourceA2, Actor expectedActor,
                           PlayerGlobals expectedPlayer, WorldGlobals expectedWorld, CdPlayerTouchResponse.Result result) {
        public HurtCall {
            Objects.requireNonNull(callCut, "callCut");
            Objects.requireNonNull(sourceA2, "sourceA2");
            Objects.requireNonNull(expectedActor, "expectedActor");
            Objects.requireNonNull(expectedPlayer, "expectedPlayer");
            Objects.requireNonNull(expectedWorld, "expectedWorld");
            Objects.requireNonNull(result, "result");
            if (sourceA2.id() != OBJECT_ID || !callCut.slots().get(sourceA2.identity().dynamicIndex()).equals(sourceA2)) {
                throw new IllegalArgumentException("Direct ObjSpikes Hurt requires the exact live ID $26 a2 image");
            }
        }
    }

    /**
     * Root synchronously validates the complete same-owner 96-image cut, current source ID/physical phase,
     * expectedPlayer/expectedGlobals and Frame.validateObjectSolidResult before publishing a0, otherSlots,
     * Frame stores and ordered fmRequests ONCE. No intervening callback, retained cut, helper replay, player
     * dispatch or reentry. On return this body requires the exact result a0 AND every otherSlot to be live.
     * Frame/FM publication remains Root's responsibility; an exception leaves prior native cuts committed.
     */
    @FunctionalInterface
    public interface SolidCommit { void commit(SolidCall call); }

    /**
     * Root preflights physical identity/current phase and exact Actor, then validates and applies only the
     * literal native Y LONG subtraction through Frame's ObjectPreHurtYRewind methods. The store MUST complete
     * synchronously before return and before a new Hurt cut is captured. Even a same-value MOVE.L is executed.
     * No other player/contact/world/pool writes or FM; no callback/reentry. Later Hurt failure cannot undo it.
     */
    @FunctionalInterface
    public interface YRewindCommit { void commit(YRewindCall call); }

    /**
     * Root synchronously preflights ALL 96 same-owner images, current ID $26/physical phase/sourceA2, exact
     * Actor and complete PlayerGlobals/WorldGlobals/contact values, changed-slot identity/field authority and
     * Frame.validateObjectHurtResult BEFORE any response write. It then publishes the actual optional LostRing
     * images, Frame Actor/invincible/scoreChain, contact shield/combineRing and checked world partition once,
     * without intervening callbacks, and appends only the Result's actual ordered FM requests once. Normal
     * Hurt has no inferred FM_HURT. Root owns phase/nonreentry and closes a failed cycle; no rollback/retry,
     * old-cut reuse, LostRing dispatch, source substitution, or helper replay. Returned changedSlots must all
     * be live. The body cannot certify Frame/contact/world/FM publication; these are Root integration gates.
     */
    @FunctionalInterface
    public interface HurtCommit { void commit(HurtCall call); }

    /** Existing shared DrawObject over the actual pool; its actual output is reloaded before the native tail. */
    @FunctionalInterface
    public interface DrawObjectCall { void drawObject(CdObjectPool pool, CdObjectState.Identity slot); }

    /** Complete final projections plus immutable completed observations; the post-Solid links/angles survive. */
    public record Result(CdObjectState state, CdSolidObject.Player player, CdSolidObject.Globals solidGlobals,
                         PlayerGlobals playerGlobals, WorldGlobals worldGlobals, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(solidGlobals, "solidGlobals");
            Objects.requireNonNull(playerGlobals, "playerGlobals");
            Objects.requireNonNull(worldGlobals, "worldGlobals");
            effects = List.copyOf(effects);
        }
        public boolean deleted() {
            return effects.stream().anyMatch(effect -> effect instanceof DespawnChecked checked && checked.deleted());
        }
    }

    public enum UnsupportedBoundary {
        /** Odd or >2 oRoutine indexes outside the two-word routine directory (:8-16), before any writes. */
        ROUTINE_DISPATCH,
        /** Reached unreviewed selector-$A subtype2 tile read AFTER Init's priority store (:20-25). */
        TILE_ID_SUBTYPE2
    }
    public static final class UnsupportedSpikes extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private UnsupportedSpikes(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime placement;
    private final BodyAssets assets;
    private final CdSolidObject sharedSolid;
    private final SolidCommit solidCommit;
    private final YRewindCommit yRewindCommit;
    private final HurtCommit hurtCommit;
    private final DrawObjectCall drawObject;
    private boolean running;

    public CdSpikes(CdObjectPlacementRuntime placement, BodyAssets assets, CdSolidObject sharedSolid,
                    SolidCommit solidCommit, YRewindCommit yRewindCommit, HurtCommit hurtCommit, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.sharedSolid = Objects.requireNonNull(sharedSolid, "sharedSolid");
        this.solidCommit = Objects.requireNonNull(solidCommit, "solidCommit");
        this.yRewindCommit = Objects.requireNonNull(yRewindCommit, "yRewindCommit");
        this.hurtCommit = Objects.requireNonNull(hurtCommit, "hurtCommit");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /** One physical RunObjects visit. Main ALWAYS reads primary Y, with no outer visibility admission. */
    public Result run(int wholeIndex, Globals globals, Inputs inputs) {
        if (running) { throw new IllegalStateException("Reentrant ObjSpikes dispatch"); }
        Objects.requireNonNull(globals, "globals");
        var pool = placement.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch requires ID $26 at slot " + wholeIndex);
        }
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 2) {
            throw new UnsupportedSpikes(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine $" + Integer.toHexString(routine) + " indexes outside the two-word directory");
        }
        running = true;
        try {
            var effects = new ArrayList<Effect>();
            if (routine == 0) {
                effects.add(new RoutineEntered(NativeRoutine.INIT));
                // ObjSpikes_Init:20-23: exact order/widths; all other dirty bytes/fractions remain untouched.
                state = state.addByte(ROUTINE, 2).moveLong(MAPPINGS, assets.mappingsAddress())
                        .moveByte(SPRITE_FLAGS, state.spriteFlags() | 4).moveByte(PRIORITY, 3);
                pool.write(state); // Native prefix is live BEFORE the reached SetObjectTileID read (:24-25).
                if (state.subtype2() != 0) {
                    throw new UnsupportedSpikes(UnsupportedBoundary.TILE_ID_SUBTYPE2, wholeIndex,
                            "selector $A / subtype2 $" + Integer.toHexString(state.subtype2()) + " is unreviewed");
                }
                int tile = CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), TILE_SELECTOR, state.subtype2());
                state = state.moveWord(TILE, tile); // SetObjectTileID:16, after its actual bounded reads succeed.
                pool.write(state);
                state = state.moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 8); // :26-27, after the tile helper.
                if ((state.spriteFlags() & 2) != 0) { // :28-31, vflip is a sprite bit, never a subtype family.
                    state = state.moveByte(WIDTH, 0x12).moveByte(COLLISION_TYPE, 0x83);
                }
                pool.write(state);
            }
            effects.add(new RoutineEntered(NativeRoutine.MAIN)); // Init FALLS THROUGH in the same dispatch.
            Objects.requireNonNull(inputs, "reached primary-player Main inputs");
            var player = inputs.player();
            var solidGlobals = inputs.solidGlobals();
            var playerGlobals = inputs.playerGlobals();
            var worldGlobals = inputs.worldGlobals();
            int playerY = player.actor().motion().centreY16_16() >>> 16;
            int objectY = state.centreY();
            int dy = (playerY - objectY) & 0xFFFF; // SUB.W, unsigned borrow determines whether NEG.W executes.
            if (playerY < objectY) { dy = (-dy) & 0xFFFF; } // :39-41, NOT signed-shortest-distance absolute.
            if (dy < 0x20) { // CMPI.W/BCC :44-45; no visibility gate outside the shared helper.
                boolean verticalFlip = (state.spriteFlags() & 2) != 0; // Native BTST BEFORE the shared call.
                pool.write(state);
                var solidCut = pool.capture(); // ONE actual capture; never an overlay or an earlier dispatch cut.
                var solid = sharedSolid.run(CdSolidObject.Entry.SOLID_OBJECT, state, player,
                        inputs.gates(), solidGlobals, solidCut);
                solidCommit.commit(new SolidCall(solidCut, player, solidGlobals, solid));
                if (!pool.state(wholeIndex).equals(solid.object())) {
                    throw new IllegalStateException("SolidObject commit did not publish a0's final image");
                }
                for (var other : solid.otherSlots()) {
                    if (!pool.state(other.identity().wholeIndex()).equals(other)) {
                        throw new IllegalStateException("SolidObject commit did not publish a2 slot " + other.identity().wholeIndex());
                    }
                }
                effects.add(new Solid(solid));
                state = pool.state(wholeIndex);
                player = solid.player();
                solidGlobals = solid.globals();
                playerGlobals = afterSolid(playerGlobals, solidGlobals);
                // Upright .ChkStand: actual d0, POST-Solid object flag3, current globals/routine/full hurt WORD.
                if (!verticalFlip && solid.d0().orElseThrow() != 0 && (state.flags() & 8) != 0
                        && playerGlobals.timeWarp() == 0 && playerGlobals.invincible() == 0
                        && player.actor().motion().routine() < 4 && player.actor().hurt() == 0) {
                    var expected = player.actor();
                    var motion = expected.motion();
                    int finalY = motion.centreY16_16() - (((short) motion.yVelocity()) << 8); // :70-75, LONG wraps.
                    var rewound = withY(expected, finalY);
                    yRewindCommit.commit(new YRewindCall(state.identity(), expected, finalY));
                    player = withActor(player, rewound); // Preserve EVERY post-Solid slot/link byte.
                    effects.add(new YRewound(finalY)); // Only after the standalone native store completed.
                    state = pool.state(wholeIndex);
                    if (state.id() != OBJECT_ID) {
                        throw new IllegalStateException("Direct Hurt source is no longer live ID $26");
                    }
                    var hurtCut = pool.capture(); // Fresh AFTER successful prefix publication, never solidCut.
                    var hurt = CdPlayerTouchResponse.hurtPlayer(player.actor(), playerGlobals, worldGlobals, state, hurtCut);
                    hurtCommit.commit(new HurtCall(hurtCut, state, player.actor(), playerGlobals, worldGlobals, hurt));
                    for (var changed : hurt.changedSlots()) {
                        if (!pool.state(changed.identity().wholeIndex()).equals(changed)) {
                            throw new IllegalStateException("HurtPlayer commit did not publish slot " + changed.identity().wholeIndex());
                        }
                    }
                    player = withActor(player, hurt.actor());
                    playerGlobals = hurt.player();
                    worldGlobals = hurt.world();
                    solidGlobals = new CdSolidObject.Globals(playerGlobals.invincible(), playerGlobals.scoreChain(),
                            solidGlobals.primaryAngle(), solidGlobals.secondaryAngle()); // Keep shared collision scratches.
                    effects.add(new Hurt(hurt));
                }
            }
            state = pool.state(wholeIndex); // .Restore reloads ACTUAL live a0 after the completed Hurt call.
            drawObject.drawObject(pool, state.identity()); // .Display:82, unconditional after normal Main.
            state = pool.state(wholeIndex); // Keep actual Draw mutations; do not rewrite a stale pre-draw image.
            effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            boolean deleted = checkObjDespawn(state, globals); // :83, tail JMP, current X (never an anchor).
            effects.add(new DespawnChecked(deleted));
            return new Result(pool.state(wholeIndex), player, solidGlobals, playerGlobals, worldGlobals, effects);
        } finally {
            running = false;
        }
    }

    private static PlayerGlobals afterSolid(PlayerGlobals before, CdSolidObject.Globals after) {
        return new PlayerGlobals(before.timeWarp(), after.invincible(), before.shield(), before.combineRing(),
                before.debugCheat(), before.debugMode(), after.scoreChain(), before.miniSonic());
    }
    private static CdSolidObject.Player withActor(CdSolidObject.Player before, Actor after) {
        return new CdSolidObject.Player(after, before.slotId(), before.pushObject(), before.standObject());
    }
    /** Only bytes $0C..$0F change here; Actor and all 20 motion fields otherwise retain the exact source cut. */
    private static Actor withY(Actor before, int yBits) {
        var m = before.motion();
        return new Actor(new CdPlayerMotionState(m.centreX16_16(), yBits, m.xVelocity(), m.yVelocity(), m.groundVelocity(),
                m.routine(), m.angle(), m.flags(), m.spriteFlags(), m.xRadius(), m.yRadius(), m.animation(),
                m.previousAnimation(), m.playerJump(), m.charge(), m.moveLock(), m.stick(), m.playerControl(),
                m.previousPrimaryAngle(), m.previousSecondaryAngle()),
                before.tile(), before.priority(), before.hurt(), before.rotationDistance());
    }

    /** CheckObjDespawn / DespawnObject / DespawnObjTimeWarp, Object Spawner.asm:385-446, through existing owners. */
    private boolean checkObjDespawn(CdObjectState state, Globals globals) {
        int x = state.centreX(); // MOVE.W oX precedes the sprite sign test (:386-390).
        boolean outside = (state.spriteFlags() & 0x80) == 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean warp = (globals.timeZone() & 0x80) != 0;
        if (!outside && !warp) { return false; }
        int savedId = state.savedFlagsId();
        if (savedId != 0) {
            int period = globals.timeZone() & 0x7F;
            if (warp) {
                int remapped = (period - (byte) globals.timeWarpDirection()) & 0xFFFF; // EXT.W/NEG.W/ADD.W :418-421.
                period = (short) remapped < 0 ? 0 : remapped < 3 ? remapped : 2; // :422-429, past/future clamps.
            }
            // Reached bad saved address leaves EVERY preceding Init/Solid/prefix/Hurt/Draw cut committed.
            placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        }
        placement.pool().deleteObject(state.identity().wholeIndex()); // DeleteObject clears sixteen LONGs/all64 bytes.
        return true;
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
