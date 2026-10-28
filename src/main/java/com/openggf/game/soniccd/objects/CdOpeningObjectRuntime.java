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
import com.openggf.game.soniccd.player.CdPlayerTimeWarp;
import com.openggf.game.soniccd.scroll.CdR11aForegroundScroll;
import com.openggf.game.soniccd.scroll.CdR11aBackgroundScroll;
import com.openggf.game.soniccd.resources.CdObjectPresentation;
import com.openggf.game.soniccd.resources.CdPowerupArtRuntime;
import com.openggf.game.soniccd.resources.CdR11aCameraArtResidency;
import com.openggf.game.soniccd.resources.CdR11aSectionArt;
import com.openggf.game.soniccd.resources.CdLevelPaletteCycle;
import com.openggf.game.soniccd.resources.CdLevelPaletteFade;
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
import static com.openggf.game.soniccd.objects.CdSpinningDisc.DiscPlayerField.*;
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
 * The 36-tile powerup window starts cold zero and changes only through the existing LoadShieldArt service.
 * The initial Ring, contact, score and level globals are caller-declared DEV ingress, never an entry proof.
 * Live dynamic IDs $10 Ring, $11 LostRing, $3D Mosqui, $3E Pata-Bata, $24 Animal, $2C 3D plant and $2A 3D ramp,
 * aftermath IDs $18/$1C/$1F, $12 Floating Block, normal and diagonal $0A Spring, $34 Spin tunnel tag, $0D horizontal Door,
 * $35 vertical Tunnel Door, $1B Boulder, $26 common Spikes, $31 Sonic Hole, $33 Log Shadow, $13 Checkpoint, $20 Collapsing Platform, $19 Monitor/Time Post and $1A shoes item and the death row's $3B Game/Time Over are owned.
 * The zero reserved bank is NOT post-native HUD/title initialization: native time stars in slots12..15
 * are admitted when Sonic creates them; other later reserved owners (Level/Main.asm:261-274) remain omitted
 * by this DEV policy. Primary slot 0 stays exclusively in Frame;
 * dynamic slots 32..127 stay exclusively in placement's existing pool. ID $0B splash execution stays unowned.
 * Every other ID fails at its live dispatch; standing-object MODE remains a typed player boundary.
 * Time Post activation submits typed voice metadata; ordinary Main owns pre-save time warp and same-bank
 * star creation. Save/restart/device stages retain precise typed boundaries. Bounded camera PLC work uses the actual plan and
 * seventeen finite sources, with one immutable resident generation; later unowned producers stop when reached.
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
        DYNAMIC_SLOTS, DYNAMIC_DONE, BUILDING, BUILT, TAIL, FAILED, CLOSED, LEVEL_START
    }

    /**
     * Stable bases in ONE root-governed atlas range exclusive to this picture.
     * Root reserves/binds its twenty-one disjoint subdivisions; this value neither
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
     * Tamabboh appends only selected current native $440 (659 potential total); its other 58 tiles retain Plant/Ramp aliases.
     * Log Shadow appends five selected current patterns (664 potential total); its other eleven retain Anton/Plant aliases.
     * The canonical form takes all twenty-one bases explicitly; no Anton, Tamabboh or Log Shadow preload is implied.
     */
    public record LogicalBanks(int player, int ring, int mosqui, int plant, int ramp,
                               int explosion, int points, int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime, int boulder, int diagonalSpring, int spikes, int checkpoint, int anton, int collapsePlatform, int tamabboh, int logShadow) {
        /** Compatibility20 preserves every old base; the five-pattern span follows Tamabboh without integer wrap. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime,
                            int boulder, int diagonalSpring, int spikes, int checkpoint, int anton, int collapsePlatform,
                            int tamabboh) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, boulder, diagonalSpring, spikes, checkpoint, anton, collapsePlatform,
                    tamabboh, Math.addExact(tamabboh, 1));
        }
        /** Compatibility19 preserves every old base; the singleton follows Platform without integer wrap. */
        public LogicalBanks(int player, int ring, int mosqui, int plant, int ramp, int explosion, int points,
                            int flower, int powerup, int floatBlock, int spring, int tunnelDoorVertical, int monitorTime,
                            int boulder, int diagonalSpring, int spikes, int checkpoint, int anton, int collapsePlatform) {
            this(player, ring, mosqui, plant, ramp, explosion, points, flower, powerup, floatBlock, spring,
                    tunnelDoorVertical, monitorTime, boulder, diagonalSpring, spikes, checkpoint, anton, collapsePlatform,
                    Math.addExact(collapsePlatform, 57));
        }
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

    /** Borrowed actual camera values; neither a capture nor a publication capability. */
    public record TimeWarpWorldCut(CdR11aForegroundScroll.State foreground,
                                   CdR11aBackgroundScroll.State background) {
        public TimeWarpWorldCut { Objects.requireNonNull(foreground); Objects.requireNonNull(background); }
    }
    /** A ready lock-only foreground projection. The bound world validates its complete source. */
    public record TimeWarpLockPublication(TimeWarpWorldCut expected,
                                          CdR11aForegroundScroll.State finalForeground) {
        public TimeWarpLockPublication { Objects.requireNonNull(expected); Objects.requireNonNull(finalForeground); }
    }
    public interface TimeWarpWorldPort {
        TimeWarpWorldCut read();
        void validate(TimeWarpLockPublication publication);
        void apply(TimeWarpLockPublication publication);
    }
    /** Explicit saved-value custody and the same Presentation owner; no cold saved-byte default. */
    public record TimeWarpBindings(CdTimeWarpSaveData.State initial, TimeWarpWorldPort world) {
        public TimeWarpBindings { Objects.requireNonNull(initial); Objects.requireNonNull(world); }
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
    public sealed interface SoundRequest permits PlayerSound, RingFm, RingSubCpu, MosquiFm, LostRingFm, GameOverSubCpu, SpringFm, TunnelDoorVerticalFm, TunnelDoorFm, MonitorFm, MonitorItemSubCpu, TimePostSubCpu, CheckpointFm, CollapsePlatformFm, TamabbohFm, SpinningDiscFm, TunnelPathFm, SonicHoleFm { }
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
    /** ID07's reached FM_91 backedge; ordered request metadata, never driver execution. */
    public record TunnelPathFm(CdTunnelPath.FmSound sound) implements SoundRequest {
        public TunnelPathFm { Objects.requireNonNull(sound, "Tunnel Path FM sound"); }
    }
    /** SonicHole's reached synchronous FM_A3 metadata; no driver queue, audio execution or replay. */
    public record SonicHoleFm(CdSonicHole.FmSound sound) implements SoundRequest {
        public SonicHoleFm { Objects.requireNonNull(sound, "SonicHole FM sound"); }
    }
    /** Disc's actual synchronous PlayFMSound cut; ordered FM_A0 metadata only, no playback. */
    public record SpinningDiscFm(CdSpinningDisc.FmSound sound) implements SoundRequest {
        public SpinningDiscFm { Objects.requireNonNull(sound, "Spinning Disc FM sound"); }
    }
    /** Ordered DEV FM_A0 metadata only; not native driver queues or playback. */
    public record TamabbohFm(CdTamabboh.FmSound sound) implements SoundRequest {
        public TamabbohFm { Objects.requireNonNull(sound, "Tamabboh FM sound"); }
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
    /** Current native palette RAM and the last actual palette publication; no extra clock or cache authority. */
    public record PaletteState(CdLevelPaletteCycle.State cycle, CdAssetBytes waterPalette, CdAssetBytes displayedPalette) {
        public PaletteState {
            Objects.requireNonNull(cycle, "current palette cycle");
            if (Objects.requireNonNull(waterPalette, "known water palette").length() != 128
                    || Objects.requireNonNull(displayedPalette, "selected palette").length() != 128) {
                throw new IllegalArgumentException("Complete native palettes require 128 bytes");
            }
        }
        @Override public boolean equals(Object other) {
            return other instanceof PaletteState value && cycle.equals(value.cycle)
                    && java.util.Arrays.equals(waterPalette.copy(), value.waterPalette.copy())
                    && java.util.Arrays.equals(displayedPalette.copy(), value.displayedPalette.copy());
        }
        @Override public int hashCode() {
            return 31 * (31 * cycle.hashCode() + java.util.Arrays.hashCode(waterPalette.copy()))
                    + java.util.Arrays.hashCode(displayedPalette.copy());
        }
    }

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
        private final CdTimeWarpSaveData.State timeWarpSavedState;
        private final PaletteState palettes;

        private Snapshot(Object owner, CdObjectPlacementRuntime.Snapshot placement, CdReservedObjectBank.Snapshot reserved,
                         CdRingGlobals ringGlobals,
                         CdRingSpillGlobals spillGlobals,
                         CdObjectDrawQueue.Snapshot queue, SpritePresentation.Frame displayedFrame,
                         List<SoundRequest> sounds, DevGlobals devGlobals,
                         CdPlayerBody.ContactGlobals contactGlobals, ScoreGlobals scoreGlobals,
                         CdLevelGlobals levelGlobals, CdPowerupArtRuntime.State powerupArtState,
                         CdR11aSectionArt.State sectionArtState, CheckpointOwnerState checkpointState,
                         CdTimeWarpSaveData.State timeWarpSavedState, PaletteState palettes) {
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
            this.timeWarpSavedState = timeWarpSavedState;
            this.palettes = palettes;
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
        public CdTimeWarpSaveData.State timeWarpSavedState() { return timeWarpSavedState; }
        public PaletteState palettes() { return palettes; }
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
    private final CdTamabboh tamabboh;
    private final CdLogShadow logShadow;
    private final CdSonicHole sonicHole;
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
    private final CdSpinningDisc spinningDisc;
    private final CdTunnelPath tunnelPath;
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
    private final CdTimeStars timeStars;
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
    private CdTimeWarpSaveData.State timeWarpSavedState;
    private final Optional<TimeWarpBindings> timeWarpBindings;
    // The one owned LoadShieldArt window; only the selector-8 V-int cut and restore replace this immutable value.
    private CdPowerupArtRuntime.State powerupArtState;
    // ONE camera-art aggregate; service/tail/restore replace it, never a separate resident or PLC store.
    private CdR11aSectionArt.State sectionArtState;
    private final List<CdLevelPaletteCycle.Script> paletteScripts;
    private PaletteState palettes;
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
    /** Actual cached-body visit and stable Frame player only; never captured or a public capability. */
    private record TamabbohInvocation(CdTamabboh body, int wholeIndex, CdPlayerBody.State player) { }
    private transient TamabbohInvocation activeTamabbohInvocation;
    // BEGIN Disc09 private invocation. Not a capture, gameplay phase, ROM clock or public capability.
    private enum DiscStep { SOLID, ACQUIRE, ORBIT, INPUT, JUMP_CLEAR, JUMP_VELOCITY, FM, HITBOX, DONE, STOPPED }
    private static final class SpinningDiscInvocation {
        private final CdSpinningDisc body;
        private final int wholeIndex;
        private DiscStep next = DiscStep.SOLID;
        private SpinningDiscInvocation(CdSpinningDisc body, int wholeIndex) {
            this.body = body; this.wholeIndex = wholeIndex;
        }
    }
    private transient SpinningDiscInvocation activeSpinningDiscInvocation;
    // END Disc09 private invocation.
    // BEGIN Tunnel07 private invocation. Neither captured state nor a public producer capability.
    private enum TunnelStep {
        MAIN, ACQUIRE_GVEL_OR_FINISH, ACQUIRE_FINISH, MAIN_FM,
        GOT_SPEED, GOT_FM, MOVE, NEXT_SPEED_OR_RELEASE, DONE, STOPPED
    }
    private static final class TunnelPathInvocation {
        private final CdTunnelPath body;
        private final int wholeIndex;
        private final int ingressRoutine;
        private CdObjectState lastSource;
        private CdObjectPool.Snapshot lastPool;
        private TunnelStep next;
        private List<SoundRequest> expectedFmLedger;
        private List<SoundRequest> readyFmLedger;

        private TunnelPathInvocation(CdTunnelPath body, int wholeIndex,
                                     CdObjectPool.Snapshot pool, CdObjectState source) {
            this.body = body; this.wholeIndex = wholeIndex; ingressRoutine = source.routine();
            lastSource = source; lastPool = pool;
            next = switch (ingressRoutine) {
                case 0, 2 -> TunnelStep.MAIN;
                case 4 -> TunnelStep.GOT_SPEED;
                case 6 -> TunnelStep.MOVE;
                default -> TunnelStep.STOPPED;
            };
        }
    }
    private transient TunnelPathInvocation activeTunnelPathInvocation;
    // END Tunnel07 private invocation.

    // BEGIN SonicHole31 private synchronous origin. Never captured gameplay or a public value capability.
    private enum SonicHoleStep { CONTROL, X, Y, FM, DONE }
    private static final class SonicHoleInvocation {
        private final CdSonicHole body;
        private final int wholeIndex;
        private final CdPlayerBody.State player;
        private final CdPlayerBody.Environment environment;
        private SonicHoleStep next = SonicHoleStep.CONTROL;
        private CdObjectPool.Snapshot lastPoolCut;

        private SonicHoleInvocation(CdSonicHole body, int wholeIndex, CdPlayerBody.State player,
                                    CdPlayerBody.Environment environment) {
            this.body = body; this.wholeIndex = wholeIndex; this.player = player; this.environment = environment;
        }
    }
    private transient SonicHoleInvocation activeSonicHoleInvocation;
    // END SonicHole31 private origin.
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
                null, initialCpuSatBytes, banks, preScrollForeground, primaryAngleWrite, Optional.empty());
    }

    /** Explicit native warp-save world/value binding; construction performs no world reads. */
    public static CdOpeningObjectRuntime usaR11aOpeningDevEmptySatPreloadedArt(
            CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
            CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore, CdLevelGlobals initialLevel,
            CheckpointBindings checkpointBindings,
            byte[] initialCpuSatBytes, LogicalBanks banks, CameraWords preScrollForeground,
            PrimaryAngleWrite primaryAngleWrite, TimeWarpBindings timeWarpBindings) throws IOException {
        // The cold DEV window above: .ClearFlowers zeroes the complete CPU buffer, InitVDP's fill its resident copy.
        return create(assets, frame, initialGlobals, initialContact, initialScore, initialLevel, checkpointBindings,
                CdObjectPlacementRuntime::fromAssets, new CdPowerupArtRuntime.State(new byte[0x480], new byte[0x480]),
                null, initialCpuSatBytes, banks, preScrollForeground, primaryAngleWrite, Optional.of(Objects.requireNonNull(timeWarpBindings)));
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
                cameraArt, initialCpuSatBytes, banks, preScrollForeground, primaryAngleWrite, Optional.empty());
    }

    /** Restart sibling carrying the distinct warp-saved value without a warp-load claim. */
    public static CdOpeningObjectRuntime usaR11aRestartDevEmptySatPreloadedArt(
            CdR11aAssets assets, CdPlayerFrameRuntime frame, CdRingGlobals initialGlobals,
            CdPlayerBody.ContactGlobals initialContact, ScoreGlobals initialScore, CdLevelGlobals initialLevel,
            CheckpointBindings checkpointBindings,
            CdAssetBytes initialSavedObjectFlags, CdPowerupArtRuntime.State initialPowerup,
            CameraArtRestartIngress cameraArt, byte[] initialCpuSatBytes, LogicalBanks banks, CameraWords preScrollForeground,
            PrimaryAngleWrite primaryAngleWrite, TimeWarpBindings timeWarpBindings) throws IOException {
        Objects.requireNonNull(initialSavedObjectFlags, "explicit carried saved-object bank");
        Objects.requireNonNull(initialPowerup, "explicit restart powerup CPU/resident windows");
        Objects.requireNonNull(cameraArt, "required explicit restart current-camera carry");
        return create(assets, frame, initialGlobals, initialContact, initialScore, initialLevel, checkpointBindings,
                source -> CdObjectPlacementRuntime.fromAssets(source, initialSavedObjectFlags), initialPowerup,
                cameraArt, initialCpuSatBytes, banks, preScrollForeground, primaryAngleWrite, Optional.of(Objects.requireNonNull(timeWarpBindings)));
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
            PrimaryAngleWrite primaryAngleWrite, Optional<TimeWarpBindings> timeWarpBindings) throws IOException {
        Objects.requireNonNull(timeWarpBindings, "explicit presence of native warp-save binding");
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
        if (sourceBank.sources().size() != 17) throw new IllegalArgumentException("Seventeen reviewed section source views required");
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
            // exactly nineteen complete tile writes from the SAME authenticated seventeen-source bank. No PLC
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
            // SAME retained seventeen-source bank and cached actual decode; exactly twelve complete writes.
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
        // Validate all twenty-one spans: old579, lazy Anton22, Platform57, Tamabboh $440 and Log Shadow5.
        // Empty SAT admission reads no Anton/Log Shadow pixels; cold $8120/$83E0 remain intentionally UNKNOWN.
        CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(frame.artState(), initialPowerup, objects, frontier, contact,
                floatBlockArt, springArt, tunnelDoorArt, monitorTimeArt, initialSectionArt.residency(), banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(),
                banks.explosion(), banks.points(), banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(), banks.spikes(), banks.checkpoint(), banks.anton(), banks.collapsePlatform(), banks.tamabboh(), banks.logShadow());
        return new CdOpeningObjectRuntime(assets, frame, initialGlobals, initialContact, initialScore, initialLevel, checkpointBindings, initial,
                banks, preScrollForeground, primaryAngleWrite, objects, frontier, contact, floatBlockArt, springArt, tunnelDoorArt, monitorTimeArt, placement, reserved, mappings, initialPowerup, sectionArt, initialSectionArt, timeWarpBindings);
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
                                   CdR11aSectionArt.State initialSectionArt, Optional<TimeWarpBindings> timeWarpBindings) throws IOException {
        this.assets = assets;
        paletteScripts = assets.paletteCycle().scripts();
        // IPX.RunMMD:1311-1319 clears FF2000..FFFB00, including ALL128 water bytes.
        // R11A's ClearMiscVars clears the six touched timer/step bytes; actual loads establish
        // the normal palette. These are explicit semantic entry postconditions, not Title execution
        // or guessed residue. PP cycles never touch water, and every restore adopts its exact value.
        var initialPalette = assets.loadPlan().initialPalette();
        palettes = new PaletteState(new CdLevelPaletteCycle.State(initialPalette,
                CdAssetBytes.copyOf(new byte[6]), CdAssetBytes.copyOf(new byte[6])),
                CdAssetBytes.copyOf(new byte[128]), initialPalette);
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
        this.timeWarpBindings = timeWarpBindings;
        timeWarpSavedState = timeWarpBindings.map(TimeWarpBindings::initial).orElseGet(CdTimeWarpSaveData.State::unknown);
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
        }, mappings, initial, List.of(new CdSlotRef(12), new CdSlotRef(13), new CdSlotRef(14), new CdSlotRef(15)));
        mosqui = new CdMosqui(placement, CdMosqui.Assets.fromObjectAssets(objects),
                new CdPlayerTerrain(assets.terrain()), (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        pataBata = new CdPataBata(placement, CdPataBata.Assets.fromObjectAssets(objects, assets.player()),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        anton = new CdAnton(placement, CdAnton.Assets.fromObjectAssets(objects),
                new CdPlayerTerrain(assets.terrain()), this::commitAntonPrimaryAngle,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        tamabboh = new CdTamabboh(placement, CdTamabboh.Assets.fromObjectAssets(objects),
                new CdPlayerTerrain(assets.terrain()), this::commitTamabbohPrimaryAngle,
                this::readTamabbohPlayerWord, this::playTamabbohFm,
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        logShadow = new CdLogShadow(placement, CdLogShadow.Assets.fromObjectAssets(objects),
                (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        sonicHole = new CdSonicHole(placement, CdSonicHole.Assets.fromObjectAssets(objects),
                this::readSonicHolePrimary, this::playSonicHoleFm,
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
        // Same authenticated placement/assets, one retained math owner and the existing shared pure Solid.
        spinningDisc = new CdSpinningDisc(placement, CdSpinningDisc.Assets.fromObjectAssets(objects),
                new CdPlayerMath(assets.player()), solid, this::commitSpinningDiscSolid,
                new CdSpinningDisc.PlayerOwner() {
                    @Override public CdSpinningDisc.PlayerCut read() { return readSpinningDiscPlayer(); }
                    @Override public void commit(CdSpinningDisc.PlayerCall call) { commitSpinningDiscPlayer(call); }
                }, this::playSpinningDiscFm, (pool, slot) -> drawDynamic(pool, slot.wholeIndex()));
        tunnelPath = new CdTunnelPath(placement, CdTunnelPath.Assets.fromObjectAssets(objects),
                new CdTunnelPath.PlayerOwner() {
                    @Override public CdTunnelPath.PlayerCut read() { return readTunnelPathPlayer(); }
                    @Override public void commit(CdTunnelPath.PlayerCall call) { commitTunnelPathPlayer(call); }
                }, this::playTunnelPathFm);
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
        timeStars = new CdTimeStars(reserved, CdTimeStars.Assets.fromObjectAssets(objects),
                new CdTimeStars.PowerupPort() {
                    @Override public int read() { requireTimeStarCall(); return levelGlobals.powerup(); }
                    @Override public void write(int value) {
                        requireTimeStarCall();
                        var old = levelGlobals;
                        levelGlobals = new CdLevelGlobals(old.levelRestart(), old.updateHudTime(), old.checkpoint(),
                                old.spawnMode(), old.plcLoadFlags(), old.flowerCount(), old.fadeEnableDisplay(), value, old.timeOver());
                    }
                }, this::drawTimeStar);
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
    /** Immutable observation, including already-published native prefixes after a terminal failure. */
    public PaletteState palettes() { return palettes; }
    public CdPlayerBody.ContactGlobals contactGlobals() { return contactGlobals; }
    public ScoreGlobals scoreGlobals() { return scoreGlobals; }
    /** The one level-flow store; Body summaries commit here without retaining their call context. */
    public CdLevelGlobals levelGlobals() { return levelGlobals; }
    /** The owned immutable LoadShieldArt CPU/resident window (Powerup/Main.asm:157-176). */
    public CdPowerupArtRuntime.State powerupArtState() { return powerupArtState; }
    /** Exact immutable camera queue/table/decoder/residency aggregate; diagnostic access survives a typed stop. */
    public CdR11aSectionArt.State sectionArtState() { return sectionArtState; }
    public CheckpointOwnerState checkpointState() { return checkpointState; }
    public CdTimeWarpSaveData.State timeWarpSavedState() { return timeWarpSavedState; }
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
                checkActive(Phase.SERVICING);
                palettes = new PaletteState(palettes.cycle(), palettes.waterPalette(), palettes.cycle().palette());
            }, () -> {
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
                        banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(), banks.spikes(), banks.checkpoint(), banks.anton(), banks.collapsePlatform(), banks.tamabboh(), banks.logShadow());
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

    /** Invocation-local borrowed reads. It is never retained by Runtime or captured. */
    private final class TimeWarpBodyReads implements CdPlayerTimeWarp.SaveReads {
        private final CdPlayerBody.State expectedPlayer = frame.state();
        private final CdPlayerBody.Environment expectedEnvironment = frame.environment();
        private final Optional<CdPlayerBody.DisplayPublication> expectedDisplay = frame.display();
        private final com.openggf.game.soniccd.resources.CdPlayerArtRuntime.State expectedArt = frame.artState();
        private final CdPlayerFrameRuntime.VIntClock expectedClock = frame.vIntClock();
        private final boolean expectedPaused = frame.paused();
        private final CdObjectPlacementRuntime.Snapshot expectedPlacement = placement.capture();
        private final CdLevelGlobals expectedLevel = levelGlobals;
        private final CdRingGlobals expectedRings = ringGlobals;
        private final CheckpointOwnerState expectedCheckpoint = checkpointState;
        private final CdTimeWarpSaveData.State expectedSaved = timeWarpSavedState;
        private final CdObjectPool.Snapshot pool;
        private final CdReservedObjectBank.Snapshot reservedCut;
        private final TimeWarpWorldPort world;
        private TimeWarpLockPublication readyLock;

        private TimeWarpBodyReads(CdObjectPool.Snapshot pool, CdReservedObjectBank.Snapshot reservedCut) {
            this.pool = pool; this.reservedCut = reservedCut;
            world = timeWarpBindings.orElseThrow().world();
        }
        private void verifyOwners() {
            checkActive(Phase.PLAYER_BODY);
            if (frame.closed() || frame.state() != expectedPlayer || frame.environment() != expectedEnvironment
                    || frame.display() != expectedDisplay || frame.artState() != expectedArt
                    || frame.vIntClock() != expectedClock || frame.paused() != expectedPaused
                    || levelGlobals != expectedLevel || ringGlobals != expectedRings
                    || checkpointState != expectedCheckpoint || timeWarpSavedState != expectedSaved
                    || !placement.pool().capture().slots().equals(pool.slots())
                    || !placement.status().equals(expectedPlacement.status())
                    || !java.util.Arrays.equals(placement.capture().savedFlags().copy(), expectedPlacement.savedFlags().copy())
                    || !reserved.capture().slots().equals(reservedCut.slots()))
                throw new IllegalStateException("Native warp-save read changed an actual owner");
        }
        private void verify() {
            verifyOwners();
            if (readyLock != null) {
                try { world.validate(readyLock); }
                catch (RuntimeException | Error failure) {
                    try { verifyOwners(); }
                    catch (RuntimeException | Error verification) { failure.addSuppressed(verification); }
                    throw failure;
                }
                verifyOwners();
            }
        }
        private <T> T readChecked(Supplier<T> read) {
            verify();
            T value;
            try { value = read.get(); }
            catch (RuntimeException | Error failure) {
                try { verify(); }
                catch (RuntimeException | Error verification) { failure.addSuppressed(verification); }
                throw failure;
            }
            verify();
            return value;
        }
        @Override public int readScrollLockWord() {
            return readChecked(() -> {
                if (readyLock != null) throw new IllegalStateException("TimeWarp read its lock more than once");
                var cut = Objects.requireNonNull(world.read(), "actual warp-save world cut");
                var old = cut.foreground();
                var after = new CdR11aForegroundScroll.State(old.cameraX(), old.cameraY16_16(),
                        old.horizontalCrossed(), old.verticalCrossed(), old.scrollXDiff(), old.scrollYDiff(),
                        old.scrollFlags(), old.destBottomBound(), old.destLeftBound(), old.bottomBoundShift(),
                        (old.scrollLock() & 0xFF) | 0x0100);
                readyLock = new TimeWarpLockPublication(cut, after);
                return old.scrollLock();
            });
        }
        @Override public int read(CdTimeWarpSaveData.Source source) {
            Objects.requireNonNull(source, "reached native warp-save source");
            return readChecked(() -> switch (source) {
                case SPAWN_MODE -> levelGlobals.spawnMode();
                case RINGS -> ringGlobals.rings();
                case LIVES_FLAGS -> ringGlobals.livesFlags();
                case WATER_ROUTINE -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.WATER_ROUTINE);
                case WATER_HEIGHT2 -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.WATER_HEIGHT2);
                case WATER_FULLSCREEN -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.WATER_FULLSCREEN);
                case TIME_LONG -> CdCheckpointLiveAux.read(checkpointState.live(), CdCheckpointLiveAux.Field.TIME_LONG);
                case FG_X, FG_Y, BG_X, BG_Y, BG2_X, BG2_Y, BG3_X, BG3_Y -> camera(source);
                case PRIMARY_X, PRIMARY_Y, PRIMARY_GVEL, PRIMARY_X_VELOCITY, PRIMARY_Y_VELOCITY,
                        PRIMARY_FLAGS, MINI_SONIC, BOTTOM_BOUND ->
                        throw new IllegalStateException("Body must supply its actual local TimeWarp source");
            });
        }
        private int camera(CdTimeWarpSaveData.Source source) {
            if (readyLock == null) throw new IllegalStateException("Warp camera read preceded the first lock access");
            var cut = readyLock.expected(); var fg = cut.foreground(); var bg = cut.background();
            return switch (source) {
                case FG_X -> fg.cameraX();
                case FG_Y -> fg.cameraYWord();
                case BG_X -> bg.cameraBgXWord();
                case BG_Y -> bg.cameraBgYWord();
                case BG2_X -> bg.cameraBg2XWord();
                case BG2_Y -> bg.cameraBg2YWord();
                case BG3_X -> bg.cameraBg3XWord();
                case BG3_Y -> bg.cameraBg3YWord();
                default -> throw new IllegalArgumentException("Not a warp-save camera WORD");
            };
        }
    }

    /** Pure complete-value preflight after the one actual cached Body call, not helper replay. */
    private TimeWarpLockPublication prepareTimeWarpSave(CdPlayerTimeWarp.SaveResult saved,
                                                       TimeWarpBodyReads reads) {
        if (reads == null || reads.readyLock == null)
            throw new IllegalStateException("Warp-save output lacks its actual reached world read");
        var lock = reads.readyLock;
        if (!saved.expectedSavedState().equals(timeWarpSavedState)
                || saved.expectedScrollLockWord() != lock.expected().foreground().scrollLock()
                || saved.finalScrollLockWord() != lock.finalForeground().scrollLock()
                || saved.expectedSpawnByte() != levelGlobals.spawnMode() || saved.finalSpawnByte() != 2
                || saved.expectedPeriodByte() != reads.expectedEnvironment.movement().gates().timeZone()
                || saved.finalPeriodByte() != frame.environment().movement().gates().timeZone())
            throw new IllegalStateException("Stale or mismatched native warp-save publication");
        var effects = saved.effects(); int at = 0;
        if (effects.size() != CdTimeWarpSaveData.Store.values().length + 3)
            throw new IllegalStateException("Native warp-save effect count differs");
        if (!(effects.get(at++) instanceof CdPlayerTimeWarp.LockWrite lockWrite)
                || lockWrite.expected() != saved.expectedScrollLockWord() || lockWrite.value() != saved.finalScrollLockWord())
            throw new IllegalStateException("Native warp-save lock order differs");
        // The bound R11A program admits rawPresent1 and active80..82, never rawPast0.
        // SCMD_FADEPCM therefore remains a pure-helper branch until that program is admitted.
        if (saved.expectedPeriodByte() == 0 || effects.stream().anyMatch(CdPlayerTimeWarp.SubCpuRequest.class::isInstance))
            throw new IllegalStateException("Unadmitted raw-Past PCM fade in the executing R11A program");
        if (!(effects.get(at++) instanceof CdPlayerTimeWarp.PeriodWrite periodWrite)
                || periodWrite.expected() != saved.expectedPeriodByte() || periodWrite.value() != saved.finalPeriodByte())
            throw new IllegalStateException("Native warp-save period order differs");
        var current = saved.expectedSavedState();
        for (var store : CdTimeWarpSaveData.Store.values()) {
            if (at >= effects.size() || !(effects.get(at++) instanceof CdPlayerTimeWarp.SavedWrite savedWrite)
                    || savedWrite.call().store() != store || !savedWrite.call().expectedState().equals(current))
                throw new IllegalStateException("Native warp-save ordered saved prefix differs");
            current = savedWrite.call().finalState();
        }
        if (!current.equals(saved.finalSavedState()) || at + 1 != effects.size()
                || !(effects.get(at) instanceof CdPlayerTimeWarp.SpawnWrite spawnWrite)
                || spawnWrite.expected() != saved.expectedSpawnByte() || spawnWrite.value() != 2)
            throw new IllegalStateException("Native warp-save final spawn/value differs");
        return lock; // Callback-free: the actual completed Frame baseline is latched before validation below.
    }

    /** ObjSonic_TimeWarp:608-611: the HIGH byte write and one fade request precede its returned call. */
    private CdLevelGlobals prepareTimeWarpRestart(CdPlayerTimeWarp.Result result,
                                                  List<CdPlayerBody.Effect> effects, int callIndex) {
        var restart = result.restartResult().orElseThrow();
        if (restart.expectedRestartWord() != levelGlobals.levelRestart()
                || restart.finalRestartWord() != ((levelGlobals.levelRestart() & 255) | 0x100)
                || result.state().timer() < 230 || result.saveResult().isPresent()
                || !result.stores().isEmpty() || !result.reservedPatches().isEmpty()
                || callIndex < 2
                || !(effects.get(callIndex - 2) instanceof CdPlayerBody.LevelWrite write)
                || write.field() != CdPlayerBody.LevelField.LEVEL_RESTART || write.value() != restart.finalRestartWord()
                || !(effects.get(callIndex - 1) instanceof CdPlayerBody.SoundRequest sound)
                || sound.sound() != CdPlayerBody.Sound.SCMD_FADECDA)
            throw new IllegalStateException("Native warp restart word/fade order differs from its actual Body cut");
        var old = levelGlobals;
        return new CdLevelGlobals(restart.finalRestartWord(), old.updateHudTime(), old.checkpoint(), old.spawnMode(),
                old.plcLoadFlags(), old.flowerCount(), old.fadeEnableDisplay(), old.powerup(), old.timeOver());
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
            var reservedCut = reserved.capture();
            var context = new CdPlayerBody.ResponseContext(touchResponse, contactGlobals,
                    new CdPlayerTouchResponse.WorldGlobals(ringGlobals.rings(), ringGlobals.lives(),
                            ringGlobals.updateHudLives(), scoreGlobals.score(), scoreGlobals.nextLifeScore(),
                            scoreGlobals.updateHudScore()));
            var level = new CdPlayerBody.LevelContext(callCut, ringGlobals, levelGlobals);
            var player = frame.state();
            var objectContext = new CdPlayerMovement.ObjectContext(callCut, player.objectLinks().standObject(),
                    player.header().slotId(), player.header().width());
            var warpReads = timeWarpBindings.isPresent() ? new TimeWarpBodyReads(callCut, reservedCut) : null;
            var warpContext = new CdPlayerTimeWarp.Context(OptionalInt.of(levelGlobals.spawnMode()), Optional.of(reservedCut),
                    warpReads == null ? Optional.empty()
                            : Optional.of(new CdPlayerTimeWarp.SaveContext(timeWarpSavedState, warpReads)),
                    OptionalInt.of(levelGlobals.levelRestart()));
            var publication = frame.runBody(levelFrames,
                    Optional.of(new CdPlayerBody.TouchContext(scanner, callCut, context)), Optional.of(level),
                    Optional.of(objectContext), Optional.of(new CdPlayerBody.TimeWarpContext(
                            warpContext, contactGlobals)));
            checkActive(Phase.PLAYER_BODY);
            // Borrow the complete actual completed Frame BEFORE any post-Body world callback.
            var completedActor = warpReads == null ? null : frame.captureActor();
            var completedShared = warpReads == null ? null : frame.captureShared();
            boolean displaySeen = false;
            boolean touchSeen = false;
            CdPlayerBody.DeadCheckGone gone = null; CdPlayerBody.RestartCut restart = null;
            var changed = new LinkedHashMap<Integer, CdObjectState>();
            var changedReserved = new ArrayList<CdReservedObjectBank.Image>();
            boolean timeWarpSeen = false;
            var nextRing = ringGlobals; var nextScore = scoreGlobals; var nextContact = contactGlobals;
            var nextLevel = levelGlobals; var nextWarpSaved = timeWarpSavedState;
            TimeWarpLockPublication readyWarpLock = null;
            // No Body owner writes live pool RAM. Even same-slot MODE and later touch images fold in source order
            // into one final publication, validated/CASed against the ORIGINAL pre-player cut below.
            var bodyEffects = publication.result().effects();
            for (int effectIndex = 0; effectIndex < bodyEffects.size(); effectIndex++) {
                var effect = bodyEffects.get(effectIndex);
                if (effect instanceof CdPlayerBody.TimeWarpCall warp) {
                    if (timeWarpSeen) throw new IllegalStateException("Body returned more than one TimeWarp call");
                    timeWarpSeen = true;
                    var result = warp.result();
                    nextContact = new CdPlayerBody.ContactGlobals(result.state().timeWarp(), nextContact.shield(), nextContact.combineRing());
                    if (result.saveResult().isPresent()) {
                        var saved = result.saveResult().orElseThrow();
                        readyWarpLock = prepareTimeWarpSave(saved, warpReads);
                        nextWarpSaved = saved.finalSavedState();
                        var old = levelGlobals;
                        nextLevel = new CdLevelGlobals(old.levelRestart(), old.updateHudTime(), old.checkpoint(), 2,
                                old.plcLoadFlags(), old.flowerCount(), old.fadeEnableDisplay(), old.powerup(), old.timeOver());
                    }
                    if (result.restartResult().isPresent())
                        nextLevel = prepareTimeWarpRestart(result, bodyEffects, effectIndex);
                    for (var patch : result.reservedPatches()) {
                        int whole = patch.before().identity().wholeIndex();
                        if (!reservedCut.slots().get(whole - CdReservedObjectBank.FIRST_WHOLE_INDEX).equals(patch.before())) {
                            throw new IllegalStateException("Stale TimeWarp reserved source image");
                        }
                        changedReserved.add(patch.after());
                    }
                } else if (effect instanceof CdPlayerBody.ModeEffect mode
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
            reserved.validateRestore(reservedCut);
            if (!reserved.capture().slots().equals(reservedCut.slots())) {
                throw new IllegalStateException("CD reserved bank changed during its pure player call");
            }
            if (gone != null || restart != null) {
                if (touchSeen || !changed.isEmpty() || timeWarpSeen) throw new IllegalStateException("Level summary cannot also publish MODE/touch/warp slots");
                if (gone != null) commitDeadGone(callCut, gone, publication.result().effects());
                else commitRestart(restart, publication.result().effects());
            } else {
                var readyImages = List.copyOf(changed.values());
                var readyReserved = List.copyOf(changedReserved);
                preflightTouchImages(callCut, readyImages);
                if (readyWarpLock != null) {
                    var world = timeWarpBindings.orElseThrow().world();
                    var expectedWorld = new TimeWarpWorldCut(readyWarpLock.finalForeground(), readyWarpLock.expected().background());
                    var lock = readyWarpLock;
                    timeWarpWorldCall(warpReads, completedActor, completedShared, () -> { world.validate(lock); return null; });
                    timeWarpWorldCall(warpReads, completedActor, completedShared, () -> { world.apply(lock); return null; });
                    var actualWorld = timeWarpWorldCall(warpReads, completedActor, completedShared, world::read);
                    if (!expectedWorld.equals(actualWorld))
                        throw new IllegalStateException("Warp-save world did not adopt exactly the ready lock projection");
                }
                if (!reserved.compareAndSet(reservedCut, readyReserved)) {
                    throw new IllegalStateException("CD reserved bank changed before TimeWarp adoption");
                }
                for (var image : readyImages) pool.write(image);
                timeWarpSavedState = nextWarpSaved; levelGlobals = nextLevel;
                ringGlobals = nextRing; scoreGlobals = nextScore; contactGlobals = nextContact;
            }
            for (var sound : publication.sounds()) { appendSound(new PlayerSound(sound)); }
            return publication;
        });
    }

    private <T> T timeWarpWorldCall(TimeWarpBodyReads reads, CdPlayerFrameRuntime.ActorState actor,
                                    CdPlayerFrameRuntime.SharedState shared, Supplier<T> callback) {
        verifyTimeWarpWorldNonwrites(reads, actor, shared);
        T result;
        try { result = callback.get(); }
        catch (RuntimeException | Error failure) {
            try { verifyTimeWarpWorldNonwrites(reads, actor, shared); }
            catch (RuntimeException | Error verification) { failure.addSuppressed(verification); }
            throw failure;
        }
        verifyTimeWarpWorldNonwrites(reads, actor, shared);
        return result;
    }

    private void verifyTimeWarpWorldNonwrites(TimeWarpBodyReads reads, CdPlayerFrameRuntime.ActorState actor,
                                              CdPlayerFrameRuntime.SharedState shared) {
        checkActive(Phase.PLAYER_BODY);
        // These existing closed-cut guards also reject pending input or spent/unspent Body entitlement.
        var actualActor = frame.captureActor(); var actualShared = frame.captureShared();
        if (frame.state() != actor.state() || frame.environment() != shared.environment()
                || !actualActor.equals(actor) || !actualShared.equals(shared)
                || levelGlobals != reads.expectedLevel || ringGlobals != reads.expectedRings
                || checkpointState != reads.expectedCheckpoint || timeWarpSavedState != reads.expectedSaved
                || !placement.pool().capture().slots().equals(reads.pool.slots())
                || !placement.status().equals(reads.expectedPlacement.status())
                || !java.util.Arrays.equals(placement.capture().savedFlags().copy(), reads.expectedPlacement.savedFlags().copy())
                || !reserved.capture().slots().equals(reads.reservedCut.slots()))
            throw new IllegalStateException("Warp-save world publication changed another actual owner");
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
        preflightTouchImages(cut, changed);
        for (var image : changed) placement.pool().write(image);
    }

    private void preflightTouchImages(CdObjectPool.Snapshot cut, List<CdObjectState> changed) {
        var pool = placement.pool();
        pool.validateRestore(cut);
        for (var image : changed) {
            pool.validateWrite(image);
            var expected = cut.slots().get(image.identity().dynamicIndex());
            if (!pool.state(image.identity().wholeIndex()).equals(expected)) {
                throw new IllegalStateException("CD touch slot changed after its call-cut at " + image.identity().wholeIndex());
            }
        }
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
            runTimeStarSlots();
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
                } else if (id == CdTamabboh.OBJECT_ID) {
                    // ObjTamabboh (Tamabboh/Main.asm:7-18,213-224), pin 1ecc5f3a:
                    // one live physical visit; the existing increasing walk alone visits later children.
                    if (activeTamabbohInvocation != null) {
                        throw new IllegalStateException("Tamabboh cannot nest its cached-body invocation");
                    }
                    var globals = new CdTamabboh.Globals(devGlobals.goodFuture(), gates.timeZone(),
                            gates.timeWarpDirection(), environment.globals().debugMode(), preScrollForeground.xWord(),
                            frame.environment().movement().globals().primaryAngle());
                    activeTamabbohInvocation = new TamabbohInvocation(tamabboh, whole, frame.state());
                    try {
                        tamabboh.run(whole, globals); // Completed effects are observations, never replay commands.
                    } finally {
                        activeTamabbohInvocation = null; // Retire on completion, native boundary, exception or Error.
                    }
                } else if (id == CdLogShadow.OBJECT_ID) {
                    // ObjLogShadow (Log Shadow/Main.asm:7-13,22-54), pin 1ecc5f3a: one live physical visit.
                    // The cached body owns Init, Draw and despawn; completed Result effects are never replayed.
                    logShadow.run(whole, new CdLogShadow.Globals(gates.timeZone(), devGlobals.goodFuture(),
                            gates.zoneAct() & 0xFF, gates.timeWarpDirection(), preScrollForeground.xWord()));
                } else if (id == CdSonicHole.OBJECT_ID) {
                    // ObjSonicHole (Tunnel Path/Main.asm:11-103): one current cached-body visit, no effect replay.
                    runSonicHole(whole);
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
                } else if (id == CdTunnelPath.OBJECT_ID) {
                    // Actual physical ID07; all native stores and FM happen inside the cached producer.
                    runTunnelPath(whole); // Returned observations never execute or replay work.
                } else if (id == CdSpinningDisc.OBJECT_ID) {
                    // Actual physical pre-scroll visit. The cached body owns every reached helper and store.
                    runSpinningDisc(whole); // Completed observations never cause replay or a second helper.
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

    /**
     * The module's actual old-LevelStart scope owns scalar entry flags and calls this once before
     * its marked-period fade. Existing Ring/score/saved owners are not reset on the loaded-PLC path.
     * ClearPLCs cancels saved decoder/count state, retaining NEMBUFFER and residents; first-load reset remains separate.
     */
    public void beginWarpLevelStart(boolean forceGoodFuture) {
        perform(Phase.CLOSED_CUT, Phase.LEVEL_START, Phase.CLOSED_CUT, () -> {
            requireWarpLevelStartCut();
            if ((levelGlobals.plcLoadFlags() & 1) == 0) {
                throw new UnsupportedOperationException("LevelStart first-load ResetSavedObjFlags is not this old-level exit");
            }
            var l = levelGlobals;
            var nextLevel = new CdLevelGlobals(l.levelRestart(), l.updateHudTime(), l.checkpoint(), l.spawnMode(),
                    l.plcLoadFlags() | 1, l.flowerCount(), l.fadeEnableDisplay(), l.powerup(), l.timeOver());
            var nextDev = forceGoodFuture ? new DevGlobals(1, devGlobals.projDestroyed()) : devGlobals;
            frame.beginLevelStart();
            devGlobals = nextDev;
            levelGlobals = nextLevel;
            sectionArtState = sectionArt.clearPlcs(sectionArtState);
            return null;
        });
    }

    /** One real selector12 wait, then native FadeColorsToWhite and ProcessPLCs; no playable loop or art step. */
    public void serviceLevelStartWhiteFade(int heldByte) {
        perform(Phase.CLOSED_CUT, Phase.LEVEL_START, Phase.CLOSED_CUT, () -> {
            requireWarpLevelStartCut();
            frame.serviceLevelPaletteFade(heldByte, () -> {
                checkActive(Phase.LEVEL_START);
                palettes = new PaletteState(palettes.cycle(), palettes.waterPalette(), palettes.cycle().palette());
                // Native VInt_PalFade increments this existing byte only when it is exactly1.
                if (levelGlobals.fadeEnableDisplay() == 1) {
                    var l = levelGlobals;
                    levelGlobals = new CdLevelGlobals(l.levelRestart(), l.updateHudTime(), l.checkpoint(), l.spawnMode(),
                            l.plcLoadFlags(), l.flowerCount(), 2, l.powerup(), l.timeOver());
                }
            }, () -> {
                checkActive(Phase.LEVEL_START);
                try { sectionArtState = sectionArt.serviceFast(sectionArtState); }
                catch (CdR11aSectionArt.SectionArtException stopped) { sectionArtState = stopped.state(); throw stopped; }
            });
            checkActive(Phase.LEVEL_START);
            // The same prior CPU SAT is displayed with NOW-resident pixels; no Sonic DPLC or object runs.
            var pixels = CdObjectPresentation.usaR11aCurrentCameraDevPreloadedArt(frame.artState(), powerupArtState,
                    objects, frontierArt, contactArt, floatBlockArt, springArt, tunnelDoorArt, monitorTimeArt, sectionArtState.residency(),
                    banks.player(), banks.ring(), banks.mosqui(), banks.plant(), banks.ramp(), banks.explosion(), banks.points(),
                    banks.flower(), banks.powerup(), banks.floatBlock(), banks.spring(), banks.tunnelDoorVertical(), banks.monitorTime(), banks.boulder(), banks.diagonalSpring(), banks.spikes(), banks.checkpoint(), banks.anton(), banks.collapsePlatform(), banks.tamabboh(), banks.logShadow());
            displayed = CdObjectPresentation.convert(queue.cpuSat(), pixels);
            var faded = CdLevelPaletteFade.stepToWhite(new CdLevelPaletteFade.Palettes(
                    palettes.cycle().palette(), palettes.waterPalette()));
            palettes = new PaletteState(new CdLevelPaletteCycle.State(faded.palette(),
                    palettes.cycle().timers(), palettes.cycle().steps()), faded.waterPalette(), palettes.displayedPalette());
            try { sectionArtState = sectionArt.prepare(sectionArtState); }
            catch (CdR11aSectionArt.SectionArtException stopped) { sectionArtState = stopped.state(); throw stopped; }
            return null;
        });
    }

    /** LevelStart:72-81 clears direction before the complete restart WORD; the raw destination marker survives act0. */
    public void finishWarpLevelStart() {
        perform(Phase.CLOSED_CUT, Phase.LEVEL_START, Phase.CLOSED_CUT, () -> {
            requireWarpLevelStartCut();
            var l = levelGlobals;
            var next = new CdLevelGlobals(0, l.updateHudTime(), l.checkpoint(), l.spawnMode(), l.plcLoadFlags(),
                    l.flowerCount(), l.fadeEnableDisplay(), l.powerup(), l.timeOver());
            frame.clearLevelStartWarpDirection();
            levelGlobals = next;
            return null;
        });
    }

    private void requireWarpLevelStartCut() {
        frame.captureShared(); // Existing closed-Frame guard, no new capture owner or service.
        var gates = frame.environment().movement().gates();
        if ((levelGlobals.levelRestart() & 0xFF00) != 0x0100 || levelGlobals.spawnMode() != 2
                || gates.zoneAct() != 0 || gates.timeZone() < 0x80 || gates.timeZone() > 0x82) {
            throw new IllegalStateException("Old LevelStart requires this actual marked R11A restart cut");
        }
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
                // Main:423-425: full-WORD time stop skips the entire native palette call.
                // Preserve its completed prefix before any later section/PLC failure.
                if (ringGlobals.timeStopTimer() == 0) {
                    var result = CdLevelPaletteCycle.pass(paletteScripts, palettes.cycle());
                    palettes = new PaletteState(result.state(), palettes.waterPalette(), palettes.displayedPalette());
                    if (!result.completed()) {
                        throw new UnsupportedOperationException("R11A palette cycle " + result.fault().orElseThrow());
                    }
                }
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
                devGlobals, contactGlobals, scoreGlobals, levelGlobals, powerupArtState, sectionArtState, checkpointState, timeWarpSavedState, palettes);
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
        Objects.requireNonNull(snapshot.timeWarpSavedState, "captured distinct time-warp saved value");
        Objects.requireNonNull(snapshot.palettes, "captured current and displayed palettes");
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
            timeWarpSavedState = snapshot.timeWarpSavedState; // Plain value adoption, never SaveTimeWarpData replay.
            palettes = snapshot.palettes; // No cycle, V-int, colour step or publication is replayed by restore.
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

    /** Only the actual cached run can establish origin; ordinary call records never do. */
    private CdObjectState requireTamabbohCallback() {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeTamabbohInvocation;
        if (invocation == null || invocation.body() != tamabboh || invocation.wholeIndex() != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || frame.state() != invocation.player()
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm
                || inMonitorAngle || inMonitorFm || inMonitorAward || inTimePostDirection || inTimePostSubCpu) {
            throw new IllegalStateException("Tamabboh port requires its actual synchronous cached-body invocation and stable Frame player");
        }
        return placement.pool().state(dispatchWholeIndex);
    }

    /** Full same-owner96/source CAS, including every current byte and the invocation's actual Frame state. */
    private void validateTamabbohCut(CdObjectPool.Snapshot cut, CdObjectState source) {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeTamabbohInvocation;
        if (invocation == null || invocation.body() != tamabboh || invocation.wholeIndex() != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || frame.state() != invocation.player()) {
            throw new IllegalStateException("Tamabboh cut has no current cached-body/stable-Frame origin");
        }
        var pool = placement.pool();
        validateDynamicCapture(cut);
        pool.validateWrite(source);
        var live = pool.state(dispatchWholeIndex);
        if (source.identity().wholeIndex() != dispatchWholeIndex || !live.identity().equals(source.identity())
                || live.id() != CdTamabboh.OBJECT_ID || !live.equals(source)
                || !cut.slots().get(source.identity().dynamicIndex()).equals(source)
                || !pool.capture().slots().equals(cut.slots())) {
            throw new IllegalStateException("Tamabboh port differs from its actual current full96/source cut");
        }
    }

    /**
     * ObjTamabboh_Position/Main and ObjTamabbohMissile_Main (Tamabboh/Main.asm:62-72,94-109,256-280),
     * ObjGetFloorDist (Collision Check.asm:157-179), pin 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff.
     * Init already advanced to2; body floor sites are2/4, missile floor site is2. Preserve every reached
     * store, including same-value pre-zero. Frame environment owns the sole byte; its player stays unchanged.
     */
    private void commitTamabbohPrimaryAngle(CdTamabboh.PrimaryAngleCall call) {
        var live = requireTamabbohCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "reached Tamabboh primary-angle call");
            validateTamabbohCut(call.callCut(), call.expectedSource());
            if ((live.subtype() == 1 ? live.routine() != 2 : live.routine() != 2 && live.routine() != 4)
                    || frame.environment().movement().globals().primaryAngle() != call.expectedPrimaryAngle()) {
                throw new IllegalStateException("Tamabboh angle call is not its reached floor/expected Frame byte cut");
            }
            // Full preflight precedes exactly ONE publication. No legacy writer or completed-effect replay.
            frame.applyObjectPrimaryAngle(call.value());
            validateTamabbohCut(call.callCut(), call.expectedSource());
        } finally {
            inSolidCommit = false;
        }
    }

    /**
     * ObjTamabboh_Main/CheckInRange (Tamabboh/Main.asm:76-91,130-144), pin 1ecc5f3a.
     * Authentic run owns Y-first and conditional-X sequence; do not replay its helper, pre-read positions,
     * infer a callback stage or retain another player authority. Read only the actually requested WORD.
     */
    private int readTamabbohPlayerWord(CdTamabboh.PlayerReadCall call) {
        var live = requireTamabbohCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "reached Tamabboh player-word read");
            validateTamabbohCut(call.callCut(), call.expectedSource());
            if (live.routine() != 4 || live.subtype() != 0 || live.word(TAMABBOH_COOLDOWN) != 0
                    || frame.environment().globals().debugMode() != 0) {
                throw new IllegalStateException("Tamabboh player read is not its reached Main range cut");
            }
            var player = frame.state().motion();
            int value = switch (call.field()) {
                case Y_WORD -> player.centreY16_16() >>> 16;
                case X_WORD -> player.centreX16_16() >>> 16;
            };
            validateTamabbohCut(call.callCut(), call.expectedSource());
            return value;
        } finally {
            inSolidCommit = false;
        }
    }

    /**
     * ObjTamabboh_Fire (Tamabboh/Main.asm:162-177), pin 1ecc5f3a: parent prefix precedes first Find,
     * prior negative sprite sign admits FM_A0 BEFORE any first-child byte. A first-free candidate is still
     * ID0, never allocated/reserved here. This existing DEV ledger policy does not execute PlayFMSound's
     * three native queue bytes (Functions (General).asm:288-310) or playback.
     */
    private void playTamabbohFm(CdTamabboh.FmCall call) {
        var live = requireTamabbohCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "reached Tamabboh FM call");
            validateTamabbohCut(call.callCut(), call.expectedSource());
            var pool = placement.pool();
            if (live.routine() != 4 || live.subtype() != 0 || live.word(TAMABBOH_COOLDOWN) != 0x78
                    || live.unsignedByte(ANIMATION) != 0 || live.signedByte(SPRITE_FLAGS) >= 0
                    || call.sound() != CdTamabboh.FmSound.FM_A0
                    || pool.findObjSlot().orElse(-1) != call.firstFreeWholeIndex()
                    || pool.state(call.firstFreeWholeIndex()).id() != 0) {
                throw new IllegalStateException("Tamabboh FM_A0 requires its actual Fire prefix and untouched first-free ID0 candidate");
            }
            var ledger = new ArrayList<SoundRequest>(sounds);
            ledger.add(new TamabbohFm(call.sound()));
            var nextSounds = List.copyOf(ledger); // Build complete immutable next value BEFORE the only assignment.
            validateTamabbohCut(call.callCut(), call.expectedSource());
            sounds = nextSounds;
        } finally {
            inSolidCommit = false;
        }
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

    // BEGIN Tunnel07 Runtime R1. Authored source only; author execution is UNRUN.
    // N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Tunnel Path/Main.asm:119-388;
    // Object Spawner.asm:385-446, Object Functions.asm:228-237. No FixBugs alternative here.
    // Private actual cached invocation grants producer authority; public values/enums/effects do not.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

    private void runTunnelPath(int wholeIndex) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (activeTunnelPathInvocation != null || activeSpinningDiscInvocation != null
                || activeCollapseInvocation != null || activePlatformInvocation != null || activeTamabbohInvocation != null
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu || inCheckpointDispatch
                || wholeIndex != dispatchWholeIndex || placement.pool().state(wholeIndex).id() != CdTunnelPath.OBJECT_ID) {
            throw new IllegalStateException("Tunnel Path requires its actual nonnested physical ID07 visit");
        }
        var gates = frame.environment().movement().gates();
        var globals = new CdTunnelPath.Globals(gates.timeZone(), gates.timeWarpDirection(), preScrollForeground.xWord());
        var pool = placement.pool();
        activeTunnelPathInvocation = new TunnelPathInvocation(tunnelPath, wholeIndex, pool.capture(), pool.state(wholeIndex));
        try {
            tunnelPath.run(wholeIndex, globals);
        } finally {
            activeTunnelPathInvocation = null; // Success, native boundary, host exception and Error.
        }
    }

    /** Reads are admitted even at DONE/STOPPED; Body's animation/dead gates precede raw routine dispatch. */
    private CdObjectState requireTunnelPathCallback() {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeTunnelPathInvocation;
        if (invocation == null || invocation.body != tunnelPath || invocation.wholeIndex != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || activeSpinningDiscInvocation != null || activeCollapseInvocation != null
                || activePlatformInvocation != null || activeTamabbohInvocation != null
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu || inCheckpointDispatch
                || frame.assets() != assets || frame.environment().dynamicSlots() != CdPlayerBody.DynamicSlots.UNIMPLEMENTED) {
            throw new IllegalStateException("Tunnel callback has no current cached producer or has a nested owner");
        }
        frame.captureActor(); // Existing open/idle/closed-Frame guard only; no retained capture partition.
        var source = placement.pool().state(dispatchWholeIndex);
        if (source.id() != CdTunnelPath.OBJECT_ID) {
            throw new IllegalStateException("Tunnel callback lost its actual physical ID07 source");
        }
        // No routine/visibility/control gate here: a legitimate earlier native read must remain earlier.
        return source;
    }

    private CdTunnelPath.PlayerCut readTunnelPathPlayer() {
        var source = requireTunnelPathCallback();
        inSolidCommit = true;
        try {
            var cut = placement.pool().capture();
            var pair = new CdTunnelPath.PlayerCut(frame.state(), frame.environment());
            validateTunnelPathCut(cut, source, pair);
            return pair; // Reading never advances the mutation cursor or adopts a historical ingress.
        } finally { inSolidCommit = false; }
    }

    private void validateTunnelPathCut(CdObjectPool.Snapshot cut, CdObjectState source, CdTunnelPath.PlayerCut expected) {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeTunnelPathInvocation;
        if (invocation == null || invocation.body != tunnelPath || invocation.wholeIndex != dispatchWholeIndex) {
            throw new IllegalStateException("Tunnel full cut has no actual cached invocation");
        }
        Objects.requireNonNull(source, "Tunnel source64"); Objects.requireNonNull(expected, "Tunnel State8/Environment4");
        frame.captureActor();
        var pool = placement.pool(); validateDynamicCapture(cut); pool.validateWrite(source);
        var live = pool.state(dispatchWholeIndex);
        if (source.identity().wholeIndex() != dispatchWholeIndex || live.id() != CdTunnelPath.OBJECT_ID
                || !live.equals(source) || !cut.slots().get(source.identity().dynamicIndex()).equals(source)
                || !pool.capture().slots().equals(cut.slots())
                || expected.state() != frame.state() || expected.environment() != frame.environment()) {
            throw new IllegalStateException("Tunnel source64/full96/full State-Environment cut is stale or foreign");
        }
    }

    /**
     * Compare only an actually reached own-only prefix. These are read-only byte/operand checks;
     * no gameplay owner, planning algorithm, shadow owner, returned effect or store command runs.
     * Read callbacks deliberately do not call this: native Init/decrement/routine stores may be ongoing.
     */
    private void validateTunnelPathSource(CdObjectState source) {
        var invocation = activeTunnelPathInvocation;
        var before = invocation.lastSource;
        boolean[] writes = new boolean[CdObjectState.BYTE_SIZE];
        int[] ranges = {};
        boolean valid = true;
        switch (invocation.next) {
            case MAIN -> {
                if (invocation.ingressRoutine == 0) {
                    // Init has already completed its selected reads before this Main player publication.
                    int base = CdR11aObjectAssets.Table.TUNNEL_PATH_COORDINATES.range().nativeAddress();
                    int pointer = base + (short) tunnelPathWord(base + ((before.subtype() * 2) & 0xFE));
                    int header = tunnelPathWord(pointer);
                    valid = source.longBits(MAPPINGS) == CdR11aObjectAssets.Table.POWERUP_MAPPINGS.range().nativeAddress()
                            && source.spriteFlags() == 4 && source.unsignedByte(PRIORITY) == 1
                            && source.unsignedByte(WIDTH) == 0x10 && source.word(TILE) == 0x0541
                            && source.routine() == 2 && source.word(TUNNEL_PATH_INDEX_END) == header
                            && source.longBits(CdObjectState.LongField.TUNNEL_PATH_DATA) == pointer + 2
                            && source.word(TUNNEL_PATH_TARGET_X) == tunnelPathWord(pointer + 2)
                            && source.word(TUNNEL_PATH_TARGET_Y) == tunnelPathWord(pointer + 4);
                    ranges = new int[]{4, 4, 1, 1, 0x18, 2, 2, 2, 0x24, 1, 0x3A, 2, 0x3C, 4, 0x36, 4};
                }
            }
            case MOVE -> {
                valid = source.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_STEPS_HIGH)
                        == ((before.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_STEPS_HIGH) - 1) & 255);
                ranges = new int[]{0x2E, 1}; // HIGH byte only; the low byte is a nonwrite.
            }
            case NEXT_SPEED_OR_RELEASE -> {
                int index = (before.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_INDEX) + 4) & 255;
                if (index < before.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_END)) {
                    int pointer = before.longBits(CdObjectState.LongField.TUNNEL_PATH_DATA);
                    valid = source.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_INDEX) == index
                            && source.word(TUNNEL_PATH_TARGET_X) == tunnelPathWord(pointer + index)
                            && source.word(TUNNEL_PATH_TARGET_Y) == tunnelPathWord(pointer + index + 2);
                    ranges = new int[]{0x3A, 1, 0x36, 4};
                }
            }
            case GOT_FM -> {
                valid = source.routine() == ((before.routine() + 2) & 255);
                ranges = new int[]{0x24, 1};
            }
            default -> { } // No own-only native store between the previous publication and this reached site.
        }
        for (int index = 0; index < ranges.length; index += 2) {
            for (int at = ranges[index]; at < ranges[index] + ranges[index + 1]; at++) writes[at] = true;
        }
        byte[] oldBytes = before.copyBytes(), actualBytes = source.copyBytes();
        for (int at = 0; at < CdObjectState.BYTE_SIZE; at++) {
            if (!writes[at] && oldBytes[at] != actualBytes[at]) valid = false;
        }
        var pool = placement.pool(); pool.validateRestore(invocation.lastPool);
        var actual = pool.capture();
        for (int index = 0; index < CdObjectPool.DYNAMIC_COUNT; index++) {
            if (index != source.identity().dynamicIndex()
                    && !actual.slots().get(index).equals(invocation.lastPool.slots().get(index))) valid = false;
        }
        if (!valid) throw new IllegalStateException("Tunnel source/full96 differs from its actually reached native prefix");
    }

    /** Read an already reached validation operand, retaining raw32 arithmetic and the native 24-bit bus. */
    private int tunnelPathWord(int addressBits) {
        if ((addressBits & 1) != 0) throw new IllegalStateException("A published Tunnel cut followed an unaligned native WORD");
        var speeds = objects.view(CdR11aObjectAssets.Table.TUNNEL_PATH_GROUND_SPEEDS);
        var paths = objects.view(CdR11aObjectAssets.Table.TUNNEL_PATH_COORDINATES);
        int word = 0;
        for (int index = 0; index < 2; index++) {
            int bus = (addressBits + index) & 0xFFFFFF, selected = -1;
            int speedOffset = (bus - (speeds.range().nativeAddress() & 0xFFFFFF)) & 0xFFFFFF;
            int pathOffset = (bus - (paths.range().nativeAddress() & 0xFFFFFF)) & 0xFFFFFF;
            if (speedOffset < 8 && speedOffset < speeds.bytes().length()) selected = speeds.bytes().unsignedByte(speedOffset);
            if (pathOffset < 284 && pathOffset < paths.bytes().length()) {
                int value = paths.bytes().unsignedByte(pathOffset);
                if (selected >= 0 && selected != value) throw new IllegalStateException("Ambiguous reached Tunnel bus byte");
                selected = value;
            }
            if (selected < 0) throw new IllegalStateException("A published Tunnel cut followed an unowned native WORD");
            word = (word << 8) | selected;
        }
        return word;
    }

    /** Independent finite old/value/width/order checks over reached native stores; produces no gameplay result. */
    private List<CdTunnelPath.NativeStore> tunnelPathExpectedStores(CdTunnelPath.PlayerCall call) {
        var source = call.expectedSource(); var state = call.expectedPlayer().state(); var m = state.motion();
        var stores = new ArrayList<CdTunnelPath.NativeStore>();
        switch (call.site()) {
            case ACQUIRE_BASE -> {
                if (source.routine() != 2 || m.animation() == 0x2B || m.routine() >= 6 || m.playerControl() != 0
                        || ((((m.centreX16_16() >>> 16) - source.centreX() + 16) & 0xFFFF) >= 32)
                        || ((((m.centreY16_16() >>> 16) - source.centreY() + 16) & 0xFFFF) >= 32)) {
                    throw new IllegalStateException("Tunnel acquisition is outside its actual Main branches");
                }
                if (m.routine() == 4) {
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.HURT_ROUTINE, 4, 2));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.HURT_TIMER, state.timers().hurt(), 0x78));
                }
                stores.add(new CdTunnelPath.ObjectStore(CdTunnelPath.ObjectField.ACQUIRE_ROUTINE, source.routine(), 4));
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_CONTROL, m.playerControl(), 0x81));
                if (source.subtype2() != 0) stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_INVISIBLE, 0x81, 0xC1));
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_ANIMATION, m.animation(), 2));
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_GVEL_BASE, m.groundVelocity() & 0xFFFF, 0x1000));
            }
            case ACQUIRE_GVEL_RAISE, ACQUIRE_FINISH -> {
                if (source.routine() != 4 || m.animation() != 2
                        || m.playerControl() != (source.subtype2() == 0 ? 0x81 : 0xC1)) {
                    throw new IllegalStateException("Tunnel acquisition continuation lost its actual published base");
                }
                boolean needsRaise = false; int selected = 0;
                if (activeTunnelPathInvocation.next == TunnelStep.ACQUIRE_GVEL_OR_FINISH && (source.subtype() & 0x80) == 0) {
                    // The Body already returned from this selected WORD read; never move it before ACQUIRE_BASE.
                    int base = CdR11aObjectAssets.Table.TUNNEL_PATH_GROUND_SPEEDS.range().nativeAddress();
                    selected = tunnelPathWord(base + 2 * (source.subtype() & 15));
                    needsRaise = (short) selected > 0x1000;
                }
                if (call.site() == CdTunnelPath.NativeCut.ACQUIRE_GVEL_RAISE) {
                    if (!needsRaise || (m.groundVelocity() & 0xFFFF) != 0x1000) {
                        throw new IllegalStateException("Tunnel GVel raise does not match the reached native signed comparison");
                    }
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_GVEL_RAISE, 0x1000, selected));
                } else {
                    if (needsRaise) throw new IllegalStateException("Tunnel finish skipped its reached GVel raise");
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_X_VELOCITY, m.xVelocity() & 0xFFFF, 0));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_Y_VELOCITY, m.yVelocity() & 0xFFFF, 0));
                    stores.add(new CdTunnelPath.ObjectStore(CdTunnelPath.ObjectField.ACQUIRE_PUSH_CLEAR, source.flags(), source.flags() & ~0x20));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_PUSH_CLEAR, m.flags(), m.flags() & ~0x20));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_AIR, m.flags() & ~0x20, (m.flags() & ~0x20) | 2));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_JUMP_CLEAR, m.playerJump(), 0));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_X_SNAP, m.centreX16_16() >>> 16, source.centreX()));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.ACQUIRE_Y_SNAP, m.centreY16_16() >>> 16, source.centreY()));
                    stores.add(new CdTunnelPath.ObjectStore(CdTunnelPath.ObjectField.ACQUIRE_SCRATCH_CLEAR,
                            source.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_SCRATCH), 0));
                }
            }
            case SPEED_Y, SPEED_X -> {
                int requiredRoutine = activeTunnelPathInvocation.next == TunnelStep.GOT_SPEED ? 4 : 6;
                if (source.routine() != requiredRoutine || m.animation() == 0x2B || m.routine() >= 6) {
                    throw new IllegalStateException("Tunnel speed is outside the actual admitted Got/next-target source");
                }
                int x = m.centreX16_16() >>> 16, y = m.centreY16_16() >>> 16;
                int tx = source.word(TUNNEL_PATH_TARGET_X), ty = source.word(TUNNEL_PATH_TARGET_Y);
                int dx = (tx - x) & 0xFFFF, dy = (ty - y) & 0xFFFF, mx = dx, my = dy;
                int sx = m.groundVelocity() & 0xFFFF, sy = sx;
                if (!tunnelPathWordBge(tx, x)) { mx = (-mx) & 0xFFFF; sx = (-sx) & 0xFFFF; }
                if (!tunnelPathWordBge(ty, y)) { my = (-my) & 0xFFFF; sy = (-sy) & 0xFFFF; }
                if (my >= mx) {
                    if (call.site() != CdTunnelPath.NativeCut.SPEED_Y) throw new IllegalStateException("Tunnel speed skipped its Y/tie branch");
                    int time = tunnelPathSignedDivide(dy << 16, sy);
                    int xv = dx == 0 ? 0 : tunnelPathSignedDivide(dx << 16, time & 0xFFFF);
                    int steps = time & 0xFFFF; if ((short) steps < 0) steps = (-steps) & 0xFFFF;
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.SPEED_Y_X_VELOCITY, m.xVelocity() & 0xFFFF, xv & 0xFFFF));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.SPEED_Y_Y_VELOCITY, m.yVelocity() & 0xFFFF, sy));
                    stores.add(new CdTunnelPath.ObjectStore(CdTunnelPath.ObjectField.SPEED_STEPS_Y, source.word(TUNNEL_PATH_STEPS), steps));
                } else {
                    if (call.site() != CdTunnelPath.NativeCut.SPEED_X) throw new IllegalStateException("Tunnel speed skipped its X branch");
                    int time = tunnelPathSignedDivide(dx << 16, sx);
                    int yv = dy == 0 ? 0 : tunnelPathSignedDivide(dy << 16, time & 0xFFFF);
                    int steps = time & 0xFFFF; if ((short) steps < 0) steps = (-steps) & 0xFFFF;
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.SPEED_X_Y_VELOCITY, m.yVelocity() & 0xFFFF, yv & 0xFFFF));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.SPEED_X_X_VELOCITY, m.xVelocity() & 0xFFFF, sx));
                    stores.add(new CdTunnelPath.ObjectStore(CdTunnelPath.ObjectField.SPEED_STEPS_X, source.word(TUNNEL_PATH_STEPS), steps));
                }
            }
            case COMPLETE_SNAP, INTEGRATE -> {
                boolean integrate = source.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_STEPS_HIGH) < 0x80;
                if (source.routine() != 6 || m.animation() == 0x2B || m.routine() >= 6
                        || integrate != (call.site() == CdTunnelPath.NativeCut.INTEGRATE)) {
                    throw new IllegalStateException("Tunnel Move site does not match the actual high-byte decrement branch");
                }
                if (integrate) {
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.MOVE_X_LONG,
                            m.centreX16_16(), m.centreX16_16() + ((short) m.xVelocity() << 8)));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.MOVE_Y_LONG,
                            m.centreY16_16(), m.centreY16_16() + ((short) m.yVelocity() << 8)));
                } else {
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.COMPLETE_X_SNAP,
                            m.centreX16_16() >>> 16, source.word(TUNNEL_PATH_TARGET_X)));
                    stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.COMPLETE_Y_SNAP,
                            m.centreY16_16() >>> 16, source.word(TUNNEL_PATH_TARGET_Y)));
                }
            }
            case RELEASE -> {
                int index = (source.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_INDEX) + 4) & 255;
                if (source.routine() != 6 || index < source.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_END)) {
                    throw new IllegalStateException("Tunnel release is outside its actual unsigned end branch");
                }
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.RELEASE_Y_MASK,
                        m.centreY16_16() >>> 16, (m.centreY16_16() >>> 16) & 0x7FF));
                stores.add(new CdTunnelPath.ObjectStore(CdTunnelPath.ObjectField.RELEASE_ROUTINE_CLEAR, source.routine(), 0));
                stores.add(new CdTunnelPath.PlayerStore(CdTunnelPath.TunnelPlayerField.RELEASE_CONTROL_CLEAR, m.playerControl(), 0));
                stores.add(new CdTunnelPath.ObjectStore(CdTunnelPath.ObjectField.RELEASE_ROUTINE_WORD,
                        source.word(TUNNEL_PATH_ROUTINE_WORD) & 255, 2)); // Native CLR.B then MOVE.W => bytes24/25=00/02.
            }
            default -> throw new IllegalStateException("Not one of the eight actual cross-owner Tunnel sites");
        }
        return List.copyOf(stores);
    }

    private boolean tunnelPathWordBge(int left, int right) {
        int result = (left - right) & 0xFFFF;
        boolean negative = (result & 0x8000) != 0, overflow = ((left ^ right) & (left ^ result) & 0x8000) != 0;
        return negative == overflow;
    }

    private int tunnelPathSignedDivide(int dividend, int divisorBits) {
        int divisor = (short) divisorBits;
        if (divisor == 0) throw new IllegalStateException("A Tunnel publication cannot follow native division by zero");
        long quotient = (long) dividend / divisor;
        if (quotient < -32768 || quotient > 32767) return dividend; // Native DIVS overflow leaves Dn unreplaced.
        int remainder = (int) ((long) dividend % divisor);
        return (remainder << 16) | ((int) quotient & 0xFFFF);
    }

    private TunnelStep tunnelPathNextStep(CdTunnelPath.PlayerCall call) {
        return switch (activeTunnelPathInvocation.next) {
            case MAIN -> {
                if (call.site() != CdTunnelPath.NativeCut.ACQUIRE_BASE) throw new IllegalStateException("Tunnel Main requires its first acquisition span");
                yield TunnelStep.ACQUIRE_GVEL_OR_FINISH;
            }
            case ACQUIRE_GVEL_OR_FINISH -> switch (call.site()) {
                case ACQUIRE_GVEL_RAISE -> TunnelStep.ACQUIRE_FINISH;
                case ACQUIRE_FINISH -> TunnelStep.MAIN_FM;
                default -> throw new IllegalStateException("Tunnel acquisition continuation is out of order");
            };
            case ACQUIRE_FINISH -> {
                if (call.site() != CdTunnelPath.NativeCut.ACQUIRE_FINISH) throw new IllegalStateException("Tunnel GVel requires its once-only finish span");
                yield TunnelStep.MAIN_FM;
            }
            case GOT_SPEED -> {
                if (call.site() != CdTunnelPath.NativeCut.SPEED_Y && call.site() != CdTunnelPath.NativeCut.SPEED_X) {
                    throw new IllegalStateException("Tunnel GotPlayer requires its reached speed span");
                }
                yield TunnelStep.GOT_FM;
            }
            case MOVE -> switch (call.site()) {
                case INTEGRATE -> TunnelStep.DONE;
                case COMPLETE_SNAP -> TunnelStep.NEXT_SPEED_OR_RELEASE;
                default -> throw new IllegalStateException("Tunnel Move requires its actual decrement-selected span");
            };
            case NEXT_SPEED_OR_RELEASE -> {
                // A next-target source already contains the new index; distinguish using the previous completed snap.
                var before = activeTunnelPathInvocation.lastSource;
                boolean nextTarget = ((before.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_INDEX) + 4) & 255)
                        < before.unsignedByte(CdObjectState.ByteField.TUNNEL_PATH_END);
                boolean speed = call.site() == CdTunnelPath.NativeCut.SPEED_Y || call.site() == CdTunnelPath.NativeCut.SPEED_X;
                if (nextTarget ? !speed : call.site() != CdTunnelPath.NativeCut.RELEASE) {
                    throw new IllegalStateException("Tunnel completion did not follow its actual next-target/release branch");
                }
                yield TunnelStep.DONE;
            }
            default -> throw new IllegalStateException("Tunnel mutation site is repeated, terminal or outside its actual source routine");
        };
    }

    private void commitTunnelPathPlayer(CdTunnelPath.PlayerCall call) {
        var source = requireTunnelPathCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual Tunnel player call");
            validateTunnelPathCut(call.actualCut(), call.expectedSource(), call.expectedPlayer());
            if (!source.equals(call.expectedSource())) throw new IllegalArgumentException("Tunnel player call differs from its actual source64");
            var next = tunnelPathNextStep(call);
            validateTunnelPathSource(source);
            if (!call.stores().equals(tunnelPathExpectedStores(call))
                    || !call.stores().stream().filter(CdTunnelPath.PlayerStore.class::isInstance)
                    .map(CdTunnelPath.PlayerStore.class::cast).toList().equals(call.result().stores())) {
                throw new IllegalArgumentException("Tunnel mixed native old/value/width/order or player subsequence differs");
            }
            var pool = placement.pool();
            var readyPool = CdObjectPool.overlay(call.actualCut(), List.of(call.finalSource()));
            pool.validateRestore(readyPool); pool.validateWrite(call.finalSource());
            var readyPair = frame.prepareObjectTunnelPathPlayerResult(call.expectedPlayer().state(),
                    call.expectedPlayer().environment(), call.result());
            var expectedLedger = sounds;
            List<SoundRequest> readyLedger = expectedLedger;
            if (next == TunnelStep.MAIN_FM || next == TunnelStep.GOT_FM) {
                var ledger = new ArrayList<SoundRequest>(expectedLedger);
                ledger.add(new TunnelPathFm(CdTunnelPath.FmSound.FM_91));
                readyLedger = List.copyOf(ledger);
            }
            validateTunnelPathCut(call.actualCut(), source, call.expectedPlayer());
            frame.validatePreparedObjectSolidPublication(readyPair);
            // All fallible preparation is above. Existing owner methods now adopt already-validated ready values.
            // No allocation, native owner, external callback or aggregate reconstruction between these assignments.
            pool.write(call.finalSource());
            frame.applyPreparedObjectSolidPublication(readyPair);
            var invocation = activeTunnelPathInvocation;
            invocation.lastSource = call.finalSource(); invocation.lastPool = readyPool; invocation.next = next;
            invocation.expectedFmLedger = expectedLedger; invocation.readyFmLedger = readyLedger;
            // A staged FM ledger is not emitted here. Only the actual later source backedge may append it.
        } finally { inSolidCommit = false; }
    }

    private void playTunnelPathFm(CdTunnelPath.FmCall call) {
        var source = requireTunnelPathCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual Tunnel FM call");
            validateTunnelPathCut(call.actualCut(), call.expectedSource(), call.expectedPlayer());
            var invocation = activeTunnelPathInvocation;
            boolean actualSite = invocation.next == TunnelStep.MAIN_FM && call.site() == CdTunnelPath.FmSite.MAIN_ACQUIRE
                    || invocation.next == TunnelStep.GOT_FM && call.site() == CdTunnelPath.FmSite.GOT_PLAYER;
            if (!actualSite || !source.equals(call.expectedSource()) || call.sound() != CdTunnelPath.FmSound.FM_91
                    || invocation.readyFmLedger == null || sounds != invocation.expectedFmLedger) {
                throw new IllegalStateException("Tunnel FM requires its once-only actual completed native prefix");
            }
            validateTunnelPathSource(source);
            validateTunnelPathCut(call.actualCut(), source, call.expectedPlayer());
            sounds = invocation.readyFmLedger;
            invocation.lastSource = source; invocation.lastPool = call.actualCut(); invocation.next = TunnelStep.DONE;
            invocation.readyFmLedger = null; invocation.expectedFmLedger = null;
            // Read-only pool/pair, including faults. Metadata never executes after producer return.
        } finally { inSolidCommit = false; }
    }
    // END Tunnel07 Runtime R1.

    // BEGIN Platform21 Runtime R1 private integration.
    // BEGIN Disc09 Runtime R1. SOURCE-ONLY; authored Java is UNRUN by this author.
    // N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff, Spinning Disc/Main.asm:7-223;
    // Object Solid.asm:15-146,188-479. Shipped branches have no FixBugs alternative here.
    // Only the private cached producer scope grants origin. Public values/cuts/masks are checked data.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

    /** Exactly one cached-body call at its actual physical walk position, retired on every exit. */
    private void runSpinningDisc(int wholeIndex) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (activeSpinningDiscInvocation != null || activeCollapseInvocation != null || activePlatformInvocation != null
                || activeTamabbohInvocation != null || wholeIndex != dispatchWholeIndex
                || placement.pool().state(wholeIndex).id() != CdSpinningDisc.OBJECT_ID) {
            throw new IllegalStateException("Disc dispatch requires its actual nonnested physical ID09 visit");
        }
        var gates = frame.environment().movement().gates();
        var globals = new CdSpinningDisc.Globals(ringGlobals.timeStopTimer(), preScrollForeground.xWord(),
                gates.timeZone(), gates.timeWarpDirection());
        activeSpinningDiscInvocation = new SpinningDiscInvocation(spinningDisc, wholeIndex);
        try {
            spinningDisc.run(wholeIndex, globals);
            // Hidden/noncontact and no-jump returns legitimately leave unused later cursor sites.
        } finally {
            activeSpinningDiscInvocation = null; // Success, native stop, host exception and Error alike.
        }
    }

    /** Reads remain legal at DONE/STOPPED until the actual body returns; they consume no site. */
    private CdObjectState requireSpinningDiscCallback() {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeSpinningDiscInvocation;
        if (invocation == null || invocation.body != spinningDisc || invocation.wholeIndex != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || activeCollapseInvocation != null || activePlatformInvocation != null || activeTamabbohInvocation != null
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu || inCheckpointDispatch
                || frame.assets() != assets || frame.environment().dynamicSlots() != CdPlayerBody.DynamicSlots.UNIMPLEMENTED) {
            throw new IllegalStateException("Disc callback requires its current synchronous cached-body origin and idle mutual guards");
        }
        frame.captureActor(); // Existing open/idle/closed-Frame guard only; retain no capture or ingress pair.
        var source = placement.pool().state(dispatchWholeIndex);
        if (source.id() != CdSpinningDisc.OBJECT_ID || source.routine() != 2 || source.signedByte(SPRITE_FLAGS) >= 0) {
            throw new IllegalStateException("Disc callback is outside its actual visible Main plain-Solid/player sites");
        }
        return source;
    }

    /** Return the complete current immutable owners, never the Solid projection or a cached visit ingress. */
    private CdSpinningDisc.PlayerCut readSpinningDiscPlayer() {
        var source = requireSpinningDiscCallback();
        inSolidCommit = true;
        try {
            var cut = placement.pool().capture();
            var pair = new CdSpinningDisc.PlayerCut(frame.state(), frame.environment());
            validateSpinningDiscCut(cut, source, pair);
            return pair;
        } finally { inSolidCommit = false; }
    }

    /** Full same-owner source64/physical96 and the exact currently sampled Frame pair, before any writes. */
    private void validateSpinningDiscCut(CdObjectPool.Snapshot cut, CdObjectState source, CdSpinningDisc.PlayerCut expected) {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeSpinningDiscInvocation;
        if (invocation == null || invocation.body != spinningDisc || invocation.wholeIndex != dispatchWholeIndex) {
            throw new IllegalStateException("Disc value cut has no actual cached invocation");
        }
        Objects.requireNonNull(source, "Disc complete source"); Objects.requireNonNull(expected, "Disc actual Frame pair");
        frame.captureActor(); // Guard only, no retained Frame partition and no reconstructed player value.
        var pool = placement.pool(); validateDynamicCapture(cut); pool.validateWrite(source);
        var live = pool.state(dispatchWholeIndex);
        if (source.identity().wholeIndex() != dispatchWholeIndex || !live.identity().equals(source.identity())
                || live.id() != CdSpinningDisc.OBJECT_ID || live.routine() != 2 || live.signedByte(SPRITE_FLAGS) >= 0
                || !live.equals(source) || !cut.slots().get(source.identity().dynamicIndex()).equals(source)
                || !pool.capture().slots().equals(cut.slots())
                || expected.state() != frame.state() || expected.environment() != frame.environment()) {
            throw new IllegalStateException("Disc source/full96/full-State-Environment cut is stale or foreign");
        }
    }

    /** Plain Solid owns bits3/5, canonical byte25 clears and genuine previous-stand BCLR3 images. */
    private List<CdObjectState> validateSpinningDiscImages(CdSpinningDisc.SolidCall call, CdObjectState source,
            CdObjectState object, List<CdObjectState> otherSlots, java.util.Set<CdObjectState.ByteField> writes) {
        var pool = placement.pool(); pool.validateWrite(object);
        if (!source.identity().equals(object.identity()) || object.copyBytes().length != CdObjectState.BYTE_SIZE
                || writes.stream().anyMatch(field -> field != FLAGS && field != SOLID_TYPE)
                || writes.contains(SOLID_TYPE) && object.unsignedByte(SOLID_TYPE) != 0
                || ((source.flags() ^ object.flags()) & ~(8 | 0x20)) != 0) {
            throw new IllegalArgumentException("Disc plain Solid output changes identity, canonical byte25 or unowned flag bits");
        }
        byte[] before = source.copyBytes(), after = object.copyBytes();
        for (int offset = 0; offset < CdObjectState.BYTE_SIZE; offset++) {
            if (offset == FLAGS.byteOffset() && writes.contains(FLAGS)
                    || offset == SOLID_TYPE.byteOffset() && writes.contains(SOLID_TYPE)) continue;
            if (before[offset] != after[offset]) throw new IllegalArgumentException("Undeclared Disc Solid source byte " + offset);
        }
        if (otherSlots.size() > 1) throw new IllegalArgumentException("Plain Solid releases at most one other dynamic stand slot");
        var images = new ArrayList<CdObjectState>(1 + otherSlots.size()); images.add(object);
        for (var other : otherSlots) {
            pool.validateWrite(other); int whole = other.identity().wholeIndex();
            if (whole == dispatchWholeIndex || whole != call.expectedPlayer().state().objectLinks().standObject()) {
                throw new IllegalArgumentException("Disc Solid release differs from the actual distinct previous stand byte");
            }
            var original = call.actualCut().slots().get(other.identity().dynamicIndex());
            if (!original.identity().equals(other.identity())
                    || !original.moveByte(FLAGS, original.flags() & ~8).equals(other)) {
                throw new IllegalArgumentException("Disc Solid other-slot image changes more than native BCLR3");
            }
            images.add(other); // A dirty ID0 is real physical storage, not a reason to reject its release.
        }
        return List.copyOf(images);
    }

    /** Stage every aggregate before publishing pool, prebuilt Frame pair, then the one ready sound ledger. */
    private void commitSpinningDiscSolid(CdSpinningDisc.SolidCall call) {
        var source = requireSpinningDiscCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual Disc plain Solid call");
            if (activeSpinningDiscInvocation.next != DiscStep.SOLID) {
                throw new IllegalStateException("Disc plain Solid site was already consumed or is out of order");
            }
            validateSpinningDiscCut(call.actualCut(), call.expectedSource(), call.expectedPlayer());
            if (!source.equals(call.expectedSource())) throw new IllegalArgumentException("Disc Solid source differs from its actual callback");
            CdObjectState object; List<CdObjectState> others; java.util.Set<CdObjectState.ByteField> writes;
            List<CdPlayerTouchResponse.FmSound> requests; CdPlayerFrameRuntime.ObjectSolidPublication prepared;
            DiscStep next;
            var expectedPlayer = solidPlayer(); var expectedGlobals = solidGlobals();
            if (call.outcome() instanceof CdSolidObject.Completed done) {
                var result = done.completedResult();
                if (result.entry() != CdSolidObject.Entry.SOLID_OBJECT || result.d0().isEmpty()) {
                    throw new IllegalArgumentException("Disc requires the genuine completed plain Solid return");
                }
                object = result.object(); others = result.otherSlots(); writes = result.objectWrites(); requests = result.fmRequests();
                prepared = frame.prepareObjectSolidResult(expectedPlayer, expectedGlobals, result);
                next = result.d0().orElseThrow() == 0 ? DiscStep.DONE : DiscStep.ACQUIRE;
            } else {
                var stopped = (CdSolidObject.NativeStopped) call.outcome(); var prefix = stopped.prefix();
                if (prefix.entry() != CdSolidObject.Entry.SOLID_OBJECT) {
                    throw new IllegalArgumentException("Disc stopped outcome must be its actual plain Solid prefix");
                }
                object = prefix.object(); others = prefix.otherSlots(); writes = prefix.objectWrites(); requests = prefix.fmRequests();
                prepared = frame.prepareObjectSolidPrefix(expectedPlayer, expectedGlobals, prefix); next = DiscStep.STOPPED;
            }
            var pool = placement.pool();
            var nextPool = CdObjectPool.overlay(call.actualCut(), validateSpinningDiscImages(call, source, object, others, writes));
            pool.validateRestore(nextPool);
            if (prepared.expectedState() != call.expectedPlayer().state()
                    || prepared.expectedEnvironment() != call.expectedPlayer().environment()) {
                throw new IllegalStateException("Prepared Disc Solid lost its actual complete Frame input pair");
            }
            var ledger = new ArrayList<SoundRequest>(sounds);
            for (var request : requests) ledger.add(new PlayerSound(new CdPlayerFrameRuntime.ContactFmSound(request)));
            var nextSounds = List.copyOf(ledger);
            frame.validatePreparedObjectSolidPublication(prepared);
            validateSpinningDiscCut(call.actualCut(), source, call.expectedPlayer());
            pool.restore(nextPool);
            frame.applyPreparedObjectSolidPublication(prepared); // Assign already-built references; no legacy build-at-apply port.
            sounds = nextSounds;
            activeSpinningDiscInvocation.next = next;
            // Body verifies the actual output, reads even at STOPPED, and rethrows the identical native exception.
        } finally { inSolidCommit = false; }
    }

    /** Fixed instruction spans only. Private origin grants execution; these branch/value checks do not replay stores. */
    private DiscStep spinningDiscNextStep(CdSpinningDisc.PlayerCall call) {
        var fields = new ArrayList<CdSpinningDisc.DiscPlayerField>();
        var player = call.expectedPlayer(); var state = player.state(); var motion = state.motion();
        DiscStep next;
        switch (activeSpinningDiscInvocation.next) {
            case ACQUIRE -> {
                fields.add(CONTROL_ACQUIRE);
                if ((motion.playerControl() & 1) == 0) {
                    fields.add(ANIMATION_ACQUIRE); fields.add(ROTATION_ZERO);
                    if ((motion.centreX16_16() >>> 16) < call.expectedSource().centreX()) fields.add(ROTATION_LEFT);
                    fields.add(DISTANCE_ACQUIRE);
                }
                // The unsigned routine gate is AFTER the entire acquisition prefix, exactly as native.
                if (motion.routine() < 6) { fields.add(ROTATION_ADVANCE); next = DiscStep.ORBIT; }
                else next = DiscStep.DONE;
            }
            case ORBIT -> { fields.add(ORBIT_X_BASE); fields.add(ORBIT_X_OFFSET); next = DiscStep.INPUT; }
            case INPUT -> {
                fields.add(SCRIPT_INDEX);
                if ((state.rotationAngle() & 0x3F) == 0) fields.add(DISTANCE_CARDINAL);
                fields.add(LOGICAL_CONTROLS);
                boolean left = (motion.centreX16_16() >>> 16) < call.expectedSource().centreX();
                int held = player.environment().controls().rawHeld();
                if ((held & (left ? 4 : 8)) != 0) fields.add(DISTANCE_GROW);
                else if ((held & (left ? 8 : 4)) != 0) {
                    int index = fields.size(); fields.add(DISTANCE_SHRINK);
                    if (call.result().stores().size() <= index) throw new IllegalArgumentException("Missing native Disc shrink site");
                    // The pure validator checks this observation against the current distance; no live store is replayed.
                    if (call.result().stores().get(index).expectedRaw() == 0) fields.add(DISTANCE_CLAMP);
                }
                next = DiscStep.JUMP_CLEAR;
            }
            case JUMP_CLEAR -> {
                if ((player.environment().controls().logicalTap() & 0x70) == 0) {
                    throw new IllegalStateException("Disc has no reached jump entrance for these actual logical controls");
                }
                fields.add(CONTROL_CLEAR); next = DiscStep.JUMP_VELOCITY;
            }
            case JUMP_VELOCITY -> {
                fields.addAll(List.of(JUMP_X_VELOCITY, JUMP_Y_VELOCITY, FLAGS_AIR, FLAGS_PUSH_CLEAR, JUMP_SET, STICK_CLEAR));
                next = DiscStep.FM;
            }
            case HITBOX -> {
                boolean mini = player.environment().movement().gates().miniSonic() != 0;
                fields.add(mini ? JUMP_Y_RADIUS_MINI : JUMP_Y_RADIUS_NORMAL);
                fields.add(mini ? JUMP_X_RADIUS_MINI : JUMP_X_RADIUS_NORMAL);
                if ((motion.flags() & 4) != 0) fields.add(FLAGS_ROLL_JUMP);
                else {
                    fields.add(mini ? ROLL_Y_RADIUS_MINI : ROLL_Y_RADIUS_NORMAL);
                    fields.add(mini ? ROLL_X_RADIUS_MINI : ROLL_X_RADIUS_NORMAL);
                    if (!mini) fields.add(ROLL_Y_ADVANCE);
                    fields.add(FLAGS_ROLL); fields.add(ANIMATION_ROLL);
                }
                next = DiscStep.DONE;
            }
            default -> throw new IllegalStateException("Disc direct player span is repeated, out of order or after its last publication");
        }
        requireSpinningDiscFields(call.result().stores(), fields);
        return next;
    }

    private void requireSpinningDiscFields(List<CdSpinningDisc.PlayerStore> stores, List<CdSpinningDisc.DiscPlayerField> fields) {
        if (stores.size() != fields.size()) throw new IllegalArgumentException("Disc direct span does not match its actual native site length");
        for (int index = 0; index < fields.size(); index++) {
            if (stores.get(index).field() != fields.get(index)) throw new IllegalArgumentException("Disc direct span differs at native site " + index);
        }
    }

    /** Complete pair CAS plus finite site consumption; the already-built result is adopted once by Frame. */
    private void commitSpinningDiscPlayer(CdSpinningDisc.PlayerCall call) {
        var source = requireSpinningDiscCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual Disc direct player call");
            validateSpinningDiscCut(call.actualCut(), call.expectedSource(), call.expectedPlayer());
            if (!source.equals(call.expectedSource())) throw new IllegalArgumentException("Disc direct player source differs from its actual callback");
            frame.validateObjectDiscPlayerResult(call.expectedPlayer().state(), call.expectedPlayer().environment(), call.result());
            var next = spinningDiscNextStep(call);
            frame.applyObjectDiscPlayerResult(call.expectedPlayer().state(), call.expectedPlayer().environment(), call.result());
            activeSpinningDiscInvocation.next = next;
            // Frame changes only. Body checks all96 nonwrites and freshly reads the actual new full pair.
        } finally { inSolidCommit = false; }
    }

    /** Only the actual post-jump/pre-hitbox PlayFMSound site can extend the immutable ledger once. */
    private void playSpinningDiscFm(CdSpinningDisc.FmCall call) {
        var source = requireSpinningDiscCallback();
        inSolidCommit = true;
        try {
            Objects.requireNonNull(call, "actual Disc FM call");
            validateSpinningDiscCut(call.actualCut(), call.expectedSource(), call.expectedPlayer());
            if (!source.equals(call.expectedSource()) || activeSpinningDiscInvocation.next != DiscStep.FM
                    || call.sound() != CdSpinningDisc.FmSound.FM_A0) {
                throw new IllegalStateException("Disc FM requires its once-only actual completed jump prefix before hitbox stores");
            }
            var ledger = new ArrayList<SoundRequest>(sounds); ledger.add(new SpinningDiscFm(call.sound()));
            var nextSounds = List.copyOf(ledger);
            validateSpinningDiscCut(call.actualCut(), source, call.expectedPlayer());
            sounds = nextSounds; activeSpinningDiscInvocation.next = DiscStep.HITBOX;
            // No player/pool write, service, playback or execution of public request metadata.
        } finally { inSolidCommit = false; }
    }
    // END Disc09 Runtime R1.

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


    // BEGIN SonicHole31 Runtime R1. Source-only authoring; all new Java UNRUN by this author.
    // N 1ecc5f3af7ccfbe26c6c58c96e6e5bcfdd15d6ff:
    // ObjSonicHole (Tunnel Path/Main.asm:11-103), PlayFMSound (Functions (General).asm:288-310).
    // Only this actual cached-body scope grants origin; public source/cut/result values never do.
    // Co-authored-by: OpenAI Codex GPT-6.1 Sol <noreply@openai.com>

    private void runSonicHole(int wholeIndex) {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (activeSonicHoleInvocation != null || activeTunnelPathInvocation != null || activeSpinningDiscInvocation != null
                || activeCollapseInvocation != null || activePlatformInvocation != null || activeTamabbohInvocation != null
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu || inCheckpointDispatch
                || wholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || wholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || wholeIndex != dispatchWholeIndex || placement.pool().state(wholeIndex).id() != CdSonicHole.OBJECT_ID
                || frame.assets() != assets || frame.environment().dynamicSlots() != CdPlayerBody.DynamicSlots.UNIMPLEMENTED) {
            throw new IllegalStateException("SonicHole requires its actual nonnested physical ID31 visit");
        }
        frame.captureActor(); // Existing open/idle/no-pending-Body guard only; retain no actor capture.
        var gates = frame.environment().movement().gates();
        var globals = new CdSonicHole.Globals(gates.timeZone(), gates.timeWarpDirection(), preScrollForeground.xWord());
        var invocation = new SonicHoleInvocation(sonicHole, wholeIndex, frame.state(), frame.environment());
        activeSonicHoleInvocation = invocation;
        try {
            sonicHole.run(wholeIndex, globals); // Completed observations are never commands or replay authority.
            checkActive(Phase.DYNAMIC_SLOTS);
            if (frame.state() != invocation.player || frame.environment() != invocation.environment) {
                throw new IllegalStateException("SonicHole changed its complete actual State8/Environment4 pair");
            }
            // Direct Init-old-set/routine4 Display may leave unused CONTROL entitlement; retire it, never invent a read.
        } finally {
            activeSonicHoleInvocation = null; // Completion, support stop, callback RuntimeException and Error.
        }
    }

    private SonicHoleInvocation requireSonicHoleCallback() {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeSonicHoleInvocation;
        if (invocation == null || invocation.body != sonicHole || invocation.wholeIndex != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || activeTunnelPathInvocation != null || activeSpinningDiscInvocation != null
                || activeCollapseInvocation != null || activePlatformInvocation != null || activeTamabbohInvocation != null
                || inDrawCallback || inSolidCommit || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu || inCheckpointDispatch
                || frame.assets() != assets || frame.environment().dynamicSlots() != CdPlayerBody.DynamicSlots.UNIMPLEMENTED
                || frame.state() != invocation.player || frame.environment() != invocation.environment) {
            throw new IllegalStateException("SonicHole callback has no current cached origin or has a nested/changed owner");
        }
        frame.captureActor();
        return invocation;
    }

    private void validateSonicHoleCut(CdObjectPool.Snapshot actualPoolCut, CdObjectState source) {
        checkActive(Phase.DYNAMIC_SLOTS);
        var invocation = activeSonicHoleInvocation;
        if (invocation == null || invocation.body != sonicHole || invocation.wholeIndex != dispatchWholeIndex
                || dispatchWholeIndex < CdObjectPool.FIRST_WHOLE_INDEX || dispatchWholeIndex >= CdObjectPool.END_WHOLE_INDEX
                || !inSolidCommit || activeTunnelPathInvocation != null || activeSpinningDiscInvocation != null
                || activeCollapseInvocation != null || activePlatformInvocation != null || activeTamabbohInvocation != null
                || inDrawCallback || inSpringLaunch || inSpinTunnelCommit || inTunnelDoorFm
                || inHorizontalTunnelDoorSolid || inHorizontalTunnelDoorFm || inMonitorAngle || inMonitorFm || inMonitorAward
                || inTimePostDirection || inTimePostSubCpu || inCheckpointDispatch) {
            throw new IllegalStateException("SonicHole full cut is outside its admitted synchronous callback");
        }
        Objects.requireNonNull(source, "actual SonicHole source64");
        frame.captureActor();
        var pool = placement.pool();
        validateDynamicCapture(actualPoolCut); pool.validateWrite(source);
        var live = pool.state(dispatchWholeIndex);
        if (source.identity().wholeIndex() != dispatchWholeIndex || !live.identity().equals(source.identity())
                || live.id() != CdSonicHole.OBJECT_ID || live.routine() != 2 || !live.equals(source)
                || actualPoolCut.slots().get(source.identity().dynamicIndex()) != source
                || !pool.capture().slots().equals(actualPoolCut.slots()) || actualPoolCut == invocation.lastPoolCut
                || frame.assets() != assets || frame.environment().dynamicSlots() != CdPlayerBody.DynamicSlots.UNIMPLEMENTED
                || frame.state() != invocation.player || frame.environment() != invocation.environment) {
            throw new IllegalStateException("SonicHole source64/full96/complete Frame pair is stale, foreign or outside Main");
        }
    }

    private int readSonicHolePrimary(CdSonicHole.PlayerField field, CdObjectPool.Snapshot actualPoolCut, CdObjectState source) {
        var invocation = requireSonicHoleCallback();
        Objects.requireNonNull(field, "actual SonicHole primary field");
        var expected = switch (field) {
            case CONTROL_2C -> SonicHoleStep.CONTROL;
            case X_WORD -> SonicHoleStep.X;
            case Y_WORD -> SonicHoleStep.Y;
        };
        if (invocation.next != expected) {
            throw new IllegalStateException("SonicHole primary field is repeated or outside its reached conditional site");
        }
        inSolidCommit = true;
        try {
            validateSonicHoleCut(actualPoolCut, source);
            // Native primary slot only, sampled NOW after real Body and earlier physical publications.
            int value = switch (field) {
                case CONTROL_2C -> frame.state().motion().playerControl();
                case X_WORD -> frame.state().motion().centreX16_16() >>> 16;
                case Y_WORD -> frame.state().motion().centreY16_16() >>> 16;
            };
            SonicHoleStep next;
            if (field == CdSonicHole.PlayerField.CONTROL_2C) {
                next = value == 0 ? SonicHoleStep.DONE : SonicHoleStep.X;
            } else {
                // Main.asm:54-65 WORD SUB/ADD, BMI then unsigned CMP; not physics or a second gameplay owner.
                int coordinate = field == CdSonicHole.PlayerField.X_WORD ? source.centreX() : source.centreY();
                int word = (value - coordinate + 0x20) & 0xFFFF;
                next = (short) word < 0 || word >= 0x40 ? SonicHoleStep.DONE
                        : field == CdSonicHole.PlayerField.X_WORD ? SonicHoleStep.Y : SonicHoleStep.FM;
            }
            validateSonicHoleCut(actualPoolCut, source);
            invocation.lastPoolCut = actualPoolCut;
            invocation.next = next;
            return value;
        } finally {
            inSolidCommit = false;
        }
    }

    private void playSonicHoleFm(CdSonicHole.FmSound sound, CdObjectPool.Snapshot actualPoolCut, CdObjectState source) {
        var invocation = requireSonicHoleCallback();
        if (invocation.next != SonicHoleStep.FM || sound != CdSonicHole.FmSound.FM_A3) {
            throw new IllegalStateException("SonicHole FM_A3 requires its once-only reached Y-overlap site");
        }
        inSolidCommit = true;
        try {
            validateSonicHoleCut(actualPoolCut, source);
            // Main.asm:66-68 / SetDisplay:85-95: raw period and saved0, AFTER actual BSET6, BEFORE routine2->4.
            int index = 2 + (short) ((3 * source.savedFlagsId()
                    + invocation.environment.movement().gates().timeZone()) & 0xFFFF);
            if (index < 2 || index >= CdObjectPlacementRuntime.SAVED_BANK_BYTES
                    || (placement.capture().savedFlags().unsignedByte(index) & 0x40) == 0) {
                throw new IllegalStateException("SonicHole FM lacks its exact completed saved-bit6 store");
            }
            var expectedLedger = sounds;
            var ledger = new ArrayList<SoundRequest>(expectedLedger);
            ledger.add(new SonicHoleFm(sound));
            var readyLedger = List.copyOf(ledger);
            validateSonicHoleCut(actualPoolCut, source);
            if (invocation.next != SonicHoleStep.FM || sounds != expectedLedger
                    || (placement.capture().savedFlags().unsignedByte(index) & 0x40) == 0) {
                throw new IllegalStateException("SonicHole FM prefix or prepared immutable ledger changed before adoption");
            }
            // All fallible preparation/revalidation is above; retain the complete earlier ordered prefix/duplicates.
            sounds = readyLedger;
            invocation.lastPoolCut = actualPoolCut;
            invocation.next = SonicHoleStep.DONE;
        } finally {
            inSolidCommit = false;
        }
    }
    // END SonicHole31 Runtime R1.

    /** Only the admitted native time-star window is dispatched here; other reserved owners remain unimplemented. */
    private void runTimeStarSlots() {
        for (int whole = CdTimeStars.FIRST_WHOLE_INDEX; whole < CdTimeStars.END_WHOLE_INDEX; whole++) {
            checkActive(Phase.DYNAMIC_SLOTS);
            dispatchWholeIndex = whole;
            var live = reserved.state(whole);
            if (live.unsignedByte(ID) == 0) continue;
            if (live.unsignedByte(ID) != CdTimeStars.OBJECT_ID) {
                throw new UnsupportedOperationException("Unowned CD reserved object ID $"
                        + Integer.toHexString(live.unsignedByte(ID)) + " at live physical slot " + whole);
            }
            var player = frame.state();
            timeStars.run(whole, new CdTimeStars.Globals(frame.environment().movement().gates().zoneAct() >>> 8,
                    contactGlobals.timeWarp(), player.motion().flags(), player.history().bytes(), player.history().index()));
        }
    }

    private void requireTimeStarCall() {
        checkActive(Phase.DYNAMIC_SLOTS);
        if (inDrawCallback || inSolidCommit || dispatchWholeIndex < CdTimeStars.FIRST_WHOLE_INDEX
                || dispatchWholeIndex >= CdTimeStars.END_WHOLE_INDEX) {
            throw new IllegalStateException("TimeStars callback requires its current reserved physical visit");
        }
    }

    private void drawTimeStar(CdReservedObjectBank bank, CdReservedObjectBank.Identity identity) {
        requireTimeStarCall();
        if (bank != reserved || identity.wholeIndex() != dispatchWholeIndex) {
            throw new IllegalStateException("TimeStars DrawObject requires its actual bank and dispatch");
        }
        inDrawCallback = true;
        try {
            var live = reserved.state(dispatchWholeIndex);
            queue.drawObject(new DrawCallCut(new CdSlotRef(dispatchWholeIndex), live.unsignedByte(SPRITE_FLAGS),
                    live.word(X), live.word(Y), live.unsignedByte(Y_RADIUS), live.unsignedByte(WIDTH),
                    live.unsignedByte(PRIORITY)), preScrollForeground);
            checkActive(Phase.DYNAMIC_SLOTS);
        } finally { inDrawCallback = false; }
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
        if (ref.wholeIndex() >= CdTimeStars.FIRST_WHOLE_INDEX && ref.wholeIndex() < CdTimeStars.END_WHOLE_INDEX) {
            var state = reserved.state(ref.wholeIndex());
            return new LiveDrawSlot(state.unsignedByte(ID), state.unsignedByte(SPRITE_FLAGS), state.word(X), state.word(Y),
                    state.word(X_FRACTION), state.longBits(MAPPINGS), state.unsignedByte(MAP_FRAME), state.word(TILE));
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
        } else if (ref.wholeIndex() >= CdTimeStars.FIRST_WHOLE_INDEX && ref.wholeIndex() < CdTimeStars.END_WHOLE_INDEX) {
            var slot = reserved.state(ref.wholeIndex());
            reserved.write(slot.moveByte(SPRITE_FLAGS, (slot.unsignedByte(SPRITE_FLAGS) & 0x7F) | (value ? 0x80 : 0)));
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
