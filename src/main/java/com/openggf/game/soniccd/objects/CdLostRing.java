package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjLostRing ($11): Level/_Objects/Ring/Main.asm:317-460 at scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * executed over the live dynamic pool of one CdObjectPlacementRuntime. One {@link #run} is one RunObjects dispatch
 * of the slot through the five-entry ObjLostRing_Index (:317-329): Init (:332-405) falls into Main (:410-435),
 * Collect (:440-444) falls into Sparkle (:449-452), and Destroy (:458-459) deletes. The routines contain no FixBugs
 * conditional; the shipped path is the only path.
 *
 * <p>Writes are committed in source order: each ring's slot writes before the next source call (spill-timer port,
 * CalcSine, FindObjSlot), Main's writes before ObjMove's standing-object test, ObjGetFloorDist and the tail call,
 * and Collect's writes before CollectRing. Children allocated in later physical slots execute in this same
 * RunObjects pass only through the caller's live walk; earlier ones wait a frame. Globals are read and written
 * through the supplied ports at their source cut. Port, terrain and animator failures propagate unwrapped with the
 * completed writes retained; nothing is rolled back or retried and no Result is published for a failed dispatch,
 * so the frame root closes admission. There is no private queue, renderer, audio owner, saved-bank access, pool
 * snapshot or state outside the pool and the two ports.</p>
 *
 * <p>Finite admission: the R11A program (zone 0). Unknown routine bytes and ObjMove's standing-object read for a
 * dirty oFlags bit 3 are typed boundaries. Nothing is keyed on a fixture, frame or route coordinate.</p>
 */
public final class CdLostRing {
    public static final int OBJECT_ID = 0x11;
    /** RunObjects: MOVEQ #OBJCOUNT-1,d7 then DBF d7 per slot (Object Functions.asm:13,31); OBJCOUNT is 128. */
    private static final int RUN_OBJECTS_LAST_WHOLE = 127;

    /** INIT precedes MAIN in one dispatch because Init falls into Main; COLLECT precedes SPARKLE likewise. */
    public enum NativeCall { INIT, MAIN, COLLECT, SPARKLE, DRAW_OBJECT }
    /** .DidInit: MOVE.W #FM_RINGLOSS,d0 / JSR PlayFMSound (Ring/Main.asm:404-405; _Sound IDs.inc:14, $94). */
    public enum Sound { FM_RINGLOSS }
    /** ObjGetFloorDist zeroes and then FindLevelFloor may write the primaryAngle byte. */
    public enum GlobalField { PRIMARY_ANGLE }
    /** Which source branch reached DeleteObject. */
    public enum DeleteEntry {
        /** .ChkDel: TST.B ringLossAnimTimer / BEQ.S ObjLostRing_Destroy (Ring/Main.asm:429-430). */
        TIMER_EXPIRED,
        /** .ChkDel: CMP.W oY(a0),d0 / BCS.S ObjLostRing_Destroy against bottomBound+$E0 (:431-434). */
        BELOW_BOTTOM_BOUND,
        /** Routine 8, ObjLostRing_Destroy: BRA.W DeleteObject (:458-459). */
        DESTROY_ROUTINE
    }

