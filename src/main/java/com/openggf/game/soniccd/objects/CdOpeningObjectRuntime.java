package com.openggf.game.soniccd.objects;

import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.graphics.SpritePresentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.openggf.game.soniccd.objects.CdObjectDrawQueue.*;
import static com.openggf.game.soniccd.objects.CdObjectState.ByteField.*;
import static com.openggf.game.soniccd.objects.CdObjectState.LongField.MAPPINGS;
import static com.openggf.game.soniccd.objects.CdObjectState.WordField.*;

/**
 * Bounded source-ordered composition, NOT a world/route/native-entry admission.
 * scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
 * Level_MainLoop (Level/Main.asm:333-430), RunObjects (Object Functions.asm:11-33).
 * Authored external source; compilation and execution belong to the integrating root.
 *
 * <p>Explicit DEV policy: USA R11A Present, goodFuture=0, absent reserved owners
 * 1..31, initially zero CPU SAT/empty picture, and preloaded actual Ring/Mosqui
 * art. The initial Ring globals are caller-declared ingress, never an entry proof.
 * This does not execute native title/HUD/PLC/section-art/audio/plane publication.
 * The loader, not declarations or this composition, authenticates asset bytes.</p>
 *
 * <p>Root prepares input on the exact bound Frame before service, owns the
 * incremented level word, actor awaiting/projection, reserved-slot admission,
 * source camera/scroll, render banks and composite restore preflight. It must
 * replace the actor's old one-player SAT path. All callbacks are synchronous,
 * on the owning game thread, and non-reentrant. Failure closes Frame and queue;
 * completed writes are neither retried nor rolled back.</p>
 */
public final class CdOpeningObjectRuntime implements NativeDynamicObjectPass, PlcLifecycleService, AutoCloseable {
    public enum Phase {
        CLOSED_CUT, SERVICING, SERVICED, SPAWNING, SPAWNED, PLAYER_BODY, PLAYER_DONE, PLAYER_ENQUEUE,
        DYNAMIC_SLOTS, DYNAMIC_DONE, BUILDING, BUILT, TAIL, FAILED, CLOSED
    }

    /**
     * Stable bases in ONE root-governed atlas range exclusive to this picture.
     * Root reserves/binds its three disjoint subdivisions; this value neither
     * claims graphics ownership nor recommends the arbitrary IDs used by tests.
     */
    public record LogicalBanks(int player, int ring, int mosqui) { }

    /**
     * Required narrow port to the EXISTING shared primaryAngle byte, not oAngle.
     * Mosqui returns this write without applying it. Root must write that byte
     * exactly once, without restoring a Frame capture or running another owner.
     * Invocation is in returned-effect order, before the next physical dispatch.
     */
    @FunctionalInterface public interface PrimaryAngleWrite { void write(int unsignedByte); }

    /** Ordered requests only, no playback and no claim to the omitted service/music ledger. */
    public sealed interface SoundRequest permits PlayerSound, RingFm, RingSubCpu, MosquiFm { }
    public record PlayerSound(CdPlayerFrameRuntime.Sound sound) implements SoundRequest {
        public PlayerSound { Objects.requireNonNull(sound, "player sound"); }
    }
    public record RingFm(CdRing.FmSound sound) implements SoundRequest {
        public RingFm { Objects.requireNonNull(sound, "Ring FM sound"); }
    }
    public record RingSubCpu(CdRing.SubCpuCommand command) implements SoundRequest {
        public RingSubCpu { Objects.requireNonNull(command, "Ring sub-CPU command"); }
    }
    public record MosquiFm(CdMosqui.Sound sound) implements SoundRequest {
        public MosquiFm { Objects.requireNonNull(sound, "Mosqui FM sound"); }
    }

    /**
     * Privately minted owned partition; callers cannot replace its pieces or owner.
     * Placement already contains the ONLY pool/saved-bank snapshot. Queue contains
     * the CPU SAT; displayedFrame freezes its own pixels, possibly an older picture.
     * No actor/shared Frame, pending input, TouchContext, scan snapshot, camera or
     * scroll capture is stored. Root captures/prevalidates those owners separately.
     */
    public static final class Snapshot {
        private final Object owner;
        private final CdObjectPlacementRuntime.Snapshot placement;
        private final CdRingGlobals ringGlobals;
        private final CdObjectDrawQueue.Snapshot queue;
        private final SpritePresentation.Frame displayedFrame;
        private final List<SoundRequest> sounds;

