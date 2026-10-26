package com.openggf.game.sonic2.specialstage;

import com.openggf.game.GameServices;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.resources.Sonic2PlcService;
import com.openggf.physics.TrigLookupTable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageConstants.*;
import static com.openggf.game.sonic2.specialstage.Sonic2SpecialStageSlotPool.Kind.*;

/**
 * Manages the intro sequence for Sonic 2 Special Stage.
 *
 * The intro sequence matches the original game's Obj5F (START banner) behavior:
 * 1. PRE_ROLL phase: Current speed/track/banner remain idle during ROM initialization
 * 2. ROM_STARTUP phase: The manager runs the two semantic track waits and first RunObjects pass
 * 3. FADE_FROM_WHITE phase: The ROM's 22 palette waits freeze the stage runtime
 * 4. DROP phase: Banner drops from y=-64 to y=72 (136 frames at 1 pixel/frame)
 * 5. WAIT1 phase: Pause while letters "explode" off banner (15 frames)
 * 6. WAIT2 phase: Display ring requirement message (31 frames)
 * 7. MESSAGE_FLYOUT: Ring message exists and SpecialStage_Started is set
 * 8. GAMEPLAY: Intro presentation has fully cleared
 *
 * Creating the "GET X RINGS" message enters MESSAGE_FLYOUT, sets
 * SpecialStage_Started, and displays it for 70 frames before flying off screen.
 *
 * During the intro sequence:
 * - Track animation runs during ROM_STARTUP, freezes during FADE_FROM_WHITE, then resumes
 * - Input remains control-locked until the later intro phases, but the ROM pre-start loop
 *   still copies logical controls and executes player/object routines before
 *   SpecialStage_Started is set (s2.asm:6674-6690)
 * - Banner and message sprites are rendered on top
 */
public class Sonic2SpecialStageIntro {
    private static final Logger LOGGER = Logger.getLogger(Sonic2SpecialStageIntro.class.getName());

    /**
     * The {@code Pal_FadeToWhite} window the special-stage entry code runs
     * before it masks interrupts and initializes the stage. The routine loads
     * {@code d4=$15} and uses {@code dbf}, so it executes exactly 22 V-ints
     * (s2.asm:3568-3578, called from the special-stage entry at s2.asm:6546).
     * Everything after it -- the VDP setup, DMA fills, RAM clears,
     * {@code ssLdComprsdData} and the entry PLC -- runs with interrupts masked
     * or blocking, so no later V-int exposes pre-initialization special-stage
     * state.
     */
    public static final int PRE_ROLL_FRAMES = 22;

    /**
     * ROM {@code Pal_FadeFromWhite} loads {@code d4=$15} and uses {@code dbf},
     * producing 22 waits (s2.asm:3460-3483, call at 6672).
     */
    public static final int FADE_FROM_WHITE_FRAMES = 22;

    /**
     * Intro sequence phases.
     */
    public enum Phase {
        /** Initial empty-player/zero-speed observation window */
        PRE_ROLL,
        /** Manager-owned drawing-index startup waits through the first RunObjects pass */
        ROM_STARTUP,
        /** Palette fade waits which do not tick track, players, objects, or banner */
        FADE_FROM_WHITE,
        /** Banner dropping from top of screen */
        DROP,
        /** Pause after banner lands, letters fly off */
        WAIT1,
        /** Ring requirement message displayed */
        WAIT2,
        /** Message flying off screen */
        MESSAGE_FLYOUT,
        /** Intro complete, normal gameplay */
        GAMEPLAY
    }

    /**
     * Represents a single letter/character in the message.
     * Each letter has its own position and can fly off independently during flyout.
     */
    public static class MessageLetter {
        public int x;
        public int y;
        public int tileOffset;
        public double flyoutAngle;
        public int flyoutSpeed;
        public boolean visible;
        private Sonic2SpecialStageSlotPool.Handle nativeHandle;
        private int nativeTimer = 0x46;
        private int nativeRoutine = 4;
        private int nativeAngle;

        public Sonic2SpecialStageSlotPool.Handle getNativeHandle() { return nativeHandle; }

        public MessageLetter(int x, int y, int tileOffset) {
            this.x = x;
            this.y = y;
            this.tileOffset = tileOffset;
            this.flyoutAngle = 0;
            this.flyoutSpeed = 8;
            this.visible = true;
        }
    }

    // Tile offsets for letters in SpecialMessages art (same as Sonic2SpecialStageRenderer)
    private static final int TILE_G = 0x04;
    private static final int TILE_E = 0x02;
    private static final int TILE_T = 0x14;
    private static final int TILE_R = 0x10;
    private static final int TILE_I = 0x08;
    private static final int TILE_N = 0x0C;
    private static final int TILE_S = 0x12;

    private Phase currentPhase = Phase.PRE_ROLL;
    private int phaseTimer = 0;
    private int frameCounter = 0;
    private boolean specialStageStarted;

    // Banner state
    private int bannerX = INTRO_BANNER_X;
    private int bannerY = INTRO_BANNER_START_Y;
    private boolean bannerVisible = true;

    // Message state
    private int messageX = 128;  // Center of H32 screen
    private int messageY = 108;  // Below center (y=$6C in original)
    private boolean messageVisible = false;
    private int ringRequirement = 0;

    // Letter flyout state (simplified - original spawns 7 child objects)
    private boolean lettersFlying = false;
    private int letterFlyoutProgress = 0;

    // Message letters for angle-based flyout (matching original Obj5A behavior)
    private final List<MessageLetter> messageLetters = new ArrayList<>();
    private boolean messageFlyoutInitialized = false;

    // Banner letters for START banner flyout (matching original Obj5F behavior)
    // From obj5F_a.asm: frames 2-8 are the individual pieces that fly off
    private final List<BannerLetter> bannerLetters = new ArrayList<>();
    private boolean bannerFlyoutInitialized = false;

    private Sonic2SpecialStageSlotPool nativeSlotPool;
    private Sonic2SpecialStageSlotPool.Handle nativeHud;
    private Sonic2SpecialStageSlotPool.Handle nativeBanner;
    private Sonic2SpecialStageSlotPool.Handle nativePlayerCounter;
    private Sonic2SpecialStageSlotPool.Handle nativeCounterChild;
    private final List<Sonic2SpecialStageSlotPool.Handle> nativeRingsToGo = new ArrayList<>();
    private boolean nativeTeamMode;
    private boolean nativeVersusMode;
    private boolean nativeFixedChildrenInitialized;
    private boolean nativeRingsToGoInitialized;
    private int nativeRingsRemaining;
    private boolean nativeRingsEnabled;
    private int nativeVInt;
    private int nativeRingsToGoBcd;
    private int nativeRingsToGoChildCount;
    private int nativeNoRingsToGoLifetime;
    private boolean nativeHideRingsToGo = true;
    private boolean nativeCheckpointMessageHidden;
    private final int[] nativeRingsToGoX = {0x68, 0x70, 0x78, 0x80, 0xC0, 0xB8, 0xB0, 0xA0, 0x98, 0x88};
    private final boolean[] nativeRingsToGoVisible = new boolean[11];
    private Sonic2SpecialStageRenderer.RingHudState nativeCounterInputs =
            new Sonic2SpecialStageRenderer.RingHudState(false, false, 0, 0, 0);
    private NativeCounterPayload nativePlayerCounterPayload;
    private NativeCounterPayload nativeTotalCounterPayload;

