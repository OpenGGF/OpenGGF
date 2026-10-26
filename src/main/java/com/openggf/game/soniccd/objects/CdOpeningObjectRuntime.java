package com.openggf.game.soniccd.objects;

import com.openggf.game.internal.NativeDynamicObjectPass;
import com.openggf.game.resources.PlcLifecyclePhase;
import com.openggf.game.resources.PlcLifecycleService;
import com.openggf.game.soniccd.level.CdAssetBytes;
import com.openggf.game.soniccd.level.CdR11aAsset;
import com.openggf.game.soniccd.level.CdR11aAssets;
import com.openggf.game.soniccd.level.CdR11aObjectAssets;
import com.openggf.game.soniccd.player.CdPlayerBody;
import com.openggf.game.soniccd.player.CdPlayerFrameRuntime;
import com.openggf.game.soniccd.player.CdPlayerObjectTouch;
import com.openggf.game.soniccd.player.CdPlayerMath;
import com.openggf.game.soniccd.player.CdPlayerMovement;
import com.openggf.game.soniccd.player.CdPlayerTouchResponse;
import com.openggf.game.soniccd.player.CdPlayerTerrain;
import com.openggf.game.soniccd.player.CdPlayerSpeedShoesReward;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11aSectionArt;
import com.openggf.graphics.SpritePresentation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
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
 * <p>Explicit DEV policy: USA R11A Present, goodFuture=0, projDestroyed=0, and
 * an actual reserved bank 1..31 at the declared DEV ClearObjects-equivalent cut,
 * initially zero CPU SAT/empty picture, and preloaded actual
 * Ring/Mosqui/3D-plant/3D-ramp-booster/explosion/points/flower/Floating-Block/normal-Spring/shared-horizontal-and-vertical-Tunnel-Door/Monitor-time/Boulder/diagonal-Spring/common-Spikes/Checkpoint/Platform57 art (the inert Animal path draws nothing).
 * The 36-tile powerup window starts cold zero and changes only at the selector-8 LoadShieldArt cut.
 * The initial Ring, contact, score and level globals are caller-declared DEV ingress, never an entry proof.
 * Live dynamic IDs $10 Ring, $11 LostRing, $3D Mosqui, $3E Pata-Bata, $24 Animal, $2C 3D plant and $2A 3D ramp,
 * aftermath IDs $18/$1C/$1F, $12 Floating Block, normal and diagonal $0A Spring, $34 Spin tunnel tag, $0D horizontal Door,
 * $35 vertical Tunnel Door, $1B Boulder, $26 common Spikes, $13 Checkpoint, $20 Collapsing Platform, $19 Monitor/Time Post and $1A shoes item and the death row's $3B Game/Time Over are owned.
 * The zero reserved bank is NOT post-native HUD/title initialization: later native owners of slots 2..31
 * (Level/Main.asm:261-274) remain omitted by this DEV policy. Primary slot 0 stays exclusively in Frame;
 * dynamic slots 32..127 stay exclusively in placement's existing pool. ID $0B splash execution stays unowned.
 * Every other ID fails at its live dispatch; standing-object MODE remains a typed player boundary.
 * Time Post activation submits only typed voice metadata; the next actual Main retains Movement's
 * TIME_WARP_ACTIVE boundary. Bounded current-camera PLC requests/preparation/service use the actual plan and
 * thirteen finite sources, with one immutable resident generation; later unowned producers stop when reached.
 * This does not execute native title/HUD/immediate-PLC prehistory, palette cycling or audio playback.
 * The loader, not declarations or this composition, authenticates asset bytes.</p>
 *
 * <p>Root prepares input on the exact bound Frame before service, owns the
 * incremented level word, actor awaiting/projection, reserved-slot admission,
 * source camera/scroll, render banks and composite restore preflight. It must
 * replace the actor's old one-player SAT path. All callbacks are synchronous,
 * on the owning game thread, and non-reentrant. Failure closes Frame and queue;
 * completed writes are neither retried nor rolled back.</p>
 *
 * Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>
 */
public final class CdOpeningObjectRuntime implements NativeDynamicObjectPass, PlcLifecycleService, AutoCloseable {
    public enum Phase {
        CLOSED_CUT, SERVICING, SERVICED, SPAWNING, SPAWNED, PLAYER_BODY, PLAYER_DONE, PLAYER_ENQUEUE,
        DYNAMIC_SLOTS, DYNAMIC_DONE, BUILDING, BUILT, TAIL, FAILED, CLOSED
    }

