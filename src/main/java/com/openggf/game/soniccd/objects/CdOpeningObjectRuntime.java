package com.openggf.game.soniccd.objects;

import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.graphics.SpritePresentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
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
 * <p>Explicit DEV policy: USA R11A Present, goodFuture=0, projDestroyed=0, absent
 * reserved owners 1..31, initially zero CPU SAT/empty picture, and preloaded actual
 * Ring/Mosqui/3D-plant/3D-ramp-booster/explosion/points/flower art (the inert Animal path draws nothing).
 * The initial Ring, contact, score and level globals are caller-declared DEV ingress, never an entry proof.
 * Live dynamic IDs $10 Ring, $11 LostRing, $3D Mosqui, $24 Animal, $2C 3D plant and $2A 3D ramp
 * and aftermath IDs $18/$1C/$1F are owned; every other ID (including $12 FloatBlock) fails at its live dispatch.
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
     * Root reserves/binds its eight disjoint subdivisions; this value neither
     * claims graphics ownership nor recommends the arbitrary IDs used by tests.
     * The three-argument form derives the sequential frontier spans the production
     * module uses: plant = mosqui + 70 and ramp = plant + 28.
     * The contact spans append 70 + 5 + 39 tiles, for 313 patterns in all.
     */
    public record LogicalBanks(int player, int ring, int mosqui, int plant, int ramp,
                               int explosion, int points, int flower) {
        public LogicalBanks(int player, int ring, int mosqui) {
            this(player, ring, mosqui, mosqui + 70, mosqui + 70 + 28);
        }
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp) {
            this(player, ring, mosqui, plant, ramp, ramp + 63, ramp + 63 + 70, ramp + 63 + 70 + 5);
        }
    }

    /**
     * Explicit DEV globals this composition owns because no native writer is admitted:
     * goodFuture (Object Time Check.asm:12-44, Animal/Main.asm via CheckAnimalPrescence)
     * and projDestroyed (_Include/Main CPU Variables.inc:122). Both are byte globals the
     * bodies read every dispatch; they are captured and restored with this partition.
     */
    public record DevGlobals(int goodFuture, int projDestroyed) {
        public DevGlobals {
            if ((goodFuture & ~0xFF) != 0 || (projDestroyed & ~0xFF) != 0) {
                throw new IllegalArgumentException("DEV globals are unsigned native bytes");
            }
        }
    }

    /** Native longs plus one HUD byte, owned only here; actor/Frame captures never duplicate them. */
    public record ScoreGlobals(int score, int nextLifeScore, int updateHudScore) {
        public ScoreGlobals {
            if ((updateHudScore & ~0xFF) != 0) throw new IllegalArgumentException("HUD score flag requires a byte");
        }
    }

    public enum UnsupportedContactBoundary { LEVEL_RESTART, LOST_RING_NATIVE_CLOCK }
    public static final class UnsupportedContact extends UnsupportedOperationException {
        private final UnsupportedContactBoundary boundary;
        private UnsupportedContact(UnsupportedContactBoundary boundary) {
            super(boundary + (boundary == UnsupportedContactBoundary.LEVEL_RESTART
                    ? ": native level reconstruction is not owned yet"
                    : ": LostRing binding requires an explicitly represented absolute native V-int counter"));
            this.boundary = boundary;
        }
        public UnsupportedContactBoundary boundary() { return boundary; }
    }

    /**
     * Required narrow port to the EXISTING shared primaryAngle byte, not oAngle.
     * Mosqui returns this write without applying it. Root must write that byte
     * exactly once, without restoring a Frame capture or running another owner.
     * Invocation is in returned-effect order, before the next physical dispatch.
     */
    @FunctionalInterface public interface PrimaryAngleWrite { void write(int unsignedByte); }

    /** Ordered requests only, no playback and no claim to the omitted service/music ledger. */
    public sealed interface SoundRequest permits PlayerSound, RingFm, RingSubCpu, MosquiFm, LostRingFm { }
    public record PlayerSound(CdPlayerFrameRuntime.Sound sound) implements SoundRequest {
        public PlayerSound { Objects.requireNonNull(sound, "player sound"); }
    }
    public record RingFm(CdRing.FmSound sound) implements SoundRequest {
        public RingFm { Objects.requireNonNull(sound, "Ring FM sound"); }
    }
    public record RingSubCpu(CdRing.SubCpuCommand command) implements SoundRequest {
        public RingSubCpu { Objects.requireNonNull(command, "Ring sub-CPU command"); }
    }
    public record LostRingFm(CdLostRing.Sound sound) implements SoundRequest {
        public LostRingFm { Objects.requireNonNull(sound, "LostRing FM sound"); }
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
        private final CdRingSpillGlobals spillGlobals;
        private final CdObjectDrawQueue.Snapshot queue;
        private final SpritePresentation.Frame displayedFrame;
        private final List<SoundRequest> sounds;
        private final DevGlobals devGlobals;
        private final CdPlayerBody.ContactGlobals contactGlobals;
        private final ScoreGlobals scoreGlobals;
        private final CdLevelGlobals levelGlobals;

        private Snapshot(Object owner, CdObjectPlacementRuntime.Snapshot placement, CdRingGlobals ringGlobals,
                         CdRingSpillGlobals spillGlobals,
                         CdObjectDrawQueue.Snapshot queue, SpritePresentation.Frame displayedFrame,
                         List<SoundRequest> sounds, DevGlobals devGlobals,
                         CdPlayerBody.ContactGlobals contactGlobals, ScoreGlobals scoreGlobals,
                         CdLevelGlobals levelGlobals) {
            this.owner = owner;
            this.placement = placement;
            this.ringGlobals = ringGlobals;
            this.spillGlobals = spillGlobals;
            this.queue = queue;
            this.displayedFrame = displayedFrame;
            this.sounds = List.copyOf(sounds);
            this.devGlobals = devGlobals;
            this.contactGlobals = contactGlobals;
            this.scoreGlobals = scoreGlobals;
            this.levelGlobals = levelGlobals;
        }
        public CdObjectPlacementRuntime.Snapshot placement() { return placement; }
        public CdRingGlobals ringGlobals() { return ringGlobals; }
        public CdRingSpillGlobals spillGlobals() { return spillGlobals; }
        public DevGlobals devGlobals() { return devGlobals; }
        public CdPlayerBody.ContactGlobals contactGlobals() { return contactGlobals; }
        public ScoreGlobals scoreGlobals() { return scoreGlobals; }
        public CdLevelGlobals levelGlobals() { return levelGlobals; }
        public SpritePresentation.Frame displayedFrame() { return displayedFrame; }
        public List<SoundRequest> lastSoundRequests() { return sounds; }
    }

    private final Object captureOwner = new Object();
    private final CdR11aAssets assets;
    private final CdPlayerFrameRuntime frame;
    private final CdR11aObjectAssets objects;
    private final CdObjectPlacementRuntime placement;
    private final CdPlayerObjectTouch scanner;
    private final CdPlayerTouchResponse touchResponse;
    private final CdRing ring;
    private final CdMosqui mosqui;
    private final CdAnimal animal;
    private final Cd3dPlant plant;
    private final Cd3dRamp ramp;
    private final CdExplosion explosion;
    private final CdPoints points;
    private final CdFlower flower;
    private final CdLostRing lostRing;
    private final CdObjectDrawQueue queue;
    private final LogicalBanks banks;
    private final PrimaryAngleWrite primaryAngleWrite;
    private final CdR11aObjectAssets.FrontierArtBank frontierArt;
    private final CdR11aObjectAssets.ContactArtBank contactArt;
    private CdRingGlobals ringGlobals;
    // Level/Main.asm:178-184 ClearFlowers covers the three ringLoss animation values.
    private CdRingSpillGlobals spillGlobals = new CdRingSpillGlobals(0, 0, 0);
    private DevGlobals devGlobals = new DevGlobals(0, 0);
    private CdPlayerBody.ContactGlobals contactGlobals;
    private ScoreGlobals scoreGlobals;
    private CdLevelGlobals levelGlobals;
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
     * Preload describes the pixel source only; root owns actual graphics banks.
     * Level globals are caller-declared post-LevelStart values with no default.</p>
     */
    public static CdOpeningObjectRuntime usaR11aOpeningDevEmptySatPreloadedArt(
            CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
            CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore, CdLevelGlobals initialLevel,
            byte[] initialCpuSatBytes, LogicalBanks banks, CameraWords preScrollForeground,
            PrimaryAngleWrite primaryAngleWrite) throws IOException {
        Objects.requireNonNull(assets, "actual R11A assets");
        Objects.requireNonNull(frame, "external-touch player Frame");
        Objects.requireNonNull(initialGlobals, "explicit initial Ring globals");
        Objects.requireNonNull(initialContact, "explicit initial contact bytes");
        Objects.requireNonNull(initialScore, "explicit initial score globals");
        Objects.requireNonNull(initialLevel, "explicit initial level globals");
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
        // The frontier art decodes once here (plant/booster pixels); an IO failure stops before construction.
        var frontier = objects.frontierArtBank();
        var contact = objects.contactArtBank();
        var placement = CdObjectPlacementRuntime.fromAssets(assets);
        var mappings = CdObjectMappings.fromOpeningAssets(assets, objects);
        // Validate physical/logical bank extents and overlaps now, even for an empty picture.
        CdObjectPresentation.usaR11aContactDevPreloadedArt(frame.artState(), objects, frontier, contact,
                banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(),
                banks.explosion(), banks.points(), banks.flower());
        return new CdOpeningObjectRuntime(assets, frame, initialGlobals, initialContact, initialScore, initialLevel, initial,
                banks, preScrollForeground, primaryAngleWrite, objects, frontier, contact, placement, mappings);
    }

    private CdOpeningObjectRuntime(CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
                                   CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore,
                                   CdLevelGlobals initialLevel, byte[] initial, LogicalBanks banks, CameraWords camera,
                                   PrimaryAngleWrite primaryAngleWrite, CdR11aObjectAssets objects,
                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                   CdR11aObjectAssets.ContactArtBank contact,
                                   CdObjectPlacementRuntime placement, CdObjectMappings mappings) {
        this.assets = assets;
        this.frame = frame;
        this.objects = objects;
        this.frontierArt = frontier;
        this.contactArt = contact;
        this.placement = placement;
        this.banks = banks;
        this.primaryAngleWrite = primaryAngleWrite;
        ringGlobals = initialGlobals;
        contactGlobals = initialContact;
        scoreGlobals = initialScore;
        levelGlobals = initialLevel;
        preScrollForeground = camera;
        cameraPrepared = true;
        scanner = CdPlayerObjectTouch.fromAssets(objects);
        touchResponse = CdPlayerTouchResponse.fromAssets(objects);
        ring = CdRing.fromAssets(placement, objects);
        queue = new CdObjectDrawQueue(new CdDrawSlots() {
            @Override public LiveDrawSlot read(CdSlotRef ref) { return readLiveSlot(ref); }
            @Override public void writeOnScreenBit(CdSlotRef ref, boolean value) { writeLiveOnScreenBit(ref, value); }
        }, mappings, initial);
        mosqui = new CdMosqui(placement, CdMosqui.Assets.fromObjectAssets(objects),
                new CdPlayerTerrain(assets.terrain()), (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        // Frontier bodies bind the same live placement/pool, the actual asset owner and the one shared
        // DrawObject adapter. The Animal's admitted path reads no assets and never draws.
        animal = new CdAnimal(placement);
        plant = new Cd3dPlant(placement, Cd3dPlant.Assets.fromObjectAssets(objects),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        ramp = new Cd3dRamp(placement, Cd3dRamp.Assets.fromObjectAssets(objects),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        explosion = new CdExplosion(placement, CdExplosion.Assets.fromObjectAssets(objects),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        points = new CdPoints(placement, CdPoints.Assets.fromObjectAssets(objects),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        flower = new CdFlower(placement, CdFlower.Assets.fromObjectAssets(objects),
                new CdPlayerTerrain(assets.terrain()), (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        lostRing = new CdLostRing(placement, CdLostRing.Assets.fromObjectAssets(objects),
                new CdPlayerMath(assets.player()), new CdPlayerTerrain(assets.terrain()),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()), CdRing::collectRing);
    }

    public Phase phase() { return phase; }
    public boolean closed() { return phase == Phase.FAILED || phase == Phase.CLOSED || frame.closed(); }
    /** Live placement authority; exposed for root's explicit source owners, not a second dispatcher. */
    public CdObjectPlacementRuntime placement() { return placement; }
    /** Immutable diagnostic state; no reset, HUD acknowledgement or time-stop writer is invented. */
    public CdRingGlobals ringGlobals() { return ringGlobals; }
    public CdRingSpillGlobals spillGlobals() { return spillGlobals; }
    /** Explicit owned DEV globals read by the bodies; no native writer (projector, good-future) is admitted. */
    public DevGlobals devGlobals() { return devGlobals; }
    public CdPlayerBody.ContactGlobals contactGlobals() { return contactGlobals; }
    public ScoreGlobals scoreGlobals() { return scoreGlobals; }
    /** The one level-flow store; Body summaries commit here without retaining their call context. */
    public CdLevelGlobals levelGlobals() { return levelGlobals; }
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
                var pixels = CdObjectPresentation.usaR11aContactDevPreloadedArt(frame.artState(), objects,
                        frontierArt, contactArt, banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(),
                        banks.explosion(), banks.points(), banks.flower());
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
     * Detached touch commit ONLY after the complete Frame.runBody returns.
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
            var callCut = placement.pool().capture();
            var context = new CdPlayerBody.ResponseContext(touchResponse, contactGlobals,
                    new CdPlayerTouchResponse.WorldGlobals(ringGlobals.rings(), ringGlobals.lives(),
                            ringGlobals.updateHudLives(), scoreGlobals.score(), scoreGlobals.nextLifeScore(),
                            scoreGlobals.updateHudScore()));
            var level = new CdPlayerBody.LevelContext(callCut, ringGlobals, levelGlobals);
            var publication = frame.runBody(levelFrames,
                    Optional.of(new CdPlayerBody.TouchContext(scanner, callCut, context)), Optional.of(level));
            checkActive(Phase.PLAYER_BODY);
            boolean displaySeen = false;
            boolean touchSeen = false;
            boolean levelSeen = false;
            for (var effect : publication.result().effects()) {
                if (effect instanceof CdPlayerBody.DeadCheckGone gone) {
                    if (levelSeen) throw new IllegalStateException("Body published more than one level summary");
                    levelSeen = true;
                    commitDeadGone(callCut, gone, publication.result().effects());
                } else if (effect instanceof CdPlayerBody.RestartCut restart) {
                    if (levelSeen) throw new IllegalStateException("Body published more than one level summary");
                    levelSeen = true;
                    commitRestart(restart, publication.result().effects());
                } else if (effect instanceof CdPlayerBody.DisplayEligibility cut) {
                    if (displaySeen) { throw new IllegalStateException("Body published more than one Display cut"); }
                    displaySeen = true;
                    if (cut.eligible()) { expectedPlayerCut = cut; }
                } else if (effect instanceof CdPlayerBody.ObjectTouchResult touch) {
                    if (touchSeen) { throw new IllegalStateException("Body published more than one touch result"); }
                    touchSeen = true;
                    touch.result().changedSlot().ifPresent(image -> commitTouchImages(callCut, List.of(image)));
                } else if (effect instanceof CdPlayerBody.ObjectTouchResponse touch) {
                    if (touchSeen) { throw new IllegalStateException("Body published more than one touch result"); }
                    touchSeen = true;
                    var result = touch.result();
                    var world = result.world();
                    // Construct and validate EVERY new immutable value before the first pool write.
                    var nextRing = new CdRingGlobals(world.rings(), world.lives(), ringGlobals.livesFlags(),
                            ringGlobals.updateHudRings(), world.updateHudLives(), ringGlobals.timeStopTimer(),
                            ringGlobals.ringAnimationTimer(), ringGlobals.ringAnimationFrame());
                    var nextScore = new ScoreGlobals(world.score(), world.nextLifeScore(), world.updateHudScore());
                    var nextContact = touch.contact();
                    commitTouchImages(callCut, result.changedSlots());
                    ringGlobals = nextRing; scoreGlobals = nextScore; contactGlobals = nextContact;
                    // The final actor/invincible/scoreChain values already belong to Frame. Never replay them here.
                }
            }
            for (var sound : publication.sounds()) { appendSound(new PlayerSound(sound)); }
            return publication;
        });
    }

    /** DeadChkGone's final world values commit once after the complete pure Body succeeds. */
    private void commitDeadGone(CdObjectPool.Snapshot cut, CdPlayerBody.DeadCheckGone gone,
                                List<CdPlayerBody.Effect> effects) {
        var pool = placement.pool(); pool.validateRestore(cut);
        if (!pool.capture().slots().equals(cut.slots())) {
            throw new IllegalStateException("CD pool changed after the death call cut");
        }
        var nextRing = new CdRingGlobals(ringGlobals.rings(), gone.lives(), ringGlobals.livesFlags(),
                ringGlobals.updateHudRings(), gone.updateHudLives(), ringGlobals.timeStopTimer(),
                ringGlobals.ringAnimationTimer(), ringGlobals.ringAnimationFrame());
        var old = levelGlobals;
        var nextLevel = new CdLevelGlobals(old.levelRestart(), gone.updateHudTime(), old.checkpoint(),
                old.spawnMode(), old.plcLoadFlags(), old.flowerCount(), old.fadeEnableDisplay(), old.powerup(), old.timeOver());
        OptionalInt found = gone.allocation().map(CdPlayerTouchResponse.FindObjSlot::wholeIndex).orElse(OptionalInt.empty());
        if (gone.allocation().isPresent()) {
            OptionalInt expected = OptionalInt.empty();
            for (var slot : cut.slots()) {
                if (slot.id() == 0) { expected = OptionalInt.of(slot.identity().wholeIndex()); break; }
            }
            if (!expected.equals(found)) throw new IllegalStateException("Death allocation differs from native first-free slot");
        }
        var aliases = effects.stream().filter(CdPlayerBody.UncheckedAliasWrite.class::isInstance)
                .map(CdPlayerBody.UncheckedAliasWrite.class::cast).toList();
        boolean unchecked = gone.allocation().isPresent() && found.isEmpty();
        var expectedAliases = unchecked ? List.of(new CdPlayerBody.UncheckedAliasWrite(0xFFF000, 0x3B)) : List.of();
        if (!aliases.equals(expectedAliases)) throw new IllegalStateException("Death alias does not match the exhausted slot search");
        gone.gameOverImage().ifPresent(image -> {
            var expected = cut.slots().get(image.identity().dynamicIndex()).moveByte(ID, CdGameOver.OBJECT_ID);
            if (!image.equals(expected)) throw new IllegalStateException("Death GameOver image changed bytes other than oID");
            pool.validateWrite(image);
        });
        // All immutable values, ownership, allocation and alias checks precede the first write.
        gone.gameOverImage().ifPresent(pool::write);
        ringGlobals = nextRing; levelGlobals = nextLevel;
        // UncheckedAliasWrite stays typed Body metadata; no pool slot or host RAM buffer represents $FFF000.
    }

    /** Restart's long clear includes the three independent count bytes and adjacent fade byte. */
    private void commitRestart(CdPlayerBody.RestartCut restart, List<CdPlayerBody.Effect> effects) {
        long resets = effects.stream().filter(CdPlayerBody.ResetSavedObjectFlagsRequest.class::isInstance).count();
        var next = restart.next();
        if (!restart.resetSavedObjectFlags() || resets != 1 || next.levelRestart() != 1
                || next.flowerCount() != 0 || next.fadeEnableDisplay() != 0) {
            throw new IllegalStateException("Restart summary lacks its native saved-flags/count/fade cut");
        }
        // The record already checked every native width; no callbacks or later validation follow this reset.
        placement.resetSavedObjectFlags();
        levelGlobals = next;
    }

    /** Same call-cut/owner preflight for all images precedes every commit; no callbacks can observe a partial patch. */
    private void commitTouchImages(CdObjectPool.Snapshot cut, List<CdObjectState> changed) {
        var pool = placement.pool();
        pool.validateRestore(cut);
        for (var image : changed) {
            pool.validateWrite(image);
            var expected = cut.slots().get(image.identity().dynamicIndex());
            if (!pool.state(image.identity().wholeIndex()).equals(expected)) {
                throw new IllegalStateException("CD touch slot changed after its call-cut at " + image.identity().wholeIndex());
            }
        }
        for (var image : changed) pool.write(image);
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
                    // Ring rewards/LostRing Init own these fields; the other three globals pass through.
                    if (value.timeStopTimer() != ringGlobals.timeStopTimer()
                            || value.ringAnimationTimer() != ringGlobals.ringAnimationTimer()
                            || value.ringAnimationFrame() != ringGlobals.ringAnimationFrame()) {
                        throw new IllegalStateException("Ring reward attempted to write another global owner");
                    }
                    ringGlobals = value;
                }
            };
            var spillPort = new CdLostRing.SpillGlobalsPort() {
                @Override public CdRingSpillGlobals read() { checkActive(Phase.DYNAMIC_SLOTS); return spillGlobals; }
                @Override public void write(CdRingSpillGlobals value) {
                    checkActive(Phase.DYNAMIC_SLOTS);
                    spillGlobals = Objects.requireNonNull(value, "LostRing animation state");
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
                } else if (id == CdLostRing.OBJECT_ID) {
                    // Binding requires an absolute clock, never engine frames or an unknown-seed delta.
                    int nativeVInt = (int) frame.vIntClock().absoluteValue().orElseThrow(() ->
                            new UnsupportedContact(UnsupportedContactBoundary.LOST_RING_NATIVE_CLOCK));
                    var result = lostRing.run(whole, new CdLostRing.Inputs(nativeVInt, 127 - whole,
                            gates.zoneAct() >>> 8, environment.movement().bounds().bottom()),
                            new CdLostRing.Ports(globalsPort, spillPort));
                    for (var effect : result.effects()) {
                        if (effect instanceof CdLostRing.GlobalWrite write) {
                            if (write.field() != CdLostRing.GlobalField.PRIMARY_ANGLE) {
                                throw new UnsupportedOperationException("Unowned LostRing shared global " + write.field());
                            }
                            primaryAngleWrite.write(write.value());
                            checkActive(Phase.DYNAMIC_SLOTS);
                        } else if (effect instanceof CdLostRing.SoundRequest request) {
                            appendSound(new LostRingFm(request.sound()));
                        } else if (effect instanceof CdLostRing.RewardFmRequest request) {
                            appendSound(new RingFm(request.sound()));
                        } else if (effect instanceof CdLostRing.RewardSubCpuRequest request) {
                            appendSound(new RingSubCpu(request.command()));
                        }
                        // Allocation/delete/call metadata describes committed work; never replay it.
                    }
                } else if (id == CdMosqui.OBJECT_ID) {
                    var player = frame.state().motion();
                    // goodFuture=0 is the named DEV world's explicit missing-global policy,
                    // not a caller-declared media certificate. All represented fields remain live.
                    var result = mosqui.run(whole, new CdMosqui.Globals(devGlobals.goodFuture(), gates.timeZone(),
                            gates.timeWarpDirection(), environment.globals().debugMode(), preScrollForeground.xWord(),
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
                } else if (id == CdExplosion.OBJECT_ID) {
                    explosion.run(whole); // Writes and first-free Points allocation already committed in source order.
                } else if (id == CdPoints.OBJECT_ID) {
                    points.run(whole); // The unconditional DrawObject cut also follows a full delete.
                } else if (id == CdFlower.OBJECT_ID) {
                    var result = flower.run(whole);
                    for (var effect : result.effects()) {
                        if (effect instanceof CdFlower.GlobalWrite write) {
                            if (write.field() != CdFlower.GlobalField.PRIMARY_ANGLE) {
                                throw new UnsupportedOperationException("Unowned Flower shared global " + write.field());
                            }
                            primaryAngleWrite.write(write.value());
                            checkActive(Phase.DYNAMIC_SLOTS);
                        }
                    }
                } else if (id == CdAnimal.OBJECT_ID) {
                    // Present/projector-intact path: CheckAnimalPrescence pops into CheckObjDespawn; no draw.
                    animal.run(whole, new CdAnimal.Globals(gates.timeZone(), devGlobals.goodFuture(),
                            devGlobals.projDestroyed(), gates.timeWarpDirection(), preScrollForeground.xWord()));
                } else if (id == Cd3dPlant.OBJECT_ID) {
                    // Completed player slot read at this physical dispatch: oX word and oPlayerCtrl (oVar2C).
                    var player = frame.state().motion();
                    plant.run(whole, new Cd3dPlant.Player(player.centreX16_16() >>> 16, player.playerControl()),
                            new Cd3dPlant.Globals(gates.timeZone(), gates.timeWarpDirection(), preScrollForeground.xWord()));
                    // ChildCreated/CallReturned/Despawn describe committed pool work; the live walk reaches
                    // later-slot children in this pass by reading their IDs at their own visit.
                } else if (id == Cd3dRamp.OBJECT_ID) {
                    var player = frame.state().motion();
                    var result = ramp.run(whole, new Cd3dRamp.Player(player.centreX16_16() >>> 16,
                            player.centreY16_16() >>> 16, player.yVelocity() & 0xFFFF, player.flags(),
                            player.animation(), player.playerControl()),
                            new Cd3dRamp.Globals(gates.timeZone(), gates.timeWarpDirection(), preScrollForeground.xWord()));
                    for (var effect : result.effects()) {
                        if (effect instanceof Cd3dRamp.PlayerWrite write) {
                            if (write.field() != Cd3dRamp.PlayerField.Y_VELOCITY) {
                                throw new UnsupportedOperationException("Unowned 3D ramp player field " + write.field());
                            }
                            // MOVE.W #+-$C00,oYVel(a6): applied exactly once after the body returned and before
                            // the next physical dispatch, whether or not that booster despawned in this call.
                            frame.applyObjectYVelocity(write.signedWord());
                            checkActive(Phase.DYNAMIC_SLOTS);
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

    /**
     * Level_MainLoop's post-RunObjects, pre-scroll restart branch (Level/Main.asm:405-408).
     * No ordinary camera, sprite construction or tail may run after this cut. Actual level
     * reconstruction remains unowned; until then a restart closes this composition explicitly.
     * The dead/debug/routine camera choice belongs to the World after this admission check.
     */
    public void beforeCameraStep() {
        perform(Phase.DYNAMIC_DONE, Phase.DYNAMIC_DONE, Phase.DYNAMIC_DONE, () -> {
            if (levelGlobals.levelRestart() != 0) {
                throw new UnsupportedContact(UnsupportedContactBoundary.LEVEL_RESTART);
            }
            return null;
        });
    }

    /** Root supplies actual post-scroll FG/BG/BG3 words; no global camera or position fence. */
    public CpuSat finishAfterCamera(DrawCameras cameras) {
        return perform(Phase.DYNAMIC_DONE, Phase.BUILDING, Phase.BUILT,
                () -> queue.finishAfterCamera(Objects.requireNonNull(cameras, "actual post-scroll cameras")));
    }

    /** UpdateGlobalAnims.Rings then RingSpill (Main.asm:520-549), once after an unpaused build. */
    public void finishOrdinaryTail() {
        perform(Phase.BUILT, Phase.TAIL, Phase.CLOSED_CUT, () -> {
            ringGlobals = ringGlobals.advanceGlobalRingAnimation();
            spillGlobals = spillGlobals.advanceTail();
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
        return new Snapshot(captureOwner, placement.capture(), ringGlobals, spillGlobals, queue.captureClosedCut(), displayed, sounds,
                devGlobals, contactGlobals, scoreGlobals, levelGlobals);
    }

    /** Pure partition preflight. A foreign snapshot rejects before any owned partition is changed. */
    public void validateRestore(Snapshot snapshot) {
        requireCut();
        Objects.requireNonNull(snapshot, "opening runtime snapshot");
        if (snapshot.owner != captureOwner) { throw new IllegalArgumentException("Foreign opening runtime/source owner capture"); }
        placement.validateRestore(snapshot.placement);
        queue.validateRestore(snapshot.queue);
        Objects.requireNonNull(snapshot.ringGlobals, "captured Ring globals");
        Objects.requireNonNull(snapshot.spillGlobals, "captured ring-spill globals");
        Objects.requireNonNull(snapshot.displayedFrame, "captured immutable picture");
        Objects.requireNonNull(snapshot.sounds, "captured immutable sound requests");
        Objects.requireNonNull(snapshot.devGlobals, "captured DEV globals");
        Objects.requireNonNull(snapshot.contactGlobals, "captured contact globals");
        Objects.requireNonNull(snapshot.scoreGlobals, "captured score globals");
        Objects.requireNonNull(snapshot.levelGlobals, "captured level globals");
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
            spillGlobals = snapshot.spillGlobals;
            devGlobals = snapshot.devGlobals;
            contactGlobals = snapshot.contactGlobals;
            scoreGlobals = snapshot.scoreGlobals;
            levelGlobals = snapshot.levelGlobals;
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