    /** One native multi-sprite child; several digits still belong to one SST owner. */
    public record NativeCounterDigit(int x, int y, int mappingFrame) { }

    /** Stored output of Obj87's actual turn, in native multi-sprite emission order. */
    public record NativeCounterPayload(Sonic2SpecialStageSlotPool.Handle handle,
                                       boolean displayed, List<NativeCounterDigit> digits) {
        public NativeCounterPayload {
            java.util.Objects.requireNonNull(handle);
            digits = List.copyOf(digits);
        }
    }


    /**
     * Represents a piece of the START banner that flies off during WAIT1.
     * Based on Obj5F frames 2-8 from obj5F_a.asm.
     */
    public static class BannerLetter {
        public int x;           // Current X position (relative to banner center)
        public int y;           // Current Y position (relative to banner center)
        public int frame;       // Mapping frame (2-8)
        public double flyoutAngle;
        public int flyoutSpeed;
        public boolean visible;
        private Sonic2SpecialStageSlotPool.Handle nativeHandle;
        private int nativeAngle;

        public Sonic2SpecialStageSlotPool.Handle getNativeHandle() { return nativeHandle; }

        public BannerLetter(int x, int y, int frame) {
            this.x = x;
            this.y = y;
            this.frame = frame;
            this.flyoutAngle = 0;
            this.flyoutSpeed = 8;
            this.visible = true;
        }
    }

    /**
     * Initializes the intro sequence for the specified stage.
     *
     * @param stageIndex Stage index (0-6)
     * @param ringRequirement Number of rings required for this stage/checkpoint
     */
    public void initialize(int stageIndex, int ringRequirement) {
        this.ringRequirement = ringRequirement;
        currentPhase = Phase.PRE_ROLL;
        phaseTimer = 0;
        frameCounter = 0;
        specialStageStarted = false;

        bannerX = INTRO_BANNER_X;
        bannerY = INTRO_BANNER_START_Y;
        bannerVisible = true;

        messageX = 128;
        messageY = 108;
        messageVisible = false;

        lettersFlying = false;
        letterFlyoutProgress = 0;

        messageLetters.clear();
        messageFlyoutInitialized = false;

        bannerLetters.clear();
        bannerFlyoutInitialized = false;

        nativeHud = null;
        nativeBanner = null;
        nativePlayerCounter = null;
        nativeCounterChild = null;
        nativeRingsToGo.clear();
        nativeFixedChildrenInitialized = false;
        nativeRingsToGoInitialized = false;
        nativeRingsRemaining = 0;
        nativeRingsEnabled = false;
        nativeVInt = 0;
        nativeRingsToGoBcd = 0;
        nativeRingsToGoChildCount = 0;
        nativeNoRingsToGoLifetime = 0;
        nativeHideRingsToGo = true;
        nativeCheckpointMessageHidden = false;
        nativeCounterInputs = new Sonic2SpecialStageRenderer.RingHudState(false, false, 0, 0, 0);
        nativePlayerCounterPayload = null;
        nativeTotalCounterPayload = null;
        int[] initialToGoX = {0x68, 0x70, 0x78, 0x80, 0xC0, 0xB8, 0xB0, 0xA0, 0x98, 0x88};
        System.arraycopy(initialToGoX, 0, nativeRingsToGoX, 0, nativeRingsToGoX.length);
        java.util.Arrays.fill(nativeRingsToGoVisible, false);

        LOGGER.info("Intro sequence initialized for stage " + (stageIndex + 1) +
                    ", ring requirement: " + ringRequirement);
    }

    /**
     * Updates the intro sequence for one frame.
     *
     * @return true if gameplay can begin (intro complete or in gameplay phase)
     */
    public boolean update() {
        frameCounter++;

        switch (currentPhase) {
            case PRE_ROLL:
                updatePreRollPhase();
                break;
            case ROM_STARTUP:
                // The manager owns the two semantic Vint_S2SS waits and advances
                // this phase only after their final RunObjects pass.
                break;
            case FADE_FROM_WHITE:
                updateFadeFromWhitePhase();
                break;
            case DROP:
                updateDropPhase();
                break;
            case WAIT1:
                updateWait1Phase();
                break;
            case WAIT2:
                updateWait2Phase();
                break;
            case MESSAGE_FLYOUT:
                updateMessageFlyoutPhase();
                break;
            case GAMEPLAY:
                // Intro complete, gameplay is active
                return true;
        }

        return currentPhase == Phase.GAMEPLAY;
    }

    private void updatePreRollPhase() {
        phaseTimer++;
        if (phaseTimer >= PRE_ROLL_FRAMES) {
            currentPhase = Phase.ROM_STARTUP;
            phaseTimer = 0;
        }
    }

    /**
     * Called by the manager immediately after the startup RunObjects pass at
     * s2.asm:6660-6663, before the CtrlDMA lag row and Pal_FadeFromWhite call.
     */
    void beginFadeFromWhite() {
        if (currentPhase != Phase.ROM_STARTUP) {
            throw new IllegalStateException("Fade from white can only follow ROM startup");
        }
        // Obj5F_Init falls through Obj5F_Main and applies its first +$100
        // ObjectMove step on the allocation pass before Pal_FadeFromWhite.
        // Preserve that object-local fall-through while the subsequent fade
        // freezes presentation/runtime state (s2.asm:9661-9684).
        bannerY += INTRO_BANNER_VELOCITY;
        currentPhase = Phase.FADE_FROM_WHITE;
        phaseTimer = 0;
    }

    private void updateFadeFromWhitePhase() {
        phaseTimer++;
        if (phaseTimer >= FADE_FROM_WHITE_FRAMES) {
            currentPhase = Phase.DROP;
            phaseTimer = 0;
        }
    }

    private void updateDropPhase() {
        // Move banner down at 1 pixel per frame
        bannerY += INTRO_BANNER_VELOCITY;
        phaseTimer++;

        // Check if banner has reached final position
        if (bannerY >= INTRO_BANNER_END_Y) {
            bannerY = INTRO_BANNER_END_Y;
            currentPhase = Phase.WAIT1;
            if (nativeSlotPool != null) {
                nativeSlotPool.setRoutine(nativeBanner, 4);
            }
            phaseTimer = 0;
            LOGGER.fine("Intro: DROP complete, entering WAIT1");
        }
    }

    // From s2.asm: objoff_2A = $F (15 frames) delay before letters fly off
    private static final int BANNER_LAND_DELAY_FRAMES = 15;

    private void updateWait1Phase() {
        phaseTimer++;

        // From s2.asm loc_71B4: wait 15 frames after landing before spawning flying letters
        // The banner stays intact during this delay
        if (phaseTimer < BANNER_LAND_DELAY_FRAMES) {
            // Still in delay period - banner is visible but not flying yet
            return;
        }

        // Initialize banner flyout after the delay
        if (!bannerFlyoutInitialized) {
            createBannerLetters();
            initializeBannerFlyout();
            bannerFlyoutInitialized = true;
            lettersFlying = true;
            letterFlyoutProgress = 0;
            // Obj5F starts its $1E countdown as soon as it creates the independent
            // banner-letter children. Their movement overlaps WAIT2; waiting for
            // them to leave the screen before starting the count adds a false
            // 21-pass delay to SpecialStage_Started (s2.asm:9710-9746).
            currentPhase = Phase.WAIT2;
            if (nativeSlotPool != null) {
                nativeSlotPool.setRoutine(nativeBanner, 0xA);
            }
            // This child-creation pass only stores $1E and returns. The first
            // decrement belongs to the following Obj5F dispatch.
            phaseTimer = 0;
            bannerVisible = true;
            messageVisible = false;
            // RunObjects scans ascending live slots. Obj5F's newly allocated
            // later-slot children therefore execute routine 6 once before this
            // allocation pass returns, while the parent $1E counter remains
            // untouched until its next dispatch (s2.asm:9707-9739).
            updateBannerLetterFlyout();
            LOGGER.fine("Intro: WAIT1 complete, entering overlapping Obj5F WAIT2");
        }
    }