    /**
     * Stable bases in ONE root-governed atlas range exclusive to this picture.
     * Root reserves/binds its nineteen disjoint subdivisions; this value neither
     * claims graphics ownership nor recommends the arbitrary IDs used by tests.
     * The three-argument form derives the sequential frontier spans the production
     * module uses: plant = mosqui + 70 and ramp = plant + 28.
     * The contact spans append 70 + 5 + 39 tiles. The shorter forms then place the
     * 36-tile powerup window (LVLDMA aniArtBuffer,$A880,$480: Powerup/Main.asm:173)
     * at flower + 39. The 15-tile FloatBlock span follows the powerup window,
     * then the 36-tile normal Spring span follows FloatBlock (native $520..$543),
     * then the 12-tile vertical Tunnel Door span follows Spring (native $328..$333),
     * then the 82-tile Monitor/time span follows Door (native $5A8..$5F9),
     * then the 19-tile Boulder span follows Monitor/time (native $374..$386),
     * then the 46-tile diagonal Spring span follows Boulder (native $490..$4BD),
     * then the 8-tile common Spikes span follows diagonal Spring (native $320..$327),
     * then the 12-tile Checkpoint span follows Spikes (native $6CB..$6D6),
     * for 579 existing patterns. Anton appends 22 lazily selected current patterns (601 potential total).
     * Platform appends 57 current patterns after Anton (658 potential total); cold DEV seeds only that
     * Std-only window explicitly, without executing Std/title. Camera PLCs never initialize Platform.
     * The canonical form takes all nineteen bases explicitly; no Anton preload is implied.
     */
    public record LogicalBanks(int player, int ring, int mosqui, int plant, int ramp,
                               int explosion, int points, int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime, int boulder, int diagonalSpring, int spikes, int checkpoint, int anton, int collapsePlatform) {
        /** Compatibility18 preserves every old base; Platform follows Anton's twenty-two lazy patterns. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime,
                            int boulder, int diagonalSpring, int spikes, int checkpoint, int anton) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, boulder, diagonalSpring, spikes, checkpoint, anton, anton + 22);
        }
        /** Compatibility17 preserves every old base; Anton appends twenty-two lazily selected current patterns. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime,
                            int boulder, int diagonalSpring, int spikes, int checkpoint) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, boulder, diagonalSpring, spikes, checkpoint, checkpoint + 12);
        }
        /** Compatibility16 preserves every explicit old base; native Checkpoint adds twelve output patterns. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime,
                            int boulder, int diagonalSpring, int spikes) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, boulder, diagonalSpring, spikes, spikes + 8);
        }
        public LogicalBanks(int player, int ring, int mosqui) {
            this(player, ring, mosqui, mosqui + 70, mosqui + 70 + 28);
        }
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp) {
            this(player, ring, mosqui, plant, ramp, ramp + 63, ramp + 63 + 70, ramp + 63 + 70 + 5);
        }
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points, int flower) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, flower + 39);
        }
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, powerup + 36);
        }
        /** Compatibility with the previous ten-span canonical constructor. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, floatBlock + 15);
        }
        /** Compatibility with the previous eleven-span canonical constructor. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring, spring + 36);
        }
        /** Compatibility with the previous fifteen-span canonical constructor, preserving every explicit prior base. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime,
                            int boulder, int diagonalSpring) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, boulder, diagonalSpring, diagonalSpring + 46);
        }
        /** Compatibility with the previous fourteen-span canonical constructor, preserving every explicit prior base. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime, int boulder) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, boulder, boulder + 19);
        }
        /** Compatibility with the previous thirteen-span canonical constructor. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, monitorTime + 82);
        }
        /** Compatibility with the previous twelve-span canonical constructor. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, tunnelDoorVertical + 12);
        }
    }

    /**
     * Plain explicit DEV restart carry, not a native full-restart/history certificate.
     * Root supplies CURRENT native bytes and last represented code table, and owns any
     * source-backed section HIGH-byte reset policy. No queue, decoder or service is carried.
     * Main.asm:275 clears sectionID.b only in the title branch; omitted immediate Std/Ring
     * loads may have changed the native table, so this does not claim that unrepresented table.
     */
    public record CameraArtRestartIngress(CdR11aCameraArtResidency.State residency,
                                           CdAssetBytes codeTable512, int sectionWord) {
        public CameraArtRestartIngress {
            Objects.requireNonNull(residency, "explicit carried current-camera residency");
            Objects.requireNonNull(codeTable512, "explicit last represented code table");
            if (codeTable512.length() != 512 || (sectionWord & ~0xFFFF) != 0) {
                throw new IllegalArgumentException("Camera restart requires a 512-byte table and unsigned section WORD");
            }
        }
    }

    /** One value partition; independent live-input and saved-byte owners retain their knowledge exactly. */
    public record CheckpointOwnerState(CdCheckpointLiveAux.State live, CdCheckpointSaveData.State saved) {
        public CheckpointOwnerState {
            Objects.requireNonNull(live, "actual checkpoint live bytes and knowledge");
            Objects.requireNonNull(saved, "actual checkpoint saved bytes and knowledge");
        }
    }

    /**
     * Reached scalar reads from Root's actual owners, synchronous and non-mutating.
     * Only the eight FG/BG selectors enter readCameraWord. Only actual whole0 byte offsets0..63
     * enter readPrimaryByte; an unrepresented byte throws CdCheckpoint.UnownedParentByte.
     * No closed-cut capture, source fallback, aggregate RAM or implicit zero is authorized.
     */
    public interface CheckpointReads {
        int readCameraWord(CdCheckpointSaveData.Source source);
        int readPrimaryByte(int byteOffset);
    }

    /**
     * Required declared ingress. Optional loadSectionCameraX is the actual post-load-scroll WORD
     * at PP Load Level Data.asm:37-39 -> LoadSectionArt (Section Art Load.asm:7-22), not inferred
     * from final checkpoint/spawn bytes. Empty explicitly omits that native load branch under DEV.
     * Present admits one HIGH-byte store plus LoadPLC append only, not WaitPLC/title/fade completion.
     */
    public record CheckpointBindings(CheckpointOwnerState initial, CheckpointReads reads,
                                     OptionalInt loadSectionCameraX) {
        public CheckpointBindings {
            Objects.requireNonNull(initial, "required initial checkpoint owner value");
            Objects.requireNonNull(reads, "required actual checkpoint read bridges");
            Objects.requireNonNull(loadSectionCameraX, "explicit presence of load-time section operation");
            if (loadSectionCameraX.isPresent() && (loadSectionCameraX.getAsInt() & ~0xFFFF) != 0) {
                throw new IllegalArgumentException("LoadSectionArt requires an unsigned camera X WORD");
            }
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

    /** The level restart is no longer a typed stop: endCycleAtLevelRestart ends the cycle at its cut. */
    public enum UnsupportedContactBoundary { LOST_RING_NATIVE_CLOCK }
    public static final class UnsupportedContact extends UnsupportedOperationException {
        private final UnsupportedContactBoundary boundary;
        private UnsupportedContact(UnsupportedContactBoundary boundary) {
            super(boundary + ": LostRing binding requires an explicitly represented absolute native V-int counter");
            this.boundary = boundary;
        }
        public UnsupportedContactBoundary boundary() { return boundary; }
    }

    /**
     * Required legacy port to the EXISTING shared primaryAngle byte, not oAngle.
     * Mosqui, Flower and LostRing return this write without applying it. Root must write that byte
     * exactly once, without restoring a Frame capture or running another owner.
     * Invocation is in returned-effect order, before the next physical dispatch.
     * Monitor's synchronous floor cut instead calls Frame's existing writer directly once;
     * it never invokes this external port as well. The legacy owners retain their existing contract.
     */
    @FunctionalInterface public interface PrimaryAngleWrite { void write(int unsignedByte); }

    /**
     * The R11A asset family supplies no MMZ global-layer owner. Source ObjMonitor_Solid
     * (Monitor and Time Post/Main.asm:242-259) reads that byte only for zone6. Stop before
     * entering the body on that reached zone; borrowing an object byte below grants no MMZ admission.
     */
    public enum UnsupportedMonitorBindingBoundary { MMZ_GLOBAL_LAYER }
    public static final class UnsupportedMonitorBinding extends UnsupportedOperationException {
        private final UnsupportedMonitorBindingBoundary boundary;
        private final int wholeIndex;
        private UnsupportedMonitorBinding(int wholeIndex) {
            super("MMZ_GLOBAL_LAYER at Monitor whole slot " + wholeIndex + ": no current native global-layer owner");
            boundary = UnsupportedMonitorBindingBoundary.MMZ_GLOBAL_LAYER;
            this.wholeIndex = wholeIndex;
        }
        public UnsupportedMonitorBindingBoundary boundary() { return boundary; }
        public int wholeIndex() { return wholeIndex; }
    }

    /** Ordered requests only, no playback and no claim to the omitted service/music ledger. */
    public sealed interface SoundRequest permits PlayerSound, RingFm, RingSubCpu, MosquiFm, LostRingFm, GameOverSubCpu, SpringFm, TunnelDoorVerticalFm, TunnelDoorFm, MonitorFm, MonitorItemSubCpu, TimePostSubCpu, CheckpointFm, CollapsePlatformFm { }
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
    /** ObjSpring's own synchronous PlayFMSound port; no unrelated numeric/enum sound alias. */
    public record SpringFm(CdSpring.FmSound sound) implements SoundRequest {
        public SpringFm { Objects.requireNonNull(sound, "Spring FM sound"); }
    }
    /** ObjTunnelDoorV's source FM_A4 cut; metadata only, separate from shared Solid FM. */
    public record TunnelDoorVerticalFm(CdTunnelDoorVertical.FmSound sound) implements SoundRequest {
        public TunnelDoorVerticalFm { Objects.requireNonNull(sound, "vertical Tunnel Door FM sound"); }
    }
    /** ObjTunnelDoor's successful native splash-creation FM_A4 cut; distinct from DoorV and shared Solid. */
    public record TunnelDoorFm(CdTunnelDoor.FmSound sound) implements SoundRequest {
        public TunnelDoorFm { Objects.requireNonNull(sound, "horizontal Tunnel Door FM sound"); }
    }
    /** Monitor Break's synchronous PlayFMSound before its first object store; no shared-contact alias. */
    public record MonitorFm(CdMonitor.FmSound sound) implements SoundRequest {
        public MonitorFm { Objects.requireNonNull(sound, "Monitor FM sound"); }
    }
    /** Commands completed after the one native five-store shoes patch; no playback or powerup/art request. */
    public record MonitorItemSubCpu(CdPlayerSpeedShoesReward.SubCpuCommand command) implements SoundRequest {
        public MonitorItemSubCpu { Objects.requireNonNull(command, "Monitor item sub-CPU command"); }
    }
    /** Time Post's synchronous SubCPUCmd cut, before wrapper DrawObject/despawn; typed request only, no playback. */
    public record TimePostSubCpu(CdTimePost.SubCpuCommand command) implements SoundRequest {
        public TimePostSubCpu { Objects.requireNonNull(command, "Time Post sub-CPU command"); }
    }
    /** Checkpoint's completed synchronous FM_AE request, metadata only; no driver execution or replay. */
    public record CheckpointFm(CdCheckpoint.FmSound sound) implements SoundRequest {
        public CheckpointFm { Objects.requireNonNull(sound, "Checkpoint FM sound"); }
    }
    /** ObjGameOver's SubCPUCmd requests (Game Over/Main.asm:28-29,87-88); no handshake, PCM fade or music. */
    /** ObjCollapsePlatform_Main's one native PlayFMSound edge; request metadata, not driver execution. */
    public record CollapsePlatformFm(int nativeId) implements SoundRequest {
        public CollapsePlatformFm {
            if (nativeId != CdCollapsePlatform.COLLAPSE_FM) {
                throw new IllegalArgumentException("Collapsing Platform requests exactly FM_A3");
            }
        }
    }
    public record GameOverSubCpu(CdGameOver.SubCpuCommand command) implements SoundRequest {
        public GameOverSubCpu { Objects.requireNonNull(command, "Game Over sub-CPU command"); }
    }

    /**
     * Privately minted owned partition; callers cannot replace its pieces or owner.
     * Placement already contains the ONLY dynamic-pool/saved-bank snapshot. reserved contains the
     * ONLY immutable capture of the one real 1..31 storage bank, with no primary/dynamic shadow. Queue contains
     * the CPU SAT; displayedFrame freezes its own pixels, possibly an older picture.
     * powerupArtState is the one immutable LoadShieldArt CPU/resident window. sectionArtState contains
     * the ONLY camera PLC queue/table/decoder/count/residency capture; displayed pixels freeze independently.
     * No actor/shared Frame, pending input, TouchContext, scan snapshot, camera or
     * scroll capture is stored. Root captures/prevalidates those owners separately.
     */
    public static final class Snapshot {
        private final Object owner;
        private final CdObjectPlacementRuntime.Snapshot placement;
        private final CdReservedObjectBank.Snapshot reserved;
        private final CdRingGlobals ringGlobals;
        private final CdRingSpillGlobals spillGlobals;
        private final CdObjectDrawQueue.Snapshot queue;
        private final SpritePresentation.Frame displayedFrame;
        private final List<SoundRequest> sounds;
        private final DevGlobals devGlobals;
        private final CdPlayerBody.ContactGlobals contactGlobals;
        private final ScoreGlobals scoreGlobals;
        private final CdLevelGlobals levelGlobals;
        private final CdPowerupArtRuntime.State powerupArtState;
        private final CdR11aSectionArt.State sectionArtState;
        private final CheckpointOwnerState checkpointState;

        private Snapshot(Object owner, CdObjectPlacementRuntime.Snapshot placement, CdReservedObjectBank.Snapshot reserved,
                         CdRingGlobals ringGlobals,
                         CdRingSpillGlobals spillGlobals,
                         CdObjectDrawQueue.Snapshot queue, SpritePresentation.Frame displayedFrame,
                         List<SoundRequest> sounds, DevGlobals devGlobals,
                         CdPlayerBody.ContactGlobals contactGlobals, ScoreGlobals scoreGlobals,
                         CdLevelGlobals levelGlobals, CdPowerupArtRuntime.State powerupArtState,
                         CdR11aSectionArt.State sectionArtState, CheckpointOwnerState checkpointState) {
            this.owner = owner;
            this.placement = placement;
            this.reserved = reserved;
            this.ringGlobals = ringGlobals;
            this.spillGlobals = spillGlobals;
            this.queue = queue;
            this.displayedFrame = displayedFrame;
            this.sounds = List.copyOf(sounds);
            this.devGlobals = devGlobals;
            this.contactGlobals = contactGlobals;
            this.scoreGlobals = scoreGlobals;
            this.levelGlobals = levelGlobals;
            this.powerupArtState = powerupArtState;
            this.sectionArtState = sectionArtState;
            this.checkpointState = checkpointState;
        }
        public CdObjectPlacementRuntime.Snapshot placement() { return placement; }
        /** Immutable reserved partition for Root's full closed-cut comparator; no live bank or helper cut. */
        public CdReservedObjectBank.Snapshot reserved() { return reserved; }
        public CdRingGlobals ringGlobals() { return ringGlobals; }
        public CdRingSpillGlobals spillGlobals() { return spillGlobals; }
        public DevGlobals devGlobals() { return devGlobals; }
        public CdPlayerBody.ContactGlobals contactGlobals() { return contactGlobals; }
        public ScoreGlobals scoreGlobals() { return scoreGlobals; }
        public CdLevelGlobals levelGlobals() { return levelGlobals; }
        public CdPowerupArtRuntime.State powerupArtState() { return powerupArtState; }
        public CdR11aSectionArt.State sectionArtState() { return sectionArtState; }
        public CheckpointOwnerState checkpointState() { return checkpointState; }
        public SpritePresentation.Frame displayedFrame() { return displayedFrame; }
        public List<SoundRequest> lastSoundRequests() { return sounds; }
    }

    private final Object captureOwner = new Object();
    private final CdR11aAssets assets;
    private final CdPlayerFrameRuntime frame;
    private final CdR11aObjectAssets objects;
    private final CdObjectPlacementRuntime placement;
    private final CdReservedObjectBank reserved;
    private final CdPlayerObjectTouch scanner;
    private final CdPlayerTouchResponse touchResponse;
    private final CdRing ring;
    private final CdMosqui mosqui;
    private final CdPataBata pataBata;
    private final CdAnton anton;
    private final CdAnimal animal;
    private final Cd3dPlant plant;
    private final Cd3dRamp ramp;
    private final CdExplosion explosion;
    private final CdPoints points;
    private final CdFlower flower;
    private final CdLostRing lostRing;
    private final CdGameOver gameOver;
    private final CdBoulder boulder;
    private final CdCollapsePlatform collapsePlatform;
    private final CdPlatform platform;
    private final CdSpikes spikes;
    private final CdCheckpoint checkpoint;
    private final CheckpointReads checkpointReads;
    private final CdFloatBlock floatBlock;
    private final CdSpring spring;
    private final CdTunnelDoorVertical tunnelDoorVertical;
    private final CdTunnelDoor tunnelDoor;
    private final CdSpinTunnelTag spinTunnelTag;
    private final CdMonitor monitor;
    private final CdTimePost timePost;
    private final CdMonitorItem monitorItem;
    private final CdPowerupArtRuntime powerupArt;
    private final CdR11aSectionArt sectionArt;
    private final CdObjectDrawQueue queue;
    private final LogicalBanks banks;
    private final PrimaryAngleWrite primaryAngleWrite;
    private final CdR11aObjectAssets.FrontierArtBank frontierArt;
    private final CdR11aObjectAssets.ContactArtBank contactArt;
    private final CdR11aObjectAssets.FloatBlockArtBank floatBlockArt;
    private final CdR11aObjectAssets.SpringArtBank springArt;
    private final CdR11aObjectAssets.TunnelDoorArtBank tunnelDoorArt;
    // One immutable authenticated decode; not a mutable/captured owner or a residency claim.
    private final CdR11aObjectAssets.MonitorTimeArtBank monitorTimeArt;
    private CdRingGlobals ringGlobals;
    // Level/Main.asm:178-184 ClearFlowers covers the three ringLoss animation values.
    private CdRingSpillGlobals spillGlobals = new CdRingSpillGlobals(0, 0, 0);
    private DevGlobals devGlobals = new DevGlobals(0, 0);
    private CdPlayerBody.ContactGlobals contactGlobals;
    private ScoreGlobals scoreGlobals;
    private CdLevelGlobals levelGlobals;
    // Sole mutable checkpoint value; no Frame/camera/pause/clock or duplicate saved-object bank.
    private CheckpointOwnerState checkpointState;
    // The one owned LoadShieldArt window; only the selector-8 V-int cut and restore replace this immutable value.
    private CdPowerupArtRuntime.State powerupArtState;
    // ONE camera-art aggregate; service/tail/restore replace it, never a separate resident or PLC store.
    private CdR11aSectionArt.State sectionArtState;
    private List<SoundRequest> sounds = List.of();
    private SpritePresentation.Frame displayed = SpritePresentation.Frame.empty();
    private CameraWords preScrollForeground;
    // Ephemeral successful CPU-build cut for Main.asm:428. Never captured or a scroll authority.
    private CameraWords postScrollForeground;
    // Only the ephemeral eligible call cut awaits the actor's explicit enqueue.
    // Never a raw player slot, Body publication, pool snapshot or capture partition.
    private CdPlayerBody.DisplayEligibility expectedPlayerCut;
    private boolean cameraPrepared;
    private Phase phase = Phase.CLOSED_CUT;
    private boolean operating;
    private boolean cleaned;
    private boolean inDrawCallback;
    private boolean inSolidCommit;
    private boolean inSpringLaunch;
    private boolean inSpinTunnelCommit;
    private boolean inTunnelDoorFm;
    private boolean inHorizontalTunnelDoorSolid;
    private boolean inHorizontalTunnelDoorFm;
    private boolean inMonitorAngle;
    private boolean inMonitorFm;
    private boolean inMonitorAward;
    private boolean inTimePostDirection;
    private boolean inTimePostSubCpu;
    // Ephemeral callback validation only; cleared in runCheckpoint finally, never captured/replayed.
    private boolean inCheckpointDispatch;
    private int checkpointGlobalStores;
    private CdObjectState checkpointActivationSource;
    private CdLevelGlobals checkpointActivationLevel;
    private boolean checkpointSaveCompleted;
    private boolean checkpointFmRequested;
    private CdCheckpointSaveData.State checkpointSavedAtCompletion;
    /** Invocation-local authority only: never gameplay state, a saved phase or a capture partition. */
    private record CollapseInvocation(CdCollapsePlatform body, int wholeIndex) { }
    private transient CollapseInvocation activeCollapseInvocation;
    /** Native visit origin only; never saved gameplay state or a retained callback capability. */
    private record PlatformInvocation(CdPlatform body, int wholeIndex) { }
    private transient PlatformInvocation activePlatformInvocation;
    private int dispatchWholeIndex = -1;

    /**
     * Fresh-entry construction path. The exact external-touch Frame must use assets by
     * reference, be open at a closed cut, and have UNIMPLEMENTED dynamic fallback.
     * Root binds Frame art publication before the first service (two-stage wiring).
     * Actual openingObjects() reauthenticates the retained media; declared source
     * records, mapping/pixel ports and raw object bytes are not accepted here.
     *
     * <p>The explicitly supplied 640 bytes must ALL be zero for this named DEV
     * empty-SAT policy. This is not a native retail title-to-level ingress proof.
     * Preload describes the pixel source only; root owns actual graphics banks.
     * Level globals are caller-declared post-LevelStart values with no default.</p>
     *
     * <p>Cold DEV powerup window: CPU aniArtBuffer ($FF1980, $480 bytes; Main CPU Variables.inc:190) and its
     * resident VRAM copy at $A880 both start all zero. LevelStart's .ClearFlowers zeroes $A00 bytes from flowerPosBuf,
     * covering the buffer (Level/Main.asm:178-184); the MMD Start's InitVDP (Level/Initialization.asm:78-79)
     * DMA-fills VRAM with zero (Level/Functions (General).asm:137-150). PLC_Std's neighbouring destinations are
     * $A400 and $AD00 (Palmtree Panic/Data (Act 1 Present).asm:54-83), and LevelStart's UpdateAnimTiles call
     * (Level/Main.asm:312) finds powerup cleared at :167. An in-module restart skips InitVDP, so this is no restart
     * or carry seed, and CdShieldArt's general resident tail stays UNKNOWN.</p>
     */
    public static CdOpeningObjectRuntime usaR11aOpeningDevEmptySatPreloadedArt(
            CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
            CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore, CdLevelGlobals initialLevel,
            CheckpointBindings checkpointBindings,
            byte[] initialCpuSatBytes, LogicalBanks banks, CameraWords preScrollForeground,
            PrimaryAngleWrite primaryAngleWrite) throws IOException {
        // The cold DEV window above: .ClearFlowers zeroes the complete CPU buffer, InitVDP's fill its resident copy.
        return create(assets, frame, initialGlobals, initialContact, initialScore, initialLevel, checkpointBindings,
                CdObjectPlacementRuntime::fromAssets, new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]),
                null, initialCpuSatBytes, banks, preScrollForeground, primaryAngleWrite);
    }

    /**
     * Restart sibling for Root's explicitly admitted in-module LevelStart reconstruction
     * (Level/Main.asm:19-329). Checkpoint live/saved bytes are carried exactly; Root owns the native clear/load branches.
     * Same arguments, order and validation, plus two explicit immutable carry windows
     * and no defaults. initialSavedObjectFlags is the exact carried savedObjFlags bank, through the placement's restart
     * ingress. initialPowerup is the complete LoadShieldArt window: .ClearFlowers zeroes its CPU buffer (:178-184),
     * ClearScreen leaves VRAM $A880 (Level/Functions (General).asm:182-228) and :312's LoadShieldArt returns at once
     * after :167 clears powerup (Powerup/Main.asm:157-159). Root supplies both; their content is not policed here.
     * Required cameraArt carries current camera bytes, last represented table and raw section WORD verbatim.
     * Its new owner clears only CPU queue/decoder/count/budget/hint/pad; no cold fallback, implicit HIGH reset,
     * load/fade/title or immediate PLC3/Std1 history is invented. Its table is not asserted to be the native Ring
     * table after unrepresented LoadPLCImm. All seventeen spans/current drawable tiles validate before construction,
     * even for an empty SAT. Installs supplied windows and explicitly selected LoadSectionArt metadata only:
     * no service/upload/replay or invented WaitPLC/title/fade completion.
     */
    public static CdOpeningObjectRuntime usaR11aRestartDevEmptySatPreloadedArt(
            CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
            CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore, CdLevelGlobals initialLevel,
            CheckpointBindings checkpointBindings,
            CdAssetBytes initialSavedObjectFlags, CdPowerupArtRuntime.State initialPowerup,
            CameraArtRestartIngress cameraArt, byte[] initialCpuSatBytes, LogicalBanks banks, CameraWords preScrollForeground,
            PrimaryAngleWrite primaryAngleWrite) throws IOException {
        Objects.requireNonNull(initialSavedObjectFlags, "explicit carried saved-object bank");
        Objects.requireNonNull(initialPowerup, "explicit restart powerup CPU/resident windows");
        Objects.requireNonNull(cameraArt, "required explicit restart current-camera carry");
        return create(assets, frame, initialGlobals, initialContact, initialScore, initialLevel, checkpointBindings,
                source -> CdObjectPlacementRuntime.fromAssets(source, initialSavedObjectFlags), initialPowerup,
                cameraArt, initialCpuSatBytes, banks, preScrollForeground, primaryAngleWrite);
    }

    /**
     * The one admission/construction path behind both named DEV factories. Their declared reconstruction
     * admits the reached LevelStart/.ClearObjects boundary (Level/Main.asm:186-192), which writes 2048
     * zero longwords over all object RAM. This composition initializes ONLY its reserved 1..31 partition
     * there. The existing Frame and placement remain their separate owners. The later native HUD/title
     * writes (:261-274) are omitted by this DEV policy; this is not their initialized native image.
     */
    private static CdOpeningObjectRuntime create(
            CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
            CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore, CdLevelGlobals initialLevel,
            CheckpointBindings checkpointBindings,
            Function<CdR11aAssets, CdObjectPlacementRuntime> placementIngress, CdPowerupArtRuntime.State initialPowerup,
            CameraArtRestartIngress cameraArt, byte[] initialCpuSatBytes, LogicalBanks banks, CameraWords preScrollForeground,
            PrimaryAngleWrite primaryAngleWrite) throws IOException {
        Objects.requireNonNull(assets, "actual R11A assets");
        Objects.requireNonNull(frame, "external-touch player Frame");
        Objects.requireNonNull(initialGlobals, "explicit initial Ring globals");
        Objects.requireNonNull(initialContact, "explicit initial contact bytes");
        Objects.requireNonNull(initialScore, "explicit initial score globals");
        Objects.requireNonNull(initialLevel, "explicit initial level globals");
        Objects.requireNonNull(checkpointBindings, "required actual checkpoint owner/read/load bindings");
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
        var floatBlockArt = objects.floatBlockArtBank();
        var springArt = objects.springArtBank(); // One actual normal36 decode; no diagonal/section-PLC claim.
        var tunnelDoorArt = objects.tunnelDoorArtBank(); // One actual twelve-tile bank; no native PLC/residency claim.
        var monitorTimeArt = objects.monitorTimeArtBank(); // Exactly one immutable actual82 decode for both conversion sites.
        // SAME authenticated object owner/source bank: reuse its actual seed decodes; never reopen via fromAssets.
        var sourceBank = objects.sectionArtSources();
        var plan = assets.loadPlan();
        if (sourceBank.sources().size() != 13) throw new IllegalArgumentException("Thirteen reviewed section source views required");
        var programs = new ArrayList<CdR11aSectionArt.SourceProgram>();
        for (var source : sourceBank.sources()) {
            var entry = source.canonicalPlc(); var reader = source.readerInputRange(); var blob = entry.sourceRange();
            if (source.canonicalPlcId() < 0 || source.canonicalPlcId() >= plan.plcs().size()
                    || source.canonicalRowIndex() < 0
                    || source.canonicalRowIndex() >= plan.plcs().get(source.canonicalPlcId()).entries().size()
                    || !entry.equals(plan.plcs().get(source.canonicalPlcId()).entries().get(source.canonicalRowIndex()))
                    || source.source() != entry.source() || !source.decode().sourceBlobRange().equals(blob)
                    || reader.file() != blob.file() || reader.domain() != blob.domain()
                    || reader.fileOffset() != blob.fileOffset() || reader.length() != source.readerBytes().length()
                    || source.readerBytes().length() < 2
                    || (source.readerBytes().unsignedWord(0) & 0x7FFF) != source.tileCount()) {
                throw new IllegalArgumentException("Current-camera source bank differs from actual R11A load plan");
            }
            programs.add(new CdR11aSectionArt.SourceProgram(entry, source.readerBytes()));
        }
        var sectionArt = new CdR11aSectionArt(plan.plcs(), plan.sections(), programs);
        // Cold ONLY: explicit DEV preload/table0/section0, not title/native cold history or known zero VRAM.
        // The source bank above authenticates immutable provenance on BOTH ingress paths. Only this cold
        // branch writes producer pixels into residency; restart takes current bytes AND masks verbatim.
        CdR11aSectionArt.State initialSectionArt;
        if (cameraArt == null) {
            var resident = CdR11aCameraArtResidency.devSeed(
                    objects.initialArtBank().binding(CdR11aAsset.ART_MOSQUI).bytes(),
                    frontier.binding(CdR11aAsset.ART_3D_PLANT).bytes(),
                    frontier.binding(CdR11aAsset.ART_3D_RAMP_BOOST).bytes());
            var source = sourceBank.source(CdR11aAsset.ART_BOULDER);
            if (source.canonicalPlcId() != 2 || source.canonicalRowIndex() != 0
                    || source.canonicalPlc().vramAddress() != 0x6E80 || source.tileCount() != 19) {
                throw new IllegalArgumentException("Boulder DEV preload requires the actual canonical PLC2 row0 nineteen tiles");
            }
            // PLC_Cam1_Full row0 (Palmtree Panic/Data (Act 1 Present).asm:85-88): explicit DEV preload,
            // exactly nineteen complete tile writes from the SAME authenticated thirteen-source bank. No PLC
            // execution/decoder/table/count/history is fabricated; Animals and all adjacent gaps stay UNKNOWN.
            var decoded = source.decode().bytes();
            for (int offset = 0; offset < decoded.length(); offset += 32) {
                resident = CdR11aCameraArtResidency.writeTile(resident,
                        source.canonicalPlc().vramAddress() + offset, decoded.slice(offset, 32));
            }
            // PLC_Std row5 (Palmtree Panic/Data (Act 1 Present).asm:66-67): cold DEV preload ONLY,
            // after the existing legacy/Boulder writes. The eighth immutable source supplies all46 tiles;
            // AT_9000 remains the sole physical owner. No actor bank, decoder/table/history or restart repair.
            var diagonalSource = sourceBank.source(CdR11aAsset.ART_DIAGONAL_SPRING);
            var diagonalDecoded = diagonalSource.decode().bytes();
            if (diagonalSource.canonicalPlcId() != 1 || diagonalSource.canonicalRowIndex() != 5
                    || diagonalSource.canonicalPlc().vramAddress() != 0x9200 || diagonalSource.tileCount() != 46
                    || diagonalDecoded.length() != 46 * 32) {
                throw new IllegalArgumentException("Diagonal Spring DEV preload requires actual canonical PLC1 row5 forty-six tiles");
            }
            for (int offset = 0; offset < diagonalDecoded.length(); offset += 32) {
                resident = CdR11aCameraArtResidency.writeTile(resident,
                        diagonalSource.canonicalPlc().vramAddress() + offset, diagonalDecoded.slice(offset, 32));
            }
            // PLC_Std row0 (Palmtree Panic/Data (Act 1 Present).asm:54-57): cold DEV preload ONLY,
            // AFTER legacy/Boulder19/diagonal46. Reuse source9's SAME cached finite decode, never reopen art.
            // Exactly eight complete writes; no PLC execution, table/history fabrication or restart repair.
            var spikesSource = sourceBank.source(CdR11aAsset.ART_SPIKES);
            var spikesDecoded = spikesSource.decode().bytes();
            if (spikesSource.canonicalPlcId() != 1 || spikesSource.canonicalRowIndex() != 0
                    || spikesSource.canonicalPlc().vramAddress() != 0x6400 || spikesSource.tileCount() != 8
                    || spikesSource.readerBytes().unsignedWord(0) != 0x8008 || spikesDecoded.length() != 256) {
                throw new IllegalArgumentException("Spikes DEV preload requires actual canonical PLC1 row0 eight XOR tiles");
            }
            for (int offset = 0; offset < spikesDecoded.length(); offset += 32) {
                resident = CdR11aCameraArtResidency.writeTile(resident,
                        spikesSource.canonicalPlc().vramAddress() + offset, spikesDecoded.slice(offset, 32));
            }
            // PLC_Level row1 (Data (Act 1 Present).asm:47-52). Cold DEV ONLY, AFTER all old writes.
            // SAME retained thirteen-source bank and cached actual decode; exactly twelve complete writes.
            var checkpointSource = sourceBank.source(CdR11aAsset.ART_CHECKPOINT);
            var checkpointDecoded = checkpointSource.decode().bytes();
            if (checkpointSource.canonicalPlcId() != 0 || checkpointSource.canonicalRowIndex() != 1
                    || checkpointSource.canonicalPlc().vramAddress() != 0xD960 || checkpointSource.tileCount() != 12
                    || checkpointDecoded.length() != 384) {
                throw new IllegalArgumentException("Checkpoint DEV preload requires actual canonical PLC0 row1 twelve tiles");
            }
            for (int offset = 0; offset < checkpointDecoded.length(); offset += 32) {
                resident = CdR11aCameraArtResidency.writeTile(resident,
                        checkpointSource.canonicalPlc().vramAddress() + offset, checkpointDecoded.slice(offset, 32));
            }
            // PLC_Std row6 (Data (Act 1 Present).asm:68-69): explicit cold DEV seed AFTER every old write.
            // Platform is Std-only: camera PLCs do not initialize it. This is no Std/title execution claim.
            // SAME sourceBank/cached decode; AT_9000 remains the sole physical owner, including known masks.
            var platformSource = sourceBank.source(CdR11aAsset.ART_PLATFORM);
            var platformDecoded = platformSource.decode().bytes();
            if (platformSource.canonicalPlcId() != 1 || platformSource.canonicalRowIndex() != 6
                    || platformSource.canonicalPlc().vramAddress() != 0x97C0 || platformSource.tileCount() != 57
                    || platformDecoded.length() != 1824) {
                throw new IllegalArgumentException("Platform DEV preload requires actual canonical PLC1 row6 fifty-seven tiles");
            }
            for (int tile = 0; tile < 57; tile++) {
                resident = CdR11aCameraArtResidency.writeTile(resident,
                        platformSource.canonicalPlc().vramAddress() + tile * 32, platformDecoded.slice(tile * 32, 32));
            }
            initialSectionArt = sectionArt.devState(resident, CdAssetBytes.copyOf(new byte[512]), 0);
        } else {
            initialSectionArt = sectionArt.devState(cameraArt.residency(), cameraArt.codeTable512(), cameraArt.sectionWord());
        }
        // Actual selected load operation, once, AFTER adopting carry verbatim. No prepare/decode/service/write.
        if (checkpointBindings.loadSectionCameraX().isPresent()) {
            initialSectionArt = sectionArt.loadSection(initialSectionArt, checkpointBindings.loadSectionCameraX().getAsInt());
        }
        var placement = placementIngress.apply(assets);
        // Actual declared DEV ClearObjects-equivalent ingress for cold AND restart, never a restore default.
        var reserved = CdReservedObjectBank.clearedAfterLevelStart();
        var mappings = CdObjectMappings.fromOpeningAssets(assets, objects);
        // Validate all nineteen spans: old579, lazy Anton22 and lazily selected current Platform57.
        // Empty SAT admission reads no canonical Anton pixels; cold $8120 remains intentionally UNKNOWN.
        CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(frame.artState(), initialPowerup, objects, frontier, contact,
                floatBlockArt, springArt, tunnelDoorArt, monitorTimeArt, initialSectionArt.residency(), banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(),
                banks.explosion(), banks.points(), banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(), banks.spikes(), banks.checkpoint(), banks.anton(), banks.collapsePlatform());
        return new CdOpeningObjectRuntime(assets, frame, initialGlobals, initialContact, initialScore, initialLevel, checkpointBindings, initial,
                banks, preScrollForeground, primaryAngleWrite, objects, frontier, contact, floatBlockArt, springArt, tunnelDoorArt, monitorTimeArt, placement, reserved, mappings, initialPowerup, sectionArt, initialSectionArt);
    }

    private CdOpeningObjectRuntime(CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
                                   CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore,
                                   CdLevelGlobals initialLevel, CheckpointBindings checkpointBindings, byte[] initial, LogicalBanks banks, CameraWords camera,
                                   PrimaryAngleWrite primaryAngleWrite, CdR11aObjectAssets objects,
                                   CdR11aObjectAssets.FrontierArtBank frontier,
                                   CdR11aObjectAssets.ContactArtBank contact,
                                   CdR11aObjectAssets.FloatBlockArtBank floatBlockArt,
                                   CdR11aObjectAssets.SpringArtBank springArt,
                                   CdR11aObjectAssets.TunnelDoorArtBank tunnelDoorArt,
                                    CdR11aObjectAssets.MonitorTimeArtBank monitorTimeArt,
                                   CdObjectPlacementRuntime placement, CdReservedObjectBank reserved, CdObjectMappings mappings,
                                   CdPowerupArtRuntime.State initialPowerup, CdR11aSectionArt sectionArt,
                                   CdR11aSectionArt.State initialSectionArt) {
        this.assets = assets;
        this.frame = frame;
        this.objects = objects;
        this.frontierArt = frontier;
        this.contactArt = contact;
        this.floatBlockArt = floatBlockArt;
        this.springArt = springArt;
        this.tunnelDoorArt = tunnelDoorArt;
        this.monitorTimeArt = monitorTimeArt;
        this.placement = placement;
        this.reserved = reserved;
        this.banks = banks;
        this.primaryAngleWrite = primaryAngleWrite;
        ringGlobals = initialGlobals;
        contactGlobals = initialContact;
        scoreGlobals = initialScore;
        levelGlobals = initialLevel;
        checkpointState = checkpointBindings.initial(); // Adopt exact supplied bytes AND knowledge; no clear/time-zero guess.
        checkpointReads = checkpointBindings.reads();
        powerupArtState = initialPowerup;
        this.sectionArt = sectionArt;
        sectionArtState = initialSectionArt;
        // One pure LoadShieldArt over the aggregate's actual five raw copies (Powerup/Main.asm:157-186).
        powerupArt = new CdPowerupArtRuntime(assets.shield());
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
        pataBata = new CdPataBata(placement, CdPataBata.Assets.fromObjectAssets(objects, assets.player()),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        anton = new CdAnton(placement, CdAnton.Assets.fromObjectAssets(objects),
                new CdPlayerTerrain(assets.terrain()), this::commitAntonPrimaryAngle,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
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
        gameOver = new CdGameOver(placement, CdGameOver.Assets.fromObjectAssets(objects),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        var solid = CdSolidObject.fromAssets(assets);
        collapsePlatform = new CdCollapsePlatform(placement, CdCollapsePlatform.Assets.fromObjectAssets(objects),
                solid, this::commitCollapseSolid,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()), this::playCollapseFm);
        platform = new CdPlatform(placement, CdPlatform.Assets.fromObjectAssets(objects),
                new CdPlayerMath(assets.player()), new CdPlayerTerrain(assets.terrain()), solid,
                this::commitPlatformSolid, this::commitPlatformPrimaryAngle,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));

        boulder = new CdBoulder(placement, CdBoulder.Assets.fromObjectAssets(objects),
                solid, this::commitBoulderSolid,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        spikes = new CdSpikes(placement, CdSpikes.BodyAssets.fromObjectAssets(objects), solid,
                this::commitSpikesSolid, this::commitSpikesYRewind, this::commitSpikesHurt,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        checkpoint = new CdCheckpoint(placement,
                CdCheckpoint.Assets.fromObjectAssets(objects, new CdPlayerMath(assets.player())),
                new CdCheckpoint.GlobalPort() {
                    @Override public CdLevelGlobals current() { return readCheckpointLevel(); }
                    @Override public void commit(CdCheckpoint.GlobalCall call) { commitCheckpointGlobal(call); }
                    @Override public int readParentByte(int rawAddressBits) { return readCheckpointParentByte(rawAddressBits); }
                }, new CdCheckpoint.SaveDataPort() {
                    @Override public CdCheckpointSaveData.State currentState() { return currentCheckpointSavedState(); }
                    @Override public CdCheckpointSaveData.Result save(CdCheckpoint.SaveCall call) { return saveCheckpoint(call); }
                }, this::playCheckpointFm, (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        floatBlock = new CdFloatBlock(placement, CdFloatBlock.Assets.fromObjectAssets(objects),
                solid, this::commitTopSolid,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        spring = new CdSpring(placement, CdSpring.Assets.fromObjectAssets(objects), new CdPlayerMath(assets.player()), solid,
                this::commitSpringSolid, this::commitSpringLaunch,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()), this::playSpringFm);
        tunnelDoorVertical = new CdTunnelDoorVertical(placement, CdTunnelDoorVertical.Assets.fromObjectAssets(objects), solid,
                this::commitTunnelDoorVerticalSolid,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()), this::playTunnelDoorVerticalFm);
        // SAME shared pure Solid, actual placement/bank/assets, and existing live DrawObject adapter.
        tunnelDoor = new CdTunnelDoor(placement, reserved, CdTunnelDoor.Assets.fromObjectAssets(objects), solid,
                new CdTunnelDoor.SolidOwner() {
                    @Override public CdTunnelDoor.LiveSolid preflight(CdTunnelDoor.SolidCut cut) {
                        return preflightTunnelDoorSolid(cut);
                    }
                    @Override public CdTunnelDoor.LiveSolid commit(CdTunnelDoor.SolidCall call) {
                        return commitTunnelDoorSolid(call);
                    }
                }, (pool, slot) -> drawDynamic(pool, slot.wholeIndex()), this::playTunnelDoorFm);
        // Immutable pure tag bound to the actual raw Powerup mapping owner, not a declared pointer or new art bank.
        spinTunnelTag = new CdSpinTunnelTag(CdSpinTunnelTag.Assets.fromObjectAssets(objects));
        var monitorAssets = CdMonitor.Assets.fromObjectAssets(objects);
        monitor = new CdMonitor(placement, monitorAssets, solid, new CdPlayerTerrain(assets.terrain()),
                this::commitMonitorSolid, this::commitMonitorPrimaryAngle,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()), this::playMonitorFm);
        timePost = new CdTimePost(placement, monitorAssets, this::commitTimePostDirection, this::requestTimePostSubCpu,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        monitorItem = new CdMonitorItem(placement, monitorAssets, this::awardMonitorItem,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
    }

    public Phase phase() { return phase; }
    public boolean closed() { return phase == Phase.FAILED || phase == Phase.CLOSED || frame.closed(); }
    /** Live placement authority; exposed for root's explicit source owners, not a second dispatcher. */
    public CdObjectPlacementRuntime placement() { return placement; }
    /** Actual immutable storage image; like placement diagnostics, remains readable after terminal failure. */
    public CdReservedObjectBank.Image reservedState(int wholeIndex) { return reserved.state(wholeIndex); }
    /**
     * Narrow Root ingress for an explicitly represented reserved owner at a closed cut. Same-bank image only;
     * never initialization, player-2 execution, a callback-time RAM port or a second reserved store.
     */
    public void writeReservedImage(CdReservedObjectBank.Image image) {
        perform(Phase.CLOSED_CUT, Phase.CLOSED_CUT, Phase.CLOSED_CUT, () -> {
            frame.captureShared(); // Guard pending Frame input/body entitlement; retain no capture.
            reserved.write(Objects.requireNonNull(image, "actual same-bank reserved image"));
            return null;
        });
    }
    /** Immutable diagnostic state; no reset, HUD acknowledgement or time-stop writer is invented. */
    public CdRingGlobals ringGlobals() { return ringGlobals; }
    public CdRingSpillGlobals spillGlobals() { return spillGlobals; }
    /** Explicit owned DEV globals read by the bodies; no native writer (projector, good-future) is admitted. */
    public DevGlobals devGlobals() { return devGlobals; }
    public CdPlayerBody.ContactGlobals contactGlobals() { return contactGlobals; }
    public ScoreGlobals scoreGlobals() { return scoreGlobals; }
    /** The one level-flow store; Body summaries commit here without retaining their call context. */
    public CdLevelGlobals levelGlobals() { return levelGlobals; }
    /** The owned immutable LoadShieldArt CPU/resident window (Powerup/Main.asm:157-176). */
    public CdPowerupArtRuntime.State powerupArtState() { return powerupArtState; }
    /** Exact immutable camera queue/table/decoder/residency aggregate; diagnostic access survives a typed stop. */
    public CdR11aSectionArt.State sectionArtState() { return sectionArtState; }
    public CheckpointOwnerState checkpointState() { return checkpointState; }
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

    /**
     * Exactly one existing Frame service. NORMAL_PAUSE publishes; PP LAG retains its picture. Both selector-8
     * dispositions run LoadShieldArt at the Frame's level-art cut; lag retains the powerup byte and window.
     */
    @Override public void serviceVBlank(PlcLifecyclePhase servicePhase) {
        serviceVBlank(servicePhase, () -> { }, 0); // Explicit legacy DEV raw scrollLock WORD0 compatibility.
    }

    /**
     * Ephemeral CD level-plane service at VInt_Level's camera-copy/DrawLevel cut (Main:840-844),
     * after UpdateAnimTiles/LoadShieldArt and before Finish/count. Ordinary and pause invoke it;
     * PP lag does not. The source owner supplies no extra clock or retained callback.
     * Null rejects before consuming prepared input. Failure closes the existing composition;
     * completed input/art writes are retained and neither retried nor rolled back.
     */
    public void serviceVBlank(PlcLifecyclePhase servicePhase, Runnable afterLevelArt) {
        serviceVBlank(servicePhase, afterLevelArt, 0); // Explicit legacy DEV WORD0; Module supplies its actual live WORD.
    }

    /**
     * VInt_Level:837-847: powerup -> copied/drawn planes -> DecompPLCSlow, before Frame Finish/count.
     * rawScrollLockWord is the actual single big-endian WORD owner; the native byte read addresses HIGH.
     * Validate widths before consuming prepared input. Selector8 ordinary/pause service, lag never does.
     * No retained callback, preparation arm, second count or new timing owner exists.
     */
    public void serviceVBlank(PlcLifecyclePhase servicePhase, Runnable afterLevelArt, int rawScrollLockWord) {
        Objects.requireNonNull(afterLevelArt, "level-plane service after UpdateAnimTiles");
        if ((rawScrollLockWord & ~0xFFFF) != 0) throw new IllegalArgumentException("scrollLock must be an unsigned native WORD");
        boolean ordinary = servicePhase == PlcLifecyclePhase.ORDINARY_LEVEL;
        perform(Phase.CLOSED_CUT, Phase.SERVICING, ordinary ? Phase.SERVICED : Phase.CLOSED_CUT, () -> {
            requireSupportedService(servicePhase);
            requireNoLevelRestart("V-int");
            if (ordinary && !cameraPrepared) {
                throw new IllegalStateException("Root must supply the next ordinary pre-scroll camera at a closed cut");
            }
            frame.serviceVBlank(servicePhase, () -> {
                servicePowerupArtAtVInt();
                afterLevelArt.run();
                checkActive(Phase.SERVICING); // Swallowed plane reentry cannot write camera art or reach Finish.
                serviceSectionArtAtVInt(rawScrollLockWord);
            });
            checkActive(Phase.SERVICING); // Also detects a swallowed callback's reentrant failure.
            if (servicePhase != PlcLifecyclePhase.LAG) {
                // VInt_Level:827 SAT DMA, then :829-837 Sonic art and LoadShieldArt. At the completed boundary,
                // convert PREVIOUS CPU SAT using NOW-serviced immutable player and powerup resident pixels.
                var pixels = CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(frame.artState(), powerupArtState,
                        objects, frontierArt, contactArt, floatBlockArt, springArt, tunnelDoorArt, monitorTimeArt, sectionArtState.residency(),
                        banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(), banks.explosion(), banks.points(),
                        banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(), banks.spikes(), banks.checkpoint(), banks.anton(), banks.collapsePlatform());
                var nextDisplay = CdObjectPresentation.convert(queue.cpuSat(), pixels);
                checkActive(Phase.SERVICING);
                displayed = nextDisplay;
            }
            if (ordinary) { sounds = List.of(); }
            return null;
        });
    }

    /** Retain every completed owned prefix before fail-close; no rollback/retry or implicit preparation. */
    private void serviceSectionArtAtVInt(int rawScrollLockWord) {
        checkActive(Phase.SERVICING);
        try { sectionArtState = sectionArt.serviceSlow(sectionArtState, rawScrollLockWord); }
        catch (CdR11aSectionArt.SectionArtException boundary) {
            sectionArtState = boundary.state();
            throw boundary;
        }
    }

    /**
     * UpdateAnimTiles is JMP LoadShieldArt (Palmtree Panic/Animated Tiles Update.asm:7-8; VInt_Level, Level/Main.asm:837).
     * The Frame calls this once per selector-8 V-int, ordinary or paused, after the Sonic DPLC/DMA and before
     * VInt_Finish/count; never at PP lag. The pure service reads the ONE shared powerup byte and the owned window. Its
     * State already reflects LVLDMA aniArtBuffer,$A880,$480 (Powerup/Main.asm:173), so there is no GPU callback or
     * replay. Both immutable values exist before the single commit. UnsupportedArt leaves no Result, so nothing is
     * committed; the Frame and this runtime then close through their existing failure paths, with no rollback.
     */
    private void servicePowerupArtAtVInt() {
        checkActive(Phase.SERVICING);
        var result = powerupArt.service(powerupArtState, levelGlobals.powerup());
        var old = levelGlobals;
        var nextLevel = new CdLevelGlobals(old.levelRestart(), old.updateHudTime(), old.checkpoint(), old.spawnMode(),
                old.plcLoadFlags(), old.flowerCount(), old.fadeEnableDisplay(), result.powerup(), old.timeOver());
        powerupArtState = result.state();
        levelGlobals = nextLevel;
    }

    @Override public void spawnBeforeReservedSlots() {
        perform(Phase.SERVICED, Phase.SPAWNING, Phase.SPAWNED, () -> {
            requireNoLevelRestart("SpawnObjects"); // Unreachable while the V-int guard holds; kept at the source call.
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
     * is passed in the ephemeral touch, level and supported-object contexts; neither is stored here or in a
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
            var player = frame.state();
            var objectContext = new CdPlayerMovement.ObjectContext(callCut, player.objectLinks().standObject(),
                    player.header().slotId(), player.header().width());
            var publication = frame.runBody(levelFrames,
                    Optional.of(new CdPlayerBody.TouchContext(scanner, callCut, context)), Optional.of(level),
                    Optional.of(objectContext));
            checkActive(Phase.PLAYER_BODY);
            boolean displaySeen = false;
            boolean touchSeen = false;
            CdPlayerBody.DeadCheckGone gone = null; CdPlayerBody.RestartCut restart = null;
            var changed = new LinkedHashMap<Integer, CdObjectState>();
            var nextRing = ringGlobals; var nextScore = scoreGlobals; var nextContact = contactGlobals;
            // No Body owner writes live pool RAM. Even same-slot MODE and later touch images fold in source order
            // into one final publication, validated/CASed against the ORIGINAL pre-player cut below.
            for (var effect : publication.result().effects()) {
                if (effect instanceof CdPlayerBody.ModeEffect mode
                        && mode.effect() instanceof CdPlayerMovement.ObjectPatch write) {
                    var patch = write.patch(); var pool = placement.pool();
                    pool.validateWrite(patch.before()); pool.validateWrite(patch.after());
                    int whole = patch.before().identity().wholeIndex();
                    var before = changed.getOrDefault(whole, callCut.slots().get(patch.before().identity().dynamicIndex()));
                    if (!before.equals(patch.before())) throw new IllegalStateException("Stale native MODE object patch");
                    changed.put(whole, patch.after());
                } else if (effect instanceof CdPlayerBody.DeadCheckGone summary) {
                    if (gone != null || restart != null) throw new IllegalStateException("Body published more than one level summary");
                    gone = summary;
                } else if (effect instanceof CdPlayerBody.RestartCut summary) {
                    if (gone != null || restart != null) throw new IllegalStateException("Body published more than one level summary");
                    restart = summary;
                } else if (effect instanceof CdPlayerBody.DisplayEligibility cut) {
                    if (displaySeen) throw new IllegalStateException("Body published more than one Display cut");
                    displaySeen = true;
                    if (cut.eligible()) expectedPlayerCut = cut;
                } else if (effect instanceof CdPlayerBody.ObjectTouchResult touch) {
                    if (touchSeen) throw new IllegalStateException("Body published more than one touch result");
                    touchSeen = true;
                    touch.result().changedSlot().ifPresent(image -> changed.put(image.identity().wholeIndex(), image));
                } else if (effect instanceof CdPlayerBody.ObjectTouchResponse touch) {
                    if (touchSeen) throw new IllegalStateException("Body published more than one touch result");
                    touchSeen = true;
                    var result = touch.result(); var world = result.world();
                    nextRing = new CdRingGlobals(world.rings(), world.lives(), ringGlobals.livesFlags(),
                            ringGlobals.updateHudRings(), world.updateHudLives(), ringGlobals.timeStopTimer(),
                            ringGlobals.ringAnimationTimer(), ringGlobals.ringAnimationFrame());
                    nextScore = new ScoreGlobals(world.score(), world.nextLifeScore(), world.updateHudScore());
                    nextContact = touch.contact();
                    for (var image : result.changedSlots()) changed.put(image.identity().wholeIndex(), image);
                    // Actor/invincible/scoreChain are already final Frame values; never re-apply them here.
                }
            }
            var pool = placement.pool(); pool.validateRestore(callCut);
            if (!pool.capture().slots().equals(callCut.slots())) {
                throw new IllegalStateException("CD pool changed during its pure player call");
            }
            if (gone != null || restart != null) {
                if (touchSeen || !changed.isEmpty()) throw new IllegalStateException("Level summary cannot also publish MODE/touch slots");
                if (gone != null) commitDeadGone(callCut, gone, publication.result().effects());
                else commitRestart(restart, publication.result().effects());
            } else {
                commitTouchImages(callCut, List.copyOf(changed.values()));
                ringGlobals = nextRing; scoreGlobals = nextScore; contactGlobals = nextContact;
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
            // ObjGameOver tests lives in the ONE Ring store and read-modify-writes powerup/timeOver in the ONE level
            // store, re-read at every TST/BCLR/ADDQ/BSET cut; each store replaces only its own byte.
            var gameOverPort = new CdGameOver.GlobalsPort() {
                @Override public CdGameOver.Globals read() {
                    checkActive(Phase.DYNAMIC_SLOTS);
                    return new CdGameOver.Globals(ringGlobals.lives(), levelGlobals.powerup(), levelGlobals.timeOver());
                }
                @Override public void writePowerup(int value) {
                    checkActive(Phase.DYNAMIC_SLOTS);
                    var old = levelGlobals;
                    levelGlobals = new CdLevelGlobals(old.levelRestart(), old.updateHudTime(), old.checkpoint(), old.spawnMode(),
                            old.plcLoadFlags(), old.flowerCount(), old.fadeEnableDisplay(), value, old.timeOver());
                }
                @Override public void writeTimeOver(int value) {
                    checkActive(Phase.DYNAMIC_SLOTS);
                    var old = levelGlobals;
                    levelGlobals = new CdLevelGlobals(old.levelRestart(), old.updateHudTime(), old.checkpoint(), old.spawnMode(),
                            old.plcLoadFlags(), old.flowerCount(), old.fadeEnableDisplay(), old.powerup(), value);
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
                } else if (id == CdPataBata.OBJECT_ID) {
                    // Live physical ID$3E, native first good-future check and actual raw globals at THIS visit.
                    // No nativeObjectSupport/time-stop/debug gate. Completed conversion/draw/despawn metadata
                    // is already applied by the same pool body; replay would run the same slot twice.
                    pataBata.run(whole, new CdPataBata.Globals(devGlobals.goodFuture(), gates.timeZone(),
                            gates.timeWarpDirection(), preScrollForeground.xWord()));
                } else if (id == CdAnton.OBJECT_ID) {
                    // Live physical ID$3F; use actual raw globals and Frame angle at this visit.
                    // Native Init/Place/Main, angle stores and guarded Draw already execute synchronously.
                    // No activation/debug/time-stop gate or replay of completed Result effects.
                    anton.run(whole, new CdAnton.Globals(devGlobals.goodFuture(), gates.timeZone(),
                            gates.timeWarpDirection(), preScrollForeground.xWord(),
                            frame.environment().movement().globals().primaryAngle()));
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
                } else if (id == CdMonitor.OBJECT_ID) {
                    // ObjMonitor:279-280 selects from the ACTUAL live subtype at this physical visit.
                    if (placement.pool().state(whole).subtype() < 8) runMonitor(whole);
                    else runTimePost(whole);
                } else if (id == CdMonitorItem.OBJECT_ID) {
                    var primary = frame.state();
                    monitorItem.run(whole, Optional.of(new CdMonitorItem.PrimarySupport(
                            primary.header().slotId(), primary.motion().flags())));
                    // Actual dynamic support is read by the body from this same live pool, only when ObjMove needs it.
                    // Award/Draw callbacks already committed; never replay returned metadata or the item prefix.
                } else if (id == CdSpinTunnelTag.OBJECT_ID) {
                    runSpinTunnelTag(whole); // Fresh actual pool/motion/gates at this physical visit; no sound or Body rerun.
                } else if (id == CdTunnelDoor.OBJECT_ID) {
                    // ID $0D is its own physical body. Primary then ACTUAL reserved whole1, even with ID0;
                    // the two synchronous helper cuts publish live stores. Never replay completed effects.
                    tunnelDoor.run(whole, new CdTunnelDoor.Globals(gates.timeZone(), gates.timeWarpDirection(),
                            preScrollForeground.xWord()), new CdTunnelDoor.SolidInputs(solidPlayer(),
                            new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), solidGlobals()));
                } else if (id == CdTunnelDoorVertical.OBJECT_ID) {
                    // ObjTunnelDoorV reads the actual primary player before Solid's independent gates. Reset
                    // has no Solid, including on carry; completed body effects must never be replayed here.
                    tunnelDoorVertical.run(whole, new CdTunnelDoorVertical.Globals(gates.timeZone(), gates.timeWarpDirection(),
                            preScrollForeground.xWord()), new CdTunnelDoorVertical.SolidInputs(solidPlayer(),
                            new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), solidGlobals()));
                } else if (id == CdSpring.OBJECT_ID) {
                    // This physical visit reads the actual current S3 and pre-scroll FG camera. Solid and launch
                    // publish synchronously inside the bound body; its returned completed metadata is not replayed.
                    spring.run(whole, new CdSpring.Globals(preScrollForeground.xWord()), new CdSpring.SolidInputs(solidPlayer(),
                            new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), solidGlobals()));
                } else if (id == CdSpikes.OBJECT_ID) {
                    // ObjSpikes_Main (Spikes/Main.asm:36-83): raw globals and CURRENT primary/contact/world
                    // at THIS physical visit. No visibility/goodFuture/activation gate outside the native body.
                    // Solid, Y LONG, direct Hurt, Draw and tail already execute once; never replay Result effects.
                    spikes.run(whole, new CdSpikes.Globals(gates.timeZone(), gates.timeWarpDirection(),
                            preScrollForeground.xWord()), new CdSpikes.Inputs(solidPlayer(), solidGates(), solidGlobals(),
                            spikesPlayerGlobals(), spikesWorldGlobals()));
                } else if (id == CdCheckpoint.OBJECT_ID) {
                    // Live actual ID13; raw native despawn globals and pre-scroll FG, no activation/visibility gate.
                    // The body owns children/object stores; global/save/FM ports publish synchronously.
                    // Never replay ChildCreated, GlobalStored, SaveCompleted or any completed metadata.
                    runCheckpoint(whole);
                } else if (id == CdCollapsePlatform.OBJECT_ID) {
                    // Native ID20 visit, with actual raw pre-scroll globals and current Frame inputs.
                    // No outer visibility/time-stop/good-future/subtype gate or replay of body effects.
                    runCollapsePlatform(whole);
                } else if (id == CdPlatform.OBJECT_ID) {
                    // Actual live ID21; native Init/Main owns every reached gate, helper, child and tail.
                    // Completed effects describe synchronous work and are never replayed.
                    runPlatform(whole);
                } else if (id == CdBoulder.OBJECT_ID) {
                    // ObjBoulder_Main (Boulder/Main.asm:41-48) reads the actual primary at this physical
                    // visit; its own sprite sign is the only outer Solid gate. No good-future/route gate.
                    // Commit/Draw/tail are synchronous live work; completed metadata is never replayed.
                    boulder.run(whole, new CdBoulder.Globals(gates.timeZone(), gates.timeWarpDirection(),
                            preScrollForeground.xWord()), new CdBoulder.SolidInputs(solidPlayer(), solidGates(), solidGlobals()));
                } else if (id == CdFloatBlock.OBJECT_ID) {
                    // The source wrapper makes two TopSolid calls (four on Main -> Fall). Every call sees the
                    // previous call's player, angle buffers and actual pool publication before the next one.
                    floatBlock.run(whole, new CdFloatBlock.Globals(gates.timeZone(), preScrollForeground.xWord(),
                            preScrollForeground.yWord()), new CdFloatBlock.SolidInputs(solidPlayer(),
                            new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), solidGlobals()));
                } else if (id == CdGameOver.OBJECT_ID) {
                    // Init/Main over the live pool; the OVER child runs only when this walk reaches its own ID.
                    for (var effect : gameOver.run(whole, gameOverPort).effects()) {
                        if (effect instanceof CdGameOver.SubCpuRequest request) {
                            appendSound(new GameOverSubCpu(request.command()));
                        }
                        // OverText/Deleted/CallReturned metadata describes committed pool work; never replay it.
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
     * Level_MainLoop's post-RunObjects, pre-scroll restart branch (Level/Main.asm:407-408). With levelRestart 0 this
     * returns false and leaves DYNAMIC_DONE for the ordinary camera, build and tail; the dead/debug/routine camera
     * choice belongs to the World. Otherwise LevelStart follows: LevelScroll (:418), DrawObjects (:421) and the tail
     * (:423-430) never run. The queue abandons its pass, the camera cut is dropped, and the cycle ends at CLOSED_CUT,
     * still capturable, with every global, the pool, placement, CPU SAT, picture and sound ledger unchanged. The
     * captured levelRestart word marks the cut (no new phase); no later V-int or spawn is admitted while it is set.
     * Reconstruction is Root's; this owner never reloads.
     */
    public boolean endCycleAtLevelRestart() {
        boolean restart = perform(Phase.DYNAMIC_DONE, Phase.DYNAMIC_DONE, Phase.DYNAMIC_DONE, () -> {
            if (levelGlobals.levelRestart() == 0) { return false; }
            queue.abandonPassAtLevelStart();
            preScrollForeground = null;
            postScrollForeground = null;
            cameraPrepared = false;
            return true;
        });
        if (restart) { phase = Phase.CLOSED_CUT; }
        return restart;
    }

    /** Root supplies actual post-scroll FG/BG/BG3 words; no global camera or position fence. */
    public CpuSat finishAfterCamera(DrawCameras cameras) {
        return perform(Phase.DYNAMIC_DONE, Phase.BUILDING, Phase.BUILT, () -> {
            var actual = Objects.requireNonNull(cameras, "actual post-scroll cameras");
            var sat = queue.finishAfterCamera(actual);
            postScrollForeground = actual.foreground(); // Retain ONLY after the complete CPU SAT build succeeds.
            return sat;
        });
    }

    /** Main.asm:428-430: UpdateSectionArt -> ProcessPLCs -> existing Ring/spill tail, only after an ordinary build. */
    public void finishOrdinaryTail() {
        perform(Phase.BUILT, Phase.TAIL, Phase.CLOSED_CUT, () -> {
            try {
                var post = Objects.requireNonNull(postScrollForeground, "successful post-scroll CPU build cut");
                try {
                    sectionArtState = sectionArt.updateSection(sectionArtState, post.xWord());
                    sectionArtState = sectionArt.prepare(sectionArtState);
                } catch (CdR11aSectionArt.SectionArtException boundary) {
                    sectionArtState = boundary.state(); // Native section/queue stores and completed resident bytes.
                    throw boundary;
                }
                ringGlobals = ringGlobals.advanceGlobalRingAnimation();
                spillGlobals = spillGlobals.advanceTail();
                return null;
            } finally {
                preScrollForeground = null;
                postScrollForeground = null; // Both camera cuts are ephemeral; Root owns scroll/capture.
            }
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
        return new Snapshot(captureOwner, placement.capture(), reserved.capture(), ringGlobals, spillGlobals, queue.captureClosedCut(), displayed, sounds,
                devGlobals, contactGlobals, scoreGlobals, levelGlobals, powerupArtState, sectionArtState, checkpointState);
    }

    /** Pure partition preflight. A foreign snapshot rejects before any owned partition is changed. */
    public void validateRestore(Snapshot snapshot) {
        requireCut();
        Objects.requireNonNull(snapshot, "opening runtime snapshot");
        if (snapshot.owner != captureOwner) { throw new IllegalArgumentException("Foreign opening runtime/source owner capture"); }
        placement.validateRestore(snapshot.placement);
        validateDynamicCapture(snapshot.placement.pool());
        Objects.requireNonNull(snapshot.placement.status(), "captured placement status");
        if (Objects.requireNonNull(snapshot.placement.savedFlags(), "captured saved-object bytes").length()
                != CdObjectPlacementRuntime.SAVED_BANK_BYTES) {
            throw new IllegalArgumentException("Captured saved-object bank must retain its complete native extent");
        }
        reserved.validateRestore(snapshot.reserved); // Full 31-image owner/identity/width/order preflight, before ANY write.
        queue.validateRestore(snapshot.queue);
        Objects.requireNonNull(snapshot.ringGlobals, "captured Ring globals");
        Objects.requireNonNull(snapshot.spillGlobals, "captured ring-spill globals");
        Objects.requireNonNull(snapshot.displayedFrame, "captured immutable picture");
        Objects.requireNonNull(snapshot.sounds, "captured immutable sound requests");
        Objects.requireNonNull(snapshot.devGlobals, "captured DEV globals");
        Objects.requireNonNull(snapshot.contactGlobals, "captured contact globals");
        Objects.requireNonNull(snapshot.scoreGlobals, "captured score globals");
        Objects.requireNonNull(snapshot.levelGlobals, "captured level globals");
        Objects.requireNonNull(snapshot.powerupArtState, "captured powerup art window");
        // Snapshot is privately minted with the same captureOwner; its immutable section State cannot
        // be supplied independently through this API. Authenticate the whole partition before any write.
        // Never call prepare/service/update/decode/queue here merely to validate a restore.
        Objects.requireNonNull(snapshot.sectionArtState, "captured current-camera aggregate");
        Objects.requireNonNull(snapshot.checkpointState, "captured checkpoint owner value");
    }

    /** Prevalidates ALL owned partitions; no service/body/queue draw/pixels/sound/initialization is replayed. */
    public void restore(Snapshot snapshot) {
        // Keep preflight outside operating: it uses the same public cut guard as root's
        // composite preflight. Rejected foreign data cannot partially restore placement.
        try { validateRestore(snapshot); }
        catch (RuntimeException | Error failure) { failIdle(failure); throw failure; }
        perform(Phase.CLOSED_CUT, Phase.CLOSED_CUT, Phase.CLOSED_CUT, () -> {
            placement.restore(snapshot.placement);
            reserved.restore(snapshot.reserved); // Exactly the captured real bank; no ClearObjects/default/shadow on restore.
            queue.restore(snapshot.queue);
            ringGlobals = snapshot.ringGlobals;
            spillGlobals = snapshot.spillGlobals;
            devGlobals = snapshot.devGlobals;
            contactGlobals = snapshot.contactGlobals;
            scoreGlobals = snapshot.scoreGlobals;
            levelGlobals = snapshot.levelGlobals;
            powerupArtState = snapshot.powerupArtState; // Exactly the captured window: no cold reset or load replay.
            sectionArtState = snapshot.sectionArtState; // ONE exact immutable aggregate, no prepare/decode/queue replay.
            checkpointState = snapshot.checkpointState; // Authenticated captureOwner first, then exact value; no save/tick/clear/FM.
            displayed = snapshot.displayedFrame;
            sounds = snapshot.sounds;
            expectedPlayerCut = null;
            preScrollForeground = null;
            postScrollForeground = null;
            cameraPrepared = false; // Root must supply its separately restored scroll cut for the next cycle.
            return null;
        });
    }

    /** CPU ProcessPLCs belongs ONLY to finishOrdinaryTail; this generic hook never arms or repeats it. */
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

    private CdSolidObject.Player solidPlayer() {
        var s = frame.state(); var h = s.header();
        return new CdSolidObject.Player(new CdPlayerTouchResponse.Actor(s.motion(), h.tile(), h.priority(),
                s.timers().hurt(), s.rotationDistance()), h.slotId(), s.objectLinks().pushObject(), s.objectLinks().standObject());
    }

    private CdSolidObject.Globals solidGlobals() {
        var env = frame.environment(); var g = env.movement().globals();
        return new CdSolidObject.Globals(env.globals().invincible(), g.scoreChain(), g.primaryAngle(), g.secondaryAngle());
    }

    /**
     * ObjSpinTunnel (Spin Tunnel/Main (Tag).asm:7-188), shipped FixBugs=0 with no fixed alternative.
     * Take a fresh actual call cut at this physical visit. Init/Main is a pure prefix; no source owner or callback
     * runs between its complete preflight and object/player publication. The optional DrawObject and mandatory
     * reloaded CheckObjDespawn tail are later cuts: a failure there retains the already completed prefix.
     */

    /** One real physical body call, never an effect replay or a retained callback entitlement. */
    private void runCheckpoint(int whole) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inCheckpointDispatch || whole != dispatchWholeIndex) {
            throw new IllegalStateException("Checkpoint must run at its one current physical visit");
        }
        resetCheckpointDispatch();
        inCheckpointDispatch = true;
        try {
            var gates = frame.environment().movement().gates();
            checkpoint.run(whole, new CdCheckpoint.DespawnGlobals(gates.timeZone(),
                    gates.timeWarpDirection(), preScrollForeground.xWord()));
        } finally {
            resetCheckpointDispatch();
        }
    }

    private void resetCheckpointDispatch() {
        inCheckpointDispatch = false;
        checkpointGlobalStores = 0;
        checkpointActivationSource = null;
        checkpointActivationLevel = null;
        checkpointSaveCompleted = false;
        checkpointFmRequested = false;
        checkpointSavedAtCompletion = null;
    }

    /** Reuses the existing shared Solid flag, so every old callback and DrawObject rejects nested entry. */
    private void requireCheckpointCallbackCut() {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Checkpoint callbacks must be synchronous and non-reentrant");
        }
        requireCheckpointDispatch();
    }

    private CdObjectState requireCheckpointDispatch() {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (!inCheckpointDispatch || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX
                || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX) {
            throw new IllegalStateException("No actual current Checkpoint dispatch");
        }
        var live = placement.pool().state(dispatchWholeIndex);
        placement.pool().validateWrite(live);
        if (live.id() != CdCheckpoint.OBJECT_ID) {
            throw new IllegalStateException("Checkpoint callback requires the live bound ID13 source");
        }
        return live;
    }

    private CdLevelGlobals readCheckpointLevel() {
        requireCheckpointCallbackCut();
        return levelGlobals; // The actual existing level owner, never a projection or guessed Frame global.
    }

    /** ObjCheckpoint_Main:133-138: two instructions, two aggregate assignments, including same-value stores. */
    private void commitCheckpointGlobal(CdCheckpoint.GlobalCall call) {
        requireCheckpointCallbackCut();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual reached Checkpoint global store");
            var live = requireCheckpointDispatch();
            placement.pool().validateWrite(call.expectedSourceObject());
            requireCheckpointContactPrefix(live);
            if (!live.equals(call.expectedSourceObject()) || !levelGlobals.equals(call.expectedLevel())
                    || checkpointGlobalStores > 1 || checkpointSaveCompleted || checkpointFmRequested) {
                throw new IllegalStateException("Stale Checkpoint source/level/store cut");
            }
            var g = levelGlobals;
            CdLevelGlobals expected;
            if (checkpointGlobalStores == 0 && call.field() == CdCheckpoint.GlobalField.CHECKPOINT) {
                expected = new CdLevelGlobals(g.levelRestart(), g.updateHudTime(), live.subtype(), g.spawnMode(),
                        g.plcLoadFlags(), g.flowerCount(), g.fadeEnableDisplay(), g.powerup(), g.timeOver());
            } else if (checkpointGlobalStores == 1 && call.field() == CdCheckpoint.GlobalField.SPAWN_MODE
                    && live.equals(checkpointActivationSource) && g.equals(checkpointActivationLevel)) {
                expected = new CdLevelGlobals(g.levelRestart(), g.updateHudTime(), g.checkpoint(), 1,
                        g.plcLoadFlags(), g.flowerCount(), g.fadeEnableDisplay(), g.powerup(), g.timeOver());
            } else {
                throw new IllegalStateException("Checkpoint globals require checkpoint then spawnMode, once each");
            }
            if (!expected.equals(call.finalLevel())) {
                throw new IllegalArgumentException("Checkpoint native store may change only its addressed level byte");
            }
            // All preflight precedes this ONE native global publication; no external callback follows inside the guard.
            levelGlobals = call.finalLevel();
            checkpointActivationSource = live;
            checkpointActivationLevel = call.finalLevel();
            checkpointGlobalStores++;
        } finally {
            inSolidCommit = false;
        }
    }

    private static void requireCheckpointContactPrefix(CdObjectState source) {
        if (source.routine() != 2 || source.unsignedByte(CHECKPOINT_ACTIVE) != 1
                || source.unsignedByte(COLLISION_TYPE) != 0 || source.unsignedByte(COLLISION_STATUS) == 0) {
            throw new IllegalStateException("Checkpoint Main contact prefix has not reached its native global/save/FM cuts");
        }
    }

    private CdObjectState requireCheckpointActivation() {
        var live = requireCheckpointDispatch();
        requireCheckpointContactPrefix(live);
        if (checkpointGlobalStores != 2 || checkpointActivationSource == null || checkpointActivationLevel == null
                || !live.equals(checkpointActivationSource) || !levelGlobals.equals(checkpointActivationLevel)
                || levelGlobals.checkpoint() != live.subtype() || levelGlobals.spawnMode() != 1) {
            throw new IllegalStateException("Checkpoint save/FM requires both completed ordered actual global stores");
        }
        return live;
    }

    private CdCheckpointSaveData.State currentCheckpointSavedState() {
        requireCheckpointCallbackCut();
        requireCheckpointActivation();
        return checkpointState.saved();
    }

    /**
     * ObjCheckpoint_SaveData (Checkpoint/Main.asm:18-43): exactly one helper execution over the actual saved owner.
     * The local port checks reached source/store cursors, current source/global/Frame cut and saved expected-value CAS.
     * No source snapshot is assembled in advance. A missing reached byte/camera preserves each earlier publication.
     */
    private CdCheckpointSaveData.Result saveCheckpoint(CdCheckpoint.SaveCall call) {
        requireCheckpointCallbackCut();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual reached Checkpoint save call");
            var source = requireCheckpointActivation();
            placement.pool().validateWrite(call.expectedSourceObject());
            if (checkpointSaveCompleted || checkpointFmRequested || !source.equals(call.expectedSourceObject())
                    || !levelGlobals.equals(call.expectedLevel()) || !checkpointState.saved().equals(call.expectedSaved())) {
                throw new IllegalStateException("Stale Checkpoint save source/level/saved state");
            }
            // Values below guard callback consistency only. Each native input is still read fresh when reached.
            var actor = frame.state();
            var environment = frame.environment();
            var live = checkpointState.live();
            var expectedLevel = levelGlobals;
            class SavePort implements CdCheckpointSaveData.Port {
                private final CdCheckpointSaveData.Store[] stores = CdCheckpointSaveData.Store.values();
                private CdCheckpointSaveData.State published = call.expectedSaved();
                private int cursor;
                private boolean awaitingStore;
                private int reachedRaw;

                private void verify() {
                    verifyCheckpointSaveCut(source, expectedLevel, actor, environment, live);
                    if (!published.equals(checkpointState.saved())) {
                        throw new IllegalStateException("Actual Checkpoint saved owner differs from completed store prefix");
                    }
                }

                @Override public CdCheckpointSaveData.State currentState() {
                    verify();
                    return checkpointState.saved();
                }

                @Override public int read(CdCheckpointSaveData.Source selector) {
                    verify();
                    Objects.requireNonNull(selector, "reached Checkpoint source");
                    if (cursor >= stores.length || awaitingStore || selector != stores[cursor].source()) {
                        throw new IllegalStateException("Checkpoint source read differs from native store cursor");
                    }
                    int value = switch (selector) {
                        case SPAWN_MODE -> levelGlobals.spawnMode();
                        case PRIMARY_X -> frame.state().motion().centreX16_16() >>> 16;
                        case PRIMARY_Y -> frame.state().motion().centreY16_16() >>> 16;
                        case BOTTOM_BOUND -> frame.environment().movement().bounds().bottom();
                        case MINI_SONIC -> frame.environment().movement().gates().miniSonic();
                        // Each reached read goes to the SAME live value owner. WATER_ROUTINE is reached TWICE.
                        case WATER_ROUTINE -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.WATER_ROUTINE);
                        case WATER_HEIGHT2 -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.WATER_HEIGHT2);
                        case WATER_FULLSCREEN -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
                        case TIME_LONG -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.TIME_LONG);
                        // Actual pre-scroll Presentation words, never FG-only prepared camera, closed captures or DrawCopies.
                        case FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y -> checkpointReads.readCameraWord(selector);
                    };
                    verify(); // Also catches a swallowed Runtime reentry, stale source, changed globals or Frame writes.
                    requireCheckpointWidth(value, selector.width());
                    reachedRaw = value;
                    awaitingStore = true;
                    return value;
                }

                @Override public void commit(CdCheckpointSaveData.StoreCall storeCall) {
                    verify();
                    Objects.requireNonNull(storeCall, "reached Checkpoint saved store");
                    if (cursor >= stores.length || !awaitingStore || storeCall.store() != stores[cursor]
                            || !published.equals(storeCall.expectedState())) {
                        throw new IllegalStateException("Checkpoint saved store differs from native cursor or expected-state CAS");
                    }
                    int expectedRaw = reachedRaw;
                    if (stores[cursor] == CdCheckpointSaveData.Store.TIME_LONG
                            && Integer.compareUnsigned(expectedRaw, 0x00050000) >= 0) {
                        // Native CMP/BCS then MOVE.L clamp; live time remains untouched (Checkpoint/Main.asm:35-40).
                        expectedRaw = 0x00050000;
                    }
                    validateCheckpointSavedStore(storeCall, expectedRaw);
                    var next = new CheckpointOwnerState(checkpointState.live(), storeCall.finalState());
                    // ONE aggregate assignment per actual native saved store; every earlier known/unknown byte survives.
                    checkpointState = next;
                    published = storeCall.finalState();
                    cursor++;
                    awaitingStore = false;
                }

                private void finished(CdCheckpointSaveData.Result result) {
                    verify();
                    if (cursor != stores.length || awaitingStore || result == null
                            || !result.state().equals(published)
                            || !result.completedStores().equals(List.of(stores))) {
                        throw new IllegalStateException("Checkpoint helper did not complete exactly eighteen native stores");
                    }
                }
            }
            var port = new SavePort();
            var result = CdCheckpointSaveData.save(checkpointState.saved(), port);
            port.finished(result); // Helper has returned; no completion entitlement exists on any thrown prefix.
            checkpointSavedAtCompletion = result.state();
            checkpointSaveCompleted = true; // Only now may the body's next synchronous FM request be admitted.
            return result;
        } finally {
            inSolidCommit = false;
        }
    }

    /** No captured Frame partition or closed-cut API: consistency checks on the exact live immutable values only. */
    private void verifyCheckpointSaveCut(CdObjectState source, CdLevelGlobals level,
                                         CdPlayerBody.State actor, CdPlayerBody.Environment environment,
                                         CdCheckpointLiveAux.State live) {
        if (!inSolidCommit || !requireCheckpointActivation().equals(source) || !levelGlobals.equals(level)
                || !frame.state().equals(actor) || !frame.environment().equals(environment)
                || !checkpointState.live().equals(live)) {
            throw new IllegalStateException("Checkpoint save read/store changed the actual source/global/Frame/live cut");
        }
    }

    /**
     * Verify every one of the actual31 saved addresses, including knowledge. The destination word at1542
     * overlaps routine1543; this verifies addressed bytes, never invents a separate height/routine shadow.
     */
    private static void validateCheckpointSavedStore(CdCheckpointSaveData.StoreCall call, int expectedRaw) {
        if (call.rawValue() != expectedRaw) {
            throw new IllegalArgumentException("Checkpoint saved store does not contain its reached native source/clamp value");
        }
        requireCheckpointWidth(expectedRaw, call.store().width());
        int[] starts = {0xFF1523, 0xFF152A, 0xFF1530, 0xFF1591};
        int[] lengths = {5, 4, 21, 1};
        int first = call.store().nativeAddress();
        int end = first + call.store().width();
        for (int span = 0; span < starts.length; span++) {
            for (int address = starts[span]; address < starts[span] + lengths[span]; address++) {
                OptionalInt expected = address >= first && address < end
                        ? OptionalInt.of((expectedRaw >>> (8 * (end - address - 1))) & 0xFF)
                        : call.expectedState().knownByte(address);
                if (!expected.equals(call.finalState().knownByte(address))) {
                    throw new IllegalArgumentException("Checkpoint saved store changed an unaddressed value or knowledge byte");
                }
            }
        }
    }

    private static void requireCheckpointWidth(int value, int width) {
        if ((width == 1 && (value & ~0xFF) != 0) || (width == 2 && (value & ~0xFFFF) != 0)) {
            throw new IllegalArgumentException("Checkpoint bridge/source exceeds its actual unsigned native width");
        }
    }

    /** ObjCheckpoint_Main:140-143, exactly once after a fresh completed helper, before wrapper DrawObject. */
    private void playCheckpointFm(CdCheckpoint.FmSound sound) {
        requireCheckpointCallbackCut();
        inSolidCommit = true;
        try {
            requireCheckpointActivation();
            if (sound != CdCheckpoint.FmSound.FM_CHECKPOINT_AE || !checkpointSaveCompleted || checkpointFmRequested
                    || checkpointSavedAtCompletion == null || !checkpointSavedAtCompletion.equals(checkpointState.saved())) {
                throw new IllegalStateException("Checkpoint FM_AE requires this visit's successfully published native save");
            }
            appendSound(new CheckpointFm(sound)); // Typed ordered metadata; never an audio executor or a replay command.
            checkpointFmRequested = true;
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally {
            inSolidCommit = false;
        }
    }

    /**
     * ObjCheckpoint_Ball:154-155 supplies FULL wrapping parent+2E bits. Resolve only the addressed24bit bus byte.
     * Actual dynamic32..127 and reserved1..31 retain full64 bytes. No alignment, ID, parent lifetime or token gate.
     * Primary0 reads only Root's actually represented bytes; no fabricated whole-player RAM exists here.
     */
    private int readCheckpointParentByte(int rawAddressBits) {
        requireCheckpointCallbackCut();
        inSolidCommit = true;
        try {
            var source = requireCheckpointDispatch();
            var level = levelGlobals;
            var actor = frame.state();
            var environment = frame.environment();
            var owner = checkpointState;
            int bus = rawAddressBits & 0xFFFFFF;
            if (bus < 0xFFD000 || bus >= 0xFFF000) throw new CdCheckpoint.UnownedParentByte(rawAddressBits);
            int displacement = bus - 0xFFD000;
            int whole = displacement >>> 6;
            int offset = displacement & 63;
            int value;
            if (whole >= CdObjectPool.FIRST_WHOLE_INDEX) {
                value = Byte.toUnsignedInt(placement.pool().state(whole).copyBytes()[offset]);
            } else if (whole >= CdReservedObjectBank.FIRST_WHOLE_INDEX) {
                value = Byte.toUnsignedInt(reserved.state(whole).copyBytes()[offset]);
            } else {
                try {
                    value = checkpointReads.readPrimaryByte(offset);
                } catch (CdCheckpoint.UnownedParentByte unknown) {
                    // Root knows only the canonical primary offset; preserve the body's original raw32 request.
                    throw new CdCheckpoint.UnownedParentByte(rawAddressBits);
                }
            }
            if (!source.equals(requireCheckpointDispatch()) || !level.equals(levelGlobals)
                    || !actor.equals(frame.state()) || !environment.equals(frame.environment()) || !owner.equals(checkpointState)) {
                throw new IllegalStateException("Checkpoint parent-byte bridge changed its actual source/global/Frame cut");
            }
            requireCheckpointWidth(value, 1);
            return value;
        } finally {
            inSolidCommit = false;
        }
    }

    private void runSpinTunnelTag(int whole) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu || whole != dispatchWholeIndex) {
            throw new IllegalStateException("Spin tunnel tag requires the current synchronous physical dispatch");
        }
        var pool = placement.pool();
        var cut = pool.capture();
        var before = cut.slots().get(whole - CdObjectPool.FIRST_WHOLE_INDEX);
        var environment = frame.environment(); var gates = environment.movement().gates();
        var result = spinTunnelTag.step(before, frame.state().motion(), new CdSpinTunnelTag.Gates(
                gates.zoneAct() >>> 8, environment.globals().debugMode(), environment.globals().debugCheat(),
                environment.controls().rawHeld()));
        commitSpinTunnelTag(cut, result);
        if (result.drawObject()) drawDynamic(pool, whole);

        // DrawObject begins with BCLR7 (Object Functions.asm:137-138). NEVER reuse the staged Init image here.
        var afterDraw = pool.state(whole);
        pool.validateWrite(afterDraw);
        var tailGates = frame.environment().movement().gates();
        var tail = spinTunnelTag.despawn(afterDraw, new CdSpinTunnelTag.DespawnGlobals(
                tailGates.timeZone(), tailGates.timeWarpDirection(), preScrollForeground.xWord()));
        if (tail.deleteObject()) {
            if (tail.loadedClear().isPresent()) {
                var clear = tail.loadedClear().orElseThrow();
                // Actual 2+$2FC bank and placement's own index/period checks, BEFORE either BCLR or DeleteObject.
                // No bank snapshot/restore, shadow bytes, rounded-up table or guessed raw-RAM write.
                Objects.checkIndex(clear.index(), CdObjectPlacementRuntime.SAVED_BANK_BYTES);
                placement.savedFlag(clear.savedId(), clear.period());
                placement.bclrSavedFlag(clear.savedId(), clear.period(), CdObjectPlacementRuntime.SavedBit.LOADED);
            }
            pool.deleteObject(whole); // Full native 64-byte clear; savedID0 makes no bank access above.
        }
    }

    /** Entire same-owner 96-slot value CAS plus exact Init-only a0 and Frame expected-motion preflight. */
    private void commitSpinTunnelTag(CdObjectPool.Snapshot cut, CdSpinTunnelTag.Result result) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Spin tunnel tag commit must be synchronous and non-reentrant");
        }
        inSpinTunnelCommit = true;
        try {
            Objects.requireNonNull(result, "completed pure Spin tunnel tag prefix");
            var pool = placement.pool();
            pool.validateRestore(cut);
            var live = pool.state(dispatchWholeIndex);
            pool.validateWrite(live);
            pool.validateWrite(result.expectedObject());
            pool.validateWrite(result.object());
            var before = cut.slots().get(dispatchWholeIndex - CdObjectPool.FIRST_WHOLE_INDEX);
            if (live.id() != CdSpinTunnelTag.OBJECT_ID || before.id() != CdSpinTunnelTag.OBJECT_ID
                    || !live.identity().equals(result.object().identity())
                    || !before.equals(result.expectedObject()) || (before.routine() != 0 && before.routine() != 2)) {
                throw new IllegalStateException("Spin tunnel tag prefix differs from its actual live ID $34 routine0/2 call cut");
            }
            if (!pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("CD pool changed after the Spin tunnel tag call cut");
            }
            var expected = before;
            if (before.routine() == 0) {
                // Only ObjSpinTunnel_Init's executed stores (Tag:26-32); Main writes no a0 bytes.
                expected = before.addByte(ROUTINE, 2).moveByte(SPRITE_FLAGS, before.spriteFlags() | 4)
                        .moveWord(TILE, 0x0544)
                        .moveLong(MAPPINGS, objects.view(CdR11aObjectAssets.Table.POWERUP_MAPPINGS).range().nativeAddress())
                        .moveByte(MAP_FRAME, before.subtype()).addByte(MAP_FRAME, 1);
            }
            if (!expected.equals(result.object())) {
                throw new IllegalStateException("Spin tunnel tag result changes bytes outside its exact native Init prefix");
            }
            frame.validateObjectSpinTunnelResult(result.player());
            // Every owner/value/preservation check is complete. No callback between these two synchronous commits.
            commitTouchImages(cut, List.of(result.object()));
            frame.applyObjectSpinTunnelResult(result.player());
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSpinTunnelCommit = false; }
    }

