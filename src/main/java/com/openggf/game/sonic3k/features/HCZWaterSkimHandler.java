package com.openggf.game.sonic3k.features;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.rewind.identity.PlayerRefId;
import com.openggf.game.sonic3k.S3kSpriteDataLoader;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.PlayableEntity;
import com.openggf.physics.Direction;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.io.IOException;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HCZ Water Skim handler — implements the mechanic where the player runs
 * across the water surface at high speed with a splash trail behind them.
 * <p>
 * Port of Obj_HCZWaterSplash (subtype 1) and sub_3857E from sonic3k.asm.
 * <p>
 * Activation conditions (sub_3857E, sonic3k.asm:75393):
 * <ul>
 *   <li>Player feet at water level (y_pos + y_radius + 1 == Water_level)</li>
 *   <li>Zero vertical velocity (y_vel == 0)</li>
 *   <li>Horizontal speed >= $700 (abs(x_vel) >= 0x700)</li>
 * </ul>
 * <p>
 * While skimming:
 * <ul>
 *   <li>Player Y pinned to water surface</li>
 *   <li>Friction of $C/frame when airborne and no directional input</li>
 *   <li>Jump exit (A/B/C): y_vel = -$680, roll state</li>
 *   <li>Speed exit: below $700 threshold, player falls into water</li>
 *   <li>sfx_WaterSkid every 16 frames</li>
 * </ul>
 */
public final class HCZWaterSkimHandler {

    private static final Logger LOGGER = Logger.getLogger(HCZWaterSkimHandler.class.getName());

    // ===== Physics constants from sub_3857E =====
    /** Minimum |x_vel| to start/sustain skimming (sonic3k.asm:75409) */
    private static final int SPEED_THRESHOLD = 0x700;
    /** Friction per frame when airborne with no directional input (sonic3k.asm:75450) */
    private static final int SKIM_FRICTION = 0xC;
    /** Y velocity on jump exit (sonic3k.asm:75484) */
    private static final short JUMP_EXIT_Y_VEL = -0x680;
    // Jump exit radii ($0E/$07) match standard rolling radii — use applyRollingRadii()

    // ===== Splash animation constants (loc_384B2, sonic3k.asm:75314) =====
    /** Animation timer: 3 frames per step (sonic3k.asm:75330) */
    private static final int SPLASH_ANIM_DELAY = 3;
    /** Number of animation frames to cycle through (0-4) (sonic3k.asm:75332) */
    private static final int SPLASH_ANIM_FRAMES = 5;
    /** Frame index for "exit" splash (sonic3k.asm:75475) */
    private static final int SPLASH_EXIT_FRAME = 5;
    /** SFX plays when (Level_frame_counter+1+2) & 0xF == 0, i.e. every 16 frames */
    private static final int SFX_INTERVAL_MASK = 0xF;

    // ===== Per-player skim state =====
    private static boolean skimActiveP1;
    private static boolean skimActiveP2;
    private static int splashAnimFrameP1;
    private static int splashAnimFrameP2;
    private static int splashAnimTimerP1;
    private static int splashAnimTimerP2;
    /** Engine extension state; native P1/P2 fields above retain their ROM ownership. */
    private static final Map<PlayableEntity, ExtensionSkimState> extensionStates = new IdentityHashMap<>();
    private static final Map<PlayerRefId, ExtensionSkimSnapshot> pendingExtensionStates = new HashMap<>();
    private static List<PlayableEntity> lastParticipants = List.of();
    private static final NativeP2Binding nativeP2Binding = new NativeP2Binding();

    // ===== Frame counter (for SFX timing) =====
    private static int frameCounter;

    // ===== Rendering =====
    private static PatternSpriteRenderer splashRenderer;
    private static boolean artLoaded;
    private static int actId;

    private HCZWaterSkimHandler() {}

    /**
     * Initialize the skim handler for a given HCZ act.
     * Loads the ArtUnc_HCZWaterSplash2 art from ROM and builds the sprite renderer.
     */
    public static void init(Rom rom, int act) {
        reset();
        actId = act;
        try {
            Pattern[] patterns = loadSplashPatterns(rom);
            List<SpriteMappingFrame> frames = loadSplashMappings(RomByteReader.fromRom(rom));
            splashRenderer = new PatternSpriteRenderer(
                    new ObjectSpriteSheet(patterns, frames, 0, 0));
            LOGGER.info(String.format("HCZ water skim: loaded %d patterns, %d mapping frames",
                    patterns.length, frames.size()));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load HCZ water skim art", e);
            splashRenderer = null;
        }
    }

