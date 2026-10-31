package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.S3kPaletteOwners;
import com.openggf.game.sonic3k.S3kPaletteWriteSupport;
import com.openggf.game.sonic3k.Sonic3kObjectArtProvider;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.events.Sonic3kCNZEvents;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.PlayerControlOwners;
import com.openggf.level.objects.SpawnRewindRecreatable;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;
import java.util.List;

/**
 * CNZ Act 2 Knuckles teleporter cutscene owner.
 *
 * <p>ROM anchors: {@code Obj_CNZTeleporter} and {@code Obj_CNZTeleporterMain}.
 *
 * <p>The verified ROM findings for Task 8 are intentionally documented inline
 * because this object is doing cutscene glue rather than normal gameplay:
 * <ul>
 *   <li>Arm once the player reaches {@code x >= $4A38}</li>
 *   <li>If airborne and past {@code $4A40}, clamp back to {@code $4A40}</li>
 *   <li>Zero {@code x_vel} and {@code ground_vel}, lock control, clear logical
 *   input, queue {@code ArtKosM_CNZTeleport}, and patch palette line 2</li>
 *   <li>Wait for the queued art load to drain and for Knuckles to be grounded</li>
 *   <li>Set the parent and beam X positions to {@code $4A40}, spawn the shared
 *   {@code Obj_TeleporterBeam} at {@code y=$0A38}, play {@code sfx_Charging},
 *   and watch the beam progress thresholds</li>
 * </ul>
 *
 * <p>The engine's synchronous KosM load path exposes queue completion through
 * the dedicated renderer's readiness state. Palette overrides are composed
 * through {@code PaletteOwnershipRegistry}, preserving the ROM's immediate
 * CRAM patch without bypassing other CNZ palette writers.
 */