        private Snapshot(Object owner, CdObjectPlacementRuntime.Snapshot placement, CdRingGlobals ringGlobals,
                         CdObjectDrawQueue.Snapshot queue, SpritePresentation.Frame displayedFrame,
                         List<SoundRequest> sounds) {
            this.owner = owner;
            this.placement = placement;
            this.ringGlobals = ringGlobals;
            this.queue = queue;
            this.displayedFrame = displayedFrame;
            this.sounds = List.copyOf(sounds);
        }
        public CdObjectPlacementRuntime.Snapshot placement() { return placement; }
        public CdRingGlobals ringGlobals() { return ringGlobals; }
        public SpritePresentation.Frame displayedFrame() { return displayedFrame; }
        public List<SoundRequest> lastSoundRequests() { return sounds; }
    }

    private final Object captureOwner = new Object();
    private final CdR11aAssets assets;
    private final CdPlayerFrameRuntime frame;
    private final CdR11aObjectAssets objects;
    private final CdObjectPlacementRuntime placement;
    private final CdPlayerObjectTouch scanner;
    private final CdRing ring;
    private final CdMosqui mosqui;
    private final CdObjectDrawQueue queue;
    private final LogicalBanks banks;
    private final PrimaryAngleWrite primaryAngleWrite;
    private CdRingGlobals ringGlobals;
    private List<SoundRequest> sounds = List.of();
    private SpritePresentation.Frame displayed = SpritePresentation.Frame.empty();
    private CameraWords preScrollForeground;
    // Only the ephemeral eligible call cut awaits the actor's explicit enqueue.
    // Never a raw player slot, Body publication, pool snapshot or capture partition.
    private CdPlayerBody.DisplayEligibility expectedPlayerCut;
    private boolean cameraPrepared;
    private Phase phase = Phase.CLOSED_CUT;
    private boolean operating;
    private boolean cleaned;
    private boolean inDrawCallback;
    private int dispatchWholeIndex = -1;

    /**
     * Sole construction path. The exact external-touch Frame must use assets by
     * reference, be open at a closed cut, and have UNIMPLEMENTED dynamic fallback.
     * Root binds Frame art publication before the first service (two-stage wiring).
     * Actual openingObjects() reauthenticates the retained media; declared source
     * records, mapping/pixel ports and raw object bytes are not accepted here.
     *
     * <p>The explicitly supplied 640 bytes must ALL be zero for this named DEV
     * empty-SAT policy. This is not a native retail title-to-level ingress proof.
     * Preload describes the pixel source only; root owns actual graphics banks.</p>
     */
    public static CdOpeningObjectRuntime usaR11aOpeningDevEmptySatPreloadedArt(
            CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
            byte[] initialCpuSatBytes, LogicalBanks banks, CameraWords preScrollForeground,
            PrimaryAngleWrite primaryAngleWrite) throws IOException {
        Objects.requireNonNull(assets, "actual R11A assets");
        Objects.requireNonNull(frame, "external-touch player Frame");
        Objects.requireNonNull(initialGlobals, "explicit initial Ring globals");
        Objects.requireNonNull(banks, "explicit logical bank bases");
        Objects.requireNonNull(preScrollForeground, "explicit pre-scroll foreground");
        Objects.requireNonNull(primaryAngleWrite, "required existing shared primaryAngle writer");
        if (frame.assets() != assets || frame.environment().dynamicSlots() != CdPlayerBody.DynamicSlots.UNIMPLEMENTED) {
            throw new IllegalArgumentException("Opening objects require the exact aggregate's external-touch Frame binding");
        }
        frame.captureShared(); // Existing closed-cut guard only; never retain a second Frame partition.
        byte[] initial = Objects.requireNonNull(initialCpuSatBytes, "explicit DEV initial CPU SAT").clone();
        if (initial.length != SAT_BYTE_SIZE) { throw new IllegalArgumentException("DEV initial SAT must contain 640 bytes"); }
        for (byte value : initial) {
            if (value != 0) { throw new IllegalArgumentException("Named DEV empty CPU/display ingress requires zero initial SAT bytes"); }
        }
        var objects = assets.openingObjects();
        var placement = CdObjectPlacementRuntime.fromAssets(assets);
        var mappings = CdObjectMappings.fromOpeningAssets(assets, objects);
        // Validate physical/logical bank extents and overlaps now, even for an empty picture.
        CdObjectPresentation.usaR11aOpeningDevPreloadedArt(frame.artState(), objects,
                banks.player(), banks.ring(), banks.mosqui());
        return new CdOpeningObjectRuntime(assets, frame, initialGlobals, initial, banks,
                preScrollForeground, primaryAngleWrite, objects, placement, mappings);
    }