    /** Advances the object-local counter once before the playable slots run. */
    public static void beginFrame() {
        frameCounter++;
    }

    /**
     * Compatibility update path for callers that do not have per-player post-physics hooks.
     */
    public static void update() {
        beginFrame();
        update(playerQueryFromGameServices(), getWaterLevel(), frameCounter);
    }

    /** Runs the skim routine for one player at ROM post-player timing. */
    public static void updateAfterPlayablePhysics(AbstractPlayableSprite player) {
        if (player == null || player.getDead() || player.isDebugMode()) {
            return;
        }
        ObjectPlayerQuery query = playerQueryFromGameServices();
        int waterLevel = getWaterLevel();
        if (waterLevel == 0) {
            return;
        }
        List<PlayableEntity> participants = query.playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        hydratePendingExtensionStates(participants);
        lastParticipants = List.copyOf(participants);
        if (player == query.mainPlayerOrNull()) {
            skimActiveP1 = processSkimPhysics(player, skimActiveP1, waterLevel, frameCounter, 0, null, false);
            return;
        }
        AbstractPlayableSprite nativeP2 = nativeP2From(query);
        bindNativeP2(nativeP2);
        if (player == nativeP2) {
            skimActiveP2 = processSkimPhysics(player, skimActiveP2, waterLevel, frameCounter, 1, null, false);
            return;
        }
        for (int index = 2; index < participants.size(); index++) {
            if (participants.get(index) == player) {
                ExtensionSkimState state = extensionStates.computeIfAbsent(player,
                        ignored -> new ExtensionSkimState());
                state.active = processSkimPhysics(player, state.active, waterLevel, frameCounter,
                        index, state, false);
                return;
            }
        }
    }

    static void update(ObjectPlayerQuery query, int waterLevel, int frameCounter) {
        AbstractPlayableSprite player = asPlayableSprite(query.mainPlayerOrNull());
        if (player == null) {
            return;
        }
        if (player.getDead() || player.isDebugMode()) {
            if (skimActiveP1) {
                skimActiveP1 = false;
                splashAnimFrameP1 = SPLASH_EXIT_FRAME;
                player.setWaterSkimActive(false);
            }
        }

        if (waterLevel == 0) {
            return;
        }

        List<PlayableEntity> participants = query.playersFor(
                ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED);
        hydratePendingExtensionStates(participants);

        if (!player.getDead() && !player.isDebugMode()) {
            skimActiveP1 = processSkimPhysics(player, skimActiveP1, waterLevel, frameCounter, 0, null, true);
        }

        AbstractPlayableSprite p2 = nativeP2From(query);
        bindNativeP2(p2);
        if (p2 != null) {
            if (!p2.getDead() && !p2.isDebugMode()) {
                skimActiveP2 = processSkimPhysics(p2, skimActiveP2, waterLevel, frameCounter, 1, null, true);
            } else if (skimActiveP2) {
                skimActiveP2 = false;
                splashAnimFrameP2 = SPLASH_EXIT_FRAME;
                p2.setWaterSkimActive(false);
            }
        }

        lastParticipants = List.copyOf(participants);
        for (int index = 2; index < participants.size(); index++) {
            PlayableEntity participant = participants.get(index);
            AbstractPlayableSprite extension = asPlayableSprite(participant);
            if (extension == null) continue;
            ExtensionSkimState state = extensionStates.computeIfAbsent(
                    participant, ignored -> new ExtensionSkimState());
            if (!extension.getDead() && !extension.isDebugMode()) {
                state.active = processSkimPhysics(
                        extension, state.active, waterLevel, frameCounter, index, state, true);
            } else if (state.active) {
                state.active = false;
                state.splashFrame = SPLASH_EXIT_FRAME;
                extension.setWaterSkimActive(false);
            }
        }
        extensionStates.entrySet().removeIf(entry -> !entry.getValue().hasState()
                && !containsIdentity(participants, entry.getKey()));
    }

