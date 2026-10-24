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
 * ObjFloatBlock ($12): Level/_Objects/Floating Block/Main.asm:7-176 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over the live dynamic pool of one CdObjectPlacementRuntime. The
 * file has no FixBugs conditional; this is the shipped behaviour. One {@link #run} is one RunObjects dispatch of the
 * slot: the routine selected by oRoutine (Init falls into Main, and Main's .StartFall falls into Fall), then the
 * dispatcher's own $280 window (:13-22) over the live slot.
 *
 * <p>Solidity: ObjFloatBlock_TopSolid (:156-165) BSRs .Check and then falls into it, so every use is two
 * TopSolidObject calls with a1 = objPlayerSlot (the player-2 call is commented out at :159-160). For each call the
 * working a0 image is written to the live pool, ONE call cut is captured, the shared pure {@link CdSolidObject} runs,
 * and the Root-owned {@link TopSolidCommit} publishes that Result before the next instruction. The body then requires
 * the published a0/a2 images to be live and threads the object, player and globals into the next call. A dispatch
 * therefore makes no call (Reset), two, or four (the Main pair, then the Fall pair after .StartFall).</p>
 *
 * <p>Drawing and deletion: DrawObject runs through the supplied port after the routine's writes, and any bit-7
 * write it makes is reloaded. Appear/Visible without a standing player, Reset and Fall's deletion never reach it.
 * Fall's JMP DeleteObject (:92) and the window's BHI.W DeleteObject (:21) are plain 64-byte clears: there is no saved
 * flag, CheckObjDespawn or DespawnObject here. After Fall's deletion the window re-reads the zeroed slot (oX 0) and
 * may delete it again.</p>
 *
 * <p>No pool snapshot, Frame, player state, queue, renderer or sound owner is retained, and nothing is keyed on a
 * fixture, frame or route. Unknown routine bytes and Init with a nonzero oSubtype2 stop before any write. Solid,
 * terrain, animation and port exceptions propagate after the already committed source writes; nothing is rolled
 * back.</p>
 */
public final class CdFloatBlock {
    public static final int OBJECT_ID = 0x12;
    /** MOVEQ #5,d0 / JSR SetObjectTileID (:43-44): the ObjectTileIDs selector. */
    public static final int TILE_SELECTOR = 5;

    /** The .Index routines (:26-33) in entry order; Init and .StartFall fall into the next routine. */
    public enum NativeRoutine { INIT, MAIN, FALL, APPEAR, VISIBLE, VANISH, RESET }
    /** Subroutines that returned, in completion order. */
    public enum NativeCall { ANIMATE_OBJECT, DRAW_OBJECT }
    /** The source branch that executed DeleteObject (Object Functions.asm:228-237). */
    public enum DeleteSite {
        /** ObjFloatBlock_Fall: oY below cameraY + $E0, JMP DeleteObject (:88-92). */
        FALL_BELOW_CAMERA,
        /** ObjFloatBlock's window on the live slot: CMPI.W #$280 / BHI.W DeleteObject (:13-21). */
        CHUNK_WINDOW
    }

    /** Ordered, completed metadata. Pool, player, global and sound work is already published: never replay it. */
    public sealed interface Effect permits RoutineEntered, TopSolid, CallReturned, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    /**
     * One completed JMP TopSolidObject with a1 = objPlayerSlot, already published through {@link TopSolidCommit}.
     * Ordinals 2k and 2k + 1 are the BSR .Check and the fall-through of one ObjFloatBlock_TopSolid.
     */
    public record TopSolid(int ordinal, CdSolidObject.Result result) implements Effect {
        public TopSolid {
            Objects.requireNonNull(result, "result");
            if (ordinal < 0 || ordinal > 3) {
                throw new IllegalArgumentException("A dispatch makes at most the Main and Fall TopSolidObject pairs");
            }
            if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                throw new IllegalArgumentException("ObjFloatBlock_TopSolid calls only TopSolidObject");
            }
        }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /** DeleteObject executed: the slot's 64 bytes are zero. */
    public record Deleted(DeleteSite site) implements Effect {
        public Deleted { Objects.requireNonNull(site, "site"); }
    }

    /**
     * Explicit immutable globals at the call cut: the raw timeZone byte (warp bit 7 included; its low seven bits must
     * name a native period) and the pre-scroll foreground cameraX/cameraY words.
     */
    public record Globals(int timeZone, int cameraXWord, int cameraYWord) {
        public Globals {
            byteValue(timeZone, "timeZone");
            wordValue(cameraXWord, "cameraX");
            wordValue(cameraYWord, "cameraY");
            if ((timeZone & 0x7F) > 2) {
                throw new IllegalArgumentException("Native CD period must be 0, 1 or 2");
            }
        }
    }

    /**
     * objPlayerSlot at this dispatch: Root's S2 player projection, the read-only gates and the four read/write
     * globals. These are the FIRST TopSolidObject call's inputs; every later call in the same dispatch receives the
     * previous call's published values instead.
     */
    public record SolidInputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public SolidInputs {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(gates, "gates");
            Objects.requireNonNull(globals, "globals");
        }
    }

    /**
     * Pure declared FloatBlock data: the 74-byte ObjectTileIDs view decoded by the existing tile owner, the six-frame
     * MapSpr_FloatBlock / three-script Ani_FloatBlock visual, and the native long Init stores in oMap. Production
     * obtains it from the authenticated owner through {@link #fromObjectAssets}; a constructed record never
     * authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdAssetBytes objectTileIds, CdObjectVisualAssets visual, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(objectTileIds, "objectTileIds");
            Objects.requireNonNull(visual, "visual");
            if (objectTileIds.length() != 74) {
                throw new IllegalArgumentException("ObjectTileIDs extent");
            }
            if (visual.mappingFrames().size() != 6 || visual.animations().size() != 3) {
                throw new IllegalArgumentException("MapSpr_FloatBlock declares six frames and Ani_FloatBlock three scripts");
            }
        }

        /** Original Word RAM address of the authenticated mapping view; this is the stored native long. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(), assets.floatBlock(),
                    assets.view(CdR11aObjectAssets.Table.FLOAT_BLOCK_MAPPINGS).range().nativeAddress());
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196), reached by JMP from the routine. Its RTS returns into the
     * dispatcher, which then applies the $280 window. The shared CPU queue implements it over the live pool: clear bit
     * 7 of the live slot, then cull/capacity, then append only the physical slot reference. Any bit-7 write is
     * reloaded afterwards. The caller must keep this synchronous port non-reentrant: it must not dispatch a body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /** One completed pure TopSolidObject call: its ephemeral call cut, its input player/globals and its Result. */
    public record TopSolidCall(CdObjectPool.Snapshot callCut, CdSolidObject.Player player, CdSolidObject.Globals globals,
                               CdSolidObject.Result result) {
        public TopSolidCall {
            Objects.requireNonNull(callCut, "callCut");
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(globals, "globals");
            Objects.requireNonNull(result, "result");
        }
    }

    /**
     * Root's synchronous publication of ONE TopSolidObject call, before the body's next instruction:
     * <ol>
     * <li>S4: CAS result.object() and every result.otherSlots() image against callCut, then write them.</li>
     * <li>S3: a guarded patch of exactly result.playerWrites() from result.player() and exactly result.globalWrites()
     * from result.globals(), CAS against call.player() and call.globals().</li>
     * <li>result.fmRequests() in order, to the existing ContactFmSound sink.</li>
     * </ol>
     * It must not retain the cut, reenter this body or dispatch another one. The body checks the S4 images on return.
     */
    @FunctionalInterface
    public interface TopSolidCommit {
        void commit(TopSolidCall call);
    }

    /** Final live image of the slot after the whole dispatch (fully zero after a deletion). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** oRoutine odd or above 12 indexes outside the seven-word .Index (:8-11, 26-33). */
        ROUTINE_DISPATCH,
        /** Init's SetObjectTileID would read another ObjectTileIDs body for a nonzero oSubtype2 (:43-44). */
        TILE_ID_SUBTYPE2
    }

    /** Thrown before any write of the dispatch; the pool holds the last committed source state. */
    public static final class UnsupportedFloatBlock extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedFloatBlock(UnsupportedBoundary boundary, int wholeIndex, String detail) {
            super(boundary + " at dynamic slot " + wholeIndex + ": " + detail);
            this.boundary = boundary;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdSolidObject solid;
    private final TopSolidCommit commit;
    private final DrawObjectCall drawObject;

    /** The runtime supplies the single live pool; production binds {@link CdSolidObject#fromAssets} over the same assets. */
    public CdFloatBlock(CdObjectPlacementRuntime runtime, Assets assets, CdSolidObject solid, TopSolidCommit commit,
                        DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.solid = Objects.requireNonNull(solid, "solid");
        this.commit = Objects.requireNonNull(commit, "commit");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    /** One ObjFloatBlock dispatch for the whole-pool dynamic slot currently holding ID $12. */
    public Result run(int wholeIndex, Globals globals, SolidInputs inputs) {
        Objects.requireNonNull(globals, "globals");
        Objects.requireNonNull(inputs, "solid inputs");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjFloatBlock $12");
        }
        int routine = state.routine();                                    // MOVE.B oRoutine / MOVE.W .Index(pc,d0.w)
        if ((routine & 1) != 0 || routine > 12) {
            throw new UnsupportedFloatBlock(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + routine + " indexes outside ObjFloatBlock .Index");
        }
        if (routine == 0 && state.subtype2() != 0) {
            throw new UnsupportedFloatBlock(UnsupportedBoundary.TILE_ID_SUBTYPE2, wholeIndex,
                    "oSubtype2 $" + Integer.toHexString(state.subtype2()) + " selects an unreviewed ObjectTileIDs word");
        }
        var c = new Cursor(pool, state, globals, inputs);
        switch (routine) {                                                // JSR .Index(pc,d0.w)
            case 0 -> { init(c); main(c); }                               // Init falls into ObjFloatBlock_Main (:48-54)
            case 2 -> main(c);
            case 4 -> fall(c);
            case 6 -> appear(c);
            case 8 -> visible(c);
            case 10 -> vanish(c);
            case 12 -> reset(c);
        }
        chunkWindow(c);
        return new Result(pool.state(wholeIndex), c.effects);
    }

    /** ObjFloatBlock_Init (:39-48); no animator, velocity, fraction or collision byte is written. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        // MOVEQ #5,d0 / JSR SetObjectTileID (Object Tile ID Set.asm:7-17): selector 5 at this slot's oSubtype2 (0).
        int tile = CdR11aObjectAssets.decodeTileWord(assets.objectTileIds().copy(), TILE_SELECTOR, c.state.subtype2());
        c.state = c.state.addByte(ROUTINE, 2)                             // ADDQ.B #2,oRoutine(a0)
                .moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)        // ORI.B #4: flips and dirty bits stay
                .moveLong(MAPPINGS, assets.mappingsAddress())             // MOVE.L #MapSpr_FloatBlock,oMap(a0)
                .moveWord(TILE, tile)
                .moveByte(PRIORITY, 1)
                .moveByte(WIDTH, 0x0C)
                .moveByte(Y_RADIUS, 0x0C)
                .moveByte(MAP_FRAME, 5);
    }

    /** ObjFloatBlock_Main (:54-78). */
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        topSolid(c);                                                      // BSR.W ObjFloatBlock_TopSolid
        int timeZone = c.globals.timeZone();
        if (timeZone == 0) {                                              // TST.B timeZone / BEQ.S .Draw: the whole byte
            draw(c);
            return;
        }
        if (timeZone != 2) {                                              // CMPI.B #TIME_FUTURE,timeZone / BNE.S .Appear
            c.state = c.state.moveByte(MAP_FRAME, 0);                     // .Appear: the invisible frame, every frame
            if ((c.state.flags() & 8) != 0) {                             // BTST #3,oFlags(a0) / BEQ.S .Draw
                c.state = c.state.moveByte(ROUTINE, 6).moveByte(ANIMATION, 1);
            }
            draw(c);
            return;
        }
        if ((c.state.flags() & 8) == 0) {                                 // .Fall: BTST #3 / BNE.S .StartFall / BRA.S .Draw
            draw(c);
            return;
        }
        c.state = c.state.addByte(ROUTINE, 2);                            // .StartFall: ADDQ.B #2, then falls into Fall
        fall(c);
    }

    /** ObjFloatBlock_Fall (:84-95). */
    private void fall(Cursor c) {
        c.enter(NativeRoutine.FALL);
        topSolid(c);
        c.state = c.state.addWord(Y, 2);                                  // ADDQ.W #2,oY(a0): the fraction is kept
        int bottom = (c.globals.cameraYWord() + 0xE0) & 0xFFFF;           // MOVE.W cameraY,d0 / ADDI.W #$E0,d0
        if (bottom >= c.state.centreY()) {                                // CMP.W oY(a0),d0 / BCC.S .Draw: unsigned
            draw(c);
            return;
        }
        delete(c, DeleteSite.FALL_BELOW_CAMERA);                          // JMP DeleteObject
    }

    /** ObjFloatBlock_Appear (:101-112). */
    private void appear(Cursor c) {
        c.enter(NativeRoutine.APPEAR);
        topSolid(c);
        if ((c.state.flags() & 8) == 0) {                                 // BTST #3,oFlags(a0) / BNE.S .Animate
            c.state = c.state.moveByte(ROUTINE, 2);                       // MOVE.B #2,oRoutine(a0) / RTS
            c.publish();
            return;
        }
        animate(c);
        draw(c);
    }

    /** ObjFloatBlock_Visible (:118-131): oAnim is written before the solidity call. */
    private void visible(Cursor c) {
        c.enter(NativeRoutine.VISIBLE);
        c.state = c.state.moveByte(ANIMATION, 0);                         // MOVE.B #0,oAnim(a0)
        topSolid(c);
        if ((c.state.flags() & 8) == 0) {                                 // BTST #3,oFlags(a0) / BNE.S .Animate
            c.state = c.state.addByte(ROUTINE, 2).moveByte(ANIMATION, 2); // ADDQ.B #2,oRoutine / MOVE.B #2,oAnim / RTS
            c.publish();
            return;
        }
        animate(c);
        draw(c);
    }

    /** ObjFloatBlock_Vanish (:137-142): it animates and draws whether or not it is stood on. */
    private void vanish(Cursor c) {
        c.enter(NativeRoutine.VANISH);
        topSolid(c);
        animate(c);
        draw(c);
    }

    /** ObjFloatBlock_Reset (:148-150): no solidity call and no DrawObject. */
    private static void reset(Cursor c) {
        c.enter(NativeRoutine.RESET);
        c.state = c.state.moveByte(ROUTINE, 2);                           // MOVE.B #2,oRoutine(a0) / RTS
        c.publish();
    }

    /**
     * ObjFloatBlock_TopSolid (:156-165): LEA objPlayerSlot,a1 / BSR.W .Check, then the fall-through into .Check.
     * Each .Check loads d3/d4 from oX/oY, which SolidObject never reads, and JMPs TopSolidObject.
     */
    private void topSolid(Cursor c) {
        topSolidObject(c);
        topSolidObject(c);
    }

    /** One JMP TopSolidObject: live a0, one ephemeral cut, the pure owner, Root's publication, then the threading. */
    private void topSolidObject(Cursor c) {
        c.publish();                                                      // a0's RAM at the call cut
        var cut = c.pool.capture();
        var result = solid.run(CdSolidObject.Entry.TOP_SOLID_OBJECT, c.state, c.player, c.gates, c.solidGlobals, cut);
        commit.commit(new TopSolidCall(cut, c.player, c.solidGlobals, result));
        if (!c.pool.state(c.wholeIndex).equals(result.object())) {
            throw new IllegalStateException("TopSolidObject commit did not publish a0's final image");
        }
        for (var other : result.otherSlots()) {
            int whole = other.identity().wholeIndex();
            if (!c.pool.state(whole).equals(other)) {
                throw new IllegalStateException("TopSolidObject commit did not publish the a2 image of slot " + whole);
            }
        }
        c.effects.add(new TopSolid(c.ordinal++, result));
        c.state = result.object();
        c.player = result.player();
        c.solidGlobals = result.globals();
    }

    /** LEA Ani_FloatBlock,a1 / BSR.W AnimateObject (Object Animate.asm:14-95); its $FC terminal advances oRoutine. */
    private void animate(Cursor c) {
        var visual = assets.visual();
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        var s = c.state;
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(
                s.unsignedByte(ANIMATION), s.unsignedByte(PREVIOUS_ANIMATION),
                s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(),
                s.routine(), s.unsignedByte(ROUTINE_2)), table);
        c.state = s.moveByte(ANIMATION, out.animationId())
                .moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex())
                .moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame())
                .moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags())
                .moveByte(ROUTINE, out.routine())
                .moveByte(ROUTINE_2, out.routine2());
        c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }

    /** JMP DrawObject after the routine's writes; the live slot, bit 7 included, is reloaded afterwards. */
    private void draw(Cursor c) {
        c.publish();
        drawObject.drawObject(c.pool, c.state.identity());
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        c.state = c.pool.state(c.wholeIndex);
    }

    /** DeleteObject (Object Functions.asm:228-237): 16 MOVE.L #0 stores over the slot, then RTS into the dispatcher. */
    private static void delete(Cursor c, DeleteSite site) {
        c.pool.deleteObject(c.wholeIndex);
        c.effects.add(new Deleted(site));
        c.state = c.pool.state(c.wholeIndex);
    }

    /**
     * ObjFloatBlock's tail (:13-22) after the routine returns: oX of the LIVE slot (zero after Fall's DeleteObject) and
     * cameraX - $80, both ANDI.W #$FF80, then CMPI.W #$280 / BHI.W DeleteObject: unsigned, and equality keeps.
     */
    private static void chunkWindow(Cursor c) {
        int d0 = c.pool.state(c.wholeIndex).centreX() & 0xFF80;
        int d1 = ((c.globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;
        if (((d0 - d1) & 0xFFFF) > 0x80 + (320 + 0x40) + 0x80) {
            delete(c, DeleteSite.CHUNK_WINDOW);
        }
    }

    /** Detached per-dispatch staging. Never retained, exposed or reused after a failure. */
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final Globals globals;
        final CdSolidObject.Gates gates;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        CdSolidObject.Player player;
        CdSolidObject.Globals solidGlobals;
        int ordinal;

        Cursor(CdObjectPool pool, CdObjectState state, Globals globals, SolidInputs inputs) {
            this.pool = pool;
            this.state = state;
            this.globals = globals;
            wholeIndex = state.identity().wholeIndex();
            gates = inputs.gates();
            player = inputs.player();
            solidGlobals = inputs.globals();
        }

        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        /** Make every staged a0 write live before an observable cut or the routine's RTS. */
        void publish() { pool.write(state); }
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }

    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
