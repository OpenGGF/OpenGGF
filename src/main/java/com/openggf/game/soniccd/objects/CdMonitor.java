package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTerrain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.IntSupplier;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.*;

/**
 * Native ID $19 Monitor branch, subtypes0..7 and routines0/2/4/6/8. Source pin
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Level/_Objects/Monitor and Time Post/Main.asm:
 * ObjMonitor_GetSavedFlags (:207-236), ObjMonitor_Solid (:242-259), ObjMonitorTimepost through
 * ObjMonitor_Break (:269-409). Shipped FixBugs=0; no fixed alternative is selected here.
 * One invocation is one already selected live physical-slot dispatch, never a frame or pool walk.
 *
 * <p>The single placement owner stores the actual 766-byte saved bank. Its checked ID/period APIs
 * address the native helper's linear byte, including ID0 and raw non-warp timeZone values. No bank,
 * player, global, support context or call-cut capture is retained. Unknown post/HUD and finite table
 * entries stop before their entries write. A failed synchronous call retains preceding live prefixes.
 *
 * <p>Solid publishes working a0, captures the actual pool, invokes SOLID_OBJECT once and requires
 * Root's synchronous adapter to publish all result images and exact player/global writes. Falling
 * uses the shared raw integrator followed by the existing object floor probe and one PRIMARY_ANGLE
 * publication, then its integer-word correction. DrawObject and FM execute at the native cuts.
 * Completed metadata is observational: never replay it as pool/global/sound work. Root owns live
 * phase/CAS/nonreentry, authenticated assets, current inputs and later physical child scheduling.
 * <p>The completed-only Solid/floor callback description above is the legacy constructor8 path.
 * Named Past mode uses the same shared algorithms with genuine Solid outcomes and synchronous raw
 * floor-angle stores. Its guards cover this placement owner's full96/saved766/status; the caller
 * still owns actual Frame, reserved/source/current state, once-only sound and invocation authority.
 * A native stopped Solid is adopted once, then its original exception is rethrown before later work.
 * The inherited Monitor animator remains atomic on a shared step failure; no prefix is invented.
 * OpenAI Codex GPT-6.1 Sol <noreply@openai.com>.
 */
public final class CdMonitor {
    public static final int OBJECT_ID = 0x19;
    private static final int MAPPING_BYTES = 450, ANIMATION_BYTES = 136;

    public enum NativeRoutine { INIT, MAIN, BREAK, ANIMATE, DRAW }
    public enum NativeCall { OBJ_MOVE_GRV, ANIMATE_OBJECT, DRAW_OBJECT, CHECK_OBJ_DESPAWN }
    public enum FmSound { FM_DESTROY }

    public sealed interface Effect permits RoutineEntered, Solid, FloorProbed, PlayedFm,
            CallReturned, SavedFlagChanged, Allocation, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record Solid(CdSolidObject.Result result) implements Effect {
        public Solid { Objects.requireNonNull(result); }
    }
    public record FloorProbed(CdObjectCollisionCheck.FloorDistance result) implements Effect {
        public FloorProbed { Objects.requireNonNull(result); }
    }
    public record PlayedFm(FmSound sound) implements Effect {
        public PlayedFm { Objects.requireNonNull(sound); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    /** A completed BCLR/BSET, including a same-value native store, at the existing bank byte. */
    public record SavedFlagChanged(int byteIndex, int previousValue, int value) implements Effect { }
    /** Each FindObjSlot already returned, and a present slot already contains the child ID/prefix. */
    public record Allocation(int objectId, OptionalInt wholeIndex) implements Effect {
        public Allocation { byteValue(objectId); Objects.requireNonNull(wholeIndex); }
    }
    public record Deleted() implements Effect { }

    /** Raw ephemeral native inputs, not safe-work flags or retained global storage. */
    public record Globals(int rawTimeZone, int rawTimeWarpDirection, int cameraXWord,
                          int timeAttackMode, int zone, int layer, int timeStopTimer) {
        public Globals {
            byteValue(rawTimeZone); byteValue(rawTimeWarpDirection); wordValue(cameraXWord);
            byteValue(timeAttackMode); byteValue(zone); byteValue(layer); wordValue(timeStopTimer);
        }
    }
    public record SolidInputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public SolidInputs { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }

    /**
     * Complete raw MA/MM includes, not synthesized script/map tables. A constructed declaration confers
     * no authentication or residency. Production enters through the existing authenticated USA owner.
     * Odd item first-piece roots are computed from these actual retained MM words by CdMonitorItem.
     */
    public record Assets(CdObjectVisualAssets visual, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(visual);
            if (visual.mappingTableOffset() != 0 || visual.mappingFrames().size() != 20
                    || visual.animations().size() != 10 || visual.mappings().length() != MAPPING_BYTES
                    || visual.animationTable().length() != ANIMATION_BYTES) {
                throw new IllegalArgumentException("Monitor requires the complete twenty-frame/ten-script includes");
            }
            if (mappingsAddress <= 0 || (mappingsAddress & 1) != 0
                    || mappingsAddress > 0xFFFFFF - (MAPPING_BYTES - 1)) {
                throw new IllegalArgumentException("Monitor mappings require an aligned bounded native root");
            }
        }
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner);
            return new Assets(owner.monitorTime(),
                    owner.view(CdR11aObjectAssets.Table.MONITOR_TIME_MAPPINGS).range().nativeAddress());
        }

