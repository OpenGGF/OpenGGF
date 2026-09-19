package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.game.sonic3k.runtime.SszZoneRuntimeState;
import com.openggf.level.scroll.compose.DeformationPlan;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.level.scroll.compose.ScrollValueTable;
import com.openggf.level.scroll.M68KMath;

import java.util.Arrays;

import static com.openggf.level.scroll.M68KMath.VISIBLE_LINES;
import static com.openggf.level.scroll.M68KMath.negWord;

/**
 * Sky Sanctuary act 1 background scroll: {@code SSZ1_BackgroundInit},
 * {@code SSZ1_BackgroundEvent} and the two parameter subroutines {@code sub_579F0}
 * (plain sky) and {@code sub_57A60} (cloud band), sonic3k.asm:116385-116708.
 *
 * <p><b>Two background modes, selected by the wrapped camera Y.</b> Both
 * {@code SSZ1_BackgroundInit} and the event's routine 0 and 8 test
 * {@code Camera_Y_pos &amp; Screen_Y_wrap_value} ({@code $FFF}, {@code LevelSetup}
 * sonic3k.asm:102205) against {@code $800} and {@code $F00}:
 * <ul>
 *   <li>inside {@code [$800,$F00)} the sanctuary sits in the cloud band and
 *       {@code sub_57A60} writes thirty per-band scroll words plus a halved,
 *       wrapped background Y;</li>
 *   <li>outside it the sky is plain and {@code sub_579F0} moves the background 1:1
 *       with the camera at a fixed offset.</li>
 * </ul>
 *
 * <p><b>The four-state machine is the plane redraw, not the scroll.</b>
 * {@code Events_routine_bg} 0 and 8 are the two steady modes; 4 and {@code $C} stage
 * {@code Draw_PlaneVertBottomUp} / {@code Draw_PlaneVertSingleBottomUp} while the
 * nametable is refilled. Routine 0 saves the outgoing {@code Camera_X/Y_pos_BG_copy}
 * into {@code Events_bg+$0C}/{@code +$0E} and routine 4 keeps rendering from them until
 * the redraw finishes, so the visible switch to the cloud bands lags the mode test.
 * The engine draws the background from the whole layout instead of a 512-pixel
 * nametable, so each staged redraw completes in one frame here; the recorded
 * consequence is that the cloud bands appear one frame after the ROM would start
 * filling them, and the return to plain sky is likewise two frames rather than the
 * ROM's redraw length.
 *
 * <p><b>The {@code $1800} toggle is not a mode.</b> {@code sub_579F0} keeps
 * {@code Events_bg+$10} as a latch: below {@code Camera_X_pos $1800} the background is
 * ({@code Camera_X+$28}, {@code Camera_Y+$160+_unkEE9C}); at or above it is
 * ({@code Camera_X}, {@code Camera_Y+$180}). Crossing the latch re-runs the subroutine
 * and re-rounds {@code Camera_X_pos_BG_rounded}; it starts no plane redraw.
 *
 * <p><b>{@code Apply_FGVScroll} is inert during ordinary play.</b> Routine 0's plain
 * path ends in {@code Apply_FGVScroll} with {@code word_577B2} and
 * {@code HScroll_table+$0BE}, which fills {@code Vscroll_buffer}. That buffer only
 * reaches VSRAM through {@code SpecialVInt_VScrollCopy}, and {@code Special_V_int_routine}
 * is non-zero in Sky Sanctuary only during the Death Egg launch
 * ({@code SSZ1_ScreenEvent}, sonic3k.asm:115961 and 116060). Ordinary act-1 frames
 * therefore keep the plain full-screen vertical scroll, and the per-column path belongs
 * to the launch slice.
 *
 * <p><b>Camera words.</b> The ROM reads {@code Camera_X_pos} for the {@code $1800} latch and
 * {@code Camera_X_pos_copy}/{@code Camera_Y_pos_copy} for the arithmetic; {@code ParallaxManager}
 * hands this handler {@code Camera_X_pos}/{@code Camera_Y_pos}. The two differ only by the screen
 * shake {@code SSZ1_ScreenEvent} adds to the Y copy, which Sky Sanctuary raises during the Death
 * Egg launch, so the launch slice owns closing that gap.
 *
 * <p>Act 2 uses its separate {@code SSZ2_BackgroundInit/Event}: a fixed {@code $5E}
 * background origin, the {@code sub_58D3E} scroll-word fan, {@code SSZ2_BGDeformArray},
 * and the twenty VSRAM column words assembled at {@code loc_5904A}.
 */
