package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Bounded R11A ID $0D body, not a Frame, dispatcher, media owner or splash implementation.
 * N=1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/Level/_Objects/Spin Tunnel/Main (Door).asm:
 * ObjTunnelDoor/Index (:7-20), ChkPlayer (:23-50), Init/Main (:55-77), Animate/Reset (:82-93),
 * ChkCollision (:103-129). The inspected body has no FixBugs alternative; shipped behavior is kept.
 *
 * <p>Main always reaches the REAL reserved whole-1 image after its primary call, including in one-player
 * mode. Only its reached ClearObjects inactive image is currently admitted. CdSolidObject's stand-link-0
 * alias targets its current Player, whereas secondary native a2=objects+0 targets PRIMARY. This body does
 * not extend that owner: it rejects every non-cleared secondary before running it, and rejects a shared
 * secondary return with player/global/other-slot writes or alias-sensitive helper effects BEFORE commit.
 * It still calls the actual TOP_SOLID_OBJECT once on the projection of the actual cleared image. ID zero
 * alone never admits a slot. The no-write secondary projection never becomes another Frame actor.
 *
 * <p>Each helper has one fresh dynamic cut, synchronous Root preflight/commit and live readback. Earlier
 * source prefixes remain published on failure. Only ephemeral SolidCut/SolidCall values contain cuts;
 * neither this owner nor its Result retains them. Completed effects must never be replayed.
 */
public final class CdTunnelDoor {
    public static final int OBJECT_ID = 0x0D, SPLASH_ID = 0x0B, SECONDARY_WHOLE_INDEX = 1;
    public static final int TILE_SELECTOR = 0x0C, MAPPING_BYTES = 28, ANIMATION_BYTES = 14, ART_TILES = 12;

    public enum NativeRoutine { INIT, MAIN, ANIMATE, RESET }
    public enum NativeCall { DRAW_OBJECT, CHECK_OBJ_DESPAWN }
    public enum Participant { PRIMARY, SECONDARY }
    /** Root's FM owner resolves the release-specific native ID. */
    public enum FmSound { FM_A4 }

