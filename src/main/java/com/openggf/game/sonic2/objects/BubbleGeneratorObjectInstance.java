package com.openggf.game.sonic2.objects;

import com.openggf.level.objects.ObjectVisualFacing;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.Sonic2Rng;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RewindRecreateContext;
import com.openggf.level.objects.RewindRecreatable;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * ARZ Bubble Generator (Object 0x24) - Underwater bubble spawner.
 * Invisible stationary object positioned in water that periodically spawns
 * rising bubble objects. Large bubbles can be breathed by the player.
 * <p>
 * Based on loc_1F9C0 through loc_1FACE (s2.asm:45292-45384).
 * <p>
 * ROM State Machine:
 * The generator uses a complex state machine with multiple counters:
 * <ul>
 *   <li>objoff_32 (byte): Burst counter - decrements each burst, controls large bubble mode</li>
 *   <li>objoff_33 (byte): Reset value for burst counter (from subtype bits 0-6)</li>
 *   <li>objoff_34 (byte): Bubble count within current burst (counts down 0-5)</li>
 *   <li>objoff_36 (word): State flag - 0=ready for new burst, non-zero=spawning, bit 7=large bubble mode</li>
 *   <li>objoff_38 (word): Frame timer between spawns (0-31 for inter-bubble, +128-255 for inter-burst)</li>
 *   <li>objoff_3C (long): Pointer to current sequence in byte_1FAF0</li>
 * </ul>
 * <p>
 * Behavior:
 * <ol>
 *   <li>When objoff_36 == 0 (no active burst) and timer expires, start new burst</li>
 *   <li>Pick random burst size (0-5 bubbles) and one of 4 sequence tables</li>
 *   <li>Decrement burst counter; if underflows, enable large bubble mode (bit 7)</li>
 *   <li>Spawn bubbles one at a time with 0-31 frame delays between each</li>
 *   <li>In large bubble mode: 25% chance for breathable bubble, extra chance on last bubble</li>
 *   <li>After burst completes, add 128-255 frame delay before next burst</li>
 * </ol>
 */
public class BubbleGeneratorObjectInstance extends AbstractObjectInstance implements RewindRecreatable, ObjectVisualFacing.NonDirectional {
    private static final int ANIMATION_TABLE_ADDRESS = 0x1FBCC;
    private static final int BUBBLE_SEQUENCE_ADDRESS = 0x1FAF0;
    private int animFrameIndex;
    private int animTimer;

    // ROM objoff_32: Burst counter (decrements each burst, triggers large bubble mode on underflow)
    private int burstCounter;

    // ROM objoff_33: Reset value for burst counter (from subtype bits 0-6)
    private int burstCounterReset;

    // ROM objoff_34: Bubble count remaining in current burst (counts down)
    private int bubblesRemainingInBurst;

    // ROM objoff_36: State flag
    // Bit 0-6: 0=ready for new burst, 1=active burst
    // Bit 7: Large bubble mode enabled
    private int stateFlags;

    // ROM objoff_38: Frame timer (counts down)
    private int frameTimer;

    // ROM objoff_3C: Current sequence table offset (0, 4, 8, or 12)
    private int sequenceOffset;
    private int displayFrame;
    private boolean visible;
    private boolean romRenderOnScreen;

    /**
     * Whether the previous pass reached the ROM's {@code DisplaySprite} call at
     * {@code loc_1FACE} (docs/s2disasm/s2.asm:45368-45376). {@code BuildSprites}
     * only rewrites {@code render_flags} bit 7 for objects that queued
     * themselves, so a pass that returned without displaying leaves the flag
     * exactly as it was -- including {@code Obj24_Init}'s starting
     * {@code $84} (:45209), which is why a freshly placed generator counts down
     * on its own first pass whatever the camera is doing.
     */
    private boolean romDisplayedLastPass;

    // Bit 6 of objoff_36: Used to track if large bubble already spawned this burst
    private static final int FLAG_LARGE_SPAWNED = 0x40;
    private static final int FLAG_LARGE_MODE = 0x80;
    private static final int FLAG_ACTIVE_BURST = 0x01;

