package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * One entitled physical ID $22 visit: normal/damaged body and subtype-1 missile, never ID $23.
 * Native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Objects/Tamabboh/Main.asm:7-282; Object Time Check.asm:12-44;
 * Object Animate.asm:14-95; Collision Check.asm:157-179; Collision Floor.asm:685-856;
 * Object Spawner.asm:329-446; Object Functions.asm:137-196,228-237.
 * These branches have no FixBugs alternative. Java/API base is
 * 62607759799bf53906fffdcb3f2896038d126418. This external source packet is ALL UNRUN.
 *
 * <p>Actual slot prefixes precede synchronous angle/player/FM ports. Every such call borrows
 * a fresh actual same-owner96-slot cut; full-pool and source nonwrites are checked before and
 * after, including exceptional callback exits, without adopting or rolling back changed images.
 * Records are plain values, not execution tokens. Root owns the authentic bound body invocation,
 * phase/current physical dispatch, full96/source validation, actual Frame read/CAS and reentry
 * exclusion, exactly-once angle stores and ordered typed DEV FM metadata. FM_A0 here neither
 * models the native three-byte sound queue nor claims playback. No completed effect retains
 * a cut, callback command or instructions to replay.
 *
 * <p>The terrain and actual player Frame root MUST remain stable for this entire invocation.
 * The player port reads Y first and X only when reached. Root enforces that stable-Frame
 * precondition; this body cannot prove it from plain returned words. With those immutable inputs,
 * repetition of a full Main image/local angle is an explicit HOST non-return support boundary,
 * not a native return, arbitrary loop budget or completed Draw/despawn. The exception reports
 * the actual full image, local angle and reached scalar player-read context.
 *
 * <p>No Frame import, new global/timer/RNG owner, object scan, child redispatch, art source,
 * graphics/picture policy, runtime or world join. The existing C29/$440 boundary is unchanged.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdTamabboh {
    public static final int OBJECT_ID = 0x22;
    public static final int TILE_SELECTOR = 4;

    public enum NativeRoutine { INIT, POSITION, MAIN, WAIT1, WAIT2, FIRE, MISSILE_INIT, MISSILE_MAIN }
    public enum NativeCall {
        DESTROY_ON_GOOD_FUTURE, SET_OBJECT_TILE_ID, OBJ_GET_FLOOR_DIST, ANIMATE_OBJECT,
        FIND_OBJ_SLOT, PLAY_FM_SOUND, DRAW_OBJECT, CHECK_OBJ_DESPAWN
    }
    public enum PlayerField { Y_WORD, X_WORD }
    public enum FmSound { FM_A0 }
    public sealed interface Effect permits RoutineEntered, CallReturned, FloorReturned, PrimaryAngleCommitted,
            PlayerWordReadReturned, FmPlayed, MissileCreated, SavedFlagChanged, Deleted, ConvertedToExplosion { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record FloorReturned(CdObjectCollisionCheck.FloorDistance result) implements Effect {
        public FloorReturned { Objects.requireNonNull(result, "result"); }
    }
    /** Completed scalar observation, never a retained call/cut or another global owner. */
    public record PrimaryAngleCommitted(int expectedPrimaryAngle, int value) implements Effect {
        public PrimaryAngleCommitted { byteValue(expectedPrimaryAngle); byteValue(value); }
    }
    public record PlayerWordReadReturned(PlayerField field, int value) implements Effect {
        public PlayerWordReadReturned { Objects.requireNonNull(field, "field"); wordValue(value); }
    }
    public record FmPlayed(int firstFreeWholeIndex, FmSound sound) implements Effect {
        public FmPlayed { new CdObjectState.Identity(firstFreeWholeIndex); Objects.requireNonNull(sound, "sound"); }
    }
    public record MissileCreated(int wholeIndex, int direction) implements Effect {
        public MissileCreated { new CdObjectState.Identity(wholeIndex); byteValue(direction); }
    }
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record Deleted() implements Effect { }
    public record ConvertedToExplosion() implements Effect { }

    /**
     * Finite format inputs, not authentication. Both body views retain the SAME full62 include,
     * rooted at0/22; missile retains42. Construction grants no media/Frame/world authority.
     * Production uses fromObjectAssets over Root's authenticated retained source owner.
     */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets normal, CdObjectVisualAssets damaged,
                         CdObjectVisualAssets missile, int normalMappingsAddress, int damagedMappingsAddress,
                         int missileMappingsAddress, int normalAnimationAddress, int damagedAnimationAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds"); Objects.requireNonNull(normal, "normal");
            Objects.requireNonNull(damaged, "damaged"); Objects.requireNonNull(missile, "missile");
            if (objectTileIds.length() != 74
                    || !format(normal, 62, 0, 3, 12, 2) || !format(damaged, 62, 22, 3, 26, 2)
                    || !format(missile, 42, 0, 4, 8, 1)
                    || !Arrays.equals(normal.mappings().copy(), damaged.mappings().copy())
                    || damagedMappingsAddress != normalMappingsAddress + 22
                    || normalAnimationAddress == damagedAnimationAddress) {
                throw new IllegalArgumentException("Tamabboh requires full62 body roots0/22, missile42 and scripts12/26/8");
            }
            for (var visual : List.of(normal, damaged, missile)) {
                for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                    if (piece.tileOffset() > 59 - piece.tileCount()) {
                        throw new IllegalArgumentException("Tamabboh piece exceeds its existing59-tile source");
                    }
                }
            }
        }
        private static boolean format(CdObjectVisualAssets view, int mappingBytes, int tableOffset, int frames, int scriptBytes, int scripts) {
            return view.mappings().length() == mappingBytes && view.mappingTableOffset() == tableOffset
                    && view.mappingFrames().size() == frames && view.animationTable().length() == scriptBytes
                    && view.animations().size() == scripts;
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            int bodyRoot = CdR11aObjectAssets.Table.TAMABBOH_MAPPINGS.range().nativeAddress();
            return new Assets(owner.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(), owner.tamabbohNormal(),
                    owner.tamabbohDamaged(), owner.tamabbohMissile(), bodyRoot, bodyRoot + 22,
                    CdR11aObjectAssets.Table.TAMABBOH_MISSILE_MAPPINGS.range().nativeAddress(),
                    CdR11aObjectAssets.Table.TAMABBOH_NORMAL_ANIMATIONS.range().nativeAddress(),
                    CdR11aObjectAssets.Table.TAMABBOH_DAMAGED_ANIMATIONS.range().nativeAddress());
        }
    }

    /** Raw bytes except debug/camera WORDs. Never normalize timeZone before native saved addressing. */
    public record Globals(int goodFuture, int timeZone, int timeWarpDirection, int debugMode, int cameraXWord, int primaryAngle) {
        public Globals {
            byteValue(goodFuture); byteValue(timeZone); byteValue(timeWarpDirection);
            wordValue(debugMode); wordValue(cameraXWord); byteValue(primaryAngle);
        }
    }
    /** Plain call values; even a same-owner equal/derived snapshot cannot prove an actual invocation. */
    public record PlayerReadCall(CdObjectPool.Snapshot callCut, CdObjectState expectedSource, PlayerField field) {
        public PlayerReadCall { sourceInCut(callCut, expectedSource); Objects.requireNonNull(field, "field"); }
    }
    @FunctionalInterface public interface PlayerWordRead { int read(PlayerReadCall call); }
    public record PrimaryAngleCall(CdObjectPool.Snapshot callCut, CdObjectState expectedSource, int expectedPrimaryAngle, int value) {
        public PrimaryAngleCall { sourceInCut(callCut, expectedSource); byteValue(expectedPrimaryAngle); byteValue(value); }
    }
    @FunctionalInterface public interface PrimaryAngleCommit { void commit(PrimaryAngleCall call); }
    public record FmCall(CdObjectPool.Snapshot callCut, CdObjectState expectedSource, int firstFreeWholeIndex, FmSound sound) {
        public FmCall {
            sourceInCut(callCut, expectedSource); new CdObjectState.Identity(firstFreeWholeIndex); Objects.requireNonNull(sound, "sound");
            int first = callCut.slots().stream().filter(s -> s.id() == 0).mapToInt(s -> s.identity().wholeIndex()).findFirst().orElse(-1);
            if (first != firstFreeWholeIndex) throw new IllegalArgumentException("FM cut must describe the actual first-free candidate");
        }
    }
    @FunctionalInterface public interface PlayFmSound { void play(FmCall call); }
    /** Existing mutating draw owner; unlike the three ports above this may clear sprite bit7. */
    @FunctionalInterface public interface DrawObjectCall { void drawObject(CdObjectPool pool, CdObjectState.Identity identity); }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, TILE_ENTRY, ANIMATION_POINTER, ANIMATION_DIRECTORY_ENTRY, ANIMATION_SPEED_ADDRESS,
        ANIMATION_FRAME_ADDRESS, ANIMATION_OPERAND_ADDRESS, SAVED_FLAGS_ADDRESS, NATIVE_NON_RETURNING_CYCLE
    }
    public static final class UnsupportedTamabboh extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedTamabboh(UnsupportedBoundary boundary, Cursor c, int value) {
            super((boundary == UnsupportedBoundary.NATIVE_NON_RETURNING_CYCLE
                    ? "HOST non-return support stop; image=" + HexFormat.of().formatHex(c.state.copyBytes())
                        + "; primaryAngle=" + c.primaryAngle + "; reachedPlayerReads="
                        + c.effects.stream().filter(PlayerWordReadReturned.class::isInstance).toList() + "; " : "")
                    + boundary + " at Tamabboh whole slot " + c.wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = c.wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdPlayerTerrain terrain;
    private final PrimaryAngleCommit angleCommit;
    private final PlayerWordRead playerRead;
    private final PlayFmSound playFmSound;
    private final DrawObjectCall drawObject;
    private boolean running;

    public CdTamabboh(CdObjectPlacementRuntime placement, Assets assets, CdPlayerTerrain terrain,
                     PrimaryAngleCommit angleCommit, PlayerWordRead playerRead, PlayFmSound playFmSound, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement, "placement"); this.assets = Objects.requireNonNull(assets, "assets");
        this.terrain = Objects.requireNonNull(terrain, "terrain"); this.angleCommit = Objects.requireNonNull(angleCommit, "angleCommit");
        this.playerRead = Objects.requireNonNull(playerRead, "playerRead"); this.playFmSound = Objects.requireNonNull(playFmSound, "playFmSound");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /** ObjTamabboh:7-18 / ObjTamabbohMissile:213-224: subtype equality1 is FIRST. */
    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Tamabboh cannot reenter its synchronous physical dispatch");
        running = true;
        try {
            Objects.requireNonNull(globals, "globals"); var pool = placement.pool(); var state = pool.state(wholeIndex);
            if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Selected slot is not Tamabboh ID $22");
            var c = new Cursor(pool, state, globals.primaryAngle());
            if (state.subtype() == 1) {
                switch (state.routine()) {
                    case 0 -> missileInit(c);
                    case 2 -> missileMain(c);
                    default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, state.routine());
                }
                draw(c); // Including the actual cleared ID0 image after missile Main DeleteObject.
                return c.finish();
            }
            if (destroyOnGoodFuture(c, globals)) return c.finish();
            switch (c.state.routine()) {
                case 0 -> { init(c); position(c); }
                case 2 -> position(c);
                case 4 -> main(c, globals);
                case 6 -> {
                    c.enter(NativeRoutine.WAIT1);
                    c.state = c.state.addByte(ROUTINE, 2).moveByte(ANIMATION, 1); c.publish();
                    wait2(c); // Actual source fallthrough, not another dispatch.
                }
                case 8 -> wait2(c);
                case 10 -> fire(c);
                default -> throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, c.state.routine());
            }
            draw(c); despawn(c, globals); return c.finish();
        } finally { running = false; }
    }

    /** Object Time Check.asm:12-44: Delete64, restore only XY WORDs, ID18, skip body wrapper. */
    private static boolean destroyOnGoodFuture(Cursor c, Globals globals) {
        if (globals.goodFuture() == 0 || (globals.timeZone() == 1 && c.state.subtype() == 0)) {
            c.returned(NativeCall.DESTROY_ON_GOOD_FUTURE); return false;
        }
        int x = c.state.centreX(), y = c.state.centreY(); delete(c);
        c.state = c.state.moveWord(X, x).moveWord(Y, y).moveByte(ID, 0x18); c.publish();
        // Cleared flags make the subsequent native sound-sign test nonnegative; no FM_EXPLODE.
        c.effects.add(new ConvertedToExplosion()); return true;
    }

    /** ObjTamabboh_Init:29-58: publish ALL native stores preceding selector4's reached read. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(PRIORITY, 4).moveByte(COLLISION_TYPE, 0x2C).moveByte(X_RADIUS, 16)
                .moveByte(WIDTH, 16).moveByte(Y_RADIUS, 15).moveWord(TAMABBOH_ANCHOR_X, c.state.centreX()); c.publish();
        int secondary = c.state.subtype2();
        if (secondary != 0) throw unsupported(UnsupportedBoundary.TILE_ENTRY, c, secondary);
        int tile = CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), TILE_SELECTOR, secondary);
        c.state = c.state.moveWord(TILE, tile); c.publish(); c.returned(NativeCall.SET_OBJECT_TILE_ID);
        boolean damaged = c.state.subtype() != 0;
        c.state = c.state.moveLong(MAPPINGS, damaged ? assets.damagedMappingsAddress() : assets.normalMappingsAddress())
                .moveLong(TAMABBOH_ANIMATION_SCRIPT, damaged ? assets.damagedAnimationAddress() : assets.normalAnimationAddress())
                .moveLong(TAMABBOH_X_DELTA, damaged ? -0x5000 : -0xA000); c.publish();
    }
    /** ObjTamabboh_Position:62-72: whole-long Y+1; no Y correction, animation or Main. */
    private void position(Cursor c) {
        c.enter(NativeRoutine.POSITION); c.state = c.state.addLong(Y_POSITION, 0x10000); c.publish();
        if (floor(c).distance() < 0) { c.state = c.state.addByte(ROUTINE, 2); c.publish(); }
    }
    /** ObjTamabboh_Main:76-126: every reversal re-enters the entire Main range/cooldown path. */
    private void main(Cursor c, Globals globals) {
        Set<MainCut> seen = new HashSet<>();
        while (true) {
            if (!seen.add(new MainCut(c.state, c.primaryAngle))) {
                throw unsupported(UnsupportedBoundary.NATIVE_NON_RETURNING_CYCLE, c, c.state.routine());
            }
            c.enter(NativeRoutine.MAIN);
            if (globals.debugMode() == 0 && c.state.subtype() == 0) {
                if (c.state.word(TAMABBOH_COOLDOWN) != 0) {
                    c.state = c.state.addWord(TAMABBOH_COOLDOWN, -1); c.publish();
                } else if (inRange(c)) {
                    c.state = c.state.addByte(ROUTINE, 2); c.publish(); return;
                }
            }
            c.state = c.state.addLong(X_POSITION, c.state.longBits(TAMABBOH_X_DELTA)); c.publish();
            int distance = (short) (c.state.centreX() - c.state.word(TAMABBOH_ANCHOR_X));
            if (distance < 0) distance = (short) -distance; // Native NEG.W $8000 stays negative.
            if (distance >= 0x80) { turn(c); continue; }
            var result = floor(c);
            if (result.distance() < -7 || result.distance() > 7) { turn(c); continue; }
            c.state = c.state.addWord(Y, result.distance()); c.publish();
            animate(c, false); return;
        }
    }
    private record MainCut(CdObjectState state, int primaryAngle) { }
    /** ObjTamabboh_CheckInRange:132-144: carry after WORD-wrapped delta+50 minusA0. */
    private boolean inRange(Cursor c) {
        int y = playerWord(c, PlayerField.Y_WORD);
        if (((y - c.state.centreY() + 0x50) & 0xFFFF) >= 0xA0) return false;
        int x = playerWord(c, PlayerField.X_WORD);
        return ((x - c.state.centreX() + 0x50) & 0xFFFF) < 0xA0;
    }
    private static void turn(Cursor c) {
        c.state = c.state.moveLong(TAMABBOH_X_DELTA, -c.state.longBits(TAMABBOH_X_DELTA))
                .moveByte(SPRITE_FLAGS, c.state.spriteFlags() ^ 1).moveByte(FLAGS, c.state.flags() ^ 1); c.publish();
    }
    private void wait2(Cursor c) { c.enter(NativeRoutine.WAIT2); animate(c, false); }

    /** ObjTamabboh_Fire:162-185: parent prefix, first Find, conditional FM, first child, fresh Find. */
    private void fire(Cursor c) {
        c.enter(NativeRoutine.FIRE);
        c.state = c.state.moveByte(ROUTINE, 4).moveByte(ANIMATION, 0).moveWord(TAMABBOH_COOLDOWN, 0x78); c.publish();
        if (c.state.subtype() != 0) return;
        var first = c.pool.findObjSlot(); c.returned(NativeCall.FIND_OBJ_SLOT);
        if (first.isEmpty()) return;
        if (c.state.signedByte(SPRITE_FLAGS) < 0) fm(c, first.getAsInt()); // BEFORE ANY first-child write.
        initMissile(c, first.getAsInt(), 0);
        var second = c.pool.findObjSlot(); c.returned(NativeCall.FIND_OBJ_SLOT); // Always restart at32; no reservation.
        if (second.isPresent()) initMissile(c, second.getAsInt(), 0xFF);
    }
    /** ObjTamabboh_InitMissile:189-199, then caller's Var3F: retain EVERY other dirty byte. */
    private static void initMissile(Cursor c, int wholeIndex, int direction) {
        var child = c.pool.state(wholeIndex);
        child = child.moveByte(ID, c.state.id()).moveByte(SUBTYPE, 1).moveWord(TILE, c.state.word(TILE))
                .moveByte(PRIORITY, c.state.unsignedByte(PRIORITY)).addByte(PRIORITY, 1)
                .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY()).addWord(Y, -10);
        c.pool.write(child); // ID is live before the next Find; neither clear nor reserved ownership.
        c.pool.write(c.pool.state(wholeIndex).moveByte(TAMABBOH_MISSILE_DIRECTION, direction));
        c.effects.add(new MissileCreated(wholeIndex, direction));
    }
    /** ObjTamabbohMissile_Init:226-252: no fallthrough, floor, motion or animation. */
    private void missileInit(Cursor c) {
        c.enter(NativeRoutine.MISSILE_INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(COLLISION_TYPE, 0xAD).moveByte(X_RADIUS, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8)
                .moveLong(MAPPINGS, assets.missileMappingsAddress()).moveLong(TAMABBOH_MISSILE_X_ACCEL, 0)
                .moveLong(TAMABBOH_MISSILE_Y_ACCEL, 0x2000); c.publish();
        int dx = c.state.unsignedByte(TAMABBOH_MISSILE_DIRECTION) == 0 ? 0x20000 : -0x20000;
        c.state = c.state.moveLong(TAMABBOH_MISSILE_X_DELTA, dx).moveLong(TAMABBOH_MISSILE_Y_DELTA, -0x40000); c.publish();
    }
    /** ObjTamabbohMissile_Main:256-282: old visibility/floor delete first, otherwise four LONG ops. */
    private void missileMain(Cursor c) {
        c.enter(NativeRoutine.MISSILE_MAIN);
        if (c.state.signedByte(SPRITE_FLAGS) >= 0) { delete(c); return; }
        if (floor(c).distance() < 0) { delete(c); return; }
        c.state = c.state.addLong(X_POSITION, c.state.longBits(TAMABBOH_MISSILE_X_DELTA))
                .addLong(Y_POSITION, c.state.longBits(TAMABBOH_MISSILE_Y_DELTA)); c.publish();
        c.state = c.state.addLong(TAMABBOH_MISSILE_X_DELTA, c.state.longBits(TAMABBOH_MISSILE_X_ACCEL))
                .addLong(TAMABBOH_MISSILE_Y_DELTA, c.state.longBits(TAMABBOH_MISSILE_Y_ACCEL)); c.publish();
        animate(c, true);
    }

    /** Shared helper3 forwards every reached native angle store, including pre-zero before terrain. */
    private CdObjectCollisionCheck.FloorDistance floor(Cursor c) {
        var result = CdObjectCollisionCheck.objGetFloorDist(terrain, c.state, value -> angle(c, value));
        c.effects.add(new FloorReturned(result)); c.returned(NativeCall.OBJ_GET_FLOOR_DIST); return result;
    }
    private void angle(Cursor c, int value) {
        var cut = c.pool.capture(); var call = new PrimaryAngleCall(cut, c.state, c.primaryAngle, value);
        requireCurrentCut(c, cut, call.expectedSource());
        try { angleCommit.commit(call); } finally { requireCurrentCut(c, cut, call.expectedSource()); }
        c.effects.add(new PrimaryAngleCommitted(c.primaryAngle, value)); c.primaryAngle = value;
    }
    private int playerWord(Cursor c, PlayerField field) {
        var cut = c.pool.capture(); var call = new PlayerReadCall(cut, c.state, field);
        requireCurrentCut(c, cut, call.expectedSource()); int value;
        try { value = playerRead.read(call); } finally { requireCurrentCut(c, cut, call.expectedSource()); }
        wordValue(value); c.effects.add(new PlayerWordReadReturned(field, value)); return value;
    }
    private void fm(Cursor c, int firstFreeWholeIndex) {
        var cut = c.pool.capture(); var call = new FmCall(cut, c.state, firstFreeWholeIndex, FmSound.FM_A0);
        requireCurrentCut(c, cut, call.expectedSource());
        try { playFmSound.play(call); } finally { requireCurrentCut(c, cut, call.expectedSource()); }
        c.effects.add(new FmPlayed(firstFreeWholeIndex, FmSound.FM_A0)); c.returned(NativeCall.PLAY_FM_SOUND);
    }
    private static void requireCurrentCut(Cursor c, CdObjectPool.Snapshot cut, CdObjectState expectedSource) {
        c.pool.validateRestore(cut);
        if (!c.state.equals(expectedSource) || !c.pool.state(c.wholeIndex).equals(expectedSource)
                || !c.pool.capture().slots().equals(cut.slots())) {
            throw new IllegalStateException("Tamabboh read-only port changed its actual full96 pool or source cut");
        }
    }
    private static void sourceInCut(CdObjectPool.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut, "callCut"); Objects.requireNonNull(source, "expectedSource");
        if (!cut.slots().get(source.identity().dynamicIndex()).equals(source)) {
            throw new IllegalArgumentException("Tamabboh source is not the supplied cut's actual slot image");
        }
    }

    /** Shared step owns successful animation; original call image is supplied once, without double SUBQ. */
    private void animate(Cursor c, boolean missile) {
        var original = c.state; var table = animationReadPrefixes(c, missile);
        var result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME),
                original.unsignedByte(MAP_FRAME), original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)), table);
        c.state = original.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer()).moveByte(MAP_FRAME, result.mapFrame())
                .moveByte(SPRITE_FLAGS, result.spriteFlags()).moveByte(FLAGS, result.statusFlags())
                .moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2());
        c.publish(); c.returned(NativeCall.ANIMATE_OBJECT);
    }
    /** Object Animate:20-33,59-76: publish only reached reset/SUBQ/speed/FF/FE failure prefixes. */
    private CdObjectAnimation.AnimationTable animationReadPrefixes(Cursor c, boolean missile) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION)) {
            c.state = c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0);
        }
        c.state = c.state.addByte(ANIMATION_TIME, -1); c.publish();
        if (c.state.signedByte(ANIMATION_TIME) >= 0) {
            // Shared step returns before ANY pointer-directory read. This required, unconsumed
            // argument neither resolves nor substitutes a live foreign Var30 pointer; that pointer
            // is checked only on the next actually reached native directory read below.
            var unread = missile ? assets.missile() : assets.normal();
            return new CdObjectAnimation.AnimationTable(unread.animationTable(), 0, unread.animations().size());
        }
        var visual = assets.missile();
        if (!missile) {
            int pointer = c.state.longBits(TAMABBOH_ANIMATION_SCRIPT);
            if (pointer == assets.normalAnimationAddress()) visual = assets.normal();
            else if (pointer == assets.damagedAnimationAddress()) visual = assets.damaged();
            else throw unsupported(UnsupportedBoundary.ANIMATION_POINTER, c, pointer); // FULL32, never masked/reselected by subtype.
        }
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        if (animation >= table.scriptCount()) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_ENTRY, c, 2 * animation);
        int script = (short) table.bytes().unsignedWord(2 * animation);
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
        } else if (frame == 0xFD) {
            animationByte(c, table, UnsupportedBoundary.ANIMATION_OPERAND_ADDRESS, script + 2 + index);
        }
        // FC increments routine in the shared owner; its real trailing00 is NOT an operand.
        return table;
    }
    private static int animationByte(Cursor c, CdObjectAnimation.AnimationTable table, UnsupportedBoundary boundary, int address) {
        if (address < 0 || address >= table.bytes().length()) throw unsupported(boundary, c, address);
        return table.bytes().unsignedByte(address);
    }

    /** Existing DrawObject is mutating. Reload live source before the wrapper's anchor/saved tail. */
    private void draw(Cursor c) {
        try { drawObject.drawObject(c.pool, c.state.identity()); }
        finally { c.state = c.pool.state(c.wholeIndex); }
        c.returned(NativeCall.DRAW_OBJECT);
    }
    /** CheckObjDespawn2:388-399,442-446 / DespawnObject:403-438; raw anchor loaded AFTER draw. */
    private void despawn(Cursor c, Globals globals) {
        int anchor = c.state.word(TAMABBOH_ANCHOR_X);
        boolean outside = c.state.signedByte(SPRITE_FLAGS) >= 0
                && (((anchor & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        if (outside || (globals.timeZone() & 0x80) != 0) {
            if (c.state.savedFlagsId() != 0) {
                var address = savedAddress(c, globals); int old = placement.savedFlag(address.id(), address.period());
                placement.bclrSavedFlag(address.id(), address.period(), CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new SavedFlagChanged(address.byteIndex(), old, placement.savedFlag(address.id(), address.period())));
            }
            delete(c);
        }
        c.returned(NativeCall.CHECK_OBJ_DESPAWN);
    }
    private record SavedAddress(int id, int period, int byteIndex) { }
    /** Native linear aliases and actual bounds FIRST; canonical API arguments only after that. */
    private static SavedAddress savedAddress(Cursor c, Globals globals) {
        int period = globals.timeZone() & 0x7F;
        if ((globals.timeZone() & 0x80) != 0) {
            int adjusted = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
            period = (short) adjusted < 0 ? 0 : adjusted >= 3 ? 2 : adjusted;
        }
        int index = 2 + (short) ((3 * c.state.savedFlagsId() + period) & 0xFFFF);
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c, index);
        return new SavedAddress((index - 2) / 3, (index - 2) % 3, index);
    }
    private static void delete(Cursor c) {
        c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
    }
    private static UnsupportedTamabboh unsupported(UnsupportedBoundary boundary, Cursor c, int value) {
        return new UnsupportedTamabboh(boundary, c, value);
    }
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        int primaryAngle;
        Cursor(CdObjectPool pool, CdObjectState state, int primaryAngle) {
            this.pool = pool; this.state = state; this.primaryAngle = primaryAngle; wholeIndex = state.identity().wholeIndex();
        }
        void publish() { pool.write(state); }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void returned(NativeCall call) { effects.add(new CallReturned(call)); }
        Result finish() { return new Result(state, effects); }
    }
    private static void byteValue(int value) { if ((value & ~255) != 0) throw new IllegalArgumentException("Expected unsigned native byte"); }
    private static void wordValue(int value) { if ((value & ~65535) != 0) throw new IllegalArgumentException("Expected unsigned native word"); }
}
