package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11aPlayerAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Native ID $3E body only, over one placement owner's actual dynamic pool and saved bank.
 * Source N: scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * src/Level/Palmtree Panic/Objects/Pata-Bata/Main.asm:7-80. Helper API baseline J:
 * 05c5c47c6d5c61a49109396d31d286f9147bf3ed. No source FixBugs alternative occurs in this
 * body or the cited CalcSine/time/despawn helpers; shipped FixBugs=0 behavior is retained.
 *
 * <p>Each run is one entitled RunObjects dispatch, not a frame or scheduling owner. DestroyOnGoodFuture
 * runs first. Routine zero initializes and falls through; EVERY nonzero byte enters Main. Writes
 * reach the real pool in source order. A failed bounded read/callback leaves its completed prefix
 * committed, and the caller must close the surrounding dispatch. No retained capture, private
 * pool/bank, physics table, mapper, drawing queue, sound owner, player mutation or global write exists here.
 * Supplied constructed Assets are format inputs only and authenticate no release or media.
 */
public final class CdPataBata {
    public static final int OBJECT_ID = 0x3E;
    /** Pata-Bata/Main.asm:14, immediate native touch classification; not a generic badnik response. */
    public static final int NATIVE_COLLISION_TYPE = 0x2A;

    public enum NativeCall { DESTROY_ON_GOOD_FUTURE, ANIMATE_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN2 }
    public enum DespawnEntry { ANCHOR_OFFSCREEN, TIME_WARP }
    public enum UnsupportedBoundary { SET_OBJECT_TILE_ID, ANIMATE_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN2 }

    /** All effects describe COMPLETED operations. Consumers must never replay conversion/bank/deletion writes. */
    public sealed interface Effect permits CallReturned, ExplosionConversion, Despawn { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /**
     * DeleteObject already cleared all 64 bytes, X/Y integer words were restored and ID became $18.
     * The native ADDQ.L #4,SP skipped the remainder of this body. No allocation or Explosion dispatch
     * occurred, and no saved-bank bit was cleared. The post-delete sprite flags suppress native FM.
     */
    public record ExplosionConversion(int xWord, int yWord) implements Effect {
        public ExplosionConversion { wordValue(xWord, "conversion X"); wordValue(yWord, "conversion Y"); }
    }
    /** Bank loaded bit already cleared, then all 64 slot bytes deleted; period -1 means no saved ID. */
    public record Despawn(DespawnEntry entry, int savedId, int period) implements Effect {
        public Despawn {
            Objects.requireNonNull(entry, "entry");
            byteValue(savedId, "saved ID");
            if ((savedId == 0 && period != -1) || (savedId != 0 && (period < 0 || period > 2))) {
                throw new IllegalArgumentException("Despawn period is -1 for saved ID zero, otherwise 0..2");
            }
        }
    }

    /**
     * Actual native global reads at this dispatch's call cut, only: Object Time Check.asm:13-15;
     * Object Spawner.asm:393,414,418,443. timeZone is RAW: no upfront period admission or bit-7 masking.
     * cameraX is pre-scroll. No debug/time-stop/proximity/player/zone gate is invented.
     */
    public record Globals(int goodFuture, int timeZone, int timeWarpDir, int cameraXWord) {
        public Globals {
            byteValue(goodFuture, "goodFuture");
            byteValue(timeZone, "timeZone");
            byteValue(timeWarpDir, "timeWarpDir");
            wordValue(cameraXWord, "camera X");
        }
    }