public class SwScrlSsz extends SwScrlS3kDefault {

    /** {@code SSZ1_BGDeformArray} (sonic3k.asm:117580): 29 finite bands then the remainder. */
    static final int[] SSZ1_BG_DEFORM = {
            0x1D0, 0x10, 0x08, 0x18, 0x10, 0x10, 0x08, 0x28, 0x10, 0x08,
            0x08, 0x28, 0x08, 0x20, 0x08, 0x08, 0x08, 0x10, 0x18, 0x20,
            0x40, 0x20, 0x18, 0x10, 0x08, 0x08, 0x08, 0x20, 0x08, 0x7FFF
    };

    /** {@code ApplyDeformation} is entered with {@code a5 = HScroll_table+$004}. */
    static final int DEFORM_TABLE_START_INDEX = 2;

    /** {@code sub_57A60} writes {@code HScroll_table+$004} through {@code +$03E}. */
    static final int SCROLL_WORD_COUNT = 32;

    /** {@code and.w (Screen_Y_wrap_value).w,d0} with {@code LevelSetup}'s {@code $FFF}. */
    static final int SCREEN_Y_WRAP_MASK = 0xFFF;
    /** {@code cmpi.w #$800,d0} / {@code cmpi.w #$F00,d0}: the cloud band. */
    static final int CLOUD_BAND_MIN_Y = 0x800;
    static final int CLOUD_BAND_MAX_Y = 0xF00;

    /** {@code sub_579F0}: {@code cmpi.w #$1800,(Camera_X_pos).w}. */
    static final int FAR_FRAMING_CAMERA_X = 0x1800;
    /** {@code loc_57A12}: the near framing's background offsets. */
    static final int NEAR_X_OFFSET = 0x28;
    static final int NEAR_Y_OFFSET = 0x160;
    /** {@code loc_57A4C}: the far framing drops the X offset and deepens the Y one. */
    static final int FAR_Y_OFFSET = 0x180;

    /** {@code sub_57A60}: {@code addi.l #$500,-4(a1)} on the {@code HScroll_table} longword. */
    static final int CLOUD_DRIFT_PER_FRAME = 0x500;

    /** {@code Events_routine_bg} stages. */
    static final int BG_PLAIN = 0;
    static final int BG_ENTERING_CLOUDS = 4;
    static final int BG_CLOUDS = 8;
    static final int BG_LEAVING_CLOUDS = 0xC;

    /** {@code Events_bg+$0C}/{@code +$0E}: the background copies frozen across the redraw. */
    static final int EV_SAVED_BG_X = 0x0C;
    static final int EV_SAVED_BG_Y = 0x0E;

    private static final DeformationPlan.ScrollValueTransform NEGATE_WORD = value -> negWord(value);

    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private final ScrollValueTable hScrollTable = ScrollValueTable.ofLength(SCROLL_WORD_COUNT);
    private final ScrollValueTable act2ScrollTable = ScrollValueTable.ofLength(40);

    static final int[] SSZ2_BG_DEFORM = {
            0x120, 8, 8, 4, 4, 8, 8, 0x18, 0x10, 0x10, 0x7FFF
    };
    static final int[] SSZ2_FG_DEFORM = {
            0x380, 0x10, 0x10, 0x18, 0x10, 0x08, 0x10, 0x10, 0x08, 0x38,
            0x10, 0x10, 0x28, 0x08, 0x20, 0x08, 0x08, 0x08, 0x08, 0x08,
            0x18, 0x20, 0x30, 0x08, 0x08, 0x10, 0x08, 0x28, 0x10, 0x10,
            0x18, 0x10, 0x08, 0x10, 0x10, 0x08, 0x7FFF
    };
    private static final int ACT2_BG_X = 0x5E;
    private static final int ACT2_BG_Y_OFFSET = 0x320;
    private static final int ACT2_COLUMN_COUNT = 20;