    /**
     * Port of sub_3857E — the core skim physics check and update.
     *
     * @param player     the player sprite
     * @param wasActive  whether skim was active last frame
     * @param waterLevel the water surface Y coordinate
     * @param frameCounter global frame counter
     * @return true if skim is active after this frame
     */
    private static boolean processSkimPhysics(AbstractPlayableSprite player,
                                               boolean wasActive,
                                               int waterLevel,
                                               int frameCounter,
                                               int playerIndex,
                                               ExtensionSkimState extensionState,
                                               boolean suppressSameFrameGravity) {
        if (!wasActive) {
            // === Activation check (sonic3k.asm:75393-75421) ===
            // Condition 1: y_vel must be zero
            if (player.getYSpeed() != 0) {
                return false;
            }

            // Condition 2: player feet at water level
            // ROM: y_radius(a1) + y_pos(a1) + 1 == Water_level
            // Engine: getCentreY() + getYRadius() gives feet Y (centre + half-height)
            int feetY = player.getCentreY() + player.getYRadius() + 1;
            if (feetY != waterLevel) {
                return false;
            }

            // Condition 3: |x_vel| >= $700
            int absXSpeed = Math.abs(player.getXSpeed());
            if (absXSpeed < SPEED_THRESHOLD) {
                return false;
            }

            // Activate! Set facing direction based on x_vel sign
            // ROM: bclr/bset #Status_Facing based on x_vel sign
            if (player.getXSpeed() < 0) {
                player.setDirection(Direction.LEFT);
            } else {
                player.setDirection(Direction.RIGHT);
            }

            // Start splash animation
            if (extensionState != null) {
                extensionState.splashFrame = 0;
                extensionState.splashTimer = SPLASH_ANIM_DELAY;
            } else if (playerIndex == 0) {
                splashAnimFrameP1 = 0;
                splashAnimTimerP1 = SPLASH_ANIM_DELAY;
            } else {
                splashAnimFrameP2 = 0;
                splashAnimTimerP2 = SPLASH_ANIM_DELAY;
            }

            // Mark player as skimming (prevents water entry)
            player.setWaterSkimActive(true);
            return true;
        }

        // === Sustain / exit logic (sonic3k.asm:75424-75491) ===

        // Check jump exit (A/B/C pressed)
        // ROM: andi.w #button_A_mask|button_B_mask|button_C_mask,d0
        if (player.isJumpPressed()) {
            return exitWithJump(player, playerIndex, extensionState);
        }

        // Calculate pin position BEFORE checking/applying it
        // ROM: d0 = Water_level - y_radius - 1 (sonic3k.asm:75428-75432)
        int pinnedY = waterLevel - player.getYRadius() - 1;

        // ROM: cmp.w y_pos(a1),d0 / bhi.s loc_38646 (sonic3k.asm:75433-75434)
        // Exit skim if terrain has pushed the player ABOVE the water pin position.
        // In Y-down coordinates, pinnedY > centreY means the pin is below the player,
        // i.e. terrain raised the player above the water surface (e.g. running into a curve).
        // Using unsigned comparison to match ROM's bhi (branch if higher, unsigned).
        if (Integer.compareUnsigned(pinnedY, player.getCentreY()) > 0) {
            return exitBySpeedLoss(player, playerIndex, extensionState, suppressSameFrameGravity);
        }

        // Check speed threshold — exit if too slow
        // ROM: cmpi.w #$700,d1 / blo.s loc_38646 (sonic3k.asm:75440-75441)
        int absXSpeed = Math.abs(player.getXSpeed());
        if (absXSpeed < SPEED_THRESHOLD) {
            return exitBySpeedLoss(player, playerIndex, extensionState, suppressSameFrameGravity);
        }

        // NOW pin player Y to water surface (only if still skimming)
        // ROM: move.w d0,y_pos(a1) / move.w #0,y_vel(a1) (sonic3k.asm:75442-75443)
        NativePositionOps.writeYPosPreserveSubpixel(player, pinnedY);
        player.setYSpeed((short) 0);
        if (suppressSameFrameGravity && player.getAir()) {
            player.suppressNextGravityStep();
        }
        // Apply friction when airborne and no directional input
        // ROM: btst #Status_InAir,status(a1) / andi.w #(left|right)<<8,d5
        if (player.getAir() && !player.isLeftPressed() && !player.isRightPressed()) {
            applySkimFriction(player);
            // ROM: move.w x_vel(a1),d0 / beq.s loc_38646 (sonic3k.asm:75452)
            // If friction reduced x_vel to zero, exit skim immediately
            if (player.getXSpeed() == 0) {
                return exitBySpeedLoss(player, playerIndex, extensionState, suppressSameFrameGravity);
            }
        }

        // Play SFX periodically
        // ROM: move.b (Level_frame_counter+1).w,d0 / addq.b #2,d0 / andi.b #$F,d0
        if (((frameCounter + 2) & SFX_INTERVAL_MASK) == 0) {
            GameServices.audio().playSfx(Sonic3kSfx.WATER_SKID.id);
        }

        // Advance splash animation
        advanceSplashAnim(playerIndex, extensionState);

        return true;
    }

