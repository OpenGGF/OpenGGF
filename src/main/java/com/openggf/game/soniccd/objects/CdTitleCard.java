package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/**
 * Native ID3C title card, over the actual dynamic placement and a selected reserved full64 CAS port.
 * N1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Title Card/Main.asm:23-191, PP Title Card Data.asm:7-21.
 * Actual USA R12A entry20DB02/Init20DB1C calls FindObjSlot2076B8, DrawObject203986,
 * DeleteObject203A32 and LoadPLC202524; R43Legacy aliases resolve to the common helpers here.
 * Screen Y is WORD0A, not world Y0C. No FixBugs branch repairs allocation or motion.
 *
 * <p>Root owns parent4 selection, every other reserved image, Frame/source/phase/order and the increasing
 * physical walk. No fake dynamic identity represents a reserved parent. External calls carry fresh full96,
 * saved-bank/status and selected-full64 observations; they cannot certify omitted owners, invocation or ABA.
 * Effects describe completed work, never authority or replay commands. Callback errors retain their identical
 * original exception and live prefix; failed verification is suppressed, with no restore or retry.
 * No current art, mapping decoder, global bank, clock, Frame or capture owner is created.</p>
 */
public final class CdTitleCard {
    public static final int OBJECT_ID = 0x3C;
    public static final int SECTION_PLC = 2;

    /** Finite raw component views only. Actual retail data has64 bytes; reads stay source-conditional. */
    public record Assets(CdAssetBytes initData, int mappingsAddress) {
        public Assets { Objects.requireNonNull(initData, "title init data"); }
    }
    /** Same selected-image value-CAS discipline as ReservedHud; no dynamic-pool alias is admitted. */
    public interface ReservedSlots {
        CdAssetBytes read(int wholeIndex);
        void write(int wholeIndex, CdAssetBytes expected, CdAssetBytes next);
    }
    public enum ReadField { ACT_BYTE, PLC_HEAD_LONG }
    public enum StoreField { SCROLL_LOCK_BYTE, CONTROL_LOCK_BYTE }
    public record SourceCut(int wholeIndex, CdAssetBytes expectedSource,
                            CdObjectPlacementRuntime.Snapshot actualPlacement) {
        public SourceCut {
            physical(wholeIndex); image64(expectedSource); Objects.requireNonNull(actualPlacement);
            if (wholeIndex >= 32 && !Arrays.equals(expectedSource.copy(),
                    actualPlacement.pool().slots().get(wholeIndex - 32).copyBytes()))
                throw new IllegalArgumentException("Title source differs from selected dynamic capture");
        }
    }
    public record ReadCall(ReadField field, SourceCut cut) {
        public ReadCall { Objects.requireNonNull(field); Objects.requireNonNull(cut); }
    }
    public record StoreCall(StoreField field, int value, SourceCut cut) {
        public StoreCall {
            Objects.requireNonNull(field); Objects.requireNonNull(cut);
            if (value != 0 && (field != StoreField.SCROLL_LOCK_BYTE || value != 1))
                throw new IllegalArgumentException("Title only clears locks or sets scrollLock BYTE1");
        }
    }
    public record PlcCall(int nativeId, SourceCut cut) {
        public PlcCall {
            Objects.requireNonNull(cut);
            if (nativeId != SECTION_PLC) throw new IllegalArgumentException("Title requests native PLC2 only");
        }
    }
    public record DrawCall(SourceCut cut) { public DrawCall { Objects.requireNonNull(cut); } }
    @FunctionalInterface public interface NativeRead { int read(ReadCall call); }
    @FunctionalInterface public interface GlobalStore { void store(StoreCall call); }
    @FunctionalInterface public interface LoadPlc { void load(PlcCall call); }
    @FunctionalInterface public interface DrawObjectCall { void drawObject(DrawCall call); }

