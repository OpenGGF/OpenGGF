package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.camera.Camera;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectPlayerParticipationPolicy;
import com.openggf.level.objects.ObjectPlayerQuery;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.ObjectControlState;

import java.util.List;
import java.util.logging.Logger;

/**
 * S3K end-of-act signpost (falls from sky after miniboss defeat).
 *
 * <p>ROM: Obj_Signpost (sonic3k.asm) — falling sign with spin animation,
 * bump-from-below mechanic, and hidden monitor interaction.
 *
 * <p>State machine: INIT -> FALLING -> LANDED -> RESULTS -> AFTER
 */
public class S3kSignpostInstance extends AbstractObjectInstance {
    private static final Logger LOG = Logger.getLogger(S3kSignpostInstance.class.getName());

    // ---- Static reference for hidden monitors ----
    private static S3kSignpostInstance activeSignpost;

    public static S3kSignpostInstance getActiveSignpost() {
        return activeSignpost;
    }

    // ---- State machine ----
    private enum State { INIT, FALLING, LANDED, RESULTS, AFTER }

    private State state = State.INIT;

    // ---- Physics (pixel-level velocities, fixed-point 8.8 where noted) ----
    private int xVel;
    private int yVel;
    private int worldX;
    private int worldY;

    /** Subpixel accumulators for fractional movement (lower 8 bits). */
    private int subX;
    private int subY;

    // ---- Signpost flags ----
    private boolean landed;
    private int postLandTimer;
    private int bumpCooldown;

    // ---- Animation ----
    private int animFrame;
    private int animIndex;
    private int animTimer;
    private int sparkleCounter;

    /**
     * ROM-accurate spin animation sequences.
     * Default (Sonic/Tails): Eggman -> spin -> Tails -> spin -> face -> spin, loop.
     * Knuckles: Tails -> spin -> Knux -> spin -> face -> spin, loop.
     */
    private static final int[] ANIM_SONIC = {0, 4, 5, 6, 1, 4, 5, 6, 3, 4, 5, 6};
    private static final int[] ANIM_KNUCKLES = {1, 4, 5, 6, 2, 4, 5, 6, 3, 4, 5, 6};

    /**
     * Face frame lookup indexed by PlayerCharacter ordinal.
     * 0=SONIC_AND_TAILS -> Sonic face (0), 1=SONIC_ALONE -> Sonic face (0),
     * 2=TAILS_ALONE -> Tails face (1), 3=KNUCKLES -> Knuckles face (2).
     */
    private static final int[] FACE_FRAMES = {0, 0, 1, 2};

    private static final int GRAVITY = 0x0C;
    private static final int Y_RADIUS = 0x1E;
    private static final int ANIM_FRAME_DELAY = 2;
    private static final int SPARKLE_INTERVAL = 4;
    private static final int POST_LAND_TIMER = 0x40;
    private static final int BUMP_COOLDOWN = 0x20;

    // Bump detection box relative to signpost center
    private static final int BUMP_LEFT = -0x20;
    private static final int BUMP_RIGHT = 0x40;
    private static final int BUMP_TOP = -0x18;
    private static final int BUMP_BOTTOM = 0x30;

    // Wall bounce margins relative to camera
    private static final int WALL_RIGHT_MARGIN = 0x128;
    private static final int WALL_LEFT_MARGIN = 0x18;

    // Landing Y threshold relative to camera
    private static final int LAND_Y_THRESHOLD = 0x50;
    private static final int AFTER_X_RANGE = 0x280;
    private static final int AFTER_Y_BIAS = 0x80;
    private static final int AFTER_Y_RANGE = 0x200;

    private int[] animSequence;

    /** ROM's Apparent_act — display-only act number, not affected by seamless reloads. */
    private final int apparentAct;

    /**
     * Creates the signpost at the given X position.
     * Y is set to above the camera in INIT state.
     *
     * @param spawnX      world X position for the signpost
     * @param apparentAct ROM's Apparent_act (0 = act 1 display, 1 = act 2 display)
     */
    public S3kSignpostInstance(int spawnX, int apparentAct) {
        super(null, "S3kSignpost");
        this.worldX = spawnX;
        this.worldY = 0; // Set properly in INIT
        this.apparentAct = apparentAct;
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }

    public boolean isLanded() {
        return landed;
    }

