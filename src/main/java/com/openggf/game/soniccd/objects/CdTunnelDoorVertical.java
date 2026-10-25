package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjTunnelDoorV ($35), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Level/Palmtree Panic/Objects/Tunnel Door/Main.asm:7-83. Shipped FixBugs=0 behavior;
 * this owner has no fixed alternative. One run is one already selected physical-slot dispatch.
 * Init falls into Main; Main always tail-calls primary-player SolidObject, including its opened
 * path. Reset returns without Solid, including on carry. Every returning routine then calls
 * DrawObject and CheckObjDespawn (Object Spawner.asm:385-446), in that order.
 *
 * <p>The incoming sprite bit7 gates only FM_A4/frame1. Range writes are live BEFORE PlayFMSound;
 * frame1 follows its return, BEFORE Solid. Solid runs through the existing pure owner with one
 * live pool cut and actual supplied player/gates/globals. Root commits its exact result through
 * the synchronous port; the body requires all result object images live before continuing.
 * DrawObject's actual live image is reloaded before the tail. The placement runtime owns the
 * single real saved bank; deletion clears only its selected loaded bit before all 64 slot bytes.
 *
 * <p>No animation is called despite the native Ani_FlapDoorV include. No player, globals, pool
 * capture, comparison data or second saved bank is retained across runs. Port exceptions retain
 * completed source prefixes; results contain completed observations, never deferred gameplay.
 * Root owns phase/CAS/nonreentry, sound forwarding, source authentication and runtime admission.
 */
public final class CdTunnelDoorVertical {
    public static final int OBJECT_ID = 0x35;
    /** Main.asm:41-42: MOVEQ #$C,d0 / JSR SetObjectTileID, not a tile word. */
    public static final int TILE_SELECTOR = 0x0C;
    private static final int MAPPING_BYTES = 28;

    public enum NativeRoutine { INIT, MAIN, RESET }
    public enum NativeCall { DRAW_OBJECT, CHECK_OBJ_DESPAWN }
    /** Semantic request; Root's existing FM owner supplies the release-specific native sound ID. */
    public enum FmSound { FM_A4 }

    /** Already completed effects, in source order. Do not replay any publication or sound. */
    public sealed interface Effect permits RoutineEntered, Solid, PlayedFm, CallReturned, LoadedCleared, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record Solid(CdSolidObject.Result result) implements Effect {
        public Solid { Objects.requireNonNull(result); }
    }
    public record PlayedFm(FmSound sound) implements Effect {
        public PlayedFm { Objects.requireNonNull(sound); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    /** DespawnObject's BCLR #7 has already changed the existing placement bank. */
    public record LoadedCleared(int savedId, int period) implements Effect { }
    /** DeleteObject has already cleared the live physical slot's 64 bytes. */
    public record Deleted() implements Effect { }

    /**
     * Raw timeZone/timeWarpDir bytes and the pre-scroll cameraX word. The direction is sign-extended
     * only at DespawnObject's actual warp branch. Unread period values introduce no earlier gate;
     * the existing saved-bank owner checks an actually reached bank address.
     */
    public record Globals(int rawTimeZone, int rawTimeWarpDirection, int cameraXWord) {
        public Globals { byteValue(rawTimeZone); byteValue(rawTimeWarpDirection); wordValue(cameraXWord); }
    }
    /** Actual objPlayerSlot cut; Solid's output replaces these values for the return to Root. */
    public record SolidInputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public SolidInputs { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }

    /**
     * Constructed declarations confer no media authority. The visual retains the complete two-frame
     * counted include with no script; mappingsAddress is the actual pointer Init stores; ObjectTileIDs
     * stays the native 74-byte source table consumed through its existing decoder owner.
     */
    public record Assets(CdObjectVisualAssets visual, int mappingsAddress, CdAssetBytes objectTileIds) {
        public Assets {
            Objects.requireNonNull(visual); Objects.requireNonNull(objectTileIds);
            if (visual.mappingFrames().size() != 2 || visual.mappingTableOffset() != 0
                    || visual.mappings().length() != MAPPING_BYTES || !visual.animations().isEmpty()
                    || visual.animationTable().length() != 0 || objectTileIds.length() != 74) {
                throw new IllegalArgumentException("DoorV needs its complete static two-frame include and ObjectTileIDs");
            }
            if (mappingsAddress <= 0 || (mappingsAddress & 1) != 0
                    || mappingsAddress > 0xFFFFFF - (MAPPING_BYTES - 1)) {
                throw new IllegalArgumentException("DoorV mappings need an aligned bounded native pointer");
            }
        }

        /**
         * Authenticated-owner ingress. The parallel asset owner supplies these exact semantic bindings
         * and selector C/subtype2=0 decoder admission. No offset, mapping, art or script fallback lives here.
         */
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner);
            return new Assets(owner.tunnelDoorVertical(),
                    owner.view(CdR11aObjectAssets.Table.TUNNEL_DOOR_VERTICAL_MAPPINGS).range().nativeAddress(),
                    owner.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes());
        }
    }