    private void updateBannerLetterFlyout() {
        if (nativeSlotPool != null) {
            if (bannerFlyoutInitialized) {
                letterFlyoutProgress++;
            }
            return; // The manager's ascending native-slot pass owns child execution.
        }
        if (!bannerFlyoutInitialized) {
            return;
        }
        letterFlyoutProgress++;
        for (BannerLetter letter : bannerLetters) {
            if (!letter.visible) {
                continue;
            }
            letter.x += (int) (Math.cos(letter.flyoutAngle) * letter.flyoutSpeed);
            letter.y += (int) (Math.sin(letter.flyoutAngle) * letter.flyoutSpeed);

            int screenX = INTRO_BANNER_X + letter.x;
            int screenY = INTRO_BANNER_END_Y + letter.y;
            if (screenX < -32 || screenX > 288 || screenY < -32 || screenY > 256) {
                letter.visible = false;
            }
        }
    }

    /**
     * Creates the banner letter pieces for flyout animation.
     * From obj5F_a.asm frames 2-8:
     * - Frame 2: Left checkered piece at x=-$48
     * - Frame 3: "S" at x=-$28
     * - Frame 4: "T" at x=-$18
     * - Frame 5: "A" at x=-8
     * - Frame 6: "R" at x=8
     * - Frame 7: "T" at x=$18
     * - Frame 8: Right checkered piece at x=$28
     */
    private void createBannerLetters() {
        bannerLetters.clear();

        // Pieces are created at banner position, which is at y=$48 (72) when stopped
        // X positions are relative to banner center at x=$80 (128)
        // Frame 2-8 with their X offsets from obj5F_a.asm
        bannerLetters.add(new BannerLetter(-0x48, 0, 2));  // Left checkered
        bannerLetters.add(new BannerLetter(-0x28, 0, 3));  // S
        bannerLetters.add(new BannerLetter(-0x18, 0, 4));  // T
        bannerLetters.add(new BannerLetter(-0x08, 0, 5));  // A
        bannerLetters.add(new BannerLetter( 0x08, 0, 6));  // R
        bannerLetters.add(new BannerLetter( 0x18, 0, 7));  // T
        bannerLetters.add(new BannerLetter( 0x28, 0, 8));  // Right checkered
        if (nativeSlotPool != null) {
            // loc_71B4 (s2.asm:9698-9730) attempts seven global allocations.
            // Failures do not invent children; the parent's countdown still starts.
            for (BannerLetter letter : bannerLetters) {
                letter.nativeHandle = nativeSlotPool.allocate(BANNER_PIECE, 0x5F, 1,
                        letter.frame, nativeBanner);
                letter.visible = letter.nativeHandle != null;
                if (letter.visible) {
                    nativeSlotPool.setRoutine(letter.nativeHandle, 6);
                }
            }
            bannerLetters.removeIf(letter -> letter.nativeHandle == null);
        }
    }

    /**
     * Initialize flyout angles for banner letters.
     * From s2.asm loc_71B4: CalcAngle is called with d2=-$28 (-40), d1=x_position
     * This makes pieces fly upward (-40 Y offset) and outward based on X position.
     */
    private void initializeBannerFlyout() {
        for (BannerLetter letter : bannerLetters) {
            // From disassembly: d2 = -$28 (-40), d1 = piece x position
            // CalcAngle(d1=dx, d2=dy) gives the angle
            int dx = letter.x;
            int dy = -0x28;  // -40 - always fly upward
            letter.flyoutAngle = Math.atan2(dy, dx);
            letter.flyoutSpeed = 8;
            letter.nativeAngle = TrigLookupTable.calcAngle((short) dx, (short) dy);
        }
    }

    private void updateWait2Phase() {
        // Initial Obj5F countdown overlaps its independently moving children.
        // Checkpoint WAIT2 reuse has no banner children and shows its message
        // immediately through showRingRequirementMessage().
        if (!specialStageStarted) {
            updateBannerLetterFlyout();
        }
        phaseTimer++;

        // After WAIT2_FRAMES, transition to message flyout phase.
        // Original: counter starts at $1E (30), decrements with bpl (branch if >= 0).
        // Counts 30->29->...->0->-1, exiting at -1. That's 31 frames total.
        // Child creation leaves the parent timer at zero even though the newly
        // allocated later-slot children already moved once. The following 30
        // parent passes publish 1..30; pass 31 is the matching terminal dispatch.
        if (phaseTimer >= INTRO_WAIT2_FRAMES) {
            // The Bombs AddPLC is part of this one-shot handoff. A rejected
            // preflight must leave WAIT2 armed so the same owner retries next
            // pass instead of permanently losing the native cue.
            if (!queueBombPlc()) {
                return;
            }
            currentPhase = Phase.MESSAGE_FLYOUT;
            phaseTimer = 0;
            bannerVisible = false;
            messageVisible = true;
            // Obj5F creates the ring-requirement message, sets
            // SpecialStage_Started, and deletes itself in this same object pass
            // (s2.asm:9734-9746). Keep this latched through later checkpoint
            // message reuse of WAIT2.
            specialStageStarted = true;
            if (nativeSlotPool != null) {
                if (nativeVersusMode) {
                    int x = appendNativeWord("MOST", 0x44, 0x48, nativeBanner);
                    x = appendNativeWord("RINGS", x + 8, 0x48, nativeBanner);
                    appendNativeWord("WINS!", x + 8, 0x48, nativeBanner);
                } else {
                    createNativeRingRequirementMessage(nativeBanner);
                }
                nativeSlotPool.release(nativeBanner);
                messageFlyoutInitialized = true;
            }
            LOGGER.fine("Intro: WAIT2 complete, entering MESSAGE_FLYOUT");
        }
    }

    private boolean queueBombPlc() {
        try {
            Sonic2PlcService plcService = GameServices.module().getGameService(Sonic2PlcService.class);
            if (plcService != null) {
                plcService.transact(Sonic2PlcService.appendOperation(Sonic2Constants.PLC_SPECIAL_STAGE_BOMBS));
            }
            return true;
        } catch (Exception ignored) {
            // Focused presentation tests do not create a game-owned PLC service.
            return GameServices.module().getGameService(Sonic2PlcService.class) == null;
        }
    }