    public sealed interface Effect permits RoutineEntered, Solid, SplashCreated, PlayedFm, CallReturned, LoadedCleared, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    /** Actual completed shared return; SECONDARY's player value is observation only, never a primary patch. */
    public record Solid(Participant participant, CdSolidObject.Result result) implements Effect {
        public Solid { Objects.requireNonNull(participant); Objects.requireNonNull(result); }
    }
    /** Creation has already happened. Physical-pass eligibility is metadata; this body dispatches no child. */
    public record SplashCreated(CdObjectState state, boolean laterInPhysicalPass) implements Effect {
        public SplashCreated { Objects.requireNonNull(state); }
    }
    public record PlayedFm(FmSound sound) implements Effect {
        public PlayedFm { Objects.requireNonNull(sound); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    public record LoadedCleared(int savedId, int period) implements Effect { }
    public record Deleted() implements Effect { }

    /** Raw native bytes plus the pre-scroll camera word; period bounds apply only at a reached bank access. */
    public record Globals(int rawTimeZone, int rawTimeWarpDirection, int cameraXWord) {
        public Globals { byteValue(rawTimeZone); byteValue(rawTimeWarpDirection); wordValue(cameraXWord); }
    }
    public record SolidInputs(CdSolidObject.Player primary, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public SolidInputs { Objects.requireNonNull(primary); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }
    /** Actual Root readback, not permission to replace an actor from completed metadata. */
    public record LiveSolid(CdSolidObject.Player primary, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public LiveSolid { Objects.requireNonNull(primary); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }

    /** Format declarations authenticate no media; production uses fromObjectAssets on the real asset owner. */
    public record Assets(CdObjectVisualAssets visual, int mappingsAddress, CdAssetBytes objectTileIds) {
        public Assets {
            Objects.requireNonNull(visual); Objects.requireNonNull(objectTileIds);
            if (visual.mappingFrames().size() != 2 || visual.mappingTableOffset() != 0
                    || visual.mappings().length() != MAPPING_BYTES || visual.animations().size() != 1
                    || visual.animationTable().length() != ANIMATION_BYTES || objectTileIds.length() != 74) {
                throw new IllegalArgumentException("Door needs complete two-frame/one-script includes and ObjectTileIDs");
            }
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                if (piece.tileOffset() > ART_TILES - piece.tileCount()) {
                    throw new IllegalArgumentException("Door mapping exceeds its existing twelve-tile bank");
                }
            }
            if (mappingsAddress <= 0 || (mappingsAddress & 1) != 0
                    || mappingsAddress > 0xFFFFFF - (MAPPING_BYTES - 1)) {
                throw new IllegalArgumentException("Door mappings need an aligned bounded native pointer");
            }
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner);
            return new Assets(owner.tunnelDoor(),
                    owner.view(CdR11aObjectAssets.Table.TUNNEL_DOOR_MAPPINGS).range().nativeAddress(),
                    owner.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes());
        }
    }

    /**
     * One real call cut, discarded on return. secondary is present exactly for the second native a1.
     * expectedPrimary remains Root's actual primary even when the helper's input is the reserved projection.
     */
    public record SolidCut(Participant participant, CdObjectState.Identity objectIdentity,
                           CdObjectPool.Snapshot callCut, CdSolidObject.Player expectedPrimary,
                           CdSolidObject.Gates expectedGates, CdSolidObject.Globals expectedGlobals,
                           Optional<CdReservedObjectBank.Image> secondary) {
        public SolidCut {
            Objects.requireNonNull(participant); Objects.requireNonNull(objectIdentity); Objects.requireNonNull(callCut);
            Objects.requireNonNull(expectedPrimary); Objects.requireNonNull(expectedGates); Objects.requireNonNull(expectedGlobals);
            Objects.requireNonNull(secondary);
            if (secondary.isPresent() != (participant == Participant.SECONDARY)
                    || secondary.isPresent() && secondary.get().identity().wholeIndex() != SECONDARY_WHOLE_INDEX) {
                throw new IllegalArgumentException("Secondary cut must name the actual reserved whole-1 image");
            }
        }
    }
    public record SolidCall(SolidCut cut, CdSolidObject.Result result) {
        public SolidCall { Objects.requireNonNull(cut); Objects.requireNonNull(result); }
    }
    /**
     * Mandatory synchronous Root seam. preflight validates open object phase, all expected primary/shared
     * values, this one dynamic cut and (when present) the real reserved image, then returns ACTUAL live values.
     * It must publish nothing. commit repeats the phase/CAS checks, folds a0/otherSlots through S4, and for
     * PRIMARY patches only the shared native write sets through S3, including the current shared KillPlayer
     * owner and Root's death-camera branch. It forwards the result's FM requests once in source order.
     * SECONDARY has already passed the no-write guard: commit only its dynamic object stores; never feed
     * result.player() to Frame/S3. Return actual live primary/gates/globals after publication. Reject stale or
     * closed phases. Do not retain these calls, dispatch bodies, replay metadata or manufacture a Solid.Result.
     * Exceptions stop without rollback; the body checks affected dynamic images and secondary readback too.
     */
    public interface SolidOwner {
        LiveSolid preflight(SolidCut cut);
        LiveSolid commit(SolidCall call);
    }
    /** Existing CPU DrawObject cull/enqueue on this live pool; no extra body dispatch or DrawObjects call. */
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }
    /** PlayFMSound only; no object/player gameplay mutation, reentry or delayed replay. */
    @FunctionalInterface public interface PlayFmCall { void playFm(FmSound sound); }

