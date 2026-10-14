package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.ContactKind;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SlopedSolidProvider;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.SpriteMappingPieces;
import com.openggf.sprites.NativePositionOps;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Object 0x2A - Cork Floor (Sonic 3 & Knuckles).
 */
public class CorkFloorObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, SlopedSolidProvider,
        RomObjectCodePointerProvider, RewindRecreatable {

    private static final Logger LOG = Logger.getLogger(CorkFloorObjectInstance.class.getName());

    private static final int FRAGMENT_GRAVITY = 0x18;
    private static final int PRIORITY = 5;
    private static final int ROLL_BREAK_LAUNCH_YVEL = -0x300;
    /** {@code byte_2A894}, sampled by ICZ's {@code sub_1DDC6} path. */
    private static final byte[] ICZ_SLOPE_DATA = {
            0x23, 0x23, 0x22, 0x22, 0x21, 0x21, 0x20, 0x1F,
            0x1F, 0x1E, 0x1E, 0x1D, 0x1D, 0x1C, 0x1B, 0x1B,
            0x1A, 0x1A, 0x19, 0x19, 0x17, 0x16, 0x15, 0x15,
            0x14, 0x14, 0x13, 0x13
    };
    private static final ObjectPlayerParticipationPolicy PLAYER_PARTICIPATION =
            ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS;

    @Override
    public int romObjectCodePointerHighWord() {
        // Obj_CorkFloor is installed at 0x0002A618 in the S&K-side ROM.
        // Tails_CPU_interact stores word 0 of the stood-on object SST
        // (docs/skdisasm/sonic3k.asm:26816-26843).
        return 0x0002;
    }

    private static final int[][] VEL_TABLE_SMALL = {
            {-0x200, -0x200}, {0x200, -0x200},
            {-0x100, -0x100}, {0x100, -0x100},
    };

    private static final int[][] VEL_TABLE_MEDIUM = {
            {-0x100, -0x200}, {0x100, -0x200},
            {-0x0E0, -0x1C0}, {0x0E0, -0x1C0},
            {-0x0C0, -0x180}, {0x0C0, -0x180},
            {-0x0A0, -0x140}, {0x0A0, -0x140},
            {-0x080, -0x100}, {0x080, -0x100},
            {-0x060, -0x0C0}, {0x060, -0x0C0},
    };

    /** word_2A84C: loc_2A816 advances this pointer by the pre-break frame * 4. */
    private static final int[][] VEL_TABLE_ICZ_SLOPED = {
            {-0x140, -0xA00}, {0x140, -0xA00},
            {-0x120, -0x900}, {0x120, -0x900},
            {-0x100, -0x800}, {0x100, -0x800},
            {-0x0E0, -0x700}, {0x0E0, -0x700},
            {-0x0C0, -0x600}, {0x0C0, -0x600},
            {-0x0A0, -0x580}, {0x0A0, -0x580},
            {-0x080, -0x500}, {0x080, -0x500}
    };

    private static final int[][] VEL_TABLE_LARGE = {
            {-0x400, -0x400}, {-0x200, -0x400}, {0x200, -0x400}, {0x400, -0x400},
            {-0x3C0, -0x3C0}, {-0x1C0, -0x3C0}, {0x1C0, -0x3C0}, {0x3C0, -0x3C0},
            {-0x380, -0x380}, {-0x180, -0x380}, {0x180, -0x380}, {0x380, -0x380},
            {-0x340, -0x340}, {-0x140, -0x340}, {0x140, -0x340}, {0x340, -0x340},
    };

    private record ZoneConfig(
            String artKey,
            int halfWidth,
            int halfHeight,
            int[][] velTable,
            boolean iczPlaneMode
    ) {}

    private static final ZoneConfig AIZ1_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_AIZ1, 0x10, 0x28, VEL_TABLE_MEDIUM, false);
    private static final ZoneConfig AIZ2_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_AIZ2, 0x10, 0x2C, VEL_TABLE_MEDIUM, false);
    private static final ZoneConfig CNZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_CNZ, 0x20, 0x20, VEL_TABLE_LARGE, false);
    private static final ZoneConfig FBZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_FBZ, 0x10, 0x10, VEL_TABLE_SMALL, false);
    private static final ZoneConfig ICZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_ICZ, 0x10, 0x24, VEL_TABLE_MEDIUM, true);
    private static final ZoneConfig ICZ_SMALL_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_ICZ, 0x10, 0x10, VEL_TABLE_SMALL, false);
    private static final ZoneConfig LBZ_CONFIG = new ZoneConfig(
            Sonic3kObjectArtKeys.CORK_FLOOR_LBZ, 0x20, 0x20, VEL_TABLE_LARGE, false);

    private enum Mode {
        BREAK_FROM_BELOW,
        ROLL_TO_BREAK,
        ICZ_PLANE_SWITCH
    }

    private final ZoneConfig config;
    private Mode mode;
    private int subtype;
    private boolean hFlip;
    private int mappingFrame;
    private int x;
    private int y;
    private final int[][] effectiveVelTable;

    private boolean broken;
    /** loc_2A5F8 runs in the original SST; CNZ retains this exact typed owner. */
    private boolean fragmentActive;
    private boolean romRenderFlag;
    private final SubpixelMotion.State fragmentMotion = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);
    private boolean playerStanding;
    private int savedPreContactYSpeed;
    private boolean savedPreContactRolling;
    private AbstractPlayableSprite rollingBreakPlayer;

    public CorkFloorObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CorkFloor");
        this.x = spawn.x();
        this.y = spawn.y();
        this.subtype = spawn.subtype() & 0xFF;
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
        this.config = resolveConfig(subtype);

        if (config.iczPlaneMode && (subtype & 0x10) == 0) {
            this.mode = Mode.ICZ_PLANE_SWITCH;
        } else if (subtype == 0) {
            this.mode = Mode.BREAK_FROM_BELOW;
        } else {
            this.mode = Mode.ROLL_TO_BREAK;
        }

        // Obj_CorkFloor stores the low nibble directly, including the
        // bit4 small-floor branch; native placements already use even frames.
        this.mappingFrame = config == ICZ_CONFIG || config == ICZ_SMALL_CONFIG ? subtype & 0x0F : 0;
        this.effectiveVelTable = mode == Mode.ICZ_PLANE_SWITCH ? VEL_TABLE_ICZ_SLOPED : config.velTable;
    }

    @Override
    public CorkFloorObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return ObjectConstructionContext.construct(
                ctx.objectServices(),
                () -> new CorkFloorObjectInstance(ctx.spawn()));
    }

    public boolean isBroken() {
        return broken;
    }

    public void forceBreakForTest() {
        broken = true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        if (mode == Mode.ICZ_PLANE_SWITCH) {
            // loc_2A6D4 passes only d2=height_pixels ($24) to sub_1DDC6;
            // unlike SolidObjectFull, there is no grounded d3=d2+1 surface.
            return SolidObjectParams.of(config.halfWidth + 0x0B, config.halfHeight, config.halfHeight);
        }
        return SolidObjectParams.of(config.halfWidth + 0x0B, config.halfHeight, config.halfHeight + 1);
    }

    @Override
    public byte[] getSlopeData() {
        // Only ICZ subtype bit 4 clear installs loc_2A6D4, which calls the
        // sloped full-solid helper. Every other variant calls SolidObjectFull.
        return mode == Mode.ICZ_PLANE_SWITCH ? ICZ_SLOPE_DATA : null;
    }

    @Override
    public boolean isSlopeFlipped() {
        return hFlip;
    }

    @Override
    public int getSlopeBaseline() {
        // sub_1DDC6 loc_1DECE subtracts byte_2A894[0] on fresh contact.
        return ICZ_SLOPE_DATA[0];
    }

    @Override
    public boolean addsSlopeCatchRangeToVerticalOverlap() {
        // sub_1DDC6 enters loc_1DECE with d2=height_pixels, then adds the
        // player's y_radius before classifying the sampled surface.
        return mode == Mode.ICZ_PLANE_SWITCH;
    }

    @Override
    public boolean forceAirOnRideExit() {
        // ICZ's sloped helper has a deliberately different continued-ride
        // exit from SolidObjectFull: sub_1DDC6/loc_1DE00 clears Status_OnObj
        // and the object's standing bit but does not set Status_InAir
        // (sonic3k.asm:41221-41264). This lets the next Player_AnglePos hand
        // the rider directly to terrain beneath the cork floor. Other cork
        // variants use SolidObjectFull and retain its ordinary airborne exit.
        return mode != Mode.ICZ_PLANE_SWITCH;
    }

    @Override
    public int getBalanceWidthPixels() {
        // Sonic_Move reads the object's width_pixels byte, not the default
        // 16-pixel render width nor SolidObjectFull's +$B side extension.
        return config.halfWidth;
    }

    @Override
    public boolean isTopSolidOnly() {
        return false;
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        return !broken;
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (broken) {
            if (fragmentActive) {
                advanceFragment();
            }
            return;
        }

        playerStanding = false;
        savedPreContactRolling = false;
        rollingBreakPlayer = null;
        // Per-frame scratch: the riders SolidObjectFull reports standing on
        // this floor during THIS update, in participation order. It is a local
        // so it carries no state across frames and needs no rewind capture.
        List<AbstractPlayableSprite> standingRiders = new ArrayList<>(2);

        resolveLaterSlotLeftSiblingBeforeRollingLanding(player);
        SolidCheckpointBatch batch = checkpointAll();
        for (PlayableEntity participant : participatingPlayers(player)) {
            if (broken) {
                break;
            }
            if (participant instanceof AbstractPlayableSprite playable) {
                applyCheckpointContact(playable, batch.perPlayer().get(participant),
                        standingRiders);
            }
        }
        if (broken) {
            return;
        }

        if (mode == Mode.BREAK_FROM_BELOW) {
            playerStanding = false;
            return;
        }

        if (rollingBreakPlayer != null) {
            int launchY = rollingBreakPlayer.getCentreY();
            rollingBreakPlayer.setRolling(true);
            // ROM sub_2A58E writes y_radius/x_radius/status directly and does
            // not alter y_pos (sonic3k.asm:58542-58554). SolidObjectFull may
            // have just restored standing dimensions, so the engine's visual
            // height swap can otherwise move centre Y by five pixels.
            NativePositionOps.writeYPosPreserveSubpixel(rollingBreakPlayer, launchY);
            if (mode != Mode.ICZ_PLANE_SWITCH) {
                rollingBreakPlayer.setYSpeed((short) ROLL_BREAK_LAUNCH_YVEL);
            }
            rollingBreakPlayer.setAir(true);
            rollingBreakPlayer.setOnObject(false);
            dropOtherStandingRiders(standingRiders);

            performBreak(rollingBreakPlayer);
            playerStanding = false;
            return;
        }

        playerStanding = false;
    }

    private void resolveLaterSlotLeftSiblingBeforeRollingLanding(AbstractPlayableSprite player) {
        if (mode == Mode.BREAK_FROM_BELOW || player == null
                || player.getAnimationId() != 2 || player.getYSpeed() < 0) {
            return;
        }
        int playerBottom = player.getCentreY() + player.getYRadius();
        int floorTop = y - config.halfHeight;
        if (playerBottom < floorTop || playerBottom > y
                || Math.abs(player.getCentreX() - x) > config.halfWidth + player.getXRadius()) {
            return;
        }
        ObjectManager objectManager = getObjectManager();
        if (objectManager == null) {
            return;
        }
        for (CorkFloorObjectInstance sibling :
                objectManager.activeObjectsOfType(CorkFloorObjectInstance.class)) {
            if (sibling == this || sibling.broken || sibling.getSlotIndex() <= getSlotIndex()) {
                continue;
            }
            // Adjacent CorkFloor placements are loaded left-to-right by the
            // native object-position cursor. A helper-created right floor can
            // occupy an earlier engine slot, reversing the two SolidObjectFull
            // calls. Replay only the still-unexecuted adjacent left sibling so
            // the seam-side push occurs before this floor launches the rider.
            if (sibling.y == y && sibling.x + (config.halfWidth * 2) == x) {
                objectManager.processImmediateInlineSolidCheckpoint(sibling, player, List.of());
                return;
            }
        }
    }

    private List<PlayableEntity> participatingPlayers(PlayableEntity updatePlayer) {
        List<PlayableEntity> participants = services().playerQuery().playersFor(PLAYER_PARTICIPATION);
        if (updatePlayer == null || participants.contains(updatePlayer)) {
            return participants;
        }
        ArrayList<PlayableEntity> withUpdatePlayer = new ArrayList<>(participants.size() + 1);
        withUpdatePlayer.add(updatePlayer);
        withUpdatePlayer.addAll(participants);
        return withUpdatePlayer;
    }

    private void applyCheckpointContact(AbstractPlayableSprite player, PlayerSolidContactResult result,
            List<AbstractPlayableSprite> standingRiders) {
        if (player == null || result == null || broken || result.kind() == ContactKind.NONE) {
            return;
        }

        savedPreContactYSpeed = result.preContact().ySpeed();
        // ROM Obj_CorkFloor caches Player_1+anim / Player_2+anim before
        // SolidObjectFull (sonic3k.asm:58493-58505) and breaks only when that
        // cached byte is anim=$02 (sonic3k.asm:58515-58528, 58532-58540).
        // Keep the per-frame decision per rider: the ROM stores P1/P2 cached
        // animation bytes separately, so a later non-rolling sidekick contact
        // must not erase the main player's roll-break checkpoint.
        boolean preContactRollAnimation = result.preContact().animationId() == 2;
        savedPreContactRolling |= preContactRollAnimation;

        if (result.standingNow()) {
            playerStanding = true;
            if (!standingRiders.contains(player)) {
                standingRiders.add(player);
            }
            if (preContactRollAnimation && canRollBreak(player) && rollingBreakPlayer == null) {
                rollingBreakPlayer = player;
            } else if (mode == Mode.ICZ_PLANE_SWITCH) {
                applyPlaneSwitch(player);
            }
        }

        if (mode == Mode.BREAK_FROM_BELOW && result.kind() == ContactKind.BOTTOM) {
            player.setYSpeed((short) savedPreContactYSpeed);
            performBreak(player);
        }
    }

    /**
     * ROM loc_2A542/loc_2A716: when BOTH riders are standing on the cork floor
     * and either cached animation byte is $02, the break path runs sub_2A588
     * (sub_2A7B0 for the ICZ sloped variant) once for Player_1 and once for
     * Player_2 (sonic3k.asm:58527-58534, 58762-58769). The rider whose cached
     * anim is not $02 falls straight through to loc_2A5AC / loc_2A7CE, which
     * still sets Status_InAir, clears Status_OnObj and writes routine 2
     * (sonic3k.asm:58566-58571, 58764-58768) — it just skips the roll, radii,
     * anim and the -$300 y_vel launch. The engine previously only ever
     * released the rolling breaker, so a standing non-rolling partner stayed
     * grounded on a floor that no longer exists.
     */
    private void dropOtherStandingRiders(List<AbstractPlayableSprite> standingRiders) {
        for (AbstractPlayableSprite rider : standingRiders) {
            if (rider == null || rider == rollingBreakPlayer) {
                continue;
            }
            rider.setAir(true);
            rider.setOnObject(false);
        }
    }

    private void applyPlaneSwitch(AbstractPlayableSprite player) {
        if ((subtype & 0x80) != 0) {
            player.setTopSolidBit((byte) 0x0E);
            player.setLrbSolidBit((byte) 0x0F);
        } else {
            player.setTopSolidBit((byte) 0x0C);
            player.setLrbSolidBit((byte) 0x0D);
        }
    }

    private boolean canRollBreak(AbstractPlayableSprite player) {
        if (mode != Mode.ICZ_PLANE_SWITCH) {
            return true;
        }
        return (subtype & 0x80) != 0 || (player.getTopSolidBit() & 0xFF) == 0x0E;
    }

    private void performBreak(AbstractPlayableSprite player) {
        if (broken) {
            return;
        }
        broken = true;

        // loc_2A816 indexes four bytes per velocity pair using the old
        // mapping byte; other break paths take their table from entry zero.
        int firstVelocity = mode == Mode.ICZ_PLANE_SWITCH ? mappingFrame : 0;
        mappingFrame++;
        fragmentActive = true;
        fragmentMotion.x = x;
        fragmentMotion.y = y;
        fragmentMotion.xVel = effectiveVelTable[firstVelocity][0];
        fragmentMotion.yVel = effectiveVelTable[firstVelocity][1];
        try {
            services().playSfx(Sonic3kSfx.COLLAPSE.id);
        } catch (Exception e) {
            // Ignore audio failures.
        }

        spawnFragments(mappingFrame, firstVelocity);
        // loc_2A5D8/loc_2A6BC fall through to loc_2A5F8 after the complete
        // allocation walk, so a0 moves exactly once in the breaking dispatch.
        advanceFragment();
        markRemembered();

        try {
            ObjectManager om = getObjectManager();
            if (om != null && player != null) {
                om.clearRidingObject(player);
            }
        } catch (Exception e) {
            // Safe fallback.
        }
    }

    private void spawnFragments(int brokenFrameIndex, int firstVelocity) {
        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        com.openggf.level.objects.ObjectSpriteSheet sheet = renderManager.getSheet(config.artKey);
        if (sheet == null || brokenFrameIndex >= sheet.getFrameCount()) {
            LOG.fine(() -> "CorkFloor: broken frame " + brokenFrameIndex
                    + " not found in sheet " + config.artKey);
            return;
        }

        SpriteMappingFrame brokenFrame = sheet.getFrame(brokenFrameIndex);
        int pieceCount = brokenFrame.pieces().size();
        int maxFragments = Math.min(pieceCount, effectiveVelTable.length - firstVelocity);

        // BreakObjectToPieces2 first assigns a1=a0. Only pieces1..N-1
        // call AllocateObjectAfterCurrent; a failed checked allocation ends
        // the mapping prefix and never borrows a lower free SST.
        for (int i = 1; i < maxFragments; i++) {
            CorkFloorFragment fragment = new CorkFloorFragment(
                    x, y, brokenFrameIndex, i, effectiveVelTable[firstVelocity + i][0], effectiveVelTable[firstVelocity + i][1],
                    config.artKey, hFlip, config.halfWidth, config.halfHeight, romRenderFlag);
            spawnDynamicObject(fragment);
            if (fragment.isDestroyed()) {
                break;
            }
        }
    }

    private void advanceFragment() {
        fragmentMotion.x = x;
        fragmentMotion.y = y;
        // MoveSprite2 changes the full position long using the old velocity;
        // loc_2A5F8 then adds $18 to the velocity word for the next dispatch.
        SubpixelMotion.objectFallXY(fragmentMotion, FRAGMENT_GRAVITY);
        fragmentMotion.yVel = (short) fragmentMotion.yVel;
        x = (short) fragmentMotion.x;
        y = (short) fragmentMotion.y;
        if (!romRenderFlag) {
            setDestroyed(true);
        }
    }

    private void markRemembered() {
        try {
            ObjectManager om = getObjectManager();
            ObjectLifetimeOps.markSpawnRemembered(om, spawn);
        } catch (Exception e) {
            // Safe fallback for tests.
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (fragmentActive) {
            drawFragmentPiece(getRenderManager(), config.artKey, mappingFrame, 0, x, y, hFlip);
            return;
        }
        if (broken) {
            return;
        }

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(config.artKey);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, x, y, hFlip, false);
        }
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getPriorityBucket() {
        // BreakObjectToPieces writes the original priority word $80.
        return fragmentActive ? RenderPriority.fromS3kWord(0x80) : RenderPriority.clamp(PRIORITY);
    }

    @Override
    public int getOnScreenHalfWidth() { return config.halfWidth; }

    @Override
    public int getOnScreenHalfHeight() { return config.halfHeight; }

    @Override
    public void refreshPostCameraRenderState() {
        if (!broken || fragmentActive) {
            romRenderFlag = isWithinRenderSpriteBounds(config.halfWidth, config.halfHeight);
        }
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() { return fragmentActive; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) { return false; }

    @Override
    public boolean isPersistent() { return fragmentActive && !isDestroyed(); }

    @Override
    public boolean isHighPriority() { return fragmentActive; }

    private static void drawFragmentPiece(ObjectRenderManager renderManager, String artKey,
            int frameIndex, int pieceIndex, int x, int y, boolean hFlip) {
        if (renderManager == null) return;
        var sheet = renderManager.getSheet(artKey);
        var renderer = renderManager.getRenderer(artKey);
        if (sheet == null || renderer == null || !renderer.isReady()
                || frameIndex < 0 || frameIndex >= sheet.getFrameCount()) return;
        var pieces = sheet.getFrame(frameIndex).pieces();
        if (pieceIndex < 0 || pieceIndex >= pieces.size()) return;
        SpriteMappingPiece piece = pieces.get(pieceIndex);
        // Static mappings point to one six-byte piece; art_tile bit15 is
        // ORed independently of the native priority bucket in the helper.
        renderer.drawPieces(List.of(SpriteMappingPieces.withAttributes(piece, piece.tileIndex(),
                piece.hFlip(), piece.vFlip(), piece.paletteIndex(), true)), x, y, hFlip, false);
    }

    private ObjectManager getObjectManager() {
        try {
            return services().objectManager();
        } catch (Exception e) {
            return null;
        }
    }

    protected SolidCheckpointBatch checkpointAll() {
        return services().solidExecution().resolveSolidNowAll();
    }

    private ZoneConfig resolveConfig(int subtype) {
        try {
            int zone = services().romZoneId();
            int act = services().currentAct();
            return switch (zone) {
                case Sonic3kZoneIds.ZONE_AIZ -> act == 0 ? AIZ1_CONFIG : AIZ2_CONFIG;
                case Sonic3kZoneIds.ZONE_CNZ -> CNZ_CONFIG;
                case Sonic3kZoneIds.ZONE_FBZ -> FBZ_CONFIG;
                case Sonic3kZoneIds.ZONE_ICZ ->
                        (subtype & 0x10) != 0 ? ICZ_SMALL_CONFIG : ICZ_CONFIG;
                case Sonic3kZoneIds.ZONE_LBZ -> LBZ_CONFIG;
                default -> {
                    LOG.warning("CorkFloor: unknown zone 0x" + Integer.toHexString(zone)
                            + ", defaulting to AIZ1 config");
                    yield AIZ1_CONFIG;
                }
            };
        } catch (Exception e) {
            LOG.fine("Could not resolve zone config: " + e.getMessage());
        }
        return AIZ1_CONFIG;
    }

    public static class CorkFloorFragment extends AbstractObjectInstance implements RewindRecreatable {

        private int currentX;
        private int currentY;
        private int fragmentFrameIndex;
        private int pieceIndex;
        private String artKey;
        private boolean hFlip;
        private int renderHalfWidth;
        private int renderHalfHeight;
        private boolean romRenderFlag;
        private final SubpixelMotion.State motionState;

        public CorkFloorFragment(int parentX, int parentY,
                                 int fragmentFrameIndex, int pieceIndex,
                                 int xVel, int yVel,
                                 String artKey, boolean hFlip) {
            this(parentX, parentY, fragmentFrameIndex, pieceIndex, xVel, yVel,
                    artKey, hFlip, 0x10, 0x28, true);
        }

        private CorkFloorFragment(int parentX, int parentY, int fragmentFrameIndex, int pieceIndex,
                int xVel, int yVel, String artKey, boolean hFlip,
                int renderHalfWidth, int renderHalfHeight, boolean romRenderFlag) {
            super(new ObjectSpawn(parentX, parentY, Sonic3kObjectIds.CORK_FLOOR,
                    0, hFlip ? 1 : 0, false, 0), "CorkFloorFragment");
            this.currentX = parentX;
            this.currentY = parentY;
            this.fragmentFrameIndex = fragmentFrameIndex;
            this.pieceIndex = pieceIndex;
            this.artKey = artKey;
            this.hFlip = hFlip;
            this.renderHalfWidth = renderHalfWidth;
            this.renderHalfHeight = renderHalfHeight;
            this.romRenderFlag = romRenderFlag;
            this.motionState = new SubpixelMotion.State(
                    currentX, currentY, 0, 0, xVel, yVel);
        }

        private CorkFloorFragment() {
            this(0, 0, 0, 0, 0, 0, Sonic3kObjectArtKeys.CORK_FLOOR_AIZ1, false);
        }

        @Override
        public CorkFloorFragment recreateForRewind(RewindRecreateContext ctx) {
            ObjectSpawn capturedSpawn = ctx.spawn();
            int x = capturedSpawn != null ? capturedSpawn.x() : 0;
            int y = capturedSpawn != null ? capturedSpawn.y() : 0;
            boolean capturedHFlip = capturedSpawn != null && (capturedSpawn.renderFlags() & 1) != 0;
            return new CorkFloorFragment(
                    x,
                    y,
                    0,
                    0,
                    0,
                    0,
                    Sonic3kObjectArtKeys.CORK_FLOOR_AIZ1,
                    capturedHFlip);
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return currentY;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity playerEntity) {
            motionState.x = currentX;
            motionState.y = currentY;
            SubpixelMotion.objectFallXY(motionState, FRAGMENT_GRAVITY);
            motionState.yVel = (short) motionState.yVel;
            currentX = (short) motionState.x;
            currentY = (short) motionState.y;

            // loc_2A5F8 observes the preceding Render_Sprites sign bit,
            // after motion, rather than doing a new padded visibility test.
            if (!romRenderFlag) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            drawFragmentPiece(getRenderManager(), artKey, fragmentFrameIndex, pieceIndex,
                    currentX, currentY, hFlip);
        }

        @Override
        public int getPriorityBucket() {
            // The shipped helper uses move.b priority(a0),priority(a1):
            // only the high byte of $0080 reaches a cleared sibling.
            // Its low byte remains zero, rather than a word copy of $0080.
            return RenderPriority.fromS3kWord(0);
        }

        @Override
        public int getOnScreenHalfWidth() { return renderHalfWidth; }

        @Override
        public int getOnScreenHalfHeight() { return renderHalfHeight; }

        @Override
        public void refreshPostCameraRenderState() {
            romRenderFlag = isWithinRenderSpriteBounds(renderHalfWidth, renderHalfHeight);
        }

        @Override
        public boolean usesCustomOutOfRangeCheck() { return true; }

        @Override
        public boolean isCustomOutOfRange(int cameraX) { return false; }

        @Override
        public boolean isHighPriority() { return true; }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