    private void updateMessageFlyoutPhase() {
        phaseTimer++;
        if (nativeSlotPool != null) {
            // Obj5A children execute at their actual slots, including on their
            // allocation pass; this presentation phase must not execute them twice.
            boolean anyLive = messageLetters.stream()
                    .anyMatch(letter -> nativeSlotPool.isLive(letter.nativeHandle));
            if (!anyLive) {
                messageVisible = false;
                currentPhase = Phase.GAMEPLAY;
            }
            return;
        }

        // Message displays for MESSAGE_DISPLAY_FRAMES then flies off
        if (phaseTimer >= MESSAGE_DISPLAY_FRAMES) {
            // Initialize flyout on first frame
            if (!messageFlyoutInitialized) {
                createMessageLetters();
                initializeFlyout();
                messageFlyoutInitialized = true;
            }

            // Move each letter based on its angle (matching Obj5A_TextFlyout behavior)
            boolean allGone = true;
            for (MessageLetter letter : messageLetters) {
                if (!letter.visible) continue;

                // Move letter based on angle
                letter.x += (int) (Math.cos(letter.flyoutAngle) * letter.flyoutSpeed);
                letter.y += (int) (Math.sin(letter.flyoutAngle) * letter.flyoutSpeed);

                // Check if off screen
                if (letter.x < 0 || letter.x > 256 || letter.y < 0 || letter.y > 224) {
                    letter.visible = false;
                } else {
                    allGone = false;
                }
            }

            if (allGone) {
                messageVisible = false;
                currentPhase = Phase.GAMEPLAY;
                LOGGER.info("Intro: Complete, gameplay started at frame " + frameCounter);
            }
        }
    }

    /**
     * Creates the message letters for "GET XX RINGS" with their positions.
     * Positions match the corrected digit positioning from s2.asm.
     */
    private void createMessageLetters() {
        messageLetters.clear();
        final int baseY = 0x6C;

        // "GET" at x=$54
        int x = 0x54;
        messageLetters.add(new MessageLetter(x, baseY, TILE_G));
        messageLetters.add(new MessageLetter(x + 8, baseY, TILE_E));
        messageLetters.add(new MessageLetter(x + 16, baseY, TILE_T));

        // Digits at corrected positions
        // From s2.asm: digits always start at x=$70 for tens/hundreds position
        int digitX;
        if (ringRequirement >= 100) {
            digitX = 0x70;  // 3 digits: hundreds at 0x70
        } else if (ringRequirement >= 10) {
            digitX = 0x70;  // 2 digits: tens at 0x70
        } else {
            digitX = 0x78;  // 1 digit: at 0x78
        }

        // For digits, we use a special marker (negative tileOffset) that the renderer interprets
        // Hundreds digit
        if (ringRequirement >= 100) {
            int hundreds = (ringRequirement / 100) % 10;
            messageLetters.add(new MessageLetter(digitX, baseY, -1 - hundreds));  // -1 to -10 for digits
            digitX += 8;
        }

        // Tens digit
        if (ringRequirement >= 10) {
            int tens = (ringRequirement / 10) % 10;
            messageLetters.add(new MessageLetter(digitX, baseY, -1 - tens));
            digitX += 8;
        }

        // Ones digit
        int ones = ringRequirement % 10;
        messageLetters.add(new MessageLetter(digitX, baseY, -1 - ones));

        // "RINGS" position: x=$84 for 2-digit, x=$8C for 3-digit
        int ringsX = (ringRequirement >= 100) ? 0x8C : 0x84;
        messageLetters.add(new MessageLetter(ringsX, baseY, TILE_R));
        messageLetters.add(new MessageLetter(ringsX + 8, baseY, TILE_I));
        messageLetters.add(new MessageLetter(ringsX + 16, baseY, TILE_N));
        messageLetters.add(new MessageLetter(ringsX + 24, baseY, TILE_G));
        messageLetters.add(new MessageLetter(ringsX + 32, baseY, TILE_S));
    }

    /**
     * Initialize flyout angles for all message letters.
     * From s2.asm Obj5A_TextFlyoutInit: angle is from center (0x80, 0x70) to letter,
     * so letters fly AWAY from center.
     */
    private void initializeFlyout() {
        for (MessageLetter letter : messageLetters) {
            // Calculate vector from center to letter position
            int dx = letter.x - 0x80;  // letter pos - center
            int dy = letter.y - 0x70;  // letter pos - center
            // Use atan2 to get angle (letters fly in direction away from center)
            letter.flyoutAngle = Math.atan2(dy, dx);
            letter.flyoutSpeed = 8;
        }
    }

    public void attachNativeSlotPool(Sonic2SpecialStageSlotPool pool) {
        nativeSlotPool = pool;
    }

    public boolean hasNativeSlotPool() { return nativeSlotPool != null; }

    /** Called when the ROM writes the fixed IDs, after its empty-RAM pre-roll. */
    public void initializeNativeFixedOwners(boolean teamMode, boolean versusMode) {
        if (nativeSlotPool == null || nativeHud != null) {
            return;
        }
        nativeTeamMode = teamMode;
        nativeVersusMode = versusMode;
        nativeHud = nativeSlotPool.claimFixed(2, HUD, 0x5E, 0, 0, null);
        nativeBanner = nativeSlotPool.claimFixed(3, BANNER, 0x5F, 1, 0, null);
        nativePlayerCounter = nativeSlotPool.claimFixed(4, PLAYER_COUNTER, 0x87, 0, 0, null);
        nativePlayerCounterPayload = new NativeCounterPayload(nativePlayerCounter, false, List.of());
    }

    /** Obj5A_CreateRingsToGoText runs before the first RunObjects (s2.asm:6660-6663). */
    public void initializeRingsToGoOwners() {
        if (nativeSlotPool == null || nativeRingsToGoInitialized) {
            return;
        }
        nativeRingsToGoInitialized = true;
        // fixBugs=0: sub3_y_pos=$0038 overwrites priority=$18, selecting bucket0.
        Sonic2SpecialStageSlotPool.Handle number = nativeSlotPool.allocate(
                RINGS_TO_GO_NUMBER, 0x5A, 0, 0, null);
        if (number == null) {
            return;
        }
        nativeSlotPool.setRoutine(number, 0xE);
        nativeRingsToGo.add(number);
        // Native allocation order: R I N G, then ! O G O T, then plural S.
        // Hidden/flashing owners remain occupied (s2.asm:71598-71804).
        int[] routines = {0x10, 0x10, 0x10, 0x10, 0x12, 0x12, 0x12, 0x12, 0x12, 0x14};
        for (int i = 0; i < routines.length; i++) {
            Sonic2SpecialStageSlotPool.Handle letter = nativeSlotPool.allocate(
                    RINGS_TO_GO_LETTER, 0x5A, 1, i, number);
            if (letter == null) {
                return; // The native creator returns on its first failed letter.
            }
            nativeSlotPool.setRoutine(letter, routines[i]);
            nativeRingsToGo.add(letter);
        }
    }

    /** Fixed slots 3 then 4 initialize before the ascending dynamic-owner scan. */
    public void initializeNativeFixedOwnerChildren() {
        if (nativeSlotPool == null || nativeFixedChildrenInitialized) {
            return;
        }
        nativeFixedChildrenInitialized = true;
        nativeSlotPool.setRoutine(nativeHud, 1);
        nativeSlotPool.setRoutine(nativeBanner, 2);
        nativeSlotPool.setRoutine(nativePlayerCounter, 2);
        if (nativeVersusMode) {
            // Obj5F_Init prints PLAYER VS PLAYER before Obj87_Init allocates its score.
            int x = 0x48;
            x = appendNativeWord("PLAYER", x, 0x48, nativeBanner);
            appendNativeLetter(x + 8, 0x48, 0x247, MESSAGE_LETTER, nativeBanner);
            appendNativeWord("PLAYER", x + 24, 0x48, nativeBanner);
        }
        if (nativeTeamMode || nativeVersusMode) {
            nativeCounterChild = nativeSlotPool.allocate(
                    nativeVersusMode ? VS_SCORE : TOTAL_COUNTER, 0x87, 0, 0, nativePlayerCounter);
            if (nativeCounterChild != null) {
                nativeSlotPool.setRoutine(nativeCounterChild, nativeVersusMode ? 6 : 4);
                if (!nativeVersusMode) {
                    nativeTotalCounterPayload = new NativeCounterPayload(nativeCounterChild, false, List.of());
                }
            }
        }
    }