public final class CnzTeleporterInstance extends AbstractObjectInstance
        implements SpawnRewindRecreatable, TeleporterBeamOwner {
    /**
     * Verified arming threshold from {@code Obj_CNZTeleporter}.
     */
    private static final int ARM_X_THRESHOLD = 0x4A38;

    /**
     * Verified parent/beam X; P1 is centered gradually during the lift.
     */
    private static final int TELEPORT_X = 0x4A40;

    /**
     * Verified beam spawn Y from {@code Obj_CNZTeleporterMain}.
     */
    private static final int TELEPORT_BEAM_Y = 0x0A38;

    private static final int[] TELEPORT_PALETTE_INDICES = {1, 2, 8, 10};
    private static final int[] TELEPORT_PALETTE_WORDS = {0x0EEE, 0x00EC, 0x0EA2, 0x0E80};

    private int centreX;
    private int centreY;

    private boolean armed;
    private boolean paletteLine2Patched;
    private boolean teleportArtQueued;
    private boolean beamSpawned;
    /** Native code pointer loc_523CA, entered after the Y-word disappearance. */
    private boolean playerHidden;
    private boolean transitionRequested;
    private TeleporterBeamObjectInstance beam;

    public CnzTeleporterInstance(ObjectSpawn spawn) {
        super(spawn, "CNZTeleporter");
        this.centreX = spawn.x();
        this.centreY = spawn.y();
    }

    private final PlayerControlOwners visualControlOwners = new PlayerControlOwners();
    private final PlayerControlOwners objectControlOwners = new PlayerControlOwners();

    /** Releases acquired player controls without changing the native teleporter code phase. */
    @Override
    public void releasePlayersForVisualDirection() {
        for (var player : objectControlOwners.players()) ObjectControlState.none().applyTo(player);
        for (var player : visualControlOwners.players()) player.setControlLocked(false);
        objectControlOwners.clear();
        visualControlOwners.clear();
        // Disappearance is a native Y-word write, not a renderer-hidden flag.
        // Camera locks, beam progress, playerHidden and queued art stay native-owned.
    }

    @Override
    public int getX() {
        return centreX;
    }

    @Override
    public int getY() {
        return centreY;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int getPriorityBucket() {
        // Obj_CNZTeleporter never initializes priority or calls Draw_Sprite.
        return 0;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!(playerEntity instanceof AbstractPlayableSprite player)) {
            return;
        }
        if (transitionRequested) {
            return;
        }

        boolean routeAlreadyActive = isKnucklesRouteAlreadyActive();
        if (!routeAlreadyActive) {
            return;
        }

        // Native Obj_CNZTeleporter ($522A0) does not write camera boundaries.
        // CNZ2_ScreenEvent installs its clamp once, then loc_521D2 opens max X
        // through a gradual worker after the separate capsule's results finish.

        if (!armed && (player.getCentreX() & 0xFFFF) >= ARM_X_THRESHOLD) {
            // Obj_CNZTeleporter returns while airborne left of $4A40, even
            // after passing $4A38. Event admission is not object arming.
            if (player.getAir() && (player.getCentreX() & 0xFFFF) < TELEPORT_X) {
                return;
            }
            armTeleporter(player);
            // ROM replaces the code pointer with Obj_CNZTeleporterMain and
            // returns; Kos_modules_left is first polled on the following frame.
            return;
        }
        if (!armed) {
            return;
        }
        if (playerHidden) {
            advanceCameraRise();
            return;
        }

        if (!beamSpawned && isTeleportArtReady() && !player.getAir()) {
            spawnBeamAndCharge();
            // Obj_CNZTeleporterMain replaces its code pointer and returns.
            // The lower-slot parent first observes child progress next pass.
            return;
        }
        if (beamSpawned) {
            watchBeamProgress(player);
        }
    }

    /**
     * Verified {@code Obj_CNZTeleporter} arming sequence.
     *
     * <p>The airborne overshoot clamp only applies once the player has already
     * crossed the teleporter beam's X position. The ROM then clears horizontal
     * motion, locks control, zeroes logical input, queues the dedicated KosM
     * art, and patches palette line 2 before handing off to the main routine.
     */
    private void armTeleporter(AbstractPlayableSprite player) {
        armed = true;

        // Obj_CNZTeleporter: only actual airborne overshoot is snapped.
        // The event's earlier route admission never widens this native gate.
        if (player.getAir() && (player.getCentreX() & 0xFFFF) > TELEPORT_X) {
            com.openggf.level.LevelMotionAccess.cut(services().levelManager(), com.openggf.level.MotionCut.player(player));
            com.openggf.sprites.NativePositionOps.writeXPosPreserveSubpixel(player, TELEPORT_X);
        }

        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);
        visualControlOwners.acquire(player).setControlLocked(true);

        /**
         * The engine does not expose the raw logical-input buffer, so Task 8
         * mirrors the ROM's "clear Ctrl_1_logical" effect by ensuring the
         * player remains control-locked until the beam explicitly takes over.
         */
        teleportArtQueued = true;
        cnzArtProvider().queueCnzTeleporterArt();
        applyTeleportPalettePatch();
        paletteLine2Patched = true;
    }

    /** ROM {@code Kos_modules_left == 0}, represented by provider queue completion. */
    private boolean isTeleportArtReady() {
        return cnzArtProvider().isCnzTeleporterArtComplete();
    }

    private Sonic3kObjectArtProvider cnzArtProvider() {
        if (services().renderManager().getArtProvider() instanceof Sonic3kObjectArtProvider provider) {
            return provider;
        }
        throw new IllegalStateException("CNZ teleporter requires the S3K object-art provider");
    }

    private void applyTeleportPalettePatch() {
        S3kPaletteWriteSupport.applyColors(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager(),
                S3kPaletteOwners.CNZ_TELEPORTER,
                S3kPaletteOwners.PRIORITY_CUTSCENE_OVERRIDE,
                1,
                TELEPORT_PALETTE_INDICES,
                TELEPORT_PALETTE_WORDS);
        S3kPaletteWriteSupport.resolvePendingWritesNow(
                services().paletteOwnershipRegistryOrNull(),
                services().currentLevel(),
                services().graphicsManager());
    }

    /**
     * Verified {@code Obj_CNZTeleporterMain} handoff once queued art is ready
     * and Knuckles has landed.
     */
    private void spawnBeamAndCharge() {
        var admitted = spawnChild(() -> TeleporterBeamObjectInstance.cnzTransportBeam(
                new ObjectSpawn(TELEPORT_X, TELEPORT_BEAM_Y, 0, 0, 0, false, 0), this));
        // AllocateObjectAfterCurrent refusal leaves the code pointer at Main.
        // spawnChild can return a constructed but unregistered object.
        if (admitted == null || admitted.getSlotIndex() < 0 || admitted.isDestroyed()) {
            return;
        }
        beam = admitted;
        centreX = TELEPORT_X;
        beamSpawned = true;
        services().playSfx(Sonic3kSfx.CHARGING.id);
        com.openggf.game.sonic3k.events.S3kCnzEventWriteSupport.markTeleporterBeamSpawned(services());
    }

    /**
     * Native loc_5234A reads the prior entry's signed child progress byte.
     *
     * <p>Verified parent reactions:
     * <ul>
     *   <li>{@code counter == 8}: write object_control=1 and roll status/animation</li>
     *   <li>{@code counter >= $18}: hide the player, play transporter SFX, and
     *   keep the route scroll-locked</li>
     * </ul>
     */
    private void watchBeamProgress(AbstractPlayableSprite player) {
        if (beam == null || beam.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        int beamCounter = beam.progress();
        if (beamCounter < 8) {
            return;
        }
        if (beamCounter == 8) {
            // loc_523B6 executes on EVERY parent entry at eight. This bset
            // changes status only: no radius, pose, subpixel or velocity write.
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(
                    objectControlOwners.acquire(player));
            boolean skidding = player.getSkidding();
            int skidDustTimer = player.getSkidDustTimer();
            boolean fixedSkidDustActive = player.isFixedSkidDustActive();
            player.setRollingFlagPreserveRadii(true);
            // The generic roll helper also clears skid state; this ROM bset
            // does not. Preserve those independent fields as well as radii.
            player.setSkidding(skidding);
            player.setSkidDustTimer(skidDustTimer);
            player.setFixedSkidDustActive(fixedSkidDustActive);
            player.setAnimationId(Sonic3kAnimationIds.ROLL.id());
            return;
        }
        if (beamCounter < 0x18) {
            int levelFrameCounter = services().levelManager().getFrameCounter();
            if ((levelFrameCounter & 1) != 0) {
                NativePositionOps.writeYPosPreserveSubpixel(player, (player.getCentreY() - 1) & 0xFFFF);
                int playerX = player.getCentreX() & 0xFFFF;
                if ((levelFrameCounter & 2) != 0 && playerX != centreX) {
                    NativePositionOps.writeXPosPreserveSubpixel(player,
                            playerX + (playerX < centreX ? 1 : -1));
                }
            }
            return;
        }
        if (!playerHidden) {
            com.openggf.level.LevelMotionAccess.cut(services().levelManager(), com.openggf.level.MotionCut.player(player));
            playerHidden = true;
            // loc_5238E writes object_control=3: bit0 holds movement, bit1
            // skips native animation. P1 disappears by Y-word zero, not by a
            // renderer visibility flag; its fractional position survives.
            ObjectControlState.nativeBits0To6CpuAllowedMovementSuppressed().applyTo(
                    objectControlOwners.acquire(player));
            player.setObjectMappingFrameControl(true);
            NativePositionOps.writeYPosPreserveSubpixel(player, 0);
            player.setAnimationId(0);
            player.setMappingFrame(0);
            services().playSfx(Sonic3kSfx.TRANSPORTER.id);
            services().camera().setScrollLocked(true);
            // The new code pointer runs on the next parent dispatch.
        }
    }

    /** loc_523CA: word subtraction and unsigned comparison on every own entry. */
    private void advanceCameraRise() {
        var camera = services().camera();
        int nextY = ((camera.getY() & 0xFFFF) - 0x10) & 0xFFFF;
        camera.setY((short) nextY);
        if (nextY < 0x780) {
            transitionRequested = true;
            services().fadeOutMusic();
            // StartNewLevel $0500: the existing transition owner retires the
            // source level and performs the normal fade/title/ICZ assembly.
            services().requestZoneAndAct(Sonic3kZoneIds.ZONE_ICZ, 0, true);
        }
    }

    /**
     * Reads the already-published CNZ route state.
     *
     * <p>This is the explicit object/event dependency Task 8 needs. The route
     * can already be active before the teleporter object gets a frame, so the
     * teleporter must respect the CNZ event surface instead of relying only on
     * its local X threshold.
     */
    private boolean isKnucklesRouteAlreadyActive() {
        Object provider = services().levelEventProvider();
        if (provider instanceof Sonic3kLevelEventManager manager) {
            Sonic3kCNZEvents events = manager.getCnzEvents();
            return events != null && events.isKnucklesTeleporterRouteActive();
        }
        return false;
    }

    /** The shared child's clr.b $38(parent) is unused by this native parent. */
    @Override
    public void onBeamFinished() {
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Obj_CNZTeleporter never calls Draw_Sprite; only its shared beam draws.
    }

}