    private int currentBgPeriodWidth = 512;
    /** The VDP plane B width the cloud path fills whole ({@code moveq #$20,d6}). */
    private static final int VDP_PLANE_WIDTH_PX = 512;
    /** {@code move.w #$1C00,d1}: background layout column 56, four chunks wide. */
    private static final int CLOUD_WINDOW_LAYOUT_X = 0x1C00;
    /** Whether the frame this handler last advanced rendered the cloud bands. */
    private boolean lastFrameUsedCloudBands;

    @Override
    public void update(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter, int actId) {
        SszZoneRuntimeState state = state();
        if (state == null || !state.screenInitApplied()) {
            super.update(horizScrollBuf, cameraX, cameraY, frameCounter, actId);
            return;
        }
        if (actId != 0) {
            composeActTwo(state, horizScrollBuf, cameraX, cameraY,
                    frameCounter != state.backgroundScrollFrame());
            state.setBackgroundScrollFrame(frameCounter);
            currentBgPeriodWidth = SwScrlHpz.requiredBgPeriodWidth(horizScrollBuf, viewportWidth());
            return;
        }
        // SSZ1_BackgroundEvent is a once-per-frame routine with persistent state (the drift
        // accumulator and the redraw machine), but the engine can compose the same frame more
        // than once: a rewind restore re-renders the frame it restored. The frame the event
        // last ran for therefore lives in the runtime state, so a restore rewinds it too and
        // the re-render pays no second $500 of drift.
        boolean advance = frameCounter != state.backgroundScrollFrame();
        state.setBackgroundScrollFrame(frameCounter);
        composeFrame(state, horizScrollBuf, cameraX, cameraY, advance);
        currentBgPeriodWidth = SwScrlHpz.requiredBgPeriodWidth(horizScrollBuf, viewportWidth());
    }