    public record Result(CdObjectState state, CdSolidObject.Player finalPrimary, CdSolidObject.Globals finalGlobals,
                         List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(finalPrimary); Objects.requireNonNull(finalGlobals);
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary { ROUTINE_DISPATCH, TILE_ID_SUBTYPE2, SECONDARY_PLAYER_OWNER, SECONDARY_SOLID_OWNER }
    public static final class UnsupportedTunnelDoor extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedTunnelDoor(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Door whole " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        /** Routine/subtype byte, first nonzero secondary byte OFFSET, or rejected shared Path ordinal. */
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final CdReservedObjectBank reserved;
    private final Assets assets;
    private final CdObjectAnimation.AnimationTable animation;
    private final CdSolidObject solid;
    private final SolidOwner solidOwner;
    private final DrawObjectCall drawObject;
    private final PlayFmCall playFm;

    public CdTunnelDoor(CdObjectPlacementRuntime runtime, CdReservedObjectBank reserved, Assets assets,
                        CdSolidObject solid, SolidOwner solidOwner, DrawObjectCall drawObject, PlayFmCall playFm) {
        this.runtime = Objects.requireNonNull(runtime); this.reserved = Objects.requireNonNull(reserved);
        this.assets = Objects.requireNonNull(assets); this.solid = Objects.requireNonNull(solid);
        this.solidOwner = Objects.requireNonNull(solidOwner); this.drawObject = Objects.requireNonNull(drawObject);
        this.playFm = Objects.requireNonNull(playFm);
        animation = new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, 1);
    }

    /** ObjTunnelDoor (:7-20): the selected routine returns before DrawObject -> CheckObjDespawn. */
    public Result run(int wholeIndex, Globals globals, SolidInputs inputs) {
        Objects.requireNonNull(globals); Objects.requireNonNull(inputs);
        var pool = runtime.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Door dispatch requires live ID $0D");
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 6) {
            throw new UnsupportedTunnelDoor(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
        }
        var c = new Cursor(pool, state, inputs);
        switch (routine) {
            case 0 -> { init(c); main(c); } // No Init RTS: this dispatch reaches Main immediately.
            case 2 -> main(c);
            case 4 -> animate(c);
            case 6 -> reset(c);
        }
        c.publish();
        drawObject.drawObject(pool, state.identity());
        c.state = pool.state(wholeIndex); // Current bit7, including DrawObject's clear, owns the following test.
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        checkObjDespawn(c, globals);
        return new Result(c.state, c.primary, c.globals, c.effects);
    }

    /** Init (:55-68) does not clear frame, timer, animation, flags, velocities, fractions or dirty variables. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveLong(MAPPINGS, assets.mappingsAddress()).moveByte(PRIORITY, 1)
                .moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveByte(WIDTH, c.state.subtype() == 2 ? 0x18 : 0x2C)
                .moveByte(Y_RADIUS, 8);
        c.publish(); // Source prefix is live before JSR SetObjectTileID's reached subtype2 read.
        if (c.state.subtype2() != 0) {
            throw new UnsupportedTunnelDoor(UnsupportedBoundary.TILE_ID_SUBTYPE2, c.wholeIndex, c.state.subtype2());
        }
        c.state = c.state.moveWord(TILE,
                CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), TILE_SELECTOR, c.state.subtype2()));
    }

    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        checkPlayer(c, Participant.PRIMARY, c.primary, Optional.empty());
        // This read belongs HERE, after every completed primary helper/creation/FM prefix (:76).
        var secondary = requireClearedSecondary(c.wholeIndex);
        checkPlayer(c, Participant.SECONDARY, projectSecondary(secondary), Optional.of(secondary));
    }

    /** ChkPlayer (:23-50). Opening returns directly; only the non-opening branch reaches TopSolidObject. */
    private void checkPlayer(Cursor c, Participant participant, CdSolidObject.Player player,
                             Optional<CdReservedObjectBank.Image> secondary) {
        var motion = player.actor().motion();
        if (motion.yVelocity() < 0 && rectangle(c.state, motion.centreX16_16() >>> 16, motion.centreY16_16() >>> 16)) {
            c.state = c.state.moveByte(ROUTINE, 4);
            c.publish();
            if (c.state.subtype() == 0) {
                var free = c.pool.findObjSlot(); // Actual live FindObjSlot: ID-only, physical first, no implicit clear.
                if (free.isPresent()) {
                    int childWhole = free.getAsInt();
                    var child = c.pool.state(childWhole).moveByte(ID, SPLASH_ID)
                            .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY()).addWord(Y, -4);
                    c.pool.write(child);
                    c.effects.add(new SplashCreated(child, childWhole > c.wholeIndex));
                    playFm.playFm(FmSound.FM_A4);
                    c.effects.add(new PlayedFm(FmSound.FM_A4));
                }
            }
            return;
        }
        topSolid(c, participant, player, secondary);
    }

    /** ChkCollision (:103-129): both sums truncate to words; BMI precedes unsigned strict CMP/BCC. */
    private static boolean rectangle(CdObjectState object, int playerX, int playerY) {
        int width = object.unsignedByte(WIDTH), height = object.unsignedByte(Y_RADIUS);
        int dx = (playerX - object.centreX() + width) & 0xFFFF;
        if ((dx & 0x8000) != 0 || dx >= width * 2) return false;
        int dy = (playerY - object.centreY() + height) & 0xFFFF;
        return (dy & 0x8000) == 0 && dy < height * 2;
    }