    private CdOpeningObjectRuntime(CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
                                   byte[] initial, LogicalBanks banks, CameraWords camera,
                                   PrimaryAngleWrite primaryAngleWrite, CdR11aObjectAssets objects,
                                   CdObjectPlacementRuntime placement, CdObjectMappings mappings) {
        this.assets = assets;
        this.frame = frame;
        this.objects = objects;
        this.placement = placement;
        this.banks = banks;
        this.primaryAngleWrite = primaryAngleWrite;
        ringGlobals = initialGlobals;
        preScrollForeground = camera;
        cameraPrepared = true;
        scanner = CdPlayerObjectTouch.fromAssets(objects);
        ring = CdRing.fromAssets(placement, objects);
        queue = new CdObjectDrawQueue(new CdDrawSlots() {
            @Override public LiveDrawSlot read(CdSlotRef ref) { return readLiveSlot(ref); }
            @Override public void writeOnScreenBit(CdSlotRef ref, boolean value) { writeLiveOnScreenBit(ref, value); }
        }, mappings, initial);
        mosqui = new CdMosqui(placement, CdMosqui.Assets.fromObjectAssets(objects),
                new CdPlayerTerrain(assets.terrain()), (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
    }

    public Phase phase() { return phase; }
    public boolean closed() { return phase == Phase.FAILED || phase == Phase.CLOSED || frame.closed(); }
    /** Live placement authority; exposed for root's explicit source owners, not a second dispatcher. */
    public CdObjectPlacementRuntime placement() { return placement; }
    /** Immutable diagnostic state; no reset, HUD acknowledgement or time-stop writer is invented. */
    public CdRingGlobals ringGlobals() { return ringGlobals; }
    public List<SoundRequest> lastSoundRequests() { return sounds; }

    /** Supply the NEXT ordinary cycle's actual pre-scroll camera before preparing Frame input. */
    public void setPreScrollForeground(CameraWords camera) {
        perform(Phase.CLOSED_CUT, Phase.CLOSED_CUT, Phase.CLOSED_CUT, () -> {
            frame.captureShared();
            preScrollForeground = Objects.requireNonNull(camera, "next pre-scroll foreground");
            cameraPrepared = true;
            return null;
        });
    }

    /** Exactly one existing Frame service. NORMAL_PAUSE publishes; PP LAG retains its picture. */
    @Override public void serviceVBlank(PlcLifecyclePhase servicePhase) {
        boolean ordinary = servicePhase == PlcLifecyclePhase.ORDINARY_LEVEL;
        perform(Phase.CLOSED_CUT, Phase.SERVICING, ordinary ? Phase.SERVICED : Phase.CLOSED_CUT, () -> {
            requireSupportedService(servicePhase);
            if (ordinary && !cameraPrepared) {
                throw new IllegalStateException("Root must supply the next ordinary pre-scroll camera at a closed cut");
            }
            frame.serviceVBlank(servicePhase);
            checkActive(Phase.SERVICING); // Also detects a swallowed callback's reentrant failure.
            if (servicePhase != PlcLifecyclePhase.LAG) {
                // VInt_Level:827 SAT DMA then :829-834 art. At the completed boundary,
                // convert PREVIOUS CPU SAT using NOW-serviced immutable resident pixels.
                var pixels = CdObjectPresentation.usaR11aOpeningDevPreloadedArt(frame.artState(), objects,
                        banks.player(), banks.ring(), banks.mosqui());
                var nextDisplay = CdObjectPresentation.convert(queue.cpuSat(), pixels);
                checkActive(Phase.SERVICING);
                displayed = nextDisplay;
            }
            if (ordinary) { sounds = List.of(); }
            return null;
        });
    }

    @Override public void spawnBeforeReservedSlots() {
        perform(Phase.SERVICED, Phase.SPAWNING, Phase.SPAWNED, () -> {
            if (!cameraPrepared) { throw new IllegalStateException("Ordinary placement lacks its source camera"); }
            queue.beginPass();
            placement.spawnObjects(preScrollForeground.xWord(), frame.environment().movement().gates().timeZone());
            cameraPrepared = false;
            return null;
        });
    }

    /**
     * Detached item commit ONLY after the complete Frame.runBody returns.
     * Exactly one fresh pool capture
     * is passed in one ephemeral TouchContext; neither is stored here or in a
     * snapshot. The actor bridge subsequently calls enqueuePlayer exactly once
     * for an eligible Main Display; this method never calls DrawObject itself.
     * Deferring Display past the detached item commit is bounded to the reviewed
     * Body/Touch owner: no admitted
     * intervening observer reads player bit7 or queues another object there.
     */
    public CdPlayerFrameRuntime.BodyPublication runPlayerBody(int levelFrames) {
        return perform(Phase.SPAWNED, Phase.PLAYER_BODY, Phase.PLAYER_DONE, () -> {
            var publication = frame.runBody(levelFrames,
                    Optional.of(new CdPlayerBody.TouchContext(scanner, placement.pool().capture())));
            checkActive(Phase.PLAYER_BODY);
            boolean displaySeen = false;
            boolean touchSeen = false;
            for (var effect : publication.result().effects()) {
                if (effect instanceof CdPlayerBody.DisplayEligibility cut) {
                    if (displaySeen) { throw new IllegalStateException("Body published more than one Display cut"); }
                    displaySeen = true;
                    if (cut.eligible()) { expectedPlayerCut = cut; }
                } else if (effect instanceof CdPlayerBody.ObjectTouchResult touch) {
                    if (touchSeen) { throw new IllegalStateException("Body published more than one touch result"); }
                    touchSeen = true;
                    touch.result().changedSlot().ifPresent(placement.pool()::write);
                }
            }
            for (var sound : publication.sounds()) { appendSound(new PlayerSound(sound)); }
            return publication;
        });
    }

    /**
     * Sole player DrawObject entry, called by the actor bridge AFTER runPlayerBody.
     * Requires that exact eligible published cut (all raw width/priority/cull
     * fields); callers cannot enqueue an invented pose, a skipped Display or Init.
     * Only the pointer survives. A duplicate call closes the whole composition.
     */
    public void enqueuePlayer(CdPlayerBody.DisplayEligibility cut) {
        perform(Phase.PLAYER_DONE, Phase.PLAYER_ENQUEUE, Phase.PLAYER_DONE, () -> {
            if (expectedPlayerCut == null || !expectedPlayerCut.equals(Objects.requireNonNull(cut, "published player Display cut"))) {
                throw new IllegalStateException("Player enqueue requires the one pending eligible Body Display cut");
            }
            queue.drawObject(new DrawCallCut(new CdSlotRef(0), cut.spriteFlags(), cut.centreXWord(),
                    cut.centreYWord(), cut.yRadius(), cut.width(), cut.priority()), preScrollForeground);
            expectedPlayerCut = null;
            return null;
        });
    }

    /** One live physical walk; later children execute in THIS pass, earlier allocations wait. */
    @Override public void runDynamicSlotsAfterReservedSlots() {
        perform(Phase.PLAYER_DONE, Phase.DYNAMIC_SLOTS, Phase.DYNAMIC_DONE, () -> {
            if (expectedPlayerCut != null) {
                throw new IllegalStateException("Reserved player returned before its eligible Display was enqueued");
            }
            var globalsPort = new CdRing.GlobalsPort() {
                @Override public CdRingGlobals read() { checkActive(Phase.DYNAMIC_SLOTS); return ringGlobals; }
                @Override public void write(CdRingGlobals value) {
                    checkActive(Phase.DYNAMIC_SLOTS);
                    // Ring writes rewards only; all other three fields are pass-through.
                    if (value.timeStopTimer() != ringGlobals.timeStopTimer()
                            || value.ringAnimationTimer() != ringGlobals.ringAnimationTimer()
                            || value.ringAnimationFrame() != ringGlobals.ringAnimationFrame()) {
                        throw new IllegalStateException("Ring reward attempted to write another global owner");
                    }
                    ringGlobals = value;
                }
            };
            var calls = new CdRing.SourceCalls() {
                @Override public void playFmSound(CdRing.FmSound sound) { appendSound(new RingFm(sound)); }
                @Override public void subCpuCommand(CdRing.SubCpuCommand command) { appendSound(new RingSubCpu(command)); }
                @Override public void drawObject(CdObjectPool pool, int wholeIndex) { drawDynamic(pool, wholeIndex); }
            };
            for (int whole = CdObjectPool.FIRST_WHOLE_INDEX; whole < CdObjectPool.END_WHOLE_INDEX; whole++) {
                checkActive(Phase.DYNAMIC_SLOTS);
                dispatchWholeIndex = whole;
                int id = placement.pool().state(whole).id(); // Fetch at the real visit, never from a captured list.
                if (id == 0) { continue; }
                var environment = frame.environment();
                var gates = environment.movement().gates();
                if (id == 0x10) {
                    ring.executeSelected(whole, new CdRing.Inputs(assets.identity().variant(), assets.identity().stage(),
                            gates.zoneAct() >>> 8, preScrollForeground.xWord(), gates.timeZone(), gates.timeWarpDirection()),
                            globalsPort, calls);
                } else if (id == CdMosqui.OBJECT_ID) {
                    var player = frame.state().motion();
                    // goodFuture=0 is the named DEV world's explicit missing-global policy,
                    // not a caller-declared media certificate. All represented fields remain live.
                    var result = mosqui.run(whole, new CdMosqui.Globals(0, gates.timeZone(), gates.timeWarpDirection(),
                            environment.globals().debugMode(), preScrollForeground.xWord(),
                            player.centreX16_16() >>> 16, player.centreY16_16() >>> 16));
                    for (var effect : result.effects()) {
                        if (effect instanceof CdMosqui.GlobalWrite write) {
                            if (write.field() != CdMosqui.GlobalField.PRIMARY_ANGLE) {
                                throw new UnsupportedOperationException("Unowned Mosqui shared global " + write.field());
                            }
                            primaryAngleWrite.write(write.value());
                            checkActive(Phase.DYNAMIC_SLOTS);
                        } else if (effect instanceof CdMosqui.SoundRequest request) {
                            appendSound(new MosquiFm(request.sound()));
                        }
                        // CallReturned/Despawn describe already applied work; never replay them.
                    }
                } else {
                    throw new UnsupportedOperationException("Unowned CD object ID $"
                            + Integer.toHexString(id) + " at live physical slot " + whole);
                }
            }
            dispatchWholeIndex = -1;
            return null;
        });
    }

    /** Root supplies actual post-scroll FG/BG/BG3 words; no global camera or position fence. */
    public CpuSat finishAfterCamera(DrawCameras cameras) {
        return perform(Phase.DYNAMIC_DONE, Phase.BUILDING, Phase.BUILT,
                () -> queue.finishAfterCamera(Objects.requireNonNull(cameras, "actual post-scroll cameras")));
    }

    /** UpdateGlobalAnims.Rings (Main.asm:520-525), exactly once after build, including time stop. */
    public void finishOrdinaryTail() {
        perform(Phase.BUILT, Phase.TAIL, Phase.CLOSED_CUT, () -> {
            ringGlobals = ringGlobals.advanceGlobalRingAnimation();
            preScrollForeground = null; // Camera/scroll is root-owned, and is never restored here.
            return null;
        });
    }

    /** Immutable CPU diagnostics at a completed build/cut; not a second publication owner. */
    public CpuSat cpuSat() {
        requireReadable();
        try { return queue.cpuSat(); }
        catch (RuntimeException | Error failure) { failIdle(failure); throw failure; }
    }
    public SpritePresentation.Frame displayedFrame() {
        requireReadable();
        return displayed;
    }

    public Snapshot captureClosedCut() {
        requireCut();
        return new Snapshot(captureOwner, placement.capture(), ringGlobals, queue.captureClosedCut(), displayed, sounds);
    }

    /** Pure partition preflight. A foreign snapshot rejects before any owned partition is changed. */
    public void validateRestore(Snapshot snapshot) {
        requireCut();
        Objects.requireNonNull(snapshot, "opening runtime snapshot");
        if (snapshot.owner != captureOwner) { throw new IllegalArgumentException("Foreign opening runtime/source owner capture"); }
        placement.validateRestore(snapshot.placement);
        queue.validateRestore(snapshot.queue);
        Objects.requireNonNull(snapshot.ringGlobals, "captured Ring globals");
        Objects.requireNonNull(snapshot.displayedFrame, "captured immutable picture");
        Objects.requireNonNull(snapshot.sounds, "captured immutable sound requests");
    }

    /** Prevalidates ALL owned partitions; no service/body/queue draw/pixels/sound/initialization is replayed. */
    public void restore(Snapshot snapshot) {
        // Keep preflight outside operating: it uses the same public cut guard as root's
        // composite preflight. Rejected foreign data cannot partially restore placement.
        try { validateRestore(snapshot); }
        catch (RuntimeException | Error failure) { failIdle(failure); throw failure; }
        perform(Phase.CLOSED_CUT, Phase.CLOSED_CUT, Phase.CLOSED_CUT, () -> {
            placement.restore(snapshot.placement);
            queue.restore(snapshot.queue);
            ringGlobals = snapshot.ringGlobals;
            displayed = snapshot.displayedFrame;
            sounds = snapshot.sounds;
            expectedPlayerCut = null;
            preScrollForeground = null;
            cameraPrepared = false; // Root must supply its separately restored scroll cut for the next cycle.
            return null;
        });
    }

    /** No native PLC preparation is supplied by this bounded composition. Ring tail has its own explicit call. */
    @Override public boolean hasPreparationBoundary(PlcLifecyclePhase servicePhase) {
        requireReadable();
        try { requireSupportedService(servicePhase); return frame.hasPreparationBoundary(servicePhase); }
        catch (RuntimeException | Error failure) { failIdle(failure); throw failure; }
    }
    @Override public void prepareAfterLoop(PlcLifecyclePhase servicePhase) {
        requireReadable();
        try { requireSupportedService(servicePhase); frame.prepareAfterLoop(servicePhase); }
        catch (RuntimeException | Error failure) { failIdle(failure); throw failure; }
    }

    private void drawDynamic(CdObjectPool pool, int whole) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || pool != placement.pool() || whole != dispatchWholeIndex) {
            throw new IllegalStateException("DrawObject must synchronously use the current bound physical dispatch");
        }
        inDrawCallback = true;
        try {
            var live = pool.state(whole); // NO ID check: Mosqui Wait may have just deleted this slot.
            queue.drawObject(new DrawCallCut(new CdSlotRef(whole), live.spriteFlags(), live.centreX(), live.centreY(),
                    live.unsignedByte(Y_RADIUS), live.unsignedByte(WIDTH), live.unsignedByte(PRIORITY)), preScrollForeground);
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inDrawCallback = false; }
    }

