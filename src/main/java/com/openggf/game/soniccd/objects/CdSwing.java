package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectTileIds;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR12aObjectAssets;
import com.openggf.game.soniccd.level.CdR12aProfile;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * ObjSwing ($29), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Palmtree Panic/Objects/Swing/Main.asm:20-197; Functions (Misc).asm:69-76;
 * Object Functions.asm:137-196,228-237; Object Spawner.asm:329-340.
 * No FixBugs alternative exists in Swing; real shared TOP retains shipped FixBugs=0.
 *
 * <p>The original increasing pool loop owns child dispatch. Allocation touches only the literal
 * native dirty-child fields, including the 256-attempt zero-count case. Motion consumes immutable
 * source WORDs, phase HIGH BYTE, signed part, unsigned MULU and ASR.L then ASR.W. No fitted period,
 * movement/floor/animation shortcut, time-stop, art/PLC/current owner or native picture is supplied.
 * Both actual player slots use the same real selected TOP owner; even ID-zero secondary runs it.
 * The player port publishes genuine completed or stopped shared prefixes, then direct Y WORD only
 * when the native compare reaches it. A stopped prefix rethrows its identical original error.
 * World owns actual Frame/reserved source pairing, nonwrites and ordered shared FM publication.
 *
 * <p>Original operations have transient identity and one positive ordinal. Public records describe
 * observations and grant no publication authority. All expected full96/status/saved images are built
 * and validated before stores/ports. Readonly callbacks retain complete placement on return/throw;
 * original errors survive with verification faults suppressed, never rollback or repair. Effects
 * retain completed scalar values only, never operation records, placement cuts or executable work.
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdSwing {
    public static final int OBJECT_ID = 0x29, TILE_SELECTOR = 0x11, MAPPING_ADDRESS = 0x20ED60;

    /** Finite format data. Raw tile/motion reads remain lazy at their native reached boundaries. */
    public record Assets(CdAssetBytes objectTileIds, CdAssetBytes motion, CdObjectVisualAssets visual,
                         int mappingAddressBits) {
        public Assets {
            Objects.requireNonNull(objectTileIds); Objects.requireNonNull(motion); Objects.requireNonNull(visual);
            if (mappingAddressBits != MAPPING_ADDRESS || visual.mappingTableOffset() != 0
                    || visual.mappings().length() != 30 || visual.mappingFrames().size() != 3
                    || !visual.animations().isEmpty() || visual.animationTable().length() != 0) {
                throw new IllegalArgumentException("Swing needs original static map30/three frames");
            }
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces()) {
                if (piece.tileOffset() > 14 - piece.tileCount()) throw new IllegalArgumentException("Swing mapping exceeds existing fourteen tiles");
            }
        }
        public static Assets fromAct2Assets(CdR12aObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12A object assets"); var identity = Objects.requireNonNull(owner.identity());
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12A
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12A.stage())) {
                throw new IllegalArgumentException("Swing requires original USA R12A Word RAM");
            }
            return new Assets(owner.view(CdR12aProfile.Asset.OBJECT_TILE_IDS), owner.view(CdR12aProfile.Asset.SWING_MOTION),
                    owner.swing(), owner.profile().range(CdR12aProfile.Asset.MAP_SPR_SWING).nativeAddress());
        }
        /** Local selector11 body only; the existing global selector whitelist is unchanged. */
        private int tileWord(CdObjectState source) {
            try {
                int end = CdObjectTileIds.endOfBody(objectTileIds, TILE_SELECTOR);
                int target = (short) objectTileIds.unsignedWord(TILE_SELECTOR * 2);
                int at = target + source.subtype2() * 2;
                if (end - target != 2 || at < target || at > end - 2) {
                    throw unsupported(UnsupportedBoundary.TILE_ID_SUBTYPE2, source, source.subtype2());
                }
                return objectTileIds.unsignedWord(at);
            } catch (IllegalArgumentException | IndexOutOfBoundsException failure) {
                var stopped = unsupported(UnsupportedBoundary.TILE_ID_SUBTYPE2, source, source.subtype2());
                stopped.initCause(failure); throw stopped;
            }
        }
    }
    public record Globals(int cameraXWord, int timeZone, int timeWarpDirection) {
        public Globals { wordValue(cameraXWord); byteValue(timeZone); byteValue(timeWarpDirection); }
    }
    /** Actual selected player and the real other slot's flags at this one reached read. */
    public record PlayerCut(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals,
                            CdSolidObject.PlayerSelection selection) {
        public PlayerCut { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); Objects.requireNonNull(selection); }
    }
    public record PlayerReadCall(CdObjectPlacementRuntime.Snapshot actualPoolCut, CdObjectState expectedSource,
                                 CdSolidObject.PlayerSlot slot) {
        public PlayerReadCall { requireSource(actualPoolCut, expectedSource); Objects.requireNonNull(slot); }
    }
    public record SolidCall(CdObjectPlacementRuntime.Snapshot actualPoolCut, CdObjectState expectedSource,
                            PlayerCut expectedPlayerCut, CdSolidObject.SelectedOutcome genuineSelectedOutcome) {
        public SolidCall { requireSource(actualPoolCut, expectedSource); Objects.requireNonNull(expectedPlayerCut); Objects.requireNonNull(genuineSelectedOutcome); }
    }
    public record PlayerYCall(CdObjectPlacementRuntime.Snapshot actualPoolCut, CdObjectState expectedSource,
                              PlayerCut expectedPlayerCut, int expectedYWord, int value) {
        public PlayerYCall {
            requireSource(actualPoolCut, expectedSource); Objects.requireNonNull(expectedPlayerCut); wordValue(expectedYWord); wordValue(value);
            if (expectedYWord != ((expectedPlayerCut.player().actor().motion().centreY16_16() >>> 16) & 0xFFFF)) {
                throw new IllegalArgumentException("Swing Y call is not its selected actual player WORD");
            }
        }
    }
    public interface PlayerPort {
        PlayerCut read(PlayerReadCall call);
        void commitSolid(SolidCall call);
        void commitY(PlayerYCall call);
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualPoolCut, CdObjectState expectedSource) {
        public DrawCall { requireSource(actualPoolCut, expectedSource); }
    }
    @FunctionalInterface public interface DrawObjectCall { void drawObject(DrawCall call); }

    public enum NativeRoutine { INIT, MAIN }
    public enum NativeCall { CALC_SINE, TOP_SOLID_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN }
    public sealed interface Effect permits RoutineEntered, AllocationAttempt, ChildCreated, MotionWordRead,
            MotionCalculated, SolidReturned, PlayerYStored, CallReturned, SavedFlagChanged, DespawnChecked, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { }
    /** wholeIndex=-1 is the genuine failed attempt; part is the raw consumed BYTE. */
    public record AllocationAttempt(int part, int wholeIndex) implements Effect { }
    public record ChildCreated(int wholeIndex, int part, int mapFrame) implements Effect { }
    public record MotionWordRead(int offset, int value) implements Effect { }
    public record MotionCalculated(int angleByte, int sineWord, int cosineWord, int distanceWord,
                                    int xWord, int yWord) implements Effect { }
    public record SolidReturned(CdSolidObject.PlayerSlot slot, int radius, int d0) implements Effect { }
    public record PlayerYStored(CdSolidObject.PlayerSlot slot, int before, int value) implements Effect { }
    public record CallReturned(NativeCall call) implements Effect { }
    public record SavedFlagChanged(int index, int before, int value) implements Effect { }
    public record DespawnChecked(boolean deleted) implements Effect { }
    public record Deleted() implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum UnsupportedBoundary { ROUTINE_DISPATCH, TILE_ID_SUBTYPE2, MOTION_WORD_READ, SAVED_FLAGS_ADDRESS }
    public static final class UnsupportedSwing extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary; private final int wholeIndex, value;
        private UnsupportedSwing(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Swing whole " + wholeIndex + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }
    public enum StoreKind { SOURCE_STORE, CHILD_STORE, SAVED_FLAG_CLEAR, DELETE }
    public record Publication(long ordinal, StoreKind kind, CdObjectPlacementRuntime.Snapshot actualPoolCut,
                              CdObjectState expectedSource, CdObjectState before, CdObjectState output,
                              int savedByteIndex, int previousValue, int value) { }
    public interface DispatchObserver {
        void beforePublication(Publication call); void afterPublication(Publication call);
        void beforeCall(Object call); void afterCall(Object call);
    }
    public interface ReceiptGuard { void prepared(Object call); void completed(Object call); }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets; private final CdPlayerMath math; private final CdSolidObject sharedSolid;
    private final PlayerPort playerPort; private final DrawObjectCall drawObject;
    private DispatchObserver observer; private ReceiptGuard receiptGuard;
    private boolean running; private Object pendingOperation; private long ordinal, pendingOrdinal;

    public CdSwing(CdObjectPlacementRuntime placement, Assets assets, CdPlayerMath math,
                   CdSolidObject sharedSolid, PlayerPort playerPort, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.math = Objects.requireNonNull(math); this.sharedSolid = Objects.requireNonNull(sharedSolid);
        this.playerPort = Objects.requireNonNull(playerPort); this.drawObject = Objects.requireNonNull(drawObject);
    }
    public static CdSwing fromAct2Assets(CdObjectPlacementRuntime placement, CdR12aObjectAssets owner,
                                         CdPlayerMath math, CdSolidObject sharedSolid, PlayerPort playerPort,
                                         DrawObjectCall drawObject, DispatchObserver observer, ReceiptGuard guard) {
        Objects.requireNonNull(placement); Objects.requireNonNull(owner);
        if (!placement.sameAct2Source(owner)) throw new IllegalArgumentException("Swing placement/assets source differs");
        var body = new CdSwing(placement, Assets.fromAct2Assets(owner), math, sharedSolid, playerPort, drawObject);
        body.observer = Objects.requireNonNull(observer); body.receiptGuard = Objects.requireNonNull(guard); return body;
    }
    public Assets assets() { return assets; }
    public boolean isCurrentCall(Object call) { return call != null && running && call == pendingOperation; }
    public long currentCallOrdinal(Object call) {
        if (!isCurrentCall(call)) throw new IllegalStateException("Not the original current Swing operation");
        return pendingOrdinal;
    }
    public Result run(int wholeIndex, Globals globals) {
        if (running) throw new IllegalStateException("Reentrant ObjSwing dispatch");
        var source = placement.pool().state(wholeIndex);
        if (source.id() != OBJECT_ID) throw new IllegalArgumentException("Selected source is not Swing $29");
        if (source.routine() != 0 && source.routine() != 2) throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, source, source.routine());
        running = true; ordinal = 0;
        try {
            var c = new Cursor(source, globals);
            if (source.routine() == 0) init(c); else main(c);
            draw(c); despawn(c);
            return new Result(placement.pool().state(wholeIndex), c.effects);
        } finally { running = false; pendingOperation = null; pendingOrdinal = 0; }
    }
    private void init(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.INIT));
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveByte(PRIORITY, 3).moveLong(MAPPINGS, assets.mappingAddressBits()));
        c.write(c.state.moveWord(TILE, assets.tileWord(c.state)));
        c.write(c.state.moveByte(WIDTH, 0x18).moveByte(Y_RADIUS, 8));
        if (c.state.unsignedByte(SWING_PART) == 0) {
            int count = c.state.subtype() & 0xF;
            c.write(c.state.moveWord(SWING_CENTRE_X, c.state.centreX()).moveWord(SWING_CENTRE_Y, c.state.centreY()).moveByte(SWING_COUNT, count));
            int attempts = count == 0 ? 256 : count;
            for (int attempt = 0, part = count; attempt < attempts; attempt++, part = (part - 1) & 0xFF) {
                var found = placement.pool().findObjSlot();
                c.effects.add(new AllocationAttempt(part, found.orElse(-1)));
                if (found.isEmpty()) continue;
                // FindObjSlot is ID-only; preserve every dirty byte not written by Main.asm:108-117.
                var child = placement.pool().state(found.getAsInt()).moveByte(ID, OBJECT_ID)
                        .moveWord(SWING_CENTRE_X, c.state.word(SWING_CENTRE_X)).moveWord(SWING_CENTRE_Y, c.state.word(SWING_CENTRE_Y))
                        .moveByte(SUBTYPE, c.state.subtype()).moveByte(SWING_COUNT, count).moveByte(SWING_PART, part).moveByte(MAP_FRAME, 2);
                nativeStore(c, child, StoreKind.CHILD_STORE);
                if (part == count) { child = child.moveByte(MAP_FRAME, 1); nativeStore(c, child, StoreKind.CHILD_STORE); }
                c.effects.add(new ChildCreated(child.identity().wholeIndex(), part, child.unsignedByte(MAP_FRAME)));
            }
        }
        int offset = (c.state.subtype() & 0xF0) >>> 1;
        c.write(c.state.moveWord(SWING_PHASE, motionWord(c, offset)));
        c.write(c.state.moveWord(SWING_STEP, motionWord(c, offset + 2)));
        c.write(c.state.moveWord(SWING_MAX, motionWord(c, offset + 4)));
        c.write(c.state.moveWord(SWING_MIN, motionWord(c, offset + 6)));
        main(c);
    }
    private int motionWord(Cursor c, int at) {
        if (at < 0 || at > 62 || at > assets.motion().length() - 2) throw unsupported(UnsupportedBoundary.MOTION_WORD_READ, c.state, at);
        int value = assets.motion().unsignedWord(at); c.effects.add(new MotionWordRead(at, value)); return value;
    }
    private void main(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.MAIN));
        int oldX = c.state.longBits(X_POSITION), oldY = c.state.longBits(Y_POSITION);
        motion(c);
        if (c.state.unsignedByte(SWING_PART) != c.state.unsignedByte(SWING_COUNT)) return;
        int xv = (c.state.longBits(X_POSITION) - oldX) >> 8, yv = (c.state.longBits(Y_POSITION) - oldY) >> 8;
        c.write(c.state.moveWord(X_VELOCITY, xv).moveWord(Y_VELOCITY, yv));
        solidPlayer(c, CdSolidObject.PlayerSlot.PRIMARY);
        solidPlayer(c, CdSolidObject.PlayerSlot.SECONDARY);
    }
    private void motion(Cursor c) {
        int reverse = c.state.unsignedByte(SWING_REVERSING);
        int angle = (c.state.word(SWING_ANGLE) + (reverse == 0 ? c.state.word(SWING_STEP) : -c.state.word(SWING_STEP))) & 0xFFFF;
        int phase = (c.state.word(SWING_PHASE) + angle) & 0xFFFF;
        if (reverse == 0) reverse = angle == c.state.word(SWING_MAX) ? 0xFF : 0;
        else if (angle == c.state.word(SWING_MIN)) reverse = 0;
        c.write(c.state.moveWord(SWING_ANGLE, angle).moveWord(SWING_PHASE, phase).moveByte(SWING_REVERSING, reverse));
        // MOVE.B at the WORD address reads its HIGH byte on the native big-endian source.
        int angleByte = phase >>> 8; var trig = math.calcSine(angleByte);
        int distance = ((short) c.state.signedByte(SWING_PART) << 4) & 0xFFFF;
        int x = (c.state.word(SWING_CENTRE_X) + scaledProduct(trig.sine(), distance)) & 0xFFFF;
        int y = (c.state.word(SWING_CENTRE_Y) + scaledProduct(trig.cosine(), distance)) & 0xFFFF;
        c.effects.add(new CallReturned(NativeCall.CALC_SINE));
        c.write(c.state.moveWord(Y, y)); c.write(c.state.moveWord(X, x));
        c.effects.add(new MotionCalculated(angleByte, trig.sine() & 0xFFFF, trig.cosine() & 0xFFFF, distance, x, y));
    }
    private static int scaledProduct(int trig, int distance) {
        int product = (int) ((long) (trig & 0xFFFF) * (distance & 0xFFFF));
        int asrLong = product >> 4;
        return (short) ((short) asrLong >> 4);
    }
    private void solidPlayer(Cursor c, CdSolidObject.PlayerSlot slot) {
        c.write(c.state.moveByte(Y_RADIUS, 8));
        int d0 = solid(c, slot);
        if (d0 == 0) { c.write(c.state.moveByte(Y_RADIUS, 12)); d0 = solid(c, slot); }
        if (d0 != 0) {
            c.write(c.state.moveByte(Y_RADIUS, 8));
            var player = readPlayer(c, slot);
            int playerY = (player.player().actor().motion().centreY16_16() >>> 16) & 0xFFFF;
            if (c.state.centreY() >= playerY) {
                int value = (c.state.centreY() - (byte) c.state.unsignedByte(Y_RADIUS)
                        - (byte) player.player().actor().motion().yRadius() + 2) & 0xFFFF;
                var call = new PlayerYCall(capture(c), c.state, player, playerY, value);
                observedCall(call, () -> readOnly(() -> { playerPort.commitY(call); return null; }));
                c.effects.add(new PlayerYStored(slot, playerY, value));
            }
        }
        c.write(c.state.moveByte(Y_RADIUS, 8));
    }
    private PlayerCut readPlayer(Cursor c, CdSolidObject.PlayerSlot slot) {
        var call = new PlayerReadCall(capture(c), c.state, slot);
        var player = observedCall(call, () -> readOnly(() -> Objects.requireNonNull(playerPort.read(call), "actual Swing player cut")));
        if (player.selection().selected() != slot) throw new IllegalArgumentException("Swing read returned a different physical player slot");
        return player;
    }
    private int solid(Cursor c, CdSolidObject.PlayerSlot slot) {
        var player = readPlayer(c, slot); var cut = capture(c); int radius = c.state.unsignedByte(Y_RADIUS);
        var selected = readOnly(() -> sharedSolid.runWithPrefix(CdSolidObject.Entry.TOP_SOLID_OBJECT,
                c.state, player.player(), player.gates(), player.globals(), cut.pool(), player.selection()));
        var output = solidOutput(selected.outcome(), cut.pool());
        if (!output.source().identity().equals(c.state.identity())) throw new IllegalArgumentException("Swing Solid changed a0 physical identity");
        placement.pool().validateRestore(output.pool());
        var expected = new Expected(images(output.pool()), cut.savedFlags().copy());
        var call = new SolidCall(cut, c.state, player, selected);
        observedCall(call, () -> {
            try { playerPort.commitSolid(call); requirePlacement(cut, expected); }
            catch (RuntimeException | Error original) {
                suppress(original, () -> { if (!samePlacement(cut, unchanged(cut))) requirePlacement(cut, expected); }); throw original;
            }
            return null;
        });
        c.state = placement.pool().state(c.whole);
        if (selected.outcome() instanceof CdSolidObject.NativeStopped stopped) throw stopped.stop().originalException();
        var result = ((CdSolidObject.Completed) selected.outcome()).completedResult(); int d0 = result.d0().orElseThrow();
        c.effects.add(new SolidReturned(slot, radius, d0)); c.effects.add(new CallReturned(NativeCall.TOP_SOLID_OBJECT)); return d0;
    }
    private record SolidOutput(CdObjectState source, CdObjectPool.Snapshot pool) { }
    private static SolidOutput solidOutput(CdSolidObject.Outcome outcome, CdObjectPool.Snapshot cut) {
        CdObjectState source; List<CdObjectState> others;
        if (outcome instanceof CdSolidObject.Completed done) { source = done.completedResult().object(); others = done.completedResult().otherSlots(); }
        else { var prefix = ((CdSolidObject.NativeStopped) outcome).prefix(); source = prefix.object(); others = prefix.otherSlots(); }
        var images = new ArrayList<CdObjectState>(); images.add(source); images.addAll(others);
        return new SolidOutput(source, CdObjectPool.overlay(cut, images));
    }
    private void draw(Cursor c) {
        var cut = capture(c); var call = new DrawCall(cut, c.state);
        var expected = overlay(cut, c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() & 0x7F));
        observedCall(call, () -> {
            try { drawObject.drawObject(call); requirePlacement(cut, expected); }
            catch (RuntimeException | Error original) {
                suppress(original, () -> { if (!samePlacement(cut, unchanged(cut))) requirePlacement(cut, expected); }); throw original;
            }
            return null;
        });
        c.state = placement.pool().state(c.whole); c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }
    private void despawn(Cursor c) {
        var g = c.globals();
        boolean outside = c.state.signedByte(SPRITE_FLAGS) >= 0
                && ((((c.state.word(SWING_CENTRE_X) & 0xFF80) - ((g.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280);
        boolean warp = (g.timeZone() & 0x80) != 0, deleted = outside || warp;
        if (deleted) {
            int id = c.state.savedFlagsId();
            if (id != 0) {
                int period = g.timeZone() & 0x7F;
                if (warp) { int adjusted = (period - (byte) g.timeWarpDirection()) & 0xFFFF;
                    period = (short) adjusted < 0 ? 0 : adjusted < 3 ? adjusted : 2; }
                int index = 2 + (short) ((3 * id + period) & 0xFFFF);
                if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c.state, index);
                int canonicalId = (index - 2) / 3, canonicalPeriod = (index - 2) % 3;
                int before = placement.savedFlag(canonicalId, canonicalPeriod);
                nativeSavedClear(c, canonicalId, canonicalPeriod, index, before);
                c.effects.add(new SavedFlagChanged(index, before, before & 0x7F));
            }
            nativeDelete(c); c.state = placement.pool().state(c.whole); c.effects.add(new Deleted());
        }
        c.effects.add(new DespawnChecked(deleted)); c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }
    private void nativeStore(Cursor c, CdObjectState output, StoreKind kind) {
        var cut = capture(c); placement.pool().validateWrite(output); var expected = overlay(cut, output);
        var call = new Publication(nextOrdinal(), kind, cut, c.state, placement.pool().state(output.identity().wholeIndex()), output, -1, -1, -1);
        observedPublication(call, () -> { placement.pool().write(output); requirePlacement(cut, expected); });
    }
    private void nativeSavedClear(Cursor c, int savedId, int period, int index, int before) {
        var cut = capture(c); byte[] saved = cut.savedFlags().copy();
        if ((saved[index] & 0xFF) != before) throw new IllegalStateException("Swing saved byte is not the actual reached cut");
        saved[index] = (byte) (before & 0x7F); var expected = new Expected(images(cut.pool()), saved);
        var call = new Publication(nextOrdinal(), StoreKind.SAVED_FLAG_CLEAR, cut, c.state, null, null, index, before, before & 0x7F);
        observedPublication(call, () -> { placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); requirePlacement(cut, expected); });
    }
    private void nativeDelete(Cursor c) {
        var cut = capture(c); var zeroImages = images(cut.pool());
        zeroImages.set(c.state.identity().dynamicIndex(), new byte[CdObjectState.BYTE_SIZE]);
        var expected = new Expected(zeroImages, cut.savedFlags().copy());
        var call = new Publication(nextOrdinal(), StoreKind.DELETE, cut, c.state, c.state, null, -1, -1, -1);
        observedPublication(call, () -> { placement.pool().deleteObject(c.whole); requirePlacement(cut, expected); });
    }
    private long nextOrdinal() {
        if (pendingOperation != null) throw new IllegalStateException("Nested Swing producer operation"); return ++ordinal;
    }
    private void observedPublication(Publication call, Runnable store) {
        pendingOperation = call; pendingOrdinal = call.ordinal();
        try {
            if (observer != null) { readOnly(() -> { observer.beforePublication(call); return null; }); readOnly(() -> { receiptGuard.prepared(call); return null; }); }
            store.run();
            if (observer != null) { readOnly(() -> { observer.afterPublication(call); return null; }); readOnly(() -> { receiptGuard.completed(call); return null; }); }
        } finally { pendingOperation = null; pendingOrdinal = 0; }
    }
    private <T> T observedCall(Object call, Supplier<T> action) {
        long value = nextOrdinal(); pendingOperation = call; pendingOrdinal = value;
        try {
            if (observer != null) { readOnly(() -> { observer.beforeCall(call); return null; }); readOnly(() -> { receiptGuard.prepared(call); return null; }); }
            T result = action.get();
            if (observer != null) { readOnly(() -> { observer.afterCall(call); return null; }); readOnly(() -> { receiptGuard.completed(call); return null; }); }
            return result;
        } finally { pendingOperation = null; pendingOrdinal = 0; }
    }
    private <T> T readOnly(Supplier<T> callback) {
        var cut = placement.capture(); var expected = unchanged(cut); T result;
        try { result = callback.get(); }
        catch (RuntimeException | Error original) { suppress(original, () -> requirePlacement(cut, expected)); throw original; }
        requirePlacement(cut, expected); return result;
    }
    private record Expected(List<byte[]> pool, byte[] saved) { }
    private static List<byte[]> images(CdObjectPool.Snapshot cut) {
        var images = new ArrayList<byte[]>(); for (var state : cut.slots()) images.add(state.copyBytes()); return images;
    }
    private static Expected unchanged(CdObjectPlacementRuntime.Snapshot cut) { return new Expected(images(cut.pool()), cut.savedFlags().copy()); }
    private static Expected overlay(CdObjectPlacementRuntime.Snapshot cut, CdObjectState output) {
        return new Expected(images(CdObjectPool.overlay(cut.pool(), List.of(output))), cut.savedFlags().copy());
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Snapshot cut, Expected expected) {
        var now = placement.capture();
        if (!now.status().equals(cut.status()) || !Arrays.equals(now.savedFlags().copy(), expected.saved())) return false;
        for (var state : now.pool().slots()) {
            if (!state.identity().equals(cut.pool().slots().get(state.identity().dynamicIndex()).identity())
                    || !Arrays.equals(state.copyBytes(), expected.pool().get(state.identity().dynamicIndex()))) return false;
        }
        return true;
    }
    private void requirePlacement(CdObjectPlacementRuntime.Snapshot cut, Expected expected) {
        placement.validateRestore(cut);
        if (!samePlacement(cut, expected)) throw new IllegalStateException("Swing callback changed its actual complete placement cut");
    }
    private CdObjectPlacementRuntime.Snapshot capture(Cursor c) { var cut = placement.capture(); requireSource(cut, c.state); return cut; }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source)) throw new IllegalArgumentException("Swing source is not the actual cut image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error fault) { if (fault != original) original.addSuppressed(fault); }
    }
    private static UnsupportedSwing unsupported(UnsupportedBoundary boundary, CdObjectState source, int value) {
        return new UnsupportedSwing(boundary, source.identity().wholeIndex(), value);
    }
    private final class Cursor {
        final int whole; final Globals sourceGlobals; final List<Effect> effects = new ArrayList<>(); CdObjectState state;
        Cursor(CdObjectState source, Globals globals) { whole = source.identity().wholeIndex(); state = source; sourceGlobals = globals; }
        Globals globals() { return Objects.requireNonNull(sourceGlobals, "reached native Swing globals"); }
        void write(CdObjectState next) { nativeStore(this, next, StoreKind.SOURCE_STORE); state = next; }
    }
    private static void byteValue(int value) { if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected native unsigned BYTE"); }
    private static void wordValue(int value) { if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected native unsigned WORD"); }
}