    private void topSolid(Cursor c, Participant participant, CdSolidObject.Player helperPlayer,
                          Optional<CdReservedObjectBank.Image> secondary) {
        c.publish();
        var cut = new SolidCut(participant, c.state.identity(), c.pool.capture(), c.primary, c.gates, c.globals, secondary);
        requireLive(c.primary, c.gates, c.globals, solidOwner.preflight(cut));
        requireDynamicCut(c.pool, cut.callCut()); // No second capture, derived view, retained lookahead or helper replay.
        if (secondary.isPresent()) {
            var current = requireClearedSecondary(c.wholeIndex);
            if (!current.equals(secondary.get())) throw new IllegalStateException("Door secondary changed during preflight");
        }
        var result = solid.run(CdSolidObject.Entry.TOP_SOLID_OBJECT, c.state, helperPlayer, c.gates, c.globals, cut.callCut());
        if (participant == Participant.SECONDARY) requireInactiveSecondaryReturn(c.wholeIndex, result, helperPlayer, c.globals);
        var live = solidOwner.commit(new SolidCall(cut, result));
        var expectedPrimary = participant == Participant.PRIMARY ? result.player() : c.primary;
        requireLive(expectedPrimary, c.gates, result.globals(), live);
        if (!c.pool.state(c.wholeIndex).equals(result.object())) {
            throw new IllegalStateException("Door SolidOwner did not publish a0");
        }
        for (var other : result.otherSlots()) {
            if (!c.pool.state(other.identity().wholeIndex()).equals(other)) {
                throw new IllegalStateException("Door SolidOwner did not publish a2 whole " + other.identity().wholeIndex());
            }
        }
        if (secondary.isPresent() && !reserved.state(SECONDARY_WHOLE_INDEX).equals(secondary.get())) {
            throw new IllegalStateException("Door SolidOwner changed inactive secondary storage");
        }
        c.state = result.object(); c.primary = live.primary(); c.globals = live.globals();
        c.effects.add(new Solid(participant, result));
    }

    /**
     * Defensive ownership admission, also usable for independently obtained real shared returns. This is not
     * collision physics or a result factory. StandOnObject and all probe/FM/Kill effects require a broader
     * secondary owner even if their final values happen to compare equal. Same-value native stores count.
     */
    static void requireInactiveSecondaryReturn(int wholeIndex, CdSolidObject.Result result,
                                               CdSolidObject.Player projected, CdSolidObject.Globals globals) {
        boolean harmlessCalls = result.calls().stream().allMatch(call -> call instanceof CdSolidObject.RoutineCall r
                && (r.routine() == CdSolidObject.Routine.STOP_OBJ_PUSH || r.routine() == CdSolidObject.Routine.GET_OFF_OBJECT));
        if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT || !result.playerWrites().isEmpty()
                || !result.globalWrites().isEmpty() || !result.otherSlots().isEmpty() || !harmlessCalls
                || !result.player().equals(projected) || !result.globals().equals(globals)) {
            throw new UnsupportedTunnelDoor(UnsupportedBoundary.SECONDARY_SOLID_OWNER, wholeIndex, result.path().ordinal());
        }
    }

    /** Test every REAL stored byte against the reached native ClearObjects image, without creating a substitute. */
    private CdReservedObjectBank.Image requireClearedSecondary(int callingWholeIndex) {
        var image = reserved.state(SECONDARY_WHOLE_INDEX);
        byte[] actual = image.copyBytes();
        for (int offset = 0; offset < actual.length; offset++) if (actual[offset] != 0) {
            throw new UnsupportedTunnelDoor(UnsupportedBoundary.SECONDARY_PLAYER_OWNER, callingWholeIndex, offset);
        }
        return image;
    }

    /** _Definitions.inc:oPlayer* (:73-95): projection reads the actual image, including all fractions/aliases. */
    private static CdSolidObject.Player projectSecondary(CdReservedObjectBank.Image image) {
        var motion = new CdPlayerMotionState(image.longBitsAt(0x08), image.longBitsAt(0x0C),
                (short) image.wordAt(0x10), (short) image.wordAt(0x12), (short) image.wordAt(0x14),
                image.byteAt(0x24), image.byteAt(0x26), image.byteAt(0x22), image.byteAt(0x01), image.byteAt(0x17),
                image.byteAt(0x16), image.byteAt(0x1C), image.byteAt(0x1D), image.byteAt(0x3C), image.byteAt(0x2A),
                (short) image.wordAt(0x3E), image.byteAt(0x38), image.byteAt(0x2C), image.byteAt(0x36), image.byteAt(0x37));
        var actor = new CdPlayerTouchResponse.Actor(motion, image.wordAt(0x02), image.byteAt(0x18),
                image.wordAt(0x30), image.byteAt(0x39));
        return new CdSolidObject.Player(actor, image.byteAt(0), image.byteAt(0x20), image.byteAt(0x3D));
    }