    /**
     * ObjTimePostTimeIcon/ObjTimePost (Monitor and Time Post/Main.asm:64-173), native pin
     * 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff. Borrow the actual current Movement bytes,
     * time-attack byte, zone high byte and PRE-scroll camera. MMZ's global layer has no owner:
     * only the body decides whether its read is reached, after clearing nonzero contact.
     * No Monitor early layer guard, guessed layer zero, second store or completed-effect replay.
     */
    private void runTimePost(int whole) {
        requireTimePostDispatch(placement.pool().state(whole).identity());
        var environment = frame.environment(); var gates = environment.movement().gates();
        timePost.run(whole, new CdTimePost.Globals(gates.timeZone(), gates.timeWarpDirection(), preScrollForeground.xWord(),
                environment.globals().timeAttackMode(), gates.zoneAct() >>> 8, OptionalInt.empty()));
        // Direction and voice callbacks already completed synchronously. In particular, NEVER forward
        // the returned SubCpuRequest again. The body has also completed wrapper DrawObject/despawn.
    }

    /** Identity carries an index, not an owner token: validate the actual image in this ONE bound pool. */
    private CdObjectState requireTimePostDispatch(CdObjectState.Identity identity) {
        checkActive(Phase.DYNAMIC_SLOTS);
        Objects.requireNonNull(identity, "Time Post physical identity");
        if (identity.wholeIndex() != dispatchWholeIndex) {
            throw new IllegalStateException("Time Post callback must belong to the current physical dispatch");
        }
        var pool = placement.pool(); var live = pool.state(dispatchWholeIndex);
        pool.validateWrite(live);
        if (live.id() != CdTimePost.OBJECT_ID || live.subtype() < 8 || !live.identity().equals(identity)) {
            throw new IllegalStateException("Time Post callback requires the actual bound live ID $19 post image");
        }
        return live;
    }