    /**
     * Ordered return metadata. CallReturned, RingInitialized, SlotUnavailable and Deleted describe completed pool
     * work and must never be replayed; a child is dispatched only by the caller's live walk reading its ID. Sound
     * requests go to the caller's sound owner in order. PRIMARY_ANGLE is the final scratch byte for the caller's
     * shared angle owner to apply exactly once; this body has not committed that global.
     */
    public sealed interface Effect permits CallReturned, RingInitialized, SlotUnavailable, SoundRequest,
            RewardFmRequest, RewardSubCpuRequest, GlobalWrite, Deleted { }

    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** .DoInit/.SetVel completed ring {@code ring} (0 is the spawner itself) with these signed velocity words. */
    public record RingInitialized(int ring, int wholeIndex, int xVelocity, int yVelocity) implements Effect {
        public RingInitialized {
            if (ring < 0 || ring >= CdObjectPool.DYNAMIC_COUNT) {
                throw new IllegalArgumentException("Lost rings occupy at most the 96 dynamic slots");
            }
            if (wholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || wholeIndex >= CdObjectPool.END_WHOLE_INDEX) {
                throw new IllegalArgumentException("Lost rings live in dynamic slots 32..127");
            }
            if (xVelocity != (short) xVelocity || yVelocity != (short) yVelocity) {
                throw new IllegalArgumentException("Velocities must be signed native words");
            }
        }
    }
    /** .Loop: FindObjSlot returned NE for ring {@code ring}; nothing was written and .DidInit followed. */
    public record SlotUnavailable(int ring) implements Effect {
        public SlotUnavailable {
            if (ring < 1 || ring > CdObjectPool.DYNAMIC_COUNT) {
                throw new IllegalArgumentException("Allocation can fail only after the spawner's own ring");
            }
        }
    }
    public record SoundRequest(Sound sound) implements Effect {
        public SoundRequest { Objects.requireNonNull(sound, "sound"); }
    }
    /** CollectRing .PlaySound: JMP PlayFMSound (Ring/Main.asm:311-312), in call order. */
    public record RewardFmRequest(CdRing.FmSound sound) implements Effect {
        public RewardFmRequest { Objects.requireNonNull(sound, "sound"); }
    }
    /** CollectRing .GainLife: JMP SubCPUCmd (Ring/Main.asm:303-307), in call order. */
    public record RewardSubCpuRequest(CdRing.SubCpuCommand command) implements Effect {
        public RewardSubCpuRequest { Objects.requireNonNull(command, "command"); }
    }
    public record GlobalWrite(GlobalField field, int value) implements Effect {
        public GlobalWrite {
            Objects.requireNonNull(field, "field");
            if ((value & ~0xFF) != 0) { throw new IllegalArgumentException("global byte must be an unsigned native byte"); }
        }
    }
    /** DeleteObject (Object Functions.asm:228-237): the full 64-byte clear, with no saved-bank access. */
    public record Deleted(DeleteEntry entry) implements Effect {
        public Deleted { Objects.requireNonNull(entry, "entry"); }
    }

    /**
     * Explicit native values at this RunObjects dispatch; never an engine frame counter, delta, comparison row or
     * default seed. levelVIntCounter is the raw long bits of $FF190A during this object pass: the parent declares its
     * absolute-clock policy or stops before dispatch, and only the low byte (levelVIntCounter+3) is read, at Main's
     * floor-phase test. runObjectsD7 is RunObjects' loop counter, 127 minus the whole index; Init counts with d5, and
     * FindObjSlot, CalcSine, PlayFMSound and ObjMove leave d7 intact (Object Spawner.asm:329-340, Functions
     * (Misc).asm:69-76, Functions (General).asm:288-310, Object Functions.asm:91-128). zoneByte is $FF1506 and
     * bottomBoundWord is $FFF72E, sampled at the call; this body writes neither.
     */
    public record Inputs(int levelVIntCounter, int runObjectsD7, int zoneByte, int bottomBoundWord) {
        public Inputs {
            if ((runObjectsD7 & ~0xFFFF) != 0) { throw new IllegalArgumentException("RunObjects d7 is a DBF word"); }
            if ((zoneByte & ~0xFF) != 0) { throw new IllegalArgumentException("zone must be an unsigned native byte"); }
            if ((bottomBoundWord & ~0xFFFF) != 0) { throw new IllegalArgumentException("bottomBound must be a native word"); }
        }
    }

    /** The new spill store: one parent-owned value. Writes publish values, not replayable actions. */
    public interface SpillGlobalsPort {
        CdRingSpillGlobals read();
        void write(CdRingSpillGlobals state);
    }

