package com.openggf.game.soniccd.objects;

import com.openggf.game.soniccd.CdNativeFile;
import com.openggf.game.soniccd.CdReleaseVariant;
import com.openggf.game.soniccd.CdStageKey;
import com.openggf.game.soniccd.level.CdObjectVisualAssets;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.media.CdMmdImage;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Finite native ObjRing body, UNADMITTED to a world. Source pin:
 * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, src/Level/_Objects/Ring/Main.asm:7-312.
 * Uses the existing physical pool and saved bank; owns no pass, touch scan, allocator,
 * queue, renderer, audio backend, reset/carry policy or additional mutable snapshot.
 *
 * <p>Caller dispatches at the actual live RunObjects cut, after player touch, and
 * supplies the sole globals owner plus synchronous source-call ports. No atomic-frame
 * rollback is claimed: completed source writes remain on failure, which propagates.
 * The frame root must close admission rather than retry a partially executed pass.</p>
 */
public final class CdRing {
    public enum FmSound { FM_RING }
    public enum SubCpuCommand { SCMD_YESSFX }

    /** One authoritative parent-owned globals store; writes publish values, not replayable actions. */
    public interface GlobalsPort {
        CdRingGlobals read();
        void write(CdRingGlobals state);
    }

    /** Native calls only, at their source cut. No generic action list or private queue. */
    public interface SourceCalls {
        void playFmSound(FmSound sound);
        void subCpuCommand(SubCpuCommand command);

        /**
         * Execute shared DrawObject semantics synchronously on this LIVE pool pointer:
         * clear bit 7 first, pre-scroll camera cull, then shared priority queue admission.
         * Ring performs no writes after this call. Later shared DrawObjects sees the live
         * slot after scroll (including subsequent deletion/reuse), never a copied packet.
         */
        void drawObject(CdObjectPool pool, int wholeIndex);
    }

    /**
     * Explicit bounded data for the pure algorithm. This is not media authentication.
     * Production uses fromAssets; no raw-data constructor can bind an authenticated placement owner.
     */
    public record SourceData(List<CdR11aObjectAssets.Delta> deltas, int mappingAddress,
                             int baseTileWord, CdObjectVisualAssets visuals) {
        public SourceData {
            deltas = List.copyOf(deltas);
            Objects.requireNonNull(visuals, "visuals");
            if (deltas.size() != 16 || mappingAddress < 0x200000 || mappingAddress >= 0x240000
                    || (mappingAddress & 1) != 0 || (baseTileWord & ~0xFFFF) != 0
                    || visuals.animations().size() != 1) {
                throw new IllegalArgumentException("Unbounded Ring source data");
            }
            for (var delta : deltas) {
                if (delta.x() < -128 || delta.x() > 127 || delta.y() < -128 || delta.y() > 127) {
                    throw new IllegalArgumentException("Ring deltas require signed source bytes");
                }
            }
        }
    }

    /**
     * Native fields sampled for this body call, not coordinates/frames from a comparison row.
     * timeWarpDirectionByte is a raw byte, sign-extended only by the native warp branch.
     * Supported period arithmetic does not authorize warp/checkpoint WORLD entry.
     */
    public record Inputs(CdReleaseVariant variant, CdStageKey stage, int zoneByte, int cameraXWord,
                         int timeZoneByte, int timeWarpDirectionByte) {
        public Inputs {
            Objects.requireNonNull(variant, "variant"); Objects.requireNonNull(stage, "stage");
            if ((zoneByte & ~0xFF) != 0 || (cameraXWord & ~0xFFFF) != 0
                    || (timeZoneByte & ~0xFF) != 0 || (timeWarpDirectionByte & ~0xFF) != 0) {
                throw new IllegalArgumentException("Ring input exceeds native byte/word widths");
            }
        }

        private void requireSupportedDomain() {
            if (variant != CdReleaseVariant.USA || !stage.equals(CdStageKey.parse("R11A")) || zoneByte != 0) {
                // MMZ's explicit priority/subtype2/tile branch is not executed as R11A (:159-166).
                throw new UnsupportedOperationException("ObjRing body is bounded to USA R11A zone 0; selected "
                        + variant + " " + stage.nativeId() + " zone=" + zoneByte);
            }
            if ((timeZoneByte & 0x7F) > 2) {
                throw new UnsupportedOperationException("ObjRing selected unowned native time zone " + timeZoneByte);
            }
        }

        private boolean warping() { return (timeZoneByte & 0x80) != 0; }
        private int warpD2Word() { return word(-(byte) timeWarpDirectionByte); }
        private int savedPeriod() {
            int period = timeZoneByte & 0x7F;
            if (warping()) { period = Math.clamp(period - (byte) timeWarpDirectionByte, 0, 2); }
            return period;
        }
    }

