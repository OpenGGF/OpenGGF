package com.openggf.game.sonic2.objects;

import static com.openggf.game.sonic2.Sonic2RomAddresses.address;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SpawnCoordinateZeroPairRewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * ARZ Bubble (Object 0x24 routine 4) - Rising breathable bubble.
 * Spawned by BubbleGeneratorObjectInstance.
 * <p>
 * Based on loc_1F924 through loc_1F99E (s2.asm:45231-45285).
 * <p>
 * Physics:
 * - Rises upward at -0x88 velocity (8.8 fixed point = ~0.53 pixels/frame)
 * - Horizontal sine-wave wobble using 128-byte lookup table
 * - Pops when reaching water surface
 * <p>
 * Player Interaction:
 * - Large bubbles (mapping_frame >= 6) can be breathed by player
 * - When touched, restores player's air and plays inhaling sound
 */
public class BubbleObjectInstance extends AbstractObjectInstance
        implements SpawnCoordinateZeroPairRewindRecreatable, ObjectVisualFacing.NonDirectional {
    // Obj24 bubbles rise and wobble without a logical horizontal front.

    // Rise velocity in 8.8 fixed point (-0x88 = ~-0.53 pixels/frame upward)
    private static final int RISE_VELOCITY = -0x88;

    // Obj0A_WobbleData and Ani_obj24, stock S2 REV01. Runtime bytes stay ROM-owned.
    private static final int WOBBLE_DATA_ADDRESS = 0x1D4C0;
    private static final int ANIMATION_TABLE_ADDRESS = 0x1FBCC;

    // Touch collision half-sizes for breathable bubbles (ROM Touch_Sizes entry)
    private static final int COLLISION_HALF_WIDTH = 16;
    private static final int COLLISION_HALF_HEIGHT = 16;

    /**
     * ROM {@code objoff_2E} condition: {@code loc_1F924} runs AnimateSprite and
     * then {@code cmpi.b #6,mapping_frame(a0) / bne / move.b #1,objoff_2E(a0)}
     * (docs/s2disasm/s2.asm:45231-45236). Mapping frame 6 is the only thing that
     * makes a bubble inhalable, and only one animation script ever reaches it.
     */
    private static final int BREATHABLE_FRAME_THRESHOLD = 6;

    // Native routine starts at 2 after Obj24_Init; 4 rises without inflating,
    // 6 runs the pop animation, and 8 deletes on the following own slot pass.
    private int routine = 2;
    private int previousAnim = -1;

    // Position as 16.16 fixed point
    private int posX16;
    private int posY16;

    // Base X position for wobble calculation
    private int baseX;

    // Wobble angle (0-255, wraps around) - ROM uses only low byte
    private int wobbleAngle;
    private boolean wobbleAnglePendingRng;

    // Current display position
    private int displayX;
    private int displayY;

    /**
     * ROM {@code anim(a0)}, written from {@code subtype} by
     * {@code loc_1F90A} ({@code move.b d0,anim(a0)}, docs/s2disasm/s2.asm:45225-45230).
     * 0 = small, 1 = medium, 2 = large. This is the ROM's own subtype byte, not
     * a size scale.
     */
    private int animId;

    // Mapping frame for rendering (ROM mapping_frame(a0))
    private int mappingFrame;

    /** ROM {@code anim_frame} -- index into the current script's frame list. */
    private int animFrameIndex;

    /** ROM {@code anim_frame_duration}. */
    private int animTimer;

    /** ROM {@code objoff_2E} -- set once, when mapping_frame reaches 6. */
    private boolean inhalable;

    // Whether this bubble has been breathed (collected)
    private boolean breathed;

    // ROM render_flags bit 7 as observed by Obj24 after ObjectMove. Obj24_Init
    // starts with render_flags=$84 (docs/s2disasm/s2.asm:45209), so a
    // just-allocated offscreen bubble survives one execution before the next
    // Render_Sprites result can delete it.
    private boolean romRenderOnScreen;

    /**
     * Whether the previous pass reached {@code DisplaySprite} at {@code loc_1F988}
     * (docs/s2disasm/s2.asm:45265-45267). {@code BuildSprites} only rewrites
     * {@code render_flags} bit 7 for objects that queued themselves that frame,
     * so a bubble that has not executed yet -- allocated during one object pass
     * but first run on the next -- has never been queued and keeps
     * {@code Obj24_Init}'s {@code $84} until its own first pass draws it.
     */
    private boolean romDisplayedLastPass;

    /**
     * Creates a rising bubble at the specified position.
     *
     * @param x           X position (world coordinates)
     * @param y           Y position (world coordinates)
     * @param bubbleSize  ROM {@code subtype}: 0 = small, 1 = medium, 2 = large
     * @param wobbleAngle Initial wobble angle (0-255)
     */
    public BubbleObjectInstance(int x, int y, int bubbleSize, int wobbleAngle) {
        this(x, y, bubbleSize, wobbleAngle, false);
    }

    BubbleObjectInstance(int x, int y, int bubbleSize, int wobbleAngle, boolean wobbleAnglePendingRng) {
        super(createDummySpawn(x, y), "Bubble");

        // Store position as 16.16 fixed point
        this.posX16 = x << 16;
        this.posY16 = y << 16;
        this.baseX = x;

        this.wobbleAngle = wobbleAngle & 0xFF;
        this.wobbleAnglePendingRng = wobbleAnglePendingRng;
        this.animId = bubbleSize;
        this.breathed = false;
        this.romRenderOnScreen = true;
        this.inhalable = false;

        // ROM Obj24_Init only writes anim(a0); AnimateSprite establishes
        // mapping_frame from the script on the bubble's first animation tick,
        // and every script's first frame equals its own index.
        this.animFrameIndex = 0;
        this.mappingFrame = 0;
        this.animTimer = 0;

        // Initial display position
        this.displayX = x;
        this.displayY = y;
    }

    private static ObjectSpawn createDummySpawn(int x, int y) {
        return new ObjectSpawn(x, y, 0x24, 0, 0, false, 0);
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        boolean observedRomRenderOnScreen = romRenderOnScreen;
        if (routine == 8) {
            setDestroyed(true);
            return;
        }
        if (wobbleAnglePendingRng) {
            wobbleAngle = services().rng().nextByte();
            wobbleAnglePendingRng = false;
        }
        if (routine == 6) {
            animateSprite();
            displayOrDelete(observedRomRenderOnScreen);
            return;
        }
        // loc_1F924 falls through even when FC advances routine 2 to 4.
        if (routine == 2) {
            animateSprite();
            if (mappingFrame == BREATHABLE_FRAME_THRESHOLD) inhalable = true;
        }
        if (atOrAboveWater()) {
            beginPop();
            animateSprite();
            displayOrDelete(observedRomRenderOnScreen);
            return;
        }
        int wobbleIndex = wobbleAngle & 0x7F;
        wobbleAngle = (wobbleAngle + 1) & 0xFF;
        displayX = baseX + (byte) romByte(fixedRomAddress(WOBBLE_DATA_ADDRESS) + wobbleIndex);
        // Native collect precedes ObjectMove AND the render-bit delete gate.
        if (player != null && isBreathable()) checkPlayerCollision(player);
        if (routine == 6) {
            animateSprite();
            displayOrDelete(observedRomRenderOnScreen);
            return;
        }
        posY16 += RISE_VELOCITY << 8;
        displayY = posY16 >> 16;
        displayOrDelete(observedRomRenderOnScreen);
    }

    private boolean atOrAboveWater() {
        if (services().currentLevel() == null) return false;
        WaterSystem water = services().waterSystem();
        int zone = services().featureZoneId(), act = services().featureActId();
        return water.hasWater(zone, act) && displayY <= water.getWaterLevelY(zone, act);
    }

    private void beginPop() {
        if (routine != 6) {
            routine = 6;
            animId = (animId + 3) & 0xFF;
        }
    }

    private void displayOrDelete(boolean observedRomRenderOnScreen) {
        if (!observedRomRenderOnScreen) setDestroyed(true);
        else romDisplayedLastPass = true;
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() { return true; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // Obj24 has no MarkObjGone: own render-bit/surface routines own lifetime.
        return false;
    }

    /**
     * {@code BuildSprites} runs after {@code RunObjects} and after
     * {@code DeformBgLayer} has scrolled the camera and republished
     * {@code Camera_X_pos_copy} (docs/s2disasm/s2.asm:5094-5110, 15174-15179),
     * so the verdict a pass reads was taken against that frame's camera at the
     * object's post-{@code ObjectMove} position. Publishing from inside
     * {@code update()} instead used the camera the object pass still held, one
     * deform older.
     * <p>
     * The guard is the other half: {@code BuildSprites} only rewrites the bit
     * for objects in the sprite queue, so a bubble allocated during one object
     * pass and first run on the next must keep {@code Obj24_Init}'s {@code $84}
     * through the frame it has not executed in. Without it the hook judges a
     * bubble that has never drawn, and every off-screen bubble dies one pass
     * early.
     */
    @Override
    public void refreshPostCameraRenderState() {
        if (!romDisplayedLastPass) {
            return;
        }
        romDisplayedLastPass = false;
        romRenderOnScreen = isWithinRenderSpriteBounds(
                getOnScreenHalfWidth(), getOnScreenHalfHeight());
    }

    /**
     * ROM {@code objoff_2E}. Set by {@code loc_1F924} when AnimateSprite has
     * advanced {@code mapping_frame} to 6 (docs/s2disasm/s2.asm:45231-45236), and
     * tested by {@code loc_1F956}'s {@code tst.b objoff_2E(a0) / beq} before the
     * collect box runs at all (:45257-45259).
     * <p>
     * The previous engine condition was {@code mappingFrame >= 6 || bubbleSize >= 3}.
     * Its first half was unreachable -- {@code mappingFrame} was seeded as
     * {@code min(bubbleSize, 5)} and only ever grew while {@code < 5} -- so the
     * live condition was a size test that is true the moment the bubble is
     * created. That made every large bubble inhalable on spawn and skipped the
     * ROM's entire inflate climb.
     */
    private boolean isBreathable() {
        return inhalable;
    }

    /** AnimateSprite/Anim_End_FC, s2.asm:30422-30488; Ani_obj24:45471-45496. */
    private void animateSprite() {
        if (previousAnim != animId) {
            previousAnim = animId;
            animFrameIndex = animTimer = 0;
        }
        animTimer = (byte) (animTimer - 1);
        if (animTimer >= 0) return;
        int script = fixedRomAddress(ANIMATION_TABLE_ADDRESS) + romWord(fixedRomAddress(ANIMATION_TABLE_ADDRESS) + animId * 2);
        animTimer = (byte) romByte(script);
        int command = romByte(script + 1 + animFrameIndex);
        if (command == 0xFC) {
            routine = (routine + 2) & 0xFF;
            animTimer = 0;
            animFrameIndex = (animFrameIndex + 1) & 0xFF;
            return;
        }
        if (command == 0xFF) {
            animFrameIndex = 0;
            command = romByte(script + 1);
        }
        if (command >= 0x80) throw new IllegalStateException("Unsupported Obj24 animation command " + command);
        mappingFrame = command & 0x7F;
        animFrameIndex = (animFrameIndex + 1) & 0xFF;
    }

    private int fixedRomAddress(int address) {
        try { return address(services().romReader(), address); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private int romByte(int address) {
        try { return services().romReader().readU8(address); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private int romWord(int address) {
        try { return services().romReader().readS16BE(address); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /**
     * Checks for collision with the player and handles air restoration.
     * ROM uses an asymmetric collision box: 16 either side horizontally,
     * downward-only from bubble Y.
     * <p>
     * ROM collision logic ({@code loc_1FB0C}, docs/s2disasm/s2.asm:45422-45436):
     * X: (bubble_x - 16) < player_x <= (bubble_x + 16)
     * Y: bubble_y < player_y <= (bubble_y + 16)
     * <p>
     * The player's center must be BELOW the bubble (bubble above player),
     * but within 16 pixels. This is a downward-only collision box.
     */
    private void checkPlayerCollision(AbstractPlayableSprite player) {
        // Only interact if player is underwater
        if (!player.isInWater()) {
            return;
        }

        int playerX = player.getCentreX();
        int playerY = player.getCentreY();

        // ROM asymmetric box collision:
        // X: ±16 from bubble center (move.w x_pos(a0),d1 / subi.w #$10,d1 ... addi.w #$20,d1)
        // Y: downward-only - player must be BELOW bubble but within 16px
        //    ROM: cmp.w y_pos(a0),d1 / blo.s (bubble_y < player_y required)
        //    ROM: addi.w #$10,d1 / cmp.w d1,d0 (player_y < bubble_y + 16)
        int bubbleLeft = displayX - COLLISION_HALF_WIDTH;
        int bubbleRight = displayX + COLLISION_HALF_WIDTH;

        // ROM loc_1FB0C (docs/s2disasm/s2.asm:45422-45436). Every bound is a
        // signed word compare against a branch that RETURNS on failure, so the
        // surviving window is:
        //   subi.w #$10,d1 / cmp.w d0,d1 / bhs -> bubble_x - $10 <  player_x
        //   addi.w #$20,d1 / cmp.w d0,d1 / blo -> player_x <= bubble_x + $10
        //   cmp.w d0,d1    / bhs         -> bubble_y      <  player_y
        //   addi.w #$10,d1 / cmp.w d0,d1 / blo -> player_y <= bubble_y + $10
        // Both upper bounds are inclusive and both lower bounds are exclusive;
        // an exclusive upper Y bound loses exactly the frame where the player
        // sits on the bottom edge of the box, which is where this inhale lands.
        if (playerX > bubbleLeft && playerX <= bubbleRight &&
            playerY > displayY && playerY <= displayY + COLLISION_HALF_HEIGHT) {

            // Player touched the bubble - restore air
            player.replenishAir();

            // ROM also clears player velocity and locks movement for 35 frames
            // (lines 44966-44998), but this requires additional player state access
            // which we defer to replenishAir() for minimal invasiveness

            // Play inhaling sound
            try {
                services().playSfx(Sonic2Sfx.INHALING_BUBBLE.id);
            } catch (Exception e) {
                // Don't let audio failure break game logic
            }

            breathed = true;
            beginPop();
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.BUBBLES);
        if (renderer == null) return;

        renderer.drawFrameIndex(mappingFrame, displayX, displayY, false, false);
    }

    @Override
    public int getX() {
        return displayX;
    }

    @Override
    public int getY() {
        return displayY;
    }

    @Override
    public int getPriorityBucket() {
        return 1; // ROM: move.b #1,priority(a1)
    }

    /**
     * Returns true if this bubble was breathed by the player.
     */
    public boolean wasBreathed() {
        return breathed;
    }
}