    /** Executes precisely one child at the slot selected by the native RunObjects scan. */
    public boolean executeNativeChildSlot(Sonic2SpecialStageSlotPool.Entry entry) {
        if (nativeSlotPool == null || entry == null || !nativeSlotPool.isLive(entry.handle())) {
            return false;
        }
        if (entry.handle().equals(nativePlayerCounter)) {
            latchNativePlayerCounter();
            return true;
        }
        if (entry.handle().equals(nativeCounterChild) && entry.kind() == TOTAL_COUNTER) {
            latchNativeTotalCounter();
            return true;
        }
        for (BannerLetter letter : bannerLetters) {
            if (!entry.handle().equals(letter.nativeHandle)) {
                continue;
            }
            int[] offsets = {-0x48, -0x28, -0x18, -8, 8, 0x18, 0x28};
            letter.x += TrigLookupTable.cosHex(letter.nativeAngle) * letter.flyoutSpeed >> 8;
            letter.y += TrigLookupTable.sinHex(letter.nativeAngle) * letter.flyoutSpeed >> 8;
            int ownerX = INTRO_BANNER_X + letter.x - offsets[letter.frame - 2];
            int ownerY = INTRO_BANNER_END_Y + letter.y;
            // loc_710A (s2.asm:9635-9656) tests owner coordinates, not mapping extents.
            if (ownerX < 0 || ownerX > 0x100 || ownerY < 0) {
                letter.visible = false;
                nativeSlotPool.release(letter.nativeHandle);
            }
            return true;
        }
        for (MessageLetter letter : messageLetters) {
            if (!entry.handle().equals(letter.nativeHandle)) {
                continue;
            }
            if (letter.nativeRoutine == 4) {
                if (--letter.nativeTimer == 0) {
                    letter.nativeRoutine = 8;
                    letter.nativeAngle = TrigLookupTable.calcAngle(
                            (short) (letter.x - 0x80), (short) (letter.y - 0x70));
                    letter.flyoutAngle = letter.nativeAngle * Math.PI / 128.0;
                    nativeSlotPool.setRoutine(letter.nativeHandle, 8);
                }
            } else {
                letter.x += TrigLookupTable.cosHex(letter.nativeAngle) * letter.flyoutSpeed >> 8;
                letter.y += TrigLookupTable.sinHex(letter.nativeAngle) * letter.flyoutSpeed >> 8;
                if (letter.x < 0 || letter.x > 0x100 || letter.y < 0) {
                    letter.visible = false;
                    nativeSlotPool.release(letter.nativeHandle);
                }
            }
            return true;
        }
        int toGoIndex = nativeRingsToGo.indexOf(entry.handle());
        if (toGoIndex >= 0) {
            if (toGoIndex == 0) {
                nativeHideRingsToGo = !nativeRingsEnabled;
                if (!nativeHideRingsToGo) {
                    int remaining = nativeRingsRemaining;
                    if (remaining <= 0) {
                        nativeRingsToGoBcd = 0;
                        nativeHideRingsToGo = ++nativeNoRingsToGoLifetime >= 0xC;
                    } else {
                        nativeNoRingsToGoLifetime = 0;
                        int hundreds = 0;
                        // fixBugs=0 uses bgt after subtracting hundreds, including
                        // its 101+ BCD bug (Obj5A_RingsNeeded, s2.asm:71715-71739).
                        if (remaining >= 100) {
                            do {
                                hundreds += 0x100;
                                remaining = (short) (remaining - 100);
                            } while (remaining > 0);
                        }
                        int unsigned = remaining & 0xFFFF;
                        nativeRingsToGoBcd = hundreds | ((unsigned / 10 << 4) & 0xFF) | unsigned % 10;
                    }
                    // loc_3577A (s2.asm:71743-71762) always emits the units
                    // child, then emits tens/hundreds only while the shifted
                    // native BCD word remains nonzero. Trigger-hidden owners
                    // return above without replacing their captured child count.
                    nativeRingsToGoChildCount = 1;
                    int remainingDigits = nativeRingsToGoBcd >>> 4;
                    if (remainingDigits != 0) {
                        nativeRingsToGoChildCount++;
                        if ((remainingDigits >>> 4) != 0) {
                            nativeRingsToGoChildCount++;
                        }
                    }
                }
            } else if (toGoIndex >= 5 && toGoIndex <= 9) {
                int[] offsets = {0xC0, 0xB8, 0xB0, 0xA0, 0x98};
                nativeRingsToGoX[toGoIndex - 1] = offsets[toGoIndex - 5]
                        - (nativeRingsToGoBcd < 2 ? 8 : 0);
            }
            nativeRingsToGoVisible[toGoIndex] = nativeRingsEnabled && !nativeHideRingsToGo
                    && !nativeCheckpointMessageHidden && (nativeVInt & 7) < 6
                    && (toGoIndex != 10 || nativeRingsToGoBcd >= 2);
            return true;
        }
        return entry.handle().equals(nativeCounterChild);
    }

    private void createNativeRingRequirementMessage(Sonic2SpecialStageSlotPool.Handle source) {
        appendNativeWord("GET", 0x54, 0x6C, source);
        int count = ringRequirement >= 100 ? 3 : 2;
        int value = ringRequirement;
        int x = count == 3 ? 0x80 : 0x78;
        // Obj5A_CreateRingReqMessage emits units first, not screen-left order.
        for (int i = 0; i < count; i++, x -= 8, value /= 10) {
            appendNativeLetter(x, 0x6C, -1 - value % 10, MESSAGE_DIGIT, source);
        }
        x = appendNativeWord("RINGS", count == 3 ? 0x8C : 0x84, 0x6C, source);
        appendNativeWord("!", x, 0x6C, source);
    }

    private int appendNativeWord(String word, int x, int y,
                                 Sonic2SpecialStageSlotPool.Handle source) {
        for (char c : word.toCharArray()) {
            if (!appendNativeLetter(x, y, nativeLetterTile(c), MESSAGE_LETTER, source)) {
                return x;
            }
            x += 8;
        }
        return x;
    }

    private boolean appendNativeLetter(int x, int y, int tile,
                                       Sonic2SpecialStageSlotPool.Kind kind,
                                       Sonic2SpecialStageSlotPool.Handle source) {
        Sonic2SpecialStageSlotPool.Handle handle = nativeSlotPool.allocateAfterCurrent(
                source, kind, 0x5A, 1, messageLetters.size(), source);
        if (handle == null) {
            return false;
        }
        MessageLetter letter = new MessageLetter(x, y, tile);
        letter.nativeHandle = handle;
        nativeSlotPool.setRoutine(handle, 4);
        messageLetters.add(letter);
        return true;
    }

    private static int nativeLetterTile(char c) {
        return switch (c) {
            case 'G' -> TILE_G; case 'E' -> TILE_E; case 'T' -> TILE_T;
            case 'R' -> TILE_R; case 'I' -> TILE_I; case 'N' -> TILE_N;
            case 'S' -> TILE_S; case '!' -> 0x18; case 'P' -> 0x23D;
            case 'L' -> 0x0A; case 'A' -> 0x23F; case 'Y' -> 0x241;
            case 'M' -> 0x243; case 'W' -> 0x245; case 'O' -> 0x6A;
            default -> throw new IllegalArgumentException("Unsupported native UI character " + c);
        };
    }

