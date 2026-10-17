package com.openggf.game.sonic3k.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.PlayableEntity;
import com.openggf.game.solid.PlayerSolidContactResult;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.objects.RomObjectCodePointerProvider;
import com.openggf.level.objects.SolidExecutionMode;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Object 0x2C - AIZ Collapsing Log Bridge (Sonic 3 & Knuckles).
 */
public class AizCollapsingLogBridgeObjectInstance extends AbstractObjectInstance
        implements SolidObjectProvider, RewindRecreatable, RomObjectCodePointerProvider {

    /**
     * Word 0 of this object's S3K SST holds its live ROM code pointer.
     * ROM {@code Obj_AIZCollapsingLogBridge} is installed from the S3K object pointer table at
     * {@code $0002ACDC} (table read from the user-supplied ROM; the
     * label is defined at docs/skdisasm/sonic3k.asm:59198).
     * Its whole code block lies in one bank, so the HIGH word that
     * {@code sub_13EFC} latches into {@code Tails_CPU_interact} and compares
     * on the next off-screen on-object frame is {@code $0002}
     * (docs/skdisasm/sonic3k.asm:26816-26843).
     */
    @Override
    public int romObjectCodePointerHighWord() {
        return 0x0002;
    }

    private static final int STATE_IDLE = 0;
    private static final int STATE_COLLAPSING = 1;
    private static final int STATE_FINAL = 2;

    private static final int SEGMENT_COUNT = 6;
    private static final int NORMAL_HALF_WIDTH = 0x5A;
    private static final int NORMAL_SEGMENT_SPACING = 0x1E;
    private static final int NORMAL_FIRST_OFFSET = 0x4B;
    private static final int FIRE_HALF_WIDTH = 0x60;
    private static final int FIRE_SEGMENT_SPACING = 0x20;
    private static final int FIRE_FIRST_OFFSET = 0x50;
    private static final int HEIGHT_PIXELS = 8;
    private static final int PRIORITY = 4;
    private static final int COLLAPSE_DELAY_INCREMENT = 8;
    private static final ObjectPlayerParticipationPolicy PLAYER_PARTICIPATION =
            ObjectPlayerParticipationPolicy.ALL_ENGINE_PLAYERS;

    private static volatile boolean drawBridgeBurnActive;

    public static void setDrawBridgeBurnActive(boolean active) {
        drawBridgeBurnActive = active;
    }

    /** Rewind snapshot accessor for the shared draw-bridge burn latch. */
    public static boolean isDrawBridgeBurnActive() {
        return drawBridgeBurnActive;
    }

    private boolean isFireBridge;
    private int halfWidth;
    private int subtypeBase;
    private int totalTimer;
    private boolean hFlip;
    private final String artKey;

    private int x;
    private int y;
    /**
     * Init has run: the display SST was allocated (or its allocation failed). ROM
     * Obj_AIZCollapsingLogBridge does this once, on its first pass.
     */
    private boolean displayChildAllocated;
    private int state = STATE_IDLE;
    private int collapseTimer;
    private boolean segmentsSpawned;
    private boolean collapseArmedByStanding;
    private boolean fireCollapseSolidFrame;
    // Zero preserves Obj_AIZCollapsingLogBridge's per-segment native timing.
    // Authored support is a whole-bridge lifetime, not the ROM's final timer.
    private int brittleSupportTicks;
    private int brittleWarningTick;
    private final List<PlayableEntity> standingPlayers = new ArrayList<>(2);
    private final Set<PlayableEntity> ejectedPlayers = new HashSet<>(2);

    private final int[] segmentX = new int[SEGMENT_COUNT];
    private final int[] segmentFrame = new int[SEGMENT_COUNT];

    public AizCollapsingLogBridgeObjectInstance(ObjectSpawn spawn) {
        super(spawn, "AIZCollapsingLogBridge");
        this.x = spawn.x();
        this.y = spawn.y();
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;

        int subtype = spawn.subtype() & 0xFF;
        this.isFireBridge = (subtype & 0x80) != 0;
        this.subtypeBase = isFireBridge ? (subtype & 0x7F) : subtype;
        this.totalTimer = subtypeBase + 0x30;
        this.collapseTimer = totalTimer;

        if (isFireBridge) {
            this.halfWidth = FIRE_HALF_WIDTH;
            this.artKey = Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE_FIRE;
            initSegments(FIRE_FIRST_OFFSET, FIRE_SEGMENT_SPACING);
        } else {
            this.halfWidth = NORMAL_HALF_WIDTH;
            this.artKey = Sonic3kObjectArtKeys.AIZ_COLLAPSING_LOG_BRIDGE;
            initSegments(NORMAL_FIRST_OFFSET, NORMAL_SEGMENT_SPACING);
        }
    }

    public AizCollapsingLogBridgeObjectInstance(ObjectSpawn spawn, int supportTicks) {
        this(spawn);
        if (supportTicks < 8 || supportTicks > 24 || isFireBridge) {
            throw new IllegalArgumentException("Brittle support requires an ordinary log and 8..24 updates");
        }
        brittleSupportTicks = supportTicks;
    }

    @Override
    public AizCollapsingLogBridgeObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new AizCollapsingLogBridgeObjectInstance(ctx.spawn());
    }

    private void initSegments(int firstOffset, int spacing) {
        int startX = x - firstOffset;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segmentX[i] = startX + i * spacing;
            segmentFrame[i] = 0;
        }
        segmentFrame[0] = 1;
        segmentFrame[SEGMENT_COUNT - 1] = 2;
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed();
    }

    @Override
    public SolidObjectParams getSolidParams() {
        // loc_2AE98/loc_2AF06 pass height_pixels(a0) directly as d3 to
        // SolidObjectTop; no bridge-local landing offset is applied afterward.
        return SolidObjectParams.of(halfWidth, HEIGHT_PIXELS, HEIGHT_PIXELS);
    }

    @Override
    public boolean isTopSolidOnly() {
        return true;
    }

    @Override
    public boolean usesCollisionHalfWidthForTopLanding() {
        // loc_2AE98/loc_2AF06 load width_pixels(a0) directly into d1 before
        // SolidObjectTop (sonic3k.asm:59299-59306,59341-59348). Unlike
        // SolidObjectFull callers, this path does not add $B and must not be
        // narrowed again by the shared landing-width correction.
        return true;
    }

    @Override
    public boolean rejectsZeroDistanceTopSolidLanding(PlayableEntity player) {
        // Both the normal bridge at loc_2AE98 and the fire bridge at loc_2AF06
        // call the same SolidObjectTop -> loc_1E42E entry. It accepts only
        // negative overlap d0 in [-16,-1]; cmpi.w #-$10,d0 / blo rejects
        // d0 == 0 for either subtype (sonic3k.asm:42048-42068).
        return true;
    }

    @Override
    public boolean gatesNewTopSolidLandingWithPreviousPosition() {
        // Both loc_2AE98 and loc_2AF06 call SolidObjectTop after the player's
        // movement and classify the current overlap. Requiring the previous
        // position to have already entered the 16-pixel band rejects fast but
        // valid downward landings that the ROM accepts.
        return false;
    }

    @Override
    public boolean isSolidFor(PlayableEntity player) {
        if (state == STATE_FINAL) {
            return false;
        }
        if ((((state == STATE_COLLAPSING || segmentsSpawned) && !fireCollapseSolidFrame) || collapseArmedByStanding)
                && (services().objectManager() == null
                || !services().objectManager().isRidingObject(player, this))) {
            // ROM loc_2AF70 no longer calls SolidObjectTop. It only tests
            // the standing bits that were already set and ejects those
            // players as their segment drops, so new landings cannot attach.
            return false;
        }
        return !ejectedPlayers.contains(player);
    }

    @Override
    public SolidExecutionMode solidExecutionMode() {
        return SolidExecutionMode.MANUAL_CHECKPOINT;
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        if (!displayChildAllocated) {
            // Obj_AIZCollapsingLogBridge init (sonic3k.asm:59245-59268 normal,
            // 59290-59318 fire): AllocateObjectAfterCurrent gives the six logs their
            // own display SST (loc_2AEB4) and stores its address in $3C. The parent
            // never calls Draw_Sprite. Init then branches into loc_2AE70/loc_2AEE2
            // in the same pass. A failed allocation (bne) leaves the logs undrawn.
            displayChildAllocated = true;
            spawnChild(() -> new DisplayChild(this));
        }
        if (brittleSupportTicks != 0 && state == STATE_IDLE) {
            brittleWarningTick = (brittleWarningTick + 1) & 0x0F;
        }
        fireCollapseSolidFrame = false;
        boolean collapseStartedThisFrame = false;
        if (state == STATE_IDLE && !isFireBridge && collapseArmedByStanding) {
            startCollapse();
            collapseStartedThisFrame = true;
        }
        if (state == STATE_IDLE && isFireBridge && drawBridgeBurnActive) {
            // loc_2AEE2 initializes the fire drawbridge collapse and falls
            // through directly into loc_2AF06/SolidObjectTop in the same
            // object routine (sonic3k.asm:59331-59348).
            startCollapse();
            collapseStartedThisFrame = true;
            fireCollapseSolidFrame = true;
        }

        if (state == STATE_IDLE || collapseStartedThisFrame) {
            SolidCheckpointBatch batch = checkpointAll();
            for (PlayableEntity participant : participatingPlayers(playerEntity)) {
                recordStandingPlayer(participant, batch.perPlayer().get(participant));
            }
        }

        switch (state) {
            case STATE_IDLE -> {
            }
            case STATE_COLLAPSING -> {
                if (collapseStartedThisFrame) {
                    break;
                }
                collapseTimer--;
                boolean timerExpired = collapseTimer <= 0;
                if (timerExpired) {
                    state = STATE_FINAL;
                }
                for (int i = standingPlayers.size() - 1; i >= 0; i--) {
                    checkPlayerKnockoff(standingPlayers.get(i));
                }
            }
            case STATE_FINAL -> {
                for (int i = standingPlayers.size() - 1; i >= 0; i--) {
                    knockOff(standingPlayers.get(i));
                }
                setDestroyed(true);
            }
        }
        deleteSpriteIfNotInRange();
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

    void recordStandingPlayer(PlayableEntity player, PlayerSolidContactResult result) {
        if (player == null) {
            return;
        }
        if (result == null || !result.standingNow()) {
            // The native parent stores its current Player 1 / Player 2 standing
            // bits directly in status(a0). SolidObjectTop clears those bits as
            // soon as a rider leaves; a later fire-triggered collapse must not
            // treat an old interact pointer as a current rider and run
            // sub_2AF9C against it.
            standingPlayers.remove(player);
            if (state == STATE_IDLE && !isFireBridge) {
                collapseArmedByStanding = !standingPlayers.isEmpty();
            }
            return;
        }
        if (!standingPlayers.contains(player)) {
            standingPlayers.add(player);
        }
        if (state == STATE_IDLE && !isFireBridge) {
            // loc_2AE70 tests the parent's standing bits before the current
            // SolidObjectTop call, so a new rider collapses the bridge next frame.
            collapseArmedByStanding = true;
        }
    }

    boolean isTrackingStandingPlayer(PlayableEntity player) {
        return standingPlayers.contains(player);
    }

    private void startCollapse() {
        if (segmentsSpawned) {
            return;
        }
        segmentsSpawned = true;
        collapseArmedByStanding = false;
        state = STATE_COLLAPSING;
        collapseTimer = brittleSupportTicks != 0 ? brittleSupportTicks : totalTimer;
        if (isFireBridge) {
            drawBridgeBurnActive = false;
        }

        var objManager = services().objectManager();
        ObjectLifetimeOps.markSpawnRemembered(objManager, spawn);

        int delay = brittleSupportTicks != 0 ? brittleSupportTicks : subtypeBase;
        DisplayChild display = liveDisplayChild(objManager);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            CollapsingLogSegment segment = new CollapsingLogSegment(
                    segmentX[i], y, segmentFrame[i], delay, artKey, isFireBridge);
            segment.brittleWarning = brittleSupportTicks != 0;
            if (i == 0 && display != null) {
                // sub_2AFFE (sonic3k.asm:59486-59493): movea.w $3C(a0),a3 / movea.l a3,a1
                // writes the first falling log into the display SST itself; it runs
                // later in this pass because that SST follows the parent.
                int displaySlot = ObjectLifetimeOps.detachSlotForTransfer(display);
                display.setDestroyed(true);
                ObjectLifetimeOps.addReplacementAtTransferredSlot(objManager, segment, displaySlot);
            } else {
                // loc_2B016: AllocateObjectAfterCurrent for the other five logs.
                spawnDynamicObject(segment);
            }
            if (brittleSupportTicks == 0) {
                delay += COLLAPSE_DELAY_INCREMENT;
            }
        }

        services().playSfx(Sonic3kSfx.COLLAPSE.id);
    }

    private DisplayChild liveDisplayChild(ObjectManager objectManager) {
        if (objectManager == null) {
            return null;
        }
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (instance instanceof DisplayChild display && display.parent == this
                    && !display.isDestroyed() && display.getSlotIndex() >= 0) {
                return display;
            }
        }
        return null;
    }

    /**
     * Delete_Sprite_If_Not_In_Range and Sprite_OnScreen_Test share this window: the
     * object's X with the low 7 bits cleared, minus Camera_X_pos_coarse_back, compared
     * unsigned against $280 (sonic3k.asm:37302-37308).
     */
    private static boolean outsideCoarseWindow(int cameraX, int objectX) {
        int cameraXPosCoarseBack = ((cameraX & 0xFFFF) - 0x80) & 0xFF80;
        int objectXCoarse = objectX & 0xFF80;
        return ((objectXCoarse - cameraXPosCoarseBack) & 0xFFFF) > 0x280;
    }

    private void deleteSpriteIfNotInRange() {
        if (isDestroyed()) {
            return;
        }
        if (outsideCoarseWindow(services().camera().getX(), x)) {
            // loc_2AE98/loc_2AF06/loc_2AF70 all tail-call
            // Delete_Sprite_If_Not_In_Range, which clears the respawn bit
            // before deleting so the layout entry can reload on camera return.
            setDestroyedByOffscreen();
        }
    }

    private void checkPlayerKnockoff(PlayableEntity player) {
        if (player.getAir()) {
            knockOff(player);
            return;
        }

        int fullWidth = halfWidth * 2;
        int relativeX = player.getCentreX() - x + halfWidth;
        if (relativeX < 0 || relativeX >= fullWidth) {
            knockOff(player);
            return;
        }

        if (brittleSupportTicks != 0) {
            if (collapseTimer <= 0) {
                knockOff(player);
            }
            return;
        }

        if (hFlip) {
            relativeX = fullWidth - relativeX;
        }

        int segmentIndex = relativeX >> 5;
        int segmentOffset = segmentIndex << 3;
        int threshold = 0x30 - segmentOffset;

        if (collapseTimer <= threshold) {
            knockOff(player);
        }
    }

    private void knockOff(PlayableEntity player) {
        player.setOnObject(false);
        player.setPushing(false);
        player.setAir(true);
        if (player instanceof AbstractPlayableSprite sprite) {
            publishKnockOffAnimationState(sprite);
        }
        standingPlayers.remove(player);
        ejectedPlayers.add(player);
    }

    void publishKnockOffAnimationState(AbstractPlayableSprite player) {
        // sub_2AF9C runs after the player's Animate pass and writes only
        // prev_anim=Run. An unchanged Walk byte therefore restarts on the next
        // player slot (sonic3k.asm:59455-59465).
        player.getAnimationManager().publishPreviousAnimationId(Sonic3kAnimationIds.RUN.id());
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The parent has no Draw_Sprite; its display SST draws the logs.
    }

    /** Draws the six logs for the display SST (mainspr_childsprites = 6). */
    void renderLogs(List<GLCommand> commands) {
        if (segmentsSpawned) {
            return;
        }

        ObjectRenderManager renderManager = getRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            int warningOffset = 0;
            if (brittleSupportTicks != 0 && i > 0 && i < SEGMENT_COUNT - 1) {
                // Before contact, loose middle logs sag and briefly tremble in
                // turn using their own ROM frames. End anchors and all solid
                // coordinates stay fixed; the presentation phase rewinds.
                warningOffset = 2;
                if ((brittleWarningTick >> 2) == i - 1 && (brittleWarningTick & 3) < 2) {
                    warningOffset = 0;
                }
            }
            renderer.drawFrameIndex(segmentFrame[i], segmentX[i], y + warningOffset, hFlip, false);
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
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public boolean isHighPriority() {
        // This engine flag means "above every terrain pixel". The fire bridge
        // instead uses the palette mask below to sit between the arena's
        // palette-3 waterfall and its palette 0-2 foreground terrain.
        return false;
    }

    @Override
    public int getTileOcclusionPaletteMask() {
        return isFireBridge ? 0b0111 : 0b1111;
    }

    @Override
    public String traceDebugDetails() {
        return String.format("state=%d armed=%s spawned=%s timer=%02X riders=%d",
                state, collapseArmedByStanding, segmentsSpawned,
                collapseTimer & 0xFF, standingPlayers.size());
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        float r = state == STATE_COLLAPSING ? 1.0f : 0.0f;
        float g = state == STATE_COLLAPSING ? 1.0f : 0.8f;
        float b = state == STATE_COLLAPSING ? 0.0f : 1.0f;
        int left = x - halfWidth;
        int right = x + halfWidth;
        int top = y - HEIGHT_PIXELS;
        int bottom = y + HEIGHT_PIXELS;
        ctx.drawLine(left, top, right, top, r, g, b);
        ctx.drawLine(right, top, right, bottom, r, g, b);
        ctx.drawLine(right, bottom, left, bottom, r, g, b);
        ctx.drawLine(left, bottom, left, top, r, g, b);
    }

    protected SolidCheckpointBatch checkpointAll() {
        return services().solidExecution().resolveSolidNowAll();
    }

    /**
     * ROM loc_2AEB4: the bridge's display SST. Init copies the parent's mappings, art,
     * width, priority $200 and the six log positions and frames into its sub-sprites,
     * then it only runs Sprite_OnScreen_Test (sonic3k.asm:37302-37315): Draw_Sprite
     * inside the coarse window, otherwise Delete_Current_Sprite. It has no respawn
     * entry. sub_2AFFE reuses this SST as the first falling log.
     */
    public static final class DisplayChild extends AbstractObjectInstance implements RewindRecreatable {
        private final AizCollapsingLogBridgeObjectInstance parent;

        DisplayChild(AizCollapsingLogBridgeObjectInstance parent) {
            this(new ObjectSpawn(parent.x, parent.y, Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE,
                    parent.isFireBridge ? 0x80 : 0, 0, false, 0), parent);
        }

        /** Rewind probe constructor. */
        public DisplayChild(ObjectSpawn spawn) {
            this(spawn, null);
        }

        private DisplayChild(ObjectSpawn spawn, AizCollapsingLogBridgeObjectInstance parent) {
            super(spawn, "AIZCollapsingLogBridgeDisplay");
            this.parent = parent;
        }

        @Override
        public DisplayChild recreateForRewind(RewindRecreateContext ctx) {
            if (ctx == null || ctx.spawn() == null) {
                return null;
            }
            AizCollapsingLogBridgeObjectInstance liveParent = null;
            if (ctx.objectServices() != null && ctx.objectServices().objectManager() != null) {
                for (ObjectInstance instance : ctx.objectServices().objectManager().getActiveObjects()) {
                    if (instance instanceof AizCollapsingLogBridgeObjectInstance bridge && !bridge.isDestroyed()
                            && bridge.x == ctx.spawn().x() && bridge.y == ctx.spawn().y()) {
                        liveParent = bridge;
                        break;
                    }
                }
            }
            return new DisplayChild(ctx.spawn(), liveParent);
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (isDestroyed()) {
                return;
            }
            if (outsideCoarseWindow(services().camera().getX(), spawn.x())) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            if (parent != null && !parent.isDestroyed()) {
                parent.renderLogs(commands);
            }
        }

        @Override
        public int getX() {
            return spawn.x();
        }

        @Override
        public int getY() {
            return spawn.y();
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public int getTileOcclusionPaletteMask() {
            return (spawn.subtype() & 0x80) != 0 ? 0b0111 : 0b1111;
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }

    public static class CollapsingLogSegment extends AbstractObjectInstance implements RewindRecreatable {

        private static final int GRAVITY = 0x38;
        private static final int OFF_SCREEN_MARGIN = 128;
        private static final int FIRE_ANIM_FRAME_DELAY = 3;
        private static final int FIRE_ANIM_FIRST_FRAME = 3;
        private static final int FIRE_ANIM_LAST_FRAME = 7;

        private final String artKey;
        private boolean isFireVariant;
        private int fixedX;
        private int mappingFrame;
        private int delayTimer;
        private final SubpixelMotion.State motion;
        private int animFrameTimer = FIRE_ANIM_FRAME_DELAY;
        private boolean brittleWarning;

        public CollapsingLogSegment(ObjectSpawn spawn) {
            this(spawn.x(), spawn.y(), 0, 0, artKeyForSpawn(spawn), isFireVariantSpawn(spawn));
        }

        public CollapsingLogSegment(int x, int y, int frame, int delay,
                                     String artKey, boolean isFireVariant) {
            super(buildSpawnAt(x, y, Sonic3kObjectIds.AIZ_COLLAPSING_LOG_BRIDGE, isFireVariant),
                    "LogSegment");
            this.fixedX = x;
            this.motion = new SubpixelMotion.State(0, y, 0, 0, 0, 0);
            this.mappingFrame = frame;
            this.delayTimer = delay;
            this.artKey = artKey;
            this.isFireVariant = isFireVariant;
        }

        private static ObjectSpawn buildSpawnAt(int x, int y, int objId, boolean isFireVariant) {
            return new ObjectSpawn(x, y, objId, isFireVariant ? 0x80 : 0, 0, false, 0);
        }

        @Override
        public CollapsingLogSegment recreateForRewind(RewindRecreateContext ctx) {
            return new CollapsingLogSegment(ctx.spawn());
        }

        private static boolean isFireVariantSpawn(ObjectSpawn spawn) {
            return (spawn.subtype() & 0x80) != 0;
        }

        private static String artKeyForSpawn(ObjectSpawn spawn) {
            return isFireVariantSpawn(spawn)
                    ? Sonic3kObjectArtKeys.AIZ_DRAW_BRIDGE_FIRE
                    : Sonic3kObjectArtKeys.AIZ_COLLAPSING_LOG_BRIDGE;
        }

        @Override
        public int getX() {
            return fixedX;
        }

        @Override
        public int getY() {
            return motion.y;
        }

        @Override
        public void update(int vIntRunCount, PlayableEntity player) {
            if (isDestroyed()) {
                return;
            }

            if (delayTimer > 0) {
                delayTimer--;
                if (isFireVariant && delayTimer == 0) {
                    mappingFrame = FIRE_ANIM_FIRST_FRAME;
                }
                return;
            }

            if (isFireVariant) {
                animFrameTimer--;
                if (animFrameTimer < 0) {
                    animFrameTimer = FIRE_ANIM_FRAME_DELAY;
                    mappingFrame++;
                    if (mappingFrame > FIRE_ANIM_LAST_FRAME) {
                        mappingFrame = FIRE_ANIM_FIRST_FRAME;
                    }
                }
            }

            SubpixelMotion.objectFall(motion, GRAVITY);

            if (!isOnScreen(OFF_SCREEN_MARGIN)) {
                setDestroyed(true);
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            PatternSpriteRenderer renderer = getRenderer(artKey);
            if (renderer == null) {
                return;
            }
            // Only the authored warning shakes the art. Solid/rider positions stay native.
            int warningOffset = brittleWarning && delayTimer > 0 ? (delayTimer & 1) : 0;
            renderer.drawFrameIndex(mappingFrame, fixedX, motion.y + warningOffset, false, false);
        }

        @Override
        public int getPriorityBucket() {
            return RenderPriority.clamp(PRIORITY);
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public int getTileOcclusionPaletteMask() {
            return isFireVariant ? 0b0111 : 0b1111;
        }

        @Override
        public boolean isPersistent() {
            return !isDestroyed();
        }
    }
}
