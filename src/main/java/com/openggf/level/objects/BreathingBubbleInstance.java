package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.game.PlayableEntity;

import java.util.List;

/**
 * Small breathing bubble that rises from the player's mouth while underwater.
 * <p>
 * The bubble moves upward with a sine wave horizontal oscillation. The sine movement
 * is adjusted based on the direction the player was facing to ensure the bubble begins
 * by moving away from the player's mouth.
 * <p>
 * For countdown bubbles (showing numbers 5-0), the bubble locks its position on screen
 * when the number forms, staying visible until the animation ends.
 * <p>
 * Art key and countdown frame mapping are data-driven, allowing this class to work
 * with both Sonic 1 (LZ_BUBBLES) and Sonic 2 (BUBBLES) bubble art sheets.
 */
public class BreathingBubbleInstance extends AbstractObjectInstance {
    /**
     * ROM Obj0A/AirCountdown wobble table. S1/S2 use this as
     * Drown_WobbleData/Obj0A_WobbleData; S3K AirCountdown uses the same
     * 7-bit index pattern before MoveSprite2.
     */
    private static final int[] WOBBLE_DATA = {
        0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2,
        2, 2, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
        3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2,
        2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0,
        0, -1, -1, -1, -1, -1, -2, -2, -2, -2, -2, -3, -3, -3, -3, -3,
        -3, -3, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4,
        -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -4, -3,
        -3, -3, -3, -3, -3, -3, -2, -2, -2, -2, -2, -1, -1, -1, -1, -1
    };

    /** Frame delay per animation sub-frame for countdown bubbles */
    private static final int COUNTDOWN_FRAME_DELAY = 5;

    /** Total frames for countdown bubble animation before number forms. */
    private static final int COUNTDOWN_BUBBLE_FRAMES = 3;

    /**
     * S1 Obj0A numbered bubbles stay allocated across the Ani_Drown appear
     * script and subsequent flash/display window before Drown_Delete. This
     * keeps the slot occupied while later FindFreeObj scans run.
     */
    private static final int COUNTDOWN_NUMBER_VISIBLE_UPDATES = 91;

    /**
     * Drown_ChkWater does not delete a small bubble immediately at the water
     * surface. It changes routine to Drown_Display, bumps the animation, and
     * AnimateSprite advances to Drown_Delete on the following display tail.
     */
    private static final int SURFACE_POP_DISPLAY_UPDATES = 2;

    /** Current X position */
    private int currentX;

    /** Current Y position */
    private int currentY;

    /** Base X position for sine calculation */
    private int baseX;

    /** 16.16 Y position used by ROM SpeedToPos-style integration. */
    private int yPos16;

    /** ROM signed y_vel word, where 0x100 is one pixel/frame. */
    private int riseVelocity;

    /** ROM angle byte used to index the 7-bit wobble table. */
    private int wobbleAngle;

    /** Countdown number to display (-1 for regular bubble) */
    private int countdownNumber;

    /** Frame counter for countdown animation */
    private int countdownFrame;

    /** Whether the countdown number has formed (position locks) */
    private boolean numberFormed;

    /** Screen-space offset from camera when number forms (for position locking) */
    private int lockedScreenX;
    private int lockedScreenY;

    /** Frames since spawn, used only for diagnostics. */
    private int lifetime;

    /** ROM Drown_Display tail after Drown_ChkWater detects the water surface. */
    private int surfacePopUpdatesRemaining;

    /**
     * ROM {@code obRender} bit 7 as observed by Obj0A during execution. Drown_Main
     * initializes {@code obRender=$84}, so a newly allocated bubble survives its
     * first same-frame update; later frames observe the prior BuildSprites pass.
     */
    private boolean romRenderOnScreen = true;

    /** Art key for the bubble renderer (game-specific) */
    private final String artKey;

    /**
     * Countdown frame mapping: index = countdown number (0-5), value = art frame index.
     * Null for regular (non-countdown) bubbles.
     */
    private final int[] countdownFrameMap;

    /** Maximum frame index for regular bubble growth animation */
    private int maxBubbleFrame;