    /**
     * Bound original ObjectTileIDs extent, both actual visual views, the ONE shared Ani_PataBata
     * table, both native mapping pointers and the existing player math owner. The generic constructor
     * supplies no source certificate, fallback data, inferred pointer or generated sine curve.
     * Bounded malformed format inputs fail only when a native read actually reaches them.
     */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets normal, CdObjectVisualAssets damaged,
                         CdObjectAnimation.AnimationTable animations,
                         int normalMappingsAddress, int damagedMappingsAddress, CdPlayerMath math) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds");
            Objects.requireNonNull(normal, "normal");
            Objects.requireNonNull(damaged, "damaged");
            Objects.requireNonNull(animations, "animations");
            Objects.requireNonNull(math, "math");
        }

        /**
         * Production binding: Root supplies these object/player owners from the same authenticated
         * R11A load. Visuals and raw views come from this SAME actual object owner; CalcSine reads
         * this actual player owner's ROM-pipeline bytes through the existing CdPlayerMath.
         * The three PATA_BATA table names/accessors are the asset-owner integration prerequisite.
         */
        public static Assets fromObjectAssets(CdR11aObjectAssets objects, CdR11aPlayerAssets playerAssets) {
            Objects.requireNonNull(objects, "objects");
            Objects.requireNonNull(playerAssets, "playerAssets");
            return new Assets(objects.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(),
                    objects.pataBataNormal(), objects.pataBataDamaged(),
                    new CdObjectAnimation.AnimationTable(
                            objects.view(CdR11aObjectAssets.Table.PATA_BATA_ANIMATIONS).bytes(), 0, 2),
                    objects.view(CdR11aObjectAssets.Table.PATA_BATA_NORMAL_MAPPINGS).range().nativeAddress(),
                    objects.view(CdR11aObjectAssets.Table.PATA_BATA_DAMAGED_MAPPINGS).range().nativeAddress(),
                    new CdPlayerMath(playerAssets));
        }
    }

    /**
     * Mandatory synchronous native DrawObject port, after AnimateObject and before anchor despawn.
     * Root binds its existing drawDynamic over this exact live pool/physical identity. The port owns
     * queue admission and entry bit-7 clear; the body reloads the live slot afterwards. It must not
     * reenter object dispatch. A callback may fail after writes; those writes are never rolled back.
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
        public boolean deleted() { return effects.stream().anyMatch(Despawn.class::isInstance); }
        public boolean converted() { return effects.stream().anyMatch(ExplosionConversion.class::isInstance); }
    }

    /** Native ownership stays explicit; a closed boundary carries the original cause and completed calls. */
    public static final class UnsupportedPataBata extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final List<Effect> completedEffects;

        private UnsupportedPataBata(UnsupportedBoundary boundary, int wholeIndex,
                                   List<Effect> completedEffects, RuntimeException cause) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + cause.getMessage(), cause);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
            this.completedEffects = List.copyOf(completedEffects);
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public List<Effect> completedEffects() { return completedEffects; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final DrawObjectCall drawObject;

    public CdPataBata(CdObjectPlacementRuntime runtime, Assets assets, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    public Assets assets() { return assets; }

    /** One body dispatch. No routine-table gate exists in the native TST/BNE entry. */
    public Result run(int wholeIndex, Globals globals) {
        Objects.requireNonNull(globals, "globals");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjPataBata $3E");
        }
        var effects = new ArrayList<Effect>();
        // DestroyOnGoodFuture (Object Time Check.asm:12-44) is the FIRST native call.
        if (globals.goodFuture() != 0 && (globals.timeZone() != 1 || state.subtype() != 0)) {
            int x = state.centreX();
            int y = state.centreY();
            pool.deleteObject(wholeIndex);                                // DeleteObject: all $40 bytes.
            pool.write(pool.state(wholeIndex).moveWord(X, x));            // Restore only integer words.
            pool.write(pool.state(wholeIndex).moveWord(Y, y));
            pool.write(pool.state(wholeIndex).moveByte(ID, CdExplosion.OBJECT_ID));
            // TST.B oSprFlags reads the CLEARED byte, so BPL skips PlayFMSound even if entry bit 7 was set.
            effects.add(new ExplosionConversion(x, y));
            return new Result(pool.state(wholeIndex), effects);           // ADDQ.L #4,SP skips the caller body.
        }
        effects.add(new CallReturned(NativeCall.DESTROY_ON_GOOD_FUTURE));

        if (state.routine() == 0) { init(wholeIndex, effects); }            // Native Init FALLS THROUGH.
        mainMotion(wholeIndex);
        try {
            animate(wholeIndex);
        } catch (RuntimeException cause) {
            throw boundary(UnsupportedBoundary.ANIMATE_OBJECT, wholeIndex, effects, cause);
        }
        effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
        try {
            drawObject.drawObject(pool, pool.state(wholeIndex).identity());
        } catch (RuntimeException cause) {
            throw boundary(UnsupportedBoundary.DRAW_OBJECT, wholeIndex, effects, cause);
        }
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        try {
            // Pata-Bata/Main.asm:79 reads oVar2A AFTER DrawObject; never reuse the earlier state.
            checkObjDespawn2(pool.state(wholeIndex), globals, effects);
        } catch (RuntimeException cause) {
            throw boundary(UnsupportedBoundary.CHECK_OBJ_DESPAWN2, wholeIndex, effects, cause);
        }
        effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN2));
        return new Result(pool.state(wholeIndex), effects);
    }

    /** Pata-Bata/Main.asm:11-46. Animator bytes, phase and position fractions are deliberately not cleared. */
    private void init(int wholeIndex, List<Effect> effects) {
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        state = state.addByte(ROUTINE, 2)
                .moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)
                .moveByte(PRIORITY, 3)                                    // Native immediate, not a priority table.
                .moveByte(COLLISION_TYPE, NATIVE_COLLISION_TYPE)
                .moveByte(X_RADIUS, 0x10).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 0x10)
                .moveWord(PATA_BATA_ANCHOR_X, state.centreX())
                .moveLong(PATA_BATA_ANCHOR_Y, (state.centreY() << 16) | 0x8000);
        pool.write(state);                                                // All native writes BEFORE SetObjectTileID.
        int tile;
        try {
            // SetObjectTileID (Object Tile ID Set.asm:7-16): MOVEQ #1; signed relative word,
            // then zero-extended FULL subtype2 byte doubled. No nibble mask or all-directory validation.
            int relative = (short) assets.objectTileIds().unsignedWord(2);
            int selected = relative + (state.subtype2() << 1);
            if ((selected & 1) != 0) {
                throw new UnsupportedOperationException("SetObjectTileID selected an odd native word address");
            }
            tile = assets.objectTileIds().unsignedWord(selected);
        } catch (RuntimeException cause) {
            throw boundary(UnsupportedBoundary.SET_OBJECT_TILE_ID, wholeIndex, effects, cause);
        }
        state = state.moveWord(TILE, tile);
        pool.write(state);
        boolean damaged = state.subtype() != 0;
        state = state.moveLong(PATA_BATA_X_DELTA, damaged ? -0x4000 : -0x8000)
                .moveWord(PATA_BATA_PHASE_SPEED, damaged ? -0x100 : -0x200)
                .moveWord(PATA_BATA_SHIFT, damaged ? 4 : 3)
                .moveByte(ANIMATION, damaged ? 1 : 0)
                .moveLong(MAPPINGS, damaged ? assets.damagedMappingsAddress() : assets.normalMappingsAddress());
        pool.write(state);
    }

    /** Pata-Bata/Main.asm:48-75; all word/long operations retain their shipped wrapping width. */
    private void mainMotion(int wholeIndex) {
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        int delta = state.longBits(PATA_BATA_X_DELTA);                      // MOVE.L oVar30,d0, including HIGH word.
        state = state.addLong(X_POSITION, delta);                         // ADD.L, not velocity integration.
        pool.write(state);
        int absolute = (state.centreX() - state.word(PATA_BATA_ANCHOR_X)) & 0xFFFF;
        if ((short) absolute < 0) { absolute = (-absolute) & 0xFFFF; }     // BPL / NEG.W: $8000 remains negative.
        if ((short) absolute >= 0x80) {                                   // CMPI.W / BLT is signed.
            delta = -delta;
            state = state.moveLong(PATA_BATA_X_DELTA, delta);              // NEG.L, then reload FULL reversed d0.
            pool.write(state);
            state = state.addLong(X_POSITION, delta);                     // Second add, ONCE; no Mosqui retry loop.
            pool.write(state);
            state = state.moveByte(SPRITE_FLAGS, state.spriteFlags() ^ 1)
                    .moveByte(FLAGS, state.flags() ^ 1).moveWord(PATA_BATA_PHASE, 0);
            pool.write(state);
        }
        state = state.addWord(PATA_BATA_PHASE, state.signedWord(PATA_BATA_PHASE_SPEED));
        pool.write(state);
        int angle = state.word(PATA_BATA_PHASE) >>> 8;                     // MOVE.B oVar34 reads BIG-ENDIAN HIGH byte.
        int sine = assets.math().calcSine(angle).sine();
        // D0 trace: initial/reversed MOVE.L delta; MOVE.W X/SUB.W/NEG.W, MOVE.W speed, MOVE.B phase;
        // CalcSine (Functions (Misc).asm:69-76) only ANDI/ADD/SUB/MOVE.W, no EXT.L/MOVEQ.
        // Therefore CalcSine leaves delta's old high word untouched. SWAP makes it the LOW word.
        int swapped = ((sine & 0xFFFF) << 16) | (delta >>> 16);
        int count = state.word(PATA_BATA_SHIFT) & 63;                      // 68000 register count, NOT Java mod 32.
        int shifted = count >= 32 ? swapped >> 31 : swapped >> count;     // Count zero retains all bits.
        state = state.moveLong(Y_POSITION, state.longBits(PATA_BATA_ANCHOR_Y) + shifted);
        pool.write(state);
    }

    /** One shared Ani_PataBata (Main.asm:76-77,84-85); subtype never substitutes an animation table. */
    private void animate(int wholeIndex) {
        var pool = runtime.pool();
        var s = pool.state(wholeIndex);
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(
                s.unsignedByte(ANIMATION), s.unsignedByte(PREVIOUS_ANIMATION),
                s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME), s.unsignedByte(MAP_FRAME),
                s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2)), assets.animations());
        pool.write(s.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame()).moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2()));
    }

    /** Object Spawner.asm:388-399,442-446, d0.w = LIVE anchor X. Entry bit 7 comes from the actual draw port. */
    private void checkObjDespawn2(CdObjectState state, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) == 0) {
            int anchorChunk = state.word(PATA_BATA_ANCHOR_X) & 0xFF80;
            int cameraChunk = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;
            if (((anchorChunk - cameraChunk) & 0xFFFF) > 0x280) {          // Unsigned BLS keeps exact equality.
                despawnObject(state, DespawnEntry.ANCHOR_OFFSCREEN, globals, effects);
                return;
            }
        }
        if ((globals.timeZone() & 0x80) != 0) {
            despawnObject(state, DespawnEntry.TIME_WARP, globals, effects);
        }
    }

    /** Object Spawner.asm:403-438. Existing typed saved-bank owner rejects an unowned raw period only here. */
    private void despawnObject(CdObjectState state, DespawnEntry entry, Globals globals, List<Effect> effects) {
        int savedId = state.savedFlagsId();
        int period = -1;
        if (savedId != 0) {
            period = globals.timeZone() & 0x7F;
            if ((globals.timeZone() & 0x80) != 0) {
                // MOVE.B timeWarpDir / EXT.W / NEG.W / ADD.W; the old period is clamped Past..Future.
                period = (short) ((period - (byte) globals.timeWarpDir()) & 0xFFFF);
                if (period < 0) { period = 0; }
                else if (period >= 3) { period = 2; }
            }
            runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        }
        runtime.pool().deleteObject(state.identity().wholeIndex());
        effects.add(new Despawn(entry, savedId, period));
    }

    private static UnsupportedPataBata boundary(UnsupportedBoundary boundary, int wholeIndex,
                                               List<Effect> effects, RuntimeException cause) {
        return new UnsupportedPataBata(boundary, wholeIndex, effects, cause);
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