    /** Obj5A_Handshake creates the next requirement from its live hand slot. */
    public void showNativeRingRequirementMessage(int requirement,
                                                Sonic2SpecialStageSlotPool.Handle source) {
        ringRequirement = requirement;
        createNativeRingRequirementMessage(source);
        currentPhase = Phase.MESSAGE_FLYOUT;
        phaseTimer = 0;
        bannerVisible = false;
        messageVisible = true;
        messageFlyoutInitialized = true;
    }

    public Sonic2SpecialStageSlotPool.Handle getNativeHudHandle() { return nativeHud; }
    public Sonic2SpecialStageSlotPool.Handle getNativeBannerHandle() { return nativeBanner; }
    public Sonic2SpecialStageSlotPool.Handle getNativePlayerCounterHandle() { return nativePlayerCounter; }
    public Sonic2SpecialStageSlotPool.Handle getNativeCounterChildHandle() { return nativeCounterChild; }
    public List<Sonic2SpecialStageSlotPool.Handle> getNativeRingsToGoHandles() {
        return List.copyOf(nativeRingsToGo);
    }

    public void setNativeRingsToGoDisplay(int remaining, boolean enabled, int vInt) {
        nativeRingsRemaining = remaining;
        nativeRingsEnabled = enabled;
        nativeVInt = vInt;
    }

    public void setNativeCheckpointMessageHidden(boolean hidden) {
        nativeCheckpointMessageHidden = hidden;
    }

    /** Obj5A_RingsMessageInit at the FC owner's own slot (s2.asm:71556-71564). */
    public void executeNativeRingsMessageInit() {
        nativeNoRingsToGoLifetime = 0;
        nativeHideRingsToGo = false;
        // SS_NoCheckpointMsg_flag belongs to streaming / the rainbow result;
        // Obj5A_RingsMessageInit does not clear it.
        nativeRingsEnabled = true;
    }

    public int getNativeRingsToGoBcd() { return nativeRingsToGoBcd; }
    public int getNativeRingsToGoChildCount() { return nativeRingsToGoChildCount; }

    /** Supplies current gameplay values; only an executing counter owner consumes them. */
    public void setNativeCounterInputs(Sonic2SpecialStageRenderer.RingHudState state) {
        nativeCounterInputs = java.util.Objects.requireNonNull(state);
    }

    /** Returns captured digits, never a conversion of current gameplay counts. */
    public NativeCounterPayload getNativeCounterPayload(Sonic2SpecialStageSlotPool.Handle handle) {
        if (nativeSlotPool == null || !nativeSlotPool.isLive(handle)) {
            return null;
        }
        if (handle.equals(nativePlayerCounter)) {
            return nativePlayerCounterPayload;
        }
        return handle.equals(nativeCounterChild) ? nativeTotalCounterPayload : null;
    }

    public List<NativeCounterPayload> getNativeCounterPayloads() {
        List<NativeCounterPayload> payloads = new ArrayList<>(2);
        NativeCounterPayload player = getNativeCounterPayload(nativePlayerCounter);
        NativeCounterPayload total = getNativeCounterPayload(nativeCounterChild);
        if (player != null) {
            payloads.add(player);
        }
        if (total != null) {
            payloads.add(total);
        }
        return List.copyOf(payloads);
    }

    private void latchNativePlayerCounter() {
        List<NativeCounterDigit> digits = new ArrayList<>(6);
        // loc_7480 / loc_74EA (s2.asm:9851-9929) copy the cached digit bytes:
        // one owner contains Sonic first, then Tails, with no draw-time reread.
        if (nativeTeamMode) {
            appendNativePlayerDigits(digits, nativeCounterInputs.sonicRings(), 0x48);
            appendNativePlayerDigits(digits, nativeCounterInputs.tailsRings(), 0xE0);
        } else {
            int count = nativeCounterInputs.tailsActive() && !nativeCounterInputs.sonicActive()
                    ? nativeCounterInputs.tailsRings() : nativeCounterInputs.sonicRings();
            appendNativePlayerDigits(digits, count, 0x9C);
        }
        nativePlayerCounterPayload = new NativeCounterPayload(nativePlayerCounter, true, digits);
    }

    private static void appendNativePlayerDigits(List<NativeCounterDigit> digits, int count, int x) {
        int hundreds = (count / 100) & 0xFF;
        int tens = count / 10 % 10;
        int units = count % 10;
        if (hundreds != 0) {
            digits.add(new NativeCounterDigit(x, 0x20, hundreds));
            x += 8;
        }
        if (hundreds != 0 || tens != 0) {
            digits.add(new NativeCounterDigit(x, 0x20, tens));
            x += 8;
        }
        digits.add(new NativeCounterDigit(x, 0x20, units));
    }

    private void latchNativeTotalCounter() {
        // loc_753E (s2.asm:9933-10017) adds the two players' cached digits,
        // carrying once at each decimal column. A hundreds value of 10 is
        // retained as mapping frame 10; neither clamping nor modulo-1000 is native.
        int sonic = nativeCounterInputs.sonicRings();
        int tails = nativeCounterInputs.tailsRings();
        int units = sonic % 10 + tails % 10;
        int tens = sonic / 10 % 10 + tails / 10 % 10;
        int hundreds = ((sonic / 100 & 0xFF) + (tails / 100 & 0xFF)) & 0xFF;
        if (units >= 10) {
            units -= 10;
            tens++;
        }
        if (tens >= 10) {
            tens -= 10;
            hundreds = (hundreds + 1) & 0xFF;
        }
        int count = hundreds != 0 ? 3 : tens != 0 ? 2 : 1;
        int x = count == 3 ? 0x78 : count == 2 ? 0x7C : 0x80;
        List<NativeCounterDigit> digits = new ArrayList<>(count);
        if (count == 3) {
            digits.add(new NativeCounterDigit(x, 0x20, hundreds));
            x += 8;
        }
        if (count >= 2) {
            digits.add(new NativeCounterDigit(x, 0x20, tens));
            x += 8;
        }
        digits.add(new NativeCounterDigit(x, 0x20, units));
        nativeTotalCounterPayload = new NativeCounterPayload(nativeCounterChild, true, digits);
    }

