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
 * Obj3DRamp ($2A), the 3D-ramp booster marker: Level/Palmtree Panic/Objects/3D Ramp/Main.asm:194-341 at
 * scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, executed over the live dynamic pool and saved bank of one
 * CdObjectPlacementRuntime. One {@link #run} is one RunObjects dispatch of the slot: the oSubtype2 owner test,
 * the routine selected by oRoutine (Init falls through into Main), DrawObject (R43LegacyDrawObject resolves to
 * DrawObject in this build) through the supplied port, then CheckObjDespawn2 against the original X anchor
 * (oVar2A).
 *
 * <p>Main reads the completed player slot: oX/oY/oYVel words, oFlags, oAnim and oPlayerCtrl bytes. Its one
 * player write, MOVE.W #+/-$C00,oYVel(a6) (:329-337), is returned as a staged {@link PlayerWrite} and is never
 * applied here; the caller's player owner applies the word after this body returns. The subtype flip is ported
 * directly. oSubtype2 nonzero selects Obj3DFall (:138-190), a different player-controlling body, and stops before
 * any write. No private queue, renderer, pool snapshot or fixture/frame/route key exists here.</p>
 */
public final class Cd3dRamp {
    public static final int OBJECT_ID = 0x2A;
    /** Obj3DRamp_Init: MOVE.W #$441,oTile(a0) - palette line 0, tile $441 (Art_3DRampBoost at VRAM $8820). */
    public static final int NATIVE_TILE_WORD = 0x441;
    /** .ChkTouch: MOVE.B #60,oVar2E(a0). */
    public static final int TOUCH_TIMER_FRAMES = 60;
    /** .TimerSet/.LaunchDown: MOVE.W #-$C00 / #$C00,oYVel(a6). */
    public static final int LAUNCH_SPEED = 0xC00;

    public enum NativeCall { INIT, ANIMATE_OBJECT, MAIN, DRAW_OBJECT, CHECK_OBJ_DESPAWN2 }
    /** Which source branch entered DespawnObject (Object Spawner.asm:403-438). */
    public enum DespawnEntry { ANCHOR_OFFSCREEN, TIME_WARP }
    /** The only player field this body writes natively. */
    public enum PlayerField { Y_VELOCITY }

    /** Ordered return metadata. Pool/bank effects describe completed writes and must not be replayed. */
    public sealed interface Effect permits CallReturned, PlayerWrite, Despawn { }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call, "call"); }
    }
    /**
     * STAGED native player write: MOVE.W #-$C00,oYVel(a6) when the player's oYVel word is negative, otherwise
     * MOVE.W #$C00,oYVel(a6). This body has not committed it; the caller's player owner must apply exactly this
     * signed word to the player slot after the body returns and before the next physical dispatch.
     */
    public record PlayerWrite(PlayerField field, int signedWord) implements Effect {
        public PlayerWrite {
            Objects.requireNonNull(field, "field");
            if (signedWord < -0x8000 || signedWord > 0x7FFF) {
                throw new IllegalArgumentException("Player write must be a signed native word");
            }
        }
    }
    /**
     * DespawnObject executed: for a nonzero saved ID the saved bank's loaded bit 7 was cleared at the selected
     * native period (-1 means no bank write), then the slot was fully deleted.
     */
    public record Despawn(DespawnEntry entry, int savedId, int period) implements Effect {
        public Despawn {
            Objects.requireNonNull(entry, "entry");
            byteValue(savedId, "saved ID");
            if (period < -1 || period > 2 || (savedId == 0) != (period == -1)) {
                throw new IllegalArgumentException("Despawn period must be -1 exactly when the saved ID is 0");
            }
        }
    }

    /**
     * The completed player slot (objPlayerSlot) after its own dispatch: integer oX/oY words, the oYVel word
     * (unsigned storage; its sign selects the launch direction), oFlags, oAnim and oPlayerCtrl (oVar2C) bytes.
     */
    public record Player(int xWord, int yWord, int yVelocityWord, int flags, int animation, int playerControl) {
        public Player {
            wordValue(xWord, "player oX");
            wordValue(yWord, "player oY");
            wordValue(yVelocityWord, "player oYVel");
            byteValue(flags, "player oFlags");
            byteValue(animation, "player oAnim");
            byteValue(playerControl, "player oPlayerCtrl");
        }
    }

    /** Explicit immutable globals at the call cut: timeZone/timeWarpDir bytes and the pre-scroll cameraX word. */
    public record Globals(int timeZone, int timeWarpDir, int cameraXWord) {
        public Globals {
            byteValue(timeZone, "timeZone");
            byteValue(timeWarpDir, "timeWarpDir");
            wordValue(cameraXWord, "cameraX");
            if ((timeZone & 0x7F) > 2) {
                throw new IllegalArgumentException("Native CD period must be 0, 1 or 2");
            }
        }
    }

    /**
     * Pure declared booster data: the ten-frame MapSpr_3DRamp table with its three Ani_3DRamp scripts, and the
     * native long Init stores in oMap. Production obtains it from the authenticated owner through
     * {@link #fromObjectAssets}; a constructed record never authenticates media and supplies no fallback bytes.
     */
    public record Assets(CdObjectVisualAssets booster, int mappingsAddress) {
        public Assets {
            Objects.requireNonNull(booster, "booster");
            if (booster.mappingFrames().size() != 10 || booster.animations().size() != 3) {
                throw new IllegalArgumentException("MapSpr_3DRamp declares ten frames and Ani_3DRamp three scripts");
            }
        }

        /** Original Word RAM address of the authenticated mapping view; this is the stored native long. */
        public static Assets fromObjectAssets(CdR11aObjectAssets assets) {
            Objects.requireNonNull(assets, "assets");
            return new Assets(assets.rampBooster(),
                    assets.view(CdR11aObjectAssets.Table.RAMP_BOOSTER_MAPPINGS).range().nativeAddress());
        }
    }

    /**
     * Source DrawObject (Object Functions.asm:137-196) at the Obj3DRamp cut (3D Ramp/Main.asm:202): after the
     * routine's pool writes and before CheckObjDespawn2. The shared CPU queue implements it over the live pool:
     * clear bit 7 of the live slot first, then cull/capacity, then append only the physical slot reference. Any
     * bit-7 write it makes is reloaded afterwards; this body never writes back its pre-call image. The caller must
     * keep this synchronous port non-reentrant: it must not dispatch another body.
     */
    @FunctionalInterface
    public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity slot);
    }

    /** Final live image of the slot after the whole dispatch (fully zero after a despawn). */
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state, "state");
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Despawn.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        /** TST.B oSubtype2 / BNE.W Obj3DFall (3D Ramp/Main.asm:198-199): the unowned falling-leaf body. */
        ALTERNATE_OWNER_3D_FALL,
        /** oRoutine outside {0,2} indexes past the two-entry Obj3DRamp_Index (3D Ramp/Main.asm:208-209). */
        ROUTINE_DISPATCH
    }

    /** Thrown before the boundary's writes; the pool and bank hold the last committed source state. */
    public static final class Unsupported3dRamp extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex;

        private Unsupported3dRamp(UnsupportedBoundary boundary, int wholeIndex, String detail) {
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

    /** The runtime supplies the single live pool and saved bank. */
    public Cd3dRamp(CdObjectPlacementRuntime runtime, Assets assets, DrawObjectCall drawObject) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.assets = Objects.requireNonNull(assets, "assets");
        this.drawObject = Objects.requireNonNull(drawObject, "drawObject");
    }

    public Assets assets() { return assets; }

    /** One Obj3DRamp dispatch for the whole-pool dynamic slot currently holding ID $2A. */
    public Result run(int wholeIndex, Player player, Globals globals) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(globals, "globals");
        var pool = runtime.pool();
        var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) {
            throw new IllegalArgumentException("RunObjects dispatch error: slot " + wholeIndex
                    + " holds ID " + state.id() + ", not Obj3DRamp $2A");
        }
        if (state.subtype2() != 0) {                                           // TST.B oSubtype2(a0) / BNE.W Obj3DFall
            throw new Unsupported3dRamp(UnsupportedBoundary.ALTERNATE_OWNER_3D_FALL, wholeIndex,
                    "oSubtype2 $" + Integer.toHexString(state.subtype2()) + " selects Obj3DFall");
        }
        var effects = new ArrayList<Effect>();
        switch (state.routine()) {                                             // MOVE.W Obj3DRamp_Index(pc,d0.w),d0 / JSR
            case 0 -> {
                state = init(state);
                effects.add(new CallReturned(NativeCall.INIT));
                state = main(state, player, effects);                          // BEQ.S Obj3DRamp_Main or fall-through
                pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            case 2 -> {
                state = main(state, player, effects);
                pool.write(state);
                effects.add(new CallReturned(NativeCall.MAIN));
            }
            default -> throw new Unsupported3dRamp(UnsupportedBoundary.ROUTINE_DISPATCH, wholeIndex,
                    "oRoutine " + state.routine() + " indexes past Obj3DRamp_Index");
        }
        drawObject.drawObject(pool, state.identity());                         // JSR R43LegacyDrawObject (= DrawObject)
        effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
        // MOVE.W oVar2A(a0),d0 / BRA.W CheckObjDespawn2 read the live slot after DrawObject, including a zeroed one.
        var reloaded = pool.state(wholeIndex);
        checkObjDespawn2(reloaded, reloaded.word(RAMP_ANCHOR_X), globals, effects);
        effects.add(new CallReturned(NativeCall.CHECK_OBJ_DESPAWN2));
        return new Result(pool.state(wholeIndex), effects);
    }

    /** Obj3DRamp_Init (3D Ramp/Main.asm:212-225); the sprite-flag byte is replaced, not OR-ed. */
    private CdObjectState init(CdObjectState state) {
        var s = state.addByte(ROUTINE, 2)                                      // ADDQ.B #2,oRoutine(a0)
                .moveByte(SPRITE_FLAGS, 4)                                     // MOVE.B #4,oSprFlags(a0)
                .moveByte(PRIORITY, 1)                                         // MOVE.B #1,oPriority(a0)
                .moveLong(MAPPINGS, assets.mappingsAddress())                  // MOVE.L #MapSpr_3DRamp,oMap(a0)
                .moveWord(TILE, NATIVE_TILE_WORD)                              // MOVE.W #$441,oTile(a0)
                .moveByte(WIDTH, 0x20)                                         // MOVE.B #$20,oWidth(a0)
                .moveByte(Y_RADIUS, 0x20)                                      // MOVE.B #$20,oYRadius(a0)
                .moveWord(RAMP_ANCHOR_X, state.centreX());                     // MOVE.W oX(a0),oVar2A(a0)
        if (s.subtype() != 0) {                                                // TST.B oSubtype(a0) / BEQ.S Obj3DRamp_Main
            s = s.moveByte(SPRITE_FLAGS, s.spriteFlags() | 1)                  // BSET #0,oSprFlags(a0)
                    .moveByte(FLAGS, s.flags() | 1);                           // BSET #0,oFlags(a0)
        }
        return s;
    }

    /** Obj3DRamp_Main (3D Ramp/Main.asm:229-341). */
    private CdObjectState main(CdObjectState state, Player player, List<Effect> effects) {
        int timer = state.unsignedByte(RAMP_TIMER);
        boolean playerOnRamp = (player.playerControl() & 2) != 0;              // BTST #1,oPlayerCtrl(a6)
        boolean chunkPosition;
        int d1 = 0;                                                            // MOVEQ #0,d1 (.TimeRunSet)
        if (timer != 0) {                                                      // TST.B oVar2E(a0) / BEQ.S .TimeRunSet
            state = state.moveByte(ANIMATION, playerOnRamp ? 1 : 2);           // MOVE.B #1,oAnim / BNE .Animate / ADDQ.B #1,oAnim
            state = animate(state);                                            // .Animate: LEA Ani_3DRamp,a1 / JSR AnimateObject
            effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
            chunkPosition = true;                                              // BRA.S .GetChunkPos
        } else {
            state = state.moveByte(MAP_FRAME, 0);                              // .TimeRunSet: MOVE.B #0,oMapFrame(a0)
            chunkPosition = playerOnRamp;                                      // BTST #1,oPlayerCtrl(a6) / BEQ.S .Move3D
        }
        if (chunkPosition) {
            int d0 = player.xWord() & 0xFF;                                    // .GetChunkPos: MOVE.W oX(a6),d0 / ANDI.W #$FF,d0
            if (state.subtype() != 0) { d0 = 0xFF - d0; }                      // TST.B oSubtype / BEQ .NoFlip / MOVE.W #$FF,d0 / SUB.W d1,d0
            if (d0 >= 0xC0) { d0 = d0 >= 0xF0 ? 0 : 0xBF; }                    // .NoFlip: CMPI #$C0 / BCS; CMPI #$F0 / BCC .Cap; #$BF
            d1 = d0;                                                           // .GotChunkPos: EXT.L d0 / MOVE.W d0,d1
            if (timer == 0) {                                                  // TST.B oVar2E(a0) / BNE.S .KeepFrame
                state = state.moveByte(MAP_FRAME, d0 / 0x30);                  // DIVU.W #$30,d0 / MOVE.B d0,oMapFrame(a0)
            }
            d1 = (d1 >>> 2) + (d1 >>> 3);                                      // .KeepFrame: LSR.W #2,d1 / MOVE.W d1,d2 / LSR.W #1,d2 / ADD.W d2,d1
            if (state.subtype() != 0) { d1 = (-d1) & 0xFFFF; }                 // TST.B oSubtype / BEQ .Move3D / NEG.W d1
        }
        state = state.moveWord(X, state.word(RAMP_ANCHOR_X) + d1);             // .Move3D: ADD.W oVar2A(a0),d1 / MOVE.W d1,oX(a0)
        if (timer != 0) {                                                      // TST.B oVar2E(a0) / BEQ.S .SkipTimer
            timer = (timer - 1) & 0xFF;
            state = state.moveByte(RAMP_TIMER, timer);                         // SUBQ.B #1,oVar2E(a0) / BRA.S .ChkTouch
        } else if ((player.flags() & 2) != 0) {                                // .SkipTimer: BTST #1,oFlags(a6) / BNE.S .End
            return state;
        }
        // .ChkTouch: word arithmetic with BMI (signed) then BCC (unsigned) on each axis.
        int width = state.signedByte(WIDTH);                                   // MOVE.B oWidth(a0),d1 / EXT.W d1
        int dx = (player.xWord() - state.centreX() + width) & 0xFFFF;          // MOVE.W oX(a6),d0 / SUB.W oX(a0),d0 / ADD.W d1,d0
        if ((short) dx < 0) { return state; }                                  // BMI.S .End
        if (dx >= ((width + width) & 0xFFFF)) { return state; }                // ADD.W d1,d1 / CMP.W d1,d0 / BCC.S .End
        int radius = state.signedByte(Y_RADIUS);                               // MOVE.B oYRadius(a0),d1 / EXT.W d1
        int dy = (player.yWord() - state.centreY() + radius) & 0xFFFF;         // MOVE.W oY(a6),d0 / SUB.W oY(a0),d0 / ADD.W d1,d0
        if ((short) dy < 0) { return state; }                                  // BMI.S .End
        if (dy >= ((radius + radius) & 0xFFFF)) { return state; }              // ADD.W d1,d1 / CMP.W d1,d0 / BCC.S .End
        if (player.animation() == 0x2B) { return state; }                      // CMPI.B #$2B,oAnim(a6) / BEQ.S .End
        if (timer == 0) {                                                      // TST.B oVar2E(a0) / BNE.S .TimerSet
            state = state.moveByte(RAMP_TIMER, TOUCH_TIMER_FRAMES);            // MOVE.B #60,oVar2E(a0)
        }
        // .TimerSet: TST.W oYVel(a6) / BPL.S .LaunchDown; the write is staged for the caller's player owner.
        boolean movingUp = (short) player.yVelocityWord() < 0;
        effects.add(new PlayerWrite(PlayerField.Y_VELOCITY, movingUp ? -LAUNCH_SPEED : LAUNCH_SPEED));
        return state;
    }

    /** .Animate: AnimateObject (Object Animate.asm:14-95) over the bound Ani_3DRamp table. */
    private CdObjectState animate(CdObjectState state) {
        var visual = assets.booster();
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

    /** CheckObjDespawn2 / DespawnObjTimeWarp (Object Spawner.asm:388-399,442-446) with d0 = oVar2A. */
    private void checkObjDespawn2(CdObjectState state, int anchorX, Globals globals, List<Effect> effects) {
        if ((state.spriteFlags() & 0x80) == 0) {                               // TST.B oSprFlags / BMI DespawnObjTimeWarp
            int d0 = anchorX & 0xFF80;
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;       // SUBI.W #$80 / ANDI.W #$FF80
            d0 = (d0 - d1) & 0xFFFF;
            if (d0 > 0x80 + (320 + 0x40) + 0x80) {                             // CMPI.W #$280 / BLS: unsigned, equality keeps
                despawnObject(state, DespawnEntry.ANCHOR_OFFSCREEN, globals, effects);
                return;
            }
        }
        if ((globals.timeZone() & 0x80) != 0) {                                // DespawnObjTimeWarp: BTST #7,timeZone
            despawnObject(state, DespawnEntry.TIME_WARP, globals, effects);
        }
    }

    /** DespawnObject (Object Spawner.asm:403-438): bank loaded bit, then the full 64-byte delete. */
    private void despawnObject(CdObjectState state, DespawnEntry entry, Globals globals, List<Effect> effects) {
        int savedId = state.savedFlagsId();                                    // MOVE.B oSavedFlagsID / BEQ .Delete
        int period = -1;
        if (savedId != 0) {
            period = despawnPeriod(globals);
            runtime.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED); // BCLR #7,2(a1,d0.w)
        }
        runtime.pool().deleteObject(state.identity().wholeIndex());            // JSR DeleteObject
        effects.add(new Despawn(entry, savedId, period));
    }

    /** Object Spawner.asm:413-431: the old period during a warp, clamped to Past..Future. */
    private static int despawnPeriod(Globals globals) {
        int d1 = globals.timeZone() & 0x7F;                                    // MOVE.B timeZone,d1 / BCLR #7,d1
        if ((globals.timeZone() & 0x80) == 0) { return d1; }                   // BEQ .MarkUnloaded
        d1 = (short) ((d1 - (byte) globals.timeWarpDir()) & 0xFFFF);           // EXT.W / NEG.W d2 / ADD.W d2,d1
        if (d1 < 0) { return 0; }                                              // BPL .CheckFuture; MOVEQ #TIME_PAST
        return d1 < 3 ? d1 : 2;                                                // CMPI.W #TIME_FUTURE+1 / BCS; MOVEQ #TIME_FUTURE
    }

    private static void byteValue(int value, String name) {
        if ((value & ~0xFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native byte"); }
    }

    private static void wordValue(int value, String name) {
        if ((value & ~0xFFFF) != 0) { throw new IllegalArgumentException(name + " must be an unsigned native word"); }
    }
}
