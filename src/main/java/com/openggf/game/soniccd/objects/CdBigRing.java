package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStagePayload;
import com.openggf.game.soniccd.media.CdMmdImage;
import com.openggf.game.soniccd.level.CdR12bObjectAssets;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.level.CdR12bTerrainAssets;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11bAssets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntSupplier;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;

/**
 * One selected original-USA ObjBigRing $14, including its same-ID nonzero-subtype flash.
 * Native pin1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff: Level/_Objects/Level End/Main.asm:385-562;
 * Object Animate.asm:14-95; Object Spawner.asm:329-340,385-446. No FixBugs alternative occurs here.
 * USA's below-fifty branch uses CheckObjDespawn, not the non-USA demo deletion alternative.
 *
 * <p>Every pool write is a real source prefix. Fresh full-placement/source cuts are descriptive
 * values, never private World/Frame/program invocation authority. The caller must prebuild and
 * validate its real Actor, Controls, programme, scroll and sound transactions before each reached
 * synchronous port, adopt only that declared operation once, and retain its actual capture lifetime.
 * Ports preserve all placement state, except DrawObject's sole live source bit7 clear. Effects are
 * completed observations, never deferred operations. Original failures survive final verification.
 * No actor reconstruction, global bank, clock, scanner, art decode/service, World or programme runs.
 */
public final class CdBigRing {
    public static final int OBJECT_ID = 0x14;
    public static final int FM_BIG_RING = 0xAF;