    /**
     * Apply friction to horizontal velocity while skimming.
     * ROM: sub.w/add.w d1 ($C) to x_vel, clamped to zero (sonic3k.asm:75450-75470).
     */
    private static void applySkimFriction(AbstractPlayableSprite player) {
        short xSpeed = player.getXSpeed();
        if (xSpeed == 0) return;

        if (xSpeed > 0) {
            xSpeed = (short) Math.max(0, xSpeed - SKIM_FRICTION);
        } else {
            xSpeed = (short) Math.min(0, xSpeed + SKIM_FRICTION);
        }
        player.setXSpeed(xSpeed);
    }

    /**
     * Exit skim by jumping (A/B/C pressed).
     * ROM: loc_38652 (sonic3k.asm:75481-75491).
     */
    private static boolean exitWithJump(AbstractPlayableSprite player, int playerIndex,
                                        ExtensionSkimState extensionState) {
        int centreY = player.getCentreY();
        player.setYSpeed(JUMP_EXIT_Y_VEL);
        player.setAir(true);
        player.setJumping(true);
        // ROM: y_radius=$0E, x_radius=$07 — standard rolling radii
        player.applyRollingRadii(false);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
        player.setRolling(true);
        NativePositionOps.writeYPosPreserveSubpixel(player, centreY);

        if (extensionState != null) {
            extensionState.splashFrame = SPLASH_EXIT_FRAME;
        } else if (playerIndex == 0) {
            splashAnimFrameP1 = SPLASH_EXIT_FRAME;
        } else {
            splashAnimFrameP2 = SPLASH_EXIT_FRAME;
        }

        player.setWaterSkimActive(false);
        return false;
    }

    /**
     * Exit skim by speed dropping below threshold.
     * ROM: loc_38646 (sonic3k.asm:75473-75476).
     */
    private static boolean exitBySpeedLoss(AbstractPlayableSprite player, int playerIndex,
                                           ExtensionSkimState extensionState,
                                           boolean suppressSameFrameGravity) {
        if (extensionState != null) {
            extensionState.splashFrame = SPLASH_EXIT_FRAME;
        } else if (playerIndex == 0) {
            splashAnimFrameP1 = SPLASH_EXIT_FRAME;
        } else {
            splashAnimFrameP2 = SPLASH_EXIT_FRAME;
        }

        if (suppressSameFrameGravity && player.getAir()) {
            player.suppressNextGravityStep();
        }
        player.setWaterSkimActive(false);
        return false;
    }

    /**
     * Advance the splash animation timer and frame.
     * ROM: loc_384DA (sonic3k.asm:75328-75334) — 3 frames per step, cycles 0-4.
     */
    private static void advanceSplashAnim(int playerIndex, ExtensionSkimState extensionState) {
        if (extensionState != null) {
            extensionState.splashTimer--;
            if (extensionState.splashTimer < 0) {
                extensionState.splashTimer = SPLASH_ANIM_DELAY - 1;
                extensionState.splashFrame = (extensionState.splashFrame + 1) % SPLASH_ANIM_FRAMES;
            }
        } else if (playerIndex == 0) {
            splashAnimTimerP1--;
            if (splashAnimTimerP1 < 0) {
                splashAnimTimerP1 = SPLASH_ANIM_DELAY - 1;
                splashAnimFrameP1 = (splashAnimFrameP1 + 1) % SPLASH_ANIM_FRAMES;
            }
        } else {
            splashAnimTimerP2--;
            if (splashAnimTimerP2 < 0) {
                splashAnimTimerP2 = SPLASH_ANIM_DELAY - 1;
                splashAnimFrameP2 = (splashAnimFrameP2 + 1) % SPLASH_ANIM_FRAMES;
            }
        }
    }

