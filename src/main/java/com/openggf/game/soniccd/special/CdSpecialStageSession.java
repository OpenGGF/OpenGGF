package com.openggf.game.soniccd.special;

import java.io.IOException;
import java.util.Objects;

/**
 * The special stage's frame loop: the main-CPU program's {@code MainLoop} (Main.asm:174-231)
 * pacing the sub-CPU updates of {@link CdSpecialStage}.
 *
 * <p><b>Pacing.</b> Each loop pass starts one stage update ({@code UpdateStage}, Main.asm:
 * 2137-2156) and waits three V-blanks (routines 0/2, 1/3 and 4; Main.asm:179-194, 207-222)
 * before collecting the update's output ({@code GetSubCPUData}, scddisasm/src/Special Stage/Main.asm:3207-3233). The game
 * therefore runs at one update per three frames (20 per second), the reason the timer counts
 * 60 / 3 updates per second ({@code UpdateTimer}, scddisasm/src/Special Stage/Sub.asm:1724-1727). The wait for the graphics
 * operation ({@code .WaitGfx}, Sub.asm:305-307) completes inside those three frames.
 *
 * <p><b>Input.</b> The controller is read in the first V-blank of each pass
 * ({@code VInt_CopyStage1_1}/{@code 2_1}, scddisasm/src/Special Stage/Main.asm:921-944), and the next pass's update reads
 * that value ({@code ObjSonic}, Sonic/Main.asm:55-59). Native SS1 confirms it: across 285
 * updates of the stage-select demo, update n read demo byte n - 2 (update 0 runs before the loop,
 * Main.asm:146). In temporary non-secret mode the read is replaced by demo input
 * ({@code RunDemo}, scddisasm/src/Special Stage/Main.asm:3638-3655) until Start is pressed.
 *
 * <p><b>Floor display.</b> The floor rendered in update n is collected at the end of pass n,
 * copied into the hidden buffer during pass n + 1 and shown from the first V-blank of pass n + 2
 * ({@code ShowStageBuf1}/{@code 2}, scddisasm/src/Special Stage/Main.asm:1022-1062).
 */
public final class CdSpecialStageSession {
    /** Frames per update: the three V-blanks of one {@code MainLoop} pass. */
    public static final int FRAMES_PER_UPDATE = 3;
    /** {@code FadeFromWhite}: three channels of eight steps, one V-blank each (scddisasm/src/Special Stage/Main.asm:2493-2525). */
    static final int FADE_IN_FRAMES = 1 + 3 * 8;

    private final CdSpecialStage stage;
    private final byte[] demo;
    private int demoCursor;
    private boolean demoActive;
    private int latchedHold;
    private int latchedTap;
    private int previousHold;
    private int frame;
    private int phase;
    private int fadeFrames;
    private boolean ended;
    private final byte[] collected = new byte[CdStampFloor.IMAGE_WIDTH * CdStampFloor.IMAGE_HEIGHT];
    private final byte[][] buffers = new byte[2][CdStampFloor.IMAGE_WIDTH * CdStampFloor.IMAGE_HEIGHT];
    // Buffer 1 is index 0, buffer 2 index 1. Odd passes (V-blank routines 0, 1) show buffer 2
    // and fill buffer 1; even passes (routines 2, 3) the reverse (Main.asm:909-953).
    private int shownBuffer;
    private int pass;
    private int fillBuffer;

    public CdSpecialStageSession(CdSpecialStageAssets assets, CdSpecialStageEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry");
        this.stage = new CdSpecialStage(assets, entry.stageId(), entry.flags(), entry.timeStones());
        this.demo = entry.flags().demoInput() ? assets.demo(entry.stageId() == 5 ? 5 : 0) : null;
        // Start (Main.asm:146-170): one update renders the first floor, then the fade from white.
        this.screen = new CdSpecialStageScreen(assets, entry.stageId(), entry.flags());
        stage.update(0, 0);
        stage.finishUpdate(0);
        collect();
        fadeFrames = FADE_IN_FRAMES;
    }

    private final CdSpecialStageScreen screen;
    private java.util.List<CdSpecialSprites.Entry> collectedSprites = java.util.List.of();
    private final byte[] collectedArt = new byte[0x300];
    private final java.util.List<CdSpecialSound> pendingSounds = new java.util.ArrayList<>();

    /** FM requests collected since the last call ({@code PlaySubFMSounds}, scddisasm/src/Special Stage/Main.asm:2686-2696). */
    public java.util.List<CdSpecialSound> takeSounds() {
        java.util.List<CdSpecialSound> out = java.util.List.copyOf(pendingSounds);
        pendingSounds.clear();
        return out;
    }

    /** {@code GetSubCPUData} (scddisasm/src/Special Stage/Main.asm:3207-3233): the image, sprites, scroll flags and Sonic's art. */
    private void collect() {
        System.arraycopy(stage.image(), 0, collected, 0, collected.length);
        screen.scrollFlags = stage.scrollFlags;
        stage.scrollFlags = 0;
        collectedSprites = java.util.List.copyOf(stage.spriteEntries());
        pendingSounds.addAll(stage.sounds());
        System.arraycopy(stage.sonicArt, 0, collectedArt, 0, collectedArt.length);
    }

