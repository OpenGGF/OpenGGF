package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * A rising bubble: the bubble spawner ID $20 with a subtype below $80 ({@code BubbleSpawnerObject_0_Routine0}
 * .. {@code Routine6}, objects/r4/bubble_spawner.asm:25-120; the spawner creates it, :186-196) and the player
 * bubble ID $21 with a subtype below $80 ({@code PlayerBubblesObject_0_Routine0}..{@code Routine6},
 * objects/r4/player_bubbles.asm:24-96). Both draw {@code BubbleSprites} (tile $848A, priority 1, $20 wide,
 * on-screen bit set by the init), start rising at {@code -$88} (16.16 pixels a frame times 256) from the
 * angle {@code Random} gave and sway by {@code WobbleTable[angle & $7F]} around their start X. A bubble
 * reaching {@code water_y} switches to the pop animation ({@code anim + 3} for the spawner's, {@code + 7} for the
 * player's); an off-screen bubble deletes itself.
 *
 * <p>A player bubble drifts four pixels a frame while a water current holds the player, and a countdown number
 * pins itself to the screen ({@code sub_2061D4}). The grown spawner bubble's breath ({@code sub_20A81E},
 * {@code CdPlayerWater.breatheAtBubble}) is called from the rise: it needs the bubble's native slot (below the
 * spawner, so it first runs a frame after it is made), which the layout walk and {@code CdObject.onUnload} (a
 * range-deleted parent takes its children with it) now give.
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBubble extends TtzObject implements ObjectVisualFacing.NonDirectional {
    private final boolean forPlayer;
    private final int startAngle;
    private int routine;
    private int baseX;
    private int angle;
    private int yFixed;
    private int yVel;
    private boolean grown;
    private boolean seenRender;
    /** The bubble has had its turn in the object loop since the last end-of-frame pass. */
    private boolean ranSincePass;
    private CdAnimator animator;
    private int mapFrame;
    private int anim;
    /** {@code obj.var_38}: a countdown number's timer ($1C at spawn for a countdown number). */
    private int numberTimer;
    /** The number's position on screen once {@code sub_2061D4} pins it ({@code sprite_flags} $80). */
    private int screenX;
    private int screenY;

    public ObjBubble(ObjectSpawn spawn, boolean forPlayer, int startAngle) {
        this(spawn, forPlayer, startAngle, 0);
    }

    /** A player bubble with {@code obj.var_38} = {@code numberTimer} (a countdown number when nonzero). */
    public ObjBubble(ObjectSpawn spawn, boolean forPlayer, int startAngle, int numberTimer) {
        super(spawn, forPlayer ? "PlayerBubble" : "Bubble");
        this.forPlayer = forPlayer;
        this.startAngle = startAngle;
        this.numberTimer = numberTimer;
        this.yFixed = y << 16;
    }

    /** move.b #$84,obj.sprite_flags(a0): the on-screen bit is set until the first DrawObjects. */
    private boolean onScreenBit() {
        return !seenRender || renderFlagOnScreen();
    }

    /**
     * The init's {@code $84} (bubble_spawner.asm:26) is replaced only by the bubble's own {@code DrawObject}
     * (:102-109, in its routine): a bubble the spawner put in a slot the loop had already passed
     * ({@code SpawnObject}, :175) has not run, so the frame's pass leaves its bit set, as
     * {@link com.openggf.game.soniccd.objects.common.CdObject} keeps such a child's pass for its first turn.
     */
    @Override
    public void refreshPostCameraRenderState() {
        super.refreshPostCameraRenderState();
        if (ranSincePass) seenRender = true;
        ranSincePass = false;
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        ranSincePass = true;
        switch (routine) {
            case 0 -> { init(); animate(); rise(); }          // Routine0 falls into Routine2 and Routine4
            case 2 -> { animate(); rise(); }
            case 4 -> rise();
            case 6, 0xE -> pop();                              // the player table runs Routine6 for $E too
            case 0xC -> showNumber();
            default -> setDestroyed(true);                    // Routine8
        }
    }

    private void init() {
        routine = 2;
        priority = 1;
        width = 0x10;
        tileWord = TtzObjectArt.BUBBLE_TILE;
        anim = spawn.subtype();
        baseX = x;
        yVel = -0x88;
        angle = forPlayer ? startAngle : services().rng().nextByte();   // Random (BubbleSpawnerObject_0_Routine0, objects/r4/bubble_spawner.asm:40-45)
        animator = TtzObjectArt.animator(artProvider(), forPlayer ? "playerBubbleAnimations" : "bubbleAnimations", anim);
    }

    private void animate() {
        if (animator == null) return;
        animator.step();
        mapFrame = animator.mapFrame();
        if (!forPlayer && mapFrame == 6) grown = true;       // cmpi.b #6,obj.sprite_frame; move.b #1,obj.var_2e
    }

    /** BubbleSpawnerObject_0_Routine4 (objects/r4/bubble_spawner.asm:54-111). */
    private void rise() {
        if (waterY() >= y) {                                  // cmp.w obj.y(a0),d0; bcs.s: at or above the surface
            routine = 6;
            anim += forPlayer ? 7 : 3;
            if (animator != null) animator.anim = anim;
            pop();
            return;
        }
        var water = services().gameService(com.openggf.game.soniccd.player.CdPlayerWater.class);
        if (forPlayer && water != null && water.currentActive()) {
            baseX += 4;                                       // addq.w #4,obj.var_30 (loc_20613C, player_bubbles.asm:63-66)
        }
        angle = (angle + 1) & 0xFF;
        int wobble = wobble((angle - 1) & 0x7F);
        x = (baseX + wobble) & 0xFFFF;
        if (forPlayer) {
            pinNumber();                                      // bsr.s sub_2061D4 (player_bubbles.asm:77)
        } else if (grown && com.openggf.game.soniccd.player.CdPlayerWater.breatheAtBubble(mainPlayer(), x, y)) {
            routine = 6;                                      // loc_20A600: breathed, then loc_20A5F2 (bubble_spawner.asm:59-96)
            anim += 3;
            if (animator != null) animator.anim = anim;
            pop();
            return;
        }
        if (routine == 0xC) return;                           // pinned: speed 0, drawn on screen
        yFixed += yVel << 8;                                  // MoveObject
        y = yFixed >> 16;
        if (!onScreenBit()) setDestroyed(true);               // loc_20A698: DeleteObject
    }

    /** Routine6: animate the pop and draw; an off-screen pop deletes (BubbleSpawnerObject_0_Routine6, objects/r4/bubble_spawner.asm:113-125). */
    private void pop() {
        if (forPlayer) pinNumber();                           // PlayerBubblesObject_0_Routine6: bsr.s sub_2061D4
        if (animator != null) {
            if (animator.step() == 2) routine = 8;
            mapFrame = animator.mapFrame();
        }
        if (!forPlayer && !onScreenBit()) setDestroyed(true);
    }

    /**
     * {@code sub_2061D4} (scddisasm/src/objects/r4/player_bubbles.asm:128-149): a countdown number's timer runs out while it
     * still shows a digit (anim below 7); it stops, is pinned to the screen and waits in routine $C.
     */
    private void pinNumber() {
        if (numberTimer == 0) return;
        numberTimer--;
        if (numberTimer != 0 || anim >= 7) return;
        numberTimer = 0x0F;
        yVel = 0;
        var camera = services().camera();
        screenX = x - (camera == null ? 0 : camera.getX());
        screenY = y - (camera == null ? 0 : camera.getY());
        routine = 0xC;
    }

    /**
     * {@code PlayerBubblesObject_0_RoutineC} (scddisasm/src/objects/r4/player_bubbles.asm:103-125): the pinned number is deleted
     * once the player has air again ({@code drown_timer} above $C); after $F passes it pops in place.
     */
    private void showNumber() {
        var water = services().gameService(com.openggf.game.soniccd.player.CdPlayerWater.class);
        if (water == null || water.drownTimer() > 0x0C) {
            setDestroyed(true);
            return;
        }
        var camera = services().camera();
        x = (screenX + (camera == null ? 0 : camera.getX())) & 0xFFFF;
        y = (screenY + (camera == null ? 0 : camera.getY())) & 0xFFFF;
        yFixed = y << 16;
        numberTimer--;
        if (numberTimer == 0) {
            routine = 0xE;
            anim += 7;
            if (animator != null) animator.anim = anim;
            pop();
            return;
        }
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
        // loc_2061B6 (objects/r4/player_bubbles.asm:114-121) tests oSprFlags bit 7, but sub_2061D4 (:128-149) left
        // the number an absolute screen position: sprite_flags $80 has no camera bit (:137) and x and the y word hold
        // the screen position (:138-146). DrawObject queues such an object without an on-screen test and DrawObjects
        // sets bit 7 for every queued object (DrawObject, Object Functions.asm:137-160; .DrawDone :346), so a
        // pinned number is never deleted for being off screen: R41C row 1459 pins one at screen x $150, past the right
        // edge, and native keeps it until its pop ends (row 1523).
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || animator == null) return;
        drawSheet(TtzObjectArt.BUBBLE, mapFrame, false, false);
    }
}
