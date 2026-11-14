package com.openggf.game.soniccd.objects.ttz;

import com.openggf.game.PlayableEntity;
import com.openggf.game.soniccd.objects.common.CdAnimator;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectVisualFacing;

import java.util.List;

/**
 * Bubble spawner, object $20 with subtype $80 or more ({@code BubbleSpawnerObject_0_RoutineA}, objects/r4/
 * bubble_spawner.asm:132-236): a vent below the water line that emits bursts of bubbles ({@link ObjBubble}, the
 * same ID with a small subtype). The low seven bits of the subtype are the number of bursts between big
 * bubbles ({@code oVar32}/{@code oVar33}). While underwater and on screen it counts {@code oVar38} down; at
 * -1 it starts a burst of 0-5 bubbles ({@code Random & 7}, redrawn while 6 or 7) whose kinds come from one of four
 * overlapping rows of {@code unk_20A80C} (:238-257); each bubble appears {@code Random & $1F} frames
 * apart at {@code x + (Random & $F) - 8}. Once a big-burst counter expires the next bubbles may become the
 * big kind (subtype 2) on a 1-in-4 draw, or on the burst's last bubble (:169-203). A vent above the water
 * line draws nothing and clears its on-screen bit, so it stops until it is below the line again; off screen
 * it deletes itself by chunk distance (:217-228).
 */
@com.openggf.level.objects.CameraRelativeDraw(com.openggf.level.objects.CameraRelativeDraw.Use.CULL_ONLY)
public final class ObjBubbleSpawner extends TtzObject implements ObjectVisualFacing.NonDirectional {
    /** unk_20A80C (objects/r4/bubble_spawner.asm:238-257). */
    private static final int[] KINDS = {0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 1, 0, 0, 1, 0};

    private int routine;
    private int mapFrame;
    private CdAnimator animator;
    /** oVar32/oVar33: bursts left before a big bubble, and the reload (subtype & $7F). */
    private int bigCountdown;
    private int bigReload;
    /** oVar36 bit 7: this burst may make big bubbles; bit 6: one big bubble made; low word: burst active. */
    private int burstWord;
    /** oVar34: bubbles left in the burst; oVar38: frames to the next; oVar3C: row offset. */
    private int left;
    private int delay;
    private int rowOffset;
    /** The on-screen bit cleared by {@code bclr #7,obj.sprite_flags} (loc_20A7F8, objects/r4/bubble_spawner.asm:229-236). */
    private boolean bitCleared;
    private boolean seenRender;
    /** loc_20A7F8 reached {@code jmp DrawObject} this frame (below the line), so the sprite pass sets bit 7 again. */
    private boolean queuedForDraw;

    public ObjBubbleSpawner(ObjectSpawn spawn) {
        super(spawn, "BubbleSpawner");
    }

    private boolean onScreenBit() {
        return !bitCleared && (!seenRender || renderFlagOnScreen());
    }

    @Override
    public void refreshPostCameraRenderState() {
        super.refreshPostCameraRenderState();
        seenRender = true;
        // The end-of-frame sprite pass sets bit 7 again for a queued sprite; renderFlagOnScreen() then decides it.
        if (queuedForDraw) bitCleared = false;
    }

    @Override public boolean usesCustomOutOfRangeCheck() { return true; }
    @Override public boolean isCustomOutOfRange(int cameraX) { return false; }

    @Override
    public void update(int vIntRunCount, PlayableEntity player) {
        if (routine == 0) init();
        run();
    }

    /** BubbleSpawnerObject_0_Routine0, spawner branch (objects/r4/bubble_spawner.asm:22-38). */
    private void init() {
        routine = 10;
        tileWord = TtzObjectArt.BUBBLE_TILE;
        width = 0x10;
        priority = 1;
        bigReload = spawn.subtype() & 0x7F;
        bigCountdown = bigReload;
        animator = TtzObjectArt.animator(artProvider(), "bubbleAnimations", 6);
    }

    /** BubbleSpawnerObject_0_RoutineA (objects/r4/bubble_spawner.asm:132-236). */
    private void run() {
        boolean below = waterY() < y;                               // cmp.w obj.y(a0),d0; bcc -> loc_20A7D4
        if (burstWord == 0) {
            if (below && onScreenBit()) {
                delay = (delay - 1) & 0xFFFF;
                if ((delay & 0x8000) == 0) { animate(); finish(); return; }
                burstWord = 1;
                int word;
                do { word = services().rng().nextWord(); } while ((word & 7) >= 6);   // loc_20A6E8: d1 keeps the last draw
                left = word & 7;
                rowOffset = word & 0xC;
                bigCountdown--;                                      // subq.b #1,obj.var_32(a0); bpl
                if (bigCountdown < 0) {
                    bigCountdown = bigReload;
                    burstWord |= 0x80;
                }
                burst();
                return;
            }
            finish();
            return;
        }
        delay = (delay - 1) & 0xFFFF;                               // loc_20A722
        if ((delay & 0x8000) == 0) { animate(); finish(); return; }
        burst();
    }

    /** loc_20A72A: one bubble of the burst. */
    private void burst() {
        delay = services().rng().nextWord() & 0x1F;
        if (onScreenBit()) {
            int dx = (services().rng().nextWord() & 0xF) - 8;
            int kind = KINDS[(rowOffset + left) % KINDS.length];
            ObjBubble bubble = null;
            int subtype = kind;
            if ((burstWord & 0x80) != 0) {                         // btst #7,obj.var_36
                if ((services().rng().nextWord() & 3) == 0) {
                    if ((burstWord & 0x40) == 0) { burstWord |= 0x40; subtype = 2; }
                } else if (left == 0 && (burstWord & 0x40) == 0) {  // loc_20A798
                    burstWord |= 0x40;
                    subtype = 2;
                }
            }
            bubble = new ObjBubble(childSpawn(x + dx, y, 0x20, subtype, 0), false, 0);
            spawnChild(bubble);                                    // bsr.w SpawnObject; bne.s loc_20A7AC
        }
        left--;                                                    // loc_20A7AC
        if (left >= 0) { animate(); finish(); return; }
        delay += (services().rng().nextWord() & 0x7F) + 0x80;
        burstWord = 0;
        animate();
        finish();
    }

    private void animate() {
        if (animator != null) {
            animator.step();
            mapFrame = animator.mapFrame();
        }
    }

    /** loc_20A7D4 and loc_20A7F8: chunk-range delete when off screen; draw only below the line. */
    private void finish() {
        if (!onScreenBit() && outsideChunkRange(x, services().camera() == null ? 0 : services().camera().getX())) {
            setDestroyed(true);
            return;
        }
        queuedForDraw = waterY() < y;                              // jmp DrawObject
        if (!queuedForDraw) bitCleared = true;                     // bclr #7,obj.sprite_flags
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (routine == 0 || animator == null || waterY() >= y) return;
        drawSheet(TtzObjectArt.BUBBLE, mapFrame, false, false);
    }
}
