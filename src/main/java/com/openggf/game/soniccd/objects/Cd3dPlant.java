package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Obj3DPlant ($2C): Level/Palmtree Panic/Objects/3D Ramp/Main.asm:7-134 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over the live dynamic pool and saved bank of one
 * CdObjectPlacementRuntime. One {@link #run} is one RunObjects dispatch of the slot: the routine selected by
 * oRoutine, DrawObject (R43LegacyDrawObject resolves to DrawObject in this build) through the supplied port, then
 * CheckObjDespawn2 against the shared original X anchor (oVar2A).
 *
 * <p>Init plants the family with first-free FindObjSlot allocations and commits every child in source order, so
 * a child in a later slot executes in the same RunObjects pass while one in an earlier slot waits. Unwritten
 * bytes (priority and subtype of .Loop children, oVar2C of .Loop2 children, every untouched field) retain slot
 * contents. Main reads the completed player slot's oX word and oPlayerCtrl bit 1. There is no private queue,
 * renderer, pool snapshot or player mutation here, and nothing is keyed on a fixture, frame or route.</p>
 *
 * <p>Finite admission: routines 0 and 2 are the whole native index. A full pool during Init stops at the
 * native out-of-pool write after committing what native had committed; nothing is rolled back.</p>
 */
public final class Cd3dPlant {
    public static final int OBJECT_ID = 0x2C;
    /** Obj3DPlant_Init: MOVE.W #$4424,oTile(a0) - palette line 2, tile $424 (Art_3DPlant at VRAM $8480). */
    public static final int NATIVE_TILE_WORD = 0x4424;

    public enum NativeCall { INIT, MAIN, DRAW_OBJECT, CHECK_OBJ_DESPAWN2 }
    /** Which source branch entered DespawnObject (Object Spawner.asm:403-438). */
    public enum DespawnEntry { ANCHOR_OFFSCREEN, TIME_WARP }
    /** .Init/.Loop plants share the parent's width/radius; .Loop2 plants are the small subtype-1 frame-1 ones. */
    public enum ChildKind { MAIN_PLANT, SMALL_PLANT }

    /** Ordered return metadata. Pool/bank effects describe completed writes and must not be replayed. */
    public sealed interface Effect permits CallReturned, ChildCreated, Despawn { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** FindObjSlot succeeded and this child's writes were committed at that whole-pool slot with that centre X. */
    public record ChildCreated(ChildKind kind, int wholeIndex, int centreX) implements Effect {
        public ChildCreated {
            Objects.requireNonNull(kind, "kind");
            if (wholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || wholeIndex >= CdObjectPool.END_WHOLE_INDEX) {
                throw new IllegalArgumentException("Plant children live in dynamic slots 32..127");
            }
            wordValue(centreX, "child centre X");
        }
    }
    /**
     * DespawnObject executed: for a nonzero saved ID the saved bank's loaded bit 7 was cleared at the selected
     * native period (-1 means no bank write), then the slot was fully deleted.
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

    /** The completed player slot (objPlayerSlot) after its own dispatch: integer oX word and oPlayerCtrl (oVar2C) byte. */
    public record Player(int xWord, int playerControl) {
        public Player {
            wordValue(xWord, "player oX");
            byteValue(playerControl, "player oPlayerCtrl");
        }
    }

    /** Explicit immutable globals at the call cut: timeZone/timeWarpDir bytes and the pre-scroll cameraX word. */
    public record Globals(int timeZone, int timeWarpDir, int cameraXWord) {
        public Globals {
            byteValue(timeZone, "timeZone");
            byteValue(timeWarpDir, "timeWarpDir");
            wordValue(cameraXWord, "cameraX");
            if ((timeZone & 0x7F) > 2) {
                throw new IllegalArgumentException("Native CD period must be 0, 1 or 2");
            }
        }
    }

    /**
     * Pure declared plant data: the two-frame MapSpr_3DPlant table, the native long Init stores in oMap, and the
     * Obj3DPlant_Offsets1/Offsets2 signed words. Production obtains it from the authenticated owner through
     * {@link #fromObjectAssets}; a constructed record never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdObjectVisualAssets plant, int mappingsAddress, CdR11aObjectAssets.PlantOffsets offsets) {
        public Assets {
            Objects.requireNonNull(plant, "plant");
            Objects.requireNonNull(offsets, "offsets");
            if (plant.mappingFrames().size() != 2) {
                throw new IllegalArgumentException("MapSpr_3DPlant declares exactly two mapping frames");
            }
        }

        /** Original Word RAM address of the authenticated mapping view; this is the stored native long. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.plant(),
                    assets.view(CdR11aObjectAssets.Table.PLANT_MAPPINGS).range().nativeAddress(),
                    assets.plantOffsets());
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the Obj3DPlant cut (3D Ramp/Main.asm:13): after the
     * routine's pool writes and before CheckObjDespawn2. The shared CPU queue implements it over the live pool:
     * clear bit 7 of the live slot first, then cull/capacity, then append only the physical slot reference. Any
     * bit-7 write it makes is reloaded afterwards; this body never writes back its pre-call image. The caller must
     * keep this synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /** Final live image of the dispatched slot after the whole dispatch (fully zero after a despawn). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Despawn.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** oRoutine outside {0,2} indexes past the two-entry Obj3DPlant_Index (3D Ramp/Main.asm:19-22). */
        ROUTINE_DISPATCH,
        /** FindObjSlot found no free slot; native .Init/.Loop2 would write past the pool (3D Ramp/Main.asm:44,67). */
        POOL_EXHAUSTED
    }

    /** Thrown at the boundary; the pool and bank hold exactly the writes native had committed before it. */
    public static final class UnsupportedPlant extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedPlant(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final DrawObjectCall drawObject;

    /** The runtime supplies the single live pool and saved bank. */
    public Cd3dPlant(CdObjectPlacementRuntime runtime, Assets assets, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    public Assets assets() { return assets; }

    /** One Obj3DPlant dispatch for the whole-pool dynamic slot currently holding ID $2C. */
    public Result run(int wholeIndex, Player player, Globals globals) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(globals, "globals");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not Obj3DPlant $2C");
        }
        var effects = new ArrayList<Effect>();
        switch (state.routine()) {                                             // MOVE.W Obj3DPlant_Index(pc,d0.w),d0 / JSR
            case 0 -> {
                state = init(state, pool, effects);
                effects.add(new CallReturned(NativeCall.INIT));
            }
            case 2 -> {
                state = main(state, player);
                pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            default -> throw new UnsupportedPlant(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " indexes past Obj3DPlant_Index");
        }
        drawObject.drawObject(pool, state.identity());                         // JSR R43LegacyDrawObject (= DrawObject)
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        // MOVE.W oVar2A(a0),d0 / BRA.W CheckObjDespawn2 read the live slot after DrawObject, including a zeroed one.
        var reloaded = pool.state(wholeIndex);
        checkObjDespawn2(reloaded, reloaded.word(PLANT_ANCHOR_X), globals, effects);
        effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN2));
        return new Result(pool.state(wholeIndex), effects);
    }

    /**
     * Obj3DPlant_Init (3D Ramp/Main.asm:24-86). The parent's own .Init iteration is committed before the first
     * FindObjSlot; each child is committed before the next search, because FindObjSlot tests the ID byte.
     */
    private CdObjectState init(CdObjectState state, CdObjectPool pool, List<Effect> effects) {
        int wholeIndex = state.identity().wholeIndex();
        int d3 = state.centreX();                                              // MOVE.W oX(a0),d3: the original X
        boolean bitZeroWasSet = (state.subtype() & 1) != 0;                    // BCLR #0,oSubtype(a0): Z from the old bit
        int mainCount = bitZeroWasSet ? 2 : 4;                                 // MOVEQ #3,d6 / BEQ.S .GotCount / MOVEQ #1,d6; DBF
        var parent = state.moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)     // ORI.B #4,oSprFlags(a0)
                .moveLong(MAPPINGS, assets.mappingsAddress())                  // MOVE.L #MapSpr_3DPlant,oMap(a0)
                .moveWord(TILE, NATIVE_TILE_WORD)                              // MOVE.W #$4424,oTile(a0)
                .moveByte(WIDTH, 0x18)                                         // MOVE.B #$18,oWidth(a0)
                .moveByte(Y_RADIUS, 0x14)                                      // MOVE.B #$14,oYRadius(a0)
                .moveByte(SUBTYPE, state.subtype() & ~1);
        int parentY = parent.centreY();
        var mainOffsets = assets.offsets().main();
        for (int i = 0; i < mainCount; i++) {                                  // .GotCount: MOVEQ #0,d2 / BRA.S .Init ... DBF d6,.Loop
            CdObjectState target;
            int targetWhole;
            if (i == 0) {
                target = parent;                                               // MOVEA.L a0,a1: the first .Init is the parent itself
                targetWhole = wholeIndex;
            } else {
                targetWhole = findObjSlot(pool, wholeIndex);                   // .Loop: JSR FindObjSlot (result not tested)
                target = pool.state(targetWhole);
            }
            target = target.addByte(ROUTINE, 2)                                // ADDQ.B #2,oRoutine(a1)
                    .moveByte(ID, OBJECT_ID)                                   // MOVE.B #$2C,oID(a1)
                    .moveWord(X, d3)                                           // MOVE.W d3,oX(a1)
                    .moveWord(Y, parentY)                                      // MOVE.W oY(a0),oY(a1)
                    .moveWord(PLANT_ANCHOR_X, d3)                              // MOVE.W d3,oVar2A(a1): shared original anchor
                    .moveLong(MAPPINGS, assets.mappingsAddress())              // MOVE.L oMap(a0),oMap(a1)
                    .moveWord(TILE, NATIVE_TILE_WORD)                          // MOVE.W oTile(a0),oTile(a1)
                    .moveByte(WIDTH, 0x18)                                     // MOVE.B oWidth(a0),oWidth(a1)
                    .moveByte(Y_RADIUS, 0x14);                                 // MOVE.B oYRadius(a0),oYRadius(a1)
            target = target.moveByte(SPRITE_FLAGS, target.spriteFlags() | 4)  // ORI.B #4,oSprFlags(a1)
                    .addWord(X, mainOffsets.get(i));                           // MOVE.W Obj3DPlant_Offsets1(pc,d2.w),d1 / ADD.W d1,oX(a1)
            target = target.moveWord(PLANT_BASE_X, target.centreX());          // MOVE.W oX(a1),oVar2C(a1)
            pool.write(target);
            if (i == 0) {
                parent = target;
            } else {
                effects.add(new ChildCreated(ChildKind.MAIN_PLANT, targetWhole, target.centreX()));
            }
        }
        var smallOffsets = assets.offsets().small();
        for (int i = 0; i < 3; i++) {                                          // MOVEQ #2,d6 / MOVEQ #0,d2 / .Loop2 ... DBF d6,.Loop2
            int childWhole = findObjSlot(pool, wholeIndex);                    // JSR FindObjSlot (result not tested)
            var child = pool.state(childWhole)
                    .addByte(ROUTINE, 2)                                       // ADDQ.B #2,oRoutine(a1)
                    .moveByte(ID, OBJECT_ID)                                   // MOVE.B #$2C,oID(a1)
                    .moveByte(SUBTYPE, 1)                                      // MOVE.B #1,oSubtype(a1)
                    .moveByte(MAP_FRAME, 1)                                    // MOVE.B #1,oMapFrame(a1)
                    .moveByte(PRIORITY, 4)                                     // MOVE.B #4,oPriority(a1)
                    .moveWord(X, d3)                                           // MOVE.W d3,oX(a1)
                    .moveWord(PLANT_ANCHOR_X, d3)                              // MOVE.W d3,oVar2A(a1)
                    .moveWord(Y, parentY)                                      // MOVE.W oY(a0),oY(a1)
                    .moveLong(MAPPINGS, assets.mappingsAddress())              // MOVE.L oMap(a0),oMap(a1)
                    .moveWord(TILE, NATIVE_TILE_WORD)                          // MOVE.W oTile(a0),oTile(a1)
                    .moveByte(WIDTH, 0xC)                                      // MOVE.B #$C,oWidth(a1)
                    .moveByte(Y_RADIUS, 0xC);                                  // MOVE.B #$C,oYRadius(a1)
            child = child.moveByte(SPRITE_FLAGS, child.spriteFlags() | 4)      // ORI.B #4,oSprFlags(a1)
                    .addWord(X, smallOffsets.get(i));                          // MOVE.W Obj3DPlant_Offsets2(pc,d2.w),d1 / ADD.W d1,oX(a1)
            pool.write(child);                                                 // oVar2C is never written for .Loop2 plants
            effects.add(new ChildCreated(ChildKind.SMALL_PLANT, childWhole, child.centreX()));
        }
        return pool.state(wholeIndex);                                         // RTS: the parent as committed
    }

    /** FindObjSlot (Object Spawner.asm:329-340) with the native caller's untested failure made explicit. */
    private static int findObjSlot(CdObjectPool pool, int parentWholeIndex) {
        var found = pool.findObjSlot();
        if (found.isEmpty()) {
            throw new UnsupportedPlant(UnsupportedBoundary.POOL_EXHAUSTED, parentWholeIndex,
                    "FindObjSlot found no free dynamic slot; native Obj3DPlant_Init would write past the pool");
        }
        return found.getAsInt();
    }

    /** Obj3DPlant_Main (3D Ramp/Main.asm:93-134): main plants follow the player's chunk position while on a 3D ramp. */
    private static CdObjectState main(CdObjectState state, Player player) {
        if (state.subtype() != 0) { return state; }                            // TST.B oSubtype(a0) / BNE.S .End
        int d0 = 0;                                                            // MOVEQ #0,d0
        if ((player.playerControl() & 2) != 0) {                               // BTST #1,oVar2C(a6) / BEQ.S .MovePlant
            int d2 = player.xWord();                                           // MOVE.W oX(a6),d0 / MOVE.W d0,d2
            d0 = d2 & 0xFF;                                                    // ANDI.W #$FF,d0
            boolean atOrRightOfAnchor = d2 >= state.word(PLANT_ANCHOR_X);      // CMP.W oVar2A(a0),d2 / BCC.S .GetChunkPos (unsigned)
            if (!atOrRightOfAnchor) { d0 = 0xFF - d0; }                        // MOVE.W d0,d1 / MOVE.W #$FF,d0 / SUB.W d1,d0
            if (d0 >= 0xC0) { d0 = d0 >= 0xF0 ? 0 : 0xBF; }                    // .GetChunkPos: CMPI #$C0 / BCS; CMPI #$F0 / BCC .Cap; #$BF
            d0 >>>= 1;                                                         // .GotChunkPos: LSR.W #1,d0
            if (!atOrRightOfAnchor) { d0 = (-d0) & 0xFFFF; }                   // CMP.W oVar2A(a0),d2 / BCC.S .MovePlant / NEG.W d0
        }
        return state.moveWord(X, state.word(PLANT_BASE_X) + d0);               // .MovePlant: ADD.W oVar2C(a0),d0 / MOVE.W d0,oX(a0)
    }

    /** CheckObjDespawn2 / DespawnObjTimeWarp (Object Spawner.asm:388-399,442-446) with d0 = oVar2A. */
    private void checkObjDespawn2(CdObjectState state, int anchorX, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) == 0) {                               // TST.B oSprFlags / BMI DespawnObjTimeWarp
            int d0 = anchorX & 0xFF80;
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;       // SUBI.W #$80 / ANDI.W #$FF80
            d0 = (d0 - d1) & 0xFFFF;
            if (d0 > 0x80 + (320 + 0x40) + 0x80) {                             // CMPI.W #$280 / BLS: unsigned, equality keeps
                despawnObject(state, DespawnEntry.ANCHOR_OFFSCREEN, globals, effects);
                return;
            }
        }
        if ((globals.timeZone() & 0x80) != 0) {                                // DespawnObjTimeWarp: BTST #7,timeZone
            despawnObject(state, DespawnEntry.TIME_WARP, globals, effects);
        }
    }

    /** DespawnObject (Object Spawner.asm:403-438): bank loaded bit, then the full 64-byte delete. */
    private void despawnObject(CdObjectState state, DespawnEntry entry, Globals globals, List<Effect> effects) {
        int savedId = state.savedFlagsId();                                    // MOVE.B oSavedFlagsID / BEQ .Delete
        int period = -1;
        if (savedId != 0) {
            period = despawnPeriod(globals);
            runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); // BCLR #7,2(a1,d0.w)
        }
        runtime.pool().deleteObject(state.identity().wholeIndex());            // JSR DeleteObject
        effects.add(new Despawn(entry, savedId, period));
    }

    /** Object Spawner.asm:413-431: the old period during a warp, clamped to Past..Future. */
    private static int despawnPeriod(Globals globals) {
        int d1 = globals.timeZone() & 0x7F;                                    // MOVE.B timeZone,d1 / BCLR #7,d1
        if ((globals.timeZone() & 0x80) == 0) { return d1; }                   // BEQ .MarkUnloaded
        d1 = (short) ((d1 - (byte) globals.timeWarpDir()) & 0xFFFF);           // EXT.W / NEG.W d2 / ADD.W d2,d1
        if (d1 < 0) { return 0; }                                              // BPL .CheckFuture; MOVEQ #TIME_PAST
        return d1 < 3 ? d1 : 2;                                                // CMPI.W #TIME_FUTURE+1 / BCS; MOVEQ #TIME_FUTURE
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }

    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
