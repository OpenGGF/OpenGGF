package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2Rng;
import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.camera.Camera;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Whisp (0x8C) - Blowfly Badnik from Aquatic Ruin Zone.
 * A flying enemy that chases the player in cycles.
 *
 * Behavior from disassembly (obj8C.asm, lines 72660-72772):
 * 1. INIT: Load graphics, set 4 attack cycles
 * 2. WAIT_ONSCREEN: Wait until visible on screen, then decrement attacks and start chase
 * 3. CHASE: Accelerate toward player for 96 frames, initial Y velocity -0x100
 * 4. PAUSE: Random 0-31 frame pause, then decrement attacks and chase again
 * 5. FLY_AWAY: When attacks exhausted, escape at max speed up-left
 *
 * Movement physics:
 * - Acceleration: ±0x10 (16 subpixels) per frame toward player
 * - Max velocity: ±0x200 (512 subpixels = 2 pixels/frame)
 * - 4 attack cycles before escape
 * - Escape velocity: x=-0x200, y=-0x200 (flies up-left at 45°)
 * - Initial chase Y velocity: -0x100 (upward momentum)
 */
public class WhispBadnikInstance extends AbstractBadnikInstance {

    private enum State {
        INIT,              // Routine 0: Initialize
        WAIT_ONSCREEN,     // Routine 2: Wait until visible
        CHASE,             // Routine 4: Chase player
        PAUSE,             // Routine 6: Pause between attacks
        FLY_AWAY           // Routine 8: Escape
    }

    // Collision size from Touch_Sizes table index 0x0B = 8x8 pixels
    // (disassembly line 72762: subObjData ...$B)
    private static final int COLLISION_SIZE_INDEX = 0x0B;

    // Timing constants from disassembly
    private static final int CHASE_DURATION = 96;         // 96 frames chase duration (line 72712)
    private static final int MAX_ATTACKS = 4;             // 4 attack cycles before escape (line 72678)
    private static final int PAUSE_MASK = 0x1F;           // Random pause 0-31 frames (line 72746)

    // Movement constants (fixed-point 8.8 format)
    private static final int ACCELERATION = 0x10;         // Per frame acceleration (line 72738-72739)
    private static final int MAX_VELOCITY = 0x200;        // 2 pixels/frame max speed (line 72728-72729)
    private static final int ESCAPE_VELOCITY_X = -0x200;  // Escape velocity X (line 72704)
    private static final int ESCAPE_VELOCITY_Y = -0x200;  // Escape velocity Y (line 72705)
    private static final int INITIAL_CHASE_Y_VEL = -0x100; // Initial upward velocity when starting chase (line 72711)

    // ROM Render_Sprites / BuildSprites on-screen culling parameters (s2.asm:30560-30621).
    // The X test sets render_flags.on_screen when the object's render box overlaps
    // the 320px-wide screen: (x_pos - cam_x + width_pixels) >= 0 AND
    // (x_pos - cam_x - width_pixels) < screen_width. width_pixels for Obj8C is $C
    // (subObjData ...,4,$C,$B at s2.asm:73223 -> the $C width field).
    private static final int RENDER_WIDTH_PIXELS = 0x0C;   // width_pixels(a0) for Obj8C
    private static final int SCREEN_WIDTH = 320;
    // Obj8C is displayed with the approximate-Y check (level_fg, no explicit_height),
    // which assumes a Y radius of 32px: on-screen in Y when
    // (y_pos - cam_y) in [-32, screen_height+32) (s2.asm:30603-30609).
    private static final int RENDER_Y_RADIUS = 32;
    private static final int SCREEN_HEIGHT = 224;

    private State state;
    private int timer;
    private int attacksRemaining;

    // ROM render_flags.on_screen bit. BuildSprites sets it at the END of a frame's
    // display pass (s2.asm:30621), and Obj8C_WaitUntilOnscreen tests it at the
    // START of the NEXT frame's object update (s2.asm:73138). We mirror that
    // one-frame display-then-observe ordering with a deferred flag so the chase
    // starts on the same frame the ROM does.
    private boolean onScreenFlag;

    // Fixed-point position (8.8 format for subpixel accuracy)
    private int xPosFixed;
    private int yPosFixed;

    // Fixed-point velocities (8.8 format)
    private int xVelFixed;
    private int yVelFixed;

    public WhispBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Whisp", Sonic2BadnikConfig.DESTRUCTION);
        this.state = State.INIT;
        this.timer = 0;
        this.attacksRemaining = MAX_ATTACKS;
        this.xVelFixed = 0;
        this.yVelFixed = 0;

        // Initialize fixed-point positions (shift by 8 for subpixel precision)
        this.xPosFixed = currentX << 8;
        this.yPosFixed = currentY << 8;