    /** Value-only render state for every live folded owner, including hidden UI. */
    public List<Sonic2SpecialStageSlotPool.RenderState> nativeRenderStates() {
        if (nativeSlotPool == null) {
            return List.of();
        }
        List<Sonic2SpecialStageSlotPool.RenderState> states = new ArrayList<>();
        // Obj5E_Init and Obj87_Init end in RTS without DisplaySprite; only
        // their subsequent dispatch displays them. The total-counter child and
        // Obj5F banner do enqueue in the startup pass retained through the fade.
        boolean fixedDisplays = nativeFixedChildrenInitialized
                && currentPhase != Phase.PRE_ROLL && currentPhase != Phase.ROM_STARTUP
                && currentPhase != Phase.FADE_FROM_WHITE;
        addNativeRenderState(states, nativeHud, 0, 0, -1, fixedDisplays,
                Sonic2SpecialStageSlotPool.ArtRole.HUD);
        addNativeRenderState(states, nativePlayerCounter, 0, 0, -1,
                nativePlayerCounterPayload != null && nativePlayerCounterPayload.displayed(),
                Sonic2SpecialStageSlotPool.ArtRole.NUMBER);
        addNativeRenderState(states, nativeCounterChild, 0, 0, -1,
                nativeTotalCounterPayload != null && nativeTotalCounterPayload.displayed(),
                Sonic2SpecialStageSlotPool.ArtRole.NUMBER);
        addNativeRenderState(states, nativeBanner, bannerX, bannerY, 0,
                nativeFixedChildrenInitialized && bannerVisible && !bannerFlyoutInitialized,
                Sonic2SpecialStageSlotPool.ArtRole.START_BANNER);
        for (BannerLetter letter : bannerLetters) {
            int[] offsets = {-0x48, -0x28, -0x18, -8, 8, 0x18, 0x28};
            addNativeRenderState(states, letter.nativeHandle,
                    INTRO_BANNER_X + letter.x - offsets[letter.frame - 2], INTRO_BANNER_END_Y + letter.y,
                    letter.frame, letter.visible, Sonic2SpecialStageSlotPool.ArtRole.START_BANNER);
        }
        for (MessageLetter letter : messageLetters) {
            addNativeRenderState(states, letter.nativeHandle, letter.x, letter.y,
                    letter.tileOffset < 0 ? -1 - letter.tileOffset : nativeMessageFrame(letter.tileOffset),
                    letter.visible, letter.tileOffset < 0 ? Sonic2SpecialStageSlotPool.ArtRole.NUMBER
                            : Sonic2SpecialStageSlotPool.ArtRole.MESSAGES);
        }
        int[] frames = {3, 4, 5, 0, 17, 8, 0, 8, 2, 6};
        for (int i = 0; i < nativeRingsToGo.size(); i++) {
            addNativeRenderState(states, nativeRingsToGo.get(i), i == 0 ? 0 : nativeRingsToGoX[i - 1],
                    i == 0 ? 0 : 0x38, i == 0 ? -1 : frames[i - 1], nativeRingsToGoVisible[i],
                    i == 0 ? Sonic2SpecialStageSlotPool.ArtRole.NUMBER : Sonic2SpecialStageSlotPool.ArtRole.MESSAGES);
        }
        states.sort(java.util.Comparator.comparingInt(state -> state.identity().slot()));
        return List.copyOf(states);
    }

    private void addNativeRenderState(List<Sonic2SpecialStageSlotPool.RenderState> states,
                                      Sonic2SpecialStageSlotPool.Handle handle,
                                      int x, int y, int frame, boolean visible,
                                      Sonic2SpecialStageSlotPool.ArtRole artRole) {
        Sonic2SpecialStageSlotPool.Entry entry = nativeSlotPool.entry(handle);
        if (entry != null) {
            states.add(new Sonic2SpecialStageSlotPool.RenderState(entry, x, y, frame,
                    false, false, visible, artRole));
        }
    }

    static int nativeMessageFrame(int tile) {
        return switch (tile) {
            case 4 -> 0; case 2 -> 1; case 0x14 -> 2; case 0x10 -> 3;
            case 8 -> 4; case 0xC -> 5; case 0x12 -> 6; case 0 -> 7;
            case 0x6A -> 8; case 0xA -> 9; case 0x16 -> 10; case 6 -> 11;
            case 0x23D -> 12; case 0x23F -> 13; case 0x241 -> 14;
            case 0x243 -> 15; case 0x245 -> 16; case 0x18 -> 17;
            case 0xE -> 18; case 0x247 -> 19;
            default -> throw new IllegalArgumentException("Unknown message mapping tile " + tile);
        };
    }

    Sonic2SpecialStageSnapshot.IntroSnapshot captureRewindSnapshot() {
        ArrayList<Sonic2SpecialStageSnapshot.IntroMessageLetterSnapshot> message = new ArrayList<>();
        for (MessageLetter letter : messageLetters) {
            message.add(new Sonic2SpecialStageSnapshot.IntroMessageLetterSnapshot(
                    letter.x,
                    letter.y,
                    letter.tileOffset,
                    letter.flyoutAngle,
                    letter.flyoutSpeed,
                    letter.visible,
                    letter.nativeHandle,
                    letter.nativeTimer,
                    letter.nativeRoutine,
                    letter.nativeAngle));
        }

        ArrayList<Sonic2SpecialStageSnapshot.IntroBannerLetterSnapshot> banner = new ArrayList<>();
        for (BannerLetter letter : bannerLetters) {
            banner.add(new Sonic2SpecialStageSnapshot.IntroBannerLetterSnapshot(
                    letter.x,
                    letter.y,
                    letter.frame,
                    letter.flyoutAngle,
                    letter.flyoutSpeed,
                    letter.visible,
                    letter.nativeHandle,
                    letter.nativeAngle));
        }

        return new Sonic2SpecialStageSnapshot.IntroSnapshot(
                currentPhase,
                phaseTimer,
                frameCounter,
                specialStageStarted,
                bannerX,
                bannerY,
                bannerVisible,
                messageX,
                messageY,
                messageVisible,
                ringRequirement,
                lettersFlying,
                letterFlyoutProgress,
                messageFlyoutInitialized,
                bannerFlyoutInitialized,
                message,
                banner,
                nativeHud,
                nativeBanner,
                nativePlayerCounter,
                nativeCounterChild,
                nativeRingsToGo,
                nativeTeamMode,
                nativeVersusMode,
                nativeFixedChildrenInitialized,
                nativeRingsToGoInitialized,
                nativeRingsRemaining,
                nativeRingsEnabled,
                nativeVInt,
                nativeRingsToGoBcd,
                nativeRingsToGoChildCount,
                nativeNoRingsToGoLifetime,
                nativeHideRingsToGo,
                nativeCheckpointMessageHidden,
                java.util.Arrays.stream(nativeRingsToGoX).boxed().toList(),
                java.util.stream.IntStream.range(0, nativeRingsToGoVisible.length)
                        .mapToObj(i -> nativeRingsToGoVisible[i]).toList(),
                nativeCounterInputs,
                nativePlayerCounterPayload,
                nativeTotalCounterPayload);
    }