    /**
     * Creates a breathing bubble with game-specific art configuration.
     *
     * @param x                 World X coordinate (player's mouth position)
     * @param y                 World Y coordinate (player's Y position)
     * @param startsFacingLeft  True if the source player was facing left
     * @param countdownNumber   Countdown number to display (-1 for regular bubble)
     * @param artKey            Art renderer key for this game's bubble sprites
     * @param countdownFrameMap Maps countdown number (0-5) to art frame index, or null
     * @param maxBubbleFrame    Maximum frame index for regular bubble growth
     * @param riseVelocity      ROM y_vel word for this game's Obj0A child
     */
    public BreathingBubbleInstance(int x, int y, boolean startsFacingLeft, int countdownNumber,
                                   String artKey, int[] countdownFrameMap, int maxBubbleFrame,
                                   int riseVelocity) {
        super(new ObjectSpawn(x, y, 0x0A, 0, 0, false, 0), "BreathingBubble");
        this.currentX = x;
        this.currentY = y;
        this.baseX = x;
        this.yPos16 = y << 16;
        this.riseVelocity = riseVelocity;
        this.wobbleAngle = startsFacingLeft ? 0x40 : 0;
        this.countdownNumber = countdownNumber;
        this.countdownFrame = 0;
        this.numberFormed = false;
        this.lifetime = 0;
        this.artKey = artKey;
        this.countdownFrameMap = countdownFrameMap;
        this.maxBubbleFrame = maxBubbleFrame;
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        boolean observedRomRenderOnScreen = romRenderOnScreen;
        lifetime++;

        if (surfacePopUpdatesRemaining > 0) {
            surfacePopUpdatesRemaining--;
            if (surfacePopUpdatesRemaining == 0) {
                setDestroyed(true);
            }
            return;
        }

        // Check if we've exited water (bubble pops)
        // ROM: Bub_ChkWater compares against the gameplay waterline
        // (S1 v_waterpos1, docs/s1disasm/_incObj/64 Bubbles.asm:57-70).
        // Use getFeatureZoneId/ActId to match the keys WaterSystem stores
        // configs under (important for SBZ3).
        if (services().currentLevel() != null) {
            WaterSystem waterSystem = services().waterSystem();
            int zoneId = services().featureZoneId();
            int actId = services().featureActId();

            if (waterSystem.hasWater(zoneId, actId)) {
                int waterY = waterSystem.getGameplayWaterLevelY(zoneId, actId);
                if (currentY <= waterY) {
                    // docs/s1disasm/s1disasm/_incObj/0A LZ Drowning Countdown.asm:
                    // Drown_ChkWater sets routine 6 and falls into Drown_Display;
                    // Drown_Delete is reached after the display animation tail.
                    surfacePopUpdatesRemaining = SURFACE_POP_DISPLAY_UPDATES;
                    return;
                }
            }
        }

        // Handle countdown bubble animation
        if (countdownNumber >= 0) {
            if (numberFormed && !observedRomRenderOnScreen) {
                setDestroyed(true);
                return;
            }
            countdownFrame++;

            // Check if number is about to form (one frame before)
            int formFrame = COUNTDOWN_BUBBLE_FRAMES * COUNTDOWN_FRAME_DELAY;
            if (countdownFrame >= formFrame - 1 && !numberFormed) {
                // Lock position relative to camera
                Camera camera = services().camera();
                lockedScreenX = (int) currentX - camera.getX();
                lockedScreenY = currentY - camera.getY();
                numberFormed = true;
            }

            // Check if animation is complete.
            if (countdownFrame > COUNTDOWN_NUMBER_VISIBLE_UPDATES) {
                setDestroyed(true);
                return;
            }
        }

        // Movement logic
        if (numberFormed) {
            // Position locked relative to camera
            Camera camera = services().camera();
            currentX = camera.getX() + lockedScreenX;
            currentY = camera.getY() + lockedScreenY;
        } else {
            // Normal bubble movement
            ZoneFeatureProvider zoneFeatures = services().zoneFeatureProvider();
            if (zoneFeatures != null && zoneFeatures.isWaterTunnelActive()) {
                // docs/s1disasm/s1disasm/_incObj/0A LZ Drowning Countdown.asm:
                // Drown_ChkWater tests f_wtunnelmode, then addq.w #4,drown_origX(a0).
                baseX += 4;
            }
            int wobbleIndex = wobbleAngle & 0x7F;
            wobbleAngle = (wobbleAngle + 1) & 0xFF;
            currentX = baseX + WOBBLE_DATA[wobbleIndex];

            yPos16 += riseVelocity << 8;
            currentY = yPos16 >> 16;

            // ROM Obj0A Drown_ChkWater deletes live mouth bubbles when
            // BuildSprites cleared obRender bit 7 on the previous frame
            // (docs/s1disasm/s1disasm/_incObj/0A LZ Drowning Countdown.asm:75-78).
            if (!observedRomRenderOnScreen) {
                setDestroyed(true);
                return;
            }
        }

        // No lifetime cap: Obj0A live bubbles only delete via the water-surface
        // display path or when Drown_ChkWater observes obRender bit 7 clear
        // after SpeedToPos (docs/s1disasm/s1disasm/_incObj/
        // 0A LZ Drowning Countdown.asm:55-92).
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        Camera camera = services().camera();
        int screenX = currentX - camera.getX();
        int screenY = currentY - camera.getY();

        // Only render if on screen
        if (screenX < -16 || screenX > camera.getWidth() + 16 ||
            screenY < -16 || screenY > camera.getHeight() + 16) {
            return;
        }

        // Get the bubble renderer using game-specific art key
        PatternSpriteRenderer renderer = getRenderer(artKey);
        if (renderer == null) return;

        // Determine which frame to render
        int frameIndex;
        if (countdownNumber >= 0 && countdownFrameMap != null) {
            // Countdown bubble - show animation frames then number
            int formFrame = COUNTDOWN_BUBBLE_FRAMES * COUNTDOWN_FRAME_DELAY;
            if (countdownFrame >= formFrame) {
                // Show the countdown number using game-specific frame mapping
                frameIndex = countdownFrameMap[countdownNumber];
            } else {
                // Bubble growing animation - cycle through bubble sizes
                int animFrame = countdownFrame / COUNTDOWN_FRAME_DELAY;
                frameIndex = Math.min(animFrame / 3, maxBubbleFrame);
            }
        } else {
            // Regular small bubble - grow through frames as bubble rises
            int growthStage = lifetime / 30;  // Change frame every 30 ticks (~0.5 sec)
            frameIndex = Math.min(growthStage, maxBubbleFrame);
        }

        // Render the sprite
        renderer.drawFrameIndex(frameIndex, currentX, currentY, false, false);
    }