    public void setLanded(boolean landed) {
        this.landed = landed;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    /**
     * ROM: Offset_ObjectsDuringTransition shifts Obj_EndSign's position by the
     * same delta as the players/camera during a seamless act reload (e.g. CNZ
     * (-$3000, +$200)), so the signpost stays on screen and in Obj_EndSignAfter
     * after the Act 1 -> Act 2 transition instead of being stranded at its old
     * Act 1 world position (docs/skdisasm/sonic3k.asm:176262-176279, CNZ1BGE_DoTransition).
     */
    @Override
    public void onCarriedAcrossSeamlessTransition(int offsetX, int offsetY) {
        worldX += offsetX;
        worldY += offsetY;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = resolveUpdatePlayer(playerEntity);
        if (isDestroyed()) {
            return;
        }

        switch (state) {
            case INIT -> updateInit(player);
            case FALLING -> updateFalling(player);
            case LANDED -> updateLanded();
            case RESULTS -> updateResults(player);
            case AFTER -> updateAfter();
        }
    }

    // =========================================================================
    // INIT
    // =========================================================================

    private void updateInit(AbstractPlayableSprite player) {
        activeSignpost = this;

        var camera = services().camera();
        worldY = camera.getY() - 0x20;

        // Select animation based on player character
        PlayerCharacter pc = getPlayerCharacter();
        animSequence = (pc == PlayerCharacter.KNUCKLES) ? ANIM_KNUCKLES : ANIM_SONIC;
        animIndex = 0;
        animFrame = animSequence[0];
        animTimer = 0;
        sparkleCounter = 0;

        try {
            services().playSfx(Sonic3kSfx.SIGNPOST.id);
        } catch (Exception e) {
            LOG.fine("Could not play signpost SFX: " + e.getMessage());
        }

        // Spawn the stub/post child
        spawnDynamicObject(new S3kSignpostStubChild(this));

        state = State.FALLING;
        LOG.fine("S3K Signpost INIT -> FALLING at X=" + worldX + " Y=" + worldY);
    }

    // =========================================================================
    // FALLING
    // =========================================================================

    private void updateFalling(AbstractPlayableSprite player) {
        // Apply gravity
        yVel += GRAVITY;

        // Move (8.8 fixed-point accumulation)
        subX += xVel;
        worldX += subX >> 8;
        subX &= 0xFF;

        subY += yVel;
        worldY += subY >> 8;
        subY &= 0xFF;

        // Decrement bump cooldown
        if (bumpCooldown > 0) {
            bumpCooldown--;
        }

        // Sparkle effect
        sparkleCounter++;
        if (sparkleCounter >= SPARKLE_INTERVAL) {
            sparkleCounter = 0;
            spawnDynamicObject(new S3kSignpostSparkleChild(worldX, worldY));
        }

        // Check bump from below
        checkBumpFromBelow(player);

        // Wall bounce
        var camera = services().camera();
        int camX = camera.getX();
        if (worldX > camX + WALL_RIGHT_MARGIN) {
            xVel = -Math.abs(xVel);
        } else if (worldX < camX + WALL_LEFT_MARGIN) {
            xVel = Math.abs(xVel);
        }

        // Animate spin
        advanceAnimation();

        // Landing check — use terrain collision (ROM: ObjCheckFloorDist)
        // Only check when moving downward and past the minimum camera-relative Y
        if (yVel > 0 && worldY >= camera.getY() + LAND_Y_THRESHOLD) {
            TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(worldX, worldY, Y_RADIUS);
            if (floor.distance() < 0) {
                // Snap to floor surface
                worldY += floor.distance();
            } else {
                return; // No floor contact yet — keep falling
            }
            landed = true;
            postLandTimer = POST_LAND_TIMER;
            yVel = 0;
            xVel = 0;
            subX = 0;
            subY = 0;
            state = State.LANDED;
            LOG.fine("S3K Signpost FALLING -> LANDED at Y=" + worldY);
        }
    }

    /**
     * ROM: Signpost bump-from-below mechanic.
     * Player must be in animation #2 and moving upward, and within the bump
     * detection box.
     */
    private void checkBumpFromBelow(AbstractPlayableSprite player) {
        if (player == null || bumpCooldown > 0) {
            return;
        }

        // ROM EndSign_CheckPlayerHit checks the range once, then calls sub_83A70
        // for Sonic and Tails in that order. The delay byte is written inside
        // sub_83A70, so a same-frame Tails hit can overwrite Sonic's x velocity
        // (docs/skdisasm/sonic3k.asm:176342-176365, 176372-176387).
        for (PlayableEntity candidate : playerQuery(player).playersFor(ObjectPlayerParticipationPolicy.NATIVE_P1_P2)) {
            if (candidate instanceof AbstractPlayableSprite sprite && isRomBumpCandidate(worldX, worldY, sprite)) {
                applyRomBumpFromBelow(sprite);
            }
        }
    }

    private void applyRomBumpFromBelow(AbstractPlayableSprite player) {
        int kickX = romBumpXVelocity(worldX, player.getCentreX());
        // xVel/yVel are 8.8 fixed-point
        xVel = kickX;
        yVel = -0x200;

        try {
            services().playSfx(Sonic3kSfx.SIGNPOST.id);
        } catch (Exception e) {
            LOG.fine("Could not play signpost bump SFX: " + e.getMessage());
        }

        services().gameState().addScore(100);
        bumpCooldown = BUMP_COOLDOWN;
        LOG.fine("S3K Signpost bumped! xVel=" + xVel);
    }

    static boolean isRomBumpCandidate(int signpostX, int signpostY, AbstractPlayableSprite player) {
        if (!hasRomBumpPose(player)) {
            return false;
        }
        int dx = player.getCentreX() - signpostX;
        int dy = player.getCentreY() - signpostY;
        return dx >= BUMP_LEFT && dx < BUMP_RIGHT && dy >= BUMP_TOP && dy < BUMP_BOTTOM;
    }

    static int romBumpXVelocity(int signpostX, int playerX) {
        int kickX = (signpostX - playerX) * 16;
        return kickX == 0 ? 8 : kickX;
    }

    static boolean hasRomBumpPose(AbstractPlayableSprite player) {
        // ROM sub_83A70 only accepts anim(a1)==#2 and upward y_vel(a1);
        // it does not test Status_InAir (docs/skdisasm/sonic3k.asm:176372-176387).
        return player != null
                && player.getAnimationId() == Sonic3kAnimationIds.ROLL.id()
                && player.getYSpeed() < 0;
    }

    // =========================================================================
    // LANDED
    // =========================================================================

    private void updateLanded() {
        // If a hidden monitor cleared our landed flag, bounce back up
        if (!landed) {
            yVel = -0x200;
            bumpCooldown = BUMP_COOLDOWN;
            state = State.FALLING;
            LOG.fine("S3K Signpost LANDED -> FALLING (hidden monitor bounce)");
            return;
        }

        // Continue spin animation during post-land timer
        advanceAnimation();

        postLandTimer--;
        if (romPostLandTimerExpired(postLandTimer)) {
            // Show final face frame
            PlayerCharacter pc = getPlayerCharacter();
            animFrame = FACE_FRAMES[pc.ordinal()];
            xVel = 0;
            yVel = 0;
            state = State.RESULTS;
            LOG.fine("S3K Signpost LANDED -> RESULTS");
        }
    }

    static boolean romPostLandTimerExpired(int timerAfterDecrement) {
        // Obj_EndSignLanded uses subq.w #1,$2E(a0); bmi.s, so $0000 is still
        // a waiting frame and only $FFFF advances (docs/skdisasm/sonic3k.asm:176198-176208).
        return (short) timerAfterDecrement < 0;
    }

    // =========================================================================
    // RESULTS
    // =========================================================================

    private void updateResults(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // Wait for player to be on the ground
        if (player.getAir()) {
            return;
        }

        applyMainPlayerEndingPose(player);

        // ROM line 176215: st (Ctrl_2_locked).w — lock sidekick input
        // Also apply Set_PlayerEndingPose equivalent so Tails does a victory pose
        for (PlayableEntity candidate : playerQuery(player)
                .playersFor(ObjectPlayerParticipationPolicy.MAIN_PLUS_ENGINE_SIDEKICKS_AS_NATIVE_P2_EXTENDED)) {
            if (candidate instanceof AbstractPlayableSprite sprite) {
                if (sprite == player) {
                    continue;
                }
                applyEndingPose(sprite);
            }
        }

        // Spawn the results screen — pass apparentAct (ROM's Apparent_act), not
        // LevelManager.getCurrentAct(). AIZ reloads act 2 resources mid-level which
        // changes LevelManager.currentAct to 1, but Apparent_act stays 0 until results exit.
        if (services().gameState() != null) {
            services().gameState().setEndOfLevelActive(true);
        }
        spawnFreeChild(() -> new S3kResultsScreenObjectInstance(
                getPlayerCharacter(), apparentAct));
        LOG.fine("S3K Signpost RESULTS -> AFTER (results instance spawned)");
        state = State.AFTER;
    }

    private void applyEndingPose(AbstractPlayableSprite sprite) {
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setControlLocked(true);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    static void applyMainPlayerEndingPose(AbstractPlayableSprite sprite) {
        if (sprite == null) {
            return;
        }
        // Set_PlayerEndingPose writes object_control=$81, victory animation,
        // and zero velocities, but does not set Ctrl_1_locked
        // (docs/skdisasm/sonic3k.asm:181977-181988). Obj_EndSignLanded only
        // locks Ctrl_2 (docs/skdisasm/sonic3k.asm:176198-176218), so Sonic
        // keeps copying raw Ctrl_1 into Ctrl_1_logical while object_control
        // freezes movement; Sonic_RecordPos then stores that live input for
        // Tails' delayed follow history (docs/skdisasm/sonic3k.asm:21541-21545,
        // 22119-22136).
        ObjectControlState.nativeBit7FullControl().applyTo(sprite);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAnimationId(Sonic3kAnimationIds.VICTORY);
    }

    private ObjectPlayerQuery playerQuery(PlayableEntity updatePlayer) {
        ObjectPlayerQuery query = services().playerQuery();
        return new ObjectPlayerQuery(() -> updatePlayer, query::sidekicks);
    }

    private AbstractPlayableSprite resolveUpdatePlayer(PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite sprite) {
            return sprite;
        }
        PlayableEntity queriedPlayer = services().playerQuery().mainPlayerOrNull();
        return queriedPlayer instanceof AbstractPlayableSprite sprite ? sprite : null;
    }

    // =========================================================================
    // AFTER
    // =========================================================================

    private void updateAfter() {
        if (isResultsScreenActive()) {
            return;
        }

        Camera camera = services().camera();
        if (camera != null && !isWithinRomAfterRange(worldX, worldY, camera.getX(), camera.getY())) {
            setDestroyed(true);
            activeSignpost = null;
            LOG.fine("S3K Signpost destroyed (off-screen)");
        }
    }

    private boolean isResultsScreenActive() {
        return services().gameState() != null && services().gameState().isEndOfLevelActive();
    }

    static boolean isWithinRomAfterRange(int signpostX, int signpostY, int cameraX, int cameraY) {
        int dx = ((signpostX & 0xFF80) - (cameraX & 0xFF80)) & 0xFFFF;
        if (dx > AFTER_X_RANGE) {
            return false;
        }
        int dy = (signpostY - cameraY + AFTER_Y_BIAS) & 0xFFFF;
        return dy <= AFTER_Y_RANGE;
    }

    // =========================================================================
    // Animation
    // =========================================================================

    private void advanceAnimation() {
        animTimer++;
        if (animTimer >= ANIM_FRAME_DELAY) {
            animTimer = 0;
            animIndex++;
            if (animIndex >= animSequence.length) {
                animIndex = 0;
            }
            animFrame = animSequence[animIndex];
        }
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getEndSignRenderer();
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(animFrame, worldX, worldY, false, false);
    }

    private PatternSpriteRenderer getEndSignRenderer() {
        try {
            var renderManager = services().renderManager();
            if (renderManager != null) {
                return renderManager.getRenderer(Sonic3kObjectArtKeys.END_SIGN);
            }
        } catch (Exception e) {
            LOG.fine(() -> "S3kSignpostInstance.getEndSignRenderer: " + e.getMessage());
        }
        return null;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public boolean isHighPriority() {
        return true;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private PlayerCharacter getPlayerCharacter() {
        return S3kRuntimeStates.resolvePlayerCharacter(
                services().zoneRuntimeRegistry(),
                services().configuration());
    }

    public int getWorldX() {
        return worldX;
    }

    public int getWorldY() {
        return worldY;
    }

    @Override
    public String traceDebugDetails() {
        return String.format(
                "state=%s x=%04X y=%04X sub=%02X,%02X vel=%04X,%04X landed=%b timer=%04X anim=%02X/%02X",
                state,
                worldX & 0xFFFF,
                worldY & 0xFFFF,
                subX & 0xFF,
                subY & 0xFF,
                xVel & 0xFFFF,
                yVel & 0xFFFF,
                landed,
                postLandTimer & 0xFFFF,
                animFrame & 0xFF,
                animIndex & 0xFF);
    }
}
