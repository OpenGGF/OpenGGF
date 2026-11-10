package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerMotionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.TILE;

/**
 * ObjSpringBoard ID$28, scddisasm1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff,
 * Level/Palmtree Panic/Objects/Springboard/Main.asm:13-373; Object Tile ID Set.asm:7-17;
 * Object Spawner.asm:385-446. No FixBugs conditional occurs in this closure.
 *
 * <p>The native collision owner is ChkPlayer's four signed-byte points, NOT SolidObject or
 * TopSolidObject. No physics, sound, allocation, input, clock or current-art owner is invoked.
 * Init RETURNS without AnimateObject/DrawObject; the outer CheckObjDespawn still runs.
 *
 * <p>Each own/primary store is published at its native instruction position. A primary call
 * carries the actual source and complete expected State/Environment plus a prebuilt one-store
 * output State. Root must validate its open World/Frame/source, placement cut, entire expected pair
 * and the original currently pending receipt before preparing/applying that one store. Environment
 * is untouched. Constructed data and detached effects confer no source or publication authority.
 * Root's Frame projection/binding and failed-World closure remain unowned here.
 *
 * <p>Only the existing placement owner is mutated. Pending receipts, local cursors and nonreentry
 * guards are invocation bookkeeping, never a gameplay capture partition. Original failures retain
 * all completed prefixes; no success result or effect list is returned/replayed after a throw.
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdSpringboard {
    private CdOriginalMechanismProtocol mechanismProtocol;
    public boolean isCurrentOriginalOperation(CdOriginalMechanismProtocol.Call call){return mechanismProtocol!=null&&mechanismProtocol.current(call);}

    public static final int OBJECT_ID = 0x28;

    public enum NativeRoutine { INIT, NORMAL_MAIN, FLIP_MAIN, NORMAL_MAIN_2, FLIP_MAIN_2, NORMAL_FLING, FLIP_FLING }
    public enum PrimaryField {
        CENTRE_Y_LONG(0x0C, 4), CENTRE_Y_WORD(0x0C, 2), Y_VELOCITY(0x12, 2),
        FLAGS(0x22, 1), PLAYER_JUMP(0x3C, 1), STICK(0x38, 1),
        Y_RADIUS(0x16, 1), X_RADIUS(0x17, 1), ANIMATION(0x1C, 1);
        private final int byteOffset, byteWidth;
        PrimaryField(int byteOffset, int byteWidth) { this.byteOffset = byteOffset; this.byteWidth = byteWidth; }
        public int byteOffset() { return byteOffset; }
        public int byteWidth() { return byteWidth; }
    }

    public sealed interface Effect permits RoutineEntered, PrimaryStored, Animated, Drawn, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    /** Completed scalar observation; never a request to apply a store again. */
    public record PrimaryStored(PrimaryField field, int value) implements Effect {
        public PrimaryStored { Objects.requireNonNull(field); }
    }
    public record Animated() implements Effect { }
    public record Drawn() implements Effect { }
    public record DespawnChecked(boolean deleted) implements Effect { }

    /** Raw native bytes and the pre-scroll camera word; no period normalization. */
    public record Globals(int timeZone, int timeWarpDirection, int cameraXWord) {
        public Globals { byteValue(timeZone); byteValue(timeWarpDirection); wordValue(cameraXWord); }
    }
    /** Already serviced primary0 cut. Debug's high BYTE is read from this actual Environment. */
    public record PrimaryInputs(CdPlayerBody.State state, CdPlayerBody.Environment environment) {
        public PrimaryInputs { Objects.requireNonNull(state); Objects.requireNonNull(environment); }
    }

    /**
     * Format-only component ingress. Short tile/hitbox views fail only at an actually reached read,
     * after preceding native stores. Even extra bytes never extend the reviewed72/32-byte domains.
     * The full raw mapping LONG is stored without address masking. Visual metadata supplies the real
     * six frames/five scripts; this component never resolves a mapping, selects art or draws pixels.
     */
    public record Assets(CdAssetBytes objectTileIds, CdAssetBytes hitboxes,
                         CdObjectVisualAssets visual, int mappingsAddressBits) {
        public Assets {
            Objects.requireNonNull(objectTileIds); Objects.requireNonNull(hitboxes); Objects.requireNonNull(visual);
            if (visual.mappingTableOffset() != 0 || visual.mappingFrames().size() != 6 || visual.animations().size() != 5)
                throw new IllegalArgumentException("Springboard requires six counted frames and five native scripts");
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces())
                if (piece.tileOffset() > 21 - piece.tileCount())
                    throw new IllegalArgumentException("Springboard exceeds its existing21-tile source");
        }
        /** Genuine retained USA R12B metadata only; no placement/Frame/World/current admission. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B metadata");
            return new Assets(owner.view(CdR12bObjectAssets.View.OBJECT_TILE_IDS),
                    owner.view(CdR12bObjectAssets.View.SPRINGBOARD_HITBOXES), owner.springboard(),
                    CdR12bObjectAssets.View.MAP_SPR_SPRINGBOARD.range().nativeAddress());
        }
    }

    public record Publication(long ordinal, CdObjectPlacementRuntime.Snapshot cut,
                              CdObjectState source, CdObjectState output, int savedIndex) { }
    public record PrimaryCall(long ordinal, CdObjectPlacementRuntime.Snapshot cut, CdObjectState source,
                              CdPlayerBody.State expectedState, CdPlayerBody.Environment expectedEnvironment,
                              CdPlayerBody.State outputState, PrimaryField field, int expectedValue, int value) {
        public PrimaryCall {
            Objects.requireNonNull(cut); Objects.requireNonNull(source); Objects.requireNonNull(expectedState);
            Objects.requireNonNull(expectedEnvironment); Objects.requireNonNull(outputState); Objects.requireNonNull(field);
        }
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        public DrawCall { Objects.requireNonNull(cut); Objects.requireNonNull(source); }
    }
    @FunctionalInterface public interface PrimaryCommit { void commit(PrimaryCall call); }
    @FunctionalInterface public interface SourceDraw { void draw(DrawCall call); }
    public interface DispatchObserver {
        void beforePublication(Publication call);
        void afterPublication(Publication call);
        void beforeCall(Object call);
        void afterCall(Object call);
    }

    public record Result(CdObjectState state, CdPlayerBody.State primary, CdPlayerBody.Environment environment,
                         List<Effect> effects) {
        public Result { Objects.requireNonNull(state); Objects.requireNonNull(primary); Objects.requireNonNull(environment); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(e -> e instanceof DespawnChecked c && c.deleted()); }
    }

    public enum UnsupportedBoundary { ROUTINE_DISPATCH, TILE_ADDRESS, HITBOX_ADDRESS, SAVED_FLAGS_ADDRESS }
    public static final class UnsupportedSpringboard extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedSpringboard(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Springboard whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    private static final DispatchObserver NO_OBSERVER = new DispatchObserver() {
        public void beforePublication(Publication call) { }
        public void afterPublication(Publication call) { }
        public void beforeCall(Object call) { }
        public void afterCall(Object call) { }
    };
    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final PrimaryCommit primaryCommit;
    private final SourceDraw draw;
    private final DispatchObserver observer;
    private boolean running;
    private Object pending;
    private long ordinal;


    public static CdSpringboard fromOriginalPresentMechanism(CdObjectPlacementRuntime placement,CdR11aAssets owner,
            PrimaryCommit primary,SourceDraw draw,CdOriginalMechanismProtocol.Observer observer)throws java.io.IOException{
        CdOriginalPresentObjectSource.require(placement,owner);var o=owner.openingObjects();
        var body=new CdSpringboard(placement,new Assets(o.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes(),o.view(CdR11aObjectAssets.Table.MECHANISM_BOARD_HITBOXES).bytes(),o.mechanismBoard(),CdR11aObjectAssets.Table.MECHANISM_BOARD_MAPPINGS.range().nativeAddress()),primary,draw);
        body.mechanismProtocol=new CdOriginalMechanismProtocol(placement,observer);return body;
    }
    public CdSpringboard(CdObjectPlacementRuntime placement, Assets assets, PrimaryCommit primaryCommit, SourceDraw draw) {
        this(placement, assets, primaryCommit, draw, NO_OBSERVER);
    }
    public CdSpringboard(CdObjectPlacementRuntime placement, Assets assets, PrimaryCommit primaryCommit, SourceDraw draw,
                         DispatchObserver observer) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.primaryCommit = Objects.requireNonNull(primaryCommit); this.draw = Objects.requireNonNull(draw);
        this.observer = Objects.requireNonNull(observer);
    }
    /** Original synchronous producer receipt only; public record equality never authenticates a call. */
    public boolean isCurrentCall(Object call) { return call != null && running && call == pending; }

    public Result run(int wholeIndex, Globals globals, PrimaryInputs inputs) {
        if (running) throw new IllegalStateException("Reentrant ObjSpringBoard dispatch");
        Objects.requireNonNull(globals); Objects.requireNonNull(inputs);
        var state = placement.pool().state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Springboard dispatch requires ID$28");
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 12)
            throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex, routine);
        running = true; ordinal = 0;if(mechanismProtocol!=null)mechanismProtocol.begin(wholeIndex);
        try {
            var c = new Cursor(wholeIndex, inputs);
            c.effects.add(new RoutineEntered(NativeRoutine.values()[routine / 2]));
            switch (routine) {
                case 0 -> init(c);
                case 2 -> main(c, false);
                case 4 -> main(c, true);
                case 6 -> main2(c, false);
                case 8 -> main2(c, true);
                case 10 -> fling(c, false);
                case 12 -> fling(c, true);
                default -> throw new AssertionError(routine);
            }
            if (routine != 0) animateAndDraw(c);
            boolean deleted = checkDespawn(c, globals); // JMP after returning routine, even Init.
            c.effects.add(new DespawnChecked(deleted));
            return new Result(c.state(), c.primary, c.environment, c.effects);
        } finally { if(mechanismProtocol!=null)mechanismProtocol.end();pending = null; running = false; }
    }

    /** Init:128-154. Each native store is observable BEFORE the next selected read/store. */
    private void init(Cursor c) {
        store(c.state().moveLong(MAPPINGS, assets.mappingsAddressBits()));
        store(c.state().moveByte(SPRITE_FLAGS, c.state().spriteFlags() | 4));
        store(c.state().moveByte(PRIORITY, 3));
        store(c.state().moveByte(WIDTH, 0x10));
        store(c.state().moveByte(X_RADIUS, 0x18));
        store(c.state().moveByte(Y_RADIUS, 4));
        int relative = (short) tileWord(c.whole, 14); // selector7, MOVE.W directory before oSubtype2.
        // Two signed WORD indices are applied by distinct LEA/read addressing operations;
        // their final address sum is not truncated to a WORD.
        int selected = relative + c.state().subtype2() * 2;
        store(c.state().moveWord(TILE, tileWord(c.whole, selected)));
        boolean flipped = c.state().subtype() != 0 || (c.state().spriteFlags() & 1) != 0;
        if (flipped) {
            store(c.state().moveByte(SPRITE_FLAGS, c.state().spriteFlags() & 0xFE));
            store(c.state().moveByte(FLAGS, c.state().flags() & 0xFE));
        }
        store(c.state().moveByte(ANIMATION, flipped ? 4 : 3));
        store(c.state().moveByte(ROUTINE, flipped ? 4 : 2));
    }

    private void main(Cursor c, boolean flipped) {
        if (checkPlayer(c, flipped ? 3 : 5)) {
            int y = c.state().longBits(CdObjectState.LongField.Y_POSITION) - (c.primary.motion().yRadius() << 16);
            primary(c, PrimaryField.CENTRE_Y_LONG, y); // LONG replaces BOTH integer/fraction words.
            store(c.state().moveByte(ROUTINE, flipped ? 12 : 10));
            store(c.state().moveByte(ANIMATION, flipped ? 4 : 3));
        }
    }
    private void main2(Cursor c, boolean flipped) {
        if (!checkPlayer(c, flipped ? 3 : 5)) {
            store(c.state().moveByte(ROUTINE, flipped ? 4 : 2));
            if ((c.primary.motion().flags() & 2) != 0)
                store(c.state().moveByte(ROUTINE, flipped ? 12 : 10));
            if (c.state().routine() == (flipped ? 12 : 10))
                store(c.state().moveByte(SPRINGBOARD_FLING, 0x40));
        }
    }
    private void fling(Cursor c, boolean flipped) {
        store(c.state().moveByte(ANIMATION, flipped ? 2 : 1));
        if (checkPlayer(c, flipped ? 4 : 6)) {
            int d0 = (c.primary.motion().yVelocity() + 0x100) & 0xFFFF;
            if (((d0 - 0xA00) & 0x8000) == 0) d0 = 0xA00; // CMPI.W/BMI, N alone.
            primary(c, PrimaryField.Y_VELOCITY, (short) -d0);
            store(c.state().moveByte(SPRINGBOARD_FLING, 0x40));
            boolean wasAir = (c.primary.motion().flags() & 2) != 0;
            primary(c, PrimaryField.FLAGS, c.primary.motion().flags() | 2);
            if (wasAir) primary(c, PrimaryField.PLAYER_JUMP, 0);
            primary(c, PrimaryField.FLAGS, c.primary.motion().flags() & 0xDF);
            primary(c, PrimaryField.STICK, 0);
            primary(c, PrimaryField.Y_RADIUS, 0x13);
            primary(c, PrimaryField.X_RADIUS, 9);
            if ((c.primary.motion().flags() & 4) == 0) {
                primary(c, PrimaryField.Y_RADIUS, 0xE);
                primary(c, PrimaryField.X_RADIUS, 7);
                primary(c, PrimaryField.CENTRE_Y_WORD, ((c.primary.motion().centreY16_16() >>> 16) + 5) & 0xFFFF);
                primary(c, PrimaryField.FLAGS, c.primary.motion().flags() | 4);
                primary(c, PrimaryField.ANIMATION, 2);
            } else primary(c, PrimaryField.FLAGS, c.primary.motion().flags() | 0x10);
        }
        int timer = (c.state().unsignedByte(SPRINGBOARD_FLING) - 1) & 0xFF;
        store(c.state().moveByte(SPRINGBOARD_FLING, timer));
        if (timer == 0) {
            // Flip:264-266 resets timer FIRST; Normal:368-370 resets it LAST.
            if (flipped) store(c.state().moveByte(SPRINGBOARD_FLING, 0x40));
            store(c.state().moveByte(ROUTINE, flipped ? 4 : 2));
            store(c.state().moveByte(ANIMATION, flipped ? 4 : 3));
            if (!flipped) store(c.state().moveByte(SPRINGBOARD_FLING, 0x40));
        }
    }

    /** ChkPlayer:13-101. Every BPL tests the wrapped CMP.W result's sign, without V correction. */
    private boolean checkPlayer(Cursor c, int selector) {
        var m = c.primary.motion();
        if ((c.environment.globals().debugMode() >>> 8) != 0 || m.routine() >= 6 || m.yVelocity() < 0)
            return collision(c, false); // No hitbox bytes are read on these branches.
        int at = (selector & 7) * 4;
        int ox = c.state().centreX(), px = m.centreX16_16() >>> 16, rx = (byte) m.xRadius();
        int right = boxByte(c.whole, at);
        if (bpl((px - rx) - (ox + right))) return collision(c, false);
        int left = boxByte(c.whole, at + 1);
        if (bpl((ox + left) - (px + rx))) return collision(c, false);
        int oy = c.state().centreY(), py = m.centreY16_16() >>> 16, ry = (byte) m.yRadius();
        int bottom = boxByte(c.whole, at + 2);
        if (bpl((py - ry) - (oy + bottom))) return collision(c, false);
        int top = boxByte(c.whole, at + 3);
        if (bpl((oy + top) - (py + ry))) return collision(c, false);
        return collision(c, true);
    }
    private boolean collision(Cursor c, boolean hit) {
        store(c.state().moveByte(FLAGS, hit ? c.state().flags() | 8 : c.state().flags() & 0xF7));
        return hit;
    }
    private static boolean bpl(int value) { return (value & 0x8000) == 0; }
    private int boxByte(int whole, int offset) {
        if (offset < 0 || offset >= 32 || offset >= assets.hitboxes().length())
            throw unsupported(UnsupportedBoundary.HITBOX_ADDRESS, whole, offset);
        return (byte) assets.hitboxes().unsignedByte(offset);
    }
    private int tileWord(int whole, int offset) {
        if (offset < 0 || offset > (mechanismProtocol==null?70:72) || offset > assets.objectTileIds().length() - 2)
            throw unsupported(UnsupportedBoundary.TILE_ADDRESS, whole, offset);
        return assets.objectTileIds().unsignedWord(offset);
    }

    private void primary(Cursor c, PrimaryField field, int value) {
        var expected = c.primary;
        var call = new PrimaryCall(++ordinal, placement.capture(), c.state(), expected, c.environment,
                project(expected, field, value), field, fieldValue(expected.motion(), field), value);
        invoke(call, () -> primaryCommit.commit(call));
        c.primary = call.outputState();
        c.effects.add(new PrimaryStored(field, value));
    }
    /** Only this component's nine exact store sites can alter the primary; all other partitions are reused. */
    private static CdPlayerBody.State project(CdPlayerBody.State s, PrimaryField field, int value) {
        var m = s.motion();
        int y = m.centreY16_16(), yv = m.yVelocity(), flags = m.flags(), jump = m.playerJump(), stick = m.stick();
        int yr = m.yRadius(), xr = m.xRadius(), anim = m.animation();
        switch (field) {
            case CENTRE_Y_LONG -> y = value;
            case CENTRE_Y_WORD -> y = ((value & 0xFFFF) << 16) | (y & 0xFFFF);
            case Y_VELOCITY -> yv = (short) value;
            case FLAGS -> flags = value & 0xFF;
            case PLAYER_JUMP -> jump = value & 0xFF;
            case STICK -> stick = value & 0xFF;
            case Y_RADIUS -> yr = value & 0xFF;
            case X_RADIUS -> xr = value & 0xFF;
            case ANIMATION -> anim = value & 0xFF;
        }
        var next = new CdPlayerMotionState(m.centreX16_16(), y, m.xVelocity(), yv, m.groundVelocity(), m.routine(),
                m.angle(), flags, m.spriteFlags(), xr, yr, anim, m.previousAnimation(), jump, m.charge(),
                m.moveLock(), stick, m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
        return new CdPlayerBody.State(next, s.animation(), s.header(), s.timers(), s.history(),
                s.rotationDistance(), s.objectLinks(), s.rotationAngle());
    }
    private static int fieldValue(CdPlayerMotionState m, PrimaryField field) {
        return switch (field) {
            case CENTRE_Y_LONG -> m.centreY16_16();
            case CENTRE_Y_WORD -> m.centreY16_16() >>> 16;
            case Y_VELOCITY -> m.yVelocity();
            case FLAGS -> m.flags();
            case PLAYER_JUMP -> m.playerJump();
            case STICK -> m.stick();
            case Y_RADIUS -> m.yRadius();
            case X_RADIUS -> m.xRadius();
            case ANIMATION -> m.animation();
        };
    }

    private void animateAndDraw(Cursor c) {
        if(mechanismProtocol!=null){
            mechanismProtocol.animate(new CdObjectAnimation.AnimationTable(assets.visual().animationTable(),0,5),()->running,state->{},read->new UnsupportedOperationException("Original Springboard animation read "+read));
            c.effects.add(new Animated());var call=new DrawCall(placement.capture(),c.state());invoke(call,()->draw.draw(call));c.effects.add(new Drawn());return;
        }
        var s = c.state();
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),
                s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2)),
                new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, 5));
        store(s.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame()).moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2()));
        c.effects.add(new Animated());
        var call = new DrawCall(placement.capture(), c.state());
        invoke(call, () -> draw.draw(call));
        c.effects.add(new Drawn()); // Subsequent reads always reload the LIVE pool, including Draw's ID/flags/X/saved writes.
    }

    private void store(CdObjectState output) {
        var call = new Publication(++ordinal, placement.capture(), placement.pool().state(output.identity().wholeIndex()), output, -1);
        publish(call, () -> placement.pool().write(output));
    }
    private void publish(Publication call, Runnable store) {
        if (pending != null) throw new IllegalStateException("Nested Springboard operation");
        pending = call;
        try { observer.beforePublication(call);
            if(mechanismProtocol==null)store.run();
            else if(call.savedIndex()>=2)mechanismProtocol.saved(call.savedIndex(),call.cut().savedFlags().unsignedByte(call.savedIndex())&127,store);
            else if(call.output()==null)mechanismProtocol.delete(store);else mechanismProtocol.store(call.output(),store);
            observer.afterPublication(call); }
        finally { pending = null; }
    }
    private void invoke(Object call, Runnable work) {
        if (pending != null) throw new IllegalStateException("Nested Springboard operation");
        pending = call;
        try { observer.beforeCall(call);if(mechanismProtocol==null)work.run();else mechanismProtocol.callback(call,work); observer.afterCall(call); }
        finally { pending = null; }
    }

    /** Shared native CheckObjDespawn order, Object Spawner.asm:385-446, over post-Draw live values. */
    private boolean checkDespawn(Cursor c, Globals globals) {
        var s = c.state();
        int x = s.centreX();
        if (s.signedByte(SPRITE_FLAGS) >= 0) {
            int distance = ((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF;
            if (distance > 0x280) return despawn(c, globals);
        }
        return (globals.timeZone() & 0x80) != 0 && despawn(c, globals);
    }
    private boolean despawn(Cursor c, Globals globals) {
        var s = c.state();
        int savedId = s.savedFlagsId();
        if (savedId != 0) {
            int period = globals.timeZone() & 0x7F;
            if ((globals.timeZone() & 0x80) != 0) {
                int adjusted = (period - (byte) globals.timeWarpDirection()) & 0xFFFF;
                period = (short) adjusted < 0 ? 0 : adjusted < 3 ? adjusted : 2;
            }
            int index = 2 + (short) ((savedId * 3 + period) & 0xFFFF);
            if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
                throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c.whole, index);
            int bodyOffset = index - 2;
            var call = new Publication(++ordinal, placement.capture(), s, s, index);
            publish(call, () -> placement.bclrSavedFlag(bodyOffset / 3, bodyOffset % 3, CdObjectPlacementRuntime.SavedBit.LOADED));
        }
        var live = c.state();
        var call = new Publication(++ordinal, placement.capture(), live, null, -1);
        publish(call, () -> placement.pool().deleteObject(c.whole));
        return true;
    }

    private final class Cursor {
        final int whole;
        final CdPlayerBody.Environment environment;
        final List<Effect> effects = new ArrayList<>();
        CdPlayerBody.State primary;
        Cursor(int whole, PrimaryInputs inputs) { this.whole = whole; primary = inputs.state(); environment = inputs.environment(); }
        CdObjectState state() { return placement.pool().state(whole); }
    }
    private static UnsupportedSpringboard unsupported(UnsupportedBoundary boundary, int whole, int value) {
        return new UnsupportedSpringboard(boundary, whole, value);
    }
    private static void byteValue(int n) { if ((n & ~0xFF) != 0) throw new IllegalArgumentException("Expected unsigned native BYTE"); }
    private static void wordValue(int n) { if ((n & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected unsigned native WORD"); }
}