    private LiveDrawSlot readLiveSlot(CdSlotRef ref) {
        checkActive(Phase.BUILDING);
        if (ref.wholeIndex() == 0) {
            var state = frame.state();
            var motion = state.motion();
            var header = state.header();
            // SCREEN_Y is the full WORD at $0A, aliasing X fraction, not the world Y word.
            return new LiveDrawSlot(header.slotId(), motion.spriteFlags(), motion.centreX16_16() >>> 16,
                    motion.centreY16_16() >>> 16, motion.centreX16_16() & 0xFFFF,
                    header.mappingAddress(), state.animation().mapFrame(), header.tile());
        }
        requireDynamicRef(ref);
        var state = placement.pool().state(ref.wholeIndex());
        return new LiveDrawSlot(state.id(), state.spriteFlags(), state.centreX(), state.centreY(),
                state.word(X_FRACTION), state.longBits(MAPPINGS), state.unsignedByte(MAP_FRAME), state.word(TILE));
    }

    private void writeLiveOnScreenBit(CdSlotRef ref, boolean value) {
        if (!operating || (phase != Phase.PLAYER_ENQUEUE && phase != Phase.DYNAMIC_SLOTS && phase != Phase.BUILDING)) {
            throw new IllegalStateException("Native bit7 write outside its source queue call");
        }
        if (ref.wholeIndex() == 0) {
            frame.publishDrawObjectOnScreen(value);
        } else {
            requireDynamicRef(ref);
            var slot = placement.pool().state(ref.wholeIndex());
            placement.pool().write(slot.moveByte(SPRITE_FLAGS, (slot.spriteFlags() & 0x7F) | (value ? 0x80 : 0)));
        }
    }