    // ===== Rendering =====

    /**
     * Cache splash patterns in the graphics manager's pattern atlas.
     */
    public static int ensurePatternsCached(GraphicsManager gfx, int baseIndex) {
        if (splashRenderer != null) {
            splashRenderer.ensurePatternsCached(gfx, baseIndex);
            artLoaded = true;
            return baseIndex + (Sonic3kConstants.ART_UNC_HCZ_WATER_SPLASH2_SIZE / Pattern.PATTERN_SIZE_IN_ROM);
        }
        return baseIndex;
    }

    /**
     * Render splash sprites for active skim players.
     * ROM: loc_384B2 (sonic3k.asm:75314-75357) — splash follows player X at water level.
     */
    public static void render(Camera camera) {
        if (splashRenderer == null || !artLoaded) return;

        int waterLevel = getWaterLevel();
        if (waterLevel == 0) return;

        // P1 splash
        if (skimActiveP1 && splashAnimFrameP1 < SPLASH_EXIT_FRAME) {
            AbstractPlayableSprite p1 = asPlayableSprite(playerQueryFromGameServices().mainPlayerOrNull());
            if (p1 != null) {
                boolean hFlip = p1.getDirection() == Direction.LEFT;
                splashRenderer.drawFrameIndex(splashAnimFrameP1,
                        p1.getCentreX(), waterLevel, hFlip, false);
            }
        }

        // P2 splash
        if (skimActiveP2 && splashAnimFrameP2 < SPLASH_EXIT_FRAME) {
            AbstractPlayableSprite p2 = nativeP2From(playerQueryFromGameServices());
            if (p2 != null) {
                boolean hFlip = p2.getDirection() == Direction.LEFT;
                splashRenderer.drawFrameIndex(splashAnimFrameP2,
                        p2.getCentreX(), waterLevel, hFlip, false);
            }
        }

        for (PlayableEntity participant : activeExtensionSplashPlayers()) {
            ExtensionSkimState state = extensionStates.get(participant);
            AbstractPlayableSprite extension = asPlayableSprite(participant);
            if (extension != null && state.active && state.splashFrame < SPLASH_EXIT_FRAME) {
                boolean hFlip = extension.getDirection() == Direction.LEFT;
                splashRenderer.drawFrameIndex(state.splashFrame,
                        extension.getCentreX(), waterLevel, hFlip, false);
            }
        }
    }

    /** Returns true if P1 is currently skimming across water. */
    public static boolean isSkimActiveP1() {
        return skimActiveP1;
    }

    /** Returns true if P2 is currently skimming across water. */
    public static boolean isSkimActiveP2() {
        return skimActiveP2;
    }

    /** Returns the skim state for the main player (0) or any configured sidekick. */
    public static boolean isSkimActive(int playerIndex) {
        if (playerIndex == 0) return skimActiveP1;
        if (playerIndex == 1) return skimActiveP2;
        if (playerIndex < 0 || playerIndex >= lastParticipants.size()) return false;
        ExtensionSkimState state = extensionStates.get(lastParticipants.get(playerIndex));
        return state != null && state.active;
    }

    public static void reset() {
        skimActiveP1 = false;
        skimActiveP2 = false;
        splashAnimFrameP1 = 0;
        splashAnimFrameP2 = 0;
        splashAnimTimerP1 = 0;
        splashAnimTimerP2 = 0;
        extensionStates.clear();
        pendingExtensionStates.clear();
        lastParticipants = List.of();
        nativeP2Binding.reset(false);
        splashRenderer = null;
        artLoaded = false;
        actId = 0;
        frameCounter = 0;
    }

    /**
     * Immutable rewind snapshot of per-player skim/splash-animation state.
     * Loaded art ({@code splashRenderer}/{@code artLoaded}) and the zone act
     * ({@code actId}) are level-load-time config, not per-frame gameplay
     * state, and are intentionally excluded.
     */
    public record Snapshot(
            boolean skimActiveP1, boolean skimActiveP2,
            int splashAnimFrameP1, int splashAnimFrameP2,
            int splashAnimTimerP1, int splashAnimTimerP2,
            int frameCounter,
            List<ExtensionSkimSnapshot> extensionStates) {
        public Snapshot(boolean skimActiveP1, boolean skimActiveP2,
                        int splashAnimFrameP1, int splashAnimFrameP2,
                        int splashAnimTimerP1, int splashAnimTimerP2,
                        int frameCounter) {
            this(skimActiveP1, skimActiveP2,
                    splashAnimFrameP1, splashAnimFrameP2,
                    splashAnimTimerP1, splashAnimTimerP2,
                    frameCounter, List.of());
        }
    }

