package com.openggf.game.sonic2.objects;
import com.openggf.level.objects.ObjectAnimationState;
import com.openggf.level.objects.ExplosionObjectInstance;

import com.openggf.game.sonic2.audio.Sonic2Music;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.audio.Sonic2Sfx;

import com.openggf.level.objects.AbstractMonitorObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectLifetimeOps;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectListener;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.level.objects.SolidRoutineProfile;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseProfile;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.TouchResponseResult;

import com.openggf.graphics.GLCommand;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.audio.GameSound;
import com.openggf.game.sonic2.audio.Sonic2SmpsConstants;

import java.util.List;
import java.util.logging.Logger;

public class MonitorObjectInstance extends AbstractMonitorObjectInstance implements TouchResponseProvider, TouchResponseListener,
        SolidObjectProvider, SolidObjectListener {
    private static final Logger LOGGER = Logger.getLogger(MonitorObjectInstance.class.getName());
    private static final int HALF_RADIUS = 0x0E;
    private static final int BROKEN_FRAME = 0x0B;
    private static final int ICON_FRAME_OFFSET = 1;
    private static final int RING_MONITOR_REWARD = 10;

    // Monitor falling constants (from ROM: Touch_Monitor and Obj26_Main)
    private static final int FALLING_INITIAL_VEL = -0x180;  // Upward pop velocity when hit from below
    private static final int FALLING_GRAVITY = 0x38;        // Same gravity as other objects

    private final MonitorType type;
    private ObjectAnimationState animationState;
    private boolean broken;
    private int mappingFrame;

    // Falling state (routineSecondary != 0 in ROM)
    private boolean falling;
    private int yVel;
    private int yFixed;
    private int currentY;

    private boolean initialized;
    private boolean mainCharacterStanding;
    private boolean mainCharacterPushing;
    private boolean sidekickStanding;
    private boolean sidekickPushing;
    private String lastTouchBranch = "none";
    private int lastTouchYSpeed;
    private int lastTouchPlayerY;
    private int lastTouchMonitorY;
    private int lastTouchAnimation;
    private int lastTouchMoveLock;
    private int lastTouchForcedAnimation;
    private boolean lastTouchObjectControlled;
    private boolean lastTouchRolling;
    private String lastTouchAnimationProfile = "none";
    private int lastTouchAnimationScriptCount;

    public MonitorObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.type = MonitorType.fromSubtype(spawn.subtype());
        this.broken = this.type == MonitorType.BROKEN;

        int initialFrame = broken ? BROKEN_FRAME : 0;
        this.mappingFrame = initialFrame;
        if (broken) {
            effectApplied = true;
        }

        // Initialize position tracking for falling behavior
        this.currentY = spawn.y();
        this.yFixed = spawn.y() << 8;
    }

    private void ensureInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;

        // Check persistence: if remembered, spawn as broken
        ObjectManager objectManager = services().objectManager();
        boolean previouslyBroken = objectManager != null && objectManager.isRemembered(spawn);
        if (previouslyBroken && !broken) {
            this.broken = true;
            this.mappingFrame = BROKEN_FRAME;
            effectApplied = true;
        }

        int initialAnim = type.id;
        int initialFrame = broken ? BROKEN_FRAME : 0;
        ObjectRenderManager renderManager = services().renderManager();
        this.animationState = new ObjectAnimationState(
                renderManager != null ? renderManager.getMonitorAnimations() : null,
                initialAnim,
                initialFrame);
    }

    @Override
    public boolean shouldStayActiveWhenRemembered() {
        // Monitor needs to stay active to show icon rising and apply powerup effect
        // After breaking, it remains as a broken monitor frame (doesn't self-destruct)
        return true;
    }

    @Override
    protected boolean delayFirstIconUpdateAfterBreak() {
        // ROM Obj26_Break spawns separate Obj2E monitor contents with FindFreeObj.
        // In this trace the contents land in slot 21 while the shell is slot 26,
        // so the child cannot execute until the next object pass (s2.asm:25523,
        // 25557-25622). This class embeds Obj2E state in the shell, so skip the
        // shell's same-frame post-break update to preserve child-object timing.
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        ensureInitialized();
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        // Handle falling state first (ROM: Obj26_Main routine_secondary check)
        if (falling) {
            updateFalling();
        }

        if (!broken) {
            animationState.update();
            mappingFrame = animationState.getMappingFrame();
            return;
        }
        updateIcon();
    }

    /**
     * Update falling monitor (after being hit from below).
     * ROM reference: s2.asm lines 25401-25408 (ObjectMoveAndFall + ObjCheckFloorDist)
     */
    private void updateFalling() {
        // ObjectMoveAndFall: apply velocity and gravity
        yFixed += yVel;
        yVel += FALLING_GRAVITY;
        currentY = yFixed >> 8;

        // ObjCheckFloorDist: check for floor collision
        TerrainCheckResult result = ObjectTerrainUtils.checkFloorDist(spawn.x(), currentY, HALF_RADIUS);
        if (result.hasCollision()) {
            // Snap to floor and stop falling
            currentY = currentY + result.distance();
            yFixed = currentY << 8;
            yVel = 0;
            falling = false;
        }
    }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (broken || player == null) {
            return;
        }
        lastTouchBranch = "enter";
        lastTouchYSpeed = player.getYSpeed();
        lastTouchPlayerY = player.getCentreY();
        lastTouchMonitorY = currentY;
        lastTouchAnimation = player.getAnimationId();
        lastTouchMoveLock = player.getMoveLockTimer();
        lastTouchForcedAnimation = player.getForcedAnimationId();
        lastTouchObjectControlled = player.isObjectControlled();
        lastTouchRolling = player.getRolling();
        lastTouchAnimationProfile = player.getAnimationProfile() == null
                ? "none"
                : player.getAnimationProfile().getClass().getSimpleName();
        lastTouchAnimationScriptCount = player.getAnimationSet() == null
                ? -1
                : player.getAnimationSet().getScriptCount();

        // Hitting from below (Moving Up)
        // ROM reference: Touch_Monitor (s2.asm lines 84742-84763)
        // Check: player.y - 0x10 >= monitor.y (player center minus 16 must be >= monitor y)
        if (player.getYSpeed() < 0) {
            int playerCenterY = player.getCentreY();
            int monitorY = currentY;  // Use current Y position (may have moved if falling)

            // ROM check: move.w y_pos(a0),d0; subi.w #$10,d0; cmp.w y_pos(a1),d0; blo.s return
            // If player center - 16 >= monitor Y, then player is hitting from below
            if (playerCenterY - 0x10 >= monitorY) {
                lastTouchBranch = "below";
                LOGGER.fine(() -> "Monitor hit from below: player at (" + player.getX() + "," + player.getY() +
                    ") ySpeed=" + player.getYSpeed() + " monitor at (" + spawn.x() + "," + currentY + ")");

                // Bounce player down (neg.w y_vel(a0))
                player.setYSpeed((short) -player.getYSpeed());

                // Make monitor pop up and fall (only if not already falling)
                if (!falling) {
                    falling = true;
                    yVel = FALLING_INITIAL_VEL;  // -0x180 upward
                }
            } else {
                lastTouchBranch = "below-side-return";
            }
            return;
        }

        // ROM Touch_Monitor .breakMonitor: cmpa.w #MainCharacter,a0 / beq +
        // / tst.w (Two_player_mode).w / beq return. A CPU sidekick can knock a
        // monitor down from below (handled above) but cannot break it in
        // single-player mode — only the lead character, or a human-controlled
        // player in 2P/competition mode, may. 2P mode is unimplemented, so the
        // sidekick is always blocked here. (s2.asm:85245-85249; S1/S3K match.)
        if (isSidekick(player)) {
            lastTouchBranch = "sidekick-no-break";
            return;
        }

        // Hitting from above (Moving Down or Stationary)
        // ROM: Touch_Monitor checks anim(a0) == AniIDSonAni_Roll here, not the
        // broader rolling status bit. The animation transition lags status changes
        // by a frame in some cases, which affects monitor break timing.
        if (player.getAnimationId() != Sonic2AnimationIds.ROLL.id()) {
            lastTouchBranch = "not-roll-return";
            return;
        }

        // Break Monitor and Bounce Player Up
        broken = true;

        boolean touchingMonitorAsSolid = wasTouchingMonitor(player);
        lastTouchBranch = "break-tms=" + (touchingMonitorAsSolid ? "1" : "0");

        // Mark as broken in persistence table
        ObjectManager objectManager = services().objectManager();
        ObjectLifetimeOps.markSpawnRemembered(objectManager, spawn);
        if (objectManager != null) {
            objectManager.clearRidingObject(player);
        }

        // ROM Obj26_Break only forces the character airborne when the monitor's
        // own standing/pushing bits were set for that character.
        if (touchingMonitorAsSolid) {
            player.setOnObject(false);
            player.setPushing(false);
            player.setAir(true);
        }
        clearTouchingMonitor(player);
        releaseTouchingCharactersOnBreak(objectManager, player);
        player.setYSpeed((short) -player.getYSpeed());
        mappingFrame = BROKEN_FRAME;
        startIconRise(spawn.y(), player);

        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager != null) {
            services().objectManager().addDynamicObject(
                    new ExplosionObjectInstance(0x27, spawn.x(), spawn.y(), renderManager));
        }
        services().playSfx(Sonic2Sfx.EXPLOSION.id);
    }

    @Override
    public String traceDebugDetails() {
        return String.format("touch=%s ys=%04X py=%04X my=%04X anim=%02X roll=%d ml=%02X forced=%02X objctl=%d prof=%s scripts=%d broken=%d fall=%d mcS=%d mcP=%d skS=%d skP=%d",
                lastTouchBranch,
                lastTouchYSpeed & 0xFFFF,
                lastTouchPlayerY & 0xFFFF,
                lastTouchMonitorY & 0xFFFF,
                lastTouchAnimation & 0xFF,
                lastTouchRolling ? 1 : 0,
                lastTouchMoveLock & 0xFF,
                lastTouchForcedAnimation & 0xFF,
                lastTouchObjectControlled ? 1 : 0,
                lastTouchAnimationProfile,
                lastTouchAnimationScriptCount,
                broken ? 1 : 0,
                falling ? 1 : 0,
                mainCharacterStanding ? 1 : 0,
                mainCharacterPushing ? 1 : 0,
                sidekickStanding ? 1 : 0,
                sidekickPushing ? 1 : 0);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = services().renderManager();
        if (renderManager == null) {
            appendFallbackBox(commands);
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getMonitorRenderer();
        if (renderer == null || !renderer.isReady()) {
            appendFallbackBox(commands);
            return;
        }
        int frameIndex = broken ? BROKEN_FRAME : mappingFrame;
        // Use currentY for rendering (supports falling animation)
        renderer.drawFrameIndex(frameIndex, spawn.x(), currentY, false, false);

        if (iconActive) {
            int iconFrame = resolveIconFrame();
            ObjectSpriteSheet sheet = renderManager.getMonitorSheet();
            if (iconFrame >= 0 && sheet != null && iconFrame < sheet.getFrameCount()) {
                SpriteMappingFrame mappingFrame = sheet.getFrame(iconFrame);
                if (mappingFrame != null && !mappingFrame.pieces().isEmpty()) {
                    SpriteMappingPiece iconPiece = mappingFrame.pieces().get(0);
                    renderer.drawPieces(List.of(iconPiece), spawn.x(), iconSubY >> 8, false, false);
                }
            }
        }
    }

    @Override
    public int getCollisionFlags() {
        return broken ? 0 : 0x46;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile() {
        return TouchResponseProfile.fromProvider(this);
    }

    @Override
    public TouchResponseProfile getTouchResponseProfile(boolean multiRegionSource) {
        return TouchResponseProfile.fromProvider(this, multiRegionSource);
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        // ROM TouchResponse polls Obj26 every frame. The first overlap can see
        // status.player.rolling before anim reaches AniIDSonAni_Roll, so keep
        // rechecking while the player remains inside the monitor touch box.
        return true;
    }

    @Override
    public SolidObjectParams getSolidParams() {
        return new SolidObjectParams(0x1A, 0x0F, 0x10);
    }

    @Override
    public boolean isSolidFor(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (broken) {
            return false;
        }
        if (player == null) {
            return true;
        }
        if (isSidekick(player)) {
            // S2 one-player mode: SolidObject_Monitor_Tails always branches to
            // SolidObject_cont before checking roll anim (docs/s2disasm/s2.asm:25459-25466).
            return true;
        }
        // ROM: SolidObject_Monitor_Sonic (s2disasm/s2.asm:25448-25453):
        //   btst d6,status(a0)              ; is Sonic already standing on the monitor?
        //   bne.s Obj26_ChkOverEdge         ; if yes → carry him regardless of rolling
        //   cmpi.b #AniIDSonAni_Roll,anim(a1) ; is Sonic spinning?
        //   bne.w SolidObject_cont           ; if not spinning → solid
        //   rts                              ; if spinning → not solid (new landing blocked)
        // The rolling gate only blocks NEW landings. A player already standing
        // bypasses the check and goes straight to the edge/carry path.
        if (mainCharacterStanding) {
            return true;
        }
        return !player.getRolling();
    }

    @Override
    public boolean hasMonitorSolidity() {
        // S2 Obj26 does not use the SPG Mon_SolidSides geometry. Its monitor
        // wrapper gates roll-animation hits, then branches to SolidObject_cont
        // for normal solid classification (docs/s2disasm/s2.asm:25448-25452).
        return false;
    }

    @Override
    public SolidRoutineProfile getSolidRoutineProfile() {
        return SolidRoutineProfile.fullSolid(false);
    }

    @Override
    public boolean usesStickyContactBuffer() {
        // Monitors are static objects — the sticky buffer is only needed for
        // moving platforms to compensate for execution-order jitter. Using it
        // here would extend riding bounds 16px beyond the ROM's ExitPlatform width.
        return false;
    }

    @Override
    public void onSolidContact(PlayableEntity playerEntity, SolidContact contact, int frameCounter) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (player == null) {
            return;
        }
        if (contact.standing()) {
            setStandingOnMonitor(player, true);
        }
        if (contact.pushing()) {
            setPushingMonitor(player, true);
        }
    }

    private boolean wasTouchingMonitor(AbstractPlayableSprite player) {
        return isSidekick(player)
                ? sidekickStanding || sidekickPushing
                : mainCharacterStanding || mainCharacterPushing;
    }

    private void clearTouchingMonitor(AbstractPlayableSprite player) {
        setStandingOnMonitor(player, false);
        setPushingMonitor(player, false);
    }

    private void releaseTouchingCharactersOnBreak(ObjectManager objectManager, AbstractPlayableSprite breaker) {
        SpriteManager spriteManager = services().spriteManager();
        if (spriteManager == null) {
            return;
        }
        for (Sprite sprite : spriteManager.getAllSprites()) {
            if (!(sprite instanceof AbstractPlayableSprite playable) || playable == breaker) {
                continue;
            }
            if (!wasTouchingMonitor(playable)) {
                continue;
            }
            spriteManager.deferCrossPlayableMutationUntilPostTick(
                    playable,
                    () -> releaseTouchingCharacterOnBreak(objectManager, playable));
        }
    }

    private void releaseTouchingCharacterOnBreak(ObjectManager objectManager, AbstractPlayableSprite player) {
        if (objectManager != null) {
            objectManager.clearRidingObject(player);
        }
        player.setOnObject(false);
        player.setPushing(false);
        player.setAir(true);
        clearTouchingMonitor(player);
    }

    private void setStandingOnMonitor(AbstractPlayableSprite player, boolean standing) {
        if (isSidekick(player)) {
            sidekickStanding = standing;
        } else {
            mainCharacterStanding = standing;
        }
    }

    private void setPushingMonitor(AbstractPlayableSprite player, boolean pushing) {
        if (isSidekick(player)) {
            sidekickPushing = pushing;
        } else {
            mainCharacterPushing = pushing;
        }
    }

    private boolean isSidekick(AbstractPlayableSprite player) {
        return player.isCpuControlled();
    }

    @Override
    protected void applyPowerup(PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (type) {
            case RINGS -> {
                player.addRings(RING_MONITOR_REWARD);
                services().playSfx(GameSound.RING);
            }
            case SHIELD -> {
                player.giveShield();
                services().playSfx(Sonic2Sfx.SHIELD.id);
            }
            case SHOES -> {
                player.giveSpeedShoes();
                services().playMusic(Sonic2SmpsConstants.CMD_SPEED_UP);
            }
            case INVINCIBILITY -> {
                // ROM: tst.b (Super_Sonic_flag).w / bne.s +++ - skip when Super Sonic
                if (!player.isSuperSonic()) {
                    player.giveInvincibility();
                    services().playMusic(Sonic2Music.INVINCIBILITY.id);
                }
            }
            case SONIC, TAILS -> {
                services().playMusic(Sonic2Music.EXTRA_LIFE.id);
                services().gameState().addLife();
            }
            case EGGMAN, STATIC -> {
                // ROM: robotnik_monitor (s2.asm:25656-25658)
                // Both Static (subtype 0) and Eggman (subtype 3) call Touch_ChkHurt2.
                // Hurts the player as if touching a badnik.
                player.setHurt(true);
            }
            case TELEPORT -> {
                // ROM: teleport_monitor (s2.asm:25825-25845)
                // Swaps player positions in 2P mode. No-op in 1P mode.
                // 2P mode is not yet implemented.
            }
            case RANDOM -> {
                // ROM: qmark_monitor (s2.asm:26018-26020)
                // addq.w #1,(a2) / rts — no gameplay effect.
            }
            default -> {}
        }
    }

    private int resolveIconFrame() {
        if (type == MonitorType.BROKEN) {
            return -1;
        }
        // ROM Obj2E_Init: mapping_frame = anim + 1, where anim = the monitor subtype.
        // The icon shown is fixed by subtype, NOT by who broke the monitor. The
        // character-awareness of the Sonic 1-up icon comes from its art tile ($154 =
        // ArtTile_ArtNem_life_counter), which Sonic2ObjectArt(Provider) loads with the
        // main character's life-counter face. (s2.asm:25770-25772; obj26.asm)
        return type.id + ICON_FRAME_OFFSET;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3);
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public ObjectSpawn getSpawn() {
        // Return spawn with dynamic Y for solid collision checks
        if (currentY != spawn.y()) {
            return buildSpawnAt(spawn.x(), currentY);
        }
        return spawn;
    }

    /**
     * Debug fallback: render as a colored box when art is unavailable.
     */
    private void appendFallbackBox(List<GLCommand> commands) {
        int cx = spawn.x();
        int cy = currentY;
        int left = cx - HALF_RADIUS;
        int right = cx + HALF_RADIUS;
        int top = cy - HALF_RADIUS;
        int bottom = cy + HALF_RADIUS;
        float r = 0.4f, g = 0.9f, b = 1.0f;
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, right, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID, r, g, b, left, top, 0, 0));
    }

    private enum MonitorType {
        STATIC(0),
        SONIC(1),
        TAILS(2),
        EGGMAN(3),
        RINGS(4),
        SHOES(5),
        SHIELD(6),
        INVINCIBILITY(7),
        TELEPORT(8),
        RANDOM(9),
        BROKEN(10);

        private final int id;

        MonitorType(int id) {
            this.id = id;
        }

        static MonitorType fromSubtype(int subtype) {
            int value = subtype & 0xF;
            for (MonitorType type : values()) {
                if (type.id == value) {
                    return type;
                }
            }
            return STATIC;
        }
    }
}