    @Override
    public void refreshPostCameraRenderState() {
        romRenderOnScreen = isWithinS1BuildSpritesBounds();
    }

    private boolean isWithinS1BuildSpritesBounds() {
        Camera camera = services().camera();
        int screenX = currentX - camera.getX();
        int screenY = currentY - camera.getY();
        // Drown_Main sets obRender=$84 and obActWid=16, with bit 4 clear, so
        // BuildSprites uses the 32px assumed-height branch for Y.
        return screenX + 16 >= 0
                && screenX - 16 < camera.getWidth()
                && screenY >= -32
                && screenY < 224 + 32;
    }

    /**
     * Gets the current X position (integer for ObjectInstance interface).
     */
    @Override
    public int getX() {
        return currentX;
    }

    /**
     * Gets the current Y position.
     */
    public int getY() {
        return currentY;
    }

    /**
     * Returns whether this is a countdown bubble.
     */
    public boolean isCountdownBubble() {
        return countdownNumber >= 0;
    }

    /**
     * Returns the countdown number (-1 if regular bubble).
     */
    public int getCountdownNumber() {
        return countdownNumber;
    }

    @Override
    public String traceDebugDetails() {
        return String.format("life=%d ysub=%04X angle=%02X vel=%04X num=%d",
            lifetime, yPos16 & 0xFFFF, wobbleAngle & 0xFF, riseVelocity & 0xFFFF, countdownNumber);
    }
}
