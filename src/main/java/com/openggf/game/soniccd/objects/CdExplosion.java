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
 * ObjExplosion ($18): Level/_Objects/Explosion/Main.asm:7-106 at scddisasm
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over the live dynamic pool of one CdObjectPlacementRuntime.
 * One {@link #run} is one RunObjects dispatch of the slot through the three-entry .Index (:45-56): Init (:62-83)
 * falls into Main (:89-92), which animates with Ani_Explosion and jumps to DrawObject through the supplied port;
 * Done (:98-106) either becomes the Flower ($1F) or deletes itself, and never draws.
 *
 * <p>Writes are committed in source order: Init's writes up to the MakePoints call, then the points child at the
 * first free slot (FindObjSlot tests oID only; a child in a later slot executes in this same RunObjects pass, an
 * earlier one waits a frame), then the remaining Init writes with Main's animation, before DrawObject. Every
 * unwritten byte keeps its slot contents, including a converted badnik's oColType until Init clears it and its
 * oFlags/oSavedFlagsID/oVar2A..oVar3F forever. No global, camera, score, sound or player state is read or written;
 * nothing is keyed on a fixture, frame or route coordinate. Completed child writes are never replayed by callers.</p>
 */
public final class CdExplosion {
    public static final int OBJECT_ID = 0x18;
    /** ObjExplosion_Init: MOVE.W #$8680,oTile(a0) - priority, palette 0, tile $680 (Art_Explosions at VRAM $D000). */
    public static final int NATIVE_TILE_WORD = 0x8680;
    /** ObjExplosion_MakePoints: MOVE.B #$1C,oID(a1) (Explosion/Main.asm:33). */
    public static final int POINTS_OBJECT_ID = 0x1C;
    /** ObjExplosion_Done .MakeFlower: MOVE.B #$1F,oID(a0) (Explosion/Main.asm:104). */
    public static final int FLOWER_OBJECT_ID = 0x1F;

    /** INIT precedes MAIN in the same dispatch because Init falls through into ObjExplosion_Main (:83-89). */
    public enum NativeCall { INIT, MAIN, DRAW_OBJECT, DONE }

    /** Ordered return metadata. Pool effects describe completed writes and must not be replayed. */
    public sealed interface Effect permits CallReturned, PointsCreated, PointsSlotUnavailable, BecameFlower, Deleted { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /**
     * ObjExplosion_MakePoints (:14-39): FindObjSlot succeeded and the child's oID, oX, oY and oSubtype writes were
     * committed at that whole-pool slot. The subtype always carries bit 7 (ORI.B #$80,d1).
     */
    public record PointsCreated(int wholeIndex, int subtype) implements Effect {
        public PointsCreated {
            if (wholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || wholeIndex >= CdObjectPool.END_WHOLE_INDEX) {
                throw new IllegalArgumentException("Points children live in dynamic slots 32..127");
            }
            if ((subtype & ~0xFF) != 0 || (subtype & 0x80) == 0) {
                throw new IllegalArgumentException("Points subtype must be a native byte with bit 7 set");
            }
        }
    }
    /** MakePoints: FindObjSlot returned NE (BNE.S ObjExplosion_MakePoints_End); nothing was written. */
    public record PointsSlotUnavailable() implements Effect { }
    /** Done with oRoutine2 zero: oID := $1F and oRoutine := 0 were committed; RTS without DrawObject. */
    public record BecameFlower() implements Effect { }
    /** Done with oRoutine2 nonzero: JMP DeleteObject, the full 64-byte delete with no saved-bank access. */
    public record Deleted() implements Effect { }

    /**
     * Pure declared explosion data: the seven-frame MapSpr_Explosion / two-script Ani_Explosion visual and the native
     * long Init stores in oMap. Production obtains it from the authenticated owner through {@link #fromObjectAssets};
     * a constructed record never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdObjectVisualAssets explosion, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(explosion, "explosion");
            if (explosion.mappingFrames().size() != 7 || explosion.animations().size() != 2) {
                throw new IllegalArgumentException("MapSpr_Explosion/Ani_Explosion declare seven frames and two scripts");
            }
        }

        /** Original Word RAM address of the authenticated mapping view; this is the stored native long. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.explosion(),
                    assets.view(CdR11aObjectAssets.Table.EXPLOSION_MAPPINGS).range().nativeAddress());
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the ObjExplosion_Main cut (Explosion/Main.asm:92): after
     * the dispatch's pool writes. The shared CPU queue implements it over the live pool: clear bit 7 of the live slot
     * first, then cull/capacity, then append only the physical slot reference; it must not test oID. Any bit-7 write
     * it makes is reloaded into the Result; this body never writes back its pre-call image. The caller must keep this
     * synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
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
        /** oRoutine outside {0,2,4} misaligns into or indexes past the three-entry .Index (Explosion/Main.asm:53-56). */
        ROUTINE_DISPATCH
    }

    /** Thrown before any write; the pool holds the last committed source state. */
    public static final class UnsupportedExplosion extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private UnsupportedExplosion(UnsupportedBoundary boundary, int wholeIndex, String detail) {
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

    /** The runtime supplies the single live pool; no admitted branch touches its saved bank. */
    public CdExplosion(CdObjectPlacementRuntime runtime, Assets assets, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    public Assets assets() { return assets; }

    /** One ObjExplosion dispatch for the whole-pool dynamic slot currently holding ID $18. */
    public Result run(int wholeIndex) {
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not ObjExplosion $18");
        }
        var effects = new ArrayList<Effect>();
        switch (state.routine()) {                                             // MOVE.W .Index(pc,d0.w),d0 / JMP .Index(pc,d0.w)
            case 0 -> {
                state = init(state, pool, effects);
                effects.add(new CallReturned(NativeCall.INIT));
                state = animate(state);                                        // falls into ObjExplosion_Main
                pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 2 -> {
                state = animate(state);
                pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 4 -> {
                done(state, pool, effects);
                effects.add(new CallReturned(NativeCall.DONE));
                return new Result(pool.state(wholeIndex), effects);            // RTS / JMP DeleteObject: no DrawObject
            }
            default -> throw new UnsupportedExplosion(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " is not an ObjExplosion .Index entry");
        }
        drawObject.drawObject(pool, state.identity());                         // JMP DrawObject
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        return new Result(pool.state(wholeIndex), effects);
    }

    /**
     * ObjExplosion_Init (Explosion/Main.asm:62-83). The writes before BSR.S ObjExplosion_MakePoints are committed
     * before its FindObjSlot; the rest return uncommitted for Main's animation and the dispatch's single commit.
     */
    private CdObjectState init(CdObjectState state, CdObjectPool pool, List<Effect> effects) {
        var self = state.addByte(ROUTINE, 2)                                   // ADDQ.B #2,oRoutine(a0)
                .moveByte(SPRITE_FLAGS, state.spriteFlags() | 4)               // ORI.B #%100,oSprFlags(a0): flips and bit 7 stay
                .moveByte(PRIORITY, 1)                                         // MOVE.B #1,oPriority(a0)
                .moveWord(TILE, NATIVE_TILE_WORD);                             // MOVE.W #$8680,oTile(a0)
        if (self.unsignedByte(LAYER) != 0) {                                   // TST.B oLayer(a0) / BEQ.S .HighPriority
            self = self.moveWord(TILE, self.word(TILE) & 0x7FFF);              // ANDI.B #$7F,oTile(a0): the high byte only
        }
        self = self.moveLong(MAPPINGS, assets.mappingsAddress());               // .HighPriority: MOVE.L #MapSpr_Explosion,oMap(a0)
        pool.write(self);
        makePoints(self, pool, effects);                                       // BSR.S ObjExplosion_MakePoints
        self = self.moveByte(COLLISION_TYPE, 0)                                // MOVE.B #0,oColType(a0)
                .moveByte(ANIMATION_FRAME, 0)                                  // MOVE.B #0,oAnimFrame(a0)
                .moveByte(ANIMATION_TIME, 0)                                   // MOVE.B #0,oAnimTime(a0)
                .moveWord(ANIMATION_WORD, 0);                                  // MOVE.W #0,oAnim(a0): oAnim and oPrevAnim
        if (self.subtype() != 0) {                                             // TST.B oSubtype(a0) / BEQ.S ObjExplosion_Main
            self = self.moveWord(ANIMATION_WORD, 0x0100);                      // MOVE.W #$100,oAnim(a0): oAnim 1, oPrevAnim 0
        }
        return self;
    }

    /**
     * ObjExplosion_MakePoints (Explosion/Main.asm:14-39). The R3_SEMANTIC_EXPLOSION SpawnPoints label (:21-27)
     * assembles no instruction in this build. The child's other bytes, including fractions, keep slot contents.
     */
    private static void makePoints(CdObjectState self, CdObjectPool pool, List<Effect> effects) {
        if (self.unsignedByte(ROUTINE_2) != 0) { return; }                     // TST.B oExplodeBadnik(a0) / BNE.S MakePoints_End
        int d1 = self.word(EXPLOSION_POINTS_FRAME);                            // MOVEQ #0,d1 / MOVE.W oExplodePoints(a0),d1
        int subtype = ((d1 & 0xFF) >>> 1) | 0x80;                              // LSR.B #1,d1 / ORI.B #$80,d1: the low byte only
        var found = pool.findObjSlot();                                        // JSR FindObjSlot: first oID-zero dynamic slot
        if (found.isEmpty()) {                                                 // BNE.S ObjExplosion_MakePoints_End
            effects.add(new PointsSlotUnavailable());
            return;
        }
        int childWhole = found.getAsInt();
        pool.write(pool.state(childWhole)
                .moveByte(ID, POINTS_OBJECT_ID)                                // MOVE.B #$1C,oID(a1)
                .moveWord(X, self.centreX())                                   // MOVE.W oX(a0),oX(a1)
                .moveWord(Y, self.centreY())                                   // MOVE.W oY(a0),oY(a1)
                .moveByte(SUBTYPE, subtype));                                  // MOVE.B d1,oSubtype(a1)
        effects.add(new PointsCreated(childWhole, subtype));
    }

    /** ObjExplosion_Main (Explosion/Main.asm:89-91): LEA Ani_Explosion,a1 / BSR.W AnimateObject. */
    private CdObjectState animate(CdObjectState state) {
        var visual = assets.explosion();
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

    /** ObjExplosion_Done (Explosion/Main.asm:98-106). */
    private static void done(CdObjectState state, CdObjectPool pool, List<Effect> effects) {
        if (state.unsignedByte(ROUTINE_2) != 0) {                             // TST.B oExplodeBadnik(a0) / BEQ.S .MakeFlower
            pool.deleteObject(state.identity().wholeIndex());                  // JMP DeleteObject
            effects.add(new Deleted());
            return;
        }
        pool.write(state.moveByte(ID, FLOWER_OBJECT_ID)                        // .MakeFlower: MOVE.B #$1F,oID(a0)
                .moveByte(ROUTINE, 0));                                        // MOVE.B #0,oRoutine(a0) / RTS
        effects.add(new BecameFlower());
    }
}
