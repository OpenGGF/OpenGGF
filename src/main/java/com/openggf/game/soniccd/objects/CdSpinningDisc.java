package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdSpinningDisc.DiscPlayerField.*;

/**
 * One synchronous physical ID09 call, with no Frame, Runtime, world or independent player owner.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/Level:
 * Palmtree Panic/Objects/Spinning Disc/Main.asm:7-223; Object Solid.asm:15-146,188-479;
 * Object Animate.asm:14-95; Functions (Misc).asm:69-101; Object Spawner.asm:385-446;
 * Object Functions.asm:137-196,228-237. These routines have no FixBugs alternative.
 * Java/API base 64776b7822f4059312988a9856d8a842855fbf68 with Root's fixed asset,
 * plain-Solid-prefix and rotation-angle source dependencies. ALL author execution is UNRUN.
 *
 * <p>Every publication adopts prebuilt immutable values through the actual owner's synchronous port.
 * Store records are ordered observations, including same-value stores, never commands to replay.
 * Fresh same-owner complete96 cuts and source64 images surround reads and nonwriting callbacks;
 * successful publications are compared with their actual returned pool/player images. Plain public
 * metadata does not authenticate an invocation, site, source, sequence, no-op or sound entitlement.
 * Root owns private physical dispatch, full-State/Environment CAS, exactly-once consumption, FM ledger,
 * callback failure closure and cross-owner atomic preflight. In particular the Solid owner must stage
 * its Frame aggregates before any owner assignment; post-publication component checks do not prove it.
 *
 * <p>Player projections below are invocation-local scalars only. No persistent primary64 shadow,
 * new capture/global RAM, phase, Frame import, asset bank, art source or controlled-Body admission.
 * The native wrapper's DrawObject is deliberately mutating; reload its actual slot before despawn.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdSpinningDisc {
    public static final int OBJECT_ID = 0x09;
    public static final int TILE_SELECTOR = 6;

    public enum NativeRoutine { INIT, MAIN, CHECK_DIRS, CHECK_JUMP }
    public enum NativeCall {
        SET_OBJECT_TILE_ID, SOLID_OBJECT, CALC_SINE, ANIMATE_OBJECT, PLAY_FM_SOUND, DRAW_OBJECT, CHECK_OBJ_DESPAWN
    }
    public enum FmSound {
        FM_A0(0xA0);
        private final int nativeId;
        FmSound(int nativeId) { this.nativeId = nativeId; }
        public int nativeId() { return nativeId; }
    }
    /** Finite native operations. A caller never supplies an offset, width, mask or operation. */
    public enum StoreKind { MOVE_BYTE, MOVE_WORD, ADD_BYTE, ADD_WORD, SUB_BYTE, BSET_BYTE, BCLR_BYTE, CLEAR_BYTE, COPY_CONTROL_WORD }
    /**
     * Each value fixes BOTH the semantic target and native instruction site. The second miniature
     * hitbox stores are intentionally distinct, even when they store the same raw values.
     */
    public enum DiscPlayerField {
        CONTROL_ACQUIRE(StoreKind.BSET_BYTE), ANIMATION_ACQUIRE(StoreKind.MOVE_BYTE),
        ROTATION_ZERO(StoreKind.MOVE_BYTE), ROTATION_LEFT(StoreKind.MOVE_BYTE), DISTANCE_ACQUIRE(StoreKind.MOVE_BYTE),
        ROTATION_ADVANCE(StoreKind.ADD_BYTE), ORBIT_X_BASE(StoreKind.MOVE_WORD), ORBIT_X_OFFSET(StoreKind.ADD_WORD),
        SCRIPT_INDEX(StoreKind.MOVE_BYTE), DISTANCE_CARDINAL(StoreKind.ADD_BYTE), LOGICAL_CONTROLS(StoreKind.COPY_CONTROL_WORD),
        DISTANCE_GROW(StoreKind.ADD_BYTE), DISTANCE_SHRINK(StoreKind.SUB_BYTE), DISTANCE_CLAMP(StoreKind.MOVE_BYTE),
        CONTROL_CLEAR(StoreKind.CLEAR_BYTE), JUMP_X_VELOCITY(StoreKind.ADD_WORD), JUMP_Y_VELOCITY(StoreKind.ADD_WORD),
        FLAGS_AIR(StoreKind.BSET_BYTE), FLAGS_PUSH_CLEAR(StoreKind.BCLR_BYTE), JUMP_SET(StoreKind.MOVE_BYTE), STICK_CLEAR(StoreKind.CLEAR_BYTE),
        JUMP_Y_RADIUS_MINI(StoreKind.MOVE_BYTE), JUMP_X_RADIUS_MINI(StoreKind.MOVE_BYTE),
        JUMP_Y_RADIUS_NORMAL(StoreKind.MOVE_BYTE), JUMP_X_RADIUS_NORMAL(StoreKind.MOVE_BYTE),
        ROLL_Y_RADIUS_MINI(StoreKind.MOVE_BYTE), ROLL_X_RADIUS_MINI(StoreKind.MOVE_BYTE),
        ROLL_Y_RADIUS_NORMAL(StoreKind.MOVE_BYTE), ROLL_X_RADIUS_NORMAL(StoreKind.MOVE_BYTE), ROLL_Y_ADVANCE(StoreKind.ADD_WORD),
        FLAGS_ROLL(StoreKind.BSET_BYTE), ANIMATION_ROLL(StoreKind.MOVE_BYTE), FLAGS_ROLL_JUMP(StoreKind.BSET_BYTE);
        private final StoreKind kind;
        DiscPlayerField(StoreKind kind) { this.kind = kind; }
        public StoreKind kind() { return kind; }
        public int widthBits() {
            return switch (kind) { case MOVE_WORD, ADD_WORD, COPY_CONTROL_WORD -> 16; default -> 8; };
        }
    }

    /** Raw mapping LONG is retained; constructed formats confer no media authentication. */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets visual, int mappingAddressBits, CdAssetBytes playerFrames) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds"); Objects.requireNonNull(visual, "visual");
            Objects.requireNonNull(playerFrames, "playerFrames");
            if (visual.mappingFrames().size() != 3 || visual.animations().size() != 1) {
                throw new IllegalArgumentException("Disc requires its existing three counted frames and one script");
            }
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                if (piece.tileOffset() > 12 - piece.tileCount()) {
                    throw new IllegalArgumentException("Disc piece exceeds its existing twelve-tile source");
                }
            }
            // No eager tileWord, pose-byte read or extent claim: those reads belong to reached code.
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            return new Assets(owner.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(), owner.spinningDisc(),
                    CdR11aObjectAssets.Table.SPINNING_DISC_MAPPINGS.range().nativeAddress(),
                    owner.view(CdR11aObjectAssets.Table.SPINNING_DISC_PLAYER_FRAMES).bytes());
        }
    }
    /** Only widths are checked. Raw time/warp bytes are not normalized into canonical periods. */
    public record Globals(int timeStopTimerWord, int preScrollCameraXWord, int rawTimeZoneByte, int rawDirectionByte) {
        public Globals { wordValue(timeStopTimerWord); wordValue(preScrollCameraXWord); byteValue(rawTimeZoneByte); byteValue(rawDirectionByte); }
    }
    /** Actual immutable live pair at this reached cut, never a capture or a capability. */
    public record PlayerCut(CdPlayerBody.State state, CdPlayerBody.Environment environment) {
        public PlayerCut { Objects.requireNonNull(state, "state"); Objects.requireNonNull(environment, "environment"); }
    }
    /** Raw WORD observations are unsigned, even when the owning Motion value is signed. */
    public record PlayerStore(DiscPlayerField field, int expectedRaw, int value) {
        public PlayerStore {
            Objects.requireNonNull(field, "field"); rawValue(field, expectedRaw); rawValue(field, value);
            fixedOperation(field, expectedRaw, value);
        }
    }
    /** All immutable aggregates and defensive metadata are built before entering the publication port. */
    public record PlayerResult(CdPlayerBody.State finalState, CdPlayerBody.Environment finalEnvironment, List<PlayerStore> stores) {
        public PlayerResult {
            Objects.requireNonNull(finalState, "finalState"); Objects.requireNonNull(finalEnvironment, "finalEnvironment");
            stores = List.copyOf(stores);
        }
    }
    public record PlayerCall(CdObjectPool.Snapshot actualCut, CdObjectState expectedSource, PlayerCut expectedPlayer, PlayerResult result) {
        public PlayerCall { sourceInCut(actualCut, expectedSource); validatePlayerResult(expectedPlayer, result); }
    }
    public interface PlayerOwner { PlayerCut read(); void commit(PlayerCall call); }
    public record SolidCall(CdObjectPool.Snapshot actualCut, CdObjectState expectedSource, PlayerCut expectedPlayer, CdSolidObject.Outcome outcome) {
        public SolidCall {
            sourceInCut(actualCut, expectedSource); Objects.requireNonNull(expectedPlayer, "expectedPlayer"); Objects.requireNonNull(outcome, "outcome");
            if (solidImages(outcome).entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalArgumentException("Disc calls genuine plain SolidObject only");
            }
            if (outcome instanceof CdSolidObject.Completed completed && completed.completedResult().d0().isEmpty()) {
                throw new IllegalArgumentException("Completed plain SolidObject must define its real d0");
            }
        }
    }
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }
    public record FmCall(CdObjectPool.Snapshot actualCut, CdObjectState expectedSource, PlayerCut expectedPlayer, FmSound sound) {
        public FmCall { sourceInCut(actualCut, expectedSource); Objects.requireNonNull(expectedPlayer, "expectedPlayer"); Objects.requireNonNull(sound, "sound"); }
    }
    @FunctionalInterface public interface PlayFmCall { void play(FmCall call); }
    @FunctionalInterface public interface DrawObjectCall { void drawObject(CdObjectPool pool, CdObjectState.Identity identity); }

    public sealed interface Effect permits RoutineEntered, CallReturned, SolidReturned, PlayerCommitted, FmPlayed, SavedFlagChanged, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { public RoutineEntered { Objects.requireNonNull(routine); } }
    public record CallReturned(NativeCall call) implements Effect { public CallReturned { Objects.requireNonNull(call); } }
    public record SolidReturned(CdSolidObject.Result result) implements Effect { public SolidReturned { Objects.requireNonNull(result); } }
    public record PlayerCommitted(PlayerResult result) implements Effect { public PlayerCommitted { Objects.requireNonNull(result); } }
    public record FmPlayed(FmSound sound) implements Effect { public FmPlayed { Objects.requireNonNull(sound); } }
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record Deleted() implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, TILE_ENTRY, TILE_ADDRESS, PLAYER_FRAME_ADDRESS, ANIMATION_DIRECTORY_ENTRY, ANIMATION_SPEED_ADDRESS,
        ANIMATION_FRAME_ADDRESS, ANIMATION_OPERAND_ADDRESS, SAVED_FLAGS_ADDRESS
    }
    public static final class UnsupportedSpinningDisc extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedSpinningDisc(UnsupportedBoundary boundary, Cursor c, int value) {
            super(boundary + " at Disc whole slot " + c.wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = c.wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdPlayerMath math;
    private final CdSolidObject sharedSolid;
    private final SolidCommit solidCommit;
    private final PlayerOwner playerOwner;
    private final PlayFmCall playFm;
    private final DrawObjectCall drawObject;
    private boolean running;

    public CdSpinningDisc(CdObjectPlacementRuntime placement, Assets assets, CdPlayerMath math, CdSolidObject sharedSolid,
                          SolidCommit solidCommit, PlayerOwner playerOwner, PlayFmCall playFm, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement, "placement"); this.assets = Objects.requireNonNull(assets, "assets");
        this.math = Objects.requireNonNull(math, "math"); this.sharedSolid = Objects.requireNonNull(sharedSolid, "sharedSolid");
        this.solidCommit = Objects.requireNonNull(solidCommit, "solidCommit"); this.playerOwner = Objects.requireNonNull(playerOwner, "playerOwner");
        this.playFm = Objects.requireNonNull(playFm, "playFm"); this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /** ObjSpinningDisc:15-33: native dispatch, optional shared animation, DrawObject, CheckObjDespawn. */
    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Disc cannot reenter its synchronous physical dispatch");
        running = true;
        try {
            Objects.requireNonNull(globals, "globals"); var pool = placement.pool(); var source = pool.state(wholeIndex);
            if (source.id() != OBJECT_ID) throw new IllegalArgumentException("Selected slot is not Disc ID $09");
            var c = new Cursor(pool, source);
            switch (source.routine()) {
                case 0 -> { init(c); main(c); }
                case 2 -> main(c);
                default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, source.routine());
            }
            if (globals.timeStopTimerWord() == 0) animate(c);
            draw(c); despawn(c, globals); return new Result(c.state, c.effects);
        } finally { running = false; }
    }
    /** Init:36-44: map and header prefix precede the reached selector6/subtype2 read. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, 4).moveByte(PRIORITY, 4)
                .moveLong(MAPPINGS, assets.mappingAddressBits()); c.publish();
        if (c.state.subtype2() != 0) throw unsupported(UnsupportedBoundary.TILE_ENTRY, c, c.state.subtype2());
        int tile;
        try { tile = CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), TILE_SELECTOR, c.state.subtype2()); }
        catch (IllegalArgumentException missing) { throw unsupported(UnsupportedBoundary.TILE_ADDRESS, c, TILE_SELECTOR * 2); }
        c.state = c.state.moveWord(TILE, tile); c.publish(); c.returned(NativeCall.SET_OBJECT_TILE_ID);
        c.state = c.state.moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 8); c.publish();
    }
    /** Main:49-104: signed visibility, real plain Solid, BSET-old acquisition, orbit, pose and input. */
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        if (c.state.signedByte(SPRITE_FLAGS) >= 0) return;
        var result = solid(c);
        if (result.d0().orElseThrow() == 0) return;
        var player = readPlayer(c); var q = new PlayerProjection(player);
        int oldControl = q.control;
        q.store(CONTROL_ACQUIRE, oldControl | 1);
        if ((oldControl & 1) == 0) {
            q.store(ANIMATION_ACQUIRE, 0x2D); q.store(ROTATION_ZERO, 0);
            int playerX = q.x >>> 16, objectX = c.state.centreX();
            int delta = (playerX - objectX) & 0xFFFF;
            if (playerX < objectX) { delta = (-delta) & 0xFFFF; q.store(ROTATION_LEFT, 0x80); }
            q.store(DISTANCE_ACQUIRE, delta & 255);
        }
        // Unsigned routine gate follows ALL acquisition stores; no early dead-player shortcut.
        if (player.state().motion().routine() >= 6) { publishPlayer(c, player, q); return; }
        q.store(ROTATION_ADVANCE, (q.rotation + 8) & 255); publishPlayer(c, player, q);
        int angle = q.rotation;
        var trig = math.calcSine(angle); c.returned(NativeCall.CALC_SINE);
        player = readPlayer(c); q = new PlayerProjection(player);
        int product = trig.cosine() * q.distance; // MULS.W signed cosine * zero-extended distance BYTE.
        q.store(ORBIT_X_BASE, c.state.centreX());
        q.store(ORBIT_X_OFFSET, ((q.x >>> 16) + (product >>> 8)) & 0xFFFF); // LSR.L, then ADD.W.
        publishPlayer(c, player, q); // Both X WORD stores precede the selected PC-relative pose byte.
        int poseIndex = (angle & 0xF0) >>> 4;
        if (poseIndex >= assets.playerFrames().length()) throw unsupported(UnsupportedBoundary.PLAYER_FRAME_ADDRESS, c, poseIndex);
        int pose = assets.playerFrames().unsignedByte(poseIndex);
        player = readPlayer(c); q = new PlayerProjection(player); q.store(SCRIPT_INDEX, pose);
        if ((angle & 0x3F) == 0) q.store(DISTANCE_CARDINAL, (q.distance + 1) & 255);
        var controls = player.environment().controls();
        q.store(LOGICAL_CONTROLS, (controls.rawHeld() << 8) | controls.rawTap()); // Native full copy ignores lock.
        c.enter(NativeRoutine.CHECK_DIRS); checkDirs(c, q); publishPlayer(c, player, q);
        checkJump(c);
    }
    /** CheckDirs:114-152: unsigned post-orbit X borrow, right equality, both held grow. */
    private static void checkDirs(Cursor c, PlayerProjection q) {
        boolean left = (q.x >>> 16) < c.state.centreX();
        int held = q.logical >>> 8, grow = left ? 4 : 8, shrink = left ? 8 : 4;
        if ((held & grow) != 0) q.store(DISTANCE_GROW, (q.distance + 1) & 255);
        else if ((held & shrink) != 0) {
            int old = q.distance; q.store(DISTANCE_SHRINK, (old - 1) & 255);
            if (old == 0) q.store(DISTANCE_CLAMP, 0); // Preserve native SUBQ 00->FF BEFORE clamp.
        }
    }
    /** CheckJump:157-223: direct custom jump; no normal Jump, GetOff or charge/link cleanup. */
    private void checkJump(Cursor c) {
        c.enter(NativeRoutine.CHECK_JUMP); var player = readPlayer(c);
        if ((player.environment().controls().logicalTap() & 0x70) == 0) return;
        var q = new PlayerProjection(player); q.store(CONTROL_CLEAR, 0); publishPlayer(c, player, q);
        int speed = (c.state.flags() & 0x40) == 0 ? 0x680 : 0x380; // OBJECT water bit, not player's.
        var trig = math.calcSine((player.state().motion().angle() - 0x40) & 255); c.returned(NativeCall.CALC_SINE);
        player = readPlayer(c); q = new PlayerProjection(player);
        q.store(JUMP_X_VELOCITY, (q.xVelocity + ((trig.cosine() * speed) >> 8)) & 0xFFFF);
        q.store(JUMP_Y_VELOCITY, (q.yVelocity + ((trig.sine() * speed) >> 8)) & 0xFFFF);
        q.store(FLAGS_AIR, q.flags | 2); q.store(FLAGS_PUSH_CLEAR, q.flags & ~0x20);
        q.store(JUMP_SET, 1); q.store(STICK_CLEAR, 0); publishPlayer(c, player, q);
        fm(c); // FM_A0 BEFORE both miniature tests and every hitbox/roll store.
        player = readPlayer(c); q = new PlayerProjection(player);
        boolean mini = player.environment().movement().gates().miniSonic() != 0;
        q.store(mini ? JUMP_Y_RADIUS_MINI : JUMP_Y_RADIUS_NORMAL, mini ? 10 : 19);
        q.store(mini ? JUMP_X_RADIUS_MINI : JUMP_X_RADIUS_NORMAL, mini ? 5 : 9);
        if ((q.flags & 4) != 0) q.store(FLAGS_ROLL_JUMP, q.flags | 0x10);
        else {
            if (player.environment().movement().gates().miniSonic() != 0) {
                q.store(ROLL_Y_RADIUS_MINI, 10); q.store(ROLL_X_RADIUS_MINI, 5);
            } else {
                q.store(ROLL_Y_RADIUS_NORMAL, 14); q.store(ROLL_X_RADIUS_NORMAL, 7);
                q.store(ROLL_Y_ADVANCE, ((q.y >>> 16) + 5) & 0xFFFF);
            }
            q.store(FLAGS_ROLL, q.flags | 4); q.store(ANIMATION_ROLL, 2);
        }
        publishPlayer(c, player, q);
    }

    /** Actual immutable read under a fresh source64/full96 nonwriting cut, also on throws. */
    private PlayerCut readPlayer(Cursor c) {
        var cut = c.pool.capture(); var source = c.state; requireCurrentCut(c, cut, source);
        try { return Objects.requireNonNull(playerOwner.read(), "actual immutable player pair"); }
        finally { requireCurrentCut(c, cut, source); }
    }
    private void requirePlayer(Cursor c, PlayerCut expected) {
        if (!readPlayer(c).equals(expected)) throw new IllegalStateException("Disc port changed or failed to publish its full actual player pair");
    }
    private void publishPlayer(Cursor c, PlayerCut expected, PlayerProjection projection) {
        var result = projection.result(); var cut = c.pool.capture(); var source = c.state;
        requireCurrentCut(c, cut, source); requirePlayer(c, expected);
        var call = new PlayerCall(cut, source, expected, result);
        // The player port must leave EVERY dynamic image intact, including on callback failure.
        try { playerOwner.commit(call); } finally { requireCurrentCut(c, cut, source); }
        requirePlayer(c, new PlayerCut(result.finalState(), result.finalEnvironment()));
        c.effects.add(new PlayerCommitted(result));
    }
    private void fm(Cursor c) {
        var expected = readPlayer(c); var cut = c.pool.capture(); var source = c.state;
        requireCurrentCut(c, cut, source); requirePlayer(c, expected);
        var call = new FmCall(cut, source, expected, FmSound.FM_A0);
        try { playFm.play(call); }
        finally { requireCurrentCut(c, cut, source); requirePlayer(c, expected); }
        c.effects.add(new FmPlayed(FmSound.FM_A0)); c.returned(NativeCall.PLAY_FM_SOUND);
    }
    private CdSolidObject.Result solid(Cursor c) {
        var player = readPlayer(c); var cut = c.pool.capture(); var source = c.state;
        requireCurrentCut(c, cut, source); requirePlayer(c, player);
        CdSolidObject.Outcome outcome;
        try {
            outcome = sharedSolid.runWithPrefix(CdSolidObject.Entry.SOLID_OBJECT, source, solidPlayer(player),
                    new CdSolidObject.Gates(player.environment().globals().debugMode(), player.environment().movement().gates().miniSonic()),
                    solidGlobals(player), cut);
        } finally { requireCurrentCut(c, cut, source); requirePlayer(c, player); }
        var output = solidImages(outcome); var images = new ArrayList<CdObjectState>();
        images.add(output.object()); images.addAll(output.otherSlots());
        var finalPool = CdObjectPool.overlay(cut, images); // Pure preflight of every same-owner final image.
        var call = new SolidCall(cut, source, player, outcome);
        requireCurrentCut(c, cut, source); requirePlayer(c, player);
        solidCommit.commit(call); // Root stages actual Frame+pool+FM ledger before any live assignment.
        c.state = c.pool.state(c.wholeIndex);
        if (!c.pool.capture().slots().equals(finalPool.slots()) || !c.state.equals(output.object())) {
            throw new IllegalStateException("Solid port did not publish the genuine complete96 output images");
        }
        requireSolidPlayer(player, readPlayer(c), output);
        if (outcome instanceof CdSolidObject.NativeStopped stopped) throw stopped.stop().originalException();
        var result = ((CdSolidObject.Completed) outcome).completedResult();
        if (result.d0().isEmpty()) throw new IllegalStateException("Completed real plain SolidObject lacks d0");
        c.effects.add(new SolidReturned(result)); c.returned(NativeCall.SOLID_OBJECT); return result;
    }
    private record SolidImages(CdSolidObject.Entry entry, CdObjectState object, CdSolidObject.Player player,
                               CdSolidObject.Globals globals, List<CdObjectState> otherSlots) { }
    private static SolidImages solidImages(CdSolidObject.Outcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        if (outcome instanceof CdSolidObject.Completed c) {
            var r = c.completedResult(); return new SolidImages(r.entry(), r.object(), r.player(), r.globals(), r.otherSlots());
        }
        var p = ((CdSolidObject.NativeStopped) outcome).prefix();
        return new SolidImages(p.entry(), p.object(), p.player(), p.globals(), p.otherSlots());
    }
    private static CdSolidObject.Player solidPlayer(PlayerCut cut) {
        var s = cut.state(); var h = s.header();
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(), s.timers().hurt(), s.rotationDistance()),
                h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
    }
    private static CdSolidObject.Globals solidGlobals(PlayerCut cut) {
        var g = cut.environment().movement().globals();
        return new CdSolidObject.Globals(cut.environment().globals().invincible(), g.scoreChain(), g.primaryAngle(), g.secondaryAngle());
    }
    /** Check genuine helper projections plus EVERY untouched full-State/Environment component. No adoption. */
    private static void requireSolidPlayer(PlayerCut before, PlayerCut after, SolidImages output) {
        var s = before.state(); var a = after.state(); var e = before.environment(); var f = after.environment();
        var sh = s.header(); var ah = a.header(); var st = s.timers(); var at = a.timers();
        var m = e.movement(); var n = f.movement(); var g = m.globals(); var h = n.globals();
        var bg = e.globals(); var ag = f.globals();
        boolean retained = s.animation().equals(a.animation()) && s.history().equals(a.history()) && s.rotationAngle() == a.rotationAngle()
                && sh.slotId() == ah.slotId() && sh.mappingAddress() == ah.mappingAddress() && sh.width() == ah.width()
                && st.invincibility() == at.invincibility() && st.speedShoes() == at.speedShoes() && st.reset() == at.reset()
                && e.controls().equals(f.controls()) && e.dynamicSlots() == f.dynamicSlots()
                && m.physics().equals(n.physics()) && m.bounds().equals(n.bounds()) && m.gates().equals(n.gates())
                && g.lookMode() == h.lookMode() && g.cameraYCentre() == h.cameraYCentre() && g.boredTimer() == h.boredTimer()
                && g.debugAngle() == h.debugAngle() && g.debugAngleShift() == h.debugAngleShift()
                && g.debugQuadrant() == h.debugQuadrant() && g.debugFloorDistance() == h.debugFloorDistance()
                && bg.levelFrames() == ag.levelFrames() && bg.cameraXCentre() == ag.cameraXCentre() && bg.timeAttackMode() == ag.timeAttackMode()
                && bg.debugMode() == ag.debugMode() && bg.debugCheat() == ag.debugCheat() && bg.timeWarpTimer() == ag.timeWarpTimer() && bg.bossMusic() == ag.bossMusic();
        if (!retained || !solidPlayer(after).equals(output.player()) || !solidGlobals(after).equals(output.globals())) {
            throw new IllegalStateException("Solid port changed an untouched player field or failed its complete final projection");
        }
    }
    private static void requireCurrentCut(Cursor c, CdObjectPool.Snapshot cut, CdObjectState source) {
        c.pool.validateRestore(cut);
        if (!c.state.equals(source) || !c.pool.state(c.wholeIndex).equals(source) || !c.pool.capture().slots().equals(cut.slots())) {
            throw new IllegalStateException("Disc nonwriting port changed its actual source64 or complete96 pool");
        }
    }
    private static void sourceInCut(CdObjectPool.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut, "actualCut"); Objects.requireNonNull(source, "expectedSource");
        if (!cut.slots().get(source.identity().dynamicIndex()).equals(source)) throw new IllegalArgumentException("Disc source is not in the supplied cut");
    }

    /**
     * Pure old/value/unchanged-field validation for already built immutable aggregates. No pool,
     * Frame, callback, sound, gameplay dispatch, owner assignment or execution capability is used.
     * A repeated same-value result can satisfy this value predicate; Root's private site consumes it once.
     */
    public static void validatePlayerResult(PlayerCut expected, PlayerResult result) {
        Objects.requireNonNull(expected, "expected"); Objects.requireNonNull(result, "result");
        var q = new PlayerProjection(expected);
        for (var store : result.stores()) {
            if (q.raw(store.field()) != store.expectedRaw()) throw new IllegalArgumentException("Stale Disc store value at " + store.field());
            if (store.field() == LOGICAL_CONTROLS) {
                var controls = expected.environment().controls();
                if (store.value() != ((controls.rawHeld() << 8) | controls.rawTap())) throw new IllegalArgumentException("Logical copy must equal actual raw control WORD");
            }
            q.acceptValue(store.field(), store.value());
        }
        if (!q.stateValue().equals(result.finalState()) || !q.environmentValue().equals(result.finalEnvironment())) {
            throw new IllegalArgumentException("Disc result changes an undeclared full State/Environment field or misses its declared values");
        }
    }
    private static void rawValue(DiscPlayerField field, int value) { if (field.widthBits() == 8) byteValue(value); else wordValue(value); }
    private static void fixedOperation(DiscPlayerField field, int old, int value) {
        boolean valid = switch (field) {
            case CONTROL_ACQUIRE -> value == (old | 1);
            case ANIMATION_ACQUIRE -> value == 0x2D;
            case ROTATION_ZERO, CONTROL_CLEAR, STICK_CLEAR -> value == 0;
            case ROTATION_LEFT -> value == 0x80;
            case ROTATION_ADVANCE -> value == ((old + 8) & 255);
            case DISTANCE_CARDINAL, DISTANCE_GROW -> value == ((old + 1) & 255);
            case DISTANCE_SHRINK -> value == ((old - 1) & 255);
            case DISTANCE_CLAMP -> old == 255 && value == 0;
            case FLAGS_AIR -> value == (old | 2);
            case FLAGS_PUSH_CLEAR -> value == (old & ~0x20);
            case JUMP_SET -> value == 1;
            case JUMP_Y_RADIUS_MINI, ROLL_Y_RADIUS_MINI -> value == 10;
            case JUMP_X_RADIUS_MINI, ROLL_X_RADIUS_MINI -> value == 5;
            case JUMP_Y_RADIUS_NORMAL -> value == 19;
            case JUMP_X_RADIUS_NORMAL -> value == 9;
            case ROLL_Y_RADIUS_NORMAL -> value == 14;
            case ROLL_X_RADIUS_NORMAL -> value == 7;
            case ROLL_Y_ADVANCE -> value == ((old + 5) & 0xFFFF);
            case FLAGS_ROLL -> value == (old | 4);
            case ANIMATION_ROLL -> value == 2;
            case FLAGS_ROLL_JUMP -> value == (old | 0x10);
            case DISTANCE_ACQUIRE, ORBIT_X_BASE, ORBIT_X_OFFSET, SCRIPT_INDEX, LOGICAL_CONTROLS, JUMP_X_VELOCITY, JUMP_Y_VELOCITY -> true;
        };
        if (!valid) throw new IllegalArgumentException("Not the fixed native Disc operation at " + field);
    }
    /** Invocation-local scalar projection; it never reads/writes owners or survives the synchronous call. */
    private static final class PlayerProjection {
        final PlayerCut input;
        final List<PlayerStore> stores = new ArrayList<>();
        int x, y, xVelocity, yVelocity, flags, animation, jump, stick, control, xRadius, yRadius, rotation, distance, scriptIndex, logical;
        PlayerProjection(PlayerCut input) {
            this.input = input; var s = input.state(); var m = s.motion(); var c = input.environment().controls();
            x = m.centreX16_16(); y = m.centreY16_16(); xVelocity = m.xVelocity(); yVelocity = m.yVelocity();
            flags = m.flags(); animation = m.animation(); jump = m.playerJump(); stick = m.stick(); control = m.playerControl();
            xRadius = m.xRadius(); yRadius = m.yRadius(); rotation = s.rotationAngle(); distance = s.rotationDistance();
            scriptIndex = s.animation().scriptIndex(); logical = (c.logicalHeld() << 8) | c.logicalTap();
        }
        int raw(DiscPlayerField field) {
            return switch (field) {
                case CONTROL_ACQUIRE, CONTROL_CLEAR -> control;
                case ANIMATION_ACQUIRE, ANIMATION_ROLL -> animation;
                case ROTATION_ZERO, ROTATION_LEFT, ROTATION_ADVANCE -> rotation;
                case DISTANCE_ACQUIRE, DISTANCE_CARDINAL, DISTANCE_GROW, DISTANCE_SHRINK, DISTANCE_CLAMP -> distance;
                case ORBIT_X_BASE, ORBIT_X_OFFSET -> x >>> 16;
                case SCRIPT_INDEX -> scriptIndex;
                case LOGICAL_CONTROLS -> logical;
                case JUMP_X_VELOCITY -> xVelocity & 0xFFFF;
                case JUMP_Y_VELOCITY -> yVelocity & 0xFFFF;
                case FLAGS_AIR, FLAGS_PUSH_CLEAR, FLAGS_ROLL, FLAGS_ROLL_JUMP -> flags;
                case JUMP_SET -> jump;
                case STICK_CLEAR -> stick;
                case JUMP_Y_RADIUS_MINI, JUMP_Y_RADIUS_NORMAL, ROLL_Y_RADIUS_MINI, ROLL_Y_RADIUS_NORMAL -> yRadius;
                case JUMP_X_RADIUS_MINI, JUMP_X_RADIUS_NORMAL, ROLL_X_RADIUS_MINI, ROLL_X_RADIUS_NORMAL -> xRadius;
                case ROLL_Y_ADVANCE -> y >>> 16;
            };
        }
        void store(DiscPlayerField field, int value) {
            stores.add(new PlayerStore(field, raw(field), value)); acceptValue(field, value);
        }
        /** Local scalar assignment for projection/value comparison, never a live metadata replay. */
        void acceptValue(DiscPlayerField field, int value) {
            switch (field) {
                case CONTROL_ACQUIRE, CONTROL_CLEAR -> control = value;
                case ANIMATION_ACQUIRE, ANIMATION_ROLL -> animation = value;
                case ROTATION_ZERO, ROTATION_LEFT, ROTATION_ADVANCE -> rotation = value;
                case DISTANCE_ACQUIRE, DISTANCE_CARDINAL, DISTANCE_GROW, DISTANCE_SHRINK, DISTANCE_CLAMP -> distance = value;
                case ORBIT_X_BASE, ORBIT_X_OFFSET -> x = (value << 16) | (x & 0xFFFF);
                case SCRIPT_INDEX -> scriptIndex = value;
                case LOGICAL_CONTROLS -> logical = value;
                case JUMP_X_VELOCITY -> xVelocity = (short) value;
                case JUMP_Y_VELOCITY -> yVelocity = (short) value;
                case FLAGS_AIR, FLAGS_PUSH_CLEAR, FLAGS_ROLL, FLAGS_ROLL_JUMP -> flags = value;
                case JUMP_SET -> jump = value;
                case STICK_CLEAR -> stick = value;
                case JUMP_Y_RADIUS_MINI, JUMP_Y_RADIUS_NORMAL, ROLL_Y_RADIUS_MINI, ROLL_Y_RADIUS_NORMAL -> yRadius = value;
                case JUMP_X_RADIUS_MINI, JUMP_X_RADIUS_NORMAL, ROLL_X_RADIUS_MINI, ROLL_X_RADIUS_NORMAL -> xRadius = value;
                case ROLL_Y_ADVANCE -> y = (value << 16) | (y & 0xFFFF);
            }
        }
        CdPlayerBody.State stateValue() {
            var s = input.state(); var m = s.motion(); var a = s.animation();
            var motion = new CdPlayerMotionState(x, y, xVelocity, yVelocity, m.groundVelocity(), m.routine(), m.angle(), flags, m.spriteFlags(),
                    xRadius, yRadius, animation, m.previousAnimation(), jump, m.charge(), m.moveLock(), stick, control, m.previousPrimaryAngle(), m.previousSecondaryAngle());
            return new CdPlayerBody.State(motion, new CdPlayerBody.AnimationCursor(scriptIndex, a.timer(), a.mapFrame()), s.header(), s.timers(),
                    s.history(), distance, s.objectLinks(), rotation);
        }
        CdPlayerBody.Environment environmentValue() {
            var e = input.environment(); var c = e.controls();
            return new CdPlayerBody.Environment(e.movement(), new CdPlayerBody.Controls(c.rawHeld(), c.rawTap(), logical >>> 8, logical & 255, c.controlLocked()), e.globals(), e.dynamicSlots());
        }
        PlayerResult result() { return new PlayerResult(stateValue(), environmentValue(), stores); }
    }

    /** Shared animator ONCE on original image. Only a reached-read failure uses the existing prefix adapter. */
    private void animate(Cursor c) {
        var original = c.state;
        var table = new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, assets.visual().animations().size());
        CdObjectAnimation.State result;
        try {
            result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION), original.unsignedByte(PREVIOUS_ANIMATION),
                    original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME), original.unsignedByte(MAP_FRAME), original.spriteFlags(),
                    original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)), table);
        } catch (UnsupportedOperationException reachedRead) {
            animationReadPrefixes(c, table); // Object Animate:20-33,59-76, as Tamabboh/Checkpoint. No second step.
            throw reachedRead;
        }
        c.state = original.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer()).moveByte(MAP_FRAME, result.mapFrame())
                .moveByte(SPRITE_FLAGS, result.spriteFlags()).moveByte(FLAGS, result.statusFlags()).moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2());
        c.publish(); c.returned(NativeCall.ANIMATE_OBJECT);
    }
    /** Reached fault prefix only: no frame interpretation, synthesis, fallback bytes or successful animation. */
    private static void animationReadPrefixes(Cursor c, CdObjectAnimation.AnimationTable table) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION)) {
            c.state = c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
        }
        c.state = c.state.addByte(ANIMATION_TIME, -1); c.publish();
        if (c.state.signedByte(ANIMATION_TIME) >= 0) return;
        int directory = 2 * animation;
        if (animation >= table.scriptCount()) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY, c, directory);
        int script = (short) table.bytes().unsignedWord(directory);
        int speed = animationByte(c, table, UnsupportedBoundary.ANIMATION_SPEED_ADDRESS, script);
        c.state = c.state.moveByte(ANIMATION_TIME, speed); c.publish();
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        if (frame == 0xFF) {
            c.state = c.state.moveByte(ANIMATION_FRAME, 0); c.publish();
            animationByte(c, table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1);
        } else if (frame == 0xFE) {
            int back = animationByte(c, table, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
            index = (index - back) & 255; c.state = c.state.moveByte(ANIMATION_FRAME, index); c.publish();
            animationByte(c, table, UnsupportedBoundary.ANIMATION_FRAME_ADDRESS, script + 1 + index);
        } else if (frame == 0xFD) animationByte(c, table, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
    }
    private static int animationByte(Cursor c, CdObjectAnimation.AnimationTable table, UnsupportedBoundary boundary, int address) {
        if (address < 0 || address >= table.bytes().length()) throw unsupported(boundary, c, address);
        return table.bytes().unsignedByte(address);
    }
    private void draw(Cursor c) {
        try { drawObject.drawObject(c.pool, c.state.identity()); }
        finally { c.state = c.pool.state(c.wholeIndex); }
        c.returned(NativeCall.DRAW_OBJECT);
    }
    /** Native current X is loaded AFTER DrawObject; raw warp/saved bank addressing is not canonicalized early. */
    private void despawn(Cursor c, Globals globals) {
        int x = c.state.centreX();
        boolean outside = c.state.signedByte(SPRITE_FLAGS) >= 0
                && (((x & 0xFF80) - ((globals.preScrollCameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        if (outside || (globals.rawTimeZoneByte() & 0x80) != 0) {
            if (c.state.savedFlagsId() != 0) {
                var at = savedAddress(c, globals); int old = placement.savedFlag(at.id(), at.period());
                placement.bclrSavedFlag(at.id(), at.period(), CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new SavedFlagChanged(at.byteIndex(), old, placement.savedFlag(at.id(), at.period())));
            }
            c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
        }
        c.returned(NativeCall.CHECK_OBJ_DESPAWN);
    }
    private record SavedAddress(int id, int period, int byteIndex) { }
    private static SavedAddress savedAddress(Cursor c, Globals globals) {
        int period = globals.rawTimeZoneByte() & 0x7F;
        if ((globals.rawTimeZoneByte() & 0x80) != 0) {
            int adjusted = (period - (byte) globals.rawDirectionByte()) & 0xFFFF;
            period = (short) adjusted < 0 ? 0 : adjusted >= 3 ? 2 : adjusted;
        }
        int index = 2 + (short) ((3 * c.state.savedFlagsId() + period) & 0xFFFF);
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c, index);
        return new SavedAddress((index - 2) / 3, (index - 2) % 3, index);
    }
    private static UnsupportedSpinningDisc unsupported(UnsupportedBoundary boundary, Cursor c, int value) { return new UnsupportedSpinningDisc(boundary, c, value); }
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        Cursor(CdObjectPool pool, CdObjectState state) { this.pool = pool; this.state = state; wholeIndex = state.identity().wholeIndex(); }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void returned(NativeCall call) { effects.add(new CallReturned(call)); }
        void publish() { pool.write(state); }
    }
    private static void byteValue(int value) { if ((value & ~255) != 0) throw new IllegalArgumentException("Expected unsigned native byte"); }
    private static void wordValue(int value) { if ((value & ~65535) != 0) throw new IllegalArgumentException("Expected unsigned native word"); }
}