    public record ExtensionSkimSnapshot(
            PlayerRefId playerRef, boolean active, int splashFrame, int splashTimer) {}

    /** Captures the current per-player skim state for rewind snapshots. */
    public static Snapshot snapshot() {
        List<ExtensionSkimSnapshot> extensions = new ArrayList<>(pendingExtensionStates.values());
        for (int index = 2; index < lastParticipants.size(); index++) {
            ExtensionSkimState state = extensionStates.get(lastParticipants.get(index));
            if (state != null && state.hasState()) {
                PlayerRefId playerRef = PlayerRefId.sidekick(index - 1);
                extensions.removeIf(snapshot -> snapshot.playerRef().equals(playerRef));
                extensions.add(state.snapshot(playerRef));
            }
        }
        return new Snapshot(skimActiveP1, skimActiveP2,
                splashAnimFrameP1, splashAnimFrameP2,
                splashAnimTimerP1, splashAnimTimerP2,
                frameCounter, List.copyOf(extensions));
    }

    /** Restores per-player skim state from a previously captured snapshot. */
    public static void restore(Snapshot snapshot) {
        skimActiveP1 = snapshot.skimActiveP1();
        skimActiveP2 = snapshot.skimActiveP2();
        splashAnimFrameP1 = snapshot.splashAnimFrameP1();
        splashAnimFrameP2 = snapshot.splashAnimFrameP2();
        splashAnimTimerP1 = snapshot.splashAnimTimerP1();
        splashAnimTimerP2 = snapshot.splashAnimTimerP2();
        frameCounter = snapshot.frameCounter();
        extensionStates.clear();
        pendingExtensionStates.clear();
        for (ExtensionSkimSnapshot state : snapshot.extensionStates()) {
            pendingExtensionStates.put(state.playerRef(), state);
        }
        nativeP2Binding.reset(true);
    }

    // ===== Art loading =====