    /**
     * A V-blank running routine 0 or 2 ({@code VInt_CopyStage1_1}/{@code 2_1}, scddisasm/src/Special Stage/Main.asm:921-944):
     * palette upload, show one buffer, scroll copy, the first half of the floor into the other.
     */
    private void vblankFirst(int show) {
        screen.vblankPalette();
        shownBuffer = show;
        fillBuffer = show ^ 1;
        screen.showBuffer(show);
        screen.copyHScroll();
        System.arraycopy(collected, 0, buffers[fillBuffer], 0, collected.length / 2);
        screen.copyFloor(collected, fillBuffer, 0);
    }

    /**
     * Advances one 60 Hz frame with the pad state of this frame (held and newly pressed,
     * Genesis bit order: Start, A, C, B, Right, Left, Down, Up from bit 7).
     */
    public void frame(int padHold) {
        if (ended) return;
        frame++;
        if (fadeFrames > 0) {
            // PrepFadeFromWhite then a VSync, then FadeFromWhite's 24 steps (Main.asm:162-166, 2493-2565);
            // each step's V-blank runs routine 0 (vintRoutine is cleared, Main.asm:92-97).
            int step = FADE_IN_FRAMES - fadeFrames;
            if (step == 0) screen.prepFadeFromWhite();
            else screen.fadeFromWhiteStep(step - 1);
            vblankFirst(1);
            readController(padHold);
            screen.display = true; // bset #6,ipxVDPReg1+1 after the first VSync (Main.asm:163-165)
            if (--fadeFrames == 0) {
                demoActive = demo != null; // Main.asm:168
                passStart();
            }
            return;
        }
        // A frame is one V-blank followed by the main-loop code that runs until the next VSync.
        switch (phase) {
            case 0 -> {
                vblankFirst((pass & 1) != 0 ? 1 : 0);    // routine 0/2 (Main.asm:921-944)
                readController(padHold);
                // The sub-CPU's end checks see this read; the main CPU acts after the pass's waits.
                if (stage.finishUpdate(latchedHold)) endAfterPass = true;
                // DrawRingCount, DrawUFOCount, LoadTimeStonePal, BGScroll2 (Main.asm:184-189).
                screen.drawRingsAndUfos(stage.rings(), stage.ufoCount());
                screen.loadTimeStonePalette(stage.ufoCount());
                screen.bgScroll(2, endAfterPass);
            }
            case 1 -> {
                // Routine 1/3 (Main.asm:930-953), then DrawTimer and BGScroll3 (Main.asm:191-192).
                screen.vblankPalette();
                screen.copyHScrollSects();
                System.arraycopy(collected, collected.length / 2, buffers[fillBuffer], collected.length / 2,
                        collected.length / 2);
                screen.copyFloor(collected, fillBuffer, 1);
                screen.copySprites(collectedSprites, collectedArt); // CopySprites (scddisasm/src/Special Stage/Main.asm:1013-1016)
                screen.paletteCycle();
                screen.drawTimer(stage.timer());
                screen.bgScroll(3, endAfterPass);
            }
            default -> {
                screen.vblankPalette();                // routine 4 (Main.asm:957-959)
                screen.copyHScrollSects();
                if (endAfterPass) {
                    ended = true;
                    return;
                }
                collect();                             // GetSubCPUData (Main.asm:196-201)
                passStart();
                return;
            }
        }
        phase++;
    }

    /** {@code MainLoop}'s head (Main.asm:176-180): start the sub-CPU update, scroll section 1. */
    private void passStart() {
        stage.update(latchedHold, latchedTap);
        screen.bgScroll(1, false);
        pass++;
        phase = 0;
    }

    private boolean endAfterPass;

    /** {@code ReadController} (scddisasm/src/Special Stage/Main.asm:3562-3608) with {@code RunDemo} (scddisasm/src/Special Stage/Main.asm:3638-3655). */
    private void readController(int pad) {
        int value = pad & 0xFF;
        if (stage.flags().demoInput()) {
            if ((value & 0x80) != 0) {
                demoActive = false;
            } else if (demoActive) {
                value = demoCursor < demo.length ? demo[demoCursor] & 0xFF : 0;
                demoCursor++;
            }
        }
        latchedTap = value & ~previousHold & 0xFF;
        latchedHold = value;
        previousHold = value;
    }

    public CdSpecialStage stage() { return stage; }
    public CdSpecialStageScreen screen() { return screen; }
    public boolean ended() { return ended; }
    public int frameCount() { return frame; }
    public int phase() { return phase; }
    public boolean fadingIn() { return fadeFrames > 0; }
    /** The floor image on screen this frame (row-major 256 x 96 colour indices, palette line 1). */
    public byte[] displayedFloor() { return buffers[shownBuffer]; }

    /** The exit contract for the program-flow owner. */
    public CdSpecialStageResult result() {
        if (!ended) throw new IllegalStateException("The special stage has not ended");
        return CdSpecialStageResult.of(stage);
    }
}