    /** Raw format inputs; a constructed value authenticates no media or current art. */
    public record Assets(CdObjectVisualAssets ring, int ringMappingsAddress, CdAssetBytes ringAnimations,
                         CdObjectVisualAssets flash, int flashMappingsAddress, CdAssetBytes flashAnimations) {
        public Assets {
            Objects.requireNonNull(ring); Objects.requireNonNull(ringAnimations);
            Objects.requireNonNull(flash); Objects.requireNonNull(flashAnimations);
            staticVisual(ring, 4, 106); staticVisual(flash, 8, 77);
        }
        /** Actual retained original-USA R12B data only; no body/Frame/World or current-art authority. */
        public static Assets fromAct2PastAssets(CdR12bObjectAssets owner) {
            Objects.requireNonNull(owner, "actual R12B metadata"); var identity = owner.identity();
            if (identity.variant() != CdReleaseVariant.USA || identity.file() != CdNativeFile.R12B
                    || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM
                    || !identity.stage().equals(CdStagePayload.R12B.stage()))
                throw new IllegalArgumentException("Actual original USA R12B metadata required");
            return new Assets(owner.bigRing(), CdR12bObjectAssets.View.MAP_SPR_BIG_RING.range().nativeAddress(),
                    owner.view(CdR12bObjectAssets.View.ANI_BIG_RING), owner.bigRingFlash(),
                    CdR12bObjectAssets.View.MAP_SPR_BIG_RING_FLASH.range().nativeAddress(),
                    owner.view(CdR12bObjectAssets.View.ANI_BIG_RING_FLASH));
        }

        public static Assets fromPastAssets(CdR11bAssets owner) {
            Objects.requireNonNull(owner, "actual original Past assets");
            var ring = owner.view(CdR11bAssets.Asset.MAP_SPR_BIG_RING);
            var flash = owner.view(CdR11bAssets.Asset.MAP_SPR_BIG_RING_FLASH);
            return new Assets(CdObjectVisualAssets.decodeStatic(ring.bytes().copy(), 4, 106), ring.range().nativeAddress(),
                    owner.view(CdR11bAssets.Asset.ANI_BIG_RING).bytes(),
                    CdObjectVisualAssets.decodeStatic(flash.bytes().copy(), 8, 77), flash.range().nativeAddress(),
                    owner.view(CdR11bAssets.Asset.ANI_BIG_RING_FLASH).bytes());
        }
        private static void staticVisual(CdObjectVisualAssets visual, int frames, int tiles) {
            if (visual.mappingFrames().size() != frames || !visual.animations().isEmpty())
                throw new IllegalArgumentException("Big Ring uses its complete static mapping directory");
            for (var frame : visual.mappingFrames()) for (var piece : frame.pieces())
                if (piece.tileOffset() > tiles - piece.tileCount())
                    throw new IllegalArgumentException("Big Ring mapping exceeds its existing native art source");
        }
    }
    public enum ReadField {
        RINGS_WORD(16), TIME_STONES_BYTE(8), TIME_ATTACK_BYTE(8), PLAYER_X_RADIUS_BYTE(8), PLAYER_X_WORD(16),
        PLAYER_Y_RADIUS_BYTE(8), PLAYER_Y_WORD(16), CAMERA_X_WORD(16), TIME_ZONE_BYTE(8), TIME_WARP_DIRECTION_BYTE(8);
        private final int bits;
        ReadField(int bits) { this.bits = bits; }
        public int widthBits() { return bits; }
    }
    public record ReadCall(ReadField field, CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public ReadCall { Objects.requireNonNull(field); requireSource(actualCut, expectedSource); }
    }
    public record SpecialStageCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public SpecialStageCall { requireSource(actualCut, expectedSource); }
        public int value() { return 1; }
    }
    /** Closed native player operations, not a general field/address store API. */
    public sealed interface PlayerStore permits PlayerXWord, ControlLockBit0, ControlHold0808,
            XVelocityZero, GroundVelocityZero {
        CdObjectPlacementRuntime.Snapshot actualCut();
        CdObjectState expectedSource();
    }
    public record PlayerXWord(int value, CdObjectPlacementRuntime.Snapshot actualCut,
                              CdObjectState expectedSource) implements PlayerStore {
        public PlayerXWord { width(value, 16); requireSource(actualCut, expectedSource); }
    }
    /** BSET0 preserves every other current ctrlLocked bit; it does not store the entire byte. */
    public record ControlLockBit0(CdObjectPlacementRuntime.Snapshot actualCut,
                                   CdObjectState expectedSource) implements PlayerStore {
        public ControlLockBit0 { requireSource(actualCut, expectedSource); }
    }
    /** WORD$0808 changes logical held/tap only; raw controller latches stay with their owner. */
    public record ControlHold0808(CdObjectPlacementRuntime.Snapshot actualCut,
                                  CdObjectState expectedSource) implements PlayerStore {
        public ControlHold0808 { requireSource(actualCut, expectedSource); }
    }
    public record XVelocityZero(CdObjectPlacementRuntime.Snapshot actualCut,
                                CdObjectState expectedSource) implements PlayerStore {
        public XVelocityZero { requireSource(actualCut, expectedSource); }
    }
    public record GroundVelocityZero(CdObjectPlacementRuntime.Snapshot actualCut,
                                     CdObjectState expectedSource) implements PlayerStore {
        public GroundVelocityZero { requireSource(actualCut, expectedSource); }
    }
    /** Native BYTE1 at scrollLock's HIGH byte; preserve the current low byte of the owned WORD. */
    public record ScrollLockHighByteOne(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public ScrollLockHighByteOne { requireSource(actualCut, expectedSource); }
    }
    public record SoundCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public SoundCall { requireSource(actualCut, expectedSource); }
        public int nativeId() { return FM_BIG_RING; }
    }
    public record DrawCall(CdObjectPlacementRuntime.Snapshot actualCut, CdObjectState expectedSource) {
        public DrawCall { requireSource(actualCut, expectedSource); }
    }
    @FunctionalInterface public interface NativeRead { int read(ReadCall call); }
    @FunctionalInterface public interface SpecialStageStore { void store(SpecialStageCall call); }
    @FunctionalInterface public interface PlayerCommit { void commit(PlayerStore call); }
    @FunctionalInterface public interface ScrollLockStore { void store(ScrollLockHighByteOne call); }
    @FunctionalInterface public interface SoundPort { void request(SoundCall call); }
    @FunctionalInterface public interface DrawObjectCall { void drawObject(DrawCall call); }

    public enum NativeRoutine { RING_INIT, RING_MAIN, RING_ANIMATE, FLASH_INIT, FLASH_ANIMATE, FLASH_DELETE }
    public enum DeleteReason { ALL_TIME_STONES, TIME_ATTACK, FLASH_FINISHED, DESPAWN }
    public sealed interface Effect permits RoutineEntered, ReadReturned, SpecialStageStored, PlayerCommitted,
            ScrollLockStored, SoundRequested, SlotSearchReturned, ChildCreated, AllocationRecheck,
            AnimationReturned, DrawReturned, Deleted, SavedFlagChanged, DespawnChecked { }
    public record RoutineEntered(NativeRoutine routine) implements Effect { }
    public record ReadReturned(ReadField field, int value) implements Effect { }
    public record SpecialStageStored() implements Effect { }
    public record PlayerCommitted(Class<? extends PlayerStore> operation) implements Effect { }
    public record ScrollLockStored() implements Effect { }
    public record SoundRequested() implements Effect { }
    public record SlotSearchReturned(OptionalInt wholeIndex) implements Effect { }
    public record ChildCreated(CdObjectState state) implements Effect { }
    public record AllocationRecheck() implements Effect { }
    public record AnimationReturned(boolean flash) implements Effect { }
    public record DrawReturned() implements Effect { }
    public record Deleted(DeleteReason reason) implements Effect { }
    public record SavedFlagChanged(int byteIndex, int before, int after) implements Effect { }
    public record DespawnChecked(boolean deleted) implements Effect { }
    public record Result(CdObjectState state, List<Effect> effects) {
        public Result { Objects.requireNonNull(state); effects = List.copyOf(effects); }
        public boolean deleted() { return effects.stream().anyMatch(Deleted.class::isInstance); }
    }
    public enum UnsupportedBoundary {
        ROUTINE_DISPATCH, READ_OWNER, READ_VALUE_WIDTH, SPECIAL_STAGE_OWNER, PLAYER_OWNER,
        SCROLL_LOCK_OWNER, SOUND_OWNER, SAVED_FLAGS_ADDRESS, ALLOCATION_MAIN_CYCLE,
        ANIMATION_DIRECTORY_READ, ANIMATION_SPEED_READ, ANIMATION_FRAME_READ, ANIMATION_OPERAND_READ
    }
    public static final class UnsupportedBigRing extends UnsupportedOperationException {
        private static final long serialVersionUID = 1L;
        private final UnsupportedBoundary boundary;
        private final int wholeIndex, value;
        private final List<Effect> completedEffects;
        private UnsupportedBigRing(UnsupportedBoundary boundary, Cursor c, int value) {
            super(boundary + " at Big Ring slot " + c.whole + ", reached value " + value);
            this.boundary = boundary; this.wholeIndex = c.whole; this.value = value;
            completedEffects = List.copyOf(c.effects);
        }
        public UnsupportedBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
        public int value() { return value; }
        public List<Effect> completedEffects() { return completedEffects; }
    }

    private final CdObjectPlacementRuntime placement;
    private final Assets assets;
    private final NativeRead nativeRead;
    private final SpecialStageStore specialStage;
    private final PlayerCommit playerCommit;
    private final ScrollLockStore scrollLock;
    private final SoundPort sound;
    private final DrawObjectCall drawObject;
    /** Exact current original R11 source ingress. Public format records grant no invocation. */
    public static CdBigRing fromOriginalPresentAssets(CdObjectPlacementRuntime placement,CdR11aAssets assets,
            NativeRead read, SpecialStageStore special, PlayerCommit player, ScrollLockStore scroll, SoundPort sound, DrawObjectCall draw,CdStageClosureInvocation.Observer observer)throws java.io.IOException{
        Objects.requireNonNull(placement);Objects.requireNonNull(assets);
        if(!(placement.placements()==assets.placements()&&placement.sourceIdentity().equals(java.util.Optional.of(assets.identity()))))throw new IllegalArgumentException("Original closure placement/source mismatch");
        var o=assets.openingObjects();var result=new CdBigRing(placement,new Assets(CdObjectVisualAssets.decodeStatic(o.view(CdR11aObjectAssets.Table.CLOSURE_BIG_RING_MAPPINGS).bytes().copy(),4,106),o.view(CdR11aObjectAssets.Table.CLOSURE_BIG_RING_MAPPINGS).range().nativeAddress(),o.view(CdR11aObjectAssets.Table.CLOSURE_BIG_RING_ANIMATIONS).bytes(),CdObjectVisualAssets.decodeStatic(o.view(CdR11aObjectAssets.Table.CLOSURE_BIG_RING_FLASH_MAPPINGS).bytes().copy(),8,77),o.view(CdR11aObjectAssets.Table.CLOSURE_BIG_RING_FLASH_MAPPINGS).range().nativeAddress(),o.view(CdR11aObjectAssets.Table.CLOSURE_BIG_RING_FLASH_ANIMATIONS).bytes()),read,special,player,scroll,sound,draw);
        result.closureIssuer=new CdStageClosureInvocation.Issuer(observer);return result;
    }
    private CdStageClosureInvocation.Issuer closureIssuer;
    public boolean isCurrentOriginalCall(CdStageClosureInvocation.Call call){return running&&closureIssuer!=null&&closureIssuer.current(call);}
    private <T>T closureRead(Object call,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,java.util.function.Supplier<T> primitive){return closureIssuer==null?primitive.get():closureIssuer.invoke(call,cut,source,primitive);}
    private void closureInvoke(Object call,CdObjectPlacementRuntime.Snapshot cut,CdObjectState source,Runnable primitive){closureRead(call,cut,source,()->{primitive.run();return null;});}
    private boolean running;

    /** Source-pair ingress only; caller retains every actual helper/publication authority. */
    public static CdBigRing fromAct2PastAssets(CdObjectPlacementRuntime placement, CdR12bTerrainAssets owner,
                                              NativeRead read, SpecialStageStore special, PlayerCommit player,
                                              ScrollLockStore scroll, SoundPort sound, DrawObjectCall draw) {
        Objects.requireNonNull(placement); Objects.requireNonNull(owner);
        if (!placement.sameAct2PastSource(owner)) throw new IllegalArgumentException("BigRing placement/assets source differs");
        return new CdBigRing(placement, Assets.fromAct2PastAssets(owner.objects()), read, special, player, scroll, sound, draw);
    }

    public CdBigRing(CdObjectPlacementRuntime placement, Assets assets, NativeRead nativeRead,
                     SpecialStageStore specialStage, PlayerCommit playerCommit, ScrollLockStore scrollLock,
                     SoundPort sound, DrawObjectCall drawObject) {
        this.placement = Objects.requireNonNull(placement); this.assets = Objects.requireNonNull(assets);
        this.nativeRead = nativeRead; this.specialStage = specialStage; this.playerCommit = playerCommit;
        this.scrollLock = scrollLock; this.sound = sound; this.drawObject = Objects.requireNonNull(drawObject);
    }
    public Assets assets() { return assets; }

    public Result run(int wholeIndex) {
        if (running) throw new IllegalStateException("Reentrant ObjBigRing dispatch");
        var state = placement.pool().state(wholeIndex);
        if (state.id() != OBJECT_ID) throw new IllegalArgumentException("Selected slot is not Big Ring $14");
        running = true;
        try {
            var c = new Cursor(state);
            if (state.subtype() != 0) {
                dispatch(c, true);
                draw(c); // Flash always draws, including the live cleared DeleteObject result.
            } else if (read(c, ReadField.RINGS_WORD) < 50) {
                despawn(c); // USA gate precedes all parent routine admission; NO DrawObject follows.
            } else {
                dispatch(c, false);
                if (c.state.routine() != 4) draw(c); // Live byte, including an Init's cleared routine0.
            }
            return new Result(placement.pool().state(wholeIndex), c.effects);
        } finally { running = false; }
    }
    private void dispatch(Cursor c, boolean flash) {
        int routine = c.state.routine();
        if ((routine & 1) != 0 || routine > 4) throw unsupported(UnsupportedBoundary.ROUTINE_DISPATCH, c, routine);
        if (flash) {
            switch (routine) {
                case 0 -> {
                    c.enter(NativeRoutine.FLASH_INIT);
                    c.write(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4).addByte(ROUTINE, 2)
                            .moveWord(TILE, 0x03EF).moveLong(MAPPINGS, assets.flashMappingsAddress()));
                    animate(c, true);
                }
                case 2 -> animate(c, true);
                case 4 -> { c.enter(NativeRoutine.FLASH_DELETE); delete(c, DeleteReason.FLASH_FINISHED); }
            }
        } else {
            switch (routine) {
                case 0 -> { if (init(c)) main(c); }
                case 2 -> main(c);
                case 4 -> animate(c, false);
            }
        }
    }
    private boolean init(Cursor c) {
        c.enter(NativeRoutine.RING_INIT);
        if (read(c, ReadField.TIME_STONES_BYTE) == 0x7F) { delete(c, DeleteReason.ALL_TIME_STONES); return false; }
        if (read(c, ReadField.TIME_ATTACK_BYTE) != 0) { delete(c, DeleteReason.TIME_ATTACK); return false; }
        c.write(c.state.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, c.state.spriteFlags() | 4)
                .moveWord(TILE, 0x2488).moveLong(MAPPINGS, assets.ringMappingsAddress())
                .moveByte(X_RADIUS, 32).moveByte(WIDTH, 32).moveByte(Y_RADIUS, 32));
        return true;
    }
    private record Touch(int xRadius, int playerX, int yRadius, int playerY, boolean collided) { }
    private Touch touch(Cursor c) {
        int xr = read(c, ReadField.PLAYER_X_RADIUS_BYTE);
        int width = ((byte) xr + 16) & 0xFFFF;
        int x = read(c, ReadField.PLAYER_X_WORD);
        int dx = (x - c.state.centreX() + width) & 0xFFFF;
        if ((short) dx < 0 || dx >= ((width + width) & 0xFFFF)) return new Touch(xr, x, -1, -1, false);
        int yr = read(c, ReadField.PLAYER_Y_RADIUS_BYTE);
        int height = ((byte) yr + 32) & 0xFFFF;
        int y = read(c, ReadField.PLAYER_Y_WORD);
        int dy = (y - c.state.centreY() + height) & 0xFFFF;
        return new Touch(xr, x, yr, y, (short) dy >= 0 && dy < ((height + height) & 0xFFFF));
    }
    private record AllocationKey(CdObjectPlacementRuntime.Status status, List<CdObjectState> images,
                                 String savedFlags, Touch touch, int cameraX) { }
    private void main(Cursor c) {
        var failedCuts = new HashSet<AllocationKey>();
        while (true) {
            c.enter(NativeRoutine.RING_MAIN);
            var contact = touch(c);
            if (!contact.collided()) { animate(c, false); return; }
            special(c); // MOVE.B #1,specialStage precedes the routine write.
            c.write(c.state.addByte(ROUTINE, 2));
            int cameraX = read(c, ReadField.CAMERA_X_WORD);
            player(c, new PlayerXWord((cameraX + 336) & 0xFFFF, capture(c), c.state));
            player(c, new ControlLockBit0(capture(c), c.state));
            player(c, new ControlHold0808(capture(c), c.state));
            player(c, new XVelocityZero(capture(c), c.state));
            player(c, new GroundVelocityZero(capture(c), c.state));
            scroll(c); fm(c);
            var found = placement.pool().findObjSlot(); c.effects.add(new SlotSearchReturned(found));
            if (found.isPresent()) {
                var child = placement.pool().state(found.getAsInt()).moveByte(ID, OBJECT_ID)
                        .moveWord(X, c.state.centreX()).moveWord(Y, c.state.centreY()).moveByte(SUBTYPE, 1);
                placement.pool().write(child); c.effects.add(new ChildCreated(child));
                animate(c, false); return;
            }
            // Actual BNE.S returns to Main, not the outer fifty-ring gate. Re-read the CURRENT player.
            // A repeated complete OWNED placement/touch/camera cut fences the unclosed native continuation,
            // not a retry budget or a claim that the entire external programme/audio state has cycled.
            // Expose the real prefix; never corrupt/unown an allocation address or fabricate a returned body.
            var failedCut = capture(c);
            if (!failedCuts.add(new AllocationKey(failedCut.status(), failedCut.pool().slots(),
                    HexFormat.of().formatHex(failedCut.savedFlags().copy()), contact, cameraX)))
                throw unsupported(UnsupportedBoundary.ALLOCATION_MAIN_CYCLE, c, c.state.routine());
            c.effects.add(new AllocationRecheck());
        }
    }
    private void special(Cursor c) {
        if (specialStage == null) throw unsupported(UnsupportedBoundary.SPECIAL_STAGE_OWNER, c, 1);
        var cut = capture(c); var call = new SpecialStageCall(cut, c.state);
        readOnly(cut, () -> closureInvoke(call,cut,c.state,() -> specialStage.store(call))); c.effects.add(new SpecialStageStored());
    }
    private void player(Cursor c, PlayerStore call) {
        if (playerCommit == null) throw unsupported(UnsupportedBoundary.PLAYER_OWNER, c, 0);
        requireSource(call.actualCut(), c.state);
        readOnly(call.actualCut(), () -> closureInvoke(call,call.actualCut(),c.state,() -> playerCommit.commit(call)));
        c.effects.add(new PlayerCommitted(call.getClass()));
    }
    private void scroll(Cursor c) {
        if (scrollLock == null) throw unsupported(UnsupportedBoundary.SCROLL_LOCK_OWNER, c, 1);
        var cut = capture(c); var call = new ScrollLockHighByteOne(cut, c.state);
        readOnly(cut, () -> closureInvoke(call,cut,c.state,() -> scrollLock.store(call))); c.effects.add(new ScrollLockStored());
    }
    private void fm(Cursor c) {
        if (sound == null) throw unsupported(UnsupportedBoundary.SOUND_OWNER, c, FM_BIG_RING);
        var cut = capture(c); var call = new SoundCall(cut, c.state);
        readOnly(cut, () -> closureInvoke(call,cut,c.state,() -> sound.request(call))); c.effects.add(new SoundRequested());
    }

    /** Successful animation is computed once by the existing shared owner from its ORIGINAL inputs. */
    private void animate(Cursor c, boolean flash) {
        c.enter(flash ? NativeRoutine.FLASH_ANIMATE : NativeRoutine.RING_ANIMATE);
        var original = c.state; var bytes = flash ? assets.flashAnimations() : assets.ringAnimations();
        animationHead(c);
        if (bytes.length() < 2) {
            if (c.state.signedByte(ANIMATION_TIME) < 0)
                throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_READ, c, c.state.unsignedByte(ANIMATION) * 2);
            c.effects.add(new AnimationReturned(flash)); return; // Native held path reaches no table read.
        }
        CdObjectAnimation.State result;
        try {
            result = CdObjectAnimation.step(new CdObjectAnimation.State(original.unsignedByte(ANIMATION),
                    original.unsignedByte(PREVIOUS_ANIMATION), original.unsignedByte(ANIMATION_FRAME), original.unsignedByte(ANIMATION_TIME),
                    original.unsignedByte(MAP_FRAME), original.spriteFlags(), original.flags(), original.routine(), original.unsignedByte(ROUTINE_2)),
                    new CdObjectAnimation.AnimationTable(bytes, 0, 1));
        } catch (UnsupportedOperationException originalFailure) {
            try { animationReadTail(c, bytes); }
            catch (UnsupportedBigRing reached) { reached.initCause(originalFailure); throw reached; }
            throw new IllegalStateException("Shared Big Ring animation fault has no reached finite read", originalFailure);
        }
        c.write(original.moveByte(ANIMATION, result.animationId()).moveByte(PREVIOUS_ANIMATION, result.previousId())
                .moveByte(ANIMATION_FRAME, result.scriptIndex()).moveByte(ANIMATION_TIME, result.timer()).moveByte(MAP_FRAME, result.mapFrame())
                .moveByte(SPRITE_FLAGS, result.spriteFlags()).moveByte(FLAGS, result.statusFlags())
                .moveByte(ROUTINE, result.routine()).moveByte(ROUTINE_2, result.routine2()));
        c.effects.add(new AnimationReturned(flash));
    }
    private void animationHead(Cursor c) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != c.state.unsignedByte(PREVIOUS_ANIMATION))
            c.write(c.state.moveByte(PREVIOUS_ANIMATION, animation).moveByte(ANIMATION_FRAME, 0).moveByte(ANIMATION_TIME, 0));
        c.write(c.state.addByte(ANIMATION_TIME, -1));
    }
    /** Only failure prefixes, never a successful fallback animator or another shared step. */
    private void animationReadTail(Cursor c, CdAssetBytes bytes) {
        int animation = c.state.unsignedByte(ANIMATION);
        if (animation != 0) throw unsupported(UnsupportedBoundary.ANIMATION_DIRECTORY_READ, c, animation * 2);
        int script = (short) bytes.unsignedWord(0);
        c.write(c.state.moveByte(ANIMATION_TIME, animationByte(c, bytes, script, UnsupportedBoundary.ANIMATION_SPEED_READ)));
        int index = c.state.unsignedByte(ANIMATION_FRAME);
        int frame = animationByte(c, bytes, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ);
        if (frame == 0xFF) {
            c.write(c.state.moveByte(ANIMATION_FRAME, 0));
            animationByte(c, bytes, script + 1, UnsupportedBoundary.ANIMATION_FRAME_READ);
        } else if (frame == 0xFE) {
            int back = animationByte(c, bytes, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ);
            index = (index - back) & 0xFF; c.write(c.state.moveByte(ANIMATION_FRAME, index));
            animationByte(c, bytes, script + 1 + index, UnsupportedBoundary.ANIMATION_FRAME_READ);
        } else if (frame == 0xFD) animationByte(c, bytes, script + 2 + index, UnsupportedBoundary.ANIMATION_OPERAND_READ);
    }
    private int animationByte(Cursor c, CdAssetBytes bytes, int at, UnsupportedBoundary boundary) {
        if (at < 0 || at >= bytes.length()) throw unsupported(boundary, c, at);
        return bytes.unsignedByte(at);
    }
    private void despawn(Cursor c) {
        boolean outside = false;
        if (c.state.signedByte(SPRITE_FLAGS) >= 0) {
            int cameraX = read(c, ReadField.CAMERA_X_WORD);
            int distance = ((c.state.centreX() & 0xFF80) - (((cameraX - 0x80) & 0xFFFF) & 0xFF80)) & 0xFFFF;
            outside = distance > 0x280;
        }
        boolean remove = outside || (read(c, ReadField.TIME_ZONE_BYTE) & 0x80) != 0;
        if (remove) {
            int savedId = c.state.savedFlagsId();
            if (savedId != 0) {
                int copied = read(c, ReadField.TIME_ZONE_BYTE), period = copied & 0x7F;
                if ((copied & 0x80) != 0) {
                    period = (short) ((period - (byte) read(c, ReadField.TIME_WARP_DIRECTION_BYTE)) & 0xFFFF);
                    period = period < 0 ? 0 : Math.min(period, 2);
                }
                int at = 2 + (short) ((3 * savedId + period) & 0xFFFF);
                if (at < 2 || at >= CdObjectPlacementRuntime.SAVED_BANK_BYTES)
                    throw unsupported(UnsupportedBoundary.SAVED_FLAGS_ADDRESS, c, at);
                int id = (at - 2) / 3, nativePeriod = (at - 2) % 3;
                int before = placement.savedFlag(id, nativePeriod);
                placement.bclrSavedFlag(id, nativePeriod, CdObjectPlacementRuntime.SavedBit.LOADED);
                c.effects.add(new SavedFlagChanged(at, before, placement.savedFlag(id, nativePeriod)));
            }
            delete(c, DeleteReason.DESPAWN);
        }
        c.effects.add(new DespawnChecked(remove));
    }
    private void delete(Cursor c, DeleteReason reason) {
        placement.pool().deleteObject(c.whole); c.state = placement.pool().state(c.whole);
        c.effects.add(new Deleted(reason));
    }
    private int read(Cursor c, ReadField field) {
        if (nativeRead == null) throw unsupported(UnsupportedBoundary.READ_OWNER, c, field.ordinal());
        var cut = capture(c); var call = new ReadCall(field, cut, c.state);
        int value = readValue(cut, () -> closureRead(call,cut,c.state,() -> nativeRead.read(call)));
        int mask = field.widthBits() == 8 ? 0xFF : 0xFFFF;
        if ((value & ~mask) != 0) throw unsupported(UnsupportedBoundary.READ_VALUE_WIDTH, c, value);
        c.effects.add(new ReadReturned(field, value)); return value;
    }
    private void draw(Cursor c) {
        var cut = capture(c); var call = new DrawCall(cut, c.state);
        var expected = CdObjectPool.overlay(cut.pool(), List.of(c.state.moveByte(SPRITE_FLAGS, c.state.spriteFlags() & 0x7F)));
        assertPlacement(cut, cut.pool());
        try { closureInvoke(call,cut,c.state,() -> drawObject.drawObject(call)); }
        catch (RuntimeException | Error original) {
            suppress(original, () -> { if (!samePlacement(cut, cut.pool())) assertPlacement(cut, expected); }); throw original;
        }
        assertPlacement(cut, expected); c.state = placement.pool().state(c.whole); c.effects.add(new DrawReturned());
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
        assertPlacement(cut, cut.pool()); final int value;
        try { value = callback.getAsInt(); }
        catch (RuntimeException | Error original) { suppress(original, () -> assertPlacement(cut, cut.pool())); throw original; }
        assertPlacement(cut, cut.pool()); return value;
    }
    private boolean samePlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        var now = placement.capture(); return now.status().equals(cut.status())
                && Arrays.equals(now.savedFlags().copy(), cut.savedFlags().copy()) && now.pool().slots().equals(expectedPool.slots());
    }
    private void assertPlacement(CdObjectPlacementRuntime.Snapshot cut, CdObjectPool.Snapshot expectedPool) {
        placement.validateRestore(cut); placement.pool().validateRestore(expectedPool);
        if (!samePlacement(cut, expectedPool)) throw new IllegalStateException("Big Ring callback changed its actual full placement cut");
    }
    private static void requireSource(CdObjectPlacementRuntime.Snapshot cut, CdObjectState source) {
        Objects.requireNonNull(cut); Objects.requireNonNull(source);
        if (!cut.pool().slots().get(source.identity().dynamicIndex()).equals(source))
            throw new IllegalArgumentException("Big Ring source is not this full placement cut's actual image");
    }
    private static void suppress(Throwable original, Runnable verification) {
        try { verification.run(); } catch (RuntimeException | Error failed) { if (failed != original) original.addSuppressed(failed); }
    }
    private static UnsupportedBigRing unsupported(UnsupportedBoundary boundary, Cursor c, int value) {
        return new UnsupportedBigRing(boundary, c, value);
    }
    private final class Cursor {
        final int whole; final List<Effect> effects = new ArrayList<>(); CdObjectState state;
        Cursor(CdObjectState state) { this.state = state; whole = state.identity().wholeIndex(); }
        void write(CdObjectState next) { placement.pool().write(next); state = next; }
        void enter(NativeRoutine routine) { effects.add(new RoutineEntered(routine)); }
    }
    private static void width(int value, int bits) {
        int mask = bits == 8 ? 0xFF : 0xFFFF;
        if ((value & ~mask) != 0) throw new IllegalArgumentException("Expected unsigned native " + bits + "-bit value");
    }
}