    /**
     * Load ArtUnc_HCZWaterSplash2 from ROM — uncompressed, 1920 bytes (60 tiles).
     * 5 animation frames × 12 tiles per frame.
     */
    private static Pattern[] loadSplashPatterns(Rom rom) throws IOException {
        byte[] data = new byte[Sonic3kConstants.ART_UNC_HCZ_WATER_SPLASH2_SIZE];
        synchronized (rom) {
            rom.getFileChannel().position(Sonic3kConstants.ART_UNC_HCZ_WATER_SPLASH2_ADDR);
            rom.getFileChannel().read(java.nio.ByteBuffer.wrap(data));
        }
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
            patterns[i].fromSegaFormat(Arrays.copyOfRange(data,
                    i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM));
        }
        return patterns;
    }

    private static List<SpriteMappingFrame> loadSplashMappings(RomByteReader reader) {
        List<SpriteMappingFrame> romFrames = S3kSpriteDataLoader.loadMappingFrames(
                reader, Sonic3kConstants.MAP_HCZ_WATER_SPLASH2_ADDR, SPLASH_ANIM_FRAMES);
        List<SpriteMappingFrame> frames = new java.util.ArrayList<>(SPLASH_EXIT_FRAME + 1);
        for (int i = 0; i < romFrames.size(); i++) {
            frames.add(offsetFrameTiles(romFrames.get(i), i * 12));
        }
        frames.add(new SpriteMappingFrame(List.of()));
        return frames;
    }

    private static SpriteMappingFrame offsetFrameTiles(SpriteMappingFrame frame, int tileOffset) {
        if (tileOffset == 0) {
            return frame;
        }
        List<SpriteMappingPiece> pieces = new java.util.ArrayList<>(frame.pieces().size());
        for (SpriteMappingPiece piece : frame.pieces()) {
            pieces.add(new SpriteMappingPiece(
                    piece.xOffset(),
                    piece.yOffset(),
                    piece.widthTiles(),
                    piece.heightTiles(),
                    piece.tileIndex() + tileOffset,
                    piece.hFlip(),
                    piece.vFlip(),
                    piece.paletteIndex(),
                    piece.priority()));
        }
        return new SpriteMappingFrame(pieces);
    }

    private static int getWaterLevel() {
        WaterSystem ws = GameServices.water();
        return ws != null ? ws.getWaterLevelY(
                com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_HCZ, actId) : 0;
    }

    private static ObjectPlayerQuery playerQueryFromGameServices() {
        AbstractPlayableSprite mainPlayer = GameServices.camera().getFocusedSprite();
        List<? extends PlayableEntity> sidekicks = List.copyOf(GameServices.sprites().getSidekicks());
        return new ObjectPlayerQuery(
                () -> mainPlayer,
                () -> sidekicks);
    }

    private static AbstractPlayableSprite nativeP2From(ObjectPlayerQuery query) {
        List<PlayableEntity> players = query.playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2);
        return players.size() > 1 ? asPlayableSprite(players.get(1)) : null;
    }

    private static AbstractPlayableSprite asPlayableSprite(PlayableEntity player) {
        return player instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    private static boolean containsIdentity(List<PlayableEntity> players, PlayableEntity target) {
        for (PlayableEntity player : players) {
            if (player == target) return true;
        }
        return false;
    }

    private static void bindNativeP2(AbstractPlayableSprite current) {
        if (nativeP2Binding.pending) {
            nativeP2Binding.owner = current;
            nativeP2Binding.pending = false;
            return;
        }
        if (nativeP2Binding.owner == current) return;
        if (nativeP2Binding.owner != null && (skimActiveP2 || splashAnimFrameP2 != 0 || splashAnimTimerP2 != 0)) {
            ExtensionSkimState migrated = extensionStates.computeIfAbsent(
                    nativeP2Binding.owner, ignored -> new ExtensionSkimState());
            migrated.active = skimActiveP2;
            migrated.splashFrame = splashAnimFrameP2;
            migrated.splashTimer = splashAnimTimerP2;
        }
        skimActiveP2 = false;
        splashAnimFrameP2 = 0;
        splashAnimTimerP2 = 0;
        if (current != null) {
            ExtensionSkimState restored = extensionStates.remove(current);
            if (restored != null) {
                skimActiveP2 = restored.active;
                splashAnimFrameP2 = restored.splashFrame;
                splashAnimTimerP2 = restored.splashTimer;
            }
        }
        nativeP2Binding.owner = current;
    }

    private static void hydratePendingExtensionStates(List<PlayableEntity> participants) {
        if (pendingExtensionStates.isEmpty()) return;
        var iterator = pendingExtensionStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PlayerRefId, ExtensionSkimSnapshot> entry = iterator.next();
            int playerIndex = entry.getKey().encoded() - 1;
            if (playerIndex >= 2 && playerIndex < participants.size()) {
                extensionStates.put(participants.get(playerIndex), ExtensionSkimState.from(entry.getValue()));
                iterator.remove();
            }
        }
    }

    static List<PlayableEntity> activeExtensionSplashPlayers() {
        List<PlayableEntity> active = new ArrayList<>();
        for (Map.Entry<PlayableEntity, ExtensionSkimState> entry : extensionStates.entrySet()) {
            if (entry.getValue().active && containsIdentity(lastParticipants, entry.getKey())) {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    private static final class ExtensionSkimState {
        private boolean active;
        private int splashFrame;
        private int splashTimer;

        private boolean hasState() {
            return active || splashFrame != 0 || splashTimer != 0;
        }

        private ExtensionSkimSnapshot snapshot(PlayerRefId playerRef) {
            return new ExtensionSkimSnapshot(playerRef, active, splashFrame, splashTimer);
        }

        private static ExtensionSkimState from(ExtensionSkimSnapshot snapshot) {
            ExtensionSkimState state = new ExtensionSkimState();
            state.active = snapshot.active();
            state.splashFrame = snapshot.splashFrame();
            state.splashTimer = snapshot.splashTimer();
            return state;
        }
    }

    private static final class NativeP2Binding {
        private PlayableEntity owner;
        private boolean pending;

        private void reset(boolean pending) {
            owner = null;
            this.pending = pending;
        }
    }
}