    public sealed interface Effect permits RoutineEntered, SlotSearchReturned, ChildInitialized,
            ReadReturned, StoreCompleted, PlcRequested, DrawReturned, Deleted { }
    public record RoutineEntered(int routine) implements Effect { }
    public record SlotSearchReturned(OptionalInt wholeIndex) implements Effect {
        public SlotSearchReturned { Objects.requireNonNull(wholeIndex); }
    }
    public record ChildInitialized(int wholeIndex) implements Effect { }
    public record ReadReturned(ReadField field, int value) implements Effect { }
    public record StoreCompleted(StoreField field, int value) implements Effect { }
    public record PlcRequested(int nativeId) implements Effect { }
    public record DrawReturned() implements Effect { }
    public record Deleted() implements Effect { }
    public record Result(CdAssetBytes image, List<Effect> effects) {
        public Result { image64(image); effects = List.copyOf(effects); }
        public boolean deleted() { return image.unsignedByte(0) == 0; }
    }
    public enum UnsupportedBoundary {
        OBJECT_ID, ROUTINE_DISPATCH, RESERVED_SLOT_OWNER, INIT_DATA_READ, READ_OWNER,
        READ_VALUE_WIDTH, STORE_OWNER, PLC_OWNER, DRAW_OWNER, ALLOCATION_ADDRESS
    }
    public static final class UnsupportedTitleCard extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedTitleCard(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at TitleCard whole " + wholeIndex + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final ReservedSlots reservedSlots;
    private final NativeRead actRead, plcHeadRead;
    private final GlobalStore globalStore;
    private final LoadPlc loadPlc;
    private final DrawObjectCall drawObject;
    private boolean running;

    /** Optional ports stop only at their actual reached instruction. Constructing values authenticates no media. */
    public CdTitleCard(CdObjectPlacementRuntime placement, Assets assets, ReservedSlots reservedSlots,
                       NativeRead actRead, NativeRead plcHeadRead, GlobalStore globalStore,
                       LoadPlc loadPlc, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.reservedSlots = reservedSlots; this.actRead = actRead; this.plcHeadRead = plcHeadRead;
        this.globalStore = globalStore; this.loadPlc = loadPlc; this.drawObject = drawObject;
    }

    /** One actual ObjTitleCard dispatch. Parent4 and same-pass child order remain the invoking World's facts. */
    public Result run(int wholeIndex) {
        physical(wholeIndex);
        if (running) throw new IllegalStateException("Reentrant ObjTitleCard dispatch");
        running = true;
        try {
            var c = new Cursor(wholeIndex, selected(wholeIndex));
            if (c.u8(0) != OBJECT_ID) throw unsupported(UnsupportedBoundary.OBJECT_ID, wholeIndex, c.u8(0));
            int routine = c.u8(0x24);
            if ((routine & 1) != 0 || routine > 10) throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
            c.effects.add(new RoutineEntered(routine));
            switch (routine) {
                case 0 -> init(c);
                case 2 -> slideIn(c, TITLE_CARD_SCREEN_Y_WORD, TITLE_CARD_DEST_Y);
                case 4 -> slideIn(c, X, TITLE_CARD_DEST_X);
                case 6 -> slideOut(c, TITLE_CARD_SCREEN_Y_WORD, TITLE_CARD_START_Y, true);
                case 8 -> slideOut(c, X, TITLE_CARD_START_X, false);
                case 10 -> waitPlc(c);
                default -> throw new AssertionError();
            }
            requireSelected(c.whole, c.image);
            return new Result(c.image, c.effects);
        } finally { running = false; }
    }

    private void init(Cursor c) {
        c.byteStore(ROUTINE, 2); c.wordStore(X, 0x118); c.wordStore(TITLE_CARD_SCREEN_Y_WORD, 0x30);
        c.wordStore(TITLE_CARD_START_Y, 0x30); c.wordStore(TITLE_CARD_DEST_Y, 0xF0);
        c.byteStore(ANIMATION_TIME, 0x5A); c.wordStore(TILE, 0x8360);
        c.longStore(MAPPINGS, assets.mappingsAddress()); c.byteStore(PRIORITY, 4);
        for (int row = 0; row < 8; row++) {
            var found = placement.pool().findObjSlot(); c.effects.add(new SlotSearchReturned(found));
            // Native lacks a failure branch. Do not model its following out-of-owned-pool writes.
            if (found.isEmpty()) throw unsupported(UnsupportedBoundary.ALLOCATION_ADDRESS, c.whole, 0xFFF000);
            int whole = found.getAsInt(); var pool = placement.pool(); int at = row * 8;
            var child = pool.state(whole).moveByte(ID, OBJECT_ID).moveByte(ROUTINE, 4)
                    .moveWord(TILE, 0x8360).moveLong(MAPPINGS, assets.mappingsAddress());
            pool.write(child);
            child = child.moveWord(TITLE_CARD_SCREEN_Y_WORD, data(c, at, 2)); pool.write(child);
            int x = data(c, at + 2, 2);
            child = child.moveWord(X, x).moveWord(TITLE_CARD_START_X, x); pool.write(child);
            child = child.moveWord(TITLE_CARD_DEST_X, data(c, at + 4, 2)); pool.write(child);
            child = child.moveByte(MAP_FRAME, data(c, at + 6, 1)); pool.write(child);
            if (row == 5) { child = child.addByte(MAP_FRAME, read(c, ReadField.ACT_BYTE)); pool.write(child); }
            child = child.moveByte(ANIMATION_TIME, data(c, at + 7, 1)); pool.write(child);
            c.effects.add(new ChildInitialized(whole));
        }
    }
    private int data(Cursor c, int at, int count) {
        if (at < 0 || at > assets.initData().length() - count)
            throw unsupported(UnsupportedBoundary.INIT_DATA_READ, c.whole, at);
        return count == 2 ? assets.initData().unsignedWord(at) : assets.initData().unsignedByte(at);
    }
    private void slideIn(Cursor c, CdObjectState.WordField position, CdObjectState.WordField destination) {
        int current = c.word(position), target = c.word(destination);
        if (current == target) c.byteStore(ROUTINE, c.u8(0x24) + 4);
        else c.wordStore(position, current + ((short) target >= (short) current ? 8 : -8));
        draw(c);
    }
    private void slideOut(Cursor c, CdObjectState.WordField position, CdObjectState.WordField start, boolean vertical) {
        int timer = c.u8(ANIMATION_TIME.byteOffset());
        if (timer != 0) { c.byteStore(ANIMATION_TIME, timer - 1); draw(c); return; }
        int current = c.word(position), target = c.word(start);
        if (current != target) {
            c.wordStore(position, current + ((short) target >= (short) current ? 16 : -16)); draw(c); return;
        }
        if (!vertical) { delete(c); return; }
        c.byteStore(ROUTINE, c.u8(0x24) + 4);
        store(c, StoreField.SCROLL_LOCK_BYTE, 1);
        if (loadPlc == null) throw unsupported(UnsupportedBoundary.PLC_OWNER, c.whole, SECTION_PLC);
        var cut = cut(c); var call = new PlcCall(SECTION_PLC, cut);
        readOnly(cut, () -> loadPlc.load(call)); c.effects.add(new PlcRequested(SECTION_PLC));
    }
    private void waitPlc(Cursor c) {
        if (read(c, ReadField.PLC_HEAD_LONG) != 0) return;
        store(c, StoreField.SCROLL_LOCK_BYTE, 0); store(c, StoreField.CONTROL_LOCK_BYTE, 0); delete(c);
    }
    private int read(Cursor c, ReadField field) {
        NativeRead port = field == ReadField.ACT_BYTE ? actRead : plcHeadRead;
        if (port == null) throw unsupported(UnsupportedBoundary.READ_OWNER, c.whole, field.ordinal());
        var cut = cut(c); var call = new ReadCall(field, cut);
        int value = readValue(cut, () -> port.read(call));
        if (field == ReadField.ACT_BYTE && (value & ~0xFF) != 0)
            throw unsupported(UnsupportedBoundary.READ_VALUE_WIDTH, c.whole, value);
        c.effects.add(new ReadReturned(field, value)); return value;
    }
    private void store(Cursor c, StoreField field, int value) {
        if (globalStore == null) throw unsupported(UnsupportedBoundary.STORE_OWNER, c.whole, field.ordinal());
        var cut = cut(c); var call = new StoreCall(field, value, cut);
        readOnly(cut, () -> globalStore.store(call)); c.effects.add(new StoreCompleted(field, value));
    }
    private void delete(Cursor c) {
        if (c.whole >= 32) placement.pool().deleteObject(c.whole);
        else reservedWrite(c, CdAssetBytes.copyOf(new byte[64]));
        c.image = selected(c.whole); c.effects.add(new Deleted());
    }
    private void draw(Cursor c) {
        if (drawObject == null) throw unsupported(UnsupportedBoundary.DRAW_OWNER, c.whole, 0);
        var cut = cut(c); byte[] next = c.image.copy(); next[1] &= 0x7F; var ready = CdAssetBytes.copyOf(next);
        var expectedPool = cut.actualPlacement().pool();
        if (c.whole >= 32) expectedPool = CdObjectPool.overlay(expectedPool,
                List.of(placement.pool().state(c.whole).moveByte(SPRITE_FLAGS, next[1])));
        var finalPool = expectedPool;
        try {
            drawObject.drawObject(new DrawCall(cut)); assertPlacement(cut.actualPlacement(), finalPool); requireSelected(c.whole, ready);
        } catch (RuntimeException | Error original) {
            suppress(original, () -> {
                if (!samePlacement(cut.actualPlacement(), cut.actualPlacement().pool())) assertPlacement(cut.actualPlacement(), finalPool);
                requireEitherSelected(c.whole, c.image, ready);
            }); throw original;
        }
        c.image = selected(c.whole); c.effects.add(new DrawReturned());
    }

    private SourceCut cut(Cursor c) {
        var snapshot = placement.capture(); requireSelected(c.whole, c.image); assertPlacement(snapshot, snapshot.pool());
        return new SourceCut(c.whole, c.image, snapshot);
    }
    private void readOnly(SourceCut cut, Runnable action) {
        readValue(cut, () -> { action.run(); return 0; });
    }
    private int readValue(SourceCut cut, IntSupplier action) {
        verify(cut);
        try { int value = action.getAsInt(); verify(cut); return value; }
        catch (RuntimeException | Error original) { suppress(original, () -> verify(cut)); throw original; }
    }
    private void verify(SourceCut cut) {
        assertPlacement(cut.actualPlacement(), cut.actualPlacement().pool()); requireSelected(cut.wholeIndex(), cut.expectedSource());
    }
    private CdAssetBytes selected(int whole) {
        if (whole >= 32) return CdAssetBytes.copyOf(placement.pool().state(whole).copyBytes());
        if (reservedSlots == null) throw unsupported(UnsupportedBoundary.RESERVED_SLOT_OWNER, whole, 0);
        var before = placement.capture();
        try { var value = image64(reservedSlots.read(whole)); assertPlacement(before, before.pool()); return value; }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(before, before.pool())); throw original; }
    }
    private void reservedWrite(Cursor c, CdAssetBytes next) {
        var before = placement.capture(); requireSelected(c.whole, c.image);
        try { reservedSlots.write(c.whole, c.image, next); assertPlacement(before, before.pool()); requireSelected(c.whole, next); }
        catch (RuntimeException | Error original) {
            suppress(original, () -> { assertPlacement(before, before.pool()); requireEitherSelected(c.whole, c.image, next); }); throw original;
        }
        c.image = selected(c.whole);
    }
    private void requireSelected(int whole, CdAssetBytes expected) {
        if (!Arrays.equals(selected(whole).copy(), expected.copy())) throw new IllegalStateException("Title selected full64 changed outside its native stores");
    }
    private void requireEitherSelected(int whole, CdAssetBytes before, CdAssetBytes after) {
        var actual = selected(whole).copy();
        if (!Arrays.equals(actual, before.copy()) && !Arrays.equals(actual, after.copy()))
            throw new IllegalStateException("Title failed callback changed an unowned selected byte");
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Snapshot before, CdObjectPool.Snapshot expected) {
        var now = placement.capture(); return now.status().equals(before.status()) && now.pool().slots().equals(expected.slots())
                && Arrays.equals(now.savedFlags().copy(), before.savedFlags().copy());
    }
    private void assertPlacement(CdObjectPlacementRuntime.Snapshot before, CdObjectPool.Snapshot expected) {
        placement.validateRestore(before); placement.pool().validateRestore(expected);
        if (!samePlacement(before, expected)) throw new IllegalStateException("Title callback changed its actual full96/saved/status cut");
    }
    private static void suppress(Throwable original, Runnable check) {
        try { check.run(); } catch (RuntimeException | Error failure) { if (failure != original) original.addSuppressed(failure); }
    }
    private static CdAssetBytes image64(CdAssetBytes bytes) {
        if (Objects.requireNonNull(bytes).length() != 64) throw new IllegalArgumentException("Complete selected full64 required"); return bytes;
    }
    private static void physical(int whole) { if (whole < 1 || whole >= 128) throw new IllegalArgumentException("Title physical whole index must be1..127"); }
    private static UnsupportedTitleCard unsupported(UnsupportedBoundary boundary, int whole, int value) {
        return new UnsupportedTitleCard(boundary, whole, value);
    }
    private final class Cursor {
        final int whole; final List<Effect> effects = new ArrayList<>(); CdAssetBytes image;
        Cursor(int whole, CdAssetBytes image) { this.whole = whole; this.image = image; }
        int u8(int at) { return image.unsignedByte(at); }
        int word(CdObjectState.WordField field) { return image.unsignedWord(field.byteOffset()); }
        void byteStore(CdObjectState.ByteField field, int value) {
            if (whole >= 32) { placement.pool().write(placement.pool().state(whole).moveByte(field, value)); image = selected(whole); }
            else { byte[] next = image.copy(); next[field.byteOffset()] = (byte) value; reservedWrite(this, CdAssetBytes.copyOf(next)); }
        }
        void wordStore(CdObjectState.WordField field, int value) {
            if (whole >= 32) { placement.pool().write(placement.pool().state(whole).moveWord(field, value)); image = selected(whole); }
            else { byte[] next = image.copy(); putWord(next, field.byteOffset(), value); reservedWrite(this, CdAssetBytes.copyOf(next)); }
        }
        void longStore(CdObjectState.LongField field, int value) {
            if (whole >= 32) { placement.pool().write(placement.pool().state(whole).moveLong(field, value)); image = selected(whole); }
            else { byte[] next = image.copy(); putWord(next, field.byteOffset(), value >>> 16); putWord(next, field.byteOffset() + 2, value); reservedWrite(this, CdAssetBytes.copyOf(next)); }
        }
    }
    private static void putWord(byte[] bytes, int at, int value) { bytes[at] = (byte) (value >>> 8); bytes[at + 1] = (byte) value; }
}