    private static void requireDynamicRef(CdSlotRef ref) {
        if (ref.wholeIndex() < CdObjectPool.FIRST_WHOLE_INDEX) {
            throw new IllegalArgumentException("Unowned reserved opening slot " + ref.wholeIndex());
        }
    }
    private void appendSound(SoundRequest request) {
        if (!operating || (phase != Phase.PLAYER_BODY && phase != Phase.DYNAMIC_SLOTS)) {
            throw new IllegalStateException("Sound request outside its completed source owner");
        }
        var next = new ArrayList<>(sounds);
        next.add(Objects.requireNonNull(request));
        sounds = List.copyOf(next);
    }

    private <T> T perform(Phase expected, Phase active, Phase completed, Supplier<T> action) {
        requireReadable();
        if (phase != expected) {
            var failure = new IllegalStateException("CD opening cycle requires " + expected + ", was " + phase);
            failIdle(failure);
            throw failure;
        }
        operating = true;
        phase = active;
        Throwable failed = null;
        try {
            T value = action.get();
            checkActive(active);
            phase = completed;
            return value;
        } catch (RuntimeException | Error failure) {
            failed = failure;
            phase = Phase.FAILED;
            throw failure;
        } finally {
            operating = false;
            if (phase == Phase.FAILED) { closeOwners(failed); }
        }
    }

