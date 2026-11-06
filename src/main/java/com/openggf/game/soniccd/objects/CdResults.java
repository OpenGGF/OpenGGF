package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.media.CdMmdImage;

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
 * Selected USA/Past ObjResults ($3A), native pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/_Objects/Results/Main.asm:42-366. No FixBugs alternative occurs in this body.
 * Object Spawner.asm:11-20,329-340 owns the saved-bank reset and ID-only free-slot search;
 * HUD and Points/Main.asm:157-180 owns the existing shared AddPoints value operation.
 *
 * <p>This component owns actual selected pool stores and no external state. Every reached external
 * operation borrows a fresh full placement/source cut synchronously. These observational values do
 * not authenticate a World/Frame/programme invocation or confer replay/exactly-once authority.
 * Root must authenticate the actual producer and external owners, use the shared selected
 * CdPlayerTouchResponse.addPoints kernel at PointsCall, and prepare all external immutable values
 * before adoption. Missing owners stop before their first reached operation; no default knowledge,
 * stage visit, checkpoint/backup save, clock, control latch or next-level load is manufactured.
 *
 * <p>Completed prefixes survive faults. Callback verification checks all 96 complete images, the
 * saved bank and placement cursors, preserving an original thrown exception. Effects observe only
 * completed operations. Draw may clear only the selected sprite's bit7; the explicit saved reset
 * action may perform only its complete existing reset. There is no wrapper Draw/despawn tail,
 * animation script or CPU/VDP/driver-handshake implementation here.
 */
public final class CdResults {
    public static final int OBJECT_ID = 0x3A;
    public static final int PLC_ID = 0x10;