    /**
     * Per-dispatch stores, each read and written at its source cut. ringGlobals is the parent's ONE existing
     * CdRingGlobals store (Init writes rings/updateHUDRings/livesFlags; CollectRing writes its reward fields);
     * spillGlobals is the new ringLossAnim store (Init writes the timer; Main reads the frame and the timer).
     */
    public record Ports(CdRing.GlobalsPort ringGlobals, SpillGlobalsPort spillGlobals) {
        public Ports {
            Objects.requireNonNull(ringGlobals, "ringGlobals");
            Objects.requireNonNull(spillGlobals, "spillGlobals");
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the tail-call cuts BRA.W DrawObject (Ring/Main.asm:435,452).
     * The shared CPU queue implements it over the live pool: clear bit 7 of the live slot first, then the pre-scroll
     * cull and capacity test, then append only the physical slot reference; it must not test oID. Any bit-7 write is
     * reloaded into the Result; this body never writes back a pre-call image. The caller must keep this synchronous
     * port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /**
     * The ONE shared CollectRing (Ring/Main.asm:290-312), never restated here. The signature equals
     * CdRing.collectRing, so the parent binds CdRing::collectRing once that helper is package-visible. This body passes
     * a pass-through of the ring-globals port and a SourceCalls that records the single tail-called request.
     */
    @FunctionalInterface
    public interface CollectRingCall {
        void collectRing(CdRing.GlobalsPort globals, CdRing.SourceCalls calls);
    }

    /**
     * Pure declared ring data shared with ObjRing: the nine-frame MapSpr_Ring / one-script Ani_Ring visual, the native
     * long Init stores in oMap, and its oTile word. Production obtains it from the authenticated owner through
     * {@link #fromObjectAssets}; a constructed record never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdObjectVisualAssets rings, int mappingsAddress, int baseTileWord) {
        public Assets {
            Objects.requireNonNull(rings, "rings");
            if (rings.mappingFrames().size() != 9 || rings.animations().size() != 1) {
                throw new IllegalArgumentException("MapSpr_Ring/Ani_Ring declare nine frames and one script");
            }
            if (mappingsAddress < 0x200000 || mappingsAddress >= 0x240000 || (mappingsAddress & 1) != 0) {
                throw new IllegalArgumentException("oMap must be an even original Word RAM address");
            }
            if ((baseTileWord & ~0xFFFF) != 0) { throw new IllegalArgumentException("oTile must be a native word"); }
        }

        /**
         * MOVE.L #MapSpr_Ring,oMap(a1) / MOVE.W #$A7AE,oTile(a1) (Ring/Main.asm:359,361): the authenticated
         * RING_MAPPINGS view address and the ART_RINGS base word (PLC_Std, VRAM $F5C0) that ObjRing also uses.
         */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.rings(),
                    assets.view(CdR11aObjectAssets.Table.RING_MAPPINGS).range().nativeAddress(),
                    assets.initialArtBank().binding(CdR11aAsset.ART_RINGS).baseWord());
        }
    }

    /** Final live image of the dispatched slot after the whole dispatch (fully zero after a delete). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** oRoutine outside {0,2,4,6,8} misaligns into or indexes past ObjLostRing_Index (Ring/Main.asm:318-329). */
        ROUTINE_DISPATCH,
        /** Only the R11A program (zone 0) is bound; Init's CMPI.B #6,zone MMZ branch (:363-369) is never admitted. */
        ZONE_DOMAIN,
        /** ObjMove with oFlags bit 3 reads oPlayerStandObj (oVar3D) and that slot's oID (Object Functions.asm:97-114). */
        OBJ_MOVE_STANDING_OBJECT
    }

    /**
     * ROUTINE_DISPATCH and ZONE_DOMAIN are thrown before any write or port call. OBJ_MOVE_STANDING_OBJECT is thrown at
     * the ObjMove call after the native writes that precede it are committed (for Init's fallthrough this includes
     * all of Init and its globals writes); nothing after it is invented.
     */
    public static final class UnsupportedLostRing extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedLostRing(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdPlayerMath math;
    private final CdPlayerTerrain terrain;
    private final DrawObjectCall drawObject;
    private final CollectRingCall collectRing;
    private final CdObjectAnimation.AnimationTable aniRing;

    /**
     * The runtime supplies the single live pool; math is the production CalcSine owner over the actual SineTable;
     * terrain is the production floor owner; drawObject is the one shared queue; collectRing is CdRing::collectRing.
     */
    public CdLostRing(CdObjectPlacementRuntime runtime, Assets assets, CdPlayerMath math, CdPlayerTerrain terrain,
                      DrawObjectCall drawObject, CollectRingCall collectRing) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.math = Objects.requireNonNull(math, "math");
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
        this.collectRing = Objects.requireNonNull(collectRing, "collectRing");
        aniRing = new CdObjectAnimation.AnimationTable(assets.rings().animationTable(), 0, assets.rings().animations().size());
    }

    public Assets assets() { return assets; }

    /** One ObjLostRing dispatch for the whole-pool dynamic slot currently holding ID $11. */
    public Result run(int wholeIndex, Inputs inputs, Ports ports) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(ports, "ports");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjLostRing $11");
        }
        if (inputs.runObjectsD7() != RUN_OBJECTS_LAST_WHOLE - wholeIndex) {
            throw new IllegalArgumentException("RunObjects d7 " + inputs.runObjectsD7() + " is not the physical counter "
                    + (RUN_OBJECTS_LAST_WHOLE - wholeIndex) + " of whole slot " + wholeIndex);
        }
        if (inputs.zoneByte() != 0) {
            throw new UnsupportedLostRing(UnsupportedBoundary.ZONE_DOMAIN, wholeIndex,
                    "zone $" + Integer.toHexString(inputs.zoneByte()) + " is not the bound R11A program");
        }
        var effects = new ArrayList<Effect>();
        switch (state.routine()) {                                                 // MOVE.B oRoutine(a0),d0 / JMP .Index
            case 0 -> {
                init(wholeIndex, ports, effects);
                effects.add(new CallReturned(NativeCall.INIT));
                main(wholeIndex, inputs, ports, effects);                          // .DidInit falls into ObjLostRing_Main
            }
            case 2 -> main(wholeIndex, inputs, ports, effects);
            case 4 -> {
                collect(wholeIndex, ports, effects);
                effects.add(new CallReturned(NativeCall.COLLECT));
                sparkle(wholeIndex, effects);                                      // falls into ObjLostRing_Sparkle
            }
            case 6 -> sparkle(wholeIndex, effects);
            case 8 -> delete(wholeIndex, DeleteEntry.DESTROY_ROUTINE, effects);    // ObjLostRing_Destroy: BRA.W DeleteObject
            default -> throw new UnsupportedLostRing(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " is not an ObjLostRing_Index entry");
        }
        return new Result(pool.state(wholeIndex), effects);
    }

    /**
     * ObjLostRing_Init (Ring/Main.asm:332-405). The spawner is ring 0 (MOVEA.L a0,a1 / BRA.S .DoInit); later rings use
     * the first oID-zero dynamic slot from whole 32 (FindObjSlot), with no reservation, clear or retry.
     */
    private void init(int whole, Ports ports, List<Effect> effects) {
        var pool = runtime.pool();
        int d5 = Objects.requireNonNull(ports.ringGlobals().read(), "ring globals").rings(); // MOVEQ #0,d5 / MOVE.W rings,d5
        if (d5 >= 0x20) { d5 = 0x20; }                                             // MOVEQ #$20,d0 / CMP.W d0,d5 / BCS.S .NoCap
        d5 = (d5 - 1) & 0xFFFF;                                                    // SUBQ.W #1,d5: rings 0 gives $FFFF, no guard
        int d4 = 0x288;                                                            // MOVE.W #$288,d4
        int d2 = 0;                                                                // The first ring always loads d2/d3: d4 = $288 is
        int d3 = 0;                                                                // positive, and only .SetVel reuses them.
        int target = whole;
        int ring = 0;
        while (true) {
            // .DoInit (:352-375). Source operands come from the live a0 on every ring.
            var spawner = pool.state(whole);
            pool.write(pool.state(target)
                    .moveByte(ID, OBJECT_ID)                                       // MOVE.B #$11,oID(a1)
                    .addByte(ROUTINE, 2)                                           // ADDQ.B #2,oRoutine(a1): a dirty routine keeps +2
                    .moveByte(Y_RADIUS, 8)                                         // MOVE.B #8,oYRadius(a1)
                    .moveByte(X_RADIUS, 8)                                         // MOVE.B #8,oXRadius(a1)
                    .moveWord(X, spawner.centreX())                                // MOVE.W oX(a0),oX(a1): fractions stay
                    .moveWord(Y, spawner.centreY())                                // MOVE.W oY(a0),oY(a1)
                    .moveLong(MAPPINGS, assets.mappingsAddress())                  // MOVE.L #MapSpr_Ring,oMap(a1)
                    .moveByte(SUBTYPE2, spawner.subtype2())                        // MOVE.B oSubtype2(a0),oSubtype2(a1)
                    .moveWord(TILE, assets.baseTileWord())                         // MOVE.W #$A7AE,oTile(a1)
                    .moveByte(PRIORITY, 3)                                         // MOVE.B #3,oPriority(a1)
                    // CMPI.B #6,zone / BNE.S .NotMMZ: run() admits only zone 0, so the MMZ writes never execute.
                    .moveByte(SPRITE_FLAGS, 0x04)                                  // .NotMMZ: MOVE.B #%00000100,oSprFlags(a1)
                    .moveByte(COLLISION_TYPE, 0x47)                                // MOVE.B #$47,oColType(a1)
                    .moveByte(WIDTH, 8)                                            // MOVE.B #8,oWidth(a1)
                    .moveByte(Y_RADIUS, 8));                                       // MOVE.B #8,oYRadius(a1)
            var spill = Objects.requireNonNull(ports.spillGlobals().read(), "spill globals");
            ports.spillGlobals().write(new CdRingSpillGlobals(0xFF, spill.accumWord(), spill.frameByte())); // MOVE.B #$FF,ringLossAnimTimer
            if ((d4 & 0x8000) == 0) {                                              // TST.W d4 / BMI.S .SetVel
                var sine = math.calcSine(d4);                                      // MOVE.W d4,d0 / JSR CalcSine: low byte only
                int count = (d4 >>> 8) & 63;                                       // MOVE.W d4,d2 / LSR.W #8,d2; ASL uses it mod 64
                d2 = aslWord(sine.sine(), count);                                  // ASL.W d2,d0 / MOVE.W d0,d2
                d3 = aslWord(sine.cosine(), count);                                // ASL.W d2,d1 / MOVE.W d1,d3
                int low = (d4 & 0xFF) + 0x10;                                      // ADDI.B #$10,d4
                d4 = (d4 & 0xFF00) | (low & 0xFF);
                if (low > 0xFF) {                                                  // BCC.S .SetVel: the byte carried
                    d4 -= 0x80;                                                    // SUBI.W #$80,d4
                    if (d4 < 0) { d4 = 0x288; }                                    // BCC.S .SetVel / MOVE.W #$288,d4 on a borrow
                }
            }
            pool.write(pool.state(target).moveWord(X_VELOCITY, d2)               // .SetVel: MOVE.W d2,oXVel(a1)
                    .moveWord(Y_VELOCITY, d3));                                    // MOVE.W d3,oYVel(a1)
            effects.add(new RingInitialized(ring, target, (short) d2, (short) d3));
            d2 = (-d2) & 0xFFFF;                                                   // NEG.W d2
            d4 = (-d4) & 0xFFFF;                                                   // NEG.W d4
            d5 = (d5 - 1) & 0xFFFF;                                                // DBF d5,.Loop
            if (d5 == 0xFFFF) { break; }
            var found = pool.findObjSlot();                                        // .Loop: BSR.W FindObjSlot
            ring++;
            if (found.isEmpty()) {                                                 // BNE.W .DidInit: a1 is not written
                effects.add(new SlotUnavailable(ring));
                break;
            }
            target = found.getAsInt();
        }
        // .DidInit (:400-405), after all allocation or its failure: MOVE.W #0,rings / MOVE.B #$80,updateHUDRings /
        // MOVE.B #0,livesFlags. Lives, HUD lives, time stop and the ring animation bytes are not written.
        var g = Objects.requireNonNull(ports.ringGlobals().read(), "ring globals");
        ports.ringGlobals().write(new CdRingGlobals(0, g.lives(), 0, 0x80, g.updateHudLives(),
                g.timeStopTimer(), g.ringAnimationTimer(), g.ringAnimationFrame()));
        effects.add(new SoundRequest(Sound.FM_RINGLOSS));                         // MOVE.W #FM_RINGLOSS,d0 / JSR PlayFMSound
    }

    /** ObjLostRing_Main (Ring/Main.asm:410-435). Time stop is not tested here, unlike ObjRing_Main. */
    private void main(int whole, Inputs in, Ports ports, List<Effect> effects) {
        var pool = runtime.pool();
        int frame = Objects.requireNonNull(ports.spillGlobals().read(), "spill globals").frameByte();
        var state = pool.state(whole).moveByte(MAP_FRAME, frame);                 // MOVE.B ringLossAnimFrame,oMapFrame(a0)
        pool.write(state);
        state = objMove(state).addWord(Y_VELOCITY, 0x18);                         // BSR.W ObjMove / ADDI.W #$18,oYVel(a0)
        pool.write(state);
        if ((state.word(Y_VELOCITY) & 0x8000) == 0) {                             // BMI.S .ChkDel tests the ADDI result
            int phase = ((in.levelVIntCounter() & 0xFF) + (in.runObjectsD7() & 0xFF)) & 3; // MOVE.B levelVIntCounter+3,d0
            if (phase == 0) {                                                      // ADD.B d7,d0 / ANDI.B #3,d0 / BNE.S .ChkDel
                var floor = CdObjectCollisionCheck.objGetFloorDist(terrain, state);  // JSR ObjGetFloorDist
                effects.add(new GlobalWrite(GlobalField.PRIMARY_ANGLE, floor.primaryAngle()));
                if (floor.distance() < 0) {                                        // TST.W d1 / BPL.S .ChkDel
                    state = state.addWord(Y, floor.distance());                    // ADD.W d1,oY(a0): integer word only
                    int yVelocity = state.word(Y_VELOCITY);
                    int quarter = ((short) yVelocity) >> 2;                        // MOVE.W oYVel(a0),d0 / ASR.W #2,d0
                    state = state.moveWord(Y_VELOCITY, -(yVelocity - quarter));    // SUB.W d0,oYVel(a0) / NEG.W oYVel(a0)
                    pool.write(state);
                }
            }
        }
        effects.add(new CallReturned(NativeCall.MAIN));
        // .ChkDel (:428-435): the timer is read at this cut, after the probe.
        if (Objects.requireNonNull(ports.spillGlobals().read(), "spill globals").timerByte() == 0) { // TST.B / BEQ.S
            delete(whole, DeleteEntry.TIMER_EXPIRED, effects);
            return;
        }
        int limit = (in.bottomBoundWord() + 0xE0) & 0xFFFF;                       // MOVE.W bottomBound.w,d0 / ADDI.W #$E0,d0
        if (limit < state.centreY()) {                                             // CMP.W oY(a0),d0 / BCS.S: unsigned
            delete(whole, DeleteEntry.BELOW_BOTTOM_BOUND, effects);
            return;
        }
        drawObject.drawObject(pool, state.identity());                             // BRA.W DrawObject
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    /**
     * ObjMove (Object Functions.asm:91-128): the old oXVel/oYVel words, sign-extended and shifted left eight, add to
     * the 16:16 position longs with 32-bit wrap. oFlags bit 3 selects the standing-object read, a typed boundary here.
     */
    private static CdObjectState objMove(CdObjectState state) {
        if ((state.flags() & 0x08) != 0) {                                         // BTST #3,oFlags(a0) / BEQ.S .NotOnObj
            throw new UnsupportedLostRing(UnsupportedBoundary.OBJ_MOVE_STANDING_OBJECT, state.identity().wholeIndex(),
                    "oFlags $" + Integer.toHexString(state.flags()) + " reads oPlayerStandObj and that slot's oID");
        }
        int x = state.longBits(X_POSITION) + (state.signedWord(X_VELOCITY) << 8); // EXT.L d0 / ASL.L #8,d0 / ADD.L d0,d2
        int y = state.longBits(Y_POSITION) + (state.signedWord(Y_VELOCITY) << 8); // MOVE.W oYVel(a0),d0 / EXT.L / ASL.L / ADD.L
        return state.moveLong(X_POSITION, x).moveLong(Y_POSITION, y);              // MOVE.L d2,oX(a0) / MOVE.L d3,oY(a0)
    }

    /** ObjLostRing_Collect (Ring/Main.asm:440-444): unlike ObjRing_Collect, no saved-bank member bit is set. */
    private void collect(int whole, Ports ports, List<Effect> effects) {
        var pool = runtime.pool();
        pool.write(pool.state(whole).addByte(ROUTINE, 2)                           // ADDQ.B #2,oRoutine(a0)
                .moveByte(COLLISION_TYPE, 0)                                       // MOVE.B #0,oColType(a0)
                .moveByte(PRIORITY, 1));                                           // MOVE.B #1,oPriority(a0)
        var globals = new CountingGlobals(ports.ringGlobals());
        var requests = new RewardRequests(effects);
        collectRing.collectRing(globals, requests);                                // BSR.W CollectRing
        if (globals.writes != 1 || requests.count != 1) {
            throw new IllegalStateException("CollectRing (Ring/Main.asm:290-312) makes one reward write and one tail-called"
                    + " request; the bound helper made " + globals.writes + " write(s) and " + requests.count + " request(s)");
        }
    }

    /** ObjLostRing_Sparkle (Ring/Main.asm:449-452): LEA Ani_Ring,a1 / BSR.W AnimateObject / BRA.W DrawObject. */
    private void sparkle(int whole, List<Effect> effects) {
        var pool = runtime.pool();
        var s = pool.state(whole);
        // $FC advances the routine to 8; the deletion belongs to the NEXT dispatch. Dirty animator bytes are not primed.
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),
                s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2)), aniRing);
        var next = s.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame()).moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2());
        pool.write(next);
        effects.add(new CallReturned(NativeCall.SPARKLE));
        drawObject.drawObject(pool, next.identity());                              // BRA.W DrawObject
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    private void delete(int whole, DeleteEntry entry, List<Effect> effects) {
        runtime.pool().deleteObject(whole);                                        // DeleteObject: all 16 longwords
        effects.add(new Deleted(entry));
    }

    /** ASL.W Dx,Dy: the register count is taken modulo 64, and 16 or more shifts every bit out of the word. */
    private static int aslWord(int value, int count) { return count >= 16 ? 0 : (value << count) & 0xFFFF; }

    /** Pass-through of the caller's one ring-globals store; counts CollectRing's single reward write. */
    private static final class CountingGlobals implements CdRing.GlobalsPort {
        private final CdRing.GlobalsPort store;
        private int writes;
        private CountingGlobals(CdRing.GlobalsPort store) { this.store = store; }
        @Override public CdRingGlobals read() { return store.read(); }
        @Override public void write(CdRingGlobals state) {
            writes++;
            store.write(state);
        }
    }

    /** CollectRing's tail-called requests, recorded in call order; CollectRing never draws. */
    private static final class RewardRequests implements CdRing.SourceCalls {
        private final List<Effect> effects;
        private int count;
        private RewardRequests(List<Effect> effects) { this.effects = effects; }
        @Override public void playFmSound(CdRing.FmSound sound) {
            count++;
            effects.add(new RewardFmRequest(sound));
        }
        @Override public void subCpuCommand(CdRing.SubCpuCommand command) {
            count++;
            effects.add(new RewardSubCpuRequest(command));
        }
        @Override public void drawObject(CdObjectPool pool, int wholeIndex) {
            throw new IllegalStateException("CollectRing (Ring/Main.asm:290-312) has no DrawObject call");
        }
    }
}