    private final CdObjectPlacementRuntime placement;
    private final SourceData data;
    private final CdObjectAnimation.AnimationTable sparkle;
    private final Optional<CdR11aAssets.Identity> identity;
    private final Optional<CdR11aObjectAssets.ArtBinding> art;

    /** Pure declared-input consumer, like the placement/visual decoders; no asset identity is conferred. */
    public CdRing(CdObjectPlacementRuntime placement, SourceData data) {
        this(placement, data, Optional.empty(), Optional.empty());
        if (placement.sourceIdentity().isPresent()) {
            throw new IllegalArgumentException("Authenticated placement requires actual Ring assets");
        }
    }

    private CdRing(CdObjectPlacementRuntime placement, SourceData data,
                   Optional<CdR11aAssets.Identity> identity, Optional<CdR11aObjectAssets.ArtBinding> art) {
        this.placement = Objects.requireNonNull(placement, "placement");
        this.data = Objects.requireNonNull(data, "data");
        this.identity = identity; this.art = art;
        sparkle = new CdObjectAnimation.AnimationTable(data.visuals().animationTable(), 0, 1);
    }

    /**
     * Source-bound production ingress. Assets come from CdProgramAssets -> ordinary R11A loader ->
     * openingObjects(), which authenticates actual retained bytes. No new data bytes/owner change needed.
     * The retained ArtBinding is descriptive: it proves no preload, live residency or PLC execution.
     */
    public static CdRing fromAssets(CdObjectPlacementRuntime placement, CdR11aObjectAssets assets) {
        Objects.requireNonNull(placement, "placement"); Objects.requireNonNull(assets, "assets");
        var identity = assets.identity();
        if (identity.variant() != CdReleaseVariant.USA || !identity.stage().equals(CdStageKey.parse("R11A"))
                || identity.file() != CdNativeFile.R11A || identity.domain() != CdMmdImage.Domain.ORIGINAL_WORD_RAM) {
            throw new UnsupportedOperationException("ObjRing assets must be authenticated original USA R11A");
        }
        if (!placement.sourceIdentity().equals(Optional.of(identity))) {
            throw new IllegalArgumentException("Ring/placement source identity mismatch or constructed placement");
        }
        var art = assets.initialArtBank().binding(CdR11aAsset.ART_RINGS);
        var deltas = java.util.stream.IntStream.range(0, 16).mapToObj(assets::ringDelta).toList();
        var data = new SourceData(deltas, assets.view(CdR11aObjectAssets.Table.RING_MAPPINGS).range().nativeAddress(),
                art.baseWord(), assets.rings());
        return new CdRing(placement, data, Optional.of(identity), Optional.of(art));
    }

    public SourceData sourceData() { return data; }
    public Optional<CdR11aAssets.Identity> sourceIdentity() { return identity; }
    public Optional<CdR11aObjectAssets.ArtBinding> artBinding() { return art; }

