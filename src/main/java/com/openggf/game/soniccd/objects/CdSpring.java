package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectTileIds;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMotionState;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/**
 * ObjSpring $0A, ordinary primary up/side/down/diagonal and ObjSpring2 player mirror. Native source:
 * Level/_Objects/Spring/Main.asm:98-481, scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
 * Shipped FixBugs=0 ordering; these routines have no FixBugs alternative. The diagonal init dimensions
 * follow the USA/non-demo retail branch (:209-214); diagonal art/residency remains an external owner.
 * MovingSpring $08 itself is outside this owner.
 *
 * <p>One run is one RunObjects dispatch. Routine0 runs even before any onscreen flag exists and returns
 * without Main. Nonzero routines with sprite bit7 clear skip the control table, including its lookup.
 * Normal control returns to DrawObject BEFORE following a parent and checking the anchor's $280 window.
 * DrawObject queues a physical slot reference: later SAT construction reads the final live slot, not
 * the earlier culling position. The routine2==5 mirror bypasses that entire ordinary wrapper.</p>
 *
 * <p>Each source Solid wrapper publishes working a0, captures ONE live pool cut, calls the shared pure
 * Solid owner and synchronously commits its result through Root's S4/S3 port. No Solid result or effect
 * is replayed. Spring's following action has a separate typed LaunchCommit after its object prefix is
 * live, then the dedicated FM_SPRING port. Down and diagonal fall through from the returning sound call into
 * AnimateObject in the same dispatch. Root owns phase/CAS validation and sound forwarding.</p>
 *
 * <p>No Player, Globals or Snapshot is retained between invocations. Source/port failures propagate
 * with already committed prefixes intact. There is no rollback, physics/probe/kill duplication,
 * route workaround, art fallback, snapshot hydration or World/Frame dependency.</p>
 */
public final class CdSpring {
    public static final int OBJECT_ID = 0x0A;

    public enum NativeRoutine {
        INIT, MAIN_UP, ANIM_UP, RESET_UP, MAIN_SIDE, ANIM_SIDE, RESET_SIDE,
        MAIN_DOWN, ANIM_DOWN, RESET_DOWN, MAIN_DIAG, ANIM_DIAG, RESET_DIAG, MIRROR_INIT, MIRROR_MAIN
    }
    public enum NativeCall { ANIMATE_OBJECT, DRAW_OBJECT }
    /** Semantic sound request; Root's sound owner supplies the release-specific native ID. */
    public enum FmSound { FM_SPRING }

    /** Native launch stores only; CENTRE_X/Y are INTEGER WORD stores, never their fraction words. */
    public enum LaunchPlayerField {
        CENTRE_X(0x08), CENTRE_Y(0x0C), X_VELOCITY(0x10), Y_VELOCITY(0x12), GROUND_VELOCITY(0x14),
        ANIMATION(0x1C), FLAGS(0x22), ANGLE(0x26), MOVE_LOCK(0x3E);
        private final int offset;
        LaunchPlayerField(int offset) { this.offset = offset; }
        public int byteOffset() { return offset; }
    }

    /** Completed observations only. All publication/sound work has already occurred; never replay these. */
    public sealed interface Effect permits RoutineEntered, Solid, Launched, PlayedFm, CallReturned,
            FollowedParent, Deleted { }
    public record RoutineEntered(NativeRoutine routine) implements Effect {
        public RoutineEntered { Objects.requireNonNull(routine); }
    }
    public record Solid(CdSolidObject.Result result) implements Effect {
        public Solid { Objects.requireNonNull(result); }
    }
    public record Launched(LaunchCall call) implements Effect {
        public Launched { Objects.requireNonNull(call); }
    }
    public record PlayedFm(FmSound sound) implements Effect {
        public PlayedFm { Objects.requireNonNull(sound); }
    }
    public record CallReturned(NativeCall call) implements Effect {
        public CallReturned { Objects.requireNonNull(call); }
    }
    public record FollowedParent(int parentAddress24) implements Effect { }
    /** Plain DeleteObject at the wrapper's BHI; no saved-placement flag is cleared. */
    public record Deleted() implements Effect { }