    private void requireTimePostCallbackCut() {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Time Post callbacks must be synchronous and non-reentrant");
        }
    }

    /** ObjTimePost_Main:156-166 has already published spin60/routine4/contact0 and its saved BSET. */
    private void commitTimePostDirection(CdTimePost.DirectionCall call) {
        requireTimePostCallbackCut(); inTimePostDirection = true;
        try {
            Objects.requireNonNull(call, "Time Post direction call");
            var live = requireTimePostDispatch(call.identity());
            if (live.routine() != 4 || live.unsignedByte(TIME_POST_SPIN) != 60 || live.unsignedByte(COLLISION_STATUS) != 0
                    || (call.rawDirection() != 0xFF && (live.subtype() == 8 || call.rawDirection() != 1
                    || call.expectedRawDirection() != 0xFF))) {
                throw new IllegalStateException("Time Post direction requires its actual activation prefix and native store");
            }
            // Preflight the ACTUAL existing gate, then apply ONE byte store, including FF -> FF.
            // Future's second call expects the already committed FF, never the entry direction.
            frame.validateObjectTimePostDirection(call.expectedRawDirection(), call.rawDirection());
            frame.applyObjectTimePostDirection(call.expectedRawDirection(), call.rawDirection());
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inTimePostDirection = false; }
    }

    /** ObjTimePost_Main.PlaySound:169-170; mandatory before wrapper DrawObject:81 / despawn:82. */
    private void requestTimePostSubCpu(CdTimePost.SubCpuCommand command) {
        requireTimePostCallbackCut(); inTimePostSubCpu = true;
        try {
            var live = requireTimePostDispatch(placement.pool().state(dispatchWholeIndex).identity());
            boolean past = live.subtype() == 8;
            var expected = past ? CdTimePost.SubCpuCommand.SCMD_PASTSFX : CdTimePost.SubCpuCommand.SCMD_FUTURESFX;
            if (live.routine() != 4 || live.unsignedByte(TIME_POST_SPIN) != 60 || live.unsignedByte(COLLISION_STATUS) != 0
                    || command != expected || frame.environment().movement().gates().timeWarpDirection() != (past ? 0xFF : 1)) {
                throw new IllegalStateException("Time Post voice requires its live activation prefix and completed direction stores");
            }
            appendSound(new TimePostSubCpu(command));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inTimePostSubCpu = false; }
    }

    /**
     * ObjMonitor's exact current source cut. Authenticated assets remain USA R11A; another zone byte
     * is not admission to that zone's source/world. Zone6 explicitly needs an unowned global layer.
     * For every other byte the body short-circuits BEFORE that global read. Its mandatory layer
     * argument borrows the ACTUAL object's layer as an UNUSED value, never a synthetic global zero.
     */
    private void runMonitor(int whole) {
        var live = requireMonitorDispatch(placement.pool().state(whole).identity());
        var environment = frame.environment(); var gates = environment.movement().gates();
        int zone = gates.zoneAct() >>> 8;
        if (zone == 6) throw new UnsupportedMonitorBinding(whole);
        int unusedBorrowedObjectLayer = live.unsignedByte(LAYER);
        monitor.run(whole, new CdMonitor.Globals(gates.timeZone(), gates.timeWarpDirection(), preScrollForeground.xWord(),
                environment.globals().timeAttackMode(), zone, unusedBorrowedObjectLayer, ringGlobals.timeStopTimer()),
                new CdMonitor.SolidInputs(solidPlayer(), new CdSolidObject.Gates(environment.globals().debugMode(), gates.miniSonic()), solidGlobals()));
    }

    /** Current identity alone carries no pool token; always validate the actual bound live image too. */
    private CdObjectState requireMonitorDispatch(CdObjectState.Identity identity) {
        checkActive(Phase.DYNAMIC_SLOTS);
        Objects.requireNonNull(identity, "Monitor physical identity");
        if (identity.wholeIndex() != dispatchWholeIndex) {
            throw new IllegalStateException("Monitor callback must belong to the current physical dispatch");
        }
        var pool = placement.pool(); var live = pool.state(dispatchWholeIndex);
        pool.validateWrite(live);
        if (live.id() != CdMonitor.OBJECT_ID || live.subtype() >= 8 || !live.identity().equals(identity)) {
            throw new IllegalStateException("Monitor callback requires the actual bound live ID $19 image");
        }
        return live;
    }

    private void requireMonitorCallbackCut() {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Monitor callbacks must be synchronous and non-reentrant");
        }
    }

    /**
     * ObjMonitor_Solid:242-259 / Main:352-357. One actual plain Solid return at the live falling-byte0
     * Main cut. Validate the same-owner entire96-slot value cut, every returned image and Frame's
     * expected player/globals before S4 writes. Then publish S4, adopt S3 once, and forward nested
     * ContactFmSound in returned source order. No fabricated Result, effect replay or capture patch.
     */
    private void commitMonitorSolid(CdMonitor.SolidCall call) {
        requireMonitorCallbackCut(); inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "completed Monitor Solid call");
            var pool = placement.pool(); var cut = call.callCut(); var result = call.result();
            pool.validateRestore(cut); pool.validateWrite(result.object());
            requireMonitorDispatch(result.object().identity());
            var before = cut.slots().get(result.object().identity().dynamicIndex());
            if (before.id() != CdMonitor.OBJECT_ID || result.object().id() != CdMonitor.OBJECT_ID
                    || before.routine() != 2 || before.unsignedByte(ROUTINE_2) != 0 || (before.spriteFlags() & 0x80) == 0
                    || result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalStateException("Monitor Solid return differs from the live plain Solid Main cut");
            }
            if (!pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("CD pool changed after the Monitor Solid call cut");
            }
            var images = new ArrayList<CdObjectState>(1 + result.otherSlots().size());
            images.add(result.object()); images.addAll(result.otherSlots());
            for (var image : images) pool.validateWrite(image);
            frame.validateObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            // No callback between fully preflighted pool publication and the one Frame adoption.
            commitTouchImages(cut, images);
            frame.applyObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            for (var fm : result.fmRequests()) appendSound(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSolidCommit = false; }
    }

    /**
     * ObjGetFloorDist (Collision Check.asm:157-179), reached by ObjAnton_Place/Main
     * (Anton/Main.asm:54-94), N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
     * Validate the complete actual same-owner 96-slot/source cut and expected Frame byte
     * before each synchronous store, including same-value stores. The body has already
     * published its source prefix; completed Result effects never request another store.
     * The existing mutual callback guard covers this port too. Root owns native call
     * authenticity; plain constructed call values are not a publication capability.
     */
    private void commitAntonPrimaryAngle(CdAnton.PrimaryAngleCall call) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Anton primary-angle commit must be synchronous and non-reentrant");
        }
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "reached Anton primary-angle call");
            var pool = placement.pool(); var cut = call.callCut(); var source = call.expectedSource();
            validateDynamicCapture(cut);
            pool.validateWrite(source);
            var identity = source.identity();
            if (dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                    || identity.wholeIndex() != dispatchWholeIndex) {
                throw new IllegalStateException("Anton angle call must belong to the current physical dispatch");
            }
            var live = pool.state(dispatchWholeIndex);
            if (!live.identity().equals(identity) || live.id() != CdAnton.OBJECT_ID
                    || (live.routine() != 0 && live.routine() != 2 && live.routine() != 4)
                    || !live.equals(source) || !cut.slots().get(identity.dynamicIndex()).equals(source)
                    || !pool.capture().slots().equals(cut.slots())
                    || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle()) {
                throw new IllegalStateException("Anton angle call differs from its actual current full-pool/source/Frame cut");
            }
            // Full preflight precedes the only publication; no pool image or duplicate angle owner is adopted.
            frame.applyObjectPrimaryAngle(call.value());
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally {
            inSolidCommit = false;
        }
    }

    /** ObjGetFloorDist:157-179 returns after the raw motion publication and before landing correction. */
    private void commitMonitorPrimaryAngle(int expectedPrimaryAngle, int primaryAngle) {
        requireMonitorCallbackCut(); inMonitorAngle = true;
        try {
            var live = requireMonitorDispatch(placement.pool().state(dispatchWholeIndex).identity());
            if (live.routine() != 2 || live.unsignedByte(ROUTINE_2) == 0 || (live.spriteFlags() & 0x80) == 0
                    || (expectedPrimaryAngle & ~0xFF) != 0
                    || frame.environment().movement().globals().primaryAngle() != expectedPrimaryAngle) {
                throw new IllegalStateException("Monitor floor angle requires its actual current falling/expected-byte cut");
            }
            frame.applyObjectPrimaryAngle(primaryAngle); // Exactly once, including same-value native stores.
            checkActive(Phase.DYNAMIC_SLOTS); // The external legacy angle port is deliberately not called too.
        } finally { inMonitorAngle = false; }
    }

    /** ObjMonitor_Break:381-385 calls FM_DESTROY BEFORE routine+4/collision0 or any child/saved write. */
    private void playMonitorFm(CdMonitor.FmSound sound) {
        requireMonitorCallbackCut(); inMonitorFm = true;
        try {
            var live = requireMonitorDispatch(placement.pool().state(dispatchWholeIndex).identity());
            if (live.routine() != 4) throw new IllegalStateException("Monitor FM requires its live Break entry before body writes");
            appendSound(new MonitorFm(sound));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inMonitorFm = false; }
    }

    /**
     * .GiveItem/.GiveSpeedShoes (Main:480-482,570-589). Body's routine4/word29 prefix is already
     * live; never repeat it. Project only current Frame values, execute the shared pure five-store
     * primitive, preflight/apply once BEFORE the ordered SubCPU ledger, then return its completed
     * semantic commands. No ring/contact/level/powerup store, second timer/physics bank or replay.
     */
    private CdMonitorItem.AwardResult awardMonitorItem(CdMonitorItem.AwardCall call) {
        requireMonitorCallbackCut(); inMonitorAward = true;
        try {
            Objects.requireNonNull(call, "Monitor item award call");
            if (call.identity().wholeIndex() != dispatchWholeIndex) {
                throw new IllegalStateException("Monitor item award must belong to the current physical dispatch");
            }
            var pool = placement.pool(); var live = pool.state(dispatchWholeIndex);
            pool.validateWrite(live);
            if (live.id() != CdMonitorItem.OBJECT_ID || !live.identity().equals(call.identity())
                    || call.rawAnimation() != 4 || live.unsignedByte(ANIMATION) != 4
                    || live.routine() != 4 || live.word(MONITOR_ITEM_DELETE) != 29) {
                throw new IllegalStateException("Monitor item award requires the live kind4/routine4/word29 source prefix");
            }
            var movement = frame.environment().movement();
            var before = new CdPlayerSpeedShoesReward.State(movement.gates().speedShoes(),
                    frame.state().timers().speedShoes(), movement.physics());
            var result = CdPlayerSpeedShoesReward.give(before, assets.identity().variant(), movement.gates().timeZone());
            var completed = result.commands().stream().map(command -> switch (command) {
                case SCMD_FADEPCM -> CdMonitorItem.AwardSound.SCMD_FADEPCM;
                case SCMD_SHOESMUS -> CdMonitorItem.AwardSound.SCMD_SHOESMUS;
            }).toList();
            var observation = new CdMonitorItem.AwardResult(completed);
            frame.validateObjectSpeedShoesReward(before, result);
            frame.applyObjectSpeedShoesReward(before, result);
            for (var command : result.commands()) appendSound(new MonitorItemSubCpu(command));
            checkActive(Phase.DYNAMIC_SLOTS);
            return observation;
        } finally { inMonitorAward = false; }
    }

    /** Complete same-owner ordered 96-image preflight, shared by composite restore and Door call cuts. */
    private void validateDynamicCapture(CdObjectPool.Snapshot cut) {
        var pool = placement.pool(); pool.validateRestore(Objects.requireNonNull(cut, "actual dynamic cut"));
        if (cut.slots().size() != CdObjectPool.DYNAMIC_COUNT) {
            throw new IllegalArgumentException("CD dynamic cut must contain all 96 physical images");
        }
        for (int index = 0; index < CdObjectPool.DYNAMIC_COUNT; index++) {
            var image = cut.slots().get(index); pool.validateWrite(image);
            if (image.identity().wholeIndex() != CdObjectPool.FIRST_WHOLE_INDEX + index
                    || image.copyBytes().length != CdObjectState.BYTE_SIZE) {
                throw new IllegalArgumentException("CD dynamic cut must retain complete physical order and image width");
            }
        }
    }

    private CdSolidObject.Gates solidGates() {
        var environment = frame.environment();
        return new CdSolidObject.Gates(environment.globals().debugMode(), environment.movement().gates().miniSonic());
    }

    private void requireTunnelDoorCallbackCut() {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Horizontal Door callbacks must be synchronous and non-reentrant");
        }
    }

    /** Physical identity grants no owner authority; read the actual live ID $0D from this runtime's pool. */
    private CdObjectState requireTunnelDoorDispatch(CdObjectState.Identity identity) {
        checkActive(Phase.DYNAMIC_SLOTS); Objects.requireNonNull(identity, "horizontal Door physical identity");
        if (identity.wholeIndex() != dispatchWholeIndex) {
            throw new IllegalStateException("Horizontal Door callback must belong to the current physical dispatch");
        }
        var pool = placement.pool(); var live = pool.state(dispatchWholeIndex); pool.validateWrite(live);
        if (live.id() != CdTunnelDoor.OBJECT_ID || !live.identity().equals(identity)) {
            throw new IllegalStateException("Horizontal Door callback requires the actual bound live ID $0D image");
        }
        return live;
    }

    /**
     * Pure whole-pool same-owner/value CAS against the ONE supplied callCut. No second capture, derived
     * cut, revision or retained invocation. Reconstructed/restored equal values retain existing value semantics.
     * SECONDARY compares all 64 bytes of actual reserved whole1, including ID0's unnamed bytes and aliases.
     */
    private CdTunnelDoor.LiveSolid validateTunnelDoorSolidCut(CdTunnelDoor.SolidCut cut) {
        Objects.requireNonNull(cut, "actual horizontal Door Solid cut");
        var live = requireTunnelDoorDispatch(cut.objectIdentity());
        validateDynamicCapture(cut.callCut());
        var before = cut.callCut().slots().get(cut.objectIdentity().dynamicIndex());
        if (before.id() != CdTunnelDoor.OBJECT_ID || !before.identity().equals(live.identity())) {
            throw new IllegalStateException("Horizontal Door call cut differs from its actual physical ID $0D");
        }
        var pool = placement.pool();
        for (var expected : cut.callCut().slots()) {
            if (!pool.state(expected.identity().wholeIndex()).equals(expected)) {
                throw new IllegalStateException("CD pool changed after the horizontal Door Solid call cut");
            }
        }
        var primary = solidPlayer(); var gates = solidGates(); var globals = solidGlobals();
        if (!primary.equals(cut.expectedPrimary()) || !gates.equals(cut.expectedGates())
                || !globals.equals(cut.expectedGlobals())) {
            throw new IllegalStateException("Horizontal Door primary/gates/globals changed after its call cut");
        }
        if (cut.participant() == CdTunnelDoor.Participant.SECONDARY) {
            var actual = reserved.state(CdTunnelDoor.SECONDARY_WHOLE_INDEX);
            if (!actual.equals(cut.secondary().orElseThrow())) {
                throw new IllegalStateException("Horizontal Door actual reserved whole1 changed after its call cut");
            }
            for (int offset = 0; offset < CdReservedObjectBank.BYTE_SIZE; offset++) {
                if (actual.byteAt(offset) != 0) {
                    throw new IllegalStateException("Horizontal Door secondary cut is outside its admitted ClearObjects image");
                }
            }
        }
        return new CdTunnelDoor.LiveSolid(primary, gates, globals); // ACTUAL stores; nothing has been published.
    }

    private CdTunnelDoor.LiveSolid preflightTunnelDoorSolid(CdTunnelDoor.SolidCut cut) {
        requireTunnelDoorCallbackCut(); inHorizontalTunnelDoorSolid = true;
        try { return validateTunnelDoorSolidCut(cut); }
        finally { inHorizontalTunnelDoorSolid = false; }
    }

    /**
     * ObjTunnelDoor_ChkPlayer .Solid (Spin Tunnel/Main (Door).asm:46-50), Main primary then secondary (:73-77).
     * Repeat ALL phase/value/owner checks, preflight EVERY S4 image and PRIMARY Frame patch before any write.
     * Publish a0/otherSlots through the existing pool, then exactly one existing S3 Frame patch for PRIMARY,
     * then forward shared FM in source order. SECONDARY commits only its object stores, never a Frame actor.
     * Earlier completed prefixes survive failure; no callback dispatch, fabricated Result or effect replay.
     */
    private CdTunnelDoor.LiveSolid commitTunnelDoorSolid(CdTunnelDoor.SolidCall call) {
        requireTunnelDoorCallbackCut(); inHorizontalTunnelDoorSolid = true;
        try {
            Objects.requireNonNull(call, "completed horizontal Door Solid call");
            var cut = call.cut(); validateTunnelDoorSolidCut(cut);
            var result = call.result(); var pool = placement.pool();
            pool.validateWrite(result.object());
            if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || result.object().id() != CdTunnelDoor.OBJECT_ID
                    || !result.object().identity().equals(cut.objectIdentity())) {
                throw new IllegalStateException("Horizontal Door return differs from its actual TOP_SOLID_OBJECT call");
            }
            var images = new ArrayList<CdObjectState>(1 + result.otherSlots().size());
            images.add(result.object()); images.addAll(result.otherSlots());
            for (var image : images) {
                pool.validateWrite(image);
                if (image.copyBytes().length != CdObjectState.BYTE_SIZE) {
                    throw new IllegalArgumentException("Horizontal Door result must retain a complete dynamic image");
                }
            }
            if (cut.participant() == CdTunnelDoor.Participant.PRIMARY) {
                frame.validateObjectSolidResult(cut.expectedPrimary(), cut.expectedGlobals(), result);
            } else {
                var actual = reserved.state(CdTunnelDoor.SECONDARY_WHOLE_INDEX);
                // Validate the completed projection AGAINST REAL storage, without inventing a zero Player/image.
                requireTunnelDoorSecondaryProjection(actual, result.player());
                CdTunnelDoor.requireInactiveSecondaryReturn(dispatchWholeIndex, result, result.player(), cut.expectedGlobals());
            }
            // All checks are complete. The pool's writes invoke no callbacks between S4 and the one S3 patch.
            for (var image : images) pool.write(image);
            if (cut.participant() == CdTunnelDoor.Participant.PRIMARY) {
                frame.applyObjectSolidResult(cut.expectedPrimary(), cut.expectedGlobals(), result);
            }
            for (var fm : result.fmRequests()) appendSound(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
            checkActive(Phase.DYNAMIC_SLOTS);
            return new CdTunnelDoor.LiveSolid(solidPlayer(), solidGates(), solidGlobals()); // ACTUAL post-commit stores.
        } finally { inHorizontalTunnelDoorSolid = false; }
    }

    /**
     * Storage preservation only: verify every field of the shared secondary return against the actual whole1
     * image (_Definitions.inc:oPlayer*, :73-95). No Player creation, native arithmetic or second actor is added.
     * The body predicate separately rejects every native player/global/other-slot store, even same-value ones,
     * and every alias-sensitive helper/probe/FM/Kill call before this runtime's first S4 write.
     */
    private static void requireTunnelDoorSecondaryProjection(CdReservedObjectBank.Image image, CdSolidObject.Player player) {
        var actor = player.actor(); var motion = actor.motion();
        if (player.slotId() != image.byteAt(0) || player.pushObject() != image.byteAt(0x20)
                || player.standObject() != image.byteAt(0x3D) || actor.tile() != image.wordAt(0x02)
                || actor.priority() != image.byteAt(0x18) || actor.hurt() != image.wordAt(0x30)
                || actor.rotationDistance() != image.byteAt(0x39)
                || motion.centreX16_16() != image.longBitsAt(0x08) || motion.centreY16_16() != image.longBitsAt(0x0C)
                || motion.xVelocity() != (short) image.wordAt(0x10) || motion.yVelocity() != (short) image.wordAt(0x12)
                || motion.groundVelocity() != (short) image.wordAt(0x14) || motion.routine() != image.byteAt(0x24)
                || motion.angle() != image.byteAt(0x26) || motion.flags() != image.byteAt(0x22)
                || motion.spriteFlags() != image.byteAt(0x01) || motion.xRadius() != image.byteAt(0x17)
                || motion.yRadius() != image.byteAt(0x16) || motion.animation() != image.byteAt(0x1C)
                || motion.previousAnimation() != image.byteAt(0x1D) || motion.playerJump() != image.byteAt(0x3C)
                || motion.charge() != image.byteAt(0x2A) || motion.moveLock() != (short) image.wordAt(0x3E)
                || motion.stick() != image.byteAt(0x38) || motion.playerControl() != image.byteAt(0x2C)
                || motion.previousPrimaryAngle() != image.byteAt(0x36) || motion.previousSecondaryAngle() != image.byteAt(0x37)) {
            throw new IllegalStateException("Horizontal Door secondary return differs from the actual reserved projection");
        }
    }

    /** Native successful ID0B creation has already committed ID/X/Y/Y-4 before this FM_A4 call (:23-40). */
    private void playTunnelDoorFm(CdTunnelDoor.FmSound sound) {
        requireTunnelDoorCallbackCut(); inHorizontalTunnelDoorFm = true;
        try {
            var live = requireTunnelDoorDispatch(placement.pool().state(dispatchWholeIndex).identity());
            if (live.routine() != 4 || live.subtype() != 0) {
                throw new IllegalStateException("Horizontal Door FM requires its completed subtype0 opening prefix");
            }
            appendSound(new TunnelDoorFm(sound));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inHorizontalTunnelDoorFm = false; }
    }

    /** Actual current physical DoorV identity at its synchronous native callback cut. */
    private CdObjectState requireTunnelDoorVerticalDispatch(CdObjectState.Identity identity) {
        checkActive(Phase.DYNAMIC_SLOTS);
        Objects.requireNonNull(identity, "vertical Tunnel Door physical identity");
        if (identity.wholeIndex() != dispatchWholeIndex) {
            throw new IllegalStateException("DoorV callback must belong to the current physical dispatch");
        }
        var pool = placement.pool(); var live = pool.state(dispatchWholeIndex);
        pool.validateWrite(live);
        if (live.id() != CdTunnelDoorVertical.OBJECT_ID || !live.identity().equals(identity)) {
            throw new IllegalStateException("DoorV callback requires the actual bound live ID $35 image");
        }
        return live;
    }

    /**
     * ObjTunnelDoorV_SolidObj (Tunnel Door/Main.asm:26-29), scddisasm 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
     * The body has published its native prefix and called the existing pure Solid owner once. Preflight its
     * same-owner entire 96-slot value cut, every returned image and the whole expected player/globals before
     * any pool or Frame write. Publish S4 first, adopt S3 once, then append shared FM in returned order.
     * No body effect, call observation, player/global capture or fabricated Solid result is replayed.
     */
    private void commitTunnelDoorVerticalSolid(CdTunnelDoorVertical.SolidCall call) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("DoorV Solid commit must be synchronous and non-reentrant");
        }
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "completed vertical Tunnel Door Solid call");
            var result = call.result(); var pool = placement.pool(); var cut = call.callCut();
            pool.validateRestore(cut);
            pool.validateWrite(result.object());
            requireTunnelDoorVerticalDispatch(result.object().identity());
            var before = cut.slots().get(result.object().identity().dynamicIndex());
            if (before.id() != CdTunnelDoorVertical.OBJECT_ID || result.object().id() != CdTunnelDoorVertical.OBJECT_ID
                    || result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalStateException("DoorV Solid return differs from its actual live plain Solid call cut");
            }
            if (!pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("CD pool changed after the DoorV Solid call cut");
            }
            var images = new ArrayList<CdObjectState>(1 + result.otherSlots().size());
            images.add(result.object()); images.addAll(result.otherSlots());
            for (var image : images) pool.validateWrite(image);
            frame.validateObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            // All preflight is complete; there is no callback between pool publication and Frame adoption.
            commitTouchImages(cut, images);
            frame.applyObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            for (var fm : result.fmRequests()) appendSound(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSolidCommit = false; }
    }

    /**
     * Main's CLR.W timer/routine4 prefix is already live; frame1 and Solid follow this return (Main.asm:61-71).
     * Source FM_A4 is metadata only. No player, object, draw, service, source or body work occurs here.
     */
    private void playTunnelDoorVerticalFm(CdTunnelDoorVertical.FmSound sound) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("DoorV FM call must follow its completed range prefix synchronously");
        }
        inTunnelDoorFm = true;
        try {
            var live = requireTunnelDoorVerticalDispatch(placement.pool().state(dispatchWholeIndex).identity());
            if (live.routine() != 4 || live.word(TUNNEL_DOOR_TIMER) != 0) {
                throw new IllegalStateException("DoorV FM call requires its already published word/routine prefix");
            }
            appendSound(new TunnelDoorVerticalFm(sound));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inTunnelDoorFm = false; }
    }

    /** Actual current physical Spring identity, never an ID-selected captured active list or another pool. */
    private CdObjectState requireSpringDispatch(CdObjectState.Identity identity) {
        checkActive(Phase.DYNAMIC_SLOTS);
        Objects.requireNonNull(identity, "Spring physical identity");
        if (identity.wholeIndex() != dispatchWholeIndex) {
            throw new IllegalStateException("Spring callback must belong to the current physical dispatch");
        }
        var pool = placement.pool(); var live = pool.state(dispatchWholeIndex);
        pool.validateWrite(live);
        if (live.id() != CdSpring.OBJECT_ID || !live.identity().equals(identity)) {
            throw new IllegalStateException("Spring callback requires the actual bound live ID $0A image");
        }
        return live;
    }

    /**
     * One completed real Solid return (Spring/Main.asm:248-252,296-300,362-368,416-423). Validate the whole actual
     * same-owner call cut and every image, then Frame's exact value sets, before publishing a0/a2 and S3.
     * Full-pool CAS also protects unrelated slots; no capture is restored as a patch. The existing shared
     * Solid owner supplies both collision buffers and completed FM_CHARGESTOP calls, forwarded only here.
     */
    private void commitSpringSolid(CdSpring.SolidCall call) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Spring Solid commit must be synchronous and non-reentrant");
        }
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "completed Spring Solid call");
            var result = call.result(); var pool = placement.pool(); var cut = call.callCut();
            pool.validateRestore(cut);
            requireSpringDispatch(result.object().identity());
            var before = cut.slots().get(result.object().identity().dynamicIndex());
            var expectedEntry = switch (before.routine()) {
                case 2, 8, 20 -> CdSolidObject.Entry.SOLID_OBJECT;
                case 14 -> CdSolidObject.Entry.BTM_SOLID_OBJECT;
                default -> throw new IllegalStateException("Spring Solid return lacks its native Main call cut");
            };
            if (before.id() != CdSpring.OBJECT_ID || result.object().id() != CdSpring.OBJECT_ID
                    || result.entry() != expectedEntry) {
                throw new IllegalStateException("Spring Solid return differs from its current native call entry");
            }
            if (!pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("CD pool changed after the Spring Solid call cut");
            }
            var images = new ArrayList<CdObjectState>(1 + result.otherSlots().size());
            images.add(result.object()); images.addAll(result.otherSlots());
            for (var image : images) pool.validateWrite(image);
            frame.validateObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            commitTouchImages(cut, images);
            frame.applyObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            for (var fm : result.fmRequests()) appendSound(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSolidCommit = false; }
    }

    /** Spring's native object-action prefix is already live; this distinct Frame value-CAS adopts S3 once. */
    private void commitSpringLaunch(CdSpring.LaunchCall call) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Spring launch must be synchronous and non-reentrant");
        }
        inSpringLaunch = true;
        try {
            Objects.requireNonNull(call, "completed Spring launch");
            var live = requireSpringDispatch(call.identity());
            if (live.routine() != 4 && live.routine() != 10 && live.routine() != 16 && live.routine() != 22) {
                throw new IllegalStateException("Spring launch requires its already committed native action prefix");
            }
            frame.validateObjectSpringLaunch(call);
            frame.applyObjectSpringLaunch(call);
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSpringLaunch = false; }
    }

    /** Dedicated PlayFMSound cut after successful launch, before Down/diagonal animation and wrapper DrawObject. */
    private void playSpringFm(CdSpring.FmSound sound) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Spring FM call must follow its completed launch synchronously");
        }
        requireSpringDispatch(placement.pool().state(dispatchWholeIndex).identity());
        appendSound(new SpringFm(sound));
    }

    /** All three synchronous Spikes publications share the EXISTING guard with every sibling callback/Draw. */
    private void requireSpikesCallbackCut() {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Spikes commit must be synchronous and non-reentrant");
        }
    }

    private CdObjectState requireSpikesDispatch(CdObjectState.Identity identity) {
        Objects.requireNonNull(identity, "current Spikes physical identity");
        if (identity.wholeIndex() != dispatchWholeIndex) {
            throw new IllegalStateException("Spikes callback must belong to the current physical dispatch");
        }
        var live = placement.pool().state(dispatchWholeIndex);
        placement.pool().validateWrite(live);
        if (!live.identity().equals(identity) || live.id() != CdSpikes.OBJECT_ID || live.routine() != 2) {
            throw new IllegalStateException("Spikes callback requires the live ID $26 Main image");
        }
        return live;
    }

    /** Native .ChkStand reached guards (:57-69), read AFTER Solid. No second outer absDY test here. */
    private void requireSpikesHurtGates(CdObjectState live) {
        var actor = solidPlayer().actor();
        if ((live.spriteFlags() & 2) != 0 || (live.flags() & 8) == 0
                || contactGlobals.timeWarp() != 0 || frame.environment().globals().invincible() != 0
                || actor.motion().routine() >= 4 || actor.hurt() != 0) {
            throw new IllegalStateException("Spikes prefix/Hurt requires current upright standing and native player gates");
        }
    }

    private CdPlayerTouchResponse.PlayerGlobals spikesPlayerGlobals() {
        var environment = frame.environment(); var body = environment.globals(); var movement = environment.movement();
        return new CdPlayerTouchResponse.PlayerGlobals(contactGlobals.timeWarp(), body.invincible(),
                contactGlobals.shield(), contactGlobals.combineRing(), body.debugCheat(), body.debugMode(),
                movement.globals().scoreChain(), movement.gates().miniSonic());
    }

    private CdPlayerTouchResponse.WorldGlobals spikesWorldGlobals() {
        return new CdPlayerTouchResponse.WorldGlobals(ringGlobals.rings(), ringGlobals.lives(), ringGlobals.updateHudLives(),
                scoreGlobals.score(), scoreGlobals.nextLifeScore(), scoreGlobals.updateHudScore());
    }

    /** Complete same-owner images AND all live values, including untouched/ID-zero/tail bytes. No new capture. */
    private void validateSpikesPoolCut(CdObjectPool.Snapshot cut) {
        validateDynamicCapture(cut);
        for (var expected : cut.slots()) {
            if (!placement.pool().state(expected.identity().wholeIndex()).equals(expected)) {
                throw new IllegalStateException("CD pool changed after the Spikes native call cut");
            }
        }
    }

    /** Final distinct physical images; an otherSlot/child cannot alias a0 or another output. Pure preflight. */
    private void validateSpikesImages(CdObjectPool.Snapshot cut, List<CdObjectState> images) {
        var pool = placement.pool(); var seen = new boolean[CdObjectPool.DYNAMIC_COUNT];
        for (var image : images) {
            pool.validateWrite(image);
            int index = image.identity().dynamicIndex();
            if (seen[index] || !image.identity().equals(cut.slots().get(index).identity())
                    || image.copyBytes().length != CdObjectState.BYTE_SIZE) {
                throw new IllegalArgumentException("Spikes outputs require distinct complete same-owner physical images");
            }
            seen[index] = true;
        }
    }

    /**
     * Cut1: ObjSpikes_Main:38-55, native SUB.W/BCC/NEG.W absDY on the PRE-Solid player only.
     * No incoming bit7 requirement: an offscreen SolidObject/GetOff is a real call. Every pool/result/Frame
     * and FM preflight precedes S4. Publish actual images, Frame once and ordered shared FM once.
     */
    private void commitSpikesSolid(CdSpikes.SolidCall call) {
        requireSpikesCallbackCut(); inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "completed Spikes Solid call");
            var cut = call.callCut(); var result = call.result();
            validateSpikesPoolCut(cut);
            var live = requireSpikesDispatch(result.object().identity());
            var before = cut.slots().get(live.identity().dynamicIndex());
            if (!live.equals(before) || result.object().id() != CdSpikes.OBJECT_ID || result.object().routine() != 2
                    || result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalStateException("Spikes Solid return differs from the live native Main call");
            }
            int playerY = call.expectedPlayer().actor().motion().centreY16_16() >>> 16;
            int distance = (playerY - before.centreY()) & 0xFFFF;
            if (playerY < before.centreY()) distance = (-distance) & 0xFFFF;
            if (distance >= 0x20) throw new IllegalStateException("Spikes Solid lies outside the native pre-call absDY branch");
            var images = new ArrayList<CdObjectState>(1 + result.otherSlots().size());
            images.add(result.object()); images.addAll(result.otherSlots());
            validateSpikesImages(cut, images);
            frame.validateObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            var ledger = new ArrayList<>(sounds);
            for (var fm : result.fmRequests()) ledger.add(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
            var nextSounds = List.copyOf(ledger);
            // ALL preflight is complete. No callback or new validation authority intervenes between these writes.
            commitTouchImages(cut, images);
            frame.applyObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            sounds = nextSounds;
        } finally { inSolidCommit = false; }
    }

    /**
     * Cut2: ObjSpikes_Main:70-75 is a standalone LONG-Y store, including fractional carry/borrow.
     * It changes no pool/contact/world/sound state. A later direct Hurt failure retains this actual prefix.
     */
    private void commitSpikesYRewind(CdSpikes.YRewindCall call) {
        requireSpikesCallbackCut(); inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "native Spikes Y LONG prefix");
            var live = requireSpikesDispatch(call.sourceIdentity());
            requireSpikesHurtGates(live);
            frame.validateObjectPreHurtYRewind(call.expectedActor(), call.finalYBits());
            frame.applyObjectPreHurtYRewind(call.expectedActor(), call.finalYBits());
        } finally { inSolidCommit = false; }
    }

    /**
     * Cut3: fresh actual source/capture AFTER the Y prefix, then direct HurtPlayer (:76), not the touch scanner.
     * PP Player Object Collision.asm:229-298 and Player_ResetOnFloor (Sonic/Main.asm:2295-2330) own all values.
     * Plain records convey values, not tokens or commands. Do not rerun Hurt/Reset/Kill, dispatch the child or
     * replay observations. Every cut, image, contact/world, call/FM and Frame check finishes BEFORE S4.
     */
    private void commitSpikesHurt(CdSpikes.HurtCall call) {
        requireSpikesCallbackCut(); inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "completed direct Spikes Hurt call");
            var cut = call.callCut(); var result = call.result();
            validateSpikesPoolCut(cut); // Required even when changedSlots is EMPTY.
            var live = requireSpikesDispatch(call.sourceA2().identity());
            requireSpikesHurtGates(live);
            var expectedPlayer = call.expectedPlayer(); var expectedWorld = call.expectedWorld();
            if (!live.equals(call.sourceA2()) || !live.equals(cut.slots().get(live.identity().dynamicIndex()))
                    || !call.expectedActor().equals(solidPlayer().actor())
                    || !expectedPlayer.equals(spikesPlayerGlobals()) || !expectedWorld.equals(spikesWorldGlobals())) {
                throw new IllegalStateException("Stale Spikes direct Hurt source/player/contact/world call cut");
            }
            if (!result.world().equals(expectedWorld)) {
                throw new IllegalArgumentException("Direct HurtPlayer cannot change rings/lives/HUD/score world values");
            }
            boolean find = expectedPlayer.shield() == 0 && expectedWorld.rings() != 0;
            boolean kill = expectedPlayer.shield() == 0 && expectedWorld.rings() == 0 && expectedPlayer.debugCheat() == 0;
            var exit = kill ? (expectedPlayer.debugMode() == 0 ? CdPlayerTouchResponse.Exit.KILL_PLAYER
                    : CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) : CdPlayerTouchResponse.Exit.HURT;
            if (result.exit() != exit) throw new IllegalArgumentException("Direct HurtPlayer exit disagrees with the current native branch");
            var player = result.player();
            int shield = exit == CdPlayerTouchResponse.Exit.HURT ? expectedPlayer.shield() & ~1 : expectedPlayer.shield();
            int combine = exit == CdPlayerTouchResponse.Exit.HURT && (expectedPlayer.shield() & 1) == 0 ? 0 : expectedPlayer.combineRing();
            if (player.timeWarp() != expectedPlayer.timeWarp() || player.shield() != shield || player.combineRing() != combine
                    || player.debugCheat() != expectedPlayer.debugCheat() || player.debugMode() != expectedPlayer.debugMode()
                    || player.miniSonic() != expectedPlayer.miniSonic()) {
                throw new IllegalArgumentException("Direct HurtPlayer changes an unowned global/contact value");
            }
            // Precreate the complete immutable Root contact value before ANY response writes.
            var nextContact = new CdPlayerBody.ContactGlobals(expectedPlayer.timeWarp(), shield, combine);
            var firstFree = OptionalInt.empty();
            if (find) {
                // FindObjSlot (Object Spawner.asm:329-340) scans ALL96 ID bytes in physical order, without clearing.
                for (var image : cut.slots()) if (image.id() == 0) { firstFree = OptionalInt.of(image.identity().wholeIndex()); break; }
            }
            var expectedCalls = new ArrayList<CdPlayerTouchResponse.Call>();
            if (find) expectedCalls.add(new CdPlayerTouchResponse.FindObjSlot(firstFree));
            if (exit != CdPlayerTouchResponse.Exit.KILL_PLAYER_DEBUG_NO_WRITE) {
                expectedCalls.add(new CdPlayerTouchResponse.ResetOnFloor());
                if ((call.expectedActor().motion().flags() & 4) != 0) {
                    expectedCalls.add(new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_CHARGESTOP));
                }
                if (exit == CdPlayerTouchResponse.Exit.KILL_PLAYER) {
                    expectedCalls.add(new CdPlayerTouchResponse.PlayFm(CdPlayerTouchResponse.FmSound.FM_HURT));
                }
            }
            // Exact observations forbid AddPoints/SubCPU/FM_DESTROY, duplicate allocation/reset, wrong FM/order.
            if (!result.calls().equals(expectedCalls)) {
                throw new IllegalArgumentException("Direct HurtPlayer native call observations disagree with the reached branch");
            }
            var images = result.changedSlots(); validateSpikesImages(cut, images);
            if (images.size() != (firstFree.isPresent() ? 1 : 0)) {
                throw new IllegalArgumentException("Direct HurtPlayer publishes only the actual optional first-free LostRing image");
            }
            if (firstFree.isPresent()) {
                int whole = firstFree.getAsInt(); var before = cut.slots().get(whole - CdObjectPool.FIRST_WHOLE_INDEX);
                var expected = before.moveByte(ID, CdLostRing.OBJECT_ID)
                        .moveWord(X, call.expectedActor().motion().centreX16_16() >>> 16)
                        .moveWord(Y, call.expectedActor().motion().centreY16_16() >>> 16);
                if (whole == dispatchWholeIndex || !images.getFirst().equals(expected)) {
                    throw new IllegalArgumentException("LostRing allocation may write only ID/X.w/Y.w over the exact dirty first-free image");
                }
            }
            frame.validateObjectHurtResult(call.expectedActor(), expectedPlayer, result); // Exactly Frame's five owned/read globals.
            var ledger = new ArrayList<>(sounds);
            for (var observation : result.calls()) {
                if (observation instanceof CdPlayerTouchResponse.PlayFm fm) {
                    ledger.add(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm.sound())));
                }
            }
            var nextSounds = List.copyOf(ledger);
            // No callback and no discoverable late preflight follows. World is checked unchanged, never rewritten.
            commitTouchImages(cut, images);
            frame.applyObjectHurtResult(call.expectedActor(), expectedPlayer, result);
            contactGlobals = nextContact;
            sounds = nextSounds;
        } finally { inSolidCommit = false; }
    }

    // BEGIN Platform21 Runtime R1 private integration.
    /** Mint the private origin only around this cached body's actual admitted physical visit. */
    private void runPlatform(int wholeIndex) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (activePlatformInvocation != null || wholeIndex != dispatchWholeIndex
                || placement.pool().state(wholeIndex).id() != CdPlatform.OBJECT_ID) {
            throw new IllegalStateException("Platform dispatch requires its current physical ID21 and no active invocation");
        }
        var gates = frame.environment().movement().gates();
        var globals = new CdPlatform.Globals(gates.zoneAct(), ringGlobals.timeStopTimer(),
                preScrollForeground.xWord(), preScrollForeground.yWord(), gates.timeZone());
        var inputs = new CdPlatform.Inputs(solidPlayer(), solidGates(), solidGlobals());
        activePlatformInvocation = new PlatformInvocation(platform, wholeIndex);
        try {
            platform.run(wholeIndex, globals, inputs);
        } finally {
            // Completion, native stop, host exception and Error all retire the invocation.
            activePlatformInvocation = null;
        }
    }

    /** Scope establishes origin; the reached site separately admits live or genuinely deleted a0. */
    private CdObjectState requirePlatformCallback() {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activePlatformInvocation;
        if (invocation == null || invocation.body() != platform || invocation.wholeIndex() != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Platform callback requires its actual synchronous cached-body invocation");
        }
        return placement.pool().state(dispatchWholeIndex);
    }

    /**
     * Platform/Main.asm:89-118,135-521, pin 1ecc5f3a. Validate post-instruction cuts, including
     * timer1->0, phase increments, same-visit second Tops and 5X's DeleteObject return into Main.
     * A detached Site value supplies no authority; requirePlatformCallback has already checked origin.
     */
    private void validatePlatformSite(CdPlatform.SolidCall call, CdObjectState source) {
        boolean anchor = call.site() == CdPlatform.Site.ANCHOR_GET_OFF;
        boolean deleted = anchor && source.id() == 0 && source.routine() == 0
                && java.util.Arrays.equals(source.copyBytes(), new byte[CdObjectState.BYTE_SIZE]);
        if (!deleted && (source.id() != CdPlatform.OBJECT_ID || source.routine() != 2)) {
            throw new IllegalStateException("Platform helper requires live ID21/routine2 except its actual cleared anchor tail");
        }
        int stop = ringGlobals.timeStopTimer();
        if (call.site() == CdPlatform.Site.TIME_STOP_TOP) {
            if (stop == 0 || deleted || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                throw new IllegalStateException("Platform time-stop helper requires its nonzero native WORD and direct Top entry");
            }
            return; // Native time-stop does not read subtype/substate/phase or execute the anchor tail.
        }
        if (stop != 0) throw new IllegalStateException("Platform ordinary helper cannot run under native time-stop");
        int family = source.subtype() >>> 4;
        int state = source.unsignedByte(PLATFORM_SUBSTATE);
        int timer = source.unsignedByte(PLATFORM_TIMER);
        int phaseByte = source.unsignedByte(PLATFORM_PHASE);
        boolean top = true;
        boolean valid = switch (call.site()) {
            case SUBTYPE_0_TOP -> family == 0;
            case SUBTYPE_1_TOP -> family == 1;
            case SUBTYPE_2_TOP -> family == 2;
            case SUBTYPE_3_TOP -> family == 3;
            case SUBTYPE_4_TOP -> family == 4;
            case SUBTYPE_5_STATIONARY_TOP -> family == 5 && state == 0;
            case SUBTYPE_5_TIMER_TOP -> family == 5 && state != 0 && timer != 0xFF;
            case SUBTYPE_5_DROP_TOP -> family == 5 && state != 0 && timer == 0;
            case SUBTYPE_5_DELETE_GET_OFF -> {
                top = false;
                yield family == 5 && state != 0 && timer == 0
                        && ((preScrollForeground.yWord() + 0x100) & 0xFFFF) < source.centreY();
            }
            case SUBTYPE_6_STATIONARY_1_TOP -> family == 6 && state == 0;
            case SUBTYPE_6_MOVE_DOWN_TOP -> family == 6 && state == 2 && phaseByte >= 2 && phaseByte <= 0x41;
            case SUBTYPE_6_STATIONARY_2_TOP -> family == 6 && state == 4;
            case SUBTYPE_7_STATIONARY_1_TOP -> family == 7 && state == 0;
            case SUBTYPE_7_TIMER_TOP -> family == 7 && state == 2 && timer != 0xFF;
            case SUBTYPE_7_RISING_TOP -> family == 7 && state == 2 && timer == 0;
            case SUBTYPE_7_STATIONARY_2_TOP -> family == 7 && state == 4;
            case SUBTYPE_8_STATIONARY_1_TOP -> family == 8 && state == 0;
            case SUBTYPE_8_TIMER_TOP -> family == 8 && state == 2 && timer != 0xFF;
            case SUBTYPE_8_MOVE_X_TOP -> family == 8 && state == 2 && timer == 0 && phaseByte >= 1 && phaseByte <= 0x40;
            case SUBTYPE_8_STATIONARY_2_TOP -> family == 8 && state == 4;
            case SUBTYPE_9_STATIONARY_1_TOP -> family == 9 && state == 0;
            case SUBTYPE_9_TIMER_TOP -> family == 9 && state == 2 && timer != 0xFF;
            case SUBTYPE_9_MOVE_X_TOP -> family == 9 && state == 2 && timer == 0 && phaseByte >= 1 && phaseByte <= 0x40;
            case SUBTYPE_9_STATIONARY_2_TOP -> family == 9 && state == 4;
            case ANCHOR_GET_OFF -> {
                top = false;
                int chunk = ((source.word(PLATFORM_ANCHOR_X) & 0xFF80)
                        - ((preScrollForeground.xWord() - 0x80) & 0xFF80)) & 0xFFFF;
                yield chunk > 0x280 && (deleted || family <= 9
                        && (family < 6 || state == 0 || state == 2 || state == 4));
            }
            case TIME_STOP_TOP -> false; // Handled before any subtype reads.
        };
        if (!valid || call.entry() != (top ? CdSolidObject.Entry.TOP_SOLID_OBJECT : CdSolidObject.Entry.GET_OFF_OBJECT)) {
            throw new IllegalStateException("Platform helper differs from its actual native instruction site/cut");
        }
    }

    /** Platform-local output validation; the existing Collapse validator and all its messages stay exact. */
    private List<CdObjectState> validatePlatformImages(CdPlatform.SolidCall call, CdObjectState source,
            CdObjectState object, List<CdObjectState> others, java.util.Set<CdObjectState.ByteField> writes) {
        var pool = placement.pool(); pool.validateWrite(object);
        if (!source.identity().equals(object.identity()) || object.copyBytes().length != CdObjectState.BYTE_SIZE
                || writes.stream().anyMatch(field -> field != SOLID_TYPE && field != FLAGS)
                || writes.contains(SOLID_TYPE) != (call.entry() == CdSolidObject.Entry.TOP_SOLID_OBJECT)
                || writes.contains(SOLID_TYPE) && object.unsignedByte(SOLID_TYPE) > 1
                || ((object.flags() ^ source.flags()) & ~8) != 0) {
            throw new IllegalArgumentException("Platform Solid output changed identity, canonical masks or unowned flag bits");
        }
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int offset = 0; offset < CdObjectState.BYTE_SIZE; offset++) {
            if (offset == SOLID_TYPE.byteOffset() && writes.contains(SOLID_TYPE)
                    || offset == FLAGS.byteOffset() && writes.contains(FLAGS)) continue;
            if (before[offset] != after[offset]) throw new IllegalArgumentException("Undeclared Platform a0 byte " + offset);
        }
        if (others.size() > 1 || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && !others.isEmpty()) {
            throw new IllegalArgumentException("Platform helper returned extra dynamic releases");
        }
        var images = new ArrayList<CdObjectState>(1 + others.size()); images.add(object);
        var seen = new java.util.HashSet<Integer>(); seen.add(dispatchWholeIndex);
        for (var other : others) {
            pool.validateWrite(other); int whole = other.identity().wholeIndex();
            if (whole < CdObjectPool.FIRST_WHOLE_INDEX || whole >= CdObjectPool.END_WHOLE_INDEX || !seen.add(whole)
                    || whole != call.expectedPlayer().standObject()) {
                throw new IllegalArgumentException("Platform release must retain its distinct actual dynamic stand identity");
            }
            var original = call.actualCut().slots().get(whole - CdObjectPool.FIRST_WHOLE_INDEX);
            if (!original.identity().equals(other.identity()) || !original.moveByte(FLAGS, original.flags() & ~8).equals(other)) {
                throw new IllegalArgumentException("Platform other-slot release changed bytes beyond native BCLR3");
            }
            images.add(other); // Dirty ID0 is native physical RAM and remains legal.
        }
        return List.copyOf(images);
    }

    /** One genuine Outcome, all preflight/overlay/ordered FM allocation BEFORE the first pool publication. */
    private void commitPlatformSolid(CdPlatform.SolidCall call) {
        var source = requirePlatformCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual Platform Solid call");
            var pool = placement.pool(); var cut = call.actualCut();
            validateDynamicCapture(cut);
            var before = cut.slots().get(dispatchWholeIndex - CdObjectPool.FIRST_WHOLE_INDEX);
            if (!before.identity().equals(source.identity()) || !before.equals(source)
                    || !pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("Platform Solid requires its exact entire live physical source cut");
            }
            validatePlatformSite(call, before);
            CdSolidObject.Result completed = null; CdSolidObject.Prefix prefix = null;
            CdObjectState object; List<CdObjectState> others;
            java.util.Set<CdObjectState.ByteField> writes;
            List<CdPlayerTouchResponse.FmSound> requests;
            if (call.outcome() instanceof CdSolidObject.Completed done) {
                completed = done.completedResult();
                if (completed.entry() != call.entry()) throw new IllegalArgumentException("Platform completed entry mismatch");
                object = completed.object(); others = completed.otherSlots(); writes = completed.objectWrites();
                requests = completed.fmRequests();
            } else {
                var stopped = (CdSolidObject.NativeStopped) call.outcome(); prefix = stopped.prefix();
                if (prefix.entry() != call.entry() || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                    throw new IllegalArgumentException("Platform native prefix must be the reached TopSolid entry");
                }
                object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites();
                requests = prefix.fmRequests();
                if (!requests.isEmpty() || !others.isEmpty()) {
                    throw new IllegalArgumentException("Stopped Top has no completed FM or dynamic release before its unavailable leaf");
                }
            }
            var images = validatePlatformImages(call, before, object, others, writes);
            var nextPool = CdObjectPool.overlay(cut, images); pool.validateRestore(nextPool);
            if (completed != null) frame.validateObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), completed);
            else frame.validateObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix);
            var ledger = new ArrayList<SoundRequest>(sounds);
            for (var request : requests) ledger.add(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(request)));
            var nextSounds = List.copyOf(ledger);
            pool.restore(nextPool);
            if (completed != null) frame.applyObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), completed);
            else frame.applyObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix);
            sounds = nextSounds;
            // Return to Body: it checks publication and rethrows its identical original stop, without d0.
        } finally { inSolidCommit = false; }
    }

    /** ObjPlatform_Subtype7X .RiseToCeiling: moved full positions and YV-=8 already belong to this cut. */
    private void commitPlatformPrimaryAngle(CdPlatform.PrimaryAngleCall call) {
        var source = requirePlatformCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "reached Platform primary-angle call");
            var pool = placement.pool(); var cut = call.actualCut(); var expected = call.expectedSource();
            validateDynamicCapture(cut); pool.validateWrite(expected);
            if (source.id() != CdPlatform.OBJECT_ID || source.routine() != 2 || source.subtype() >>> 4 != 7
                    || source.unsignedByte(PLATFORM_SUBSTATE) != 2 || source.unsignedByte(PLATFORM_TIMER) != 0
                    || ringGlobals.timeStopTimer() != 0 || !source.identity().equals(expected.identity())
                    || !source.equals(expected) || !cut.slots().get(dispatchWholeIndex - CdObjectPool.FIRST_WHOLE_INDEX).equals(expected)
                    || !pool.capture().slots().equals(cut.slots())
                    || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle()) {
                throw new IllegalStateException("Platform angle call differs from its actual Rising full-pool/source/Frame cut");
            }
            // Raw stores only: no pre-zero, pool publication, secondary write or post-return effect replay.
            frame.applyObjectPrimaryAngle(call.value());
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSolidCommit = false; }
    }
    // END Platform21 Runtime R1 private integration.

    /** Exact cached private producer scope, immediately around one admitted physical native visit. */
    private void runCollapsePlatform(int wholeIndex) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (activeCollapseInvocation != null || wholeIndex != dispatchWholeIndex
                || placement.pool().state(wholeIndex).id() != CdCollapsePlatform.OBJECT_ID) {
            throw new IllegalStateException("Collapse dispatch requires its current physical ID20 and no active invocation");
        }
        var gates = frame.environment().movement().gates();
        var despawn = new CdCollapsePlatform.DespawnGlobals(gates.timeZone(), gates.timeWarpDirection(), preScrollForeground.xWord());
        var inputs = new CdCollapsePlatform.Inputs(solidPlayer(), solidGates(), solidGlobals());
        activeCollapseInvocation = new CollapseInvocation(collapsePlatform, wholeIndex);
        try {
            collapsePlatform.run(wholeIndex, despawn, inputs);
        } finally {
            // Includes Completed, native-stop rethrow, callback/host error and Error. No failed entry can resume.
            activeCollapseInvocation = null;
        }
    }

    /** Plain public Site/Outcome values never grant this actual private body invocation's authority. */
    private CdObjectState requireCollapseCallback() {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeCollapseInvocation;
        if (invocation == null || invocation.body() != collapsePlatform || invocation.wholeIndex() != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Collapse callback requires its actual synchronous cached-body invocation");
        }
        var source = placement.pool().state(dispatchWholeIndex);
        if (source.id() != CdCollapsePlatform.OBJECT_ID) {
            throw new IllegalStateException("Collapse callback lost its live physical ID20");
        }
        return source;
    }

    /** Source instruction sites, including Delay's already-published timer/routine and real capacity branch. */
    private void validateCollapseSite(CdCollapsePlatform.SolidCall call, CdObjectState source) {
        boolean top;
        switch (call.site()) {
            case MAIN_TOP, MAIN_GET_OFF -> {
                if (source.routine() != 2) throw new IllegalStateException("Collapse Main helper requires routine2");
                top = call.site() == CdCollapsePlatform.Site.MAIN_TOP;
            }
            case DELAY_TOP, DELAY_GET_OFF -> {
                if ((source.routine() != 4 && source.routine() != 6)
                        || source.unsignedByte(COLLAPSE_SOLID_MARKER) == 0
                        || (source.routine() == 6) != (source.word(COLLAPSE_DELAY) == 0)
                        || call.site() == CdCollapsePlatform.Site.DELAY_GET_OFF && source.word(COLLAPSE_DELAY) != 0) {
                    throw new IllegalStateException("Collapse Delay helper differs from its post-timer source cut");
                }
                top = call.site() == CdCollapsePlatform.Site.DELAY_TOP;
            }
            case MULTI_CAPACITY_TOP, MULTI_CAPACITY_GET_OFF, SINGLE_CAPACITY_TOP, SINGLE_CAPACITY_GET_OFF -> {
                boolean single = call.site() == CdCollapsePlatform.Site.SINGLE_CAPACITY_TOP
                        || call.site() == CdCollapsePlatform.Site.SINGLE_CAPACITY_GET_OFF;
                if (source.routine() != 4 || (source.signedByte(SUBTYPE) < 0) != single
                        || source.unsignedByte(COLLAPSE_SAVED_SOURCE_ID) != CdCollapsePlatform.OBJECT_ID
                        || placement.pool().findObjSlot().isPresent()) {
                    throw new IllegalStateException("Collapse capacity helper requires the actual exhausted native family cut");
                }
                top = call.site() == CdCollapsePlatform.Site.MULTI_CAPACITY_TOP
                        || call.site() == CdCollapsePlatform.Site.SINGLE_CAPACITY_TOP;
            }
            default -> throw new IllegalStateException("Unowned Collapse native helper site");
        }
        if (call.entry() != (top ? CdSolidObject.Entry.TOP_SOLID_OBJECT : CdSolidObject.Entry.GET_OFF_OBJECT)) {
            throw new IllegalStateException("Collapse helper entry differs from its native instruction site");
        }
    }

    /**
     * Only canonical BYTE25/SOLID_TYPE and BYTE22/FLAGS stores belong to this native call. All other63/62
     * source bytes retain their exact image. Same-value masks remain present; ROUTINE_2 is not another store.
     * Actual invocation origin supplies the reached masks: no second Solid execution or metadata replay.
     */
    private List<CdObjectState> validateCollapseImages(CdCollapsePlatform.SolidCall call, CdObjectState source,
            CdObjectState object, List<CdObjectState> others, java.util.Set<CdObjectState.ByteField> writes) {
        var pool = placement.pool(); pool.validateWrite(object);
        if (!source.identity().equals(object.identity()) || object.copyBytes().length != CdObjectState.BYTE_SIZE
                || writes.stream().anyMatch(field -> field != SOLID_TYPE && field != FLAGS)
                || writes.contains(SOLID_TYPE) != (call.entry() == CdSolidObject.Entry.TOP_SOLID_OBJECT)
                || writes.contains(SOLID_TYPE) && object.unsignedByte(SOLID_TYPE) > 1
                || ((object.flags() ^ source.flags()) & ~8) != 0) {
            throw new IllegalArgumentException("Collapse Solid output changed identity, canonical masks or unowned flag bits");
        }
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int offset = 0; offset < CdObjectState.BYTE_SIZE; offset++) {
            if (offset == SOLID_TYPE.byteOffset() && writes.contains(SOLID_TYPE)
                    || offset == FLAGS.byteOffset() && writes.contains(FLAGS)) continue;
            if (before[offset] != after[offset]) throw new IllegalArgumentException("Undeclared Collapse a0 byte " + offset);
        }
        if (others.size() > 1 || call.entry() == CdSolidObject.Entry.GET_OFF_OBJECT && !others.isEmpty()) {
            throw new IllegalArgumentException("Collapse helper returned extra dynamic releases");
        }
        var images = new ArrayList<CdObjectState>(1 + others.size()); images.add(object);
        var seen = new java.util.HashSet<Integer>(); seen.add(dispatchWholeIndex);
        for (var other : others) {
            pool.validateWrite(other); int whole = other.identity().wholeIndex();
            if (whole < CdObjectPool.FIRST_WHOLE_INDEX || whole >= CdObjectPool.END_WHOLE_INDEX || !seen.add(whole)
                    || whole != call.expectedPlayer().standObject()) {
                throw new IllegalArgumentException("Collapse release must retain its distinct actual dynamic stand identity");
            }
            var original = call.actualCut().slots().get(whole - CdObjectPool.FIRST_WHOLE_INDEX);
            if (!original.identity().equals(other.identity()) || !original.moveByte(FLAGS, original.flags() & ~8).equals(other)) {
                throw new IllegalArgumentException("Collapse other-slot release changed bytes beyond native BCLR3");
            }
            // ID0 is legal here: native standing links dereference raw physical memory, never generations.
            images.add(other);
        }
        return List.copyOf(images);
    }

    /**
     * One genuine shared call: full96/source/output/Frame preflight and the complete immutable ordered FM
     * ledger precede S4. Adopt one prepared pool image, the matching Frame branch once and the ledger once.
     * NativeStopped returns to Body for its own image check and identical originalException rethrow; no
     * Result/path/d0 adapter, early rethrow or later source instruction is fabricated for a stopped prefix.
     */
    private void commitCollapseSolid(CdCollapsePlatform.SolidCall call) {
        var source = requireCollapseCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual Collapse Solid call");
            var pool = placement.pool(); var cut = call.actualCut();
            validateDynamicCapture(cut);
            var before = cut.slots().get(dispatchWholeIndex - CdObjectPool.FIRST_WHOLE_INDEX);
            if (!before.identity().equals(source.identity()) || !before.equals(source)
                    || before.id() != CdCollapsePlatform.OBJECT_ID || !pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("Collapse Solid requires its exact entire live physical source cut");
            }
            validateCollapseSite(call, before);
            CdSolidObject.Result completed = null; CdSolidObject.Prefix prefix = null;
            CdObjectState object; List<CdObjectState> others;
            java.util.Set<CdObjectState.ByteField> writes;
            List<CdPlayerTouchResponse.FmSound> requests;
            if (call.outcome() instanceof CdSolidObject.Completed done) {
                completed = done.completedResult();
                if (completed.entry() != call.entry()) throw new IllegalArgumentException("Collapse completed entry mismatch");
                object = completed.object(); others = completed.otherSlots(); writes = completed.objectWrites();
                requests = completed.fmRequests();
            } else {
                var stopped = (CdSolidObject.NativeStopped) call.outcome(); prefix = stopped.prefix();
                if (prefix.entry() != call.entry() || call.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT) {
                    throw new IllegalArgumentException("Collapse native prefix must be the reached TopSolid entry");
                }
                object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites();
                requests = prefix.fmRequests();
                if (!requests.isEmpty() || !others.isEmpty()) {
                    throw new IllegalArgumentException("Stopped Top has no completed FM or dynamic release before its unavailable leaf");
                }
            }
            var images = validateCollapseImages(call, before, object, others, writes);
            var nextPool = CdObjectPool.overlay(cut, images); pool.validateRestore(nextPool);
            if (completed != null) frame.validateObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), completed);
            else frame.validateObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix);
            var ledger = new ArrayList<SoundRequest>(sounds);
            for (var request : requests) ledger.add(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(request)));
            var nextSounds = List.copyOf(ledger); // All wrappers/flattening/list allocation are BEFORE S4.
            // No callback, new source read, late sound constructor or discoverable ownership validation gap.
            pool.restore(nextPool);
            if (completed != null) frame.applyObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), completed);
            else frame.applyObjectSolidPrefix(call.expectedPlayer(), call.expectedGlobals(), prefix);
            sounds = nextSounds;
        } finally { inSolidCommit = false; }
    }

    /** Main.asm:83-84, after actual GetOff and before routine+2. Shared requests already form its prefix. */
    private void playCollapseFm(int nativeId) {
        var source = requireCollapseCallback();
        inSolidCommit = true;
        try {
            var request = new CollapsePlatformFm(nativeId);
            var cut = placement.pool().capture(); validateDynamicCapture(cut);
            if (source.routine() != 2 || !source.equals(cut.slots().get(dispatchWholeIndex - CdObjectPool.FIRST_WHOLE_INDEX))
                    || !placement.pool().capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("Collapse FM_A3 requires its actual live Main cut before routine+2");
            }
            var ledger = new ArrayList<SoundRequest>(sounds); ledger.add(request);
            var nextSounds = List.copyOf(ledger); // Retain ordered duplicates, without joining or running a driver.
            sounds = nextSounds;
        } finally { inSolidCommit = false; }
    }

    /**
     * ObjBoulder_Main (Boulder/Main.asm:41-48), one actual primary SolidObject call. Validate ALL 96
     * complete same-owner images and the current physical ID $1B/Entry, exact live-pool value cut,
     * returned S4 images and Frame expected player/globals BEFORE the first S4 write. Then publish
     * S4, apply S3 once, and append shared ContactFmSound in result order once. The existing shared
     * inSolidCommit flag makes every other callback and DrawObject mutually non-reentrant as well.
     */
    private void commitBoulderSolid(CdBoulder.SolidCall call) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Boulder Solid commit must be synchronous and non-reentrant");
        }
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "completed Boulder Solid call");
            var pool = placement.pool(); var cut = call.callCut(); var result = call.result();
            validateDynamicCapture(cut);
            pool.validateWrite(result.object());
            var identity = result.object().identity();
            if (identity.wholeIndex() != dispatchWholeIndex) {
                throw new IllegalStateException("Boulder Solid return must belong to the current physical dispatch");
            }
            var live = pool.state(dispatchWholeIndex);
            var before = cut.slots().get(identity.dynamicIndex());
            if (!live.identity().equals(identity) || live.id() != CdBoulder.OBJECT_ID
                    || before.id() != CdBoulder.OBJECT_ID || result.object().id() != CdBoulder.OBJECT_ID
                    || before.routine() != 2 || (before.spriteFlags() & 0x80) == 0
                    || result.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                throw new IllegalStateException("Boulder Solid return differs from the live ID $1B Main cut");
            }
            if (!pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("CD pool changed after the Boulder Solid call cut");
            }
            var images = new ArrayList<CdObjectState>(1 + result.otherSlots().size());
            images.add(result.object()); images.addAll(result.otherSlots());
            for (var image : images) pool.validateWrite(image);
            frame.validateObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            // No callback or other owner runs between complete preflight and the single S4/S3 publication.
            commitTouchImages(cut, images);
            frame.applyObjectSolidResult(call.expectedPlayer(), call.expectedGlobals(), result);
            for (var fm : result.fmRequests()) appendSound(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSolidCommit = false; }
    }

    /**
     * One synchronous native TopSolid return. The pure owner has committed nothing. The Float wrapper has
     * already made its source a0 writes and captured exactly this cut; no callback/owner runs between the
     * following preflight, pool CAS and Frame apply. Only completed FM requests then enter the sound ledger.
     * Both globals' collision buffers are adopted by Frame's one existing store, never replayed via metadata.
     */
    private void commitTopSolid(CdFloatBlock.TopSolidCall call) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("TopSolid commit must be synchronous and non-reentrant");
        }
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "completed TopSolid call");
            var result = call.result(); var pool = placement.pool(); var cut = call.callCut();
            pool.validateRestore(cut);
            if (result.entry() != CdSolidObject.Entry.TOP_SOLID_OBJECT
                    || result.object().identity().wholeIndex() != dispatchWholeIndex
                    || cut.slots().get(result.object().identity().dynamicIndex()).id() != CdFloatBlock.OBJECT_ID) {
                throw new IllegalStateException("TopSolid return must belong to the current Floating Block dispatch");
            }
            if (!pool.capture().slots().equals(cut.slots())) {
                throw new IllegalStateException("CD pool changed after the TopSolid call cut");
            }
            var images = new ArrayList<CdObjectState>(1 + result.otherSlots().size());
            images.add(result.object()); images.addAll(result.otherSlots());
            // Validate every image's real pool owner before any pool or player/global mutation.
            for (var image : images) pool.validateWrite(image);
            frame.validateObjectSolidResult(call.player(), call.globals(), result);
            commitTouchImages(cut, images);
            frame.applyObjectSolidResult(call.player(), call.globals(), result);
            for (var fm : result.fmRequests()) appendSound(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(fm)));
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inSolidCommit = false; }
    }

    private void drawDynamic(CdObjectPool pool, int whole) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu
                || pool != placement.pool() || whole != dispatchWholeIndex) {
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
    /** LevelStart clears levelRestart (Level/Main.asm:299) before Level_MainLoop's next VSync (:333-335). */
    private void requireNoLevelRestart(String operation) {
        if (levelGlobals.levelRestart() != 0) {
            throw new IllegalStateException("No old-visit " + operation + " follows the LevelStart branch (levelRestart $"
                    + Integer.toHexString(levelGlobals.levelRestart()) + ")");
        }
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