        /** Authenticated original USA B metadata only; art remains with the existing section owner. */
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "original Past assets");
            var identity = Objects.requireNonNull(owner.identity(), "Past identity");
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R11B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R11B.stage())) {
                throw new IllegalArgumentException("Monitor Past assets require authenticated original USA R11B Word RAM");
            }
            var mapping = owner.view(CdR11bAssets.Asset.MAP_SPR_MONITOR_TIME);
            var animation = owner.view(CdR11bAssets.Asset.ANI_MONITOR);
            return new Assets(CdObjectVisualAssets.decode(mapping.bytes().copy(), 20,
                    animation.bytes().copy(), 10, 82), mapping.range().nativeAddress());
        }
    }

    /** One actual pure Solid call and the live expected values Root must validate before publication. */
    public record SolidCall(CdObjectPool.Snapshot callCut, CdSolidObject.Player expectedPlayer,
                            CdSolidObject.Globals expectedGlobals, CdSolidObject.Result result) {
        public SolidCall {
            Objects.requireNonNull(callCut); Objects.requireNonNull(expectedPlayer);
            Objects.requireNonNull(expectedGlobals); Objects.requireNonNull(result);
        }
    }
    /**
     * Root validates the open object phase, expected player/globals and every affected slot against
     * callCut, commits result.object/otherSlots, patches exactly the native player/global write sets,
     * and forwards nested FM calls once in source order. Reject stale/closed phases; never retain or
     * replay this call. The body verifies all result object images are live before continuing.
     */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }
    /** Exactly one returning ObjGetFloorDist publication, including a same-value PRIMARY_ANGLE write. */
    @FunctionalInterface public interface PrimaryAngleCommit { void commit(int expectedPrimaryAngle, int primaryAngle); }
    /** Actual synchronous DrawObject cull/enqueue over this pool; no mapping dereference/body reentry. */
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }
    /** Actual PlayFMSound cut; the sound owner supplies the native release-specific ID and I/O. */
    @FunctionalInterface public interface PlayFmCall { void playFm(FmSound sound); }

    public record Result(CdObjectState state, CdSolidObject.Player finalPlayer, CdSolidObject.Globals finalGlobals,
                         List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(finalPlayer); Objects.requireNonNull(finalGlobals);
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum UnsupportedBoundary { TIME_POST_OR_HUD, ROUTINE_DISPATCH, SAVED_FLAGS_ADDRESS }
    public static final class UnsupportedMonitor extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private UnsupportedMonitor(UnsupportedBoundary boundary, int wholeIndex, int value) {
            super(boundary + " at Monitor whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
    }

    /** Raw sources are read only where the native branch reaches each addressed input. */
    public interface PastGlobals {
        int rawTimeZone();
        int rawTimeWarpDirection();
        int cameraXWord();
        int timeAttackMode();
        int zone();
        int layer();
        int timeStopTimer();
    }
    /** Actual shared readback; values confer no private invocation or publication authority. */
    public record LiveSolid(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public LiveSolid { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }
    public record PrefixSolidCut(CdObjectState.Identity objectIdentity, CdObjectPool.Snapshot callCut,
                                 CdSolidObject.Player expectedPlayer, CdSolidObject.Gates expectedGates,
                                 CdSolidObject.Globals expectedGlobals) {
        public PrefixSolidCut {
            Objects.requireNonNull(objectIdentity); Objects.requireNonNull(callCut); Objects.requireNonNull(expectedPlayer);
            Objects.requireNonNull(expectedGates); Objects.requireNonNull(expectedGlobals);
        }
    }
    /** The genuine shared Outcome, including a nonreturning prefix without a fabricated Result/d0. */
    public record PrefixSolidCall(PrefixSolidCut cut, CdSolidObject.Outcome outcome) {
        public PrefixSolidCall {
            Objects.requireNonNull(cut); Objects.requireNonNull(outcome);
            var entry = outcome instanceof CdSolidObject.Completed done ? done.completedResult().entry()
                    : ((CdSolidObject.NativeStopped) outcome).prefix().entry();
            if (entry != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalArgumentException("Monitor prefix call requires genuine plain SolidObject");
            }
        }
    }
    /**
     * Root validates open phase/full Frame/source before execution and prepares all immutable output
     * values before publication. Commit returns actual readback after adopting the genuine Outcome once.
     * NativeStopped grants no native return value, resumption, command replay or invocation authority.
     */
    public interface PrefixSolidOwner {
        LiveSolid preflight(PrefixSolidCut cut);
        LiveSolid commit(PrefixSolidCall call);
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdSolidObject solid;
    private final CdPlayerTerrain terrain;
    private final SolidCommit solidCommit;
    private final PrimaryAngleCommit primaryAngleCommit;
    private final DrawObjectCall drawObject;
    private final PlayFmCall playFm;
    // Only the distinct private Past constructor assigns this mode; constructor8 retains legacy behavior.
    private PrefixSolidOwner pastSolidOwner;
    private boolean pastRunning;

    public CdMonitor(CdObjectPlacementRuntime runtime, Assets assets, CdSolidObject solid, CdPlayerTerrain terrain,
                     SolidCommit solidCommit, PrimaryAngleCommit primaryAngleCommit,
                     DrawObjectCall drawObject, PlayFmCall playFm) {
        this.runtime = Objects.requireNonNull(runtime); this.assets = Objects.requireNonNull(assets);
        this.solid = Objects.requireNonNull(solid); this.terrain = Objects.requireNonNull(terrain);
        this.solidCommit = Objects.requireNonNull(solidCommit); this.primaryAngleCommit = Objects.requireNonNull(primaryAngleCommit);
        this.drawObject = Objects.requireNonNull(drawObject); this.playFm = Objects.requireNonNull(playFm);
    }

    /** Named bounded Past ingress; no additional Solid, floor, Frame, bank or visual lifetime. */
    public static CdMonitor withPastOwner(CdObjectPlacementRuntime runtime, Assets assets,
                                          CdSolidObject solid, CdPlayerTerrain terrain,
                                          PrefixSolidOwner owner, PrimaryAngleCommit primaryAngleCommit,
                                          DrawObjectCall drawObject, PlayFmCall playFm) {
        return new CdMonitor(runtime, assets, solid, terrain, owner, primaryAngleCommit, drawObject, playFm, true);
    }

    /** Distinct private arity preserves public constructor8 and literal-null overload resolution. */
    private CdMonitor(CdObjectPlacementRuntime runtime, Assets assets, CdSolidObject solid, CdPlayerTerrain terrain,
                      PrefixSolidOwner owner, PrimaryAngleCommit primaryAngleCommit,
                      DrawObjectCall drawObject, PlayFmCall playFm, boolean pastMode) {
        if (!pastMode) throw new IllegalArgumentException("Private Monitor constructor requires Past mode");
        this.runtime = Objects.requireNonNull(runtime); this.assets = Objects.requireNonNull(assets);
        this.solid = Objects.requireNonNull(solid); this.terrain = Objects.requireNonNull(terrain);
        this.solidCommit = null; this.primaryAngleCommit = Objects.requireNonNull(primaryAngleCommit);
        this.drawObject = Objects.requireNonNull(drawObject); this.playFm = Objects.requireNonNull(playFm);
        this.pastSolidOwner = Objects.requireNonNull(owner);
    }

    /** Legacy value ingress retains its exact eager Globals and completed-only A path. */
    public Result run(int wholeIndex, Globals globals, SolidInputs inputs) {
        var reads = new NativeGlobals(Objects.requireNonNull(globals));
        return pastSolidOwner == null ? runNative(wholeIndex, reads, inputs) : runPastGuarded(wholeIndex, reads, inputs);
    }

    public Result runPast(int wholeIndex, PastGlobals globals, SolidInputs inputs) {
        if (pastSolidOwner == null) throw new IllegalStateException("Monitor runPast requires named Past construction");
        return runPastGuarded(wholeIndex, new NativeGlobals(Objects.requireNonNull(globals)), inputs);
    }

    private Result runPastGuarded(int wholeIndex, NativeGlobals globals, SolidInputs inputs) {
        if (pastRunning) throw new IllegalStateException("Reentrant Past Monitor invocation");
        pastRunning = true;
        try { return runNative(wholeIndex, globals, inputs); }
        finally { pastRunning = false; }
    }

    /** ObjMonitorTimepost/ObjMonitor (:269-294), raw byte-indexed native dispatch. */
    private Result runNative(int wholeIndex, NativeGlobals globals, SolidInputs inputs) {
        Objects.requireNonNull(globals); Objects.requireNonNull(inputs);
        var pool = runtime.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Monitor requires live ID $19 at whole " + wholeIndex);
        var c = new Cursor(pool, state, inputs);
        if (state.subtype() == 0 && globals.timeAttackMode() != 0) {
            checkObjDespawn(c, globals); // Native JMP, no routine lookup, initialization or DrawObject.
            return c.finish();
        }
        if (state.subtype() >= 8) throw unsupported(UnsupportedBoundary.TIME_POST_OR_HUD, c, state.subtype());
        int routine = state.routine();
        if ((routine & 1) != 0 || routine > 8) throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, routine);
        switch (routine) {
            case 0 -> { if (init(c, globals)) main(c, globals); }
            case 2 -> main(c, globals);
            case 4 -> breakMonitor(c, globals);
            case 6 -> animate(c, globals);
            case 8 -> drawTail(c, globals);
        }
        return c.finish();
    }

    /** ObjMonitor_Init (:300-330). Already-broken Init RTS bypasses even Draw/despawn this dispatch. */
    private boolean init(Cursor c, NativeGlobals globals) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveByte(Y_RADIUS, 14).moveByte(X_RADIUS, 14)
                .moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, 0x5A8).moveByte(PRIORITY, 3);
        if (pastSolidOwner != null) c.publish(); // Native stores precede the reached zone source read.
        if (globals.zone() == 6 && c.state.unsignedByte(LAYER) == 0) {
            c.state = c.state.moveWord(TILE, c.state.word(TILE) | 0x8000).moveByte(PRIORITY, 0);
        }
        c.state = c.state.moveByte(SPRITE_FLAGS, 4).moveByte(WIDTH, 15);
        c.publish(); // The complete native Init prefix precedes GetSavedFlags' reached address.
        var at = savedAddress(c, globals);
        int old = runtime.savedFlag(at.id(), at.period());
        runtime.bclrSavedFlag(at.id(), at.period(), CdObjectPlacementRuntime.SavedBit.LOADED);
        int value = runtime.savedFlag(at.id(), at.period());
        c.effects.add(new SavedFlagChanged(at.byteIndex(), old, value));
        if ((value & 1) != 0) {
            c.state = c.state.moveByte(ROUTINE, 8).moveByte(MAP_FRAME, 17);
            c.publish();
            return false;
        }
        c.state = c.state.moveByte(COLLISION_TYPE, 0x46).moveByte(ANIMATION, c.state.subtype());
        return true; // Fallthrough. New sprite flags4 select Main's offscreen Draw immediately.
    }

    /** ObjMonitor_Main (:336-357): sprite sign gates all movement, floor, Solid and animation. */
    private void main(Cursor c, NativeGlobals globals) {
        c.enter(NativeRoutine.MAIN);
        if ((c.state.spriteFlags() & 0x80) == 0) { drawTail(c, globals); return; }
        if (c.state.unsignedByte(ROUTINE_2) != 0) {
            c.publish();
            var moved = CdPlayerMovement.moveObjectWithGravity(rawMotion(c.state));
            c.state = c.state.moveLong(X_POSITION, moved.centreX16_16()).moveLong(Y_POSITION, moved.centreY16_16())
                    .moveWord(Y_VELOCITY, moved.yVelocity());
            c.publish(); // ObjMoveGrv has returned before the object floor probe can be reached/fail.
            c.effects.add(new CallReturned(NativeCall.OBJ_MOVE_GRV));
            CdObjectCollisionCheck.FloorDistance floor;
            if (pastSolidOwner == null) {
                floor = CdObjectCollisionCheck.objGetFloorDist(terrain, c.state);
                primaryAngleCommit.commit(c.globals.primaryAngle(), floor.primaryAngle());
                c.globals = new CdSolidObject.Globals(c.globals.invincible(), c.globals.scoreChain(),
                        floor.primaryAngle(), c.globals.secondaryAngle());
            } else {
                floor = CdObjectCollisionCheck.objGetFloorDist(terrain, c.state, raw -> commitPastAngle(c, raw));
            }
            c.effects.add(new FloorProbed(floor));
            if (floor.distance() < 0) {
                c.state = c.state.addWord(Y, floor.distance()).moveWord(Y_VELOCITY, 0).moveByte(ROUTINE_2, 0);
            }
            c.publish();
        } else if (globals.zone() != 6
                || (globals.layer() == 0) == (c.state.unsignedByte(LAYER) == 0)) {
            callSolid(c); // ObjMonitor_Solid (:242-259): primary player, plain SOLID_OBJECT only.
        }
        animate(c, globals);
    }

    private void callSolid(Cursor c) {
        if (pastSolidOwner != null) { callPastSolid(c); return; }
        c.publish();
        var cut = c.pool.capture();
        var result = solid.run(CdSolidObject.Entry.SOLID_OBJECT, c.state, c.player, c.gates, c.globals, cut);
        solidCommit.commit(new SolidCall(cut, c.player, c.globals, result));
        if (!c.pool.state(c.wholeIndex).equals(result.object())) {
            throw new IllegalStateException("Monitor SolidCommit did not publish a0");
        }
        for (var other : result.otherSlots()) {
            if (!c.pool.state(other.identity().wholeIndex()).equals(other)) {
                throw new IllegalStateException("Monitor SolidCommit did not publish other slot " + other.identity().wholeIndex());
            }
        }
        c.state = result.object(); c.player = result.player(); c.globals = result.globals();
        c.effects.add(new Solid(result));
    }

    /** ObjMonitor_Animate (:363-367). timeStopTimer gates only this actual shared animation call. */
    private void animate(Cursor c, NativeGlobals globals) {
        c.enter(NativeRoutine.ANIMATE);
        c.publish(); // Any landing/solid/native prefix is live even if the selected script is unowned.
        if (globals.timeStopTimer() == 0) {
            var s = c.state;
            var r = CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),
                    s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                    s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2)),
                    new CdObjectAnimation.AnimationTable(assets.visual().animationTable(), 0, 10));
            c.state = s.moveByte(ANIMATION, r.animationId()).moveByte(PREVIOUS_ANIMATION, r.previousId())
                    .moveByte(ANIMATION_FRAME, r.scriptIndex()).moveByte(ANIMATION_TIME, r.timer())
                    .moveByte(MAP_FRAME, r.mapFrame()).moveByte(SPRITE_FLAGS, r.spriteFlags())
                    .moveByte(FLAGS, r.statusFlags()).moveByte(ROUTINE, r.routine()).moveByte(ROUTINE_2, r.routine2());
            c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
        }
        drawTail(c, globals);
    }

    /** ObjMonitor_Draw (:373-375), unlike Break's direct DrawObject without CheckObjDespawn. */
    private void drawTail(Cursor c, NativeGlobals globals) {
        c.enter(NativeRoutine.DRAW);
        draw(c);
        checkObjDespawn(c, globals);
    }
    private void draw(Cursor c) {
        c.publish();
        if (pastSolidOwner == null) drawObject.drawObject(c.pool, c.state.identity());
        else drawPast(c);
        c.state = c.pool.state(c.wholeIndex); // Keep DrawObject's live sprite-bit7 writes.
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    /** ObjMonitor_Break (:381-409): sound BEFORE any break store; live item search BEFORE explosion. */
    private void breakMonitor(Cursor c, NativeGlobals globals) {
        c.enter(NativeRoutine.BREAK);
        c.publish();
        if (pastSolidOwner == null) playFm.playFm(FmSound.FM_DESTROY);
        else callPastReadOnly(() -> playFm.playFm(FmSound.FM_DESTROY));
        c.effects.add(new PlayedFm(FmSound.FM_DESTROY));
        c.state = c.pool.state(c.wholeIndex).addByte(ROUTINE, 4).moveByte(COLLISION_TYPE, 0);
        c.publish();
        var item = c.pool.findObjSlot();
        if (item.isPresent()) {
            int whole = item.getAsInt();
            c.pool.write(c.pool.state(whole).moveByte(ID, CdMonitorItem.OBJECT_ID)
                    .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY())
                    .moveByte(ANIMATION, c.state.unsignedByte(ANIMATION)).moveByte(LAYER, c.state.unsignedByte(LAYER)));
        }
        c.effects.add(new Allocation(CdMonitorItem.OBJECT_ID, item));
        var explosion = c.pool.findObjSlot();
        if (explosion.isPresent()) {
            int whole = explosion.getAsInt();
            c.pool.write(c.pool.state(whole).moveByte(ID, CdExplosion.OBJECT_ID)
                    .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY())
                    .moveByte(ROUTINE_2, 1).moveByte(SUBTYPE, 1).moveByte(LAYER, c.state.unsignedByte(LAYER)));
        }
        c.effects.add(new Allocation(CdExplosion.OBJECT_ID, explosion));
        var at = savedAddress(c, globals);
        int old = runtime.savedFlag(at.id(), at.period());
        runtime.bsetSavedFlag(at.id(), at.period(), CdObjectPlacementRuntime.SavedBit.MEMBER_1);
        c.effects.add(new SavedFlagChanged(at.byteIndex(), old, runtime.savedFlag(at.id(), at.period())));
        c.state = c.state.moveByte(MAP_FRAME, 17);
        draw(c); // Direct BRA.W DrawObject: no despawn, Solid, GetOff, points, bounce or reward.
    }

    /** CheckObjDespawn/DespawnObject/DespawnObjTimeWarp (Object Spawner.asm:385-446). */
    private void checkObjDespawn(Cursor c, NativeGlobals globals) {
        int x = c.state.centreX(); // The native MOVE.W precedes its sprite-sign gate.
        boolean outside = (c.state.spriteFlags() & 0x80) == 0
                && (((x & 0xFF80) - ((globals.cameraXWord() - 0x80) & 0xFF80)) & 0xFFFF) > 0x280;
        if (outside || (globals.rawTimeZone() & 0x80) != 0) {
            if (c.state.savedFlagsId() != 0) { // Despawn skips ID0; Monitor's own helper above does not.
                var at = savedAddress(c, globals);
                int old = runtime.savedFlag(at.id(), at.period());
                runtime.bclrSavedFlag(at.id(), at.period(), CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new SavedFlagChanged(at.byteIndex(), old, runtime.savedFlag(at.id(), at.period())));
            }
            c.pool.deleteObject(c.wholeIndex); c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
        }
        c.effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN));
    }

    /** Actual address projection, not a second bank or a semantic period normalization. */
    private record SavedAddress(int id, int period, int byteIndex) { }
    private static SavedAddress savedAddress(Cursor c, NativeGlobals globals) {
        int rawTimeZone = globals.rawTimeZone();
        int period = rawTimeZone & 0x7F;
        if ((rawTimeZone & 0x80) != 0) {
            int d1 = (period - (byte) globals.rawTimeWarpDirection()) & 0xFFFF;
            period = (short) d1 < 0 ? 0 : d1 >= 3 ? 2 : d1; // EXT.W, NEG.W, ADD.W, BPL/CMP/BCS.
        }
        int offset = (3 * c.state.savedFlagsId() + period) & 0xFFFF;
        int index = 2 + (short) offset; // The native indexed d0.w is sign-extended for the address.
        if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c, index);
        }
        // The existing API checks periods0..2; quotient/remainder preserves the SAME linear byte.
        return new SavedAddress(offset / 3, offset % 3, index);
    }

    /** One shared plain Solid call, with preflight and full96 publication checks on both outcomes. */
    private void callPastSolid(Cursor c) {
        c.publish();
        var cut = new PrefixSolidCut(c.state.identity(), c.pool.capture(), c.player, c.gates, c.globals);
        var before = runtime.capture();
        try {
            requireLive(c.player, c.gates, c.globals, pastSolidOwner.preflight(cut));
            requireStorage(before, cut.callCut());
        } catch (RuntimeException | Error failure) {
            verifySuppressed(failure, () -> requireStorage(before, cut.callCut()));
            throw failure;
        }
        var outcome = solid.runWithPrefix(CdSolidObject.Entry.SOLID_OBJECT, c.state,
                c.player, c.gates, c.globals, cut.callCut());
        var completed = outcome instanceof CdSolidObject.Completed done ? done.completedResult() : null;
        var stopped = outcome instanceof CdSolidObject.NativeStopped stop ? stop : null;
        CdObjectState object;
        List<CdObjectState> others;
        CdSolidObject.Player player;
        CdSolidObject.Globals globals;
        if (completed != null) {
            object = completed.object(); others = completed.otherSlots(); player = completed.player(); globals = completed.globals();
        } else {
            var prefix = stopped.prefix();
            object = prefix.object(); others = prefix.otherSlots(); player = prefix.player(); globals = prefix.globals();
        }
        if (!object.identity().equals(c.state.identity())) throw new IllegalArgumentException("Monitor Solid changed a0 identity");
        var images = new ArrayList<CdObjectState>(1 + others.size());
        images.add(object); images.addAll(others);
        var ready = CdObjectPool.overlay(cut.callCut(), images);
        c.pool.validateRestore(ready);
        requireStorage(before, cut.callCut());
        LiveSolid actual;
        try {
            actual = pastSolidOwner.commit(new PrefixSolidCall(cut, outcome));
            requireLive(player, c.gates, globals, actual);
            requireStorage(before, ready);
        } catch (RuntimeException | Error failure) {
            verifySuppressed(failure, () -> requireStorage(before, ready));
            throw failure;
        }
        c.state = c.pool.state(c.wholeIndex); c.player = actual.player(); c.globals = actual.globals();
        if (stopped != null) throw stopped.stop().originalException();
        c.effects.add(new Solid(completed));
    }

    /** Every genuine pre-zero/raw/flip store is forwarded synchronously; no final aggregate replay. */
    private void commitPastAngle(Cursor c, int value) {
        callPastReadOnly(() -> primaryAngleCommit.commit(c.globals.primaryAngle(), value));
        c.globals = new CdSolidObject.Globals(c.globals.invincible(), c.globals.scoreChain(),
                value, c.globals.secondaryAngle());
    }

    private void callPastReadOnly(Runnable call) {
        var before = runtime.capture();
        try {
            call.run(); requireStorage(before, before.pool());
        } catch (RuntimeException | Error failure) {
            verifySuppressed(failure, () -> requireStorage(before, before.pool()));
            throw failure;
        }
    }

    private void drawPast(Cursor c) {
        var before = runtime.capture();
        try {
            drawObject.drawObject(c.pool, c.state.identity());
            requireDrawStorage(before, c.wholeIndex);
        } catch (RuntimeException | Error failure) {
            verifySuppressed(failure, () -> requireDrawStorage(before, c.wholeIndex));
            throw failure;
        }
    }

    private void requireDrawStorage(CdObjectPlacementRuntime.Snapshot before, int wholeIndex) {
        var original = before.pool().slots().get(wholeIndex - CdObjectPool.FIRST_WHOLE_INDEX);
        var actual = runtime.pool().state(wholeIndex);
        var permitted = original.moveByte(SPRITE_FLAGS, (original.spriteFlags() & 0x7F) | (actual.spriteFlags() & 0x80));
        requireStorage(before, CdObjectPool.overlay(before.pool(), List.of(permitted)));
    }

    private void requireStorage(CdObjectPlacementRuntime.Snapshot before, CdObjectPool.Snapshot expectedPool) {
        var actual = runtime.capture();
        if (!before.status().equals(actual.status())
                || !Arrays.equals(before.savedFlags().copy(), actual.savedFlags().copy())) {
            throw new IllegalStateException("Past Monitor callback changed placement or saved storage");
        }
        runtime.pool().validateRestore(expectedPool);
        for (var expected : expectedPool.slots()) {
            if (!expected.equals(runtime.pool().state(expected.identity().wholeIndex()))) {
                throw new IllegalStateException("Past Monitor callback changed dynamic image " + expected.identity().wholeIndex());
            }
        }
    }

    private static void requireLive(CdSolidObject.Player player, CdSolidObject.Gates gates,
                                    CdSolidObject.Globals globals, LiveSolid actual) {
        Objects.requireNonNull(actual, "actual shared readback");
        if (!player.equals(actual.player()) || !gates.equals(actual.gates()) || !globals.equals(actual.globals())) {
            throw new IllegalStateException("Past Monitor shared readback differs from required values");
        }
    }

    private static void verifySuppressed(Throwable failure, Runnable verification) {
        try { verification.run(); }
        catch (RuntimeException | Error fault) { if (fault != failure) failure.addSuppressed(fault); }
    }

    /** Private mechanical view keeps public A records unchanged and sources conditional in Past. */
    private final class NativeGlobals {
        private final Globals legacy;
        private final PastGlobals past;
        NativeGlobals(Globals legacy) { this.legacy = legacy; this.past = null; }
        NativeGlobals(PastGlobals past) { this.legacy = null; this.past = past; }
        int rawTimeZone() { return legacy != null ? legacy.rawTimeZone() : read(past::rawTimeZone, false); }
        int rawTimeWarpDirection() { return legacy != null ? legacy.rawTimeWarpDirection() : read(past::rawTimeWarpDirection, false); }
        int cameraXWord() { return legacy != null ? legacy.cameraXWord() : read(past::cameraXWord, true); }
        int timeAttackMode() { return legacy != null ? legacy.timeAttackMode() : read(past::timeAttackMode, false); }
        int zone() { return legacy != null ? legacy.zone() : read(past::zone, false); }
        int layer() { return legacy != null ? legacy.layer() : read(past::layer, false); }
        int timeStopTimer() { return legacy != null ? legacy.timeStopTimer() : read(past::timeStopTimer, true); }
        private int read(IntSupplier source, boolean word) {
            var before = runtime.capture();
            try {
                int value = source.getAsInt();
                if (word) wordValue(value); else byteValue(value);
                requireStorage(before, before.pool());
                return value;
            } catch (RuntimeException | Error failure) {
                verifySuppressed(failure, () -> requireStorage(before, before.pool()));
                throw failure;
            }
        }
    }

    /** Exact raw fields, including dirty scratch on a non-player object. No invented player/zeroes. */
    static CdPlayerMovement.RawObjectMotion rawMotion(CdObjectState state) {
        return new CdPlayerMovement.RawObjectMotion(state.longBits(X_POSITION), state.longBits(Y_POSITION),
                state.signedWord(X_VELOCITY), state.signedWord(Y_VELOCITY), state.flags(),
                state.unsignedByte(RAW_CONTROL_2C), state.unsignedByte(STAND_LINK_3D));
    }
    private static UnsupportedMonitor unsupported(UnsupportedBoundary b, Cursor c, int value) {
        return new UnsupportedMonitor(b, c.wholeIndex, value);
    }
    private static final class Cursor {
        final CdObjectPool pool;
        final int wholeIndex;
        final CdSolidObject.Gates gates;
        final List<Effect> effects = new ArrayList<>();
        CdObjectState state;
        CdSolidObject.Player player;
        CdSolidObject.Globals globals;
        Cursor(CdObjectPool pool, CdObjectState state, SolidInputs inputs) {
            this.pool = pool; this.state = state; wholeIndex = state.identity().wholeIndex();
            player = inputs.player(); gates = inputs.gates(); globals = inputs.globals();
        }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        void publish() { pool.write(state); }
        Result finish() { return new Result(state, player, globals, effects); }
    }
    private static void byteValue(int value) {
        if ((value & ~0xFF) != 0) throw new IllegalArgumentException("Expected an unsigned native byte");
    }
    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