    /**
     * Execute the already selected ObjRing handler exactly once, not the entire object pass.
     * Native dispatch has already fetched the ID: there is no extra current-ID-zero guard here.
     * Caller alone assigns collection routine 4 through touch; never also award via RingManager.
     */
    public void executeSelected(int wholeIndex, Inputs inputs, GlobalsPort globals, SourceCalls calls) {
        Objects.requireNonNull(inputs, "inputs").requireSupportedDomain();
        Objects.requireNonNull(globals, "globals"); Objects.requireNonNull(calls, "calls");
        switch (placement.pool().state(wholeIndex).routine()) {
            case 0 -> initialize(wholeIndex, inputs, globals, calls);
            case 2 -> main(wholeIndex, inputs, globals, calls);
            case 4 -> collect(wholeIndex, inputs, globals, calls);
            case 6 -> sparkle(wholeIndex, calls);
            case 8 -> placement.pool().deleteObject(wholeIndex);
            default -> throw new UnsupportedOperationException("ObjRing unowned routine "
                    + placement.pool().state(wholeIndex).routine() + " in whole slot " + wholeIndex);
        }
    }

    /** ObjRing_Init:38-210, including each native period-selection D2 write and no implicit clear. */
    private void initialize(int whole, Inputs in, GlobalsPort globals, SourceCalls calls) {
        var pool = placement.pool();
        var parent = pool.state(whole);
        int savedId = parent.savedFlagsId();
        int period = in.savedPeriod();
        placement.savedFlag(savedId, period); // Native first bank read (:67), before subtype/table lookup.
        int countMinusOne = parent.subtype() & 7;
        if (countMinusOne == 7) { countMinusOne = 6; }
        var delta = data.deltas().get(parent.subtype() >>> 4);
        int x = parent.centreX(); int y = parent.centreY();
        for (int member = 1; member <= countMinusOne + 1; member++) {
            // FixBugs=0 shipped source: .GotTime2/3 preparation (:94-97,126-129)
            // reuses D2 for -signed(timeWarpDir), CLOBBERING the current X register.
            // No fixed alternative exists here; retaining the anchor would repair the ROM.
            if (in.warping()) { x = in.warpD2Word(); }
            if (!collectedInSelectedOrEarlier(savedId, period, member)) {
                placement.bclrSavedFlag(savedId, period, CdObjectPlacementRuntime.SavedBit.LOADED);
                int target = whole;
                if (member != 1) {
                    // FindNext always restarts after A0, not after the previous A1 child (:147).
                    var found = pool.findNextObjSlotAfterWholeIndex(whole);
                    if (found.isEmpty()) { break; } // .DidInit, not rollback or deferred spawn.
                    target = found.getAsInt();
                }
                var next = pool.state(target).moveByte(ID, 0x10).moveByte(ROUTINE, 2).moveWord(X, x);
                // :153 precedes :154: when A1==A0 the anchor reads the newly written parent X.
                int anchor = target == whole ? next.centreX() : pool.state(whole).centreX();
                next = next.moveWord(RING_ANCHOR_X, anchor).moveWord(Y, y)
                        .moveLong(MAPPINGS, data.mappingAddress()).moveWord(TILE, data.baseTileWord())
                        .moveByte(PRIORITY, 2).moveByte(SPRITE_FLAGS, 4).moveByte(COLLISION_TYPE, 0x47)
                        .moveByte(WIDTH, 8).moveByte(Y_RADIUS, 8)
                        .moveByte(SAVED_FLAGS_ID, pool.state(whole).savedFlagsId()).moveByte(RING_MEMBER, member);
                pool.write(next); // Animation, fractions, subtype2, status, velocities etc. stay dirty.
            }
            x = word(x + delta.x()); y = word(y + delta.y());
        }
        // .ChkDel inspects bit 0 only, after all child creation/failure. Plain DeleteObject.
        if (collectedInSelectedOrEarlier(savedId, period, 1)) { pool.deleteObject(whole); }
        else { main(whole, in, globals, calls); } // Native Init falls straight into Main.
    }

    private boolean collectedInSelectedOrEarlier(int savedId, int period, int member) {
        // LSR.B D1,D4 carry is the original bit member-1, not nonzero shifted data/loaded bit.
        for (int p = period; p >= 0; p--) {
            if ((placement.savedFlag(savedId, p) & (1 << (member - 1))) != 0) { return true; }
        }
        return false;
    }