    public BubbleGeneratorObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);

        // ROM: andi.w #$7F,d0 / move.b d0,objoff_32(a0) / move.b d0,objoff_33(a0)
        int subtypeBits = spawn.subtype() & 0x7F;
        this.burstCounterReset = subtypeBits;
        this.burstCounter = subtypeBits;

        // Initial state: ready for new burst
        this.stateFlags = 0;
        this.frameTimer = 0;
        this.bubblesRemainingInBurst = 0;
        this.sequenceOffset = 0;
        this.romRenderOnScreen = true;
    }

    @Override
    public BubbleGeneratorObjectInstance recreateForRewind(RewindRecreateContext ctx) {
        return new BubbleGeneratorObjectInstance(ctx.spawn(), getName());
    }

    @Override
    public void update(int vIntRunCount, PlayableEntity playerEntity) {
        visible = false;

        // ROM Level_MainLoop (docs/s2disasm/s2.asm:5088-5111) runs RunObjects
        // (:5094) BEFORE JmpTo_DeformBgLayer (:5097) and calls BuildSprites
        // (:5110) after that deform. So the object pass at frame N and
        // BuildSprites at frame N-1 both see the same camera position, and the
        // render_flags bit 7 this pass reads is BuildSprites' verdict on that
        // camera -- not on the one the previous object pass saw. Evaluating the
        // bounds here rather than carrying the previous pass's evaluation is
        // what puts the flag in phase; carrying it left this generator one frame
        // behind the ROM every time the camera brought it back on screen.
        if (romDisplayedLastPass) {
            romRenderOnScreen = isWithinRenderSpriteBounds(
                    getOnScreenHalfWidth(), getOnScreenHalfHeight());
        }
        romDisplayedLastPass = false;
        boolean observedRomRenderOnScreen = romRenderOnScreen;

        // loc_1F9C0 gates water/render visibility ONLY while the burst is idle.
        // Every branch still reaches loc_1FACE; only timer/active paths animate.
        if ((stateFlags & FLAG_ACTIVE_BURST) == 0) {
            if (!isUnderwater() || !observedRomRenderOnScreen) {
                finishGeneratorPass(false);
                return;
            }
            frameTimer = (short) (frameTimer - 1);
            if (frameTimer < 0) startNewBurst();
        } else {
            frameTimer = (short) (frameTimer - 1);
            if (frameTimer < 0) spawnNextBubble();
        }
        finishGeneratorPass(true);
    }

    private boolean isUnderwater() {
        if (services().currentLevel() == null) return true;
        WaterSystem water = services().waterSystem();
        int zone = services().currentLevel().getZoneIndex(), act = services().currentAct();
        return !water.hasWater(zone, act) || spawn.y() > water.getWaterLevelY(zone, act);
    }

    private void finishGeneratorPass(boolean animate) {
        if (animate) animateGenerator();
        if (!isInRangeAt(spawn.x())) {
            setDestroyed(true);
            return;
        }
        // Above-water tail returns without queueing, preserving render_flags.
        if (isUnderwater()) {
            romDisplayedLastPass = true;
            visible = isWithinRenderSpriteBounds(getOnScreenHalfWidth(), getOnScreenHalfHeight());
        }
    }

    private void animateGenerator() {
        animTimer = (byte) (animTimer - 1);
        if (animTimer >= 0) return;
        int script = ANIMATION_TABLE_ADDRESS + romWord(ANIMATION_TABLE_ADDRESS + 6 * 2);
        animTimer = (byte) romByte(script);
        int command = romByte(script + 1 + animFrameIndex);
        if (command == 0xFF) {
            animFrameIndex = 0;
            command = romByte(script + 1);
        }
        if (command >= 0x80) throw new IllegalStateException("Unsupported Obj24 generator animation command " + command);
        displayFrame = command;
        animFrameIndex = (animFrameIndex + 1) & 0xFF;
    }

    private int romByte(int address) {
        try { return services().romReader().readU8(address); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    private int romWord(int address) {
        try { return services().romReader().readS16BE(address); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    @Override
    public boolean usesCustomOutOfRangeCheck() { return true; }

    @Override
    public boolean isCustomOutOfRange(int cameraX) {
        // Own loc_1FACE tail uses DeleteObject, not MarkObjGone/respawn-bit clear.
        return false;
    }

    /**
     * Starts a new bubble burst sequence.
     * ROM: loc_1F9E8 (s2.asm:45304-45322)
     */
    private void startNewBurst() {
        // ROM: jsr (RandomNumber).l / move.w d0,d1 / andi.w #7,d0 / cmpi.w #6,d0 / bhs.s loc_1F9E8
        var rng = services().rng();
        var burstChoice = Sonic2Rng.nextBubbleBurstChoice(rng);

        // ROM: move.b d0,objoff_34(a0)
        bubblesRemainingInBurst = burstChoice.bubbleCount();

        // ROM: andi.w #$C,d1 / lea (byte_1FAF0).l,a1 / adda.w d1,a1 / move.l a1,objoff_3C(a0)
        sequenceOffset = burstChoice.sequenceOffset(); // 0, 4, 8, or 12

        // ROM: subq.b #1,objoff_32(a0) / bpl.s BranchTo_loc_1FA2A
        burstCounter--;
        if (burstCounter < 0) {
            // ROM: move.b objoff_33(a0),objoff_32(a0) / bset #7,objoff_36(a0)
            burstCounter = burstCounterReset;
            stateFlags |= FLAG_LARGE_MODE;
        }

        // Mark burst as active and start spawning
        // ROM: move.w #1,objoff_36(a0) (preserves bit 7)
        stateFlags = (stateFlags & FLAG_LARGE_MODE) | FLAG_ACTIVE_BURST;

        // Spawn first bubble immediately
        spawnNextBubble();
    }

    /**
     * Spawns the next bubble in the current burst.
     * ROM: loc_1FA2A (s2.asm:45329-45369)
     */
    private void spawnNextBubble() {
        if (services().objectManager() == null) {
            return;
        }
        var rng = services().rng();

        // ROM: jsr (RandomNumber).l / andi.w #$1F,d0 / move.w d0,objoff_38(a0)
        // Set timer for next bubble (0-31 frames)
        frameTimer = rng.nextBits(0x1F);

        // AllocateObject precedes BOTH child-X and large-chance draws. The
        // ordinary spawnFreeChild supplier is eager, so reserve before executing it.
        services().objectManager().createDynamicObjectWithReservedSlot(() -> {
            int spawnX = spawn.x() + Sonic2Rng.nextMaskedOffset(rng, 0x0F, -8);
            int bubbleSubtype = romByte(BUBBLE_SEQUENCE_ADDRESS + sequenceOffset + bubblesRemainingInBurst);
            if ((stateFlags & FLAG_LARGE_MODE) != 0) {
                if (rng.nextBits(3) == 0) {
                    if ((stateFlags & FLAG_LARGE_SPAWNED) == 0) {
                        stateFlags |= FLAG_LARGE_SPAWNED;
                        bubbleSubtype = 2;
                    }
                }
                if (bubblesRemainingInBurst == 0 && (stateFlags & FLAG_LARGE_SPAWNED) == 0) {
                    stateFlags |= FLAG_LARGE_SPAWNED;
                    bubbleSubtype = 2;
                }
            }
            return new BubbleObjectInstance(spawnX, spawn.y(), bubbleSubtype, 0, true);
        });

        // ROM: Decrement bubble counter
        // subq.b #1,objoff_34(a0) / bpl.s loc_1FAC2
        bubblesRemainingInBurst--;

        if (bubblesRemainingInBurst < 0) {
            // Burst complete - add long delay before next burst
            // ROM: jsr (RandomNumber).l / andi.w #$7F,d0 / addi.w #$80,d0 / add.w d0,objoff_38(a0)
            frameTimer += rng.nextBits(0x7F) + 0x80;

            // ROM: clr.w objoff_36(a0)
            stateFlags = 0; // Clear all flags including large mode and large spawned
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!visible || isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.BUBBLES);
        if (renderer == null) {
            return;
        }

        renderer.drawFrameIndex(displayFrame, spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return 1; // ROM: move.b #1,priority(a0) at line 44742
    }
}