    /** Animate (:82-84) borrows the shared animator and the actual complete asset view, with no priming. */
    private void animate(Cursor c) {
        c.enter(NativeRoutine.ANIMATE);
        var before = c.state;
        var next = CdObjectAnimation.step(new CdObjectAnimation.State(before.unsignedByte(ANIMATION),
                before.unsignedByte(PREVIOUS_ANIMATION), before.unsignedByte(ANIMATION_FRAME), before.unsignedByte(ANIMATION_TIME),
                before.unsignedByte(MAP_FRAME), before.spriteFlags(), before.flags(), before.routine(), before.unsignedByte(ROUTINE_2)), animation);
        c.state = before.moveByte(ANIMATION, next.animationId()).moveByte(PREVIOUS_ANIMATION, next.previousId())
                .moveByte(ANIMATION_FRAME, next.scriptIndex()).moveByte(ANIMATION_TIME, next.timer())
                .moveByte(MAP_FRAME, next.mapFrame()).moveByte(SPRITE_FLAGS, next.spriteFlags())
                .moveByte(FLAGS, next.statusFlags()).moveByte(ROUTINE, next.routine()).moveByte(ROUTINE_2, next.routine2());
    }

    /** Reset (:89-93) returns after three byte stores; Main waits for the next dispatch. */
    private static void reset(Cursor c) {
        c.enter(NativeRoutine.RESET);
        c.state = c.state.moveByte(PREVIOUS_ANIMATION, 1).moveByte(MAP_FRAME, 0).addByte(ROUTINE, -4);
    }

    /** Object Spawner.asm:CheckObjDespawn/DespawnObject/DespawnObjTimeWarp (:385-446). */
    private void checkObjDespawn(Cursor c, Globals globals) {
        int d0 = c.state.centreX();
        boolean outside = false;
        if ((c.state.spriteFlags() & 0x80) == 0) {
            d0 &= 0xFF80;
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;
            outside = ((d0 - d1) & 0xFFFF) > 0x280;
        }
        boolean warping = (globals.rawTimeZone() & 0x80) != 0;
        if (outside || warping) {
            int savedId = c.state.savedFlagsId();
            if (savedId != 0) {
                int period = globals.rawTimeZone() & 0x7F;
                if (warping) {
                    int d1 = (period - (byte) globals.rawTimeWarpDirection()) & 0xFFFF;
                    period = (short) d1 < 0 ? 0 : d1 >= 3 ? 2 : d1;
                }
                runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new LoadedCleared(savedId, period));
            }
            c.pool.deleteObject(c.wholeIndex);
            c.state = c.pool.state(c.wholeIndex);
            c.effects.add(new Deleted());
        }
        c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }

    private static void requireLive(CdSolidObject.Player primary, CdSolidObject.Gates gates,
                                    CdSolidObject.Globals globals, LiveSolid actual) {
        Objects.requireNonNull(actual, "Root live shared readback");
        if (!primary.equals(actual.primary()) || !gates.equals(actual.gates()) || !globals.equals(actual.globals())) {
            throw new IllegalStateException("Door Root shared readback differs from required live values");
        }
    }
    private static void requireDynamicCut(CdObjectPool pool, CdObjectPool.Snapshot cut) {
        for (var expected : cut.slots()) if (!pool.state(expected.identity().wholeIndex()).equals(expected)) {
            throw new IllegalStateException("Door dynamic call cut changed during preflight");
        }
    }
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final CdSolidObject.Gates gates;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        CdSolidObject.Player primary;
        CdSolidObject.Globals globals;
        Cursor(CdObjectPool pool, CdObjectState state, SolidInputs inputs) {
            this.pool = pool; this.state = state; wholeIndex = state.identity().wholeIndex();
            primary = inputs.primary(); gates = inputs.gates(); globals = inputs.globals();
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