    /** ObjRing_Main:214-232. Range uses anchor, wrapped words, unsigned BHI and inclusive $280. */
    private void main(int whole, Inputs in, GlobalsPort globals, SourceCalls calls) {
        var pool = placement.pool(); var state = pool.state(whole);
        if ((state.spriteFlags() & 0x80) == 0) {
            int anchorChunk = state.word(RING_ANCHOR_X) & 0xFF80;
            int cameraChunk = word(in.cameraXWord() - 0x80) & 0xFF80;
            if (word(anchorChunk - cameraChunk) > 0x280) { pool.deleteObject(whole); return; }
        }
        var g = Objects.requireNonNull(globals.read(), "globals state");
        if (g.timeStopTimer() == 0) { pool.write(state.moveByte(MAP_FRAME, g.ringAnimationFrame())); }
        calls.drawObject(pool, whole); // Tail call: do not write a stale image after shared DrawObject.
    }

    /** ObjRing_Collect:237-271 then native fallthrough to Sparkle. */
    private void collect(int whole, Inputs in, GlobalsPort globals, SourceCalls calls) {
        var pool = placement.pool();
        pool.write(pool.state(whole).addByte(ROUTINE, 2).moveByte(COLLISION_TYPE, 0).moveByte(PRIORITY, 1));
        collectRing(globals, calls); // Sound observes reward writes, before collected bank bit (:241).
        var state = pool.state(whole);
        int bit = (state.unsignedByte(RING_MEMBER) - 1) & 7; // BSET to memory is modulo 8, even member=0.
        placement.bsetSavedFlag(state.savedFlagsId(), in.savedPeriod(), CdObjectPlacementRuntime.SavedBit.values()[bit]);
        sparkle(whole, calls); // No oAnim/oPrevAnim/oAnimFrame/oAnimTime/map reset in Collect.
    }

    /** CollectRing:290-312. BSET uses the old bit; thresholds compare unsigned after word wrap. */
    private static void collectRing(GlobalsPort globals, SourceCalls calls) {
        var g = Objects.requireNonNull(globals.read(), "globals state");
        int rings = word(g.rings() + 1); int flags = g.livesFlags();
        boolean life = false;
        if (rings >= 100) {
            boolean already100 = (flags & 2) != 0;
            flags |= 2;
            if (!already100) { life = true; }
            else if (rings >= 200) {
                boolean already200 = (flags & 4) != 0;
                flags |= 4;
                life = !already200;
            }
        }
        globals.write(new CdRingGlobals(rings, (g.lives() + (life ? 1 : 0)) & 0xFF, flags,
                g.updateHudRings() | 1, (g.updateHudLives() + (life ? 1 : 0)) & 0xFF,
                g.timeStopTimer(), g.ringAnimationTimer(), g.ringAnimationFrame()));
        if (life) { calls.subCpuCommand(SubCpuCommand.SCMD_YESSFX); }
        else { calls.playFmSound(FmSound.FM_RING); }
    }

    /** ObjRing_Sparkle:276-279; $FC advances routine, deletion belongs to the NEXT dispatch. */
    private void sparkle(int whole, SourceCalls calls) {
        var pool = placement.pool(); var s = pool.state(whole);
        var next = CdObjectAnimation.step(new CdObjectAnimation.State(s.unsignedByte(ANIMATION),
                s.unsignedByte(PREVIOUS_ANIMATION), s.unsignedByte(ANIMATION_FRAME), s.unsignedByte(ANIMATION_TIME),
                s.unsignedByte(MAP_FRAME), s.spriteFlags(), s.flags(), s.routine(), s.unsignedByte(ROUTINE_2)), sparkle);
        pool.write(s.moveByte(ANIMATION, next.animationId()).moveByte(PREVIOUS_ANIMATION, next.previousId())
                .moveByte(ANIMATION_FRAME, next.scriptIndex()).moveByte(ANIMATION_TIME, next.timer())
                .moveByte(MAP_FRAME, next.mapFrame()).moveByte(SPRITE_FLAGS, next.spriteFlags())
                .moveByte(FLAGS, next.statusFlags()).moveByte(ROUTINE, next.routine()).moveByte(ROUTINE_2, next.routine2()));
        calls.drawObject(pool, whole);
    }

    private static int word(int value) { return value & 0xFFFF; }
}
