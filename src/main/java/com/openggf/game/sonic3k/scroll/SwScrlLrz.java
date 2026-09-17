package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.runtime.LrzZoneRuntimeState;
import com.openggf.game.sonic3k.runtime.S3kRuntimeStates;
import com.openggf.level.scroll.compose.DeformationPlan;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.level.scroll.compose.ScrollValueTable;

import static com.openggf.level.scroll.M68KMath.negWord;

/**
 * Lava Reef Zone background scroll for the two playable acts, {@code $900} and {@code $901}.
 *
 * <p>Act 1 runs {@code LRZ1_Deform} (sonic3k.asm:115389-115435) from
 * {@code LRZ1_BackgroundInit}/{@code LRZ1_BackgroundEvent}; act 2 runs {@code sub_57082}
 * (115739-115820) from {@code LRZ2_BackgroundInit}/{@code LRZ2_BackgroundEvent}. Both hold
 * {@code Camera_X_pos_copy} as a 16.16 long and derive two fractions,
 * {@code b = X/8} ({@code asr.l #3}) and {@code s = b/4} ({@code asr.l #2}). They then scatter
 * {@code b + n*s} across {@code HScroll_table} twice: a descending run of eight words
 * ({@code move.w d1,-(a1)}, step {@code s}) and an ascending run of five
 * ({@code move.w d2,(a1)+}, starting at {@code b+s} with the step doubled).
 *
 * <p>The acts differ in three places only.
 * <ul>
 *   <li>Where the runs start. Act 1 uses {@code HScroll_table+$01C} for both, and separately
 *       writes {@code Camera_X_pos_BG_copy} and {@code HScroll_table+$004} with {@code b}. Act 2
 *       uses {@code HScroll_table+$010} for both and writes only {@code Camera_X_pos_BG_copy},
 *       so its {@code +$004} is whatever the descending run left there ({@code b+5s}).</li>
 *   <li>{@code Camera_Y_pos_BG_copy}. Act 1 is {@code ((Camera_Y_pos_copy - shake) asr 3) + shake};
 *       act 2 is {@code (Y/8 - Y/32) + shake} with {@code Y = Camera_Y_pos_copy - shake}, i.e.
 *       three thirty-seconds. Both remove the shake before scaling and add it back, so the
 *       background shakes 1:1 with the foreground.</li>
 *   <li>The band table and where {@code ApplyDeformation} starts reading:
 *       {@code LRZ1_BGDeformArray} from {@code HScroll_table+$00C} (word 6), and
 *       {@code LRZ2_BGDeformArray} from {@code HScroll_table} (word 0).</li>
 * </ul>
 *
 * <p>{@code Events_bg+$10} and {@code +$12} - {@code b-s} and {@code b-2s} - are not scroll words
 * at all; they are the phase inputs {@code AnimateTiles_LRZ1}/{@code AnimateTiles_LRZ2} subtract
 * {@code Camera_X_pos_BG_copy} from. They are published into {@link LrzZoneRuntimeState} for the
 * animated-tile channels, which own them from slice 2 on.
 *
 * <p>Both Lava Reef background layouts repeat every four 128 px chunks, so the engine's default
 * 512 px plane period is the ROM's nametable. Act 2 confirms it from the other side: its plane is
 * refilled with {@code moveq #0,d1} ({@code LRZ2_BackgroundInit}, {@code loc_57044},
 * {@code loc_5705C}), i.e. always from layout column 0, and only the scroll words move it.
 *
 * <p>Not modelled here, by slice: the locked dome background ({@code sub_56DAC}, reached when
 * {@code Events_bg+$02} is non-zero and {@code PlainDeformation} replaces this routine), the act-2
 * Death Egg background sprite at the tail of {@code sub_57082}, and the boss act {@code $1600},
 * which has its own {@code LRZ3_BackgroundEvent} and does not use this handler.
 */
public class SwScrlLrz extends SwScrlS3kDefault {

    /** {@code LRZ1_BGDeformArray} (sonic3k.asm:115643); the trailing {@code $7FFF} is the remainder. */
    private static final int[] ACT1_BG_DEFORM =
            {0x40, 0x20, 0x10, 0x10, 0x10, 0x10, 0x10, 0x100, 0x10, 0x10, 0x10, 0x20, 0x7FFF};
    /** {@code LRZ2_BGDeformArray} (sonic3k.asm:115845). */
    private static final int[] ACT2_BG_DEFORM =
            {0x20, 0x20, 0x20, 0x10, 0x10, 0x10, 0x10, 0xF0, 0x10, 0x10, 0x10, 0x20, 0x7FFF};

    /** {@code lea (HScroll_table+$00C).w,a5} before {@code ApplyDeformation} (115379). */
    private static final int ACT1_DEFORM_START_WORD = 6;
    /** {@code lea (HScroll_table).w,a5} before {@code ApplyDeformation} (115738). */
    private static final int ACT2_DEFORM_START_WORD = 0;

    /** Act 1 writes up to {@code HScroll_table+$024}, word 18. */
    private static final int TABLE_WORDS = 19;

    /** Act 1: {@code lea (HScroll_table+$01C).w,a1} for both runs. */
    private static final int ACT1_DESCENDING_FIRST_WORD = 13;
    private static final int ACT1_ASCENDING_FIRST_WORD = 14;
    /** Act 2: {@code lea (HScroll_table+$010).w,a1} for both runs. */
    private static final int ACT2_DESCENDING_FIRST_WORD = 7;
    private static final int ACT2_ASCENDING_FIRST_WORD = 8;

    /** {@code moveq #8-1,d3} and {@code moveq #5-1,d3}. */
    private static final int DESCENDING_WORDS = 8;
    private static final int ASCENDING_WORDS = 5;

