package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjMosqui ($3D): Level/Palmtree Panic/Objects/Mosqui/Main.asm:7-151 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over the live dynamic pool and saved bank of
 * one CdObjectPlacementRuntime. One {@link #run} is one RunObjects dispatch of the slot:
 * DestroyOnGoodFuture, the routine selected by oRoutine, DrawObject through the supplied port,
 * then CheckObjDespawn2 against the original X anchor (oVar2A). Native writes are committed to the
 * pool in source order; a failure propagates and the caller closes the frame. There is no private
 * queue, renderer, audio owner, pool snapshot or player mutation here, and no state outside the pool.
 *
 * <p>Finite admission: fresh/same-world R11A Present with goodFuture = 0 runs completely. The good
 * future conversion into Explosion ($18) stops at its predicate before any write; unknown routine
 * bytes and unbound script pointers stop before any write; the native infinite reversal loop of a
 * corrupt slot stops instead of hanging. Nothing is keyed on a fixture, frame or route coordinate.</p>
 */
public final class CdMosqui {
    public static final int OBJECT_ID = 0x3D;
    /** ObjMosqui_Init colType; ObjColSizes entry 43 is ($C,$C) for the player touch owner. */
    public static final int NATIVE_COLLISION_TYPE = 0x2B;

    public enum NativeCall { DESTROY_ON_GOOD_FUTURE, INIT, MAIN, ANIMATE, DIVE, WAIT, DRAW_OBJECT, CHECK_OBJ_DESPAWN2 }
    /** ObjMosqui_Dive requests FM_A7 (Sound Drivers/FM/_Sound IDs.inc:33) through PlayFMSound. */
    public enum Sound { FM_A7 }
    /** ObjGetFloorDist zeroes and then FindLevelFloor may write the primaryAngle byte. */
    public enum GlobalField { PRIMARY_ANGLE }
    /** Which source branch entered DespawnObject (Object Spawner.asm:403-438). */
    public enum DespawnEntry { WAIT_OFFSCREEN, ANCHOR_OFFSCREEN, TIME_WARP }

    /**
     * Ordered return metadata. Pool/bank effects describe completed writes and must not be replayed.
     * Sound requests go to the caller's sound owner. PRIMARY_ANGLE supplies the final scratch byte
     * for the caller's separate shared angle owner; this body has not committed that global.
     */
    public sealed interface Effect permits CallReturned, SoundRequest, GlobalWrite, Despawn { }
    /** INIT precedes MAIN in the same dispatch because Init falls through into Main (Mosqui/Main.asm:55-59). */
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record SoundRequest(Sound sound) implements Effect {
        public SoundRequest { Objects.requireNonNull(sound, "sound"); }
    }
    public record GlobalWrite(GlobalField field, int value) implements Effect {
        public GlobalWrite {
            Objects.requireNonNull(field, "field");
            byteValue(value, "global byte");
        }
    }
    /**
     * DespawnObject executed: for a nonzero saved ID the saved bank's loaded bit 7 was cleared at
     * the selected native period (-1 means no bank write), then the slot was fully deleted.
     */
    public record Despawn(DespawnEntry entry, int savedId, int period) implements Effect {
        public Despawn {
            Objects.requireNonNull(entry, "entry");
            byteValue(savedId, "saved ID");
            if (period < -1 || period > 2 || (savedId == 0) != (period == -1)) {
                throw new IllegalArgumentException("Despawn period must be -1 exactly when the saved ID is 0");
            }
        }
    }

    /**
     * Explicit immutable globals at the call cut: goodFuture/timeZone/timeWarpDir bytes, the full
     * debugMode word, the pre-scroll cameraX word and the player slot's integer oX/oY words after
     * the player's own dispatch. The time zone's low seven bits must name a native period.
     */
    public record Globals(int goodFuture, int timeZone, int timeWarpDir, int debugMode,
                          int cameraXWord, int playerXWord, int playerYWord) {
        public Globals {
            byteValue(goodFuture, "goodFuture");
            byteValue(timeZone, "timeZone");
            byteValue(timeWarpDir, "timeWarpDir");
            wordValue(debugMode, "debugMode");
            wordValue(cameraXWord, "cameraX");
            wordValue(playerXWord, "player oX");
            wordValue(playerYWord, "player oY");
            if ((timeZone & 0x7F) > 2) {
                throw new IllegalArgumentException("Native CD period must be 0, 1 or 2");
            }
        }
    }

    /**
     * Pure declared Mosqui data: the 74-byte ObjectTileIDs view decoded by the existing tile owner,
     * both finite visual tables, and the four native pointer values Init stores in oMap/oVar30.
     * Production obtains it from the authenticated owner through {@link #fromObjectAssets}; a
     * constructed record never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets normal, CdObjectVisualAssets damaged,
                         int normalMappingsAddress, int normalScriptAddress,
                         int damagedMappingsAddress, int damagedScriptAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds");
            Objects.requireNonNull(normal, "normal");
            Objects.requireNonNull(damaged, "damaged");
            if (objectTileIds.length() != 74) {
                throw new IllegalArgumentException("ObjectTileIDs extent");
            }
            if (normal.animations().size() != 2 || damaged.animations().size() != 2) {
                throw new IllegalArgumentException("Mosqui script tables declare exactly two animations");
            }
            if (normalScriptAddress == damagedScriptAddress || normalMappingsAddress == damagedMappingsAddress) {
                throw new IllegalArgumentException("Normal and damaged Mosqui pointers must be distinct");
            }
        }

        /** Original Word RAM addresses of the authenticated views; these are the stored native longs. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(),
                    assets.normalMosqui(), assets.damagedMosqui(),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_NORMAL_MAPPINGS),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_NORMAL_ANIMATIONS),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_MAPPINGS),
                    address(assets, CdR11aObjectAssets.Table.MOSQUI_DAMAGED_ANIMATIONS));
        }

        private static int address(CdR11aObjectAssets assets, CdR11aObjectAssets.Table table) {
            return assets.view(table).range().nativeAddress();
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the ObjMosqui cut (Mosqui/Main.asm:13):
     * after the routine's pool writes and before CheckObjDespawn2. The shared CPU queue implements it
     * over the live pool: clear bit 7 of the live slot first, then cull/capacity, then append only the
     * physical slot reference. It must not test oID: after a Wait despawn the slot is already zeroed,
     * and native still enqueues that zeroed slot in bucket 0 without a camera test. Any bit-7 write
     * it makes is reloaded afterwards; this body never writes back its pre-call image.
     * The caller must keep this synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /** Final live image of the slot after the whole dispatch (fully zero after a despawn). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Despawn.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** DestroyOnGoodFuture .Destroy (Object Time Check.asm:20-41): conversion into the unowned Explosion. */
        GOOD_FUTURE_CONVERSION,
        /** oRoutine outside {0,2,4,6,8} indexes past ObjMosqui_Index (Mosqui/Main.asm:19-23). */
        ROUTINE_DISPATCH,
        /** oVar30 selects neither bound Mosqui script table (Mosqui/Main.asm:85,117). */
        ANIMATION_SCRIPT_POINTER,
        /** .SkipRange would reverse twice in one call: the native loop never terminates. */
        PATROL_REVERSAL_LOOP
    }

    /** Thrown before the boundary's writes; the pool and bank hold the last committed source state. */
    public static final class UnsupportedMosqui extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedMosqui(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdPlayerTerrain terrain;
    private final DrawObjectCall drawObject;

    /** The runtime supplies the single live pool and saved bank; the terrain is the production floor owner. */
    public CdMosqui(CdObjectPlacementRuntime runtime, Assets assets, CdPlayerTerrain terrain, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    public Assets assets() { return assets; }

    /** One ObjMosqui dispatch for the whole-pool dynamic slot currently holding ID $3D. */
    public Result run(int wholeIndex, Globals globals) {
        Objects.requireNonNull(globals, "globals");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjMosqui $3D");
        }
        var effects = new ArrayList<Effect>();
        destroyOnGoodFuture(state, globals);
        effects.add(new CallReturned(NativeCall.DESTROY_ON_GOOD_FUTURE));
        switch (state.routine()) {
            case 0 -> {
                state = init(state);
                effects.add(new CallReturned(NativeCall.INIT));
                state = main(state, globals);
                pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 2 -> {
                state = main(state, globals);
                pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 4 -> {
                state = animate(state);
                pool.write(state);
                effects.add(new CallReturned(NativeCall.ANIMATE));
            }
            case 6 -> {
                state = dive(state, effects);
                pool.write(state);
                effects.add(new CallReturned(NativeCall.DIVE));
            }
            case 8 -> {
                waitRoutine(state, globals, effects);
                effects.add(new CallReturned(NativeCall.WAIT));
            }
            default -> throw new UnsupportedMosqui(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " indexes past ObjMosqui_Index");
        }
        drawObject.drawObject(pool, state.identity());
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        // MOVE.W oVar2A(a0),d0 / JMP CheckObjDespawn2 read the live slot after DrawObject, including a zeroed one.
        var reloaded = pool.state(wholeIndex);
        checkObjDespawn2(reloaded, reloaded.word(MOSQUI_ANCHOR_X), globals, effects);
        effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN2));
        return new Result(pool.state(wholeIndex), effects);
    }

    /** Object Time Check.asm:12-44. Only the predicate is owned; .Destroy converts into the unowned Explosion. */
    private static void destroyOnGoodFuture(CdObjectState state, Globals globals) {
        if (globals.goodFuture() == 0) { return; }                        // TST.B goodFuture / BEQ .End
        if (globals.timeZone() == 1 && state.subtype() == 0) { return; } // CMPI.B #1,timeZone / TST.B oSubtype / BEQ .End
        // .Destroy: DeleteObject, restore oX/oY words, oID := $18, FM_EXPLODE tested on the already
        // cleared flags (never plays), ADDQ.L #4,SP skips the rest of ObjMosqui. Stop before those writes.
        throw new UnsupportedMosqui(UnsupportedBoundary.GOOD_FUTURE_CONVERSION, state.identity().wholeIndex(),
                "goodFuture " + globals.goodFuture() + ", timeZone $" + Integer.toHexString(globals.timeZone())
                        + ", oSubtype $" + Integer.toHexString(state.subtype()));
    }

    /** ObjMosqui_Init (Mosqui/Main.asm:26-54); no animator, velocity, fraction or collision-status field is cleared. */
    private CdObjectState init(CdObjectState state) {
        // MOVEQ #0,d0 / JSR SetObjectTileID (Object Tile ID Set.asm:7-17): selector 0, this slot's oSubtype2.
        int tile = CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), 0, state.subtype2());
        boolean damaged = state.subtype() != 0;                           // TST.B oSubtype / BNE .Damaged
        return state.addByte(ROUTINE, 2)
                .moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)          // ORI.B #4: retain flips and path layer
                .moveByte(PRIORITY, 3)
                .moveByte(X_RADIUS, 0x10)
                .moveByte(WIDTH, 0x10)
                .moveByte(Y_RADIUS, 0x10)
                .moveByte(COLLISION_TYPE, NATIVE_COLLISION_TYPE)
                .moveWord(MOSQUI_ANCHOR_X, state.centreX())               // MOVE.W oX,oVar2A: original anchor
                .moveWord(TILE, tile)
                .moveLong(MAPPINGS, damaged ? assets.damagedMappingsAddress() : assets.normalMappingsAddress())
                .moveLong(MOSQUI_ANIMATION_SCRIPT, damaged ? assets.damagedScriptAddress() : assets.normalScriptAddress())
                .moveLong(MOSQUI_X_DELTA, damaged ? -0x8000 : -0x10000);
    }

    /** ObjMosqui_Main (Mosqui/Main.asm:59-94). */
    private CdObjectState main(CdObjectState state, Globals globals) {
        // TST.W debugMode: the full word. BSR ObjMosqui_CheckInRange / BCS .StartDive.
        if (globals.debugMode() == 0 && checkInRange(state, globals)) {
            return state.addByte(ROUTINE, 2).moveByte(ANIMATION, 1);       // .StartDive: RTS, no movement or animation
        }
        int reversals = 0;
        while (true) {                                                     // .SkipRange
            int delta = state.longBits(MOSQUI_X_DELTA);
            state = state.addLong(X_POSITION, delta);                      // ADD.L oVar2C(a0),oX(a0): 16:16 add
            int d0 = (state.centreX() - state.word(MOSQUI_ANCHOR_X)) & 0xFFFF;
            if ((short) d0 < 0) { d0 = (-d0) & 0xFFFF; }                   // BPL .ChkTurn / NEG.W: $8000 stays negative
            if ((short) d0 < 0x80) { break; }                              // CMPI.W #$80 / BLT .Animate (signed)
            if (++reversals > 1) {
                throw new UnsupportedMosqui(UnsupportedBoundary.PATROL_REVERSAL_LOOP, state.identity().wholeIndex(),
                        "oVar2C $" + Integer.toHexString(delta) + " cannot leave |oX-oVar2A| >= $80");
            }
            state = state.moveLong(MOSQUI_X_DELTA, -delta)                 // NEG.L oVar2C(a0)
                    .moveByte(SPRITE_FLAGS, state.spriteFlags() ^ 1)       // BCHG #0,oSprFlags(a0)
                    .moveByte(FLAGS, state.flags() ^ 1);                   // BCHG #0,oFlags(a0); BRA .SkipRange adds again
        }
        return animate(state);                                             // .Animate: MOVEA.L oVar30,a1 / JMP AnimateObject
    }

    /** ObjMosqui_CheckInRange (Mosqui/Main.asm:98-112): carry out of the second SUBI.W on each axis. */
    private static boolean checkInRange(CdObjectState state, Globals globals) {
        int d0 = (globals.playerYWord() - state.centreY()) & 0xFFFF;       // MOVE.W oY(a1),d0 / SUB.W oY(a0),d0
        d0 = (d0 + 0x30) & 0xFFFF;                                         // SUBI.W #-$30,d0
        if (d0 >= 0x70) { return false; }                                  // SUBI.W #$70,d0 borrows below $70 / BCC .End
        int d1 = (globals.playerXWord() - state.centreX()) & 0xFFFF;       // MOVE.W oX(a1),d0 / SUB.W oX(a0),d0
        d1 = (d1 + 0x30) & 0xFFFF;                                         // SUBI.W #-$30,d1
        return d1 < 0x60;                                                  // SUBI.W #$60,d1 borrow -> BCS .StartDive
    }

    /** ObjMosqui_Animate / .Animate: the stored oVar30 long selects the script table, not the subtype. */
    private CdObjectState animate(CdObjectState state) {
        int pointer = state.longBits(MOSQUI_ANIMATION_SCRIPT);
        CdObjectVisualAssets visual;
        if (pointer == assets.normalScriptAddress()) { visual = assets.normal(); }
        else if (pointer == assets.damagedScriptAddress()) { visual = assets.damaged(); }
        else {
            throw new UnsupportedMosqui(UnsupportedBoundary.ANIMATION_SCRIPT_POINTER, state.identity().wholeIndex(),
                    "oVar30 $" + Integer.toHexString(pointer) + " is not a bound Mosqui script table");
        }
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(
                state.unsignedByte(ANIMATION), state.unsignedByte(PREVIOUS_ANIMATION),
                state.unsignedByte(ANIMATION_FRAME), state.unsignedByte(ANIMATION_TIME),
                state.unsignedByte(MAP_FRAME), state.spriteFlags(), state.flags(),
                state.routine(), state.unsignedByte(ROUTINE_2)), table);
        return state.moveByte(ANIMATION, out.animationId())
                .moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex())
                .moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame())
                .moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags())
                .moveByte(ROUTINE, out.routine())
                .moveByte(ROUTINE_2, out.routine2());
    }

    /** ObjMosqui_Dive (Mosqui/Main.asm:123-138). */
    private CdObjectState dive(CdObjectState state, List<Effect> effects) {
        state = state.addWord(Y, 6);                                       // ADDQ.W #6,oY(a0): integer word only
        var floor = CdObjectCollisionCheck.objGetFloorDist(terrain, state);
        effects.add(new GlobalWrite(GlobalField.PRIMARY_ANGLE, floor.primaryAngle()));
        int d1 = floor.distance();
        if (d1 > -8) { return state; }                                     // CMPI.W #-8,d1 / BGT .End
        state = state.addWord(Y, d1 + 8)                                   // SUBI.W #-8,d1 / ADD.W d1,oY(a0)
                .addByte(ROUTINE, 2);                                      // ADDQ.B #2,oRoutine(a0)
        if ((state.spriteFlags() & 0x80) != 0) {                           // TST.B oSprFlags / BPL .End: last build's bit 7
            effects.add(new SoundRequest(Sound.FM_A7));                    // MOVE.W #FM_A7,d0 / JSR PlayFMSound
        }
        return state;
    }

    /** ObjMosqui_Wait (Mosqui/Main.asm:142-151). */
    private void waitRoutine(CdObjectState state, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) != 0) { return; }                 // TST.B oSprFlags / BMI .End
        despawnObject(state, DespawnEntry.WAIT_OFFSCREEN, globals, effects); // JMP DespawnObject
    }

    /** CheckObjDespawn2 / DespawnObjTimeWarp (Object Spawner.asm:388-399,442-446) with d0 = oVar2A. */
    private void checkObjDespawn2(CdObjectState state, int anchorX, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) == 0) {                           // TST.B oSprFlags / BMI DespawnObjTimeWarp
            int d0 = anchorX & 0xFF80;
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;   // SUBI.W #$80 / ANDI.W #$FF80
            d0 = (d0 - d1) & 0xFFFF;
            if (d0 > 0x80 + (320 + 0x40) + 0x80) {                         // CMPI.W #$280 / BLS: unsigned, equality keeps
                despawnObject(state, DespawnEntry.ANCHOR_OFFSCREEN, globals, effects);
                return;
            }
        }
        if ((globals.timeZone() & 0x80) != 0) {                            // DespawnObjTimeWarp: BTST #7,timeZone
            despawnObject(state, DespawnEntry.TIME_WARP, globals, effects);
        }
    }

    /** DespawnObject (Object Spawner.asm:403-438): bank loaded bit, then the full 64-byte delete. */
    private void despawnObject(CdObjectState state, DespawnEntry entry, Globals globals, List<Effect> effects) {
        int savedId = state.savedFlagsId();                                // MOVE.B oSavedFlagsID / BEQ .Delete
        int period = -1;
        if (savedId != 0) {
            period = despawnPeriod(globals);
            runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); // BCLR #7,2(a1,d0.w)
        }
        runtime.pool().deleteObject(state.identity().wholeIndex());        // JSR DeleteObject
        effects.add(new Despawn(entry, savedId, period));
    }

    /** Object Spawner.asm:413-431: the old period during a warp, clamped to Past..Future. */
    private static int despawnPeriod(Globals globals) {
        int d1 = globals.timeZone() & 0x7F;                                // MOVE.B timeZone,d1 / BCLR #7,d1
        if ((globals.timeZone() & 0x80) == 0) { return d1; }               // BEQ .MarkUnloaded
        d1 = (short) ((d1 - (byte) globals.timeWarpDir()) & 0xFFFF);       // EXT.W / NEG.W d2 / ADD.W d2,d1
        if (d1 < 0) { return 0; }                                          // BPL .CheckFuture; MOVEQ #TIME_PAST
        return d1 < 3 ? d1 : 2;                                            // CMPI.W #TIME_FUTURE+1 / BCS; MOVEQ #TIME_FUTURE
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }

    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