    /** Exactly one actual shared Solid call and its ephemeral call cut. */
    public record SolidCall(CdObjectPool.Snapshot callCut, CdSolidObject.Player expectedPlayer,
                            CdSolidObject.Globals expectedGlobals, CdSolidObject.Result result) {
        public SolidCall {
            Objects.requireNonNull(callCut); Objects.requireNonNull(expectedPlayer);
            Objects.requireNonNull(expectedGlobals); Objects.requireNonNull(result);
        }
    }
    /**
     * Root validates the open object phase, expected player/globals and affected slots against callCut;
     * publishes a0/otherSlots through S4; patches exactly result's player/global write sets through S3;
     * and forwards result.fmRequests() once in source order. Reject stale or closed phases. Do not retain
     * the call, dispatch a body or replay its metadata. Exceptions stop composition without rollback.
     */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }
    /** Existing synchronous DrawObject cull/enqueue over this live pool, without another body dispatch. */
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }
    /** Source PlayFMSound only. The sink owns sound I/O, never player/object gameplay or reentry. */
    @FunctionalInterface public interface PlayFmCall { void playFm(FmSound sound); }

    public record Result(CdObjectState state, CdSolidObject.Player finalPlayer, CdSolidObject.Globals finalGlobals,
                         List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(finalPlayer); Objects.requireNonNull(finalGlobals);
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** An odd byte or a byte above 4 indexes outside the native three-word routine table. */
        ROUTINE_DISPATCH,
        /** Init has published its prefix and reached SetObjectTileID's unowned subtype2 word. */
        TILE_ID_SUBTYPE2
    }
    public static final class UnsupportedTunnelDoorVertical extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedTunnelDoorVertical(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at DoorV whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdSolidObject solid;
    private final SolidCommit solidCommit;
    private final DrawObjectCall drawObject;
    private final PlayFmCall playFm;

    public CdTunnelDoorVertical(CdObjectPlacementRuntime runtime, Assets assets, CdSolidObject solid,
                                SolidCommit solidCommit, DrawObjectCall drawObject, PlayFmCall playFm) {
        this.runtime = Objects.requireNonNull(runtime); this.assets = Objects.requireNonNull(assets);
        this.solid = Objects.requireNonNull(solid); this.solidCommit = Objects.requireNonNull(solidCommit);
        this.drawObject = Objects.requireNonNull(drawObject); this.playFm = Objects.requireNonNull(playFm);
    }

    /** Main.asm:7-20: routine call, DrawObject, then JMP CheckObjDespawn, for this one physical slot. */
    public Result run(int wholeIndex, Globals globals, SolidInputs inputs) {
        Objects.requireNonNull(globals); Objects.requireNonNull(inputs);
        var pool = runtime.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("DoorV dispatch requires live ID $35, slot " + wholeIndex);
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 4) {
            throw new UnsupportedTunnelDoorVertical(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
        }
        var c = new Cursor(pool, state, inputs);
        switch (routine) {
            case 0 -> { init(c); main(c); } // Init has no RTS and falls straight into Main.
            case 2 -> main(c);
            case 4 -> reset(c);
        }
        draw(c);
        checkObjDespawn(c, globals);
        return new Result(c.state, c.player, c.globals, c.effects);
    }

    /** ObjTunnelDoorV_Init (:34-42): no timer, frame, animation, velocity, fraction or flags clear. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveLong(MAPPINGS, assets.mappingsAddress())
                .moveByte(PRIORITY, 1).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(WIDTH, 4).moveByte(Y_RADIUS, 0x18);
        c.publish(); // Prefix precedes JSR SetObjectTileID, including when its reached read is unsupported.
        if (c.state.subtype2() != 0) {
            throw new UnsupportedTunnelDoorVertical(UnsupportedBoundary.TILE_ID_SUBTYPE2, c.wholeIndex, c.state.subtype2());
        }
        int tile = CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), TILE_SELECTOR, c.state.subtype2());
        c.state = c.state.moveWord(TILE, tile);
    }

    /** ObjTunnelDoorV_Main (:47-71): unsigned word borrow and range tests, then unconditional Solid. */
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        var motion = c.player.actor().motion(); // Main explicitly LEAs the actual primary player.
        int objectY = c.state.centreY(), playerY = motion.centreY16_16() >>> 16;
        int d0 = (objectY - playerY) & 0xFFFF;
        if (objectY < playerY) d0 = (-d0) & 0xFFFF; // SUB.W / BCC .AbsDX / NEG.W: borrow, not BMI.
        if (d0 < 0x20) {
            int objectX = c.state.centreX(), playerX = motion.centreX16_16() >>> 16;
            d0 = (objectX - playerX) & 0xFFFF;
            if (objectX >= playerX && d0 < 0x30) { // BCS .NotRange, then unsigned CMP/BCC.
                c.state = c.state.moveWord(TUNNEL_DOOR_TIMER, 0).moveByte(ROUTINE, 4);
                if ((c.state.spriteFlags() & 0x80) != 0) {
                    c.publish(); // CLR.W/routine4 are live while sound runs; frame1 is not written yet.
                    playFm.playFm(FmSound.FM_A4);
                    c.effects.add(new PlayedFm(FmSound.FM_A4));
                    c.state = c.state.moveByte(MAP_FRAME, 1);
                }
            }
        }
        solid(c); // .NotRange BRA.W ObjTunnelDoorV_SolidObj also follows the opened/sounded path.
    }

    /** ObjTunnelDoorV_SolidObj (:26-29): d3/d4 X/Y loads are unused by the shared Solid owner. */
    private void solid(Cursor c) {
        c.publish();
        var callCut = c.pool.capture();
        var result = solid.run(CdSolidObject.Entry.SOLID_OBJECT, c.state, c.player, c.gates, c.globals, callCut);
        solidCommit.commit(new SolidCall(callCut, c.player, c.globals, result));
        if (!c.pool.state(c.wholeIndex).equals(result.object())) {
            throw new IllegalStateException("DoorV SolidCommit did not publish a0");
        }
        for (var other : result.otherSlots()) {
            if (!c.pool.state(other.identity().wholeIndex()).equals(other)) {
                throw new IllegalStateException("DoorV SolidCommit did not publish a2 slot " + other.identity().wholeIndex());
            }
        }
        c.state = result.object(); c.player = result.player(); c.globals = result.globals();
        c.effects.add(new Solid(result));
    }

    /** ObjTunnelDoorV_Reset (:76-83): ADDQ.B addresses HIGH $3A; low $3B is untouched. */
    private static void reset(Cursor c) {
        c.enter(NativeRoutine.RESET);
        int sum = c.state.unsignedByte(TUNNEL_DOOR_TIMER_HIGH) + 8;
        c.state = c.state.moveByte(TUNNEL_DOOR_TIMER_HIGH, sum);
        if (sum > 0xFF) { // BCC .End: unsigned BYTE carry, no word timer or signed-overflow test.
            c.state = c.state.addByte(ROUTINE, -2).moveByte(MAP_FRAME, 0);
        }
    }

    private void draw(Cursor c) {
        c.publish();
        drawObject.drawObject(c.pool, c.state.identity());
        c.state = c.pool.state(c.wholeIndex); // DrawObject clears bit7 even when culled or its bucket is full.
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    /** CheckObjDespawn/DespawnObject/DespawnObjTimeWarp (Object Spawner.asm:385-446). */
    private void checkObjDespawn(Cursor c, Globals globals) {
        int d0 = c.state.centreX(); // CheckObjDespawn loads the live oX before its sprite-sign test.
        boolean outside = false;
        if ((c.state.spriteFlags() & 0x80) == 0) {
            d0 &= 0xFF80;
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;
            outside = ((d0 - d1) & 0xFFFF) > 0x280; // BLS keeps equality and enters the time-warp check.
        }
        boolean warping = (globals.rawTimeZone() & 0x80) != 0;
        if (outside || warping) {
            int savedId = c.state.savedFlagsId();
            if (savedId != 0) {
                int period = globals.rawTimeZone() & 0x7F;
                if (warping) {
                    int d1 = (period - (byte) globals.rawTimeWarpDirection()) & 0xFFFF; // EXT.W / NEG.W / ADD.W.
                    period = (short) d1 < 0 ? 0 : d1 >= 3 ? 2 : d1; // BPL, then CMP #TIME_FUTURE+1 / BCS.
                }
                runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new LoadedCleared(savedId, period)); // A failed bank bound never reaches DeleteObject.
            }
            c.pool.deleteObject(c.wholeIndex);
            c.state = c.pool.state(c.wholeIndex);
            c.effects.add(new Deleted());
        }
        c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }

    /** Invocation-local working registers, never retained by the owner or exposed for later replay. */
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final CdSolidObject.Gates gates;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        CdSolidObject.Player player;
        CdSolidObject.Globals globals;
        Cursor(CdObjectPool pool, CdObjectState state, SolidInputs inputs) {
            this.pool = pool; this.state = state; wholeIndex = state.identity().wholeIndex();
            player = inputs.player(); gates = inputs.gates(); globals = inputs.globals();
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