    /** {@code Camera_X_pos_BG_copy} and, on act 1 only, {@code HScroll_table+$004}. */
    private static final int BG_CAMERA_WORD = 2;

    private static final DeformationPlan.ScrollValueTransform NEGATE_WORD = value -> negWord(value);

    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private final ScrollValueTable hScrollTable = ScrollValueTable.ofLength(TABLE_WORDS);

    /**
     * ROM {@code V_scroll_value} for the foreground is {@code Camera_Y_pos_copy} after
     * {@code LRZ1_ScreenEvent}/{@code LRZ2_ScreenEvent} added {@code Screen_shake_offset} to it.
     * Zero selects the parallax manager's plain camera Y, which is what an unshaken frame gives.
     */
    private short foregroundVscroll;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();
        composer.reset();

        int shake = screenShakeOffset();
        // LRZ1_ScreenEvent / LRZ2_ScreenEvent: add.w d0,(Camera_Y_pos_copy).w.
        int cameraYCopy = (short) (cameraY + shake);
        foregroundVscroll = (short) cameraYCopy;

        short bgY = backgroundY(actId, cameraYCopy, shake);
        composer.setVscrollFactorBG(bgY);

        int base = (cameraX << 16) >> 3;               // asr.l #3: b
        int step = base >> 2;                          // asr.l #2: s
        buildHScrollTable(actId, base, step);
        publishDeformationWords(bgY, base, step);

        DeformationPlan.applyTableBands(
                composer,
                bgY,
                negWord(cameraX),
                hScrollTable,
                actId == 0 ? ACT1_BG_DEFORM : ACT2_BG_DEFORM,
                actId == 0 ? ACT1_DEFORM_START_WORD : ACT2_DEFORM_START_WORD,
                NEGATE_WORD);

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }

    @Override
    public short getVscrollFactorFG() {
        return foregroundVscroll;
    }


    /**
     * {@code Camera_Y_pos_BG_copy}. Act 1 ({@code LRZ1_Deform}, 115390-115395) works in words:
     * {@code sub.w d1,d0 / asr.w #3,d0 / add.w d1,d0}. Act 2 ({@code sub_57082}, 115740-115751)
     * works in 16.16: {@code asr.l #3} then a copy {@code asr.l #2} subtracted from it, i.e.
     * {@code Y/8 - Y/32}, with the shake added back to the high word.
     */
    private static short backgroundY(int actId, int cameraYCopy, int shake) {
        int withoutShake = (short) (cameraYCopy - shake);
        if (actId == 0) {
            return (short) (((short) (withoutShake >> 3)) + shake);
        }
        int eighth = (withoutShake << 16) >> 3;
        int thirtySecond = eighth >> 2;
        return (short) (((eighth - thirtySecond) >> 16) + shake);
    }

    /**
     * {@code Events_bg+$10} = {@code b - s} and {@code Events_bg+$12} = {@code b - 2s}, together
     * with the two background camera words. Both acts write the same four.
     */
    private void publishDeformationWords(short bgY, int base, int step) {
        if (!GameServices.hasRuntime()) {
            return;
        }
        S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry()).ifPresent(lrz ->
                lrz.publishDeformationWords(
                        base >> 16,
                        bgY,
                        (base - step) >> 16,
                        (base - 2 * step) >> 16));
    }

    /**
     * The two scatter runs. {@code move.w d1,-(a1)} predecrements, so the descending run fills
     * words from {@code first} downwards with {@code b, b+s ... b+7s}; the ascending run restarts
     * at the same pointer with {@code d2 = b+s} and {@code d0} doubled, so it fills upwards with
     * {@code b+s, b+3s ... b+9s}.
     */
    private void buildHScrollTable(int actId, int base, int step) {
        hScrollTable.clear();
        int descendingFirst = actId == 0 ? ACT1_DESCENDING_FIRST_WORD : ACT2_DESCENDING_FIRST_WORD;
        int ascendingFirst = actId == 0 ? ACT1_ASCENDING_FIRST_WORD : ACT2_ASCENDING_FIRST_WORD;

        int value = base;
        for (int i = 0; i < DESCENDING_WORDS; i++) {
            hScrollTable.set(descendingFirst - i, (short) (value >> 16));
            value += step;
        }

        value = base + step;
        int doubledStep = step * 2;
        for (int i = 0; i < ASCENDING_WORDS; i++) {
            hScrollTable.set(ascendingFirst + i, (short) (value >> 16));
            value += doubledStep;
        }

        if (actId == 0) {
            // LRZ1_Deform only: move.w d1,(Camera_X_pos_BG_copy).w / move.w d1,(HScroll_table+$004).w
            hScrollTable.set(BG_CAMERA_WORD, (short) (base >> 16));
        }
    }

    /**
     * {@code Screen_shake_offset} as this frame's events read it. The Lava Reef runtime state owns
     * the {@code ShakeScreen_Setup} countdown; outside a gameplay runtime there is no shake.
     */
    protected int screenShakeOffset() {
        if (!GameServices.hasRuntime()) {
            return 0;
        }
        return S3kRuntimeStates.currentLrz(GameServices.zoneRuntimeRegistry())
                .map(LrzZoneRuntimeState::appliedScreenShakeOffset)
                .orElse(0);
    }

    /** Test access to the {@code HScroll_table} words this handler writes for an act. */
    short[] buildHScrollTableForTest(int actId, int cameraX) {
        int base = (cameraX << 16) >> 3;
        buildHScrollTable(actId, base, base >> 2);
        short[] words = new short[TABLE_WORDS];
        for (int i = 0; i < TABLE_WORDS; i++) {
            words[i] = hScrollTable.get(i);
        }
        return words;
    }
}