    private void requireReadable() {
        if (operating) {
            phase = Phase.FAILED;
            throw new IllegalStateException("Reentrant CD opening operation closes admission");
        }
        if (closed()) {
            var failure = new IllegalStateException("CD opening runtime is terminally closed");
            if (phase != Phase.CLOSED) { failIdle(failure); }
            throw failure;
        }
    }
    private void checkActive(Phase expected) {
        if (!operating || phase != expected || frame.closed()) {
            throw new IllegalStateException("CD opening source operation failed or was reentered at " + expected);
        }
    }
    private void requireCut() {
        requireReadable();
        try {
            if (phase != Phase.CLOSED_CUT) { throw new IllegalStateException("Capture/restore requires a completed CD opening tail or held service"); }
            if (queue.phase() != CdObjectDrawQueue.Phase.CLOSED_CUT) { throw new IllegalStateException("CD opening queue is not drained"); }
            frame.captureShared(); // Guard pending input/body entitlement without storing that partition.
        } catch (RuntimeException | Error failure) { failIdle(failure); throw failure; }
    }
    private static void requireSupportedService(PlcLifecyclePhase phase) {
        if (phase != PlcLifecyclePhase.ORDINARY_LEVEL && phase != PlcLifecyclePhase.NORMAL_PAUSE && phase != PlcLifecyclePhase.LAG) {
            throw new UnsupportedOperationException("Unowned opening V-int loop " + phase);
        }
    }
    private void failIdle(Throwable failure) {
        phase = Phase.FAILED;
        if (!operating) { closeOwners(failure); }
    }

    /** Cleanup happens once AFTER the active callback/Frame operation unwinds. Never mask its failure. */
    private void closeOwners(Throwable failure) {
        if (cleaned) { return; }
        cleaned = true;
        Throwable cleanupFailure = failure;
        try { queue.close(); }
        catch (RuntimeException | Error cleanup) {
            if (cleanupFailure == null) { cleanupFailure = cleanup; }
            else if (cleanup != cleanupFailure) { cleanupFailure.addSuppressed(cleanup); }
        }
        try { frame.close(); }
        catch (RuntimeException | Error cleanup) {
            if (cleanupFailure == null) { cleanupFailure = cleanup; }
            else if (cleanup != cleanupFailure) { cleanupFailure.addSuppressed(cleanup); }
        }
        if (failure == null && cleanupFailure != null) {
            phase = Phase.FAILED;
            if (cleanupFailure instanceof Error error) { throw error; }
            throw (RuntimeException) cleanupFailure;
        }
    }

    @Override public void close() {
        if (operating) {
            phase = Phase.FAILED;
            throw new IllegalStateException("Cannot close CD opening runtime inside a source callback");
        }
        if (phase != Phase.FAILED) { phase = Phase.CLOSED; }
        closeOwners(null);
    }
}