    /** Original finite views, retaining the four mapping roots at +0/+10/+20/+30. */
    public record Assets(CdAssetBytes initData, CdAssetBytes mappings, int mappingsAddress) {
        public Assets { Objects.requireNonNull(initData); Objects.requireNonNull(mappings); }
        /** Actual retained original-USA R12B bytes; no programme, art/current or World authority. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B metadata"); var identity = owner.identity();
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12B.stage()))
                throw new IllegalArgumentException("Actual original USA R12B metadata required");
            return new Assets(owner.view(CdR12bObjectAssets.View.RESULTS_INIT_DATA),
                    owner.view(CdR12bObjectAssets.View.MAP_SPR_RESULTS),
                    CdR12bObjectAssets.View.MAP_SPR_RESULTS.range().nativeAddress());
        }

        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "actual Past assets");
            var map = owner.view(CdR11bAssets.Asset.MAP_SPR_RESULTS);
            return new Assets(owner.view(CdR11bAssets.Asset.RESULTS_INIT_DATA).bytes(), map.bytes(),
                    map.range().nativeAddress());
        }
    }
    public enum NativeRoutine { INIT, WAIT_PLC, MOVE, BONUS, NEXT_LEVEL }
    public enum ReadField {
        PLC_HEAD_LONG(32), ZONE_ACT_WORD(16), CAMERA_X_WORD(16), PLAYER_X_WORD(16),
        GOOD_FUTURE_BYTE(8), ACT_BYTE(8), TIME_BONUS_WORD(16), RING_BONUS_WORD(16),
        SPECIAL_STAGE_BYTE(8), TIME_ATTACK_MODE_BYTE(8), TIME_STONES_BYTE(8), GOOD_FUTURE_FLAGS_BYTE(8);
        private final int widthBits;
        ReadField(int widthBits) { this.widthBits = widthBits; }
        public int widthBits() { return widthBits; }
    }
    public enum BonusField {
        UPDATE_HUD_BONUS_BYTE(8), TIME_BONUS_WORD(16), RING_BONUS_WORD(16);
        private final int widthBits;
        BonusField(int widthBits) { this.widthBits = widthBits; }
        public int widthBits() { return widthBits; }
    }
    /** BCLR/BSET variants carry a bit operand, retaining every other current target byte bit. */
    public enum ProgrammeField {
        LEVEL_RESTART_WORD(16), SPAWN_MODE_BYTE(8), SECTION_ID_WORD(16), FLOWER_COUNT_LONG(32),
        UNKNOWN_LEVEL_BYTE(8), PROJ_DESTROYED_BYTE(8), CHECKPOINT_BYTE(8),
        PLC_LOAD_FLAGS_CLEAR_BIT0(8), PLC_LOAD_FLAGS_CLEAR_BIT1(8), TIME_ZONE_BYTE(8), ZONE_ACT_WORD(16),
        GOOD_FUTURE_BYTE(8), GOOD_FUTURE_FLAGS_CLEAR_BYTE(8), GOOD_FUTURE_FLAGS_SET_BIT(8);
        private final int widthBits;
        ProgrammeField(int widthBits) { this.widthBits = widthBits; }
        public int widthBits() { return widthBits; }
    }
    /** Reset delegates to the existing placement reset, including its two one-valued headers. */
    public enum ProgrammeAction { RESET_SAVED_OBJECT_FLAGS, FADE_OUT_MUSIC }
    public enum Sound {
        FM_TALLY(0xBD), FM_SSWARP(0xC8), DRIVER_KACHING(0x9A);
        private final int nativeId;
        Sound(int nativeId) { this.nativeId = nativeId; }
        public int nativeId() { return nativeId; }
    }
    public record ReadCall(ReadField field, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public ReadCall { Objects.requireNonNull(field); requireSource(actualCut, expectedSource); }
    }
    public record BonusStoreCall(BonusField field, int value, CdObjectPlacementRuntime.Snapshot actualCut,
                                 CdObjectState expectedSource) {
        public BonusStoreCall {
            Objects.requireNonNull(field); requireSource(actualCut, expectedSource); width(value, field.widthBits());
            if (field == BonusField.UPDATE_HUD_BONUS_BYTE && value != 1)
                throw new IllegalArgumentException("Results sets the HUD bonus update byte to one");
        }
    }
    /** The synchronous existing score owner calls the shared kernel once, then adopts/sends its result. */
    public record PointsCall(int points, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public PointsCall {
            requireSource(actualCut, expectedSource);
            if (points != 10 && points != 20) throw new IllegalArgumentException("Results adds ten per nonzero bonus");
        }
    }
    public record PlcCall(int nativeId, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public PlcCall {
            requireSource(actualCut, expectedSource);
            if (nativeId != PLC_ID) throw new IllegalArgumentException("Results requests native cue16");
        }
    }
    public record SoundCall(Sound sound, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public SoundCall { Objects.requireNonNull(sound); requireSource(actualCut, expectedSource); }
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public DrawCall { requireSource(actualCut, expectedSource); }
    }
    public record ProgrammeStoreCall(ProgrammeField field, int value, CdObjectPlacementRuntime.Snapshot actualCut,
                                     CdObjectState expectedSource) {
        public ProgrammeStoreCall {
            Objects.requireNonNull(field); requireSource(actualCut, expectedSource); width(value, field.widthBits());
            boolean valid = switch (field) {
                case LEVEL_RESTART_WORD -> value == 2;
                case SPAWN_MODE_BYTE, SECTION_ID_WORD, FLOWER_COUNT_LONG, UNKNOWN_LEVEL_BYTE,
                        PROJ_DESTROYED_BYTE, CHECKPOINT_BYTE, PLC_LOAD_FLAGS_CLEAR_BIT0,
                        GOOD_FUTURE_FLAGS_CLEAR_BYTE -> value == 0;
                case PLC_LOAD_FLAGS_CLEAR_BIT1 -> value == 1;
                case TIME_ZONE_BYTE -> value == 1 || value == 2;
                case ZONE_ACT_WORD -> true;
                case GOOD_FUTURE_BYTE -> value == 0 || value == 1;
                case GOOD_FUTURE_FLAGS_SET_BIT -> value < 8;
            };
            if (!valid) throw new IllegalArgumentException("Not a reached native Results programme operation");
        }
    }
    public record ProgrammeActionCall(ProgrammeAction action, CdObjectPlacementRuntime.Snapshot actualCut,
                                      CdObjectState expectedSource) {
        public ProgrammeActionCall { Objects.requireNonNull(action); requireSource(actualCut, expectedSource); }
    }
    @FunctionalInterface public interface NativeRead { int read(ReadCall call); }
    @FunctionalInterface public interface BonusStore { void store(BonusStoreCall call); }
    @FunctionalInterface public interface PointsPort { void addPoints(PointsCall call); }
    @FunctionalInterface public interface LoadPlc { void load(PlcCall call); }
    @FunctionalInterface public interface SoundPort { void request(SoundCall call); }
    @FunctionalInterface public interface DrawObject { void drawObject(DrawCall call); }
    @FunctionalInterface public interface ProgrammeStore { void store(ProgrammeStoreCall call); }
    @FunctionalInterface public interface ProgrammeActionPort { void perform(ProgrammeActionCall call); }

