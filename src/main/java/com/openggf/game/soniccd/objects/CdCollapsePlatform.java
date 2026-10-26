package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjCollapsePlatform ($20), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Palmtree Panic/Objects/Collapsing Platform/Main.asm:7-308, Data (Present).asm:11-59.
 * A run is one physical RunObjects visit: Init/Main/Delay/Fall, unconditional wrapper DrawObject,
 * then the signed live-routine gate before CheckObjDespawn. Init enters Main in the same visit.
 * There is no outer visibility, timeStop, goodFuture or animation gate and no FixBugs conditional.
 * The shared Solid owner retains shipped FixBugs=0 behavior.
 *
 * <p>Every reached native TopSolidObject/GetOffObject publishes its source prefix, captures the actual
 * entire pool ONCE, invokes the shared owner once, and synchronously commits the genuine Outcome.
 * Completed metadata is descriptive and must never be replayed. NativeStopped is committed and checked
 * exactly like its real prefix, then its identical originalException is thrown; no d0 or successful
 * Result is manufactured for that cut. Subsequent actual calls receive the preceding call's player,
 * globals and current images, not the original inputs or a synthetic overlay.
 *
 * <p>Root owns private-phase/source/site/full-pool/Frame/FM validation and one publication. This body
 * verifies source and output images, but cannot prove Frame adoption or sound publication. It imports
 * no Frame and creates no state owner, art bank, decoder, selector, saved bank or capture partition.
 * The transient guard prevents synchronous reentry through any supplied port. The caller owns terminal
 * failure and completed capture eligibility; a finally-cleared guard is not authority to resume/retry.
 *
 * <p>All source stores preceding a reached read or host failure remain live. FindObjSlot tests actual
 * physical ID bytes, clears/reserves nothing, and precedes every breakup script read. A SingleRow child
 * header is written before that read. Partial children survive later exhaustion/failure. Opaque dirty
 * slot bytes and native fractions are preserved by the existing typed immutable writers.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdCollapsePlatform {
    public static final int OBJECT_ID = 0x20;
    /** Literal MOVE.W #$44BE,oTile, Main.asm:35,182,279; never SetObjectTileID. */
    public static final int TILE_WORD = 0x44BE;
    /** MOVE.W #FM_A3,d0 / JSR PlayFMSound, Main.asm:83-84. */
    public static final int COLLAPSE_FM = 0xA3;

    public enum NativeRoutine { INIT, MAIN, DELAY, FALL, MULTI_ROW, SINGLE_ROW }
    public enum NativeCall { DRAW_OBJECT }
    public enum DeleteSite { FALL_DISTANCE, MULTI_SPLIT, SINGLE_SPLIT }
    public enum Site {
        MAIN_TOP, MAIN_GET_OFF, DELAY_TOP, DELAY_GET_OFF,
        MULTI_CAPACITY_TOP, MULTI_CAPACITY_GET_OFF, SINGLE_CAPACITY_TOP, SINGLE_CAPACITY_GET_OFF
    }

    /** Ordered, completed native observations only; values confer no execution/publication authority. */
    public sealed interface Effect permits RoutineEntered, Solid, SlotFound, ChildInitialized, FmRequested,
            Deleted, CallReturned, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine, "routine"); }
    }
    public record Solid(Site site, CdSolidObject.Result result) implements Effect {
        public Solid {
            Objects.requireNonNull(site, "site"); Objects.requireNonNull(result, "result");
            if (result.entry() != entryFor(site)) throw new IllegalArgumentException("Wrong completed Solid entry/site");
        }
    }
    /** FindObjSlot returned this physical slot; a negative MultiRow cell does not reserve or write it. */
    public record SlotFound(int wholeIndex) implements Effect {
        public SlotFound { Objects.checkIndex(wholeIndex - 32, 96); }
    }
    public record ChildInitialized(CdObjectState state) implements Effect {
        public ChildInitialized { Objects.requireNonNull(state, "state"); }
    }
    public record FmRequested(int soundId) implements Effect {
        public FmRequested {
            if (soundId != COLLAPSE_FM) throw new IllegalArgumentException("Collapse directly requests only FM_A3");
        }
    }
    public record Deleted(DeleteSite site) implements Effect {
        public Deleted { Objects.requireNonNull(site, "site"); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    public record DespawnChecked(boolean deleted) implements Effect { }

    /** Raw widths only. Period/address interpretation is deferred until the native tail reaches that read. */
    public record DespawnGlobals(int timeZone, int timeWarpDirection, int cameraXWord) {
        public DespawnGlobals {
            byteValue(timeZone, "timeZone"); byteValue(timeWarpDirection, "timeWarpDirection");
            wordValue(cameraXWord, "cameraXWord");
        }
    }
    /** First reached primary inputs. Later helper calls use the actual prior outcome's published values. */
    public record Inputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public Inputs {
            Objects.requireNonNull(player, "player"); Objects.requireNonNull(gates, "gates");
            Objects.requireNonNull(globals, "globals");
        }
    }

    /**
     * Declared component data; construction authenticates nothing. Raw sizes are NOT eagerly walked or
     * normalized: signed relative words, header bytes and script reads are bounded only when reached.
     * All four addresses are native full LONG values taken from authenticated views in production.
     */
    public record Assets(CdObjectVisualAssets collapsePlatform1, CdObjectVisualAssets collapsePlatform2,
                         CdObjectVisualAssets collapsePlatform3, CdObjectVisualAssets collapsePlatform4,
                         CdAssetBytes sizes1, CdAssetBytes sizes2,
                         int mappings1, int mappings2, int mappings3, int mappings4) {
        public Assets {
            Objects.requireNonNull(collapsePlatform1, "collapsePlatform1");
            Objects.requireNonNull(collapsePlatform2, "collapsePlatform2");
            Objects.requireNonNull(collapsePlatform3, "collapsePlatform3");
            Objects.requireNonNull(collapsePlatform4, "collapsePlatform4");
            Objects.requireNonNull(sizes1, "sizes1"); Objects.requireNonNull(sizes2, "sizes2");
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets objects) {
            Objects.requireNonNull(objects, "objects");
            return new Assets(objects.collapsePlatform1(), objects.collapsePlatform2(),
                    objects.collapsePlatform3(), objects.collapsePlatform4(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_1).bytes(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_SIZES_2).bytes(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_1).range().nativeAddress(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_2).range().nativeAddress(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_3).range().nativeAddress(),
                    objects.view(CdR11aObjectAssets.Table.COLLAPSE_PLATFORM_MAPPINGS_4).range().nativeAddress());
        }
    }

    /** The actual borrowed cut for ONE native call, with its exact current semantic Frame inputs. */
    public record SolidCall(Site site, CdSolidObject.Entry entry, CdObjectPool.Snapshot actualCut,
                            CdSolidObject.Player expectedPlayer, CdSolidObject.Globals expectedGlobals,
                            CdSolidObject.Outcome outcome) {
        public SolidCall {
            Objects.requireNonNull(site, "site"); Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(actualCut, "actualCut"); Objects.requireNonNull(expectedPlayer, "expectedPlayer");
            Objects.requireNonNull(expectedGlobals, "expectedGlobals"); Objects.requireNonNull(outcome, "outcome");
            CdSolidObject.Entry actualEntry = outcome instanceof CdSolidObject.Completed completed
                    ? completed.completedResult().entry() : ((CdSolidObject.NativeStopped) outcome).prefix().entry();
            if (entry != entryFor(site) || actualEntry != entry) {
                throw new IllegalArgumentException("Wrong native Solid entry/site/outcome");
            }
        }
    }
    /**
     * Synchronous Root port. Preflight actual private phase, physical slot/source/site, all 96 current
     * images, exact Frame expected values, declared outputs/masks and ordered FM requests BEFORE any
     * returned-image publication. Then pool CAS, one Frame adoption and one FM append, with no callback
     * gap or replay. For NativeStopped use its actual Prefix, never a completed-result adapter.
     */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }
    /** Source DrawObject over the actual live pool; its resulting image is always reloaded. */
    @FunctionalInterface public interface DrawObjectCall { void drawObject(CdObjectPool pool, CdObjectState.Identity slot); }
    /** One direct PlayFMSound request at the Main source edge. Shared Solid requests belong to SolidCommit. */
    @FunctionalInterface public interface FmCall { void playFm(int soundId); }

    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state, "state"); effects = List.copyOf(effects); }
        public boolean deleted() {
            return effects.stream().anyMatch(e -> e instanceof Deleted || e instanceof DespawnChecked d && d.deleted());
        }
    }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, SIZE_DIRECTORY_READ, SIZE_HEADER_READ, MULTI_FRAME_READ, SINGLE_FRAME_READ
    }
    public enum NativeTable { ROUTINE_INDEX, SIZES_1, SIZES_2 }
    /** Reached unauthenticated read/dispatch. Earlier stores remain live; there is no default byte or resume. */
    public static final class UnsupportedCollapsePlatform extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;
        private final NativeTable table;
        private final int byteOffset;
        private UnsupportedCollapsePlatform(UnsupportedBoundary boundary, int wholeIndex, NativeTable table, int byteOffset) {
            super(boundary + " at dynamic slot " + wholeIndex + ", " + table + " byte offset " + byteOffset);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.table = table; this.byteOffset = byteOffset;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public NativeTable table() { return table; }
        public int byteOffset() { return byteOffset; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final CdSolidObject sharedSolid;
    private final SolidCommit commit;
    private final DrawObjectCall drawObject;
    private final FmCall fmCall;
    private boolean running;

    public CdCollapsePlatform(CdObjectPlacementRuntime placement, Assets assets, CdSolidObject sharedSolid,
                              SolidCommit commit, DrawObjectCall drawObject, FmCall fmCall) {
        this.placement = Objects.requireNonNull(placement, "placement"); this.assets = Objects.requireNonNull(assets, "assets");
        this.sharedSolid = Objects.requireNonNull(sharedSolid, "sharedSolid"); this.commit = Objects.requireNonNull(commit, "commit");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject"); this.fmCall = Objects.requireNonNull(fmCall, "fmCall");
    }

    /** No blanket Inputs/DespawnGlobals dereference: each belongs only to the source instruction that reads it. */
    public Result run(int wholeIndex, DespawnGlobals globals, Inputs inputs) {
        if (running) throw new IllegalStateException("Reentrant ObjCollapsePlatform dispatch");
        var pool = placement.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("RunObjects requires ID $20 at slot " + wholeIndex);
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 6) {
            throw new UnsupportedCollapsePlatform(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, NativeTable.ROUTINE_INDEX, routine);
        }
        running = true;
        try {
            var c = new Cursor(pool, state, inputs);
            switch (routine) {
                case 0 -> { init(c); main(c); }
                case 2 -> main(c);
                case 4 -> delay(c);
                case 6 -> fall(c);
            }
            // ObjCollapsePlatform:12-16: ALWAYS Draw, including after DeleteObject, then reload signed BYTE.
            drawObject.drawObject(pool, c.state.identity());
            c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT)); c.state = pool.state(wholeIndex);
            if (c.state.signedByte(ROUTINE) < 4) c.effects.add(new DespawnChecked(checkObjDespawn(c.state, globals)));
            return new Result(pool.state(wholeIndex), c.effects);
        } finally { running = false; }
    }

    /** Init:31-69. Every prefix before a reached size read is already in the real pool. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(PRIORITY, 3).moveWord(TILE, TILE_WORD));
        int subtype = c.state.subtype(); boolean single = (subtype & 0x80) != 0;
        NativeTable table = single ? NativeTable.SIZES_2 : NativeTable.SIZES_1;
        CdAssetBytes sizes = single ? assets.sizes2() : assets.sizes1();
        c.write(c.state.moveLong(MAPPINGS, single ? assets.mappings2() : assets.mappings1()));
        if ((subtype & 0x10) != 0) {
            c.write(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 1).moveByte(FLAGS, c.state.flags() | 1));
        }
        int frame = subtype & 15; c.write(c.state.moveByte(MAP_FRAME, frame));
        int at = directory(c, sizes, table, frame);
        int dx = read(c, sizes, table, at, UnsupportedBoundary.SIZE_HEADER_READ);
        dx = ((dx + 1) << 3) & 0xFF;
        c.write(c.state.moveByte(X_RADIUS, dx).moveByte(WIDTH, dx));
        int dy = (byte) read(c, sizes, table, at + 1, UnsupportedBoundary.SIZE_HEADER_READ);
        if (dy < 0) dy = (byte) -dy; // NEG.B: $80 remains negative, then the following BYTE arithmetic wraps.
        dy = ((((dy + 1) & 0xFF) << 3) + 2) & 0xFF;
        c.write(c.state.moveByte(Y_RADIUS, dy));
    }

    /** Main:73-88. Top is unconditional; only its genuine completed MOVEQ d0 selects breakup. */
    private void main(Cursor c) {
        c.enter(NativeRoutine.MAIN);
        if (solid(c, Site.MAIN_TOP).d0().orElseThrow() == 0) return;
        solid(c, Site.MAIN_GET_OFF); // GetOffObject defines NO d0.
        fmCall.playFm(COLLAPSE_FM);
        c.effects.add(new FmRequested(COLLAPSE_FM));
        c.state = c.pool.state(c.wholeIndex); // Native ADDQ and subtype test use current memory after PlayFMSound.
        c.write(c.state.addByte(ROUTINE, 2));
        if (c.state.signedByte(SUBTYPE) >= 0) multiRow(c); else singleRow(c);
    }

    /** Delay:93-109. The transition visit remains Delay and may call Top/GetOff with routine6. */
    private void delay(Cursor c) {
        c.enter(NativeRoutine.DELAY);
        c.write(c.state.addWord(COLLAPSE_DELAY, -1));
        if (c.state.word(COLLAPSE_DELAY) == 0) c.write(c.state.addByte(ROUTINE, 2));
        if (c.state.unsignedByte(COLLAPSE_SOLID_MARKER) == 0) return;
        if (solid(c, Site.DELAY_TOP).d0().orElseThrow() != 0 && c.state.word(COLLAPSE_DELAY) == 0) {
            solid(c, Site.DELAY_GET_OFF);
        }
    }

    /** Fall:114-129. Full LONG Y/add-delta precede the reached primary Y WORD read. */
    private static void fall(Cursor c) {
        c.enter(NativeRoutine.FALL);
        c.write(c.state.addLong(Y_POSITION, c.state.longBits(COLLAPSE_FALL_DELTA))
                .addLong(COLLAPSE_FALL_DELTA, 0x4000));
        int primaryY = c.player().actor().motion().centreY16_16() >>> 16;
        if ((short) (c.state.centreY() - primaryY) > 0x200) delete(c, DeleteSite.FALL_DISTANCE);
    }

    /** MultiRow:134-223. Actual Find BEFORE each signed frame read, including both FF cells. */
    private void multiRow(Cursor c) {
        c.enter(NativeRoutine.MULTI_ROW);
        int subtype = c.state.subtype(); boolean flipped = (subtype & 0x10) != 0;
        int at = directory(c, assets.sizes1(), NativeTable.SIZES_1, subtype & 15);
        int columnsMinus1 = read(c, assets.sizes1(), NativeTable.SIZES_1, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        int startX = (c.state.centreX() + (flipped ? 1 : -1) * (columnsMinus1 << 3)) & 0xFFFF;
        int xStep = flipped ? -16 : 16;
        int rowsMinus1 = read(c, assets.sizes1(), NativeTable.SIZES_1, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        int y = (c.state.centreY() + (rowsMinus1 << 3)) & 0xFFFF;
        int rowDelay = 9;
        c.write(c.state.moveByte(COLLAPSE_SAVED_SOURCE_ID, c.state.id()));
        for (int rowCounter = rowsMinus1; rowCounter >= 0; rowCounter--) {
            int x = startX, timer = rowDelay;
            for (int columnCounter = columnsMinus1; columnCounter >= 0; columnCounter--) {
                var found = c.pool.findObjSlot();
                if (found.isEmpty()) {
                    capacity(c, Site.MULTI_CAPACITY_TOP, Site.MULTI_CAPACITY_GET_OFF);
                    delete(c, DeleteSite.MULTI_SPLIT); return;
                }
                int whole = found.getAsInt(); c.effects.add(new SlotFound(whole));
                int frame = (byte) read(c, assets.sizes1(), NativeTable.SIZES_1, at++, UnsupportedBoundary.MULTI_FRAME_READ);
                if (frame >= 0) {
                    var child = c.pool.state(whole);
                    child = child.moveByte(MAP_FRAME, frame).moveByte(SPRITE_FLAGS, child.spriteFlags() | 4)
                            .moveByte(PRIORITY, 3).moveWord(TILE, TILE_WORD).moveLong(MAPPINGS, assets.mappings3())
                            .moveLong(COLLAPSE_FALL_DELTA, 0x20000)
                            .moveByte(ID, c.state.unsignedByte(COLLAPSE_SAVED_SOURCE_ID)).moveByte(ROUTINE, c.state.routine());
                    if (flipped) child = child.moveByte(SPRITE_FLAGS, child.spriteFlags() | 1).moveByte(FLAGS, child.flags() | 1);
                    if (rowCounter == 0) child = child.moveByte(COLLAPSE_SOLID_MARKER, 0xFF)
                            .moveByte(X_RADIUS, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 9);
                    child = child.moveWord(Y, y).moveWord(X, x).moveWord(COLLAPSE_DELAY, timer);
                    c.pool.write(child); c.effects.add(new ChildInitialized(child));
                }
                // .Endxt is reached even for a negative cell; it consumes position and timer, not the slot.
                x = (x + xStep) & 0xFFFF; timer = (timer + 12) & 0xFFFF;
            }
            y = (y - 16) & 0xFFFF; rowDelay = (rowDelay + 5) & 0xFFFF;
        }
        delete(c, DeleteSite.MULTI_SPLIT); // Complete split goes straight to Delete, with NO cleanup Top.
    }

    /** SingleRow:227-308. Low FIVE bits here; speed is actual post-GetOff primary XV if bit6 selects it. */
    private void singleRow(Cursor c) {
        c.enter(NativeRoutine.SINGLE_ROW);
        int subtype = c.state.subtype();
        int at = directory(c, assets.sizes2(), NativeTable.SIZES_2, subtype & 31);
        int columnsMinus1 = read(c, assets.sizes2(), NativeTable.SIZES_2, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        int dy = read(c, assets.sizes2(), NativeTable.SIZES_2, at++, UnsupportedBoundary.SIZE_HEADER_READ);
        dy = ((((dy + 1) & 0xFF) << 3) + 2) & 0xFF; // No Init absolute-value branch here.
        boolean reverse;
        if ((subtype & 0x40) == 0) {
            reverse = (subtype & 0x20) != 0; // LSL.B #2 N comes from original bit5.
        } else {
            int speed = (short) c.player().actor().motion().xVelocity();
            if ((subtype & 0x20) != 0) speed = (short) -speed; // NEG.W $8000 remains negative.
            reverse = speed < 0;
        }
        int offset = -(columnsMinus1 << 3), xStep = 16, scriptStep = 1;
        if (reverse) { at += columnsMinus1; offset = -offset; xStep = -16; scriptStep = -1; }
        int x = (c.state.centreX() + offset) & 0xFFFF, timer = 9;
        c.write(c.state.moveByte(COLLAPSE_SAVED_SOURCE_ID, c.state.id()));
        for (int count = columnsMinus1; count >= 0; count--) {
            var found = c.pool.findObjSlot();
            if (found.isEmpty()) {
                capacity(c, Site.SINGLE_CAPACITY_TOP, Site.SINGLE_CAPACITY_GET_OFF);
                delete(c, DeleteSite.SINGLE_SPLIT); return;
            }
            int whole = found.getAsInt(); c.effects.add(new SlotFound(whole));
            var child = c.pool.state(whole);
            // Native :278-289 stores precede the actual script read. Dirty X/frame/delay survive a read stop.
            child = child.moveByte(PRIORITY, 3).moveWord(TILE, TILE_WORD).moveByte(SPRITE_FLAGS, child.spriteFlags() | 4)
                    .moveLong(MAPPINGS, assets.mappings4()).moveLong(COLLAPSE_FALL_DELTA, 0x20000)
                    .moveByte(ID, c.state.unsignedByte(COLLAPSE_SAVED_SOURCE_ID)).moveByte(ROUTINE, c.state.routine())
                    .moveWord(Y, c.state.centreY()).moveByte(COLLAPSE_SOLID_MARKER, 0xFF)
                    .moveByte(X_RADIUS, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, dy);
            c.pool.write(child);
            int frame = read(c, assets.sizes2(), NativeTable.SIZES_2, at, UnsupportedBoundary.SINGLE_FRAME_READ);
            child = child.moveByte(MAP_FRAME, frame); at += scriptStep;
            child = child.moveWord(X, x); x = (x + xStep) & 0xFFFF;
            child = child.moveWord(COLLAPSE_DELAY, timer); timer = (timer + 12) & 0xFFFF;
            c.pool.write(child); c.effects.add(new ChildInitialized(child));
        }
        delete(c, DeleteSite.SINGLE_SPLIT);
    }

    private void capacity(Cursor c, Site top, Site getOff) {
        if (solid(c, top).d0().orElseThrow() != 0) solid(c, getOff);
    }

    /** One actual ephemeral full-pool cut, one genuine outcome, synchronous commit, full output-image check. */
    private CdSolidObject.Result solid(Cursor c, Site site) {
        c.prepareInputs();
        var entry = entryFor(site); var cut = c.pool.capture();
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
        validateImages(c, cut, object, others);
        assertSourceCut(c.pool, cut); // Pure shared owner must not have changed ANY live source image.
        commit.commit(new SolidCall(site, entry, cut, expectedPlayer, expectedGlobals, outcome));
        assertPublished(c.pool, cut, object, others); // Includes unchanged ID-zero images and all 64 bytes.
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
        for (var source : cut.slots()) {
            if (!source.equals(pool.state(source.identity().wholeIndex()))) throw new IllegalStateException("Solid changed its live source cut");
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

    /** Signed relative WORD, read raw at the actual index; no eager admission, modulo, padding or fallback. */
    private static int directory(Cursor c, CdAssetBytes bytes, NativeTable table, int index) {
        int at = index + index;
        if (at < 0 || at > bytes.length() - 2) {
            throw new UnsupportedCollapsePlatform(UnsupportedBoundary.SIZE_DIRECTORY_READ, c.wholeIndex, table, at);
        }
        return (short) bytes.unsignedWord(at);
    }
    private static int read(Cursor c, CdAssetBytes bytes, NativeTable table, int at, UnsupportedBoundary boundary) {
        if (at < 0 || at >= bytes.length()) throw new UnsupportedCollapsePlatform(boundary, c.wholeIndex, table, at);
        return bytes.unsignedByte(at);
    }
    private static void delete(Cursor c, DeleteSite site) {
        c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted(site));
    }

    /** Existing saved-bank primitives; Object Spawner.asm:385-446, reached only after the live signed tail gate. */
    private boolean checkObjDespawn(CdObjectState state, DespawnGlobals globals) {
        int x = state.centreX(); // Native reads live X before the sprite sign branch.
        Objects.requireNonNull(globals, "reached CheckObjDespawn globals");
        boolean outside = state.signedByte(SPRITE_FLAGS) >= 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean warp = (globals.timeZone() & 0x80) != 0;
        if (!outside && !warp) return false;
        int savedId = state.savedFlagsId();
        if (savedId != 0) {
            int period = globals.timeZone() & 0x7F;
            if (warp) {
                int remapped = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
                period = (short) remapped < 0 ? 0 : remapped < 3 ? remapped : 2;
            }
            // The existing bounded bank owns reached address rejection. No early raw-period validation/reset.
            placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
        }
        placement.pool().deleteObject(state.identity().wholeIndex()); return true;
    }

    private static CdSolidObject.Entry entryFor(Site site) {
        return switch (site) {
            case MAIN_TOP, DELAY_TOP, MULTI_CAPACITY_TOP, SINGLE_CAPACITY_TOP -> CdSolidObject.Entry.TOP_SOLID_OBJECT;
            case MAIN_GET_OFF, DELAY_GET_OFF, MULTI_CAPACITY_GET_OFF, SINGLE_CAPACITY_GET_OFF -> CdSolidObject.Entry.GET_OFF_OBJECT;
        };
    }

    /** Invocation-local values only; no retained captures, completed outcomes or independent Snapshot owner. */
    private static final class Cursor {
        final CdObjectPool pool; final int wholeIndex; final Inputs inputs;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state; CdSolidObject.Player player; CdSolidObject.Gates gates; CdSolidObject.Globals solidGlobals;
        Cursor(CdObjectPool pool, CdObjectState state, Inputs inputs) {
            this.pool = pool; this.state = state; this.inputs = inputs; wholeIndex = state.identity().wholeIndex();
        }
        void write(CdObjectState next) { pool.write(next); state = next; }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        CdSolidObject.Player player() {
            if (player == null) player = Objects.requireNonNull(inputs, "reached primary player inputs").player();
            return player;
        }
        void prepareInputs() {
            player();
            if (gates == null) gates = inputs.gates();
            if (solidGlobals == null) solidGlobals = inputs.globals();
        }
    }
    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException(name + " must be an unsigned native byte");
    }
    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException(name + " must be an unsigned native word");
    }
}