    /** Pre-scroll native cameraX word; no camera/picture/route policy belongs here. */
    public record Globals(int cameraXWord) {
        public Globals { wordValue(cameraXWord); }
    }
    /** Explicit post-player invocation cut. Each completed call's outputs replace these staged values. */
    public record SolidInputs(CdSolidObject.Player player, CdSolidObject.Gates gates, CdSolidObject.Globals globals) {
        public SolidInputs { Objects.requireNonNull(player); Objects.requireNonNull(gates); Objects.requireNonNull(globals); }
    }

    /**
     * Three authentic visual families retain the complete 200-byte include and real 14-byte script.
     * Roots0/6/80 and three-frame indices remain distinct. Speed4 and the genuine ObjectTileIDs72 Past
     * or ObjectTileIDs74 Present includes remain native source bytes, never padding or fitted selector results.
     * Declaration alone authenticates no media; the named aggregate factories own authenticated ingress.
     */
    public record Assets(CdObjectVisualAssets vertical, CdObjectVisualAssets horizontal,
                         CdObjectVisualAssets diagonal, int mappingsAddress, CdAssetBytes speedWords,
                         CdAssetBytes objectTileIds) {
        public Assets {
            Objects.requireNonNull(vertical); Objects.requireNonNull(horizontal); Objects.requireNonNull(diagonal);
            Objects.requireNonNull(speedWords); Objects.requireNonNull(objectTileIds);
            if (vertical.mappingTableOffset() != 0 || horizontal.mappingTableOffset() != 6
                    || diagonal.mappingTableOffset() != 80
                    || vertical.mappingFrames().size() != 3 || horizontal.mappingFrames().size() != 3
                    || diagonal.mappingFrames().size() != 3
                    || vertical.animations().size() != 1 || horizontal.animations().size() != 1
                    || diagonal.animations().size() != 1
                    || vertical.mappings().length() != 200 || horizontal.mappings().length() != 200
                    || diagonal.mappings().length() != 200
                    || vertical.animationTable().length() != 14 || horizontal.animationTable().length() != 14
                    || diagonal.animationTable().length() != 14
                    || !Arrays.equals(vertical.mappings().copy(), horizontal.mappings().copy())
                    || !Arrays.equals(vertical.mappings().copy(), diagonal.mappings().copy())
                    || !Arrays.equals(vertical.animationTable().copy(), horizontal.animationTable().copy())
                    || !Arrays.equals(vertical.animationTable().copy(), diagonal.animationTable().copy())
                    || speedWords.length() != 4 || (objectTileIds.length() != 72 && objectTileIds.length() != 74)) {
                throw new IllegalArgumentException("Spring needs three independent roots, full includes, speed4 and native ObjectTileIDs72/74");
            }
            if (mappingsAddress < 0 || mappingsAddress > 0xFFFFFF - 199) {
                throw new IllegalArgumentException("Spring mapping source exceeds its native 24-bit address extent");
            }
        }

        /** Authenticated USA owner ingress. No raw media, source file, script synthesis or decoded-art dependency. */
        public static Assets fromObjectAssets(CdR11aObjectAssets owner) {
            Objects.requireNonNull(owner);
            return new Assets(owner.springVertical(), owner.springHorizontal(), owner.springDiagonal(),
                    owner.view(CdR11aObjectAssets.Table.SPRING_MAPPINGS).range().nativeAddress(),
                    owner.view(CdR11aObjectAssets.Table.SPRING_SPEEDS).bytes(),
                    owner.view(CdR11aObjectAssets.Table.OBJECT_TILE_IDS).bytes());
        }

        /**
         * Actual authenticated USA Past aggregate ingress. All four views belong to the original R11B;
         * the genuine 72-byte tile include ends before Animal code. No Present facade or art decode is used.
         */
        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner);
            var identity = Objects.requireNonNull(owner.identity());
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R11B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R11B.stage())) {
                throw new IllegalArgumentException("Spring Past assets require authenticated original USA R11B Word RAM");
            }
            var mappings = owner.view(CdR11bAssets.Asset.MAP_SPRING);
            var scripts = owner.view(CdR11bAssets.Asset.ANI_SPRING).bytes();
            return new Assets(CdObjectVisualAssets.decodeIndexed(mappings.bytes().copy(), 0, 3, 12, 46,
                            scripts.copy(), 1, 36),
                    CdObjectVisualAssets.decodeIndexed(mappings.bytes().copy(), 6, 3, 46, 80,
                            scripts.copy(), 1, 36),
                    CdObjectVisualAssets.decodeIndexed(mappings.bytes().copy(), 80, 3, 86, 200,
                            scripts.copy(), 1, 46),
                    mappings.range().nativeAddress(), owner.view(CdR11bAssets.Asset.SPRING_SPEEDS).bytes(),
                    owner.view(CdR11bAssets.Asset.OBJECT_TILE_IDS).bytes());
        }

        /** Only primary diagonal selector $F/subtype2 zero is owned for the genuine Past72 format. */
        private int diagonalTileWord(int subtype2) {
            if (objectTileIds.length() == 74) {
                return CdR11aObjectAssets.decodeTileWord(objectTileIds.copy(), 0xF, subtype2);
            }
            if (subtype2 != 0) {
                throw new UnsupportedOperationException("Only primary diagonal Spring selector $F at subtype2 0 is reviewed");
            }
            int end = CdObjectTileIds.endOfBody(objectTileIds, 0xF);
            int target = (short) objectTileIds.unsignedWord(0xF * 2);
            if (end - target < 2) {
                throw new IllegalArgumentException("Primary diagonal Spring tile word crosses its selected body");
            }
            return objectTileIds.unsignedWord(target);
        }

        /** ANDI.W #2 / MOVE.W ObjSpring_Speeds(pc,d0.w), Spring Main:237-244. */
        public int speedWord(int subtypeByte) {
            if ((subtypeByte & ~0xFF) != 0) throw new IllegalArgumentException("Spring subtype exceeds a native byte");
            return (short) speedWords.unsignedWord(subtypeByte & 2);
        }
    }

    /** One real call cut and its pure result, not a capture retained by the body or a deferred command. */
    public record SolidCall(CdObjectPool.Snapshot callCut, CdSolidObject.Player expectedPlayer,
                            CdSolidObject.Globals expectedGlobals, CdSolidObject.Result result) {
        public SolidCall {
            Objects.requireNonNull(callCut); Objects.requireNonNull(expectedPlayer);
            Objects.requireNonNull(expectedGlobals); Objects.requireNonNull(result);
        }
    }
    /**
     * Root synchronously validates the expected player/globals and every affected live slot against callCut,
     * publishes result.object/otherSlots through S4, applies exactly result's player/global write sets through
     * S3, and forwards its sound calls once. Reject stale or closed phases. Exceptions close composition;
     * a callback must not dispatch another body or retain this call for later replay.
     */
    @FunctionalInterface public interface SolidCommit { void commit(SolidCall call); }

    /**
     * Native action patch after Solid: exact native stores INCLUDING same-value stores. The object prefix
     * is already live at identity. This is deliberately separate from Solid metadata; no invented Result.
     * Preserve both position fractions and every field outside the declared sets. Globals are unchanged
     * by all four actions, with an empty global write set. Sets are immutable detached values.
     */
    public record LaunchCall(CdObjectState.Identity identity, CdSolidObject.Player expectedPlayer,
                             CdSolidObject.Globals expectedGlobals, CdSolidObject.Player finalPlayer,
                             CdSolidObject.Globals finalGlobals, Set<LaunchPlayerField> playerWrites,
                             Set<CdSolidObject.GlobalField> globalWrites) {
        public LaunchCall {
            Objects.requireNonNull(identity); Objects.requireNonNull(expectedPlayer); Objects.requireNonNull(expectedGlobals);
            Objects.requireNonNull(finalPlayer); Objects.requireNonNull(finalGlobals);
            playerWrites = Set.copyOf(playerWrites); globalWrites = Set.copyOf(globalWrites);
        }
    }
    /**
     * Root's narrow guarded value projection: validate current S3 against both expected values, require
     * the open object phase/identity, then publish exactly the declared native stores once. MOVE_LOCK is
     * motion.moveLock at $3E (a WORD), not the player's unrelated invincibility timer at $32. The existing
     * shared Solid PlayerField lacks this store, hence this Spring-local typed set. No Frame helper required.
     */
    @FunctionalInterface public interface LaunchCommit { void commit(LaunchCall call); }
    /** DrawObject's synchronous cull/enqueue cut. Reload any flag writes, then follow the parent. */
    @FunctionalInterface public interface DrawObjectCall {
        void drawObject(CdObjectPool pool, CdObjectState.Identity identity);
    }
    /** Source PlayFMSound call after a successful launch commit; failure prevents subsequent native work. */
    @FunctionalInterface public interface PlayFmCall { void playFm(FmSound sound); }

    public record Result(CdObjectState state, CdSolidObject.Player finalPlayer, CdSolidObject.Globals finalGlobals,
                         List<Effect> effects) {
        public Result {
            Objects.requireNonNull(state); Objects.requireNonNull(finalPlayer); Objects.requireNonNull(finalGlobals);
            effects = List.copyOf(effects);
        }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }

    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, MIRROR_ROUTINE_DISPATCH,
        /** Init prefix is already live; reached selector $F/subtype2 is outside the owned tile-table contract. */
        DIAGONAL_TILE_SELECTOR,
        /** First actual parent oX WORD read addresses an unowned reserved/out-of-pool/unaligned image. */
        PARENT_READ
    }
    public static final class UnsupportedSpring extends UnsupportedOperationException {
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private final OptionalInt readAddress24;
        private UnsupportedSpring(UnsupportedBoundary boundary, int wholeIndex, int value, OptionalInt readAddress24) {
            super(boundary + " at Spring whole slot " + wholeIndex + ", reached value $" + Integer.toHexString(value)
                    + (readAddress24.isPresent() ? ", native read $" + Integer.toHexString(readAddress24.getAsInt()) : ""));
            this.boundary = boundary; this.wholeIndex = wholeIndex; this.value = value; this.readAddress24 = readAddress24;
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
        public OptionalInt readAddress24() { return readAddress24; }
    }

    private final CdObjectPlacementRuntime runtime;
    private final Assets assets;
    private final CdPlayerMath math;
    private final CdSolidObject solid;
    private final SolidCommit solidCommit;
    private final LaunchCommit launchCommit;
    private final DrawObjectCall drawObject;
    private final PlayFmCall playFm;

    public CdSpring(CdObjectPlacementRuntime runtime, Assets assets, CdPlayerMath math, CdSolidObject solid,
                    SolidCommit solidCommit, LaunchCommit launchCommit, DrawObjectCall drawObject, PlayFmCall playFm) {
        this.runtime = Objects.requireNonNull(runtime); this.assets = Objects.requireNonNull(assets);
        this.math = Objects.requireNonNull(math);
        this.solid = Objects.requireNonNull(solid); this.solidCommit = Objects.requireNonNull(solidCommit);
        this.launchCommit = Objects.requireNonNull(launchCommit); this.drawObject = Objects.requireNonNull(drawObject);
        this.playFm = Objects.requireNonNull(playFm);
    }

    /** ObjSpring (:130-169), including the reached control-table lookup and the special ObjSpring2 branch. */
    public Result run(int wholeIndex, Globals globals, SolidInputs inputs) {
        Objects.requireNonNull(globals); Objects.requireNonNull(inputs);
        var pool = runtime.pool(); var state = pool.state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Spring dispatch requires live ID $0A, slot " + wholeIndex);
        var c = new Cursor(pool, state, inputs);
        if (state.unsignedByte(ROUTINE_2) == 5) {
            mirror(c); // CMPI.B #5,oRoutine2 / BEQ ObjSpring2: no ordinary wrapper or offscreen gate.
        } else {
            if (state.routine() == 0 || (state.spriteFlags() & 0x80) != 0) control(c);
            draw(c);                                                     // BSR.W DrawObject BEFORE following a1.
            followParent(c);
            int d0 = c.state.word(SPRING_ANCHOR_X) & 0xFF80;
            int d1 = ((globals.cameraXWord() - 0x80) & 0xFFFF) & 0xFF80;
            if (((d0 - d1) & 0xFFFF) > 0x280) {                           // CMPI.W #$280 / BHI, equality keeps.
                c.pool.deleteObject(c.wholeIndex);
                c.state = c.pool.state(c.wholeIndex); c.effects.add(new Deleted());
            }
        }
        return new Result(c.state, c.player, c.globals, c.effects);
    }

    private void control(Cursor c) {
        switch (c.state.routine()) {
            case 0 -> init(c);
            case 2 -> mainUp(c);
            case 4 -> animate(c, NativeRoutine.ANIM_UP);
            case 6 -> reset(c, NativeRoutine.RESET_UP, true);
            case 8 -> mainSide(c);
            case 10 -> animate(c, NativeRoutine.ANIM_SIDE);
            case 12 -> reset(c, NativeRoutine.RESET_SIDE, false);
            case 14 -> mainDown(c);
            case 16 -> animate(c, NativeRoutine.ANIM_DOWN);
            case 18 -> reset(c, NativeRoutine.RESET_DOWN, false);
            case 20 -> mainDiag(c);
            case 22 -> animate(c, NativeRoutine.ANIM_DIAG);
            case 24 -> reset(c, NativeRoutine.RESET_DIAG, false);
            default -> throw c.unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c.state.routine());
        }
    }

    /** ObjSpring_Init (:186-239). RTS is before the speed table: no fall-through into Main_Up. */
    private void init(Cursor c) {
        c.enter(NativeRoutine.INIT);
        c.state = c.state.addByte(ROUTINE, 2).moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, 0x520)
                .moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveByte(WIDTH, 0x10).moveByte(Y_RADIUS, 8)
                .moveWord(SPRING_ANCHOR_X, c.state.centreX()).moveByte(PRIORITY, 4);
        int subtype = c.state.subtype();
        if ((subtype & 4) != 0) {                                        // bit2 has priority over bit3 and vertical flip.
            c.state = c.state.moveByte(ROUTINE, 8).moveByte(WIDTH, 8).moveByte(Y_RADIUS, 0x10)
                    .moveLong(MAPPINGS, assets.mappingsAddress() + 6);
        } else if ((subtype & 8) != 0) {
            c.state = c.state.moveByte(ROUTINE, 0x14).moveByte(WIDTH, 0x18).moveByte(Y_RADIUS, 0x0C)
                    .moveLong(MAPPINGS, assets.mappingsAddress() + 80);
            c.publish();                                                 // MOVEQ #$F / JSR SetObjectTileID is reached NOW.
            int tile;
            try {
                tile = assets.diagonalTileWord(c.state.unsignedByte(SUBTYPE2));
            } catch (UnsupportedOperationException unsupported) {
                throw c.unsupported(UnsupportedBoundary.DIAGONAL_TILE_SELECTOR, 0xF);
            }
            c.state = c.state.moveWord(TILE, tile);                       // Selector return precedes palette/speed.
        } else if ((c.state.spriteFlags() & 2) != 0) {
            c.state = c.state.moveByte(ROUTINE, 0x0E).moveByte(FLAGS, c.state.flags() | 2);
        }
        if ((subtype & 2) != 0) c.state = c.state.moveWord(TILE, c.state.word(TILE) | 0x2000); // BSET high byte bit5.
        c.state = c.state.moveWord(SPRING_SPEED, assets.speedWord(subtype));
        c.publish();
    }

    /** ObjSpring_SolidObject/2/3 (:248-252,296-300,362-368): d3/d4 loads are unused by shared Solid. */
    private CdSolidObject.Result solid(Cursor c, CdSolidObject.Entry entry) {
        c.publish();
        var cut = c.pool.capture();
        var result = solid.run(entry, c.state, c.player, c.gates, c.globals, cut);
        solidCommit.commit(new SolidCall(cut, c.player, c.globals, result));
        if (!c.pool.state(c.wholeIndex).equals(result.object())) {
            throw new IllegalStateException("Spring SolidCommit did not publish a0");
        }
        for (var other : result.otherSlots()) {
            if (!c.pool.state(other.identity().wholeIndex()).equals(other)) {
                throw new IllegalStateException("Spring SolidCommit did not publish a2 slot " + other.identity().wholeIndex());
            }
        }
        c.state = result.object(); c.player = result.player(); c.globals = result.globals();
        c.effects.add(new Solid(result));
        return result;
    }

    /** ObjSpring_Main_Up/.Action (:257-277). Native BNE consumes Solid's final MOVEQ condition/d0. */
    private void mainUp(Cursor c) {
        c.enter(NativeRoutine.MAIN_UP);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        if (solid(c, CdSolidObject.Entry.SOLID_OBJECT).d0().orElseThrow() == 0) return;
        c.state = c.state.moveByte(ROUTINE, 4);
        var p = new PlayerPatch(c.player);
        p.yWord(p.yWord() + 8);
        p.yVelocity(c.state.signedWord(SPRING_SPEED));
        p.flags(p.flags | 2); p.flags(p.flags & ~8);
        p.animation(0x10);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        launch(c, p);
        sound(c);                                                        // JMP PlayFMSound; return into the wrapper.
    }

    /** ObjSpring_Main_Side/.Action (:305-343). Its trigger is object flag5, even when d0==0. */
    private void mainSide(Cursor c) {
        c.enter(NativeRoutine.MAIN_SIDE);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        solid(c, CdSolidObject.Entry.SOLID_OBJECT);
        if ((c.state.flags() & 0x20) == 0) return;
        c.state = c.state.moveByte(ROUTINE, 0x0A);
        var p = new PlayerPatch(c.player);
        p.xVelocity(c.state.signedWord(SPRING_SPEED)); p.xWord(p.xWord() + 8); p.flags(p.flags | 1);
        if ((c.state.flags() & 1) == 0) {
            p.xWord(p.xWord() - 0x10); p.xVelocity(-p.xv); p.flags(p.flags & ~1);
        }
        p.moveLock(0x0F); p.groundVelocity(p.xv);
        if ((p.flags & 4) == 0) p.animation(0);
        p.angle(0);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~0x20);
        p.flags(p.flags & ~0x20);
        launch(c, p);
        sound(c);                                                        // JMP, no same-dispatch AnimateObject.
    }

    /** ObjSpring_Main_Down/.Action (:373-396), THEN falls through to ObjSpring_Anim_Down (:400-402). */
    private void mainDown(Cursor c) {
        c.enter(NativeRoutine.MAIN_DOWN);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        if (solid(c, CdSolidObject.Entry.BTM_SOLID_OBJECT).d0().orElseThrow() == 0) return;
        c.state = c.state.moveByte(ROUTINE, 0x10);
        var p = new PlayerPatch(c.player);
        p.yWord(p.yWord() - 8); p.yVelocity(c.state.signedWord(SPRING_SPEED)); p.yVelocity(-p.yv);
        p.flags(p.flags | 2); p.flags(p.flags & ~8);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        launch(c, p);
        sound(c);                                                        // JSR PlayFMSound, then native fall-through.
        animate(c, NativeRoutine.ANIM_DOWN);
    }

    /**
     * ObjSpring_Main_Diag/.Action (:416-465): ONE shared SOLID, then d0 OR returned object flag5.
     * CalcSine comes from the injected declared player assets (Functions (Misc).asm:69-76).
     * MULU.W zero-extends both words; LSR.L #8 precedes each low-word store. No normalized trig.
     */
    private void mainDiag(Cursor c) {
        c.enter(NativeRoutine.MAIN_DIAG);
        if ((c.state.spriteFlags() & 0x80) == 0) return;
        var result = solid(c, CdSolidObject.Entry.SOLID_OBJECT);
        if (result.d0().orElseThrow() == 0 && (c.state.flags() & 0x20) == 0) return;
        c.state = c.state.moveByte(ROUTINE, 0x16);
        var sine = math.calcSine(0xE0);
        int magnitude = (-c.state.word(SPRING_SPEED)) & 0xFFFF;             // NEG.W d2.
        var p = new PlayerPatch(c.player);
        p.yVelocity((int) (((long) (sine.sine() & 0xFFFF) * magnitude) >>> 8));
        p.xVelocity((int) (((long) (sine.cosine() & 0xFFFF) * magnitude) >>> 8));
        p.yWord(p.yWord() + 8);
        if ((c.state.spriteFlags() & 2) != 0) {
            p.yWord(p.yWord() - 0x10); p.yVelocity(-p.yv);
        }
        p.flags(p.flags & ~1); p.xWord(p.xWord() - 8);
        if ((c.state.flags() & 1) != 0) {
            p.xWord(p.xWord() + 0x10); p.flags(p.flags | 1); p.xVelocity(-p.xv);
        }
        p.flags(p.flags | 2); p.flags(p.flags & ~8); p.flags(p.flags & ~0x20);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        c.state = c.state.moveByte(FLAGS, c.state.flags() & ~0x20);
        launch(c, p);                                                     // Exactly X/Y integer words, XV/YV, FLAGS.
        sound(c);                                                        // JSR, so same-dispatch Ani_Spring follows.
        animate(c, NativeRoutine.ANIM_DIAG);
    }

    /** No snapshot recapture or fabricated Solid metadata: a distinct synchronous patch at its source cut. */
    private void launch(Cursor c, PlayerPatch p) {
        c.publish();
        var call = new LaunchCall(c.state.identity(), c.player, c.globals, p.player(), c.globals, p.writes, Set.of());
        launchCommit.commit(call);
        c.player = call.finalPlayer(); c.globals = call.finalGlobals();
        c.effects.add(new Launched(call));
    }

    private void sound(Cursor c) {
        playFm.playFm(FmSound.FM_SPRING);
        c.effects.add(new PlayedFm(FmSound.FM_SPRING));
    }

    /** All ordinary orientations use the SAME real Ani_Spring; shared AnimateObject owns FC and flip semantics. */
    private void animate(Cursor c, NativeRoutine routine) {
        c.enter(routine);
        var visual = switch (routine) {
            case ANIM_SIDE -> assets.horizontal();
            case ANIM_DIAG -> assets.diagonal();
            default -> assets.vertical();
        };
        var table = new CdObjectAnimation.AnimationTable(visual.animationTable(), 0, visual.animations().size());
        var s = c.state;
        var out = CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),
                s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2)), table);
        c.state = s.moveByte(ANIMATION, out.animationId()).moveByte(PREVIOUS_ANIMATION, out.previousId())
                .moveByte(ANIMATION_FRAME, out.scriptIndex()).moveByte(ANIMATION_TIME, out.timer())
                .moveByte(MAP_FRAME, out.mapFrame()).moveByte(SPRITE_FLAGS, out.spriteFlags())
                .moveByte(FLAGS, out.statusFlags()).moveByte(ROUTINE, out.routine()).moveByte(ROUTINE_2, out.routine2());
        c.publish();
        c.effects.add(new CallReturned(NativeCall.ANIMATE_OBJECT));
    }

    /** ObjSpring_Reset_Up/Side/Down/Diag (:287-292,353-357,406-409,477-481); only Up clears object flag3. */
    private static void reset(Cursor c, NativeRoutine routine, boolean clearStanding) {
        c.enter(routine);
        if (clearStanding) c.state = c.state.moveByte(FLAGS, c.state.flags() & ~8);
        c.state = c.state.moveByte(PREVIOUS_ANIMATION, 1).addByte(ROUTINE, -4).moveByte(MAP_FRAME, 0);
        c.publish();
    }

    /** ObjSpring2 (:98-125), reached only through the ordinary entry's routine2==5 branch. */
    private void mirror(Cursor c) {
        int routine = c.state.routine();
        if (routine != 0 && routine != 2) throw c.unsupported(UnsupportedBoundary.MIRROR_ROUTINE_DISPATCH, routine);
        if (routine == 0) {
            c.enter(NativeRoutine.MIRROR_INIT);
            c.state = c.state.moveLong(MAPPINGS, assets.mappingsAddress()).moveWord(TILE, 0x8520)
                    .moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).moveByte(WIDTH, 0x10)
                    .moveByte(Y_RADIUS, 8).moveByte(PRIORITY, 4).addByte(ROUTINE, 2);
        }
        c.enter(NativeRoutine.MIRROR_MAIN);
        c.state = c.state.moveWord(X, c.player.actor().motion().centreX16_16() >>> 16)
                .moveWord(Y, c.player.actor().motion().centreY16_16() >>> 16);
        draw(c);                                                         // JMP DrawObject, no ordinary wrapper tail.
    }

    private void draw(Cursor c) {
        c.publish();
        drawObject.drawObject(c.pool, c.state.identity());
        c.state = c.pool.state(c.wholeIndex);
        c.effects.add(new CallReturned(NativeCall.DRAW_OBJECT));
    }

    /** ObjSpring .DisplayOnly (:149-159). Native a1 = $FFFF0000 | parentWord, then signed byte offsets. */
    private static void followParent(Cursor c) {
        int parent = c.state.word(SPRING_PARENT);
        if (parent == 0) return;
        int address = 0xFF0000 | parent;
        int x, y;
        if (address == 0xFFD000) {
            x = c.player.actor().motion().centreX16_16() >>> 16;
            y = c.player.actor().motion().centreY16_16() >>> 16;
        } else if (address >= 0xFFD800 && address < 0xFFF000 && ((address - 0xFFD000) & 0x3F) == 0) {
            var live = c.pool.state((address - 0xFFD000) >>> 6);
            x = live.centreX(); y = live.centreY();                         // Real live image, even if its ID is zero.
        } else {
            throw new UnsupportedSpring(UnsupportedBoundary.PARENT_READ, c.wholeIndex, parent,
                    OptionalInt.of((address + 8) & 0xFFFFFF));             // First reached MOVE.W oX(a1), not a zero fill.
        }
        c.state = c.state.moveWord(X, x).moveWord(Y, y)
                .addWord(X, c.state.signedByte(SPRING_PARENT_X_OFFSET))
                .addWord(Y, c.state.signedByte(SPRING_PARENT_Y_OFFSET));
        c.publish();
        c.effects.add(new FollowedParent(address));
    }

    /** Invocation-local values only; not retained in this owner or reused after an exception. */
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
        void publish() { pool.write(state); }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
        UnsupportedSpring unsupported(UnsupportedBoundary boundary, int value) {
            return new UnsupportedSpring(boundary, wholeIndex, value, OptionalInt.empty());
        }
    }

    /**
     * Native action stores in source order, after the shared Solid output. Only nine potential launch fields
     * are mutable; reconstruct every other actor/slot field from the exact before value. WORD centre setters
     * retain all 16 fraction bits, speed/move-lock setters truncate/sign-extend as native words.
     */
    private static final class PlayerPatch {
        final CdSolidObject.Player before;
        final Set<LaunchPlayerField> writes = EnumSet.noneOf(LaunchPlayerField.class);
        int x, y, xv, yv, gv, flags, anim, angle, lock;
        PlayerPatch(CdSolidObject.Player before) {
            this.before = before; var m = before.actor().motion();
            x = m.centreX16_16(); y = m.centreY16_16(); xv = m.xVelocity(); yv = m.yVelocity();
            gv = m.groundVelocity(); flags = m.flags(); anim = m.animation(); angle = m.angle(); lock = m.moveLock();
        }
        int xWord() { return x >>> 16; }
        int yWord() { return y >>> 16; }
        void xWord(int word) { x = ((word & 0xFFFF) << 16) | (x & 0xFFFF); writes.add(LaunchPlayerField.CENTRE_X); }
        void yWord(int word) { y = ((word & 0xFFFF) << 16) | (y & 0xFFFF); writes.add(LaunchPlayerField.CENTRE_Y); }
        void xVelocity(int word) { xv = (short) word; writes.add(LaunchPlayerField.X_VELOCITY); }
        void yVelocity(int word) { yv = (short) word; writes.add(LaunchPlayerField.Y_VELOCITY); }
        void groundVelocity(int word) { gv = (short) word; writes.add(LaunchPlayerField.GROUND_VELOCITY); }
        void moveLock(int word) { lock = (short) word; writes.add(LaunchPlayerField.MOVE_LOCK); }
        void flags(int value) { flags = value & 0xFF; writes.add(LaunchPlayerField.FLAGS); }
        void animation(int value) { anim = value & 0xFF; writes.add(LaunchPlayerField.ANIMATION); }
        void angle(int value) { angle = value & 0xFF; writes.add(LaunchPlayerField.ANGLE); }
        CdSolidObject.Player player() {
            var a = before.actor(); var m = a.motion();
            var next = new CdPlayerMotionState(x, y, xv, yv, gv, m.routine(), angle, flags, m.spriteFlags(),
                    m.xRadius(), m.yRadius(), anim, m.previousAnimation(), m.playerJump(), m.charge(), lock,
                    m.stick(), m.playerControl(), m.previousPrimaryAngle(), m.previousSecondaryAngle());
            return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(next, a.tile(), a.priority(), a.hurt(),
                    a.rotationDistance()), before.slotId(), before.pushObject(), before.standObject());
        }
    }

    private static void wordValue(int value) {
        if ((value & ~0xFFFF) != 0) throw new IllegalArgumentException("Expected an unsigned native word");
    }
}
