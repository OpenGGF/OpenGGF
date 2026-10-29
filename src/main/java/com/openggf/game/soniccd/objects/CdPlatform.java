package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Full native ObjPlatform ($21), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Palmtree Panic/Objects/Platform/Main.asm:7-555. Init RETURNS before Main; every normal
 * routine return calls actual DrawObject, even after deletion. The Main time-stop WORD branches
 * directly to Top and bypasses its anchor tail. No outer visibility, good-future or player gate exists.
 * All Init zone branches select MapSpr_Platform; MapSpr_Platform2 is not selected/admitted here.
 *
 * <p>Scalar sine, raw ObjMove, object ceiling and Top/GetOff algorithms stay with their existing
 * owners. Each actual shared Solid call captures all 96 CURRENT images once, runs the SAME cached
 * runWithPrefix owner once and synchronously commits that genuine outcome. NativeStopped publishes
 * its real prefix and then throws its identical originalException; no d0, sag, timer, Draw or tail is
 * manufactured after it. Subsequent calls use the preceding real published player/globals/images.
 * Metadata describes completed observations only and must never be replayed.
 *
 * <p>Root owns authentic private phase/site/body scope, full source CAS, Frame adoption and FM ledger.
 * These public records enforce component values only. This body checks same-owner source/output
 * images and angle-callback pool nonwrites, but a void port cannot prove private Frame adoption.
 * No direct FM family, Frame import, capture owner, global RAM, callback token or resume cursor is
 * introduced. Only the transient synchronous-reentry guard survives an invocation; it is not retry
 * authority after a failed native visit. Shipped FixBugs=0 behavior belongs to the shared owners.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdPlatform {
    public static final int OBJECT_ID = 0x21;
    /** Native literal MOVE.W #$44BE,oTile at Platform/Main.asm:32, never SetObjectTileID. */
    public static final int TILE_WORD = 0x44BE;

    public enum NativeRoutine { INIT, MAIN }
    public enum NativeCall { OBJ_MOVE, OBJ_GET_CEIL_DIST, DRAW_OBJECT }
    public enum DeleteSite { INIT_CAPACITY, VERTICAL_FALL, ANCHOR_DISTANCE }
    public enum Site {
        TIME_STOP_TOP,
        SUBTYPE_0_TOP, SUBTYPE_1_TOP, SUBTYPE_2_TOP, SUBTYPE_3_TOP, SUBTYPE_4_TOP,
        SUBTYPE_5_STATIONARY_TOP, SUBTYPE_5_TIMER_TOP, SUBTYPE_5_DROP_TOP, SUBTYPE_5_DELETE_GET_OFF,
        SUBTYPE_6_STATIONARY_1_TOP, SUBTYPE_6_MOVE_DOWN_TOP, SUBTYPE_6_STATIONARY_2_TOP,
        SUBTYPE_7_STATIONARY_1_TOP, SUBTYPE_7_TIMER_TOP, SUBTYPE_7_RISING_TOP, SUBTYPE_7_STATIONARY_2_TOP,
        SUBTYPE_8_STATIONARY_1_TOP, SUBTYPE_8_TIMER_TOP, SUBTYPE_8_MOVE_X_TOP, SUBTYPE_8_STATIONARY_2_TOP,
        SUBTYPE_9_STATIONARY_1_TOP, SUBTYPE_9_TIMER_TOP, SUBTYPE_9_MOVE_X_TOP, SUBTYPE_9_STATIONARY_2_TOP,
        ANCHOR_GET_OFF
    }

    /** Ordered completed observations, never requests or capability tokens. */
    public sealed interface Effect permits RoutineEntered, VariantEntered, Solid, SlotFound, ChildInitialized,
            PrimaryAngleCommitted, CeilingReturned, SavedFlagChanged, Deleted, CallReturned { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record VariantEntered(int variant) implements Effect {
        public VariantEntered { Objects.checkIndex(variant, 10); }
    }
    public record Solid(Site site, CdSolidObject.Result result) implements Effect {
        public Solid {
            Objects.requireNonNull(site, "site"); Objects.requireNonNull(result, "result");
            if (result.entry() != entryFor(site)) throw new IllegalArgumentException("Wrong completed Solid entry/site");
        }
    }
    public record SlotFound(int wholeIndex) implements Effect {
        public SlotFound { Objects.checkIndex(wholeIndex - 32, 96); }
    }
    public record ChildInitialized(CdObjectState state) implements Effect {
        public ChildInitialized { Objects.requireNonNull(state, "state"); }
    }
    public record PrimaryAngleCommitted(PrimaryAngleCall call) implements Effect {
        public PrimaryAngleCommitted { Objects.requireNonNull(call, "call"); }
    }
    public record CeilingReturned(CdObjectCollisionCheck.CeilingDistance result) implements Effect {
        public CeilingReturned { Objects.requireNonNull(result, "result"); }
    }
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record Deleted(DeleteSite site) implements Effect {
        public Deleted { Objects.requireNonNull(site, "site"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }

    /** Raw widths only; reads/period/address interpretation remain at their actual native cut. */
    public record Globals(int zoneWord, int timeStopTimerWord, int preScrollCameraXWord,
                          int preScrollCameraYWord, int rawTimeZoneByte) {
        public Globals {
            wordValue(zoneWord); wordValue(timeStopTimerWord); wordValue(preScrollCameraXWord);
            wordValue(preScrollCameraYWord); byteValue(rawTimeZoneByte);
        }
    }
    /** Each component is dereferenced only when reached. This is not duplicated actor/global RAM. */
    public record Inputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) { }

    /** Constructed values authenticate no media. Width/range reads are bounded only when reached. */
    public record Assets(CdObjectVisualAssets visual, int mappingsAddress, CdAssetBytes widths, CdAssetBytes ranges) {
        public Assets {
            Objects.requireNonNull(visual, "visual"); Objects.requireNonNull(widths, "widths"); Objects.requireNonNull(ranges, "ranges");
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner, "owner");
            return new Assets(owner.platform(), owner.view(CdR11aObjectAssets.Table.PLATFORM_MAPPINGS).range().nativeAddress(),
                    owner.view(CdR11aObjectAssets.Table.PLATFORM_WIDTHS).bytes(),
                    owner.view(CdR11aObjectAssets.Table.PLATFORM_RANGES).bytes());
        }

        /**
         * Authenticated original USA Past aggregate. The exact132-byte primary table has three
         * counted frames and no animation; its widths/ranges remain raw four-byte source views.
         * Platform/Main.asm:37-55 selects the same primary root on all native zone branches.
         */
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "owner");
            var identity = Objects.requireNonNull(owner.identity(), "identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R11B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R11B.stage())) {
                throw new IllegalArgumentException("Platform Past assets require authenticated original USA R11B Word RAM");
            }
            var mappings = owner.view(CdR11bAssets.Asset.PLATFORM_MAPPINGS);
            return new Assets(CdObjectVisualAssets.decodeStatic(mappings.bytes().copy(), 3, 57),
                    mappings.range().nativeAddress(), owner.view(CdR11bAssets.Asset.PLATFORM_WIDTHS).bytes(),
                    owner.view(CdR11bAssets.Asset.PLATFORM_RANGES).bytes());
        }
    }

    /** One real helper's borrowed actual full-pool cut and current semantic Frame inputs. */
    public record SolidCall(Site site, CdSolidObject.Entry entry, CdObjectPool.Snapshot actualCut,
                            CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                            CdSolidObject.Outcome outcome) {
        public SolidCall {
            Objects.requireNonNull(site, "site"); Objects.requireNonNull(entry, "entry"); Objects.requireNonNull(actualCut, "actualCut");
            Objects.requireNonNull(expectedPlayer, "expectedPlayer"); Objects.requireNonNull(expectedGlobals, "expectedGlobals");
            Objects.requireNonNull(outcome, "outcome");
            var actualEntry = outcome instanceof CdSolidObject.Completed done ? done.completedResult().entry()
                    : ((CdSolidObject.NativeStopped) outcome).prefix().entry();
            if (entry != entryFor(site) || actualEntry != entry) throw new IllegalArgumentException("Wrong native Solid entry/site/outcome");
        }
    }
    /** Root preflights phase/site/full cut/Frame/masks and prebuilds publication before one actual adoption. */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }

    /** Each synchronous raw primaryAngle byte store, including same-value stores; not a Frame capability. */
    public record PrimaryAngleCall(CdObjectPool.Snapshot actualCut, CdObjectState expectedSource,
                                   int expectedPrimaryAngle, int value) {
        public PrimaryAngleCall {
            Objects.requireNonNull(actualCut, "actualCut"); Objects.requireNonNull(expectedSource, "expectedSource");
            byteValue(expectedPrimaryAngle); byteValue(value);
            if (!actualCut.slots().get(expectedSource.identity().dynamicIndex()).equals(expectedSource)) {
                throw new IllegalArgumentException("Platform angle source is not the supplied cut's slot image");
            }
        }
    }
    @FunctionalInterface public interface PrimaryAngleCommit { void commit(PrimaryAngleCall call); }
    /** Actual source DrawObject; caller uses current images even when ID is zero. */
    @FunctionalInterface public interface DrawObjectCall { void drawObject(CdObjectPool pool, CdObjectState.Identity identity); }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum NativeTable {
        ROUTINE_INDEX, SUBTYPE_INDEX, SUBTYPE6_INDEX, SUBTYPE7_INDEX, SUBTYPE8_INDEX, SUBTYPE9_INDEX,
        WIDTHS, RANGES, SAVED_FLAGS
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, SUBTYPE_DISPATCH, SUBSTATE_DISPATCH, WIDTH_READ, RANGE_READ, SAVED_FLAGS_ADDRESS
    }
    /** Reached finite source/address boundary; preceding native stores stay live. */
    public static final class UnsupportedPlatform extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final NativeTable table;
        private final int byteOffset;
        private UnsupportedPlatform(UnsupportedBoundary boundary, int wholeIndex, NativeTable table, int byteOffset) {
            super(boundary + " at Platform whole slot " + wholeIndex + ", " + table + " byte offset " + byteOffset);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.table = table; this.byteOffset = byteOffset;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public NativeTable table() { return table; }
        public int byteOffset() { return byteOffset; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdPlayerMath math;
    private final CdPlayerTerrain terrain;
    private final CdSolidObject sharedSolid;
    private final SolidCommit solidCommit;
    private final PrimaryAngleCommit primaryAngleCommit;
    private final DrawObjectCall draw;
    private boolean running;

    public CdPlatform(CdObjectPlacementRuntime placement, Assets assets, CdPlayerMath math, CdPlayerTerrain terrain,
                      CdSolidObject sharedSolid, SolidCommit solidCommit, PrimaryAngleCommit primaryAngleCommit, DrawObjectCall draw) {
        this.placement = Objects.requireNonNull(placement, "placement"); this.assets = Objects.requireNonNull(assets, "assets");
        this.math = Objects.requireNonNull(math, "math"); this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.sharedSolid = Objects.requireNonNull(sharedSolid, "sharedSolid"); this.solidCommit = Objects.requireNonNull(solidCommit, "solidCommit");
        this.primaryAngleCommit = Objects.requireNonNull(primaryAngleCommit, "primaryAngleCommit"); this.draw = Objects.requireNonNull(draw, "draw");
    }

    /** Platform/Main.asm:7-18: only byte offsets 0/2; unconditional Draw after a NORMAL routine return. */
    public Result run(int wholeIndex, Globals globals, Inputs inputs) {
        if (running) throw new IllegalStateException("Reentrant ObjPlatform dispatch");
        var pool = placement.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("RunObjects requires ID $21 at slot " + wholeIndex);
        int routine = state.routine();
        if (routine != 0 && routine != 2) throw new UnsupportedPlatform(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, NativeTable.ROUTINE_INDEX, routine);
        running = true;
        try {
            var c = new Cursor(pool, state, inputs);
            if (routine == 0) init(c, globals); else main(c, globals);
            draw.drawObject(pool, c.state.identity());
            c.state = pool.state(wholeIndex); c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
            return new Result(c.state, c.effects);
        } finally { running = false; }
    }

    /** Init:30-81. Every prefix is live before the next reached table/global/allocation read. */
    private void init(Cursor c, Globals globals) {
        c.enter(NativeRoutine.INIT);
        c.write(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveWord(TILE, TILE_WORD).moveByte(PRIORITY, 2)
                .moveWord(PLATFORM_ANCHOR_X, c.state.centreX()).moveWord(PLATFORM_ANCHOR_Y, c.state.centreY())
                .moveWord(PLATFORM_INITIAL_Y, c.state.centreY()));
        int zone = Objects.requireNonNull(globals, "reached zone WORD").zoneWord();
        // Native :37/40/43 redundantly loads the SAME MapSpr_Platform on all three WORD branches.
        int map;
        if (zone == 0) map = assets.mappingsAddress();
        else if (zone == 1) map = assets.mappingsAddress();
        else map = assets.mappingsAddress();
        c.write(c.state.moveLong(MAPPINGS, map));
        int subtype = c.state.subtype(), frame = subtype & 3;
        c.write(c.state.moveByte(MAP_FRAME, frame));
        int width = read(c, assets.widths(), NativeTable.WIDTHS, frame, UnsupportedBoundary.WIDTH_READ);
        c.write(c.state.moveByte(WIDTH, width));
        c.write(c.state.moveByte(Y_RADIUS, 8));
        int range = read(c, assets.ranges(), NativeTable.RANGES, (subtype >>> 2) & 3, UnsupportedBoundary.RANGE_READ);
        c.write(c.state.moveByte(PLATFORM_RANGE, range));
        if (c.state.subtype2() != 0) {
            var found = c.pool.findObjSlot(); // Object Spawner.asm:329-340: ID-only, no reserve/clear.
            if (found.isEmpty()) { destroy(c, globals, DeleteSite.INIT_CAPACITY); return; }
            int whole = found.getAsInt(); c.effects.add(new SlotFound(whole));
            var child = c.pool.state(whole).moveByte(ID, 0x0A); c.pool.write(child);
            child = child.moveWord(X, c.state.centreX()); c.pool.write(child);
            child = child.moveWord(Y, c.state.centreY()); c.pool.write(child);
            child = child.addWord(Y, -0x10); c.pool.write(child);
            child = child.moveByte(SPRING_PARENT_Y_OFFSET, 0xF0); c.pool.write(child);
            child = child.moveWord(SPRING_PARENT, c.state.identity().nativeAddress24()); c.pool.write(child);
            int secondary = c.state.subtype2();
            child = child.moveByte(SUBTYPE, secondary & 2); c.pool.write(child);
            child = child.moveByte(SPRING_PARENT_X_OFFSET, secondary & 0xF8); c.pool.write(child);
            // FindObjSlot success leaves d0.W's upper byte zero: initial ADD.W is +0..248.
            // Spring's LATER .DisplayOnly sign-extends its stored byte instead; do not fix that discontinuity.
            child = child.addWord(X, secondary & 0xF8); c.pool.write(child);
            c.effects.add(new ChildInitialized(child));
        }
        c.write(c.state.addByte(ROUTINE, 2)); // RTS, NOT Main fallthrough (:79-81).
    }

    /** Main:89-118: WORD time-stop direct Top has NO anchor-distance tail. */
    private void main(Cursor c, Globals globals) {
        c.enter(NativeRoutine.MAIN);
        if (Objects.requireNonNull(globals, "reached timeStopTimer WORD").timeStopTimerWord() != 0) {
            solid(c, Site.TIME_STOP_TOP); return;
        }
        int variant = c.state.subtype() >>> 4;
        if (variant >= 10) throw unsupported(c, UnsupportedBoundary.SUBTYPE_DISPATCH, NativeTable.SUBTYPE_INDEX, variant * 2);
        c.effects.add(new VariantEntered(variant));
        switch (variant) {
            case 0 -> subtype0(c);
            case 1 -> subtype1(c);
            case 2, 3 -> subtype2Or3(c, variant == 3);
            case 4 -> subtype4(c, Site.SUBTYPE_4_TOP);
            case 5 -> subtype5(c, globals);
            case 6 -> subtype6(c);
            case 7 -> subtype7(c);
            case 8, 9 -> subtype8Or9(c, variant == 9);
        }
        int difference = ((c.state.word(PLATFORM_ANCHOR_X) & 0xFF80)
                - ((globals.preScrollCameraXWord() - 0x80) & 0xFF80)) & 0xFFFF;
        if (difference > 0x280) {
            solid(c, Site.ANCHOR_GET_OFF);
            destroy(c, globals, DeleteSite.ANCHOR_DISTANCE);
        }
    }

    /** 0X:135-140: increment BYTE before sine; direct Top, inherited XV/YV and no sag update. */
    private void subtype0(Cursor c) {
        c.write(c.state.addByte(PLATFORM_PHASE, 1));
        int displacement = oscillate(c);
        c.write(c.state.moveWord(Y, c.state.word(PLATFORM_ANCHOR_Y) + displacement));
        solid(c, Site.SUBTYPE_0_TOP);
    }
    /** 1X:145-165: sine OLD phase, WORD X, phase++, shifted old sag, full LONG delta ASR.L8. */
    private void subtype1(Cursor c) {
        int oldX = c.state.longBits(X_POSITION), displacement = oscillate(c);
        c.write(c.state.moveWord(X, c.state.word(PLATFORM_ANCHOR_X) + displacement));
        c.write(c.state.addByte(PLATFORM_PHASE, 1));
        sagY(c); setXSpeedAndDrop(c, oldX, Site.SUBTYPE_1_TOP);
    }
    /** 2X/3X:191-215: both sine calls use the SAME incremented phase; only 3X NEG.Ws X. */
    private void subtype2Or3(Cursor c, boolean negateX) {
        int oldX = c.state.longBits(X_POSITION);
        c.write(c.state.addByte(PLATFORM_PHASE, 1));
        int displacement = oscillate(c);
        c.write(c.state.moveWord(Y, c.state.word(PLATFORM_ANCHOR_Y) + displacement));
        displacement = oscillate(c);
        if (negateX) displacement = (short) -displacement;
        c.write(c.state.moveWord(X, c.state.word(PLATFORM_ANCHOR_X) + displacement));
        setXSpeedAndDrop(c, oldX, negateX ? Site.SUBTYPE_3_TOP : Site.SUBTYPE_2_TOP);
    }
    /** 4X:220-226 and shared :164-186. Source Y uses OLD sag before shared Top chooses new sag. */
    private boolean subtype4(Cursor c, Site site) { sagY(c); return dropWhenStoodOn(c, site); }
    private static void sagY(Cursor c) {
        int displacement = (c.state.signedByte(PLATFORM_SAG) >> 1) & 0xFF; // MOVEQ0; MOVE.B; ASR.B, NOT ASR.W.
        c.write(c.state.moveWord(Y, c.state.word(PLATFORM_ANCHOR_Y) + displacement));
    }
    private void setXSpeedAndDrop(Cursor c, int oldFullX, Site site) {
        int delta = c.state.longBits(X_POSITION) - oldFullX; // wrapping LONG subtraction BEFORE ASR.L.
        c.write(c.state.moveWord(X_VELOCITY, delta >> 8));
        dropWhenStoodOn(c, site);
    }
    private boolean dropWhenStoodOn(Cursor c, Site site) {
        int d0 = solid(c, site).d0().orElseThrow(); // NativeStopped never reaches this selection.
        int sag = c.state.unsignedByte(PLATFORM_SAG);
        if (d0 != 0) {
            if (sag < 8) c.write(c.state.addByte(PLATFORM_SAG, 1));
            return true;
        }
        if (sag != 0) c.write(c.state.addByte(PLATFORM_SAG, -1));
        return false;
    }

    /** 5X:231-277. Any nonzero substate runs timer; first contact immediately makes the second Top. */
    private void subtype5(Cursor c, Globals globals) {
        if (c.state.unsignedByte(PLATFORM_SUBSTATE) == 0) {
            if (!subtype4(c, Site.SUBTYPE_5_STATIONARY_TOP)) return;
            c.write(c.state.moveByte(PLATFORM_TIMER, 30));
            c.write(c.state.addByte(PLATFORM_SUBSTATE, 2));
        }
        if (c.state.unsignedByte(PLATFORM_TIMER) != 0) {
            c.write(c.state.addByte(PLATFORM_TIMER, -1)); subtype4(c, Site.SUBTYPE_5_TIMER_TOP); return;
        }
        solid(c, Site.SUBTYPE_5_DROP_TOP);
        int oldYVelocity = c.state.signedWord(Y_VELOCITY);
        c.write(c.state.addLong(Y_POSITION, oldYVelocity << 8));
        if (c.state.word(Y_VELOCITY) < 0x400) c.write(c.state.addWord(Y_VELOCITY, 0x40));
        int threshold = (globals.preScrollCameraYWord() + 0x100) & 0xFFFF;
        if (threshold < c.state.centreY()) {
            solid(c, Site.SUBTYPE_5_DELETE_GET_OFF);
            delete(c, DeleteSite.VERTICAL_FALL); // Direct DeleteObject: no saved-membership clear.
        }
    }

    /** 6X:282-330. Raw byte offset directory; stationary1 may fall straight into MoveDown. */
    private void subtype6(Cursor c) {
        int substate = substate(c, NativeTable.SUBTYPE6_INDEX);
        if (substate == 0) {
            if (!subtype4(c, Site.SUBTYPE_6_STATIONARY_1_TOP)) return;
            c.write(c.state.addByte(PLATFORM_SUBSTATE, 2));
        }
        if (substate == 4) { subtype4(c, Site.SUBTYPE_6_STATIONARY_2_TOP); return; }
        if (c.state.unsignedByte(PLATFORM_PHASE) < 0x40) {
            int displacement = (short) -oscillate(c);
            c.write(c.state.moveWord(Y, c.state.word(PLATFORM_ANCHOR_Y) + displacement));
            c.write(c.state.addByte(PLATFORM_PHASE, 2)); solid(c, Site.SUBTYPE_6_MOVE_DOWN_TOP); return;
        }
        c.write(c.state.moveWord(PLATFORM_ANCHOR_Y, c.state.centreY()));
        c.write(c.state.addByte(PLATFORM_SUBSTATE, 2));
        subtype4(c, Site.SUBTYPE_6_STATIONARY_2_TOP);
    }

    /** 7X:335-395. Real raw ObjMove then SUBQ.W8 before the NO-PRECLEAR object ceiling helper. */
    private void subtype7(Cursor c) {
        int substate = substate(c, NativeTable.SUBTYPE7_INDEX);
        if (substate == 0) {
            if (!subtype4(c, Site.SUBTYPE_7_STATIONARY_1_TOP)) return;
            c.write(c.state.addByte(PLATFORM_SUBSTATE, 2)); c.write(c.state.moveByte(PLATFORM_TIMER, 0x3C));
        }
        if (substate == 4) { subtype4(c, Site.SUBTYPE_7_STATIONARY_2_TOP); return; }
        if (c.state.unsignedByte(PLATFORM_TIMER) != 0) {
            c.write(c.state.addByte(PLATFORM_TIMER, -1)); subtype4(c, Site.SUBTYPE_7_TIMER_TOP); return;
        }
        moveObject(c);
        c.write(c.state.addWord(Y_VELOCITY, -8));
        c.prepareGlobals();
        var cut = c.pool.capture();
        var ceiling = CdObjectCollisionCheck.objGetCeilDist(terrain, c.state, c.solidGlobals.primaryAngle(), value -> primaryAngle(c, value));
        assertSourceCut(c.pool, cut);
        if (ceiling.primaryAngle() != c.solidGlobals.primaryAngle()) throw new IllegalStateException("Ceiling angle stores were not synchronous");
        c.effects.add(new CeilingReturned(ceiling)); c.effects.add(new CallReturned(NativeCall.OBJ_GET_CEIL_DIST));
        if (ceiling.distance() >= 0) { dropWhenStoodOn(c, Site.SUBTYPE_7_RISING_TOP); return; }
        c.write(c.state.addWord(Y, -ceiling.distance()));
        c.write(c.state.moveWord(PLATFORM_ANCHOR_Y, c.state.centreY()));
        c.write(c.state.addByte(PLATFORM_SUBSTATE, 2));
        subtype4(c, Site.SUBTYPE_7_STATIONARY_2_TOP);
    }

    /** 8X/9X:400-523. Same timed horizontal path, word negation only for 9X, no XV reset on stop. */
    private void subtype8Or9(Cursor c, boolean negateX) {
        int substate = substate(c, negateX ? NativeTable.SUBTYPE9_INDEX : NativeTable.SUBTYPE8_INDEX);
        Site stationary1 = negateX ? Site.SUBTYPE_9_STATIONARY_1_TOP : Site.SUBTYPE_8_STATIONARY_1_TOP;
        Site timer = negateX ? Site.SUBTYPE_9_TIMER_TOP : Site.SUBTYPE_8_TIMER_TOP;
        Site moving = negateX ? Site.SUBTYPE_9_MOVE_X_TOP : Site.SUBTYPE_8_MOVE_X_TOP;
        Site stationary2 = negateX ? Site.SUBTYPE_9_STATIONARY_2_TOP : Site.SUBTYPE_8_STATIONARY_2_TOP;
        if (substate == 0) {
            if (!subtype4(c, stationary1)) return;
            c.write(c.state.addByte(PLATFORM_SUBSTATE, 2)); c.write(c.state.moveByte(PLATFORM_TIMER, 0x3C));
        }
        if (substate == 4) { subtype4(c, stationary2); return; }
        if (c.state.unsignedByte(PLATFORM_TIMER) != 0) {
            c.write(c.state.addByte(PLATFORM_TIMER, -1)); subtype4(c, timer); return;
        }
        if (c.state.unsignedByte(PLATFORM_PHASE) >= 0x40) {
            c.write(c.state.moveWord(PLATFORM_ANCHOR_X, c.state.centreX()));
            c.write(c.state.addByte(PLATFORM_SUBSTATE, 2)); subtype4(c, stationary2); return;
        }
        int oldX = c.state.longBits(X_POSITION), displacement = oscillate(c);
        if (negateX) displacement = (short) -displacement;
        c.write(c.state.moveWord(X, c.state.word(PLATFORM_ANCHOR_X) + displacement));
        c.write(c.state.addByte(PLATFORM_PHASE, 1)); sagY(c); setXSpeedAndDrop(c, oldX, moving);
    }

    /** DoOsc:527-535: native CalcSine, MULS.W, ASR.W of LOW product WORD (not ASR.L). */
    private int oscillate(Cursor c) {
        int sine = math.calcSine(c.state.unsignedByte(PLATFORM_PHASE)).sine();
        int lowProduct = (short) (sine * c.state.unsignedByte(PLATFORM_RANGE));
        return lowProduct >> 4;
    }
    private static int substate(Cursor c, NativeTable table) {
        int raw = c.state.unsignedByte(PLATFORM_SUBSTATE);
        if (raw != 0 && raw != 2 && raw != 4) throw unsupported(c, UnsupportedBoundary.SUBSTATE_DISPATCH, table, raw);
        return raw;
    }

    /** ObjMove's ACTUAL selected slot ID/flags. Reserved/outside delegates its existing typed read stop. */
    private static void moveObject(Cursor c) {
        int link = c.state.unsignedByte(STAND_LINK_3D);
        Optional<CdPlayerMovement.RawObjectSupport> support = Optional.empty();
        if ((c.state.flags() & 8) != 0) {
            if (link == 0) {
                var primary = c.player();
                support = Optional.of(new CdPlayerMovement.RawObjectSupport(0, primary.slotId(), primary.actor().motion().flags()));
            } else if (link >= 32 && link < 128) {
                var selected = c.pool.state(link);
                support = Optional.of(new CdPlayerMovement.RawObjectSupport(link, selected.id(), selected.flags()));
            }
            // No default row for 1..31 or 128..255. moveObject reaches rawSupport and throws BEFORE stores.
        }
        var moved = CdPlayerMovement.moveObject(new CdPlayerMovement.RawObjectMotion(c.state.longBits(X_POSITION),
                c.state.longBits(Y_POSITION), c.state.signedWord(X_VELOCITY), c.state.signedWord(Y_VELOCITY),
                c.state.flags(), c.state.unsignedByte(RAW_CONTROL_2C), link), support);
        c.write(c.state.moveLong(X_POSITION, moved.centreX16_16()).moveLong(Y_POSITION, moved.centreY16_16()));
        c.effects.add(new CallReturned(NativeCall.OBJ_MOVE)); // ObjMove NEVER writes stored XV/YV.
    }

    /** A fresh actual cut/current primary before EACH raw store; full pool must remain unchanged. */
    private void primaryAngle(Cursor c, int value) {
        c.prepareGlobals();
        var cut = c.pool.capture(); var call = new PrimaryAngleCall(cut, c.state, c.solidGlobals.primaryAngle(), value);
        c.pool.validateRestore(cut); assertSourceCut(c.pool, cut);
        primaryAngleCommit.commit(call);
        assertSourceCut(c.pool, cut);
        c.solidGlobals = new CdSolidObject.Globals(c.solidGlobals.invincible(), c.solidGlobals.scoreChain(), value, c.solidGlobals.secondaryAngle());
        c.effects.add(new PrimaryAngleCommitted(call));
    }

    /** One cached real helper, one fresh full cut, one synchronous genuine-outcome publication. */
    private CdSolidObject.Result solid(Cursor c, Site site) {
        c.prepareInputs(); var entry = entryFor(site); var cut = c.pool.capture();
        if (!cut.slots().get(c.wholeIndex - 32).equals(c.state)) throw new IllegalStateException("Stale Solid source image");
        var expectedPlayer = c.player; var expectedGlobals = c.solidGlobals;
        var outcome = sharedSolid.runWithPrefix(entry, c.state, expectedPlayer, c.gates, expectedGlobals, cut);
        CdSolidObject.Result completed = null; CdSolidObject.NativeStopped stopped = null;
        CdObjectState object; List<CdObjectState> others; CdSolidObject.Player player; CdSolidObject.Globals globals;
        if (outcome instanceof CdSolidObject.Completed done) {
            completed = done.completedResult(); object = completed.object(); others = completed.otherSlots();
            player = completed.player(); globals = completed.globals();
        } else {
            stopped = (CdSolidObject.NativeStopped) outcome; var prefix = stopped.prefix();
            object = prefix.object(); others = prefix.otherSlots(); player = prefix.player(); globals = prefix.globals();
        }
        validateImages(c, cut, object, others); assertSourceCut(c.pool, cut);
        solidCommit.commit(new SolidCall(site, entry, cut, expectedPlayer, expectedGlobals, outcome));
        assertPublished(c.pool, cut, object, others);
        c.state = c.pool.state(c.wholeIndex); c.player = player; c.solidGlobals = globals;
        if (stopped != null) throw stopped.stop().originalException();
        c.effects.add(new Solid(site, completed)); return completed;
    }
    private static void validateImages(Cursor c, CdObjectPool.Snapshot cut, CdObjectState object, List<CdObjectState> others) {
        c.pool.validateRestore(cut); c.pool.validateWrite(object);
        if (!object.identity().equals(c.state.identity())) throw new IllegalArgumentException("Solid changed a0 physical identity");
        var seen = new HashSet<Integer>(); seen.add(c.wholeIndex);
        for (var other : others) {
            c.pool.validateWrite(other);
            if (!seen.add(other.identity().wholeIndex())) throw new IllegalArgumentException("Duplicate/aliased Solid output slot");
        }
    }
    private static void assertSourceCut(CdObjectPool pool, CdObjectPool.Snapshot cut) {
        for (var source : cut.slots()) if (!source.equals(pool.state(source.identity().wholeIndex()))) {
            throw new IllegalStateException("Callback/helper changed its actual full source pool");
        }
    }
    private static void assertPublished(CdObjectPool pool, CdObjectPool.Snapshot cut, CdObjectState object, List<CdObjectState> others) {
        for (var source : cut.slots()) {
            int whole = source.identity().wholeIndex(); CdObjectState expected = source;
            if (whole == object.identity().wholeIndex()) expected = object;
            else for (var other : others) if (whole == other.identity().wholeIndex()) { expected = other; break; }
            if (!expected.equals(pool.state(whole))) throw new IllegalStateException("Solid commit output image mismatch at slot " + whole);
        }
    }
    private static CdSolidObject.Entry entryFor(Site site) {
        return switch (site) {
            case ANCHOR_GET_OFF, SUBTYPE_5_DELETE_GET_OFF -> CdSolidObject.Entry.GET_OFF_OBJECT;
            default -> CdSolidObject.Entry.TOP_SOLID_OBJECT;
        };
    }

    /** Destroy:541-555. Raw linear address; no timeZone mask, warp direction, clamp or period remap. */
    private void destroy(Cursor c, Globals globals, DeleteSite site) {
        int saved = c.state.savedFlagsId();
        if (saved != 0) {
            int index = 2 + 3 * saved + Objects.requireNonNull(globals, "reached raw timeZone BYTE").rawTimeZoneByte();
            if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
                throw unsupported(c, UnsupportedBoundary.SAVED_FLAGS_ADDRESS, NativeTable.SAVED_FLAGS, index);
            }
            // Existing public bank owner accepts canonical ID/0..2. This is ONLY an EXACT addressed-byte
            // adapter: 2+3*id+period == index, even when raw TZ aliases another saved object's bytes.
            int id = (index - 2) / 3, period = (index - 2) % 3;
            int previous = placement.savedFlag(id, period);
            placement.bclrSavedFlag(id, period, CdObjectPlacementRuntime.SavedBit.LOADED);
            c.effects.add(new SavedFlagChanged(index, previous, placement.savedFlag(id, period)));
        }
        delete(c, site);
    }
    private static void delete(Cursor c, DeleteSite site) {
        c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted(site));
    }
    private static int read(Cursor c, CdAssetBytes bytes, NativeTable table, int at, UnsupportedBoundary boundary) {
        if (at < 0 || at >= bytes.length()) throw unsupported(c, boundary, table, at);
        return bytes.unsignedByte(at);
    }
    private static UnsupportedPlatform unsupported(Cursor c, UnsupportedBoundary boundary, NativeTable table, int at) {
        return new UnsupportedPlatform(boundary, c.wholeIndex, table, at);
    }

    /** Invocation-local borrowed values only; no independent primary/global/snapshot/capture owner. */
    private static final class Cursor {
        final CdObjectPool pool; final int wholeIndex; final Inputs inputs; final List<Effect> effects = new ArrayList<>();
        CdObjectState state; CdSolidObject.Player player; CdSolidObject.Gates gates; CdSolidObject.Globals solidGlobals;
        Cursor(CdObjectPool pool, CdObjectState state, Inputs inputs) {
            this.pool = pool; this.state = state; this.inputs = inputs; wholeIndex = state.identity().wholeIndex();
        }
        void write(CdObjectState next) { pool.write(next); state = next; }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        CdSolidObject.Player player() {
            if (player == null) player = Objects.requireNonNull(Objects.requireNonNull(inputs, "reached primary inputs").player(), "reached primary player");
            return player;
        }
        void prepareGlobals() {
            if (solidGlobals == null) solidGlobals = Objects.requireNonNull(Objects.requireNonNull(inputs, "reached primary globals inputs").globals(), "reached Solid globals");
        }
        void prepareInputs() {
            player(); prepareGlobals();
            if (gates == null) gates = Objects.requireNonNull(inputs.gates(), "reached Solid gates");
        }
    }
    private static void byteValue(int value) { if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte"); }
    private static void wordValue(int value) { if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word"); }
}