    /** Cold route of {@code sub_58D3E}/{@code sub_58FBC}, while Events_routine_fg is below $C. */
    void composeActTwo(SszZoneRuntimeState state, int[] output,
                       int cameraX, int cameraY, boolean advance) {
        resetScrollTracking();
        composer.reset();
        if (!state.backgroundInitApplied()) {
            state.markBackgroundInitApplied();
            state.setCloudDrift(0);
        }
        int accumulator = state.cloudDrift();
        if (advance) state.setCloudDrift(accumulator + 0x1000);

        act2ScrollTable.clear();
        int cameraRate = (((short) cameraX) << 16) >> 5;
        int step = cameraRate;
        int value = (cameraRate >> 1) + accumulator;
        setAct2Words(value, 0x00, 0x04, 0x08, 0x10, 0x36, 0x3A, 0x3E, 0x46);
        value += step;
        setAct2Words(value, 0x06, 0x0A, 0x0E, 0x14, 0x3C, 0x40, 0x44);
        value += step;
        setAct2Words(value, 0x02, 0x0C, 0x16, 0x34, 0x38, 0x42);
        value += step;
        setAct2Words(value, 0x12);
        for (int i = 0; i < 9; i++) {
            value += step;
            setAct2Words(value, 0x18 + i * 2);
        }

        int bgY = (short) (cameraY - ACT2_BG_Y_OFFSET - state.cloudOscillator() + 8);
        state.setBackgroundCameraX(ACT2_BG_X);
        state.setBackgroundCameraY(bgY);
        short fg = negWord(cameraX);
        composer.setVscrollFactorBG((short) bgY);
        DeformationPlan.applyTableBands(composer, (short) bgY, fg, act2ScrollTable,
                SSZ2_BG_DEFORM, DEFORM_TABLE_START_INDEX, NEGATE_WORD);
        applyActTwoColumns(state, bgY);
        composer.copyPackedScrollWordsTo(output);
        if (state.foregroundRoutine() < 0x0C) {
            applyActTwoForegroundBands(output, cameraY);
        }
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    /** {@code ApplyFGDeformation} over {@code word_58C80} and {@code HScroll_table+$004}. */
    private void applyActTwoForegroundBands(int[] output, int cameraY) {
        int y = (short) cameraY;
        int band = 0;
        int valueIndex = DEFORM_TABLE_START_INDEX;
        int height = SSZ2_FG_DEFORM[band++];
        while (y - height >= 0) {
            y -= height;
            valueIndex++;
            height = SSZ2_FG_DEFORM[Math.min(band++, SSZ2_FG_DEFORM.length - 1)];
        }
        int remaining = height - y;
        int line = 0;
        while (line < output.length) {
            short fg = negWord(act2ScrollTable.get(valueIndex));
            int count = Math.min(remaining, output.length - line);
            for (int i = 0; i < count; i++, line++) {
                output[line] = M68KMath.packScrollWords(fg, M68KMath.unpackBG(output[line]));
            }
            valueIndex++;
            height = SSZ2_FG_DEFORM[Math.min(band++, SSZ2_FG_DEFORM.length - 1)];
            remaining = height;
        }
    }

    private void setAct2Words(int fixed, int... byteOffsets) {
        short word = (short) (fixed >> 16);
        for (int offset : byteOffsets) {
            act2ScrollTable.set(DEFORM_TABLE_START_INDEX + (offset >> 1), word);
        }
    }

    /** {@code loc_58EA2}, {@code loc_58EE8}, then {@code loc_58F1E/loc_5904A}. */
    private void applyActTwoColumns(SszZoneRuntimeState state, int bgY) {
        short[] columns = composer.writablePerColumnVScrollBG(ACT2_COLUMN_COUNT);
        Arrays.fill(columns, (short) bgY);
        int amplitude = (short) state.eventsBgWord(0);
        int[] wave = new int[144];
        fillSymmetricWave(wave, 0, 64, amplitude, 7);
        fillSymmetricWave(wave, 128, 8, amplitude, 3);
        int rounded = (ACT2_BG_X + 0x0F) & ~0x0F;
        int source = (rounded >> 3) & ~1;
        for (int i = 0; i < columns.length; i++) {
            columns[i] = (short) (bgY + 8 + wave[Math.floorMod(source + i, wave.length)]);
        }
    }

    private static void fillSymmetricWave(int[] target, int start, int half, int amplitude,
                                          int shift) {
        int magnitude = Math.abs((short) amplitude);
        boolean negativeFirst = amplitude < 0;
        for (int i = 0; i < half; i++) {
            int sample = (magnitude * (i + 1)) >> shift;
            target[start + half - 1 - i] = negativeFirst ? sample : -sample;
            target[start + half + i] = negativeFirst ? -sample : sample;
        }
    }

    @Override
    public short[] getPerColumnVScrollBG() {
        return composer.getPerColumnVScrollBG();
    }

    /**
     * One {@code SSZ1_BackgroundEvent} frame against an explicit state, so the machine and the
     * deformation can be exercised without a live runtime. Returns whether the frame rendered the
     * cloud bands.
     */
    boolean composeFrame(SszZoneRuntimeState state, int[] horizScrollBuf, int cameraX, int cameraY) {
        return composeFrame(state, horizScrollBuf, cameraX, cameraY, true);
    }

    private boolean composeFrame(SszZoneRuntimeState state, int[] horizScrollBuf,
                                 int cameraX, int cameraY, boolean advance) {
        resetScrollTracking();
        composer.reset();

        if (state.foregroundRoutine() >= 8) {
            // SSZ1_ScreenEvent stage 8 owns Plane B through sub_574DC. The launch image
            // is a single scroll plane, not either of SSZ1_BackgroundEvent's sky modes.
            short fg = negWord(cameraX);
            short bgY = (short) state.backgroundCameraY();
            composer.setVscrollFactorBG(bgY);
            composer.fillPackedScrollWords(0, VISIBLE_LINES, fg,
                    negWord(state.backgroundCameraX()));
            composer.copyPackedScrollWordsTo(horizScrollBuf);
            vscrollFactorBG = composer.getVscrollFactorBG();
            minScrollOffset = composer.getMinScrollOffset();
            maxScrollOffset = composer.getMaxScrollOffset();
            return false;
        }

        boolean clouds;
        if (advance) {
            if (!state.backgroundInitApplied()) {
                state.markBackgroundInitApplied();
                backgroundInit(state, cameraX, cameraY);
            }
            clouds = backgroundEvent(state, cameraX, cameraY);
            lastFrameUsedCloudBands = clouds;
        } else {
            clouds = lastFrameUsedCloudBands;
        }

        short fgScroll = negWord(cameraX);
        short bgY = (short) state.backgroundCameraY();
        composer.setVscrollFactorBG(bgY);
        if (clouds) {
            DeformationPlan.applyTableBands(composer, bgY, fgScroll, hScrollTable,
                    SSZ1_BG_DEFORM, DEFORM_TABLE_START_INDEX, NEGATE_WORD);
        } else {
            // PlainDeformation: one background word for every line.
            composer.fillPackedScrollWords(0, VISIBLE_LINES, fgScroll,
                    negWord(state.backgroundCameraX()));
        }

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
        return clouds;
    }

    /**
     * {@code SSZ1_BackgroundInit} (sonic3k.asm:116385-116428) minus its object allocation, which
     * {@link com.openggf.game.sonic3k.events.Sonic3kSSZEvents} owns: {@code clr.w (Events_bg+$10)}
     * then the same cloud-band test the event routines use, entering either routine 0 with
     * {@code sub_579F0} or routine 8 with {@code sub_57A60}.
     */
    static void backgroundInit(SszZoneRuntimeState state, int cameraX, int cameraY) {
        state.setBackgroundFarFraming(0);
        if (inCloudBand(cameraY)) {
            state.setBackgroundRoutine(BG_CLOUDS);
        } else {
            state.setBackgroundRoutine(BG_PLAIN);
            plainParameters(state, cameraX, cameraY);
        }
    }

    /**
     * {@code SSZ1_BackgroundEvent}. Returns whether this frame renders the cloud bands
     * ({@code ApplyDeformation} with {@code SSZ1_BGDeformArray}) rather than
     * {@code PlainDeformation}.
     */
    private boolean backgroundEvent(SszZoneRuntimeState state, int cameraX, int cameraY) {
        switch (state.backgroundRoutine()) {
            case BG_PLAIN -> {
                // loc_578AA
                if (inCloudBand(cameraY)) {
                    state.setEventsBgWord(EV_SAVED_BG_X, state.backgroundCameraX() & 0xFFFF);
                    state.setEventsBgWord(EV_SAVED_BG_Y, state.backgroundCameraY() & 0xFFFF);
                    cloudParameters(state, cameraX, cameraY);
                    state.setBackgroundRoutine(BG_ENTERING_CLOUDS);
                    // loc_57946 falls into loc_5799A with Events_bg+$0C still set, so the frame
                    // that starts the redraw still renders the saved plain framing.
                    restoreSavedFraming(state);
                    return false;
                }
                plainParameters(state, cameraX, cameraY);
                return false;
            }
            case BG_ENTERING_CLOUDS -> {
                // loc_57942: Draw_PlaneVertBottomUp completes in one engine frame.
                cloudParameters(state, cameraX, cameraY);
                state.setEventsBgWord(EV_SAVED_BG_X, 0);
                state.setBackgroundRoutine(BG_CLOUDS);
                return true;
            }
            case BG_CLOUDS -> {
                // loc_57960
                if (!inCloudBand(cameraY)) {
                    plainParameters(state, cameraX, cameraY);
                    state.setBackgroundRoutine(BG_LEAVING_CLOUDS);
                    return false;
                }
                cloudParameters(state, cameraX, cameraY);
                return true;
            }
            case BG_LEAVING_CLOUDS -> {
                // loc_579D2: Draw_PlaneVertSingleBottomUp, then loc_578F0 either way.
                plainParameters(state, cameraX, cameraY);
                state.setBackgroundRoutine(BG_PLAIN);
                return false;
            }
            default -> throw new IllegalStateException(
                    "SSZ Events_routine_bg " + state.backgroundRoutine());
        }
    }

    /** {@code loc_5799A}: {@code Events_bg+$0C} non-zero restores the pre-redraw framing. */
    private static void restoreSavedFraming(SszZoneRuntimeState state) {
        state.setBackgroundCameraX(state.eventsBgWord(EV_SAVED_BG_X));
        state.setBackgroundCameraY(state.eventsBgWord(EV_SAVED_BG_Y));
    }

    /** {@code and.w (Screen_Y_wrap_value).w,d0} then the {@code $800}/{@code $F00} comparisons. */
    static boolean inCloudBand(int cameraY) {
        int wrapped = cameraY & SCREEN_Y_WRAP_MASK;
        return wrapped >= CLOUD_BAND_MIN_Y && wrapped < CLOUD_BAND_MAX_Y;
    }

    /**
     * {@code sub_579F0}. {@code Events_bg+$10} latches the framing; crossing
     * {@code Camera_X_pos $1800} in either direction flips the latch, re-runs the
     * subroutine and re-rounds {@code Camera_X_pos_BG_rounded}.
     */
    static void plainParameters(SszZoneRuntimeState state, int cameraX, int cameraY) {
        boolean far = state.backgroundFarFraming() != 0;
        boolean shouldBeFar = (cameraX & 0xFFFF) >= FAR_FRAMING_CAMERA_X;
        if (far != shouldBeFar) {
            state.setBackgroundFarFraming(shouldBeFar ? 0xFFFF : 0);
            plainParameters(state, cameraX, cameraY);
            state.setBackgroundCameraXRounded(state.backgroundCameraX() & 0xFFF0);
            return;
        }
        if (far) {
            // loc_57A4C
            state.setBackgroundCameraY(cameraY + FAR_Y_OFFSET);
            state.setBackgroundCameraX(cameraX);
        } else {
            // loc_57A12
            state.setBackgroundCameraY(cameraY + NEAR_Y_OFFSET + state.cloudOscillator());
            state.setBackgroundCameraX(cameraX + NEAR_X_OFFSET);
        }
    }

    /**
     * {@code sub_57A60}. {@code Camera_Y_pos_BG_copy} is half the wrapped camera Y measured from
     * {@code $800} plus {@code $A0}, with half the cloud oscillator folded in before the mask.
     * The thirty scroll words are a fixed fan built from two 16.16 rates — {@code Camera_X/64}
     * plus the drift accumulator for the topmost band, and {@code Camera_X/32} plus the
     * accumulator as the step between bands — so the accumulator's {@code $500} per frame drifts
     * each band at a multiple of its index.
     */
    void cloudParameters(SszZoneRuntimeState state, int cameraX, int cameraY) {
        int y = (short) (cameraY + (state.cloudOscillator() >> 1));
        y &= SCREEN_Y_WRAP_MASK;
        y = (short) (y - CLOUD_BAND_MIN_Y);
        state.setBackgroundCameraY((y >> 1) + 0xA0);

        int drift = state.cloudDrift();
        state.setCloudDrift(drift + CLOUD_DRIFT_PER_FRAME);

        int perThirtySecond = (((short) cameraX) << 16) >> 5;
        int step = perThirtySecond + drift;
        int value = (perThirtySecond >> 1) + drift;

        hScrollTable.clear();
        setWords(value, 0x00, 0x06, 0x0A, 0x14);
        value += step;
        setWords(value, 0x08, 0x0E);
        value += step;
        setWords(value, 0x04, 0x0C, 0x12, 0x16, 0x3A);
        value += step;
        setWords(value, 0x02, 0x10, 0x18, 0x38);
        value += step;
        setWords(value, 0x1A, 0x36);
        value += step;
        setWords(value, 0x1C, 0x34);
        value += step;
        setWords(value, 0x1E, 0x32);
        value += step;
        setWords(value, 0x20, 0x30);
        value += step;
        setWords(value, 0x22, 0x2E);
        value += 2 * step;
        setWords(value, 0x24, 0x2C);
        int halfStep = step >> 1;
        value += 2 * step + halfStep;
        setWords(value, 0x26, 0x2A);
        value += 2 * step + halfStep;
        setWords(value, 0x28);
    }

    /** {@code move.w d0,offset(a1)} with {@code a1 = HScroll_table+$004}. */
    private void setWords(int value, int... byteOffsets) {
        short word = (short) (value >> 16);
        for (int offset : byteOffsets) {
            hScrollTable.set(DEFORM_TABLE_START_INDEX + (offset >> 1), word);
        }
    }

    ScrollValueTable scrollWords() {
        return hScrollTable;
    }

    ScrollValueTable actTwoScrollWords() {
        return act2ScrollTable;
    }

    @Override
    public int getBgPeriodWidth() {
        // Cloud mode fills the whole 512-pixel plane from four chunks (loc_5799A's moveq #$20,d6)
        // and moves it only with the HScroll bands, so its period is the plane, not the fan width.
        // Act 1 draws Plane B as a nametable in both modes, so its period is the plane. Act 2's
        // layer is slice 9's and keeps the fan-derived period.
        return backgroundWindowActive() ? VDP_PLANE_WIDTH_PX : currentBgPeriodWidth;
    }

    /**
     * {@code loc_5786A}, {@code loc_57946} and {@code loc_5799A} each draw the cloud-mode plane
     * with a literal {@code move.w #$1C00,d1} — a fixed background-layout X, not a camera-derived
     * one. {@code sub_57A60} never writes {@code Camera_X_pos_BG_copy} at all, so that word is
     * stale throughout cloud mode and every pixel of horizontal motion comes from the
     * {@code HScroll_table} fan it builds instead.
     *
     * <p>{@code $1C00 >> 7} is background layout column 56, and {@code loc_5799A}'s
     * {@code moveq #$20,d6} is 32 cells — four 128-pixel chunks, exactly the 512-pixel plane
     * width. So the cloud band the player climbs past is layout columns 56-59, which is where the
     * ROM puts it: rows 3-7 of those four columns carry the cloud chunks and nothing else does.
     *
     * <p>Without this override the tilemap window stays at the camera-derived default, which over
     * this stretch is layout columns 0-22 — all the flat sky chunk — so the plane rendered as
     * empty blue for the whole ascent (s3k-known-bugs #41). Plain mode keeps returning
     * {@code MIN_VALUE}: there the ROM really does use {@code Camera_X_pos_BG_copy}
     * ({@code Reset_TileOffsetPositionEff}), which is a separate question.
     *
     * <p>This is the same mechanism {@code SwScrlMgz} state 8 uses, and the ICZ1 opening
     * ({@code d1 = $1880}) is the other zone that pins its plane this way.
     */
    @Override
    public int getBgCameraX() {
        if (cloudWindowActive()) {
            return CLOUD_WINDOW_LAYOUT_X;
        }
        // Plain mode is camera-derived on the cartridge, but the cache window still has to follow
        // the word rather than sit at layout X 0: sub_57A60 writes Camera_X_pos_BG_copy and
        // Reset_TileOffsetPositionEff refills Plane B from it. See s3k-known-bugs #41.
        SszZoneRuntimeState state = state();
        if (state == null || state.actIndex() != 0) {
            return Integer.MIN_VALUE;
        }
        return state.backgroundCameraX() & 0xFFFF;
    }

    /**
     * Whether act 1's background plane is on the 512-pixel wrap model at all. True in both modes:
     * the cloud window pins the plane and plain mode moves it with the camera, but either way the
     * plane is a nametable and not a slice of a contiguous background.
     */
    public static boolean backgroundWindowActive() {
        SszZoneRuntimeState state = state();
        return state != null && state.actIndex() == 0;
    }

    /**
     * Whether the background plane is currently sourced from the fixed cloud window. Read by
     * {@link #getBgCameraX()} and by {@code Sonic3kZoneFeatureProvider.bgWrapsHorizontally()},
     * which has to agree with it for the window relocation to be applied at all.
     */
    public static boolean cloudWindowActive() {
        SszZoneRuntimeState state = state();
        if (state == null || state.actIndex() != 0) {
            return false;
        }
        // Both cloud-path routines load #$1C00: loc_57946 (the entering redraw, which still
        // renders the saved plain framing that frame) and loc_5799A (the steady state). So the
        // plane source moves to the window on the frame the redraw starts, not a frame later.
        int routine = state.backgroundRoutine();
        return routine == BG_ENTERING_CLOUDS || routine == BG_CLOUDS;
    }

    private static int viewportWidth() {
        return GameServices.hasRuntime() && GameServices.cameraOrNull() != null
                ? GameServices.camera().getWidth() & 0xFFFF : 320;
    }

    private static SszZoneRuntimeState state() {
        if (!GameServices.hasRuntime()) {
            return null;
        }
        return S3kRuntimeStates.currentSsz(GameServices.zoneRuntimeRegistry()).orElse(null);
    }
}
