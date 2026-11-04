package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/**
 * One selected native ObjGoalPost ($16), not an act-completion controller.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/_Objects/Level End/Main.asm:568-702;
 * ordinary PP LevelEndDespawn alias (:89-98) to Object Spawner.asm:385-446;
 * DrawObject in Object Functions.asm:137-196. No FixBugs alternative occurs here.
 *
 * <p>Actual object prefixes precede synchronous existing-owner ports. Every port carries a fresh
 * full placement/source cut; scalar values are not private invocation authority. Root owns the real
 * Frame/presentation/contact/warp/PLC transaction and authenticates expected values before publication.
 * GoalReached clears direction before this same call's despawn; invocation-local knowledge of that
 * completed store is not another global owner. Effects observe completed work, never deferred commands.
 * Callback failures retain the original exception and live prefix; failed placement verification is
 * suppressed without repair. No fabricated player, queue, clock, art producer or CPU/RAM access exists.</p>
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdGoalPost {
    public static final int OBJECT_ID = 0x16;
    public static final int GOAL_PLC = 0x12;
    public static final int TIDAL_PLC = 0x13;

    /** Common signpost mapping and raw finite goal tile table, not current art or media authentication. */
    public record Assets(CdObjectVisualAssets visual, int mappingsAddress, CdAssetBytes baseTiles) {
        public Assets {
            Objects.requireNonNull(visual); Objects.requireNonNull(baseTiles);
            if (visual.mappingFrames().size() != 6 || !visual.animations().isEmpty())
                throw new IllegalArgumentException("GoalPost uses six static common signpost frames");
            for (int frame = 0; frame < 6; frame++) for (var piece : visual.mappingFrames().get(frame).pieces())
                if (piece.tileOffset() > (frame == 5 ? 24 : 76) - piece.tileCount())
                    throw new IllegalArgumentException("GoalPost mapping exceeds its declared native source tiles");
        }
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "actual Past assets");
            var map = owner.view(CdR11bAssets.Asset.MAP_SPR_GOAL_SIGNPOST);
            return new Assets(CdObjectVisualAssets.decodeStatic(map.bytes().copy(), 6, 76),
                    map.range().nativeAddress(), owner.view(CdR11bAssets.Asset.GOAL_POST_BASE_TILES).bytes());
        }
    }
    /** Original call-cut native widths; time zone and good-future values are not narrowed to fixture policy. */
    public record Globals(int zoneAct, int timeZone, int goodFuture, int timeWarpDirection, int cameraXWord) {
        public Globals { wordValue(zoneAct); byteValue(timeZone); byteValue(goodFuture); byteValue(timeWarpDirection); wordValue(cameraXWord); }
    }
    public enum PlayerField { Y_WORD, X_WORD }
    public enum PlcSite { TIDAL_INIT, GOAL_REACHED }
    public enum NativeRoutine { INIT, MAIN, DONE }
    public record ReadCall(PlayerField field, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public ReadCall { Objects.requireNonNull(field); requireSource(actualCut, expectedSource); }
    }
    /** Scalar source values only: Root prepares and publishes all five owning stores at this reached cut. */
    public record GoalReachedCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource, int cameraXWord) {
        public GoalReachedCall { requireSource(actualCut, expectedSource); wordValue(cameraXWord); }
    }
    public record PlcCall(PlcSite site, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource, int nativeId) {
        public PlcCall {
            Objects.requireNonNull(site); requireSource(actualCut, expectedSource);
            if (nativeId != (site == PlcSite.TIDAL_INIT ? TIDAL_PLC : GOAL_PLC)) throw new IllegalArgumentException("Wrong native GoalPost PLC ID");
        }
    }
    public record PlcHeadCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public PlcHeadCall { requireSource(actualCut, expectedSource); }
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public DrawCall { requireSource(actualCut, expectedSource); }
    }
    @FunctionalInterface public interface PlayerWordRead { int read(ReadCall call); }
    @FunctionalInterface public interface GoalReached { void store(GoalReachedCall call); }
    @FunctionalInterface public interface LoadPlc { void load(PlcCall call); }
    /** Raw LONG bits of the actual first PLC entry, not a decoder/queue service or fabricated readiness flag. */
    @FunctionalInterface public interface PlcHeadRead { int read(PlcHeadCall call); }
    @FunctionalInterface public interface DrawObjectCall { void drawObject(DrawCall call); }

    public sealed interface Effect permits RoutineEntered, PlayerWordReadReturned, PlcHeadReadReturned,
            GoalStored, PlcRequested, DrawReturned, SavedFlagChanged, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { }
    public record PlayerWordReadReturned(PlayerField field, int value) implements Effect { }
    public record PlcHeadReadReturned(int rawLongBits) implements Effect { }
    public record GoalStored(int cameraXWord) implements Effect { }
    public record PlcRequested(PlcSite site, int nativeId) implements Effect { }
    public record DrawReturned() implements Effect { }
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    public record DespawnChecked(boolean deleted) implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(e -> e instanceof DespawnChecked d && d.deleted()); }
    }
    public enum UnsupportedBoundary { ROUTINE_DISPATCH, BASE_TILE_READ, PLC_HEAD_OWNER, SAVED_FLAGS_ADDRESS }
    public static final class UnsupportedGoalPost extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary; private final int wholeIndex, value;
        private UnsupportedGoalPost(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at GoalPost slot " + wholeIndex + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }
    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final PlayerWordRead playerRead;
    private final GoalReached goalReached;
    private final LoadPlc loadPlc;
    private final PlcHeadRead plcHeadRead;
    private final DrawObjectCall drawObject;
    private boolean running;

    /** plcHeadRead may be absent: it stops only the genuinely reached Tidal Tempest wait read. */
    public CdGoalPost(CdObjectPlacementRuntime placement, Assets assets, PlayerWordRead playerRead,
                      GoalReached goalReached, LoadPlc loadPlc, PlcHeadRead plcHeadRead, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.playerRead = Objects.requireNonNull(playerRead); this.goalReached = Objects.requireNonNull(goalReached);
        this.loadPlc = Objects.requireNonNull(loadPlc); this.plcHeadRead = plcHeadRead; this.drawObject = Objects.requireNonNull(drawObject);
    }
    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Reentrant ObjGoalPost dispatch");
        var state = placement.pool().state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Selected slot is not GoalPost $16");
        if (state.routine() != 0 && state.routine() != 2 && state.routine() != 4)
            throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, state.routine());
        running = true;
        try {
            var c = new Cursor(state, globals);
            switch (state.routine()) {
                case 0 -> init(c);
                case 2 -> main(c);
                case 4 -> c.effects.add(new RoutineEntered(NativeRoutine.DONE));
            }
            if ((c.globals().zoneAct() & 0xFF) != 2) draw(c);
            despawn(c); // LevelEndDespawn is unconditional, including after a reached-goal routine4.
            return new Result(c.state, c.effects);
        } finally { running = false; }
    }
    private void init(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.INIT));
        if (c.globals().zoneAct() == 0x201 && c.globals().timeZone() == 1) {
            if (c.state.subtype() == 0) {
                c.write(c.state.moveByte(SUBTYPE, 1)); plc(c, PlcSite.TIDAL_INIT); return;
            }
            if (plcHead(c) != 0) return;
        }
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(PRIORITY, 4).moveLong(MAPPINGS, assets.mappingsAddress())
                .moveByte(X_RADIUS, 16).moveByte(WIDTH, 16).moveByte(Y_RADIUS, 32).moveByte(MAP_FRAME, 5));
        setBaseTile(c); main(c);
    }
    private void setBaseTile(Cursor c) {
        var g = c.globals(); int d0 = (g.zoneAct() & 0xFF00) | ((g.zoneAct() << 7) & 0xFF);
        d0 >>>= 4;
        int d1 = g.timeZone(); if (d1 == 2) d1 = (d1 + g.goodFuture()) & 0xFF;
        d1 = (d1 + d1) & 0xFF;
        d0 = (d0 & 0xFF00) | ((d0 + d1) & 0xFF);
        int at = (short) d0;
        if (at < 0 || at > assets.baseTiles().length() - 2) throw unsupported(UnsupportedBoundary.BASE_TILE_READ, c.whole, at);
        c.write(c.state.moveWord(TILE, assets.baseTiles().unsignedWord(at)));
        if ((g.zoneAct() >>> 8) != 3) c.write(c.state.moveWord(TILE, c.state.word(TILE) | 0x8000));
    }
    private void main(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.MAIN));
        int dy = (readPlayer(c, PlayerField.Y_WORD) - c.state.centreY() + 128) & 0xFFFF;
        if ((short) dy < 0 || dy >= 256) return;
        if (readPlayer(c, PlayerField.X_WORD) < c.state.centreX()) return;
        c.write(c.state.addByte(ROUTINE, 2));
        var cut = capture(c); var call = new GoalReachedCall(cut, c.state, c.globals().cameraXWord());
        readOnly(cut, () -> goalReached.store(call));
        c.clearedDirection = true; c.effects.add(new GoalStored(call.cameraXWord()));
        plc(c, PlcSite.GOAL_REACHED);
    }
    private int readPlayer(Cursor c, PlayerField field) {
        var cut = capture(c); var call = new ReadCall(field, cut, c.state);
        int value = readValue(cut, () -> playerRead.read(call)); wordValue(value);
        c.effects.add(new PlayerWordReadReturned(field, value)); return value;
    }
    private int plcHead(Cursor c) {
        if (plcHeadRead == null) throw unsupported(UnsupportedBoundary.PLC_HEAD_OWNER, c.whole, 0);
        var cut = capture(c); var call = new PlcHeadCall(cut, c.state);
        int value = readValue(cut, () -> plcHeadRead.read(call)); c.effects.add(new PlcHeadReadReturned(value)); return value;
    }
    private void plc(Cursor c, PlcSite site) {
        var cut = capture(c); int id = site == PlcSite.TIDAL_INIT ? TIDAL_PLC : GOAL_PLC;
        var call = new PlcCall(site, cut, c.state, id); readOnly(cut, () -> loadPlc.load(call));
        c.effects.add(new PlcRequested(site, id));
    }
    private void draw(Cursor c) {
        var cut = capture(c); var call = new DrawCall(cut, c.state);
        var expected = CdObjectPool.overlay(cut.pool(), List.of(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() & 0x7F)));
        try { drawObject.drawObject(call); assertPlacement(cut, expected); }
        catch (RuntimeException | Error original) {
            suppress(original, () -> { if (!samePlacement(cut, cut.pool())) assertPlacement(cut, expected); }); throw original;
        }
        c.state = placement.pool().state(c.whole); c.effects.add(new DrawReturned());
    }
    private void despawn(Cursor c) {
        var g = c.globals(); int x = c.state.centreX();
        boolean outside = c.state.signedByte(SPRITE_FLAGS) >= 0
                && (((x & 0xFF80) - ((g.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        boolean deleted = outside;
        if (!deleted) deleted = (g.timeZone() & 0x80) != 0; // DespawnObjTimeWarp is not read on the outside branch.
        if (deleted) {
            int id = c.state.savedFlagsId();
            if (id != 0) {
                int rawTime = g.timeZone(), period = rawTime & 0x7F;
                if ((rawTime & 0x80) != 0) {
                    int direction = c.clearedDirection ? 0 : g.timeWarpDirection();
                    int adjusted = (period - (byte) direction) & 0xFFFF;
                    period = (short) adjusted < 0 ? 0 : adjusted < 3 ? adjusted : 2;
                }
                int index = 2 + (short) ((3 * id + period) & 0xFFFF);
                if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
                    throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c.whole, index);
                int canonicalId = (index - 2) / 3, canonicalPeriod = (index - 2) % 3;
                int previous = placement.savedFlag(canonicalId, canonicalPeriod);
                placement.bclrSavedFlag(canonicalId, canonicalPeriod, CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new SavedFlagChanged(index, previous, placement.savedFlag(canonicalId, canonicalPeriod)));
            }
            placement.pool().deleteObject(c.whole); c.state = placement.pool().state(c.whole);
        }
        c.effects.add(new DespawnChecked(deleted));
    }
    private CdObjectPlacementRuntime.Snapshot capture(Cursor c) {
        var cut = placement.capture(); requireSource(cut, c.state); assertPlacement(cut, cut.pool()); return cut;
    }
    private void readOnly(CdObjectPlacementRuntime.Snapshot cut, Runnable callback) {
        assertPlacement(cut, cut.pool());
        try { callback.run(); assertPlacement(cut, cut.pool()); }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
    }
    private int readValue(CdObjectPlacementRuntime.Snapshot cut, IntSupplier callback) {
        assertPlacement(cut, cut.pool());
        try { int value = callback.getAsInt(); assertPlacement(cut, cut.pool()); return value; }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        var now = placement.capture(); return now.status().equals(cut.status())
                && Arrays.equals(now.savedFlags().copy(), cut.savedFlags().copy()) && now.pool().slots().equals(expectedPool.slots());
    }
    private void assertPlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        placement.validateRestore(cut); placement.pool().validateRestore(expectedPool);
        if (!samePlacement(cut, expectedPool)) throw new IllegalStateException("GoalPost callback changed its actual full placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("GoalPost source is not this full placement capture's actual image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error failed) { if (failed != original) original.addSuppressed(failed); }
    }
    private static UnsupportedGoalPost unsupported(UnsupportedBoundary boundary, int whole, int value) {
        return new UnsupportedGoalPost(boundary, whole, value);
    }
    private final class Cursor {
        final int whole; final Globals sourceGlobals; final List<Effect> effects = new ArrayList<>();
        CdObjectState state; boolean clearedDirection;
        Cursor(CdObjectState state, Globals globals) { this.state = state; whole = state.identity().wholeIndex(); sourceGlobals = globals; }
        Globals globals() { return Objects.requireNonNull(sourceGlobals, "reached native globals"); }
        void write(CdObjectState next) { placement.pool().write(next); state = next; }
    }
    private static void byteValue(int value) { if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected native unsigned BYTE"); }
    private static void wordValue(int value) { if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected native unsigned WORD"); }
}