        // Initial facing based on render_flags
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.facingLeft = !xFlip;
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state) {
            case INIT -> updateInit();
            case WAIT_ONSCREEN -> updateWaitOnscreen();
            case CHASE -> updateChase(player);
            case PAUSE -> updatePause();
            case FLY_AWAY -> updateFlyAway();
        }

        // Update integer position from fixed-point
        currentX = xPosFixed >> 8;
        currentY = yPosFixed >> 8;
    }

    /**
     * INIT state: Transition immediately to WAIT_ONSCREEN.
     */
    private void updateInit() {
        state = State.WAIT_ONSCREEN;
    }

    /**
     * WAIT_ONSCREEN state: Wait until the whisp is visible on screen.
     * When visible, decrement attacks and start chase (matching loc_36970 flow).
     */
    private void updateWaitOnscreen() {
        // Obj8C_WaitUntilOnscreen (s2.asm:73138-73140): test render_flags.on_screen,
        // and on a set bit branch to loc_36970 to begin the first attack. The flag
        // reflects the PREVIOUS frame's BuildSprites pass, so we observe last frame's
        // computed value first, then recompute it from this frame's geometry for the
        // next frame. This deferred ordering is what aligns the engine's chase start
        // with the ROM's (a live same-frame check started the chase ~2 frames early,
        // drifting the Whisp ahead of ROM into Sonic's touch box -> ARZ frame 2169).
        boolean wasOnScreen = onScreenFlag;
        onScreenFlag = computeOnScreen();
        if (wasOnScreen) {
            startNextAttackOrEscape();
        }
    }

    /**
     * ROM BuildSprites on-screen culling for Obj8C (s2.asm:30560-30621). Sets the
     * render_flags.on_screen bit when the object's render box overlaps the screen.
     */
    private boolean computeOnScreen() {
        Camera camera = services().camera();
        int screenX = currentX - camera.getX();
        // X: (screenX + width) >= 0 && (screenX - width) < screen_width (s2.asm:30566-30571)
        if (screenX + RENDER_WIDTH_PIXELS < 0) return false;
        if (screenX - RENDER_WIDTH_PIXELS >= SCREEN_WIDTH) return false;
        // Y: approximate check, radius 32 -> screenY in [-32, screen_height+32)
        // (s2.asm:30603-30609)
        int screenY = currentY - camera.getY();
        if (screenY + RENDER_Y_RADIUS < 0) return false;
        if (screenY - RENDER_Y_RADIUS >= SCREEN_HEIGHT) return false;
        return true;
    }

    /**
     * Common routine for starting next attack or escaping (loc_36970).
     * Decrements attack counter BEFORE starting chase, not after.
     */
    private void startNextAttackOrEscape() {
        attacksRemaining--;
        if (attacksRemaining < 0) {
            // All attacks exhausted - fly away (routine 8)
            state = State.FLY_AWAY;
            xVelFixed = ESCAPE_VELOCITY_X;
            yVelFixed = ESCAPE_VELOCITY_Y;
        } else {
            // Start chase with initial upward velocity (routine 4)
            state = State.CHASE;
            timer = CHASE_DURATION;
            yVelFixed = INITIAL_CHASE_Y_VEL;  // -0x100 upward (line 72711)
        }
    }

    /**
     * PAUSE state: Random 0-31 frame pause between attacks
     * (Obj8C_WaitUntilTimerExpires, routine 6, s2.asm:73145-73147).
     *
     * <p>ROM decrements the timer FIRST and branches to the attack check on
     * underflow: {@code subq.b #1,obj8C_timer(a0) ; bmi.s loc_36970}. {@code bmi}
     * fires only when the result is strictly negative, so a pause loaded with
     * value P lasts P+1 frames. The previous {@code timer <= 0} test ended the
     * pause one frame early (P frames per cycle). Using {@code timer < 0}
     * reproduces the ROM {@code bmi} timing exactly.
     */
    private void updatePause() {
        timer--;
        if (timer < 0) {
            // Timer underflowed (bmi.s loc_36970) - check attacks and start next chase
            startNextAttackOrEscape();
        }
    }

    /**
     * CHASE state: Accelerate toward player for 96 frames.
     * When timer expires, transition to PAUSE with random duration.
     */
    private void updateChase(AbstractPlayableSprite player) {
        if (player != null) {
            // Calculate direction to player
            int playerX = player.getCentreX();
            int playerY = player.getCentreY();

            // Accelerate toward player on X axis
            if (playerX < currentX) {
                xVelFixed -= ACCELERATION;
                if (xVelFixed < -MAX_VELOCITY) {
                    xVelFixed = -MAX_VELOCITY;
                }
                facingLeft = true;
            } else {
                xVelFixed += ACCELERATION;
                if (xVelFixed > MAX_VELOCITY) {
                    xVelFixed = MAX_VELOCITY;
                }
                facingLeft = false;
            }

            // Accelerate toward player on Y axis
            if (playerY < currentY) {
                yVelFixed -= ACCELERATION;
                if (yVelFixed < -MAX_VELOCITY) {
                    yVelFixed = -MAX_VELOCITY;
                }
            } else {
                yVelFixed += ACCELERATION;
                if (yVelFixed > MAX_VELOCITY) {
                    yVelFixed = MAX_VELOCITY;
                }
            }
        }

        // Apply velocity to position
        xPosFixed += xVelFixed;
        yPosFixed += yVelFixed;

        // Decrement chase timer
        timer--;
        if (timer <= 0) {
            // Chase finished - transition to pause with random duration (lines 72744-72747)
            state = State.PAUSE;
            timer = Sonic2Rng.nextWhispPauseTimer(services().rng());  // Random 0-31 frames
        }
    }

    /**
     * FLY_AWAY state: Escape at max speed up-left.
     */
    private void updateFlyAway() {
        // Apply constant escape velocity
        xPosFixed += xVelFixed;
        yPosFixed += yVelFixed;
        facingLeft = true;
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // Fast wing flapping - toggle between frames every tick
        animFrame = frameCounter & 1;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.WHISP);
        if (renderer == null) return;

        // Render current animation frame
        // Art faces right by default; flip when facing left
        boolean hFlip = facingLeft;
        boolean vFlip = false;
        renderer.drawFrameIndex(animFrame, currentX, currentY, hFlip, vFlip);
    }
}