    public sealed interface Effect permits RoutineEntered, ReadReturned, BonusStored, PointsReturned,
            PlcRequested, SoundRequested, SlotSearchReturned, RowInitialized, DrawReturned,
            ProgrammeStored, ProgrammeActionReturned { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { }
    public record ReadReturned(ReadField field, int value) implements Effect { }
    public record BonusStored(BonusField field, int value) implements Effect { }
    public record PointsReturned(int points) implements Effect { }
    public record PlcRequested(int nativeId) implements Effect { }
    public record SoundRequested(Sound sound) implements Effect { }
    public record SlotSearchReturned(OptionalInt wholeIndex) implements Effect { }
    public record RowInitialized(CdObjectState state) implements Effect { }
    public record DrawReturned() implements Effect { }
    public record ProgrammeStored(ProgrammeField field, int value) implements Effect { }
    public record ProgrammeActionReturned(ProgrammeAction action) implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, READ_OWNER, READ_VALUE_WIDTH, BONUS_STORE_OWNER, POINTS_OWNER, PLC_OWNER,
        SOUND_OWNER, DRAW_OWNER, PROGRAMME_STORE_OWNER, PROGRAMME_ACTION_OWNER,
        INIT_Y_READ, INIT_X_READ, INIT_DEST_X_READ, INIT_FRAME_READ, ALLOCATION_ADDRESS
    }
    public static final class UnsupportedResults extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedResults(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Results slot " + wholeIndex + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final NativeRead nativeRead;
    private final BonusStore bonusStore;
    private final PointsPort pointsPort;
    private final LoadPlc loadPlc;
    private final SoundPort soundPort;
    private final DrawObject drawObject;
    private final ProgrammeStore programmeStore;
    private final ProgrammeActionPort programmeAction;
    private boolean running;

    /** Source-pair ingress only; the caller retains every reached publication/helper authority. */
    public static CdResults fromAct2PastAssets(CdObjectPlacementRuntime placement, CdR12bTerrainAssets owner,
                                               NativeRead read, BonusStore bonus, PointsPort points, LoadPlc plc,
                                               SoundPort sound, DrawObject draw, ProgrammeStore programme,
                                               ProgrammeActionPort action) {
        Objects.requireNonNull(placement); Objects.requireNonNull(owner);
        if (!placement.sameAct2PastSource(owner)) throw new IllegalArgumentException("Results placement/assets source differs");
        return new CdResults(placement, Assets.fromAct2PastAssets(owner.objects()), read, bonus, points, plc,
                sound, draw, programme, action);
    }

    /** Absent owners are admitted until, and only until, their first actually reached operation. */
    public CdResults(CdObjectPlacementRuntime placement, Assets assets, NativeRead nativeRead,
                     BonusStore bonusStore, PointsPort pointsPort, LoadPlc loadPlc, SoundPort soundPort,
                     DrawObject drawObject, ProgrammeStore programmeStore, ProgrammeActionPort programmeAction) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.nativeRead = nativeRead; this.bonusStore = bonusStore; this.pointsPort = pointsPort;
        this.loadPlc = loadPlc; this.soundPort = soundPort; this.drawObject = drawObject;
        this.programmeStore = programmeStore; this.programmeAction = programmeAction;
    }
    public Result run(int wholeIndex) {
        if (running) throw new IllegalStateException("Reentrant ObjResults dispatch");
        var state = placement.pool().state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Selected slot is not Results $3A");
        if ((state.routine() & 1) != 0 || state.routine() > 8)
            throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, state.routine());
        running = true;
        try {
            var c = new Cursor(state);
            switch (state.routine()) {
                case 0 -> init(c);
                case 2 -> waitPlc(c);
                case 4 -> move(c);
                case 6 -> bonus(c);
                case 8 -> nextLevel(c);
            }
            return new Result(c.state, c.effects);
        } finally { running = false; }
    }
    private void init(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.INIT));
        c.write(c.state.addByte(RESULTS_TIMER, -1)); // SUBQ.B at HIGH $32; low $33 is untouched.
        if (c.state.unsignedByte(RESULTS_TIMER) != 0) return;
        plc(c); // LoadPLC is before the routine-byte store (:65-68).
        c.write(c.state.addByte(ROUTINE, 2));
        waitPlc(c);
    }
    private void waitPlc(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.WAIT_PLC));
        if (read(c, ReadField.PLC_HEAD_LONG) != 0) return;
        if (read(c, ReadField.ZONE_ACT_WORD) != 0x502) {
            int edge = (read(c, ReadField.CAMERA_X_WORD) + 336) & 0xFFFF;
            if (edge >= read(c, ReadField.PLAYER_X_WORD)) return; // CMP.W/BCC: strict unsigned.
        }
        c.write(c.state.moveWord(RESULTS_TIMER_WORD, 480)); // USA WORD replaces the earlier BYTE delay.
        int selected = c.whole;
        for (int row = 0; row < 3; row++) {
            if (row != 0) {
                var found = placement.pool().findObjSlot(); c.effects.add(new SlotSearchReturned(found));
                // Native omits allocation failure handling. Stop before the first out-of-pool write.
                if (found.isEmpty()) throw unsupported(UnsupportedBoundary.ALLOCATION_ADDRESS, c.whole,
                        CdObjectPool.END_WHOLE_INDEX);
                selected = found.getAsInt();
            }
            c.writeRow(selected, placement.pool().state(selected).moveWord(RESULTS_TIMER_WORD, 480)
                    .moveByte(ID, OBJECT_ID).moveByte(ROUTINE, 4).moveWord(TILE, 0x83C4));
            boolean ssz = read(c, ReadField.ZONE_ACT_WORD) == 0x502;
            if (ssz) c.writeRow(selected, placement.pool().state(selected).moveWord(TILE, 0x82F2));
            int mappingRoot = assets.mappingsAddress() + (ssz ? 10 : 0);
            c.writeRow(selected, placement.pool().state(selected).moveLong(MAPPINGS, mappingRoot));
            if (read(c, ReadField.GOOD_FUTURE_BYTE) != 0)
                c.writeRow(selected, placement.pool().state(selected).moveLong(MAPPINGS, mappingRoot + 20));
            int at = row * 8;
            c.writeRow(selected, placement.pool().state(selected).moveWord(RESULTS_SCREEN_Y_WORD,
                    initWord(c, at, UnsupportedBoundary.INIT_Y_READ)));
            c.writeRow(selected, placement.pool().state(selected).moveWord(X,
                    initWord(c, at + 2, UnsupportedBoundary.INIT_X_READ)));
            c.writeRow(selected, placement.pool().state(selected).moveWord(RESULTS_DEST_X,
                    initWord(c, at + 4, UnsupportedBoundary.INIT_DEST_X_READ)));
            if (at + 7 >= assets.initData().length())
                throw unsupported(UnsupportedBoundary.INIT_FRAME_READ, c.whole, at + 7);
            c.writeRow(selected, placement.pool().state(selected).moveByte(MAP_FRAME,
                    assets.initData().unsignedByte(at + 7))); // Frame WORD's high byte is not read.
            if (row == 2) c.writeRow(selected, placement.pool().state(selected).addByte(MAP_FRAME,
                    read(c, ReadField.ACT_BYTE)));
            c.effects.add(new RowInitialized(placement.pool().state(selected)));
        }
    }
    private int initWord(Cursor c, int at, UnsupportedBoundary boundary) {
        if (at > assets.initData().length() - 2) throw unsupported(boundary, c.whole, at);
        return assets.initData().unsignedWord(at);
    }
    private void move(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.MOVE));
        if (c.state.word(RESULTS_TIMER_WORD) != 0) c.write(c.state.addWord(RESULTS_TIMER_WORD, -1));
        int x = c.state.centreX(), destination = c.state.word(RESULTS_DEST_X);
        if (x != destination) c.write(c.state.addWord(X, (short) destination < (short) x ? -8 : 8));
        else if (c.state.unsignedByte(MAP_FRAME) == 0) c.write(c.state.addByte(ROUTINE, 2));
        if (c.state.word(RESULTS_TIMER_WORD) < 472) draw(c); // BCC tests WORD unsigned.
    }
    private void bonus(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.BONUS));
        bonusStore(c, BonusField.UPDATE_HUD_BONUS_BYTE, 1);
        int points = 0, time = read(c, ReadField.TIME_BONUS_WORD);
        if (time != 0) {
            bonusStore(c, BonusField.TIME_BONUS_WORD, (time - 100) & 0xFFFF); points += 10;
        }
        int rings = read(c, ReadField.RING_BONUS_WORD);
        if (rings != 0) {
            bonusStore(c, BonusField.RING_BONUS_WORD, (rings - 100) & 0xFFFF); points += 10;
        }
        if (points == 0) {
            c.write(c.state.addWord(RESULTS_TIMER_WORD, -1));
            int timer = c.state.word(RESULTS_TIMER_WORD);
            if ((short) timer < 0) c.write(c.state.addByte(ROUTINE, 2));
            if (timer == 30 && read(c, ReadField.SPECIAL_STAGE_BYTE) != 0) sound(c, Sound.FM_SSWARP);
            draw(c); return;
        }
        // Real freshly borrowed short-circuit reads follow the two possible subtract stores.
        if (read(c, ReadField.TIME_BONUS_WORD) == 0 && read(c, ReadField.RING_BONUS_WORD) == 0) {
            sound(c, Sound.DRIVER_KACHING); // USA driver queue1 $1C09: one semantic write, no bus protocol.
            if (c.state.word(RESULTS_TIMER_WORD) < 45) c.write(c.state.moveWord(RESULTS_TIMER_WORD, 45));
        } else {
            if (c.state.word(RESULTS_TIMER_WORD) != 0) c.write(c.state.addWord(RESULTS_TIMER_WORD, -1));
            // Memory BTST#0 addresses the HIGH BYTE $32, not WORD parity (:264, actual E16E).
            if ((c.state.unsignedByte(RESULTS_TIMER) & 1) == 0) sound(c, Sound.FM_TALLY);
        }
        points(c, points); draw(c);
    }
    private void nextLevel(Cursor c) {
        c.effects.add(new RoutineEntered(NativeRoutine.NEXT_LEVEL));
        programmeStore(c, ProgrammeField.LEVEL_RESTART_WORD, 2); // First genuinely unowned action if absent.
        programmeStore(c, ProgrammeField.SPAWN_MODE_BYTE, 0);
        programmeStore(c, ProgrammeField.SECTION_ID_WORD, 0);
        programmeStore(c, ProgrammeField.FLOWER_COUNT_LONG, 0); // Includes adjacent fadeEnableDisplay BYTE.
        programmeStore(c, ProgrammeField.UNKNOWN_LEVEL_BYTE, 0);
        programmeStore(c, ProgrammeField.PROJ_DESTROYED_BYTE, 0);
        programmeStore(c, ProgrammeField.CHECKPOINT_BYTE, 0);
        if (read(c, ReadField.TIME_ATTACK_MODE_BYTE) != 0)
            programmeStore(c, ProgrammeField.PLC_LOAD_FLAGS_CLEAR_BIT0, 0);
        programmeStore(c, ProgrammeField.PLC_LOAD_FLAGS_CLEAR_BIT1, 1);
        programmeStore(c, ProgrammeField.TIME_ZONE_BYTE, 1);
        int zoneAct = read(c, ReadField.ZONE_ACT_WORD);
        zoneAct = (zoneAct & 0xFF00) | ((zoneAct + 1) & 0xFF); // ADDQ.B: no carry into zone BYTE.
        if ((zoneAct & 0xFF) == 2) programmeStore(c, ProgrammeField.TIME_ZONE_BYTE, 2);
        else if ((zoneAct & 0xFF) == 3) zoneAct = ((zoneAct & 0xFF00) + 0x100) & 0xFF00;
        // All preceding act increments/low-byte clears are D0 register operations (:296-308).
        // The single native zoneAct memory store follows conditional timeZone=2 (:311).
        programmeStore(c, ProgrammeField.ZONE_ACT_WORD, zoneAct);
        action(c, ProgrammeAction.RESET_SAVED_OBJECT_FLAGS);
        action(c, ProgrammeAction.FADE_OUT_MUSIC);
        draw(c); // Native draw occurs BEFORE every fresh tail operand (:317-343).
        int actBit = (read(c, ReadField.ACT_BYTE) - 1) & 0xFF;
        if ((byte) actBit < 0) { programmeStore(c, ProgrammeField.GOOD_FUTURE_FLAGS_CLEAR_BYTE, 0); return; }
        if (read(c, ReadField.TIME_ATTACK_MODE_BYTE) != 0) return;
        if (read(c, ReadField.TIME_STONES_BYTE) == 0x7F) {
            programmeStore(c, ProgrammeField.GOOD_FUTURE_BYTE, 1); return;
        }
        if (read(c, ReadField.GOOD_FUTURE_BYTE) == 0) return;
        programmeStore(c, ProgrammeField.GOOD_FUTURE_BYTE, 0);
        programmeStore(c, ProgrammeField.GOOD_FUTURE_FLAGS_SET_BIT, actBit & 7); // Memory BSET modulo8.
        if (read(c, ReadField.GOOD_FUTURE_FLAGS_BYTE) == 3)
            programmeStore(c, ProgrammeField.GOOD_FUTURE_BYTE, 1);
    }

    private int read(Cursor c, ReadField field) {
        if (nativeRead == null) throw unsupported(UnsupportedBoundary.READ_OWNER, c.whole, field.ordinal());
        var cut = capture(c); var call = new ReadCall(field, cut, c.state);
        int value = readValue(cut, () -> nativeRead.read(call));
        if (field.widthBits() != 32 && (value < 0 || value >= 1 << field.widthBits()))
            throw unsupported(UnsupportedBoundary.READ_VALUE_WIDTH, c.whole, value);
        c.effects.add(new ReadReturned(field, value)); return value;
    }
    private void bonusStore(Cursor c, BonusField field, int value) {
        if (bonusStore == null) throw unsupported(UnsupportedBoundary.BONUS_STORE_OWNER, c.whole, field.ordinal());
        var cut = capture(c); var call = new BonusStoreCall(field, value, cut, c.state);
        readOnly(cut, () -> bonusStore.store(call)); c.effects.add(new BonusStored(field, value));
    }
    private void points(Cursor c, int points) {
        if (pointsPort == null) throw unsupported(UnsupportedBoundary.POINTS_OWNER, c.whole, points);
        var cut = capture(c); var call = new PointsCall(points, cut, c.state);
        readOnly(cut, () -> pointsPort.addPoints(call)); c.effects.add(new PointsReturned(points));
    }
    private void plc(Cursor c) {
        if (loadPlc == null) throw unsupported(UnsupportedBoundary.PLC_OWNER, c.whole, PLC_ID);
        var cut = capture(c); var call = new PlcCall(PLC_ID, cut, c.state);
        readOnly(cut, () -> loadPlc.load(call)); c.effects.add(new PlcRequested(PLC_ID));
    }
    private void sound(Cursor c, Sound sound) {
        if (soundPort == null) throw unsupported(UnsupportedBoundary.SOUND_OWNER, c.whole, sound.nativeId());
        var cut = capture(c); var call = new SoundCall(sound, cut, c.state);
        readOnly(cut, () -> soundPort.request(call)); c.effects.add(new SoundRequested(sound));
    }
    private void programmeStore(Cursor c, ProgrammeField field, int value) {
        if (programmeStore == null) throw unsupported(UnsupportedBoundary.PROGRAMME_STORE_OWNER, c.whole, field.ordinal());
        var cut = capture(c); var call = new ProgrammeStoreCall(field, value, cut, c.state);
        readOnly(cut, () -> programmeStore.store(call)); c.effects.add(new ProgrammeStored(field, value));
    }
    private void action(Cursor c, ProgrammeAction action) {
        if (programmeAction == null) throw unsupported(UnsupportedBoundary.PROGRAMME_ACTION_OWNER, c.whole, action.ordinal());
        var cut = capture(c); var call = new ProgrammeActionCall(action, cut, c.state);
        if (action != ProgrammeAction.RESET_SAVED_OBJECT_FLAGS) readOnly(cut, () -> programmeAction.perform(call));
        else {
            byte[] expectedSaved = new byte[CdObjectPlacementRuntime.SAVED_BANK_BYTES];
            expectedSaved[0] = expectedSaved[1] = 1;
            var s = cut.status();
            var expectedStatus = new CdObjectPlacementRuntime.Status(s.routine(), s.previousChunkWord(),
                    s.rightRecordIndex(), s.leftRecordIndex(), 1, 1, s.nullRightByteOffset(), s.nullLeftByteOffset());
            try { programmeAction.perform(call); }
            catch (RuntimeException | Error original) {
                suppress(original, () -> {
                    if (!samePlacement(cut.status(), cut.pool(), cut.savedFlags().copy()))
                        assertPlacement(cut, expectedStatus, cut.pool(), expectedSaved);
                }); throw original;
            }
            assertPlacement(cut, expectedStatus, cut.pool(), expectedSaved);
        }
        c.effects.add(new ProgrammeActionReturned(action));
    }
    private void draw(Cursor c) {
        if (drawObject == null) throw unsupported(UnsupportedBoundary.DRAW_OWNER, c.whole, c.state.routine());
        var cut = capture(c); var call = new DrawCall(cut, c.state);
        var expected = CdObjectPool.overlay(cut.pool(), List.of(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() & 0x7F)));
        try { drawObject.drawObject(call); }
        catch (RuntimeException | Error original) {
            suppress(original, () -> {
                if (!samePlacement(cut.status(), cut.pool(), cut.savedFlags().copy())) assertPlacement(cut, expected);
            }); throw original;
        }
        assertPlacement(cut, expected);
        c.state = placement.pool().state(c.whole); c.effects.add(new DrawReturned());
    }
    private CdObjectPlacementRuntime.Snapshot capture(Cursor c) {
        var cut = placement.capture(); requireSource(cut, c.state); assertPlacement(cut, cut.pool()); return cut;
    }
    private void readOnly(CdObjectPlacementRuntime.Snapshot cut, Runnable callback) {
        assertPlacement(cut, cut.pool());
        try { callback.run(); }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
        assertPlacement(cut, cut.pool());
    }
    private int readValue(CdObjectPlacementRuntime.Snapshot cut, IntSupplier callback) {
        assertPlacement(cut, cut.pool());
        int value;
        try { value = callback.getAsInt(); }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
        assertPlacement(cut, cut.pool()); return value;
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Status status, CdObjectPool.Snapshot pool, byte[] saved) {
        var now = placement.capture(); return now.status().equals(status)
                && Arrays.equals(now.savedFlags().copy(), saved) && now.pool().slots().equals(pool.slots());
    }
    private void assertPlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        assertPlacement(cut, cut.status(), expectedPool, cut.savedFlags().copy());
    }
    private void assertPlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPlacementRuntime.Status expectedStatus,
                                 CdObjectPool.Snapshot expectedPool, byte[] expectedSaved) {
        placement.validateRestore(cut); placement.pool().validateRestore(expectedPool);
        if (!samePlacement(expectedStatus, expectedPool, expectedSaved))
            throw new IllegalStateException("Results callback changed its actual full placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("Results source is not this full placement capture's actual image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error failed) { if (failed != original) original.addSuppressed(failed); }
    }
    private static UnsupportedResults unsupported(UnsupportedBoundary boundary, int whole, int value) {
        return new UnsupportedResults(boundary, whole, value);
    }
    private final class Cursor {
        final int whole; final List<Effect> effects = new ArrayList<>(); CdObjectState state;
        Cursor(CdObjectState state) { this.state = state; whole = state.identity().wholeIndex(); }
        void write(CdObjectState next) { placement.pool().write(next); state = next; }
        void writeRow(int selected, CdObjectState next) {
            placement.pool().write(next); if (selected == whole) state = next;
        }
    }
    private static void width(int value, int bits) {
        if (bits != 32 && (value < 0 || value >= 1 << bits))
            throw new IllegalArgumentException("Expected unsigned native " + bits + "-bit value");
    }
}