    void restoreRewindSnapshot(Sonic2SpecialStageSnapshot.IntroSnapshot snapshot) {
        currentPhase = snapshot.currentPhase();
        phaseTimer = snapshot.phaseTimer();
        frameCounter = snapshot.frameCounter();
        specialStageStarted = snapshot.specialStageStarted();
        bannerX = snapshot.bannerX();
        bannerY = snapshot.bannerY();
        bannerVisible = snapshot.bannerVisible();
        messageX = snapshot.messageX();
        messageY = snapshot.messageY();
        messageVisible = snapshot.messageVisible();
        ringRequirement = snapshot.ringRequirement();
        lettersFlying = snapshot.lettersFlying();
        letterFlyoutProgress = snapshot.letterFlyoutProgress();
        messageFlyoutInitialized = snapshot.messageFlyoutInitialized();
        bannerFlyoutInitialized = snapshot.bannerFlyoutInitialized();
        nativeHud = snapshot.nativeHud();
        nativeBanner = snapshot.nativeBanner();
        nativePlayerCounter = snapshot.nativePlayerCounter();
        nativeCounterChild = snapshot.nativeCounterChild();
        nativeRingsToGo.clear();
        nativeRingsToGo.addAll(snapshot.nativeRingsToGo());
        nativeTeamMode = snapshot.nativeTeamMode();
        nativeVersusMode = snapshot.nativeVersusMode();
        nativeFixedChildrenInitialized = snapshot.nativeFixedChildrenInitialized();
        nativeRingsToGoInitialized = snapshot.nativeRingsToGoInitialized();
        nativeCounterInputs = snapshot.nativeCounterInputs();
        nativePlayerCounterPayload = snapshot.nativePlayerCounterPayload();
        nativeTotalCounterPayload = snapshot.nativeTotalCounterPayload();
        nativeRingsRemaining = snapshot.nativeRingsRemaining();
        nativeRingsEnabled = snapshot.nativeRingsEnabled();
        nativeVInt = snapshot.nativeVInt();
        nativeRingsToGoBcd = snapshot.nativeRingsToGoBcd();
        nativeRingsToGoChildCount = snapshot.nativeRingsToGoChildCount();
        nativeNoRingsToGoLifetime = snapshot.nativeNoRingsToGoLifetime();
        nativeHideRingsToGo = snapshot.nativeHideRingsToGo();
        nativeCheckpointMessageHidden = snapshot.nativeCheckpointMessageHidden();
        for (int i = 0; i < nativeRingsToGoX.length; i++) {
            nativeRingsToGoX[i] = snapshot.nativeRingsToGoX().get(i);
        }
        for (int i = 0; i < nativeRingsToGoVisible.length; i++) {
            nativeRingsToGoVisible[i] = snapshot.nativeRingsToGoVisible().get(i);
        }

        messageLetters.clear();
        for (Sonic2SpecialStageSnapshot.IntroMessageLetterSnapshot letter : snapshot.messageLetters()) {
            MessageLetter restored = new MessageLetter(letter.x(), letter.y(), letter.tileOffset());
            restored.flyoutAngle = letter.flyoutAngle();
            restored.flyoutSpeed = letter.flyoutSpeed();
            restored.visible = letter.visible();
            restored.nativeHandle = letter.nativeHandle();
            restored.nativeTimer = letter.nativeTimer();
            restored.nativeRoutine = letter.nativeRoutine();
            restored.nativeAngle = letter.nativeAngle();
            messageLetters.add(restored);
        }

        bannerLetters.clear();
        for (Sonic2SpecialStageSnapshot.IntroBannerLetterSnapshot letter : snapshot.bannerLetters()) {
            BannerLetter restored = new BannerLetter(letter.x(), letter.y(), letter.frame());
            restored.flyoutAngle = letter.flyoutAngle();
            restored.flyoutSpeed = letter.flyoutSpeed();
            restored.visible = letter.visible();
            restored.nativeHandle = letter.nativeHandle();
            restored.nativeAngle = letter.nativeAngle();
            bannerLetters.add(restored);
        }
    }

    /**
     * Gets the current intro phase.
     */
    public Phase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Returns whether the ROM's empty-player/zero-speed pre-roll is active.
     */
    public boolean isPreRollActive() {
        return currentPhase == Phase.PRE_ROLL;
    }

    /**
     * Checks if the intro sequence is complete.
     */
    public boolean isComplete() {
        return currentPhase == Phase.GAMEPLAY;
    }

    /**
     * Returns the ROM's latched {@code SpecialStage_Started} state. This turns
     * on when Obj5F creates the initial ring message at the WAIT2 boundary,
     * before the message flyout presentation completes (s2.asm:9734-9746).
     */
    public boolean isSpecialStageStarted() {
        return specialStageStarted;
    }

    /** Native Obj5F state immediately before its terminal WAIT2 object pass. */
    boolean isTerminalPreStartPassPending() {
        return currentPhase == Phase.WAIT2
                && phaseTimer == INTRO_WAIT2_FRAMES - 1
                && !specialStageStarted;
    }

    /** First recurring pass after the fade, whose full redraw misses its VBlank row. */
    boolean isSlowFirstPostFadePassPending() {
        return currentPhase == Phase.DROP
                && phaseTimer == 0
                && !specialStageStarted;
    }

    /**
     * Gets the total frame count since intro started.
     */
    public int getFrameCounter() {
        return frameCounter;
    }

    // ========== Rendering state getters ==========

    /**
     * Checks if the START banner should be rendered.
     */
    public boolean isBannerVisible() {
        return bannerVisible;
    }

    /**
     * Gets the banner X position (center of banner).
     */
    public int getBannerX() {
        return bannerX;
    }

    /**
     * Gets the banner Y position (center of banner).
     */
    public int getBannerY() {
        return bannerY;
    }

    /**
     * Checks if the ring requirement message should be rendered.
     */
    public boolean isMessageVisible() {
        return messageVisible;
    }

    /**
     * Gets the message X position.
     */
    public int getMessageX() {
        return messageX;
    }

    /**
     * Gets the message Y position.
     */
    public int getMessageY() {
        return messageY;
    }

    /**
     * Gets the ring requirement to display.
     */
    public int getRingRequirement() {
        return ringRequirement;
    }

    /**
     * Checks if letters are currently flying off the banner.
     */
    public boolean areLettersFlying() {
        return lettersFlying && (currentPhase == Phase.WAIT1
                || (currentPhase == Phase.WAIT2 && !specialStageStarted));
    }

    /**
     * Gets the letter flyout progress (0 = just started, WAIT1_FRAMES = complete).
     */
    public int getLetterFlyoutProgress() {
        return letterFlyoutProgress;
    }

    /**
     * Checks if the message is currently in the flyout animation phase.
     * When true, the renderer should use per-letter positions instead of static rendering.
     */
    public boolean isInFlyoutPhase() {
        return currentPhase == Phase.MESSAGE_FLYOUT &&
               phaseTimer >= MESSAGE_DISPLAY_FRAMES &&
               messageFlyoutInitialized;
    }

    /**
     * Gets the message letters for rendering during flyout.
     * Only valid when isInFlyoutPhase() returns true.
     */
    public List<MessageLetter> getMessageLetters() {
        return messageLetters;
    }

    /**
     * Checks if the banner is currently in its independent child-flight phase.
     * When true, the renderer should render individual banner pieces instead of the full banner.
     * Returns false during the 15-frame delay after landing and after Obj5F's
     * terminal pass creates the initial ring-requirement message.
     */
    public boolean isBannerInFlyoutPhase() {
        boolean childFlightPhase = currentPhase == Phase.WAIT1
                || (currentPhase == Phase.WAIT2 && !specialStageStarted);
        return childFlightPhase && bannerFlyoutInitialized;
    }

    /**
     * Gets the banner letters for rendering during flyout.
     * Only valid when isBannerInFlyoutPhase() returns true.
     */
    public List<BannerLetter> getBannerLetters() {
        return bannerLetters;
    }

    /**
     * Shows just the "GET XX RINGS" message without the full intro sequence.
     * Used after passing a checkpoint to display the next ring requirement.
     *
     * @param ringRequirement Number of rings required for the next checkpoint
     */
    public void showRingRequirementMessage(int ringRequirement) {
        this.ringRequirement = ringRequirement;

        // Skip directly to WAIT2 (message display phase)
        currentPhase = Phase.WAIT2;
        phaseTimer = 0;

        bannerVisible = false;  // No banner
        messageVisible = true;  // Show the message
        messageX = 128;
        messageY = 108;

        lettersFlying = false;
        letterFlyoutProgress = 0;

        // Reset flyout state for new message
        messageLetters.clear();
        messageFlyoutInitialized = false;

        LOGGER.info("Showing ring requirement message: GET " + ringRequirement + " RINGS");
    }
}
